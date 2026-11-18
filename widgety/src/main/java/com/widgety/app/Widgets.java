package com.widgety.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import org.json.JSONObject;

/** يرسم ويدجتات الشاشة الرئيسية — برمجة أبو نواف */
public class Widgets {

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("w", 0);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        update(c, m, WidgetWide.class, R.layout.w_wide);
        update(c, m, WidgetBar.class, R.layout.w_bar);
        update(c, m, WidgetSquare.class, R.layout.w_square);
        update(c, m, WidgetClock.class, R.layout.w_clock);
        update(c, m, WidgetWeather.class, R.layout.w_weather);
        update(c, m, WidgetCw.class, R.layout.w_cw);
        update(c, m, WidgetAnalog.class, R.layout.w_analog);
    }

    private static void update(Context c, AppWidgetManager m, Class<?> cls, int layout) {
        try {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
            if (ids == null || ids.length == 0) return;
            m.updateAppWidget(ids, build(c, layout));
        } catch (Exception ignored) { }
    }

    /** نقرأ الأغنية ونحدّث الويدجت بس إذا تغيّر شي */
    static void refreshMusic(Context c) {
        try {
            JSONObject o = Media.read(c);
            String sig = o.optBoolean("access") + "|" + o.optBoolean("has") + "|" + o.optString("title")
                    + "|" + o.optString("artist") + "|" + o.optBoolean("playing");
            SharedPreferences p = prefs(c);
            if (sig.equals(p.getString("msig", ""))) return;
            p.edit()
                .putString("msig", sig)
                .putBoolean("access", o.optBoolean("access"))
                .putBoolean("has", o.optBoolean("has"))
                .putString("title", o.optString("title"))
                .putString("artist", o.optString("artist"))
                .putBoolean("playing", o.optBoolean("playing"))
                .apply();
            updateAll(c);
        } catch (Exception ignored) { }
    }

    static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(c, 5, new Intent(c, WxReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR,
                    AlarmManager.INTERVAL_HALF_HOUR, pi);
        } catch (Exception ignored) { }
    }

    static int wxIcon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wx_sun : R.drawable.wx_moon;
        if (code <= 2) return day ? R.drawable.wx_part : R.drawable.wx_moon;
        if (code == 3) return R.drawable.wx_cloud;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 95) return R.drawable.wx_storm;
        if (code >= 51) return R.drawable.wx_rain;
        return R.drawable.wx_cloud;
    }

    static String wxText(int code) {
        if (code == 0) return "صحو";
        if (code <= 2) return "غائم جزئياً";
        if (code == 3) return "غائم";
        if (code == 45 || code == 48) return "ضباب";
        if (code >= 95) return "عواصف رعدية";
        if (code >= 80) return "زخات مطر";
        if (code >= 71 && code <= 77) return "ثلج";
        if (code >= 61) return "مطر";
        if (code >= 51) return "رذاذ";
        return "غائم";
    }

    private static PendingIntent action(Context c, String a, int req) {
        Intent i = new Intent(c, WidgetAction.class);
        i.putExtra("a", a);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews build(Context c, int layout) {
        SharedPreferences p = prefs(c);
        boolean glass = p.getBoolean("glass", true);
        boolean wide = layout == R.layout.w_wide;
        boolean bar = layout == R.layout.w_bar;
        boolean sq = layout == R.layout.w_square;
        boolean clk = layout == R.layout.w_clock;
        boolean wth = layout == R.layout.w_weather;
        boolean cw = layout == R.layout.w_cw;
        boolean an = layout == R.layout.w_analog;
        boolean hasWxViews = !clk && !an;
        boolean hasMusic = wide || bar || sq;
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);

        // الخلفية: زجاجي أو عادي
        int solid = wide ? R.drawable.bg_wide : bar ? R.drawable.bg_bar : sq ? R.drawable.bg_square
                : clk ? R.drawable.bg_clock : wth ? R.drawable.bg_weather : an ? R.drawable.bg_analog : R.drawable.bg_cw;
        v.setInt(R.id.root, "setBackgroundResource", glass ? R.drawable.bg_glass : solid);
        if (wide) {
            int pn = glass ? R.drawable.pn_glass : R.drawable.pn_solid;
            v.setInt(R.id.p1, "setBackgroundResource", pn);
            v.setInt(R.id.p2, "setBackgroundResource", pn);
            v.setInt(R.id.p3, "setBackgroundResource", pn);
        }
        if (sq) v.setInt(R.id.p3, "setBackgroundResource", glass ? R.drawable.sq_card_glass : R.drawable.sq_card);

        int base = wide ? 10 : bar ? 20 : sq ? 30 : clk ? 40 : wth ? 50 : an ? 70 : 60;
        PendingIntent open = PendingIntent.getActivity(c, base, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, open);

        // الطقس
        if (hasWxViews) {
            String city = p.getString("city", "الرياض");
            if (p.getBoolean("hasWx", false)) {
                int code = p.getInt("code", 0);
                String hilo = "العظمى " + p.getInt("hi", 0) + "° · الصغرى " + p.getInt("lo", 0) + "°";
                v.setTextViewText(R.id.temp, p.getInt("t", 0) + "°");
                v.setImageViewResource(R.id.wx, wxIcon(code, p.getBoolean("day", true)));
                if (wide || wth || cw) {
                    v.setTextViewText(R.id.cond, wxText(code) + " · " + city);
                    v.setTextViewText(R.id.hilo, hilo);
                } else if (bar) {
                    v.setTextViewText(R.id.cond, city);
                } else {
                    v.setTextViewText(R.id.cond, wxText(code) + "\n" + city);
                }
            } else {
                v.setTextViewText(R.id.temp, "--");
                v.setTextViewText(R.id.cond, city);
            }
        }
        if (!hasMusic) return v;

        // الموسيقى
        boolean access = Media.hasAccess(c);
        boolean has = p.getBoolean("has", false);
        boolean playing = access && has && p.getBoolean("playing", false);
        String title, artist;
        if (!access) { title = "اربط الموسيقى"; artist = "اضغط هنا مرة وحدة"; }
        else if (!has) { title = "ما فيه أغنية شغالة"; artist = "اضغط ▶ يشتغل آخر تطبيق"; }
        else {
            title = p.getString("title", "");
            artist = p.getString("artist", "");
            if (title.isEmpty()) title = "بدون اسم";
        }
        v.setTextViewText(R.id.title, title);
        v.setTextViewText(R.id.artist, artist);
        boolean darkIcon = !sq;
        v.setImageViewResource(R.id.play, playing
                ? (darkIcon ? R.drawable.ic_pause_d : R.drawable.ic_pause_w)
                : (darkIcon ? R.drawable.ic_play_d : R.drawable.ic_play_w));

        if (!access) {
            // نفتح التطبيق، وهو يشرح خطوات ربط الموسيقى
            v.setOnClickPendingIntent(R.id.song, open);
            v.setOnClickPendingIntent(R.id.play, open);
            if (!bar) {
                v.setOnClickPendingIntent(R.id.prev, open);
                v.setOnClickPendingIntent(R.id.next, open);
            }
        } else {
            v.setOnClickPendingIntent(R.id.song, open);
            v.setOnClickPendingIntent(R.id.play, action(c, "toggle", base + 2));
            if (!bar) {
                v.setOnClickPendingIntent(R.id.prev, action(c, "prev", base + 3));
                v.setOnClickPendingIntent(R.id.next, action(c, "next", base + 4));
            }
        }
        return v;
    }
}
