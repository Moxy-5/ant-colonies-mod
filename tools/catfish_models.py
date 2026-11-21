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
`python tools/catfish_models.py game` writes the game's files (model, look A's texture, clips) into the savanna mod.
"""
import math
import random

import tilapia_models as tm
from model_parts import curve, cube, mix, shade, png, clip, rot_only, paint_faces, geo, animations, write_assets

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
    for z0, z1, k in ((SNOUT + 2.4, -14.0, 0.7), (-14.0, -12.0, 0.45), (-12.0, -10.5, 0.2)):
        top, _, w = at((z0 + z1) / 2)
        bones[owner(z0)].append(cube([-w * k, MID + top - 0.15, z0], [w * k, MID + top + 0.22, z1], 'helmet'))
    # small eyes high on the head's sides
    ez = SNOUT + 4.0
    top, _, w = at(ez)
    ey = MID + top * 0.55
    w = max(at(ez + d / 4)[2] for d in range(-6, 7))    # the widest slice the eye sits on, so it stands proud of it
    for s in (1, -1):
        x0, x1 = sorted([s * (w - 0.05), s * (w + 0.2)])
        h.append(cube([x0, ey - 0.45, ez - 0.45], [x1, ey + 0.45, ez + 0.45], 'iris'))
        x0, x1 = sorted([s * (w - 0.05), s * (w + 0.32)])
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
    # where a caught tilapia is held (no boxes: the game draws the tilapia's own model here, CatfishRenderer): at the
    # jaw's hinge, moved and turned by the catch clip
    out.append({'name': 'prey', 'parent': 'head', 'pivot': [0, SPLIT, JAW_END]})
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
        top = at(SNOUT + 1.5)[0]
        p = [s * 1.1, MID + top, SNOUT + 1.6]     # out of the snout, in front of the helmet
        out.append({'name': f'barbel_nose_{side}', 'parent': 'head', 'pivot': p, 'rotation': [50, s * 30, 0],
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

    def pixel(c, face, px, py, pz):
        part = c['_part']
        lo, hi = c['origin'][1], c['origin'][1] + c['size'][1]
        if part == 'anal':
            edge = (hi - py) / max(0.1, hi - lo)
        elif part == 'caudal':
            edge = max(abs(py - MID) / max(0.1, (hi - lo) / 2), (pz - HIND_END) / (TAIL_END - HIND_END))
        elif part in ('pectoral', 'pelvic'):
            edge = 0
        else:
            edge = (py - lo) / max(0.1, hi - lo)
        return colour(part, look, px, py, pz, face, edge, rnd)
    return paint_faces(bones, TEXELS, pixel)


# --- the game's files (the user, 2026-11-14: "A looks the best lets just do that for now and put it in the game") -------
# One model in look A (grey-brown mottled, the user's picture); 2 blocks long in the game (the user's pick).
GAME_TEXTURE = 'catfish'
BARBELS = [f'barbel_{kind}_{side}' for side in ('left', 'right')
           for kind in ('long', 'long_tip', 'nose', 'chin_out', 'chin_in')]


def wave_pose(t, amount, beat, curl=0, lag_scale=1.0):
    """Each bone's turn (degrees, the model file's rules) in the swim wave at time t, as the viewer swims it: the body
    bones' sideways angles a sine, bigger and later toward the tail, each less its parent's; the side fins flutter."""
    ab, out = {'body': 0.0}, {}
    for name, parent, a, lag in WAVE:
        wave = math.sin(beat * t - lag * lag_scale)
        ab[name] = amount * a * (wave if not curl else curl * (0.5 + 0.5 * wave))
        out[name] = [0.0, math.degrees(ab[name] - ab[parent]), 0.0]
    for i, side in enumerate(('left', 'right')):
        sign = -1 if i else 1
        out[f'pectoral_{side}'] = [0.0, math.degrees(sign * (0.25 + 0.75 * amount) * 0.15 * math.sin(beat * 2 * t + i * 0.5)), 0.0]
        out[f'pelvic_{side}'] = [-math.degrees(0.08 * math.sin(beat * t)), 0.0, 0.0]
    return out


def barbel_sway(t, out, k=1.0, period=None):
    """The whiskers sway gently on their own slow clock, as in the viewer (the tips more). With period, the clock is
    rounded to a whole number of sways in it, so a looping clip joins up."""
    for i, name in enumerate(BARBELS):
        a = (0.22 if 'tip' in name else 0.12) * k
        w1, w2 = 1.7, 1.3
        if period:
            w1 = 2 * math.pi * max(1, round(w1 * period / (2 * math.pi))) / period
            w2 = 2 * math.pi * max(1, round(w2 * period / (2 * math.pi))) / period
        out[name] = [-math.degrees(a * 0.6 * math.sin(t * w2 + i * 1.7)), math.degrees(a * math.sin(t * w1 + i * 0.9)), 0.0]
    return out


# The jaws' angle clamped on a caught tilapia lying flat (degrees, head and jaw together): a grown fish is about 1.5
# catfish pixels thick at the lips, 5 pixels from the hinge, and about 1 thick 3 pixels from it; a small one (drawn
# under 0.75 of a grown fish, CatfishEntity) about half that.
BITE, BITE_SMALL = 18.0, 11.0
CATCH_LEN = 2.6       # the catch clip (seconds; CatfishEntity.HOLD_TICKS is the same in ticks)
# The bite-off clip (the user, 2026-11-15: half the catches the mouth shuts early, the front half of the tilapia left):
# since the same day its own animation, the tilapia fighting until the catfish bites it through at its middle (fought,
# below; the times are the tilapia's, tm.FIGHT_*): the jaws shut through the fish at CUT_AT, done at CUT_LEN
# (seconds; CatfishEntity.CUT_TICKS and CUT_AT_TICK the same in ticks).
CUT_AT, CUT_LEN = tm.FIGHT_CUT, tm.FIGHT_LEN
COURT_LEN, COURT_BEAT, COURT_CURL = 2.0, 1.0, 4.0   # the courtship clip (seconds; its pulse; how hard the curl)
GULP_LEN = 1.5       # the gulp of air (seconds; CatfishEntity.GULP_TICKS is the same in ticks, plus its CLIP_LAG)


def clips():
    """catch (a caught tilapia held in the open mouth, shaken, gulped down); bite_off (the same, cut short: the jaws
    snap shut through it at the first gulp, the front half left); swim (a full beat, 1.2 s, slower and longer than the tilapia's; played faster or slower by the speed), idle
    (hanging still, the tail barely moving, the whiskers feeling), strike (a lunge with the mouth thrown open, snapped
    shut, the whiskers swept back), flop (out of water it stays upright and wriggles in S-curves, as real catfish crawl
    over wet ground), death (rolls onto its side)."""
    rot = rot_only
    swim_len = 1.2
    beat = 2 * math.pi / swim_len

    def swim(t):
        return rot(barbel_sway(t, wave_pose(t, 1.0, beat), 0.8, swim_len))

    def idle(t):
        p = rot(barbel_sway(t, wave_pose(t, 0.12, 2 * math.pi / 4.0), 1.0, 4.0))
        p['body'] = {'rotation': [0, 0, 0], 'position': [0, 0.25 * math.sin(2 * math.pi * t / 4.0), 0]}
        return p

    def strike(t):
        ease = lambda x: x * x * (3 - 2 * x)
        lunge = ease(min(1, t / 0.25)) if t < 0.45 else ease(max(0.0, (1.0 - t) / 0.55))
        gape = 0 if t < 0.05 else ease((t - 0.05) / 0.15) if t < 0.2 else ease(max(0.0, (0.38 - t) / 0.18)) if t < 0.38 else 0
        p = rot(barbel_sway(t, wave_pose(t, 0.5 + 1.2 * (1 - min(1, t / 0.6)), 2 * math.pi / 0.5), 0.3))
        p['jaw'] = {'rotation': [34 * gape, 0, 0]}
        p['head'] = {'rotation': [-8 * gape, p['head']['rotation'][1], 0]}
        for name in BARBELS:          # swept back by the rush of water
            r = p[name]['rotation']
            side = 1 if name.endswith('left') else -1
            p[name] = {'rotation': [r[0], r[1] - side * 18 * lunge, r[2]]}
        p['body'] = {'rotation': [0, 0, 0], 'position': [0, 0, -4 * lunge]}
        return p

    def flop(t):
        p = rot(barbel_sway(t, wave_pose(t, 2.2, 2 * math.pi / 0.8, lag_scale=0.8), 0.5, 0.8))
        p['body'] = {'rotation': [-4, 0, 0], 'position': [0, -1.2, 0]}
        return p

    def catch(t, bite, cut=False):
        """A tilapia caught (2026-11-15, the user's asks): the mouth, thrown wide by the strike, bites down on the
        fish, whose tail end lies between the jaws, and stays clamped on it (bite: the jaws' angle that just touches a
        fish lying flat, degrees; a squeeze past it at first); two shakes of the head, then three gulps, the jaws
        letting go a little for each as the fish goes in a step, and the mouth shuts over it. The 'prey' bone carries
        the fish: tilted to lie midway between the jaws, sliding in, gone at the end."""
        ease = lambda x: (lambda v: v * v * (3 - 2 * v))(max(0.0, min(1.0, x)))
        bump = lambda a, b: math.sin(math.pi * (t - a) / (b - a)) if a < t < b else 0.0
        gulp = bump(1.25, 1.55) + bump(1.75, 2.05) + bump(2.1, 2.3)
        shut = ease((t - 2.2) / 0.2)
        down = ease((t - 0.04) / 0.1)                       # the bite: wide to clamped in a tenth of a second
        squeeze = 0.12 * bump(0.1, 0.34)                    # and a squeeze, the jaws pressing into the fish
        if cut:
            return fought(t, bite, down, squeeze)
        angle = (42 + (bite - 42) * down - bite * squeeze + 9 * gulp) * (1 - shut)
        # held 2 pixels past the hinge (the tail's tip hidden in the head), so the mouth is round the fish's body
        slide = 2.0 + 3.5 * ease((t - 1.3) / 0.25) + 3.5 * ease((t - 1.8) / 0.25) + 3.0 * ease((t - 2.12) / 0.18)
        shake = 13 * math.sin(2 * math.pi * (t - 0.3) / 0.4) * ease((t - 0.3) / 0.1) * (1 - ease((t - 1.0) / 0.1))
        p = rot(barbel_sway(t, wave_pose(t, 0.6, 2 * math.pi / 1.3), 0.6))
        p['jaw'] = {'rotation': [0.8 * angle, 0, 0]}
        p['head'] = {'rotation': [-0.2 * angle - 4 * gulp, p['head']['rotation'][1] + shake, 0]}
        seen = 1.0 if t < 2.32 else 0.0
        # half the jaw's turn: the jaw hangs on the head, so this is midway between the two
        p['prey'] = {'rotation': [0.4 * angle, 0, 0], 'position': [0, 0, slide], 'scale': [seen, seen, seen]}
        p['body'] = {'rotation': [0, 0, 0], 'position': [0, 0, -4 * (1 - ease(t / 0.5)) - 1.2 * gulp]}
        return p

    def fought(t, bite, down, squeeze):
        """The catch that ends bitten in two (the user, 2026-11-15: its own animation, "the tilapia fights more and
        wiggles so the catfish closes early right where the indentation is on the remains"). The bite and one shake
        as in the catch; then the tilapia fights (its own clip 'caught_fight', on the same clock, tm.fight): each beat
        of its thrash pries the jaws a little apart and jolts the head, and it works itself a little way out of the
        mouth; the catfish lunges after it, opens to take a new hold, and slams shut on the fish's middle, where the
        remains' bitten end is (the lips over the fish 0.6 pixels in front of its middle, tm 'tear'), biting it
        through. The front half falls away (the game puts the remains there), the head recoils, one gulp of its half."""
        ease = lambda x: (lambda v: v * v * (3 - 2 * v))(max(0.0, min(1.0, x)))
        bump = lambda a, b: math.sin(math.pi * (t - a) / (b - a)) if a < t < b else 0.0
        f = tm.fight(t) * (1 - ease((t - tm.FIGHT_CLAMP + 0.12) / 0.1))      # the fight felt in the jaws, until the lunge
        beat = math.sin(tm.fight_phase(t))
        regrab = bump(tm.FIGHT_CLAMP - 0.14, tm.FIGHT_CLAMP + 0.02)          # the jaws open for a new hold
        shut = ease((t - tm.FIGHT_CLAMP - 0.02) / (tm.FIGHT_CUT - tm.FIGHT_CLAMP - 0.02))
        gulp = 0.7 * bump(1.72, 2.0)
        pried = f * (2.0 + 2.5 * max(0.0, beat))
        angle = (42 + (bite - 42) * down - bite * squeeze + pried + 8 * regrab) * (1 - shut) + 5 * gulp
        # working its way out (2 is the fish's middle at the lips), then caught again a little deeper: the lips come
        # down on the fish just in front of its middle, where the remains' bitten end is
        slide = 2.0 - 0.9 * tm.fight(t) * (1 - ease((t - tm.FIGHT_CLAMP + 0.14) / 0.14)) + 0.65 * ease((t - tm.FIGHT_CLAMP + 0.14) / 0.14)
        shake = 13 * math.sin(2 * math.pi * (t - 0.3) / 0.4) * ease((t - 0.3) / 0.1) * (1 - ease((t - 0.62) / 0.1))
        lunge = bump(tm.FIGHT_CLAMP - 0.16, tm.FIGHT_CUT + 0.25)
        recoil = bump(tm.FIGHT_CUT, tm.FIGHT_CUT + 0.3)                      # the head tossed up as the jaws meet
        p = rot(barbel_sway(t, wave_pose(t, 0.6 + 0.5 * f, 2 * math.pi / 1.3), 0.6))
        p['jaw'] = {'rotation': [0.8 * angle, 0, 0]}
        p['head'] = {'rotation': [-0.2 * angle - 4 * gulp - 2.2 * f * beat - 5 * recoil, p['head']['rotation'][1] + shake, 1.5 * f * beat]}
        seen = 1.0 if t < tm.FIGHT_CUT else 0.0                   # the front half falls away as the remains
        p['prey'] = {'rotation': [0.4 * angle, 0, 0], 'position': [0, 0, slide], 'scale': [seen, seen, seen]}
        p['body'] = {'rotation': [0, 0, 0], 'position': [0, 0.35 * f * beat, -4 * (1 - ease(t / 0.5)) - 1.8 * lunge - 1.0 * gulp]}
        for s, side in ((1, 'left'), (-1, 'right')):              # the side fins braced out against the pull
            r = p[f'pectoral_{side}']['rotation']
            p[f'pectoral_{side}'] = {'rotation': [r[0], r[1] - s * 16 * max(f, lunge), r[2]]}
        return p

    def gulp(t):
        """Up for a gulp of air (the user, 2026-11-18, "catfish coming up to gulp air to breathe"; real air-breathing
        catfish do this, fun first, my timings): the body is already tipped nose up by the game (CatfishEntity, about 35
        degrees); the tail drives it up, the head lifts the last bit so the snout breaks the surface, the whiskers fan
        out flat on the water, the mouth opens wide (0.35 to 0.55 s), hangs open a moment, snaps shut with a jerk of
        the head (0.75 s), then the head drops and the tail turns it down again."""
        ease = lambda x: (lambda v: v * v * (3 - 2 * v))(max(0.0, min(1.0, x)))
        up = ease(t / 0.35) * (1 - ease((t - 0.95) / 0.45))           # the head lifted to the air
        gape = ease((t - 0.35) / 0.2) * (1 - ease((t - 0.75) / 0.08))
        snap = math.sin(math.pi * (t - 0.75) / 0.25) if 0.75 < t < 1.0 else 0.0
        quiver = 0.08 * math.sin(2 * math.pi * t / 0.07) if 0.55 < t < 0.75 else 0.0
        drive = 0.9 * (1 - ease((t - 0.3) / 0.3)) + 0.35 + 0.4 * ease((t - 0.9) / 0.3)
        p = rot(barbel_sway(t, wave_pose(t, drive, 2 * math.pi / 0.6), 0.4))
        p['jaw'] = {'rotation': [36 * (gape + quiver * gape), 0, 0]}
        p['head'] = {'rotation': [-12 * up - 8 * gape + 7 * snap, p['head']['rotation'][1], 0]}
        for name in BARBELS:          # fanned out flat on the surface while the head is up
            r = p[name]['rotation']
            side = 1 if name.endswith('left') else -1
            p[name] = {'rotation': [r[0] - 14 * up, r[1] + side * 16 * up, r[2]]}
        p['body'] = {'rotation': [0, 0, 0], 'position': [0, 1.2 * up - 0.6 * snap, 0]}
        return p

    def court(t):
        """The male's courtship (animal life cycle step 3, 2026-11-19; fun first, my picks; real catfish spawn this
        way in flooded shallows after rain): his body curled hard to one side in a C round the female's tail, the curl
        pulsing in quick shivers, the whiskers swept back, the side fins fanning. The game keeps him alongside her tail
        while they court (CatfishEntity.courting)."""
        pose = wave_pose(t, 0.3, 2 * math.pi / COURT_BEAT)       # the side fins' and pelvic fins' flutter
        ab, beat = {'body': 0.0}, 2 * math.pi / COURT_BEAT
        for name, parent, a, lag in WAVE:      # a held C, tighter toward the tail, pulsing a little
            ab[name] = COURT_CURL * a * (0.8 + 0.2 * math.sin(beat * t - lag))
            pose[name] = [0.0, math.degrees(ab[name] - ab[parent]), 0.0]
        p = rot(barbel_sway(t, pose, 0.5, COURT_LEN))
        shiver = 0.6 * math.sin(2 * math.pi * t / 0.12)
        for name in ('mid', 'rear', 'hind'):
            r = p[name]['rotation']
            p[name] = {'rotation': [r[0], r[1] + shiver, r[2]]}
        for name in BARBELS:
            r = p[name]['rotation']
            side = 1 if name.endswith('left') else -1
            p[name] = {'rotation': [r[0], r[1] - side * 10, r[2]]}
        p['body'] = {'rotation': [0, 0, 6 * math.sin(2 * math.pi * t / COURT_BEAT)], 'position': [0, 0, 0]}
        return p

    lie = 1.4

    def death(t):
        f = min(1.0, t / 0.8)
        f = f * f * (3 - 2 * f)
        p = rot(wave_pose(t, 0.9 * (1 - f), 2 * math.pi / 0.8))
        for s, side in ((1, 'left'), (-1, 'right')):
            p[f'pectoral_{side}'] = {'rotation': [10 * f, -s * 30 * f, s * 10 * f]}
        p['jaw'] = {'rotation': [10 * f, 0, 0]}
        p['body'] = {'rotation': [0, 0, 80 * f], 'position': [0, -lie * f, 0]}
        return p

    return {'idle': clip(4.0, idle, 40), 'swim': clip(swim_len, swim, 24),
            'strike': clip(1.0, strike, 30, False), 'catch': clip(CATCH_LEN, lambda t: catch(t, BITE), 52, 'hold'),
            'catch_small': clip(CATCH_LEN, lambda t: catch(t, BITE_SMALL), 52, 'hold'),
            'bite_off': clip(CUT_LEN, lambda t: catch(t, BITE, True), 84, 'hold'),
            'bite_off_small': clip(CUT_LEN, lambda t: catch(t, BITE_SMALL, True), 84, 'hold'), 'flop': clip(0.8, flop, 16),
            'gulp': clip(GULP_LEN, gulp, 30, 'hold'), 'court': clip(COURT_LEN, court, 40),
            'death': clip(1.2, death, 12, 'hold')}


def write_game():
    """Writes the catfish's model, look A's texture and its clips into the savanna mod."""
    game_bones, rows, w, h = paint(model(), 'A')
    files = {f'textures/entity/{GAME_TEXTURE}.png': png(rows)}
    files['geo/entity/catfish.geo.json'] = geo('catfish', game_bones, w, h, (4, 1.5, [0, 0.5, 0]))
    files['animations/entity/catfish.animation.json'] = animations(clips())
    # the animal life cycle step 3 (2026-11-19), the looks the user confirmed in the life viewer (tools/life_looks.py):
    # a female ready to spawn, plump (her belly swelled; her own texture, the swelled boxes take other places on it),
    # and the young, tiny and near-black (look Y, the usual model's layout, so only a texture)
    import life_looks
    plump_bones, rows, pw, ph = paint(life_looks.catfish_plump(), 'A')
    files['textures/entity/catfish_plump.png'] = png(rows)
    files['geo/entity/catfish_plump.geo.json'] = geo('catfish_plump', plump_bones, pw, ph, (4, 1.5, [0, 0.5, 0]))
    LOOKS.setdefault('Y', life_looks.cm.LOOKS['Y'])      # (run as a script, this module is not the one life_looks set it in)
    _, rows, _, _ = paint(model(), 'Y')
    files['textures/entity/catfish_young.png'] = png(rows)
    # faces=False: its old faces until its in-game film is compared with its approved version (BUGS 245; step C,
    # 2026-11-20, proved the monitor, crocodile and fly first)
    write_assets(files, faces=False)


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['game']:
        write_game()
        sys.exit()
    bones = model()
    n = sum(len(b.get('cubes', [])) for b in bones)
    _, rows, w, h = paint(bones, 'A')
    print(f'{n} boxes, texture {w} x {h}')
