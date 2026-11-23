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
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from model_parts import cube, paint_faces, png, clip, smooth, geo, animations, write_assets   # noqa: E402

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
WIDEST = 2.25                  # half the width along the flat sides (4.5 across a depth of about 11; was 2.0 until
                               # v4, the user: "make the fish slightly bulkier")
CHEEK = 1.5                    # half the head's width across the eye: flat there, so the eye is one flush box
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
    """The head a wedge from the snout, but flat across the eye (the user, v3: the eye's two pieces sat at different
    depths and split the pupil; "make the fish slightly bulkier so that doesn't happen"): straight up from the snout
    to the eye's front, level across the eye, straight up to the back of the head; flat sides; a straight taper to
    the tail root."""
    u = LENGTH - x                                   # model pixels back from the snout
    eye_front, eye_back = LENGTH - (EYE_AT + EYE_SIZE), LENGTH - (EYE_AT - EYE_SIZE)
    head = LENGTH - HEAD_FROM
    if u < head:
        return _line(((0.0, 0.75), (eye_front, CHEEK), (eye_back, CHEEK), (head, WIDEST)), u)
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
    """Each eye one box across the flat cheek (the head is level there, so the eye is flush and the pupil whole), from
    the narrow core out a quarter pixel past the cheek, no higher or lower than the cheek boxes it lies on (the user,
    v1: the eyes' top front corners stuck out past the head). Its outer face is the red ring with the black pupil."""
    a, b = EYE_AT - EYE_SIZE, EYE_AT + EYE_SIZE
    edges = slice_edges()
    under = [flank_of(x0, x1) for x0, x1 in zip(edges, edges[1:]) if min(b, x1) - max(a, x0) >= 0.25]
    outer = max(f[0] for f in under) + 0.25
    y0 = max(EYE_HEIGHT - EYE_SIZE, max(f[1] for f in under))
    y1 = min(EYE_HEIGHT + EYE_SIZE, min(f[2] for f in under))
    core = q(half_width(EYE_AT) * CORE_WIDTH)
    for s in (1, -1):
        lo, hi = sorted((s * core, s * outer))
        bones['head'].append(box(a, b, (hi - lo) / 2, y0, y1, 'eye', across=(hi + lo) / 2))


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
    if part == 'eye' and face in ('east', 'west'):
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


# --- paint (the workshop's stage 4, 2026-11-22; the user: v4 "Shape is OK"; the detail stage skipped, as the barb's:
# the shape already carries the jaw, every fin and the eye) -----------------------------------------------------------
# The design sheet's colours (tools/distichodus_sheet.py COLOURS), placed by where each texture pixel lies on the fish:
# the body by its share of the depth at that place (the bronze line along the back, the warm peach-gold upper flank,
# the pale cream-gold lower flank, the cream belly), the six straight bands at the sheet's places along the body (a
# slate core, a softer edge, running from the back down over the belly, across the back seen from above), the head
# silver-pink with a darker top and a bright cheek and gill cover, the red-ringed eye; the fins red-orange, deep red
# where they meet the body, orange at their tips.
SHEET = {
    'back': (166, 124, 106), 'upper': (218, 170, 142), 'lower': (238, 206, 182), 'belly': (242, 226, 216),
    'band': (70, 64, 84), 'band_edge': (86, 78, 98), 'head': (178, 162, 168), 'head_top': (134, 114, 116),
    'cheek': (208, 192, 196), 'pupil': (14, 12, 16), 'ring': (156, 58, 48), 'mouth': (84, 62, 62),
    'fin': (222, 62, 22), 'tip': (246, 128, 40), 'base': (172, 52, 30), 'adipose': (226, 210, 214),
    'inside': (84, 52, 52),
}
BANDS = [(LENGTH - (10.0 + 3.5 * i), 0.85 if i < 5 else 0.6) for i in range(6)]   # (outline x, half width): the sheet's


def band_at(ox):
    """'band', 'band_edge' or None: the six straight bands along the body."""
    for c, h in BANDS:
        if abs(ox - c) <= h * 0.5:
            return 'band'
        if abs(ox - c) <= h:
            return 'band_edge'
    return None


def body_colour(face, ox, y):
    top, belly, _ = at(min(LENGTH, max(TAIL_ROOT, ox)))
    f = (y - belly) / max(0.5, top - belly)               # 0 at the belly, 1 at the back
    if ox >= HEAD_FROM:                                    # the head
        if face == 'up' or f > 0.78:
            return SHEET['head_top']
        if face != 'down' and ox < HEAD_FROM + 3.0 and 0.15 < f < 0.55:
            return SHEET['cheek']                          # the bright gill cover
        return SHEET['head']
    if face == 'down':
        return SHEET['belly']
    band = band_at(ox)
    if band:
        return SHEET[band]
    if face == 'up' or f > 0.92:
        return SHEET['back']
    return SHEET['upper'] if f > 0.58 else SHEET['lower'] if f > 0.2 else SHEET['belly']


def fin_colour(bone, ox, y):
    """Deep red at the base, red-orange, orange at the tips and free edges (the sheet's fins)."""
    if bone == 'back_fin':
        return SHEET['base'] if y < 16.0 else SHEET['tip'] if y >= 19.5 or ox < 17.0 else SHEET['fin']
    if bone in ('fin_left', 'fin_right'):
        return SHEET['base'] if ox > 27.0 else SHEET['tip'] if ox < 24.0 else SHEET['fin']
    if bone == 'tail_fin':
        return SHEET['base'] if ox > 7.5 else SHEET['tip'] if ox < 1.5 else SHEET['fin']
    # the belly fins, the fin under the tail (on the body and the stalk)
    if 16.0 <= ox <= 22.0:                                 # belly fins: deep red only in a thin strip at the body
        return SHEET['base'] if y > 4.25 else SHEET['tip'] if y < 1.0 else SHEET['fin']
    return SHEET['base'] if y > 5.5 else SHEET['tip'] if y < 3.5 else SHEET['fin']


def paint(c, face, x, y, z):
    part, bone = c['_part'], c['_bone']
    ox = LENGTH / 2 - z
    if part == 'eye' and face in ('east', 'west'):
        return SHEET['pupil'] if abs(ox - EYE_AT) < 0.5 and abs(y - EYE_HEIGHT) < 0.5 else SHEET['ring']
    if part == 'mouth':
        return SHEET['inside']
    if part == 'adipose':
        return SHEET['adipose']
    if part == 'fin':
        return fin_colour(bone, ox, y)
    if part in ('body', 'flank', 'eye'):
        if ox > LENGTH - 1.0 and face not in ('up', 'down') and abs(y - MOUTH_Y) < 0.25:
            return SHEET['mouth']                          # the mouth line round the snout's tip
        return body_colour(face, ox, y)
    return SHEET['fin']


LOOKS = {'everyday': paint, 'plain': plain_colour}
WORKSHOP_LOOK = 'everyday'

WORKSHOP_SHAPE = [
    ('WIDEST', 'Half the width along the flat sides (model pixels)', 1.25, 3.0, 0.25),
    ('CHEEK', 'Half the head width across the eye (model pixels)', 1.0, 2.25, 0.25),
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


# --- the clips (the workshop's stage 5, 2026-11-22; the user: the paint "Paint is OK") -------------------------------
# Fun first, inspired by real big river grazers (CLAUDE.md: animals are generalized): a steadier, slower beat than the
# barb's (a deep fish twice its length), the wave carried back along the body with the tail fin the most and latest;
# the breast fins held back cruising and sculling out hovering. Grazing: under a hippo grass mat it tips its nose up,
# bites and tears with quick sideways shakes of the head; at young reed shoots on the bed it tips nose down, nips and
# tugs. Each clip gives a bone's turn ADDED to its rest turn, in the model file's degrees: y the sideways swing, z the
# roll about the length, x the pitch (minus: nose up).
SWIM_LEN = 0.6           # one tail beat cruising (the game plays it faster or slower with the speed)
IDLE_LEN = 2.0
GRAZE_LEN = 2.4          # two bites at a mat overhead, each torn with head shakes
NIBBLE_LEN = 1.8         # three nips at reed shoots
BOLT_LEN = 0.45
LEAP_LEN = 1.0
FLARE_LEN = 1.4
FLOP_LEN = 0.55
DEATH_LEN = 1.2
JAW_OPEN = 35.0          # how far the small lower jaw drops, wide open (degrees)
FIN_LEAN, FIN_SHRINK, FIN_GROW = 20.0, 0.45, 0.8   # the back fin folded: leans back 20 degrees, 0.55 as tall;
                                                 # raised (the flare, -0.45): 1.36 as tall
LIE = MID_Y - WIDEST     # how far the fish comes down lying on its side: its middle line to its half width

# the wave: bone, its parent, how far it swings (radians, against the fish's line), how late (radians of the beat)
WAVE = (('body', None, 0.03, 0.9), ('head', 'body', 0.05, 0.0), ('tail_stalk', 'body', 0.2, 1.7),
        ('tail_fin', 'tail_stalk', 0.4, 2.5))


def _ease(x):
    return smooth(x)


def _wave(p, t, beat, amount, curl=0.0):
    """The swim wave at t: each bone's swing, its parent's taken off (curl bends every bone one way as well, a fish
    curving round in a leap or a flop)."""
    ab = {None: 0.0}
    for name, parent, a, lag in WAVE:
        ab[name] = amount * a * math.sin(beat * t - lag) + curl * a * 2.2
        p.setdefault(name, {})['rotation'] = [0.0, round(math.degrees(ab[name] - ab[parent]), 3), 0.0]
    return p


def _fins(p, t, out=0.0, flutter=0.0, rate=6.0, back_fin=0.0):
    """The breast fins: held back along the flank (out 0) or spread out sculling (out 1), fluttering; the back fin
    up as at rest (0), folded flat back (1) or raised taller (below 0, the flare)."""
    for s, name in ((1, 'fin_left'), (-1, 'fin_right')):
        f = flutter * math.sin(2 * math.pi * rate * t + (0.6 if s < 0 else 0.0))
        p[name] = {'rotation': [0.0, round(s * (-26.0 + 30.0 * out + 10.0 * f), 3), round(s * (10.0 * f), 3)]}
    # The back fin's base always stays on the back (the user: "the fin on the top cuts off of him"): turned as one stiff
    # plate on its front edge, tipping it up lifted its back end off the body (the flare) and folding it 50 degrees
    # pushed its swept-back tip inside the body (bolt, leap, flop, death). Now, as a real fish's rays do, folding leans
    # it back only a little while it grows shorter, lying along the back, and raising makes it taller, never tipped.
    if back_fin >= 0:
        lean, tall = -FIN_LEAN * back_fin, 1.0 - FIN_SHRINK * back_fin
    else:
        lean, tall = 0.0, 1.0 - FIN_GROW * back_fin
    p['back_fin'] = {'rotation': [round(lean, 3), 0.0, 0.0], 'scale': [1.0, round(tall, 3), 1.0]}
    return p


def _jaw(p, open_):
    p['jaw'] = {'rotation': [round(JAW_OPEN * open_, 3), 0.0, 0.0]}
    return p


def clips():
    out = {}

    def make(name, length, pose_at, steps_per_s=40, loop=True):
        out[name] = clip(length, pose_at, max(8, round(length * steps_per_s)), loop)

    # idle: hanging in the water, a slow small wave, the breast fins sculling to hold it still, a gentle bob
    def idle(t):
        w = 2 * math.pi / IDLE_LEN
        p = _wave({}, t, w, 0.3)
        p['body']['position'] = [0.0, round(0.3 * math.sin(w * t), 3), 0.0]
        return _fins(p, t, out=0.7, flutter=1.0, rate=2 / IDLE_LEN * 2)
    make('idle', IDLE_LEN, idle)

    # swim: the steady wave, the breast fins tucked back with a small flick each beat
    def swim(t):
        w = 2 * math.pi / SWIM_LEN
        return _fins(_wave({}, t, w, 1.0), t, out=0.0, flutter=0.25, rate=1 / SWIM_LEN, back_fin=0.1)
    make('swim', SWIM_LEN, swim)

    # graze: under a hippo grass mat (the game holds it just under the mat): nose tipped up, two bites, each a reach up
    # with the mouth open, a snap shut on the stems, then quick sideways shakes of the head to tear the bite off
    def graze(t):
        up = _ease(t / 0.3) * (1 - _ease((t - (GRAZE_LEN - 0.3)) / 0.3))
        k = 2 * t / GRAZE_LEN % 1.0                       # where in the bite
        reach = math.sin(math.pi * min(1.0, k / 0.4))
        open_ = _ease(k / 0.2) * (1 - _ease((k - 0.28) / 0.08))
        shake = math.sin(2 * math.pi * 3 * max(0.0, k - 0.36) / 0.5) * (1 - _ease((k - 0.7) / 0.2)) if k > 0.36 else 0
        p = _wave({}, t, 2 * math.pi / (GRAZE_LEN / 2), 0.4)
        p['body']['rotation'][0] = round(-30.0 * up - 4.0 * reach, 3)
        p['body']['position'] = [0.0, round(1.2 * up + 0.4 * reach, 3), 0.0]
        p['head']['rotation'][0] = round(-6.0 * reach, 3)
        p['head']['rotation'][1] = round(14.0 * shake, 3)
        p['body']['rotation'][2] = round(4.0 * shake, 3)
        _jaw(p, open_)
        return _fins(p, t, out=0.9, flutter=1.0, rate=4.0)
    make('graze', GRAZE_LEN, graze)

    # nibble: nose down to young reed shoots on the bed, three nips: the mouth opens as the head reaches in, snaps
    # shut on a shoot and tugs back with a twist
    def nibble(t):
        k = 3 * t / NIBBLE_LEN % 1.0
        reach = math.sin(math.pi * min(1.0, k / 0.55))
        open_ = _ease(k / 0.3) * (1 - _ease((k - 0.38) / 0.1))
        tug = math.sin(math.pi * min(1.0, max(0.0, (k - 0.48) / 0.3)))
        p = _wave({}, t, 2 * math.pi / (NIBBLE_LEN / 2), 0.4)
        p['body']['rotation'][0] = round(28.0 + 4.0 * reach - 3.0 * tug, 3)
        p['body']['position'] = [0.0, round(-0.4 * reach, 3), round(-0.8 * reach + 0.5 * tug, 3)]
        p['head']['rotation'][0] = round(6.0 * reach - 5.0 * tug, 3)
        p['head']['rotation'][1] = round(8.0 * tug, 3)
        _jaw(p, open_)
        return _fins(p, t, out=0.9, flutter=1.0, rate=3 / NIBBLE_LEN * 2)
    make('nibble', NIBBLE_LEN, nibble)

    # bolt: the fast start when scared: the body snaps into a C, the tail whips back the other way (the push), then
    # fast beats; fins folded flat (the game sends it off toward the reeds as it plays)
    def bolt(t):
        bend_end, snap_end = 0.09, 0.2
        if t < bend_end:
            c = _ease(t / bend_end)
        elif t < snap_end:
            c = 1 - 2 * _ease((t - bend_end) / (snap_end - bend_end))
        else:
            c = -1 * (1 - _ease((t - snap_end) / (BOLT_LEN - snap_end)))
        settle = _ease((t - snap_end) / (BOLT_LEN - snap_end))
        p = _wave({}, t, 2 * math.pi / (SWIM_LEN * 0.5), settle, curl=1.1 * c)
        p['body']['rotation'][2] = round(10.0 * c, 3)
        return _fins(p, t, out=0.0, back_fin=1.0 - 0.8 * settle)
    make('bolt', BOLT_LEN, bolt, 60, loop=False)

    # leap: clear of the water when badly scared (the game throws it in an arc; it must not pitch the fish itself
    # while this plays): hard tail beats driving it up nose first, a curve through the air, nose down into the water
    def leap(t):
        f = t / LEAP_LEN
        drive = 1 - _ease((f - 0.15) / 0.2)                # hard beats leaving the water, then none
        p = _wave({}, t, 2 * math.pi / (SWIM_LEN * 0.45), 1.5 * drive, curl=0.5 * math.sin(math.pi * f))
        # nose up, level at the top, nose down, straightening as it goes back in (so the swim takes over gently)
        pitch = -38.0 + 76.0 * _ease(f / 0.75) - 26.0 * _ease((f - 0.75) / 0.25)
        p['body']['rotation'][0] = round(pitch, 3)
        fold = _ease((f - 0.8) / 0.2)                       # the breast fins back along the flank as it enters
        return _fins(p, t, out=0.6 * (1 - drive) * (1 - fold), flutter=0.3 * (1 - fold), rate=5.0, back_fin=0.3 * drive)
    make('leap', LEAP_LEN, leap, 60, loop=False)

    # flare: at a rival on its patch: the back fin raised tall, the breast fins spread, the body held in a small S,
    # two quick sideways jerks of the head with the mouth a little open
    def flare(t):
        up = _ease(t / 0.25) * (1 - _ease((t - (FLARE_LEN - 0.3)) / 0.3))
        jerk = math.sin(2 * math.pi * 2 * max(0.0, t - 0.35) / 0.6) if 0.35 < t < 0.95 else 0.0
        p = _wave({}, t, 2 * math.pi / (FLARE_LEN / 2), 0.5 * up)
        p['body']['rotation'][1] = round(p['body']['rotation'][1] + 8.0 * up, 3)
        p['tail_stalk']['rotation'][1] = round(p['tail_stalk']['rotation'][1] - 14.0 * up, 3)
        p['head']['rotation'][1] = round(p['head']['rotation'][1] - 6.0 * up + 12.0 * jerk, 3)
        _jaw(p, 0.35 * up)
        return _fins(p, t, out=1.3 * up, flutter=0.6, rate=5.0, back_fin=-0.45 * up)   # the fin stood up tall
    make('flare', FLARE_LEN, flare, loop=False)

    # flop: stranded out of the water, on its side, curling and springing (the game hops it about)
    def flop(t):
        w = 2 * math.pi / FLOP_LEN
        p = _wave({}, t, w, 1.0, curl=-0.6 * (0.5 + 0.5 * math.sin(w * t)))
        p['body']['rotation'][2] = 90.0
        p['body']['position'] = [0.0, round(-LIE + 0.75 + 1.5 * abs(math.sin(w * t)), 3), 0.0]
        return _fins(p, t, out=-1.7, back_fin=1.0)
    make('flop', FLOP_LEN, flop)

    # death: the wave dies away as it rolls onto its side and settles
    def death(t):
        f = _ease(t / (DEATH_LEN * 0.8))
        down = _ease((t - DEATH_LEN * 0.25) / (DEATH_LEN * 0.6))
        p = _wave({}, t, 2 * math.pi / 0.6, 0.9 * (1 - f), curl=-0.25 * f)
        p['body']['rotation'][2] = round(90.0 * f, 3)
        p['body']['position'] = [0.0, round(-LIE * down, 3), 0.0]
        return _fins(p, t, out=-1.7 * f, back_fin=f)
    make('death', DEATH_LEN, death, loop='hold')
    return out


def WORKSHOP_CLIPS():
    return clips()


# For the versions page's clip player: the made size against the game's (0.8 blocks of the made 2.3125), the water
# clips and their surface (blocks at made size), and a line about each clip.
WORKSHOP_CLIP_INFO = {
    'scale': 0.8 / (LENGTH / 16), 'water': ['idle', 'swim', 'graze', 'nibble', 'bolt', 'flare'],
    'surface': 1.6,
    'notes': {
        'idle': 'Hanging in the water: a slow small wave, the breast fins sculling to hold it still, a gentle bob.',
        'swim': 'The steady tail beat, the wave running back along the body; the game plays it faster the faster it swims.',
        'graze': 'Under a hippo grass mat: nose up, two bites, each torn off with quick shakes of the head.',
        'nibble': 'Nose down to young reed shoots on the bed: three nips, each tugged back with a twist.',
        'bolt': 'The escape when scared: snaps into a C, the tail whips it clear, fast beats, off to the reeds.',
        'leap': 'Badly scared: hard beats drive it up out of the water nose first, a curve through the air, nose down back in.',
        'flare': 'At a rival on its patch: back fin raised tall, breast fins spread, a small S and two jerks of the head.',
        'flop': 'Stranded on land: on its side, curling and springing.',
        'death': 'The wave dies away as it rolls onto its side.',
    }}


def write_game():
    """Writes the distichodus's model, texture and clips into the savanna mod (no entity uses them yet: that is the
    plan's step 6; the files let the clip checks read the model as the game will)."""
    bones, rows, w, h = paint_faces(model(), 2, LOOKS['everyday'])
    files = {'textures/entity/distichodus.png': png(rows),
             'geo/entity/distichodus.geo.json': geo('distichodus', bones, w, h, (3, 2, [0, 0.6, 0])),
             'animations/entity/distichodus.animation.json': animations(clips())}
    write_assets(files)


if __name__ == '__main__':
    if sys.argv[1:] == ['game']:
        write_game()
        sys.exit()
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
