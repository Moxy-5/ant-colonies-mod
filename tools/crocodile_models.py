"""The Nile crocodile (M9 river life, the river's top hunter: algae -> tilapia -> catfish -> crocodile), its model and
three looks for the user to pick in the model viewer (python tools/model_viewer.py crocodile).

From the user's inspiration picture (2026-11-15, a blocky Nile crocodile on a sandy bank) and the real animal, fun first
(animals are generalized, CLAUDE.md):
- A long, low body: a long flat snout with a raised nostril bump at the tip, a skull table with the eyes in raised
  turrets on top (so it can lie with only eyes and nostrils above the water), rows of white teeth showing along both
  jaws with the big fourth lower tooth outside the upper jaw, a lower jaw hinged far back so it gapes wide.
- Armour: a cluster of nuchal scutes behind the head, keeled scutes in rows across the back, a double crest down the
  tail that joins into one toward the tip.
- Short sprawling legs, five fingers in front, four webbed toes behind, dark claws; the legs out to the side at rest
  (lying on the bank), lifted under the body for the high walk.
- Not rigid: head, neck, two trunk bones and five tail bones in a chain, the jaw its own bone, each leg three bones, so
  it can swim with its tail, walk, gape and roll.

Looks (my picks): A olive-bronze with dark bands (the user's picture), B an old dark male, C a young one, bright and
boldly banded.
Units are model pixels (16 a block), the head points to -Z, up is +Y, left is +X; y = 0 is the ground it lies on.
"""
import copy
import math
import random

from tilapia_models import curve, cube, mix, shade, faces, face_point, png
from catfish_models import vnoise

TEXELS = 1.0          # texture pixels a model pixel
SNOUT, TAIL_END = -34.0, 34.0
SPLIT = 2.6           # the mouth line, where the lower jaw parts from the head
HINGE = -21.5         # the jaw's hinge, behind the eyes
BELLY = 0.6           # lying on the bank, the belly just off the ground

CHAIN = [('head', 'body', SNOUT, -22.0), ('neck', 'body', -22.0, -17.0), ('chest', 'body', -17.0, -7.0),
         ('hips', 'chest', -7.0, 3.0), ('tail1', 'hips', 3.0, 9.0), ('tail2', 'tail1', 9.0, 15.0),
         ('tail3', 'tail2', 15.0, 21.0), ('tail4', 'tail3', 21.0, 27.0), ('tail5', 'tail4', 27.0, TAIL_END)]
# The swim wave (shared with the viewer): bone, parent, how far it swings, how late (radians).
WAVE = [('neck', 'body', -0.03, 0.0), ('chest', 'body', 0.02, 0.5), ('hips', 'chest', 0.05, 1.0),
        ('tail1', 'hips', 0.12, 1.6), ('tail2', 'tail1', 0.2, 2.2), ('tail3', 'tail2', 0.28, 2.8),
        ('tail4', 'tail3', 0.36, 3.4), ('tail5', 'tail4', 0.44, 4.0)]

# Profiles along the body, by z: top (y), bottom (y), half-width.
TOP = [(-34, 3.5), (-30, 3.8), (-26, 4.5), (-23, 5.3), (-20, 5.9), (-15, 6.8), (-8, 7.5), (-2, 7.4), (3, 6.7),
       (10, 5.6), (18, 4.5), (26, 3.4), (34, 2.4)]
BOT = [(-34, 1.4), (-28, 1.1), (-22, 0.9), (-17, BELLY), (0, BELLY), (3, 0.7), (10, 0.9), (20, 1.2), (34, 1.5)]
WIDE = [(-34, 1.75), (-32, 1.95), (-30, 1.85), (-26, 2.5), (-23, 3.3), (-21, 3.2), (-17, 4.1), (-9, 5.3), (-3, 5.1),
        (3, 4.2), (10, 3.0), (18, 2.0), (26, 1.2), (34, 0.45)]


def prof(points, z):
    if z <= points[0][0]:
        return points[0][1]
    for (z0, v0), (z1, v1) in zip(points, points[1:]):
        if z <= z1:
            return curve([(0, v0), (1, v1)], (z - z0) / (z1 - z0))
    return points[-1][1]


def at(z):
    return prof(TOP, z), prof(BOT, z), prof(WIDE, z)


def owner(z):
    for name, _, z0, z1 in CHAIN:
        if z0 <= z < z1:
            return name
    return 'tail5'


def body_slices(bones):
    """Rounded slices, each a narrow full-height box and a wide lower one (a broad back, a flat belly). In the head every
    slice is cut at the mouth line, the lower part going to the jaw; the jaw runs back to its hinge."""
    for name, _, z0, z1 in CHAIN:
        step = 1.0 if name == 'head' else 1.25 if name == 'neck' else 2.0
        n = max(1, round((z1 - z0) / step))
        for i in range(n):
            a, b = z0 + (z1 - z0) * i / n, z0 + (z1 - z0) * (i + 1) / n
            top, bot, w = at((a + b) / 2)
            part = 'head' if name == 'head' else 'body'
            for hw, k in ((w * 0.72, 1.0), (w, 0.72)):
                lo, hi = bot, bot + (top - bot) * k
                if name == 'head' or (name == 'neck' and b <= HINGE + 1e-6):
                    if hi > SPLIT:
                        bones['head'].append(cube([-hw, SPLIT, a], [hw, hi, b], 'head'))
                    bones['jaw'].append(cube([-hw * 0.96, lo, a], [hw * 0.96, SPLIT, b], 'jaw'))
                else:
                    bones[name].append(cube([-hw, lo, a], [hw, hi, b], part))


def head_parts(bones):
    h, j = bones['head'], bones['jaw']
    # the nostril bump on the snout's tip, two dark nostrils on it
    top = at(-32.5)[0]
    h.append(cube([-1.0, top - 0.1, -33.6], [1.0, top + 0.55, -31.6], 'head'))
    for s in (1, -1):
        x0, x1 = sorted([s * 0.25, s * 0.75])
        h.append(cube([x0, top + 0.55, -33.1], [x1, top + 0.6, -32.5], 'nostril'))
    # the skull table and the eye turrets: raised, so it can lie with only these above the water
    top = at(-24.5)[0]
    h.append(cube([-2.6, top - 0.2, -26.0], [2.6, top + 0.35, -21.6], 'head'))
    for s in (1, -1):
        x0, x1 = sorted([s * 0.7, s * 2.5])
        h.append(cube([x0, top + 0.2, -25.9], [x1, top + 1.6, -23.2], 'brow'))
        x0, x1 = sorted([s * 2.45, s * 2.62])
        h.append(cube([x0, top + 0.35, -25.5], [x1, top + 1.4, -23.6], 'iris'))
        x0, x1 = sorted([s * 2.55, s * 2.66])
        h.append(cube([x0, top + 0.4, -24.75], [x1, top + 1.35, -24.4], 'pupil'))
    # teeth: the upper row down over the lower jaw's edge, the lower row up inside them, the big fourth lower tooth
    # standing up outside the upper jaw in its notch
    z = -33.4
    while z < -23.5:
        w = at(z + 0.2)[2]
        for s in (1, -1):
            x0, x1 = sorted([s * (w + 0.12), s * (w - 0.3)])
            h.append(cube([x0, SPLIT - 0.95, z], [x1, SPLIT, z + 0.5], 'tooth'))
        z += 1.15
    z = -32.8
    while z < -24.0:
        w = at(z + 0.2)[2] * 0.96
        for s in (1, -1):
            x0, x1 = sorted([s * (w / 0.96 + 0.1), s * (w / 0.96 - 0.3)])
            j.append(cube([x0, SPLIT, z], [x1, SPLIT + 0.8, z + 0.45], 'tooth'))
        z += 1.15
    w = at(-30.6)[2]
    for s in (1, -1):
        x0, x1 = sorted([s * (w - 0.2), s * (w + 0.25)])
        j.append(cube([x0, SPLIT - 0.2, -31.0], [x1, SPLIT + 1.3, -30.3], 'tooth'))
    # the inside of the mouth: a pale floor and a fleshy palate, seen when it gapes
    for z0, z1 in ((-33.0, -28.0), (-28.0, -23.0)):
        w = at((z0 + z1) / 2)[2] * 0.82
        j.append(cube([-w, SPLIT, z0], [w, SPLIT + 0.05, z1], 'mouth'))
        h.append(cube([-w, SPLIT - 0.05, z0], [w, SPLIT, z1], 'mouth'))


def scutes(bones):
    """The armour: nuchal scutes behind the head, rows of keeled scutes across the back, a double crest down the tail
    joining into one."""
    for x, z in ((1.0, -20.9), (-1.0, -20.9), (1.0, -19.5), (-1.0, -19.5), (2.5, -20.2), (-2.5, -20.2)):
        top = at(z)[0]
        bones[owner(z)].append(cube([x - 0.6, top - 0.1, z - 0.6], [x + 0.6, top + 0.75, z + 0.6], 'scute'))
    z = -16.6
    row = 0
    while z < 2.6:
        top, _, w = at(z + 0.7)
        across = [0.75, 2.05, 3.25] if w > 4.4 else [0.75, 2.05]
        for k, x in enumerate(across):
            x = x * min(1.0, w / 4.6) if k else x
            hgt = 0.85 - 0.18 * k
            for s in (1, -1):
                x0, x1 = sorted([s * (x - 0.5), s * (x + 0.5)])
                bones[owner(z)].append(cube([x0, top - 0.1, z], [x1, top + hgt, z + 1.35], 'scute'))
        z += 1.75
        row += 1
    z = 3.0
    while z < 33.0:
        top, _, w = at(z + 0.6)
        f = (z - 3.0) / 30.0
        hgt = 1.35 - 0.85 * f
        if z < 20.0:      # the double crest
            x = max(0.55, w * 0.55)
            for s in (1, -1):
                x0, x1 = sorted([s * (x - 0.28), s * (x + 0.28)])
                bones[owner(z + 0.6)].append(cube([x0, top - 0.1, z], [x1, top + hgt, z + 1.2], 'crest'))
        else:             # one crest down the middle
            bones[owner(z + 0.6)].append(cube([-0.3, top - 0.1, z], [0.3, top + hgt, z + 1.2], 'crest'))
        z += 1.5


def toe(x, y, z, dx, dz, length, width, part='toe'):
    """A finger or toe as a box from (x, y, z) running length along (dx, dz) (one of the four flat directions), with a
    dark claw on its end."""
    out = []
    if dz:
        z1 = z + dz * length
        out.append(cube([x - width / 2, y, min(z, z1)], [x + width / 2, y + 0.38, max(z, z1)], part))
        c = z1 + dz * 0.4
        out.append(cube([x - width * 0.35, y, min(z1, c)], [x + width * 0.35, y + 0.25, max(z1, c)], 'claw'))
    else:
        x1 = x + dx * length
        out.append(cube([min(x, x1), y, z - width / 2], [max(x, x1), y + 0.38, z + width / 2], part))
        c = x1 + dx * 0.4
        out.append(cube([min(x1, c), y, z - width * 0.35], [max(x1, c), y + 0.25, z + width * 0.35], 'claw'))
    return out


def legs(out):
    """Each leg three bones (upper, lower, foot), at rest sprawled out to the side with the foot flat on the ground, as
    it lies on a bank."""
    for s, side in ((1, 'left'), (-1, 'right')):
        sx = lambda a, b: sorted([s * a, s * b])
        for kind, z, sh, up, low, foot, digits, length, parent in (
                ('arm', -14.0, 3.6, 4.2, 2.3, 1.9, 5, 1.2, 'chest'),
                ('leg', 0.0, 3.8, 5.0, 2.8, 2.4, 4, 1.7, 'hips')):
            elbow = sh + up
            x0, x1 = sx(sh - 0.4, elbow + 0.5)
            upper = [cube([x0, 1.0, z - low * 0.5], [x1, 1.0 + low, z + low * 0.5], 'leg')]
            x0, x1 = sx(elbow - low * 0.4, elbow + low * 0.45)
            lower = [cube([x0, 0.35, z - low * 0.42], [x1, 1.0 + low * 0.9, z + low * 0.42], 'leg')]
            # the foot flat on the ground, turned a little out; the digits spread forward and out
            fx = elbow + 0.3
            x0, x1 = sx(fx - foot * 0.55, fx + foot * 0.65)
            pad = [cube([x0, 0.0, z - foot * 0.9], [x1, 0.45, z + foot * 0.5], 'foot')]
            spread = [(-0.5, -1), (0.0, -1), (0.5, -1), (1.0, -1), (1.1, 0)][:digits] if digits == 5 else \
                [(-0.6, -1), (-0.05, -1), (0.5, -1), (1.0, -1)]
            for i, (off, dz) in enumerate(spread):
                if dz:
                    pad += toe(s * (fx + off * foot * 0.6), 0.02, z - foot * 0.9, 0, -1,
                               length * (0.85 + 0.25 * (1 - abs(i - 1.5) / 2)), 0.42, 'toe')
                else:
                    pad += toe(s * (fx + foot * 0.65), 0.02, z - foot * 0.1, s, 0, length * 0.7, 0.4, 'toe')
            if digits == 4:   # the webbing between the hind toes
                x0, x1 = sx(fx - foot * 0.45, fx + foot * 0.65)
                pad.append(cube([x0, 0.02, z - foot * 0.9 - length * 0.8], [x1, 0.2, z - foot * 0.9], 'web'))
            out.append({'name': f'{kind}_upper_{side}', 'parent': parent, 'pivot': [s * sh, 1.0 + low * 0.5, z],
                        'cubes': upper})
            out.append({'name': f'{kind}_lower_{side}', 'parent': f'{kind}_upper_{side}',
                        'pivot': [s * elbow, 1.0 + low * 0.5, z], 'cubes': lower})
            out.append({'name': f'{kind}_foot_{side}', 'parent': f'{kind}_lower_{side}', 'pivot': [s * fx, 0.4, z],
                        'cubes': pad})


def model():
    bones = {name: [] for name, *_ in CHAIN}
    bones['jaw'] = []
    body_slices(bones)
    head_parts(bones)
    scutes(bones)
    out = [{'name': 'body', 'pivot': [0, 3.0, -6.0]}]
    for name, parent, z0, _ in CHAIN:
        if name == 'head':
            parent, z0 = 'neck', -22.0
        out.append({'name': name, 'parent': parent, 'pivot': [0, 3.0, z0], 'cubes': bones[name]})
    out.append({'name': 'jaw', 'parent': 'head', 'pivot': [0, SPLIT, HINGE], 'cubes': bones['jaw']})
    legs(out)
    return out


# --- colours -----------------------------------------------------------------------------------------------------------
LOOKS = {
    'A': {'name': 'Olive-bronze, banded', 'note': "your picture: a dark olive back, olive-bronze sides blotched darker, "
          "dark bands across the back and the tail, a creamy yellow belly and lower jaw, darker scutes, a yellow eye "
          "with a black slit",
          'back': (70, 74, 40), 'flank': (122, 118, 60), 'belly': (218, 202, 138), 'band': 0.6, 'mottle': 0.78,
          'scute': 0.8, 'iris': (226, 188, 40), 'claw': (52, 44, 34)},
    'B': {'name': 'Old dark male', 'note': "a big old male: almost black-olive on the back, dull grey-olive sides, the "
          "bands faded into the dark, a dirty cream belly; the look of the biggest crocodiles",
          'back': (46, 48, 36), 'flank': (86, 86, 62), 'belly': (196, 186, 148), 'band': 0.82, 'mottle': 0.84,
          'scute': 0.82, 'iris': (196, 160, 52), 'claw': (40, 36, 30)},
    'C': {'name': 'Young, bright banded', 'note': "a young crocodile: bright yellow-olive with bold black bands and "
          "spots, a pale belly; young Nile crocodiles are much more boldly marked than old ones",
          'back': (96, 104, 46), 'flank': (150, 150, 70), 'belly': (232, 226, 182), 'band': 0.42, 'mottle': 0.62,
          'scute': 0.82, 'iris': (220, 200, 70), 'claw': (50, 44, 34)},
}


def band(z):
    """Dark cross bands on the back and tail: about every 5 pixels, wider and plainer toward the tail."""
    if z < -18:
        return False
    period = 5.0 if z < 3 else 4.2
    return (z + 40.0) % period < (1.5 if z < 3 else 1.8)


def colour(part, look, x, y, z, face, rnd):
    L = LOOKS[look]
    noise = rnd.uniform(0.94, 1.06)
    if part == 'mouth':
        return shade((206, 150, 120) if face == 'up' else (176, 104, 90), noise)
    if part in ('head', 'jaw', 'body', 'scute', 'crest', 'brow'):
        top, bot, w = at(max(SNOUT, min(z, TAIL_END)))
        f = (y - bot) / max(0.1, top - bot)           # 0 at the belly, 1 at the back
        if part == 'jaw':
            f = min(f, 0.22)
        if face == 'down' or f < 0.24:
            c = L['belly']
        elif f < 0.36:
            c = mix(L['belly'], L['flank'], (f - 0.24) / 0.12)
        else:
            c = mix(L['flank'], L['back'], min(1, (f - 0.36) / 0.4))
        if f >= 0.3 and face != 'down':
            n = 0.65 * vnoise(x / 1.6, y / 1.6, z / 1.6, 3) + 0.35 * vnoise(x / 0.7, y / 0.7, z / 0.7, 4)
            if n > 0.62:
                c = shade(c, L['mottle'])
            elif n < 0.3:
                c = shade(c, 1.1)
            if band(z) and f > 0.45:
                c = shade(c, L['band'])
        if part in ('scute', 'crest'):
            c = shade(c, L['scute'] if face == 'up' else L['scute'] * 0.92)
        if part == 'head' and z < -24 and face == 'up':
            ix, iz = math.floor(x * 2), math.floor(z * 2)
            c = shade(c, 0.9 if (ix * 5 + iz * 3) % 7 == 0 else 1.0)     # the snout's pitted skin
        if part in ('head', 'jaw') and abs(y - SPLIT) < 0.2 and face not in ('up', 'down'):
            c = shade(c, 0.55)                        # the mouth's line along the side
        return shade(c, noise)
    if part in ('leg', 'foot', 'toe', 'web'):
        c = L['belly'] if face == 'down' else mix(L['flank'], L['back'], 0.35)
        if face != 'down':
            n = vnoise(x / 1.2, y / 1.2, z / 1.2, 5)
            c = shade(c, L['mottle'] if n > 0.6 else 1.08 if n < 0.3 else 1.0)
        if part == 'web':
            c = shade(c, 0.85)
        return shade(c, noise)
    if part == 'claw':
        return shade(L['claw'], noise)
    if part == 'tooth':
        return shade((238, 230, 206), noise)
    if part == 'nostril':
        return (24, 22, 18)
    if part == 'iris':
        return shade(L['iris'], noise)
    if part == 'pupil':
        return (14, 12, 10)
    return (255, 0, 255)


def paint(bones, look):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
    rnd = random.Random(23)
    bones = copy.deepcopy(bones)
    patches = []
    for b in bones:
        for c in b.get('cubes', []):
            c['uv'] = {}
            for face, (w, h) in faces(c).items():
                patches.append((max(1, round(w * TEXELS)), max(1, round(h * TEXELS)), c, face))
    patches.sort(key=lambda p: (-p[1], -p[0]))
    for width in (64, 128, 256, 512, 1024):
        x = y = shelf = 0
        places = []
        for w, h, c, face in patches:
            if x + w > width:
                x, y, shelf = 0, y + shelf, 0
            places.append((x, y, w, h, c, face))
            x, shelf = x + w, max(shelf, h)
        if y + shelf <= width:
            break
    height = 16
    while height < y + shelf:
        height *= 2
    rows = [[(0, 0, 0, 0)] * width for _ in range(height)]
    for x0, y0, w, h, c, face in places:
        c['uv'][face] = {'uv': [x0, y0], 'uv_size': [w, h]}
        part = c['_part']
        for jj in range(h):
            for ii in range(w):
                px, py, pz = face_point(c, face, (ii + 0.5) / w, (jj + 0.5) / h)
                rgb = colour(part, look, px, py, pz, face, rnd)
                rows[y0 + jj][x0 + ii] = tuple(max(0, min(255, int(v))) for v in rgb) + (255,)
    for b in bones:
        for c in b.get('cubes', []):
            c.pop('_part')
    return bones, rows, width, height


if __name__ == '__main__':
    bones = model()
    n = sum(len(b.get('cubes', [])) for b in bones)
    _, rows, w, h = paint(bones, 'A')
    print(f'{n} boxes, texture {w} x {h}')
