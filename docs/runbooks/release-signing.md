# Release 签名 Runbook

## 1. 生成/保管 keystore

由项目所有者执行，keystore 和密码不要入库：

```powershell
New-Item -ItemType Directory -Force .release | Out-Null
keytool -genkeypair `
  -keystore .release/inkreader-release.jks `
  -alias inkreader `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storepass "<STORE_PASSWORD>" `
  -keypass "<KEY_PASSWORD>" `
  -dname "CN=InkReader, O=InkReader, C=CN"
```

把 keystore 和密码备份到离线位置；丢失后无法覆盖升级。

## 2. 配置

复制 `keystore.properties.example` 为 `keystore.properties`（已 gitignore）：

```properties
storeFile=.release/inkreader-release.jks
storePassword=...
keyAlias=inkreader
keyPassword=...
```

## 3. 构建

```powershell
.\gradlew.bat :app:assembleRelease
```

## 4. 校验

```powershell
$apk = "app/build/outputs/apk/release/app-release.apk"
& "$env:ANDROID_HOME\build-tools\36.1.0\apksigner.bat" verify --print-certs $apk
& "$env:ANDROID_HOME\build-tools\36.1.0\zipalign.exe" -c -P 16 -v 4 $apk
```

证书 DN 不能是 `CN=Android Debug`。

## 5. 安装

正式包与 debug 包证书不同，不能覆盖安装。安装前确认是否接受卸载 debug 版导致本地阅读进度/插件丢失；先导出需要保留的数据。

## 6. 归档

- 记录 SHA-256、versionCode、证书指纹
- 保留 mapping.txt（`app/build/outputs/mapping/release/`）
- 不把 keystore、密码、mapping 上传到公开仓库
