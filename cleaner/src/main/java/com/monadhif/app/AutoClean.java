package com.monadhif.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Calendar;

/** جدولة التنظيف التلقائي */
public class AutoClean {

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("nz", 0); }

    static String label(Context c) {
        SharedPreferences s = sp(c);
        int h = s.getInt("hour", 3);
        String hh = (h == 0 ? 12 : h > 12 ? h - 12 : h) + (h < 12 ? " ص" : " م");
        return (s.getString("freq", "d").equals("w") ? "كل أسبوع " : "كل يوم ") + "الساعة " + hh;
    }

    static long next(Context c) {
        SharedPreferences s = sp(c);
        Calendar k = Calendar.getInstance();
        k.set(Calendar.HOUR_OF_DAY, s.getInt("hour", 3));
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        if (k.getTimeInMillis() <= System.currentTimeMillis() + 60000) k.add(Calendar.DAY_OF_YEAR, 1);
        if (s.getString("freq", "d").equals("w")) {
            long lastAuto = s.getLong("lastAuto", 0);
            while (k.getTimeInMillis() - lastAuto < 6L * 24 * 3600 * 1000) k.add(Calendar.DAY_OF_YEAR, 1);
        }
        return k.getTimeInMillis();
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, AutoReceiver.class).setAction(AutoReceiver.AUTO);
        PendingIntent pi = PendingIntent.getBroadcast(c, 20, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (!sp(c).getBoolean("auto", false)) return;
        long at = next(c);
        sp(c).edit().putLong("nextAuto", at).apply();
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }
}
