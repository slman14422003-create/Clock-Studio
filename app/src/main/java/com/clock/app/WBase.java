package com.clock.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.Locale;

abstract class WBase extends AppWidgetProvider {
    static final String REFRESH = "com.clock.app.WIDGET_REFRESH";
    static final int TEXT = 1, ACCENT = 2, SIZE = 4, FMT = 8, DATE = 16, FACE = 32;
    static final Class<?>[] ALL = {ClockWidget.class, WidgetDigital.class, WidgetStack.class, WidgetAnalog.class,
            WidgetDate.class, WidgetPrayer.class, WidgetPrayers.class, WidgetAlarm.class};

    abstract int layout(boolean rtl, WCfg cfg);

    abstract void fill(Context c, RemoteViews rv, WCfg cfg);

    int defaultBg() {
        return 0;
    }

    int caps() {
        return TEXT | ACCENT | SIZE | FMT | DATE;
    }

    int[] primary() {
        return new int[0];
    }

    int[] secondary() {
        return new int[0];
    }

    int[] accents() {
        return new int[0];
    }

    int[][] sizes() {
        return new int[0][];
    }

    int defaultAccent(WCfg cfg) {
        return cfg.adaptive() ? 0 : cfg.autoText();
    }

    Intent target(Context c) {
        return new Intent(c, MainActivity.class);
    }

    static Context localized(Context c) {
        try {
            Configuration conf = new Configuration(c.getResources().getConfiguration());
            conf.setLocale(Lang.isAr() ? new Locale("ar") : Locale.ENGLISH);
            return c.createConfigurationContext(conf);
        } catch (Exception e) {
            return c;
        }
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            try {
                m.updateAppWidget(id, build(c, id));
            } catch (Exception ignored) {
            }
        }
        scheduleTicks(c);
    }

    @Override
    public void onEnabled(Context c) {
        scheduleTicks(c);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) WCfg.delete(c, id);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i == null ? null : i.getAction();
        if (REFRESH.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                || Intent.ACTION_LOCALE_CHANGED.equals(a)) refreshAll(c);
    }

    RemoteViews build(Context app, int id) {
        Context c = localized(app);
        WCfg cfg = WCfg.load(app, id, defaultBg());
        RemoteViews rv = new RemoteViews(app.getPackageName(), layout(Lang.isAr(), cfg));
        applyBg(rv, cfg);
        fill(c, rv, cfg);
        applyColors(c, rv, cfg);
        post(c, rv, cfg);
        PendingIntent open = PendingIntent.getActivity(app, 30 + (getClass().getSimpleName().hashCode() & 0xFFF), target(app),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.wRoot, open);
        return rv;
    }

    void applyBg(RemoteViews rv, WCfg cfg) {
        int r = cfg.radius;
        rv.setImageViewResource(R.id.wBg, WRes.bg(cfg.bg, r));
        rv.setInt(R.id.wBg, "setColorFilter", cfg.bg > WCfg.GRAD_LAST ? cfg.bgColor() : 0);
        rv.setInt(R.id.wBg, "setImageAlpha", cfg.alpha * 255 / 100);
    }

    void applyColors(Context c, RemoteViews rv, WCfg cfg) {
        int main = cfg.text != 0 ? WCfg.PALETTE[cfg.text] : cfg.autoText();
        boolean setMain = main != 0;
        int sec = setMain ? (main & 0x00FFFFFF) | 0xB3000000 : 0;
        for (int id : primary()) if (setMain) rv.setTextColor(id, main);
        for (int id : secondary()) if (setMain) rv.setTextColor(id, sec);
        int ac = cfg.accent != 0 ? WCfg.PALETTE[cfg.accent] : defaultAccent(cfg);
        if (ac != 0) for (int id : accents()) rv.setTextColor(id, ac);
        if (setMain) tint(rv, sec);
        if (ac != 0) tintAccent(rv, ac);
        float s = cfg.scale();
        if ((caps() & SIZE) != 0 && s != 1f) {
            for (int[] t : sizes()) rv.setTextViewTextSize(t[0], TypedValue.COMPLEX_UNIT_SP, t[1] * s);
        }
    }

    void post(Context c, RemoteViews rv, WCfg cfg) {
    }

    void tint(RemoteViews rv, int color) {
    }

    void tintAccent(RemoteViews rv, int color) {
    }

    static void timeFormat(RemoteViews rv, int id, WCfg cfg, Context c, String p12, String p24) {
        boolean is24 = cfg.fmt == 0 ? Fmt.is24(c) : cfg.fmt == 2;
        String p = is24 ? p24 : p12;
        rv.setCharSequence(id, "setFormat12Hour", p);
        rv.setCharSequence(id, "setFormat24Hour", p);
    }

    static boolean is24(Context c, WCfg cfg) {
        return cfg.fmt == 0 ? Fmt.is24(c) : cfg.fmt == 2;
    }

    static String ampm(Context c, WCfg cfg) {
        if (is24(c, cfg)) return "";
        return c.getString(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12 ? R.string.am : R.string.pm);
    }

    static void refresh(Context c, Class<?> cls) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
            if (ids == null || ids.length == 0) return;
            WBase w = (WBase) cls.getDeclaredConstructor().newInstance();
            for (int id : ids) {
                try {
                    m.updateAppWidget(id, w.build(c, id));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    static void refreshOne(Context c, int id) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            String cn = m.getAppWidgetInfo(id).provider.getClassName();
            WBase w = (WBase) Class.forName(cn).getDeclaredConstructor().newInstance();
            m.updateAppWidget(id, w.build(c, id));
        } catch (Exception ignored) {
        }
    }

    static void refreshAll(Context c) {
        for (Class<?> k : ALL) refresh(c, k);
        scheduleTicks(c);
    }

    static boolean any(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            for (Class<?> k : ALL) {
                int[] ids = m.getAppWidgetIds(new ComponentName(c, k));
                if (ids != null && ids.length > 0) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static void scheduleTicks(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Calendar cal = Calendar.getInstance();
            int h = cal.get(Calendar.HOUR_OF_DAY);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 2);
            cal.set(Calendar.MILLISECOND, 0);
            cal.set(Calendar.HOUR_OF_DAY, h < 12 ? 12 : 0);
            if (h >= 12) cal.add(Calendar.DAY_OF_YEAR, 1);
            long at = cal.getTimeInMillis();
            PendingIntent day = PendingIntent.getBroadcast(c, 78, new Intent(c, WidgetDate.class).setAction(REFRESH),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            am.setAndAllowWhileIdle(AlarmManager.RTC, at, day);
            PrayerTimes.Next n = PrayerTimes.next(c);
            if (n != null) {
                PendingIntent pi = PendingIntent.getBroadcast(c, 77, new Intent(c, WidgetPrayer.class).setAction(REFRESH),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                am.setAndAllowWhileIdle(AlarmManager.RTC, n.at + 1500, pi);
            }
        } catch (Exception ignored) {
        }
    }
}
