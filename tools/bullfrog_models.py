"""The African bullfrog's model maker (docs/plans/prey-fish-and-frogs.md; the model workshop, stage 2: the plain 3D
shape, 2026-11-20). Built from the outline the user saw (references/outlines/bullfrog.json) and the user's picture
(references/inspiration/bullfrog.png): made 40 model pixels long (the sheet's 2.5 blocks), drawn in the game at 0.8
blocks for a male, 0.65 for a female.

    python tools/workshop.py snapshot bullfrog --note "..."   keep this build as the next version
    python tools/workshop.py versions bullfrog                the versions page with Compare against the picture

Units are model pixels (16 a block); the head to -Z, up +Y, left +X, the ground at y 0 and the middle line at x 0.
The outline's x (back end 0 to snout 40) is z = 20 - x here.

The shape: the body is cut into 2-pixel slices from the rump to the snout, each slice a stack of 2-pixel layers whose
width follows a rounded (superellipse) cross-section, so the frog is a soft dome with a flat belly, not a box. Its top,
belly and width along the length follow the outline's side and top views, eased between control points. The head is its
own bone (with the upper lip) and the lower jaw another below the mouth line, with the throat its own bone under it, so
the gape (the ambush lunge, the barking bite) and the swelling throat (calling) can move later. The legs sit folded the
way a real bullfrog sits: a short upper arm straight down to a hand of four plain fingers turned in; the big thigh lying
along the flank from the hip forward to the knee, the shin folded back under it to the heel, and the hind foot forward
again flat on the ground (webbed, as one plate for now: the user's detailed webbed foot comes later).
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import cube, curve, limb, ball, paint_faces, png   # noqa: E402

# --- shape numbers (model pixels; along the outline's x, back end 0 to snout 40) --------------------------------------
LENGTH = 40.0
TOP = ((0, 11.0), (3, 16.0), (8, 21.0), (14, 24.0), (22, 25.0), (30, 25.0), (34, 24.5), (37, 23.0), (39, 21.0),
       (40, 19.0))       # the head as high and broad as the back, rounding off only at the snout (the user's sheet)
BELLY = ((0, 7.0), (3, 5.0), (8, 3.5), (20, 3.0), (28, 3.5), (32, 4.5), (36, 6.0), (39, 8.0), (40, 9.5))   # a tall blunt face
HALF_WIDTH = ((0, 6.0), (3, 10.0), (8, 13.0), (16, 14.0), (26, 14.0), (30, 13.5), (34, 12.5), (37, 11.0), (40, 8.5))
ROUNDNESS = 2.6          # the cross-section's superellipse power: 2 an ellipse, higher squarer (a broad flat frog)
FLAT_BELLY = 0.55        # how much of the lower half keeps its full width (0 a round belly, 1 a flat box bottom)
HEAD_FROM = 28.0         # where the head bone starts (outline x)
MOUTH = ((28, 12.5), (34, 13.0), (40, 13.5))   # the mouth line's height along the head
THROAT_BELOW = 7.0       # the throat (its own bone, for the swelling call) is the jaw below this height
EYE_AT, EYE_HEIGHT, EYE_APART, EYE_R = 33.5, 24.0, 7.5, 2.2
SLICE = 2.0
LAYER = 2.0


def outline_z(x):
    return LENGTH / 2 - x


def at(points, x):
    """The value at x along the length, eased between control points (x, value)."""
    t_points = [(p / LENGTH, v) for p, v in points]
    return curve(t_points, x / LENGTH)


def half_width_at(x, y, top, belly):
    """The body's half width at height y in the slice at x: a superellipse, flatter at the bottom."""
    mid = (top + belly) / 2
    half_h = (top - belly) / 2
    u = (y - mid) / half_h
    if u < 0:
        u = max(0.0, (abs(u) - FLAT_BELLY) / (1 - FLAT_BELLY)) * -1
    k = max(0.0, 1 - abs(u) ** ROUNDNESS) ** (1 / ROUNDNESS)
    return at(HALF_WIDTH, x) * k


def body_slices(cubes_by_bone):
    """The body, head, jaw and throat as slices of layers (each layer one box)."""
    n = round(LENGTH / SLICE)
    for i in range(n):
        x0, x1 = i * SLICE, (i + 1) * SLICE
        xm = (x0 + x1) / 2
        top, belly = at(TOP, xm), at(BELLY, xm)
        mouth = at(MOUTH, xm) if xm >= HEAD_FROM else None
        y = belly
        while y < top - 0.01:
            y1 = min(top, y + LAYER)
            if mouth is not None and y < mouth < y1:      # a layer split at the mouth line, so jaw and head part cleanly
                y1 = mouth
            ym = (y + y1) / 2
            hw = round(half_width_at(xm, ym, top, belly) * 4) / 4
            if hw >= 0.5:
                part = 'belly' if ym < belly + 2.5 and xm < HEAD_FROM else 'skin'
                if mouth is None:
                    bone = 'body'
                elif ym >= mouth:
                    bone = 'head'
                elif ym >= THROAT_BELOW:
                    bone, part = 'jaw', ('lip' if y1 >= mouth - 0.01 else 'skin')
                else:
                    bone, part = 'throat', 'throat'
                z0, z1 = sorted((outline_z(x0), outline_z(x1)))
                cubes_by_bone[bone].append(cube([-hw, y, z0], [hw, y1, z1], part))
            y = y1


def eyes(cubes):
    """Each eye a skin bump high on the head, rising about a pixel over it, with the golden eye on its outer side
    and a dark pupil across it (real bullfrog eyes sit high and look out to the sides and up)."""
    z = outline_z(EYE_AT)
    for side in (1, -1):
        ball(cubes, 0, [side * EYE_APART, EYE_HEIGHT, z], EYE_R, 'skin')
        # the golden eye wraps the bump's outer front corner, so it looks out to the side and forward (seen from the
        # side and from the front, as on the sheet), the dark pupil in its middle on both faces
        xin, xout = sorted((side * (EYE_APART - EYE_R * 0.2), side * (EYE_APART + EYE_R * 1.05)))
        front, back = z - EYE_R * 1.05, z + EYE_R * 0.6
        cubes.append(cube([xin, EYE_HEIGHT - 1.5, front], [xout, EYE_HEIGHT + 1.5, back], 'eye'))
        px0, px1 = sorted((side * (EYE_APART + EYE_R * 0.3), side * (EYE_APART + EYE_R * 1.1)))
        cubes.append(cube([px0, EYE_HEIGHT - 0.6, front - 0.1], [px1, EYE_HEIGHT + 0.7, z - 0.2], 'pupil'))


def front_leg(side):
    """A short round upper arm down from the shoulder, the hand flat on the ground with four plain fingers turned in."""
    s = side
    sx, sz = s * 12.5, outline_z(31.5)
    arm, hand = [], []
    sx, sz = s * 13.0, outline_z(32.5)                      # at the chest's sides, below the head, as the sheet sits
    limb(arm, 1, [sx, 0, sz], 9.0, 1.2, ((0, 2.8), (0.6, 2.3), (1, 2.0)), 'leg')
    hz = sz - 1.0
    sx += s * 1.5                                            # the hand turned out a little
    hand.append(cube([sx - 2.2, 0, hz - 2.0], [sx + 2.2, 1.5, hz + 2.0], 'leg'))
    for k, (dx, dz, ln) in enumerate(((-1.6, -1, 3.0), (-0.4, -1, 3.6), (0.8, -1, 3.2), (2.0, -0.4, 2.2))):
        fx = sx - s * dx                                     # fingers fan toward the middle line and forward
        tip = hz - 2.0 - ln
        hand.append(cube([fx - 0.5, 0, tip], [fx + 0.5, 1.0, hz - 1.0], 'toe'))
    name = 'left' if s > 0 else 'right'
    return [
        {'name': f'arm_{name}', 'parent': 'body', 'pivot': [sx, 9.0, sz], 'cubes': arm},
        {'name': f'hand_{name}', 'parent': f'arm_{name}', 'pivot': [sx, 1.5, sz], 'cubes': hand},
    ]


def hind_leg(side):
    """The folded hind leg: the thigh along the flank from the hip forward to the knee, the shin folded back under it
    to the heel, the webbed foot forward again flat on the ground."""
    s = side
    name = 'left' if s > 0 else 'right'
    hip_z, knee_z, heel_z = outline_z(4.0), outline_z(17.0), outline_z(6.0)
    thigh, shin, foot = [], [], []
    limb(thigh, 2, [s * 13.5, 8.5, 0], hip_z, knee_z, ((0, 4.4), (0.45, 5.2), (1, 3.6)), 'leg')   # big haunches out at the sides
    limb(shin, 2, [s * 16.0, 4.0, 0], knee_z + 1.5, heel_z, ((0, 2.9), (0.5, 2.7), (1, 2.1)), 'leg')
    fz0, fz1 = heel_z, outline_z(17.5)                       # the foot reaches forward from the heel
    foot.append(cube([s * 15.5 - 2.5, 0, fz1], [s * 15.5 + 2.5, 1.2, fz0], 'web'))
    for k, dx in enumerate((-2.2, -1.1, 0.0, 1.1, 2.2)):     # five long toes past the web
        ln = (2.0, 3.0, 3.6, 3.2, 2.4)[k]
        fx = s * (15.5 + dx)
        foot.append(cube([fx - 0.45, 0, fz1 - ln], [fx + 0.45, 1.0, fz1 + 0.5], 'toe'))
    return [
        {'name': f'thigh_{name}', 'parent': 'body', 'pivot': [s * 13.5, 8.5, hip_z], 'cubes': thigh},
        {'name': f'shin_{name}', 'parent': f'thigh_{name}', 'pivot': [s * 16.0, 4.0, knee_z], 'cubes': shin},
        {'name': f'foot_{name}', 'parent': f'shin_{name}', 'pivot': [s * 15.5, 1.0, heel_z], 'cubes': foot},
    ]


def model():
    cubes = {'body': [], 'head': [], 'jaw': [], 'throat': []}
    body_slices(cubes)
    eyes(cubes['head'])
    neck_z = outline_z(HEAD_FROM)
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0], 'cubes': []},
        {'name': 'body', 'parent': 'root', 'pivot': [0, 8.0, 0], 'cubes': cubes['body']},
        {'name': 'head', 'parent': 'body', 'pivot': [0, 13.0, neck_z], 'cubes': cubes['head']},
        {'name': 'jaw', 'parent': 'head', 'pivot': [0, 12.0, neck_z], 'cubes': cubes['jaw']},
        {'name': 'throat', 'parent': 'jaw', 'pivot': [0, 7.0, neck_z], 'cubes': cubes['throat']},
    ]
    for s in (1, -1):
        bones += front_leg(s) + hind_leg(s)
    return bones


# --- colours ---------------------------------------------------------------------------------------------------------
PLAIN = {
    'skin': (104, 116, 58),       # olive green
    'leg': (98, 110, 54),
    'belly': (224, 206, 150),     # cream
    'throat': (230, 214, 160),
    'lip': (150, 150, 86),
    'eye': (200, 160, 50),        # golden
    'pupil': (20, 16, 12),
    'toe': (196, 184, 132),       # pale toes
    'web': (120, 128, 70),
}


def plain_colour(c, face, x, y, z):
    col = PLAIN[c['_part']]
    if c['_part'] in ('skin', 'lip') and c['_bone'] in ('head', 'jaw'):
        ox = LENGTH / 2 - z                                   # back along the outline's x
        mouth = at(MOUTH, ox)
        if abs(y - mouth) < 0.55 and ox > HEAD_FROM + 1:
            return (70, 52, 30)                               # the dark mouth line round the face
        if y < mouth and ox > HEAD_FROM + 1:
            return PLAIN['throat']                            # the cream chin and jaw
    if c['_part'] == 'skin' and y < 9.0:                      # the orange-yellow flush low on the sides
        f = max(0.0, min(1.0, (9.0 - y) / 3.0))
        col = tuple(a + (b - a) * f * 0.6 for a, b in zip(col, (214, 160, 52)))
    return col


WORKSHOP_SHAPE = [
    ('ROUNDNESS', 'Cross-section squareness (2 round, 4 boxy)', 2.0, 4.0, 0.1),
    ('FLAT_BELLY', 'Flat belly (0 round, 1 flat)', 0.0, 0.9, 0.05),
    ('HEAD_FROM', 'Where the head starts (model pixels from the back)', 24, 32, 0.5),
    ('EYE_HEIGHT', 'Eye height (model pixels)', 20, 26, 0.25),
    ('EYE_APART', 'Eyes apart, each from the middle (model pixels)', 5, 10, 0.25),
    ('EYE_R', 'Eye size (model pixels)', 1.8, 3.4, 0.1),
]
WORKSHOP_PARTS = {'head': ['head'], 'body': ['body'], 'front legs': ['arm_left', 'arm_right'],
                  'hind legs': ['thigh_left', 'thigh_right']}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), 1, plain_colour)
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
