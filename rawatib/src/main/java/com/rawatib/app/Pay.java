package com.rawatib.app;

import android.content.Context;
import java.util.Calendar;

/** حساب مواعيد الصرف: الجمعة تتقدم للخميس، والسبت تتأخر للأحد */
public class Pay {
    public static final String[] NAME = {"راتب الدولة", "حساب المواطن", "الضمان المطوّر", "راتبي"};
    public static final String[] ICON = {"🏛️", "🏠", "🤝", "💼"};
    static final String[] DAYS = {"الأحد","الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت"};
    static final String[] MONTHS = {"يناير","فبراير","مارس","أبريل","مايو","يونيو","يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"};

    static int[] baseDays(Context c) {
        int priv = c.getSharedPreferences("pay", Context.MODE_PRIVATE).getInt("priv", 27);
        return new int[]{27, 10, 1, priv};
    }

    static Calendar payDate(int day, int y, int m) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, 1);
        int last = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(day, last));
        int w = c.get(Calendar.DAY_OF_WEEK);
        if (w == Calendar.FRIDAY) c.add(Calendar.DAY_OF_MONTH, -1);
        else if (w == Calendar.SATURDAY) c.add(Calendar.DAY_OF_MONTH, 1);
        return c;
    }

    /** أول موعد صرف ما خلص يومه */
    public static Calendar next(int day, long now) {
        Calendar n = Calendar.getInstance();
        n.setTimeInMillis(now);
        int y = n.get(Calendar.YEAR), m = n.get(Calendar.MONTH) - 1;
        for (int i = 0; i < 15; i++) {
            Calendar d = payDate(day, y, m + i);
            if (d.getTimeInMillis() + 86400000L > now) return d;
        }
        return payDate(day, y, m + 1);
    }

    public static int daysLeft(Calendar d, long now) {
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(now);
        t.set(Calendar.HOUR_OF_DAY, 0); t.set(Calendar.MINUTE, 0); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
        return (int) Math.round((d.getTimeInMillis() - t.getTimeInMillis()) / 86400000.0);
    }

    public static String fmt(Calendar d) {
        return DAYS[d.get(Calendar.DAY_OF_WEEK) - 1] + " " + d.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[d.get(Calendar.MONTH)];
    }

    public static String left(int n) {
        if (n <= 0) return "اليوم 🎉";
        if (n == 1) return "بكرة";
        if (n == 2) return "بعد يومين";
        return "باقي " + n + (n <= 10 ? " أيام" : " يوم");
    }
}
