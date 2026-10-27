"""Heart rot art (dead wood for the raids, part 4): writes the game files of antcolonies:bracket_fungus and of the
heart rot bark, from the looks the user picked in the bracket viewer 2026-10-26 (tools/rot_looks_art.py draws them).

- bracket_fungus: one model per age (knob, young, grown, old) and the tiers (grown and old), turned by its facing;
  the item shows a grown one.
- pulp_heap: the crumbly rotten heartwood spilled where a hollow tree snapped, in 8 layers like snow: dark brown
  cube-cracked brown rot with pale stringy punk (white rot) through it (my pick, 2026-10-26).
- for each bark a rotting acacia can have (umbrella thorn, camel thorn, bleached, charred): <wood>_spores, the bark under
  a grown bracket stained cocoa-brown by its spores, and <wood>_wound, the scar where the rot got in.

The first look (a single two-tier shelf, before 2026-10-26) is kept below for the viewer's comparison only.
Run: python tools/bracket_art.py
"""
import math
import random

import rot_looks_art as rot
import tree_art
from tree_art import ASSETS, model, texture, write_json, item_from_block, drops_itself, shade

NAME = "bracket_fungus"
AGES = ["knob", "young", "mature", "old"]  # AGE 0 to 3
TIERED = {"mature": "tiers", "old": "tiers_old"}
# The wood block (a trunk's outside, bark on every face) each bark look marks, and the kind of block.
BARKS = {
    "umbrella": ("umbrella_thorn_wood", "log"),
    "camel": ("camel_thorn_wood", "log"),
    "bleached": ("bleached_wood", "eaten"),
    "charred": ("charred_wood", "log"),
}


def bracket_files():
    """Every bracket look's textures and model, then the blockstate (facing x age x tiers)."""
    looks = rot.stages()
    looks["tiers_old"] = old_tiers(looks["old"][2])
    for look, (_, elements, textures) in looks.items():
        names = {}
        for key, rows in textures.items():
            tex = (f"{NAME}_{look}" if key == look else f"{NAME}_{look}_{key}") if look in AGES else None
            if tex is None:
                # The tiers reuse the ages' textures: the lower shelves the grown (or old) ones, the top the young.
                src = {"top": "mature", "under": "mature", "edge": "mature", "ytop": "young", "yunder": "young", "yedge": "young"}
                base = "old" if look == "tiers_old" else src[key]
                tex = f"{NAME}_{base}_{key.lstrip('y') if key.startswith('y') else key}"
            else:
                texture(tex, rows)
            names[key] = f"antcolonies:block/{tex}"
        name = "tiers" if look == "stacked" else look
        model(f"{NAME}_{name}", {"parent": "minecraft:block/block", "ambientocclusion": False,
                                 "textures": dict(particle=next(iter(names.values())), **names), "elements": elements})
    variants = {}
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in turn.items():
        for age, look in enumerate(AGES):
            for tiers in (False, True):
                m = TIERED.get(look, look) if tiers else look
                v = {"model": f"antcolonies:block/{NAME}_{m}"}
                if y:
                    v["y"] = y
                variants[f"age={age},facing={facing},tiers={str(tiers).lower()}"] = v
    write_json(ASSETS / f"blockstates/{NAME}.json", {"variants": variants})
    item_from_block(NAME, f"{NAME}_mature")
    drops_itself(NAME)


def old_tiers(old_textures):
    """The tiers grown old: the same three shelves, all old (my pick: the top one has stopped growing too)."""
    _, elements, _ = rot.stages()["stacked"]
    old = []
    for el in elements:
        faces = {f: dict(v, texture=v["texture"].replace("#y", "#")) for f, v in el["faces"].items()}
        old.append(dict(el, faces=faces))
    return ("tiers, old", old, {k: v for k, v in old_textures.items()})


def bark_files():
    """The spore-dusted and wound logs of each bark."""
    for kind, looks in rot.barks().items():
        log_name, how = BARKS[kind]
        for look in ("spores", "wound"):
            name = f"{log_name}_{look}"
            texture(name, looks["dusted" if look == "spores" else "wound"])
            side = f"antcolonies:block/{name}"
            if how == "eaten":
                tree_art.eaten_log(name, side, side, True)  # termites eat it like the plain bleached wood
            else:
                tree_art.log(name, side, side)


def pulp_texture(seed, side):
    """Brown rot broken into little cubes (dark cracks on a grid) with pale punk strands; the sides darker, damp below."""
    rng = random.Random(seed)
    n = tree_art.noise(seed + "n", 4)
    img = [[tree_art.mix((78, 46, 28), (138, 88, 52), n(x, y)) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if (x + (y // 3) % 2 * 2) % 4 == 0 or y % 3 == 0 and rng.random() < 0.7:
                img[y][x] = shade(img[y][x], 0.62)  # the cracks between the cubes
    for _ in range(4):
        x, y = rng.uniform(0, 16), rng.uniform(0, 16)
        for _ in range(rng.randint(4, 9)):
            img[int(y) % 16][int(x) % 16] = shade((214, 196, 158), rng.uniform(0.9, 1.06))  # white-rot punk strands
            x += rng.uniform(-1, 1)
            y += rng.choice([0, 1]) if side else rng.uniform(-1, 1)
    if side:
        for y in range(16):
            for x in range(16):
                img[y][x] = shade(img[y][x], 0.95 - 0.12 * y / 15)
    for _ in range(14):
        img[rng.randrange(16)][rng.randrange(16)] = (52, 32, 22)
    return img


def pulp_files():
    texture("pulp_heap", pulp_texture("pulp", False))
    texture("pulp_heap_side", pulp_texture("pulpside", True))
    variants = {}
    for layers in range(1, 9):
        h = layers * 2
        sides = {f: {"uv": [0, 16 - h, 16, 16], "texture": "#side", "cullface": f} for f in ("north", "south", "east", "west")}
        el = {"from": [0, 0, 0], "to": [16, h, 16], "faces": dict({
            "down": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "down"},
            "up": dict({"uv": [0, 0, 16, 16], "texture": "#top"}, **({"cullface": "up"} if h == 16 else {}))}, **sides)}
        model(f"pulp_heap_height{h}", {"parent": "minecraft:block/block", "textures": {
            "particle": "antcolonies:block/pulp_heap", "top": "antcolonies:block/pulp_heap", "side": "antcolonies:block/pulp_heap_side"},
            "elements": [el]})
        variants[f"layers={layers}"] = {"model": f"antcolonies:block/pulp_heap_height{h}"}
    write_json(ASSETS / "blockstates/pulp_heap.json", {"variants": variants})
    item_from_block("pulp_heap", "pulp_heap_height6")
    drops_itself("pulp_heap")


def game_files():
    bracket_files()
    bark_files()
    pulp_files()


# ---- The first bracket look, for the viewer's comparison only (not written into the game) ----

BROWN = (104, 70, 44)
DARK = (70, 46, 30)
RIM = (226, 214, 186)
PORES = (206, 192, 160)


def first_top():
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


def first_under():
    rng = random.Random(5)
    img = tree_art.blank()
    for y in range(16):
        for x in range(16):
            c = PORES
            if (x + 2 * y) % 3 == 0 and rng.random() < 0.6:
                c = shade(PORES, 0.86)  # a pore
            img[y][x] = shade(c, 0.95 + rng.random() * 0.08)
    return img


def first_edge():
    """Side faces: the dark top, the pale rim, the underside's edge (rows 0, 1, 2; the rest the same as row 2)."""
    rng = random.Random(6)
    img = tree_art.blank()
    for y in range(16):
        for x in range(16):
            c = DARK if y == 0 else RIM if y == 1 else shade(PORES, 0.8)
            img[y][x] = shade(c, 0.94 + rng.random() * 0.1)
    return img


def first_box(a, b, h):
    (x1, y1, z1), (x2, y2, z2) = a, b
    return {"from": list(a), "to": list(b), "faces": {
        "up": {"uv": [x1, z1, x2, z2], "texture": "#top"},
        "down": {"uv": [x1, z1, x2, z2], "texture": "#under"},
        "north": {"uv": [x1, 0, x2, h], "texture": "#edge"},
        "east": {"uv": [z1, 0, z2, h], "texture": "#edge"},
        "west": {"uv": [z1, 0, z2, h], "texture": "#edge"}}}


def first_look():
    """(elements, textures as pixel rows) of the first bracket."""
    return ([first_box((3, 4, 10), (13, 7, 16), 3), first_box((5, 4, 8), (11, 7, 10), 3),
             first_box((5, 8, 12), (11, 10, 16), 2), first_box((6, 8, 11), (10, 10, 12), 2)],
            {"top": first_top(), "under": first_under(), "edge": first_edge()})


if __name__ == "__main__":
    game_files()
