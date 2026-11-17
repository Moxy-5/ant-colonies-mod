"""THE OLD SCULPTED NILE MONITOR (2026-11-17), kept only so the viewer can show it beside the blocky rebuild in monitor_models.py; delete once the user has picked. Original notes follow.
(python tools/model_viewer.py monitor).

Made on 2026-11-16 from the user's picture (a blocky Minecraft Nile monitor on a sand bank: charcoal with golden-yellow
bands of blotches, a cream belly and throat, a long neck, a wedge head, a pink forked tongue, long clawed toes, a long
banded tail). Fun first, it only has to look good in the game (animals are generalized, CLAUDE.md). What it is:
- Reworked 2026-11-17 (the user: "it kind of looks like a normal lizard instead of a nile monitor"; a second picture
  of a heavy blocky monitor): bigger cubes, a boxy heavy build. 50 cubes long (the head 6, the neck 6, the body 13,
  the tail 25), 7 wide at the belly, 11 tall at the head. Measured in model pixels, one a cube; the texture has 2
  pixels a cube so half cubes come out exact. About 2.2 blocks long in the viewer (my pick).
- The monitor's outline: the neck rising thick from the shoulders so the head is carried above the back, a deep barrel
  widest in the middle with the belly sagging between the legs (1.5 cubes off the ground there), thick legs held out
  wide with big pale claws, the tail tall and thick at its base, flattened side to side, lying on the ground from
  about its middle. The head kept narrow and pointed with the neck a head long, so it stays a Nile monitor and not a
  Komodo dragon.
- Boxy: every slice of the neck, body and tail is a box with flat sides and only a half-cube bevel at the belly and
  back, the tail taller than wide with a low crest.
- The colours are the thing to get right (the user: "depending on the shade of yellow it can look bad"): a warm
  charcoal (never pure black), a golden yellow with more red than a lemon has (never greenish), in four close shades
  each so no face is flat; mostly dark, the yellow in rows of blotches across the back, rings on the tail, a net of
  yellow and dark low on the flank, cream underneath with faint bars; dark lips with yellow bars, a yellow stripe
  behind the eye. LOOKS has two other yellows to compare.
- Not rigid: neck in two bones, head, jaw, a throat pouch that pumps and swells, a tongue in two bones with two tines
  that slides out of the closed mouth, each pupil and each lower eyelid a bone (lizards blink with the lower lid),
  chest and hips that bend against each other, six tail bones, each leg three bones (upper, lower, foot) bent like a
  lizard's: the elbows out and back, the knees out and forward.
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
SPLIT = 8.5           # the mouth line
HZ = 0.0              # (the first model slid its head's boxes; this one writes them where they are)
HEAD_BACK, SNOUT, TAIL_END = -13.0, -19.0, 31.0

# The neck, body and tail's shape: (z, the middle's height, half its height, half its width), read between the rows.
# Heavy (the user's second picture, 2026-11-17): a deep barrel widest in the middle with the belly sagging between the
# legs, the neck rising thick from the shoulders so the head is carried above the back, the tail tall and thick at its
# base and flattened side to side.
PROFILE = [(-13.0, 8.4, 1.7, 1.75), (-11.0, 7.9, 1.9, 1.9), (-9.0, 7.0, 2.2, 2.2), (-7.0, 6.0, 2.6, 2.7),
           (-4.5, 5.0, 2.9, 3.3), (-1.0, 4.6, 3.1, 3.6), (3.0, 4.7, 2.9, 3.4), (6.0, 4.8, 2.6, 2.6),
           (9.0, 4.2, 2.3, 2.0), (13.0, 3.1, 1.9, 1.6), (18.0, 1.9, 1.5, 1.15), (23.0, 1.2, 1.1, 0.85),
           (27.0, 0.8, 0.75, 0.6), (31.0, 0.5, 0.5, 0.4)]
# The chain: bone, parent, the z of each slice's edges.
CHAIN = [('neck2', 'neck1', [-13, -11.5, -10]), ('neck1', 'chest', [-10, -8.5, -7]),
         ('chest', 'body', [-7, -5.5, -4, -2.5, -1, 0]), ('hips', 'body', [0, 1.5, 3, 4.5, 6]),
         ('tail1', 'hips', [6, 7.5, 9, 10.5]), ('tail2', 'tail1', [10.5, 12, 13.5, 15]),
         ('tail3', 'tail2', [15, 16.5, 18, 19.5]), ('tail4', 'tail3', [19.5, 21, 22.5, 24]),
         ('tail5', 'tail4', [24, 25.5, 27.5]), ('tail6', 'tail5', [27.5, 29.25, 31])]
PIVOT_Z = {'neck2': -10, 'neck1': -7, 'chest': 0, 'hips': 0, 'tail1': 6, 'tail2': 10.5, 'tail3': 15, 'tail4': 19.5,
           'tail5': 24, 'tail6': 27.5}
NECK, TAIL_Z = -7.0, 6.0                      # where the neck ends and the tail starts
FAR_TAIL = 19.0                               # from here the tail is ringed all the way round
CREST = (7.0, 27.5)                           # the tail's crest runs between these

# The legs: where each leaves the body, its upper and lower parts' lengths and thicknesses, where its wrist stands at
# rest (the sole on the ground), which way the elbow or knee points (out and back for the arm, out and forward for the
# leg, as a lizard's do), and how far the toes point outward. For the left side; the right is its mirror. Thick and
# strong, the elbows and knees held out wide, big clawed feet (the user's picture).
LEGS = {'arm': dict(parent='chest', at=(2.5, 5.0, -4.5), upper=3.25, thick=2.5, lower=3.0, thin=2.25,
                    hand=(5.25, 0.5, -4.25), pole=(1.0, 0.1, 0.9), toe_out=20, toes=(1.0, 1.5, 1.75, 1.5, 1.0)),
        'leg': dict(parent='hips', at=(2.5, 4.75, 3.25), upper=3.5, thick=3.0, lower=3.25, thin=2.5,
                    hand=(5.5, 0.5, 3.75), pole=(1.0, 0.25, -0.8), toe_out=35, toes=(1.0, 1.5, 1.75, 2.0, 1.25))}
SIDES = (('left', 1), ('right', -1))


def prof(z):
    """(the middle's height, half the height, half the width) at z."""
    for (z0, *a), (z1, *b) in zip(PROFILE, PROFILE[1:]):
        if z <= z1:
            f = min(1.0, max(0.0, (z - z0) / (z1 - z0)))
            f = f * f * (3 - 2 * f)
            return tuple(p + (q - p) * f for p, q in zip(a, b))
    return tuple(PROFILE[-1][1:])


def half(v):
    return round(v * 2) / 2


def slices():
    """Every slice of the neck, body and tail: (bone, z from, z to, bottom, top, half-width), on the half-cube grid."""
    out = []
    for bone, _, zs in CHAIN:
        for z0, z1 in zip(zs, zs[1:]):
            cy, hh, hw = prof((z0 + z1) / 2)
            out.append((bone, z0, z1, max(0.0, half(cy - hh)), half(cy + hh), max(0.5, half(hw))))
    return out


SLICES = slices()


def cut(hw):
    """How much a slice's belly and back are narrower than its flanks: only a small bevel, so the trunk is a box with
    flat sides (heavy, as in the user's picture) rather than a rounded tube."""
    return 0.5 if hw >= 1.5 else 0.0


def box(cubes, x0, y0, z0, x1, y1, z1, part):
    cubes.append(cube([min(x0, x1), y0, z0], [max(x0, x1), y1, z1], part))


def both(cubes, x0, y0, z0, x1, y1, z1, part):
    box(cubes, x0, y0, z0, x1, y1, z1, part)
    box(cubes, -x1, y0, z0, -x0, y1, z1, part)


def trunk(bones):
    for bone, z0, z1, bot, top, hw in SLICES:
        c, cubes = cut(hw), bones[bone]
        if c:
            box(cubes, -(hw - c), bot, z0, hw - c, bot + c, z1, 'skin')
            box(cubes, -hw, bot + c, z0, hw, top - c, z1, 'skin')
            box(cubes, -(hw - c), top - c, z0, hw - c, top, z1, 'skin')
        else:
            box(cubes, -hw, bot, z0, hw, top, z1, 'skin')
        if z0 >= CREST[0] and z1 <= CREST[1]:  # the tail's low crest
            box(cubes, -0.25, top, z0, 0.25, top + (0.5 if z0 < FAR_TAIL else 0.25), z1, 'crest')
    # the back's ridge over the shoulders and hips, so the back is not one flat plank
    for bone, z0, z1 in (('chest', -5.5, 0.0), ('hips', 0.0, 4.5)):
        top = max(t for b, a0, a1, _, t, _ in SLICES if b == bone and a0 >= z0 - 0.01 and a1 <= z1 + 0.01)
        box(bones[bone], -1.0, top, z0, 1.0, top + 0.5, z1, 'ridge')


def head(bones):
    def hb(cubes, x0, y0, z0, x1, y1, z1, part):
        box(cubes, x0, y0, z0 + HZ, x1, y1, z1 + HZ, part)

    def hboth(cubes, x0, y0, z0, x1, y1, z1, part):
        both(cubes, x0, y0, z0 + HZ, x1, y1, z1 + HZ, part)
    h, j = bones['head'], bones['jaw']
    # the upper head: a narrow pointed wedge, four steps from the skull to the snout's tip, each with a narrower cap
    hb(h, -2.0, SPLIT, -15.5, 2.0, 10.5, -13.0, 'head')
    hb(h, -1.5, 10.5, -15.5, 1.5, 11.0, -13.0, 'head')
    hb(h, -1.5, SPLIT, -17.5, 1.5, 10.0, -15.5, 'head')
    hb(h, -1.0, 10.0, -17.5, 1.0, 10.5, -15.5, 'head')
    hb(h, -1.0, SPLIT, -18.5, 1.0, 9.5, -17.5, 'head')
    hb(h, -0.5, 9.5, -18.5, 0.5, 10.0, -17.5, 'head')
    hb(h, -0.5, SPLIT, -19.0, 0.5, 9.5, -18.5, 'head')
    hboth(h, 1.25, 10.5, -15.75, 2.25, 11.0, -13.75, 'brow')         # a brow ridge over each eye
    hboth(h, 1.5, 9.25, -16.75, 1.56, 9.75, -16.25, 'nostril')     # set back toward the eye, as a Nile monitor's are
    hboth(h, 2.0, 9.25, -15.0, 2.06, 10.25, -13.75, 'iris')
    # the eyes: each pupil a bone that slides over the iris, with a white glint; each lower lid a bone, a low rim
    # under the eye that grows up over it to blink
    for side, s in SIDES:
        p = bones[f'pupil_{side}']
        hb(p, s * 2.06, 9.5, -14.625, s * 2.12, 10.0, -14.125, 'pupil')
        hb(p, s * 2.12, 9.75, -14.625, s * 2.15, 10.0, -14.375, 'glint')
        hb(bones[f'eyelid_{side}'], s * 2.0, 9.0, -15.1, s * 2.2, 9.25, -13.65, 'lid')
    # the lower jaw, hinged at the back of the skull, a little shorter and narrower than the head over it
    hb(j, -1.75, 7.5, -15.5, 1.75, SPLIT, -13.5, 'jaw')
    hb(j, -1.25, 7.75, -17.5, 1.25, SPLIT, -15.5, 'jaw')
    hb(j, -0.75, 8.0, -18.75, 0.75, SPLIT, -17.5, 'jaw')
    # inside the mouth, seen when it gapes
    for z0, z1, w in ((-18.5, -17.5, 0.5), (-17.5, -15.5, 1.0), (-15.5, -13.75, 1.25)):
        hb(h, -w, SPLIT - 0.04, z0, w, SPLIT, z1, 'mouth')
        hb(j, -w, SPLIT, z0, w, SPLIT + 0.04, z1, 'mouth')
    # the tongue, hidden in the closed mouth until it slides out: a stem in two bones and two tines
    hb(bones['tongue'], -0.25, 8.375, -16.0, 0.25, 8.625, -14.5, 'tongue')
    hb(bones['tongue_mid'], -0.25, 8.375, -17.5, 0.25, 8.625, -16.0, 'tongue')
    for side, s in SIDES:
        hb(bones[f'tongue_{side}'], s * 0.02, 8.375, -18.5, s * 0.25, 8.625, -17.5, 'tongue_tip')
    # the throat pouch under the neck
    hb(bones['throat'], -1.25, 6.0, -13.25, 1.25, 7.0, -10.5, 'throat')


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
    q = unit([x - k * y for x, y in zip(p, u)])
    elbow = [S[i] + a * u[i] + h * q[i] for i in range(3)]
    end = [S[i] + far * u[i] for i in range(3)]
    return along(unit([e - a0 for e, a0 in zip(elbow, S)]), s), along(unit([w - e for w, e in zip(end, elbow)]), s)


def foot_flat(kind, s, yaw=0.0, pitch=0.0):
    """The foot's matrix when it lies flat, toes out by the leg's own angle (and turned or tipped a little more)."""
    return rot([pitch, -s * LEGS[kind]['toe_out'] + yaw, 0])


def leg_turns(upper, lower, foot):
    """The three bones' own turns for an upper part, lower part and foot given as matrices in the body part's space."""
    return [turns(upper), turns(mul(flip(upper), lower)), turns(mul(flip(lower), foot))]


def rest_turns(kind, s):
    upper, lower = reach(kind, s, hand(kind, s))
    return leg_turns(upper, lower, foot_flat(kind, s))


def leg(kind, side, s):
    """One leg's three bones (upper, lower, foot). The upper and lower parts are drawn straight out to the side from
    the shoulder and the bones' own turns bend them into place (rest_turns), the foot's turn lays it flat again."""
    d = LEGS[kind]
    x0, y0, z0 = shoulder(kind, s)
    up, low, foot = [], [], []
    t, n = d['thick'] / 2, d['thin'] / 2
    elbow = x0 + s * d['upper']
    box(up, x0 - s * 0.75, y0 - t, z0 - t, x0 + s * d['upper'] * 0.55, y0 + t, z0 + t, 'limb')
    box(up, x0 + s * d['upper'] * 0.55, y0 - t + 0.25, z0 - t + 0.25, elbow + s * 0.75, y0 + t - 0.25, z0 + t - 0.25, 'limb')
    wrist = elbow + s * d['lower']
    box(low, elbow - s * 0.75, y0 - n, z0 - n, elbow + s * d['lower'] * 0.6, y0 + n, z0 + n, 'limb')
    box(low, elbow + s * d['lower'] * 0.6, y0 - n + 0.25, z0 - n + 0.25, wrist + s * 0.25, y0 + n - 0.25, z0 + n - 0.25, 'limb')
    # the foot, drawn pointing forward (-Z) round the wrist: a palm and five toes fanned by starting the outer ones
    # farther back and farther out, each ending in a pale claw
    sole = y0 - 0.5
    box(foot, wrist - 1.5, sole, z0 - 1.5, wrist + 1.5, sole + 1.0, z0 + 1.0, 'foot')
    for i, length in enumerate(d['toes']):
        x = wrist + s * (i - 2) * 0.625
        back = z0 - 1.5 + abs(i - 2) * 0.5
        box(foot, x - 0.25, sole, back - length, x + 0.25, sole + 0.5, back, 'toe')
        box(foot, x - 0.25, sole, back - length - 0.75, x + 0.25, sole + 0.5, back - length, 'claw')
    r = [[round(v, 3) for v in t3] for t3 in rest_turns(kind, s)]
    name = f'{kind}_{{}}_{side}'
    return [{'name': name.format('upper'), 'parent': d['parent'], 'pivot': [x0, y0, z0], 'rotation': r[0], 'cubes': up},
            {'name': name.format('lower'), 'parent': name.format('upper'), 'pivot': [elbow, y0, z0], 'rotation': r[1],
             'cubes': low},
            {'name': name.format('foot'), 'parent': name.format('lower'), 'pivot': [wrist, y0, z0], 'rotation': r[2],
             'cubes': foot}]


def model():
    names = ['head', 'jaw', 'throat', 'tongue', 'tongue_mid', 'tongue_left', 'tongue_right', 'pupil_left',
             'pupil_right', 'eyelid_left', 'eyelid_right'] + [c[0] for c in CHAIN]
    bones = {n: [] for n in names}
    trunk(bones)
    head(bones)
    out = [{'name': 'body', 'pivot': [0, round(prof(0)[0], 2), 0]}]
    parents = {c[0]: c[1] for c in CHAIN}
    for name in ('chest', 'hips', 'neck1', 'neck2', 'tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'tail6'):
        out.append({'name': name, 'parent': parents[name], 'pivot': [0, round(prof(PIVOT_Z[name])[0], 2), PIVOT_Z[name]],
                    'cubes': bones[name]})
    out += [{'name': 'head', 'parent': 'neck2', 'pivot': [0, 9.0, HEAD_BACK], 'cubes': bones['head']},
            {'name': 'jaw', 'parent': 'head', 'pivot': [0, SPLIT, -13.5], 'cubes': bones['jaw']},
            {'name': 'throat', 'parent': 'neck2', 'pivot': [0, 7.0, -12.0], 'cubes': bones['throat']},
            {'name': 'tongue', 'parent': 'head', 'pivot': [0, 8.5, -14.5], 'cubes': bones['tongue']},
            {'name': 'tongue_mid', 'parent': 'tongue', 'pivot': [0, 8.5, -16.0], 'cubes': bones['tongue_mid']}]
    for side, s in SIDES:
        out += [{'name': f'tongue_{side}', 'parent': 'tongue_mid', 'pivot': [s * 0.12, 8.5, -17.5],
                 'rotation': [0, -s * 7, 0], 'cubes': bones[f'tongue_{side}']},
                {'name': f'pupil_{side}', 'parent': 'head', 'pivot': [s * 2.1, 9.75, -14.375],
                 'cubes': bones[f'pupil_{side}']},
                {'name': f'eyelid_{side}', 'parent': 'head', 'pivot': [s * 2.1, 9.0, -14.375],
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
    """The neck, body and tail: 'Y' yellow, 'D' dark, 'T' tan, 'C' cream, 'B' a faint belly bar."""
    bot, top, hw = section(z)
    g = abs(x) + (top - y)                    # how far round the body from the spine
    around = hw + (top - bot)                 # the spine to the belly's middle, about
    u = g / around
    side = 1 if x >= 0 else -1
    cz, cg = math.floor(z + 1e-4), math.floor(g + 1e-4)
    r = dice(cz, cg, side)
    tail = z >= TAIL_Z
    if tail:                                  # rings: 2 cubes yellow in every 6, closer toward the tip
        period, start = (5, TAIL_Z) if z < 16 else (4, 16) if z < 24 else (3, 24)
    elif z < NECK:
        period, start = 3, HEAD_BACK          # the neck: closer rows
    else:
        period, start = 4, NECK + 1
    band = (cz - start) // period
    # a row of blotches: each blotch 2 cubes wide round the body with 1 dark between, 1 or 2 cubes long, set a cube
    # forward or back of its neighbours so the row wanders
    cell = (cg + (band % 3)) // 3
    at = (cz - start) % period
    first = int(dice(band, cell, 5) * 2)
    long = 2 if tail or dice(band, cell, 6) < 0.6 else 1
    blotch = first <= at < first + long and ((cg + (band % 3)) % 3 != 2 or tail)
    if face == 'down' or u > 0.88:            # underneath: cream with faint bars
        if z > FAR_TAIL:                            # the far tail is ringed all the way round
            return 'Y' if at < 2 and r < 0.8 else 'D'
        return 'B' if at == period - 1 and r < 0.6 else 'C'
    if u > 0.76:                              # the edge of the belly: tan breaking into the dark
        if z > FAR_TAIL:
            return 'Y' if at < 2 and r < 0.85 else 'D'
        return 'T' if r < 0.5 else 'C' if r < 0.7 else 'D' if r < 0.9 else 'Y'
    if u > 0.58:                              # low on the flank: a net of yellow and dark
        if z > FAR_TAIL:
            return 'Y' if at < 2 and r < 0.85 else 'D'
        if blotch:
            return 'Y' if r < 0.65 else 'T'
        return ('Y' if r < 0.3 else 'T' if r < 0.5 else 'D') if (cz + cg) % 3 == 0 else 'D'
    if blotch:
        return 'Y' if r < 0.92 else 'D'
    return 'Y' if r < 0.04 else 'D'


def colour(part, look, x, y, z, face, rnd):
    P = LOOKS[look]
    cx, cy, cz = math.floor(x + 1e-4), math.floor(y + 1e-4), math.floor(z + 1e-4)
    r = dice(cx * 3 + cy * 7, cz, 21 if face in ('up', 'down') else 22 if face in ('east', 'west') else 23)
    grain = rnd.uniform(0.985, 1.015)
    dark, yellow, cream, tan = tone(P['dark'], r), tone(P['yellow'], r), P['cream'], P['tan']
    paint_of = {'Y': yellow, 'D': dark, 'T': mix(tan, yellow, 0.25), 'C': cream if r < 0.75 else mix(cream, tan, 0.5),
                'B': mix(cream, P['dark'][1], 0.3)}
    if part == 'pupil' or part == 'nostril':
        return (14, 13, 15)
    if part == 'glint':
        return (250, 248, 240)
    if part == 'iris':
        hz = z - HZ
        edge = y < 9.5 or y > 10.0 or hz < -14.75 or hz > -14.0
        return shade((156, 96, 34) if edge else (206, 142, 50), grain)
    if part == 'lid':
        return shade(mix(P['yellow'][2], P['dark'][1], 0.45), grain)
    if part == 'mouth':
        return shade((206, 124, 128) if r < 0.5 else (194, 110, 116), grain)
    if part == 'tongue':
        return shade((214, 122, 142) if cz % 2 else (204, 110, 132), grain)
    if part == 'tongue_tip':
        return shade((190, 98, 122), grain)
    if part == 'claw':
        return shade((236, 226, 200) if face == 'up' else (216, 202, 170), grain)
    if part == 'toe':
        return shade(tan if face == 'down' else yellow if cz % 2 == 0 else dark, grain)
    if part == 'foot':
        return shade(tan if face == 'down' else yellow if r < 0.22 else dark, grain)
    if part == 'limb':                        # spotted: yellow dots on dark, a paler underside
        if face == 'down':
            return shade(mix(tan, P['dark'][1], 0.35), grain)
        spot = (cx + 2 * cy + 3 * cz) % 5 == 0 and dice(cx, cy, cz) < 0.8 or dice(cx, cy + 9, cz) < 0.06
        return shade(yellow if spot else dark, grain)
    if part == 'throat':
        return shade(cream if face == 'down' or r < 0.7 else mix(cream, tan, 0.6), grain)
    hz = z - HZ
    chz = math.floor(hz + 1e-4)
    if part == 'jaw':                         # cream with dark bars up the lips
        if face == 'down':
            return shade(cream, grain)
        bar = chz % 2 == 0 and hz < -14
        return shade(dark if bar and y > 7.75 else cream if r < 0.7 else mix(cream, tan, 0.6), grain)
    if part == 'brow':
        if face in ('east', 'west') and y < 10.75:
            return shade(yellow, grain)
        return shade(dark, grain)
    if part == 'head':
        ax = abs(x)
        if face in ('up', 'south', 'north'):  # fine speckles, a little thicker toward the back of the skull
            fx, fz = math.floor(x * 2 + 1e-4), math.floor(hz * 2 + 1e-4)
            fleck = dice(fx, fz, 41) < (0.10 if hz < -16.5 else 0.17)
            return shade(yellow if fleck else dark, grain)
        if face == 'down':
            return shade(dark, grain)
        if y < 9.0:                           # the upper lip: dark with yellow bars
            return shade(yellow if chz % 2 == 1 and hz < -14 else dark, grain)
        if y < 10.0 and hz > -13.75:          # the stripe from the eye back along the temple
            return shade(yellow, grain)
        if y < 10.0 and -16.5 < hz < -15.0 and chz % 2 == 0:    # dashes from the eye toward the nostril
            return shade(yellow, grain)
        return shade(dark, grain)
    if part == 'crest' or part == 'ridge':
        k = skin(x, section(z)[1], z, 'up')
        return shade(yellow if k == 'Y' else shade(P['dark'][2], 0.92) if part == 'crest' else dark, grain)
    # the neck, body and tail
    k = skin(x, y, z, face)
    if z < -11 and face in ('east', 'west'):
        bot, top, _ = section(z)              # the temple's stripe runs on a little way down the neck
        if 0.28 < (top - y) / (top - bot) < 0.55:
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
TONGUE_OUT = 3.5                              # how far the tongue slides out (cubes)
STEP, ON_GROUND, STEP_UP = 2.0, 0.6, 1.0      # the walk: half a step's length (cubes), the share of the stride a foot
#                                               stands, how high a foot lifts
BEND = 8.0                                    # how far chest and hips turn against each other in the walk (degrees)
TAIL = ['tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'tail6']
REST = {(kind, s): rest_turns(kind, s) for kind in LEGS for _, s in SIDES}
BODY_Y = round(prof(0)[0], 2)                 # the chest's and hips' pivot height


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
    S, d = shoulder(kind, s), LEGS[kind]
    first = unit([s * 0.55, -0.22, 1.0])
    second = unit([s * 0.12, -0.12, 1.0])
    f = unit([second[0], 0.0, second[2]])
    foot = [[-f[2], 0, -f[0]], [0, 1, 0], [f[0], 0, -f[2]]]        # level, the toes pointing along the lower leg
    set_leg(p, kind, side, s, along(first, s), along(second, s), foot)
    del S, d


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
             'throat': {'scale': [1 + 0.12 * pump, 1 + 0.45 * pump, 1]}}
        tail_wave(p, t, w, [1, 2, 3, 5, 7, 9], 0.6)
        for kind in LEGS:
            for side, s in SIDES:
                stand(p, kind, side, s, lifted=up)
        return p

    def walk(t):
        ph = t / WALK_LEN
        bend = BEND * math.cos(2 * math.pi * (ph - 0.05))
        up = 0.2 * abs(math.sin(2 * math.pi * ph))
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
                    x += s * 0.4 * math.sin(math.pi * f)
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
        up = 0.8 + 0.08 * breath
        sway = 3 * math.sin(w * t)
        p = {'body': {'rotation': [0, 0, 0], 'position': [0, up, 0]},
             'chest': {'rotation': [0, sway, 0]},
             'neck1': {'rotation': [-20, 0, 0]}, 'neck2': {'rotation': [-8, 0, 0]},
             'head': {'rotation': [24 + 2 * breath, 4 * math.sin(w * t + 1.0), 0]},
             'jaw': {'rotation': [20 + 6 * breath, 0, 0]},
             'throat': {'scale': [1.5 + 0.2 * breath, 2.0 + 0.4 * breath, 1.1]}}
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
        return {f'eyelid_{side}': {'scale': [1, 1 + 5.2 * shut, 1]} for side, _ in SIDES}

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
        if 'rotation' in b:
            print(' ', b['name'], b['rotation'])
