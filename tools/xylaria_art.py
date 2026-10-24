"""Xylaria on a dead colony's fungus comb (M8 step 1a, the user's pick 2026-10-24): three looks for the model viewer
(python tools/model_viewer.py xylaria), drawn in plain Python (no Pillow); once the user has picked, the game's textures
and block models come from here too.

The real thing: a Macrotermes colony keeps its comb clean (the workers weed out other fungi, and Termitomyces holds them
back). Once the termites are gone, Xylaria (Pseudoxylaria), which was in the comb all along, takes over within days:
a grey-white fuzz of threads spreads over the comb first, then slender black stalks (stromata) push up out of it, single
or forked like little antlers, their tips powdered whitish-grey with spores while young; the comb under them darkens to
black and goes soft and crumbly, the stalks blacken all over and fall, and the comb rots away to dust. Real stalks are
about as tall as the comb is thick or a little less, so here they are about one comb block's 8 pixels high.

Each comb layer keeps the game's 2-pixel bands: the dead comb's stages replace the living ages' textures ("furred",
"black", "rotten", top and side), and the stalks are small boxes in pixels on the comb's top (block model elements in
the game). Looks (my picks, for the user to choose from):
  A  Field photo: real colours and size, thin stalks, a few forks, whitish tips, the comb going from grey fuzz to black.
  B  Antler forest: more and taller stalks, forked two or three times like antlers, bright white tips.
  C  Sooty crust: the comb black and sooty all over, short stubby stalks, white only as a fine dust.
"""
import random

import comb_age_art as comb

S = 16
LOOKS = "ABC"
STAGES = ["furred", "black", "rotten"]
GAME_LOOK = "B"  # picked by the user 2026-10-24 ("antler forest", more dramatic)
VARIANTS = 3


def clamp(c):
    return max(0, min(255, round(c)))


def mix(a, b, k):
    return tuple(clamp(x + (y - x) * k) for x, y in zip(a, b))


def shade(color, k):
    return tuple(clamp(c * k) for c in color)


# --- colours ----------------------------------------------------------------------------------------------------------

FUZZ = {"A": (186, 184, 178), "B": (214, 214, 208), "C": (150, 146, 140)}
CRUST = {"A": (44, 38, 34), "B": (38, 34, 32), "C": (28, 26, 25)}
ROT = {"A": (58, 46, 38), "B": (54, 44, 38), "C": (40, 34, 30)}
STALK = {"A": (26, 24, 23), "B": (22, 20, 20), "C": (30, 28, 26)}
STALK_FOOT = {"A": (52, 42, 34), "B": (46, 38, 32), "C": (44, 38, 32)}
TIP = {"A": (204, 202, 194), "B": (236, 236, 230), "C": (150, 148, 142)}


def patches(rng, count, radius):
    """Round blobs on a wrapping 16 x 16: how strongly each pixel is covered (0 to 1)."""
    cover = [[0.0] * S for _ in range(S)]
    for _ in range(count):
        cx, cy, r = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(radius * 0.6, radius)
        for y in range(S):
            for x in range(S):
                dx = min(abs(x - cx), S - abs(x - cx))
                dy = min(abs(y - cy), S - abs(y - cy))
                k = 1 - (dx * dx + dy * dy) ** 0.5 / r
                if k > 0:
                    cover[y][x] = min(1.0, cover[y][x] + k * 1.4)
    return cover


def furred(look, face, variant=0):
    """Day 1: the living comb's ripe pattern, nodules gone, grey-white threads spreading over it in patches."""
    rng = random.Random(f"furred{look}{face}{variant}")
    img = comb.texture("A", "ripe", face, variant)
    amount = {"A": 5, "B": 7, "C": 4}[look]
    cover = patches(rng, amount, 4.5)
    fuzz = FUZZ[look]
    for y in range(S):
        for x in range(S):
            k = cover[y][x] * rng.uniform(0.55, 1.0)
            dull = mix(shade(img[y][x], 0.72), (96, 90, 84), 0.35)
            img[y][x] = mix(dull, fuzz, k * (0.7 if look == "C" else 0.85))
    # Loose single threads running out of the patches.
    for _ in range({"A": 6, "B": 9, "C": 3}[look]):
        x, y = rng.randrange(S), rng.randrange(S)
        for _ in range(rng.randint(2, 4)):
            img[y % S][x % S] = mix(img[y % S][x % S], fuzz, 0.8)
            x += rng.choice((-1, 0, 1))
            y += rng.choice((-1, 0, 1)) if face == "top" else 0
    return img


def black(look, face, variant=0):
    """Days 2 to 4: the comb darkened to a black crust, the old ridges just showing, pale spore dust in specks."""
    rng = random.Random(f"black{look}{face}{variant}")
    ridge = comb.texture("A", "ripe", face, variant)
    crust, fuzz = CRUST[look], FUZZ[look]
    img = []
    for y in range(S):
        row = []
        for x in range(S):
            # Keep a ghost of the comb's ridges: lighter comb pixels stay a little lighter under the crust.
            light = sum(ridge[y][x]) / (3 * 170)
            k = {"A": 0.25, "B": 0.3, "C": 0.12}[look]
            c = shade(crust, rng.uniform(0.85, 1.15) * (1 - k + k * light * 1.6))
            row.append(c)
        img.append(row)
    for _ in range({"A": 7, "B": 10, "C": 14}[look]):
        x, y = rng.randrange(S), rng.randrange(S)
        img[y][x] = mix(img[y][x], fuzz, rng.uniform(0.4, 0.75) if look != "C" else rng.uniform(0.25, 0.45))
    if look != "C" and face == "top":
        # The last of the grey fuzz in faint patches on the top.
        cover = patches(rng, 2, 3.0)
        for y in range(S):
            for x in range(S):
                img[y][x] = mix(img[y][x], fuzz, cover[y][x] * 0.18)
    return img


def rotten(look, face, variant=0):
    """Days 4 to 6: soft, crumbling, brown-black, full of holes, a little dust."""
    rng = random.Random(f"rotten{look}{face}{variant}")
    base = ROT[look]
    img = [[shade(base, rng.uniform(0.8, 1.2)) for _ in range(S)] for _ in range(S)]
    for _ in range({"A": 9, "B": 9, "C": 11}[look]):
        x, y = rng.randrange(S), rng.randrange(S)
        hole = shade(base, 0.45)
        img[y][x] = hole
        if rng.random() < 0.5:
            img[y][(x + 1) % S] = hole
    for _ in range(6):
        x, y = rng.randrange(S), rng.randrange(S)
        img[y][x] = mix(img[y][x], (150, 132, 112), 0.5)
    if face == "side":
        # Crumbling: the bottom row of each 2-pixel band a shade darker, so the layers read as slumped.
        for y in range(1, S, 2):
            img[y] = [shade(c, 0.82) for c in img[y]]
    return img


DRAW = {"furred": furred, "black": black, "rotten": rotten}


def texture(look, stage, face, variant=0):
    return DRAW[stage](look, face, variant)


# --- the stalks -------------------------------------------------------------------------------------------------------

def hexc(color):
    return "#%02x%02x%02x" % color


def stalk(rng, look, x, z, height, forks, tip, fallen=False):
    """One stalk as boxes [x, y, z, w, h, d, colour] in pixels, y from the comb's top: a black stem in 1-pixel steps
    that may lean a pixel now and then, forking sideways and up, each tip powdered (or black once old)."""
    boxes = []
    foot, body, tipc = STALK_FOOT[look], STALK[look], TIP[look] if tip else STALK[look]
    width = 1 if look != "C" else 2
    if fallen:
        dx, dz = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
        length = max(2, height - 2)
        for i in range(length):
            boxes.append([x + dx * i, 0, z + dz * i, width, 1, width, hexc(body if i else foot)])
        return boxes

    def grow(px, py, pz, steps, branches_left):
        for i in range(steps):
            colour = foot if py == 0 else body
            last = i == steps - 1
            if last:
                colour = tipc
            elif i == steps - 2 and tip and look != "C":
                colour = mix(body, tipc, 0.45)
            boxes.append([px, py, pz, width, 1, width, hexc(colour)])
            if branches_left and i >= 2 and i < steps - 2 and rng.random() < 0.6:
                bx, bz = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
                boxes.append([px + bx, py, pz + bz, width, 1, width, hexc(body)])
                grow(px + bx * 2, py + 1, pz + bz * 2, max(2, (steps - i) // 2 + rng.randint(0, 1)), branches_left - 1)
                branches_left -= 1
            if i >= 1 and rng.random() < 0.3:
                px += rng.choice((-1, 1)) if rng.random() < 0.5 else 0
                pz += rng.choice((-1, 1)) if rng.random() < 0.5 else 0
            py += 1

    grow(x, 0, z, height, forks)
    return boxes


SETTINGS = {
    # stage: (count range, height range, forks, tips white, share fallen)
    "A": {"furred": ((0, 2), (2, 3), 0, True, 0), "black": ((3, 5), (5, 8), 1, True, 0), "rotten": ((2, 3), (3, 6), 0, False, 0.5)},
    "B": {"furred": ((1, 3), (2, 4), 0, True, 0), "black": ((5, 8), (7, 11), 2, True, 0), "rotten": ((3, 4), (4, 7), 1, False, 0.5)},
    "C": {"furred": ((0, 1), (1, 2), 0, True, 0), "black": ((4, 6), (2, 4), 0, True, 0), "rotten": ((2, 3), (1, 3), 0, False, 0.4)},
}


def stalks(look, stage, variant=0):
    """The stalks on one comb block's top, as a list of boxes (see stalk), kept 2 pixels in from the edges."""
    rng = random.Random(f"stalks{look}{stage}{variant}")
    (lo, hi), (hlo, hhi), forks, tip, fallen = SETTINGS[look][stage]
    out, taken = [], []
    for _ in range(rng.randint(lo, hi)):
        for _ in range(20):
            x, z = rng.randint(3, 12), rng.randint(3, 12)
            if all(abs(x - a) + abs(z - b) >= 4 for a, b in taken):
                break
        taken.append((x, z))
        out += stalk(rng, look, x, z, rng.randint(hlo, hhi), forks, tip, rng.random() < fallen)
    return out
