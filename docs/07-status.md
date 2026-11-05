# 当前状态

**更新时间：** 2026-11-05 23:55（Asia/Shanghai）  
**当前阶段：** M0 — 项目骨架与开发环境  
**下一阶段：** M1 — 垂直切片

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| 技术方案报告 | ✅ 完成 | Word 46 页 + Markdown 主稿 |
| 项目文件夹 | ✅ 完成 | `inkreader/`，内部代号 |
| 目录骨架 | ✅ 完成 | app/kernel/formats/plugin-host/sdk 等 |
| 文档体系 | ✅ 第一批完成 | 章程、基线、模块地图、路线图、工作流、环境、质量 |
| 环境审计 | ✅ 完成（只读） | SDK/NDK/JDK21/Gradle/Git 均可用；无 AVD/系统镜像 |
| 本地 git | ✅ 完成 | `main`，M0 代码提交 `8ec2212`，文档提交 `64776d4`，标签 `m0-build` |
| Gradle 工程 | ✅ 完成 | AGP 8.13.2 / Kotlin 2.4.20 / Compose BOM 2025.10.01 |
| debug APK | ✅ 构建成功 | 10.68 MiB；无 INTERNET；16 KB 对齐；v2 签名通过 |
| 单元测试 | ✅ 通过 | `testDebugUnitTest` 1 个冒烟测试 |
| 真机冒烟 | ⏳ 等待安装 | 需要项目所有者安装 APK 并确认启动 |
| GitHub remote | ⏳ 等待信息 | 项目所有者稍后提供 |
| 模拟器/AVD | ⏳ 未准备 | 无 system image；优先真实手机 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. 初始化本地 git 仓库并完成 M0 首次提交。
2. 生成 Gradle wrapper，创建最小 `app` / `kernel/api` / `kernel/core`。
3. 用 JDK 21 + 本机 SDK 构建 debug APK。
4. 检查 manifest 无 `INTERNET`。
5. 项目所有者提供 GitHub 信息后配置 remote 并推送。
6. 开始 M1：内核 API、QuickJS 宿主、TXT、最小阅读视图和示例插件。

## 阻塞与待定

| 项 | 是否阻塞 M0 | 说明 |
|---|---|---|
| 无 AVD/系统镜像 | 不阻塞构建 | 阻塞运行时验证；优先用真实手机 |
| GitHub 仓库未提供 | 不阻塞本地开发 | 影响推送；稍后配置 |
| 正式名称/包名未定 | 不阻塞 | 暂用 `com.inkreader.app`，发布前改 |
| 签名密钥未准备 | 不阻塞 | M7 前准备 |
| 包名/应用图标/视觉设计 | 不阻塞 | M5 前完善 |

## 最近一次环境结论

- Android Studio 2026.2.2.8 已安装（scoop）
- Android SDK：`C:\Users\34553\AppData\Local\Android\Sdk`
- JDK 21.0.2 可用；默认 `JAVA_HOME` 是 JDK 25，构建时需覆盖
- Gradle 8.14 分发已缓存；无全局 gradle
- Git 2.56，身份 `Lanzune <3455312265@qq.com>`
- 无系统镜像/AVD；adb 在 SDK 内但不在 PATH

详见 [环境审计](05-environment.md)。
