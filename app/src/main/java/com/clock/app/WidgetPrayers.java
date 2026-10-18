package com.clock.app;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class WidgetPrayers extends WBase {
    private static final int[] NAMES = {R.id.wN0, R.id.wN1, R.id.wN2, R.id.wN3, R.id.wN4};
    private static final int[] TIMES = {R.id.wH0, R.id.wH1, R.id.wH2, R.id.wH3, R.id.wH4};
    private static final int[] CELLS = {R.id.wC0, R.id.wC1, R.id.wC2, R.id.wC3, R.id.wC4};

    @Override
    int layout(boolean rtl, WCfg cfg) {
        return rtl ? R.layout.wl_prayers_rtl : R.layout.wl_prayers_ltr;
    }

    @Override
    int defaultBg() {
        return 2;
    }

    @Override
    int caps() {
        return TEXT | ACCENT | SIZE | FMT;
    }

    @Override
    Intent target(Context c) {
        return new Intent(c, PrayerActivity.class);
    }

    @Override
    int defaultAccent(WCfg cfg) {
        if (cfg.adaptive()) return 0xFF0A84FF;
        return cfg.autoText() == 0xFFFFFFFF ? 0xFFE8C468 : 0xFF0A84FF;
    }

    @Override
    int[] primary() {
        return TIMES;
    }

    @Override
    int[] secondary() {
        return new int[]{R.id.wS1, R.id.wS2, R.id.wN0, R.id.wN1, R.id.wN2, R.id.wN3, R.id.wN4};
    }

    @Override
    int[][] sizes() {
        return new int[][]{{R.id.wS1, 13}, {R.id.wS2, 12}, {R.id.wN0, 12}, {R.id.wN1, 12}, {R.id.wN2, 12}, {R.id.wN3, 12},
                {R.id.wN4, 12}, {R.id.wH0, 14}, {R.id.wH1, 14}, {R.id.wH2, 14}, {R.id.wH3, 14}, {R.id.wH4, 14}};
    }

    @Override
    void fill(Context c, RemoteViews rv, WCfg cfg) {
        String[] names = c.getResources().getStringArray(R.array.prayer_names);
        PrayerTimes.Cfg g = PrayerTimes.cfg(c);
        PrayerTimes.Day d = null;
        try {
            if (g.configured) d = PrayerTimes.day(c, g, PrayerTimes.today(g));
        } catch (Exception ignored) {
        }
        rv.setTextViewText(R.id.wS1, g.configured && g.place != null && !g.place.isEmpty() ? g.place : c.getString(R.string.prayer_times));
        rv.setTextViewText(R.id.wS2, d != null && d.hd != null && !d.hd.isEmpty()
                ? c.getString(R.string.hijri_fmt, d.hd, Lang.isAr() ? d.hma : d.hme, d.hy) : "");
        for (int i = 0; i < 5; i++) {
            int idx = PrayerTimes.PRAYERS[i];
            rv.setTextViewText(NAMES[i], names[idx]);
            String t = d == null || d.hm[idx] == null ? "--:--" : PrayerTimes.hm(c, d.hm[idx]);
            if (is24(c, cfg) != Fmt.is24(c) && d != null && d.hm[idx] != null) {
                try {
                    int h = Integer.parseInt(d.hm[idx].substring(0, 2));
                    int m = Integer.parseInt(d.hm[idx].substring(3, 5));
                    t = is24(c, cfg) ? Fmt.two(h) + ":" + Fmt.two(m) : (h % 12 == 0 ? 12 : h % 12) + ":" + Fmt.two(m) + " " + c.getString(h < 12 ? R.string.am : R.string.pm);
                } catch (Exception ignored) {
                }
            }
            rv.setTextViewText(TIMES[i], t);
        }
    }

    @Override
    void post(Context c, RemoteViews rv, WCfg cfg) {
        PrayerTimes.Next nx = null;
        try {
            nx = PrayerTimes.next(c);
        } catch (Exception ignored) {
        }
        int ac = cfg.accentColor() != 0 ? cfg.accentColor() : defaultAccent(cfg);
        for (int i = 0; i < 5; i++) {
            boolean on = nx != null && PrayerTimes.PRAYERS[i] == nx.idx;
            rv.setInt(CELLS[i], "setBackgroundResource", on ? R.drawable.wb_cell_on : R.drawable.wb_cell_off);
        }
        if (nx != null) {
            for (int i = 0; i < 5; i++) {
                if (PrayerTimes.PRAYERS[i] == nx.idx) {
                    rv.setTextColor(NAMES[i], ac);
                    rv.setTextColor(TIMES[i], ac);
                }
            }
        }
    }
}
