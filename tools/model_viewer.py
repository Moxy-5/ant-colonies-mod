"""The model viewer (the user asked to keep it for future use, 2026-10-14): builds a web page with a 3D scene you can
turn and zoom, the blocks drawn with Minecraft's flat face shading, to compare looks before anything goes into the game.
Now set up for the fallen tree (stump, trunk, crown branches) and the looks for the untouched Dead Wood (A, B, C from
dead_wood_concepts.py; D the user's reference, a darker Minecraft-style dead log with bark strips, the one picked and
now in the game, see dead_wood_art.py; E a mix: D's strips in A's sun-bleached silver), with the termite-eaten mud
stages on top. Textures are drawn here and put into the page as data, so the page is one file.

To show something else, change what main() puts into DATA and the build() function in model_viewer.template.html
(three.js boxes: one per block, 6 textures each). The page needs the internet for three.js (from cdnjs/jsdelivr).

Writes the page to the path given (default references/model_viewer.html; open it in a web browser).
Run from the project folder:  python tools/model_viewer.py [out.html]

Second scene, the fungus comb as it ages (2026-10-15, comb_age_art.py; its own page template, comb_viewer.template.html):
python tools/model_viewer.py comb [out.html]   (default references/comb_viewer.html)

Third scene, the grama grasses for the harvester ants (2026-10-16, grama_art.py; its own template, grama_viewer.template.html):
python tools/model_viewer.py grama [out.html]   (default references/grama_viewer.html)

Fourth scene, the giant savanna trees (M7.5 step 0, 2026-10-18, tree_art.py for the textures; its own template,
tree_viewer.template.html, which also holds the tree shape maker: every kind, age and dead stage from one seed):
python tools/model_viewer.py trees [out.html]   (default references/tree_viewer.html)
"""
import base64
import json
import random
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dead_wood_concepts as concepts  # noqa: E402
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = mod_paths.assets("savanna") / "textures/block"
S = 16
MUD = (138, 78, 50)
MUD_DARK = (104, 58, 38)
MUD_COVER = [0.0, 0.25, 0.5, 0.75]


def clamp(c):
    return max(0, min(255, round(c)))


def png(rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in (px if len(px) == 4 else px + (255,))) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def uri(data):
    return "data:image/png;base64," + base64.b64encode(data).decode()


def strips(name, variant):
    """D and E: bark in long strips along the trunk, like the user's reference (a Minecraft-style dead log)."""
    rng = random.Random(f"{name}{variant}")
    if name == "D":
        tones = [(62, 56, 48), (84, 77, 67), (104, 95, 83), (122, 112, 98)]
    else:
        tones = [(96, 90, 82), (138, 132, 121), (162, 156, 145), (184, 178, 167)]
    img = [[None] * S for _ in range(S)]
    for y in range(S):
        tone = rng.randrange(1, 4)
        x = 0
        while x < S:
            run = rng.randint(3, 9)
            for i in range(run):
                if x + i < S:
                    img[y][x + i] = tuple(clamp(c * rng.uniform(0.95, 1.05)) for c in tones[tone])
            x += run
            tone = max(0, min(3, tone + rng.choice([-2, -1, 1, 2])))
    if name == "E":
        # A's long cracks, lined up across block edges.
        for start, end, rows, width in concepts.fissures(rng, 2):
            for x in range(start, end):
                img[int(round(rows[x])) % S][x] = (70, 64, 58)
    return img


def side(name, variant):
    return concepts.option(name, variant) if name in "ABC" else strips(name, variant)


def with_mud(img, stage):
    """The eaten stages' mud, as on the real Dead Wood (tools/dead_wood_art.py)."""
    img = [list(row) for row in img]
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
                    img[y][x] = tuple(clamp(c + mrng.randint(-6, 6)) for c in shade)
    return img


def turned(img):
    """The same texture with the grain running up and down (for the upright stump)."""
    return [[img[x][S - 1 - y] for x in range(S)] for y in range(S)]


def d_end():
    """The reference picture's end: brown rings, dark bark rim."""
    rows = []
    for y in range(S):
        row = []
        for x in range(S):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            color = (70, 62, 52) if ring > 7 else [(132, 116, 96), (112, 98, 82)][int(ring) % 2]
            row.append(color)
        rows.append(row)
    return rows


def grass():
    rng = random.Random(1)
    return [[tuple(clamp(c * rng.uniform(0.85, 1.12)) for c in (150, 160, 82)) for _ in range(S)] for _ in range(S)]


def file_uri(name):
    return uri((TEXTURES / name).read_bytes())


def comb_page(out):
    """The comb looks: for each look (A, B, C) and age (fresh, ripe, old) a top and a side texture."""
    import comb_age_art as comb
    data = {"looks": {}, "nodule": uri(png(comb.nodule())), "wall": uri(png(comb.room_wall()))}
    for look in comb.LOOKS:
        data["looks"][look] = {age: {face: uri(png(comb.texture(look, age, face))) for face in ("top", "side")}
                               for age in comb.AGES}
    template = (Path(__file__).resolve().parent / "comb_viewer.template.html").read_text(encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(template.replace("/*DATA*/null", json.dumps(data)), encoding="utf-8")
    print("wrote", out)


def grama_page(out):
    """The grama looks: for each kind (blue, black), look (A, B) and stage, two versions; and the ground round them."""
    import grama_art as grama
    data = {"grass": uri(png(grama.grass_top())), "redSand": uri(png(grama.red_sand())),
            "shortGrass": uri(png(grama.short_grass())), "deadBush": uri(png(grama.dead_bush()))}
    for kind in grama.KINDS:
        data[kind] = {look: {stage: [uri(grama.png(grama.texture(kind, look, stage, v))) for v in range(grama.VARIANTS)]
                             for stage in grama.STAGES} for look in grama.LOOKS}
    template = (Path(__file__).resolve().parent / "grama_viewer.template.html").read_text(encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(template.replace("/*DATA*/null", json.dumps(data)), encoding="utf-8")
    print("wrote", out)


def trees_page(out):
    """The tree looks: every block texture (two looks where there is a choice); the viewer's shapes are made in the page,
    the game's own shapes (step 2) come from build/tree_shapes.json, written by ./gradlew test."""
    import tree_art
    data = {}
    for name, img in tree_art.all_textures().items():
        if isinstance(img, tuple):
            data[name] = file_uri(img[1])
        else:
            data[name] = uri(png([[px if px is not None else (0, 0, 0, 0) for px in row] for row in img]))
    shapes = ROOT / "build/tree_shapes.json"
    if shapes.exists():
        data["shapes"] = json.loads(shapes.read_text(encoding="utf-8"))
    else:
        print("no build/tree_shapes.json yet (run ./gradlew test): the page shows only the viewer's own shapes")
    template = (Path(__file__).resolve().parent / "tree_viewer.template.html").read_text(encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(template.replace("/*DATA*/null", json.dumps(data)), encoding="utf-8")
    print("wrote", out)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "trees":
        trees_page(Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "references/tree_viewer.html")
        return
    if len(sys.argv) > 1 and sys.argv[1] == "grama":
        grama_page(Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "references/grama_viewer.html")
        return
    if len(sys.argv) > 1 and sys.argv[1] == "comb":
        comb_page(Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "references/comb_viewer.html")
        return
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "references/model_viewer.html"
    data = {"looks": {}, "branch": [file_uri(f"fallen_branch_bark_{s}.png") for s in range(4)],
            "branchEnd": file_uri("fallen_branch_end.png"), "grass": uri(png(grass()))}
    for name in "ABCDE":
        look = {"side": [], "stump": []}
        for stage in range(4):
            sides = [with_mud(side(name, v), stage) for v in range(3)]
            look["side"].append([uri(png(t)) for t in sides])
            look["stump"].append([uri(png(turned(t))) for t in sides])
        look["end"] = [uri(png(d_end())) if name == "D" and s == 0 else file_uri(f"dead_wood_top_{s}.png") for s in range(4)]
        data["looks"][name] = look
    template = (Path(__file__).resolve().parent / "model_viewer.template.html").read_text(encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(template.replace("/*DATA*/null", json.dumps(data)), encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    main()
