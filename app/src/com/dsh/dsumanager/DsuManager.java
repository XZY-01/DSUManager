package com.dsh.dsumanager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** DSU 槽的查询与操作（底层用 gsi_tool，需要 root）。 */
public final class DsuManager {

    public static final class Slot {
        public String name = "";
        public boolean installed = false;
        public boolean hasSystem = false;
        public boolean hasUserdata = false;
        public int imageCount = 0;
        public boolean active = false;
        public long totalKB = 0;
        public String avbSha1 = "";
    }

    public static final class State {
        public boolean loadOk = false;
        public boolean running = false;
        public boolean installed = false;
        public boolean enabled = false;
        public boolean disabled = false;
        public String activeSlot = "";
        public boolean oneShot = false;
        public String installStatus = "";
        public long freeKB = 0;
        public final ArrayList<Slot> slots = new ArrayList<>();
        public String raw = "";
    }

    private static final String SCRIPT =
            "echo @@A; gsi_tool status 2>&1; "
          + "echo @@B; cat /metadata/gsi/dsu/active 2>/dev/null; echo; "
          + "echo @@C; if [ -e /metadata/gsi/dsu/one_shot_boot ]; then echo 1; else echo 0; fi; "
          + "echo @@D; cat /metadata/gsi/dsu/install_status 2>/dev/null; echo; "
          + "echo @@E; for d in /data/gsi/dsu/*/; do [ -d \"$d\" ] || continue; "
          + "echo \"SIZE $(basename \"$d\") $(du -sk \"$d\" 2>/dev/null | cut -f1)\"; done; "
          + "echo @@F; df -k /data 2>/dev/null | tail -n 1; echo @@G";

    public static State load() {
        return parseFrom(RootShell.runRoot(SCRIPT, 90));
    }

    /** 无 Root：通过 Shizuku 执行同一脚本解析状态。 */
    public static State loadViaShizuku() {
        return parseFrom(RootShell.runShizuku(SCRIPT, 90));
    }

    private static State parseFrom(RootShell.Result r) {
        State st = new State();
        st.raw = r.out;
        if (!r.ok() && r.out.trim().isEmpty()) {
            return st;
        }
        String section = "";
        Map<String, Long> sizes = new LinkedHashMap<>();
        ArrayList<String> statusLines = new ArrayList<>();
        String active = "", oneShot = "", installStatus = "", dfLine = "";
        for (String line : r.out.split("\n")) {
            String l = line.trim();
            if (l.equals("@@A")) { section = "A"; continue; }
            if (l.equals("@@B")) { section = "B"; continue; }
            if (l.equals("@@C")) { section = "C"; continue; }
            if (l.equals("@@D")) { section = "D"; continue; }
            if (l.equals("@@E")) { section = "E"; continue; }
            if (l.equals("@@F")) { section = "F"; continue; }
            if (l.equals("@@G")) { section = "G"; break; }
            switch (section) {
                case "A":
                    if (!l.isEmpty()) statusLines.add(l);
                    break;
                case "B":
                    if (!l.isEmpty()) active = l;
                    break;
                case "C":
                    oneShot = l;
                    break;
                case "D":
                    installStatus = l;
                    break;
                case "E":
                    if (l.startsWith("SIZE ")) {
                        String[] p = l.split(" ");
                        if (p.length >= 3) {
                            try { sizes.put(p[1], Long.parseLong(p[2])); } catch (Exception ignored) { }
                        }
                    }
                    break;
                case "F":
                    if (!l.isEmpty()) dfLine = l;
                    break;
                default:
                    break;
            }
        }
        st.activeSlot = active;
        st.oneShot = "1".equals(oneShot);
        st.installStatus = installStatus;
        st.freeKB = parseFreeKB(dfLine);

        Slot cur = null;
        for (String l : statusLines) {
            if (l.startsWith("[")) {
                cur = new Slot();
                int idx = l.indexOf(']');
                cur.name = idx >= 0 ? l.substring(idx + 1).trim() : l.trim();
                cur.installed = true;
                st.slots.add(cur);
            } else if (l.equals("running")) {
                st.running = true;
            } else if (l.equals("installed")) {
                st.installed = true;
            } else if (l.equals("enabled")) {
                st.enabled = true;
            } else if (l.equals("disabled")) {
                st.disabled = true;
            } else if (cur != null) {
                if (l.startsWith("installed: ")) cur.imageCount++;
                if (l.startsWith("installed: system_gsi")) cur.hasSystem = true;
                else if (l.startsWith("installed: userdata_gsi")) cur.hasUserdata = true;
                else if (l.startsWith("AVB public key")) {
                    int i = l.indexOf(':');
                    if (i >= 0) cur.avbSha1 = l.substring(i + 1).trim();
                }
            }
        }
        for (Slot s : st.slots) {
            Long kb = sizes.get(s.name);
            s.totalKB = kb == null ? 0 : kb;
            s.active = s.name.equals(st.activeSlot);
        }
        // active 可能指向已被删除的槽，避免界面显示不存在的活动槽
        if (!st.activeSlot.isEmpty()) {
            boolean found = false;
            for (Slot s : st.slots) {
                if (s.name.equals(st.activeSlot)) { found = true; break; }
            }
            if (!found) st.activeSlot = "";
        }
        st.loadOk = st.raw.contains("@@A");
        if (st.loadOk) cached = st;
        return st;
    }

    // 最近一次成功的加载结果：界面先用缓存秒开，再后台刷新
    private static State cached;

    public static State getCached() {
        return cached;
    }

    private static long parseFreeKB(String dfLine) {
        if (dfLine == null || dfLine.isEmpty()) return 0;
        String[] parts = dfLine.split("\\s+");
        if (parts.length >= 4) {
            try { return Long.parseLong(parts[3]); } catch (Exception ignored) { }
        }
        return 0;
    }

    public static RootShell.Result enableOnce(String slot) {
        return RootShell.runRoot("gsi_tool enable -d " + slot + " -s", 120);
    }

    public static RootShell.Result enableSticky(String slot) {
        return RootShell.runRoot("gsi_tool enable -d " + slot, 120);
    }

    public static RootShell.Result disable() {
        return RootShell.runRoot("gsi_tool disable", 120);
    }

    public static RootShell.Result deleteSlot(String slot, String activeSlot) {
        StringBuilder cmd = new StringBuilder();
        if (slot.equals(activeSlot)) {
            cmd.append("gsi_tool disable 2>/dev/null; ");
        }
        cmd.append("rm -rf /data/gsi/dsu/").append(slot).append(" /metadata/gsi/dsu/").append(slot).append("; ");
        if (slot.equals(activeSlot)) {
            cmd.append(": > /metadata/gsi/dsu/active; rm -f /metadata/gsi/dsu/one_shot_boot; ");
        }
        cmd.append("echo DONE");
        return RootShell.runRoot(cmd.toString(), 120);
    }

    public static RootShell.Result install(String slot, String imagePath, long gsiSize, long userdataSize) {
        // 兼容旧调用：仅用于已有可读文件（不推荐，会走文件重定向）
        String cmd = "gsi_tool install --install-dir=/data/gsi/dsu/" + slot
                + " --gsi-size=" + gsiSize
                + " --userdata-size=" + userdataSize
                + " --no-reboot < '" + imagePath.replace("'", "'\\''") + "'";
        return RootShell.runRoot(cmd, 3600);
    }

    public static final String[] ROM_PARTITIONS = {
            "system", "system_ext", "product", "vendor", "odm",
            "vendor_dlkm", "system_dlkm", "mi_ext"
    };

    /**
     * 多分区 DSU 安装：把整包 ROM 的各个逻辑分区（system/vendor/product/...）装进同一个槽。
     * 原理：AOSP TransformFstabForDsu 会把 <名字>_gsi 映射到对应分区（system_gsi→system 等）。
     * 每个分区单独调一次 gsi_tool install --partition-name。
     */
    public static RootShell.Result installPartitionStream(String slot, String partition,
                                                          java.io.InputStream in, long gsiSize,
                                                          long userdataSize, boolean guardExisting,
                                                          RootShell.StreamListener listener) {
        String img = "/data/local/tmp/dsu_part.img";
        StringBuilder cmd = new StringBuilder();
        cmd.append("S=").append(slot).append("; P=").append(partition).append("; IMG=").append(img).append("; ");
        if (guardExisting) {
            cmd.append("if [ -f \"/metadata/gsi/dsu/$S/complete\" ] || [ -f \"/data/gsi/dsu/$S/system_gsi.img.0000\" ]; then ")
               .append("echo ERR_SLOT_EXISTS; exit 9; fi; ");
        }
        cmd.append("command -v gsi_tool >/dev/null 2>&1 || { echo ERR_NO_TOOL; exit 8; }; ")
           .append("rm -f \"$IMG\"; cat > \"$IMG\" || { echo ERR_WRITE; rm -f \"$IMG\"; exit 7; }; ")
           .append("echo IMAGE_WRITTEN; ")
           .append("cat \"$IMG\" | gsi_tool install --install-dir=/data/gsi/dsu/$S")
           .append(" --gsi-size=").append(gsiSize)
           .append(" --userdata-size=").append(userdataSize)
           .append(" --partition-name ").append(partition)
           .append(" --no-reboot; rc=$?; rm -f \"$IMG\"; ")
           .append("if [ $rc -ne 0 ]; then gsi_tool cancel >/dev/null 2>&1; ")
           .append("rm -rf \"/data/gsi/dsu/$S\" \"/metadata/gsi/dsu/$S\" 2>/dev/null; ")
           .append("if [ \"$(cat /metadata/gsi/dsu/active 2>/dev/null)\" = \"$S\" ]; then ")
           .append(": > /metadata/gsi/dsu/active; rm -f /metadata/gsi/dsu/one_shot_boot; fi; ")
           .append("echo INSTALL_FAILED rc=$rc; exit $rc; fi; echo INSTALL_OK");
        return RootShell.runRootWithStream(cmd.toString(), in, gsiSize, 3600, listener);
    }

    public static RootShell.Result cancelInstall() {
        return RootShell.runRoot("gsi_tool cancel 2>/dev/null; echo CANCEL_DONE", 120);
    }

    /** 读取某个槽的镜像占用：{system 字节, userdata 字节, 其他字节}。 */
    public static long[] slotUsage(String slot) {
        String cmd = "D=/data/gsi/dsu/" + slot + "; "
                + "for f in \"$D\"/*; do n=$(basename \"$f\"); s=$(stat -c %s \"$f\" 2>/dev/null); "
                + "[ -n \"$s\" ] && echo \"$n $s\"; done";
        RootShell.Result r = RootShell.runRoot(cmd, 60);
        long[] out = new long[3];
        for (String line : r.out.split("\n")) {
            String[] p = line.trim().split(" ");
            if (p.length < 2) continue;
            long v;
            try { v = Long.parseLong(p[1]); } catch (Exception e) { continue; }
            if (p[0].startsWith("system_gsi")) out[0] += v;
            else if (p[0].startsWith("userdata_gsi")) out[1] += v;
            else out[2] += v;
        }
        return out;
    }

    /**
     * 调整已有槽的 userdata 大小（会清空该 DSU 的全部用户数据）。
     * 原理：把旧的 system 镜像复制到 /data/local/tmp，然后用 gsi_tool 走"安装 system"流程：
     * gsid 会先用新大小重建 userdata（不写入内容，保持稀疏），再用旧 system 内容重建 system。
     * vendor/product 等其他分区不动。
     */
    public static RootShell.Result resizeUserdata(String slot, long newUserdataSize) {
        String cmd =
                "S=" + slot + "; D=/data/gsi/dsu/$S; T=/data/local/tmp/dsu_sys_resize.img; "
              + "ls \"$D\"/system_gsi.img.0* >/dev/null 2>&1 || { echo ERR_NO_SYSTEM; exit 6; }; "
              + "rm -f \"$T\"; "
              + "cat \"$D\"/system_gsi.img.0* > \"$T\" || { echo ERR_COPY; rm -f \"$T\"; exit 7; }; "
              + "SZ=$(stat -c %s \"$T\"); "
              + "cat \"$T\" | gsi_tool install --install-dir=\"$D\" --gsi-size=$SZ"
              + " --userdata-size=" + newUserdataSize + " --no-reboot; "
              + "rc=$?; rm -f \"$T\"; "
              + "if [ $rc -ne 0 ]; then gsi_tool cancel >/dev/null 2>&1; echo RESIZE_FAILED rc=$rc; exit $rc; fi; "
              + "echo RESIZE_OK";
        return RootShell.runRoot(cmd, 1200);
    }

    /**
     * 安装：app 把镜像流喂给 root 的 `cat > 临时文件`，再让 root 用
     * `cat 临时文件 | gsi_tool install` 安装。
     * 这样管道由 root 创建，gsid 读取时不会被 SELinux 拒绝（app 创建的管道会被拒绝）。
     * 保护：
     *  1) 目标槽已完整安装时拒绝，避免 gsid openInstall 删掉旧槽的 complete；
     *  2) 失败时 gsi_tool cancel + 清理不完整槽，避免之后所有安装都报 error code 1。
     */
    public static RootShell.Result installStream(String slot, java.io.InputStream in,
                                                 long gsiSize, long userdataSize,
                                                 RootShell.StreamListener listener) {
        String img = "/data/local/tmp/dsu_install_" + slot + ".img";
        String cmd =
                "S=" + slot + "; IMG='" + img + "'; "
              + "if [ -f \"/metadata/gsi/dsu/$S/complete\" ] || [ -f \"/data/gsi/dsu/$S/system_gsi.img.0000\" ]; then "
              + "  echo ERR_SLOT_EXISTS; exit 9; fi; "
              + "if ! command -v gsi_tool >/dev/null 2>&1; then echo ERR_NO_TOOL; exit 8; fi; "
              + "rm -f \"$IMG\"; "
              + "cat > \"$IMG\" || { echo ERR_WRITE; rm -f \"$IMG\"; exit 7; }; "
              + "echo IMAGE_WRITTEN; "
              + "cat \"$IMG\" | gsi_tool install --install-dir=/data/gsi/dsu/$S"
              + " --gsi-size=" + gsiSize
              + " --userdata-size=" + userdataSize
              + " --no-reboot; "
              + "rc=$?; rm -f \"$IMG\"; "
              + "if [ $rc -ne 0 ]; then "
              + "  gsi_tool cancel >/dev/null 2>&1; "
              + "  rm -rf \"/data/gsi/dsu/$S\" \"/metadata/gsi/dsu/$S\" 2>/dev/null; "
              + "  if [ \"$(cat /metadata/gsi/dsu/active 2>/dev/null)\" = \"$S\" ]; then "
              + "    : > /metadata/gsi/dsu/active; rm -f /metadata/gsi/dsu/one_shot_boot; fi; "
              + "  echo INSTALL_FAILED rc=$rc; exit $rc; "
              + "fi; "
              + "echo INSTALL_OK";
        return RootShell.runRootWithStream(cmd, in, gsiSize, 3600, listener);
    }

    /**
     * 重命名 DSU 槽：
     * 1) 移动 /data/gsi/dsu/OLD -> NEW
     * 2) 移动 /metadata/gsi/dsu/OLD -> NEW
     * 3) 更新 install_dir / metadata_encryption_dir 中的路径
     * 4) 如果删的是活动槽，更新 active
     */
    public static RootShell.Result renameSlot(String oldName, String newName) {
        String o = oldName, n = newName;
        String cmd =
                "D=/data/gsi/dsu; M=/metadata/gsi/dsu; K=/metadata/vold/metadata_encryption/dsu; "
              + "test -d \"$D/" + o + "\" || { echo ERR_NO_DATA; exit 10; }; "
              + "test ! -e \"$D/" + n + "\" || { echo ERR_TARGET_DATA; exit 11; }; "
              + "test ! -e \"$M/" + n + "\" || { echo ERR_TARGET_META; exit 12; }; "
              + "mv \"$D/" + o + "\" \"$D/" + n + "\" || { echo ERR_MV_DATA; exit 13; }; "
              + "mv \"$M/" + o + "\" \"$M/" + n + "\" || { echo ERR_MV_META; exit 14; }; "
              + "sed -i \"s#/data/gsi/dsu/" + o + "/#/data/gsi/dsu/" + n + "/#\" \"$M/" + n + "/install_dir\" 2>/dev/null; "
              + "sed -i \"s#$K/" + o + "#$K/" + n + "#\" \"$M/" + n + "/metadata_encryption_dir\" 2>/dev/null; "
              + "if [ -d \"$K/" + o + "\" ]; then mv \"$K/" + o + "\" \"$K/" + n + "\"; fi; "
              + "if [ \"$(cat $M/active 2>/dev/null)\" = \"" + o + "\" ]; then printf '%s' \"" + n + "\" > \"$M/active\"; fi; "
              + "echo RENAMED_OK";
        return RootShell.runRoot(cmd, 120);
    }

    /** 从 DSU 里启动主系统（先禁用 DSU 再重启）；在主系统里则直接重启。 */
    public static RootShell.Result bootMainSystem(boolean inDsu) {
        if (inDsu) {
            return RootShell.runRoot("gsi_tool disable 2>/dev/null; sleep 1; reboot", 120);
        }
        return RootShell.runRoot("reboot", 120);
    }

    public static String guessNewSlot(State st) {
        if (st == null) return "dsu";
        ArrayList<String> names = new ArrayList<>();
        for (Slot s : st.slots) names.add(s.name);
        if (!names.contains("dsu")) return "dsu";
        int i = 2;
        while (names.contains("dsu" + i)) i++;
        return "dsu" + i;
    }

    public static String fmtBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        double b = bytes;
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (b >= 1024 && i < u.length - 1) { b /= 1024; i++; }
        return String.format(java.util.Locale.US, i == 0 ? "%.0f %s" : "%.1f %s", b, u[i]);
    }

    private DsuManager() {}
}
