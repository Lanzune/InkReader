# ADR-0001：Kotlin 原生 + Compose 壳层 + 自定义 Canvas 阅读视图

**状态：** Accepted  
**日期：** 2026-11-05  
**决策者：** 项目所有者、开发

## 背景

需要小体积、快速启动、可控文本分页和 60 fps 翻页，同时保持现代 UI 开发效率。跨平台框架会引入额外运行时和平台桥接。

## 决策

- 使用 Kotlin 原生 Android。
- 应用壳层、设置、插件声明式面板使用 Jetpack Compose。
- 正文阅读使用自定义 Android View + Canvas + `StaticLayout`。
- 不引入 Flutter/React Native/WebView 作为主 UI。

## 替代方案

| 方案 | 不选原因 |
|---|---|
| Flutter | 引擎/运行时增加体积，文本分页和 SAF/PdfRenderer 需要平台通道 |
| React Native | JS 引擎和桥接增加启动/内存成本，插件边界复杂 |
| 全 Compose | 可行，但正文高频绘制/折页/大文本布局的可控性较差 |
| 全 XML View | 包体可能更小，但设置/插件表单开发效率低 |

## 后果

- 需要维护 Compose 与自定义 View 的桥接。
- 正文热路径完全可控，便于性能调优。
- APK 体积预计 8–12 MB（arm64），需持续用 APK Analyzer 守住。
- 未来若 Compose 体积/性能不达标，可在不改内核的情况下替换壳层。

## 验证

- M0 构建并测量空壳 APK。
- M1 测量 TXT 翻页帧率。
- M2 测量启动与内存。
