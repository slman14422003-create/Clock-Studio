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

    /** All alarms are paused until this instant (0 = not paused). */
    static long pauseUntil(Context c) {
        return p(c).getLong("alarm_pause", 0L);
    }

    static void setPauseUntil(Context c, long v) {
        p(c).edit().putLong("alarm_pause", v).apply();
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

    static String raw(Context c, String key) {
        return p(c).getString(key, "");
    }

    static void putRaw(Context c, String key, String v) {
        p(c).edit().putString(key, v).apply();
    }

    static boolean haptics(Context c) {
        return p(c).getBoolean("haptics", true);
    }

    static void setHaptics(Context c, boolean v) {
        p(c).edit().putBoolean("haptics", v).apply();
    }

    /** Liquid Glass look (blur, translucent surfaces, live backdrop). */
    static boolean glass(Context c) {
        return p(c).getBoolean("glass", true);
    }

    static void setGlass(Context c, boolean v) {
        p(c).edit().putBoolean("glass", v).apply();
    }

    /** Blur strength: 0 soft, 1 normal, 2 strong. */
    static int glassLevel(Context c) {
        return p(c).getInt("glass_level", 1);
    }

    static void setGlassLevel(Context c, int v) {
        p(c).edit().putInt("glass_level", v).apply();
    }

    /** 0 = digital, 1 = analog, 2 = night. */
    static int shareTheme(Context c) {
        return Math.max(0, p(c).getInt("share_theme", 0));
    }

    static void setShareTheme(Context c, int v) {
        p(c).edit().putInt("share_theme", v).apply();
    }

    static int shareTpl(Context c) {
        return Math.max(0, p(c).getInt("share_tpl", 0));
    }

    static void setShareTpl(Context c, int v) {
        p(c).edit().putInt("share_tpl", v).apply();
    }

    static boolean standbyAuto(Context c) {
        return p(c).getBoolean("standby_auto", false);
    }

    static void setStandbyAuto(Context c, boolean v) {
        p(c).edit().putBoolean("standby_auto", v).apply();
    }

    static boolean standbyLock(Context c) {
        return p(c).getBoolean("standby_lock", true);
    }

    static void setStandbyLock(Context c, boolean v) {
        p(c).edit().putBoolean("standby_lock", v).apply();
    }

    static boolean standbyLandscape(Context c) {
        return p(c).getBoolean("standby_land", true);
    }

    static void setStandbyLandscape(Context c, boolean v) {
        p(c).edit().putBoolean("standby_land", v).apply();
    }

    static boolean standbyAutoNight(Context c) {
        return p(c).getBoolean("standby_autonight", true);
    }

    static void setStandbyAutoNight(Context c, boolean v) {
        p(c).edit().putBoolean("standby_autonight", v).apply();
    }

    static int standbyColor(Context c) {
        return Math.max(0, p(c).getInt("standby_color", 0));
    }

    static void setStandbyColor(Context c, int v) {
        p(c).edit().putInt("standby_color", v).apply();
    }

    static int standbyStyle(Context c) {
        return Math.max(0, Math.min(StandByView.COUNT - 1, p(c).getInt("standby_style", 0)));
    }

    static void setStandbyStyle(Context c, int v) {
        p(c).edit().putInt("standby_style", v).apply();
    }

    static boolean stopwatchAnalog(Context c) {
        return p(c).getBoolean("sw_analog", false);
    }

    static void setStopwatchAnalog(Context c, boolean v) {
        p(c).edit().putBoolean("sw_analog", v).apply();
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
