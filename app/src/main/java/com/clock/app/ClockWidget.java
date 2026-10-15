package com.clock.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.TimeZone;

/** Home screen widget: big thin time, date and the next alarm. */
public class ClockWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, ClockWidget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews rv = build(c);
            for (int id : ids) m.updateAppWidget(id, rv);
        } catch (Exception ignored) {
        }
    }

    private static RemoteViews build(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
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
        rv.setTextViewText(R.id.wNext, next);
        PendingIntent open = PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.wRoot, open);
        return rv;
    }
}
