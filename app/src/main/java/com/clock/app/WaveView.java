package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Audio waveform for the current verse with a bubble riding the playhead; drag it to seek inside the verse. Runs right to left. */
final class WaveView extends View {
    interface Seek {
        void to(float fraction);
    }

    private static final int BARS = 42;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path tip = new Path();
    private final float[] h = new float[BARS];
    private final float d;
    private float progress, phase;
    private boolean live, dragging;
    private String label = "";
    private int accent = 0xFF0A84FF, dim = 0x55888888;
    Seek seek;

    WaveView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeCap(Paint.Cap.ROUND);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        txt.setTextSize(11 * d);
        txt.setColor(0xFFFFFFFF);
        setAyah(0, "");
    }

    void setColors(int accentColor, int dimColor) {
        accent = accentColor;
        dim = dimColor;
        invalidate();
    }

    void setAyah(int seed, String text) {
        Random r = new Random(seed * 7919L + 13);
        float prev = 0.5f;
        for (int i = 0; i < BARS; i++) {
            float v = 0.2f + 0.8f * r.nextFloat();
            prev = prev * 0.35f + v * 0.65f;
            h[i] = Math.max(0.18f, Math.min(1f, prev * 1.15f));
        }
        label = text;
        progress = 0f;
        invalidate();
    }

    void setProgress(float p) {
        if (dragging) return;
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    void setLive(boolean on) {
        live = on;
        if (on) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), hh = getHeight();
        float pad = 10 * d;
        float bubbleH = 22 * d;
        float top = bubbleH + 6 * d;
        float waveH = Math.max(10 * d, hh - top - 2 * d);
        float cy = top + waveH / 2f;
        float span = w - pad * 2;
        float step = span / BARS;
        bar.setStrokeWidth(step * 0.52f);
        if (live) phase += 0.14f;
        for (int i = 0; i < BARS; i++) {
            float frac = (i + 0.5f) / BARS;
            boolean played = frac <= progress;
            float amp = h[i];
            if (live && played && Math.abs(frac - progress) < 0.22f) amp = Math.min(1f, amp * (1f + 0.22f * (float) Math.sin(phase + i * 0.7f)));
            float half = Math.max(2 * d, amp * waveH * 0.5f);
            float x = w - pad - step * (i + 0.5f);
            bar.setColor(played ? accent : dim);
            cv.drawLine(x, cy - half, x, cy + half, bar);
        }
        float hx = w - pad - span * progress;
        fill.setColor(0xFFFFFFFF);
        fill.setShadowLayer(3 * d, 0, 1 * d, 0x55000000);
        cv.drawCircle(hx, cy, 7 * d, fill);
        fill.clearShadowLayer();
        fill.setColor(accent);
        cv.drawCircle(hx, cy, 4.5f * d, fill);
        float bw = Math.max(34 * d, txt.measureText(label) + 18 * d);
        float bx = Math.max(bw / 2f, Math.min(w - bw / 2f, hx));
        rect.set(bx - bw / 2f, 0, bx + bw / 2f, bubbleH - 3 * d);
        cv.drawRoundRect(rect, 10 * d, 10 * d, fill);
        tip.reset();
        tip.moveTo(hx - 5 * d, bubbleH - 3.5f * d);
        tip.lineTo(hx + 5 * d, bubbleH - 3.5f * d);
        tip.lineTo(hx, bubbleH + 3 * d);
        tip.close();
        cv.drawPath(tip, fill);
        cv.drawText(label, bx, (bubbleH - 3 * d) / 2f + 4 * d, txt);
        if (live) postInvalidateOnAnimation();
    }

    private float fractionAt(float x) {
        float pad = 10 * d;
        float span = getWidth() - pad * 2;
        return Math.max(0f, Math.min(1f, (getWidth() - pad - x) / span));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                progress = fractionAt(e.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                progress = fractionAt(e.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                progress = fractionAt(e.getX());
                dragging = false;
                if (seek != null) seek.to(progress);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }
}
