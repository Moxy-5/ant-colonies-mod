"""The broken snail shell a crab leaves (M9 river life, 2026-11-16, the user: "Yes, crabs eat snails"): the river snail's
Garden spiral shell (snail_models.py, look A) cracked open and empty, lying on the bed with a few chips round it. Fun
first, my picks: the coil lies flat, rings stepping up to its pale tip, a bite broken out of the outer whorl, the pale
lip piece and three chips beside it. Three ages (CrabShellBlock.AGE): 0 fresh (the snail's browns and cream), 1 bleached,
2 crumbling (paler still, two chips and the lip gone, the coil half sunk). Writes the textures, block models, block
state and item model into the savanna mod: python tools/shell_art.py

Boxes in model pixels (16 a block): (x0, y0, z0, x1, y1, z1, part, turn), x and z from the block's corner.
"""
import json
import os
import random

import river_blocks_art

S = 16
ROOT_ASSETS = "savanna/src/main/resources/assets/antcolonies"
TINTS = (0.93, 1.0, 1.07)
# (brown, dark band, cream) at each age
COLOURS = [((204, 146, 88), (124, 80, 48), (240, 224, 196)),
           ((206, 176, 140), (150, 122, 96), (238, 230, 212)),
           ((218, 204, 182), (176, 160, 138), (240, 236, 226))]
PARTS = ("brown", "band", "cream")


def clamp(c):
    return max(0, min(255, round(c)))


def pieces(age):
    """The boxes: the coil lying flat as rings stepping up to its pale tip (each ring two crossed boxes, so it reads
    round), a bite broken out of the outer whorl showing the cream inside, the lip piece and chips round it."""
    sink = [0.0, 0.0, 0.4][age]
    out = []
    cx, cz = 8.0, 8.0
    rings = [(3.0, 0.0, 0.8, "brown"), (2.2, 0.8, 1.4, "band"), (1.5, 1.4, 1.9, "brown"), (0.8, 1.9, 2.3, "cream")]
    for i, (r, y0, y1, part) in enumerate(rings):
        y0, y1 = max(0.0, y0 - sink), y1 - sink
        bite = 1.3 if i == 0 else 0.0                    # the outer whorl broken open on its east side
        out.append((cx - r, y0, cz - r * 0.6, cx + r - bite, y1, cz + r * 0.6, part, 0))
        out.append((cx - r * 0.6, y0, cz - r, cx + r * 0.6, y1, cz + r, part, 0))
    out.append((cx + 1.5, 0, cz - 1.0, cx + 1.8, 0.9 - sink, cz + 1.0, "cream", 0))              # the broken inside
    if age < 2:
        out.append((cx - 2.8, 0, cz - 4.6, cx - 0.6, 0.5, cz - 3.8, "cream", 22.5))              # the lip piece
        out.append((cx + 3.6, 0, cz - 2.4, cx + 4.6, 0.45, cz - 1.2, "brown", 45))               # chips
        out.append((cx + 2.4, 0, cz + 3.2, cx + 3.2, 0.4, cz + 4.2, "band", -22.5))
    out.append((cx - 4.4, 0, cz + 1.2, cx - 3.4, 0.4 if age < 2 else 0.2, cz + 2.0, "brown", 22.5))
    return out


def palette_rows(age):
    """Nine 4 x 4 speckled cells: brown, band and cream, each in three tints."""
    rng = random.Random(700 + age)
    rows = [[(0, 0, 0, 0)] * S for _ in range(S)]
    for i, c in enumerate(tuple(v * k for v in col) for col in COLOURS[age] for k in TINTS):
        cx, cy = i % 4, i // 4
        for y in range(4):
            for x in range(4):
                k = rng.uniform(0.93, 1.06)
                rows[cy * 4 + y][cx * 4 + x] = tuple(clamp(v * k) for v in c) + (255,)
    return rows


def model(age):
    tex = f"antcolonies:block/crab_shell_{age}"
    rng = random.Random(age)
    elements = []
    for x0, y0, z0, x1, y1, z1, part, turn in pieces(age):
        cell = PARTS.index(part) * 3 + rng.randrange(3)
        u, v = (cell % 4) * 4, (cell // 4) * 4
        face = {"uv": [u, v, u + 4, v + 4], "texture": "#t"}
        el = {"from": [round(x0, 2), round(y0, 2), round(z0, 2)], "to": [round(x1, 2), round(max(y1, y0 + 0.1), 2), round(z1, 2)],
              "faces": {f: dict(face) for f in ("north", "south", "east", "west", "up", "down")}}
        if turn:
            el["rotation"] = {"origin": [round((x0 + x1) / 2, 2), 0, round((z0 + z1) / 2, 2)], "axis": "y", "angle": turn}
        elements.append(el)
    return {"parent": "block/block", "ambientocclusion": False, "textures": {"particle": tex, "t": tex},
            "elements": elements}


def main():
    def dump(path, data):
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    tex, models, states, items = (os.path.join(ROOT_ASSETS, p) for p in ("textures/block", "models/block", "blockstates", "models/item"))
    for age in range(3):
        river_blocks_art.write_png(os.path.join(tex, f"crab_shell_{age}.png"), S, S, [p for r in palette_rows(age) for p in r])
        dump(os.path.join(models, f"crab_shell_{age}.json"), model(age))
    turns = (0, 90, 180, 270)
    dump(os.path.join(states, "crab_shell.json"), {"variants": {
        f"age={a}": [dict({"model": f"antcolonies:block/crab_shell_{a}"}, **({"y": y} if y else {})) for y in turns] for a in range(3)}})
    dump(os.path.join(items, "crab_shell.json"), {"parent": "antcolonies:block/crab_shell_0"})
    print("wrote the broken snail shell's textures, models and block state")


if __name__ == "__main__":
    main()
