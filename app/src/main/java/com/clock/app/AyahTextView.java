package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Layout;
import android.widget.TextView;

/** Text view that paints translucent glass bubbles behind chosen character ranges, hugging the glyphs instead of the whole line box. */
final class AyahTextView extends TextView {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF inner = new RectF();
    private final Path clip = new Path();
    private int[] ranges = new int[0];
    private int[] colors = new int[0];

    AyahTextView(Context c) {
        super(c);
        glow.setStyle(Paint.Style.STROKE);
        edge.setStyle(Paint.Style.STROKE);
        sheen.setStyle(Paint.Style.FILL);
    }

    void setHighlights(int[] r, int[] col) {
        ranges = r;
        colors = col;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        Layout l = getLayout();
        if (l != null && colors.length > 0) {
            float d = getResources().getDisplayMetrics().density;
            Paint.FontMetrics fm = getPaint().getFontMetrics();
            cv.save();
            cv.translate(getTotalPaddingLeft(), getTotalPaddingTop());
            int len = getText().length();
            for (int k = 0; k < colors.length; k++) {
                int s = Math.max(0, ranges[k * 2]);
                int e = Math.min(len, ranges[k * 2 + 1]);
                if (e <= s) continue;
                int tint = colors[k] & 0x00FFFFFF;
                int first = l.getLineForOffset(s), last = l.getLineForOffset(e - 1);
                for (int line = first; line <= last; line++) {
                    int ls = Math.max(s, l.getLineStart(line));
                    int le = Math.min(e, l.getLineVisibleEnd(line));
                    if (le <= ls) continue;
                    float x1 = l.getPrimaryHorizontal(ls);
                    float x2 = l.getPrimaryHorizontal(le);
                    float base = l.getLineBaseline(line);
                    float top = Math.max(base + fm.ascent * 0.86f, l.getLineTop(line));
                    float bottom = Math.min(base + fm.descent * 1.15f, l.getLineBottom(line));
                    rect.set(Math.min(x1, x2) - 6 * d, top, Math.max(x1, x2) + 6 * d, bottom);
                    drawBubble(cv, rect, tint, d);
                }
            }
            cv.restore();
        }
        super.onDraw(cv);
    }

    private void drawBubble(Canvas cv, RectF r, int tint, float d) {
        float radius = Math.min(r.height() / 2f, 18 * d);

        for (int i = 3; i >= 1; i--) {
            glow.setStrokeWidth(i * 3 * d);
            glow.setColor((tint) | ((0x0E / i) << 24));
            cv.drawRoundRect(r, radius, radius, glow);
        }

        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                new int[]{tint | (0x4A << 24), tint | (0x1C << 24), tint | (0x30 << 24)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, radius, radius, fill);
        fill.setShader(null);

        edge.setStrokeWidth(1.1f * d);
        edge.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                new int[]{Color.argb(0x8C, 255, 255, 255), tint | (0x40 << 24), Color.argb(0x40, 255, 255, 255)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        inner.set(r.left + 0.5f * d, r.top + 0.5f * d, r.right - 0.5f * d, r.bottom - 0.5f * d);
        cv.drawRoundRect(inner, radius, radius, edge);
        edge.setShader(null);

        cv.save();
        clip.reset();
        clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        cv.clipPath(clip);
        float sh = Math.min(r.height() * 0.42f, 14 * d);
        sheen.setShader(new LinearGradient(0, r.top, 0, r.top + sh,
                Color.argb(0x4D, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        cv.drawRect(r.left, r.top, r.right, r.top + sh, sheen);
        sheen.setShader(null);
        cv.restore();
    }
}
