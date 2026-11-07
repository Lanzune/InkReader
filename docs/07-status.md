# 当前状态

**更新时间：** 2026-11-08
**当前阶段：** M3 EPUB 已完成并合并
**下一阶段：** M4（搜索/书架/设置）

## 阶段看板

| 项目 | 状态 | 备注 |
|---|---|---|
| M0～M2 | ✅ 完成 | 真机通过，`m2-txt-reader` |
| M3a EPUB 文本 | ✅ 真机 | EPUB2/3、目录、元数据 |
| M3b EPUB 富内容 | ✅ 真机 | 图片/样式/标题/对齐/脚注 |
| M3c1 CSS/健壮性 | ✅ 真机+7 单测 | class CSS、缩进、损坏样本 |
| M3c2 回归 | ✅ 真机 | 非 a 锚点、返回栈、真实书 |
| M4 | ⏳ 待规划 | 搜索/书架/设置等 |

## 已完成的关键决策

- Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图
- PageKernel 可信内核；格式/主题/命令/面板插件化
- 第三方插件用 QuickJS，不加载 DEX
- EPUB 自研轻量重排；PDF 用系统 `PdfRenderer`
- 主 APK 无 `INTERNET`
- 先做 M1 垂直切片，再进入 EPUB/PDF

## 下一步（按顺序）

1. 规划 M4：搜索、书架/最近阅读、设置页等。
2. 复杂 CSS/分块加载留到 M6。
3. 保持 `m3-epub` 可回退。

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
