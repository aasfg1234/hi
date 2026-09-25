package io.github.aasfg1234.palmguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;

/** 在所有 App 上面放一塊擋板。擋板開著的時候，通知欄會一直有一則通知。 */
public final class GuardService extends Service implements GuardView.Listener {

    static final String ACTION_START = "io.github.aasfg1234.palmguard.START";
    static final String ACTION_TOGGLE_VISIBLE = "io.github.aasfg1234.palmguard.TOGGLE_VISIBLE";
    static final String ACTION_TOGGLE_LOCK = "io.github.aasfg1234.palmguard.TOGGLE_LOCK";
    static final String ACTION_REFRESH = "io.github.aasfg1234.palmguard.REFRESH";
    static final String ACTION_STOP = "io.github.aasfg1234.palmguard.STOP";

    private static final String CHANNEL = "guard";
    private static final int NOTIFY_ID = 1;

    static volatile boolean running = false;
    static volatile boolean visible = false;

    private Context windowContext;
    private WindowManager wm;
    private GuardView view;
    private WindowManager.LayoutParams lp;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowContext = makeWindowContext();
        wm = (WindowManager) windowContext.getSystemService(Context.WINDOW_SERVICE);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "擋板狀態", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Context makeWindowContext() {
        if (Build.VERSION.SDK_INT >= 30) {
            DisplayManager dm = getSystemService(DisplayManager.class);
            Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
            return createDisplayContext(display).createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        return this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null && intent.getAction() != null
                ? intent.getAction() : ACTION_START;

        if (!running) {
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            // 新開的服務要馬上變成前景服務，不然系統會把它關掉
            try {
                goForeground();
            } catch (RuntimeException e) {
                stopSelf();
                return START_NOT_STICKY;
            }
            running = true;
            action = ACTION_START;
        }

        if (ACTION_STOP.equals(action) || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            showGuard();
        } else if (ACTION_TOGGLE_VISIBLE.equals(action)) {
            if (visible) hideGuard();
            else showGuard();
        } else if (ACTION_TOGGLE_LOCK.equals(action)) {
            Prefs.setLocked(this, !Prefs.locked(this));
            refreshGuard();
        } else if (ACTION_REFRESH.equals(action)) {
            refreshGuard();
        }
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideGuard();
        running = false;
        visible = false;
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 轉向以後，照原本的比例重算擋板高度
        if (view != null) {
            lp.height = targetHeight();
            wm.updateViewLayout(view, lp);
            view.invalidate();
        }
    }

    // ---------- 擋板 ----------

    private void showGuard() {
        if (view == null) {
            view = new GuardView(windowContext, this);
            view.setState(Prefs.rightHanded(this), Prefs.locked(this));
            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    targetHeight(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // NOT_TOUCH_MODAL + SPLIT_TOUCH：擋板外面的觸控照常給下面的 App
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.START;
            lp.setTitle("PalmGuard");
            try {
                wm.addView(view, lp);
            } catch (RuntimeException e) {
                view = null;
                visible = false;
                return;
            }
        }
        visible = true;
    }

    private void hideGuard() {
        if (view != null) {
            try {
                wm.removeView(view);
            } catch (RuntimeException ignored) {
                // 視窗已經不在了
            }
            view = null;
        }
        visible = false;
    }

    private void refreshGuard() {
        if (view != null) view.setState(Prefs.rightHanded(this), Prefs.locked(this));
    }

    private int screenHeight() {
        if (Build.VERSION.SDK_INT >= 30) {
            return wm.getCurrentWindowMetrics().getBounds().height();
        }
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int clampHeight(int h) {
        float dp = getResources().getDisplayMetrics().density;
        int min = Math.round(64 * dp);
        int max = Math.round(screenHeight() * 0.9f);
        return Math.max(min, Math.min(max, h));
    }

    private int targetHeight() {
        return clampHeight(Math.round(screenHeight() * Prefs.ratio(this)));
    }

    @Override
    public void onResize(int newHeight) {
        if (view == null) return;
        int h = clampHeight(newHeight);
        if (h == lp.height) return;
        lp.height = h;
        wm.updateViewLayout(view, lp);
    }

    @Override
    public void onResizeEnd() {
        if (view == null) return;
        Prefs.setRatio(this, lp.height / (float) screenHeight());
    }

    // ---------- 通知 ----------

    private Notification buildNotification() {
        boolean locked = Prefs.locked(this);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(visible ? "手掌擋板開著" : "手掌擋板暫時藏起來")
                .setContentText(visible
                        ? "手放在藍色區塊上，不會點到下面的 App"
                        : "按「顯示」叫回擋板")
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(openAppIntent())
                .addAction(action(visible ? "隱藏" : "顯示", ACTION_TOGGLE_VISIBLE, 1))
                .addAction(action(locked ? "解鎖把手" : "鎖定把手", ACTION_TOGGLE_LOCK, 2))
                .addAction(action("關閉", ACTION_STOP, 3));
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private Notification.Action action(String title, String act, int requestCode) {
        Intent i = new Intent(this, GuardService.class).setAction(act);
        PendingIntent pi = PendingIntent.getService(this, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_notify), title, pi).build();
    }

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void goForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFY_ID, n);
        }
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIFY_ID, buildNotification());
    }
}
