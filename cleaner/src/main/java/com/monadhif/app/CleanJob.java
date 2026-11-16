package com.monadhif.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** التنظيف بالخلفية: يحذف الملفات الزايدة الآمنة بس */
public class CleanJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters p) {
        final boolean auto = p.getExtras().getInt("auto", 0) == 1;
        new Thread(() -> {
            SharedPreferences sp = getSharedPreferences("nz", 0);
            long freed = 0; int n = 0;
            try {
                boolean ok = Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
                    : checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
                if (!ok) {
                    notifyMsg("منظّف يحتاج صلاحية الملفات", "افتح التطبيق وفعّل «السماح بإدارة كل الملفات» عشان يقدر ينظّف");
                } else {
                    Engine e = new Engine();
                    e.scan((t, x) -> {});
                    long now = System.currentTimeMillis();
                    List<Engine.F> del = new ArrayList<>();
                    for (String g : new String[]{"cache", "thumbs", "temp", "logs", "empty"}) { List<Engine.F> l = e.groups.get(g); if (l != null) del.addAll(l); }
                    List<Engine.F> part = e.groups.get("partial");
                    if (part != null) for (Engine.F f : part) if (now - f.m > 24 * 3600 * 1000L) del.add(f);
                    if (sp.getBoolean("autoApk", false)) { List<Engine.F> l = e.groups.get("apk"); if (l != null) del.addAll(l); }
                    if (sp.getBoolean("autoWa", false)) { List<Engine.F> l = e.wa.get("statuses"); if (l != null) for (Engine.F f : l) if (now - f.m > 7 * 24 * 3600 * 1000L) del.add(f); }
                    List<String> media = new ArrayList<>();
                    for (Engine.F f : del) {
                        File x = new File(f.p);
                        if (x.isFile() && x.delete()) { freed += f.s; n++; media.add(f.p); }
                    }
                    List<Engine.F> dirs = e.groups.get("emptydir");
                    if (dirs != null) for (Engine.F f : dirs) { File x = new File(f.p); String[] k = x.list(); if (k != null && k.length == 0 && x.delete()) n++; }
                    if (!media.isEmpty()) try { MediaScannerConnection.scanFile(this, media.toArray(new String[0]), null, null); } catch (Exception ignored) {}
                    sp.edit().putLong("freed", sp.getLong("freed", 0) + freed).putLong("lastClean", now).putLong("lastFreed", freed)
                        .putLong(auto ? "lastAuto" : "lastNow", now).apply();
                    if (!auto || freed > 0)
                        notifyMsg("✨ نظّفنا " + CleanWidget.sz(freed), (auto ? "تنظيف تلقائي: " : "") + "حذفنا " + n + " ملف زايد، وجوالك صار أخف");
                }
            } catch (Throwable ignored) {}
            sp.edit().putBoolean("busy", false).apply();
            CleanWidget.updateAll(this);
            jobFinished(p, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return false; }

    void notifyMsg(String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("clean", "التنظيف", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 7, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = (Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "clean") : new Notification.Builder(this))
                .setSmallIcon(android.R.drawable.ic_menu_delete).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true).setContentIntent(open);
            nm.notify(56, b.build());
        } catch (Exception ignored) {}
    }
}
