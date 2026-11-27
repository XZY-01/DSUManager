package com.dsh.dsumanager;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 运行环境识别（2.0 修复 Bug 13/14）。
 * 原则：是否 DSU 与是否有 root 完全独立，绝不能用“没有 su”推断“在 DSU 内”。
 */
public final class EnvDetector {

    public static boolean isDsu() {
        if (isTrueProp("ro.gsid.image_running")) return true;
        if (isTrueProp("ro.gsi.dsu")) return true;
        if (dmNamesContainGsi()) return true;
        return mountInfoLooksLikeDsu();
    }

    /** 兼容 "1" / "true" / "yes" / "on" 等布尔属性写法。 */
    private static boolean isTrueProp(String name) {
        String v = getprop(name);
        if (v == null) return false;
        v = v.trim();
        return "1".equals(v)
                || "true".equalsIgnoreCase(v)
                || "yes".equalsIgnoreCase(v)
                || "on".equalsIgnoreCase(v);
    }

    public static String getprop(String name) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Object v = c.getMethod("get", String.class).invoke(null, name);
            return v == null ? "" : v.toString();
        } catch (Throwable ignored) {
        }
        try {
            Process p = new ProcessBuilder("getprop", name).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            return line == null ? "" : line.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean dmNamesContainGsi() {
        try {
            File[] blocks = new File("/sys/block").listFiles();
            if (blocks == null) return false;
            for (File b : blocks) {
                if (!b.getName().startsWith("dm-")) continue;
                try {
                    String name = new String(readAll(new File(b, "dm/name"))).trim();
                    if (name.contains("_gsi")) return true;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean mountInfoLooksLikeDsu() {
        try {
            String info = new String(readAll(new File("/proc/self/mountinfo")));
            for (String line : info.split("\n")) {
                if (!(line.contains(" /system ") || line.contains(" /vendor "))) continue;
                if (line.contains("/data/gsi") || line.contains("_gsi")) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static byte[] readAll(File f) throws Exception {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /** 系统显示名（HyperOS / ColorOS / LineageOS / MikuUI / Android）。 */
    public static String systemDisplayName(Context c) {
        String hyper = getprop("ro.mi.os.version.name");
        if (!hyper.isEmpty()) {
            if (hyper.startsWith("HyperOS")) return hyper;
            if (hyper.startsWith("OS")) return "HyperOS " + hyper.substring(2).trim();
            return "HyperOS " + hyper;
        }
        String oplus = getprop("ro.build.version.oplusrom");
        if (oplus.isEmpty()) oplus = getprop("ro.build.version.opporom");
        if (oplus.isEmpty()) oplus = getprop("ro.oxygen.version");
        if (!oplus.isEmpty()) {
            String v = oplus.startsWith("V") ? oplus.substring(1) : oplus;
            return "ColorOS " + v;
        }
        String lineage = getprop("ro.lineage.version");
        if (lineage.isEmpty()) lineage = getprop("ro.modversion");
        if (!lineage.isEmpty()) return "LineageOS " + lineage;
        String miku = getprop("ro.miku.version");
        if (!miku.isEmpty()) return "MikuUI " + miku;
        String realme = getprop("ro.build.version.realmeui");
        if (!realme.isEmpty()) return "realme UI " + realme;
        return "Android " + Build.VERSION.RELEASE;
    }

    /** 主系统名称：统一显示系统名（不再用设备型号）。 */
    public static String mainSystemName(Context c) {
        return systemDisplayName(c);
    }

    /** 当前运行系统名（用于标题栏副标题）。 */
    public static String runningSystemLabel(Context c, String activeSlot) {
        if (isDsu()) {
            return activeSlot == null || activeSlot.isEmpty()
                    ? "DSU（运行中）" : "DSU · " + activeSlot;
        }
        return mainSystemName(c);
    }

    /** 粗略检测 Shizuku / ADB 环境（实验性无 Root 模式提示用）。 */
    public static boolean hasShizuku(Context c) {
        try {
            c.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private EnvDetector() {}
}
