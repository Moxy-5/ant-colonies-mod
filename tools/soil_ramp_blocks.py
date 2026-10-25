"""The soil ramps' files (docs/plans/soil-ramp.md): block states, models, loot tables and names for every soil ramp of
the Colony Framework (framework/.../block/SoilRamps.java). The ramp's shape is the termite ramp's straight one (8 steps
of 2 pixels, rising toward east before turning), drawn with its soil's own Minecraft texture; breaking one gives what
breaking its soil gives (its loot table points at the soil's).

Run from the project folder after adding a ramp to SoilRamps.java (and to RAMPS below):
    python tools/soil_ramp_blocks.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "framework/src/main/resources/assets/colonyframework"
DATA = ROOT / "framework/src/main/resources/data/colonyframework"

# ramp name: (its soil's block id, its texture, its name in the game)
RAMPS = {
    "dirt_ramp": ("minecraft:dirt", "minecraft:block/dirt", "Dirt Ramp"),
    "coarse_dirt_ramp": ("minecraft:coarse_dirt", "minecraft:block/coarse_dirt", "Coarse Dirt Ramp"),
    "rooted_dirt_ramp": ("minecraft:rooted_dirt", "minecraft:block/rooted_dirt", "Rooted Dirt Ramp"),
    "clay_ramp": ("minecraft:clay", "minecraft:block/clay", "Clay Ramp"),
    "mud_ramp": ("minecraft:mud", "minecraft:block/mud", "Mud Ramp"),
    "sand_ramp": ("minecraft:sand", "minecraft:block/sand", "Sand Ramp"),
    "red_sand_ramp": ("minecraft:red_sand", "minecraft:block/red_sand", "Red Sand Ramp"),
}
# The straight model rises toward east; the other high sides turn it.
TURNS = {"east": 0, "south": 90, "west": 180, "north": 270}


def face(uv, cull=None):
    out = {"uv": uv, "texture": "#soil"}
    if cull:
        out["cullface"] = cull
    return out


def ramp_model():
    """8 slabs, each 2 pixels higher and 2 pixels shorter toward east than the one under it (the termite ramp's sums)."""
    elements = []
    for i in range(8):
        x0, y0, y1 = 2 * i, 2 * i, 2 * i + 2
        v0, v1 = 16 - y1, 16 - y0
        faces = {
            "up": face([x0, 0, 16, 16], "up" if i == 7 else None),
            "north": face([0, v0, 16 - x0, v1], "north"),
            "south": face([x0, v0, 16, v1], "south"),
            "west": face([0, v0, 16, v1], "west" if i == 0 else None),
            "east": face([0, v0, 16, v1], "east"),
        }
        if i == 0:
            faces["down"] = face([0, 0, 16, 16], "down")
        elements.append({"from": [x0, y0, 0], "to": [16, y1, 16], "faces": faces})
    return {"parent": "minecraft:block/block", "textures": {"particle": "#soil"}, "elements": elements}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    write(ASSETS / "models/block/soil_ramp.json", ramp_model())
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    for name, (soil, texture, title) in RAMPS.items():
        write(ASSETS / f"models/block/{name}.json",
              {"parent": "colonyframework:block/soil_ramp", "textures": {"soil": texture}})
        write(ASSETS / f"blockstates/{name}.json", {"variants": {
            f"facing={side}": {"model": f"colonyframework:block/{name}", "y": turn, "uvlock": True} if turn
            else {"model": f"colonyframework:block/{name}", "uvlock": True}
            for side, turn in TURNS.items()}})
        write(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:loot_table",
                                                "value": soil.replace(":", ":blocks/")}]}]})
        lang[f"block.colonyframework.{name}"] = title
    write(lang_path, lang)
    print("wrote", len(RAMPS), "soil ramps")


if __name__ == "__main__":
    main()
