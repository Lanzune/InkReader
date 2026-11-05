# -*- coding: utf-8 -*-
"""检查仓库内 Markdown 相对链接是否存在。"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LINK_RE = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
SKIP_PREFIXES = ("http://", "https://", "mailto:", "tel:", "#")


def main() -> int:
    broken: list[tuple[Path, str]] = []
    markdown_files = sorted(ROOT.rglob("*.md"))
    for path in markdown_files:
        text = path.read_text(encoding="utf-8")
        for raw in LINK_RE.findall(text):
            target = raw.strip().split("#", 1)[0].strip()
            if not target or target.startswith(SKIP_PREFIXES):
                continue
            if target.startswith("/"):
                resolved = ROOT / target.lstrip("/")
            else:
                resolved = (path.parent / target).resolve()
            if not resolved.exists():
                broken.append((path.relative_to(ROOT), raw))

    for path, target in broken:
        print(f"BROKEN {path}: {target}")
    print(f"checked={len(markdown_files)} broken={len(broken)}")
    return 1 if broken else 0


if __name__ == "__main__":
    raise SystemExit(main())
