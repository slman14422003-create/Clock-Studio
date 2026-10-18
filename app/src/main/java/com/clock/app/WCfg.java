package com.clock.app;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONObject;

final class WCfg {
    static final int[] RADII = {10, 22, 34, 50};
    static final int[][] BG = {
            {0, 0},
            {0xFF0A84FF, 0xFF5E5CE6}, {0xFF12906B, 0xFF0B3D4A}, {0xFFFF9F0A, 0xFFFF375F}, {0xFF2C2C3E, 0xFF0B0B14},
            {0xFFBF5AF2, 0xFF5E5CE6}, {0xFFFF6482, 0xFFBF5AF2}, {0xFF30B0C7, 0xFF0A84FF},
            {0xFF000000, 0xFF000000}, {0xFFFFFFFF, 0xFFFFFFFF}, {0xFF2C2C2E, 0xFF2C2C2E}, {0xFFFF3B30, 0xFFFF3B30},
            {0xFFFF9500, 0xFFFF9500}, {0xFFFFD60A, 0xFFFFD60A}, {0xFF34C759, 0xFF34C759}, {0xFF0A84FF, 0xFF0A84FF},
            {0xFF30B0C7, 0xFF30B0C7}, {0xFFFF2D55, 0xFFFF2D55}
    };
    static final int GRAD_LAST = 7;
    static final int[] PALETTE = {0, 0xFFFFFFFF, 0xFF000000, 0xFFE8C468, 0xFF0A84FF, 0xFF30D158, 0xFFFF375F, 0xFFFF9F0A};

    int bg, alpha = 100, radius = 1, text, accent, size = 1, fmt, face;
    boolean date = true;

    static WCfg load(Context c, int id, int defBg) {
        WCfg g = new WCfg();
        g.bg = defBg;
        try {
            String raw = Store.raw(c, "wc_" + id);
            if (raw != null && !raw.isEmpty()) {
                JSONObject o = new JSONObject(raw);
                g.bg = Math.max(0, Math.min(BG.length - 1, o.optInt("bg", defBg)));
                g.alpha = Math.max(0, Math.min(100, o.optInt("a", 100)));
                g.radius = Math.max(0, Math.min(RADII.length - 1, o.optInt("r", 1)));
                g.text = Math.max(0, Math.min(PALETTE.length - 1, o.optInt("t", 0)));
                g.accent = Math.max(0, Math.min(PALETTE.length - 1, o.optInt("ac", 0)));
                g.size = Math.max(0, Math.min(2, o.optInt("s", 1)));
                g.fmt = Math.max(0, Math.min(2, o.optInt("f", 0)));
                g.face = Math.max(0, Math.min(2, o.optInt("fc", 0)));
                g.date = o.optBoolean("d", true);
            }
        } catch (Exception ignored) {
        }
        return g;
    }

    void save(Context c, int id) {
        try {
            JSONObject o = new JSONObject();
            o.put("bg", bg).put("a", alpha).put("r", radius).put("t", text).put("ac", accent).put("s", size)
                    .put("f", fmt).put("fc", face).put("d", date);
            Store.putRaw(c, "wc_" + id, o.toString());
        } catch (Exception ignored) {
        }
    }

    static void delete(Context c, int id) {
        Store.putRaw(c, "wc_" + id, "");
    }

    boolean adaptive() {
        return bg == 0;
    }

    int bgColor() {
        return BG[bg][0];
    }

    int autoText() {
        if (bg == 0) return 0;
        int col = BG[bg][0];
        double lum = (0.299 * Color.red(col) + 0.587 * Color.green(col) + 0.114 * Color.blue(col)) / 255.0;
        if (alpha < 40) return Color.WHITE;
        return lum > 0.62 ? Color.BLACK : Color.WHITE;
    }

    float scale() {
        return size == 0 ? 0.85f : size == 2 ? 1.18f : 1f;
    }
}
