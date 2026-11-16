package com.monadhif.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.StatFs;
import android.widget.RemoteViews;

/** ويدجت منظّف: المساحة وزر التنظيف */
public class CleanWidget extends AppWidgetProvider {

    static String sz(long b) {
        if (b < 1024) return b + " بايت";
        String[] u = {"ك.ب", "م.ب", "ج.ب", "ت.ب"};
        double v = b; int i = -1;
        do { v /= 1024; i++; } while (v >= 1024 && i < 3);
        return (v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.US, "%.1f", v)) + " " + u[i];
    }

    static String ago(long t) {
        if (t == 0) return "";
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "الحين";
        if (m < 60) return "قبل " + m + " دقيقة";
        long h = m / 60;
        if (h < 24) return "قبل " + h + " ساعة";
        return "قبل " + (h / 24) + " يوم";
    }

    static Bitmap ring(int pct) {
        int s = 240;
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(26);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF r = new RectF(20, 20, s - 20, s - 20);
        p.setColor(0x40ffffff);
        c.drawArc(r, 0, 360, false, p);
        p.setColor(pct >= 90 ? 0xffffd1d1 : 0xffffffff);
        c.drawArc(r, -90, 360f * pct / 100f, false, p);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xffffffff);
        t.setTextSize(64);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextAlign(Paint.Align.CENTER);
        c.drawText(pct + "%", s / 2f, s / 2f + 22, t);
        return bm;
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, CleanWidget.class));
        if (ids.length > 0) draw(c, m, ids);
    }

    static void draw(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences sp = c.getSharedPreferences("nz", 0);
        StatFs st = new StatFs(Environment.getDataDirectory().getPath());
        long total = st.getTotalBytes(), free = st.getAvailableBytes();
        int pct = (int) ((total - free) * 100 / Math.max(1, total));
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setImageViewBitmap(R.id.ring, ring(pct));
        v.setTextViewText(R.id.free, "فاضي " + sz(free) + " من " + sz(total));
        boolean busy = sp.getBoolean("busy", false) && System.currentTimeMillis() - sp.getLong("busyAt", 0) < 10 * 60 * 1000L;
        long last = sp.getLong("lastClean", 0), lastFreed = sp.getLong("lastFreed", -1);
        String l = last == 0 ? "ما نظّفت للحين" : "آخر تنظيف " + ago(last) + (lastFreed >= 0 ? " • " + sz(lastFreed) : "");
        if (sp.getBoolean("auto", false)) l += "\n⏰ تلقائي " + AutoClean.label(c);
        v.setTextViewText(R.id.last, l);
        v.setTextViewText(R.id.btn, busy ? "⏳ جاري التنظيف…" : "🧹 نظّف الحين");
        Intent now = new Intent(c, AutoReceiver.class).setAction(AutoReceiver.NOW);
        v.setOnClickPendingIntent(R.id.btn, PendingIntent.getBroadcast(c, 21, now, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setOnClickPendingIntent(R.id.ring, PendingIntent.getActivity(c, 22, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        v.setOnClickPendingIntent(R.id.title, PendingIntent.getActivity(c, 23, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(ids, v);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { draw(c, m, ids); }
}
