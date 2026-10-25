"""The soil ramp's looks (planned with the user 2026-10-24, docs/plans/soil-ramp.md): builds
references/burrow_viewer.html, a 3D scene to turn and zoom (the model viewer's parts, see model_viewer.py) showing a
burrow cut open from the side: a stair tunnel to a 3x3 room, as the Matabele dig in bare ground, with its floor
smoothed by soil ramps carved from the soil it is dug in (Minecraft's own textures, read from the game's resources
jar). Switches: the soil, the steepness (45 degrees or gentler, one down per two forward), ramps or today's stairs,
the cut, and a Matabele major (about 1 block long) for size. Planning only: nothing in the game uses it.

Run from the project folder (after any ./gradlew build, which unpacks the game's resources):
    python tools/burrow_viewer.py [out.html]
"""
import base64
import glob
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# The soils ants dig (colonyframework:diggable), each with the block on top of the ground there and its ramp's texture.
SOILS = {
    "dirt": {"name": "Dirt under grass", "top": "grass", "soil": "dirt"},
    "coarse_dirt": {"name": "Coarse dirt", "top": "coarse_dirt", "soil": "coarse_dirt"},
    "rooted_dirt": {"name": "Rooted dirt", "top": "grass", "soil": "rooted_dirt"},
    "clay": {"name": "Clay", "top": "clay", "soil": "clay"},
    "mud": {"name": "Mud", "top": "mud", "soil": "mud"},
    "sand": {"name": "Sand", "top": "sand", "soil": "sand"},
    "red_sand": {"name": "Red sand", "top": "red_sand", "soil": "red_sand"},
}
TEXTURES = ["dirt", "coarse_dirt", "rooted_dirt", "clay", "mud", "sand", "red_sand",
            "grass_block_top", "grass_block_side", "grass_block_side_overlay"]


def game_jar():
    jars = glob.glob(str(ROOT / "build/moddev/artifacts/*client-extra*.jar"))
    if not jars:
        sys.exit("No game resources jar in build/moddev/artifacts: run ./gradlew build first.")
    return jars[0]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "references/burrow_viewer.html"
    textures = {}
    with zipfile.ZipFile(game_jar()) as jar:
        for name in TEXTURES:
            data = jar.read(f"assets/minecraft/textures/block/{name}.png")
            textures[name] = "data:image/png;base64," + base64.b64encode(data).decode()
    data = {"textures": textures, "soils": SOILS}
    template = (Path(__file__).resolve().parent / "burrow_viewer.template.html").read_text(encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(template.replace("/*DATA*/null", json.dumps(data)), encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    main()
