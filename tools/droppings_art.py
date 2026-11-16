"""Crocodile droppings and river scraps (M9 river life; the user, 2026-11-15: "I know its poop but I dont want it to be
too ugly"), looks to pick from in the model viewer (python tools/model_viewer.py droppings,
tools/droppings_viewer.template.html). Only drawn here; nothing goes into the mod until the user picks.

Crocodile droppings (generalized, fun first; the real detail: a crocodile's stomach dissolves bones, so its dung dries
chalk white with calcium; crocodiles do it in the water as well as on land, the game shows only the land ones, where
they bask). A small thing lying on the grass or silt of a basking bank, drawn bigger than life so it is seen (about
0.35 blocks long, my pick). Three looks, each at three ages:
  A  chalky log       one short tube in two or three pieces, slightly bent
  B  pellet heap      a little heap of round lumps
  C  crumbly patch    a low patch of chalky crumbs, the plainest of the three
Ages: 0 fresh (pale grey with darker streaks), 1 dried (chalk white), 2 crumbling (white bits, half sunk, about to go).

Scraps on the riverbed (one block for flesh scraps and fish droppings: they feed the same eaters and look alike that
small; the crab picks at it, it fades in about a day). A thin film drawn over the bed like the algae mat. Three looks:
  A  white flecks         pale bits of flesh only
  B  flecks and detritus  the flecks on a soft brown-olive dusting (the fish droppings)
  C  flecks and scales    the flecks with silver scales catching the light
Amounts: 0 fresh (a fish just eaten above), 1 settled, 2 nearly gone.

Boxes are in model pixels (16 a block), x and z round the block's middle, y up from the ground.
"""
import math
import random

S = 16
AGES = ["Fresh", "Dried", "Crumbling"]
AMOUNTS = ["Fresh", "Settled", "Nearly gone"]

# droppings colours by age: (main, dark, light)
DUNG = [((176, 174, 162), (104, 104, 88), (206, 204, 192)),     # fresh: pale grey, olive-grey streaks
        ((226, 222, 208), (184, 178, 160), (242, 240, 230)),    # dried: chalk white
        ((214, 210, 196), (168, 162, 146), (234, 232, 222))]    # crumbling: dulled white


def clamp(c):
    return max(0, min(255, round(c)))


def _box(x, y, z, w, h, d, col, turn=0.0):
    return {"x": x, "y": y, "z": z, "w": w, "h": h, "d": d, "c": "#%02x%02x%02x" % tuple(clamp(v) for v in col),
            "turn": turn}


def _vary(rng, col, k=0.06):
    f = rng.uniform(1 - k, 1 + k)
    return tuple(v * f for v in col)


def chalky_log(age, seed=1):
    """A: a short tube broken in pieces; its pieces bend a little; a dark core shows at the broken ends when fresh."""
    rng = random.Random(seed)
    main, dark, light = DUNG[age]
    boxes = []
    pieces = [(-2.6, 2.4, 0.0), (0.2, 2.2, 0.35), (2.6, 1.6, 0.7)]         # (middle x, length, turn)
    sink = [0, 0, 0.6][age]
    for i, (mx, ln, turn) in enumerate(pieces):
        if age == 2 and i == 1:
            continue                                                       # crumbling: the middle piece gone to bits
        th = 1.7 if i < 2 else 1.4
        z = 0.4 * math.sin(i * 1.3)
        col = _vary(rng, main)
        boxes.append(_box(mx, th / 2 - sink, z, ln, th, th, col, turn))
        boxes.append(_box(mx, th - 0.1 - sink, z, ln * 0.8, 0.25, th * 0.6, _vary(rng, light), turn))  # chalky top
        if age == 0:
            boxes.append(_box(mx + ln / 2 - 0.05, th / 2 - sink, z, 0.12, th * 0.6, th * 0.6, dark, turn))  # broken end
    if age == 2:
        for _ in range(6):
            boxes.append(_box(rng.uniform(-1.4, 1.4), 0.2, rng.uniform(-1, 1), 0.5, 0.4, 0.5, _vary(rng, light, 0.1),
                              rng.uniform(0, 1.5)))
    return boxes


def pellet_heap(age, seed=2):
    """B: round lumps, three under two, the top ones smaller."""
    rng = random.Random(seed)
    main, dark, light = DUNG[age]
    spots = [(-1.3, 0, -0.6, 1.8), (1.0, 0, -0.9, 1.6), (0.0, 0, 1.0, 1.7), (-0.3, 1.3, -0.2, 1.4), (1.9, 0, 1.2, 1.2)]
    if age == 2:
        spots = [(-1.3, -0.5, -0.6, 1.5), (1.0, -0.5, -0.9, 1.2), (0.3, -0.4, 1.0, 1.0)]
    boxes = []
    for x, y, z, s in spots:
        col = _vary(rng, main)
        boxes.append(_box(x, y + s / 2, z, s, s * 0.85, s, col, rng.uniform(0, 1.5)))
        boxes.append(_box(x, y + s * 0.85, z, s * 0.7, 0.2, s * 0.7, _vary(rng, light), rng.uniform(0, 1.5)))  # rounded top
        if age == 0 and rng.random() < 0.6:
            boxes.append(_box(x + s * 0.3, y + s * 0.4, z + s * 0.5, s * 0.3, s * 0.3, 0.1, dark))  # a dark streak
    return boxes


def crumbly_patch(age, seed=3):
    """C: a low uneven patch of crumbs, wider than tall, with a couple of bigger chunks."""
    rng = random.Random(seed)
    main, dark, light = DUNG[age]
    boxes = []
    n = [16, 16, 10][age]
    for i in range(n):
        a, r = rng.uniform(0, 6.283), 3.2 * math.sqrt(rng.random())
        s = rng.uniform(0.6, 1.3) * (1.6 if i < 2 else 1)
        h = s * (0.7 if age < 2 else 0.4)
        col = _vary(rng, light if rng.random() < 0.3 else main, 0.08)
        boxes.append(_box(r * math.cos(a), h / 2 - (0.2 if age == 2 else 0), r * math.sin(a) * 0.7, s, h, s, col,
                          rng.uniform(0, 1.5)))
    if age == 0:
        for _ in range(3):
            boxes.append(_box(rng.uniform(-2, 2), 0.35, rng.uniform(-1.5, 1.5), 0.8, 0.3, 0.4, dark, rng.uniform(0, 1.5)))
    return boxes


DROPPINGS = {"A": ("Chalky log", chalky_log), "B": ("Pellet heap", pellet_heap), "C": ("Crumbly patch", crumbly_patch)}


def all_droppings():
    return {k: {"name": name, "ages": [f(a) for a in range(3)]} for k, (name, f) in DROPPINGS.items()}


# ---- scraps on the bed ----

FLESH = [(236, 226, 214), (226, 204, 196), (244, 238, 230), (214, 196, 184)]   # cream, pale pink, white, beige
DETRITUS = [(96, 86, 54), (78, 70, 44), (112, 100, 64)]
SCALE = [(214, 222, 226), (244, 248, 250), (176, 188, 194)]
FLECKS = [22, 12, 5]           # flecks of flesh on a block at each amount (my picks)
DUST = [0.45, 0.30, 0.12]      # share of the block the brown dusting covers (look B)
SCALES = [7, 4, 1]             # scales (look C)


def _noise(seed, waves=6):
    rng = random.Random(seed)
    parts = [(rng.randint(1, 3), rng.randint(-3, 3), rng.uniform(0, 6.283), rng.uniform(0.5, 1)) for _ in range(waves)]
    return [[sum(a * math.sin(6.283 * (fx * x + fy * y) / S + p) for fx, fy, p, a in parts) for x in range(S)]
            for y in range(S)]


def _empty():
    return [[(0, 0, 0, 0) for _ in range(S)] for _ in range(S)]


def _dust(rows, amount, seed):
    field = _noise(seed, 7)
    flat = sorted((v for row in field for v in row), reverse=True)
    cut = flat[max(0, round(DUST[amount] * len(flat)) - 1)]
    rng = random.Random(seed + 1)
    for y in range(S):
        for x in range(S):
            if field[y][x] >= cut and rng.random() < 0.7:           # soft and broken, never a solid sheet
                c = rng.choice(DETRITUS)
                rows[y][x] = c + (150 if rng.random() < 0.5 else 190,)


def _flecks(rows, amount, seed):
    rng = random.Random(seed)
    for i in range(FLECKS[amount]):
        x, y = rng.randrange(S), rng.randrange(S)
        c = rng.choice(FLESH)
        rows[y][x] = c + (240,)
        if i < FLECKS[amount] // 4:                                 # a few bits two pixels long
            dx, dy = rng.choice(((1, 0), (0, 1)))
            rows[(y + dy) % S][(x + dx) % S] = tuple(clamp(v * 0.9) for v in c) + (240,)


def _scales(rows, amount, seed):
    rng = random.Random(seed)
    for _ in range(SCALES[amount]):
        x, y = rng.randrange(S), rng.randrange(S)
        rows[y][x] = SCALE[1] + (250,)
        rows[y][(x + 1) % S] = SCALE[0] + (230,)
        if rng.random() < 0.5:
            rows[(y + 1) % S][x] = SCALE[2] + (220,)


def scraps(look, amount):
    rows = _empty()
    if look == "B":
        _dust(rows, amount, 40 + amount)
    _flecks(rows, amount, 10 + amount)
    if look == "C":
        _scales(rows, amount, 70 + amount)
    return rows


SCRAPS = {"A": "White flecks", "B": "Flecks and detritus", "C": "Flecks and scales"}


def all_scraps():
    return {k: {"name": name, "amounts": [scraps(k, a) for a in range(3)]} for k, name in SCRAPS.items()}


# ---- the game's files (the user's picks, 2026-11-15: droppings C, scraps B) ----

GAME_DROPPINGS, GAME_SCRAPS = "C", "B"
ROOT_ASSETS = "savanna/src/main/resources/assets/antcolonies"
TINTS = (0.93, 1.0, 1.07)


def _palette(age):
    """Nine 4 x 4 speckled cells of a 16 x 16 texture: main, light and dark, each in three tints."""
    main, dark, light = DUNG[age]
    return [tuple(v * k for v in c) for c in (main, light, dark) for k in TINTS]


def _palette_rows(age):
    pal = _palette(age)
    rng = random.Random(500 + age)
    rows = [[(0, 0, 0, 0)] * S for _ in range(S)]
    for i, c in enumerate(pal):
        cx, cy = i % 4, i // 4
        for y in range(4):
            for x in range(4):
                k = rng.uniform(0.92, 1.06)
                rows[cy * 4 + y][cx * 4 + x] = tuple(clamp(v * k) for v in c) + (255,)
    return [[p for p in r] for r in rows]


def _nearest(pal, hexcol):
    c = tuple(int(hexcol[i:i + 2], 16) for i in (1, 3, 5))
    return min(range(len(pal)), key=lambda i: sum((a - b) ** 2 for a, b in zip(pal[i], c)))


def _droppings_model(age):
    pal = _palette(age)
    tex = f"antcolonies:block/croc_droppings_{age}"
    elements = []
    for b in DROPPINGS[GAME_DROPPINGS][1](age):
        lo = [8 + b["x"] - b["w"] / 2, max(0.0, b["y"] - b["h"] / 2), 8 + b["z"] - b["d"] / 2]
        hi = [8 + b["x"] + b["w"] / 2, max(0.1, b["y"] + b["h"] / 2), 8 + b["z"] + b["d"] / 2]
        lo = [round(max(0.0, min(16.0, v)), 2) for v in lo]
        hi = [round(max(0.0, min(16.0, v)), 2) for v in hi]
        cell = _nearest(pal, b["c"])
        u, v = (cell % 4) * 4, (cell // 4) * 4
        face = {"uv": [u, v, u + 4, v + 4], "texture": "#t"}
        deg = math.degrees(b["turn"]) % 90
        deg = deg - 90 if deg > 45 else deg
        angle = min((-45, -22.5, 0, 22.5, 45), key=lambda a: abs(a - deg))
        el = {"from": lo, "to": hi, "faces": {f: dict(face) for f in ("north", "south", "east", "west", "up", "down")}}
        if angle:
            el["rotation"] = {"origin": [round((lo[0] + hi[0]) / 2, 2), lo[1], round((lo[2] + hi[2]) / 2, 2)],
                              "axis": "y", "angle": angle}
        elements.append(el)
    return {"parent": "block/block", "ambientocclusion": False, "textures": {"particle": tex, "t": tex},
            "elements": elements}


def _scraps_model(amount):
    tex = f"antcolonies:block/scraps_{amount}"
    return {"parent": "block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": {"particle": tex, "mat": tex},
            "elements": [{"from": [0, 0, 0], "to": [16, 0.2, 16], "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#mat"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#mat", "cullface": "down"}}}]}


def main():
    """Writes the picked looks into the savanna mod: textures, block models, block states and item models."""
    import json
    import os
    import river_blocks_art

    def dump(path, data):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    tex, models, states, items = (os.path.join(ROOT_ASSETS, p) for p in ("textures/block", "models/block", "blockstates", "models/item"))
    turns = (0, 90, 180, 270)
    for age in range(3):
        river_blocks_art.write_png(os.path.join(tex, f"croc_droppings_{age}.png"), S, S, [p for r in _palette_rows(age) for p in r])
        dump(os.path.join(models, f"croc_droppings_{age}.json"), _droppings_model(age))
    dump(os.path.join(states, "croc_droppings.json"), {"variants": {
        f"age={a}": [dict({"model": f"antcolonies:block/croc_droppings_{a}"}, **({"y": y} if y else {})) for y in turns] for a in range(3)}})
    dump(os.path.join(items, "croc_droppings.json"), {"parent": "antcolonies:block/croc_droppings_1"})
    for amount in range(3):
        rows = scraps(GAME_SCRAPS, amount)
        river_blocks_art.write_png(os.path.join(tex, f"scraps_{amount}.png"), S, S, [p for r in rows for p in r])
        dump(os.path.join(models, f"scraps_{amount}.json"), _scraps_model(amount))
    dump(os.path.join(states, "scraps.json"), {"variants": {
        f"amount={a}": [dict({"model": f"antcolonies:block/scraps_{a}"}, **({"y": y} if y else {})) for y in turns] for a in range(3)}})
    dump(os.path.join(items, "scraps.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "antcolonies:block/scraps_0"}})
    print("wrote the droppings and scraps textures, models and block states")


if __name__ == "__main__":
    main()
