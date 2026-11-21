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
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import clip, cube, curve, paint_faces, png, smooth   # noqa: E402

LENGTH = 18.0
# (x, top, belly, half width) along the body, eased between: the tail's root, the widest middle, the rounded snout.
# Read off the outline (body rear y 2-6 half 1.5, body 1-6 half 2.5, snout 2-5 half 1.5), rounded.
# 2026-11-21, v5: slimmer and lower (the user: "the tadpole looks too bulbous compared to the 2D render ... I want it to
# be cute as well"): about a fifth less deep and wide, so it flows into the tail's root as on the sheet.
PROFILE = ((11.0, 5.0, 2.2, 1.4), (12.0, 5.4, 1.6, 1.9), (13.5, 5.6, 1.4, 2.2), (15.0, 5.6, 1.4, 2.2),
           (16.5, 5.3, 1.7, 1.9), (17.5, 4.8, 2.2, 1.5), (18.0, 4.5, 2.5, 1.1))
CORE_WIDTH = 0.55        # the tall box's share of the width
FLANK_DEPTH = 0.6        # the wide box's share of the depth
BODY_FROM = 11.0
EYE = (14.0, 16.0, 3.75, 4.75)      # the eye high on the flank (the sheet's: black at 15-16, a gold patch behind
                                    # at 14-15; the user, 2026-11-21: "looks better with the eyes in the 2D image")
TEXELS = 4               # texture pixels a model pixel (4, so the round eye has room)
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


# --- the paint (stage 4, 2026-11-21, from the sheet's colours) --------------------------------------------------------
PAINT = {
    'body': (50, 50, 40), 'fleck': (80, 78, 58), 'gold': (176, 150, 80), 'eye': (10, 10, 10), 'ring': (196, 168, 88),
    'belly': (104, 106, 96), 'belly_fleck': (128, 128, 116), 'gut': (66, 62, 50), 'beak': (28, 26, 22),
    'lip': (150, 140, 116), 'muscle': (64, 62, 50), 'mottle': (94, 90, 70), 'fin': (134, 142, 132),
    'fin_speck': (84, 90, 84), 'leg': (70, 70, 52), 'toe': (46, 46, 36),
}


def noise(x, y, z, seed):
    """A steady 0-1 number for each texture pixel, so the flecks stay put from build to build."""
    ix, iy, iz = (int(round(v * 4)) for v in (x, y, z))
    return ((ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)) % 1000 / 1000


def paint_colour(c, face, x, y, z):
    part = c['_part']
    ox = LENGTH / 2 - z
    n = noise(x, y, z, 1)
    if part == 'fin':
        return PAINT['fin_speck'] if n < 0.14 else PAINT['fin']          # clear fins, dark speckles
    if part == 'muscle':
        return PAINT['mottle'] if n < 0.3 else PAINT['muscle']           # the tail's mottled muscle
    if part == 'leg':
        if ox < 8.0:
            return PAINT['toe']                                           # the foot's dark toe tips
        return PAINT['fleck'] if n < 0.25 else PAINT['leg']
    if face in ('east', 'west') and EYE[0] <= ox < EYE[1] and EYE[2] <= y < EYE[3]:
        return PAINT['eye'] if ox >= EYE[0] + 1.0 else PAINT['ring']    # the sheet's eye: black, a gold patch behind
    if face == 'down':
        if ox > LENGTH - 1.5 and abs(x) < 1.0:
            return PAINT['beak'] if abs(x) < 0.5 else PAINT['lip']       # the mouth underneath at the front
        r = ((ox - 13.75) ** 2 + (x * 1.1) ** 2) ** 0.5
        if r < 0.35 or 0.7 < r < 1.15 or 1.5 < r < 1.85 and ox < 13.75:
            return PAINT['gut']                                           # the coiled gut showing through
        return PAINT['belly_fleck'] if n < 0.2 else PAINT['belly']
    if y < 1.75:
        return PAINT['belly']
    if face in ('east', 'west') and 13.5 <= ox < 15.5 and y < 2.5:
        return PAINT['gut']                                               # the gut's edge seen from the side
    if n < 0.05 and face != 'north' and (face == 'up' or y >= 3.0):
        return PAINT['gold']                                              # the gold flecks
    return PAINT['fleck'] if n < 0.2 else PAINT['body']


LOOKS = {'painted': paint_colour, 'plain': plain_colour}
WORKSHOP_LOOK = 'painted'
WORKSHOP_SHAPE = [
    ('CORE_WIDTH', 'Back and belly ridge width (share of the full width)', 0.3, 0.9, 0.05),
    ('FLANK_DEPTH', 'Round flank depth (share of the full depth)', 0.4, 0.9, 0.05),
    ('LEGS', 'Day-3 hind legs (0 hidden, 1 shown)', 0.0, 1.0, 1.0),
]
WORKSHOP_PARTS = {'body': ['body'], 'tail root': ['tail_1'], 'tail middle': ['tail_2'], 'tail tip': ['tail_3'],
                  'hind legs': ['leg_left', 'leg_right']}


# --- the clips (stage 5, 2026-11-21). Fun first, from real tadpoles: they wobble, the whole body rocking side to side
# as the tail beats (a tadpole's swim is famously wobbly); they hang still with only the tail tip quivering; they graze
# nose down, rasping algae off the bed with small quick bobs; bullfrog tadpoles rise to gulp air; a startled one bends
# into a C and darts. The legs (day 3) trail along the tail and paddle a little. The game moves the tadpole; the clips
# only bend it. -------------------------------------------------------------------------------------------------------
IDLE_LEN, SWIM_LEN, GRAZE_LEN, GULP_LEN, SCATTER_LEN, FLOP_LEN, DEATH_LEN = 2.0, 0.3, 1.6, 1.2, 0.5, 0.5, 1.2
# the wave: bone, its parent, how far it swings (radians, against the tadpole's line), how late (radians of the beat).
# The body swings a lot for its size (the wobble); the tail's tip most of all.
WAVE = (('body', None, 0.10, 0.0), ('tail_1', 'body', 0.30, 1.0), ('tail_2', 'tail_1', 0.55, 1.9),
        ('tail_3', 'tail_2', 0.85, 2.8))
SNOUT_DROP = 0.84        # how far the body comes down grazing nose down 25 degrees, so the mouth just meets the bed
GULP_RISE = 5.5          # how far the body rises as it tips 35 degrees nose up, so the tail's lower fin stays over its
                         # lowest point at rest (measured: the tail tip's fin drops about 5 pixels)
LIE = 3.5 - 2.2          # how far it comes down lying on its side: its middle line (3.5 up) to its half width


def _ease(x):
    return smooth(x)


def _wave(p, t, beat, amount, curl=0.0):
    """The swim wave at t: each bone's swing, its parent's taken off (curl bends every bone one way as well)."""
    ab = {None: 0.0}
    for name, parent, a, lag in WAVE:
        ab[name] = amount * a * math.sin(beat * t - lag) + curl * a * 2.0
        p.setdefault(name, {})['rotation'] = [0.0, round(math.degrees(ab[name] - ab[parent]), 3), 0.0]
    return p


def _legs(p, t, paddle=0.0, rate=2.0):
    """The hind legs (day 3): held back along the tail's root, trailing its swing, paddling a little (paddle 0 to 1)."""
    trail = -0.6 * p['tail_1']['rotation'][1]
    for s, name in ((1, 'leg_left'), (-1, 'leg_right')):
        k = paddle * math.sin(2 * math.pi * rate * t + (math.pi if s < 0 else 0.0))
        p[name] = {'rotation': [round(12.0 * k, 3), round(trail + s * 6.0 * k, 3), 0.0]}
    return p


def clips():
    out = {}

    def make(name, length, pose_at, steps_per_s=40, loop=True):
        out[name] = clip(length, pose_at, max(8, round(length * steps_per_s)), loop)

    # idle: hanging in the water, the body still, only the tail's back half quivering, a slow small bob
    def idle(t):
        w = 2 * math.pi / IDLE_LEN
        p = _wave({}, t, 2 * math.pi / 0.5, 0.12)
        p['body']['position'] = [0.0, round(0.2 * math.sin(w * t), 3), 0.0]
        return _legs(p, t, paddle=0.15, rate=1 / IDLE_LEN)
    make('idle', IDLE_LEN, idle)

    # swim: the quick wobbly wave (the game plays it faster the faster it swims)
    def swim(t):
        return _legs(_wave({}, t, 2 * math.pi / SWIM_LEN, 1.0), t, paddle=0.3, rate=1 / SWIM_LEN)
    make('swim', SWIM_LEN, swim)

    # graze: nose down on the bed, rasping: small quick bobs of the mouth, the tail fanning slowly to hold it there
    def graze(t):
        rasp = math.sin(2 * math.pi * 6 / GRAZE_LEN * t)
        p = _wave({}, t, 2 * math.pi / (GRAZE_LEN / 2), 0.35)
        p['body']['rotation'][0] = round(25.0 + 2.5 * rasp, 3)
        p['body']['position'] = [0.0, round(-SNOUT_DROP + 0.12 * (1 - rasp), 3), round(-0.1 * rasp, 3)]
        return _legs(p, t, paddle=0.2, rate=2 / GRAZE_LEN)
    make('graze', GRAZE_LEN, graze)

    # gulp: at the surface, nose tips up, a quick gulp of air (a little jerk), then back down, the tail beating hard
    # at the start to push it up (the game lifts it to the surface first)
    def gulp(t):
        up = _ease(t / (GULP_LEN * 0.3)) * (1 - _ease((t - GULP_LEN * 0.65) / (GULP_LEN * 0.35)))
        g = math.sin(math.pi * min(1.0, max(0.0, (t - GULP_LEN * 0.38) / 0.14)))
        p = _wave({}, t, 2 * math.pi / (SWIM_LEN * 1.4), 0.8 * (1 - 0.6 * up))
        p['body']['rotation'][0] = round(-35.0 * up - 6.0 * g, 3)
        p['body']['position'] = [0.0, round(GULP_RISE * up + 0.5 * g, 3), 0.0]   # rises as it tips, the tail clear
        return _legs(p, t, paddle=0.3, rate=1 / SWIM_LEN)
    make('gulp', GULP_LEN, gulp, loop=False)

    # scatter: startled: the body snaps into a C, whips the other way, then fast beats
    def scatter(t):
        bend_end, snap_end = 0.07, 0.16
        if t < bend_end:
            c = _ease(t / bend_end)
        elif t < snap_end:
            c = 1 - 2 * _ease((t - bend_end) / (snap_end - bend_end))
        else:
            c = -1 * (1 - _ease((t - snap_end) / (SCATTER_LEN - snap_end)))
        settle = _ease((t - snap_end) / (SCATTER_LEN - snap_end))
        p = _wave({}, t, 2 * math.pi / (SWIM_LEN * 0.6), settle, curl=1.0 * c)
        return _legs(p, t, paddle=0.0)
    make('scatter', SCATTER_LEN, scatter, 60, loop=False)

    # flop: stranded, on its side, wriggling and flipping its tail (the game hops it about)
    def flop(t):
        w = 2 * math.pi / FLOP_LEN
        p = _wave({}, t, w, 1.5, curl=-0.5 * (0.5 + 0.5 * math.sin(w * t)))
        p['body']['rotation'][2] = 90.0
        p['body']['position'] = [0.0, round(-LIE + 0.8 * abs(math.sin(w * t)), 3), 0.0]
        return _legs(p, t, paddle=0.8, rate=2 / FLOP_LEN)
    make('flop', FLOP_LEN, flop)

    # death: the wave dies away as it rolls over belly up and settles
    def death(t):
        f = _ease(t / (DEATH_LEN * 0.8))
        p = _wave({}, t, 2 * math.pi / 0.4, 0.9 * (1 - f), curl=-0.2 * f)
        p['body']['rotation'][2] = round(180.0 * f, 3)
        return _legs(p, t, paddle=0.0)
    make('death', DEATH_LEN, death, loop='hold')
    return out


def WORKSHOP_CLIPS():
    return clips()


# For the versions page's clip player: the made size against the game's (0.15 blocks of the made 18 pixels), the
# water clips and a line about each.
WORKSHOP_CLIP_INFO = {
    'scale': 0.15 / (LENGTH / 16), 'water': ['idle', 'swim', 'graze', 'gulp', 'scatter'], 'surface': 1.0,
    'notes': {
        'idle': 'Hanging in the water: the body still, only the tail tip quivering, a slow small bob.',
        'swim': 'The wobbly swim: the wave runs down the three tail pieces and rocks the whole body side to side.',
        'graze': 'Nose down on the bed, rasping algae: small quick bobs of the mouth, the tail fanning slowly.',
        'gulp': 'At the surface: nose up for a quick gulp of air, then back down.',
        'scatter': 'Startled: snaps into a C, whips the other way, darts off.',
        'flop': 'Stranded on land: on its side, wriggling.',
        'death': 'The wave dies away as it rolls belly up.',
    }}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), TEXELS, LOOKS[WORKSHOP_LOOK])
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
    LEGS = 1.0
    print('with legs:', sum(len(b['cubes']) for b in model()), 'boxes')
