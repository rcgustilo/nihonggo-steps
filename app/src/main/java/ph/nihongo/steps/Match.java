package ph.nihongo.steps;

import java.util.ArrayList;
import java.util.List;

/** Checks what the speech recognizer heard against the expected word or phrase. */
public final class Match {

    private Match() { }

    /** Katakana to hiragana, and drops spaces, punctuation and blanks. */
    public static String norm(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'ァ' && c <= 'ヶ') c = (char) (c - 0x60);
            if (Character.isWhitespace(c) || c == '_' || "、。,.!?！？・…~～〜()（）「」;；-".indexOf(c) >= 0) continue;
            b.append(Character.toLowerCase(c));
        }
        return b.toString();
    }

    /** The forms a correct answer may take: the word itself, its reading, each side of an "a / b". */
    public static List<String> expected(Data.Item it) {
        List<String> out = new ArrayList<>();
        for (String src : new String[]{it.japanese, it.reading}) {
            if (src == null || src.isEmpty()) continue;
            for (String part : src.split("/|;|；")) {
                String n = norm(part);
                if (!n.isEmpty()) out.add(n);
            }
        }
        return out;
    }

    /** True if any heard alternative matches closely enough. */
    public static boolean isCorrect(Data.Item it, List<String> heard) {
        List<String> exp = expected(it);
        boolean hasBlank = it.japanese.contains("___");
        for (String h : heard) {
            String nh = norm(h);
            if (nh.isEmpty()) continue;
            for (String e : exp) {
                if (nh.equals(e) || nh.contains(e) || (e.length() >= 3 && e.contains(nh) && nh.length() >= e.length() - 1)) return true;
                if (similarity(nh, e) >= 0.8) return true;
            }
            if (hasBlank) {
                // "わたしは ___ です": every fixed part must be heard, in any filled-in form
                boolean all = true;
                for (String part : it.japanese.split("___")) {
                    String p = norm(part);
                    if (!p.isEmpty() && !nh.contains(p)) { all = false; break; }
                }
                if (all) return true;
            }
        }
        return false;
    }

    static double similarity(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0 || m == 0) return 0;
        int[] prev = new int[m + 1], cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return 1.0 - (double) prev[m] / Math.max(n, m);
    }
}
