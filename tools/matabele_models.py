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

Seventh round (the user, 2026-10-23: the head "still looks exactly like the red harvester ant just with a little nose
now", "the head needs to be completely remade", "stick with the black colour just go off the images", five options):
the head, antennae and jaws built again from nothing, from the photos: an egg-shaped head from rounded slices, oval
eyes on the sides, the antennae from the front of the face under little lobes, flat broad jaws with a comb of teeth;
five ways (HEADS, A to E) for the user to pick from, all black.

Eighth round (the user, 2026-10-23: "Face B looks perfect and the body is perfect"): the whole body one even black, no
texture (the 'flat' paint), the eyes a lighter black; head B kept, the others gone; four new jaws on it (JAWS, B1 to
B4) from two more photos, built new (curved blades narrowing to a point, a deep red inner edge); the antennae's whips
fading from black to white toward their tips.

Ninth round (the user, 2026-10-23: "I want the jaws in B4 but I want them in the place of where the fangs are in
B2"): B4's broad toothed blades hung out and down from the face like crushers, four ways to keep them off the floor
(JAWS, 1 to 4).

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
    'head': 'flat', 'thorax': 'flat', 'waist': 'flat', 'gaster': 'flat', 'rim': 'flat', 'leg': 'flat',
    'antenna': 'flat', 'tarsus': 'flat', 'joint': 'flat', 'eye': 'flat', 'jaw': 'flat', 'jawedge': 'flat',
    'sting': 'flat', **{f'whip{i}': 'flat' for i in range(11)},
}
TEXELS = 3.0

# 'flat' (the body and eyes, the user 2026-10-23): one even colour, no grain, specks, hairs or glint.
# The colour: glossy black (the user's pick, the photo; "stick with the black colour", 2026-10-23). grain: how far a
# pixel strays from its colour; flecks: how many pixels are lighter (or darker) specks; hair: how much the gaster's
# fine pale hairs grey it.
BLACK, WHITE = (22, 22, 24), (236, 234, 228)
LOOKS = {
    'A': {'name': 'Black',
          'note': "plain black all over, no texture, like a real Matabele: the eyes a lighter black, the jaws black "
                  "with a deep red inner edge, the antennae fading from black to white toward their tips",
          'grain': 7, 'flecks': 0.10, 'hair': 0.08,
          'colours': {'head': BLACK, 'thorax': BLACK, 'waist': BLACK, 'gaster': BLACK, 'rim': BLACK, 'leg': BLACK,
                      'antenna': BLACK, 'tarsus': BLACK, 'joint': BLACK, 'eye': (50, 50, 55), 'jaw': (32, 22, 22),
                      'jawedge': (96, 26, 22), 'sting': BLACK,
                      # the antenna's whip, segment by segment, from black at the elbow to white at the tip
                      **{f'whip{i}': tuple(round(k + (w - k) * (i / 10) ** 1.4) for k, w in zip(BLACK, WHITE))
                         for i in range(11)}}},
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
                if pattern == 'flat':
                    d = 0                                             # one even colour, no grain or specks
                elif roll < spec['flecks']:
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
# --- the head (remade from nothing, from the user's photos, 2026-10-23) ---------------------------------------------
# The user: the head still looked like the harvester's ("just with a little nose"), so it is remade from the photos,
# five ways to pick from (HEADS), all black. What the photos show (Daniel Kronauer's photo of a major and a minor):
# - The head is an egg, not a cube: a little longer than wide, widest behind the middle, narrowing to the face, domed
#   on top and flatter below, the back rounded. It hangs slanted down from the neck, the majors' the most.
# - The eyes are oval, on the sides of the head's front half, a smooth dark grey against the black.
# - The antennae start from the front of the face, close to the middle, under two little lobes, below and in front of
#   the eyes: the long scape points forward and up, a red-brown joint at the elbow, then the long beaded whip, dark
#   red-brown, reaching on forward and curving down.
# - The jaws are broad flat blades from the front corners of the face, seen broad from the side: reaching forward,
#   then curving down and back to the tips like hooks, red-brown, a comb of red teeth along the inner edge.
# The head is drawn level in its own frame (from the neck, the face toward -z), from slices across it (each slice a box
# with its long edges rounded off in steps, as a Minecraft head is), and its bone is tilted nose-down at the neck.
MAJOR_TILT = 31.0       # degrees: between the user's two photos, the majors the most (my picks, to confirm)
MINOR_TILT = 21.0
# The head was drawn 2.6 times as wide as the thorax; in the photo it is about 1.5 times: drawn smaller now, still big
# (about twice as wide as the thorax, my pick). The minor's head and jaws smaller for its body, its antennae less so.
MAJOR_HEAD = 1.0
MINOR_HEAD = 0.8
MINOR_ANTENNA = 1.1

# The head the user picked (B, "Long face", 2026-10-23: "Face B looks perfect"): its slices (back end, front end, half
# width, bottom, top) from the neck forward and how much their edges are rounded; extra boxes (lo, hi, swatch), the side
# ones for the left side, mirrored; the eyes (middle along, middle up, length, height, how far they stand out); the
# antenna socket, then the elbow and the whip as offsets from the socket on the standing ant.
NECK_BOX = ([-0.8, -0.5, -0.25], [0.8, 0.5, 0.4], 'thorax')
HEAD_B = {
    'slices': [(-0.15, -0.9, 1.85, -1.1, 1.5), (-0.9, -2.2, 2.05, -1.4, 1.85), (-2.2, -3.6, 2.1, -1.5, 1.95),
               (-3.6, -4.9, 2.0, -1.45, 1.7), (-4.9, -5.9, 1.8, -1.35, 1.35), (-5.9, -6.6, 1.5, -1.2, 0.95)],
    'round': 0.42,
    'boxes': [([-0.9, -1.15, -7.0], [0.9, 0.05, -6.55], 'head'), NECK_BOX],
    'side_boxes': [([0.1, 0.55, -6.85], [0.75, 0.95, -6.1], 'head')],
    'eyes': (-4.8, 0.5, 1.1, 0.8, 0.25),
    'socket': (1.0, 0.25, -6.75), 'elbow': (2.9, 0.9, -1.6),
    'whip': [(4.3, 1.2, -3.2), (5.2, 0.8, -5.0), (5.6, -0.2, -6.6), (5.7, -1.3, -7.6)],
}
# The jaws (the user, 2026-10-23): B4's broad three-cornered blades with their four blunt teeth, hung where B2's hooks
# are (out from the face, then down and in, like crushers: the real jaws open side to side across the head, and the
# head hangs down, so they reach out and then down). B4's blades are bigger than B2's hooks and would reach through the
# floor, so four ways side by side (the user's picks): 1 curled in tighter and a little smaller, 2 only curled in
# tighter, 3 only smaller, 4 as they are, the floor left to the animations (the jaws resting closed while walking).
# Each jaw: its middle line (left side, in the head's own frame), its width at each point, its thickness, which way its
# flat sides face (side: out, so the curve shows from the side), how far the tips pass each other, and its teeth (how
# far along, how long, how wide).
JAW_BASE = (1.25, -0.8, -6.35)
B4_WIDTHS = [1.35, 1.1, 0.85, 0.6, 0.38, 0.14]
B4_TEETH = [(0.3, 0.3, 0.24), (0.45, 0.28, 0.22), (0.6, 0.24, 0.2), (0.74, 0.2, 0.18)]
# The jaw's path as offsets from its base: B2's hook drawn out to B4's length, the tips meeting; and a tighter curl,
# bending down sooner and back in under the face.
HANGING = [(0, 0, 0), (0.3, -0.55, -1.15), (0.35, -1.4, -1.85), (0.1, -2.15, -1.9), (-0.45, -2.6, -1.35),
           (-1.1, -2.7, -0.65)]
CURLED = [(0, 0, 0), (0.3, -0.6, -0.8), (0.3, -1.35, -1.15), (0.05, -1.95, -0.95), (-0.5, -2.15, -0.45),
          (-1.1, -2.05, 0.1)]


def hanging_jaw(path, scale):
    return {'jaw': [tuple(b + o * scale for b, o in zip(JAW_BASE, p)) for p in path],
            'jaw_width': [w * scale for w in B4_WIDTHS], 'jaw_thick': 0.42 * scale ** 0.5, 'flat': 'side',
            'jaw_cross': 0.0, 'jaw_teeth': [(f, l * scale, w * scale) for f, l, w in B4_TEETH]}


SMALLER = 0.8
JAWS = {
    '1': {'name': 'Curled in, smaller',
          'note': "B4's blades at 80 percent, curled in tighter under the face: out, down soon, the points meeting "
                  "underneath", **hanging_jaw(CURLED, SMALLER)},
    '2': {'name': 'Curled in',
          'note': "B4's blades at full size, curled in tighter under the face", **hanging_jaw(CURLED, 1.0)},
    '3': {'name': 'Smaller',
          'note': "B4's blades at 80 percent, hanging as B2's hooks do: out from the face, down, the points meeting "
                  "behind", **hanging_jaw(HANGING, SMALLER)},
    '4': {'name': 'Full size, left to the animations',
          'note': "B4's blades at full size hanging as B2's hooks do; they may reach the floor, the animations will "
                  "rest them closed while walking", **hanging_jaw(HANGING, 1.0)},
}
HEADS = {key: {**HEAD_B, **jaw} for key, jaw in JAWS.items()}
WHIP_SEGMENTS = 11              # the real funiculus: 11 segments


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


def unit(v):
    n = math.sqrt(sum(c * c for c in v))
    return tuple(c / n for c in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(p * q for p, q in zip(a, b))


def plate(a, b, across, thick, up, swatch):
    """A flat box from a to b, across wide and thick thick, its flat side toward up (a jaw's blade), turned as the
    viewer and the game turn a box (Z, then Y, then X; the file's X and Z turns read negative, as in rod())."""
    d = unit([q - p for p, q in zip(a, b)])
    y = unit([u - dot(up, d) * c for u, c in zip(up, d)])
    z = cross(d, y)
    turn_y = math.asin(max(-1.0, min(1.0, -d[2])))
    turn_z = math.atan2(d[1], d[0])
    turn_x = math.atan2(y[2], z[2])
    length = math.dist(a, b)
    return box([a[0], a[1] - thick / 2, a[2] - across / 2], [a[0] + length, a[1] + thick / 2, a[2] + across / 2],
               swatch, a, [-math.degrees(turn_x), math.degrees(turn_y), -math.degrees(turn_z)])


def rounded_slice(z0, z1, hw, lo, hi, rnd):
    """One slice across the head, z0 (back) to z1 (front): a box with its four long edges rounded off in steps."""
    r = min(hw, (hi - lo) / 2) * rnd
    out = [([-hw, lo + r, z1], [hw, hi - r, z0]), ([-(hw - r), lo, z1], [hw - r, hi, z0])]
    if r > 0.5:
        q = r * 0.45
        out.append(([-(hw - q), lo + q, z1], [hw - q, hi - q, z0]))
    return out


def carve(lo, hi, cut_lo, cut_hi):
    """What is left of the box lo..hi once the box cut_lo..cut_hi is taken out of it (up to six boxes)."""
    if any(cut_hi[i] <= lo[i] or cut_lo[i] >= hi[i] for i in range(3)):
        return [(lo, hi)]
    out, lo, hi = [], list(lo), list(hi)
    for i in range(3):
        if cut_lo[i] > lo[i]:
            a, b = list(lo), list(hi)
            b[i] = cut_lo[i]
            out.append((a, b))
            lo[i] = cut_lo[i]
        if cut_hi[i] < hi[i]:
            a, b = list(lo), list(hi)
            a[i] = cut_hi[i]
            out.append((a, b))
            hi[i] = cut_hi[i]
    return [(a, b) for a, b in out if all(q - p > 0.04 for p, q in zip(a, b))]


def head_boxes(spec):
    """The head's boxes in its own frame, (lo, hi, swatch): the rounded slices, carved, then the extra boxes and the
    eyes (each eye in pieces following the steps of the head's side)."""
    boxes = []
    for z0, z1, hw, lo, hi in spec['slices']:
        boxes += [(a, b, 'head') for a, b in rounded_slice(z0, z1, hw, lo, hi, spec['round'])]
    for cut_lo, cut_hi in spec.get('cuts', []):
        boxes = [(p, q, s) for a, b, s in boxes for p, q in carve(a, b, cut_lo, cut_hi)]
    boxes += list(spec.get('boxes', []))
    zc, yc, lz, hy, out = spec['eyes']
    front, back = zc - lz / 2, zc + lz / 2
    for sx in (1, -1):
        boxes += [(*sym_points(lo, hi, sx), s) for lo, hi, s in spec.get('side_boxes', [])]
        for z0, z1, hw, lo, hi in spec['slices']:
            a, b = max(z1, front), min(z0, back)
            if b - a > 0.05:
                boxes.append((*sym_points([hw - 0.12, yc - hy / 2, a], [hw + out, yc + hy / 2, b], sx), 'eye'))
    return boxes


def jaw_cubes(f, spec, sx, size):
    """The jaw (the left one for sx = 1) as a curved blade narrowing to its point, in the model file's frame: short
    overlapping pieces along its middle line, a deep red strip along the inner edge of the curve, and its teeth."""
    line = smooth(smooth(spec['jaw']))
    n = len(line) - 1
    # The tips pass one over the other: the left jaw's end a little lower, the right one's a little higher.
    line = [(x, y - sx * spec['jaw_cross'] * (i / n) ** 2, z) for i, (x, y, z) in enumerate(line)]
    pts = [f.pt((x * sx, y, z)) for x, y, z in line]
    up = (0, 1, 0) if spec['flat'] == 'up' else (1, 0, 0)
    widths, thick = spec['jaw_width'], spec['jaw_thick'] * size
    middle = tuple(sum(c) / len(pts) for c in zip(*pts))     # inside the curve: the inner edge faces it

    def width(t):
        k = min(int(t * (len(widths) - 1)), len(widths) - 2)
        u = t * (len(widths) - 1) - k
        return (widths[k] + (widths[k + 1] - widths[k]) * u) * size

    def inward(a, b):
        d = unit([q - p for p, q in zip(a, b)])
        side = unit(cross(up, d))
        if dot(side, [m - c for m, c in zip(middle, lerp(a, b, 0.5))]) < 0:
            side = tuple(-c for c in side)
        return d, side

    cubes = []
    for i, (a, b) in enumerate(zip(pts, pts[1:])):
        t = (i + 0.5) / n
        w, d = width(t), unit([q - p for p, q in zip(a, b)])
        reach = 0.12 * size if i < n - 1 else 0                 # each piece a little longer, so the bends close up
        a2 = tuple(p - c * reach for p, c in zip(a, d))
        b2 = tuple(p + c * reach for p, c in zip(b, d))
        tk = thick * (1 - 0.45 * t)                             # thinner toward the point
        cubes.append(plate(a2, b2, w, tk, up, 'jaw'))
        _, side = inward(a, b)
        strip = max(0.12 * size, w * 0.32)
        edge = [tuple(p + c * (w - strip) / 2 for p, c in zip(P, side)) for P in (a2, b2)]
        cubes.append(plate(edge[0], edge[1], strip, tk * 1.08, up, 'jawedge'))
    for frac, length, across in spec['jaw_teeth']:
        i = min(int(frac * n), n - 1)
        a, b = pts[i], pts[i + 1]
        _, side = inward(a, b)
        w = width((i + 0.5) / n)
        root = tuple(p + c * (w / 2 - 0.08 * size) for p, c in zip(lerp(a, b, frac * n - i), side))
        tip = tuple(p + c * length * size for p, c in zip(root, side))
        cubes.append(plate(root, tip, across * size, thick * 0.8, up, 'jawedge'))
    return cubes


def head_bones(spec, size=1.0, antenna=1.0, tilt=0.0):
    f = HeadFrame(size, tilt)
    head = bone('head', 'forebody', NECK, [f.box(lo, hi, s) for lo, hi, s in head_boxes(spec)])
    if tilt:
        head['rotation'] = [tilt, 0, 0]
    bones = [head]
    for side, sx in (('l', 1), ('r', -1)):
        base = spec['jaw'][0]
        bones.append(bone(f'mandible_{side}', 'head', f.pt((base[0] * sx, base[1], base[2])),
                          jaw_cubes(f, spec, sx, size)))
        sock = spec['socket']
        socket_at = f.world((sock[0] * sx, sock[1], sock[2]))
        k = size * antenna

        def a(off):
            """An antenna point: an offset from the socket on the standing ant, as written in the model file."""
            return f.written(tuple(s + o * k for s, o in zip(socket_at, (off[0] * sx, off[1], off[2]))))
        socket, elbow = f.written(socket_at), a(spec['elbow'])
        end = 0.48 * size                                                     # the scape thickens toward the elbow
        bones.append(bone(f'antenna_{side}_1', 'head', socket, [
            centered(*socket, 0.55 * size, 0.55 * size, 0.55 * size, 'joint'),
            rod(socket, lerp(socket, elbow, 0.55), 0.4 * size, 'antenna'),
            rod(lerp(socket, elbow, 0.5), elbow, end, 'antenna'),
            centered(*elbow, 0.56 * size, 0.56 * size, 0.56 * size, 'joint')]))  # the elbow
        rings = along([elbow] + [a(p) for p in spec['whip']], WHIP_SEGMENTS)
        whip = []
        for i, (p, q) in enumerate(zip(rings, rings[1:])):
            thick = (0.32 + 0.1 * i / (WHIP_SEGMENTS - 1)) * size            # a little thicker toward the tip
            whip.append(rod(p, q, thick, f'whip{i}'))                         # black at the elbow to white at the tip
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', elbow, whip))
    tips = spec['jaw'][-1]
    bones.append(bone('carry', 'head', f.pt((0, tips[1], tips[2] + 0.4))))     # between the jaw tips: the termites held
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


def model(minor=False, head='A'):
    """The ant's bones (the game's model file format, each box naming its swatch until paint() gives it its place on
    the texture). minor: the small ant's proportions; head: which of the HEADS."""
    bones = [bone('body', None, (0, 4.5, 0)), bone('forebody', 'body', WAIST)]
    bones += head_bones(HEADS[head], MINOR_HEAD if minor else MAJOR_HEAD, MINOR_ANTENNA if minor else 1.0,
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
