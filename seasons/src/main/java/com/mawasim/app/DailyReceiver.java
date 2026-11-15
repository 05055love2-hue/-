package com.mawasim.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/** يشتغل كل يوم الصبح: يحدّث الويدجت ويرسل تنبيه دخول الموسم أو النجم */
public class DailyReceiver extends BroadcastReceiver {

    static void scheduleNext(Context c) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 7);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 5);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        Intent i = new Intent(c, DailyReceiver.class).setAction("com.mawasim.app.DAILY");
        PendingIntent p = PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), p);
    }

    static void check(Context c) {
        long now = System.currentTimeMillis();
        Seasons.Info in = Seasons.at(now);
        SharedPreferences sp = c.getSharedPreferences("notify", Context.MODE_PRIVATE);
        Calendar cal = Calendar.getInstance();
        String today = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
        if (today.equals(sp.getString("last", ""))) return;
        String title = null, text = null;
        if (Seasons.sameDay(in.mawsimStart, now)) {
            title = Seasons.M_ICON[in.mawsim] + " دخل موسم " + Seasons.M_NAME[in.mawsim];
            text = "اليوم أول يوم في " + Seasons.M_NAME[in.mawsim] + "، وطلع نجم " + Seasons.starName(in.star);
        } else if (Seasons.sameDay(in.nextMawsimStart, now + 86400000L)) {
            title = Seasons.M_ICON[in.nextMawsim] + " بكرة يدخل " + Seasons.M_NAME[in.nextMawsim];
            text = "باقي يوم واحد على موسم " + Seasons.M_NAME[in.nextMawsim];
        } else if (Seasons.sameDay(in.starStart, now)) {
            title = "🌟 طلع نجم " + Seasons.starName(in.star);
            text = "احنا في موسم " + Seasons.M_NAME[in.mawsim] + "، وباقي " + Seasons.daysUntil(now, in.nextMawsimStart) + " يوم على " + Seasons.M_NAME[in.nextMawsim];
        }
        if (title != null) {
            Notify.show(c, Notify.CH_SEASON, 100, title, text);
        }
        sp.edit().putString("last", today).apply();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        SeasonWidget.updateAll(c);
        check(c);
        scheduleNext(c);
    }
}
