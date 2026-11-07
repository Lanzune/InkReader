# M2b9 上部安全区调整与真机验证（2026-11-07）

## 调整

- 阅读区顶部 padding：80dp
- 页级顶部字形安全区：16dp
- 底部保持：56dp + 24dp
- 有效顶部安全区从 `96 + 48 = 144dp` 降到 `80 + 16 = 96dp`，与底部 `56 + 24 = 80dp` 更接近。

同时修复 `ReaderSurfaceView.onDraw` 的 `canvas.save()` 被错误注释导致 `Canvas.restore` underflow 的崩溃。

## 真机验证方式

使用 USB 调试，直接通过 adb 安装、启动、点击继续阅读、截图和翻页，不再手动传输 APK。

- 只安装/启动 `com.inkreader.app`
- 只截取 InkReader 前台界面
- 未修改系统设置，未读取其他应用数据

调试过程中有一次截图时桌面在前台，截到了桌面；该文件已立即删除，没有读取或使用任何其他应用信息。后续截图前均确认 InkReader 在前台。

## 构建

```powershell
$env:JAVA_HOME = 'C:\Users\34553\scoop\apps\openjdk21\current'
$env:GRADLE_USER_HOME = 'C:\Project\DeepSeekHarness\inkreader\.gradle-user-home'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :formats:txt:test --no-daemon
```

## 结果

| 项 | 结果 |
|---|---|
| 构建/单测 | ✅ |
| 真机安装 | ✅ |
| 阅读器启动 | ✅（无崩溃） |
| 页首/页尾 | ✅ 连续翻页完整 |
| APK | `artifacts/m2b9/inkreader-0.2.9-m2b9-debug.apk` |
| 版本 | `0.2.9`，versionCode 14 |
| 大小 | 14,846,322 bytes（约 14.16 MiB） |
| 权限 | 无 `INTERNET` |
| 16 KB 对齐 | ✅ |
| SHA-256 | `C1659E64E07F2012351F7042065BB405E3FEFF20F388B994D814F6ADA9E63E63` |

## 真机截图

- `artifacts/device/m2b9-reader-final.png`
- `artifacts/device/m2b9-next-page.png`

## 后续

- 视觉确认后可合并 `feat/m2b-settings`，打 `m2-txt-reader` 标签。
- 进入 M3 EPUB。
