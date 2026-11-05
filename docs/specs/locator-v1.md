# Locator v1

阅读进度不按“第几页”保存，而按稳定内容位置保存。字号、屏幕、主题变化后页码会变，但 Locator 不变。

## 数据结构

```json
{
  "version": 1,
  "format": "epub",
  "chapter": 12,
  "block": 48,
  "charOffset": 716,
  "pdfPage": null
}
```

| 字段 | 说明 |
|---|---|
| `version` | 结构版本；当前 1 |
| `format` | `txt` / `epub` / `pdf` / `html` / `md` |
| `chapter` | spine 或章节索引；PDF 为 null |
| `block` | 章节内块索引；PDF 为 null |
| `charOffset` | 块内或章节内字符偏移；PDF 为 null |
| `pdfPage` | PDF 页码；非 PDF 为 null |

## 恢复规则

1. 找到章节；失败则退到第 0 章。
2. 找到块；失败则退到章节开头。
3. `charOffset` 超出块长度时截断到块尾。
4. 布局后定位到包含该字符的页；没有则取最近页。
5. PDF 页码越界时取第 0 页。
6. 恢复失败不能导致崩溃；给出“已回到本章开头”的提示。

## 归一化进度

`progress` 是 0..1 的估算值，用于显示百分比，不用于恢复位置。

- TXT/EPUB：已读字符/总字符估算。
- PDF：`page / pageCount`。
- 页码显示以当前章节/当前 PDF 页为准，不承诺全书精确页数。

## 兼容性

- 增加字段是次版本变更；删除/改名是主版本变更。
- 旧 Locator 必须能读；新 Locator 在旧版本中至少能退回章节开头。
- 持久化前做边界检查，防止负索引/超大偏移。
