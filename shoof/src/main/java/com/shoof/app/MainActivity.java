package com.shoof.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** شوف — يوتيوب بدون إعلانات • برمجة أبو نواف */
public class MainActivity extends Activity {

    static final String HOME = "https://m.youtube.com/";
    static final String[] BLOCK_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagservices.com", "imasdk.googleapis.com", "adservice.google.", "youtube.com/pagead",
        "youtube.com/api/stats/ads", "youtube.com/ptracking", "youtube.com/get_midroll", "/pagead/",
        "youtube.com/api/stats/atr", "youtube.com/youtubei/v1/log_event"
    };

    private WebView web;
    private FrameLayout root, full;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCb;
    private View splash;
    private String js = "";
    private boolean playing = false;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Updater.check(this, "shoof");
        try (InputStream in = getAssets().open("clean.js")) {
            byte[] buf = new byte[in.available()];
            int n = in.read(buf);
            js = new String(buf, 0, n, "UTF-8");
        } catch (Exception ignored) { }

        root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(0xff0f0f12);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        full = new FrameLayout(this);
        full.setBackgroundColor(Color.BLACK);
        full.setVisibility(View.GONE);
        root.addView(full, new FrameLayout.LayoutParams(-1, -1));
        splash = makeSplash();
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new Updater.JS(this, "shoof"), "AppUpd");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                String host = r.getUrl().getHost() == null ? "" : r.getUrl().getHost();
                if (u.startsWith("intent:") || u.startsWith("vnd.youtube")) return true;
                if (host.endsWith("youtube.com") || host.endsWith("youtu.be") || host.endsWith("google.com")
                    || host.endsWith("googleusercontent.com") || host.endsWith("gstatic.com")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                for (String b : BLOCK_HOSTS) {
                    if (u.contains(b)) {
                        return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", null, new ByteArrayInputStream(new byte[0]));
                    }
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) { inject(); }

            @Override
            public void onPageFinished(WebView v, String url) {
                inject();
                hideSplash();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (p > 30) inject();
                if (p > 70) hideSplash();
            }

            // ملء الشاشة للفيديو
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fullView != null) { cb.onCustomViewHidden(); return; }
                fullView = view; fullCb = cb;
                full.addView(view, new FrameLayout.LayoutParams(-1, -1));
                full.setVisibility(View.VISIBLE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() { exitFull(); }
        });

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        if (b != null) web.restoreState(b); else web.loadUrl(HOME);
        h.postDelayed(this::hideSplash, 4000);
        // نتابع هل فيه فيديو شغال (عشان نكمل بالخلفية)
        h.post(watch);
        handle(getIntent());
    }

    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            web.evaluateJavascript("(function(){var v=document.querySelector('video');return !!(v&&!v.paused&&!v.ended)})()",
                r -> {
                    boolean now = "true".equals(r);
                    // نشغّل خدمة الخلفية وقت ما يبدأ الفيديو (والتطبيق قدامك) ونوقفها لما يوقف
                    if (now && !playing) PlayService.start(MainActivity.this);
                    else if (!now && playing) PlayService.stop(MainActivity.this);
                    playing = now;
                });
            h.postDelayed(this, 1500);
        }
    };

    void inject() {
        if (!js.isEmpty()) web.evaluateJavascript(js, null);
    }

    void exitFull() {
        if (fullView == null) return;
        full.removeView(fullView);
        full.setVisibility(View.GONE);
        fullView = null;
        if (fullCb != null) fullCb.onCustomViewHidden();
        fullCb = null;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    /** شاشة البداية: الشعار ورقم النسخة */
    View makeSplash() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(0xff0f0f12);
        l.setClickable(true);

        TextView logo = new TextView(this);
        logo.setText("▶");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(44);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff5b3fd6, 0xffff7a45});
        g.setCornerRadius(dp(30));
        logo.setBackground(g);
        l.addView(logo, new LinearLayout.LayoutParams(dp(110), dp(110)));

        TextView name = new TextView(this);
        name.setText("شوف");
        name.setTextColor(Color.WHITE);
        name.setTextSize(34);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(18), 0, 0);
        l.addView(name);

        TextView sub = new TextView(this);
        sub.setText("فيديوهات بدون إعلانات");
        sub.setTextColor(0xffa1a1aa);
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        l.addView(sub);

        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        TextView cr = new TextView(this);
        cr.setText("برمجة أبو نواف • نسخة " + ver);
        cr.setTextColor(0xff71717a);
        cr.setTextSize(13);
        cr.setGravity(Gravity.CENTER);
        cr.setPadding(0, dp(60), 0, 0);
        l.addView(cr);
        return l;
    }

    void hideSplash() {
        if (splash == null || splash.getVisibility() != View.VISIBLE) return;
        splash.animate().alpha(0f).setDuration(350).withEndAction(() -> splash.setVisibility(View.GONE)).start();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    void handle(Intent i) {
        if (i != null && i.getBooleanExtra("pause", false)) {
            web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.pause()})()", null);
            playing = false;
            PlayService.stop(this);
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handle(i);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // ما نوقف المتصفح، عشان الصوت يكمل بالخلفية
        web.resumeTimers();
    }

    @Override
    protected void onStop() {
        super.onStop();
        web.resumeTimers();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (fullView != null) { exitFull(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        PlayService.stop(this);
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
