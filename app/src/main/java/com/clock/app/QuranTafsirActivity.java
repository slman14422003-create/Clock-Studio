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
                body.setText(R.string.quran_tafsir_fail);
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
