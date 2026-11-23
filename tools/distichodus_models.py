"""The six-banded distichodus's model maker (docs/plans/distichodus.md; the model workshop, stage 2: the plain 3D shape,
2026-11-22). Built from the outline the user approved ("Outline is OK", references/outlines/distichodus.json) and the
design sheet's straight lines (tools/distichodus_sheet.py; the user: "straight lines"): made 37 model pixels long,
drawn in the game at 0.8 blocks grown.

    python tools/distichodus_models.py                          boxes and bones
    python tools/workshop.py snapshot distichodus --note "..."  keep this build as the next version
    python tools/workshop.py versions distichodus               the versions page with Compare against the sheet

Units are model pixels (16 a block); the head to -Z, up +Y, left +X. The outline's x (tail tip 0 to snout 37) is
z = 18.5 - x here, and its y (the lowest fin tip 0) is kept as y; the body's middle line is y 9.5.

The shape keeps the sheet's straight lines (the user twice turned down a curved face): the top and the belly run
straight between a few points, never eased into curves, and the width is the sheet's wedge. The body is slices from
the tail root to the snout, each two boxes, a tall narrow one (the full depth) and a short wide one (the flank), so
seen from the front the fish is a flat-sided oval, not a brick; the head's slices are one pixel long so the wedge of
the face steps finely and reads straight. One box a side makes each eye. The tail is a stalk and a fin of two lobes
stepping out into a deep fork (so the swim can bend it); the back fin, the breast fins and the small jaw are their own
bones (to fold, flutter and nibble); the belly fins, the fin under the tail and the small pale fin near the tail sit
on the body and the stalk. Box budget under 70.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from model_parts import cube, paint_faces, png   # noqa: E402

# --- shape numbers (model pixels; along the outline's x, tail tip 0 to snout 37) -------------------------------------
LENGTH = 37.0
MID_Y = 9.5                    # the body's middle line (the snout sits on it)
# The sheet's straight lines (x, y): the top from the snout up to the back fin's front, level under the fin, down to
# the tail root; the belly from the snout down to the throat, nearly flat, up to the tail root.
TOP = ((9.0, 11.3), (18.0, 15.5), (23.0, 15.5), (37.0, 10.1))
BELLY = ((9.0, 7.7), (17.0, 4.5), (28.0, 4.9), (37.0, 8.9))
HEAD_FROM = 28.0               # the gill cover's edge (the head bone from here forward)
BODY_FROM = 13.0               # the body bone from here; the tail stalk behind it to TAIL_ROOT
TAIL_ROOT = 9.0                # the tail fin behind this
WIDEST = 2.0                   # half the width along the flat sides (4 across a depth of about 11)
CORE_WIDTH = 0.55              # the tall box's share of the width (the back and belly ridges)
FLANK_DEPTH = 0.62             # the wide box's share of the depth (the flat flank)
HEAD_FLANK_DEPTH = 0.85        # the head's: a full flat cheek, so the eye sits on it (the user, v1: the eyes' top
                               # corners stuck out past the head)
BODY_SLICE, HEAD_SLICE = 2.0, 1.0
SNOUT = 1.5                    # the last stretch, split at the mouth line into the upper lip and the small jaw
EYE_AT, EYE_HEIGHT, EYE_SIZE = 33.0, 10.25, 1.0   # the eye's middle (outline x, y) and its half size: two head
                                                  # slices long, inside the cheek's height
MOUTH_Y = 9.35                 # the mouth line, a hair under the middle line (the small mouth at the tip)


def z_of(x):
    return LENGTH / 2 - x


def _line(points, x):
    """Straight between the points (no easing: the user's straight lines)."""
    if x <= points[0][0]:
        return points[0][1]
    for (xa, ya), (xb, yb) in zip(points, points[1:]):
        if xa <= x <= xb:
            return ya + (yb - ya) * (x - xa) / (xb - xa)
    return points[-1][1]


def half_width(x):
    """The sheet's wedge: half the width straight up from the snout to the back of the head, flat sides, a straight
    taper to the tail root."""
    u = LENGTH - x                                   # model pixels back from the snout
    if u < LENGTH - HEAD_FROM:
        return 0.5 + (WIDEST - 0.5) * u / (LENGTH - HEAD_FROM)
    if u < 22:
        return WIDEST
    return max(0.5, WIDEST - (u - 22) * 0.25)


def at(x):
    """(top, belly, half width) of the body at outline x."""
    return _line(TOP, x), _line(BELLY, x), half_width(x)


def q(v):
    return round(v * 4) / 4


def box(x0, x1, half, y0, y1, part, across=0.0):
    """A box between outline x0 and x1, half wide either side of `across`, from y0 to y1."""
    z0, z1 = sorted((z_of(x0), z_of(x1)))
    return cube([across - half, y0, z0], [across + half, y1, z1], part)


def flank_of(x0, x1):
    """A slice's flank box: (half width, bottom, top); fuller on the head (the cheek)."""
    top, belly, hw = at((x0 + x1) / 2)
    mid, half_d = (top + belly) / 2, (top - belly) / 2
    depth = HEAD_FLANK_DEPTH if x0 >= HEAD_FROM - 1e-6 else FLANK_DEPTH
    return q(hw), q(mid - half_d * depth), q(mid + half_d * depth)


def slice_boxes(x0, x1):
    """A slice: the tall narrow box the full depth, the wide one the middle of the depth (the flat flank)."""
    top, belly, hw = at((x0 + x1) / 2)
    flank, f0, f1 = flank_of(x0, x1)
    return [box(x0, x1, q(hw * CORE_WIDTH), q(belly), q(top), 'body'),
            box(x0, x1, flank, f0, f1, 'flank')]


def slice_edges():
    """Where the slices begin and end: two pixels along the body, one along the head (the straight wedge)."""
    edges, x = [BODY_FROM], BODY_FROM
    while x < LENGTH - SNOUT - 1e-6:
        step = HEAD_SLICE if x >= HEAD_FROM - 1e-6 else BODY_SLICE
        x = min(LENGTH - SNOUT, HEAD_FROM if x < HEAD_FROM < x + step else x + step)
        edges.append(x)
    return edges


def body(bones):
    """The body and head as slices, the tail stalk as two single boxes (a narrow flat-sided stalk)."""
    edges = slice_edges()
    for x0, x1 in zip(edges, edges[1:]):
        bones['head' if x0 >= HEAD_FROM - 1e-6 else 'body'] += slice_boxes(x0, x1)
    for x0, x1 in ((TAIL_ROOT, 11.0), (11.0, BODY_FROM)):
        top, belly, hw = at((x0 + x1) / 2)
        bones['tail_stalk'].append(box(x0, x1, max(0.5, q(hw)), q(belly), q(top), 'body'))


def mouth(bones):
    """The snout's tip split at the mouth line: the upper lip on the head, the small lower jaw its own bone (it drops
    to nip hippo grass), the dark inside of the mouth behind the lips, hidden while it is shut."""
    x0, x1 = LENGTH - SNOUT, LENGTH
    top, belly, hw = at(x0 + SNOUT / 2)
    bones['head'].append(box(x0, x1, q(max(0.5, hw * 0.9)), MOUTH_Y, q(top), 'body'))
    bones['jaw'].append(box(x0 - 0.5, x1, q(max(0.5, hw * 0.8)), q(belly), MOUTH_Y, 'body'))
    bones['head'].append(box(x0, x1 - 0.5, 0.25, q(belly) + 0.25, MOUTH_Y + 0.25, 'mouth'))


def eyes(bones):
    """Each eye one piece per head slice it covers, each from the narrow core out to a quarter pixel past that slice's
    own cheek and no higher or lower than the cheek, so the eye follows the wedge of the face and nothing of it stands
    out past the head (the user, v1: "the top left of their eyes go past their body and stick out": one box sized to
    the widest slice stood proud of the narrower ones in front, and rose above the cheek). Its outer faces are the red
    ring with the black pupil."""
    a, b = EYE_AT - EYE_SIZE, EYE_AT + EYE_SIZE
    edges = slice_edges()
    for x0, x1 in zip(edges, edges[1:]):
        p0, p1 = max(a, x0), min(b, x1)
        if p1 - p0 < 0.25:
            continue
        flank, f0, f1 = flank_of(x0, x1)
        core = q(half_width((x0 + x1) / 2) * CORE_WIDTH)
        y0, y1 = max(EYE_HEIGHT - EYE_SIZE, f0), min(EYE_HEIGHT + EYE_SIZE, f1)
        for s in (1, -1):
            lo, hi = sorted((s * core, s * (flank + 0.25)))
            bones['head'].append(box(p0, p1, (hi - lo) / 2, y0, y1, 'eye', across=(hi + lo) / 2))


def tail(bones):
    """The forked tail: two lobes, each five boxes stepping up and back from the stalk (the sheet's straight edges:
    the outer edge from the stalk to the tip, the inner from the tip down to the notch on the middle line)."""
    t = bones['tail_fin']
    steps = ((6.0, 9.0, 0.0, 3.1), (4.0, 6.0, 0.0, 4.3), (2.5, 4.0, 1.1, 5.3), (1.0, 2.5, 3.1, 6.3),
             (0.0, 1.0, 5.1, 6.9))                     # x0, x1, from, to (model pixels up from the middle line)
    for sign in (1, -1):                               # the upper lobe, then the lower, mirrored about the middle line
        for x0, x1, a, b in steps:
            t.append(box(x0, x1, 0.25, *sorted((MID_Y + sign * a, MID_Y + sign * b)), 'fin'))


def fins(bones):
    # the back fin: tall and swept back, its tip behind its base (four steps up the sheet's straight edges)
    for x0, x1, y0, y1 in ((18.0, 23.0, 15.0, 16.5), (17.3, 21.0, 16.5, 18.0), (16.8, 19.0, 18.0, 19.5),
                           (16.5, 17.6, 19.5, 20.5)):
        bones['back_fin'].append(box(x0, x1, 0.25, y0, y1, 'fin'))
    # the small pale fin on the back near the tail (on the stalk)
    bones['tail_stalk'].append(box(11.0, 12.5, 0.25, q(at(11.75)[0]) - 0.25, 14.0, 'adipose'))
    # the fin under the tail, swept back and down
    for x0, x1, y0, y1 in ((11.5, 14.8, 4.75, 6.0), (10.0, 13.0, 3.5, 4.75), (9.25, 11.25, 2.75, 3.5)):
        bones['body' if x0 >= BODY_FROM - 2 else 'tail_stalk'].append(box(x0, x1, 0.25, y0, y1, 'fin'))
    # the belly fins, one each side under the deepest part, swept back to their tips
    for s in (1, -1):
        for x0, x1, y0, y1 in ((18.0, 21.5, 3.25, 4.75), (17.25, 19.5, 1.5, 3.25), (16.5, 18.5, 0.0, 1.5)):
            bones['body'].append(box(x0, x1, 0.25, y0, y1, 'fin', across=s * 0.75))
    # the breast fins: thin plates out from the lower flank behind the gill cover, trailing back and down
    for s, name in ((1, 'fin_left'), (-1, 'fin_right')):
        out = WIDEST + 0.6
        bones[name].append(box(25.25, 27.75, 0.6, 5.0, 5.5, 'fin', across=s * out))
        bones[name].append(box(23.0, 25.25, 0.6, 4.25, 4.75, 'fin', across=s * (out + 0.3)))


def model():
    names = ('body', 'head', 'jaw', 'tail_stalk', 'tail_fin', 'back_fin', 'fin_left', 'fin_right')
    bones_c = {n: [] for n in names}
    body(bones_c)
    mouth(bones_c)
    eyes(bones_c)
    tail(bones_c)
    fins(bones_c)
    return [
        {'name': 'root', 'pivot': [0, 0, 0], 'cubes': []},
        {'name': 'body', 'parent': 'root', 'pivot': [0, MID_Y, z_of(20.0)], 'cubes': bones_c['body']},
        {'name': 'head', 'parent': 'body', 'pivot': [0, MID_Y, z_of(HEAD_FROM)], 'cubes': bones_c['head']},
        {'name': 'jaw', 'parent': 'head', 'pivot': [0, MOUTH_Y, z_of(LENGTH - SNOUT - 0.5)], 'cubes': bones_c['jaw']},
        {'name': 'tail_stalk', 'parent': 'body', 'pivot': [0, MID_Y, z_of(BODY_FROM)], 'cubes': bones_c['tail_stalk']},
        {'name': 'tail_fin', 'parent': 'tail_stalk', 'pivot': [0, MID_Y, z_of(TAIL_ROOT)],
         'cubes': bones_c['tail_fin']},
        {'name': 'back_fin', 'parent': 'body', 'pivot': [0, 15.5, z_of(23.0)], 'cubes': bones_c['back_fin']},
        {'name': 'fin_left', 'parent': 'body', 'pivot': [WIDEST, 5.25, z_of(27.75)], 'cubes': bones_c['fin_left']},
        {'name': 'fin_right', 'parent': 'body', 'pivot': [-WIDEST, 5.25, z_of(27.75)], 'cubes': bones_c['fin_right']},
    ]


# --- plain colours (the paint stage comes later: the six bands, the silver-pink head) -------------------------------
PLAIN = {
    'back': (166, 124, 106),      # bronze
    'body': (218, 170, 142),      # peach-gold
    'belly': (242, 226, 216),     # cream
    'head': (178, 162, 168),      # silver-pink
    'fin': (222, 62, 22),         # red-orange
    'adipose': (226, 210, 214),   # pale
    'eye': (156, 58, 48),         # the red ring
    'pupil': (14, 12, 16),
    'mouth': (84, 52, 52),        # the dark inside of the mouth
}


def plain_colour(c, face, x, y, z):
    part, bone = c['_part'], c['_bone']
    ox = LENGTH / 2 - z
    if part == 'eye' and face not in ('up', 'down'):     # the sides, and the little step between the eye's two
                                                          # pieces, so the pupil reads as one
        if abs(ox - EYE_AT) < 0.5 and abs(y - EYE_HEIGHT) < 0.5:
            return PLAIN['pupil']
        return PLAIN['eye']
    if part in ('body', 'flank', 'eye'):
        if bone in ('head', 'jaw') or part == 'eye':
            return PLAIN['head']
        top, belly, _ = at(min(LENGTH, max(TAIL_ROOT, ox)))
        f = (y - belly) / max(0.5, top - belly)
        return PLAIN['back'] if f > 0.85 else PLAIN['belly'] if f < 0.22 else PLAIN['body']
    return PLAIN[part]


LOOKS = {'plain': plain_colour}
WORKSHOP_LOOK = 'plain'

WORKSHOP_SHAPE = [
    ('WIDEST', 'Half the width along the flat sides (model pixels)', 1.25, 3.0, 0.25),
    ('CORE_WIDTH', 'Back and belly ridge width (share of the full width)', 0.3, 0.9, 0.05),
    ('FLANK_DEPTH', 'Flat flank depth (share of the full depth)', 0.4, 0.9, 0.02),
    ('HEAD_FLANK_DEPTH', 'Cheek depth on the head (share of the full depth)', 0.5, 1.0, 0.05),
    ('EYE_AT', 'Eye along the head (model pixels from the tail tip)', 31.0, 35.0, 0.25),
    ('EYE_HEIGHT', 'Eye height (model pixels)', 9.0, 11.5, 0.25),
    ('EYE_SIZE', 'Eye half size (model pixels)', 0.5, 1.5, 0.25),
]
WORKSHOP_PARTS = {'head': ['head', 'jaw'], 'body': ['body'], 'tail': ['tail_stalk', 'tail_fin'],
                  'back fin': ['back_fin'], 'breast fins': ['fin_left', 'fin_right']}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), 2, LOOKS[WORKSHOP_LOOK])
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
