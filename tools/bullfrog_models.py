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
- **Eyes high and close together on top** (the front photo: their middles 0.38 of the head's width apart), big and
  bulging, a bronze eye with a dark bar of a pupil, a lid ridge over each; a round eardrum behind each eye; two small
  nostrils near the tip.
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
# The eyes on the upper sides of the head looking out (the user, 2026-11-20, from the side photo: the front photo's
# forward look was its angle): measured on the side photo, the eye 3 pixels long and 2.5 tall, its middle 16.3 high
# and 7 back from the snout; EYE_APART is how far out its middle sits, EYE_R half its height.
EYE_AT, EYE_HEIGHT, EYE_APART, EYE_R = 33.0, 16.3, 7.6, 1.4
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


def face(cubes):
    """The eyes on the upper sides of the head, each a round eye facing out (a pale iris, a wide dark pupil), in a
    swelling of the head's side, under a knobbly brow crest rising over the head's top line; the eardrum behind and
    below each eye; the nostrils on the upper sides of the snout."""
    z = outline_z(EYE_AT)
    y = EYE_HEIGHT
    for s in (1, -1):
        surface = half_width_at(EYE_AT, y)                                     # the head's side at the eye
        out = EYE_APART + EYE_R * 0.9                                          # the eye's outer face
        _box(cubes, s * (surface - 1.6), s * (out - 0.3), y - 1.9, y + 1.7, z - 2.0, z + 2.0, 'skin')   # the swelling
        _box(cubes, s * (out - 1.0), s * out, y - EYE_R * 0.8, y + EYE_R * 0.8, z - 1.7, z + 1.7, 'eye')  # the eye,
        _box(cubes, s * (out - 1.0), s * out, y - EYE_R, y + EYE_R, z - 1.2, z + 1.2, 'eye')             # rounded
        _box(cubes, s * out, s * (out + 0.1), y - 0.4, y + 0.45, z - 1.05, z + 1.05, 'pupil')           # wide pupil
        # the brow: a low lid over the eye's top and a small knob on it, a pixel over the head's top line at most
        _box(cubes, s * (EYE_APART - 1.2), s * (out + 0.05), y + EYE_R * 0.85, y + EYE_R + 0.9, z - 2.0, z + 1.0,
             'skin')
        _box(cubes, s * (EYE_APART - 0.6), s * (out - 0.6), y + EYE_R + 0.9, y + EYE_R + 1.5, z - 1.6, z + 0.2, 'skin')
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
        bones += front_leg(s) + hind_leg(s)
    return bones


# --- plain colours (shape only; the pattern comes in the paint stage) --------------------------------------------------
PLAIN = {
    'skin': (92, 112, 52),        # olive green back
    'leg': (100, 116, 56),
    'jaw': (196, 190, 92),        # yellow-green lip and lower jaw
    'throat': (226, 206, 112),    # pale yellow throat
    'eye': (226, 214, 180),       # a pale iris (the side photo)
    'pupil': (16, 14, 12),
    'ear': (74, 88, 46),          # the eardrum, a shade darker
    'toe': (176, 168, 110),
    'web': (110, 122, 64),
}


def plain_colour(c, face_, x, y, z):
    col = PLAIN[c['_part']]
    if c['_part'] in ('jaw', 'skin') and c['_bone'] in ('head', 'jaw'):
        mouth = at(MOUTH, max(HEAD_FROM, LENGTH / 2 - z))
        if abs(y - mouth) < 0.45:
            return (58, 46, 28)                                         # the dark mouth line round the face
    if c['_part'] == 'skin' and c['_bone'] == 'body' and y < 6.0:      # yellow low on the flanks
        f = max(0.0, min(1.0, (6.0 - y) / 3.0))
        col = tuple(a + (b - a) * f for a, b in zip(col, (196, 182, 70)))
    return col


WORKSHOP_SHAPE = [
    ('ROUNDNESS', 'Cross-section squareness (2 round, 4 boxy)', 2.0, 4.0, 0.1),
    ('FLAT_BELLY', 'Flat belly (0 round, 1 flat)', 0.0, 0.9, 0.05),
    ('EYE_HEIGHT', 'Eye height (model pixels)', 14, 18, 0.25),
    ('EYE_APART', 'Eye out from the middle (model pixels)', 5.5, 9, 0.25),
    ('EYE_R', 'Eye size, half its height (model pixels)', 0.9, 1.8, 0.05),
]
WORKSHOP_PARTS = {'head': ['head'], 'body': ['body'], 'front legs': ['arm_left_1', 'arm_right_1'],
                  'hind legs': ['leg_left_1', 'leg_right_1']}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), 1, plain_colour)
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
