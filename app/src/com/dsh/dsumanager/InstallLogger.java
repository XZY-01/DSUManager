package com.dsh.dsumanager;

import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** 安装日志：仅保存在内存中，安装结束由用户手动通过 SAF 导出，不自动写入应用目录。 */
public final class InstallLogger {

    private final ArrayList<String> lines = new ArrayList<>();
    private final String fileName;
    private final SimpleDateFormat ts = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public InstallLogger(Context c, String tag) {
        fileName = "DSUManager-install-" + tag + "-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".log";
    }

    public synchronized void add(String s) {
        lines.add(ts.format(new Date()) + "  " + s);
    }

    public synchronized void add(String s, Throwable t) {
        add(s);
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        for (String l : sw.toString().split("\n")) add("  " + l);
    }

    public synchronized String text() {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    public String fileName() {
        return fileName;
    }
}
