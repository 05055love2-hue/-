package com.rawatib.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Calendar;

/** كل يوم الصبح: يحدّث الويدجت وينبّه إذا الصرف اليوم أو بكرة */
public class DailyReceiver extends BroadcastReceiver {

    static void scheduleNext(Context c) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 8); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 5);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        PendingIntent p = PendingIntent.getBroadcast(c, 1, new Intent(c, DailyReceiver.class).setAction("com.rawatib.app.DAILY"),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), p);
    }

    static void check(Context c) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        String today = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
        SharedPreferences sp = c.getSharedPreferences("notify", Context.MODE_PRIVATE);
        if (today.equals(sp.getString("last", ""))) return;
        int[] base = Pay.baseDays(c);
        StringBuilder todayList = new StringBuilder(), tomorrowList = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            int n = Pay.daysLeft(Pay.next(base[i], now), now);
            StringBuilder sb = n == 0 ? todayList : n == 1 ? tomorrowList : null;
            if (sb != null) { if (sb.length() > 0) sb.append(" و"); sb.append(Pay.NAME[i]); }
        }
        if (base[3] != base[0]) {
            int n = Pay.daysLeft(Pay.next(base[3], now), now);
            StringBuilder sb = n == 0 ? todayList : n == 1 ? tomorrowList : null;
            if (sb != null) { if (sb.length() > 0) sb.append(" و"); sb.append(Pay.NAME[3]); }
        }
        if (todayList.length() > 0) Notify.show(c, 10, "💰 ينزل اليوم: " + todayList, "اليوم موعد صرف " + todayList + "، الله يبارك لك فيه");
        if (tomorrowList.length() > 0) Notify.show(c, 11, "⏳ بكرة ينزل: " + tomorrowList, "باقي يوم واحد على صرف " + tomorrowList);
        sp.edit().putString("last", today).apply();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        SeasonWidget.updateAll(c);
        check(c);
        scheduleNext(c);
    }
}
