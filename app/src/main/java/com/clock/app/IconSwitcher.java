package com.clock.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Launcher icon variants: one activity-alias per colour, exactly one enabled at a time. */
final class IconSwitcher {
    private IconSwitcher() {
    }

    static final String[] IDS = {"default", "violet", "green", "orange", "red", "pink", "graphite", "midnight", "gold"};
    static final int[] NAMES = {R.string.icon_default, R.string.icon_violet, R.string.icon_green, R.string.icon_orange,
            R.string.icon_red, R.string.icon_pink, R.string.icon_graphite, R.string.icon_midnight, R.string.icon_gold};
    static final int[] PREVIEWS = {R.mipmap.ic_launcher, R.mipmap.ic_launcher_violet, R.mipmap.ic_launcher_green,
            R.mipmap.ic_launcher_orange, R.mipmap.ic_launcher_red, R.mipmap.ic_launcher_pink,
            R.mipmap.ic_launcher_graphite, R.mipmap.ic_launcher_midnight, R.mipmap.ic_launcher_gold};

    private static ComponentName name(Context c, int i) {
        String id = IDS[i];
        return new ComponentName(c.getPackageName(), "com.clock.app.Icon" + Character.toUpperCase(id.charAt(0)) + id.substring(1));
    }

    static int current(Context c) {
        PackageManager pm = c.getPackageManager();
        for (int i = 0; i < IDS.length; i++) {
            try {
                int s = pm.getComponentEnabledSetting(name(c, i));
                if (s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        || (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0)) return i;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static void apply(Context c, int idx) {
        if (idx < 0 || idx >= IDS.length || idx == current(c)) return;
        PackageManager pm = c.getPackageManager();
        try {
            pm.setComponentEnabledSetting(name(c, idx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (int i = 0; i < IDS.length; i++) {
                if (i != idx) {
                    pm.setComponentEnabledSetting(name(c, i), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
