package io.github.aasfg1234.palmguard;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuService;

/**
 * 跟 Shizuku 講話的最小版本。
 * Shizuku 會透過 ShizukuBinderProvider 把連線交給我們，
 * 之後就能用 shell 身分（比一般 App 高一點的權限）執行指令。
 */
final class ShizukuClient {

    interface Listener {
        void onShizukuChanged();
    }

    private static final int API_VERSION = 13;
    private static final String ATTACH_PACKAGE_NAME = "shizuku:attach-package-name";
    private static final String ATTACH_API_VERSION = "shizuku:attach-api-version";
    private static final String REPLY_PERMISSION_GRANTED = "shizuku:attach-reply-permission-granted";
    private static final String REPLY_ALLOWED = "shizuku:request-permission-reply-allowed";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile IShizukuService service;
    private static volatile boolean granted;
    private static Listener listener;

    private ShizukuClient() {}

    static boolean connected() {
        IShizukuService s = service;
        return s != null && s.asBinder().pingBinder();
    }

    static boolean granted() {
        return connected() && granted;
    }

    static void setListener(Listener l) {
        listener = l;
    }

    private static void notifyChanged() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onShizukuChanged();
            }
        });
    }

    private static final IShizukuApplication.Stub APPLICATION = new IShizukuApplication.Stub() {
        @Override
        public void bindApplication(Bundle data) {
            granted = data != null && data.getBoolean(REPLY_PERMISSION_GRANTED, false);
            notifyChanged();
        }

        @Override
        public void dispatchRequestPermissionResult(int requestCode, Bundle data) {
            granted = data != null && data.getBoolean(REPLY_ALLOWED, false);
            notifyChanged();
        }

        @Override
        public void showPermissionConfirmation(int requestUid, int requestPid, String packageName, int requestCode) {
            // 只有 Shizuku 自己的管理 App 會用到
        }
    };

    /** Shizuku 把連線送來了。 */
    static void onBinderReceived(IBinder binder, String packageName) {
        if (binder == null) return;
        IShizukuService s = IShizukuService.Stub.asInterface(binder);
        service = s;
        granted = false;
        try {
            binder.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    service = null;
                    granted = false;
                    notifyChanged();
                }
            }, 0);
        } catch (RemoteException e) {
            service = null;
            notifyChanged();
            return;
        }
        Bundle args = new Bundle();
        args.putString(ATTACH_PACKAGE_NAME, packageName);
        args.putInt(ATTACH_API_VERSION, API_VERSION);
        try {
            s.attachApplication(APPLICATION, args);
        } catch (RemoteException e) {
            service = null;
        }
        notifyChanged();
    }

    /** 跳出 Shizuku 的「允許」視窗。 */
    static void requestPermission() {
        IShizukuService s = service;
        if (s == null) return;
        try {
            s.requestPermission(1);
        } catch (RemoteException ignored) {
            // Shizuku 剛好停了，畫面會顯示沒連線
        }
    }

    /**
     * 用 shell 身分執行一行指令，回傳畫面上會看到的文字。
     * 會卡住等指令結束，不能在主執行緒呼叫。
     */
    static String run(String command, long timeoutMs) {
        IShizukuService s = service;
        if (s == null) return "（沒有連上 Shizuku）\n";
        IRemoteProcess p = null;
        try {
            p = s.newProcess(new String[] {"sh", "-c", command + " 2>&1"}, null, null);
            ParcelFileDescriptor outPfd = p.getOutputStream();
            if (outPfd != null) outPfd.close();
            String out = readAll(new ParcelFileDescriptor.AutoCloseInputStream(p.getInputStream()));
            if (!p.waitForTimeout(timeoutMs, "MILLISECONDS")) {
                p.destroy();
                return out + "\n（指令超過時間，已停止）\n";
            }
            return out;
        } catch (Exception e) {
            return "（執行失敗：" + e + "）\n";
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (RemoteException ignored) {
                    // 已經結束
                }
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        try {
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        } finally {
            in.close();
        }
        return buf.toString("UTF-8");
    }
}
