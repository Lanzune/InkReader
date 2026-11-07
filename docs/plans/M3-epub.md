# M3：EPUB 与富内容

**目标：** 把 EPUB 2/3 的重排正文接入现有阅读器，保留目录和元数据，再逐步补图片/脚注。  
**时间：** 2 周。  
**前置：** M2 TXT 阅读器通过。

## 拆分

| 子阶段 | 内容 | 状态 |
|---|---|---|
| M3a | EPUB 2/3 ZIP/OPF/NAV/NCX、元数据、正文、目录 | ✅ 构建/单测/真机样书 |
| M3b1 | 图片、懒解码、居中显示 | ✅ 真实 EPUB 验证 |
| M3b2 | 基础 CSS、脚注/内部链接 | ⏳ 待开始 |
| M3c | 真实书库回归、损坏样本、大文件 | ⏳ 待开始 |

## M3a 范围

- ZIP 容器、`META-INF/container.xml`
- OPF metadata/manifest/spine
- NAV（EPUB3）与 NCX（EPUB2）目录
- XHTML 转段落文本，实体解码，脚本/样式/iframe 剔除
- 章节合并为可重排文本，目录映射到字符偏移
- 标题/作者/语言
- ZIP 条目数、单条大小、总解压大小限制
- 路径穿越拒绝、XML 外部实体/DOCTYPE 禁用
- 固定版式/DRM/远程资源暂不支持，明确报错

## M3a 结果

- `formats/epub` 独立模块
- 4 个 JVM 测试通过（EPUB3/EPUB2/实体/非 ZIP）
- Android 真机用内置样书验证：
  - 标题 `示例 EPUB · 夜航`
  - 3 个目录项
  - 正文正确显示
- 调试样书只在 debug assets，release 不包含

## M3b1 结果

- `<img>` 字符偏移 + `BookDocument.resources`
- `ImageSpan` 居中、等比缩放
- `LazyEpubDrawable` 延迟解码
- 真实 EPUB 3936 页，图片正常，打开约 6s

## M3b2 待办

- `<b>/<i>`、对齐/缩进等基础 CSS
- 脚注/内部链接跳转
- 图片像素与内存上限
- 真实 EPUB 语料回归

## 退出标准

- 语料库 ≥95% 可打开
- 不丢字、不重页、目录可跳转
- 封面/元数据正确
- 不支持的书显示明确提示而不是崩溃
