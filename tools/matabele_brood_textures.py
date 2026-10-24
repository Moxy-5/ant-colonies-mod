"""Draws the Matabele ant's young and dead (M8 step 1c): textures/entity/matabele_larva.png and matabele_corpse.png.

The shared grub and dead-body models (client/AntLarvaRenderer, client/AntCorpseRenderer) use one patch of texture for
every box, so each texture is an even, mottled colour: a Megaponera larva is a pale creamy-white grub; a dead worker is
the living ant's plain black gone dull and dusty, with grey patches. Needs no extra libraries (writes the PNG itself).
Run from the project folder:
    python tools/matabele_brood_textures.py
"""
import random
import struct
import zlib
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)

LOOKS = {
    # name: (size, base colour, blotch colour, blotch strength, seed)
    "matabele_larva": (16, (232, 226, 205), (205, 196, 168), 0.6, 3),
    "matabele_corpse": (32, (34, 32, 31), (78, 74, 70), 0.7, 11),
}


def pixels(size, base, blotch, strength, seed):
    rng = random.Random(seed)
    blotches = [(rng.randrange(size), rng.randrange(size), rng.uniform(1.5, 3.5) * size / 32) for _ in range(size // 2 + 2)]
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            shade = 0.0
            for bx, by, r in blotches:
                d = ((x - bx) ** 2 + (y - by) ** 2) ** 0.5
                shade = max(shade, max(0.0, 1.0 - d / r))
            noise = rng.randint(-6, 6)
            colour = [round(base[i] + (blotch[i] - base[i]) * shade * strength) + noise for i in range(3)]
            row.append(tuple(max(0, min(255, c)) for c in colour) + (255,))
        rows.append(row)
    return rows


def write_png(path, size, rows):
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    for name, (size, base, blotch, strength, seed) in LOOKS.items():
        out = str(mod_paths.assets("savanna") / f"textures/entity/{name}.png")
        write_png(out, size, pixels(size, base, blotch, strength, seed))
        print("wrote", out)
