package com.monadhif.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

/** تذكير أسبوعي بالتنظيف */
public class Remind extends BroadcastReceiver {
    static final long WEEK = 7L * 24 * 3600 * 1000;

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 5, new Intent(c, Remind.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (!c.getSharedPreferences("nz", 0).getBoolean("remind", true)) return;
        am.set(AlarmManager.RTC, System.currentTimeMillis() + WEEK, pi);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            StatFs st = new StatFs(Environment.getDataDirectory().getPath());
            int used = (int) (100 - st.getAvailableBytes() * 100 / Math.max(1, st.getTotalBytes()));
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(new NotificationChannel("remind", "تذكير التنظيف", NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open = PendingIntent.getActivity(c, 6, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = (Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "remind") : new Notification.Builder(c))
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentTitle("🧹 صار لك أسبوع ما نظّفت")
                .setContentText("الذاكرة مستخدمة " + used + "٪ — افتح منظّف وسوّ فحص سريع")
                .setAutoCancel(true)
                .setContentIntent(open);
            nm.notify(55, b.build());
        } catch (Exception ignored) {}
        schedule(c);
    }
}
