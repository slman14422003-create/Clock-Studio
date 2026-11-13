package com.clock.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-app updates from the project's GitHub releases. The release is read from the public GitHub API, the APK is
 * accepted only from this repository's release downloads, its SHA-256 is checked against the published checksum, and
 * Android itself refuses the install unless the APK carries the same signature as the installed app.
 */
final class Updater {
    private Updater() {
    }

    static final String REPO = "slman14422003-create/Clock-Studio";
    static final String PAGE = "https://github.com/" + REPO + "/releases";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String DL_PREFIX = "https://github.com/" + REPO + "/releases/download/";
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Info {
        String tag = "", version = "", notes = "", apkUrl, shaUrl;
        long size;
        boolean newer;
    }

    static final class Job {
        volatile boolean cancel;

        void cancel() {
            cancel = true;
        }
    }

    interface CheckCb {
        /** err: 0 ok, 1 network, 2 no release published. */
        void done(Info info, int err);
    }

    interface Progress {
        void on(long done, long total);
    }

    interface DoneCb {
        /** err: 0 ok, 1 network, 2 verification failed, 3 cancelled. */
        void done(File apk, int err);
    }

    // ------------------------------------------------------------------ check

    static void check(Context c, CheckCb cb) {
        EX.execute(() -> {
            Info info = null;
            int err = 0;
            HttpURLConnection con = null;
            try {
                con = open(API);
                int code = con.getResponseCode();
                if (code == 404) {
                    err = 2;
                } else if (code != 200) {
                    err = 1;
                } else {
                    info = parse(new JSONObject(read(con.getInputStream())));
                    if (info == null) err = 2;
                    else info.newer = isNewer(info.version, BuildConfig.VERSION_NAME);
                }
            } catch (Throwable e) {
                err = 1;
            } finally {
                if (con != null) con.disconnect();
            }
            final Info fi = info;
            final int fe = err;
            MAIN.post(() -> cb.done(fi, fe));
        });
    }

    private static Info parse(JSONObject o) {
        String tag = o.optString("tag_name", "");
        if (tag.isEmpty()) return null;
        Info i = new Info();
        i.tag = tag;
        i.version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        i.notes = cleanNotes(o.optString("body", ""));
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int k = 0; k < assets.length(); k++) {
                JSONObject a = assets.optJSONObject(k);
                if (a == null) continue;
                String name = a.optString("name", "");
                String url = a.optString("browser_download_url", "");
                if (!url.startsWith(DL_PREFIX)) continue;
                if (name.endsWith(".apk") && i.apkUrl == null) {
                    i.apkUrl = url;
                    i.size = a.optLong("size", 0);
                } else if (name.endsWith(".apk.sha256") && i.shaUrl == null) {
                    i.shaUrl = url;
                }
            }
        }
        return i;
    }

    private static String cleanNotes(String body) {
        String t = body.replace("\r", "").replaceAll("(?m)^#+\\s*", "").replace("**", "").replace("`", "")
                .replaceAll("(?m)^\\s*[*-]\\s+", "\u2022 ").replaceAll("\\n{3,}", "\n\n").trim();
        return t.length() > 800 ? t.substring(0, 800).trim() + "\u2026" : t;
    }

    /** True when the remote version number is greater than the local one (dotted numbers, e.g. 1.2.0 &gt; 1.1). */
    static boolean isNewer(String remote, String local) {
        List<Integer> r = nums(remote), l = nums(local);
        if (r.isEmpty()) return false;
        int n = Math.max(r.size(), l.size());
        for (int k = 0; k < n; k++) {
            int a = k < r.size() ? r.get(k) : 0;
            int b = k < l.size() ? l.get(k) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static List<Integer> nums(String v) {
        List<Integer> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+").matcher(v == null ? "" : v);
        while (m.find() && out.size() < 5) {
            try {
                out.add(Integer.parseInt(m.group()));
            } catch (NumberFormatException e) {
                out.add(0);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ download

    static File apkFile(Context c) {
        File dir = new File(c.getCacheDir(), "share");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "Clock-Studio-update.apk");
    }

    static void clean(Context c) {
        try {
            File f = apkFile(c);
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {
        }
    }

    static Job download(Context c, Info info, Progress p, DoneCb cb) {
        final Job job = new Job();
        final Context app = c.getApplicationContext();
        EX.execute(() -> {
            File out = apkFile(app);
            File tmp = new File(out.getParentFile(), out.getName() + ".part");
            int err = 0;
            HttpURLConnection con = null;
            try {
                if (info.apkUrl == null || !info.apkUrl.startsWith(DL_PREFIX)) throw new Exception("url");
                String expected = null;
                if (info.shaUrl != null && info.shaUrl.startsWith(DL_PREFIX)) {
                    HttpURLConnection sc = null;
                    try {
                        sc = open(info.shaUrl);
                        if (sc.getResponseCode() == 200) {
                            Matcher m = Pattern.compile("[0-9a-fA-F]{64}").matcher(read(sc.getInputStream()));
                            if (m.find()) expected = m.group().toLowerCase(java.util.Locale.US);
                        }
                    } finally {
                        if (sc != null) sc.disconnect();
                    }
                }
                con = open(info.apkUrl);
                if (con.getResponseCode() != 200) throw new Exception("http");
                long total = con.getContentLengthLong();
                if (total <= 0) total = info.size;
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long done = 0, lastPost = 0;
                try (InputStream in = con.getInputStream(); FileOutputStream fo = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[1 << 15];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (job.cancel) break;
                        fo.write(buf, 0, n);
                        md.update(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastPost > 120) {
                            lastPost = now;
                            final long d = done, t = total;
                            MAIN.post(() -> p.on(d, t));
                        }
                    }
                }
                if (job.cancel) {
                    err = 3;
                } else {
                    StringBuilder hex = new StringBuilder();
                    for (byte b : md.digest()) hex.append(String.format(java.util.Locale.US, "%02x", b));
                    if (expected != null && !expected.equals(hex.toString())) {
                        err = 2;
                    } else if (tmp.length() < 100000) {
                        err = 2;
                    } else {
                        if (out.exists()) out.delete();
                        if (!tmp.renameTo(out)) err = 1;
                    }
                }
            } catch (Throwable e) {
                err = job.cancel ? 3 : 1;
            } finally {
                if (con != null) con.disconnect();
                if (err != 0 && tmp.exists()) tmp.delete();
            }
            final int fe = err;
            MAIN.post(() -> cb.done(fe == 0 ? out : null, fe));
        });
        return job;
    }

    // ------------------------------------------------------------------ install

    static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    static void openInstallPermission(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
        } catch (Throwable e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
            } catch (Throwable ignored) {
            }
        }
    }

    static void install(Activity a, File apk) {
        try {
            Uri u = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Throwable e) {
            Ui.toast(a, a.getString(R.string.upd_err_install));
        }
    }

    // ------------------------------------------------------------------ automatic check

    /** At most once a day, asks GitHub whether a newer release exists and offers to open the update screen. */
    static void autoCheck(final Activity a) {
        try {
            if (a.isFinishing() || "0".equals(Store.raw(a, "upd_auto"))) return;
            long last = 0;
            try {
                last = Long.parseLong(Store.raw(a, "upd_last"));
            } catch (Exception ignored) {
            }
            long now = System.currentTimeMillis();
            if (now - last < 24L * 3600 * 1000) return;
            Store.putRaw(a, "upd_last", String.valueOf(now));
            check(a, (info, err) -> {
                try {
                    if (info == null || !info.newer || a.isFinishing() || a.isDestroyed()) return;
                    if (info.version.equals(Store.raw(a, "upd_skip"))) return;
                    new Dlg(a).setTitle(R.string.upd_title).setMessage(a.getString(R.string.upd_new_msg, info.version))
                            .setPositiveButton(R.string.upd_now, (d, w) -> a.startActivity(new Intent(a, UpdateActivity.class)))
                            .setNegativeButton(R.string.upd_later, (d, w) -> Store.putRaw(a, "upd_skip", info.version)).show();
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ http

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        con.setRequestProperty("User-Agent", "ClockStudio");
        return con;
    }

    private static String read(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int r;
            while ((r = br.read(buf)) > 0) sb.append(buf, 0, r);
        }
        return sb.toString();
    }
}
