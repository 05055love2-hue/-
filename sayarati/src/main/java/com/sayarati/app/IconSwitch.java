package com.sayarati.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.webkit.JavascriptInterface;

/** يغيّر أيقونة سيارتي: يشغّل شكل واحد ويطفّي الباقي */
public class IconSwitch {
    static final int COUNT = 12;
    private final Activity a;
    public IconSwitch(Activity a) { this.a = a; }

    private ComponentName cn(int i) { return new ComponentName(a.getPackageName(), a.getPackageName() + ".Ic" + i); }

    @JavascriptInterface
    public int get() {
        PackageManager pm = a.getPackageManager();
        for (int i = 1; i <= COUNT; i++) {
            int s = pm.getComponentEnabledSetting(cn(i));
            if (s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (i == 1 && s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) return i;
        }
        return 1;
    }

    @JavascriptInterface
    public boolean set(int n) {
        if (n < 1 || n > COUNT) return false;
        try {
            PackageManager pm = a.getPackageManager();
            // نشغّل الجديدة أول، بعدين نطفّي الباقي عشان ما يختفي التطبيق
            pm.setComponentEnabledSetting(cn(n), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (int i = 1; i <= COUNT; i++) {
                if (i == n) continue;
                pm.setComponentEnabledSetting(cn(i), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            return true;
        } catch (Exception e) { return false; }
    }
}
