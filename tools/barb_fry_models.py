"""The barb fry's model maker (docs/plans/prey-fish-and-frogs.md, step 2; the model workshop, stage 2: the plain 3D
shape, 2026-11-20). Built from its outline (references/outlines/barb_fry.json) and Claude's design sheet
(references/inspiration/barb_fry.png, tools/young_sheets.py): made 12 model pixels long, drawn in the game at 0.08
blocks as it hatches.

    python tools/workshop.py snapshot barb_fry --note "..."   keep this build as the next version
    python tools/workshop.py versions barb_fry                the versions page with Compare against the sheet

Units are model pixels (16 a block); the head to -Z, up +Y, left +X. The outline's x (tail tip 0 to snout 12) is
z = 6 - x here, and its y (the belly fold 0, the back fold's top 5) is kept as y.

The shape (box budget 12, as up to 30 fry can be near a breeding school): the grown barb's bone names (body, head,
tail_stalk, tail_fin, back_fin, fin_left, fin_right), so its swim, turn and scatter clips move the fry too. The head
is one box, the eyes two thin bulges on its sides (big, round and shiny, as a real fry's); the body two boxes
narrowing to the tail; the fins still folds: a low fold on the back and under the belly, a small fork for the tail.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import clip, cube, paint_faces, png, smooth   # noqa: E402

LENGTH = 12.0
HEAD_HALF = 1.25         # the head's half width (the eyes on its sides)
BELLY_HALF = 1.25        # the body's half width round the gut
REAR_HALF = 0.5          # the rear body's half width
EYE = (8.5, 10.5, 1.75, 3.75)   # the eye on the head's side: from x, to x, from y, to y (2026-11-21: a touch lower and
                                # forward, bulging, round with a ring and two shine spots: the user, "make the baby fish cuter")
EYE_BULGE = 0.5          # how far each eye stands out of the head's side
TEXELS = 4               # texture pixels a model pixel (4, so the round eye has room: 8 by 8)


def z_of(x):
    return LENGTH / 2 - x


def box(x0, x1, half, y0, y1, part, across=0.0):
    z0, z1 = sorted((z_of(x0), z_of(x1)))
    return cube([across - half, y0, z0], [across + half, y1, z1], part)


def model():
    b = {n: [] for n in ('body', 'head', 'tail_stalk', 'tail_fin', 'back_fin', 'fin_left', 'fin_right')}
    b['head'] += [box(8.0, 11.0, HEAD_HALF, 1.0, 4.0, 'head'), box(11.0, 12.0, 0.5, 1.75, 2.75, 'body')]
    for s in (1, -1):                                                         # the bulging eyes
        b['head'].append(box(EYE[0], EYE[1], EYE_BULGE / 2, EYE[2], EYE[3], 'eye',
                             across=s * (HEAD_HALF + EYE_BULGE / 2)))
    b['body'] += [box(6.0, 8.0, BELLY_HALF, 1.0, 4.0, 'body'), box(3.0, 6.0, REAR_HALF, 1.25, 3.75, 'body'),
                  box(4.0, 6.0, 0.25, 0.0, 1.25, 'fin')]                       # the belly fold
    b['back_fin'].append(box(5.0, 7.0, 0.25, 3.75, 5.0, 'fin'))
    b['tail_stalk'].append(box(2.0, 3.0, 0.5, 2.0, 3.0, 'body'))
    b['tail_fin'] += [box(1.0, 2.0, 0.25, 1.0, 4.0, 'fin'), box(0.0, 1.0, 0.25, 3.0, 5.0, 'fin'),
                      box(0.0, 1.0, 0.25, 0.0, 2.0, 'fin')]
    for s, name in ((1, 'fin_left'), (-1, 'fin_right')):                     # the breast fins, tiny paddles
        b[name].append(box(6.5, 7.5, 0.4, 1.5, 1.75, 'fin', across=s * (BELLY_HALF + 0.4)))
    mid = 2.5
    return [
        {'name': 'root', 'pivot': [0, 0, 0], 'cubes': []},
        {'name': 'body', 'parent': 'root', 'pivot': [0, mid, z_of(6.0)], 'cubes': b['body']},
        {'name': 'head', 'parent': 'body', 'pivot': [0, mid, z_of(8.0)], 'cubes': b['head']},
        {'name': 'tail_stalk', 'parent': 'body', 'pivot': [0, mid, z_of(3.0)], 'cubes': b['tail_stalk']},
        {'name': 'tail_fin', 'parent': 'tail_stalk', 'pivot': [0, mid, z_of(2.0)], 'cubes': b['tail_fin']},
        {'name': 'back_fin', 'parent': 'body', 'pivot': [0, 3.75, z_of(7.0)], 'cubes': b['back_fin']},
        {'name': 'fin_left', 'parent': 'body', 'pivot': [BELLY_HALF, 1.75, z_of(7.5)], 'cubes': b['fin_left']},
        {'name': 'fin_right', 'parent': 'body', 'pivot': [-BELLY_HALF, 1.75, z_of(7.5)], 'cubes': b['fin_right']},
    ]


# --- plain colours (the sheet's; the paint stage comes later). My pick for the game: the fry drawn see-through. -----
PLAIN = {
    'glass': (196, 210, 212), 'belly': (222, 228, 226), 'silver': (236, 240, 244), 'gut': (206, 170, 92),
    'eye': (16, 16, 18), 'dot': (104, 112, 108), 'spot': (98, 104, 110), 'fin': (160, 178, 182),
}


def plain_colour(c, face, x, y, z):
    part = c['_part']
    ox = LENGTH / 2 - z
    if part == 'fin':
        return PLAIN['fin']
    if face in ('east', 'west'):
        if part == 'head' and EYE[0] <= ox < EYE[1] and EYE[2] <= y < EYE[3]:
            return PLAIN['eye']
        if 2.0 <= ox < 4.0 and 2.0 <= y < 3.0:
            return PLAIN['spot']                       # the tail spot, just starting
        if 6.0 <= ox < 8.0 and y < 2.0:
            return PLAIN['gut']
        if 6.0 <= ox < 8.0 and y < 3.0:
            return PLAIN['silver']                     # the swim bladder over the gut
    if face == 'up' and part == 'body' and int(ox) in (4, 6) and abs(x) < 0.5:
        return PLAIN['dot']                            # the dark dots along the back
    if face == 'down' or y < 1.6:
        return PLAIN['belly']
    return PLAIN['glass']


# --- the paint (stage 4, 2026-11-21, from the sheet's colours). Kept solid here; my pick for the game: the glass, belly
# and fins drawn see-through (the gut, bladder, eyes and dots solid), as real fry are clear but for those. ----------
PAINT = {
    'glass': (196, 210, 212), 'back': (164, 178, 180), 'head_top': (150, 162, 164), 'spine': (180, 194, 196), 'belly': (222, 228, 226),
    'silver': (236, 240, 244), 'gut': (206, 170, 92), 'gut_dark': (176, 136, 64), 'eye': (16, 16, 18),
    'shine': (252, 254, 255), 'ring': (214, 198, 150), 'mouth': (120, 128, 126), 'dot': (78, 86, 84), 'spot': (98, 104, 110), 'fin': (160, 178, 182),
    'fin_edge': (140, 160, 168),
}


def eye_colour(face, ox, y):
    """The round eye on the bulge's outer face: a black pupil, a gold-silver ring, the corners the head's glass so it
    reads round, and two shine spots up and to the front (a big one and a small one)."""
    if face not in ('east', 'west'):
        return PAINT['eye']                           # the eyeball's edge: dark, so the eyes show big from the front and above
    cx, cy = (EYE[0] + EYE[1]) / 2, (EYE[2] + EYE[3]) / 2
    if cx + 0.25 <= ox < cx + 0.75 and cy + 0.25 <= y < cy + 0.75:
        return PAINT['shine']
    if cx - 0.5 <= ox < cx - 0.25 and cy - 0.5 <= y < cy - 0.25:
        return PAINT['shine']
    r = ((ox - cx) ** 2 + (y - cy) ** 2) ** 0.5
    if r < 0.85:
        return PAINT['eye']
    if r < 1.08:
        return PAINT['ring']
    return PAINT['glass']


def paint_colour(c, face, x, y, z):
    part = c['_part']
    ox = LENGTH / 2 - z
    if part == 'eye':
        return eye_colour(face, ox, y)
    if part == 'fin':
        return PAINT['fin_edge'] if (c['_bone'] == 'tail_fin' and ox < 0.5) else PAINT['fin']
    if face == 'north' and ox > LENGTH - 0.5 and abs(x) < 0.26 and y < 2.25:
        return PAINT['mouth']                         # the tiny mouth
    if face in ('east', 'west'):
        if 2.0 <= ox < 4.0 and 2.0 <= y < 3.0:
            return PAINT['spot']                      # the tail spot, just starting
        if 6.0 <= ox < 8.0 and y < 2.0:
            return PAINT['gut_dark'] if (ox < 6.5 or y < 1.5) else PAINT['gut']   # the gold gut, shaded below
        if 6.0 <= ox < 8.0 and y < 3.0:
            return PAINT['silver']                    # the swim bladder over the gut
        if 3.0 <= ox < 6.0 and 2.25 <= y < 2.75:
            return PAINT['spine']                     # the backbone showing through the clear rear body
    if face == 'up':
        if part == 'body' and int(ox) in (4, 6) and abs(x) < 0.5:
            return PAINT['dot']                       # the dark dots along the back
        if part == 'head':
            return PAINT['head_top']                  # a little pigment on the head's top
        if part == 'body':
            return PAINT['back']                      # the back a touch darker than the clear sides
    if face == 'down' and part == 'body' and int(ox) in (3, 5, 7) and abs(x) < 0.5:
        return PAINT['dot']                           # and a row under the belly
    if face == 'down' or y < 1.6:
        return PAINT['belly']
    return PAINT['glass']


LOOKS = {'painted': paint_colour, 'plain': plain_colour}
WORKSHOP_LOOK = 'painted'
WORKSHOP_SHAPE = [
    ('HEAD_HALF', 'Head half width (the eyes on its sides)', 1.0, 2.0, 0.25),
    ('BELLY_HALF', 'Body half width round the gut', 0.5, 1.5, 0.25),
    ('REAR_HALF', 'Rear body half width', 0.25, 1.0, 0.25),
]
WORKSHOP_PARTS = {'head': ['head'], 'body': ['body'], 'tail': ['tail_stalk'], 'back fin': ['back_fin'],
                  'breast fins': ['fin_left', 'fin_right']}


# --- the clips (stage 5, 2026-11-21). Fun first, from real fish fry: they hover with their breast fins fanning all the
# time (a fry's fins never rest), move in short darting bursts with a glide between, pick at specks in the water with a
# quick lunge, and scatter in a flash. Their own clips (not the grown barb's: the barb's moves are measured to its own
# body, its snout to the bed and its mouth to the surface). The game moves the fry; the clips only bend it. -----------
IDLE_LEN, SWIM_LEN, DART_LEN, PECK_LEN, SCATTER_LEN, DEATH_LEN = 1.2, 0.2, 0.9, 0.8, 0.4, 1.0
WAVE = (('body', None, 0.04, 0.9), ('head', 'body', 0.06, 0.0), ('tail_stalk', 'body', 0.30, 1.7),
        ('tail_fin', 'tail_stalk', 0.55, 2.5))
LIE = 2.5 - (HEAD_HALF + EYE_BULGE)   # how far it comes down lying on its side: its middle line to its eye's edge


def _ease(x):
    return smooth(x)


def _wave(p, t, beat, amount, curl=0.0):
    ab = {None: 0.0}
    for name, parent, a, lag in WAVE:
        ab[name] = amount * a * math.sin(beat * t - lag) + curl * a * 2.2
        p.setdefault(name, {})['rotation'] = [0.0, round(math.degrees(ab[name] - ab[parent]), 3), 0.0]
    return p


def _fins(p, t, out=0.0, flutter=0.0, rate=8.0, back_fin=0.0):
    """The breast fins held back (out 0) or spread fanning (out 1), fluttering; the back fold up (0) or flat (1)."""
    for s, name in ((1, 'fin_left'), (-1, 'fin_right')):
        f = flutter * math.sin(2 * math.pi * rate * t + (0.6 if s < 0 else 0.0))
        p[name] = {'rotation': [0.0, round(s * (-28.0 + 30.0 * out + 14.0 * f), 3), round(s * 14.0 * f, 3)]}
    p['back_fin'] = {'rotation': [round(-50.0 * back_fin, 3), 0.0, 0.0]}
    return p


def clips():
    out = {}

    def make(name, length, pose_at, steps_per_s=40, loop=True):
        out[name] = clip(length, pose_at, max(8, round(length * steps_per_s)), loop)

    # idle: hovering, the breast fins fanning fast, the tail barely moving, a tiny bob
    def idle(t):
        w = 2 * math.pi / IDLE_LEN
        p = _wave({}, t, w * 2, 0.25)
        p['body']['position'] = [0.0, round(0.15 * math.sin(w * t), 3), 0.0]
        return _fins(p, t, out=0.8, flutter=1.0, rate=6 / IDLE_LEN)
    make('idle', IDLE_LEN, idle)

    # swim: the quick wave, fins tucked (the game plays it faster the faster it swims)
    def swim(t):
        return _fins(_wave({}, t, 2 * math.pi / SWIM_LEN, 1.0), t, out=0.0, flutter=0.3, rate=1 / SWIM_LEN)
    make('swim', SWIM_LEN, swim)

    # dart: three quick hard beats, then a glide with the fins spreading to brake (fry move in short bursts)
    def dart(t):
        burst = 3 * SWIM_LEN * 0.8
        k = 1 - _ease((t - burst) / 0.25)
        p = _wave({}, t, 2 * math.pi / (SWIM_LEN * 0.8), 1.2 * k)
        return _fins(p, t, out=0.8 * (1 - k), flutter=0.6 * (1 - k), rate=6.0)
    make('dart', DART_LEN, dart, loop=False)

    # peck: a quick lunge at a speck in the water, the head snapping forward, then backing off fanning
    def peck(t):
        lunge = math.sin(math.pi * min(1.0, max(0.0, (t - 0.15) / 0.18)))
        back = math.sin(math.pi * min(1.0, max(0.0, (t - 0.33) / 0.4)))
        p = _wave({}, t, 2 * math.pi / (SWIM_LEN * 1.5), 0.6 * lunge + 0.2)
        p['body']['position'] = [0.0, 0.0, round(-0.6 * lunge + 0.3 * back, 3)]
        p['head']['rotation'][0] = round(-6.0 * lunge, 3)
        return _fins(p, t, out=0.9 - 0.7 * lunge, flutter=1.0, rate=7.0)
    make('peck', PECK_LEN, peck, loop=False)

    # scatter: snaps into a C, whips the other way, fast beats, fins flat
    def scatter(t):
        bend_end, snap_end = 0.06, 0.13
        if t < bend_end:
            c = _ease(t / bend_end)
        elif t < snap_end:
            c = 1 - 2 * _ease((t - bend_end) / (snap_end - bend_end))
        else:
            c = -1 * (1 - _ease((t - snap_end) / (SCATTER_LEN - snap_end)))
        settle = _ease((t - snap_end) / (SCATTER_LEN - snap_end))
        p = _wave({}, t, 2 * math.pi / (SWIM_LEN * 0.7), settle, curl=1.2 * c)
        p['body']['rotation'][2] = round(10.0 * c, 3)
        return _fins(p, t, out=0.0, back_fin=1.0 - 0.8 * settle)
    make('scatter', SCATTER_LEN, scatter, 60, loop=False)

    # death: the wave dies away as it rolls onto its side and settles
    def death(t):
        f = _ease(t / (DEATH_LEN * 0.8))
        down = _ease((t - DEATH_LEN * 0.25) / (DEATH_LEN * 0.6))
        p = _wave({}, t, 2 * math.pi / 0.35, 0.9 * (1 - f), curl=-0.25 * f)
        p['body']['rotation'][2] = round(90.0 * f, 3)
        p['body']['position'] = [0.0, round(-LIE * down, 3), 0.0]
        return _fins(p, t, out=-1.5 * f, back_fin=f)
    make('death', DEATH_LEN, death, loop='hold')
    return out


def WORKSHOP_CLIPS():
    return clips()


WORKSHOP_CLIP_INFO = {
    'scale': 0.08 / (LENGTH / 16), 'water': ['idle', 'swim', 'dart', 'peck', 'scatter'], 'surface': 1.0,
    'notes': {
        'idle': 'Hovering: the breast fins fanning fast, the tail barely moving, a tiny bob.',
        'swim': 'The quick tail beat, fins tucked; the game plays it faster the faster it swims.',
        'dart': 'A short burst: three hard beats, then a glide with the fins spreading to brake.',
        'peck': 'A quick lunge at a speck in the water, then backing off fanning.',
        'scatter': 'Startled: snaps into a C, whips the other way, darts off.',
        'death': 'The wave dies away as it rolls onto its side.',
    }}


def workshop_look():
    bones, rows, w, h = paint_faces(model(), TEXELS, LOOKS[WORKSHOP_LOOK])
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
