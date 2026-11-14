#!/system/bin/sh
# 安装脚本：初始化备份目录并立即备份当前主系统生物识别数据。
umask 022

ui_print "****************************************"
ui_print "  DSU 生物识别保护  v1.0"
ui_print "  重启 DSU 前备份，回主系统自动恢复"
ui_print "****************************************"

chmod 0755 "$MODPATH/backup.sh" "$MODPATH/post-fs-data.sh" \
    "$MODPATH/boot-completed.sh" "$MODPATH/action.sh" 2>/dev/null

mkdir -p /data/adb/dsu_bio_guard/backup
BK=/data/adb/dsu_bio_guard/backup
export BK
sh "$MODPATH/backup.sh"

if [ -f "$BK/.complete" ]; then
    ui_print "- 已备份当前主系统指纹 / 面容数据"
    ui_print "- 开机时会在指纹服务启动前自动恢复缺失的数据"
else
    ui_print "! 备份失败：请确认当前系统已有指纹/面容数据"
fi

ui_print "- 安装完成，重启手机生效"
ui_print "- 在 KernelSU 模块页可点「操作」刷新备份"
