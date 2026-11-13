"""Drafts for the floodplain trees (the user's ask, 2026-11-13): the fever tree, the sycamore fig and the doum palm.

Only textures for the model viewer (python tools/model_viewer.py floodplain); nothing here is in the game yet. Each
texture is 16 x 16, a pixel an (r, g, b) tuple or None for see-through, in the same style as tools/tree_art.py.

Real colours aimed at:
- Fever tree (Vachellia xanthophloea): smooth, powdery lime yellow-green bark (it photosynthesises through it), on the
  trunk and every limb; tiny feathery leaflets in thin open layers; paired straight white spines.
- Sycamore fig (Ficus sycomorus): pale buff to orange-yellow bark that flakes in thin papery scales; broad dark leaves
  in a dense crown; figs in clusters straight on the trunk and big limbs (cauliflory), green then yellow then red.
- Doum palm (Hyphaene thebaica): a grey-brown trunk ringed by old leaf scars, the upper trunk often still wearing the
  criss-cross old leaf bases; stiff grey-green fan fronds, dead ones hanging tan under the crown; glossy orange-brown
  fruit the size of an apple in hanging bunches.
"""
import math
import random

from tree_art import S, blank, clamp, mix, noise, shade, speckle, thorns


# ---- Fever tree ----

def fever_bark(look):
    """A: the powdery lime yellow-green of the photos, a few dark lenticel scars. B: yellower, with papery flakes."""
    rng = random.Random(f"fever{look}")
    base, green, pale, scar = (((192, 194, 86), (158, 176, 76), (218, 216, 128), (112, 104, 50)) if look == "A"
                               else ((208, 198, 98), (176, 180, 84), (230, 222, 150), (128, 108, 56)))
    n, n2 = noise(f"fev{look}", 4), noise(f"fev2{look}", 2)
    img = [[mix(mix(base, green, n2((x * 3) % S, y) * 0.9), pale, max(0.0, n(x, y) - 0.55) * 1.4) for x in range(S)]
           for y in range(S)]
    for _ in range(4 if look == "A" else 2):  # lenticels: short dark dashes across the trunk
        x, y, w = rng.randrange(S), rng.randrange(S), rng.randint(2, 3)
        for i in range(w):
            img[y][(x + i) % S] = mix(img[y][(x + i) % S], scar, 0.7)
    if look == "B":  # papery flakes: a pale patch with a dark line under it
        for _ in range(4):
            x, y, w = rng.randrange(S), rng.randrange(S), rng.randint(3, 5)
            for i in range(w):
                img[y][(x + i) % S] = shade(pale, 1.04)
                img[(y + 1) % S][(x + i) % S] = mix(img[(y + 1) % S][(x + i) % S], scar, 0.45)
    for _ in range(10):  # the yellow powder
        img[rng.randrange(S)][rng.randrange(S)] = shade(pale, 1.05)
    return speckle(img, rng, 0.02)


def feathery(seed, base, sprigs):
    """Fine feathery leaflets on short stems (as the acacias' look C), `sprigs` of them: fewer is airier."""
    rng = random.Random(seed)
    img = blank(None)
    stem = shade(base, 0.62)
    for _ in range(sprigs):
        x, y, a = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0, math.pi * 2)
        for _ in range(rng.randint(5, 8)):
            img[int(y) % S][int(x) % S] = stem
            for side in (-1, 1):
                lx, ly = int(x - math.sin(a) * side) % S, int(y + math.cos(a) * side) % S
                if img[ly][lx] is None:
                    img[ly][lx] = shade(base, rng.uniform(0.85, 1.2))
            x, y = x + math.cos(a), y + math.sin(a)
    return img


# ---- Sycamore fig ----

def fig_bark(look):
    """A: warm buff-orange, flaking in thin scales. B: paler and greyer, with yellow-green patches where it has peeled."""
    rng = random.Random(f"fig{look}")
    base, dark, pale, under = (((190, 160, 110), (140, 110, 74), (218, 200, 156), (206, 172, 96)) if look == "A"
                               else ((176, 164, 128), (124, 112, 86), (208, 200, 168), (186, 184, 112)))
    n = noise(f"figb{look}", 3)
    img = [[mix(base, pale, n(x, y) * 0.7) for x in range(S)] for y in range(S)]
    for _ in range(7):  # scales: a patch of fresh bark with a shadow line along its lower edge
        x0, y0, w, h = rng.randrange(S), rng.randrange(S), rng.randint(3, 5), rng.randint(2, 4)
        for dy in range(h):
            for dx in range(w):
                x, y = (x0 + dx) % S, (y0 + dy) % S
                img[y][x] = mix(img[y][x], under, 0.55)
        for dx in range(w):
            x, y = (x0 + dx) % S, (y0 + h) % S
            img[y][x] = mix(img[y][x], dark, 0.6)
    for _ in range(5):
        x, y0 = rng.randrange(S), rng.randrange(S)
        for y in range(y0, y0 + rng.randint(2, 5)):
            img[y % S][x] = mix(img[y % S][x], dark, 0.35)
    return speckle(img, rng, 0.03)


def fig_leaves():
    """Broad, dark, nearly closed: round leaves with a pale midrib, only a little sky between them."""
    rng = random.Random("figleaf")
    base = (56, 98, 46)
    img = [[shade(base, rng.uniform(0.6, 0.72)) if rng.random() < 0.82 else None for _ in range(S)] for _ in range(S)]
    for _ in range(13):
        cx, cy, a = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0, math.pi)
        tone = rng.uniform(0.88, 1.22)
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                u, w = dx * math.cos(a) + dy * math.sin(a), -dx * math.sin(a) + dy * math.cos(a)
                if (u / 2.9) ** 2 + (w / 2.0) ** 2 <= 1:
                    rib = abs(w) < 0.5
                    img[int(cy + dy) % S][int(cx + dx) % S] = shade(base, tone * (1.28 if rib else 1.0))
    return img


def figs():
    """A cluster of figs on a short leafless twig, straight out of the bark: unripe green, yellow, ripe red."""
    rng = random.Random("figs")
    img = blank(None)
    twig = (126, 100, 66)
    for y in range(3, 14):
        img[y][8] = twig
    spots = [(5, 4), (11, 5), (4, 9), (10, 10), (7, 13), (12, 13), (6, 7)]
    colours = [(128, 160, 70), (212, 178, 78), (200, 110, 62), (176, 72, 54), (212, 178, 78), (200, 110, 62), (128, 160, 70)]
    for (cx, cy), c in zip(spots, colours):
        for dy in range(-2, 2):
            for dx in range(-2, 2):
                if (dx + 0.5) ** 2 + (dy + 0.5) ** 2 <= 3.2 and 0 <= cx + dx < S and 0 <= cy + dy < S:
                    img[cy + dy][cx + dx] = shade(c, 1.16 if (dx, dy) == (-1, -1) else 0.84 if dy == 1 else 1.0 + rng.uniform(-0.04, 0.04))
    return img


# ---- Doum palm ----

def doum_bark(look):
    """A: the bare ringed trunk (old leaf scars every few pixels). B: the old leaf bases still on, criss-crossed."""
    rng = random.Random(f"doum{look}")
    base, dark, pale = (126, 110, 94), (70, 60, 52), (160, 144, 124)
    n = noise(f"doumb{look}", 4)
    img = [[mix(base, pale, n(x, y) * 0.6) for x in range(S)] for y in range(S)]
    if look == "A":
        for y in range(S):
            for x in range(S):
                if y % 4 == 3:
                    img[y][x] = mix(img[y][x], dark, 0.6 if (x + y // 4) % 5 else 0.3)
                elif y % 4 == 0:
                    img[y][x] = mix(img[y][x], pale, 0.35)
                elif rng.random() < 0.12:
                    img[y][x] = mix(img[y][x], dark, 0.25)
    else:
        boot = (146, 120, 88)
        for y in range(S):
            for x in range(S):
                d = min((x + y) % 8, (x - y) % 8)
                img[y][x] = mix(dark, boot, 0.25) if d == 0 else mix(img[y][x], boot, 0.5 if d in (3, 4) else 0.25)
    return speckle(img, rng, 0.03)


def frond(base, seed):
    """One block of a fan frond: stiff pleated blades radiating from a point, sky between the blade tips."""
    rng = random.Random(seed)
    img = blank(None)
    cx, cy = 7.5, 7.5
    for i in range(18):
        a = i / 18 * math.pi * 2 + rng.uniform(-0.08, 0.08)
        reach = rng.uniform(7.0, 10.5)
        tone = rng.uniform(0.82, 1.14)
        for t in range(0, int(reach * 2)):
            r = t / 2
            x, y = int(round(cx + math.cos(a) * r)), int(round(cy + math.sin(a) * r))
            if 0 <= x < S and 0 <= y < S:
                img[y][x] = shade(base, tone * (0.8 + 0.4 * r / reach))
    for y in range(6, 10):  # the blades are joined near the stalk
        for x in range(6, 10):
            img[y][x] = shade(base, 0.74)
    return img


def doum_fruit():
    """A hanging bunch: a stalk with glossy orange-brown fruit down both sides."""
    img = blank(None)
    stalk, fruit = (112, 96, 60), (172, 98, 44)
    for y in range(0, 14):
        img[y][8 if y < 7 else 7] = stalk
    for cx, cy in [(5, 4), (11, 5), (4, 8), (10, 9), (6, 12), (11, 13)]:
        for dy in range(-2, 2):
            for dx in range(-1, 2):
                if abs(dx) + abs(dy + 0.5) <= 2.2 and 0 <= cy + dy < S:
                    img[cy + dy][cx + dx] = shade(fruit, 1.3 if (dx, dy) == (-1, -1) else 0.78 if dy == 1 else 1.0)
    return img


# ---- The ground the trees stand on ----

def flat_ground(name, base, lumps=0.2):
    rng = random.Random(name)
    n = noise(name, 4)
    return [[shade(base, 1 - lumps / 2 + lumps * n(x, y) + rng.uniform(-0.03, 0.03)) for x in range(S)] for y in range(S)]


def plain(colour, seed):
    rng = random.Random(seed)
    return [[shade(colour, 1 + rng.uniform(-0.05, 0.05)) for _ in range(S)] for _ in range(S)]


def all_textures():
    """Every texture the floodplain tree page needs: name -> rows of pixels."""
    out = {}
    for look in ("A", "B"):
        out[f"bark_fever_{look}"] = fever_bark(look)
        out[f"bark_fig_{look}"] = fig_bark(look)
        out[f"bark_doum_{look}"] = doum_bark(look)
    out["leaves_fever"] = feathery("feverleaf", (116, 164, 70), 8)
    out["leaves_fig"] = fig_leaves()
    out["frond"] = frond((122, 148, 96), "frond")
    out["frond_dead"] = frond((170, 144, 96), "fronddead")
    out["figs"] = figs()
    out["doum_fruit"] = doum_fruit()
    out["thorns"] = thorns()
    out["inner_fever"] = plain((214, 196, 132), "infever")
    out["inner_fig"] = plain((222, 204, 160), "infig")
    out["grass"] = flat_ground("floodgrass", (120, 152, 70))
    out["silt"] = flat_ground("floodsilt", (164, 150, 116), 0.14)
    out["water"] = flat_ground("floodwater", (88, 104, 70), 0.1)
    return out
