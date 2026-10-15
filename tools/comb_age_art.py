"""The fungus comb as it ages (the user's request, 2026-10-15): three looks for each age of comb, drawn in plain Python (no
Pillow), for the model viewer (python tools/model_viewer.py comb) and, once the user has picked, for the game's textures.

Real Macrotermes comb is a conveyor belt: the young workers lay fresh comb on top (dark, damp pellets of chewed plant
matter, their own first-passage droppings); the fungus spreads through it and it ripens (mottled with white fungus
threads, the nodules sprout on it); the oldest comb at the bottom goes pale, dry and crumbly, full of holes, and the old
workers eat it. Each age has a top and a side texture; the sides are seen 2 pixels at a time (one layer of comb), so
they are drawn in rows that read well in any 2-pixel slice.

Looks (my picks, for the user to choose from):
  A  Field photo: the real colours, fresh dark chocolate pellets, ripe mid-brown with white threads, old pale buff.
  B  Brain sponge: the real comb's winding brain-like ridges kept at every age, only the colour and wear change.
  C  Bold pixels: the same story in chunkier pixels and stronger contrast, closer to vanilla Minecraft blocks.
"""
import random

S = 16
AGES = ["fresh", "ripe", "old"]
LOOKS = "ABC"


def clamp(c):
    return max(0, min(255, round(c)))


def shade(color, k):
    return tuple(clamp(c * k) for c in color)


def jitter(rng, color, amount=0.06):
    return shade(color, rng.uniform(1 - amount, 1 + amount))


def blank(color):
    return [[color] * S for _ in range(S)]


def put(img, x, y, color):
    img[y % S][x % S] = color


# --- the colours of each age, per look ------------------------------------------------------------------------------

PALETTES = {
    "A": {
        "fresh": {"base": (70, 45, 29), "pellet": (92, 60, 38), "gap": (42, 27, 17), "shine": (138, 104, 76)},
        "ripe": {"base": (140, 106, 72), "ridge": (168, 134, 96), "groove": (104, 78, 52), "thread": (236, 232, 218)},
        "old": {"base": (206, 188, 152), "speck": (184, 162, 126), "pore": (122, 98, 70), "dust": (226, 214, 188)},
    },
    "B": {
        "fresh": {"base": (64, 42, 28), "pellet": (98, 66, 42), "gap": (38, 24, 16), "shine": (150, 116, 86)},
        "ripe": {"base": (126, 98, 70), "ridge": (172, 142, 106), "groove": (92, 70, 48), "thread": (232, 228, 214)},
        "old": {"base": (196, 176, 140), "speck": (214, 198, 166), "pore": (128, 104, 76), "dust": (232, 220, 196)},
    },
    "C": {
        "fresh": {"base": (58, 36, 22), "pellet": (98, 62, 36), "gap": (30, 18, 10), "shine": (164, 124, 88)},
        "ripe": {"base": (150, 108, 64), "ridge": (186, 146, 98), "groove": (110, 76, 44), "thread": (246, 244, 236)},
        "old": {"base": (222, 204, 164), "speck": (196, 172, 128), "pore": (110, 84, 56), "dust": (240, 232, 212)},
    },
}


# --- fresh: dark, wet, lumpy pellets ----------------------------------------------------------------------------------

def fresh(look, face, variant=0):
    rng = random.Random(f"fresh{look}{face}{variant}")
    p = PALETTES[look]["fresh"]
    img = [[jitter(rng, p["base"]) for _ in range(S)] for _ in range(S)]
    size = 3 if look == "C" else 2
    # Pellets: small round lumps packed in rows (on the side one row per layer, so every 2-pixel slice shows lumps).
    for row in range(0, S, 2):
        x = rng.randrange(3)
        while x < S:
            w = size + rng.randrange(2)
            for dx in range(w):
                for dy in range(min(2, size)):
                    if not (dx in (0, w - 1) and dy == 1 and rng.random() < 0.5):  # rounded corners
                        put(img, x + dx, row + dy, jitter(rng, p["pellet"], 0.08))
            if rng.random() < 0.6:
                put(img, x, row, p["shine"])  # a wet glint on the lump's top left
            put(img, x + w, row + rng.randrange(2), p["gap"])
            x += w + 1 + rng.randrange(2)
    if face == "top":
        for _ in range(6 if look != "B" else 4):
            put(img, rng.randrange(S), rng.randrange(S), p["shine"])
    return img


# --- ripe: mid brown, mottled with white fungus threads ---------------------------------------------------------------

def ridges(img, rng, p, count, length):
    """Winding brain-like ridges (random walks)."""
    for _ in range(count):
        x, y = rng.randrange(S), rng.randrange(S)
        dx, dy = rng.choice([(1, 0), (0, 1), (-1, 0), (0, -1)])
        for _ in range(rng.randint(*length)):
            put(img, x, y, jitter(rng, p["ridge"], 0.05))
            if rng.random() < 0.35:
                dx, dy = rng.choice([(dy, dx), (-dy, -dx)])
            x, y = x + dx, y + dy


def threads(img, rng, color, count, length, horizontal=False):
    """Fine white fungus threads (hyphae) creeping through it."""
    for _ in range(count):
        x, y = rng.randrange(S), rng.randrange(S)
        for _ in range(rng.randint(*length)):
            put(img, x, y, jitter(rng, color, 0.03))
            if horizontal:
                x += 1
                y += rng.choice([0, 0, 0, 1, -1])
            else:
                x += rng.choice([1, 0, -1])
                y += rng.choice([1, 0, -1])


def ripe(look, face, variant=0):
    rng = random.Random(f"ripe{look}{face}{variant}")
    p = PALETTES[look]["ripe"]
    img = [[jitter(rng, p["base"]) for _ in range(S)] for _ in range(S)]
    for _ in range(40):
        put(img, rng.randrange(S), rng.randrange(S), p["groove"])
    if look == "B" or face == "top":
        ridges(img, rng, p, 9 if look == "B" else 6, (6, 12))
    else:
        for row in range(0, S, 2):  # side: a lighter crust on each layer
            for x in range(S):
                if rng.random() < 0.55:
                    put(img, x, row, jitter(rng, p["ridge"], 0.05))
    threads(img, rng, p["thread"], 5 if look != "C" else 4, (4, 8), horizontal=face == "side")
    for _ in range(8):
        put(img, rng.randrange(S), rng.randrange(S), p["thread"])  # tiny white spots
    return img


# --- old: pale, dry, crumbly, full of holes ---------------------------------------------------------------------------

def old(look, face, variant=0):
    rng = random.Random(f"old{look}{face}{variant}")
    p = PALETTES[look]["old"]
    img = [[jitter(rng, p["base"], 0.04) for _ in range(S)] for _ in range(S)]
    for _ in range(36):
        put(img, rng.randrange(S), rng.randrange(S), p["speck"])
    if look == "B":
        ridges(img, rng, {"ridge": p["dust"]}, 6, (5, 9))
    # Holes: the fungus has eaten its way through it (1 and 2 pixel pores, a few bigger, with a pale rim above).
    for _ in range(10 if look != "C" else 7):
        x, y = rng.randrange(S), rng.randrange(S)
        big = rng.random() < (0.45 if look == "C" else 0.3)
        for dx in range(2 if big else 1):
            put(img, x + dx, y, p["pore"])
            if big:
                put(img, x + dx, y + 1, shade(p["pore"], 0.85))
        put(img, x, y - 1, p["dust"])
    # A dry crack or two.
    for _ in range(2):
        x, y = rng.randrange(S), rng.randrange(S)
        for _ in range(rng.randint(3, 6)):
            put(img, x, y, shade(p["speck"], 0.9))
            x += 1
            y += rng.choice([0, 0, 1, -1])
    return img


DRAW = {"fresh": fresh, "ripe": ripe, "old": old}


def texture(look, age, face, variant=0):
    """Rows of (r, g, b) for this look, age (fresh, ripe, old) and face (top, side)."""
    return DRAW[age](look, face, variant)


def nodule():
    """The white fungus nodule (as in the game now)."""
    rng = random.Random(3)
    tones = [(250, 248, 240), (238, 234, 222), (222, 216, 200), (204, 196, 178)]
    return [[tones[min(3, int(rng.random() * 2.2 + y / 16 * 1.5))] for _ in range(S)] for y in range(S)]


def room_wall():
    """Damp termite-built soil for the garden room's walls and floor."""
    rng = random.Random(5)
    return [[jitter(rng, (112, 66, 44), 0.1) for _ in range(S)] for _ in range(S)]
