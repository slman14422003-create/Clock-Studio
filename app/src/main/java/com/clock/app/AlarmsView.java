package com.clock.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Alarm list: a Bedtime card on top (sleep schedule), then big thin times, label and repeat line and the iOS switch.
 * Swipe to delete with undo; long press for the menu (edit, skip the next ring, delete).
 */
final class AlarmsView extends TabView {
    private final RecyclerView rv;
    private final Header header;
    private final View empty;
    private final LinearLayout bedCard;
    private final TextView bedSub;
    private final SwitchCompat bedSwitch;
    private final ImageView bedChevron;
    private final List<Alarm> list = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    AlarmsView(Context c, Host h) {
        super(c, h);
        header = new Header(c, c.getString(R.string.nav_alarms));
        header.addButton(R.drawable.ic_add, R.string.new_alarm, true, v -> host.editAlarm(0));
        addView(header);

        bedCard = new LinearLayout(c);
        bedCard.setOrientation(LinearLayout.HORIZONTAL);
        bedCard.setGravity(Gravity.CENTER_VERTICAL);
        bedCard.setBackground(Ui.rect(Ui.color(c, R.color.field), Ui.dpf(c, 16)));
        bedCard.setPaddingRelative(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36));
        tl.setMarginEnd(Ui.dp(c, 12));
        bedCard.addView(Ui.tile(c, R.drawable.ic_moon, R.color.indigo), tl);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView bedTitle = Ui.text(c, c.getString(R.string.bedtime), 17, R.color.text_primary);
        bedTitle.setTypeface(Ui.medium());
        col.addView(bedTitle);
        bedSub = Ui.text(c, "", 13, R.color.text_secondary);
        bedSub.setSingleLine(true);
        col.addView(bedSub);
        bedCard.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        bedSwitch = Ui.styledSwitch(c, false);
        bedCard.addView(bedSwitch);
        bedChevron = new ImageView(c);
        bedChevron.setImageResource(R.drawable.ic_chevron);
        bedChevron.setImageTintList(ColorStateList.valueOf(Ui.color(c, R.color.text_hint)));
        bedCard.addView(bedChevron, new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
        Ui.press(c, bedCard);
        bedCard.setOnClickListener(v -> openBedtime());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 8));
        addView(bedCard, bp);

        FrameLayout stage = new FrameLayout(c);
        rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setAdapter(adapter);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        header.bindScroll(rv);
        new ItemTouchHelper(new SwipeDelete(c, this::deleteAt)).attachToRecyclerView(rv);
        stage.addView(rv, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.empty(c, R.drawable.ic_alarm, c.getString(R.string.no_alarms), c.getString(R.string.no_alarms_sub));
        stage.addView(empty, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        addView(stage, new LayoutParams(-1, 0, 1f));
        refresh();
    }

    private void openBedtime() {
        Context c = getContext();
        if (c instanceof Activity) Ui.openSheet((Activity) c, new Intent(c, BedtimeActivity.class));
    }

    private void updateBedtime() {
        final Context c = getContext();
        Bedtime b = Bedtime.load(c);
        if (!b.configured) {
            bedSub.setText(R.string.bedtime_setup_sub);
            bedSwitch.setVisibility(GONE);
            bedChevron.setVisibility(VISIBLE);
        } else {
            bedSub.setText(c.getString(R.string.bedtime_line, Bedtime.label(c, b.bedHour(), b.bedMinute()),
                    Bedtime.label(c, b.wakeHour, b.wakeMinute)));
            bedChevron.setVisibility(GONE);
            bedSwitch.setVisibility(VISIBLE);
            bedSwitch.setOnCheckedChangeListener(null);
            bedSwitch.setChecked(b.enabled);
            bedSwitch.setOnCheckedChangeListener((v, on) -> {
                Ui.tick(v);
                Bedtime.setEnabled(c, on);
            });
        }
        bedCard.setAlpha(!b.configured || b.enabled ? 1f : 0.6f);
    }

    private void deleteAt(int pos) {
        if (pos < 0 || pos >= list.size()) return;
        removeWithUndo(list.get(pos));
    }

    private void removeWithUndo(final Alarm a) {
        final Context c = getContext();
        Alarms.delete(c, a.id);
        host.undo(c.getString(R.string.alarm_deleted), () -> Alarms.save(c, a));
    }

    private void menu(final Alarm a) {
        final Context c = getContext();
        final boolean canSkip = a.enabled && a.days != 0;
        final boolean skipping = a.skipUntil > System.currentTimeMillis();
        List<String> labels = new ArrayList<>();
        labels.add(c.getString(R.string.edit_alarm));
        if (canSkip) labels.add(c.getString(skipping ? R.string.skip_cancel : R.string.skip_next));
        labels.add(c.getString(R.string.delete_alarm));
        final int skipIdx = canSkip ? 1 : -1;
        final int delIdx = labels.size() - 1;
        Dlg.choose(c, R.string.alarm, labels.toArray(new String[0]), -1, i -> {
            if (i == 0) {
                host.editAlarm(a.id);
            } else if (i == skipIdx) {
                if (skipping) {
                    Alarms.clearSkip(c, a.id);
                } else {
                    Alarms.skipNext(c, a.id);
                    Ui.toast(c, c.getString(R.string.skip_done));
                }
            } else if (i == delIdx) {
                removeWithUndo(a);
            }
        });
    }

    @Override
    void refresh() {
        list.clear();
        list.addAll(Alarms.all(getContext()));
        adapter.notifyDataSetChanged();
        empty.setVisibility(list.isEmpty() ? VISIBLE : GONE);
        updateSubtitle();
        updateBedtime();
    }

    private void updateSubtitle() {
        long next = Alarms.nextTrigger(getContext());
        header.setSubtitle(next == 0 ? getContext().getString(R.string.no_active_alarm)
                : getContext().getString(R.string.next_alarm, Fmt.untilText(getContext(), next - System.currentTimeMillis())));
    }

    @Override
    void tick() {
        updateSubtitle();
    }

    @Override
    void scrollToTop() {
        rv.smoothScrollToPosition(0);
    }

    private final class Adapter extends RecyclerView.Adapter<VH> {
        @Override
        public int getItemCount() {
            return list.size();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VH(getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int p) {
            h.bind(list.get(p));
        }
    }

    private final class VH extends RecyclerView.ViewHolder {
        final TextView time, ampm, sub;
        final SwitchCompat sw;
        final LinearLayout box;

        VH(Context c) {
            super(new LinearLayout(c));
            LinearLayout l = (LinearLayout) itemView;
            l.setOrientation(LinearLayout.VERTICAL);
            l.setBackgroundColor(Ui.color(c, R.color.bg));
            l.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            box = new LinearLayout(c);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPaddingRelative(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 10));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout tr = new LinearLayout(c);
            tr.setGravity(Gravity.BOTTOM);
            time = Ui.tabular(Ui.text(c, "", 52, R.color.text_primary));
            time.setTypeface(Ui.thin());
            tr.addView(time);
            ampm = Ui.text(c, "", 18, R.color.text_primary);
            ampm.setTypeface(Ui.light());
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
            al.setMarginStart(Ui.dp(c, 5));
            al.bottomMargin = Ui.dp(c, 9);
            tr.addView(ampm, al);
            col.addView(tr);
            sub = Ui.text(c, "", 14, R.color.text_secondary);
            sub.setSingleLine(true);
            col.addView(sub);
            box.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            sw = Ui.styledSwitch(c, false);
            box.addView(sw);
            l.addView(box, new LinearLayout.LayoutParams(-1, -2));
            l.addView(Ui.hairline(c, 20));
            Ui.ripple(c, box);
        }

        void bind(final Alarm a) {
            final Context c = itemView.getContext();
            time.setText(Fmt.hm(c, a.hour, a.minute));
            ampm.setText(Fmt.ampm(c, a.hour));
            String s = Fmt.repeat(c, a.days);
            if (a.enabled && a.skipUntil > System.currentTimeMillis()) {
                s = c.getString(R.string.skipping_next) + c.getString(R.string.list_sep) + " " + s;
            }
            sub.setText(a.label.isEmpty() ? s : a.label + c.getString(R.string.list_sep) + " " + s);
            box.setAlpha(a.enabled ? 1f : 0.45f);
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(a.enabled);
            sw.setOnCheckedChangeListener((v, on) -> {
                Ui.tick(v);
                Alarms.setEnabled(c, a.id, on);
                if (on) {
                    Alarm fresh = Alarms.get(c, a.id);
                    long t = fresh == null ? 0 : fresh.next(System.currentTimeMillis());
                    if (t > 0) Ui.toast(c, c.getString(R.string.alarm_set_in, Fmt.untilText(c, t - System.currentTimeMillis())));
                }
            });
            box.setOnClickListener(v -> host.editAlarm(a.id));
            box.setOnLongClickListener(v -> {
                Ui.tick(v);
                menu(a);
                return true;
            });
        }
    }
}
