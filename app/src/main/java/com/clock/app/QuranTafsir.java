package com.clock.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

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

    static void load(Context c, String key, int s, int n, Cb cb) {
        final Context app = c.getApplicationContext();
        final Ed e = ed(key);
        final File f = file(app, e.key, s, n);
        new Thread(() -> {
            dropOld(app);
            String out = readCached(f);
            if (out == null) {
                out = fetch(e, s, n);
                if (out != null) {
                    try (FileOutputStream os = new FileOutputStream(f)) {
                        os.write(out.getBytes(StandardCharsets.UTF_8));
                    } catch (Throwable ignored) {
                    }
                }
            }
            final String res = out;
            MAIN.post(() -> cb.done(res));
        }, "quran-tafsir").start();
    }

    private static String fetch(Ed e, int s, int n) {
        if (e.qid == 0) return clean(get(CLOUD + s + ":" + n + "/" + e.key, "data"));
        String t = clean(get(QCOM + e.qid + "/by_ayah/" + s + ":" + n, "tafsir"));
        if (t == null) t = clean(get(MIRROR + e.slug + "/" + s + "/" + n + ".json", null));
        return t;
    }

    /** Downloads a JSON document and returns its "text" field (inside the given object when one is named). */
    private static String get(String url, String inner) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(12000);
            con.setReadTimeout(20000);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "ClockStudio");
            if (con.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (InputStream in = con.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int r;
                while ((r = br.read(buf)) > 0) sb.append(buf, 0, r);
            }
            JSONObject o = new JSONObject(sb.toString());
            if (inner != null) o = o.getJSONObject(inner);
            return o.optString("text", "");
        } catch (Throwable e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
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
