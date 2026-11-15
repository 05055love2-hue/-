package com.mawasim.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class SeasonWidget extends AppWidgetProvider {

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, SeasonWidget.class));
        if (ids.length > 0) draw(c, m, ids);
    }

    static void draw(Context c, AppWidgetManager m, int[] ids) {
        long now = System.currentTimeMillis();
        Seasons.Info in = Seasons.at(now);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.icon, Seasons.M_ICON[in.mawsim]);
        v.setTextViewText(R.id.title, "موسم " + Seasons.M_NAME[in.mawsim]);
        v.setTextViewText(R.id.star, "نجم " + Seasons.starName(in.star) + " • يخلص بعد " + Seasons.daysUntil(now, in.starEnd) + " يوم");
        v.setTextViewText(R.id.days, String.valueOf(Seasons.daysUntil(now, in.nextMawsimStart)));
        v.setTextViewText(R.id.next, "على " + Seasons.M_NAME[in.nextMawsim] + " " + Seasons.M_ICON[in.nextMawsim]);
        v.setTextViewText(R.id.fasl, Seasons.F_ICON[in.fasl] + " " + Seasons.F_NAME[in.fasl] + " • باقي "
                + Seasons.daysUntil(now, in.nextFaslStart) + " يوم على " + Seasons.F_NAME[in.nextFasl]);
        Intent open = new Intent(c, MainActivity.class);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(ids, v);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        draw(c, m, ids);
        DailyReceiver.scheduleNext(c);
    }
}
