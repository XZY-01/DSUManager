# 更新日志

本文件按版本倒序记录。每个版本的 APK 位于 `apk/` 目录，并对应 GitHub Releases。

## v2.3（2026-11）
- 修复 DSU 环境中主系统被误显示为“运行中”（兼容 `true` 布尔属性）
- 设置页新增 GitHub 仓库地址跳转

## v2.2（2026-11）
- 2.1.12 合并为 2.2 正式版
- 主系统卡片新增重启按钮：主系统中为「重启手机」，DSU 中为「重启到主系统」
- DSU 状态卡不再重复显示「重启主系统」，统一由主系统卡片操作

## v2.1.11（2026-11）
- 移除应用内置的指纹 / 面容备份功能（实测无效）
- 生物识别保护改由独立的 KernelSU 模块 `dsu_bio_guard` 实现
- 模块 zip：`modules/dsu_bio_guard-v1.0.zip`

## v2.1.10（2026-11）
- DSU 状态卡恢复「重启到 DSU」按钮：可直接选槽、启用并重启（仅本次启动）
- 无槽位时按钮置灰；多槽位时弹窗选择

## v2.1.9（2026-11）
- 新增「生物识别保护」：启用 DSU 槽 / 打开应用时自动备份主系统指纹、面容模板与 persist 校准
- 回到主系统后若检测到指纹 / 面容模板缺失，可一键恢复备份并重启
- 修复从澎湃 DSU 返回主系统后指纹 / 面容丢失的问题
- APK：`DSUManager-v2.1.9.apk`

## v2.1.8（2026-11）
- 修复 zip/tar 只能识别 system 分区的问题：现在支持压缩包内的多分区镜像
  （system / system_ext / product / vendor / odm / vendor_dlkm / system_dlkm / mi_ext）
- 压缩包内含 super.img 时自动按 super 安装；含多个分区镜像时逐分区安装
- APK：`DSUManager-v2.1.8.apk`

## v2.1.7（2026-11）
- 修复「无论选哪种安装方式都走原生安装」：软件安装与原生安装恢复各自动作
- 软件安装失败时提供「改用原生安装」重试按钮（仅单个 system.img）
- 清理失败安装残留的槽位后再重试
- APK：`DSUManager-v2.1.7.apk`

## v2.1.6（2026-11）
- 修复单个 system.img 软件安装失败（KernelSU 下 gsi_tool 提交镜像数据报 EPERM）
- 单 system 镜像自动改用系统 DSU 安装器；软件安装仅用于 super.img / 整包多分区
- 无 Root + Shizuku 也可安装单个 system.img
- APK：`DSUManager-v2.1.6.apk`

## v2.1.5（2026-11）
- 安装成功后自动清理安装缓存（原生安装与软件安装均适用）
- APK：`DSUManager-v2.1.5.apk`

## v2.1.4（2026-11）
- 修复更改主题色后自动安装测试镜像的问题（移除隐藏测试入口）
- APK：`DSUManager-v2.1.4.apk`

## v2.1.3（2026-11）
- 原生安装弹窗移除“无需 Root / Shizuku 授权”说明文字
- APK：`DSUManager-v2.1.3.apk`

## v2.1.2（2026-11）
- 原生安装成功弹窗不再显示压缩包保存路径
- APK：`DSUManager-v2.1.2.apk`

## v2.1.1（2026-11）
- 原生安装完成后自动删除 `Download/DSUManager/` 下的临时 `.img.gz` 压缩包
- APK：`DSUManager-v2.1.1.apk`

## v2.1.0（2026-11）
- **原生安装改为 DSU Sideloader 方式**：镜像压缩成 `.img.gz` 后调起系统
  `com.android.dynsystem` 完成安装
- 原生安装**无需 Root**：开启无 Root 模式并授权 Shizuku 即可安装单个 `system.img`
- 软件安装继续使用 `gsi_tool` 分段管道（需 Root，支持 super/整包）
- 主界面显示「Shizuku 免 Root」，无 Root 时也显示安装按钮
- APK：`DSUManager-v2.1.0.apk`

## v2.0.4（2026-11）
- 修复设置页两个开关需要点多次才有反应（开关不再拦截触摸，整行单击生效）
- 收紧主页 DSU 槽四个按钮（启动一次 / 每次启动 / 禁用 / 删除）的间距
- APK：`DSUManager-v2.0.4.apk`

## v2.0.3（2026-11）
- 精简安装成功弹窗：只保留槽名与分区数
- 删除安装失败弹窗中的“日志未保存”提示
- APK：`DSUManager-v2.0.3.apk`

## v2.0.2（2026-11）
- 安装日志不再自动写入应用目录，改为仅内存保存、手动通过 SAF 导出
- 更新日志入口移入「关于」选项卡
- APK：`DSUManager-v2.0.2.apk`

## v2.0.1（2026-11）
- 引入 Shizuku SDK：无 Root 时可通过 Shizuku 只读查看槽位
- 恢复开发者头像显示
- 当前系统显示系统名（HyperOS / ColorOS / Android）而不是设备名
- 设置页槽位管理缓存秒开
- 安装完成后可通过 SAF 保存安装日志
- 修复环境识别与主系统状态误判
- APK：`DSUManager-v2.0.1.apk`

## v2.0（2026-11）
- 更名 **DSU Manager**
- Material 3 风格，支持 Monet 动态取色与自定义主题色
- 安装格式识别：system / super / zip / tar / gzip / sparse（zst/xz 需系统命令）
- super 与 system 安装入口合并，保留整包文件夹安装
- 完整安装日志 + 成功/失败弹窗 + 失败原因
- 安装细节开关、安装域（软件 / 原生）
- 无 Root 只读模式（实验）
- 兼容 Android 11~17
- 修复环境识别与主系统状态误判
- APK：`DSUManager-v2.0.apk`

## v1.9.2 / v1.9.1 / v1.9（2026-11）
- 设置页与主界面缓存秒开，排序立即生效
- 右上角三点菜单；开发者头像
- APK：`DSUManager-v1.9.2.apk`、`DSUManager-v1.9.1.apk`、`DSUManager-v1.9.apk`

## v1.8（2026-11）
- 应用设置页；开发者署名；动态取色
- APK：`DSUManager-v1.8.apk`

## v1.7.2 / v1.7.1 / v1.7（2026-11）
- 独立设置页；修复重命名交互
- APK：`DSUManager-v1.7.2.apk`、`DSUManager-v1.7.1.apk`、`DSUManager-v1.7.apk`

## v1.6（2026-11）
- 槽位排序
- APK：`DSUManager-v1.6.apk`

## v1.5（2026-11）
- 调整 userdata 大小
- APK：`DSUManager-v1.5.apk`

## v1.4（2026-11）
- super.img 自动拆分
- APK：`DSUManager-v1.4.apk`

## v1.3.2 / v1.3.1 / v1.3（2026-11）
- 整包 ROM 多分区安装
- APK：`DSUManager-v1.3.2.apk`、`DSUManager-v1.3.1.apk`、`DSUManager-v1.3.apk`

## v1.2.3 / v1.2（2026-11）
- 安装稳定性修复、图标
- APK：`DSUManager-v1.2.3.apk`、`DSUManager-v1.2.apk`

## v1.1（2026-11）
- 早期改进版本
- APK：`DSUManager-v1.1.apk`

## v1.0（2026-11）
- 初版多 DSU 槽管理
- APK：`DSUManager-v1.0.apk`

