# M4c 文档回归与 M4 收尾（2026-11-08）

## 范围

- Markdown 内部链接：
  - `[文本](#标题)` 解析为 ChapterLink
  - 标题作为锚点
  - 紫色下划线，点击跳转
- HTML 标题（H1–H6）：
  - 生成目录
  - H1–H3 层级样式
- Markdown 粗体/斜体 StyleSpan
- 文档测试：
  - Markdown 标题/目录/强调/内部链接
  - HTML 标题/目录/脚本剔除
  - GB18030 Markdown

## 真机验证

样书 Markdown：

- 标题层级
- 列表、引用
- “内部链接”紫色下划线
- 目录可跳转

截图：`artifacts/device/m4c-markdown-link.png`

## 测试

- HTML/Markdown 3/3
- PDF 2/2
- EPUB 7/7
- App 构建通过

## 结果

| 项 | 结果 |
|---|---|
| APK | `artifacts/m4c/inkreader-0.4.3-m4c-debug.apk` |
| 版本 | `0.4.3`，versionCode 27 |
| 大小 | 14,951,751 bytes（约 14.26 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `BA473E7B1418D906F0543FE45C7C45A59B810DA712B8D71E2CF8CEF161C49F79` |

## M4 已知限制

- PDF 无文本选择/搜索/复制
- 加密/损坏 PDF 只提示，不恢复
- 放大后位图略糊，停止缩放重渲染未做
- HTML/Markdown 不加载远程图片/CSS
- 完整 CommonMark/HTML 内嵌不支持
- 真实 PDF/HTML/Markdown 语料仍有限

## 收尾

- 合并 `feat/m4-pdf` 到 `main`
- 标签 `m4-docs`
- 推送远端
