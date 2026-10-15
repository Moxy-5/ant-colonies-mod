"""Draws the fungus garden's art and writes its block files: the fungus nodule (block texture and item) and the fungus
comb's loot table. Since the comb ages (2026-10-15) its textures, models, blockstate and item model are written by
tools/comb_age_art.py instead (comb_top, comb_side and comb_model here are the old flat comb, kept for reference).

Real Macrotermes fungus comb is a light brown, brain-like sponge of winding ridges and holes, built from chewed plant
material; small white balls of fungus (nodules) sprout on it. The comb here is a flat mat, 2 pixels per layer, with up
to 3 white nodules on top (the game uses layers 1 to 4; models for 5 to 8 exist so every block state has one).

Run with Python 3 and Pillow from the project folder:  python tools/fungus_garden_art.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/antcolonies"
DATA = ROOT / "src/main/resources/data/antcolonies"
BLOCKS = ASSETS / "textures/block"
ITEMS = ASSETS / "textures/item"

RIDGE = [(214, 190, 150), (200, 174, 132), (186, 158, 116)]
GROOVE = [(122, 94, 64), (104, 78, 52)]
HOLE = (74, 54, 36)
MYCELIUM = [(232, 226, 210), (220, 212, 192)]
NODULE = [(250, 248, 240), (238, 234, 222), (222, 216, 200), (204, 196, 178)]

MAX_LAYERS = 8
MAX_NODULES = 3
# Where the nodules sit on top (pixel x, z of their corner); each is a 3x3 ball made of a 3x2x3 box and a 1x1 cap.
NODULE_SPOTS = [(3, 4), (10, 9), (5, 11)]


def comb_top(rng):
    """Winding ridges (light) with grooves between them (dark) and a few holes: drawn by random walks on a groove base."""
    img = Image.new("RGBA", (16, 16), GROOVE[0] + (255,))
    for x in range(16):
        for y in range(16):
            if rng.random() < 0.25:
                img.putpixel((x, y), GROOVE[1] + (255,))
    for _ in range(9):
        x, y = rng.randrange(16), rng.randrange(16)
        dx, dy = rng.choice([(1, 0), (0, 1), (-1, 0), (0, -1)])
        for _ in range(rng.randint(6, 12)):
            img.putpixel((x % 16, y % 16), rng.choice(RIDGE) + (255,))
            if rng.random() < 0.35:
                dx, dy = rng.choice([(dy, dx), (-dy, -dx)])  # a bend
            x, y = x + dx, y + dy
    for _ in range(4):
        img.putpixel((rng.randrange(16), rng.randrange(16)), HOLE + (255,))
    for _ in range(5):
        img.putpixel((rng.randrange(16), rng.randrange(16)), rng.choice(MYCELIUM) + (255,))
    return img


def comb_side(rng):
    """Seen from the side: thin wavy layers of comb with dark pores between them."""
    img = Image.new("RGBA", (16, 16), GROOVE[0] + (255,))
    for row in range(16):
        for x in range(16):
            if row % 2 == 0 or rng.random() < 0.3:
                img.putpixel((x, row), rng.choice(RIDGE) + (255,))
        for _ in range(2):
            img.putpixel((rng.randrange(16), row), HOLE + (255,))
    return img


def nodule_texture(rng):
    img = Image.new("RGBA", (16, 16), NODULE[0] + (255,))
    for x in range(16):
        for y in range(16):
            img.putpixel((x, y), NODULE[min(3, int(rng.random() * 2.2 + y / 16 * 1.5))] + (255,))
    return img


def nodule_item(rng):
    """Three small white balls in a cluster, with a dark outline like vanilla items."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for cx, cy, r in [(6, 9, 3.2), (10, 7, 2.7), (9, 11, 2.4)]:
        for x in range(16):
            for y in range(16):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d <= r:
                    light = (x - cx + y - cy) / (2 * r)  # lit from the top left
                    img.putpixel((x, y), NODULE[max(0, min(3, int(1.5 + light * 2.5)))] + (255,))
    outline = img.copy()
    for x in range(16):
        for y in range(16):
            if img.getpixel((x, y))[3] == 0 and any(
                    0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] == 255
                    for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]):
                outline.putpixel((x, y), (96, 84, 64, 255))
    return outline


def face(uv, texture, cull=None):
    f = {"uv": uv, "texture": texture}
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def comb_model(layers, nodules):
    h = layers * 2
    elements = [box([0, 0, 0], [16, h, 16], {
        "down": face([0, 0, 16, 16], "#top", "down"),
        "up": face([0, 0, 16, 16], "#top"),
        "north": face([0, 16 - h, 16, 16], "#side", "north"),
        "south": face([0, 16 - h, 16, 16], "#side", "south"),
        "west": face([0, 16 - h, 16, 16], "#side", "west"),
        "east": face([0, 16 - h, 16, 16], "#side", "east"),
    })]
    for x, z in NODULE_SPOTS[:nodules]:
        all_faces = {side: face([x, z, x + 3, z + 3], "#nodule") for side in ["down", "up", "north", "south", "west", "east"]}
        elements.append(box([x, h, z], [x + 3, h + 2, z + 3], all_faces))
        cap = {side: face([x + 1, z + 1, x + 2, z + 2], "#nodule") for side in ["up", "north", "south", "west", "east"]}
        elements.append(box([x + 1, h + 2, z + 1], [x + 2, h + 3, z + 2], cap))
    return {
        "textures": {
            "particle": "antcolonies:block/fungus_comb_top",
            "top": "antcolonies:block/fungus_comb_top",
            "side": "antcolonies:block/fungus_comb_side",
            "nodule": "antcolonies:block/fungus_nodule",
        },
        "elements": elements,
    }


def loot_table():
    """The comb drops itself; its nodules drop as fungus nodules."""
    nodules = [{
        "type": "minecraft:item",
        "name": "antcolonies:fungus_nodule",
        "conditions": [{"condition": "minecraft:block_state_property", "block": "antcolonies:fungus_comb",
                        "properties": {"nodules": str(n)}}],
        "functions": [{"function": "minecraft:set_count", "count": n}],
    } for n in range(MAX_NODULES, 0, -1)]
    return {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "antcolonies:fungus_comb"}]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": nodules}]},
        ],
    }


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def main():
    rng = random.Random(11)
    comb_top(rng)  # (drawn and thrown away, so the nodules keep their random numbers)
    comb_side(rng)
    nodule_texture(rng).save(BLOCKS / "fungus_nodule.png")
    nodule_item(rng).save(ITEMS / "fungus_nodule.png")
    write_json(ASSETS / "models/item/fungus_nodule.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "antcolonies:item/fungus_nodule"}})
    write_json(DATA / "loot_table/blocks/fungus_comb.json", loot_table())
    print("wrote the fungus nodule textures, the nodule item model and the comb's loot table")


if __name__ == "__main__":
    main()
