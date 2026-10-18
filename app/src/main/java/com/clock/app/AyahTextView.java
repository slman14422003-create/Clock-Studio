package com.clock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.widget.TextView;

/** Text view that paints slim rounded highlights behind chosen character ranges, hugging the glyphs instead of the whole line box. */
final class AyahTextView extends TextView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] ranges = new int[0];
    private int[] colors = new int[0];

    AyahTextView(Context c) {
        super(c);
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
                paint.setColor(colors[k]);
                int first = l.getLineForOffset(s), last = l.getLineForOffset(e - 1);
                for (int line = first; line <= last; line++) {
                    int ls = Math.max(s, l.getLineStart(line));
                    int le = Math.min(e, l.getLineVisibleEnd(line));
                    if (le <= ls) continue;
                    float x1 = l.getPrimaryHorizontal(ls);
                    float x2 = l.getPrimaryHorizontal(le);
                    float base = l.getLineBaseline(line);
                    float top = Math.max(base + fm.ascent * 0.82f, l.getLineTop(line));
                    float bottom = Math.min(base + fm.descent * 1.1f, l.getLineBottom(line));
                    rect.set(Math.min(x1, x2) - 4 * d, top - 1 * d, Math.max(x1, x2) + 4 * d, bottom + 1 * d);
                    cv.drawRoundRect(rect, 9 * d, 9 * d, paint);
                }
            }
            cv.restore();
        }
        super.onDraw(cv);
    }
}
