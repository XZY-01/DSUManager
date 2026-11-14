#!/system/bin/sh
# 备份主系统指纹 / 面容模板、设置与 persist 校准。
# 用法：BK=/data/adb/dsu_bio_guard/backup sh backup.sh
BK="${BK:-/data/adb/dsu_bio_guard/backup}"

rm -rf "$BK"
mkdir -p "$BK" || exit 1

cp -a /data/vendor_de/0/fpdata "$BK/fpdata" 2>/dev/null
cp -a /data/vendor_ce/0/facedata "$BK/facedata_ce" 2>/dev/null
cp -a /data/vendor_de/0/facedata "$BK/facedata_de" 2>/dev/null
cp -a /data/vendor/fpdata "$BK/vendor_fpdata" 2>/dev/null

for d in fingerprint fpc goodix mi_fp; do
    [ -d "/mnt/vendor/persist/$d" ] && cp -a "/mnt/vendor/persist/$d" "$BK/persist_$d" 2>/dev/null
done

cp -a /data/system/users/0/settings_fingerprint_*.xml "$BK/" 2>/dev/null
cp -a /data/system/users/0/settings_face_*.xml "$BK/" 2>/dev/null
cp -a /data/system_ce/0/FaceUnlockTrack.xml "$BK/FaceUnlockTrack.xml" 2>/dev/null
cp -a /data/system_de/0/security/FaceUnlockMisight.xml "$BK/FaceUnlockMisight.xml" 2>/dev/null

touch "$BK/.complete"
echo "backup done: $BK"
