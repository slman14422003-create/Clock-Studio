package com.clock.app;

import org.json.JSONObject;

import java.util.Calendar;

final class Alarm {
    int id;
    int hour = 7;
    int minute = 0;
    /** Bit 0 = Sunday ... bit 6 = Saturday (Calendar.DAY_OF_WEEK - 1). 0 means "once". */
    int days;
    String label = "";
    boolean enabled = true;
    boolean vibrate = true;
    boolean snooze = true;
    boolean ramp = true;
    boolean challenge;
    String sound = "";
    /** While in the future, occurrences up to this instant are skipped ("skip next alarm"). */
    long skipUntil;
    /** Per-alarm snooze length in minutes, 0 = use the global default. */
    int snoozeMin;
    /** Ring for at most this many minutes before it is missed, 0 = use the global default. */
    int silenceMin;
    /** Peak alarm volume, 10..100. */
    int volume = 100;
    /** Seconds the volume takes to rise to its peak when the ramp is on. */
    int rampSec = 15;
    /** One-time alarms: remove the alarm once it has rung. */
    boolean deleteAfter;
    /** Colour tag, index into {@link #COLORS}. */
    int color;
    /** One-time alarms: a specific day (any instant of it); 0 = the next occurrence of the time. */
    long date;

    static final int[] COLORS = {0xFF007AFF, 0xFF34C759, 0xFFFF9500, 0xFFFF3B30, 0xFFAF52DE, 0xFFFF2D55};

    static final int EVERY_DAY = 127;
    static final int SUN_THU = 1 | 2 | 4 | 8 | 16;
    static final int MON_FRI = 2 | 4 | 8 | 16 | 32;

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("h", hour).put("m", minute).put("d", days).put("l", label).put("on", enabled)
                    .put("v", vibrate).put("sn", snooze).put("r", ramp).put("c", challenge).put("s", sound).put("sk", skipUntil)
                    .put("sm", snoozeMin).put("si", silenceMin).put("vo", volume).put("rs", rampSec)
                    .put("da", deleteAfter).put("co", color).put("dt", date);
        } catch (Exception ignored) {
        }
        return o;
    }

    static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optInt("id");
        a.hour = o.optInt("h", 7);
        a.minute = o.optInt("m", 0);
        a.days = o.optInt("d", 0);
        a.label = o.optString("l", "");
        a.enabled = o.optBoolean("on", true);
        a.vibrate = o.optBoolean("v", true);
        a.snooze = o.optBoolean("sn", true);
        a.ramp = o.optBoolean("r", true);
        a.challenge = o.optBoolean("c", false);
        a.sound = o.optString("s", "");
        a.skipUntil = o.optLong("sk", 0L);
        a.snoozeMin = o.optInt("sm", 0);
        a.silenceMin = o.optInt("si", 0);
        a.volume = Math.max(10, Math.min(100, o.optInt("vo", 100)));
        a.rampSec = Math.max(5, o.optInt("rs", 15));
        a.deleteAfter = o.optBoolean("da", false);
        a.color = Math.max(0, Math.min(COLORS.length - 1, o.optInt("co", 0)));
        a.date = o.optLong("dt", 0L);
        return a;
    }

    /** Next trigger strictly after {@code now}, or 0 if none. */
    long next(long now) {
        return next(now, 0L);
    }

    /** Like {@link #next(long)} but nothing before {@code floor} counts (global pause). */
    long next(long now, long floor) {
        long gate = Math.max(skipUntil, floor);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (days == 0 && date > 0) {
            c.setTimeInMillis(date);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            long t = c.getTimeInMillis();
            return t > now && t > gate ? t : 0;
        }
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        for (int i = 0; i < 9; i++) {
            if (c.getTimeInMillis() > now && c.getTimeInMillis() > gate) {
                if (days == 0 || (days & (1 << (c.get(Calendar.DAY_OF_WEEK) - 1))) != 0) return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return 0;
    }

    /** Next trigger of a repeating alarm that is not the very next one, used for the "skip" preview. */
    boolean once() {
        return days == 0;
    }

    Alarm copy() {
        return fromJson(toJson());
    }
}
