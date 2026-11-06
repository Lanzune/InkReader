# 当前状态

**更新时间：** 2026-11-06
**当前阶段：** M1d 沉浸式阅读已构建，等待真机验证
**下一阶段：** 验证通过后推送/合并，进入 M2（章节、进度、设置/亮度、手动编码）

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0 骨架 | ✅ 完成 | 一加 Ace 5 / ColorOS 16.0.10 / Android 16 |
| M1 垂直切片 | ✅ 真机通过 | TXT/翻页/编码/QuickJS 主题与控制栏流畅度通过 |
| M1d 沉浸式阅读 | 🚧 待真机 | 阅读时隐藏导航栏，底部上滑临时呼出；离开恢复 |
| M2 TXT 完整化 | ⏳ 待开始 | 章节、进度、设置/亮度、手动编码 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. 安装 `artifacts/m1d/inkreader-0.1.2-m1d-debug.apk`，验证导航栏隐藏/上滑呼出/退出恢复。
2. 通过后合并 `fix/reader-immersive`，推送 `main`、M1 和 M1d 分支/标签。
3. 进入 M2：章节索引、进度持久化、设置/亮度、手动编码切换。

## 阻塞与待定

| 项 | 是否阻塞 | 说明 |
|---|---|---|
| 导航栏交互真机验证 | 是（M1d） | 需项目所有者安装 APK 后确认 |
| GitHub 推送 | 否 | 项目所有者已解决上传；本机凭据状态待复核 |
| 正式名称/包名 | 否 | 暂用 `com.inkreader.app` |
| 签名密钥 | 否 | M7 前准备 |
| 图标/视觉设计 | 否 | M5 前完善 |

## 最近一次环境结论

- Android Studio 2026.2.2.8 已安装（scoop）
- Android SDK：`C:\Users\34553\AppData\Local\Android\Sdk`
- JDK 21.0.2 可用；默认 `JAVA_HOME` 是 JDK 25，构建时需覆盖
- Gradle 8.14 分发已缓存；无全局 gradle
- Git 2.56，身份 `Lanzune <3455312265@qq.com>`
- 无系统镜像/AVD；adb 在 SDK 内但不在 PATH

详见 [环境审计](05-environment.md)。
