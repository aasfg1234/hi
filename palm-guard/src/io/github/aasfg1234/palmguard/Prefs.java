package io.github.aasfg1234.palmguard;

import android.content.Context;
import android.content.SharedPreferences;

/** 存使用者的設定：慣用手、擋板高度、把手有沒有鎖住。 */
final class Prefs {
    private static final String NAME = "palm_guard";
    private static final String HAND = "hand";
    private static final String RATIO = "ratio";
    private static final String LOCKED = "locked";

    static final float DEFAULT_RATIO = 0.35f;

    private Prefs() {}

    private static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean rightHanded(Context c) {
        return !"left".equals(get(c).getString(HAND, "right"));
    }

    static void setRightHanded(Context c, boolean right) {
        get(c).edit().putString(HAND, right ? "right" : "left").apply();
    }

    /** 擋板高度佔螢幕高度的比例。 */
    static float ratio(Context c) {
        return get(c).getFloat(RATIO, DEFAULT_RATIO);
    }

    static void setRatio(Context c, float ratio) {
        get(c).edit().putFloat(RATIO, ratio).apply();
    }

    static boolean locked(Context c) {
        return get(c).getBoolean(LOCKED, false);
    }

    static void setLocked(Context c, boolean locked) {
        get(c).edit().putBoolean(LOCKED, locked).apply();
    }
}
