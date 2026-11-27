# DSU 电源键延迟修复（KernelSU 模块）

## 症状

在 ColorOS 16 的 DSU 里，按电源键要等 **7-8 秒**屏幕才有反应（开屏 / 关屏都慢）。

## 原因

`com.android.nfc` 在 DSU 中开机即崩，并且无限重启：

```
java.lang.ExceptionInInitializerError
  at com.android.nfc.VendorNfcService.newVendorNfcService(VendorNfcService.java:383)
Caused by: java.util.NoSuchElementException
  at android.os.HwBinder.getService(Native Method)
  at vendor.oplus.hardware.nfc.V1_0.IOplusNfc.getService(IOplusNfc.java:88)
  at com.android.nfc.NfcChipManager.getChipVersion(NfcChipManager.java:191)
  at com.android.nfc.NfcChipManager.<clinit>(NfcChipManager.java:25)
```

DSU 的 vendor 只有 `android.hardware.nfc.INfc`（NXP AIDL）和 `nfcextnsservice`，
**没有** `vendor.oplus.hardware.nfc@1.0::IOplusNfc` 这个 HIDL 服务，
所以 `HwBinder.getService` 抛 `NoSuchElementException`，
发生在静态初始化里 → 应用每次启动必崩。

而 `com.android.nfc` 带 `PERSISTENT` 标记，AMS 会不停重启它：

```
ActivityManager: Start proc 4713:com.android.nfc/1027 for restart com.android.nfc
```

实测 **8 秒内崩溃 64 次**，每次都要 fork 进程、写 DropBox、刷日志，
CPU 和 system_server 线程被占满，输入事件排队 5 秒以上才被处理，
表现就是电源键「按了没反应，过 7-8 秒才好」。

## 修复

把 `com.android.nfc` 从 user 0 移除，AMS 不再重启它，崩溃循环终止：

```sh
pm disable com.android.nfc
pm uninstall --user 0 com.android.nfc
settings put secure nfc_enabled 0
```

实测修复后 8 秒内崩溃数从 64 → **0**，日志量从 13555 行/8秒 降到 4318 行/8秒。

## 安装

KernelSU / SukiSU 模块页 → 从本地安装 → 选 `dsu_nfc_crashfix-v1.0.zip` → 重启。

安装脚本会立即应用修复，重启后 `service.sh` 会再确认一次。

## 副作用

DSU 内 **NFC 不可用**（本来也是崩溃状态，无法使用）。

需要恢复 NFC：

- 卸载模块（`uninstall.sh` 会自动 `pm install-existing`），或
- 手动执行 `pm install-existing --user 0 com.android.nfc`、`pm enable com.android.nfc`

## 文件

| 文件 | 作用 |
|---|---|
| `service.sh` | 开机后确保 NFC 不启动（幂等） |
| `action.sh` | 模块页「操作」：立即修复并查看日志 |
| `uninstall.sh` | 卸载时恢复 NFC |
| `customize.sh` | 安装时立即修复 |
| `disable`（可选） | 存在此文件时模块跳过处理 |