package com.darbi.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;
import android.widget.TextView;

/** شاشة سودا: تغطي كل شي وتطفّي الإضاءة لأقل شي، وأي لمسة أو زر يرجّعها */
public class BlackScreen {
    private static FrameLayout v;

    public static boolean isOn() { return v != null; }

    public static void show(final Activity a) {
        a.runOnUiThread(new Runnable() { public void run() {
            if (v != null) return;
            v = new FrameLayout(a);
            v.setBackgroundColor(Color.BLACK);
            v.setClickable(true);
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
            TextView t = new TextView(a);
            t.setText("اضغط على الشاشة للرجوع");
            t.setTextColor(0x66FFFFFF);
            t.setTextSize(16);
            v.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            t.animate().alpha(0f).setStartDelay(2500).setDuration(1500).start();
            v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { hide(a); } });
            v.setOnKeyListener(new View.OnKeyListener() { public boolean onKey(View x, int k, KeyEvent e) {
                if (isPassKey(k)) return false;
                if (e.getAction() == KeyEvent.ACTION_UP) hide(a);
                return true;
            }});
            ((ViewGroup) a.getWindow().getDecorView()).addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            v.requestFocus();
            WindowManager.LayoutParams w = a.getWindow().getAttributes();
            w.screenBrightness = 0.01f;
            a.getWindow().setAttributes(w);
        }});
    }

    /** أزرار الصوت والوسائط تشتغل عادي والشاشة سودا */
    public static boolean isPassKey(int k) {
        return k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE
            || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_NEXT || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS
            || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE;
    }

    public static boolean hide(Activity a) {
        if (v == null) return false;
        ViewGroup p = (ViewGroup) v.getParent();
        if (p != null) p.removeView(v);
        v = null;
        WindowManager.LayoutParams w = a.getWindow().getAttributes();
        w.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        a.getWindow().setAttributes(w);
        return true;
    }

    /** يناديه الكود اللي في الصفحة: BlackScr.on() */
    public static class JS {
        private final Activity a;
        public JS(Activity a) { this.a = a; }
        @JavascriptInterface public void on() { show(a); }
    }
}
