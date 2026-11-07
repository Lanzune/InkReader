# M3c2 回归与 M3 收尾（2026-11-08）

## 范围

- 非 `<a id>` 锚点：支持 `<p id>`、标题 id 等
- 脚注链接返回栈：点“返回”先回到跳转前位置，再退出阅读器
- 真实 EPUB《拿破仑大帝（全2册）》：
  - 3936 页
  - 图片正常
  - 打开约 6 秒
- 损坏样本测试保持通过

## 真机验证

- 样书脚注跳到第三章注释
- 点“返回”回到第一章原位置
- 截图：
  - `artifacts/device/m3c2-link-target.png`
  - `artifacts/device/m3c2-back-result.png`
  - `artifacts/device/m3c-css-class.png`

## 测试

- EPUB 单测 7/7
- App 构建通过
- 真实大书真机打开

## 结果

| 项 | 结果 |
|---|---|
| APK | `artifacts/m3c2/inkreader-0.3.7-m3c2-debug.apk` |
| 版本 | `0.3.7`，versionCode 23 |
| 大小 | 14,891,290 bytes（约 14.20 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `DBC27700207B3450FFF94189E9C675809B14557C62629100F58E91B76B104444` |

## M3 已知限制

- 复杂 CSS 级联/字体/浮动布局不完整
- 固定版式、DRM、远程资源不支持
- 超大 EPUB 仍整本加载和分页；M6 再评估分块
- 图片按屏幕缩放，不保留原始 CSS 尺寸
- 外链不打开浏览器

## 收尾

- 合并 `feat/m3-epub` 到 `main`
- 标签 `m3-epub`
- 推送远端
