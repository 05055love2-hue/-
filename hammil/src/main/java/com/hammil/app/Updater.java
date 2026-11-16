package com.hammil.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** يشيّك إذا فيه نسخة أحدث من التطبيق ويعرض للمستخدم زر التحديث — برمجة أبو نواف */
public class Updater {
    static final String BASE = "https://github.com/05055love2-hue/tatbeeqati/releases/latest/download/";

    public static void check(final Activity a, final String key) {
        final SharedPreferences p = a.getSharedPreferences("updater", 0);
        final long now = System.currentTimeMillis();
        if (now - p.getLong("t", 0) < 4 * 3600 * 1000L) return;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "versions.json?t=" + now).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONObject o = new JSONObject(sb.toString()).getJSONObject(key);
                final int code = o.getInt("code");
                final String apk = o.optString("apk", key + ".apk");
                final String notes = o.optString("notes", "");
                final String ver = o.optString("name", "");
                PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                long mine = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
                p.edit().putLong("t", now).apply();
                if (code <= mine || p.getInt("skip", 0) == code) return;
                a.runOnUiThread(() -> {
                    if (a.isFinishing()) return;
                    String msg = "فيه نسخة جديدة من التطبيق" + (ver.isEmpty() ? "" : " (" + ver + ")") + ".";
                    if (!notes.isEmpty()) msg += "\n\nالجديد:\n" + notes;
                    msg += "\n\nاضغط «حدّث الآن» وثبّت الملف فوق النسخة الحالية، وإعداداتك ما تضيع.";
                    new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("🎉 تحديث جديد")
                        .setMessage(msg)
                        .setPositiveButton("حدّث الآن", (d, w) -> {
                            try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BASE + apk))); } catch (Exception ignored) {}
                        })
                        .setNegativeButton("بعدين", null)
                        .setNeutralButton("تخطّي", (d, w) -> p.edit().putInt("skip", code).apply())
                        .show();
                });
            } catch (Throwable ignored) {}
        }).start();
    }
}
