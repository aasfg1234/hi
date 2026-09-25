package io.github.aasfg1234.palmguard;

import android.os.SystemClock;
import android.view.MotionEvent;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * 記下擋板收到的觸控，用來查「手掌放上去，擋板收到什麼」。
 * 只存在記憶體裡，最多留最後 300 行。
 */
final class TouchLog {

    private static final int MAX_LINES = 300;
    /** 安卓 13 以後，系統認出手掌時會用這兩個值（舊版 SDK 沒有這兩個名字）。 */
    private static final int TOOL_TYPE_PALM = 5;
    private static final int FLAG_CANCELED = 0x20;

    private static final ArrayDeque<String> LINES = new ArrayDeque<String>();
    private static long t0 = SystemClock.uptimeMillis();

    // 移動太多，不一行一行記，改成一段一段加總
    private static int moveCount = 0;
    private static float moveDist = 0;
    private static int followCount = 0;
    private static float lastX;
    private static float lastY;

    private TouchLog() {}

    static synchronized void clear() {
        LINES.clear();
        moveCount = 0;
        moveDist = 0;
        followCount = 0;
        t0 = SystemClock.uptimeMillis();
    }

    static synchronized String text() {
        flushMoves();
        if (LINES.isEmpty()) return "（還沒有紀錄。把手掌放到擋板上移動看看）";
        StringBuilder b = new StringBuilder();
        for (String s : LINES) b.append(s).append('\n');
        return b.toString();
    }

    /** 擋板因為這次移動跟著走了一步。 */
    static synchronized void followed() {
        followCount++;
    }

    static synchronized void event(MotionEvent ev, float dp) {
        int action = ev.getActionMasked();
        float x = 0;
        float y = 0;
        for (int i = 0; i < ev.getPointerCount(); i++) {
            x += ev.getRawX(i);
            y += ev.getRawY(i);
        }
        x /= ev.getPointerCount();
        y /= ev.getPointerCount();

        if (action == MotionEvent.ACTION_MOVE) {
            if (moveCount > 0) moveDist += (float) Math.hypot(x - lastX, y - lastY) / dp;
            moveCount++;
            lastX = x;
            lastY = y;
            return;
        }
        flushMoves();
        lastX = x;
        lastY = y;
        moveCount = 0;

        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.US, "%6.2f秒 %s 點數%d", (SystemClock.uptimeMillis() - t0) / 1000f,
                actionName(action), ev.getPointerCount()));
        if ((ev.getFlags() & FLAG_CANCELED) != 0) b.append(" 系統說是誤觸");
        for (int i = 0; i < ev.getPointerCount(); i++) {
            b.append(String.format(Locale.US, " [%s 大小%.0f]",
                    toolName(ev.getToolType(i)), ev.getTouchMajor(i) / dp));
        }
        add(b.toString());
    }

    private static void flushMoves() {
        if (moveCount == 0) return;
        add(String.format(Locale.US, "        移動%d次 共%.0f 擋板跟著走%d次", moveCount, moveDist, followCount));
        moveCount = 0;
        moveDist = 0;
        followCount = 0;
    }

    private static void add(String s) {
        LINES.addLast(s);
        while (LINES.size() > MAX_LINES) LINES.removeFirst();
    }

    private static String actionName(int a) {
        switch (a) {
            case MotionEvent.ACTION_DOWN: return "按下";
            case MotionEvent.ACTION_UP: return "放開";
            case MotionEvent.ACTION_CANCEL: return "被取消";
            case MotionEvent.ACTION_POINTER_DOWN: return "多一點";
            case MotionEvent.ACTION_POINTER_UP: return "少一點";
            default: return "動作" + a;
        }
    }

    private static String toolName(int t) {
        switch (t) {
            case MotionEvent.TOOL_TYPE_FINGER: return "手指";
            case MotionEvent.TOOL_TYPE_STYLUS: return "筆";
            case TOOL_TYPE_PALM: return "手掌";
            default: return "其他" + t;
        }
    }
}
