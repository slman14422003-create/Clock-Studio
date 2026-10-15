package com.clock.app;

import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.Calendar;

/** iOS sheet to add or edit an alarm: time wheels, day circles, label, sound and the wake-up options. */
public class AlarmEditActivity extends BaseActivity {
    private Alarm alarm;
    private TimeWheels wheels;
    private final TextView[] dayViews = new TextView[7];
    private final int[] dayBit = new int[7];
    private TextView repeatSummary;
    private LinearLayout soundRow;
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
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 200));
        wl.bottomMargin = Ui.dp(this, 12);
        col.addView(wheels.view, wl);

        col.addView(buildRepeat());

        LinearLayout g = Ui.group(this);
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.setMinimumHeight(Ui.dp(this, 52));
        labelRow.setPaddingRelative(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        TextView lt = Ui.text(this, getString(R.string.label), 17, R.color.text_primary);
        labelRow.addView(lt);
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

        soundRow = Ui.row(this, 0, 0, getString(R.string.sound), soundTitle(), true, v -> pickSound());
        Ui.addRow(g, soundRow, false);
        col.addView(g);

        LinearLayout g2 = Ui.group(this);
        Ui.addRow(g2, Ui.switchRow(this, 0, 0, getString(R.string.vibrate), null, alarm.vibrate, (v, on) -> alarm.vibrate = on), false);
        Ui.addRow(g2, Ui.switchRow(this, 0, 0, getString(R.string.snooze), null, alarm.snooze, (v, on) -> alarm.snooze = on), false);
        Ui.addRow(g2, Ui.switchRow(this, 0, 0, getString(R.string.ramp), getString(R.string.ramp_sub), alarm.ramp, (v, on) -> alarm.ramp = on), false);
        Ui.addRow(g2, Ui.switchRow(this, 0, 0, getString(R.string.challenge), getString(R.string.challenge_sub), alarm.challenge, (v, on) -> alarm.challenge = on), false);
        col.addView(g2);

        if (existing != null) {
            LinearLayout g3 = Ui.group(this);
            LinearLayout del = Ui.row(this, 0, 0, getString(R.string.delete_alarm), null, false, v -> confirmDelete());
            ((TextView) del.findViewById(R.id.row_title)).setTextColor(Ui.color(this, R.color.bad));
            ((TextView) del.findViewById(R.id.row_title)).setGravity(Gravity.CENTER);
            Ui.addRow(g3, del, false);
            col.addView(g3);
        }
    }

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

        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.addView(Ui.chipView(this, getString(R.string.once), v -> setDays(0)));
        chips.addView(Ui.chipView(this, getString(R.string.every_day), v -> setDays(Alarm.EVERY_DAY)));
        chips.addView(Ui.chipView(this, getString(R.string.sun_thu), v -> setDays(Alarm.SUN_THU)));
        chips.addView(Ui.chipView(this, getString(R.string.mon_fri), v -> setDays(Alarm.MON_FRI)));
        hs.addView(chips);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 12);
        box.addView(hs, hl);
        g.addView(box);
        paintDays();
        return g;
    }

    private void setDays(int mask) {
        alarm.days = mask;
        paintDays();
    }

    private void paintDays() {
        for (int i = 0; i < 7; i++) {
            boolean on = (alarm.days & dayBit[i]) != 0;
            dayViews[i].setBackground(Ui.oval(on ? Ui.color(this, R.color.accent) : Ui.color(this, R.color.neutral_soft)));
            dayViews[i].setTextColor(on ? 0xFFFFFFFF : Ui.color(this, R.color.text_primary));
        }
        repeatSummary.setText(Fmt.repeat(this, alarm.days));
    }

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
        Alarms.save(this, alarm);
        long t = alarm.next(System.currentTimeMillis());
        if (t > 0) Ui.toast(this, getString(R.string.alarm_set_in, Fmt.untilText(this, t - System.currentTimeMillis())));
        finish();
    }
}
