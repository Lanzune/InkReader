# InkReader 插件开发（v0 基线）

> 状态：M6 实现基线  
> 适用：InkReader 0.6.x  
> 安全模型：QuickJS 沙箱 + manifest 能力白名单；不是进程隔离。

## 1. 可以做什么

当前实现支持三类贡献：

- 主题：背景色、正文色、强调色、明/暗标记
- 命令结果：根据阅读进度返回一段文本，显示在首页和阅读器命令面板
- 进度标签：顶栏显示页码/百分比，翻页时由宿主反复调用

当前**不提供**：

- 网络
- Android API / Context / Uri / File
- 自定义 View / Canvas / 页面渲染
- 格式解析
- 多插件并存
- 插件命令动态注册（只返回命令文本）
- 插件签名校验

## 2. 包结构

```text
my-plugin.inkp        # ZIP
├── manifest.json
├── main.js
└── assets/           # 可选，当前只校验，不加载
```

限制：

| 项 | 限制 |
|---|---|
| 包总解压大小 | 5 MB |
| 单 JS | 1 MB |
| ZIP 条目 | 200 |
| 激活时间 | 2 s |
| 已安装插件数量 | 主题/命令 1；进度插件 1（独立目录） |

禁止包含 `.dex`、`.jar`、`.so`、`.apk`；禁止路径穿越。

## 3. manifest.json

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

校验规则：

- `schemaVersion` 必须为 1
- `id` 匹配 `[a-z][a-z0-9._-]{1,80}`
- `entry` 必须是包内 `.js`，不能以 `/` 开头或包含 `..`
- `api.minHostApi` / `targetHostApi` 必须为 1
- `capabilities` 不能为空，不能包含未知能力
- 至少声明一个可执行贡献：`theme.contribute`、`command.contribute`、`progress.contribute`

## 4. main.js 实际契约

当前宿主执行脚本后读取全局 `inkPlugin`：

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
            return state.totalChars > 0 ? state.percent.toFixed(1) + "%" : "…";
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

`progress.contribute` 会保留 QuickJS 运行时，每次翻页由宿主调用；返回值直接显示在顶栏。分页未就绪或插件超时/异常时，宿主回退到内核百分比。

## 5. 能力

| 能力 | M6 状态 | 说明 |
|---|---|---|
| `theme.contribute` | ✅ 生效 | 读取 `inkPlugin.theme` |
| `command.contribute` | ✅ 生效 | 读取 `inkPlugin.command.render` |
| `progress.contribute` | ✅ 生效 | 顶栏进度标签，翻页时反复调用 |
| `reader.pagination` | ✅ 生效 | 请求全书页索引；未就绪时 `pageCount = 0` |
| `reader.state.read` | ✅ 进度子集 | 只传进度字段 |
| `settings.readwrite` | ⏳ 预留 | 尚未暴露 |
| `storage.plugin` | ⏳ 预留 | 尚未暴露 |
| `log.write` | ⏳ 预留 | 尚未暴露 |

未知能力会被拒绝。

## 6. 安装与卸载

1. 首页点“导入 .inkp 插件”
2. 选择 `.inkp`
3. 校验通过后写入 `files/plugins/<id>/`
4. 启动时优先加载已安装插件
5. 首页“卸载已安装插件”删除私有目录并回退内置示例

页码插件的 `.inkp` 安装到独立目录 `files/plugins-progress/`，不会替换主题插件。首页“安装页码插件（.inkp）”用于安装内置第一方示例；安装后可卸载并回退内置版本。

安装失败会显示错误，不会覆盖已有插件。

## 7. 安全与信任

- QuickJS 运行在应用进程的专用调度线程，不是独立进程
- 2 秒超时是协程级；CPU 死循环仍可能占用线程
- 安装即信任：没有签名/来源验证
- 插件只能通过宿主读取 `inkPlugin`，拿不到 Android 对象
- 卸载会删除插件私有目录
- 只应安装自己信任的插件

## 8. 示例

- 主题/命令：`examples/paper-theme/`
- 页码进度：`examples/page-number/`
- 内置默认加载：`app/src/main/assets/plugins/page-number.js`

页码插件目前作为第一方内置插件加载；`.inkp` 安装版留给后续多插件管理器。

```powershell
Compress-Archive -Path .\examples\paper-theme\* -DestinationPath .\paper-theme.inkp -Force
```

## 9. 后续

- v1.1：独立进程
- 多插件并存与权限授权页
- `reader.state` / settings / storage API
- 插件命令注册到命令面板
- 签名/来源校验
