#!/system/bin/sh
# KernelSU 模块操作按钮：刷新备份（当前主系统有指纹模板时才覆盖旧备份）。
MODDIR=${0%/*}
BK=/data/adb/dsu_bio_guard/backup

echo "== DSU 生物识别保护 =="
if [ -f /data/vendor_de/0/fpdata/finger_0_0.so ]; then
    BK="$BK" sh "$MODDIR/backup.sh"
    echo "已刷新主系统指纹/面容备份"
else
    echo "当前主系统没有指纹模板，保留旧备份不动"
    echo "请先在系统设置里录入指纹后再点一次"
fi
echo
echo "备份目录：$BK"
ls -la "$BK" 2>/dev/null | head -n 20
