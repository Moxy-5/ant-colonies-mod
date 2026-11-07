"""The Matabele home choices (planning, 2026-11-06, docs/plans/matabele-homes.md): builds
references/nest_sites_viewer.html, a 3D scene to turn and zoom (the model viewer's way of drawing blocks: Minecraft's
flat face shading, the game's own textures) with every place a Matabele colony could live, each cut open down the
middle, the ants inside at today's real sizes (a major 0.5 blocks, 1 block = 30 mm of ant, species/InsectSizes):
the dead termite mound of today, a hollow fallen giant (already in the game), a room under a fallen log, an old
aardvark burrow, a ground squirrel warren, a root hollow at a living tree's foot, under a boulder, under a pile of fallen
branches, an old Matabele nest left behind; and two corners for the looks: "one block is 3 cm" (loose bark, a
gallery block) and the dead-wood tenants (centipede, scorpion, millipede, beetle grub) at real size against squeezed.
Planning only: nothing in the game uses it.

Run from the project folder (after any ./gradlew build, which unpacks the game's resources):
    python tools/nest_sites_viewer.py [out.html]
also: python tools/model_viewer.py nestsites [out.html]
"""
import base64
import glob
import json
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mod_paths  # noqa: E402  (where each mod's files are)

ROOT = Path(__file__).resolve().parent.parent

VANILLA = ["grass_block_top", "grass_block_side", "grass_block_side_overlay", "dirt", "coarse_dirt", "rooted_dirt",
           "granite", "stone", "andesite"]
SAVANNA = ["bleached_bark", "dead_wood_top_0", "frass", "rotten_wood", "termite_mound_old_side", "termite_mound_old_top",
           "umbrella_thorn_bark", "umbrella_thorn_log_top", "umbrella_thorn_leaves", "fallen_branch_bark_0",
           "fallen_branch_end", "peeling_bleached_bark", "termite_remains"]
# Each insect: its model and real length in mm (InsectSizes: 30 mm of ant to a block).
INSECTS = {"major": ("matabele_major", 15.0), "minor": ("matabele_minor", 7.5), "queen": ("matabele_queen", 18.0),
           "termite": ("termite_worker", 4.5)}


def game_jar():
    jars = glob.glob(str(ROOT / "build/moddev/artifacts/*client-extra*.jar"))
    if not jars:
        sys.exit("No game resources jar in build/moddev/artifacts: run ./gradlew build first.")
    return jars[0]


def uri(data):
    return "data:image/png;base64," + base64.b64encode(data).decode()


def page(out):
    textures = {}
    with zipfile.ZipFile(game_jar()) as jar:
        for name in VANILLA:
            textures[name] = uri(jar.read(f"assets/minecraft/textures/block/{name}.png"))
    assets = mod_paths.assets("savanna")
    for name in SAVANNA:
        textures[name] = uri((assets / f"textures/block/{name}.png").read_bytes())
    insects = {}
    for key, (model, mm) in INSECTS.items():
        geo = json.loads((assets / f"geo/entity/{model}.geo.json").read_text(encoding="utf-8"))
        g = geo["minecraft:geometry"][0]
        insects[key] = {"bones": g["bones"], "w": g["description"].get("texture_width", 64),
                        "h": g["description"].get("texture_height", 64), "mm": mm,
                        "texture": uri((assets / f"textures/entity/{model}.png").read_bytes())}
    data = {"textures": textures, "insects": insects}
    template = (Path(__file__).resolve().parent / "nest_sites_viewer.template.html").read_text(encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(template.replace("/*DATA*/null", json.dumps(data)), encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    page(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "references/nest_sites_viewer.html")
