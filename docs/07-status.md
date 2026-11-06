# 当前状态

**更新时间：** 2026-11-06
**当前阶段：** M2b3 首尾行裁切修复已构建，等待真机验证
**下一阶段：** 通过后 M2c 回归、合并、进入 M3 EPUB

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0 骨架 | ✅ 完成 | Android 16 / 一加 Ace 5 |
| M1 垂直切片 | ✅ 真机通过 | TXT/编码/QuickJS/翻页 |
| M1d 沉浸式阅读 | ✅ 真机通过 | 已合并 main |
| M2a 章节与进度 | ✅ 真机通过 | 已合并 main |
| M2b 设置与编码 | 🚧 待复验 | 滑杆/亮度/重排已修 |
| M2b3 首尾行裁切 | 🚧 待真机 | includePad、顶部/底部安全区 |
| M2c 验收 | ⏳ 待开始 | 回归、合并、推送 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. 安装 `artifacts/m2b3/inkreader-0.2.3-m2b3-debug.apk`，确认页首/页尾行完整。
2. 通过后做 M2c 回归，合并 `feat/m2b-settings`，打 `m2-txt-reader` 标签并推送。
3. 进入 M3 EPUB。

## 阻塞与待定

| 项 | 是否阻塞 | 说明 |
|---|---|---|
| M2b3 真机验证 | 是 | 首尾行完整性需要项目所有者确认 |
| M2b2 滑杆/亮度/漂移 | 待确认 | 已构建，需一并复验 |
| 正式名称/包名 | 否 | 暂用 `com.inkreader.app` |

## Git 推送状态

已用官方 GitHub CLI 完成设备授权，token 存在系统 keyring，项目内没有 token。

| Ref | Commit |
|---|---|
| `origin/main` | `43422e5`（含 M2a 合并） |
| `origin/feat/m2b-settings` | `da6879c` |
| `m2a-chapters` | `43422e5` |
| `m2b-build` | `237cd7a` |
| `m2b2-build` | `da6879c` |

M2b2 真机通过后，把 `feat/m2b-settings` 合并到 `main`，打 `m2-txt-reader` 标签并推送，然后进入 M3。

## 最近一次环境结论

- Android Studio 2026.2.2.8 已安装（scoop）
- Android SDK：`C:\Users\34553\AppData\Local\Android\Sdk`
- JDK 21.0.2 可用；默认 `JAVA_HOME` 是 JDK 25，构建时需覆盖
- Gradle 8.14 分发已缓存；无全局 gradle
- Git 2.56，身份 `Lanzune <3455312265@qq.com>`
- 无系统镜像/AVD；adb 在 SDK 内但不在 PATH

详见 [环境审计](05-environment.md)。
