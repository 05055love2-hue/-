package com.mawasim.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class Notify {
    static final String CH_SEASON = "seasons";
    static final String CH_TIMER = "timers2";

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel s = new NotificationChannel(CH_SEASON, "دخول المواسم", NotificationManager.IMPORTANCE_DEFAULT);
        s.setDescription("تنبيه لما يدخل موسم أو يطلع نجم");
        nm.createNotificationChannel(s);
        NotificationChannel t = new NotificationChannel(CH_TIMER, "المؤقتات", NotificationManager.IMPORTANCE_HIGH);
        t.setDescription("يرن لما يخلص المؤقت");
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm == null) alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        t.setSound(alarm, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        t.enableVibration(true);
        t.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
        nm.createNotificationChannel(t);
    }

    static void show(Context c, String channel, int id, String title, String text) {
        channels(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi).setAutoCancel(true);
        if (CH_TIMER.equals(channel)) {
            b.setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX);
            if (Build.VERSION.SDK_INT < 26) b.setDefaults(Notification.DEFAULT_ALL);
        }
        try {
            c.getSystemService(NotificationManager.class).notify(id, b.build());
        } catch (SecurityException ignored) { }
    }
}
