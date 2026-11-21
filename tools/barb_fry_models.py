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
is one box as wide as the eyes, the eyes painted on its sides (big and dark, as a real fry's); the body two boxes
narrowing to the tail; the fins still folds: a low fold on the back and under the belly, a small fork for the tail.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import cube, paint_faces, png   # noqa: E402

LENGTH = 12.0
HEAD_HALF = 1.25         # the head's half width (the eyes on its sides)
BELLY_HALF = 1.25        # the body's half width round the gut
REAR_HALF = 0.5          # the rear body's half width
EYE = (8.0, 10.0, 2.0, 4.0)   # the eye on the head's side: from x, to x, from y, to y


def z_of(x):
    return LENGTH / 2 - x


def box(x0, x1, half, y0, y1, part, across=0.0):
    z0, z1 = sorted((z_of(x0), z_of(x1)))
    return cube([across - half, y0, z0], [across + half, y1, z1], part)


def model():
    b = {n: [] for n in ('body', 'head', 'tail_stalk', 'tail_fin', 'back_fin', 'fin_left', 'fin_right')}
    b['head'] += [box(8.0, 11.0, HEAD_HALF, 1.0, 4.0, 'head'), box(11.0, 12.0, 0.5, 2.0, 3.0, 'body')]
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
    'glint': (200, 214, 222), 'dot': (78, 86, 84), 'spot': (98, 104, 110), 'fin': (160, 178, 182),
    'fin_edge': (140, 160, 168),
}


def paint_colour(c, face, x, y, z):
    part = c['_part']
    ox = LENGTH / 2 - z
    if part == 'fin':
        return PAINT['fin_edge'] if (c['_bone'] == 'tail_fin' and ox < 0.5) else PAINT['fin']
    if face in ('east', 'west'):
        if part == 'head' and EYE[0] <= ox < EYE[1] and EYE[2] <= y < EYE[3]:
            return PAINT['glint'] if (ox >= EYE[1] - 0.5 and y >= EYE[3] - 0.5) else PAINT['eye']   # one bright glint
        if 2.0 <= ox < 4.0 and 2.0 <= y < 3.0:
            return PAINT['spot']                      # the tail spot, just starting
        if 6.0 <= ox < 8.0 and y < 2.0:
            return PAINT['gut_dark'] if (ox < 6.5 or y < 1.5) else PAINT['gut']   # the gold gut, shaded below
        if 6.0 <= ox < 8.0 and y < 3.0:
            return PAINT['silver']                    # the swim bladder over the gut
        if 3.0 <= ox < 6.0 and 2.25 <= y < 2.75:
            return PAINT['spine']                     # the backbone showing through the clear rear body
    if face == 'up' and part == 'head' and abs(x) > HEAD_HALF - 0.5 and EYE[0] <= ox < EYE[1]:
        return PAINT['eye']                           # the big eyes bulge, so they show from above and the front too
    if face == 'north' and part == 'head' and abs(x) > HEAD_HALF - 0.5 and EYE[2] <= y < EYE[3]:
        return PAINT['eye']
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


def workshop_look():
    bones, rows, w, h = paint_faces(model(), 2, LOOKS[WORKSHOP_LOOK])
    return bones, png(rows), w, h


if __name__ == '__main__':
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
