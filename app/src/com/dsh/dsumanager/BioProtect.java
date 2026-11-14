package com.dsh.dsumanager;

/**
 * 主系统指纹 / 面容保护：
 * 在重启进入 DSU 之前，用 root 备份主系统的生物识别模板、设置与 persist 校准数据；
 * 回到主系统后如果发现模板丢失，可用备份恢复并重启。
 *
 * 备份放在 /data/local/tmp/dsu_bio_backup（与主 /data 同分区，cp -a 可保留权限；
 * DSU 使用独立 userdata，不会改动主 /data 里的备份）。
 */
public final class BioProtect {

    public static final String BACKUP_DIR = "/data/local/tmp/dsu_bio_backup";

    private static final String BACKUP_SCRIPT =
            "BK=" + BACKUP_DIR + "\n"
            + "rm -rf \"$BK\"; mkdir -p \"$BK\" || { echo BIO_BACKUP_FAIL_MKDIR; exit 1; }\n"
            + "cp -a /data/vendor_de/0/fpdata \"$BK/fpdata\" 2>/dev/null\n"
            + "cp -a /data/vendor_ce/0/facedata \"$BK/facedata_ce\" 2>/dev/null\n"
            + "cp -a /data/vendor_de/0/facedata \"$BK/facedata_de\" 2>/dev/null\n"
            + "cp -a /data/vendor/fpdata \"$BK/vendor_fpdata\" 2>/dev/null\n"
            + "cp -a /mnt/vendor/persist/fingerprint \"$BK/persist_fingerprint\" 2>/dev/null\n"
            + "cp -a /mnt/vendor/persist/fpc \"$BK/persist_fpc\" 2>/dev/null\n"
            + "cp -a /mnt/vendor/persist/goodix \"$BK/persist_goodix\" 2>/dev/null\n"
            + "cp -a /mnt/vendor/persist/mi_fp \"$BK/persist_mi_fp\" 2>/dev/null\n"
            + "cp -a /data/system/users/0/settings_fingerprint_*.xml \"$BK/\" 2>/dev/null\n"
            + "cp -a /data/system/users/0/settings_face_*.xml \"$BK/\" 2>/dev/null\n"
            + "cp -a /data/system_ce/0/FaceUnlockTrack.xml \"$BK/FaceUnlockTrack.xml\" 2>/dev/null\n"
            + "cp -a /data/system_de/0/security/FaceUnlockMisight.xml \"$BK/FaceUnlockMisight.xml\" 2>/dev/null\n"
            + "touch \"$BK/.complete\"\n"
            + "echo BIO_BACKUP_OK\n";

    private static final String RESTORE_SCRIPT =
            "BK=" + BACKUP_DIR + "\n"
            + "[ -f \"$BK/.complete\" ] || { echo BIO_NO_BACKUP; exit 1; }\n"
            + "[ -d \"$BK/fpdata\" ] && { rm -rf /data/vendor_de/0/fpdata; cp -a \"$BK/fpdata\" /data/vendor_de/0/fpdata; }\n"
            + "[ -d \"$BK/facedata_ce\" ] && { rm -rf /data/vendor_ce/0/facedata; cp -a \"$BK/facedata_ce\" /data/vendor_ce/0/facedata; }\n"
            + "[ -d \"$BK/facedata_de\" ] && { rm -rf /data/vendor_de/0/facedata; cp -a \"$BK/facedata_de\" /data/vendor_de/0/facedata; }\n"
            + "[ -d \"$BK/vendor_fpdata\" ] && { rm -rf /data/vendor/fpdata; cp -a \"$BK/vendor_fpdata\" /data/vendor/fpdata; }\n"
            + "[ -d \"$BK/persist_fingerprint\" ] && { rm -rf /mnt/vendor/persist/fingerprint; cp -a \"$BK/persist_fingerprint\" /mnt/vendor/persist/fingerprint; }\n"
            + "[ -d \"$BK/persist_fpc\" ] && { rm -rf /mnt/vendor/persist/fpc; cp -a \"$BK/persist_fpc\" /mnt/vendor/persist/fpc; }\n"
            + "[ -d \"$BK/persist_goodix\" ] && { rm -rf /mnt/vendor/persist/goodix; cp -a \"$BK/persist_goodix\" /mnt/vendor/persist/goodix; }\n"
            + "[ -d \"$BK/persist_mi_fp\" ] && { rm -rf /mnt/vendor/persist/mi_fp; cp -a \"$BK/persist_mi_fp\" /mnt/vendor/persist/mi_fp; }\n"
            + "cp -a \"$BK\"/settings_fingerprint_*.xml /data/system/users/0/ 2>/dev/null\n"
            + "cp -a \"$BK\"/settings_face_*.xml /data/system/users/0/ 2>/dev/null\n"
            + "[ -f \"$BK/FaceUnlockTrack.xml\" ] && cp -a \"$BK/FaceUnlockTrack.xml\" /data/system_ce/0/FaceUnlockTrack.xml 2>/dev/null\n"
            + "[ -f \"$BK/FaceUnlockMisight.xml\" ] && cp -a \"$BK/FaceUnlockMisight.xml\" /data/system_de/0/security/FaceUnlockMisight.xml 2>/dev/null\n"
            + "restorecon -R /data/vendor_de/0 /data/vendor_ce/0 /data/vendor/fpdata /mnt/vendor/persist 2>/dev/null\n"
            + "echo BIO_RESTORE_OK\n";

    private static final String CHECK_SCRIPT =
            "BK=" + BACKUP_DIR + "\n"
            + "[ -f \"$BK/.complete\" ] && echo HAVE_BACKUP || echo NO_BACKUP\n"
            + "FPBK=no; [ -n \"$(ls -A \"$BK/fpdata\" 2>/dev/null)\" ] && FPBK=yes\n"
            + "FACEBK=no; [ -n \"$(ls -A \"$BK/facedata_ce\" 2>/dev/null)$(ls -A \"$BK/facedata_de\" 2>/dev/null)\" ] && FACEBK=yes\n"
            + "FP=yes; if [ \"$FPBK\" = yes ] && [ ! -f /data/vendor_de/0/fpdata/finger_0_0.so ]; then FP=no; fi\n"
            + "FACE=yes; if [ \"$FACEBK\" = yes ] && [ -z \"$(ls -A /data/vendor_ce/0/facedata 2>/dev/null)$(ls -A /data/vendor_de/0/facedata 2>/dev/null)\" ]; then FACE=no; fi\n"
            + "echo FP=$FP FACE=$FACE FPBK=$FPBK FACEBK=$FACEBK\n";

    public static boolean backup() {
        RootShell.Result r = RootShell.runRoot(BACKUP_SCRIPT, 120);
        return r != null && r.out != null && r.out.contains("BIO_BACKUP_OK");
    }

    public static boolean restore() {
        RootShell.Result r = RootShell.runRoot(RESTORE_SCRIPT, 120);
        return r != null && r.out != null && r.out.contains("BIO_RESTORE_OK");
    }

    public static boolean hasBackup() {
        RootShell.Result r = RootShell.runRoot(CHECK_SCRIPT, 60);
        return r != null && r.out != null && r.out.contains("HAVE_BACKUP");
    }

    /** 主系统是否至少缺少一类生物识别数据（有备份时才值得恢复）。 */
    public static boolean missing() {
        RootShell.Result r = RootShell.runRoot(CHECK_SCRIPT, 60);
        if (r == null || r.out == null) return false;
        String out = r.out;
        boolean fp = out.contains("FP=yes");
        boolean face = out.contains("FACE=yes");
        return !fp || !face;
    }

    private BioProtect() {}
}
