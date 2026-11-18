package com.mushaghli.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final String BASE = "https://mushaghli.local/";
    private static final int PICK_FILE = 1001;

    private WebView web;
    private WebView yt;
    private WebView mp;
    private String mpHome = "https://www.google.com/maps";
    private String ytHome = "https://m.youtube.com/";
    private String ytDefaultUA;
    private FrameLayout root;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCb;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Updater.check(this, "mushaghli");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        setupYouTube();
        setupMaps();
        goFullScreen();

        WebSettings s = web.getSettings();
        s.setTextZoom(100); // حجم الخط ثابت عشان الصف اللي فوق يدخل في الشاشة
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // ملاءمة تلقائية: نرسل مقاس الشاشة الحقيقي للواجهة كل ما تغيّر
        web.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol && b - t == ob - ot) return;
            float dn = getResources().getDisplayMetrics().density;
            web.evaluateJavascript("window.fitScreen&&fitScreen(" + ((r - l) / dn) + "," + ((b - t) / dn) + ")", null);
        });

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
                String mime = path.endsWith(".html") ? "text/html"
                        : path.endsWith(".jpg") ? "image/jpeg"
                        : path.endsWith(".png") ? "image/png" : "application/octet-stream";
                try {
                    return new WebResourceResponse(mime, "UTF-8", getAssets().open(path));
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
            }
        });

        s.setGeolocationEnabled(true);
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 7);
        } else if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
                != PackageManager.PERMISSION_GRANTED) {
            // عشان نعرف إذا فيه جهاز بلوتوث متصل
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 8);
        }

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

        web.addJavascriptInterface(new Bridge(), "Native");
        web.addJavascriptInterface(new Updater.JS(this, "mushaghli"), "AppUpd");
        web.addJavascriptInterface(new BlackScreen.JS(this), "BlackScr");
        web.addJavascriptInterface(new YtBridge(), "YT");
        web.addJavascriptInterface(new MapBridge(), "MP");
        web.loadUrl(BASE + "index.html");
    }

    /** جسر بين الصفحة والجهاز: يجيب قائمة التطبيقات المثبتة ويفتحها */
    private class Bridge {
        @JavascriptInterface
        public boolean saveFile(String name, String text) {
            // يحفظ النسخة الاحتياطية في مجلد التنزيلات
            try {
                byte[] b = text.getBytes("UTF-8");
                if (Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download");
                    Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u == null) return false;
                    java.io.OutputStream o = getContentResolver().openOutputStream(u);
                    o.write(b); o.close();
                    return true;
                }
                java.io.File d = getExternalFilesDir(null);
                if (d == null) return false;
                java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(d, name));
                o.write(b); o.close();
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface
        public String getApps() {
            JSONArray arr = new JSONArray();
            try {
                PackageManager pm = getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                for (ResolveInfo r : list) {
                    String pkg = r.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;
                    JSONObject o = new JSONObject();
                    o.put("n", String.valueOf(r.loadLabel(pm)));
                    o.put("pkg", pkg);
                    o.put("img", iconData(r.loadIcon(pm)));
                    arr.put(o);
                }
            } catch (Exception e) { /* نرجع اللي قدرنا عليه */ }
            return arr.toString();
        }

        @JavascriptInterface
        public boolean open(String pkg) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            return true;
        }

        /** يفتح تطبيق جنب مشغّلي (لو الشاشة تدعم تقسيم الشاشة) */
        @JavascriptInterface
        public boolean openSide(String pkg) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            if (Build.VERSION.SDK_INT >= 24) launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            try { startActivity(launch); return true; } catch (Exception e) { return false; }
        }

        /** يحاول يفتح التطبيق كنافذة بمكان المربع بالضبط (يحتاج وضع النوافذ في الشاشة) */
        @JavascriptInterface
        public boolean openInBox(String pkg, int x, int y, int w, int h) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            try {
                if (Build.VERSION.SDK_INT >= 24) {
                    android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                    o.setLaunchBounds(new android.graphics.Rect(x, y, x + w, y + h));
                    try {
                        java.lang.reflect.Method m = android.app.ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class);
                        m.invoke(o, 5); // نافذة حرة
                    } catch (Throwable ignored) { }
                    startActivity(launch, o.toBundle());
                } else {
                    startActivity(launch);
                }
                return true;
            } catch (Exception e) {
                try { startActivity(launch); return true; } catch (Exception e2) { return false; }
            }
        }

        /** حالة الشريط اللي فوق: واي فاي، بلوتوث، موقع، صوت، بطارية */
        @JavascriptInterface
        public String status() {
            JSONObject o = new JSONObject();
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                boolean wOn = wm != null && wm.isWifiEnabled();
                boolean wConn = false;
                int lvl = -1;
                if (cm != null) {
                    NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    wConn = ni != null && ni.isConnected();
                }
                if (wConn && wm != null) {
                    WifiInfo wi = wm.getConnectionInfo();
                    if (wi != null) lvl = WifiManager.calculateSignalLevel(wi.getRssi(), 4);
                }
                o.put("wOn", wOn); o.put("wConn", wConn); o.put("wLvl", lvl);
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
                o.put("bOn", bOn); o.put("bConn", bConn);
            } catch (Exception e) { }
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                o.put("gps", lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
            } catch (Exception e) { }
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                o.put("vol", max > 0 ? Math.round(cur * 100f / max) : -1);
            } catch (Exception e) { }
            try {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int pct = -1; boolean chg = false;
                if (b != null && b.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
                    int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    if (l >= 0 && sc > 0) pct = Math.round(l * 100f / sc);
                    chg = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                }
                o.put("bat", pct); o.put("chg", chg);
            } catch (Exception e) { }
            return o.toString();
        }

        /** يطلّع شريط الصوت حق الشاشة */
        @JavascriptInterface
        public void vol(final int dir) {
            runOnUiThread(() -> {
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    int a = dir > 0 ? AudioManager.ADJUST_RAISE : dir < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_SAME;
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, a, AudioManager.FLAG_SHOW_UI);
                } catch (Exception e) { }
            });
        }

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
    }

    /** يوتيوب الحقيقي داخل مربع المشغل في الواجهة */
    private void setupYouTube() {
        yt = new WebView(this);
        WebSettings s = yt.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        ytDefaultUA = s.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(yt, true);
        yt.setBackgroundColor(0xFF000000);
        yt.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                // نخلي كل شي داخل المربع، وما نفتح تطبيق يوتيوب برا
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }
        });
        yt.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullView != null) { cb.onCustomViewHidden(); return; }
                fullView = view; fullCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                goFullScreen();
            }
            @Override
            public void onHideCustomView() { exitVideoFull(); }
        });
        yt.setVisibility(View.GONE);
        root.addView(yt, new FrameLayout.LayoutParams(1, 1));
    }

    /** الخرائط داخل المربع الثاني (وضع الشاشتين) */
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
                if (mp.getUrl() == null) mp.loadUrl(mpHome);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                lp.leftMargin = x; lp.topMargin = y;
                mp.setLayoutParams(lp);
                mp.setVisibility(View.VISIBLE);
            });
        }
        @JavascriptInterface
        public void hide() { runOnUiThread(() -> mp.setVisibility(View.GONE)); }
        @JavascriptInterface
        public void home() { runOnUiThread(() -> mp.loadUrl(mpHome)); }
        @JavascriptInterface
        public void back() { runOnUiThread(() -> { if (mp.canGoBack()) mp.goBack(); }); }
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
                if (yt.getUrl() == null) yt.loadUrl(ytHome);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                lp.leftMargin = x; lp.topMargin = y;
                yt.setLayoutParams(lp);
                yt.setVisibility(View.VISIBLE);
            });
        }
        @JavascriptInterface
        public void hide() {
            runOnUiThread(() -> yt.setVisibility(View.GONE));
        }
        @JavascriptInterface
        public void run(final String js) {
            runOnUiThread(() -> yt.evaluateJavascript(js, null));
        }
        @JavascriptInterface
        public void back() {
            runOnUiThread(() -> { if (yt.canGoBack()) yt.goBack(); });
        }
        @JavascriptInterface
        public void home() {
            runOnUiThread(() -> yt.loadUrl(ytHome));
        }
        /** يغيّر نسخة يوتيوب اللي داخل المربع */
        @JavascriptInterface
        public void setMode(final String url, final String ua) {
            runOnUiThread(() -> {
                boolean changed = !url.equals(ytHome);
                ytHome = url;
                String want = (ua == null || ua.isEmpty()) ? ytDefaultUA : ua;
                if (!want.equals(yt.getSettings().getUserAgentString())) {
                    yt.getSettings().setUserAgentString(want);
                    changed = true;
                }
                if (changed || yt.getUrl() == null) yt.loadUrl(ytHome);
            });
        }
    }

    private String iconData(Drawable d) {
        try {
            int size = 96;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    /** يفتح التطبيقات والإعدادات بدال ما يفتحها داخل الصفحة */
    private boolean handleUrl(String url) {
        if (url.startsWith(BASE)) return false;
        if (url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:")) return false;
        if (url.contains("youtube.com/embed") || url.contains("youtube-nocookie.com/embed")) return false;

        try {
            if (url.startsWith("intent:")) {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String pkg = intent.getPackage();
                // فتح تطبيق مثبت
                if (pkg != null && Intent.ACTION_MAIN.equals(intent.getAction())) {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch != null) {
                        startActivity(launch);
                        return true;
                    }
                    String fb = intent.getStringExtra("browser_fallback_url");
                    if (fb != null) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fb)));
                    } else {
                        Toast.makeText(this, "التطبيق هذا مو موجود في الجهاز", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setPackage(pkg);
                startActivity(intent);
                return true;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "ما قدرت أفتحه، جرّب خيار ثاني", Toast.LENGTH_SHORT).show();
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
                for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
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
        if (code == 7) web.evaluateJavascript("typeof wxUpdate==='function'&&wxUpdate()", null);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "<h1>خطأ في تحميل الصفحة</h1>";
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
        // التطبيق شاشة رئيسية، فزر الرجوع يسكّر النوافذ بس وما يطلع من التطبيق
        web.evaluateJavascript(
                "document.querySelectorAll('.overlay.show').forEach(o=>o.classList.remove('show'))", null);
    }

    /** الشاشة الرئيسية ما تنقفل، فنشيّك على التحديث كل ما ترجع لها */
    @Override
    protected void onResume() {
        super.onResume();
        Updater.check(this, "mushaghli");
    }
}
