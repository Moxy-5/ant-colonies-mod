"""The Nile tilapia (M9 river life, the grazing fish; the user, 2026-11-14: a named species is fine for this one), its
model and three looks for the user to pick in the model viewer (python tools/model_viewer.py tilapia).

From the user's inspiration picture (2026-11-14, a blocky tilapia, "pretty rigid though") and the real fish:
- A deep, flat body seen from the side: the back rises steeply behind the head to the front of the back fin, the
  belly is round, the body narrows to a short thick tail stalk; seen from above it is thin (laterally compressed).
- Thick lips on a mouth at the very front (it scrapes algae off the bed), a big eye high on the head, a gill cover
  with a dark spot on its back edge.
- One long back fin from over the gill cover to past the tail stalk: a low spiny front (stiff spines sticking out of
  the membrane) and a taller soft back part with a pointed tip trailing over the tail; the fin under the tail (anal)
  the same, shorter; a square tail fin with neat dark stripes across it, the Nile tilapia's own mark; long pointed
  fins behind the gills (pectorals) and small ones under the chest (pelvics).
- Not rigid (the user): the body is a chain of bones (head, front, middle, rear, tail stalk, tail fin), each a stack of
  rounded slices, so the whole body bends in a wave when it swims, the tail the most; the side fins are their own
  bones, to flutter.

Looks (my picks): A everyday olive-silver with faint bars; B young fish, paler and strongly barred, the black
"tilapia spot" at the soft back fin's base; C breeding male, dark blue-grey, red-edged fins, black pelvic fins.

Units are model pixels (16 a block), the head points to -Z, up is +Y, left is +X; the lateral line is at y = MID.
Every face is painted pixel by pixel from where that pixel lies on the fish (paint()), so bars, countershading and the
tail's stripes run across the boxes as they would on the real fish.
`python tools/tilapia_models.py game` writes the game's files (model, three textures, clips) into the savanna mod.
"""
import copy
import math
import random
import struct
import zlib

TEXELS = 2.0          # texture pixels a model pixel (a 27-pixel fish is about 54 texture pixels long, as in the picture)
MID = 6.0             # the lateral line's height (the pelvic fins' tips come down near y = 0)
SNOUT, PEDUNCLE = -12.0, 9.6     # the body's length, snout to the tail fin's base
TAIL_END = 15.6

# The side profile (half-depths over and under the lateral line) and half-width, by t = 0 at the snout to 1 at the tail.
TOP = [(0, 0.8), (0.1, 2.2), (0.22, 3.6), (0.34, 4.3), (0.48, 4.4), (0.62, 3.9), (0.78, 2.9), (0.9, 2.0), (1, 1.75)]
BOT = [(0, -0.7), (0.1, -1.9), (0.22, -3.0), (0.38, -3.7), (0.52, -3.6), (0.66, -3.0), (0.8, -2.2), (0.92, -1.8), (1, -1.7)]
WIDE = [(0, 0.85), (0.1, 1.35), (0.25, 1.75), (0.45, 1.8), (0.65, 1.4), (0.82, 0.95), (1, 0.75)]

# The bones along the body: name, parent, z where it starts (its pivot), z where it ends.
CHAIN = [('head', 'body', SNOUT, -5.0), ('front', 'body', -5.0, 0.0), ('mid', 'front', 0.0, 4.0),
         ('rear', 'mid', 4.0, 7.4), ('peduncle', 'rear', 7.4, PEDUNCLE), ('tail', 'peduncle', PEDUNCLE, TAIL_END)]


def curve(points, t):
    t = max(0.0, min(1.0, t))
    for (t0, v0), (t1, v1) in zip(points, points[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0)
            f = f * f * (3 - 2 * f)
            return v0 + (v1 - v0) * f
    return points[-1][1]


def at(z):
    """(top, bottom, half-width) of the body at z (relative to the lateral line)."""
    t = (z - SNOUT) / (PEDUNCLE - SNOUT)
    return curve(TOP, t), curve(BOT, t), curve(WIDE, t)


def cube(lo, hi, part):
    return {'origin': [round(v, 3) for v in lo], 'size': [round(b - a, 3) for a, b in zip(lo, hi)], '_part': part}


def owner(z):
    for name, _, z0, z1 in CHAIN:
        if z0 <= z < z1:
            return name
    return 'tail'


def body_slices(bones):
    """The body as rounded slices: each a tall narrow box (the side profile) and a short wide one (the round flank)."""
    for name, _, z0, z1 in CHAIN[:-1]:
        n = max(1, round((z1 - z0) / (1.0 if name == 'head' else 1.25)))
        for i in range(n):
            a, b = z0 + (z1 - z0) * i / n, z0 + (z1 - z0) * (i + 1) / n
            top, bot, w = at((a + b) / 2)
            part = 'head' if name == 'head' else 'body'
            bones[name].append(cube([-w * 0.6, MID + bot, a], [w * 0.6, MID + top, b], part))
            bones[name].append(cube([-w, MID + bot * 0.62, a], [w, MID + top * 0.62, b], part))


def head_parts(bones):
    h = bones['head']
    # thick lips, a little open, the mouth's dark inside between them
    h.append(cube([-0.8, MID + 0.15, SNOUT - 0.8], [0.8, MID + 0.9, SNOUT + 0.4], 'lip'))
    h.append(cube([-0.7, MID - 0.85, SNOUT - 0.65], [0.7, MID - 0.15, SNOUT + 0.4], 'lip'))
    h.append(cube([-0.5, MID - 0.15, SNOUT - 0.45], [0.5, MID + 0.15, SNOUT + 0.2], 'mouth'))
    # the big eye, high on the head: a golden iris and the pupil standing out of it
    ez, ey = -9.3, MID + 1.35
    w = at(ez)[2]
    for s in (1, -1):
        x0, x1 = sorted([s * (w - 0.1), s * (w + 0.22)])
        h.append(cube([x0, ey - 0.85, ez - 0.85], [x1, ey + 0.85, ez + 0.85], 'iris'))
        h.append(cube([min(s * w, s * (w + 0.38)), ey - 0.45, ez - 0.4], [max(s * w, s * (w + 0.38)), ey + 0.45, ez + 0.5], 'pupil'))
    # the gill cover's back edge, a little proud of the body, with its dark spot near the top
    oz = -5.9
    top, bot, w = at(oz)
    for s in (1, -1):
        xs = sorted([s * (w * 0.92), s * (w + 0.14)])
        h.append(cube([xs[0], MID + bot * 0.8, oz], [xs[1], MID + top * 0.7, oz + 0.75], 'gill'))


def fin_columns(bones, z0, z1, base, height, part, width=1.0, drop=False):
    """A fin as thin columns 0.3 thick along z, each on the body's edge at its z (base: 'top' or 'bot'), height(z) tall."""
    z = z0
    while z < z1 - 1e-6:
        b = min(z1, z + width)
        mz = (z + b) / 2
        top, bot, _ = at(min(mz, PEDUNCLE - 0.01))
        hgt = height(mz)
        if hgt > 0.05:
            if base == 'top':
                y0 = MID + top - 0.25
                c = cube([-0.15, y0, z], [0.15, y0 + hgt, b], part)
            else:
                y1 = MID + bot + 0.25
                c = cube([-0.15, y1 - hgt, z], [0.15, y1, b], part)
            bones[owner(mz)].append(c)
        z = b


def fins(bones):
    # the back fin: a low spiny front, the membrane with stiff spines poking out of it, then the tall soft part
    fin_columns(bones, -6.6, 2.0, 'top', lambda z: 1.5 + 0.08 * (z + 6.6), 'dorsal_spiny', 1.1)
    z = -6.2
    while z < 1.8:
        top = at(z)[0]
        y0 = MID + top - 0.25
        bones[owner(z)].append(cube([-0.17, y0, z], [0.17, y0 + 2.2 + 0.08 * (z + 6.6), z + 0.32], 'spine'))
        z += 1.2
    soft = [(2.0, 2.3), (3.2, 3.0), (4.4, 3.5), (5.6, 3.7), (6.8, 3.4), (8.0, 2.7), (9.0, 1.6), (9.6, 0.0)]
    fin_columns(bones, 2.0, 9.6, 'top', lambda z: curve([((a - 2.0) / 7.6, h) for a, h in soft], (z - 2.0) / 7.6), 'dorsal', 1.0)
    # the fin under the tail: three spines, then a soft part with a trailing point
    anal = [(3.4, 1.2), (4.6, 2.0), (5.8, 2.6), (7.0, 2.8), (8.2, 2.3), (9.2, 1.2), (9.6, 0.0)]
    fin_columns(bones, 3.4, 9.6, 'bot', lambda z: curve([((a - 3.4) / 6.2, h) for a, h in anal], (z - 3.4) / 6.2), 'anal', 1.0)
    # the square tail fin, widening to a straight back edge with rounded corners
    tail = [(0, 1.75), (0.2, 2.4), (0.4, 2.95), (0.6, 3.4), (0.8, 3.7), (0.92, 3.8), (1, 3.55)]
    z = PEDUNCLE
    while z < TAIL_END - 1e-6:
        b = min(TAIL_END, z + 1.0)
        hh = curve(tail, ((z + b) / 2 - PEDUNCLE) / (TAIL_END - PEDUNCLE))
        bones['tail'].append(cube([-0.15, MID - hh, z], [0.15, MID + hh, b], 'caudal'))
        z = b


def side_fin(length, heights, part, thick=0.25):
    """A side fin in its own bone's space: from its root (0, 0, 0) back along +z, tapering."""
    cubes, n = [], len(heights)
    for i, hgt in enumerate(heights):
        a, b = length * i / n, length * (i + 1) / n
        cubes.append(cube([-thick / 2, -hgt * 0.55, a], [thick / 2, hgt * 0.45, b], part))
    return cubes


def model():
    """The fish's bones (GeckoLib style: name, parent, pivot, rotation, cubes with '_part' still to paint)."""
    bones = {name: [] for name, *_ in CHAIN}
    body_slices(bones)
    head_parts(bones)
    fins(bones)
    out = [{'name': 'body', 'pivot': [0, MID, -3.0]}]
    for name, parent, z0, _ in CHAIN:
        out.append({'name': name, 'parent': parent, 'pivot': [0, MID, z0], 'cubes': bones[name]})
    _, bot, w = at(-4.8)
    for s, side in ((1, 'left'), (-1, 'right')):
        out.append({'name': f'pectoral_{side}', 'parent': 'front', 'pivot': [s * (w - 0.1), MID - 1.2, -4.9],
                    'rotation': [-15, s * 32, s * -12], 'cubes': [dict(c, origin=[c['origin'][0] + s * (w - 0.1), c['origin'][1] + MID - 1.2,
                                                                         c['origin'][2] - 4.9]) for c in side_fin(4.8, [1.7, 1.7, 1.5, 1.2, 0.9, 0.5], 'pectoral')]})
        _, bot, w2 = at(-4.4)
        px, py, pz = s * 0.7, MID + bot + 0.2, -4.4
        out.append({'name': f'pelvic_{side}', 'parent': 'front', 'pivot': [px, py, pz], 'rotation': [-38, s * 10, 0],
                    'cubes': [dict(c, origin=[c['origin'][0] + px, c['origin'][1] + py, c['origin'][2] + pz])
                              for c in side_fin(3.4, [1.3, 1.2, 1.0, 0.7, 0.4], 'pelvic')]})
    return out


# --- colours -----------------------------------------------------------------------------------------------------------
LOOKS = {
    'A': {'name': 'Everyday', 'note': "olive-grey back fading to silver flanks and a pale belly, faint dark bars, a fine "
          "pattern of scale edges, grey fins with dark spots on the soft back fin, the striped tail fin, pinkish fin edges",
          'back': (88, 98, 78), 'flank': (150, 156, 132), 'belly': (212, 210, 194), 'bar': 0.86, 'bars_to': -1.6,
          'fin': (128, 126, 118), 'fin_edge': (178, 120, 112), 'caudal': (150, 134, 128), 'stripe': (78, 74, 68),
          'pectoral': (184, 174, 168), 'pelvic': (150, 144, 136), 'lip': (176, 160, 150), 'iris': (180, 142, 72),
          'gill': (96, 104, 86), 'spot': False, 'throat': None},
    'B': {'name': 'Young fish', 'note': "paler and silvery with strong dark bars down the sides, the black 'tilapia spot' "
          "ringed in pale at the soft back fin's base, clear grey fins, the tail's stripes fainter",
          'back': (110, 118, 96), 'flank': (176, 180, 160), 'belly': (222, 222, 210), 'bar': 0.68, 'bars_to': -2.6,
          'fin': (160, 160, 152), 'fin_edge': (170, 166, 158), 'caudal': (168, 164, 156), 'stripe': (118, 114, 106),
          'pectoral': (196, 194, 188), 'pelvic': (176, 174, 168), 'lip': (190, 182, 170), 'iris': (196, 170, 96),
          'gill': (120, 128, 108), 'spot': True, 'throat': None},
    'C': {'name': 'Breeding male', 'note': "dark blue-grey body, a reddish throat, the back and tail fins edged bright red, "
          "the tail fin red-tinged with dark stripes, black pelvic fins",
          'back': (52, 60, 72), 'flank': (78, 88, 102), 'belly': (98, 104, 114), 'bar': 0.9, 'bars_to': -1.0,
          'fin': (70, 72, 80), 'fin_edge': (206, 62, 48), 'caudal': (128, 72, 70), 'stripe': (52, 44, 48),
          'pectoral': (150, 120, 124), 'pelvic': (26, 26, 30), 'lip': (112, 104, 116), 'iris': (170, 120, 60),
          'gill': (44, 50, 60), 'spot': False, 'throat': (150, 76, 70)},
}
BARS = [-3.6, -1.2, 1.2, 3.5, 5.8, 8.2]       # the dark bars across the flank (z)


def mix(a, b, f):
    return tuple(p + (q - p) * f for p, q in zip(a, b))


def shade(c, k):
    return tuple(v * k for v in c)


def colour(part, look, x, y, z, edge, rnd):
    """The colour of the fish at this point (model pixels), edge: how near the top or outer edge of a fin (0 to 1)."""
    L = LOOKS[look]
    noise = rnd.uniform(0.95, 1.05)
    ly = y - MID
    if part in ('body', 'head', 'gill'):
        top, bot, _ = at(max(SNOUT, min(z, PEDUNCLE)))
        f = (ly - bot) / max(0.1, top - bot)                  # 0 at the belly, 1 at the back
        c = mix(L['belly'], L['flank'], min(1, f / 0.5)) if f < 0.5 else mix(L['flank'], L['back'], min(1, (f - 0.5) / 0.45))
        if L['throat'] and z < -3.5 and f < 0.42:
            c = mix(c, L['throat'], 0.75)
        if part != 'head' and ly > L['bars_to'] and any(abs(z - b) < 0.55 for b in BARS):
            c = shade(c, L['bar'])
        iz, iy = math.floor(z * TEXELS), math.floor(y * TEXELS)
        if part == 'body' and (iz + iy) % 3 == 0 and (iz - iy) % 2 == 0:
            c = shade(c, 0.9)                                 # scale edges
        if part == 'gill':
            c = (24, 24, 26) if ly > 0.8 else L['gill']       # the gill cover's dark spot
        return shade(c, noise)
    if part == 'lip':
        return shade(L['lip'], noise)
    if part == 'mouth':
        return (40, 28, 28)
    if part == 'iris':
        return shade(L['iris'], noise)
    if part == 'pupil':
        return (12, 12, 14)
    if part in ('dorsal', 'dorsal_spiny', 'spine'):
        c = L['fin']
        if part == 'spine':
            c = shade(c, 0.82)
        if part == 'dorsal' and (math.floor(z * TEXELS * 0.8) + math.floor(y * TEXELS * 0.8)) % 4 == 0:
            c = shade(c, 0.72)                                # the soft rays' dark spots
        if L['spot'] and part == 'dorsal' and 2.0 <= z <= 4.0:
            base = MID + at(z)[0]
            d = math.hypot((z - 3.0) / 1.0, (y - base - 0.9) / 0.8)
            if d < 0.75:
                c = (18, 18, 20)
            elif d < 1.05:
                c = (214, 210, 190)
        if edge > 0.82:
            c = L['fin_edge']
        return shade(c, noise)
    if part == 'anal':
        return shade(L['fin_edge'] if edge > 0.8 else L['pelvic'], noise)
    if part == 'caudal':
        c = L['caudal']
        if math.floor((z - PEDUNCLE) * TEXELS) % 3 == 1 and z > PEDUNCLE + 0.5:
            c = L['stripe']
        if edge > 0.9 or z > TAIL_END - 0.45:
            c = L['fin_edge']
        return shade(c, noise)
    if part == 'pectoral':
        return shade(L['pectoral'], noise)
    if part == 'pelvic':
        return shade(L['pelvic'], noise)
    return (255, 0, 255)


def faces(c):
    sx, sy, sz = c['size']
    return {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}


def face_point(c, face, u, v):
    """Where texture pixel (u, v) (0 to 1 across the face, v down from the top) lies on the box, in the box's own
    model space (the viewer's and the game's face layout)."""
    (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
    if face == 'east':
        return ox + sx, oy + sy * (1 - v), oz + sz * (1 - u)
    if face == 'west':
        return ox, oy + sy * (1 - v), oz + sz * u
    if face == 'up':
        return ox + sx * u, oy + sy, oz + sz * v
    if face == 'down':
        return ox + sx * u, oy, oz + sz * (1 - v)
    if face == 'south':
        return ox + sx * u, oy + sy * (1 - v), oz + sz
    return ox + sx * (1 - u), oy + sy * (1 - v), oz


def paint(bones, look):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
    rnd = random.Random(7)
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
                    edge = abs(py - MID) / max(0.1, (hi - lo) / 2)
                else:
                    edge = (py - lo) / max(0.1, hi - lo)
                rgb = colour(part, look, px, py, pz, edge, rnd)
                rows[y0 + j][x0 + i] = tuple(max(0, min(255, int(v))) for v in rgb) + (255,)
    for b in bones:
        for c in b.get('cubes', []):
            c.pop('_part')
    return bones, rows, width, height


def png(rows):
    height, width = len(rows), len(rows[0])
    raw = b''.join(b'\x00' + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')


# --- the game's files (built in the game 2026-11-14, the user's "sounds good build it in game") -----------------------
# One model, three textures: the young wear B, grown fish A, about 1 grown male in 3 the breeding colours C (my picks).
GAME_TEXTURES = {'A': 'tilapia', 'B': 'tilapia_young', 'C': 'tilapia_breeding'}
WAVE = [('head', 'body', -0.05, 0.0), ('front', 'body', 0.04, 0.6), ('mid', 'front', 0.1, 1.3),
        ('rear', 'mid', 0.19, 2.0), ('peduncle', 'rear', 0.3, 2.6), ('tail', 'peduncle', 0.42, 3.1)]


def wave_pose(t, amount, beat, curl=0):
    """Each bone's turn (degrees, the model file's rules) in the viewer's swim wave at time t: the body bones' sideways
    angles a sine, bigger and later toward the tail, each less its parent's (curl 1 or -1: one way only, a fish on its
    side arching up off the ground); the side fins flutter at twice the beat."""
    ab, out = {'body': 0.0}, {}
    for name, parent, a, lag in WAVE:
        wave = math.sin(beat * t - lag)
        ab[name] = amount * a * (wave if not curl else curl * (0.5 + 0.5 * wave))   # curl: bends one way only
        out[name] = [0.0, math.degrees(ab[name] - ab[parent]), 0.0]
    for i, side in enumerate(('left', 'right')):
        sign = -1 if i else 1
        out[f'pectoral_{side}'] = [0.0, math.degrees(sign * (0.25 + 0.75 * amount) * 0.22 * math.sin(beat * 2 * t + i * 0.5)), 0.0]
        out[f'pelvic_{side}'] = [-math.degrees(0.08 * math.sin(beat * t)), 0.0, 0.0]
    return out


def clip(length, pose_at, steps, loop=True):
    """A clip from pose_at(t) -> {bone: {'rotation': [..], 'position': [..]}} sampled at steps + 1 times."""
    bones = {}
    for k in range(steps + 1):
        t = length * k / steps
        for bone, chans in pose_at(t).items():
            for ch, v in chans.items():
                bones.setdefault(bone, {}).setdefault(ch, {})[f'{t:.4f}'.rstrip('0').rstrip('.') or '0'] = [round(x, 3) for x in v]
    out = {'animation_length': round(length, 4), 'bones': bones}
    if loop is True:
        out['loop'] = True
    elif loop == 'hold':
        out['loop'] = 'hold_on_last_frame'
    return out


def rot_only(pose):
    return {b: {'rotation': r} for b, r in pose.items()}


def clips():
    """swim (a full beat, 0.9 s; the game plays it faster or slower by the swimming speed), idle (hanging in the water,
    a slow gentle wave), graze (nose down to the bed, three pecks), flop (on land, on its side, curling), death (rolls
    onto its side and goes still)."""
    swim_len = 0.9
    CURL = -1            # which way lying on its side is up
    LIE = 4.0           # how far the body comes down lying on its side
    beat = 2 * math.pi / swim_len

    def idle(t):
        p = rot_only(wave_pose(t, 0.22, math.pi))
        p['body'] = {'rotation': [0, 0, 0], 'position': [0, 0.2 * math.sin(math.pi * t), 0]}
        return p

    def graze(t):
        peck = max(0.0, math.sin(2 * math.pi * t / 0.6))
        p = rot_only(wave_pose(t, 0.55 + 0.25 * peck, beat))
        p['body'] = {'rotation': [38 + 6 * peck, 0, 0], 'position': [0, 1.3, -0.3 * peck]}
        return p

    def folded(p, f):
        """The side fins pressed flat to the body (f 0 to 1), as a fish lying on its side holds them."""
        for s, side in ((1, 'left'), (-1, 'right')):
            p[f'pectoral_{side}'] = {'rotation': [15 * f, -s * 32 * f, s * 12 * f]}
            p[f'pelvic_{side}'] = {'rotation': [38 * f, -s * 10 * f, 0]}
        return p

    def flop(t):
        p = folded(rot_only(wave_pose(t, 1.5, 2 * math.pi / 0.6, CURL)), 1)
        p['body'] = {'rotation': [0, 0, 90], 'position': [0, -LIE + 0.8 * abs(math.sin(2 * math.pi * t / 0.6)), 0]}
        return p

    def death(t):
        f = min(1.0, t / 0.8)
        f = f * f * (3 - 2 * f)
        p = folded(rot_only(wave_pose(t, 0.9 * (1 - f), 2 * math.pi / 0.6, CURL)), min(1.0, t / 0.3))
        p['body'] = {'rotation': [0, 0, 90 * f], 'position': [0, -LIE * f, 0]}
        return p

    return {'idle': clip(2.0, idle, 24), 'swim': clip(swim_len, lambda t: rot_only(wave_pose(t, 1.0, beat)), 18),
            'graze': clip(1.8, graze, 36), 'flop': clip(0.6, flop, 12), 'death': clip(1.2, death, 12, 'hold')}


def write_game():
    """Writes the tilapia's model, its three textures and its clips into the savanna mod."""
    import json
    import mod_paths
    root = mod_paths.assets('savanna')
    bones = model()
    files = {}
    for look, name in GAME_TEXTURES.items():
        game_bones, rows, w, h = paint(bones, look)
        files[f'textures/entity/{name}.png'] = png(rows)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.tilapia', 'texture_width': w, 'texture_height': h,
                        'visible_bounds_width': 2, 'visible_bounds_height': 1.5, 'visible_bounds_offset': [0, 0.5, 0]},
        'bones': game_bones}]}
    files['geo/entity/tilapia.geo.json'] = geo
    files['animations/entity/tilapia.animation.json'] = {'format_version': '1.8.0', 'animations': clips()}
    for rel, data in files.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(data, bytes):
            path.write_bytes(data)
        else:
            with open(path, 'w', encoding='utf-8', newline='\n') as f:
                json.dump(data, f, indent=1)
        print('wrote', path.relative_to(mod_paths.PROJECT))


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['game']:
        write_game()
        sys.exit()
    bones = model()
    n = sum(len(b.get('cubes', [])) for b in bones)
    _, rows, w, h = paint(bones, 'A')
    print(f'{n} boxes, texture {w} x {h}')
