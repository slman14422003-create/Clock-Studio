package com.clock.app;

import android.Manifest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Prayer times for the user's region, downloaded from Aladhan and cached; method, Asr school and reminders. */
public class PrayerActivity extends BaseActivity {
    private LinearLayout col;
    private TextView nextCount;
    private long nextAt;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (nextCount != null && nextAt > 0) {
                long left = nextAt - System.currentTimeMillis();
                if (left <= 0) {
                    build();
                } else {
                    nextCount.setText(Fmt.countdown(left));
                }
            }
            ui.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };

    private final ActivityResultLauncher<String> perm = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) locateAuto();
                else Ui.toast(this, getString(R.string.loc_failed));
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.prayer_times));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        hd.addButton(R.drawable.ic_refresh, R.string.prayer_refresh, false, v -> refreshNow());
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
        PrayerTimes.autoRefresh(this, ok -> {
            if (!isFinishing() && !isDestroyed()) build();
        });
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    private void refreshNow() {
        if (!PrayerTimes.cfg(this).configured) {
            pickLocation();
            return;
        }
        Ui.toast(this, getString(R.string.prayer_updating));
        PrayerTimes.refresh(this, true, ok -> {
            if (isFinishing() || isDestroyed()) return;
            Ui.toast(this, getString(ok ? R.string.prayer_updated_ok : R.string.prayer_refresh_fail));
            build();
        });
    }

    private void build() {
        col.removeAllViews();
        nextCount = null;
        nextAt = 0;
        final PrayerTimes.Cfg g = PrayerTimes.cfg(this);
        String[] names = getResources().getStringArray(R.array.prayer_names);

        PrayerTimes.Day today = g.configured ? PrayerTimes.day(this, g, PrayerTimes.today(g)) : null;
        if (g.configured && today != null) {
            PrayerTimes.Next next = PrayerTimes.next(this);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackground(Ui.rect(Ui.color(this, R.color.field), Ui.dpf(this, 18)));
            card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
            TextView place = Ui.text(this, g.place, 15, R.color.text_secondary);
            place.setGravity(Gravity.CENTER);
            card.addView(place);
            if (next != null) {
                TextView cap = Ui.text(this, getString(R.string.next_prayer), 13, R.color.text_secondary);
                cap.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
                cl.topMargin = Ui.dp(this, 10);
                card.addView(cap, cl);
                TextView nm = Ui.text(this, names[next.idx], 26, R.color.text_primary);
                nm.setTypeface(Ui.medium());
                nm.setGravity(Gravity.CENTER);
                card.addView(nm);
                nextCount = Ui.tabular(Ui.text(this, Fmt.countdown(next.at - System.currentTimeMillis()), 44, R.color.text_primary));
                nextCount.setTypeface(Ui.thin());
                nextCount.setGravity(Gravity.CENTER);
                card.addView(nextCount);
                nextAt = next.at;
            }
            String date = Fmt.date(this, System.currentTimeMillis(), "EEEE d MMMM");
            if (!today.hd.isEmpty()) {
                date += getString(R.string.list_sep) + " " + getString(R.string.hijri_fmt, today.hd,
                        Lang.isAr() ? today.hma : today.hme, today.hy);
            }
            TextView dt = Ui.text(this, date, 14, R.color.text_secondary);
            dt.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
            dl.topMargin = Ui.dp(this, 8);
            card.addView(dt, dl);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 10));
            col.addView(card, cp);

            LinearLayout times = Ui.group(this);
            for (int i = 0; i < 6; i++) {
                LinearLayout row = Ui.row(this, 0, 0, names[i], PrayerTimes.hm(this, today.hm[i]), false, null);
                if (next != null && next.idx == i) {
                    TextView t = row.findViewById(R.id.row_title);
                    TextView v = row.findViewById(R.id.row_value);
                    t.setTextColor(Ui.color(this, R.color.accent));
                    t.setTypeface(Ui.medium());
                    v.setTextColor(Ui.color(this, R.color.accent));
                    v.setTypeface(Ui.medium());
                }
                Ui.addRow(times, row, false);
            }
            col.addView(times);
        } else if (g.configured) {
            col.addView(Ui.footnote(this, getString(R.string.prayer_no_data)));
        } else {
            col.addView(Ui.footnote(this, getString(R.string.prayer_setup_sub)));
        }

        col.addView(Ui.sectionTitle(this, getString(R.string.sec_location)));
        LinearLayout gl = Ui.group(this);
        String locValue = !g.configured ? "" : g.auto ? getString(R.string.loc_auto) : g.place;
        Ui.addRow(gl, Ui.row(this, R.drawable.ic_globe, R.color.info, getString(R.string.location), locValue, true,
                v -> pickLocation()), true);
        col.addView(gl);

        if (g.configured) {
            LinearLayout gm = Ui.group(this);
            String[] methods = getResources().getStringArray(R.array.prayer_methods);
            int mi = 0;
            for (int i = 0; i < PrayerTimes.METHODS.length; i++) if (PrayerTimes.METHODS[i] == g.method) mi = i;
            Ui.addRow(gm, Ui.row(this, R.drawable.ic_sliders, R.color.violet, getString(R.string.prayer_method),
                    methods[mi], true, v -> pickMethod()), true);
            Ui.addRow(gm, Ui.switchRow(this, R.drawable.ic_sun, R.color.warn, getString(R.string.prayer_hanafi), null,
                    g.hanafi, (v, on) -> {
                        PrayerTimes.Cfg k = PrayerTimes.cfg(this);
                        k.hanafi = on;
                        PrayerTimes.save(this, k);
                        refreshNow();
                    }), true);
            Ui.addRow(gm, Ui.switchRow(this, R.drawable.ic_bell, R.color.bad, getString(R.string.prayer_notify), null,
                    g.notify, (v, on) -> {
                        PrayerTimes.Cfg k = PrayerTimes.cfg(this);
                        k.notify = on;
                        PrayerTimes.save(this, k);
                        PrayerTimes.scheduleNext(this);
                    }), true);
            col.addView(gm);
        }

        String note = getString(R.string.prayer_source);
        long ts = PrayerTimes.lastUpdate(this);
        if (ts > 0) {
            note += "\n" + getString(R.string.prayer_updated, Fmt.date(this, ts, "d MMM, HH:mm"));
        }
        col.addView(Ui.footnote(this, note));
    }

    private void pickLocation() {
        PrayerTimes.Cfg g = PrayerTimes.cfg(this);
        String[] labels = {getString(R.string.loc_auto), getString(R.string.loc_city)};
        Dlg.choose(this, R.string.location, labels, !g.configured ? -1 : g.auto ? 0 : 1, i -> {
            if (i == 0) locateAuto();
            else pickCity();
        });
    }

    private void pickCity() {
        final List<PrayerCities.City> cities = PrayerCities.all();
        String[] labels = new String[cities.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = cities.get(i).name();
        Dlg.choose(this, R.string.loc_city, labels, -1, i -> {
            Ui.toast(this, getString(R.string.prayer_updating));
            PrayerTimes.setCity(this, cities.get(i), ok -> {
                if (isFinishing() || isDestroyed()) return;
                if (!ok) Ui.toast(this, getString(R.string.prayer_refresh_fail));
                build();
            });
        });
    }

    private void locateAuto() {
        if (!PrayerLoc.has(this)) {
            perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            return;
        }
        Ui.toast(this, getString(R.string.loc_locating));
        PrayerLoc.locate(this, new PrayerLoc.Cb() {
            @Override
            public void on(double lat, double lon) {
                if (isFinishing() || isDestroyed()) return;
                PrayerTimes.setAuto(PrayerActivity.this, lat, lon, ok -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!ok) Ui.toast(PrayerActivity.this, getString(R.string.prayer_refresh_fail));
                    build();
                });
            }

            @Override
            public void fail() {
                if (isFinishing() || isDestroyed()) return;
                Ui.toast(PrayerActivity.this, getString(R.string.loc_failed));
            }
        });
    }

    private void pickMethod() {
        String[] methods = getResources().getStringArray(R.array.prayer_methods);
        PrayerTimes.Cfg g = PrayerTimes.cfg(this);
        int cur = 0;
        for (int i = 0; i < PrayerTimes.METHODS.length; i++) if (PrayerTimes.METHODS[i] == g.method) cur = i;
        Dlg.choose(this, R.string.prayer_method, methods, cur, i -> {
            PrayerTimes.Cfg k = PrayerTimes.cfg(this);
            k.method = PrayerTimes.METHODS[i];
            PrayerTimes.save(this, k);
            refreshNow();
        });
    }
}
