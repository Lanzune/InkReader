# -*- coding: utf-8 -*-
"""将报告 Markdown 主稿转换为排版后的 DOCX。"""

from __future__ import annotations

import re
from datetime import datetime
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.opc.constants import RELATIONSHIP_TYPE as RT
from docx.shared import Cm, Pt, RGBColor

ROOT = Path(__file__).resolve().parents[1]
MD_PATH = ROOT / "docs" / "reports" / "ebook-reader-technical-report.zh-CN.md"
OUT_PATH = ROOT / "docs" / "reports" / "ebook-reader-technical-report.zh-CN.docx"
ASSET_DIR = ROOT / "docs" / "reports"

BODY_START = "## 摘要"
REPORT_TITLE = "轻量级、插件化的 Android 本地电子书阅读器"
REPORT_SUBTITLE = "技术方案与实施报告"
REPORT_TAGLINE = "从需求分析到可交付 APK 的架构、排期与风险评估"

BODY_FONT_CN = "宋体"
BODY_FONT_EN = "Calibri"
HEAD_FONT_CN = "微软雅黑"
HEAD_FONT_EN = "Microsoft YaHei"
MONO_FONT = "Consolas"

COLOR_INK = RGBColor(0x17, 0x21, 0x2B)
COLOR_MUTED = RGBColor(0x5E, 0x6B, 0x78)
COLOR_NAVY = RGBColor(0x17, 0x3A, 0x58)
COLOR_BLUE = RGBColor(0x24, 0x4A, 0x78)
COLOR_LINE = "C9D1D9"
COLOR_TABLE_HEADER = "E5EEF9"
COLOR_TABLE_ALT = "F7F9FC"
COLOR_CODE_BG = "F4F6F8"
COLOR_CODE_INK = RGBColor(0x1F, 0x3A, 0x4D)
COLOR_LINK = RGBColor(0x1F, 0x5F, 0xAD)
COLOR_INLINE_CODE = RGBColor(0x9C, 0x3A, 0x2C)

TOKEN_RE = re.compile(r"(\*\*.+?\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))")
FIGURE_INDEX = 0


# ---------------------------------------------------------------------------
# 基础格式工具
# ---------------------------------------------------------------------------
def set_east_asia(target, font_cn: str, font_en: str) -> None:
    """同时设置西文与中文（eastAsia）字体，适用于 style 或 run。"""
    target.font.name = font_en
    rpr = target._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), font_en)
    rfonts.set(qn("w:hAnsi"), font_en)
    rfonts.set(qn("w:eastAsia"), font_cn)
    rfonts.set(qn("w:cs"), font_en)


def font_run(run, *, size: float = 10.5, bold: bool = False, color: RGBColor | None = None,
             mono: bool = False, italic: bool = False) -> None:
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    if color is not None:
        run.font.color.rgb = color
    if mono:
        set_east_asia(run, MONO_FONT, MONO_FONT)
        run.font.name = MONO_FONT
    else:
        set_east_asia(run, BODY_FONT_CN, BODY_FONT_EN)


def set_paragraph_border(paragraph, *, bottom: bool = False, left: bool = False,
                         color: str = COLOR_LINE, size: int = 4, space: int = 4) -> None:
    ppr = paragraph._p.get_or_add_pPr()
    pbdr = ppr.find(qn("w:pBdr"))
    if pbdr is None:
        pbdr = OxmlElement("w:pBdr")
        ppr.append(pbdr)
    for edge in ("bottom", "left"):
        if not (bottom if edge == "bottom" else left):
            continue
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), str(size))
        el.set(qn("w:space"), str(space))
        el.set(qn("w:color"), color)
        pbdr.append(el)


def shade_paragraph(paragraph, fill: str) -> None:
    ppr = paragraph._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    ppr.append(shd)


def shade_cell(cell, fill: str) -> None:
    tcpr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    tcpr.append(shd)


def set_cell_margins(cell, top: int = 80, bottom: int = 80, left: int = 100, right: int = 100) -> None:
    tcpr = cell._tc.get_or_add_tcPr()
    mar = OxmlElement("w:tcMar")
    for name, value in (("top", top), ("bottom", bottom), ("start", left), ("end", right)):
        el = OxmlElement(f"w:{name}")
        el.set(qn("w:w"), str(value))
        el.set(qn("w:type"), "dxa")
        mar.append(el)
    tcpr.append(mar)


def set_table_borders(table, color: str = "C9D1D9", size: int = 4) -> None:
    tblpr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), str(size))
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), color)
        borders.append(el)
    tblpr.append(borders)


def repeat_header(row) -> None:
    trpr = row._tr.get_or_add_trPr()
    hdr = OxmlElement("w:tblHeader")
    hdr.set(qn("w:val"), "true")
    trpr.append(hdr)


def prevent_row_split(row) -> None:
    trpr = row._tr.get_or_add_trPr()
    cant = OxmlElement("w:cantSplit")
    cant.set(qn("w:val"), "true")
    trpr.append(cant)


def add_hyperlink(paragraph, url: str, text: str, *, size: float = 10.5) -> None:
    part = paragraph.part
    r_id = part.relate_to(url, RT.HYPERLINK, is_external=True)
    hyperlink = OxmlElement("w:hyperlink")
    hyperlink.set(qn("r:id"), r_id)
    run = OxmlElement("w:r")
    rpr = OxmlElement("w:rPr")
    rfonts = OxmlElement("w:rFonts")
    rfonts.set(qn("w:ascii"), BODY_FONT_EN)
    rfonts.set(qn("w:hAnsi"), BODY_FONT_EN)
    rfonts.set(qn("w:eastAsia"), BODY_FONT_CN)
    rpr.append(rfonts)
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "1F5FAD")
    rpr.append(color)
    u = OxmlElement("w:u")
    u.set(qn("w:val"), "single")
    rpr.append(u)
    sz = OxmlElement("w:sz")
    sz.set(qn("w:val"), str(int(size * 2)))
    rpr.append(sz)
    run.append(rpr)
    t = OxmlElement("w:t")
    t.set(qn("xml:space"), "preserve")
    t.text = text
    run.append(t)
    hyperlink.append(run)
    paragraph._p.append(hyperlink)


def add_runs(paragraph, text: str, *, size: float = 10.5, base_bold: bool = False,
             color: RGBColor | None = None, table: bool = False) -> None:
    pos = 0
    for match in TOKEN_RE.finditer(text):
        if match.start() > pos:
            run = paragraph.add_run(text[pos:match.start()])
            font_run(run, size=size, bold=base_bold, color=color)
        token = match.group(0)
        if token.startswith("**") and token.endswith("**") and len(token) > 4:
            run = paragraph.add_run(token[2:-2])
            font_run(run, size=size, bold=True, color=color)
        elif token.startswith("`") and token.endswith("`"):
            run = paragraph.add_run(token[1:-1])
            font_run(run, size=max(8.0, size - 0.8), bold=base_bold, color=COLOR_INLINE_CODE, mono=True)
        else:
            m = re.match(r"\[([^\]]+)\]\(([^)]+)\)", token)
            if m:
                add_hyperlink(paragraph, m.group(2), m.group(1), size=size)
            else:
                run = paragraph.add_run(token)
                font_run(run, size=size, bold=base_bold, color=color)
        pos = match.end()
    if pos < len(text):
        run = paragraph.add_run(text[pos:])
        font_run(run, size=size, bold=base_bold, color=color)
    # 保证表格内段落没有首行缩进
    if table:
        paragraph.paragraph_format.first_line_indent = Cm(0)


# ---------------------------------------------------------------------------
# 样式与页面
# ---------------------------------------------------------------------------
def setup_styles(doc: Document) -> None:
    normal = doc.styles["Normal"]
    set_east_asia(normal, BODY_FONT_CN, BODY_FONT_EN)
    normal.font.size = Pt(10.5)
    normal.paragraph_format.line_spacing = 1.5
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.first_line_indent = Pt(21)
    normal.paragraph_format.widow_control = True

    for name, size, color, before, after in (
        ("Heading 1", 18, COLOR_NAVY, 16, 8),
        ("Heading 2", 14, COLOR_BLUE, 12, 6),
        ("Heading 3", 12, RGBColor(0x3D, 0x5A, 0x73), 8, 4),
    ):
        style = doc.styles[name]
        set_east_asia(style, HEAD_FONT_CN, HEAD_FONT_EN)
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = color
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True
        style.paragraph_format.line_spacing = 1.2
        style.paragraph_format.first_line_indent = Cm(0)
        if name == "Heading 1":
            style.paragraph_format.page_break_before = True

    # 表格单元格
    table_style = doc.styles.add_style("ReportTableText", 1)  # WD_STYLE_TYPE.PARAGRAPH == 1
    set_east_asia(table_style, BODY_FONT_CN, BODY_FONT_EN)
    table_style.font.size = Pt(9)
    table_style.paragraph_format.first_line_indent = Cm(0)
    table_style.paragraph_format.line_spacing = 1.05
    table_style.paragraph_format.space_after = Pt(0)
    table_style.paragraph_format.space_before = Pt(0)

    # 代码块
    code_style = doc.styles.add_style("ReportCode", 1)
    code_style.font.name = MONO_FONT
    code_style.font.size = Pt(8.5)
    code_style.paragraph_format.first_line_indent = Cm(0)
    code_style.paragraph_format.line_spacing = 1.0
    code_style.paragraph_format.space_before = Pt(3)
    code_style.paragraph_format.space_after = Pt(8)
    set_east_asia(code_style, MONO_FONT, MONO_FONT)

    caption_style = doc.styles.add_style("ReportCaption", 1)
    set_east_asia(caption_style, BODY_FONT_CN, BODY_FONT_EN)
    caption_style.font.size = Pt(9)
    caption_style.font.color.rgb = COLOR_MUTED
    caption_style.paragraph_format.first_line_indent = Cm(0)
    caption_style.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption_style.paragraph_format.space_before = Pt(4)
    caption_style.paragraph_format.space_after = Pt(10)


def setup_section(section) -> None:
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.0)
    section.bottom_margin = Cm(2.0)
    section.left_margin = Cm(2.1)
    section.right_margin = Cm(2.1)
    section.header_distance = Cm(1.0)
    section.footer_distance = Cm(1.0)
    section.different_first_page_header_footer = True


def add_page_field(paragraph, instr: str, placeholder: str = "1") -> None:
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr_el = OxmlElement("w:instrText")
    instr_el.set(qn("xml:space"), "preserve")
    instr_el.text = instr
    sep = OxmlElement("w:fldChar")
    sep.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t")
    text.text = placeholder
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    for el in (begin, instr_el, sep, text, end):
        run._r.append(el)
    font_run(run, size=8.5, color=COLOR_MUTED)


def setup_header_footer(doc: Document) -> None:
    section = doc.sections[0]
    # 封面不要页眉页脚
    section.different_first_page_header_footer = True

    header = section.header
    p = header.paragraphs[0]
    p.text = ""
    p.paragraph_format.space_after = Pt(2)
    run = p.add_run("InkReader · 技术方案与实施报告")
    font_run(run, size=8.5, color=COLOR_MUTED)
    set_paragraph_border(p, bottom=True, color="D6DEE6", size=4)

    footer = section.footer
    fp = footer.paragraphs[0]
    fp.text = ""
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    fp.paragraph_format.space_before = Pt(2)
    run = fp.add_run("第 ")
    font_run(run, size=8.5, color=COLOR_MUTED)
    add_page_field(fp, " PAGE ")
    run = fp.add_run(" 页 / 共 ")
    font_run(run, size=8.5, color=COLOR_MUTED)
    add_page_field(fp, " NUMPAGES ")
    run = fp.add_run(" 页")
    font_run(run, size=8.5, color=COLOR_MUTED)


# ---------------------------------------------------------------------------
# 封面与目录
# ---------------------------------------------------------------------------
def add_cover(doc: Document) -> None:
    # 顶部色带
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(36)
    p.paragraph_format.space_after = Pt(4)
    run = p.add_run("INKREADER  /  PAGEKERNEL")
    font_run(run, size=10, bold=True, color=COLOR_BLUE)

    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_after = Pt(22)
    set_paragraph_border(p, bottom=True, color="173A58", size=18)

    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(24)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.line_spacing = 1.15
    run = p.add_run("轻量级、插件化的 Android")
    font_run(run, size=26, bold=True, color=COLOR_NAVY)
    set_east_asia(run, HEAD_FONT_CN, HEAD_FONT_EN)
    run.add_break(WD_BREAK.LINE)
    run.add_text("本地电子书阅读器")
    font_run(run, size=26, bold=True, color=COLOR_NAVY)

    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_after = Pt(12)
    run = p.add_run(REPORT_SUBTITLE)
    font_run(run, size=18, bold=True, color=COLOR_BLUE)
    set_east_asia(run, HEAD_FONT_CN, HEAD_FONT_EN)

    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_after = Pt(30)
    run = p.add_run(REPORT_TAGLINE)
    font_run(run, size=11, color=COLOR_MUTED)

    # 核心结论框
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    table.columns[0].width = Cm(16.4)
    cell = table.cell(0, 0)
    cell.width = Cm(16.4)
    shade_cell(cell, "EAF2FE")
    set_cell_margins(cell, top=140, bottom=140, left=180, right=180)
    p = cell.paragraphs[0]
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_after = Pt(4)
    run = p.add_run("核心结论")
    font_run(run, size=10, bold=True, color=COLOR_BLUE)
    p = cell.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.line_spacing = 1.45
    p.paragraph_format.space_after = Pt(0)
    run = p.add_run("可行。推荐 Kotlin 原生 + PageKernel + QuickJS 插件；v1.0 单人全职 18–22 周；先做 2 周垂直切片验证插件与阅读主链路。")
    font_run(run, size=10.5, color=COLOR_INK)

    spacer = doc.add_paragraph()
    spacer.paragraph_format.first_line_indent = Cm(0)
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(10)
    spacer.paragraph_format.line_spacing = Pt(1)
    spacer.add_run("")

    # 元信息
    meta = [
        ("版本", "v1.0"),
        ("日期", "2026-11-05"),
        ("项目代号", "InkReader（暂名）"),
        ("内核代号", "PageKernel"),
        ("报告性质", "技术可行性与实施方案"),
    ]
    table = doc.add_table(rows=len(meta), cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    widths = (Cm(4.0), Cm(12.4))
    for i, (key, value) in enumerate(meta):
        table.columns[0].width = widths[0]
        table.columns[1].width = widths[1]
        for j, text in enumerate((key, value)):
            cell = table.cell(i, j)
            cell.width = widths[j]
            set_cell_margins(cell, top=60, bottom=60, left=100, right=100)
            p = cell.paragraphs[0]
            p.style = doc.styles["ReportTableText"]
            p.paragraph_format.first_line_indent = Cm(0)
            run = p.add_run(text)
            font_run(run, size=9.5, bold=(j == 0), color=COLOR_INK if j else COLOR_MUTED)
    set_table_borders(table, color="D6DEE6", size=2)

    # 底部
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(40)
    p.paragraph_format.space_after = Pt(0)
    run = p.add_run("2026 年 11 月")
    font_run(run, size=10, color=COLOR_MUTED)

    doc.add_page_break()


def add_toc(doc: Document, headings: list[str]) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(4)
    run = p.add_run("目 录")
    font_run(run, size=22, bold=True, color=COLOR_NAVY)
    set_east_asia(run, HEAD_FONT_CN, HEAD_FONT_EN)

    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_after = Pt(18)
    set_paragraph_border(p, bottom=True, color="C9D1D9", size=4)

    for heading in headings:
        p = doc.add_paragraph()
        p.paragraph_format.first_line_indent = Cm(0)
        p.paragraph_format.left_indent = Cm(0.2)
        p.paragraph_format.space_after = Pt(7)
        p.paragraph_format.line_spacing = 1.15
        run = p.add_run(heading)
        font_run(run, size=10.5, color=COLOR_INK)

    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(18)
    run = p.add_run("说明：本页为章节导航；Word 导航窗格可快速跳转，页码见页脚。正文从下一页开始。")
    font_run(run, size=9, color=COLOR_MUTED, italic=True)
    # 不在这里加 page break：Heading 1 自带 page_break_before。


# ---------------------------------------------------------------------------
# 正文元素
# ---------------------------------------------------------------------------
def add_body_paragraph(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Pt(21)
    add_runs(p, text, size=10.5)


def add_bold_label(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(2)
    add_runs(p, text, size=10.5)
    for run in p.runs:
        run.font.bold = True


def add_bullet(doc: Document, text: str, level: int) -> None:
    p = doc.add_paragraph()
    text_indent = 0.95 + level * 0.5
    p.paragraph_format.first_line_indent = Cm(-0.29)
    p.paragraph_format.left_indent = Cm(text_indent)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(text_indent), WD_TAB_ALIGNMENT.LEFT)
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.35
    marker = "•\t" if level == 0 else "–\t"
    run = p.add_run(marker)
    font_run(run, size=10.5, bold=False)
    add_runs(p, text, size=10.5)


def add_numbered(doc: Document, number: str, text: str) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(-0.46)
    p.paragraph_format.left_indent = Cm(0.7)
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.35
    run = p.add_run(f"{number}.\u00A0")
    font_run(run, size=10.5, bold=True, color=COLOR_BLUE)
    add_runs(p, text, size=10.5)


def add_rule(doc: Document) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(6)
    set_paragraph_border(p, bottom=True, color="D6DEE6", size=4)


def add_code_block(doc: Document, lines: list[str]) -> None:
    p = doc.add_paragraph(style="ReportCode")
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.keep_together = True
    shade_paragraph(p, COLOR_CODE_BG)
    set_paragraph_border(p, left=True, color="9EB4C9", size=12, space=6)
    run = p.add_run()
    font_run(run, size=8.5, color=COLOR_CODE_INK, mono=True)
    for i, line in enumerate(lines):
        if i:
            run.add_break(WD_BREAK.LINE)
        if line:
            run.add_text(line)
    # 代码块后的小间距
    spacer = doc.add_paragraph()
    spacer.paragraph_format.first_line_indent = Cm(0)
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(0)
    spacer.paragraph_format.line_spacing = Pt(3)
    spacer.add_run("")


def add_image(doc: Document, alt: str, rel_path: str) -> None:
    global FIGURE_INDEX
    path = (ASSET_DIR / rel_path).resolve()
    if not path.exists():
        raise FileNotFoundError(path)
    FIGURE_INDEX += 1
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(2)
    run = p.add_run()
    run.add_picture(str(path), width=Cm(16.0))
    cap = doc.add_paragraph(style="ReportCaption")
    cap.paragraph_format.first_line_indent = Cm(0)
    cap.add_run(f"图 {FIGURE_INDEX}：{alt}")


def display_width(text: str) -> int:
    total = 0
    for ch in text:
        total += 2 if ord(ch) > 127 else 1
    return total


def table_widths(header: list[str], rows: list[list[str]], usable_cm: float = 16.4) -> list[float]:
    n = len(header)
    weights: list[float] = []
    for c in range(n):
        max_w = display_width(header[c])
        for row in rows:
            if c < len(row):
                max_w = max(max_w, display_width(row[c]))
        weights.append(float(max(4, min(max_w, 30))))
    # 每列最低宽度
    min_cm = 1.45
    widths = [max(min_cm, usable_cm * w / sum(weights)) for w in weights]
    # 按比例修正到总宽度
    while sum(widths) > usable_cm + 0.01:
        over = sum(widths) - usable_cm
        adjustable = [i for i, w in enumerate(widths) if w > min_cm + 0.01]
        if not adjustable:
            break
        each = over / len(adjustable)
        for i in adjustable:
            widths[i] = max(min_cm, widths[i] - each)
    while sum(widths) < usable_cm - 0.01:
        widths[0] += usable_cm - sum(widths)
    return widths


def add_table(doc: Document, header: list[str], rows: list[list[str]]) -> None:
    ncols = len(header)
    table = doc.add_table(rows=1, cols=ncols)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    table.style = "Table Grid"
    widths = table_widths(header, rows)
    font_size = 8.6 if ncols >= 4 else 9.2

    # 表头
    header_cells = table.rows[0].cells
    for j, text in enumerate(header):
        cell = header_cells[j]
        cell.width = Cm(widths[j])
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        shade_cell(cell, COLOR_TABLE_HEADER)
        set_cell_margins(cell)
        p = cell.paragraphs[0]
        p.style = doc.styles["ReportTableText"]
        p.paragraph_format.first_line_indent = Cm(0)
        add_runs(p, text, size=font_size, base_bold=True, color=COLOR_NAVY, table=True)
        for run in p.runs:
            run.font.bold = True
    repeat_header(table.rows[0])
    prevent_row_split(table.rows[0])

    for i, row in enumerate(rows):
        tr = table.add_row()
        prevent_row_split(tr)
        for j in range(ncols):
            cell = tr.cells[j]
            cell.width = Cm(widths[j])
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell)
            if i % 2 == 1:
                shade_cell(cell, COLOR_TABLE_ALT)
            text = row[j] if j < len(row) else ""
            p = cell.paragraphs[0]
            p.style = doc.styles["ReportTableText"]
            p.paragraph_format.first_line_indent = Cm(0)
            add_runs(p, text, size=font_size, table=True)

    for j, width in enumerate(widths):
        table.columns[j].width = Cm(width)
    set_table_borders(table)

    spacer = doc.add_paragraph()
    spacer.paragraph_format.first_line_indent = Cm(0)
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(2)
    spacer.paragraph_format.line_spacing = Pt(4)
    spacer.add_run("")


def parse_table(lines: list[str]) -> tuple[list[str], list[list[str]]]:
    def split_row(line: str) -> list[str]:
        stripped = line.strip()
        if stripped.startswith("|"):
            stripped = stripped[1:]
        if stripped.endswith("|"):
            stripped = stripped[:-1]
        return [c.strip() for c in stripped.split("|")]

    parsed = [split_row(line) for line in lines]
    if len(parsed) >= 2 and all(re.fullmatch(r":?-{3,}:?", c.strip()) for c in parsed[1]):
        header = parsed[0]
        body = parsed[2:]
    else:
        header = parsed[0]
        body = parsed[1:]
    n = max(len(header), max((len(r) for r in body), default=0))
    header += [""] * (n - len(header))
    body = [r + [""] * (n - len(r)) for r in body]
    return header, body


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def main() -> None:
    md = MD_PATH.read_text(encoding="utf-8")
    lines = md.splitlines()
    try:
        start = lines.index(BODY_START)
    except ValueError as exc:
        raise RuntimeError(f"未找到正文起点 {BODY_START}") from exc

    body_lines = lines[start:]
    h2s = [line[3:].strip() for line in body_lines if line.startswith("## ")]

    doc = Document()
    setup_section(doc.sections[0])
    setup_styles(doc)
    setup_header_footer(doc)

    add_cover(doc)
    add_toc(doc, h2s)

    i = 0
    n = len(body_lines)
    while i < n:
        line = body_lines[i].rstrip()
        stripped = line.strip()

        if not stripped:
            i += 1
            continue

        if stripped.startswith("```"):
            i += 1
            code_lines: list[str] = []
            while i < n and not body_lines[i].strip().startswith("```"):
                code_lines.append(body_lines[i].rstrip("\n"))
                i += 1
            if i < n:
                i += 1
            add_code_block(doc, code_lines)
            continue

        if stripped.startswith("!["):
            m = re.match(r"!\[([^\]]*)\]\(([^)]+)\)", stripped)
            if m:
                add_image(doc, m.group(1), m.group(2))
            i += 1
            continue

        if stripped.startswith("|"):
            table_lines: list[str] = []
            while i < n and body_lines[i].strip().startswith("|"):
                table_lines.append(body_lines[i].strip())
                i += 1
            header, rows = parse_table(table_lines)
            add_table(doc, header, rows)
            continue

        if stripped.startswith("#### "):
            p = doc.add_paragraph(style="Heading 3")
            add_runs(p, stripped[5:].strip(), size=12)
            for run in p.runs:
                run.font.bold = True
            i += 1
            continue

        if stripped.startswith("### "):
            p = doc.add_paragraph(style="Heading 2")
            add_runs(p, stripped[4:].strip(), size=14)
            for run in p.runs:
                run.font.bold = True
            i += 1
            continue

        if stripped.startswith("## "):
            p = doc.add_paragraph(style="Heading 1")
            add_runs(p, stripped[3:].strip(), size=18)
            for run in p.runs:
                run.font.bold = True
            i += 1
            continue

        if stripped == "---":
            add_rule(doc)
            i += 1
            continue

        if stripped.startswith("- "):
            level = 1 if line.startswith("  - ") else 0
            add_bullet(doc, stripped[2:].strip(), level)
            i += 1
            continue

        numbered = re.match(r"^(\d+)\.\s+(.*)$", stripped)
        if numbered:
            add_numbered(doc, numbered.group(1), numbered.group(2))
            i += 1
            continue

        if stripped.startswith("> "):
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Cm(0.8)
            p.paragraph_format.first_line_indent = Cm(0)
            p.paragraph_format.space_after = Pt(4)
            add_runs(p, stripped[2:].strip(), size=10, color=COLOR_MUTED)
            for run in p.runs:
                run.font.italic = True
            i += 1
            continue

        # 全粗体的小标题（如 P0/P1/P2、核心结论标签）
        if stripped.startswith("**") and stripped.endswith("**") and stripped.count("**") == 2:
            add_bold_label(doc, stripped)
            i += 1
            continue

        add_body_paragraph(doc, stripped)
        i += 1

    # 文档属性
    props = doc.core_properties
    props.title = REPORT_TITLE
    props.subject = REPORT_SUBTITLE
    props.author = "InkReader 项目组"
    props.comments = "技术方案与实施报告；所有体积、性能、工期均为工程估算。"
    props.created = datetime(2026, 11, 5)
    props.last_modified_by = "InkReader 项目组"
    props.language = "zh-CN"

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUT_PATH)
    print(f"wrote {OUT_PATH}")
    print(f"body headings={len(h2s)} tables={len(doc.tables)} images={len(doc.inline_shapes)} paragraphs={len(doc.paragraphs)}")


if __name__ == "__main__":
    main()
