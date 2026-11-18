package com.tatbeeqati.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** تطبيق موقع «تطبيقاتي» — يفتح الموقع ويثبّت التطبيقات مباشرة — برمجة أبو نواف */
public class MainActivity extends Activity {
    static final String SITE = "https://tatbeeqati-sa.pages.dev/";
    WebView web;
    boolean failed = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Updater.check(this, "tatbeeqati");
        web = new WebView(this);
        web.setBackgroundColor(0xffffffff);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString(s.getUserAgentString() + " TatbeeqatiApp/1");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handle(r.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) { failed = false; }

            @Override
            public void onPageFinished(WebView v, String url) {
                // رقم نسخة التطبيق تحت في الزاوية
                String ver = "";
                try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
                v.evaluateJavascript("(function(){if(document.getElementById('appVer'))return;var d=document.createElement('div');d.id='appVer';"
                    + "d.textContent='تطبيقاتي • نسخة " + ver + " • 🔄 تحقق من التحديث';d.onclick=function(){if(window.AppUpd)AppUpd.check()};d.style.cssText='position:fixed;bottom:6px;left:10px;z-index:99;font:700 12px system-ui,sans-serif;color:#6e6e73;opacity:.8;padding:6px 10px;border-radius:14px;background:rgba(0,0,0,.05);cursor:pointer';"
                    + "document.body.appendChild(d)})()", null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame() && !failed) {
                    failed = true;
                    v.loadDataWithBaseURL(null, OFFLINE, "text/html", "utf-8", null);
                }
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> handle(url));
        String start = SITE;
        if (getIntent() != null && getIntent().getData() != null) start = getIntent().getData().toString();
        web.addJavascriptInterface(new Updater.JS(this, "tatbeeqati"), "AppUpd");
        web.loadUrl(start);
    }

    static final String OFFLINE = "<html dir='rtl'><body style='font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:90vh;color:#1d1d1f;text-align:center'>"
        + "<div style='font-size:60px'>📡</div><h2>ما فيه إنترنت</h2><p style='color:#6e6e73'>تأكد من الاتصال وجرّب مرة ثانية</p>"
        + "<button onclick=\"location.href='" + SITE + "'\" style='background:#0071e3;color:#fff;border:0;border-radius:30px;padding:12px 28px;font-size:16px'>حاول مرة ثانية</button></body></html>";

    /** روابط الموقع تفتح داخل التطبيق، وملفات التطبيقات تتثبت مباشرة، والباقي في المتصفح */
    boolean handle(String url) {
        if (url.toLowerCase().contains(".apk")) {
            // حماية: نثبّت بس تطبيقات أبو نواف من مكانها الرسمي، وأي ملف ثاني يفتح في المتصفح
            if (trusted(url)) install(url); else browser(url);
            return true;
        }
        if (url.startsWith(SITE) || url.startsWith("https://tatbeeqati-sa.pages.dev")) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
        return true;
    }

    void install(final String url) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("خطوة وحدة بس")
                .setMessage("عشان «تطبيقاتي» يثبّت لك التطبيقات، فعّل «السماح من هذا المصدر» في الصفحة اللي بتفتح، وارجع. التثبيت بيكمل بروحه.")
                .setPositiveButton("تمام", (d, w) -> {
                    try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
                    catch (Exception e) { browser(url); return; }
                    new Thread(() -> {
                        for (int i = 0; i < 180; i++) {
                            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                            if (getPackageManager().canRequestPackageInstalls()) { runOnUiThread(() -> download(url)); return; }
                        }
                    }).start();
                })
                .setNegativeButton("لا", null).show();
            return;
        }
        download(url);
    }

    void download(final String url) {
        if (isFinishing()) return;
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("⬇️ جاري التحميل").setMessage("0%").setCancelable(false).create();
        dlg.show();
        new Thread(() -> {
            PackageInstaller.Session s = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                long total = c.getContentLengthLong();
                PackageInstaller pi = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                s = pi.openSession(pi.createSession(sp));
                InputStream in = c.getInputStream();
                OutputStream out = s.openWrite("app.apk", 0, total > 0 ? total : -1);
                byte[] buf = new byte[65536];
                long done = 0; int last = -1; int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        final int pct = (int) (done * 100 / total);
                        if (pct != last) { last = pct; runOnUiThread(() -> dlg.setMessage(pct + "%")); }
                    }
                }
                s.fsync(out); out.close(); in.close();
                int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                s.commit(PendingIntent.getBroadcast(this, 78, new Intent(this, InstallReceiver.class), fl).getIntentSender());
                s.close();
                runOnUiThread(() -> { try { dlg.dismiss(); } catch (Exception ignored) {} });
            } catch (Throwable e) {
                if (s != null) try { s.abandon(); } catch (Exception ignored) {}
                runOnUiThread(() -> { try { dlg.dismiss(); } catch (Exception ignored) {} browser(url); });
            }
        }).start();
    }

    /** المصادر الموثوقة بس: إصدارات أبو نواف في GitHub، وموقع تطبيقاتي */
    static boolean trusted(String url) {
        try {
            Uri u = Uri.parse(url);
            if (!"https".equalsIgnoreCase(u.getScheme())) return false;
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            String path = u.getPath() == null ? "" : u.getPath();
            if (host.equals("github.com") && path.startsWith("/05055love2-hue/tatbeeqati/releases/")) return true;
            return host.equals("tatbeeqati-sa.pages.dev");
        } catch (Exception e) { return false; }
    }

    void browser(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
