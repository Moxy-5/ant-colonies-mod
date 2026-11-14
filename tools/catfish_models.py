"""The river catfish (M9 river life, the top of the food chain: algae -> tilapia -> catfish), its model and three looks
for the user to pick in the model viewer (python tools/model_viewer.py catfish).

From the user's inspiration picture (2026-11-14, a blocky African sharptooth catfish) and the real fish, kept fun
first (animals are generalized, CLAUDE.md):
- A long body, round in front and flattened side to side toward the tail; a broad, flat head, wider than it is tall,
  with a bony, grainy helmet on top; small eyes high on the sides; a wide mouth at the front.
- Four pairs of pale barbels (whiskers): a long pair from the mouth's corners, a short pair up from the nostrils, and
  two pairs under the chin; each its own bone, so they sway.
- One long low back fin running nearly to the tail, a long fin under the tail too, a rounded tail fin, stout side fins
  behind the gills with a pale spine in front, small belly fins.
- Not rigid: the body is a chain of bones (head, front, middle, rear, hind, tail fin), each a stack of rounded slices, so
  it bends in a wave when it swims, more evenly along its length than the tilapia (a catfish swims like a long fish).
  The lower jaw is its own bone, so it can gape when it strikes.

Looks (my picks): A grey-brown mottled (the user's picture), B marbled olive, C dark slate.
Units are model pixels (16 a block), the head points to -Z, up is +Y, left is +X; y = MID runs through the body's middle.
"""
import copy
import math
import random

import tilapia_models as tm
from tilapia_models import curve, cube, mix, shade, faces, face_point, png

TEXELS = 1.5          # texture pixels a model pixel
MID = 4.0
SNOUT, HIND_END, TAIL_END = -20.0, 19.0, 26.0
SPLIT = MID - 0.5     # where the lower jaw parts from the head
JAW_END = SNOUT + 5.0  # how far back the gape runs

# The side profile (half-depths over and under MID) and half-width, by t = 0 at the snout to 1 at the tail's base.
TOP = [(0, 0.9), (0.08, 1.8), (0.18, 2.5), (0.32, 2.95), (0.5, 2.9), (0.7, 2.5), (0.88, 2.0), (1, 1.7)]
BOT = [(0, -1.0), (0.08, -1.7), (0.2, -2.5), (0.36, -2.85), (0.55, -2.7), (0.75, -2.2), (0.9, -1.85), (1, -1.6)]
WIDE = [(0, 2.0), (0.06, 3.0), (0.14, 3.4), (0.22, 3.1), (0.36, 2.5), (0.55, 1.9), (0.75, 1.3), (0.9, 0.95), (1, 0.8)]

CHAIN = [('head', 'body', SNOUT, -12.0), ('front', 'body', -12.0, -4.0), ('mid', 'front', -4.0, 4.0),
         ('rear', 'mid', 4.0, 11.0), ('hind', 'rear', 11.0, HIND_END), ('tail', 'hind', HIND_END, TAIL_END)]
# The swim wave: bone, parent, how far it swings, how late (radians) (shared with the viewer).
WAVE = [('head', 'body', -0.04, 0.0), ('front', 'body', 0.05, 0.7), ('mid', 'front', 0.1, 1.4),
        ('rear', 'mid', 0.17, 2.1), ('hind', 'rear', 0.26, 2.8), ('tail', 'hind', 0.36, 3.4)]


def at(z):
    t = (z - SNOUT) / (HIND_END - SNOUT)
    return curve(TOP, t), curve(BOT, t), curve(WIDE, t)


def owner(z):
    for name, _, z0, z1 in CHAIN:
        if z0 <= z < z1:
            return name
    return 'tail'


def body_slices(bones):
    """Rounded slices (a tall narrow box and a short wide one each); at the front of the head every slice is cut at the
    mouth line, the lower half going to the jaw."""
    for name, _, z0, z1 in CHAIN[:-1]:
        n = max(1, round((z1 - z0) / (1.0 if name == 'head' else 1.6)))
        for i in range(n):
            a, b = z0 + (z1 - z0) * i / n, z0 + (z1 - z0) * (i + 1) / n
            top, bot, w = at((a + b) / 2)
            part = 'head' if name == 'head' else 'body'
            for hw, k in ((w * 0.62, 1.0), (w, 0.66)):
                lo, hi = MID + bot * k, MID + top * k
                if name == 'head' and b <= JAW_END + 1e-6:
                    bones['head'].append(cube([-hw, SPLIT, a], [hw, hi, b], 'head'))
                    bones['jaw'].append(cube([-hw * 0.94, lo, a], [hw * 0.94, SPLIT, b], 'jaw'))
                else:
                    bones[name].append(cube([-hw, lo, a], [hw, hi, b], part))


def head_parts(bones):
    h, j = bones['head'], bones['jaw']
    w0 = at(SNOUT + 0.5)[2]
    h.append(cube([-w0 * 0.9, SPLIT, SNOUT - 0.4], [w0 * 0.9, SPLIT + 0.55, SNOUT + 0.8], 'lip'))
    j.append(cube([-w0 * 0.85, SPLIT - 0.5, SNOUT - 0.25], [w0 * 0.85, SPLIT, SNOUT + 0.8], 'lip'))
    # the grainy helmet on top of the head, running back in a point over the neck
    for z0, z1, k in ((SNOUT + 1.0, -14.0, 0.7), (-14.0, -12.0, 0.45), (-12.0, -10.5, 0.2)):
        top, _, w = at((z0 + z1) / 2)
        bones[owner(z0)].append(cube([-w * k, MID + top - 0.15, z0], [w * k, MID + top + 0.22, z1], 'helmet'))
    # small eyes high on the head's sides
    ez = SNOUT + 4.0
    top, _, w = at(ez)
    ey = MID + top * 0.55
    for s in (1, -1):
        x0, x1 = sorted([s * (w * 0.86), s * (w * 0.86 + 0.25)])
        h.append(cube([x0, ey - 0.45, ez - 0.45], [x1, ey + 0.45, ez + 0.45], 'iris'))
        x0, x1 = sorted([s * (w * 0.86), s * (w * 0.86 + 0.4)])
        h.append(cube([x0, ey - 0.25, ez - 0.2], [x1, ey + 0.25, ez + 0.25], 'pupil'))
    # the gill opening's edge, a little proud, low on the side behind the head
    gz = -12.6
    top, bot, w = at(gz)
    for s in (1, -1):
        xs = sorted([s * (w * 0.6), s * (w + 0.12)])
        h.append(cube([xs[0], MID + bot * 0.7, gz], [xs[1], MID + top * 0.25, gz + 0.5], 'gill'))


def fin_columns(bones, z0, z1, base, height, part, width=1.5):
    z = z0
    while z < z1 - 1e-6:
        b = min(z1, z + width)
        mz = (z + b) / 2
        top, bot, _ = at(min(mz, HIND_END - 0.01))
        hgt = height(mz)
        if hgt > 0.05:
            if base == 'top':
                y0 = MID + top - 0.3
                c = cube([-0.17, y0, z], [0.17, y0 + hgt, b], part)
            else:
                y1 = MID + bot + 0.3
                c = cube([-0.17, y1 - hgt, z], [0.17, y1, b], part)
            bones[owner(mz)].append(c)
        z = b


def fins(bones):
    span = lambda pts, z0, z1: (lambda z: curve([((a - z0) / (z1 - z0), v) for a, v in pts], (z - z0) / (z1 - z0)))
    dorsal = [(-7.0, 0.8), (-5.0, 2.0), (2.0, 2.4), (12.0, 2.7), (17.0, 2.3), (19.0, 0.6)]
    fin_columns(bones, -7.0, 19.0, 'top', span(dorsal, -7.0, 19.0), 'dorsal')
    anal = [(1.0, 0.6), (3.0, 1.7), (10.0, 2.1), (16.0, 2.0), (18.5, 0.5)]
    fin_columns(bones, 1.0, 18.5, 'bot', span(anal, 1.0, 18.5), 'anal')
    tail = [(0, 1.5), (0.25, 2.1), (0.5, 2.5), (0.72, 2.45), (0.88, 1.9), (1, 1.0)]
    z = HIND_END
    while z < TAIL_END - 1e-6:
        b = min(TAIL_END, z + 1.0)
        hh = curve(tail, ((z + b) / 2 - HIND_END) / (TAIL_END - HIND_END))
        bones['tail'].append(cube([-0.17, MID - hh, z], [0.17, MID + hh, b], 'caudal'))
        z = b


def strand(length, thick, part, n, droop=0.0, flat=False):
    """A fin or a barbel in its own bone's space: from its root (0, 0, 0) back along +z, tapering; droop bends it down."""
    cubes = []
    for i in range(n):
        a, b = length * i / n, length * (i + 1) / n
        t = thick[i] if isinstance(thick, list) else thick * (1 - 0.45 * i / max(1, n - 1))
        y = -droop * (i / n) ** 1.6
        if flat:      # a fin: wide across, thin up and down
            cubes.append(cube([-t / 2, y - 0.13, a], [t / 2, y + 0.13, b], part))
        else:
            cubes.append(cube([-t / 2, y - t / 2, a], [t / 2, y + t / 2, b], part))
    return cubes


def placed(cubes, p):
    return [dict(c, origin=[c['origin'][0] + p[0], c['origin'][1] + p[1], c['origin'][2] + p[2]]) for c in cubes]


def model():
    bones = {name: [] for name, *_ in CHAIN}
    bones['jaw'] = []
    body_slices(bones)
    head_parts(bones)
    fins(bones)
    out = [{'name': 'body', 'pivot': [0, MID, -2.0]}]
    for name, parent, z0, _ in CHAIN:
        out.append({'name': name, 'parent': parent, 'pivot': [0, MID, z0], 'cubes': bones[name]})
    out.append({'name': 'jaw', 'parent': 'head', 'pivot': [0, SPLIT, JAW_END], 'cubes': bones['jaw']})
    w0 = at(SNOUT + 1.0)[2]
    for s, side in ((1, 'left'), (-1, 'right')):
        # side fins: broad, held out flat, a pale stout spine along the front edge
        top, bot, w = at(-11.0)
        p = [s * (w - 0.2), MID + bot * 0.55, -11.2]
        lead = 1.3      # the leading edge straight, the spine along it, the fin rounding off behind
        fin = [dict(c, origin=[s * lead - (c['size'][0] if s > 0 else 0), *c['origin'][1:]])
               for c in strand(5.5, [2.6, 2.6, 2.3, 1.9, 1.4, 0.8], 'pectoral', 6, flat=True)]
        spine = [dict(c, origin=[s * lead - c['size'][0] / 2, *c['origin'][1:]]) for c in strand(4.6, 0.45, 'spine', 4)]
        out.append({'name': f'pectoral_{side}', 'parent': 'front', 'pivot': p, 'rotation': [-8, s * 55, s * -8],
                    'cubes': placed(fin + spine, p)})
        top, bot, w = at(0.0)
        p = [s * w * 0.5, MID + bot + 0.3, 0.0]
        out.append({'name': f'pelvic_{side}', 'parent': 'mid', 'pivot': p, 'rotation': [-20, s * 30, 0],
                    'cubes': placed(strand(3.0, [1.6, 1.4, 1.0, 0.5], 'pelvic', 4, flat=True), p)})
        # the barbels: the long pair from the mouth's corners (two bones, to curl), the nasal pair up off the snout,
        # and two pairs under the chin on the jaw
        p = [s * w0 * 0.92, SPLIT + 0.2, SNOUT + 1.0]
        out.append({'name': f'barbel_long_{side}', 'parent': 'head', 'pivot': p, 'rotation': [-6, s * 62, 0],
                    'cubes': placed(strand(6.0, 0.34, 'barbel', 4, droop=0.2), p)})
        tip = [p[0], p[1] - 0.3, p[2] + 6.0]
        out.append({'name': f'barbel_long_tip_{side}', 'parent': f'barbel_long_{side}', 'pivot': tip,
                    'rotation': [-4, s * 24, 0], 'cubes': placed(strand(6.0, 0.24, 'barbel_tip', 4, droop=0.35), tip)})
        top = at(SNOUT + 1.6)[0]
        p = [s * 1.1, MID + top - 0.1, SNOUT + 1.6]
        out.append({'name': f'barbel_nose_{side}', 'parent': 'head', 'pivot': p, 'rotation': [24, s * 30, 0],
                    'cubes': placed(strand(3.0, 0.26, 'barbel', 3), p)})
        bot = at(SNOUT + 2.6)[1]
        p = [s * 1.9, MID + bot * 0.66 + 0.05, SNOUT + 2.6]
        out.append({'name': f'barbel_chin_out_{side}', 'parent': 'jaw', 'pivot': p, 'rotation': [-14, s * 132, 0],
                    'cubes': placed(strand(5.0, 0.28, 'barbel', 4, droop=0.5), p)})
        p = [s * 0.8, MID + bot * 0.66 + 0.05, SNOUT + 2.1]
        out.append({'name': f'barbel_chin_in_{side}', 'parent': 'jaw', 'pivot': p, 'rotation': [-18, s * 158, 0],
                    'cubes': placed(strand(3.6, 0.26, 'barbel', 3, droop=0.4), p)})
    return out


# --- colours -----------------------------------------------------------------------------------------------------------
LOOKS = {
    'A': {'name': 'Grey-brown, mottled', 'note': "your picture: a dark grey-brown back and sides broken up by darker and "
          "lighter blotches, a cream belly and lower jaw with a sharp edge, dark mottled fins, pale pinkish barbels, the "
          "grainy helmet on the head a shade darker",
          'back': (66, 60, 52), 'flank': (108, 98, 84), 'belly': (226, 214, 186), 'dark': 0.66, 'light': 1.16,
          'grain': 2.0, 'fin': (62, 56, 50), 'barbel': (214, 182, 166), 'tip': (232, 210, 196), 'spine': (196, 180, 156),
          'iris': (150, 128, 84), 'line': 0.3},
    'B': {'name': 'Marbled olive', 'note': "olive-green with bold dark marbling down the sides, as river catfish in clear "
          "water look, a yellowish cream belly, olive fins marbled too, the barbels a little darker",
          'back': (62, 68, 44), 'flank': (120, 124, 82), 'belly': (224, 216, 170), 'dark': 0.55, 'light': 1.2,
          'grain': 1.5, 'fin': (70, 74, 50), 'barbel': (196, 172, 140), 'tip': (220, 202, 172), 'spine': (190, 182, 140),
          'iris': (160, 140, 70), 'line': 0.34},
    'C': {'name': 'Dark slate', 'note': "almost black on the back fading to slate grey, faint mottling, an off-white belly, "
          "near-black fins: a catfish from muddy water, the darkest shape in the river",
          'back': (38, 40, 44), 'flank': (72, 74, 78), 'belly': (204, 198, 186), 'dark': 0.8, 'light': 1.08,
          'grain': 2.4, 'fin': (36, 36, 40), 'barbel': (190, 170, 160), 'tip': (214, 200, 192), 'spine': (150, 146, 140),
          'iris': (130, 112, 80), 'line': 0.26},
}


def vnoise(x, y, z, seed):
    """Smooth value noise, 0 to 1."""
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = (f * f * (3 - 2 * f) for f in (fx, fy, fz))

    def h(a, b, c):
        n = (a * 374761393 + b * 668265263 + c * 1274126177 + seed * 974634431) & 0xFFFFFFFF
        n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
        return (n ^ (n >> 16)) / 4294967296.0

    def lerp(a, b, f):
        return a + (b - a) * f
    x0 = lerp(lerp(h(ix, iy, iz), h(ix + 1, iy, iz), fx), lerp(h(ix, iy + 1, iz), h(ix + 1, iy + 1, iz), fx), fy)
    x1 = lerp(lerp(h(ix, iy, iz + 1), h(ix + 1, iy, iz + 1), fx), lerp(h(ix, iy + 1, iz + 1), h(ix + 1, iy + 1, iz + 1), fx), fy)
    return lerp(x0, x1, fz)


def mottle(L, look, x, y, z):
    g = L['grain']
    n = 0.65 * vnoise(x / g, y / g, z / g, 1) + 0.35 * vnoise(x / (g * 0.45), y / (g * 0.45), z / (g * 0.45), 2)
    if look == 'B':     # marbling: the blotches drawn out into wavy veins
        n = 0.5 + 0.5 * math.sin(n * 13.0 + z * 0.35)
        return L['dark'] if n > 0.78 else L['light'] if n < 0.12 else 1.0
    return L['dark'] if n > 0.6 else L['light'] if n < 0.36 else 1.0


def colour(part, look, x, y, z, face, edge, rnd):
    L = LOOKS[look]
    noise = rnd.uniform(0.95, 1.05)
    if part in ('head', 'jaw') and z < JAW_END and abs(y - SPLIT) < 0.06 and face in ('up', 'down'):
        return (118, 70, 70)                                    # the inside of the mouth, seen when it gapes
    if part in ('body', 'head', 'jaw', 'gill', 'helmet'):
        top, bot, _ = at(max(SNOUT, min(z, HIND_END)))
        f = (y - MID - bot) / max(0.1, top - bot)               # 0 at the belly, 1 at the back
        line = L['line']
        if part == 'jaw':
            f = min(f, line - 0.02)                             # the lower jaw is all cream
        if f < line:
            c = L['belly']
        elif f < line + 0.1:
            c = mix(L['belly'], L['flank'], (f - line) / 0.1)
        else:
            c = mix(L['flank'], L['back'], min(1, (f - line - 0.1) / 0.45))
        if f > line + 0.04:
            c = shade(c, mottle(L, look, x, y, z))
        if part == 'helmet':
            ix, iz = math.floor(x * TEXELS), math.floor(z * TEXELS)
            c = shade(c, 0.82 if (ix * 3 + iz) % 4 == 0 else 0.92)      # the bony helmet's grain
        if part == 'gill':
            c = shade(c, 0.7)
        if part in ('head', 'jaw') and z < SNOUT + 0.4 and abs(y - SPLIT) < 0.25:
            c = (40, 28, 28)                                    # the closed mouth's line across the front
        return shade(c, noise)
    if part == 'lip':
        return shade(mix(L['belly'], L['barbel'], 0.4), noise)
    if part == 'iris':
        return shade(L['iris'], noise)
    if part == 'pupil':
        return (12, 12, 14)
    if part == 'barbel':
        return shade(L['barbel'], noise)
    if part == 'barbel_tip':
        return shade(L['tip'], noise)
    if part == 'spine':
        return shade(L['spine'], noise)
    if part in ('dorsal', 'anal', 'caudal', 'pectoral', 'pelvic'):
        c = mix(L['fin'], L['flank'], 0.4)                      # fins a shade lighter than the back, so they read
        c = shade(c, mottle(L, look, x * 1.3, y * 1.3, z * 1.3) ** 0.6)
        if part in ('dorsal', 'anal') and math.floor(z * TEXELS) % 3 == 0:
            c = shade(c, 0.8)                                   # the soft rays
        if part == 'caudal' and math.floor(y * TEXELS) % 3 == 0:
            c = shade(c, 0.82)
        if edge > 0.86:
            c = shade(c, 0.62)                                  # a dark rim
        return shade(c, noise)
    return (255, 0, 255)


def paint(bones, look):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
    rnd = random.Random(11)
    bones = copy.deepcopy(bones)
    patches = []
    for b in bones:
        for c in b.get('cubes', []):
            c['uv'] = {}
            for face, (w, h) in faces(c).items():
                patches.append((max(1, round(w * TEXELS)), max(1, round(h * TEXELS)), c, face))
    patches.sort(key=lambda p: (-p[1], -p[0]))
    for width in (64, 128, 256, 512):
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
        for j in range(h):
            for i in range(w):
                px, py, pz = face_point(c, face, (i + 0.5) / w, (j + 0.5) / h)
                lo, hi = c['origin'][1], c['origin'][1] + c['size'][1]
                if part == 'anal':
                    edge = (hi - py) / max(0.1, hi - lo)
                elif part == 'caudal':
                    edge = max(abs(py - MID) / max(0.1, (hi - lo) / 2), (pz - HIND_END) / (TAIL_END - HIND_END))
                elif part in ('pectoral', 'pelvic'):
                    edge = 0
                else:
                    edge = (py - lo) / max(0.1, hi - lo)
                rgb = colour(part, look, px, py, pz, face, edge, rnd)
                rows[y0 + j][x0 + i] = tuple(max(0, min(255, int(v))) for v in rgb) + (255,)
    for b in bones:
        for c in b.get('cubes', []):
            c.pop('_part')
    return bones, rows, width, height


if __name__ == '__main__':
    bones = model()
    n = sum(len(b.get('cubes', [])) for b in bones)
    _, rows, w, h = paint(bones, 'A')
    print(f'{n} boxes, texture {w} x {h}')
