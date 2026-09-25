package io.github.aasfg1234.palmguard;

import android.content.Context;
import android.content.SharedPreferences;

/** 存使用者的設定：慣用手、擋板形狀和大小、位置、有沒有鎖住。 */
final class Prefs {
    private static final String NAME = "palm_guard";
    private static final String HAND = "hand";
    private static final String RATIO = "ratio";
    private static final String LOCKED = "locked";
    private static final String SHAPE = "shape";
    private static final String WIDTH_DP = "width_dp";
    private static final String HEIGHT_DP = "height_dp";
    private static final String CENTER_X = "center_x";
    private static final String CENTER_Y = "center_y";

    static final float DEFAULT_RATIO = 0.35f;

    /** 擋板形狀。BAR 是原本貼在畫面最下面的整條。 */
    static final String SHAPE_BAR = "bar";
    static final String SHAPE_CIRCLE = "circle";
    static final String SHAPE_OVAL = "oval";
    static final String SHAPE_RECT = "rect";

    static final int MIN_SIZE_DP = 60;
    static final int MAX_SIZE_DP = 700;

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

    /** 整條擋板的高度佔螢幕高度的比例。 */
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

    static String shape(Context c) {
        return get(c).getString(SHAPE, SHAPE_BAR);
    }

    static void setShape(Context c, String shape) {
        get(c).edit().putString(SHAPE, shape).apply();
    }

    static boolean floating(Context c) {
        return !SHAPE_BAR.equals(shape(c));
    }

    /** 浮動擋板的寬（dp）。圓形用這個當直徑。 */
    static int widthDp(Context c) {
        return get(c).getInt(WIDTH_DP, 260);
    }

    static void setWidthDp(Context c, int dp) {
        get(c).edit().putInt(WIDTH_DP, clampSize(dp)).apply();
    }

    /** 浮動擋板的高（dp）。圓形不用。 */
    static int heightDp(Context c) {
        return get(c).getInt(HEIGHT_DP, 200);
    }

    static void setHeightDp(Context c, int dp) {
        get(c).edit().putInt(HEIGHT_DP, clampSize(dp)).apply();
    }

    private static int clampSize(int dp) {
        return Math.max(MIN_SIZE_DP, Math.min(MAX_SIZE_DP, dp));
    }

    /** 浮動擋板中心點的位置，用佔螢幕寬高的比例存，轉向以後還在差不多的地方。 */
    static float centerX(Context c) {
        return get(c).getFloat(CENTER_X, 0.5f);
    }

    static float centerY(Context c) {
        return get(c).getFloat(CENTER_Y, 0.7f);
    }

    static void setCenter(Context c, float x, float y) {
        get(c).edit().putFloat(CENTER_X, x).putFloat(CENTER_Y, y).apply();
    }
}
