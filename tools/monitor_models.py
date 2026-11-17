"""The Nile monitor (M9 river life, planned with the crocodile's nests, which it raids), its model for the model viewer
(python tools/model_viewer.py monitor).

Rebuilt 2026-11-17 from the user's picture (a big blocky Minecraft Nile monitor on a sand bank) after two sculpted
versions: the user, "to sculpted ... to many pixels it looks really bad", "this image is perfect". So the shape is now
a few big plain boxes, as Minecraft's own mobs are, and the detail is in the paint; the colours and the pattern of the
sculpted model are kept (the user liked them; the old model is in monitor_models_sculpted.py until the user picks).
Fun first, it only has to look good in the game (animals are generalized, CLAUDE.md). What it is:
- About 73 cubes long, measured off the picture: the head 8, long and low (a skull box, a snout box, a blunt tip, each a
  little lower than the one behind; only about 4 tall with the jaw, half the body's depth), the neck 7 in two boxes
  rising gently so the head rides a little above the back, the body 23.5 in a chest box and a hips box with a sagging
  belly box under them, the tail 35 in twelve boxes, taller than wide (a monitor's tail is flattened from the sides),
  stepping down and in to a point, lying on the ground from about its middle.
- Heavy and high: the body deep and wide with the belly clear of the ground, standing on thick bent legs (the picture's
  monitor stands up, it does not drag its belly).
- The legs as the picture has them (the user, 2026-11-17: "see how the back legs come out at a different angle than
  front legs to brace itself"): the front legs go out from the shoulders to elbows held wide and a little back, the
  forearms down to the feet, which point forward and a little out; the hind legs sprawl wider, the thighs out to the
  side, knees out, high and forward, the shins down and back, the feet turned out and back, bracing. Each foot is a broad palm with five thick toes fanned out (each toe its own bone),
  each ending in a long pale claw that comes out and then down to the ground (the user: "claws actually come out and
  down").
- The colours: a warm charcoal (never pure black), a golden yellow with more red than a lemon has, in four close shades
  each; mostly dark, yellow in rows of blotches across the back, rings on the tail, a net of yellow and dark low on the
  flank, cream underneath with faint bars; dark lips with yellow bars, a yellow stripe back from the eye, a cream jaw
  with dark spots. LOOKS has two other yellows to compare.
- Not rigid: neck in two bones, head, jaw, a throat pouch that pumps and swells, a tongue in two bones with two tines
  that slides out of the closed mouth, each pupil and each lower eyelid a bone (lizards blink with the lower lid),
  chest and hips that bend against each other, six tail bones, each leg three bones (upper, lower, foot) and a bone a
  toe.
- The legs are placed by where the foot should stand (reach), so in the walk each foot stays planted on the ground
  while the body passes over it, then lifts and swings forward.

Units are model pixels (16 a block before the game's scale), the head points to -Z, up is +Y; y = 0 is the ground.
Turns are written as the model file wants them; the viewer maps them (x, y, z) -> (-x, y, -z) as for the crab.
"""
import copy
import math
import random

from tilapia_models import cube, mix, shade, faces, face_point, png, clip  # noqa: F401 (png for the viewer)

TEXELS = 2.0          # texture pixels a model pixel
SPLIT = 9.0           # the mouth line
HZ = 0.0              # (kept for the viewer's old head offset; this model writes the head where it is)
HEAD_BACK = -19.5     # where the head meets the neck
SKULL, SNOUT, TIP, TIP_END = -19.5, -23.5, -26.5, -27.5     # the head's three boxes, back to front
EYE_Z, EYE_Y = (-23.5, -22.0), (10.5, 11.5)
NECK, TAIL_Z, TAIL_END = -12.5, 11.0, 46.0    # where the neck ends, the tail starts and ends
SEG = 6.0                                     # each tail bone's length (the last is shorter)
FAR_TAIL = 29.0                               # from here the tail is ringed all the way round
BODY_Y = 7.5                                  # the chest's and hips' pivot height

# The big boxes of the neck and body: (bone, z from, z to, bottom, top, half-width).
NECK_BOXES = [('neck2', -19.5, -16.0, 6.75, 12.0, 2.75), ('neck1', -16.0, -12.5, 5.75, 11.75, 3.25)]
BODY_BOXES = [('chest', -12.5, -9.5, 5.0, 11.0, 3.75), ('chest', -9.5, 0.0, 4.5, 11.25, 4.75),
              ('hips', 0.0, 7.5, 4.75, 11.0, 4.75), ('hips', 7.5, 11.0, 5.0, 10.75, 3.75)]
BELLY = [('chest', -9.0, 0.0, 4.0, 4.5, 4.0), ('hips', 0.0, 6.0, 4.25, 4.75, 3.75)]   # the belly sagging between the legs
# The tail's outline at a few places (z, bottom, top, half-width); each tail bone holds two boxes read off it. Taller
# than wide all the way, as a monitor's tail is.
TAIL_SHAPE = [(11.0, 5.0, 10.75, 3.25), (15.0, 4.25, 10.0, 2.75), (19.0, 3.5, 9.0, 2.45), (23.0, 2.75, 8.0, 2.2),
              (27.0, 2.0, 7.0, 2.0), (32.0, 1.25, 5.75, 1.75), (36.0, 0.5, 4.5, 1.5), (40.0, 0.0, 3.25, 1.15),
              (43.0, 0.0, 2.25, 0.8), (46.0, 0.0, 1.25, 0.45)]
TAIL = ['tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'tail6']

# The legs: where each leaves the body, its upper and lower parts' lengths and thicknesses, where its wrist stands at
# rest (well inside the leg's full reach, so it stands bent), which way the elbow or knee points (out, up and back for
# the arm, out, up and forward for the leg, as a lizard's do), how far the foot turns out (the front foot forward and a
# little out, the hind foot out and back, bracing), the five toes' lengths (inner to outer) and how far they fan. For
# the left side; the right is its mirror.
LEGS = {'arm': dict(parent='chest', at=(4.0, 8.25, -9.5), upper=4.25, thick=4.5, lower=5.5, thin=4.0,
                    hand=(7.5, 1.5, -11.25), pole=(1.0, 1.2, 0.5), toe_out=18,
                    toes=(1.5, 2.0, 2.5, 2.25, 1.75), fan=14),
        'leg': dict(parent='hips', at=(4.0, 8.25, 9.25), upper=4.5, thick=5.0, lower=5.5, thin=4.25,
                    hand=(7.5, 1.5, 12.75), pole=(1.0, 1.0, -0.8), toe_out=90,
                    toes=(1.25, 2.0, 2.25, 2.5, 1.75), fan=14)}
WRIST_UP = 1.5        # the wrist (the foot's pivot) above the sole
SIDES = (('left', 1), ('right', -1))


def lerp_shape(z):
    """(bottom, top, half-width) of the tail at z."""
    for (z0, *a), (z1, *b) in zip(TAIL_SHAPE, TAIL_SHAPE[1:]):
        if z <= z1:
            f = max(0.0, min(1.0, (z - z0) / (z1 - z0)))
            return tuple(p + (q - p) * f for p, q in zip(a, b))
    return tuple(TAIL_SHAPE[-1][1:])


def q(v):
    return round(v * 4) / 4


def tail_boxes():
    """Every tail box: (bone, z from, z to, bottom, top, half-width), two to a bone, the last bone with the tip."""
    out = []
    for i, bone in enumerate(TAIL):
        z0 = TAIL_Z + SEG * i
        z1 = TAIL_END if bone == 'tail6' else z0 + SEG
        cuts = [z0, (z0 + z1) / 2, z1]
        for a, b in zip(cuts, cuts[1:]):
            bot, top, hw = lerp_shape((a + b) / 2)
            out.append((bone, a, b, q(bot), q(top), max(0.25, q(hw))))
    return out


TAIL_BOXES = tail_boxes()
SLICES = sorted(NECK_BOXES + BODY_BOXES + TAIL_BOXES, key=lambda s: s[1])


def box(cubes, x0, y0, z0, x1, y1, z1, part):
    cubes.append(cube([min(x0, x1), y0, z0], [max(x0, x1), y1, z1], part))


def both(cubes, x0, y0, z0, x1, y1, z1, part):
    box(cubes, x0, y0, z0, x1, y1, z1, part)
    box(cubes, -x1, y0, z0, -x0, y1, z1, part)


def trunk(bones):
    for bone, z0, z1, bot, top, hw in NECK_BOXES + BODY_BOXES + BELLY + TAIL_BOXES:
        box(bones[bone], -hw, bot, z0, hw, top, z1, 'skin')


def head(bones):
    h, j = bones['head'], bones['jaw']
    # the upper head: a long low skull, a narrower snout, a blunt tip, each a little lower than the one behind (the
    # picture's long flat head, its top only a little over the back); a low brow ledge over each eye
    box(h, -2.75, SPLIT, SNOUT, 2.75, 12.5, SKULL, 'head')
    box(h, -2.25, SPLIT, TIP, 2.25, 11.75, SNOUT, 'head')
    box(h, -1.75, SPLIT, TIP_END, 1.75, 11.0, TIP, 'head')
    both(h, 2.5, 11.5, -24.0, 3.0, 12.0, -21.75, 'brow')
    both(h, 2.25, 10.75, -26.25, 2.31, 11.25, -25.75, 'nostril')
    both(h, 2.75, EYE_Y[0], EYE_Z[0], 2.81, EYE_Y[1], EYE_Z[1], 'iris')
    # the eyes: each pupil a bone that slides over the iris, with a white glint; each lower lid a bone, a low rim
    # under the eye that grows up over it to blink
    for side, s in SIDES:
        p = bones[f'pupil_{side}']
        box(p, s * 2.81, 10.7, -23.15, s * 2.87, 11.3, -22.35, 'pupil')
        box(p, s * 2.87, 11.05, -23.15, s * 2.9, 11.3, -22.9, 'glint')
        box(bones[f'eyelid_{side}'], s * 2.75, 10.5, -23.6, s * 2.97, 10.75, -21.9, 'lid')
    # the lower jaw, hinged at the back of the skull, a little narrower than the head over it
    box(j, -2.5, 7.75, SNOUT, 2.5, SPLIT, SKULL - 0.25, 'jaw')
    box(j, -2.0, 8.0, TIP + 0.25, 2.0, SPLIT, SNOUT, 'jaw')
    box(j, -1.5, 8.25, TIP_END + 0.25, 1.5, SPLIT, TIP + 0.25, 'jaw')
    # inside the mouth, seen when it gapes
    for z0, z1, w in ((TIP_END + 0.5, TIP, 1.0), (TIP, SNOUT, 1.5), (SNOUT, SKULL - 0.75, 2.0)):
        box(h, -w, SPLIT - 0.04, z0, w, SPLIT, z1, 'mouth')
        box(j, -w, SPLIT, z0, w, SPLIT + 0.04, z1, 'mouth')
    # the tongue, hidden in the closed mouth until it slides out: a stem in two bones and two tines
    box(bones['tongue'], -0.3, SPLIT - 0.15, -23.5, 0.3, SPLIT + 0.15, -21.0, 'tongue')
    box(bones['tongue_mid'], -0.3, SPLIT - 0.15, -26.0, 0.3, SPLIT + 0.15, -23.5, 'tongue')
    for side, s in SIDES:
        box(bones[f'tongue_{side}'], s * 0.02, SPLIT - 0.15, -27.0, s * 0.3, SPLIT + 0.15, -26.0, 'tongue_tip')
    # the throat pouch under the neck
    box(bones['throat'], -2.5, 6.0, -19.5, 2.5, 7.0, -15.5, 'throat')


# --- the legs ----------------------------------------------------------------------------------------------------------
# Turns are worked out as matrices and written as the three turns a bone takes (the viewer and the game turn a bone
# about Z, then Y, then X; the viewer's angles are the file's (x, y, z) as (-x, y, -z)).
def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def rot(r):
    rx, ry, rz = math.radians(-r[0]), math.radians(r[1]), math.radians(-r[2])
    ca, sa, cb, sb, cc, sc = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    return mul([[cc, -sc, 0], [sc, cc, 0], [0, 0, 1]],
               mul([[cb, 0, sb], [0, 1, 0], [-sb, 0, cb]], [[1, 0, 0], [0, ca, -sa], [0, sa, ca]]))


def flip(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


def turned(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def turns(m):
    """The file's three turns that make this matrix."""
    ay = math.asin(max(-1.0, min(1.0, -m[2][0])))
    return [-math.degrees(math.atan2(m[2][1], m[2][2])), math.degrees(ay), -math.degrees(math.atan2(m[1][0], m[0][0]))]


def unit(v):
    n = math.sqrt(sum(x * x for x in v)) or 1.0
    return [x / n for x in v]


def along(d, s):
    """The matrix that lays a limb part drawn along s * X along d, its front face kept facing along the body."""
    x = [s * v for v in d]
    k = x[2]
    z = unit([-k * x[0], -k * x[1], 1 - k * x[2]])
    y = [z[1] * x[2] - z[2] * x[1], z[2] * x[0] - z[0] * x[2], z[0] * x[1] - z[1] * x[0]]
    return [[x[i], y[i], z[i]] for i in range(3)]


def shoulder(kind, s):
    x, y, z = LEGS[kind]['at']
    return [s * x, y, z]


def hand(kind, s):
    x, y, z = LEGS[kind]['hand']
    return [s * x, y, z]


def reach(kind, s, wrist):
    """A leg bent to put its wrist at this point (in its body part's space): the elbow or knee is found from the two
    lengths and pushed toward the leg's own side (its pole). Returns the upper and lower parts' matrices."""
    d = LEGS[kind]
    S, l1, l2 = shoulder(kind, s), d['upper'], d['lower']
    v = [w - a for w, a in zip(wrist, S)]
    far = math.sqrt(sum(x * x for x in v))
    u = [x / far for x in v]
    far = max(abs(l1 - l2) + 0.05, min(l1 + l2 - 0.05, far))
    a = (l1 * l1 - l2 * l2 + far * far) / (2 * far)
    h = math.sqrt(max(0.0, l1 * l1 - a * a))
    p = [s * d['pole'][0], d['pole'][1], d['pole'][2]]
    k = sum(x * y for x, y in zip(p, u))
    qv = unit([x - k * y for x, y in zip(p, u)])
    elbow = [S[i] + a * u[i] + h * qv[i] for i in range(3)]
    end = [S[i] + far * u[i] for i in range(3)]
    return along(unit([e - a0 for e, a0 in zip(elbow, S)]), s), along(unit([w - e for w, e in zip(end, elbow)]), s)


def foot_flat(kind, s, yaw=0.0, pitch=0.0):
    """The foot's matrix when it lies flat, turned out by the leg's own angle (and turned or tipped a little more)."""
    return rot([pitch, -s * LEGS[kind]['toe_out'] + yaw, 0])


def leg_turns(upper, lower, foot):
    """The three bones' own turns for an upper part, lower part and foot given as matrices in the body part's space."""
    return [turns(upper), turns(mul(flip(upper), lower)), turns(mul(flip(lower), foot))]


def rest_turns(kind, s):
    upper, lower = reach(kind, s, hand(kind, s))
    return leg_turns(upper, lower, foot_flat(kind, s))


def leg(kind, side, s):
    """One leg's bones: upper, lower, foot and a bone for each toe. The upper and lower parts are each one thick box
    drawn straight out to the side from the shoulder; the bones' own turns bend them into place (rest_turns), the
    foot's turn lays it flat again. The foot is drawn pointing forward (-Z) round the wrist: a broad palm and five
    toes, each toe its own bone turned to fan them out, each ending in a claw that comes out and then down."""
    d = LEGS[kind]
    x0, y0, z0 = shoulder(kind, s)
    up, low, foot = [], [], []
    t, n = d['thick'] / 2, d['thin'] / 2
    elbow = x0 + s * d['upper']
    wrist = elbow + s * d['lower']
    box(up, x0 - s * 1.25, y0 - t, z0 - t, elbow + s * n * 0.8, y0 + t, z0 + t, 'limb')
    box(low, elbow - s * n * 0.4, y0 - n, z0 - n, wrist - s * 0.6, y0 + n, z0 + n, 'limb')
    sole = y0 - WRIST_UP
    box(foot, wrist - 2.25, sole, z0 - 1.5, wrist + 2.25, sole + 1.75, z0 + 1.75, 'foot')
    name = f'{kind}_{{}}_{side}'
    toes = []
    for i, length in enumerate(d['toes']):
        tx = wrist + s * (i - 2) * 0.95
        back = z0 - 1.0 + abs(i - 2) * 0.4
        cubes = []
        box(cubes, tx - 0.5, sole + 0.1, back - length, tx + 0.5, sole + 1.2, back, 'toe')
        box(cubes, tx - 0.45, sole + 0.3, back - length - 0.9, tx + 0.45, sole + 1.2, back - length, 'claw')
        box(cubes, tx - 0.38, sole, back - length - 1.65, tx + 0.38, sole + 0.85, back - length - 0.85, 'claw')
        toes.append({'name': name.format(f'toe{i}'), 'parent': name.format('foot'), 'pivot': [tx, sole, back],
                     'rotation': [0, round(-s * (i - 2) * d['fan'], 3), 0], 'cubes': cubes})
    r = [[round(v, 3) for v in t3] for t3 in rest_turns(kind, s)]
    return [{'name': name.format('upper'), 'parent': d['parent'], 'pivot': [x0, y0, z0], 'rotation': r[0], 'cubes': up},
            {'name': name.format('lower'), 'parent': name.format('upper'), 'pivot': [elbow, y0, z0], 'rotation': r[1],
             'cubes': low},
            {'name': name.format('foot'), 'parent': name.format('lower'), 'pivot': [wrist, y0, z0], 'rotation': r[2],
             'cubes': foot}] + toes


def model():
    names = ['head', 'jaw', 'throat', 'tongue', 'tongue_mid', 'tongue_left', 'tongue_right', 'pupil_left',
             'pupil_right', 'eyelid_left', 'eyelid_right', 'neck1', 'neck2', 'chest', 'hips'] + TAIL
    bones = {n: [] for n in names}
    trunk(bones)
    head(bones)
    mid = {b: (bot + top) / 2 for b, z0, z1, bot, top, hw in TAIL_BOXES if z0 == TAIL_Z + SEG * TAIL.index(b)}
    out = [{'name': 'body', 'pivot': [0, BODY_Y, 0]},
           {'name': 'chest', 'parent': 'body', 'pivot': [0, BODY_Y, 0], 'cubes': bones['chest']},
           {'name': 'hips', 'parent': 'body', 'pivot': [0, BODY_Y, 0], 'cubes': bones['hips']},
           {'name': 'neck1', 'parent': 'chest', 'pivot': [0, 8.5, NECK], 'cubes': bones['neck1']},
           {'name': 'neck2', 'parent': 'neck1', 'pivot': [0, 9.25, -16.0], 'cubes': bones['neck2']}]
    for i, name in enumerate(TAIL):
        out.append({'name': name, 'parent': 'hips' if i == 0 else TAIL[i - 1],
                    'pivot': [0, round(mid[name], 2), TAIL_Z + SEG * i], 'cubes': bones[name]})
    out += [{'name': 'head', 'parent': 'neck2', 'pivot': [0, 10.0, HEAD_BACK], 'cubes': bones['head']},
            {'name': 'jaw', 'parent': 'head', 'pivot': [0, SPLIT, SKULL - 0.25], 'cubes': bones['jaw']},
            {'name': 'throat', 'parent': 'neck2', 'pivot': [0, 7.0, -17.5], 'cubes': bones['throat']},
            {'name': 'tongue', 'parent': 'head', 'pivot': [0, SPLIT, -21.0], 'cubes': bones['tongue']},
            {'name': 'tongue_mid', 'parent': 'tongue', 'pivot': [0, SPLIT, -23.5], 'cubes': bones['tongue_mid']}]
    for side, s in SIDES:
        out += [{'name': f'tongue_{side}', 'parent': 'tongue_mid', 'pivot': [s * 0.15, SPLIT, -26.0],
                 'rotation': [0, -s * 7, 0], 'cubes': bones[f'tongue_{side}']},
                {'name': f'pupil_{side}', 'parent': 'head', 'pivot': [s * 2.84, 11.0, -22.75],
                 'cubes': bones[f'pupil_{side}']},
                {'name': f'eyelid_{side}', 'parent': 'head', 'pivot': [s * 2.84, 10.5, -22.75],
                 'cubes': bones[f'eyelid_{side}']}]
    for kind in LEGS:
        for side, s in SIDES:
            out += leg(kind, side, s)
    return out


# --- colours -----------------------------------------------------------------------------------------------------------
# Each look: four darks (most used first), four yellows, the belly's cream and the tan between flank and belly.
LOOKS = {
    'A': {'name': 'Gold on charcoal', 'note': "your picture: a warm charcoal with golden-yellow blotches in rows, a "
          "cream belly and throat; the yellow kept golden (more red than a lemon has, a little muted) so it never "
          "turns greenish or neon against the dark",
          'dark': [(48, 45, 51), (61, 57, 62), (38, 36, 42), (78, 72, 76)],
          'yellow': [(210, 158, 50), (220, 170, 62), (190, 140, 42), (228, 186, 94)],
          'cream': (228, 208, 156), 'tan': (204, 176, 118)},
    'B': {'name': 'Straw on grey-brown', 'note': "paler and closer to a real old Nile monitor: straw-cream blotches on "
          "a grey-brown; softer, less contrast",
          'dark': [(64, 59, 54), (78, 72, 64), (53, 49, 46), (95, 88, 78)],
          'yellow': [(214, 190, 122), (226, 204, 140), (198, 172, 104), (236, 218, 162)],
          'cream': (232, 218, 180), 'tan': (206, 186, 142)},
    'C': {'name': 'Amber on black', 'note': "bolder: a deep amber-ochre on a near-black, like a freshly shed young "
          "monitor; the strongest contrast",
          'dark': [(37, 35, 40), (48, 45, 50), (29, 28, 32), (62, 57, 60)],
          'yellow': [(210, 146, 40), (224, 164, 52), (192, 128, 34), (234, 184, 78)],
          'cream': (224, 198, 142), 'tan': (196, 162, 102)}}
GAME_LOOK = 'A'


def dice(a, b, c):
    """0 to 1, the same for the same cube every time."""
    n = (int(a) * 374761393 + int(b) * 668265263 + int(c) * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) / 4294967296.0


def section(z):
    for _, z0, z1, bot, top, hw in SLICES:
        if z < z1:
            return bot, top, hw
    return SLICES[-1][3:]


def tone(shades, r):
    """One of a colour's shades: the first two most of the time, the deep one less, the light one now and then."""
    return shades[0] if r < 0.36 else shades[1] if r < 0.66 else shades[2] if r < 0.86 else shades[3]


def skin(x, y, z, face):
    """The neck, body and tail, texel by texel (the picture's pattern is as fine as its pixels): 'Y' yellow, 'D' dark,
    'T' tan, 'C' cream, 'B' a faint belly bar."""
    bot, top, hw = section(z)
    bot = min(bot, y)                         # the sagging belly lies below its slice
    g = abs(x) + (top - y)                    # how far round the body from the spine
    around = hw + (top - bot)                 # the spine to the belly's middle, about
    u = g / around
    side = 1 if x >= 0 else -1
    tz, tg = math.floor(z * TEXELS + 1e-4), math.floor(g * TEXELS + 1e-4)
    r = dice(tz, tg, side + 3)
    tail = z >= TAIL_Z
    if tail:                                  # rings, closer toward the tip (texels from one to the next)
        period, start = (7, TAIL_Z) if z < 23 else (6, 23) if z < 35 else (5, 35)
    elif z < NECK:
        period, start = 5, HEAD_BACK          # the neck: closer rows
    else:
        period, start = 6, NECK
    k = tz - math.floor(start * TEXELS)
    if not tail:                              # the rows wander a texel forward or back as they go round
        k += int(dice(tg // 3, k // period, side + 7) * 3) - 1
    band, at = k // period, k % period
    if tail:                                  # a ring 2 or 3 texels long, its edges ragged
        edge = int(dice(tg // 2, band, side + 19) < 0.3)
        blotch = edge <= at < (3 if period >= 6 else 2) + edge
    else:
        # a row of blotches across the back: each 2 to 4 texels round and 2 to 4 along, a dark texel or two between,
        # set a texel forward or back of its neighbours; now and then one missing, now and then two run together, and
        # a corner nipped off so they are not all squares
        lane = tg + 2 * (band % 2)
        cell = lane // 4
        wide = 4 if dice(band, cell, side + 21) < 0.22 else 2 + (dice(band, cell, side + 11) < 0.55)
        long = 2 + int(dice(band, cell, side + 13) * 2.4)
        first = int(dice(band, cell, side + 15) * 2)
        blotch = lane % 4 < wide and first <= at < first + long and dice(band, cell, side + 17) > 0.12
        if blotch and lane % 4 in (0, wide - 1) and at in (first, first + long - 1) and r < 0.4:
            blotch = False
    if face == 'down' or u > 0.88:            # underneath: cream with faint bars and a little dark mottling
        if z > FAR_TAIL:                            # the far tail is ringed all the way round
            return 'Y' if blotch and r < 0.85 else 'D'
        return 'B' if (at == period - 1 and r < 0.5) or r < 0.06 else 'C'
    if u > 0.75:                              # the edge of the belly: tan breaking into the dark
        if z > FAR_TAIL:
            return 'Y' if blotch and r < 0.85 else 'D'
        return 'T' if r < 0.5 else 'C' if r < 0.68 else 'D' if r < 0.88 else 'Y'
    if u > 0.57:                              # low on the flank: a net, yellow round dark spots
        if z > FAR_TAIL:
            return 'Y' if blotch and r < 0.85 else 'D'
        hole = (tz + 2 * ((tg // 3) % 2)) % 4 < 2 and tg % 3 < 2
        if hole:
            return 'D' if r < 0.85 else 'T'
        return 'Y' if r < 0.78 else 'T' if r < 0.9 else 'D'
    if blotch:
        return 'Y' if r < 0.92 else 'D'
    return 'Y' if r < 0.035 else 'D'


def colour(part, look, x, y, z, face, rnd):
    P = LOOKS[look]
    cx, cy, cz = math.floor(x + 1e-4), math.floor(y + 1e-4), math.floor(z + 1e-4)
    r = dice(cx * 3 + cy * 7, cz, 21 if face in ('up', 'down') else 22 if face in ('east', 'west') else 23)
    grain = rnd.uniform(0.985, 1.015)
    dark, yellow, cream, tan = tone(P['dark'], r), tone(P['yellow'], r), P['cream'], P['tan']
    paint_of = {'Y': yellow, 'D': dark, 'T': mix(tan, yellow, 0.55), 'C': cream if r < 0.75 else mix(cream, tan, 0.5),
                'B': mix(cream, P['dark'][1], 0.3)}
    if part == 'pupil' or part == 'nostril':
        return (14, 13, 15)
    if part == 'glint':
        return (250, 248, 240)
    if part == 'iris':
        edge = y < EYE_Y[0] + 0.25 or y > EYE_Y[1] - 0.25 or z < EYE_Z[0] + 0.25 or z > EYE_Z[1] - 0.25
        return shade((156, 96, 34) if edge else (206, 142, 50), grain)
    if part == 'lid':
        return shade(mix(P['yellow'][2], P['dark'][1], 0.2), grain)
    if part == 'mouth':
        return shade((206, 124, 128) if r < 0.5 else (194, 110, 116), grain)
    if part == 'tongue':
        return shade((214, 122, 142) if cz % 2 else (204, 110, 132), grain)
    if part == 'tongue_tip':
        return shade((190, 98, 122), grain)
    if part == 'claw':
        return shade((236, 226, 200) if face == 'up' else (216, 202, 170), grain)
    tx, ty, tw = (math.floor(v * TEXELS + 1e-4) for v in (x, y, z))
    spot = dice(tx * 31 + ty, ty * 17 + tw, 61) < 0.17          # single yellow dots, now and then two together
    limb_dark = mix(dark, P['dark'][3], 0.35)
    if part == 'toe':                         # dark with yellow bands across the knuckles
        return shade(tan if face == 'down' else yellow if tw % 3 == 0 and r < 0.8 else limb_dark, grain)
    if part == 'foot':
        return shade(tan if face == 'down' else yellow if spot else limb_dark, grain)
    if part == 'limb':                        # spotted: yellow dots on dark, a paler underside
        if face == 'down':
            return shade(yellow if spot else mix(limb_dark, tan, 0.2), grain)
        return shade(yellow if spot else limb_dark, grain)
    if part == 'throat':
        return shade(cream if face == 'down' or r < 0.7 else mix(cream, tan, 0.6), grain)
    fx, fy, fz = (math.floor(v * 2 + 1e-4) for v in (x, y, z))
    if part == 'jaw':                         # cream with dark flecks, the lip's edge dark
        if face == 'down':
            return shade(cream if r < 0.85 else mix(cream, tan, 0.6), grain)
        if y > SPLIT - 0.5 and face in ('east', 'west'):
            return shade(dark if dice(fz, 3, 44) < 0.7 else mix(cream, tan, 0.6), grain)
        fleck = dice(fx * 7 + fy, fz, 43) < 0.12
        return shade(dark if fleck else cream if r < 0.7 else mix(cream, tan, 0.6), grain)
    if part == 'brow':                        # the yellow line over the eye
        return shade(yellow if face in ('east', 'west') else dark, grain)
    if part == 'head':
        if face in ('up', 'south', 'north'):  # fine speckles, a little thicker toward the back of the skull
            fleck = dice(fx, fz, 41) < (0.10 if z < SNOUT else 0.17)
            return shade(yellow if fleck else dark, grain)
        if face == 'down':
            return shade(dark, grain)
        if y < SPLIT + 0.5:                   # the upper lip: a yellow line, broken here and there
            return shade(yellow if dice(fz, 1, 45) < 0.82 and z < SKULL - 0.5 else dark, grain)
        if EYE_Y[0] - 0.25 < y < EYE_Y[1] - 0.5 and z > EYE_Z[1]:     # the stripe from the eye back along the temple
            return shade(yellow, grain)
        if EYE_Y[0] - 0.75 < y < EYE_Y[0] and EYE_Z[0] - 0.5 < z < EYE_Z[1] + 0.25:   # yellow under the eye
            return shade(yellow, grain)
        if EYE_Y[0] - 0.25 < y < EYE_Y[1] and EYE_Z[0] - 0.75 < z < EYE_Z[0]:          # and just in front of it
            return shade(yellow, grain)
        fleck = dice(fy, fz, 47) < 0.1
        return shade(yellow if fleck else dark, grain)
    # the neck, body and tail
    k = skin(x, y, z, face)
    if z < -15.5 and face in ('east', 'west'):
        bot, top, _ = section(z)              # the temple's stripe runs on a little way down the neck
        if 0.2 < (top - y) / (top - bot) < 0.4:
            k = 'Y'
    return shade(paint_of[k], grain)


def paint(bones, look=GAME_LOOK):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height). Every look packs the same
    way, so the looks share one model."""
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


# --- clips -------------------------------------------------------------------------------------------------------------
WALK_LEN, SWIM_LEN, HISS_LEN, IDLE_LEN = 1.0, 1.1, 2.4, 8.0
TONGUE_LEN, BLINK_LEN = 0.8, 0.3
TONGUE_OUT = 4.0                              # how far the tongue slides out (cubes)
STEP, ON_GROUND, STEP_UP = 2.5, 0.6, 1.5      # the walk: half a step's length (cubes), the share of the stride a foot
#                                               stands, how high a foot lifts
BEND = 8.0                                    # how far chest and hips turn against each other in the walk (degrees)
REST = {(kind, s): rest_turns(kind, s) for kind in LEGS for _, s in SIDES}


def sm(a, b, x):
    v = max(0.0, min(1.0, (x - a) / (b - a)))
    return v * v * (3 - 2 * v)


def stride():
    """How far one whole walk cycle carries it (cubes): what a standing foot sweeps back under the body."""
    return 2 * STEP / ON_GROUND


def near(a):
    return (a + 180.0) % 360.0 - 180.0


def set_leg(p, kind, side, s, upper, lower, foot):
    """Writes a leg's three bones into a pose, as what each turns away from its rest."""
    for part, now, rest in zip(('upper', 'lower', 'foot'), leg_turns(upper, lower, foot), REST[(kind, s)]):
        p[f'{kind}_{part}_{side}'] = {'rotation': [near(a - b) for a, b in zip(now, rest)]}


def stand(p, kind, side, s, wrist=None, part_yaw=0.0, lifted=0.0, foot_yaw=0.0, foot_pitch=0.0):
    """A leg with its foot on a point of the ground (the body's own space; its rest place if none), whatever the body
    does: its body part turned (degrees) or the body lifted (cubes). The foot stays level and pointing its own way."""
    wrist = list(wrist or hand(kind, s))
    wrist[1] -= lifted
    back = flip(rot([0, part_yaw, 0]))        # into the turned chest's or hips' own space
    local = turned(back, [wrist[0], wrist[1] - BODY_Y, wrist[2]])
    local[1] += BODY_Y
    upper, lower = reach(kind, s, local)
    set_leg(p, kind, side, s, upper, lower, mul(back, foot_flat(kind, s, foot_yaw, foot_pitch)))


def trail(p, kind, side, s):
    """A leg folded back along the body, the foot in line with it: swimming."""
    first = unit([s * 0.55, -0.22, 1.0])
    second = unit([s * 0.12, -0.12, 1.0])
    f = unit([second[0], 0.0, second[2]])
    foot = [[-f[2], 0, -f[0]], [0, 1, 0], [f[0], 0, -f[2]]]        # level, the toes pointing along the lower leg
    set_leg(p, kind, side, s, along(first, s), along(second, s), foot)


def tail_wave(p, t, beat, amounts, lag, lead=0.0):
    """A wave running down the tail: each bone's own bend (degrees), later toward the tip."""
    for i, (name, a) in enumerate(zip(TAIL, amounts)):
        p[name] = {'rotation': [0, a * math.sin(beat * t - lead - lag * i), 0]}


def clips():
    """idle (standing: looking slowly about, the throat pumping, the tail's end stirring), walk (the lizard's walk:
    legs in diagonal pairs, each foot planted while the body passes over it, chest and hips bending against each other,
    the tail snaking, the head held steady), swim (legs folded back, the body and tail driving), hiss (standing tall,
    neck arched, mouth open, throat swollen, the tail lashing), and two short ones played over the others: tongue (it
    slides out of the closed mouth, flickers up and down, the tines spreading, and back in) and blink (the lower lids
    rise over the eyes)."""
    def idle(t):
        w = 2 * math.pi / IDLE_LEN
        pump = max(0.0, math.sin(2 * math.pi * t / 1.6)) ** 2
        up = 0.1 * math.sin(2 * w * t)
        p = {'body': {'rotation': [0, 0, 0], 'position': [0, up, 0]},
             'neck1': {'rotation': [1.5 * math.sin(2 * w * t), 5 * math.sin(w * t), 0]},
             'neck2': {'rotation': [0, 6 * math.sin(w * t + 0.4), 0]},
             'head': {'rotation': [3 * math.sin(2 * w * t + 1.0), 8 * math.sin(w * t + 0.8), 0]},
             'throat': {'scale': [1 + 0.1 * pump, 1 + 0.4 * pump, 1]}}
        tail_wave(p, t, w, [1, 2, 3, 5, 7, 9], 0.6)
        for kind in LEGS:
            for side, s in SIDES:
                stand(p, kind, side, s, lifted=up)
        return p

    def walk(t):
        ph = t / WALK_LEN
        bend = BEND * math.cos(2 * math.pi * (ph - 0.05))
        up = 0.25 * abs(math.sin(2 * math.pi * ph))
        p = {'body': {'rotation': [0, 0, 0], 'position': [0, up, 0]},
             'chest': {'rotation': [0, bend, 0]}, 'hips': {'rotation': [0, -bend, 0]},
             'neck1': {'rotation': [0, -0.45 * bend, 0]}, 'neck2': {'rotation': [0, -0.35 * bend, 0]},
             'head': {'rotation': [0, -0.2 * bend, 0]}}
        tail_wave(p, t, 2 * math.pi / WALK_LEN, [9, 9, 10, 11, 12, 12], 0.75, lead=-1.2)
        for kind in LEGS:
            for side, s in SIDES:
                a = (ph + (0.0 if (kind == 'arm') == (s > 0) else 0.5) + (0.06 if kind == 'leg' else 0.0)) % 1.0
                x, y, z = hand(kind, s)
                pitch = 0.0
                if a < ON_GROUND:             # planted: the ground carries it back under the body
                    z += -STEP + 2 * STEP * a / ON_GROUND
                else:                         # lifted and swung forward, the toes hanging
                    f = (a - ON_GROUND) / (1 - ON_GROUND)
                    z += STEP - 2 * STEP * sm(0.0, 1.0, f)
                    y += STEP_UP * math.sin(math.pi * f)
                    x += s * 0.5 * math.sin(math.pi * f)
                    pitch = 15 * math.sin(math.pi * f)
                stand(p, kind, side, s, [x, y, z], part_yaw=bend if kind == 'arm' else -bend, lifted=up, foot_pitch=pitch)
        return p

    def swim(t):
        w = 2 * math.pi / SWIM_LEN
        bend = 4 * math.sin(w * t)
        p = {'chest': {'rotation': [0, bend, 0]}, 'hips': {'rotation': [0, -6 * math.sin(w * t - 0.6), 0]},
             'neck1': {'rotation': [-4, -0.6 * bend, 0]}, 'neck2': {'rotation': [-5, -0.4 * bend, 0]},
             'head': {'rotation': [9, 0, 0]}}
        tail_wave(p, t, w, [10, 14, 18, 20, 20, 18], 0.7, lead=1.2)
        for kind in LEGS:
            for side, s in SIDES:
                trail(p, kind, side, s)
        return p

    def hiss(t):
        w = 2 * math.pi / HISS_LEN
        breath = 0.5 + 0.5 * math.sin(2 * w * t)
        up = 1.2 + 0.1 * breath
        sway = 3 * math.sin(w * t)
        p = {'body': {'rotation': [0, 0, 0], 'position': [0, up, 0]},
             'chest': {'rotation': [0, sway, 0]},
             'neck1': {'rotation': [-20, 0, 0]}, 'neck2': {'rotation': [-8, 0, 0]},
             'head': {'rotation': [24 + 2 * breath, 4 * math.sin(w * t + 1.0), 0]},
             'jaw': {'rotation': [20 + 6 * breath, 0, 0]},
             'throat': {'scale': [1.35 + 0.15 * breath, 1.8 + 0.3 * breath, 1.1]}}
        tail_wave(p, t, 2 * w, [10, 14, 18, 20, 22, 22], 0.6)
        for kind in LEGS:
            for side, s in SIDES:
                stand(p, kind, side, s, part_yaw=sway if kind == 'arm' else 0.0, lifted=up)
        return p

    def tongue(t):
        u = t / TONGUE_LEN
        out = sm(0.0, 0.16, u) - sm(0.78, 1.0, u)
        flick = math.sin(2 * math.pi * 3 * u) * out
        p = {'tongue': {'position': [0, 0, -TONGUE_OUT * out], 'rotation': [0, 0, 0]},
             'tongue_mid': {'rotation': [22 * flick, 0, 0]}}
        for side, s in SIDES:
            p[f'tongue_{side}'] = {'rotation': [8 * flick, -s * 9 * out, 0]}
        return p

    def blink(t):
        u = t / BLINK_LEN
        shut = sm(0.0, 0.3, u) - sm(0.55, 1.0, u)
        cover = (EYE_Y[1] - EYE_Y[0] + 0.05) / 0.25 - 1     # the lid is 0.25 tall; grown, it covers the eye
        return {f'eyelid_{side}': {'scale': [1, 1 + cover * shut, 1]} for side, _ in SIDES}

    return {'idle': clip(IDLE_LEN, idle, 64), 'walk': clip(WALK_LEN, walk, 40), 'swim': clip(SWIM_LEN, swim, 24),
            'hiss': clip(HISS_LEN, hiss, 48), 'tongue': clip(TONGUE_LEN, tongue, 32, False),
            'blink': clip(BLINK_LEN, blink, 12, False)}


if __name__ == '__main__':
    import crab_models
    bones = model()
    n = sum(len(b.get('cubes', [])) for b in bones)
    _, rows, w, h = paint(bones)
    lo, hi = crab_models.bounds(bones)
    print(f'{n} boxes, {len(bones)} bones, texture {w} x {h}')
    print('lowest', [round(v, 2) for v in lo], 'highest', [round(v, 2) for v in hi], 'stride', round(stride(), 2))
    for b in bones:
        if 'rotation' in b and 'toe' not in b['name']:
            print(' ', b['name'], b['rotation'])
