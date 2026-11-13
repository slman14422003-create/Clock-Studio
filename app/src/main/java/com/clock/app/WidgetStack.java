package com.clock.app;

import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

public class WidgetStack extends WBase {
    @Override
    int layout(boolean rtl, WCfg cfg) {
        return rtl ? R.layout.wl_stack_rtl : R.layout.wl_stack_ltr;
    }

    @Override
    int defaultBg() {
        return 1;
    }

    @Override
    int[][] sizes() {
        return new int[][]{{R.id.wS1, 13}};
    }

    @Override
    int caps() {
        return TEXT | FMT | DATE;
    }

    @Override
    int[] primary() {
        return new int[]{R.id.wP1};
    }

    @Override
    int[] secondary() {
        return new int[]{R.id.wP2, R.id.wS1};
    }

    @Override
    void fill(Context c, RemoteViews rv, WCfg cfg) {
        timeFormat(rv, R.id.wP1, cfg, c, "h", "HH");
        timeFormat(rv, R.id.wP2, cfg, c, "mm", "mm");
        rv.setViewVisibility(R.id.wS1, cfg.date ? View.VISIBLE : View.GONE);
        rv.setTextViewText(R.id.wS1, Fmt.date(c, System.currentTimeMillis(), "EEE d MMM"));
    }
}
