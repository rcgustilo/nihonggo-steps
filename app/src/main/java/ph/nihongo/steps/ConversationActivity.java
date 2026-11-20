package ph.nihongo.steps;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversation check: a short spoken back-and-forth that tests the sessions finished since the
 * last check. The app speaks, then listens straight away (hands-free), checks the answer and moves on.
 * At the end it reports how well each session and each skill has stuck, and what to review.
 */
public class ConversationActivity extends Activity {

    public static final String EXTRA_SESSIONS = "sessions";      // int[] of sessions to test
    public static final String EXTRA_FROM_PENDING = "fromPending";
    private static final int REQ_MIC = 7, REQ_FALLBACK = 8;
    private static final int QUESTIONS = 10;

    private static final int T_SPEAK = 0, T_LISTEN = 1, T_REPEAT = 2, T_RESPOND = 3;
    private static final String[] SKILL = {"Saying it in Japanese", "Understanding by ear", "Repeating after me", "Replying in a conversation"};

    /** Call-and-response pairs from the starter course: prompt, what it means, what to do, accepted replies. */
    private static final String[][] PAIRS = {
            {"おはようございます", "Good morning!", "Greet them back", "おはようございます", "おはよう"},
            {"こんにちは", "Hello!", "Greet them back", "こんにちは"},
            {"こんばんは", "Good evening!", "Greet them back", "こんばんは"},
            {"ありがとうございます", "Thank you!", "Say “You're welcome”", "どういたしまして", "いえいえ"},
            {"はじめまして", "Nice to meet you!", "Reply politely", "はじめまして", "よろしくおねがいします", "よろしく"},
            {"おなまえは", "What is your name?", "Say “I am (your name)”", "わたしは", "私は", "です"},
            {"さようなら", "Goodbye!", "Say goodbye", "さようなら", "またね", "じゃあまた"},
            {"またね", "See you!", "Say see you", "またね", "じゃあまた", "さようなら"},
            {"おやすみなさい", "Good night!", "Say good night", "おやすみなさい", "おやすみ"},
            {"ごめんなさい", "I'm sorry!", "Say “It's okay”", "だいじょうぶ", "大丈夫", "いいえ"},
            {"えいごがわかりますか", "Do you understand English?", "Answer yes, no or a little", "はい", "いいえ", "すこし", "少し"},
            {"いただきます", "(said before eating)", "Say it too", "いただきます"},
            {"どうぞ", "Here you are.", "Say thank you", "ありがとう", "どうも"},
            {"なにがすきですか", "What do you like?", "Say “I like ___”", "がすきです", "が好きです", "すきです", "好きです"},
            {"いいてんきですね", "Nice weather, isn't it?", "Agree", "そうですね", "いいてんきですね", "いい天気ですね"},
            {"いっしょにいきませんか", "Shall we go together?", "Say yes or politely no", "いいですね", "ざんねんですが", "残念ですが"},
            {"すわってもいいですか", "May I sit down?", "Say “Go ahead”", "どうぞ"},
            {"はいってもいいですか", "May I come in?", "Say “Go ahead”", "どうぞ"},
            {"しゃしんをとってもいいですか", "May I take a photo?", "Say “Go ahead” or “Not allowed”", "どうぞ", "だめです", "ダメです"},
            {"なにがしたいですか", "What do you want to do?", "Say “I want to ___”", "たいです"},
            {"あしたなにをしますか", "What will you do tomorrow?", "Say “I'll go to ___”", "にいきます", "に行きます", "ます"},
            {"おくれてすみません", "Sorry I'm late.", "Say “It's okay”", "だいじょうぶ", "大丈夫"},
            {"もしもし", "Hello? (on the phone)", "Answer the phone", "もしもし"},
    };

    private static final class Turn {
        int type, session, attempts;
        Data.Item item;
        String[] pair;
        String[] choices;
        int answer;
        boolean correct, finished;
    }

    private final List<Turn> turns = new ArrayList<>();
    private int index = -1, sessionsFrom, sessionsTo;
    private int[] sessions;
    private boolean fromPending;
    private String label;

    private Speaker speaker;
    private SpeechRecognizer recognizer;
    private boolean useInApp, listening, paused, ended, inFallback;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private LinearLayout chat;
    private ScrollView scroll;
    private TextView status, header, partial;
    private ProgressBar bar;
    private Button bMic, bRepeat, bSkip, bRight;
    private float dp;

    // ---------------- set-up ----------------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sessions = getIntent().getIntArrayExtra(EXTRA_SESSIONS);
        fromPending = getIntent().getBooleanExtra(EXTRA_FROM_PENDING, false);
        if (sessions == null || sessions.length == 0) { finish(); return; }
        sessionsFrom = Integer.MAX_VALUE; sessionsTo = 0;
        for (int s : sessions) { sessionsFrom = Math.min(sessionsFrom, s); sessionsTo = Math.max(sessionsTo, s); }
        label = sessionsFrom == sessionsTo ? "Session " + sessionsFrom : "Sessions " + sessionsFrom + "–" + sessionsTo;
        buildUi();
        buildTurns();

        speaker = new Speaker(this, ok -> { });
        speaker.setRate(Progress.rate(this));
        speaker.setListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onError(String id) { onDone(id); }
            @Override public void onDone(String id) {
                if (id != null && id.startsWith("turn")) ui.post(() -> afterPrompt(id));
            }
        });

        useInApp = SpeechRecognizer.isRecognitionAvailable(this);
        if (useInApp && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);   // intro starts after the answer
        } else {
            intro();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_MIC && (res.length == 0 || res[0] != PackageManager.PERMISSION_GRANTED)) {
            useInApp = false;   // fall back to the Google voice-input pop-up
            appSay("Microphone access was not allowed, so I'll use the Google voice pop-up for each answer instead.", null, null);
        }
        if (req == REQ_MIC && index < 0) intro();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!ended && index >= 0 && !inFallback) setPaused(true);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (speaker != null) speaker.shutdown();
        super.onDestroy();
    }

    // ---------------- building the questions ----------------

    private static final Pattern EXAMPLE = Pattern.compile("Example: (\\S+) \\(([^)]+)\\) = ([^.]+)");

    /** Turns a lesson item into something that can be asked; kana lessons use their example word. */
    private Data.Item askable(Data.Item it) {
        if (it.japanese.length() <= 1 || it.meaning.startsWith("Example") || it.meaning.contains("Example:")) {
            Matcher m = EXAMPLE.matcher(it.meaning);
            if (m.find()) return new Data.Item(it.session, it.topic, m.group(1), "", m.group(2), m.group(3).trim());
            return null;
        }
        if (it.meaning.startsWith("Particle") || it.meaning.startsWith("Rare")) return null;
        return it;
    }

    private static String cleanMeaning(String m) {
        String s = m.replaceAll("\\s*\\(.*?\\)", "").trim();
        if (s.length() > 46) {
            int cut = s.indexOf(',', 20);
            if (cut > 0) s = s.substring(0, cut);
        }
        return s.isEmpty() ? m : s;
    }

    private String[] pairFor(Data.Item it) {
        String n = Match.norm(it.japanese);
        for (String[] p : PAIRS) if (n.equals(Match.norm(p[0]))) return p;
        return null;
    }

    private void buildTurns() {
        // collect items, newest pending items first in priority, no duplicates
        Map<String, Data.Item> pool = new LinkedHashMap<>();
        for (int s : sessions) {
            for (Data.Item raw : Data.session(this, s)) {
                Data.Item it = askable(raw);
                if (it != null && !pool.containsKey(it.japanese)) pool.put(it.japanese, it);
            }
        }
        List<Data.Item> items = new ArrayList<>(pool.values());
        Collections.shuffle(items, rnd);
        int n = Math.min(QUESTIONS, items.size());
        List<Data.Item> chosen = new ArrayList<>(items.subList(0, n));
        // make sure up to two conversation replies are included when the sessions have them
        int have = 0;
        for (Data.Item it : chosen) if (pairFor(it) != null) have++;
        for (Data.Item it : items) {
            if (have >= 2) break;
            if (pairFor(it) == null || chosen.contains(it)) continue;
            for (int i = chosen.size() - 1; i >= 0; i--) {
                if (pairFor(chosen.get(i)) == null) { chosen.set(i, it); have++; break; }
            }
        }
        // distractor meanings for listening questions
        List<String> meanings = new ArrayList<>();
        for (Data.Item it : items) meanings.add(cleanMeaning(it.meaning));
        for (int s = Math.max(1, sessionsFrom - 6); s <= Math.min(Data.SESSIONS, sessionsTo + 6) && meanings.size() < 30; s++) {
            for (Data.Item raw : Data.session(this, s)) {
                Data.Item it = askable(raw);
                if (it != null) meanings.add(cleanMeaning(it.meaning));
            }
        }
        int listens = 0, repeats = 0;
        for (Data.Item it : chosen) {
            Turn t = new Turn();
            t.item = it;
            t.session = it.session;
            String[] pair = pairFor(it);
            boolean blank = it.japanese.contains("___");
            if (pair != null && turnsOfType(T_RESPOND) < 2) { t.type = T_RESPOND; t.pair = pair; }
            else if (!blank && listens < 3 && distinctOthers(meanings, cleanMeaning(it.meaning)) >= 2) { t.type = T_LISTEN; listens++; }
            else if (repeats < 2 && (blank || Match.norm(it.japanese).length() >= 6)) { t.type = T_REPEAT; repeats++; }
            else t.type = blank ? T_REPEAT : T_SPEAK;
            if (t.type == T_LISTEN) makeChoices(t, meanings);
            turns.add(t);
        }
        Collections.shuffle(turns, rnd);
        // start gently: put a listening or repeating question first
        for (int i = 0; i < turns.size(); i++) {
            if (turns.get(i).type == T_LISTEN || turns.get(i).type == T_REPEAT) { Collections.swap(turns, 0, i); break; }
        }
    }

    private int turnsOfType(int type) {
        int n = 0;
        for (Turn t : turns) if (t.type == type) n++;
        return n;
    }

    private static int distinctOthers(List<String> meanings, String m) {
        Set<String> s = new HashSet<>(meanings);
        s.remove(m);
        return s.size();
    }

    private void makeChoices(Turn t, List<String> meanings) {
        String right = cleanMeaning(t.item.meaning);
        List<String> others = new ArrayList<>(new HashSet<>(meanings));
        others.remove(right);
        Collections.shuffle(others, rnd);
        List<String> opts = new ArrayList<>();
        opts.add(right);
        for (String o : others) { if (opts.size() >= 3) break; if (!o.equalsIgnoreCase(right)) opts.add(o); }
        Collections.shuffle(opts, rnd);
        t.choices = opts.toArray(new String[0]);
        t.answer = opts.indexOf(right);
    }

    // ---------------- user interface ----------------

    private int px(float v) { return Math.round(v * dp); }

    private GradientDrawable round(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radius));
        if (stroke != 0) g.setStroke(px(1), stroke);
        return g;
    }

    private void buildUi() {
        int accent = getColor(R.color.accent), paper = getColor(R.color.paper);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(paper);
        root.setFitsSystemWindows(true);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(accent);
        top.setPadding(px(20), px(12), px(20), px(12));
        TextView t = new TextView(this);
        t.setText("🗣 Conversation check");
        t.setTextColor(Color.WHITE);
        t.setTextSize(21);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(t);
        header = new TextView(this);
        header.setTextColor(0xE6FFFFFF);
        header.setTextSize(14);
        top.addView(header);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x55FFFFFF));
        top.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        root.addView(top);

        scroll = new ScrollView(this);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding(px(12), px(12), px(12), px(20));
        scroll.addView(chat);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(getColor(R.color.card));
        bottom.setElevation(px(8));
        bottom.setPadding(px(10), px(8), px(10), px(10));
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(getColor(R.color.ink_soft));
        status.setTextSize(15);
        bottom.addView(status);
        partial = new TextView(this);
        partial.setGravity(Gravity.CENTER);
        partial.setTextColor(getColor(R.color.ink));
        partial.setTextSize(20);
        partial.setVisibility(View.GONE);
        bottom.addView(partial);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        bRepeat = smallButton("🔁 Again");
        bMic = new Button(this);
        bMic.setText("🎤");
        bMic.setTextSize(26);
        bMic.setTextColor(Color.WHITE);
        bMic.setBackground(round(accent, 0, 40));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(px(76), px(76));
        mp.setMargins(px(14), px(4), px(14), px(4));
        bSkip = smallButton("⏭ Skip");
        row.addView(bRepeat);
        row.addView(bMic, mp);
        row.addView(bSkip);
        bottom.addView(row);
        bRight = smallButton("It was right ✓");
        bRight.setVisibility(View.GONE);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        bottom.addView(bRight, rp);
        root.addView(bottom);
        setContentView(root);

        bMic.setOnClickListener(v -> {
            if (paused) { setPaused(false); return; }
            if (listening) stopListening(); else listen();
        });
        bRepeat.setOnClickListener(v -> repeatPrompt());
        bSkip.setOnClickListener(v -> skip());
        bRight.setOnClickListener(v -> markRight());
    }

    private Button smallButton(String text) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(getColor(R.color.accent));
        b.setTextSize(15);
        return b;
    }

    private void updateHeader() {
        int shown = Math.max(0, Math.min(index + 1, turns.size()));
        header.setText(label + "  ·  question " + shown + " of " + turns.size());
        bar.setMax(turns.size());
        bar.setProgress(Math.max(0, index));
    }

    private void scrollDown() { scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)); }

    /** An app message: big Japanese line (optional), then the English text. */
    private LinearLayout appSay(String english, String japanese, String romaji) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(getColor(R.color.card), 0x22000000, 18));
        box.setPadding(px(16), px(12), px(16), px(12));
        if (japanese != null) {
            TextView jp = new TextView(this);
            jp.setText(japanese);
            jp.setTextSize(26);
            jp.setTextColor(getColor(R.color.ink));
            box.addView(jp);
            if (romaji != null && Progress.showRomaji(this) && !romaji.isEmpty()) {
                TextView r = new TextView(this);
                r.setText(romaji);
                r.setTextSize(15);
                r.setTextColor(getColor(R.color.accent));
                box.addView(r);
            }
        }
        if (english != null) {
            TextView en = new TextView(this);
            en.setText(english);
            en.setTextSize(16);
            en.setTextColor(getColor(R.color.ink));
            if (japanese != null) en.setPadding(0, px(4), 0, 0);
            box.addView(en);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, px(6), px(48), px(6));
        lp.gravity = Gravity.START;
        chat.addView(box, lp);
        scrollDown();
        return box;
    }

    private void userSay(String text, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(18);
        v.setTextColor(color);
        v.setBackground(round(getColor(R.color.accent_soft), 0, 18));
        v.setPadding(px(16), px(10), px(16), px(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(px(48), px(6), 0, px(6));
        lp.gravity = Gravity.END;
        chat.addView(v, lp);
        scrollDown();
    }

    private void note(String text, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(15);
        v.setTextColor(color);
        v.setPadding(px(8), px(2), px(8), px(6));
        chat.addView(v);
        scrollDown();
    }

    private void setStatus(String s) { status.setText(s); }

    // ---------------- conversation flow ----------------

    private void intro() {
        updateHeader();
        appSay("Let's see how well " + label.toLowerCase(Locale.US) + " stuck. I'll ask " + turns.size()
                + " quick questions. When I finish speaking, the microphone turns on by itself, so just answer out loud.\n\n"
                + "Tip: tap 🔁 to hear me again, ⏭ to skip, and 🎤 to answer again.", "はじめましょう！", "ha-ji-me-ma-shō!");
        setStatus("Starting…");
        bMic.setEnabled(false);
        ui.postDelayed(() -> {
            speaker.speakId("はじめましょう", false, "intro");
            ui.postDelayed(this::next, 1800);
        }, 600);
    }

    private void next() {
        if (ended) return;
        stopListening();
        bRight.setVisibility(View.GONE);
        partial.setVisibility(View.GONE);
        index++;
        if (index >= turns.size()) { results(); return; }
        updateHeader();
        ask(turns.get(index));
    }

    private Turn cur() { return index >= 0 && index < turns.size() ? turns.get(index) : null; }

    private void ask(Turn t) {
        Data.Item it = t.item;
        switch (t.type) {
            case T_SPEAK:
                appSay("How do you say “" + cleanMeaning(it.meaning) + "” in Japanese?", null, null);
                speak("にほんごで なんと いいますか", t);
                break;
            case T_LISTEN: {
                appSay("Listen. What does it mean?", "🔊 …", null);
                LinearLayout opts = new LinearLayout(this);
                opts.setOrientation(LinearLayout.VERTICAL);
                for (int i = 0; i < t.choices.length; i++) {
                    Button b = new Button(this);
                    b.setText(t.choices[i]);
                    b.setAllCaps(false);
                    b.setTextSize(16);
                    b.setTextColor(getColor(R.color.ink));
                    b.setBackground(round(getColor(R.color.card), getColor(R.color.accent), 14));
                    final int pick = i;
                    b.setOnClickListener(v -> choose(t, pick, opts));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.setMargins(px(24), px(4), px(24), px(4));
                    opts.addView(b, lp);
                }
                chat.addView(opts);
                scrollDown();
                speak(it.speakText(), t);
                break;
            }
            case T_REPEAT:
                appSay(it.japanese.contains("___") ? "Say this, and fill the blank with your own word:" : "Say it after me:",
                        it.japanese, it.romaji);
                note("= " + it.meaning, getColor(R.color.ink_soft));
                speak(it.speakText(), t);
                break;
            default:
                appSay("They say this to you. " + t.pair[2] + " in Japanese.", it.japanese, it.romaji);
                speak(it.speakText(), t);
                break;
        }
    }

    private void speak(String text, Turn t) {
        stopListening();
        bMic.setEnabled(false);
        setStatus("🔊 Speaking…");
        speaker.speakId(text, false, "turn" + index);
        if (!speaker.isReady()) ui.postDelayed(() -> afterPrompt("turn" + index), 1200);
    }

    /** Called when the app has finished speaking a question. */
    private void afterPrompt(String id) {
        Turn t = cur();
        if (t == null || ended || t.finished || listening || inFallback || !id.equals("turn" + index)) return;
        bMic.setEnabled(true);
        if (t.type == T_LISTEN) { setStatus("Tap the meaning"); return; }
        if (paused) { setStatus("Paused. Tap 🎤 to continue"); return; }
        listen();
    }

    private void repeatPrompt() {
        Turn t = cur();
        if (t == null || t.finished) return;
        stopListening();
        String text = t.type == T_SPEAK ? "にほんごで なんと いいますか" : t.item.speakText();
        speak(text, t);
    }

    private void skip() {
        Turn t = cur();
        if (t == null || t.finished) return;
        stopListening();
        resolve(t, false, "Skipped.");
    }

    private void markRight() {
        Turn t = cur();
        if (t == null || t.finished) return;
        resolve(t, true, null);
    }

    private void setPaused(boolean p) {
        paused = p;
        if (p) {
            stopListening();
            speaker.stop();
            setStatus("Paused. Tap 🎤 to continue");
            bMic.setEnabled(true);
        } else {
            Turn t = cur();
            if (t != null && !t.finished) repeatPrompt();
        }
    }

    // ---------------- listening ----------------

    private Intent recognizeIntent() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ja-JP");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        return i;
    }

    private void listen() {
        Turn t = cur();
        if (t == null || t.finished || t.type == T_LISTEN || ended) return;
        speaker.stop();
        if (!useInApp || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            try {
                inFallback = true;
                startActivityForResult(recognizeIntent(), REQ_FALLBACK);
            } catch (ActivityNotFoundException e) {
                inFallback = false;
                setStatus("Speech input is not available. Install or update the Google app.");
            }
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(listener);
        }
        listening = true;
        partial.setText("");
        partial.setVisibility(View.VISIBLE);
        setStatus("🎤 Listening… speak now");
        bMic.setText("■");
        try {
            recognizer.startListening(recognizeIntent());
        } catch (RuntimeException e) {
            listening = false;
            useInApp = false;
            listen();
        }
    }

    private void stopListening() {
        if (recognizer != null && listening) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) { }
        }
        listening = false;
        bMic.setText("🎤");
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle b) { setStatus("🎤 Listening… speak now"); }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float v) {
            float k = Math.max(0, Math.min(1, (v + 2) / 12f));
            bMic.setScaleX(1 + 0.18f * k);
            bMic.setScaleY(1 + 0.18f * k);
        }
        @Override public void onBufferReceived(byte[] bytes) { }
        @Override public void onEndOfSpeech() { setStatus("Checking…"); bMic.setScaleX(1); bMic.setScaleY(1); }
        @Override public void onError(int error) {
            listening = false;
            bMic.setText("🎤");
            bMic.setScaleX(1); bMic.setScaleY(1);
            partial.setVisibility(View.GONE);
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                setStatus("I didn't catch that. Tap 🎤 to answer, or 🔁 to hear it again.");
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                if (recognizer != null) { recognizer.destroy(); recognizer = null; }
                setStatus("Tap 🎤 to answer");
            } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                useInApp = false;
                setStatus("Tap 🎤 to answer");
            } else {
                setStatus("Speech recognition problem (code " + error + "). Tap 🎤 to try again.");
            }
        }
        @Override public void onResults(Bundle b) {
            listening = false;
            bMic.setText("🎤");
            partial.setVisibility(View.GONE);
            ArrayList<String> heard = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            handleHeard(heard);
        }
        @Override public void onPartialResults(Bundle b) {
            ArrayList<String> p = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (p != null && !p.isEmpty() && !p.get(0).isEmpty()) partial.setText(p.get(0));
        }
        @Override public void onEvent(int t, Bundle b) { }
    };

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FALLBACK) return;
        inFallback = false;
        if (res != RESULT_OK || data == null) { setStatus("Tap 🎤 to answer"); return; }
        handleHeard(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
    }

    // ---------------- checking answers ----------------

    private boolean replyOk(String[] pair, List<String> heard) {
        for (String h : heard) {
            String nh = Match.norm(h);
            if (nh.isEmpty()) continue;
            for (int i = 3; i < pair.length; i++) {
                String a = Match.norm(pair[i]);
                if (nh.contains(a) || Match.similarity(nh, a) >= 0.75) return true;
            }
        }
        return false;
    }

    private void handleHeard(List<String> heard) {
        Turn t = cur();
        if (t == null || t.finished) return;
        if (heard == null || heard.isEmpty() || heard.get(0).trim().isEmpty()) {
            setStatus("I didn't catch that. Tap 🎤 to answer.");
            return;
        }
        boolean ok = t.type == T_RESPOND ? replyOk(t.pair, heard) : Match.isCorrect(t.item, heard);
        t.attempts++;
        if (ok) {
            userSay(heard.get(0) + "  ✓", getColor(R.color.good));
            resolve(t, true, null);
        } else if (t.attempts < 2) {
            userSay(heard.get(0) + "  ✗", getColor(R.color.warn));
            String hint;
            if (t.type == T_RESPOND) hint = "Not quite. Hint: " + t.pair[2] + " (for example “" + t.pair[3] + "”).";
            else if (t.type == T_SPEAK) hint = "Not quite. Hint: it starts with “" + firstKana(t.item) + "…”.";
            else hint = "Not quite. Listen once more and try again.";
            note(hint + "  If you did say it right, tap “It was right ✓”.", getColor(R.color.warn));
            bRight.setVisibility(View.VISIBLE);
            setStatus("🔊 One more try");
            if (t.type == T_REPEAT) speak(t.item.speakText(), t);
            else speak("もう いちど", t);
        } else {
            userSay(heard.get(0) + "  ✗", getColor(R.color.warn));
            resolve(t, false, null);
        }
    }

    private static String firstKana(Data.Item it) {
        String s = Match.norm(it.reading.isEmpty() ? it.japanese : it.reading);
        return s.isEmpty() ? "" : s.substring(0, Math.min(2, s.length()));
    }

    private void choose(Turn t, int pick, LinearLayout opts) {
        if (t.finished || t != cur()) return;
        for (int i = 0; i < opts.getChildCount(); i++) {
            Button b = (Button) opts.getChildAt(i);
            b.setEnabled(false);
            if (i == t.answer) b.setBackground(round(0xFFF1FAF3, getColor(R.color.good), 14));
            else if (i == pick) b.setBackground(round(0xFFFFF4E5, getColor(R.color.warn), 14));
        }
        boolean ok = pick == t.answer;
        resolve(t, ok, null);
    }

    /** Ends a question: shows the answer, speaks feedback, then moves on. */
    private void resolve(Turn t, boolean ok, String extra) {
        t.finished = true;
        t.correct = ok;
        stopListening();
        bRight.setVisibility(View.GONE);
        partial.setVisibility(View.GONE);
        Data.Item it = t.item;
        String answer = it.japanese + (it.romaji.isEmpty() ? "" : "  (" + it.romaji + ")") + " = " + it.meaning;
        if (ok) {
            note("✓ Correct!  " + (t.type == T_RESPOND ? "" : answer), getColor(R.color.good));
            setStatus("いいですね！");
            speaker.speakId(praise(), false, "fb");
            ui.postDelayed(this::next, 1700);
        } else {
            String told = t.type == T_RESPOND
                    ? "A good reply: “" + t.pair[3] + "”. (They said: " + t.pair[1] + ")"
                    : "The answer: " + answer;
            note((extra != null ? extra + " " : "✗ ") + told, getColor(R.color.warn));
            setStatus("🔊 Here's the answer");
            String say = t.type == T_RESPOND ? t.pair[3] : it.speakText();
            speaker.speakId(say, false, "fb");
            ui.postDelayed(this::next, 3200);
        }
        updateHeader();
        bar.setProgress(index + 1);
    }

    private String praise() {
        String[] p = {"いいですね", "すごい", "じょうずですね", "そのとおり", "せいかい"};
        return p[rnd.nextInt(p.length)];
    }

    // ---------------- results ----------------

    private void results() {
        ended = true;
        stopListening();
        bMic.setEnabled(false);
        bRepeat.setEnabled(false);
        bSkip.setEnabled(false);
        setStatus("Check complete");
        int score = 0;
        int[] skillRight = new int[4], skillAll = new int[4];
        Map<Integer, int[]> bySession = new java.util.TreeMap<>();
        List<Turn> missed = new ArrayList<>();
        for (Turn t : turns) {
            if (t.correct) score++; else missed.add(t);
            skillAll[t.type]++;
            if (t.correct) skillRight[t.type]++;
            int[] s = bySession.get(t.session);
            if (s == null) { s = new int[2]; bySession.put(t.session, s); }
            s[1]++;
            if (t.correct) s[0]++;
        }
        int total = turns.size();
        int pct = total == 0 ? 0 : Math.round(score * 100f / total);
        Progress.addCheck(this, label, score, total);
        if (fromPending) Progress.clearPending(this);
        bar.setProgress(total);
        header.setText(label + "  ·  finished");

        String verdict;
        if (pct >= 85) verdict = "Excellent! These sessions have stuck. Keep going with new lessons.";
        else if (pct >= 70) verdict = "Good. Most of it has stuck. Give the missed words below a quick listen.";
        else if (pct >= 50) verdict = "Getting there. Review the sessions marked below before moving on.";
        else verdict = "These sessions need another pass. Review them, then try the check again.";
        speaker.speakId(pct >= 70 ? "おつかれさまでした。よく できました" : "おつかれさまでした。がんばりましょう", false, "end");

        StringBuilder sb = new StringBuilder();
        sb.append("Score: ").append(score).append(" / ").append(total).append("  (").append(pct).append("%)\n\n");
        sb.append(verdict).append("\n\nBy skill:\n");
        for (int k = 0; k < 4; k++) {
            if (skillAll[k] == 0) continue;
            sb.append("•  ").append(SKILL[k]).append(": ").append(skillRight[k]).append(" / ").append(skillAll[k]).append('\n');
        }
        List<Integer> weak = new ArrayList<>();
        sb.append("\nBy session:\n");
        for (Map.Entry<Integer, int[]> e : bySession.entrySet()) {
            int[] v = e.getValue();
            boolean w = v[0] * 2 < v[1] || (v[1] == 1 && v[0] == 0);
            if (w) weak.add(e.getKey());
            sb.append("•  Session ").append(e.getKey()).append(": ").append(v[0]).append(" / ").append(v[1]).append(w ? "  ← review" : "").append('\n');
        }
        appSay(sb.toString().trim(), "おつかれさまでした！", "o-tsu-ka-re-sa-ma de-shi-ta! (Well done!)");

        if (!missed.isEmpty()) {
            note("Words to practise (tap to hear):", getColor(R.color.ink_soft));
            for (Turn t : missed) {
                Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
                b.setAllCaps(false);
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                b.setText("🔊  " + t.item.japanese + "  " + t.item.romaji + "  =  " + t.item.meaning);
                b.setTextColor(getColor(R.color.ink));
                b.setTextSize(15);
                b.setOnClickListener(v -> speaker.speak(t.item.speakText(), false));
                chat.addView(b);
            }
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(px(12), px(12), px(12), 0);
        if (!weak.isEmpty()) {
            int first = weak.get(0);
            Button r = new Button(this);
            r.setText("Review session " + first + (weak.size() > 1 ? "  (then " + weak.subList(1, weak.size()).toString().replace("[", "").replace("]", "") + ")" : ""));
            r.setAllCaps(false);
            r.setOnClickListener(v -> {
                Intent i = new Intent(this, MainActivity.class);
                i.putExtra(MainActivity.EXTRA_SESSION, first);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            });
            actions.addView(r);
        }
        Button again = new Button(this);
        again.setText("Try a new check on these sessions");
        again.setAllCaps(false);
        again.setOnClickListener(v -> {
            Intent i = new Intent(this, ConversationActivity.class);
            i.putExtra(EXTRA_SESSIONS, sessions);
            startActivity(i);
            finish();
        });
        actions.addView(again);
        Button done = new Button(this);
        done.setText("Continue learning");
        done.setAllCaps(false);
        done.setOnClickListener(v -> finish());
        actions.addView(done);
        chat.addView(actions);
        scrollDown();
    }
}
