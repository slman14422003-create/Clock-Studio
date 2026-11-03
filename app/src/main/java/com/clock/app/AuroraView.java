package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.animation.ValueAnimator;

/**
 * The living backdrop behind every screen: four huge soft colour blobs drifting slowly. Glass surfaces blur and refract
 * it, which is what makes them look alive. It stops drawing while the window is hidden and stays still when the system
 * has animations turned off.
 */
final class AuroraView extends View {
    private static final int[][] DAY = {{0x4D, 0x9B, 0xFF}, {0xB4, 0x8B, 0xFF}, {0x55, 0xE0, 0xC8}, {0xFF, 0xA6, 0x8A}};
    private static final int[][] NIGHT = {{0x0A, 0x5F, 0xFF}, {0x7A, 0x3C, 0xF0}, {0x00, 0x9E, 0x9A}, {0xC0, 0x2F, 0x8F}};
    private static final float[] PEAK_DAY = {0.46f, 0.40f, 0.34f, 0.34f};
    private static final float[] PEAK_NIGHT = {0.50f, 0.45f, 0.32f, 0.30f};
    // centre x, centre y, radius (fraction of the long side), phase x, phase y, speed
    private static final float[][] SPEC = {
            {0.12f, 0.20f, 0.58f, 0.0f, 1.3f, 0.11f},
            {0.92f, 0.38f, 0.52f, 2.1f, 0.4f, 0.09f},
            {0.18f, 0.82f, 0.58f, 4.0f, 2.2f, 0.10f},
            {0.86f, 0.90f, 0.50f, 5.3f, 3.1f, 0.12f}};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient[] shaders = new RadialGradient[4];
    private final float[] radii = new float[4];
    private final int base;
    private final boolean night;
    private final boolean motion;
    private final long t0 = SystemClock.uptimeMillis();
    private boolean running;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, 40);
        }
    };

    AuroraView(Context c) {
        super(c);
        night = Appearance.isNight(c);
        base = Ui.color(c, R.color.bg_group);
        boolean anim = true;
        if (Build.VERSION.SDK_INT >= 26) anim = ValueAnimator.areAnimatorsEnabled();
        motion = anim;
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        int[][] cols = night ? NIGHT : DAY;
        float[] peak = night ? PEAK_NIGHT : PEAK_DAY;
        float big = Math.max(w, h);
        for (int i = 0; i < 4; i++) {
            int[] c = cols[i];
            int a0 = Math.round(255f * peak[i]);
            int a1 = Math.round(255f * peak[i] * 0.42f);
            int c0 = (a0 << 24) | (c[0] << 16) | (c[1] << 8) | c[2];
            int c1 = (a1 << 24) | (c[0] << 16) | (c[1] << 8) | c[2];
            int c2 = (c[0] << 16) | (c[1] << 8) | c[2];
            radii[i] = big * SPEC[i][2];
            shaders[i] = new RadialGradient(0f, 0f, radii[i], new int[]{c0, c1, c2},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        cv.drawColor(base);
        int w = getWidth(), h = getHeight();
        if (w == 0 || shaders[0] == null) return;
        float t = motion ? (SystemClock.uptimeMillis() - t0) / 1000f : 0f;
        for (int i = 0; i < 4; i++) {
            float[] s = SPEC[i];
            float x = w * (s[0] + 0.16f * (float) Math.sin(t * s[5] * 2.4f + s[3]));
            float y = h * (s[1] + 0.10f * (float) Math.cos(t * s[5] * 2.0f + s[4]));
            cv.save();
            cv.translate(x, y);
            p.setShader(shaders[i]);
            cv.drawCircle(0f, 0f, radii[i], p);
            cv.restore();
        }
    }

    private void update() {
        boolean should = motion && isAttachedToWindow() && getWindowVisibility() == VISIBLE;
        if (should == running) return;
        running = should;
        removeCallbacks(tick);
        if (running) post(tick);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        update();
    }
}
