package com.clock.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Choose a reciter and download surahs for offline listening. */
public class QuranAudioActivity extends BaseActivity {
    private QuranData d;
    private LinearLayout reciterRow;
    private final List<LinearLayout> rows = new ArrayList<>();
    private boolean built;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        d = QuranData.peek(this);
        if (d == null) {
            Ui.toast(this, getString(R.string.quran_audio_need_text));
            startActivity(new Intent(this, QuranActivity.class));
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.quran_audio));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        hd.setSubtitle(getString(R.string.quran_audio_sub));
        root.addView(hd);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        hd.bindScroll(sv);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.dp(this, 30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        col.addView(Ui.sectionTitle(this, getString(R.string.quran_reciter)));
        LinearLayout g = Ui.group(this);
        reciterRow = Ui.row(this, R.drawable.ic_sound, R.color.ok, getString(R.string.quran_reciter),
                QuranAudio.current(this).name(), true, v -> pickReciter());
        Ui.addRow(g, reciterRow, true);
        col.addView(g);

        col.addView(Ui.sectionTitle(this, getString(R.string.quran_surahs)));
        LinearLayout list = Ui.group(this);
        for (int s = 1; s <= QuranData.SURAHS; s++) {
            final QuranData.Surah x = d.surahs[s];
            LinearLayout row = Ui.row(this, 0, 0, QuranData.num(s) + "  " + d.surahShort(s), "", false, v -> tapSurah(x));
            rows.add(row);
            Ui.addRow(list, row, false);
        }
        col.addView(list);
        col.addView(Ui.footnote(this, getString(R.string.quran_audio_src)));
        built = true;
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        QuranAudio.onChange = this::onProgress;
        if (built) refreshAll();
    }

    @Override
    protected void onPause() {
        if (QuranAudio.onChange != null) QuranAudio.onChange = null;
        super.onPause();
    }

    private void onProgress() {
        if (isFinishing() || isDestroyed()) return;
        if (QuranAudio.failed) {
            QuranAudio.failed = false;
            Ui.toast(this, getString(R.string.quran_audio_fail));
        }
        refreshAll();
    }

    private void pickReciter() {
        String[] names = new String[QuranAudio.RECITERS.length];
        int cur = 0;
        QuranAudio.Reciter now = QuranAudio.current(this);
        for (int i = 0; i < names.length; i++) {
            names[i] = QuranAudio.RECITERS[i].name();
            if (QuranAudio.RECITERS[i].folder.equals(now.folder)) cur = i;
        }
        Dlg.choose(this, R.string.quran_reciter, names, cur, i -> {
            QuranAudio.setCurrent(this, QuranAudio.RECITERS[i]);
            Ui.setValue(reciterRow, QuranAudio.RECITERS[i].name());
            refreshAll();
        });
    }

    private void refreshAll() {
        QuranAudio.Reciter r = QuranAudio.current(this);
        for (int i = 0; i < rows.size(); i++) {
            QuranData.Surah s = d.surahs[i + 1];
            TextView v = rows.get(i).findViewById(R.id.row_value);
            if (v == null) continue;
            int[] pr = QuranAudio.progress(r, s.n);
            if (pr != null) {
                v.setText(pr[1] == 0 ? "" : (pr[0] * 100 / pr[1]) + "%");
                v.setTextColor(Ui.color(this, R.color.accent_text));
            } else {
                int have = QuranAudio.have(this, r, s);
                if (have >= s.ayahs) {
                    v.setText(R.string.quran_audio_done);
                    v.setTextColor(Ui.color(this, R.color.ok));
                } else if (have > 0) {
                    v.setText((have * 100 / s.ayahs) + "% \u00B7 " + getString(R.string.quran_audio_dl));
                    v.setTextColor(Ui.color(this, R.color.accent_text));
                } else {
                    v.setText(R.string.quran_audio_dl);
                    v.setTextColor(Ui.color(this, R.color.accent_text));
                }
            }
        }
    }

    private void tapSurah(QuranData.Surah s) {
        QuranAudio.Reciter r = QuranAudio.current(this);
        if (QuranAudio.running(r, s.n)) {
            QuranAudio.cancel(r, s.n);
            return;
        }
        if (QuranAudio.have(this, r, s) >= s.ayahs) {
            String[] items = {getString(R.string.quran_audio_play), getString(R.string.quran_audio_delete)};
            Dlg.choose(this, R.string.quran_audio, items, -1, i -> {
                if (i == 0) {
                    startActivity(new Intent(this, QuranReaderActivity.class)
                            .putExtra(QuranReaderActivity.EXTRA_PAGE, s.page)
                            .putExtra(QuranReaderActivity.EXTRA_AYAH, s.first)
                            .putExtra(QuranReaderActivity.EXTRA_PLAY, true));
                } else {
                    QuranAudio.delete(this, r, s);
                    Ui.toast(this, getString(R.string.quran_audio_deleted));
                    refreshAll();
                }
            });
            return;
        }
        QuranAudio.download(this, r, s);
    }
}
