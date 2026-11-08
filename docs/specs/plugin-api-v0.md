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
- 至少声明一个可执行贡献：`theme.contribute`、`command.contribute`、`progress.contribute`

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
    },
    progress: {
        render: function (state) {
            if (state.pageCount > 0) {
                return (state.page + 1) + "/" + state.pageCount;
            }
            return state.percent.toFixed(1) + "%";
        }
    }
};
```

宿主调用：

```javascript
JSON.stringify(inkPlugin.theme);
inkPlugin.command.render(0.42);
inkPlugin.progress.render({
  page: 9,
  pageCount: 4145,
  percent: 0.24,
  charOffset: 2700,
  totalChars: 1131577
});
```

`progress.contribute` 是唯一会保留运行时、在翻页后反复调用的贡献。宿主传入：

| 字段 | 含义 |
|---|---|
| `page` | 当前页，0 起 |
| `pageCount` | 全书页数；分页未就绪时为 0 |
| `percent` | 字符百分比，0–100 |
| `charOffset` | 当前全书字符偏移 |
| `totalChars` | 全书字符数；未知时为 0 |

插件返回短文本，宿主显示在阅读器顶栏。超时或异常时回退到内核百分比。

## 4. 能力

| 能力 | 状态 | 说明 |
|---|---|---|
| `theme.contribute` | ✅ | 主题 |
| `command.contribute` | ✅ | 命令结果文本 |
| `progress.contribute` | ✅ | 阅读器顶栏进度标签，可反复调用 |
| `reader.pagination` | ✅ | 申请全书页索引；未就绪时 `pageCount = 0` |
| `reader.state.read` | ✅（进度子集） | 只传进度状态，不传书籍内容 |
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
| 已安装插件 | 主题/命令 1；进度插件 1（独立目录） |

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
