package com.tvhome.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** منبه الصلاة: ينبّه وقت الأذان وقبله، حتى لو التلفزيون على تطبيق ثاني */
public class PrayerReceiver extends BroadcastReceiver {
    static final String CH = "prayer";
    static final int MAX = 12;

    static PendingIntent pi(Context c, int i, String title, String text) {
        Intent it = new Intent(c, PrayerReceiver.class).setAction("prayer." + i);
        if (title != null) { it.putExtra("title", title); it.putExtra("text", text); }
        return PendingIntent.getBroadcast(c, 7000 + i, it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void scheduleAll(Context c, String json) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (int i = 0; i < MAX; i++) am.cancel(pi(c, i, null, null));
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length() && i < MAX; i++) {
                JSONObject o = a.getJSONObject(i);
                long t = o.getLong("t");
                PendingIntent p = pi(c, i, o.getString("title"), o.optString("text"));
                boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                try {
                    if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
                } catch (SecurityException e) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String title = i.getStringExtra("title");
        String text = i.getStringExtra("text");
        if (title == null) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "منبه الصلاة", NotificationManager.IMPORTANCE_HIGH);
            Uri snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (snd == null) snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ch.setSound(snd, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("prayer", title);
        PendingIntent op = PendingIntent.getActivity(c, 7100, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(text)
                .setContentIntent(op).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX).setFullScreenIntent(op, true);
        try { nm.notify(7200, b.build()); } catch (SecurityException ignored) { }
        // التطبيق هو الشاشة الرئيسية، فنحاول نرجّعه للواجهة عشان يطلع تنبيه الصلاة الكبير
        try { c.startActivity(open); } catch (Exception ignored) { }
    }
}
