"""The bullfrog tadpole's model maker (docs/plans/prey-fish-and-frogs.md, step 4; the model workshop, stage 2: the
plain 3D shape, 2026-11-20). Built from its outline (references/outlines/tadpole.json) and Claude's design sheet
(references/inspiration/tadpole.png, tools/young_sheets.py): made 18 model pixels long, drawn in the game at 0.15
blocks.

    python tools/workshop.py snapshot tadpole --note "..."   keep this build as the next version
    python tools/workshop.py versions tadpole                the versions page with Compare against the sheet

Units are model pixels (16 a block); the head to -Z, up +Y, left +X. The outline's x (tail tip 0 to snout 18) is
z = 9 - x here, and its y (the lower fin's edge 0, the upper fin's top 7) is kept as y.

The shape (box budget 30, a school of 8 to 12): the body is one-pixel slices from the tail's root to the snout, each
two boxes (a tall narrow core and a wide low flank), so it is a flat oval from the front and from above, wider than
tall; the eyes and the mouth underneath are painted. The tail is three bones (tail_1 at the root to tail_3 at the
tip), so the wriggle can travel down it, each a piece of the muscle with its thin fins above and below. The hind legs
(day 3) are their own bones at the tail's root, small buds trailing back along it; LEGS 0 leaves their boxes out (the
game keeps the bones and hides them until day 3).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import cube, curve, paint_faces, png   # noqa: E402

LENGTH = 18.0
# (x, top, belly, half width) along the body, eased between: the tail's root, the widest middle, the rounded snout.
# Read off the outline (body rear y 2-6 half 1.5, body 1-6 half 2.5, snout 2-5 half 1.5), rounded.
PROFILE = ((11.0, 5.4, 2.2, 1.6), (12.0, 5.9, 1.4, 2.2), (13.5, 6.0, 1.0, 2.6), (15.0, 6.0, 1.0, 2.6),
           (16.5, 5.6, 1.4, 2.3), (17.5, 5.0, 2.0, 1.7), (18.0, 4.6, 2.4, 1.3))
CORE_WIDTH = 0.55        # the tall box's share of the width
FLANK_DEPTH = 0.6        # the wide box's share of the depth
BODY_FROM = 11.0
EYE = (15.0, 16.0, 4.0, 5.0)        # the eye on the flank: from x, to x, from y, to y
LEGS = 0.0               # 1: the day-3 hind legs shown


def z_of(x):
    return LENGTH / 2 - x


def at(x):
    xs = [p[0] for p in PROFILE]
    t = (x - xs[0]) / (xs[-1] - xs[0])
    return tuple(curve([((p[0] - xs[0]) / (xs[-1] - xs[0]), p[k]) for p in PROFILE], t) for k in (1, 2, 3))


def box(x0, x1, half, y0, y1, part, across=0.0):
    z0, z1 = sorted((z_of(x0), z_of(x1)))
    return cube([across - half, y0, z0], [across + half, y1, z1], part)


def q(v):
    return round(v * 4) / 4


def body(out):
    x = BODY_FROM
    while x < LENGTH - 1e-6:
        x1 = min(LENGTH, x + 1.0)
        top, belly, hw = at((x + x1) / 2)
        mid, half_d = (top + belly) / 2, (top - belly) / 2
        out += [box(x, x1, q(hw * CORE_WIDTH), q(belly), q(top), 'body'),
                box(x, x1, q(hw), q(mid - half_d * FLANK_DEPTH), q(mid + half_d * FLANK_DEPTH), 'flank')]
        x = x1


def tail(b):
    # tail_1, the root: the thick muscle in two steps, the fins starting here
    b['tail_1'] += [box(9.5, 11.0, 1.25, 2.0, 5.0, 'muscle'), box(8.0, 9.5, 0.9, 2.4, 4.6, 'muscle'),
                    box(8.0, 11.0, 0.25, 4.5, 7.0, 'fin'), box(8.0, 11.0, 0.25, 0.0, 2.5, 'fin')]
    # tail_2, the middle: thinner muscle, the fins at their deepest
    b['tail_2'] += [box(4.0, 8.0, 0.6, 2.9, 4.1, 'muscle'),
                    box(4.0, 8.0, 0.25, 4.0, 7.0, 'fin'), box(4.0, 8.0, 0.25, 0.5, 3.0, 'fin')]
    # tail_3, the tip: the muscle thinning to the point, the fins narrowing to it
    b['tail_3'] += [box(0.0, 4.0, 0.4, 3.0, 4.0, 'muscle'),
                    box(2.0, 4.0, 0.25, 3.9, 6.0, 'fin'), box(1.0, 2.0, 0.25, 3.9, 5.0, 'fin'),
                    box(2.0, 4.0, 0.25, 1.5, 3.1, 'fin')]


def legs(b):
    """The day-3 hind legs: small buds at the tail's root, trailing back along it, the foot just showing its toes."""
    for s, name in ((1, 'leg_left'), (-1, 'leg_right')):
        b[name] += [box(8.75, 10.75, 0.4, 1.0, 2.0, 'leg', across=s * 1.5),
                    box(7.5, 8.75, 0.6, 0.6, 1.4, 'leg', across=s * 1.9)]


def model():
    names = ('body', 'tail_1', 'tail_2', 'tail_3', 'leg_left', 'leg_right')
    b = {n: [] for n in names}
    body(b['body'])
    tail(b)
    if LEGS >= 0.5:
        legs(b)
    mid = 3.5
    return [
        {'name': 'root', 'pivot': [0, 0, 0], 'cubes': []},
        {'name': 'body', 'parent': 'root', 'pivot': [0, mid, z_of(14.0)], 'cubes': b['body']},
        {'name': 'tail_1', 'parent': 'body', 'pivot': [0, mid, z_of(11.0)], 'cubes': b['tail_1']},
        {'name': 'tail_2', 'parent': 'tail_1', 'pivot': [0, mid, z_of(8.0)], 'cubes': b['tail_2']},
        {'name': 'tail_3', 'parent': 'tail_2', 'pivot': [0, mid, z_of(4.0)], 'cubes': b['tail_3']},
        {'name': 'leg_left', 'parent': 'body', 'pivot': [1.5, 1.5, z_of(10.75)], 'cubes': b['leg_left']},
        {'name': 'leg_right', 'parent': 'body', 'pivot': [-1.5, 1.5, z_of(10.75)], 'cubes': b['leg_right']},
    ]


# --- plain colours (the sheet's; the paint stage comes later) -------------------------------------------------------
PLAIN = {
    'body': (50, 50, 40), 'belly': (104, 106, 96), 'eye': (10, 10, 10), 'ring': (176, 150, 80),
    'beak': (28, 26, 22), 'lip': (150, 140, 116), 'muscle': (64, 62, 50), 'fin': (134, 142, 132), 'leg': (70, 70, 52),
}


def plain_colour(c, face, x, y, z):
    part = c['_part']
    ox = LENGTH / 2 - z
    if part in ('fin', 'muscle', 'leg'):
        return PLAIN[part]
    if face in ('east', 'west') and EYE[0] <= ox < EYE[1] and EYE[2] <= y < EYE[3]:
        return PLAIN['eye'] if ox < EYE[0] + 0.5 else PLAIN['ring']
    if face == 'down':
        if ox > LENGTH - 1.5 and abs(x) < 1.0:
            return PLAIN['beak'] if abs(x) < 0.5 else PLAIN['lip']      # the mouth underneath at the front
        return PLAIN['belly']
    if y < 1.75:
        return PLAIN['belly']
    return PLAIN['body']


LOOKS = {'plain': plain_colour}
WORKSHOP_LOOK = 'plain'
WORKSHOP_SHAPE = [
    ('CORE_WIDTH', 'Back and belly ridge width (share of the full width)', 0.3, 0.9, 0.05),
    ('FLANK_DEPTH', 'Round flank depth (share of the full depth)', 0.4, 0.9, 0.05),
    ('LEGS', 'Day-3 hind legs (0 hidden, 1 shown)', 0.0, 1.0, 1.0),
]
WORKSHOP_PARTS = {'body': ['body'], 'tail root': ['tail_1'], 'tail middle': ['tail_2'], 'tail tip': ['tail_3'],
                  'hind legs': ['leg_left', 'leg_right']}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), 2, LOOKS[WORKSHOP_LOOK])
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
    LEGS = 1.0
    print('with legs:', sum(len(b['cubes']) for b in model()), 'boxes')
