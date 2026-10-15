package com.clock.app;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Prayer times from the Aladhan public API (aladhan.com), booked by the user's region. A whole month is downloaded at
 * once and cached, so the times keep working offline; they refresh when the month, the place or the method changes.
 */
final class PrayerTimes {
    private PrayerTimes() {
    }

    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    static final int[] PRAYERS = {FAJR, DHUHR, ASR, MAGHRIB, ISHA};
    private static final String[] KEYS = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    /** Aladhan method ids; -1 lets the service choose by location. */
    static final int[] METHODS = {-1, 3, 4, 5, 2, 1, 13};
    private static final String CFG = "prayer_cfg", CACHE = "prayer_cache";
    private static final Executor EXEC = Executors.newSingleThreadExecutor();

    interface Done {
        void on(boolean ok);
    }

    static final class Cfg {
        boolean configured;
        boolean auto = true;
        double lat, lon;
        String place = "";
        String zone = TimeZone.getDefault().getID();
        int method = -1;
        boolean hanafi;
        boolean notify;
        long locTs;
    }

    static final class Day {
        final String[] hm = new String[6];
        String hd = "", hma = "", hme = "", hy = "";
    }

    static final class Next {
        int idx;
        long at;
    }

    // ------------------------------------------------------------------ settings

    static Cfg cfg(Context c) {
        Cfg g = new Cfg();
        String raw = Store.raw(c, CFG);
        if (raw == null || raw.isEmpty()) return g;
        try {
            JSONObject o = new JSONObject(raw);
            g.configured = o.optBoolean("cf", false);
            g.auto = o.optBoolean("au", true);
            g.lat = o.optDouble("la", 0);
            g.lon = o.optDouble("lo", 0);
            g.place = o.optString("pl", "");
            g.zone = o.optString("tz", g.zone);
            g.method = o.optInt("m", -1);
            g.hanafi = o.optBoolean("h", false);
            g.notify = o.optBoolean("n", false);
            g.locTs = o.optLong("ts", 0);
        } catch (Exception ignored) {
        }
        return g;
    }

    static void save(Context c, Cfg g) {
        try {
            JSONObject o = new JSONObject();
            o.put("cf", g.configured).put("au", g.auto).put("la", g.lat).put("lo", g.lon).put("pl", g.place)
                    .put("tz", g.zone).put("m", g.method).put("h", g.hanafi).put("n", g.notify).put("ts", g.locTs);
            Store.putRaw(c, CFG, o.toString());
        } catch (Exception ignored) {
        }
    }

    private static String key(Cfg g) {
        return String.format(Locale.US, "%.2f,%.2f,%d,%d,%s", g.lat, g.lon, g.method, g.hanafi ? 1 : 0, g.zone);
    }

    // ------------------------------------------------------------------ cache

    private static String memRaw;
    private static JSONObject memCache = new JSONObject();

    private static synchronized JSONObject loadCache(Context c) {
        String raw = Store.raw(c, CACHE);
        if (raw == null) raw = "";
        if (!raw.equals(memRaw)) {
            try {
                memCache = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            } catch (Exception e) {
                memCache = new JSONObject();
            }
            memRaw = raw;
        }
        return memCache;
    }

    static long lastUpdate(Context c) {
        return loadCache(c).optLong("ts", 0);
    }

    static String ymd(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String today(Cfg g) {
        return ymd(Calendar.getInstance(TimeZone.getTimeZone(g.zone)));
    }

    static Day day(Context c, Cfg g, String ymd) {
        JSONObject cache = loadCache(c);
        if (!key(g).equals(cache.optString("key"))) return null;
        JSONObject days = cache.optJSONObject("days");
        if (days == null) return null;
        JSONArray a = days.optJSONArray(ymd);
        if (a == null || a.length() < 10) return null;
        Day d = new Day();
        for (int i = 0; i < 6; i++) d.hm[i] = a.optString(i);
        d.hd = a.optString(6);
        d.hma = a.optString(7);
        d.hme = a.optString(8);
        d.hy = a.optString(9);
        return d;
    }

    static long timeAt(Cfg g, String ymd, String hm) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(g.zone));
        c.clear();
        c.set(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(5, 7)) - 1,
                Integer.parseInt(ymd.substring(8, 10)), Integer.parseInt(hm.substring(0, 2)),
                Integer.parseInt(hm.substring(3, 5)), 0);
        return c.getTimeInMillis();
    }

    /** The next of the five prayers (sunrise is not one), looking into tomorrow if needed. */
    static Next next(Context c) {
        Cfg g = cfg(c);
        if (!g.configured) return null;
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(g.zone));
        cal.setTimeInMillis(now);
        for (int off = 0; off < 2; off++) {
            String ymd = ymd(cal);
            Day d = day(c, g, ymd);
            if (d != null) {
                for (int i : PRAYERS) {
                    try {
                        long t = timeAt(g, ymd, d.hm[i]);
                        if (t > now) {
                            Next n = new Next();
                            n.idx = i;
                            n.at = t;
                            return n;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return null;
    }

    static String label(Context c, long at, String zone) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(zone));
        cal.setTimeInMillis(at);
        return Bedtime.label(c, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    static String hm(Context c, String hm) {
        try {
            return Bedtime.label(c, Integer.parseInt(hm.substring(0, 2)), Integer.parseInt(hm.substring(3, 5)));
        } catch (Exception e) {
            return hm;
        }
    }

    // ------------------------------------------------------------------ notifications

    static void scheduleNext(Context c) {
        for (int i = 0; i < 6; i++) Sched.cancel(c, Sched.KIND_PRAYER, i);
        Cfg g = cfg(c);
        if (!g.configured || !g.notify) return;
        Next n = next(c);
        if (n != null) Sched.exact(c, n.at, Sched.KIND_PRAYER, n.idx);
    }

    // ------------------------------------------------------------------ online updates

    /** Refreshes the cache when needed (quietly relocates first when the place is automatic). */
    static void autoRefresh(final Context c, final Done cb) {
        final Context app = c.getApplicationContext();
        final Cfg g = cfg(app);
        if (!g.configured) return;
        if (g.auto && PrayerLoc.has(app) && System.currentTimeMillis() - g.locTs > 6 * 3600000L) {
            PrayerLoc.locate(app, new PrayerLoc.Cb() {
                @Override
                public void on(double lat, double lon) {
                    float[] r = new float[1];
                    Location.distanceBetween(g.lat, g.lon, lat, lon, r);
                    if (r[0] > 15000f) {
                        setAuto(app, lat, lon, cb);
                    } else {
                        Cfg k = cfg(app);
                        k.locTs = System.currentTimeMillis();
                        save(app, k);
                        refresh(app, false, cb);
                    }
                }

                @Override
                public void fail() {
                    refresh(app, false, cb);
                }
            });
        } else {
            refresh(app, false, cb);
        }
    }

    static void refresh(Context c, final boolean force, final Done cb) {
        final Context app = c.getApplicationContext();
        EXEC.execute(() -> finish(app, fetch(app, force), cb, false));
    }

    static void setAuto(Context c, final double lat, final double lon, final Done cb) {
        final Context app = c.getApplicationContext();
        EXEC.execute(() -> {
            Cfg g = cfg(app);
            g.configured = true;
            g.auto = true;
            g.lat = lat;
            g.lon = lon;
            g.zone = TimeZone.getDefault().getID();
            g.place = placeName(app, lat, lon);
            g.locTs = System.currentTimeMillis();
            save(app, g);
            finish(app, fetch(app, true), cb, true);
        });
    }

    static void setCity(Context c, final PrayerCities.City city, final Done cb) {
        final Context app = c.getApplicationContext();
        EXEC.execute(() -> {
            Cfg g = cfg(app);
            g.configured = true;
            g.auto = false;
            g.lat = city.lat;
            g.lon = city.lon;
            g.zone = city.tz;
            g.place = city.name();
            save(app, g);
            finish(app, fetch(app, true), cb, true);
        });
    }

    private static void finish(final Context app, final int res, final Done cb, final boolean force) {
        scheduleNext(app);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (res == 2 || force) Bus.changed();
            if (cb != null) cb.on(res > 0);
        });
    }

    private static String placeName(Context c, double lat, double lon) {
        try {
            if (Geocoder.isPresent()) {
                List<Address> l = new Geocoder(c, Lang.isAr() ? new Locale("ar") : Locale.ENGLISH).getFromLocation(lat, lon, 1);
                if (l != null && !l.isEmpty()) {
                    Address a = l.get(0);
                    if (a.getLocality() != null) return a.getLocality();
                    if (a.getSubAdminArea() != null) return a.getSubAdminArea();
                    if (a.getAdminArea() != null) return a.getAdminArea();
                }
            }
        } catch (Exception ignored) {
        }
        return String.format(Locale.US, "%.2f, %.2f", lat, lon);
    }

    /** 0 = no data for today, 1 = cache is current, 2 = new data was downloaded. */
    private static int fetch(Context c, boolean force) {
        try {
            Cfg g = cfg(c);
            if (!g.configured) return 0;
            String key = key(g);
            JSONObject cache = loadCache(c);
            boolean same = key.equals(cache.optString("key"));
            JSONObject days = same ? cache.optJSONObject("days") : null;
            if (days == null) days = new JSONObject();
            Calendar now = Calendar.getInstance(TimeZone.getTimeZone(g.zone));
            List<int[]> months = new ArrayList<>();
            months.add(new int[]{now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1});
            if (now.get(Calendar.DAY_OF_MONTH) >= 26) {
                Calendar nx = (Calendar) now.clone();
                nx.add(Calendar.MONTH, 1);
                months.add(new int[]{nx.get(Calendar.YEAR), nx.get(Calendar.MONTH) + 1});
            }
            boolean changed = !same;
            for (int[] ym : months) {
                if (!force && same && days.has(lastKey(ym[0], ym[1]))) continue;
                JSONArray arr = request(g, ym[0], ym[1]);
                if (arr == null) continue;
                merge(days, arr);
                changed = true;
            }
            Calendar y = (Calendar) now.clone();
            y.add(Calendar.DAY_OF_YEAR, -1);
            String oldest = ymd(y);
            List<String> drop = new ArrayList<>();
            Iterator<String> it = days.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.compareTo(oldest) < 0) drop.add(k);
            }
            for (String k : drop) days.remove(k);
            if (changed) {
                JSONObject out = new JSONObject();
                out.put("key", key);
                out.put("days", days);
                out.put("ts", System.currentTimeMillis());
                Store.putRaw(c, CACHE, out.toString());
            }
            if (!days.has(ymd(now))) return 0;
            return changed ? 2 : 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String lastKey(int y, int m) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, 1);
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, c.getActualMaximum(Calendar.DAY_OF_MONTH));
    }

    private static void merge(JSONObject days, JSONArray arr) throws Exception {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject d = arr.getJSONObject(i);
            JSONObject t = d.getJSONObject("timings");
            JSONObject date = d.getJSONObject("date");
            String ds = date.getJSONObject("gregorian").getString("date");
            String k = ds.substring(6, 10) + "-" + ds.substring(3, 5) + "-" + ds.substring(0, 2);
            JSONArray a = new JSONArray();
            for (String name : KEYS) a.put(t.getString(name).substring(0, 5));
            JSONObject hj = date.getJSONObject("hijri");
            a.put(hj.getString("day"));
            a.put(hj.getJSONObject("month").optString("ar"));
            a.put(hj.getJSONObject("month").optString("en"));
            a.put(hj.getString("year"));
            days.put(k, a);
        }
    }

    private static JSONArray request(Cfg g, int year, int month) {
        HttpURLConnection h = null;
        try {
            StringBuilder u = new StringBuilder("https://api.aladhan.com/v1/calendar/");
            u.append(year).append('/').append(month);
            u.append(String.format(Locale.US, "?latitude=%.2f&longitude=%.2f", g.lat, g.lon));
            if (g.method >= 0) u.append("&method=").append(g.method);
            u.append("&school=").append(g.hanafi ? 1 : 0);
            u.append("&timezonestring=").append(g.zone);
            h = (HttpURLConnection) new URL(u.toString()).openConnection();
            h.setConnectTimeout(10000);
            h.setReadTimeout(15000);
            h.setRequestProperty("Accept", "application/json");
            if (h.getResponseCode() != 200) return null;
            InputStream in = h.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject o = new JSONObject(bo.toString("UTF-8"));
            if (o.optInt("code") != 200) return null;
            return o.getJSONArray("data");
        } catch (Exception e) {
            return null;
        } finally {
            if (h != null) h.disconnect();
        }
    }
}
