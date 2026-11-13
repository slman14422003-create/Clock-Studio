package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Full-screen clock for a phone resting on a desk or charger (iPhone StandBy, Samsung always-on clock).
 * Swipe sideways to change the style, swipe down to leave. Content drifts a few pixels every minute against burn-in.
 */
final class StandByView extends View {
    interface StyleCb {
        void on(int style);
    }

    static final int DIGITAL = 0, ANALOG = 1, NIGHT = 2, PRAYER = 3, VERSE = 4, CALENDAR = 5, COUNT = 6;
    private static final int[] PALETTE = {0xFFFFFFFF, 0xFFFF9F0A, 0xFF0A84FF, 0xFF30D158, 0xFFFF375F, 0xFFFFD60A};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
    private final GestureDetector gd;
    private int style;
    private StyleCb styleCb;
    private Runnable closeCb;
    private long alarmAt;
    private boolean dark;
    private final RectF box = new RectF();
    private long dataCheck;
    private PrayerTimes.Cfg pcfg;
    private PrayerTimes.Day pday;
    private PrayerTimes.Next pnext;
    private QuranData qd;
    private boolean qLoading;
    private Typeface qFace;
    private StaticLayout vLayout;
    private String vKey = "";
    private long born = System.currentTimeMillis();
    private boolean showHint;
    private long alarmCheck;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            h.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };

    StandByView(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
        showHint = !"1".equals(Store.raw(c, "standby_hint_seen"));
        if (showHint) Store.putRaw(c, "standby_hint_seen", "1");
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                int n = (Store.standbyColor(getContext()) + 1) % PALETTE.length;
                Store.setStandbyColor(getContext(), n);
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (Math.abs(vx) > Math.abs(vy)) {
                    change(vx < 0 ? 1 : -1);
                } else if (vy > 0 && closeCb != null) {
                    closeCb.run();
                }
                return true;
            }
        });
    }

    void setStyle(int s) {
        style = Math.max(0, Math.min(COUNT - 1, s));
        invalidate();
    }

    int style() {
        return style;
    }

    void setDark(boolean d) {
        dark = d;
        invalidate();
    }

    private int main(boolean night) {
        if (night) return 0xFFD7352C;
        return PALETTE[Math.min(PALETTE.length - 1, Store.standbyColor(getContext()))];
    }

    private int accent(boolean night) {
        if (night) return 0xFF8F2620;
        int i = Store.standbyColor(getContext());
        return i == 0 ? 0xFFFF9F0A : PALETTE[Math.min(PALETTE.length - 1, i)];
    }

    void setOnStyleChanged(StyleCb cb) {
        styleCb = cb;
    }

    void setOnClose(Runnable r) {
        closeCb = r;
    }

    private void change(int dir) {
        final int next = ((style + dir) % COUNT + COUNT) % COUNT;
        animate().cancel();
        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(Anim.D_SHORT).setInterpolator(Anim.IN).withEndAction(() -> {
            style = next;
            if (styleCb != null) styleCb.on(next);
            invalidate();
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Anim.D_MED).setInterpolator(Anim.OUT).start();
        }).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return gd.onTouchEvent(e) || super.onTouchEvent(e);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        h.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        Context c = getContext();
        cv.drawColor(Color.BLACK);
        long now = System.currentTimeMillis();
        if (now - alarmCheck > 15000) {
            alarmAt = Alarms.nextTrigger(c);
            alarmCheck = now;
        }
        if (now - dataCheck > 15000) {
            dataCheck = now;
            try {
                pcfg = PrayerTimes.cfg(c);
                pday = pcfg.configured ? PrayerTimes.day(c, pcfg, PrayerTimes.today(pcfg)) : null;
                pnext = PrayerTimes.next(c);
            } catch (Throwable e) {
                pday = null;
                pnext = null;
            }
        }
        float w = getWidth(), hh = getHeight();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        float shift = Ui.dpf(c, 2) * ((cal.get(Calendar.MINUTE) % 5) - 2);
        int st = dark ? NIGHT : style;
        boolean night = st == NIGHT;
        cv.save();
        cv.translate(shift, -shift);
        if (night) drawDigital(cv, c, now, cal, w, hh, true);
        else if (st == ANALOG) drawAnalog(cv, c, now, cal, w, hh);
        else if (st == PRAYER) drawPrayer(cv, c, now, w, hh);
        else if (st == VERSE) drawVerse(cv, c, w, hh);
        else if (st == CALENDAR) drawCalendar(cv, c, now, cal, w, hh);
        else drawDigital(cv, c, now, cal, w, hh, false);
        cv.restore();
        drawDots(cv, c, w, hh, night);
    }

    private void drawDots(Canvas cv, Context c, float w, float h, boolean night) {
        float r = Ui.dpf(c, 3);
        float gap = Ui.dpf(c, 14);
        float x0 = w / 2f - (COUNT - 1) * gap / 2f;
        float y = h - Ui.dpf(c, 22);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < COUNT; i++) {
            p.setColor(i == style ? (night ? 0xFF8F2620 : 0xB3FFFFFF) : (night ? 0xFF2A0C0A : 0x40FFFFFF));
            cv.drawCircle(x0 + i * gap, y, i == style ? r * 1.15f : r, p);
        }
        long age = System.currentTimeMillis() - born;
        if (showHint && age < 6000 && !night) {
            p.setTypeface(Ui.medium());
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Ui.dpf(c, 13));
            p.setColor(Color.argb((int) (150 * Math.min(1f, (6000 - age) / 1500f)), 255, 255, 255));
            cv.drawText(c.getString(R.string.standby_hint), w / 2f, y - Ui.dpf(c, 16), p);
            p.setTypeface(null);
        }
    }

    private void glass(Canvas cv, RectF r, float rad, int tint, int fillA) {
        p.setStyle(Paint.Style.FILL);
        p.setColor((tint & 0xFFFFFF) | (fillA << 24));
        cv.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dpf(getContext(), 1));
        p.setColor(0x40FFFFFF);
        cv.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void message(Canvas cv, Context c, float w, float h, int res) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.medium());
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.max(Ui.dpf(c, 16), Math.min(w, h) * 0.05f));
        p.setColor(0xFF8E8E93);
        cv.drawText(c.getString(res), w / 2f, h / 2f, p);
        p.setTypeface(null);
    }

    private void drawPrayer(Canvas cv, Context c, long now, float w, float h) {
        if (pnext == null || pday == null || pcfg == null) {
            message(cv, c, w, h, R.string.standby_no_prayer);
            return;
        }
        String[] names = c.getResources().getStringArray(R.array.prayer_names);
        int acc = accent(false);
        float m = Math.min(w, h);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Ui.medium());
        p.setTextSize(Math.max(Ui.dpf(c, 14), m * 0.045f));
        p.setColor(0xFF8E8E93);
        cv.drawText(c.getString(R.string.standby_next_prayer), w / 2f, h * 0.15f, p);
        p.setTextSize(Math.max(Ui.dpf(c, 26), m * 0.11f));
        p.setColor(acc);
        cv.drawText(names[pnext.idx] + "  " + PrayerTimes.label(c, pnext.at, pcfg.zone), w / 2f, h * 0.29f, p);

        p.setTypeface(Ui.thin());
        p.setFontFeatureSettings("tnum");
        String cd = Fmt.countdown(pnext.at - now);
        float size = Math.min(m * 0.34f, 240f);
        p.setTextSize(100f);
        float tw = p.measureText(cd);
        size = Math.min(size * 1.0f, 100f * (w * 0.8f) / tw);
        p.setTextSize(size);
        p.setColor(main(false));
        cv.drawText(cd, w / 2f, h * 0.30f + size * 1.05f, p);
        p.setFontFeatureSettings(null);

        boolean rtl = Lang.isAr();
        float cw = Math.min(w * 0.9f, Ui.dpf(c, 760)) / 5f;
        float x0 = w / 2f - cw * 2.5f;
        float top = h * 0.72f, bot = h * 0.9f;
        for (int k = 0; k < 5; k++) {
            int idx = PrayerTimes.PRAYERS[k];
            int col = rtl ? 4 - k : k;
            float l = x0 + col * cw + Ui.dpf(c, 3);
            box.set(l, top, l + cw - Ui.dpf(c, 6), bot);
            boolean on = idx == pnext.idx;
            glass(cv, box, Ui.dpf(c, 16), on ? acc : 0xFFFFFFFF, on ? 0x55 : 0x14);
            p.setTypeface(Ui.medium());
            p.setTextSize(Math.max(Ui.dpf(c, 11), Math.min(cw * 0.2f, (bot - top) * 0.26f)));
            p.setColor(on ? Color.WHITE : 0xFF8E8E93);
            cv.drawText(names[idx], box.centerX(), box.top + (bot - top) * 0.36f, p);
            p.setTextSize(Math.max(Ui.dpf(c, 12), Math.min(cw * 0.22f, (bot - top) * 0.28f)));
            p.setColor(Color.WHITE);
            cv.drawText(PrayerTimes.hm(c, pday.hm[idx]), box.centerX(), box.top + (bot - top) * 0.76f, p);
        }
        p.setTypeface(null);
    }

    private void drawVerse(Canvas cv, Context c, float w, float h) {
        if (qd == null) {
            if (!qLoading) {
                qLoading = true;
                QuranData.load(c.getApplicationContext(), d -> {
                    qd = d;
                    if (d != null) invalidate();
                });
            }
            message(cv, c, w, h, R.string.standby_no_verse);
            return;
        }
        if (qFace == null) {
            try {
                qFace = Typeface.createFromAsset(c.getAssets(), "fonts/quran.ttf");
            } catch (Throwable e) {
                qFace = Typeface.SERIF;
            }
        }
        int a = qd.dailyVerse();
        String key = a + ":" + (int) w + "x" + (int) h;
        String text = "\uFD3F" + qd.text[a] + "\uFD3E";
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(qFace);
        tp.setColor(main(false));
        int lw = (int) (w * 0.84f);
        if (!key.equals(vKey) || vLayout == null) {
            float max = Math.min(w * 0.075f, h * 0.15f);
            float min = Ui.dpf(c, 18);
            for (float sz = max; sz >= min; sz -= Ui.dpf(c, 1)) {
                tp.setTextSize(sz);
                vLayout = StaticLayout.Builder.obtain(text, 0, text.length(), tp, lw)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.55f).setIncludePad(false)
                        .setTextDirection(android.text.TextDirectionHeuristics.RTL).build();
                if (vLayout.getHeight() <= h * 0.58f) break;
            }
            vKey = key;
        }
        float top = h * 0.46f - vLayout.getHeight() / 2f;
        cv.save();
        cv.translate((w - lw) / 2f, top);
        vLayout.draw(cv);
        cv.restore();
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.medium());
        p.setTextAlign(Paint.Align.CENTER);
        float small = Math.max(Ui.dpf(c, 15), Math.min(w, h) * 0.045f);
        p.setTextSize(small);
        p.setColor(accent(false));
        cv.drawText(qd.surahTitle(qd.surahOf[a]) + " \u00B7 " + QuranData.num(qd.num[a]), w / 2f, top + vLayout.getHeight() + small * 2f, p);
        p.setTypeface(null);
    }

    private void drawCalendar(Canvas cv, Context c, long now, Calendar cal, float w, float h) {
        Calendar first = Calendar.getInstance(Fmt.locale(c));
        first.setTimeInMillis(now);
        int today = first.get(Calendar.DAY_OF_MONTH);
        first.set(Calendar.DAY_OF_MONTH, 1);
        int fdow = first.getFirstDayOfWeek();
        int offset = (first.get(Calendar.DAY_OF_WEEK) - fdow + 7) % 7;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (offset + days + 6) / 7;
        float cell = Math.min(w * 0.86f / 7f, h * 0.74f / (rows + 1.6f));
        boolean rtl = Lang.isAr();
        float cx = w / 2f;
        float top = h * 0.5f - cell * (rows + 1.6f) / 2f;
        int acc = accent(false);

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Ui.medium());
        p.setTextSize(cell * 0.6f);
        p.setColor(acc);
        cv.drawText(Fmt.date(c, now, "MMMM yyyy"), cx, top + cell * 0.6f, p);

        p.setTextSize(cell * 0.38f);
        p.setColor(0xFF8E8E93);
        Calendar t = (Calendar) first.clone();
        for (int i = 0; i < 7; i++) {
            t.set(Calendar.DAY_OF_WEEK, fdow);
            t.add(Calendar.DAY_OF_YEAR, i);
            float x = cx + (rtl ? (3 - i) : (i - 3)) * cell;
            cv.drawText(Fmt.date(c, t.getTimeInMillis(), "EEEEE"), x, top + cell * 1.5f, p);
        }
        p.setTypeface(Ui.light());
        p.setTextSize(cell * 0.46f);
        for (int d = 1; d <= days; d++) {
            int pos = offset + d - 1;
            int r = pos / 7, col = pos % 7;
            float x = cx + (rtl ? (3 - col) : (col - 3)) * cell;
            float y = top + cell * (2.1f + r) + cell * 0.12f;
            if (d == today) {
                p.setColor(acc);
                cv.drawCircle(x, y - cell * 0.16f, cell * 0.44f, p);
                p.setColor(Color.BLACK);
                p.setTypeface(Ui.medium());
            } else {
                p.setColor(Color.WHITE);
                p.setTypeface(Ui.light());
            }
            cv.drawText(String.valueOf(d), x, y, p);
        }
        p.setTypeface(null);
    }

    private void drawDigital(Canvas cv, Context c, long now, Calendar cal, float w, float h, boolean night) {
        int main = main(night);
        int soft = night ? 0xFF8F2620 : 0xFF8E8E93;
        int accent = accent(night);
        String t = Fmt.time(c, now, TimeZone.getDefault(), false);
        String ap = Fmt.ampm(c, cal.get(Calendar.HOUR_OF_DAY));

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.thin());
        p.setFontFeatureSettings("tnum");
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(100f);
        float tw = p.measureText(t);
        float gap = ap.isEmpty() ? 0f : 6f;
        p.setTextSize(20f);
        float aw = ap.isEmpty() ? 0f : p.measureText(ap);
        float total = tw + gap + aw;
        float size = Math.min(100f * (w * 0.84f) / total, h * (night ? 0.4f : 0.54f));
        float s = size / 100f;
        float startX = (w - total * s) / 2f;
        float base = h * 0.47f + size * 0.36f;

        p.setColor(main);
        p.setTextSize(size);
        cv.drawText(t, startX, base, p);
        if (!ap.isEmpty()) {
            p.setTextSize(size * 0.2f);
            p.setColor(soft);
            cv.drawText(ap, startX + (tw + gap) * s, base, p);
        }

        p.setTypeface(Ui.medium());
        p.setFontFeatureSettings(null);
        p.setTextAlign(Paint.Align.CENTER);
        float small = Math.max(Ui.dpf(c, 16), size * 0.1f);
        p.setTextSize(small);
        p.setColor(soft);
        float y = base + small * 2.1f;
        cv.drawText(Fmt.date(c, now, "EEEE d MMMM"), w / 2f, y, p);
        if (pday != null && !pday.hd.isEmpty()) {
            y += small * 1.6f;
            cv.drawText(c.getString(R.string.hijri_fmt, pday.hd, Lang.isAr() ? pday.hma : pday.hme, pday.hy), w / 2f, y, p);
        }
        if (alarmAt > 0) {
            p.setColor(accent);
            cv.drawText(c.getString(R.string.next_alarm, Fmt.untilText(c, alarmAt - now)), w / 2f, y + small * 1.7f, p);
        }
        p.setTypeface(null);
    }

    private void drawAnalog(Canvas cv, Context c, long now, Calendar cal, float w, float h) {
        float cx = w / 2f, cy = h * 0.47f;
        float r = Math.min(w, h * 0.9f) * 0.44f;

        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 60; i++) {
            boolean major = i % 5 == 0;
            double a = Math.toRadians(i * 6);
            float len = major ? r * 0.08f : r * 0.04f;
            p.setStrokeWidth(major ? Ui.dpf(c, 2.4f) : Ui.dpf(c, 1));
            p.setColor(major ? Color.WHITE : 0xFF636366);
            float r1 = r, r0 = r1 - len;
            cv.drawLine(cx + (float) Math.sin(a) * r0, cy - (float) Math.cos(a) * r0,
                    cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Ui.light());
        p.setTextSize(r * 0.2f);
        p.setColor(Color.WHITE);
        for (int n = 1; n <= 12; n++) {
            double a = Math.toRadians(n * 30);
            float rr = r * 0.78f;
            cv.drawText(String.valueOf(n), cx + (float) Math.sin(a) * rr,
                    cy - (float) Math.cos(a) * rr - (p.ascent() + p.descent()) / 2f, p);
        }
        float ms = cal.get(Calendar.MILLISECOND);
        float sec = cal.get(Calendar.SECOND) + ms / 1000f;
        float min = cal.get(Calendar.MINUTE) + sec / 60f;
        float hour = (cal.get(Calendar.HOUR_OF_DAY) % 12) + min / 60f;
        hand(cv, cx, cy, hour * 30, r * 0.5f, Ui.dpf(c, 5.5f), Color.WHITE, 0);
        hand(cv, cx, cy, min * 6, r * 0.78f, Ui.dpf(c, 3.6f), Color.WHITE, 0);
        hand(cv, cx, cy, sec * 6, r * 0.86f, Ui.dpf(c, 1.8f), accent(false), r * 0.16f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(accent(false));
        cv.drawCircle(cx, cy, Ui.dpf(c, 5), p);
        p.setColor(Color.BLACK);
        cv.drawCircle(cx, cy, Ui.dpf(c, 1.8f), p);

        p.setTypeface(Ui.medium());
        p.setTextSize(Math.max(Ui.dpf(c, 15), r * 0.1f));
        p.setColor(0xFF8E8E93);
        float y = cy + r + Math.max(Ui.dpf(c, 34), r * 0.2f);
        if (y < h - Ui.dpf(c, 12)) cv.drawText(Fmt.date(c, now, "EEEE d MMMM"), w / 2f, y, p);
        p.setTypeface(null);
    }

    private void hand(Canvas cv, float cx, float cy, float deg, float len, float w, int color, float tail) {
        double a = Math.toRadians(deg);
        float sx = (float) Math.sin(a), sy = -(float) Math.cos(a);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(color);
        cv.drawLine(cx - sx * tail, cy - sy * tail, cx + sx * len, cy + sy * len, p);
    }
}
