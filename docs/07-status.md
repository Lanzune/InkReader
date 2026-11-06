# 当前状态

**更新时间：** 2026-11-06
**当前阶段：** M2b 设置/亮度/编码已构建，等待真机验证
**下一阶段：** M2c 回归、优化、合并并推送

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0 骨架 | ✅ 完成 | Android 16 / 一加 Ace 5 |
| M1 垂直切片 | ✅ 真机通过 | TXT/编码/QuickJS/翻页 |
| M1d 沉浸式阅读 | ✅ 真机通过 | 已合并 main |
| M2a 章节与进度 | ✅ 真机通过 | 已合并 main |
| M2b 设置与编码 | 🚧 待真机 | 主题/字号/行距/边距/亮度/手动编码 |
| M2c 验收 | ⏳ 待开始 | 回归、滑杆性能、合并 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. 安装 `artifacts/m2b/inkreader-0.2.1-m2b-debug.apk`，验证设置、亮度、手动编码。
2. 通过后做 M2c 回归（章节/进度/导航栏/主题/编码）。
3. 合并 `feat/m2b-settings`，打 `m2-txt-reader` 标签并推送。

## 阻塞与待定

| 项 | 是否阻塞 | 说明 |
|---|---|---|
| M2b 真机验证 | 是 | 设置/亮度/编码需要项目所有者确认 |
| 滑杆重排性能 | 否 | M2c 视真机表现做防抖 |
| 正式名称/包名 | 否 | 暂用 `com.inkreader.app` |
| 签名密钥 | 否 | M7 前准备 |

## Git 推送状态

已用官方 GitHub CLI 完成设备授权，token 存在系统 keyring，项目内没有 token。

| Ref | Commit |
|---|---|
| `origin/main` | `cdb697f`（含 M1d 合并） |
| `origin/feat/m2-txt-reader` | `cc51859` |
| `origin/feat/m1-vertical-slice` | `8716def` |
| `m1-vertical-slice` | `56bca31` |
| `m1d-immersive` | `cdb697f` |
| `m2a-build` | `cc51859` |

M2a 真机通过后，再把 `feat/m2-txt-reader` 合并到 `main` 并推送。

## 最近一次环境结论

- Android Studio 2026.2.2.8 已安装（scoop）
- Android SDK：`C:\Users\34553\AppData\Local\Android\Sdk`
- JDK 21.0.2 可用；默认 `JAVA_HOME` 是 JDK 25，构建时需覆盖
- Gradle 8.14 分发已缓存；无全局 gradle
- Git 2.56，身份 `Lanzune <3455312265@qq.com>`
- 无系统镜像/AVD；adb 在 SDK 内但不在 PATH

详见 [环境审计](05-environment.md)。
