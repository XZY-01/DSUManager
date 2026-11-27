package com.dsh.dsumanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** DSU Manager 2.0 应用设置 */
public class SettingsActivity extends Activity {

    private ThemeUtil.Palette P;
    private boolean dark;
    private LinearLayout content, slotBox;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private DsuManager.State state = new DsuManager.State();
    private boolean rootMode = false;
    private boolean shizukuMode = false;
    private boolean inDsu = false;
    private String activeSlot = "";
    private final ArrayList<String> slotOrder = new ArrayList<>();
    private boolean monetSnapshot;
    private int seedSnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        monetSnapshot = AppPrefs.monet(this);
        seedSnapshot = AppPrefs.seedColor(this);
        P = ThemeUtil.palette(this);
        dark = ThemeUtil.isDark(this);
        buildUi();
        loadSlotOrder();
        // 用缓存立即渲染槽位，避免进入设置后等待
        DsuManager.State cached = DsuManager.getCached();
        if (cached != null) {
            state = cached;
            activeSlot = cached.activeSlot;
            rootMode = cached.loadOk;
            renderSlots();
        }
        detectEnv();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppPrefs.monet(this) != monetSnapshot || AppPrefs.seedColor(this) != seedSnapshot) {
            recreate();
            return;
        }
        detectEnv();
    }

    // ---------------- UI 基础 ----------------

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radiusDp));
        d.setColor(color);
        return d;
    }

    private GradientDrawable roundedStroke(int color, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = rounded(color, radiusDp);
        d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private RippleDrawable rippleBg(int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(P.onSurface, 40)), rounded(color, radiusDp), null);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private TextView text(String s, float size, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private TextView button(String s, int fg, int bg, View.OnClickListener l) {
        TextView t = text(s, 13f, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rippleBg(bg, 100));
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout card(int bg) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(bg, 24));
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        return lp;
    }

    private View gap(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(d), dp(1)));
        return v;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(P.surface);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(40));
        sv.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30f, P.onSurface, true);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        head.addView(text("应用设置", 24f, P.onSurface, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(head);

        slotBox = new LinearLayout(this);
        slotBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(slotBox);

        content.addView(appearanceCard(), cardParams());
        content.addView(installCard(), cardParams());
        content.addView(experimentalCard(), cardParams());
        content.addView(aboutCard(), cardParams());

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
            content.setPadding(dp(20), top + dp(14), dp(20), dp(40));
            return insets;
        });
        setContentView(root);
    }

    // ---------------- 槽位管理 ----------------

    private void detectEnv() {
        io.execute(() -> {
            boolean su = RootShell.findSu() != null;
            boolean rootOk = false;
            if (su && !AppPrefs.noRootMode(this)) {
                RootShell.Result id = RootShell.runRoot("id", 20);
                rootOk = id.ok() && id.out.contains("uid=0");
            }
            boolean shizuku = false, shizukuPerm = false;
            if (!rootOk) {
                shizuku = ShizukuHelper.ping();
                shizukuPerm = shizuku && ShizukuHelper.hasPermission();
                if (shizuku && !shizukuPerm) ShizukuHelper.requestPermission(81);
            }
            final boolean fRoot = rootOk;
            final boolean fShizuku = shizukuPerm;
            final boolean fInDsu = EnvDetector.isDsu();
            DsuManager.State st;
            if (fRoot) st = DsuManager.load();
            else if (fShizuku) st = DsuManager.loadViaShizuku();
            else st = new DsuManager.State();
            final DsuManager.State fs = st;
            ui.post(() -> {
                rootMode = fRoot;
                shizukuMode = !fRoot && fShizuku;
                inDsu = fInDsu;
                state = fs;
                activeSlot = state.activeSlot;
                renderSlots();
            });
        });
    }

    private void loadSlotOrder() {
        slotOrder.clear();
        for (String n : AppPrefs.slotOrder(this).split(",")) {
            n = n.trim();
            if (!n.isEmpty()) slotOrder.add(n);
        }
    }

    private void saveSlotOrder() {
        StringBuilder sb = new StringBuilder();
        for (String n : slotOrder) {
            if (sb.length() > 0) sb.append(',');
            sb.append(n);
        }
        AppPrefs.setSlotOrder(this, sb.toString());
    }

    private ArrayList<DsuManager.Slot> orderedSlots() {
        ArrayList<DsuManager.Slot> list = new ArrayList<>(state.slots);
        final ArrayList<DsuManager.Slot> orig = new ArrayList<>(state.slots);
        Collections.sort(list, (a, b) -> {
            int ia = slotOrder.indexOf(a.name), ib = slotOrder.indexOf(b.name);
            if (ia < 0 && ib < 0) return orig.indexOf(a) - orig.indexOf(b);
            if (ia < 0) return 1;
            if (ib < 0) return -1;
            return ia - ib;
        });
        return list;
    }

    private void renderSlots() {
        slotBox.removeAllViews();
        LinearLayout c = card(P.surfaceContainer);
        c.addView(text("槽位管理", 16f, P.onSurface, true));
        TextView tip = text(rootMode
                ? "上移 / 下移调整卡片顺序（会保存）；重命名、调整 userdata 大小也在这里。"
                : (shizukuMode ? "Shizuku 只读模式：可查看槽位，管理操作需要 Root。"
                : "当前无 Root 权限，槽位管理不可用。"), 12f, P.onSurfaceVariant, false);
        tip.setPadding(0, dp(6), 0, dp(8));
        c.addView(tip);
        if ((rootMode || shizukuMode) && !state.slots.isEmpty()) {
            for (DsuManager.Slot s : orderedSlots()) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, dp(10), 0, dp(10));
                LinearLayout title = new LinearLayout(this);
                title.setGravity(Gravity.CENTER_VERTICAL);
                title.addView(text(s.name, 16f, P.onSurface, true),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if (s.name.equals(activeSlot) && inDsu) {
                    TextView chip = text("运行中", 11f, Color.WHITE, true);
                    chip.setBackground(rounded(P.success, 100));
                    chip.setPadding(dp(9), dp(3), dp(9), dp(3));
                    title.addView(chip);
                }
                row.addView(title);
                TextView meta = text("占用 " + DsuManager.fmtBytes(s.totalKB * 1024), 11f, P.onSurfaceVariant, false);
                meta.setPadding(0, dp(4), 0, dp(6));
                row.addView(meta);
                LinearLayout act = new LinearLayout(this);
                if (rootMode) {
                    act.addView(button("上移", P.primary, withAlpha(P.primary, 26), v -> moveSlot(s, -1)));
                    act.addView(gap(8));
                    act.addView(button("下移", P.primary, withAlpha(P.primary, 26), v -> moveSlot(s, 1)));
                    act.addView(gap(8));
                    act.addView(button("重命名", P.onSurface, withAlpha(P.onSurface, 22), v -> rename(s)));
                    act.addView(gap(8));
                    act.addView(button("调大小", P.onSecondaryContainer, P.secondaryContainer, v -> resize(s)));
                } else {
                    act.addView(text("只读", 11f, P.onSurfaceVariant, false));
                }
                row.addView(act);
                c.addView(row);
            }
        } else if (rootMode || shizukuMode) {
            c.addView(text("还没有 DSU 槽", 12f, P.onSurfaceVariant, false));
        }
        slotBox.addView(c, cardParams());
    }

    private void moveSlot(DsuManager.Slot s, int dir) {
        ArrayList<DsuManager.Slot> ordered = orderedSlots();
        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).name.equals(s.name)) { idx = i; break; }
        if (idx < 0) return;
        int to = idx + dir;
        if (to < 0 || to >= ordered.size()) { toast(dir < 0 ? "已经在最前面" : "已经在最后面"); return; }
        slotOrder.clear();
        for (DsuManager.Slot x : ordered) slotOrder.add(x.name);
        Collections.swap(slotOrder, idx, to);
        saveSlotOrder();
        renderSlots();
    }

    private void rename(DsuManager.Slot s) {
        if (inDsu && s.name.equals(activeSlot)) { toast("不能重命名正在运行的系统"); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(8));
        box.addView(text("重命名槽 " + s.name, 18f, P.onSurface, true));
        EditText in = new EditText(this);
        in.setText(s.name);
        in.setSingleLine(true);
        in.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        in.setTextColor(P.onSurface);
        in.setBackground(roundedStroke(P.surfaceVariant, 12, P.outline, 1));
        in.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.addView(in);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("重命名", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = in.getText().toString().trim();
            if (n.equals(s.name)) { dlg.dismiss(); return; }
            if (!n.matches("[A-Za-z0-9_]+")) { toast("槽名只能用字母/数字/下划线"); return; }
            dlg.dismiss();
            runOp("正在重命名", "移动镜像与元数据…", () -> {
                RootShell.Result r = DsuManager.renameSlot(s.name, n);
                if (!(r.ok() && r.out.contains("RENAMED_OK"))) throw new RuntimeException("重命名失败");
                int i = slotOrder.indexOf(s.name);
                if (i >= 0) { slotOrder.set(i, n); saveSlotOrder(); }
            });
        }));
        dlg.show();
    }

    private void resize(DsuManager.Slot s) {
        if (inDsu && s.name.equals(activeSlot)) { toast("不能调整正在运行的系统"); return; }
        io.execute(() -> {
            long[] u = DsuManager.slotUsage(s.name);
            ui.post(() -> {
                double gb = u[1] / 1024.0 / 1024.0 / 1024.0;
                int def = (int) Math.max(1, Math.min(256, Math.round(gb)));
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(24), dp(20), dp(24), dp(8));
                box.addView(text("调整数据大小", 18f, P.onSurface, true));
                TextView info = text("槽：" + s.name
                        + "\n当前 userdata：" + (gb < 0.1 ? "未知" : String.format(Locale.US, "%.1f GB", gb))
                        + "\n系统镜像：" + DsuManager.fmtBytes(u[0] + u[2])
                        + "\n\n会重建 userdata，清空该 DSU 里的数据；系统镜像保留。", 12f, P.onSurfaceVariant, false);
                info.setPadding(0, dp(8), 0, dp(12));
                box.addView(info);
                box.addView(text("新的 userdata 大小（GB）", 12f, P.onSurfaceVariant, false));
                EditText gbIn = new EditText(this);
                gbIn.setText(String.valueOf(def));
                gbIn.setInputType(InputType.TYPE_CLASS_NUMBER);
                gbIn.setSingleLine(true);
                gbIn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                gbIn.setTextColor(P.onSurface);
                gbIn.setBackground(roundedStroke(P.surfaceVariant, 12, P.outline, 1));
                gbIn.setPadding(dp(12), dp(8), dp(12), dp(8));
                box.addView(gbIn);
                AlertDialog dlg = new AlertDialog.Builder(this)
                        .setView(box)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("重建", (d, w) -> {
                            int n;
                            try { n = Integer.parseInt(gbIn.getText().toString().trim()); }
                            catch (Exception e) { n = def; }
                            if (n < 1) n = 1;
                            if (n > 256) n = 256;
                            final int fn = n;
                            runOp("正在重建 userdata", "复制 system 镜像并重建 userdata…", () -> {
                                RootShell.Result r = DsuManager.resizeUserdata(s.name,
                                        (long) fn * 1024L * 1024L * 1024L);
                                if (!(r.out.contains("RESIZE_OK") || r.ok()))
                                    throw new RuntimeException("调整失败");
                            });
                        })
                        .create();
                dlg.show();
            });
        });
    }

    private void runOp(String title, String working, Runnable task) {
        ProgressBar pb = new ProgressBar(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(20));
        box.addView(text(title, 16f, P.onSurface, true));
        TextView wt = text(working, 13f, P.onSurfaceVariant, false);
        wt.setPadding(0, dp(6), 0, dp(14));
        box.addView(wt);
        box.addView(pb);
        AlertDialog dlg = new AlertDialog.Builder(this).setView(box).setCancelable(false).create();
        dlg.show();
        io.execute(() -> {
            String err = null;
            try { task.run(); } catch (Throwable t) { err = String.valueOf(t.getMessage()); }
            final String fe = err;
            ui.post(() -> {
                try { dlg.dismiss(); } catch (Exception ignored) { }
                if (fe != null) toast("失败："+ fe);
                detectEnv();
            });
        });
    }

    // ---------------- 设置卡片 ----------------

    private static final String[] COLOR_NAMES = {
            "跟随系统（莫奈取色）", "默认紫色", "蓝色", "绿色", "青色",
            "橙色", "红色", "粉色"
    };
    private static final int[] COLOR_VALUES = {
            0, 0, 0xFF1565C0, 0xFF2E7D32, 0xFF00838F,
            0xFFEF6C00, 0xFFC62828, 0xFFAD1457
    };

    private String themeSummary() {
        if (AppPrefs.seedColor(this) == 0) {
            return AppPrefs.monet(this) && ThemeUtil.isDynamicSupported(this)
                    ? "跟随系统（莫奈）" : "默认紫色";
        }
        int seed = AppPrefs.seedColor(this);
        for (int i = 2; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i] == seed) return COLOR_NAMES[i];
        }
        return "自定义颜色";
    }

    private LinearLayout appearanceCard() {
        LinearLayout c = card(P.surfaceContainer);
        c.addView(text("外观", 16f, P.onSurface, true));
        c.addView(settingRow("主题色", themeSummary(), v -> showThemeDialog()));
        return c;
    }

    private LinearLayout installCard() {
        LinearLayout c = card(P.surfaceContainer);
        c.addView(text("安装", 16f, P.onSurface, true));
        c.addView(switchRow("显示安装细节", "安装时实时显示详细日志",
                AppPrefs.showInstallDetails(this), v -> AppPrefs.setShowInstallDetails(this, v)));
        c.addView(settingRow("安装域",
                "native".equals(AppPrefs.installDomain(this)) ? "原生安装（免 Root）" : "软件安装（需 Root）",
                v -> showDomainDialog()));
        return c;
    }

    private LinearLayout experimentalCard() {
        LinearLayout c = card(P.surfaceContainer);
        c.addView(text("实验性功能", 16f, P.onSurface, true));
        c.addView(switchRow("无 Root 模式（实验）", "仅识别环境与只读查看；安装需要 Root",
                AppPrefs.noRootMode(this), v -> {
                    AppPrefs.setNoRootMode(this, v);
                    toast(v ? "已开启无 Root 模式" : "已关闭无 Root 模式");
                    detectEnv();
                }));
        TextView hint = text(EnvDetector.hasShizuku(this)
                ? "已集成 Shizuku SDK：无 Root 时授权后可只读查看槽位，并可把安装域设为「原生安装」免 Root 安装单个 system.img；启停/删除仍需 Root。"
                : "安装 Shizuku 并授权后，无 Root 也能只读查看槽位，并用「原生安装」免 Root 安装单个 system.img；启停/删除仍需 Root。",
                11f, P.onSurfaceVariant, false);
        hint.setPadding(dp(4), dp(6), dp(4), 0);
        c.addView(hint);
        return c;
    }

    private LinearLayout aboutCard() {
        LinearLayout c = card(P.surfaceContainer);
        c.addView(text("关于", 16f, P.onSurface, true));
        c.addView(text("DSU Manager v2.4", 12f, P.onSurfaceVariant, false));
        c.addView(settingRow("更新日志", "1.0 → 2.4", v -> showChangelog()));
        c.addView(settingRow("GitHub 仓库", "github.com/XZY-01/DSUManager",
                v -> openUrl("https://github.com/XZY-01/DSUManager")));
        c.addView(devRow("XZY", "酷安主页", "https://www.coolapk.com/u/28096321", R.drawable.dev_xzy));
        c.addView(devRow("大肥鱼", "DeepSeek 官网", "https://www.deepseek.com/", R.drawable.dev_dfy));
        return c;
    }

    private View settingRow(String title, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(12), dp(4), dp(12));
        row.setClickable(true);
        row.setBackground(rippleBg(Color.TRANSPARENT, 18));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 14f, P.onSurface, true));
        col.addView(text(value, 11f, P.primary, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 18f, P.onSurfaceVariant, false));
        row.setOnClickListener(click);
        return row;
    }

    private View devRow(String name, String desc, String url, int avatarRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setClickable(true);
        row.setBackground(rippleBg(Color.TRANSPARENT, 18));

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(circleCrop(avatarRes));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(2), P.primary);
        ring.setColor(Color.TRANSPARENT);
        iv.setBackground(ring);
        iv.setPadding(dp(3), dp(3), dp(3), dp(3));
        row.addView(iv, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        col.addView(text(name, 14f, P.onSurface, true));
        col.addView(text(desc, 11f, P.onSurfaceVariant, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 18f, P.onSurfaceVariant, false));
        row.setOnClickListener(v -> openUrl(url));
        return row;
    }

    private Bitmap circleCrop(int resId) {
        Bitmap src = BitmapFactory.decodeResource(getResources(), resId);
        if (src == null) return null;
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        float scale = (float) size / Math.min(src.getWidth(), src.getHeight());
        m.setScale(scale, scale);
        m.postTranslate((size - src.getWidth() * scale) / 2f, (size - src.getHeight() * scale) / 2f);
        shader.setLocalMatrix(m);
        paint.setShader(shader);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return out;
    }

    private View switchRow(String title, String desc, boolean checked, SwitchCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setBackground(rippleBg(Color.TRANSPARENT, 18));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 14f, P.onSurface, true));
        col.addView(text(desc, 11f, P.onSurfaceVariant, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Toggle toggle = new Toggle(P);
        toggle.setChecked(checked);
        toggle.setClickable(false);
        toggle.setFocusable(false);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(52), dp(32)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            boolean now = !toggle.checked;
            toggle.setChecked(now);
            cb.onChanged(now);
        });
        return row;
    }

    private interface SwitchCallback {
        void onChanged(boolean checked);
    }

    private class Toggle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean checked;
        private final int on, off, thumbOn, thumbOff;

        Toggle(ThemeUtil.Palette p) {
            super(SettingsActivity.this);
            on = p.primary;
            off = p.surfaceVariant;
            thumbOn = p.onPrimary;
            thumbOff = p.onSurfaceVariant;
        }

        void setChecked(boolean v) {
            checked = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(checked ? on : off);
            canvas.drawRoundRect(0, 0, w, h, r, r, t);
            Paint th = new Paint(Paint.ANTI_ALIAS_FLAG);
            th.setColor(checked ? thumbOn : thumbOff);
            canvas.drawCircle(checked ? w - r : r, h / 2f, r - dp(3), th);
        }
    }

    private void showThemeDialog() {
        int current;
        if (AppPrefs.seedColor(this) != 0) current = 2;
        else current = (AppPrefs.monet(this) && ThemeUtil.isDynamicSupported(this)) ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle("更改主题色")
                .setSingleChoiceItems(COLOR_NAMES, -1, (d, which) -> {
                    if (which == 0) {
                        AppPrefs.setMonet(this, true);
                        AppPrefs.setSeedColor(this, 0);
                    } else if (which == 1) {
                        AppPrefs.setMonet(this, false);
                        AppPrefs.setSeedColor(this, 0);
                    } else {
                        AppPrefs.setMonet(this, false);
                        AppPrefs.setSeedColor(this, COLOR_VALUES[which]);
                    }
                    d.dismiss();
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDomainDialog() {
        String[] items = {
                "软件安装（super / 整包多分区，需要 Root）",
                "原生安装（DSU Sideloader 方式，Shizuku 免 Root，仅单 system.img）"
        };
        int cur = "native".equals(AppPrefs.installDomain(this)) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("安装")
                .setSingleChoiceItems(items, cur, (d, which) -> {
                    AppPrefs.setInstallDomain(this, which == 1 ? "native" : "software");
                    d.dismiss();
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showChangelog() {
        String text = "DSU Manager 更新日志\n\n"
                + "2.4（2026-11）\n"
                + "· 新增 DSU 备份：一键备份当前活动槽（含 userdata）到 Download/DSUManager/backups\n"
                + "· 新增备份恢复：可覆盖恢复到同名槽并直接启用重启\n"
                + "· 备份支持进度显示与取消，稀疏镜像按稀疏复制\n"
                + "\n"
                + "2.3.1（2026-11）\n"
                + "· DSU 状态卡在 DSU 中恢复「重启到主系统」按钮\n"
                + "· DSU 中槽占用空间不再误显示 0B，改为提示主系统数据不可读\n"
                + "\n"
                + "2.3（2026-11）\n"
                + "· 修复 DSU 环境中主系统被误显示为「运行中」（兼容 true 布尔属性）\n"
                + "· 设置页新增 GitHub 仓库地址跳转\n"
                + "\n"
                + "2.2（2026-11）\n"
                + "· 2.1.12 合并为 2.2 正式版\n"
                + "· 主系统卡片新增重启按钮：主系统中正常重启，DSU 中重启到主系统\n"
                + "\n"
                + "2.1.11（2026-11）\n"
                + "· 移除应用内置的指纹/面容备份（改为独立的 KernelSU 模块 dsu_bio_guard）\n"
                + "\n"
                + "2.1.10（2026-11）\n"
                + "· DSU 状态卡恢复「重启到 DSU」按钮：可直接选槽、启用并重启\n\n"
                + "2.1.9（2026-11）\n"
                + "· 新增「生物识别保护」：重启 DSU 前自动备份主系统指纹/面容模板与 persist 校准\n"
                + "· 回到主系统后若检测到指纹/面容数据缺失，可用备份恢复并重启\n"
                + "· 修复从澎湃 DSU 返回主系统后指纹/面容丢失的问题\n\n"
                + "2.1.8（2026-11）\n"
                + "· 修复 zip/tar 只能识别 system 分区：现在支持多分区镜像（system/vendor/product…）\n"
                + "· 压缩包内含 super.img 时自动按 super 安装；含多个分区镜像时逐分区安装\n\n"
                + "2.1.7（2026-11）\n"
                + "· 修复「无论选哪种安装方式都走原生安装」：软件安装与原生安装恢复各自动作\n"
                + "· 软件安装失败时提供「改用原生安装」重试按钮（单个 system.img）\n\n"
                + "2.1.6（2026-11）\n"
                + "· 修复单个 system.img 软件安装失败：自动改用系统 DSU 安装器\n"
                + "· 软件安装仅用于 super.img / 整包多分区；无 Root 也可安装单个 system.img\n\n"
                + "2.1.5（2026-11）\n"
                + "· 安装成功后自动清理安装缓存（原生安装与软件安装均适用）\n\n"
                + "2.1.4（2026-11）\n"
                + "· 修复更改主题色后自动安装测试镜像的问题（移除隐藏测试入口）\n\n"
                + "2.1.3（2026-11）\n"
                + "· 原生安装弹窗移除“无需 Root / Shizuku 授权”说明文字\n\n"
                + "2.1.2（2026-11）\n"
                + "· 原生安装成功弹窗不再显示压缩包保存路径\n\n"
                + "2.1.1（2026-11）\n"
                + "· 原生安装完成后自动删除 Download/DSUManager 下的临时 .img.gz 压缩包\n\n"
                + "2.1.0（2026-11）\n"
                + "· 原生安装改为 DSU Sideloader 方式：压缩为 gz 后调起系统 DSU 安装器\n"
                + "· 原生安装无需 Root：Shizuku 授权即可安装单个 system.img\n"
                + "· 软件安装继续使用 gsi_tool（需 Root）\n\n"
                + "2.0.4（2026-11）\n"
                + "· 修复设置页开关点击不灵敏（整行单击即可切换）\n"
                + "· 收紧主页 DSU 槽四个按钮的间距\n\n"
                + "2.0.3（2026-11）\n"
                + "· 精简安装成功弹窗提示文字\n\n"
                + "2.0.2（2026-11）\n"
                + "· 安装日志不再自动写入应用目录，改为安装完成后手动通过 SAF 保存\n"
                + "· 更新日志入口移入「关于」选项卡\n\n"
                + "2.0.1（2026-11）\n"
                + "· 引入 Shizuku SDK：无 Root 时可通过 Shizuku 只读查看槽位\n"
                + "· 开发者头像恢复显示\n"
                + "· 安装完成后可通过 SAF 保存安装日志\n"
                + "· 当前系统显示系统名（HyperOS / ColorOS / Android）\n"
                + "· 修复设置页槽位管理延迟显示\n\n"
                + "2.0（2026-11）\n"
                + "· 更名 DSU Manager\n"
                + "· MD3 风格，支持动态取色与自定义主题色\n"
                + "· 安装格式识别：system.img / super.img / zip / tar / gzip / sparse（zst/xz 需系统命令）\n"
                + "· super 与 system 安装入口合并，保留整包文件夹安装\n"
                + "· 完整安装日志 + 成功/失败弹窗 + 失败原因 + 日志保存\n"
                + "· 安装细节开关、安装域（软件/原生）\n"
                + "· 无 Root 只读模式（实验）\n"
                + "· 修复环境识别与主系统状态误判\n"
                + "· 兼容 Android 11-17\n\n"
                + "1.9.2（2026-11）\n· 设置页与主界面缓存秒开，排序立即生效\n\n"
                + "1.9（2026-11）\n· 右上角三点菜单；开发者头像\n\n"
                + "1.8（2026-11）\n· 应用设置页；开发者署名；动态取色\n\n"
                + "1.7（2026-11）\n· 独立设置页；修复重命名交互\n\n"
                + "1.6（2026-11）\n· 槽位排序\n\n"
                + "1.5（2026-11）\n· 调整 userdata 大小\n\n"
                + "1.4（2026-11）\n· super.img 自动拆分\n\n"
                + "1.3（2026-11）\n· 整包 ROM 多分区安装\n\n"
                + "1.2（2026-11）\n· 安装稳定性修复、图标\n\n"
                + "1.0（2026-11）\n· 初版多 DSU 槽管理";;
        new AlertDialog.Builder(this)
                .setTitle("更新日志")
                .setMessage(text)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            toast("没有可打开链接的应用");
        }
    }

    private void toast(String s) {
        ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }
}

