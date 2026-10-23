"""The Matabele ant (Megaponera analis), M8 step 1 (2026-10-23): the model, one shape for every ant of the colony (big
majors and small minors are the same animal at different sizes, the minors with a smaller head and jaws for their
size), and the looks for the user to pick in the model viewer (python tools/model_viewer.py matabele).

Nothing here goes into the game yet: once the user has picked a look and a size, this file writes the game's model,
texture and body plan into the savanna mod, as ant_models.py does for the harvester ant.

Redrawn 2026-10-23 from the user's two pictures (Daniel Kronauer's photo of a major and a minor, and the ant in Empires
of the Undergrowth), the user: "make them longer", "the mandibles also have to be curved", its own shape:
- Long and slender, standing high on long thin legs; the hind legs about as long as the body.
- A big glossy head about as wide as long, rounded at the back; the eyes at the front of the sides (drawn bigger than
  the real ones, under a heavy brow: the user wants them to look fierce, the harvester ants are the cute ones).
- Big thick hooked jaws (the user's third picture, 2026-10-23, an AI drawing made from a photo: only its jaws, colours
  and eyes are taken, not its boxy body): out from the front of the head, down and in to the tips, held a little open,
  dark red with a row of teeth along the inside of the hook.
- Long antennae: a long scape reaching back past the head, then a ringed whip as long again.
- A long thorax: a rounded pronotum, a lower mesonotum, a groove, a long sloping propodeum, no spines.
- One thick, tall waist node (a ponerine), the gaster a long oval pinched between its first two segments, a sting.
- Glossy black, the feet and the jaws' edges red-brown.

Nothing is taken from the harvester ant: every box is this ant's own. The bones have this ant's own names; its body
plan (tools/body_plan.py, written when it goes into the game) names which bone plays which part for the walking code,
and the motion library works its walks out from these legs, as it did for each termite. A ponerine has no
postpetiole: its gaster bends at the real pinch between the gaster's first segment (`gaster_front`) and the rest
(`gaster_back`).

Fourth round (the user's pictures, 2026-10-23): the body slimmer behind the head (thorax, waist and gaster drawn
SLIM as wide), the antennae set low on the sides of the head, under the eyes, and reaching out sideways and forward.

Fifth round (the user, 2026-10-23): the real head hangs slanted down from the neck, the majors' the most ("looking
down from the weight of their mandibles and head"), between the user's two photos in steepness; three ways to build it
side by side (HEADS): tilted as one piece like a horse's head, stepped square to the grid, and tilted with every detail
the size allows. No painted shine for now (the user: that is light bouncing off them).

Sixth round (the user's picture of a Minecraft-style head, 2026-10-23, "make it like this"): the head a rounded block
with a narrower muzzle under the face, big dark eyes with a pale rim, antennae from the face with a red band, sickle
jaws with a red inner edge, still tilted at the neck; and every box face painted with pixels of one size (paint()), as
on a Minecraft mob, in place of one small swatch stretched over each box. Two colours: black (the user's pick) and the
picture's brown-grey.

Units are model pixels before SCALE, as in ant_models.py: the head points to -Z, the ground is y = 0, left is +X.
"""
import copy
import math
import random
import struct
import zlib

SCALE = 0.85
LIFT = 1.5   # the whole ant is drawn this much higher than its boxes say, the feet brought down to the ground: it stands tall

# --- texture: every box face gets its own patch, all at TEXELS pixels a model pixel (the user's picture, 2026-10-23:
# the same pixel size on the big head and the thin jaws, as on a Minecraft mob), packed into one texture by paint().
# Each box names its swatch: what part of the ant it is, its colour from the look and its pattern.
SWATCHES = {
    'head': 'plate', 'thorax': 'plate', 'waist': 'plate', 'gaster': 'haired', 'rim': 'plate',
    'leg': 'plain', 'antenna': 'plain', 'ring': 'plain', 'band': 'plain', 'tarsus': 'plain', 'joint': 'plain',
    'eye': 'eye', 'eyerim': 'plain', 'jaw': 'plain', 'teeth': 'plain', 'sting': 'plain',
}
TEXELS = 3.0

# The looks: A glossy black (the user's pick, the photo), B the brown-grey of the user's picture of the head
# (2026-10-23). grain: how far a pixel strays from its colour; flecks: how many pixels are lighter (or darker) specks;
# hair: how much the gaster's fine pale hairs grey it.
LOOKS = {
    'A': {'name': 'Black',
          'note': "glossy black, the look you picked (the photo): the eyes dark with a grey rim, the jaws' inner edges, "
                  "the feet and the antenna bands red-brown",
          'grain': 7, 'flecks': 0.10, 'hair': 0.18,
          'colours': {'head': (26, 25, 27), 'thorax': (28, 27, 29), 'waist': (28, 27, 29), 'gaster': (31, 30, 31),
                      'rim': (52, 50, 54), 'leg': (36, 30, 28), 'antenna': (38, 31, 28), 'ring': (46, 36, 30),
                      'band': (120, 40, 26), 'tarsus': (78, 44, 30), 'joint': (22, 20, 21), 'eye': (14, 14, 16),
                      'eyerim': (92, 92, 98), 'jaw': (36, 26, 23), 'teeth': (130, 40, 26), 'sting': (90, 52, 34)}},
    'B': {'name': 'Like the picture',
          'note': "the brown-grey of your picture of the head: pale specks, the eyes dark grey with a light rim, the "
                  "jaws' inner edges and the antenna bands red",
          'grain': 9, 'flecks': 0.14, 'hair': 0.12,
          'colours': {'head': (86, 74, 66), 'thorax': (84, 72, 64), 'waist': (84, 72, 64), 'gaster': (82, 70, 63),
                      'rim': (100, 88, 79), 'leg': (80, 68, 60), 'antenna': (82, 70, 62), 'ring': (82, 70, 62),
                      'band': (166, 40, 26), 'tarsus': (92, 76, 64), 'joint': (60, 52, 47), 'eye': (50, 52, 56),
                      'eyerim': (150, 150, 155), 'jaw': (84, 70, 62), 'teeth': (170, 42, 28), 'sting': (104, 84, 68)}},
}


def faces(c):
    """A box's six faces and their sizes in model pixels."""
    sx, sy, sz = c['size']
    return {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}


def paint(bones, look):
    """The bones with every face's place on the texture, and the texture: (bones, rows of (r, g, b, a), width, height).
    Each face gets its own patch at TEXELS pixels a model pixel, packed in shelves, the tallest first."""
    spec = LOOKS[look]
    rnd = random.Random(23)
    bones = copy.deepcopy(bones)
    patches = []
    for b in bones:
        for c in b.get('cubes', []):
            swatch = c.pop('_swatch')
            c['uv'] = {}
            for face, (w, h) in faces(c).items():
                patches.append((max(1, round(w / SCALE * TEXELS)), max(1, round(h / SCALE * TEXELS)), c, face, swatch))
    patches.sort(key=lambda p: (-p[1], -p[0]))
    for width in (64, 128, 256, 512, 1024):
        x = y = shelf = 0
        places = []
        for w, h, c, face, swatch in patches:
            if x + w > width:
                x, y, shelf = 0, y + shelf, 0
            places.append((x, y, w, h, c, face, swatch))
            x, shelf = x + w, max(shelf, h)
        if y + shelf <= width:
            break
    height = 16
    while height < y + shelf:
        height *= 2
    rows = [[(0, 0, 0, 0)] * width for _ in range(height)]
    for x0, y0, w, h, c, face, swatch in places:
        c['uv'][face] = {'uv': [x0, y0], 'uv_size': [w, h]}
        base, pattern = spec['colours'][swatch], SWATCHES[swatch]
        for y in range(h):
            for x in range(w):
                d = rnd.uniform(-1, 1) * spec['grain']
                roll = rnd.random()
                if roll < spec['flecks']:
                    d += spec['grain'] * 2.2                          # a pale speck
                elif roll < spec['flecks'] * 1.6:
                    d -= spec['grain'] * 1.6                          # a dark one
                rgb = [v + d for v in base]
                if pattern == 'haired' and rnd.random() < 0.3:
                    mix = spec['hair'] * rnd.uniform(0.3, 0.7)       # a fine pale hair
                    rgb = [v + (150 - v) * mix for v in rgb]
                if pattern == 'eye' and w > 3 and h > 3 and (x, y) in ((1, 1), (2, 1), (1, 2)):
                    rgb = [v + 60 for v in rgb]                       # a hard glint high up
                rows[y0 + y][x0 + x] = tuple(max(0, min(255, int(v))) for v in rgb) + (255,)
    return bones, rows, width, height


def png(rows):
    height, width = len(rows), len(rows[0])
    raw = b''.join(b'\x00' + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')


# --- boxes (ant_models.py's helpers, with this ant's swatches) ------------------------------------------------------
def r3(values):
    return [round(v * SCALE, 3) for v in values]


def lifted(p):
    return [p[0], p[1] + LIFT, p[2]]


def box(lo, hi, swatch, pivot=None, rotation=None):
    """A box from corner lo to corner hi (design units), optionally turned by rotation about pivot."""
    c = {'origin': r3(lifted(lo)), 'size': r3([b - a for a, b in zip(lo, hi)]), '_swatch': swatch}   # painted by paint()
    if rotation and any(abs(v) > 1e-6 for v in rotation):
        c['pivot'] = r3(lifted(pivot))
        c['rotation'] = [round(v, 2) for v in rotation]
    return c


def centered(cx, cy, cz, sx, sy, sz, swatch):
    return box([cx - sx / 2, cy - sy / 2, cz - sz / 2], [cx + sx / 2, cy + sy / 2, cz + sz / 2], swatch)


def rod(a, b, thick, swatch, height=None):
    """A box from point a to point b, thick x thick: a leg or antenna segment (the turn rule of ant_models.rod)."""
    d = [q - p for p, q in zip(a, b)]
    length = math.sqrt(sum(v * v for v in d))
    dx, dy, dz = (v / length for v in d)
    ry = math.degrees(math.asin(max(-1.0, min(1.0, -dz))))
    rz = math.degrees(math.atan2(-dy, dx))
    h = thick if height is None else height
    return box([a[0], a[1] - h / 2, a[2] - thick / 2], [a[0] + length, a[1] + h / 2, a[2] + thick / 2], swatch, a, [0, ry, rz])


def bone(name, parent, pivot, cubes=()):
    b = {'name': name, 'pivot': r3(lifted(pivot))}
    if parent:
        b['parent'] = parent
    if cubes:
        b['cubes'] = list(cubes)
    return b


def mirror(p):
    return (-p[0], p[1], p[2])


def lerp(a, b, f):
    return tuple(p + (q - p) * f for p, q in zip(a, b))


def along(points, n):
    """n + 1 points spaced evenly along the broken line through points."""
    lengths = [math.dist(a, b) for a, b in zip(points, points[1:])]
    total, out = sum(lengths), []
    for i in range(n + 1):
        d, k = total * i / n, 0
        while k < len(lengths) - 1 and d > lengths[k]:
            d -= lengths[k]
            k += 1
        out.append(lerp(points[k], points[k + 1], min(1.0, d / lengths[k])))
    return out


# --- the ant --------------------------------------------------------------------------------------------------------
NECK = (0, 5.5, -7.2)           # high on the front of the pronotum: the head hangs from it, tilted down
WAIST = (0, 4.2, 0.4)            # thorax to the waist node
GIRDLE_FRONT = (0, 4.4, 2.0)     # the waist node to the gaster's first segment
GIRDLE = (0, 4.5, 4.8)           # the pinch between the gaster's first and second segments

# Legs: hip, knee, ankle, toe (left side; before LIFT, so the toes at -LIFT stand on the ground). Long and thin, the knees high over the back, the body carried high; the hind
# legs the longest, about as long as the body.
LEGS = {
    1: [(0.7, 3.6, -6.0), (3.8, 6.9, -8.4), (6.0, 1.3, -11.2), (7.0, -LIFT, -13.4)],
    2: [(0.8, 3.5, -3.4), (5.0, 7.3, -3.6), (8.2, 1.3, -3.2), (9.6, -LIFT, -2.6)],
    3: [(0.78, 3.45, -1.2), (4.6, 7.5, 1.3), (7.6, 1.3, 6.0), (8.8, -LIFT, 8.6)],
}
# --- the head (the user's picture of the head, 2026-10-23, "make it like this") ------------------------------------
# A rounded block, the narrower muzzle (clypeus) jutting out under the face, the big dark eyes high on the sides toward
# the front with a pale rim, the antennae from the face beside the muzzle with a red band past the elbow, the jaws thick
# sickles from under the muzzle, down and in, a red edge along the inside. The real head hangs slanted down from the
# neck, the majors' the most (the user: "looking down from the weight of their mandibles and head"): the head is drawn
# level in its own frame (from the neck, the face toward -z), and its bone is tilted nose-down at the neck, the way a
# horse's head is built in Minecraft.
MAJOR_TILT = 31.0       # degrees: between the user's two photos, the majors the most (my picks, to confirm)
MINOR_TILT = 21.0
# The head is drawn at this size: the major's big head stands out from its narrow thorax as in the photo; the minor's
# head and jaws are smaller for its body, its antennae less so (my picks, from the photo).
MAJOR_HEAD = 1.15
MINOR_HEAD = 0.92
MINOR_ANTENNA = 0.92

# The head's boxes in its own frame, (lo, hi, swatch); the side boxes for the left side, mirrored for the right.
HEAD_BOXES = [
    ([-2.3, -1.1, -4.6], [2.3, 1.7, -0.8], 'head'),        # the head, a rounded block: its full width,
    ([-1.9, -1.5, -4.6], [1.9, 2.1, -0.8], 'head'),        # its full height,
    ([-1.9, -1.1, -5.0], [1.9, 1.7, -0.4], 'head'),        # its full length
    ([-1.5, 2.1, -4.2], [1.5, 2.4, -1.2], 'head'),         # the domed crown
    ([-2.55, -0.7, -4.2], [2.55, 1.3, -1.2], 'head'),      # the full sides
    ([-1.5, -1.8, -4.2], [1.5, -1.5, -1.2], 'head'),       # the rounded underside
    ([-1.5, -0.7, -0.4], [1.5, 1.3, -0.1], 'head'),        # the rounded back
    ([-1.3, -1.45, -5.65], [1.3, -0.15, -4.85], 'head'),   # the muzzle under the face, narrower, jutting out
    ([-1.0, -0.15, -5.4], [1.0, 0.2, -4.9], 'head'),       # its sloping top
    ([-0.9, -1.75, -5.5], [0.9, -1.45, -5.0], 'jaw'),      # the lip under it
    ([-0.8, -0.5, -0.1], [0.8, 0.5, 0.4], 'thorax'),       # the neck
]
HEAD_SIDE_BOXES = [
    ([1.0, -0.25, -5.25], [1.75, 0.5, -4.85], 'head'),     # the antenna socket on the face, beside the muzzle
    ([2.25, 0.35, -4.45], [2.6, 1.75, -2.55], 'eyerim'),   # the pale rim round the eye
    ([2.3, 0.5, -4.3], [2.68, 1.6, -2.7], 'eye'),          # the big dark eye, high on the side toward the front
    ([1.4, 1.7, -4.4], [2.2, 2.0, -2.6], 'head'),          # the brow over it
]

# The jaw in the head's frame: a thick sickle from under the side of the muzzle, forward and a little out, then
# curving in to the tip in front of the face, the two tips nearly meeting (left side; the user's picture and the real
# ant: the curve is sideways, not down); with the head tilted it points forward and down, the major's tips off the
# ground. Its width and height taper from the base to the tip.
JAW = [(1.15, -1.0, -5.1), (1.55, -0.8, -6.0), (1.7, -0.7, -6.9), (1.5, -0.65, -7.7), (1.0, -0.7, -8.3),
       (0.4, -0.8, -8.6)]
JAW_BASE, JAW_TIP = (0.95, 0.85), (0.4, 0.4)      # (width, height)
CARRY = (0, -1.0, -8.0)         # between the jaw tips, in the head's frame: where the stack of termites is held

# The antennae: from the socket in the head's frame; the scape and whip are offsets from the socket on the standing
# ant, so they reach out however far the head is tilted: the scape out sideways and a little up, the whip out and
# forward, drooping at the tip, a red band past the elbow (the user's picture).
ANTENNA_SOCKET = (1.4, 0.12, -5.15)
ANTENNA_ELBOW = (2.7, 1.0, -0.7)
ANTENNA_WHIP = [(3.7, 1.15, -2.3), (4.3, 0.7, -4.0), (4.6, -0.2, -5.4), (4.7, -1.2, -6.4)]
WHIP_RINGS = 10
BAND = (1, 2)                   # the whip's rings drawn red


class HeadFrame:
    """Where the head's own-frame points go: the head bone is turned nose-down at the neck, so its boxes are written
    level in the model file and turn with it."""

    def __init__(self, size, tilt):
        self.size = size
        t = math.radians(tilt)
        self.c, self.s = math.cos(t), math.sin(t)

    def turn(self, v):
        x, y, z = v
        return (x, y * self.c + z * self.s, z * self.c - y * self.s)

    def unturn(self, v):
        x, y, z = v
        return (x, y * self.c - z * self.s, z * self.c + y * self.s)

    def world(self, u):
        """Where an own-frame point ends up on the standing ant."""
        return tuple(n + v for n, v in zip(NECK, self.turn([c * self.size for c in u])))

    def pt(self, u):
        """An own-frame point as written in the model file."""
        return tuple(n + c * self.size for n, c in zip(NECK, u))

    def written(self, w):
        """A point on the standing ant as written in the model file."""
        return tuple(n + v for n, v in zip(NECK, self.unturn([a - b for a, b in zip(w, NECK)])))

    def box(self, lo, hi, swatch):
        return box(self.pt(lo), self.pt(hi), swatch)


def smooth(points):
    """One round of corner cutting: the jaw's bends rounded off."""
    out = [points[0]]
    for a, b in zip(points, points[1:]):
        out += [lerp(a, b, 0.25), lerp(a, b, 0.75)]
    return out + [points[-1]]


def head_bones(size=1.0, antenna=1.0, tilt=0.0):
    f = HeadFrame(size, tilt)
    cubes = [f.box(lo, hi, swatch) for lo, hi, swatch in HEAD_BOXES]
    for sx in (1, -1):
        cubes += [f.box(*sym_points(lo, hi, sx), swatch) for lo, hi, swatch in HEAD_SIDE_BOXES]
    head = bone('head', 'forebody', NECK, cubes)
    if tilt:
        head['rotation'] = [tilt, 0, 0]
    bones = [head]
    for side, sx in (('l', 1), ('r', -1)):
        jaw = [f.pt((p[0] * sx, p[1], p[2])) for p in smooth(JAW)]
        cubes, n = [], len(jaw) - 1
        for i, (a, b) in enumerate(zip(jaw, jaw[1:])):
            t = i / (n - 1)
            w, h = ((p + (q - p) * t) * size for p, q in zip(JAW_BASE, JAW_TIP))
            cubes.append(rod(a, b, w, 'jaw', h))
            # The red inner edge, along the inside of the hook; a tooth on every other piece.
            inward = (-sx * w / 2, 0, 0)
            cubes.append(rod(tuple(p + o for p, o in zip(a, inward)), tuple(p + o for p, o in zip(b, inward)),
                             0.3 * size, 'teeth', h * 0.7))
            if 0 < i < n - 1 and i % 2:
                c = lerp(a, b, 0.5)
                cubes.append(centered(c[0] - sx * (w / 2 + 0.15 * size), c[1], c[2], 0.4 * size, 0.45 * size,
                                      0.4 * size, 'teeth'))
        bones.append(bone(f'mandible_{side}', 'head', jaw[0], cubes))
        socket_at = f.world((ANTENNA_SOCKET[0] * sx, ANTENNA_SOCKET[1], ANTENNA_SOCKET[2]))
        k = size * antenna

        def a(off):
            """An antenna point: an offset from the socket on the standing ant, as written in the model file."""
            return f.written(tuple(s + o * k for s, o in zip(socket_at, (off[0] * sx, off[1], off[2]))))
        socket, elbow = f.written(socket_at), a(ANTENNA_ELBOW)
        bones.append(bone(f'antenna_{side}_1', 'head', socket, [
            rod(socket, lerp(socket, elbow, 0.55), 0.42 * size, 'antenna'),      # the scape, thicker to its end
            rod(lerp(socket, elbow, 0.5), elbow, 0.5 * size, 'antenna'),
            centered(*elbow, 0.5 * size, 0.5 * size, 0.5 * size, 'joint')]))
        rings = along([elbow] + [a(p) for p in ANTENNA_WHIP], WHIP_RINGS)
        whip = [rod(p, q, (0.4 - 0.01 * i) * size, 'band' if i in BAND else 'ring' if i % 2 else 'antenna')
                for i, (p, q) in enumerate(zip(rings, rings[1:]))]
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', elbow, whip))
    bones.append(bone('carry', 'head', f.pt(CARRY)))
    return bones


def sym_points(lo, hi, sx):
    xs = sorted((lo[0] * sx, hi[0] * sx))
    return [xs[0], lo[1], lo[2]], [xs[1], hi[1], hi[2]]


def thorax_bone():
    return bone('thorax', 'forebody', (0, 4.5, -3.4), [
        box([-1.5, 3.6, -7.2], [1.5, 6.0, -4.4], 'thorax'),       # the pronotum, round and high
        box([-1.1, 6.0, -6.8], [1.1, 6.35, -4.8], 'thorax'),      # its domed top
        box([-1.15, 3.7, -4.5], [1.15, 5.6, -2.4], 'thorax'),     # the mesonotum, lower
        box([-0.85, 5.6, -4.2], [0.85, 5.85, -2.7], 'thorax'),
        box([-0.85, 3.8, -2.45], [0.85, 4.9, -1.9], 'thorax'),      # the groove before the propodeum
        box([-1.1, 3.4, -2.0], [1.1, 5.3, 0.4], 'thorax'),        # the long propodeum
        box([-0.85, 5.3, -1.8], [0.85, 5.5, -0.4], 'thorax'),       # its sloping top
        box([-1.1, 3.2, -6.5], [1.1, 3.6, -0.5], 'thorax'),       # the underside
    ])


def leg_bones():
    bones = []
    for n, (hip, knee, ankle, toe) in LEGS.items():
        for side, sx in (('l', 1), ('r', -1)):
            def m(p):
                return p if sx > 0 else mirror(p)
            h, k, a, t = m(hip), m(knee), m(ankle), m(toe)
            bones.append(bone(f'leg_{side}{n}', 'forebody', h, [
                centered(h[0], h[1], h[2], 1.0, 1.0, 1.0, 'leg'),     # the coxa
                rod(h, k, 0.62, 'leg')]))                             # the femur, long and thin
            bones.append(bone(f'leg_{side}{n}_lower', f'leg_{side}{n}', k, [
                centered(k[0], k[1], k[2], 0.58, 0.58, 0.58, 'joint'),
                rod(k, a, 0.48, 'leg')]))                             # the tibia
            steps = along([a, t], 3)                                  # the tarsus in three segments
            bones.append(bone(f'leg_{side}{n}_foot', f'leg_{side}{n}_lower', a,
                              [centered(*a, 0.44, 0.44, 0.44, 'joint')] +
                              [rod(p, q, 0.36 - 0.03 * i, 'tarsus') for i, (p, q) in enumerate(zip(steps, steps[1:]))]))
    return bones


SLIM = 0.72
SLIM_BONES = ('thorax', 'petiole', 'gaster_front', 'gaster_back')


def model(minor=False):
    """The ant's bones (the game's model file format, each box naming its swatch until paint() gives it its place on
    the texture). minor: the small ant's proportions."""
    bones = [bone('body', None, (0, 4.5, 0)), bone('forebody', 'body', WAIST)]
    bones += head_bones(MINOR_HEAD if minor else MAJOR_HEAD, MINOR_ANTENNA if minor else 1.0,
                        MINOR_TILT if minor else MAJOR_TILT)
    bones.append(thorax_bone())
    bones += leg_bones()
    bones.append(bone('petiole', 'body', WAIST, [
        box([-0.5, 3.8, 0.3], [0.5, 4.5, 0.8], 'waist'),          # the short stalk
        box([-1.1, 3.5, 0.7], [1.1, 6.0, 1.9], 'waist'),          # the thick, tall node
        box([-0.85, 6.0, 0.85], [0.85, 6.25, 1.75], 'waist'),     # its rounded top
        box([-0.3, 3.15, 0.9], [0.3, 3.5, 1.6], 'waist'),         # the keel under it
    ]))
    bones.append(bone('gaster_front', 'petiole', GIRDLE_FRONT, [  # the gaster's first segment
        box([-1.3, 3.4, 1.9], [1.3, 5.6, 2.2], 'gaster'),
        box([-1.75, 3.15, 2.1], [1.75, 5.85, 4.6], 'gaster'),
        box([-1.45, 2.95, 2.4], [1.45, 6.05, 4.3], 'gaster'),
        box([-1.5, 3.4, 4.55], [1.5, 5.6, 5.0], 'gaster'),        # the girdle, the pinch
    ]))
    bones.append(bone('gaster_back', 'gaster_front', GIRDLE, gaster_cubes()))
    for b in bones:
        if b['name'] in SLIM_BONES:                                 # the slim body behind the head
            for c in b.get('cubes', []):
                c['origin'][0] = round(c['origin'][0] * SLIM, 3)
                c['size'][0] = round(c['size'][0] * SLIM, 3)
    return bones


# The gaster from its pinch to the sting, curving down (the user, 2026-10-23: "slightly curved and more intimidating";
# real ponerines carry the gaster's tip turned down and under, the sting ready): each plate bent further down than the
# one before, a shining rim on the back edge of each (the armoured bands of the real plates), the sting long and sharp.
# (length, width, height, degrees down): the second segment on to the tip.
GASTER_PLATES = [(4.4, 4.7, 3.9, 6.0), (1.8, 3.8, 3.2, 22.0), (1.3, 2.7, 2.3, 42.0), (0.9, 1.5, 1.3, 62.0)]
STING = [(1.1, 0.55, 75.0), (1.1, 0.26, 86.0)]    # (length, thickness, degrees down): its base, then the point


def tilted(centre, size, down, swatch):
    """A box of size round centre, its back end turned down by down degrees (the model file's X turn: negative
    lowers the back, as the head's positive turn lowers the front)."""
    return box([c - v / 2 for c, v in zip(centre, size)], [c + v / 2 for c, v in zip(centre, size)], swatch, centre,
               [-down, 0, 0])


def gaster_cubes():
    cubes, p = [], [GIRDLE[0], GIRDLE[1], GIRDLE[2] + 0.1]
    for i, (length, w, h, down) in enumerate(GASTER_PLATES):
        d = (0, -math.sin(math.radians(down)), math.cos(math.radians(down)))
        c = [q + v * length / 2 for q, v in zip(p, d)]
        cubes.append(tilted(c, (w, h, length), down, 'gaster'))
        if i == 0:                                                   # the big segment rounded
            cubes.append(tilted(c, (w + 0.3, h - 0.8, length - 0.8), down, 'gaster'))
            cubes.append(tilted(c, (w - 1.2, h + 0.5, length - 0.8), down, 'gaster'))
        if i < len(GASTER_PLATES) - 1:
            rim = [q + v * (length / 2 - 0.15) for q, v in zip(c, d)]
            cubes.append(tilted(rim, (w + 0.2, h + 0.2, 0.3), down, 'rim'))
        p = [q + v * (length - 0.1) for q, v in zip(p, d)]
    for length, thick, down in STING:
        d = (0, -math.sin(math.radians(down)), math.cos(math.radians(down)))
        c = [q + v * length / 2 for q, v in zip(p, d)]
        cubes.append(tilted(c, (thick, thick, length), down, 'sting'))
        p = [q + v * (length - 0.05) for q, v in zip(p, d)]
    return cubes
