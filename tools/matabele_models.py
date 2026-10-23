"""The Matabele ant (Megaponera analis), M8 step 1 (2026-10-23): the model, one shape for every ant of the colony (big
majors and small minors are the same animal at different sizes, the minors with a smaller head for their size), and
the looks for the user to pick in the model viewer (python tools/model_viewer.py matabele).

Nothing here goes into the game yet: once the user has picked a look and a size, this file writes the game's model,
texture and body plan into the savanna mod, as ant_models.py does for the harvester ant.

The real animal (photos and Bolton's and Schmidt's descriptions of the Ponerinae):
- Black, heavily built, the head and thorax rough with dense pits (matt), the gaster finely haired (a dull grey sheen);
  legs and antennae black-brown, their tips and the jaws often rust brown.
- Head a little longer than wide, the sides rounded, the back edge dipped in the middle; the antennae sit under small
  frontal lobes; the eyes are small and in front of the head's middle; strong triangular toothed jaws.
- The thorax deep and rounded, no spines; the propodeum (the back of the thorax) box-like with a flat back face.
- One waist segment (a ponerine): a thick, tall, flat-topped node; a little keel under it.
- The gaster with a pinch between its first and second segments (the ponerine "girdle") and a working sting at the tip.
- Legs long and stout; the hind legs the longest.

Bones: the harvester worker's names (tools/body_plan.py), so the motion library and the game's body code work as they
are. A ponerine has no postpetiole: here the bone of that name holds the gaster's first segment, so the gaster bends
at its real pinch.

Units are model pixels before SCALE, as in ant_models.py: the head points to -Z, the ground is y = 0, left is +X.
"""
import math
import random
import struct
import zlib

SCALE = 0.85

# --- texture: 64x64, 8x8 swatches (column, row, pattern); colours come from the look ------------------------------
CELLS = {
    'head':    (0, 0, 'pitted'),
    'thorax':  (1, 0, 'pitted'),
    'waist':   (2, 0, 'pitted'),
    'gaster':  (3, 0, 'haired'),
    'leg':     (4, 0, 'noise'),
    'antenna': (5, 0, 'noise'),
    'jaw':     (6, 0, 'noise'),
    'eye':     (7, 0, 'eye'),
    'tarsus':  (0, 1, 'noise'),
    'joint':   (1, 1, 'noise'),
    'sting':   (2, 1, 'noise'),
    'tip':     (3, 1, 'noise'),    # the antenna's last segments
}

# The looks (my three readings of the real ant, for the user to pick). shine: highlights on the hard parts; hair: how
# much the gaster's fine hairs grey it; pits: how many dark pits on the head and thorax.
LOOKS = {
    'A': {'name': 'Matt black',
          'note': 'dull black, the head and thorax densely pitted, the gaster greyed by fine hairs, legs black-brown',
          'shine': 0.0, 'hair': 0.35, 'pits': 0.3,
          'colours': {'head': (36, 34, 33), 'thorax': (38, 35, 34), 'waist': (37, 34, 33), 'gaster': (40, 39, 39),
                      'leg': (40, 33, 29), 'antenna': (40, 33, 29), 'jaw': (44, 31, 24), 'eye': (16, 16, 18),
                      'tarsus': (52, 40, 32), 'joint': (30, 27, 26), 'sting': (60, 44, 34), 'tip': (48, 38, 31)}},
    'B': {'name': 'Satin black',
          'note': 'deeper black with a soft shine on the head, thorax and gaster, fewer hairs, legs black',
          'shine': 0.55, 'hair': 0.15, 'pits': 0.2,
          'colours': {'head': (26, 26, 28), 'thorax': (27, 27, 29), 'waist': (27, 26, 28), 'gaster': (29, 29, 31),
                      'leg': (30, 28, 28), 'antenna': (30, 28, 28), 'jaw': (34, 26, 22), 'eye': (12, 12, 14),
                      'tarsus': (38, 32, 28), 'joint': (22, 21, 22), 'sting': (50, 38, 30), 'tip': (36, 31, 28)}},
    'C': {'name': 'Black, rust-brown tips',
          'note': 'A, with the jaws, antenna tips, feet and sting rust brown, as in many field photos',
          'shine': 0.1, 'hair': 0.35, 'pits': 0.3,
          'colours': {'head': (36, 34, 33), 'thorax': (38, 35, 34), 'waist': (37, 34, 33), 'gaster': (40, 39, 39),
                      'leg': (44, 34, 28), 'antenna': (52, 36, 27), 'jaw': (96, 46, 24), 'eye': (16, 16, 18),
                      'tarsus': (112, 58, 30), 'joint': (32, 28, 26), 'sting': (118, 62, 32), 'tip': (104, 56, 30)}},
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
                k = rnd.uniform(-0.08, 0.08)
                rgb = list(base)
                if pattern == 'pitted':
                    if rnd.random() < spec['pits']:
                        k -= 0.35                                   # a pit
                    if spec['shine'] and (x, y) in ((2, 2), (3, 2), (2, 3), (5, 5)):
                        k += spec['shine'] * 1.6                    # the light on a hard plate
                elif pattern == 'haired':
                    if rnd.random() < 0.5:
                        mix = spec['hair'] * rnd.uniform(0.25, 0.55)  # a fine pale hair
                        rgb = [c + (150 - c) * mix for c in rgb]
                    if spec['shine'] and (x, y) in ((2, 2), (3, 2), (2, 3)):
                        k += spec['shine'] * 1.6
                elif pattern == 'eye':
                    k = rnd.uniform(-0.05, 0.05) + (3.0 if (x, y) == (2, 2) else 0.0)   # a glint
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


def box(lo, hi, swatch, pivot=None, rotation=None):
    """A box from corner lo to corner hi (design units), optionally turned by rotation about pivot."""
    col, row, _ = CELLS[swatch]
    face = {'uv': [col * 8 + 1, row * 8 + 1], 'uv_size': [6, 6]}
    c = {'origin': r3(lo), 'size': r3([b - a for a, b in zip(lo, hi)]),
         'uv': {f: dict(face) for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if rotation and any(abs(v) > 1e-6 for v in rotation):
        c['pivot'] = r3(pivot)
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
    b = {'name': name, 'pivot': r3(pivot)}
    if parent:
        b['parent'] = parent
    if cubes:
        b['cubes'] = list(cubes)
    return b


def mirror(p):
    return (-p[0], p[1], p[2])


def sides(lo_x, hi_x, sx):
    """The x range lo_x..hi_x (left side) on side sx, low end first."""
    return sorted((lo_x * sx, hi_x * sx))


# --- the ant --------------------------------------------------------------------------------------------------------
NECK = (0, 3.1, -5.1)
WAIST = (0, 2.7, 0.7)            # thorax to the waist node
GIRDLE_FRONT = (0, 2.9, 1.9)     # the waist node to the gaster's first segment
GIRDLE = (0, 2.9, 4.1)           # the pinch between the gaster's first and second segments
MANDIBLE_TIPS_Z = -10.9
CARRY = (0, 2.4, -11.4)          # between the jaw tips, a little in front: where the stack of termites is held

# Legs: hip, knee, ankle, toe (left side). Long and stout, the body carried high; the hind legs the longest.
LEGS = {
    1: [(0.95, 2.3, -4.2), (3.3, 4.4, -5.6), (4.9, 1.0, -7.2), (5.6, 0.0, -8.4)],
    2: [(1.05, 2.3, -2.3), (3.9, 4.6, -2.6), (5.9, 1.0, -2.4), (6.9, 0.0, -2.0)],
    3: [(1.0, 2.25, -0.6), (3.6, 4.7, 0.9), (5.5, 1.0, 4.4), (6.2, 0.0, 6.1)],
}
ANTENNA_SOCKET = (0.55, 4.3, -8.4)
ANTENNA_ELBOW = (1.95, 5.7, -6.2)                                    # the long scape reaches back past the eyes
ANTENNA_WHIP = [(2.6, 6.0, -8.1), (3.3, 5.6, -9.9), (3.75, 4.8, -11.3)]

# The minor: the same ant, its head (with jaws) smaller for its body; the antennae a little less so (my picks).
MINOR_HEAD = 0.82
MINOR_ANTENNA = 0.92


def head_bones(head=1.0, antenna=1.0):
    def h(p):
        """A head point, the head scaled about the neck."""
        return tuple(n + (v - n) * head for v, n in zip(p, NECK))

    def hb(lo, hi, swatch):
        return box(h(lo), h(hi), swatch)

    cubes = [
        hb([-1.6, 2.1, -8.9], [1.6, 4.4, -5.5], 'head'),             # the head capsule
        hb([-1.75, 2.35, -8.5], [1.75, 4.15, -5.8], 'head'),         # its rounded sides
        hb([-1.25, 4.4, -8.5], [1.25, 4.65, -5.9], 'head'),          # the rounded crown
        hb([-1.3, 1.95, -8.4], [1.3, 2.1, -6.0], 'head'),            # the underside
        hb([-1.0, 2.4, -9.3], [1.0, 3.3, -8.9], 'head'),             # the clypeus over the jaws
        hb([-0.7, 2.7, -5.6], [0.7, 3.5, -4.9], 'thorax'),           # the neck
    ]
    for sx in (1, -1):
        cubes += [
            hb([sides(0.55, 1.6, sx)[0], 2.4, -5.5], [sides(0.55, 1.6, sx)[1], 4.25, -5.2], 'head'),   # back corners
            hb([sides(0.2, 0.85, sx)[0], 4.25, -8.85], [sides(0.2, 0.85, sx)[1], 4.55, -8.0], 'head'),  # frontal lobe
            hb([sides(1.74, 1.86, sx)[0], 3.45, -8.05], [sides(1.74, 1.86, sx)[1], 4.0, -7.45], 'eye'),
        ]
    bones = [bone('head', 'forebody', NECK, cubes)]
    for side, sx in (('l', 1), ('r', -1)):
        def m(p):
            return p if sx > 0 else mirror(p)
        bones.append(bone(f'mandible_{side}', 'head', h(m((0.85, 2.55, -9.2))), [
            hb([sides(0.3, 1.45, sx)[0], 2.25, -10.0], [sides(0.3, 1.45, sx)[1], 2.85, -9.1], 'jaw'),   # the broad base
            hb([sides(0.0, 0.95, sx)[0], 2.3, -10.6], [sides(0.0, 0.95, sx)[1], 2.8, -9.95], 'jaw'),    # the toothed edge
            hb([sides(-0.15, 0.45, sx)[0], 2.35, MANDIBLE_TIPS_Z], [sides(-0.15, 0.45, sx)[1], 2.75, -10.55], 'jaw'),
        ]))
        socket = h(m(ANTENNA_SOCKET))

        def a(p):
            """An antenna point, scaled about its socket."""
            return tuple(s + (v - s) * head * antenna for v, s in zip(h(m(p)), socket))
        elbow = a(ANTENNA_ELBOW)
        bones.append(bone(f'antenna_{side}_1', 'head', socket, [rod(socket, elbow, 0.4 * head, 'antenna')]))
        points = [elbow] + [a(p) for p in ANTENNA_WHIP]
        whip = [rod(p, q, (0.36 + 0.03 * i) * head, 'antenna' if i < 2 else 'tip')   # thickening slowly to the tip
                for i, (p, q) in enumerate(zip(points, points[1:]))]
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', elbow, whip))
    bones.append(bone('carry', 'head', h(CARRY)))
    return bones


def thorax_bone():
    return bone('thorax', 'forebody', (0, 3.0, -2.0), [
        box([-1.45, 2.2, -5.0], [1.45, 4.3, -3.0], 'thorax'),     # the pronotum, broad and round
        box([-1.1, 4.3, -4.7], [1.1, 4.55, -3.2], 'thorax'),      # its rounded top
        box([-1.15, 2.3, -3.05], [1.15, 4.05, -1.6], 'thorax'),   # the mesonotum
        box([-0.95, 2.3, -1.65], [0.95, 3.6, -1.2], 'thorax'),    # the groove before the propodeum
        box([-1.15, 2.0, -1.25], [1.15, 3.9, 0.7], 'thorax'),     # the propodeum, box-like, flat at the back
    ])


def leg_bones():
    bones = []
    for n, (hip, knee, ankle, toe) in LEGS.items():
        for side, sx in (('l', 1), ('r', -1)):
            def m(p):
                return p if sx > 0 else mirror(p)
            h, k, a, t = m(hip), m(knee), m(ankle), m(toe)
            bones.append(bone(f'leg_{side}{n}', 'forebody', h, [
                centered(h[0], h[1], h[2], 0.95, 0.95, 0.95, 'leg'),  # the coxa
                rod(h, k, 0.8, 'leg')]))                              # the femur, stout
            bones.append(bone(f'leg_{side}{n}_lower', f'leg_{side}{n}', k, [
                centered(k[0], k[1], k[2], 0.68, 0.68, 0.68, 'joint'),
                rod(k, a, 0.62, 'leg')]))                             # the tibia
            bones.append(bone(f'leg_{side}{n}_foot', f'leg_{side}{n}_lower', a, [rod(a, t, 0.42, 'tarsus')]))
    return bones


def model(minor=False):
    """The ant's bones (the game's model file format). minor: the small ant's proportions."""
    bones = [bone('body', None, (0, 3.0, 0)), bone('forebody', 'body', WAIST)]
    bones += head_bones(MINOR_HEAD if minor else 1.0, MINOR_ANTENNA if minor else 1.0)
    bones.append(thorax_bone())
    bones += leg_bones()
    bones.append(bone('petiole', 'body', WAIST, [
        box([-0.45, 2.3, 0.6], [0.45, 2.9, 1.0], 'waist'),        # the short stalk
        box([-1.0, 2.0, 0.95], [1.0, 4.1, 1.85], 'waist'),        # the thick, tall node
        box([-0.8, 4.1, 1.05], [0.8, 4.3, 1.7], 'waist'),         # its flat top
        box([-0.3, 1.7, 1.1], [0.3, 2.0, 1.6], 'waist'),          # the keel under it
    ]))
    bones.append(bone('postpetiole', 'petiole', GIRDLE_FRONT, [   # the gaster's first segment
        box([-1.1, 2.0, 1.85], [1.1, 3.9, 2.05], 'gaster'),
        box([-1.55, 1.6, 2.0], [1.55, 4.3, 3.9], 'gaster'),
        box([-1.3, 1.9, 3.85], [1.3, 4.0, 4.25], 'gaster'),       # the girdle, the pinch
    ]))
    bones.append(bone('abdomen', 'postpetiole', GIRDLE, [
        box([-1.85, 1.3, 4.2], [1.85, 4.6, 7.4], 'gaster'),       # the big second segment
        box([-2.0, 1.6, 4.6], [2.0, 4.3, 7.0], 'gaster'),         # its full sides
        box([-1.4, 4.6, 4.6], [1.4, 4.85, 7.0], 'gaster'),        # the rounded back
        box([-1.4, 1.1, 4.6], [1.4, 1.3, 7.0], 'gaster'),         # the belly
        box([-1.45, 1.5, 7.35], [1.45, 4.1, 8.5], 'gaster'),
        box([-0.9, 1.9, 8.45], [0.9, 3.5, 9.2], 'gaster'),        # the tip
        box([-0.15, 2.35, 9.15], [0.15, 2.6, 9.85], 'sting'),     # the sting
    ]))
    return bones
