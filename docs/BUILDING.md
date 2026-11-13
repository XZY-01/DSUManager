# 编译说明

本项目不使用 Gradle，直接用 Android SDK 构建工具手动打包，产物约 360 KB。

## 环境要求

| 组件 | 版本 / 说明 |
|---|---|
| JDK | 17+（`javac --release 11`） |
| Android SDK Build-Tools | 36.0.0（`aapt2`、`d8`、`zipalign`、`apksigner`） |
| Android Platform | android-37（`android.jar`） |
| Python | 3.x（仅用于把 classes.dex 打进 APK） |
| PowerShell | Windows PowerShell 5.1+ / PowerShell 7 |

Shizuku SDK 已放在 `app/libs/`（12.2.0 的 api / provider / aidl / shared 四个 jar），
无需联网即可编译。

## 一键构建

```powershell
cd app
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```

脚本会依次执行：

1. `aapt2 compile` 编译 `res/`
2. `aapt2 link` 生成 `build/base.apk` 与 `R.java`
3. `javac` 编译 Java 源码 + R.java
4. `d8` 生成 `classes.dex`（--min-api 30）
5. 用 Python 把 dex 塞进 APK
6. `zipalign` 对齐
7. 若不存在 `debug.keystore` 则自动生成，`apksigner` 签名
8. 输出 `apk/DSUManager-v<版本>.apk`

> 路径：脚本按自身位置推导项目目录，`ANDROID_HOME` / `ANDROID_SDK_ROOT` 未设置时使用
> `%LOCALAPPDATA%\Android\Sdk`。

## 修改版本号

- `app/build_apk.ps1` 中 `--version-code` / `--version-name` 与 `$outApk`
- `app/src/com/dsh/dsumanager/MainActivity.java` 页脚版本
- `app/src/com/dsh/dsumanager/SettingsActivity.java` 关于页版本与更新日志

## 目录说明

- `app/src/com/dsh/dsumanager/`：全部 Java 源码
  - `MainActivity` 主界面 / 安装流程
  - `SettingsActivity` 设置页
  - `DsuManager` 封装 `gsi_tool`（状态、安装、启停、改名、扩容）
  - `DsuSideloadInstaller` 原生安装（DSU Sideloader 方式）
  - `ImageFormatDetector` / `SuperImage` 镜像格式识别、LP 解析、sparse 转换
  - `ShizukuHelper` / `RootShell` shell 执行
  - `EnvDetector` 系统名与 DSU 环境检测
  - `ThemeUtil` MD3 调色板 / Monet
- `app/res/`：资源与图标
- `apk/`：历史版本产物

## 注意事项

- 修改 `AndroidManifest.xml` 中的 Shizuku provider 时保持 authorities 唯一
- 需要 `moe.shizuku.manager.permission.API_V23` 权限声明与 `ShizukuProvider`
- 如果换签名，覆盖安装旧版本会失败，需要先卸载
