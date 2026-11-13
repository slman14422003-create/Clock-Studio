package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Frosted surface (iOS material): a flat tint over the live blur with a hairline edge, no sheen and no specular rim. */
final class GlassDrawable extends Drawable {
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final float radius;
    private final float edgeW;
    private final float edgeK;
    private int fill;

    GlassDrawable(Context c, float radiusPx, int fillColor, float rimStrength) {
        this.radius = radiusPx;
        this.fill = fillColor;
        this.edgeW = Math.max(1f, Ui.dpf(c, 0.5f));
        this.edgeK = rimStrength;
        boolean night = Appearance.isNight(c);
        int base = night ? 0x2EFFFFFF : 0x14000000;
        edgeP.setStyle(Paint.Style.STROKE);
        edgeP.setStrokeWidth(edgeW);
        edgeP.setColor(Glass.dim(base, Math.min(1f, rimStrength)));
        fillP.setStyle(Paint.Style.FILL);
    }

    void setFill(int color) {
        fill = color;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas cv) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float half = edgeW / 2f;
        rf.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        float rr = Math.min(radius, Math.min(rf.width(), rf.height()) / 2f);
        fillP.setColor(fill);
        cv.drawRoundRect(rf, rr, rr, fillP);
        if (edgeK > 0.4f) cv.drawRoundRect(rf, rr, rr, edgeP);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        Rect b = getBounds();
        float rr = Math.min(radius, Math.min(b.width(), b.height()) / 2f);
        outline.setRoundRect(b, rr);
        outline.setAlpha(0.4f);
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
