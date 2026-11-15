package com.mawasim.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class TimerReceiver extends BroadcastReceiver {

    static PendingIntent pi(Context c, String id, String name) {
        Intent i = new Intent(c, TimerReceiver.class).setAction("timer:" + id);
        if (name != null) i.putExtra("name", name);
        return PendingIntent.getBroadcast(c, id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context c, String id, String name, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent p = pi(c, id, name);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) {
                Intent open = new Intent(c, MainActivity.class);
                PendingIntent show = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), p);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    static void cancel(Context c, String id) {
        c.getSystemService(AlarmManager.class).cancel(pi(c, id, null));
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String name = i.getStringExtra("name");
        if (name == null) name = "المؤقت";
        Notify.show(c, Notify.CH_TIMER, 5000 + Math.abs(i.getAction().hashCode() % 1000),
                "⏰ خلص وقت «" + name + "»", "اضغط هنا عشان تفتح التطبيق");
    }
}
