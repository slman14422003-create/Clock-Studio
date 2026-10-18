package com.clock.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
 * Verse explanations fetched on demand from api.alquran.cloud and cached on the device,
 * so every verse that was opened once keeps working offline.
 */
final class QuranTafsir {
    static final String[] IDS = {"ar.muyassar", "ar.jalalayn", "ar.qurtubi", "ar.baghawi", "ar.waseet", "ar.miqbas", "en.sahih"};
    private static final String BASE = "https://api.alquran.cloud/v1/ayah/";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Cb {
        /** The explanation text, or null when it could not be loaded. */
        void done(String text);
    }

    private static File file(Context c, String ed, int s, int n) {
        File dir = new File(c.getApplicationContext().getFilesDir(), "tafsir/" + ed);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, s + "_" + n + ".txt");
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

    static void load(Context c, String ed, int s, int n, Cb cb) {
        final Context app = c.getApplicationContext();
        final File f = file(app, ed, s, n);
        new Thread(() -> {
            String out = readCached(f);
            if (out == null) {
                out = fetch(ed, s, n);
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

    private static String fetch(String ed, int s, int n) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(BASE + s + ":" + n + "/" + ed).openConnection();
            con.setConnectTimeout(12000);
            con.setReadTimeout(20000);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "ClockStudio");
            if (con.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (InputStream in = con.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int r;
                while ((r = br.read(buf)) > 0) sb.append(buf, 0, r);
            }
            JSONObject o = new JSONObject(sb.toString());
            if (o.optInt("code") != 200) return null;
            String t = o.getJSONObject("data").optString("text", "").trim();
            return t.isEmpty() ? null : t;
        } catch (Throwable e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
