#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Phase 53：生成 db/demo 下的 PNG/PDF 样例，并保留由 GenerateDemoVideo.java 生成的真实 MP4。
  - sample-image.png   合法 PNG（32x32 纯色），materials 预览用，浏览器可正常显示。
  - sample-material.pdf 合法单页 PDF（含文字），materials/免考佐证预览用，PDF 阅读器可打开。
  - sample-video.mp4    真实 H.264 视频轨，必须先由 scripts/GenerateDemoVideo.java 生成。
运行：python scripts/gen-demo-samples.py   （幂等：覆盖 PNG/PDF，不覆盖真实视频）
"""
import struct, zlib, binascii, os

OUT = os.path.join(os.path.dirname(__file__), "..", "platform-boot", "src", "main", "resources", "db", "demo")
OUT = os.path.abspath(OUT)


def write(name, data):
    p = os.path.join(OUT, name)
    with open(p, "wb") as f:
        f.write(data)
    print("wrote %-22s %6d bytes" % (name, len(data)))


# ---------------- PNG (32x32 纯色, 合法) ----------------
def make_png():
    w = h = 32
    r, g, b = 0x2E, 0x6B, 0xE6  # 品牌蓝
    raw = bytearray()
    for _y in range(h):
        raw.append(0)  # filter type 0
        for _x in range(w):
            raw += bytes((r, g, b))
    def chunk(typ, payload):
        c = struct.pack(">I", len(payload)) + typ + payload
        crc = binascii.crc32(typ + payload) & 0xffffffff
        return c + struct.pack(">I", crc)
    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)  # 8-bit, colour type 2 (truecolour)
    idat = zlib.compress(bytes(raw), 9)
    return sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")


# ---------------- PDF (单页含文字, 合法) ----------------
def make_pdf():
    objs = []
    objs.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objs.append(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    objs.append(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 160] "
                b"/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>")
    objs.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    stream = (b"BT /F1 18 Tf 40 110 Td (Phase53 Demo Material) Tj "
              b"0 -28 Td /F1 11 Tf (teacher-cert-platform demo file) Tj ET")
    objs.append(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, body in enumerate(objs, start=1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % i + body + b"\nendobj\n"
    xref_pos = len(out)
    n = len(objs) + 1
    out += b"xref\n0 %d\n" % n
    out += b"0000000000 65535 f \n"
    for off in offsets:
        out += b"%010d 00000 n \n" % off
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (n, xref_pos)
    return bytes(out)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write("sample-image.png", make_png())
    write("sample-material.pdf", make_pdf())
    video = os.path.join(OUT, "sample-video.mp4")
    if not os.path.isfile(video) or os.path.getsize(video) == 0:
        raise SystemExit("sample-video.mp4 缺失；请先运行 scripts/GenerateDemoVideo.java")
    print("kept  %-22s %6d bytes" % ("sample-video.mp4", os.path.getsize(video)))
    print("done ->", OUT)
