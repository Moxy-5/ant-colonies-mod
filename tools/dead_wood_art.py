"""Draws Dead Wood (a fallen acacia's trunk and stump) and writes its block files: side textures (3 versions, picked at
random per block, for each of the 4 eaten stages), end textures, block models, the blockstate (lying along x or z, or
upright as a stump), item model, loot table and the savanna world generation of fallen trees. Also draws a preview,
references/termites/preview/dead_wood.png.

The look is the user's pick (2026-10-14, "D" in tools/model_viewer.py): a dead version of Minecraft's savanna log, dark
grey-brown bark in long strips along the trunk, so a fallen tree fits in beside the acacias round it (the user liked
the bleached silver look best for realism, but it looked nothing like Minecraft's acacias). Macrotermes hollow a log
out from inside under a crust of mud plastered over the wood, so as it is eaten the outside gets more and more mud and
the cut ends show a growing hollow core (the user: keep those stages).

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

S = 16
TONES = [(62, 56, 48), (84, 77, 67), (104, 95, 83), (122, 112, 98)]   # bark strips, darkest to lightest
RIM = (70, 62, 52)                                                    # the bark round the cut end
RINGS = [(132, 116, 96), (112, 98, 82)]
MUD = (138, 78, 50)          # fresh termite mud (as on the fallen branch)
MUD_DARK = (104, 58, 38)
HOLLOW = (44, 30, 22)
STAGES = 4
VARIANTS = 3
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


def bark(variant):
    """16x16 bark, the strips running top to bottom (the model turns it to lie along the log): each column a run of
    strips of different shades, so neighbouring blocks never repeat when each picks its own version."""
    rng = random.Random(f"D{variant}")
    img = [[None] * S for _ in range(S)]
    for x in range(S):
        tone = rng.randrange(1, 4)
        y = 0
        while y < S:
            run = rng.randint(3, 9)
            for i in range(run):
                if y + i < S:
                    img[y + i][x] = tuple(clamp(c * rng.uniform(0.95, 1.05)) for c in TONES[tone])
            y += run
            tone = max(0, min(3, tone + rng.choice([-2, -1, 1, 2])))
    return img


def side(stage, variant):
    """The bark with termite mud spreading over the same spots each stage (tiles both ways)."""
    rows = bark(variant)
    mrng = random.Random(9)
    blobs = [(mrng.uniform(0, 16), mrng.uniform(0, 16), mrng.uniform(1.8, 3.6)) for _ in range(24)]
    cover = MUD_COVER[stage]
    if cover > 0:
        for y in range(S):
            for x in range(S):
                d = min(((min(abs(x - bx), 16 - abs(x - bx))) ** 2 + (min(abs(y - by), 16 - abs(y - by))) ** 2) ** 0.5 / r
                        for bx, by, r in blobs)
                if d < cover * 1.25:
                    shade = MUD_DARK if d > cover or mrng.random() < 0.15 else MUD
                    rows[y][x] = tuple(clamp(c + mrng.randint(-6, 6)) for c in shade)
    return [[px + (255,) for px in row] for row in rows]


def end(stage):
    """The cut end: a dark bark rim, brown growth rings, a radial crack, a hollow core growing as it is eaten."""
    rng = random.Random(4)
    rows = []
    for y in range(S):
        row = []
        for x in range(S):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if ring > 7:
                color = RIM
            elif d < HOLLOW_RADIUS[stage]:
                color = HOLLOW if d < HOLLOW_RADIUS[stage] - 1 else MUD_DARK
            elif x == 7 and y < 7 or y == 9 and x > 9:
                color = (72, 62, 52)
            else:
                color = RINGS[int(ring) % 2]
            row.append(tuple(clamp(c + rng.randint(-5, 5)) for c in color) + (255,))
        rows.append(row)
    return rows


def main():
    # Old single-version files (before the user's pick) are replaced by the versions below.
    for stage in range(STAGES):
        for old in (ASSETS / f"textures/block/dead_wood_{stage}.png", ASSETS / f"models/block/dead_wood_{stage}.json"):
            old.unlink(missing_ok=True)
    previews = []
    for stage in range(STAGES):
        e = end(stage)
        write_png(ASSETS / f"textures/block/dead_wood_top_{stage}.png", e)
        for v in range(VARIANTS):
            s = side(stage, v)
            if v == 0:
                previews += [s, e]
            write_png(ASSETS / f"textures/block/dead_wood_{stage}_{v}.png", s)
            write_json(ASSETS / f"models/block/dead_wood_{stage}_{v}.json", {
                "parent": "minecraft:block/cube_column",
                "textures": {"side": f"antcolonies:block/dead_wood_{stage}_{v}", "end": f"antcolonies:block/dead_wood_top_{stage}"}})
    turn = {"x": {"x": 90, "y": 90}, "y": {}, "z": {"x": 90}}   # like a vanilla log
    # A list of models: the game picks one per block (by its position), so a long trunk does not repeat.
    write_json(ASSETS / "blockstates/dead_wood.json", {"variants": {
        f"axis={axis},eaten={stage}": [dict({"model": f"antcolonies:block/dead_wood_{stage}_{v}"}, **turn[axis]) for v in range(VARIANTS)]
        for axis in ("x", "y", "z") for stage in range(STAGES)}})
    write_json(ASSETS / "models/item/dead_wood.json", {"parent": "antcolonies:block/dead_wood_0_0"})
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
