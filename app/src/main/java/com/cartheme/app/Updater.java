package com.cartheme.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** يشيّك إذا فيه نسخة أحدث، وينزّلها ويحدّث التطبيق من داخله — برمجة أبو نواف */
public class Updater {
    static final String BASE = "https://github.com/05055love2-hue/tatbeeqati/releases/latest/download/";

    public static void check(final Activity a, final String key) {
        final SharedPreferences p = a.getSharedPreferences("updater", 0);
        final long now = System.currentTimeMillis();
        if (now - p.getLong("t", 0) < 60 * 1000L) return;
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
                    new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("🎉 تحديث جديد")
                        .setMessage(msg)
                        .setPositiveButton("حدّث الآن", (d, w) -> start(a, BASE + apk))
                        .setNegativeButton("بعدين", null)
                        .setNeutralButton("تخطّي", (d, w) -> p.edit().putInt("skip", code).apply())
                        .show();
                });
            } catch (Throwable ignored) {}
        }).start();
    }

    /** زر «التحقق من التحديث» في صفحة «حول التطبيق» — يشيّك الحين ويقول النتيجة */
    public static void manual(final Activity a, final String key) {
        if (a.isFinishing()) return;
        final AlertDialog wait = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🔄 التحقق من التحديث")
            .setMessage("جاري البحث عن نسخة أحدث…")
            .create();
        wait.show();
        new Thread(() -> {
            String err = null; int code = 0; long mine = 0; String apk = key + ".apk", notes = "", ver = "", myVer = "";
            try {
                long now = System.currentTimeMillis();
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "versions.json?t=" + now).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                StringBuilder sb = new StringBuilder();
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONObject o = new JSONObject(sb.toString()).getJSONObject(key);
                code = o.getInt("code");
                apk = o.optString("apk", apk);
                notes = o.optString("notes", "");
                ver = o.optString("name", "");
                PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                mine = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
                myVer = pi.versionName == null ? "" : pi.versionName;
            } catch (Throwable e) { err = "x"; }
            final boolean fail = err != null, newer = code > mine;
            final String fApk = apk, fNotes = notes, fVer = ver, fMy = myVer;
            a.runOnUiThread(() -> {
                try { wait.dismiss(); } catch (Exception ignored) {}
                if (a.isFinishing()) return;
                AlertDialog.Builder b = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert);
                if (fail) {
                    b.setTitle("📡 ما قدرنا نتحقق").setMessage("تأكد إن الإنترنت شغّال وجرّب مرة ثانية.")
                     .setPositiveButton("حاول مرة ثانية", (d, w) -> manual(a, key)).setNegativeButton("إغلاق", null);
                } else if (newer) {
                    String msg = "فيه نسخة جديدة من التطبيق" + (fVer.isEmpty() ? "" : " (" + fVer + ")") + ".";
                    if (!fNotes.isEmpty()) msg += "\n\nالجديد:\n" + fNotes;
                    b.setTitle("🎉 تحديث جديد").setMessage(msg)
                     .setPositiveButton("حدّث الآن", (d, w) -> start(a, BASE + fApk)).setNegativeButton("بعدين", null);
                } else {
                    b.setTitle("✅ عندك آخر نسخة").setMessage("التطبيق محدّث" + (fMy.isEmpty() ? "" : " — نسخة " + fMy) + ".\nما فيه تحديث جديد الحين.")
                     .setPositiveButton("تمام", null);
                }
                b.show();
            });
        }).start();
    }

    /** يربط صفحة التطبيق بالتحديث: AppUpd.check() و AppUpd.version() */
    public static class JS {
        final Activity a; final String key;
        public JS(Activity a, String key) { this.a = a; this.key = key; }
        @android.webkit.JavascriptInterface
        public void check() { a.runOnUiThread(() -> manual(a, key)); }
        @android.webkit.JavascriptInterface
        public String version() {
            try { String v = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; return v == null ? "" : v; }
            catch (Exception e) { return ""; }
        }
    }

    /** أول مرة: نطلب إذن «تثبيت التطبيقات» ونكمل بروحنا لما يوافق */
    static void start(final Activity a, final String url) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("خطوة وحدة بس")
                .setMessage("عشان التطبيق يحدّث نفسه، فعّل «السماح من هذا المصدر» في الصفحة اللي بتفتح، وارجع. التحديث بيكمل بروحه.")
                .setPositiveButton("تمام", (d, w) -> {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                    } catch (Exception e) { browser(a, url); return; }
                    new Thread(() -> {
                        for (int i = 0; i < 180; i++) {
                            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                            if (a.getPackageManager().canRequestPackageInstalls()) {
                                a.runOnUiThread(() -> download(a, url));
                                return;
                            }
                        }
                    }).start();
                })
                .setNegativeButton("لا", null)
                .show();
            return;
        }
        download(a, url);
    }

    static void download(final Activity a, final String url) {
        if (a.isFinishing()) return;
        final AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⬇️ جاري التحديث")
            .setMessage("جاري تنزيل النسخة الجديدة… 0%")
            .setCancelable(false)
            .create();
        dlg.show();
        new Thread(() -> {
            PackageInstaller.Session s = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                long total = c.getContentLengthLong();
                PackageInstaller pkgi = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(a.getPackageName());
                int id = pkgi.createSession(sp);
                s = pkgi.openSession(id);
                InputStream in = c.getInputStream();
                OutputStream out = s.openWrite("update.apk", 0, total > 0 ? total : -1);
                byte[] buf = new byte[65536];
                long done = 0; int lastPct = -1; int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        final int pct = (int) (done * 100 / total);
                        if (pct != lastPct) { lastPct = pct; a.runOnUiThread(() -> dlg.setMessage("جاري تنزيل النسخة الجديدة… " + pct + "%")); }
                    }
                }
                s.fsync(out);
                out.close();
                in.close();
                Intent cb = new Intent(a, InstallReceiver.class);
                int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pi = PendingIntent.getBroadcast(a, 77, cb, fl);
                s.commit(pi.getIntentSender());
                s.close();
                a.runOnUiThread(() -> { try { dlg.dismiss(); } catch (Exception ignored) {} });
            } catch (final Throwable e) {
                if (s != null) try { s.abandon(); } catch (Exception ignored) {}
                a.runOnUiThread(() -> { try { dlg.dismiss(); } catch (Exception ignored) {} browser(a, url); });
            }
        }).start();
    }

    static void browser(Activity a, String url) {
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
    }
}
