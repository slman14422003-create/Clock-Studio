package com.clock.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Shows one verse with an explanation (tafsir) loaded from the internet and cached for offline use. */
public class QuranTafsirActivity extends BaseActivity {
    static final String EXTRA_AYAH = "ayah";
    private static final String PREF = "quran_ui", KEY_ED = "tafsir_ed2";

    private QuranData d;
    private int ayah, edition, token;
    private Header hd;
    private ScrollView sv;
    private TextView verse, ref, label, body;
    private LinearLayout chips;
    private final TextView[] chipViews = new TextView[QuranTafsir.IDS.length];
    private String[] names;
    private Typeface face = Typeface.SERIF;
    private String loaded;
    private TextView modeAuto, modeOff, dlInfo, dlSurah, dlBook, dlDelete;
    private ProgressBar dlBar;
    private boolean wasRunning;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        d = QuranData.peek(this);
        if (d == null) {
            Ui.toast(this, getString(R.string.quran_need_quran));
            finish();
            return;
        }
        ayah = Math.max(0, Math.min(QuranData.AYAHS - 1, getIntent().getIntExtra(EXTRA_AYAH, 0)));
        edition = Math.max(0, Math.min(QuranTafsir.IDS.length - 1, getSharedPreferences(PREF, MODE_PRIVATE).getInt(KEY_ED, 0)));
        names = getResources().getStringArray(R.array.tafsir_names);
        try {
            face = Typeface.createFromAsset(getAssets(), "fonts/quran.ttf");
        } catch (Throwable e) {
            face = Typeface.SERIF;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        hd = new Header(this, "");
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        hd.addButton(R.drawable.ic_copy, R.string.quran_copy, false, v -> copy());
        hd.addButton(R.drawable.ic_share, R.string.quran_share, false, v -> share());
        root.addView(hd);

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 20));
        col.addView(verseCard());
        col.addView(chipRow(), margins(0, 14, 0, 0));
        col.addView(downloadCard(), margins(0, 12, 0, 0));
        col.addView(bodyCard(), margins(0, 12, 0, 0));
        TextView hint = Ui.footnote(this, getString(R.string.quran_tafsir_hint));
        hint.setPaddingRelative(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), 0);
        col.addView(hint);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(bottomBar());
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
        bind();
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return lp;
    }

    private View verseCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 18));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF12906B, 0xFF0B3D4A});
        g.setCornerRadius(Ui.dpf(this, 22));
        c.setBackground(g);
        verse = new TextView(this);
        verse.setTypeface(face);
        verse.setTextSize(26);
        verse.setLineSpacing(0f, 1.5f);
        verse.setTextColor(Color.WHITE);
        verse.setGravity(Gravity.CENTER);
        verse.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        verse.setTextIsSelectable(true);
        c.addView(verse, new LinearLayout.LayoutParams(-1, -2));
        ref = new TextView(this);
        ref.setTextSize(14);
        ref.setTextColor(0xCCFFFFFF);
        ref.setTypeface(Ui.medium());
        ref.setGravity(Gravity.CENTER);
        ref.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        ref.setPadding(0, Ui.dp(this, 14), 0, 0);
        c.addView(ref, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private View chipRow() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < QuranTafsir.IDS.length; i++) {
            final int k = i;
            TextView t = Ui.chipView(this, names[i], v -> {
                if (edition == k) return;
                Ui.tick(v);
                edition = k;
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt(KEY_ED, k).apply();
                styleChips();
                loadBody();
                refreshDl();
            });
            chipViews[i] = t;
            chips.addView(t);
        }
        hs.addView(chips, new HorizontalScrollView.LayoutParams(-2, -2));
        styleChips();
        return hs;
    }

    private void styleChips() {
        for (int i = 0; i < chipViews.length; i++) {
            boolean on = i == edition;
            chipViews[i].setBackground(Ui.rect(Ui.color(this, on ? R.color.accent : R.color.neutral_soft), Ui.dpf(this, 100)));
            chipViews[i].setTextColor(Ui.color(this, on ? R.color.on_accent : R.color.text_primary));
        }
    }

    private View downloadCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 18)));
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));

        LinearLayout modes = new LinearLayout(this);
        modeAuto = Ui.chipView(this, getString(R.string.tafsir_mode_auto), v -> setMode(false));
        modeOff = Ui.chipView(this, getString(R.string.tafsir_mode_offline), v -> setMode(true));
        modes.addView(modeAuto);
        modes.addView(modeOff);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.addView(modes, new HorizontalScrollView.LayoutParams(-2, -2));
        c.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        dlInfo = Ui.text(this, "", 13, R.color.text_secondary);
        dlInfo.setPadding(Ui.dp(this, 2), Ui.dp(this, 12), 0, Ui.dp(this, 6));
        c.addView(dlInfo, new LinearLayout.LayoutParams(-1, -2));

        dlBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        dlBar.setMax(1000);
        dlBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        dlBar.setVisibility(View.GONE);
        c.addView(dlBar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 6)));

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, Ui.dp(this, 8), 0, 0);
        dlSurah = pill(R.string.tafsir_dl_surah, R.drawable.btn_primary, R.color.on_accent);
        dlSurah.setTextSize(14);
        dlSurah.setOnClickListener(v -> onDownload(false));
        dlBook = pill(R.string.tafsir_dl_book, R.drawable.btn_secondary, R.color.text_primary);
        dlBook.setTextSize(14);
        dlBook.setOnClickListener(v -> onDownload(true));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        a.setMarginEnd(Ui.dp(this, 8));
        row.addView(dlSurah, a);
        row.addView(dlBook, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        c.addView(row, new LinearLayout.LayoutParams(-1, -2));

        dlDelete = Ui.text(this, getString(R.string.tafsir_dl_delete), 14, R.color.bad);
        dlDelete.setTypeface(Ui.medium());
        dlDelete.setGravity(Gravity.CENTER);
        dlDelete.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
        dlDelete.setOnClickListener(v -> {
            int s = d.surahOf[ayah];
            QuranTafsir.deleteSurah(this, QuranTafsir.IDS[edition], s, d.surahs[s].ayahs);
            Ui.toast(this, getString(R.string.tafsir_dl_deleted));
            refreshDl();
        });
        c.addView(dlDelete, new LinearLayout.LayoutParams(-1, -2));
        styleModes();
        return c;
    }

    private void styleModes() {
        boolean off = QuranTafsir.offlineOnly(this);
        modeAuto.setBackground(Ui.rect(Ui.color(this, !off ? R.color.accent : R.color.neutral_soft), Ui.dpf(this, 100)));
        modeAuto.setTextColor(Ui.color(this, !off ? R.color.on_accent : R.color.text_primary));
        modeOff.setBackground(Ui.rect(Ui.color(this, off ? R.color.accent : R.color.neutral_soft), Ui.dpf(this, 100)));
        modeOff.setTextColor(Ui.color(this, off ? R.color.on_accent : R.color.text_primary));
    }

    private void setMode(boolean offline) {
        if (QuranTafsir.offlineOnly(this) == offline) return;
        Ui.tick(offline ? modeOff : modeAuto);
        QuranTafsir.setOfflineOnly(this, offline);
        styleModes();
        loadBody();
    }

    private void onDownload(boolean book) {
        String ed = QuranTafsir.IDS[edition];
        int s = d.surahOf[ayah];
        int key = book ? 0 : s;
        if (QuranTafsir.running(ed, key)) {
            QuranTafsir.cancel(ed, key);
            return;
        }
        QuranTafsir.failed = false;
        QuranTafsir.download(this, ed, d, key);
    }

    private void refreshDl() {
        if (dlInfo == null || d == null) return;
        String ed = QuranTafsir.IDS[edition];
        int s = d.surahOf[ayah], n = d.surahs[s].ayahs;
        int[] ps = QuranTafsir.progress(ed, s), pb = QuranTafsir.progress(ed, 0);
        int[] pr = pb != null ? pb : ps;
        boolean running = pr != null;
        if (running) {
            dlInfo.setText(getString(R.string.tafsir_dl_progress, QuranData.num(pr[0]), QuranData.num(pr[1])));
            dlBar.setVisibility(View.VISIBLE);
            dlBar.setProgress(pr[1] == 0 ? 0 : (int) (1000L * pr[0] / pr[1]), true);
            dlSurah.setText(R.string.tafsir_dl_stop);
            dlBook.setVisibility(View.GONE);
            dlDelete.setVisibility(View.GONE);
            dlSurah.setOnClickListener(v -> onDownload(pb != null));
        } else {
            int have = QuranTafsir.cachedCount(this, ed, s, n);
            dlInfo.setText(getString(R.string.tafsir_dl_saved, QuranData.num(have), QuranData.num(n)));
            dlBar.setVisibility(View.GONE);
            dlSurah.setText(R.string.tafsir_dl_surah);
            dlSurah.setVisibility(have >= n ? View.GONE : View.VISIBLE);
            dlSurah.setOnClickListener(v -> onDownload(false));
            dlBook.setVisibility(View.VISIBLE);
            dlDelete.setVisibility(have > 0 ? View.VISIBLE : View.GONE);
            if (wasRunning) {
                if (QuranTafsir.failed) {
                    QuranTafsir.failed = false;
                    Ui.toast(this, getString(R.string.tafsir_dl_fail));
                } else if (have >= n) {
                    Ui.toast(this, getString(R.string.tafsir_dl_done));
                }
                if (loaded == null) loadBody();
            }
        }
        wasRunning = running;
    }

    @Override
    protected void onResume() {
        super.onResume();
        QuranTafsir.onChange = this::refreshDl;
        refreshDl();
    }

    @Override
    protected void onPause() {
        if (QuranTafsir.onChange != null) QuranTafsir.onChange = null;
        super.onPause();
    }

    private View bodyCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 18)));
        c.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 18));
        label = Ui.text(this, "", 13, R.color.accent_text);
        label.setTypeface(Ui.medium());
        c.addView(label);
        body = Ui.text(this, "", 18, R.color.text_primary);
        body.setLineSpacing(0f, 1.4f);
        body.setTextIsSelectable(true);
        body.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(body, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private View bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Ui.color(this, R.color.bg_group));
        bar.addView(Ui.hairline(this, 0));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        TextView prev = pill(R.string.quran_prev_ayah, R.drawable.btn_secondary, R.color.text_primary);
        prev.setOnClickListener(v -> step(-1));
        TextView next = pill(R.string.quran_next_ayah, R.drawable.btn_primary, R.color.on_accent);
        next.setOnClickListener(v -> step(1));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f);
        a.setMarginEnd(Ui.dp(this, 10));
        row.addView(prev, a);
        row.addView(next, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
        bar.addView(row);
        return bar;
    }

    private TextView pill(int label, int bg, int color) {
        TextView t = Ui.text(this, getString(label), 16, color);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        t.setBackgroundResource(bg);
        Ui.press(this, t);
        return t;
    }

    private void step(int delta) {
        int n = ayah + delta;
        if (n < 0 || n >= QuranData.AYAHS) return;
        ayah = n;
        bind();
        sv.scrollTo(0, 0);
    }

    private void bind() {
        int s = d.surahOf[ayah];
        hd.title.setText(d.surahShort(s));
        hd.setSubtitle(getString(R.string.quran_ayah_n, QuranData.num(d.num[ayah])) + " \u00B7 "
                + getString(R.string.quran_page, QuranData.num(d.page[ayah])));
        verse.setText("\uFD3F" + d.text[ayah] + "\uFD3E");
        ref.setText(d.surahTitle(s) + " \u00B7 " + QuranData.num(d.num[ayah]));
        QuranData.saveLast(this, d.page[ayah], d.surahTitle(s));
        loadBody();
        refreshDl();
    }

    private void loadBody() {
        final int mine = ++token;
        loaded = null;
        label.setText(names[edition]);
        body.setOnClickListener(null);
        body.setText(R.string.quran_tafsir_loading);
        body.setTextColor(Ui.color(this, R.color.text_secondary));
        final int s = d.surahOf[ayah], n = d.num[ayah];
        QuranTafsir.load(this, QuranTafsir.IDS[edition], s, n, text -> {
            if (mine != token || isFinishing() || isDestroyed()) return;
            if (text == null) {
                body.setText(QuranTafsir.offlineMiss ? R.string.tafsir_offline_missing : R.string.quran_tafsir_fail);
                body.setTextColor(Ui.color(this, R.color.bad));
                body.setOnClickListener(v -> loadBody());
                return;
            }
            loaded = text;
            boolean en = QuranTafsir.IDS[edition].startsWith("en.");
            body.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            body.setTextDirection(en ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
            body.setText(text);
            body.setTextColor(Ui.color(this, R.color.text_primary));
            body.setAlpha(0f);
            body.animate().alpha(1f).setDuration(Anim.D_SHORT).start();
            if (ayah + 1 < QuranData.AYAHS) QuranTafsir.prefetch(this, QuranTafsir.IDS[edition], d.surahOf[ayah + 1], d.num[ayah + 1]);
        });
    }

    private String shareText() {
        int s = d.surahOf[ayah];
        StringBuilder b = new StringBuilder();
        b.append("\uFD3F").append(d.text[ayah]).append("\uFD3E [").append(d.surahShort(s)).append(": ")
                .append(QuranData.num(d.num[ayah])).append("]");
        if (loaded != null) b.append("\n\n").append(names[edition]).append(":\n").append(loaded);
        return b.toString();
    }

    private void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("quran", shareText()));
        Ui.toast(this, getString(R.string.quran_copied));
    }

    private void share() {
        ShareCard.show(this, d.surahShort(d.surahOf[ayah]), QuranData.num(d.num[ayah]), d.text[ayah], shareText());
    }
}
