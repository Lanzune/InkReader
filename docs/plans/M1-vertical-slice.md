# M1：垂直切片（Walking Skeleton）

**目标：** 用最小功能证明“内核 + 插件 + 阅读主链路”成立。  
**时间：** 2–3 周。  
**前置：** M0 构建成功、git 提交完成。

## 实现拆分与当前结果

| 子阶段 | 内容 | 状态 |
|---|---|---|
| M1a | 内核 API、TXT、阅读视图、SAF、基础主题 | ✅ 构建/单测通过；等待真机 |
| M1b | QuickJS 宿主、示例主题/命令脚本 | ✅ 构建通过；等待真机 |
| M1c | 真机验收、修复、合并 | ⏳ 待开始 |

产物：

- `artifacts/m1a/inkreader-0.1.0-m1a-debug.apk`
- `artifacts/m1b/inkreader-0.1.0-m1b-debug.apk`

M1b 使用 `io.github.dokar3:quickjs-kt:1.0.15`（1.0.16 要求 compileSdk 37）。示例脚本 `app/src/main/assets/plugins/sample-theme.js` 同时提供主题和命令结果；`.inkp` 安装、权限/限额在 M6。

## 范围

必须做：

- `kernel-api` 最小模型和 SPI
- `kernel-core` 最小会话、命令/事件、能力校验
- QuickJS 宿主：加载 `.inkp`，注册主题和命令
- 阅读引擎：TXT 分页、上一页/下一页、左右滑动
- UI：首页“打开文件”、阅读器、插件列表
- SAF 打开本地 TXT
- 一个示例主题插件 + 一个示例命令插件
- 无 `INTERNET`；插件超时不崩溃

明确不做：

- EPUB/PDF
- 复杂设置、折页、命令面板
- 插件面板/设置 schema 完整实现
- 原生插件

## 任务顺序

1. 内核 API + 测试夹具
2. `BookSource` + SAF 读取 + 测试文件
3. TXT 最小解析（UTF-8 + 简单章节）
4. `ReaderSurfaceView` 最小分页/滑动
5. QuickJS 宿主和 manifest 校验
6. 主题插件改变颜色
7. 命令插件显示进度
8. 插件禁用/安全路径
9. 构建 APK + 真机清单

## 验收

自动：

- 内核/TXT/插件单元测试通过
- `assembleDebug` 成功
- APK 无 INTERNET
- 插件非法 manifest 被拒绝

真机（项目所有者）：

1. 安装 APK；
2. 从系统文件选择器打开一个 TXT；
3. 左右滑动翻 20 页以上；
4. 安装/启用示例主题，背景和文字颜色变化；
5. 执行示例命令，能看到当前进度；
6. 禁用插件，恢复内置主题；
7. 退出重进，仍能继续阅读（若权限有效）。

## 退出标准

- 上述真机步骤全部通过，或失败项有明确修复计划
- 插件异常不会导致阅读器退出
- 记录 APK 体积、冷启动时间、设备型号/Android 版本
- 更新状态看板，打标签 `m1-vertical-slice`

## 风险

- QuickJS 集成/16 KB 问题：先验证最小运行时，再扩展 API。
- SAF 在部分文件管理器上行为不一致：准备 `content://` 和 `file://` 两种测试入口。
- 阅读热路径被 Compose 重组拖慢：正文必须走自定义 View。
