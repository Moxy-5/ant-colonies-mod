"""The Nile monitor (M9 river life, planned with the crocodile's nests, which it raids), its model for the model viewer
(python tools/model_viewer.py monitor).

Started over 2026-11-17 from the user's picture (a blocky Minecraft Nile monitor on a sand bank), after a sculpted model
("to many pixels it looks really bad") and a blocky one patched three times that still looked "off" and "clunky"
(the user: "I almost want to just start over"; that one is kept in monitor_models_blocky.py as the viewer's "before").
Fun first, it only has to look good in the game (animals are generalized, CLAUDE.md). The rules this time:
- **One even pixel grid.** A model pixel is one of the picture's texture pixels and one texel on the texture
  (TEXELS = 1), and the body's boxes are whole pixels, so every square of paint is the same size, as in the picture.
- **The picture's proportions, measured off its side and top views** (88 pixels nose to tail tip): the head 9 long
  and 8 wide (wider than the neck, a wedge from 2 tall at the tip to 6 at the back), its top level with the back; the
  neck 10, rising a pixel to the shoulders; the body 27, 14 tall over a belly 5 clear of the ground, widest (12) at
  its middle; the tail 42 (just under half), taller than wide, stepping down and in every 3 or 4 pixels to a point
  near the ground.
- **The legs as the picture has them** (the user: "the back legs come out at a different angle than front legs to
  brace itself"): the front legs an L, the upper arm straight out sideways from the shoulder to an elbow held out at
  shoulder height, the forearm down to the foot, which points forward; the hind legs sprawl wider and back, the thigh
  out to a knee out to the side, the shin down and back, the foot turned well out. Thick legs (5 and 6 pixels).
- **Feet**: a broad palm and five toes, each a dark block ending in a pale claw that comes out and then down to the
  ground (the user: "claws actually come out and down").
- **The paint** keeps the old colours (a warm charcoal, a golden yellow, cream underneath; the user liked them) and
  copies the picture's pattern at its own size: rows of yellow blotches a pixel or two across the back with a dotted
  line down the spine, a yellow net low on the flanks breaking into the cream belly, yellow rings round the tail,
  yellow specks on the legs, a yellow brow and stripe at the eye, every square a little lighter or darker than its
  neighbours, as the picture's are.

The bones (and so the clips) are the old model's: neck in two, head, jaw, throat, a tongue in two with two tines,
pupils and lower eyelids, chest and hips, six tail bones, three bones a leg and a bone a toe. The legs are placed by
where each foot should stand (reach), so in the walk a foot stays planted while the body passes over it.

Units are model pixels (16 a block before the game's scale; the viewer sizes it by its length), the head points to
-Z, up is +Y; y = 0 is the ground. Turns are written as the model file wants them; the viewer maps them
(x, y, z) -> (-x, y, -z) as for the crab.
"""
import copy
import math
import random

from tilapia_models import cube, mix, shade, faces, face_point, png, clip  # noqa: F401 (png for the viewer)

TEXELS = 1.0          # one texel a model pixel: the picture's even grid
NOSE = -34            # z of the snout's tip (the body's middle is near 0)
SPLIT = 10.0          # the mouth line
HZ = 0.0              # (kept for the viewer's old head offset; the head is written where it is)
HEAD_BACK, NECK, TAIL_Z, TAIL_END = -25, -15, 12, 54     # where the head, neck, body and tail end
EYE_Z, EYE_Y = (-29.5, -26.5), (12.0, 14.0)              # the eye's socket on the side of the skull
FAR_TAIL = 38         # from here back the tail is ringed all the way round
BODY_Y = 10.0         # the chest's and hips' pivot height

# The trunk's boxes, front to back: (bone, z from, z to, bottom, top, half-width). Whole pixels; the half-widths of
# the head and neck halves so the width is whole.
NECK_BOXES = [('neck2', -25, -20, 7, 15, 3.5), ('neck1', -20, -15, 6, 16, 4.0)]
BODY_BOXES = [('chest', -15, -11, 5, 16, 5.5), ('chest', -11, -4, 4, 16, 6.5),
              ('hips', -4, 6, 4, 15, 7.0), ('hips', 6, 12, 5, 14, 6.0)]
# The tail: two boxes a bone, each a pixel lower on top and a little narrower than the one before.
TAIL = ['tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'tail6']
TAIL_STEPS = [(12, 16, 5, 13, 4.5), (16, 19, 5, 12, 4.0), (19, 23, 5, 11, 3.5), (23, 26, 4, 10, 3.5),
              (26, 30, 4, 9, 3.0), (30, 33, 4, 8, 3.0), (33, 37, 3, 8, 2.5), (37, 40, 3, 7, 2.5),
              (40, 44, 3, 6, 2.0), (44, 47, 2, 6, 1.5), (47, 51, 2, 5, 1.5), (51, 54, 2, 4, 1.0)]
TAIL_BOXES = [(TAIL[i // 2], *s) for i, s in enumerate(TAIL_STEPS)]
SLICES = sorted(NECK_BOXES + BODY_BOXES + TAIL_BOXES, key=lambda s: s[1])

# The legs (for the left side; the right is its mirror): where each leaves the body, its upper and lower parts'
# lengths and thicknesses, where its wrist stands at rest (the foot's pivot, WRIST_UP over the sole), which way the
# elbow or knee points (out and up, a little back for the arm, a little forward for the leg), how far the foot turns
# out, the five toes' lengths (inner to outer) and how far they fan.
LEGS = {'arm': dict(parent='chest', at=(5.5, 10.5, -12.0), upper=6.5, thick=7.0, lower=9.0, thin=6.0,
                    hand=(13.0, 2.5, -16.0), pole=(1.0, 1.5, 0.3), toe_out=12,
                    toes=(2.5, 3.0, 3.5, 3.5, 2.5), fan=11),
        'leg': dict(parent='hips', at=(6.0, 10.0, 9.0), upper=7.0, thick=7.5, lower=9.0, thin=6.5,
                    hand=(15.0, 2.5, 13.0), pole=(1.0, 1.3, -0.4), toe_out=62,
                    toes=(2.0, 3.0, 3.5, 4.0, 3.0), fan=13)}
WRIST_UP = 2.5
SIDES = (('left', 1), ('right', -1))


def box(cubes, x0, y0, z0, x1, y1, z1, part):
    cubes.append(cube([min(x0, x1), min(y0, y1), min(z0, z1)], [max(x0, x1), max(y0, y1), max(z0, z1)], part))


def both(cubes, x0, y0, z0, x1, y1, z1, part):
    box(cubes, x0, y0, z0, x1, y1, z1, part)
    box(cubes, -x1, y0, z0, -x0, y1, z1, part)


def trunk(bones):
    for bone, z0, z1, bot, top, hw in NECK_BOXES + BODY_BOXES + TAIL_BOXES:
        box(bones[bone], -hw, bot, z0, hw, top, z1, 'skin')


def head(bones):
    h, j = bones['head'], bones['jaw']
    # the upper head, a wedge in three steps: the skull, the snout a pixel lower and narrower, the blunt tip
    box(h, -4.0, SPLIT, -30, 4.0, 15, HEAD_BACK, 'head')
    box(h, -3.5, SPLIT, -33, 3.5, 14, -30, 'head')
    box(h, -3.0, SPLIT, NOSE, 3.0, 13, -33, 'head')
    # the eye's socket, a pixel proud of the skull's side; each pupil a bone (it slides) with a glint, each lower lid a
    # bone that grows up over the eye to blink; the nostrils near the tip
    both(h, 4.0, EYE_Y[0], EYE_Z[0], 4.02, EYE_Y[1], EYE_Z[1], 'iris')
    both(h, 2.8, 11, -34.02, 1.8, 12, -34.0, 'nostril')
    for side, s in SIDES:
        p = bones[f'pupil_{side}']
        box(p, s * 4.02, 12.0, -28.5, s * 4.06, 14.0, -27.5, 'pupil')
        box(p, s * 4.06, 13.5, -28.5, s * 4.08, 14.0, -28.0, 'glint')
        box(bones[f'eyelid_{side}'], s * 4.0, 11.75, EYE_Z[0], s * 4.1, 12.0, EYE_Z[1], 'lid')
    # the lower jaw, hinged at the back of the skull, deepest at the back where it meets the throat
    box(j, -4.0, 7, -30, 4.0, SPLIT, HEAD_BACK - 0.5, 'jaw')
    box(j, -3.5, 8, -33, 3.5, SPLIT, -30, 'jaw')
    box(j, -3.0, 8.5, NOSE, 3.0, SPLIT, -33, 'jaw')
    # inside the mouth, seen when it gapes
    for z0, z1, w in ((NOSE + 0.5, -33, 2.0), (-33, -30, 2.5), (-30, HEAD_BACK - 1, 3.0)):
        box(h, -w, SPLIT - 0.04, z0, w, SPLIT, z1, 'mouth')
        box(j, -w, SPLIT, z0, w, SPLIT + 0.04, z1, 'mouth')
    # the tongue, hidden in the closed mouth until it slides out: a stem in two bones and two tines
    box(bones['tongue'], -0.5, SPLIT - 0.2, -29.0, 0.5, SPLIT + 0.2, -26.0, 'tongue')
    box(bones['tongue_mid'], -0.5, SPLIT - 0.2, -32.0, 0.5, SPLIT + 0.2, -29.0, 'tongue')
    for side, s in SIDES:
        box(bones[f'tongue_{side}'], s * 0.02, SPLIT - 0.2, -33.5, s * 0.5, SPLIT + 0.2, -32.0, 'tongue_tip')
    # the throat pouch under the neck
    box(bones['throat'], -3.5, 5.5, -25, 3.5, 7, -19, 'throat')


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
    """One leg's bones: upper, lower, foot and a bone for each toe. The upper and lower parts are each one thick square
    box drawn straight out to the side from the shoulder; the bones' own turns bend them into place (rest_turns), the
    foot's turn lays it flat again. The foot is drawn pointing forward (-Z) round the wrist: a broad palm and five
    toes, each toe its own bone turned to fan them out, each ending in a pale claw that comes out and then down."""
    d = LEGS[kind]
    x0, y0, z0 = shoulder(kind, s)
    up, low, foot = [], [], []
    t, n = d['thick'] / 2, d['thin'] / 2
    elbow = x0 + s * d['upper']
    wrist = elbow + s * d['lower']
    box(up, x0 - s * 1.5, y0 - t, z0 - t, elbow + s * n, y0 + t, z0 + t, 'limb')
    box(low, elbow - s * n, y0 - n, z0 - n, wrist + s * 0.5, y0 + n, z0 + n, 'limb')
    sole = y0 - WRIST_UP
    box(foot, wrist - 4.5, sole, z0 - 2.5, wrist + 4.5, sole + 2.5, z0 + 2.5, 'foot')
    name = f'{kind}_{{}}_{side}'
    toes = []
    for i, length in enumerate(d['toes']):
        tx = wrist + s * (i - 2) * 1.9
        back = z0 - 2.0 + abs(i - 2) * 0.5
        cubes = []
        box(cubes, tx - 0.75, sole, back - length, tx + 0.75, sole + 1.5, back, 'toe')
        box(cubes, tx - 0.6, sole + 0.4, back - length - 1.2, tx + 0.6, sole + 1.5, back - length, 'claw')
        box(cubes, tx - 0.5, sole, back - length - 2.1, tx + 0.5, sole + 1.1, back - length - 1.1, 'claw')
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
    out = [{'name': 'body', 'pivot': [0, BODY_Y, 0]},
           {'name': 'chest', 'parent': 'body', 'pivot': [0, BODY_Y, -4], 'cubes': bones['chest']},
           {'name': 'hips', 'parent': 'body', 'pivot': [0, BODY_Y, -4], 'cubes': bones['hips']},
           {'name': 'neck1', 'parent': 'chest', 'pivot': [0, 11, NECK], 'rotation': [-14, 0, 0], 'cubes': bones['neck1']},
           {'name': 'neck2', 'parent': 'neck1', 'pivot': [0, 11, -20], 'rotation': [-8, 0, 0], 'cubes': bones['neck2']}]
    for i, name in enumerate(TAIL):
        z0, _, bot, top, _ = TAIL_STEPS[2 * i]
        out.append({'name': name, 'parent': 'hips' if i == 0 else TAIL[i - 1],
                    'pivot': [0, (bot + top) / 2, z0], 'cubes': bones[name]})
    out += [{'name': 'head', 'parent': 'neck2', 'pivot': [0, 11, HEAD_BACK], 'rotation': [18, 0, 0], 'cubes': bones['head']},
            {'name': 'jaw', 'parent': 'head', 'pivot': [0, SPLIT, HEAD_BACK - 0.5], 'cubes': bones['jaw']},
            {'name': 'throat', 'parent': 'neck2', 'pivot': [0, 7, -22.0], 'cubes': bones['throat']},
            {'name': 'tongue', 'parent': 'head', 'pivot': [0, SPLIT, -26.0], 'cubes': bones['tongue']},
            {'name': 'tongue_mid', 'parent': 'tongue', 'pivot': [0, SPLIT, -29.0], 'cubes': bones['tongue_mid']}]
    for side, s in SIDES:
        out += [{'name': f'tongue_{side}', 'parent': 'tongue_mid', 'pivot': [s * 0.25, SPLIT, -32.0],
                 'rotation': [0, -s * 8, 0], 'cubes': bones[f'tongue_{side}']},
                {'name': f'pupil_{side}', 'parent': 'head', 'pivot': [s * 4.04, 13.0, -28.0],
                 'cubes': bones[f'pupil_{side}']},
                {'name': f'eyelid_{side}', 'parent': 'head', 'pivot': [s * 4.05, 11.75, -28.0],
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
          'dark': [(52, 48, 54), (64, 59, 64), (42, 39, 45), (80, 74, 77)],
          'yellow': [(212, 160, 52), (222, 174, 66), (192, 142, 44), (232, 190, 98)],
          'cream': (226, 204, 150), 'tan': (198, 168, 108)},
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
    """0 to 1, the same for the same numbers every time."""
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
    """The neck, body and tail, square by square: 'Y' yellow, 'D' dark, 'T' tan, 'C' cream. g is how far round the body
    the square lies from the spine (along the back, then down the flank), u that as a share of the way to the belly."""
    bot, top, hw = section(z)
    if face == 'down':
        g = hw + (top - bot) + abs(x)
    elif face == 'up':
        g = abs(x)
    else:
        g = hw + (top - y)
    around = hw + (top - bot)
    u = g / around
    side = 1 if x >= 0 else -1
    tz, tg = math.floor(z + 1e-4), math.floor(g + 1e-4)
    r = dice(tz, tg, side + 3)
    if z >= TAIL_Z:
        # rings round the tail, closer toward the tip, each 2 squares long with a ragged edge
        period = 6 if z < 26 else 5 if z < 40 else 4
        start = TAIL_Z if z < 26 else 26 if z < 40 else 40
        k = tz - start
        at = k % period
        ring = at < period // 2 or (at == period // 2 and dice(tg, k // period, side + 19) < 0.3)
        ring = ring and dice(tg, k // period, side + 23) > 0.08
        if u > 0.8 and z < FAR_TAIL:           # cream underneath the tail's first half
            return 'C' if r < 0.7 else 'T'
        if u > 0.62 and z < FAR_TAIL:          # the tail's lower side: the rings run into tan
            return 'Y' if ring else 'T' if r < 0.25 else 'D'
        return 'Y' if ring else 'Y' if r < 0.04 else 'D'
    if u > 0.82:                               # the belly: cream, tan here and there
        return 'C' if r < 0.68 else 'T' if r < 0.95 else 'D'
    if u > 0.6:                                # low on the flank: a yellow net round dark squares, tan toward the belly
        if dice(tz // 2, tg // 2, side + 31) < 0.3 and r < 0.8:
            return 'D'
        return 'Y' if r < 0.7 else 'T' if r < 0.88 else 'D'
    # the back and upper flank: rows of yellow blotches across, a square or two each, a dark square or two between,
    # each row wandering a square forward or back as it goes round; a dotted line down the spine between the rows
    period = 4 if z < NECK else 5
    k = tz - (HEAD_BACK if z < NECK else NECK)
    k += int(dice(tg // 2, k // period, side + 7) * 3) - 1
    band, at = k // period, k % period
    if tg == 0:
        return 'Y' if at == period // 2 + 1 and r < 0.8 else 'Y' if r < 0.05 else 'D'
    lane = tg + (band % 2)
    cell = lane // 3
    wide = 1 + (dice(band, cell, side + 11) < 0.5)
    long = 1 + (dice(band, cell, side + 13) < 0.55)
    first = int(dice(band, cell, side + 15) * 2)
    blotch = lane % 3 < wide and first <= at < first + long + 1 and dice(band, cell, side + 17) > 0.12
    if u > 0.42 and not blotch:                # the blotches grow and join lower down the flank
        blotch = dice(tz, tg, side + 41) < 0.32
    return 'Y' if blotch and r < 0.94 else 'Y' if r < 0.04 else 'D'


def colour(part, look, x, y, z, face, rnd):
    P = LOOKS[look]
    tx, ty, tz = (math.floor(v + 1e-4) for v in (x, y, z))
    r = dice(tx * 3 + ty * 7, tz, 21 if face in ('up', 'down') else 22 if face in ('east', 'west') else 23)
    grain = 0.9 + 0.2 * dice(tx * 13 + ty, tz * 5 + ty, 29 + len(face))   # every square a little lighter or darker
    dark, yellow, cream, tan = tone(P['dark'], r), tone(P['yellow'], r), P['cream'], P['tan']
    paint_of = {'Y': yellow, 'D': dark, 'T': mix(tan, yellow, 0.4), 'C': cream if r < 0.75 else mix(cream, tan, 0.5)}
    if part in ('pupil', 'nostril'):
        return (16, 14, 17)
    if part == 'glint':
        return (246, 244, 236)
    if part == 'iris':
        return shade((150, 96, 38), grain)
    if part == 'lid':
        return shade(P['yellow'][2], grain)
    if part == 'mouth':
        return shade((200, 118, 122), grain)
    if part == 'tongue':
        return shade((212, 120, 140) if tz % 2 else (200, 108, 130), grain)
    if part == 'tongue_tip':
        return shade((188, 96, 120), grain)
    if part == 'claw':
        return shade((232, 222, 196) if face == 'up' else (210, 198, 166), grain)
    spot = dice(tx * 31 + ty, ty * 17 + tz, 61) < 0.22       # single yellow squares on the legs
    limb_dark = mix(dark, P['dark'][3], 0.25)
    if part == 'toe':
        return shade(tan if face == 'down' else yellow if r < 0.12 else limb_dark, grain)
    if part == 'foot':
        return shade(tan if face == 'down' else yellow if spot else limb_dark, grain)
    if part == 'limb':
        return shade(yellow if spot else mix(limb_dark, tan, 0.25) if face == 'down' else limb_dark, grain)
    if part == 'throat':
        return shade(cream if r < 0.65 else mix(cream, tan, 0.6), grain)
    if part == 'jaw':                          # cream and tan with dark flecks, the lip's edge dark
        if face == 'down':
            return shade(cream if r < 0.7 else mix(cream, tan, 0.6), grain)
        if face in ('east', 'west', 'north') and y > SPLIT - 1:
            return shade(dark if r < 0.55 else mix(tan, yellow, 0.5), grain)
        return shade(dark if r < 0.12 else cream if r < 0.6 else mix(cream, tan, 0.6), grain)
    if part == 'head':
        if face in ('up', 'north', 'south'):   # dark, a few yellow specks, more toward the back of the skull
            return shade(yellow if r < (0.07 if z < -30 else 0.14) else dark, grain)
        if face == 'down':
            return shade(dark, grain)
        e0, e1 = EYE_Z
        if y < SPLIT + 1:                      # the upper lip: tan and yellow breaking the dark
            return shade(mix(tan, yellow, 0.5) if r < 0.55 else dark, grain)
        if 14 <= y < 15 and e0 - 1 <= z < e1 + 1:          # the yellow brow over the eye
            return shade(yellow, grain)
        if 13 <= y < 14 and z >= e1:                        # the stripe from the eye back along the temple
            return shade(yellow if r < 0.9 else dark, grain)
        if 12 <= y < 13 and e0 - 1 <= z < e0:              # yellow just in front of the eye
            return shade(yellow, grain)
        return shade(yellow if r < 0.1 else dark, grain)
    # the neck, body and tail
    k = skin(x, y, z, face)
    if z < -20 and face in ('east', 'west') and 13 <= y < 14:     # the temple's stripe runs on down the neck
        k = 'Y' if r < 0.85 else k
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
WALK_LEN, SWIM_LEN, HISS_LEN, IDLE_LEN = 1.1, 1.2, 2.4, 8.0
TONGUE_LEN, BLINK_LEN = 0.8, 0.3
TONGUE_OUT = 6.0                              # how far the tongue slides out (pixels)
STEP, ON_GROUND, STEP_UP = 3.5, 0.6, 2.0      # the walk: half a step's length, the share of the stride a foot stands,
#                                               how high a foot lifts
BEND = 8.0                                    # how far chest and hips turn against each other in the walk (degrees)
REST = {(kind, s): rest_turns(kind, s) for kind in LEGS for _, s in SIDES}


def sm(a, b, x):
    v = max(0.0, min(1.0, (x - a) / (b - a)))
    return v * v * (3 - 2 * v)


def stride():
    """How far one whole walk cycle carries it (pixels): what a standing foot sweeps back under the body."""
    return 2 * STEP / ON_GROUND


def near(a):
    return (a + 180.0) % 360.0 - 180.0


def set_leg(p, kind, side, s, upper, lower, foot):
    """Writes a leg's three bones into a pose, as what each turns away from its rest."""
    for part, now, rest in zip(('upper', 'lower', 'foot'), leg_turns(upper, lower, foot), REST[(kind, s)]):
        p[f'{kind}_{part}_{side}'] = {'rotation': [near(a - b) for a, b in zip(now, rest)]}


def stand(p, kind, side, s, wrist=None, part_yaw=0.0, lifted=0.0, foot_yaw=0.0, foot_pitch=0.0):
    """A leg with its foot on a point of the ground (the body's own space; its rest place if none), whatever the body
    does: its body part turned (degrees) or the body lifted (pixels). The foot stays level and pointing its own way."""
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
    """idle (standing: looking slowly about, the throat pumping, the tail's end stirring), walk (legs in diagonal pairs,
    each foot planted while the body passes over it, chest and hips bending against each other, the tail snaking, the
    head held steady), swim (legs folded back, the body and tail driving), hiss (standing tall, neck arched, mouth
    open, throat swollen, the tail lashing), and two short ones played over the others: tongue (it slides out of the
    closed mouth, flickers up and down, the tines spreading, and back in) and blink (the lower lids rise over the
    eyes)."""
    def idle(t):
        w = 2 * math.pi / IDLE_LEN
        pump = max(0.0, math.sin(2 * math.pi * t / 1.6)) ** 2
        up = 0.15 * math.sin(2 * w * t)
        p = {'body': {'rotation': [0, 0, 0], 'position': [0, up, 0]},
             'neck1': {'rotation': [1.5 * math.sin(2 * w * t), 5 * math.sin(w * t), 0]},
             'neck2': {'rotation': [0, 6 * math.sin(w * t + 0.4), 0]},
             'head': {'rotation': [3 * math.sin(2 * w * t + 1.0), 8 * math.sin(w * t + 0.8), 0]},
             'throat': {'scale': [1 + 0.1 * pump, 1 + 0.6 * pump, 1]}}
        tail_wave(p, t, w, [1, 2, 3, 5, 7, 9], 0.6)
        for kind in LEGS:
            for side, s in SIDES:
                stand(p, kind, side, s, lifted=up)
        return p

    def walk(t):
        ph = t / WALK_LEN
        bend = BEND * math.cos(2 * math.pi * (ph - 0.05))
        up = 0.35 * abs(math.sin(2 * math.pi * ph))
        p = {'body': {'rotation': [0, 0, 0], 'position': [0, up, 0]},
             'chest': {'rotation': [0, bend, 0]}, 'hips': {'rotation': [0, -bend, 0]},
             'neck1': {'rotation': [0, -0.45 * bend, 0]}, 'neck2': {'rotation': [0, -0.35 * bend, 0]},
             'head': {'rotation': [0, -0.2 * bend, 0]}}
        tail_wave(p, t, 2 * math.pi / WALK_LEN, [8, 9, 10, 11, 12, 12], 0.75, lead=-1.2)
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
                    x += s * 0.7 * math.sin(math.pi * f)
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
        up = 1.8 + 0.15 * breath
        sway = 3 * math.sin(w * t)
        p = {'body': {'rotation': [0, 0, 0], 'position': [0, up, 0]},
             'chest': {'rotation': [0, sway, 0]},
             'neck1': {'rotation': [-20, 0, 0]}, 'neck2': {'rotation': [-8, 0, 0]},
             'head': {'rotation': [24 + 2 * breath, 4 * math.sin(w * t + 1.0), 0]},
             'jaw': {'rotation': [20 + 6 * breath, 0, 0]},
             'throat': {'scale': [1.3 + 0.15 * breath, 2.2 + 0.4 * breath, 1.1]}}
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
