"""Draws the Fallen Branch and writes its block files: bark textures for the 4 eaten stages, the end grain, block models
(base, middle and tip, each in 4 stages, thinner as it is eaten), the blockstate, item model, loot table and the
savanna world generation. Also draws a preview, references/termites/preview/fallen_branch.png.

A dead acacia branch lying in the savanna is bleached silver and weathered (the dead giant trees' look since M7.5
step 6, user 2026-10-19), with long cracks along the grain where the bark has gone. Macrotermes eat dead wood from under a crust of mud they plaster over it (soil sheeting), so as a branch is eaten
more and more of it is covered in reddish-brown mud and less wood is left.

Needs no extra libraries (writes the PNGs itself). Run from the project folder:  python tools/fallen_branch_art.py
"""
import json
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "savanna/src/main/resources/assets/antcolonies"
DATA = ROOT / "savanna/src/main/resources/data/antcolonies"
PREVIEW = ROOT / "references/termites/preview"

WOOD = (156, 150, 139)       # sun-bleached silver, as the dead giant trees' bark (tools/tree_art.py)
WOOD_LIGHT = (184, 178, 167)
CRACK = (70, 64, 58)
MUD = (138, 78, 50)          # fresh termite mud, like the fresh mound soil
MUD_DARK = (104, 58, 38)
END_RING = [(186, 168, 136), (160, 140, 108)]
STAGES = 4
PARTS = {"base": 5, "middle": 4, "tip": 3}   # thickness in pixels before it is eaten (FallenBranchBlock.Part)
MUD_COVER = [0.0, 0.25, 0.5, 0.75]           # how much of the bark is under mud at each stage


def clamp(c):
    return max(0, min(255, round(c)))


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def bark(stage):
    """16x16 bark, the grain running left to right, with mud blobs covering MUD_COVER[stage] of it."""
    rng = random.Random(5)
    rows = []
    cracks = {y: [(rng.randrange(16), rng.randint(3, 9)) for _ in range(2)] for y in range(16) if rng.random() < 0.35}
    for y in range(16):
        row = []
        for x in range(16):
            t = rng.random()
            color = WOOD_LIGHT if t < 0.18 else WOOD
            for start, length in cracks.get(y, []):
                if start <= x < start + length:
                    color = CRACK
            noise = rng.randint(-8, 8)
            row.append(tuple(clamp(c + noise) for c in color) + (255,))
        rows.append(row)
    # Mud: blobs grow over the same spots each stage, so the crust spreads rather than jumps about.
    mrng = random.Random(9)
    blobs = [(mrng.uniform(0, 16), mrng.uniform(0, 16), mrng.uniform(1.8, 3.6)) for _ in range(24)]
    cover = MUD_COVER[stage]
    if cover > 0:
        for y in range(16):
            for x in range(16):
                # Wrap round so the texture tiles along the branch.
                d = min(((min(abs(x - bx), 16 - abs(x - bx))) ** 2 + (y - by) ** 2) ** 0.5 / r for bx, by, r in blobs)
                if d < cover * 1.25:
                    shade = MUD_DARK if d > cover * 1.0 or mrng.random() < 0.15 else MUD
                    rows[y][x] = tuple(clamp(c + mrng.randint(-6, 6)) for c in shade) + (255,)
    return rows


def end_grain():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            row.append(END_RING[int(d) % 2] + (255,))
        rows.append(row)
    return rows


def face(texture, uv):
    return {"texture": texture, "uv": uv}


def model(part, stage):
    t = max(2, PARTS[part] - stage)
    lo, hi = 8 - t / 2, 8 + t / 2
    side_uv = [0, 8 - t / 2, 16, 8 + t / 2]
    elements = [{
        "from": [0, 0, lo], "to": [16, t, hi],
        "faces": {
            "north": face("#bark", side_uv), "south": face("#bark", side_uv),
            "up": face("#bark", [0, lo, 16, hi]), "down": face("#bark", [0, lo, 16, hi], ),
            "east": face("#end", [lo, 16 - t, hi, 16]), "west": face("#end", [lo, 16 - t, hi, 16]),
        },
    }]
    if part == "tip" and stage < 2:
        # A dry twig sticking up out of the tip.
        elements.append({
            "from": [9, t - 1, 7.5], "to": [10, t + 6, 8.5],
            "rotation": {"origin": [9.5, t - 1, 8], "axis": "z", "angle": -45},
            "faces": {d: face("#bark", [0, 4, 1, 11]) for d in ("north", "south", "east", "west", "up", "down")},
        })
    if part == "base" and stage < 2:
        # The stub of a side branch broken off near the thick end.
        elements.append({
            "from": [5, 1, hi], "to": [7, 3, hi + 3],
            "faces": {d: face("#bark", [0, 6, 2, 9]) for d in ("north", "south", "east", "west", "up", "down")},
        })
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": f"antcolonies:block/fallen_branch_bark_{stage}",
                         "bark": f"antcolonies:block/fallen_branch_bark_{stage}", "end": "antcolonies:block/fallen_branch_end"},
            "elements": elements}


def main():
    sheets = []
    for stage in range(STAGES):
        tex = bark(stage)
        sheets.append(tex)
        write_png(ASSETS / f"textures/block/fallen_branch_bark_{stage}.png", tex)
        for part in PARTS:
            write_json(ASSETS / f"models/block/fallen_branch_{part}_{stage}.json", model(part, stage))
    write_png(ASSETS / "textures/block/fallen_branch_end.png", end_grain())
    write_json(ASSETS / "blockstates/fallen_branch.json", {"variants": {
        f"axis={axis},eaten={stage},part={part}": dict({"model": f"antcolonies:block/fallen_branch_{part}_{stage}"},
                                                      **({"y": 90} if axis == "z" else {}))
        for axis in ("x", "z") for stage in range(STAGES) for part in PARTS}})
    write_json(ASSETS / "models/item/fallen_branch.json", {"parent": "antcolonies:block/fallen_branch_middle_0"})
    write_json(DATA / "loot_table/blocks/fallen_branch.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1.0, "bonus_rolls": 0.0,
        "entries": [{"type": "minecraft:item", "name": "minecraft:stick"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # (Its savanna world generation was switched off in M7.5 step 6c: the giant trees bring their own dead wood.)

    # Preview: the 4 bark stages side by side, 8 times bigger.
    scale = 8
    sheet = [[(170, 205, 235, 255) for _ in range((STAGES * 17 + 1) * scale)] for _ in range(18 * scale)]
    for n, tex in enumerate(sheets):
        for y, row in enumerate(tex):
            for x, px in enumerate(row):
                for sy in range(scale):
                    for sx in range(scale):
                        sheet[(1 + y) * scale + sy][(1 + n * 17 + x) * scale + sx] = px
    write_png(PREVIEW / "fallen_branch.png", sheet)
    print("wrote the fallen branch textures, models, loot table (no world generation since M7.5 step 6c)")


if __name__ == "__main__":
    main()
