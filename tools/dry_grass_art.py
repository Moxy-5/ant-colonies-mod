"""Draws Dry Grass and Tall Dry Grass and writes their block files: textures for the three stages (green, drying,
dry), block models, blockstates, item models, loot tables, and the savanna world generation (features and the biome
modifier). Also draws a preview, references/termites/preview/dry_grass.png.

Real savanna grasses (red oat grass, Themeda triandra, and thatching grasses, Hyparrhenia) grow as tufts of thin
blades, green in the rains. As the dry season comes they cure from the tips down (the "drying" stage: tan tips over a
green base) until the whole tuft is pale straw, which is what Macrotermes harvest. Tall plants carry seed heads: loose
drooping spikelets with long dark awns, rusty brown on red oat grass.

Needs no extra libraries (writes the PNGs itself). Run from the project folder:  python tools/dry_grass_art.py
"""
import json
import random
import struct
import zlib
from pathlib import Path
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)

ROOT = Path(__file__).resolve().parent.parent
ASSETS = mod_paths.assets("savanna")
DATA = mod_paths.data("savanna")
PREVIEW = ROOT / "references/termites/preview"

STAGES = ["green", "drying", "dry"]
# (base of the blade, tip of the blade) for each stage. Drying: still green low down, tan at the tips.
BLADE = {
    "green": ((70, 104, 38), (134, 160, 70)),
    "drying": ((92, 112, 46), (196, 170, 104)),
    "dry": ((160, 128, 76), (226, 204, 148)),
}
SEED = {"green": (118, 112, 60), "drying": (150, 104, 62), "dry": (148, 92, 56)}   # spikelets (rusty when ripe)
AWN = {"green": (88, 84, 50), "drying": (92, 66, 44), "dry": (84, 60, 40)}


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(color, amount):
    return tuple(max(0, min(255, round(c * amount))) for c in color)


def blank(width, height):
    return [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]


def put(img, x, y, color):
    if 0 <= y < len(img) and 0 <= x < len(img[0]):
        img[y][x] = tuple(color) + (255,)


def blade(img, rng, stage, x0, length, lean, droop=0.0, light=1.0):
    """One blade from the bottom row up: leans sideways as it rises, the tip bending over (droop). Every other blade is
    a little darker, so the blades of a tuft stand apart."""
    height = len(img)
    base, tip = BLADE[stage]
    light *= rng.uniform(0.94, 1.04)
    x = x0 + 0.5
    for i in range(length):
        t = i / max(1, length - 1)
        x += lean * (0.4 + t) + droop * t * t
        color = shade(mix(base, tip, t ** 0.8), light)
        if stage == "drying" and t < 0.45:
            color = shade(mix(BLADE["green"][0], BLADE["green"][1], t * 1.6), light)
        put(img, int(x), height - 1 - i, color)


def tuft(img, rng, stage, blades, top, spread=5.5):
    """A tuft: blades fanning out from the middle of the bottom, up to `top` pixels tall."""
    width = len(img[0])
    middle = width / 2 - 0.5
    for n in range(blades):
        # Spread evenly across the fan (left to right), with a little jitter, so each blade shows.
        side = (n + rng.uniform(0.2, 0.8)) / blades * 2 - 1
        x0 = round(middle + side * 2.0)
        lean = side * spread / top * rng.uniform(0.4, 0.7)
        length = rng.randint(int(top * 0.6), top)
        blade(img, rng, stage, x0, length, lean, side * rng.uniform(0.0, 0.5), 0.8 if n % 2 else 1.05)


def seed_heads(img, rng, stage, count, low, high):
    """Seed heads near the top: a thin stalk ending in a few drooping spikelets with dark awns."""
    width = len(img[0])
    height = len(img)
    for _ in range(count):
        x = rng.randint(3, width - 4)
        top = height - 1 - rng.randint(low, high)
        for y in range(top, height - 1 - low + 4):
            put(img, x, y, shade(BLADE[stage][1], 0.9))
        side = rng.choice([-1, 1])
        for k in range(3):
            sx, sy = x + side * (1 + k // 2), top + 1 + k
            put(img, sx, sy, shade(SEED[stage], rng.uniform(0.9, 1.1)))
            put(img, sx + side, sy + 1, AWN[stage])
        put(img, x, top - 1, AWN[stage])


def short_texture(stage):
    rng = random.Random(11)
    img = blank(16, 16)
    tuft(img, rng, stage, 9, 13, spread=6.0)
    return img


def tall_textures(stage):
    """The tall plant drawn as one 16x32 picture, then cut into its top and bottom halves."""
    rng = random.Random(23)
    img = blank(16, 32)
    tuft(img, rng, stage, 11, 27, spread=9.0)
    seed_heads(img, rng, stage, 4, 20, 29)
    return img[:16], img[16:]


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


def cross_model(texture):
    return {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": texture}}


def shears_loot(block, drop, count):
    """Only shears get the grass (it keeps how dry it was); anything else breaks it to nothing."""
    entry = {
        "type": "minecraft:item",
        "name": drop,
        "conditions": [{"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}],
        "functions": [
            {"function": "minecraft:copy_state", "block": block, "properties": ["growth"]},
            {"function": "minecraft:set_count", "count": count},
        ],
    }
    if block.endswith("tall_dry_grass"):
        entry["conditions"].append(
            {"condition": "minecraft:block_state_property", "block": block, "properties": {"half": "lower"}})
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}]}


def patch(block, weights, tries):
    """A patch of grass on grass blocks and dirt, each tuft as green, drying or dry as the weights say."""
    return {
        "type": "minecraft:random_patch",
        "config": {
            "tries": tries,
            "xz_spread": 7,
            "y_spread": 3,
            "feature": {
                "feature": {
                    "type": "minecraft:simple_block",
                    "config": {"to_place": {
                        "type": "minecraft:weighted_state_provider",
                        "entries": [{"weight": w, "data": {"Name": block, "Properties": {"growth": s}}}
                                    for s, w in weights.items()],
                    }},
                },
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:all_of", "predicates": [
                        {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                        {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": "minecraft:dirt"},
                    ]}}],
            },
        },
    }


def placed(feature, count):
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
        {"type": "minecraft:biome"},
    ]}


def main():
    previews = []
    for stage in STAGES:
        short = short_texture(stage)
        top, bottom = tall_textures(stage)
        write_png(ASSETS / f"textures/block/dry_grass_{stage}.png", short)
        write_png(ASSETS / f"textures/block/tall_dry_grass_top_{stage}.png", top)
        write_png(ASSETS / f"textures/block/tall_dry_grass_bottom_{stage}.png", bottom)
        write_json(ASSETS / f"models/block/dry_grass_{stage}.json", cross_model(f"antcolonies:block/dry_grass_{stage}"))
        for half in ("top", "bottom"):
            write_json(ASSETS / f"models/block/tall_dry_grass_{half}_{stage}.json",
                       cross_model(f"antcolonies:block/tall_dry_grass_{half}_{stage}"))
        previews.append((short, top + bottom))

    write_json(ASSETS / "blockstates/dry_grass.json", {"variants": {
        f"growth={s}": {"model": f"antcolonies:block/dry_grass_{s}"} for s in STAGES}})
    write_json(ASSETS / "blockstates/tall_dry_grass.json", {"variants": {
        f"growth={s},half={half}": {"model": f"antcolonies:block/tall_dry_grass_{part}_{s}"}
        for s in STAGES for half, part in (("lower", "bottom"), ("upper", "top"))}})
    write_json(ASSETS / "models/item/dry_grass.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "antcolonies:block/dry_grass_dry"}})
    write_json(ASSETS / "models/item/tall_dry_grass.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "antcolonies:block/tall_dry_grass_top_dry"}})

    write_json(DATA / "loot_table/blocks/dry_grass.json", shears_loot("antcolonies:dry_grass", "antcolonies:dry_grass", 1))
    write_json(DATA / "loot_table/blocks/tall_dry_grass.json",
               shears_loot("antcolonies:tall_dry_grass", "antcolonies:dry_grass", 2))

    # Savannas: mostly dry with some green and drying tufts (my pick), short tufts much more common than tall plants.
    write_json(DATA / "worldgen/configured_feature/patch_dry_grass.json",
               patch("antcolonies:dry_grass", {"green": 2, "drying": 2, "dry": 5}, 32))
    write_json(DATA / "worldgen/configured_feature/patch_tall_dry_grass.json",
               patch("antcolonies:tall_dry_grass", {"green": 2, "drying": 2, "dry": 5}, 24))
    write_json(DATA / "worldgen/placed_feature/patch_dry_grass.json", placed("antcolonies:patch_dry_grass", 4))
    write_json(DATA / "worldgen/placed_feature/patch_tall_dry_grass.json", placed("antcolonies:patch_tall_dry_grass", 1))
    write_json(DATA / "neoforge/biome_modifier/dry_grass.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_savanna",
        "features": ["antcolonies:patch_dry_grass", "antcolonies:patch_tall_dry_grass"],
        "step": "vegetal_decoration",
    })

    # Preview: the three stages side by side (the tall plant, the short tuft beside it), 8 times bigger, on sky blue.
    scale, cell = 8, 34
    sheet = [[(170, 205, 235, 255) for _ in range(cell * 3 * scale)] for _ in range(34 * scale)]
    for n, (short, tall) in enumerate(previews):
        for img, dx, dy in ((tall, 1, 1), (short, 18, 17)):
            for y, row in enumerate(img):
                for x, px in enumerate(row):
                    if px[3]:
                        for sy in range(scale):
                            for sx in range(scale):
                                sheet[(dy + y) * scale + sy][(n * cell + dx + x) * scale + sx] = px
    write_png(PREVIEW / "dry_grass.png", sheet)
    print("wrote the dry grass textures, models, loot tables and world generation")


if __name__ == "__main__":
    main()
