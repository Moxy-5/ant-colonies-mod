"""Textures for the river algae (M9 river life, the first link of the food chain; 2026-11-14), three looks to pick from in
the algae viewer (python tools/model_viewer.py algae, tools/algae_viewer.template.html):
  A  green felt      a soft, even green film, darker specks; thickens from scattered specks to a full carpet
  B  slime mat       olive brown-green blobs with pale gas bubbles caught in it when thick (the "pond scum" look)
  C  waving strands  A's film plus bright green streamers standing up from the bed when thick, swaying in the water
Each look has 4 growth stages: 0 grazed bare (a few specks left, grows back from them), 1 thin, 2 thick, 3 lush.
The mat is a thin layer drawn over whatever the bed is (silt, river mud, sand, stone), so the bed shows through its gaps.
Only drawn here (no files written into the mod yet: that comes once the user picks a look).
No PIL needed; every texture tiles seamlessly (16 x 16, built from whole-number waves)."""
import math
import random

S = 16
COVER = [0.10, 0.42, 0.78, 0.97]      # share of the block's top the mat covers at each stage (my picks)


def clamp(c):
    return max(0, min(255, round(c)))


def _noise(seed, waves=6):
    """A smooth 16 x 16 field that tiles: a sum of waves with whole-number frequencies."""
    rng = random.Random(seed)
    parts = [(rng.randint(1, 3), rng.randint(-3, 3), rng.uniform(0, 6.283), rng.uniform(0.5, 1)) for _ in range(waves)]
    return [[sum(a * math.sin(6.283 * (fx * x + fy * y) / S + p) for fx, fy, p, a in parts) for x in range(S)]
            for y in range(S)]


def _mask(field, share):
    """The pixels whose field value lies in the top `share` of the block: blobby patches, exactly that much cover."""
    flat = sorted((v for row in field for v in row), reverse=True)
    if share <= 0:
        return [[False] * S for _ in range(S)]
    cut = flat[min(len(flat) - 1, max(0, round(share * len(flat)) - 1))]
    return [[v >= cut for v in row] for row in field]


def _paint(mask, base, dark, light, seed, edge_dark=True):
    rng = random.Random(seed)
    rows = []
    for y in range(S):
        row = []
        for x in range(S):
            if not mask[y][x]:
                row.append((0, 0, 0, 0))
                continue
            # the mat is a bit darker and thinner at its edges (a pixel with a bare neighbour)
            edge = edge_dark and not all(mask[(y + dy) % S][(x + dx) % S] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            r = rng.random()
            c = dark if r < 0.18 else light if r > 0.88 else base
            k = rng.uniform(0.9, 1.08) * (0.82 if edge else 1)
            row.append(tuple(clamp(v * k) for v in c) + (200 if edge else 235,))
        rows.append(row)
    return rows


def felt(stage):
    field = _noise(11)
    return _paint(_mask(field, COVER[stage]), (78, 128, 52), (52, 92, 36), (110, 160, 70), 100 + stage)


def slime(stage):
    field = _noise(23, 8)
    rows = _paint(_mask(field, COVER[stage]), (96, 112, 50), (68, 80, 36), (124, 136, 66), 200 + stage)
    if stage >= 2:  # gas bubbles caught in a thick mat: pale dots with a bright glint
        rng = random.Random(300 + stage)
        for _ in range(3 if stage == 2 else 6):
            x, y = rng.randrange(S), rng.randrange(S)
            if rows[y][x][3]:
                rows[y][x] = (196, 214, 170, 245)
                rows[y][(x + 1) % S] = (232, 240, 214, 250) if rng.random() < 0.6 else rows[y][(x + 1) % S]
    return rows


def strands_film(stage):
    return felt(stage)


def streamer(tall):
    """The upright tuft for look C (drawn on two crossed planes, as Minecraft draws grass): thin wavy strands rising
    from the bottom edge, brighter at their tips; `tall` the stage (2 short, 3 long)."""
    rows = [[(0, 0, 0, 0)] * S for _ in range(S)]
    rng = random.Random(400 + tall)
    for i in range(7 if tall == 3 else 5):
        x0 = 1 + i * 2 + rng.randint(0, 1)
        top = rng.randint(2, 6) if tall == 3 else rng.randint(8, 11)
        phase = rng.uniform(0, 6.283)
        for y in range(S - 1, top - 1, -1):
            t = (S - 1 - y) / max(1, S - 1 - top)          # 0 at the foot, 1 at the tip
            x = int(round(x0 + 1.4 * t * math.sin(phase + y * 0.7))) % S
            g = (60 + 70 * t, 104 + 76 * t, 40 + 30 * t)
            rows[y][x] = tuple(clamp(v) for v in g) + (240,)
    return rows


LOOKS = {"A": felt, "B": slime, "C": strands_film}


def all_looks():
    """{look: {"mat": [rows per stage], "tuft": {stage: rows}}} for the viewer."""
    out = {}
    for look, fn in LOOKS.items():
        out[look] = {"mat": [fn(s) for s in range(4)], "tuft": {}}
    out["C"]["tuft"] = {2: streamer(2), 3: streamer(3)}
    return out
