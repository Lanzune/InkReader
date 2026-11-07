# InkReader 插件示例

## paper-theme

最小可安装插件，贡献一个主题和一个命令结果：

```text
paper-theme/
├── manifest.json
└── main.js
```

打包：

```powershell
Compress-Archive -Path .\paper-theme\* -DestinationPath .\paper-theme.inkp -Force
```

安装：

1. 打开 InkReader。
2. 首页点“导入 .inkp 插件”。
3. 选择生成的 `paper-theme.inkp`。
4. 安装成功后首页显示插件名称、版本和能力。
5. “卸载已安装插件”可回退内置示例。

注意：

- 扩展名必须是 `.inkp`，内容必须是 ZIP。
- 根目录必须有 `manifest.json`，`entry` 指向根目录内的 `.js`。
- 不能包含 DEX/JAR/SO/APK。
- 当前只支持一个已安装插件。
- 安装即信任，未做签名校验。

完整说明见 `docs/plugin-dev.md`。
