package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** iPhone style analog stopwatch: a 60 second sweep hand and a small 30 minute dial. */
final class StopwatchDial extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long elapsed;

    StopwatchDial(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void setElapsed(long ms) {
        elapsed = ms;
        if (getVisibility() == VISIBLE) invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        Context c = getContext();
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - Ui.dpf(c, 3);
        int primary = Ui.color(c, R.color.text_primary);
        int hint = Ui.color(c, R.color.text_hint);
        int orange = Ui.color(c, R.color.warn);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.color(c, R.color.field));
        cv.drawCircle(cx, cy, r, p);

        for (int i = 0; i < 60; i++) {
            boolean major = i % 5 == 0;
            double a = Math.toRadians(i * 6);
            float len = major ? Ui.dpf(c, 7) : Ui.dpf(c, 3.5f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(major ? Ui.dpf(c, 2) : Ui.dpf(c, 1));
            p.setColor(major ? primary : hint);
            float r1 = r - Ui.dpf(c, 5), r0 = r1 - len;
            cv.drawLine(cx + (float) Math.sin(a) * r0, cy - (float) Math.cos(a) * r0,
                    cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Ui.medium());
        p.setTextSize(r * 0.13f);
        p.setColor(primary);
        for (int n = 5; n <= 60; n += 5) {
            double a = Math.toRadians(n * 6);
            float rr = r * 0.74f;
            float x = cx + (float) Math.sin(a) * rr;
            float y = cy - (float) Math.cos(a) * rr - (p.ascent() + p.descent()) / 2f;
            cv.drawText(String.valueOf(n), x, y, p);
        }

        // minutes sub dial (30 min per turn)
        float sx = cx, sy = cy - r * 0.34f, sr = r * 0.24f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.hairPx(c));
        p.setColor(hint);
        cv.drawCircle(sx, sy, sr, p);
        for (int i = 0; i < 30; i++) {
            double a = Math.toRadians(i * 12);
            float len = i % 5 == 0 ? Ui.dpf(c, 3.5f) : Ui.dpf(c, 2f);
            p.setStrokeWidth(Ui.dpf(c, 1));
            cv.drawLine(sx + (float) Math.sin(a) * (sr - len), sy - (float) Math.cos(a) * (sr - len),
                    sx + (float) Math.sin(a) * sr, sy - (float) Math.cos(a) * sr, p);
        }
        float minDeg = (elapsed % 1800000L) / 1800000f * 360f;
        hand(cv, sx, sy, minDeg, sr * 0.86f, Ui.dpf(c, 1.6f), orange, 0);

        // seconds sweep
        float secDeg = (elapsed % 60000L) / 60000f * 360f;
        hand(cv, cx, cy, secDeg, r * 0.86f, Ui.dpf(c, 1.8f), orange, r * 0.14f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(orange);
        cv.drawCircle(cx, cy, Ui.dpf(c, 4), p);
        p.setColor(Ui.color(c, R.color.bg));
        cv.drawCircle(cx, cy, Ui.dpf(c, 1.5f), p);
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
