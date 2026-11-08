# InkReader（内部开发代号）

> 名称暂定。`InkReader` 仅作为内部开发代号，正式名称确定后可整体重命名目录、包名和文档标题。

## 项目定位

一个轻量、本地优先、无广告、可插件扩展的 Android 电子书阅读器。

- **默认轻**：基础 APK 只包含打开、阅读、翻页、进度、日夜/亮度等核心能力。
- **按需重**：复杂格式、增强排版、特殊交互通过插件逐步接入。
- **硬内核**：文件访问、会话状态、权限、存储和版本协商留在 PageKernel。
- **无网络**：主 APK 不声明 `INTERNET` 权限；不内置广告、统计、账号和云同步。
- **可验证**：先做垂直切片，再按模块推进；每个阶段有明确退出标准。

## 当前状态

**阶段：M8a.1 图片预加载（0.8.1），待真机授权验证。**

- M0～M2：TXT 阅读器完成，标签 `m2-txt-reader`
- M3：EPUB 完成，标签 `m3-epub`
- M4：PDF/HTML/Markdown 完成，标签 `m4-docs`
- M5：翻页动画、命令面板，标签 `m5-experience`
- M6：manifest、`.inkp` 安装、启停/卸载，标签 `m6-plugins`
- M7：R8 release、正式签名、AGPL-3.0-only，标签 `v0.7.2`
- M8a：首屏局部排版、并行 EPUB 解析、后台富文本/分页、内存缓存，0.8.0
- M8a.1：页面图片预加载，去掉绘制时解码，0.8.1
- 当前大书 12.5 MB：基线 8.4 s → 首屏约 2.2 s
- 后续：章节懒加载，目标首屏 <1 s

性能测试：`scripts/bench-open.cmd -Runs 5`
性能记录：[perf-phase1](docs/quality/2026-11-08-perf-phase1.md)
图片预加载：[image-preload](docs/quality/2026-11-08-image-preload.md)

下一步：M8b 章节懒加载。

## 目录结构

```text
inkreader/
├── app/                      # Android 应用壳层
├── kernel/
│   ├── api/                  # 纯 Kotlin 公开 API、模型、SPI
│   └── core/                 # Android 内核实现
├── reader-engine/            # 分页、绘制、主题、翻页
├── formats/                  # 格式插件：epub / txt / pdf / html-md
├── builtin/                  # 内置主题、翻页、命令
├── plugin-host-quickjs/      # QuickJS 插件宿主
├── sdk/                      # 插件 SDK、manifest schema、TS 类型
├── benchmark/                # 性能基准
├── docs/                     # 计划、规范、ADR、质量、报告
├── scripts/                  # 只读检查、构建、报告生成脚本
├── .editorconfig
└── .gitignore
```

## 文档入口

- [文档索引](docs/README.md)
- [项目章程](docs/00-charter.md)
- [基线定义](docs/01-baseline.md)
- [功能模块地图](docs/02-module-map.md)
- [阶段路线图](docs/03-roadmap.md)
- [开发工作流](docs/04-workflow.md)
- [环境与工具链](docs/05-environment.md)
- [质量门槛](docs/06-quality-gates.md)
- [技术方案与实施报告](docs/reports/ebook-reader-technical-report.zh-CN.md)
- [Word 版报告](docs/reports/ebook-reader-technical-report.zh-CN.docx)

## 开发原则

1. 先基线，后增强；先可运行，后完善。
2. 每个模块有独立文档、接口、测试和退出标准。
3. 不把热路径和基础能力塞进插件；插件只承载可选能力。
4. 不加载 DEX/任意原生代码；第三方脚本走受控宿主。
5. 命令行优先；真机交互验证由项目所有者执行并反馈结果。
6. 每个阶段结束提交、打标签、更新文档和状态。

## 许可证

InkReader 使用 **AGPL-3.0-only**：

- 完整文本：[LICENSE](LICENSE)
- 说明：[LICENSE.md](LICENSE.md)
- 第三方组件：[NOTICE](NOTICE)
- 贡献规则：[CONTRIBUTING.md](CONTRIBUTING.md)
- 商标：[TRADEMARK.md](TRADEMARK.md)

分发修改版必须提供完整对应源码；修改版作为网络服务提供时必须向用户提供源码。私用且不分发不强制公开。

## 尚未确定

- 正式产品名称
- 包名 / GitHub 仓库地址
- 是否上架 Google Play
- v1 是否包含 HTML/Markdown，或顺延到 v1.1

这些问题不影响 M0 的文档和骨架工作。
