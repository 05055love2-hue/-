package com.sayarati.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** سيارتي — لانشر شاشة السيارة. برمجة أبو نواف */
public class MainActivity extends Activity {

    private static final String BASE = "https://sayarati.local/";
    private static final int PICK_FILE = 1001;

    private WebView web;
    private WebView yt;
    private WebView mp;
    private static final String MAP_HOME = "https://www.google.com/maps";
    private FrameLayout root;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCb;
    private String ytDefaultUA;
    private static final String YT_HOME = "https://m.youtube.com/";
    private static final String DESK_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Updater.check(this, "sayarati");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        setupYouTube();
        setupMaps();
        goFullScreen();

        WebSettings s = web.getSettings();
        s.setTextZoom(100);
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setSupportMultipleWindows(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(BASE)) return null;
                String path = request.getUrl().getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                path = path.substring(1);
                String mime = path.endsWith(".html") ? "text/html" : "application/octet-stream";
                try {
                    return new WebResourceResponse(mime, "UTF-8", getAssets().open(path));
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, true);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = params.createIntent();
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(i, PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 7);
        } else {
            askBluetooth();
        }

        web.addJavascriptInterface(new Bridge(), "Native");
        web.addJavascriptInterface(new Updater.JS(this, "sayarati"), "AppUpd");
        new Thread(() -> { try { listApps(); } catch (Throwable ignored) { } }).start();
        web.addJavascriptInterface(new BlackScreen.JS(this), "BlackScr");
        web.addJavascriptInterface(new IconSwitch(this), "AppIcon");
        web.addJavascriptInterface(new YtBridge(), "YTN");
        web.addJavascriptInterface(new MapBridge(), "MPN");
        web.loadUrl(BASE + "index.html");
    }

    /** الصفحة تطلب من الجهاز يفتح رابط أو تطبيق */
    private class Bridge {
        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(() -> handleUrl(url));
        }

        /** قائمة التطبيقات المثبتة في الشاشة مع أيقوناتها */
        @JavascriptInterface
        public String getApps() {
            return listApps();
        }

        /** يفتح صفحة من إعدادات الجهاز (بلوتوث، واي فاي...) */
        @JavascriptInterface
        public boolean openAction(String action) {
            try {
                Intent i = new Intent(action);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(getPackageManager()) == null) return false;
                startActivity(i);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** حالة الشريط اللي فوق: بلوتوث، واي فاي، بطارية */
        @JavascriptInterface
        public String status() {
            JSONObject o = new JSONObject();
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                boolean wOn = wm != null && wm.isWifiEnabled();
                boolean wConn = false;
                if (cm != null) {
                    NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    wConn = ni != null && ni.isConnected();
                }
                o.put("wOn", wOn);
                o.put("wConn", wConn);
            } catch (Exception e) { }
            try {
                BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
                boolean bOn = false, bConn = false;
                try { bOn = ba != null && ba.isEnabled(); } catch (Exception e) { }
                if (bOn) {
                    try {
                        bConn = ba.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                             || ba.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED;
                    } catch (Exception e) { }
                }
                o.put("bOn", bOn);
                o.put("bConn", bConn);
            } catch (Exception e) { }
            try {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int pct = -1;
                boolean chg = false;
                if (b != null && b.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
                    int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    if (l >= 0 && sc > 0) pct = Math.round(l * 100f / sc);
                    chg = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                }
                o.put("bat", pct);
                o.put("chg", chg);
            } catch (Exception e) { }
            return o.toString();
        }

        @JavascriptInterface
        public boolean openApp(String pkg) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch == null) return false;
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** يوتيوب الحقيقي داخل المربع في الواجهة */
    private void setupYouTube() {
        yt = new WebView(this);
        WebSettings s = yt.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        ytDefaultUA = s.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(yt, true);
        yt.setBackgroundColor(0xFF000000);
        yt.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                // كل شي يبقى داخل المربع، وما نفتح تطبيق يوتيوب برا
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }
        });
        yt.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullView != null) { cb.onCustomViewHidden(); return; }
                fullView = view;
                fullCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                goFullScreen();
            }

            @Override
            public void onHideCustomView() { exitVideoFull(); }
        });
        if (getSharedPreferences("yt", 0).getBoolean("desk", false)) s.setUserAgentString(DESK_UA);
        yt.setVisibility(View.GONE);
        root.addView(yt, new FrameLayout.LayoutParams(1, 1));
    }

    private void exitVideoFull() {
        if (fullView == null) return;
        root.removeView(fullView);
        fullView = null;
        if (fullCb != null) fullCb.onCustomViewHidden();
        fullCb = null;
        goFullScreen();
    }

    private class YtBridge {
        @JavascriptInterface
        public void show(final int x, final int y, final int w, final int h) {
            runOnUiThread(() -> {
                if (yt.getUrl() == null) yt.loadUrl(YT_HOME);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                lp.leftMargin = x;
                lp.topMargin = y;
                yt.setLayoutParams(lp);
                yt.setVisibility(View.VISIBLE);
            });
        }

        @JavascriptInterface
        public void hide() {
            runOnUiThread(() -> yt.setVisibility(View.GONE));
        }

        @JavascriptInterface
        public void back() {
            runOnUiThread(() -> { if (yt.canGoBack()) yt.goBack(); });
        }

        @JavascriptInterface
        public void home() {
            runOnUiThread(() -> yt.loadUrl(YT_HOME));
        }

        /** أوامر داخل صفحة يوتيوب: تقديم، ترجيع، تشغيل وإيقاف، التالي */
        @JavascriptInterface
        public void run(final String js) {
            runOnUiThread(() -> yt.evaluateJavascript(js, null));
        }

        @JavascriptInterface
        public void setDesktop(final boolean desk) {
            runOnUiThread(() -> {
                getSharedPreferences("yt", 0).edit().putBoolean("desk", desk).apply();
                yt.getSettings().setUserAgentString(desk ? DESK_UA : ytDefaultUA);
                yt.loadUrl(desk ? "https://www.youtube.com/" : YT_HOME);
            });
        }
    }

    /** الخرائط داخل المربع في الواجهة */
    private void setupMaps() {
        mp = new WebView(this);
        WebSettings s = mp.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setTextZoom(100);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mp, true);
        mp.setBackgroundColor(0xFF1c2733);
        mp.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }
        });
        mp.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, true);
            }
        });
        mp.setVisibility(View.GONE);
        root.addView(mp, new FrameLayout.LayoutParams(1, 1));
    }

    private class MapBridge {
        @JavascriptInterface
        public void show(final int x, final int y, final int w, final int h) {
            runOnUiThread(() -> {
                if (mp.getUrl() == null) mp.loadUrl(MAP_HOME);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                lp.leftMargin = x;
                lp.topMargin = y;
                mp.setLayoutParams(lp);
                mp.setVisibility(View.VISIBLE);
            });
        }

        @JavascriptInterface
        public void hide() {
            runOnUiThread(() -> mp.setVisibility(View.GONE));
        }

        @JavascriptInterface
        public void go(final String url) {
            runOnUiThread(() -> mp.loadUrl(url));
        }

        @JavascriptInterface
        public void home() {
            runOnUiThread(() -> mp.loadUrl(MAP_HOME));
        }

        @JavascriptInterface
        public void back() {
            runOnUiThread(() -> { if (mp.canGoBack()) mp.goBack(); });
        }
    }

    /** قائمة كل تطبيقات الجهاز — الأيقونات تنحفظ عشان المرة الجاية تطلع على طول، وأي تطبيق فيه مشكلة نتخطاه بس */
    static final java.util.concurrent.ConcurrentHashMap<String, String> ICONS = new java.util.concurrent.ConcurrentHashMap<>();
    String listApps() {
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
            } catch (Throwable ignored) { /* نتخطى هذا التطبيق ونكمل الباقي */ }
        }
        return arr.toString();
    }

    private String iconData(Drawable d) {
        try {
            if (d == null) return "";
            int size = 72;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable e) {
            return "";
        }
    }

    private void askBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 8);
        }
    }

    /** أي رابط برا الواجهة يفتح في تطبيقه (يوتيوب، الخرائط، الاتصال...) */
    private boolean handleUrl(String url) {
        if (url == null) return true;
        if (url.startsWith(BASE)) return false;
        if (url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:")) return false;
        try {
            Intent i;
            if (url.startsWith("tel:")) {
                i = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
            } else if (url.startsWith("intent:")) {
                i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "ما قدرت أفتحه", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int k = 0; k < n; k++) result[k] = data.getClipData().getItemAt(k).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 7) {
            web.evaluateJavascript("typeof getWeather==='function'&&getWeather()", null);
            askBluetooth();
        }
    }

    private void goFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goFullScreen();
    }

    @Override
    public void onBackPressed() {
        if (BlackScreen.hide(this)) return;
        if (fullView != null) { exitVideoFull(); return; }
        if (yt != null && yt.getVisibility() == View.VISIBLE && yt.canGoBack()) { yt.goBack(); return; }
        if (mp != null && mp.getVisibility() == View.VISIBLE && mp.canGoBack()) { mp.goBack(); return; }
        // شاشة رئيسية: زر الرجوع يرجّعك للرئيسية بدل ما يطلع من التطبيق
        web.evaluateJavascript("typeof switchPage==='function'&&switchPage('home')", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.check(this, "sayarati");
        if (web != null) web.evaluateJavascript("typeof devStatus==='function'&&devStatus()", null);
    }
}
