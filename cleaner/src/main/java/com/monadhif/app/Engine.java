package com.monadhif.app;

import android.os.Environment;
import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** محرك الفحص: يمشي على ملفات الجوال ويصنّفها — برمجة أبو نواف */
public class Engine {

    public interface Progress { void on(String text, int pct); }

    static class F {
        final String p; final long s; final long m;
        F(String p, long s, long m) { this.p = p; this.s = s; this.m = m; }
        JSONObject json() throws Exception { return new JSONObject().put("p", p).put("s", s).put("m", m); }
    }

    // مجموعات الملفات الزايدة
    static final String[] JUNK = {"cache", "thumbs", "temp", "logs", "partial", "empty", "apk", "emptydir"};
    final Map<String, List<F>> groups = new HashMap<>();
    final Map<String, long[]> cats = new HashMap<>();      // [حجم, عدد]
    final List<F> large = new ArrayList<>();
    final List<F> oldDownloads = new ArrayList<>();
    final List<F> screenshots = new ArrayList<>();
    final Map<String, List<F>> wa = new HashMap<>();
    final List<F> all = new ArrayList<>();
    List<List<F>> dups = new ArrayList<>();
    long scannedFiles = 0, scannedBytes = 0;
    boolean done = false;

    static final Set<String> KEEP_DIRS = new HashSet<>(Arrays.asList(
        "android", "dcim", "pictures", "music", "movies", "download", "documents", "alarms",
        "notifications", "ringtones", "podcasts", "audiobooks", "recordings"));

    static String ext(String n) {
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static String cat(String e) {
        switch (e) {
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "heic": case "heif": case "bmp": case "dng":
                return "images";
            case "mp4": case "mkv": case "3gp": case "mov": case "webm": case "avi": case "m4v":
                return "videos";
            case "mp3": case "m4a": case "aac": case "ogg": case "opus": case "wav": case "flac": case "amr":
                return "audio";
            case "pdf": case "doc": case "docx": case "xls": case "xlsx": case "ppt": case "pptx": case "txt": case "csv": case "rtf": case "odt":
                return "docs";
            case "apk": case "apks": case "xapk":
                return "apks";
            case "zip": case "rar": case "7z": case "tar": case "gz":
                return "archives";
            default:
                return "other";
        }
    }

    void add(String g, F f) {
        List<F> l = groups.get(g);
        if (l == null) { l = new ArrayList<>(); groups.put(g, l); }
        l.add(f);
    }

    void addWa(String g, F f) {
        List<F> l = wa.get(g);
        if (l == null) { l = new ArrayList<>(); wa.put(g, l); }
        l.add(f);
    }

    public void scan(Progress pr) {
        File root = Environment.getExternalStorageDirectory();
        String rootPath = root.getAbsolutePath();
        // نعدّ المجلدات الأساسية عشان نحسب النسبة تقريبيًا
        File[] tops = root.listFiles();
        if (tops == null) tops = new File[0];
        Arrays.sort(tops);
        long now = System.currentTimeMillis();
        for (int i = 0; i < tops.length; i++) {
            File t = tops[i];
            pr.on(t.getName(), (int) (i * 95L / Math.max(1, tops.length)));
            walk(t, rootPath, now, 0, pr);
        }
        Collections.sort(large, (a, b) -> Long.compare(b.s, a.s));
        if (large.size() > 300) large.subList(300, large.size()).clear();
        done = true;
        pr.on("", 100);
    }

    /** يرجع true إذا المجلد فاضي تمامًا (بعد ما نمشي فيه) */
    boolean walk(File d, String root, long now, int depth, Progress pr) {
        if (depth > 24) return false;
        String path = d.getAbsolutePath();
        String rel = path.length() > root.length() ? path.substring(root.length() + 1) : "";
        String relL = rel.toLowerCase(Locale.ROOT);
        if (relL.equals("android/data") || relL.equals("android/obb") || relL.startsWith("android/data/") || relL.startsWith("android/obb/")) return false;
        if (d.isFile()) { file(d, relL, now); return false; }
        File[] kids = d.listFiles();
        if (kids == null) return false;
        if (kids.length == 0) {
            if (depth >= 1 && !KEEP_DIRS.contains(relL) && !relL.startsWith("android")) add("emptydir", new F(path, 0, d.lastModified()));
            return true;
        }
        boolean empty = true;
        for (File k : kids) {
            if (k.isDirectory()) { if (!walk(k, root, now, depth + 1, pr)) empty = false; }
            else { file(k, (relL.isEmpty() ? "" : relL + "/") + k.getName().toLowerCase(Locale.ROOT), now); empty = false; }
        }
        return empty;
    }

    void file(File f, String relL, long now) {
        long s = f.length(), m = f.lastModified();
        F x = new F(f.getAbsolutePath(), s, m);
        scannedFiles++; scannedBytes += s;
        String name = f.getName().toLowerCase(Locale.ROOT);
        String e = ext(name);
        String c = cat(e);
        long[] cc = cats.get(c); if (cc == null) { cc = new long[2]; cats.put(c, cc); }
        cc[0] += s; cc[1]++;
        if (s >= 50L * 1024) all.add(x);
        if (s >= 50L * 1024 * 1024) large.add(x);

        // واتساب
        int wi = relL.indexOf("whatsapp/media/");
        if (wi >= 0) {
            String sub = relL.substring(wi + 15);
            String g = sub.startsWith(".statuses") ? "statuses"
                : sub.startsWith("whatsapp images") ? "images"
                : sub.startsWith("whatsapp video") ? "videos"
                : sub.startsWith("whatsapp voice notes") ? "voice"
                : sub.startsWith("whatsapp audio") ? "audio"
                : sub.startsWith("whatsapp documents") ? "docs"
                : sub.startsWith("whatsapp stickers") ? "stickers"
                : sub.startsWith("whatsapp animated gifs") ? "gifs"
                : "other";
            if (!name.equals(".nomedia")) addWa(g, x);
            return;
        }

        // الملفات الزايدة
        if (relL.contains("/.thumbnails/") || relL.startsWith(".thumbnails/") || relL.contains("/.thumbs/")) { add("thumbs", x); return; }
        if (relL.contains("/cache/") || relL.contains("/.cache/") || relL.startsWith("cache/") || relL.startsWith(".cache/")) { add("cache", x); return; }
        if (e.equals("tmp") || e.equals("temp") || name.endsWith("~") || name.startsWith("~$")) { add("temp", x); return; }
        if (e.equals("log") || e.equals("trace") || relL.contains("/logs/") || relL.startsWith("logs/")) { add("logs", x); return; }
        if (e.equals("crdownload") || e.equals("part") || e.equals("partial") || e.equals("download") || e.equals("ytdl")) { add("partial", x); return; }
        if (c.equals("apks") && !relL.startsWith("android/")) { add("apk", x); return; }
        if (s == 0 && !name.equals(".nomedia")) { add("empty", x); return; }

        if (relL.startsWith("download/") && now - m > 90L * 24 * 3600 * 1000) oldDownloads.add(x);
        if (relL.contains("screenshots/")) screenshots.add(x);
    }

    /** يدوّر الملفات المكررة: نفس الحجم، وبعدها نفس البصمة */
    public void findDups(Progress pr) {
        Map<Long, List<F>> bySize = new HashMap<>();
        for (F f : all) {
            List<F> l = bySize.get(f.s);
            if (l == null) { l = new ArrayList<>(); bySize.put(f.s, l); }
            l.add(f);
        }
        List<List<F>> cand = new ArrayList<>();
        for (List<F> l : bySize.values()) if (l.size() > 1) cand.add(l);
        List<List<F>> out = new ArrayList<>();
        int i = 0;
        for (List<F> l : cand) {
            pr.on("", (int) (i++ * 100L / Math.max(1, cand.size())));
            Map<String, List<F>> byHash = new HashMap<>();
            for (F f : l) {
                String h = hash(f);
                if (h == null) continue;
                List<F> g = byHash.get(h);
                if (g == null) { g = new ArrayList<>(); byHash.put(h, g); }
                g.add(f);
            }
            for (List<F> g : byHash.values()) if (g.size() > 1) {
                Collections.sort(g, (a, b) -> Long.compare(a.m, b.m)); // الأقدم أول = الأصل
                out.add(g);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.get(0).s * (b.size() - 1), a.get(0).s * (a.size() - 1)));
        dups = out;
        pr.on("", 100);
    }

    /** بصمة من أول ونص وآخر الملف (سريعة وكافية) */
    static String hash(F f) {
        try (RandomAccessFile r = new RandomAccessFile(f.p, "r")) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = new byte[65536];
            long[] at = f.s <= 3L * b.length ? new long[]{0} : new long[]{0, f.s / 2 - b.length / 2, f.s - b.length};
            for (long pos : at) {
                r.seek(pos);
                int n = r.read(b, 0, (int) Math.min(b.length, f.s - pos));
                if (n > 0) md.update(b, 0, n);
            }
            if (f.s <= 3L * b.length) {
                int n;
                while ((n = r.read(b)) > 0) md.update(b, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    static long sum(List<F> l) { long t = 0; if (l != null) for (F f : l) t += f.s; return t; }

    static JSONArray arr(List<F> l, int max) throws Exception {
        JSONArray a = new JSONArray();
        if (l == null) return a;
        List<F> c = new ArrayList<>(l);
        Collections.sort(c, (x, y) -> Long.compare(y.s, x.s));
        for (int i = 0; i < c.size() && i < max; i++) a.put(c.get(i).json());
        return a;
    }

    public JSONObject summary() throws Exception {
        JSONObject j = new JSONObject();
        JSONObject g = new JSONObject();
        long junk = 0;
        for (String k : JUNK) {
            List<F> l = groups.get(k);
            long s = sum(l);
            int n = l == null ? 0 : l.size();
            if (k.equals("emptydir")) s = 0;
            g.put(k, new JSONObject().put("s", s).put("n", n));
            junk += s;
        }
        j.put("junk", junk).put("groups", g);
        JSONObject c = new JSONObject();
        for (Map.Entry<String, long[]> e : cats.entrySet()) c.put(e.getKey(), new JSONObject().put("s", e.getValue()[0]).put("n", e.getValue()[1]));
        j.put("cats", c);
        JSONObject w = new JSONObject();
        for (Map.Entry<String, List<F>> e : wa.entrySet()) w.put(e.getKey(), new JSONObject().put("s", sum(e.getValue())).put("n", e.getValue().size()));
        j.put("wa", w);
        j.put("large", new JSONObject().put("s", sum(large)).put("n", large.size()));
        j.put("old", new JSONObject().put("s", sum(oldDownloads)).put("n", oldDownloads.size()));
        j.put("shots", new JSONObject().put("s", sum(screenshots)).put("n", screenshots.size()));
        j.put("files", scannedFiles).put("bytes", scannedBytes);
        return j;
    }

    public JSONArray list(String kind) throws Exception {
        if (kind.startsWith("g:")) return arr(groups.get(kind.substring(2)), 3000);
        if (kind.startsWith("wa:")) return arr(wa.get(kind.substring(3)), 3000);
        switch (kind) {
            case "large": return arr(large, 300);
            case "old": return arr(oldDownloads, 2000);
            case "shots": return arr(screenshots, 3000);
            case "dups": {
                JSONArray a = new JSONArray();
                for (int i = 0; i < dups.size() && i < 300; i++) {
                    JSONArray g = new JSONArray();
                    for (F f : dups.get(i)) g.put(f.json());
                    a.put(g);
                }
                return a;
            }
        }
        return new JSONArray();
    }

    /** بعد الحذف نشيل الملفات من كل القوائم */
    public void forget(Set<String> gone) {
        for (List<F> l : groups.values()) l.removeIf(f -> gone.contains(f.p));
        for (List<F> l : wa.values()) l.removeIf(f -> gone.contains(f.p));
        large.removeIf(f -> gone.contains(f.p));
        oldDownloads.removeIf(f -> gone.contains(f.p));
        screenshots.removeIf(f -> gone.contains(f.p));
        all.removeIf(f -> gone.contains(f.p));
        for (List<F> g : dups) g.removeIf(f -> gone.contains(f.p));
        dups.removeIf(g -> g.size() < 2);
    }
}
