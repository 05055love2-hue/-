package com.widgety.app;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.List;
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

    private boolean hasAccess() {
        String list = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return list != null && list.contains(getPackageName());
    }

    private MediaController controller() {
        try {
            MediaSessionManager m = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = m.getActiveSessions(new ComponentName(this, MediaListener.class));
            if (l == null || l.isEmpty()) return null;
            for (MediaController c : l) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) return c;
            }
            return l.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    class Bridge {
        @JavascriptInterface
        public String media() {
            JSONObject o = new JSONObject();
            try {
                boolean acc = hasAccess();
                o.put("access", acc);
                MediaController c = acc ? controller() : null;
                o.put("has", c != null);
                if (c != null) {
                    MediaMetadata md = c.getMetadata();
                    PlaybackState st = c.getPlaybackState();
                    String title = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
                    String artist = md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
                    if (artist == null && md != null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                    long dur = md != null ? md.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
                    boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
                    long pos = 0;
                    if (st != null) {
                        pos = st.getPosition();
                        if (playing && st.getLastPositionUpdateTime() > 0) {
                            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
                        }
                    }
                    if (dur > 0 && pos > dur) pos = dur;
                    o.put("title", title == null ? "" : title);
                    o.put("artist", artist == null ? "" : artist);
                    o.put("dur", dur);
                    o.put("pos", Math.max(0, pos));
                    o.put("playing", playing);
                }
            } catch (Exception ignored) { }
            return o.toString();
        }

        @JavascriptInterface
        public void cmd(String a) {
            MediaController c = controller();
            if (c != null) {
                MediaController.TransportControls t = c.getTransportControls();
                if ("play".equals(a)) t.play();
                else if ("pause".equals(a)) t.pause();
                else if ("next".equals(a)) t.skipToNext();
                else if ("prev".equals(a)) t.skipToPrevious();
                return;
            }
            // ما فيه مشغّل مفتوح: نرسل زر الوسائط عشان آخر تطبيق موسيقى يشتغل
            int code = "next".equals(a) ? KeyEvent.KEYCODE_MEDIA_NEXT
                    : "prev".equals(a) ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    : "pause".equals(a) ? KeyEvent.KEYCODE_MEDIA_PAUSE : KeyEvent.KEYCODE_MEDIA_PLAY;
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
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

        @JavascriptInterface
        public void keepOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }
}
