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
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.net.Uri;
import android.widget.Toast;
import android.widget.FrameLayout;
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
    private WebView yt;
    private FrameLayout root;
    private boolean ytFull = false;
    private FrameLayout.LayoutParams ytBoxLp;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private ValueCallback<Uri[]> fileCb;
    private static final int PICK = 4242;

    // واجهة يوتيوب المصممة للتلفزيون والريموت
    private static final String YT_URL = "https://www.youtube.com/tv";
    private static final String TV_UA = "Mozilla/5.0 (Linux; Android 9; Android TV) Cobalt/20.lts.2.0-gold (unlike Gecko) v8/6.5.254.43 gles Starboard/11, Google_ATV_sabrina/RTA1.0 (Google, Chromecast, Wireless) com.google.android.youtube.tv/2.12.08";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Updater.check(this, "tv-launcher");
        HomeGuard.start(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 9);
        }
        setupYouTube();
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
        web.addJavascriptInterface(new YtBridge(), "YT");
        // اختيار صورة من الجهاز للخلفية
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(i, "اختر صورة"), PICK);
                } catch (Exception e) {
                    fileCb = null;
                    Toast.makeText(MainActivity.this, "ما فيه مدير ملفات في التلفزيون، ثبّت واحد من المتجر", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

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

        handlePrayerIntent(getIntent());
        web.loadUrl(BASE + "index.html");
        web.requestFocus();
    }

    /** جسر بين الواجهة والتلفزيون: قائمة التطبيقات وفتحها والإعدادات */
    private class Bridge {
        /** يجدول منبهات الصلاة عشان تشتغل حتى لو كنت داخل تطبيق ثاني */
        @JavascriptInterface
        public void schedulePrayers(String json) {
            PrayerReceiver.scheduleAll(MainActivity.this, json);
        }

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
        public boolean guardOn() {
            return HomeGuard.canJump(MainActivity.this);
        }

        @JavascriptInterface
        public void homeSettings() {
            // أول شيء: نفعّل الحارس اللي يمنع التلفزيون يرجّع شاشته الأصلية
            if (!HomeGuard.canJump(MainActivity.this)) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "شغّل «السماح» لشاشتي، بعدها ارجع", Toast.LENGTH_LONG).show());
                    return;
                } catch (Exception ignored) { }
            }
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                settings();
            }
        }
    }

    private void setupYouTube() {
        yt = new WebView(this);
        WebSettings s = yt.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(TV_UA);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(yt, true);
        yt.setBackgroundColor(0xFF000000);
        yt.setFocusable(true);
        yt.setFocusableInTouchMode(true);
        yt.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }
        });
        yt.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view; customCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override
            public void onHideCustomView() { hideCustom(); }
        });
        yt.setVisibility(View.GONE);
        root.addView(yt, new FrameLayout.LayoutParams(1, 1));
    }

    private void hideCustom() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
    }

    private void ytLeave() {
        // يرجع التحكم لأزرار الواجهة
        if (ytFull) {
            ytFull = false;
            if (ytBoxLp != null) yt.setLayoutParams(ytBoxLp);
        }
        web.requestFocus();
        web.evaluateJavascript("typeof ytBlurred==='function'&&ytBlurred()", null);
    }

    private class YtBridge {
        @JavascriptInterface
        public void show(final int x, final int y, final int w, final int h) {
            runOnUiThread(() -> {
                if (yt.getUrl() == null) yt.loadUrl(YT_URL);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                lp.leftMargin = x; lp.topMargin = y;
                ytBoxLp = lp;
                if (!ytFull) yt.setLayoutParams(lp);
                yt.setVisibility(View.VISIBLE);
            });
        }
        @JavascriptInterface
        public void hide() {
            runOnUiThread(() -> {
                if (ytFull) return;
                if (yt.hasFocus()) web.requestFocus();
                yt.setVisibility(View.GONE);
            });
        }
        @JavascriptInterface
        public void run(final String js) {
            runOnUiThread(() -> yt.evaluateJavascript(js, null));
        }
        @JavascriptInterface
        public void focus() {
            runOnUiThread(() -> { yt.setVisibility(View.VISIBLE); yt.requestFocus(); });
        }
        @JavascriptInterface
        public void back() {
            runOnUiThread(() -> {
                // زر الرجوع داخل واجهة يوتيوب نفسها
                yt.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE));
                yt.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE));
            });
        }
        @JavascriptInterface
        public void full() {
            runOnUiThread(() -> {
                ytFull = true;
                yt.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
                yt.setVisibility(View.VISIBLE);
                yt.bringToFront();
                yt.requestFocus();
            });
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
    protected void onPause() {
        super.onPause();
        // لو فتحت تطبيق ثاني، يوقف فيديو اليوتيوب
        if (yt != null) yt.evaluateJavascript("var v=document.querySelector('video');if(v)v.pause()", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        goFullScreen();
        HomeGuard.start(this);
        if (web != null) {
            if (yt == null || !yt.hasFocus()) web.requestFocus();
            web.evaluateJavascript("typeof refreshApps==='function'&&refreshApps();typeof guardLabel==='function'&&guardLabel()", null);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // زر الرجوع في الريموت يسكّر النوافذ بس، وما يطلع من الشاشة الرئيسية
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (customView != null) { hideCustom(); return true; }
                if (ytFull || (yt != null && yt.hasFocus())) { ytLeave(); return true; }
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

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                out = new Uri[n];
                for (int k = 0; k < n; k++) out[k] = data.getClipData().getItemAt(k).getUri();
            } else if (data.getData() != null) out = new Uri[]{data.getData()};
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    private String pendingPrayer;

    private void handlePrayerIntent(Intent i) {
        if (i == null) return;
        String t = i.getStringExtra("prayer");
        if (t == null) return;
        pendingPrayer = t;
        web.postDelayed(() -> {
            if (pendingPrayer != null) {
                web.evaluateJavascript("typeof onPrayerAlarm==='function'&&onPrayerAlarm(" + org.json.JSONObject.quote(pendingPrayer) + ")", null);
                pendingPrayer = null;
            }
        }, 1500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handlePrayerIntent(intent);
    }
}
