package com.signal.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.TelephonyManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {

    private static final String BASE = "https://signal.local/";
    private WebView web;
    private TelephonyManager tm;
    private SensorManager sm;
    private LocationManager lm;
    private volatile float heading = -1;
    private volatile Location lastLoc;
    private volatile List<CellInfo> cells;
    private final java.util.concurrent.Executor exec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                if (u.startsWith(BASE)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(BASE)) return null;
                String path = request.getUrl().getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                path = path.substring(1);
                try {
                    return new WebResourceResponse(path.endsWith(".html") ? "text/html" : "application/octet-stream", "UTF-8", getAssets().open(path));
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
            }
        });
        web.loadUrl(BASE + "index.html");
        askPermissions();
    }

    private void askPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE}, 1);
        } else {
            startUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        startUpdates();
        web.evaluateJavascript("typeof onPerm==='function'&&onPerm()", null);
    }

    private boolean hasLoc() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startUpdates() {
        if (!hasLoc()) return;
        try {
            for (String prov : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(prov)) {
                    lm.requestLocationUpdates(prov, 3000, 2, this, Looper.getMainLooper());
                    Location l = lm.getLastKnownLocation(prov);
                    if (l != null && (lastLoc == null || l.getTime() > lastLoc.getTime())) lastLoc = l;
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rv != null) sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float[] rot = new float[9], o = new float[3];
        SensorManager.getRotationMatrixFromVector(rot, e.values);
        SensorManager.getOrientation(rot, o);
        float deg = (float) Math.toDegrees(o[0]);
        heading = (deg + 360) % 360;
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }
    @Override public void onLocationChanged(Location l) { lastLoc = l; }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }
    @Override public void onStatusChanged(String p, int s, Bundle b) { }

    private static boolean ok(int v) { return v != Integer.MAX_VALUE && v != Long.MAX_VALUE; }

    private class Bridge {

        @JavascriptInterface
        public String getCells() {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            try {
                root.put("perm", hasLoc());
                root.put("op", String.valueOf(tm.getNetworkOperatorName()));
                root.put("sim", String.valueOf(tm.getSimOperatorName()));
                if (!hasLoc()) { root.put("cells", arr); return root.toString(); }
                if (Build.VERSION.SDK_INT >= 29) {
                    tm.requestCellInfoUpdate(exec, new TelephonyManager.CellInfoCallback() {
                        @Override public void onCellInfo(List<CellInfo> list) { cells = list; }
                    });
                }
                List<CellInfo> list = cells != null ? cells : tm.getAllCellInfo();
                if (list != null) for (CellInfo ci : list) {
                    JSONObject o = new JSONObject();
                    o.put("reg", ci.isRegistered());
                    if (ci instanceof CellInfoLte) {
                        CellInfoLte c = (CellInfoLte) ci;
                        CellIdentityLte id = c.getCellIdentity();
                        CellSignalStrengthLte ss = c.getCellSignalStrength();
                        o.put("t", "4G");
                        if (ok(ss.getRsrp())) o.put("rsrp", ss.getRsrp());
                        if (ok(ss.getRsrq())) o.put("rsrq", ss.getRsrq());
                        if (ok(ss.getRssnr())) o.put("sinr", ss.getRssnr() / (Build.VERSION.SDK_INT >= 29 ? 1.0 : 10.0));
                        if (ok(ss.getRssi())) o.put("rssi", ss.getRssi());
                        if (ok(ss.getTimingAdvance())) o.put("ta", ss.getTimingAdvance());
                        if (ok(id.getEarfcn())) o.put("ch", id.getEarfcn());
                        if (ok(id.getPci())) o.put("pci", id.getPci());
                        if (ok(id.getCi())) o.put("ci", id.getCi());
                        if (ok(id.getTac())) o.put("tac", id.getTac());
                        if (Build.VERSION.SDK_INT >= 28 && ok(id.getBandwidth())) o.put("bw", id.getBandwidth());
                        if (Build.VERSION.SDK_INT >= 30) { int[] bands = id.getBands(); if (bands.length > 0) o.put("band", bands[0]); }
                        if (Build.VERSION.SDK_INT >= 28) { o.put("mcc", id.getMccString()); o.put("mnc", id.getMncString()); }
                    } else if (Build.VERSION.SDK_INT >= 29 && ci instanceof CellInfoNr) {
                        CellInfoNr c = (CellInfoNr) ci;
                        CellIdentityNr id = (CellIdentityNr) c.getCellIdentity();
                        CellSignalStrengthNr ss = (CellSignalStrengthNr) c.getCellSignalStrength();
                        o.put("t", "5G");
                        if (ok(ss.getSsRsrp())) o.put("rsrp", ss.getSsRsrp());
                        if (ok(ss.getSsRsrq())) o.put("rsrq", ss.getSsRsrq());
                        if (ok(ss.getSsSinr())) o.put("sinr", ss.getSsSinr());
                        if (ok(id.getNrarfcn())) o.put("ch", id.getNrarfcn());
                        if (ok(id.getPci())) o.put("pci", id.getPci());
                        if (id.getNci() != Long.MAX_VALUE) o.put("ci", id.getNci());
                        if (ok(id.getTac())) o.put("tac", id.getTac());
                        if (Build.VERSION.SDK_INT >= 30) { int[] bands = id.getBands(); if (bands.length > 0) o.put("band", bands[0]); }
                        o.put("mcc", id.getMccString()); o.put("mnc", id.getMncString());
                    } else if (ci instanceof CellInfoWcdma) {
                        CellInfoWcdma c = (CellInfoWcdma) ci;
                        CellIdentityWcdma id = c.getCellIdentity();
                        o.put("t", "3G");
                        o.put("rssi", c.getCellSignalStrength().getDbm());
                        if (ok(id.getUarfcn())) o.put("ch", id.getUarfcn());
                        if (ok(id.getCid())) o.put("ci", id.getCid());
                    } else if (ci instanceof CellInfoGsm) {
                        CellInfoGsm c = (CellInfoGsm) ci;
                        CellIdentityGsm id = c.getCellIdentity();
                        o.put("t", "2G");
                        o.put("rssi", c.getCellSignalStrength().getDbm());
                        if (ok(id.getCid())) o.put("ci", id.getCid());
                    } else continue;
                    arr.put(o);
                }
                // هل الجوال متصل بـ 5G (حتى لو ما ظهرت خلية 5G)
                if (Build.VERSION.SDK_INT >= 29) {
                    try { root.put("nr", tm.getServiceState() != null && tm.getServiceState().toString().contains("nrState=CONNECTED")); } catch (Exception ignored) { }
                }
                root.put("cells", arr);
            } catch (Exception e) {
                try { root.put("err", e.toString()); root.put("cells", arr); } catch (Exception ignored) { }
            }
            return root.toString();
        }

        @JavascriptInterface
        public String getWifi() {
            JSONObject o = new JSONObject();
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm.getConnectionInfo();
                o.put("on", wm.isWifiEnabled());
                if (wi != null && wi.getNetworkId() != -1) {
                    o.put("ssid", wi.getSSID().replace("\"", ""));
                    o.put("rssi", wi.getRssi());
                    o.put("speed", wi.getLinkSpeed());
                    if (Build.VERSION.SDK_INT >= 21) o.put("freq", wi.getFrequency());
                    o.put("ip", ip(wi.getIpAddress()));
                }
                int gw = wm.getDhcpInfo() != null ? wm.getDhcpInfo().gateway : 0;
                if (gw != 0) o.put("gw", ip(gw));
            } catch (Exception ignored) { }
            return o.toString();
        }

        @JavascriptInterface
        public String getLoc() {
            JSONObject o = new JSONObject();
            try {
                o.put("h", heading);
                Location l = lastLoc;
                if (l != null) { o.put("lat", l.getLatitude()); o.put("lon", l.getLongitude()); o.put("acc", l.getAccuracy()); }
                o.put("gps", lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
            } catch (Exception ignored) { }
            return o.toString();
        }

        @JavascriptInterface
        public void openRouter(String url) {
            Intent i = new Intent(MainActivity.this, RouterActivity.class);
            i.putExtra("url", url);
            startActivity(i);
        }

        @JavascriptInterface
        public void askPerm() {
            runOnUiThread(() -> {
                if (!hasLoc()) askPermissions();
                else {
                    try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Exception ignored) { }
                }
            });
        }
    }

    private static String ip(int a) {
        return (a & 0xff) + "." + ((a >> 8) & 0xff) + "." + ((a >> 16) & 0xff) + "." + ((a >> 24) & 0xff);
    }
}
