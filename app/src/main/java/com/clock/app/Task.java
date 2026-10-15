package com.clock.app;

import org.json.JSONObject;

final class Task {
    static final int REPEAT_NONE = 0, REPEAT_DAILY = 1, REPEAT_WEEKLY = 2, REPEAT_MONTHLY = 3;

    int id;
    String title = "";
    String notes = "";
    /** Due instant (start of day when no time was chosen), 0 = no date. */
    long due;
    boolean hasTime;
    /** 0 none, 1 low, 2 medium, 3 high. */
    int priority;
    boolean done;
    long doneAt;
    boolean remind = true;
    int repeat;
    long created;

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("t", title).put("n", notes).put("d", due).put("ht", hasTime).put("p", priority)
                    .put("done", done).put("da", doneAt).put("rm", remind).put("rp", repeat).put("cr", created);
        } catch (Exception ignored) {
        }
        return o;
    }

    static Task fromJson(JSONObject o) {
        Task t = new Task();
        t.id = o.optInt("id");
        t.title = o.optString("t", "");
        t.notes = o.optString("n", "");
        t.due = o.optLong("d");
        t.hasTime = o.optBoolean("ht", false);
        t.priority = o.optInt("p", 0);
        t.done = o.optBoolean("done", false);
        t.doneAt = o.optLong("da");
        t.remind = o.optBoolean("rm", true);
        t.repeat = o.optInt("rp", 0);
        t.created = o.optLong("cr");
        return t;
    }
}
