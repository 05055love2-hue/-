package com.rawatib.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.Calendar;

/** ويدجت مواعيد الصرف */
public class SeasonWidget extends AppWidgetProvider {

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, SeasonWidget.class));
        if (ids.length > 0) draw(c, m, ids);
    }

    static void draw(Context c, AppWidgetManager m, int[] ids) {
        long now = System.currentTimeMillis();
        int[] base = Pay.baseDays(c);
        int[] left = new int[3];
        Calendar[] dates = new Calendar[3];
        int best = 0;
        for (int i = 0; i < 3; i++) {
            dates[i] = Pay.next(base[i], now);
            left[i] = Pay.daysLeft(dates[i], now);
            if (left[i] < left[best]) best = i;
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.icon, Pay.ICON[best]);
        v.setTextViewText(R.id.title, Pay.NAME[best]);
        v.setTextViewText(R.id.star, Pay.fmt(dates[best]));
        v.setTextViewText(R.id.days, left[best] <= 0 ? "🎉" : String.valueOf(left[best]));
        v.setTextViewText(R.id.unit, left[best] <= 0 ? "اليوم" : "يوم");
        int[] rows = {R.id.r0, R.id.r1, R.id.r2};
        for (int i = 0; i < 3; i++) {
            v.setTextViewText(rows[i], Pay.ICON[i] + " " + Pay.NAME[i] + ": " + Pay.left(left[i]));
        }
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(ids, v);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        draw(c, m, ids);
        DailyReceiver.scheduleNext(c);
    }
}
