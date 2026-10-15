package com.clock.app;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

/** Edge-to-edge insets for every screen (Android 15+), notification channels and appearance. */
public class App extends Application {
    private static final List<Activity> OPEN = new ArrayList<>();

    static List<Activity> openActivities() {
        synchronized (OPEN) {
            return new ArrayList<>(OPEN);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Lang.init(this);
        Appearance.applyMode(this);
        Notif.init(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity a, Bundle b) {
                synchronized (OPEN) {
                    OPEN.add(a);
                }
            }

            @Override
            public void onActivityStarted(Activity a) {
                if (Build.VERSION.SDK_INT < 35) return;
                View content = a.findViewById(android.R.id.content);
                if (content == null || content.getTag(R.id.tag_insets) != null) return;
                content.setTag(R.id.tag_insets, Boolean.TRUE);
                ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                    Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
                    v.setPadding(b.left, b.top, b.right, b.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
                ViewCompat.requestApplyInsets(content);
            }

            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }

            @Override
            public void onActivityDestroyed(Activity a) {
                synchronized (OPEN) {
                    OPEN.remove(a);
                }
            }
        });
    }
}
