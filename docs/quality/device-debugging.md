# 真机直接调试

## 一次性连接

### USB（推荐先做）

1. 手机设置 → 关于手机 → 连续点击版本号 7 次，打开开发者选项。
2. 开发者选项 → USB 调试。
3. 数据线连接电脑，手机上点“允许”。
4. 电脑执行：

```powershell
& 'C:\Users\34553\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices
```

看到 `device` 即可。

### 无线调试（同一 Wi-Fi）

1. 开发者选项 → 无线调试 → 开启。
2. 点“使用配对码配对设备”，记下 `IP:端口` 和 6 位配对码。
3. 电脑执行：

```powershell
adb pair 手机IP:配对端口
# 输入配对码
adb connect 手机IP:调试端口
adb devices
```

注意：配对端口和无线调试主页显示的调试端口通常不同。

## 一键构建、安装、启动、截图

```powershell
.\scripts\device-debug.cmd all
```

如果系统执行策略允许，也可以直接运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\device-debug.ps1 all
```

会输出截图路径，例如：

```text
artifacts/device/inkreader-20261107-200000.png
```

也可以分开执行：

```powershell
.\scripts\device-debug.cmd build
.\scripts\device-debug.cmd install
.\scripts\device-debug.cmd launch
.\scripts\device-debug.cmd screenshot
```

这样不需要手动把 APK 传到手机，也不需要手动截图再上传。

## Android Studio 的“热启动”

- **Apply Changes**：把代码/资源改动推到已运行的 App；Compose UI 改动通常可直接生效。
- **Live Edit**：面向 Compose 的实时编辑，需要 Android Studio 和可调试构建。
- 阅读器核心是自定义 `ReaderSurfaceView` + Canvas，修改 `ReaderSurfaceView` 的布局/绘制逻辑通常仍需要重新安装并重启 Activity。

所以当前阶段推荐：

- 一般 UI 改动：Android Studio Apply Changes；
- 阅读引擎/分页改动：`scripts/device-debug.ps1 all`；
- 需要实时看屏幕：Android Studio 的 Running Devices 镜像，或安装 scrcpy。

## 安全

- USB 调试只对已授权的电脑开放。
- 无线调试配对码只在局域网内使用；调试完可在开发者选项里关闭无线调试或撤销授权。
