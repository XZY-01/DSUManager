#!/system/bin/sh
umask 022

ui_print "****************************************"
ui_print "  DSU 电源键延迟修复  v1.0"
ui_print "  修复 NFC 崩溃循环导致的按键卡顿"
ui_print "****************************************"

chmod 0755 "$MODPATH/service.sh" "$MODPATH/action.sh" "$MODPATH/uninstall.sh" 2>/dev/null

ui_print "- 立即应用修复…"
pm disable com.android.nfc >/dev/null 2>&1
pm uninstall --user 0 com.android.nfc >/dev/null 2>&1
settings put secure nfc_enabled 0 >/dev/null 2>&1
settings put global nfc_on 0 >/dev/null 2>&1
for p in $(pidof com.android.nfc 2>/dev/null); do kill -9 "$p" 2>/dev/null; done

if pm list packages --user 0 2>/dev/null | grep -q 'package:com.android.nfc'; then
    ui_print "! 自动修复未生效，重启后由 service.sh 再试"
else
    ui_print "- 已停止 com.android.nfc（NFC 崩溃循环已终止）"
fi

ui_print "- 安装完成，重启手机确保干净状态"
ui_print "- 若需要 NFC：卸载本模块会自动恢复，或手动执行"
ui_print "  pm install-existing --user 0 com.android.nfc"