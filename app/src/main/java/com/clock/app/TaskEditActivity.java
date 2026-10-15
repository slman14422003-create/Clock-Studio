package com.clock.app;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;

/** iOS sheet to add or edit a task: title, notes, date, time, priority, repeat and reminder. */
public class TaskEditActivity extends BaseActivity {
    private Task task;
    private EditText title, notes;
    private boolean hasDate, hasTime;
    private long day;
    private TimeWheels wheels;
    private LinearLayout dateBox, timeBox, remindGroup, dateRow;
    private int hour = 9, minute;
    private android.widget.CompoundButton swDate, swTime;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        int id = getIntent().getIntExtra("id", 0);
        Task existing = id == 0 ? null : Tasks.get(this, id);
        task = existing != null ? Task.fromJson(existing.toJson()) : new Task();
        if (task.due > 0) {
            hasDate = true;
            day = Fmt.startOfDay(task.due);
            hasTime = task.hasTime;
            if (hasTime) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(task.due);
                hour = c.get(Calendar.HOUR_OF_DAY);
                minute = c.get(Calendar.MINUTE);
            }
        } else {
            day = Fmt.startOfDay(System.currentTimeMillis());
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.sheetBar(this, getString(existing == null ? R.string.new_task : R.string.edit_task),
                getString(R.string.cancel), v -> finish(), getString(R.string.save), v -> save()));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 28));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        LinearLayout g = Ui.group(this);
        title = field(getString(R.string.task_title_hint), task.title, false);
        title.setTextSize(19);
        notes = field(getString(R.string.notes), task.notes, true);
        Ui.addRow(g, wrap(title), false);
        Ui.addRow(g, wrap(notes), false);
        col.addView(g);

        LinearLayout g2 = Ui.group(this);
        LinearLayout dateSwitchRow = Ui.switchRow(this, R.drawable.ic_calendar, R.color.bad, getString(R.string.date), null, hasDate, (v, on) -> {
            hasDate = on;
            if (!on) hasTime = false;
            sync();
        });
        swDate = dateSwitchRow.findViewById(R.id.row_switch);
        Ui.addRow(g2, dateSwitchRow, true);
        dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.VERTICAL);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        chips.addView(Ui.chipView(this, getString(R.string.today), v -> setDay(0)));
        chips.addView(Ui.chipView(this, getString(R.string.tomorrow), v -> setDay(1)));
        chips.addView(Ui.chipView(this, getString(R.string.next_week), v -> setDay(7)));
        chips.addView(Ui.chipView(this, getString(R.string.pick_date), v -> pickDate()));
        hs.addView(chips);
        dateBox.addView(hs);
        dateRow = Ui.row(this, 0, 0, getString(R.string.date), Fmt.dayLabel(this, day), false, null);
        dateBox.addView(dateRow);
        g2.addView(dateBox);
        LinearLayout timeSwitchRow = Ui.switchRow(this, R.drawable.ic_clock, R.color.accent, getString(R.string.time), null, hasTime, (v, on) -> {
            hasTime = on;
            if (on) hasDate = true;
            sync();
        });
        swTime = timeSwitchRow.findViewById(R.id.row_switch);
        Ui.addRow(g2, timeSwitchRow, true);
        timeBox = new LinearLayout(this);
        wheels = new TimeWheels(this, hour, minute);
        timeBox.addView(wheels.view, new LinearLayout.LayoutParams(-1, Ui.dp(this, 200)));
        g2.addView(timeBox);
        col.addView(g2);

        LinearLayout g3 = Ui.group(this);
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.VERTICAL);
        pr.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 14));
        pr.addView(Ui.text(this, getString(R.string.priority), 15, R.color.text_secondary));
        final LinearLayout prio = Ui.segmented(this, new CharSequence[]{getString(R.string.none), "!", "!!", "!!!"}, task.priority, v -> task.priority = v);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = Ui.dp(this, 8);
        pr.addView(prio, pl);
        pr.addView(Ui.text(this, getString(R.string.repeat), 15, R.color.text_secondary), new LinearLayout.LayoutParams(-2, -2) {{
            topMargin = Ui.dp(TaskEditActivity.this, 14);
        }});
        final LinearLayout rep = Ui.segmented(this, new CharSequence[]{getString(R.string.never), getString(R.string.daily),
                getString(R.string.weekly), getString(R.string.monthly)}, task.repeat, v -> task.repeat = v);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 8);
        pr.addView(rep, rl);
        g3.addView(pr);
        col.addView(g3);

        remindGroup = Ui.group(this);
        Ui.addRow(remindGroup, Ui.switchRow(this, R.drawable.ic_bell, R.color.warn, getString(R.string.remind_me), getString(R.string.remind_sub), task.remind, (v, on) -> task.remind = on), true);
        col.addView(remindGroup);

        if (existing != null) {
            LinearLayout g4 = Ui.group(this);
            LinearLayout del = Ui.row(this, 0, 0, getString(R.string.delete_task), null, false, v -> {
                Tasks.delete(this, task.id);
                finish();
            });
            ((TextView) del.findViewById(R.id.row_title)).setTextColor(Ui.color(this, R.color.bad));
            ((TextView) del.findViewById(R.id.row_title)).setGravity(Gravity.CENTER);
            Ui.addRow(g4, del, false);
            col.addView(g4);
        }
        sync();
    }

    private LinearLayout wrap(EditText e) {
        LinearLayout l = new LinearLayout(this);
        l.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        l.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    private EditText field(String hint, String text, boolean multi) {
        EditText e = new EditText(this);
        e.setBackground(null);
        e.setHint(hint);
        e.setText(text);
        e.setTextSize(16);
        e.setTextColor(Ui.color(this, R.color.text_primary));
        e.setHintTextColor(Ui.color(this, R.color.text_hint));
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(2);
            e.setMaxLines(6);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setSingleLine(true);
        }
        return e;
    }

    private void sync() {
        dateBox.setVisibility(hasDate ? View.VISIBLE : View.GONE);
        timeBox.setVisibility(hasTime ? View.VISIBLE : View.GONE);
        remindGroup.setVisibility(hasDate && hasTime ? View.VISIBLE : View.GONE);
        Ui.setValue(dateRow, Fmt.dayLabel(this, day));
        if (swDate != null && swDate.isChecked() != hasDate) swDate.setChecked(hasDate);
        if (swTime != null && swTime.isChecked() != hasTime) swTime.setChecked(hasTime);
    }

    private void setDay(int plus) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(Fmt.startOfDay(System.currentTimeMillis()));
        c.add(Calendar.DAY_OF_YEAR, plus);
        day = c.getTimeInMillis();
        sync();
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(day);
        new DatePickerDialog(this, (v, y, m, d) -> {
            Calendar n = Calendar.getInstance();
            n.setTimeInMillis(0);
            n.set(y, m, d, 0, 0, 0);
            day = Fmt.startOfDay(n.getTimeInMillis());
            sync();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void save() {
        String t = title.getText().toString().trim();
        if (t.isEmpty()) {
            Ui.toast(this, getString(R.string.title_required));
            return;
        }
        task.title = t;
        task.notes = notes.getText().toString().trim();
        task.hasTime = hasDate && hasTime;
        if (hasDate) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(day);
            if (hasTime) {
                c.set(Calendar.HOUR_OF_DAY, wheels.hour());
                c.set(Calendar.MINUTE, wheels.minute());
            }
            task.due = c.getTimeInMillis();
        } else {
            task.due = 0;
            task.repeat = Task.REPEAT_NONE;
        }
        Tasks.save(this, task);
        finish();
    }
}
