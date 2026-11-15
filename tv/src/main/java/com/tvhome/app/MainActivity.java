package com.tvhome.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String BASE = "https://tvhome.local/";
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);
        goFullScreen();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.addJavascriptInterface(new Bridge(), "Native");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith(BASE);
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

        web.loadUrl(BASE + "index.html");
        web.requestFocus();
    }

    /** جسر بين الواجهة والتلفزيون: قائمة التطبيقات وفتحها والإعدادات */
    private class Bridge {
        @JavascriptInterface
        public String getApps() {
            JSONArray arr = new JSONArray();
            Set<String> seen = new HashSet<>();
            PackageManager pm = getPackageManager();
            String[] cats = {"android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER};
            for (String cat : cats) {
                try {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(cat);
                    List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                    for (ResolveInfo r : list) {
                        ActivityInfo a = r.activityInfo;
                        String pkg = a.packageName;
                        if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                        JSONObject o = new JSONObject();
                        o.put("n", String.valueOf(r.loadLabel(pm)));
                        o.put("pkg", pkg);
                        Drawable banner = null;
                        try { banner = a.loadBanner(pm); } catch (Exception ignored) { }
                        if (banner == null) {
                            try { banner = pm.getApplicationBanner(pkg); } catch (Exception ignored) { }
                        }
                        if (banner != null) o.put("banner", toData(banner, 320, 180));
                        o.put("img", toData(r.loadIcon(pm), 128, 128));
                        arr.put(o);
                    }
                } catch (Exception ignored) { }
            }
            return arr.toString();
        }

        @JavascriptInterface
        public boolean open(String pkg) {
            PackageManager pm = getPackageManager();
            Intent launch = null;
            if (Build.VERSION.SDK_INT >= 21) launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            return true;
        }

        @JavascriptInterface
        public void settings() {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void homeSettings() {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                settings();
            }
        }
    }

    private String toData(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        goFullScreen();
        if (web != null) {
            web.requestFocus();
            web.evaluateJavascript("typeof refreshApps==='function'&&refreshApps()", null);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // زر الرجوع في الريموت يسكّر النوافذ بس، وما يطلع من الشاشة الرئيسية
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                web.evaluateJavascript("typeof onBack==='function'&&onBack()", null);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
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
}
