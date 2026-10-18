package com.clock.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;

public class WidgetPrayer extends WBase {
    @Override
    int layout() {
        return R.layout.widget_prayer;
    }

    @Override
    Intent target(Context c) {
        return new Intent(c, PrayerActivity.class);
    }

    @Override
    void fill(Context c, RemoteViews rv) {
        PrayerTimes.Next n = null;
        try {
            n = PrayerTimes.next(c);
        } catch (Exception ignored) {
        }
        if (n == null) {
            rv.setTextViewText(R.id.wLabel, c.getString(R.string.widget_next_prayer));
            rv.setTextViewText(R.id.wName, c.getString(R.string.prayer_times));
            rv.setTextViewText(R.id.wAt, c.getString(R.string.widget_prayer_setup));
            rv.setChronometer(R.id.wCount, SystemClock.elapsedRealtime(), null, false);
            return;
        }
        long now = System.currentTimeMillis();
        PrayerTimes.Cfg g = PrayerTimes.cfg(c);
        rv.setTextViewText(R.id.wLabel, c.getString(R.string.widget_next_prayer));
        rv.setTextViewText(R.id.wName, c.getResources().getStringArray(R.array.prayer_names)[n.idx]);
        rv.setTextViewText(R.id.wAt, PrayerTimes.label(c, n.at, g.zone));
        rv.setChronometerCountDown(R.id.wCount, true);
        rv.setChronometer(R.id.wCount, SystemClock.elapsedRealtime() + (n.at - now), null, true);
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, WidgetPrayer.class).setAction(REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(c, 77, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            am.setAndAllowWhileIdle(AlarmManager.RTC, n.at + 1500, pi);
        } catch (Exception ignored) {
        }
    }
}
