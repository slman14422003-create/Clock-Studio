package com.clock.app;

import android.content.Context;

import org.json.JSONObject;

import java.util.Calendar;

/**
 * Sleep schedule (iPhone "Sleep" + Samsung "Bedtime"): a wake-up time, a sleep goal and a wind-down reminder.
 * The wake-up time is backed by a normal alarm so it rings, snoozes and shows up everywhere like any other alarm.
 */
final class Bedtime {
    private static final String KEY = "bedtime";
    private static final int REMINDER_ID = 1;

    boolean configured;
    boolean enabled;
    int wakeHour = 7;
    int wakeMinute;
    int goalMin = 480;
    int days = Alarm.EVERY_DAY;
    int remindMin = 30;
    int alarmId;

    static Bedtime load(Context c) {
        Bedtime b = new Bedtime();
        String raw = Store.raw(c, KEY);
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONObject o = new JSONObject(raw);
                b.configured = o.optBoolean("cf", false);
                b.enabled = o.optBoolean("on", false);
                b.wakeHour = o.optInt("wh", 7);
                b.wakeMinute = o.optInt("wm", 0);
                b.goalMin = o.optInt("g", 480);
                b.days = o.optInt("d", Alarm.EVERY_DAY);
                b.remindMin = o.optInt("r", 30);
                b.alarmId = o.optInt("a", 0);
            } catch (Exception ignored) {
            }
        }
        if (b.configured && b.alarmId != 0) {
            Alarm a = Alarms.get(c, b.alarmId);
            if (a != null) {
                b.wakeHour = a.hour;
                b.wakeMinute = a.minute;
                if (a.days != 0) b.days = a.days;
                b.enabled = a.enabled;
            } else {
                b.enabled = false;
            }
        }
        if (b.days == 0) b.days = Alarm.EVERY_DAY;
        return b;
    }

    private void write(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("cf", configured).put("on", enabled).put("wh", wakeHour).put("wm", wakeMinute)
                    .put("g", goalMin).put("d", days).put("r", remindMin).put("a", alarmId);
            Store.putRaw(c, KEY, o.toString());
        } catch (Exception ignored) {
        }
    }

    int bedMinutes() {
        int m = wakeHour * 60 + wakeMinute - goalMin;
        return ((m % 1440) + 1440) % 1440;
    }

    int bedHour() {
        return bedMinutes() / 60;
    }

    int bedMinute() {
        return bedMinutes() % 60;
    }

    static String label(Context c, int h, int m) {
        String ap = Fmt.ampm(c, h);
        String t = Fmt.hm(c, h, m);
        return ap.isEmpty() ? t : t + " " + ap;
    }

    /** Next wind-down reminder strictly after {@code now}, 0 if there is none. */
    long nextReminder(long now) {
        if (!configured || !enabled || remindMin <= 0) return 0;
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < 9; i++) {
            c.setTimeInMillis(now);
            c.add(Calendar.DAY_OF_YEAR, i);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.set(Calendar.HOUR_OF_DAY, bedHour());
            c.set(Calendar.MINUTE, bedMinute());
            c.add(Calendar.MINUTE, -remindMin);
            long t = c.getTimeInMillis();
            if (t <= now) continue;
            Calendar wake = Calendar.getInstance();
            wake.setTimeInMillis(t + (remindMin + goalMin) * 60000L);
            if ((days & (1 << (wake.get(Calendar.DAY_OF_WEEK) - 1))) != 0) return t;
        }
        return 0;
    }

    static void reschedule(Context c) {
        Sched.cancel(c, Sched.KIND_BEDTIME, REMINDER_ID);
        long t = load(c).nextReminder(System.currentTimeMillis());
        if (t > 0) Sched.exact(c, t, Sched.KIND_BEDTIME, REMINDER_ID);
    }

    /** Stores the schedule and creates / updates the linked wake-up alarm and the reminder. */
    static void apply(Context c, Bedtime b) {
        if (b.configured) {
            Alarm a = b.alarmId == 0 ? null : Alarms.get(c, b.alarmId);
            if (a == null) {
                a = new Alarm();
                a.vibrate = Store.defaultVibrate(c);
                a.ramp = true;
            }
            a.hour = b.wakeHour;
            a.minute = b.wakeMinute;
            a.days = b.days;
            a.label = c.getString(R.string.wake_up);
            a.enabled = b.enabled;
            a.skipUntil = 0;
            Alarms.save(c, a);
            b.alarmId = a.id;
        }
        b.write(c);
        reschedule(c);
        Bus.changed();
    }

    static void setEnabled(Context c, boolean on) {
        Bedtime b = load(c);
        if (!b.configured) return;
        b.enabled = on;
        apply(c, b);
    }

    static void remove(Context c) {
        Bedtime b = load(c);
        if (b.alarmId != 0) Alarms.delete(c, b.alarmId);
        Store.putRaw(c, KEY, "");
        Sched.cancel(c, Sched.KIND_BEDTIME, REMINDER_ID);
        Notif.cancelBedtime(c);
        Bus.changed();
    }
}
