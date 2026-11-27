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
import math  # noqa: E402
from model_parts import animations, clip, cube, curve, geo, paint_faces, png, smooth, write_assets   # noqa: E402

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


# --- the paint (the workshop's paint stage, 2026-11-23, the river tie-up; my picks from the sheet) ----------------------
# Near black on top with a faint olive sheen and darker freckles; a grey belly with the coiled gut showing gold-brown
# through it; the eye a black pupil in a thin gold ring; the fins clear grey-green, freckled black; the leg buds the
# body's colour. At 0.15 blocks the game shows little more than a dark comma with a pale wisp of fin.
def _speck(x, y, z, every=5):
    """A steady freckle pattern: true at about one pixel in every (from the pixel's place, so it does not flicker)."""
    h = (int(math.floor(x * 2)) * 73856093) ^ (int(math.floor(y * 2)) * 19349663) ^ (int(math.floor(z * 2)) * 83492791)
    return h % every == 0


def paint(c, face, x, y, z):
    part = c['_part']
    ox = LENGTH / 2 - z
    if part == 'fin':
        return (22, 24, 20) if _speck(x, y, z, 6) else (128, 138, 126)
    if part == 'muscle':
        return (34, 34, 28) if _speck(x, y, z, 4) else (58, 58, 46)
    if part == 'leg':
        return (54, 54, 40)
    if face in ('east', 'west') and EYE[0] <= ox < EYE[1] and EYE[2] <= y < EYE[3]:
        return (8, 8, 8) if ox < EYE[0] + 0.5 else (184, 156, 82)
    if face == 'down':
        if ox > LENGTH - 1.5 and abs(x) < 1.0:
            return (24, 22, 18) if abs(x) < 0.5 else (156, 146, 120)        # the beak and its pale lips
        if 12.0 <= ox <= 15.5 and abs(x) < 1.5:
            return (132, 104, 60) if _speck(x, y, z, 3) else (112, 112, 100)   # the coiled gut through the belly
        return (108, 110, 100)
    if y < 1.75:
        return (100, 102, 92)
    return (20, 20, 16) if _speck(x, y, z, 5) else (44, 46, 34)


LOOKS = {'plain': plain_colour, 'paint': paint}
WORKSHOP_LOOK = 'paint'
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


# --- the clips (the workshop's animation stage, 2026-11-23; fun first, inspired by real tadpoles) ----------------------
# A tadpole swims all with its tail: a fast wave running down it, the tip swinging most, the body barely rocking; it
# hangs in the water with the tail tip shivering; it grazes nose down on the algae, rasping, the tail fanning to hold
# it there; frightened it snaps into a C and darts. Each clip adds a bone's turn to its rest, in the model file's
# degrees: y the sideways swing, x the pitch.
SWIM_LEN = 0.3          # one tail beat at its cruising speed (the game plays it faster or slower with the speed)
IDLE_LEN = 1.2
GRAZE_LEN = 1.0
SCATTER_LEN = 0.3
FLOP_LEN = 0.4
FLOP_CURL, FLOP_LIFT = -0.6, 2.8
DEATH_LEN = 1.0
# the wave down the tail: bone, its parent, how far it swings (radians, against the line), how late (radians)
WAVE = (('body', None, 0.05, 0.0), ('tail_1', 'body', 0.22, 0.9), ('tail_2', 'tail_1', 0.42, 1.8),
        ('tail_3', 'tail_2', 0.62, 2.7))


def _wave(p, t, beat, amount, curl=0.0):
    ab = {None: 0.0}
    for name, parent, a, lag in WAVE:
        ab[name] = amount * a * math.sin(beat * t - lag) + curl * a * 2.0
        p.setdefault(name, {})['rotation'] = [0.0, round(math.degrees(ab[name] - ab[parent]), 3), 0.0]
    return p


def _legs(p, kick=0.0):
    """The day-3 leg buds trail back along the tail, kicking a little with the beat."""
    for s, name in ((1, 'leg_left'), (-1, 'leg_right')):
        p[name] = {'rotation': [round(6.0 * kick, 3), round(s * 8.0, 3), 0.0]}
    return p


def clips():
    out = {}

    def make(name, length, pose_at, loop=True):
        out[name] = clip(length, pose_at, max(8, round(length * 40)), loop)

    # idle: hanging still in the water, the tail tip shivering, the body bobbing a hair
    def idle(t):
        w = 2 * math.pi / IDLE_LEN
        p = _wave({}, t, w * 3, 0.25)
        p['body']['position'] = [0.0, round(0.2 * math.sin(w * t), 3), 0.0]
        return _legs(p)
    make('idle', IDLE_LEN, idle)

    # swim: the fast wave down the tail
    def swim(t):
        w = 2 * math.pi / SWIM_LEN
        return _legs(_wave({}, t, w, 1.0), math.sin(w * t))
    make('swim', SWIM_LEN, swim)

    # graze: nose down to the algae (pitched 35 degrees), the mouth rasping (quick small nods), the tail fanning
    # slowly to hold it in place
    def graze(t):
        w = 2 * math.pi / GRAZE_LEN
        p = _wave({}, t, w * 2, 0.45)
        nod = 4.0 * max(0.0, math.sin(w * 4 * t))
        p['body']['rotation'][0] = round(35.0 + nod, 3)
        p['tail_1']['rotation'][0] = -20.0          # the tail lifts back toward level behind the dipped body
        return _legs(p)
    make('graze', GRAZE_LEN, graze)

    # scatter: the fright: snaps into a C, then whips straight with fast beats
    def scatter(t):
        f = t / SCATTER_LEN
        snap = smooth(f / 0.15) if f < 0.15 else 1.0 - smooth(min(1.0, (f - 0.15) / 0.3))
        return _legs(_wave({}, t, 2 * math.pi / 0.15, 1.0 * (1.0 - snap) + 0.2, curl=snap * 0.9))
    make('scatter', SCATTER_LEN, scatter, loop=False)
    # struggle: thrashing in a hunter's jaws: the scatter round and round, its own clip (code review R12-32: the game
    # looped "scatter", whose file says it plays once)
    make('struggle', SCATTER_LEN, scatter)

    # flop: out of the water, on its side, curling and springing
    # (lying on its side its sideways swing goes up and down, so the curl only lifts the tail off the ground: FLOP_CURL's
    # sign is the one that bends it up; the middle line raised its half width, FLOP_LIFT, so the flank rests on it)
    def flop(t):
        w = 2 * math.pi / FLOP_LEN
        p = _wave({}, t, w, 0.15, curl=FLOP_CURL * (0.55 + 0.45 * math.sin(w * t)))
        p['root'] = {'rotation': [0.0, 0.0, 90.0], 'position': [0.0, FLOP_LIFT, 0.0]}
        return _legs(p)
    make('flop', FLOP_LEN, flop)

    # death: the beat dies away, it rolls belly up and the tail sags
    def death(t):
        f = smooth(min(1.0, t / DEATH_LEN))
        p = _wave({}, t, 2 * math.pi / 0.3, 1.0 - f)
        p['root'] = {'rotation': [0.0, 0.0, round(180.0 * f, 3)], 'position': [0.0, round(7.0 * f, 3), 0.0]}
        p['tail_2']['rotation'][0] = round(10.0 * f, 3)
        return _legs(p)
    make('death', DEATH_LEN, death, loop='hold')
    return out


def WORKSHOP_CLIPS():
    return clips()


WORKSHOP_CLIP_INFO = {
    'scale': 0.15 / (LENGTH / 16), 'water': ['idle', 'swim', 'graze', 'scatter', 'struggle', 'death'], 'surface': 0.5,
    'notes': {
        'idle': 'Hanging in the water, the tail tip shivering.',
        'swim': 'The fast wave down the tail; the game plays it faster the faster it swims.',
        'graze': 'Nose down on the algae, rasping, the tail fanning to hold it there.',
        'scatter': 'Frightened: it snaps into a C and whips away.',
        'flop': 'Out of the water: on its side, curling and springing.',
        'death': 'The beat dies away; it rolls belly up.',
    }}


def write_game():
    """Writes the tadpole's model (the leg buds in: the game hides them until day 3), texture and clips into the
    savanna mod."""
    global LEGS
    keep, LEGS = LEGS, 1.0
    bones, rows, w, h = paint_faces(model(), 2, paint)
    LEGS = keep
    write_assets({'textures/entity/tadpole.png': png(rows),
                  'geo/entity/tadpole.geo.json': geo('tadpole', bones, w, h, (1, 1, [0, 0.3, 0])),
                  'animations/entity/tadpole.animation.json': animations(clips())})


if __name__ == '__main__':
    if sys.argv[1:] == ['game']:
        write_game()
        sys.exit()
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
    LEGS = 1.0
    print('with legs:', sum(len(b['cubes']) for b in model()), 'boxes')
