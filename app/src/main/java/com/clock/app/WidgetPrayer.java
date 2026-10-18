package com.clock.app;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;

public class WidgetPrayer extends WBase {
    @Override
    int layout(boolean rtl, WCfg cfg) {
        return rtl ? R.layout.wl_prayer_rtl : R.layout.wl_prayer_ltr;
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
        if (cfg.adaptive()) return 0;
        return cfg.autoText() == 0xFFFFFFFF ? 0xFFE8C468 : 0xFF0A84FF;
    }

    @Override
    int[] primary() {
        return new int[]{R.id.wP1, R.id.wP2};
    }

    @Override
    int[] secondary() {
        return new int[]{R.id.wS1, R.id.wS2};
    }

    @Override
    int[] accents() {
        return new int[]{R.id.wA1};
    }

    @Override
    int[][] sizes() {
        return new int[][]{{R.id.wS1, 13}, {R.id.wP1, 32}, {R.id.wA1, 15}, {R.id.wP2, 26}, {R.id.wS2, 12}};
    }

    @Override
    void fill(Context c, RemoteViews rv, WCfg cfg) {
        timeFormat(rv, R.id.wS2, cfg, c, "h:mm a", "HH:mm");
        rv.setTextViewText(R.id.wS1, c.getString(R.string.widget_next_prayer));
        PrayerTimes.Next n = null;
        try {
            n = PrayerTimes.next(c);
        } catch (Exception ignored) {
        }
        if (n == null) {
            rv.setTextViewText(R.id.wP1, c.getString(R.string.prayer_times));
            rv.setTextViewText(R.id.wA1, c.getString(R.string.widget_prayer_setup));
            rv.setChronometer(R.id.wP2, SystemClock.elapsedRealtime(), null, false);
            return;
        }
        long now = System.currentTimeMillis();
        PrayerTimes.Cfg g = PrayerTimes.cfg(c);
        rv.setTextViewText(R.id.wP1, c.getResources().getStringArray(R.array.prayer_names)[n.idx]);
        rv.setTextViewText(R.id.wA1, PrayerTimes.label(c, n.at, g.zone));
        rv.setChronometerCountDown(R.id.wP2, true);
        rv.setChronometer(R.id.wP2, SystemClock.elapsedRealtime() + (n.at - now), null, true);
    }
}
