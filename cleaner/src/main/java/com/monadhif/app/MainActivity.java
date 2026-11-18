package com.monadhif.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** منظّف — برمجة أبو نواف */
public class MainActivity extends Activity {
    WebView web;
    final Handler ui = new Handler(Looper.getMainLooper());
    volatile Engine eng = null;
    volatile boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Updater.check(this, "monadhif");
        Remind.schedule(this);
        AutoClean.schedule(this);
        CleanWidget.updateAll(this);
        web = new WebView(this);
        web.setBackgroundColor(0xfff4f7fb);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Nz");
        web.addJavascriptInterface(new Updater.JS(this, "monadhif"), "AppUpd");
        web.loadUrl("file:///android_asset/index.html");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
    }

    @Override
    protected void onResume() {
        super.onResume();
        js("window.onResumeApp&&onResumeApp()");
    }

    void js(String code) { ui.post(() -> { try { web.evaluateJavascript(code, null); } catch (Exception ignored) {} }); }

    static String q(String s) { return JSONObject.quote(s); }

    boolean hasFiles() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasUsage() {
        try {
            AppOpsManager ao = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int m = Build.VERSION.SDK_INT >= 29
                ? ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName())
                : ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
            return m == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    long ramAvail() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    static String iconB64(Drawable d) {
        try {
            int sz = 72;
            Bitmap bm = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, sz, sz);
            d.draw(c);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 90, o);
            return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

    class Bridge {
        @JavascriptInterface
        public String info() {
            try {
                JSONObject j = new JSONObject();
                StatFs st = new StatFs(Environment.getDataDirectory().getPath());
                j.put("total", st.getTotalBytes()).put("free", st.getAvailableBytes());
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                j.put("ramTotal", mi.totalMem).put("ramFree", mi.availMem);
                Intent bt = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (bt != null) {
                    int lv = bt.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = bt.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    j.put("bat", lv * 100 / Math.max(1, sc))
                     .put("temp", bt.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)
                     .put("charging", bt.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
                     .put("health", bt.getIntExtra(BatteryManager.EXTRA_HEALTH, 0));
                }
                j.put("model", Build.MANUFACTURER + " " + Build.MODEL).put("android", Build.VERSION.RELEASE);
                j.put("files", hasFiles()).put("usage", hasUsage());
                return j.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void askFiles() {
            ui.post(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                        catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                    } else {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void askUsage() {
            ui.post(() -> {
                try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception ignored) {} }
            });
        }

        @JavascriptInterface
        public boolean scanned() { return eng != null && eng.done; }

        @JavascriptInterface
        public void scan() {
            if (busy) return;
            busy = true;
            new Thread(() -> {
                Engine e = new Engine();
                try {
                    final long[] last = {0};
                    e.scan((t, p) -> {
                        long n = System.currentTimeMillis();
                        if (n - last[0] > 120 || p == 100) { last[0] = n; js("window.onProg&&onProg(" + q(t) + "," + p + "," + e.scannedFiles + ")"); }
                    });
                    eng = e;
                    js("window.onScan&&onScan(" + e.summary().toString() + ")");
                } catch (Throwable t) {
                    js("window.onErr&&onErr(" + q("صار خطأ وقت الفحص") + ")");
                }
                busy = false;
            }).start();
        }

        @JavascriptInterface
        public String summary() {
            try { return eng == null ? "null" : eng.summary().toString(); } catch (Exception e) { return "null"; }
        }

        @JavascriptInterface
        public String list(String kind) {
            try { return eng == null ? "[]" : eng.list(kind).toString(); } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public void dups() {
            if (eng == null || busy) return;
            busy = true;
            new Thread(() -> {
                try {
                    eng.findDups((t, p) -> js("window.onDupProg&&onDupProg(" + p + ")"));
                    js("window.onDups&&onDups(" + eng.list("dups").toString() + ")");
                } catch (Throwable t) { js("window.onDups&&onDups([])"); }
                busy = false;
            }).start();
        }

        /** يحذف الملفات اللي اختارها المستخدم بس */
        @JavascriptInterface
        public void delete(String json) {
            new Thread(() -> {
                long freed = 0; int n = 0;
                Set<String> gone = new HashSet<>();
                List<String> media = new ArrayList<>();
                try {
                    JSONArray a = new JSONArray(json);
                    String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                    for (int i = 0; i < a.length(); i++) {
                        String p = a.getString(i);
                        if (!p.startsWith(root + "/")) continue;
                        File f = new File(p);
                        long s = f.isFile() ? f.length() : 0;
                        if (f.isDirectory()) {
                            String[] k = f.list();
                            if (k != null && k.length == 0 && f.delete()) { gone.add(p); n++; }
                        } else if (f.delete()) { freed += s; n++; gone.add(p); media.add(p); }
                        if (i % 20 == 0) js("window.onDelProg&&onDelProg(" + (i * 100 / Math.max(1, a.length())) + ")");
                    }
                } catch (Exception ignored) {}
                if (eng != null) eng.forget(gone);
                if (!media.isEmpty()) {
                    try { MediaScannerConnection.scanFile(MainActivity.this, media.toArray(new String[0]), null, null); } catch (Exception ignored) {}
                }
                long total = getSharedPreferences("nz", 0).getLong("freed", 0) + freed;
                getSharedPreferences("nz", 0).edit().putLong("freed", total).putLong("lastClean", System.currentTimeMillis()).apply();
                getSharedPreferences("nz", 0).edit().putLong("lastFreed", freed).apply();
                CleanWidget.updateAll(MainActivity.this);
                js("window.onDeleted&&onDeleted(" + freed + "," + n + ")");
            }).start();
        }

        @JavascriptInterface
        public String stats() {
            try {
                return new JSONObject().put("freed", getSharedPreferences("nz", 0).getLong("freed", 0))
                    .put("last", getSharedPreferences("nz", 0).getLong("lastClean", 0)).toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void apps() {
            new Thread(() -> {
                try {
                    PackageManager pm = getPackageManager();
                    boolean usage = hasUsage();
                    StorageStatsManager ssm = Build.VERSION.SDK_INT >= 26 ? (StorageStatsManager) getSystemService(Context.STORAGE_STATS_SERVICE) : null;
                    Map<String, UsageStats> us = null;
                    if (usage) {
                        UsageStatsManager um = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                        long now = System.currentTimeMillis();
                        us = um.queryAndAggregateUsageStats(now - 120L * 24 * 3600 * 1000, now);
                    }
                    JSONArray a = new JSONArray();
                    for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                        if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
                        if (ai.packageName.equals(getPackageName())) continue;
                        JSONObject o = new JSONObject();
                        o.put("pkg", ai.packageName).put("n", String.valueOf(pm.getApplicationLabel(ai)));
                        o.put("icon", iconB64(pm.getApplicationIcon(ai)));
                        if (usage && ssm != null && Build.VERSION.SDK_INT >= 26) {
                            try {
                                StorageStats ss = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, android.os.Process.myUserHandle());
                                o.put("app", ss.getAppBytes()).put("data", ss.getDataBytes()).put("cache", ss.getCacheBytes());
                            } catch (Exception ignored) {}
                        } else {
                            o.put("app", new File(ai.sourceDir).length());
                        }
                        if (us != null) {
                            UsageStats u = us.get(ai.packageName);
                            o.put("last", u == null ? 0 : u.getLastTimeUsed());
                        }
                        try { o.put("installed", pm.getPackageInfo(ai.packageName, 0).firstInstallTime); } catch (Exception ignored) {}
                        a.put(o);
                    }
                    js("window.onApps&&onApps(" + a.toString() + "," + usage + ")");
                } catch (Throwable t) { js("window.onApps&&onApps([],false)"); }
            }).start();
        }

        @JavascriptInterface
        public void uninstall(String pkg) {
            ui.post(() -> {
                try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))); } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void appSettings(String pkg) {
            ui.post(() -> {
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))); } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void storageSettings() {
            ui.post(() -> {
                try { startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); } catch (Exception ignored) {}
            });
        }

        /** يوقف التطبيقات اللي شغالة في الخلفية ويرجع كم تحررت ذاكرة */
        @JavascriptInterface
        public void boost() {
            new Thread(() -> {
                long before = ramAvail();
                int n = 0;
                try {
                    ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                    for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
                        if (ai.packageName.equals(getPackageName())) continue;
                        if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
                        try { am.killBackgroundProcesses(ai.packageName); n++; } catch (Exception ignored) {}
                    }
                    System.gc();
                    Thread.sleep(1500);
                } catch (Exception ignored) {}
                long after = ramAvail();
                js("window.onBoost&&onBoost(" + Math.max(0, after - before) + "," + n + ")");
            }).start();
        }

        @JavascriptInterface
        public void openFile(String p) {
            ui.post(() -> {
                try {
                    String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                    String dir = new File(p).getParent();
                    // نفتح مدير الملفات على المجلد
                    Uri u = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + Uri.encode(dir.length() > root.length() ? dir.substring(root.length() + 1) : ""));
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(u, "vnd.android.document/directory");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                } catch (Exception e) {
                    try { startActivity(new Intent("android.intent.action.VIEW_DOWNLOADS")); } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void setRemind(boolean on) {
            getSharedPreferences("nz", 0).edit().putBoolean("remind", on).apply();
            Remind.schedule(MainActivity.this);
        }

        @JavascriptInterface
        public String getAuto() {
            try {
                android.content.SharedPreferences sp = getSharedPreferences("nz", 0);
                return new JSONObject().put("on", sp.getBoolean("auto", false)).put("freq", sp.getString("freq", "d"))
                    .put("hour", sp.getInt("hour", 3)).put("apk", sp.getBoolean("autoApk", false)).put("wa", sp.getBoolean("autoWa", false))
                    .put("next", sp.getLong("nextAuto", 0)).put("lastAuto", sp.getLong("lastAuto", 0)).put("label", AutoClean.label(MainActivity.this)).toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public String setAuto(String json) {
            try {
                JSONObject o = new JSONObject(json);
                getSharedPreferences("nz", 0).edit().putBoolean("auto", o.optBoolean("on")).putString("freq", o.optString("freq", "d"))
                    .putInt("hour", o.optInt("hour", 3)).putBoolean("autoApk", o.optBoolean("apk")).putBoolean("autoWa", o.optBoolean("wa")).apply();
                AutoClean.schedule(MainActivity.this);
                CleanWidget.updateAll(MainActivity.this);
            } catch (Exception ignored) {}
            return getAuto();
        }

        @JavascriptInterface
        public void cleanNow() {
            getSharedPreferences("nz", 0).edit().putBoolean("busy", true).putLong("busyAt", System.currentTimeMillis()).apply();
            CleanWidget.updateAll(MainActivity.this);
            AutoReceiver.startJob(MainActivity.this, false);
        }

        @JavascriptInterface
        public boolean getRemind() { return getSharedPreferences("nz", 0).getBoolean("remind", true); }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        js("window.onResumeApp&&onResumeApp()");
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.goBack?goBack():false", v -> { if (!"true".equals(v)) finish(); });
    }
}
