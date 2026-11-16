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
        Wv.update(c, m, ids);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle o) {
        Wv.update(c, m, new int[]{id});
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        draw(c, m, ids);
        DailyReceiver.scheduleNext(c);
    }
}
