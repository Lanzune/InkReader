# M3c1 CSS class、首行缩进与损坏样本（2026-11-08）

## 范围

- 解析 XHTML `<style>` 中的基础规则
- `.class { text-align: center|right; }`
- `.class / p { text-indent: ...; }`
- 首行缩进映射为 `LeadingMarginSpan.Standard`
- 增加损坏 EPUB 单测：
  - 缺失 OPF
  - 畸形 OPF
  - ZIP 路径穿越

## 真机验证

样书第一章：

- 内联 `align=center` 居中
- class CSS 居中
- class CSS `text-indent` 首行缩进
- 脚注链接仍可点击

截图：`artifacts/device/m3c-css-class.png`

## Android 兼容修复

`cssRulePattern` 中未转义的 `}` 在 Android ICU 正则上抛 `PatternSyntaxException`，JVM 测试不会暴露。

已改为 `\\}`，并保留真机样书验证。

## 测试

- EPUB 测试 7/7 通过
  - 4 个基础解析/图片/样式/链接
  - 3 个损坏样本/路径穿越
- App 构建通过

## 结果

| 项 | 结果 |
|---|---|
| APK | `artifacts/m3c1/inkreader-0.3.6-m3c1-debug.apk` |
| 版本 | `0.3.6`，versionCode 22 |
| 大小 | 14,967,141 bytes（约 14.27 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `8163D5176585EC8D7D3C07B652863D1433767769C1067F2487C7E8DC9E451A06` |

## 尚未完成

- 更多真实 EPUB 语料回归
- 超大 EPUB 内存/耗时
- 非 `<a id>` 锚点
- 链接返回栈
- 合并 `feat/m3-epub`
