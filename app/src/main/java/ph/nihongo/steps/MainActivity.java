package ph.nihongo.steps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One session: 5 words or phrases, each with Listen and Speak. Finish shows the progress tracker. */
public class MainActivity extends Activity {

    public static final String EXTRA_SESSION = "session";
    private static final int REQ_SPEAK = 100;
    private static final int M_GOTO = 1, M_SPEED = 2, M_ROMAJI = 3, M_PROGRESS = 4, M_ABOUT = 5, M_CHECK = 6, M_CHECK_EVERY = 7;

    private Speaker speaker;
    private int session;
    private List<Data.Item> items;
    private boolean[] spokenOk;
    private final List<View> cards = new ArrayList<>();

    private TextView title, topic, stats;
    private ProgressBar overall;
    private LinearLayout list;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        title = findViewById(R.id.title);
        topic = findViewById(R.id.topic);
        stats = findViewById(R.id.stats);
        overall = findViewById(R.id.overall);
        list = findViewById(R.id.list);
        scroll = findViewById(R.id.scroll);

        speaker = new Speaker(this, ok -> runOnUiThread(() -> {
            if (!ok) offerJapaneseVoice();
        }));
        speaker.setRate(Progress.rate(this));

        findViewById(R.id.btn_prev).setOnClickListener(v -> show(session - 1));
        findViewById(R.id.btn_next).setOnClickListener(v -> show(session + 1));
        findViewById(R.id.btn_play_all).setOnClickListener(v -> playAll());
        findViewById(R.id.btn_finish).setOnClickListener(v -> finishSession());

        int start = getIntent().getIntExtra(EXTRA_SESSION, Progress.current(this));
        show(start);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show(intent.getIntExtra(EXTRA_SESSION, Progress.current(this)));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig); // fold / unfold: the layout reflows by itself
    }

    @Override
    protected void onDestroy() {
        speaker.shutdown();
        super.onDestroy();
    }

    // ---------------- session screen ----------------

    private void show(int s) {
        if (s < 1 || s > Data.SESSIONS) return;
        speaker.stop();
        session = s;
        Progress.setCurrent(this, s);
        items = Data.session(this, s);
        spokenOk = new boolean[items.size()];
        LearnWidget.updateAll(this);

        boolean done = Progress.done(this)[s];
        title.setText(getString(R.string.session_of, s, Data.SESSIONS) + (done ? "  ✓" : ""));
        topic.setText(items.isEmpty() ? "" : items.get(0).topic);
        refreshStats();

        list.removeAllViews();
        cards.clear();
        LayoutInflater inf = getLayoutInflater();
        boolean romaji = Progress.showRomaji(this);
        for (int i = 0; i < items.size(); i++) {
            Data.Item it = items.get(i);
            View card = inf.inflate(R.layout.item_card, list, false);
            ((TextView) card.findViewById(R.id.num)).setText(String.valueOf(i + 1));
            ((TextView) card.findViewById(R.id.jp)).setText(it.japanese);
            TextView reading = card.findViewById(R.id.reading);
            reading.setText(it.reading);
            reading.setVisibility(it.reading.isEmpty() ? View.GONE : View.VISIBLE);
            TextView rom = card.findViewById(R.id.romaji);
            rom.setText(it.romaji);
            rom.setVisibility(romaji ? View.VISIBLE : View.GONE);
            ((TextView) card.findViewById(R.id.meaning)).setText(it.meaning);
            final int idx = i;
            card.findViewById(R.id.btn_listen).setOnClickListener(v -> speaker.speak(it.speakText(), false));
            card.findViewById(R.id.btn_speak).setOnClickListener(v -> startSpeaking(idx));
            card.findViewById(R.id.btn_ok).setOnClickListener(v -> markSpoken(idx, true));
            list.addView(card);
            cards.add(card);
        }
        updateFinishLabel();
        scroll.post(() -> scroll.scrollTo(0, 0));
        findViewById(R.id.btn_prev).setEnabled(s > 1);
        findViewById(R.id.btn_next).setEnabled(s < Data.SESSIONS);
    }

    private void refreshStats() {
        int done = Progress.doneCount(this);
        overall.setMax(Data.SESSIONS);
        overall.setProgress(done);
        String line = getString(R.string.stats_line, done, Data.SESSIONS, Progress.streak(this));
        if (Progress.checkDue(this)) line += "  ·  🗣 check due";
        stats.setText(line);
    }

    private void playAll() {
        speaker.stop();
        for (Data.Item it : items) {
            speaker.speak(it.speakText(), true);
            speaker.pause(1500);
        }
    }

    // ---------------- speaking practice ----------------

    private void startSpeaking(int idx) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ja-JP");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.say_it, items.get(idx).japanese));
        try {
            startActivityForResult(i, REQ_SPEAK + idx);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_speech, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        int idx = req - REQ_SPEAK;
        if (idx < 0 || idx >= items.size() || res != RESULT_OK || data == null) return;
        ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (heard == null || heard.isEmpty()) return;
        View card = cards.get(idx);
        TextView fb = card.findViewById(R.id.feedback);
        Button ok = card.findViewById(R.id.btn_ok);
        if (Match.isCorrect(items.get(idx), heard)) {
            fb.setText(getString(R.string.great, heard.get(0)));
            fb.setTextColor(getColor(R.color.good));
            ok.setVisibility(View.GONE);
            markSpoken(idx, false);
        } else {
            // Recognizers often answer in kanji for a kana phrase: let the learner confirm
            fb.setText(getString(R.string.heard, heard.get(0)));
            fb.setTextColor(getColor(R.color.warn));
            ok.setVisibility(View.VISIBLE);
        }
        fb.setVisibility(View.VISIBLE);
    }

    private void markSpoken(int idx, boolean byHand) {
        spokenOk[idx] = true;
        View card = cards.get(idx);
        card.setActivated(true);
        if (byHand) {
            TextView fb = card.findViewById(R.id.feedback);
            fb.setText(R.string.marked_ok);
            fb.setTextColor(getColor(R.color.good));
            card.findViewById(R.id.btn_ok).setVisibility(View.GONE);
        }
        updateFinishLabel();
    }

    private void updateFinishLabel() {
        int n = 0;
        for (boolean b : spokenOk) if (b) n++;
        ((Button) findViewById(R.id.btn_finish)).setText(getString(R.string.finish_n, n, items.size()));
    }

    // ---------------- finishing and progress ----------------

    private void finishSession() {
        speaker.stop();
        Progress.finish(this, session);
        Progress.addPending(this, session);
        refreshStats();
        LearnWidget.updateAll(this);
        int next = Progress.nextUnfinished(this, Math.min(session + 1, Data.SESSIONS));
        showProgress(true, next);
    }

    private void showProgress(boolean justFinished, int next) {
        View v = getLayoutInflater().inflate(R.layout.dialog_progress, null);
        boolean[] done = Progress.done(this);
        int sessions = 0;
        for (boolean d : done) if (d) sessions++;
        int words = Data.itemsUpTo(this, done);
        int total = Data.totalItems(this);
        ((TextView) v.findViewById(R.id.p_title)).setText(justFinished
                ? getString(R.string.session_done, session) : getString(R.string.my_progress));
        ProgressBar bar = v.findViewById(R.id.p_bar);
        bar.setMax(Data.SESSIONS);
        bar.setProgress(sessions);
        ((TextView) v.findViewById(R.id.p_percent)).setText(String.format(Locale.US, "%.1f%%", sessions * 100.0 / Data.SESSIONS));
        ((TextView) v.findViewById(R.id.p_sessions)).setText(getString(R.string.p_sessions, sessions, Data.SESSIONS));
        ((TextView) v.findViewById(R.id.p_words)).setText(getString(R.string.p_words, words, total));
        ((TextView) v.findViewById(R.id.p_streak)).setText(getString(R.string.p_streak, Progress.streak(this), Progress.best(this)));
        StringBuilder today = new StringBuilder(getString(R.string.p_today, Progress.sessionsOn(this, Progress.today())));
        List<String> checks = Progress.checkLines(this, 3);
        if (!checks.isEmpty()) {
            today.append("\n\nConversation checks:");
            for (String l : checks) today.append("\n•  ").append(l);
        }
        int every = Progress.checkEvery(this), pending = Progress.pendingCheck(this).size();
        if (every > 0) today.append("\n\nNext check: after ").append(Math.max(0, every - pending)).append(" more session(s)");
        ((TextView) v.findViewById(R.id.p_today)).setText(today.toString());
        ((TextView) v.findViewById(R.id.p_level)).setText(levelText(session));

        AlertDialog.Builder b = new AlertDialog.Builder(this).setView(v);
        if (justFinished) {
            b.setPositiveButton(getString(R.string.go_next, next), (d, w) -> show(next));
            b.setNegativeButton(R.string.stay, null);
            if (Progress.checkDue(this)) {
                b.setNeutralButton("🗣 Conversation check", (d, w) -> startCheck());
                ((TextView) v.findViewById(R.id.p_title)).setText(getString(R.string.session_done, session)
                        + "\n🗣 Time for a conversation check on your last " + Progress.pendingCheck(this).size() + " sessions!");
            }
        } else {
            b.setPositiveButton(android.R.string.ok, null);
        }
        b.show();
    }

    private String levelText(int s) {
        if (s <= 14) return getString(R.string.lvl_basics);
        if (s <= 42) return getString(R.string.lvl_hiragana);
        if (s <= 70) return getString(R.string.lvl_sentences);
        if (s <= 84) return getString(R.string.lvl_katakana);
        List<Data.Item> it = Data.session(this, s);
        String t = it.isEmpty() ? "" : it.get(0).topic;
        return getString(R.string.lvl_jlpt, t.startsWith("JLPT") ? t : "JLPT vocabulary and review");
    }

    // ---------------- menu ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, M_GOTO, 0, R.string.m_goto);
        menu.add(0, M_PROGRESS, 1, R.string.m_progress);
        menu.add(0, M_SPEED, 2, R.string.m_speed);
        menu.add(0, M_ROMAJI, 3, R.string.m_romaji);
        menu.add(0, M_CHECK, 1, "🗣 Conversation check");
        menu.add(0, M_CHECK_EVERY, 3, "Conversation check every…");
        menu.add(0, M_ABOUT, 4, R.string.m_about);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case M_GOTO: askSession(); return true;
            case M_PROGRESS: showProgress(false, session); return true;
            case M_SPEED: chooseSpeed(); return true;
            case M_CHECK: startCheck(); return true;
            case M_CHECK_EVERY: chooseCheckEvery(); return true;
            case M_ROMAJI:
                Progress.setShowRomaji(this, !Progress.showRomaji(this));
                show(session);
                return true;
            case M_ABOUT:
                new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage(R.string.about_text)
                        .setPositiveButton(android.R.string.ok, null).show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void askSession() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint("1 - " + Data.SESSIONS);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        e.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this).setTitle(R.string.m_goto).setView(e)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try { show(Integer.parseInt(e.getText().toString().trim())); } catch (NumberFormatException ignored) { }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Opens a conversation check on the sessions finished since the last check (or the most recent ones). */
    private void startCheck() {
        List<Integer> pending = Progress.pendingCheck(this);
        boolean fromPending = pending.size() >= 3;
        List<Integer> list = new ArrayList<>();
        if (fromPending) list.addAll(pending);
        else {
            boolean[] d = Progress.done(this);
            for (int s = Data.SESSIONS; s >= 1 && list.size() < 10; s--) if (d[s] && s <= Math.max(session, 1) + 10) list.add(0, s);
        }
        if (list.isEmpty()) {
            Toast.makeText(this, "Finish a few sessions first, then try a conversation check.", Toast.LENGTH_LONG).show();
            return;
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        Intent i = new Intent(this, ConversationActivity.class);
        i.putExtra(ConversationActivity.EXTRA_SESSIONS, arr);
        i.putExtra(ConversationActivity.EXTRA_FROM_PENDING, fromPending);
        startActivity(i);
    }

    private void chooseCheckEvery() {
        final int[] opts = {5, 10, 15, 20, 0};
        String[] names = {"Every 5 sessions", "Every 10 sessions (default)", "Every 15 sessions", "Every 20 sessions", "Never (only from the menu)"};
        int cur = Progress.checkEvery(this), sel = 1;
        for (int i = 0; i < opts.length; i++) if (opts[i] == cur) sel = i;
        new AlertDialog.Builder(this).setTitle("Conversation check")
                .setSingleChoiceItems(names, sel, (d, w) -> {
                    Progress.setCheckEvery(this, opts[w]);
                    refreshStats();
                    d.dismiss();
                }).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (overall != null) refreshStats();
    }

    private void chooseSpeed() {
        final float[] rates = {0.6f, 0.75f, 1.0f};
        String[] names = getResources().getStringArray(R.array.speeds);
        float cur = Progress.rate(this);
        int sel = 1;
        for (int i = 0; i < rates.length; i++) if (Math.abs(rates[i] - cur) < 0.01) sel = i;
        new AlertDialog.Builder(this).setTitle(R.string.m_speed)
                .setSingleChoiceItems(names, sel, (d, w) -> {
                    Progress.setRate(this, rates[w]);
                    speaker.setRate(rates[w]);
                    d.dismiss();
                }).show();
    }

    private void offerJapaneseVoice() {
        new AlertDialog.Builder(this).setTitle(R.string.no_voice_title).setMessage(R.string.no_voice_msg)
                .setPositiveButton(R.string.install, (d, w) -> {
                    try {
                        startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                    } catch (ActivityNotFoundException ignored) { }
                })
                .setNegativeButton(R.string.later, null).show();
    }
}
