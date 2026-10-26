"""Bracket fungus art (dead wood for the raids, part 4): writes the textures, block model, blockstate, item model and
loot table of antcolonies:bracket_fungus, the shelf of a heart rot fungus on a rotting tree.

Look (my pick, from Ganoderma applanatum / Phellinus on savanna acacias): a hard woody shelf in two tiers, its top
banded in brown rings round where it grows from the bark, a pale cream growing rim at the front edge, and cream pores
underneath. Run: python tools/bracket_art.py
"""
import math
import random

import tree_art
from tree_art import ASSETS, model, texture, write_json, item_from_block, drops_itself, shade

NAME = "bracket_fungus"
BROWN = (104, 70, 44)
DARK = (70, 46, 30)
RIM = (226, 214, 186)
PORES = (206, 192, 160)


def top():
    """Rings round the middle of the wall edge (the model's top face has the wall at the bottom of the picture)."""
    rng = random.Random(4)
    img = tree_art.blank()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 16.5)
            if r > 14.2:
                c = RIM
            elif r > 12.8:
                c = tree_art.mix(RIM, BROWN, 0.55)
            else:
                band = int(r / 1.6) % 2
                c = shade(BROWN if band else DARK, 0.92 + 0.1 * (r / 13))
            img[y][x] = shade(c, 0.94 + rng.random() * 0.1)
    return img


def under():
    rng = random.Random(5)
    img = tree_art.blank()
    for y in range(16):
        for x in range(16):
            c = PORES
            if (x + 2 * y) % 3 == 0 and rng.random() < 0.6:
                c = shade(PORES, 0.86)  # a pore
            img[y][x] = shade(c, 0.95 + rng.random() * 0.08)
    return img


def edge():
    """Side faces: the dark top, the pale rim, the underside's edge (rows 0, 1, 2; the rest the same as row 2)."""
    rng = random.Random(6)
    img = tree_art.blank()
    for y in range(16):
        for x in range(16):
            c = DARK if y == 0 else RIM if y == 1 else shade(PORES, 0.8)
            img[y][x] = shade(c, 0.94 + rng.random() * 0.1)
    return img


def box(a, b, h):
    """One block of the shelf, facing north (the trunk at z 16): its faces, with the wall face left off."""
    (x1, y1, z1), (x2, y2, z2) = a, b
    t = "#top"
    return {"from": list(a), "to": list(b), "faces": {
        "up": {"uv": [x1, z1, x2, z2], "texture": t},
        "down": {"uv": [x1, z1, x2, z2], "texture": "#under"},
        "north": {"uv": [x1, 0, x2, h], "texture": "#edge"},
        "east": {"uv": [z1, 0, z2, h], "texture": "#edge"},
        "west": {"uv": [z1, 0, z2, h], "texture": "#edge"}}}


def game_files():
    texture(f"{NAME}_top", top())
    texture(f"{NAME}_under", under())
    texture(f"{NAME}_edge", edge())
    model(NAME, {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": f"antcolonies:block/{NAME}_top", "top": f"antcolonies:block/{NAME}_top",
                     "under": f"antcolonies:block/{NAME}_under", "edge": f"antcolonies:block/{NAME}_edge"},
        "elements": [
            box((3, 4, 10), (13, 7, 16), 3),   # the big lower shelf
            box((5, 4, 8), (11, 7, 10), 3),    # its rounded front
            box((5, 8, 12), (11, 10, 16), 2),  # the small tier over it
            box((6, 8, 11), (10, 10, 12), 2),
        ]})
    write_json(ASSETS / f"blockstates/{NAME}.json", {"variants": {
        "facing=north": {"model": f"antcolonies:block/{NAME}"},
        "facing=east": {"model": f"antcolonies:block/{NAME}", "y": 90},
        "facing=south": {"model": f"antcolonies:block/{NAME}", "y": 180},
        "facing=west": {"model": f"antcolonies:block/{NAME}", "y": 270}}})
    item_from_block(NAME)
    drops_itself(NAME)


if __name__ == "__main__":
    game_files()
