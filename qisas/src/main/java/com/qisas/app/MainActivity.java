package com.qisas.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/** قصص برودكاست • برمجة أبو نواف */
public class MainActivity extends Activity {

    private static final String BASE = "https://qisas.local/";
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Updater.check(this, "qisas");
        web = new WebView(this);
        web.setBackgroundColor(0xff0f1a17);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.addJavascriptInterface(new Updater.JS(this, "qisas"), "AppUpd");
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
                try {
                    return new WebResourceResponse("text/html", "UTF-8", getAssets().open(path.substring(1)));
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
            }
        });
        if (b != null) web.restoreState(b); else web.loadUrl(BASE + "index.html");
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

    class Bridge {
        /** مشاركة القصة لأي تطبيق (واتساب، سناب، تلقرام...) */
        @JavascriptInterface
        public void share(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(Intent.createChooser(i, "أرسل القصة")); } catch (Exception ignored) { }
            });
        }

        /** إرسال مباشر للواتساب */
        @JavascriptInterface
        public void whatsapp(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                i.setPackage("com.whatsapp");
                try { startActivity(i); }
                catch (Exception e) {
                    i.setPackage("com.whatsapp.w4b");
                    try { startActivity(i); } catch (Exception e2) { share(text); }
                }
            });
        }

        @JavascriptInterface
        public void copy(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("قصة", text));
                Toast.makeText(MainActivity.this, "تم النسخ ✓", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
