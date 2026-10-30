package com.clock.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Verse explanations from the classical printed tafsir books (as published by quran.com), with a second mirror as a
 * fallback, cleaned of markup and cached on the device so every verse that was opened once keeps working offline.
 * The last edition is a plain English translation, not a tafsir.
 */
final class QuranTafsir {
    private static final class Ed {
        final String key, slug;
        final int qid;

        Ed(String key, int qid, String slug) {
            this.key = key;
            this.qid = qid;
            this.slug = slug;
        }
    }

    private static final Ed[] EDS = {
            new Ed("muyassar", 16, "ar-tafsir-muyassar"),
            new Ed("saadi", 91, "ar-tafseer-al-saddi"),
            new Ed("ibnkathir", 14, "ar-tafsir-ibn-kathir"),
            new Ed("tabari", 15, "ar-tafsir-al-tabari"),
            new Ed("qurtubi", 90, "ar-tafseer-al-qurtubi"),
            new Ed("baghawi", 94, "ar-tafsir-al-baghawi"),
            new Ed("waseet", 93, "ar-tafsir-al-wasit"),
            new Ed("en.sahih", 0, "en.sahih")
    };
    static final String[] IDS = new String[EDS.length];

    static {
        for (int i = 0; i < EDS.length; i++) IDS[i] = EDS[i].key;
    }

    private static final String QCOM = "https://api.quran.com/api/v4/tafsirs/";
    private static final String MIRROR = "https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@main/tafsir/";
    private static final String CLOUD = "https://api.alquran.cloud/v1/ayah/";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Cb {
        /** The explanation text, or null when it could not be loaded. */
        void done(String text);
    }

    private static Ed ed(String key) {
        for (Ed e : EDS) if (e.key.equals(key)) return e;
        return EDS[0];
    }

    private static File file(Context c, String ed, int s, int n) {
        File dir = new File(c.getApplicationContext().getFilesDir(), "tafsir2/" + ed);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, s + "_" + n + ".txt");
    }

    private static volatile boolean oldDropped;

    private static void dropOld(Context c) {
        if (oldDropped) return;
        oldDropped = true;
        File old = new File(c.getApplicationContext().getFilesDir(), "tafsir");
        if (!old.exists()) return;
        File[] eds = old.listFiles();
        if (eds != null) {
            for (File e : eds) {
                File[] fs = e.listFiles();
                if (fs != null) for (File f : fs) f.delete();
                e.delete();
            }
        }
        old.delete();
    }

    private static String readCached(File f) {
        if (!f.exists() || f.length() == 0) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                int r = in.read(b, off, b.length - off);
                if (r < 0) break;
                off += r;
            }
            return new String(b, 0, off, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    static boolean isCached(Context c, String ed, int s, int n) {
        return file(c, ed, s, n).length() > 0;
    }

    static boolean offlineOnly(Context c) {
        return "1".equals(Store.raw(c, "tafsir_offline"));
    }

    static void setOfflineOnly(Context c, boolean v) {
        Store.putRaw(c, "tafsir_offline", v ? "1" : "0");
    }

    /** Result of a load: the text, or null when it is missing; {@code offlineMiss} tells the screen why. */
    static volatile boolean offlineMiss;

    static void load(Context c, String key, int s, int n, Cb cb) {
        final Context app = c.getApplicationContext();
        final Ed e = ed(key);
        final File f = file(app, e.key, s, n);
        final boolean offline = offlineOnly(app);
        new Thread(() -> {
            dropOld(app);
            String out = readCached(f);
            offlineMiss = false;
            if (out == null && offline) {
                offlineMiss = true;
            } else if (out == null) {
                out = fetch(e, s, n);
                if (out != null) write(f, out);
            }
            final String res = out;
            MAIN.post(() -> cb.done(res));
        }, "quran-tafsir").start();
    }

    /** Warms the cache with the next verse so stepping forward feels instant. */
    static void prefetch(Context c, String key, int s, int n) {
        final Context app = c.getApplicationContext();
        if (offlineOnly(app) || n < 1) return;
        final Ed e = ed(key);
        final File f = file(app, e.key, s, n);
        if (f.length() > 0) return;
        PRE.execute(() -> {
            if (f.length() > 0) return;
            String out = fetch(e, s, n);
            if (out != null) write(f, out);
        });
    }

    private static void write(File f, String text) {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            tmp.delete();
            return;
        }
        if (f.exists()) f.delete();
        if (!tmp.renameTo(f)) tmp.delete();
    }

    // ------------------------------------------------------------------ manual download for offline use

    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final ExecutorService PRE = Executors.newSingleThreadExecutor();
    private static final Map<String, int[]> RUN = new ConcurrentHashMap<>();
    private static final Set<String> CANCEL = ConcurrentHashMap.newKeySet();
    /** Called on the main thread whenever a download advances or ends. */
    static volatile Runnable onChange;
    static volatile boolean failed;

    private static String dlKey(String ed, int surah) {
        return ed + ":" + surah;
    }

    static boolean running(String ed, int surah) {
        return RUN.containsKey(dlKey(ed, surah));
    }

    /** {done, total} of a running download, or null. */
    static int[] progress(String ed, int surah) {
        return RUN.get(dlKey(ed, surah));
    }

    static void cancel(String ed, int surah) {
        CANCEL.add(dlKey(ed, surah));
    }

    static int cachedCount(Context c, String ed, int surah, int ayahs) {
        int k = 0;
        for (int a = 1; a <= ayahs; a++) if (isCached(c, ed, surah, a)) k++;
        return k;
    }

    static void deleteSurah(Context c, String ed, int surah, int ayahs) {
        for (int a = 1; a <= ayahs; a++) file(c, ed, surah, a).delete();
    }

    private static void changed() {
        MAIN.post(() -> {
            Runnable r = onChange;
            if (r != null) r.run();
        });
    }

    /** Saves one surah of the given edition, or the whole book when {@code surah} is 0. */
    static void download(Context c, String edKey, QuranData d, int surah) {
        final Context app = c.getApplicationContext();
        final Ed e = ed(edKey);
        final String k = dlKey(e.key, surah);
        if (RUN.containsKey(k)) return;
        CANCEL.remove(k);
        final int from = surah == 0 ? 1 : surah, to = surah == 0 ? 114 : surah;
        int total = 0;
        for (int s = from; s <= to; s++) total += d.surahs[s].ayahs;
        final int all = total;
        RUN.put(k, new int[]{0, all});
        changed();
        EX.execute(() -> {
            boolean ok = true;
            int done = 0;
            for (int s = from; s <= to && ok; s++) {
                if (CANCEL.contains(k)) break;
                int n = d.surahs[s].ayahs;
                int have = cachedCount(app, e.key, s, n);
                if (have < n) {
                    String[] texts = fetchSurah(e, s, n);
                    for (int a = 1; a <= n; a++) {
                        if (CANCEL.contains(k)) break;
                        File f = file(app, e.key, s, a);
                        if (f.length() > 0) continue;
                        String t = texts != null ? texts[a] : null;
                        if (t == null) t = fetch(e, s, a);
                        if (t == null) {
                            ok = false;
                            break;
                        }
                        write(f, t);
                        if (a % 8 == 0) {
                            RUN.put(k, new int[]{done + a, all});
                            changed();
                        }
                    }
                }
                done += n;
                RUN.put(k, new int[]{done, all});
                changed();
            }
            if (!ok) failed = true;
            CANCEL.remove(k);
            RUN.remove(k);
            changed();
        });
    }

    /** One request for a whole surah; entry [a] is the text of ayah a or null when the source has no text for it. */
    private static String[] fetchSurah(Ed e, int s, int n) {
        String url = e.qid == 0 ? "https://api.alquran.cloud/v1/surah/" + s + "/" + e.key
                : MIRROR + e.slug + "/" + s + ".json";
        String body = getRaw(url);
        if (body == null) return null;
        try {
            JSONObject o = new JSONObject(body);
            JSONArray arr;
            String numKey, txtKey = "text";
            if (e.qid == 0) {
                arr = o.getJSONObject("data").getJSONArray("ayahs");
                numKey = "numberInSurah";
            } else {
                arr = o.getJSONArray("ayahs");
                numKey = "ayah";
            }
            String[] out = new String[n + 1];
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.getJSONObject(i);
                int no = a.optInt(numKey, 0);
                if (no < 1 || no > n) continue;
                String t = clean(a.optString(txtKey, ""));
                if (t != null) out[no] = t;
            }
            return out;
        } catch (Throwable ex) {
            return null;
        }
    }

    private static String getRaw(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(12000);
            con.setReadTimeout(40000);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "ClockStudio");
            if (con.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (InputStream in = con.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[16384];
                int r;
                while ((r = br.read(buf)) > 0) sb.append(buf, 0, r);
            }
            return sb.toString();
        } catch (Throwable ex) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String fetch(Ed e, int s, int n) {
        if (e.qid == 0) return clean(get(CLOUD + s + ":" + n + "/" + e.key, "data"));
        String t = clean(get(QCOM + e.qid + "/by_ayah/" + s + ":" + n, "tafsir"));
        if (t == null) t = clean(get(MIRROR + e.slug + "/" + s + "/" + n + ".json", null));
        return t;
    }

    /** Downloads a JSON document and returns its "text" field (inside the given object when one is named). */
    private static String get(String url, String inner) {
        String body = getRaw(url);
        if (body == null) return null;
        try {
            JSONObject o = new JSONObject(body);
            if (inner != null) o = o.getJSONObject(inner);
            return o.optString("text", "");
        } catch (Throwable e) {
            return null;
        }
    }

    /** Turns the HTML of a tafsir page into readable paragraphs of plain text. */
    private static String clean(String raw) {
        if (raw == null) return null;
        String h = raw.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</(p|div|h[1-6]|li)>", "\n\n");
        String t = Html.fromHtml(h, Html.FROM_HTML_MODE_LEGACY).toString();
        t = t.replace('\u00A0', ' ').replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        return t.length() < 2 ? null : t;
    }
}
