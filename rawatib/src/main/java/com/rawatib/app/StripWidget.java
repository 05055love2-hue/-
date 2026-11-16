package com.rawatib.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** ويدجت شريط ٤×١: الثلاثة جنب بعض */
public class StripWidget extends AppWidgetProvider {

    static final String[] SHORT = {"الراتب", "المواطن", "الضمان"};

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, StripWidget.class));
        if (ids.length > 0) draw(c, m, ids);
    }

    static void draw(Context c, AppWidgetManager m, int[] ids) {
        long now = System.currentTimeMillis();
        int[] base = Pay.baseDays(c);
        int[] d = {R.id.d0, R.id.d1, R.id.d2};
        int[] n = {R.id.n0, R.id.n1, R.id.n2};
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_strip);
        for (int i = 0; i < 3; i++) {
            int l = Pay.daysLeft(Pay.next(base[i], now), now);
            v.setTextViewText(d[i], l <= 0 ? "🎉" : String.valueOf(l));
            v.setTextViewText(n[i], Pay.ICON[i] + " " + SHORT[i]);
        }
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        v.setInt(R.id.root, "setBackgroundResource", WTheme.bg(c));
        m.updateAppWidget(ids, v);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        draw(c, m, ids);
        DailyReceiver.scheduleNext(c);
    }
}
