package com.widgety.app;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import java.util.List;
import org.json.JSONObject;

/** قراءة الأغنية الشغالة والتحكم فيها — برمجة أبو نواف */
public class Media {

    static boolean hasAccess(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return list != null && list.contains(c.getPackageName());
    }

    static MediaController controller(Context c) {
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = m.getActiveSessions(new ComponentName(c, MediaListener.class));
            if (l == null || l.isEmpty()) return null;
            for (MediaController mc : l) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) return mc;
            }
            return l.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject read(Context c) {
        JSONObject o = new JSONObject();
        try {
            boolean acc = hasAccess(c);
            o.put("access", acc);
            MediaController mc = acc ? controller(c) : null;
            o.put("has", mc != null);
            if (mc != null) {
                MediaMetadata md = mc.getMetadata();
                PlaybackState st = mc.getPlaybackState();
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
        return o;
    }

    static void cmd(Context c, String a) {
        MediaController mc = controller(c);
        if (mc != null) {
            MediaController.TransportControls t = mc.getTransportControls();
            if ("toggle".equals(a)) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play();
            }
            else if ("play".equals(a)) t.play();
            else if ("pause".equals(a)) t.pause();
            else if ("next".equals(a)) t.skipToNext();
            else if ("prev".equals(a)) t.skipToPrevious();
            return;
        }
        // ما فيه مشغّل مفتوح: نرسل زر الوسائط عشان آخر تطبيق موسيقى يشتغل
        int code = "next".equals(a) ? KeyEvent.KEYCODE_MEDIA_NEXT
                : "prev".equals(a) ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                : "pause".equals(a) ? KeyEvent.KEYCODE_MEDIA_PAUSE : KeyEvent.KEYCODE_MEDIA_PLAY;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }
}
