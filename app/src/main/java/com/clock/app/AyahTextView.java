package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.widget.TextView;

/** Text view that paints a soft translucent bubble with a hairline edge behind chosen character ranges. */
final class AyahTextView extends TextView {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] ranges = new int[0];
    private int[] colors = new int[0];

    AyahTextView(Context c) {
        super(c);
        fill.setStyle(Paint.Style.FILL);
        edge.setStyle(Paint.Style.STROKE);
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
            float minX = -getTotalPaddingLeft() + 1.5f * d;
            float maxX = l.getWidth() + getTotalPaddingRight() - 1.5f * d;
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
                    float x1 = xAt(l, line, ls);
                    float x2 = xAt(l, line, le);
                    float base = l.getLineBaseline(line);
                    float top = Math.max(base + fm.ascent * 0.9f, l.getLineTop(line));
                    float bottom = Math.min(base + fm.descent * 1.2f, l.getLineBottom(line));
                    float left = Math.max(minX, Math.min(x1, x2) - 5 * d);
                    float right = Math.min(maxX, Math.max(x1, x2) + 5 * d);
                    rect.set(left, top, right, bottom);
                    drawBubble(cv, rect, tint, d);
                }
            }
            cv.restore();
        }
        super.onDraw(cv);
    }

    /** Glyph position on a line, corrected for inter-word justification (Layout reports the unjustified position). */
    private float xAt(Layout l, int line, int offset) {
        float x = l.getPrimaryHorizontal(offset);
        if (android.os.Build.VERSION.SDK_INT < 26 || getJustificationMode() == Layout.JUSTIFICATION_MODE_NONE) return x;
        if (line >= l.getLineCount() - 1) return x;
        int start = l.getLineStart(line), end = l.getLineVisibleEnd(line);
        CharSequence t = getText();
        int spaces = 0, before = 0;
        for (int i = start; i < end; i++) {
            if (t.charAt(i) == ' ') {
                spaces++;
                if (i < offset) before++;
            }
        }
        if (spaces == 0) return x;
        float extra = (l.getWidth() - l.getLineMax(line)) / spaces;
        if (extra <= 0) return x;
        return l.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT ? x - extra * before : x + extra * before;
    }

    private void drawBubble(Canvas cv, RectF r, int tint, float d) {
        float radius = Math.min(r.height() / 2f, 14 * d);
        fill.setColor(tint | (0x26 << 24));
        cv.drawRoundRect(r, radius, radius, fill);
        edge.setStrokeWidth(1f * d);
        edge.setColor(tint | (0x59 << 24));
        r.inset(0.5f * d, 0.5f * d);
        cv.drawRoundRect(r, radius, radius, edge);
    }
}
