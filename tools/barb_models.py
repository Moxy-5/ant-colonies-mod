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
# The head's top and chin follow the sheet's steps (deep to x 25, rising only at the blunt snout). Plumper than the
# outline (the user, 2026-11-20, "make it plumper so there's more space for the eyes"): as wide as the sheet's front
# view (7 at the deepest), the head kept broad back from the eyes so they sit on a flat cheek.
PROFILE = ((7, 6.9, 4.1, 0.5), (9, 7.0, 4.0, 0.7), (11.5, 8.0, 3.0, 1.3), (14, 9.0, 2.0, 2.2), (17, 9.8, 1.2, 3.0),
           (20, 10.0, 1.0, 3.5), (23, 9.6, 1.1, 3.4), (25.5, 9.0, 1.8, 3.1), (27.5, 8.0, 2.8, 2.6),
           (29, 7.0, 3.6, 1.8), (30, 6.2, 4.0, 1.2))
CORE_WIDTH = 0.55        # the tall box's share of the width (the back and belly ridges)
FLANK_DEPTH = 0.62       # the wide box's share of the depth (the round flank)
SLICE = 2.0
BODY_FROM, HEAD_FROM, TAIL_ROOT = 11.0, 23.0, 7.0    # the body bone from x 11, the head bone from 23, tail fin before 7
EYE_AT, EYE_HEIGHT, EYE_SIZE = 26.0, 7.0, 1.0        # the eye's middle (outline x, y) and its half size
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


def slice_of(x):
    """The body slice holding outline x: (x0, x1, core half width, flank half width, flank bottom, flank top)."""
    x0 = BODY_FROM + SLICE * int((x - BODY_FROM) // SLICE)
    x1 = min(LENGTH, x0 + SLICE)
    top, belly, hw = at((x0 + x1) / 2)
    mid, half_d = (top + belly) / 2, (top - belly) / 2
    return (x0, x1, round(hw * CORE_WIDTH * 4) / 4, round(hw * 4) / 4,
            mid - half_d * FLANK_DEPTH, mid + half_d * FLANK_DEPTH)


def slice_boxes(x0, x1, part='body'):
    top, belly, _ = at((x0 + x1) / 2)
    _, _, core, flank, f0, f1 = slice_of(x0)
    return [box(x0, x1, core, belly, top, part), box(x0, x1, flank, f0, f1, 'flank')]


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
    """Each eye a block in one head slice (never across a slice's edge, where the next slice would cover half of it),
    from the narrow core out to a quarter pixel past the cheek, so it is joined to the head all round even where it
    rises above the round flank. Its outer face is the gold ring with the black pupil; its other faces are painted as
    the head's skin."""
    x0, x1, core, flank, _, _ = slice_of(EYE_AT)
    a, b = max(x0, EYE_AT - EYE_SIZE), min(x1, EYE_AT + EYE_SIZE)
    for s in (1, -1):
        lo, hi = sorted((s * (core - 0.25), s * (flank + 0.25)))
        bones['head'].append(box(a, b, (hi - lo) / 2, EYE_HEIGHT - EYE_SIZE, EYE_HEIGHT + EYE_SIZE, 'eye',
                                 across=(hi + lo) / 2))


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
    if part == 'eye' and face in ('east', 'west'):
        ox = LENGTH / 2 - z
        if abs(ox - EYE_AT) < 0.5 and abs(y - EYE_HEIGHT) < 0.5:
            return PLAIN['pupil']
        return PLAIN['eye']
    if part in ('body', 'flank', 'eye'):
        ox = LENGTH / 2 - z
        top, belly, _ = at(min(LENGTH, max(TAIL_ROOT, ox)))
        f = (y - belly) / max(0.5, top - belly)
        return PLAIN['back'] if f > 0.72 else PLAIN['belly'] if f < 0.3 else PLAIN['body']
    return PLAIN[part]


# --- paint (the workshop's stage 4, 2026-11-20; the user: v3 "that's perfect, color it") ----------------------------
# The colours of the design sheet (tools/barb_sheet.py), placed by where each texture pixel lies on the fish. The sheet's
# rows are fixed heights on the level fish (row r is y 11 - r to 12 - r), so the flank's bands are read by height: the
# olive-grey back, a paler upper flank, the silver flank with the thin dark line along the middle, silver in shade low
# down, the white belly. From above the back is olive, the flank's shoulders paler; from below white.
SHEET = {
    'back': (92, 104, 96), 'upper': (122, 134, 124), 'silver': (196, 204, 208), 'shade': (168, 176, 182),
    'belly': (236, 236, 228), 'line': (120, 128, 134), 'spot': (28, 30, 34), 'eye': (214, 168, 64),
    'pupil': (16, 16, 18), 'fin': (178, 186, 182), 'orange': (222, 132, 52), 'red': (200, 46, 40),
    'mouth': (70, 72, 70), 'flash': (250, 252, 255),
}
LINE = (9.0, 22.0, 6.0, 6.5)        # the dark line along the side: from x, to x, from y, to y (half a pixel thin)
SPOT = (7.0, 9.0, 5.5, 6.5)         # the dark spot at the tail's base (on the sides only), the line running into it
GILL = 22.75                        # the gill cover's back edge, a shaded line down the cheek


def flank_band(y, flash=False):
    """The side's colour at height y (the sheet's rows); flash: the flank turned to the light (the sheet's turning
    fish: the silver goes white, the shaded silver and the line go silver)."""
    if y >= 9.0:
        return 'back'
    if y >= 8.0:
        return 'upper'
    if y >= 5.0:
        return 'flash' if flash else 'silver'
    if y >= 4.0:
        return 'silver' if flash else 'shade'
    return 'belly'


def paint(c, face, x, y, z, flash=False):
    part, bone = c['_part'], c['_bone']
    ox = LENGTH / 2 - z
    col = SHEET
    if part == 'eye' and face in ('east', 'west'):
        return col['pupil'] if abs(ox - EYE_AT) < 0.5 and abs(y - EYE_HEIGHT) < 0.5 else col['eye']
    if part in ('body', 'flank', 'eye'):
        if face == 'up':
            return col['back'] if part == 'body' or y >= 9.0 else col['upper']
        if face == 'down':
            return col['belly']
        if ox > LENGTH - 0.99 and 4.75 <= y < 5.25:
            return col['mouth']                                   # the mouth line round the snout
        if SPOT[0] <= ox < SPOT[1] and SPOT[2] <= y < SPOT[3] and face in ('east', 'west'):
            return col['spot']
        if LINE[0] <= ox < LINE[1] and LINE[2] <= y < LINE[3]:
            return col['silver'] if flash else col['line']
        band = flank_band(y, flash)
        if abs(ox - GILL) < 0.25 and band in ('silver', 'flash', 'shade') and face in ('east', 'west'):
            return col['shade'] if not flash else col['silver']   # the gill cover's edge
        return col[band]
    # the fins: pale grey, the back fin's tip and front edge, the belly fins' and the tail fin's back tips orange, the
    # forked tail's two tips red
    if bone == 'back_fin':
        return col['orange'] if y >= 11.0 or (ox >= 20.5 and y >= 10.5) else col['fin']
    if bone in ('fin_left', 'fin_right'):
        return col['orange'] if ox < 21.0 else col['fin']
    if bone == 'tail_fin':
        return col['red'] if ox < FORK and (y >= 9.25 or y < 1.75) else col['fin']
    if bone == 'body' and y < 1.0 and ((17.5 < ox < 19.0) or (11.5 < ox < 13.0)):
        return col['orange']                                      # the belly fins' and the fin under the tail's tips
    return col['fin']


LOOKS = {'everyday': lambda c, f, x, y, z: paint(c, f, x, y, z),
         'flash': lambda c, f, x, y, z: paint(c, f, x, y, z, flash=True),
         'plain': plain_colour}
WORKSHOP_LOOK = 'everyday'


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
    bones, rows, w, h = paint_faces(model(), 2, LOOKS[WORKSHOP_LOOK])
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
