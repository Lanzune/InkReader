# M3b2a EPUB 行内样式（2026-11-07）

## 范围

- 识别 `<b>/<strong>` 与 `<i>/<em>`
- 在正文文本中记录字符区间，不改变字符数
- `Chapter.styles` 传入 App
- App 使用 `StyleSpan(Typeface.BOLD/ITALIC)` 渲染
- 与图片 `ImageSpan`、目录偏移、进度偏移共用同一套字符坐标

## 实现

- XHTML 转文本前插入私有区标记
- 去标签/实体解码/段落整理后，扫描标记生成区间
- 移除标记，保留正文长度和图片占位符偏移
- EPUB 提供器把章节内区间平移到全书文本偏移

## 真机验证

调试样书第一章加入粗体/斜体：

- 粗体：`夜航`
- 斜体：`风从窗外经过`

截图：`artifacts/device/m3b2-sample-style.png`

中文粗体在不同字体下可能较细微；斜体效果明显。

## 结果

| 项 | 结果 |
|---|---|
| EPUB 单测 | ✅ 4/4（含样式断言） |
| 真机样书 | ✅ 粗体/斜体/图片 |
| APK | `artifacts/m3b2/inkreader-0.3.2-m3b2-debug.apk` |
| 版本 | `0.3.2`，versionCode 18 |
| 大小 | 14,874,626 bytes（约 14.19 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `9AB6361D031CFA216DD43CA4676C930564DC3D002103835676530B63FD761C10` |

## 尚未完成

- 对齐/缩进类 CSS
- 脚注/内部链接跳转
- 标题字号层级
