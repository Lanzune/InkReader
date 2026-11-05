# ADR-0004：首版使用系统 PdfRenderer

**状态：** Accepted  
**日期：** 2026-11-05

## 背景

PDF 是“最好能支持”的格式。需要控制 APK 体积，不引入 Pdfium/AndroidX PDF 的额外依赖。

## 决策

- 首版使用 `android.graphics.pdf.PdfRenderer`。
- 只做页面渲染、缩放、页码、夜间降亮。
- 不承诺文本搜索/选择/重排/表单/批注。
- 需要 seekable FD；非 seekable 源临时复制并删除。
- 不可信 PDF 的隔离渲染作为后续插件/服务处理。

## 替代方案

| 方案 | 不选原因 |
|---|---|
| Pdfium 原生库 | 多 ABI 体积、16 KB、安全维护成本 |
| AndroidX PDF | 依赖和包体增加，当前仍在演进；可作为后续插件 |
| 不显示 PDF | 不满足用户“最好支持 PDF”的需求 |

## 后果

- APK 几乎不增加 PDF 解析体积。
- 高级 PDF 能力缺失；用户需要搜索/选择时使用后续插件。
- 大页面/缩放需要采样和内存控制。
- 安全风险记录在风险表，v1.1 评估隔离渲染。

## 验证

- 常见文本/扫描 PDF 可显示、翻页、缩放。
- 大页面不 OOM；非 seekable 源可处理。
- 关闭后释放 Page/Renderer/FD。
