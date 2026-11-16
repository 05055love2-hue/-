package com.monadhif.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.widget.Toast;

/** يستقبل وقت التنظيف التلقائي وزر الويدجت */
public class AutoReceiver extends BroadcastReceiver {
    static final String AUTO = "com.monadhif.app.AUTO";
    static final String NOW = "com.monadhif.app.NOW";

    static void startJob(Context c, boolean auto) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        PersistableBundle b = new PersistableBundle();
        b.putInt("auto", auto ? 1 : 0);
        JobInfo ji = new JobInfo.Builder(auto ? 701 : 702, new ComponentName(c, CleanJob.class))
            .setOverrideDeadline(auto ? 30 * 60 * 1000L : 0)
            .setExtras(b)
            .build();
        js.schedule(ji);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (AUTO.equals(a)) {
            startJob(c, true);
            AutoClean.schedule(c);
        } else if (NOW.equals(a)) {
            c.getSharedPreferences("nz", 0).edit().putBoolean("busy", true).putLong("busyAt", System.currentTimeMillis()).apply();
            CleanWidget.updateAll(c);
            Toast.makeText(c, "🧹 بدأ التنظيف…", Toast.LENGTH_SHORT).show();
            startJob(c, false);
        } else {
            // بعد تشغيل الجوال نرجع نجدول
            AutoClean.schedule(c);
            CleanWidget.updateAll(c);
        }
    }
}
