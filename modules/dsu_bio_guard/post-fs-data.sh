#!/system/bin/sh
# 主系统开机早期（post-fs-data）恢复指纹模板与 persist 校准。
# 只恢复“缺失 / 空文件”，不会覆盖用户重新录入的新数据。
BK=/data/adb/dsu_bio_guard/backup
LOG=/data/adb/dsu_bio_guard/restore.log

[ -f "$BK/.complete" ] || exit 0

i=0
while [ $i -lt 20 ] && [ ! -d /data/vendor_de/0 ]; do
    sleep 1
    i=$((i + 1))
done

need_fp=0
if [ -f "$BK/fpdata/finger_0_0.so" ] && [ ! -f /data/vendor_de/0/fpdata/finger_0_0.so ]; then
    need_fp=1
fi
if [ -f "$BK/persist_goodix/cali_data.so" ]; then
    sz=$(stat -c %s /mnt/vendor/persist/goodix/cali_data.so 2>/dev/null)
    if [ -z "$sz" ] || [ "$sz" = "0" ]; then
        need_fp=1
    fi
fi

if [ "$need_fp" = "1" ]; then
    if [ -d "$BK/fpdata" ]; then
        rm -rf /data/vendor_de/0/fpdata
        cp -a "$BK/fpdata" /data/vendor_de/0/fpdata
    fi
    for d in fingerprint fpc goodix mi_fp; do
        if [ -d "$BK/persist_$d" ]; then
            rm -rf "/mnt/vendor/persist/$d"
            cp -a "$BK/persist_$d" "/mnt/vendor/persist/$d"
        fi
    done
    chown -R system:system /data/vendor_de/0/fpdata 2>/dev/null
    chown -R system:system /mnt/vendor/persist/fingerprint /mnt/vendor/persist/fpc \
        /mnt/vendor/persist/goodix /mnt/vendor/persist/mi_fp 2>/dev/null
    restorecon -R /data/vendor_de/0/fpdata 2>/dev/null
    restorecon -R /mnt/vendor/persist/fingerprint /mnt/vendor/persist/fpc \
        /mnt/vendor/persist/goodix /mnt/vendor/persist/mi_fp 2>/dev/null
    echo "fingerprint restored $(date)" >> "$LOG"
fi

# 设置文件丢失时补回
for f in "$BK"/settings_fingerprint_*.xml "$BK"/settings_face_*.xml; do
    [ -f "$f" ] || continue
    dst="/data/system/users/0/$(basename "$f")"
    [ -f "$dst" ] || cp -a "$f" "$dst" 2>/dev/null
done

exit 0
