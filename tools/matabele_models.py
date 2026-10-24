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
(JAWS, 1 to 4); then 4 picked and made new: bulkier, wider crushers, all black.

Tenth round (the user, 2026-10-23, three photos: the hanging jaws "look like ram horns"): the jaws remade as the real
ones are, short thick triangles hinged at the mouth's corners, folded shut across the mouth into a pointed muzzle,
the toothed edges meeting; closed and a little open side by side (JAWS A and B).

Eleventh round (the user, 2026-10-23, a close photo of a real jaw: "you got it now make them long and curve down for
B"): B's jaws twice as long, curving down along their length like sickles (MANDIBLE 'bend'), still a little open; A
unchanged.

Twelfth round (the user, 2026-10-23: "Okay thats alot better"): only the long jaws kept, all black, the toothed inner
edges the darkest black, hinged higher so they meet the chin (two heights, JAWS 1 and 2); the antennae moved up and in
on the face, to the user's red dot, clear of the jaws.

Thirteenth round (the user, 2026-10-23: "Jaw 2 a little higher"): height 2 picked and raised a little more (0.75 over
the old jaw, as high as it goes before touching the antennae on the major); the toothed inner edge a shade less black
than before, still darker than the body so it stands out a little.

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
                  "with the toothed inner edge a shade darker than the body, the antennae fading from black to white toward their tips",
          'grain': 7, 'flecks': 0.10, 'hair': 0.08,
          'colours': {'head': BLACK, 'thorax': BLACK, 'waist': BLACK, 'gaster': BLACK, 'rim': BLACK, 'leg': BLACK,
                      'antenna': BLACK, 'tarsus': BLACK, 'joint': BLACK, 'eye': (50, 50, 55), 'jaw': BLACK,
                      'jawedge': (13, 13, 14), 'sting': BLACK,
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
    'socket': (0.55, 0.7, -6.9), 'elbow': (2.9, 0.9, -1.6),
    'whip': [(4.3, 1.2, -3.2), (5.2, 0.8, -5.0), (5.6, -0.2, -6.6), (5.7, -1.3, -7.6)],
}
# The jaws (the user, 2026-10-23, from three photos: the hanging blades "look like ram horns"): a real ant's jaws are
# hinged at the corners of the mouth and swing side to side like gates in the plane of the mouth; closed, the Matabele's
# are two short, thick triangles folded across the front of the mouth under the clypeus, their toothed inner edges
# meeting in the middle, so from the front the face ends in a pointed chin, a muzzle. Each jaw is drawn as rows across
# it from the hinge to the point: the outer edge curving round from the face's full width to the point, the inner edge
# slanting in from the hinge's inner corner to the middle and then running along it (the toothed edge, the darkest
# black, the user's idea), blunt teeth on every other row, the left jaw's between the right one's so they interlock
# when closed; thickest at the hinge, thinner toward the point, sloping a little down.
# hinge: the outer back corner (left side, head frame); length: hinge to point; width: the outer edge's reach from the
# middle at the hinge; basal: the inner corner's reach at the hinge, and how far along the inner edge reaches the middle;
# top, slope: the top at the hinge and how far it drops by the point; thick: at the hinge and at the point; gap: the
# inner edge's distance from the middle; tooth: how far a tooth reaches past it; open: degrees each jaw swings out;
# bend (B only): degrees below the head's level at the hinge and at the point, the jaw curving down along its length
# (B's starts 32 up, which on the major's head, slanted 31 down, is straight out, so the long tips clear the ground).
MANDIBLE = {'hinge': (1.5, -1.05, -6.2), 'length': 2.1, 'width': 1.5, 'basal': (0.9, 0.38), 'top': -1.05,
            'slope': 0.25, 'thick': (0.72, 0.32), 'gap': 0.1, 'tooth': 0.16, 'rows': 11, 'open': 0.0}
# B's long jaws curving down (the user, 2026-10-23: "Okay thats alot better"), all black, hinged higher up under the
# face so they meet the chin instead of hanging below it: two heights to pick from (only the jaw differs).
LONG_JAWS = {**MANDIBLE, 'length': 4.0, 'width': 1.6, 'basal': (0.95, 0.25), 'rows': 22,
             'thick': (1.3, 0.28), 'bend': (-32.0, 60.0), 'open': 16.0}


def raised(up):
    """The long jaws with their hinge (and so the whole jaw) up higher by up."""
    x, y, z = LONG_JAWS['hinge']
    return {**LONG_JAWS, 'hinge': (x, y + up, z), 'top': LONG_JAWS['top'] + up}


JAWS = {
    '2': {'name': 'Jaws high up',
          'note': "the long jaws curving down, all black, hinged high up so their base is tucked into the face under "
                  "the antennae (the user's pick, version 2, then a little higher)",
          'mandible': raised(0.75)},
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


def jaw_line(m):
    """The jaw's line from the hinge to the point, one (z, y) point per row edge in the head's own frame: its top,
    straight and sloping a little down, or, with 'bend' (degrees below level at the hinge and at the point), its middle,
    curving down along its length like a sickle, the bend growing toward the point."""
    z, n = m['hinge'][2], m['rows']
    if 'bend' not in m:
        return [(z - m['length'] * r / n, m['top'] - m['slope'] * r / n) for r in range(n + 1)]
    y, (a0, a1) = m['top'] - m['thick'][0] / 2, m['bend']
    pts = [(z, y)]
    for r in range(n):
        a = math.radians(a0 + (a1 - a0) * ((r + 0.5) / n) ** 1.5)
        z, y = z - math.cos(a) * m['length'] / n, y - math.sin(a) * m['length'] / n
        pts.append((z, y))
    return pts


def jaw_cubes(f, spec, sx, size):
    """The jaw (the left one for sx = 1) in the head's own frame: rows across it from the hinge to the point (see
    MANDIBLE), each a box round its piece of the middle line, the inner edge's strip and the teeth in the toothed
    edge's colour."""
    m = spec['mandible']
    basal_x, basal_t = m['basal']
    rows, line, cubes = m['rows'], jaw_line(m), []
    for r in range(rows):
        t = (r + 0.5) / rows
        (za, ya), (zb, yb) = line[r], line[r + 1]
        x_out = m['width'] * (1 - t ** 1.6) ** 0.7
        x_in = max(m['gap'], basal_x * (1 - t / basal_t)) if t < basal_t else m['gap']
        thick = m['thick'][0] + (m['thick'][1] - m['thick'][0]) * t
        run = math.hypot(za - zb, ya - yb)
        down, along = abs(ya - yb) / run, abs(za - zb) / run      # the piece's own slant: its thickness across it

        def piece(shrink, z_mid=None, z_half=None):
            """The row's box in z and y, its thickness less shrink on each face (or only z_half either side of
            z_mid along the line, for a tooth)."""
            if 'bend' not in m:                 # straight: the line is the top, the jaw hangs under it
                top = (ya + yb) / 2
                lo, hi = [zb, top - thick + shrink], [za, top - shrink]
                if z_mid is not None:
                    lo[0], hi[0] = z_mid[0] - z_half, z_mid[0] + z_half
                return lo, hi
            pz, py = (thick / 2 - shrink) * down, (thick / 2 - shrink) * along
            if z_mid is None:
                return [min(za, zb) - pz, min(ya, yb) - py], [max(za, zb) + pz, max(ya, yb) + py]
            zm, ym = z_mid
            return [zm - z_half * along - pz, ym - z_half * down - py], [zm + z_half * along + pz, ym + z_half * down + py]
        toothed = t >= basal_t
        strip = min(0.2, (x_out - x_in) / 2) if toothed else 0
        (z_lo, y_lo), (z_hi, y_hi) = piece(0)
        if x_out - x_in - strip > 0.05:
            cubes.append(f.box(*sym_points([x_in + strip, y_lo, z_lo], [x_out, y_hi, z_hi], sx), 'jaw'))
        if strip:
            (z_lo, y_lo), (z_hi, y_hi) = piece(0.04)
            cubes.append(f.box(*sym_points([x_in, y_lo, z_lo], [x_in + strip, y_hi, z_hi], sx), 'jawedge'))
            if (r + (0 if sx > 0 else 1)) % 2 == 0:                        # the teeth interlock when closed
                (z_lo, y_lo), (z_hi, y_hi) = piece(0.1, ((za + zb) / 2, (ya + yb) / 2), 0.07)
                cubes.append(f.box(*sym_points([x_in - m['tooth'], y_lo, z_lo], [x_in, y_hi, z_hi], sx), 'jawedge'))
    return cubes


def head_bones(spec, size=1.0, antenna=1.0, tilt=0.0):
    f = HeadFrame(size, tilt)
    head = bone('head', 'forebody', NECK, [f.box(lo, hi, s) for lo, hi, s in head_boxes(spec)])
    if tilt:
        head['rotation'] = [tilt, 0, 0]
    bones = [head]
    for side, sx in (('l', 1), ('r', -1)):
        hinge = spec['mandible']['hinge']
        jaw = bone(f'mandible_{side}', 'head', f.pt((hinge[0] * sx, hinge[1], hinge[2])), jaw_cubes(f, spec, sx, size))
        if spec['mandible']['open']:
            jaw['rotation'] = [0, -spec['mandible']['open'] * sx, 0]          # swung out at its hinge
        bones.append(jaw)
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
    m = spec['mandible']
    z, y = jaw_line(m)[-1]
    bones.append(bone('carry', 'head', f.pt((0, y + (m['thick'][1] / 2 if 'bend' in m else 0), z))))  # at the points
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


# --- the draft game files (2026-10-23, for the animations) -----------------------------------------------------------
# The model as the game would read it, a major and a minor, each with its own texture, written into drafts/savanna/
# (tools/mod_paths.py, "Drafts"): every tool (the animation lab, the body plans, the motion library) reads them as if
# they were in the savanna mod, while the game never sees them. Putting the ant into the game is moving these files
# into the mod (the user's call). The clips are written next to them by tools/matabele_anims.py.
#   python tools/matabele_models.py      writes both models and textures
import json  # noqa: E402
import os  # noqa: E402
import sys  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mod_paths  # noqa: E402

DRAFTS = mod_paths.draft_assets('savanna')
MODELS = {'matabele_major': False, 'matabele_minor': True}     # name -> minor
JAW = '2'                                                       # the jaws the user picked (JAWS)
LOOK = 'A'


def game_bones(name):
    """The model's bones with every face's place on its texture, and the texture: (bones, rows, width, height)."""
    return paint(model(minor=MODELS[name], head=JAW), LOOK)


def write_drafts():
    for name in MODELS:
        bones, rows, w, h = game_bones(name)
        geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': f'geometry.{name}', 'texture_width': w, 'texture_height': h,
                            'visible_bounds_width': 4, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]},
            'bones': bones}]}
        for rel, data in ((f'geo/entity/{name}.geo.json', None), (f'textures/entity/{name}.png', png(rows))):
            path = DRAFTS / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            if data is None:
                with open(path, 'w', encoding='utf-8', newline='\n') as f:
                    json.dump(geo, f, indent=1)
            else:
                path.write_bytes(data)
        print('wrote', (DRAFTS / f'geo/entity/{name}.geo.json').relative_to(mod_paths.PROJECT))


if __name__ == '__main__':
    write_drafts()
