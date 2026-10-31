package com.clock.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Alarm tab: a hero card with the next alarm and a live countdown ring, quick presets, the Bedtime and calendar cards,
 * then one rounded card per alarm (colour tag, big time, repeat dots, feature badges, countdown).
 * Swipe to delete with undo; long press for edit, duplicate, skip the next ring and delete.
 */
final class AlarmsView extends TabView {
    private static final int T_TOP = 0, T_ALARM = 1;

    private final RecyclerView rv;
    private final Header header;
    private final LinearLayout top;
    private final View empty;
    private final List<Alarm> list = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    // hero
    private final LinearLayout hero;
    private final TextView heroCaption, heroTime, heroAmpm, heroLine, heroLabel, heroResume;
    private final Countdown heroRing;

    // bedtime / calendar
    private final LinearLayout bedCard;
    private final TextView bedSub;
    private final LinearLayout calCard;
    private final TextView calSub;
    private final SwitchCompat bedSwitch;
    private final ImageView bedChevron;

    AlarmsView(Context c, Host h) {
        super(c, h);
        header = new Header(c, c.getString(R.string.nav_alarms));
        header.addButton(R.drawable.ic_brain, R.string.assistant, false, v -> AssistantActivity.open(c, false));
        header.addButton(R.drawable.ic_more, R.string.al_options, false, v -> optionsMenu());
        header.addButton(R.drawable.ic_add, R.string.new_alarm, true, v -> host.editAlarm(0));
        addView(header);

        top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));

        // ---------- hero ----------
        hero = new LinearLayout(c);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPaddingRelative(Ui.dp(c, 22), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        LinearLayout hcol = new LinearLayout(c);
        hcol.setOrientation(LinearLayout.VERTICAL);
        heroCaption = Ui.text(c, c.getString(R.string.al_next_title), 13, R.color.on_accent);
        heroCaption.setTypeface(Ui.medium());
        heroCaption.setAlpha(0.8f);
        hcol.addView(heroCaption);
        LinearLayout tr = new LinearLayout(c);
        tr.setGravity(Gravity.BOTTOM);
        heroTime = Ui.tabular(Ui.text(c, "", 58, R.color.on_accent));
        heroTime.setTypeface(Ui.thin());
        tr.addView(heroTime);
        heroAmpm = Ui.text(c, "", 18, R.color.on_accent);
        heroAmpm.setTypeface(Ui.light());
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
        al.setMarginStart(Ui.dp(c, 5));
        al.bottomMargin = Ui.dp(c, 11);
        tr.addView(heroAmpm, al);
        hcol.addView(tr);
        heroLine = Ui.text(c, "", 15, R.color.on_accent);
        heroLine.setSingleLine(true);
        heroLine.setEllipsize(TextUtils.TruncateAt.END);
        heroLine.setAlpha(0.92f);
        hcol.addView(heroLine);
        heroLabel = Ui.text(c, "", 13, R.color.on_accent);
        heroLabel.setSingleLine(true);
        heroLabel.setEllipsize(TextUtils.TruncateAt.END);
        heroLabel.setAlpha(0.7f);
        hcol.addView(heroLabel);
        heroResume = Ui.text(c, c.getString(R.string.al_resume), 14, R.color.accent);
        heroResume.setTypeface(Ui.medium());
        heroResume.setBackground(Ui.rect(Color.WHITE, Ui.dpf(c, 100)));
        heroResume.setPadding(Ui.dp(c, 16), Ui.dp(c, 7), Ui.dp(c, 16), Ui.dp(c, 7));
        heroResume.setVisibility(GONE);
        Ui.press(c, heroResume);
        heroResume.setOnClickListener(v -> {
            Ui.tick(v);
            Alarms.setPause(c, 0);
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.topMargin = Ui.dp(c, 10);
        hcol.addView(heroResume, rl);
        hero.addView(hcol, new LinearLayout.LayoutParams(0, -2, 1f));
        heroRing = new Countdown(c);
        hero.addView(heroRing, new LinearLayout.LayoutParams(Ui.dp(c, 76), Ui.dp(c, 76)));
        Ui.press(c, hero);
        hero.setOnClickListener(v -> {
            Alarm n = nextAlarm();
            if (n != null) host.editAlarm(n.id);
        });
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 10));
        top.addView(hero, hp);

        // ---------- quick presets ----------
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout chips = new LinearLayout(c);
        chips.setPaddingRelative(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0);
        boolean ar = Lang.isAr();
        final int workdays = ar ? Alarm.SUN_THU : Alarm.MON_FRI;
        final int weekend = ar ? (32 | 64) : (64 | 1);
        chips.addView(quickChip(c, R.string.al_quick_nap, () -> quickNap()));
        chips.addView(quickChip(c, R.string.al_quick_tomorrow, () -> quickAdd(7, 0, 0, "", true, false)));
        chips.addView(quickChip(c, R.string.al_quick_weekdays, () -> quickAdd(6, 30, workdays, "", false, false)));
        chips.addView(quickChip(c, R.string.al_quick_weekend, () -> quickAdd(9, 0, weekend, "", false, false)));
        hs.addView(chips);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.bottomMargin = Ui.dp(c, 10);
        top.addView(hs, hl);

        // ---------- bedtime ----------
        bedCard = new LinearLayout(c);
        bedCard.setOrientation(LinearLayout.HORIZONTAL);
        bedCard.setGravity(Gravity.CENTER_VERTICAL);
        bedCard.setBackground(Ui.rect(Ui.color(c, R.color.field), Ui.dpf(c, 20)));
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
        bp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        top.addView(bedCard, bp);

        // ---------- Semo Calendar link ----------
        calCard = new LinearLayout(c);
        calCard.setOrientation(LinearLayout.HORIZONTAL);
        calCard.setGravity(Gravity.CENTER_VERTICAL);
        calCard.setBackground(Ui.rect(Ui.color(c, R.color.field), Ui.dpf(c, 20)));
        calCard.setPaddingRelative(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36));
        cl.setMarginEnd(Ui.dp(c, 12));
        calCard.addView(Ui.tile(c, R.drawable.ic_calendar, R.color.bad), cl);
        LinearLayout ccol = new LinearLayout(c);
        ccol.setOrientation(LinearLayout.VERTICAL);
        TextView calTitle = Ui.text(c, c.getString(R.string.next_event), 17, R.color.text_primary);
        calTitle.setTypeface(Ui.medium());
        ccol.addView(calTitle);
        calSub = Ui.text(c, "", 13, R.color.text_secondary);
        calSub.setSingleLine(true);
        ccol.addView(calSub);
        calCard.addView(ccol, new LinearLayout.LayoutParams(0, -2, 1f));
        Ui.press(c, calCard);
        calCard.setOnClickListener(v -> {
            Semo.Sibling cs = Semo.byRole(c, Semo.ROLE_CALENDAR);
            if (cs != null) Semo.open(c, cs.pkg);
        });
        calCard.setVisibility(GONE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        top.addView(calCard, cp);

        empty = Ui.empty(c, R.drawable.ic_alarm, c.getString(R.string.no_alarms), c.getString(R.string.no_alarms_sub));
        top.addView(empty, new LinearLayout.LayoutParams(-1, Ui.dp(c, 280)));

        rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setAdapter(adapter);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setItemViewCacheSize(6);
        rv.getRecycledViewPool().setMaxRecycledViews(T_TOP, 0);
        header.bindScroll(rv);
        new ItemTouchHelper(new SwipeDelete(c, p -> deleteAt(p - 1)).filter(p -> p > 0)).attachToRecyclerView(rv);
        addView(rv, new LayoutParams(-1, 0, 1f));
        refresh();
    }

    // ------------------------------------------------------------------ quick add

    private TextView quickChip(Context c, int label, Runnable r) {
        TextView t = Ui.chipView(c, "+  " + c.getString(label), v -> {
            Ui.tick(v);
            r.run();
        });
        t.setBackground(Ui.rect(Ui.color(c, R.color.accent_soft), Ui.dpf(c, 100)));
        t.setTextColor(Ui.color(c, R.color.accent_text));
        return t;
    }

    private void quickNap() {
        Context c = getContext();
        Calendar k = Calendar.getInstance();
        k.add(Calendar.MINUTE, 20);
        quickAdd(k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE), 0, c.getString(R.string.al_nap), false, true);
    }

    private void quickAdd(int hour, int minute, int days, String label, boolean tomorrow, boolean deleteAfter) {
        final Context c = getContext();
        Alarm a = new Alarm();
        a.hour = hour;
        a.minute = minute;
        a.days = days;
        a.label = label;
        a.vibrate = Store.defaultVibrate(c);
        a.ramp = Store.defaultRamp(c);
        a.deleteAfter = deleteAfter;
        if (tomorrow) {
            Calendar k = Calendar.getInstance();
            k.add(Calendar.DAY_OF_YEAR, 1);
            a.date = k.getTimeInMillis();
        }
        Alarms.save(c, a);
        final Alarm saved = a;
        host.undo(c.getString(R.string.al_added) + c.getString(R.string.list_sep) + " " + Fmt.hm(c, hour, minute) + Fmt.ampm(c, hour),
                () -> Alarms.delete(c, saved.id));
    }

    // ------------------------------------------------------------------ menus

    private void optionsMenu() {
        final Context c = getContext();
        final boolean paused = Alarms.paused(c);
        List<String> labels = new ArrayList<>();
        labels.add(c.getString(paused ? R.string.al_resume_now : R.string.al_pause));
        labels.add(c.getString(R.string.al_all_on));
        labels.add(c.getString(R.string.al_all_off));
        Dlg.choose(c, R.string.al_options, labels.toArray(new String[0]), -1, i -> {
            if (i == 0) {
                if (paused) Alarms.setPause(c, 0);
                else pauseMenu();
            } else if (i == 1) {
                Alarms.setAllEnabled(c, true);
            } else if (i == 2) {
                Alarms.setAllEnabled(c, false);
            }
        });
    }

    private void pauseMenu() {
        final Context c = getContext();
        String[] labels = {c.getString(R.string.al_pause_1d), c.getString(R.string.al_pause_3d), c.getString(R.string.al_pause_1w)};
        final int[] days = {1, 3, 7};
        Dlg.choose(c, R.string.al_pause, labels, -1, i -> {
            Calendar k = Calendar.getInstance();
            k.add(Calendar.DAY_OF_YEAR, days[i]);
            k.set(Calendar.HOUR_OF_DAY, 0);
            k.set(Calendar.MINUTE, 0);
            k.set(Calendar.SECOND, 0);
            k.set(Calendar.MILLISECOND, 0);
            Alarms.setPause(c, k.getTimeInMillis());
        });
    }

    private void menu(final Alarm a) {
        final Context c = getContext();
        final boolean canSkip = a.enabled && a.days != 0;
        final boolean skipping = a.skipUntil > System.currentTimeMillis();
        List<String> labels = new ArrayList<>();
        labels.add(c.getString(R.string.edit_alarm));
        labels.add(c.getString(R.string.al_duplicate));
        if (canSkip) labels.add(c.getString(skipping ? R.string.skip_cancel : R.string.skip_next));
        labels.add(c.getString(R.string.delete_alarm));
        final int skipIdx = canSkip ? 2 : -1;
        final int delIdx = labels.size() - 1;
        Dlg.choose(c, R.string.alarm, labels.toArray(new String[0]), -1, i -> {
            if (i == 0) {
                host.editAlarm(a.id);
            } else if (i == 1) {
                Alarms.duplicate(c, a.id);
                Ui.toast(c, c.getString(R.string.al_duplicated));
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

    private void openBedtime() {
        Context c = getContext();
        if (c instanceof Activity) Ui.openSheet((Activity) c, new Intent(c, BedtimeActivity.class));
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

    // ------------------------------------------------------------------ state

    private Alarm nextAlarm() {
        Context c = getContext();
        long now = System.currentTimeMillis(), best = 0;
        Alarm pick = null;
        for (Alarm a : list) {
            if (!a.enabled) continue;
            long t = Alarms.nextOf(c, a, now);
            if (t > 0 && (best == 0 || t < best)) {
                best = t;
                pick = a;
            }
        }
        return pick;
    }

    private int heroState = -1;

    private void heroBackground(Context c, int state) {
        if (state == heroState) return;
        heroState = state;
        int a = Ui.color(c, state == 1 ? R.color.warn : R.color.accent);
        int b = ColorUtils.blendARGB(a, Ui.color(c, state == 1 ? R.color.bad : R.color.indigo), state == 1 ? 0.5f : 0.65f);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        bg.setCornerRadius(Ui.dpf(c, 28));
        hero.setBackground(bg);
    }

    private void updateHero() {
        Context c = getContext();
        long now = System.currentTimeMillis();
        boolean paused = Alarms.paused(c);
        Alarm n = paused ? null : nextAlarm();
        heroResume.setVisibility(paused ? VISIBLE : GONE);
        heroBackground(c, paused ? 1 : 0);
        if (paused) {
            long until = Store.pauseUntil(c);
            heroCaption.setText(R.string.al_pause);
            heroTime.setText("--:--");
            heroAmpm.setText("");
            heroLine.setText(c.getString(R.string.al_paused_until, Fmt.dayLabel(c, until)));
            heroLabel.setVisibility(GONE);
            heroRing.set(0f, R.drawable.ic_pause_fill);
        } else if (n == null) {
            heroCaption.setText(R.string.al_next_title);
            heroTime.setText("--:--");
            heroAmpm.setText("");
            heroLine.setText(R.string.al_none_active);
            heroLabel.setVisibility(GONE);
            heroRing.set(0f, R.drawable.ic_alarm);
        } else {
            long t = Alarms.nextOf(c, n, now);
            heroCaption.setText(R.string.al_next_title);
            heroTime.setText(Fmt.hm(c, n.hour, n.minute));
            heroAmpm.setText(Fmt.ampm(c, n.hour));
            heroLine.setText(Fmt.untilText(c, t - now) + c.getString(R.string.list_sep) + " " + Fmt.dayLabel(c, t));
            heroLabel.setText(n.label);
            heroLabel.setVisibility(n.label.isEmpty() ? GONE : VISIBLE);
            heroRing.set(1f - Math.min(1f, (t - now) / 86400000f), R.drawable.ic_alarm);
        }
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

    private void updateCalendar() {
        Context c = getContext();
        Semo.CalSummary k = Semo.hasCalendar(c) ? Semo.calendarSummary(c) : null;
        if (k == null || k.nextEvent <= System.currentTimeMillis()) {
            calCard.setVisibility(GONE);
            return;
        }
        calSub.setText(Fmt.dayLabel(c, k.nextEvent) + " \u00b7 " + Fmt.time(c, k.nextEvent, java.util.TimeZone.getDefault(), false)
                + (k.today > 0 ? " \u00b7 " + c.getString(R.string.events_today_fmt, k.today) : ""));
        calCard.setVisibility(VISIBLE);
    }

    @Override
    void refresh() {
        list.clear();
        list.addAll(Alarms.all(getContext()));
        adapter.notifyDataSetChanged();
        empty.setVisibility(list.isEmpty() ? VISIBLE : GONE);
        updateHero();
        updateBedtime();
        updateCalendar();
    }

    @Override
    void tick() {
        updateHero();
        // refresh the "in 6 h" lines once a minute only
        if (Calendar.getInstance().get(Calendar.SECOND) == 0 && adapter.getItemCount() > 1) {
            adapter.notifyItemRangeChanged(1, adapter.getItemCount() - 1, "t");
        }
    }

    @Override
    void scrollToTop() {
        rv.smoothScrollToPosition(0);
    }

    // ------------------------------------------------------------------ adapter

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return list.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? T_TOP : T_ALARM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == T_TOP) {
                if (top.getParent() instanceof ViewGroup) ((ViewGroup) top.getParent()).removeView(top);
                return new RecyclerView.ViewHolder(top) {
                };
            }
            return new VH(getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
            if (h instanceof VH) ((VH) h).bind(list.get(p - 1));
        }
    }

    private final class VH extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final TextView time, ampm, label, when, once;
        final View dot;
        final SwitchCompat sw;
        final LinearLayout dayRow, badges;
        final TextView[] days = new TextView[7];
        final int[] dayBit = new int[7];

        VH(Context c) {
            super(new LinearLayout(c));
            LinearLayout outer = (LinearLayout) itemView;
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            outer.setBackgroundColor(Ui.color(c, R.color.bg));
            card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.rect(Ui.color(c, R.color.field), Ui.dpf(c, 24)));
            card.setClipToOutline(true);
            card.setPaddingRelative(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 16));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
            outer.addView(card, cp);

            LinearLayout r1 = new LinearLayout(c);
            r1.setGravity(Gravity.CENTER_VERTICAL);
            dot = new View(c);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 9), Ui.dp(c, 9));
            dl.setMarginEnd(Ui.dp(c, 8));
            r1.addView(dot, dl);
            label = Ui.text(c, "", 14, R.color.text_secondary);
            label.setTypeface(Ui.medium());
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            r1.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            sw = Ui.styledSwitch(c, false);
            r1.addView(sw);
            card.addView(r1);

            LinearLayout r2 = new LinearLayout(c);
            r2.setGravity(Gravity.BOTTOM);
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
            r2.addView(tr, new LinearLayout.LayoutParams(0, -2, 1f));
            when = Ui.text(c, "", 13, R.color.accent_text);
            when.setTypeface(Ui.medium());
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
            wl.bottomMargin = Ui.dp(c, 10);
            r2.addView(when, wl);
            card.addView(r2);

            dayRow = new LinearLayout(c);
            dayRow.setGravity(Gravity.CENTER_VERTICAL);
            String[] initials = c.getResources().getStringArray(R.array.day_initials);
            int first = Calendar.getInstance().getFirstDayOfWeek();
            for (int i = 0; i < 7; i++) {
                int dow = ((first - 1 + i) % 7) + 1;
                dayBit[i] = 1 << (dow - 1);
                TextView t = new TextView(c);
                t.setText(initials[dow - 1]);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(11);
                t.setTypeface(Ui.medium());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26));
                lp.setMarginEnd(Ui.dp(c, 6));
                dayRow.addView(t, lp);
                days[i] = t;
            }
            once = Ui.text(c, "", 14, R.color.text_secondary);
            dayRow.addView(once);
            LinearLayout.LayoutParams dr = new LinearLayout.LayoutParams(-2, -2);
            dr.topMargin = Ui.dp(c, 2);
            card.addView(dayRow, dr);

            badges = new LinearLayout(c);
            badges.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
            bl.topMargin = Ui.dp(c, 10);
            card.addView(badges, bl);

            Ui.ripple(c, card);
            Ui.press(c, card);
        }

        private TextView badge(Context c, String text) {
            TextView t = Ui.text(c, text, 11, R.color.text_secondary);
            t.setTypeface(Ui.medium());
            t.setBackground(Ui.rect(Ui.color(c, R.color.bg), Ui.dpf(c, 100)));
            t.setPadding(Ui.dp(c, 9), Ui.dp(c, 3), Ui.dp(c, 9), Ui.dp(c, 3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(c, 6));
            t.setLayoutParams(lp);
            return t;
        }

        void bind(final Alarm a) {
            final Context c = itemView.getContext();
            int tag = Alarm.COLORS[a.color];
            dot.setBackground(Ui.oval(tag));
            label.setText(a.label.isEmpty() ? c.getString(R.string.alarm) : a.label);
            time.setText(Fmt.hm(c, a.hour, a.minute));
            ampm.setText(Fmt.ampm(c, a.hour));

            boolean skipping = a.enabled && a.skipUntil > System.currentTimeMillis();
            long now = System.currentTimeMillis();
            long t = a.enabled ? Alarms.nextOf(c, a, now) : 0;
            if (skipping) {
                when.setText(R.string.skipping_next);
                when.setTextColor(Ui.color(c, R.color.warn));
            } else if (t > 0) {
                when.setText(Fmt.untilText(c, t - now));
                when.setTextColor(Ui.color(c, R.color.accent_text));
            } else {
                when.setText("");
            }

            boolean repeating = a.days != 0;
            for (int i = 0; i < 7; i++) {
                days[i].setVisibility(repeating ? VISIBLE : GONE);
                boolean on = (a.days & dayBit[i]) != 0;
                days[i].setBackground(Ui.oval(on ? tag : Ui.color(c, R.color.neutral_soft)));
                days[i].setTextColor(on ? Color.WHITE : Ui.color(c, R.color.text_secondary));
            }
            once.setVisibility(repeating ? GONE : VISIBLE);
            if (!repeating) {
                String d = a.date > 0 ? Fmt.dayLabel(c, a.date) : (t > 0 ? Fmt.dayLabel(c, t) : "");
                once.setText(c.getString(R.string.once) + (d.isEmpty() ? "" : c.getString(R.string.list_sep) + " " + d));
            }

            badges.removeAllViews();
            if (a.challenge) badges.addView(badge(c, c.getString(R.string.al_badge_challenge)));
            if (a.ramp) badges.addView(badge(c, c.getString(R.string.al_badge_ramp)));
            if (a.snooze) {
                int m = a.snoozeMin > 0 ? a.snoozeMin : Store.snoozeMin(c);
                badges.addView(badge(c, c.getString(R.string.al_badge_snooze, m)));
            }
            if (a.vibrate) badges.addView(badge(c, c.getString(R.string.al_badge_vibrate)));
            badges.setVisibility(badges.getChildCount() == 0 ? GONE : VISIBLE);

            float alpha = a.enabled ? 1f : 0.45f;
            for (int i = 0; i < card.getChildCount(); i++) {
                View v = card.getChildAt(i);
                if (i == 0) continue;
                v.setAlpha(alpha);
            }
            label.setAlpha(alpha);
            dot.setAlpha(alpha);

            sw.setOnCheckedChangeListener(null);
            sw.setChecked(a.enabled);
            sw.setOnCheckedChangeListener((v, on) -> {
                Ui.tick(v);
                Alarms.setEnabled(c, a.id, on);
                if (on) {
                    Alarm fresh = Alarms.get(c, a.id);
                    long nt = fresh == null ? 0 : Alarms.nextOf(c, fresh, System.currentTimeMillis());
                    if (nt > 0) Ui.toast(c, c.getString(R.string.alarm_set_in, Fmt.untilText(c, nt - System.currentTimeMillis())));
                }
            });
            card.setOnClickListener(v -> host.editAlarm(a.id));
            card.setOnLongClickListener(v -> {
                Ui.tick(v);
                menu(a);
                return true;
            });
        }
    }

    /** Ring that fills as the next alarm gets closer (full circle = rings now), with an icon in the middle. */
    private static final class Countdown extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float progress;
        private android.graphics.drawable.Drawable icon;
        private int iconRes;

        Countdown(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(float progress, int iconRes) {
            if (iconRes != this.iconRes) {
                this.iconRes = iconRes;
                icon = getContext().getDrawable(iconRes);
                if (icon != null) {
                    icon = icon.mutate();
                    icon.setTint(Color.WHITE);
                }
            }
            this.progress = progress;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = Ui.dpf(getContext(), 5);
            r.set(w, w, getWidth() - w, getHeight() - w);
            p.setStrokeWidth(w);
            p.setColor(0x40FFFFFF);
            cv.drawArc(r, 0, 360, false, p);
            p.setColor(Color.WHITE);
            cv.drawArc(r, -90, 360f * progress, false, p);
            if (icon != null) {
                int s = (int) Ui.dpf(getContext(), 26);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                icon.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
                icon.draw(cv);
            }
        }
    }
}
