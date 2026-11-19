package com.watsy.app;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** يحفظ رسائل الواتساب أول ما توصل — عشان لو انحذفت تبقى عندك • برمجة أبو نواف */
public class MsgService extends NotificationListenerService {

    static final int MAX = 3000;
    static final Object LOCK = new Object();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (!"com.whatsapp".equals(pkg) && !"com.whatsapp.w4b".equals(pkg)) return;
            Notification n = sbn.getNotification();
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle ex = n.extras;
            String chat = str(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
            if (chat.isEmpty()) chat = str(ex.getCharSequence(Notification.EXTRA_TITLE));
            if (chat.isEmpty()) return;

            JSONArray add = new JSONArray();
            Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (msgs != null && msgs.length > 0) {
                for (Parcelable p : msgs) {
                    if (!(p instanceof Bundle)) continue;
                    Bundle m = (Bundle) p;
                    String text = str(m.getCharSequence("text"));
                    String from = str(m.getCharSequence("sender"));
                    long t = m.getLong("time", sbn.getPostTime());
                    put(add, chat, from, text, t);
                }
            } else {
                String text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
                put(add, chat, "", text, sbn.getPostTime());
            }
            if (add.length() > 0) save(this, add);
        } catch (Throwable ignored) { }
    }

    static void put(JSONArray a, String chat, String from, String text, long t) throws Exception {
        if (text.isEmpty()) return;
        if (junk(text) || appName(chat)) {
            String l = norm(text);
            if (appName(chat) && !l.toLowerCase().contains("check") && !l.contains("التحقق")) hidden = System.currentTimeMillis();
            return;
        }
        String c = chat.replaceAll("\\s*\\(\\d+.*\\)$", "").trim();
        JSONObject o = new JSONObject();
        o.put("c", c); o.put("f", from); o.put("m", text); o.put("t", t);
        a.put(o);
    }

    /** آخر مرة وصل إشعار من الواتساب بدون نص الرسالة (المعاينة مقفلة) */
    static volatile long hidden = 0;

    /** ينظّف النص من العلامات المخفية ويحوّل الأرقام العربية */
    static String norm(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '\u200e' || ch == '\u200f' || ch == '\u202a' || ch == '\u202b' || ch == '\u202c' || ch == '\u2066' || ch == '\u2067' || ch == '\u2068' || ch == '\u2069') continue;
            if (ch >= '\u0660' && ch <= '\u0669') ch = (char) ('0' + ch - '\u0660');
            if (ch >= '\u06f0' && ch <= '\u06f9') ch = (char) ('0' + ch - '\u06f0');
            if (ch == '\u00a0' || ch == '\u202f') ch = ' ';
            b.append(ch);
        }
        return b.toString().trim();
    }

    static boolean appName(String chat) {
        String c = norm(chat).toLowerCase();
        return c.equals("whatsapp") || c.equals("واتساب") || c.equals("واتس اب") || c.equals("whatsapp business") || c.equals("واتساب للأعمال") || c.equals("واتساب للاعمال");
    }

    static boolean junk(String s) {
        String l = norm(s).toLowerCase();
        return l.matches("^\\d+ (new )?messages?.*") || l.matches(".*\\d+ chats?.*") || l.contains("checking for new messages")
            || l.contains("new message") || l.contains("رسالة جديدة") || l.contains("رسائل جديدة") || l.contains("رسالتان جديدتان")
            || l.contains("جارٍ التحقق") || l.contains("جار التحقق") || l.contains("من المحادثات") || l.matches("^\\d+ رسائل.*") || l.matches("^\\d+ رسالة.*");
    }

    static String str(CharSequence c) { return c == null ? "" : c.toString().trim(); }

    static File file(android.content.Context c) { return new File(c.getFilesDir(), "msgs.json"); }

    static JSONArray load(android.content.Context c) {
        synchronized (LOCK) {
            try {
                File f = file(c);
                if (!f.exists()) return new JSONArray();
                byte[] b = new byte[(int) f.length()];
                try (FileInputStream in = new FileInputStream(f)) { int r = 0; while (r < b.length) { int k = in.read(b, r, b.length - r); if (k < 0) break; r += k; } }
                return new JSONArray(new String(b, StandardCharsets.UTF_8));
            } catch (Exception e) { return new JSONArray(); }
        }
    }

    static void save(android.content.Context c, JSONArray add) throws Exception {
        synchronized (LOCK) {
            JSONArray all = load(c);
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            int from = Math.max(0, all.length() - 400);
            for (int i = from; i < all.length(); i++) {
                JSONObject o = all.getJSONObject(i);
                seen.add(o.optString("c") + "|" + o.optString("f") + "|" + o.optString("m") + "|" + o.optLong("t") / 60000);
            }
            boolean changed = false;
            for (int i = 0; i < add.length(); i++) {
                JSONObject o = add.getJSONObject(i);
                String k = o.optString("c") + "|" + o.optString("f") + "|" + o.optString("m") + "|" + o.optLong("t") / 60000;
                if (seen.add(k)) { all.put(o); changed = true; }
            }
            if (!changed) return;
            JSONArray out = all;
            if (all.length() > MAX) {
                out = new JSONArray();
                for (int i = all.length() - MAX; i < all.length(); i++) out.put(all.get(i));
            }
            try (FileOutputStream os = new FileOutputStream(file(c))) { os.write(out.toString().getBytes(StandardCharsets.UTF_8)); }
        }
    }

    /** الرسائل للعرض، بدون الإشعارات اللي ما فيها كلام */
    static JSONArray clean(android.content.Context c) {
        JSONArray all = load(c), out = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null || junk(o.optString("m")) || appName(o.optString("c"))) continue;
            out.put(o);
        }
        return out;
    }

    static void clear(android.content.Context c) {
        synchronized (LOCK) { file(c).delete(); }
    }
}
