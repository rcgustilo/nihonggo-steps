package ph.nihongo.steps;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/** Saved progress: the current session, which sessions are done, the daily streak and history. */
public final class Progress {

    private static final String P = "progress";
    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private Progress() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    public static String today() {
        return DAY.format(new Date());
    }

    public static String daysAgo(int n) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -n);
        return DAY.format(cal.getTime());
    }

    // ---- current session ----
    public static int current(Context c) { return prefs(c).getInt("current", 1); }

    public static void setCurrent(Context c, int s) {
        prefs(c).edit().putInt("current", Math.max(1, Math.min(Data.SESSIONS, s))).putInt("wIndex", 0).apply();
    }

    // ---- completed sessions ----
    public static boolean[] done(Context c) {
        String s = prefs(c).getString("done", "");
        boolean[] d = new boolean[Data.SESSIONS + 1];
        for (int i = 0; i < s.length() && i < Data.SESSIONS; i++) d[i + 1] = s.charAt(i) == '1';
        return d;
    }

    public static int doneCount(Context c) {
        int n = 0;
        for (boolean b : done(c)) if (b) n++;
        return n;
    }

    /** Marks a session finished, updates streak and history. Returns true the first time it is finished. */
    public static boolean finish(Context c, int session) {
        boolean[] d = done(c);
        boolean first = !d[session];
        d[session] = true;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= Data.SESSIONS; i++) sb.append(d[i] ? '1' : '0');

        SharedPreferences p = prefs(c);
        String today = today();
        String last = p.getString("lastDay", "");
        int streak = p.getInt("streak", 0);
        if (!today.equals(last)) {
            streak = daysAgo(1).equals(last) ? streak + 1 : 1;
        }
        int best = Math.max(p.getInt("best", 0), streak);

        JSONObject hist;
        try {
            hist = new JSONObject(p.getString("hist", "{}"));
            hist.put(today, hist.optInt(today, 0) + 1);
            // keep the last 60 days
            String cutoff = daysAgo(60);
            Iterator<String> it = hist.keys();
            java.util.List<String> old = new java.util.ArrayList<>();
            while (it.hasNext()) { String k = it.next(); if (k.compareTo(cutoff) < 0) old.add(k); }
            for (String k : old) hist.remove(k);
        } catch (Exception e) {
            hist = new JSONObject();
        }
        p.edit().putString("done", sb.toString()).putString("lastDay", today).putInt("streak", streak)
                .putInt("best", best).putString("hist", hist.toString()).apply();
        return first;
    }

    /** Streak shown to the user: still counts if the last session was yesterday. */
    public static int streak(Context c) {
        SharedPreferences p = prefs(c);
        String last = p.getString("lastDay", "");
        if (last.equals(today()) || last.equals(daysAgo(1))) return p.getInt("streak", 0);
        return 0;
    }

    public static int best(Context c) { return prefs(c).getInt("best", 0); }

    public static int sessionsOn(Context c, String day) {
        try {
            return new JSONObject(prefs(c).getString("hist", "{}")).optInt(day, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** First session not yet finished, at or after `from`. */
    public static int nextUnfinished(Context c, int from) {
        boolean[] d = done(c);
        for (int s = from; s <= Data.SESSIONS; s++) if (!d[s]) return s;
        for (int s = 1; s < from; s++) if (!d[s]) return s;
        return Math.min(from, Data.SESSIONS);
    }

    // ---- conversation checks ----
    /** Sessions finished since the last conversation check, oldest first. */
    public static java.util.List<Integer> pendingCheck(Context c) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (String t : prefs(c).getString("pendingCheck", "").split(",")) {
            try { if (!t.isEmpty()) out.add(Integer.parseInt(t)); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    public static void addPending(Context c, int session) {
        java.util.List<Integer> l = pendingCheck(c);
        if (l.contains(session)) return;
        l.add(session);
        StringBuilder b = new StringBuilder();
        for (int s : l) { if (b.length() > 0) b.append(','); b.append(s); }
        prefs(c).edit().putString("pendingCheck", b.toString()).apply();
    }

    public static void clearPending(Context c) { prefs(c).edit().putString("pendingCheck", "").apply(); }

    /** How many finished sessions trigger a check (0 = never). */
    public static int checkEvery(Context c) { return prefs(c).getInt("checkEvery", 10); }
    public static void setCheckEvery(Context c, int n) { prefs(c).edit().putInt("checkEvery", n).apply(); }

    public static boolean checkDue(Context c) {
        int n = checkEvery(c);
        return n > 0 && pendingCheck(c).size() >= n;
    }

    /** Saves a finished check: label such as "Sessions 11-20", score and number of questions. */
    public static void addCheck(Context c, String label, int score, int total) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs(c).getString("checks", "[]"));
            JSONObject o = new JSONObject();
            o.put("day", today()); o.put("label", label); o.put("score", score); o.put("total", total);
            a.put(o);
            while (a.length() > 30) a.remove(0);
            prefs(c).edit().putString("checks", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** The last few checks, newest first, as display lines. */
    public static java.util.List<String> checkLines(Context c, int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs(c).getString("checks", "[]"));
            for (int i = a.length() - 1; i >= 0 && out.size() < max; i--) {
                JSONObject o = a.getJSONObject(i);
                int sc = o.getInt("score"), t = o.getInt("total");
                out.add(String.format(Locale.US, "%s: %d/%d (%d%%), %s", o.getString("day"), sc, t,
                        t == 0 ? 0 : Math.round(sc * 100f / t), o.getString("label")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ---- settings ----
    public static float rate(Context c) { return prefs(c).getFloat("rate", 0.75f); }
    public static void setRate(Context c, float r) { prefs(c).edit().putFloat("rate", r).apply(); }
    public static boolean showRomaji(Context c) { return prefs(c).getBoolean("romaji", true); }
    public static void setShowRomaji(Context c, boolean b) { prefs(c).edit().putBoolean("romaji", b).apply(); }

    // ---- widget word position ----
    public static int widgetIndex(Context c) { return prefs(c).getInt("wIndex", 0); }
    public static void setWidgetIndex(Context c, int i) { prefs(c).edit().putInt("wIndex", i).apply(); }
}
