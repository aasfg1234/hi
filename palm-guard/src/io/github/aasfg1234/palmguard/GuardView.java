package io.github.aasfg1234.palmguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * 擋板本體：畫一塊半透明區塊，吃掉所有碰到它的觸控。
 * 整條擋板：上緣有一個把手，上下拖可以調整擋板高度。
 * 浮動擋板（圓形、橢圓、方塊）：手掌放在上面移動，擋板就跟著手掌走。
 */
final class GuardView extends View {

    interface Listener {
        void onResize(int newHeight);

        void onResizeEnd();

        /** 手掌拖著浮動擋板移動了 dx、dy（螢幕像素）。 */
        void onMove(float dx, float dy);

        void onMoveEnd();
    }

    private static final int ACCENT = 0xFF2F6FDB;

    private final Listener listener;
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF handle = new RectF();
    private final RectF box = new RectF();

    private String shape = Prefs.SHAPE_BAR;
    private boolean rightHanded = true;
    private boolean locked = false;

    // 整條擋板：拖把手調高度
    private int dragPointer = -1;
    private boolean dragging = false;
    private float downRawX;
    private float downRawY;
    private int startHeight;

    // 浮動擋板：跟著手掌走
    private boolean moving = false;
    private float lastCx;
    private float lastCy;
    private float pendingDx;
    private float pendingDy;

    GuardView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        dp = context.getResources().getDisplayMetrics().density;

        fill.setColor(0x382F6FDB);
        edge.setColor(0xCC2F6FDB);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2 * dp);
        edge.setPathEffect(new DashPathEffect(new float[] {8 * dp, 6 * dp}, 0));
        pillText.setColor(Color.WHITE);
        pillText.setTextSize(14 * dp);
        pillText.setTextAlign(Paint.Align.CENTER);
        hint.setColor(0xB32F6FDB);
        hint.setTextSize(15 * dp);
        hint.setTextAlign(Paint.Align.CENTER);
    }

    void setState(String shape, boolean rightHanded, boolean locked) {
        this.shape = shape;
        this.rightHanded = rightHanded;
        this.locked = locked;
        invalidate();
    }

    private boolean floating() {
        return !Prefs.SHAPE_BAR.equals(shape);
    }

    // ---------- 畫 ----------

    @Override
    protected void onDraw(Canvas canvas) {
        if (floating()) {
            drawFloating(canvas);
        } else {
            drawBar(canvas);
        }
    }

    private void drawFloating(Canvas canvas) {
        float inset = edge.getStrokeWidth() / 2;
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        if (Prefs.SHAPE_RECT.equals(shape)) {
            float r = 24 * dp;
            canvas.drawRoundRect(box, r, r, fill);
            canvas.drawRoundRect(box, r, r, edge);
        } else {
            // 圓形和橢圓都用 drawOval；圓形的寬高一樣
            canvas.drawOval(box, fill);
            canvas.drawOval(box, edge);
        }
        if (locked && getWidth() > 100 * dp && getHeight() > 60 * dp) {
            Paint.FontMetrics fm = hint.getFontMetrics();
            float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2;
            canvas.drawText("位置已鎖定", getWidth() / 2f, y, hint);
        }
    }

    private String handleLabel() {
        return locked ? "把手已鎖定" : "↕ 拖我調高度";
    }

    /** 把手放在慣用手的另一邊，比較不會被手掌碰到。 */
    private void layoutHandle() {
        float w = pillText.measureText(handleLabel()) + 32 * dp;
        float h = 36 * dp;
        float margin = 16 * dp;
        float top = 8 * dp;
        float left = rightHanded ? margin : getWidth() - margin - w;
        handle.set(left, top, left + w, top + h);
    }

    private void drawBar(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, fill);
        float lineY = edge.getStrokeWidth() / 2;
        canvas.drawLine(0, lineY, w, lineY, edge);

        layoutHandle();
        pill.setColor(locked ? 0xCC5B6679 : ACCENT);
        float r = handle.height() / 2;
        canvas.drawRoundRect(handle, r, r, pill);
        Paint.FontMetrics fm = pillText.getFontMetrics();
        float textY = handle.centerY() - (fm.ascent + fm.descent) / 2;
        canvas.drawText(handleLabel(), handle.centerX(), textY, pillText);

        if (h > 120 * dp) {
            float hintY = Math.max(handle.bottom + 40 * dp, h / 2f);
            canvas.drawText("手放這裡，不會點到下面的 App", w / 2f, hintY, hint);
        }
    }

    // ---------- 觸控 ----------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        TouchLog.event(ev, dp);
        if (floating()) {
            onFloatingTouch(ev);
        } else {
            onBarTouch(ev);
        }
        // 全部吃掉，不讓觸控傳到下面的 App
        return true;
    }

    /**
     * 手掌碰螢幕常常是好幾個點，而且點會一直出現、消失。
     * 所以用「所有點的中心」當手掌位置；點的數量一變，就從新的中心重新算，擋板才不會突然跳一下。
     */
    private void onFloatingTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (moving) listener.onMoveEnd();
            moving = false;
            return;
        }
        if (locked) return;

        // 放開的那一點不算
        int skip = action == MotionEvent.ACTION_POINTER_UP ? ev.getActionIndex() : -1;
        float sx = 0;
        float sy = 0;
        int n = 0;
        for (int i = 0; i < ev.getPointerCount(); i++) {
            if (i == skip) continue;
            sx += ev.getRawX(i);
            sy += ev.getRawY(i);
            n++;
        }
        if (n == 0) return;
        float cx = sx / n;
        float cy = sy / n;

        if (action != MotionEvent.ACTION_MOVE) {
            // 按下、多一點、少一點：只換起點，不移動
            if (action == MotionEvent.ACTION_DOWN) {
                moving = false;
                pendingDx = 0;
                pendingDy = 0;
            }
            lastCx = cx;
            lastCy = cy;
            return;
        }

        float dx = cx - lastCx;
        float dy = cy - lastCy;
        lastCx = cx;
        lastCy = cy;
        if (!moving) {
            // 手掌放著會小小抖動；移超過 6dp 才開始跟著走
            pendingDx += dx;
            pendingDy += dy;
            if (Math.hypot(pendingDx, pendingDy) < 6 * dp) return;
            moving = true;
            dx = pendingDx;
            dy = pendingDy;
        }
        TouchLog.followed();
        listener.onMove(dx, dy);
    }

    private boolean onHandle(float x, float y) {
        float pad = 12 * dp;
        return x >= handle.left - pad && x <= handle.right + pad
                && y >= handle.top - pad && y <= handle.bottom + pad;
    }

    private void maybeStartDrag(MotionEvent ev, int index) {
        if (locked || dragPointer >= 0) return;
        if (!onHandle(ev.getX(index), ev.getY(index))) return;
        dragPointer = ev.getPointerId(index);
        dragging = false;
        downRawX = ev.getRawX(index);
        downRawY = ev.getRawY(index);
        startHeight = getHeight();
    }

    private void finishDrag() {
        if (dragging) listener.onResizeEnd();
        dragging = false;
        dragPointer = -1;
    }

    private void onBarTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragPointer = -1;
                dragging = false;
                maybeStartDrag(ev, 0);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                maybeStartDrag(ev, ev.getActionIndex());
                break;
            case MotionEvent.ACTION_MOVE: {
                if (dragPointer < 0) break;
                int i = ev.findPointerIndex(dragPointer);
                if (i < 0) break;
                float dx = ev.getRawX(i) - downRawX;
                float dy = ev.getRawY(i) - downRawY;
                if (!dragging) {
                    // 手掌貼著寫字多半是左右滑；要明顯上下拖才算在調高度
                    if (Math.abs(dy) > 12 * dp && Math.abs(dy) > Math.abs(dx)) {
                        dragging = true;
                    } else {
                        break;
                    }
                }
                listener.onResize(Math.round(startHeight - dy));
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (ev.getPointerId(ev.getActionIndex()) == dragPointer) finishDrag();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishDrag();
                break;
            default:
                break;
        }
    }
}
