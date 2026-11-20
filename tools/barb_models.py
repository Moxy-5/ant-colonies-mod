"""The river barb's model maker (docs/plans/prey-fish-and-frogs.md; the model workshop, stage 2: the plain 3D shape,
2026-11-20). Built from the outline the user approved ("the fish looks perfect", references/outlines/barb.json) and
Claude's design sheet (references/inspiration/barb.png, tools/barb_sheet.py): made 30 model pixels long, drawn in the
game at 0.25 blocks.

    python tools/workshop.py snapshot barb --note "..."   keep this build as the next version
    python tools/workshop.py versions barb                the versions page with Compare against the sheet

Units are model pixels (16 a block); the head to -Z, up +Y, left +X. The outline's x (tail tip 0 to snout 30) is
z = 15 - x here, and its y (the lowest fin 0, the back fin's tip 12) is kept as y.

The shape (box budget under 40, as 40 barbs can be near a player): the body is 2-pixel slices from the tail root to
the snout, each slice two boxes, a tall narrow one (the full depth) and a short wide one (the flank), so seen from the
front the fish is a rounded spindle, not a brick. Its top, belly and width along the length are eased through points
read off the outline's parts (deepest a third of the way back from the snout, as the sheet). One box
per side makes each eye. The tail is two thin pieces (the stalk, then the fin with
its base and two lobes stepping out into a deep fork), so the swim can bend it. The back fin and the two breast fins
are their own bones (to fold and flutter); the belly fins and the fin under the tail sit on the body.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import cube, curve, paint_faces, png   # noqa: E402

# --- shape numbers (model pixels; along the outline's x, tail tip 0 to snout 30) -------------------------------------
LENGTH = 30.0
# (x, top, belly, half width), eased between: the tail root's narrow waist, the body deepest at x 20 (a third back
# from the snout), the head a little shallower, the snout blunt. Read off the outline's parts (tail root y 4-7, body
# rear 2-9, body 1-10, head 2-9, snout 4-7; half widths 0.5, 1.5, 2.5, 2.0, 1.0).
# The head's top and chin follow the sheet's steps (deep to x 25, rising only at the blunt snout).
PROFILE = ((7, 6.9, 4.1, 0.5), (9, 7.0, 4.0, 0.6), (11.5, 8.0, 3.0, 1.0), (14, 9.0, 2.0, 1.5), (17, 9.8, 1.2, 2.2),
           (20, 10.0, 1.0, 2.5), (23, 9.6, 1.1, 2.4), (25.5, 9.0, 1.8, 2.1), (27.5, 8.0, 2.8, 1.6),
           (29, 7.0, 3.6, 1.1), (30, 6.2, 4.0, 0.8))
CORE_WIDTH = 0.55        # the tall box's share of the width (the back and belly ridges)
FLANK_DEPTH = 0.62       # the wide box's share of the depth (the round flank)
SLICE = 2.0
BODY_FROM, HEAD_FROM, TAIL_ROOT = 11.0, 23.0, 7.0    # the body bone from x 11, the head bone from 23, tail fin before 7
EYE_AT, EYE_HEIGHT, EYE_SIZE = 27.0, 7.0, 1.0        # the eye's middle (outline x, y) and its half size
FORK = 2.0               # where the lobes' tip pieces begin (model pixels from the tail tip's line)


def z_of(x):
    return LENGTH / 2 - x


def at(x):
    """(top, belly, half width) of the body at outline x, eased between the profile's points."""
    xs = [p[0] for p in PROFILE]
    t_of = lambda v: (v - xs[0]) / (xs[-1] - xs[0])           # noqa: E731
    return tuple(curve([(t_of(p[0]), p[k]) for p in PROFILE], t_of(x)) for k in (1, 2, 3))


def box(x0, x1, half, y0, y1, part, across=0.0):
    """A box between outline x0 and x1, half wide either side of `across`, from y0 to y1."""
    z0, z1 = sorted((z_of(x0), z_of(x1)))
    return cube([across - half, y0, z0], [across + half, y1, z1], part)


def slice_boxes(x0, x1, part='body'):
    top, belly, hw = at((x0 + x1) / 2)
    mid, half_d = (top + belly) / 2, (top - belly) / 2
    return [box(x0, x1, round(hw * CORE_WIDTH * 4) / 4, belly, top, part),
            box(x0, x1, round(hw * 4) / 4, mid - half_d * FLANK_DEPTH, mid + half_d * FLANK_DEPTH, part)]


def body(bones):
    """The body and head as slices, the tail root as two narrow single boxes (it is a thin stalk, flat sided)."""
    x = BODY_FROM
    while x < LENGTH - 1e-6:
        x1 = min(LENGTH, x + SLICE)
        bones['head' if x >= HEAD_FROM else 'body'] += slice_boxes(x, x1)
        x = x1
    for x0, x1 in ((TAIL_ROOT, 9.0), (9.0, BODY_FROM)):
        top, belly, hw = at((x0 + x1) / 2)
        bones['tail_stalk'].append(box(x0, x1, max(0.5, round(hw * 4) / 4), belly, top, 'body'))


def eyes(bones):
    """Each eye a thin box set into the side of the head, a quarter pixel proud: the gold ring with the black pupil in
    its middle, looking out to the side (from the front only its edge shows, as on the sheet's front view)."""
    hw = at(EYE_AT)[2]
    for s in (1, -1):
        bones['head'].append(box(EYE_AT - EYE_SIZE, EYE_AT + EYE_SIZE, 0.35, EYE_HEIGHT - EYE_SIZE,
                                 EYE_HEIGHT + EYE_SIZE, 'eye', across=s * (hw - 0.1)))


def tail(bones):
    """The tail fin: its base (the outline's 2-9 high strip) and two lobes, each a band of three boxes stepping up and
    back, so the fork's notch is shallow by the base and deep at the tips (the sheet's swallow tail)."""
    t = bones['tail_fin']
    t.append(box(5.0, TAIL_ROOT, 0.25, 2.0, 9.0, 'fin'))
    mid = 5.5
    steps = ((FORK + 1.5, 5.0, 0.5, 3.5), (FORK, FORK + 1.5, 2.0, 4.5), (0.0, FORK, 3.5, 5.5))   # x0, x1, from, to
    for sign in (1, -1):                               # upper lobe, then lower (mirrored about the middle line)
        for x0, x1, a, b in steps:
            t.append(box(x0, x1, 0.25, *sorted((mid + sign * a, mid + sign * b)), 'fin'))


def fins(bones):
    # the back fin: its front rays tallest, sloping back (the sheet's pale fin with an orange tip)
    top = at(19.5)[0]
    bones['back_fin'] += [box(18.0, 21.0, 0.25, top - 0.4, 11.0, 'fin'), box(19.0, 20.5, 0.25, 11.0, 12.0, 'fin')]
    # the belly fins, one each side under the deepest part
    belly = at(19.0)[1]
    for s in (1, -1):
        bones['body'].append(box(18.0, 20.0, 0.25, 0.0, belly + 0.4, 'fin', across=s * 0.6))
    # the fin under the tail
    bones['body'].append(box(12.0, 14.0, 0.25, 0.0, at(13.0)[1] + 0.4, 'fin'))
    # the breast fins: thin plates out from the lower flank behind the gills, trailing back
    for s, name in ((1, 'fin_left'), (-1, 'fin_right')):
        hw = at(22.0)[2]
        bones[name].append(box(20.5, 23.0, 0.6, 3.25, 3.75, 'fin', across=s * (hw + 0.4)))


def model():
    names = ('body', 'head', 'tail_stalk', 'tail_fin', 'back_fin', 'fin_left', 'fin_right')
    bones_c = {n: [] for n in names}
    body(bones_c)
    eyes(bones_c)
    tail(bones_c)
    fins(bones_c)
    mid_y = 5.5
    hw22 = at(22.0)[2]
    return [
        {'name': 'root', 'pivot': [0, 0, 0], 'cubes': []},
        {'name': 'body', 'parent': 'root', 'pivot': [0, mid_y, z_of(17.0)], 'cubes': bones_c['body']},
        {'name': 'head', 'parent': 'body', 'pivot': [0, mid_y, z_of(HEAD_FROM)], 'cubes': bones_c['head']},
        {'name': 'tail_stalk', 'parent': 'body', 'pivot': [0, mid_y, z_of(BODY_FROM)], 'cubes': bones_c['tail_stalk']},
        {'name': 'tail_fin', 'parent': 'tail_stalk', 'pivot': [0, mid_y, z_of(TAIL_ROOT)], 'cubes': bones_c['tail_fin']},
        {'name': 'back_fin', 'parent': 'body', 'pivot': [0, at(19.5)[0], z_of(21.0)], 'cubes': bones_c['back_fin']},
        {'name': 'fin_left', 'parent': 'body', 'pivot': [hw22, 3.5, z_of(23.0)], 'cubes': bones_c['fin_left']},
        {'name': 'fin_right', 'parent': 'body', 'pivot': [-hw22, 3.5, z_of(23.0)], 'cubes': bones_c['fin_right']},
    ]


# --- plain colours (the paint stage comes later) ---------------------------------------------------------------------
PLAIN = {
    'back': (92, 104, 96),        # olive grey
    'body': (190, 198, 202),      # silver
    'belly': (232, 232, 224),     # white
    'fin': (178, 186, 182),       # see-through grey
    'eye': (210, 166, 62),        # the gold ring
    'pupil': (18, 18, 20),
}


def plain_colour(c, face, x, y, z):
    part = c['_part']
    if part == 'eye':
        ox = LENGTH / 2 - z
        if abs(ox - EYE_AT) < 0.5 and abs(y - EYE_HEIGHT) < 0.5 and face in ('east', 'west', 'north'):
            return PLAIN['pupil']
        return PLAIN['eye']
    if part == 'body':
        ox = LENGTH / 2 - z
        top, belly, _ = at(min(LENGTH, max(TAIL_ROOT, ox)))
        f = (y - belly) / max(0.5, top - belly)
        return PLAIN['back'] if f > 0.72 else PLAIN['belly'] if f < 0.3 else PLAIN['body']
    return PLAIN[part]


WORKSHOP_SHAPE = [
    ('CORE_WIDTH', 'Back and belly ridge width (share of the full width)', 0.3, 0.9, 0.05),
    ('FLANK_DEPTH', 'Round flank depth (share of the full depth)', 0.4, 0.9, 0.02),
    ('EYE_AT', 'Eye along the head (model pixels from the tail tip)', 25.0, 29.0, 0.25),
    ('EYE_HEIGHT', 'Eye height (model pixels)', 5.5, 8.0, 0.25),
    ('EYE_SIZE', 'Eye half size (model pixels)', 0.5, 1.5, 0.25),
    ('FORK', 'Where the tail lobes tips begin (model pixels)', 1.0, 3.0, 0.25),
]
WORKSHOP_PARTS = {'head': ['head'], 'body': ['body'], 'tail': ['tail_stalk'], 'back fin': ['back_fin'],
                  'breast fins': ['fin_left', 'fin_right']}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), 2, plain_colour)
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
