package com.tvhome.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

/**
 * حارس الشاشة الرئيسية: كل ما يشتغل التلفزيون (من الإطفاء أو من أول تشغيل)
 * يرجّع «شاشتي» قدّام، لأن بعض التلفزيونات ترجع شاشتها الأصلية غصب.
 */
public class HomeGuard extends Service {
    static final String CH = "guard";
    private BroadcastReceiver onWake;

    /** يشغّل الحارس (لو التطبيق مسموح له يطلع فوق التطبيقات) */
    static void start(Context c) {
        Intent i = new Intent(c, HomeGuard.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) { }
    }

    /** هل عنده إذن «الظهور فوق التطبيقات الأخرى»؟ بدونه التلفزيون يمنعه يرجع قدّام */
    static boolean canJump(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    static void bringHome(Context c) {
        // طريقة احتياطية: تنبيه يفتح شاشتي على طول (تنفع حتى بدون إذن الظهور فوق التطبيقات)
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(new NotificationChannel("guard_jump", "رجوع شاشتي", NotificationManager.IMPORTANCE_HIGH));
            Intent open = new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(c, 7310, open,
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "guard_jump") : new Notification.Builder(c);
            b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("شاشتي").setContentText("رجوع للشاشة الرئيسية")
                    .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX);
            nm.notify(7311, b.build());
        } catch (Exception ignored) { }
        try {
            c.startActivity(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (Exception ignored) { }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "تثبيت الشاشة الرئيسية", NotificationManager.IMPORTANCE_MIN));
            b = new Notification.Builder(this, CH);
        } else b = new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("شاشتي").setContentText("الشاشة الرئيسية مثبّتة").setOngoing(true);
        try {
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(7300, b.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(7300, b.build());
        } catch (Exception e) { stopSelf(); return; }

        onWake = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                // نعطي التلفزيون ثانيتين يفتح شاشته، بعدها نرجّع شاشتي فوقها
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> bringHome(c), 2000);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> bringHome(c), 6000);
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction("android.intent.action.DREAMING_STOPPED");
        registerReceiver(onWake, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (onWake != null) try { unregisterReceiver(onWake); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /** أول ما يشتغل التلفزيون من الصفر */
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            start(c);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> bringHome(c), 3000);
        }
    }
}
