package com.signal.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** صفحة إدارة الراوتر داخل التطبيق */
public class RouterActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String url = getIntent().getStringExtra("url");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0b1220"));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(24, 16, 24, 16);
        TextView title = new TextView(this);
        title.setText("إدارة الراوتر  •  " + url);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bar.addView(title, tl);
        Button close = new Button(this);
        close.setText("✕ إغلاق");
        close.setOnClickListener(v -> finish());
        bar.addView(close);
        root.addView(bar);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
                handler.cancel();
            }
            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                // نقبل الشهادة المحلية بس لعناوين الشبكة المنزلية (الراوتر)، وغيرها نرفض
                String h = android.net.Uri.parse(error.getUrl()).getHost();
                if (h != null && (h.startsWith("192.168.") || h.startsWith("10.") || h.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\..*"))) handler.proceed();
                else handler.cancel();
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
