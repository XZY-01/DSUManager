# 第三方组件

## Shizuku SDK 12.2.0

- 项目：https://github.com/RikkaApps/Shizuku
- 许可：Apache License 2.0
- 来源：Maven Central `dev.rikka.shizuku:api`、`:provider`、`:aidl`、`:shared` 12.2.0
- 文件：`app/libs/api-12.2.0.jar`、`provider-12.2.0.jar`、`aidl-12.2.0.jar`、`shared-12.2.0.jar`

选择 12.2.0 是因为 13.x 移除了 `Shizuku.newProcess`，本项目改为
`IShizukuService.newProcess`（AIDL）执行 shell。

## 参考

- DSU Sideloader：https://github.com/VegaBobo/DSU-Sideloader
  - 原生安装通过系统 `com.android.dynsystem` 的 `VerificationActivity` 完成，思路参考该项目
- KernelSU：https://github.com/tiann/KernelSU
- Magisk：https://github.com/topjohnwu/Magisk
- APatch：https://github.com/bmax121/APatch
