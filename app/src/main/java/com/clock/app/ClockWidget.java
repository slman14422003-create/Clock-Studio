package com.clock.app;

import android.app.AlarmManager;
import android.content.Context;
import android.widget.RemoteViews;

import java.util.TimeZone;

public class ClockWidget extends WBase {
    static void refresh(Context c) {
        WBase.refresh(c, ClockWidget.class);
    }

    @Override
    int layout(boolean rtl, WCfg cfg) {
        return rtl ? R.layout.wl_classic_rtl : R.layout.wl_classic_ltr;
    }

    @Override
    int[] primary() {
        return new int[]{R.id.wP1};
    }

    @Override
    int[] secondary() {
        return new int[]{R.id.wS2};
    }

    @Override
    int[] accents() {
        return new int[]{R.id.wA1};
    }

    @Override
    int[][] sizes() {
        return new int[][]{{R.id.wP1, 50}, {R.id.wA1, 14}, {R.id.wS2, 13}};
    }

    @Override
    void tint(RemoteViews rv, int color) {
        rv.setInt(R.id.wIcon, "setColorFilter", color);
    }

    @Override
    void fill(Context c, RemoteViews rv, WCfg cfg) {
        timeFormat(rv, R.id.wP1, cfg, c, "h:mm", "HH:mm");
        rv.setViewVisibility(R.id.wA1, cfg.date ? android.view.View.VISIBLE : android.view.View.GONE);
        rv.setTextViewText(R.id.wA1, Fmt.date(c, System.currentTimeMillis(), "EEEE d MMMM"));
        String next = c.getString(R.string.no_alarm);
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am.getNextAlarmClock();
            if (info != null) {
                long t = info.getTriggerTime();
                next = Fmt.dayLabel(c, t) + " " + Fmt.time(c, t, TimeZone.getDefault(), false);
            }
        } catch (Exception ignored) {
        }
        rv.setTextViewText(R.id.wS2, next);
    }
}
