package com.clock.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Semo ecosystem glue (same contract as Gallery, Music, Files and Keyboard): every Semo app declares the
 * app.semo.ECOSYSTEM activity filter plus the app.semo.ROLE meta-data, so the apps can find each other.
 */
final class Semo {
    private Semo() {
    }

    static final String ACTION_ECO = "app.semo.ECOSYSTEM";
    static final String META_ROLE = "app.semo.ROLE";

    static final class Sibling {
        String pkg = "";
        String role = "";
        CharSequence label = "";
    }

    static List<Sibling> siblings(Context c) {
        List<Sibling> out = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            Intent probe = new Intent(ACTION_ECO).addCategory(Intent.CATEGORY_DEFAULT);
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
                if (ri.activityInfo == null) continue;
                String p = ri.activityInfo.packageName;
                if (p == null || p.equals(c.getPackageName()) || !seen.add(p)) continue;
                Sibling s = new Sibling();
                s.pkg = p;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(p, PackageManager.GET_META_DATA);
                    s.label = pm.getApplicationLabel(ai);
                    Bundle md = ai.metaData;
                    if (md != null && md.getString(META_ROLE) != null) s.role = md.getString(META_ROLE);
                } catch (Exception ignored) {
                }
                out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static boolean open(Context c, String pkg) {
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
