# M5a 翻页动画（2026-11-08）

## 范围

- 翻页时捕获上一页为 RGB_565 位图
- 220ms 水平滑动：
  - 下一页：旧页向左滑出
  - 上一页：旧页向右滑出
- 动画结束回收位图
- 设置新增“翻页动画”开关，默认开启并持久化
- 关闭时直接切换页面

## 性能/内存

- 只保留一张全屏位图
- 位图使用 RGB_565，单页约 5 MB
- 动画期间不重建 StaticLayout
- 目录跳转/样式重排会取消动画

## 真机验证

- Markdown 样书第 1 页翻到 2/2
- 设置页显示“翻页动画”开关，默认开
- 截图：
  - `artifacts/device/m5-settings-animation.png`
  - `artifacts/device/m5-turn-done.png`

## 测试

- EPUB 7/7
- PDF 2/2
- HTML/Markdown 3/3
- App 构建通过

## 结果

| 项 | 结果 |
|---|---|
| APK | `artifacts/m5a/inkreader-0.5.0-m5a-debug.apk` |
| 版本 | `0.5.0`，versionCode 28 |
| 大小 | 14,951,751 bytes（约 14.26 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `A41537B9601F3980F93A6A10222A5B249E0D0EEA56A409456E6A289553589DA3` |

## 已知限制

- 动画是整页水平滑动，不做仿真书页卷曲
- 极低端机可能因全屏位图捕获有短暂掉帧
- 翻页动画只作用于文本阅读器，PDF 仍用按钮/手势翻页
