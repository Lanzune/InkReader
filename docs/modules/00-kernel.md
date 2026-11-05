# 模块 00：内核与公开 API

**代码：** `kernel/api`, `kernel/core`  
**阶段：** M1 起  
**状态：** 未开始

## 目的

提供不可替代的可信核心：会话状态、文件访问、命令/事件、能力授权、插件注册、版本协商、存储接口。内核不知道 EPUB/PDF/TXT 的具体实现。

## 职责

- 打开/关闭 `ReaderSession`
- 从 SAF 取得可读源，封装成 `BookSource`
- 维护当前 `Locator`、书籍元数据和阅读状态
- 注册格式、主题、命令、面板
- 校验插件能力与 API 版本
- 暴露只读模型和受控能力
- 持久化书籍、进度、设置和插件状态
- 故障隔离：插件/格式异常不能拖垮主会话

## 不负责

- ZIP/OPF/XHTML/PDF 的解析细节
- 具体排版、字体渲染、动画
- 第三方插件业务
- 任意 View/UI 注入

## 首版公开概念

```text
BookSource      只读随机访问接口（不暴露 Android Uri）
FormatProvider  格式探测 + 解析，产出 BookDocument
BookDocument    元数据、spine、toc、布局类型
Locator         章节 + 块 + 字符偏移 / PDF 页码
ReaderSession   打开、翻页、跳转、进度
Theme           颜色/字体/间距 token
Command         可搜索、可执行的命令
PluginContext   插件能力入口（只读、异步、可校验）
```

## M1 范围

- `kernel-api` 纯 Kotlin，无 Android 依赖
- `kernel-core` 最小实现：打开 TXT、保存 Locator、注册一个主题/命令
- 不做 EPUB/PDF 细节，不做完整插件市场
- 不暴露 `Context`、`File`、`Uri`、`Intent` 给插件

## 测试

- 模块依赖检查（API 不引用 Android）
- 会话状态机：打开→翻页→暂停→恢复→关闭
- Locator 编解码与边界
- 未知格式/损坏文件/权限丢失
- 插件 API 版本不匹配时拒绝

## 退出标准

- 一个测试用 TXT 可以通过内核打开并产生 `BookDocument`
- 一个插件可以注册主题/命令并影响阅读状态
- 关闭会话后无泄漏的解析器/文件描述符
- 代码评审确认没有格式细节泄漏进内核
