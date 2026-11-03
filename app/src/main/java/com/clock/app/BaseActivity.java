package com.clock.app;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import eightbitlab.com.blurview.BlurTarget;

/**
 * Base of every screen: keeps the system bar icons readable in day and night mode, picks the iOS transition and
 * hosts the Liquid Glass layers. The screen's own root goes into a BlurTarget on top of a living backdrop; floating
 * glass (tab bar, menus, toasts) sits in an overlay above it and blurs what is underneath, live.
 */
public abstract class BaseActivity extends AppCompatActivity {
    /** Screen transitions: push (default), bottom sheet, soft zoom. */
    static final int PUSH = 0, SHEET = 1, ZOOM = 2;

    private BlurTarget target;
    private FrameLayout overlay;
    private boolean hosted;
    private int insetTop, insetBottom, reserved;
    private Runnable onInsets;
    private View tiltView;
    private float tx, ty;
    private final SensorEventListener tilt = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            onTilt(e);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    protected int transition() {
        return PUSH;
    }

    /** False for screens that must stay exactly as drawn (the alarm ring screen, StandBy, the Quran pages). */
    protected boolean glassScreen() {
        return true;
    }

    /** 0 keeps the theme from the manifest; otherwise the style to apply before the window is created. */
    protected int themeOverride() {
        return glassScreen() && Glass.enabled(this) ? 0 : R.style.AppTheme_Solid;
    }

    BlurTarget glassTarget() {
        return Glass.enabled(this) ? target : null;
    }

    FrameLayout glassOverlay() {
        return overlay;
    }

    boolean glassHosted() {
        return hosted;
    }

    int glassInsetTop() {
        return insetTop;
    }

    /** Room taken at the bottom by the system bar and by a floating tab bar, for toasts and pills. */
    int glassBottom() {
        return insetBottom + reserved;
    }

    void setGlassReserved(int px) {
        reserved = px;
    }

    void setOnGlassInsets(Runnable r) {
        onInsets = r;
    }

    int glassInsetBottom() {
        return insetBottom;
    }

    void setTiltView(View v) {
        tiltView = v;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int t = themeOverride();
        if (t != 0) setTheme(t);
        super.onCreate(savedInstanceState);
    }

    /** iOS inset-grouped screens sit on grey in day mode; the window follows so no white strip shows. */
    protected void useGroupedBackground() {
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.color(this, R.color.bg_group)));
    }

    @Override
    public void setContentView(View view) {
        if (!glassScreen()) {
            super.setContentView(view);
            return;
        }
        hosted = true;
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.color(this, R.color.bg_group)));
        FrameLayout host = new FrameLayout(this);
        target = new BlurTarget(this);
        if (Glass.enabled(this)) target.addView(new AuroraView(this), new FrameLayout.LayoutParams(-1, -1));
        final FrameLayout holder = new FrameLayout(this);
        holder.addView(view, new FrameLayout.LayoutParams(-1, -1));
        target.addView(holder, new FrameLayout.LayoutParams(-1, -1));
        host.addView(target, new FrameLayout.LayoutParams(-1, -1));
        overlay = new FrameLayout(this);
        overlay.setClipChildren(false);
        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 35) {
            ViewCompat.setOnApplyWindowInsetsListener(host, (v, insets) -> {
                Insets all = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                holder.setPadding(all.left, all.top, all.right, all.bottom);
                overlay.setPadding(bars.left, 0, bars.right, 0);
                insetTop = bars.top;
                insetBottom = bars.bottom;
                if (onInsets != null) onInsets.run();
                return WindowInsetsCompat.CONSUMED;
            });
            ViewCompat.requestApplyInsets(host);
        }
        super.setContentView(host);
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

    // ------------------------------------------------------------------ tilt: the light on the glass follows the phone

    @Override
    protected void onResume() {
        super.onResume();
        if (hosted && Glass.enabled(this)) {
            try {
                SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (s != null) sm.registerListener(tilt, s, SensorManager.SENSOR_DELAY_UI);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        try {
            SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) sm.unregisterListener(tilt);
        } catch (Throwable ignored) {
        }
        super.onPause();
    }

    private void onTilt(SensorEvent e) {
        if (e.values == null || e.values.length < 2) return;
        float x = Math.max(-1f, Math.min(1f, -e.values[0] / 9.8f));
        float y = Math.max(-1f, Math.min(1f, e.values[1] / 9.8f));
        tx += (x - tx) * 0.12f;
        ty += (y - ty) * 0.12f;
        if (Math.abs(tx - Glass.tiltX) > 0.025f || Math.abs(ty - Glass.tiltY) > 0.025f) {
            Glass.tiltX = tx;
            Glass.tiltY = ty;
            if (tiltView != null) tiltView.invalidate();
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
