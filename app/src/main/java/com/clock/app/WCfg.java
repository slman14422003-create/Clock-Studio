package com.clock.app;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONObject;

/** Per-widget appearance, stored as JSON under wc_<appWidgetId>. Version 2 added custom colours, six radii, border, hijri. */
final class WCfg {
    /** Corner radii in dp, sharp to circular. */
    static final int[] RADII = {0, 12, 22, 34, 50, 80};
    static final int DEF_RADIUS = 2;

    /** 0 adaptive, 1..12 gradients, 13..26 solid colours, 27 custom colour. */
    static final int[][] BG = {
            {0, 0},
            {0xFF0A84FF, 0xFF5E5CE6}, {0xFF12906B, 0xFF0B3D4A}, {0xFFFF9F0A, 0xFFFF375F}, {0xFF2C2C3E, 0xFF0B0B14},
            {0xFFBF5AF2, 0xFF5E5CE6}, {0xFFFF6482, 0xFFBF5AF2}, {0xFF30B0C7, 0xFF0A84FF}, {0xFFF6B93B, 0xFFF78F6B},
            {0xFF0F2027, 0xFF2C5364}, {0xFF11998E, 0xFF38C98A}, {0xFFFF512F, 0xFFDD2476}, {0xFF667EEA, 0xFF764BA2},
            {0xFF000000, 0xFF000000}, {0xFFFFFFFF, 0xFFFFFFFF}, {0xFF2C2C2E, 0xFF2C2C2E}, {0xFFFF3B30, 0xFFFF3B30},
            {0xFFFF9500, 0xFFFF9500}, {0xFFFFD60A, 0xFFFFD60A}, {0xFF34C759, 0xFF34C759}, {0xFF0A84FF, 0xFF0A84FF},
            {0xFF30B0C7, 0xFF30B0C7}, {0xFFFF2D55, 0xFFFF2D55}, {0xFF5E5CE6, 0xFF5E5CE6}, {0xFFAF52DE, 0xFFAF52DE},
            {0xFF8E8E93, 0xFF8E8E93}, {0xFFA2845E, 0xFFA2845E},
            {0xFF0A84FF, 0xFF0A84FF}
    };
    static final int GRAD_LAST = 12;
    static final int SOLID_FIRST = 13;
    static final int CUSTOM = BG.length - 1;

    /** Text / accent palette; index 0 = automatic, index PALETTE.length = custom colour. */
    static final int[] PALETTE = {0, 0xFFFFFFFF, 0xFF000000, 0xFFE8C468, 0xFF0A84FF, 0xFF30D158, 0xFFFF375F, 0xFFFF9F0A,
            0xFFBF5AF2, 0xFF64D2FF, 0xFFFFD60A, 0xFF8E8E93};

    int bg, alpha = 100, radius = DEF_RADIUS, text, accent, sizePct = 100, fmt, face;
    int customBg = 0xFF0A84FF, customText = 0xFFFFFFFF, customAccent = 0xFFFF9F0A;
    boolean date = true, hijri, border;

    static WCfg load(Context c, int id, int defBg) {
        WCfg g = new WCfg();
        g.bg = defBg;
        try {
            String raw = Store.raw(c, "wc_" + id);
            if (raw != null && !raw.isEmpty()) g.read(new JSONObject(raw), defBg);
        } catch (Exception ignored) {
        }
        return g;
    }

    private void read(JSONObject o, int defBg) {
        boolean v2 = o.optInt("v", 1) >= 2;
        int b = o.optInt("bg", defBg);
        int r = o.optInt("r", v2 ? DEF_RADIUS : 1);
        if (!v2) {
            // version 1: 4 radii (10/22/34/50) and solid colours started at index 8
            r = r + 1;
            if (b >= 8) b += SOLID_FIRST - 8;
        }
        bg = Math.max(0, Math.min(BG.length - 1, b));
        radius = Math.max(0, Math.min(RADII.length - 1, r));
        alpha = Math.max(0, Math.min(100, o.optInt("a", 100)));
        text = Math.max(0, Math.min(PALETTE.length, o.optInt("t", 0)));
        accent = Math.max(0, Math.min(PALETTE.length, o.optInt("ac", 0)));
        if (v2) {
            sizePct = Math.max(70, Math.min(150, o.optInt("sz", 100)));
        } else {
            int s = o.optInt("s", 1);
            sizePct = s == 0 ? 85 : s == 2 ? 118 : 100;
        }
        fmt = Math.max(0, Math.min(2, o.optInt("f", 0)));
        face = Math.max(0, Math.min(2, o.optInt("fc", 0)));
        date = o.optBoolean("d", true);
        hijri = o.optBoolean("h", false);
        border = o.optBoolean("bd", false);
        customBg = o.optInt("cb", customBg);
        customText = o.optInt("ct", customText);
        customAccent = o.optInt("ca", customAccent);
    }

    void save(Context c, int id) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 2).put("bg", bg).put("a", alpha).put("r", radius).put("t", text).put("ac", accent)
                    .put("sz", sizePct).put("f", fmt).put("fc", face).put("d", date).put("h", hijri).put("bd", border)
                    .put("cb", customBg).put("ct", customText).put("ca", customAccent);
            Store.putRaw(c, "wc_" + id, o.toString());
        } catch (Exception ignored) {
        }
    }

    static void delete(Context c, int id) {
        Store.putRaw(c, "wc_" + id, "");
    }

    static void move(Context c, int from, int to) {
        String raw = Store.raw(c, "wc_" + from);
        if (raw != null && !raw.isEmpty()) Store.putRaw(c, "wc_" + to, raw);
        Store.putRaw(c, "wc_" + from, "");
    }

    boolean adaptive() {
        return bg == 0;
    }

    int bgColor() {
        return bg == CUSTOM ? customBg : BG[bg][0];
    }

    /** Colour used to judge whether the background is light or dark (average of the gradient ends). */
    private int judgeColor() {
        if (bg == CUSTOM) return customBg;
        int a = BG[bg][0], b = BG[bg][1];
        return Color.rgb((Color.red(a) + Color.red(b)) / 2, (Color.green(a) + Color.green(b)) / 2, (Color.blue(a) + Color.blue(b)) / 2);
    }

    int autoText() {
        if (bg == 0) return 0;
        int col = judgeColor();
        double lum = (0.299 * Color.red(col) + 0.587 * Color.green(col) + 0.114 * Color.blue(col)) / 255.0;
        if (alpha < 40) return Color.WHITE;
        return lum > 0.62 ? Color.BLACK : Color.WHITE;
    }

    /** Main text colour, or 0 to keep the theme colour of the layout. */
    int textColor() {
        if (text == 0) return autoText();
        if (text >= PALETTE.length) return customText;
        return PALETTE[text];
    }

    /** Accent colour chosen by the user, or 0 when automatic. */
    int accentColor() {
        if (accent == 0) return 0;
        if (accent >= PALETTE.length) return customAccent;
        return PALETTE[accent];
    }

    float scale() {
        return sizePct / 100f;
    }
}
