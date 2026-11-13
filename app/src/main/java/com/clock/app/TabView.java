package com.clock.app;

import android.content.Context;
import android.widget.LinearLayout;

/** One tab of the main screen. */
abstract class TabView extends LinearLayout {
    interface Host {
        void undo(CharSequence message, Runnable undo);

        void editAlarm(int id);

        void editTask(int id);

        void pickCity();
    }

    protected final Host host;

    TabView(Context c, Host h) {
        super(c);
        host = h;
        setOrientation(VERTICAL);
    }

    /** Re-reads the data and rebuilds what changed. */
    abstract void refresh();

    /** Called once a second while the tab is on screen. */
    void tick() {
    }

    void scrollToTop() {
    }
}
