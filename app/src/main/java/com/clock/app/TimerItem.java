package com.clock.app;

import org.json.JSONObject;

final class TimerItem {
    static final int RUNNING = 1, PAUSED = 2;

    int id;
    String label = "";
    long total;
    int state = RUNNING;
    /** Wall clock instant the timer ends (running). */
    long endAt;
    /** Time left (paused). */
    long remaining;
    /** When this timer ends and is stopped, a timer of this length starts (focus break). 0 = none. */
    long chain;
    String chainLabel = "";

    long left(long now) {
        return state == RUNNING ? Math.max(0, endAt - now) : remaining;
    }

    float fraction(long now) {
        if (total <= 0) return 0f;
        return Math.max(0f, Math.min(1f, left(now) / (float) total));
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("l", label).put("t", total).put("st", state).put("e", endAt).put("r", remaining)
                    .put("c", chain).put("cl", chainLabel);
        } catch (Exception ignored) {
        }
        return o;
    }

    static TimerItem fromJson(JSONObject o) {
        TimerItem t = new TimerItem();
        t.id = o.optInt("id");
        t.label = o.optString("l", "");
        t.total = o.optLong("t");
        t.state = o.optInt("st", RUNNING);
        t.endAt = o.optLong("e");
        t.remaining = o.optLong("r");
        t.chain = o.optLong("c");
        t.chainLabel = o.optString("cl", "");
        return t;
    }
}
