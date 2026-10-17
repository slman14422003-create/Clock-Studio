package com.clock.app;

import android.content.Context;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Iterator;
import java.util.TimeZone;

/** Which of the five prayers were marked as prayed on which day (kept for about ten weeks, on the device only). */
final class PrayerLog {
    private PrayerLog() {
    }

    private static final String KEY = "prayer_log";

    static int pos(int prayerIdx) {
        for (int i = 0; i < PrayerTimes.PRAYERS.length; i++) if (PrayerTimes.PRAYERS[i] == prayerIdx) return i;
        return -1;
    }

    private static JSONObject load(Context c) {
        try {
            String raw = Store.raw(c, KEY);
            return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static int mask(Context c, String ymd) {
        return load(c).optInt(ymd, 0) & 31;
    }

    static boolean done(Context c, String ymd, int pos) {
        return (mask(c, ymd) & (1 << pos)) != 0;
    }

    static int count(int mask) {
        return Integer.bitCount(mask & 31);
    }

    static void set(Context c, String ymd, int pos, boolean on) {
        if (pos < 0) return;
        JSONObject o = load(c);
        int m = o.optInt(ymd, 0);
        m = on ? (m | (1 << pos)) : (m & ~(1 << pos));
        try {
            if (m == 0) o.remove(ymd);
            else o.put(ymd, m);
            String oldest = shift(ymd, -70);
            Iterator<String> it = o.keys();
            java.util.List<String> drop = new java.util.ArrayList<>();
            while (it.hasNext()) {
                String k = it.next();
                if (k.compareTo(oldest) < 0) drop.add(k);
            }
            for (String k : drop) o.remove(k);
        } catch (Exception ignored) {
        }
        Store.putRaw(c, KEY, o.toString());
    }

    static void markToday(Context c, int prayerIdx, boolean on) {
        PrayerTimes.Cfg g = PrayerTimes.cfg(c);
        set(c, PrayerTimes.today(g), pos(prayerIdx), on);
    }

    static String shift(String ymd, int days) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(5, 7)) - 1, Integer.parseInt(ymd.substring(8, 10)));
        cal.add(Calendar.DAY_OF_YEAR, days);
        return PrayerTimes.ymd(cal);
    }

    /** Weekday (Calendar.SUNDAY..) of a yyyy-MM-dd string. */
    static int weekday(String ymd) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(5, 7)) - 1, Integer.parseInt(ymd.substring(8, 10)));
        return cal.get(Calendar.DAY_OF_WEEK);
    }

    /** Days in a row with all five prayers marked; today only counts once it is complete. */
    static int streak(Context c, String today) {
        JSONObject o = load(c);
        String d = today;
        if (count(o.optInt(d, 0)) < 5) d = shift(d, -1);
        int s = 0;
        while (count(o.optInt(d, 0)) == 5 && s < 400) {
            s++;
            d = shift(d, -1);
        }
        return s;
    }
}
