package com.clock.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Quran index: continue reading, surahs, juz and bookmarks. The text is downloaded once and then works offline. */
public class QuranActivity extends BaseActivity {
    private static final int T_CONTINUE = 0, T_DOWNLOAD = 1, T_SURAH = 2, T_JUZ = 3, T_MARK = 4, T_EMPTY = 5, T_FOOT = 6, T_PAD = 7, T_HERO = 8, T_SECT = 9, T_AYAH = 10;

    private static final class Row {
        final int type;
        Object data;
        boolean first, last;

        Row(int type) {
            this.type = type;
        }
    }

    private QuranData d;
    private Header hd;
    private RecyclerView rv;
    private Adapter ad;
    private LinearLayout tools;
    private LinearLayout seg;
    private int tab;
    private String query = "";
    private boolean downloading, failed, loaded;
    private final List<Row> rows = new ArrayList<>();
    private Typeface face = Typeface.SERIF;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        try {
            face = Typeface.createFromAsset(getAssets(), "fonts/quran.ttf");
        } catch (Throwable e) {
            face = Typeface.SERIF;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        hd = new Header(this, getString(R.string.quran));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        hd.addButton(R.drawable.ic_sound, R.string.quran_audio, false, v -> startActivity(new Intent(this, QuranAudioActivity.class)));
        hd.setSubtitle(getString(R.string.quran_sub));
        root.addView(hd);

        tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setVisibility(View.GONE);
        tools.addView(searchBox(), margins(16, 2, 16, 8));
        seg = Ui.segmented(this, new CharSequence[]{getString(R.string.quran_surahs), getString(R.string.quran_juz),
                getString(R.string.quran_marks)}, 0, i -> {
            tab = i;
            rebuild();
            rv.scrollToPosition(0);
        });
        tools.addView(seg, margins(16, 0, 16, 8));
        root.addView(tools, new LinearLayout.LayoutParams(-1, -2));

        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        rv.setClipToPadding(false);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setPadding(0, 0, 0, Ui.dp(this, 24));
        ad = new Adapter();
        rv.setAdapter(ad);
        hd.bindScroll(rv);
        root.addView(rv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        if (QuranData.ready(this)) {
            QuranData.load(this, data -> {
                if (isFinishing() || isDestroyed()) return;
                d = data;
                loaded = true;
                rebuild();
            });
        } else {
            loaded = true;
            rebuild();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loaded) rebuild();
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int bt) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, bt));
        return lp;
    }

    private View searchBox() {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 12)));
        box.setPaddingRelative(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_search);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.text_hint)));
        box.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        EditText et = new EditText(this);
        et.setBackground(null);
        et.setHint(R.string.quran_search_hint);
        et.setHintTextColor(Ui.color(this, R.color.text_hint));
        et.setTextColor(Ui.color(this, R.color.text_primary));
        et.setTextSize(16);
        et.setSingleLine(true);
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        et.setPaddingRelative(Ui.dp(this, 10), 0, 0, 0);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString();
                if (tab != 0) {
                    tab = 0;
                    Ui.setSegment(seg, 0);
                }
                rebuild();
            }
        });
        box.addView(et, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));
        return box;
    }

    // ------------------------------------------------------------------ rows

    private void rebuild() {
        rows.clear();
        if (d == null) {
            tools.setVisibility(View.GONE);
            if (!QuranData.ready(this)) rows.add(new Row(T_DOWNLOAD));
        } else {
            tools.setVisibility(View.VISIBLE);
            if (tab == 0) {
                boolean searching = !query.trim().isEmpty();
                if (!searching) {
                    rows.add(new Row(T_HERO));
                    if (QuranData.lastPage(this) > 0) rows.add(new Row(T_CONTINUE));
                }
                List<QuranData.Surah> l = d.search(query);
                if (searching && !l.isEmpty()) {
                    Row h = new Row(T_SECT);
                    h.data = getString(R.string.quran_sect_surahs);
                    rows.add(h);
                }
                for (int i = 0; i < l.size(); i++) {
                    Row r = new Row(T_SURAH);
                    r.data = l.get(i);
                    r.first = i == 0;
                    r.last = i == l.size() - 1;
                    rows.add(r);
                }
                List<Integer> hits = searching ? d.searchAyahs(query, 80) : new ArrayList<Integer>();
                if (!hits.isEmpty()) {
                    Row h = new Row(T_SECT);
                    h.data = getString(R.string.quran_sect_ayahs);
                    rows.add(h);
                }
                for (int i = 0; i < hits.size(); i++) {
                    Row r = new Row(T_AYAH);
                    r.data = hits.get(i);
                    r.first = i == 0;
                    r.last = i == hits.size() - 1;
                    rows.add(r);
                }
                if (l.isEmpty() && hits.isEmpty()) {
                    Row r = new Row(T_EMPTY);
                    r.data = getString(R.string.quran_no_results);
                    rows.add(r);
                }
            } else if (tab == 1) {
                for (int j = 1; j <= 30; j++) {
                    Row r = new Row(T_JUZ);
                    r.data = j;
                    r.first = j == 1;
                    r.last = j == 30;
                    rows.add(r);
                }
            } else {
                List<Integer> m = QuranData.marks(this);
                for (int i = 0; i < m.size(); i++) {
                    Row r = new Row(T_MARK);
                    r.data = m.get(i);
                    r.first = i == 0;
                    r.last = i == m.size() - 1;
                    rows.add(r);
                }
                if (m.isEmpty()) {
                    Row r = new Row(T_EMPTY);
                    r.data = getString(R.string.quran_no_marks);
                    rows.add(r);
                }
            }
            Row f = new Row(T_FOOT);
            rows.add(f);
        }
        ad.notifyDataSetChanged();
    }

    private void open(int page, int ayah) {
        Intent i = new Intent(this, QuranReaderActivity.class).putExtra(QuranReaderActivity.EXTRA_PAGE, page);
        if (ayah >= 0) i.putExtra(QuranReaderActivity.EXTRA_AYAH, ayah).putExtra(QuranReaderActivity.EXTRA_HIGHLIGHT, true);
        startActivity(i);
    }

    private void startDownload() {
        if (downloading) return;
        downloading = true;
        failed = false;
        ad.notifyItemChanged(0);
        QuranData.download(this, ok -> {
            downloading = false;
            if (isFinishing() || isDestroyed()) return;
            if (ok) {
                d = QuranData.peek(this);
                failed = d == null;
                rebuild();
            } else {
                failed = true;
                ad.notifyItemChanged(0);
            }
        });
    }

    // ------------------------------------------------------------------ adapter

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int p) {
            return rows.get(p).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v;
            switch (type) {
                case T_CONTINUE:
                    v = continueCard();
                    break;
                case T_HERO:
                    v = heroCard();
                    break;
                case T_SECT:
                    v = Ui.sectionTitle(QuranActivity.this, "");
                    break;
                case T_AYAH:
                    v = ayahRow();
                    break;
                case T_DOWNLOAD:
                    v = downloadCard();
                    break;
                case T_EMPTY:
                    v = Ui.footnote(QuranActivity.this, "");
                    ((TextView) v).setGravity(Gravity.CENTER);
                    ((TextView) v).setPadding(Ui.dp(QuranActivity.this, 32), Ui.dp(QuranActivity.this, 40), Ui.dp(QuranActivity.this, 32), 0);
                    break;
                case T_FOOT:
                    v = Ui.footnote(QuranActivity.this, getString(R.string.quran_source));
                    ((TextView) v).setPadding(Ui.dp(QuranActivity.this, 32), Ui.dp(QuranActivity.this, 16), Ui.dp(QuranActivity.this, 32), 0);
                    break;
                default:
                    v = listRow();
                    break;
            }
            if (v.getLayoutParams() == null) v.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
            Row r = rows.get(p);
            View v = h.itemView;
            switch (r.type) {
                case T_CONTINUE:
                    bindContinue(v);
                    break;
                case T_HERO:
                    bindHero(v);
                    break;
                case T_SECT:
                    ((TextView) v).setText((String) r.data);
                    break;
                case T_AYAH:
                    bindAyah(v, r);
                    break;
                case T_DOWNLOAD:
                    bindDownload(v);
                    break;
                case T_EMPTY:
                    ((TextView) v).setText((String) r.data);
                    break;
                case T_FOOT:
                    break;
                case T_SURAH: {
                    QuranData.Surah s = (QuranData.Surah) r.data;
                    String sub = getString(s.meccan ? R.string.quran_meccan : R.string.quran_medinan) + " \u00B7 "
                            + getString(R.string.quran_ayahs, QuranData.num(s.ayahs));
                    bindRow(v, r, QuranData.num(s.n), d.surahTitle(s.n), sub, QuranData.num(s.page), () -> open(s.page, -1));
                    break;
                }
                case T_JUZ: {
                    int j = (Integer) r.data;
                    int first = d.juzFirst[j];
                    String sub = getString(R.string.quran_starts_at, d.surahShort(d.surahOf[first]) + " " + QuranData.num(d.num[first]));
                    bindRow(v, r, QuranData.num(j), getString(R.string.quran_juz_n, QuranData.num(j)), sub,
                            QuranData.num(d.juzPage[j]), () -> open(d.juzPage[j], -1));
                    break;
                }
                case T_MARK: {
                    int a = (Integer) r.data;
                    String title = d.surahTitle(d.surahOf[a]) + " \u00B7 " + QuranData.num(d.num[a]);
                    String t = d.text[a];
                    String sub = t.length() > 46 ? t.substring(0, 46) + "\u2026" : t;
                    bindRow(v, r, "\u2605", title, sub, QuranData.num(d.page[a]), () -> open(d.page[a], a));
                    v.setOnLongClickListener(x -> {
                        QuranData.toggleMark(QuranActivity.this, a);
                        Ui.toast(QuranActivity.this, getString(R.string.quran_unbookmarked));
                        rebuild();
                        return true;
                    });
                    break;
                }
                default:
                    break;
            }
        }
    }

    // ------------------------------------------------------------------ views

    private View listRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        row.setMinimumHeight(Ui.dp(this, 60));

        TextView badge = new TextView(this);
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(14);
        badge.setTypeface(Ui.medium());
        badge.setTextColor(Ui.color(this, R.color.accent_text));
        badge.setBackground(Ui.rect(Ui.color(this, R.color.accent_soft), Ui.dpf(this, 11)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38));
        bl.setMarginEnd(Ui.dp(this, 14));
        row.addView(badge, bl);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView a = Ui.text(this, "", 18, R.color.text_primary);
        a.setTypeface(Ui.medium());
        a.setSingleLine(true);
        TextView b = Ui.text(this, "", 13, R.color.text_secondary);
        b.setSingleLine(true);
        mid.addView(a);
        mid.addView(b);
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView pg = Ui.text(this, "", 14, R.color.text_secondary);
        pg.setMinWidth(Ui.dp(this, 30));
        pg.setGravity(Gravity.CENTER);
        row.addView(pg, new LinearLayout.LayoutParams(-2, -2));

        wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View hair = Ui.hairline(this, 66);
        wrap.addView(hair);
        wrap.setTag(new TextView[]{badge, a, b, pg});
        return wrap;
    }

    private void bindRow(View v, Row r, String badge, String title, String sub, String page, Runnable click) {
        LinearLayout wrap = (LinearLayout) v;
        TextView[] t = (TextView[]) wrap.getTag();
        t[0].setText(badge);
        t[1].setText(title);
        t[2].setText(sub);
        t[3].setText(page);
        LinearLayout row = (LinearLayout) wrap.getChildAt(0);
        row.setBackground(null);
        wrap.setBackground(Ui.groupShape(this, r.first, r.last, R.color.surface));
        ViewGroup.MarginLayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        wrap.setLayoutParams(lp);
        wrap.getChildAt(1).setVisibility(r.last ? View.INVISIBLE : View.VISIBLE);
        wrap.setOnLongClickListener(null);
        wrap.setOnClickListener(x -> click.run());
        Ui.ripple(this, wrap);
    }

    private View heroCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 16));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1AA37A, 0xFF0B3D4A});
        g.setCornerRadius(Ui.dpf(this, 24));
        c.setBackground(g);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView cap = new TextView(this);
        cap.setTextSize(13);
        cap.setTypeface(Ui.medium());
        cap.setTextColor(0xB3FFFFFF);
        cap.setText(R.string.quran_daily);
        top.addView(cap, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_quran);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(0x80FFFFFF));
        top.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        c.addView(top);
        TextView verse = new TextView(this);
        verse.setTypeface(face);
        verse.setTextSize(24);
        verse.setLineSpacing(0f, 1.45f);
        verse.setTextColor(Color.WHITE);
        verse.setGravity(Gravity.CENTER);
        verse.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        verse.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 10));
        c.addView(verse, new LinearLayout.LayoutParams(-1, -2));
        TextView ref = new TextView(this);
        ref.setTextSize(13);
        ref.setTextColor(0xCCFFFFFF);
        ref.setGravity(Gravity.CENTER);
        ref.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        c.addView(ref, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, Ui.dp(this, 14), 0, 0);
        TextView read = heroButton(R.string.quran_read, 0x33FFFFFF, Color.WHITE);
        TextView tafsir = heroButton(R.string.quran_tafsir, Color.WHITE, 0xFF0B3D4A);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        a.setMarginEnd(Ui.dp(this, 10));
        actions.addView(read, a);
        actions.addView(tafsir, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        c.addView(actions);
        LinearLayout actions2 = new LinearLayout(this);
        actions2.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView share = heroButton(R.string.quran_share, 0x33FFFFFF, Color.WHITE);
        TextView suggest = heroButton(R.string.quran_suggest, 0x33FFFFFF, Color.WHITE);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        b.setMarginEnd(Ui.dp(this, 10));
        actions2.addView(share, b);
        actions2.addView(suggest, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        c.addView(actions2);
        c.setTag(new TextView[]{verse, ref, read, tafsir, share, suggest});
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 12));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView heroButton(int label, int bg, int color) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15);
        t.setTypeface(Ui.medium());
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setBackground(Ui.rect(bg, Ui.dpf(this, 100)));
        Ui.press(this, t);
        return t;
    }

    private void bindHero(View v) {
        TextView[] t = (TextView[]) v.getTag();
        final int a = d.dailyVerse();
        t[0].setText("\uFD3F" + d.text[a] + "\uFD3E");
        t[1].setText(d.surahTitle(d.surahOf[a]) + " \u00B7 " + QuranData.num(d.num[a]));
        t[2].setOnClickListener(x -> open(d.page[a], a));
        t[3].setOnClickListener(x -> openTafsir(a));
        t[4].setOnClickListener(x -> ShareCard.show(this, d.surahShort(d.surahOf[a]), QuranData.num(d.num[a]), d.text[a],
                "\uFD3F" + d.text[a] + "\uFD3E [" + d.surahShort(d.surahOf[a]) + ": " + QuranData.num(d.num[a]) + "]"));
        t[5].setOnClickListener(x -> suggestFlow(v));
    }

    /** "Suggest a verse": the person picks what they need, or writes how they feel, and a verse about it becomes the verse of the day. */
    private void suggestFlow(final View hero) {
        if (d == null) return;
        final int n = QuranSmart.TOPICS.length;
        CharSequence[] items = new CharSequence[n + 1];
        for (int i = 0; i < n; i++) items[i] = QuranSmart.TOPICS[i].label();
        items[n] = getString(R.string.quran_suggest_write);
        new Dlg(this).setTitle(R.string.quran_suggest_title).setItems(items, (dlg, w) -> {
            if (w < n) runSuggest(hero, w, null);
            else askFeeling(hero);
        }).show();
    }

    private void askFeeling(final View hero) {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setHint(R.string.quran_suggest_hint);
        in.setTextColor(Ui.color(this, R.color.text_primary));
        in.setHintTextColor(Ui.color(this, R.color.text_secondary));
        in.setMaxLines(3);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), Ui.dp(this, 4));
        box.addView(in, new android.widget.FrameLayout.LayoutParams(-1, -2));
        new Dlg(this).setTitle(R.string.quran_suggest_write).setView(box)
                .setPositiveButton(R.string.quran_suggest, (dlg, w) -> runSuggest(hero, -1, in.getText().toString()))
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void runSuggest(final View hero, int topic, String free) {
        if (d == null || (topic < 0 && (free == null || free.trim().isEmpty()))) return;
        Ui.toast(this, getString(R.string.quran_suggest_busy));
        QuranSmart.pick(this, d, topic, free, (ayah, label) -> {
            if (isFinishing() || isDestroyed()) return;
            if (ayah < 0) {
                Ui.toast(this, getString(R.string.quran_suggest_none));
                return;
            }
            d.setDailyVerse(this, ayah);
            bindHero(hero);
            TextView[] t = (TextView[]) hero.getTag();
            t[0].setAlpha(0f);
            t[0].animate().alpha(1f).setDuration(Anim.D_MED).start();
            Ui.toast(this, getString(R.string.quran_suggest_topic, label));
        });
    }

    private void openTafsir(int ayah) {
        startActivity(new Intent(this, QuranTafsirActivity.class).putExtra(QuranTafsirActivity.EXTRA_AYAH, ayah));
    }

    private View ayahRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView ref = Ui.text(this, "", 13, R.color.accent_text);
        ref.setTypeface(Ui.medium());
        ref.setSingleLine(true);
        TextView txt = new TextView(this);
        txt.setTypeface(face);
        txt.setTextSize(19);
        txt.setLineSpacing(0f, 1.35f);
        txt.setMaxLines(2);
        txt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.setTextColor(Ui.color(this, R.color.text_primary));
        txt.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        txt.setPadding(0, Ui.dp(this, 4), 0, 0);
        mid.addView(ref);
        mid.addView(txt);
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView tf = Ui.text(this, getString(R.string.quran_tafsir), 14, R.color.accent_text);
        tf.setTypeface(Ui.medium());
        tf.setGravity(Gravity.CENTER);
        tf.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tf.setSingleLine(true);
        tf.setBackground(Ui.rect(Ui.color(this, R.color.accent_soft), Ui.dpf(this, 100)));
        tf.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        Ui.press(this, tf);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(Ui.dp(this, 10));
        row.addView(tf, tl);
        wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
        wrap.addView(Ui.hairline(this, 16));
        wrap.setTag(new TextView[]{ref, txt, tf});
        return wrap;
    }

    private void bindAyah(View v, Row r) {
        final int a = (Integer) r.data;
        LinearLayout wrap = (LinearLayout) v;
        TextView[] t = (TextView[]) wrap.getTag();
        t[0].setText(d.surahTitle(d.surahOf[a]) + " \u00B7 " + QuranData.num(d.num[a]) + " \u00B7 "
                + getString(R.string.quran_page, QuranData.num(d.page[a])));
        t[1].setText(d.text[a]);
        t[2].setOnClickListener(x -> openTafsir(a));
        wrap.setBackground(Ui.groupShape(this, r.first, r.last, R.color.surface));
        ViewGroup.MarginLayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        wrap.setLayoutParams(lp);
        wrap.getChildAt(1).setVisibility(r.last ? View.INVISIBLE : View.VISIBLE);
        wrap.setOnClickListener(x -> open(d.page[a], a));
        wrap.setOnLongClickListener(x -> {
            openTafsir(a);
            return true;
        });
        Ui.ripple(this, wrap);
    }

    private View continueCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF12906B, 0xFF0B3D4A});
        g.setCornerRadius(Ui.dpf(this, 18));
        c.setBackground(g);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView cap = new TextView(this);
        cap.setTextSize(13);
        cap.setTextColor(0xB3FFFFFF);
        cap.setText(R.string.quran_continue);
        TextView name = new TextView(this);
        name.setTextSize(24);
        name.setTypeface(Ui.medium());
        name.setTextColor(Color.WHITE);
        name.setPadding(0, Ui.dp(this, 4), 0, 0);
        TextView pg = new TextView(this);
        pg.setTextSize(14);
        pg.setTextColor(0xCCFFFFFF);
        col.addView(cap);
        col.addView(name);
        col.addView(pg);
        c.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_quran);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(0x66FFFFFF));
        c.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
        c.setTag(new TextView[]{name, pg});
        Ui.press(this, c);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 14));
        c.setLayoutParams(lp);
        return c;
    }

    private void bindContinue(View v) {
        TextView[] t = (TextView[]) v.getTag();
        int p = QuranData.lastPage(this);
        t[0].setText(QuranData.lastLabel(this));
        t[1].setText(getString(R.string.quran_page, QuranData.num(p)));
        v.setOnClickListener(x -> open(p, -1));
    }

    private View downloadCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 18)));
        c.setPadding(Ui.dp(this, 22), Ui.dp(this, 28), Ui.dp(this, 22), Ui.dp(this, 24));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_quran);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        c.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        TextView t = Ui.text(this, getString(R.string.quran_download_title), 22, R.color.text_primary);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 12), 0, 0);
        c.addView(t);
        TextView s = Ui.text(this, getString(R.string.quran_download_sub), 15, R.color.text_secondary);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 18));
        c.addView(s);
        TextView btn = new TextView(this);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(16);
        btn.setTypeface(Ui.medium());
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundResource(R.drawable.btn_primary);
        Ui.press(this, btn);
        c.addView(btn, new LinearLayout.LayoutParams(-1, Ui.dp(this, 50)));
        TextView err = Ui.text(this, getString(R.string.quran_download_fail), 13, R.color.bad);
        err.setGravity(Gravity.CENTER);
        err.setPadding(0, Ui.dp(this, 10), 0, 0);
        c.addView(err);
        c.setTag(new TextView[]{btn, err});
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        c.setLayoutParams(lp);
        return c;
    }

    private void bindDownload(View v) {
        TextView[] t = (TextView[]) v.getTag();
        t[0].setText(downloading ? R.string.quran_downloading : failed ? R.string.quran_retry : R.string.quran_download_btn);
        t[0].setAlpha(downloading ? 0.55f : 1f);
        t[0].setOnClickListener(x -> startDownload());
        t[1].setVisibility(failed && !downloading ? View.VISIBLE : View.GONE);
    }
}
