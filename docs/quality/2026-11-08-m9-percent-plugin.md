# M9.1 百分比内核与页码插件（0.9.0）

## 要求

> 页码当插件，百分比做内核，兼顾两方面的好处。

## 内核：字符百分比

阅读进度改为：

```text
当前字符偏移 / 全书总字符数
```

- 不依赖字号、行距、屏幕尺寸
- 打开时先进入局部排版，不再启动全书 `StaticLayout`
- 翻页只重新排版当前页，不等待页索引
- 页码插件关闭时，顶栏始终显示 `42.3%` 形式

总字符数来源：

| 场景 | 来源 |
|---|---|
| EPUB 已有章节索引 | 最后一章 `charOffset + length` |
| EPUB 新书 / 大书 | `estimateTextLength()` 流式扫 XHTML 文本长度，不保存图片和全文 |
| TXT/HTML/Markdown 完整解析后 | 正文字符长度 |
| 预览未完成 | 先显示 `…`，长度就绪后替换为百分比 |

## 页码插件

新增 `progress.contribute` 与 `reader.pagination` 能力。

内置第一方插件：`dev.inkreader.page-number`

- manifest：`app/src/main/assets/plugins/page-number.manifest.json`
- 脚本：`app/src/main/assets/plugins/page-number.js`
- 示例：`examples/page-number/`
- 默认关闭；首页可开启/关闭

插件契约：

```javascript
var inkPlugin = {
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

宿主每次翻页调用 `inkPlugin.progress.render(state)`，`state` 包含：

- `page`、`pageCount`
- `percent`、`charOffset`、`totalChars`

QuickJS 运行时在插件启用期间保留，超时 500 ms；异常、超时或插件关闭时回退内核百分比。

## 页索引的新位置

`PageIndexStore` 与全书分页没有删除，但只在页码插件启用时工作：

- 插件关闭：不读页索引、不启动全书分页、不写页索引
- 插件开启：按布局签名加载已有页索引；没有索引时构建并保存
- 页索引只在完整书内容上构建，避免把预览窗口的页数写成全书页数
- 有索引时，预览阶段也能立即显示页码；没有索引时先显示百分比

## 真机结果

设备：OnePlus Ace 5，Android 16  
书：12.5 MB EPUB

| 状态 | 结果 |
|---|---|
| 页码插件关闭 | 约 200 ms 显示 `0.2%`；等待 4 s 后仍为 `0.2%`，不加载页索引 |
| 页码插件开启，已有页索引 | 约 250 ms 显示 `30/4336` |
| 插件关闭后 | 顶栏回到百分比，页分页任务取消 |
| 插件启用/禁用 | 不崩溃，首页状态和错误提示正常 |
| 大书 | 135 MB 安全模式仍不整本入内存；文本长度扫描只保留单章 |

截图：

- [百分比内核](assets/m9-percent.png)
- [页码插件](assets/m9-page-plugin.png)

## 兼容性

- 页索引按布局签名隔离；换字号/行距/屏幕后页码会重新计算
- 页码是排版结果，不同布局下页数可能不同；百分比保持稳定
- 旧页索引文件保留，插件重新启用后仍可复用
- 备份新增 `pageNumberEnabled` 字段；旧备份默认关闭

## 限制

- 页码插件目前从内置 assets 加载，也可通过 `.inkp` 安装到独立的 `files/plugins-progress/`，与主题插件并存
- 进度插件目录当前只加载一个 `.inkp`；多进度插件并存和统一权限页尚未做
- 启用页码插件后，完整解析完成会立即应用完整书，放弃 M8c 的“按需切换”延迟；这是换取页索引和页码的代价
- 没有页索引的新书，页码插件会先显示百分比，等完整书分页完成后才显示页码
- 页码依赖布局签名；换字号/行距/屏幕后页数会变化，百分比保持稳定
- 大书安全模式只做百分比，不做全书页索引
- PDF 仍显示固定页 `1/2`，没有改成百分比

## 文件

- `app/src/main/assets/plugins/page-number.manifest.json`
- `app/src/main/assets/plugins/page-number.js`
- `plugin-host-quickjs/src/main/kotlin/com/inkreader/plugin/quickjs/QuickJsPluginHost.kt`
- `plugin-host-quickjs/src/main/kotlin/com/inkreader/plugin/quickjs/PluginManifest.kt`
- `reader-engine/src/main/kotlin/com/inkreader/reader/engine/ReaderSurfaceView.kt`
- `app/src/main/java/com/inkreader/app/MainActivity.kt`
- `app/src/main/java/com/inkreader/app/DataBackup.kt`
- `examples/page-number/`

APK：`artifacts/perf1/inkreader-0.9.0-progress.apk`（4,575,046 bytes，SHA-256 `1CE5A90638CE4F3FA05C626B3618B92EDE07333390CFEB481ED703A08EEF60BC`）
