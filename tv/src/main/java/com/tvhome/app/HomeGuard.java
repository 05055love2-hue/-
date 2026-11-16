package com.tvhome.app;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * حارس الشاشة الرئيسية: بعض التلفزيونات ترجع شاشتها الأصلية بعد ما تطفي وتشغّل
 * أو لما تضغط زر البيت. هذا يلاحظ لما تطلع الشاشة الأصلية ويرجّع «شاشتي» فورًا.
 */
public class HomeGuard extends AccessibilityService {

    private final Set<String> otherHomes = new HashSet<>();
    private final Set<String> otherPkgs = new HashSet<>();
    private long lastJump = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        loadHomes();
    }

    /** يجمع كل الشاشات الرئيسية الثانية الموجودة في الجهاز */
    private void loadHomes() {
        otherHomes.clear();
        otherPkgs.clear();
        try {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = pm.queryIntentActivities(i, PackageManager.MATCH_ALL);
            for (ResolveInfo r : list) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                // نتجاهل شاشة الاختيار حقت النظام
                if (pkg.equals("android") || pkg.contains("resolver")) continue;
                otherHomes.add(r.activityInfo.name);
                if (r.activityInfo.targetActivity != null) otherHomes.add(r.activityInfo.targetActivity);
                otherPkgs.add(pkg);
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = e.getClassName();
        if (cls == null) return;
        String c = cls.toString();
        String pkg = e.getPackageName() == null ? "" : e.getPackageName().toString();
        String low = c.toLowerCase();
        boolean homeLike = low.contains("home") || low.contains("launcher");
        boolean isOther = otherHomes.contains(c) || (otherPkgs.contains(pkg) && homeLike);
        if (!isOther && homeLike && !pkg.equals(getPackageName())) {
            // يمكن انضاف لانشر جديد بعد التشغيل
            loadHomes();
            isOther = otherHomes.contains(c);
        }
        if (!isOther) return;
        long now = System.currentTimeMillis();
        if (now - lastJump < 1500) return;
        lastJump = now;
        try {
            Intent go = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(go);
        } catch (Exception ignored) { }
    }

    @Override
    public void onInterrupt() { }

    /** هل الحارس مفعّل؟ */
    public static boolean isOn(Context ctx) {
        try {
            String on = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(on)) return false;
            String me = new ComponentName(ctx, HomeGuard.class).flattenToString();
            String meShort = new ComponentName(ctx, HomeGuard.class).flattenToShortString();
            for (String s : on.split(":")) {
                if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
}
