# 本地构建手册

> 目标：在命令行完成 M0/M1 的 debug 构建、单元测试、APK 权限和 16 KB 检查。真机安装/交互验证由项目所有者执行。

## 1. 固定环境

```powershell
$env:JAVA_HOME = 'C:\Users\34553\scoop\apps\openjdk21\current'
$env:GRADLE_USER_HOME = 'C:\Project\DeepSeekHarness\inkreader\.gradle-user-home'
# 可选：避免 AGP 往用户目录写 analytics
$env:ANDROID_USER_HOME = 'C:\Project\DeepSeekHarness\inkreader\.android-user-home'
```

项目根目录：`C:\Project\DeepSeekHarness\inkreader`

`local.properties` 已指向本机 SDK，**不提交**：

```properties
sdk.dir=C\:\\Users\\34553\\AppData\\Local\\Android\\Sdk
```

## 2. 构建与单元测试

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/reports/tests/testDebugUnitTest/index.html
```

## 3. 权限检查

```powershell
$apk = "$PWD\app\build\outputs\apk\debug\app-debug.apk"
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\aapt2.exe" dump permissions $apk
```

期望：没有 `android.permission.INTERNET`。AndroidX 可能生成内部动态接收器权限，这是正常的。

## 4. 16 KB 对齐检查

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\zipalign.exe" -c -P 16 -v 4 $apk
```

期望最后一行包含 `Verification successful`。

## 5. 安装到真机

项目所有者执行：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r $apk
```

首次安装需要手机开启 USB 调试并授权。M0 只需确认应用能启动、显示 `InkReader` 和 `PageKernel API 1`。

## 6. 调试签名

调试签名使用项目内 `.debug/debug.keystore`，已被 `.gitignore` 忽略。若文件丢失，可重新生成：

```powershell
New-Item -ItemType Directory -Force -Path .debug | Out-Null
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore .debug\debug.keystore `
  -storepass android -alias androiddebugkey -keypass android `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=Android Debug,O=Android,C=US" -noprompt
```

正式发布签名不在 M0 处理，密钥不得提交仓库。

## 7. 已知警告

- Gradle 8.14 被 Kotlin 2.4 标为弃用；构建成功。计划在 Kotlin 2.5 前升级到 Gradle 8.14.4。
- 若未设置 `ANDROID_USER_HOME`，AGP 可能尝试写 `C:\Users\34553\.android\analytics.settings` 并被沙箱拒绝；不影响构建。
- 无 AVD/系统镜像；M0 运行验证用真实手机。
