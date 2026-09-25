package io.github.aasfg1234.palmguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * 關鍵測試用的畫布。
 * 使用者用手指一直畫圈，同時 App 用 shell 假裝有一支筆碰螢幕。
 * 如果安卓的「有筆就丟掉手指」規則在這台平板有效，
 * 手指會收到取消，而且筆在的時候手指的移動不會再送進來。
 */
final class TestPad extends View {

    interface Listener {
        /** 手指第一次放下，可以開始假裝有筆了。 */
        void onFingerReady(TestPad pad);
    }

    private final Listener listener;
    private final float dp;
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fingerInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint penInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fingerPath = new Path();

    private boolean armed = false;
    private boolean started = false;
    private boolean fingerDown = false;
    private boolean fingerLiftedEarly = false;
    private boolean stylusDown = false;
    private boolean sawStylus = false;
    private boolean stylusEnded = false;
    private boolean fingerCancelled = false;
    private int movesBefore = 0;
    private int movesDuring = 0;
    private int movesAfter = 0;
    private float penX = -1;
    private float penY = -1;
    private final StringBuilder log = new StringBuilder();
    private long t0;
    private String message = "按「開始檢查 2」後，用一根手指在這裡一直畫圈";

    TestPad(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        dp = context.getResources().getDisplayMetrics().density;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2 * dp);
        border.setColor(0xFF2F6FDB);
        fingerInk.setStyle(Paint.Style.STROKE);
        fingerInk.setStrokeWidth(3 * dp);
        fingerInk.setColor(0xFF8A94A6);
        penInk.setColor(0xFFD64545);
        label.setColor(0xFF8A94A6);
        label.setTextSize(15 * dp);
        label.setTextAlign(Paint.Align.CENTER);
    }

    /** 重設，準備新的一輪。 */
    void arm() {
        armed = true;
        started = false;
        fingerDown = false;
        fingerLiftedEarly = false;
        stylusDown = false;
        sawStylus = false;
        stylusEnded = false;
        fingerCancelled = false;
        movesBefore = movesDuring = movesAfter = 0;
        penX = penY = -1;
        fingerPath.reset();
        log.setLength(0);
        message = "用一根手指在這裡一直畫圈，不要拿起來";
        invalidate();
    }

    void setMessage(String m) {
        message = m;
        invalidate();
    }

    /** 假裝的筆要點在畫布的哪裡（螢幕座標）。 */
    int[] penTargetOnScreen() {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        return new int[] {loc[0] + Math.round(getWidth() * 0.75f), loc[1] + Math.round(getHeight() * 0.3f)};
    }

    private void note(String s) {
        if (log.length() > 4000) return;
        log.append(String.format("%5d ms  %s\n", SystemClock.uptimeMillis() - t0, s));
    }

    private static String actionName(int a) {
        switch (a) {
            case MotionEvent.ACTION_DOWN: return "按下";
            case MotionEvent.ACTION_UP: return "放開";
            case MotionEvent.ACTION_MOVE: return "移動";
            case MotionEvent.ACTION_CANCEL: return "被取消";
            case MotionEvent.ACTION_POINTER_DOWN: return "多一點按下";
            case MotionEvent.ACTION_POINTER_UP: return "少一點放開";
            case MotionEvent.ACTION_HOVER_ENTER: return "懸停進入";
            case MotionEvent.ACTION_HOVER_EXIT: return "懸停離開";
            default: return "動作 " + a;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!armed) return false;
        getParent().requestDisallowInterceptTouchEvent(true);
        int action = ev.getActionMasked();
        boolean stylus = ev.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS;

        if (action != MotionEvent.ACTION_MOVE) {
            note((stylus ? "筆 " : "手指 ") + actionName(action) + "  裝置 " + ev.getDeviceId());
        }

        if (stylus) {
            sawStylus = true;
            penX = ev.getX();
            penY = ev.getY();
            if (action == MotionEvent.ACTION_DOWN) stylusDown = true;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                stylusDown = false;
                stylusEnded = true;
            }
            invalidate();
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!started) {
                    t0 = SystemClock.uptimeMillis();
                    log.setLength(0);
                    note("手指 按下  裝置 " + ev.getDeviceId());
                    started = true;
                    fingerDown = true;
                    fingerPath.moveTo(ev.getX(), ev.getY());
                    message = "繼續畫圈，不要拿起來…";
                    listener.onFingerReady(this);
                } else {
                    fingerDown = true;
                    fingerPath.moveTo(ev.getX(), ev.getY());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                fingerPath.lineTo(ev.getX(), ev.getY());
                if (stylusDown) movesDuring++;
                else if (stylusEnded) movesAfter++;
                else movesBefore++;
                break;
            case MotionEvent.ACTION_CANCEL:
                fingerCancelled = true;
                fingerDown = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!stylusEnded && !fingerCancelled) fingerLiftedEarly = true;
                fingerDown = false;
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    /** 測試做完了，整理結果。 */
    String verdict(String commandOutput) {
        armed = false;
        StringBuilder r = new StringBuilder();
        r.append("== 檢查 2：筆會不會擋掉手指\n");
        r.append("收到假裝的筆：").append(sawStylus ? "是" : "否").append('\n');
        r.append("手指被取消：").append(fingerCancelled ? "是" : "否").append('\n');
        r.append("手指移動次數：筆出現前 ").append(movesBefore)
                .append("，筆在的時候 ").append(movesDuring)
                .append("，筆離開後 ").append(movesAfter).append('\n');
        String result;
        if (fingerLiftedEarly) {
            result = "無法判斷：手指太早拿起來了，請再測一次，手指要一直畫圈";
        } else if (!sawStylus) {
            result = "無法判斷：畫布沒收到假裝的筆";
        } else if (fingerCancelled || movesDuring == 0) {
            result = "通過：筆一出現，手指就被擋掉了。全自動方案的核心在這台平板有效";
        } else {
            result = "沒通過：筆出現時手指還在動。全自動方案的核心在這台平板無效";
        }
        r.append("結果：").append(result).append('\n');
        r.append("-- 事件紀錄\n").append(log);
        if (commandOutput != null && commandOutput.trim().length() > 0) {
            r.append("-- 指令輸出\n").append(commandOutput);
        }
        message = result;
        invalidate();
        return r.toString();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(dp, dp, getWidth() - dp, getHeight() - dp, border);
        canvas.drawPath(fingerPath, fingerInk);
        if (penX >= 0) canvas.drawCircle(penX, penY, 10 * dp, penInk);
        canvas.drawText(message, getWidth() / 2f, getHeight() - 20 * dp, label);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Math.round(320 * dp));
    }
}
