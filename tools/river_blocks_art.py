"""Textures of the savanna river blocks (savanna rivers, 2026-11-07, docs/plans/savanna-rivers.md), the user's picks in the
river viewer (python tools/model_viewer.py rivers, tools/river_colors_viewer.template.html, the same recipes):
  silt       Minecraft's sand mixed with its dirt, darkened to the river's olive grey (the dry top of a silt bar)
  wet_silt   the same, darker (by the water)
  river_mud  Minecraft's mud pattern recoloured to dark olive-brown, no light speckles (look A, the user: "almost perfect,
             just remove the 5 speckles")
python tools/river_blocks_art.py  (reads the textures from the NeoForge client jar, writes the PNGs into the savanna mod)
No PIL needed: a small PNG reader and writer."""
import os, struct, sys, zlib, zipfile

JAR = "build/moddev/artifacts/neoforge-21.1.256-client-extra-aka-minecraft-resources.jar"
OUT = "savanna/src/main/resources/assets/antcolonies/textures/block"

def read_png(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, plte, trns = 8, b"", None, None
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8]); body = data[pos + 8:pos + 8 + n]; pos += 12 + n
        if kind == b"IHDR": w, h, depth, ctype, _, _, inter = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE": plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS": trns = body
        elif kind == b"IDAT": idat += body
    assert inter == 0 and (depth == 8 or ctype in (0, 3)), (depth, ctype, inter)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bpp = max(1, ch * depth // 8)
    raw = zlib.decompress(idat); stride = (w * ch * depth + 7) // 8; rows, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[p]; line = bytearray(raw[p + 1:p + 1 + stride]); p += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0; b = prev[i]; c = prev[i - bpp] if i >= bpp else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                pp = a + b - c; pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        if depth < 8:  # unpack the small samples to one byte each
            per = 8 // depth; mask = (1 << depth) - 1; scale = 1 if ctype == 3 else 255 // mask
            line = bytearray(((line[x // per] >> (8 - depth * (x % per + 1))) & mask) * scale for x in range(w))
        rows.append(line)
    px = []
    for line in rows:
        for x in range(w):
            v = line[x * ch:(x + 1) * ch]
            if ctype == 6: px.append(tuple(v))
            elif ctype == 2: px.append((*v, 255))
            elif ctype == 3: px.append((*plte[v[0]], trns[v[0]] if trns and v[0] < len(trns) else 255))
            elif ctype == 0: px.append((v[0], v[0], v[0], 255))
            else: px.append((v[0], v[0], v[0], v[1]))
    return w, h, px

def write_png(path, w, h, px):
    raw = b"".join(b"\x00" + bytes(c for p in px[y * w:(y + 1) * w] for c in p) for y in range(h))
    def chunk(k, b): return struct.pack(">I", len(b)) + k + b + struct.pack(">I", zlib.crc32(k + b) & 0xffffffff)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def main():
    z = zipfile.ZipFile(sys.argv[1] if len(sys.argv) > 1 else JAR)
    tex = lambda n: read_png(z.read(f"assets/minecraft/textures/block/{n}.png"))
    _, _, sand = tex("sand"); _, _, dirt = tex("dirt"); _, _, mud = tex("mud")
    def silt(wet):
        k = 0.68 if wet else 0.86; m = (0.45, 0.42, 0.4); tint = (1, 0.97, 0.9)
        return [tuple(round((s[c] * m[c] + d[c] * (1 - m[c])) * k * tint[c]) for c in range(3)) + (255,) for s, d in zip(sand, dirt)]
    lum = [p[0] + p[1] + p[2] for p in mud]; mn, mx = min(lum), max(lum)
    lo, hi = (0x2F, 0x29, 0x1E), (0x5A, 0x4E, 0x3B)
    river_mud = [tuple(round(lo[c] + (hi[c] - lo[c]) * (L - mn) / max(1, mx - mn)) for c in range(3)) + (255,) for L in lum]
    os.makedirs(OUT, exist_ok=True)
    for name, px in (("silt", silt(False)), ("wet_silt", silt(True)), ("river_mud", river_mud)):
        write_png(os.path.join(OUT, name + ".png"), 16, 16, px); print("wrote", name)

if __name__ == "__main__":
    main()
