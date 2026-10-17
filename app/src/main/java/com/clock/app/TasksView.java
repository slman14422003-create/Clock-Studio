package com.clock.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Tasks in the iOS Reminders style: four smart-list tiles on grey, then the chosen list as a rounded group. */
final class TasksView extends TabView {
    private static final int T_TILES = 0, T_SECTION = 1, T_TASK = 2, T_EMPTY = 3;
    private static final int[] TILE_COLOR = {R.color.accent, R.color.bad, R.color.violet, R.color.ok};
    private static final int[] TILE_ICON = {R.drawable.ic_calendar, R.drawable.ic_clock, R.drawable.ic_list, R.drawable.ic_check_circle};
    private static final int[] TILE_NAME = {R.string.list_today, R.string.list_scheduled, R.string.list_all, R.string.list_done};

    private int smart = Tasks.TODAY;
    private final RecyclerView rv;
    private final Adapter adapter = new Adapter();
    private final List<Task> tasks = new ArrayList<>();
    private final int[] counts = new int[4];
    private final Header hd;

    TasksView(Context c, Host h) {
        super(c, h);
        setBackgroundColor(Ui.color(c, R.color.bg_group));
        hd = new Header(c, c.getString(R.string.nav_tasks));
        hd.addButton(R.drawable.ic_brain, R.string.assistant, false, v -> AssistantActivity.open(c, false));
        hd.addButton(R.drawable.ic_add, R.string.new_task, true, v -> host.editTask(0));
        addView(hd);
        rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setAdapter(adapter);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hd.bindScroll(rv);
        new ItemTouchHelper(new SwipeDelete(c, this::deleteAt).filter(p -> p >= 2 && !tasks.isEmpty())).attachToRecyclerView(rv);
        addView(rv, new LayoutParams(-1, 0, 1f));
        refresh();
    }

    private void deleteAt(int pos) {
        int i = pos - 2;
        if (i < 0 || i >= tasks.size()) return;
        final Task t = tasks.get(i);
        Tasks.delete(getContext(), t.id);
        host.undo(getContext().getString(R.string.task_deleted), () -> Tasks.save(getContext(), t));
    }

    @Override
    void refresh() {
        Context c = getContext();
        tasks.clear();
        tasks.addAll(Tasks.filter(c, smart));
        for (int i = 0; i < 4; i++) counts[i] = Tasks.count(c, i);
        hd.setSubtitle(Smart.insight(c));
        adapter.notifyDataSetChanged();
    }

    @Override
    void scrollToTop() {
        rv.smoothScrollToPosition(0);
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return 2 + Math.max(1, tasks.size());
        }

        @Override
        public int getItemViewType(int p) {
            if (p == 0) return T_TILES;
            if (p == 1) return T_SECTION;
            return tasks.isEmpty() ? T_EMPTY : T_TASK;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = getContext();
            if (type == T_TILES) return new TilesVH(c);
            if (type == T_SECTION) return new SectionVH(c);
            if (type == T_EMPTY) return new EmptyVH(c);
            return new TaskVH(c);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
            if (h instanceof TilesVH) ((TilesVH) h).bind();
            else if (h instanceof SectionVH) ((SectionVH) h).bind();
            else if (h instanceof TaskVH) {
                int i = p - 2;
                ((TaskVH) h).bind(tasks.get(i), i == 0, i == tasks.size() - 1);
            }
        }
    }

    private final class TilesVH extends RecyclerView.ViewHolder {
        final LinearLayout[] tile = new LinearLayout[4];
        final TextView[] count = new TextView[4];

        TilesVH(Context c) {
            super(new LinearLayout(c));
            LinearLayout col = (LinearLayout) itemView;
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), 0);
            col.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            for (int r = 0; r < 2; r++) {
                LinearLayout row = new LinearLayout(c);
                for (int k = 0; k < 2; k++) {
                    final int idx = r * 2 + k;
                    LinearLayout t = new LinearLayout(c);
                    t.setOrientation(LinearLayout.VERTICAL);
                    t.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
                    t.setMinimumHeight(Ui.dp(c, 92));
                    LinearLayout top = new LinearLayout(c);
                    top.setGravity(Gravity.CENTER_VERTICAL);
                    ImageView ic = new ImageView(c);
                    ic.setImageResource(TILE_ICON[idx]);
                    ic.setImageTintList(ColorStateList.valueOf(Color.WHITE));
                    int p = Ui.dp(c, 7);
                    ic.setPadding(p, p, p, p);
                    ic.setBackground(Ui.oval(Ui.color(c, TILE_COLOR[idx])));
                    top.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34)));
                    View sp = new View(c);
                    top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
                    TextView n = Ui.tabular(Ui.text(c, "0", 28, R.color.text_primary));
                    n.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                    top.addView(n);
                    t.addView(top, new LinearLayout.LayoutParams(-1, -2));
                    TextView name = Ui.text(c, c.getString(TILE_NAME[idx]), 15, R.color.text_secondary);
                    name.setTypeface(Ui.medium());
                    LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
                    nl.topMargin = Ui.dp(c, 8);
                    t.addView(name, nl);
                    t.setOnClickListener(v -> {
                        smart = idx;
                        refresh();
                    });
                    Ui.press(c, t);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                    lp.setMarginStart(k == 0 ? 0 : Ui.dp(c, 6));
                    lp.setMarginEnd(k == 0 ? Ui.dp(c, 6) : 0);
                    lp.bottomMargin = Ui.dp(c, 12);
                    row.addView(t, lp);
                    tile[idx] = t;
                    count[idx] = n;
                }
                col.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout cap = new LinearLayout(c);
            cap.setGravity(Gravity.CENTER_VERTICAL);
            cap.setPaddingRelative(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
            cap.setBackground(Ui.rect(Ui.color(c, R.color.surface), Ui.dpf(c, 18)));
            ImageView spark = new ImageView(c);
            spark.setImageResource(R.drawable.ic_brain);
            spark.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            int sp2 = Ui.dp(c, 6);
            spark.setPadding(sp2, sp2, sp2, sp2);
            spark.setBackground(Ui.rect(Ui.color(c, R.color.violet), Ui.dpf(c, 9)));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32));
            sl.setMarginEnd(Ui.dp(c, 12));
            cap.addView(spark, sl);
            TextView hint = Ui.text(c, c.getString(R.string.ai_capture_hint), 15, R.color.text_secondary);
            hint.setSingleLine(true);
            hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cap.addView(hint, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView mic = new ImageView(c);
            mic.setImageResource(R.drawable.ic_mic);
            mic.setImageTintList(ColorStateList.valueOf(Ui.color(c, R.color.accent)));
            cap.addView(mic, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
            cap.setOnClickListener(v -> AssistantActivity.open(c, true));
            Ui.press(c, cap);
            LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(-1, -2);
            capLp.bottomMargin = Ui.dp(c, 4);
            col.addView(cap, capLp);
        }

        void bind() {
            Context c = itemView.getContext();
            for (int i = 0; i < 4; i++) {
                count[i].setText(String.valueOf(counts[i]));
                GradientDrawable g = Ui.rect(Ui.color(c, R.color.surface), Ui.dpf(c, 18));
                if (i == smart) g.setStroke(Ui.dp(c, 2), Ui.color(c, TILE_COLOR[i]));
                tile[i].setBackground(g);
            }
        }
    }

    private final class SectionVH extends RecyclerView.ViewHolder {
        final TextView t;

        SectionVH(Context c) {
            super(new LinearLayout(c));
            LinearLayout l = (LinearLayout) itemView;
            l.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            l.setPaddingRelative(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 8));
            t = Ui.text(c, "", 26, R.color.accent);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            l.addView(t);
        }

        void bind() {
            t.setText(TILE_NAME[smart]);
            t.setTextColor(Ui.color(itemView.getContext(), TILE_COLOR[smart]));
        }
    }

    private final class EmptyVH extends RecyclerView.ViewHolder {
        EmptyVH(Context c) {
            super(new LinearLayout(c));
            LinearLayout l = (LinearLayout) itemView;
            l.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            l.setGravity(Gravity.CENTER);
            l.setPadding(Ui.dp(c, 32), Ui.dp(c, 40), Ui.dp(c, 32), Ui.dp(c, 40));
            TextView t = Ui.text(c, c.getString(R.string.no_tasks), 16, R.color.text_secondary);
            t.setGravity(Gravity.CENTER);
            l.addView(t);
        }
    }

    private final class TaskVH extends RecyclerView.ViewHolder {
        final LinearLayout card, box;
        final FrameLayout check;
        final ImageView tick;
        final TextView title, sub, prio;
        final View line;

        TaskVH(Context c) {
            super(new LinearLayout(c));
            card = (LinearLayout) itemView;
            card.setOrientation(LinearLayout.VERTICAL);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
            card.setLayoutParams(lp);
            box = new LinearLayout(c);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setMinimumHeight(Ui.dp(c, 56));
            box.setPaddingRelative(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            check = new FrameLayout(c);
            tick = new ImageView(c);
            tick.setImageResource(R.drawable.ic_check);
            tick.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            check.addView(tick, new FrameLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26));
            cl.setMarginEnd(Ui.dp(c, 12));
            box.addView(check, cl);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout tr = new LinearLayout(c);
            prio = Ui.text(c, "", 17, R.color.warn);
            prio.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
            pl.setMarginEnd(Ui.dp(c, 5));
            tr.addView(prio, pl);
            title = Ui.text(c, "", 17, R.color.text_primary);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tr.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(tr);
            sub = Ui.text(c, "", 13, R.color.text_secondary);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(sub);
            box.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(box, new LinearLayout.LayoutParams(-1, -2));
            line = Ui.hairline(c, 52);
            card.addView(line);
            Ui.ripple(c, box);
        }

        void bind(final Task t, boolean first, boolean last) {
            final Context c = itemView.getContext();
            card.setBackground(Ui.groupShape(c, first, last, R.color.surface));
            line.setVisibility(last ? View.GONE : View.VISIBLE);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            if (t.done) {
                ring.setColor(Ui.color(c, R.color.accent));
            } else {
                ring.setColor(0);
                ring.setStroke(Ui.dp(c, 2), Ui.color(c, R.color.text_hint));
            }
            check.setBackground(ring);
            tick.setVisibility(t.done ? View.VISIBLE : View.INVISIBLE);
            title.setText(t.title);
            title.setTextColor(Ui.color(c, t.done ? R.color.text_secondary : R.color.text_primary));
            title.setPaintFlags(t.done ? title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < t.priority; i++) p.append('!');
            prio.setText(p);
            prio.setVisibility(t.priority > 0 ? View.VISIBLE : View.GONE);
            String due = Fmt.taskDue(c, t);
            boolean overdue = !t.done && t.due > 0 && (t.hasTime ? t.due < System.currentTimeMillis() : t.due < Fmt.startOfDay(System.currentTimeMillis()));
            String notes = t.notes.isEmpty() ? "" : t.notes.split("\n")[0];
            String s = due.isEmpty() ? notes : (notes.isEmpty() ? due : due + c.getString(R.string.list_sep) + " " + notes);
            sub.setText(s);
            sub.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
            sub.setTextColor(Ui.color(c, overdue ? R.color.bad : R.color.text_secondary));
            check.setOnClickListener(v -> check.animate().scaleX(0.7f).scaleY(0.7f).setDuration(90).withEndAction(() -> {
                check.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                Tasks.toggle(c, t.id);
            }).start());
            box.setOnClickListener(v -> host.editTask(t.id));
        }
    }
}
