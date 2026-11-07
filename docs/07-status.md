# 当前状态

**更新时间：** 2026-11-07
**当前阶段：** M2b9 上部安全区已调整并真机验证
**下一阶段：** 视觉确认后合并 M2，进入 M3 EPUB

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0～M2a | ✅ 完成 | 真机通过并合并 |
| M2b 设置与编码 | ✅ 真机验证 | 滑杆/亮度/编码 |
| M2b7 页裁切 | ✅ 真机验证 | 每页独立 StaticLayout |
| M2b9 上部安全区 | ✅ 真机验证 | 顶部 80+16dp，底部 56+24dp |
| M2c 验收 | ⏳ 待开始 | 合并、标签、推送 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. 视觉确认 `artifacts/device/m2b9-reader-final.png` 的上下比例。
2. 合并 `feat/m2b-settings`，打 `m2-txt-reader` 标签并推送。
3. 进入 M3 EPUB。

## 阻塞与待定

| 项 | 是否阻塞 | 说明 |
|---|---|---|
| 上部安全区视觉确认 | 是 | 已真机截图，比例可再微调 |
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
