# M7：发布准备

**目标：** 产出可验证、可升级、体积受控的 release 构建。  
**前置：** M6 完成。

## 拆分

| 子阶段 | 内容 | 状态 |
|---|---|---|
| M7a | R8/release 构建、体积、16 KB、升级和真机回归 | ✅ 测试签名 |
| M7b | 正式 release keystore、签名 | ✅ 已构建 |
| M7c | 数据备份/恢复、正式包安装验收 | ✅ 真机 |
| M7d | AGPL-3.0-only、NOTICE、DCO、关于页 | ✅ 0.7.2 |
| M7 收尾 | 发布说明、许可证、标签 | ✅ |

## M7a 结果

- release 开启 R8 + resource shrink
- release-test 使用 debug keystore（仅本地验证，不可分发）
- 4.53 MB（debug 约 15.1 MB）
- 16 KB 对齐通过
- 无 `INTERNET`
- 覆盖安装保留最近阅读和插件状态
- EPUB 大书打开、插件加载正常

## M7b 阻塞项

正式发布需要项目所有者提供/确认 release keystore：

- keystore 文件（不入库）
- alias
- store/key 密码
- 是否接受卸载 debug 版导致本地阅读进度丢失

## M7 退出标准

- release 签名证书不是 debug
- 真机安装/升级/回退通过
- 体积、16 KB、权限复核
- 发布说明、许可证、版本信息齐全
- 项目所有者验收
