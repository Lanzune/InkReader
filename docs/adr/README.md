# 架构决策记录（ADR）

| 编号 | 决策 | 状态 |
|---|---|---|
| [0001](ADR-0001-kotlin-native-ui.md) | Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图 | Accepted |
| [0002](ADR-0002-script-plugins-no-dex.md) | 第三方插件使用 QuickJS，不加载 DEX/原生代码 | Accepted |
| [0003](ADR-0003-custom-epub-engine.md) | 首版自研轻量 EPUB 重排引擎 | Accepted |
| [0004](ADR-0004-platform-pdf-renderer.md) | 首版使用系统 PdfRenderer | Accepted |
| [0005](ADR-0005-no-internet-permission.md) | 主 APK 不声明 INTERNET 权限 | Accepted |

## 规则

- 任何影响公开 API、插件边界、数据格式、体积/性能预算的决策，先写 ADR。
- ADR 一旦 Accepted，修改要新增一条 Superseding ADR，不直接改写历史结论。
- ADR 必须写：背景、决策、替代方案、后果、验证方式。
