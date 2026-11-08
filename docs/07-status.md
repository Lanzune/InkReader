# 当前状态

**更新时间：** 2026-11-08
**当前阶段：** M8b EPUB 预览加载已真机验证，首屏约 2.2 s
**下一阶段：** M8b.1 增量章节/目标章定位

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0～M2 | ✅ 完成 | 真机通过，`m2-txt-reader` |
| M3 EPUB | ✅ 完成 | 标签 `m3-epub` |
| M4 PDF/HTML/MD | ✅ 完成 | 标签 `m4-docs` |
| M5 阅读体验 | ✅ 完成 | 标签 `m5-experience` |
| M6 插件 SDK | ✅ 完成 | 标签 `m6-plugins` |
| M7 发布/许可 | ✅ 完成 | 0.7.2，AGPL-3.0-only |
| M8a 首屏优化 | ✅ 0.8.0 | 8.4 s → 约 2.2 s 首屏 |
| M8a.1 图片预加载 | ✅ 0.8.1 真机 | 加载态→完整页，不再显示文字半成品 |
| M8b EPUB 预览 | ⏳ 0.8.2 | ZipFile 随机读 + 前 8 章；首屏 2.2s |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. M8b：`ChapterSource`、EPUB central directory 随机读、图片按需。
2. M8c：TXT/HTML/MD 流式、PDF 基准、持久缓存。
3. M8d：低端机、全格式验收；目标大书首屏 <1 秒。

## 阻塞与待定

| 项 | 是否阻塞 | 说明 |
|---|---|---|
| 真实 PDF 样本 | 是（M4c） | 需要项目所有者提供或确认 |
| HTML/Markdown | 否 | M4b |
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
