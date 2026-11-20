"""The African bullfrog's model maker (docs/plans/prey-fish-and-frogs.md; the model workshop, stage 2: the plain 3D
shape). Remade 2026-11-20 from the user's two real photos (references/inspiration/bullfrog_photo_side.png and
bullfrog_photo_front.png), at the user's word: the first build (v1 to v4) copied the AI sheet's proportions, a tall box
head with a straight mouth and arms that did not join the body, and patching it kept that wrong start.

    python tools/workshop.py snapshot bullfrog --note "..."   keep this build as the next version
    python tools/workshop.py versions bullfrog                the versions page

Made 40 model pixels long, snout to rump (the sheet's 2.5 blocks), drawn in the game at 0.8 blocks for a male and 0.65
for a female. Units are model pixels (16 a block); the head to -Z, up +Y, left +X, the ground at y 0, the middle line at
x 0. Along the length the numbers use the outline's x, rump 0 to snout 40 (z = 20 - x here).

Read off the side photo on a model-pixel grid (13.9 photo pixels a model pixel):
- **Low and long:** the back rises from the rump (7 high) to its peak behind the eyes (19), half the length; the head
  slopes down from there to a blunt rounded snout (13 high at the tip).
- **The mouth (the user: "all frogs' mouths have this V shape"):** from the snout tip (12.6 high) the mouth line runs
  back and down to its corner under the eardrum (8.4 high, 13 pixels behind the snout); from above the snout is a
  rounded V, the head narrowing from the jaw corners to the tip. The lower jaw and the throat below it are their own
  bones (the gape for the lunge and the bite, the throat for calling).
- **Eyes:** Minecraft style, not from the photos (the user, 2026-11-20; EYE_LOOKS below); a round eardrum behind each
  eye; two small nostrils near the tip.
- **Legs joined and bent:** each is a chain of round pieces turned from joint to joint (the shoulder or hip inside the
  body, so nothing floats). The arm: the upper arm down and back from under the jaw corner to the elbow near the ground,
  the forearm forward to the wrist, the hand flat with four long plain fingers (no webbing on a frog's hand). The hind
  leg folded the way it sits: the thigh forward along the flank from the hip to the knee (the big haunch), the shin
  back under it to the heel, the foot forward again, flat and webbed with five toes (the user's detailed webbed foot
  comes later).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (cube, curve, disc, profile, paint_faces, png, aim, rot, mat_mul, transpose, vsub,  # noqa: E402
                         vlen)


# --- round pieces, coarser than the monitor's (the frog is small in the game: 0.8 blocks) -----------------------------
# The shared limb() and ball() cut half-pixel slices with quarter-pixel radii (the monitor's legs, 6 blocks long);
# the frog's first remake cost 725 boxes that way. Here a piece is cut in one-pixel slices with radii to half a pixel,
# equal slices joined, and a joint is three slices; the shape is the same at the frog's size.
def limb(cubes, axis, centre, a0, a1, prof, part, lead=0.0):
    n = max(1, round(abs(a1 - a0)))
    runs = []
    for i in range(n):
        r = round(profile(prof, (i + 0.5) / n) * 2) / 2
        s0, s1 = a0 + (a1 - a0) * i / n, a0 + (a1 - a0) * (i + 1) / n
        if runs and runs[-1][0] == r:
            runs[-1][2] = s1
        else:
            runs.append([r, s0, s1])
    if lead:
        runs[0][1] -= lead * (1 if a1 > a0 else -1)
    for r, s0, s1 in runs:
        disc(cubes, axis, centre, r, s0, s1, part)


def ball(cubes, axis, centre, r, part):
    c = centre[axis]
    for r_, a, b in ((r * 0.72, -r, -r * 0.45), (r, -r * 0.45, r * 0.45), (r * 0.72, r * 0.45, r)):
        disc(cubes, axis, centre, round(r_ * 2) / 2, c + a, c + b, part)

# --- shape numbers (model pixels; along the outline's x, rump 0 to snout 40) ------------------------------------------
LENGTH = 40.0
TOP = ((0, 6.5), (3, 7.5), (7, 10.0), (10, 12.0), (15, 14.6), (20, 17.4), (25, 18.6), (30, 19.0), (33, 18.4),
       (36, 17.0), (38, 15.6), (39.3, 14.4), (40, 13.3))
BELLY = ((0, 2.4), (1.5, 1.2), (5, 0.7), (15, 0.4), (24, 0.6), (27, 2.0), (30, 3.4), (33, 4.6), (35, 5.6), (37, 7.0),
         (39, 10.0), (40, 12.0))
HALF_WIDTH = ((0, 5.0), (3, 8.5), (8, 11.0), (14, 12.0), (24, 12.0), (28, 11.5), (31, 10.8), (34, 9.6), (36.5, 7.8),
              (38.5, 5.6), (40, 3.0))          # from above: the head a rounded V from the jaw corners to the snout
MOUTH = ((27, 8.4), (31, 9.5), (34, 10.4), (37, 11.5), (40, 12.6))   # the mouth line, low at its corner
HEAD_FROM = 27.0         # the head and jaw bones start at the mouth's corner
THROAT_BELOW = 6.0       # the throat bone is the jaw below this height
ROUNDNESS = 2.4          # the cross-section's superellipse power: 2 an ellipse, higher squarer
FLAT_BELLY = 0.5         # how much of the lower half keeps its full width
EAR_AT, EAR_HEIGHT, EAR_R = 27.6, 13.2, 1.2
SLICE = 2.0
LAYER = 2.0

# The legs: joints in the outline's x (along), height, and out from the middle line (for the left leg).
ARM = ((29.0, 6.5, 8.0), (27.5, 4.8, 11.6), (32.0, 1.4, 12.6))       # shoulder (inside), elbow at the side under
                                                                    # the jaw corner, wrist forward and down (the front photo)
ARM_R = (((0, 2.2), (1, 1.9)), ((0, 2.0), (1, 1.6)))
LEG = ((5.0, 5.5, 7.5), (14.5, 5.0, 12.5), (4.5, 2.2, 11.8))        # hip (inside), knee, heel
LEG_R = (((0, 4.0), (0.45, 4.6), (1, 3.2)), ((0, 2.6), (1, 2.0)))


def outline_z(x):
    return LENGTH / 2 - x


def at(points, x):
    """The value at x along the length, eased between control points (x, value)."""
    return curve([(p / LENGTH, v) for p, v in points], x / LENGTH)


def half_width_at(x, y):
    """The body's half width at height y in the slice at x: a superellipse, flatter toward the belly."""
    top, belly = at(TOP, x), at(BELLY, x)
    mid, half_h = (top + belly) / 2, (top - belly) / 2
    u = (y - mid) / half_h
    if u < 0:
        u = -max(0.0, (-u - FLAT_BELLY) / (1 - FLAT_BELLY))
    k = max(0.0, 1 - abs(u) ** ROUNDNESS) ** (1 / ROUNDNESS)
    return at(HALF_WIDTH, x) * k


def body_slices(cubes_by_bone):
    """The body, head, jaw and throat as 2-pixel slices along the length, each a stack of layers (one box each); a
    layer that crosses the mouth line is split there so the jaw parts cleanly from the head."""
    for i in range(round(LENGTH / SLICE)):
        x0, x1 = i * SLICE, (i + 1) * SLICE
        xm = (x0 + x1) / 2
        top, belly = at(TOP, xm), at(BELLY, xm)
        in_head = xm >= HEAD_FROM
        mouth = at(MOUTH, xm) if in_head else None
        y = belly
        while y < top - 0.01:
            y1 = min(top, y + LAYER)
            if mouth is not None and y < mouth - 0.01 and mouth < y1 - 0.01:
                y1 = mouth
            ym = (y + y1) / 2
            hw = round(half_width_at(xm, ym) * 4) / 4
            if hw >= 0.5:
                if not in_head:
                    bone, part = 'body', 'skin'
                elif ym >= mouth:
                    bone, part = 'head', 'skin'
                elif ym >= THROAT_BELOW:
                    bone, part = 'jaw', 'jaw'
                else:
                    bone, part = 'throat', 'throat'
                z0, z1 = sorted((outline_z(x0), outline_z(x1)))
                cubes_by_bone[bone].append(cube([-hw, y, z0], [hw, y1, z1], part))
            y = y1


def _box(cubes, x_a, x_b, y0, y1, z0, z1, part):
    """A box between two x values given in either order."""
    lo, hi = sorted((x_a, x_b))
    cubes.append(cube([lo, y0, z0], [hi, y1, z1], part))


# --- the eyes: Minecraft style (the user, 2026-11-20: "throw out all realism, I want them to look good in Minecraft";
# every earlier eye try deleted, nothing taken from it or from any picture). Straight square boxes on whole model
# pixels, never tilted, and the eye painted on their flat faces as pixel art, the way Minecraft's own mobs have them.
# Each eye is still its own bone, so it can blink later. A look is: the box (x from the middle line out, y, z with the
# snout to -z, all whole pixels; for the left eye, mirrored for the right), the pixel art on its front face and on
# its outer face, or one 'strip' running from the front face round the corner onto the outer face (rows from the box's top; 'g' skin, 'w' white, 'k' black, 'h' glint, 'd' dark lid, 'l' light lid; anything outside the art is skin), and
# extra skin boxes (a brow).
EYE_LOOKS = {
    # A, pop-up cubes: a cube standing up on top of the head at each front corner, a green lid on top.
    'cubes': {'box': ((2, 8), (15, 22), (-15, -9)),
              'front': ('gggggg', 'wwwwww', 'wwkkww', 'wwkkww', 'wwwwww'),
              'outer': ('gggggg', 'wwwwww', 'wkkwww', 'wkkwww', 'wwwwww'),
              'extra': ()},
    # B, side cubes: a cube poking straight out of each side of the head, its top level with the head's top, so the
    # head reads wide and flat from ahead.
    'sides': {'box': ((6, 12), (13, 19), (-15, -9)),
              'front': ('gggggg', 'wwwwww', 'wwwkkw', 'wwwkkw', 'wwwwww', 'gggggg'),
              'outer': ('gggggg', 'wwwwww', 'wkkwww', 'wkkwww', 'wwwwww', 'gggggg'),
              'extra': ()},
    # C, painted on: a flat plate set into each upper corner of the head, standing a pixel or two proud, the eye
    # painted on it the way a Minecraft cow's or pig's is (a white square and a black one), the black wrapping round
    # the front corner so it reads from ahead and from the side, under a square brow.
    'painted': {'box': ((6, 11), (12, 18), (-16, -8)),
                'front': ('ggggg', 'ggwkk', 'ggwkk', 'ggwkk', 'ggggg'),
                'outer': ('gggggggg', 'kkkwwwgg', 'kkkwwwgg', 'kkkwwwgg', 'gggggggg'),
                'extra': (((5, 12), (18, 19), (-17, -8)),)},
    # C2, painted on with more life (the user, 2026-11-20: "C is perfect, just edit it", less blocky, more detail;
    # C itself kept as v16). The eye is ONE eye wrapping round the plate's front outer corner (the user: the first C2
    # painted the front and the side each as its own eye, two glints, lids starting over at the corner): its art is
    # one strip, the front face's columns (from the inner edge to the corner) running straight on into the outer
    # face's (from the corner back). One pupil across the corner, one glint sitting on the corner itself (so it shows from ahead,
    # from the side and between), a dark upper lid line and a lighter lower
    # lid running unbroken round the corner and rounding the eye's ends; the brow tapers at its outer edge.
    'painted_detail': {'box': ((6, 11), (12, 18), (-16, -8)),
                       'strip': ('ggddd' 'ddddddgg',
                                 'ggwkh' 'hkkwwwdg',
                                 'ggwkk' 'kkkwwwwg',
                                 'gglkk' 'kkkwwllg',
                                 'gggll' 'llllllgg'),
                       'extra': (((5, 11), (18, 19), (-17, -8)), ((11, 12), (18, 18.5), (-16, -8)))},
}
EYE_LOOK = 'painted_detail'        # approved by the user 2026-11-20 ("YES that's perfect", v17)


def eye_bones(s):
    """One eye's bone (s = 1 left, -1 right), untilted, its boxes and pixel art from EYE_LOOKS[EYE_LOOK]."""
    look = EYE_LOOKS[EYE_LOOK]
    (x0, x1), (y0, y1), (z0, z1) = look['box']
    xa, xb = sorted((s * x0, s * x1))
    if 'strip' in look:                    # one eye round the corner: the front face's columns, then the side's
        w = x1 - x0
        front, outer = [r[:w] for r in look['strip']], [r[w:] for r in look['strip']]
    else:
        front, outer = look['front'], look['outer']
    info = {'s': s, 'x_in': s * x0, 'y1': y1, 'z0': z0, 'front': front, 'outer': outer}
    eye = cube([xa, y0, z0], [xb, y1, z1], 'eye')
    eye['_eye'] = info
    c = [eye]
    for (ex0, ex1), (ey0, ey1), (ez0, ez1) in look['extra']:
        ea, eb = sorted((s * ex0, s * ex1))
        c.append(cube([ea, ey0, ez0], [eb, ey1, ez1], 'skin'))
    name = 'eye_left' if s > 0 else 'eye_right'
    return {'name': name, 'parent': 'head', 'pivot': [s * (x0 + x1) / 2, y0, (z0 + z1) / 2], 'cubes': c}


ART = {'g': 'skin', 'w': 'eye_white', 'k': 'pupil', 'h': 'glint', 'd': 'lid_dark', 'l': 'lid_light'}


def eye_colour(c, face_, x, y, z):
    """An eye box's face, pixel by pixel, from its pixel art: the front face's columns counted from the inner edge,
    the outer face's from the front edge; rows from the top."""
    e = c['_eye']
    outer = 'east' if e['s'] > 0 else 'west'
    if face_ == 'north':
        art, col = e['front'], int((x - e['x_in']) * e['s'])
    elif face_ == outer:
        art, col = e['outer'], int(z - e['z0'])
    else:
        return PLAIN['skin']
    row = int(e['y1'] - y)
    if 0 <= row < len(art) and 0 <= col < len(art[row]):
        return PLAIN[ART[art[row][col]]]
    return PLAIN['skin']


def face(cubes):
    """The eardrum behind and below each eye and the nostrils on the snout's upper sides (the eyes are their own
    bones, eye_bones)."""
    for s in (1, -1):
        ez = outline_z(EAR_AT)
        side = half_width_at(EAR_AT, EAR_HEIGHT)
        _box(cubes, s * (side - 0.2), s * (side + 0.15), EAR_HEIGHT - EAR_R, EAR_HEIGHT + EAR_R, ez - EAR_R, ez + EAR_R,
             'ear')
        nz, ny = outline_z(37.2), at(TOP, 37.2)
        _box(cubes, s * 2.0, s * 2.8, ny - 0.4, ny + 0.1, nz - 0.35, nz + 0.35, 'pupil')                  # nostril


def chain(prefix, parent, joints, profiles, part):
    """A bent limb: one round piece from each joint to the next, each its own bone turned to point at the next joint
    (built straight down from its joint and turned, as tools/model_parts.round_leg does), with a round ball at each
    joint so the bend shows no gap. Returns the bones, the last joint's place before the turns and the last turn."""
    bones = []
    place = list(joints[0])
    turn_before = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    for i, (a, b) in enumerate(zip(joints, joints[1:])):
        length = vlen(vsub(b, a))
        turn = aim((0, -1, 0), vsub(b, a))
        cubes = []
        limb(cubes, 1, place, place[1], place[1] - length, profiles[i], part, lead=0.5)
        ball(cubes, 1, place, profiles[i][0][1] + 0.2, part)
        name = f'{prefix}{i + 1}'
        bones.append({'name': name, 'parent': parent, 'pivot': [round(v, 3) for v in place],
                      'rotation': rot(mat_mul(transpose(turn_before), turn)), 'cubes': cubes})
        parent, turn_before = name, turn
        place = [place[0], place[1] - length, place[2]]
    return bones, place, turn_before


def world(x, y, out, s):
    """A joint from the outline's x, height and the distance out to one side (s = 1 left, -1 right)."""
    return [s * out, y, outline_z(x)]


def front_leg(s):
    name = 'left' if s > 0 else 'right'
    joints = [world(x, y, o, s) for x, y, o in ARM]
    bones, place, last = chain(f'arm_{name}_', 'body', joints, ARM_R, 'leg')
    # The hand: its own bone turned back square to the world, flat on the ground, four long plain fingers forward,
    # spread (no webbing on a frog's hand), knobbed at the tips.
    wx, wy, wz = place
    g = wy - joints[-1][1]                                     # the ground, in the hand's own unturned space
    hand = [cube([wx - 1.5, g, wz - 1.2], [wx + 1.5, g + 1.1, wz + 1.2], 'leg')]
    for off, ln in ((-1.6, 3.0), (-0.5, 3.6), (0.6, 3.4), (1.6, 2.6)):
        fx = wx + s * off
        hand.append(cube([fx - 0.45, g, wz - 1.2 - ln], [fx + 0.45, g + 0.8, wz - 1.0], 'leg'))
        hand.append(cube([fx - 0.55, g, wz - 1.4 - ln], [fx + 0.55, g + 0.9, wz - 0.6 - ln], 'toe'))
    bones.append({'name': f'hand_{name}', 'parent': bones[-1]['name'], 'pivot': [round(v, 3) for v in place],
                  'rotation': rot(transpose(last)), 'cubes': hand})
    return bones


def hind_leg(s):
    name = 'left' if s > 0 else 'right'
    joints = [world(x, y, o, s) for x, y, o in LEG]
    bones, place, last = chain(f'leg_{name}_', 'body', joints, LEG_R, 'leg')
    # The foot: flat, forward from the heel, webbed between five toes (one plate for now, wider at the front).
    hx, hy, hz = place
    g = hy - joints[-1][1]
    foot = [cube([hx - 1.6, g, hz - 5.0], [hx + 1.6, g + 1.2, hz + 1.0], 'leg'),
            cube([hx - 2.8, g, hz - 9.0], [hx + 2.8, g + 0.8, hz - 4.6], 'web')]
    for off, ln in ((-2.6, 2.0), (-1.3, 3.0), (0.0, 3.8), (1.3, 4.4), (2.6, 3.2)):
        fx = hx + s * off
        foot.append(cube([fx - 0.4, g, hz - 9.0 - ln], [fx + 0.4, g + 0.8, hz - 8.6], 'toe'))
    bones.append({'name': f'foot_{name}', 'parent': bones[-1]['name'], 'pivot': [round(v, 3) for v in place],
                  'rotation': rot(transpose(last)), 'cubes': foot})
    return bones


def model():
    cubes = {'body': [], 'head': [], 'jaw': [], 'throat': []}
    body_slices(cubes)
    face(cubes['head'])
    corner = outline_z(HEAD_FROM)
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0], 'cubes': []},
        {'name': 'body', 'parent': 'root', 'pivot': [0, 6.0, 0], 'cubes': cubes['body']},
        {'name': 'head', 'parent': 'body', 'pivot': [0, at(MOUTH, HEAD_FROM), corner], 'cubes': cubes['head']},
        {'name': 'jaw', 'parent': 'head', 'pivot': [0, at(MOUTH, HEAD_FROM), corner], 'cubes': cubes['jaw']},
        {'name': 'throat', 'parent': 'jaw', 'pivot': [0, THROAT_BELOW, corner], 'cubes': cubes['throat']},
    ]
    for s in (1, -1):
        bones.append(eye_bones(s))
    for s in (1, -1):
        bones += front_leg(s) + hind_leg(s)
    return bones


# --- plain colours (shape only; the pattern comes in the paint stage) --------------------------------------------------
PLAIN = {
    'skin': (92, 112, 52),        # olive green back
    'leg': (100, 116, 56),
    'jaw': (196, 190, 92),        # yellow-green lip and lower jaw
    'throat': (226, 206, 112),    # pale yellow throat
    'eye_white': (238, 236, 222),
    'pupil': (20, 18, 16),
    'glint': (255, 255, 255),     # the shine in the pupil
    'lid_dark': (58, 72, 32),     # the upper lid line under the brow
    'lid_light': (130, 148, 72),  # the lower lid
    'ear': (74, 88, 46),          # the eardrum, a shade darker
    'toe': (176, 168, 110),
    'web': (110, 122, 64),
}


def plain_colour(c, face_, x, y, z):
    if '_eye' in c:
        return eye_colour(c, face_, x, y, z)
    col = PLAIN[c['_part']]
    if c['_part'] in ('jaw', 'skin') and c['_bone'] in ('head', 'jaw'):
        mouth = at(MOUTH, max(HEAD_FROM, LENGTH / 2 - z))
        if abs(y - mouth) < 0.45:
            return (58, 46, 28)                                         # the dark mouth line round the face
    if c['_part'] == 'skin' and c['_bone'] == 'body' and y < 6.0:      # yellow low on the flanks
        f = max(0.0, min(1.0, (6.0 - y) / 3.0))
        col = tuple(a + (b - a) * f for a, b in zip(col, (196, 182, 70)))
    return col


# --- the paint (the workshop's stage 4, 2026-11-20): read off the user's side photo, Minecraft style -------------------
# Every texture pixel (one a model pixel) is coloured from where it lies on the model, in its own bone's unturned
# space, so a mark carries on across the overlapping boxes of the sliced body. A little noise on every pixel, as
# Minecraft's own textures have. The eyes keep their approved pixel art (eye_colour). All colours my picks from the
# photo; fun first.
PAINT = {
    'back': (66, 88, 34),         # dark olive green
    'head': (66, 92, 34),         # the head barely greener than the back: its top is one flat face that catches
    'snout': (66, 92, 34),        # the light, so it reads lighter than the stepped back in the same colour
    'dash': (30, 40, 18),         # the dark dashes along the back and flanks
    'wart': (118, 128, 58),       # pale warts
    'flank': (176, 168, 58),      # yellow-green low on the sides
    'yellow': (220, 190, 60),     # the yellow edge along the belly
    'orange': (232, 128, 36),     # behind the arm and in the groin
    'belly': (232, 214, 140),     # cream
    'lip': (196, 196, 72),        # the yellow line along the upper lip
    'mouth': (52, 40, 24),
    'throat': (226, 204, 96),     # the yellow throat
    'blotch': (150, 156, 66),     # its olive mottling
    'leg': (74, 96, 38),
    'bar': (48, 62, 26),          # dark bars on the legs
    'toe': (196, 182, 112),
    'web': (128, 136, 70),
    'ear': (120, 104, 62),        # the eardrum, tan
    'nostril': (24, 20, 14),
}


def _hash(*v):
    """A steady random number 0 to 1 for whole-number coordinates (the same pixel always gets the same)."""
    h = 2166136261
    for n in v:
        h = ((h ^ (int(n) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 0x5bd1e995) & 0xFFFFFFFF
    return (h ^ (h >> 15)) / 0xFFFFFFFF


def _noise(col, *v, amount=7):
    d = (_hash(*v, 99) - 0.5) * 2 * amount
    return tuple(max(0, min(255, c + d)) for c in col)


def _dash(along, across, chance, seed):
    """True where a dark dash lies: the skin is cut into cells 5 along and 3 across, and in some cells a dash 3 to 5
    long and 1 wide sits at a steady random place, stepping a pixel up or down along the way in some (the photo's dark
    wavy marks running front to back)."""
    ca, cc = along // 5, across // 3
    if _hash(ca, cc, seed) > chance:
        return False
    a0 = ca * 5 + int(_hash(ca, cc, seed, 1) * 2)
    length = 3 + int(_hash(ca, cc, seed, 3) * 3)
    c0 = cc * 3 + int(_hash(ca, cc, seed, 2) * 3)
    if not a0 <= along < a0 + length:
        return False
    wave = 1 if _hash(ca, cc, seed, 4) < 0.4 and along >= a0 + 2 else 0
    return across == c0 + wave


def _top_skin(along):
    """The back's colour blending into the head's over the shoulders (the outline's x 22 to 34), and how likely a
    dark mark is there (fading out onto the head): one rule for the body, the head and the brows, so there is no edge
    where the head's bone begins (the user, 2026-11-20: the change from dark to light green looked odd)."""
    from model_parts import smooth
    f = smooth((along - 22) / 12)
    base = tuple(a + (b - a) * f for a, b in zip(PAINT['back'], PAINT['head']))
    return base, 0.8 * (1 - smooth((along - 24) / 10))


def paint_colour(c, face_, x, y, z):
    part, bone = c['_part'], c['_bone']
    ix, iy, iz = int(x // 1), int(y // 1), int(z // 1)
    along = int((LENGTH / 2 - z) // 1)                         # the outline's x: rump 0 to snout 40
    across = ix if face_ in ('up', 'down') else iy             # the second direction on the skin
    if '_eye' in c:                                            # the approved eye art; its skin in the painted skin
        col = eye_colour(c, face_, x, y, z)
        if col != PLAIN['skin']:
            return col
        base, _ = _top_skin(along)
        return _noise(base, ix, iy, iz, amount=5)
    if part == 'pupil':                                        # the nostrils
        return PAINT['nostril']
    if part == 'ear':
        return _noise(PAINT['ear'], ix, iy, iz, amount=5)
    if part in ('toe',):
        return _noise(PAINT['toe'], ix, iy, iz, amount=6)
    if part == 'web':
        return _noise(PAINT['web'], ix, iy, iz)
    if part == 'leg':
        # dark bars across the leg along its own length (each leg piece is built straight down in its own space)
        if bone.startswith(('arm', 'leg')) and int(y // 1.5) % 3 == 0 and _hash(ix, int(y // 1.5), iz) < 0.8:
            return _noise(PAINT['bar'], ix, iy, iz, amount=5)
        if _hash(ix, iy, iz, 7) < 0.02:
            return PAINT['wart']
        return _noise(PAINT['leg'], ix, iy, iz)
    if bone.startswith('eye'):                                 # the brow over each eye: the head's skin
        base, chance = _top_skin(along)
        if _dash(along, across, chance, 31):
            return _noise(PAINT['dash'], ix, iy, iz, amount=4)
        return _noise(base, ix, iy, iz, amount=5)
    if bone in ('head', 'jaw', 'throat'):
        # The mouth follows each slice's own jaw edge (the boxes step down slice by slice; a smooth painted line
        # crossed the steps and showed twice): the dark line along the top of the jaw's boxes, the yellow lip along
        # the bottom of the head's lowest boxes.
        (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
        mid = LENGTH / 2 - (oz + sz / 2)
        mouth = at(MOUTH, mid)
        if bone == 'jaw' and abs(oy + sy - mouth) < 0.05 and y > oy + sy - 0.6:
            return PAINT['mouth']
        if bone == 'head' and abs(oy - mouth) < 0.05 and y < oy + 0.9:
            return _noise(PAINT['lip'], ix, iy, iz, amount=5)
        if bone in ('jaw', 'throat'):                          # the jaw and throat: yellow, mottled with olive
            if face_ == 'down' and y < 4:
                return _noise(PAINT['belly'], ix, iy, iz, amount=5)
            blot = _hash(ix // 2, iy // 2, iz // 2, 3) < 0.28
            return _noise(PAINT['blotch'] if blot else PAINT['throat'], ix, iy, iz, amount=6)
        base, chance = _top_skin(along)
        if _dash(along, across, chance, 31):
            return _noise(PAINT['dash'], ix, iy, iz, amount=4)
        return _noise(base, ix, iy, iz)
    # the body
    if face_ == 'down' or y < 1.2:
        return _noise(PAINT['belly'], ix, iy, iz, amount=5)
    if y < 3.0:                                                # the yellow edge, orange behind the arm and in the groin
        if 24 <= along <= 28 or 2 <= along <= 7:
            return _noise(PAINT['orange'], ix, iy, iz, amount=8)
        return _noise(PAINT['yellow'], ix, iy, iz, amount=6)
    if y < 5.5:
        if 24 <= along <= 28 and y < 4.5:
            return _noise(PAINT['orange'], ix, iy, iz, amount=8)
        if _dash(along, across, 0.5, 21):
            return _noise(PAINT['dash'], ix, iy, iz, amount=4)
        return _noise(PAINT['flank'], ix, iy, iz)
    base, chance = _top_skin(along)
    if _dash(along, across, chance, 31):
        return _noise(PAINT['dash'], ix, iy, iz, amount=4)
    if _hash(ix, iy, iz, 5) < 0.025:
        return PAINT['wart']
    return _noise(base, ix, iy, iz)


LOOKS = {'painted': paint_colour, 'plain': plain_colour}
GAME_LOOK = 'painted'


WORKSHOP_SHAPE = [
    ('ROUNDNESS', 'Cross-section squareness (2 round, 4 boxy)', 2.0, 4.0, 0.1),
    ('FLAT_BELLY', 'Flat belly (0 round, 1 flat)', 0.0, 0.9, 0.05),
]
WORKSHOP_PARTS = {'head': ['head'], 'eyes': ['eye_left', 'eye_right'], 'body': ['body'], 'front legs': ['arm_left_1', 'arm_right_1'],
                  'hind legs': ['leg_left_1', 'leg_right_1']}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), 1, LOOKS[GAME_LOOK])
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
