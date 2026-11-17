package ph.nihongo.steps;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The 1000 sessions, read once from assets/sessions.tsv
 *  (session, topic, japanese, reading, romaji, meaning). */
public final class Data {

    public static final int SESSIONS = 1000;

    public static final class Item {
        public final int session;
        public final String topic, japanese, reading, romaji, meaning;

        Item(int session, String topic, String japanese, String reading, String romaji, String meaning) {
            this.session = session;
            this.topic = topic;
            this.japanese = japanese;
            this.reading = reading;
            this.romaji = romaji;
            this.meaning = meaning;
        }

        /** Text for text-to-speech: blanks and slashes made speakable. */
        public String speakText() {
            // The kana reading is spoken when there is one, so kanji are never misread
            String s = reading.isEmpty() ? japanese : reading;
            return s.replace("___", "").replace(" / ", "、").replace(";", "、").replace("～", "").replace("〜", "");
        }
    }

    private static List<List<Item>> sessions;
    private static int totalItems;

    private Data() { }

    public static synchronized void load(Context c) {
        if (sessions != null) return;
        List<List<Item>> list = new ArrayList<>();
        for (int i = 0; i <= SESSIONS; i++) list.add(new ArrayList<>());
        int count = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getApplicationContext().getAssets().open("sessions.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 6) continue;
                int s = Integer.parseInt(f[0]);
                if (s < 1 || s > SESSIONS) continue;
                list.get(s).add(new Item(s, f[1], f[2], f[3], f[4], f[5]));
                count++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not read the lessons", e);
        }
        sessions = list;
        totalItems = count;
    }

    public static List<Item> session(Context c, int n) {
        load(c);
        if (n < 1) n = 1;
        if (n > SESSIONS) n = SESSIONS;
        return sessions.get(n);
    }

    public static int totalItems(Context c) {
        load(c);
        return totalItems;
    }

    /** Items in sessions 1..n, inclusive (for "words learned" counts). */
    public static int itemsUpTo(Context c, boolean[] done) {
        load(c);
        int n = 0;
        for (int s = 1; s <= SESSIONS; s++) if (done[s]) n += sessions.get(s).size();
        return n;
    }
}
