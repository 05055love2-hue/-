package com.mawasim.app;

import java.util.Calendar;

/** حساب المواسم ونجوم الطوالع (نفس حساب الصفحة) */
public class Seasons {
    public static final String[] M_NAME = {"سهيل","الوسم","المربعانية","الشبط","العقارب","الحميمين","الذراعين","الكنة","الثريا","التويبع","الجوزاء الأولى","الجوزاء الثانية","المرزم","الكليبين"};
    public static final String[] M_ICON = {"⭐","🌧️","🥶","❄️","🦂","🌤️","🌬️","🌫️","✨","🔥","☀️","🌞","🌡️","🥵"};
    static final String[] S_NAME = {"الطرفة","الجبهة","الزبرة","الصرفة","العواء","السماك","الغفر","الزبانا","الإكليل","القلب","الشولة","النعايم","البلدة","سعد الذابح","سعد بلع","سعد السعود","سعد الأخبية","المقدم","المؤخر","الرشا","الشرطين","البطين","الثريا","الدبران","الهقعة","الهنعة","الذراع","النثرة"};
    static final int[] S_DAYS = {13,14,13,13, 13,13,13,13, 13,13,13, 13,13, 13,13,13, 13,13, 13,13, 13,13, 13,13,13,13,13,13};
    static final int[] S_M = {0,0,0,0, 1,1,1,1, 2,2,2, 3,3, 4,4,4, 5,5, 6,6, 7,7, 8,9,10,11,12,13};

    public static final String[] F_NAME = {"الربيع","الصيف","الخريف","الشتاء"};
    public static final String[] F_ICON = {"🌸","☀️","🍂","❄️"};
    static final int[][] F_START = {{2,20},{5,21},{8,22},{11,21}};

    public static class Info {
        public int mawsim, star, nextMawsim, fasl, nextFasl;
        public long starStart, starEnd, mawsimStart, nextMawsimStart, nextFaslStart;
    }

    static long[] starts(int y) {
        long[] r = new long[S_NAME.length + 1];
        Calendar c = day(y, 7, 24);
        for (int i = 0; i < S_NAME.length; i++) {
            r[i] = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, S_DAYS[i]);
        }
        r[S_NAME.length] = c.getTimeInMillis();
        return r;
    }

    static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, d, 0, 0, 0);
        return c;
    }

    public static Info at(long now) {
        Calendar n = Calendar.getInstance();
        n.setTimeInMillis(now);
        int y = n.get(Calendar.YEAR);
        if (now < day(y, 7, 24).getTimeInMillis()) y--;
        long[] a = starts(y), b = starts(y + 1);
        Info in = new Info();
        int i = S_NAME.length - 1;
        for (int k = 0; k < S_NAME.length; k++) if (a[k] <= now && now < a[k + 1]) { i = k; break; }
        in.star = i;
        in.starStart = a[i];
        in.starEnd = a[i + 1];
        in.mawsim = S_M[i];
        for (int k = 0; k < S_NAME.length; k++) if (S_M[k] == in.mawsim) { in.mawsimStart = a[k]; break; }
        in.nextMawsim = (in.mawsim + 1) % M_NAME.length;
        in.nextMawsimStart = 0;
        for (int k = 0; k < S_NAME.length; k++) if (S_M[k] == in.nextMawsim) {
            in.nextMawsimStart = a[k] > now ? a[k] : b[k];
            break;
        }
        // الفصول
        int yy = n.get(Calendar.YEAR);
        int best = 3; long bt = day(yy - 1, 11, 21).getTimeInMillis();
        for (int k = 0; k < 4; k++) {
            long t = day(yy, F_START[k][0], F_START[k][1]).getTimeInMillis();
            if (t <= now && t > bt) { bt = t; best = k; }
        }
        in.fasl = best;
        in.nextFasl = (best + 1) % 4;
        long t = day(yy, F_START[in.nextFasl][0], F_START[in.nextFasl][1]).getTimeInMillis();
        if (t <= now) t = day(yy + 1, F_START[in.nextFasl][0], F_START[in.nextFasl][1]).getTimeInMillis();
        in.nextFaslStart = t;
        return in;
    }

    public static String starName(int i) { return S_NAME[i]; }

    public static int daysUntil(long now, long t) {
        return (int) Math.ceil((t - now) / 86400000.0);
    }

    public static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(), z = Calendar.getInstance();
        x.setTimeInMillis(a); z.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == z.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == z.get(Calendar.DAY_OF_YEAR);
    }
}
