package com.salati.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;

/** صلاتي — برمجة أبو نواف */
public class MainActivity extends Activity implements SensorEventListener {
    WebView web;
    final Handler ui = new Handler(Looper.getMainLooper());
    SensorManager sm;
    long lastHead = 0;
    String pickKey = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Updater.check(this, "salati");
        Alarms.channels(this);
        web = new WebView(this);
        web.setBackgroundColor(0xff0b1a2a);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) { checkAdhan(getIntent()); }
        });
        web.addJavascriptInterface(new Bridge(), "Sl");
        web.addJavascriptInterface(new Updater.JS(this, "salati"), "AppUpd");
        web.loadUrl("file:///android_asset/index.html");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
        Alarms.schedule(this);
        Widgets.updateAll(this);
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); checkAdhan(i); }

    void checkAdhan(Intent i) {
        if (i != null && i.hasExtra("adhan") && AdhanService.playing) js("window.showAdhan&&showAdhan(" + i.getIntExtra("adhan", 2) + ")");
    }

    @Override protected void onResume() { super.onResume(); js("window.onResumeApp&&onResumeApp()"); }
    @Override protected void onPause() { super.onPause(); if (sm != null) sm.unregisterListener(this); }

    void js(String code) { ui.post(() -> { try { web.evaluateJavascript(code, null); } catch (Exception ignored) {} }); }

    void changed() { Alarms.schedule(this); Widgets.updateAll(this); }

    // ===== البوصلة =====
    final float[] rot = new float[9], ori = new float[3];
    @Override
    public void onSensorChanged(SensorEvent e) {
        long n = System.currentTimeMillis();
        if (n - lastHead < 50) return;
        lastHead = n;
        SensorManager.getRotationMatrixFromVector(rot, e.values);
        SensorManager.getOrientation(rot, ori);
        double az = (Math.toDegrees(ori[0]) + 360) % 360;
        js("window.onHeading&&onHeading(" + Math.round(az) + ")");
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ===== الموقع =====
    void locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 9);
            return;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 6 * 3600000L) { gotLocation(best); return; }
            String prov = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            final Location fallback = best;
            final boolean[] done = {false};
            LocationListener ll = new LocationListener() {
                @Override public void onLocationChanged(Location l) { if (!done[0]) { done[0] = true; gotLocation(l); } }
                @Override public void onStatusChanged(String p, int s, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            lm.requestSingleUpdate(prov, ll, Looper.getMainLooper());
            ui.postDelayed(() -> {
                if (!done[0]) { done[0] = true; lm.removeUpdates(ll); if (fallback != null) gotLocation(fallback); else js("window.onLocated&&onLocated(null)"); }
            }, 15000);
        } catch (Exception e) { js("window.onLocated&&onLocated(null)"); }
    }

    void gotLocation(Location l) {
        Core.sp(this).edit().putFloat("lat", (float) l.getLatitude()).putFloat("lon", (float) l.getLongitude()).putString("city", "📍 موقعي").apply();
        new Thread(() -> {
            String name = "📍 موقعي";
            try {
                android.location.Geocoder g = new android.location.Geocoder(this, new java.util.Locale("ar"));
                java.util.List<android.location.Address> a = g.getFromLocation(l.getLatitude(), l.getLongitude(), 1);
                if (a != null && !a.isEmpty()) {
                    String n = a.get(0).getLocality();
                    if (n == null) n = a.get(0).getSubAdminArea();
                    if (n == null) n = a.get(0).getAdminArea();
                    if (n != null) name = "📍 " + n;
                }
            } catch (Exception ignored) {}
            Core.sp(this).edit().putString("city", name).apply();
            changed();
            js("window.onLocated&&onLocated(" + JSONObject.quote(name) + ")");
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        if (rc == 9) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) locate();
            else js("window.onLocated&&onLocated(null)");
        }
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        if (rc == 31 && res == RESULT_OK && data != null && data.getData() != null && pickKey != null) {
            Uri u = data.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            String name = "ملف صوت";
            try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
                if (c != null && c.moveToFirst()) { int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i); }
            } catch (Exception ignored) {}
            Core.sp(this).edit().putString(pickKey, u.toString()).putString(pickKey + "Name", name).apply();
            js("window.onSound&&onSound()");
        }
    }

    class Bridge {
        @JavascriptInterface
        public String settings() {
            try {
                SharedPreferences s = Core.sp(MainActivity.this);
                JSONObject o = new JSONObject();
                o.put("lat", s.getFloat("lat", 21.4225f)).put("lon", s.getFloat("lon", 39.8262f)).put("city", s.getString("city", "مكة المكرمة"));
                o.put("method", s.getInt("method", 0)).put("asr", s.getInt("asr", 0)).put("pre", s.getInt("pre", 10)).put("iqama", s.getInt("iqama", 0));
                o.put("kahf", s.getBoolean("kahf", true)).put("hadj", s.getInt("hadj", 0)).put("wtheme", s.getInt("wtheme", 0));
                o.put("snd", s.getString("sndName", "")).put("sndFajr", s.getString("sndFajrName", ""));
                JSONArray adj = new JSONArray(), mode = new JSONArray();
                for (int i = 0; i < 6; i++) { adj.put(s.getInt("adj" + i, 0)); mode.put(Core.mode(MainActivity.this, i)); }
                o.put("adj", adj).put("mode", mode);
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                o.put("exact", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
                o.put("battery", ((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()));
                o.put("compass", ((SensorManager) getSystemService(SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface public void setInt(String k, int v) { Core.sp(MainActivity.this).edit().putInt(k, v).apply(); changed(); }
        @JavascriptInterface public void setBool(String k, boolean v) { Core.sp(MainActivity.this).edit().putBoolean(k, v).apply(); changed(); }
        @JavascriptInterface public void setStr(String k, String v) { Core.sp(MainActivity.this).edit().putString(k, v).apply(); changed(); }
        @JavascriptInterface public void setPlace(String name, double lat, double lon) {
            Core.sp(MainActivity.this).edit().putString("city", name).putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply(); changed();
        }

        @JavascriptInterface
        public String day(int offset) {
            try { Calendar d = Calendar.getInstance(); d.add(Calendar.DAY_OF_YEAR, offset); return Core.dayJson(MainActivity.this, d).toString(); }
            catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public String month(int offset) {
            try {
                JSONArray a = new JSONArray();
                Calendar d = Calendar.getInstance();
                d.add(Calendar.MONTH, offset);
                d.set(Calendar.DAY_OF_MONTH, 1);
                int m = d.get(Calendar.MONTH);
                while (d.get(Calendar.MONTH) == m) { a.put(Core.dayJson(MainActivity.this, d)); d.add(Calendar.DAY_OF_YEAR, 1); }
                return a.toString();
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface public double qibla() { SharedPreferences s = Core.sp(MainActivity.this); return Core.qibla(s.getFloat("lat", 21.4225f), s.getFloat("lon", 39.8262f)); }

        @JavascriptInterface public void locate() { ui.post(MainActivity.this::locate); }

        @JavascriptInterface
        public void compass(boolean on) {
            ui.post(() -> {
                if (sm == null) sm = (SensorManager) getSystemService(SENSOR_SERVICE);
                sm.unregisterListener(MainActivity.this);
                if (on) { Sensor s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR); if (s != null) sm.registerListener(MainActivity.this, s, SensorManager.SENSOR_DELAY_UI); }
            });
        }

        @JavascriptInterface
        public void pickSound(String key) {
            pickKey = key;
            ui.post(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");
                try { startActivityForResult(i, 31); } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface public void clearSound(String key) { Core.sp(MainActivity.this).edit().remove(key).remove(key + "Name").apply(); }

        @JavascriptInterface
        public void testAdhan(int p) {
            Intent s = new Intent(MainActivity.this, AdhanService.class).putExtra("p", p).putExtra("test", true);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        }

        @JavascriptInterface public void stopAdhan() { startService(new Intent(MainActivity.this, AdhanService.class).setAction("stop")); }
        @JavascriptInterface public boolean playing() { return AdhanService.playing; }

        @JavascriptInterface
        public void askExact() {
            ui.post(() -> { try { if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {} });
        }

        @JavascriptInterface
        public void askBattery() {
            ui.post(() -> {
                try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {} }
            });
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)); else v.vibrate(ms);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface public String getStr(String k, String d) { return Core.sp(MainActivity.this).getString(k, d); }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.goBack?goBack():false", v -> { if (!"true".equals(v)) finish(); });
    }
}
