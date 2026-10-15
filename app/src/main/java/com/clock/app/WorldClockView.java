package com.clock.app;

import android.content.Context;
import android.graphics.Typeface;
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

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** World clock: analog local clock on top, then one big-time row per city, swipe to delete. */
final class WorldClockView extends TabView {
    private final RecyclerView rv;
    private LinearLayout prayerCard;
    private TextView prayerTitle, prayerSub;
    private long lastPrayerUi;
    private final List<String> zones = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    WorldClockView(Context c, Host h) {
        super(c, h);
        Header hd = new Header(c, c.getString(R.string.nav_clock));
        hd.addButton(R.drawable.ic_standby, R.string.standby, false, v -> openScreen(StandByActivity.class, true));
        hd.addButton(R.drawable.ic_globe, R.string.converter, false, v -> openScreen(ConverterActivity.class, false));
        hd.addButton(R.drawable.ic_add, R.string.add_city, true, v -> host.pickCity());
        addView(hd);
        prayerCard = new LinearLayout(c);
        prayerCard.setOrientation(LinearLayout.HORIZONTAL);
        prayerCard.setGravity(Gravity.CENTER_VERTICAL);
        prayerCard.setBackground(Ui.rect(Ui.color(c, R.color.field), Ui.dpf(c, 16)));
        prayerCard.setPaddingRelative(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34));
        ptl.setMarginEnd(Ui.dp(c, 12));
        prayerCard.addView(Ui.tile(c, R.drawable.ic_prayer, R.color.ok), ptl);
        LinearLayout pcol = new LinearLayout(c);
        pcol.setOrientation(LinearLayout.VERTICAL);
        prayerTitle = Ui.text(c, "", 16, R.color.text_primary);
        prayerTitle.setTypeface(Ui.medium());
        prayerTitle.setSingleLine(true);
        pcol.addView(prayerTitle);
        prayerSub = Ui.text(c, "", 13, R.color.text_secondary);
        prayerSub.setSingleLine(true);
        pcol.addView(prayerSub);
        prayerCard.addView(pcol, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView pch = new ImageView(c);
        pch.setImageResource(R.drawable.ic_chevron);
        pch.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.text_hint)));
        prayerCard.addView(pch, new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
        Ui.press(c, prayerCard);
        prayerCard.setOnClickListener(v -> c.startActivity(new android.content.Intent(c, PrayerActivity.class)));
        LinearLayout.LayoutParams pcl = new LinearLayout.LayoutParams(-1, -2);
        pcl.setMargins(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 8));
        addView(prayerCard, pcl);
        rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setAdapter(adapter);
        rv.setClipToPadding(false);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hd.bindScroll(rv);
        new ItemTouchHelper(new SwipeDelete(c, this::deleteAt).filter(p -> p > 0).mover(this::move)).attachToRecyclerView(rv);
        addView(rv, new LayoutParams(-1, 0, 1f));
        refresh();
    }

    static List<String> loadZones(Context c) {
        if (!Store.seeded(c)) {
            JSONArray a = new JSONArray();
            a.put("Europe/London").put("America/New_York").put("Asia/Tokyo");
            Store.putArray(c, Store.CITIES, a);
            Store.setSeeded(c);
        }
        JSONArray a = Store.array(c, Store.CITIES);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    static void saveZones(Context c, List<String> z) {
        JSONArray a = new JSONArray();
        for (String s : z) a.put(s);
        Store.putArray(c, Store.CITIES, a);
    }

    static void addZone(Context c, String zone) {
        List<String> z = loadZones(c);
        if (!z.contains(zone)) z.add(zone);
        saveZones(c, z);
        Bus.changed();
    }

    private void openScreen(Class<?> cls, boolean zoom) {
        Context c = getContext();
        if (!(c instanceof android.app.Activity)) return;
        android.content.Intent i = new android.content.Intent(c, cls);
        if (zoom) Ui.openZoom((android.app.Activity) c, i);
        else Ui.openSheet((android.app.Activity) c, i);
    }

    /** Long press and drag reorders the cities (the analog clock on top stays put). */
    private boolean move(int from, int to) {
        int a = from - 1, b = to - 1;
        if (a < 0 || b < 0 || a >= zones.size() || b >= zones.size()) return false;
        zones.add(b, zones.remove(a));
        adapter.notifyItemMoved(from, to);
        saveZones(getContext(), zones);
        return true;
    }

    private void updatePrayerCard() {
        Context c = getContext();
        lastPrayerUi = System.currentTimeMillis();
        PrayerTimes.Cfg g = PrayerTimes.cfg(c);
        PrayerTimes.Next n = g.configured ? PrayerTimes.next(c) : null;
        if (n == null) {
            prayerTitle.setText(R.string.prayer_times);
            prayerSub.setText(g.configured ? g.place : c.getString(R.string.prayer_setup_sub));
            return;
        }
        String[] names = c.getResources().getStringArray(R.array.prayer_names);
        prayerTitle.setText(c.getString(R.string.next_prayer) + c.getString(R.string.list_sep) + " " + names[n.idx]);
        prayerSub.setText(PrayerTimes.label(c, n.at, g.zone) + " \u00B7 " + Fmt.untilText(c, n.at - lastPrayerUi));
    }

    private void deleteAt(int pos) {
        final int idx = pos - 1;
        if (idx < 0 || idx >= zones.size()) {
            adapter.notifyDataSetChanged();
            return;
        }
        final String zone = zones.remove(idx);
        saveZones(getContext(), zones);
        adapter.notifyItemRemoved(pos);
        host.undo(getContext().getString(R.string.city_removed, Cities.name(zone)), () -> {
            List<String> z = loadZones(getContext());
            z.add(Math.min(idx, z.size()), zone);
            saveZones(getContext(), z);
            Bus.changed();
        });
    }

    @Override
    void refresh() {
        updatePrayerCard();
        zones.clear();
        zones.addAll(loadZones(getContext()));
        adapter.notifyDataSetChanged();
    }

    @Override
    void tick() {
        if (System.currentTimeMillis() - lastPrayerUi > 20000) updatePrayerCard();
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
            if (vh instanceof Row) ((Row) vh).time();
            else if (vh instanceof Hero) ((Hero) vh).time();
        }
    }

    @Override
    void scrollToTop() {
        rv.smoothScrollToPosition(0);
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return zones.size() + 1;
        }

        @Override
        public int getItemViewType(int p) {
            return p == 0 ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return type == 0 ? new Hero(getContext()) : new Row(getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
            if (h instanceof Row) ((Row) h).bind(zones.get(p - 1), p == zones.size());
            else ((Hero) h).time();
        }
    }

    private static final class Hero extends RecyclerView.ViewHolder {
        final TextView digital, date, city;

        Hero(Context c) {
            super(new LinearLayout(c));
            LinearLayout l = (LinearLayout) itemView;
            l.setOrientation(LinearLayout.VERTICAL);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            l.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 10));
            l.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            AnalogClockView clock = new AnalogClockView(c);
            l.addView(clock, new LinearLayout.LayoutParams(Ui.dp(c, 230), Ui.dp(c, 230)));
            city = Ui.text(c, Cities.name(TimeZone.getDefault().getID()), 15, R.color.text_secondary);
            city.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.topMargin = Ui.dp(c, 10);
            l.addView(city, cl);
            digital = Ui.tabular(Ui.text(c, "", 44, R.color.text_primary));
            digital.setTypeface(Ui.thin());
            digital.setGravity(Gravity.CENTER);
            l.addView(digital, new LinearLayout.LayoutParams(-2, -2));
            date = Ui.text(c, "", 15, R.color.accent);
            date.setGravity(Gravity.CENTER);
            date.setTypeface(Ui.medium());
            l.addView(date, new LinearLayout.LayoutParams(-2, -2));
            l.addView(Ui.hairline(c, 0), lpHair(c));
        }

        private static LinearLayout.LayoutParams lpHair(Context c) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.hairPx(c));
            lp.topMargin = Ui.dp(c, 14);
            lp.setMarginStart(Ui.dp(c, 20));
            return lp;
        }

        void time() {
            Context c = itemView.getContext();
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            String ap = Fmt.ampm(c, cal.get(Calendar.HOUR_OF_DAY));
            String t = Fmt.time(c, now, TimeZone.getDefault(), Store.showSeconds(c));
            digital.setText(ap.isEmpty() ? t : t + " " + ap);
            date.setText(Fmt.date(c, now, "EEEE d MMMM"));
        }
    }

    private static final class Row extends RecyclerView.ViewHolder {
        final TextView sub, name, time, ampm;
        final ImageView sun;
        TimeZone tz = TimeZone.getDefault();

        Row(Context c) {
            super(new LinearLayout(c));
            LinearLayout l = (LinearLayout) itemView;
            l.setOrientation(LinearLayout.VERTICAL);
            l.setBackgroundColor(Ui.color(c, R.color.bg));
            l.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 12));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout subRow = new LinearLayout(c);
            subRow.setGravity(Gravity.CENTER_VERTICAL);
            sun = new ImageView(c);
            sun.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.text_secondary)));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14));
            sl.setMarginEnd(Ui.dp(c, 5));
            subRow.addView(sun, sl);
            sub = Ui.text(c, "", 13, R.color.text_secondary);
            subRow.addView(sub);
            col.addView(subRow);
            name = Ui.text(c, "", 22, R.color.text_primary);
            name.setSingleLine(true);
            col.addView(name);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            time = Ui.tabular(Ui.text(c, "", 40, R.color.text_primary));
            time.setTypeface(Ui.thin());
            row.addView(time);
            ampm = Ui.text(c, "", 16, R.color.text_secondary);
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
            al.setMarginStart(Ui.dp(c, 4));
            row.addView(ampm, al);
            l.addView(row, new LinearLayout.LayoutParams(-1, -2));
            l.addView(Ui.hairline(c, 20));
        }

        void bind(String zone, boolean last) {
            tz = TimeZone.getTimeZone(zone);
            name.setText(Cities.name(zone));
            time();
        }

        void time() {
            Context c = itemView.getContext();
            long now = System.currentTimeMillis();
            time.setText(Fmt.time(c, now, tz, false));
            Calendar cal = Calendar.getInstance(tz);
            cal.setTimeInMillis(now);
            ampm.setText(Fmt.ampm(c, cal.get(Calendar.HOUR_OF_DAY)));
            sub.setText(Fmt.zoneLine(c, tz, now));
            sun.setImageResource(Fmt.isDay(tz, now) ? R.drawable.ic_sun : R.drawable.ic_moon);
        }
    }
}
