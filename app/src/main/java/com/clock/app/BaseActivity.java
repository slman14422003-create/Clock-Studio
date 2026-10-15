package com.clock.app;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Base of every screen: keeps the system bar icons readable in day and night mode and picks the iOS transition. */
public abstract class BaseActivity extends AppCompatActivity {
    /** Screen transitions: push (default), bottom sheet, soft zoom. */
    static final int PUSH = 0, SHEET = 1, ZOOM = 2;

    protected int transition() {
        return PUSH;
    }

    /** iOS inset-grouped screens sit on grey in day mode; the window follows so no white strip shows. */
    protected void useGroupedBackground() {
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.color(this, R.color.bg_group)));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        try {
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            boolean light = !Appearance.isNight(this);
            c.setAppearanceLightStatusBars(light);
            c.setAppearanceLightNavigationBars(light);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void finish() {
        super.finish();
        int k = transition();
        if (k == SHEET) overridePendingTransition(R.anim.sheet_close_enter, R.anim.sheet_close_exit);
        else if (k == ZOOM) overridePendingTransition(R.anim.hold_close, R.anim.viewer_close_exit);
    }
}
