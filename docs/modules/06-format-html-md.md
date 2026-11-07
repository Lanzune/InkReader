# 模块 06：HTML 与 Markdown

**代码：** `formats/htmlmd`
**阶段：** M4c
**状态：** HTML/Markdown 文本、标题目录、强调、内部链接已真机验证

## 目的

复用 EPUB 的块模型和阅读引擎，支持单文件 HTML 与 Markdown 阅读。它是 v1.0 的补充格式，不阻塞 TXT/EPUB/PDF。

## HTML

- 支持单文件 HTML
- 移除脚本、iframe、object
- 阻止远程图片/CSS/字体
- 本地相对资源不可访问时提示缺失
- 使用与 EPUB 相同的受控样式子集

## Markdown

- 标题、段落、强调、列表、引用、代码块、分隔线、链接
- 图片：内联 data URI 或可访问本地资源；远程图阻止
- 不实现完整 CommonMark 扩展和 HTML 内嵌

## 为什么单独成模块

HTML/Markdown 与 EPUB 共享块模型，但文件源、资源解析和错误形态不同。独立模块可以避免把格式判断塞进阅读引擎。

## 测试

- 基本 HTML/Markdown 文档
- 脚本/iframe/远程资源被阻断
- 缺失本地资源
- 超长代码块/表格/图片

## 退出标准

- 常见单文件 HTML/Markdown 可读
- 不执行脚本、不联网
- 不支持的结构有降级而不是崩溃
