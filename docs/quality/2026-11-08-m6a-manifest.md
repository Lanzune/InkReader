# M6：插件 SDK 与安全

**目标：** 把 QuickJS 示例插件扩展为可校验、可启停的插件基线，再补 `.inkp` 安装。  
**前置：** M5 完成。

## 拆分

| 子阶段 | 内容 | 状态 |
|---|---|---|
| M6a | manifest 校验、能力白名单、2s 超时、启停 | ✅ 真机 |
| M6b | `.inkp` 导入/安装、路径/大小限制 | ⏳ 待开始 |
| M6c | 示例插件文档、合并、标签 | ⏳ 待开始 |

## M6a 范围

- `manifest.json`：schemaVersion/id/name/version/entry/api/capabilities
- 能力白名单：
  - reader.state.read
  - theme.contribute
  - command.contribute
  - settings.readwrite
  - storage.plugin
  - log.write
- 未知能力/非法 id/路径穿越 entry/API 版本不匹配直接拒绝
- 脚本 ≤1 MB，激活 ≤2 s
- 主题/命令能力未声明时拒绝
- 插件启停持久化；禁用后使用内置主题

## 真机验证

- Home 显示 `dev.inkreader.paper v1.0.0 · theme.contribute, command.contribute`
- 点击“禁用示例插件”后显示内置主题状态
- 点击“启用示例插件”重新加载
- 截图：
  - `artifacts/device/m6-plugin-home.png`
  - `artifacts/device/m6-plugin-disabled.png`

## 测试

- Plugin manifest 3/3
- EPUB 7/7
- PDF 2/2
- HTML/Markdown 3/3
- App 构建通过

## 结果

| 项 | 结果 |
|---|---|
| APK | `artifacts/m6a/inkreader-0.6.0-m6a-debug.apk` |
| 版本 | `0.6.0`，versionCode 31 |
| 大小 | 14,968,458 bytes（约 14.27 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `98612D5EF5A4CC15A34FA36A3EF0B06154230963FDF08C663922D2CC1196DBB3` |

## 已知限制

- 仍是 assets 内置插件，不能导入 `.inkp`
- QuickJS 仍在应用进程内，不是进程隔离
- 超时是协程级，CPU 死循环仍可能占用执行线程
- 插件命令未动态注册到命令面板
- 存储/设置/reader.state 能力尚未暴露实际 API
