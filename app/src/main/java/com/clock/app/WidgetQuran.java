package com.clock.app;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** "Verse of the day": one short ayah chosen by date, from the cached Quran text. Tapping opens the reader on its page. */
public class WidgetQuran extends WBase {
    @Override
    int layout(boolean rtl, WCfg cfg) {
        return rtl ? R.layout.wl_quran_rtl : R.layout.wl_quran_ltr;
    }

    @Override
    int defaultBg() {
        return 2;
    }

    @Override
    int caps() {
        return TEXT | ACCENT | SIZE;
    }

    @Override
    Intent target(Context c) {
        try {
            QuranData d = QuranData.peek(c);
            if (d != null) {
                int idx = d.dailyVerse();
                return new Intent(c, QuranReaderActivity.class)
                        .putExtra(QuranReaderActivity.EXTRA_PAGE, (int) d.page[idx])
                        .putExtra(QuranReaderActivity.EXTRA_AYAH, idx)
                        .putExtra(QuranReaderActivity.EXTRA_HIGHLIGHT, true);
            }
        } catch (Throwable ignored) {
        }
        return new Intent(c, QuranActivity.class);
    }

    @Override
    int defaultAccent(WCfg cfg) {
        if (cfg.adaptive()) return 0;
        return cfg.autoText() == 0xFFFFFFFF ? 0xFFE8C468 : 0xFF0A84FF;
    }

    @Override
    int[] primary() {
        return new int[]{R.id.wP1};
    }

    @Override
    int[] secondary() {
        return new int[]{R.id.wS1};
    }

    @Override
    int[] accents() {
        return new int[]{R.id.wA1};
    }

    @Override
    int[][] sizes() {
        return new int[][]{{R.id.wS1, 12}, {R.id.wP1, 18}, {R.id.wA1, 13}};
    }

    @Override
    void fill(Context c, RemoteViews rv, WCfg cfg) {
        rv.setTextViewText(R.id.wS1, c.getString(R.string.widget_quran));
        QuranData d = QuranData.peek(c);
        if (d == null) {
            rv.setTextViewText(R.id.wP1, c.getString(R.string.quran_widget_empty));
            rv.setTextViewText(R.id.wA1, "");
            return;
        }
        int i = d.dailyVerse();
        rv.setTextViewText(R.id.wP1, "\uFD3F" + d.text[i] + "\uFD3E");
        rv.setTextViewText(R.id.wA1, d.surahTitle(d.surahOf[i]) + " \u00B7 " + QuranData.ar(d.num[i]));
    }
}
