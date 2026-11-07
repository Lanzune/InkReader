# M5c 性能收尾与 M5 合并（2026-11-08）

## 范围

- 翻页快照位图复用：
  - 不再每次翻页 `Bitmap.createBitmap`
  - 同一尺寸下复用上一张 RGB_565 快照
  - 主题/样式/尺寸变化/退出时释放
- 避免动画结束后立即回收又被下一帧使用
- 命令面板保留
- 主题/亮度/行距/边距设置回归

## 真机验证

- Markdown 样书 1/2 → 2/2
- 翻页动画开启时正常
- 设置开关/命令面板正常
- 截图：`artifacts/device/m5c-turn.png`

## 测试

- EPUB 7/7
- PDF 2/2
- HTML/Markdown 3/3
- App 构建通过

## 结果

| 项 | 结果 |
|---|---|
| APK | `artifacts/m5c/inkreader-0.5.2-m5c-debug.apk` |
| 版本 | `0.5.2`，versionCode 30 |
| 大小 | 14,968,135 bytes（约 14.27 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `78D6BD09E0AC405C3A57B2B549FC643115D3F7EA698BB3E7355A84A3BE99C4D7` |

## 已知限制

- 翻页动画仍为整页水平滑动
- 低端机未做自动降级采样
- 命令面板未从插件 manifest 动态注册
- PDF 未接入命令面板

## 收尾

- 合并 `feat/m5-experience` 到 `main`
- 标签 `m5-experience`
- 推送远端
