package com.tatbeeqati.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** يستقبل نتيجة التحديث ويطلب من المستخدم يضغط «تحديث» */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(confirm); } catch (Exception ignored) {}
            }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(c, "ما كمل التحديث، جرّب مرة ثانية", Toast.LENGTH_LONG).show();
        }
    }
}
