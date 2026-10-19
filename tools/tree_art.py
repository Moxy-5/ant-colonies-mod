"""Draws the block looks for the giant savanna trees (M7.5, planned with the user 2026-10-18, docs/DESIGN.md "Giant
savanna trees", docs/plans/m7.5-giant-trees.md), in two looks where there is a choice, for the user to pick in the tree
viewer (python tools/model_viewer.py trees). Once looks are picked this file will also write the game's textures.

Real trees (what the textures follow):
- Umbrella thorn (Vachellia tortilis): grey to near-black bark, rough, cut by long fissures, the young stems
  red-brown; reddish-brown heartwood; tiny feathery leaflets in flat, see-through layers; paired long straight white
  spines with small hooked thorns between them.
- Camel thorn (Vachellia erioloba): very deeply furrowed dark grey bark broken into plates; very hard dark red-brown
  heartwood; denser grey-green crown; thick grey velvety half-moon pods.
- Baobab (Adansonia digitata): smooth grey to pinkish-brown bark in folds, with lichen spots; pale spongy, watery wood
  that rots into a heap of fibres; hand-shaped dark green leaves, leafless most of the year.
- Dead wood: bark weathers to silver-grey (the user's favourite, E) or stays the darker in-game grey-brown (D) and
  peels in strips; inside, heart-rot leaves soft rotten wood, and beetle larvae and termites leave frass (wood dust).

Looks: A "Minecraft-simple" (close to vanilla acacia, few details), B "Field detail" (the real bark's fissures, plates
and folds; the leaflets one by one).

Needs no extra libraries. Used by tools/model_viewer.py (trees).
"""
import math
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/antcolonies/textures/block"
S = 16
KINDS = ["umbrella", "camel", "baobab"]
LOOKS = ["A", "B"]


def clamp(c):
    return max(0, min(255, round(c)))


def shade(color, f):
    return tuple(clamp(c * f) for c in color[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise(seed, cells=4):
    """Smooth value noise that tiles across block edges: a function (x, y) -> 0..1."""
    rng = random.Random(seed)
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        fx, fy = x / S * cells, y / S * cells
        x0, y0 = int(fx) % cells, int(fy) % cells
        x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        top = grid[y0][x0] + (grid[y0][x1] - grid[y0][x0]) * tx
        bottom = grid[y1][x0] + (grid[y1][x1] - grid[y1][x0]) * tx
        return top + (bottom - top) * ty
    return at


def blank(color=None):
    return [[color] * S for _ in range(S)]


def speckle(img, rng, amount=0.06):
    return [[None if px is None else shade(px, 1 + rng.uniform(-amount, amount)) for px in row] for row in img]


# ---- Bark (the grain runs up and down: y is along the trunk) ----

def bark(kind, look):
    rng = random.Random(f"bark{kind}{look}")
    if kind == "umbrella":
        base, dark, warm = (92, 84, 76), (44, 38, 34), (118, 72, 52)
    elif kind == "camel":
        base, dark, warm = (88, 84, 80), (30, 28, 27), (96, 64, 50)
    else:
        base, dark, warm = (150, 132, 118), (104, 90, 82), (168, 140, 124)
    n = noise(f"n{kind}{look}", 4)
    img = [[shade(base, 0.85 + 0.3 * n(x, y)) for x in range(S)] for y in range(S)]
    if look == "A":
        # Vanilla-style: a few straight dark lines down the trunk.
        for x in rng.sample(range(S), 4 if kind != "baobab" else 2):
            for y in range(S):
                if rng.random() < 0.85:
                    img[y][x] = shade(dark, 1.3 if kind != "baobab" else 1.0)
        return speckle(img, rng, 0.05)
    if kind == "umbrella":
        # Long wandering fissures with red-brown in their depths, rough ridges between them.
        for _ in range(5):
            x = rng.uniform(0, S)
            for y in range(S):
                x += rng.choice([-1, 0, 0, 1]) * 0.6
                xi = int(x) % S
                img[y][xi] = dark
                if rng.random() < 0.35:
                    img[y][(xi + 1) % S] = warm
        for _ in range(14):
            img[rng.randrange(S)][rng.randrange(S)] = shade(base, 1.25)
    elif kind == "camel":
        # Deep furrows (two pixels wide, near black) and grey plates broken across.
        cols = sorted(rng.sample(range(0, S, 2), 4))
        for c in cols:
            wobble = 0
            for y in range(S):
                wobble += rng.choice([-1, 0, 0, 0, 1])
                wobble = max(-1, min(1, wobble))
                for w in (0, 1):
                    img[y][(c + wobble + w) % S] = dark
        for y in rng.sample(range(S), 5):
            x0 = rng.randrange(S)
            for x in range(x0, x0 + rng.randint(2, 4)):
                img[y][x % S] = shade(dark, 1.4)
        for _ in range(10):
            y, x = rng.randrange(S), rng.randrange(S)
            if img[y][x] != dark:
                img[y][x] = shade(base, 1.2)
    else:
        # Smooth folded bark: soft horizontal wrinkles, a few vertical folds, grey-green lichen spots.
        for y in rng.sample(range(S), 5):
            x0 = rng.randrange(S)
            for x in range(x0, x0 + rng.randint(3, 7)):
                img[y][x % S] = shade(base, 0.82)
        for c in rng.sample(range(S), 2):
            for y in range(S):
                img[y][c] = mix(img[y][c], dark, 0.5)
        for _ in range(4):
            cx, cy = rng.randrange(S), rng.randrange(S)
            for dx, dy in [(0, 0), (1, 0), (0, 1)]:
                if rng.random() < 0.8:
                    img[(cy + dy) % S][(cx + dx) % S] = (162, 164, 128)
    return speckle(img, rng, 0.04)


BARK_COLOURS = {  # base, dark, light for the good-looking barks (C, D)
    "umbrella": ((96, 86, 76), (52, 44, 40), (132, 118, 102)),
    "camel": ((82, 76, 72), (36, 32, 30), (118, 110, 102)),
    "baobab": ((152, 134, 120), (112, 96, 86), (186, 168, 152)),
}


def bark_soft(kind):
    """C "soft painted" (the user wanted more good-looking options): smooth bands of colour running down the trunk,
    gentle streaks and a few warm highlights; calm to look at from far, still bark close up."""
    base, dark, light = BARK_COLOURS[kind]
    rng = random.Random(f"soft{kind}")
    phase = [rng.uniform(0, math.pi * 2) for _ in range(3)]
    img = []
    for y in range(S):
        row = []
        for x in range(S):
            w = 0.5 + 0.25 * math.sin(x / S * math.pi * 4 + phase[0] + math.sin(y / S * math.pi * 2 + phase[1]) * 0.8)                 + 0.15 * math.sin(x / S * math.pi * 6 + phase[2])
            c = mix(dark, light, clamp(w * 255) / 255)
            row.append(mix(c, base, 0.35))
        img.append(row)
    for _ in range(3):
        x, y0 = rng.randrange(S), rng.randrange(S)
        for y in range(y0, y0 + rng.randint(4, 9)):
            img[y % S][x] = mix(img[y % S][x], dark, 0.55)
    for _ in range(6):
        img[rng.randrange(S)][rng.randrange(S)] = shade(light, 1.08)
    return speckle(img, rng, 0.025)


def bark_plates(kind):
    """D "chunky plates": bold blocky plates of bark with dark outlines, like Minecraft's spruce and oak logs;
    the baobab's are long and smooth, the camel thorn's small and deep."""
    base, dark, light = BARK_COLOURS[kind]
    rng = random.Random(f"plates{kind}")
    img = blank(dark)
    width = {"umbrella": (3, 4), "camel": (2, 3), "baobab": (4, 6)}[kind]
    height = {"umbrella": (4, 7), "camel": (3, 5), "baobab": (6, 12)}[kind]
    x = 0
    while x < S:
        w = rng.randint(*width)
        y = rng.randrange(S)
        covered = 0
        while covered < S:
            h = rng.randint(*height)
            tone = rng.uniform(0.88, 1.12)
            for dy in range(h - 1):
                for dx in range(w - 1):
                    px, py = (x + dx) % S, (y + covered + dy) % S
                    c = shade(base, tone)
                    if dx == 0 or dy == 0:
                        c = mix(c, light, 0.45)  # the lit top-left edge of each plate
                    img[py][px] = c
            covered += h
        x += w
    return speckle(img, rng, 0.03)


def wood_end(kind):
    """The end of a cut or broken trunk: heartwood rings, sapwood, the bark rim."""
    if kind == "umbrella":
        heart, sap, rim = (128, 70, 50), (176, 128, 86), (60, 52, 46)
    elif kind == "camel":
        heart, sap, rim = (92, 44, 36), (150, 96, 70), (40, 36, 34)
    else:
        heart, sap, rim = (200, 182, 150), (214, 198, 166), (130, 114, 102)
    rng = random.Random(f"end{kind}")
    img = []
    for y in range(S):
        row = []
        for x in range(S):
            r = math.hypot(x - 7.5, y - 7.5)
            if max(abs(x - 7.5), abs(y - 7.5)) > 7:
                c = rim
            elif r > 5.3:
                c = sap
            else:
                c = shade(heart, 0.88 if int(r * 1.3) % 2 else 1.04)
            row.append(c)
        img.append(row)
    if kind == "baobab":
        for _ in range(26):
            img[rng.randrange(1, 15)][rng.randrange(1, 15)] = (176, 156, 124)
    return speckle(img, rng, 0.04)


def wood_inner(kind):
    """Wood inside a trunk, seen when it is cut open (grain up and down)."""
    end = wood_end(kind)
    base = end[8][8]
    rng = random.Random(f"inner{kind}")
    img = [[shade(base, 0.92 + 0.12 * math.sin(x * 1.7 + rng.random())) for x in range(S)] for _ in range(S)]
    return speckle(img, rng, 0.05)


# ---- Dead wood ----

def dead_bark_silver(variant=0):
    """E, the user's favourite: D's long strips in sun-bleached silver, with long cracks (as model_viewer.strips)."""
    import model_viewer as mv
    img = mv.strips("E", variant)
    return [[img[x][S - 1 - y] for x in range(S)] for y in range(S)]  # grain up and down


def dead_bark_grey():
    """D, the dead wood in the game now (a dead version of Minecraft's savanna log)."""
    return ("file", "dead_wood_0_0.png")


def peeling_bark():
    """Silver bark lifting in strips: pale bare wood where it has fallen off, dark shadow under the curled edges."""
    rng = random.Random("peel")
    img = dead_bark_silver(1)
    bare = (206, 196, 172)
    for _ in range(3):
        x0, y0, w, h = rng.randrange(S), rng.randrange(S), rng.randint(3, 5), rng.randint(5, 9)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img[y % S][x % S] = shade(bare, rng.uniform(0.93, 1.05))
            img[y % S][(x0 - 1) % S] = (54, 48, 42)
        for x in range(x0, x0 + w):
            img[(y0 + h) % S][x % S] = (70, 62, 54)
    return img


def dead_inner():
    rng = random.Random("deadinner")
    img = [[shade((176, 166, 146), 0.9 + 0.15 * math.sin(x * 2.1 + y * 0.15 + rng.random())) for x in range(S)] for y in range(S)]
    return speckle(img, rng, 0.05)


def rotten_wood():
    """Heart-rot: soft red-brown crumbly wood, darker pits, white fungus threads."""
    rng = random.Random("rot")
    n = noise("rotn", 4)
    img = [[mix((92, 50, 32), (150, 92, 56), n(x, y)) for x in range(S)] for y in range(S)]
    for _ in range(18):
        img[rng.randrange(S)][rng.randrange(S)] = (52, 30, 22)
    for _ in range(3):
        x, y = rng.uniform(0, S), rng.randrange(S)
        for _ in range(rng.randint(4, 8)):
            img[int(y) % S][int(x) % S] = (226, 218, 196)
            x += rng.choice([-1, 1]) * 0.8
            y += rng.choice([0, 1])
    return speckle(img, rng, 0.06)


def frass():
    """Frass: fine pale wood dust and droppings left by beetle larvae and termites."""
    rng = random.Random("frass")
    img = [[shade((196, 170, 128), rng.uniform(0.85, 1.12)) for _ in range(S)] for _ in range(S)]
    for _ in range(26):
        img[rng.randrange(S)][rng.randrange(S)] = rng.choice([(150, 116, 80), (226, 206, 168), (120, 92, 64)])
    return img


def fibre():
    """A collapsed baobab: tangled wet fibres, pale tan strings over darker soggy pulp."""
    rng = random.Random("fibre")
    img = [[shade((122, 96, 70), rng.uniform(0.85, 1.1)) for _ in range(S)] for _ in range(S)]
    for _ in range(9):
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        dx, dy = math.cos(a := rng.uniform(0, math.pi)), math.sin(a)
        for _ in range(rng.randint(6, 12)):
            img[int(y) % S][int(x) % S] = shade((206, 184, 140), rng.uniform(0.9, 1.08))
            x, y = x + dx, y + dy * 0.6 + rng.uniform(-0.4, 0.4)
    return img


# ---- Leaves and plants (None is a see-through pixel) ----

LEAF = {"umbrella": (128, 156, 72), "camel": (124, 146, 108), "baobab": (64, 112, 44)}
# A dead tree's withered leaves (M7.5 step 5b): the same leaflets dried to straw brown (the baobab's darker).
DEAD_LEAF = {"umbrella": (158, 122, 68), "camel": (146, 118, 84), "baobab": (124, 90, 50)}


def leaves(kind, look):
    rng = random.Random(f"leaf{kind}{look}")
    base = LEAF[kind]
    img = blank(None)
    if look == "A":
        # Vanilla-style clumps: mostly filled, a few holes.
        n = noise(f"ln{kind}", 4)
        for y in range(S):
            for x in range(S):
                if rng.random() < (0.82 if kind != "baobab" else 0.75):
                    img[y][x] = shade(base, 0.75 + 0.45 * n(x, y))
        return img
    if kind == "baobab":
        # Hand-shaped leaves: five leaflets fanned from a point, dark glossy green.
        for _ in range(5):
            cx, cy = rng.randrange(S), rng.randrange(S)
            for i in range(5):
                a = -math.pi / 2 + (i - 2) * 0.6 + rng.uniform(-0.1, 0.1)
                for t in range(1, 5):
                    x, y = int(round(cx + math.cos(a) * t)) % S, int(round(cy + math.sin(a) * t)) % S
                    img[y][x] = shade(base, 0.9 + 0.08 * t + rng.uniform(-0.05, 0.05))
        return img
    # Feathery leaves: thin dark stems with tiny paired leaflets either side; lots of sky between.
    stem = shade(base, 0.6)
    for _ in range(6 if kind == "umbrella" else 9):
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        a = rng.uniform(0, math.pi * 2)
        for t in range(rng.randint(5, 8)):
            px, py = int(x) % S, int(y) % S
            img[py][px] = stem
            for side in (-1, 1):
                lx, ly = int(x - math.sin(a) * side) % S, int(y + math.cos(a) * side) % S
                if img[ly][lx] is None:
                    img[ly][lx] = shade(base, rng.uniform(0.9, 1.2))
            x, y = x + math.cos(a), y + math.sin(a)
    return img


def leaves_c(kind, base=None):
    """C, between A (too dense, the user) and B (too thin): B's fine leaflets, more of them and fuller, about two thirds
    leaf and one third sky; the baobab's hand-shaped leaves twice as many. {@code base}: another colour (withered)."""
    rng = random.Random(f"leafC{kind}")
    base = base or LEAF[kind]
    img = blank(None)
    if kind == "baobab":
        for _ in range(8):
            cx, cy = rng.randrange(S), rng.randrange(S)
            for i in range(5):
                a = -math.pi / 2 + (i - 2) * 0.6 + rng.uniform(-0.15, 0.15)
                for t in range(1, 5):
                    for w in (0, 1) if t > 1 else (0,):
                        x = int(round(cx + math.cos(a) * t - math.sin(a) * w * 0.7)) % S
                        y = int(round(cy + math.sin(a) * t + math.cos(a) * w * 0.7)) % S
                        img[y][x] = shade(base, 0.85 + 0.08 * t + rng.uniform(-0.06, 0.06))
    else:
        stem = shade(base, 0.62)
        for _ in range(10 if kind == "umbrella" else 12):
            x, y = rng.uniform(0, S), rng.uniform(0, S)
            a = rng.uniform(0, math.pi * 2)
            for t in range(rng.randint(5, 8)):
                img[int(y) % S][int(x) % S] = stem
                for side in (-1, 1):
                    for reach in (1, 2):
                        if reach == 2 and rng.random() < 0.7:
                            continue
                        lx, ly = int(x - math.sin(a) * side * reach) % S, int(y + math.cos(a) * side * reach) % S
                        if img[ly][lx] is None:
                            img[ly][lx] = shade(base, rng.uniform(0.85, 1.2))
                x, y = x + math.cos(a), y + math.sin(a)
    return img


def thorns():
    """A thorny twig: a zigzag red-brown twig, a pair of long straight white spines at each joint, small hooks."""
    img = blank(None)
    twig, spine, hook = (110, 64, 46), (236, 232, 218), (60, 46, 40)
    x = 7
    for y in range(15, 0, -1):
        img[y][x] = twig
        if y % 4 == 1:
            for t in range(1, 5):
                if 0 <= x - t < S and y - t // 2 >= 0:
                    img[y - t // 2][x - t] = spine
                if 0 <= x + t < S and y - t // 2 >= 0:
                    img[y - t // 2][x + t] = spine
            x += 1 if (y // 4) % 2 else -1
        elif y % 4 == 3:
            img[y][x + 1] = hook
    return img


def seedling():
    """A young umbrella thorn: a thin red stem, a few feathery leaves, the first white spines."""
    img = blank(None)
    rng = random.Random("seedling")
    for y in range(8, 16):
        img[y][8] = (120, 60, 44)
    for cy, side in [(8, 1), (10, -1), (12, 1)]:
        for t in range(1, 6):
            img[cy - t // 3][8 + side * t] = (120, 60, 44) if t == 1 else shade(LEAF["umbrella"], rng.uniform(0.9, 1.15))
            if t > 1 and rng.random() < 0.6:
                img[cy - t // 3 - 1][8 + side * t] = shade(LEAF["umbrella"], rng.uniform(0.95, 1.2))
    for y in (11, 13):
        img[y][7] = img[y][9] = (236, 232, 218)
    for x in range(5, 12):
        img[7 - abs(x - 8) // 2][x] = shade(LEAF["umbrella"], 1.1)
    return img


def pods():
    """Camel thorn pods: thick grey velvety half-moons, hanging two or three from a twig."""
    img = blank(None)
    stalk, fur, edge = (90, 70, 56), (160, 158, 146), (118, 116, 108)
    for x in range(2, 14):
        img[0][x] = stalk
    for cx in (4, 11):
        for t in range(10):
            a = t / 9 * math.pi * 0.9
            px, py = int(round(cx + math.cos(a + math.pi) * 2.5 + 2.5)), int(round(1 + math.sin(a) * 10))
            for w in (-1, 0, 1):
                if 0 <= px + w < S and 0 <= py < S:
                    img[py][px + w] = fur if w <= 0 else edge
    return img


def savanna_grass():
    """The ground: Minecraft's savanna grass colour, a little uneven."""
    rng = random.Random("ground")
    n = noise("groundn", 4)
    return [[shade((176, 168, 82), 0.85 + 0.25 * n(x, y) + rng.uniform(-0.04, 0.04)) for x in range(S)] for y in range(S)]


def all_textures():
    """Every texture the tree viewer needs: name -> rows of pixels (or ("file", name) for one already in the game)."""
    out = {}
    for kind in KINDS:
        for look in LOOKS:
            out[f"bark_{kind}_{look}"] = bark(kind, look)
            out[f"leaves_{kind}_{look}"] = leaves(kind, look)
        out[f"leaves_{kind}_C"] = leaves_c(kind)
        out[f"bark_{kind}_C"] = bark_soft(kind)
        out[f"bark_{kind}_D"] = bark_plates(kind)
        out[f"end_{kind}"] = wood_end(kind)
        out[f"inner_{kind}"] = wood_inner(kind)
    out["deadbark_E"] = dead_bark_silver(0)
    out["deadbark_D"] = dead_bark_grey()
    out["peeling"] = peeling_bark()
    out["deadinner"] = dead_inner()
    out["rotten"] = rotten_wood()
    out["frass"] = frass()
    out["fibre"] = fibre()
    out["thorns"] = thorns()
    out["seedling"] = seedling()
    out["pods"] = pods()
    out["ground"] = savanna_grass()
    out["mud"] = ("file", "mud_crust_dry_0.png")
    out["branch"] = ("file", "fallen_branch_bark_0.png")
    out["dead_end"] = ("file", "dead_wood_top_0.png")
    return out


# --- The game's files (M7.5 step 1, the user's picks, docs/DESIGN.md "Giant savanna trees") --------------------------

ASSETS = ROOT / "src/main/resources/assets/antcolonies"
DATA = ROOT / "src/main/resources/data/antcolonies"
GAME_NAMES = {"umbrella": "umbrella_thorn", "camel": "camel_thorn", "baobab": "baobab"}
PICKED_BARK = {"umbrella": "D", "camel": "C", "baobab": "B"}  # the user's picks, 2026-10-18


def picked_bark(kind):
    look = PICKED_BARK[kind]
    return bark_plates(kind) if look == "D" else bark_soft(kind) if look == "C" else bark(kind, look)


def seedling_for(kind):
    """The seedling in each kind's leaf colour; a baobab seedling has a green-grey stem and no spines."""
    img = [list(row) for row in seedling()]
    stem, spine = (120, 60, 44), (236, 232, 218)
    for y in range(S):
        for x in range(S):
            px = img[y][x]
            if px is None or kind == "umbrella":
                continue
            if px == spine:
                img[y][x] = None if kind == "baobab" else spine
            elif px == stem:
                img[y][x] = (112, 116, 84) if kind == "baobab" else (96, 70, 58)
            else:  # a leaf pixel: the same lightness in this kind's leaf colour
                f = sum(px) / max(1, sum(LEAF["umbrella"]))
                img[y][x] = shade(LEAF[kind], f)
    return img


def png(rows):
    import struct
    import zlib
    raw = b"".join(b"\x00" + bytes(c for px in row for c in ((0, 0, 0, 0) if px is None else tuple(px)[:3] + (255,))) for row in rows)

    def chunk(name, data):
        return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def write_json(path, data):
    import json
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + chr(10), encoding="utf-8")


def texture(name, rows):
    out = ASSETS / f"textures/block/{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(png(rows))


def model(name, data):
    write_json(ASSETS / f"models/block/{name}.json", data)


def item_from_block(name, model_name=None):
    write_json(ASSETS / f"models/item/{name}.json", {"parent": f"antcolonies:block/{model_name or name}"})


def flat_item(name, tex):
    write_json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"antcolonies:block/{tex}"}})


def loot(name, pools):
    write_json(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": pools})


def drops_itself(name):
    loot(name, [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"antcolonies:{name}"}],
                 "conditions": [{"condition": "minecraft:survives_explosion"}]}])


SHEARS_OR_SILK = {"condition": "minecraft:any_of", "terms": [
    {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
    {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}


def log(name, side, end):
    """A log (bark round the sides, the cut end on top and bottom, turned by how it was placed) and its blockstate."""
    model(name, {"parent": "minecraft:block/cube_column", "textures": {"side": side, "end": end}})
    model(f"{name}_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"side": side, "end": end}})
    write_json(ASSETS / f"blockstates/{name}.json", {"variants": {
        "axis=y": {"model": f"antcolonies:block/{name}"},
        "axis=z": {"model": f"antcolonies:block/{name}_horizontal", "x": 90},
        "axis=x": {"model": f"antcolonies:block/{name}_horizontal", "x": 90, "y": 90}}})
    item_from_block(name)
    drops_itself(name)


def single(name, model_data, item=None):
    model(name, model_data)
    write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"antcolonies:block/{name}"}}})
    if item is None:
        item_from_block(name)
    else:
        flat_item(name, item)


def cross(name, tex):
    single(name, {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": f"antcolonies:block/{tex}"}}, item=tex)


def branch(name, bark_tex, end_tex):
    """A thin branch, 6 pixels thick, joining every way it touches wood or leaves (like Minecraft's chorus plant)."""
    textures = {"particle": bark_tex, "side": bark_tex, "end": end_tex}
    model(f"{name}_core", {"textures": textures, "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": {
        f: {"texture": "#side", "uv": [5, 5, 11, 11]} for f in ("north", "south", "east", "west", "up", "down")}}]})
    arm_faces = {f: {"texture": "#side", "uv": [5, 0, 11, 5]} for f in ("east", "west", "up", "down")}
    arm_faces["north"] = {"texture": "#end", "uv": [5, 5, 11, 11], "cullface": "north"}
    model(f"{name}_arm", {"textures": textures, "elements": [{"from": [5, 5, 0], "to": [11, 11, 5], "faces": arm_faces}]})
    model(f"{name}_inventory", {"parent": "minecraft:block/block", "textures": textures, "elements": [{"from": [5, 5, 0], "to": [11, 11, 16], "faces": {
        "north": {"texture": "#end", "uv": [5, 5, 11, 11]}, "south": {"texture": "#end", "uv": [5, 5, 11, 11]},
        "east": {"texture": "#side", "uv": [0, 5, 16, 11]}, "west": {"texture": "#side", "uv": [0, 5, 16, 11]},
        "up": {"texture": "#side", "uv": [5, 0, 11, 16]}, "down": {"texture": "#side", "uv": [5, 0, 11, 16]}}}]})
    turn = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"apply": {"model": f"antcolonies:block/{name}_core"}}]
    for d, r in turn.items():
        parts.append({"when": {d: "true"}, "apply": dict({"model": f"antcolonies:block/{name}_arm"}, **r)})
    write_json(ASSETS / f"blockstates/{name}.json", {"multipart": parts})
    item_from_block(name, f"{name}_inventory")
    drops_itself(name)


def game_files():
    """Every block of the giant savanna trees: textures, models, blockstates, item models and loot."""
    for kind in KINDS:
        g = GAME_NAMES[kind]
        texture(f"{g}_bark", picked_bark(kind))
        texture(f"{g}_log_top", wood_end(kind))
        texture(f"{g}_leaves", leaves_c(kind))
        texture(f"{g}_seedling", seedling_for(kind))
        bark_tex, end_tex = f"antcolonies:block/{g}_bark", f"antcolonies:block/{g}_log_top"
        log(f"{g}_log", bark_tex, end_tex)
        log(f"{g}_wood", bark_tex, bark_tex)
        branch(f"{g}_branch", bark_tex, end_tex)
        single(f"{g}_leaves", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                               "textures": {"all": f"antcolonies:block/{g}_leaves"}})
        # Leaves: themselves with shears or silk touch; otherwise now and then a seedling (1 in 20) or a stick (1 in 50).
        loot(f"{g}_leaves", [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"antcolonies:{g}_leaves", "conditions": [SHEARS_OR_SILK]},
            {"type": "minecraft:item", "name": f"antcolonies:{g}_seedling", "conditions": [
                {"condition": "minecraft:survives_explosion"}, {"condition": "minecraft:random_chance", "chance": 0.05}]},
            {"type": "minecraft:item", "name": "minecraft:stick", "conditions": [
                {"condition": "minecraft:survives_explosion"}, {"condition": "minecraft:random_chance", "chance": 0.02}]}]}]}])
        cross(f"{g}_seedling", f"{g}_seedling")
        drops_itself(f"{g}_seedling")
        # Withered leaves of a dead tree (step 5b): themselves with shears or silk touch, otherwise now and then a stick.
        texture(f"{g}_dead_leaves", leaves_c(kind, DEAD_LEAF[kind]))
        single(f"{g}_dead_leaves", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                                    "textures": {"all": f"antcolonies:block/{g}_dead_leaves"}})
        loot(f"{g}_dead_leaves", [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"antcolonies:{g}_dead_leaves", "conditions": [SHEARS_OR_SILK]},
            {"type": "minecraft:item", "name": "minecraft:stick", "conditions": [
                {"condition": "minecraft:survives_explosion"}, {"condition": "minecraft:random_chance", "chance": 0.02}]}]}]}])
    # Dead wood: bleached silver bark, the old dead wood's cut end.
    texture("bleached_bark", dead_bark_silver(0))
    texture("peeling_bleached_bark", peeling_bark())
    dead_end = "antcolonies:block/dead_wood_top_0"
    log("bleached_log", "antcolonies:block/bleached_bark", dead_end)
    log("bleached_wood", "antcolonies:block/bleached_bark", "antcolonies:block/bleached_bark")
    log("peeling_bleached_log", "antcolonies:block/peeling_bleached_bark", dead_end)
    branch("bleached_branch", "antcolonies:block/bleached_bark", dead_end)
    for name, rows in (("rotten_wood", rotten_wood()), ("baobab_fibre", fibre())):
        texture(name, rows)
        single(name, {"parent": "minecraft:block/cube_all", "textures": {"all": f"antcolonies:block/{name}"}})
        drops_itself(name)
    texture("frass", frass())
    sides = {f: {"uv": [0, 14, 16, 16], "texture": "#texture", "cullface": f} for f in ("north", "south", "east", "west")}
    single("frass", {"parent": "minecraft:block/thin_block", "textures": {"particle": "antcolonies:block/frass", "texture": "antcolonies:block/frass"},
                     "elements": [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": dict({
                         "down": {"uv": [0, 0, 16, 16], "texture": "#texture", "cullface": "down"},
                         "up": {"uv": [0, 0, 16, 16], "texture": "#texture"}}, **sides)}]})
    drops_itself("frass")
    texture("thorny_twig", thorns())
    cross("thorny_twig", "thorny_twig")
    # A thorny twig gives itself only to shears or silk touch (my pick, to confirm); broken by hand it gives nothing.
    loot("thorny_twig", [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "antcolonies:thorny_twig"}],
                          "conditions": [SHEARS_OR_SILK]}])
    texture("camel_thorn_pods", pods())
    cross("camel_thorn_pods", "camel_thorn_pods")
    drops_itself("camel_thorn_pods")
    print("wrote the giant savanna tree blocks")


if __name__ == "__main__":
    game_files()
