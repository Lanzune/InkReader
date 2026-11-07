# M6b `.inkp` 导入与安装（2026-11-08）

## 范围

- 新增 `.inkp` 安装器
- 通过系统文件选择器导入 `.inkp`
- ZIP 解析安全边界：
  - 总解压 ≤5 MB
  - 单脚本 ≤1 MB
  - 条目 ≤200
  - 禁止路径穿越
  - 禁止 DEX/JAR/SO/APK
  - manifest 必须能通过校验
  - entry 必须存在
- 安装到应用私有目录 `files/plugins/<id>/`
- 启动时发现已安装插件；优先于内置示例插件
- 卸载后回退到内置示例插件
- debug 构建增加“安装内置示例 .inkp”按钮

## 真机验证

- 安装内置示例 `.inkp`
- 私有目录生成：
  - `files/plugins/dev.inkreader.paper/manifest.json`
  - `files/plugins/dev.inkreader.paper/main.js`
- 首页显示卸载入口
- 卸载后目录清空，回到内置 `Paper` 示例
- 截图：
  - `artifacts/device/m6b-inkp-installed.png`
  - `artifacts/device/m6b-inkp-uninstalled.png`

## 测试

- Plugin manifest 3/3
- Plugin installer 3/3
  - 正常安装
  - 路径穿越拒绝
  - `.so` 拒绝
- EPUB 7/7
- PDF 2/2
- HTML/Markdown 3/3

## 结果

| 项 | 结果 |
|---|---|
| APK | `artifacts/m6b/inkreader-0.6.1-m6b-debug.apk` |
| 版本 | `0.6.1`，versionCode 32 |
| 大小 | 15,111,064 bytes（约 14.41 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `653E222685136AC83B75E2A4D0F8FCDF08F1DEF380E750AB94806318BC44A32E` |

## 已知限制

- 只支持一个已安装插件，后装覆盖前装
- 未做签名/来源信任，安装即信任
- ZIP 内 assets 会校验但不参与加载
- 插件仍与主应用同进程
- 安装器只读取系统选择器提供的 URI，不遍历其他文件
- 未实现权限授权页；能力校验在 manifest 层
