package com.clock.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * Renders an ayah as a 9:16 card (WhatsApp status size) in one of several ornamented templates and colour themes,
 * and hands it to WhatsApp or the system share sheet.
 */
final class ShareCard {
    private ShareCard() {
    }

    static final int W = 1080, H = 1920;
    private static final int BG0 = 0, BG1 = 1, GLOW = 2, ORN = 3, TXT = 4, SUB = 5, LIGHT = 6;

    private static int[] dark(int a, int b, int c, int d) {
        return new int[]{a, b, c, d, 0xFFFFFFFF, d, 0};
    }

    private static int[] light(int a, int b, int c, int d, int text) {
        return new int[]{a, b, c, d, text, d, 1};
    }

    private static final int[][] THEMES = {
            dark(0xFF06231F, 0xFF0E6B57, 0xFF39D3A6, 0xFFF2D58B),
            dark(0xFF070B1C, 0xFF1D3A8A, 0xFF6C9BFF, 0xFFBFD4FF),
            dark(0xFF21160A, 0xFF8A5A14, 0xFFF0B84A, 0xFFFFE3A3),
            dark(0xFF1E0A24, 0xFF8E2C5C, 0xFFFF7BA9, 0xFFFFD0E1),
            dark(0xFF000000, 0xFF2B2B30, 0xFF8E8E93, 0xFFE5E5EA),
            dark(0xFF03171C, 0xFF0B6E7A, 0xFF3FD7E8, 0xFFCFF8FF),
            dark(0xFF140A2A, 0xFF5B2DB0, 0xFFB48CFF, 0xFFE6D9FF),
            dark(0xFF2A0610, 0xFF8C1B2F, 0xFFFF6B7D, 0xFFFFD6B0),
            dark(0xFF0A1A0C, 0xFF2F6B2C, 0xFF8BE07F, 0xFFEAF5C8),
            dark(0xFF021526, 0xFF0D4C92, 0xFF59C1FF, 0xFFFFF1C2),
            dark(0xFF2B0F1B, 0xFFE2552C, 0xFFFFC46B, 0xFFFFF0D2),
            dark(0xFF0A0D2E, 0xFF3730A3, 0xFF818CF8, 0xFFFDE68A),
            light(0xFFFBF3E2, 0xFFEBD9B4, 0xFFC9A24B, 0xFF9A6B1F, 0xFF2E2112),
            light(0xFFF2FBF7, 0xFFCDEFE2, 0xFF3BBF93, 0xFF0E7A5A, 0xFF0B2B22),
            light(0xFFF0F7FF, 0xFFC7DEFA, 0xFF5E9BEF, 0xFF1F5FBF, 0xFF0D2240),
            light(0xFFFFF3F6, 0xFFF9CFDC, 0xFFEC6F98, 0xFFB0305F, 0xFF3B0F20)
    };

    private static final int[] TPL_LABELS = {
            R.string.tpl_glass, R.string.tpl_frame, R.string.tpl_star, R.string.tpl_mosque,
            R.string.tpl_floral, R.string.tpl_arch, R.string.tpl_lantern, R.string.tpl_minimal
    };

    static int themeCount() {
        return THEMES.length;
    }

    static int templateCount() {
        return TPL_LABELS.length;
    }

    static int themeColor(int t) {
        return THEMES[Math.floorMod(t, THEMES.length)][BG1];
    }

    static Typeface face(Activity a) {
        try {
            return Typeface.createFromAsset(a.getAssets(), "fonts/quran.ttf");
        } catch (Throwable e) {
            return Typeface.SERIF;
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    private static int orn(int[] t, int alpha) {
        return (alpha << 24) | (t[ORN] & 0xFFFFFF);
    }

    private static int glow(int[] t, int alpha) {
        return (alpha << 24) | (t[GLOW] & 0xFFFFFF);
    }

    private static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    private static Paint line(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    private static Path star(float cx, float cy, float ro, float ri, int pts, float rot) {
        Path s = new Path();
        for (int i = 0; i < pts * 2; i++) {
            double ang = rot + Math.PI * i / pts - Math.PI / 2.0;
            float r = i % 2 == 0 ? ro : ri;
            float x = cx + (float) (r * Math.cos(ang)), y = cy + (float) (r * Math.sin(ang));
            if (i == 0) s.moveTo(x, y);
            else s.lineTo(x, y);
        }
        s.close();
        return s;
    }

    private static void diamond(Canvas cv, Paint p, float x, float y, float r) {
        Path d = new Path();
        d.moveTo(x, y - r);
        d.lineTo(x + r * 0.72f, y);
        d.lineTo(x, y + r);
        d.lineTo(x - r * 0.72f, y);
        d.close();
        cv.drawPath(d, p);
    }

    private static void divider(Canvas cv, int[] t, float cx, float y, float half) {
        int c = t[ORN] & 0xFFFFFF;
        Paint p = fill(0);
        p.setShader(new LinearGradient(cx - half, 0, cx + half, 0, new int[]{c, (0xB0 << 24) | c, c}, null, Shader.TileMode.CLAMP));
        cv.drawRect(cx - half, y, cx + half, y + 2.5f, p);
        Paint d = fill((0xFF << 24) | c);
        diamond(cv, d, cx, y + 1.2f, 14f);
        diamond(cv, d, cx - half * 0.55f, y + 1.2f, 7f);
        diamond(cv, d, cx + half * 0.55f, y + 1.2f, 7f);
    }

    private static void medallion(Canvas cv, int[] t, float cx, float cy, float r) {
        cv.drawCircle(cx, cy, r, fill(t[BG0]));
        cv.drawCircle(cx, cy, r, line(orn(t, 0xE0), 5f));
        cv.drawCircle(cx, cy, r * 0.82f, line(orn(t, 0x80), 2f));
        cv.drawPath(star(cx, cy, r * 0.62f, r * 0.34f, 8, 0f), line(orn(t, 0xF0), 4f));
        cv.drawCircle(cx, cy, r * 0.1f, fill(orn(t, 0xF0)));
    }

    private static void crescent(Canvas cv, Paint p, float cx, float cy, float r) {
        Path a = new Path();
        a.addCircle(cx, cy, r, Path.Direction.CW);
        Path b = new Path();
        b.addCircle(cx + r * 0.42f, cy - r * 0.1f, r * 0.86f, Path.Direction.CW);
        a.op(b, Path.Op.DIFFERENCE);
        cv.drawPath(a, p);
    }

    private static void scatter(Canvas cv, int[] t, long seed, int n, float yMin, float yMax) {
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * W, y = yMin + r.nextFloat() * (yMax - yMin);
            float rad = 4f + r.nextFloat() * 9f;
            cv.drawPath(star(x, y, rad, rad * 0.4f, 4, 0f), fill(orn(t, 0x30 + r.nextInt(0x60))));
        }
    }

    private static void background(Canvas cv, int[] t) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, W, H, new int[]{t[BG0], t[BG1], t[BG0]}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, p);
        int ga = t[LIGHT] == 1 ? 0x55 : 0x66;
        p.setShader(new RadialGradient(W * 0.85f, H * 0.12f, 620f, glow(t, ga), glow(t, 0), Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W * 0.1f, H * 0.9f, 700f, orn(t, 0x40), orn(t, 0), Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, p);
    }

    private static TextPaint textPaint(Typeface face, int[] t) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(face);
        tp.setColor(t[TXT]);
        if (t[LIGHT] == 0) tp.setShadowLayer(10f, 0f, 4f, 0x55000000);
        return tp;
    }

    private static StaticLayout build(String text, TextPaint tp, int w, int maxLines) {
        StaticLayout.Builder b = StaticLayout.Builder.obtain(text, 0, text.length(), tp, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.7f)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL);
        if (maxLines != Integer.MAX_VALUE) {
            b.setMaxLines(maxLines);
            b.setEllipsize(TextUtils.TruncateAt.END);
        }
        return b.build();
    }

    private static final class Block {
        StaticLayout lay;
        float header, footer, total;
    }

    private static Block layout(String text, Typeface face, int[] t, float textW, float maxH, boolean pill) {
        Block b = new Block();
        b.header = pill ? 150f : 100f;
        b.footer = 170f;
        float maxText = maxH - b.header - b.footer;
        TextPaint tp = textPaint(face, t);
        StaticLayout lay = null;
        for (float size = 78f; size >= 30f; size -= 2f) {
            tp.setTextSize(size);
            lay = build(text, tp, (int) textW, Integer.MAX_VALUE);
            if (lay.getHeight() <= maxText) break;
        }
        if (lay.getHeight() > maxText) {
            lay = build(text, tp, (int) textW, Math.max(3, (int) (maxText / (tp.getTextSize() * 1.7f))));
        }
        b.lay = lay;
        b.total = b.header + lay.getHeight() + b.footer;
        return b;
    }

    private static void drawBlock(Canvas cv, Block b, int[] t, Typeface face, String surah, String ayahNo, RectF area, float top, boolean pill) {
        float cx = area.centerX();
        TextPaint hp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        hp.setTypeface(face);
        hp.setTextSize(58f);
        hp.setColor(t[SUB]);
        hp.setTextAlign(Paint.Align.CENTER);
        if (pill) {
            float pillW = Math.min(area.width() - 20f, hp.measureText(surah) + 150f);
            RectF r = new RectF(cx - pillW / 2f, top + 26f, cx + pillW / 2f, top + 122f);
            cv.drawRoundRect(r, 48f, 48f, fill(t[LIGHT] == 1 ? Color.argb(0x22, 0, 0, 0) : Color.argb(0x30, 255, 255, 255)));
            cv.drawRoundRect(r, 48f, 48f, line(orn(t, 0x90), 2f));
            cv.drawText(surah, cx, r.centerY() - (hp.ascent() + hp.descent()) / 2f, hp);
        } else {
            cv.drawText(surah, cx, top + 60f - (hp.ascent() + hp.descent()) / 2f, hp);
        }
        cv.save();
        cv.translate(area.left, top + b.header);
        b.lay.draw(cv);
        cv.restore();

        float fy = top + b.header + b.lay.getHeight() + 46f;
        divider(cv, t, cx, fy, area.width() * 0.36f);
        TextPaint rp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        rp.setTypeface(Ui.medium());
        rp.setTextSize(44f);
        rp.setColor(t[SUB]);
        rp.setTextAlign(Paint.Align.CENTER);
        cv.drawText(surah + "  \u00B7  " + ayahNo, cx, fy + 100f, rp);
    }

    private static void centered(Canvas cv, Block b, int[] t, Typeface face, String surah, String ayahNo, RectF area, boolean pill) {
        drawBlock(cv, b, t, face, surah, ayahNo, area, area.top + (area.height() - b.total) / 2f, pill);
    }

    // ------------------------------------------------------------------ templates

    private static void tplGlass(Canvas cv, int[] t, Typeface face, String surah, String ayahNo, String text) {
        float margin = 78f, pad = 70f;
        float cardW = W - margin * 2f;
        Block b = layout(text, face, t, cardW - pad * 2f, H - 520f, true);
        float cardH = b.total + 40f;
        float top = (H - cardH) / 2f;
        RectF card = new RectF(margin, top, margin + cardW, top + cardH);
        float rad = 72f;
        for (int i = 4; i >= 1; i--) {
            cv.drawRoundRect(card, rad, rad, line(glow(t, 0x10 / i), i * 10f));
        }
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean lt = t[LIGHT] == 1;
        f.setShader(new LinearGradient(0, card.top, 0, card.bottom,
                new int[]{Color.argb(lt ? 0x99 : 0x3A, 255, 255, 255), Color.argb(lt ? 0x44 : 0x14, 255, 255, 255), Color.argb(lt ? 0x70 : 0x26, 255, 255, 255)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRoundRect(card, rad, rad, f);
        Paint e = line(0, 2.5f);
        e.setShader(new LinearGradient(0, card.top, 0, card.bottom,
                new int[]{Color.argb(0xB0, 255, 255, 255), Color.argb(0x30, 255, 255, 255), Color.argb(0x60, 255, 255, 255)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRoundRect(card, rad, rad, e);
        if (lt) cv.drawRoundRect(card, rad, rad, line(orn(t, 0x40), 2f));
        RectF area = new RectF(card.left + pad, card.top, card.right - pad, card.bottom);
        drawBlock(cv, b, t, face, surah, ayahNo, area, card.top + 20f, true);
    }

    private static void cornerOrn(Canvas cv, int[] t, float x, float y, float sx, float sy, float size) {
        cv.save();
        cv.translate(x, y);
        cv.scale(sx, sy);
        Paint p = line(orn(t, 0xE0), 4f);
        float[] rs = {size, size * 0.72f, size * 0.44f};
        for (float r : rs) cv.drawArc(new RectF(-r, -r, r, r), 0f, 90f, false, p);
        float k = size * 0.58f / 1.4142f;
        cv.drawPath(star(k, k, size * 0.17f, size * 0.07f, 8, 0f), fill(orn(t, 0xF0)));
        cv.drawLine(size, 0f, size * 1.45f, 0f, p);
        cv.drawLine(0f, size, 0f, size * 1.45f, p);
        cv.drawCircle(size * 1.55f, 0f, 6f, fill(orn(t, 0xE0)));
        cv.drawCircle(0f, size * 1.55f, 6f, fill(orn(t, 0xE0)));
        cv.restore();
    }

    private static void tplFrame(Canvas cv, int[] t, Typeface face, String surah, String ayahNo, String text) {
        RectF outer = new RectF(54f, 54f, W - 54f, H - 54f);
        RectF inner = new RectF(outer);
        inner.inset(26f, 26f);
        cv.drawRoundRect(inner, 18f, 18f, fill(t[LIGHT] == 1 ? Color.argb(0x40, 255, 255, 255) : Color.argb(0x0E, 255, 255, 255)));
        cv.drawRoundRect(outer, 18f, 18f, line(orn(t, 0xF0), 7f));
        cv.drawRoundRect(inner, 12f, 12f, line(orn(t, 0xA0), 2.5f));
        Paint d = fill(orn(t, 0xB0));
        for (float x = 300f; x <= W - 300f; x += 70f) {
            diamond(cv, d, x, 66f, 5f);
            diamond(cv, d, x, H - 66f, 5f);
        }
        for (float y = 400f; y <= H - 400f; y += 70f) {
            diamond(cv, d, 66f, y, 5f);
            diamond(cv, d, W - 66f, y, 5f);
        }
        cornerOrn(cv, 84f, 84f, 1f, 1f, 150f);
        cornerOrn(cv, W - 84f, 84f, -1f, 1f, 150f);
        cornerOrn(cv, 84f, H - 84f, 1f, -1f, 150f);
        cornerOrn(cv, W - 84f, H - 84f, -1f, -1f, 150f);
        medallion(cv, t, W / 2f, 54f, 66f);
        medallion(cv, t, W / 2f, H - 54f, 66f);
        RectF area = new RectF(170f, 260f, W - 170f, H - 260f);
        centered(cv, layout(text, face, t, area.width(), area.height(), true), t, face, surah, ayahNo, area, true);
    }

    private static void starPattern(Canvas cv, int[] t, float cell) {
        Paint p = line(orn(t, 0x2A), 3f);
        Paint f = fill(orn(t, 0x20));
        int row = 0;
        for (float y = -cell / 2f; y < H + cell; y += cell * 0.5f, row++) {
            float off = row % 2 == 0 ? 0f : cell * 0.5f;
            for (float x = -cell + off; x < W + cell; x += cell) {
                cv.drawPath(star(x, y, cell * 0.36f, cell * 0.2f, 8, 0f), p);
                diamond(cv, f, x + cell * 0.5f, y, cell * 0.08f);
            }
        }
    }

    private static void tplStar(Canvas cv, int[] t, Typeface face, String surah, String ayahNo, String text) {
        starPattern(cv, t, 190f);
        Block b = layout(text, face, t, W - 200f - 110f, H - 700f, true);
        float cardH = b.total + 90f;
        RectF card = new RectF(100f, (H - cardH) / 2f, W - 100f, (H + cardH) / 2f);
        cv.drawRoundRect(card, 56f, 56f, fill((t[LIGHT] == 1 ? 0xEE : 0xE4) << 24 | (t[BG0] & 0xFFFFFF)));
        cv.drawRoundRect(card, 56f, 56f, line(orn(t, 0xE6), 5f));
        RectF in = new RectF(card);
        in.inset(18f, 18f);
        cv.drawRoundRect(in, 42f, 42f, line(orn(t, 0x80), 2f));
        medallion(cv, t, W / 2f, card.top, 60f);
        medallion(cv, t, W / 2f, card.bottom, 60f);
        RectF area = new RectF(card.left + 55f, card.top, card.right - 55f, card.bottom);
        drawBlock(cv, b, t, face, surah, ayahNo, area, card.top + 22f, true);
    }

    private static void dome(Path pa, float cx, float baseY, float r) {
        pa.moveTo(cx - r, baseY);
        pa.cubicTo(cx - r, baseY - r * 1.05f, cx - r * 0.45f, baseY - r * 1.45f, cx, baseY - r * 1.75f);
        pa.cubicTo(cx + r * 0.45f, baseY - r * 1.45f, cx + r, baseY - r * 1.05f, cx + r, baseY);
        pa.close();
    }

    private static void minaret(Path pa, float cx, float baseY, float topY, float w) {
        pa.addRect(cx - w / 2f, topY + 110f, cx + w / 2f, baseY, Path.Direction.CW);
        pa.addRect(cx - w * 0.85f, topY + 150f, cx + w * 0.85f, topY + 176f, Path.Direction.CW);
        pa.addRect(cx - w * 0.62f, topY + 90f, cx + w * 0.62f, topY + 112f, Path.Direction.CW);
        pa.moveTo(cx - w * 0.62f, topY + 90f);
        pa.lineTo(cx, topY);
        pa.lineTo(cx + w * 0.62f, topY + 90f);
        pa.close();
    }

    private static void tplMosque(Canvas cv, int[] t, Typeface face, String surah, String ayahNo, String text) {
        scatter(cv, t, 11L, 34, 40f, H * 0.62f);
        crescent(cv, fill(orn(t, 0xE0)), 880f, 250f, 62f);
        Path far = new Path();
        dome(far, 300f, H - 360f, 150f);
        dome(far, 790f, H - 360f, 150f);
        far.addRect(0f, H - 360f, W, H, Path.Direction.CW);
        cv.drawPath(far, fill(orn(t, 0x26)));
        Path near = new Path();
        dome(near, 540f, H - 380f, 230f);
        dome(near, 150f, H - 330f, 105f);
        dome(near, 930f, H - 330f, 105f);
        minaret(near, 330f, H - 380f, H - 880f, 50f);
        minaret(near, 750f, H - 380f, H - 880f, 50f);
        near.addRect(0f, H - 380f, W, H, Path.Direction.CW);
        cv.drawPath(near, fill(orn(t, 0x4A)));
        cv.drawPath(near, line(orn(t, 0x90), 3f));
        crescent(cv, fill(orn(t, 0xF0)), 540f, H - 380f - 402f - 34f, 30f);
        Paint win = fill(glow(t, 0xA0));
        float[] xs = {120f, 250f, 430f, 650f, 830f, 960f};
        for (float x : xs) cv.drawRoundRect(new RectF(x - 18f, H - 300f, x + 18f, H - 220f), 18f, 18f, win);
        cv.drawRect(0f, H - 150f, W, H, fill(orn(t, 0x30)));
        RectF area = new RectF(100f, 300f, W - 100f, 1040f);
        centered(cv, layout(text, face, t, area.width(), area.height(), true), t, face, surah, ayahNo, area, true);
    }

    private static void leaf(Canvas cv, Paint f, Paint s, float x, float y, float deg, float len) {
        cv.save();
        cv.translate(x, y);
        cv.rotate(deg);
        Path l = new Path();
        l.moveTo(0f, 0f);
        l.quadTo(len * 0.5f, -len * 0.38f, len, 0f);
        l.quadTo(len * 0.5f, len * 0.38f, 0f, 0f);
        cv.drawPath(l, f);
        cv.drawLine(0f, 0f, len * 0.9f, 0f, s);
        cv.restore();
    }

    private static void flower(Canvas cv, int[] t, float cx, float cy, float r) {
        Paint f = fill(orn(t, 0x66));
        Paint s = line(orn(t, 0xC0), 2.5f);
        for (int i = 0; i < 8; i++) {
            cv.save();
            cv.translate(cx, cy);
            cv.rotate(i * 45f);
            RectF o = new RectF(-r * 0.26f, -r, r * 0.26f, -r * 0.12f);
            cv.drawOval(o, f);
            cv.drawOval(o, s);
            cv.restore();
        }
        cv.drawCircle(cx, cy, r * 0.2f, fill(orn(t, 0xF0)));
    }

    private static void vine(Canvas cv, int[] t, Path path, float width, float leafLen, float from, float to, float step) {
        Paint s = line(orn(t, 0xC0), width);
        Paint lf = fill(orn(t, 0x5A));
        Paint ls = line(orn(t, 0x90), 2f);
        cv.drawPath(path, s);
        PathMeasure pm = new PathMeasure(path, false);
        float len = pm.getLength();
        float[] pos = new float[2], tan = new float[2];
        int k = 0;
        for (float f = from; f <= to; f += step, k++) {
            pm.getPosTan(len * f, pos, tan);
            float deg = (float) Math.toDegrees(Math.atan2(tan[1], tan[0]));
            leaf(cv, lf, ls, pos[0], pos[1], deg + (k % 2 == 0 ? -55f : 55f), leafLen);
        }
    }

    private static void floralCorner(Canvas cv, int[] t) {
        Path main = new Path();
        main.moveTo(24f, 600f);
        main.cubicTo(24f, 250f, 250f, 24f, 600f, 24f);
        vine(cv, t, main, 5f, 62f, 0.06f, 0.94f, 0.08f);
        Path inner = new Path();
        inner.moveTo(86f, 430f);
        inner.cubicTo(86f, 230f, 230f, 86f, 430f, 86f);
        vine(cv, t, inner, 3.5f, 42f, 0.1f, 0.9f, 0.13f);
        flower(cv, t, 130f, 130f, 66f);
        flower(cv, t, 40f, 470f, 32f);
        flower(cv, t, 470f, 40f, 32f);
        flower(cv, t, 330f, 210f, 22f);
        flower(cv, t, 210f, 330f, 22f);
    }

    private static void tplFloral(Canvas cv, int[] t, Typeface face, String surah, String ayahNo, String text) {
        float[][] c = {{0f, 0f, 1f, 1f}, {W, 0f, -1f, 1f}, {0f, H, 1f, -1f}, {W, H, -1f, -1f}};
        for (float[] k : c) {
            cv.save();
            cv.translate(k[0], k[1]);
            cv.scale(k[2], k[3]);
            floralCorner(cv, t);
            cv.restore();
        }
        divider(cv, t, W / 2f, 210f, 260f);
        divider(cv, t, W / 2f, H - 210f, 260f);
        RectF area = new RectF(170f, 400f, W - 170f, H - 400f);
        centered(cv, layout(text, face, t, area.width(), area.height(), true), t, face, surah, ayahNo, area, true);
    }

    private static Path archPath() {
        Path a = new Path();
        a.moveTo(100f, H - 200f);
        a.lineTo(100f, 780f);
        a.cubicTo(100f, 540f, 400f, 470f, 540f, 250f);
        a.cubicTo(680f, 470f, 980f, 540f, 980f, 780f);
        a.lineTo(980f, H - 200f);
        a.close();
        return a;
    }

    private static void tplArch(Canvas cv, int[] t, Typeface face, String surah, String ayahNo, String text) {
        scatter(cv, t, 5L, 16, 30f, 240f);
        Path arch = archPath();
        cv.drawPath(arch, fill(t[LIGHT] == 1 ? Color.argb(0x66, 255, 255, 255) : Color.argb(0x16, 255, 255, 255)));
        cv.drawPath(arch, line(orn(t, 0xF0), 7f));
        float cy = (250f + H - 200f) / 2f;
        float[] sc = {0.94f, 0.88f};
        int[] al = {0xA0, 0x55};
        for (int i = 0; i < sc.length; i++) {
            cv.save();
            cv.scale(sc[i], sc[i], W / 2f, cy);
            cv.drawPath(arch, line(orn(t, al[i]), i == 0 ? 3.5f : 2.5f));
            cv.restore();
        }
        lantern(cv, t, W / 2f, 120f, 0.62f);
        Paint d = fill(orn(t, 0xC0));
        for (float x = 130f; x <= W - 130f; x += 64f) diamond(cv, d, x, H - 150f, 7f);
        cv.drawLine(100f, H - 200f, W - 100f, H - 200f, line(orn(t, 0xE0), 5f));
        RectF area = new RectF(190f, 640f, W - 190f, H - 330f);
        centered(cv, layout(text, face, t, area.width(), area.height(), true), t, face, surah, ayahNo, area, true);
    }

    private static void lantern(Canvas cv, int[] t, float cx, float hang, float s) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new RadialGradient(cx, hang + 140f * s, 300f * s, glow(t, 0x70), glow(t, 0), Shader.TileMode.CLAMP));
        cv.drawCircle(cx, hang + 140f * s, 300f * s, g);
        cv.drawLine(cx, -10f, cx, hang, line(orn(t, 0xC0), 4f * s + 1f));
        Path cap = new Path();
        cap.moveTo(cx - 36f * s, hang + 42f * s);
        cap.lineTo(cx, hang);
        cap.lineTo(cx + 36f * s, hang + 42f * s);
        cap.close();
        cv.drawPath(cap, fill(orn(t, 0xE6)));
        Path body = new Path();
        body.moveTo(cx - 52f * s, hang + 42f * s);
        body.lineTo(cx + 52f * s, hang + 42f * s);
        body.cubicTo(cx + 90f * s, hang + 120f * s, cx + 90f * s, hang + 190f * s, cx + 52f * s, hang + 252f * s);
        body.lineTo(cx - 52f * s, hang + 252f * s);
        body.cubicTo(cx - 90f * s, hang + 190f * s, cx - 90f * s, hang + 120f * s, cx - 52f * s, hang + 42f * s);
        body.close();
        cv.drawPath(body, fill(glow(t, 0x66)));
        cv.drawPath(body, line(orn(t, 0xF0), 4f * s + 1f));
        Paint rib = line(orn(t, 0xA0), 2.5f * s + 0.5f);
        for (int side = -1; side <= 1; side += 2) {
            Path r = new Path();
            r.moveTo(cx + side * 26f * s, hang + 42f * s);
            r.quadTo(cx + side * 66f * s, hang + 147f * s, cx + side * 26f * s, hang + 252f * s);
            cv.drawPath(r, rib);
        }
        cv.drawLine(cx, hang + 42f * s, cx, hang + 252f * s, rib);
        cv.drawRoundRect(new RectF(cx - 58f * s, hang + 252f * s, cx + 58f * s, hang + 274f * s), 8f * s, 8f * s, fill(orn(t, 0xE6)));
        cv.drawLine(cx, hang + 274f * s, cx, hang + 334f * s, line(orn(t, 0xC0), 3f * s + 0.5f));
        cv.drawCircle(cx, hang + 338f * s, 8f * s, fill(orn(t, 0xE6)));
    }

    private static void tplLantern(Canvas cv, int[] t, Typeface face, String surah, String ayahNo, String text) {
        scatter(cv, t, 23L, 44, 20f, H - 100f);
        lantern(cv, t, 360f, 20f, 0.55f);
        lantern(cv, t, 730f, 10f, 0.6f);
        lantern(cv, t, 190f, 70f, 1f);
        lantern(cv, t, 890f, 50f, 0.92f);
        lantern(cv, t, 540f, 190f, 1.2f);
        Paint d = fill(orn(t, 0xC0));
        for (float x = 150f; x <= W - 150f; x += 64f) diamond(cv, d, x, H - 140f, 7f);
        cv.drawLine(110f, H - 190f, W - 110f, H - 190f, line(orn(t, 0xA0), 3f));
        RectF area = new RectF(110f, 700f, W - 110f, H - 240f);
        centered(cv, layout(text, face, t, area.width(), area.height(), true), t, face, surah, ayahNo, area, true);
    }

    private static void tplMinimal(Canvas cv, int[] t, Typeface face, String surah, String ayahNo, String text) {
        float[] rs = {420f, 580f, 740f};
        for (float r : rs) cv.drawCircle(W / 2f, H / 2f, r, line(orn(t, 0x22), 2f));
        for (int i = 0; i < 2; i++) {
            float y = i == 0 ? 230f : H - 230f;
            Paint p = line(0, 3f);
            p.setShader(new LinearGradient(120f, 0, W - 120f, 0, new int[]{orn(t, 0), orn(t, 0xB0), orn(t, 0)}, null, Shader.TileMode.CLAMP));
            cv.drawLine(120f, y, W - 120f, y, p);
            cv.drawCircle(W / 2f, y, 44f, fill(t[BG0]));
            cv.drawPath(star(W / 2f, y, 36f, 20f, 8, 0f), line(orn(t, 0xF0), 4f));
        }
        RectF area = new RectF(110f, 330f, W - 110f, H - 330f);
        centered(cv, layout(text, face, t, area.width(), area.height(), false), t, face, surah, ayahNo, area, false);
    }

    static Bitmap render(String surah, String ayahNo, String text, int theme, int tpl, Typeface face, float scale) {
        int[] t = THEMES[Math.floorMod(theme, THEMES.length)];
        Bitmap bm = Bitmap.createBitmap(Math.round(W * scale), Math.round(H * scale), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        cv.scale(scale, scale);
        background(cv, t);
        switch (Math.floorMod(tpl, TPL_LABELS.length)) {
            case 1:
                tplFrame(cv, t, face, surah, ayahNo, text);
                break;
            case 2:
                tplStar(cv, t, face, surah, ayahNo, text);
                break;
            case 3:
                tplMosque(cv, t, face, surah, ayahNo, text);
                break;
            case 4:
                tplFloral(cv, t, face, surah, ayahNo, text);
                break;
            case 5:
                tplArch(cv, t, face, surah, ayahNo, text);
                break;
            case 6:
                tplLantern(cv, t, face, surah, ayahNo, text);
                break;
            case 7:
                tplMinimal(cv, t, face, surah, ayahNo, text);
                break;
            default:
                tplGlass(cv, t, face, surah, ayahNo, text);
                break;
        }
        return bm;
    }

    // ------------------------------------------------------------------ sharing

    static Uri save(Activity a, Bitmap bm) throws Exception {
        File dir = new File(a.getCacheDir(), "share");
        if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("dir");
        File f = new File(dir, "ayah_" + System.currentTimeMillis() + ".png");
        File[] old = dir.listFiles();
        if (old != null) {
            for (File o : old) if (o.lastModified() < System.currentTimeMillis() - 3600000L) o.delete();
        }
        try (FileOutputStream os = new FileOutputStream(f)) {
            bm.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        return FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f);
    }

    private static boolean installed(Activity a, String pkg) {
        try {
            a.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    static void sendImage(Activity a, Uri uri, boolean whatsapp) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("", uri));
        if (whatsapp) {
            String pkg = installed(a, "com.whatsapp") ? "com.whatsapp" : installed(a, "com.whatsapp.w4b") ? "com.whatsapp.w4b" : null;
            if (pkg != null) {
                i.setPackage(pkg);
                try {
                    a.startActivity(i);
                    return;
                } catch (RuntimeException ignored) {
                    i.setPackage(null);
                }
            } else {
                Ui.toast(a, a.getString(R.string.card_no_whatsapp));
            }
        }
        a.startActivity(Intent.createChooser(i, a.getString(R.string.quran_share)));
    }

    private static TextView label(Activity a, int res) {
        TextView l = Ui.text(a, a.getString(res), 13, R.color.text_secondary);
        l.setTypeface(Ui.medium());
        l.setPaddingRelative(Ui.dp(a, 4), Ui.dp(a, 12), 0, Ui.dp(a, 6));
        return l;
    }

    private static HorizontalScrollView scroller(Activity a, LinearLayout row) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        return hs;
    }

    static void show(final Activity a, final String surah, final String ayahNo, final String text, final String plain) {
        final Typeface face = face(a);
        final String surahName = a.getString(R.string.card_surah, surah);
        final int[] sel = {Math.floorMod(Store.shareTheme(a), THEMES.length), Math.floorMod(Store.shareTpl(a), TPL_LABELS.length)};
        final int[] gen = {0};
        final boolean[] busy = {false};

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 8));
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        final ImageView img = new ImageView(a);
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(img, new LinearLayout.LayoutParams(Ui.dp(a, 190), Ui.dp(a, 338)));

        final Runnable[] rerender = new Runnable[1];
        rerender[0] = () -> {
            final int mine = ++gen[0];
            final int th = sel[0], tp = sel[1];
            img.animate().cancel();
            img.animate().alpha(0.5f).setDuration(Anim.D_TAP).start();
            new Thread(() -> {
                Bitmap bm = null;
                try {
                    bm = render(surahName, ayahNo, text, th, tp, face, 0.4f);
                } catch (Throwable ignored) {
                }
                final Bitmap res = bm;
                a.runOnUiThread(() -> {
                    if (mine != gen[0] || a.isFinishing() || a.isDestroyed()) return;
                    if (res != null) img.setImageBitmap(res);
                    img.animate().alpha(1f).setDuration(Anim.D_SHORT).start();
                });
            }, "share-card-preview").start();
        };

        LinearLayout tplRow = new LinearLayout(a);
        tplRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] chips = new TextView[TPL_LABELS.length];
        final Runnable styleChips = () -> {
            for (int i = 0; i < chips.length; i++) {
                boolean on = i == sel[1];
                chips[i].setBackground(Ui.rect(Ui.color(a, on ? R.color.accent : R.color.neutral_soft), Ui.dpf(a, 100)));
                chips[i].setTextColor(Ui.color(a, on ? R.color.on_accent : R.color.text_primary));
            }
        };
        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            chips[i] = Ui.chipView(a, a.getString(TPL_LABELS[i]), v -> {
                if (sel[1] == idx) return;
                Ui.tick(v);
                sel[1] = idx;
                Store.setShareTpl(a, idx);
                styleChips.run();
                rerender[0].run();
            });
            tplRow.addView(chips[i]);
        }
        styleChips.run();

        LinearLayout dots = new LinearLayout(a);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        final View[] dv = new View[THEMES.length];
        final Runnable styleDots = () -> {
            for (int i = 0; i < dv.length; i++) {
                int[] th = THEMES[i];
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{th[BG1], th[BG0]});
                g.setShape(GradientDrawable.OVAL);
                boolean on = i == sel[0];
                g.setStroke(Ui.dp(a, on ? 3 : 1), on ? Ui.color(a, R.color.accent) : Ui.color(a, R.color.stroke));
                dv[i].setBackground(g);
                dv[i].animate().scaleX(on ? 1.14f : 1f).scaleY(on ? 1.14f : 1f).setDuration(Anim.D_MED).setInterpolator(Anim.SPRING).start();
            }
        };
        for (int i = 0; i < dv.length; i++) {
            final int idx = i;
            View v = new View(a);
            dv[i] = v;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36));
            lp.setMargins(Ui.dp(a, 5), Ui.dp(a, 4), Ui.dp(a, 5), Ui.dp(a, 4));
            dots.addView(v, lp);
            v.setOnClickListener(x -> {
                if (sel[0] == idx) return;
                Ui.tick(x);
                sel[0] = idx;
                Store.setShareTheme(a, idx);
                styleDots.run();
                rerender[0].run();
            });
        }
        styleDots.run();

        root.addView(label(a, R.string.card_template), new LinearLayout.LayoutParams(-1, -2));
        root.addView(scroller(a, tplRow), new LinearLayout.LayoutParams(-1, -2));
        root.addView(label(a, R.string.card_color), new LinearLayout.LayoutParams(-1, -2));
        root.addView(scroller(a, dots), new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog[] dlg = new AlertDialog[1];
        TextView wa = button(a, R.string.card_whatsapp, R.drawable.btn_primary, R.color.on_accent);
        TextView sh = button(a, R.string.card_share_image, R.drawable.btn_secondary, R.color.text_primary);
        TextView tx = button(a, R.string.card_share_text, R.drawable.btn_secondary, R.color.text_primary);
        wa.setOnClickListener(x -> export(a, surahName, ayahNo, text, sel, face, true, dlg[0], busy));
        sh.setOnClickListener(x -> export(a, surahName, ayahNo, text, sel, face, false, dlg[0], busy));
        tx.setOnClickListener(x -> {
            dlg[0].dismiss();
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, plain);
            a.startActivity(Intent.createChooser(i, a.getString(R.string.quran_share)));
        });
        for (TextView b : new TextView[]{wa, sh, tx}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 48));
            lp.topMargin = Ui.dp(a, 10);
            root.addView(b, lp);
        }
        dlg[0] = new Dlg(a).setView(sv).create();
        dlg[0].setOnDismissListener(d -> gen[0]++);
        dlg[0].show();
        rerender[0].run();
    }

    private static void export(final Activity a, final String surahName, final String ayahNo, final String text, final int[] sel,
                               final Typeface face, final boolean whatsapp, final AlertDialog d, final boolean[] busy) {
        if (busy[0]) return;
        busy[0] = true;
        final int th = sel[0], tp = sel[1];
        new Thread(() -> {
            Uri u = null;
            try {
                Bitmap bm = render(surahName, ayahNo, text, th, tp, face, 1f);
                u = save(a, bm);
                bm.recycle();
            } catch (Throwable ignored) {
            }
            final Uri fu = u;
            a.runOnUiThread(() -> {
                busy[0] = false;
                if (a.isFinishing() || a.isDestroyed()) return;
                if (fu == null) {
                    Ui.toast(a, a.getString(R.string.card_failed));
                    return;
                }
                d.dismiss();
                sendImage(a, fu, whatsapp);
            });
        }, "share-card-save").start();
    }

    private static TextView button(Activity a, int label, int bg, int colorRes) {
        TextView t = Ui.text(a, a.getString(label), 16, colorRes);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(bg);
        Ui.press(a, t);
        return t;
    }
}
