# -*- coding: utf-8 -*-
from pathlib import Path

from docx import Document

p = Path("docs/reports/ebook-reader-technical-report.zh-CN.docx")
doc = Document(p)
print("paragraphs", len(doc.paragraphs), "tables", len(doc.tables), "images", len(doc.inline_shapes))
heads = [(x.style.name, x.text) for x in doc.paragraphs if x.style.name.startswith("Heading")]
print("headings", len(heads))
for h in heads[:30]:
    print(h)
alltext = "\n".join(x.text for x in doc.paragraphs)
for t in doc.tables:
    for row in t.rows:
        for c in row.cells:
            alltext += "\n" + c.text
print("chars", len(alltext))
for needle in ["PageKernel", "QuickJS", "18–22 周", "12 MB", "附录 C", "第 4 章"]:
    print(needle, alltext.count(needle))
print("literal **", alltext.count("**"))
print("literal backtick", alltext.count("`"))
print("literal TODO", alltext.count("TODO"))
print("images", [(round(s.width.cm, 2), round(s.height.cm, 2)) for s in doc.inline_shapes])
print("sections", len(doc.sections), "different first", doc.sections[0].different_first_page_header_footer)
print("header", doc.sections[0].header.paragraphs[0].text)
print("footer", doc.sections[0].footer.paragraphs[0].text)
