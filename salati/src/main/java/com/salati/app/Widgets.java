package com.salati.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;
import java.util.Calendar;

/** ويدجتات صلاتي الستة */
public class Widgets {

    static final int[] BGS = {R.drawable.wbg_0, R.drawable.wbg_1, R.drawable.wbg_2, R.drawable.wbg_3, R.drawable.wbg_4, R.drawable.wbg_5, R.drawable.wbg_6, R.drawable.wbg_7};
    static final int[] FIVE = {0, 2, 3, 4, 5};
    static final int[] C = {R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4};
    static final int[] N = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4};
    static final int[] T = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4};

    static int bg(Context c) { int t = Core.sp(c).getInt("wtheme", 0); return BGS[Math.max(0, Math.min(BGS.length - 1, t))]; }

    static PendingIntent open(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    }

    static void common(Context c, RemoteViews v) {
        v.setInt(R.id.root, "setBackgroundResource", bg(c));
        v.setOnClickPendingIntent(R.id.root, open(c));
    }

    static void countdown(RemoteViews v, long target) {
        long now = System.currentTimeMillis();
        v.setChronometer(R.id.cd, SystemClock.elapsedRealtime() + Math.max(0, target - now), null, true);
        v.setChronometerCountDown(R.id.cd, true);
    }

    static RemoteViews w1(Context c) {
        long now = System.currentTimeMillis();
        long[] nx = Core.next(c, now, false);
        int p = (int) nx[0];
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.w1);
        v.setTextViewText(R.id.name, Core.ICONS[p] + " " + Core.NAMES[p]);
        v.setTextViewText(R.id.time, Core.hm(nx[1]));
        countdown(v, nx[1]);
        common(c, v);
        return v;
    }

    static void five(Context c, RemoteViews v) {
        long now = System.currentTimeMillis();
        long[] t = Core.millis(c, Core.dayOf(now));
        long[] nx = Core.next(c, now, false);
        for (int i = 0; i < 5; i++) {
            int p = FIVE[i];
            v.setTextViewText(N[i], Core.NAMES[p]);
            v.setTextViewText(T[i], Core.hm(t[p]).replace(" ص", "").replace(" م", ""));
            v.setInt(C[i], "setBackgroundResource", p == nx[0] ? R.drawable.hl : R.drawable.cell);
        }
    }

    static RemoteViews w2(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.w2);
        five(c, v);
        common(c, v);
        return v;
    }

    static RemoteViews w3(Context c) {
        long now = System.currentTimeMillis();
        Calendar d = Core.dayOf(now);
        long[] nx = Core.next(c, now, false);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.w3);
        v.setTextViewText(R.id.greg, Core.gregStr(d));
        v.setTextViewText(R.id.hijri, Core.hijriStr(c, d));
        v.setTextViewText(R.id.name, "باقي على " + Core.NAMES[(int) nx[0]]);
        countdown(v, nx[1]);
        five(c, v);
        common(c, v);
        return v;
    }

    static RemoteViews w4(Context c) {
        long now = System.currentTimeMillis();
        Calendar d = Core.dayOf(now);
        long[] nx = Core.next(c, now, false);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.w4);
        v.setTextViewText(R.id.greg, Core.gregStr(d) + " • " + Core.hijriStr(c, d));
        v.setTextViewText(R.id.next, Core.ICONS[(int) nx[0]] + " " + Core.NAMES[(int) nx[0]] + " " + Core.hm(nx[1]));
        common(c, v);
        return v;
    }

    static RemoteViews w5(Context c) {
        Calendar d = Core.dayOf(System.currentTimeMillis());
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.w5);
        v.setTextViewText(R.id.day, Core.DAYS[d.get(Calendar.DAY_OF_WEEK) - 1]);
        v.setTextViewText(R.id.hijri, Core.hijriStr(c, d));
        v.setTextViewText(R.id.greg, d.get(Calendar.DAY_OF_MONTH) + " " + Core.GMONTHS[d.get(Calendar.MONTH)] + " " + d.get(Calendar.YEAR));
        common(c, v);
        return v;
    }

    static RemoteViews w6(Context c) {
        long now = System.currentTimeMillis();
        long[] t = Core.millis(c, Core.dayOf(now));
        long[] nx = Core.next(c, now, true);
        int[] cc = {R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5};
        int[] nn = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5};
        int[] tt = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5};
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.w6);
        v.setTextViewText(R.id.hijri, Core.hijriStr(c, Core.dayOf(now)));
        for (int i = 0; i < 6; i++) {
            v.setTextViewText(nn[i], Core.ICONS[i] + " " + Core.NAMES[i]);
            v.setTextViewText(tt[i], Core.hm(t[i]));
            v.setInt(cc[i], "setBackgroundResource", i == nx[0] ? R.drawable.hl : 0);
        }
        common(c, v);
        return v;
    }

    static RemoteViews build(Context c, Class<?> k) {
        if (k == W1.class) return w1(c);
        if (k == W2.class) return w2(c);
        if (k == W3.class) return w3(c);
        if (k == W4.class) return w4(c);
        if (k == W5.class) return w5(c);
        return w6(c);
    }

    static final Class<?>[] ALL = {W1.class, W2.class, W3.class, W4.class, W5.class, W6.class};

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<?> k : ALL) {
            try {
                int[] ids = m.getAppWidgetIds(new ComponentName(c, k));
                if (ids.length > 0) m.updateAppWidget(ids, build(c, k));
            } catch (Throwable ignored) {}
        }
    }

    public static class Base extends AppWidgetProvider {
        @Override
        public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
            try { m.updateAppWidget(ids, build(c, getClass())); } catch (Throwable ignored) {}
            Alarms.schedule(c);
        }
    }

    public static class W1 extends Base {}
    public static class W2 extends Base {}
    public static class W3 extends Base {}
    public static class W4 extends Base {}
    public static class W5 extends Base {}
    public static class W6 extends Base {}
}
