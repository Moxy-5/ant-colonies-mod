"""Block looks for the river animals' breeding (animal life cycle step 2, 2026-11-19; docs/plans/animal-life-cycle.md).

The looks the user confirmed in the life viewer (tools/life_looks.py, "they all look perfect"), as game blocks:
- the tilapia's nest pit (TilapiaNestBlock): a saucer a block across fanned out of the bed, a darker hollow inside a
  low ragged rim of the dug-up ground; in silt (wet silt hollow, silt rim) or, on a sandy bed, golden river sand;
- the river snail's eggs (SnailEggsBlock): a small cluster of pearly pink-white eggs stuck to a face, made facing up
  and turned by the block state to stick to any side.

Writes the egg texture, the block models, block states and item models into the savanna mod:
    python tools/breeding_art.py
"""
import json
import math
import os
import random

import river_blocks_art

ROOT_ASSETS = "savanna/src/main/resources/assets/antcolonies"
FACES = ("north", "south", "east", "west", "up", "down")


def box(x0, y0, z0, x1, y1, z1, tex, uv=(0, 0, 16, 16), turn=0, faces=FACES):
    el = {"from": [round(x0, 2), round(y0, 2), round(z0, 2)], "to": [round(x1, 2), round(y1, 2), round(z1, 2)],
          "faces": {f: {"uv": list(uv), "texture": tex} for f in faces}}
    if turn:
        el["rotation"] = {"origin": [round((x0 + x1) / 2, 2), 0, round((z0 + z1) / 2, 2)], "axis": "y", "angle": turn}
    return el


def nest_model(hollow, rim):
    """The pit: a round-ish hollow (three crossed sheets just over the bed) and a rim of 16 low lumps round it, each
    turned along the ring (Minecraft turns a box only in steps of 22.5 degrees, so the ring is made of those)."""
    # each sheet a little higher than the last and its texture laid by its place, so they never flicker against each other
    els = [box(x0, 0, z0, x1, y, z1, "#hollow", (x0, z0, x1, z1), faces=("up",))
           for x0, z0, x1, z1, y in ((3, 1, 13, 15, 0.06), (1, 3, 15, 13, 0.08), (2, 2, 14, 14, 0.1))]
    rng = random.Random(11)
    r = 6.6
    for i in range(16):
        a = i * math.tau / 16
        cx, cz = 8 + r * math.cos(a), 8 + r * math.sin(a)
        ln, wd = 2.9 + rng.uniform(-0.3, 0.3), 1.8 + rng.uniform(-0.2, 0.3)
        h = 1.1 + rng.uniform(-0.3, 0.4)
        deg = (math.degrees(a) + 90) % 180      # along the ring
        if deg > 90:
            deg -= 180
        if abs(deg) > 45:                       # a long box along z instead, turned the rest of the way
            ln, wd = wd, ln
            deg = deg - 90 if deg > 0 else deg + 90
        turn = round(deg / 22.5) * 22.5
        u = rng.randrange(0, 12)
        els.append(box(cx - ln / 2, 0, cz - wd / 2, cx + ln / 2, h, cz + wd / 2, "#rim", (u, u, u + 3, u + 2), turn))
    return {"parent": "block/block", "ambientocclusion": False,
            "textures": {"particle": rim, "hollow": hollow, "rim": rim}, "elements": els}


EGG = (242, 230, 236)


def egg_texture():
    """16 x 16: 4 x 4 cells of pearly egg colours, from pink-white to cream, each cell a soft lit top and a darker edge."""
    rng = random.Random(4)
    px = []
    tones = [(244, 232, 238), (238, 222, 232), (246, 238, 230), (232, 214, 226)]
    rows = [[None] * 16 for _ in range(16)]
    for cy in range(4):
        for cx in range(4):
            base = tones[(cx + cy * 3) % 4]
            for y in range(4):
                for x in range(4):
                    k = 1.04 if (x, y) in ((1, 1), (2, 1), (1, 2)) else (0.9 if x in (0, 3) or y in (0, 3) else 1.0)
                    k *= rng.uniform(0.97, 1.02)
                    rows[cy * 4 + y][cx * 4 + x] = tuple(max(0, min(255, round(v * k))) for v in base) + (255,)
    for r in rows:
        px += r
    return px


def eggs_model():
    """A cluster of 13 round little eggs, a pile two deep in the middle, on the floor of the block (facing up)."""
    rng = random.Random(8)
    els = []
    spots = [(8, 8, 1), (6.6, 7.4, 0), (9.4, 7.2, 0), (7.2, 9.4, 0), (9.2, 9.3, 0), (8, 6.1, 0), (6.1, 8.9, 0),
             (10.2, 8.4, 0), (8.3, 10.6, 0), (5.6, 6.6, 0), (10.4, 6.3, 0), (6.4, 10.5, 0), (10.6, 10.4, 0)]
    for x, z, layer in spots:
        s = rng.uniform(1.3, 1.7)
        y0 = 1.0 if layer else 0.0
        c = rng.randrange(16)
        u, v = (c % 4) * 4, (c // 4) * 4
        uv = (u, v, u + 4, v + 4)
        els.append(box(x - s / 2, y0, z - s / 2, x + s / 2, y0 + s * 0.85, z + s / 2, "#t", uv))
        els.append(box(x - s * 0.35, y0 + 0.15, z - s * 0.35, x + s * 0.35, y0 + s, z + s * 0.35, "#t", uv))
    tex = "antcolonies:block/snail_eggs"
    return {"parent": "block/block", "ambientocclusion": False, "render_type": "minecraft:cutout",
            "textures": {"particle": tex, "t": tex}, "elements": els}


def main():
    def dump(path, data):
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    tex, models, states, items = (os.path.join(ROOT_ASSETS, p) for p in ("textures/block", "models/block", "blockstates", "models/item"))
    dump(os.path.join(models, "tilapia_nest.json"), nest_model("antcolonies:block/wet_silt", "antcolonies:block/silt"))
    dump(os.path.join(models, "tilapia_nest_sandy.json"),
         nest_model("antcolonies:block/wet_golden_river_sand", "antcolonies:block/golden_river_sand"))
    variants = {}
    for sandy in ("false", "true"):
        m = "antcolonies:block/tilapia_nest" + ("_sandy" if sandy == "true" else "")
        for age in range(3):
            variants[f"age={age},sandy={sandy}"] = [dict({"model": m}, **({"y": y} if y else {})) for y in (0, 90, 180, 270)]
    dump(os.path.join(states, "tilapia_nest.json"), {"variants": variants})
    dump(os.path.join(items, "tilapia_nest.json"), {"parent": "antcolonies:block/tilapia_nest"})

    river_blocks_art.write_png(os.path.join(tex, "snail_eggs.png"), 16, 16, egg_texture())
    dump(os.path.join(models, "snail_eggs.json"), eggs_model())
    turns = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
             "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}
    dump(os.path.join(states, "snail_eggs.json"), {"variants": {
        f"facing={f},waterlogged={w}": dict({"model": "antcolonies:block/snail_eggs"}, **t)
        for f, t in turns.items() for w in ("false", "true")}})
    dump(os.path.join(items, "snail_eggs.json"), {"parent": "antcolonies:block/snail_eggs"})
    print("wrote the tilapia nest pit and the snail eggs: textures, models, block states, item models")


if __name__ == "__main__":
    main()
