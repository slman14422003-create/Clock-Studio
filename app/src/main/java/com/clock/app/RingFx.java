package com.clock.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

/** Visual pieces of the ring screen: breathing glow, pulsing bell and the slide-to-stop control. */
final class RingFx {
    private RingFx() {
    }

    /** Base for views that redraw every frame while they are on screen. */
    abstract static class Live extends View {
        private boolean running;
        protected long t0 = System.nanoTime();
        private final Runnable frame = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                invalidate();
                postOnAnimation(this);
            }
        };

        Live(Context c) {
            super(c);
        }

        /** Seconds since the view was created. */
        protected float time() {
            return (System.nanoTime() - t0) / 1e9f;
        }

        private void go() {
            if (running || !isAttachedToWindow() || getWindowVisibility() != VISIBLE) return;
            running = true;
            postOnAnimation(frame);
        }

        private void halt() {
            running = false;
            removeCallbacks(frame);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            go();
        }

        @Override
        protected void onDetachedFromWindow() {
            halt();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onWindowVisibilityChanged(int v) {
            super.onWindowVisibilityChanged(v);
            if (v == VISIBLE) go();
            else halt();
        }
    }

    /** Black screen with a tinted glow that breathes behind the bell and a faint colour wash at the bottom. */
    static final class Backdrop extends Live {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int tint;
        private Shader glow, wash;
        private float fx, fy, fr;

        Backdrop(Context c, int tint) {
            super(c);
            this.tint = tint;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            fx = w / 2f;
            fy = h * 0.5f;
            fr = Math.max(w, h) * 0.55f;
            glow = new RadialGradient(fx, fy, fr, new int[]{ColorUtils.setAlphaComponent(tint, 120), ColorUtils.setAlphaComponent(tint, 38), 0},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            wash = new LinearGradient(0, h * 0.55f, 0, h, 0, ColorUtils.setAlphaComponent(tint, 46), Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas cv) {
            cv.drawColor(Color.BLACK);
            if (glow == null) return;
            float b = 0.5f + 0.5f * (float) Math.sin(time() * 1.9f);
            p.setShader(wash);
            p.setAlpha(255);
            cv.drawRect(0, getHeight() * 0.55f, getWidth(), getHeight(), p);
            p.setShader(glow);
            p.setAlpha((int) (170 + 85 * b));
            cv.save();
            float s = 0.94f + 0.08f * b;
            cv.scale(s, s, fx, fy);
            cv.drawCircle(fx, fy, fr, p);
            cv.restore();
            p.setShader(null);
        }
    }

    /** Disc with a ringing bell and three rings that expand and fade. */
    static final class Bell extends Live {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable icon;
        private final int tint;
        private final boolean shake;

        Bell(Context c, int tint, int iconRes, boolean shake) {
            super(c);
            this.tint = tint;
            this.shake = shake;
            Drawable d = c.getDrawable(iconRes);
            icon = d == null ? null : d.mutate();
            if (icon != null) icon.setTint(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float max = Math.min(cx, cy);
            float r0 = max * 0.36f;
            float t = time();
            p.setShader(null);
            for (int k = 0; k < 3; k++) {
                float u = ((t / 2.6f) + k / 3f) % 1f;
                float e = 1f - (1f - u) * (1f - u);
                float r = r0 + (max - r0) * e;
                int a = (int) (150 * (1f - u));
                p.setStyle(Paint.Style.FILL);
                p.setColor(ColorUtils.setAlphaComponent(tint, (int) (a * 0.22f)));
                cv.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dpf(getContext(), 1.6f));
                p.setColor(ColorUtils.setAlphaComponent(tint, a));
                cv.drawCircle(cx, cy, r, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx - r0 * 0.3f, cy - r0 * 0.35f, r0 * 1.5f,
                    ColorUtils.blendARGB(tint, Color.WHITE, 0.28f), ColorUtils.blendARGB(tint, Color.BLACK, 0.18f), Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r0, p);
            p.setShader(null);
            if (icon != null) {
                float ph = (t % 1.5f) / 1.5f;
                float ang = 0f;
                if (shake && ph < 0.6f) {
                    float q = ph / 0.6f;
                    ang = (float) Math.sin(q * Math.PI * 6) * 15f * (1f - 0.35f * q);
                }
                int s = (int) (r0 * 1.05f);
                cv.save();
                cv.rotate(ang, cx, cy - s * 0.35f);
                icon.setBounds((int) cx - s / 2, (int) cy - s / 2, (int) cx + s / 2, (int) cy + s / 2);
                icon.draw(cv);
                cv.restore();
            }
        }
    }

    /** iOS style "slide to stop": drag the knob across the track; letting go early springs it back. */
    static final class Slide extends Live {
        interface Done {
            void onComplete();
        }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path chevron = new Path();
        private final RectF r = new RectF();
        private final Matrix m = new Matrix();
        private final int tint;
        private final String label;
        private final float pad, knob;
        private float pos;          // 0..1
        private boolean dragging, fired, hapticed;
        private float downX, startPos;
        private Shader knobShader, shimmer;
        private float shimmerW;
        private ValueAnimator back;
        Done done;

        Slide(Context c, int tint, String label) {
            super(c);
            this.tint = tint;
            this.label = label;
            pad = Ui.dpf(c, 6);
            knob = Ui.dpf(c, 64);
            setLayoutDirection(LAYOUT_DIRECTION_LTR);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Ui.dpf(c, 18) );
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            setContentDescription(label);
            ViewCompat.addAccessibilityAction(this, label, (v, a) -> {
                finish();
                return true;
            });
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (knob + pad * 2));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            knobShader = new LinearGradient(0, 0, 0, h, ColorUtils.blendARGB(tint, Color.WHITE, 0.22f),
                    ColorUtils.blendARGB(tint, Color.BLACK, 0.12f), Shader.TileMode.CLAMP);
            shimmerW = w * 0.5f;
            shimmer = new LinearGradient(0, 0, shimmerW, 0, new int[]{0x66FFFFFF, 0xFFFFFFFF, 0x66FFFFFF}, null, Shader.TileMode.CLAMP);
        }

        private float travel() {
            return Math.max(1f, getWidth() - pad * 2 - knob);
        }

        private void finish() {
            if (fired) return;
            fired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (done != null) done.onComplete();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float kx = pad + pos * travel();
                    if (e.getX() < kx - pad || e.getX() > kx + knob + pad) return false;
                    if (back != null) back.cancel();
                    dragging = true;
                    hapticed = false;
                    downX = e.getX();
                    startPos = pos;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        pos = Math.max(0f, Math.min(1f, startPos + (e.getX() - downX) / travel()));
                        if (pos > 0.9f && !hapticed) {
                            hapticed = true;
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging) return true;
                    dragging = false;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && pos >= 0.9f) {
                        pos = 1f;
                        invalidate();
                        finish();
                    } else {
                        springBack();
                    }
                    return true;
                default:
                    return true;
            }
        }

        private void springBack() {
            final float from = pos;
            back = ValueAnimator.ofFloat(0f, 1f);
            back.setDuration(420);
            back.setInterpolator(Anim.SPRING);
            back.addUpdateListener(a -> {
                pos = from * (1f - (float) a.getAnimatedValue());
                invalidate();
            });
            back.start();
        }

        /** Re-arms the control (after the challenge was dismissed, for example). */
        void reset() {
            fired = false;
            pos = 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float rad = h / 2f;
            r.set(0, 0, w, h);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x24FFFFFF);
            cv.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.hairPx(getContext()) * 1.5f);
            p.setColor(0x33FFFFFF);
            cv.drawRoundRect(r, rad, rad, p);

            float kx = pad + pos * travel();
            // progress fill behind the knob
            p.setStyle(Paint.Style.FILL);
            p.setColor(ColorUtils.setAlphaComponent(tint, 90));
            r.set(0, 0, kx + knob + pad, h);
            cv.drawRoundRect(r, rad, rad, p);

            // shimmering label
            float t = time();
            float fade = 1f - Math.min(1f, pos * 2.4f);
            if (fade > 0f) {
                float off = ((t * 0.55f) % 1.6f - 0.3f) * w;
                p.setStyle(Paint.Style.FILL);
                m.setTranslate(off - shimmerW / 2f, 0);
                shimmer.setLocalMatrix(m);
                p.setShader(shimmer);
                p.setAlpha((int) (255 * fade));
                float ty = h / 2f - (p.ascent() + p.descent()) / 2f;
                cv.drawText(label, w / 2f + knob / 2f, ty, p);
                p.setShader(null);
                p.setAlpha(255);
            }

            // knob
            float cx = kx + knob / 2f, cy = h / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(knobShader);
            cv.drawCircle(cx, cy, knob / 2f, p);
            p.setShader(null);

            // chevrons (two, the second follows the first)
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(Ui.dpf(getContext(), 3));
            float s = Ui.dpf(getContext(), 6.5f);
            for (int i = 0; i < 2; i++) {
                float a = 0.45f + 0.55f * (float) Math.max(0, Math.sin(t * 5f - i * 1.1f));
                p.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * (i == 0 ? 1f : a))));
                float ox = cx - s * 0.9f + i * s * 1.35f;
                chevron.reset();
                chevron.moveTo(ox - s * 0.5f, cy - s);
                chevron.lineTo(ox + s * 0.5f, cy);
                chevron.lineTo(ox - s * 0.5f, cy + s);
                cv.drawPath(chevron, p);
            }
            p.setStrokeCap(Paint.Cap.BUTT);
        }
    }
}
