"""The Nile crocodile (M9 river life, the river's top hunter: algae -> tilapia -> catfish -> crocodile), its model for
the model viewer (python tools/model_viewer.py crocodile).

Started over on 2026-11-15 from the user's picture (a blocky Minecraft Nile crocodile on a sandy bank; the first model,
slim and finely sliced, was "fine, just not what I'm looking for"). Fun first, it only has to look good in the game
(animals are generalized, CLAUDE.md). What the picture shows, and so what this is:
- Built of cubes, one flat colour a cube face in a loose checker of olives. The first try at this (25 big cubes
  long) was "too chunky"; the user asked for an in-between of it and the old slim model, so now smaller cubes
  and more of them: 44 long, 8.5 wide, about 6 tall, long and low, with a long snout and a long tapering tail.
  Everything here is measured in those cubes ("voxels", V model pixels each).
- A big boxy head: a broad blunt snout with a raised nostril block on its tip, a taller skull with a brow block over
  each yellow, slit-pupilled eye, a few big white teeth hanging from the upper jaw, a cream lower jaw, a red mouth.
- A thick body with rows of raised square scutes down the back, a long tapering tail with a double row of scutes that
  joins into one, a tan stripe low along the side.
- Short thick sprawling legs with big feet and white claws.
- Not rigid: head, neck, chest, hips and five tail bones in a chain, the jaw its own bone, each leg three bones.

Units are model pixels (16 a block), the head points to -Z, up is +Y, left is +X; y = 0 is the ground it lies on.
"""
import copy
import random

from tilapia_models import cube, mix, shade, faces, face_point, png  # noqa: F401 (png for the viewer)

V = 2.0               # model pixels a voxel
TEXELS = 0.5          # texture pixels a model pixel: one flat colour a voxel face
SNOUT, HINGE, TAIL_END = -20.0, -10.0, 24.0     # voxels
SPLIT = 2.25          # the mouth line (voxels up)

# The swim wave (shared with the viewer): bone, parent, how far it swings, how late (radians).
WAVE = [('neck', 'body', -0.03, 0.0), ('chest', 'body', 0.02, 0.5), ('hips', 'chest', 0.05, 1.0),
        ('tail1', 'hips', 0.12, 1.6), ('tail2', 'tail1', 0.2, 2.2), ('tail3', 'tail2', 0.28, 2.8),
        ('tail4', 'tail3', 0.36, 3.4), ('tail5', 'tail4', 0.44, 4.0)]

# The trunk and tail in steps: bone, parent, z from, z to, half-width, bottom, top (voxels).
TRUNK = [('neck', 'body', -10.0, -8.5, 3.0, 0.75, 4.75), ('neck', 'body', -8.5, -7.0, 3.25, 0.75, 5.0),
         ('chest', 'body', -7.0, -5.0, 3.75, 0.75, 5.5), ('chest', 'body', -5.0, 0.0, 4.25, 0.75, 5.75),
         ('hips', 'chest', 0.0, 4.0, 4.25, 0.75, 5.75), ('hips', 'chest', 4.0, 6.0, 3.75, 0.75, 5.5),
         ('tail1', 'hips', 6.0, 8.0, 3.25, 0.75, 5.0), ('tail1', 'hips', 8.0, 10.0, 2.75, 0.75, 4.5),
         ('tail2', 'tail1', 10.0, 12.0, 2.25, 0.75, 4.0), ('tail2', 'tail1', 12.0, 14.0, 1.9, 0.8, 3.6),
         ('tail3', 'tail2', 14.0, 16.0, 1.5, 0.85, 3.2), ('tail3', 'tail2', 16.0, 18.0, 1.2, 0.9, 2.8),
         ('tail4', 'tail3', 18.0, 20.0, 0.9, 0.95, 2.45), ('tail4', 'tail3', 20.0, 22.0, 0.7, 1.0, 2.15),
         ('tail5', 'tail4', 22.0, 24.0, 0.5, 1.05, 1.85)]


def box(bones, name, x0, y0, z0, x1, y1, z1, part):
    """A box given in voxels."""
    bones[name].append(cube([x0 * V, y0 * V, z0 * V], [x1 * V, y1 * V, z1 * V], part))


def both(bones, name, x0, y0, z0, x1, y1, z1, part):
    """The same box on the left and on the right."""
    box(bones, name, x0, y0, z0, x1, y1, z1, part)
    box(bones, name, -x1, y0, z0, -x0, y1, z1, part)


def head(bones):
    # the upper jaw: a long flat snout, a little narrower at the tip, stepping up to the skull
    box(bones, 'head', -1.75, SPLIT, -20, 1.75, 3.4, -18.5, 'skin')
    box(bones, 'head', -2.0, SPLIT, -18.5, 2.0, 3.4, -15, 'skin')
    box(bones, 'head', -2.5, SPLIT, -15, 2.5, 3.75, -13, 'skin')
    box(bones, 'head', -1.25, 3.4, -20, 1.25, 4.0, -18.5, 'skin')         # the nostril block on its tip
    both(bones, 'head', 0.25, 4.0, -19.6, 0.85, 4.04, -19.0, 'nostril')
    box(bones, 'head', -1.5, 3.75, -14, 1.5, 4.25, -13, 'skin')           # the step up to the skull
    box(bones, 'head', -3.0, SPLIT, -13, 3.0, 4.75, HINGE, 'skin')        # the skull
    both(bones, 'head', 1.0, 4.75, -12.75, 3.0, 5.5, -11.0, 'brow')       # a brow block over each eye
    both(bones, 'head', 3.0, 3.75, -12.5, 3.04, 4.75, -11.25, 'iris')
    both(bones, 'head', 3.04, 3.75, -12.05, 3.07, 4.75, -11.7, 'pupil')
    # the lower jaw, hinged at the back of the skull
    box(bones, 'jaw', -1.65, 1.2, -20, 1.65, SPLIT, -18.5, 'jaw')
    box(bones, 'jaw', -1.9, 1.1, -18.5, 1.9, SPLIT, -15, 'jaw')
    box(bones, 'jaw', -2.4, 1.0, -15, 2.4, SPLIT, -13, 'jaw')
    box(bones, 'jaw', -2.9, 0.9, -13, 2.9, SPLIT, HINGE, 'jaw')
    # teeth: the upper ones hang down outside the lower jaw, the lower ones stand up inside the mouth
    for z, w in ((-19.9, 1.75), (-18.2, 2.0), (-16.6, 2.0), (-14.9, 2.5), (-13.5, 2.5)):
        both(bones, 'head', w - 0.4, SPLIT - 0.85, z, w + 0.08, SPLIT, z + 0.6, 'tooth')
    both(bones, 'head', 2.55, SPLIT - 0.85, -12.3, 3.08, SPLIT, -11.7, 'tooth')
    both(bones, 'head', 0.4, SPLIT - 0.85, -20.08, 1.0, SPLIT, -19.6, 'tooth')
    for z, w in ((-19.0, 1.65), (-17.4, 1.9), (-15.8, 1.9), (-14.2, 2.4)):
        both(bones, 'jaw', w - 0.85, SPLIT, z, w - 0.35, SPLIT + 0.75, z + 0.55, 'tooth')
    both(bones, 'jaw', 1.9, SPLIT, -12.9, 2.4, SPLIT + 0.7, -12.35, 'tooth')
    both(bones, 'jaw', 0.2, SPLIT, -19.7, 0.75, SPLIT + 0.7, -19.2, 'tooth')
    # inside the mouth, seen when it gapes: a red palate, a red floor and a paler tongue
    for z0, z1, w in ((-19.7, -15, 1.6), (-15, -13, 2.2), (-13, HINGE, 2.7)):
        box(bones, 'head', -w, SPLIT - 0.04, z0, w, SPLIT, z1, 'mouth')
        box(bones, 'jaw', -w + 0.1, SPLIT, z0, w - 0.1, SPLIT + 0.04, z1, 'mouth')
    box(bones, 'jaw', -1.0, SPLIT, -17.5, 1.0, SPLIT + 0.35, -10.4, 'tongue')


def trunk(bones):
    for name, _, z0, z1, hw, bot, top in TRUNK:
        shoulder = 1.0 if hw >= 2.0 else 0.5 if hw >= 1.0 else 0.25
        box(bones, name, -hw, bot, z0, hw, top - shoulder, z1, 'skin')             # the wide lower part
        box(bones, name, -(hw - shoulder), top - shoulder, z0, hw - shoulder, top, z1, 'skin')   # the narrower back


def scutes(bones):
    """The armour: raised square scutes in rows down the back, a double row on the tail joining into one."""
    row = 0
    z = -9.6
    while z + 1.0 <= TAIL_END:
        name, _, _, _, hw, _, top = next(t for t in TRUNK if t[2] <= z + 0.5 < t[3])
        flat = hw - (1.0 if hw >= 2.0 else 0.5 if hw >= 1.0 else 0.25)      # the half-width of the back's top
        tall = (0.7 if row % 2 == 0 else 0.5) if z < 6 else 0.9 - 0.4 * (z - 6) / 18
        if z < 6:                                       # the neck and back: two or four across
            both(bones, name, 0.2, top, z, 1.2, top + tall, z + 1.0, 'scute')
            if flat >= 2.7:
                both(bones, name, 1.7, top, z, 2.7, top + tall - 0.2, z + 1.0, 'scute')
        elif flat >= 0.9:                               # the tail's double crest
            both(bones, name, flat - 0.75, top, z, flat + 0.05, top + tall, z + 1.0, 'scute')
        else:                                           # one crest down the middle
            box(bones, name, -0.35, top, z, 0.35, top + tall, z + 1.0, 'scute')
        z += 1.5
        row += 1


def legs(out):
    """Each leg three bones (upper, lower, foot): short and sprawled out to the side, the foot flat on the ground with
    white claws."""
    for s, side in ((1, 'left'), (-1, 'right')):
        for kind, parent, z, hw, reach, thick, foot_len in (('arm', 'chest', -5.5, 3.75, 2.3, 1.7, 2.3),
                                                             ('leg', 'hips', 3.5, 4.25, 2.7, 2.2, 2.8)):
            parts = {'u': [], 'l': [], 'f': []}

            def b(key, x0, y0, z0, x1, y1, z1, part):
                lo, hi = sorted([s * x0, s * x1])
                parts[key].append(cube([lo * V, y0 * V, z0 * V], [hi * V, y1 * V, z1 * V], part))

            elbow = hw + reach
            b('u', hw - 0.4, 1.5, z - thick / 2, elbow, 1.5 + thick, z + thick / 2, 'leg')
            b('l', elbow - 0.75, 0.4, z - thick / 2 - 0.5, elbow + 0.75, 1.5 + thick * 0.75, z + thick / 2 - 0.5, 'leg')
            front = z - thick / 2 - 0.3 - foot_len
            b('f', elbow - 1.0, 0.0, front, elbow + 1.0, 0.6, z - thick / 2 + 0.6, 'foot')
            for i in range(4):
                x = elbow - 0.95 + i * 0.5
                b('f', x, 0.0, front - 0.55, x + 0.4, 0.4, front, 'claw')
            sh = [s * hw * V, (1.5 + thick / 2) * V, z * V]
            out.append({'name': f'{kind}_upper_{side}', 'parent': parent, 'pivot': sh, 'cubes': parts['u']})
            out.append({'name': f'{kind}_lower_{side}', 'parent': f'{kind}_upper_{side}',
                        'pivot': [s * elbow * V, sh[1], z * V], 'cubes': parts['l']})
            out.append({'name': f'{kind}_foot_{side}', 'parent': f'{kind}_lower_{side}',
                        'pivot': [s * elbow * V, 0.5 * V, (z - thick / 2) * V], 'cubes': parts['f']})


def model():
    names = ['head', 'jaw'] + list(dict.fromkeys(t[0] for t in TRUNK))
    bones = {name: [] for name in names}
    head(bones)
    trunk(bones)
    scutes(bones)
    out = [{'name': 'body', 'pivot': [0, 3.0 * V, 0.0]},
           {'name': 'neck', 'parent': 'body', 'pivot': [0, 2.5 * V, -7.0 * V], 'cubes': bones['neck']},
           {'name': 'head', 'parent': 'neck', 'pivot': [0, SPLIT * V, HINGE * V], 'cubes': bones['head']},
           {'name': 'jaw', 'parent': 'head', 'pivot': [0, SPLIT * V, HINGE * V], 'cubes': bones['jaw']}]
    done = {'neck'}
    for name, parent, z0, *_ in TRUNK:
        if name not in done:
            done.add(name)
            out.append({'name': name, 'parent': parent, 'pivot': [0, 3.0 * V, z0 * V], 'cubes': bones[name]})
    legs(out)
    return out


# --- colours -----------------------------------------------------------------------------------------------------------
LOOKS = {'A': {'name': 'Nile crocodile', 'note': "your picture: a dark olive back with raised darker scutes, sides in a "
               "loose checker of olive and bronze, a tan stripe low along the side, a cream lower jaw and belly, white "
               "teeth and claws, a yellow eye with a black slit, a red mouth"}}
DARK, OLIVE, LIGHT, BRONZE, TAN, CREAM = ((58, 62, 30), (88, 92, 42), (120, 120, 54), (150, 136, 66), (200, 176, 112),
                                          (226, 208, 152))


def dice(a, b, c):
    """0 to 1, the same for the same voxel every time."""
    n = (int(a) * 374761393 + int(b) * 668265263 + int(c) * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) / 4294967296.0


def section(zv):
    if zv < HINGE:
        return 1.0, 4.75
    for _, _, z0, z1, _, bot, top in TRUNK:
        if zv < z1:
            return bot, top
    return TRUNK[-1][5], TRUNK[-1][6]


def pick(colours, r):
    return colours[min(len(colours) - 1, int(r * len(colours)))]


def colour(part, look, x, y, z, face, rnd):
    """One flat colour a voxel face: the voxel is found from the point (model pixels), and its dice pick the colour."""
    import math
    xv, yv, zv = x / V, y / V, z / V
    cx, cy, cz = math.floor(xv + 1e-4), math.floor(yv * 2 + 1e-4), math.floor(zv + 1e-4)
    if face in ('up', 'down'):
        r = dice(cx, 77, cz)
    elif face in ('east', 'west'):
        r = dice(55, cy, cz)
    else:
        r = dice(cx, cy, 33)
    grain = rnd.uniform(0.98, 1.02)
    band = cz % 6 < 2 and zv > -7            # dark bands across the back and tail
    if part == 'tooth' or part == 'claw':
        return shade((240, 234, 214), grain)
    if part == 'nostril' or part == 'pupil':
        return (20, 18, 14)
    if part == 'iris':
        return shade((236, 200, 36), grain)
    if part == 'mouth':
        return shade(pick([(168, 70, 62), (186, 84, 72), (176, 76, 66)], r), grain)
    if part == 'tongue':
        return shade(pick([(214, 132, 116), (204, 120, 106)], r), grain)
    if part == 'jaw':
        c = CREAM if face == 'down' else pick([CREAM, TAN, CREAM, mix(TAN, CREAM, 0.5)], r)
        return shade(c, grain)
    if part in ('scute', 'brow'):
        c = pick([DARK, DARK, mix(DARK, OLIVE, 0.6), OLIVE], r)
        if face != 'up':
            c = shade(c, 0.9)
        return shade(c, (0.85 if band and part == 'scute' else 1.0) * grain)
    if part in ('leg', 'foot'):
        if face == 'down':
            c = CREAM
        elif part == 'foot':
            c = pick([BRONZE, LIGHT, mix(BRONZE, TAN, 0.5)], r)
        else:
            c = pick([OLIVE, LIGHT, OLIVE, BRONZE, mix(DARK, OLIVE, 0.5)], r)
        return shade(c, grain)
    # the skin of the head, body and tail, by how high up the side it is
    bot, top = section(zv)
    f = (yv - bot) / max(0.1, top - bot)
    if face == 'down':
        c = CREAM
    elif face == 'up' and f > 0.6:
        c = pick([DARK, mix(DARK, OLIVE, 0.5), OLIVE, DARK], r)
    elif f < 0.26:
        c = pick([TAN, CREAM, TAN, mix(TAN, BRONZE, 0.4)], r)
    elif f < 0.5:
        c = pick([BRONZE, LIGHT, mix(BRONZE, TAN, 0.4), OLIVE], r)
    else:
        c = pick([OLIVE, LIGHT, OLIVE, mix(DARK, OLIVE, 0.5), DARK], r)
    if band and f >= 0.5:
        c = shade(c, 0.8)
    return shade(c, grain)


def paint(bones, look='A'):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
    rnd = random.Random(23)
    bones = copy.deepcopy(bones)
    patches = []
    for b in bones:
        for c in b.get('cubes', []):
            c['uv'] = {}
            for face, (w, h) in faces(c).items():
                patches.append((max(1, round(w * TEXELS)), max(1, round(h * TEXELS)), c, face))
    patches.sort(key=lambda p: (-p[1], -p[0]))
    for width in (64, 128, 256, 512, 1024):
        x = y = shelf = 0
        places = []
        for w, h, c, face in patches:
            if x + w > width:
                x, y, shelf = 0, y + shelf, 0
            places.append((x, y, w, h, c, face))
            x, shelf = x + w, max(shelf, h)
        if y + shelf <= width:
            break
    height = 16
    while height < y + shelf:
        height *= 2
    rows = [[(0, 0, 0, 0)] * width for _ in range(height)]
    for x0, y0, w, h, c, face in places:
        c['uv'][face] = {'uv': [x0, y0], 'uv_size': [w, h]}
        part = c['_part']
        for jj in range(h):
            for ii in range(w):
                px, py, pz = face_point(c, face, (ii + 0.5) / w, (jj + 0.5) / h)
                rgb = colour(part, look, px, py, pz, face, rnd)
                rows[y0 + jj][x0 + ii] = tuple(max(0, min(255, int(v))) for v in rgb) + (255,)
    for b in bones:
        for c in b.get('cubes', []):
            c.pop('_part')
    return bones, rows, width, height


if __name__ == '__main__':
    bones = model()
    n = sum(len(b.get('cubes', [])) for b in bones)
    _, rows, w, h = paint(bones)
    print(f'{n} boxes, texture {w} x {h}')
