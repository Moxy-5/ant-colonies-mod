"""The Nile crocodile (M9 river life, the river's top hunter: algae -> tilapia -> catfish -> crocodile), its model for
the model viewer (python tools/model_viewer.py crocodile).

Started over on 2026-11-15 from the user's picture (a blocky Minecraft Nile crocodile on a sandy bank; the first model,
slim and finely sliced, was "fine, just not what I'm looking for"). Fun first, it only has to look good in the game
(animals are generalized, CLAUDE.md). What the picture shows, and so what this is:
- Chunky and built of big cubes: about 25 cubes long, 6 wide, 4 tall. Everything here is measured in those cubes
  ("voxels", V model pixels each), and the texture is one flat colour a cube face, in a loose checker of olives.
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
TEXELS = 1.0          # texture pixels a model pixel (so a voxel face is 2 x 2 texture pixels of one colour)
SNOUT, HINGE, NECK, TAIL_END = -12.0, -6.0, -6.0, 13.0     # voxels
SPLIT = 1.5           # the mouth line (voxels up)

# The swim wave (shared with the viewer): bone, parent, how far it swings, how late (radians).
WAVE = [('neck', 'body', -0.03, 0.0), ('chest', 'body', 0.02, 0.5), ('hips', 'chest', 0.05, 1.0),
        ('tail1', 'hips', 0.12, 1.6), ('tail2', 'tail1', 0.2, 2.2), ('tail3', 'tail2', 0.28, 2.8),
        ('tail4', 'tail3', 0.36, 3.4), ('tail5', 'tail4', 0.44, 4.0)]

# The trunk and tail: bone, parent, z from, z to, half-width, bottom, top (voxels).
TRUNK = [('neck', 'body', -6.0, -4.0, 2.0, 0.5, 3.25),
         ('chest', 'body', -4.0, -2.5, 2.5, 0.5, 3.75), ('chest', 'body', -2.5, 0.0, 3.0, 0.5, 4.0),
         ('hips', 'chest', 0.0, 2.0, 3.0, 0.5, 4.0), ('hips', 'chest', 2.0, 3.5, 2.5, 0.5, 3.75),
         ('tail1', 'hips', 3.5, 5.5, 2.0, 0.5, 3.25), ('tail2', 'tail1', 5.5, 7.5, 1.5, 0.5, 2.75),
         ('tail3', 'tail2', 7.5, 9.5, 1.0, 0.6, 2.25), ('tail4', 'tail3', 9.5, 11.5, 0.75, 0.7, 1.8),
         ('tail5', 'tail4', 11.5, 13.0, 0.5, 0.8, 1.45)]


def box(bones, name, x0, y0, z0, x1, y1, z1, part):
    """A box given in voxels."""
    bones[name].append(cube([x0 * V, y0 * V, z0 * V], [x1 * V, y1 * V, z1 * V], part))


def both(bones, name, x0, y0, z0, x1, y1, z1, part):
    """The same box on the left and on the right."""
    box(bones, name, x0, y0, z0, x1, y1, z1, part)
    box(bones, name, -x1, y0, z0, -x0, y1, z1, part)


def head(bones):
    # the upper jaw and skull
    box(bones, 'head', -1.5, SPLIT, -12, 1.5, 2.5, -8, 'skin')            # the broad blunt snout
    box(bones, 'head', -1.0, 2.5, -12, 1.0, 3.0, -11, 'skin')             # the nostril block on its tip
    both(bones, 'head', 0.2, 3.0, -11.75, 0.7, 3.04, -11.25, 'nostril')
    box(bones, 'head', -1.25, 2.5, -9, 1.25, 2.9, -8, 'skin')             # the step up to the skull
    box(bones, 'head', -2.0, SPLIT, -8, 2.0, 3.25, HINGE, 'skin')         # the skull
    both(bones, 'head', 0.75, 3.25, -7.75, 2.0, 3.9, -6.5, 'brow')        # a brow block over each eye
    both(bones, 'head', 2.0, 2.5, -7.6, 2.04, 3.25, -6.6, 'iris')
    both(bones, 'head', 2.04, 2.5, -7.25, 2.07, 3.25, -6.95, 'pupil')
    # the lower jaw, hinged at the back of the skull
    box(bones, 'jaw', -1.4, 0.75, -12, 1.4, SPLIT, -8, 'jaw')
    box(bones, 'jaw', -1.9, 0.6, -8, 1.9, SPLIT, HINGE, 'jaw')
    # a few big teeth: the upper ones hang down outside the lower jaw, the lower ones stand up inside the mouth
    for z in (-11.9, -10.6, -9.3):
        both(bones, 'head', 1.2, 0.85, z, 1.55, SPLIT, z + 0.5, 'tooth')
    both(bones, 'head', 1.7, 0.85, -7.5, 2.05, SPLIT, -7.0, 'tooth')
    both(bones, 'head', 0.3, 0.85, -12.05, 0.8, SPLIT, -11.7, 'tooth')
    for z in (-11.2, -9.9, -8.7):
        both(bones, 'jaw', 0.75, SPLIT, z, 1.15, 2.05, z + 0.45, 'tooth')
    both(bones, 'jaw', 1.2, SPLIT, -7.9, 1.6, 2.0, -7.45, 'tooth')
    both(bones, 'jaw', 0.15, SPLIT, -11.8, 0.6, 2.0, -11.4, 'tooth')
    # inside the mouth, seen when it gapes: a red palate, a red floor and a paler tongue
    box(bones, 'head', -1.3, SPLIT - 0.04, -11.8, 1.3, SPLIT, -8, 'mouth')
    box(bones, 'head', -1.8, SPLIT - 0.04, -8, 1.8, SPLIT, HINGE, 'mouth')
    box(bones, 'jaw', -1.2, SPLIT, -11.8, 1.2, SPLIT + 0.04, -8, 'mouth')
    box(bones, 'jaw', -1.7, SPLIT, -8, 1.7, SPLIT + 0.04, HINGE, 'mouth')
    box(bones, 'jaw', -0.75, SPLIT, -10.5, 0.75, SPLIT + 0.3, -6.3, 'tongue')


def trunk(bones):
    for name, _, z0, z1, hw, bot, top in TRUNK:
        shoulder = 0.75 if hw >= 2.0 else 0.5 if hw >= 1.0 else 0.25
        box(bones, name, -hw, bot, z0, hw, top - shoulder, z1, 'skin')             # the wide lower part
        box(bones, name, -(hw - shoulder * 0.67), top - shoulder, z0, hw - shoulder * 0.67, top, z1, 'skin')   # the back


def scutes(bones):
    """The armour: raised square scutes in rows down the back, a double row on the tail joining into one."""
    both(bones, 'neck', 0.2, 3.25, -4.9, 1.2, 3.75, -4.1, 'scute')
    row = 0
    z = -3.75
    while z < 3.4:
        name, _, _, _, hw, _, top = next(t for t in TRUNK if t[2] <= z + 0.5 < t[3])
        tall = 0.6 if row % 2 == 0 else 0.45
        both(bones, name, 0.15, top, z, 1.1, top + tall, z + 1.0, 'scute')
        both(bones, name, 1.35, top, z, min(2.25, hw - 0.5), top + tall - 0.15, z + 1.0, 'scute')
        z += 1.5
        row += 1
    for name, _, z0, z1, hw, _, top in TRUNK[5:]:
        z = z0 + 0.1
        while z + 0.8 <= z1 + 1e-6:
            f = (z - 3.5) / 9.5
            tall = 0.8 - 0.35 * f
            if hw >= 1.5:
                both(bones, name, hw - 1.1, top, z, hw - 0.4, top + tall, z + 0.8, 'scute')
            else:
                box(bones, name, -0.3, top, z, 0.3, top + tall, z + 0.8, 'scute')
            z += 1.0


def legs(out):
    """Each leg three bones (upper, lower, foot): short, thick and sprawled out to the side, the big foot flat on the
    ground with white claws."""
    for s, side in ((1, 'left'), (-1, 'right')):
        for kind, parent, z, hw, reach, thick, foot_len in (('arm', 'chest', -3.25, 2.5, 1.6, 1.3, 1.7),
                                                             ('leg', 'hips', 2.0, 2.75, 2.0, 1.6, 2.0)):
            parts = {'u': [], 'l': [], 'f': []}

            def b(key, x0, y0, z0, x1, y1, z1, part):
                lo, hi = sorted([s * x0, s * x1])
                parts[key].append(cube([lo * V, y0 * V, z0 * V], [hi * V, y1 * V, z1 * V], part))

            elbow = hw + reach
            b('u', hw - 0.3, 1.0, z - thick / 2, elbow, 1.0 + thick, z + thick / 2, 'leg')
            b('l', elbow - 0.55, 0.3, z - thick / 2 - 0.35, elbow + 0.6, 1.0 + thick * 0.8, z + thick / 2 - 0.35, 'leg')
            front = z - thick / 2 - 0.2 - foot_len
            b('f', elbow - 0.75, 0.0, front, elbow + 0.85, 0.5, z - thick / 2 + 0.5, 'foot')
            for i in range(4):
                x = elbow - 0.7 + i * 0.42
                b('f', x, 0.0, front - 0.4, x + 0.3, 0.35, front, 'claw')
            sh = [s * hw * V, (1.0 + thick / 2) * V, z * V]
            out.append({'name': f'{kind}_upper_{side}', 'parent': parent, 'pivot': sh, 'cubes': parts['u']})
            out.append({'name': f'{kind}_lower_{side}', 'parent': f'{kind}_upper_{side}',
                        'pivot': [s * elbow * V, sh[1], z * V], 'cubes': parts['l']})
            out.append({'name': f'{kind}_foot_{side}', 'parent': f'{kind}_lower_{side}',
                        'pivot': [s * elbow * V, 0.4 * V, (z - thick / 2) * V], 'cubes': parts['f']})


def model():
    names = ['head', 'jaw'] + list(dict.fromkeys(t[0] for t in TRUNK))
    bones = {name: [] for name in names}
    head(bones)
    trunk(bones)
    scutes(bones)
    out = [{'name': 'body', 'pivot': [0, 2.0 * V, -1.0 * V]},
           {'name': 'neck', 'parent': 'body', 'pivot': [0, 1.5 * V, -4.0 * V], 'cubes': bones['neck']},
           {'name': 'head', 'parent': 'neck', 'pivot': [0, 1.5 * V, NECK * V], 'cubes': bones['head']},
           {'name': 'jaw', 'parent': 'head', 'pivot': [0, SPLIT * V, HINGE * V], 'cubes': bones['jaw']}]
    done = {'neck'}
    for name, parent, z0, *_ in TRUNK:
        if name not in done:
            done.add(name)
            out.append({'name': name, 'parent': parent, 'pivot': [0, 2.0 * V, z0 * V], 'cubes': bones[name]})
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
    if zv < NECK:
        return 0.75, 3.25
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
    band = cz % 4 == 0 and zv > -4            # dark bands across the back and tail
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
