# M5：阅读体验

**目标：** 在格式能力完成后打磨阅读手感、命令入口和低端机表现。  
**前置：** M4 完成。

## 拆分

| 子阶段 | 内容 | 状态 |
|---|---|---|
| M5a | 翻页滑动动画、设置开关 | ✅ 真机 |
| M5b | 命令面板/插件命令 | ⏳ 待开始 |
| M5c | 主题/亮度细化、性能回归、合并 | ⏳ 待开始 |

## M5a 范围

- 阅读器翻页 220ms 水平滑动
- 只缓存一张上一页 RGB_565 位图，动画结束立即回收
- 设置里增加“翻页动画”开关，默认开
- 关闭后直接翻页
- 目录/字号/行距/亮度/进度不受影响

## M5a 结果

- `ReaderSurfaceView` 增加 page snapshot + ValueAnimator
- `ReaderSettings.pageTurnAnimation` 持久化
- 设置对话框显示开关
- 真机截图：`artifacts/device/m5-settings-animation.png`
- 翻页后：`artifacts/device/m5-turn-done.png`

## M5b 待办

- 命令面板
- QuickJS 命令/主题命令统一入口
- 翻页方向/动画强度可调
- 低端机性能采样

## M5c 退出标准

- 真机翻页流畅、无明显掉帧
- 命令面板可用
- 关闭动画后行为正确
- 项目所有者验收
