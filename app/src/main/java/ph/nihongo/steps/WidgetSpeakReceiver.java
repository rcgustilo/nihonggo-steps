package ph.nihongo.steps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.List;
import java.util.Locale;

/** Speaks the widget's current word without opening the app. */
public class WidgetSpeakReceiver extends BroadcastReceiver {

    public static final String ACTION_SPEAK = "ph.nihongo.steps.W_SPEAK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_SPEAK.equals(intent.getAction())) return;
        Context app = context.getApplicationContext();
        List<Data.Item> items = Data.session(app, Progress.current(app));
        if (items.isEmpty()) return;
        Data.Item it = items.get(Math.min(Progress.widgetIndex(app), items.size() - 1));
        final PendingResult pending = goAsync();
        final Handler h = new Handler(Looper.getMainLooper());
        final TextToSpeech[] holder = new TextToSpeech[1];
        final boolean[] finished = {false};
        final Runnable done = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (holder[0] != null) holder[0].shutdown();
            pending.finish();
        };
        h.postDelayed(done, 9000); // a broadcast may only run for about 10 seconds
        holder[0] = new TextToSpeech(app, status -> {
            if (status != TextToSpeech.SUCCESS) { h.post(done); return; }
            TextToSpeech t = holder[0];
            t.setLanguage(Locale.JAPAN);
            t.setSpeechRate(Progress.rate(app));
            t.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { h.post(done); }
                @Override public void onError(String id) { h.post(done); }
            });
            t.speak(it.speakText(), TextToSpeech.QUEUE_FLUSH, null, "widget");
        });
    }
}
