package com.clock.app;

import android.content.Context;
import android.widget.RemoteViews;

public class WidgetAnalog extends WBase {
    @Override
    int layout(boolean rtl, WCfg cfg) {
        return cfg.face == 1 ? R.layout.wl_analog_w : cfg.face == 2 ? R.layout.wl_analog_b : R.layout.wl_analog_a;
    }

    @Override
    int caps() {
        return FACE;
    }

    @Override
    void fill(Context c, RemoteViews rv, WCfg cfg) {
    }
}
