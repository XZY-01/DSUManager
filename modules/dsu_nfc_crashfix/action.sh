#!/system/bin/sh
# KernelSU 模块「操作」：立即重新应用修复并显示状态
MODDIR=${0%/*}
PKG=com.android.nfc

echo "== DSU 电源键延迟修复 =="
if [ -f "$MODDIR/disable" ]; then
    echo "模块已被 disable 文件停用"
    echo "删除 $MODDIR/disable 后重新执行即可"
    exit 0
fi

echo "当前状态："
if pm list packages --user 0 2>/dev/null | grep -q "package:$PKG"; then
    echo "  $PKG 仍对 user 0 可见，正在修复…"
    pm disable "$PKG" >/dev/null 2>&1
    pm uninstall --user 0 "$PKG" >/dev/null 2>&1
    settings put secure nfc_enabled 0 >/dev/null 2>&1
    settings put global nfc_on 0 >/dev/null 2>&1
    for p in $(pidof $PKG 2>/dev/null); do kill -9 "$p" 2>/dev/null; done
    echo "  已修复"
else
    echo "  $PKG 已对 user 0 移除（正常，说明修复生效）"
fi

echo
echo "近 30 行日志："
tail -n 30 /data/local/tmp/dsu_nfc_crashfix.log 2>/dev/null