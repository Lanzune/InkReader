# 模块 08：存储、缓存与隐私

**代码：** `kernel/core` + 平台适配层  
**阶段：** M1 起  
**状态：** 未开始

## 目的

在不申请广泛存储权限的前提下读取书籍，保存最少量的进度/设置/元数据，并保证缓存和插件存储有明确上限。

## 文件访问

- 使用 SAF：`ACTION_OPEN_DOCUMENT` / `ACTION_VIEW`
- `takePersistableUriPermission`，只保留读取权限
- 不申请 `READ_EXTERNAL_STORAGE`、`MANAGE_EXTERNAL_STORAGE` 或媒体权限
- 不扫描全盘；最近阅读只记录用户主动打开的书

## 数据

SQLite（`SQLiteOpenHelper`）：

- `books`：URI、显示名、格式、元数据、最近打开时间、Locator、进度
- `plugins`：ID、版本、启用状态、manifest、最近错误

设置使用 `SharedPreferences` 封装；插件设置和插件数据存放在独立目录，带 schema 与配额。

## 缓存策略

| 数据 | 策略 |
|---|---|
| 正文/页面 | 不缓存 |
| 封面缩略图 | `cacheDir/covers`，LRU ≤16 MB，可清理 |
| 当前/相邻页位图 | 仅内存，低内存释放 |
| PDF 非 seekable 临时文件 | 当前会话，关闭即删 |
| 插件代码/资源 | 应用私有安装目录，非缓存 |
| 插件数据 | 每插件 ≤5 MB |
| 日志 | 内存环形缓冲，默认不落盘 |

不引入 WebView，因此没有 WebView 缓存/Cookie/localStorage。

## 隐私

- 主 APK 不声明 `INTERNET`
- 无广告、统计、账号、设备标识
- `allowBackup=false`
- EPUB/HTML/Markdown 远程资源阻断
- 卸载清除应用数据；不删除用户原始书籍

## 测试

- SAF 权限持久化/丢失
- 非 seekable provider
- 文件移动/删除
- 缓存上限/清理
- 卸载重装
- 检查 manifest 无 INTERNET/危险权限

## 退出标准

- 不复制书籍也能正常阅读
- 退出/崩溃后进度可恢复（在权限仍有效时）
- 缓存不超过预算
- 权限清单可供项目所有者复核
