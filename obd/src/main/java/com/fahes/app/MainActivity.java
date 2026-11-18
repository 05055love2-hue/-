package com.fahes.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** فاحص السيارة: يتصل بقطعة ELM327 بالبلوتوث ويرسل لها أوامر OBD2 */
public class MainActivity extends Activity {

    private static final String BASE = "https://fahes.local/";
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private WebView web;
    private BluetoothSocket sock;
    private InputStream in;
    private OutputStream out;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Updater.check(this, "fahes");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(0xff070b12);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "OBD");
        web.addJavascriptInterface(new Updater.JS(this, "fahes"), "AppUpd");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith(BASE);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(BASE)) return null;
                try {
                    return new WebResourceResponse("text/html", "UTF-8", getAssets().open("index.html"));
                } catch (Exception e) {
                    return null;
                }
            }
        });
        web.loadUrl(BASE + "index.html");
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        js("typeof listDevs==='function'&&listDevs()");
    }

    private void js(final String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    private static String q(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }

    private void closeSock() {
        try { if (sock != null) sock.close(); } catch (Exception ignored) { }
        sock = null; in = null; out = null;
    }

    class Bridge {
        @JavascriptInterface
        public String paired() {
            JSONArray arr = new JSONArray();
            try {
                BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                if (a == null) return "[]";
                for (BluetoothDevice d : a.getBondedDevices()) {
                    JSONObject o = new JSONObject();
                    o.put("n", d.getName());
                    o.put("a", d.getAddress());
                    arr.put(o);
                }
            } catch (SecurityException e) {
                return "[]";
            } catch (Exception ignored) { }
            return arr.toString();
        }

        @JavascriptInterface
        public void connect(final String addr) {
            io.execute(() -> {
                closeSock();
                try {
                    BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                    if (a == null) { js("onObdConn(false," + q("الجهاز ما فيه بلوتوث") + ")"); return; }
                    if (!a.isEnabled()) { js("onObdConn(false," + q("شغّل البلوتوث أول") + ")"); return; }
                    BluetoothDevice d = a.getRemoteDevice(addr);
                    try { a.cancelDiscovery(); } catch (Exception ignored) { }
                    BluetoothSocket s = null;
                    Exception last = null;
                    for (int t = 0; t < 3 && s == null; t++) {
                        try {
                            BluetoothSocket c = t == 0 ? d.createRfcommSocketToServiceRecord(SPP)
                                    : t == 1 ? d.createInsecureRfcommSocketToServiceRecord(SPP)
                                    : (BluetoothSocket) d.getClass().getMethod("createRfcommSocket", int.class).invoke(d, 1);
                            c.connect();
                            s = c;
                        } catch (Exception e) { last = e; }
                    }
                    if (s == null) throw last != null ? last : new Exception("connect failed");
                    sock = s; in = s.getInputStream(); out = s.getOutputStream();
                    js("onObdConn(true," + q("اتصلت بـ " + d.getName()) + ")");
                } catch (SecurityException e) {
                    js("onObdConn(false," + q("التطبيق يحتاج إذن البلوتوث") + ")");
                } catch (Exception e) {
                    js("onObdConn(false," + q("ما قدرت أتصل. تأكد إن السيارة شغالة والقطعة مركبة") + ")");
                }
            });
        }

        @JavascriptInterface
        public void send(final int id, final String cmd) {
            io.execute(() -> {
                String res = "";
                try {
                    if (out == null) throw new Exception("not connected");
                    while (in.available() > 0) in.read();
                    out.write((cmd + "\r").getBytes());
                    out.flush();
                    StringBuilder sb = new StringBuilder();
                    long end = System.currentTimeMillis() + 7000;
                    while (System.currentTimeMillis() < end) {
                        if (in.available() > 0) {
                            int c = in.read();
                            if (c == '>') break;
                            if (c >= 0) sb.append((char) c);
                        } else {
                            Thread.sleep(10);
                        }
                    }
                    res = sb.toString();
                } catch (Exception e) {
                    res = "";
                }
                js("onObdResp(" + id + "," + q(res) + ")");
            });
        }

        @JavascriptInterface
        public void disconnect() {
            io.execute(MainActivity.this::closeSock);
        }
    }

    @Override
    protected void onDestroy() {
        closeSock();
        super.onDestroy();
    }
}
