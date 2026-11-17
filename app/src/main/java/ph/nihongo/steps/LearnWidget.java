package ph.nihongo.steps;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Home-screen widget: today's session, one word at a time, Listen, and the progress bar. */
public class LearnWidget extends AppWidgetProvider {

    public static final String ACTION_NEXT = "ph.nihongo.steps.W_NEXT";
    public static final String ACTION_PREV = "ph.nihongo.steps.W_PREV";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        updateAll(c);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        String a = intent.getAction();
        if (ACTION_NEXT.equals(a) || ACTION_PREV.equals(a)) {
            int n = Data.session(c, Progress.current(c)).size();
            if (n == 0) return;
            int i = Progress.widgetIndex(c) + (ACTION_NEXT.equals(a) ? 1 : -1);
            Progress.setWidgetIndex(c, (i + n) % n);
            updateAll(c);
        }
    }

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, LearnWidget.class));
        if (ids == null || ids.length == 0) return;

        int session = Progress.current(c);
        List<Data.Item> items = Data.session(c, session);
        int idx = items.isEmpty() ? 0 : Math.min(Progress.widgetIndex(c), items.size() - 1);
        Data.Item it = items.isEmpty() ? null : items.get(idx);
        int done = Progress.doneCount(c);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_learn);
        v.setTextViewText(R.id.w_session, c.getString(R.string.w_session, session, Data.SESSIONS));
        v.setTextViewText(R.id.w_count, (idx + 1) + " / " + items.size());
        if (it != null) {
            v.setTextViewText(R.id.w_jp, it.japanese);
            String sub = it.reading.isEmpty() ? it.romaji : it.reading + "  ·  " + it.romaji;
            v.setTextViewText(R.id.w_romaji, sub);
            v.setTextViewText(R.id.w_meaning, it.meaning);
        }
        v.setProgressBar(R.id.w_bar, Data.SESSIONS, done, false);
        int streak = Progress.streak(c);
        v.setTextViewText(R.id.w_progress, c.getString(R.string.w_progress, done, Data.SESSIONS)
                + (streak > 0 ? "  ·  🔥 " + streak : ""));
        v.setViewVisibility(R.id.w_done, Progress.done(c)[session] ? View.VISIBLE : View.GONE);

        v.setOnClickPendingIntent(R.id.w_next, broadcast(c, LearnWidget.class, ACTION_NEXT, 1));
        v.setOnClickPendingIntent(R.id.w_prev, broadcast(c, LearnWidget.class, ACTION_PREV, 2));
        v.setOnClickPendingIntent(R.id.w_listen, broadcast(c, WidgetSpeakReceiver.class, WidgetSpeakReceiver.ACTION_SPEAK, 3));

        Intent open = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_SESSION, session)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(c, 4, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_start, pOpen);
        v.setOnClickPendingIntent(R.id.w_card, pOpen);

        m.updateAppWidget(ids, v);
    }

    private static PendingIntent broadcast(Context c, Class<?> cls, String action, int req) {
        Intent i = new Intent(c, cls).setAction(action);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
