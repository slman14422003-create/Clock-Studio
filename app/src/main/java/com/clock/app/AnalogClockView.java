package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Calendar;
import java.util.TimeZone;

/** Analog face in the iOS Clock style: numerals, minute ticks, white hands and an orange second hand. */
final class AnalogClockView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TimeZone zone = TimeZone.getDefault();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            h.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };

    AnalogClockView(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void setZone(TimeZone z) {
        zone = z;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
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
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - Ui.dpf(c, 4);
        int primary = Ui.color(c, R.color.text_primary);
        int hint = Ui.color(c, R.color.text_hint);
        int accent = Ui.color(c, R.color.accent);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.color(c, R.color.surface));
        cv.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.hairPx(c));
        p.setColor(Ui.color(c, R.color.stroke));
        cv.drawCircle(cx, cy, r, p);

        for (int i = 0; i < 60; i++) {
            boolean major = i % 5 == 0;
            double a = Math.toRadians(i * 6);
            float len = major ? Ui.dpf(c, 7) : Ui.dpf(c, 3.5f);
            p.setStrokeWidth(major ? Ui.dpf(c, 2) : Ui.dpf(c, 1));
            p.setColor(major ? primary : hint);
            float r1 = r - Ui.dpf(c, 6), r0 = r1 - len;
            cv.drawLine(cx + (float) Math.sin(a) * r0, cy - (float) Math.cos(a) * r0,
                    cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Ui.medium());
        p.setTextSize(r * 0.17f);
        p.setColor(primary);
        for (int n = 1; n <= 12; n++) {
            double a = Math.toRadians(n * 30);
            float rr = r * 0.66f;
            float x = cx + (float) Math.sin(a) * rr;
            float y = cy - (float) Math.cos(a) * rr - (p.ascent() + p.descent()) / 2f;
            cv.drawText(String.valueOf(n), x, y, p);
        }

        Calendar t = Calendar.getInstance(zone);
        t.setTimeInMillis(System.currentTimeMillis());
        float sec = t.get(Calendar.SECOND);
        float min = t.get(Calendar.MINUTE) + sec / 60f;
        float hour = (t.get(Calendar.HOUR_OF_DAY) % 12) + min / 60f;
        p.setStyle(Paint.Style.STROKE);
        hand(cv, cx, cy, hour * 30, r * 0.46f, Ui.dpf(c, 4.5f), primary, 0);
        hand(cv, cx, cy, min * 6, r * 0.72f, Ui.dpf(c, 3f), primary, 0);
        int orange = Ui.color(c, R.color.warn);
        hand(cv, cx, cy, sec * 6, r * 0.8f, Ui.dpf(c, 1.5f), orange, r * 0.16f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(orange);
        cv.drawCircle(cx, cy, Ui.dpf(c, 4), p);
        p.setColor(accent);
        cv.drawCircle(cx, cy, Ui.dpf(c, 1.6f), p);
        p.setTypeface(null);
    }

    private void hand(Canvas cv, float cx, float cy, float deg, float len, float w, int color, float tail) {
        double a = Math.toRadians(deg);
        float sx = (float) Math.sin(a), sy = -(float) Math.cos(a);
        p.setStrokeWidth(w);
        p.setColor(color);
        cv.drawLine(cx - sx * tail, cy - sy * tail, cx + sx * len, cy + sy * len, p);
    }
}
