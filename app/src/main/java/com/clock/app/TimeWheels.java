package com.clock.app;

import android.content.Context;
import android.widget.FrameLayout;

/** Hour / minute (/ AM-PM) picker wheels that follow the 12 / 24 hour setting. */
final class TimeWheels {
    final FrameLayout view;
    private final WheelView hour, min, ap;
    private final boolean is24;

    TimeWheels(Context c, int hour24, int minute) {
        is24 = Fmt.is24(c);
        hour = new WheelView(c);
        min = new WheelView(c);
        hour.setLoop(true);
        min.setLoop(true);
        min.setItems(Wheels.range(0, 59, true));
        if (is24) {
            hour.setItems(Wheels.range(0, 23, true));
            hour.setIndex(hour24);
            ap = null;
            min.setIndex(minute);
            view = Wheels.group(c, hour, min);
        } else {
            hour.setItems(Wheels.range(1, 12, false));
            hour.setIndex((hour24 + 11) % 12);
            ap = new WheelView(c);
            ap.setItems(new String[]{c.getString(R.string.am), c.getString(R.string.pm)});
            ap.setIndex(hour24 >= 12 ? 1 : 0);
            min.setIndex(minute);
            view = Wheels.group(c, hour, min, ap);
        }
    }

    /** Called whenever any wheel settles on a new row. */
    void setOnChange(Runnable r) {
        WheelView.Listener l = i -> r.run();
        hour.setListener(l);
        min.setListener(l);
        if (ap != null) ap.setListener(l);
    }

    int hour() {
        if (is24) return hour.getIndex();
        int h12 = hour.getIndex() + 1;
        return (h12 % 12) + (ap.getIndex() == 1 ? 12 : 0);
    }

    int minute() {
        return min.getIndex();
    }
}
