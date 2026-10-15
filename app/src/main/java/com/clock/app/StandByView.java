package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

    static final int DIGITAL = 0, ANALOG = 1, NIGHT = 2, COUNT = 3;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
    private final GestureDetector gd;
    private int style;
    private StyleCb styleCb;
    private Runnable closeCb;
    private long alarmAt;
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
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
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

    void setOnStyleChanged(StyleCb cb) {
        styleCb = cb;
    }

    void setOnClose(Runnable r) {
        closeCb = r;
    }

    private void change(int dir) {
        final int next = ((style + dir) % COUNT + COUNT) % COUNT;
        animate().cancel();
        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(150).setInterpolator(Anim.IN).withEndAction(() -> {
            style = next;
            if (styleCb != null) styleCb.on(next);
            invalidate();
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(460).setInterpolator(Anim.OUT).start();
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
        float w = getWidth(), hh = getHeight();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        float shift = Ui.dpf(c, 2) * ((cal.get(Calendar.MINUTE) % 5) - 2);
        cv.save();
        cv.translate(shift, -shift);
        if (style == ANALOG) drawAnalog(cv, c, now, cal, w, hh);
        else drawDigital(cv, c, now, cal, w, hh, style == NIGHT);
        cv.restore();
    }

    private void drawDigital(Canvas cv, Context c, long now, Calendar cal, float w, float h, boolean night) {
        int main = night ? 0xFFD7352C : Color.WHITE;
        int soft = night ? 0xFF8F2620 : 0xFF8E8E93;
        int accent = night ? 0xFF8F2620 : 0xFFFF9F0A;
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
        hand(cv, cx, cy, sec * 6, r * 0.86f, Ui.dpf(c, 1.8f), 0xFFFF9F0A, r * 0.16f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF9F0A);
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
