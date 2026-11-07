# M4：PDF 与文档格式

**目标：** 增加 PDF 固定页阅读，再补 HTML/Markdown。  
**前置：** M3 EPUB 完成。

## 拆分

| 子阶段 | 内容 | 状态 |
|---|---|---|
| M4a | PDF 页面渲染、翻页、缩放、夜间 | ✅ 真机样书 |
| M4b | HTML/Markdown 单文件导入 | ⏳ 待开始 |
| M4c | 回归、异常 PDF、合并 | ⏳ 待开始 |

## M4a 范围

- 系统 `PdfRenderer`，不引入 Pdfium/AndroidX PDF
- 适配屏幕宽度、页码、上一页/下一页
- 双指缩放 1×–4×、上下/左右拖动
- 双击 2× / 还原
- 夜间反色
- 非 seekable 源复制到 cache 后打开，关闭时删除
- 一次只渲染当前页，关闭释放 Page/Renderer/FD

## M4a 结果

- 新增 `formats/pdf` 纯 Kotlin provider（只识别/元数据）
- `PdfDocumentController` + `PdfReaderScreen`
- debug 样书 `sample.pdf`
- PDF 单测 2/2
- 真机截图：
  - `artifacts/device/m4-pdf-reader.png`
  - `artifacts/device/m4-pdf-page2-night.png`
  - `artifacts/device/m4-pdf-zoom.png`

## M4b 待办

- HTML/Markdown 单文件
- 链接/图片策略
- 编码探测复用
- 真实语料回归

## M4c 退出标准

- 常见 PDF 稳定显示、翻页、缩放、夜间
- 损坏/加密/超大 PDF 有明确提示
- 退出无 FD/位图泄漏
- 项目所有者真机验收
