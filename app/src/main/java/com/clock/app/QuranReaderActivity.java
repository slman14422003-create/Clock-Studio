package com.clock.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Mushaf reader: one screen per page of the 604-page Madinah Mushaf. Swipe to turn pages; the pages glide and fade
 * as they pass, and the reader always runs right-to-left like a printed Quran.
 */
public class QuranReaderActivity extends BaseActivity {
    static final String EXTRA_PAGE = "page", EXTRA_AYAH = "ayah", EXTRA_HIGHLIGHT = "hl", EXTRA_PLAY = "play";
    private static final int MIN_FONT = 18, MAX_FONT = 44;

    private QuranData d;
    private RecyclerView pager;
    private LinearLayoutManager lm;
    private PagerSnapHelper snap;
    private TextView title, subtitle, loading;
    private SeekBar seek;
    private LinearLayout selBar;
    private TextView selText;
    private ImageButton selMark;
    private int selected = -1;
    private int curPos = -1;
    private int startPos = 0;
    private boolean seeking;
    private int fontSp = 26;
    private Typeface face = Typeface.SERIF;
    private FrameLayout root;
    private ImageButton playBtn;
    private int playing = -1;
    private final QuranPlayer player = QuranPlayer.get();
    private LinearLayout pill;
    private WaveView wave;
    private ImageButton pillPlay;
    private FrameLayout.LayoutParams selLp;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (pill == null || pill.getVisibility() != View.VISIBLE) return;
            int dur = player.duration();
            if (dur > 0) wave.setProgress(player.position() / (float) dur);
            pill.postDelayed(this, 90);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            String f = Store.raw(this, "quran_font");
            if (f != null && !f.isEmpty()) fontSp = Math.max(MIN_FONT, Math.min(MAX_FONT, Integer.parseInt(f)));
        } catch (Exception ignored) {
        }
        try {
            face = Typeface.createFromAsset(getAssets(), "fonts/quran.ttf");
        } catch (Throwable e) {
            face = Typeface.SERIF;
        }

        int page = getIntent().getIntExtra(EXTRA_PAGE, 0);
        if (page <= 0) page = QuranData.lastPage(this);
        if (page <= 0) page = 1;
        startPos = Math.max(0, Math.min(QuranData.PAGES - 1, page - 1));
        if (getIntent().getBooleanExtra(EXTRA_HIGHLIGHT, false)) selected = getIntent().getIntExtra(EXTRA_AYAH, -1);

        root = new FrameLayout(this);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        main.addView(topBar());

        pager = new RecyclerView(this);
        pager.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        pager.setLayoutManager(lm);
        pager.setItemAnimator(null);
        pager.setHasFixedSize(true);
        pager.setItemViewCacheSize(3);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        snap = new PagerSnapHelper();
        snap.attachToRecyclerView(pager);
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                glide(rv);
                int p = centerPos(rv);
                if (p >= 0 && p != curPos) setCurrent(p);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) saveProgress();
            }
        });
        main.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1f));
        main.addView(playerPill());
        main.addView(bottomBar());

        loading = Ui.text(this, getString(R.string.quran_loading), 16, R.color.text_secondary);
        loading.setGravity(Gravity.CENTER);
        root.addView(loading, new FrameLayout.LayoutParams(-1, -1));

        buildSelectionBar();
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });

        QuranData.load(this, data -> {
            if (isFinishing() || isDestroyed()) return;
            if (data == null) {
                startActivity(new Intent(this, QuranActivity.class));
                finish();
                return;
            }
            d = data;
            loading.setVisibility(View.GONE);
            pager.setAdapter(new PageAdapter());
            lm.scrollToPositionWithOffset(startPos, 0);
            pager.post(() -> {
                setCurrent(startPos);
                glide(pager);
                if (selected >= 0) showSelection();
                player.listener = audioListener;
                audioListener.onState(player.isPlaying());
                if (getIntent().getBooleanExtra(EXTRA_PLAY, false)) {
                    int from = getIntent().getIntExtra(EXTRA_AYAH, d.pageFirst[startPos + 1]);
                    player.play(this, d, QuranAudio.current(this), from);
                }
            });
        });
    }

    // ------------------------------------------------------------------ chrome

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPaddingRelative(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        ImageButton back = Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        title = Ui.text(this, "", 18, R.color.text_primary);
        title.setTypeface(Ui.medium());
        title.setSingleLine(true);
        subtitle = Ui.text(this, "", 12, R.color.text_secondary);
        subtitle.setSingleLine(true);
        mid.addView(title);
        mid.addView(subtitle);
        bar.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView minus = Ui.chipView(this, "A\u2212", v -> changeFont(-2));
        TextView plus = Ui.chipView(this, "A+", v -> changeFont(2));
        ((LinearLayout.LayoutParams) minus.getLayoutParams()).setMarginEnd(Ui.dp(this, 6));
        ((LinearLayout.LayoutParams) plus.getLayoutParams()).setMarginEnd(0);
        minus.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        plus.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        playBtn = Ui.iconButton(this, R.drawable.ic_play_fill, R.string.quran_play, true, v -> togglePlay());
        ImageButton more = Ui.iconButton(this, R.drawable.ic_more, R.string.quran_more, false, v -> showMenu());
        bar.addView(minus);
        bar.addView(plus);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        pb.setMarginStart(Ui.dp(this, 6));
        bar.addView(playBtn, pb);
        LinearLayout.LayoutParams mb = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        mb.setMarginStart(Ui.dp(this, 6));
        bar.addView(more, mb);
        return bar;
    }

    private View bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPaddingRelative(Ui.dp(this, 18), Ui.dp(this, 2), Ui.dp(this, 18), Ui.dp(this, 8));
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView first = Ui.text(this, QuranData.ar(1), 12, R.color.text_secondary);
        TextView last = Ui.text(this, QuranData.ar(QuranData.PAGES), 12, R.color.text_secondary);
        seek = new SeekBar(this);
        seek.setMax(QuranData.PAGES - 1);
        seek.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser || d == null) return;
                lm.scrollToPositionWithOffset(p, 0);
                pager.post(() -> glide(pager));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                saveProgress();
            }
        });
        bar.addView(first);
        bar.addView(seek, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f));
        bar.addView(last);
        return bar;
    }

    private View playerPill() {
        pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        pill.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        GradientDrawable pbg = Ui.rect(Ui.alpha(Ui.color(this, R.color.surface_high), 0xF0), Ui.dpf(this, 30));
        pbg.setStroke(Ui.dp(this, 1), Ui.alpha(Ui.color(this, R.color.text_secondary), 0x30));
        pill.setBackground(pbg);
        pill.setElevation(Ui.dpf(this, 6));
        pill.setVisibility(View.GONE);
        ImageButton prev = Ui.iconButton(this, R.drawable.ic_ayah_prev, R.string.quran_prev_ayah, false, v -> player.previous());
        pillPlay = Ui.iconButton(this, R.drawable.ic_play_fill, R.string.quran_play, true, v -> togglePlay());
        ImageButton next = Ui.iconButton(this, R.drawable.ic_ayah_next, R.string.quran_next_ayah, false, v -> player.next());
        pill.addView(prev, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        pl.setMarginStart(Ui.dp(this, 4));
        pl.setMarginEnd(Ui.dp(this, 4));
        pill.addView(pillPlay, pl);
        pill.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        wave = new WaveView(this);
        wave.setColors(Ui.color(this, R.color.accent_text), Ui.alpha(Ui.color(this, R.color.text_secondary), 0x66));
        wave.seek = f -> player.seekFraction(f);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f);
        wl.setMarginStart(Ui.dp(this, 8));
        pill.addView(wave, wl);
        ImageButton rec = Ui.iconButton(this, R.drawable.ic_mic, R.string.quran_reciter, false, v -> showReciters());
        pill.addView(rec, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 4));
        pill.setLayoutParams(lp);
        return pill;
    }

    private void updatePill() {
        if (pill == null) return;
        boolean show = player.isPlaying() || player.canResume();
        pillPlay.setImageResource(player.isPlaying() ? R.drawable.ic_pause_fill : R.drawable.ic_play_fill);
        wave.setLive(player.isPlaying());
        boolean was = pill.getVisibility() == View.VISIBLE;
        pill.setVisibility(show ? View.VISIBLE : View.GONE);
        if (selLp != null && selBar != null) {
            selLp.bottomMargin = Ui.dp(this, show ? 144 : 56);
            selBar.setLayoutParams(selLp);
        }
        pill.removeCallbacks(tick);
        if (show) {
            if (!was) {
                pill.setAlpha(0f);
                pill.setTranslationY(Ui.dp(this, 16));
                pill.animate().alpha(1f).translationY(0f).setDuration(Anim.D_MED).setInterpolator(Anim.OUT).start();
            }
            pill.post(tick);
        }
    }

    private void buildSelectionBar() {
        selBar = new LinearLayout(this);
        selBar.setOrientation(LinearLayout.VERTICAL);
        selBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 12));
        GradientDrawable bg = Ui.rect(Ui.alpha(Ui.color(this, R.color.surface_high), 0xF2), Ui.dpf(this, 28));
        bg.setStroke(Ui.dp(this, 1), Ui.alpha(Ui.color(this, R.color.text_secondary), 0x30));
        selBar.setBackground(bg);
        selBar.setElevation(Ui.dpf(this, 12));
        selBar.setVisibility(View.GONE);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPaddingRelative(Ui.dp(this, 6), 0, 0, 0);
        selText = Ui.text(this, "", 15, R.color.text_secondary);
        selText.setTypeface(Ui.medium());
        selText.setSingleLine(true);
        head.addView(selText, new LinearLayout.LayoutParams(0, -2, 1f));
        selMark = Ui.iconButton(this, R.drawable.ic_bookmark, R.string.quran_marks, false, v -> toggleMark());
        ImageButton close = Ui.iconButton(this, R.drawable.ic_x, R.string.cancel, false, v -> select(-1));
        head.addView(selMark, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        head.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        selBar.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 4), 0, 0);
        View tafsir = actionPill(R.drawable.ic_quran, R.string.quran_tafsir, true, v -> openTafsir());
        View listen = actionPill(R.drawable.ic_play_fill, R.string.quran_listen_here, false, v -> playSelected());
        View share = actionPill(R.drawable.ic_share, R.string.quran_share, false, v -> share());
        ImageButton copy = Ui.iconButton(this, R.drawable.ic_copy, R.string.quran_copy, false, v -> copy());
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1.35f);
        t1.setMarginEnd(Ui.dp(this, 8));
        LinearLayout.LayoutParams t2 = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f);
        t2.setMarginEnd(Ui.dp(this, 8));
        LinearLayout.LayoutParams t3 = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f);
        t3.setMarginEnd(Ui.dp(this, 6));
        row.addView(tafsir, t1);
        row.addView(listen, t2);
        row.addView(share, t3);
        row.addView(copy, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        selBar.addView(row);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 56));
        selLp = lp;
        root.addView(selBar, lp);
    }

    /** Capsule button with an icon and a label; the primary one is filled with the accent colour. */
    private View actionPill(int icon, int label, boolean primary, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        int fg = Ui.color(this, primary ? R.color.on_accent : R.color.text_primary);
        b.setBackground(Ui.rect(Ui.color(this, primary ? R.color.accent : R.color.neutral_soft), Ui.dpf(this, 100)));
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setImageTintList(ColorStateList.valueOf(fg));
        b.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15);
        t.setTypeface(Ui.medium());
        t.setTextColor(fg);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(Ui.dp(this, 6));
        b.addView(t, tl);
        b.setOnClickListener(click);
        Ui.press(this, b);
        return b;
    }

    private void playSelected() {
        if (d == null || selected < 0) return;
        player.listener = audioListener;
        player.play(this, d, QuranAudio.current(this), selected);
    }

    private void showReciters() {
        final QuranAudio.Reciter cur = QuranAudio.current(this);
        CharSequence[] items = new CharSequence[QuranAudio.RECITERS.length];
        for (int i = 0; i < items.length; i++) {
            QuranAudio.Reciter r = QuranAudio.RECITERS[i];
            items[i] = (r == cur ? "\u2713 " : "") + r.name();
        }
        new Dlg(this).setTitle(R.string.quran_reciter).setItems(items, (dlg, which) -> {
            QuranAudio.Reciter r = QuranAudio.RECITERS[which];
            QuranAudio.setCurrent(this, r);
            Ui.toast(this, getString(R.string.quran_reciter_now, r.name()));
            if (d != null && player.isPlaying() && playing >= 0) {
                player.listener = audioListener;
                player.play(this, d, r, playing);
            }
        }).show();
    }

    /** The verse the options act on: the selected one, otherwise the first verse of the current page. */
    private int targetAyah() {
        if (selected >= 0) return selected;
        return d.pageFirst[Math.max(0, curPos) + 1];
    }

    private void showMenu() {
        if (d == null) return;
        CharSequence[] items = {
                getString(R.string.quran_reciter_now, QuranAudio.current(this).name()),
                getString(R.string.quran_audio),
                getString(R.string.quran_tafsir),
                getString(R.string.quran_share)
        };
        new Dlg(this).setTitle(R.string.quran_more).setItems(items, (dlg, which) -> {
            if (which == 0) showReciters();
            else if (which == 1) startActivity(new Intent(this, QuranAudioActivity.class));
            else if (which == 2) startActivity(new Intent(this, QuranTafsirActivity.class)
                    .putExtra(QuranTafsirActivity.EXTRA_AYAH, targetAyah()));
            else {
                int a = targetAyah();
                ShareCard.show(this, d.surahShort(d.surahOf[a]), QuranData.num(d.num[a]), d.text[a], ayahShare(a));
            }
        }).show();
    }

    // ------------------------------------------------------------------ paging

    private int centerPos(RecyclerView rv) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        float c = rv.getWidth() / 2f;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View v = rv.getChildAt(i);
            float dist = Math.abs((v.getLeft() + v.getRight()) / 2f - c);
            if (dist < bestD) {
                bestD = dist;
                best = rv.getChildAdapterPosition(v);
            }
        }
        return best;
    }

    /** Pages shrink and fade a little as they leave the centre, which makes the turn feel physical. */
    private void glide(RecyclerView rv) {
        float w = Math.max(1f, rv.getWidth());
        for (int i = 0; i < rv.getChildCount(); i++) {
            View v = rv.getChildAt(i);
            float off = Math.min(1f, Math.abs(((v.getLeft() + v.getRight()) / 2f - w / 2f) / w));
            float e = off * off * (3f - 2f * off);
            v.setAlpha(1f - 0.55f * e);
            float sc = 1f - 0.07f * e;
            v.setScaleX(sc);
            v.setScaleY(sc);
        }
    }

    private void setCurrent(int pos) {
        if (d == null || pos < 0 || pos >= QuranData.PAGES) return;
        curPos = pos;
        int p = pos + 1;
        int first = d.pageFirst[p];
        title.setText(d.surahTitle(d.surahOf[first]));
        subtitle.setText(getString(R.string.quran_juz_n, QuranData.num(d.juz[first])) + " \u00B7 "
                + getString(R.string.quran_page, QuranData.num(p)));
        if (!seeking) seek.setProgress(pos);
    }

    private void saveProgress() {
        if (d == null || curPos < 0) return;
        int p = curPos + 1;
        QuranData.saveLast(this, p, d.surahTitle(d.surahOf[d.pageFirst[p]]));
    }

    private void changeFont(int delta) {
        int n = Math.max(MIN_FONT, Math.min(MAX_FONT, fontSp + delta));
        if (n == fontSp) return;
        fontSp = n;
        Store.putRaw(this, "quran_font", String.valueOf(n));
        if (pager.getAdapter() != null) pager.getAdapter().notifyDataSetChanged();
    }

    // ------------------------------------------------------------------ selection

    private void select(int ayah) {
        int old = selected;
        selected = ayah == old ? -1 : ayah;
        if (selected < 0) hideSelection(); else showSelection();
        if (pager.getAdapter() != null) {
            if (old >= 0) pager.getAdapter().notifyItemChanged(d.page[old] - 1);
            if (selected >= 0 && (old < 0 || d.page[selected] != d.page[old])) pager.getAdapter().notifyItemChanged(d.page[selected] - 1);
        }
    }

    private void showSelection() {
        if (d == null || selected < 0) return;
        selText.setText(d.surahShort(d.surahOf[selected]) + " \u00B7 " + QuranData.num(d.num[selected]));
        selMark.setImageResource(QuranData.isMarked(this, selected) ? R.drawable.ic_bookmark_fill : R.drawable.ic_bookmark);
        if (selBar.getVisibility() != View.VISIBLE) {
            selBar.setVisibility(View.VISIBLE);
            selBar.setAlpha(0f);
            selBar.setTranslationY(Ui.dpf(this, 30));
            selBar.animate().alpha(1f).translationY(0).setDuration(Anim.D_MED).setInterpolator(Anim.OUT).start();
        }
    }

    private void hideSelection() {
        selBar.animate().alpha(0f).translationY(Ui.dpf(this, 30)).setDuration(Anim.D_SHORT).setInterpolator(Anim.IN)
                .withEndAction(() -> selBar.setVisibility(View.GONE)).start();
    }

    private String ayahShare(int a) {
        return "\uFD3F" + d.text[a] + "\uFD3E [" + d.surahShort(d.surahOf[a]) + ": " + QuranData.num(d.num[a]) + "]";
    }

    private void toggleMark() {
        if (selected < 0) return;
        boolean on = QuranData.toggleMark(this, selected);
        selMark.setImageResource(on ? R.drawable.ic_bookmark_fill : R.drawable.ic_bookmark);
        Ui.toast(this, getString(on ? R.string.quran_bookmarked : R.string.quran_unbookmarked));
        if (pager.getAdapter() != null) pager.getAdapter().notifyItemChanged(d.page[selected] - 1);
    }

    private void openTafsir() {
        if (selected < 0) return;
        startActivity(new Intent(this, QuranTafsirActivity.class).putExtra(QuranTafsirActivity.EXTRA_AYAH, selected));
    }

    private void copy() {
        if (selected < 0) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("quran", ayahShare(selected)));
        Ui.toast(this, getString(R.string.quran_copied));
    }

    private void share() {
        if (selected < 0) return;
        ShareCard.show(this, d.surahShort(d.surahOf[selected]), QuranData.num(d.num[selected]), d.text[selected], ayahShare(selected));
    }

    // ------------------------------------------------------------------ pages

    private final class PageVH extends RecyclerView.ViewHolder {
        final ScrollView sv;
        final LinearLayout content;

        PageVH(FrameLayout card, ScrollView sv, LinearLayout content) {
            super(card);
            this.sv = sv;
            this.content = content;
        }
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageVH> {
        @Override
        public int getItemCount() {
            return QuranData.PAGES;
        }

        @NonNull
        @Override
        public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            FrameLayout card = new FrameLayout(QuranReaderActivity.this);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -1);
            lp.setMargins(Ui.dp(QuranReaderActivity.this, 14), Ui.dp(QuranReaderActivity.this, 4),
                    Ui.dp(QuranReaderActivity.this, 14), Ui.dp(QuranReaderActivity.this, 4));
            card.setLayoutParams(lp);
            GradientDrawable cbg = Ui.rect(Ui.color(QuranReaderActivity.this, R.color.surface), Ui.dpf(QuranReaderActivity.this, 26));
            cbg.setStroke(Ui.dp(QuranReaderActivity.this, 1), Ui.alpha(Ui.color(QuranReaderActivity.this, R.color.text_secondary), 0x22));
            card.setBackground(cbg);
            card.setClipToOutline(true);
            ScrollView sv = new ScrollView(QuranReaderActivity.this);
            sv.setVerticalScrollBarEnabled(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sv.setFillViewport(true);
            LinearLayout content = new LinearLayout(QuranReaderActivity.this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            content.setPadding(Ui.dp(QuranReaderActivity.this, 20), Ui.dp(QuranReaderActivity.this, 14),
                    Ui.dp(QuranReaderActivity.this, 20), Ui.dp(QuranReaderActivity.this, 18));
            sv.addView(content, new ScrollView.LayoutParams(-1, -2));
            card.addView(sv, new FrameLayout.LayoutParams(-1, -1));
            return new PageVH(card, sv, content);
        }

        @Override
        public void onBindViewHolder(@NonNull PageVH h, int pos) {
            bindPage(h, pos + 1);
            h.itemView.setAlpha(1f);
            h.itemView.setScaleX(1f);
            h.itemView.setScaleY(1f);
        }
    }

    private void bindPage(PageVH h, int p) {
        LinearLayout c = h.content;
        c.removeAllViews();
        h.sv.scrollTo(0, 0);
        int from = d.pageFirst[p], to = d.pageFirst[p + 1];

        LinearLayout strip = new LinearLayout(this);
        TextView juz = Ui.text(this, getString(R.string.quran_juz_n, QuranData.num(d.juz[from])), 12, R.color.text_secondary);
        TextView sur = Ui.text(this, d.surahShort(d.surahOf[from]), 12, R.color.text_secondary);
        sur.setGravity(Gravity.END);
        strip.addView(juz, new LinearLayout.LayoutParams(0, -2, 1f));
        strip.addView(sur, new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(strip, new LinearLayout.LayoutParams(-1, -2));

        int i = from;
        while (i < to) {
            int s = d.surahOf[i];
            if (d.num[i] == 1) {
                c.addView(banner(s));
                if (s != 1 && s != 9) c.addView(basmala());
            }
            int j = i + 1;
            while (j < to && d.num[j] != 1) j++;
            c.addView(ayahText(i, j));
            i = j;
        }

        TextView foot = Ui.text(this, QuranData.ar(p), 14, R.color.text_secondary);
        foot.setGravity(Gravity.CENTER);
        foot.setTypeface(Ui.medium());
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = Ui.dp(this, 14);
        c.addView(foot, fl);
    }

    private View banner(int s) {
        QuranData.Surah x = d.surahs[s];
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rect(Ui.color(this, R.color.accent_soft), Ui.dpf(this, 14)));
        b.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        TextView n = new TextView(this);
        n.setText(d.surahTitle(s));
        n.setTypeface(face, Typeface.BOLD);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(20, fontSp - 2));
        n.setTextColor(Ui.color(this, R.color.accent_text));
        n.setGravity(Gravity.CENTER);
        TextView m = new TextView(this);
        m.setText(getString(x.meccan ? R.string.quran_meccan : R.string.quran_medinan) + " \u00B7 "
                + getString(R.string.quran_ayahs, QuranData.num(x.ayahs)));
        m.setTextSize(12);
        m.setTextColor(Ui.color(this, R.color.text_secondary));
        m.setGravity(Gravity.CENTER);
        b.addView(n);
        b.addView(m);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 10);
        lp.bottomMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }

    private View basmala() {
        TextView t = new TextView(this);
        t.setText("\u0628\u0650\u0633\u0652\u0645\u0650 \u0671\u0644\u0644\u0651\u064E\u0647\u0650 \u0671\u0644\u0631\u0651\u064E\u062D\u0652\u0645\u064E\u0670\u0646\u0650 \u0671\u0644\u0631\u0651\u064E\u062D\u0650\u064A\u0645\u0650");
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);
        t.setTextColor(Ui.color(this, R.color.text_primary));
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 4);
        lp.bottomMargin = Ui.dp(this, 4);
        t.setLayoutParams(lp);
        return t;
    }

    private View ayahText(int from, int to) {
        final int accent = Ui.color(this, R.color.accent_text);
        final int warn = Ui.color(this, R.color.warn);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        final java.util.ArrayList<int[]> hl = new java.util.ArrayList<>();
        java.util.List<Integer> marks = QuranData.marks(this);
        for (int k = from; k < to; k++) {
            final int ayah = k;
            int st = sb.length();
            sb.append(d.text[k]);
            int mk = sb.length();
            sb.append(" \uFD3F").append(QuranData.ar(d.num[k])).append("\uFD3E");
            int en = sb.length();
            sb.append(' ');
            sb.setSpan(new ForegroundColorSpan(marks.contains(k) ? warn : accent), mk, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(0.8f), mk, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (k == selected || k == playing) {
                hl.add(new int[]{st, en});
            }
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    select(ayah);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setUnderlineText(false);
                }
            }, st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        AyahTextView t = new AyahTextView(this);
        t.setText(sb);
        int[] rr = new int[hl.size() * 2], cc = new int[hl.size()];
        for (int q = 0; q < hl.size(); q++) {
            rr[q * 2] = hl.get(q)[0];
            rr[q * 2 + 1] = hl.get(q)[1];
            cc[q] = Ui.alpha(Ui.color(this, R.color.text_primary), 0xFF);
        }
        t.setHighlights(rr, cc);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);
        t.setTextColor(Ui.color(this, R.color.text_primary));
        t.setLineSpacing(0f, 1.6f);
        t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setGravity(Gravity.START);
        t.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
        t.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        if (Build.VERSION.SDK_INT >= 26) t.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setHighlightColor(Color.TRANSPARENT);
        t.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 6);
        t.setLayoutParams(lp);
        return t;
    }

    private final QuranPlayer.Listener audioListener = new QuranPlayer.Listener() {
        @Override
        public void onAyah(int ayah) {
            int old = playing;
            playing = ayah;
            RecyclerView.Adapter<?> ad = pager.getAdapter();
            if (ad != null) {
                if (old >= 0 && d.page[old] != d.page[ayah]) ad.notifyItemChanged(d.page[old] - 1);
                ad.notifyItemChanged(d.page[ayah] - 1);
            }
            wave.setAyah(ayah, QuranData.ar(d.num[ayah]));
            updatePill();
            int pg = d.page[ayah] - 1;
            if (pg != curPos && pager.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) pager.smoothScrollToPosition(pg);
        }

        @Override
        public void onState(boolean on) {
            playBtn.setImageResource(on ? R.drawable.ic_pause_fill : R.drawable.ic_play_fill);
            updatePill();
            if (!on && !player.canResume() && playing >= 0) {
                int old = playing;
                playing = -1;
                if (pager.getAdapter() != null) pager.getAdapter().notifyItemChanged(d.page[old] - 1);
            }
        }

        @Override
        public void onError() {
            Ui.toast(QuranReaderActivity.this, getString(R.string.quran_audio_err));
        }
    };

    private void togglePlay() {
        if (d == null) return;
        player.listener = audioListener;
        if (player.isPlaying()) {
            player.pause();
        } else if (player.canResume()) {
            player.resume();
        } else {
            int from = selected >= 0 ? selected : d.pageFirst[curPos + 1];
            player.play(this, d, QuranAudio.current(this), from);
        }
    }

    @Override
    protected void onDestroy() {
        if (pill != null) pill.removeCallbacks(tick);
        if (player.listener == audioListener) {
            player.listener = null;
            player.stop();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        saveProgress();
        super.onPause();
    }
}
