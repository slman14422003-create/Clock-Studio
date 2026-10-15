package com.clock.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Alarm list: big thin times, label and repeat line, iOS switch. Swipe to delete with undo. */
final class AlarmsView extends TabView {
    private final RecyclerView rv;
    private final Header header;
    private final View empty;
    private final List<Alarm> list = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    AlarmsView(Context c, Host h) {
        super(c, h);
        header = new Header(c, c.getString(R.string.nav_alarms));
        header.addButton(R.drawable.ic_add, R.string.new_alarm, true, v -> host.editAlarm(0));
        addView(header);
        FrameLayout stage = new FrameLayout(c);
        rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setAdapter(adapter);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        new ItemTouchHelper(new SwipeDelete(c, this::deleteAt)).attachToRecyclerView(rv);
        stage.addView(rv, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.empty(c, R.drawable.ic_alarm, c.getString(R.string.no_alarms), c.getString(R.string.no_alarms_sub));
        stage.addView(empty, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        addView(stage, new LayoutParams(-1, 0, 1f));
        refresh();
    }

    private void deleteAt(int pos) {
        if (pos < 0 || pos >= list.size()) return;
        final Alarm a = list.get(pos);
        Alarms.delete(getContext(), a.id);
        host.undo(getContext().getString(R.string.alarm_deleted), () -> {
            Alarms.save(getContext(), a);
        });
    }

    @Override
    void refresh() {
        list.clear();
        list.addAll(Alarms.all(getContext()));
        adapter.notifyDataSetChanged();
        empty.setVisibility(list.isEmpty() ? VISIBLE : GONE);
        updateSubtitle();
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
            Context c = itemView.getContext();
            time.setText(Fmt.hm(c, a.hour, a.minute));
            ampm.setText(Fmt.ampm(c, a.hour));
            String s = Fmt.repeat(c, a.days);
            sub.setText(a.label.isEmpty() ? s : a.label + c.getString(R.string.list_sep) + " " + s);
            box.setAlpha(a.enabled ? 1f : 0.45f);
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(a.enabled);
            sw.setOnCheckedChangeListener((v, on) -> {
                Alarms.setEnabled(c, a.id, on);
                if (on) {
                    Alarm fresh = Alarms.get(c, a.id);
                    long t = fresh == null ? 0 : fresh.next(System.currentTimeMillis());
                    if (t > 0) Ui.toast(c, c.getString(R.string.alarm_set_in, Fmt.untilText(c, t - System.currentTimeMillis())));
                }
            });
            box.setOnClickListener(v -> host.editAlarm(a.id));
        }
    }
}
