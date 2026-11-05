# 环境与工具链审计

**审计日期：** 2026-11-05  
**审计方式：** 严格只读；仅枚举目录、读取版本/配置文件、执行 `java -version` 和 `git --version`。未写入任何外部目录，未启动模拟器、Gradle daemon 或 adb server。  
**审计范围：** 项目所有者批准的 Android Studio、SDK、JDK、Gradle 缓存、Git、scoop 安装目录和 Git 身份配置。

## 1. 结论

**这台机器已经具备开始 M0 的条件。** Android Studio、Android SDK、NDK、JDK 21、Gradle 分发缓存和 Git 都已安装；缺的是项目级配置、仓库远端、模拟器/真机验证条件，以及尚未确定的包名/正式名称。

之前“只看到 JDK 25、没有 Android SDK”的结论，是因为只检查了工作区和环境变量。实际 SDK 和 Android Studio 安装在用户 profile/scoop 下。

## 2. 已安装项

| 项目 | 状态 | 路径/版本 |
|---|---|---|
| Android Studio | ✅ | `C:\Users\34553\scoop\apps\android-studio\current`，版本 `AI-262.10968.63.2622.16730706`（2026.2.2.8） |
| Android SDK | ✅ | `C:\Users\34553\AppData\Local\Android\Sdk` |
| SDK Platforms | ✅ | `android-34`、`android-35`、`android-36`、`android-36.1` |
| Build Tools | ✅ | `35.0.0`、`36.1.0`、`37.0.0` |
| Command-line Tools | ✅ | `cmdline-tools\latest` |
| NDK | ✅ | `28.2.13676358`、`30.0.14904198` |
| Platform Tools / adb | ✅ | `platform-tools\adb.exe` |
| Emulator 二进制 | ✅ | `emulator\emulator.exe` |
| 模拟器系统镜像 | ❌ | `system-images` 不存在 |
| AVD | ❌ | `C:\Users\34553\.android\avd` 为空 |
| JDK 21 | ✅ | `C:\Users\34553\scoop\apps\openjdk21\current`，OpenJDK 21.0.2 |
| 默认 JAVA_HOME | ⚠️ | 指向 Temurin JDK 25；Gradle 构建时需覆盖为 JDK 21 |
| Gradle 分发 | ✅ | `~/.gradle/wrapper/dists/gradle-8.14-all/.../gradle-8.14` |
| 全局 gradle 命令 | ❌ | PATH 中没有 `gradle`；使用项目 wrapper 或缓存分发即可 |
| Git | ✅ | 2.56.0.windows.4 |
| Git 身份 | ✅ | `Lanzune <3455312265@qq.com>`，`init.defaultBranch=main` |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | ❌ | 未设置；用项目 `local.properties` 固定 SDK 路径 |
| GitHub 远端 | ❌ | 尚未提供仓库地址 |

## 3. 可直接使用的命令

```powershell
# JDK 21（构建用）
C:\Users\34553\scoop\apps\openjdk21\current\bin\java.exe -version

# Android SDK
C:\Users\34553\AppData\Local\Android\Sdk\platform-tools\adb.exe version
C:\Users\34553\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat --list

# Android Studio
C:\Users\34553\scoop\apps\android-studio\current\bin\studio64.exe

# Gradle 8.14 分发（示例路径）
C:\Users\34553\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat
```

## 4. M0 需要补齐的项目级配置

1. 生成项目 Gradle wrapper（目标 Gradle 8.14）。
2. 创建 `local.properties`，设置 `sdk.dir=C:\\Users\\34553\\AppData\\Local\\Android\\Sdk`；该文件不提交。
3. 构建时设置 `JAVA_HOME` 为 JDK 21，不使用默认 JDK 25。
4. 选择并锁定 AGP/Kotlin/Compose 版本；以实际依赖解析和构建结果为准。
5. 建立最小 `app` 模块、`kernel/api`、`kernel/core` 的 Gradle 结构。
6. 构建 debug APK，检查 manifest 中没有 `INTERNET` 权限。
7. 初始化 git 本地仓库，完成 M0 首次提交。
8. 项目所有者提供 GitHub 仓库信息后配置 `origin` 并推送。

## 5. 运行时验证缺口

| 缺口 | 影响 | 解决方案 |
|---|---|---|
| 无模拟器系统镜像 | 无法在模拟器启动应用 | 安装 system image，或使用真实手机 |
| 无 AVD | 无现成虚拟设备 | 创建 AVD，或使用真实手机 |
| 未验证 adb 设备 | 不知道当前是否有手机连接 | 项目所有者连接手机并授权 USB 调试 |
| 无 GitHub 远端 | 不能推送 | 提供仓库地址/账号后配置 |
| 包名/正式名称未定 | 影响 applicationId 和目录重命名 | M0 使用临时 `com.inkreader.app`，发布前确定 |

## 6. 环境相关风险

- 默认 JDK 25 可能不被目标 AGP 支持；所有构建命令显式使用 JDK 21。
- 没有全局 Gradle，不影响项目；wrapper 建立后应优先使用 `gradlew`。
- 模拟器可用但无镜像；不要在未确认前下载大体积系统镜像。
- `C:` 盘剩余空间未可靠测出；安装 system image 前需要项目所有者确认空间。
- Android Studio 的路径不在 PATH；CLI 能完成构建，IDE 只是可选辅助。

## 7. 只读审计声明

本次审计没有修改 Android SDK、Android Studio、JDK、`.gradle`、`.android`、Git 配置或任何项目外文件；只读取了目录结构、版本文件和 Git 全局身份。
