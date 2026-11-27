package com.dsh.dsumanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** DSU Manager 2.0 主页 */
public class MainActivity extends Activity {

    private static final int REQ_IMAGE = 71;
    private static final int REQ_TREE = 72;
    private static final int REQ_SAVE_LOG = 73;
    private static final int REQ_SHIZUKU = 81;

    private ThemeUtil.Palette P;
    private boolean dark;
    private FrameLayout rootFrame;
    private LinearLayout content, bodyBox;
    private TextView fab, subtitleView;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private DsuManager.State state = new DsuManager.State();
    private boolean rootMode = false;
    private boolean rootAvailable = false;
    private boolean inDsu = false;
    private boolean noRootMode = false;
    private boolean shizukuMode = false;
    private String activeSlot = "";
    private final ArrayList<String> slotOrder = new ArrayList<>();
    private boolean monetSnapshot;
    private int seedSnapshot;
    private File imageCache;
    private String pendingLogText;
    private String pendingLogName;
    private Shizuku.OnRequestPermissionResultListener permissionListener;

    // 待安装数据
    private ImageFormatDetector.Plan pendingPlan;
    private final ArrayList<PartInput> pendingFolder = new ArrayList<>();
    private boolean forceNativeOnce = false;

    private static class PartInput {
        String name, display;
        Uri uri;
        long size;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        monetSnapshot = AppPrefs.monet(this);
        seedSnapshot = AppPrefs.seedColor(this);
        P = ThemeUtil.palette(this);
        dark = ThemeUtil.isDark(this);
        imageCache = new File(getCacheDir(), "images");
        buildUi();
        loadSlotOrder();
        permissionListener = (requestCode, grantResult) -> {
            if (grantResult == PackageManager.PERMISSION_GRANTED) detectEnvAndRefresh();
        };
        ShizukuHelper.addPermissionListener(permissionListener);
        ShizukuHelper.addBinderListener(() -> ui.post(this::detectEnvAndRefresh));
        detectEnvAndRefresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (permissionListener != null) ShizukuHelper.removePermissionListener(permissionListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppPrefs.monet(this) != monetSnapshot || AppPrefs.seedColor(this) != seedSnapshot) {
            recreate();
            return;
        }
        loadSlotOrder();
        detectEnvAndRefresh();
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

    private TextView chip(String s, int bg, int fg) {
        TextView t = text(s, 11f, fg, true);
        t.setBackground(rounded(bg, 100));
        t.setPadding(dp(9), dp(3), dp(9), dp(3));
        return t;
    }

    private TextView actionButton(String s, int fg, int bg, View.OnClickListener l) {
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

    private void buildUi() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(P.surface);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(120));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("DSU Manager", 28f, P.onSurface, true));
        subtitleView = text("正在检测环境…", 13f, P.onSurfaceVariant, false);
        subtitleView.setPadding(0, dp(2), 0, 0);
        titleBox.addView(subtitleView);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        DotsButton dots = new DotsButton(P);
        dots.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(dots, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(top);

        bodyBox = new LinearLayout(this);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(bodyBox);

        fab = text("＋", 30f, P.onPrimaryContainer, false);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(rounded(P.primaryContainer, 100));
        fab.setElevation(dp(6));
        fab.setClickable(true);
        fab.setOnClickListener(v -> showInstallChooser());
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(60), dp(60));
        fabLp.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp.setMargins(dp(20), dp(20), dp(24), dp(28));
        rootFrame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootFrame.addView(fab, fabLp);

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
        rootFrame.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = insets.getInsets(WindowInsets.Type.statusBars()).top;
            content.setPadding(dp(20), topInset + dp(14), dp(20), dp(120));
            return insets;
        });
        setContentView(rootFrame);
    }

    private class DotsButton extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        DotsButton(ThemeUtil.Palette p) {
            super(MainActivity.this);
            paint.setColor(p.onSurface);
            setClickable(true);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            float r = dp(2.8f), gap = dp(3.8f);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, step = 2 * r + gap;
            canvas.drawCircle(cx, cy - step, r, paint);
            canvas.drawCircle(cx, cy, r, paint);
            canvas.drawCircle(cx, cy + step, r, paint);
        }
    }

    // ---------------- 环境检测 ----------------

    private void detectEnvAndRefresh() {
        noRootMode = AppPrefs.noRootMode(this);
        io.execute(() -> {
            boolean su = RootShell.findSu() != null;
            boolean rootOk = false;
            if (su && !noRootMode) {
                RootShell.Result id = RootShell.runRoot("id", 20);
                rootOk = id.ok() && id.out.contains("uid=0");
            }
            boolean shizuku = false, shizukuPerm = false;
            if (!rootOk) {
                shizuku = ShizukuHelper.ping();
                shizukuPerm = shizuku && ShizukuHelper.hasPermission();
            }
            final boolean fRoot = rootOk;
            final boolean fShizukuPerm = shizukuPerm;
            final boolean needRequest = shizuku && !shizukuPerm;
            final boolean fInDsu = EnvDetector.isDsu();
            ui.post(() -> {
                rootAvailable = su;
                rootMode = fRoot;
                shizukuMode = !fRoot && fShizukuPerm;
                inDsu = fInDsu;
                if (needRequest) ShizukuHelper.requestPermission(REQ_SHIZUKU);
                refreshAsync();
            });
        });
    }

    private void refreshAsync() {
        io.execute(() -> {
            DsuManager.State st;
            if (rootMode) {
                st = DsuManager.load();
                if (!st.loadOk) st = new DsuManager.State();
            } else if (shizukuMode) {
                st = DsuManager.loadViaShizuku();
                if (!st.loadOk) st = new DsuManager.State();
            } else {
                st = new DsuManager.State();
            }
            final DsuManager.State fs = st;
            ui.post(() -> {
                state = fs;
                activeSlot = state.activeSlot;
                render();
            });
        });
    }

    // ---------------- 渲染 ----------------

    private void render() {
        bodyBox.removeAllViews();
        String sub = "当前系统："+ EnvDetector.systemDisplayName(this);
        if (inDsu) sub += "安装 DSU";
        subtitleView.setText(sub);

        // DSU 状态卡
        LinearLayout st = card(P.surfaceContainerHigh);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("DSU 状态", 18f, P.onSurface, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String summary;
        if (rootMode) summary = inDsu ? "DSU 运行中" : "主系统运行中";
        else if (shizukuMode) summary = nativeDomain() ? "Shizuku 免 Root" : "Shizuku 只读";
        else summary = noRootMode ? "无 Root 只读" : "未授权";
        head.addView(chip(summary, P.secondaryContainer, P.onSecondaryContainer));
        st.addView(head);

        String info = "槽数量：" + state.slots.size() + " 个";
        if (activeSlot != null && !activeSlot.isEmpty()) info += "　活动槽：" + activeSlot;
        if (state.freeKB > 0) info += "\n可用空间："+ DsuManager.fmtBytes(state.freeKB * 1024);
        TextView infoTv = text(info, 12f, P.onSurfaceVariant, false);
        infoTv.setPadding(0, dp(8), 0, 0);
        st.addView(infoTv);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(actionButton("刷新", P.onPrimary, P.primary, v -> detectEnvAndRefresh()));
        if (rootMode) {
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gap.leftMargin = dp(8);
            TextView reboot = actionButton("重启到 DSU", P.onPrimaryContainer, P.primaryContainer,
                    v -> confirmRebootToDsu());
            reboot.setEnabled(!state.slots.isEmpty());
            reboot.setAlpha(state.slots.isEmpty() ? 0.5f : 1f);
            row.addView(reboot, gap);
        }
        st.addView(row);
        bodyBox.addView(st, cardParams());

        // 主系统卡
        bodyBox.addView(mainSystemCard(), cardParams());

        // 槽位
        if (!rootMode && !shizukuMode) {
            LinearLayout c = card(P.surfaceContainer);
            c.addView(text(noRootMode ? "无 Root 模式（实验）" : "需要 Root 权限", 16f, P.onSurface, true));
            StringBuilder sb = new StringBuilder();
            if (noRootMode) {
                sb.append("当前仅识别环境，槽位管理/安装需要 Root。\n环境：");
                sb.append(inDsu ? "DSU 运行中" : "主系统");
                sb.append("\n如已安装 Shizuku，可先用 Shizuku 提供 shell 权限后再试。");
            } else {
                sb.append("请先在 KernelSU / Magisk / Apatch 中允许本应用 root 请求。\n");
                if (EnvDetector.hasShizuku(this)) {
                    sb.append("检测到 Shizuku：可在设置中开启“无 Root 模式（实验）”。");
                }
            }
            TextView t = text(sb.toString(), 12f, P.onSurfaceVariant, false);
            t.setPadding(0, dp(6), 0, 0);
            c.addView(t);
            bodyBox.addView(c, cardParams());
        } else {
            if (shizukuMode) {
                LinearLayout hint = card(P.surfaceContainer);
                hint.addView(text(nativeDomain() ? "Shizuku 免 Root 模式" : "Shizuku 只读模式",
                        14f, P.onSurface, true));
                hint.addView(text(nativeDomain()
                                ? "已通过 Shizuku 获取槽位状态；可使用「原生安装」（DSU Sideloader 方式）免 Root 安装单个 system.img，启停/删除仍需 Root。"
                                : "已通过 Shizuku 获取槽位状态；安装与启停需要 Root。可在设置中把安装域切到「原生安装」以获得免 Root 安装。",
                        11f, P.onSurfaceVariant, false));
                bodyBox.addView(hint, cardParams());
            }
            if (state.slots.isEmpty()) {
                LinearLayout c = card(P.surfaceContainer);
                c.addView(text("没有读到 DSU 槽", 16f, P.onSurface, true));
                bodyBox.addView(c, cardParams());
            } else {
                for (DsuManager.Slot s : orderedSlots()) {
                    bodyBox.addView(slotCard(s), cardParams());
                }
            }
        }

        TextView footer = text("DSU Manager v2.4　·　开发者：XZY / 大肥鱼", 11f, P.onSurfaceVariant, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(14), 0, dp(8));
        bodyBox.addView(footer);

        fab.setVisibility(rootMode || shizukuMode ? View.VISIBLE : View.GONE);
    }

    private View mainSystemCard() {
        LinearLayout c = card(P.surfaceContainerHigh);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("主系统", 18f, P.onSurface, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        boolean mainRunning = !inDsu;
        head.addView(chip(mainRunning ? "运行中" : "未运行",
                mainRunning ? P.success : P.surfaceVariant,
                mainRunning ? Color.WHITE : P.onSurfaceVariant));
        c.addView(head);

        String name = EnvDetector.mainSystemName(this);
        String meta = name + "\nAndroid " + android.os.Build.VERSION.RELEASE;
        if (inDsu) meta = "未运行（当前在 DSU 中）\n" + name + " · Android " + android.os.Build.VERSION.RELEASE;
        TextView m = text(meta, 12f, P.onSurfaceVariant, false);
        m.setPadding(0, dp(8), 0, 0);
        c.addView(m);

        if (rootMode) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(12), 0, 0);
            actions.addView(actionButton(inDsu ? "重启到主系统" : "重启手机",
                    P.onPrimary, P.primary, v -> confirmRebootFromMainCard()));
            c.addView(actions);
        }
        return c;
    }

    private void confirmRebootFromMainCard() {
        new AlertDialog.Builder(this)
                .setTitle(inDsu ? "重启到主系统？" : "重启手机？")
                .setMessage(inDsu
                        ? "将关闭 DSU 并重启，进入主系统。"
                        : "立即重启手机。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重启", (d, w) -> confirmBootMain())
                .show();
    }

    private View slotCard(DsuManager.Slot s) {
        LinearLayout c = card(P.surfaceContainerHigh);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView nameTv = text(s.name, 18f, P.onSurface, true);
        if (rootMode) {
            nameTv.setOnLongClickListener(v -> {
                showRenameDialog(s);
                return true;
            });
        }
        head.addView(nameTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean running = inDsu && s.name.equals(activeSlot);
        if (running) head.addView(chip("运行中", P.success, Color.WHITE));
        else if (s.active) head.addView(chip("单次启动", P.secondaryContainer, P.onSecondaryContainer));
        c.addView(head);

        StringBuilder meta = new StringBuilder(
                inDsu && s.totalKB == 0
                        ? "占用：DSU 中不可读（主系统数据未挂载）"
                        : "占用 " + DsuManager.fmtBytes(s.totalKB * 1024));
        if (s.hasSystem) meta.append("　· system ✓");
        if (s.hasUserdata) meta.append("　· userdata ✓");
        if (s.imageCount > 2) meta.append("　· 分区 ").append(s.imageCount).append(" 个");
        TextView m = text(meta.toString(), 12f, P.onSurfaceVariant, false);
        m.setPadding(0, dp(6), 0, 0);
        c.addView(m);

        if (rootMode) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(12), 0, 0);
            actions.addView(actionButton("启动一次", P.primary, withAlpha(P.primary, 26), v -> slotAction("enableOnce", s)));
            actions.addView(gap(dp(2)));
            actions.addView(actionButton("每次启动", P.onPrimaryContainer, P.secondaryContainer, v -> slotAction("enableSticky", s)));
            actions.addView(gap(dp(2)));
            actions.addView(actionButton("禁用", P.onSurface, withAlpha(P.onSurface, 22), v -> slotAction("disable", s)));
            actions.addView(gap(dp(2)));
            actions.addView(actionButton("删除", P.error, withAlpha(P.error, 22), v -> confirmDelete(s)));
            c.addView(actions);
        }
        return c;
    }

    private View gap(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(d), dp(1)));
        return v;
    }

    private int indexOfPart(String name) {
        for (int i = 0; i < DsuManager.ROM_PARTITIONS.length; i++)
            if (DsuManager.ROM_PARTITIONS[i].equals(name)) return i;
        return 100;
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

    private void slotAction(String action, DsuManager.Slot s) {
        io.execute(() -> {
            RootShell.Result r;
            switch (action) {
                case "enableOnce": r = DsuManager.enableOnce(s.name); break;
                case "enableSticky": r = DsuManager.enableSticky(s.name); break;
                default: r = DsuManager.disable(); break;
            }
            final boolean ok = r.ok();
            ui.post(() -> {
                toast(ok ? "操作成功（重启后生效）" : "操作失败：" + r.out.trim());
                refreshAsync();
            });
        });
    }

    private void confirmDelete(DsuManager.Slot s) {
        new AlertDialog.Builder(this)
                .setTitle("删除槽 " + s.name + "？")
                .setMessage("会永久删除该槽的系统镜像和 userdata，不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> io.execute(() -> {
                    RootShell.Result r = DsuManager.deleteSlot(s.name, activeSlot);
                    ui.post(() -> {
                        toast(r.ok() ? "已删除 " + s.name : "删除失败");
                        refreshAsync();
                    });
                }))
                .show();
    }

    private void confirmBootMain() {
        io.execute(() -> {
            RootShell.Result r = DsuManager.bootMainSystem(inDsu);
            ui.post(() -> toast(r.ok() ? "正在重启…" : "重启失败：" + r.out.trim()));
        });
    }

    private void showRenameDialog(DsuManager.Slot s) {
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
            io.execute(() -> {
                RootShell.Result r = DsuManager.renameSlot(s.name, n);
                if (r.ok() && r.out.contains("RENAMED_OK")) {
                    int i = slotOrder.indexOf(s.name);
                    if (i >= 0) { slotOrder.set(i, n); saveSlotOrder(); }
                }
                ui.post(() -> {
                    toast(r.ok() && r.out.contains("RENAMED_OK") ? "重命名" + n : "重命名失败：" + r.out.trim());
                    refreshAsync();
                });
            });
        }));
        dlg.show();
    }

    // ---------------- 安装 ----------------

    private void showInstallChooser() {
        boolean shizukuOk = shizukuMode && ShizukuHelper.hasPermission();
        if (!rootMode && !shizukuOk) {
            toast("需要 Root；或开启无 Root 模式并授权 Shizuku（仅支持单个 system.img）");
            return;
        }
        String[] items;
        if (rootMode) {
            items = new String[]{
                    "安装镜像 / 压缩包（img / super / zip / tar / gz）",
                    "安装整包 ROM（已拆分的分区文件夹）"
            };
        } else {
            items = new String[]{
                    "安装 system.img / zip / tar / gz（原生安装，DSU Sideloader 方式）"
            };
        }
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("安装 DSU")
                .setItems(items, (d, which) -> {
                    if (which == 0) pickImageFile();
                    else if (rootMode) pickFolder();
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(rounded(P.surfaceContainerHigh, 28));
    }

    private void pickImageFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_IMAGE);
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SAVE_LOG) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && pendingLogText != null) {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new RuntimeException("无法写入");
                    out.write(pendingLogText.getBytes("UTF-8"));
                    toast("日志已保存");
                } catch (Throwable t) {
                    toast("保存日志失败：" + t.getMessage());
                }
            }
            pendingLogText = null;
            pendingLogName = null;
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_IMAGE) {
            Uri uri = data.getData();
            String name = queryName(uri);
            long size = querySize(uri);
            analyzePackage(uri, name, size);
        } else if (requestCode == REQ_TREE) {
            scanFolder(data.getData());
        }
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && !c.isNull(i)) return c.getString(i);
            }
        } catch (Throwable ignored) {
        }
        return uri.getLastPathSegment();
    }

    private long querySize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private void analyzePackage(Uri uri, String name, long size) {
        final ArrayList<String> logs = new ArrayList<>();
        AlertDialog dlg = progressDialog("正在分析安装包", false);
        dlg.show();
        io.execute(() -> {
            ImageFormatDetector.Plan plan;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new RuntimeException("无法读取文件");
                plan = ImageFormatDetector.prepare(in, size, imageCache, s -> {
                    logs.add(s);
                    ui.post(() -> setProgressText(dlg, s));
                });
            } catch (Throwable t) {
                plan = new ImageFormatDetector.Plan();
                plan.error = "读取失败："+ t.getMessage();
                logs.add(plan.error);
            }
            final ImageFormatDetector.Plan fp = plan;
            ui.post(() -> {
                dlg.dismiss();
                if (fp.error != null && !fp.error.isEmpty()) {
                    showFailureDialog("解析安装包失败", fp.error, logs, null);
                    return;
                }
                boolean planOk = fp.rawFile != null
                        || fp.kind == ImageFormatDetector.KIND_SUPER
                        || (fp.kind == ImageFormatDetector.KIND_MULTI && !fp.partFiles.isEmpty());
                if (!planOk) {
                    showFailureDialog("没有可安装的镜像",
                            "请选择 system.img / super.img / 含分区镜像的 zip/tar", logs, null);
                    return;
                }
                pendingPlan = fp;
                pendingFolder.clear();
                showPlanDialog("镜像安装："+ fp.label, planSummary(fp), fp.totalBytes);
            });
        });
    }

    private void scanFolder(Uri tree) {
        io.execute(() -> {
            final ArrayList<PartInput> parts = new ArrayList<>();
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                        DocumentsContract.getTreeDocumentId(tree));
                try (Cursor c = getContentResolver().query(children,
                        new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_SIZE}, null, null, null)) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            String docId = c.getString(0);
                            String n = c.getString(1);
                            long sz = c.isNull(2) ? 0 : c.getLong(2);
                            String part = nameToPartition(n);
                            if (part == null) continue;
                            PartInput pi = new PartInput();
                            pi.name = part;
                            pi.display = n;
                            pi.size = sz;
                            pi.uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
                            parts.add(pi);
                        }
                    }
                }
            } catch (Throwable t) {
                ui.post(() -> showFailureDialog("读取文件夹失败", String.valueOf(t.getMessage()),
                        new ArrayList<>(), null));
                return;
            }
            parts.sort((a, b) -> indexOfPart(a.name) - indexOfPart(b.name));
            boolean hasSystem = false;
            for (PartInput pi : parts) if ("system".equals(pi.name)) hasSystem = true;
            if (!hasSystem) {
                ui.post(() -> toast("文件夹缺少 system.img"));
                return;
            }
            ui.post(() -> {
                pendingFolder.clear();
                pendingFolder.addAll(parts);
                pendingPlan = null;
                long total = 0;
                StringBuilder sb = new StringBuilder();
                for (PartInput pi : parts) {
                    total += pi.size;
                    sb.append("  · ").append(pi.name).append("：")
                            .append(DsuManager.fmtBytes(pi.size)).append('\n');
                }
                showPlanDialog("整包 ROM（" + parts.size() + " 个分区）",
                        sb + "合计："+ DsuManager.fmtBytes(total), total);
            });
        });
    }

    private String nameToPartition(String fileName) {
        if (fileName == null) return null;
        String n = fileName.toLowerCase(Locale.US).trim();
        if (!n.endsWith(".img")) return null;
        n = n.substring(0, n.length() - 4);
        if (n.endsWith("_a") || n.endsWith("_b")) n = n.substring(0, n.length() - 2);
        for (String p : DsuManager.ROM_PARTITIONS) if (p.equals(n)) return p;
        return null;
    }

    private String planSummary(ImageFormatDetector.Plan p) {
        if (p.kind == ImageFormatDetector.KIND_SINGLE) {
            return "检测到单系统镜像\n大小："+ DsuManager.fmtBytes(p.totalBytes)
                    + "\n将作为 system 分区安装。";
        }
        if (p.kind == ImageFormatDetector.KIND_MULTI) {
            StringBuilder sb = new StringBuilder("检测到 " + p.partFiles.size() + " 个分区镜像：\n");
            int shown = 0;
            long total = 0;
            for (ImageFormatDetector.PartFile pf : p.partFiles) {
                total += pf.size;
                if (shown++ < 8) {
                    sb.append("  · ").append(pf.name).append("：")
                            .append(DsuManager.fmtBytes(pf.size)).append('\n');
                }
            }
            if (p.partFiles.size() > 8) sb.append("　· 分区 ").append(p.partFiles.size()).append(" 个\n");
            sb.append("合计：").append(DsuManager.fmtBytes(total));
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder("检测到 " + p.partitions.size() + " 个分区：\n");
        int shown = 0;
        long total = 0;
        for (SuperImage.Partition sp : p.partitions) {
            total += sp.size;
            if (shown++ < 8) sb.append("  · ").append(sp.name).append("：").append(DsuManager.fmtBytes(sp.size)).append('\n');
        }
        if (p.partitions.size() > 8) sb.append("　· 分区 ").append(p.partitions.size()).append(" 个\n");
        sb.append("合计：").append(DsuManager.fmtBytes(total));
        return sb.toString();
    }

    private void showPlanDialog(String title, String summary, long totalBytes) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(8));
        box.addView(text(title, 18f, P.onSurface, true));
        TextView info = text(summary, 12f, P.onSurfaceVariant, false);
        info.setPadding(0, dp(8), 0, dp(12));
        box.addView(info);
        box.addView(text("槽名称", 12f, P.onSurfaceVariant, false));
        EditText slotIn = new EditText(this);
        slotIn.setText(DsuManager.guessNewSlot(state));
        slotIn.setSingleLine(true);
        slotIn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        slotIn.setTextColor(P.onSurface);
        slotIn.setBackground(roundedStroke(P.surfaceVariant, 12, P.outline, 1));
        slotIn.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.addView(slotIn);
        box.addView(text("userdata 大小（GB）", 12f, P.onSurfaceVariant, false));
        EditText gbIn = new EditText(this);
        gbIn.setText("8");
        gbIn.setInputType(InputType.TYPE_CLASS_NUMBER);
        gbIn.setSingleLine(true);
        gbIn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        gbIn.setTextColor(P.onSurface);
        gbIn.setBackground(roundedStroke(P.surfaceVariant, 12, P.outline, 1));
        gbIn.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.addView(gbIn);
        TextView hint = text("安装域：" + ("native".equals(AppPrefs.installDomain(this))
                ? "原生安装（DSU Sideloader 方式，Shizuku 免 Root，仅单 system.img）"
                : "软件安装（super / 整包多分区，需 Root；单 system.img 建议用原生安装）")
                + "\n安装后默认单次启动，下次开机回主系统。", 11f, P.onSurfaceVariant, false);
        hint.setPadding(0, dp(10), 0, 0);
        box.addView(hint);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("开始安装", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String slot = slotIn.getText().toString().trim();
            if (!slot.matches("[A-Za-z0-9_]+")) { toast("槽名只能字母/数字/下划线"); return; }
            int gb;
            try { gb = Integer.parseInt(gbIn.getText().toString().trim()); } catch (Exception e) { gb = 8; }
            if (gb < 1) gb = 1;
            if (gb > 256) gb = 256;
            dlg.dismiss();
            startInstall(slot, (long) gb * 1024L * 1024L * 1024L);
        }));
        dlg.show();
    }

    private void confirmRebootToDsu() {
        if (state.slots.isEmpty()) { toast("没有可启动的 DSU 槽"); return; }
        if (state.slots.size() == 1) {
            askRebootToDsu(state.slots.get(0).name);
            return;
        }
        String[] names = new String[state.slots.size()];
        for (int i = 0; i < state.slots.size(); i++) names[i] = state.slots.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("重启到哪个 DSU 槽？")
                .setItems(names, (d, which) -> askRebootToDsu(names[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void askRebootToDsu(String slot) {
        String msg = inDsu
                ? "将切换到该 DSU 槽并立即重启。"
                : "将启用该槽并立即重启，仅本次启动；下次开机会回到主系统。";
        new AlertDialog.Builder(this)
                .setTitle("重启到 DSU「" + slot + "」？")
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("启用并重启", (d, w) -> rebootToDsu(slot))
                .show();
    }

    private void rebootToDsu(String slot) {
        io.execute(() -> {
            RootShell.Result r = DsuManager.enableOnceFallback(slot);
            if (r == null || !(r.ok() || r.out.contains("ENABLED_OK"))) {
                ui.post(() -> toast("启用 DSU 失败：" + (r == null ? "无返回" : r.out.trim())));
                return;
            }
            if (inDsu) {
                // 在 DSU 中切换槽位：不能 gsi_tool disable，否则会回到主系统
                RootShell.runRoot("reboot", 120);
            } else {
                DsuManager.bootMainSystem(false);
            }
        });
    }

    private boolean nativeDomain() {
        return "native".equals(AppPrefs.installDomain(this));
    }

    private void showNativeLaunchedDialog() {        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("已调起系统 DSU 安装器")
                .setMessage("请在系统弹出的「动态系统更新」确认框里点「安装」。"
                        + "安装完成后可通过系统通知直接启动 DSU；回到本应用刷新即可看到新槽位。")
                .setPositiveButton("知道了", null)
                .create();
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(rounded(P.surfaceContainerHigh, 28));
        }
    }

    private void startInstall(String slot, long userdataBytes) {
        if (state.running) {
            toast("当前正在运行 DSU，请回主系统后再安装");
            return;
        }
        final boolean nativeModeDomain = nativeDomain() || forceNativeOnce;
        forceNativeOnce = false;
        if (!rootMode && !nativeModeDomain) {
            toast("需要 Root；无 Root 请把安装域切到「原生安装」并授权 Shizuku");
            return;
        }
        if (!rootMode && !ShizukuHelper.hasPermission()) {
            toast("无 Root 安装需要 Shizuku 授权");
            return;
        }
        boolean detail = AppPrefs.showInstallDetails(this);
        final InstallLogger log = new InstallLogger(this, slot);
        log.add("=== DSU Manager 2.4 安装开始 ===");
        log.add("slot=" + slot + " userdata=" + DsuManager.fmtBytes(userdataBytes)
                + " domain=" + AppPrefs.installDomain(this));

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        TextView status = text("准备中…", 13f, P.onSurfaceVariant, false);
        status.setPadding(0, dp(6), 0, dp(10));
        TextView logView = null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(16));
        box.addView(text("正在安装", 16f, P.onSurface, true));
        box.addView(status);
        box.addView(bar);
        if (detail) {
            ScrollView sv = new ScrollView(this);
            logView = text("", 10f, P.onSurfaceVariant, false);
            logView.setTypeface(Typeface.MONOSPACE);
            logView.setPadding(0, dp(10), 0, 0);
            sv.addView(logView);
            box.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        }
        AlertDialog dlg = new AlertDialog.Builder(this).setView(box).setCancelable(false).create();
        dlg.show();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(rounded(P.surfaceContainerHigh, 24));

        final TextView fLogView = logView;
        final ImageFormatDetector.Plan plan = pendingPlan;
        final ArrayList<PartInput> folder = new ArrayList<>(pendingFolder);

        io.execute(() -> {
            int parts = plan != null
                    ? (plan.kind == ImageFormatDetector.KIND_SUPER
                        ? plan.partitions.size()
                        : (plan.kind == ImageFormatDetector.KIND_MULTI ? plan.partFiles.size() : 1))
                    : folder.size();
            final int[] doneParts = {0};
            final long totalBytes = plan != null ? plan.totalBytes : sumFolder(folder);
            final long[] doneBytes = {0};
            try {
                if (nativeModeDomain) {
                    if (plan == null || plan.kind != ImageFormatDetector.KIND_SINGLE) {
                        throw new RuntimeException("原生安装（DSU Sideloader 方式）只支持单个 system.img；"
                                + "super / 整包文件夹请改用软件安装（需 Root）");
                    }
                    log.add("原生安装（DSU Sideloader 方式，Shizuku 免 Root 可用）");
                    final int[] lastPct = {-1};
                    DsuSideloadInstaller.Result nr = DsuSideloadInstaller.install(
                            this, plan.rawFile, plan.totalBytes, userdataBytes,
                            (permille, msg) -> {
                                ui.post(() -> {
                                    bar.setProgress(permille);
                                    status.setText(msg);
                                });
                                if (permille - lastPct[0] >= 50) {
                                    lastPct[0] = permille;
                                    log.add(msg);
                                }
                            });
                    log.add("gz=" + nr.gzPath + " shell_rc=" + nr.shellCode);
                    if (nr.shellOut != null && !nr.shellOut.trim().isEmpty()) {
                        log.add("shell: " + nr.shellOut.trim());
                    }
                    if (!nr.ok) throw new RuntimeException(nr.message);
                    clearInstallCache();
                    log.add("已清理安装缓存");
                    log.add("=== INSTALL_OK ===");
                    ui.post(() -> {
                        dlg.dismiss();
                        showNativeLaunchedDialog();
                        refreshAsync();
                    });
                    return;
                }
                if (plan != null) {
                    log.add("plan=" + plan.label + " file="
                            + (plan.rawFile != null ? plan.rawFile.getName() : "-")
                            + " size=" + DsuManager.fmtBytes(plan.size));
                    if (plan.kind == ImageFormatDetector.KIND_SINGLE) {
                        try (InputStream sysIn = new FileInputStream(plan.rawFile)) {
                            installOne(slot, "system", sysIn, plan.size, userdataBytes, true,
                                    log, status, bar, fLogView, doneBytes, totalBytes, actual -> {
                                log.add("system 写入完成");
                            });
                        }
                    } else if (plan.kind == ImageFormatDetector.KIND_MULTI) {
                        for (ImageFormatDetector.PartFile pf : plan.partFiles) {
                            log.add("安装分区 " + pf.name + "（" + DsuManager.fmtBytes(pf.size) + "）");
                            ui.post(() -> status.setText("安装 " + pf.name
                                    + "（" + (doneParts[0] + 1) + "/" + parts + "）"));
                            try (InputStream in = new FileInputStream(pf.file)) {
                                installOne(slot, pf.name, in, pf.size, userdataBytes,
                                        "system".equals(pf.name) && doneParts[0] == 0,
                                        log, status, bar, fLogView, doneBytes, totalBytes, null);
                            }
                            doneParts[0]++;
                        }
                    } else {
                        for (SuperImage.Partition sp : plan.partitions) {
                            log.add("安装分区 " + sp.name + "（" + DsuManager.fmtBytes(sp.size) + "）");
                            ui.post(() -> status.setText("安装 " + sp.name
                                    + "（" + (doneParts[0] + 1) + "/" + parts + "）"));
                            InputStream in = SuperImage.openPartition(plan.rawFile, sp);
                            installOne(slot, sp.name, in, sp.size, userdataBytes,
                                    "system".equals(sp.name) && doneParts[0] == 0,
                                    log, status, bar, fLogView, doneBytes, totalBytes, null);
                            doneParts[0]++;
                        }
                    }
                } else {
                    for (PartInput pi : folder) {
                        log.add("安装分区 " + pi.name + "（" + DsuManager.fmtBytes(pi.size) + "）");
                        ui.post(() -> status.setText("安装 " + pi.name
                                + "（" + (doneParts[0] + 1) + "/" + parts + "）"));
                        InputStream in = getContentResolver().openInputStream(pi.uri);
                        if (in == null) throw new RuntimeException("无法读取 " + pi.display);
                        installOne(slot, pi.name, in, pi.size, userdataBytes,
                                "system".equals(pi.name) && doneParts[0] == 0,
                                log, status, bar, fLogView, doneBytes, totalBytes, null);
                        doneParts[0]++;
                    }
                }
                clearInstallCache();
                log.add("已清理安装缓存");
                log.add("=== INSTALL_OK ===");
                ui.post(() -> {
                    dlg.dismiss();
                    showSuccessDialog(slot, parts, log);
                    refreshAsync();
                });
            } catch (Throwable t) {
                log.add("=== INSTALL_FAILED ===", t);
                final String reason = analyzeFailure(t.getMessage() + "\n");
                final boolean canFallback = !nativeModeDomain
                        && plan != null && plan.kind == ImageFormatDetector.KIND_SINGLE;
                if (canFallback) {
                    RootShell.runRoot("gsi_tool cancel >/dev/null 2>&1; "
                            + "rm -rf '/data/gsi/dsu/" + slot + "/' '/metadata/gsi/dsu/" + slot + "/'; "
                            + "if [ \"$(cat /metadata/gsi/dsu/active 2>/dev/null)\" = \"" + slot + "\" ]; then "
                            + "  : > /metadata/gsi/dsu/active; rm -f /metadata/gsi/dsu/one_shot_boot; fi", 60);
                    log.add("已清理失败的槽位，可选原生安装重试");
                }
                ui.post(() -> {
                    dlg.dismiss();
                    if (canFallback) {
                        showSoftwareFallbackDialog(reason, log, slot, userdataBytes);
                    } else {
                        showFailureDialog("安装失败", reason, null, log);
                    }
                    refreshAsync();
                });
            }
        });
    }

    /** 安装成功后清理本次安装产生的缓存（cache/images 下的 raw、解压、sparse 转换等临时文件）。 */
    private void clearInstallCache() {
        final int[] deleted = {0};
        try {
            File[] files = imageCache.listFiles();
            if (files != null) {
                for (File f : files) deleteRecursively(f, deleted);
            }
        } catch (Throwable ignored) {
        }
        android.util.Log.i("DSUAPP", "clearInstallCache deleted=" + deleted[0]);
    }

    private static void deleteRecursively(File f, int[] deleted) {
        if (f == null) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursively(c, deleted);
            }
        }
        if (f.delete()) deleted[0]++;
    }

    private long sumFolder(ArrayList<PartInput> parts) {
        long t = 0;
        for (PartInput p : parts) t += p.size;
        return t;
    }

    private interface After {
        void run(boolean ok);
    }

    private void installOne(String slot, String partition, InputStream in, long size,
                            long userdataBytes, boolean guard, InstallLogger log,
                            TextView status, ProgressBar bar, TextView logView,
                            long[] doneBytes, long totalBytes, After after) throws Exception {
        final long[] last = {0};
        boolean nativeMode = "native".equals(AppPrefs.installDomain(this)) && size == totalBytes;
        RootShell.Result r;
        if (nativeMode) {
            log.add("原生安装（文件重定向）");
            File tmp = new File(imageCache, "native_" + System.currentTimeMillis() + ".img");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                }
            }
            r = DsuManager.install(slot, tmp.getAbsolutePath(), size, userdataBytes);
            tmp.delete();
        } else {
            r = DsuManager.installPartitionStream(slot, partition, in, size, userdataBytes, guard,
                    (written, total) -> {
                        long base = doneBytes[0];
                        long now = base + written;
                        if (totalBytes > 0) {
                            int permille = (int) Math.min(1000, now * 1000 / totalBytes);
                            ui.post(() -> bar.setProgress(permille));
                        }
                        if (written - last[0] >= 128L * 1024 * 1024) {
                            last[0] = written;
                            String line = partition + " 个"+ DsuManager.fmtBytes(written)
                                    + " / " + DsuManager.fmtBytes(total);
                            log.add(line);
                            if (logView != null) ui.post(() -> logView.append("\n" + line));
                        }
                    });
        }
        doneBytes[0] += size;
        String out = r.out;
        log.add("gsi_tool rc=" + r.code + " " + (out.length() > 500 ? out.substring(0, 500) : out));
        if (after != null) after.run(r.ok() && (out.contains("INSTALL_OK") || out.contains("Please reboot")));
        if (!(out.contains("INSTALL_OK") || out.contains("Please reboot") || (r.ok() && nativeMode))) {
            throw new RuntimeException(partition + " 安装失败："+ out.trim());
        }
    }

    private String analyzeFailure(String out) {
        String s = out == null ? "" : out.toLowerCase(Locale.US);
        if (s.contains("err_slot_exists")) return "槽已存在，请换一个槽名或先删除旧槽。";
        if (s.contains("no su binary")) return "没有拿到 Root 权限。";
        if (s.contains("operation not permitted") || s.contains("eperm")) return "权限被 SELinux 拒绝（gsid 限制）。";
        if (s.contains("in progress")) return "gsid 上一次安装未结束，已尝试 cancel，请重试。";
        if (s.contains("no space")) return "存储空间不足。";
        if (s.contains("err_no_tool")) return "系统缺少 gsi_tool。";
        if (s.contains("timeout")) return "执行超时（镜像较大或写入卡住）。";
        if (s.contains("install_failed")) return "gsi_tool 返回失败（rc 见日志）。";
        if (out == null || out.trim().isEmpty()) return "未知错误，请查看日志。";
        String first = out.trim().split("\n")[0];
        return first.length() > 200 ? first.substring(0, 200) : first;
    }

    private long queryTotal(String slot, long userdataBytes) {
        return userdataBytes;
    }

    // ---------------- 结果对话框 ----------------

    private void showSuccessDialog(String slot, int parts, InstallLogger log) {
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("安装成功")
                .setMessage("已安装到 DSU 槽「" + slot + "」\n共 " + parts + " 个分区。")
                .setPositiveButton("保存日志", (d, w) -> saveLogViaSaf(log.text(), log.fileName()))
                .setNeutralButton("重启进入", (d, w) -> io.execute(() -> {
                    DsuManager.enableOnce(slot);
                    RootShell.Result r = DsuManager.bootMainSystem(inDsu);
                    ui.post(() -> toast("正在重启…"));
                }))
                .setNegativeButton("关闭", null)
                .create();
        dlg.show();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(rounded(P.surfaceContainerHigh, 28));
    }

    private void saveLogViaSaf(String content, String fileName) {
        if (content == null) content = "";
        pendingLogText = content;
        pendingLogName = fileName;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, fileName);
        try {
            startActivityForResult(i, REQ_SAVE_LOG);
        } catch (Throwable t) {
            toast("没有可用的文件保存器");
        }
    }

    private void showSoftwareFallbackDialog(String reason, InstallLogger log, String slot, long userdataBytes) {
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("软件安装失败")
                .setMessage(reason + "\n\n"
                        + "KernelSU 等 Root 方案下，gsi_tool 向 gsid 提交镜像数据可能被 SELinux 拒绝。\n"
                        + "可改用「原生安装」（系统 DSU 安装器）重试这个单 system.img。")
                .setPositiveButton("改用原生安装", (d, w) -> {
                    forceNativeOnce = true;
                    startInstall(slot, userdataBytes);
                })
                .setNeutralButton("保存日志", (d, w) -> saveLogViaSaf(log.text(), log.fileName()))
                .setNegativeButton("关闭", null)
                .create();
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(rounded(P.surfaceContainerHigh, 28));
        }
    }

    private void showFailureDialog(String title, String reason, ArrayList<String> extra, InstallLogger log) {
        StringBuilder msg = new StringBuilder();
        msg.append("原因：").append(reason).append('\n');
        if (extra != null && !extra.isEmpty()) {
            msg.append('\n');
            for (int i = 0; i < Math.min(6, extra.size()); i++) msg.append(extra.get(i)).append('\n');
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg.toString())
                .setNegativeButton("关闭", null);
        if (log != null) {
            b.setPositiveButton("保存日志", (d, w) -> saveLogViaSaf(log.text(), log.fileName()));
        }
        AlertDialog dlg = b.create();
        dlg.show();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(rounded(P.surfaceContainerHigh, 28));
    }

    // ---------------- 进度对话框 ----------------

    private AlertDialog progressDialog(String title, boolean horizontal) {
        ProgressBar bar = new ProgressBar(this, null,
                horizontal ? android.R.attr.progressBarStyleHorizontal : android.R.attr.progressBarStyle);
        bar.setMax(1000);
        TextView status = text(title, 13f, P.onSurfaceVariant, false);
        status.setPadding(0, dp(6), 0, dp(10));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(16));
        box.addView(status);
        box.addView(bar);
        AlertDialog dlg = new AlertDialog.Builder(this).setView(box).setCancelable(false).create();
        dlg.show();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(rounded(P.surfaceContainerHigh, 24));
        return dlg;
    }

    private void setProgressText(AlertDialog dlg, String s) {
        try {
            LinearLayout box = (LinearLayout) ((ViewGroup) dlg.getWindow().getDecorView())
                    .getChildAt(0);
            // 简单兜底：忽略，进度文本已在创建时设置
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 工具 ----------------

    private void toast(String s) {
        ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }
}

