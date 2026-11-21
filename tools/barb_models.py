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
import math  # noqa: E402

from model_parts import cube, curve, paint_faces, png, clip, smooth, geo, animations, write_assets   # noqa: E402

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
MOUTH_Y, JAW_FROM, SNOUT = 5.0, 27.5, 2.0   # the mouth line's height, where the lower jaw hinges, the snout's length
FORK = 2.0               # where the lobes' tip pieces begin (model pixels from the tail tip's line)
# The female (2026-11-20, my pick from real small barbs: the female a little fuller, her belly rounded with eggs, the
# male slimmer and brighter): PLUMP 0 is the approved fish (the male), 1 the female. The belly drops and widens round
# the deepest part (PLUMP_AT, PLUMP_SPREAD), the belly fins and the fin under the tail move down with it.
PLUMP = 0.0
PLUMP_DROP, PLUMP_WIDEN, PLUMP_AT, PLUMP_SPREAD = 1.6, 0.6, 17.5, 4.5


def z_of(x):
    return LENGTH / 2 - x


def at(x):
    """(top, belly, half width) of the body at outline x, eased between the profile's points."""
    xs = [p[0] for p in PROFILE]
    t_of = lambda v: (v - xs[0]) / (xs[-1] - xs[0])           # noqa: E731
    top, belly, hw = (curve([(t_of(p[0]), p[k]) for p in PROFILE], t_of(x)) for k in (1, 2, 3))
    b = PLUMP * math.exp(-((x - PLUMP_AT) / PLUMP_SPREAD) ** 2)
    return top, belly - PLUMP_DROP * b, hw + PLUMP_WIDEN * b


def fin_drop():
    """How far the belly fins and the fin under the tail sit lower on a plump fish (they keep their own length)."""
    return PLUMP * PLUMP_DROP * 0.8


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
    while x < LENGTH - SNOUT - 1e-6:                  # the last two pixels are the snout with its mouth (mouth())
        x1 = min(LENGTH - SNOUT, x + SLICE)
        bones['head' if x >= HEAD_FROM else 'body'] += slice_boxes(x, x1)
        x = x1
    for x0, x1 in ((TAIL_ROOT, 9.0), (9.0, BODY_FROM)):
        top, belly, hw = at((x0 + x1) / 2)
        bones['tail_stalk'].append(box(x0, x1, max(0.5, round(hw * 4) / 4), belly, top, 'body'))


def mouth(bones):
    """The snout's last two pixels split at the mouth line: the upper lip on the head, the lower jaw its own bone hinged
    a little behind (it drops to open the mouth: nibbling algae, sipping at the surface), and the dark inside of the
    mouth behind the lips, hidden while the mouth is shut (the user, 2026-11-20, "the mouth opening for algae")."""
    x0, x1 = LENGTH - SNOUT, LENGTH
    top, belly, hw = at(x0 + SNOUT / 2)
    bones['head'].append(box(x0, x1, round(hw * 0.85 * 4) / 4, MOUTH_Y, top, 'body'))
    bones['jaw'].append(box(JAW_FROM, x1, round(hw * 0.7 * 4) / 4, belly, MOUTH_Y, 'body'))
    bones['head'].append(box(JAW_FROM + 0.3, x1 - 0.4, round(hw * 0.5 * 4) / 4, belly + 0.4, MOUTH_Y + 0.25, 'mouth'))


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
    belly, d = at(19.0)[1], fin_drop()
    for s in (1, -1):
        bones['body'].append(box(18.0, 20.0, 0.25, 0.0 - d, belly + 0.4, 'fin', across=s * 0.6))
    # the fin under the tail
    bones['body'].append(box(12.0, 14.0, 0.25, 0.0 - d * 0.5, at(13.0)[1] + 0.4, 'fin'))
    # the breast fins: thin plates out from the lower flank behind the gills, trailing back
    for s, name in ((1, 'fin_left'), (-1, 'fin_right')):
        hw = at(22.0)[2]
        bones[name].append(box(20.5, 23.0, 0.6, 3.25, 3.75, 'fin', across=s * (hw + 0.4)))


def model():
    names = ('body', 'head', 'jaw', 'tail_stalk', 'tail_fin', 'back_fin', 'fin_left', 'fin_right')
    bones_c = {n: [] for n in names}
    body(bones_c)
    mouth(bones_c)
    eyes(bones_c)
    tail(bones_c)
    fins(bones_c)
    mid_y = 5.5
    hw22 = at(22.0)[2]
    return [
        {'name': 'root', 'pivot': [0, 0, 0], 'cubes': []},
        {'name': 'body', 'parent': 'root', 'pivot': [0, mid_y, z_of(17.0)], 'cubes': bones_c['body']},
        {'name': 'head', 'parent': 'body', 'pivot': [0, mid_y, z_of(HEAD_FROM)], 'cubes': bones_c['head']},
        {'name': 'jaw', 'parent': 'head', 'pivot': [0, MOUTH_Y, z_of(JAW_FROM)], 'cubes': bones_c['jaw']},
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
    'mouth': (96, 44, 46),        # the dark inside of the mouth
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
    'mouth': (70, 72, 70), 'flash': (250, 252, 255), 'inside': (96, 44, 46),
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


# The sexes' colours (2026-11-20, my picks, inspired by real small barbs): the approved paint is the male's everyday
# look. The female's fins are paler, amber rather than orange, the tail's tips a softer red. In the rains a breeding
# male brightens: deeper orange-red fins, the tail's tips scarlet, a coppery gold flush above the side line, and the
# few tiny white bumps real breeding barbs grow on the snout and head.
STYLES = {
    'male': {},
    'female': {'orange': (224, 178, 100), 'red': (206, 104, 80)},
    'breeding': {'orange': (238, 92, 34), 'red': (220, 28, 30), 'upper': (166, 140, 82), 'silver': (212, 206, 190)},
}
BUMPS = ((26.5, 0.5), (27.5, -0.5), (28.5, 0.25), (25.5, -1.0), (25.0, 1.0))   # the breeding bumps: (x, across)


def paint(c, face, x, y, z, flash=False, style='male'):
    part, bone = c['_part'], c['_bone']
    ox = LENGTH / 2 - z
    col = dict(SHEET, **STYLES[style])
    if style == 'breeding' and bone == 'head' and face == 'up' and any(
            abs(ox - bx) < 0.5 and abs(x - bz) < 0.5 for bx, bz in BUMPS):
        return (244, 244, 236)
    if part == 'eye' and face in ('east', 'west'):
        return col['pupil'] if abs(ox - EYE_AT) < 0.5 and abs(y - EYE_HEIGHT) < 0.5 else col['eye']
    if part == 'mouth':
        return col['inside']
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
    d = fin_drop()
    if bone == 'body' and ((17.5 < ox < 19.0 and y < 1.0 - d) or (11.5 < ox < 13.0 and y < 1.0 - d * 0.5)):
        return col['orange']                                      # the belly fins' and the fin under the tail's tips
    return col['fin']


LOOKS = {'everyday': lambda c, f, x, y, z: paint(c, f, x, y, z),
         'flash': lambda c, f, x, y, z: paint(c, f, x, y, z, flash=True),
         'female': lambda c, f, x, y, z: paint(c, f, x, y, z, style='female'),
         'female_flash': lambda c, f, x, y, z: paint(c, f, x, y, z, flash=True, style='female'),
         'breeding': lambda c, f, x, y, z: paint(c, f, x, y, z, style='breeding'),
         'breeding_flash': lambda c, f, x, y, z: paint(c, f, x, y, z, flash=True, style='breeding'),
         'plain': plain_colour}
WORKSHOP_LOOK = 'everyday'


WORKSHOP_SHAPE = [
    ('CORE_WIDTH', 'Back and belly ridge width (share of the full width)', 0.3, 0.9, 0.05),
    ('FLANK_DEPTH', 'Round flank depth (share of the full depth)', 0.4, 0.9, 0.02),
    ('EYE_AT', 'Eye along the head (model pixels from the tail tip)', 25.0, 29.0, 0.25),
    ('EYE_HEIGHT', 'Eye height (model pixels)', 5.5, 8.0, 0.25),
    ('EYE_SIZE', 'Eye half size (model pixels)', 0.5, 1.5, 0.25),
    ('FORK', 'Where the tail lobes tips begin (model pixels)', 1.0, 3.0, 0.25),
    ('PLUMP', 'Plumpness (0 the male, 1 the female full of eggs)', 0.0, 1.5, 0.05),
]
WORKSHOP_PARTS = {'head': ['head'], 'body': ['body'], 'tail': ['tail_stalk'], 'back fin': ['back_fin'],
                  'breast fins': ['fin_left', 'fin_right']}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), 2, LOOKS[WORKSHOP_LOOK])
    return bones, png(rows), w, h


# --- the clips (the workshop's stage 5, 2026-11-20; the user: the paint "perfect, start the animations") -------------
# Fun first, inspired by real small schooling fish (CLAUDE.md: animals are generalized): the swim is a quick wave
# carried back along the body, the head swinging a little against it and the tail fin the most, late; the breast fins
# held back along the body while cruising and sculling out while hovering. Each clip gives a bone's turn ADDED to its
# rest turn, in the model file's degrees: y the sideways swing, z the roll about the length, x the pitch.
SWIM_LEN = 0.45          # one tail beat at cruising speed (the game plays it faster or slower with the speed)
IDLE_LEN = 1.6
TURN_LEN = 0.6
BANK = 38.0              # how far a turning fish rolls its flank to the light (the flash)
SIP_LEN = 1.4
SCATTER_LEN = 0.4
DEATH_LEN = 1.0
FLOP_LEN = 0.5
NIBBLE_LEN = 1.8         # three pecks at the algae
JAW_OPEN = 40.0          # how far the lower jaw drops, wide open (degrees): big enough to read on a quarter-block fish
LIE = 5.5 - 3.6          # how far the fish comes down lying on its side: its middle line (5.5 up) to its half width

# the wave: bone, its parent, how far it swings (radians, against the fish's line), how late (radians of the beat)
WAVE = (('body', None, 0.035, 0.9), ('head', 'body', 0.06, 0.0), ('tail_stalk', 'body', 0.24, 1.7),
        ('tail_fin', 'tail_stalk', 0.46, 2.5))


def _ease(x):
    return smooth(x)


def _wave(p, t, beat, amount, curl=0.0):
    """The swim wave at t: each bone's swing, its parent's taken off (curl bends every bone one way as well, a fish
    curving round as it turns or flops)."""
    ab = {None: 0.0}
    for name, parent, a, lag in WAVE:
        ab[name] = amount * a * math.sin(beat * t - lag) + curl * a * 2.2
        p.setdefault(name, {})['rotation'] = [0.0, round(math.degrees(ab[name] - ab[parent]), 3), 0.0]
    return p


def _fins(p, t, out=0.0, flutter=0.0, rate=8.0, back_fin=0.0):
    """The breast fins: held back along the flank (out 0) or spread out sculling (out 1), fluttering; the back fin
    raised (0) or folded flat back (1)."""
    for s, name in ((1, 'fin_left'), (-1, 'fin_right')):
        f = flutter * math.sin(2 * math.pi * rate * t + (0.6 if s < 0 else 0.0))
        p[name] = {'rotation': [0.0, round(s * (-28.0 + 30.0 * out + 10.0 * f), 3), round(s * (12.0 * f), 3)]}
    p['back_fin'] = {'rotation': [round(-55.0 * back_fin, 3), 0.0, 0.0]}
    return p


def clips():
    out = {}

    def make(name, length, pose_at, steps_per_s=40, loop=True):
        out[name] = clip(length, pose_at, max(8, round(length * steps_per_s)), loop)

    # idle: hanging in the water, a slow small wave, the breast fins sculling to hold it still, a gentle bob
    def idle(t):
        w = 2 * math.pi / IDLE_LEN
        p = _wave({}, t, w, 0.28)
        p['body']['position'] = [0.0, round(0.25 * math.sin(w * t), 3), 0.0]
        return _fins(p, t, out=0.7, flutter=1.0, rate=2 / IDLE_LEN * 2)
    make('idle', IDLE_LEN, idle)

    # swim: the quick wave, fins tucked back, a tiny flick of the breast fins each beat
    def swim(t):
        w = 2 * math.pi / SWIM_LEN
        return _fins(_wave({}, t, w, 1.0), t, out=0.0, flutter=0.25, rate=1 / SWIM_LEN, back_fin=0.15)
    make('swim', SWIM_LEN, swim)

    # turn: played once as the fish turns hard (the game turns the fish; the clip curves the body round the turn and
    # rolls it onto its side so the flank catches the light; the game swaps in the flash look while it plays)
    def turn(side):
        def pose(t):
            k = _ease(t / (TURN_LEN * 0.3)) * (1 - _ease((t - TURN_LEN * 0.7) / (TURN_LEN * 0.3)))
            w = 2 * math.pi / (SWIM_LEN * 0.8)
            p = _wave({}, t, w, 1.0 - 0.4 * k, curl=side * 0.6 * k)
            p['body']['rotation'][2] = round(side * BANK * k, 3)
            return _fins(p, t, out=0.3 * k, flutter=0.4, rate=1 / SWIM_LEN, back_fin=0.1)
        return pose
    make('turn_left', TURN_LEN, turn(1), loop=False)
    make('turn_right', TURN_LEN, turn(-1), loop=False)

    # sip: tips its nose up to the surface, a quick gulp (the head jerks up), and down again (the game puts the fish
    # just under the surface first; the rings on the water are the game's)
    def sip(t):
        up = _ease(t / (SIP_LEN * 0.35)) * (1 - _ease((t - SIP_LEN * 0.68) / (SIP_LEN * 0.32)))
        gulp = math.sin(math.pi * min(1.0, max(0.0, (t - SIP_LEN * 0.42) / 0.16)))
        p = _wave({}, t, 2 * math.pi / IDLE_LEN, 0.35)
        p['body']['rotation'][0] = round(-28.0 * up, 3)
        p['body']['position'] = [0.0, round(2.5 * up, 3), 0.0]
        p['head']['rotation'][0] = round(-9.0 * gulp, 3)
        p['jaw'] = {'rotation': [round(JAW_OPEN * gulp, 3), 0.0, 0.0]}
        return _fins(p, t, out=0.8 * up, flutter=1.0, rate=4.0)
    make('sip', SIP_LEN, sip, loop=False)

    # nibble: nose down to the algae (strands on the bed or on a stone), held there by small tail beats and sculling
    # breast fins; three pecks: the mouth opens as the head reaches in, snaps shut on a strand and tugs back
    def nibble(t):
        k = 3 * t / NIBBLE_LEN % 1.0                     # where in the peck
        reach = math.sin(math.pi * min(1.0, k / 0.55))   # the head reaching in, then drawing back
        open_ = _ease(k / 0.3) * (1 - _ease((k - 0.38) / 0.1))   # opens as it reaches, snaps shut at the strand
        tug = math.sin(math.pi * min(1.0, max(0.0, (k - 0.48) / 0.3)))
        p = _wave({}, t, 2 * math.pi / (NIBBLE_LEN / 2), 0.4)
        p['body']['rotation'][0] = round(30.0 + 4.0 * reach - 3.0 * tug, 3)
        p['body']['position'] = [0.0, round(4.1 - 0.2 * reach, 3), round(-0.6 * reach + 0.4 * tug, 3)]   # the snout just reaches the bed
        p['head']['rotation'][0] = round(6.0 * reach - 5.0 * tug, 3)
        p['jaw'] = {'rotation': [round(JAW_OPEN * open_, 3), 0.0, 0.0]}
        return _fins(p, t, out=0.9, flutter=1.0, rate=3 / NIBBLE_LEN * 2)
    make('nibble', NIBBLE_LEN, nibble)

    # scatter: the fast start when a hunter strikes: the body snaps into a C, the tail whips back the other way (the
    # push that throws it clear), then fast beats; fins folded flat (the game shoots the fish away as it plays)
    def scatter(t):
        bend_end, snap_end = 0.08, 0.18
        if t < bend_end:
            c = _ease(t / bend_end)
        elif t < snap_end:
            c = 1 - 2 * _ease((t - bend_end) / (snap_end - bend_end))
        else:
            c = -1 * (1 - _ease((t - snap_end) / (SCATTER_LEN - snap_end)))
        settle = _ease((t - snap_end) / (SCATTER_LEN - snap_end))
        p = _wave({}, t, 2 * math.pi / (SWIM_LEN * 0.6), settle, curl=1.2 * c)
        p['body']['rotation'][2] = round(12.0 * c, 3)
        return _fins(p, t, out=0.0, back_fin=1.0 - 0.8 * settle)
    make('scatter', SCATTER_LEN, scatter, 60, loop=False)

    # flop: stranded out of water, on its side, curling and springing (the game hops it about)
    def flop(t):
        w = 2 * math.pi / FLOP_LEN
        p = _wave({}, t, w, 1.4, curl=-0.6 * (0.5 + 0.5 * math.sin(w * t)))   # curls up off the ground
        p['body']['rotation'][2] = 90.0
        p['body']['position'] = [0.0, round(-LIE + 1.2 * abs(math.sin(w * t)), 3), 0.0]
        return _fins(p, t, out=-1.7, back_fin=1.0)
    make('flop', FLOP_LEN, flop)

    # death: the wave dies away as it rolls onto its side and settles
    def death(t):
        f = _ease(t / (DEATH_LEN * 0.8))
        down = _ease((t - DEATH_LEN * 0.25) / (DEATH_LEN * 0.6))      # rolls first, then settles (the tail clear)
        p = _wave({}, t, 2 * math.pi / 0.5, 0.9 * (1 - f), curl=-0.25 * f)
        p['body']['rotation'][2] = round(90.0 * f, 3)
        p['body']['position'] = [0.0, round(-LIE * down, 3), 0.0]
        return _fins(p, t, out=-1.7 * f, back_fin=f)
    make('death', DEATH_LEN, death, loop='hold')
    return out


def WORKSHOP_CLIPS():
    return clips()


# For the versions page's clip player: the made size against the game's (0.25 blocks of the made 1.875), the water
# clips and their surface (blocks at made size), and a line about each clip.
WORKSHOP_CLIP_INFO = {
    'scale': 0.25 / (LENGTH / 16), 'water': ['idle', 'swim', 'turn_left', 'turn_right', 'sip', 'nibble', 'scatter'],
    'surface': 1.0,
    'notes': {
        'idle': 'Hanging in the water: a slow small wave, the breast fins sculling to hold it still, a gentle bob.',
        'swim': 'The quick tail beat, the wave running back along the body; the game plays it faster the faster it swims.',
        'turn_left': 'A hard turn: the body curves round and rolls its flank to the light (the flash; the game swaps in the bright look).',
        'turn_right': 'The same turn the other way.',
        'sip': 'Nose up to the surface, the mouth opens for a quick gulp, back down (dawn and dusk).',
        'nibble': 'Nose down to the algae, three pecks: the mouth opens as it reaches in, snaps shut on a strand, tugs.',
        'scatter': 'The escape when a hunter strikes: snaps into a C, the tail whips it clear, fast beats.',
        'flop': 'Stranded on land: on its side, curling and springing.',
        'death': 'The wave dies away as it rolls onto its side.',
    }}


def write_game():
    """Writes the barb's model, its two textures (everyday and the turning flash) and its clips into the savanna mod
    (no entity uses them yet: that is the plan's step 1; the files let the clip checks read the model as the game
    will)."""
    files = {}
    for look, name in (('everyday', 'barb'), ('flash', 'barb_flash')):
        bones, rows, w, h = paint_faces(model(), 2, LOOKS[look])
        files[f'textures/entity/{name}.png'] = png(rows)
    files['geo/entity/barb.geo.json'] = geo('barb', bones, w, h, (2, 1, [0, 0.4, 0]))
    files['animations/entity/barb.animation.json'] = animations(clips())
    # faces=False: its old faces until its in-game film is compared with its approved version (BUGS 245; step C,
    # 2026-11-20, proved the monitor, crocodile and fly first)
    write_assets(files, faces=False)


if __name__ == '__main__':
    if sys.argv[1:] == ['game']:
        write_game()
        sys.exit()
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
