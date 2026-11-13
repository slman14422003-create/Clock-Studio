package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Circular progress ring of a running timer: grey track, rounded accent arc shrinking clockwise from the top. */
final class RingView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float fraction = 1f;
    private int color;

    RingView(Context c) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        color = Ui.color(c, R.color.accent);
    }

    void set(float f, int c) {
        if (f != fraction || c != color) {
            fraction = f;
            color = c;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        Context c = getContext();
        float w = Ui.dpf(c, 5);
        float pad = w / 2 + 1;
        box.set(pad, pad, getWidth() - pad, getHeight() - pad);
        p.setStrokeWidth(w);
        p.setColor(Ui.color(c, R.color.neutral_soft));
        cv.drawOval(box, p);
        if (fraction > 0f) {
            p.setColor(color);
            cv.drawArc(box, -90, 360f * fraction, false, p);
        }
    }
}
