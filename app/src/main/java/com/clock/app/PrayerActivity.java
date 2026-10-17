package com.clock.app;

import android.Manifest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

            final String todayKey = PrayerTimes.today(g);
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
                int pos = PrayerLog.pos(i);
                if (pos >= 0) {
                    FrameLayout ck = check(pos, todayKey);
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28));
                    cl.setMarginStart(Ui.dp(this, 14));
                    row.addView(ck, cl);
                }
                Ui.addRow(times, row, false);
            }
            col.addView(times);
            col.addView(Ui.footnote(this, getString(R.string.prayer_check_hint)));

            col.addView(Ui.sectionTitle(this, getString(R.string.prayer_log)));
            col.addView(logCard(todayKey));

            long[] night = PrayerTimes.night(this, g);
            if (night[0] > 0) {
                LinearLayout gn = Ui.group(this);
                Ui.addRow(gn, Ui.row(this, R.drawable.ic_moon, R.color.indigo, getString(R.string.night_mid),
                        PrayerTimes.label(this, night[0], g.zone), false, null), true);
                Ui.addRow(gn, Ui.row(this, R.drawable.ic_moon, R.color.violet, getString(R.string.night_third),
                        PrayerTimes.label(this, night[1], g.zone), false, null), true);
                col.addView(Ui.sectionTitle(this, getString(R.string.night_title)));
                col.addView(gn);
            }
        } else if (g.configured) {
            col.addView(Ui.footnote(this, getString(R.string.prayer_no_data)));
        } else {
            col.addView(Ui.footnote(this, getString(R.string.prayer_setup_sub)));
        }

        col.addView(Ui.sectionTitle(this, getString(R.string.prayer_tools)));
        LinearLayout gt = Ui.group(this);
        Ui.addRow(gt, Ui.row(this, R.drawable.ic_compass, R.color.ok, getString(R.string.qibla), null, true,
                v -> startActivity(new Intent(this, QiblaActivity.class))), true);
        Ui.addRow(gt, Ui.row(this, R.drawable.ic_beads, R.color.indigo, getString(R.string.tasbih), null, true,
                v -> startActivity(new Intent(this, TasbihActivity.class))), true);
        col.addView(gt);

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
                        build();
                    }), true);
            if (g.notify) {
                Ui.addRow(gm, Ui.row(this, R.drawable.ic_alarm, R.color.accent, getString(R.string.prayer_pre),
                        g.pre == 0 ? getString(R.string.none) : getString(R.string.prayer_min, g.pre), true, v -> pickPre()), true);
            }
            col.addView(gm);

            if (g.notify) {
                col.addView(Ui.sectionTitle(this, getString(R.string.prayer_each)));
                LinearLayout ge = Ui.group(this);
                for (int pos = 0; pos < PrayerTimes.PRAYERS.length; pos++) {
                    final int bit = 1 << pos;
                    Ui.addRow(ge, Ui.switchRow(this, 0, 0, names[PrayerTimes.PRAYERS[pos]], null, (g.mask & bit) != 0, (v, on) -> {
                        PrayerTimes.Cfg k = PrayerTimes.cfg(this);
                        k.mask = on ? (k.mask | bit) : (k.mask & ~bit);
                        PrayerTimes.save(this, k);
                        PrayerTimes.scheduleNext(this);
                    }), false);
                }
                col.addView(ge);
            }
        }

        String note = getString(R.string.prayer_source);
        long ts = PrayerTimes.lastUpdate(this);
        if (ts > 0) {
            note += "\n" + getString(R.string.prayer_updated, Fmt.date(this, ts, "d MMM, HH:mm"));
        }
        col.addView(Ui.footnote(this, note));
    }

    private FrameLayout check(final int pos, final String ymd) {
        final FrameLayout f = new FrameLayout(this);
        ImageView tick = new ImageView(this);
        tick.setImageResource(R.drawable.ic_check);
        tick.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        f.addView(tick, new FrameLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16), Gravity.CENTER));
        styleCheck(f, PrayerLog.done(this, ymd, pos));
        f.setOnClickListener(v -> {
            boolean on = !PrayerLog.done(this, ymd, pos);
            PrayerLog.set(this, ymd, pos, on);
            Ui.tick(f);
            styleCheck(f, on);
            fillLog(ymd);
        });
        return f;
    }

    private void styleCheck(FrameLayout f, boolean on) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        if (on) {
            ring.setColor(Ui.color(this, R.color.ok));
        } else {
            ring.setColor(0);
            ring.setStroke(Ui.dp(this, 2), Ui.color(this, R.color.text_hint));
        }
        f.setBackground(ring);
        f.getChildAt(0).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    private TextView logTitle, logSub;
    private LinearLayout logBars;

    private LinearLayout logCard(String ymd) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 14)));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        logTitle = Ui.text(this, "", 18, R.color.text_primary);
        logTitle.setTypeface(Ui.medium());
        card.addView(logTitle);
        logSub = Ui.text(this, "", 14, R.color.text_secondary);
        card.addView(logSub);
        logBars = new LinearLayout(this);
        logBars.setGravity(Gravity.BOTTOM);
        logBars.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 78));
        bl.topMargin = Ui.dp(this, 12);
        card.addView(logBars, bl);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 6));
        card.setLayoutParams(cp);
        fillLog(ymd);
        return card;
    }

    private void fillLog(String ymd) {
        int today = PrayerLog.count(PrayerLog.mask(this, ymd));
        logTitle.setText(getString(R.string.prayer_log_today, today));
        int streak = PrayerLog.streak(this, ymd);
        logSub.setText(streak > 0 ? getString(R.string.prayer_streak, streak) : getString(R.string.prayer_streak_none));
        logBars.removeAllViews();
        String[] ini = getResources().getStringArray(R.array.day_initials);
        for (int i = 6; i >= 0; i--) {
            String d = PrayerLog.shift(ymd, -i);
            int n = PrayerLog.count(PrayerLog.mask(this, d));
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            View bar = new View(this);
            int color = n >= 5 ? Ui.color(this, R.color.ok) : n > 0 ? Ui.color(this, R.color.accent) : Ui.color(this, R.color.neutral_soft);
            bar.setBackground(Ui.rect(color, Ui.dpf(this, 5)));
            int h = Math.max(Ui.dp(this, 6), Ui.dp(this, 44) * n / 5);
            cell.addView(bar, new LinearLayout.LayoutParams(Ui.dp(this, 14), h));
            TextView lbl = Ui.text(this, ini[PrayerLog.weekday(d) - 1], 12, i == 0 ? R.color.text_primary : R.color.text_secondary);
            lbl.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.topMargin = Ui.dp(this, 4);
            cell.addView(lbl, ll);
            logBars.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
        }
    }

    private void pickPre() {
        final int[] vals = {0, 5, 10, 15, 20, 30};
        PrayerTimes.Cfg g = PrayerTimes.cfg(this);
        String[] labels = new String[vals.length];
        int cur = 0;
        for (int i = 0; i < vals.length; i++) {
            labels[i] = vals[i] == 0 ? getString(R.string.none) : getString(R.string.prayer_min, vals[i]);
            if (vals[i] == g.pre) cur = i;
        }
        Dlg.choose(this, R.string.prayer_pre, labels, cur, i -> {
            PrayerTimes.Cfg k = PrayerTimes.cfg(this);
            k.pre = vals[i];
            PrayerTimes.save(this, k);
            PrayerTimes.scheduleNext(this);
            build();
        });
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
