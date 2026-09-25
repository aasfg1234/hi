package io.github.aasfg1234.palmguard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 全自動擋手掌的事前檢查。
 * 透過 Shizuku 查這台平板的權限和觸控晶片，並做「筆會不會擋掉手指」的關鍵測試。
 */
public final class CheckActivity extends Activity implements ShizukuClient.Listener, TestPad.Listener {

    private static final String INFO_COMMAND =
            "echo '== 型號'; getprop ro.product.model; getprop ro.build.display.id; "
            + "echo '== 身分'; id; "
            + "echo '== 虛擬裝置'; ls -lZ /dev/uhid /dev/uinput; "
            + "echo '== uhid 可以寫嗎'; if [ -w /dev/uhid ]; then echo 可以; else echo 不行; fi; "
            + "echo '== 觸控裝置'; getevent -lp | head -n 200";

    private float dp;
    private LinearLayout column;
    private TextView shizukuState;
    private Button permissionButton;
    private Button infoButton;
    private Button testButton;
    private TestPad pad;
    private TextView results;
    private final StringBuilder resultText = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad24 = px(24);
        column.setPadding(pad24, pad24, pad24, pad24);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, px(720));
        frame.addView(column, new FrameLayout.LayoutParams(
                width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        TextView title = text("全自動擋手掌：檢查這台平板", 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        hint("這一頁只做檢查，不會改平板的任何設定。");

        heading("連上 Shizuku");
        shizukuState = text("", 16);
        permissionButton = button("允許這個 App 使用 Shizuku", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ShizukuClient.requestPermission();
            }
        });
        hint("顯示「沒連上」時：打開 Shizuku，確認寫著「執行中」，再回到這一頁。");

        heading("檢查 1：權限和觸控晶片");
        hint("大約 5 秒。會查這台平板能不能做出虛擬的筆，以及觸控晶片回報哪些資料。");
        infoButton = button("開始檢查 1", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runInfo();
            }
        });

        heading("檢查 2：筆會不會擋掉手指");
        hint("按下按鈕後，用一根手指在下面的框裡一直畫圈，不要拿起來，大約 5 秒。"
                + "框的右上方會出現一個紅點，那是 App 假裝的筆。畫到結果出現再拿起手指。");
        testButton = button("開始檢查 2", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy) return;
                pad.arm();
                testButton.setEnabled(false);
            }
        });
        pad = new TestPad(this, this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = px(8);
        column.addView(pad, pp);

        heading("結果");
        button("複製全部結果", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyResults();
            }
        });
        results = text("（還沒有結果）", 13);
        results.setTypeface(Typeface.MONOSPACE);
        results.setTextIsSelectable(true);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ShizukuClient.setListener(this);
        refresh();
    }

    @Override
    protected void onPause() {
        ShizukuClient.setListener(null);
        super.onPause();
    }

    @Override
    public void onShizukuChanged() {
        refresh();
    }

    private void refresh() {
        boolean connected = ShizukuClient.connected();
        boolean granted = ShizukuClient.granted();
        shizukuState.setText(!connected ? "狀態：沒連上 Shizuku"
                : granted ? "狀態：已連上，已允許" : "狀態：已連上，還沒允許");
        permissionButton.setEnabled(connected && !granted);
        infoButton.setEnabled(granted && !busy);
        testButton.setEnabled(granted && !busy);
    }

    private void runInfo() {
        if (busy) return;
        busy = true;
        refresh();
        infoButton.setText("檢查中…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String out = "== 檢查 1：權限和觸控晶片\n" + ShizukuClient.run(INFO_COMMAND, 20000);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        infoButton.setText("開始檢查 1");
                        addResult(out);
                        refresh();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onFingerReady(final TestPad p) {
        busy = true;
        final int[] xy = p.penTargetOnScreen();
        // 先讓手指畫 1 秒，再假裝筆碰螢幕 1.5 秒，筆離開後再觀察 1 秒
        final String cmd = "sleep 1; input stylus motionevent DOWN " + xy[0] + " " + xy[1]
                + "; sleep 1.5; input stylus motionevent UP " + xy[0] + " " + xy[1] + "; sleep 1";
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String out = ShizukuClient.run(cmd, 15000);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        addResult(p.verdict(out));
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void addResult(String s) {
        if (resultText.length() > 0) resultText.append('\n');
        resultText.append(s);
        results.setText(resultText);
    }

    private void copyResults() {
        if (resultText.length() == 0) {
            Toast.makeText(this, "還沒有結果可以複製", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("手掌擋板檢查結果", resultText.toString()));
        Toast.makeText(this, "已複製，可以貼到聊天視窗", Toast.LENGTH_SHORT).show();
    }

    // ---------- 畫面小工具 ----------

    private int px(int value) {
        return Math.round(value * dp);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = px(8);
        column.addView(t, p);
        return t;
    }

    private void hint(String s) {
        text(s, 14).setAlpha(0.75f);
    }

    private void heading(String s) {
        TextView t = text(s, 19);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        ((LinearLayout.LayoutParams) t.getLayoutParams()).topMargin = px(28);
    }

    private Button button(String label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = px(8);
        column.addView(b, p);
        return b;
    }
}
