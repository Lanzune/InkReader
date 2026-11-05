# -*- coding: utf-8 -*-
from __future__ import annotations

import hashlib
import re
from pathlib import Path

from docx import Document

root = Path(__file__).resolve().parents[1]
md_path = root / "docs" / "reports" / "ebook-reader-technical-report.zh-CN.md"
docx_path = root / "docs" / "reports" / "ebook-reader-technical-report.zh-CN.docx"

md = md_path.read_text(encoding="utf-8")
doc = Document(docx_path)

assert "TODO" not in md
assert "```" in md
assert "assets/architecture.png" in md
assert "assets/plugin-lifecycle.png" in md
for rel in ("architecture.png", "plugin-lifecycle.png"):
    assert (root / "docs" / "reports" / "assets" / rel).exists(), rel

assert len(doc.inline_shapes) == 2
assert len(doc.tables) >= 20
assert len(doc.paragraphs) > 600

text = "\n".join(p.text for p in doc.paragraphs)
for table in doc.tables:
    for row in table.rows:
        for cell in row.cells:
            text += "\n" + cell.text

assert "**" not in text
assert "```" not in text
assert "PageKernel" in text
assert "QuickJS" in text
assert "PdfRenderer" in text
assert "18–22" in text
assert "12 MB" in text
assert "附录 C" in text

md_h2 = [line[3:].strip() for line in md.splitlines() if line.startswith("## ") and line[3:].strip() != "技术方案与实施报告"]
doc_h1 = [p.text.strip() for p in doc.paragraphs if p.style.name == "Heading 1"]
missing = [h for h in md_h2 if h not in doc_h1]
assert not missing, missing

for p in (md_path, docx_path):
    digest = hashlib.sha256(p.read_bytes()).hexdigest()
    print(f"{p.name}: {p.stat().st_size} bytes, sha256={digest}")
print(f"markdown_chars={len(md)} docx_paragraphs={len(doc.paragraphs)} docx_tables={len(doc.tables)} docx_images={len(doc.inline_shapes)} h2={len(md_h2)}")
print("FINAL_CHECK_OK")
