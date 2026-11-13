package com.dsh.dsumanager;

import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/** Shizuku SDK 封装：无 Root 时通过 Shizuku 执行 shell（只读/受限）。 */
public final class ShizukuHelper {

    private static final ExecutorService EXEC = Executors.newCachedThreadPool();

    public static boolean ping() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return ping() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        try {
            if (ping()) Shizuku.requestPermission(requestCode);
        } catch (Throwable ignored) {
        }
    }

    public static void addPermissionListener(Shizuku.OnRequestPermissionResultListener l) {
        try {
            Shizuku.addRequestPermissionResultListener(l);
        } catch (Throwable ignored) {
        }
    }

    public static void removePermissionListener(Shizuku.OnRequestPermissionResultListener l) {
        try {
            Shizuku.removeRequestPermissionResultListener(l);
        } catch (Throwable ignored) {
        }
    }

    public static void addBinderListener(Shizuku.OnBinderReceivedListener l) {
        try {
            Shizuku.addBinderReceivedListenerSticky(l);
        } catch (Throwable ignored) {
        }
    }

    /** 通过 IShizukuService.newProcess 执行 shell（兼容 Shizuku 13.x 服务端）。 */
    public static RootShell.Result exec(String cmd, int timeoutSec) {
        final StringBuilder sb = new StringBuilder();
        IRemoteProcess rp = null;
        try {
            IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
            if (service == null) return new RootShell.Result(-1, "Shizuku 未连接");
            String shell = "/system/bin/sh";
            rp = service.newProcess(new String[]{shell, "-c", cmd + " 2>&1"}, null, null);
            if (rp == null) return new RootShell.Result(-1, "Shizuku 无法创建进程");

            ParcelFileDescriptor pfd = rp.getInputStream();
            if (pfd != null) {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                }
            }
            int code;
            try {
                boolean finished = rp.waitForTimeout(Math.max(1, timeoutSec) * 1000L, null);
                if (!finished) {
                    try { rp.destroy(); } catch (Throwable ignored) { }
                    return new RootShell.Result(-2, "Shizuku 执行超时\n" + sb);
                }
                code = rp.exitValue();
            } catch (Throwable t) {
                code = -3;
            }
            return new RootShell.Result(code, sb.toString());
        } catch (Throwable t) {
            if (rp != null) {
                try { rp.destroy(); } catch (Throwable ignored) { }
            }
            return new RootShell.Result(-3, "Shizuku 执行失败：" + t.getMessage() + "\n" + sb);
        }
    }

    private ShizukuHelper() {}
}
