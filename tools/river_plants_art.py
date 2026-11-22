"""Textures for the river plants: reed beds and hippo grass (docs/plans/papyrus-and-hippo-grass.md, 2026-11-22; shown in
the model viewer: `python tools/model_viewer.py riverplants`, template tools/river_plants_viewer.template.html). Plants
are fun first, inspired by real life (CLAUDE.md). The user swapped papyrus for reeds the same day ("I thought they were
like reeds or tall grass that fish could hide in").

Common reed (Phragmites): leafy stalks like very tall grass, long blades angling off them, a soft feathery plume on top
that leans to one side; thick reed beds in shallow water where small fish, tadpoles and young crocodiles hide.
  stem   the blocks below the top: two stalks with blades off them (drawn as crossed planes, like tall grass)
  top    the top block: the stalks' upper blades and the plume
         A  fresh plume   purple-brown, as new plumes are
         B  old plume     pale silvery tan, as they turn by the dry season
Hippo grass (Vossia cuspidata): stems floating out over the water from the bank, broad blades along them; a life cycle
(the user's idea): young shoots, grown, flowering, dying back (straw brown, breaking up; the roots at the bank live and
it grows back out).
  mat    a top-view tile lying on the water's surface, gaps showing the water through it (tiles seamlessly)
  clump  the rooted tuft at the bank's edge, crossed planes, blades arching up and out
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


def line(rows, x0, y0, x1, y1, colour, rng, wrap=False):
    n = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 1.5))
    for i in range(n + 1):
        t = i / n
        put(rows, int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), shade(colour(t), rng.uniform(0.92, 1.07)), wrap)


# --- reeds ---------------------------------------------------------------------------------------------------------
STALK = (104, 138, 70)
STALK_DARK = (74, 102, 50)
BLADE = (112, 152, 74)
BLADE_LIT = (150, 184, 98)
PLUME = {"A": ((112, 84, 74), (146, 116, 100), (82, 60, 56)),        # fresh: dusky purple-brown
         "B": ((196, 184, 152), (222, 212, 184), (160, 146, 114))}    # old: pale silvery tan
STALKS = (5, 10)                                                      # two stalks a block (my pick)


def _reed_stalks(rows, top):
    for x in STALKS:
        for y in range(top, S):
            put(rows, x, y, STALK if y % 5 else STALK_DARK)          # a darker node every 5 pixels


def _blades(rows, rng, ys):
    """Long blades angling up and out from the stalks, their tips bending down."""
    for i, y in enumerate(ys):
        x = STALKS[i % 2]
        side = 1 if (i // 2) % 2 == 0 else -1
        reach = rng.uniform(4, 6)
        line(rows, x, y, x + side * reach, y - reach * 0.7, lambda t: BLADE_LIT if t > 0.6 else BLADE, rng)
        put(rows, int(round(x + side * (reach + 1))), int(round(y - reach * 0.7 + 1)), BLADE)


def reed_stem():
    rows = blank()
    _reed_stalks(rows, 0)
    _blades(rows, random.Random(1), (14, 11, 8, 5, 2))
    return rows


def reed_top(look):
    rows = blank()
    rng = random.Random(2)
    _reed_stalks(rows, 7)
    _blades(rows, rng, (15, 12, 10))
    base, lit, dark = PLUME[look]
    for x0 in STALKS:                     # each plume a feathery spray leaning one way, wider toward its tip
        for k in range(11):
            t = k / 10
            cx, cy = x0 + 2.2 * t * t, 7 - 6.5 * t
            half = 0.6 + 1.6 * math.sin(math.pi * min(1, t * 1.15))
            for d in range(-round(half), round(half) + 1):
                if rng.random() < 0.85:
                    put(rows, int(round(cx + d)), int(round(cy)), shade(lit if d < 0 else dark if d > 0 else base, rng.uniform(0.9, 1.08)))
    return rows


# --- hippo grass ---------------------------------------------------------------------------------------------------
# a stage: (base, lit, dark) colours, stems across the tile, blade every n pixels, blade length, share of pixels lost
STAGES = {
    "young": {"name": "Young shoots", "col": ((150, 198, 80), (186, 220, 112), (108, 150, 56)), "stems": 2, "every": 3, "reach": 2, "broken": 0.0},
    "grown": {"name": "Grown", "col": ((118, 176, 62), (164, 206, 92), (80, 128, 46)), "stems": 4, "every": 2, "reach": 3, "broken": 0.0},
    "flowering": {"name": "Flowering", "col": ((118, 176, 62), (164, 206, 92), (80, 128, 46)), "stems": 4, "every": 2, "reach": 3, "broken": 0.0},
    "dying": {"name": "Dying back", "col": ((170, 146, 92), (200, 180, 122), (124, 100, 62)), "stems": 3, "every": 2, "reach": 3, "broken": 0.35},
}
FLOWER = (214, 204, 150)


def hippo_grass_mat(stage):
    """Stems lying across the water, blades off them both ways; gaps so the water shows."""
    st = STAGES[stage]
    base, lit, dark = st["col"]
    rows = blank()
    rng = random.Random(30)
    gap = S // st["stems"]
    for k in range(st["stems"]):          # stems floating out over the water, a little wavy, tiling across
        y0 = k * gap + rng.randint(0, 1)
        ph = rng.uniform(0, 6.28)
        for x in range(S):
            y = y0 + round(0.8 * math.sin(6.283 * x / S + ph))
            put(rows, x, y, shade(dark, rng.uniform(0.9, 1.05)), wrap=True)
            if x % st["every"] == k % st["every"]:   # blades off one side then the other
                side = 1 if (x // st["every"]) % 2 else -1
                for s in range(1, st["reach"] + 1):
                    put(rows, x + s, y + side * s, shade(lit if s == 1 else base, rng.uniform(0.9, 1.08)), wrap=True)
    if stage == "flowering":
        for _ in range(5):
            x, y = rng.randrange(S), rng.randrange(S)
            put(rows, x, y, FLOWER)
            put(rows, x + 1, y, shade(FLOWER, 0.9), wrap=True)
    if stage == "dying":                  # broken up, a few green bits left
        for y in range(S):
            for x in range(S):
                if rows[y][x][3] and rng.random() < st["broken"]:
                    rows[y][x] = CLEAR
                elif rows[y][x][3] and rng.random() < 0.06:
                    rows[y][x] = (110, 150, 60, 255)
    return rows


def hippo_grass_clump(stage):
    """A rooted tuft: broad blades arching up and out from the bottom middle (short when young, flopped when dying)."""
    st = STAGES[stage]
    base, lit, dark = st["col"]
    rows = blank()
    rng = random.Random(40)
    tall = {"young": 0.55, "grown": 1.0, "flowering": 1.0, "dying": 0.7}[stage]
    for k in range(9 if stage != "young" else 6):
        a = math.pi * (0.18 + 0.64 * k / 8) + rng.uniform(-0.06, 0.06)
        reach = rng.uniform(9, 14) * tall
        bend = rng.uniform(1.5, 3.5) * (2.2 if stage == "dying" else 1)
        steps = int(reach * 1.8)
        for s in range(steps):
            t = s / steps
            x = 7.5 + math.cos(a) * reach * t * 0.7 + (bend * t * t if math.cos(a) > 0 else -bend * t * t)
            y = 15 - math.sin(a) * reach * t + bend * t * t * 1.2
            c = lit if s % 5 == 0 else dark if t < 0.2 else base
            put(rows, int(round(x)), int(round(y)), shade(c, rng.uniform(0.9, 1.06)))
            put(rows, int(round(x)) + 1, int(round(y)), shade(base, 0.9))
    if stage == "flowering":
        for _ in range(3):
            x, y = 4 + rng.randrange(8), rng.randrange(4)
            for d in range(3):
                put(rows, x, y + d, FLOWER)
    if stage == "dying":                  # the roots' new green at the foot
        for x in range(6, 10):
            put(rows, x, 15, (120, 170, 64))
            put(rows, x + (1 if x > 7 else -1), 14, (140, 190, 80))
    return rows


def all_looks():
    return {
        "reeds": {"stem": reed_stem(), "tops": {k: reed_top(k) for k in PLUME}},
        "hippo_grass": {k: {"name": v["name"], "mat": hippo_grass_mat(k), "clump": hippo_grass_clump(k)} for k, v in STAGES.items()},
    }


ASSETS = "savanna/src/main/resources/assets/antcolonies"
DATA = "savanna/src/main/resources/data/antcolonies"
STAGE_NUMBER = {"young": 0, "grown": 1, "flowering": 2, "dying": 3}
TUFT = {"young": 5, "grown": 7, "flowering": 7}          # the blades through a mat, pixels tall (none when dying back)


def _plane(angle, tex, x0=0.8, x1=15.2, h=16, axis_x=False):
    """One upright plane through the block's middle (crossed-plant style), both faces, unshaded."""
    face = {"uv": [0, 0, 16, 16], "texture": tex}
    el = {"from": [x0, 0, 8], "to": [x1, h, 8], "shade": False, "faces": {"north": face, "south": dict(face)}}
    if axis_x:
        el = {"from": [8, 0, x0], "to": [8, h, x1], "shade": False, "faces": {"east": face, "west": dict(face)}}
    if angle:
        el["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": angle, "rescale": True}
    return el


def _model(textures, elements):
    return {"parent": "block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": textures, "elements": elements}


def _reed_model(tex):
    """Four planes: the two diagonals and the two through the middle along x and z (a thick bed, as the viewer draws)."""
    return _model({"particle": tex, "reed": tex},
                  [_plane(45, "#reed"), _plane(-45, "#reed"), _plane(0, "#reed", 0, 16), _plane(0, "#reed", 0, 16, axis_x=True)])


def _mat_model(stage, tuft):
    """The mat lying on the water (a little over it, as a lily pad), short blades through it when `tuft`."""
    mat, clump = f"antcolonies:block/hippo_grass_mat_{stage}", f"antcolonies:block/hippo_grass_clump_{stage}"
    els = [{"from": [0, 0.25, 0], "to": [16, 0.5, 16], "shade": False, "faces": {
        "up": {"uv": [0, 0, 16, 16], "texture": "#mat"}, "down": {"uv": [0, 0, 16, 16], "texture": "#mat"}}}]
    textures = {"particle": mat, "mat": mat}
    if tuft:
        textures["blades"] = clump
        els += [_plane(45, "#blades", 3, 13, TUFT[stage]), _plane(-45, "#blades", 3, 13, TUFT[stage])]
    return _model(textures, els)


def _write(path, obj):
    import json
    import os
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    """Writes the reeds' and hippo grass's textures, block models, block states, item models and loot tables into the
    savanna mod (step 2 of docs/plans/papyrus-and-hippo-grass.md; the looks the user picked 2026-11-22)."""
    import os
    import river_blocks_art
    tex = os.path.join(ASSETS, "textures/block")
    looks = all_looks()
    pngs = {"reeds_stem": looks["reeds"]["stem"], "reeds_top_fresh": looks["reeds"]["tops"]["A"],
            "reeds_top_pale": looks["reeds"]["tops"]["B"]}
    for stage, v in looks["hippo_grass"].items():
        pngs[f"hippo_grass_mat_{stage}"] = v["mat"]
        pngs[f"hippo_grass_clump_{stage}"] = v["clump"]
    for name, rows in pngs.items():
        river_blocks_art.write_png(os.path.join(tex, name + ".png"), S, S, [p for r in rows for p in r])
    models = os.path.join(ASSETS, "models/block")
    for name in ("reeds_stem", "reeds_top_fresh", "reeds_top_pale"):
        _write(os.path.join(models, name + ".json"), _reed_model("antcolonies:block/" + name))
    turns = lambda model: [dict({"model": model}, **({"y": y} if y else {})) for y in (0, 90, 180, 270)]
    reeds = {}
    for top in ("false", "true"):
        for pale in ("false", "true"):
            for wet in ("false", "true"):
                model = "antcolonies:block/" + ("reeds_stem" if top == "false" else "reeds_top_pale" if pale == "true" else "reeds_top_fresh")
                reeds[f"pale={pale},top={top},waterlogged={wet}"] = turns(model)
    _write(os.path.join(ASSETS, "blockstates/reeds.json"), {"variants": reeds})
    mats, clumps = {}, {}
    for stage, n in STAGE_NUMBER.items():
        _write(os.path.join(models, f"hippo_grass_mat_{stage}.json"), _mat_model(stage, False))
        if stage in TUFT:
            _write(os.path.join(models, f"hippo_grass_mat_{stage}_tuft.json"), _mat_model(stage, True))
        _write(os.path.join(models, f"hippo_grass_clump_{stage}.json"), {"parent": "minecraft:block/cross",
               "render_type": "minecraft:cutout", "textures": {"cross": f"antcolonies:block/hippo_grass_clump_{stage}"}})
        choices = turns(f"antcolonies:block/hippo_grass_mat_{stage}")
        if stage in TUFT:                                 # about half the mats with blades through them (the viewer's)
            choices += turns(f"antcolonies:block/hippo_grass_mat_{stage}_tuft")
        for reach in (1, 2, 3):
            mats[f"reach={reach},stage={n}"] = choices
        clumps[f"stage={n}"] = {"model": f"antcolonies:block/hippo_grass_clump_{stage}"}
    _write(os.path.join(ASSETS, "blockstates/hippo_grass.json"), {"variants": mats})
    _write(os.path.join(ASSETS, "blockstates/hippo_grass_clump.json"), {"variants": clumps})
    items = {"reeds": "reeds_top_fresh", "hippo_grass": "hippo_grass_mat_grown", "hippo_grass_clump": "hippo_grass_clump_grown"}
    for item, picture in items.items():
        _write(os.path.join(ASSETS, f"models/item/{item}.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "antcolonies:block/" + picture}})
        _write(os.path.join(DATA, f"loot_table/blocks/{item}.json"), {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": "antcolonies:" + item}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    print("wrote the reeds' and hippo grass's textures, models, block states, item models and loot tables")


if __name__ == "__main__":
    main()
