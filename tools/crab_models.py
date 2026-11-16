"""The river crab (M9 river life, the clean-up crew: it picks at remains, scraps and algae), its model for the model
viewer (python tools/model_viewer.py crab).

From the user's picture of 2026-11-15 (a blocky "spiny Nile river crab" on a riverbank, "the size is perfect": about
0.8 blocks wide with its legs, 0.3 tall). Fun first, it only has to look good in the game (animals are generalized,
CLAUDE.md). Rebuilt the same day at half the cube size (the user: the legs stood at head level, the spines looked like
white speckles, and the picture has finer detail). What the picture shows, and so what this is:
- Built of small cubes ("voxels", V model pixels each), one flat colour a cube face.
- A broad carapace, wider than deep, widest in its front third and narrowing behind; its own part over a narrower lower body, flaring up and out like a crown to a flat top, with open
  space under its edge above the legs (the user, 2026-11-15); dark
  purple in the middle, warmer red-brown toward the edge; six spikes a side on the top edge (tan at the base, cream at the point), pointing up and a little out.
- A deep cream face under a dark front rim, nearly the whole front wide, with rows of mouthparts.
- A mouth of two doors (bones mouth_left, mouth_right) that swing open forward over a dark mouth.
- Two tall black eyes set wide apart on the front edge.
- Two big claws, slimmer than long, held forward and inward in front of the face, resting on the ground: a thick orange-brown arm
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
LEG_Z = [-4.8, -1.6, 1.6, 4.6]           # where each walking leg leaves the lower body (voxels)
LEG_YAW = [34, 10, -14, -40]             # how far each leg is turned forward (degrees, + forward)
LEG_LONG = [1.0, 1.06, 1.0, 0.9]         # each leg's length (the last pair shorter)
LEG_Y = 9.0                              # the hips' height: on the lower body, well under the shell (its edge 13.5 up)
BODY_IN = 5.5                            # how far the lower body's sides stand in from the shell's edge
RISE, KNEE, ANKLE = 10, -55, -20         # the thigh rises this much, the shin bends down at the knee, the tip more

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
    """A spike of three shrinking cubes from (x, y, z) on the shell's top edge, pointing up and a little out along
    (dx, dz): the crown's points."""
    n = math.hypot(dx, dz)
    d = (0.42 * dx / n, 0.9, 0.42 * dz / n)
    for i, (along, size) in enumerate(((0.3, 1.9), (1.7, 1.3), (2.8, 0.8))):
        px, py, pz = x + d[0] * along, y + d[1] * along, z + d[2] * along
        h = size / 2
        box(c, px - h, py - h, pz - h, px + h, py + h, pz + h, f'spine{i}')


def carapace(c):
    # Two parts (the user, 2026-11-15): a narrower lower body the legs and arms join, and over it the shell, its own
    # part, flaring up and out like a crown to a flat top, so there is open space under its edge above the legs.
    layer(c, 6, 7.2, BODY_IN + 1.5, 'under')            # the lower body, cream
    layer(c, 7.2, 10.5, BODY_IN, 'under')
    layer(c, 10.5, 12, 3.2, 'shell')                    # the shell flaring out and up
    layer(c, 12, 13.5, 1.4, 'shell')
    layer(c, 13.5, 15.8, 0, 'shell')                    # its broad edge
    layer(c, 15.8, 16.4, 0.7, 'shell')                  # the flat top
    box(c, -9.8, 13.5, -10.6, 9.8, 15.0, -10, 'rim')    # the dark front rim, over the face
    box(c, -8.6, 7.6, -8.4, 8.6, 13.5, -6.0, 'face')    # the deep cream face under the rim, nearly the whole front
    box(c, -4.3, 8.4, -8.5, 4.3, 12.0, -8.38, 'gape')   # the dark mouth behind its two doors (mouth_door)
    # the crown's points: one each side of the front, one at each front corner, three down each side, one behind
    for s in (1, -1):
        spine(c, s * 8.6, 15.6, -9.4, s * 0.3, -1)
        spine(c, s * 12.6, 15.6, -7.6, s * 0.8, -0.6)
        for z in (-3.6, 0.6, 4.6):
            spine(c, s * (half_at(z) - 0.9), 15.6, z, s, 0.1 * z)
        spine(c, s * 7.4, 15.6, 8.0, s * 0.6, 0.8)


def mouth_door(s):
    """One of the mouth's two doors (the outer mouthparts): hinged at its outer edge, it swings forward to open."""
    c = []
    box(c, s * 0.15, 8.2, -8.95, s * 4.6, 12.2, -8.5, 'mouth')
    box(c, s * 0.15, 9.4, -9.1, s * 3.8, 10.0, -8.95, 'mouth')      # a ridge across it
    return c


def eye(s):
    c = []
    box(c, s * 4.2, 15.0, -10.4, s * 6.0, 16.6, -8.8, 'stalk')
    box(c, s * 3.9, 16.2, -10.8, s * 6.3, 19.4, -8.5, 'eye')
    return c


def claw(side, s):
    """The claw: the arm (out from under the shell's front corner to the elbow), the big hand with its fixed lower
    finger, and the hinged upper finger; the fingers thick and gaping, dark between them."""
    g = BIG[side]                               # the claw's length
    f = 0.78 * g                                # its height (slimmer than long, as in the picture)
    k = 0.72 * g                                # its width
    arm = []
    box(arm, s * 6.5, 7.2, -9.6, s * 14.2, 9.8, -7.0, 'leg')              # out from the lower body, under the shell
    box(arm, s * 13.2, 5.0, -13.4, s * 16.6, 8.8, -8.6, 'leg')            # the forearm, forward and down
    box(arm, s * 13.0, 4.8, -10.2, s * 16.8, 9.0, -9.0, 'joint')          # the elbow's darker band
    cx, y0 = 15.4, 0.4                          # the hand's middle across, its underside (resting on the ground)
    hand, finger = [], []
    zb, zf = -12.0, -12.0 - 7.5 * g             # the palm's back and front
    top = y0 + 7.4 * f
    box(hand, s * (cx - 4.0 * k), y0 + 0.8 * f, zf, s * (cx + 4.0 * k), top - 0.8 * f, zb, 'hand')
    box(hand, s * (cx - 3.3 * k), y0, zf + 0.6, s * (cx + 3.3 * k), top, zb - 0.8, 'hand')      # rounded above and below
    box(hand, s * (cx - 2.4 * k), top, zf + 1.6, s * (cx + 2.4 * k), top + 0.5 * f, zb - 1.8, 'hand')
    box(hand, s * (cx - 3.0 * k), y0 + 1.4 * f, zb, s * (cx + 3.0 * k), top - 1.4 * f, zb + 1.2, 'hand')  # its heel
    fx0, fx1 = cx - 3.2 * k, cx + 1.6 * k       # the fingers, on the hand's inner side
    w = fx1 - fx0
    box(hand, s * (fx0 + 0.4), y0 + 2.6 * f, zf - 1.0 * g, s * (fx1 - 0.4), y0 + 4.8 * f, zf, 'gape')
    # the fixed finger: thick at the hand, narrowing in steps to a point that curves up
    box(hand, s * fx0, y0, zf - 2.6 * g, s * fx1, y0 + 3.0 * f, zf, 'finger')
    box(hand, s * (fx0 + 0.12 * w), y0 + 0.3 * f, zf - 4.6 * g, s * (fx1 - 0.12 * w), y0 + 2.7 * f, zf - 2.6 * g, 'finger')
    box(hand, s * (fx0 + 0.25 * w), y0 + 0.9 * f, zf - 6.0 * g, s * (fx1 - 0.25 * w), y0 + 2.9 * f, zf - 4.6 * g, 'finger')
    box(hand, s * (fx0 + 0.36 * w), y0 + 1.8 * f, zf - 6.8 * g, s * (fx1 - 0.36 * w), y0 + 3.3 * f, zf - 6.0 * g, 'finger')
    for z in (1.2, 3.2):                        # teeth
        box(hand, s * (fx0 + 0.3 * w), y0 + 2.8 * f, zf - (z + 0.8) * g, s * (fx1 - 0.3 * w), y0 + 3.5 * f, zf - z * g, 'finger')
    # the upper finger (the dactyl): from the hinge, narrowing to a point that hooks down over the fixed one
    box(finger, s * fx0, y0 + 4.4 * f, zf - 2.6 * g, s * fx1, y0 + 7.3 * f, zf + 0.4, 'finger')
    box(finger, s * (fx0 + 0.12 * w), y0 + 4.5 * f, zf - 4.6 * g, s * (fx1 - 0.12 * w), y0 + 6.9 * f, zf - 2.6 * g, 'finger')
    box(finger, s * (fx0 + 0.25 * w), y0 + 4.1 * f, zf - 6.0 * g, s * (fx1 - 0.25 * w), y0 + 6.1 * f, zf - 4.6 * g, 'finger')
    box(finger, s * (fx0 + 0.36 * w), y0 + 3.5 * f, zf - 7.0 * g, s * (fx1 - 0.36 * w), y0 + 5.0 * f, zf - 6.0 * g, 'finger')
    for z in (0.9, 2.9):
        box(finger, s * (fx0 + 0.3 * w), y0 + 3.8 * f, zf - (z + 0.8) * g, s * (fx1 - 0.3 * w), y0 + 4.5 * f, zf - z * g, 'finger')
    hinge = [s * cx * V, (y0 + 6.2 * f) * V, zf * V]
    pinch = [s * (fx0 + fx1) / 2 * V, (y0 + 3.7 * f) * V, (zf - 6.2 * g) * V]     # where the fingers' points meet
    return arm, hand, finger, hinge, pinch


def walking_leg(s, px, pz, f):
    """One walking leg along +s X from its hip (px, LEG_Y, pz), drawn straight: the bones' turns (RISE, KNEE, ANKLE)
    bend it. The thigh with a darker knee, the shin narrowing, and the long pointed cream tip.
    Returns (thigh, shin, tip, the knee's x, the ankle's x)."""
    up, low, tip = [], [], []
    y = LEG_Y
    knee = px + s * 10 * f
    box(up, px - s * 1.0, y - 1.3, pz - 1.1, knee, y + 1.3, pz + 1.1, 'leg')
    box(up, knee - s * 1.3, y - 1.55, pz - 1.3, knee + s * 1.2, y + 1.55, pz + 1.3, 'joint')
    ankle = knee + s * 6.5 * f
    box(low, knee, y - 1.25, pz - 1.05, knee + s * 3.6 * f, y + 1.25, pz + 1.05, 'leg')
    box(low, knee + s * 3.6 * f, y - 1.0, pz - 0.9, ankle, y + 1.0, pz + 0.9, 'leg')
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
        out.append({'name': f'eye_{side}', 'parent': 'body', 'pivot': [s * 5.1 * V, 15.8 * V, -9.6 * V], 'cubes': eye(s)})
    for side, s in (('left', 1), ('right', -1)):
        out.append({'name': f'mouth_{side}', 'parent': 'body', 'pivot': [s * 4.6 * V, 10 * V, -8.5 * V],
                    'cubes': mouth_door(s)})
    for side, s in (('left', 1), ('right', -1)):
        arm, hand, finger, hinge, pinch = claw(side, s)
        out.append({'name': f'claw_arm_{side}', 'parent': 'body', 'pivot': [s * 7.5 * V, 8.5 * V, -8 * V], 'cubes': arm})
        out.append({'name': f'claw_hand_{side}', 'parent': f'claw_arm_{side}', 'pivot': [s * 15.4 * V, 6 * V, -12 * V],
                    'rotation': [0, s * 24, 0], 'cubes': hand, 'pinch': pinch})
        out.append({'name': f'claw_finger_{side}', 'parent': f'claw_hand_{side}', 'pivot': hinge, 'cubes': finger})
    for i, (z, yaw, f) in enumerate(zip(LEG_Z, LEG_YAW, LEG_LONG)):
        for side, s in (('left', 1), ('right', -1)):
            px = s * (half_at(z) - BODY_IN - 0.4)
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
               "middle and red-brown toward the edge, a flat-topped shell flaring up like a crown over a narrower body, spikes pointing up round its top edge, a deep cream face under a dark rim, tall black eyes set wide apart, big purple-brown claws "
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
            if yv < 13.5:
                c = shade(mix(c, ORANGE, 0.25), 0.9)            # the flare under the edge: warmer, in shadow
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
