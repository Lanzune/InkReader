# 功能模块地图

> 以功能模块划分，不以“前端/后端/工具”这种技术层划分。每个模块有自己的文档、接口、测试和退出标准。

## 模块总表

| 模块 | 代码位置 | 文档 | 首个阶段 | 依赖 | 状态 |
|---|---|---|---|---|---|
| 内核与公开 API | `kernel/api`, `kernel/core` | [00-kernel](modules/00-kernel.md) | M1 | 无 | M1 已实现，等待真机 |
| QuickJS 插件宿主 | `plugin-host-quickjs` | [01-plugin-host](modules/01-plugin-host.md) | M1 | 内核 API | M1 示例宿主已实现 |
| 阅读引擎 | `reader-engine` | [02-reader-engine](modules/02-reader-engine.md) | M1 | 内核 API | M1 最小分页已实现 |
| TXT 格式 | `formats/txt` | [03-format-txt](modules/03-format-txt.md) | M1 | 内核 API | M1 最小版已实现 |
| EPUB 格式 | `formats/epub` | [04-format-epub](modules/04-format-epub.md) | M3 | 内核 API、阅读引擎 | 待开始 |
| PDF 格式 | `formats/pdf` | [05-format-pdf](modules/05-format-pdf.md) | M4 | 内核 API | M4a 已实现 |
| HTML / Markdown | `formats/htmlmd` | [06-format-html-md](modules/06-format-html-md.md) | M4 | 内核 API | M4b 文本基线已实现 |
| UI 壳层 | `app` | [07-ui-shell](modules/07-ui-shell.md) | M1 | 内核 API | M1 已实现，等待真机 |
| 存储、缓存与隐私 | `kernel/core`, `docs` | [08-storage-privacy](modules/08-storage-privacy.md) | M1 | 内核 API | M1 仅 SAF 读取；进度/缓存在 M2 |
| 插件 SDK 与示例 | `sdk`, `plugin-host-quickjs` | 01-plugin-host、[plugin-api-v0](specs/plugin-api-v0.md) | M1/M6 | 插件宿主 | 示例脚本已接；SDK 待 M6 |
| 质量与发布 | `benchmark`, `docs/quality` | [06-quality-gates](06-quality-gates.md) | M0 | 全部 | 持续 |

## 依赖方向

```text
app ──────────────► kernel/api
 │                        ▲
 ├──► formats/* ──────────┤
 ├──► builtin/* ──────────┤
 └──► plugin-host ────────┘

kernel/core ──► kernel/api
reader-engine ──► kernel/api
formats/* ──► kernel/api + reader-engine model
```

硬规则：

- `kernel/api` 不依赖 Android。
- `kernel/core` 不出现 EPUB/PDF/TXT 具体类名。
- 插件只依赖公开 API，不依赖 `kernel/core`。
- 格式插件不反向依赖 UI。
- 阅读引擎不直接读取 URI。
- 跨插件/跨进程数据必须可序列化、不可变、可版本化。

## 模块完成定义（DoD）

一个模块不能只用“代码编译通过”宣告完成。至少满足：

1. 目的、输入输出和边界已写入模块文档；
2. 对外接口有测试，错误路径有测试；
3. 正常路径有集成或手工验证记录；
4. 不引入未经评估的新依赖；
5. 体积/内存/性能影响已记录；
6. 相关 ADR、规格、状态看板已更新；
7. 项目所有者能在真机上验证的，已给出验证步骤。

## 阶段与模块的关系

- **M1**：内核、插件宿主、阅读引擎最小路径、TXT、UI 壳层、存储最小实现。
- **M2**：TXT 完整化、设置/进度、主题、基本翻页。
- **M3**：EPUB。
- **M4**：PDF、HTML/Markdown。
- **M5**：UI 体验、亮度、仿真折页、最近阅读。
- **M6**：插件 SDK、安全模式、文档、示例。
- **M7**：质量、发布。

详见 [阶段路线图](03-roadmap.md)。
