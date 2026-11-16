package com.salati.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** يشغّل الأذان بصوت المنبّه، مع زر إيقاف */
public class AdhanService extends Service {
    MediaPlayer mp;
    final Handler h = new Handler(Looper.getMainLooper());
    static volatile boolean playing = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && "stop".equals(i.getAction())) { stopAll(); return START_NOT_STICKY; }
        int p = i == null ? 2 : i.getIntExtra("p", 2);
        boolean test = i != null && i.getBooleanExtra("test", false);
        Alarms.channels(this);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, AdhanService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class).putExtra("adhan", p), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = test ? "🔊 تجربة صوت الأذان" : "🕌 حان وقت صلاة " + Core.NAMES[p];
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "adhan") : new Notification.Builder(this))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(test ? "اضغط إيقاف لما تخلص" : "الله أكبر الله أكبر")
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "⏹ إيقاف الأذان", stop).build());
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(77, b.build());
        play(p);
        return START_NOT_STICKY;
    }

    void play(int p) {
        stopPlayer();
        String key = p == 0 ? "sndFajr" : "snd";
        String u = Core.sp(this).getString(key, "");
        if (u.isEmpty() && p == 0) u = Core.sp(this).getString("snd", "");
        Uri uri = u.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(u);
        boolean custom = !u.isEmpty();
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(this, uri);
            mp.setLooping(!custom);
            mp.setOnCompletionListener(m -> stopAll());
            mp.prepare();
            mp.start();
            playing = true;
            // صوت المنبّه الافتراضي نوقفه بعد ٤٥ ثانية، والأذان نوقفه بعد ٦ دقايق كحد أقصى
            h.postDelayed(this::stopAll, custom ? 6 * 60000L : 45000L);
        } catch (Exception e) {
            if (custom) { Core.sp(this).edit().remove(key).apply(); play(p); } else stopAll();
        }
    }

    void stopPlayer() {
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception ignored) {}
        mp = null;
    }

    void stopAll() {
        playing = false;
        h.removeCallbacksAndMessages(null);
        stopPlayer();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() { playing = false; stopPlayer(); super.onDestroy(); }
}
