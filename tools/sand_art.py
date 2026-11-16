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


def _look(*pebbles):
    return [b for p in pebbles for b in p]


PEBBLES = [
    ("One big flat stone", _look(pebble(0.5, 0.0, 8.0, 6.0, 2.4, "grey", 22.5))),
    ("Two medium stones", _look(pebble(-2.4, -1.0, 4.0, 3.0, 1.8, "ochre", -22.5),
                                pebble(2.6, 1.6, 3.6, 3.0, 1.6, "dark", 22.5))),
    ("A medium stone and two small", _look(pebble(-1.0, -0.5, 4.2, 3.2, 1.9, "tan", 0.0),
                                          pebble(3.2, 2.4, 2.0, 1.6, 1.0, "grey", 45.0),
                                          pebble(-4.4, 3.0, 1.8, 1.4, 0.9, "quartz", -22.5))),
    ("A cluster of small pebbles", _look(pebble(-1.6, -1.2, 2.4, 2.0, 1.2, "grey", 22.5),
                                         pebble(1.2, -1.8, 2.0, 1.6, 1.1, "rust", -45.0),
                                         pebble(0.2, 1.2, 2.2, 1.8, 1.2, "ochre", 0.0),
                                         pebble(-2.8, 1.6, 1.6, 1.4, 0.9, "dark", 45.0))),
    ("A scatter of tiny ones", _look(*[pebble(x, z, s, s * 0.8, s * 0.6, c, t, round_top=False) for x, z, s, c, t in (
        (-4.8, -3.2, 1.2, "grey", 22.5), (-1.0, -4.6, 1.0, "ochre", 0.0), (3.6, -2.6, 1.3, "dark", -22.5),
        (-3.2, 2.0, 1.0, "quartz", 45.0), (1.4, 0.6, 1.1, "tan", 0.0), (4.6, 3.8, 1.0, "rust", 22.5))])),
    ("A half-buried stone", _look(pebble(0.0, 0.5, 7.0, 5.4, 2.8, "dark", -22.5, sink=1.3))),
    ("A mixed handful", _look(pebble(-2.0, 0.6, 3.4, 2.8, 1.6, "grey", 22.5),
                              pebble(2.2, -2.0, 2.0, 1.6, 1.0, "ochre", 0.0),
                              pebble(2.8, 2.6, 1.8, 1.5, 0.9, "rust", -45.0),
                              *[pebble(x, z, s, s * 0.8, s * 0.6, c, t, round_top=False) for x, z, s, c, t in (
                                  (-4.6, -3.4, 1.0, "quartz", 0.0), (-0.4, -4.4, 0.9, "tan", 22.5),
                                  (-4.8, 4.0, 1.0, "dark", -22.5))])),
]
MAX_BOXES = 12


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
