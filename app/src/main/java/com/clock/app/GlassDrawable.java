package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The skin of a Liquid Glass surface: a translucent tint, a soft sheen across the top and a thin bright rim that is
 * strong on two opposite corners (the specular edge of iOS 26). Tilting the phone slides the light along the rim.
 */
final class GlassDrawable extends Drawable {
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Matrix m = new Matrix();
    private final float radius;
    private final float rimW;
    private final boolean night;
    private final float rimStrength;
    private int fill;
    private LinearGradient rimShader, sheenShader;
    private int lastW = -1, lastH = -1;
    private float lastTilt = Float.NaN;

    GlassDrawable(Context c, float radiusPx, int fillColor, float rimStrength) {
        this.radius = radiusPx;
        this.fill = fillColor;
        this.night = Appearance.isNight(c);
        this.rimStrength = rimStrength;
        this.rimW = Math.max(1f, Ui.dpf(c, 1f));
        fillP.setStyle(Paint.Style.FILL);
        rimP.setStyle(Paint.Style.STROKE);
        rimP.setStrokeWidth(rimW);
        sheenP.setStyle(Paint.Style.FILL);
    }

    void setFill(int color) {
        fill = color;
        invalidateSelf();
    }

    private void shaders(Rect b) {
        int w = b.width(), h = b.height();
        if (w == lastW && h == lastH && rimShader != null) return;
        lastW = w;
        lastH = h;
        int a = Math.round(255f * (night ? 0.55f : 0.95f) * rimStrength);
        int hot = (Math.min(255, a) << 24) | 0xFFFFFF;
        int mid = (Math.round(255f * 0.10f * rimStrength) << 24) | 0xFFFFFF;
        int back = (Math.round(255f * (night ? 0.30f : 0.60f) * rimStrength) << 24) | 0xFFFFFF;
        rimShader = new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{hot, mid, mid, back}, new float[]{0f, 0.38f, 0.62f, 1f}, Shader.TileMode.CLAMP);
        rimP.setShader(rimShader);
        int s0 = (Math.round(255f * (night ? 0.16f : 0.55f) * rimStrength) << 24) | 0xFFFFFF;
        sheenShader = new LinearGradient(0, b.top, 0, b.top + Math.max(1f, h * 0.6f),
                s0, 0x00FFFFFF, Shader.TileMode.CLAMP);
        sheenP.setShader(sheenShader);
        lastTilt = Float.NaN;
    }

    @Override
    public void draw(@NonNull Canvas cv) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        shaders(b);
        float half = rimW / 2f;
        rf.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        float rr = Math.min(radius, Math.min(rf.width(), rf.height()) / 2f);
        fillP.setColor(fill);
        cv.drawRoundRect(rf, rr, rr, fillP);
        cv.drawRoundRect(rf, rr, rr, sheenP);
        float tilt = Glass.tiltX;
        if (tilt != lastTilt) {
            m.setRotate(tilt * 35f, b.exactCenterX(), b.exactCenterY());
            rimShader.setLocalMatrix(m);
            rimP.setShader(rimShader);
            lastTilt = tilt;
        }
        cv.drawRoundRect(rf, rr, rr, rimP);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        Rect b = getBounds();
        float rr = Math.min(radius, Math.min(b.width(), b.height()) / 2f);
        outline.setRoundRect(b, rr);
        outline.setAlpha(0.55f);
    }

    @Override
    public void setAlpha(int alpha) {
        fillP.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
