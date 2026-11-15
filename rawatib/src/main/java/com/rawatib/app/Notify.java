package com.rawatib.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class Notify {
    static final String CH = "pay";

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CH, "مواعيد الصرف", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("تنبيه قبل الصرف بيوم ويوم الصرف");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static void show(Context c, int id, String title, String text) {
        channels(c);
        PendingIntent pi = PendingIntent.getActivity(c, id, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true);
        try { c.getSystemService(NotificationManager.class).notify(id, b.build()); } catch (SecurityException ignored) { }
    }
}
