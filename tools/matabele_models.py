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

Bones: the harvester worker's names (tools/body_plan.py), only as labels: every box is this ant's own, and the motion
library will work its walks out from these legs, as it did for each termite. A ponerine has no postpetiole: here the
bone of that name holds the gaster's first segment, so the gaster bends at its real pinch.

Units are model pixels before SCALE, as in ant_models.py: the head points to -Z, the ground is y = 0, left is +X.
"""
import math
import random
import struct
import zlib

SCALE = 0.85
LIFT = 1.5   # the whole ant is drawn this much higher than its boxes say, the feet brought down to the ground: it stands tall

# --- texture: 64x64, 8x8 swatches (column, row, pattern); colours come from the look ------------------------------
CELLS = {
    'head':    (0, 0, 'glossy'),
    'thorax':  (1, 0, 'glossy'),
    'waist':   (2, 0, 'glossy'),
    'gaster':  (3, 0, 'haired'),
    'leg':     (4, 0, 'noise'),
    'antenna': (5, 0, 'noise'),    # the scape and every other ring of the whip
    'ring':    (6, 0, 'noise'),    # the whip's other rings
    'eye':     (7, 0, 'eye'),
    'jaw':     (0, 1, 'noise'),
    'teeth':   (1, 1, 'noise'),    # the jaws' inner toothed edge
    'tarsus':  (2, 1, 'noise'),
    'joint':   (3, 1, 'noise'),
    'sting':   (4, 1, 'noise'),
}

# The looks. shine: the light on the hard plates; hair: how much the gaster's fine hairs grey it.
LOOKS = {
    'C': {'name': 'Like your third picture',
          'note': 'dark charcoal plates patched with grey and warm brown, dark brown legs and antennae with tan tips and '
                  'feet, dark red jaws, big dark eyes with a hard glint',
          'shine': 0.35, 'hair': 0.05, 'mottle': [((18, 18, 20), 0.25), ((34, 14, 2), 0.25)],
          'colours': {'head': (50, 46, 46), 'thorax': (52, 47, 46), 'waist': (52, 47, 46), 'gaster': (52, 47, 46),
                      'leg': (62, 44, 32), 'antenna': (66, 46, 34), 'ring': (56, 40, 30), 'eye': (58, 60, 68),
                      'jaw': (98, 30, 26), 'teeth': (80, 24, 22), 'tarsus': (150, 100, 60), 'joint': (44, 34, 28),
                      'sting': (120, 84, 52)}},
    'A': {'name': 'Like the photo',
          'note': 'glossy black (the real ant, Daniel Kronauer\'s photo): the head, thorax and gaster shining, fine pale '
                  'hairs on the gaster, the jaws\' toothed edges, the feet and the antenna tips red-brown',
          'shine': 0.9, 'hair': 0.18,
          'colours': {'head': (24, 23, 25), 'thorax': (26, 25, 27), 'waist': (26, 25, 27), 'gaster': (30, 29, 30),
                      'leg': (34, 28, 26), 'antenna': (36, 29, 26), 'ring': (52, 38, 30), 'eye': (12, 12, 14),
                      'jaw': (34, 24, 21), 'teeth': (112, 40, 26), 'tarsus': (78, 44, 30), 'joint': (22, 20, 21),
                      'sting': (90, 52, 34)}},
    'B': {'name': 'Like Empires of the Undergrowth',
          'note': 'the game\'s ant: a blue-grey sheen on black, the antennae\'s whips and the feet ringed in pale grey, '
                  'the jaws dark grey',
          'shine': 1.3, 'hair': 0.1,
          'colours': {'head': (34, 36, 44), 'thorax': (36, 38, 46), 'waist': (36, 38, 46), 'gaster': (38, 40, 48),
                      'leg': (40, 42, 50), 'antenna': (44, 46, 54), 'ring': (150, 152, 160), 'eye': (14, 14, 18),
                      'jaw': (52, 54, 62), 'teeth': (78, 78, 86), 'tarsus': (130, 132, 140), 'joint': (28, 30, 36),
                      'sting': (90, 92, 100)}},
}


def texture_rows(look):
    """The look's 64x64 texture as rows of (r, g, b, a)."""
    spec = LOOKS[look]
    rnd = random.Random(23)
    rows = [[(0, 0, 0, 0)] * 64 for _ in range(64)]
    for name, (col, row, pattern) in CELLS.items():
        base = spec['colours'][name]
        for y in range(8):
            for x in range(8):
                k = rnd.uniform(-0.06, 0.06)
                rgb = list(base)
                if pattern in ('glossy', 'haired'):
                    if (x, y) == (2, 2):
                        k += spec['shine'] * 1.1                    # the light on a hard plate
                    elif (x, y) in ((3, 2), (2, 3)):
                        k += spec['shine'] * 0.45
                if pattern in ('glossy', 'haired') and spec.get('mottle'):
                    roll = rnd.random()                             # patches of other tones on the plates
                    for tone, chance in spec['mottle']:
                        if roll < chance:
                            rgb = [c + t for c, t in zip(rgb, tone)]
                            break
                        roll -= chance
                if pattern == 'haired' and rnd.random() < 0.5:
                    mix = spec['hair'] * rnd.uniform(0.25, 0.55)    # a fine pale hair
                    rgb = [c + (150 - c) * mix for c in rgb]
                elif pattern == 'eye':
                    k = rnd.uniform(-0.05, 0.05) + (4.0 if (x, y) in ((1, 1), (2, 1)) else 0.0)   # a hard glint high up
                rows[row * 8 + y][col * 8 + x] = tuple(max(0, min(255, int(c * (1 + k)))) for c in rgb) + (255,)
    return rows


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
    col, row, _ = CELLS[swatch]
    face = {'uv': [col * 8 + 1, row * 8 + 1], 'uv_size': [6, 6]}
    c = {'origin': r3(lifted(lo)), 'size': r3([b - a for a, b in zip(lo, hi)]),
         'uv': {f: dict(face) for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
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
NECK = (0, 4.5, -7.2)
WAIST = (0, 4.2, 0.4)            # thorax to the waist node
GIRDLE_FRONT = (0, 4.4, 2.0)     # the waist node to the gaster's first segment
GIRDLE = (0, 4.5, 4.8)           # the pinch between the gaster's first and second segments
MANDIBLE_TIPS_Z = -15.9
CARRY = (0, 2.5, -16.1)          # between the jaw tips: where the stack of termites is held

# Legs: hip, knee, ankle, toe (left side; before LIFT, so the toes at -LIFT stand on the ground). Long and thin, the knees high over the back, the body carried high; the hind
# legs the longest, about as long as the body.
LEGS = {
    1: [(0.95, 3.6, -6.0), (3.8, 6.9, -8.4), (6.0, 1.3, -11.2), (7.0, -LIFT, -13.4)],
    2: [(1.1, 3.5, -3.4), (5.0, 7.3, -3.6), (8.2, 1.3, -3.2), (9.6, -LIFT, -2.6)],
    3: [(1.05, 3.45, -1.2), (4.6, 7.5, 1.3), (7.6, 1.3, 6.0), (8.8, -LIFT, 8.6)],
}
ANTENNA_SOCKET = (0.75, 5.6, -11.8)
ANTENNA_ELBOW = (2.6, 7.7, -8.5)                                     # the long scape reaches back past the head
ANTENNA_WHIP = [(3.6, 8.3, -11.0), (4.5, 8.1, -13.5), (5.2, 7.3, -15.7), (5.6, 6.0, -17.5)]
WHIP_RINGS = 10
# The jaw: a thick hook from its base under the front of the head, forward and out, then down and in to the tip, held a
# little open (the tips about 1.8 apart); thickest at the base (left side).
JAW = [(1.35, 3.9, -12.2), (1.9, 3.5, -13.4), (2.0, 2.9, -14.6), (1.6, 2.3, -15.5), (0.9, 1.9, -15.9)]
JAW_WIDTH = [1.3, 1.15, 0.95, 0.7]
JAW_HEIGHT = [1.0, 0.9, 0.8, 0.6]

# The head is drawn at HEAD of its boxes' size: the major's big head stands out from its narrow thorax as in the
# photo; the minor's head and jaws are smaller for its body, its antennae less so (my picks, from the photo).
MAJOR_HEAD = 1.15
MINOR_HEAD = 0.92
MINOR_ANTENNA = 0.92


def head_bones(head=1.0, antenna=1.0):
    def h(p):
        """A head point, the head scaled about the neck."""
        return tuple(n + (v - n) * head for v, n in zip(p, NECK))

    def hb(lo, hi, swatch):
        return box(h(lo), h(hi), swatch)

    cubes = [
        hb([-2.0, 3.3, -12.2], [2.0, 6.3, -7.8], 'head'),            # the head capsule
        hb([-2.3, 3.7, -11.6], [2.3, 5.9, -8.3], 'head'),            # its full rounded sides
        hb([-1.6, 6.3, -11.6], [1.6, 6.65, -8.4], 'head'),           # the domed crown
        hb([-1.6, 3.0, -11.6], [1.6, 3.3, -8.4], 'head'),            # the underside
        hb([-1.5, 3.7, -7.8], [1.5, 5.9, -7.45], 'head'),            # the rounded back
        hb([-1.3, 3.6, -12.6], [1.3, 4.7, -12.2], 'head'),           # the clypeus over the jaws
        hb([-0.8, 4.0, -7.6], [0.8, 5.0, -6.9], 'thorax'),           # the neck
    ]
    for sx in (1, -1):
        cubes += [
            box(*[h(p) for p in sym_points([0.25, 5.9, -12.15], [1.0, 6.35, -11.2], sx)], 'head'),   # frontal lobe
            box(*[h(p) for p in sym_points([2.2, 4.6, -11.5], [2.58, 5.85, -10.0], sx)], 'eye'),    # big, bulging
            box(*[h(p) for p in sym_points([1.3, 5.85, -11.8], [2.45, 6.3, -9.9], sx)], 'head'),    # the heavy brow
        ]
    bones = [bone('head', 'forebody', NECK, cubes)]
    for side, sx in (('l', 1), ('r', -1)):
        def m(p):
            return p if sx > 0 else mirror(p)
        jaw = [h(m(p)) for p in JAW]
        cubes = []
        for i, (a, b) in enumerate(zip(jaw, jaw[1:])):
            w = JAW_WIDTH[i] * head
            cubes.append(rod(a, b, w, 'jaw', JAW_HEIGHT[i] * head))
            if i == 0:
                continue
            # Two teeth on the inside of the hook: from the jaw's middle line toward the other jaw.
            for f in (0.3, 0.75):
                c = lerp(a, b, f)
                inward = (-sx * (w / 2 + 0.12 * head), 0, 0)
                cubes.append(centered(c[0] + inward[0], c[1] - 0.05, c[2], 0.42 * head, 0.5 * head, 0.36 * head, 'teeth'))
        bones.append(bone(f'mandible_{side}', 'head', jaw[0], cubes))
        socket = h(m(ANTENNA_SOCKET))

        def a(p):
            """An antenna point, scaled about its socket."""
            return tuple(s + (v - s) * head * antenna for v, s in zip(h(m(p)), socket))
        elbow = a(ANTENNA_ELBOW)
        bones.append(bone(f'antenna_{side}_1', 'head', socket, [rod(socket, elbow, 0.45 * head, 'antenna')]))
        rings = along([elbow] + [a(p) for p in ANTENNA_WHIP], WHIP_RINGS)
        whip = [rod(p, q, (0.38 + 0.012 * i) * head, 'ring' if i % 2 else 'antenna')   # ringed, a little thicker out
                for i, (p, q) in enumerate(zip(rings, rings[1:]))]
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', elbow, whip))
    bones.append(bone('carry', 'head', h(CARRY)))
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


def model(minor=False):
    """The ant's bones (the game's model file format). minor: the small ant's proportions."""
    bones = [bone('body', None, (0, 4.5, 0)), bone('forebody', 'body', WAIST)]
    bones += head_bones(MINOR_HEAD if minor else MAJOR_HEAD, MINOR_ANTENNA if minor else 1.0)
    bones.append(thorax_bone())
    bones += leg_bones()
    bones.append(bone('petiole', 'body', WAIST, [
        box([-0.5, 3.8, 0.3], [0.5, 4.5, 0.8], 'waist'),          # the short stalk
        box([-1.1, 3.5, 0.7], [1.1, 6.0, 1.9], 'waist'),          # the thick, tall node
        box([-0.85, 6.0, 0.85], [0.85, 6.25, 1.75], 'waist'),     # its rounded top
        box([-0.3, 3.15, 0.9], [0.3, 3.5, 1.6], 'waist'),         # the keel under it
    ]))
    bones.append(bone('postpetiole', 'petiole', GIRDLE_FRONT, [   # the gaster's first segment
        box([-1.3, 3.4, 1.9], [1.3, 5.6, 2.2], 'gaster'),
        box([-1.75, 3.15, 2.1], [1.75, 5.85, 4.6], 'gaster'),
        box([-1.45, 2.95, 2.4], [1.45, 6.05, 4.3], 'gaster'),
        box([-1.5, 3.4, 4.55], [1.5, 5.6, 5.0], 'gaster'),        # the girdle, the pinch
    ]))
    bones.append(bone('abdomen', 'postpetiole', GIRDLE, [
        box([-2.3, 2.7, 4.9], [2.3, 6.4, 9.0], 'gaster'),         # the big second segment
        box([-2.45, 3.1, 5.4], [2.45, 6.0, 8.5], 'gaster'),       # its full sides
        box([-1.7, 6.4, 5.4], [1.7, 6.7, 8.6], 'gaster'),         # the rounded back
        box([-1.7, 2.45, 5.4], [1.7, 2.7, 8.6], 'gaster'),        # the belly
        box([-1.8, 3.0, 8.95], [1.8, 5.9, 10.4], 'gaster'),
        box([-1.2, 3.5, 10.35], [1.2, 5.3, 11.3], 'gaster'),
        box([-0.6, 3.9, 11.25], [0.6, 4.9, 11.8], 'gaster'),      # the tip
        box([-0.15, 4.25, 11.75], [0.15, 4.5, 12.5], 'sting'),    # the sting
    ]))
    return bones
