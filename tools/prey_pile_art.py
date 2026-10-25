"""The Matabele prey pile (M8 step 1e, the user's ask 2026-10-24): a heap of paralysed Macrotermes workers and soldiers
in the queen's room, a block that grows a layer for each bunch brought in, and the heap of leftovers by the entrance.
Three looks for the model viewer (python tools/model_viewer.py preypile), drawn in plain Python (no Pillow); once the
user has picked, the game's textures, block models and blockstates come from here too (python tools/prey_pile_art.py).

The real thing (Yusuf 2026, Annual Review of Entomology 71): the sting paralyses; the termites are carried home alive
and eaten fresh, never stored. So a fresh pile is of living, still termites whose legs and antennae flick now and then
(an animated texture on each termite's legs), pale workers with golden heads and soldiers with big orange-brown heads
and black jaws (the game's own termite colours, tools/termite_models.py). In the last quarter of its day the termites
die: still, greyed and browned. A layer is about 10 termites, one big ant's load; the pile is 2 pixels higher a layer.
Sizes from the real ones next to the Matabele major (15 mm drawn as one block): a worker about 6 pixels long, a minor
soldier 8, a major soldier 11.

Each termite is a few small boxes (abdomen, thorax, head, a soldier's two jaws) and one flat square of legs and
antennae; the boxes take their colours from one pixel each on a 16 x 16 colour sheet (one sheet fresh, one dead); a
termite lies at one of 20 headings (the model format turns boxes only in steps of 22.5 degrees), some on their backs,
legs up. Under them a core of packed bodies gives the heap its round shape. Looks (my picks, for the user to choose):
  A  Field heap: real colours and sizes, about 1 soldier in 6, mostly right way up, a loose round heap.
  B  Tumbled mass: more termites, piled every which way, many on their backs with legs in the air, soldiers 1 in 4.
  C  Dropped bundles: the termites lie side by side in rows as the big ants set their stacks down, each layer turned.
"""
import json
import math
import random

S = 16
LOOKS = "ABC"
STAGES = ["fresh", "dead"]
GAME_LOOK = "B"  # the user's pick, "tumbled mass" (2026-10-24)
LEG_VARIANTS = 3
MAX_LAYERS = 8


def clamp(c):
    return max(0, min(255, round(c)))


def mix(a, b, k):
    return tuple(clamp(x + (y - x) * k) for x, y in zip(a, b))


def shade(c, k):
    return tuple(clamp(x * k) for x in c)


# --- colours (the game's termites, tools/termite_models.py) ------------------------------------------------------------

FRESH = {
    "w_head": (212, 172, 96), "w_body": (236, 226, 200), "w_gut": (178, 170, 154), "w_belly": (226, 214, 188),
    "s_head": (178, 94, 36), "s_shine": (218, 138, 64), "s_front": (118, 54, 22), "s_jaw": (34, 24, 18),
    "s_body": (238, 230, 208), "s_belly": (226, 216, 192),
}
DEATH = (112, 92, 72)


def colours(stage):
    if stage == "fresh":
        return dict(FRESH)
    out = {}
    for key, c in FRESH.items():
        k = 0.15 if key == "s_jaw" else 0.45 if key.startswith("s_") and "body" not in key and "belly" not in key else 0.55
        out[key] = shade(mix(c, DEATH, k), 0.86)
    out["w_gut"] = (92, 80, 66)
    return out


KEYS = list(FRESH)


def sheet(stage):
    """The colour sheet: one pixel per colour, in the order of KEYS, the rest clear."""
    rows = [[(0, 0, 0, 0)] * S for _ in range(S)]
    for i, key in enumerate(KEYS):
        rows[i // S][i % S] = colours(stage)[key] + (255,)
    return rows


def swatch(key):
    i = KEYS.index(key)
    return [i % S, i // S, i % S + 1, i // S + 1]


# --- the core of packed bodies ------------------------------------------------------------------------------------------

def core(stage, look):
    """Packed bodies seen through the gaps: little pale bodies with golden heads, the odd orange soldier head, dark gaps."""
    rng = random.Random(f"core{look}{stage}")
    c = colours(stage)
    gap = shade(mix(c["w_gut"], (70, 56, 40), 0.5), 0.8)
    img = [[gap] * S for _ in range(S)]
    for _ in range(26):
        x, y = rng.randrange(S), rng.randrange(S)
        vertical = rng.random() < 0.5
        soldier = rng.random() < (0.25 if look == "B" else 0.16)
        body = c["s_body"] if soldier else c["w_body"]
        for i in range(4):
            px, py = (x, (y + i) % S) if vertical else ((x + i) % S, y)
            img[py][px] = shade(body if i < 3 else c["w_gut"], rng.uniform(0.85, 1.05)) if i else body
        hx, hy = (x, (y - 1) % S) if vertical else ((x - 1) % S, y)
        img[hy][hx] = c["s_head"] if soldier else c["w_head"]
    return img


# --- legs and antennae: a flat square under each termite, its frames the twitch -------------------------------------

def legs(stage, variant, frame):
    """A termite's six legs and two antennae from above, head toward the bottom (+z), on a clear 16 x 16. Frames: 0 at
    rest, 1 a middle leg flicked, 2 the antennae swung, 3 a hind leg kicked. A dead one's legs curl in."""
    img = [[(0, 0, 0, 0)] * S for _ in range(S)]
    dead = stage == "dead"
    leg = (150, 128, 96, 255) if dead else (196, 168, 112, 255)
    feeler = (128, 100, 66, 255) if dead else (184, 140, 80, 255)

    def line(x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            x, y = round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)
            if 0 <= x < S and 0 <= y < S:
                img[y][x] = c

    # Hip, knee, foot for each leg on the right side (x > 8); the left side mirrored.
    rest = [((9, 9), (12, 11), (14, 14)), ((9, 8), (13, 8), (15, 7)), ((9, 7), (12, 5), (14, 2))]
    curled = [((9, 9), (11, 10), (10, 12)), ((9, 8), (11, 8), (11, 6)), ((9, 7), (11, 6), (10, 4))]
    pose = [list(p) for p in (curled if dead else rest)]
    flick = {1: (1, 0, (2, -2)), 3: (2, 1, (1, 2))}  # frame -> (leg, 0 right 1 left, foot moved by)
    for side in (0, 1):
        for i, (hip, knee, foot) in enumerate(pose):
            if not dead and frame in flick and flick[frame][0] == i and flick[frame][1] == side:
                foot = (foot[0] + flick[frame][2][0], foot[1] + flick[frame][2][1])
                knee = (knee[0] + flick[frame][2][0] // 2, knee[1] + flick[frame][2][1] // 2)
            m = (lambda p: p) if side == 0 else (lambda p: (15 - p[0], p[1]))
            line(*m(hip), *m(knee), leg)
            line(*m(knee), *m(foot), leg)
    swing = 1 if (frame == 2 and not dead) else 0
    for side in (0, 1):
        m = (lambda p: p) if side == 0 else (lambda p: (15 - p[0], p[1]))
        tip = (11, 15) if not dead else (9, 14)
        line(*m((8, 12)), *m((tip[0] + swing * (1 if side == 0 else -1), tip[1] - swing)), feeler)
    return img


def leg_animation(variant):
    """Frame order and times (ticks) of a fresh termite's twitching; each variant out of step with the others."""
    rng = random.Random(f"twitch{variant}")
    frames = []
    for _ in range(4):
        frames.append({"index": 0, "time": rng.randint(30, 90)})
        frames.append({"index": rng.choice([1, 2, 3]), "time": rng.randint(2, 5)})
    return {"animation": {"interpolate": False, "frames": frames}}


# --- one termite as boxes -------------------------------------------------------------------------------------------

# Local boxes, head toward +z, centred on the body, the belly at y 0: (x0, y0, z0, x1, y1, z1, colour, on its back).
def termite_boxes(kind, on_back):
    if kind == "worker":
        parts = [(-1.2, 0, -3.0, 1.2, 1.6, 0.0, "w_gut"), (-0.8, 0, 0.0, 0.8, 1.2, 1.4, "w_body"),
                 (-0.9, 0, 1.4, 0.9, 1.5, 3.0, "w_head")]
        reach = 4.0
    elif kind == "minor":
        parts = [(-1.3, 0, -3.6, 1.3, 1.7, -0.4, "s_body"), (-0.9, 0, -0.4, 0.9, 1.3, 1.1, "s_body"),
                 (-1.2, 0, 1.1, 1.2, 1.8, 3.4, "s_head"), (-0.9, 0.3, 3.4, -0.3, 0.8, 4.6, "s_jaw"),
                 (0.3, 0.3, 3.4, 0.9, 0.8, 4.6, "s_jaw")]
        reach = 5.0
    else:  # major soldier
        parts = [(-1.6, 0, -5.0, 1.6, 2.0, -1.0, "s_body"), (-1.1, 0, -1.0, 1.1, 1.5, 0.8, "s_body"),
                 (-1.6, 0, 0.8, 1.6, 2.4, 4.4, "s_head"), (-0.9, 1.8, 1.4, 0.9, 2.6, 3.6, "s_shine"),
                 (-1.3, 0.4, 4.4, -0.4, 1.1, 6.0, "s_jaw"), (0.4, 0.4, 4.4, 1.3, 1.1, 6.0, "s_jaw")]
        reach = 6.5
    out = []
    for x0, y0, z0, x1, y1, z1, c in parts:
        if on_back and c in ("w_gut", "s_body"):
            c = "w_belly" if c == "w_gut" else "s_belly"
        out.append((x0, y0, z0, x1, y1, z1, c))
    return out, reach


ANGLES = [-45.0, -22.5, 0.0, 22.5, 45.0]


def termite(kind, cx, base, cz, heading, on_back, legs_tex):
    """Model elements for one termite at (cx, base, cz) (pixels): heading 0 to 19 (axis z or x, head forward or back,
    one of the five turns)."""
    boxes, reach = termite_boxes(kind, on_back)
    swap, back, angle = heading // 10, (heading // 5) % 2, ANGLES[heading % 5]
    rot = {"origin": [round(cx, 3), round(base, 3), round(cz, 3)], "axis": "y", "angle": angle}
    out = []
    top = 0.0
    for x0, y0, z0, x1, y1, z1, c in boxes:
        if back:
            x0, x1, z0, z1 = -x1, -x0, -z1, -z0
        if swap:
            x0, x1, z0, z1 = z0, z1, x0, x1
        top = max(top, y1)
        uv = swatch(c)
        faces = {f: {"uv": uv, "texture": "#colours"} for f in ("up", "down", "north", "south", "west", "east")}
        out.append({"from": [round(cx + x0, 3), round(base + y0, 3), round(cz + z0, 3)],
                    "to": [round(cx + x1, 3), round(base + y1, 3), round(cz + z1, 3)], "rotation": rot, "faces": faces})
    y = base + (top + 0.05 if on_back else 0.15)
    r = reach * 0.75
    turn = {(0, 0): 0, (1, 0): 180, (0, 1): 270, (1, 1): 90}[(back, swap)]
    face = {"uv": [0, 0, 16, 16], "texture": legs_tex}
    if turn:
        face["rotation"] = turn
    out.append({"from": [round(cx - r, 3), round(y, 3), round(cz - r, 3)], "to": [round(cx + r, 3), round(y, 3), round(cz + r, 3)],
                "rotation": rot, "faces": {"up": face}})
    return out


# --- the heap -------------------------------------------------------------------------------------------------------

def heap_shape(layers):
    """The heap's height (pixels), the core's height and its radius."""
    height = layers * 2
    return height, max(0.5, height - 1.6), min(7.6, 2.6 + 0.7 * layers)


def core_elements(layers, wobble):
    _, h, r = heap_shape(layers)
    out = []
    ox, oz = 8 + wobble[0], 8 + wobble[1]
    tiers = 3 if layers > 2 else 1
    for t in range(tiers):
        rr = r * (1 - 0.3 * t)
        y0, y1 = h * t / tiers, h * (t + 1) / tiers
        x0, x1 = max(0.0, ox - rr), min(16.0, ox + rr)
        z0, z1 = max(0.0, oz - rr * 0.9), min(16.0, oz + rr * 0.9)
        faces = {f: {"uv": [x0, 16 - y1, x1, 16 - y0] if f in ("north", "south") else [z0, 16 - y1, z1, 16 - y0], "texture": "#core"}
                 for f in ("north", "south", "west", "east")}
        faces["up"] = {"uv": [x0, z0, x1, z1], "texture": "#core"}
        out.append({"from": [round(x0, 3), round(y0, 3), round(z0, 3)], "to": [round(x1, 3), round(y1, 3), round(z1, 3)], "faces": faces})
    return out


def counts(look, layers):
    return {"A": 3 + 3 * layers, "B": 4 + 5 * layers, "C": 3 + 3 * layers}[look]


def pile(look, stage, layers, variant=0):
    """The block model of a pile of {@code layers} layers in a look and stage."""
    rng = random.Random(f"pile{look}{layers}{variant}")
    height, h_core, r = heap_shape(layers)
    wobble = (rng.uniform(-0.6, 0.6), rng.uniform(-0.6, 0.6))
    elements = core_elements(layers, wobble)
    soldier_share = 0.25 if look == "B" else 0.17
    back_share = {"A": 0.15, "B": 0.4, "C": 0.08}[look]
    n = counts(look, layers)
    for i in range(n):
        kind = "worker"
        if rng.random() < soldier_share:
            kind = "major" if rng.random() < 0.35 else "minor"
        if look == "C":
            row_layer = i * layers // n
            across = (i % 4) - 1.5
            heading = (0 if row_layer % 2 == 0 else 10) + (5 if rng.random() < 0.5 else 0) + rng.choice([1, 2, 3])
            along = rng.uniform(-r * 0.4, r * 0.4)
            x, z = (8 + wobble[0] + across * 2.6, 8 + wobble[1] + along) if row_layer % 2 == 0 else (8 + wobble[0] + along, 8 + wobble[1] + across * 2.6)
            dist = math.hypot(x - 8, z - 8)
        else:
            dist = r * math.sqrt(rng.random()) * (0.95 if look == "A" else 1.05)
            a = rng.uniform(0, 2 * math.pi)
            x, z = 8 + wobble[0] + dist * math.cos(a), 8 + wobble[1] + dist * math.sin(a)
            heading = rng.randrange(20)
        reach = {"worker": 3.4, "minor": 4.3, "major": 5.6}[kind]
        x, z = min(16 - reach * 0.6, max(reach * 0.6, x)), min(16 - reach * 0.6, max(reach * 0.6, z))
        surface = h_core * max(0.0, 1 - (dist / max(r, 0.1)) ** 2) if look != "C" else h_core * (1 - min(1.0, dist / (r + 1)) ** 2)
        base = max(0.0, min(height - 1.4, surface - 0.4 + rng.uniform(-0.3, 0.3)))
        on_back = rng.random() < back_share
        tex = f"#legs{rng.randrange(LEG_VARIANTS)}" if stage == "fresh" else "#legs_dead"
        elements += termite(kind, x, base, z, heading, on_back, tex)
    textures = {"colours": f"antcolonies:block/prey_pile_colours_{stage}", "core": f"antcolonies:block/prey_pile_core_{stage}",
                "particle": f"antcolonies:block/prey_pile_core_{stage}"}
    if stage == "fresh":
        for v in range(LEG_VARIANTS):
            textures[f"legs{v}"] = f"antcolonies:block/prey_pile_legs_{v}"
    else:
        textures["legs_dead"] = "antcolonies:block/prey_pile_legs_dead"
    return {"render_type": "minecraft:cutout", "textures": textures, "elements": elements}


# --- the leftovers by the entrance ----------------------------------------------------------------------------------

def remains(look):
    """The heap of termite leftovers: dust and broken bits, golden worker heads, orange soldier head capsules, black jaws."""
    rng = random.Random(f"remains{look}")
    c = colours("dead")
    dust = (128, 104, 78)
    img = [[shade(dust, rng.uniform(0.82, 1.08)) for _ in range(S)] for _ in range(S)]
    for _ in range(14):
        x, y = rng.randrange(S - 1), rng.randrange(S - 1)
        head = FRESH["s_head"] if rng.random() < 0.35 else FRESH["w_head"]
        head = shade(mix(head, DEATH, 0.35), rng.uniform(0.8, 1.0))
        img[y][x] = head
        if rng.random() < 0.6:
            img[y][x + 1] = shade(head, 0.85)
        if rng.random() < 0.5:
            img[y + 1][x] = c["s_jaw"]
    for _ in range(10):
        x, y = rng.randrange(S), rng.randrange(S)
        img[y][x] = shade(c["w_body"], 0.7)
    return img


def remains_model(layers):
    h = layers * 2
    faces = {f: {"uv": [0, 16 - h, 16, 16], "texture": "#remains"} for f in ("north", "south", "west", "east")}
    faces["up"] = {"uv": [0, 0, 16, 16], "texture": "#remains"}
    faces["down"] = {"uv": [0, 0, 16, 16], "texture": "#remains"}
    return {"textures": {"remains": "antcolonies:block/termite_remains", "particle": "antcolonies:block/termite_remains"},
            "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": faces}]}


def item(stage="fresh"):
    """A paralysed termite from above, for a carrier's jaws and the item icon."""
    img = [[(0, 0, 0, 0)] * S for _ in range(S)]
    for y, row in enumerate(legs(stage, 0, 0)):
        for x, px in enumerate(row):
            if px[3]:
                img[y][x] = px
    c = colours(stage)
    for y in range(3, 9):
        for x in range(6, 10):
            img[y][x] = c["w_gut"] + (255,) if y < 7 else c["w_body"] + (255,)
    for y in range(9, 13):
        for x in range(7, 9):
            img[y][x] = c["w_body"] + (255,)
    for y in range(11, 14):
        for x in range(6, 10):
            img[y][x] = c["w_head"] + (255,)
    return img


# --- the game's files -----------------------------------------------------------------------------------------------

def write_game_files(look):
    import mod_paths
    from model_viewer import png
    assets = mod_paths.assets("savanna")
    blocks = assets / "textures/block"
    models = assets / "models/block"
    for stage in STAGES:
        (blocks / f"prey_pile_colours_{stage}.png").write_bytes(png(sheet(stage)))
        (blocks / f"prey_pile_core_{stage}.png").write_bytes(png(core(stage, look)))
    for v in range(LEG_VARIANTS):
        frames = [legs("fresh", v, f) for f in range(4)]
        (blocks / f"prey_pile_legs_{v}.png").write_bytes(png([row for frame in frames for row in frame]))
        (blocks / f"prey_pile_legs_{v}.png.mcmeta").write_text(json.dumps(leg_animation(v), indent=2) + "\n", encoding="utf-8")
    (blocks / "prey_pile_legs_dead.png").write_bytes(png(legs("dead", 0, 0)))
    (blocks / "termite_remains.png").write_bytes(png(remains(look)))
    (assets / "textures/item/paralysed_termite.png").write_bytes(png(item()))
    variants, rem = {}, {}
    for layers in range(1, MAX_LAYERS + 1):
        for stage in STAGES:
            name = f"prey_pile_{layers}_{stage}"
            (models / f"{name}.json").write_text(json.dumps(pile(look, stage, layers)) + "\n", encoding="utf-8")
            variants[f"dead={'true' if stage == 'dead' else 'false'},layers={layers}"] = {"model": f"antcolonies:block/{name}"}
        (models / f"termite_remains_{layers}.json").write_text(json.dumps(remains_model(layers)) + "\n", encoding="utf-8")
        rem[f"layers={layers}"] = {"model": f"antcolonies:block/termite_remains_{layers}"}
    (assets / "blockstates/prey_pile.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8")
    (assets / "blockstates/termite_remains.json").write_text(json.dumps({"variants": rem}, indent=2) + "\n", encoding="utf-8")
    (assets / "models/item/prey_pile.json").write_text(json.dumps({"parent": "antcolonies:block/prey_pile_4_fresh"}) + "\n", encoding="utf-8")
    (assets / "models/item/termite_remains.json").write_text(json.dumps({"parent": "antcolonies:block/termite_remains_2"}) + "\n", encoding="utf-8")
    (assets / "models/item/paralysed_termite.json").write_text(json.dumps({"parent": "minecraft:item/generated",
                                                                           "textures": {"layer0": "antcolonies:item/paralysed_termite"}}) + "\n", encoding="utf-8")
    print(f"wrote the prey pile in look {look}: {MAX_LAYERS * 2} pile models, {MAX_LAYERS} remains models, textures, blockstates, items")


if __name__ == "__main__":
    import sys
    write_game_files(sys.argv[1] if len(sys.argv) > 1 else GAME_LOOK)
