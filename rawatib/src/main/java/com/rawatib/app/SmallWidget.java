package com.rawatib.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.Calendar;

/** ويدجت صغير ٢×٢: أقرب صرف بخط كبير */
public class SmallWidget extends AppWidgetProvider {

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, SmallWidget.class));
        if (ids.length > 0) draw(c, m, ids);
    }

    static void draw(Context c, AppWidgetManager m, int[] ids) {
        long now = System.currentTimeMillis();
        int[] base = Pay.baseDays(c);
        int best = 0, bestLeft = Integer.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            Calendar d = Pay.next(base[i], now);
            int l = Pay.daysLeft(d, now);
            if (l < bestLeft) { bestLeft = l; best = i; }
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_small);
        v.setTextViewText(R.id.icon, Pay.ICON[best]);
        v.setTextViewText(R.id.title, Pay.NAME[best]);
        v.setTextViewText(R.id.days, bestLeft <= 0 ? "🎉" : String.valueOf(bestLeft));
        v.setTextViewText(R.id.unit, bestLeft <= 0 ? "اليوم الصرف" : (bestLeft == 1 ? "باقي يوم" : "يوم باقي"));
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        v.setInt(R.id.root, "setBackgroundResource", WTheme.bg(c));
        m.updateAppWidget(ids, v);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        draw(c, m, ids);
        DailyReceiver.scheduleNext(c);
    }
}
