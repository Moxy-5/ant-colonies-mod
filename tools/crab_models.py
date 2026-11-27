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
import math
import random
import sys

from model_parts import cube, mix, shade, png, clip, paint_faces, geo, animations, write_assets  # noqa: F401 (png for the viewer)

V = 0.25              # model pixels a voxel (half the first model's: the picture's grain)
TEXELS = 4.0          # texture pixels a model pixel: one flat colour a voxel face
BIG = {'left': 1.0, 'right': 1.15}       # each claw's size (the right one bigger)
USUAL_BIG = dict(BIG)                    # the usual crab's, which the clips were made for
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


# --- the snail it cracks (2026-11-16, the user: "Yes, crabs eat snails") -------------------------------------------------
# The river snail's own shell (snail_models.py, look A, the Garden spiral) held up in front of the crab's mouth, between
# its claws' fingertips, turned round so the opening faces the mouth, at HELD_SIZE of the snail's (a smaller snail than
# the biggest: my pick, the crab's face stays seen). HELD_AT is where the coil's middle sits, in voxels; the crack clip's
# grip (CRACK_GRIP) puts each claw's pinch against its flat sides (worked out with the posing maths, as bounds()).
HELD_SIZE = 0.6
HELD_AT = (0.0, 8.0, -19.5)


def held_shell():
    """The snail's shell cubes moved to HELD_AT, turned half round and shrunk; their parts renamed snail_*."""
    import snail_models as sm
    cx, cy, cz = (v * sm.V for v in sm.SPIRAL_A)
    tx, ty, tz = (v * V for v in HELD_AT)
    out = []
    for c in sm.shell_a():
        (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
        lo = [tx - HELD_SIZE * (ox + sx - cx), ty + HELD_SIZE * (oy - cy), tz - HELD_SIZE * (oz + sz - cz)]
        hi = [tx - HELD_SIZE * (ox - cx), ty + HELD_SIZE * (oy + sy - cy), tz - HELD_SIZE * (oz - cz)]
        out.append(cube(lo, hi, 'snail_' + c['_part']))
    return out


def held_colour(part, x, y, z, face, rnd):
    """The held shell's colours: the snail's own, read at the point of the snail the held point came from."""
    import snail_models as sm
    cx, cy, cz = (v * sm.V for v in sm.SPIRAL_A)
    tx, ty, tz = (v * V for v in HELD_AT)
    sx, sy, sz = cx - (x - tx) / HELD_SIZE, cy + (y - ty) / HELD_SIZE, cz - (z - tz) / HELD_SIZE
    return sm.colour(part[len('snail_'):], 'A', sx, sy, sz, face, rnd)


def model(game=False):
    """The bones. For the game each claw hand also carries two small bits (algae green, meat pink) between its fingers'
    points, shown by the pick clip and CrabModel while it carries food to its mouth."""
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
        if game:
            for kind in ('green', 'meat'):
                bit = []
                px, py, pz = (v / V for v in pinch)
                box(bit, px - 1, py - 0.8, pz - 1, px + 1, py + 0.8, pz + 1, 'bit_' + kind)
                out.append({'name': f'bit_{kind}_{side}', 'parent': f'claw_hand_{side}', 'pivot': pinch, 'cubes': bit})
    if game:
        out.append({'name': 'held_shell', 'parent': 'body', 'pivot': [0, HELD_AT[1] * V, HELD_AT[2] * V],
                    'cubes': held_shell()})
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
    if part.startswith('snail_'):
        return held_colour(part, x, y, z, face, rnd)
    if part == 'eye':
        return (44, 42, 48) if face == 'up' else (18, 16, 20)
    if part == 'bit_green':
        return shade(pick([(95, 146, 56), (78, 128, 48), (112, 160, 66)], r), grain)
    if part == 'bit_meat':
        return shade(pick([(206, 122, 112), (188, 100, 96), (222, 150, 136)], r), grain)
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
    return paint_faces(bones, TEXELS, lambda c, face, px, py, pz: colour(c['_part'], look, px, py, pz, face, rnd))


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


# --- the game's clips --------------------------------------------------------------------------------------------------
# Turns here are in the model file's own directions, which the viewer's poses map to as (x, y, z) -> (-x, y, -z).
WALK_LEN = 0.45       # one stride of the walk clip (seconds)
PICK_LEN = 4.0        # the pick clip: the left claw's trip to the mouth, then the right's (CrabEntity.PICK_TICKS)
PICK_TRIP = 2.6       # one claw's trip (seconds); the right claw starts PICK_SECOND after the left
PICK_SECOND = 1.4
PICK_BITE = 0.68      # how far through a trip the bit goes into the mouth (CrabEntity.BITE_TICKS: 35 and 63)
SIDES = (('left', 1), ('right', -1))
# the crack clip (2026-11-16, crabs eat snails; all timings my picks): both claws reach in and lift the snail up to the
# mouth, squeeze till the shell cracks (CrabEntity: CRACK_AT, the bits and the sound), then the right claw makes two
# trips from the shell to the mouth with meat while the left holds the shell; the goal then drops the broken shell.
CRACK_LEN = 4.0
CRACK_AT = 1.2        # when the shell gives (seconds)
CRACK_TRIPS = (1.5, 2.7)
CRACK_TRIP = 1.2
CRACK_GRIP = {'left': (-18, 33), 'right': (-12, 19)}   # each claw hand's turn (pitch, yaw; the right's yaw mirrored) with
#                                                         its pinch on the shell's flat side (the right claw is longer)
CRACK_MOUTH = (-27, 72)   # the pick clip's turn with the pinch at the mouth


def sm(a, b, x):
    v = max(0.0, min(1.0, (x - a) / (b - a)))
    return v * v * (3 - 2 * v)


def clips(big=None):
    """idle (standing, eyes and fingers moving), walk (the legs stepping in waves; the game turns the body sideways),
    pick (each claw in turn pinches a bit off, carries it to the mouth, the mouth's doors open and chew), threat
    (reared up, claws raised and snapping), court (face to face with a mate, claws raised and reaching, the fingers
    slowly clasping; animal life cycle step 3), death (onto its back, legs curling in).

    With big (a male's or female's claw sizes, SEXES), the pick and crack clips are re-aimed for those claws: each hand's
    turn at the mouth and on the shell is corrected so its pinch lands where the usual claws' pinch does (reaim), so a
    male's big claws do not overshoot the mouth and a female's small ones do not fall short."""
    fix = reaim(big) if big else None

    def fixed(side, name, k):
        """The correction (arm pitch, arm yaw, hand pitch, hand yaw; final turns) for this claw at this pose, times k
        (0 none, 1 all)."""
        if not fix:
            return 0.0, 0.0, 0.0, 0.0
        return tuple(v * k for v in fix[name][side])

    def legs(p, t, speed, amount):
        for side, s in SIDES:
            for i in range(1, 5):
                ph = t * speed + i * math.pi / 2 + (0 if s > 0 else math.pi)
                lift = max(0.0, math.sin(ph))
                p[f'leg{i}_upper_{side}'] = {'rotation': [0, s * math.cos(ph) * 10.3 * amount, -s * lift * 25.8 * amount]}
                p[f'leg{i}_lower_{side}'] = {'rotation': [0, 0, s * lift * 14.3 * amount]}
        return p

    def eyes(p, t, length):
        for j, (side, s) in enumerate(SIDES):
            p[f'eye_{side}'] = {'rotation': [-8 * math.sin(2 * math.pi * t / length + j * 1.7), 0,
                                             -6 * math.sin(4 * math.pi * t / length + j)]}
        return p

    def still(p):
        p.setdefault('body', {'rotation': [0, 0, 0], 'position': [0, 0, 0]})
        for side, s in SIDES:
            p.setdefault(f'mouth_{side}', {'rotation': [0, 0, 0]})
            for kind in ('green', 'meat'):
                p.setdefault(f'bit_{kind}_{side}', {'scale': [0, 0, 0]})
        p.setdefault('held_shell', {'scale': [0, 0, 0], 'position': [0, 0, 0], 'rotation': [0, 0, 0]})
        return p

    def idle(t):
        p = eyes(legs({}, t, math.pi, 0.06), t, 4.0)
        for side, s in SIDES:
            p[f'claw_finger_{side}'] = {'rotation': [-(6 + 5 * math.sin(math.pi * t + (0 if s > 0 else 1.2))), 0, 0]}
        return still(p)

    def walk(t):
        w = 2 * math.pi / WALK_LEN
        p = eyes(legs({}, t, w, 1.0), t, WALK_LEN)
        p['body'] = {'rotation': [0, 0, 0], 'position': [0, 0.12 * abs(math.sin(w * t)), 0]}
        for side, s in SIDES:
            p[f'claw_finger_{side}'] = {'rotation': [-(4 + 4 * math.sin(w * t)), 0, 0]}
        return still(p)

    def threat(t):
        p = eyes({}, t, 3.2)
        for side, s in SIDES:
            for i in range(1, 5):
                p[f'leg{i}_upper_{side}'] = {'rotation': [0, 0, s * 10]}
                p[f'leg{i}_lower_{side}'] = {'rotation': [0, 0, -s * 10]}
            p[f'claw_arm_{side}'] = {'rotation': [-(38 + 4 * math.sin(2 * math.pi * t / 1.6)), s * 16, 0]}
            p[f'claw_hand_{side}'] = {'rotation': [-18, 0, 0]}
            snap = max(0.0, math.sin(2 * math.pi * t / 1.6 + (0 if s > 0 else 1.4)))
            p[f'claw_finger_{side}'] = {'rotation': [-32 * snap, 0, 0]}
        p['body'] = {'rotation': [6, 0, 0], 'position': [0, 0.3, 0]}
        return still(p)

    def pick(t):
        p = eyes(legs({}, t, math.pi / 2, 0.15), t, PICK_LEN)
        o = chew = 0.0
        for j, (side, s) in enumerate(SIDES):
            u = max(0.0, min(1.0, (t - j * PICK_SECOND) / PICK_TRIP))
            lift = sm(0.3, 0.55, u) - sm(0.78, 0.95, u)
            dip = sm(0, 0.15, u) - sm(0.22, 0.32, u)
            ap, ay, fp, fy = fixed(side, 'pick_mouth', lift)
            dip *= min(1.0, USUAL_BIG[side] / big[side]) if big else 1.0    # a longer claw dips less to reach the bed
            p[f'claw_hand_{side}'] = {'rotation': [-(27 * lift - 7 * dip) + fp, s * 72 * lift + fy, 0]}
            p[f'claw_arm_{side}'] = {'rotation': [-8 * lift + ap, ay, 0]}
            p[f'claw_finger_{side}'] = {'rotation': [-(4 + 24 * (sm(0, 0.12, u) - sm(0.2, 0.28, u))
                                                       + 16 * (sm(0.6, 0.68, u) - sm(0.8, 0.9, u))), 0, 0]}
            shown = 1.0 if 0.25 < u < PICK_BITE - 0.01 else 0.0
            for kind in ('green', 'meat'):
                p[f'bit_{kind}_{side}'] = {'scale': [shown, shown, shown]}
            o = max(o, sm(0.46, 0.58, u) - sm(0.68, 0.76, u))
            if 0.74 < u < 0.99:
                chew = 1.0
        o = min(1.0, o + chew * 0.22 * (0.5 + 0.5 * math.sin(t * 17)))
        p['mouth_left'] = {'rotation': [0, -64 * o, 0]}
        p['mouth_right'] = {'rotation': [0, 64 * o, 0]}
        return still(p)

    def crack(t):
        p = eyes(legs({}, t, math.pi / 2, 0.1), t, CRACK_LEN)
        grip = sm(0.0, 0.45, t)
        squeeze = sm(0.5, 0.6, t) - sm(1.35, 1.5, t)
        shake = math.sin(t * 2 * math.pi * 7) * squeeze
        mp, my = CRACK_MOUTH
        o = chew = 0.0
        for side, s in SIDES:
            gp, gy = CRACK_GRIP[side]
            lift = 0.0
            shown = 0.0
            if s < 0:                                       # the right claw's two trips with meat
                for start in CRACK_TRIPS:
                    u = (t - start) / CRACK_TRIP
                    if 0 <= u <= 1:
                        lift = sm(0.1, 0.42, u) - sm(0.72, 0.95, u)
                        shown = 1.0 if 0.08 < u < 0.55 else 0.0
                        o = max(o, sm(0.3, 0.42, u) - sm(0.52, 0.62, u))
                        chew = 1.0 if 0.6 < u < 0.98 else chew
            pitch = gp * grip + (mp - gp) * lift
            yaw = gy * grip + (my - gy) * lift + 3 * shake
            g, m = fixed(side, 'crack_grip', 1.0), fixed(side, 'crack_mouth', 1.0)
            f = [g[i] * grip + (m[i] - g[i]) * lift for i in range(4)]
            p[f'claw_hand_{side}'] = {'rotation': [pitch + f[2], s * yaw + f[3], 0]}
            p[f'claw_arm_{side}'] = {'rotation': [-6 * grip - 4 * lift + f[0], f[1], 0]}
            opened = 26 * (sm(0.0, 0.15, t) - sm(0.3, 0.5, t)) + 18 * (sm(0.05, 0.12, (t - CRACK_TRIPS[0]) / CRACK_TRIP)
                                                                      - sm(0.15, 0.25, (t - CRACK_TRIPS[0]) / CRACK_TRIP)) * (s < 0)
            p[f'claw_finger_{side}'] = {'rotation': [-(4 + opened) + 5 * squeeze, 0, 0]}
            for kind in ('green', 'meat'):
                v = shown if kind == 'meat' else 0.0
                p[f'bit_{kind}_{side}'] = {'scale': [v, v, v]}
        o = min(1.0, o + chew * 0.22 * (0.5 + 0.5 * math.sin(t * 17)))
        p['mouth_left'] = {'rotation': [0, -64 * o, 0]}
        p['mouth_right'] = {'rotation': [0, 64 * o, 0]}
        pop = sm(CRACK_AT, CRACK_AT + 0.05, t) - sm(CRACK_AT + 0.05, CRACK_AT + 0.25, t)
        p['held_shell'] = {'scale': [1, 1, 1], 'position': [0, -0.875 * (1 - sm(0.1, 0.55, t)), 0],
                           'rotation': [-12 * pop, 0, 4 * shake]}
        p['body'] = {'rotation': [2 * squeeze, 0, 0], 'position': [0, -0.08 * squeeze, 0]}
        return still(p)

    def death(t):
        f = min(1.0, t / 0.5)
        f = f * f * (3 - 2 * f)
        p = {'body': {'rotation': [0, 0, 180 * f], 'position': [0, -0.9 * f + 2.0 * math.sin(math.pi * f), 0]}}
        for side, s in SIDES:
            for i in range(1, 5):
                p[f'leg{i}_upper_{side}'] = {'rotation': [0, 0, -s * 20 * f]}
                p[f'leg{i}_lower_{side}'] = {'rotation': [0, 0, s * 35 * f]}
            p[f'claw_hand_{side}'] = {'rotation': [0, s * 30 * f, 0]}
        return still(p)

    def court(t):
        """His part of the courtship (animal life cycle step 3, 2026-11-19; fun first, my picks): face to face with the
        female, his body raised a little on his legs and rocking slowly, both claws reaching forward a little lifted,
        round the front corners of her shell, the fingers slowly clasping and letting go. The game stands the pair face
        to face (CrabEntity.COURT_APART); hers is court_female."""
        w = 2 * math.pi / COURT_LEN
        p = eyes(legs({}, t, w, 0.08), t, COURT_LEN)
        for side, s in SIDES:
            ph = 0 if s > 0 else 0.9
            p[f'claw_arm_{side}'] = {'rotation': [-COURT_ARM - 2 * math.sin(w * t + ph), -s * 4, 0]}
            p[f'claw_hand_{side}'] = {'rotation': [-COURT_HAND + 2 * math.sin(w * t + ph), s * 6, 0]}
            p[f'claw_finger_{side}'] = {'rotation': [-(6 + 9 * (0.5 + 0.5 * math.sin(2 * w * t + ph))), 0, 0]}
        p['body'] = {'rotation': [-2, 0, 2 * math.sin(w * t)], 'position': [0, 0.3 + 0.06 * math.sin(2 * w * t), 0]}
        return still(p)

    def court_female(t):
        """Her part: still and low, her small claws folded in under her face out of his way, the eyes raised to him."""
        w = 2 * math.pi / COURT_LEN
        p = eyes(legs({}, t, w, 0.04), t, COURT_LEN)
        for side, s in SIDES:
            p[f'claw_arm_{side}'] = {'rotation': [-4, s * 10, 0]}
            p[f'claw_hand_{side}'] = {'rotation': [-8, s * 40, 0]}
            p[f'claw_finger_{side}'] = {'rotation': [-2, 0, 0]}
            p[f'eye_{side}'] = {'rotation': [-12, 0, -3 * math.sin(w * t + s)]}
        p['body'] = {'rotation': [1.5, 0, -1.5 * math.sin(w * t)], 'position': [0, -0.1, 0]}
        return still(p)

    return {'idle': clip(4.0, idle, 48), 'walk': clip(WALK_LEN, walk, 12), 'pick': clip(PICK_LEN, pick, 120),
            'crack': clip(CRACK_LEN, crack, 120, 'hold'), 'threat': clip(3.2, threat, 48), 'court': clip(COURT_LEN, court, 36),
            'court_female': clip(COURT_LEN, court_female, 36),
            'death': clip(1.0, death, 12, 'hold')}


# --- males and females (animal life cycle step 3, 2026-11-19; the looks the user confirmed in the life viewer) -----------
# A male's big claws (both bigger, the right one much bigger) and narrow belly flap; a female's small claws and broad
# flap; a female carrying eggs (berried) has an orange egg mass bulging from under her flap. tools/life_looks.py makes
# these models (crab_model); the game's young crabs use the usual model above.
SEXES = {'male': {'left': 1.35, 'right': 1.7}, 'female': {'left': 0.8, 'right': 0.85}}
COURT_LEN = 3.0        # the court clip (seconds, a loop)
COURT_ARM, COURT_HAND = 6, 2     # how far his claws are lifted in it (degrees)


def with_claws(big, game=False):
    """The bones built with these claw sizes."""
    saved = dict(BIG)
    BIG.update(big)
    try:
        return model(game=game)
    finally:
        BIG.clear()
        BIG.update(saved)


def posed(bones, name, point, pose):
    """Where a point of a bone lies with the bones turned (their own turn plus the clip's pose), model pixels."""
    by = {b['name']: b for b in bones}
    b = by[name]
    p = list(point)
    while b:
        r = [x + y for x, y in zip(b.get('rotation', [0, 0, 0]), pose.get(b['name'], [0, 0, 0]))]
        rx, ry, rz = math.radians(-r[0]), math.radians(r[1]), math.radians(-r[2])
        ca, sa, cb_, sb, cc, sc = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
        # Z, then Y, then X (bounds' rot)
        m = [[cc * cb_, cc * sb * sa - sc * ca, cc * sb * ca + sc * sa],
             [sc * cb_, sc * sb * sa + cc * ca, sc * sb * ca - cc * sa],
             [-sb, cb_ * sa, cb_ * ca]]
        piv = b['pivot']
        d = [p[i] - piv[i] for i in range(3)]
        p = [piv[i] + sum(m[i][k] * d[k] for k in range(3)) for i in range(3)]
        b = by.get(b.get('parent'))
    return p


def pinch_at(bones, side, pose):
    by = {b['name']: b for b in bones}
    return posed(bones, f'claw_hand_{side}', by[f'claw_hand_{side}']['pinch'], pose)


# the poses re-aimed (re aim): (each hand's turn, its arm's turn) as the clips hold them there
REAIM_POSES = {'pick_mouth': (lambda s: [-27, s * 72, 0], [-8, 0, 0]),
               'crack_grip': (lambda s: [CRACK_GRIP['left' if s > 0 else 'right'][0],
                                         s * CRACK_GRIP['left' if s > 0 else 'right'][1], 0], [-6, 0, 0]),
               'crack_mouth': (lambda s: [CRACK_MOUTH[0], s * CRACK_MOUTH[1], 0], [-10, 0, 0])}


def fixed_pose(side, s, name, fix):
    """The pose REAIM_POSES names with a correction (arm pitch, arm yaw, hand pitch, hand yaw) added."""
    hand, arm = REAIM_POSES[name]
    h = hand(s)
    return {f'claw_hand_{side}': [h[0] + fix[2], h[1] + fix[3], 0], f'claw_arm_{side}': [arm[0] + fix[0], arm[1] + fix[1], 0]}


_REAIMED = {}
# A claw too long to bring its tip to the mouth holds the bit just in front of it (real crabs pass food to the mouthparts
# from the claw's tip): up to this far in front counts as at the mouth (model pixels). Every degree of correction costs
# a little, so the claws keep their own pose as far as they can (my picks).
MOUTH_SLACK = 1.2
TURN_COST = 3e-4


def reaim(big):
    """For each pose in REAIM_POSES and each claw, the correction (arm pitch, arm yaw, hand pitch, hand yaw; degrees)
    that puts these claws' pinch where the usual claws' pinch is in that pose: a longer claw bends in more at the elbow,
    a shorter one reaches out (a rotation of the hand alone cannot shorten a claw). Searched by halving steps, the
    smallest turns that do it."""
    key = tuple(sorted(big.items()))
    if key in _REAIMED:
        return _REAIMED[key]
    usual, these = model(), with_claws(big)
    out = {}
    for name in REAIM_POSES:
        out[name] = {}
        for side, s in SIDES:
            want = pinch_at(usual, side, fixed_pose(side, s, name, (0, 0, 0, 0)))

            slack = MOUTH_SLACK if name.endswith('mouth') else 0.0

            def cost(f):
                got = pinch_at(these, side, fixed_pose(side, s, name, f))
                ahead = want[2] - got[2]                    # how far in front of the wanted point (-Z is forward)
                dz = 0.0 if 0 <= ahead <= slack else min(abs(ahead), abs(ahead - slack))
                return (got[0] - want[0]) ** 2 + (got[1] - want[1]) ** 2 + dz * dz + TURN_COST * sum(v * v for v in f)
            f, c = [0.0] * 4, None
            c = cost(f)
            step = 16.0
            while step > 0.2:
                better = True
                while better:
                    better = False
                    for i in range(4):
                        for d in (step, -step):
                            g = list(f)
                            g[i] += d
                            cg = cost(g)
                            if cg < c:
                                f, c, better = g, cg, True
                step /= 2
            out[name][side] = tuple(round(v, 1) for v in f)
    _REAIMED[key] = out
    return out


def check_sexes():
    """The offline check of the re-aimed clips (CLAUDE.md's hard rule): for each sex and claw, how far its pinch lands
    from the usual claws' pinch at the mouth and on the shell, before and after re-aiming (model pixels)."""
    usual = model()
    for sex, big in SEXES.items():
        these, fix = with_claws(big), reaim(big)
        for name in REAIM_POSES:
            for side, s in SIDES:
                none = fixed_pose(side, s, name, (0, 0, 0, 0))
                want = pinch_at(usual, side, none)
                before = pinch_at(these, side, none)
                after = pinch_at(these, side, fixed_pose(side, s, name, fix[name][side]))
                ahead = want[2] - after[2]
                print(f'{sex:6} {name:11} {side:5} fix {fix[name][side]}  off by {math.dist(before, want):.2f} px'
                      f' -> {math.dist(after, want):.2f} px ({ahead:+.2f} in front; across {after[0] - want[0]:+.2f}, '
                      f'up {after[1] - want[1]:+.2f})')


def write_game():
    """Writes the crab's models, textures and clips into the savanna mod: the usual crab (the young), and since the
    animal life cycle step 3 (2026-11-19) the male (big claws, narrow flap), the female (small claws, broad flap) and
    the female carrying eggs under her flap (crab_berried), the looks the user confirmed in the life viewer
    (tools/life_looks.py); the male's and the female's clips re-aimed for their claws (crab_male, crab_female; the
    berried female plays the female's)."""
    import life_looks

    def game_files(name, bones):
        game_bones, rows, w, h = paint(bones)
        for b in game_bones:
            b.pop('pinch', None)
        return {f'textures/entity/{name}.png': png(rows), f'geo/entity/{name}.geo.json': geo(name, game_bones, w, h, (2, 1.5, [0, 0.5, 0]))}

    files = game_files('crab', model(game=True))
    files['animations/entity/crab.animation.json'] = animations(clips())
    with life_looks.Recolour(sys.modules[__name__], wrap=life_looks.extra_parts(life_looks.CRAB_EXTRA)):
        files.update(game_files('crab_male', life_looks.crab_model(SEXES['male'], 'narrow', game=True)))
        files.update(game_files('crab_female', life_looks.crab_model(SEXES['female'], 'broad', game=True)))
        files.update(game_files('crab_berried', life_looks.crab_model(SEXES['female'], 'broad', eggs=True, game=True)))
    for sex, big in SEXES.items():
        files[f'animations/entity/crab_{sex}.animation.json'] = animations(clips(big))
    # faces=False: its old faces until its in-game film is compared with its approved version (BUGS 245; step C,
    # 2026-11-20, proved the monitor, crocodile and fly first)
    write_assets(files, faces=False)


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['game']:
        write_game()
        sys.exit()
    bones = model()
    n = sum(len(b.get('cubes', [])) for b in bones)
    lo, hi = bounds(bones)
    _, rows, w, h = paint(bones)
    print(f'{n} boxes, texture {w} x {h}, width {(hi[0] - lo[0]) / 16:.2f} blocks, length {(hi[2] - lo[2]) / 16:.2f}, '
          f'lowest {lo[1] / V:.2f} and highest {hi[1] / V:.2f} voxels')
