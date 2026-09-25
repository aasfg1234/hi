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
 * 上緣有一個把手，上下拖可以調整擋板高度。
 */
final class GuardView extends View {

    interface Listener {
        void onResize(int newHeight);

        void onResizeEnd();
    }

    private static final int ACCENT = 0xFF2F6FDB;

    private final Listener listener;
    private final float dp;
    private final Paint fill = new Paint();
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF handle = new RectF();

    private boolean rightHanded = true;
    private boolean locked = false;

    private int dragPointer = -1;
    private boolean dragging = false;
    private float downRawX;
    private float downRawY;
    private int startHeight;

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

    void setState(boolean rightHanded, boolean locked) {
        this.rightHanded = rightHanded;
        this.locked = locked;
        invalidate();
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

    @Override
    protected void onDraw(Canvas canvas) {
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

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
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
        // 全部吃掉，不讓觸控傳到下面的 App
        return true;
    }
}
