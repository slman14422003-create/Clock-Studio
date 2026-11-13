package com.clock.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Builds picker wheel groups with the shared grey selection capsule behind the middle row. */
final class Wheels {
    private Wheels() {
    }

    static String[] range(int from, int to, boolean pad) {
        String[] s = new String[to - from + 1];
        for (int i = 0; i < s.length; i++) s[i] = pad ? Fmt.two(from + i) : String.valueOf(from + i);
        return s;
    }

    static FrameLayout group(Context c, WheelView... wheels) {
        FrameLayout f = new FrameLayout(c);
        View cap = new View(c);
        cap.setBackground(Ui.rect(Ui.color(c, R.color.neutral_soft), Ui.dpf(c, 10)));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, Ui.dp(c, 40), Gravity.CENTER_VERTICAL);
        cl.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        f.addView(cap, cl);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER);
        for (WheelView w : wheels) row.addView(w, new LinearLayout.LayoutParams(0, Ui.dp(c, 200), 1f));
        f.addView(row, new FrameLayout.LayoutParams(-1, Ui.dp(c, 200)));
        return f;
    }
}
