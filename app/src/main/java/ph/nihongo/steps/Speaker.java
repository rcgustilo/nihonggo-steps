package ph.nihongo.steps;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Japanese text-to-speech for the lesson screen. */
public class Speaker {

    public interface Listener {
        void onReady(boolean japaneseAvailable);
    }

    private TextToSpeech tts;
    private boolean ready, japanese;
    private final List<String> pending = new ArrayList<>();

    public Speaker(Context c, Listener l) {
        tts = new TextToSpeech(c.getApplicationContext(), status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                int r = tts.setLanguage(Locale.JAPAN);
                japanese = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                for (String s : pending) speak(s, false);
                pending.clear();
            }
            if (l != null) l.onReady(ready && japanese);
        });
    }

    public void setRate(float r) { tts.setSpeechRate(r); }

    public void speak(String text, boolean queue) {
        if (!ready) { pending.add(text); return; }
        tts.speak(text, queue ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime());
    }

    /** Speaks with a known utterance id, so a listener can react when this one finishes. */
    public void speakId(String text, boolean queue, String id) {
        if (!ready) { pending.add(text); return; }
        tts.speak(text, queue ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH, null, id);
    }

    public boolean isReady() { return ready && japanese; }

    public void pause(long ms) {
        if (ready) tts.playSilentUtterance(ms, TextToSpeech.QUEUE_ADD, "p" + System.nanoTime());
    }

    public void stop() { if (ready) tts.stop(); }

    public void shutdown() { tts.stop(); tts.shutdown(); }

    public void setListener(UtteranceProgressListener l) { tts.setOnUtteranceProgressListener(l); }
}
