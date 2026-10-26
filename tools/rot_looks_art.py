"""Heart rot looks, drafts for the bracket viewer only (dead wood for the raids, part 4, 2026-10-26): nothing here is
written into the game. python tools/model_viewer.py bracket shows them beside today's bracket (tools/bracket_art.py).

From real Ganoderma (the "artist's conk", G. applanatum group) and Phellinus brackets on African acacias:
- a bracket starts as a soft white knob pushing out of the bark (stage a);
- it widens into a thin shelf with a bright white growing rim; the pores underneath are white (stage b);
- it is perennial: each year a new layer is added under the old one, so a mature bracket is a thick woody hoof with
  ridges round its top and side like yearly bands, brown on top, a cream rim, cream pores underneath (stage c);
- an old one stops growing: its crust darkens and cracks, and its own spores (Ganoderma's are cocoa-brown, shed in
  billions) settle on its top as a brown powder (stage d);
- they often grow in tiers, shelf over shelf (the stacked version);
- the spores also stain the bark under a bracket cocoa-brown (spore-dusted bark);
- the rot gets in through a wound: a sunken scar of bare, cracked, white-rotted wood inside a raised roll of new bark
  (the callus) and, on a living acacia, beads and dribbles of amber gum (acacias bleed gum, the gum arabic, at every
  wound). My picks: a dead bleached tree has only old dark gum lumps left (the dead do not bleed), a burnt one none.
"""
import math
import random

import tree_art
from tree_art import S, shade, mix, noise, speckle

COCOA = (122, 76, 46)  # Ganoderma's spore dust

# ---- Brackets: each stage a block model facing north (the trunk at z 16) with its own textures ----

WHITE = (240, 236, 224)
CREAM = (220, 206, 172)
PORE = (212, 198, 164)


def box(a, b, top="#top", under="#under", edge="#edge"):
    """One box of a bracket. The side faces take the bottom h rows of the edge texture, so row 15 is always the
    underside's edge and the rows above it the rim and the crust."""
    (x1, y1, z1), (x2, y2, z2) = a, b
    h = y2 - y1
    return {"from": list(a), "to": list(b), "faces": {
        "up": {"uv": [x1, z1, x2, z2], "texture": top},
        "down": {"uv": [x1, z1, x2, z2], "texture": under},
        "north": {"uv": [x1, 16 - h, x2, 16], "texture": edge},
        "east": {"uv": [z1, 16 - h, z2, 16], "texture": edge},
        "west": {"uv": [z1, 16 - h, z2, 16], "texture": edge}}}


def rings(seed, at_wall, at_rim, rim, rim_width, band=1.6, crack=0.0, dust=0.0, dark=None):
    """A bracket's top: rings round the middle of the wall edge (the wall at the bottom of the picture), the colour
    going from at_wall to at_rim outward, a rim of rim_width pixels; cracks and spore dust for old ones."""
    rng = random.Random(seed)
    dn = noise(f"{seed}dust", 4)
    img = tree_art.blank()
    for y in range(S):
        for x in range(S):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 16.5)
            if r > 14.6 - rim_width:
                c = rim
            else:
                c = mix(at_wall, at_rim, min(1, r / 14))
                if int(r / band) % 2:
                    c = shade(c, 0.84)  # the furrow between two years' ridges
                if dark is not None and rng.random() < 0.18:
                    c = mix(c, dark, 0.6)
                if crack and rng.random() < crack:
                    c = shade(c, 0.45)
                if dust:
                    p = dn(x, y)
                    if p > 0.42:  # the dust lies in drifts, the dark crust bare between them
                        c = mix(c, COCOA, min(0.85, dust * (0.6 + p)))
            img[y][x] = shade(c, 0.95 + rng.random() * 0.08)
    if crack:  # a few long cracks across the rings
        for _ in range(3):
            x, y = rng.randrange(3, 13), rng.randrange(4, 15)
            for _ in range(5):
                if 0 <= x < S and 0 <= y < S:
                    img[y][x] = (34, 24, 18)
                x += rng.choice((-1, 0, 1))
                y -= 1
    return img


def pores(seed, base, bruise=0.0):
    rng = random.Random(seed)
    img = tree_art.blank()
    for y in range(S):
        for x in range(S):
            c = base
            if (x + 2 * y) % 3 == 0 and rng.random() < 0.6:
                c = shade(base, 0.86)  # a pore
            if bruise and rng.random() < bruise:
                c = mix(c, (120, 84, 54), 0.6)  # pores bruise brown where touched or old
            img[y][x] = shade(c, 0.95 + rng.random() * 0.07)
    return img


def side(seed, crust, rim_rows, rim, under_edge, bands=0, crack=0.0, dust=0.0):
    """Side faces from the bottom up: the underside's edge (row 15), the growing rim, then the crust, ridged by year."""
    rng = random.Random(seed)
    img = tree_art.blank()
    for y in range(S):
        up = 15 - y
        for x in range(S):
            if up == 0:
                c = under_edge
            elif up <= rim_rows:
                c = rim
            else:
                c = crust
                if bands and (up - rim_rows) % 2 == 0:
                    c = mix(crust, (150, 112, 74), 0.45)  # one year's ridge, lighter
                if crack and rng.random() < crack:
                    c = shade(c, 0.5)
                if dust and up >= 4 and rng.random() < dust:
                    c = mix(c, COCOA, 0.7)
            img[y][x] = shade(c, 0.94 + rng.random() * 0.09)
    return img


def knob_texture():
    """Stage a: soft white felt, a faint yellow tint, shadowed where it meets the bark."""
    rng = random.Random("knob")
    img = tree_art.blank()
    for y in range(S):
        for x in range(S):
            c = mix(WHITE, (236, 226, 190), rng.random() * 0.4)
            if y > 12:
                c = shade(c, 0.9)
            img[y][x] = shade(c, 0.96 + rng.random() * 0.06)
    return img


def stages():
    """Every new bracket look: id -> (name, elements, textures as pixel rows)."""
    young = {"top": rings("ytop", (178, 140, 96), (226, 214, 190), WHITE, 4, band=2.4),
             "under": pores("yunder", (238, 232, 216)),
             "edge": side("yedge", (176, 138, 96), 2, WHITE, (232, 226, 210))}
    mature = {"top": rings("mtop", (88, 56, 34), (126, 86, 54), CREAM, 1.3, band=1.5),
              "under": pores("munder", PORE),
              "edge": side("medge", (96, 62, 38), 1, CREAM, shade(PORE, 0.85), bands=1)}
    old = {"top": rings("otop", (46, 36, 30), (60, 46, 38), (140, 112, 84), 1.0, band=1.4, crack=0.06, dust=0.5,
                        dark=(40, 32, 28)),
           "under": pores("ounder", (186, 168, 132), bruise=0.25),
           "edge": side("oedge", (44, 34, 28), 1, (134, 108, 82), (150, 130, 100), bands=1, crack=0.08, dust=0.1)}
    knob = {"knob": knob_texture()}
    k = dict(top="#knob", under="#knob", edge="#knob")
    out = {
        "knob": ("a, knob", [box((6, 5, 13), (10, 9, 16), **k), box((7, 6, 12), (9, 8, 13), **k),
                             box((5, 6, 14), (11, 8, 16), **k)], knob),
        "young": ("b, young shelf", [box((4, 6, 12), (12, 8, 16)), box((6, 6, 10), (10, 8, 12))], young),
        "mature": ("c, mature hoof", [box((2, 3, 11), (14, 9, 16)), box((3, 3, 9), (13, 8, 11)),
                                      box((5, 3, 7), (11, 7, 9))], mature),
        "old": ("d, old", [box((1, 2, 10), (15, 9, 16)), box((2, 2, 8), (14, 8, 10)), box((4, 2, 6), (12, 7, 8)),
                           box((6, 2, 5), (10, 6, 6))], old),
    }
    # Tiers on one block: two mature shelves, the newest on top still with its white rim (my pick).
    tiers = {"top": mature["top"], "under": mature["under"], "edge": mature["edge"],
             "ytop": young["top"], "yunder": young["under"], "yedge": young["edge"]}
    y = dict(top="#ytop", under="#yunder", edge="#yedge")
    out["stacked"] = ("stacked tiers", [
        box((2, 1, 10), (14, 4, 16)), box((4, 1, 8), (12, 4, 10)),
        box((2, 6, 11), (11, 9, 16)), box((4, 6, 9), (9, 9, 11)),
        box((6, 11, 12), (14, 13, 16), **y), box((8, 11, 10), (13, 13, 12), **y)], tiers)
    return out


# ---- Bark looks: each replaces a log block's side texture ----

def base_barks():
    return {"umbrella": tree_art.picked_bark("umbrella"), "camel": tree_art.picked_bark("camel"),
            "bleached": tree_art.dead_bark_silver(0), "charred": tree_art.charred_bark(0)}


def lum(c):
    return (c[0] * 3 + c[1] * 6 + c[2]) / 10


def dusted(kind, img):
    """Spore-dusted: cocoa-brown dust heavy at the top of the block (just under the bracket), fading downward in
    patches, settling thickest in the bark's dark furrows."""
    n = noise(f"dust{kind}", 4)
    rng = random.Random(f"dust{kind}")
    out = []
    for y, row in enumerate(img):
        r = []
        for x, c in enumerate(row):
            s = (1 - y / 16) ** 1.4 * (0.55 + 0.6 * n(x, y))
            if lum(c) < 70:
                s *= 1.25  # furrows hold the dust
            s = min(0.9, s * (1.15 if kind == "charred" else 1.0))
            r.append(mix(c, shade(COCOA, rng.uniform(0.9, 1.08)), s))
        out.append(r)
    return out


GUM = {"fresh": ((176, 94, 20), (250, 200, 100), (112, 52, 12)), "old": ((92, 44, 18), (138, 80, 38), (52, 26, 12))}


def wound(kind, img):
    """A wound where the rot got in: a sunken oval scar of bare wood, cracked and white-rotted, inside a raised callus
    roll of new bark; amber gum beads on the rim and dribbles down the bark on a living tree."""
    rng = random.Random(f"wound{kind}")
    img = [list(r) for r in img]
    cx, cy, rx, ry = 7.5, 7.0, 3.7, 5.8
    if kind == "charred":
        wood, crack, roll = (22, 18, 16), (104, 98, 92), (70, 64, 58)  # charcoal; ash in the cracks
    elif kind == "bleached":
        wood, crack, roll = (176, 168, 152), (70, 62, 54), (214, 208, 196)
    else:
        wood, crack, roll = (138, 118, 92), (52, 40, 32), mix(img[0][0], (170, 150, 120), 0.6)
    splits = {rng.randrange(5, 11) for _ in range(3)}  # the bare wood splits along its grain
    for y in range(S):
        for x in range(S):
            d = math.hypot((x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry)
            c = img[y][x]
            if d < 1:
                c = shade(wood, rng.uniform(0.93, 1.05))
                if kind != "charred" and d < 0.7 and rng.random() < 0.22:
                    c = mix(c, (228, 220, 198), 0.8)  # white rot: the rotted wood goes pale and soft
                if x in splits and 0.15 < (y + 0.5) / S < 0.85 and d < 0.85:
                    c = crack
                if d > 0.7 and y + 0.5 < cy:
                    c = shade(c, 0.5)  # the sunken scar's upper inner edge in shadow
                elif d > 0.7:
                    c = shade(c, 1.12)  # its lower inner edge catching the light
            elif d < 1.4:
                c = shade(mix(c, roll, 0.7), 1.08 if y + 0.5 > cy else 0.96)  # the callus roll of new bark
            elif d < 1.55:
                c = shade(c, 0.62)  # the shadow the roll throws on the old bark
            img[y][x] = c
    # Zone lines: the thin black lines a white rot draws between its patches.
    if kind != "charred":
        for _ in range(2):
            x, y = rng.randrange(6, 10), rng.randrange(3, 6)
            for _ in range(6):
                if math.hypot((x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry) < 0.8:
                    img[y][x] = (28, 22, 18)
                x += rng.choice((-1, 0, 1)); y += 1
    if kind == "charred":
        return speckle(img, rng, 0.02)
    gum = GUM["fresh" if kind in ("umbrella", "camel") else "old"]
    beads = [(4, 3), (11, 4), (3, 9), (12, 10), (6, 13), (9, 1)]
    for i, (bx, by) in enumerate(beads[: 6 if gum is GUM["fresh"] else 3]):
        drop(img, bx, by, gum, big=i % 2 == 0)
    if gum is GUM["fresh"]:  # dribbles running down from two beads, one over the lower callus
        for bx, by, length in ((4, 4, 9), (12, 11, 4), (8, 13, 3)):
            for y in range(by, min(S, by + length)):
                img[y][bx] = shade(gum[0], 0.9 + 0.15 * ((y - by) % 2))
            end = min(S - 1, by + length)
            drop(img, bx, end, gum, big=False)
    return img


def drop(img, x, y, gum, big):
    body, light, rim = gum
    pts = [(0, 0), (1, 0), (0, 1), (1, 1)] if big else [(0, 0)]
    for dx, dy in pts:
        if 0 <= x + dx < S and 0 <= y + dy < S:
            img[y + dy][x + dx] = body
    if 0 <= y + 1 + (1 if big else 0) < S:
        img[y + 1 + (1 if big else 0)][x] = rim  # the darker underside of the bead
    img[y][x] = light  # the shine on top


def barks():
    """kind -> {"plain", "dusted", "wound"}: pixel rows."""
    return {k: {"plain": b, "dusted": dusted(k, b), "wound": wound(k, b)} for k, b in base_barks().items()}
