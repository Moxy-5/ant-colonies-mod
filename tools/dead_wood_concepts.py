"""Planning only (nothing in the game uses it): concept pictures for the untouched Dead Wood side, so the user can pick a
look. Writes references/termites/preview/dead_wood_concepts.png: three options, each a 4-block log seen from the side
(each block a different random variant, as the game would pick per block) above its eaten stages are kept as they are.

Real long-dead acacia: the bark falls off in big pieces and the bare wood bleaches silver-grey in the sun; the grain
opens into long wavy cracks that run the whole length of the trunk; the bark that is left is dark grey-brown and
deeply furrowed along the trunk, with ragged, lifting edges; branch stubs leave knots; lichen grows on old wood.

    A  bleached silver: almost all bare wood, long wavy cracks, a knot now and then
    B  bark remnants: bare silver wood with ragged patches of dark furrowed acacia bark still on it
    C  old and lichened: greyer-brown, more cracks, pale green-grey lichen spots

Run from the project folder:  python tools/dead_wood_concepts.py
"""
import math
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "references/termites/preview/dead_wood_concepts.png"

S = 16


def clamp(c):
    return max(0, min(255, round(c)))


def shade(color, f):
    return tuple(clamp(c * f) for c in color)


def smooth_noise(rng, n, scale):
    """Values along a ring of n pixels (wraps round), smooth over about `scale` pixels."""
    points = [rng.uniform(-1, 1) for _ in range(max(2, n // scale))]
    out = []
    for i in range(n):
        t = i / n * len(points)
        a, b = points[int(t) % len(points)], points[(int(t) + 1) % len(points)]
        f = t - int(t)
        f = f * f * (3 - 2 * f)
        out.append(a + (b - a) * f)
    return out


# Rows where the long cracks cross the block edges: the same for every variant, so a crack carries on into the next
# block whichever variant it is.
EDGE_ROWS = [3, 8, 13]


def fissures(rng, count):
    """Cracks along the grain (left to right): `count` long ones that meet the block edges at EDGE_ROWS and wander in
    between, and one or two short ones that start and stop inside the block. A list of (x from, x to, row for each x, width)."""
    out = []
    for y0 in EDGE_ROWS[:count]:
        amp = rng.uniform(-1.8, 1.8)
        wobble = rng.uniform(-0.6, 0.6)
        rows = [y0 + amp * math.sin(math.pi * x / S) + wobble * math.sin(2 * math.pi * x / S) for x in range(S)]
        out.append((0, S, rows, 1))
    for _ in range(rng.randint(1, 2)):
        start, length = rng.randrange(S - 4), rng.randint(4, 9)
        y0 = rng.uniform(2, S - 3)
        rows = [y0 + 0.8 * math.sin(x / 2.5) for x in range(S)]
        out.append((start, min(S, start + length), rows, 1))
    return out


def wood(rng, base, light, crack, cracks, knot):
    """Bare weathered wood: grain streaks along the length, wavy cracks with a light edge above (depth), maybe a knot."""
    img = [[None] * S for _ in range(S)]
    streak = [smooth_noise(rng, S, 4) for _ in range(S)]
    rowtone = [rng.uniform(-1, 1) for _ in range(S)]
    for y in range(S):
        for x in range(S):
            f = 1 + 0.035 * rowtone[y] + 0.05 * streak[y][x] + rng.uniform(-0.035, 0.035)
            img[y][x] = shade(light if streak[y][x] > 0.55 else base, f)
    for start, end, rows, width in fissures(rng, cracks):
        for x in range(start, end):
            y = int(round(rows[x])) % S
            for w in range(width):
                img[(y + w) % S][x] = shade(crack, rng.uniform(0.9, 1.1))
            img[(y - 1) % S][x] = shade(light, 1.06)
    if knot:
        cx, cy = rng.uniform(4, 12), rng.uniform(4, 12)
        for y in range(S):
            for x in range(S):
                d = math.hypot((x - cx) / 1.8, y - cy)
                if d < 1.0:
                    img[y][x] = shade(crack, 0.85)
                elif d < 1.9:
                    img[y][x] = shade(base, 0.8)
                elif d < 2.6 and rng.random() < 0.6:
                    img[y][x] = shade(light, 1.05)
    return img


def bark_patches(rng, img, bark, ridge, furrow, edge, amount):
    """Ragged patches of dark acacia bark, furrowed along the trunk, with a lighter lifting edge."""
    blobs = [(rng.uniform(0, S), rng.uniform(4, S), rng.uniform(3, 6.5), rng.uniform(1.5, 2.8)) for _ in range(amount)]
    furrows = [smooth_noise(rng, S, 5) for _ in range(4)]
    inside = [[False] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            for bx, by, rx, ry in blobs:
                dx = min(abs(x - bx), S - abs(x - bx))
                if (dx / rx) ** 2 + ((y - by) / ry) ** 2 < 1 + rng.uniform(-0.25, 0.25):
                    inside[y][x] = True
    for y in range(S):
        for x in range(S):
            if not inside[y][x]:
                continue
            on_furrow = any(abs(3 + 4 * k + 1.2 * furrows[k][x] - y) < 0.5 for k in range(4))
            color = furrow if on_furrow else (ridge if rng.random() < 0.25 else bark)
            img[y][x] = shade(color, rng.uniform(0.92, 1.06))
    for y in range(S):
        for x in range(S):
            if inside[y][x] and y > 0 and not inside[y - 1][x]:
                img[y - 1][x] = edge                                   # the lifting edge catches the light
            if inside[y][x] and y < S - 1 and not inside[y + 1][x]:
                img[y + 1][x] = shade(img[y + 1][x], 0.72)             # shadow under the raised bark
    return img


def lichen(rng, img, spots):
    for _ in range(spots):
        cx, cy = rng.randrange(S), rng.randrange(S)
        for dx, dy in [(0, 0), (1, 0), (0, 1), (-1, 0), (1, 1)][:rng.randint(2, 5)]:
            img[(cy + dy) % S][(cx + dx) % S] = shade((156, 166, 128), rng.uniform(0.9, 1.08))
    return img


def option(name, variant):
    rng = random.Random(f"{name}{variant}")  # (a string seed gives the same picture every run)
    if name == "A":
        return wood(rng, (162, 157, 146), (186, 181, 170), (70, 64, 58), 3, variant == 2)
    if name == "B":
        img = wood(rng, (164, 158, 146), (188, 182, 170), (72, 66, 58), 2, variant == 1)
        return bark_patches(rng, img, (84, 70, 58), (104, 90, 76), (50, 40, 34), (132, 116, 98), 2 + variant % 2)
    img = wood(rng, (134, 126, 114), (158, 150, 136), (64, 56, 50), 4, variant == 0)
    return lichen(rng, img, 3 + variant)


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    scale, blocks, gap = 6, 4, 2
    names = ["A", "B", "C"]
    width = (blocks * S + 2 * gap) * scale
    height = (len(names) * (S + gap) + gap) * scale
    sheet = [[(120, 160, 90, 255) for _ in range(width)] for _ in range(height)]
    for row, name in enumerate(names):
        for b in range(blocks):
            tex = option(name, [0, 1, 2, 1][b])
            for y in range(S):
                for x in range(S):
                    for sy in range(scale):
                        for sx in range(scale):
                            sheet[((gap + row * (S + gap)) + y) * scale + sy][(gap + b * S + x) * scale + sx] = tex[y][x] + (255,)
    write_png(OUT, sheet)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
