package com.salati.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.List;

/** يجدول الحدث الجاي (تنبيه قبل، أذان، إقامة، تحديث الويدجت) ويستقبله */
public class Alarms extends BroadcastReceiver {

    static final String ACT = "com.salati.app.EVENT";

    static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            List<Core.Ev> ev = Core.events(c, System.currentTimeMillis());
            if (ev.isEmpty()) return;
            Core.Ev e = ev.get(0);
            Intent i = new Intent(c, Alarms.class).setAction(ACT).putExtra("type", e.type).putExtra("p", e.prayer).putExtra("at", e.at);
            PendingIntent pi = PendingIntent.getBroadcast(c, 100, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact && e.type.equals("adhan")) {
                PendingIntent show = PendingIntent.getActivity(c, 101, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(e.at, show), pi);
            } else if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi);
            }
        } catch (Throwable ignored) {}
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("pre", "تنبيه قبل الصلاة", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel("time", "دخول وقت الصلاة", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel("iqama", "الإقامة", NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel a = new NotificationChannel("adhan", "الأذان", NotificationManager.IMPORTANCE_HIGH);
        a.setSound(null, null);
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(new NotificationChannel("kahf", "تذكير الجمعة", NotificationManager.IMPORTANCE_DEFAULT));
    }

    static void notify(Context c, String ch, int id, String title, String text) {
        try {
            channels(c);
            PendingIntent open = PendingIntent.getActivity(c, 102, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = (Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(text)
                .setAutoCancel(true).setContentIntent(open);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build());
        } catch (Exception ignored) {}
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (ACT.equals(a)) {
            String type = i.getStringExtra("type");
            int p = i.getIntExtra("p", 0);
            long at = i.getLongExtra("at", 0);
            boolean late = System.currentTimeMillis() - at > 10 * 60000L; // ما نأذّن إذا تأخر التنبيه كثير
            if ("pre".equals(type)) {
                int pre = Core.sp(c).getInt("pre", 10);
                notify(c, "pre", 200 + p, "⏳ باقي " + pre + " دقيقة على " + Core.NAMES[p], "الساعة " + Core.hm(at + pre * 60000L));
            } else if ("adhan".equals(type) && !late) {
                int m = Core.mode(c, p);
                if (m == 2 && p != 1) {
                    Intent s = new Intent(c, AdhanService.class).putExtra("p", p);
                    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
                } else if (m >= 1) {
                    notify(c, "time", 300 + p, Core.ICONS[p] + " حان وقت " + (p == 1 ? "الشروق" : "صلاة " + Core.NAMES[p]), Core.hm(at));
                }
            } else if ("iqama".equals(type) && !late) {
                notify(c, "iqama", 400 + p, "🕌 إقامة صلاة " + Core.NAMES[p], "قامت الصلاة");
            } else if ("kahf".equals(type)) {
                notify(c, "kahf", 500, "📖 جمعة مباركة", "لا تنسى قراءة سورة الكهف والصلاة على النبي ﷺ");
            }
        }
        Widgets.updateAll(c);
        schedule(c);
    }
}
