# M7c 数据迁移与正式包验收（2026-11-08）

## 背景

正式 release 签名与 debug 签名不同，不能覆盖升级。  
切换前增加应用内阅读数据导出/导入，避免卸载后丢失进度。

## 实现

- 首页新增：
  - 导出阅读数据
  - 导入阅读数据
- 备份 ZIP 内容：
  - `backup.json`：阅读状态、设置、插件启停
  - `book/book.epub`：当前书籍副本
  - `plugin/`：已安装插件的 manifest/main.js（如有）
- 恢复后书籍写入应用私有 `files/restored/`
- 通过 `file://` URI + `FileBookSource` 打开
- 解决卸载后 SAF URI 权限失效的问题

## 真机迁移

1. debug 0.7.1 导出 `/sdcard/inkreader-backup.zip`
   - 11,454,441 bytes
   - book.epub 12,552,017 bytes（压缩后 10.9 MB）
2. 卸载 debug 版
3. 安装 release 0.7.1（RSA 4096 release 签名）
4. 导入备份
5. 打开恢复后的书籍

## 验证结果

- 备份包含 `backup.json` + `book/book.epub`
- 恢复提示：“备份已恢复，阅读进度和设置已还原”
- 恢复后 122/4145 页，接近备份时的阅读位置
- 设置恢复：
  - 日间主题
  - 字号约 20 sp
  - 行距 1.2
  - 左右边距 15 dp
  - 翻页动画开
  - 亮度 56%
- 正式签名验证通过
- 无 `INTERNET`
- 16 KB 对齐通过

截图：

- `artifacts/device/m7c-restored-reader.png`
- `artifacts/device/m7c-restored-settings.png`

## 结果

| 项 | 结果 |
|---|---|
| APK | `artifacts/m7c/inkreader-0.7.1-release.apk` |
| 版本 | `0.7.1`，versionCode 34 |
| 大小 | 4,527,929 bytes（约 4.32 MiB） |
| 证书 | `CN=InkReader, OU=Personal, O=InkReader, C=CN` |
| SHA-256 | `530DEDA5101C8EF410812D07DD3F35CC774B0992A969500650F10F5478E973B1` |
| 签名校验 | ✅ Signer #1 release |
| 16 KB 对齐 | ✅ |

## 备份文件

- 手机：`/sdcard/inkreader-backup.zip`
- 本地副本：`artifacts/m7c/inkreader-backup.zip`
- SHA-256：`3EF4077FBA781232D7E028ECEBEA329E1B9F5B975B6FE29E7138A2F728E2CC7D`

该备份包含用户书籍，仅本地保存，不要上传公开仓库。

## 已知事项

- 备份只保存当前最近阅读的一本书；其他书需重新打开
- 已安装插件会随备份恢复；内置示例不写入备份
- 恢复后的书籍在应用私有目录，卸载会一并删除
- 正式包证书与 debug 不同，不能回退覆盖 debug 版
- 许可证尚未选择
