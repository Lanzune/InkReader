# 开发工作流

## 1. 工作方式

- **命令行优先**：能在 CLI 完成的构建、单元测试、静态检查、体积检查，不要求打开 IDE。
- **沙箱优先**：先在本地 JVM/临时目录验证逻辑，再进入 Android 运行时。
- **真机验证交给项目所有者**：涉及 SAF、系统 PDF、手势、字体渲染、页面动画、厂商兼容性的验证，由项目所有者安装 APK 后反馈。
- **阶段推进**：一个阶段有明确的进入条件、产物、退出标准和验证清单；未验收不进入下一阶段。

## 2. Git 规则

当前计划：

- 主分支：`main`
- 功能分支：`feat/<module>-<short>`，例如 `feat/txt-pagination`
- 文档分支：`docs/<topic>`
- 修复分支：`fix/<issue>`
- 阶段标签：`m0-bootstrap`、`m1-vertical-slice`、`m2-txt-reader`……

提交信息使用 Conventional Commits 风格：

```text
feat(txt): detect GB18030 and build chapter index
fix(plugin): reject manifest path traversal
docs(adr): record QuickJS plugin runtime decision
chore(repo): add Gradle wrapper and local SDK config
```

规则：

- 不提交 `local.properties`、签名密钥、token、设备序列号、构建产物。
- 不 force push `main`。
- 每个阶段完成时提交并推送；远程仓库建立后按阶段或 PR 合并。
- 提交前运行 `scripts/final_check.py` 或对应模块检查。
- GitHub 远端信息由项目所有者提供后配置，不在文档中写 token。

## 3. 环境约定

- 项目使用 **JDK 21** 构建；当前系统默认 `JAVA_HOME` 是 JDK 25，必须在执行 Gradle 前覆盖。
- Android SDK 使用 `local.properties` 中的 `sdk.dir`，不依赖全局 `ANDROID_HOME`。
- Gradle 使用项目 wrapper；当前机器已有 Gradle 8.14 分发包缓存，优先复用以避免重复下载。
- 构建命令统一从项目根目录执行，避免依赖当前目录的隐式路径。

示例（PowerShell，仅示意，实际路径以环境文档为准）：

```powershell
$env:JAVA_HOME = 'C:\Users\34553\scoop\apps\openjdk21\current'
.\gradlew.bat :app:assembleDebug
```

## 4. 测试分层

| 层级 | 谁执行 | 内容 |
|---|---|---|
| L0 单元测试 | 我 | 解析、编码、Locator、manifest、分页算法 |
| L1 集成/Robolectric | 我 | 文件源、会话状态、插件生命周期、错误路径 |
| L2 构建/静态检查 | 我 | `assembleDebug`、lint、APK 体积、权限检查、16 KB |
| L3 真机验证 | 项目所有者 | 安装、打开文件、翻页、字体、PDF、手势、系统分享入口、厂商兼容 |
| L4 回归/性能 | 双方 | 格式语料、Macrobenchmark、低端机、长时间阅读 |

我不把 L0–L2 的结果说成真机通过；真机结论以项目所有者反馈为准。

## 5. 真机验证协议

每次需要真机验证时，我提供：

1. APK 路径和版本/commit；
2. 安装方式；
3. 3–5 个最小步骤；
4. 预期结果；
5. 需要回传的信息：Android 版本、机型、截图/录屏、`adb logcat` 中相关崩溃；
6. 是否阻塞下一阶段。

项目所有者只需按清单执行；如果失败，保留 APK 和日志，不要自行清理。

## 6. 阶段收尾

每个阶段结束必须：

- [ ] 功能/模块文档已更新
- [ ] 自动测试通过
- [ ] APK 可构建
- [ ] 体积/权限/16 KB 检查通过（适用时）
- [ ] 真机清单已给出（适用时）
- [ ] 状态看板已更新
- [ ] git commit 完成
- [ ] 远程仓库已推送（远端建立后）

## 7. 分支与远程

GitHub 信息到位后：

1. 创建远程仓库（建议先 private）；
2. 添加 `origin`；
3. 首次推送 `main`；
4. 之后每个阶段使用功能分支，完成后合并/推送；
5. 在仓库设置中保护 `main`，禁止 force push；
6. 后续可加 GitHub Actions：构建、单元测试、APK 体积检查。
