package com.clock.app;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Sheet to set the sleep schedule: wake-up time, sleep goal, days and the wind-down reminder. */
public class BedtimeActivity extends BaseActivity {
    private static final int[] GOALS = {360, 390, 420, 450, 480, 510, 540, 570, 600};
    private static final int[] REMINDS = {0, 15, 30, 45, 60};
    private static final int[] PRESETS = {Alarm.EVERY_DAY, Alarm.MON_FRI, Alarm.SUN_THU};

    private Bedtime bt;
    private TimeWheels wheels;
    private TextView bedBig, bedNote;
    private LinearLayout goalRow, repeatRow, remindRow;

    @Override
    protected int transition() {
        return SHEET;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        useGroupedBackground();
        bt = Bedtime.load(this);
        if (!bt.configured) bt.enabled = true;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.sheetBar(this, getString(R.string.bedtime), getString(R.string.cancel), v -> finish(),
                getString(R.string.save), v -> save()));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 28));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.rect(Ui.color(this, R.color.field), Ui.dpf(this, 18)));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        TextView cap = Ui.text(this, getString(R.string.go_to_bed), 13, R.color.text_secondary);
        cap.setGravity(Gravity.CENTER);
        card.addView(cap);
        bedBig = Ui.tabular(Ui.text(this, "", 50, R.color.text_primary));
        bedBig.setTypeface(Ui.thin());
        bedBig.setGravity(Gravity.CENTER);
        card.addView(bedBig);
        bedNote = Ui.text(this, "", 14, R.color.text_secondary);
        bedNote.setGravity(Gravity.CENTER);
        card.addView(bedNote);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 6));
        col.addView(card, cp);

        col.addView(Ui.sectionTitle(this, getString(R.string.wake_up)));
        wheels = new TimeWheels(this, bt.wakeHour, bt.wakeMinute);
        wheels.setOnChange(this::refresh);
        col.addView(wheels.view, new LinearLayout.LayoutParams(-1, Ui.dp(this, 200)));

        LinearLayout g = Ui.group(this);
        goalRow = Ui.row(this, R.drawable.ic_moon, R.color.indigo, getString(R.string.sleep_goal),
                Fmt.spoken(this, bt.goalMin * 60000L), true, v -> pickGoal());
        repeatRow = Ui.row(this, R.drawable.ic_repeat, R.color.warn, getString(R.string.repeat),
                Fmt.repeat(this, bt.days), true, v -> pickRepeat());
        remindRow = Ui.row(this, R.drawable.ic_bell, R.color.bad, getString(R.string.bedtime_reminder),
                remindText(), true, v -> pickRemind());
        Ui.addRow(g, goalRow, true);
        Ui.addRow(g, repeatRow, true);
        Ui.addRow(g, remindRow, true);
        LinearLayout.LayoutParams gl = (LinearLayout.LayoutParams) g.getLayoutParams();
        gl.topMargin = Ui.dp(this, 14);
        col.addView(g);

        LinearLayout g2 = Ui.group(this);
        Ui.addRow(g2, Ui.switchRow(this, R.drawable.ic_alarm, R.color.ok, getString(R.string.bedtime_on), null,
                bt.enabled, (v, isOn) -> bt.enabled = isOn), true);
        col.addView(g2);

        if (bt.configured) {
            LinearLayout g3 = Ui.group(this);
            LinearLayout del = Ui.row(this, 0, 0, getString(R.string.bedtime_remove), null, false, v -> {
                Bedtime.remove(this);
                finish();
            });
            TextView t = del.findViewById(R.id.row_title);
            t.setTextColor(Ui.color(this, R.color.bad));
            t.setGravity(Gravity.CENTER);
            Ui.addRow(g3, del, false);
            col.addView(g3);
        }
        refresh();
    }

    private String remindText() {
        if (bt.remindMin <= 0) return getString(R.string.off);
        return getString(R.string.remind_before, Fmt.spoken(this, bt.remindMin * 60000L));
    }

    private void refresh() {
        int wake = wheels.hour() * 60 + wheels.minute();
        int bed = (((wake - bt.goalMin) % 1440) + 1440) % 1440;
        bedBig.setText(Bedtime.label(this, bed / 60, bed % 60));
        bedNote.setText(getString(R.string.sleep_goal) + getString(R.string.list_sep) + " "
                + Fmt.spoken(this, bt.goalMin * 60000L));
    }

    private void pickGoal() {
        String[] labels = new String[GOALS.length];
        int cur = 0;
        for (int k = 0; k < GOALS.length; k++) {
            labels[k] = Fmt.spoken(this, GOALS[k] * 60000L);
            if (GOALS[k] == bt.goalMin) cur = k;
        }
        Dlg.choose(this, R.string.sleep_goal, labels, cur, k -> {
            bt.goalMin = GOALS[k];
            Ui.setValue(goalRow, Fmt.spoken(this, bt.goalMin * 60000L));
            refresh();
        });
    }

    private void pickRepeat() {
        String[] labels = new String[PRESETS.length];
        int cur = -1;
        for (int k = 0; k < PRESETS.length; k++) {
            labels[k] = Fmt.repeat(this, PRESETS[k]);
            if (PRESETS[k] == bt.days) cur = k;
        }
        Dlg.choose(this, R.string.repeat, labels, cur, k -> {
            bt.days = PRESETS[k];
            Ui.setValue(repeatRow, Fmt.repeat(this, bt.days));
        });
    }

    private void pickRemind() {
        String[] labels = new String[REMINDS.length];
        int cur = 0;
        for (int k = 0; k < REMINDS.length; k++) {
            labels[k] = REMINDS[k] == 0 ? getString(R.string.off)
                    : getString(R.string.remind_before, Fmt.spoken(this, REMINDS[k] * 60000L));
            if (REMINDS[k] == bt.remindMin) cur = k;
        }
        Dlg.choose(this, R.string.bedtime_reminder, labels, cur, k -> {
            bt.remindMin = REMINDS[k];
            Ui.setValue(remindRow, remindText());
        });
    }

    private void save() {
        bt.wakeHour = wheels.hour();
        bt.wakeMinute = wheels.minute();
        bt.configured = true;
        Bedtime.apply(this, bt);
        Ui.toast(this, getString(R.string.bedtime_saved));
        finish();
    }
}
