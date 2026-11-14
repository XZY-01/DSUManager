# DSU 生物识别保护（KernelSU 模块）

用于解决：从澎湃 / 其他 DSU 重启回主系统后，主系统指纹、面容丢失的问题。

## 原理

DSU 启动时可能改写共享的 persist 指纹校准数据；本模块在主系统开机早期
（`post-fs-data`，指纹服务启动前）检查并恢复：

- 指纹模板：`/data/vendor_de/0/fpdata`
- 面容模板：`/data/vendor_ce/0/facedata`、`/data/vendor_de/0/facedata`
- 设置：`/data/system/users/0/settings_fingerprint_*.xml`、`settings_face_*.xml`
- persist 校准：`/mnt/vendor/persist/{fingerprint,fpc,goodix,mi_fp}`

备份存放在 `/data/adb/dsu_bio_guard/backup`（主系统 /data，DSU 独立 userdata 不会覆盖）。

## 安装

1. KernelSU / SukiSU 模块页 → 从本地安装 → 选择 `dsu_bio_guard-v1.0.zip`
2. 重启手机
3. 安装时已自动备份当前指纹 / 面容；之后进 DSU 再回主系统会自动恢复

## 刷新备份

在 KernelSU 模块页点本模块的「操作」按钮，会重新备份当前主系统数据。
重新录入指纹 / 面容后建议点一次。

## 注意

- 只恢复“缺失或空”的模板，不会覆盖你重新录入的新指纹；
- 如果指纹已经丢失且没有旧备份，需要先重新录入一次，再点「操作」刷新备份；
- 卸载模块会删除备份目录。
