# 当前状态

**更新时间：** 2026-11-09
**当前阶段：** M9.6 完成：目录层级、内部链接与脚注（0.9.3）
**下一阶段：** 多插件安装管理、发布检查、低端机后续验收

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0～M2 | ✅ 完成 | 真机通过，`m2-txt-reader` |
| M3 EPUB | ✅ 完成 | 标签 `m3-epub` |
| M4 PDF/HTML/MD | ✅ 完成 | 标签 `m4-docs` |
| M5 阅读体验 | ✅ 完成 | 标签 `m5-experience` |
| M6 插件 SDK | ✅ 完成 | 标签 `m6-plugins` |
| M7 发布/许可 | ✅ 完成 | 0.7.2，AGPL-3.0-only |
| M8a～M8d | ✅ 完成 | 0.8.0–0.8.5 打开速度与大书安全模式 |
| M9.1 百分比内核 | ✅ 0.9.0 真机 | 关闭页插件时约 200 ms 显示百分比 |
| M9.2 页码插件 | ✅ 0.9.0 真机 | 有页索引时立即显示 `30/4336` |
| M9.4 章节分页 | ✅ 0.9.1 真机 | 部/章从新页开始，部+章连续标题同页；45 个边界 |
| M9.5 EPUB 排版 | ✅ 0.9.2 真机 | 外部 CSS、标题居中、章首引语、2em 缩进 |
| M9.6 导航/脚注 | ✅ 0.9.3 真机 | 目录层级、跨文件内部链接、行内脚注、分隔线、双向跳转 |
| M9.3 多插件/发布 | ⏳ 下一步 | `.inkp` 多插件管理、体积/对齐/升级检查 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. M9.3：多插件并存与 `.inkp` 页码插件安装，不再只加载内置插件。
2. 发布检查：release/Debug 构建、签名、体积、16 KB 对齐、权限、升级安装。
3. 低端机（有硬件时）和长期阅读验收。

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
