package com.clock.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Read-only summary for the other Semo apps (the calendar shows it in "today at a glance"): only the time of the next
 * alarm and the next prayer with its name; never alarm labels, tasks or locations. Rows are (key, value) pairs:
 * next_alarm (millis, 0 none), next_prayer_at (millis, 0 none), next_prayer_name.
 */
public final class SemoProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{"key", "value"});
        try {
            Context ctx = getContext();
            if (ctx == null) return c;
            c.addRow(new Object[]{"next_alarm", String.valueOf(Alarms.nextTrigger(ctx))});
            long at = 0;
            String name = "";
            PrayerTimes.Next n = PrayerTimes.next(ctx);
            if (n != null) {
                String[] names = ctx.getResources().getStringArray(R.array.prayer_names);
                at = n.at;
                if (n.idx >= 0 && n.idx < names.length) name = names[n.idx];
            }
            c.addRow(new Object[]{"next_prayer_at", String.valueOf(at)});
            c.addRow(new Object[]{"next_prayer_name", name});
        } catch (Throwable ignored) {
        }
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
