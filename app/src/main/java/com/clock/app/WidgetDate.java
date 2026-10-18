package com.clock.app;

import android.content.Context;
import android.widget.RemoteViews;

public class WidgetDate extends WBase {
    @Override
    int layout(boolean rtl, WCfg cfg) {
        return rtl ? R.layout.wl_date_rtl : R.layout.wl_date_ltr;
    }

    @Override
    int caps() {
        return TEXT | ACCENT;
    }

    @Override
    int defaultAccent(WCfg cfg) {
        return cfg.adaptive() ? 0 : 0xFFFF3B30;
    }

    @Override
    int[] primary() {
        return new int[]{R.id.wP1};
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
    void fill(Context c, RemoteViews rv, WCfg cfg) {
        long now = System.currentTimeMillis();
        rv.setTextViewText(R.id.wA1, Fmt.date(c, now, "EEEE"));
        rv.setTextViewText(R.id.wP1, Fmt.date(c, now, "d"));
        rv.setTextViewText(R.id.wS1, Fmt.date(c, now, "MMMM yyyy"));
    }
}
