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

    static final String ROLE_CALENDAR = "calendar";
    static final String ACTION_ADD_EVENT = "app.semo.calendar.ADD_EVENT";

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

    static Sibling byRole(Context c, String role) {
        for (Sibling s : siblings(c)) if (role.equals(s.role)) return s;
        return null;
    }

    static boolean hasCalendar(Context c) {
        return byRole(c, ROLE_CALENDAR) != null;
    }

    static final class CalSummary {
        long nextEvent;
        int today;
    }

    /** The calendar's read-only summary (time of the next event, number of today's events); null when unavailable. */
    static CalSummary calendarSummary(Context c) {
        Sibling s = byRole(c, ROLE_CALENDAR);
        if (s == null) return null;
        android.database.Cursor cur = null;
        try {
            cur = c.getContentResolver().query(android.net.Uri.parse("content://" + s.pkg + ".semo/summary"), null, null, null, null);
            if (cur == null) return null;
            CalSummary out = new CalSummary();
            while (cur.moveToNext()) {
                String k = cur.getString(0), v = cur.getString(1);
                if (k == null || v == null) continue;
                try {
                    if (k.equals("next_event")) out.nextEvent = Long.parseLong(v);
                    else if (k.equals("events_today")) out.today = Integer.parseInt(v);
                } catch (NumberFormatException ignored) {
                }
            }
            return out;
        } catch (Throwable e) {
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    /** Opens the calendar's new-event editor prefilled; nothing is saved until the person presses Save there. */
    static boolean addToCalendar(Context c, String title, String notes, long start, long end, boolean allDay) {
        Sibling s = byRole(c, ROLE_CALENDAR);
        if (s == null) return false;
        try {
            Intent i = new Intent(ACTION_ADD_EVENT).setPackage(s.pkg).putExtra("title", title == null ? "" : title)
                    .putExtra("start", start).putExtra("end", end).putExtra("allday", allDay)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (notes != null && !notes.isEmpty()) i.putExtra("notes", notes);
            c.startActivity(i);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
