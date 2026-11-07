# M3b2b1 标题层级与引用缩进（2026-11-07）

## 范围

- `<h1>/<h2>/<h3>/<h4-6>` 映射为标题层级
- App 使用 `RelativeSizeSpan` + `StyleSpan(BOLD)`：
  - H1：1.45×
  - H2：1.28×
  - H3：1.15×
- `<blockquote>` 映射为 `LeadingMarginSpan`（24dp 缩进）
- 标题前后保留段落空行，不丢正文

## 真机验证

调试样书第一章/第二章标题现在明显大于正文并加粗：

截图：`artifacts/device/m3b2b-heading.png`

## 结果

| 项 | 结果 |
|---|---|
| EPUB 单测 | ✅ 4/4 |
| 真机样书 | ✅ 标题层级、行内样式、图片 |
| APK | `artifacts/m3b2b/inkreader-0.3.3-m3b2b-debug.apk` |
| 版本 | `0.3.3`，versionCode 19 |
| 大小 | 14,874,622 bytes（约 14.19 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `C1065FAD0157B651DF109545DDF0DAE720FF1E1993381A095B60CA261A59C85A` |

## 尚未完成

- 段落对齐（居中/右对齐）
- 脚注与内部链接跳转
- 标题层级与用户字号设置的组合调优
