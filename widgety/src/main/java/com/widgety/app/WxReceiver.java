package com.widgety.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** يجيب الطقس كل نص ساعة للويدجت */
public class WxReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult pr = goAsync();
        fetch(c.getApplicationContext(), pr);
    }

    static void fetch(final Context c, final PendingResult pr) {
        new Thread(() -> {
            try {
                SharedPreferences p = Widgets.prefs(c);
                String lat = p.getString("lat", "24.71");
                String lon = p.getString("lon", "46.68");
                String u = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1";
                HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                StringBuilder sb = new StringBuilder();
                BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONObject d = new JSONObject(sb.toString());
                JSONObject cur = d.getJSONObject("current");
                JSONObject daily = d.getJSONObject("daily");
                p.edit()
                    .putInt("t", (int) Math.round(cur.getDouble("temperature_2m")))
                    .putInt("code", cur.getInt("weather_code"))
                    .putBoolean("day", cur.optInt("is_day", 1) == 1)
                    .putInt("hi", (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)))
                    .putInt("lo", (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)))
                    .putBoolean("hasWx", true)
                    .apply();
                Widgets.updateAll(c);
            } catch (Exception ignored) {
            } finally {
                if (pr != null) pr.finish();
            }
        }).start();
    }
}
