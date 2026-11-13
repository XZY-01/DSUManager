package com.dsh.dsumanager;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * 原生安装：DSU Sideloader 方式。
 * 把单 system 镜像压缩为 .img.gz 放入下载目录，然后通过 shell（Shizuku 免 Root 或 root）
 * 调起系统 com.android.dynsystem 的 VerificationActivity 完成安装。
 */
public final class DsuSideloadInstaller {

    public interface Progress {
        void onProgress(int permille, String message);
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final String gzPath;
        public final int shellCode;
        public final String shellOut;

        Result(boolean ok, String message, String gzPath, int shellCode, String shellOut) {
            this.ok = ok;
            this.message = message;
            this.gzPath = gzPath;
            this.shellCode = shellCode;
            this.shellOut = shellOut;
        }
    }

    public static Result install(Context ctx, File rawImage, long rawSize,
                                 long userdataBytes, Progress cb) {
        if (rawImage == null || !rawImage.exists() || rawImage.length() <= 0) {
            return new Result(false, "镜像文件不存在", null, -1, "");
        }
        String displayName = "DSUManager-" + System.currentTimeMillis() + ".img.gz";
        ContentResolver cr = ctx.getContentResolver();
        Uri uri = null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/gzip");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/DSUManager");
            uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return new Result(false, "无法在下载目录创建文件", null, -1, "");

            long total = rawImage.length();
            long done = 0, lastPct = -1;
            try (InputStream in = new FileInputStream(rawImage);
                 OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) throw new RuntimeException("无法写入下载文件");
                GZIPOutputStream gz = new GZIPOutputStream(os, 1 << 16) {
                    {
                        def.setLevel(Deflater.BEST_SPEED);
                    }
                };
                byte[] buf = new byte[1 << 20];
                int n;
                while ((n = in.read(buf)) > 0) {
                    gz.write(buf, 0, n);
                    done += n;
                    int pct = (int) Math.min(1000, done * 1000 / Math.max(1, total));
                    if (pct != lastPct) {
                        lastPct = pct;
                        if (cb != null) cb.onProgress(pct, "正在压缩镜像（DSU 需要 gz 格式）："
                                + DsuManager.fmtBytes(done) + " / " + DsuManager.fmtBytes(total));
                    }
                }
                gz.finish();
                gz.flush();
            }

            String path = "/storage/emulated/0/Download/DSUManager/" + displayName;
            String cmd = "am force-stop com.android.dynsystem; "
                    + "setprop persist.sys.fflag.override.settings_dynamic_system true; "
                    + "am start-activity -n com.android.dynsystem/com.android.dynsystem.VerificationActivity "
                    + "-a android.os.image.action.START_INSTALL "
                    + "-d file://" + shellQuote(path) + " "
                    + "--el KEY_USERDATA_SIZE " + userdataBytes + " "
                    + "--el KEY_SYSTEM_SIZE " + rawSize;
            if (cb != null) cb.onProgress(1000, "正在调起系统 DSU 安装器…");
            RootShell.Result r;
            if (ShizukuHelper.hasPermission()) r = ShizukuHelper.exec(cmd, 60);
            else r = RootShell.runRoot(cmd, 60);
            boolean started = r.code == 0 || r.out.contains("Starting:")
                    || r.out.contains("cmp=com.android.dynsystem")
                    || r.out.contains("act=android.os.image.action.START_INSTALL")
                    || r.out.contains("Warning: Activity not started");
            if (!started) {
                return new Result(false, "调起系统 DSU 安装器失败：" + r.out.trim(),
                        path, r.code, r.out);
            }
            return new Result(true, path, path, r.code, r.out);
        } catch (Throwable t) {
            try {
                if (uri != null) cr.delete(uri, null, null);
            } catch (Throwable ignored) {
            }
            return new Result(false, "原生安装失败：" + t.getMessage(), null, -3,
                    String.valueOf(t));
        }
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private DsuSideloadInstaller() {}
}
