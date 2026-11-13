package com.dsh.dsumanager;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** Material You：动态取色 / 自定义主题色 / 默认 M3 配色。 */
public final class ThemeUtil {

    public static final class Palette {
        public int primary, onPrimary, primaryContainer, onPrimaryContainer;
        public int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
        public int surface, surfaceContainer, surfaceContainerHigh, onSurface;
        public int surfaceVariant, onSurfaceVariant, outline, outlineVariant;
        public int error, errorContainer, onErrorContainer, success;
    }

    public static boolean isDark(Context c) {
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isDynamicSupported(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 31) return false;
        try {
            c.getColor(android.R.color.system_accent1_500);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 是否启用“跟随系统莫奈取色”。 */
    public static boolean monetEnabled(Context c) {
        return AppPrefs.monet(c) && AppPrefs.seedColor(c) == 0 && isDynamicSupported(c);
    }

    public static Palette palette(Context c) {
        boolean dark = isDark(c);
        int seed = AppPrefs.seedColor(c);
        if (monetEnabled(c)) {
            return dynamic(c, dark);
        }
        if (seed != 0) {
            return custom(seed, dark);
        }
        return baseline(dark);
    }

    // ---------------- 动态取色 ----------------

    private static Palette dynamic(Context c, boolean dark) {
        Palette p = new Palette();
        int accent1_100 = sys(c, android.R.color.system_accent1_100, 0xFFEADDFF);
        int accent1_200 = sys(c, android.R.color.system_accent1_200, 0xFFD0BCFF);
        int accent1_500 = sys(c, android.R.color.system_accent1_500, 0xFF6750A4);
        int accent1_900 = sys(c, android.R.color.system_accent1_900, 0xFF21005D);
        int accent2_100 = sys(c, android.R.color.system_accent2_100, 0xFFE8DEF8);
        int accent2_200 = sys(c, android.R.color.system_accent2_200, 0xFFCCC2DC);
        int accent2_500 = sys(c, android.R.color.system_accent2_500, 0xFF625B71);
        int accent2_900 = sys(c, android.R.color.system_accent2_900, 0xFF1D192B);
        int neutral1_50 = sys(c, android.R.color.system_neutral1_50, 0xFFFEF7FF);
        int neutral1_100 = sys(c, android.R.color.system_neutral1_100, 0xFFF3EDF7);
        int neutral1_900 = sys(c, android.R.color.system_neutral1_900, 0xFF1D1B20);
        int neutral2_100 = sys(c, android.R.color.system_neutral2_100, 0xFFE7E0EC);
        int neutral2_200 = sys(c, android.R.color.system_neutral2_200, 0xFFD5CEE0);
        int neutral2_700 = sys(c, android.R.color.system_neutral2_700, 0xFF49454F);

        if (dark) {
            p.primary = accent1_200;
            p.onPrimary = accent1_900;
            p.primaryContainer = 0xFF4F378B;
            p.onPrimaryContainer = 0xFFEADDFF;
            p.secondary = accent2_200;
            p.onSecondary = accent2_900;
            p.secondaryContainer = 0xFF4A4458;
            p.onSecondaryContainer = 0xFFE8DEF8;
            p.surface = neutral1_900;
            p.surfaceContainer = 0xFF211F26;
            p.surfaceContainerHigh = 0xFF2B2930;
            p.onSurface = 0xFFE6E0E9;
            p.surfaceVariant = 0xFF49454F;
            p.onSurfaceVariant = 0xFFCAC4D0;
            p.outline = 0xFF938F99;
            p.outlineVariant = 0xFF49454F;
            p.error = 0xFFF2B8B5;
            p.errorContainer = 0xFF8C1D18;
            p.onErrorContainer = 0xFFF9DEDC;
            p.success = 0xFF9DD5A7;
        } else {
            p.primary = accent1_500;
            p.onPrimary = 0xFFFFFFFF;
            p.primaryContainer = accent1_100;
            p.onPrimaryContainer = accent1_900;
            p.secondary = accent2_500;
            p.onSecondary = 0xFFFFFFFF;
            p.secondaryContainer = accent2_100;
            p.onSecondaryContainer = accent2_900;
            p.surface = neutral1_50;
            p.surfaceContainer = neutral1_100;
            p.surfaceContainerHigh = 0xFFECE6F0;
            p.onSurface = neutral1_900;
            p.surfaceVariant = neutral2_100;
            p.onSurfaceVariant = neutral2_700;
            p.outline = 0xFF79747E;
            p.outlineVariant = neutral2_200;
            p.error = 0xFFB3261E;
            p.errorContainer = 0xFFF9DEDC;
            p.onErrorContainer = 0xFF410E0B;
            p.success = 0xFF2E7D32;
        }
        return p;
    }

    // ---------------- 自定义种子色 ----------------

    private static Palette custom(int seed, boolean dark) {
        Palette p = new Palette();
        int baseSurface = dark ? 0xFF141218 : 0xFFFEF7FF;
        int onSurface = dark ? 0xFFE6E0E9 : 0xFF1D1B20;
        int onSurfaceVariant = dark ? 0xFFCAC4D0 : 0xFF49454F;

        p.primary = dark ? shade(seed, 0.45f) : seed;
        p.onPrimary = onColor(p.primary);
        p.primaryContainer = mix(baseSurface, seed, dark ? 0.42f : 0.20f);
        p.onPrimaryContainer = onColor(p.primaryContainer);
        p.secondary = dark ? shade(shiftHue(seed, 24), 0.35f) : shiftHue(seed, 24);
        p.onSecondary = onColor(p.secondary);
        p.secondaryContainer = mix(baseSurface, p.secondary, dark ? 0.38f : 0.16f);
        p.onSecondaryContainer = onColor(p.secondaryContainer);

        p.surface = mix(baseSurface, seed, dark ? 0.06f : 0.03f);
        p.surfaceContainer = mix(baseSurface, seed, dark ? 0.10f : 0.07f);
        p.surfaceContainerHigh = mix(baseSurface, seed, dark ? 0.14f : 0.11f);
        p.onSurface = onSurface;
        p.surfaceVariant = mix(baseSurface, seed, dark ? 0.20f : 0.14f);
        p.onSurfaceVariant = onSurfaceVariant;
        p.outline = dark ? 0xFF938F99 : 0xFF79747E;
        p.outlineVariant = mix(baseSurface, seed, dark ? 0.28f : 0.22f);
        p.error = dark ? 0xFFF2B8B5 : 0xFFB3261E;
        p.errorContainer = dark ? 0xFF8C1D18 : 0xFFF9DEDC;
        p.onErrorContainer = dark ? 0xFFF9DEDC : 0xFF410E0B;
        p.success = dark ? 0xFF9DD5A7 : 0xFF2E7D32;
        return p;
    }

    private static Palette baseline(boolean dark) {
        return custom(0xFF6750A4, dark);
    }

    // ---------------- 颜色工具 ----------------

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (Color.red(a) * (1 - t) + Color.red(b) * t);
        int g = (int) (Color.green(a) * (1 - t) + Color.green(b) * t);
        int bl = (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return Color.rgb(r, g, bl);
    }

    public static int shade(int color, float amount) {
        if (amount >= 0) return mix(color, 0xFFFFFFFF, amount);
        return mix(color, 0xFF000000, -amount);
    }

    public static int onColor(int bg) {
        return Color.luminance(bg) > 0.45f ? 0xFF000000 : 0xFFFFFFFF;
    }

    public static int shiftHue(int color, float deg) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + deg) % 360f;
        return Color.HSVToColor(hsv);
    }

    private static int sys(Context c, int resId, int fallback) {
        try {
            return c.getColor(resId);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private ThemeUtil() {}
}
