package com.clock.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The Quran text, downloaded once and kept on the device so it works offline.
 *
 * Source: the Tanzil Project's Uthmani text (verified verse by verse against the King Fahd Complex Madinah Mushaf),
 * served by api.alquran.cloud (edition "quran-uthmani"), including the page of the 604-page Madinah Mushaf for every ayah.
 */
final class QuranData {
    static final String URL = "https://api.alquran.cloud/v1/quran/quran-uthmani";
    static final int AYAHS = 6236, SURAHS = 114, PAGES = 604;
    private static final String FILE = "quran_uthmani_v1.tsv";

    interface Listener {
        void done(boolean ok);
    }

    static final class Surah {
        int n;
        String name = "", en = "";
        int ayahs, first, page;
        boolean meccan;
    }

    final String[] text = new String[AYAHS];
    final short[] surahOf = new short[AYAHS];
    final short[] num = new short[AYAHS];
    final short[] page = new short[AYAHS];
    final byte[] juz = new byte[AYAHS];
    final Surah[] surahs = new Surah[SURAHS + 1];
    /** First global ayah index of every page (1..604); pageFirst[605] = AYAHS. */
    final int[] pageFirst = new int[PAGES + 2];
    /** Page on which every juz starts (1..30). */
    final int[] juzPage = new int[31];
    /** Global ayah index at which every juz starts. */
    final int[] juzFirst = new int[31];

    private static volatile QuranData cache;
    private static volatile Context appCtx;
    private static boolean loading;

    // ------------------------------------------------------------------ access

    static boolean ready(Context c) {
        return cache != null || new File(c.getApplicationContext().getFilesDir(), FILE).length() > 100000;
    }

    /** The data if it is already downloaded (loads it from disk on first use), else null. Safe on the main thread but not free. */
    static QuranData peek(Context c) {
        appCtx = c.getApplicationContext();
        QuranData d = cache;
        if (d != null) return d;
        synchronized (QuranData.class) {
            if (cache != null) return cache;
            File f = new File(c.getApplicationContext().getFilesDir(), FILE);
            if (!f.exists()) return null;
            try {
                cache = read(f);
            } catch (Throwable e) {
                f.delete();
                cache = null;
            }
            return cache;
        }
    }

    /** Loads from disk on a worker thread and calls back on the main thread (null when not downloaded or damaged). */
    static void load(Context c, Loaded cb) {
        final Context app = c.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            QuranData d = peek(app);
            h.post(() -> cb.on(d));
        }, "quran-load").start();
    }

    interface Loaded {
        void on(QuranData d);
    }

    // ------------------------------------------------------------------ download

    static void download(Context c, Listener l) {
        final Context app = c.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        synchronized (QuranData.class) {
            if (loading) {
                h.post(() -> l.done(false));
                return;
            }
            loading = true;
        }
        new Thread(() -> {
            boolean ok = false;
            try {
                String json = fetch();
                QuranData d = parseJson(json);
                File out = new File(app.getFilesDir(), FILE);
                File tmp = new File(app.getFilesDir(), FILE + ".tmp");
                write(d, tmp);
                if (out.exists()) out.delete();
                ok = tmp.renameTo(out);
                if (ok) cache = d;
            } catch (Throwable ignored) {
                ok = false;
            }
            synchronized (QuranData.class) {
                loading = false;
            }
            final boolean res = ok;
            if (res) {
                try {
                    WBase.refresh(app, WidgetQuran.class);
                } catch (Throwable ignored) {
                }
            }
            h.post(() -> l.done(res));
        }, "quran-download").start();
    }

    private static String fetch() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(URL).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "ClockStudio");
        try {
            if (con.getResponseCode() != 200) throw new Exception("http " + con.getResponseCode());
            StringBuilder sb = new StringBuilder(3_000_000);
            try (InputStream in = con.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
                char[] buf = new char[1 << 15];
                int n;
                while ((n = br.read(buf)) > 0) sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }

    // ------------------------------------------------------------------ parsing / storage

    private static QuranData parseJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray ss = root.getJSONObject("data").getJSONArray("surahs");
        if (ss.length() != SURAHS) throw new Exception("surahs " + ss.length());
        QuranData d = new QuranData();
        int g = 0;
        for (int i = 0; i < ss.length(); i++) {
            JSONObject so = ss.getJSONObject(i);
            Surah s = new Surah();
            s.n = so.getInt("number");
            s.name = so.optString("name", "");
            s.en = so.optString("englishName", "");
            s.meccan = "Meccan".equalsIgnoreCase(so.optString("revelationType", ""));
            JSONArray as = so.getJSONArray("ayahs");
            s.ayahs = as.length();
            s.first = g;
            d.surahs[s.n] = s;
            for (int k = 0; k < as.length(); k++) {
                JSONObject a = as.getJSONObject(k);
                if (g >= AYAHS) throw new Exception("too many ayahs");
                d.text[g] = a.getString("text");
                d.surahOf[g] = (short) s.n;
                d.num[g] = (short) a.getInt("numberInSurah");
                d.page[g] = (short) a.getInt("page");
                d.juz[g] = (byte) a.getInt("juz");
                g++;
            }
        }
        d.finish();
        return d;
    }

    private static void write(QuranData d, File f) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8), 1 << 16)) {
            for (int s = 1; s <= SURAHS; s++) {
                Surah x = d.surahs[s];
                w.write("S\t" + x.n + "\t" + x.name + "\t" + x.en + "\t" + x.ayahs + "\t" + (x.meccan ? 1 : 0) + "\n");
            }
            for (int i = 0; i < AYAHS; i++) {
                w.write("A\t" + d.surahOf[i] + "\t" + d.num[i] + "\t" + d.page[i] + "\t" + d.juz[i] + "\t" + d.text[i] + "\n");
            }
        }
    }

    private static QuranData read(File f) throws Exception {
        QuranData d = new QuranData();
        int g = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            int first = 0;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("S\t")) {
                    String[] p = line.split("\t", -1);
                    Surah s = new Surah();
                    s.n = Integer.parseInt(p[1]);
                    s.name = p[2];
                    s.en = p[3];
                    s.ayahs = Integer.parseInt(p[4]);
                    s.meccan = "1".equals(p[5]);
                    s.first = first;
                    first += s.ayahs;
                    d.surahs[s.n] = s;
                } else if (line.startsWith("A\t")) {
                    String[] p = line.split("\t", 6);
                    if (g >= AYAHS) throw new Exception("too many");
                    d.surahOf[g] = Short.parseShort(p[1]);
                    d.num[g] = Short.parseShort(p[2]);
                    d.page[g] = Short.parseShort(p[3]);
                    d.juz[g] = Byte.parseByte(p[4]);
                    d.text[g] = p[5];
                    g++;
                }
            }
        }
        d.finish();
        return d;
    }

    /** Validates the data, removes the repeated basmala from the first ayah of each surah and builds the page / juz maps. */
    private void finish() throws Exception {
        int g = 0;
        for (int s = 1; s <= SURAHS; s++) {
            if (surahs[s] == null) throw new Exception("missing surah " + s);
            g += surahs[s].ayahs;
        }
        if (g != AYAHS) throw new Exception("ayah count " + g);
        for (int i = 0; i < AYAHS; i++) {
            if (text[i] == null || text[i].isEmpty() || page[i] < 1 || page[i] > PAGES) throw new Exception("bad ayah " + i);
            if (i > 0 && page[i] < page[i - 1]) throw new Exception("page order " + i);
            if (num[i] == 1 && surahOf[i] != 1 && surahOf[i] != 9) text[i] = stripBasmala(text[i]);
        }
        for (int p = 0; p < pageFirst.length; p++) pageFirst[p] = -1;
        for (int i = AYAHS - 1; i >= 0; i--) pageFirst[page[i]] = i;
        pageFirst[PAGES + 1] = AYAHS;
        for (int p = PAGES; p >= 1; p--) if (pageFirst[p] < 0) pageFirst[p] = pageFirst[p + 1];
        for (int j = 0; j <= 30; j++) juzFirst[j] = -1;
        for (int i = AYAHS - 1; i >= 0; i--) juzFirst[juz[i]] = i;
        for (int j = 1; j <= 30; j++) {
            if (juzFirst[j] < 0) throw new Exception("juz " + j);
            juzPage[j] = page[juzFirst[j]];
        }
        for (int s = 1; s <= SURAHS; s++) surahs[s].page = page[surahs[s].first];
    }

    private static String stripBasmala(String t) {
        String[] w = t.trim().split("\\s+", 5);
        if (w.length >= 5 && norm(w[0]).equals("بسم")) return w[4];
        return t;
    }

    // ------------------------------------------------------------------ helpers

    int surahStart(int surahNumber) {
        return surahs[surahNumber].first;
    }

    /** Surah display title, e.g. "سورة البقرة". */
    String surahTitle(int s) {
        Surah x = surahs[s];
        return Lang.isAr() ? x.name : x.en;
    }

    /** Surah name without the leading word "سورة". */
    String surahShort(int s) {
        Surah x = surahs[s];
        if (!Lang.isAr()) return x.en;
        int sp = x.name.indexOf(' ');
        return sp > 0 && norm(x.name.substring(0, sp)).startsWith("سور") ? x.name.substring(sp + 1) : x.name;
    }

    /** Global index of the verse of the day: a verse picked with "suggest a verse" today, otherwise a stable pick for the whole day. */
    int dailyVerse() {
        long day = System.currentTimeMillis() / 86400000L;
        Context c = appCtx;
        if (c != null) {
            try {
                String s = Store.raw(c, "quran_daily_pick");
                if (s != null && s.startsWith(day + ":")) {
                    int v = Integer.parseInt(s.substring(s.indexOf(':') + 1));
                    if (v >= 0 && v < AYAHS) return v;
                }
            } catch (Exception ignored) {
            }
        }
        int i = (int) Math.floorMod(day * 2654435761L, (long) AYAHS);
        for (int k = 0; k < 400; k++) {
            int j = (i + k) % AYAHS;
            if (text[j].length() <= 130) return j;
        }
        return i;
    }

    /** Replaces today's verse of the day with a new random short verse and refreshes the widgets. */
    int suggestVerse(Context c) {
        final Context app = c.getApplicationContext();
        long day = System.currentTimeMillis() / 86400000L;
        int cur = dailyVerse();
        java.util.Random r = new java.util.Random();
        int pick = cur;
        for (int k = 0; k < 600; k++) {
            int j = r.nextInt(AYAHS);
            if (j != cur && text[j].length() <= 130) {
                pick = j;
                break;
            }
        }
        Store.putRaw(app, "quran_daily_pick", day + ":" + pick);
        try {
            WBase.refresh(app, WidgetQuran.class);
            WBase.refresh(app, WidgetQuranL.class);
        } catch (Throwable ignored) {
        }
        return pick;
    }

    /** All surahs / ayahs whose name or number matches the query (empty query = everything). */
    List<Surah> search(String q) {
        List<Surah> r = new ArrayList<>();
        String nq = norm(q == null ? "" : q.trim());
        String lq = q == null ? "" : q.trim().toLowerCase();
        for (int s = 1; s <= SURAHS; s++) {
            Surah x = surahs[s];
            if (nq.isEmpty() || norm(x.name).contains(nq) || x.en.toLowerCase().contains(lq) || String.valueOf(s).equals(lq)
                    || ar(s).equals(lq)) r.add(x);
        }
        return r;
    }

    private String[] normCache;

    /** Global indices of the ayahs whose text contains the query, ignoring diacritics; also understands "2:255" style references. */
    List<Integer> searchAyahs(String q, int limit) {
        List<Integer> r = new ArrayList<>();
        if (q == null) return r;
        int ref = locate(q);
        if (ref >= 0) {
            r.add(ref);
            return r;
        }
        String nq = norm(q.trim());
        if (nq.length() < 2) return r;
        String[] nt = normCache;
        if (nt == null) {
            nt = new String[AYAHS];
            for (int i = 0; i < AYAHS; i++) nt[i] = norm(text[i]);
            normCache = nt;
        }
        for (int i = 0; i < AYAHS && r.size() < limit; i++) if (nt[i].contains(nq)) r.add(i);
        return r;
    }

    /** Resolves "surah:ayah" (digits in any script) or "surah name ayah" to a global ayah index, or -1. */
    int locate(String q) {
        String t = q.trim();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 0x0660 && c <= 0x0669) c = (char) ('0' + (c - 0x0660));
            else if (c >= 0x06F0 && c <= 0x06F9) c = (char) ('0' + (c - 0x06F0));
            b.append(c);
        }
        t = b.toString().trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,3})\\s*[:/\\-.]\\s*(\\d{1,3})$").matcher(t);
        if (m.matches()) return at(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        m = java.util.regex.Pattern.compile("^(.*\\D)\\s+(\\d{1,3})$").matcher(t);
        if (m.matches()) {
            List<Surah> l = search(m.group(1).trim());
            if (l.size() == 1) return at(l.get(0).n, Integer.parseInt(m.group(2)));
        }
        return -1;
    }

    private int at(int s, int n) {
        if (s < 1 || s > SURAHS || n < 1 || n > surahs[s].ayahs) return -1;
        return surahs[s].first + n - 1;
    }

    /** Strips Arabic diacritics and unifies letter forms so searching works without typing the marks. */
    static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x064B && c <= 0x065F) || c == 0x0670 || (c >= 0x06D6 && c <= 0x06ED) || c == 0x0640) continue;
            if (c == 0x0671 || c == 0x0623 || c == 0x0625 || c == 0x0622) c = 0x0627;
            else if (c == 0x0629) c = 0x0647;
            else if (c == 0x0649) c = 0x064A;
            b.append(c);
        }
        return b.toString();
    }

    /** Arabic-Indic digits, as printed in the mushaf. */
    static String ar(int n) {
        String s = String.valueOf(n);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) b.append((char) (0x0660 + (s.charAt(i) - '0')));
        return b.toString();
    }

    /** Digits in the language of the interface. */
    static String num(int n) {
        return Lang.isAr() ? ar(n) : String.valueOf(n);
    }

    // ------------------------------------------------------------------ reading position and bookmarks

    static int lastPage(Context c) {
        try {
            String r = Store.raw(c, "quran_last");
            return r == null || r.isEmpty() ? 0 : Math.max(1, Math.min(PAGES, Integer.parseInt(r)));
        } catch (Exception e) {
            return 0;
        }
    }

    static String lastLabel(Context c) {
        String r = Store.raw(c, "quran_last_label");
        return r == null ? "" : r;
    }

    static void saveLast(Context c, int page, String label) {
        Store.putRaw(c, "quran_last", String.valueOf(page));
        Store.putRaw(c, "quran_last_label", label);
    }

    static List<Integer> marks(Context c) {
        List<Integer> r = new ArrayList<>();
        try {
            String raw = Store.raw(c, "quran_marks");
            if (raw != null && !raw.isEmpty()) {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) r.add(a.getInt(i));
            }
        } catch (Exception ignored) {
        }
        return r;
    }

    static boolean isMarked(Context c, int ayah) {
        return marks(c).contains(ayah);
    }

    /** Adds or removes a bookmark; returns true when the ayah is bookmarked afterwards. */
    static boolean toggleMark(Context c, int ayah) {
        List<Integer> m = marks(c);
        boolean on;
        if (m.contains((Integer) ayah)) {
            m.remove((Integer) ayah);
            on = false;
        } else {
            m.add(0, ayah);
            on = true;
        }
        JSONArray a = new JSONArray();
        for (int v : m) a.put(v);
        Store.putRaw(c, "quran_marks", a.toString());
        return on;
    }
}
