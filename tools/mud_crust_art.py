"""Draws the Mud Crust block and writes its block files: textures (look C, "crumbly with pores", the user's pick of
2026-10-14 in references/mud_crust_viewer.html; 3 versions for each of the 4 ages), one plate model per texture, the
multipart blockstate (a plate on any of the 6 faces, a random version each), the item model and the loot table.

The textures come from the same drawing as the viewer (tools/mud_crust_viewer.py, crumbly), so the game matches what
the user picked. Run from the project folder:  python tools/mud_crust_art.py
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dead_wood_art import write_png  # noqa: E402
from mud_crust_viewer import AGES, THICKNESS, crumbly  # noqa: E402
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)

ROOT = Path(__file__).resolve().parent.parent
ASSETS = mod_paths.assets("savanna")
DATA = mod_paths.data("savanna")
VARIANTS = 3
T = THICKNESS["C"]
# How the plate model (on the north face) is turned to sit on each face, as vanilla glow lichen.
TURN = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def write_json(rel, data):
    path = ASSETS / rel if not rel.startswith("data:") else DATA / rel[5:]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def plate_model(texture):
    """A plate of mud on the north face of the block, T pixels thick."""
    edge_v = [0, 0, 16, T]
    edge_u = [0, 0, T, 16]
    return {
        "parent": "block/block",
        "ambientocclusion": False,
        "textures": {"particle": texture, "crust": texture},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 16, T],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#crust", "cullface": "north"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#crust"},
                "east": {"uv": edge_u, "texture": "#crust"},
                "west": {"uv": edge_u, "texture": "#crust"},
                "up": {"uv": edge_v, "texture": "#crust"},
                "down": {"uv": edge_v, "texture": "#crust"},
            },
        }],
    }


def main():
    parts = []
    for age_index, age in enumerate(AGES):
        models = []
        for v in range(VARIANTS):
            name = f"mud_crust_{age}_{v}"
            pixels = [[tuple(c) + (255,) for c in row] for row in crumbly(age, v)]
            write_png(ASSETS / f"textures/block/{name}.png", pixels)
            write_json(f"models/block/{name}.json", plate_model(f"antcolonies:block/{name}"))
            models.append(f"antcolonies:block/{name}")
        for face, turn in TURN.items():
            parts.append({
                "when": {face: "true", "age": str(age_index)},
                "apply": [dict({"model": m, "uvlock": True}, **turn) for m in models],
            })
    write_json("blockstates/mud_crust.json", {"multipart": parts})
    write_json("models/item/mud_crust.json", {"parent": "minecraft:item/generated",
                                              "textures": {"layer0": "antcolonies:block/mud_crust_setting_0"}})
    # Thin mud crumbles when broken: it drops nothing.
    write_json("data:loot_table/blocks/mud_crust.json", {"type": "minecraft:block", "pools": []})
    print("wrote the mud crust textures, models, blockstate, item model and loot table")


if __name__ == "__main__":
    main()
