package com.clock.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

/** AlarmManager wrapper. Every scheduled item is identified by (kind, id) through the intent data. */
final class Sched {
    private Sched() {
    }

    static final String ACTION_FIRE = "app.clock.action.FIRE";
    static final String KIND_ALARM = "alarm", KIND_SNOOZE = "snooze", KIND_TIMER = "timer", KIND_TASK = "task", KIND_BEDTIME = "bedtime", KIND_PRAYER = "prayer";

    private static AlarmManager am(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent fire(Context c, String kind, int id, int flags) {
        Intent i = new Intent(c, ClockReceiver.class).setAction(ACTION_FIRE)
                .setData(Uri.parse("clock://" + kind + "/" + id))
                .putExtra("kind", kind).putExtra("id", id);
        return PendingIntent.getBroadcast(c, 0, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Alarm-clock grade trigger: exact, shown as the next alarm by the system, wakes the device from Doze. */
    static void alarmClock(Context c, long at, String kind, int id) {
        PendingIntent show = PendingIntent.getActivity(c, 1,
                new Intent(c, MainActivity.class).putExtra("tab", NavBar.ALARMS)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am(c).setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), fire(c, kind, id, PendingIntent.FLAG_UPDATE_CURRENT));
        } catch (SecurityException e) {
            am(c).set(AlarmManager.RTC_WAKEUP, at, fire(c, kind, id, PendingIntent.FLAG_UPDATE_CURRENT));
        }
    }

    /** Exact trigger for timers and task reminders (falls back to inexact when exact alarms are refused). */
    static void exact(Context c, long at, String kind, int id) {
        AlarmManager am = am(c);
        PendingIntent pi = fire(c, kind, id, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static void cancel(Context c, String kind, int id) {
        PendingIntent pi = fire(c, kind, id, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            am(c).cancel(pi);
            pi.cancel();
        }
    }
}
