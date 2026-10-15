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

    static final int EVERY_DAY = 127;
    static final int SUN_THU = 1 | 2 | 4 | 8 | 16;
    static final int MON_FRI = 2 | 4 | 8 | 16 | 32;

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("h", hour).put("m", minute).put("d", days).put("l", label).put("on", enabled)
                    .put("v", vibrate).put("sn", snooze).put("r", ramp).put("c", challenge).put("s", sound);
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
        return a;
    }

    /** Next trigger strictly after {@code now}, or 0 if none. */
    long next(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        for (int i = 0; i < 9; i++) {
            if (c.getTimeInMillis() > now) {
                if (days == 0 || (days & (1 << (c.get(Calendar.DAY_OF_WEEK) - 1))) != 0) return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return 0;
    }

    Alarm copy() {
        return fromJson(toJson());
    }
}
