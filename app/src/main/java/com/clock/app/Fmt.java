package com.clock.app;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Time, duration and date formatting. Digits stay Latin so big clock faces line up. */
final class Fmt {
    private Fmt() {
    }

    static Locale locale(Context c) {
        return c.getResources().getConfiguration().getLocales().get(0);
    }

    static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    static boolean is24(Context c) {
        int m = Store.timeFormat(c);
        if (m == 1) return false;
        if (m == 2) return true;
        return DateFormat.is24HourFormat(c);
    }

    /** "7:30" in 12 hour mode, "07:30" in 24 hour mode. */
    static String hm(Context c, int h, int m) {
        if (is24(c)) return two(h) + ":" + two(m);
        int hh = h % 12;
        if (hh == 0) hh = 12;
        return hh + ":" + two(m);
    }

    static String ampm(Context c, int h) {
        if (is24(c)) return "";
        return c.getString(h < 12 ? R.string.am : R.string.pm);
    }

    static String time(Context c, long ms, TimeZone tz, boolean seconds) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(ms);
        String s = hm(c, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        if (seconds) s += ":" + two(cal.get(Calendar.SECOND));
        return s;
    }

    /** Countdown text: m:ss under an hour, h:mm:ss above. Rounds up so 0:00 only shows at the end. */
    static String countdown(long ms) {
        long s = (ms + 999) / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return h + ":" + two((int) m) + ":" + two((int) sec);
        return two((int) m) + ":" + two((int) sec);
    }

    static String stopwatch(long ms) {
        long cs = (ms / 10) % 100;
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        String base = two((int) m) + ":" + two((int) sec) + "." + two((int) cs);
        return h > 0 ? h + ":" + base : base;
    }

    /** "1 h 30 min" style text. */
    static String spoken(Context c, long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        StringBuilder b = new StringBuilder();
        if (h > 0) b.append(h).append(' ').append(c.getString(R.string.unit_h));
        if (m > 0) b.append(b.length() > 0 ? " " : "").append(m).append(' ').append(c.getString(R.string.unit_m));
        if (sec > 0 || b.length() == 0) b.append(b.length() > 0 ? " " : "").append(sec).append(' ').append(c.getString(R.string.unit_s));
        return b.toString();
    }

    /** "in 7 h 20 min" for the next-alarm line. */
    static String untilText(Context c, long diffMs) {
        long min = Math.max(1, (diffMs + 59999) / 60000);
        long d = min / 1440;
        long h = (min % 1440) / 60;
        long m = min % 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append(' ').append(c.getString(R.string.unit_d));
        if (h > 0) b.append(b.length() > 0 ? " " : "").append(h).append(' ').append(c.getString(R.string.unit_h));
        if (m > 0 && d == 0) b.append(b.length() > 0 ? " " : "").append(m).append(' ').append(c.getString(R.string.unit_m));
        return c.getString(R.string.in_fmt, b.toString());
    }

    static String dayName(Context c, int dow) {
        String[] names = new DateFormatSymbols(locale(c)).getShortWeekdays();
        return names[dow];
    }

    /** Summary of an alarm's repeat mask. */
    static String repeat(Context c, int days) {
        if (days == 0) return c.getString(R.string.once);
        if (days == Alarm.EVERY_DAY) return c.getString(R.string.every_day);
        if (days == Alarm.SUN_THU) return c.getString(R.string.sun_thu);
        if (days == Alarm.MON_FRI) return c.getString(R.string.mon_fri);
        StringBuilder b = new StringBuilder();
        int first = Calendar.getInstance().getFirstDayOfWeek();
        for (int i = 0; i < 7; i++) {
            int dow = ((first - 1 + i) % 7) + 1;
            if ((days & (1 << (dow - 1))) != 0) {
                if (b.length() > 0) b.append(c.getString(R.string.list_sep)).append(' ');
                b.append(dayName(c, dow));
            }
        }
        return b.toString();
    }

    static String date(Context c, long ms, String pattern) {
        return new SimpleDateFormat(pattern, locale(c)).format(new Date(ms));
    }

    static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static long startOfDay(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** "Today", "Tomorrow", "Yesterday" or "Wed, 14 Oct". */
    static String dayLabel(Context c, long ms) {
        long today = startOfDay(System.currentTimeMillis());
        long day = startOfDay(ms);
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(today);
        a.add(Calendar.DAY_OF_YEAR, 1);
        if (day == today) return c.getString(R.string.today);
        if (day == a.getTimeInMillis()) return c.getString(R.string.tomorrow);
        a.add(Calendar.DAY_OF_YEAR, -2);
        if (day == a.getTimeInMillis()) return c.getString(R.string.yesterday);
        return date(c, ms, "EEE d MMM");
    }

    static String taskDue(Context c, Task t) {
        if (t.due <= 0) return "";
        String d = dayLabel(c, t.due);
        if (!t.hasTime) return d;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t.due);
        String tm = hm(c, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        String ap = ampm(c, cal.get(Calendar.HOUR_OF_DAY));
        return d + " " + tm + (ap.isEmpty() ? "" : " " + ap);
    }

    /** World clock subtitle: "Today, +3 h". */
    static String zoneLine(Context c, TimeZone tz, long now) {
        TimeZone local = TimeZone.getDefault();
        Calendar there = Calendar.getInstance(tz);
        there.setTimeInMillis(now);
        Calendar here = Calendar.getInstance(local);
        here.setTimeInMillis(now);
        String day;
        if (sameDay(there, here)) {
            day = c.getString(R.string.today);
        } else {
            int cmp = there.get(Calendar.YEAR) != here.get(Calendar.YEAR)
                    ? there.get(Calendar.YEAR) - here.get(Calendar.YEAR)
                    : there.get(Calendar.DAY_OF_YEAR) - here.get(Calendar.DAY_OF_YEAR);
            day = c.getString(cmp > 0 ? R.string.tomorrow : R.string.yesterday);
        }
        int diffMin = (tz.getOffset(now) - local.getOffset(now)) / 60000;
        if (diffMin == 0) return day + c.getString(R.string.list_sep) + " " + c.getString(R.string.same_time);
        int a = Math.abs(diffMin);
        String amount = a % 60 == 0 ? String.valueOf(a / 60) : (a / 60) + ":" + two(a % 60);
        return day + c.getString(R.string.list_sep) + " " + (diffMin > 0 ? "+" : "\u2212") + amount + " " + c.getString(R.string.unit_h);
    }

    static boolean isDay(TimeZone tz, long now) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(now);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= 6 && h < 18;
    }
}
