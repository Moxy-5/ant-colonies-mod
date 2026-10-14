"""Draws Dead Wood (a fallen acacia's trunk and stump) and writes its block files: side and end textures for the 4
eaten stages, block models, the blockstate (lying along x or z, or upright as a stump), item model, loot table and the
savanna world generation of fallen trees. Also draws a preview, references/termites/preview/dead_wood.png.

A long-dead acacia goes silver-grey in the sun, split by deep cracks along the grain, with the last strips of dark
bark peeling off it. Macrotermes hollow a log out from inside under a crust of mud plastered over the wood, so as it is
eaten the outside gets more and more mud and the cut ends show a growing hollow core.

Needs no extra libraries (writes the PNGs itself). Run from the project folder:  python tools/dead_wood_art.py
"""
import json
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/antcolonies"
DATA = ROOT / "src/main/resources/data/antcolonies"
PREVIEW = ROOT / "references/termites/preview"

SILVER = (152, 146, 134)
SILVER_LIGHT = (184, 178, 164)
CRACK = (72, 66, 58)
BARK = (92, 76, 62)          # the last dark bark
BARK_EDGE = (124, 104, 84)   # its lifted, peeling edge
MUD = (138, 78, 50)          # fresh termite mud (as on the fallen branch)
MUD_DARK = (104, 58, 38)
RINGS = [(196, 182, 152), (170, 154, 124)]
HOLLOW = (44, 30, 22)
STAGES = 4
MUD_COVER = [0.0, 0.25, 0.5, 0.75]
HOLLOW_RADIUS = [0.0, 2.0, 3.6, 5.2]


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


def side(stage):
    """16x16 side, the grain running top to bottom (the model turns it to lie along the log)."""
    rng = random.Random(3)
    rows = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            color = SILVER_LIGHT if rng.random() < 0.2 else SILVER
            rows[y][x] = tuple(clamp(c + rng.randint(-7, 7)) for c in color)
    # Long cracks along the grain.
    for _ in range(5):
        x, y, length = rng.randrange(16), rng.randrange(16), rng.randint(5, 12)
        for i in range(length):
            rows[(y + i) % 16][x] = CRACK
    # Two strips of old bark peeling off, with a lighter lifted edge.
    for x0 in (rng.randrange(0, 6), rng.randrange(9, 14)):
        top, height = rng.randrange(16), rng.randint(5, 9)
        for i in range(height):
            for dx in range(2):
                rows[(top + i) % 16][x0 + dx] = tuple(clamp(c + rng.randint(-5, 5)) for c in BARK)
            rows[(top + i) % 16][x0 + 2] = BARK_EDGE
    # Termite mud spreading over the same spots each stage (tiles both ways).
    mrng = random.Random(9)
    blobs = [(mrng.uniform(0, 16), mrng.uniform(0, 16), mrng.uniform(1.8, 3.6)) for _ in range(24)]
    cover = MUD_COVER[stage]
    if cover > 0:
        for y in range(16):
            for x in range(16):
                d = min(((min(abs(x - bx), 16 - abs(x - bx))) ** 2 + (min(abs(y - by), 16 - abs(y - by))) ** 2) ** 0.5 / r
                        for bx, by, r in blobs)
                if d < cover * 1.25:
                    shade = MUD_DARK if d > cover or mrng.random() < 0.15 else MUD
                    rows[y][x] = tuple(clamp(c + mrng.randint(-6, 6)) for c in shade)
    return [[px + (255,) for px in row] for row in rows]


def end(stage):
    """The cut end: grey bark rim, pale growth rings with a radial crack, a hollow core growing as it is eaten."""
    rng = random.Random(4)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if x in (0, 15) or y in (0, 15):
                color = SILVER
            elif d < HOLLOW_RADIUS[stage]:
                color = HOLLOW if d < HOLLOW_RADIUS[stage] - 1 else MUD_DARK
            elif x == 7 and y < 7 or y == 9 and x > 9:
                color = CRACK
            else:
                color = RINGS[int(d * 0.9) % 2]
            row.append(tuple(clamp(c + rng.randint(-5, 5)) for c in color) + (255,))
        rows.append(row)
    return rows


def main():
    previews = []
    for stage in range(STAGES):
        s, e = side(stage), end(stage)
        previews += [s, e]
        write_png(ASSETS / f"textures/block/dead_wood_{stage}.png", s)
        write_png(ASSETS / f"textures/block/dead_wood_top_{stage}.png", e)
        write_json(ASSETS / f"models/block/dead_wood_{stage}.json", {
            "parent": "minecraft:block/cube_column",
            "textures": {"side": f"antcolonies:block/dead_wood_{stage}", "end": f"antcolonies:block/dead_wood_top_{stage}"}})
    turn = {"x": {"x": 90, "y": 90}, "y": {}, "z": {"x": 90}}   # like a vanilla log
    write_json(ASSETS / "blockstates/dead_wood.json", {"variants": {
        f"axis={axis},eaten={stage}": dict({"model": f"antcolonies:block/dead_wood_{stage}"}, **turn[axis])
        for axis in ("x", "y", "z") for stage in range(STAGES)}})
    write_json(ASSETS / "models/item/dead_wood.json", {"parent": "antcolonies:block/dead_wood_0"})
    write_json(DATA / "loot_table/blocks/dead_wood.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1.0, "bonus_rolls": 0.0,
        "entries": [{"type": "minecraft:item", "name": "antcolonies:dead_wood"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(DATA / "worldgen/configured_feature/fallen_tree.json", {"type": "antcolonies:fallen_tree", "config": {}})
    # Rare: about one fallen tree every 8 chunks of savanna (my pick).
    write_json(DATA / "worldgen/placed_feature/fallen_tree.json", {"feature": "antcolonies:fallen_tree", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 8},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
        {"type": "minecraft:biome"},
    ]})
    write_json(DATA / "neoforge/biome_modifier/fallen_tree.json", {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_savanna",
        "features": "antcolonies:fallen_tree", "step": "vegetal_decoration"})

    # Preview: side and end of each stage, 8 times bigger.
    scale = 8
    sheet = [[(170, 205, 235, 255) for _ in range((len(previews) * 17 + 1) * scale)] for _ in range(18 * scale)]
    for n, tex in enumerate(previews):
        for y, row in enumerate(tex):
            for x, px in enumerate(row):
                for sy in range(scale):
                    for sx in range(scale):
                        sheet[(1 + y) * scale + sy][(1 + n * 17 + x) * scale + sx] = px
    write_png(PREVIEW / "dead_wood.png", sheet)
    print("wrote the dead wood textures, models, loot table and world generation")


if __name__ == "__main__":
    main()
