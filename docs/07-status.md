# 当前状态

**更新时间：** 2026-11-06
**当前阶段：** M1 — 垂直切片已构建，等待真机验证
**下一阶段：** M1 真机验收通过后进入 M2（TXT 完整化）

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0 骨架 | ✅ 完成 | 真机启动通过（一加 Ace 5 / ColorOS 16.0.10） |
| 技术方案报告 | ✅ 完成 | Word 46 页 + Markdown 主稿 |
| 项目文件夹 | ✅ 完成 | `inkreader/`，内部代号 |
| 目录骨架 | ✅ 完成 | app/kernel/formats/plugin-host/sdk 等 |
| 文档体系 | ✅ 第一批完成 | 章程、基线、模块地图、路线图、工作流、环境、质量 |
| 环境审计 | ✅ 完成（只读） | SDK/NDK/JDK21/Gradle/Git 均可用 |
| 本地 git | ✅ 完成 | `main` + `feat/m1-vertical-slice`；标签 `m0-build`、`m0-complete`、`m1-build` |
| Gradle 工程 | ✅ 完成 | AGP 8.13.2 / Kotlin 2.4.20 / Compose BOM 2025.10.01 |
| debug APK | ✅ 构建/真机通过 | 无 INTERNET；16 KB 对齐；v2 签名 |
| GitHub remote | ⚠️ 已添加 | `https://github.com/Lanzune/InkReader.git`；推送待本机认证 |
| M1 垂直切片 | 🚧 等待真机 | M1a TXT 阅读 + M1b QuickJS 示例主题/命令已构建 |
| M2 TXT 完整化 | ⏳ 待开始 | 编码、章节、进度、设置 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. 安装 `artifacts/m1b/inkreader-0.1.0-m1b-debug.apk`，验证首页插件主题/命令、TXT 打开、翻页、日夜切换。
2. 完成 GitHub 推送认证，推送 `main`、`feat/m1-vertical-slice` 和标签。
3. 真机通过后合并 M1 分支，打 `m1-vertical-slice` 标签。
4. 进入 M2：编码探测、章节索引、进度持久化、设置/亮度。

## 阻塞与待定

| 项 | 是否阻塞 M1 | 说明 |
|---|---|---|
| 真机验证 M1b | 是（阶段闸门） | 需要项目所有者安装并反馈；重点看 QuickJS 是否崩溃 |
| GitHub 推送认证 | 不阻塞本地开发 | HTTPS 凭据未配置；主分支已添加 remote，推送待认证 |
| Android API 级别 | 不阻塞 | 截图显示 ColorOS 16.0.10；具体 API 待 `getprop` 补充 |
| 正式名称/包名未定 | 不阻塞 | 暂用 `com.inkreader.app`，发布前改 |
| 签名密钥未准备 | 不阻塞 | M7 前准备 |
| 图标/视觉设计 | 不阻塞 | M5 前完善 |

## 最近一次环境结论

- Android Studio 2026.2.2.8 已安装（scoop）
- Android SDK：`C:\Users\34553\AppData\Local\Android\Sdk`
- JDK 21.0.2 可用；默认 `JAVA_HOME` 是 JDK 25，构建时需覆盖
- Gradle 8.14 分发已缓存；无全局 gradle
- Git 2.56，身份 `Lanzune <3455312265@qq.com>`
- 无系统镜像/AVD；adb 在 SDK 内但不在 PATH

详见 [环境审计](05-environment.md)。
