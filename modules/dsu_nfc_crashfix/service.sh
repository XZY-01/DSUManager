#!/system/bin/sh
# 开机后期修复：让 com.android.nfc 不再启动，终止崩溃循环。
# 崩溃原因：NfcChipManager.<clinit> 调用 vendor.oplus.hardware.nfc@1.0::IOplusNfc，
#          该 HIDL HAL 在 DSU 中不存在 → HwBinder.getService 抛 NoSuchElementException
#          → ExceptionInInitializerError → AMS 以 persistent 身份无限重启 → CPU 打满
MODDIR=${0%/*}
LOG=/data/local/tmp/dsu_nfc_crashfix.log
PKG=com.android.nfc

[ -f "$MODDIR/disable" ] && exit 0

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done
sleep 2

# 如果用户手动恢复了 NFC，就不重复处理
if pm list packages --user 0 2>/dev/null | grep -q "package:$PKG"; then
    echo "$(date '+%F %T') 处理 $PKG" >> "$LOG"
    pm disable "$PKG" >/dev/null 2>&1
    pm uninstall --user 0 "$PKG" >/dev/null 2>&1
    settings put secure nfc_enabled 0 >/dev/null 2>&1
    settings put global nfc_on 0 >/dev/null 2>&1
    echo "$(date '+%F %T') 完成" >> "$LOG"
fi

# 顺手清掉可能残留的崩溃进程
for p in $(pidof $PKG 2>/dev/null); do
    kill -9 "$p" 2>/dev/null
done

exit 0