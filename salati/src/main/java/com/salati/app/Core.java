package com.salati.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** حساب أوقات الصلاة والتاريخ الهجري والإعدادات — برمجة أبو نواف */
public class Core {

    public static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    public static final String[] ICONS = {"🌅", "☀️", "🌞", "🌤️", "🌇", "🌙"};
    static final String[] HMONTHS = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};
    static final String[] GMONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
    static final String[] DAYS = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};

    // طرق الحساب: [زاوية الفجر, زاوية العشاء أو دقائق بعد المغرب (سالب = دقائق)]
    static final double[][] METHODS = {
        {18.5, -90},   // 0 أم القرى
        {18, 17},      // 1 رابطة العالم الإسلامي
        {19.5, 17.5},  // 2 الهيئة المصرية
        {18, 18},      // 3 كراتشي
        {15, 15},      // 4 أمريكا الشمالية
        {19.5, -90},   // 5 الخليج
        {18, 17.5},    // 6 الكويت
        {18, -90},     // 7 قطر
    };

    public static SharedPreferences sp(Context c) { return c.getSharedPreferences("salati", Context.MODE_PRIVATE); }

    // ===== الحساب الفلكي =====
    static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    static double darccot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }

    static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double A = Math.floor(y / 100.0);
        double B = 2 - A + Math.floor(A / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + B - 1524.5;
    }

    /** يرجع [الميل, معادلة الزمن] */
    static double[] sun(double jd) {
        double D = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * D, 360);
        double q = fix(280.459 + 0.98564736 * D, 360);
        double L = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360);
        double e = 23.439 - 0.00000036 * D;
        double RA = darctan2(dcos(e) * dsin(L), dcos(L)) / 15;
        double eqt = q / 15 - fix(RA, 24);
        double decl = darcsin(dsin(e) * dsin(L));
        return new double[]{decl, eqt};
    }

    double lat, lon, tz, jDate;
    double fajrA, ishaA; int asrF;

    double midDay(double t) { double eqt = sun(jDate + t)[1]; return fix(12 - eqt, 24); }

    double angleTime(double angle, double t, boolean ccw) {
        double decl = sun(jDate + t)[0];
        double noon = midDay(t);
        double T = darccos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))) / 15;
        return noon + (ccw ? -T : T);
    }

    double asrTime(double factor, double t) {
        double decl = sun(jDate + t)[0];
        double angle = -darccot(factor + dtan(Math.abs(lat - decl)));
        return angleTime(angle, t, false);
    }

    /** أوقات اليوم بالساعات العشرية: فجر، شروق، ظهر، عصر، مغرب، عشاء */
    static double[] compute(Context c, Calendar day) {
        SharedPreferences s = sp(c);
        Core k = new Core();
        k.lat = s.getFloat("lat", 21.4225f);
        k.lon = s.getFloat("lon", 39.8262f);
        TimeZone z = TimeZone.getDefault();
        k.tz = z.getOffset(day.getTimeInMillis()) / 3600000.0;
        int method = s.getInt("method", 0);
        double[] mt = METHODS[Math.max(0, Math.min(METHODS.length - 1, method))];
        k.fajrA = mt[0];
        k.asrF = s.getInt("asr", 0) == 1 ? 2 : 1;
        k.jDate = julian(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)) - k.lon / (15 * 24.0);
        double[] t = {5, 6, 12, 13, 18, 18, 18};
        for (int it = 0; it < 2; it++) {
            double[] p = new double[7];
            for (int i = 0; i < 7; i++) p[i] = t[i] / 24;
            double fajr = k.angleTime(k.fajrA, p[0], true);
            double sunrise = k.angleTime(0.833, p[1], true);
            double dhuhr = k.midDay(p[2]);
            double asr = k.asrTime(k.asrF, p[3]);
            double sunset = k.angleTime(0.833, p[4], false);
            double isha = mt[1] > 0 ? k.angleTime(mt[1], p[6], false) : sunset;
            t = new double[]{fajr, sunrise, dhuhr, asr, sunset, sunset, isha};
        }
        double shift = k.tz - k.lon / 15;
        double[] r = new double[6];
        r[0] = t[0] + shift; r[1] = t[1] + shift; r[2] = t[2] + shift + 1 / 60.0; r[3] = t[3] + shift;
        r[4] = t[5] + shift + 1 / 60.0; // المغرب
        if (mt[1] < 0) {
            int mins = (int) -mt[1];
            if (method == 0 && hijri(c, day)[1] == 9) mins = 120; // رمضان في أم القرى
            r[5] = r[4] + mins / 60.0;
        } else r[5] = t[6] + shift;
        // تعديلات المستخدم بالدقائق
        for (int i = 0; i < 6; i++) {
            r[i] += s.getInt("adj" + i, 0) / 60.0;
            if (Double.isNaN(r[i])) r[i] = new double[]{4.5, 6, 12, 15.3, 18, 19.5}[i];
        }
        return r;
    }

    /** وقت الصلاة كميلي ثانية لليوم المحدد */
    static long[] millis(Context c, Calendar day) {
        double[] h = compute(c, day);
        long[] out = new long[6];
        for (int i = 0; i < 6; i++) {
            Calendar k = (Calendar) day.clone();
            k.set(Calendar.HOUR_OF_DAY, 0); k.set(Calendar.MINUTE, 0); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
            long mins = Math.round(h[i] * 60);
            out[i] = k.getTimeInMillis() + mins * 60000L;
        }
        return out;
    }

    static Calendar dayOf(long t) { Calendar k = Calendar.getInstance(); k.setTimeInMillis(t); return k; }

    static String hm(long t) {
        Calendar k = dayOf(t);
        int h = k.get(Calendar.HOUR_OF_DAY), m = k.get(Calendar.MINUTE);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + (m < 10 ? "0" : "") + m + " " + (h < 12 ? "ص" : "م");
    }

    /** [سنة, شهر 1-12, يوم] هجري أم القرى مع تعديل المستخدم */
    static int[] hijri(Context c, Calendar day) {
        try {
            android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar();
            ic.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            ic.setTimeInMillis(day.getTimeInMillis() + sp(c).getInt("hadj", 0) * 86400000L);
            return new int[]{ic.get(android.icu.util.Calendar.YEAR), ic.get(android.icu.util.Calendar.MONTH) + 1, ic.get(android.icu.util.Calendar.DAY_OF_MONTH)};
        } catch (Throwable e) { return new int[]{1448, 1, 1}; }
    }

    static String hijriStr(Context c, Calendar day) {
        int[] h = hijri(c, day);
        return h[2] + " " + HMONTHS[Math.max(0, Math.min(11, h[1] - 1))] + " " + h[0] + " هـ";
    }

    static String gregStr(Calendar d) {
        return DAYS[d.get(Calendar.DAY_OF_WEEK) - 1] + " " + d.get(Calendar.DAY_OF_MONTH) + " " + GMONTHS[d.get(Calendar.MONTH)];
    }

    /** الصلاة الجاية: [رقمها, وقتها] (يتجاوز الشروق إلا إذا طلب) */
    static long[] next(Context c, long now, boolean withSunrise) {
        for (int dd = 0; dd < 2; dd++) {
            Calendar day = Calendar.getInstance(); day.setTimeInMillis(now); day.add(Calendar.DAY_OF_YEAR, dd);
            long[] t = millis(c, day);
            for (int i = 0; i < 6; i++) {
                if (i == 1 && !withSunrise) continue;
                if (t[i] > now) return new long[]{i, t[i]};
            }
        }
        return new long[]{0, now + 3600000};
    }

    /** الصلاة الحالية (اللي دخل وقتها) */
    static int current(Context c, long now) {
        long[] t = millis(c, dayOf(now));
        int cur = 5;
        for (int i = 0; i < 6; i++) if (i != 1 && t[i] <= now) cur = i;
        if (now < t[0]) cur = 5;
        return cur;
    }

    static JSONObject dayJson(Context c, Calendar day) throws Exception {
        long[] t = millis(c, day);
        JSONArray a = new JSONArray();
        for (long x : t) a.put(x);
        int[] h = hijri(c, day);
        return new JSONObject().put("t", a).put("hijri", hijriStr(c, day)).put("greg", gregStr(day))
            .put("hd", h[2]).put("hm", h[1]).put("hy", h[0]).put("dow", day.get(Calendar.DAY_OF_WEEK))
            .put("date", day.get(Calendar.DAY_OF_MONTH));
    }

    // ===== الأحداث (تنبيه قبل، أذان، إقامة) =====
    static class Ev { long at; int prayer; String type; Ev(long a, int p, String t) { at = a; prayer = p; type = t; } }

    /** وضع كل صلاة: 0 صامت، 1 إشعار، 2 أذان */
    static int mode(Context c, int p) { return sp(c).getInt("mode" + p, p == 1 ? 0 : 2); }

    static List<Ev> events(Context c, long now) {
        SharedPreferences s = sp(c);
        int pre = s.getInt("pre", 10), iq = s.getInt("iqama", 0);
        List<Ev> out = new ArrayList<>();
        for (int dd = 0; dd < 3; dd++) {
            Calendar day = dayOf(now); day.add(Calendar.DAY_OF_YEAR, dd);
            long[] t = millis(c, day);
            for (int i = 0; i < 6; i++) {
                if (mode(c, i) == 0) out.add(new Ev(t[i], i, "time")); // بس نحدّث الويدجت
                else {
                    if (pre > 0 && i != 1) out.add(new Ev(t[i] - pre * 60000L, i, "pre"));
                    out.add(new Ev(t[i], i, "adhan"));
                    if (iq > 0 && i != 1) out.add(new Ev(t[i] + iq * 60000L, i, "iqama"));
                }
            }
            // الجمعة: تذكير سورة الكهف الساعة ٩ الصبح
            if (s.getBoolean("kahf", true) && day.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                Calendar k = (Calendar) day.clone();
                k.set(Calendar.HOUR_OF_DAY, 9); k.set(Calendar.MINUTE, 0); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
                out.add(new Ev(k.getTimeInMillis(), -1, "kahf"));
            }
            // نص الليل: نحدّث الويدجت لليوم الجديد
            Calendar mid = (Calendar) day.clone();
            mid.set(Calendar.HOUR_OF_DAY, 0); mid.set(Calendar.MINUTE, 0); mid.set(Calendar.SECOND, 5); mid.add(Calendar.DAY_OF_YEAR, 1);
            out.add(new Ev(mid.getTimeInMillis(), -1, "time"));
        }
        List<Ev> f = new ArrayList<>();
        for (Ev e : out) if (e.at > now + 1000) f.add(e);
        java.util.Collections.sort(f, (a, b) -> Long.compare(a.at, b.at));
        return f;
    }

    static double qibla(double lat, double lon) {
        double kLat = 21.4225, kLon = 39.8262;
        double y = dsin(kLon - lon);
        double x = dcos(lat) * dtan(kLat) - dsin(lat) * dcos(kLon - lon);
        return fix(darctan2(y, x), 360);
    }
}
