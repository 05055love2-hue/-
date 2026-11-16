package com.tvhome.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * حارس الشاشة الرئيسية (نسخة خفيفة): بدون خدمة شغّالة وبدون تنبيهات.
 * لما يصحى التلفزيون من الإطفاء، يحاول يرجّع «شاشتي» مرة وحدة بس.
 */
public class HomeGuard {
    private static BroadcastReceiver onWake;
    static volatile boolean onTop = false;

    static void start(Context ctx) {
        if (onWake != null) return;
        final Context app = ctx.getApplicationContext();
        onWake = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> bringHome(app), 2500);
            }
        };
        try { app.registerReceiver(onWake, new IntentFilter(Intent.ACTION_SCREEN_ON)); }
        catch (Exception e) { onWake = null; }
    }

    static boolean canJump(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    static void bringHome(Context c) {
        if (onTop) return;
        try {
            c.startActivity(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (Exception ignored) { }
    }
}
