"""The Nile monitor (M9 river life), third start (2026-11-17), its 3D model in plain colours for the model viewer
(python tools/model_viewer.py monitor).

Built from the user's picture and the agreed flat outline (tools/monitor_outline.py) alone; nothing from the old
models. The user's rule for this one: only some parts need heavy detail. So:
- The tail, the neck and the face are made close to round: each is a chain of short pieces, every piece a rounded
  cross-section (three overlapping boxes, wide, tall and between, so it reads as round), and the chain is BENT a few
  degrees at every joint (bone rest rotations), so the tail curves down to the ground and the neck sweeps up into the
  head with no staircase. The head narrows into the snout in one-pixel slices, its top and bottom edges bevelled.
- The body and legs stay chunky boxes, as in the picture (that is its Minecraft look).
- Colours are plain for now (step 2, the shape only); the pattern is step 3, the clips step 4.

Units are model pixels (16 a block), 96 long (6 blocks, the user's size for now). The head points to -Z, up is +Y,
left is +X; y = 0 is the ground. A rest rotation is given here as a pitch in degrees, positive turning the bone's
far end DOWN for the tail and UP for the neck (see pitch()).
"""
import copy
import math

from tilapia_models import cube, faces, face_point, png, shade  # noqa: F401 (png for the viewer)

TEXELS = 1.0          # texture pixels a model pixel (plain colours for now)

# --- the outline's numbers (tools/monitor_outline.py), turned into the model's axes: z = 48 - outline x -------------
BODY_Z = (-26.0, 0.0)          # the body, front to back
BODY_Y = (6.0, 16.0)           # belly to back
BODY_HW = 5.0                  # half width

TAIL_SEGS, TAIL_LEN = 12, 48.0         # 12 pieces of 4 px
TAIL_AXIS = 11.0                       # the tail's middle line where it leaves the body
TAIL_R0, TAIL_R1 = 4.6, 0.6            # radius at the base and the tip
TAIL_LIES = 0.62                       # how far along the tail it is lying on the ground

NECK_SEGS = 2                          # two neck bones, each 4 px of round slices
NECK_AXIS = 11.5
NECK_Z0, NECK_LEN = -26.0, 8.0
NECK_TURNS = (15.0, 3.0)               # how much each neck bone turns up from the one before (degrees)
HEAD_LEN = 14.0
HEAD_PITCH = -2.0                      # the head's own angle to level ground, a little nose down (degrees)


def pitch(deg):
    """A bone's rest rotation from a pitch (degrees): for a piece reaching to +Z (the tail) positive bends it down,
    for one reaching to -Z (neck, head) positive raises it (GeckoLib's x turn is the viewer's negated)."""
    return [-deg, 0.0, 0.0]


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def rounded(cubes, cx, cy, rw, rh, z0, z1, part):
    """A round cross-section from z0 to z1: a wide box, a tall box and one between (an eight-sided outline)."""
    if min(rw, rh) < 0.9:
        cubes.append(cube([cx - rw, cy - rh, z0], [cx + rw, cy + rh, z1], part))
        return
    cubes.append(cube([cx - rw, cy - rh * 0.55, z0], [cx + rw, cy + rh * 0.55, z1], part))
    cubes.append(cube([cx - rw * 0.55, cy - rh, z0], [cx + rw * 0.55, cy + rh, z1], part))
    cubes.append(cube([cx - rw * 0.85, cy - rh * 0.85, z0], [cx + rw * 0.85, cy + rh * 0.85, z1], part))


# --- the body and legs: chunky boxes ---------------------------------------------------------------------------------
def body():
    z0, z1 = BODY_Z
    y0, y1 = BODY_Y
    c = [cube([-BODY_HW, y0, z0], [BODY_HW, y1 - 1.0, z1], 'skin'),
         cube([-BODY_HW + 0.7, y1 - 1.0, z0], [BODY_HW - 0.7, y1, z1], 'skin'),        # the back's edge softened
         cube([-4.5, 5.0, -21.0], [4.5, y0, -7.0], 'belly')]                          # the belly hanging low
    return {'name': 'body', 'pivot': [0.0, TAIL_AXIS, -13.0], 'cubes': c}


# A little rotation maths for the legs: each leg piece is aimed from one joint to the next, so its rest rotation is
# worked out from where its joints should be. Matrices are in the viewer's axes (three.js, the order Z then Y then X,
# as GeckoLib turns bones); rot() turns one into the bone file's degrees (x and z negated, as the viewer reads them).
def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _tr(a):
    return [list(r) for r in zip(*a)]


def _rx(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def _ry(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def _aim(a, b):
    """The smallest turn taking direction a onto direction b."""
    na, nb = math.sqrt(sum(v * v for v in a)), math.sqrt(sum(v * v for v in b))
    a, b = [v / na for v in a], [v / nb for v in b]
    v = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
    c = sum(x * y for x, y in zip(a, b))
    k = [[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]]
    k2 = _mul(k, k)
    return [[(i == j) + k[i][j] + k2[i][j] / (1 + c) for j in range(3)] for i in range(3)]


def rot(m):
    """A turn matrix as a bone's rest rotation in degrees."""
    ay = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(m[2][0]) < 0.9999999:
        ax, az = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
    else:
        ax, az = 0.0, math.atan2(-m[0][1], m[1][1])
    return [round(-math.degrees(ax), 3), round(math.degrees(ay), 3), round(-math.degrees(az), 3)]


def _sub(a, b):
    return [x - y for x, y in zip(a, b)]


def _len(v):
    return math.sqrt(sum(x * x for x in v))


# Where each leg's joints sit (the left side; the right is the mirror), in model pixels: shoulder or hip, elbow or
# knee, wrist or ankle. The upper part runs out and down from the body, the elbow or knee sticking out to the side; the
# fore arm slants down and forward to the hand, the shin down and back to the foot (as in the user's picture), the
# feet flat and turned out, the toes fanned. Then the thicknesses (upper, lower), the joint knob, the foot's turn and
# palm, and the five toes (inner to outer): length and fan angle (outward +); every toe raised a little at the knuckle.
LEG_PLANS = {
    'arm': {'joints': ((4.0, 11.0, -21.5), (10.5, 7.5, -22.5), (10.8, 2.3, -25.0)),
            'thick': (4.0, 3.4), 'knob': 4.0, 'wrist': 4.2, 'turn': 18.0, 'palm': (4.6, 3.6),
            'toes': ((2.6, -34.0), (3.4, -14.0), (3.9, 2.0), (3.6, 18.0), (2.8, 38.0)), 'raise': 14.0},
    'leg': {'joints': ((4.0, 10.5, -4.0), (11.0, 7.5, -6.0), (11.6, 2.3, -2.6)),
            'thick': (4.6, 3.8), 'knob': 5.0, 'wrist': 5.0, 'turn': 38.0, 'palm': (5.0, 4.4),
            'toes': ((2.6, -30.0), (3.6, -12.0), (4.2, 4.0), (4.6, 20.0), (3.0, 46.0)), 'raise': 14.0},
}
CLAW_DROP = 23.0     # how far each claw bends down from its toe (degrees); the hook below curves it on to the ground
# A claw from root to point, as a hook in small steps (along its length from the toe's tip, width, bottom and top
# against the toe's middle line, colour): a dark sheath where it leaves the toe, thick and dark at the root, thinner and
# browner as it curves down, a dull horn point turning down at the end. Each step is quarter-pixel sized.
CLAW_SHAPE = (
    (-0.25, 0.30, 0.86, -0.43, 0.43, 'clawbase'),
    (0.30, 0.90, 0.64, -0.34, 0.34, 'claw'),
    (0.90, 1.40, 0.52, -0.38, 0.22, 'claw'),
    (1.40, 1.85, 0.40, -0.46, 0.02, 'clawmid'),
    (1.85, 2.20, 0.28, -0.64, -0.20, 'clawtip'),
    (2.10, 2.32, 0.16, -0.88, -0.42, 'clawtip'),
)


def legs():
    """Each leg: an upper piece and a lower one aimed between its joints, a foot flat on the ground turned out, and five
    fanned toes, each raised a little at the knuckle with a long claw hooking down from its tip to the ground (a claw is
    its own little piece so it can bend down while the toe lifts, and so the clips can flex them later)."""
    out = []
    for s, side in ((1, 'left'), (-1, 'right')):
        for kind, plan in LEG_PLANS.items():
            (sx, sy, sz), (ex, ey, ez), (wx, wy, wz) = plan['joints']
            S, E, W = (s * sx, sy, sz), (s * ex, ey, ez), (s * wx, wy, wz)
            t1, t2 = plan['thick']
            l1, l2 = _len(_sub(E, S)), _len(_sub(W, E))
            r1 = _aim((s, 0, 0), _sub(E, S))                        # the upper part's own axis is straight out
            r2 = _aim((0, -1, 0), _sub(W, E))                       # the lower part's is straight down
            r3 = _ry(-s * plan['turn'])                              # the foot: flat, turned out
            e_rest = (S[0] + s * l1, S[1], S[2])                     # the joints before the turns are applied
            w_rest = (e_rest[0], e_rest[1] - l2, e_rest[2])

            # Each joint is one block a little bigger than the pieces meeting there, and both pieces end at its middle,
            # so no box corner pokes out past another (the user's report: corners stuck out at the elbow and wrist).
            ux = sorted([S[0] - s * 1.0, S[0] + s * l1])
            k = plan['knob'] / 2
            ex_, ey_, ez_ = e_rest
            up = [cube([ux[0], S[1] - t1 / 2, S[2] - t1 / 2], [ux[1], S[1] + t1 / 2, S[2] + t1 / 2], 'leg'),
                  cube([ex_ - k, ey_ - k, ez_ - k], [ex_ + k, ey_ + k, ez_ + k], 'leg')]          # elbow or knee
            low = [cube([ex_ - t2 / 2, ey_ - l2 + 0.4, ez_ - t2 / 2], [ex_ + t2 / 2, ey_, ez_ + t2 / 2], 'leg')]
            pw, pl = plan['palm']
            g = -W[1]                                                # the ground, below the wrist
            fx, fy, fz = w_rest
            wk = plan['wrist'] / 2
            foot = [cube([fx - wk, fy - wk, fz - wk], [fx + wk, fy + wk, fz + wk], 'leg'),                  # wrist
                    cube([fx - pw / 2, fy + g, fz - pl + 1.0], [fx + pw / 2, fy + g + 1.6, fz + 1.4], 'leg'),             # palm
                    cube([fx - pw / 2 + 0.5, fy + g + 1.6, fz - pl + 1.6], [fx + pw / 2 - 0.5, fy + g + 2.4, fz + 1.2], 'leg')]

            def name(piece):
                return f'{kind}_{piece}_{side}'
            out.append({'name': name('upper'), 'parent': 'body', 'pivot': list(S), 'rotation': rot(r1), 'cubes': up})
            out.append({'name': name('lower'), 'parent': name('upper'), 'pivot': list(e_rest),
                        'rotation': rot(_mul(_tr(r1), r2)), 'cubes': low})
            out.append({'name': name('foot'), 'parent': name('lower'), 'pivot': list(w_rest),
                        'rotation': rot(_mul(_tr(r2), r3)), 'cubes': foot})
            n = len(plan['toes'])
            for i, (tl, fan) in enumerate(plan['toes']):
                tx = fx + s * (-pw / 2 + 0.55 + i * (pw - 1.1) / (n - 1))
                ty, tz = fy + g + 0.55, fz - pl + 1.2                # the knuckle, at the palm's front edge
                toe = [cube([tx - 0.55, ty - 0.55, tz - tl * 0.55], [tx + 0.55, ty + 0.55, tz + 0.4], 'leg'),
                       cube([tx - 0.42, ty - 0.45, tz - tl], [tx + 0.42, ty + 0.4, tz - tl * 0.5], 'leg')]
                cy, cz = ty + 0.05, tz - tl + 0.1                    # the claw's root, at the toe's tip
                claw = [cube([tx - w / 2, cy + lo, cz - z1], [tx + w / 2, cy + hi, cz - z0], part)
                        for z0, z1, w, lo, hi, part in CLAW_SHAPE]
                out.append({'name': name(f'toe{i + 1}'), 'parent': name('foot'), 'pivot': [tx, ty, tz],
                            'rotation': rot(_mul(_ry(-s * fan), _rx(plan['raise']))), 'cubes': toe})
                out.append({'name': name(f'claw{i + 1}'), 'parent': name(f'toe{i + 1}'), 'pivot': [tx, cy, cz],
                            'rotation': rot(_rx(-CLAW_DROP)), 'cubes': claw})
    return out


# --- the tail: a bent chain of round pieces ----------------------------------------------------------------------------
def tail_radius(t):
    return TAIL_R0 + (TAIL_R1 - TAIL_R0) * t


def tail_height(t):
    """Where the tail's middle line should be (y) a fraction t along it: dropping from the body in a smooth curve until
    its underside rests on the ground."""
    r = tail_radius(t)
    return r + 0.25 + (TAIL_AXIS - TAIL_R0 - 0.25) * (1 - smooth(t / TAIL_LIES))


def tail():
    """Built straight back from the body at TAIL_AXIS; each bone then bends down by the angle that puts the curve where
    tail_height says."""
    L = TAIL_LEN / TAIL_SEGS
    angles = []
    for i in range(TAIL_SEGS):
        drop = tail_height(i / TAIL_SEGS) - tail_height((i + 1) / TAIL_SEGS)
        angles.append(math.degrees(math.asin(max(-0.9, min(0.9, drop / L)))))
    out, prev, parent = [], 0.0, 'body'
    for i in range(TAIL_SEGS):
        z0, z1 = BODY_Z[1] + i * L, BODY_Z[1] + (i + 1) * L
        t = (i + 0.5) / TAIL_SEGS
        r = tail_radius(t)
        c = []
        back = r * 1.2 * math.tan(math.radians(abs(angles[i] - prev))) + 0.3     # close the joint's wedge
        rounded(c, 0.0, TAIL_AXIS, r, r * (1 + 0.2 * t), z0 - back, z1, 'skin')   # a little taller than wide toward the tip
        name = f'tail{i + 1}'
        out.append({'name': name, 'parent': parent, 'pivot': [0.0, TAIL_AXIS, z0],
                    'rotation': pitch(angles[i] - prev), 'cubes': c})
        prev, parent = angles[i], name
    return out


# --- the neck and head: round, rising, then a long smooth wedge into the snout ----------------------------------------
def neck():
    """Round one-pixel slices that change smoothly from the body's front (a broad round section, centred on the body)
    to exactly the head's back (narrower, centred higher), on two bones: the first turns up, the second a little more,
    so the neck sweeps up out of the shoulders. Each bone's first slice reaches back into the piece before it far enough
    to close the wedge its turn opens."""
    out, parent = [], 'body'
    L = NECK_LEN / NECK_SEGS
    hw, htop, hbot = head_shape(0.0)
    start = (4.5, 4.7, (BODY_Y[0] + BODY_Y[1]) / 2 - NECK_AXIS)           # half width, half height, centre (relative)
    end = (hw, (htop - hbot) / 2, (htop + hbot) / 2)
    for i in range(NECK_SEGS):
        z0 = NECK_Z0 - i * L
        c = []
        n = 8                                   # half-pixel slices: the underside climbs 3 px, so no visible steps
        for k in range(n):
            a = z0 - k * L / n
            t = smooth((i * n + k + 0.5) / (NECK_SEGS * n))
            rw, rh, cy = (s0 + (s1 - s0) * t for s0, s1 in zip(start, end))
            back = 0.3 if k else rh * math.tan(math.radians(abs(NECK_TURNS[i]))) + 0.3
            rounded(c, 0.0, NECK_AXIS + cy, rw, rh, a - L / n, a + back, 'skin')
        name = f'neck{i + 1}'
        out.append({'name': name, 'parent': parent, 'pivot': [0.0, NECK_AXIS, z0],
                    'rotation': pitch(NECK_TURNS[i]), 'cubes': c})
        parent = name
    return out


def head_shape(t):
    """(half width, top, bottom) of the head a fraction t from its back (0) to the snout tip (1), heights relative to
    the neck's middle line. Even skull for the back third, then narrowing and sloping smoothly to a blunt snout."""
    f = max(0.0, (t - 0.3) / 0.7)
    w = 1.9 + 1.75 * (1 - smooth(f))
    top = 4.2 - 1.7 * f ** 1.25
    bottom = -1.9 + 0.9 * t
    return w, top, bottom


MOUTH = 0.5           # the mouth line, relative to the neck's middle line
GAPE = 0.14           # the mouth runs back to here (a fraction of the head), under the eye


def head():
    z0 = NECK_Z0 - NECK_LEN
    y = NECK_AXIS
    upper, jaw = [], []
    cuts = [0.0, 0.15, 0.3] + [0.3 + 0.7 * (k + 1) / 10 for k in range(10)]   # longer slices at the back, 1 px at the front
    for a, b in zip(cuts, cuts[1:]):
        t = (a + b) / 2
        w, top, bot = head_shape(t)
        if b == 1.0:                                     # the snout's very tip a little smaller: a rounded end
            w, top, bot = w * 0.85, top - 0.3, bot + 0.2
        za, zb = z0 - b * HEAD_LEN, z0 - a * HEAD_LEN + 0.2
        if a == 0.0:                                     # reach back into the neck to close the joint's wedge
            zb = z0 + top * math.tan(math.radians(abs(HEAD_PITCH - sum(NECK_TURNS)))) + 0.3
            w, top, bot = w * 0.96, top - 0.15, bot + 0.15
        rr = min(1.4, 0.42 * w)                          # the top edge, bevelled twice
        upper.append(cube([-w, y + MOUTH, za], [w, y + top - rr, zb], 'skin'))
        upper.append(cube([-(w - rr * 0.35), y + top - rr, za], [w - rr * 0.35, y + top - rr * 0.35, zb], 'skin'))
        upper.append(cube([-(w - rr), y + top - rr * 0.35, za], [w - rr, y + top, zb], 'skin'))
        rb, wj = min(1.2, 0.35 * w), w * 0.97                # the underside, bevelled twice
        target, part = (jaw if t > GAPE else upper), 'throat'     # behind the gape the throat is part of the skull
        target.append(cube([-wj, y + bot + rb, za], [wj, y + MOUTH, zb], part))
        target.append(cube([-(wj - rb * 0.4), y + bot + rb * 0.4, za], [wj - rb * 0.4, y + bot + rb, zb], part))
        target.append(cube([-(wj - rb), y + bot, za], [wj - rb, y + bot + rb * 0.4, zb], part))
    # the eyes, far back and high, under a brow ridge; the nostrils near the tip
    ez = z0 - 0.19 * HEAD_LEN
    w, top, _ = head_shape(0.19)
    for s in (1, -1):
        def xs(a, b):
            return sorted([s * a, s * b])
        upper.append(cube([xs(w - 0.2, w + 0.25)[0], y + 2.4, ez - 0.7], [xs(w - 0.2, w + 0.25)[1], y + 3.4, ez + 0.7], 'eye'))
        upper.append(cube([xs(w - 1.3, w + 0.15)[0], y + 3.4, ez - 1.2], [xs(w - 1.3, w + 0.15)[1], y + 4.1, ez + 1.0], 'skin'))
        nw, ntop, _ = head_shape(0.88)
        nz = z0 - 0.88 * HEAD_LEN
        upper.append(cube([xs(nw - 1.1, nw - 0.35)[0], y + ntop - 0.75, nz - 0.4],      # on the top's side edge
                          [xs(nw - 1.1, nw - 0.35)[1], y + ntop + 0.1, nz + 0.4], 'nostril'))
    hinge = [0.0, y + MOUTH, z0 - GAPE * HEAD_LEN]
    return [{'name': 'head', 'parent': f'neck{NECK_SEGS}', 'pivot': [0.0, y, z0],
             'rotation': pitch(HEAD_PITCH - sum(NECK_TURNS)), 'cubes': upper},
            {'name': 'jaw', 'parent': 'head', 'pivot': hinge, 'cubes': jaw}]


def model():
    return [body()] + neck() + head() + tail() + legs()


# --- plain colours (step 2: shape only) --------------------------------------------------------------------------------
LOOKS = {'plain': {'name': 'Plain colours', 'note': 'shape only: charcoal skin, a cream throat and belly, yellow eyes, '
                   'dark hooked claws with horn points; the pattern comes in step 3'}}
GAME_LOOK = 'plain'
NOTES = [
    ('Tail', 'a chain of 12 short pieces, each a rounded cross-section of overlapping boxes, each turned a few degrees '
             'from the last, so it curves down off the hips and lies along the sand to a thin tip without stair steps'),
    ('Neck', 'round slices that change smoothly from the body to the back of the head, on two pieces tilted up, '
             'so it sweeps up from the shoulders and holds the head above the back'),
    ('Head', '14 pixels long. It narrows toward the snout in one-pixel slices, top and bottom edges bevelled, '
             'ending in a blunt rounded tip. The eyes sit far back under a brow ridge, the nostrils near the tip, '
             'and the lower jaw is its own piece for the clips later'),
    ('Body', 'blocky on purpose, as in your picture: a deep body with the belly hanging low'),
    ('Legs', 'angled like your picture: the upper part slants out and down from the body, the elbow or knee sticking '
             'out to the side; the fore arm slants down and forward to the hand, the shin down and back to the foot'),
    ('Joints', 'each elbow, knee, wrist and ankle is one block a little bigger than the pieces meeting there, so no box '
               'corner pokes out past another'),
    ('Feet and claws', 'flat on the ground and turned out (the hind feet more), five long toes fanned out, each raised '
                       'a little at the knuckle; each claw a hook in small steps, a dark sheath at the toe, thick and dark '
                       'at the root, thinner and browner as it curves down, a dull horn point touching the ground; every '
                       'toe and claw is its own piece, so the walk can spread and grip them later'),
    ('Still to come', 'step 3, the paint (yellow spots, the banded tail, cream throat, one pixel size everywhere); '
                      'step 4, the animations (walk, tongue, swim)'),
]
CHARCOAL, CREAM = (62, 58, 54), (214, 196, 150)


def colour(part, face):
    if part == 'eye':
        return (226, 186, 54)
    if part == 'nostril':
        return (18, 16, 14)
    if part == 'clawbase':
        return (28, 24, 21)                  # the dark sheath where the claw leaves the toe
    if part == 'claw':
        return (46, 38, 33)                  # dark horn, as in the picture
    if part == 'clawmid':
        return (88, 72, 55)                  # browner as it thins
    if part == 'clawtip':
        return (156, 134, 102)               # a dull horn point, not white
    if part in ('belly', 'throat') or face == 'down':
        return CREAM if face != 'up' else CHARCOAL
    return CHARCOAL


def paint(bones, look='plain'):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
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
        rgb = colour(c['_part'], face)
        for jj in range(h):
            for ii in range(w):
                rows[y0 + jj][x0 + ii] = tuple(rgb) + (255,)
    for b in bones:
        for c in b.get('cubes', []):
            c.pop('_part')
    return bones, rows, width, height


if __name__ == '__main__':
    bs = model()
    print(len(bs), 'bones,', sum(len(b.get('cubes', [])) for b in bs), 'boxes')
    for b in bs:
        print(f"  {b['name']:16} {len(b.get('cubes', [])):3} boxes  rot {b.get('rotation', '')}")
