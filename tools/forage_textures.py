"""Draws the forage textures: the forage pile block (top and side) and the forage item.

Forage is what termites cut off dead plants: bits of dry grass, dead leaves and twigs. The pile is a tangle of short
straw-coloured blades with a few darker twig and leaf bits; the item is a small bundle of dry blades with a twig.

Run with Python 3 and Pillow from the project folder:  python tools/forage_textures.py
"""
import random
from pathlib import Path

from PIL import Image
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)

ROOT = Path(__file__).resolve().parent.parent
BLOCKS = mod_paths.assets("savanna") / "textures/block"
ITEMS = mod_paths.assets("savanna") / "textures/item"

# Dry grass, light to dark, and the darker bits mixed in.
STRAW = [(222, 200, 140), (205, 180, 118), (188, 160, 98), (170, 142, 84), (150, 124, 72)]
OLIVE = [(160, 156, 100), (138, 134, 84)]
TWIG = [(112, 84, 52), (92, 68, 42)]
LEAF = [(150, 98, 52), (124, 80, 44)]
GAP = (96, 78, 50)


def blade(img, x, y, dx, dy, length, colour, rng, wrap=True):
    """A thin blade from (x, y), one pixel at a time along (dx, dy), getting a little darker toward its tip."""
    w, h = img.size
    for i in range(length):
        px, py = x + dx * i, y + dy * i
        if wrap:
            px, py = px % w, py % h
        elif not (0 <= px < w and 0 <= py < h):
            return
        shade = max(0, 10 * i // max(1, length - 1) - rng.randint(0, 6))
        img.putpixel((px, py), tuple(max(0, c - shade) for c in colour) + (255,))


def pile_top(rng):
    img = Image.new("RGBA", (16, 16), GAP + (255,))
    # The bottom of the tangle: dark gaps with a few deep blades.
    for _ in range(18):
        blade(img, rng.randrange(16), rng.randrange(16), *rng.choice([(1, 0), (0, 1), (1, 1), (1, -1)]),
              rng.randint(2, 4), rng.choice(STRAW[3:]), rng)
    # Leaf bits and twigs.
    for _ in range(3):
        x, y = rng.randrange(16), rng.randrange(16)
        c = rng.choice(LEAF)
        for ox, oy in [(0, 0), (1, 0), (0, 1)]:
            img.putpixel(((x + ox) % 16, (y + oy) % 16), c + (255,))
    for _ in range(2):
        blade(img, rng.randrange(16), rng.randrange(16), *rng.choice([(1, 1), (1, -1), (1, 0)]), rng.randint(3, 5),
              rng.choice(TWIG), rng)
    # The top: lighter blades lying every which way.
    for _ in range(26):
        colour = rng.choice(OLIVE) if rng.random() < 0.18 else rng.choice(STRAW[:3])
        blade(img, rng.randrange(16), rng.randrange(16), *rng.choice([(1, 0), (0, 1), (1, 1), (1, -1), (-1, 1)]),
              rng.randint(2, 5), colour, rng)
    return img


def pile_side(rng):
    img = Image.new("RGBA", (16, 16), GAP + (255,))
    # Seen from the side the blades lie mostly flat, in layers; the lower ones are darker (in the shade).
    for row in range(16):
        dark = row / 15
        for _ in range(2):
            x = rng.randrange(16)
            colour = rng.choice(OLIVE) if rng.random() < 0.15 else STRAW[min(4, int(dark * 3.2 + rng.random() * 1.6))]
            blade(img, x, row, 1, rng.choice([0, 0, 0, 1, -1]), rng.randint(3, 7), colour, rng)
    for _ in range(3):
        blade(img, rng.randrange(16), rng.randrange(16), 1, rng.choice([0, 1]), rng.randint(3, 5), rng.choice(TWIG), rng)
    for _ in range(2):
        x, y = rng.randrange(16), rng.randrange(16)
        img.putpixel((x, y), rng.choice(LEAF) + (255,))
        img.putpixel(((x + 1) % 16, y), rng.choice(LEAF) + (255,))
    return img


def item(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # A bundle of dry blades fanning out from the bottom left, a twig across them, a dead leaf bit.
    starts = [(3, 13), (4, 13), (4, 12), (5, 13), (3, 12)]
    ends = [(13, 2), (14, 7), (9, 1), (14, 11), (5, 2)]
    for (sx, sy), (ex, ey) in zip(starts, ends):
        steps = max(abs(ex - sx), abs(ey - sy))
        colour = rng.choice(OLIVE) if rng.random() < 0.25 else rng.choice(STRAW[:4])
        for i in range(steps + 1):
            x = sx + round((ex - sx) * i / steps)
            y = sy + round((ey - sy) * i / steps)
            shade = 30 * i // steps
            img.putpixel((x, y), tuple(max(0, c - (30 - shade) // 2) for c in colour) + (255,))
    for x in range(1, 11):
        y = 8 + (x - 1) // 3
        img.putpixel((x, y), TWIG[(x // 2) % 2] + (255,))
    for x, y in [(9, 12), (10, 12), (9, 13), (10, 13), (11, 13)]:
        img.putpixel((x, y), LEAF[(x + y) % 2] + (255,))
    # A dark outline so it reads against any background, like vanilla items.
    outline = img.copy()
    for x in range(16):
        for y in range(16):
            if img.getpixel((x, y))[3] == 0 and any(
                    0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] == 255
                    for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]):
                outline.putpixel((x, y), (58, 44, 26, 255))
    return outline


def main():
    rng = random.Random(7)
    pile_top(rng).save(BLOCKS / "forage_pile_top.png")
    pile_side(rng).save(BLOCKS / "forage_pile_side.png")
    item(rng).save(ITEMS / "forage.png")
    print("wrote forage_pile_top.png, forage_pile_side.png and forage.png")


if __name__ == "__main__":
    main()
