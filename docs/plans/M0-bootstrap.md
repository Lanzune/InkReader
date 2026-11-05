# M0：项目骨架与开发环境

**目标：** 建立可构建、可提交、可验证的 Android 工程骨架。  
**时间：** 1 周。  
**前置：** 技术报告、项目文件夹、环境审计。

## 已完成

- [x] 建立 `inkreader/` 项目目录
- [x] 移动技术报告、DOCX、图表和生成脚本
- [x] 建立模块目录骨架
- [x] 建立 README、文档索引、章程、基线、模块地图、路线图、工作流、环境、质量门槛
- [x] 只读审计 Android Studio / SDK / JDK / Gradle / Git
- [x] 确认 Git 身份：`Lanzune <3455312265@qq.com>`

## 待完成

- [ ] 初始化本地 git 仓库（`main`）
- [ ] 添加 `.gitattributes`
- [ ] 生成 Gradle wrapper（Gradle 8.14；复用本机缓存）
- [ ] 创建 `local.properties`（不提交），固定 SDK 路径
- [ ] 创建最小 Gradle 工程：`app`、`kernel/api`、`kernel/core`
- [ ] 锁定 AGP / Kotlin / Compose / compileSdk / targetSdk 版本
- [ ] 添加最小 `MainActivity`，显示项目名称和“打开书籍”占位
- [ ] 添加一个 JVM 单元测试
- [ ] `:app:assembleDebug` 成功
- [ ] 检查 APK 无 `INTERNET` 权限
- [ ] 完成 M0 首次提交并打标签 `m0-bootstrap`
- [ ] 项目所有者提供 GitHub 信息后配置 remote 并推送

## 技术参数基线

- `minSdk 26`
- `targetSdk 36`
- `compileSdk 36`（已安装 android-36）
- JDK 21（`C:\Users\34553\scoop\apps\openjdk21\current`）
- Gradle 8.14
- 临时 applicationId：`com.inkreader.app`（发布前可改）
- 不申请 `INTERNET`

## 构建命令

```powershell
$env:JAVA_HOME = 'C:\Users\34553\scoop\apps\openjdk21\current'
.\gradlew.bat :app:assembleDebug
```

## 退出标准

- 从干净命令行执行 `assembleDebug` 成功
- APK 可安装（若无设备则只验证构建和 manifest）
- 项目有第一次 git commit
- 状态看板更新
- 下一阶段 M1 的输入和验收清单已确认

## 不做

- 不做 EPUB/PDF
- 不做完整 UI
- 不安装模拟器镜像/不下载大依赖，除非项目所有者批准
- 不创建正式签名密钥
