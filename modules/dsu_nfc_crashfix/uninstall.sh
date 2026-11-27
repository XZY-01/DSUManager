#!/system/bin/sh
# 卸载模块时恢复 NFC（若用户需要）
pm install-existing --user 0 com.android.nfc >/dev/null 2>&1
pm enable com.android.nfc >/dev/null 2>&1
rm -f /data/local/tmp/dsu_nfc_crashfix.log