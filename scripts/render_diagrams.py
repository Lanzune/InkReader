# -*- coding: utf-8 -*-
"""生成报告用的架构图与插件生命周期图。"""

from __future__ import annotations

import math
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "docs" / "reports" / "assets"
ASSETS.mkdir(parents=True, exist_ok=True)

FONT_REGULAR = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

BG = "#F7F8FA"
INK = "#17212B"
MUTED = "#5E6B78"
LINE = "#C9D1D9"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size)


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    """按字符宽度换行，兼容中英文混排。"""
    lines: list[str] = []
    for paragraph in text.split("\n"):
        current = ""
        for ch in paragraph:
            candidate = current + ch
            if draw.textlength(candidate, font=fnt) <= max_width or not current:
                current = candidate
            else:
                lines.append(current)
                current = ch
        lines.append(current)
    return lines


def draw_center_text(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    w: int,
    h: int,
    title: str,
    body: str = "",
    title_size: int = 34,
    body_size: int = 24,
    title_color: str = INK,
    body_color: str = MUTED,
    align: str = "center",
    title_bold: bool = True,
) -> None:
    title_font = font(title_size, bold=title_bold)
    body_font = font(body_size, bold=False)
    title_lines = wrap(draw, title, title_font, w - 34)
    body_lines = wrap(draw, body, body_font, w - 34) if body else []
    title_line_h = int(title_size * 1.35)
    body_line_h = int(body_size * 1.45)
    total_h = len(title_lines) * title_line_h + (len(body_lines) * body_line_h + 8 if body_lines else 0)
    cursor_y = y + max(10, (h - total_h) // 2)
    for line in title_lines:
        text_w = draw.textlength(line, font=title_font)
        tx = x + (w - text_w) / 2 if align == "center" else x + 18
        draw.text((tx, cursor_y), line, font=title_font, fill=title_color)
        cursor_y += title_line_h
    if body_lines:
        cursor_y += 8
        for line in body_lines:
            text_w = draw.textlength(line, font=body_font)
            tx = x + (w - text_w) / 2 if align == "center" else x + 18
            draw.text((tx, cursor_y), line, font=body_font, fill=body_color)
            cursor_y += body_line_h


def card(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    w: int,
    h: int,
    title: str,
    body: str = "",
    fill: str = "#FFFFFF",
    outline: str = LINE,
    title_color: str = INK,
    body_color: str = MUTED,
    radius: int = 18,
    title_size: int = 32,
    body_size: int = 22,
    width: int = 3,
    align: str = "center",
    title_bold: bool = True,
) -> None:
    draw.rounded_rectangle((x, y, x + w, y + h), radius=radius, fill=fill, outline=outline, width=width)
    draw_center_text(
        draw,
        x,
        y,
        w,
        h,
        title,
        body,
        title_size=title_size,
        body_size=body_size,
        title_color=title_color,
        body_color=body_color,
        align=align,
        title_bold=title_bold,
    )


def arrow_head(
    draw: ImageDraw.ImageDraw,
    x1: int,
    y1: int,
    x2: int,
    y2: int,
    color: str,
    size: int = 15,
) -> None:
    angle = math.atan2(y2 - y1, x2 - x1)
    left = (x2 - size * math.cos(angle - math.pi / 6), y2 - size * math.sin(angle - math.pi / 6))
    right = (x2 - size * math.cos(angle + math.pi / 6), y2 - size * math.sin(angle + math.pi / 6))
    draw.polygon([(x2, y2), left, right], fill=color)


def arrow(
    draw: ImageDraw.ImageDraw,
    points: list[tuple[int, int]],
    color: str = "#788591",
    width: int = 4,
    dashed: bool = False,
) -> None:
    for idx in range(len(points) - 1):
        x1, y1 = points[idx]
        x2, y2 = points[idx + 1]
        if dashed:
            total = math.hypot(x2 - x1, y2 - y1)
            if total == 0:
                continue
            dash = 16
            gap = 12
            pos = 0.0
            while pos < total:
                end = min(pos + dash, total)
                sx = x1 + (x2 - x1) * pos / total
                sy = y1 + (y2 - y1) * pos / total
                ex = x1 + (x2 - x1) * end / total
                ey = y1 + (y2 - y1) * end / total
                draw.line([(sx, sy), (ex, ey)], fill=color, width=width)
                pos += dash + gap
        else:
            draw.line([(x1, y1), (x2, y2)], fill=color, width=width)
    arrow_head(draw, points[-2][0], points[-2][1], points[-1][0], points[-1][1], color, 17)


def dashed_rect(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int, color: str, width: int = 3, dash: int = 14) -> None:
    # 仅用于示意安全/进程边界，手动画虚线框。
    for xx in range(x, x + w, dash * 2):
        draw.line((xx, y, min(xx + dash, x + w), y), fill=color, width=width)
        draw.line((xx, y + h, min(xx + dash, x + w), y + h), fill=color, width=width)
    for yy in range(y, y + h, dash * 2):
        draw.line((x, yy, x, min(yy + dash, y + h)), fill=color, width=width)
        draw.line((x + w, yy, x + w, min(yy + dash, y + h)), fill=color, width=width)


def section_header(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    title: str,
    subtitle: str,
    title_color: str = INK,
    subtitle_color: str = MUTED,
    title_size: int = 36,
    subtitle_size: int = 21,
) -> None:
    draw.text((x + 24, y + 16), title, font=font(title_size, True), fill=title_color)
    draw.text((x + 24, y + 16 + title_size + 10), subtitle, font=font(subtitle_size), fill=subtitle_color)


def save(img: Image.Image, name: str) -> None:
    path = ASSETS / name
    img.save(path, "PNG", optimize=True)
    print(f"wrote {path} ({img.size[0]}x{img.size[1]})")


# ---------------------------------------------------------------------------
# 图 1：总体架构
# ---------------------------------------------------------------------------
def architecture() -> None:
    W, H = 2400, 1800
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    d.text((80, 52), "InkReader 总体架构：可信内核 + 能力插件", font=font(58, True), fill=INK)
    d.text((82, 125), "应用壳层可替换、可选能力插件化；会话、权限、存储和版本协商留在 PageKernel。", font=font(28), fill=MUTED)

    # 应用壳层
    d.rounded_rectangle((80, 185, 2320, 410), radius=22, fill="#EAF2FE", outline="#7BA3D9", width=3)
    d.text((112, 205), "应用壳层（Compose）", font=font(36, True), fill="#244A78")
    app_items = [
        ("首页 / 最近阅读", "打开文件、继续阅读"),
        ("阅读器 UI", "控制层、目录、进度"),
        ("设置", "主题、亮度、字体"),
        ("插件管理", "安装、授权、禁用"),
        ("命令面板", "插件命令与设置"),
    ]
    app_w, app_gap, app_x, app_y = 414, 22, 112, 270
    for i, (title, body) in enumerate(app_items):
        card(d, app_x + i * (app_w + app_gap), app_y, app_w, 112, title, body,
             fill="#FFFFFF", outline="#B8CBE4", title_size=28, body_size=20, title_color="#244A78")

    # 内核
    d.rounded_rectangle((80, 465, 2320, 770), radius=22, fill="#102B43", outline="#102B43", width=3)
    d.text((112, 485), "PageKernel 可信内核", font=font(38, True), fill="#FFFFFF")
    d.text((1144, 498), "不依赖任何具体格式；不暴露 Android 类型；不做任意代码加载", font=font(22), fill="#A9C2D8")
    kernel_items = [
        ("ReaderSession", "打开/关闭/恢复\n分页状态机"),
        ("Command / Event", "异步命令与\n低频阅读事件"),
        ("Capability Broker", "能力声明、授权、\n参数与配额校验"),
        ("Registry / Version", "格式/主题/命令注册\nAPI 版本协商"),
        ("Storage / SAF", "URI 权限、进度、\n设置、插件记录"),
    ]
    k_w, k_gap, k_x, k_y = 414, 22, 112, 552
    for i, (title, body) in enumerate(kernel_items):
        card(d, k_x + i * (k_w + k_gap), k_y, k_w, 168, title, body,
             fill="#173A58", outline="#3B6383", title_color="#FFFFFF", body_color="#C7D8E6",
             title_size=26, body_size=20)

    # 三个并列区域：阅读引擎 / 内置格式 / 脚本宿主
    d.text((80, 818), "进程内：可信、性能敏感", font=font(26, True), fill="#3D5A73")
    d.text((1730, 790), "受控边界：能力受限、可降级", font=font(26, True), fill="#6A3B8C")

    # Reader Engine
    d.rounded_rectangle((80, 870, 730, 1310), radius=18, fill="#EAF7EE", outline="#71B383", width=3)
    section_header(d, 80, 870, "Reader Engine", "分页 · 绘制 · 主题 · 动画", "#1D5B31", "#3C6B4A")
    engine_items = [
        ("TextPaginator", "StaticLayout / 行级分页"),
        ("ReaderSurfaceView", "Canvas 绘制，不阻塞 UI"),
        ("ThemeEngine", "明/暗/护眼 token"),
        ("Transition", "滑动/覆盖/淡入/折页"),
    ]
    for i, (title, body) in enumerate(engine_items):
        x = 104 + (i % 2) * 306
        y = 1000 + (i // 2) * 146
        card(d, x, y, 292, 124, title, body, fill="#FFFFFF", outline="#B9DDC4",
             title_size=24, body_size=18, title_color="#1D5B31")

    # Built-in formats
    d.rounded_rectangle((780, 870, 1680, 1310), radius=18, fill="#FFF5E3", outline="#D6A24A", width=3)
    section_header(d, 780, 870, "内置格式插件", "同一套 FormatProvider SPI，dogfooding", "#6A4300", "#755C2E")
    formats = [
        ("EPUB 2/3", "ZIP / OPF / XHTML\n受控 CSS 子集"),
        ("TXT", "编码探测 / 章节规则\n大文件流式索引"),
        ("PDF", "PdfRenderer 页面\n缩放 / 夜间降亮"),
        ("HTML / Markdown", "块模型复用\n脚本与远程资源阻断"),
    ]
    for i, (title, body) in enumerate(formats):
        x = 804 + (i % 2) * 438
        y = 1000 + (i // 2) * 146
        card(d, x, y, 420, 124, title, body, fill="#FFFFFF", outline="#E8C98D",
             title_size=26, body_size=18, title_color="#6A4300")

    # QuickJS host + package, dashed boundary
    dashed_rect(d, 1718, 848, 602, 518, "#9B6BC1", width=4)
    d.rounded_rectangle((1734, 868, 2304, 1168), radius=18, fill="#F4ECFC", outline="#B58BD6", width=3)
    section_header(
        d,
        1734,
        868,
        "QuickJS 脚本宿主",
        "第三方插件 · 首版线程，v1.1 独立进程",
        "#5E2E80",
        "#79558F",
        title_size=31,
        subtitle_size=16,
    )
    host_items = [
        ("Runtime", "内存 16 MB\n激活 2 s"),
        ("Bridge", "JSON 参数校验\n无 Android 对象"),
        ("Quota", "命令 2 s\n事件 50 ms"),
        ("Sandbox", "无网络 / 无文件\n无 DEX / 无 native"),
    ]
    for i, (title, body) in enumerate(host_items):
        x = 1754 + (i % 2) * 278
        y = 975 + (i // 2) * 96
        card(d, x, y, 262, 84, title, body, fill="#FFFFFF", outline="#D8C2E9",
             title_size=21, body_size=15, title_color="#5E2E80")

    card(d, 1734, 1192, 570, 150, "插件包 .inkp", "manifest.json + main.js + assets；安装到私有目录\n能力授权后才激活；可禁用、可安全模式",
         fill="#FFFFFF", outline="#B58BD6", title_color="#5E2E80", body_color="#79558F",
         title_size=26, body_size=18)

    # Android platform
    card(d, 80, 1390, 2240, 150, "Android 平台能力", "SAF 文件选择器 / ParcelFileDescriptor / PdfRenderer / SQLiteOpenHelper / SharedPreferences / 系统字体",
         fill="#ECEFF1", outline="#8A9AA8", title_color="#34424E", body_color="#5D6D7A",
         title_size=30, body_size=22)
    d.text((80, 1585), "安全与隐私：主 APK 不声明 INTERNET；EPUB/HTML 远程资源阻断；插件不能直接拿文件路径、Intent 或 Android Context。",
           font=font(24), fill="#40505E")
    d.text((80, 1628), "体积策略：无 WebView / Readium / PDFium / Room / Hilt；按 ABI 拆分；QuickJS 原生库需满足 16 KB 页大小。",
           font=font(24), fill="#40505E")

    # 箭头
    arrow(d, [(1200, 412), (1200, 462)], color="#56789C", width=5)
    arrow(d, [(405, 772), (405, 866)], color="#56789C", width=5)
    arrow(d, [(1230, 772), (1230, 866)], color="#56789C", width=5)
    arrow(d, [(2018, 772), (2018, 862)], color="#7B4F9E", width=5, dashed=True)
    arrow(d, [(730, 1090), (776, 1090)], color="#56789C", width=5)
    arrow(d, [(2018, 1188), (2018, 1152)], color="#7B4F9E", width=5, dashed=True)
    arrow(d, [(405, 1312), (405, 1386)], color="#56789C", width=5)
    arrow(d, [(1230, 1312), (1230, 1386)], color="#56789C", width=5)

    # 图例
    legend_y = 1712
    d.line((80, legend_y, 132, legend_y), fill="#56789C", width=5)
    d.text((146, legend_y - 14), "同进程可信调用", font=font(22), fill=MUTED)
    d.line((390, legend_y, 442, legend_y), fill="#7B4F9E", width=5)
    d.text((456, legend_y - 14), "沙箱 / 可降级边界", font=font(22), fill=MUTED)
    d.rectangle((716, legend_y - 16, 748, legend_y + 2), fill="#FFF5E3", outline="#D6A24A", width=2)
    d.text((760, legend_y - 14), "内置插件（与内核同版本发布）", font=font(22), fill=MUTED)

    save(img, "architecture.png")


# ---------------------------------------------------------------------------
# 图 2：插件生命周期
# ---------------------------------------------------------------------------
def plugin_lifecycle() -> None:
    W, H = 2400, 1350
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    d.text((80, 52), "插件生命周期与故障降级", font=font(58, True), fill=INK)
    d.text((82, 125), "安装是显式行为；运行受能力与配额约束；失败只影响插件，不阻塞阅读主链路。", font=font(28), fill=MUTED)

    d.text((92, 220), "正常路径", font=font(26, True), fill="#2F6B47")
    d.text((92, 770), "失败与恢复", font=font(26, True), fill="#8A4B2A")

    steps = [
        ("导入 .inkp", "文件选择器 / 分享入口"),
        ("校验", "schema / 大小 / 路径安全"),
        ("能力授权", "逐项展示权限说明"),
        ("安装", "私有目录 + 哈希记录"),
        ("激活", "创建 QuickJS 运行时"),
        ("注册贡献", "主题 / 命令 / 面板"),
        ("运行", "命令与低频阅读事件"),
    ]
    w, gap, x0, y0, h = 300, 26, 92, 280, 190
    for i, (title, body) in enumerate(steps):
        x = x0 + i * (w + gap)
        fill = "#EAF7EE" if i < 4 else "#F4ECFC"
        outline = "#7DB68D" if i < 4 else "#B58BD6"
        title_color = "#1F5A35" if i < 4 else "#5E2E80"
        card(d, x, y0, w, h, title, body, fill=fill, outline=outline,
             title_color=title_color, title_size=28, body_size=18)
        if i < len(steps) - 1:
            arrow(d, [(x + w + 3, y0 + h // 2), (x + w + gap - 4, y0 + h // 2)],
                  color="#6E7C88", width=4)

    # 失败分支：从“运行”向下、向左，再进入失败链
    start_x = x0 + 6 * (w + gap) + w // 2
    fail_y = 880
    arrow(
        d,
        [
            (start_x, y0 + h + 5),
            (start_x, 700),
            (222, 700),
            (222, fail_y - 4),
        ],
        color="#B56F42",
        width=4,
        dashed=True,
    )
    d.text((235, 665), "超时 / 崩溃 / 内存超限 / 版本不兼容", font=font(22, True), fill="#8A4B2A")

    fail_steps = [
        ("禁用 / 隔离", "注销贡献，停止运行时"),
        ("记录错误", "结构化错误 + 最近日志"),
        ("安全模式", "只加载内置插件"),
        ("重试 / 卸载", "用户决定，不自动恢复"),
    ]
    fw, fgap, fx0 = 330, 28, 92
    for i, (title, body) in enumerate(fail_steps):
        x = fx0 + i * (fw + fgap)
        card(d, x, fail_y, fw, 180, title, body, fill="#FFF3EC", outline="#D69A72",
             title_color="#8A4B2A", title_size=28, body_size=18)
        if i < len(fail_steps) - 1:
            arrow(d, [(x + fw + 3, fail_y + 90), (x + fw + fgap - 4, fail_y + 90)],
                  color="#B56F42", width=4)

    # 限额信息
    d.rounded_rectangle((92, 1130, 2308, 1240), radius=16, fill="#FFFFFF", outline="#D6DEE6", width=2)
    d.text((120, 1150), "默认资源限额", font=font(24, True), fill=INK)
    limits = "脚本 + 资源 ≤ 5 MB   ·   运行时内存 16 MB   ·   激活 ≤ 2 s   ·   用户命令 ≤ 2 s   ·   事件回调 ≤ 50 ms   ·   插件存储 5 MB"
    d.text((330, 1150), limits, font=font(22), fill=MUTED)

    d.text((92, 1280), "关键点：插件从未获得 Context、File、Uri、Intent、网络或任意 Android 对象；能力以外的副作用一律拒绝。",
           font=font(22), fill="#40505E")

    save(img, "plugin-lifecycle.png")


if __name__ == "__main__":
    architecture()
    plugin_lifecycle()
