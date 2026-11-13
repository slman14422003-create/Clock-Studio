package com.clock.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** iOS style picker wheel: snaps to rows, fades away from the centre, optional looping and unit label. */
final class WheelView extends View {
    interface Listener {
        void onChanged(int index);
    }

    private String[] items = new String[0];
    private boolean loop;
    private float pos;
    private int lastIndex = -1;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float rowH;
    private final float textBig;
    private final float textSmall;
    private String unit = "";
    private Listener listener;
    private VelocityTracker vt;
    private float lastY;
    private ValueAnimator anim;

    WheelView(Context c) {
        super(c);
        rowH = Ui.dpf(c, 40);
        textBig = Ui.dpf(c, 24);
        textSmall = Ui.dpf(c, 19);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFontFeatureSettings("tnum");
        setMinimumHeight((int) (rowH * 5));
    }

    void setItems(String[] it) {
        items = it;
        invalidate();
    }

    void setLoop(boolean l) {
        loop = l;
    }

    void setUnit(String u) {
        unit = u == null ? "" : u;
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    int getIndex() {
        return items.length == 0 ? 0 : norm(Math.round(pos));
    }

    void setIndex(int i) {
        if (anim != null) anim.cancel();
        pos = Math.max(0, Math.min(items.length - 1, i));
        lastIndex = getIndex();
        invalidate();
    }

    private int norm(int i) {
        int n = items.length;
        if (n == 0) return 0;
        return ((i % n) + n) % n;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int hh = (int) (rowH * 5);
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 90), w), resolveSize(hh, h));
    }

    @Override
    protected void onDraw(Canvas cv) {
        int n = items.length;
        if (n == 0) return;
        float cy = getHeight() / 2f;
        float cx = getWidth() / 2f;
        int base = Math.round(pos);
        int primary = Ui.color(getContext(), R.color.text_primary);
        for (int k = base - 3; k <= base + 3; k++) {
            if (!loop && (k < 0 || k >= n)) continue;
            float d = k - pos;
            float ad = Math.min(Math.abs(d), 2.5f);
            float y = cy + d * rowH;
            float t = 1f - ad / 2.5f;
            paint.setTextSize(textSmall + (textBig - textSmall) * Math.max(0f, 1f - Math.abs(d)));
            paint.setColor(Ui.alpha(primary, (int) (255 * (0.22f + 0.78f * t * t))));
            float ty = y - (paint.ascent() + paint.descent()) / 2f;
            String s = items[norm(k)];
            cv.drawText(s, cx, ty, paint);
            if (!unit.isEmpty() && Math.abs(d) < 0.5f) {
                float w = paint.measureText(s);
                paint.setTextSize(textSmall * 0.72f);
                paint.setTextAlign(Paint.Align.LEFT);
                cv.drawText(unit, cx + w / 2f + Ui.dpf(getContext(), 4), ty, paint);
                paint.setTextAlign(Paint.Align.CENTER);
            }
        }
    }

    private void settle() {
        int idx = getIndex();
        if (idx != lastIndex) {
            lastIndex = idx;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onChanged(idx);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (anim != null) anim.cancel();
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                vt.addMovement(e);
                pos -= (e.getY() - lastY) / rowH;
                lastY = e.getY();
                if (!loop) pos = Math.max(-0.4f, Math.min(items.length - 1 + 0.4f, pos));
                settle();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                vt.addMovement(e);
                vt.computeCurrentVelocity(1000);
                float v = vt.getYVelocity();
                vt.recycle();
                vt = null;
                float target = Math.round(pos - v / rowH * 0.16f);
                if (!loop) target = Math.max(0, Math.min(items.length - 1, target));
                animateTo(target);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void animateTo(float target) {
        if (anim != null) anim.cancel();
        final float from = pos;
        anim = ValueAnimator.ofFloat(from, target);
        anim.setDuration((long) Math.min(700, 160 + Math.abs(target - from) * 45));
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            settle();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
