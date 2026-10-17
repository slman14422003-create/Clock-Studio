package com.clock.app;

import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.app.NotificationManagerCompat;

import java.util.List;

/** Inset-grouped iOS settings: appearance, language, time format, alarm defaults, permissions, Semo apps, about. */
public class SettingsActivity extends BaseActivity {
    private LinearLayout col;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.nav_settings));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        root.addView(hd);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        hd.bindScroll(sv);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.dp(this, 30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        col.removeAllViews();

        col.addView(Ui.sectionTitle(this, getString(R.string.sec_look)));
        LinearLayout g = Ui.group(this);
        final String[] modes = {Appearance.AUTO, Appearance.LIGHT, Appearance.DARK};
        Ui.addRow(g, Ui.row(this, R.drawable.ic_palette, R.color.accent, getString(R.string.appearance),
                getString(Appearance.modeName(Store.themeMode(this))), true, v -> {
                    String[] labels = {getString(R.string.theme_auto), getString(R.string.theme_light), getString(R.string.theme_dark)};
                    int cur = 0;
                    for (int i = 0; i < modes.length; i++) if (modes[i].equals(Store.themeMode(this))) cur = i;
                    Dlg.choose(this, R.string.appearance, labels, cur, i -> {
                        Store.setThemeMode(this, modes[i]);
                        Appearance.applyMode(this);
                        build();
                    });
                }), true);
        final String[] langs = {"system", "ar", "en"};
        Ui.addRow(g, Ui.row(this, R.drawable.ic_language, R.color.violet, getString(R.string.language),
                langName(Store.language(this)), true, v -> {
                    String[] labels = {getString(R.string.lang_system), "العربية", "English"};
                    int cur = 0;
                    for (int i = 0; i < langs.length; i++) if (langs[i].equals(Store.language(this))) cur = i;
                    Dlg.choose(this, R.string.language, labels, cur, i -> {
                        Store.setLanguage(this, langs[i]);
                        Lang.apply(this);
                    });
                }), true);
        Ui.addRow(g, Ui.switchRow(this, R.drawable.ic_vibrate, R.color.ok, getString(R.string.haptics), null,
                Store.haptics(this), (v, on) -> Store.setHaptics(this, on)), true);
        col.addView(g);

        LinearLayout gai = Ui.group(this);
        Ui.addRow(gai, Ui.row(this, R.drawable.ic_brain, R.color.violet, getString(R.string.assistant),
                getString(R.string.on), true, v -> AssistantActivity.open(this, false)), true);
        Ui.addRow(gai, Ui.row(this, R.drawable.ic_prayer, R.color.ok, getString(R.string.prayer_times), null, true,
                v -> startActivity(new Intent(this, PrayerActivity.class))), true);
        col.addView(gai);

        col.addView(Ui.sectionTitle(this, getString(R.string.sec_time)));
        LinearLayout g2 = Ui.group(this);
        Ui.addRow(g2, Ui.row(this, R.drawable.ic_clock, R.color.info, getString(R.string.time_format), timeFormatName(), true, v -> {
            String[] labels = {getString(R.string.fmt_auto), getString(R.string.fmt_12), getString(R.string.fmt_24)};
            Dlg.choose(this, R.string.time_format, labels, Store.timeFormat(this), i -> {
                Store.setTimeFormat(this, i);
                build();
            });
        }), true);
        Ui.addRow(g2, Ui.switchRow(this, R.drawable.ic_timer, R.color.warn, getString(R.string.show_seconds), null,
                Store.showSeconds(this), (v, on) -> Store.setShowSeconds(this, on)), true);
        col.addView(g2);

        col.addView(Ui.sectionTitle(this, getString(R.string.sec_alarms)));
        LinearLayout g3 = Ui.group(this);
        final int[] snoozes = {5, 9, 10, 15, 20};
        Ui.addRow(g3, Ui.row(this, R.drawable.ic_repeat, R.color.warn, getString(R.string.snooze_length),
                Fmt.spoken(this, Store.snoozeMin(this) * 60000L), true, v -> {
                    String[] labels = new String[snoozes.length];
                    int cur = 0;
                    for (int i = 0; i < snoozes.length; i++) {
                        labels[i] = Fmt.spoken(this, snoozes[i] * 60000L);
                        if (snoozes[i] == Store.snoozeMin(this)) cur = i;
                    }
                    Dlg.choose(this, R.string.snooze_length, labels, cur, i -> {
                        Store.setSnoozeMin(this, snoozes[i]);
                        build();
                    });
                }), true);
        final int[] silences = {1, 5, 10, 15};
        Ui.addRow(g3, Ui.row(this, R.drawable.ic_volume, R.color.bad, getString(R.string.silence_after),
                Fmt.spoken(this, Store.silenceMin(this) * 60000L), true, v -> {
                    String[] labels = new String[silences.length];
                    int cur = 0;
                    for (int i = 0; i < silences.length; i++) {
                        labels[i] = Fmt.spoken(this, silences[i] * 60000L);
                        if (silences[i] == Store.silenceMin(this)) cur = i;
                    }
                    Dlg.choose(this, R.string.silence_after, labels, cur, i -> {
                        Store.setSilenceMin(this, silences[i]);
                        build();
                    });
                }), true);
        Ui.addRow(g3, Ui.switchRow(this, R.drawable.ic_vibrate, R.color.ok, getString(R.string.vibrate_default), null,
                Store.defaultVibrate(this), (v, on) -> Store.setDefaultVibrate(this, on)), true);
        Ui.addRow(g3, Ui.switchRow(this, R.drawable.ic_sound, R.color.accent, getString(R.string.ramp_default), null,
                Store.defaultRamp(this), (v, on) -> Store.setDefaultRamp(this, on)), true);
        col.addView(g3);

        col.addView(Ui.sectionTitle(this, getString(R.string.sec_standby)));
        LinearLayout gsb = Ui.group(this);
        final String[] sbNames = {getString(R.string.standby_digital), getString(R.string.standby_analog), getString(R.string.standby_night)};
        Ui.addRow(gsb, Ui.row(this, R.drawable.ic_standby, R.color.indigo, getString(R.string.standby_style),
                sbNames[Store.standbyStyle(this)], true, v -> Dlg.choose(this, R.string.standby_style, sbNames,
                        Store.standbyStyle(this), i -> {
                            Store.setStandbyStyle(this, i);
                            build();
                        })), true);
        Ui.addRow(gsb, Ui.row(this, R.drawable.ic_clock, R.color.info, getString(R.string.standby), null, true,
                v -> Ui.openZoom(this, new Intent(this, StandByActivity.class))), true);
        col.addView(gsb);

        col.addView(Ui.sectionTitle(this, getString(R.string.sec_permissions)));
        LinearLayout g4 = Ui.group(this);
        boolean notif = NotificationManagerCompat.from(this).areNotificationsEnabled();
        Ui.addRow(g4, Ui.row(this, R.drawable.ic_bell, R.color.bad, getString(R.string.notifications),
                getString(notif ? R.string.on : R.string.off), true, v -> openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)), true);
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            boolean fs = nm != null && nm.canUseFullScreenIntent();
            Ui.addRow(g4, Ui.row(this, R.drawable.ic_lock, R.color.info, getString(R.string.full_screen),
                    getString(fs ? R.string.on : R.string.off), true, v -> openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)), true);
        }
        col.addView(g4);
        col.addView(Ui.footnote(this, getString(R.string.permissions_note)));

        List<Semo.Sibling> sibs = Semo.siblings(this);
        if (!sibs.isEmpty()) {
            col.addView(Ui.sectionTitle(this, getString(R.string.sec_semo)));
            LinearLayout gs = Ui.group(this);
            for (final Semo.Sibling s : sibs) {
                Ui.addRow(gs, Ui.row(this, R.drawable.ic_shield, R.color.accent, String.valueOf(s.label), null, true,
                        v -> Semo.open(this, s.pkg)), true);
            }
            col.addView(gs);
        }

        col.addView(Ui.sectionTitle(this, getString(R.string.sec_about)));
        LinearLayout g5 = Ui.group(this);
        String ver = "";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName == null ? "" : pi.versionName;
        } catch (Exception ignored) {
        }
        LinearLayout about = Ui.row(this, 0, 0, getString(R.string.app_name), ver, false, null);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_foreground);
        logo.setBackground(Ui.rect(Ui.color(this, R.color.accent_soft), Ui.dpf(this, 9)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        lp.setMarginEnd(Ui.dp(this, 12));
        about.addView(logo, 0, lp);
        about.setGravity(Gravity.CENTER_VERTICAL);
        Ui.addRow(g5, about, true);
        col.addView(g5);
    }

    private String langName(String v) {
        if ("ar".equals(v)) return "العربية";
        if ("en".equals(v)) return "English";
        return getString(R.string.lang_system);
    }

    private String timeFormatName() {
        int f = Store.timeFormat(this);
        return getString(f == 1 ? R.string.fmt_12 : f == 2 ? R.string.fmt_24 : R.string.fmt_auto);
    }

    private void openSettings(String action) {
        try {
            Intent i = new Intent(action);
            if (Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(action)) {
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                i.setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }
}
