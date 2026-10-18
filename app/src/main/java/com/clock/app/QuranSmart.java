package com.clock.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * On-device verse suggestions. Every verse is scored against a topic using its own text and the words of its
 * explanation (Tafseer al-Muyassar, downloaded once and kept on the device), so the suggested verse is really about
 * what the person asked for. Verses about punishment are pushed down for comforting topics, long verses are avoided,
 * and recently suggested verses are not repeated. No server or AI service is involved after the one-time download.
 */
final class QuranSmart {
    private QuranSmart() {
    }

    static final class Topic {
        final String ar, en;
        final String[] keys;
        final boolean gentle;

        Topic(String ar, String en, boolean gentle, String keys) {
            this.ar = ar;
            this.en = en;
            this.gentle = gentle;
            String[] k = keys.split("\\s+");
            for (int i = 0; i < k.length; i++) k[i] = QuranData.norm(k[i].replace('_', ' '));
            this.keys = k;
        }

        String label() {
            return Lang.isAr() ? ar : en;
        }
    }

    static final Topic[] TOPICS = {
            new Topic("الصبر والثبات", "Patience & steadfastness", true,
                    "صبر اصبر صابر صابرين تصبر ثبت تثبيت يثبت بلاء ابتلاء نبلو مصيبه اصابتهم احتمل"),
            new Topic("الطمأنينة وزوال القلق", "Peace & relief from worry", true,
                    "تطمئن اطمئنان سكينه امن لا_تخف لا_تحزن خوف حزن يحزنون هم_يحزنون ضيق ينشرح صدر"),
            new Topic("الرزق والفرج", "Provision & relief", true,
                    "رزق يرزق رازق فرج مخرج يسر ييسر تيسير فضل بركه يغنيهم كفايه سعه"),
            new Topic("التوكل على الله", "Trusting Allah", true,
                    "توكل يتوكل توكلت حسبي حسبنا كفي وكيل فوض اعتصم"),
            new Topic("التوبة والمغفرة", "Repentance & forgiveness", true,
                    "توبه تاب توبوا استغفر استغفار مغفره يغفر غفور ذنوب ذنب رجع عفو تواب"),
            new Topic("الشكر والنعم", "Gratitude & blessings", true,
                    "شكر اشكر شاكر شكور نعمه نعم حمد الحمد فضل"),
            new Topic("الدعاء والاستجابة", "Supplication", true,
                    "دعاء ادع ادعوني دعوه يجيب استجاب مجيب قريب سال ربنا"),
            new Topic("رحمة الله وسعتها", "Allah's mercy", true,
                    "رحمه رحمتي رحيم رحمن رووف لطيف ودود حليم"),
            new Topic("التقوى والإخلاص", "Piety & sincerity", true,
                    "تقوي اتقوا يتق متقين خشيه يخشي اخلاص مخلصين مراقبه"),
            new Topic("الأمل وحسن الظن بالله", "Hope & good expectations", true,
                    "امل رجاء يرجو لا_تقنطوا بشري بشر يبشر خير حسن_الظن خيرا"),
            new Topic("العلم والتفكر", "Knowledge & reflection", false,
                    "علم يعلم العلماء تفكر يتفكرون عقل يعقلون تدبر يتدبرون اياتنا عبره اولي_الالباب"),
            new Topic("بر الوالدين والأسرة", "Parents & family", true,
                    "والدين والدين والده ابويه احسانا اهل زوج ازواج ازواجكم اولاد ذريه ذرياتنا مودة"),
            new Topic("الأخلاق والعدل والإحسان", "Character, justice & kindness", true,
                    "عدل اعدلوا قسط احسان محسنين صدق صادقين امانه كظم الغيظ عفو صفح حسنه"),
            new Topic("الآخرة والاستعداد لها", "The Hereafter", false,
                    "اخره جنه جنات حساب القيامه بعث لقاء_ربهم يوم_الدين ميزان"),
            new Topic("القوة والعزيمة والنصر", "Strength & determination", true,
                    "قوه قووا عزم جاهد جهاد نصر ينصركم غلبه ثبات لا_تهنوا"),
            new Topic("الشفاء والعافية", "Healing & wellbeing", true,
                    "شفاء يشفي شفا مرض عافيه ضر كشف يكشف الضر"),
            new Topic("العزاء والموت", "Comfort & loss", true,
                    "موت وفاه راجعون فراق فقد فناء كل_نفس_ذائقه"),
    };

    /** Everyday words (also dialect) that point to a topic when the person types how they feel. */
    private static final Map<String, Integer> WORDS = new HashMap<>();

    static {
        String[][] m = {
                {"0", "صبر تعب تعبان بلاء ابتلاء مصيبه وجع ظلم ظلموني مشاكل ضغوط"},
                {"1", "خايف خوف قلق قلقان توتر ضيق ضايق حزين زعلان حزن وحده وحيد اكتئاب كئيب هم هموم"},
                {"2", "رزق فقر ديون دين شغل وظيفه مال فلوس بطاله ضائقه محتاج"},
                {"3", "توكل مستقبل نتيجه امتحان نجاح قرار خطوه سفر"},
                {"4", "ذنب ذنوب غلط معصيه اخطات ندم تعبت_من_ذنبي"},
                {"5", "شكر نعمه نعم حمد سعيد فرحان فرح"},
                {"6", "دعاء ادعو دعا ادعي"},
                {"7", "رحمه رحيم مسامحه"},
                {"8", "تقوي اخلاص خشوع ايمان"},
                {"9", "امل تفاؤل بشري يأس ياس"},
                {"10", "علم تعلم دراسه تفكر"},
                {"11", "اهل والدين ماما بابا ام اب زوجه زوجي ولد اولاد اسره بيت"},
                {"12", "عدل اخلاق صدق امانه كذب غضب"},
                {"13", "اخره جنه نار حساب قيامه"},
                {"14", "قوه عزيمه همه نصر تحدي"},
                {"15", "مريض مرض شفاء عافيه صحه"},
                {"16", "ميت موت وفاه فقد فراق توفي"}
        };
        for (String[] row : m) {
            int idx = Integer.parseInt(row[0]);
            for (String w : row[1].split("\\s+")) WORDS.put(QuranData.norm(w), idx);
        }
    }

    /** Words that make a verse a poor answer to a comforting request. */
    private static final String[] HARSH = {"عذاب", "جحيم", "سعير", "اليم", "لعنه", "هلاك", "اهلكنا", "كفروا", "خسر", "شديد العقاب", "النار", "جهنم", "غضب"};
    private static final String[] HARSH_N = new String[HARSH.length];

    static {
        for (int i = 0; i < HARSH.length; i++) HARSH_N[i] = QuranData.norm(HARSH[i]);
    }

    interface Cb {
        /** The global ayah index chosen (or -1 when nothing fitted) and the topic label to show. */
        void done(int ayah, String label);
    }

    private static final String FILE = "muyassar_full.json";
    private static final String URL_ALL = "https://api.alquran.cloud/v1/quran/ar.muyassar";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile String[][] words;

    // ------------------------------------------------------------------ public

    /** Picks a verse for a topic (topic >= 0) or for a sentence typed by the person (topic < 0). Result arrives on the main thread. */
    static void pick(Context c, QuranData d, int topic, String free, Cb cb) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            int res = -1;
            String label = "";
            try {
                String[] keys;
                boolean gentle = true;
                if (topic >= 0 && topic < TOPICS.length) {
                    keys = TOPICS[topic].keys;
                    gentle = TOPICS[topic].gentle;
                    label = TOPICS[topic].label();
                } else {
                    List<String> k = new ArrayList<>();
                    Set<Integer> hit = new HashSet<>();
                    for (String raw : QuranData.norm(free == null ? "" : free.trim()).split("[^\\p{L}]+")) {
                        if (raw.length() < 2) continue;
                        String st = stem(raw);
                        Integer t = WORDS.get(raw);
                        if (t == null) t = WORDS.get(st);
                        if (t != null) hit.add(t);
                        if (st.length() >= 3) k.add(st);
                    }
                    for (int t : hit) k.addAll(Arrays.asList(TOPICS[t].keys));
                    if (!hit.isEmpty()) gentle = true;
                    for (int t : hit) if (!TOPICS[t].gentle) gentle = false;
                    keys = k.toArray(new String[0]);
                    label = free == null ? "" : free.trim();
                }
                String[][] tafsir = loadWords(app);
                res = score(app, d, keys, gentle, tafsir);
            } catch (Throwable ignored) {
            }
            final int out = res;
            final String lb = label;
            MAIN.post(() -> cb.done(out, lb));
        }, "quran-smart").start();
    }

    // ------------------------------------------------------------------ scoring

    private static int score(Context app, QuranData d, String[] keys, boolean gentle, String[][] tafsir) {
        if (keys.length == 0) return -1;
        String[] ks = new String[keys.length];
        for (int i = 0; i < keys.length; i++) ks[i] = keys[i].indexOf(' ') > 0 ? keys[i] : stem(keys[i]);
        String[] harsh = new String[HARSH_N.length];
        for (int i = 0; i < harsh.length; i++) harsh[i] = HARSH_N[i].indexOf(' ') > 0 ? HARSH_N[i] : stem(HARSH_N[i]);
        List<int[]> top = new ArrayList<>();
        for (int i = 0; i < QuranData.AYAHS; i++) {
            int len = d.text[i].length();
            if (len > 230) continue;
            String[] own = tokens(d.text[i]);
            String[] exp = tafsir == null ? new String[0] : tafsir[i];
            String ownJoined = " " + String.join(" ", own) + " ";
            String expJoined = " " + String.join(" ", exp) + " ";
            int s = 0;
            for (String k : ks) {
                if (k.length() < 3) continue;
                if (has(own, ownJoined, k)) s += 4;
                if (exp.length > 0 && has(exp, expJoined, k)) s += 2;
            }
            if (s == 0) continue;
            if (gentle) {
                int bad = 0;
                for (String h : harsh) {
                    if (ownJoined.contains(h) || expJoined.contains(h)) bad++;
                }
                s -= Math.min(bad, 3) * 4;
            }
            if (len > 150) s -= 1;
            if (s >= 4) top.add(new int[]{s, i});
        }
        if (top.isEmpty()) return -1;
        java.util.Collections.sort(top, (a, b) -> b[0] - a[0]);
        Set<Integer> recent = recent(app);
        List<Integer> pool = new ArrayList<>();
        int best = top.get(0)[0];
        for (int[] t : top) {
            if (pool.size() >= 14) break;
            if (t[0] < Math.max(4, best - 6)) break;
            if (!recent.contains(t[1])) pool.add(t[1]);
        }
        if (pool.isEmpty()) pool.add(top.get(0)[1]);
        int pick = pool.get(new Random().nextInt(pool.size()));
        remember(app, pick);
        return pick;
    }

    private static boolean has(String[] stems, String joined, String k) {
        if (k.indexOf(' ') > 0) return joined.contains(" " + k + " ") || joined.contains(k);
        for (String t : stems) {
            if (t.equals(k) || t.startsWith(k) || (k.length() >= 4 && t.contains(k))) return true;
        }
        return false;
    }

    /** Normalised word stems of a text. */
    private static String[] tokens(String text) {
        String[] raw = QuranData.norm(text).split("[^\\p{L}]+");
        List<String> out = new ArrayList<>(raw.length);
        for (String r : raw) {
            if (r.length() < 2) continue;
            out.add(stem(r));
        }
        return out.toArray(new String[0]);
    }

    private static final String[] PREFIX = {"وال", "فال", "بال", "كال", "لل", "ال", "و", "ف", "ب", "ل", "ك"};

    private static String stem(String w) {
        for (String p : PREFIX) {
            if (w.startsWith(p) && w.length() - p.length() >= 3) return w.substring(p.length());
        }
        return w;
    }

    // ------------------------------------------------------------------ history

    private static Set<Integer> recent(Context c) {
        Set<Integer> s = new HashSet<>();
        try {
            String raw = Store.raw(c, "quran_suggest_recent");
            if (raw != null && !raw.isEmpty()) for (String p : raw.split(",")) s.add(Integer.parseInt(p));
        } catch (Exception ignored) {
        }
        return s;
    }

    private static void remember(Context c, int ayah) {
        List<String> l = new ArrayList<>();
        String raw = Store.raw(c, "quran_suggest_recent");
        if (raw != null && !raw.isEmpty()) l.addAll(Arrays.asList(raw.split(",")));
        l.add(String.valueOf(ayah));
        while (l.size() > 40) l.remove(0);
        Store.putRaw(c, "quran_suggest_recent", String.join(",", l));
    }

    // ------------------------------------------------------------------ explanation words

    /** The words of every verse's explanation, or null when it was not downloaded yet and there is no connection. */
    private static String[][] loadWords(Context app) {
        String[][] w = words;
        if (w != null) return w;
        synchronized (QuranSmart.class) {
            if (words != null) return words;
            File f = new File(app.getFilesDir(), FILE);
            try {
                if (!f.exists() || f.length() < 200000) download(f);
                words = parse(f);
            } catch (Throwable e) {
                f.delete();
                words = null;
            }
            return words;
        }
    }

    private static void download(File out) throws Exception {
        HttpURLConnection con = null;
        File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
        try {
            con = (HttpURLConnection) new URL(URL_ALL).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(60000);
            con.setRequestProperty("User-Agent", "ClockStudio");
            if (con.getResponseCode() != 200) throw new Exception("http " + con.getResponseCode());
            try (InputStream in = con.getInputStream(); FileOutputStream fo = new FileOutputStream(tmp)) {
                byte[] buf = new byte[1 << 15];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            }
            if (tmp.length() < 200000) throw new Exception("short");
            if (out.exists()) out.delete();
            if (!tmp.renameTo(out)) throw new Exception("rename");
        } finally {
            if (con != null) con.disconnect();
            if (tmp.exists()) tmp.delete();
        }
    }

    private static String[][] parse(File f) throws Exception {
        StringBuilder sb = new StringBuilder((int) f.length());
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 1 << 16)) {
            char[] buf = new char[1 << 14];
            int r;
            while ((r = br.read(buf)) > 0) sb.append(buf, 0, r);
        }
        JSONArray surahs = new JSONObject(sb.toString()).getJSONObject("data").getJSONArray("surahs");
        String[][] out = new String[QuranData.AYAHS][];
        int g = 0;
        for (int s = 0; s < surahs.length(); s++) {
            JSONArray ay = surahs.getJSONObject(s).getJSONArray("ayahs");
            for (int a = 0; a < ay.length(); a++) {
                if (g >= QuranData.AYAHS) throw new Exception("too many");
                out[g++] = tokens(ay.getJSONObject(a).optString("text", ""));
            }
        }
        if (g != QuranData.AYAHS) throw new Exception("count " + g);
        return out;
    }
}
