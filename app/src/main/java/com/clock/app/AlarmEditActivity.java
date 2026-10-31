package com.clock.app;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.Calendar;

/** iOS sheet to add or edit an alarm: wheels, live "rings in", repeat or date, label, colour, sound, volume and wake-up options. */
public class AlarmEditActivity extends BaseActivity {
    private static final int[] SNOOZE_VALUES = {0, 3, 5, 9, 10, 15, 20};
    private static final int[] RAMP_VALUES = {5, 10, 15, 30, 60};
    private static final int[] STOP_VALUES = {0, 1, 3, 5, 10, 15, 30};

    private Alarm alarm;
    private TimeWheels wheels;
    private final TextView[] dayViews = new TextView[7];
    private final int[] dayBit = new int[7];
    private TextView repeatSummary, whenText;
    private LinearLayout soundRow, dateGroup, dateRow, snoozeRow, rampRow, deleteAfterRow, stopRow;
    private LinearLayout snoozeBox, rampBox, deleteBox;
    private LinearLayout colorRow;
    private EditText labelEdit;

    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                Intent d = r.getData();
                if (r.getResultCode() != RESULT_OK || d == null) return;
                @SuppressWarnings("deprecation")
                Uri u = d.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                alarm.sound = u == null ? "" : u.toString();
                Ui.setValue(soundRow, soundTitle());
            });

    @Override
    protected int transition() {
        return SHEET;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        int id = getIntent().getIntExtra("id", 0);
        Alarm existing = id == 0 ? null : Alarms.get(this, id);
        if (existing != null) {
            alarm = existing.copy();
        } else {
            alarm = new Alarm();
            Calendar c = Calendar.getInstance();
            c.add(Calendar.HOUR_OF_DAY, 1);
            alarm.hour = c.get(Calendar.HOUR_OF_DAY);
            alarm.minute = 0;
            alarm.vibrate = Store.defaultVibrate(this);
            alarm.ramp = Store.defaultRamp(this);
            Intent pre = getIntent();
            if (pre.hasExtra("pre_h")) {
                alarm.hour = Math.max(0, Math.min(23, pre.getIntExtra("pre_h", alarm.hour)));
                alarm.minute = Math.max(0, Math.min(59, pre.getIntExtra("pre_m", 0)));
                String pl = pre.getStringExtra("pre_label");
                if (pl != null) alarm.label = pl;
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.sheetBar(this, getString(existing == null ? R.string.new_alarm : R.string.edit_alarm),
                getString(R.string.cancel), v -> finish(), getString(R.string.save), v -> save()));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 28));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        wheels = new TimeWheels(this, alarm.hour, alarm.minute);
        wheels.setOnChange(this::refreshWhen);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 200));
        col.addView(wheels.view, wl);

        whenText = Ui.text(this, "", 15, R.color.accent_text);
        whenText.setTypeface(Ui.medium());
        whenText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wt = new LinearLayout.LayoutParams(-1, -2);
        wt.topMargin = Ui.dp(this, 6);
        wt.bottomMargin = Ui.dp(this, 14);
        col.addView(whenText, wt);

        col.addView(buildRepeat());
        col.addView(buildDate());
        col.addView(buildLabel());
        col.addView(buildColor());
        col.addView(buildSound());
        col.addView(buildWake());
        col.addView(buildMore());

        if (existing != null) {
            LinearLayout g3 = Ui.group(this);
            Ui.addRow(g3, Ui.row(this, R.drawable.ic_play_fill, R.color.ok, getString(R.string.al_test),
                    getString(R.string.al_test_sub), false, v -> testRing()), true);
            LinearLayout del = Ui.row(this, 0, 0, getString(R.string.delete_alarm), null, false, v -> confirmDelete());
            ((TextView) del.findViewById(R.id.row_title)).setTextColor(Ui.color(this, R.color.bad));
            ((TextView) del.findViewById(R.id.row_title)).setGravity(Gravity.CENTER);
            Ui.addRow(g3, del, false);
            col.addView(g3);
        }
        refreshWhen();
    }

    // ------------------------------------------------------------------ sections

    private View buildRepeat() {
        LinearLayout g = Ui.group(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 14));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, getString(R.string.repeat), 17, R.color.text_primary), new LinearLayout.LayoutParams(0, -2, 1f));
        repeatSummary = Ui.text(this, Fmt.repeat(this, alarm.days), 15, R.color.text_secondary);
        head.addView(repeatSummary);
        box.addView(head);

        LinearLayout days = new LinearLayout(this);
        days.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        String[] initials = getResources().getStringArray(R.array.day_initials);
        int first = Calendar.getInstance().getFirstDayOfWeek();
        for (int i = 0; i < 7; i++) {
            int dow = ((first - 1 + i) % 7) + 1;
            final int bit = 1 << (dow - 1);
            dayBit[i] = bit;
            TextView t = new TextView(this);
            t.setText(initials[dow - 1]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(15);
            t.setTypeface(Ui.medium());
            t.setOnClickListener(v -> {
                alarm.days ^= bit;
                if (alarm.days != 0) alarm.date = 0;
                paintDays();
            });
            Ui.press(this, t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            days.addView(t, lp);
            dayViews[i] = t;
        }
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = Ui.dp(this, 12);
        box.addView(days, dl);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.addView(Ui.chipView(this, getString(R.string.once), v -> setDays(0)));
        chips.addView(Ui.chipView(this, getString(R.string.every_day), v -> setDays(Alarm.EVERY_DAY)));
        chips.addView(Ui.chipView(this, getString(R.string.sun_thu), v -> setDays(Alarm.SUN_THU)));
        chips.addView(Ui.chipView(this, getString(R.string.mon_fri), v -> setDays(Alarm.MON_FRI)));
        final int weekend = Lang.isAr() ? (32 | 64) : (64 | 1);
        chips.addView(Ui.chipView(this, Fmt.repeat(this, weekend), v -> setDays(weekend)));
        hs.addView(chips);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 12);
        box.addView(hs, hl);
        g.addView(box);
        paintDays();
        return g;
    }

    private View buildDate() {
        dateGroup = Ui.group(this);
        dateRow = Ui.row(this, R.drawable.ic_calendar, R.color.bad, getString(R.string.al_date), dateText(), true, v -> pickDate());
        Ui.addRow(dateGroup, dateRow, true);
        dateGroup.setVisibility(alarm.days == 0 ? View.VISIBLE : View.GONE);
        return dateGroup;
    }

    private String dateText() {
        return alarm.date > 0 ? Fmt.date(this, alarm.date, "EEE d MMM yyyy") : getString(R.string.al_next_occurrence);
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        if (alarm.date > 0) c.setTimeInMillis(alarm.date);
        DatePickerDialog dp = new DatePickerDialog(this, (view, y, m, d) -> {
            Calendar k = Calendar.getInstance();
            k.set(y, m, d, 12, 0, 0);
            alarm.date = k.getTimeInMillis();
            Ui.setValue(dateRow, dateText());
            refreshWhen();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.al_next_occurrence), (d, w) -> {
            alarm.date = 0;
            Ui.setValue(dateRow, dateText());
            refreshWhen();
        });
        dp.show();
    }

    private View buildLabel() {
        LinearLayout g = Ui.group(this);
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.setMinimumHeight(Ui.dp(this, 52));
        labelRow.setPaddingRelative(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        labelRow.addView(Ui.text(this, getString(R.string.label), 17, R.color.text_primary));
        labelEdit = new EditText(this);
        labelEdit.setBackground(null);
        labelEdit.setHint(R.string.alarm);
        labelEdit.setText(alarm.label);
        labelEdit.setSingleLine(true);
        labelEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        labelEdit.setTextSize(17);
        labelEdit.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        labelEdit.setTextColor(Ui.color(this, R.color.text_secondary));
        labelEdit.setHintTextColor(Ui.color(this, R.color.text_hint));
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(0, -2, 1f);
        el.setMarginStart(Ui.dp(this, 12));
        labelRow.addView(labelEdit, el);
        Ui.addRow(g, labelRow, false);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 12));
        int[] names = {R.string.al_label_work, R.string.al_label_gym, R.string.al_label_prayer,
                R.string.al_label_meds, R.string.al_label_school, R.string.al_label_flight};
        for (int n : names) {
            final String text = getString(n);
            chips.addView(Ui.chipView(this, text, v -> labelEdit.setText(text)));
        }
        hs.addView(chips);
        g.addView(hs);
        return g;
    }

    private View buildColor() {
        LinearLayout g = Ui.group(this);
        colorRow = new LinearLayout(this);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setMinimumHeight(Ui.dp(this, 56));
        colorRow.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        colorRow.addView(Ui.text(this, getString(R.string.al_color), 17, R.color.text_primary), new LinearLayout.LayoutParams(0, -2, 1f));
        for (int i = 0; i < Alarm.COLORS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(15);
            t.setTextColor(0xFFFFFFFF);
            t.setTypeface(Ui.medium());
            t.setOnClickListener(v -> {
                Ui.tick(v);
                alarm.color = idx;
                paintColors();
            });
            Ui.press(this, t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
            lp.setMarginStart(Ui.dp(this, 8));
            colorRow.addView(t, lp);
        }
        g.addView(colorRow);
        paintColors();
        return g;
    }

    private void paintColors() {
        for (int i = 0; i < Alarm.COLORS.length; i++) {
            TextView t = (TextView) colorRow.getChildAt(i + 1);
            t.setBackground(Ui.oval(Alarm.COLORS[i]));
            t.setText(i == alarm.color ? "\u2713" : "");
        }
    }

    private View buildSound() {
        LinearLayout g = Ui.group(this);
        soundRow = Ui.row(this, R.drawable.ic_sound, R.color.bad, getString(R.string.sound), soundTitle(), true, v -> pickSound());
        Ui.addRow(g, soundRow, true);

        LinearLayout vol = new LinearLayout(this);
        vol.setGravity(Gravity.CENTER_VERTICAL);
        vol.setMinimumHeight(Ui.dp(this, 52));
        vol.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        vol.addView(Ui.text(this, getString(R.string.al_volume), 17, R.color.text_primary), new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView pct = Ui.tabular(Ui.text(this, alarm.volume + "%", 15, R.color.text_secondary));
        pct.setGravity(Gravity.END);
        SeekBar sb = new SeekBar(this);
        sb.setMax(90);
        sb.setProgress(alarm.volume - 10);
        sb.setProgressTintList(ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        sb.setThumbTintList(ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                alarm.volume = 10 + p;
                pct.setText(alarm.volume + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                Ui.tick(s);
            }
        });
        vol.addView(sb, new LinearLayout.LayoutParams(Ui.dp(this, 150), -2));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.dp(this, 48), -2);
        vol.addView(pct, pl);
        Ui.addRow(g, vol, false);

        Ui.addRow(g, Ui.switchRow(this, R.drawable.ic_vibrate, R.color.indigo, getString(R.string.vibrate), null, alarm.vibrate,
                (v, on) -> alarm.vibrate = on), true);
        return g;
    }

    private View buildWake() {
        LinearLayout g = Ui.group(this);
        Ui.addRow(g, Ui.switchRow(this, R.drawable.ic_timer, R.color.warn, getString(R.string.snooze), null, alarm.snooze,
                (v, on) -> {
                    alarm.snooze = on;
                    snoozeBox.setVisibility(on ? View.VISIBLE : View.GONE);
                }), true);
        snoozeRow = Ui.row(this, 0, 0, getString(R.string.al_snooze_len), snoozeText(), true, v -> chooseSnooze());
        snoozeBox = cond(snoozeRow);
        snoozeBox.setVisibility(alarm.snooze ? View.VISIBLE : View.GONE);
        g.addView(snoozeBox);

        Ui.addRow(g, Ui.switchRow(this, R.drawable.ic_volume, R.color.ok, getString(R.string.ramp), getString(R.string.ramp_sub), alarm.ramp,
                (v, on) -> {
                    alarm.ramp = on;
                    rampBox.setVisibility(on ? View.VISIBLE : View.GONE);
                }), true);
        rampRow = Ui.row(this, 0, 0, getString(R.string.al_ramp_time), rampText(), true, v -> chooseRamp());
        rampBox = cond(rampRow);
        rampBox.setVisibility(alarm.ramp ? View.VISIBLE : View.GONE);
        g.addView(rampBox);

        Ui.addRow(g, Ui.switchRow(this, R.drawable.ic_brain, R.color.indigo, getString(R.string.challenge), getString(R.string.challenge_sub),
                alarm.challenge, (v, on) -> alarm.challenge = on), true);
        return g;
    }

    private View buildMore() {
        LinearLayout g = Ui.group(this);
        stopRow = Ui.row(this, R.drawable.ic_pause_fill, R.color.info, getString(R.string.al_auto_stop), stopText(), true, v -> chooseStop());
        Ui.addRow(g, stopRow, true);
        deleteAfterRow = Ui.switchRow(this, R.drawable.ic_delete, R.color.bad, getString(R.string.al_delete_after),
                getString(R.string.al_delete_after_sub), alarm.deleteAfter, (v, on) -> alarm.deleteAfter = on);
        deleteBox = cond(deleteAfterRow);
        deleteBox.setVisibility(alarm.days == 0 ? View.VISIBLE : View.GONE);
        g.addView(deleteBox);
        return g;
    }

    /** A row with its own leading hairline, so it can be hidden without leaving a stray divider. */
    private LinearLayout cond(View row) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(Ui.hairline(this, 58));
        l.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    // ------------------------------------------------------------------ value pickers

    private String minutes(int m) {
        return m + " " + getString(R.string.unit_m);
    }

    private String snoozeText() {
        return alarm.snoozeMin > 0 ? minutes(alarm.snoozeMin) : getString(R.string.al_default_fmt, Store.snoozeMin(this));
    }

    private String rampText() {
        return alarm.rampSec >= 60 ? "1 " + getString(R.string.unit_m) : alarm.rampSec + " " + getString(R.string.unit_s);
    }

    private String stopText() {
        return alarm.silenceMin > 0 ? minutes(alarm.silenceMin) : getString(R.string.al_default_fmt, Store.silenceMin(this));
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    private void chooseSnooze() {
        String[] labels = new String[SNOOZE_VALUES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = SNOOZE_VALUES[i] == 0 ? getString(R.string.al_default_fmt, Store.snoozeMin(this)) : minutes(SNOOZE_VALUES[i]);
        }
        Dlg.choose(this, R.string.al_snooze_len, labels, indexOf(SNOOZE_VALUES, alarm.snoozeMin), i -> {
            alarm.snoozeMin = SNOOZE_VALUES[i];
            Ui.setValue(snoozeRow, snoozeText());
        });
    }

    private void chooseRamp() {
        String[] labels = new String[RAMP_VALUES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = RAMP_VALUES[i] >= 60 ? "1 " + getString(R.string.unit_m) : RAMP_VALUES[i] + " " + getString(R.string.unit_s);
        }
        Dlg.choose(this, R.string.al_ramp_time, labels, indexOf(RAMP_VALUES, alarm.rampSec), i -> {
            alarm.rampSec = RAMP_VALUES[i];
            Ui.setValue(rampRow, rampText());
        });
    }

    private void chooseStop() {
        String[] labels = new String[STOP_VALUES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = STOP_VALUES[i] == 0 ? getString(R.string.al_default_fmt, Store.silenceMin(this)) : minutes(STOP_VALUES[i]);
        }
        Dlg.choose(this, R.string.al_auto_stop, labels, indexOf(STOP_VALUES, alarm.silenceMin), i -> {
            alarm.silenceMin = STOP_VALUES[i];
            Ui.setValue(stopRow, stopText());
        });
    }

    // ------------------------------------------------------------------ repeat

    private void setDays(int mask) {
        alarm.days = mask;
        if (mask != 0) alarm.date = 0;
        paintDays();
    }

    private void paintDays() {
        for (int i = 0; i < 7; i++) {
            boolean on = (alarm.days & dayBit[i]) != 0;
            dayViews[i].setBackground(Ui.oval(on ? Ui.color(this, R.color.accent) : Ui.color(this, R.color.neutral_soft)));
            dayViews[i].setTextColor(on ? 0xFFFFFFFF : Ui.color(this, R.color.text_primary));
        }
        repeatSummary.setText(Fmt.repeat(this, alarm.days));
        boolean once = alarm.days == 0;
        if (dateGroup != null) {
            dateGroup.setVisibility(once ? View.VISIBLE : View.GONE);
            Ui.setValue(dateRow, dateText());
        }
        if (deleteBox != null) deleteBox.setVisibility(once ? View.VISIBLE : View.GONE);
        if (whenText != null) refreshWhen();
    }

    private void refreshWhen() {
        if (whenText == null || wheels == null) return;
        Alarm t = alarm.copy();
        t.hour = wheels.hour();
        t.minute = wheels.minute();
        t.skipUntil = 0;
        long now = System.currentTimeMillis();
        long n = Alarms.nextOf(this, t, now);
        whenText.setText(n > 0 ? Fmt.untilText(this, n - now) + getString(R.string.list_sep) + " " + Fmt.dayLabel(this, n)
                : getString(R.string.al_past));
    }

    // ------------------------------------------------------------------ sound / actions

    private String soundTitle() {
        if (alarm.sound.isEmpty()) return getString(R.string.default_sound);
        try {
            Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(alarm.sound));
            if (r != null) return r.getTitle(this);
        } catch (Exception ignored) {
        }
        return getString(R.string.default_sound);
    }

    private void pickSound() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        if (!alarm.sound.isEmpty()) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarm.sound));
        picker.launch(i);
    }

    private void testRing() {
        try {
            Ring.start(this, Ring.KIND_ALARM, alarm.id, alarm.label, 0, "");
        } catch (Throwable ignored) {
        }
    }

    private void confirmDelete() {
        new Dlg(this).setTitle(R.string.delete_alarm).setMessage(R.string.delete_alarm_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Alarms.delete(this, alarm.id);
                    finish();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void save() {
        alarm.hour = wheels.hour();
        alarm.minute = wheels.minute();
        alarm.label = labelEdit.getText().toString().trim();
        alarm.enabled = true;
        alarm.skipUntil = 0;
        if (alarm.days != 0) {
            alarm.date = 0;
            alarm.deleteAfter = false;
        }
        long now = System.currentTimeMillis();
        if (Alarms.nextOf(this, alarm, now) <= 0 && alarm.days == 0 && alarm.date > 0 && alarm.next(now) <= 0) {
            Ui.toast(this, getString(R.string.al_past));
            return;
        }
        Alarms.save(this, alarm);
        long t = Alarms.nextOf(this, alarm, now);
        if (t > 0) Ui.toast(this, getString(R.string.alarm_set_in, Fmt.untilText(this, t - now)));
        finish();
    }
}
