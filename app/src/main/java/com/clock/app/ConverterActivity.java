package com.clock.app;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Samsung style time converter: pick a time in one city and see it in all your cities. */
public class ConverterActivity extends BaseActivity {
    private final List<String> zones = new ArrayList<>();
    private int baseIdx;
    private TimeWheels wheels;
    private LinearLayout rows;
    private LinearLayout baseRow;

    @Override
    protected int transition() {
        return SHEET;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        useGroupedBackground();
        zones.add(TimeZone.getDefault().getID());
        for (String z : WorldClockView.loadZones(this)) if (!zones.contains(z)) zones.add(z);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.sheetBar(this, getString(R.string.converter), getString(R.string.cancel), v -> finish(),
                getString(R.string.mark_done), v -> finish()));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 28));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        Calendar now = Calendar.getInstance();
        wheels = new TimeWheels(this, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        wheels.setOnChange(this::update);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 200));
        wl.bottomMargin = Ui.dp(this, 12);
        col.addView(wheels.view, wl);

        LinearLayout g = Ui.group(this);
        baseRow = Ui.row(this, R.drawable.ic_globe, R.color.info, getString(R.string.conv_from),
                Cities.name(zones.get(0)), true, v -> pickBase());
        Ui.addRow(g, baseRow, true);
        col.addView(g);

        rows = Ui.group(this);
        col.addView(rows);
        update();
    }

    private void pickBase() {
        final String[] labels = new String[zones.size()];
        for (int k = 0; k < labels.length; k++) labels[k] = Cities.name(zones.get(k));
        Dlg.choose(this, R.string.conv_from, labels, baseIdx, k -> {
            baseIdx = k;
            Ui.setValue(baseRow, labels[k]);
            update();
        });
    }

    private void update() {
        TimeZone bz = TimeZone.getTimeZone(zones.get(baseIdx));
        Calendar bc = Calendar.getInstance(bz);
        bc.set(Calendar.HOUR_OF_DAY, wheels.hour());
        bc.set(Calendar.MINUTE, wheels.minute());
        bc.set(Calendar.SECOND, 0);
        bc.set(Calendar.MILLISECOND, 0);
        long base = bc.getTimeInMillis();
        long baseDay = Math.floorDiv(base + bz.getOffset(base), 86400000L);
        rows.removeAllViews();
        for (int k = 0; k < zones.size(); k++) {
            TimeZone tz = TimeZone.getTimeZone(zones.get(k));
            Calendar cal = Calendar.getInstance(tz);
            cal.setTimeInMillis(base);
            int diff = (int) (Math.floorDiv(base + tz.getOffset(base), 86400000L) - baseDay);
            String dayText = diff == 0 ? getString(R.string.conv_same_day)
                    : getString(diff > 0 ? R.string.tomorrow : R.string.yesterday);
            int dm = (tz.getOffset(base) - bz.getOffset(base)) / 60000;
            if (dm != 0) {
                int a = Math.abs(dm);
                String amount = a % 60 == 0 ? String.valueOf(a / 60) : (a / 60) + ":" + Fmt.two(a % 60);
                dayText += getString(R.string.list_sep) + " " + (dm > 0 ? "+" : "\u2212") + amount + " " + getString(R.string.unit_h);
            }

            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(this, Cities.name(zones.get(k)), 17, R.color.text_primary);
            name.setSingleLine(true);
            left.addView(name);
            left.addView(Ui.text(this, dayText, 13, R.color.text_secondary));
            r.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView time = Ui.tabular(Ui.text(this, Fmt.time(this, base, tz, false), 34,
                    k == baseIdx ? R.color.accent : R.color.text_primary));
            time.setTypeface(Ui.thin());
            r.addView(time);
            TextView ap = Ui.text(this, Fmt.ampm(this, cal.get(Calendar.HOUR_OF_DAY)), 14, R.color.text_secondary);
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
            al.setMarginStart(Ui.dp(this, 4));
            r.addView(ap, al);
            Ui.addRow(rows, r, false);
        }
    }
}
