"""The river crab (M9 river life, the clean-up crew: it picks at remains, scraps and algae), its model for the model
viewer (python tools/model_viewer.py crab).

From the user's picture of 2026-11-15 (a blocky "spiny Nile river crab" on a riverbank, "the size is perfect": about
0.8 blocks wide with its legs, 0.3 tall). Fun first, it only has to look good in the game (animals are generalized,
CLAUDE.md). What the picture shows, and so what this is:
- Built of small cubes ("voxels", V model pixels each), one flat colour a cube face in a loose checker.
- A broad rounded carapace in mottled dark brown, purple-brown and red-brown with a few tan flecks, stepping up to a
  low dome; cream spines round its front corners and sides; a cream underside and a cream face plate of mouthparts
  under the front rim.
- Two black eyes on short stalks at the front of the shell.
- Two big claws in front, held low: a purple-brown hand with cream fingers, the upper finger hinged so it can open;
  the right claw a little bigger (asymmetric-looking claws, as in the picture).
- Four walking legs a side, orange-brown banded darker on top, rising from the body to a knee then down to cream
  tips, the front legs angled forward and the back legs back.

Units are model pixels (16 a block), the front points to -Z, up is +Y, left is +X; y = 0 is the ground it stands on.
"""
import copy
import math
import random

from tilapia_models import cube, mix, shade, faces, face_point, png  # noqa: F401 (png for the viewer)

V = 0.5               # model pixels a voxel
TEXELS = 2.0          # texture pixels a model pixel: one flat colour a voxel face
BIG = {'left': 1.0, 'right': 1.15}       # each claw's size (the right one bigger)
LEG_Z = [-3.2, -1.0, 1.2, 3.4]           # where each walking leg leaves the body (voxels)
LEG_YAW = [32, 10, -12, -36]             # how far each leg is turned forward (degrees, + forward)


def box(cubes, x0, y0, z0, x1, y1, z1, part):
    """A box given in voxels (corners in any order)."""
    (x0, x1), (y0, y1), (z0, z1) = sorted((x0, x1)), sorted((y0, y1)), sorted((z0, z1))
    cubes.append(cube([x0 * V, y0 * V, z0 * V], [x1 * V, y1 * V, z1 * V], part))


def carapace(c):
    box(c, -6, 3, -5, 6, 4, 4.5, 'under')               # the cream underside
    box(c, -7, 4, -5.5, 7, 6.5, 4, 'shell')             # the shell's broad edge
    box(c, -6, 4, 4, 6, 6.5, 5.5, 'shell')              # narrowing behind
    box(c, -6.2, 6.5, -5.2, 6.2, 7.4, 4.6, 'shell')     # stepping up to the dome
    box(c, -4.6, 7.4, -3.8, 4.6, 7.9, 3.2, 'shell')     # the dome's crown
    box(c, -6.6, 6.0, -6.0, 6.6, 6.8, -5.5, 'rim')      # the front rim, over the face
    box(c, -3.6, 3.2, -5.9, 3.6, 6.0, -5.5, 'face')     # the face plate
    for y in (3.3, 4.0, 4.7):                           # the mouthparts in rows
        box(c, -2.6, y, -6.2, 2.6, y + 0.6, -5.9, 'mouth')
    # the spines: cream points along the front edge, round the corners and down the sides, a few behind
    for x in (3.7, 5.3):
        for s in (1, -1):
            box(c, s * x, 6.8, -6.4, s * (x + 0.8), 7.8, -5.6, 'spine')
    for s in (1, -1):
        box(c, s * 6.6, 6.3, -5.5, s * 7.6, 7.3, -4.6, 'spine')        # the corners, poking out and up
        for z in (-3.3, -1.1, 1.1):
            box(c, s * 7.0, 5.8, z, s * 7.9, 6.7, z + 0.8, 'spine')
        box(c, s * 5.6, 6.0, 4.8, s * 6.4, 6.8, 5.7, 'spine')


def eye(s):
    c = []
    box(c, s * 1.6, 6.0, -6.2, s * 2.5, 7.6, -5.3, 'stalk')
    box(c, s * 1.3, 7.6, -6.6, s * 2.8, 9.0, -5.1, 'eye')
    return c


def claw(side, s):
    """The claw: the arm (merus and wrist), the hand with its fixed lower finger, and the hinged upper finger."""
    f = BIG[side]
    arm = []
    box(arm, s * 5.5, 3.8, -5.8, s * 9.0, 5.6, -4.0, 'leg')
    box(arm, s * 7.4, 3.4, -8.0, s * 9.8, 5.8, -5.6, 'leg')
    cx = 7.8                                    # the hand's middle across
    hand, finger = [], []
    zf = -8.0 - 5.0 * f                         # the front of the palm
    top = 0.6 + 4.4 * f
    box(hand, s * (cx - 2.1 * f), 0.6, zf, s * (cx + 2.1 * f), top, -7.6, 'hand')
    box(hand, s * (cx - 1.4 * f), top, zf + 1.0, s * (cx + 1.4 * f), top + 0.4, -8.4, 'hand')   # its rounded back
    fx0, fx1 = cx - 1.8 * f, cx + 1.0 * f       # the fingers, on the hand's inner side
    w = fx1 - fx0
    # the fixed finger: thick at the hand, narrowing in two steps to a point that curves up
    box(hand, s * fx0, 0.6, zf - 2.2 * f, s * fx1, 0.6 + 1.6 * f, zf, 'finger')
    box(hand, s * (fx0 + 0.2 * w), 0.6 + 0.2 * f, zf - 3.6 * f, s * (fx1 - 0.2 * w), 0.6 + 1.4 * f, zf - 2.2 * f, 'finger')
    box(hand, s * (fx0 + 0.32 * w), 0.6 + 0.9 * f, zf - 4.4 * f, s * (fx1 - 0.32 * w), 0.6 + 1.9 * f, zf - 3.6 * f, 'finger')
    box(hand, s * (fx0 + 0.3 * w), 0.6 + 1.6 * f, zf - 1.9 * f, s * (fx1 - 0.35 * w), 0.6 + 2.0 * f, zf - 1.3 * f, 'finger')  # a tooth
    # the upper finger (the dactyl): from the hinge, narrowing to a point that hooks down over the fixed one
    box(finger, s * fx0, 0.6 + 2.6 * f, zf - 2.4 * f, s * fx1, 0.6 + 4.0 * f, zf + 0.3, 'finger')
    box(finger, s * (fx0 + 0.2 * w), 0.6 + 2.4 * f, zf - 3.8 * f, s * (fx1 - 0.2 * w), 0.6 + 3.6 * f, zf - 2.4 * f, 'finger')
    box(finger, s * (fx0 + 0.32 * w), 0.6 + 1.9 * f, zf - 4.6 * f, s * (fx1 - 0.32 * w), 0.6 + 3.0 * f, zf - 3.8 * f, 'finger')
    box(finger, s * (fx0 + 0.3 * w), 0.6 + 2.2 * f, zf - 1.6 * f, s * (fx1 - 0.35 * w), 0.6 + 2.6 * f, zf - 1.0 * f, 'finger')  # a tooth
    hinge = [s * cx * V, (0.6 + 3.6 * f) * V, zf * V]
    return arm, hand, finger, hinge, zf


def walking_leg(s):
    """One walking leg along +s X: the upper part rising to the knee, the lower part dropping, the cream tip."""
    up, low, tip = [], [], []
    box(up, s * 6.0, 5.0, -0.7, s * 9.0, 6.4, 0.7, 'leg')
    box(up, s * 8.6, 5.6, -0.7, s * 10.8, 7.2, 0.7, 'leg')
    box(low, s * 10.0, 4.0, -0.65, s * 11.6, 7.4, 0.65, 'leg')
    box(low, s * 10.8, 2.0, -0.6, s * 12.2, 4.4, 0.6, 'leg')
    box(tip, s * 11.2, 0.6, -0.5, s * 12.3, 2.4, 0.5, 'tip')
    box(tip, s * 11.5, 0.0, -0.35, s * 12.1, 0.6, 0.35, 'tip')
    return up, low, tip


def shift_z(cubes, dz):
    for c in cubes:
        c['origin'][2] = round(c['origin'][2] + dz * V, 3)
    return cubes


def model():
    body = []
    carapace(body)
    out = [{'name': 'body', 'pivot': [0, 5 * V, 0], 'cubes': body}]
    for side, s in (('left', 1), ('right', -1)):
        out.append({'name': f'eye_{side}', 'parent': 'body', 'pivot': [s * 2.0 * V, 6.0 * V, -5.8 * V], 'cubes': eye(s)})
    for side, s in (('left', 1), ('right', -1)):
        arm, hand, finger, hinge, zf = claw(side, s)
        out.append({'name': f'claw_arm_{side}', 'parent': 'body', 'pivot': [s * 6.0 * V, 4.7 * V, -4.9 * V], 'cubes': arm})
        out.append({'name': f'claw_hand_{side}', 'parent': f'claw_arm_{side}', 'pivot': [s * 8.6 * V, 4.0 * V, -7.0 * V],
                    'rotation': [0, s * 18, 0], 'cubes': hand})
        out.append({'name': f'claw_finger_{side}', 'parent': f'claw_hand_{side}', 'pivot': hinge, 'cubes': finger})
    for i, (z, yaw) in enumerate(zip(LEG_Z, LEG_YAW)):
        for side, s in (('left', 1), ('right', -1)):
            up, low, tip = (shift_z(p, z) for p in walking_leg(s))
            n = f'leg{i + 1}'
            out.append({'name': f'{n}_upper_{side}', 'parent': 'body', 'pivot': [s * 6.2 * V, 5.6 * V, z * V],
                        'rotation': [0, s * yaw, 0], 'cubes': up})
            out.append({'name': f'{n}_lower_{side}', 'parent': f'{n}_upper_{side}', 'pivot': [s * 10.6 * V, 6.8 * V, z * V],
                        'cubes': low})
            out.append({'name': f'{n}_tip_{side}', 'parent': f'{n}_lower_{side}', 'pivot': [s * 11.6 * V, 2.2 * V, z * V],
                        'cubes': tip})
    return out


# --- colours -----------------------------------------------------------------------------------------------------------
LOOKS = {'A': {'name': 'Spiny river crab', 'note': "your picture: a mottled shell of dark brown, purple-brown and "
               "red-brown with a few tan flecks, cream spines round its front and sides, a cream underside and face, "
               "black eyes on stalks, purple-brown claws with cream fingers (the right one bigger), orange-brown legs "
               "darker on top with cream tips"}}
DARK, PURPLE, RED, ORANGE, TAN, CREAM = ((88, 60, 56), (104, 62, 76), (128, 76, 62), (172, 104, 60), (190, 146, 104),
                                         (228, 214, 184))


def dice(a, b, c):
    """0 to 1, the same for the same voxel every time."""
    n = (int(a) * 374761393 + int(b) * 668265263 + int(c) * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) / 4294967296.0


def pick(colours, r):
    return colours[min(len(colours) - 1, int(r * len(colours)))]


def colour(part, look, x, y, z, face, rnd):
    """One flat colour a voxel face: the voxel is found from the point (model pixels), and its dice pick the colour."""
    xv, yv, zv = x / V, y / V, z / V
    cx, cy, cz = math.floor(xv + 1e-4), math.floor(yv + 1e-4), math.floor(zv + 1e-4)
    if face in ('up', 'down'):
        r = dice(cx, 77, cz)
    elif face in ('east', 'west'):
        r = dice(55, cy, cz)
    else:
        r = dice(cx, cy, 33)
    grain = rnd.uniform(0.97, 1.03)
    if part == 'eye':
        return (60, 58, 62) if face == 'up' else (20, 18, 20)
    if part == 'spine':
        return shade(pick([CREAM, CREAM, mix(CREAM, TAN, 0.4)], r), grain)
    if part in ('under', 'face'):
        return shade(pick([CREAM, mix(CREAM, TAN, 0.5), CREAM, TAN], r), grain)
    if part == 'mouth':
        return shade(TAN if cy % 2 else mix(CREAM, TAN, 0.3), grain)
    if part == 'finger':
        return shade(pick([CREAM, CREAM, mix(CREAM, TAN, 0.35)], r), grain)
    if part == 'tip':
        return shade(pick([CREAM, mix(CREAM, TAN, 0.5)], r), grain)
    if part == 'stalk':
        return shade(pick([RED, DARK], r), grain)
    if part in ('shell', 'rim', 'hand'):
        if face == 'down':
            c = TAN
        elif r > 0.94 and part == 'shell':
            c = TAN                                             # a tan fleck now and then
        elif face == 'up':
            c = pick([DARK, PURPLE, RED, mix(DARK, PURPLE, 0.5), PURPLE, (76, 54, 50)], r)
        else:
            c = shade(pick([RED, PURPLE, DARK, mix(RED, ORANGE, 0.3)], r), 0.9)
        if part == 'rim' and face != 'up':
            c = mix(c, ORANGE, 0.3)
        return shade(c, grain)
    # the legs and claw arms: darker on top, banded orange-brown down the sides, tan underneath
    if face == 'down':
        c = TAN
    elif face == 'up':
        c = pick([PURPLE, DARK, RED, mix(RED, ORANGE, 0.4)], r)
    else:
        c = pick([ORANGE, RED, ORANGE, mix(ORANGE, TAN, 0.4), mix(RED, PURPLE, 0.5)], r)
    return shade(c, grain)


def paint(bones, look='A'):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
    rnd = random.Random(31)
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
    xs = [x for b in bones for c in b.get('cubes', []) for x in (c['origin'][0], c['origin'][0] + c['size'][0])]
    ys = [y for b in bones for c in b.get('cubes', []) for y in (c['origin'][1], c['origin'][1] + c['size'][1])]
    _, rows, w, h = paint(bones)
    print(f'{n} boxes, texture {w} x {h}, unturned width {(max(xs) - min(xs)) / 16:.2f} blocks, height {max(ys) / 16:.2f}')
