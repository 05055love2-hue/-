package com.rawatib.app;

import android.content.Context;

/** ألوان وأشكال الويدجت */
public class WTheme {
    static int bg(Context c) {
        String t = c.getSharedPreferences("pay", Context.MODE_PRIVATE).getString("wtheme", "green");
        switch (t) {
            case "blue": return R.drawable.wbg_blue;
            case "purple": return R.drawable.wbg_purple;
            case "sunset": return R.drawable.wbg_sunset;
            case "pink": return R.drawable.wbg_pink;
            case "gold": return R.drawable.wbg_gold;
            case "night": return R.drawable.wbg_night;
            case "glass": return R.drawable.wbg_glass;
            case "glassdark": return R.drawable.wbg_glassdark;
            default: return R.drawable.wbg_green;
        }
    }
}
