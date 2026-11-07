# M7b 正式 Release 签名（2026-11-08）

## 结果

- 已生成独立 release keystore
- 已构建正式签名 release APK
- 包名/版本：`com.inkreader.app` / `0.7.0` (33)
- 大小：4,527,929 bytes（约 4.32 MiB）
- 16 KB 对齐：✅
- 无 `INTERNET`
- 签名校验：✅

## 证书

| 项 | 值 |
|---|---|
| DN | `CN=InkReader, OU=Personal, O=InkReader, C=CN` |
| SHA-256 | `07E08D20E75B60FA1FB749AC3CA5FCDE23406DEB09B22F5CD387CF13D2D7E22B` |
| 密钥 | RSA 4096 |
| 有效期 | 10000 天 |
| keystore | `.release/inkreader-release.jks`（已 gitignore） |

## 产物

| 文件 | SHA-256 |
|---|---|
| `artifacts/m7b/inkreader-0.7.0-release.apk` | `B717A8FDABC18F246B2F352089788EACE8DC4E2CB0DA7D2948FA9B59C170F11A` |
| `artifacts/m7b/inkreader-release-signing-backup.zip` | `97699968F5B0EBF1BD6F40134AC2116253B67A298AF9A2DA6643D452F458E5E2` |

备份 ZIP 包含：

- `inkreader-release.jks`
- `CREDENTIALS.txt`（alias/密码/DN）
- `keystore.properties`

## 重要：尚未安装到手机

当前手机装的是 **debug 签名**的 0.7.0。正式包是**独立 release 签名**，不能覆盖安装：

- 系统会报签名不一致
- 必须卸载 debug 版再安装正式包
- 卸载会删除本地阅读进度、设置、已安装插件

所以签名完成后没有执行安装，手机数据保持原样。

## 数据迁移

切换正式包前应先做一次数据备份/导出。当前尚未实现导出入口；下一步可选：

1. 增加应用内“导出/导入阅读数据”，先备份再切换
2. 接受进度丢失，直接卸载重装
3. 暂不切换，继续使用当前 debug 版

## 后续

- M7c：数据迁移、正式包真机验收、许可证、最终标签。
- 发布签名文档：`docs/runbooks/release-signing.md`。
