package com.clock.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-device language engine (no network, no model download). It understands Arabic and English sentences and turns
 * them into tasks, alarms and timers, and answers questions about the day, tasks, alarms and prayer times.
 */
final class Smart {
    private Smart() {
    }

    static final int NONE = 0, TASK = 1, ALARM = 2, TIMER = 3;
    static final int MISS_NONE = 0, MISS_TIME = 1, MISS_DURATION = 2, MISS_TITLE = 3;

    static final class Result {
        int kind = NONE;
        String title = "";
        long due;
        boolean hasTime;
        int priority;
        int repeat;
        int hour = -1, minute;
        int days;
        long timerMs;
        int missing = MISS_NONE;
        boolean prayerUnset;
        boolean explicit;
    }

    static final class Reply {
        String text = "";
        String actionLabel;
        Runnable action;
        Runnable undo;
    }

    // ------------------------------------------------------------------ vocabulary

    private static final Map<String, Integer> WORDS = new HashMap<>();

    static {
        String[] ar = {"واحد", "واحده", "اثنين", "اثنان", "اتنين", "ثلاثه", "ثلاث", "تلاته", "اربعه", "اربع", "خمسه", "خمس",
                "سته", "ست", "سبعه", "سبع", "ثمانيه", "ثماني", "ثمان", "تمانيه", "تسعه", "تسع", "عشره", "عشر",
                "احدعشر", "اثنا عشر", "خمسه عشر", "خمس عشره", "عشرين", "ثلاثين", "اربعين", "خمسين", "ستين"};
        int[] arv = {1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 8, 8, 9, 9, 10, 10, 11, 12, 15, 15, 20, 30, 40, 50, 60};
        for (int i = 0; i < ar.length; i++) WORDS.put(ar[i], arv[i]);
        String[] en = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
                "fifteen", "twenty", "thirty", "forty", "fifty", "sixty"};
        int[] env = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 20, 30, 40, 50, 60};
        for (int i = 0; i < en.length; i++) WORDS.put(en[i], env[i]);
    }

    private static String wordAlt() {
        List<String> keys = new ArrayList<>(WORDS.keySet());
        Collections.sort(keys, (a, b) -> b.length() - a.length());
        StringBuilder b = new StringBuilder();
        for (String k : keys) {
            if (b.length() > 0) b.append('|');
            b.append(k);
        }
        return b.toString();
    }

    private static final String WA = wordAlt();
    private static final String NUM = "(\\d+(?:[.,]\\d+)?|" + WA + ")";
    private static final String FRAC = "(نصف|نص|ربع|ثلث|half|quarter)";
    private static final String UNIT = "(ثانيتين|ثانيه|ثواني|ثوان|seconds?|secs?|دقيقتين|دقيقه|دقايق|minutes?|mins?|ساعتين|ساعات|ساعه|hours?|hrs?"
            + "|يومين|ايام|يوم|days?|اسبوعين|اسابيع|اسبوع|weeks?|شهرين|اشهر|شهور|شهر|months?)";
    private static final String NL = "(?<!\\p{L})";
    private static final String NR = "(?!\\p{L})";
    private static final Pattern DUR = Pattern.compile("(?:" + NUM + "\\s*|" + FRAC + "\\s+(?:of\\s+an?\\s+)?)?" + UNIT + "(?:\\s*(?:و|and)?\\s*" + FRAC + NR + ")?" + NR);
    private static final Pattern REL_MARK = Pattern.compile(NL + "(بعد|خلال|in|after|within)\\s+");
    private static final Pattern SEP = Pattern.compile("\\s*(?:،|,|و|and)?\\s*");

    private static final String PERIOD = "(صباحا|صباح|الصبح|فجرا|ظهرا|عصرا|مساء|مساءا|ليلا|بالليل|الليل|am|pm|a\\.m\\.|p\\.m\\.|ص|م)";
    private static final Pattern T_PREFIX = Pattern.compile(NL + "(?:الساعه|ساعه|عند|at|@)\\s*(\\d{1,2}|" + WA + ")(?::(\\d{2}))?"
            + "(?:\\s*(و|الا)\\s*(?:" + FRAC + "|(\\d{1,2})\\s*(?:دقيقه|دقايق)))?(?:\\s*" + PERIOD + NR + ")?");
    private static final Pattern T_COLON = Pattern.compile("(?<![\\d:])(\\d{1,2}):(\\d{2})(?!\\d)(?:\\s*" + PERIOD + NR + ")?");
    private static final Pattern T_PERIOD = Pattern.compile("(?<![\\d:/.])(\\d{1,2})\\s*" + PERIOD + NR);
    private static final Pattern T_PART = Pattern.compile(NL + "(منتصف الليل|midnight|الظهيره|noon|tonight|الليله|صباحا|الصبح|ظهرا|عصرا|مساء|المساء|ليلا|morning|afternoon|evening)" + NR);

    private static final String[] PRAYER_KEYS = {"فجر", "شروق", "ظهر", "عصر", "مغرب", "عشاء"};
    private static final Pattern PRAYER = Pattern.compile("(?:(?:" + NUM + "\\s*)?" + UNIT + "\\s+)?(قبل|بعد|عند|مع|وقت|اذان|before|after|at)\\s*(?:صلاه\\s*|اذان\\s*|prayer\\s*)?(?:ال)?(فجر|ظهر|عصر|مغرب|عشاء|fajr|dhuhr|zuhr|asr|maghrib|isha)" + NR);

    private static final String WD_AR = "(احد|اثنين|اتنين|ثلاثاء|تلاتاء|اربعاء|خميس|جمعه|سبت)";
    private static final String WD_EN = "(sunday|sun|monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thu|friday|fri|saturday|sat)";
    private static final Pattern WEEKDAY = Pattern.compile("(?:(كل|every|each)\\s+)?(?:" + NL + "(?:يوم\\s+(?:ال)?|بال|لل|ال)" + WD_AR + NR + "|" + NL + "(?:on\\s+|next\\s+)?" + WD_EN + NR + ")(?:\\s+(?:القادم|الجاي|المقبل|next))?");

    private static final String MON_AR = "(كانون\\s+(?:ال)?ثاني|شباط|اذار|نيسان|ايار|حزيران|تموز|اب|ايلول|تشرين\\s+(?:ال)?اول|تشرين\\s+(?:ال)?ثاني|كانون\\s+(?:ال)?اول|يناير|فبراير|مارس|ابريل|مايو|يونيو|يوليو|اغسطس|سبتمبر|اكتوبر|نوفمبر|ديسمبر|january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec)";
    private static final Pattern DATE_NUM = Pattern.compile("(?<![\\d/:.-])(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?(?![\\d/:])");
    private static final Pattern DATE_MON = Pattern.compile("(?<![\\d:])(\\d{1,2})\\s*(?:من\\s+|of\\s+)?" + MON_AR + NR + "(?:\\s+(\\d{4}))?");

    private static final Pattern P_AFTER_TOMORROW = Pattern.compile(NL + "(?:بعد\\s+(?:غد|بكره|بكرا)|day after tomorrow)" + NR);
    private static final Pattern P_TOMORROW = Pattern.compile(NL + "(?:ل?غدا|ل?غد|بكره|بكرا|tomorrow|tmrw)" + NR);
    private static final Pattern P_TODAY = Pattern.compile(NL + "(?:اليوم|هذا اليوم|today)" + NR);
    private static final Pattern P_NEXT_WEEK = Pattern.compile(NL + "(?:الاسبوع\\s+(?:القادم|الجاي|المقبل)|next week)" + NR);
    private static final Pattern P_NEXT_MONTH = Pattern.compile(NL + "(?:الشهر\\s+(?:القادم|الجاي|المقبل)|next month)" + NR);
    private static final Pattern P_WEEKEND = Pattern.compile(NL + "(?:نهايه الاسبوع|weekend)" + NR);
    private static final Pattern R_DAILY = Pattern.compile(NL + "(?:كل يوم(?!\\s+(?:ال)?(?:احد|اثنين|ثلاثاء|اربعاء|خميس|جمعه|سبت))|يوميا|every ?day|daily)" + NR);
    private static final Pattern R_WEEKLY = Pattern.compile(NL + "(?:كل اسبوع|اسبوعيا|every week|weekly)" + NR);
    private static final Pattern R_MONTHLY = Pattern.compile(NL + "(?:كل شهر|شهريا|every month|monthly)" + NR);
    private static final Pattern R_WORKDAYS = Pattern.compile(NL + "(?:ايام العمل|ايام الدوام|ايام الاسبوع|weekdays|workdays)" + NR);

    private static final Pattern PRI3 = Pattern.compile(NL + "(?:عاجل|عاجله|ضروري|ضروريه|urgent|asap|critical|اولويه (?:قصوي|قصوى|عاليه جدا))" + NR + "|!{3}");
    private static final Pattern PRI2 = Pattern.compile(NL + "(?:مهم|مهمه جدا|important|high priority|اولويه عاليه)" + NR + "|!{2}");
    private static final Pattern PRI1 = Pattern.compile(NL + "(?:اولويه (?:منخفضه|متوسطه)|low priority|medium priority)" + NR + "|!");

    private static final Pattern KW_ALARM = Pattern.compile(NL + "(?:منبه|المنبه|صحيني|اصحيني|ايقظني|alarm|wake me(?: up)?)" + NR);
    private static final Pattern KW_TIMER = Pattern.compile(NL + "(?:موقت|المؤقت|الموقت|تايمر|timer|countdown)" + NR + "|^\\s*عداد" + NR);
    private static final Pattern LEAD = Pattern.compile("^\\s*(?:ذكرني|ذكروني|نبهني|ضيف|اضف|اضافه|انشئ|سوي|اعمل|اضبط|ضبط|شغل|ابدا|سجل|حط|ممكن|لو سمحت|من فضلك|الرجاء|يا|مهمه|مهام|منبه|المنبه|موقت|المؤقت|الموقت|تايمر|عداد|صحيني|اصحيني|ايقظني|remind me|reminder|please|alarm|timer|task|add|create|set|start|new|wake me up|wake me|set an|set a|a|an|the|ان|بان|ب|ل|لل|عن|عند|في|على|الي|الى|to|that|about|at|on|in|for|of|and|و|ثم|من|مع|by)" + NR);
    private static final Pattern TAIL = Pattern.compile("(?:" + NL + "(?:في|على|عند|الى|الي|من|مع|ب|ل|و|to|at|on|in|for|and|by|from|of|the|a|an)\\s*[,،.]?\\s*)$");

    // ------------------------------------------------------------------ text preparation

    private static String clean(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 0x064B && ch <= 0x065F) || ch == 0x0640 || ch == 0x0670) continue;
            if (ch >= 0x0660 && ch <= 0x0669) ch = (char) ('0' + (ch - 0x0660));
            else if (ch >= 0x06F0 && ch <= 0x06F9) ch = (char) ('0' + (ch - 0x06F0));
            else if (ch == 0x060C) ch = ',';
            b.append(ch);
        }
        return b.toString();
    }

    private static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\u0623':
                case '\u0625':
                case '\u0622':
                case '\u0671':
                    ch = '\u0627';
                    break;
                case '\u0649':
                    ch = '\u064A';
                    break;
                case '\u0629':
                    ch = '\u0647';
                    break;
                case '\u0624':
                    ch = '\u0648';
                    break;
                case '\u0626':
                    ch = '\u064A';
                    break;
                default:
                    ch = Character.toLowerCase(ch);
            }
            b.append(ch);
        }
        return b.toString();
    }

    /** Works on two strings of equal length: the normalised one for matching, the original one for the title. */
    private static final class Buf {
        final StringBuilder n, t;

        Buf(String raw) {
            String c = clean(raw);
            t = new StringBuilder(c);
            n = new StringBuilder(norm(c));
        }

        void blank(int s, int e) {
            for (int i = s; i < e; i++) {
                n.setCharAt(i, ' ');
                t.setCharAt(i, ' ');
            }
        }

        Matcher find(Pattern p) {
            return p.matcher(n);
        }

        /** Blanks the first match and returns it, or null. */
        Matcher take(Pattern p) {
            Matcher m = p.matcher(n);
            if (m.find()) {
                Matcher copy = p.matcher(n.toString());
                copy.find();
                blank(m.start(), m.end());
                return copy;
            }
            return null;
        }
    }

    private static double numOf(String s) {
        if (s == null) return 1;
        s = s.trim();
        Integer w = WORDS.get(s);
        if (w != null) return w;
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (Exception e) {
            return 1;
        }
    }

    private static double fracOf(String f) {
        if (f == null) return 0;
        if (f.startsWith("ربع") || f.startsWith("quarter")) return 0.25;
        if (f.startsWith("ثلث")) return 1.0 / 3.0;
        return 0.5;
    }

    private static long unitMs(String u) {
        if (u.startsWith("ثان") || u.startsWith("ثوان") || u.startsWith("sec")) return 1000L;
        if (u.startsWith("دقيق") || u.startsWith("دقايق") || u.startsWith("min")) return 60000L;
        if (u.startsWith("ساع") || u.startsWith("hour") || u.startsWith("hr")) return 3600000L;
        if (u.startsWith("يوم") || u.startsWith("ايام") || u.startsWith("day")) return 86400000L;
        if (u.startsWith("اسبوع") || u.startsWith("اسابيع") || u.startsWith("week")) return 7 * 86400000L;
        return 30 * 86400000L;
    }

    private static boolean dual(String u) {
        return u.endsWith("تين") || u.equals("يومين") || u.equals("اسبوعين") || u.equals("شهرين");
    }

    /** Length in milliseconds of one duration match (days / weeks / months are counted as 24 h / 7 d / 30 d). */
    private static long durationOf(Matcher m) {
        String unit = m.group(3);
        long u = unitMs(unit);
        double base;
        if (m.group(1) != null) base = numOf(m.group(1));
        else if (dual(unit)) base = 2;
        else base = 1;
        double ms;
        if (m.group(2) != null) ms = fracOf(m.group(2)) * u;
        else ms = base * u;
        if (m.group(4) != null) ms += fracOf(m.group(4)) * u;
        return (long) ms;
    }

    private static boolean hasDayUnit(Matcher m) {
        return unitMs(m.group(3)) >= 86400000L;
    }

    private static int weekdayOf(String w) {
        if (w == null) return 0;
        switch (w) {
            case "احد":
            case "sunday":
            case "sun":
                return Calendar.SUNDAY;
            case "اثنين":
            case "اتنين":
            case "monday":
            case "mon":
                return Calendar.MONDAY;
            case "ثلاثاء":
            case "تلاتاء":
            case "tuesday":
            case "tues":
            case "tue":
                return Calendar.TUESDAY;
            case "اربعاء":
            case "wednesday":
            case "wed":
                return Calendar.WEDNESDAY;
            case "خميس":
            case "thursday":
            case "thurs":
            case "thu":
                return Calendar.THURSDAY;
            case "جمعه":
            case "friday":
            case "fri":
                return Calendar.FRIDAY;
            case "سبت":
            case "saturday":
            case "sat":
                return Calendar.SATURDAY;
            default:
                return 0;
        }
    }

    private static int monthOf(String w) {
        String s = w.replaceAll("\\s+", " ");
        if (s.startsWith("كانون")) return s.endsWith("اول") ? 12 : 1;
        if (s.startsWith("تشرين")) return s.endsWith("اول") ? 10 : 11;
        switch (s) {
            case "شباط":
            case "فبراير":
            case "february":
            case "feb":
                return 2;
            case "اذار":
            case "مارس":
            case "march":
            case "mar":
                return 3;
            case "نيسان":
            case "ابريل":
            case "april":
            case "apr":
                return 4;
            case "ايار":
            case "مايو":
            case "may":
                return 5;
            case "حزيران":
            case "يونيو":
            case "june":
            case "jun":
                return 6;
            case "تموز":
            case "يوليو":
            case "july":
            case "jul":
                return 7;
            case "اب":
            case "اغسطس":
            case "august":
            case "aug":
                return 8;
            case "ايلول":
            case "سبتمبر":
            case "september":
            case "sept":
            case "sep":
                return 9;
            case "اكتوبر":
            case "october":
            case "oct":
                return 10;
            case "نوفمبر":
            case "november":
            case "nov":
                return 11;
            case "ديسمبر":
            case "december":
            case "dec":
                return 12;
            default:
                return 1;
        }
    }

    // ------------------------------------------------------------------ parsing

    static Result parse(Context c, String raw) {
        return parse(c, raw, System.currentTimeMillis());
    }

    static Result parse(Context c, String raw, long now) {
        Result r = new Result();
        if (raw == null) return r;
        Buf b = new Buf(raw.trim());
        if (b.n.length() == 0) return r;

        boolean wantAlarm = b.find(KW_ALARM).find();
        boolean wantTimer = !wantAlarm && b.find(KW_TIMER).find();

        int repeat = Task.REPEAT_NONE;
        int alarmDays = 0;
        if (b.take(R_DAILY) != null) {
            repeat = Task.REPEAT_DAILY;
            alarmDays = Alarm.EVERY_DAY;
        }
        if (b.take(R_WORKDAYS) != null) {
            alarmDays = Lang.isAr() ? Alarm.SUN_THU : Alarm.MON_FRI;
            repeat = Task.REPEAT_DAILY;
        }
        if (b.take(R_WEEKLY) != null) repeat = Task.REPEAT_WEEKLY;
        if (b.take(R_MONTHLY) != null) repeat = Task.REPEAT_MONTHLY;

        // priority
        if (b.take(PRI3) != null) r.priority = 3;
        else if (b.take(PRI2) != null) r.priority = 2;
        else if (b.take(PRI1) != null) r.priority = 1;

        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(now);
        Calendar date = null; // day chosen by the sentence (time part cleared)
        boolean dateExplicit = false;

        // day words
        Matcher m;
        if (b.take(P_AFTER_TOMORROW) != null) {
            date = dayPlus(now, 2);
            dateExplicit = true;
        } else if (b.take(P_TOMORROW) != null) {
            date = dayPlus(now, 1);
            dateExplicit = true;
        } else if (b.take(P_NEXT_WEEK) != null) {
            date = dayPlus(now, 7);
            dateExplicit = true;
        } else if (b.take(P_NEXT_MONTH) != null) {
            date = dayPlus(now, 0);
            date.add(Calendar.MONTH, 1);
            dateExplicit = true;
        } else if (b.take(P_WEEKEND) != null) {
            date = nextWeekday(now, Lang.isAr() ? Calendar.FRIDAY : Calendar.SATURDAY, true);
            dateExplicit = true;
        } else if (b.take(P_TODAY) != null) {
            date = dayPlus(now, 0);
            dateExplicit = true;
        }

        // prayer anchored time ("after Asr", "30 minutes before Maghrib")
        long prayerAt = 0;
        m = b.take(PRAYER);
        if (m != null) {
            String key = m.group(4);
            int idx = prayerIndex(key);
            long off = 0;
            boolean hasAmount = m.group(2) != null;
            if (hasAmount) {
                Matcher dm = DUR.matcher(m.group().trim().toLowerCase(Locale.ROOT));
                if (dm.find()) off = durationOf(dm);
            }
            String rel = m.group(3);
            boolean before = "قبل".equals(rel) || "before".equals(rel);
            boolean after = "بعد".equals(rel) || "after".equals(rel);
            if (!hasAmount && before) off = 10 * 60000L;
            else if (!hasAmount && after) off = 10 * 60000L;
            PrayerTimes.Cfg g = PrayerTimes.cfg(c);
            if (!g.configured) {
                r.prayerUnset = true;
            } else {
                prayerAt = prayerMoment(c, g, idx, date, now, before ? -off : after ? off : 0);
            }
        }

        // explicit calendar dates
        if (date == null) {
            m = b.take(DATE_MON);
            if (m != null) {
                date = explicitDate(now, parseInt(m.group(1)), monthOf(m.group(2)), m.group(3) == null ? 0 : parseInt(m.group(3)));
                dateExplicit = true;
            } else {
                m = b.take(DATE_NUM);
                if (m != null) {
                    int d = parseInt(m.group(1)), mo = parseInt(m.group(2));
                    if (mo > 12 && d <= 12) {
                        int t = d;
                        d = mo;
                        mo = t;
                    }
                    int y = m.group(3) == null ? 0 : parseInt(m.group(3));
                    if (y > 0 && y < 100) y += 2000;
                    if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
                        date = explicitDate(now, d, mo, y);
                        dateExplicit = true;
                    }
                }
            }
        }

        // weekdays ("on Thursday", "every Friday")
        List<Integer> wds = new ArrayList<>();
        boolean everyWeekday = false;
        while (true) {
            m = b.take(WEEKDAY);
            if (m == null) break;
            int wd = weekdayOf(m.group(2) != null ? m.group(2) : m.group(3));
            if (wd == 0) continue;
            wds.add(wd);
            if (m.group(1) != null) everyWeekday = true;
        }
        if (!wds.isEmpty()) {
            if (wds.size() > 1) {
                for (int w : wds) alarmDays |= 1 << (w - 1);
                if (date == null) {
                    date = nextWeekday(now, wds.get(0), false);
                    dateExplicit = true;
                }
            } else {
                int w = wds.get(0);
                if (everyWeekday) {
                    alarmDays |= 1 << (w - 1);
                    repeat = Task.REPEAT_WEEKLY;
                }
                if (date == null) {
                    date = nextWeekday(now, w, false);
                    dateExplicit = true;
                }
            }
        }

        // relative amounts ("in 2 hours", "after 3 days")
        long relMs = 0;
        boolean relFine = false;
        boolean relFound = false;
        Matcher rm = b.find(REL_MARK);
        if (!wantTimer) {
            int from = 0;
            while (rm.find(from)) {
                int pos = rm.end();
                int end = pos;
                boolean any = false;
                long total = 0;
                boolean fine = false;
                while (true) {
                    Matcher dm = DUR.matcher(b.n);
                    dm.region(end, b.n.length());
                    if (!dm.lookingAt()) break;
                    total += durationOf(dm);
                    if (!hasDayUnit(dm)) fine = true;
                    end = dm.end();
                    any = true;
                    Matcher sm = SEP.matcher(b.n);
                    sm.region(end, b.n.length());
                    if (sm.lookingAt()) {
                        Matcher nx = DUR.matcher(b.n);
                        nx.region(sm.end(), b.n.length());
                        if (nx.lookingAt()) end = sm.end();
                    }
                }
                if (any) {
                    b.blank(rm.start(), end);
                    relMs += total;
                    relFine |= fine;
                    relFound = true;
                    from = end;
                    rm = b.find(REL_MARK);
                } else {
                    from = rm.end();
                }
            }
        }

        // timer: duration without marker
        if (wantTimer) {
            long total = 0;
            Matcher dm = b.find(DUR);
            int guard = 0;
            while (dm.find() && guard++ < 6) {
                total += durationOf(dm);
                b.blank(dm.start(), dm.end());
                dm = b.find(DUR);
            }
            if (total == 0) {
                Matcher bare = Pattern.compile(NL + "(\\d+)" + NR).matcher(b.n);
                if (bare.find()) {
                    total = Long.parseLong(bare.group(1)) * 60000L;
                    b.blank(bare.start(), bare.end());
                }
            }
            r.kind = TIMER;
            r.timerMs = total;
            r.explicit = true;
            if (total <= 0) r.missing = MISS_DURATION;
            r.title = finishTitle(b);
            return r;
        }

        // clock time
        int hour = -1, minute = 0;
        boolean periodGiven = false;
        String period = null;
        m = b.take(T_PREFIX);
        if (m != null) {
            hour = (int) numOf(m.group(1));
            minute = m.group(2) == null ? 0 : parseInt(m.group(2));
            String conj = m.group(3);
            if (conj != null) {
                int add = m.group(4) != null ? Math.round((float) (fracOf(m.group(4)) * 60)) : m.group(5) != null ? parseInt(m.group(5)) : 0;
                if ("الا".equals(conj)) {
                    minute -= add;
                } else {
                    minute += add;
                }
            }
            period = m.group(6);
        } else {
            m = b.take(T_COLON);
            if (m != null) {
                hour = parseInt(m.group(1));
                minute = parseInt(m.group(2));
                period = m.group(3);
            } else {
                m = b.take(T_PERIOD);
                if (m != null) {
                    hour = parseInt(m.group(1));
                    period = m.group(2);
                }
            }
        }
        int partHour = -1;
        if (hour < 0 && prayerAt == 0 && !relFound) {
            Matcher pm = b.take(T_PART);
            if (pm != null) partHour = partHourOf(pm.group(1));
        }
        if (hour >= 0) {
            while (minute < 0) {
                minute += 60;
                hour -= 1;
            }
            if (hour < 0) hour += 12;
            periodGiven = period != null;
        }

        // assemble
        boolean wantTime = hour >= 0 || partHour >= 0;
        if (prayerAt > 0) {
            r.due = prayerAt;
            r.hasTime = true;
        } else if (relFound) {
            if (relFine) {
                r.hasTime = true;
                r.due = now + relMs;
            } else {
                int days = (int) Math.round(relMs / 86400000.0);
                Calendar d = dayPlus(now, 0);
                if (relMs % (30 * 86400000L) == 0 && relMs >= 30 * 86400000L) d.add(Calendar.MONTH, (int) (relMs / (30 * 86400000L)));
                else d.add(Calendar.DAY_OF_YEAR, days);
                date = d;
                dateExplicit = true;
                if (wantTime) {
                    applyClock(r, date, hour, minute, period, partHour, now, true);
                } else {
                    r.due = date.getTimeInMillis();
                    r.hasTime = false;
                }
            }
        } else if (wantTime) {
            Calendar d = date;
            boolean explicitDay = dateExplicit;
            if (d == null) d = dayPlus(now, 0);
            applyClock(r, d, hour, minute, period, partHour, now, explicitDay);
        } else if (date != null) {
            r.due = date.getTimeInMillis();
            r.hasTime = false;
        }

        // intent
        if (wantAlarm) {
            r.kind = ALARM;
            if (!r.hasTime) {
                r.missing = MISS_TIME;
            } else {
                Calendar a = Calendar.getInstance();
                a.setTimeInMillis(r.due);
                r.hour = a.get(Calendar.HOUR_OF_DAY);
                r.minute = a.get(Calendar.MINUTE);
                r.days = alarmDays;
                if (r.days == 0 && repeat == Task.REPEAT_WEEKLY) r.days = 1 << (a.get(Calendar.DAY_OF_WEEK) - 1);
                if (r.days == 0 && repeat == Task.REPEAT_DAILY) r.days = Alarm.EVERY_DAY;
                // an alarm only knows a time and weekdays: a faraway date becomes a timed task
                if (r.days == 0 && dateExplicit) {
                    Alarm probe = new Alarm();
                    probe.hour = r.hour;
                    probe.minute = r.minute;
                    if (!sameDayAs(r.due, probe.next(now))) {
                        r.kind = TASK;
                        repeat = Task.REPEAT_NONE;
                    }
                }
            }
            r.repeat = repeat;
            r.title = finishTitle(b);
            if (r.kind == TASK && r.title.isEmpty()) r.title = c.getString(R.string.ai_item_alarm);
            r.explicit = true;
            return r;
        }

        r.kind = TASK;
        r.repeat = r.due > 0 ? repeat : Task.REPEAT_NONE;
        r.title = finishTitle(b);
        r.explicit = lead;
        if (r.title.isEmpty()) r.missing = MISS_TITLE;
        return r;
    }

    private static boolean sameDayAs(long a, long b) {
        if (b <= 0) return false;
        return Fmt.startOfDay(a) == Fmt.startOfDay(b);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static Calendar dayPlus(long now, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(Fmt.startOfDay(now));
        c.add(Calendar.DAY_OF_YEAR, days);
        return c;
    }

    private static Calendar nextWeekday(long now, int wd, boolean strictlyAfterToday) {
        Calendar c = dayPlus(now, 0);
        int add = (wd - c.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        if (add == 0 && strictlyAfterToday) add = 7;
        c.add(Calendar.DAY_OF_YEAR, add);
        return c;
    }

    private static Calendar explicitDate(long now, int day, int month, int year) {
        Calendar today = dayPlus(now, 0);
        Calendar c = dayPlus(now, 0);
        int y = year > 0 ? year : today.get(Calendar.YEAR);
        c.set(y, month - 1, Math.max(1, Math.min(31, day)), 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (year == 0 && c.getTimeInMillis() < today.getTimeInMillis()) c.add(Calendar.YEAR, 1);
        return c;
    }

    private static int partHourOf(String w) {
        if (w.startsWith("منتصف") || w.equals("midnight")) return 24;
        if (w.equals("الظهيره") || w.equals("noon") || w.equals("ظهرا")) return 12;
        if (w.equals("عصرا") || w.equals("afternoon")) return 16;
        if (w.equals("مساء") || w.equals("المساء") || w.equals("evening")) return 18;
        if (w.equals("الليله") || w.equals("ليلا") || w.equals("tonight")) return 21;
        return 8;
    }

    private static int prayerIndex(String key) {
        if (key == null) return PrayerTimes.DHUHR;
        if (key.startsWith("فجر") || key.equals("fajr")) return PrayerTimes.FAJR;
        if (key.startsWith("ظهر") || key.equals("dhuhr") || key.equals("zuhr")) return PrayerTimes.DHUHR;
        if (key.startsWith("عصر") || key.equals("asr")) return PrayerTimes.ASR;
        if (key.startsWith("مغرب") || key.equals("maghrib")) return PrayerTimes.MAGHRIB;
        return PrayerTimes.ISHA;
    }

    /** Date-aware prayer instant (+ offset); with no day given it picks the first one still ahead. */
    private static long prayerMoment(Context c, PrayerTimes.Cfg g, int idx, Calendar date, long now, long offMs) {
        Calendar zc = Calendar.getInstance(TimeZone.getTimeZone(g.zone));
        zc.setTimeInMillis(date != null ? date.getTimeInMillis() + 12 * 3600000L : now);
        for (int i = 0; i < 3; i++) {
            PrayerTimes.Day d = PrayerTimes.day(c, g, PrayerTimes.ymd(zc));
            if (d != null) {
                try {
                    long t = PrayerTimes.timeAt(g, PrayerTimes.ymd(zc), d.hm[idx]) + offMs;
                    if (date != null || t > now) return t;
                } catch (Exception ignored) {
                }
            }
            if (date != null) break;
            zc.add(Calendar.DAY_OF_YEAR, 1);
        }
        return 0;
    }

    private static void applyClock(Result r, Calendar day, int hour, int minute, String period, int partHour, long now, boolean dayChosen) {
        int h;
        int min = minute;
        if (hour < 0) {
            if (partHour == 24) {
                h = 0;
                Calendar d = (Calendar) day.clone();
                if (!dayChosen) d.add(Calendar.DAY_OF_YEAR, 1);
                else d.add(Calendar.DAY_OF_YEAR, 1);
                day = d;
            } else {
                h = partHour;
            }
            min = 0;
        } else {
            h = hour;
            String p = period == null ? "" : period;
            boolean pm = p.equals("pm") || p.equals("p.m.") || p.equals("م") || p.startsWith("مساء") || p.equals("عصرا");
            boolean am = p.equals("am") || p.equals("a.m.") || p.equals("ص") || p.startsWith("صباح") || p.equals("الصبح") || p.equals("فجرا");
            boolean noon = p.equals("ظهرا");
            boolean night = p.equals("ليلا") || p.equals("بالليل") || p.equals("الليل");
            if (h > 12 || h == 0) {
                // 24 hour already
            } else if (pm) {
                if (h < 12) h += 12;
            } else if (am) {
                if (h == 12) h = 0;
            } else if (noon) {
                if (h < 6) h += 12;
            } else if (night) {
                if (h == 12) h = 0;
                else if (h >= 6) h += 12;
            } else {
                // no marker: take the next occurrence today, otherwise a daytime guess
                int amH = h == 12 ? 0 : h;
                int pmH = h == 12 ? 12 : h + 12;
                Calendar a = (Calendar) day.clone();
                a.set(Calendar.HOUR_OF_DAY, amH);
                a.set(Calendar.MINUTE, min);
                Calendar p2 = (Calendar) day.clone();
                p2.set(Calendar.HOUR_OF_DAY, pmH);
                p2.set(Calendar.MINUTE, min);
                if (!dayChosen) {
                    if (a.getTimeInMillis() > now) h = amH;
                    else if (p2.getTimeInMillis() > now) h = pmH;
                    else h = amH;
                } else {
                    h = (h >= 1 && h <= 6) ? pmH : (h == 12 ? 12 : h);
                }
            }
        }
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, h)));
        c.set(Calendar.MINUTE, Math.max(0, Math.min(59, min)));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (!dayChosen && c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1);
        r.due = c.getTimeInMillis();
        r.hasTime = true;
    }

    private static boolean lead;

    private static String finishTitle(Buf b) {
        // sentence-level intent words are not part of the title
        Matcher k = KW_ALARM.matcher(b.n);
        while (k.find()) b.blank(k.start(), k.end());
        k = KW_TIMER.matcher(b.n);
        while (k.find()) b.blank(k.start(), k.end());
        lead = false;
        for (int guard = 0; guard < 8; guard++) {
            Matcher m = LEAD.matcher(b.n);
            if (!m.find() || m.end() == 0) break;
            lead = true;
            b.blank(m.start(), m.end());
        }
        for (int guard = 0; guard < 4; guard++) {
            String trimmed = b.n.toString().replaceAll("\\s+$", "");
            Matcher t = TAIL.matcher(trimmed);
            if (!t.find() || t.start() >= trimmed.length()) break;
            b.blank(t.start(), trimmed.length());
        }
        String out = b.t.toString().replaceAll("\\s+", " ").trim();
        out = out.replaceAll("^[\\s,،.:;\\-]+|[\\s,،.:;\\-]+$", "");
        if (out.length() > 0) out = out.substring(0, 1).toUpperCase(Locale.ROOT) + out.substring(1);
        return out;
    }

    // ------------------------------------------------------------------ doing what was asked

    /** Creates the task / alarm / timer described by {@code r}; returns the confirmation (with an undo when possible). */
    static Reply create(final Context c, String original, Result r) {
        Reply out = new Reply();
        if (r.prayerUnset) {
            out.text = c.getString(R.string.ai_no_prayer_cfg);
            out.actionLabel = c.getString(R.string.ai_open_prayer);
            out.action = () -> c.startActivity(new android.content.Intent(c, PrayerActivity.class));
            return out;
        }
        if (r.kind == TIMER) {
            if (r.timerMs <= 0) {
                out.text = c.getString(R.string.ai_need_duration);
                return out;
            }
            final TimerItem t = Timers.start(c, r.timerMs, r.title, 0, "");
            String label = r.title.isEmpty() ? "" : " · " + r.title;
            out.text = c.getString(R.string.ai_timer_added, Fmt.spoken(c, r.timerMs), label);
            out.undo = () -> Timers.cancel(c, t.id);
            return out;
        }
        if (r.kind == ALARM) {
            if (r.missing == MISS_TIME || r.hour < 0) {
                out.text = c.getString(R.string.ai_need_time);
                return out;
            }
            Alarm a = new Alarm();
            a.hour = r.hour;
            a.minute = r.minute;
            a.days = r.days;
            a.label = r.title;
            a.vibrate = Store.defaultVibrate(c);
            a.ramp = Store.defaultRamp(c);
            Alarms.save(c, a);
            final int id = a.id;
            String label = r.title.isEmpty() ? "" : " · " + r.title;
            out.text = c.getString(R.string.ai_alarm_added, Bedtime.label(c, r.hour, r.minute), Fmt.repeat(c, r.days), label);
            out.undo = () -> Alarms.delete(c, id);
            return out;
        }
        if (r.kind == TASK) {
            if (r.missing == MISS_TITLE) {
                out.text = c.getString(R.string.ai_need_title);
                return out;
            }
            Task t = new Task();
            t.title = r.title;
            t.due = r.due;
            t.hasTime = r.hasTime;
            t.priority = r.priority;
            t.repeat = r.repeat;
            t.remind = r.hasTime;
            Tasks.save(c, t);
            final int id = t.id;
            String when = t.due > 0 ? " · " + Fmt.taskDue(c, t) : "";
            String pr = "";
            if (t.priority > 0) {
                StringBuilder s = new StringBuilder(" ");
                for (int i = 0; i < t.priority; i++) s.append('!');
                pr = s.toString();
            }
            out.text = c.getString(R.string.ai_task_added, t.title + pr, when);
            out.undo = () -> Tasks.delete(c, id);
            return out;
        }
        out.text = c.getString(R.string.ai_unknown);
        return out;
    }

    // ------------------------------------------------------------------ questions

    private static boolean has(String n, String... keys) {
        for (String k : keys) if (n.contains(k)) return true;
        return false;
    }

    /** Understands questions and commands about the app; returns null when the sentence is not one of them. */
    static Reply answer(final Context c, String raw) {
        String n = norm(clean(raw == null ? "" : raw)).trim();
        if (n.isEmpty()) return null;
        Reply r = new Reply();

        if (has(n, "رتب يومي", "خطط يومي", "خطه يومي", "خطه اليوم", "ملخص", "plan my day", "my day", "summary", "daily plan", "ماذا لدي", "شو عندي")) {
            r.text = plan(c);
            return r;
        }
        if (has(n, "المتاخره", "متاخره", "فاتتني", "overdue", "late tasks", "missed tasks")) {
            List<Task> late = overdue(c);
            if (late.isEmpty()) {
                r.text = c.getString(R.string.ai_late_none);
            } else {
                r.text = c.getString(R.string.ai_late_head, late.size()) + "\n" + lines(c, late);
            }
            return r;
        }
        if (has(n, "مهامي", "مهام اليوم", "مهمات اليوم", "my tasks", "today's tasks", "tasks today", "todays tasks", "what's today")) {
            List<Task> today = Tasks.filter(c, Tasks.TODAY);
            if (today.isEmpty()) {
                r.text = c.getString(R.string.ai_today_none);
            } else {
                r.text = c.getString(R.string.ai_today_head, today.size()) + "\n" + lines(c, today);
            }
            return r;
        }
        if (has(n, "اوقات الصلاه", "مواقيت", "prayer times", "اوقات الصلوات")) {
            r.text = prayerDay(c, r);
            return r;
        }
        if (has(n, "الصلاه القادمه", "متي الصلاه", "كم باقي", "باقي علي", "next prayer", "when is the next prayer", "متي الاذان", "الاذان القادم", "كم يتبقي")) {
            r.text = nextPrayer(c, r);
            return r;
        }
        if (has(n, "المنبه القادم", "منبهاتي", "next alarm", "my alarms", "متي منبهي", "متي المنبه")) {
            long t = Alarms.nextTrigger(c);
            r.text = t <= 0 ? c.getString(R.string.ai_no_alarm)
                    : c.getString(R.string.ai_next_alarm, Fmt.dayLabel(c, t) + " " + Fmt.time(c, t, TimeZone.getDefault(), false) + " " + Fmt.ampm(c, hourOf(t)), Fmt.untilText(c, t - System.currentTimeMillis()));
            return r;
        }
        if (has(n, "احذف المكتمله", "امسح المكتمله", "نظف المكتمله", "clear completed", "delete completed", "remove completed")) {
            int cnt = 0;
            for (Task t : Tasks.filter(c, Tasks.DONE)) {
                Tasks.delete(c, t.id);
                cnt++;
            }
            r.text = c.getString(R.string.ai_cleared, cnt);
            return r;
        }
        Matcher dm = Pattern.compile("^(?:خلصت|انهيت|اكملت|انجزت|تمت|تم|خلص|i finished|i completed|i did|finished|completed|done with|done|complete|mark)\\s+(.+)$").matcher(n);
        if (dm.find()) {
            Task t = bestMatch(c, dm.group(1));
            if (t != null) {
                if (!t.done) Tasks.toggle(c, t.id);
                r.text = c.getString(R.string.ai_done_ok, t.title);
                final int id = t.id;
                r.undo = () -> {
                    Task x = Tasks.get(c, id);
                    if (x != null && x.done) Tasks.toggle(c, id);
                };
                return r;
            }
            if (has(n, "خلصت", "انهيت", "اكملت", "انجزت", "finished", "completed")) {
                r.text = c.getString(R.string.ai_done_none);
                return r;
            }
        }
        if (has(n, "مساعدة", "مساعده", "ماذا تستطيع", "ماذا تفعل", "help", "what can you do")) {
            r.text = c.getString(R.string.ai_help);
            return r;
        }
        return null;
    }

    private static int hourOf(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    private static Task bestMatch(Context c, String phrase) {
        List<String> words = new ArrayList<>();
        for (String w : phrase.split("\\s+")) {
            String x = w.replaceAll("^(?:ال|لل|ب|ل)", "");
            if (x.length() >= 3) words.add(x);
        }
        if (words.isEmpty()) return null;
        Task best = null;
        int bestScore = 0;
        for (Task t : Tasks.all(c)) {
            if (t.done) continue;
            String title = norm(clean(t.title));
            int score = 0;
            for (String w : words) if (title.contains(w)) score++;
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    static List<Task> overdue(Context c) {
        List<Task> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        long sod = Fmt.startOfDay(now);
        for (Task t : Tasks.filter(c, Tasks.SCHEDULED)) {
            if (t.due > 0 && (t.hasTime ? t.due < now : t.due < sod)) out.add(t);
        }
        return out;
    }

    private static String lines(Context c, List<Task> list) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Task t : list) {
            if (n++ >= 8) {
                b.append("\n\u2022 \u2026");
                break;
            }
            if (b.length() > 0) b.append('\n');
            b.append("\u2022 ");
            for (int i = 0; i < t.priority; i++) b.append('!');
            if (t.priority > 0) b.append(' ');
            b.append(t.title);
            String due = Fmt.taskDue(c, t);
            if (!due.isEmpty()) b.append(" \u00B7 ").append(due);
        }
        return b.toString();
    }

    private static String nextPrayer(Context c, Reply r) {
        PrayerTimes.Cfg g = PrayerTimes.cfg(c);
        if (!g.configured) return c.getString(R.string.ai_no_prayer_cfg);
        PrayerTimes.Next nx = PrayerTimes.next(c);
        if (nx == null) return c.getString(R.string.prayer_no_data);
        String[] names = c.getResources().getStringArray(R.array.prayer_names);
        r.actionLabel = c.getString(R.string.ai_open_prayer);
        r.action = () -> c.startActivity(new android.content.Intent(c, PrayerActivity.class));
        return c.getString(R.string.ai_next_prayer, names[nx.idx], PrayerTimes.label(c, nx.at, g.zone), Fmt.untilText(c, nx.at - System.currentTimeMillis()));
    }

    private static String prayerDay(Context c, Reply r) {
        PrayerTimes.Cfg g = PrayerTimes.cfg(c);
        if (!g.configured) return c.getString(R.string.ai_no_prayer_cfg);
        PrayerTimes.Day d = PrayerTimes.day(c, g, PrayerTimes.today(g));
        if (d == null) return c.getString(R.string.prayer_no_data);
        String[] names = c.getResources().getStringArray(R.array.prayer_names);
        StringBuilder b = new StringBuilder(g.place);
        for (int i = 0; i < 6; i++) b.append('\n').append("\u2022 ").append(names[i]).append(" \u00B7 ").append(PrayerTimes.hm(c, d.hm[i]));
        r.actionLabel = c.getString(R.string.ai_open_prayer);
        r.action = () -> c.startActivity(new android.content.Intent(c, PrayerActivity.class));
        return b.toString();
    }

    /** One-sentence status for the Tasks header. */
    static String insight(Context c) {
        int late = overdue(c).size();
        int today = Tasks.count(c, Tasks.TODAY);
        if (late > 0) return c.getString(R.string.ai_insight_late, late, today);
        if (today > 0) return c.getString(R.string.ai_insight_today, today);
        return "";
    }

    /** Today's timeline: alarms, prayers and timed tasks in order, then the tasks without a time, then advice. */
    static String plan(Context c) {
        long now = System.currentTimeMillis();
        long sod = Fmt.startOfDay(now);
        long eod = sod + 86400000L;
        final List<long[]> keys = new ArrayList<>();
        final List<String> text = new ArrayList<>();
        for (Alarm a : Alarms.all(c)) {
            if (!a.enabled) continue;
            long t = a.next(sod - 1);
            if (t > 0 && t < eod && t >= now - 3600000L) {
                keys.add(new long[]{t, text.size()});
                text.add(Bedtime.label(c, a.hour, a.minute) + "  " + c.getString(R.string.ai_item_alarm) + (a.label.isEmpty() ? "" : " \u00B7 " + a.label));
            }
        }
        PrayerTimes.Cfg g = PrayerTimes.cfg(c);
        if (g.configured) {
            PrayerTimes.Day d = PrayerTimes.day(c, g, PrayerTimes.today(g));
            String[] names = c.getResources().getStringArray(R.array.prayer_names);
            if (d != null) {
                for (int i : PrayerTimes.PRAYERS) {
                    try {
                        long t = PrayerTimes.timeAt(g, PrayerTimes.today(g), d.hm[i]);
                        if (t >= now - 1800000L) {
                            keys.add(new long[]{t, text.size()});
                            text.add(PrayerTimes.hm(c, d.hm[i]) + "  " + names[i]);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        List<Task> noTime = new ArrayList<>();
        for (Task t : Tasks.filter(c, Tasks.TODAY)) {
            if (t.hasTime && t.due >= sod) {
                keys.add(new long[]{t.due, text.size()});
                StringBuilder p = new StringBuilder();
                for (int i = 0; i < t.priority; i++) p.append('!');
                text.add(Fmt.time(c, t.due, TimeZone.getDefault(), false) + " " + Fmt.ampm(c, hourOf(t.due)) + "  " + (p.length() > 0 ? p + " " : "") + t.title);
            } else if (!t.hasTime && t.due >= sod) {
                noTime.add(t);
            }
        }
        Collections.sort(keys, (x, y) -> Long.compare(x[0], y[0]));
        StringBuilder b = new StringBuilder(c.getString(R.string.ai_plan_head));
        for (long[] k : keys) b.append("\n\u2022 ").append(text.get((int) k[1]));
        if (!noTime.isEmpty()) {
            b.append("\n\n").append(c.getString(R.string.ai_plan_anytime));
            b.append('\n').append(lines(c, noTime));
        }
        List<Task> late = overdue(c);
        if (keys.isEmpty() && noTime.isEmpty() && late.isEmpty()) {
            b.append("\n").append(c.getString(R.string.ai_today_none));
        }
        if (!late.isEmpty()) {
            b.append("\n\n").append(c.getString(R.string.ai_late_head, late.size())).append('\n').append(lines(c, late));
            b.append("\n\n").append(c.getString(R.string.ai_tip_late, late.get(0).title));
        } else {
            Task top = null;
            for (Task t : Tasks.filter(c, Tasks.TODAY)) {
                if (top == null || t.priority > top.priority) top = t;
            }
            if (top != null && top.priority > 0) b.append("\n\n").append(c.getString(R.string.ai_tip_top, top.title));
        }
        Bedtime bt = Bedtime.load(c);
        if (bt.configured && bt.enabled) {
            b.append("\n\n").append(c.getString(R.string.ai_tip_bed, Bedtime.label(c, bt.bedHour(), bt.bedMinute())));
        }
        return b.toString();
    }
}
