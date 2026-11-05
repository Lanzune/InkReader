# 插件 API v0（草案）

> 状态：Draft  
> 适用：M1 垂直切片  
> 目标：先让主题/命令两类插件跑通，再扩展面板、设置和格式贡献。

## 1. 包格式

```text
my-plugin.inkp
├── manifest.json
├── main.js
└── assets/
```

`.inkp` 是 ZIP；不允许 `.so`、`.dex`、`.jar`，不允许路径穿越和符号链接。

## 2. manifest

```json
{
  "schemaVersion": 1,
  "id": "dev.example.papertheme",
  "name": "Paper Theme",
  "version": "1.0.0",
  "entry": "main.js",
  "api": { "minHostApi": 1, "targetHostApi": 1 },
  "capabilities": [
    "reader.state.read",
    "theme.contribute",
    "command.contribute"
  ]
}
```

必填：`schemaVersion`、`id`、`name`、`version`、`entry`、`api`。  
未知主版本、缺少能力、非法 ID/路径直接拒绝。

## 3. JS 入口

```javascript
export function activate(ink) {
  ink.themes.register({
    id: "paper",
    name: "Paper",
    light: { background: "#FAF7F0", text: "#2A2A2A" },
    dark: { background: "#121212", text: "#C9C9C9" }
  });

  ink.commands.register({
    id: "paper.showProgress",
    title: "显示阅读进度",
    run: async () => {
      const state = await ink.reader.getState();
      ink.ui.setBadge(`${Math.round(state.progress * 100)}%`);
    }
  });
}
```

可选 `deactivate()`。不允许顶层无限循环；激活有 2 秒上限。

## 4. v0 能力

| 能力 | 作用 | 备注 |
|---|---|---|
| `reader.state.read` | 书籍元数据、进度、当前主题 | 只读 |
| `theme.contribute` | 注册明/暗主题 | 不直接绘制 |
| `command.contribute` | 注册命令 | 命令有超时 |
| `settings.readwrite` | 插件设置 | 配额 5 MB |
| `storage.plugin` | 插件私有键值/文件 | 不能访问书籍文件 |
| `log.write` | 插件日志 | 环形缓冲 |

v0 **不提供**：网络、Android API、任意文件、Intent、自定义 View/Canvas、页面渲染、格式解析。

## 5. 宿主 API 形状

```text
ink.themes.register(theme)
ink.commands.register({ id, title, run })
ink.reader.getState() -> { book, locator, progress, themeId }
ink.settings.get(key) / set(key, value)
ink.storage.get(key) / set(key, value)
ink.log.info/warn/error(message)
ink.events.on("page.changed", handler)
```

所有调用异步或短时同步；不允许同步等待 UI。

## 6. 生命周期

```text
安装校验 → 用户授权 → 私有目录安装 → 激活 → 注册贡献
→ 运行 → 异常/超时 → 禁用 → 安全模式 → 卸载
```

## 7. 版本与兼容

- `api.minHostApi` / `targetHostApi`：主版本不匹配拒绝激活。
- 次版本只增不改；未知能力拒绝。
- 公开 API 至少保留两个次版本的弃用期。
- 每个 API 版本至少有一个契约测试插件。

## 8. 限额

| 项 | 值 |
|---|---|
| 包总大小 | 5 MB |
| 单 JS | 1 MB |
| 运行时内存 | 16 MB |
| 激活 | 2 s |
| UI 钩子 | 50 ms |
| 用户命令 | 2 s |
| 事件频率 | 100 ms 合并 |
| 私有存储 | 5 MB |

## 9. 安全声明

QuickJS 是可靠性和误操作边界，不是绝对安全边界。v0 在专用线程运行；v1.1 迁移独立进程。用户只应安装可信插件；卸载时删除插件私有数据。
