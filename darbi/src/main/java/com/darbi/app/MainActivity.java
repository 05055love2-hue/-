package com.darbi.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;

/** دربي — واجهة شاشة السيارة • برمجة أبو نواف */
public class MainActivity extends Activity implements LocationListener {

    private static final String BASE = "https://darbi.local/";
    private WebView web;
    private WebView yt;
    private FrameLayout root;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCb;
    private static final String YT_HOME = "https://m.youtube.com/";
    private LocationManager lm;
    private static final HashMap<String, String> ICONS = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Updater.check(this, "darbi");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(0xff070b14);
        root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        setupYouTube();
        goFullScreen();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.addJavascriptInterface(new Updater.JS(this, "darbi"), "AppUpd");
        web.addJavascriptInterface(new BlackScreen.JS(this), "BlackScr");
        web.addJavascriptInterface(new YtBridge(), "YTN");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openUrl(request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(BASE)) return null;
                String path = request.getUrl().getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                path = path.substring(1);
                String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".ttf") ? "font/ttf" : "application/octet-stream";
                try {
                    WebResourceResponse r = new WebResourceResponse(mime, mime.startsWith("text") ? "UTF-8" : null, getAssets().open(path));
                    r.setResponseHeaders(java.util.Collections.singletonMap("Access-Control-Allow-Origin", "*"));
                    return r;
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
            }
        });

        new Thread(() -> { try { listApps(); } catch (Throwable ignored) { } }).start();
        web.loadUrl(BASE + "index.html");

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        } else startGps();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 7) {
            startGps();
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 8);
        }
    }

    // ───── الخانة اللي في النص: يوتيوب أو الخرائط يشتغلون داخلها
    private WebView mp;
    private static final String MAP_HOME = "https://www.google.com/maps";

    private WebView makeBox(final boolean maps) {
        final WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setTextZoom(100);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setBackgroundColor(maps ? 0xFF1c2733 : 0xFF000000);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullView != null) { cb.onCustomViewHidden(); return; }
                fullView = view; fullCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                goFullScreen();
            }
            @Override
            public void onHideCustomView() { exitVideoFull(); }
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, true);
            }
        });
        w.setVisibility(View.GONE);
        root.addView(w, new FrameLayout.LayoutParams(1, 1));
        return w;
    }

    private void setupYouTube() {
        yt = makeBox(false);
        mp = makeBox(true);
    }

    private void exitVideoFull() {
        if (fullView == null) return;
        root.removeView(fullView);
        fullView = null;
        if (fullCb != null) fullCb.onCustomViewHidden();
        fullCb = null;
        goFullScreen();
    }

    private WebView box(String which) { return "map".equals(which) ? mp : yt; }
    private String homeOf(String which) { return "map".equals(which) ? MAP_HOME : YT_HOME; }

    private class YtBridge {
        /** الصفحة تقول وين الخانة بالضبط، ونحط يوتيوب أو الخرائط فوقها */
        @JavascriptInterface
        public void show(final String which, final int x, final int y, final int w, final int h) {
            runOnUiThread(() -> {
                WebView v = box(which), other = v == yt ? mp : yt;
                other.setVisibility(View.GONE);
                if (v.getUrl() == null) v.loadUrl(homeOf(which));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                lp.leftMargin = x; lp.topMargin = y;
                v.setLayoutParams(lp);
                v.setVisibility(View.VISIBLE);
            });
        }
        @JavascriptInterface public void hide() { runOnUiThread(() -> { yt.setVisibility(View.GONE); mp.setVisibility(View.GONE); }); }
        @JavascriptInterface public void back(String which) { runOnUiThread(() -> { WebView v = box(which); if (v.canGoBack()) v.goBack(); }); }
        @JavascriptInterface public void home(String which) { runOnUiThread(() -> box(which).loadUrl(homeOf(which))); }
        @JavascriptInterface
        public void toggle() {
            runOnUiThread(() -> yt.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v)return;if(v.paused)v.play();else v.pause();})()", null));
        }
        /** يقدّم أو يرجّع الفيديو بالثواني */
        @JavascriptInterface
        public void seek(final int sec) {
            runOnUiThread(() -> yt.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+(" + sec + "));})()", null));
        }
        @JavascriptInterface
        public void mute() {
            runOnUiThread(() -> yt.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.muted=!v.muted;})()", null));
        }
        @JavascriptInterface
        public void full() {
            runOnUiThread(() -> yt.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v)return;var p=document.querySelector('#player-container-id,.html5-video-player')||v;(p.requestFullscreen||p.webkitRequestFullscreen).call(p);})()", null));
        }
    }

    // ───── السرعة والمكان من الـ GPS
    private void startGps() {
        try {
            lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 50, this);
            Location last = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
            }
            if (last != null) onLocationChanged(last);
        } catch (SecurityException ignored) { } catch (Exception ignored) { }
    }

    private long lastGpsFix = 0;

    @Override
    public void onLocationChanged(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        if (gps) lastGpsFix = SystemClock.elapsedRealtime();
        else if (SystemClock.elapsedRealtime() - lastGpsFix < 10000) return;
        double kmh = (gps && l.hasSpeed()) ? l.getSpeed() * 3.6 : -1;
        String js = "window.onGps&&onGps(" + l.getLatitude() + "," + l.getLongitude() + "," + (Math.round(kmh * 10) / 10.0) + "," + (gps ? "true" : "false") + ")";
        if (web != null) web.post(() -> web.evaluateJavascript(js, null));
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    // ───── قائمة التطبيقات
    private synchronized String listApps() {
        JSONArray arr = new JSONArray();
        PackageManager pm = getPackageManager();
        List<ResolveInfo> list;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            list = pm.queryIntentActivities(i, 0);
        } catch (Throwable e) { return "[]"; }
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (ResolveInfo r : list) {
            try {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                String img = ICONS.get(pkg);
                if (img == null) {
                    img = iconData(r.loadIcon(pm));
                    if (!img.isEmpty()) ICONS.put(pkg, img);
                }
                JSONObject o = new JSONObject();
                o.put("n", String.valueOf(r.loadLabel(pm)));
                o.put("pkg", pkg);
                o.put("img", img);
                arr.put(o);
            } catch (Throwable ignored) { }
        }
        return arr.toString();
    }

    private String iconData(Drawable d) {
        try {
            if (d == null) return "";
            int size = 96;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable e) { return ""; }
    }

    private boolean openUrl(String url) {
        if (url == null) return true;
        if (url.startsWith(BASE)) return false;
        try {
            Intent i;
            if (url.startsWith("tel:")) i = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
            else if (url.startsWith("intent:")) i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            else i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "ما قدرت أفتحه", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private class Bridge {
        @JavascriptInterface public String getApps() { return listApps(); }

        @JavascriptInterface public void openUrl(String url) { runOnUiThread(() -> MainActivity.this.openUrl(url)); }

        @JavascriptInterface
        public boolean openApp(String pkg) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch == null) return false;
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                return true;
            } catch (Exception e) { return false; }
        }

        /** يفتح أول تطبيق موجود من القائمة (مثلاً: خرائط قوقل ثم ويز) */
        @JavascriptInterface
        public String installed(String csv) {
            for (String p : csv.split(",")) {
                if (getPackageManager().getLaunchIntentForPackage(p.trim()) != null) return p.trim();
            }
            return "";
        }

        @JavascriptInterface
        public boolean openAction(String action) {
            try {
                Intent i = new Intent(action);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(getPackageManager()) == null) return false;
                startActivity(i);
                return true;
            } catch (Exception e) { return false; }
        }

        /** أزرار الموسيقى: تشغيل/إيقاف، التالي، السابق — تشتغل مع أي مشغّل */
        @JavascriptInterface
        public void media(int code) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            long t = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
        }

        @JavascriptInterface
        public boolean playing() {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            return am != null && am.isMusicActive();
        }

        /** الصوت: 1 يرفع، -1 ينزّل */
        @JavascriptInterface
        public int volume(int dir) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return -1;
            if (dir != 0) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return max > 0 ? Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max) : -1;
        }

        @JavascriptInterface
        public String status() {
            JSONObject o = new JSONObject();
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                boolean wConn = false;
                if (cm != null) { NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI); wConn = ni != null && ni.isConnected(); }
                o.put("wOn", wm != null && wm.isWifiEnabled());
                o.put("wConn", wConn);
                NetworkInfo an = cm != null ? cm.getActiveNetworkInfo() : null;
                o.put("net", an != null && an.isConnected());
            } catch (Exception ignored) { }
            try {
                BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
                boolean bOn = false, bConn = false;
                try { bOn = ba != null && ba.isEnabled(); } catch (Exception ignored) { }
                if (bOn) try {
                    bConn = ba.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                        || ba.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED;
                } catch (Exception ignored) { }
                o.put("bOn", bOn);
                o.put("bConn", bConn);
            } catch (Exception ignored) { }
            try {
                Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int pct = -1; boolean chg = false;
                if (bi != null && bi.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
                    int l = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    if (l >= 0 && sc > 0) pct = Math.round(l * 100f / sc);
                    chg = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                }
                o.put("bat", pct); o.put("chg", chg);
            } catch (Exception ignored) { }
            return o.toString();
        }
    }

    private void goFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goFullScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("document.documentElement.classList.remove('paused');window.onAppResume&&onAppResume()", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.evaluateJavascript("document.documentElement.classList.add('paused')", null);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // زر الرئيسية: يرجع للواجهة
        if (web != null) web.evaluateJavascript("window.goHome&&goHome()", null);
    }

    @Override
    public void onBackPressed() {
        if (BlackScreen.hide(this)) return;
        if (fullView != null) { exitVideoFull(); return; }
        web.evaluateJavascript("window.goHome&&goHome()", null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception ignored) { }
    }
}
