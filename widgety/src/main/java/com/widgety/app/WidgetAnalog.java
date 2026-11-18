package com.widgety.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class WidgetAnalog extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Widgets.schedule(c);
        Widgets.refreshMusic(c);
        Widgets.updateAll(c);
    }

    @Override
    public void onEnabled(Context c) {
        Widgets.schedule(c);
        WxReceiver.fetch(c, null);
    }
}
