# 插件 API v0

> 状态：M6 基线已实现
> 实际开发说明：`docs/plugin-dev.md`
> 示例：`examples/paper-theme/`

## 1. 包格式

```text
my-plugin.inkp        # ZIP
├── manifest.json
├── main.js
└── assets/           # 可选，当前只校验不加载
```

不允许 `.dex`、`.jar`、`.so`、`.apk`，不允许路径穿越。

## 2. manifest

```json
{
  "schemaVersion": 1,
  "id": "dev.inkreader.example.paper",
  "name": "Example Paper",
  "version": "1.0.0",
  "entry": "main.js",
  "api": { "minHostApi": 1, "targetHostApi": 1 },
  "capabilities": ["theme.contribute", "command.contribute"]
}
```

必填：`schemaVersion`、`id`、`name`、`version`、`entry`、`api`、`capabilities`。

校验：

- `schemaVersion = 1`
- `id` 匹配 `[a-z][a-z0-9._-]{1,80}`
- `entry` 为包内 `.js`，无路径穿越
- `api` 两项均为 1
- 能力非空且在白名单内
- 当前宿主要求同时声明 theme/command 能力

## 3. 当前 JS 契约

宿主执行脚本后读取全局 `inkPlugin`：

```javascript
var inkPlugin = {
    id: "dev.inkreader.example.paper",
    theme: {
        id: "example.paper",
        name: "Example Paper",
        background: "#F4EBDD",
        foreground: "#332B24",
        accent: "#7A5230",
        dark: false
    },
    command: {
        title: "显示阅读进度",
        render: function (progress) {
            return "示例插件进度：" + Math.round(progress * 100) + "%";
        }
    }
};
```

宿主调用 `JSON.stringify(inkPlugin.theme)` 和 `inkPlugin.command.render(0.42)`。

> `progress` 当前固定 0.42，仅用于验证链路；真实阅读状态 API 尚未接入。

## 4. 能力

| 能力 | 状态 | 说明 |
|---|---|---|
| `theme.contribute` | ✅ | 主题 |
| `command.contribute` | ✅ | 命令结果文本 |
| `reader.state.read` | ⏳ | 预留 |
| `settings.readwrite` | ⏳ | 预留 |
| `storage.plugin` | ⏳ | 预留 |
| `log.write` | ⏳ | 预留 |

## 5. 限制

| 项 | 值 |
|---|---|
| 包总解压 | 5 MB |
| 单 JS | 1 MB |
| ZIP 条目 | 200 |
| 激活 | 2 s |
| 私有存储 | 5 MB（规划） |
| 已安装插件 | 1 |

## 6. 安全边界

QuickJS 沙箱 + manifest 白名单，不是绝对安全边界。当前插件与主应用同进程，2 秒超时为协程级；v1.1 迁移独立进程。安装即信任，没有签名校验。

v0 不提供网络、Android API、任意文件、Intent、自定义 View/Canvas、页面渲染、格式解析。

## 7. 后续 API

```text
ink.themes.register(theme)
ink.commands.register({ id, title, run })
ink.reader.getState() -> { book, locator, progress, themeId }
ink.settings.get(key) / set(key, value)
ink.storage.get(key) / set(key, value)
ink.log.info/warn/error(message)
ink.events.on("page.changed", handler)
```

以上为 v0 草案的后续方向，尚未实现。
