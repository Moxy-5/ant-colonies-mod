"""Textures for papyrus and hippo grass (docs/plans/papyrus-and-hippo-grass.md, step 1, 2026-11-22; the user asked for
the models to be made and shown in the model viewer: `python tools/model_viewer.py papyrus`, the template
tools/papyrus_viewer.template.html). Plants are fun first, inspired by real life (CLAUDE.md).

Papyrus (Cyperus papyrus): a column of blocks like sugar cane, drawn as crossed planes.
  base   the bottom block: three green three-sided stems in brown sheath leaves (real papyrus has scale leaves at its foot)
  stem   the blocks above: the bare stems, one face lit, one in shade
  head   the top block, drawn wider than a block (1.6): the umbel, a mop of fine green rays fanning out from the stems' tips
         A  green mop      dense bright rays all round, like a firework
         B  brown tips     the same with brown flower spikelets at the ray tips (an older, flowering head)
         C  drooping mop   longer rays curving down at their ends, a shaggy broom
Hippo grass (Vossia cuspidata): stems floating out over the water from the bank, broad blades along them.
  mat    a top-view tile lying on the water's surface, gaps showing the water through it (tiles seamlessly)
  clump  the rooted tuft at the bank's edge, crossed planes, blades arching up and out
         A  lush green     B  blue-green (the real grass is a little glaucous)     C  flowering (pale spikes over A)
Only drawn here (no files written into the mod until the user picks). No PIL needed: rows of (r, g, b, a) pixels,
16 x 16 like the game's own plants.
"""
import math
import random

S = 16
CLEAR = (0, 0, 0, 0)


def clamp(c):
    return max(0, min(255, round(c)))


def blank():
    return [[CLEAR] * S for _ in range(S)]


def put(rows, x, y, c, wrap=False):
    if wrap:
        x, y = x % S, y % S
    if 0 <= x < S and 0 <= y < S:
        rows[y][x] = tuple(clamp(v) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def shade(c, k):
    return tuple(v * k for v in c[:3])


# --- papyrus -------------------------------------------------------------------------------------------------------
STEM = (92, 146, 58)
STEM_LIT = (132, 182, 78)
STEM_DARK = (60, 104, 40)
SHEATH = (128, 96, 58)
SHEATH_DARK = (92, 66, 40)
STALKS = [(3, 2), (7, 2), (11, 2)]          # x and width of the three stems in a block (my pick: a stand reads dense)


def _stalks(rows, seed, top=0, bottom=S):
    rng = random.Random(seed)
    for x0, w in STALKS:
        for y in range(top, bottom):
            for i in range(w):
                c = STEM_LIT if i == 0 else STEM_DARK if i == w - 1 and w > 1 else STEM
                put(rows, x0 + i, y, shade(c, rng.uniform(0.92, 1.06)))
    return rows


def papyrus_stem():
    return _stalks(blank(), 1)


def papyrus_base():
    rows = _stalks(blank(), 2)
    rng = random.Random(3)
    # brown sheath leaves wrapped round each stem's foot, ragged at their tops
    for x0, w in STALKS:
        top = 9 + rng.randint(0, 2)
        for y in range(top, S):
            for x in range(x0 - 1, x0 + w + 1):
                if y == top and rng.random() < 0.4:
                    continue
                edge = x in (x0 - 1, x0 + w)
                put(rows, x, y, shade(SHEATH_DARK if edge else SHEATH, rng.uniform(0.9, 1.08)))
    return rows


RAY = (104, 160, 62)
RAY_LIT = (150, 196, 90)
RAY_DARK = (70, 118, 44)
SPIKE = (150, 108, 60)


def papyrus_head(look):
    """The umbel: thin rays bursting from the stems' tip (bottom middle) out and up, gaps of sky between them, a few
    brighter threads at their ends (the real head is a firework of fine green threads, not a leafy ball)."""
    rows = blank()
    rng = random.Random({"A": 10, "B": 10, "C": 20}[look])
    n = 15
    for k in range(n):
        a = math.pi * (0.04 + 0.92 * k / (n - 1)) + rng.uniform(-0.04, 0.04)   # fanned across the upper half
        reach = rng.uniform(6.8, 8.4) if look != "C" else rng.uniform(7.5, 9.0)
        droop = 0.0 if look != "C" else rng.uniform(2.0, 4.0) * abs(math.cos(a))
        steps = int(reach * 2.4)
        tip = None
        for s in range(steps):
            t = s / steps
            x = 7.5 + math.cos(a) * reach * t
            y = 15.2 - math.sin(a) * reach * t + droop * t * t * 1.6
            c = RAY_DARK if t < 0.35 else RAY if t < 0.75 else RAY_LIT
            put(rows, int(round(x)), int(round(y)), shade(c, rng.uniform(0.93, 1.07)))
            tip = (x, y, a)
        if tip:                                   # each ray ends in a little tuft of threads
            x, y, a = tip
            for d in (-0.5, 0.5):
                put(rows, int(round(x + math.cos(a + d))), int(round(y - math.sin(a + d))), RAY_LIT)
            if look == "B" and rng.random() < 0.8:
                put(rows, int(round(x)), int(round(y)), shade(SPIKE, rng.uniform(0.85, 1.1)))
    for y in range(13, S):                        # the stems' tips under the rays
        put(rows, 7, y, STEM)
        put(rows, 8, y, STEM_DARK)
    return rows


# --- hippo grass ---------------------------------------------------------------------------------------------------
GRASS = {"A": ((118, 176, 62), (164, 206, 92), (80, 128, 46)),
         "B": ((110, 160, 120), (152, 196, 160), (72, 114, 84)),
         "C": ((118, 176, 62), (164, 206, 92), (80, 128, 46))}
FLOWER = (214, 204, 150)


def hippo_grass_mat(look):
    """Stems lying across the water, blades off them both ways; about a quarter left open so the water shows."""
    base, lit, dark = GRASS[look]
    rows = blank()
    rng = random.Random(30)
    for k in range(4):                       # stems floating out over the water, a little wavy, tiling across
        y0 = k * 4 + rng.randint(0, 1)
        ph = rng.uniform(0, 6.28)
        for x in range(S):
            y = y0 + round(0.8 * math.sin(6.283 * x / S + ph))
            put(rows, x, y, shade(dark, rng.uniform(0.9, 1.05)), wrap=True)
            if x % 2 == k % 2:               # a blade every 2 pixels, off one side then the other
                side = 1 if (x // 2) % 2 else -1
                for s in range(1, 4):
                    c = lit if s == 1 else base
                    put(rows, x + s, y + side * s, shade(c, rng.uniform(0.9, 1.08)), wrap=True)
    if look == "C":
        for _ in range(4):
            x, y = rng.randrange(S), rng.randrange(S)
            put(rows, x, y, FLOWER)
            put(rows, x + 1, y, shade(FLOWER, 0.9), wrap=True)
    return rows


def hippo_grass_clump(look):
    """A rooted tuft: broad blades arching up and out from the bottom middle."""
    base, lit, dark = GRASS[look]
    rows = blank()
    rng = random.Random(40)
    for k in range(9):
        a = math.pi * (0.18 + 0.64 * k / 8) + rng.uniform(-0.06, 0.06)
        reach = rng.uniform(9, 14)
        bend = rng.uniform(1.5, 3.5)
        steps = int(reach * 1.8)
        for s in range(steps):
            t = s / steps
            x = 7.5 + math.cos(a) * reach * t * 0.7 + (bend * t * t if math.cos(a) > 0 else -bend * t * t)
            y = 15 - math.sin(a) * reach * t + bend * t * t * 1.2
            c = lit if s % 5 == 0 else dark if t < 0.2 else base
            put(rows, int(round(x)), int(round(y)), shade(c, rng.uniform(0.9, 1.06)))
            put(rows, int(round(x)) + 1, int(round(y)), shade(base, 0.9))
    if look == "C":
        for _ in range(3):
            x, y = 4 + rng.randrange(8), rng.randrange(4)
            for d in range(3):
                put(rows, x, y + d, FLOWER)
    return rows


def all_looks():
    return {
        "papyrus": {"base": papyrus_base(), "stem": papyrus_stem(), "heads": {k: papyrus_head(k) for k in "ABC"}},
        "hippo_grass": {k: {"mat": hippo_grass_mat(k), "clump": hippo_grass_clump(k)} for k in "ABC"},
    }


if __name__ == "__main__":
    looks = all_looks()
    print("papyrus heads:", list(looks["papyrus"]["heads"]), "hippo grass looks:", list(looks["hippo_grass"]))
