package com.clock.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.ArrayList;

/** Offline assistant: type or speak a sentence, it creates tasks / alarms / timers and answers about the day and prayers. */
public class AssistantActivity extends BaseActivity {
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private boolean taskMode;

    static void open(Context c, boolean taskMode) {
        Intent i = new Intent(c, AssistantActivity.class).putExtra("task", taskMode);
        if (c instanceof Activity) {
            Ui.openSheet((Activity) c, i);
        } else {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        }
    }

    private final ActivityResultLauncher<Intent> voice = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                Intent d = r.getData();
                if (r.getResultCode() == RESULT_OK && d != null) {
                    ArrayList<String> l = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (l != null && !l.isEmpty()) submit(l.get(0));
                }
            });

    @Override
    protected int transition() {
        return SHEET;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        taskMode = getIntent().getBooleanExtra("task", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.sheetBar(this, getString(R.string.assistant), getString(R.string.close), v -> finish(),
                getString(R.string.ai_clear), v -> reset()));
        TextView sub = Ui.text(this, getString(R.string.ai_offline_note), 12, R.color.text_secondary);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 6));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 10));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 6));
        chips.addView(Ui.chipView(this, getString(R.string.ai_chip_plan), v -> submit(getString(R.string.ai_chip_plan))));
        chips.addView(Ui.chipView(this, getString(R.string.ai_chip_today), v -> submit(getString(R.string.ai_chip_today))));
        chips.addView(Ui.chipView(this, getString(R.string.ai_chip_late), v -> submit(getString(R.string.ai_chip_late))));
        chips.addView(Ui.chipView(this, getString(R.string.ai_chip_prayer), v -> submit(getString(R.string.ai_chip_prayer))));
        chips.addView(Ui.chipView(this, getString(R.string.ai_chip_alarm), v -> submit(getString(R.string.ai_chip_alarm))));
        chips.addView(Ui.chipView(this, getString(R.string.ai_ex_task), v -> fill(getString(R.string.ai_ex_task))));
        chips.addView(Ui.chipView(this, getString(R.string.ai_ex_alarm), v -> fill(getString(R.string.ai_ex_alarm))));
        chips.addView(Ui.chipView(this, getString(R.string.ai_ex_timer), v -> fill(getString(R.string.ai_ex_timer))));
        chips.addView(Ui.chipView(this, getString(R.string.ai_ex_prayer), v -> fill(getString(R.string.ai_ex_prayer))));
        hs.addView(chips);
        root.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 10));
        input = new EditText(this);
        input.setHint(R.string.ai_hint);
        input.setHintTextColor(Ui.color(this, R.color.text_hint));
        input.setTextColor(Ui.color(this, R.color.text_primary));
        input.setTextSize(16);
        input.setBackground(Ui.rect(Ui.color(this, R.color.surface), Ui.dpf(this, 22)));
        input.setPaddingRelative(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND) {
                submit(input.getText().toString());
                return true;
            }
            return false;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(round(R.drawable.ic_mic, false, v -> listen()), lp(44, 8));
        bar.addView(round(R.drawable.ic_send, true, v -> submit(input.getText().toString())), lp(44, 8));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        greet();
        if (taskMode) {
            input.setHint(R.string.ai_hint_task);
            input.requestFocus();
        }
    }

    private LinearLayout.LayoutParams lp(int sizeDp, int startMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp));
        p.setMarginStart(Ui.dp(this, startMarginDp));
        return p;
    }

    private View round(int icon, boolean filled, View.OnClickListener l) {
        ImageView v = new ImageView(this);
        v.setImageResource(icon);
        v.setScaleType(ImageView.ScaleType.CENTER);
        int p = Ui.dp(this, 11);
        v.setPadding(p, p, p, p);
        v.setImageTintList(ColorStateList.valueOf(filled ? Color.WHITE : Ui.color(this, R.color.accent)));
        v.setBackground(Ui.oval(filled ? Ui.color(this, R.color.accent) : Ui.color(this, R.color.surface)));
        v.setContentDescription(getString(filled ? R.string.ai_send : R.string.ai_voice));
        v.setOnClickListener(l);
        Ui.press(this, v);
        return v;
    }

    private void greet() {
        list.removeAllViews();
        String hi = getString(taskMode ? R.string.ai_hi_task : R.string.ai_hi);
        String insight = Smart.insight(this);
        bot(insight.isEmpty() ? hi : hi + "\n\n" + insight, null);
    }

    private void reset() {
        greet();
    }

    private void fill(String s) {
        input.setText(s);
        input.setSelection(s.length());
        input.requestFocus();
    }

    private void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Lang.isAr() ? "ar" : "en")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.ai_listen));
        try {
            voice.launch(i);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, getString(R.string.ai_voice_fail));
        } catch (Exception e) {
            Ui.toast(this, getString(R.string.ai_voice_fail));
        }
    }

    private void submit(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return;
        input.setText("");
        user(text);
        Smart.Reply r;
        try {
            r = Smart.answer(this, text);
            if (r == null) {
                Smart.Result res = Smart.parse(this, text);
                boolean go = res.kind == Smart.TIMER || res.kind == Smart.ALARM
                        || (res.kind == Smart.TASK && (taskMode || res.explicit || res.due > 0));
                if (go) {
                    r = Smart.create(this, text, res);
                } else {
                    r = new Smart.Reply();
                    r.text = getString(R.string.ai_unknown);
                }
            }
        } catch (Throwable t) {
            r = new Smart.Reply();
            r.text = getString(R.string.ai_unknown);
        }
        bot(r.text, r);
    }

    private TextView bubble(CharSequence text, boolean mine) {
        TextView t = Ui.text(this, text, 16, mine ? R.color.on_accent : R.color.text_primary);
        if (mine) t.setTextColor(Color.WHITE);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        t.setBackground(Ui.rect(mine ? Ui.color(this, R.color.accent) : Ui.color(this, R.color.surface), Ui.dpf(this, 18)));
        t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        return t;
    }

    private void user(String text) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.END;
        p.topMargin = Ui.dp(this, 8);
        list.addView(bubble(text, true), p);
        down();
    }

    private void bot(String text, Smart.Reply r) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(bubble(text, false), new LinearLayout.LayoutParams(-2, -2));
        if (r != null && (r.undo != null || r.action != null)) {
            LinearLayout row = new LinearLayout(this);
            row.setPaddingRelative(Ui.dp(this, 6), Ui.dp(this, 4), 0, 0);
            if (r.action != null) {
                final Runnable act = r.action;
                TextView a = Ui.navText(this, r.actionLabel == null ? "" : r.actionLabel, false);
                a.setTextSize(14);
                a.setMinHeight(Ui.dp(this, 34));
                a.setPaddingRelative(Ui.dp(this, 8), 0, Ui.dp(this, 14), 0);
                a.setOnClickListener(v -> act.run());
                row.addView(a);
            }
            if (r.undo != null) {
                final Runnable undo = r.undo;
                final TextView u = Ui.navText(this, getString(R.string.undo), false);
                u.setTextSize(14);
                u.setMinHeight(Ui.dp(this, 34));
                u.setPaddingRelative(Ui.dp(this, 8), 0, Ui.dp(this, 14), 0);
                u.setOnClickListener(v -> {
                    undo.run();
                    u.setEnabled(false);
                    u.setAlpha(0.4f);
                    u.setText(R.string.ai_undone);
                });
                row.addView(u);
            }
            box.addView(row);
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.START;
        p.topMargin = Ui.dp(this, 8);
        list.addView(box, p);
        Ui.enter(box, 0);
        down();
    }

    private void down() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
}
