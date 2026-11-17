"""River sand, pebbles and the crocodile's nest (M9 river life, the user, 2026-11-16: sand banks for the crocodiles to
lay their eggs on; "would the sand look different in color depending on how far from the coast it is"; "pebbles on the
sand would also be a good addition"; "I do want the pebbles to be 3D models ... 7 variations, different sizes, amounts,
because they will also line the river bed"). Looks to pick from in the model viewer (python tools/model_viewer.py sand,
tools/sand_viewer.template.html). Only drawn here; nothing goes into the mod until the user picks.

The real rule behind the tones (animals and land are fun first, but this is how real rivers sort their load): a river
drops its heavy load first. Far inland the water runs fast, the bars are coarse and pebbly and stained by the red savanna
soil they wash out of; mid inland the classic golden sand bar; near the coast only fine grains arrive, washed pale.
  far    red-ochre coarse sand with grit (far inland)
  mid    golden sand (mid inland, the classic crocodile bank)
  coast  pale fine sand (near the coast)
Each tone comes in: dry, wet (the darker band at the waterline; sand dug up from below is damp and dark too, so a fresh
nest shows it), ripple (the current's waves on the bed under shallow water), slide (a crocodile's belly-drag path down
the bank into the water, a smooth polished groove with claw scratches at its sides).

Textures are Minecraft's own sand pattern recoloured, so they sit beside vanilla blocks.

Pebbles: 3D, seven looks (the user's ask), each kept to 12 boxes or fewer so a river bed lined with them stays cheap
(blocks are drawn into the chunk once, not every frame like a mob; the cost is the boxes). River pebbles are smooth,
rounded and flattish: each pebble is a body box with a smaller box on top to round it. Turns are only the steps a
Minecraft block model allows (-45, -22.5, 0, 22.5, 45 degrees about the up axis), so the viewer shows what the game can.
Colours mixed: grey, dark grey, ochre, rust, tan and the odd white quartz.

The nest (crocodile, generalized): the mother digs a hole in the sand above the waterline, lays her eggs, scrapes sand
back over them and guards it. Covered: a low loose heap of damp dug-up sand. Dug open (hatching time, or a raider):
a pit with the white eggs showing. Eggs drawn bigger than life so they are seen (my pick, like the droppings).

Boxes are in model pixels (16 a block), x and z round the block's middle, y up from the block's top.
"""
import math
import random
import zipfile
from pathlib import Path

import river_blocks_art as rba

S = 16
JAR = Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"

# tone ramps: (dark, mid, light), and how much of the vanilla pattern's contrast each keeps (coarse far, fine coast)
TONES = {
    "far": {"name": "Far inland: red-ochre, coarse", "ramp": ((146, 92, 58), (180, 120, 78), (206, 150, 102)), "contrast": 1.25},
    "mid": {"name": "Mid inland: golden", "ramp": ((178, 142, 90), (210, 176, 118), (230, 204, 150)), "contrast": 1.0},
    "coast": {"name": "Near the coast: pale, fine", "ramp": ((198, 184, 156), (224, 212, 186), (240, 233, 214)), "contrast": 0.6},
}
WET = 0.70                      # wet sand's darkness (my pick)


def clamp(c):
    return max(0, min(255, round(c)))


def _vanilla(name):
    w, h, px = rba.read_png(zipfile.ZipFile(JAR).read(f"assets/minecraft/textures/block/{name}.png"))
    return [[px[y * w + x] for x in range(w)] for y in range(h)]


def _lum_field():
    """Minecraft's sand pattern as brightness from -1 to 1 round its middle."""
    rows = _vanilla("sand")
    lum = [[0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2] for p in r] for r in rows]
    flat = [v for r in lum for v in r]
    mean, spread = sum(flat) / len(flat), max(abs(v - sum(flat) / len(flat)) for v in flat)
    return [[(v - mean) / spread for v in r] for r in lum]


LUM = None


def _ramp(tone, t):
    """Colour at brightness t (-1 dark, 0 mid, 1 light) on the tone's ramp."""
    dark, mid, light = TONES[tone]["ramp"]
    t = max(-1.0, min(1.0, t))
    a, b, k = (mid, light, t) if t >= 0 else (mid, dark, -t)
    return tuple(a[i] + (b[i] - a[i]) * k for i in range(3))


def _base(tone):
    global LUM
    if LUM is None:
        LUM = _lum_field()
    c = TONES[tone]["contrast"]
    return [[_ramp(tone, LUM[y][x] * c) for x in range(S)] for y in range(S)]


def _px(rows):
    return [[tuple(clamp(v) for v in p[:3]) + (255,) for p in r] for r in rows]


def _wetten(rows, seed):
    rng = random.Random(seed)
    out = [[tuple(v * WET for v in p) for p in r] for r in rows]
    for _ in range(3):                                          # a few glints of water in the grains
        x, y = rng.randrange(S), rng.randrange(S)
        out[y][x] = tuple(min(255, v * 1.35) for v in out[y][x])
    return out


def _grit(rows, tone, seed):
    """Far inland: coarse grit and tiny pebble grains through the sand."""
    if tone != "far":
        return rows
    rng = random.Random(seed)
    for _ in range(7):
        x, y = rng.randrange(S), rng.randrange(S)
        c = rng.choice(((96, 90, 84), (120, 74, 48), (150, 140, 128)))
        rows[y][x] = c
        if rng.random() < 0.5:
            rows[y][(x + 1) % S] = tuple(v * 1.25 for v in c)
    return rows


def dry(tone):
    return _px(_grit(_base(tone), tone, 11))


def wet(tone):
    return _px(_wetten(_grit(_base(tone), tone, 11), 21))


def ripple(tone):
    """The bed under shallow water: ripples across the current (the current runs along x)."""
    rows = _base(tone)
    out = []
    for y in range(S):
        line = []
        for x in range(S):
            ph = (x + 1.2 * math.sin(y * 2 * math.pi / S)) * 2 * math.pi / 4
            k = 0.86 if math.sin(ph) > 0.55 else 1.07 if math.sin(ph) < -0.75 else 1.0
            line.append(tuple(v * 0.92 * k for v in rows[y][x]))
        out.append(line)
    return _px(_grit(out, tone, 31))


def slide(tone, is_wet):
    """A crocodile's belly-drag path, running along y (down the bank): a smooth polished groove (the pattern
    flattened, a shade darker) with claw scratches at its sides."""
    rows = _base(tone)
    mid = _ramp(tone, 0)
    out = []
    for y in range(S):
        line = []
        for x in range(S):
            p = rows[y][x]
            d = abs(x - 7.5)
            if d < 4.5:                                         # the groove: smoothed, darker toward its middle
                k = 0.25 + 0.75 * (d / 4.5) ** 2
                p = tuple(mid[i] + (p[i] - mid[i]) * k for i in range(3))
                p = tuple(v * (0.9 + 0.06 * d / 4.5) for v in p)
            elif d < 5.5:                                       # the little raised lip the belly pushes up
                p = tuple(v * 1.06 for v in p)
            line.append(p)
        out.append(line)
    for y0 in range(1, S, 5):                                   # claw scratches, angled outward
        for side, x0 in ((-1, 2), (1, 13)):
            for k in range(3):
                x, y = x0 + side * (k // 2), (y0 + k) % S
                out[y][x] = tuple(v * 0.8 for v in out[y][x])
    if is_wet:
        out = _wetten(out, 41)
    return _px(out)


def nest_top(tone):
    """A covered nest's top: dry sand churned with damp dark sand dug up from below, and scrape marks."""
    rng = random.Random(51)
    base, damp = _base(tone), _wetten(_base(tone), 52)
    out = [[damp[y][x] if rng.random() < 0.45 else base[y][x] for x in range(S)] for y in range(S)]
    for i in range(4):                                          # scrape marks from the mother's feet
        y = 2 + i * 4
        for x in range(3, 13):
            if rng.random() < 0.8:
                out[y][x] = tuple(v * 0.82 for v in out[y][x])
    return _px(out)


def all_textures():
    out = {}
    for tone in TONES:
        out[tone] = {"dry": dry(tone), "wet": wet(tone), "ripple": ripple(tone), "slide": slide(tone, False),
                     "slideWet": slide(tone, True), "nest": nest_top(tone)}
    return out


# ---- pebbles ----

STONE = {"grey": (128, 126, 120), "dark": (90, 88, 84), "ochre": (170, 126, 78), "rust": (128, 84, 56),
         "tan": (186, 160, 122), "quartz": (228, 224, 214)}
TURNS = (-45.0, -22.5, 0.0, 22.5, 45.0)       # the only turns a Minecraft block model's box can have


def _hex(c):
    return "#%02x%02x%02x" % tuple(clamp(v) for v in c)


def pebble(x, z, w, d, h, colour, turn=0.0, sink=0.0, round_top=True):
    """One river pebble. Tiny ones are one box. Others have a body and a smaller top box that rounds it; stones 3 pixels
    or wider get their body split into two crossing boxes (one long and narrow, one short and wide), so they look round
    from above, not square (3 boxes). Returns its boxes."""
    col = STONE[colour]
    if not round_top:
        return [{"x": x, "y": h / 2 - sink, "z": z, "w": w, "h": h, "d": d, "c": _hex(col), "turn": turn}]
    body = h * 0.62
    top = {"x": x, "y": body + (h - body) / 2 - sink, "z": z, "w": max(0.5, w * 0.62), "h": h - body,
           "d": max(0.5, d * 0.62), "c": _hex(tuple(v * 1.07 for v in col)), "turn": turn}
    if min(w, d) < 3:
        return [{"x": x, "y": body / 2 - sink, "z": z, "w": w, "h": body, "d": d, "c": _hex(col), "turn": turn}, top]
    cut = min(w, d) * 0.22
    return [{"x": x, "y": body / 2 - sink, "z": z, "w": w, "h": body, "d": d - 2 * cut, "c": _hex(col), "turn": turn},
            {"x": x, "y": body / 2 - sink, "z": z, "w": w - 2 * cut, "h": body, "d": d,
             "c": _hex(tuple(v * 0.97 for v in col)), "turn": turn},
            top]


# The seven arrangements, written once (the user, 2026-11-16: "the variants of size and amount of them are perfect").
# Each stone: x, z (pixels round the block's middle), w, d, h (pixels), colour, turn (degrees), sink (pixels into the
# sand), tiny (a grain of a stone: one or two boxes).
def _s(x, z, w, d, h, colour, turn, sink=0.0, tiny=False):
    return {"x": x, "z": z, "w": w, "d": d, "h": h, "colour": colour, "turn": turn, "sink": sink, "tiny": tiny}


def _tiny(x, z, size, colour, turn):
    return _s(x, z, size, size * 0.8, size * 0.6, colour, turn, tiny=True)


STONES = [
    ("One big flat stone", [_s(0.5, 0.0, 8.0, 6.0, 2.4, "grey", 22.5)]),
    ("Two medium stones", [_s(-2.4, -1.0, 4.0, 3.0, 1.8, "ochre", -22.5), _s(2.6, 1.6, 3.6, 3.0, 1.6, "dark", 22.5)]),
    ("A medium stone and two small", [_s(-1.0, -0.5, 4.2, 3.2, 1.9, "tan", 0.0), _s(3.2, 2.4, 2.0, 1.6, 1.0, "grey", 45.0),
                                      _s(-4.4, 3.0, 1.8, 1.4, 0.9, "quartz", -22.5)]),
    ("A cluster of small pebbles", [_s(-1.6, -1.2, 2.4, 2.0, 1.2, "grey", 22.5), _s(1.2, -1.8, 2.0, 1.6, 1.1, "rust", -45.0),
                                    _s(0.2, 1.2, 2.2, 1.8, 1.2, "ochre", 0.0), _s(-2.8, 1.6, 1.6, 1.4, 0.9, "dark", 45.0)]),
    ("A scatter of tiny ones", [_tiny(-4.8, -3.2, 1.2, "grey", 22.5), _tiny(-1.0, -4.6, 1.0, "ochre", 0.0),
                                _tiny(3.6, -2.6, 1.3, "dark", -22.5), _tiny(-3.2, 2.0, 1.0, "quartz", 45.0),
                                _tiny(1.4, 0.6, 1.1, "tan", 0.0), _tiny(4.6, 3.8, 1.0, "rust", 22.5)]),
    ("A half-buried stone", [_s(0.0, 0.5, 7.0, 5.4, 2.8, "dark", -22.5, sink=1.3)]),
    ("A mixed handful", [_s(-2.0, 0.6, 3.4, 2.8, 1.6, "grey", 22.5), _s(2.2, -2.0, 2.0, 1.6, 1.0, "ochre", 0.0),
                         _s(2.8, 2.6, 1.8, 1.5, 0.9, "rust", -45.0), _tiny(-4.6, -3.4, 1.0, "quartz", 0.0),
                         _tiny(-0.4, -4.4, 0.9, "tan", 22.5), _tiny(-4.8, 4.0, 1.0, "dark", -22.5)]),
]


def _old(st):
    return pebble(st["x"], st["z"], st["w"], st["d"], st["h"], st["colour"], st["turn"], st["sink"], not st["tiny"])


PEBBLES = [(name, [b for st in stones for b in _old(st)]) for name, stones in STONES]   # the first look, kept to compare
MAX_BOXES = 12

# ---- three new stone looks (the user, 2026-11-16: "more detail, more pixel, more round") ----
# Each stone is a squashed egg built from layers: each layer a cross of two boxes (one long and narrow, one short and
# wide; three boxes on the biggest stones), so from above it reads round and from the side it curves in over the top.
# The textures are laid at true pixel size on every face (in the game: each face's UV the size of the face, so texels
# never stretch), from a little atlas of three parts: the sides, the tops (lighter, where the sun and the wear are) and a
# damp band where the stone sits in the sand. The UV picks the part per face; all of it is what a block model can do.
#   A  smooth and speckled: half-pixel steps and half-pixel texels (a 32 by 32 texture), grains of other minerals.
#   B  fine pixels: quarter-pixel steps and quarter-pixel texels (64 by 64), more layers, shaded rounder.
#   C  veined river stones: B's shapes, quartz veins through the grey, dark and rust stones, bands in the ochre and tan
#      ones, the quartz milky, a wet glint on top; quarter-pixel texels.
LOOKS = {
    "A": {"name": "A, smooth and speckled", "step": 0.5, "grid": 0.5, "cross": 1.5, "three": 99.0, "layers": 4, "dens": 2},
    "B": {"name": "B, fine pixels", "step": 0.25, "grid": 0.25, "cross": 1.25, "three": 4.0, "layers": 6, "dens": 4},
    "C": {"name": "C, veined river stones", "step": 0.25, "grid": 0.25, "cross": 1.25, "three": 4.0, "layers": 6, "dens": 4},
}
NEW_MAX_BOXES = 24
STONE_GAME_LOOK = "B"  # the user's pick, 2026-11-16: fine pixels
CROSS2 = ((1.0, 0.72), (0.72, 1.0))
CROSS3 = ((1.0, 0.56), (0.9, 0.86), (0.56, 1.0))


def _snap(v, grid):
    return max(grid, round(v / grid) * grid)


def round_stone(st, look, seed):
    """One stone in a new look: a list of boxes (x, y, z, w, h, d in pixels, y the box's middle up from the sand)."""
    L = LOOKS[look]
    rng = random.Random(seed)
    room = max(0.0, (16 - max(st["w"], st["d"])) / 2 - 1)
    if look == "A":
        ox, oz = rng.uniform(-room, room), rng.uniform(-room, room)
    else:
        ox, oz = rng.uniform(-1, 1), rng.uniform(-1, 1)
    base = {"x": st["x"], "z": st["z"], "c": st["colour"], "turn": st["turn"], "ox": ox, "oz": oz}
    top = st["h"] - st["sink"]
    if st["tiny"]:
        g = L["grid"]
        w, d = _snap(st["w"], g), _snap(st["d"], g)
        body = top * 0.6
        return [dict(base, y=body / 2, w=w, h=body, d=d, damp=True, cap=False),
                dict(base, y=body + (top - body) / 2, w=max(g, w - 2 * g), h=top - body, d=max(g, d - 2 * g),
                     damp=False, cap=True)]
    # a squashed egg bedded in the sand: its full height runs from 70 percent of the stone's height under the sand line
    # to the top, so it is widest just over the sand and curves in all the way up, like a pebble half sunk in a bar
    bottom = -st["sink"] - 0.7 * st["h"]
    yc, a = (top + bottom) / 2, (top - bottom) / 2
    size = max(st["w"], st["d"])
    most = L["layers"] if size >= 5 else min(L["layers"], 4 if size >= 3 else 3)   # small stones need fewer layers
    n = min(most, max(2, math.ceil(top / L["step"] - 0.2)))
    layers = []
    # layers spaced round the curve: thin near the top where it bends most, thick lower down
    t0 = math.asin(max(-1.0, -yc / a))
    edges = [yc + a * math.sin(t0 + (math.pi / 2 - t0) * i / n) for i in range(n + 1)]
    edges[0], edges[-1] = 0.0, top
    for i in range(n):
        y0, y1 = edges[i], edges[i + 1]
        t = abs(((y0 + y1) / 2 - yc) / a)
        k = math.sqrt(max(0.0, 1 - t * t))
        w, d = _snap(st["w"] * k, L["grid"]), _snap(st["d"] * k, L["grid"])
        if layers and layers[-1][2:] == (w, d):
            layers[-1] = (layers[-1][0], y1, w, d)          # the same size as the layer under it: one taller box
        else:
            layers.append((y0, y1, w, d))
    boxes = []
    for j, (y0, y1, w, d) in enumerate(layers):
        m = min(w, d)
        parts = CROSS3 if m >= L["three"] else CROSS2 if m >= L["cross"] else ((1.0, 1.0),)
        for fw, fd in parts:
            boxes.append(dict(base, y=(y0 + y1) / 2, h=y1 - y0, w=_snap(w * fw, L["grid"]), d=_snap(d * fd, L["grid"]),
                              damp=y0 <= 0.01, cap=j == len(layers) - 1))
    return boxes


def new_looks():
    out = []
    for key, L in LOOKS.items():
        looks = []
        for li, (name, stones) in enumerate(STONES):
            boxes = [b for si, st in enumerate(stones) for b in round_stone(st, key, f"{key}{li}{si}")]
            assert len(boxes) <= NEW_MAX_BOXES, (key, name, len(boxes))
            looks.append({"name": name, "boxes": [dict(b, turn=math.radians(b["turn"])) for b in boxes]})
        out.append({"key": key, "name": L["name"], "dens": L["dens"], "looks": looks})
    return out


def _noise(seed, scale):
    """Smooth value noise in pixel units (smoothed between hashed lattice points)."""
    rng = random.Random(seed)
    grid = [[rng.random() for _ in range(40)] for _ in range(40)]

    def g(a, b):
        return grid[b % 40][a % 40]

    def f(u, v):
        u, v = u / scale + 7, v / scale + 7
        i, j = int(u), int(v)
        fu, fv = u - i, v - j
        fu, fv = fu * fu * (3 - 2 * fu), fv * fv * (3 - 2 * fv)
        return (g(i, j) * (1 - fu) + g(i + 1, j) * fu) * (1 - fv) + (g(i, j + 1) * (1 - fu) + g(i + 1, j + 1) * fu) * fv
    return f


VEIN = (222, 216, 202)


def stone_texture(look, colour):
    """The stone's atlas: 2 by 2 parts of 16 pixels each (sides, tops, damp band, spare), texels 1/dens of a pixel.
    In a part, u runs along x (or z) and v down from 12 pixels over the sand line on the sides, so a side's row is a
    height and the shading can follow the stone's curve."""
    L = LOOKS[look]
    dens = L["dens"]
    R = 16 * dens
    col = STONE[colour]
    rng = random.Random(f"tex{look}{colour}")
    smooth = _noise(f"n{look}{colour}", 2.2)
    big = _noise(f"b{look}{colour}", 5.0)
    veins = []
    if look == "C" and colour in ("grey", "dark", "rust"):
        for _ in range({"grey": 3, "dark": 2, "rust": 1}[colour]):    # each passes near the stone's middle and sides
            t = rng.uniform(0.3, math.pi - 0.3)
            cx, cz = math.cos(t), math.sin(t)
            veins.append((cx, cz, 8 * cx + 9.5 * cz + rng.uniform(-2.5, 2.5), rng.uniform(0.3, 0.55)))
    band = rng.uniform(0, math.pi)
    rows = [[(0, 0, 0, 0)] * (2 * R) for _ in range(2 * R)]
    for part, (pc, pr) in (("side", (0, 0)), ("top", (1, 0)), ("damp", (0, 1)), ("spare", (1, 1))):
        for j in range(R):
            for i in range(R):
                u, v = (i + 0.5) / dens, (j + 0.5) / dens
                grain = rng.random()
                if look == "A":
                    k = 1 + 0.06 * (rng.random() - 0.5) * 2 + 0.05 * (big(u, v) - 0.5) * 2
                else:
                    k = 1 + 0.09 * (smooth(u, v) - 0.5) * 2 + 0.05 * (big(u, v) - 0.5) * 2 + 0.03 * (rng.random() - 0.5)
                if part in ("top", "spare"):
                    k *= 1.10 if look == "A" else 1.04 + 0.14 * max(0.0, 1 - math.hypot(u - 8, v - 8) / 4.5)
                elif part == "side":
                    lo = 0.10 if look == "A" else 0.2
                    k *= 1.05 - lo * min(1.0, max(0.0, (v - 9.5) / 3.0))      # lighter up the curve, darker down
                c = [x * k for x in col]
                if colour == "quartz":
                    milk = big(u + 30, v)
                    c = [x * (0.94 + 0.06 * milk) for x in c]
                    if milk > 0.7 and look != "A":
                        c = [c[0], c[1] * 0.95, c[2] * 0.93]       # a faint pink cloud
                speck = 1 if look == "A" else 0.4                 # grains of other minerals
                if grain < 0.08 * speck:
                    c = [x * 0.66 for x in c]
                elif grain < 0.13 * speck:
                    c = [min(255, x * 1.22) for x in c]
                elif grain < 0.15 * speck:
                    c = [x * (0.5 if colour in ("tan", "quartz", "ochre") else 0.7) for x in c]
                if look == "C":
                    if colour in ("ochre", "tan"):
                        t = u * math.cos(band) + v * math.sin(band)
                        c = [x * (1 + 0.06 * (1 if math.sin(t * 2 * math.pi / 2.6) > 0 else -1)) for x in c]
                    for cx, cz, off, width in veins:
                        if abs(u * cx + v * cz - off - 2.5 * (smooth(u + 9, v) - 0.5)) < width:
                            c = [x * 0.25 + y * 0.75 for x, y in zip(c, VEIN)]
                if part == "damp":
                    mean = sum(c) / 3
                    c = [(mean + (x - mean) * 1.12) * 0.76 for x in c]
                rows[pr * R + j][pc * R + i] = tuple(clamp(x) for x in c) + (255,)
    if look == "C":                                   # a wet glint or two near the middle of the tops
        for _ in range(3):
            gi, gj = R + int(rng.uniform(6.5, 9.5) * dens), int(rng.uniform(6.5, 9.5) * dens)
            rows[gj][gi] = (250, 250, 244, 255)
    return rows


def stone_textures():
    return {key: {colour: stone_texture(key, colour) for colour in STONE} for key in LOOKS}


def all_pebbles():
    out = []
    for name, boxes in PEBBLES:
        assert len(boxes) <= MAX_BOXES, (name, len(boxes))
        for b in boxes:
            assert b["turn"] in TURNS, (name, b["turn"])
        out.append({"name": name, "boxes": [dict(b, turn=math.radians(b["turn"])) for b in boxes]})
    return out


# ---- the nest ----

EGG = (240, 236, 224)


def _egg(x, z, turn):
    """An egg lying on its side, about 3 pixels long (bigger than life to be seen; my pick)."""
    return [{"x": x, "y": 1.0, "z": z, "w": 3.0, "h": 2.0, "d": 2.2, "c": _hex(EGG), "turn": turn},
            {"x": x, "y": 1.0, "z": z, "w": 2.0, "h": 2.4, "d": 2.6, "c": _hex(tuple(v * 0.97 for v in EGG)), "turn": turn}]


def nest():
    """Covered: a low loose heap on the sand (boxes of the nest texture). Open: a pit 5 pixels deep with a heaped rim,
    eggs at its bottom. y is from the block's top; 'sand' boxes use the nest texture, others their colour."""
    covered = [{"x": 0, "y": 0.6, "z": 0, "w": 12, "h": 1.2, "d": 11, "c": "sand", "turn": 0},
               {"x": 0.5, "y": 1.6, "z": -0.3, "w": 8, "h": 0.8, "d": 7, "c": "sand", "turn": 0},
               {"x": -3.5, "y": 1.3, "z": 4.2, "w": 3, "h": 0.6, "d": 2, "c": "sand", "turn": 0}]
    rim = [{"x": 0, "y": 0.6, "z": -5.5, "w": 13, "h": 1.2, "d": 3, "c": "sand", "turn": 0},
           {"x": 0, "y": 0.6, "z": 5.5, "w": 13, "h": 1.2, "d": 3, "c": "sand", "turn": 0},
           {"x": -5.5, "y": 0.6, "z": 0, "w": 3, "h": 1.2, "d": 8, "c": "sand", "turn": 0},
           {"x": 5.5, "y": 0.9, "z": 0, "w": 3, "h": 1.8, "d": 8, "c": "sand", "turn": 0}]   # the side dug from, higher
    eggs = []
    rng = random.Random(61)
    for x, z in ((-2.2, -1.8), (0.8, -2.0), (2.8, 0.2), (-2.6, 1.2), (0.0, 0.6), (1.6, 2.4), (-0.8, 2.8), (-3.0, -0.2)):
        eggs += _egg(x, z, math.radians(rng.choice(TURNS)))
    for x, z in ((-0.8, -0.6), (1.2, 0.8)):                     # a second layer showing on top
        eggs += [dict(b, y=b["y"] + 1.9) for b in _egg(x, z, math.radians(rng.choice(TURNS)))]
    return {"covered": covered, "open": {"rim": rim, "eggs": eggs, "pit": {"w": 8, "d": 8, "depth": 5}}}


# ---- into the game (sand banks step 2, 2026-11-16) ----
# python tools/sand_art.py game   writes the textures, models and blockstates of the river sand (dry, wet, rippled and
# the crocodile slide in three tones) and the river pebbles (look B, the user's pick, seven arrangements) into the
# savanna mod. The block names give the tones plain colour words: far inland "red", mid inland "golden", coast "pale".

import json
import sys

GAME_TONES = {"far": "red", "mid": "golden", "coast": "pale"}
ASSETS = Path(__file__).resolve().parent.parent / "savanna/src/main/resources/assets/antcolonies"


def _write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def _write_tex(name, rows):
    path = ASSETS / "textures/block" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    rba.write_png(str(path), len(rows[0]), len(rows), [p for r in rows for p in r])


def _cube(name, textures, parent="minecraft:block/cube_all"):
    _write_json(ASSETS / "models/block" / f"{name}.json", {"parent": parent, "textures": textures})
    _write_json(ASSETS / "models/item" / f"{name}.json", {"parent": f"antcolonies:block/{name}"})


def _single(name):
    _write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"antcolonies:block/{name}"}}})


def _uv_clamp(v):
    return round(max(0.01, min(7.99, v)), 4)


def _pebble_element(b):
    """One box of a look-B stone as a block model element. The atlas (stone_texture) is 2 by 2 parts of 16 pixels, each
    part 8 UV units wide: sides top-left, tops top-right, the damp band bottom-left. Texels keep their true size."""
    x0, x1 = 8 + b["x"] - b["w"] / 2, 8 + b["x"] + b["w"] / 2
    z0, z1 = 8 + b["z"] - b["d"] / 2, 8 + b["z"] + b["d"] / 2
    y0, y1 = b["y"] - b["h"] / 2, b["y"] + b["h"] / 2
    tex = "#" + b["c"]
    side_v0 = 8 if b["damp"] else 0
    vs = [round(side_v0 + _uv_clamp((12 - y1) / 2), 4), round(side_v0 + _uv_clamp((12 - y0) / 2), 4)]

    def along(a0, a1, shift):
        return [_uv_clamp((a0 - 8 + shift + 8) / 2), _uv_clamp((a1 - 8 + shift + 8) / 2)]

    ux = along(x0, x1, b["ox"])
    uz = along(z0, z1, b["oz"])
    top = [round(8 + ux[0], 4), uz[0], round(8 + ux[1], 4), uz[1]]
    faces = {
        "up": {"uv": top, "texture": tex},
        "north": {"uv": [ux[0], vs[0], ux[1], vs[1]], "texture": tex},
        "south": {"uv": [ux[0], vs[0], ux[1], vs[1]], "texture": tex},
        "west": {"uv": [uz[0], vs[0], uz[1], vs[1]], "texture": tex},
        "east": {"uv": [uz[0], vs[0], uz[1], vs[1]], "texture": tex},
    }
    if y0 > 0.01:
        faces["down"] = {"uv": [ux[0], round(8 + uz[0], 4), ux[1], round(8 + uz[1], 4)], "texture": tex}
    el = {"from": [round(x0, 4), round(y0, 4), round(z0, 4)], "to": [round(x1, 4), round(y1, 4), round(z1, 4)],
          "faces": faces}
    if b["turn"]:
        el["rotation"] = {"origin": [round(8 + b["x"], 4), round(y0, 4), round(8 + b["z"], 4)], "axis": "y",
                          "angle": b["turn"]}
    return el


def game():
    for tone, word in GAME_TONES.items():
        _write_tex(f"{word}_river_sand", dry(tone))
        _write_tex(f"wet_{word}_river_sand", wet(tone))
        _write_tex(f"rippled_{word}_river_sand", ripple(tone))
        _write_tex(f"{word}_crocodile_slide", slide(tone, False))
        _write_tex(f"wet_{word}_crocodile_slide", slide(tone, True))
        for name in (f"{word}_river_sand", f"wet_{word}_river_sand", f"rippled_{word}_river_sand"):
            _cube(name, {"all": f"antcolonies:block/{name}"})
            _single(name)
        slide_name = f"{word}_crocodile_slide"
        for wetness, prefix, sand in ((False, "", f"{word}_river_sand"), (True, "wet_", f"wet_{word}_river_sand")):
            model = f"{prefix}{slide_name}"
            _write_json(ASSETS / "models/block" / f"{model}.json", {
                "parent": "minecraft:block/cube_bottom_top",
                "textures": {"top": f"antcolonies:block/{prefix}{slide_name}", "side": f"antcolonies:block/{sand}",
                             "bottom": f"antcolonies:block/{sand}"}})
        _write_json(ASSETS / "models/item" / f"{slide_name}.json", {"parent": f"antcolonies:block/{slide_name}"})
        variants = {}
        for axis, rot in (("z", 0), ("x", 90)):
            for wetness in ("false", "true"):
                v = {"model": f"antcolonies:block/{'wet_' if wetness == 'true' else ''}{slide_name}"}
                if rot:
                    v["y"] = rot
                variants[f"axis={axis},wet={wetness}"] = v
        _write_json(ASSETS / "blockstates" / f"{slide_name}.json", {"variants": variants})
    for colour in STONE:
        _write_tex(f"river_pebble_{colour}", stone_texture(STONE_GAME_LOOK, colour))
    variants = {}
    for li, (name, stones) in enumerate(STONES):
        boxes = [b for si, st in enumerate(stones) for b in round_stone(st, STONE_GAME_LOOK, f"{STONE_GAME_LOOK}{li}{si}")]
        colours = sorted({b["c"] for b in boxes})
        textures = {c: f"antcolonies:block/river_pebble_{c}" for c in colours}
        textures["particle"] = textures[colours[0]]
        _write_json(ASSETS / "models/block" / f"river_pebbles_{li}.json", {
            "ambientocclusion": False, "textures": textures, "elements": [_pebble_element(b) for b in boxes]})
        variants[f"look={li}"] = [{"model": f"antcolonies:block/river_pebbles_{li}", **({"y": r} if r else {})}
                                  for r in (0, 90, 180, 270)]
    _write_json(ASSETS / "blockstates" / "river_pebbles.json", {"variants": variants})
    _write_json(ASSETS / "models/item" / "river_pebbles.json", {
        "parent": "antcolonies:block/river_pebbles_6",
        "display": {"gui": {"rotation": [30, 45, 0], "translation": [0, 3, 0], "scale": [1.4, 1.4, 1.4]},
                    "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
                    "fixed": {"scale": [1, 1, 1]}}})
    print("river sand and pebbles written into", ASSETS)


if __name__ == "__main__" and len(sys.argv) > 1 and sys.argv[1] == "game":
    game()
