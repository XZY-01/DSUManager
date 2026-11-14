#!/system/bin/sh
# 开机完成后：恢复 CE 面容数据（需要用户解锁后 CE 存储可用），并刷新模板备份。
BK=/data/adb/dsu_bio_guard/backup

[ -f "$BK/.complete" ] || exit 0

# 面容 CE 数据
if [ -d "$BK/facedata_ce" ] && [ -n "$(ls -A "$BK/facedata_ce" 2>/dev/null)" ]; then
    if [ ! -d /data/vendor_ce/0/facedata ] || [ -z "$(ls -A /data/vendor_ce/0/facedata 2>/dev/null)" ]; then
        rm -rf /data/vendor_ce/0/facedata
        cp -a "$BK/facedata_ce" /data/vendor_ce/0/facedata
        chown -R system:system /data/vendor_ce/0/facedata 2>/dev/null
        restorecon -R /data/vendor_ce/0/facedata 2>/dev/null
    fi
fi

# 设置文件
for f in "$BK"/settings_fingerprint_*.xml "$BK"/settings_face_*.xml; do
    [ -f "$f" ] || continue
    dst="/data/system/users/0/$(basename "$f")"
    [ -f "$dst" ] || cp -a "$f" "$dst" 2>/dev/null
done

# 用户重新录入后（模板比备份新），刷新模板备份；persist 校准备份保持安装时状态
if [ -f /data/vendor_de/0/fpdata/finger_0_0.so ] && [ /data/vendor_de/0/fpdata/finger_0_0.so -nt "$BK/fpdata/finger_0_0.so" ]; then
    cp -a /data/vendor_de/0/fpdata/. "$BK/fpdata/" 2>/dev/null
fi

exit 0
