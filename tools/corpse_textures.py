"""Draws the dead termite's texture (textures/entity/termite_corpse.png).

The dead-body model (client/AntCorpseRenderer) uses the same 32x32 patch of texture for every box, so the texture is
an even, mottled colour: a dead termite is the worker's pale cream, gone dull and a little yellow-grey as it dries,
with darker blotches. Needs no extra libraries (writes the PNG itself). Run from the project folder:
    python tools/corpse_textures.py
"""
import random
import struct
import zlib
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)

SIZE = 32
BASE = (196, 184, 150)     # dull dried cream
BLOTCH = (150, 138, 112)   # darker grey-brown patches
OUT = str(mod_paths.assets("savanna") / "textures/entity/termite_corpse.png")


def pixels():
    rng = random.Random(7)
    blotches = [(rng.randrange(SIZE), rng.randrange(SIZE), rng.uniform(1.5, 3.5)) for _ in range(14)]
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            shade = 0.0
            for bx, by, r in blotches:
                d = ((x - bx) ** 2 + (y - by) ** 2) ** 0.5
                shade = max(shade, max(0.0, 1.0 - d / r))
            noise = rng.randint(-10, 10)
            colour = [round(BASE[i] + (BLOTCH[i] - BASE[i]) * shade * 0.8) + noise for i in range(3)]
            row.append(tuple(max(0, min(255, c)) for c in colour) + (255,))
        rows.append(row)
    return rows


def write_png(path, rows):
    write_png_sized(path, rows, SIZE, SIZE)


def write_png_sized(path, rows, width, height):
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    write_png(OUT, pixels())
    print("wrote", OUT)
