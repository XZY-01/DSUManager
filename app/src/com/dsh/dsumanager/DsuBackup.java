package com.dsh.dsumanager;

import java.util.ArrayList;

/**
 * DSU 槽备份 / 恢复（需要 Root，仅在主系统可用）。
 *
 * 备份目录：/data/media/0/Download/DSUManager/backups/&lt;槽名&gt;_&lt;时间&gt;/
 * 直接写 /data/media/0 而不是 /sdcard，避免 FUSE 把稀疏文件写实导致体积暴涨。
 */
public final class DsuBackup {

    public static final String ROOT = "/data/media/0/Download/DSUManager/backups";
    private static final String CANCEL_FILE = "/data/local/tmp/.dsu_backup_cancel";

    public interface Progress {
        void onLine(String line);
        void onProgress(long doneKB, long totalKB);
    }

    public static final class Backup {
        public String name = "";
        public String dir = "";
        public String slot = "";
        public String time = "";
        public long kb = 0;
        public boolean complete = false;

        public String display() {
            String s = slot.isEmpty() ? name : slot;
            if (!time.isEmpty()) s += "　·　" + time;
            s += "　·　" + DsuManager.fmtBytes(kb * 1024);
            if (!complete) s += "（不完整）";
            return s;
        }
    }

    private static final String LIST_SCRIPT =
            "B=" + ROOT + "\n"
          + "[ -d \"$B\" ] || exit 0\n"
          + "for d in \"$B\"/*/; do\n"
          + "  [ -d \"$d\" ] || continue\n"
          + "  n=$(basename \"$d\")\n"
          + "  case \"$n\" in *.tmp) continue;; esac\n"
          + "  slot=$(sed -n 's/^SLOT=//p' \"$d/manifest.txt\" 2>/dev/null | head -n1)\n"
          + "  tm=$(sed -n 's/^TIME=//p' \"$d/manifest.txt\" 2>/dev/null | head -n1)\n"
          + "  kb=$(du -sk \"$d\" 2>/dev/null | cut -f1)\n"
          + "  ok=0; [ -f \"$d/.complete\" ] && ok=1\n"
          + "  echo \"BK|$n|$slot|$tm|$kb|$ok\"\n"
          + "done\n";

    public static ArrayList<Backup> list() {
        ArrayList<Backup> out = new ArrayList<>();
        RootShell.Result r = RootShell.runRoot(LIST_SCRIPT, 90);
        if (r == null || r.out == null) return out;
        for (String line : r.out.split("\n")) {
            line = line.trim();
            if (!line.startsWith("BK|")) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 6) continue;
            Backup b = new Backup();
            b.name = p[1];
            b.slot = p[2];
            b.time = p[3];
            try {
                b.kb = Long.parseLong(p[4]);
            } catch (Exception e) {
                b.kb = 0;
            }
            b.complete = "1".equals(p[5]);
            b.dir = ROOT + "/" + b.name;
            out.add(b);
        }
        return out;
    }

    public static RootShell.Result backup(String slot, Progress p) {
        String cmd =
                "S='" + slot + "'\n"
              + "B=" + ROOT + "\n"
              + "C=" + CANCEL_FILE + "\n"
              + "TS=$(date +%Y%m%d_%H%M%S)\n"
              + "DST=\"$B/${S}_$TS\"\n"
              + "TMP=\"$DST.tmp\"\n"
              + "D=\"/data/gsi/dsu/$S\"\n"
              + "M=\"/metadata/gsi/dsu/$S\"\n"
              + "rm -f \"$C\"\n"
              + "test -d \"$D\" || { echo ERR_NO_DATA; exit 10; }\n"
              + "mkdir -p \"$B\" || { echo ERR_MKDIR; exit 11; }\n"
              + "rm -rf \"$TMP\"; mkdir -p \"$TMP/data\" \"$TMP/metadata\" || { echo ERR_TMP; exit 12; }\n"
              + "TOTAL=$(du -sk \"$D\" 2>/dev/null | cut -f1); [ -z \"$TOTAL\" ] && TOTAL=0\n"
              + "AVAIL=$(df -k /data 2>/dev/null | awk 'NR==2{print $4}')\n"
              + "echo \"TOTAL $TOTAL\"\n"
              + "if [ -n \"$AVAIL\" ] && [ \"$AVAIL\" -lt $((TOTAL + TOTAL / 10)) ]; then "
              + "echo \"ERR_NO_SPACE need=$TOTAL avail=$AVAIL\"; rm -rf \"$TMP\"; exit 13; fi\n"
              + "( cp -a --sparse=always \"$D/.\" \"$TMP/data/\" 2>/dev/null || cp -a \"$D/.\" \"$TMP/data/\" ) &\n"
              + "CPID=$!\n"
              + "while kill -0 $CPID 2>/dev/null; do\n"
              + "  if [ -f \"$C\" ]; then kill $CPID 2>/dev/null; wait $CPID 2>/dev/null; "
              + "echo CANCELLED; rm -rf \"$TMP\"; exit 14; fi\n"
              + "  echo \"PROG $(du -sk \"$TMP/data\" 2>/dev/null | cut -f1)\"\n"
              + "  sleep 2\n"
              + "done\n"
              + "wait $CPID; rc=$?\n"
              + "[ $rc -ne 0 ] && { echo \"ERR_CP_DATA rc=$rc\"; rm -rf \"$TMP\"; exit 15; }\n"
              + "cp -a \"$M/.\" \"$TMP/metadata/\" 2>/dev/null\n"
              + "printf 'SLOT=%s\\nTIME=%s\\n' \"$S\" \"$TS\" > \"$TMP/manifest.txt\"\n"
              + "mv \"$TMP\" \"$DST\" || { echo ERR_RENAME; rm -rf \"$TMP\"; exit 16; }\n"
              + "touch \"$DST/.complete\"\n"
              + "echo \"BACKUP_OK $DST\"\n";
        return RootShell.runRootLines(cmd, 7200, line -> handleLine(line, p));
    }

    public static RootShell.Result restore(String backupDir, String slot, Progress p) {
        String cmd =
                "B='" + backupDir + "'\n"
              + "S='" + slot + "'\n"
              + "C=" + CANCEL_FILE + "\n"
              + "D=\"/data/gsi/dsu/$S\"\n"
              + "M=\"/metadata/gsi/dsu/$S\"\n"
              + "K=\"/metadata/vold/metadata_encryption/dsu\"\n"
              + "rm -f \"$C\"\n"
              + "test -d \"$B\" || { echo ERR_NO_BACKUP; exit 20; }\n"
              + "test -f \"$B/.complete\" || { echo ERR_INCOMPLETE; exit 21; }\n"
              + "OS=$(sed -n 's/^SLOT=//p' \"$B/manifest.txt\" 2>/dev/null | head -n1); [ -z \"$OS\" ] && OS=\"$S\"\n"
              + "TOTAL=$(du -sk \"$B/data\" 2>/dev/null | cut -f1); [ -z \"$TOTAL\" ] && TOTAL=0\n"
              + "echo \"TOTAL $TOTAL\"\n"
              + "echo \"FROM $OS\"\n"
              + "rm -rf \"$D\" \"$M\"; mkdir -p \"$D\" \"$M\" || { echo ERR_MKDIR; exit 22; }\n"
              + "( cp -a --sparse=always \"$B/data/.\" \"$D/\" 2>/dev/null || cp -a \"$B/data/.\" \"$D/\" ) &\n"
              + "CPID=$!\n"
              + "while kill -0 $CPID 2>/dev/null; do\n"
              + "  if [ -f \"$C\" ]; then kill $CPID 2>/dev/null; wait $CPID 2>/dev/null; "
              + "echo CANCELLED; exit 23; fi\n"
              + "  echo \"PROG $(du -sk \"$D\" 2>/dev/null | cut -f1)\"\n"
              + "  sleep 2\n"
              + "done\n"
              + "wait $CPID; rc=$?\n"
              + "[ $rc -ne 0 ] && { echo \"ERR_CP_DATA rc=$rc\"; exit 24; }\n"
              + "cp -a \"$B/metadata/.\" \"$M/\" 2>/dev/null\n"
              + "if [ \"$OS\" != \"$S\" ]; then\n"
              + "  sed -i \"s#/data/gsi/dsu/$OS/#/data/gsi/dsu/$S/#\" \"$M/install_dir\" 2>/dev/null\n"
              + "  sed -i \"s#$K/$OS#$K/$S#\" \"$M/metadata_encryption_dir\" 2>/dev/null\n"
              + "  if [ -d \"$K/$OS\" ]; then rm -rf \"$K/$S\"; mv \"$K/$OS\" \"$K/$S\"; fi\n"
              + "fi\n"
              + "echo RESTORE_OK\n";
        return RootShell.runRootLines(cmd, 7200, line -> handleLine(line, p));
    }

    public static RootShell.Result delete(String dir) {
        return RootShell.runRoot("rm -rf '" + dir + "' && echo DELETED", 300);
    }

    public static void cancel() {
        RootShell.runRoot("touch " + CANCEL_FILE, 30);
    }

    private static long totalKB = 0;

    private static void handleLine(String line, Progress p) {
        if (line == null) return;
        String l = line.trim();
        if (l.startsWith("TOTAL ")) {
            try {
                totalKB = Long.parseLong(l.substring(6).trim());
            } catch (Exception ignored) { }
            if (p != null) p.onProgress(0, totalKB);
            return;
        }
        if (l.startsWith("PROG ")) {
            long done = 0;
            try {
                done = Long.parseLong(l.substring(5).trim());
            } catch (Exception ignored) { }
            if (p != null) p.onProgress(done, totalKB);
            return;
        }
        if (p != null && !l.isEmpty()) p.onLine(l);
    }

    private DsuBackup() {}
}
