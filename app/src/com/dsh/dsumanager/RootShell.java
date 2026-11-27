package com.dsh.dsumanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** 通过 su 执行命令（主系统 KSU），或直接执行（DSU 内的只读兜底）。 */
public final class RootShell {

    public static final class Result {
        public final int code;
        public final String out;
        public Result(int code, String out) { this.code = code; this.out = out; }
        public boolean ok() { return code == 0; }
        public String firstLine() {
            for (String l : out.split("\n")) {
                l = l.trim();
                if (!l.isEmpty()) return l;
            }
            return "";
        }
    }

    private static final String[] SU_PATHS = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su"};

    public static String findSu() {
        for (String p : SU_PATHS) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    public static boolean isRootAvailable() {
        return findSu() != null;
    }

    public static Result runRoot(String cmd, int timeoutSec) {
        String su = findSu();
        if (su == null) return new Result(-1, "no su binary");
        return exec(new String[]{su, "-c", cmd}, timeoutSec);
    }

    public interface StreamListener {
        void onProgress(long written, long total);
    }

    /**
     * 执行 su 命令，并把数据通过 stdin 管道喂给它。
     * 关键点：gsi_tool install 需要从 stdin 读镜像；用管道传数据，
     * 避免 gsid 读 app 私有目录里的文件被 SELinux 拒绝（EPERM）。
     */
    public static Result runRootWithStream(String cmd, java.io.InputStream data, long total,
                                           int timeoutSec, StreamListener listener) {
        String su = findSu();
        if (su == null) return new Result(-1, "no su binary");
        final StringBuilder sb = new StringBuilder();
        Process p = null;
        try {
            p = new ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start();
            final Process fp = p;
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(fp.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                } catch (Exception ignored) { }
            });
            reader.setDaemon(true);
            reader.start();

            java.io.OutputStream os = p.getOutputStream();
            byte[] buf = new byte[256 * 1024];
            long written = 0;
            try {
                int n;
                while ((n = data.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    os.flush();
                    written += n;
                    if (listener != null) listener.onProgress(written, total);
                    if (!p.isAlive()) break;
                }
            } catch (java.io.IOException io) {
                // gsi_tool 提前退出（例如参数错误）时管道会断开，这里保留输出用于报错
            } finally {
                try { os.close(); } catch (Exception ignored) { }
            }

            boolean finished = p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new Result(-2, "timeout after " + timeoutSec + "s\n" + sb);
            }
            reader.join(2000);
            return new Result(p.exitValue(), sb.toString());
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            return new Result(-3, e.getMessage() + "\n" + sb);
        }
    }

    public static Result runDirect(String[] argv, int timeoutSec) {
        return exec(argv, timeoutSec);
    }

    public interface LineListener {
        void onLine(String line);
    }

    /** 执行 su 命令并逐行回调输出（备份/恢复进度用）。 */
    public static Result runRootLines(String cmd, int timeoutSec, LineListener listener) {
        String su = findSu();
        if (su == null) return new Result(-1, "no su binary");
        final StringBuilder sb = new StringBuilder();
        Process p = null;
        try {
            p = new ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start();
            final Process fp = p;
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(fp.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line).append('\n');
                        if (listener != null) listener.onLine(line);
                    }
                } catch (Exception ignored) { }
            });
            reader.setDaemon(true);
            reader.start();
            boolean finished = p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new Result(-2, "timeout after " + timeoutSec + "s\n" + sb);
            }
            reader.join(2000);
            return new Result(p.exitValue(), sb.toString());
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            return new Result(-3, e.getMessage() + "\n" + sb);
        }
    }

    /** 通过 Shizuku 执行 shell（无 Root 模式）。 */
    public static Result runShizuku(String cmd, int timeoutSec) {
        return ShizukuHelper.exec(cmd, timeoutSec);
    }

    private static Result exec(String[] argv, int timeoutSec) {
        final StringBuilder sb = new StringBuilder();
        Process p = null;
        try {
            p = new ProcessBuilder(argv).redirectErrorStream(true).start();
            final Process fp = p;
            final StringBuilder buf = sb;
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(fp.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) buf.append(line).append('\n');
                } catch (Exception ignored) { }
            });
            reader.setDaemon(true);
            reader.start();
            boolean finished = p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new Result(-2, "timeout after " + timeoutSec + "s\n" + sb);
            }
            reader.join(2000);
            return new Result(p.exitValue(), sb.toString());
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            return new Result(-3, e.getMessage() + "\n" + sb);
        }
    }

    private RootShell() {}
}
