package com.clock.app;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Base of every screen: keeps the system bar icons readable in day and night mode. */
public abstract class BaseActivity extends AppCompatActivity {
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
}
