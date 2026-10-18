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
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

/** Renders an ayah as a 9:16 glass card (WhatsApp status size) and hands it to WhatsApp or the system share sheet. */
final class ShareCard {
    private ShareCard() {
    }

    static final int W = 1080, H = 1920;
    private static final int[][] THEMES = {
            {0xFF06231F, 0xFF0E6B57, 0xFF39D3A6, 0xFFF2D58B},
            {0xFF070B1C, 0xFF1D3A8A, 0xFF6C9BFF, 0xFFBFD4FF},
            {0xFF21160A, 0xFF8A5A14, 0xFFF0B84A, 0xFFFFE3A3},
            {0xFF1E0A24, 0xFF8E2C5C, 0xFFFF7BA9, 0xFFFFD0E1},
            {0xFF000000, 0xFF2B2B30, 0xFF8E8E93, 0xFFE5E5EA}
    };

    static int themeCount() {
        return THEMES.length;
    }

    static int themeColor(int t) {
        return THEMES[Math.floorMod(t, THEMES.length)][1];
    }

    static Typeface face(Activity a) {
        try {
            return Typeface.createFromAsset(a.getAssets(), "fonts/quran.ttf");
        } catch (Throwable e) {
            return Typeface.SERIF;
        }
    }

    static Bitmap render(String surah, String ayahNo, String text, int theme, Typeface face, String brand) {
        int[] t = THEMES[Math.floorMod(theme, THEMES.length)];
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setShader(new LinearGradient(0, 0, W, H, new int[]{t[0], t[1], t[0]}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W * 0.85f, H * 0.12f, 620f, (t[2] & 0xFFFFFF) | 0x66000000, t[2] & 0xFFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W * 0.1f, H * 0.9f, 700f, (t[3] & 0xFFFFFF) | 0x40000000, t[3] & 0xFFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, p);
        p.setShader(null);

        float margin = 78f, pad = 70f;
        float cardW = W - margin * 2;
        float textW = cardW - pad * 2;

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(face);
        tp.setColor(Color.WHITE);
        StaticLayout lay = null;
        float header = 190f, footer = 190f;
        float maxText = H - 2 * 230f - header - footer;
        for (float size = 78f; size >= 30f; size -= 2f) {
            tp.setTextSize(size);
            lay = build(text, tp, (int) textW, Integer.MAX_VALUE);
            if (lay.getHeight() <= maxText) break;
        }
        if (lay.getHeight() > maxText) lay = build(text, tp, (int) textW, Math.max(3, (int) (maxText / (tp.getTextSize() * 1.7f))));
        float cardH = header + lay.getHeight() + footer;
        float top = (H - cardH) / 2f;
        RectF card = new RectF(margin, top, margin + cardW, top + cardH);

        float rad = 72f;
        for (int i = 4; i >= 1; i--) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(i * 10f);
            p.setColor((t[2] & 0xFFFFFF) | ((0x10 / i) << 24));
            cv.drawRoundRect(card, rad, rad, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, card.top, 0, card.bottom,
                new int[]{Color.argb(0x3A, 255, 255, 255), Color.argb(0x14, 255, 255, 255), Color.argb(0x26, 255, 255, 255)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRoundRect(card, rad, rad, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setShader(new LinearGradient(0, card.top, 0, card.bottom,
                new int[]{Color.argb(0xB0, 255, 255, 255), Color.argb(0x30, 255, 255, 255), Color.argb(0x60, 255, 255, 255)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRoundRect(card, rad, rad, p);
        p.setShader(null);

        cv.save();
        Path clip = new Path();
        clip.addRoundRect(card, rad, rad, Path.Direction.CW);
        cv.clipPath(clip);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, card.top, 0, card.top + 200f,
                Color.argb(0x40, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        cv.drawRect(card.left, card.top, card.right, card.top + 200f, p);
        p.setShader(null);
        cv.restore();

        TextPaint hp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        hp.setTypeface(face);
        hp.setTextSize(58f);
        hp.setColor(t[3]);
        hp.setTextAlign(Paint.Align.CENTER);
        float pillW = Math.min(cardW - 160f, hp.measureText(surah) + 150f);
        RectF pill = new RectF(W / 2f - pillW / 2f, card.top + 52f, W / 2f + pillW / 2f, card.top + 148f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(0x30, 255, 255, 255));
        cv.drawRoundRect(pill, 48f, 48f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(Color.argb(0x66, 255, 255, 255));
        cv.drawRoundRect(pill, 48f, 48f, p);
        cv.drawText(surah, W / 2f, pill.centerY() - (hp.ascent() + hp.descent()) / 2f, hp);

        cv.save();
        cv.translate(card.left + pad, card.top + header);
        lay.draw(cv);
        cv.restore();

        float fy = card.bottom - footer + 46f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(card.left + 150f, 0, card.right - 150f, 0,
                new int[]{0, Color.argb(0x90, 255, 255, 255), 0}, null, Shader.TileMode.CLAMP));
        cv.drawRect(card.left + 150f, fy, card.right - 150f, fy + 2.5f, p);
        p.setShader(null);

        TextPaint rp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        rp.setTypeface(Ui.medium());
        rp.setTextSize(44f);
        rp.setColor(t[3]);
        rp.setTextAlign(Paint.Align.CENTER);
        cv.drawText(surah + "  \u00B7  " + ayahNo, W / 2f, fy + 100f, rp);

        return bm;
    }

    private static Bitmap safeRender(Activity a, String surah, String ayahNo, String text, int theme, Typeface face, String brand) {
        try {
            return render(surah, ayahNo, text, theme, face, brand);
        } catch (Throwable e) {
            Ui.toast(a, a.getString(R.string.card_failed));
            return null;
        }
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

    static void show(final Activity a, final String surah, final String ayahNo, final String text, final String plain) {
        final Typeface face = face(a);
        final String surahName = a.getString(R.string.card_surah, surah);
        final String brand = a.getString(R.string.card_brand);
        final int[] theme = {Store.shareTheme(a)};
        final Bitmap[] cur = {safeRender(a, surahName, ayahNo, text, theme[0], face, brand)};
        if (cur[0] == null) return;

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 8));

        final ImageView img = new ImageView(a);
        img.setImageBitmap(cur[0]);
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(a, 190), Ui.dp(a, 338));
        root.addView(img, ip);

        LinearLayout dots = new LinearLayout(a);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(a, 14);
        root.addView(dots, dlp);
        final View[] dv = new View[themeCount()];
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            for (int i = 0; i < dv.length; i++) {
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                g.setColor(themeColor(i));
                g.setStroke(Ui.dp(a, i == theme[0] ? 3 : 1), i == theme[0] ? Ui.color(a, R.color.accent) : Ui.color(a, R.color.stroke));
                dv[i].setBackground(g);
            }
        };
        for (int i = 0; i < dv.length; i++) {
            final int idx = i;
            View v = new View(a);
            dv[i] = v;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34));
            lp.setMargins(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
            dots.addView(v, lp);
            v.setOnClickListener(x -> {
                theme[0] = idx;
                Store.setShareTheme(a, idx);
                Bitmap nb = safeRender(a, surahName, ayahNo, text, idx, face, brand);
                if (nb == null) return;
                Bitmap old = cur[0];
                cur[0] = nb;
                img.setImageBitmap(cur[0]);
                if (old != null && !old.isRecycled()) old.recycle();
                refresh[0].run();
            });
        }
        refresh[0].run();

        final AlertDialog[] dlg = new AlertDialog[1];
        TextView wa = button(a, R.string.card_whatsapp, R.drawable.btn_primary, R.color.on_accent);
        TextView sh = button(a, R.string.card_share_image, R.drawable.btn_secondary, R.color.text_primary);
        TextView tx = button(a, R.string.card_share_text, R.drawable.btn_secondary, R.color.text_primary);
        wa.setOnClickListener(x -> go(a, cur[0], true, dlg[0]));
        sh.setOnClickListener(x -> go(a, cur[0], false, dlg[0]));
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
        dlg[0] = new Dlg(a).setView(root).create();
        dlg[0].show();
    }

    private static void go(Activity a, Bitmap bm, boolean whatsapp, AlertDialog d) {
        try {
            Uri u = save(a, bm);
            d.dismiss();
            sendImage(a, u, whatsapp);
        } catch (Exception e) {
            Ui.toast(a, a.getString(R.string.card_failed));
        }
    }

    private static TextView button(Activity a, int label, int bg, int colorRes) {
        TextView t = Ui.text(a, a.getString(label), 16, colorRes);
        t.setTypeface(Ui.medium());
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(bg);
        return t;
    }
}
