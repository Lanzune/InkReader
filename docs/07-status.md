# 当前状态

**更新时间：** 2026-11-07
**当前阶段：** M3b2a 行内粗体/斜体已真机验证
**下一阶段：** M3b2b 对齐/脚注/内部链接，随后 M3c 语料回归

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0～M2 | ✅ 完成 | 真机通过，`m2-txt-reader` |
| M3a EPUB 文本 | ✅ 真机 | EPUB2/3、目录、元数据 |
| M3b1 EPUB 图片 | ✅ 真实书验证 | ImageSpan、懒解码、居中 |
| M3b2a 行内样式 | ✅ 真机样书 | 粗体/斜体 |
| M3b2b 脚注/对齐 | ⏳ 待开始 | 内部链接、基础 CSS |
| M3c 语料回归 | ⏳ 待开始 | ≥95% 可打开 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. M3b2b：对齐/缩进、脚注与内部链接。
2. 用真实 EPUB 复验样式/图片/目录/进度。
3. M3c：损坏样本、大文件、合并标签。

## 阻塞与待定

| 项 | 是否阻塞 | 说明 |
|---|---|---|
| 真实 EPUB 语料 | 是（M3c） | 需要项目所有者提供或确认样书 |
| 图片/CSS | 否 | M3b |
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
