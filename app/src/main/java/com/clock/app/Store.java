package com.clock.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

/** All persistent state lives in one SharedPreferences file; lists are stored as JSON arrays. */
final class Store {
    private Store() {
    }

    static final String ALARMS = "alarms", TIMERS = "timers", TASKS = "tasks", CITIES = "cities", RECENTS = "recents", LAPS = "sw_laps";

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("clock", Context.MODE_PRIVATE);
    }

    static String themeMode(Context c) {
        return p(c).getString("theme", Appearance.AUTO);
    }

    static void setThemeMode(Context c, String v) {
        p(c).edit().putString("theme", v).apply();
    }

    static String language(Context c) {
        return p(c).getString("lang", "system");
    }

    static void setLanguage(Context c, String v) {
        p(c).edit().putString("lang", v).apply();
    }

    /** 0 = follow system, 1 = 12 hour, 2 = 24 hour. */
    static int timeFormat(Context c) {
        return p(c).getInt("timefmt", 0);
    }

    static void setTimeFormat(Context c, int v) {
        p(c).edit().putInt("timefmt", v).apply();
    }

    static boolean showSeconds(Context c) {
        return p(c).getBoolean("seconds", true);
    }

    static void setShowSeconds(Context c, boolean v) {
        p(c).edit().putBoolean("seconds", v).apply();
    }

    static int snoozeMin(Context c) {
        return p(c).getInt("snooze", 9);
    }

    static void setSnoozeMin(Context c, int v) {
        p(c).edit().putInt("snooze", v).apply();
    }

    static int silenceMin(Context c) {
        return p(c).getInt("silence", 10);
    }

    static void setSilenceMin(Context c, int v) {
        p(c).edit().putInt("silence", v).apply();
    }

    static boolean defaultRamp(Context c) {
        return p(c).getBoolean("def_ramp", true);
    }

    static void setDefaultRamp(Context c, boolean v) {
        p(c).edit().putBoolean("def_ramp", v).apply();
    }

    static boolean defaultVibrate(Context c) {
        return p(c).getBoolean("def_vibrate", true);
    }

    static void setDefaultVibrate(Context c, boolean v) {
        p(c).edit().putBoolean("def_vibrate", v).apply();
    }

    static boolean askedNotifications(Context c) {
        return p(c).getBoolean("asked_notif", false);
    }

    static void setAskedNotifications(Context c) {
        p(c).edit().putBoolean("asked_notif", true).apply();
    }

    static boolean seeded(Context c) {
        return p(c).getBoolean("seeded", false);
    }

    static void setSeeded(Context c) {
        p(c).edit().putBoolean("seeded", true).apply();
    }

    static JSONArray array(Context c, String key) {
        try {
            return new JSONArray(p(c).getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void putArray(Context c, String key, JSONArray a) {
        p(c).edit().putString(key, a.toString()).apply();
    }

    static synchronized int nextId(Context c) {
        SharedPreferences sp = p(c);
        int id = sp.getInt("next_id", 1);
        sp.edit().putInt("next_id", id + 1).apply();
        return id;
    }

    // stopwatch state: accumulated time while stopped, start instant (elapsed realtime) while running
    static long swAcc(Context c) {
        return p(c).getLong("sw_acc", 0L);
    }

    static long swStart(Context c) {
        return p(c).getLong("sw_start", 0L);
    }

    static boolean swRunning(Context c) {
        return p(c).getBoolean("sw_run", false);
    }

    static void setStopwatch(Context c, long acc, long start, boolean running) {
        p(c).edit().putLong("sw_acc", acc).putLong("sw_start", start).putBoolean("sw_run", running).apply();
    }
}
