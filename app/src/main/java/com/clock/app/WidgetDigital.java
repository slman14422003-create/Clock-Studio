package com.clock.app;

import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

public class WidgetDigital extends WBase {
    @Override
    int layout(boolean rtl, WCfg cfg) {
        return rtl ? R.layout.wl_digital_rtl : R.layout.wl_digital_ltr;
    }

    @Override
    int[] primary() {
        return new int[]{R.id.wP1, R.id.wP2};
    }

    @Override
    int[] secondary() {
        return new int[]{R.id.wS1};
    }

    @Override
    int[] accents() {
        return new int[]{R.id.wA1};
    }

    @Override
    int[][] sizes() {
        return new int[][]{{R.id.wP1, 46}, {R.id.wA1, 15}, {R.id.wP2, 16}, {R.id.wS1, 14}};
    }

    @Override
    void fill(Context c, RemoteViews rv, WCfg cfg) {
        long now = System.currentTimeMillis();
        timeFormat(rv, R.id.wP1, cfg, c, "h:mm", "HH:mm");
        rv.setTextViewText(R.id.wA1, ampm(c, cfg));
        rv.setViewVisibility(R.id.wP2, cfg.date ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.wS1, cfg.date ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.wP2, Fmt.date(c, now, "EEEE"));
        rv.setTextViewText(R.id.wS1, Fmt.date(c, now, "d MMMM"));
    }
}
