# 技术报告

本目录保存项目初始的完整技术方案与实施报告。

| 文件 | 说明 |
|---|---|
| `ebook-reader-technical-report.zh-CN.md` | Markdown 主稿，唯一内容源 |
| `ebook-reader-technical-report.zh-CN.docx` | 由主稿生成的 46 页 Word 报告 |
| `assets/architecture.png` | 总体架构图 |
| `assets/plugin-lifecycle.png` | 插件生命周期图 |

## 生成方式

从项目根目录执行：

```powershell
& 'C:\Users\34553\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe' scripts\render_diagrams.py
& 'C:\Users\34553\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe' scripts\build_report_docx.py
& 'C:\Users\34553\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe' scripts\final_check.py
```

脚本只依赖工作区内的 Python 运行时和 `python-docx`/`Pillow`。不要直接编辑 DOCX；修改 Markdown 后重新生成。

## 报告与当前开发文档的关系

- 报告是**完整论证**：需求、选型、架构、格式、排期、风险。
- `docs/00–07` 是**执行文档**：基线、模块、阶段、工作流、环境、质量、状态。
- 两者冲突时，以当前阶段文档和 ADR 为准，并回写报告或记录偏差。

## Word 版视觉检查

DOCX 已用 LibreOffice Kit 渲染检查过封面、目录、架构图、插件生命周期图、表格、代码块和附录；正文约 46 页。若重新生成后版式变化，重新渲染检查。
