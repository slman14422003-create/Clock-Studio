package com.clock.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ayah-by-ayah recitations from everyayah.com. A surah is saved as one small mp3 per ayah under files/audio/<reciter>/,
 * which lets the reader highlight the ayah being recited and lets playback work offline.
 */
final class QuranAudio {
    private QuranAudio() {
    }

    static final class Reciter {
        final String folder, ar, en;

        Reciter(String folder, String ar, String en) {
            this.folder = folder;
            this.ar = ar;
            this.en = en;
        }

        String name() {
            return Lang.isAr() ? ar : en;
        }
    }

    static final Reciter[] RECITERS = {
            new Reciter("Abdul_Basit_Murattal_64kbps", "عبد الباسط عبد الصمد (مرتّل)", "Abdul Basit Abdus-Samad (Murattal)"),
            new Reciter("Abdul_Basit_Mujawwad_128kbps", "عبد الباسط عبد الصمد (مجوّد)", "Abdul Basit Abdus-Samad (Mujawwad)"),
            new Reciter("Husary_64kbps", "محمود خليل الحصري", "Mahmoud Khalil Al-Husary"),
            new Reciter("Minshawy_Murattal_128kbps", "محمد صديق المنشاوي (مرتّل)", "Mohamed Siddiq Al-Minshawi (Murattal)"),
            new Reciter("Minshawy_Mujawwad_192kbps", "محمد صديق المنشاوي (مجوّد)", "Mohamed Siddiq Al-Minshawi (Mujawwad)"),
            new Reciter("Mohammad_al_Tablaway_128kbps", "محمد محمود الطبلاوي", "Mohammad Al-Tablawi"),
            new Reciter("Alafasy_128kbps", "مشاري راشد العفاسي", "Mishary Rashid Alafasy"),
            new Reciter("Abdurrahmaan_As-Sudais_192kbps", "عبد الرحمن السديس", "Abdurrahman As-Sudais"),
            new Reciter("Saood_ash-Shuraym_128kbps", "سعود الشريم", "Saud Ash-Shuraim"),
            new Reciter("Hani_Rifai_192kbps", "هاني الرفاعي", "Hani Ar-Rifai"),
            new Reciter("Abu_Bakr_Ash-Shaatree_128kbps", "أبو بكر الشاطري", "Abu Bakr Ash-Shatri"),
            new Reciter("Maher_AlMuaiqly_64kbps", "ماهر المعيقلي", "Maher Al-Muaiqly"),
            new Reciter("Ghamadi_40kbps", "سعد الغامدي", "Saad Al-Ghamdi"),
            new Reciter("Salah_Al_Budair_128kbps", "صلاح البدير", "Salah Al-Budair"),
            new Reciter("Hudhaify_128kbps", "علي الحذيفي", "Ali Al-Hudhaify"),
            new Reciter("Abdullah_Basfar_64kbps", "عبد الله بصفر", "Abdullah Basfar"),
            new Reciter("Ahmed_ibn_Ali_al-Ajamy_128kbps_ketaballah.net", "أحمد العجمي", "Ahmed Al-Ajamy"),
            new Reciter("Yasser_Ad-Dussary_128kbps", "ياسر الدوسري", "Yasser Ad-Dossari"),
            new Reciter("Nasser_Alqatami_128kbps", "ناصر القطامي", "Nasser Al-Qatami"),
            new Reciter("Muhammad_Ayyoub_128kbps", "محمد أيوب", "Muhammad Ayyub"),
            new Reciter("Muhammad_Jibreel_128kbps", "محمد جبريل", "Muhammad Jibreel"),
            new Reciter("Khaalid_Abdullaah_al-Qahtaanee_192kbps", "خالد القحطاني", "Khalid Al-Qahtani"),
            new Reciter("Muhsin_Al_Qasim_192kbps", "محسن القاسم", "Muhsin Al-Qasim"),
            new Reciter("Abdullaah_3awwaad_Al-Juhaynee_128kbps", "عبد الله الجهني", "Abdullah Al-Juhani"),
            new Reciter("Fares_Abbad_64kbps", "فارس عباد", "Fares Abbad"),
            new Reciter("Ayman_Sowaid_64kbps", "أيمن سويد (تعليمي)", "Ayman Sowaid (Teaching)"),
            new Reciter("Ali_Hajjaj_AlSuesy_128kbps", "علي حجاج السويسي", "Ali Hajjaj Al-Suesy"),
            new Reciter("Mustafa_Ismail_48kbps", "مصطفى إسماعيل", "Mustafa Ismail"),
    };

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final Map<String, int[]> RUN = new ConcurrentHashMap<>();
    private static final Set<String> CANCEL = ConcurrentHashMap.newKeySet();
    /** Called on the main thread whenever a download advances or ends. */
    static volatile Runnable onChange;
    /** Set when a download stopped because of an error; the screen shows a message and clears it. */
    static volatile boolean failed;

    static Reciter current(Context c) {
        String f = Store.raw(c, "quran_reciter");
        for (Reciter r : RECITERS) if (r.folder.equals(f)) return r;
        return RECITERS[0];
    }

    static void setCurrent(Context c, Reciter r) {
        Store.putRaw(c, "quran_reciter", r.folder);
    }

    static String name(int s, int a) {
        return String.format(Locale.US, "%03d%03d", s, a);
    }

    static String url(Reciter r, int s, int a) {
        return "https://everyayah.com/data/" + r.folder + "/" + name(s, a) + ".mp3";
    }

    static File file(Context c, Reciter r, int s, int a) {
        return new File(new File(c.getApplicationContext().getFilesDir(), "audio/" + r.folder), name(s, a) + ".mp3");
    }

    static int have(Context c, Reciter r, QuranData.Surah s) {
        int n = 0;
        for (int a = 1; a <= s.ayahs; a++) if (file(c, r, s.n, a).length() > 0) n++;
        return n;
    }

    static void delete(Context c, Reciter r, QuranData.Surah s) {
        for (int a = 1; a <= s.ayahs; a++) file(c, r, s.n, a).delete();
    }

    private static String key(Reciter r, int s) {
        return r.folder + ":" + s;
    }

    static boolean running(Reciter r, int s) {
        return RUN.containsKey(key(r, s));
    }

    /** {done, total} of a running download, or null. */
    static int[] progress(Reciter r, int s) {
        return RUN.get(key(r, s));
    }

    static void cancel(Reciter r, int s) {
        CANCEL.add(key(r, s));
    }

    private static void changed() {
        MAIN.post(() -> {
            Runnable r = onChange;
            if (r != null) r.run();
        });
    }

    static void download(Context c, Reciter r, QuranData.Surah s) {
        final Context app = c.getApplicationContext();
        final String k = key(r, s.n);
        if (RUN.containsKey(k)) return;
        CANCEL.remove(k);
        RUN.put(k, new int[]{have(app, r, s), s.ayahs});
        changed();
        EX.execute(() -> {
            boolean ok = true;
            int done = 0;
            for (int a = 1; a <= s.ayahs; a++) {
                if (CANCEL.contains(k)) {
                    ok = true;
                    break;
                }
                File f = file(app, r, s.n, a);
                if (f.length() == 0 && !fetch(url(r, s.n, a), f)) {
                    ok = false;
                    break;
                }
                done++;
                RUN.put(k, new int[]{done, s.ayahs});
                if (a % 2 == 0 || a == s.ayahs) changed();
            }
            if (!ok) failed = true;
            CANCEL.remove(k);
            RUN.remove(k);
            changed();
        });
    }

    interface AyahCb {
        void done(boolean ok);
    }

    private static final ExecutorService ONE = Executors.newFixedThreadPool(2);

    static boolean haveAyah(Context c, Reciter r, int s, int a) {
        return file(c, r, s, a).length() > 0;
    }

    /** Saves a single ayah of the given reciter so it plays without internet; the callback runs on the main thread. */
    static void downloadAyah(Context c, Reciter r, int s, int a, AyahCb cb) {
        final Context app = c.getApplicationContext();
        final File f = file(app, r, s, a);
        if (f.length() > 0) {
            MAIN.post(() -> cb.done(true));
            return;
        }
        ONE.execute(() -> {
            final boolean ok = f.length() > 0 || fetch(url(r, s, a), f);
            MAIN.post(() -> cb.done(ok));
        });
    }

    private static boolean fetch(String u, File out) {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection con = null;
            File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
            try {
                File dir = out.getParentFile();
                if (dir != null && !dir.exists() && !dir.mkdirs()) return false;
                con = (HttpURLConnection) new URL(u).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(30000);
                con.setRequestProperty("User-Agent", "ClockStudio");
                if (con.getResponseCode() != 200) throw new Exception("http " + con.getResponseCode());
                try (InputStream in = con.getInputStream(); FileOutputStream fo = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[1 << 14];
                    int n;
                    while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                }
                if (tmp.length() < 500) throw new Exception("short file");
                if (out.exists()) out.delete();
                if (tmp.renameTo(out)) return true;
            } catch (Throwable ignored) {
                tmp.delete();
            } finally {
                if (con != null) con.disconnect();
            }
        }
        return false;
    }
}
