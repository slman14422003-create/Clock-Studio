package com.clock.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

abstract class WBase extends AppWidgetProvider {
    static final String REFRESH = "com.clock.app.WIDGET_REFRESH";

    abstract int layout();

    void fill(Context c, RemoteViews rv) {
    }

    Intent target(Context c) {
        return new Intent(c, MainActivity.class);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i == null ? null : i.getAction();
        if (REFRESH.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) refresh(c, getClass());
    }

    RemoteViews build(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), layout());
        fill(c, rv);
        PendingIntent open = PendingIntent.getActivity(c, 30 + layout() % 50, target(c),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.wRoot, open);
        return rv;
    }

    static void refresh(Context c, Class<?> cls) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
            if (ids == null || ids.length == 0) return;
            WBase w = (WBase) cls.getDeclaredConstructor().newInstance();
            for (int id : ids) m.updateAppWidget(id, w.build(c));
        } catch (Exception ignored) {
        }
    }

    static void refreshAll(Context c) {
        ClockWidget.refresh(c);
        refresh(c, WidgetPrayer.class);
        refresh(c, WidgetAlarm.class);
    }
}
