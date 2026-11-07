from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile

root = Path(__file__).resolve().parents[1]
out = root / "app" / "src" / "debug" / "assets" / "sample.epub"
out.parent.mkdir(parents=True, exist_ok=True)

container = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:inkreader:sample</dc:identifier>
    <dc:title>示例 EPUB · 夜航</dc:title>
    <dc:creator>InkReader</dc:creator>
    <dc:language>zh-CN</dc:language>
    <meta property="dcterms:modified">2026-11-07T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
    <item id="c3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="c1"/>
    <itemref idref="c2"/>
    <itemref idref="c3"/>
  </spine>
</package>
"""

nav = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>目录</title></head>
<body>
<nav epub:type="toc"><h1>目录</h1><ol>
<li><a href="chapter1.xhtml">第一章 起点</a></li>
<li><a href="chapter2.xhtml">第二章 风</a></li>
<li><a href="chapter3.xhtml">第三章 归途</a></li>
</ol></nav>
</body></html>
"""

chapters = {
    "chapter1.xhtml": """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第一章 起点</title></head><body>
<h1>第一章 起点</h1>
<p>夜航从一盏灯开始。</p>
<p>风从窗外经过，带着一点潮湿的味道。</p>
<p>她把书翻到第一页，却先看了一会儿空白。</p>
</body></html>""",
    "chapter2.xhtml": """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第二章 风</title></head><body>
<h1>第二章 风</h1>
<p>风没有方向，只有声音。</p>
<p>他沿着河岸走，路灯的影子在水面上碎成很多段。</p>
<p>“你迟到了。”她说。</p>
<p>“路上有风。”他回答。</p>
</body></html>""",
    "chapter3.xhtml": """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第三章 归途</title></head><body>
<h1>第三章 归途</h1>
<p>回去的时候，天已经亮了。</p>
<p>书还在包里，页角被折出一个小小的痕迹。</p>
<p>这只是一个示例 EPUB。</p>
</body></html>""",
}

with ZipFile(out, "w") as z:
    z.writestr("mimetype", "application/epub+zip", compress_type=ZIP_STORED)
    z.writestr("META-INF/container.xml", container, compress_type=ZIP_DEFLATED)
    z.writestr("OEBPS/content.opf", opf, compress_type=ZIP_DEFLATED)
    z.writestr("OEBPS/nav.xhtml", nav, compress_type=ZIP_DEFLATED)
    for name, body in chapters.items():
        z.writestr(f"OEBPS/{name}", body, compress_type=ZIP_DEFLATED)

print(out)
