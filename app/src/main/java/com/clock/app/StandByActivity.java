package com.clock.app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.lang.ref.WeakReference;

/**
 * Full-screen desk clock (iPhone StandBy). Keeps the screen on, can sit above the lock screen, dims itself in the dark.
 * Started by hand or automatically by StandByService while charging.
 */
public class StandByActivity extends BaseActivity implements SensorEventListener {
    private static WeakReference<StandByActivity> current = new WeakReference<>(null);
    private StandByView view;
    private boolean auto;
    private SensorManager sm;
    private boolean dark;

    static boolean visible() {
        StandByActivity a = current.get();
        return a != null && !a.isFinishing() && !a.isDestroyed();
    }

    /** Closes the screen if it was raised automatically (charger removed). */
    static void closeAuto() {
        StandByActivity a = current.get();
        if (a != null && a.auto && !a.isFinishing()) a.finish();
    }

    @Override
    protected int transition() {
        return ZOOM;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = new WeakReference<>(this);
        auto = getIntent().getBooleanExtra(StandByService.EXTRA_AUTO, false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Store.standbyLock(this)) {
            try {
                if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
                else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            } catch (Throwable ignored) {
            }
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        try {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        } catch (Throwable ignored) {
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        view = new StandByView(this);
        view.setStyle(Store.standbyStyle(this));
        applyBrightness();
        view.setOnStyleChanged(s -> {
            Store.setStandbyStyle(this, s);
            applyBrightness();
        });
        view.setOnClose(this::finish);
        setContentView(view);
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(4102);
        } catch (Throwable ignored) {
        }
        if (auto && Store.standbyLandscape(this)
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            view.post(this::finishIfPortrait);
        }
    }

    private void finishIfPortrait() {
        if (auto && Store.standbyLandscape(this)
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) finish();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        finishIfPortrait();
    }

    private void applyBrightness() {
        boolean night = view.style() == StandByView.NIGHT || dark;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = night ? 0.02f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
    }

    private void hideBars() {
        try {
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsetsCompat.Type.systemBars());
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        if (Store.standbyAutoNight(this)) {
            sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        if (sm != null) sm.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (current.get() == this) current = new WeakReference<>(null);
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float lux = e.values[0];
        boolean now = dark ? lux < 12f : lux < 3f;
        if (now != dark) {
            dark = now;
            view.setDark(dark);
            applyBrightness();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }
}
