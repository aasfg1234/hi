package io.github.aasfg1234.palmguard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 設定畫面：給權限、開關擋板、選慣用手。 */
public final class MainActivity extends Activity {

    private static final String NOTIFY_PERMISSION = "android.permission.POST_NOTIFICATIONS";

    private float dp;
    private LinearLayout column;
    private TextView permState;
    private TextView status;
    private Button permButton;
    private Button startButton;
    private Button visibleButton;
    private Button lockButton;
    private Button rightButton;
    private Button leftButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = px(24);
        column.setPadding(pad, pad, pad, pad);
        // 平板橫放時不要拉太寬，放在中間比較好讀
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, px(600));
        frame.addView(column, new FrameLayout.LayoutParams(
                width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        TextView title = text("手掌擋板", 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        text("在畫面下方放一塊半透明擋板。手掌放在擋板上，不會點到下面的 App。", 16);

        heading("第 1 步：允許擋板浮在畫面上");
        permState = text("", 16);
        permButton = button("去設定頁打開", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlaySettings();
            }
        });
        hintText("設定頁打開後，找到「手掌擋板」，把開關打開，再按返回鍵回來。");

        heading("第 2 步：開啟擋板");
        status = text("", 16);
        startButton = button("開啟擋板", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartStop();
            }
        });

        heading("你用哪隻手寫字？");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(row);
        rightButton = new Button(this);
        leftButton = new Button(this);
        row.addView(rightButton, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(leftButton, new LinearLayout.LayoutParams(0, -2, 1));
        rightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setHand(true);
            }
        });
        leftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setHand(false);
            }
        });
        hintText("擋板的把手會放在另一邊，比較不會被手掌碰到。");

        heading("擋板控制");
        visibleButton = button("暫時隱藏擋板", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(GuardService.ACTION_TOGGLE_VISIBLE);
            }
        });
        lockButton = button("鎖定把手", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (GuardService.running) {
                    send(GuardService.ACTION_TOGGLE_LOCK);
                } else {
                    Prefs.setLocked(MainActivity.this, !Prefs.locked(MainActivity.this));
                    refreshUi();
                }
            }
        });

        heading("怎麼用");
        hintText("1. 拖擋板上緣的藍色把手，可以調整擋板高度。");
        hintText("2. 調好以後按「鎖定把手」，手掌就不會不小心拖到把手。");
        hintText("3. 寫字時把筆記往上捲，讓正在寫的那一行在擋板上面。");
        hintText("4. 拉下通知欄，也可以隱藏或關閉擋板。");

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        // 剛允許通知的話，重發一次通知，讓控制按鈕出現
        if (GuardService.running) send(GuardService.ACTION_REFRESH);
    }

    private void refreshUi() {
        boolean allowed = Settings.canDrawOverlays(this);
        permState.setText(allowed ? "狀態：已允許" : "狀態：還沒允許");
        permButton.setText(allowed ? "已允許（可以再去設定頁看看）" : "去設定頁打開");

        boolean running = GuardService.running;
        boolean visible = GuardService.visible;
        startButton.setText(running ? "關閉擋板" : "開啟擋板");
        status.setText(!running ? "狀態：擋板關著"
                : visible ? "狀態：擋板開著" : "狀態：擋板暫時藏起來");

        visibleButton.setEnabled(running);
        visibleButton.setText(running && !visible ? "顯示擋板" : "暫時隱藏擋板");
        lockButton.setText(Prefs.locked(this) ? "解鎖把手" : "鎖定把手");

        boolean right = Prefs.rightHanded(this);
        rightButton.setText(right ? "右手 ✓" : "右手");
        leftButton.setText(right ? "左手" : "左手 ✓");
    }

    private void onStartStop() {
        if (GuardService.running) {
            send(GuardService.ACTION_STOP);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "請先做第 1 步：允許擋板浮在畫面上", Toast.LENGTH_LONG).show();
            return;
        }
        // 沒有通知權限也能用，只是通知欄不會出現控制按鈕
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(NOTIFY_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {NOTIFY_PERMISSION}, 1);
        }
        Intent i = new Intent(this, GuardService.class).setAction(GuardService.ACTION_START);
        startForegroundService(i);
        refreshSoon();
    }

    private void send(String action) {
        if (!GuardService.running) {
            refreshUi();
            return;
        }
        startService(new Intent(this, GuardService.class).setAction(action));
        refreshSoon();
    }

    private void setHand(boolean right) {
        Prefs.setRightHanded(this, right);
        if (GuardService.running) send(GuardService.ACTION_REFRESH);
        refreshUi();
    }

    private void openOverlaySettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void refreshSoon() {
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 250);
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

    private void hintText(String s) {
        TextView t = text(s, 14);
        t.setAlpha(0.75f);
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
