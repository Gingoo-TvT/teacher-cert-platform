#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Phase 53：生成 db/demo 下的三个「微型但格式合法」的样例文件（无需 ffmpeg / 第三方库，纯标准库）。
  - sample-image.png   合法 PNG（32x32 纯色），materials 预览用，浏览器可正常显示。
  - sample-material.pdf 合法单页 PDF（含文字），materials/免考佐证预览用，PDF 阅读器可打开。
  - sample-video.mp4    结构合法的 MP4 容器（ftyp+moov(含一条 vide 轨,空样本表)+mdat）。
                        无 ffmpeg / H.264 编码器，无法在本无头环境生成「真正可播放」的帧，
                        故这是一个「可下载、被识别为 video/mp4」的占位对象；浏览器可否播放未经验证。
运行：python scripts/gen-demo-samples.py   （幂等：每次覆盖生成，字节稳定）
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


# ---------------- MP4 (结构合法容器, 空样本轨) ----------------
def b(typ, payload):
    return struct.pack(">I", len(payload) + 8) + typ + payload

def make_mp4():
    ftyp = b(b"ftyp", b"isom" + struct.pack(">I", 0x200) + b"isomiso2mp41")
    # mvhd v0
    mvhd = b(b"mvhd", struct.pack(">IIIIIi", 0, 0, 0, 1000, 0, 0x00010000)
             + struct.pack(">hH", 0x0100, 0) + b"\x00" * 8
             + struct.pack(">iiiiiiiii", 0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000)
             + b"\x00" * 24 + struct.pack(">I", 2))
    tkhd = b(b"tkhd", struct.pack(">IIIIII", 0x7, 0, 0, 1, 0, 0)
             + b"\x00" * 8 + struct.pack(">hhhH", 0, 0, 0, 0)
             + struct.pack(">iiiiiiiii", 0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000)
             + struct.pack(">II", 320 << 16, 240 << 16))
    mdhd = b(b"mdhd", struct.pack(">IIIIHH", 0, 0, 0, 1000, 0, 0x55c4))
    hdlr = b(b"hdlr", struct.pack(">II", 0, 0) + b"vide" + b"\x00" * 12 + b"VideoHandler\x00")
    vmhd = b(b"vmhd", struct.pack(">IHHHH", 1, 0, 0, 0, 0))
    url_ = b(b"url ", struct.pack(">I", 1))
    dref = b(b"dref", struct.pack(">II", 0, 1) + url_)
    dinf = b(b"dinf", dref)
    # empty sample tables (0 samples) -> valid container, 0-length track
    stsd = b(b"stsd", struct.pack(">II", 0, 0))
    stts = b(b"stts", struct.pack(">II", 0, 0))
    stsc = b(b"stsc", struct.pack(">II", 0, 0))
    stsz = b(b"stsz", struct.pack(">III", 0, 0, 0))
    stco = b(b"stco", struct.pack(">II", 0, 0))
    stbl = b(b"stbl", stsd + stts + stsc + stsz + stco)
    minf = b(b"minf", vmhd + dinf + stbl)
    mdia = b(b"mdia", mdhd + hdlr + minf)
    trak = b(b"trak", tkhd + mdia)
    moov = b(b"moov", mvhd + trak)
    mdat = b(b"mdat", b"phase53-demo-teaching-video-placeholder")
    return ftyp + moov + mdat


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write("sample-image.png", make_png())
    write("sample-material.pdf", make_pdf())
    write("sample-video.mp4", make_mp4())
    print("done ->", OUT)
