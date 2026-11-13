package com.clock.app;

import android.app.AlarmManager;
import android.content.Context;
import android.widget.RemoteViews;

import java.util.TimeZone;

public class WidgetAlarm extends WBase {
    @Override
    int layout(boolean rtl, WCfg cfg) {
        return rtl ? R.layout.wl_alarm_rtl : R.layout.wl_alarm_ltr;
    }

    @Override
    int defaultBg() {
        return 3;
    }

    @Override
    int caps() {
        return TEXT | ACCENT | SIZE | FMT;
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
        return new int[][]{{R.id.wS1, 13}, {R.id.wP1, 38}, {R.id.wA1, 14}};
    }

    @Override
    void tint(RemoteViews rv, int color) {
        rv.setInt(R.id.wIcon, "setColorFilter", color);
    }

    @Override
    void tintAccent(RemoteViews rv, int color) {
        rv.setInt(R.id.wIcon, "setColorFilter", color);
    }

    @Override
    void fill(Context c, RemoteViews rv, WCfg cfg) {
        rv.setTextViewText(R.id.wS1, c.getString(R.string.widget_next_alarm));
        String time = "--:--";
        String day = c.getString(R.string.no_alarm);
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am.getNextAlarmClock();
            if (info != null) {
                long t = info.getTriggerTime();
                java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getDefault());
                cal.setTimeInMillis(t);
                int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
                int m = cal.get(java.util.Calendar.MINUTE);
                time = is24(c, cfg) ? Fmt.two(h) + ":" + Fmt.two(m) : Bedtime.label(c, h, m);
                day = Fmt.dayLabel(c, t);
            }
        } catch (Exception ignored) {
        }
        rv.setTextViewText(R.id.wP1, time);
        rv.setTextViewText(R.id.wA1, day);
    }
}
