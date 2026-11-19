package com.watsy.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;

/** واتسي — أدوات الواتساب في تطبيق واحد • برمجة أبو نواف */
public class MainActivity extends Activity {

    private static final String BASE = "https://watsy.local/";
    private static final int PICK_STATUS = 41;
    private static final String WA_STATUS = "primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses";
    private WebView web;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("watsy", MODE_PRIVATE);
        Updater.check(this, "watsy");
        web = new WebView(this);
        web.setBackgroundColor(0xff0b1512);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.addJavascriptInterface(new Updater.JS(this, "watsy"), "AppUpd");
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
                Uri url = request.getUrl();
                if (!url.toString().startsWith(BASE)) return null;
                String path = url.getPath();
                try {
                    if ("/st".equals(path)) {
                        // صورة أو مقطع من الحالات
                        Uri u = Uri.parse(url.getQueryParameter("u"));
                        String mime = getContentResolver().getType(u);
                        if (mime == null) mime = u.toString().endsWith(".mp4") ? "video/mp4" : "image/jpeg";
                        return new WebResourceResponse(mime, null, getContentResolver().openInputStream(u));
                    }
                    if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                    String mt = path.endsWith(".css") ? "text/css" : path.endsWith(".woff2") ? "font/woff2"
                        : path.endsWith(".ttf") ? "font/ttf" : "text/html";
                    WebResourceResponse r = new WebResourceResponse(mt, mt.startsWith("text") ? "UTF-8" : null, getAssets().open(path.substring(1)));
                    r.setResponseHeaders(java.util.Collections.singletonMap("Access-Control-Allow-Origin", "*"));
                    return r;
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
            }
        });
        if (b != null) web.restoreState(b); else web.loadUrl(BASE + "index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onAppResume&&onAppResume()", null);
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
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_STATUS && res == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            prefs.edit().putString("statusTree", tree.toString()).apply();
            web.evaluateJavascript("window.onStatusReady&&onStatusReady()", null);
        }
    }

    private Uri statusTree() {
        String t = prefs.getString("statusTree", null);
        if (t == null) return null;
        Uri u = Uri.parse(t);
        for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions())
            if (p.getUri().equals(u) && p.isReadPermission()) return u;
        return null;
    }

    private void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    private void openWa(Intent i) {
        i.setPackage("com.whatsapp");
        try { startActivity(i); return; } catch (Exception ignored) { }
        i.setPackage("com.whatsapp.w4b");
        try { startActivity(i); return; } catch (Exception ignored) { }
        i.setPackage(null);
        try { startActivity(i); } catch (Exception e) { toast("ما لقيت الواتساب في جوالك"); }
    }

    /** يحفظ ملف في معرض الصور (مجلد واتسي) ويرجع رابطه */
    private Uri saveToGallery(InputStream in, String name, String mime) throws Exception {
        boolean video = mime.startsWith("video");
        ContentResolver cr = getContentResolver();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/واتسي");
            Uri col = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Uri u = cr.insert(col, v);
            try (OutputStream os = cr.openOutputStream(u)) { copy(in, os); }
            return u;
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES), "Watsy");
            dir.mkdirs();
            File f = new File(dir, name);
            try (OutputStream os = new FileOutputStream(f)) { copy(in, os); }
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DATA, f.getAbsolutePath());
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            Uri col = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Uri u = cr.insert(col, v);
            return u != null ? u : Uri.fromFile(f);
        }
    }

    private static void copy(InputStream in, OutputStream os) throws Exception {
        byte[] buf = new byte[65536]; int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        in.close();
    }

    private boolean needOldPermission() {
        if (Build.VERSION.SDK_INT < 29 && Build.VERSION.SDK_INT >= 23
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7));
            toast("اسمح للتطبيق يحفظ بالمعرض وجرّب مرة ثانية");
            return true;
        }
        return false;
    }

    private void shareUri(Uri u, String mime, boolean toWa) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (toWa) openWa(i);
        else try { startActivity(Intent.createChooser(i, "مشاركة")); } catch (Exception ignored) { }
    }

    private static final java.util.Set<String> WAY = new java.util.HashSet<>(java.util.Arrays.asList(
        "Android", "media", "com.whatsapp", "com.whatsapp.w4b", "WhatsApp", "WhatsApp Business", "Media"));
    private static final String[] COLS = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};

    private void findDirs(Uri tree, String id, int depth, java.util.List<String> found) {
        if (depth > 6) return;
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        try (Cursor c = getContentResolver().query(kids, COLS, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) continue;
                String name = c.getString(1);
                if (".Statuses".equals(name)) found.add(c.getString(0));
                else if (WAY.contains(name)) findDirs(tree, c.getString(0), depth + 1, found);
            }
        } catch (Exception ignored) { }
    }

    private void listDir(Uri tree, String id, JSONArray out) {
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        try (Cursor c = getContentResolver().query(kids, COLS, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String name = c.getString(1);
                String mime = c.getString(2);
                if (name == null || name.startsWith(".")) continue;
                String ln = name.toLowerCase();
                boolean vid = (mime != null && mime.startsWith("video")) || ln.endsWith(".mp4");
                boolean img = (mime != null && mime.startsWith("image")) || ln.endsWith(".jpg") || ln.endsWith(".jpeg") || ln.endsWith(".webp") || ln.endsWith(".png");
                if (!vid && !img) continue;
                JSONObject o = new JSONObject();
                o.put("u", DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString());
                o.put("n", name);
                o.put("v", vid);
                o.put("t", c.getLong(3));
                out.put(o);
            }
        } catch (Exception ignored) { }
    }

    class Bridge {
        // ───── مراسلة رقم بدون حفظه
        @JavascriptInterface
        public void chat(String number, String text) {
            runOnUiThread(() -> {
                try {
                    String url = "https://api.whatsapp.com/send?phone=" + number
                        + (text == null || text.isEmpty() ? "" : "&text=" + URLEncoder.encode(text, "UTF-8"));
                    openWa(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) { }
            });
        }

        // ───── نصوص
        @JavascriptInterface
        public void copy(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("واتسي", text));
                Toast.makeText(MainActivity.this, "تم النسخ ✓", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void sendText(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                openWa(i);
            });
        }

        // ───── الرسائل المحفوظة
        @JavascriptInterface
        public boolean msgAccess() {
            String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return s != null && s.contains(new ComponentName(MainActivity.this, MsgService.class).flattenToString());
        }

        @JavascriptInterface
        public void openMsgAccess() {
            runOnUiThread(() -> {
                try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }
                catch (Exception e) { toast("افتح الإعدادات ← الإشعارات ← الوصول للإشعارات"); }
            });
        }

        @JavascriptInterface
        public String messages() { return MsgService.load(MainActivity.this).toString(); }

        @JavascriptInterface
        public void clearMessages() { MsgService.clear(MainActivity.this); }

        // ───── الحالات
        @JavascriptInterface
        public boolean statusAccess() { return statusTree() != null; }

        @JavascriptInterface
        public void pickStatus() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                if (Build.VERSION.SDK_INT >= 26) {
                    i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", WA_STATUS));
                }
                try { startActivityForResult(i, PICK_STATUS); } catch (Exception e) { toast("ما قدرت أفتح الملفات"); }
            });
        }

        /** يدوّر على مجلد الحالات داخل أي مجلد يختاره المستخدم، ويرجع الصور والمقاطع */
        @JavascriptInterface
        public String statuses() {
            JSONObject res = new JSONObject();
            JSONArray out = new JSONArray();
            try {
                Uri tree = statusTree();
                res.put("ok", tree != null);
                if (tree == null) { res.put("items", out); res.put("dirs", 0); return res.toString(); }
                java.util.ArrayList<String> dirs = new java.util.ArrayList<>();
                String root = DocumentsContract.getTreeDocumentId(tree);
                if (root.endsWith(".Statuses")) dirs.add(root); else findDirs(tree, root, 0, dirs);
                res.put("dirs", dirs.size());
                res.put("picked", root.replace("primary:", ""));
                for (String d : dirs) listDir(tree, d, out);
                res.put("items", out);
            } catch (Exception e) {
                try { res.put("items", out); res.put("err", String.valueOf(e.getMessage())); } catch (Exception ignored) { }
            }
            return res.toString();
        }

        @JavascriptInterface
        public void saveStatus(String uri, boolean video) {
            if (needOldPermission()) return;
            new Thread(() -> {
                try {
                    Uri u = Uri.parse(uri);
                    String mime = video ? "video/mp4" : "image/jpeg";
                    saveToGallery(getContentResolver().openInputStream(u), "watsy_" + System.currentTimeMillis() + (video ? ".mp4" : ".jpg"), mime);
                    toast("انحفظت بالمعرض ✓");
                } catch (Exception e) { toast("ما انحفظت، جرّب مرة ثانية"); }
            }).start();
        }

        @JavascriptInterface
        public void viewStatus(String uri, boolean video) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.parse(uri), video ? "video/*" : "image/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivity(i); } catch (Exception e) { toast("ما لقيت برنامج يشغّله"); }
            });
        }

        @JavascriptInterface
        public void shareStatus(String uri, boolean video, boolean toWa) {
            runOnUiThread(() -> shareUri(Uri.parse(uri), video ? "video/mp4" : "image/jpeg", toWa));
        }

        // ───── البطاقات
        @JavascriptInterface
        public void card(String dataUrl, boolean toWa) {
            if (needOldPermission()) return;
            new Thread(() -> {
                try {
                    String b64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                    Uri u = saveToGallery(new java.io.ByteArrayInputStream(bytes), "card_" + System.currentTimeMillis() + ".png", "image/png");
                    if (toWa) runOnUiThread(() -> shareUri(u, "image/png", true));
                    else toast("انحفظت البطاقة بالمعرض ✓");
                } catch (Exception e) { toast("ما انحفظت، جرّب مرة ثانية"); }
            }).start();
        }
    }
}
