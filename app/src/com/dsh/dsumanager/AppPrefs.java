package com.dsh.dsumanager;

import android.content.Context;
import android.content.SharedPreferences;

/** 2.0 应用设置（SharedPreferences 统一入口）。 */
public final class AppPrefs {

    private static final String NAME = "dsu_prefs";

    public static SharedPreferences p(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // 安装细节
    public static boolean showInstallDetails(Context c) {
        return p(c).getBoolean("install_details", true);
    }

    public static void setShowInstallDetails(Context c, boolean v) {
        p(c).edit().putBoolean("install_details", v).apply();
    }

    // 安装域：software / native
    public static String installDomain(Context c) {
        return p(c).getString("install_domain", "software");
    }

    public static void setInstallDomain(Context c, String v) {
        p(c).edit().putString("install_domain", v).apply();
    }

    // 主系统名称：disabled / device
    public static String mainNameMode(Context c) {
        return p(c).getString("main_name_mode", "disabled");
    }

    public static void setMainNameMode(Context c, String v) {
        p(c).edit().putString("main_name_mode", v).apply();
    }

    // 无 Root 模式（实验）
    public static boolean noRootMode(Context c) {
        return p(c).getBoolean("no_root_mode", false);
    }

    public static void setNoRootMode(Context c, boolean v) {
        p(c).edit().putBoolean("no_root_mode", v).apply();
    }

    // 主题：monet 跟随系统；seed 自定义颜色（0=默认）
    public static boolean monet(Context c) {
        return p(c).getBoolean("monet", true);
    }

    public static void setMonet(Context c, boolean v) {
        p(c).edit().putBoolean("monet", v).apply();
    }

    public static int seedColor(Context c) {
        return p(c).getInt("seed_color", 0);
    }

    public static void setSeedColor(Context c, int color) {
        p(c).edit().putInt("seed_color", color).apply();
    }

    // 槽位排序
    public static String slotOrder(Context c) {
        return p(c).getString("slot_order", "");
    }

    public static void setSlotOrder(Context c, String order) {
        p(c).edit().putString("slot_order", order).apply();
    }

    private AppPrefs() {}
}
