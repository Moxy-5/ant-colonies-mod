"""Drafts for the floodplain trees: the sycamore fig and the doum palm, rebuilt from the user's two pictures (2026-11-13;
the fever tree dropped by the user the same day, "they look really bad in minecraft").

Only textures for the model viewer (python tools/model_viewer.py floodplain); nothing here is in the game yet. Each
texture is 16 x 16, a pixel an (r, g, b) tuple or None for see-through, in the same style as tools/tree_art.py. The
fruit, the palm fronds and the other sculpted pieces have no texture here: the page builds them from small plain boxes.

What the textures follow:
- Sycamore fig (Ficus sycomorus). Look A is the user's picture: cream bark mottled with big red-brown patches and pale
  ones. Look B is nearer the real tree: buff-yellow bark flaking in papery scales, the fresh bark under a flake
  yellow-green. The trunk is mottled from three tiles (the base, the patch, the pale patch), whole blocks of
  each as in the picture, a patch running over several blocks. Leaves in three tones (sunlit, plain,
  shaded) and a hanging spray for the underside of the leaf pads.
- Doum palm (Hyphaene thebaica). Look A is the user's picture: a warm dark brown, fibrous trunk. Look B is the real
  palm's lower trunk: grey-brown, ringed by old leaf scars.
"""
import math
import random

from tree_art import S, blank, mix, noise, shade, speckle


# ---- Sycamore fig ----

FIG_BARK = {  # look -> base, the patch (fresh bark), the pale patch, the shadow line under a flake
    "A": ((214, 196, 160), (160, 92, 72), (236, 228, 210), (150, 124, 94)),
    "B": ((200, 178, 122), (172, 176, 104), (230, 218, 184), (138, 116, 76)),
}


def fig_bark(look, tile):
    """Tile 0 the base bark, 1 the patch, 2 the pale patch, 3 the base bark drawn a second time (the two are mixed at
    random so the trunk does not repeat): whole blocks of each, as in the picture. Each tile carries a few flecks of the
    other colours and the same faint flakes, so the patches read as one bark."""
    rng = random.Random(f"figbark{look}{tile}")
    base, fresh, pale, line = FIG_BARK[look]
    ground = (base, fresh, pale, base)[tile]
    n = noise(f"figb{look}{tile}", 4)
    img = [[shade(ground, 0.92 + 0.16 * n(x, y)) for x in range(S)] for y in range(S)]
    for other in ((fresh, pale), (base, base), (base, base), (pale, fresh))[tile]:  # flecks: two or three pixels of another colour
        for _ in range(2):
            x, y = rng.randrange(S), rng.randrange(S)
            for dx, dy in ((0, 0), (1, 0), (0, 1))[:rng.randint(2, 3)]:
                img[(y + dy) % S][(x + dx) % S] = mix(img[(y + dy) % S][(x + dx) % S], other, 0.3)
    for _ in range(4):  # flakes: a short lit edge with a faint shadow line under it
        x, y, w = rng.randrange(S), rng.randrange(S), rng.randint(2, 6)
        for i in range(w):
            img[y][(x + i) % S] = shade(img[y][(x + i) % S], 1.04)
            img[(y + 1) % S][(x + i) % S] = mix(img[(y + 1) % S][(x + i) % S], line, 0.22)
    return speckle(img, rng, 0.04)


FIG_YOUNG = {  # look -> the smooth young bark, its lenticels (the corky breathing pores), a faint light streak
    "A": ((186, 180, 136), (124, 114, 84), (206, 200, 160)),
    "B": ((176, 172, 112), (120, 114, 72), (198, 194, 140)),
}


def fig_young_bark(look):
    """Tile 4, the young bark (2026-11-13, for the ages): a young sycamore fig's bark is smooth and pale, greener than
    the old bark, with small corky lenticels in short level dashes; the papery flakes and the patches come only with
    age, low on the trunk first. Same colour family as the look's base bark so the two meet without a seam."""
    rng = random.Random(f"figyoung{look}")
    base, pore, light = FIG_YOUNG[look]
    n = noise(f"figy{look}", 4)
    img = [[mix(base, light, n(x, y) * 0.35) for x in range(S)] for y in range(S)]
    for _ in range(9):  # lenticels: a dash two or three pixels long, darker, a lit pixel above it
        x, y, w = rng.randrange(S), rng.randrange(S), rng.randint(2, 3)
        for i in range(w):
            img[y][(x + i) % S] = mix(img[y][(x + i) % S], pore, 0.55)
            img[(y - 1) % S][(x + i) % S] = mix(img[(y - 1) % S][(x + i) % S], light, 0.3)
    return speckle(img, rng, 0.03)


FIG_LEAF = [(146, 160, 60), (104, 132, 52), (66, 98, 44)]  # sunlit, plain, shaded


def fig_leaves(tone):
    """Small rounded leaves, nearly closed; three tones so a pad has a lit top and a dark underside."""
    rng = random.Random(f"figleaf{tone}")
    base = FIG_LEAF[tone]
    img = [[shade(base, rng.uniform(0.62, 0.74)) if rng.random() < 0.86 else None for _ in range(S)] for _ in range(S)]
    for _ in range(30):
        cx, cy, f = rng.randrange(S), rng.randrange(S), rng.uniform(0.86, 1.18)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1))[:rng.randint(3, 6)]:
            img[(cy + dy) % S][(cx + dx) % S] = shade(base, f * (1.12 if (dx, dy) == (0, 0) else 1.0))
    return img


def sprig():
    """A spray of leaves hanging from the underside of a leaf pad: three drooping twigs, leaves down both sides."""
    rng = random.Random("figsprig")
    img = blank(None)
    twig = (70, 92, 44)
    for x0, length in ((3, 9), (8, 13), (12, 7)):
        x = float(x0)
        for y in range(length):
            xi = int(round(x)) % S
            img[y][xi] = twig
            if y % 2 == 1 or y == length - 1:
                for side in (-1, 1):
                    c = shade(FIG_LEAF[1 if rng.random() < 0.6 else 2], rng.uniform(0.9, 1.15))
                    for dx, dy in ((side, 0), (side * 2, 0), (side, 1), (side * 2, 1), (side, -1)):
                        if 0 <= xi + dx < S and 0 <= y + dy < S:
                            img[y + dy][xi + dx] = c
            x += rng.uniform(-0.35, 0.35)
    return img


# ---- Doum palm ----

def doum_bark(look):
    """A: warm dark brown and fibrous, the old leaf bases a faint criss-cross (the picture). B: grey-brown, ringed."""
    rng = random.Random(f"doum{look}")
    if look == "A":
        base, dark, pale = (116, 86, 56), (66, 46, 30), (152, 118, 78)
        n = noise("doumA", 4)
        img = [[mix(base, pale, n(x, y) * 0.45) for x in range(S)] for y in range(S)]
        for x in range(S):  # fibres: dark threads running up, broken here and there
            if rng.random() < 0.45:
                for y in range(S):
                    if rng.random() < 0.8:
                        img[y][x] = mix(img[y][x], dark, 0.4)
        for y in range(S):
            for x in range(S):
                d = min((x + y) % 8, (x - y) % 8)
                if d == 0:
                    img[y][x] = mix(img[y][x], dark, 0.55)
                elif d == 1:
                    img[y][x] = mix(img[y][x], pale, 0.3)
        return speckle(img, rng, 0.03)
    base, dark, pale = (126, 110, 94), (70, 60, 52), (160, 144, 124)
    n = noise("doumB", 4)
    img = [[mix(base, pale, n(x, y) * 0.6) for x in range(S)] for y in range(S)]
    for y in range(S):
        for x in range(S):
            if y % 4 == 3:
                img[y][x] = mix(img[y][x], dark, 0.6 if (x + y // 4) % 5 else 0.3)
            elif y % 4 == 0:
                img[y][x] = mix(img[y][x], pale, 0.35)
            elif rng.random() < 0.12:
                img[y][x] = mix(img[y][x], dark, 0.25)
    return speckle(img, rng, 0.03)


# ---- Dead wood (2026-11-13, the dead stages; my picks) ----

DEAD_GREY = (150, 146, 136)


def fig_dead_bark(look):
    """Tile 5, dead fig bark: the look's bark gone dull and grey once the tree dies, the papery flakes curling off
    (a dark shadow under each lifted edge) and grey wood showing where one has fallen."""
    rng = random.Random(f"figdead{look}")
    base, _, pale, line = FIG_BARK[look]
    ground = mix(base, DEAD_GREY, 0.5)
    n = noise(f"figd{look}", 4)
    img = [[shade(ground, 0.9 + 0.18 * n(x, y)) for x in range(S)] for y in range(S)]
    for _ in range(7):  # curling flakes: a lit lifted edge, a dark gap under it, grey wood beside
        x, y, w = rng.randrange(S), rng.randrange(S), rng.randint(3, 6)
        for i in range(w):
            xx = (x + i) % S
            img[y][xx] = mix(img[y][xx], pale, 0.35)
            img[(y + 1) % S][xx] = mix(img[(y + 1) % S][xx], line, 0.6)
            if rng.random() < 0.5:
                img[(y + 2) % S][xx] = mix(img[(y + 2) % S][xx], (176, 172, 162), 0.6)
    return speckle(img, rng, 0.05)


def bare_wood():
    """Tile 6, the bare wood of a dead fig: weathered silver-grey, the grain running up in long darker lines, a few
    deep cracks. Fig wood is soft, so it splits and checks as it dries."""
    rng = random.Random("figbare")
    base, grain, crack = (170, 166, 154), (128, 124, 114), (78, 74, 68)
    n = noise("figbare", 4)
    img = [[shade(base, 0.92 + 0.14 * n(x, y)) for x in range(S)] for y in range(S)]
    for x in range(S):  # grain: broken lines running up
        if rng.random() < 0.5:
            for y in range(S):
                if rng.random() < 0.75:
                    img[y][x] = mix(img[y][x], grain, 0.35)
    for _ in range(2):  # checks: deep cracks along the grain
        x, y0, length = rng.randrange(S), rng.randrange(S), rng.randint(5, 10)
        for i in range(length):
            img[(y0 + i) % S][x] = crack
            if rng.random() < 0.3:
                x = (x + rng.choice((-1, 1))) % S
    return speckle(img, rng, 0.04)


def termite_mud():
    """Tile 7, the termites' mud over dead wood, built from the floodplain's own grey-brown silt (Macrotermes build
    with the soil under them, so here it is the silt's colour, not red savanna earth, darkened as the mud is kept damp):
    little pellets, darker gaps."""
    rng = random.Random("figmud")
    base, dark, light = (110, 94, 72), (74, 62, 48), (140, 122, 94)
    img = [[shade(base, rng.uniform(0.9, 1.05)) for _ in range(S)] for _ in range(S)]
    for _ in range(26):  # pellets: a lit top, a dark rim below
        x, y = rng.randrange(S), rng.randrange(S)
        img[y][x] = light
        img[y][(x + 1) % S] = mix(img[y][(x + 1) % S], light, 0.5)
        img[(y + 1) % S][x] = dark
    return img


def doum_dead_bark(look):
    """A dead doum stem: the look's trunk weathered grey, the fibres standing out, split by long cracks. Doum wood is
    hard and fibrous, so a dead stem stands for years."""
    rng = random.Random(f"doumdead{look}")
    img = [[mix(px, DEAD_GREY, 0.7) for px in row] for row in doum_bark(look)]
    for _ in range(2):
        x, y0, length = rng.randrange(S), rng.randrange(S), rng.randint(6, 12)
        for i in range(length):
            img[(y0 + i) % S][x] = mix(img[(y0 + i) % S][x], (60, 56, 50), 0.7)
    return speckle(img, rng, 0.04)


# ---- The ground the trees stand on ----

def flat_ground(name, base, lumps=0.2):
    rng = random.Random(name)
    n = noise(name, 4)
    return [[shade(base, 1 - lumps / 2 + lumps * n(x, y) + rng.uniform(-0.03, 0.03)) for x in range(S)] for y in range(S)]


def all_textures():
    """Every texture the floodplain tree page needs: name -> rows of pixels."""
    out = {}
    for look in ("A", "B"):
        for tile in range(4):
            out[f"bark_fig_{look}_{tile}"] = fig_bark(look, tile)
        out[f"bark_fig_{look}_4"] = fig_young_bark(look)
        out[f"bark_doum_{look}"] = doum_bark(look)
        out[f"bark_fig_{look}_5"] = fig_dead_bark(look)
        out[f"bark_fig_{look}_6"] = bare_wood()
        out[f"bark_fig_{look}_7"] = termite_mud()
        out[f"bark_doumdead_{look}"] = doum_dead_bark(look)
    for tone in range(3):
        out[f"leaves_fig_{tone}"] = fig_leaves(tone)
    out["sprig_fig"] = sprig()
    out["grass"] = flat_ground("floodgrass", (120, 152, 70))
    out["silt"] = flat_ground("floodsilt", (164, 150, 116), 0.14)
    out["water"] = flat_ground("floodwater", (88, 104, 70), 0.1)
    return out
