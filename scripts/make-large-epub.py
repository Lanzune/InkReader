import io
import os
import random
import zipfile

from PIL import Image

OUT = r"C:\Project\DeepSeekHarness\inkreader\artifacts\device\testbooks\large.epub"
CHAPTERS = 15
IMAGES_PER_CHAPTER = 10
SIDE = 1000

os.makedirs(os.path.dirname(OUT), exist_ok=True)
random.seed(20261108)

container = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

manifest = []
spine = []
for chapter in range(CHAPTERS):
    manifest.append(
        f'<item id="c{chapter}" href="text{chapter:03d}.xhtml" media-type="application/xhtml+xml"/>'
    )
    spine.append(f'<itemref idref="c{chapter}"/>')
    for image in range(IMAGES_PER_CHAPTER):
        manifest.append(
            f'<item id="i{chapter}_{image}" href="images/i{chapter:03d}_{image:02d}.jpg" media-type="image/jpeg"/>'
        )

opf = f"""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:test:large</dc:identifier>
    <dc:title>Large EPUB 100MB Test</dc:title>
    <dc:creator>InkReader</dc:creator>
    <dc:language>zh-CN</dc:language>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    {''.join(manifest)}
  </manifest>
  <spine>
    {''.join(spine)}
  </spine>
</package>
"""

nav_items = "".join(
    f'<li><a href="text{chapter:03d}.xhtml">第{chapter + 1}章</a></li>'
    for chapter in range(CHAPTERS)
)
nav = f"""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<body><nav epub:type="toc"><ol>{nav_items}</ol></nav></body></html>
"""

with zipfile.ZipFile(OUT, "w") as archive:
    archive.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
    archive.writestr("META-INF/container.xml", container, compress_type=zipfile.ZIP_DEFLATED)
    archive.writestr("OEBPS/content.opf", opf, compress_type=zipfile.ZIP_DEFLATED)
    archive.writestr("OEBPS/nav.xhtml", nav, compress_type=zipfile.ZIP_DEFLATED)
    for chapter in range(CHAPTERS):
        body = [f"<h1>第{chapter + 1}章</h1>"]
        body.append("<p>大文件测试章节，包含十张随机图片。</p>")
        for image in range(IMAGES_PER_CHAPTER):
            body.append(f'<p><img src="images/i{chapter:03d}_{image:02d}.jpg" alt="图{chapter}-{image}"/></p>')
        html = (
            '<?xml version="1.0" encoding="UTF-8"?>'
            '<html xmlns="http://www.w3.org/1999/xhtml"><head>'
            f"<title>第{chapter + 1}章</title></head><body>"
            + "\n".join(body)
            + "</body></html>"
        )
        archive.writestr(f"OEBPS/text{chapter:03d}.xhtml", html, compress_type=zipfile.ZIP_DEFLATED)
        for image in range(IMAGES_PER_CHAPTER):
            pixels = bytes(random.getrandbits(8) for _ in range(SIDE * SIDE * 3))
            image_obj = Image.frombytes("RGB", (SIDE, SIDE), pixels)
            buffer = io.BytesIO()
            image_obj.save(buffer, format="JPEG", quality=90)
            archive.writestr(
                f"OEBPS/images/i{chapter:03d}_{image:02d}.jpg",
                buffer.getvalue(),
                compress_type=zipfile.ZIP_STORED,
            )
        print(f"chapter {chapter + 1}/{CHAPTERS}", flush=True)

print(os.path.getsize(OUT))
