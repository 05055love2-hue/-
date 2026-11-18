package com.widgety.app;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;

/** ويدجتي — ساعة وطقس وموسيقى — برمجة أبو نواف */
public class MainActivity extends Activity {

    private static final String BASE = "https://widgety.local/";
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Updater.check(this, "widgety");
        web = new WebView(this);
        web.setBackgroundColor(0xff0b0d12);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, true);
            }
        });
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
                try {
                    return new WebResourceResponse("text/html", "UTF-8", getAssets().open(path.substring(1)));
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
            }
        });
        if (b != null) web.restoreState(b); else web.loadUrl(BASE + "index.html");

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 7);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (web != null) web.evaluateJavascript("window.wxUpdate&&wxUpdate()", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.goBack?goBack():false", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Widgets.schedule(this);
        Widgets.refreshMusic(this);
        Widgets.updateAll(this);
    }

    class Bridge {
        @JavascriptInterface
        public String media() {
            return Media.read(MainActivity.this).toString();
        }

        @JavascriptInterface
        public void cmd(String a) {
            Media.cmd(MainActivity.this, a);
        }

        @JavascriptInterface
        public void openAccess() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
                }
            });
        }

        /** صفحة معلومات التطبيق: منها نسمح بالإعدادات المقيدة */
        @JavascriptInterface
        public void openAppInfo() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void keepOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        /** الطقس من التطبيق يوصل للويدجت */
        @JavascriptInterface
        public void saveWx(String json) {
            try {
                JSONObject o = new JSONObject(json);
                Widgets.prefs(MainActivity.this).edit()
                    .putString("lat", o.optString("lat", "24.71"))
                    .putString("lon", o.optString("lon", "46.68"))
                    .putString("city", o.optString("city", "الرياض"))
                    .putInt("t", o.optInt("t"))
                    .putInt("code", o.optInt("code"))
                    .putBoolean("day", o.optBoolean("day", true))
                    .putInt("hi", o.optInt("hi"))
                    .putInt("lo", o.optInt("lo"))
                    .putBoolean("hasWx", true)
                    .apply();
                Widgets.updateAll(MainActivity.this);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void setGlass(boolean on) {
            Widgets.prefs(MainActivity.this).edit().putBoolean("glass", on).apply();
            Widgets.updateAll(MainActivity.this);
        }

        /** يطلب من الجوال يحط الويدجت على الشاشة الرئيسية */
        @JavascriptInterface
        public boolean pin(String kind) {
            try {
                AppWidgetManager m = AppWidgetManager.getInstance(MainActivity.this);
                if (Build.VERSION.SDK_INT < 26 || !m.isRequestPinAppWidgetSupported()) return false;
                Class<?> cls = "bar".equals(kind) ? WidgetBar.class : "square".equals(kind) ? WidgetSquare.class : WidgetWide.class;
                return m.requestPinAppWidget(new ComponentName(MainActivity.this, cls), null, null);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
