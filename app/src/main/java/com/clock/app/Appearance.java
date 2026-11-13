package com.clock.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/** Light (white, grouped grey), Dark (true AMOLED black) or Auto, exactly like the Gallery app. */
public final class Appearance {
    private Appearance() {
    }

    public static final String LIGHT = "light", DARK = "dark", AUTO = "auto";

    public static void applyMode(Context c) {
        String m = Store.themeMode(c);
        int mode = DARK.equals(m) ? AppCompatDelegate.MODE_NIGHT_YES
                : LIGHT.equals(m) ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static int modeName(String mode) {
        if (DARK.equals(mode)) return R.string.theme_dark;
        if (LIGHT.equals(mode)) return R.string.theme_light;
        return R.string.theme_auto;
    }

    public static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void recreateAll() {
        for (Activity a : App.openActivities()) {
            try {
                a.recreate();
            } catch (Throwable ignored) {
            }
        }
    }
}
