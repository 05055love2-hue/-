package com.widgety.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** أزرار الموسيقى في الويدجت */
public class WidgetAction extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getStringExtra("a");
        if (a == null) return;
        final Context app = c.getApplicationContext();
        final PendingResult pr = goAsync();
        try { Media.cmd(app, a); } catch (Exception ignored) { }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { Widgets.refreshMusic(app); Widgets.updateAll(app); } catch (Exception ignored) { }
            pr.finish();
        }, 600);
    }
}
