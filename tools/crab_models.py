"""The river crab (M9 river life, the clean-up crew: it picks at remains, scraps and algae), its model for the model
viewer (python tools/model_viewer.py crab).

From the user's picture of 2026-11-15 (a blocky "spiny Nile river crab" on a riverbank, "the size is perfect": about
0.8 blocks wide with its legs, 0.3 tall). Fun first, it only has to look good in the game (animals are generalized,
CLAUDE.md). Rebuilt the same day at half the cube size (the user: the legs stood at head level, the spines looked like
white speckles, and the picture has finer detail). What the picture shows, and so what this is:
- Built of small cubes ("voxels", V model pixels each), one flat colour a cube face.
- A broad carapace, wider than deep, widest in its front third and narrowing behind, stepping up to a low dome; dark
  purple in the middle, warmer red-brown toward the edge; real spikes (tan at the base, cream at the point) along the
  front, round the corners and down the sides, pointing out and up.
- A deep cream face under a dark front rim, nearly the whole front wide, with rows of mouthparts.
- Two tall black eyes set wide apart on the front edge.
- Two big chunky claws held forward and inward in front of the face, resting on the ground: a thick orange-brown arm
  with an elbow, a purple-brown hand, thick cream fingers gaping open (dark between them), the upper one hinged; the
  right claw a little bigger.
- Four walking legs a side, leaving from under the shell's edge: a thigh rising a little to a dark knee that stays
  below the shell's top, a shin going out and down, and a long pointed cream tip; orange-brown, darker at the joints.

Units are model pixels (16 a block), the front points to -Z, up is +Y, left is +X; y = 0 is the ground it stands on.
"""
import copy
import math
import random

from tilapia_models import cube, mix, shade, faces, face_point, png  # noqa: F401 (png for the viewer)

V = 0.25              # model pixels a voxel (half the first model's: the picture's grain)
TEXELS = 4.0          # texture pixels a model pixel: one flat colour a voxel face
BIG = {'left': 1.0, 'right': 1.15}       # each claw's size (the right one bigger)
LEG_Z = [-5.5, -1.5, 2.5, 5.6]           # where each walking leg leaves the body (voxels)
LEG_YAW = [32, 9, -14, -40]              # how far each leg is turned forward (degrees, + forward)
LEG_LONG = [1.0, 1.06, 1.0, 0.9]         # each leg's length (the last pair shorter)
LEG_Y = 9.4                              # the hips' height: under the shell's edge (the edge is 10 to 13)
RISE, KNEE, ANKLE = 18, -70, -20         # the thigh rises this much, the shin bends down at the knee, the tip more

# The shell's outline from above, front to back: (z from, z to, half its width), widest in the front third.
OUTLINE = [(-10, -9, 9.5), (-9, -8, 12), (-8, -6, 13.5), (-6, -1, 14), (-1, 2, 13.5), (2, 4, 12.5), (4, 6, 11),
           (6, 8, 9), (8, 10, 6.5)]


def half_at(z):
    for z0, z1, h in OUTLINE:
        if z0 <= z <= z1:
            return h
    return OUTLINE[-1][2]


def box(cubes, x0, y0, z0, x1, y1, z1, part):
    """A box given in voxels (corners in any order)."""
    (x0, x1), (y0, y1), (z0, z1) = sorted((x0, x1)), sorted((y0, y1)), sorted((z0, z1))
    cubes.append(cube([x0 * V, y0 * V, z0 * V], [x1 * V, y1 * V, z1 * V], part))


def layer(c, y0, y1, inset, part):
    """One slab of the shell: the outline drawn in by `inset` voxels all round."""
    zmin, zmax = -10 + inset * 0.7, 10 - inset * 0.7
    for z0, z1, h in OUTLINE:
        z0, z1, h = max(z0, zmin), min(z1, zmax), h - inset
        if z1 - z0 > 0.2 and h > 0.5:
            box(c, -h, y0, z0, h, y1, z1, part)


def spine(c, x, y, z, dx, dz):
    """A spike of three shrinking cubes from (x, y, z) on the shell's edge, pointing out along (dx, dz) and up."""
    n = math.sqrt(dx * dx + dz * dz + 0.5 * 0.5)
    d = (dx / n, 0.5 / n, dz / n)
    for i, (along, size) in enumerate(((0.3, 1.9), (1.7, 1.3), (2.8, 0.8))):
        px, py, pz = x + d[0] * along, y + d[1] * along, z + d[2] * along
        h = size / 2
        box(c, px - h, py - h, pz - h, px + h, py + h, pz + h, f'spine{i}')


def carapace(c):
    layer(c, 7, 9, 3, 'under')                          # the cream underside
    layer(c, 9, 10, 1, 'under')
    layer(c, 10, 13, 0, 'shell')                        # the shell's broad edge
    layer(c, 13, 15, 1.5, 'shell')                      # stepping up to the dome
    layer(c, 15, 16.5, 3.5, 'shell')
    layer(c, 16.5, 17.5, 6, 'shell')                    # the dome's crown
    box(c, -9.8, 10.5, -10.6, 9.8, 12.2, -10, 'rim')    # the dark front rim, over the face
    for x in (-2.6, -0.6, 1.4):                         # little teeth on the rim between the eyes
        box(c, x, 12.2, -10.6, x + 1.2, 12.9, -9.8, 'rim')
    box(c, -9.2, 7, -10.3, 9.2, 10.5, -9.5, 'face')     # the deep cream face under the rim, nearly the whole front
    box(c, -7.5, 5.8, -9.8, 7.5, 7, -8, 'face')         # stepping down to the belly
    for i, y in enumerate((7.3, 8.3, 9.3)):             # the mouthparts in rows
        w = 3.6 + i * 0.7
        box(c, -w, y, -10.7, w, y + 0.7, -10.3, 'mouth')
    box(c, -0.4, 7.3, -10.8, 0.4, 10.0, -10.3, 'mouth')
    # the spines: spikes along the front edge, at the front corners, down both sides and two behind
    for s in (1, -1):
        spine(c, s * 7.8, 12.6, -9.8, s * 0.25, -1)
        spine(c, s * 11.4, 12.6, -8.9, s * 0.7, -0.75)
        for z in (-7, -4.4, -1.8, 0.8, 3.2, 5.4):
            spine(c, s * (half_at(z) - 0.3), 12.6, z, s, 0.12 * z / 5)
        spine(c, s * 8.6, 12.6, 7.4, s * 0.7, 0.7)
        spine(c, s * 5.2, 12.6, 9.6, s * 0.2, 1)


def eye(s):
    c = []
    box(c, s * 4.2, 12.6, -10.4, s * 6.0, 14.2, -8.8, 'stalk')
    box(c, s * 3.9, 14.2, -10.8, s * 6.3, 18.0, -8.5, 'eye')
    return c


def claw(side, s):
    """The claw: the arm (out from under the shell's front corner to the elbow), the big hand with its fixed lower
    finger, and the hinged upper finger; the fingers thick and gaping, dark between them."""
    f = BIG[side]
    arm = []
    box(arm, s * 9.5, 6, -11.5, s * 16, 9.6, -7.5, 'leg')
    box(arm, s * 14, 4, -14.5, s * 19.5, 9.2, -10, 'leg')
    box(arm, s * 13.7, 3.7, -11.8, s * 19.8, 9.5, -10.4, 'joint')         # the elbow's darker band
    cx, y0 = 17.0, 0.4                          # the hand's middle across, its underside (resting on the ground)
    hand, finger = [], []
    zb, zf = -12.5, -12.5 - 7.0 * f             # the palm's back and front
    top = y0 + 7.4 * f
    box(hand, s * (cx - 4.0 * f), y0 + 0.8 * f, zf, s * (cx + 4.0 * f), top - 0.8 * f, zb, 'hand')
    box(hand, s * (cx - 3.3 * f), y0, zf + 0.6, s * (cx + 3.3 * f), top, zb - 0.8, 'hand')      # rounded above and below
    box(hand, s * (cx - 2.4 * f), top, zf + 1.6, s * (cx + 2.4 * f), top + 0.6 * f, zb - 1.8, 'hand')
    box(hand, s * (cx - 3.0 * f), y0 + 1.4 * f, zb, s * (cx + 3.0 * f), top - 1.4 * f, zb + 1.2, 'hand')  # its heel
    fx0, fx1 = cx - 3.2 * f, cx + 1.6 * f       # the fingers, on the hand's inner side
    w = fx1 - fx0
    box(hand, s * (fx0 + 0.5), y0 + 2.6 * f, zf - 1.0 * f, s * (fx1 - 0.5), y0 + 4.8 * f, zf, 'gape')
    # the fixed finger: thick at the hand, narrowing in steps to a point that curves up
    box(hand, s * fx0, y0, zf - 2.6 * f, s * fx1, y0 + 3.0 * f, zf, 'finger')
    box(hand, s * (fx0 + 0.12 * w), y0 + 0.3 * f, zf - 4.6 * f, s * (fx1 - 0.12 * w), y0 + 2.7 * f, zf - 2.6 * f, 'finger')
    box(hand, s * (fx0 + 0.25 * w), y0 + 0.9 * f, zf - 6.0 * f, s * (fx1 - 0.25 * w), y0 + 2.9 * f, zf - 4.6 * f, 'finger')
    box(hand, s * (fx0 + 0.36 * w), y0 + 1.8 * f, zf - 6.8 * f, s * (fx1 - 0.36 * w), y0 + 3.3 * f, zf - 6.0 * f, 'finger')
    for z in (1.2, 3.2):                        # teeth
        box(hand, s * (fx0 + 0.3 * w), y0 + 2.8 * f, zf - (z + 0.8) * f, s * (fx1 - 0.3 * w), y0 + 3.5 * f, zf - z * f, 'finger')
    # the upper finger (the dactyl): from the hinge, narrowing to a point that hooks down over the fixed one
    box(finger, s * fx0, y0 + 4.4 * f, zf - 2.6 * f, s * fx1, y0 + 7.3 * f, zf + 0.4, 'finger')
    box(finger, s * (fx0 + 0.12 * w), y0 + 4.5 * f, zf - 4.6 * f, s * (fx1 - 0.12 * w), y0 + 6.9 * f, zf - 2.6 * f, 'finger')
    box(finger, s * (fx0 + 0.25 * w), y0 + 4.1 * f, zf - 6.0 * f, s * (fx1 - 0.25 * w), y0 + 6.1 * f, zf - 4.6 * f, 'finger')
    box(finger, s * (fx0 + 0.36 * w), y0 + 3.5 * f, zf - 7.0 * f, s * (fx1 - 0.36 * w), y0 + 5.0 * f, zf - 6.0 * f, 'finger')
    for z in (0.9, 2.9):
        box(finger, s * (fx0 + 0.3 * w), y0 + 3.8 * f, zf - (z + 0.8) * f, s * (fx1 - 0.3 * w), y0 + 4.5 * f, zf - z * f, 'finger')
    hinge = [s * cx * V, (y0 + 6.2 * f) * V, zf * V]
    return arm, hand, finger, hinge


def walking_leg(s, px, pz, f):
    """One walking leg along +s X from its hip (px, LEG_Y, pz), drawn straight: the bones' turns (RISE, KNEE, ANKLE)
    bend it. The thigh with a darker knee, the shin narrowing, and the long pointed cream tip.
    Returns (thigh, shin, tip, the knee's x, the ankle's x)."""
    up, low, tip = [], [], []
    y = LEG_Y
    knee = px + s * 8 * f
    box(up, px - s * 1.0, y - 1.4, pz - 1.2, knee, y + 1.4, pz + 1.2, 'leg')
    box(up, knee - s * 1.3, y - 1.65, pz - 1.4, knee + s * 1.2, y + 1.65, pz + 1.4, 'joint')
    ankle = knee + s * 7 * f
    box(low, knee, y - 1.25, pz - 1.05, knee + s * 4 * f, y + 1.25, pz + 1.05, 'leg')
    box(low, knee + s * 4 * f, y - 1.0, pz - 0.9, ankle, y + 1.0, pz + 0.9, 'leg')
    box(low, ankle - s * 0.9, y - 1.15, pz - 1.0, ankle + s * 0.5, y + 1.15, pz + 1.0, 'joint')
    box(tip, ankle - s * 0.2, y - 0.85, pz - 0.75, ankle + s * 2.4 * f, y + 0.85, pz + 0.75, 'tip0')
    box(tip, ankle + s * 2.4 * f, y - 0.6, pz - 0.55, ankle + s * 4.4 * f, y + 0.6, pz + 0.55, 'tip')
    box(tip, ankle + s * 4.4 * f, y - 0.35, pz - 0.35, ankle + s * 6 * f, y + 0.35, pz + 0.35, 'tip')
    return up, low, tip, knee, ankle


def model():
    body = []
    carapace(body)
    out = [{'name': 'body', 'pivot': [0, 10 * V, 0], 'cubes': body}]
    for side, s in (('left', 1), ('right', -1)):
        out.append({'name': f'eye_{side}', 'parent': 'body', 'pivot': [s * 5.1 * V, 13 * V, -9.6 * V], 'cubes': eye(s)})
    for side, s in (('left', 1), ('right', -1)):
        arm, hand, finger, hinge = claw(side, s)
        out.append({'name': f'claw_arm_{side}', 'parent': 'body', 'pivot': [s * 10 * V, 8 * V, -9.5 * V], 'cubes': arm})
        out.append({'name': f'claw_hand_{side}', 'parent': f'claw_arm_{side}', 'pivot': [s * 17.0 * V, 6 * V, -12.5 * V],
                    'rotation': [0, s * 24, 0], 'cubes': hand})
        out.append({'name': f'claw_finger_{side}', 'parent': f'claw_hand_{side}', 'pivot': hinge, 'cubes': finger})
    for i, (z, yaw, f) in enumerate(zip(LEG_Z, LEG_YAW, LEG_LONG)):
        for side, s in (('left', 1), ('right', -1)):
            px = s * (half_at(z) - 2.6)
            up, low, tip, knee, ankle = walking_leg(s, px, z, f)
            n = f'leg{i + 1}'
            out.append({'name': f'{n}_upper_{side}', 'parent': 'body', 'pivot': [px * V, LEG_Y * V, z * V],
                        'rotation': [0, s * yaw, -s * RISE], 'cubes': up})
            out.append({'name': f'{n}_lower_{side}', 'parent': f'{n}_upper_{side}', 'pivot': [knee * V, LEG_Y * V, z * V],
                        'rotation': [0, 0, -s * KNEE], 'cubes': low})
            out.append({'name': f'{n}_tip_{side}', 'parent': f'{n}_lower_{side}', 'pivot': [ankle * V, LEG_Y * V, z * V],
                        'rotation': [0, 0, -s * ANKLE], 'cubes': tip})
    return out


# --- colours -----------------------------------------------------------------------------------------------------------
LOOKS = {'A': {'name': 'Spiny river crab', 'note': "your picture, at half the cube size: a shell dark purple in the "
               "middle and red-brown toward the edge, real spikes round its front and sides (tan at the base, cream at "
               "the point), a deep cream face under a dark rim, tall black eyes set wide apart, big purple-brown claws "
               "with thick cream fingers held in front of the face (the right one bigger), orange-brown legs from under "
               "the shell's edge with dark knees and long cream tips"}}
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
    blotch = dice(cx // 2, cy // 2 + 9, cz // 2)        # the same for 2 x 2 voxels: the shell's mottle in patches
    if part == 'eye':
        return (44, 42, 48) if face == 'up' else (18, 16, 20)
    if part == 'gape':
        return (46, 28, 32)
    if part == 'spine0':
        return shade(pick([TAN, mix(TAN, ORANGE, 0.3)], r), grain)
    if part == 'spine1':
        return shade(mix(CREAM, TAN, 0.45), grain)
    if part == 'spine2':
        return shade(CREAM, grain)
    if part in ('under', 'face'):
        return shade(pick([CREAM, mix(CREAM, TAN, 0.5), CREAM, mix(CREAM, TAN, 0.25)], r), grain)
    if part == 'mouth':
        return shade(TAN if cy % 2 else mix(TAN, ORANGE, 0.35), grain)
    if part == 'finger':
        return shade(pick([CREAM, mix(CREAM, TAN, 0.3), mix(CREAM, TAN, 0.55)], r), grain if face != 'down' else 0.85)
    if part == 'tip0':
        return shade(pick([mix(ORANGE, TAN, 0.6), TAN], r), grain)
    if part == 'tip':
        return shade(pick([CREAM, mix(CREAM, TAN, 0.4)], r), grain)
    if part == 'stalk':
        return shade(pick([DARK, (60, 44, 44)], r), grain)
    if part == 'joint':
        return shade(pick([PURPLE, DARK, mix(PURPLE, RED, 0.4)], r), grain if face != 'down' else 0.85)
    if part in ('shell', 'rim'):
        if part == 'rim':
            return shade(pick([DARK, (70, 48, 48), mix(DARK, PURPLE, 0.5)], r), grain)
        if face == 'up':
            # dark purple in the middle, warmer red-brown out toward the edge, in patches
            out = min(1.0, math.hypot(xv / 14.0, zv / 10.0)) + (blotch - 0.5) * 0.5
            if out < 0.45:
                c = pick([DARK, PURPLE, mix(DARK, PURPLE, 0.5), (76, 54, 58)], r)
            elif out < 0.8:
                c = pick([PURPLE, mix(PURPLE, RED, 0.5), DARK, PURPLE], r)
            else:
                c = pick([RED, mix(RED, PURPLE, 0.5), mix(RED, ORANGE, 0.35), RED], r)
        else:
            c = shade(pick([RED, mix(RED, PURPLE, 0.5), mix(RED, ORANGE, 0.35), PURPLE], r), 0.92)
            if yv < 11:
                c = mix(c, ORANGE, 0.25)                        # the edge's lower lip warmer
        return shade(c, grain)
    if part == 'hand':
        if face == 'down':
            c = TAN
        elif face == 'up':
            c = pick([PURPLE, mix(PURPLE, RED, 0.5), PURPLE, DARK], r)
        else:
            c = pick([PURPLE, mix(PURPLE, RED, 0.4), mix(PURPLE, DARK, 0.5), PURPLE], r)
            c = mix(c, RED, max(0.0, 0.5 - yv / 10.0))          # warmer low down
        return shade(c, grain)
    # the legs and claw arms: orange-brown, darker on top, tan underneath
    if face == 'down':
        c = mix(TAN, ORANGE, 0.3)
    elif face == 'up':
        c = pick([RED, mix(RED, ORANGE, 0.5), mix(RED, PURPLE, 0.4), RED], r)
    else:
        c = pick([ORANGE, mix(ORANGE, RED, 0.5), ORANGE, mix(ORANGE, TAN, 0.3)], r)
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


def bounds(bones):
    """The lowest and highest corner of the posed model, in model pixels (the bones' turns as the viewer makes them)."""
    def rot(r):
        rx, ry, rz = math.radians(-r[0]), math.radians(r[1]), math.radians(-r[2])
        ca, sa, cb, sb, cc, sc = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
        mx = [[1, 0, 0], [0, ca, -sa], [0, sa, ca]]
        my = [[cb, 0, sb], [0, 1, 0], [-sb, 0, cb]]
        mz = [[cc, -sc, 0], [sc, cc, 0], [0, 0, 1]]

        def mul(a, b):
            return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
        return mul(mz, mul(my, mx))
    by = {b['name']: b for b in bones}

    def place(b, p):
        while b:
            m, piv = rot(b.get('rotation', [0, 0, 0])), b['pivot']
            d = [p[i] - piv[i] for i in range(3)]
            p = [piv[i] + sum(m[i][k] * d[k] for k in range(3)) for i in range(3)]
            b = by.get(b.get('parent'))
        return p
    pts = []
    for b in bones:
        for c in b.get('cubes', []):
            o, sz = c['origin'], c['size']
            for i in range(8):
                pts.append(place(b, [o[k] + sz[k] * ((i >> k) & 1) for k in range(3)]))
    return [min(p[k] for p in pts) for k in range(3)], [max(p[k] for p in pts) for k in range(3)]


if __name__ == '__main__':
    bones = model()
    n = sum(len(b.get('cubes', [])) for b in bones)
    lo, hi = bounds(bones)
    _, rows, w, h = paint(bones)
    print(f'{n} boxes, texture {w} x {h}, width {(hi[0] - lo[0]) / 16:.2f} blocks, length {(hi[2] - lo[2]) / 16:.2f}, '
          f'lowest {lo[1] / V:.2f} and highest {hi[1] / V:.2f} voxels')
