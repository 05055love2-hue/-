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

/** ويدجتات صلاتي */
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

    static final String[] SDAYS = {"أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت"};

    static final int[] W7R = {R.drawable.w7r0, R.drawable.w7r1, R.drawable.w7r2, R.drawable.w7r3, R.drawable.w7r4};
    static final int[] W7B = {R.drawable.w7b0, R.drawable.w7b1, R.drawable.w7b2, R.drawable.w7b3, R.drawable.w7b4};
    static final float[] W7S = {0.82f, 1f, 1.2f, 1.4f};

    static int col(org.json.JSONObject o, String k, String d) {
        try { return android.graphics.Color.parseColor(o.optString(k, d)); } catch (Throwable e) { return android.graphics.Color.parseColor(d); }
    }

    static RemoteViews w7(Context c) {
        long now = System.currentTimeMillis();
        Calendar d = Core.dayOf(now);
        int[] h = Core.hijri(c, d);
        long[] nx = Core.next(c, now, false);
        org.json.JSONObject o;
        try { o = new org.json.JSONObject(Core.sp(c).getString("w7", "{}")); } catch (Throwable e) { o = new org.json.JSONObject(); }
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.w7);
        String day = SDAYS[d.get(Calendar.DAY_OF_WEEK) - 1] + "، ";
        v.setTextViewText(R.id.hijri, o.optInt("date", 0) == 1
            ? day + d.get(Calendar.DAY_OF_MONTH) + " " + Core.GMONTHS[d.get(Calendar.MONTH)]
            : day + h[2] + " " + Core.HMONTHS[Math.max(0, Math.min(11, h[1] - 1))]);
        v.setTextViewText(R.id.city, Core.sp(c).getString("city", ""));
        v.setTextViewText(R.id.name, Core.NAMES[(int) nx[0]]);
        v.setTextViewText(R.id.time, Core.hm(nx[1]).replace(" ص", "").replace(" م", ""));
        countdown(v, nx[1]);
        // الخلفية
        int r = Math.max(0, Math.min(4, o.optInt("rad", 3)));
        v.setImageViewResource(R.id.bgimg, W7R[r]);
        v.setInt(R.id.bgimg, "setColorFilter", col(o, "bg", "#d4dde4"));
        v.setInt(R.id.bgimg, "setImageAlpha", Math.round(o.optInt("op", 60) * 2.55f));
        v.setImageViewResource(R.id.boximg, W7B[r]);
        v.setInt(R.id.boximg, "setColorFilter", col(o, "box", "#3f9a84"));
        v.setInt(R.id.boximg, "setImageAlpha", Math.round(o.optInt("bop", 100) * 2.55f));
        // النصوص
        int tx = col(o, "txt", "#111111"), bt = col(o, "btxt", "#ffffff");
        v.setTextColor(R.id.hijri, tx); v.setTextColor(R.id.city, tx); v.setTextColor(R.id.cd, tx);
        v.setInt(R.id.ic1, "setColorFilter", tx); v.setInt(R.id.ic2, "setColorFilter", tx);
        v.setTextColor(R.id.name, bt); v.setTextColor(R.id.time, bt);
        float z = W7S[Math.max(0, Math.min(3, o.optInt("size", 1)))];
        v.setTextViewTextSize(R.id.hijri, android.util.TypedValue.COMPLEX_UNIT_SP, 15 * z);
        v.setTextViewTextSize(R.id.city, android.util.TypedValue.COMPLEX_UNIT_SP, 15 * z);
        v.setTextViewTextSize(R.id.cd, android.util.TypedValue.COMPLEX_UNIT_SP, 18 * z);
        v.setTextViewTextSize(R.id.name, android.util.TypedValue.COMPLEX_UNIT_SP, 15 * z);
        v.setTextViewTextSize(R.id.time, android.util.TypedValue.COMPLEX_UNIT_SP, 19 * z);
        // إظهار وإخفاء
        int G = android.view.View.GONE, V = android.view.View.VISIBLE;
        boolean sd = o.optBoolean("sDate", true), sc = o.optBoolean("sCity", true), si = o.optBoolean("sIcons", true);
        v.setViewVisibility(R.id.row1, sd ? V : G);
        v.setViewVisibility(R.id.row2, sc ? V : G);
        v.setViewVisibility(R.id.info, sd || sc ? V : G);
        v.setViewVisibility(R.id.ic1, si ? V : G);
        v.setViewVisibility(R.id.ic2, si ? V : G);
        v.setViewVisibility(R.id.cd, o.optBoolean("sCd", true) ? V : android.view.View.INVISIBLE);
        v.setViewVisibility(R.id.box, o.optBoolean("sBox", true) ? V : G);
        v.setOnClickPendingIntent(R.id.root, open(c));
        return v;
    }

    static RemoteViews build(Context c, Class<?> k) {
        if (k == W1.class) return w1(c);
        if (k == W2.class) return w2(c);
        if (k == W3.class) return w3(c);
        if (k == W4.class) return w4(c);
        if (k == W5.class) return w5(c);
        if (k == W7.class) return w7(c);
        return w6(c);
    }

    static final Class<?>[] ALL = {W1.class, W2.class, W3.class, W4.class, W5.class, W6.class, W7.class};

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
    public static class W7 extends Base {}
}
