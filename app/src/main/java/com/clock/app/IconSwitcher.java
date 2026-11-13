package com.clock.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** The launcher icon has one light and one dark look that follow the system; old colour aliases are switched off. */
final class IconSwitcher {
    private IconSwitcher() {
    }

    private static final String[] OLD = {"Violet", "Green", "Orange", "Red", "Pink", "Graphite", "Midnight", "Gold"};

    static void normalize(Context c) {
        PackageManager pm = c.getPackageManager();
        try {
            ComponentName def = new ComponentName(c.getPackageName(), "com.clock.app.IconDefault");
            if (pm.getComponentEnabledSetting(def) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(def, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }
            for (String n : OLD) {
                ComponentName cn = new ComponentName(c.getPackageName(), "com.clock.app.Icon" + n);
                int s = pm.getComponentEnabledSetting(cn);
                if (s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
