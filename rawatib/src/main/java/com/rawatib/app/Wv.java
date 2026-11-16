package com.rawatib.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;
import java.util.Calendar;

/** يبني أشكال الويدجت كلها، ويختار الشكل حسب المقاس وإعداداتك */
public class Wv {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("pay", Context.MODE_PRIVATE); }

    static float scale(Context c) {
        String f = sp(c).getString("wfont", "m");
        return f.equals("s") ? 0.85f : f.equals("l") ? 1.25f : f.equals("xl") ? 1.45f : 1f;
    }

    static void ts(RemoteViews v, int id, float sp, float k) { v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * k); }

    static PendingIntent open(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    }

    /** المربع الصغير: أقرب صرف، أو الصرف اللي اخترته */
    static RemoteViews small(Context c) {
        long now = System.currentTimeMillis();
        int[] base = Pay.baseDays(c);
        int pick = sp(c).getInt("wsmall", -1);
        int best = 0, bestLeft = Integer.MAX_VALUE;
        if (pick >= 0 && pick <= 3) { best = pick; bestLeft = Pay.daysLeft(Pay.next(base[pick], now), now); }
        else for (int i = 0; i < 3; i++) {
            int l = Pay.daysLeft(Pay.next(base[i], now), now);
            if (l < bestLeft) { bestLeft = l; best = i; }
        }
        float k = scale(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_small);
        v.setTextViewText(R.id.icon, Pay.ICON[best]);
        v.setTextViewText(R.id.title, Pay.NAME[best]);
        v.setTextViewText(R.id.days, bestLeft <= 0 ? "🎉" : String.valueOf(bestLeft));
        v.setTextViewText(R.id.unit, bestLeft <= 0 ? "اليوم الصرف" : (bestLeft == 1 ? "باقي يوم" : "يوم باقي"));
        ts(v, R.id.days, 40, k); ts(v, R.id.title, 13, k); ts(v, R.id.unit, 11, k); ts(v, R.id.icon, 22, k);
        v.setOnClickPendingIntent(R.id.root, open(c));
        v.setInt(R.id.root, "setBackgroundResource", WTheme.bg(c));
        return v;
    }

    static final String[] SHORT = {"الراتب", "المواطن", "الضمان"};

    static RemoteViews strip(Context c) {
        long now = System.currentTimeMillis();
        int[] base = Pay.baseDays(c);
        int[] d = {R.id.d0, R.id.d1, R.id.d2};
        int[] n = {R.id.n0, R.id.n1, R.id.n2};
        float k = scale(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_strip);
        for (int i = 0; i < 3; i++) {
            int l = Pay.daysLeft(Pay.next(base[i], now), now);
            v.setTextViewText(d[i], l <= 0 ? "🎉" : String.valueOf(l));
            v.setTextViewText(n[i], Pay.ICON[i] + " " + SHORT[i]);
            ts(v, d[i], 22, k); ts(v, n[i], 11, k);
        }
        v.setOnClickPendingIntent(R.id.root, open(c));
        v.setInt(R.id.root, "setBackgroundResource", WTheme.bg(c));
        return v;
    }

    static RemoteViews big(Context c) {
        long now = System.currentTimeMillis();
        int[] base = Pay.baseDays(c);
        int[] left = new int[3];
        Calendar[] dates = new Calendar[3];
        int best = 0;
        for (int i = 0; i < 3; i++) {
            dates[i] = Pay.next(base[i], now);
            left[i] = Pay.daysLeft(dates[i], now);
            if (left[i] < left[best]) best = i;
        }
        float k = scale(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.icon, Pay.ICON[best]);
        v.setTextViewText(R.id.title, Pay.NAME[best]);
        v.setTextViewText(R.id.star, Pay.fmt(dates[best]));
        v.setTextViewText(R.id.days, left[best] <= 0 ? "🎉" : String.valueOf(left[best]));
        v.setTextViewText(R.id.unit, left[best] <= 0 ? "اليوم" : "يوم");
        int[] rows = {R.id.r0, R.id.r1, R.id.r2};
        for (int i = 0; i < 3; i++) {
            v.setTextViewText(rows[i], Pay.ICON[i] + " " + Pay.NAME[i] + ": " + Pay.left(left[i]));
            ts(v, rows[i], 13, k);
        }
        ts(v, R.id.title, 18, k); ts(v, R.id.days, 24, k); ts(v, R.id.icon, 30, k); ts(v, R.id.star, 12, k);
        v.setOnClickPendingIntent(R.id.root, open(c));
        v.setInt(R.id.root, "setBackgroundResource", WTheme.bg(c));
        return v;
    }

    /** أي ويدجت تصغّره أو تكبّره يتغير شكله: مربع ← شريط ← كبير */
    static RemoteViews resp(Context c, AppWidgetManager m, int id) {
        if (Build.VERSION.SDK_INT >= 31) {
            java.util.Map<android.util.SizeF, RemoteViews> map = new java.util.HashMap<>();
            map.put(new android.util.SizeF(50f, 50f), small(c));
            map.put(new android.util.SizeF(200f, 50f), strip(c));
            map.put(new android.util.SizeF(240f, 120f), big(c));
            return new RemoteViews(map);
        }
        Bundle o = m.getAppWidgetOptions(id);
        int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110), h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
        if (w < 200) return small(c);
        if (h < 120) return strip(c);
        return big(c);
    }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) { try { m.updateAppWidget(id, resp(c, m, id)); } catch (Exception ignored) {} }
    }
}
