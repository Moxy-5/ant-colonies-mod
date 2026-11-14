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


# ---- The game's files (floodplain trees step 3, 2026-11-13: the sycamore fig's blocks, look A, shape B) ----
# python tools/floodplain_tree_art.py writes the fig's textures, models, blockstates, item models and loot into the
# savanna mod. The blocks are in registry/TreeBlocks.java (FIG_LOG ... FIG_SEEDLING); a fig block's bark is one of
# block/FigBark (mottled, patch, pale, young, dead, bare).

GAME_LOOK = "A"  # the user's pick, 2026-11-13
BARKS = ("mottled", "patch", "pale", "young", "dead", "bare")
FIG_TAPER = (14, 12, 10, 8, 6, 4)  # FigBranchBlock.width, the model viewer's TAPER
FIG_TINTS = ((0x74, 0x20, 0x2a), (0x7e, 0x24, 0x30), (0x8a, 0x26, 0x30), (0xa0, 0x2f, 0x38), (0xb2, 0x43, 0x3c),
             (0xc2, 0x90, 0x3c), (0x8f, 0x9c, 0x48))  # ripe dark red to unripe yellow and green, as in the viewer
FIG_TINT_ODDS = (2, 1, 2, 2, 1, 1, 1)  # the viewer's mix: mostly reds, a few still yellow or green
FIG_STALK = (0x6e, 0x5c, 0x36)
# Step 4b, the fig's stages (my picks): cell 8 light green, 9 deep green, 10 orange, then rotting fallen figs, 11 brown,
# 12 dark brown, 13 grey mould.
FIG_STAGE_TINTS = ((0xa6, 0xb4, 0x5c), (0x6f, 0x82, 0x38), (0xc8, 0x6e, 0x34), (0x5e, 0x40, 0x26), (0x3e, 0x2a, 0x1c),
                   (0x8a, 0x80, 0x68))
FIG_GREEN_CELLS, FIG_TURNING_CELLS, FIG_ROT_CELLS = (6, 8, 9), (5, 10, 8, 4), (11, 12, 13, 11)
FIG_STAGES = ("bare", "green", "turning", "ripe")


def fig_log_top(dead):
    """The cut end of a fig log: soft pale wood, faint rings, the bark rim (grey once the tree is dead)."""
    heart, sap, rim = ((206, 186, 148), (224, 208, 172), (170, 136, 104)) if not dead else (
        (164, 158, 144), (176, 170, 156), (124, 118, 108))
    rng = random.Random(f"figend{dead}")
    img = []
    for y in range(S):
        row = []
        for x in range(S):
            r = math.hypot(x - 7.5, y - 7.5)
            if max(abs(x - 7.5), abs(y - 7.5)) > 7:
                c = rim
            elif r > 5.6:
                c = sap
            else:
                c = shade(heart, 0.9 if int(r * 1.2) % 2 else 1.03)
            row.append(c)
        img.append(row)
    if dead:
        for _ in range(3):  # radial checks in dry dead wood
            a = rng.uniform(0, 6.283)
            for i in range(2, 7):
                img[int(7.5 + i * math.sin(a))][int(7.5 + i * math.cos(a))] = (96, 90, 82)
    return speckle(img, rng, 0.04)


def fig_seedling():
    """A fig seedling: a pale stem with a leaf bud at the top and broad rounded leaves turning up it, as in the viewer."""
    rng = random.Random("figseedling")
    img = blank(None)
    stem = (172, 166, 120)
    for y in range(3, S):
        img[y][7] = stem
        img[y][8] = shade(stem, 0.85) if y > 9 else None
    img[2][7] = (130, 150, 60)
    for y0, side in ((4, -1), (7, 1), (10, -1), (12, 1)):  # broad leaves, alternate sides
        tone = FIG_LEAF[0 if y0 < 6 else 1]
        for dy in range(-1, 3):
            for dx in range(1, 6 - abs(dy - 1)):
                x = 7 + side * dx if side < 0 else 8 + dx
                if 0 <= x < S and 0 <= y0 + dy < S:
                    img[y0 + dy][x] = shade(tone, rng.uniform(0.9, 1.1) * (1.08 if dy == 0 else 1))
    return img


def fig_fruit_atlas():
    """One small picture holding every fig colour, 4 x 4 pixels each (cell i at column i % 4, row i // 4): the fig
    tints, then the stalk. The bunch models paint each little box from one cell."""
    rng = random.Random("figfruit")
    img = blank((0, 0, 0))
    for i, c in enumerate(FIG_TINTS + (FIG_STALK,) + FIG_STAGE_TINTS):
        cx, cy = (i % 4) * 4, (i // 4) * 4
        for y in range(4):
            for x in range(4):
                f = 1.12 if (x, y) == (1, 1) else 0.92 if x == 3 or y == 3 else 1.0
                img[cy + y][cx + x] = shade(c, f * rng.uniform(0.96, 1.04))
    return img


def _cell(i):
    cx, cy = (i % 4) * 4, (i // 4) * 4
    return [cx, cy, cx + 4, cy + 4]


def _box(lo, hi, cell):
    return {"from": [round(v, 3) for v in lo], "to": [round(v, 3) for v in hi],
            "faces": {f: {"texture": "#fruit", "uv": cell} for f in ("north", "south", "east", "west", "up", "down")}}


def _fig(rng, cx, cy, cz, size, stage="ripe", tones=None):
    """One fig: three crossing boxes, so it looks round from every side (the viewer's figs). Every stage of one bunch
    draws the same dice from rng (the same figs in the same places); the stage's colour comes from tones, its own dice.
    A bare spur is one small stub where the fig will grow; "rotten" is a fallen fig going brown."""
    tint = rng.choices(range(len(FIG_TINTS)), FIG_TINT_ODDS)[0]
    if stage == "bare":
        return [_box((cx - 0.75, cy - 0.75, cz - 0.75), (cx + 0.75, cy + 0.75, cz + 0.75), _cell(len(FIG_TINTS)))]
    if stage != "ripe":
        tint = tones.choice({"green": FIG_GREEN_CELLS, "turning": FIG_TURNING_CELLS, "rotten": FIG_ROT_CELLS}[stage])
    if stage == "green":
        size -= 1  # unripe figs are smaller
    h, t = size / 2, size / 2 - 1
    return [_box((cx - h, cy - t, cz - t), (cx + h, cy + t, cz + t), _cell(tint)),
            _box((cx - t, cy - h, cz - t), (cx + t, cy + h, cz + t), _cell(tint)),
            _box((cx - t, cy - t, cz - h), (cx + t, cy + t, cz + h), _cell(tint))]


def fig_bunch_model(variant, stage="ripe"):
    """A bunch of figs hanging from the block above: a short stalk, then 3 or 4 rings of figs, smaller to the bottom."""
    rng, tones = random.Random(f"figbunch{variant}"), random.Random(f"figbunch{variant}{stage}")
    stalk = _cell(len(FIG_TINTS))
    els = [_box((7, 10, 7), (9, 16, 9), stalk), _box((7.5, 7, 7.5), (8.5, 10, 8.5), stalk)]
    rings = ((3, 3.4), (4, 4.4), (2, 2.2)) + (((1, 0.0),) if rng.random() < 0.5 else ())
    for level, (count, radius) in enumerate(rings):
        y = 16 - (7.5 + level * 3.2)
        turn = rng.uniform(0, 6.283)
        for k in range(count):
            a = turn + k * 6.283 / count
            size = rng.choice((5, 6)) if level < 2 else 5
            els += _fig(rng, 8 + radius * math.cos(a), y - rng.uniform(0, 1.2), 8 + radius * math.sin(a), size, stage, tones)
    return {"textures": {"particle": "antcolonies:block/fig_fruit", "fruit": "antcolonies:block/fig_fruit"}, "elements": els}


def trunk_figs_model(variant, stage="ripe"):
    """Figs growing on the bark (built facing north: the bark behind them at the south face), 4 to 6 in a cluster."""
    rng, tones = random.Random(f"trunkfigs{variant}"), random.Random(f"trunkfigs{variant}{stage}")
    els = []
    for _ in range(rng.randint(4, 6)):
        size = rng.choice((5, 6))
        out = rng.uniform(2, 4.5)
        els += _fig(rng, 8 + rng.uniform(-5.5, 5.5) * 0.7, 8 + rng.uniform(-5.5, 5.5) * 0.7, 16 - out, size, stage, tones)
    return {"textures": {"particle": "antcolonies:block/fig_fruit", "fruit": "antcolonies:block/fig_fruit"}, "elements": els}


def fallen_figs_model(fruits, old):
    """Ripe figs lying under the tree, 2 a fruit (my pick), fresh red or rotting brown; each squashed a little flat."""
    rng, tones = random.Random(f"fallenfigs{fruits}"), random.Random(f"fallenfigs{fruits}{old}")
    els = []
    spots = ((5, 6), (10, 9), (7, 11), (11, 4), (4, 11), (9, 6))
    for x, z in spots[:fruits * 2]:
        size = rng.choice((4, 5))
        h = size / 2
        for box in _fig(rng, x + rng.uniform(-0.8, 0.8), h - 0.6, z + rng.uniform(-0.8, 0.8), size, "rotten" if old else "ripe", tones):
            box["from"][1] = max(0, box["from"][1])
            els.append(box)
    return {"textures": {"particle": "antcolonies:block/fig_fruit", "fruit": "antcolonies:block/fig_fruit"}, "elements": els}


# The doum nuts on the ground (step 6b, my picks): a little atlas as the fig's, 4 ripe tones (cells 0 to 3), the glossy
# top (4), the dark ends (5), and 4 dull grey-browns for nuts that have lain a while (6 to 9).
DOUM_GROUND = ((0x7e, 0x38, 0x14), (0x8a, 0x40, 0x18), (0x96, 0x49, 0x1c), (0xa2, 0x53, 0x1f), (0xc0, 0x74, 0x3a),
               (0x50, 0x24, 0x0e), (0x6a, 0x4c, 0x36), (0x74, 0x58, 0x40), (0x62, 0x48, 0x34), (0x7c, 0x62, 0x4a))


def doum_nut_atlas():
    rng = random.Random("doumnutfruit")
    img = blank((0, 0, 0))
    for i, c in enumerate(DOUM_GROUND):
        cx, cy = (i % 4) * 4, (i // 4) * 4
        for y in range(4):
            for x in range(4):
                f = 1.15 if (x, y) == (1, 1) else 0.92 if x == 3 or y == 3 else 1.0
                img[cy + y][cx + x] = shade(c, f * rng.uniform(0.95, 1.05))
    return img


def _nut_box(lo, hi, cell, angle, cx, cz):
    box = {"from": [round(v, 3) for v in lo], "to": [round(v, 3) for v in hi],
           "faces": {f: {"texture": "#fruit", "uv": _cell(cell)} for f in ("north", "south", "east", "west", "up", "down")}}
    if angle:
        box["rotation"] = {"origin": [cx, 0, cz], "axis": "y", "angle": angle}
    return box


def fallen_doum_nuts_model(fruits, old):
    """Doum nuts lying on their sides, 1 a fruit: egg-shaped, about 6 pixels across and 10 long, glossy on top."""
    rng = random.Random(f"fallennuts{fruits}")
    els = []
    for x, z in ((8, 8), (4.5, 5), (11.5, 10.5))[:fruits]:
        angle = rng.choice((-45, -22.5, 0, 22.5, 45))
        tone = rng.randrange(6, 10) if old else rng.randrange(0, 4)
        top = tone if old else 4
        els += [_nut_box((x - 3, 0, z - 4), (x + 3, 4.5, z + 4), tone, angle, x, z),
                _nut_box((x - 2, 0.5, z - 5), (x + 2, 4, z + 5), 5 if not old else tone, angle, x, z),
                _nut_box((x - 2, 4.5, z - 3), (x + 2, 5.5, z + 3), top, angle, x, z)]
    return {"textures": {"particle": "antcolonies:block/doum_nut_fruit", "fruit": "antcolonies:block/doum_nut_fruit"}, "elements": els}


def _blob(img, cx, cy, rx, ry, tone, rng):
    """A round fruit seen from the side, lit from the upper left."""
    for y in range(S):
        for x in range(S):
            d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
            if d <= 1:
                light = 1.25 - 0.45 * ((x + 0.5 - cx + rx * 0.4) ** 2 + (y + 0.5 - cy + ry * 0.4) ** 2) ** 0.5 / max(rx, ry)
                img[y][x] = shade(tone, max(0.6, light) * rng.uniform(0.95, 1.05))


def fruit_icons():
    """Item pictures: a ripe fig and a green one (a fat pear shape, the eye at the bottom, a stalk on top), a ripe doum
    nut and a green one (a glossy egg)."""
    rng = random.Random("fruiticons")
    out = []
    for body, eye in (((0x9a, 0x2c, 0x34), (0x52, 0x14, 0x1c)), ((0x8f, 0x9c, 0x48), (0x5e, 0x6a, 0x2a))):
        img = blank(None)
        _blob(img, 8, 10, 5.2, 4.8, body, rng)
        _blob(img, 8, 6.2, 3.2, 3.0, body, rng)
        for y in range(1, 4):
            img[y][8] = FIG_STALK
        img[14][8] = eye
        img[3][7] = shade(body, 0.8)
        out.append(img)
    for body in ((0x96, 0x49, 0x1c), (0x76, 0x64, 0x2c)):
        img = blank(None)
        _blob(img, 8, 8.5, 5.0, 6.6, body, rng)
        for y in range(1, 3):
            img[y][8] = (0x6e, 0x5c, 0x36)
        img[5][5], img[6][5], img[5][6] = (shade(body, 1.7),) * 3  # the gloss
        out.append(img)
    return out


def fruit_loot(name, ripe, ripe_count, unripe_steps):
    """A hanging fruit block's drops (step 4b and 6b, my picks): with shears or silk touch the block itself; else by its
    stage, ripe fruit, or for green and turning ones what unripe_steps lists, nothing from a bare stalk."""
    from tree_art import SHEARS_OR_SILK, loot

    def at(stage):
        return {"condition": "minecraft:block_state_property", "block": f"antcolonies:{name}", "properties": {"stage": str(stage)}}

    def item(what, lo, hi):
        e = {"type": "minecraft:item", "name": f"antcolonies:{what}"}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e
    children = [{"type": "minecraft:item", "name": f"antcolonies:{name}", "conditions": [SHEARS_OR_SILK]},
                dict(item(ripe, *ripe_count), conditions=[at(3)])]
    for stage, items in unripe_steps:
        entry = item(*items[0]) if len(items) == 1 else {"type": "minecraft:group", "children": [item(*i) for i in items]}
        children.append(dict(entry, conditions=[at(stage)]))
    loot(name, [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": children}],
                 "conditions": [{"condition": "minecraft:survives_explosion"}]}])


def fallen_loot(name, fruit, rot_gives_nothing):
    """Fallen fruit broken: one fruit a fruit lying there; rotten figs nothing (my pick)."""
    from tree_art import loot
    children = []
    for n in (1, 2, 3):
        props = {"fruits": str(n)}
        if rot_gives_nothing:
            props["old"] = "false"
        e = {"type": "minecraft:item", "name": f"antcolonies:{fruit}",
             "conditions": [{"condition": "minecraft:block_state_property", "block": f"antcolonies:{name}", "properties": props}]}
        if n > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": n}]
        children.append(e)
    loot(name, [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": children}],
                 "conditions": [{"condition": "minecraft:survives_explosion"}]}])


def fruit_files():
    """The floodplain trees' fruit (steps 4b and 6b): stage models, fallen fruit, items, drops."""
    from tree_art import ASSETS, flat_item, item_from_block, model, texture, write_json
    stages = list(enumerate(FIG_STAGES))
    for v in range(3):
        for _, st in stages:
            model(f"fig_bunch_{st}_{v}", fig_bunch_model(v, st))
            model(f"trunk_figs_{st}_{v}", trunk_figs_model(v, st))
    write_json(ASSETS / "blockstates/fig_bunch.json", {"variants": {
        f"stage={i}": [{"model": f"antcolonies:block/fig_bunch_{st}_{v}"} for v in range(3)] for i, st in stages}})
    variants = {}
    for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for i, st in stages:
            variants[f"facing={d},stage={i}"] = [dict({"model": f"antcolonies:block/trunk_figs_{st}_{v}"}, **({"y": r} if r else {}))
                                                for v in range(3)]
    write_json(ASSETS / "blockstates/trunk_figs.json", {"variants": variants})
    item_from_block("fig_bunch", "fig_bunch_ripe_0")
    item_from_block("trunk_figs", "trunk_figs_ripe_0")
    for v in range(3):  # the old one-stage models
        for old in (f"fig_bunch_{v}", f"trunk_figs_{v}"):
            f = ASSETS / f"models/block/{old}.json"
            if f.exists():
                f.unlink()
    fruit_loot("fig_bunch", "fig", (2, 4), [(1, [("green_fig", 1, 2)]), (2, [("green_fig", 1, 2)])])
    fruit_loot("trunk_figs", "fig", (1, 3), [(1, [("green_fig", 1, 1)]), (2, [("green_fig", 1, 1)])])
    fruit_loot("doum_nut_bunch", "doum_nut", (1, 3), [(1, [("green_doum_nut", 1, 2)]),
                                                      (2, [("doum_nut", 1, 1), ("green_doum_nut", 1, 1)])])

    texture("doum_nut_fruit", doum_nut_atlas())
    for name, maker in (("fallen_figs", fallen_figs_model), ("fallen_doum_nuts", fallen_doum_nuts_model)):
        variants = {}
        for n in (1, 2, 3):
            for old in (False, True):
                m = f"{name}_{n}{'_old' if old else ''}"
                model(m, maker(n, old))
                variants[f"fruits={n},old={'true' if old else 'false'}"] = {"model": f"antcolonies:block/{m}"}
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        item_from_block(name, f"{name}_3")
    fallen_loot("fallen_figs", "fig", True)
    fallen_loot("fallen_doum_nuts", "doum_nut", False)

    for name, rows in zip(("fig", "green_fig", "doum_nut", "green_doum_nut"), fruit_icons()):
        texture(f"{name}_item", rows)
        flat_item(name, f"{name}_item")
    print("wrote the floodplain fruit")


def game_files():
    """Every block of the sycamore fig (floodplain trees step 3)."""
    from tree_art import ASSETS, SHEARS_OR_SILK, drops_itself, item_from_block, loot, model, single, cross, texture, write_json
    look = GAME_LOOK
    texture("fig_bark", fig_bark(look, 0))
    texture("fig_bark_2", fig_bark(look, 3))
    texture("fig_bark_patch", fig_bark(look, 1))
    texture("fig_bark_pale", fig_bark(look, 2))
    texture("fig_bark_young", fig_young_bark(look))
    texture("fig_bark_dead", fig_dead_bark(look))
    texture("fig_wood_bare", bare_wood())
    texture("fig_log_top", fig_log_top(False))
    texture("fig_log_top_dead", fig_log_top(True))
    for tone, name in enumerate(("fig_leaves_lit", "fig_leaves", "fig_leaves_shaded")):
        texture(name, fig_leaves(tone))
    texture("fig_leaf_spray", sprig())
    texture("fig_seedling", fig_seedling())
    texture("fig_fruit", fig_fruit_atlas())

    def sides(bark):
        """The bark picture(s) for a FigBark value: mottled has two, mixed at random so the trunk does not repeat."""
        names = {"mottled": ["fig_bark", "fig_bark_2"], "patch": ["fig_bark_patch"], "pale": ["fig_bark_pale"],
                 "young": ["fig_bark_young"], "dead": ["fig_bark_dead"], "bare": ["fig_wood_bare"]}[bark]
        return [f"antcolonies:block/{n}" for n in names]

    def end(bark):
        return "antcolonies:block/" + ("fig_log_top_dead" if bark in ("dead", "bare") else "fig_log_top")

    # Log and wood: turned by placing, one model per bark (two for mottled).
    for name, all_round in (("fig_log", False), ("fig_wood", True)):
        variants = {}
        for bark in BARKS:
            ups, sideways = [], []
            for i, side in enumerate(sides(bark)):
                top = side if all_round else end(bark)
                m = f"{name}_{bark}" + (f"_{i}" if i else "")
                model(m, {"parent": "minecraft:block/cube_column", "textures": {"side": side, "end": top}})
                model(f"{m}_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"side": side, "end": top}})
                ups.append(f"antcolonies:block/{m}")
                sideways.append(f"antcolonies:block/{m}_horizontal")
            variants[f"axis=y,bark={bark}"] = [{"model": m} for m in ups]
            variants[f"axis=z,bark={bark}"] = [{"model": m, "x": 90} for m in sideways]
            variants[f"axis=x,bark={bark}"] = [{"model": m, "x": 90, "y": 90} for m in sideways]
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        item_from_block(name, f"{name}_mottled")
        drops_itself(name)

    # Branch: six thicknesses (FigBranchBlock.width), 2 steps a thickness, each bark.
    turn = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = []
    for bark in BARKS:
        tex = {"particle": sides(bark)[0], "side": sides(bark)[0], "end": end(bark)}
        for level, w in enumerate(FIG_TAPER):
            lo, hi = (16 - w) / 2, 16 - (16 - w) / 2
            steps = f"{2 * level}|{2 * level + 1}"
            core, arm = f"fig_branch_{bark}_core_{level}", f"fig_branch_{bark}_arm_{level}"
            model(core, {"textures": tex, "elements": [{"from": [lo, lo, lo], "to": [hi, hi, hi], "faces": {
                f: {"texture": "#side", "uv": [lo, lo, hi, hi]} for f in ("north", "south", "east", "west", "up", "down")}}]})
            arm_faces = {f: {"texture": "#side", "uv": [lo, 0, hi, lo]} for f in ("east", "west", "up", "down")}
            arm_faces["north"] = {"texture": "#end", "uv": [lo, lo, hi, hi], "cullface": "north"}
            model(arm, {"textures": tex, "elements": [{"from": [lo, lo, 0], "to": [hi, hi, lo], "faces": arm_faces}]})
            parts.append({"when": {"step": steps, "bark": bark}, "apply": {"model": f"antcolonies:block/{core}"}})
            for d, r in turn.items():
                parts.append({"when": {d: "true", "step": steps, "bark": bark}, "apply": dict({"model": f"antcolonies:block/{arm}"}, **r)})
    lo, hi = 1, 15
    tex = {"particle": sides("mottled")[0], "side": sides("mottled")[0], "end": end("mottled")}
    model("fig_branch_inventory", {"parent": "minecraft:block/block", "textures": tex, "elements": [{"from": [lo, lo, 0], "to": [hi, hi, 16], "faces": {
        "north": {"texture": "#end", "uv": [lo, lo, hi, hi]}, "south": {"texture": "#end", "uv": [lo, lo, hi, hi]},
        "east": {"texture": "#side", "uv": [0, lo, 16, hi]}, "west": {"texture": "#side", "uv": [0, lo, 16, hi]},
        "up": {"texture": "#side", "uv": [lo, 0, hi, 16]}, "down": {"texture": "#side", "uv": [lo, 0, hi, 16]}}}]})
    write_json(ASSETS / "blockstates/fig_branch.json", {"multipart": parts})
    item_from_block("fig_branch", "fig_branch_inventory")
    drops_itself("fig_branch")

    # Root steps: 8 or 5 pixels high, bark on every face (the viewer's slab8 and slab5).
    variants = {}
    for bark in BARKS:
        for low, h in ((False, 8), (True, 5)):
            m = f"fig_root_{bark}_{'low' if low else 'high'}"
            faces = {f: {"texture": "#bark", "uv": [0, 16 - h, 16, 16], "cullface": f} for f in ("north", "south", "east", "west")}
            faces["up"] = {"texture": "#bark", "uv": [0, 0, 16, 16]}
            faces["down"] = {"texture": "#bark", "uv": [0, 0, 16, 16], "cullface": "down"}
            model(m, {"parent": "minecraft:block/block", "textures": {"particle": sides(bark)[0], "bark": sides(bark)[0]},
                      "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": faces}]})
            variants[f"bark={bark},low={'true' if low else 'false'}"] = {"model": f"antcolonies:block/{m}"}
    write_json(ASSETS / "blockstates/fig_root.json", {"variants": variants})
    item_from_block("fig_root", "fig_root_mottled_high")
    drops_itself("fig_root")

    # Leaves: three tones (FigLeavesBlock.TONE).
    for name in ("fig_leaves_lit", "fig_leaves", "fig_leaves_shaded"):
        model(name, {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                     "textures": {"all": f"antcolonies:block/{name}"}})
    write_json(ASSETS / "blockstates/fig_leaves.json", {"variants": {
        "tone=0": {"model": "antcolonies:block/fig_leaves_lit"}, "tone=1": {"model": "antcolonies:block/fig_leaves"},
        "tone=2": {"model": "antcolonies:block/fig_leaves_shaded"}}})
    item_from_block("fig_leaves")
    loot("fig_leaves", [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": "antcolonies:fig_leaves", "conditions": [SHEARS_OR_SILK]},
        {"type": "minecraft:item", "name": "antcolonies:fig_seedling", "conditions": [
            {"condition": "minecraft:survives_explosion"}, {"condition": "minecraft:random_chance", "chance": 0.05}]},
        {"type": "minecraft:item", "name": "minecraft:stick", "conditions": [
            {"condition": "minecraft:survives_explosion"}, {"condition": "minecraft:random_chance", "chance": 0.02}]}]}]}])

    # The leaf spray under the pads: itself only to shears or silk touch, like the thorny twig.
    cross("fig_leaf_spray", "fig_leaf_spray")
    loot("fig_leaf_spray", [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "antcolonies:fig_leaf_spray"}],
                             "conditions": [SHEARS_OR_SILK]}])
    cross("fig_seedling", "fig_seedling")
    drops_itself("fig_seedling")

    # Fig bunches and figs on the bark: three looks each at every stage, in fruit_files (step 4b).
    print("wrote the sycamore fig blocks")


# ---- The doum palm's blocks (floodplain trees step 5, 2026-11-14; bark A and shape B, the user's picks) ----
# Stems are plain blocks (doum_log, doum_wood, living or dead grey); a crown, a nut bunch, the seedling and the rosette
# are each one block whose model replays the viewer's own sculpted piece (antcolonies:doum_sculpted, the client's
# DoumModel): those boxes are drawn with a plain white picture coloured box by box, and the crown's cap with the bark
# tinted as the viewer tints it (baked here). Leaf-base stubs are ordinary block models, the viewer's two boxes.

DOUM_CAP_TINT = (1.12, 1.08, 0.9)
DOUM_BOOT = ((1.5, 1.42, 1.24), (1.3, 1.25, 1.12))
DOUM_FROND = ((0x56, 0x70, 0x2c), (0x96, 0xa4, 0x40))
DOUM_NUT = ((0x7e, 0x38, 0x14), (0x96, 0x49, 0x1c), (0xb0, 0x62, 0x2a), (0x6c, 0x2e, 0x12))


def _lin(v):
    v /= 255
    return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4


def _srgb(v):
    v = max(0.0, min(1.0, v))
    return round(255 * (v * 12.92 if v <= 0.0031308 else 1.055 * v ** (1 / 2.4) - 0.055))


def tinted(rows, tint):
    """A picture coloured as the viewer colours it: each pixel times the tint in linear light."""
    return [[None if px is None else tuple(_srgb(_lin(c) * t) for c, t in zip(px, tint)) for px in row] for row in rows]


def doum_log_top(dead):
    """A doum stem's cut end: no rings (a palm has none), pale fibrous pith speckled with the cut dark strands, thickest
    toward the rim, and the bark round it (grey once dead)."""
    rng = random.Random(f"doumend{dead}")
    pith, strand = ((196, 170, 120), (104, 72, 44)) if not dead else ((172, 166, 152), (96, 90, 80))
    bark = doum_dead_bark(GAME_LOOK) if dead else doum_bark(GAME_LOOK)
    img = []
    for y in range(S):
        row = []
        for x in range(S):
            edge = max(abs(x - 7.5), abs(y - 7.5))
            if edge > 7:
                row.append(bark[y][x])
                continue
            r = math.hypot(x - 7.5, y - 7.5) / 7.5
            c = shade(pith, rng.uniform(0.94, 1.06))
            if rng.random() < 0.12 + 0.28 * r:  # the strands lie thicker toward the outside, as in a real palm stem
                c = mix(c, strand, rng.uniform(0.5, 0.85))
            row.append(c)
        img.append(row)
    return img


def doum_frond_texture():
    """A solid frond green for the pieces' break specks: fan segments in light and dark."""
    rng = random.Random("doumfrond")
    return [[shade(mix(*DOUM_FROND, 0.5 + 0.5 * math.sin((x + y * 0.3) * 1.7)), rng.uniform(0.9, 1.08)) for x in range(S)] for y in range(S)]


def doum_nut_texture():
    rng = random.Random("doumnut")
    return [[shade(DOUM_NUT[(x // 3 + y // 4) % 4], rng.uniform(0.85, 1.12)) for x in range(S)] for y in range(S)]


def _icon_fan(img, cx, cy, radius, a0, a1, rays, rng):
    """A fan leaf seen from the side: segments from (cx, cy) out to radius between angles a0 and a1 (degrees, 0 up)."""
    for i in range(rays):
        a = math.radians(a0 + (a1 - a0) * i / max(1, rays - 1))
        tone = DOUM_FROND[i % 2]
        for step in range(1, radius * 3):
            t = step / 3
            x, y = int(round(cx + math.sin(a) * t)), int(round(cy - math.cos(a) * t))
            if 0 <= x < S and 0 <= y < S:
                img[y][x] = shade(tone, rng.uniform(0.9, 1.1) * (1.05 - 0.2 * t / radius))


def doum_icons():
    """Item pictures: the crown (a fan of fronds on its cap), the rosette (fans from the ground), the seedling (one
    folded leaf from the nut) and a bunch of nuts."""
    rng = random.Random("doumicons")
    crown = blank(None)
    _icon_fan(crown, 7.5, 9, 7, -80, 80, 13, rng)
    for x in range(5, 11):
        for y in range(9, 12):
            crown[y][x] = shade(doum_bark(GAME_LOOK)[y][x], 1.1)
    for y in range(2, 9):  # the spear leaf
        crown[y][7] = (0xc4, 0xc6, 0x74)
    rosette = blank(None)
    _icon_fan(rosette, 4, 14, 8, -40, 50, 7, rng)
    _icon_fan(rosette, 11, 14, 8, -50, 40, 7, rng)
    _icon_fan(rosette, 7.5, 14, 10, -25, 25, 5, rng)
    seedling = blank(None)
    for y in range(6, 15):
        seedling[y][7] = (0x7a, 0x8a, 0x3a)
    _icon_fan(seedling, 7.5, 7, 5, -35, 35, 5, rng)
    for x in range(6, 10):
        seedling[15][x] = DOUM_NUT[0]
    bunch = blank(None)
    for y in range(0, 6):
        bunch[y][7] = (0x9a, 0x94, 0x50)
    for i, (x, y) in enumerate(((5, 6), (9, 6), (7, 8), (4, 10), (10, 10), (7, 12), (6, 4), (8, 4), (5, 13), (9, 13))):
        tone = DOUM_NUT[i % 4]
        for dx in range(-1, 2):
            for dy in range(-1, 3):
                if abs(dx) + abs(dy - 0.5) < 2.4 and 0 <= x + dx < S and 0 <= y + dy < S:
                    bunch[y + dy][x + dx] = shade(tone, 1.25 if dx < 0 and dy < 1 else 1.0)
    return crown, rosette, seedling, bunch


def doum_game_files():
    """Every block of the doum palm (floodplain trees step 5)."""
    from tree_art import ASSETS, SHEARS_OR_SILK, drops_itself, flat_item, item_from_block, loot, model, texture, write_json
    bark, dead = doum_bark(GAME_LOOK), doum_dead_bark(GAME_LOOK)
    texture("doum_bark", bark)
    texture("doum_bark_dead", dead)
    texture("doum_log_top", doum_log_top(False))
    texture("doum_log_top_dead", doum_log_top(True))
    texture("doum_plain", [[(255, 255, 255)] * S for _ in range(S)])
    texture("doum_cap", tinted(bark, DOUM_CAP_TINT))
    texture("doum_cap_dead", tinted(dead, DOUM_CAP_TINT))
    texture("doum_frond", doum_frond_texture())
    texture("doum_nut", doum_nut_texture())
    texture("doum_leaf_base", tinted(bark, (0.84, 0.84, 0.84)))
    texture("doum_leaf_base_dead", tinted(dead, (0.84, 0.84, 0.84)))
    for i, t in enumerate(DOUM_BOOT):  # 0 the paler boot
        texture(f"doum_leaf_boot_{i}", tinted(bark, t))
        texture(f"doum_leaf_boot_dead_{i}", tinted(dead, t))
    for name, rows in zip(("doum_crown_item", "doum_rosette_item", "doum_seedling_item", "doum_nut_bunch_item"), doum_icons()):
        texture(name, rows)

    # Stems: log (cut ends top and bottom) and wood (bark all round), turned by placing, living or dead grey.
    for name, all_round in (("doum_log", False), ("doum_wood", True)):
        variants = {}
        for is_dead in (False, True):
            side = "antcolonies:block/" + ("doum_bark_dead" if is_dead else "doum_bark")
            end = side if all_round else "antcolonies:block/" + ("doum_log_top_dead" if is_dead else "doum_log_top")
            m = name + ("_dead" if is_dead else "")
            model(m, {"parent": "minecraft:block/cube_column", "textures": {"side": side, "end": end}})
            model(f"{m}_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"side": side, "end": end}})
            flag = "true" if is_dead else "false"
            variants[f"axis=y,dead={flag}"] = {"model": f"antcolonies:block/{m}"}
            variants[f"axis=z,dead={flag}"] = {"model": f"antcolonies:block/{m}_horizontal", "x": 90}
            variants[f"axis=x,dead={flag}"] = {"model": f"antcolonies:block/{m}_horizontal", "x": 90, "y": 90}
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        item_from_block(name)
        drops_itself(name)

    # The sculpted pieces: one model each, drawn by the doum_sculpted loader.
    for name, particle in (("doum_crown", "doum_frond"), ("doum_nut_bunch", "doum_nut"), ("doum_seedling", "doum_frond"),
                           ("doum_rosette", "doum_frond"), ("doum_fronds", "doum_frond")):
        model(name, {"loader": "antcolonies:doum_sculpted", "textures": {
            "plain": "antcolonies:block/doum_plain", "cap": "antcolonies:block/doum_cap", "cap_dead": "antcolonies:block/doum_cap_dead",
            "particle": f"antcolonies:block/{particle}"}})
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"antcolonies:block/{name}"}}})
        flat_item(name, "doum_frond" if name == "doum_fronds" else f"{name}_item")
    # A crown gives itself to shears or silk touch, now and then a seedling (my pick, as a leaf block gives a sapling).
    loot("doum_crown", [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": "antcolonies:doum_crown", "conditions": [SHEARS_OR_SILK]},
        {"type": "minecraft:item", "name": "antcolonies:doum_seedling", "conditions": [
            {"condition": "minecraft:survives_explosion"}, {"condition": "minecraft:random_chance", "chance": 0.05}]}]}]}])
    # The nut bunch's drops by stage are in fruit_files (step 6b).
    drops_itself("doum_seedling")
    drops_itself("doum_rosette")
    loot("doum_fronds", [])  # old fronds crumble (my pick)

    # Root steps at the clump's foot: 8 or 5 pixels high, bark on every face, living or dead grey (step 6).
    variants = {}
    for is_dead in (False, True):
        tex = "antcolonies:block/" + ("doum_bark_dead" if is_dead else "doum_bark")
        for low, h in ((False, 8), (True, 5)):
            m = f"doum_root_{'dead_' if is_dead else ''}{'low' if low else 'high'}"
            fc = {f: {"texture": "#bark", "uv": [0, 16 - h, 16, 16], "cullface": f} for f in ("north", "south", "east", "west")}
            fc["up"] = {"texture": "#bark", "uv": [0, 0, 16, 16]}
            fc["down"] = {"texture": "#bark", "uv": [0, 0, 16, 16], "cullface": "down"}
            model(m, {"parent": "minecraft:block/block", "textures": {"particle": tex, "bark": tex},
                      "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": fc}]})
            variants[f"dead={'true' if is_dead else 'false'},low={'true' if low else 'false'}"] = {"model": f"antcolonies:block/{m}"}
    write_json(ASSETS / "blockstates/doum_root.json", {"variants": variants})
    item_from_block("doum_root", "doum_root_high")
    drops_itself("doum_root")

    # Leaf-base stubs: the viewer's two boxes against the stem, facing out east, turned by the blockstate.
    def faces(tex, w, h, d):
        dims = {"east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d), "south": (w, h), "north": (w, h)}
        return {f: {"texture": tex, "uv": [0, 16 - min(16, v), min(16, u), 16]} for f, (u, v) in dims.items()}

    variants = {}
    for low in (False, True):
        for wide in (True, False):
            width = 14 if wide else 10
            for pale, is_dead in ((p, d) for d in (False, True) for p in (0, 1)):
                for shift in range(5):
                    sh = shift - 2
                    boot = 0 if pale else 1  # DOUM_BOOT[0] is the paler boot
                    dd = "dead_" if is_dead else ""
                    m = f"doum_leaf_base_{dd}{'low' if low else 'high'}_{width}_{pale}_{shift}"
                    y0 = 0 if low else 6
                    z0, z1 = 8 + sh - width / 2, 8 + sh + width / 2
                    b0, b1 = 8 + sh - (width - 4) / 2, 8 + sh + (width - 4) / 2
                    by = 4.5 if low else 10.5
                    model(m, {"parent": "minecraft:block/block", "textures": {
                        "particle": f"antcolonies:block/doum_leaf_base{'_dead' if is_dead else ''}",
                        "sheath": f"antcolonies:block/doum_leaf_base{'_dead' if is_dead else ''}",
                        "boot": f"antcolonies:block/doum_leaf_boot_{dd}{boot}"}, "elements": [
                        {"from": [0, y0, z0], "to": [3, y0 + 6, z1], "faces": faces("#sheath", 3, 6, width)},
                        {"from": [0, by, b0], "to": [5, by + 5, b1], "faces": faces("#boot", 5, 5, width - 4)}]})
                    for facing, turn in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
                        v = {"model": f"antcolonies:block/{m}"}
                        if turn:
                            v["y"] = turn
                        variants[f"dead={str(is_dead).lower()},facing={facing},low={str(low).lower()},"
                                 f"pale={'true' if pale else 'false'},shift={shift},wide={str(wide).lower()}"] = v
    write_json(ASSETS / "blockstates/doum_leaf_base.json", {"variants": variants})
    item_from_block("doum_leaf_base", "doum_leaf_base_high_14_0_2")
    loot("doum_leaf_base", [])
    print("wrote the doum palm blocks")


if __name__ == "__main__":
    game_files()
    doum_game_files()
    fruit_files()
