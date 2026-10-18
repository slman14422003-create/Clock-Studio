package com.clock.app;

import android.app.AlarmManager;
import android.content.Context;
import android.widget.RemoteViews;

import java.util.TimeZone;

public class WidgetAlarm extends WBase {
    @Override
    int layout() {
        return R.layout.widget_alarm;
    }

    @Override
    void fill(Context c, RemoteViews rv) {
        rv.setTextViewText(R.id.wLabel, c.getString(R.string.widget_next_alarm));
        String time = "--:--";
        String day = c.getString(R.string.no_alarm);
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am.getNextAlarmClock();
            if (info != null) {
                long t = info.getTriggerTime();
                time = Fmt.time(c, t, TimeZone.getDefault(), false);
                day = Fmt.dayLabel(c, t);
            }
        } catch (Exception ignored) {
        }
        rv.setTextViewText(R.id.wName, time);
        rv.setTextViewText(R.id.wAt, day);
    }
}
