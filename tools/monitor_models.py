"""The Nile monitor (M9 river life), third start (2026-11-17), its 3D model in plain colours for the model viewer
(python tools/model_viewer.py monitor).

Built from the user's picture and the agreed flat outline (tools/monitor_outline.py) alone; nothing from the old
models. The user's rule for this one: only some parts need heavy detail. So:
- The tail, the neck and the face are made close to round: each is a chain of short pieces, every piece a rounded
  cross-section (three overlapping boxes, wide, tall and between, so it reads as round), and the chain is BENT a few
  degrees at every joint (bone rest rotations), so the tail curves down to the ground and the neck sweeps up into the
  head with no staircase. The head narrows into the snout in one-pixel slices, its top and bottom edges bevelled.
- The body stays a chunky box, as in the picture (that is its Minecraft look); the legs are round, tapering
  from the muscle to round joints (the user's ask, 2026-11-17).
- The paint (step 3) is worked out pixel by pixel from where each pixel lies on the model; the clips (step 4) are made here too, in clips().

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


# --- the body: chunky boxes; the legs: round pieces ------------------------------------------------------------------------------
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
# feet flat and turned out, the toes fanned. Then each piece's roundness: 'upper' and 'lower' are its radius along it
# (fraction along, radius in pixels: thick at the muscle, slimmer toward the next joint), 'knob' and 'wrist' the radius
# of the round elbow or knee and wrist or ankle; then the foot's turn and palm, and the four toes (inner to outer; real monitors have five, four looks better):
# length and fan angle (outward +); every toe raised a little at the knuckle.
LEG_PLANS = {
    'arm': {'joints': ((4.0, 11.0, -21.5), (10.5, 7.5, -22.5), (10.8, 2.3, -25.0)),
            'upper': ((0.0, 2.0), (0.35, 2.2), (1.0, 1.55)), 'lower': ((0.0, 1.7), (0.25, 1.85), (1.0, 1.25)),
            'knob': 1.95, 'wrist': 1.55, 'turn': 18.0, 'palm': (4.6, 3.6),
            'toes': ((2.9, -32.0), (3.8, -9.0), (3.8, 13.0), (3.0, 36.0)), 'raise': 14.0},
    'leg': {'joints': ((4.0, 10.5, -4.0), (11.2, 8.5, -6.0), (11.8, 2.6, -2.6)),
            'upper': ((0.0, 2.3), (0.35, 2.55), (1.0, 1.8)), 'lower': ((0.0, 1.95), (0.25, 2.1), (1.0, 1.35)),
            'knob': 2.25, 'wrist': 1.75, 'hinge': True, 'turn': 38.0, 'palm': (5.0, 4.4),
            'toes': ((3.0, -28.0), (4.1, -6.0), (4.6, 17.0), (3.3, 44.0)), 'raise': 14.0},
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


def _cols(x, y, z):
    """A turn matrix from where its three axes go."""
    return [[x[i], y[i], z[i]] for i in range(3)]


def _unit(v):
    n = _len(v)
    return [c / n for c in v]


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


# Round leg pieces (the user, 2026-11-17: the legs needed more pixels and to be round to look good). A cross-section
# is four boxes at quarter-pixel sizes (wide, tall and two between), close to a circle; a piece is cut into half-pixel
# slices along its own line, each slice's radius read off its profile and rounded to a quarter pixel, and slices of the
# same size joined into one, so a taper costs boxes only where it really changes.
ROUND = ((1.0, 0.4), (0.4, 1.0), (0.93, 0.68), (0.68, 0.93))


def _q(r):
    return round(r * 4) / 4


def _disc(cubes, axis, centre, r, a0, a1, part):
    """One round slice of radius r along an axis (0 x, 1 y, 2 z) from a0 to a1, its middle at centre (a point)."""
    u, v = [i for i in range(3) if i != axis]
    lo, hi = sorted((a0, a1))
    for fu, fv in (ROUND if r >= 0.9 else ((1.0, 1.0),)):
        a, b = list(centre), list(centre)
        a[axis], b[axis] = lo, hi
        a[u], b[u] = centre[u] - _q(r * fu), centre[u] + _q(r * fu)
        a[v], b[v] = centre[v] - _q(r * fv), centre[v] + _q(r * fv)
        if a[u] < b[u] and a[v] < b[v]:
            cubes.append(cube(a, b, part))


def _profile(prof, t):
    for (t0, r0), (t1, r1) in zip(prof, prof[1:]):
        if t <= t1:
            return r0 + (r1 - r0) * smooth((t - t0) / (t1 - t0))
    return prof[-1][1]


def _limb(cubes, axis, centre, a0, a1, prof, part, lead=0.0):
    """A round piece from a0 (fraction 0) to a1 (fraction 1) along an axis, its radius following prof; lead runs the
    first slice that far back past a0 (into the body)."""
    n = max(1, round(abs(a1 - a0) / 0.5))
    runs = []
    for i in range(n):
        r = _q(_profile(prof, (i + 0.5) / n))
        s0, s1 = a0 + (a1 - a0) * i / n, a0 + (a1 - a0) * (i + 1) / n
        if runs and runs[-1][0] == r:
            runs[-1][2] = s1
        else:
            runs.append([r, s0, s1])
    if lead:
        runs[0][1] -= lead * (1 if a1 > a0 else -1)
    for r, s0, s1 in runs:
        _disc(cubes, axis, centre, r, s0, s1, part)


def _ball(cubes, axis, centre, r, part):
    """A round joint: half-pixel slices along an axis, each as wide as the ball is there."""
    n = max(2, round(2 * r / 0.5))
    runs = []
    for i in range(n):
        z0, z1 = -r + 2 * r * i / n, -r + 2 * r * (i + 1) / n
        zm = max(abs(z0), abs(z1)) if i not in (n // 2 - 1, n // 2) else 0.0
        rr = _q(math.sqrt(max(0.0, r * r - (zm * 0.85) ** 2)))
        if runs and runs[-1][0] == rr:
            runs[-1][2] = z1
        else:
            runs.append([rr, z0, z1])
    for rr, z0, z1 in runs:
        _disc(cubes, axis, centre, rr, centre[axis] + z0, centre[axis] + z1, part)


def _leg_frames(s, plan, S, E, W):
    """The upper and lower pieces' turns for joints S, E, W (shared by the model and the clips)."""
    r1 = _aim((s, 0, 0), _sub(E, S))                        # the upper part's own axis is straight out
    r2 = _aim((0, -1, 0), _sub(W, E))                       # the lower part's is straight down
    if plan.get('hinge'):
        # The hind leg bends a long way at the knee, so the thigh and shin share one hinge line there (the line
        # across both): their boxes then turn about it like a real knee instead of twisting against each other
        # (the user's report: the hind leg looked broken). The ankle block then sits in the shin's line.
        d1, d2 = _unit(_sub(E, S)), _unit(_sub(W, E))
        h = _unit(_cross(d1, d2))
        x1 = [s * c for c in d1]
        r1 = _cols(x1, _cross(h, x1), h)
        y2 = [-c for c in d2]
        r2 = _cols(_cross(y2, h), y2, h)
    return r1, r2


def legs():
    """Each leg: an upper piece and a lower one aimed between its joints, a foot flat on the ground turned out, and four
    fanned toes, each raised a little at the knuckle with a long claw hooking down from its tip to the ground (a claw is
    its own little piece so it can bend down while the toe lifts, and so the clips can flex them later)."""
    out = []
    for s, side in ((1, 'left'), (-1, 'right')):
        for kind, plan in LEG_PLANS.items():
            (sx, sy, sz), (ex, ey, ez), (wx, wy, wz) = plan['joints']
            S, E, W = (s * sx, sy, sz), (s * ex, ey, ez), (s * wx, wy, wz)
            l1, l2 = _len(_sub(E, S)), _len(_sub(W, E))
            r1, r2 = _leg_frames(s, plan, S, E, W)
            r3 = _ry(-s * plan['turn'])                              # the foot: flat, turned out
            e_rest = (S[0] + s * l1, S[1], S[2])                     # the joints before the turns are applied
            w_rest = (e_rest[0], e_rest[1] - l2, e_rest[2])

            # Round pieces tapering from the muscle to the next joint, and a round ball at each joint a little bigger
            # than the ends meeting there, both pieces ending at its middle, so nothing pokes out at the bend.
            ex_, ey_, ez_ = e_rest
            up, low = [], []
            _limb(up, 0, S, S[0], S[0] + s * l1, plan['upper'], 'leg', lead=1.0)
            _ball(up, 0, e_rest, plan['knob'], 'leg')                                              # elbow or knee
            _limb(low, 1, e_rest, ey_, ey_ - l2, plan['lower'], 'leg')
            pw, pl = plan['palm']
            g = -W[1]                                                # the ground, below the wrist
            fx, fy, fz = w_rest
            ankle = []
            _ball(ankle, 1, w_rest, plan['wrist'], 'leg')                                          # wrist or ankle
            if plan.get('hinge'):
                low += ankle
            foot = ([] if plan.get('hinge') else ankle) + [
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
    extra = []
    for s, side in ((1, 'left'), (-1, 'right')):
        def xs(a, b):
            return sorted([s * a, s * b])
        # a narrow eye set in under a heavy brow: the brow juts out past it and slants down toward the snout (a hard,
        # hooded stare), a thick lower lid narrows it from below, and the pupil sits at the front, staring ahead
        def box(x0, x1, y0, y1, z0_, z1_, part, into=upper):
            into.append(cube([xs(x0, x1)[0], y + y0, ez + z0_], [xs(x0, x1)[1], y + y1, ez + z1_], part))
        box(w - 0.2, w + 0.12, 2.55, 3.2, -0.9, 0.7, 'eye')
        pupil, lid = [], []
        box(w + 0.12, w + 0.17, 2.6, 3.15, -0.62, -0.08, 'pupil', pupil)   # its own piece: it slides to glance
        box(w - 0.2, w + 0.19, 2.32, 2.6, -0.9, 0.7, 'brow', lid)          # the blinking lid, hidden in the lower lid;
        extra.append({'name': f'pupil_{side}', 'parent': 'head',            # a blink stretches it up over the eye
                      'pivot': [s * (w + 0.15), y + 2.875, ez], 'cubes': pupil})
        extra.append({'name': f'lid_{side}', 'parent': 'head', 'pivot': [s * w, y + 2.32, ez], 'cubes': lid})
        box(w - 0.2, w + 0.24, 2.3, 2.62, -1.0, 0.8, 'brow')            # the lower lid
        box(w - 1.3, w + 0.42, 3.15, 4.0, -0.2, 1.2, 'brow')            # the brow over the back of the eye
        box(w - 1.3, w + 0.42, 2.98, 3.85, -1.3, -0.2, 'brow')          # lower toward the snout: the scowl
        nw, ntop, _ = head_shape(0.88)
        nz = z0 - 0.88 * HEAD_LEN
        upper.append(cube([xs(nw - 1.1, nw - 0.35)[0], y + ntop - 0.75, nz - 0.4],      # on the top's side edge
                          [xs(nw - 1.1, nw - 0.35)[1], y + ntop + 0.1, nz + 0.4], 'nostril'))
    hinge = [0.0, y + MOUTH, z0 - GAPE * HEAD_LEN]
    # the forked tongue, lying hidden along the mouth line inside the snout; a flick slides it out 7 pixels through the
    # closed lips, as a monitor's does
    tip = z0 - HEAD_LEN
    tongue = [cube([-0.25, y + MOUTH - 0.12, tip + 1.0], [0.25, y + MOUTH + 0.12, tip + 9.0], 'tongue')]
    for s in (1, -1):
        tongue.append(cube([min(s * 0.1, s * 0.42), y + MOUTH - 0.1, tip + 0.1],
                           [max(s * 0.1, s * 0.42), y + MOUTH + 0.1, tip + 1.4], 'tonguetip'))
    return [{'name': 'head', 'parent': f'neck{NECK_SEGS}', 'pivot': [0.0, y, z0],
             'rotation': pitch(HEAD_PITCH - sum(NECK_TURNS)), 'cubes': upper},
            {'name': 'jaw', 'parent': 'head', 'pivot': hinge, 'cubes': jaw},
            {'name': 'tongue', 'parent': 'head', 'pivot': [0.0, y + MOUTH, tip + 8.5], 'cubes': tongue}] + extra


def model():
    return [body()] + neck() + head() + tail() + legs()


# --- the paint (step 3) --------------------------------------------------------------------------------------------
# Every texture pixel is coloured from where it lies on the model (each piece's own straight-out position: the tail laid
# straight back, the legs straight out and down), so a spot or band carries across the overlapping boxes of a round
# piece, and the pattern is drawn on one even grid of one model pixel everywhere, whatever a box's size.
LOOKS = {
    'nile': {'name': 'Painted', 'note': 'dark charcoal-olive scales with rows of yellow spots across the back, yellow '
             'rings down the tail, a speckled head, a pale yellow lip line, a cream throat and belly with dark bars, '
             'yellow-dotted legs, amber eyes'},
    'plain': {'name': 'Plain colours', 'note': 'shape only: charcoal skin, a cream throat and belly'},
}
GAME_LOOK = 'nile'
NOTES = [
    ('Tail', 'a chain of 12 short pieces, each a rounded cross-section of overlapping boxes, each turned a few degrees '
             'from the last, so it curves down off the hips and lies along the sand to a thin tip without stair steps'),
    ('Neck', 'round slices that change smoothly from the body to the back of the head, on two pieces tilted up, '
             'so it sweeps up from the shoulders and holds the head above the back'),
    ('Head', '14 pixels long. It narrows toward the snout in one-pixel slices, top and bottom edges bevelled, '
             'ending in a blunt rounded tip. The eyes sit far back under a brow ridge, the nostrils near the tip, '
             'and the lower jaw is its own piece, so it opens in the hiss'),
    ('Body', 'blocky on purpose, as in your picture: a deep body with the belly hanging low'),
    ('Legs', 'angled like your picture: the upper part slants out and down from the body, the elbow or knee sticking '
             'out to the side; the fore arm slants down and forward to the hand, the shin down and back to the foot'),
    ('Joints', 'each leg piece is round, cut in half-pixel slices that taper from the muscle to the next joint, and '
               'each elbow, knee, wrist and ankle is a round ball a little bigger than the ends meeting there, so '
               'nothing pokes out at the bend; the hind thigh and shin turn on one knee hinge'),
    ('Feet and claws', 'flat on the ground and turned out (the hind feet more), four long toes fanned out (real monitors have five; four picked by the user for the look), each raised '
                       'a little at the knuckle; each claw a hook in small steps, a dark sheath at the toe, thick and dark '
                       'at the root, thinner and browner as it curves down, a dull horn point touching the ground; every '
                       'toe and claw is its own piece, so the walk can spread and grip them later'),
    ('The paint', 'dark charcoal-olive scales, each pixel a shade different; rows of yellow spots across the back every '
                  '4 pixels, running down the flanks and breaking into a yellow net low on the sides; yellow rings down '
                  'the tail, spotty near the hips and solid toward the tip, the underside pale; a dark head speckled '
                  'yellow with a pale lip line; a cream throat and belly crossed by grey bars; yellow dots on the legs, '
                  'the feet darker; amber eyes with a dark pupil'),
    ('Clips', 'Walk: diagonal legs step together, the feet stay put on the ground, the body swings side to side, the '
              'head stays steady and the tail waves. Standing about: breathing, looking round, the eyes glancing, blinks '
              'and tongue flicks. Tongue: two flicks of the forked tongue. Swim: legs folded back along the body, the '
              'tail sweeping it along. Hiss: body raised, neck arched, mouth wide, the tail lashing'),
    ('Still to come', 'into the game: an attack and a death clip, and how it behaves (raiding crocodile nests)'),
]
CHARCOAL, CREAM = (62, 58, 54), (214, 196, 150)
SKIN = (52, 51, 43)          # charcoal with a little olive
SKIN_DARK = (40, 38, 33)
YELLOW = (214, 176, 58)
YELLOW_DULL = (150, 128, 62)
PALE = (222, 206, 150)       # throat and belly
BAR = (128, 120, 98)         # the grey bars across them


def _h(*k):
    """A steady random number 0 to 1 for a pixel of the grid (the same every build)."""
    n = 0x9E3779B1
    for v in k:
        n = ((n ^ (int(v) & 0xFFFFFFFF)) * 0x85EBCA6B) & 0xFFFFFFFF
        n ^= n >> 13
    n = (n * 0xC2B2AE35) & 0xFFFFFFFF
    return (n ^ (n >> 16)) / 0xFFFFFFFF


def _grain(c, gx, gy, gz, amount=0.07):
    return shade(c, 1 - amount + 2 * amount * _h(gx, gy, gz, 7))


def plain_colour(part, face):
    if part == 'eye':
        return (226, 186, 54)
    if part in ('nostril', 'pupil'):
        return (18, 16, 14)
    if part == 'tongue':
        return (58, 64, 92)          # a dark blue-grey tongue
    if part == 'tonguetip':
        return (84, 90, 122)
    if part == 'clawbase':
        return (28, 24, 21)
    if part == 'claw':
        return (46, 38, 33)
    if part == 'clawmid':
        return (88, 72, 55)
    if part == 'clawtip':
        return (156, 134, 102)
    if part in ('belly', 'throat') or face == 'down':
        return CREAM if face != 'up' else CHARCOAL
    return CHARCOAL


def colour(part, face, bone, x, y, z):
    """The painted colour at a point of a piece (its own straight-out model pixels)."""
    if part == 'eye':
        return (168, 92, 22)                             # a deep amber
    if part == 'brow':
        return _grain(SKIN_DARK, math.floor(abs(x)), math.floor(y), math.floor(z), 0.05)
    if part in ('nostril', 'pupil') or part.startswith(('claw', 'tongue')):
        return plain_colour(part, face)
    gx, gy, gz = math.floor(x), math.floor(y), math.floor(z)
    ax = math.floor(abs(x))                               # pixels out from the middle line (both sides alike)
    spot = _h(ax, gy, gz, 1)
    if bone == 'body':
        if part == 'belly' or face == 'down' or y < BODY_Y[0] + 0.6:
            return _grain(BAR if gz % 3 == 0 and _h(ax, gz, 5) < 0.8 else PALE, ax, gy, gz, 0.04)
        if y < BODY_Y[0] + 3.5:                           # low on the flank: a yellow net fading to the belly
            net = (gz % 2 == 0) != (gy % 2 == 0)
            return _grain(YELLOW_DULL if net and spot < 0.7 else SKIN, ax, gy, gz)
        if face == 'up':
            if (gz - 1) % 4 == 0 and spot < 0.62:        # rows of spots across the back, every 4 pixels
                return _grain(YELLOW, ax, gy, gz, 0.05)
        elif (gz - 1) % 4 in (0, 1) and (gy // 2 + gz // 4) % 2 == 0 and spot < 0.8:
            return _grain(YELLOW, ax, gy, gz, 0.05)      # on the flanks the rows break into 2-pixel spots
        if spot < 0.035:
            return _grain(YELLOW_DULL, ax, gy, gz)
        return _grain(SKIN_DARK if y > BODY_Y[1] - 0.5 else SKIN, ax, gy, gz)
    if bone.startswith('tail'):
        t = (z - BODY_Z[1]) / TAIL_LEN                   # 0 at the hips, 1 at the tip
        r = tail_radius(min(1.0, max(0.0, t)))
        if y - TAIL_AXIS < -0.5 * r or face == 'down':   # the pale underside, banded faintly
            return _grain(PALE if (gz // 3) % 2 else shade(PALE, 0.86), ax, gy, gz, 0.04)
        ring = gz % 6 in (1, 2) if t < 0.7 else gz % 5 in (1, 2)
        if ring and spot < 0.45 + 0.6 * t:               # spotty rings near the hips, solid toward the tip
            return _grain(YELLOW, ax, gy, gz, 0.05)
        if spot < 0.03:
            return _grain(YELLOW_DULL, ax, gy, gz)
        return _grain(SKIN, ax, gy, gz)
    if bone.startswith('neck'):
        if y - NECK_AXIS < -2.0 or face == 'down':       # the throat: cream with grey bars
            return _grain(BAR if gz % 2 == 0 and _h(ax, gz, 6) < 0.7 else PALE, ax, gy, gz, 0.04)
        if gz % 3 == 0 and (gy + gz // 3) % 2 == 0 and spot < 0.6:   # small rows of spots
            return _grain(YELLOW, ax, gy, gz, 0.05)
        return _grain(SKIN, ax, gy, gz)
    if bone in ('head', 'jaw'):
        if part == 'throat':
            if y > NECK_AXIS + MOUTH - 0.6 and face != 'down':
                return _grain(PALE, ax, gy, gz, 0.04)     # the lower lip
            return _grain(BAR if gz % 2 == 0 and _h(ax, gz, 6) < 0.6 else PALE, ax, gy, gz, 0.04)
        if y < NECK_AXIS + MOUTH + 0.35 and face != 'up':  # the pale upper lip line
            return _grain(shade(PALE, 0.92), ax, gy, gz, 0.04)
        ez = NECK_Z0 - NECK_LEN - 0.19 * HEAD_LEN        # the eye: a dark mask round it, a dark streak back from it
        if face != 'up' and ((abs(z - ez) < 2.0 and y > NECK_AXIS + 0.9) or
                             (ez < z < ez + 6.0 and NECK_AXIS + 2.0 < y < NECK_AXIS + 3.2)):
            return _grain(shade(SKIN_DARK, 0.8), ax, gy, gz, 0.05)
        if spot < 0.16:                                  # fine yellow speckles
            return _grain(YELLOW if spot < 0.08 else YELLOW_DULL, ax, gy, gz, 0.05)
        return _grain(SKIN_DARK if face == 'up' else SKIN, ax, gy, gz)
    # the legs: dark with yellow dots, the feet and toes darker
    if '_foot_' in bone or '_toe' in bone:
        if face == 'down':
            return _grain(SKIN_DARK, ax, gy, gz, 0.04)
        return _grain(YELLOW_DULL if spot < 0.06 else SKIN_DARK, ax, gy, gz)
    if spot < 0.14:
        return _grain(YELLOW if spot < 0.08 else YELLOW_DULL, ax, gy, gz, 0.05)
    return _grain(SKIN, ax, gy, gz)


def paint(bones, look='nile'):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
    bones = copy.deepcopy(bones)
    patches = []
    for b in bones:
        for c in b.get('cubes', []):
            c['uv'] = {}
            c['_bone'] = b['name']
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
        for jj in range(h):
            for ii in range(w):
                if look == 'plain':
                    rgb = plain_colour(c['_part'], face)
                else:
                    px, py, pz = face_point(c, face, (ii + 0.5) / w, (jj + 0.5) / h)
                    rgb = colour(c['_part'], face, c['_bone'], px, py, pz)
                rows[y0 + jj][x0 + ii] = tuple(round(v) for v in rgb) + (255,)
    for b in bones:
        for c in b.get('cubes', []):
            c.pop('_part')
            c.pop('_bone')
    return bones, rows, width, height


# --- the clips (step 4): walk, idle, tongue, swim, hiss ----------------------------------------------------------------
# Every clip is worked out pose by pose from what the animal does, not keyed by hand: the feet are placed on the ground
# and the legs are bent to reach them (two-piece leg reaching: the elbow or knee bends out the way it does at rest), so
# a planted foot stays put while the body sways over it. A clip's value for a bone is its turn ADDED to the bone's rest
# turn, in the bone file's degrees (as GeckoLib and the viewer read them).
BODY_PIVOT = (0.0, TAIL_AXIS, -13.0)
WALK_LEN, SWIM_LEN = 1.2, 1.6          # one full stride, one full tail beat (seconds; the game will scale them by speed)


def _rz(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _euler_m(r):
    """A bone's turn in file degrees back to a matrix (the viewer's setRot: x and z negated, the order Z, Y, X)."""
    return _mul(_mul(_rz(-r[2]), _ry(r[1])), _rx(-r[0]))


def _mv(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def _add(a, b):
    return [x + y for x, y in zip(a, b)]


def _ease(x):
    return smooth(x)


def _pulse(t, start, rise, hold, fall):
    """0 before start, easing up to 1 over rise, held, easing back to 0 over fall."""
    if t < start:
        return 0.0
    if t < start + rise:
        return _ease((t - start) / rise)
    if t < start + rise + hold:
        return 1.0
    return 1.0 - _ease((t - start - rise - hold) / fall)


def _ramp(t, keys):
    """A value easing from key to key ((time, value), ...); held before the first and after the last."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            return v0 + (v1 - v0) * _ease((t - t0) / (t1 - t0))
    return keys[-1][1]


class _Poser:
    """Turns wanted bone turns (matrices) into clip values, and bends the legs to reach wanted foot places."""

    def __init__(self):
        bones = model()
        self.rest = {b['name']: b.get('rotation', [0.0, 0.0, 0.0]) for b in bones}
        self.last = {}
        self.worst = 0.0
        self.legs = []
        for s, side in ((1, 'left'), (-1, 'right')):
            for kind, plan in LEG_PLANS.items():
                (sx, sy, sz), (ex, ey, ez), (wx, wy, wz) = plan['joints']
                S, E, W = (s * sx, sy, sz), (s * ex, ey, ez), (s * wx, wy, wz)
                self.legs.append({'s': s, 'side': side, 'kind': kind, 'plan': plan, 'S': S, 'E': E, 'W': W,
                                  'l1': _len(_sub(E, S)), 'l2': _len(_sub(W, E)), 'foot': _ry(-s * plan['turn'])})

    def value(self, name, m):
        """The clip value for a bone whose wanted turn (against its parent) is m: the difference from rest, kept
        within half a turn of the last value so the keys never spin round the long way."""
        r, rest = rot(m), self.rest[name]
        last = self.last.get(name, [0.0, 0.0, 0.0])
        v = []
        for a, b, l in zip(r, rest, last):
            d = a - b
            d += 360.0 * round((l - d) / 360.0)
            v.append(round(d, 3))
        self.last[name] = v
        return v

    def leg(self, p, L, body_m, body_d, wrist, foot_m, in_body=False, curl=0.0):
        """Bends leg L so its wrist or ankle is at wrist and its foot turned foot_m (both on the ground's axes, or the
        body's if in_body), the body turned body_m about its pivot and moved body_d."""
        s, plan, S = L['s'], L['plan'], list(L['S'])
        if in_body:
            Wb, Fb = list(wrist), foot_m
        else:
            Wb = _add(_mv(_tr(body_m), _sub(_sub(wrist, body_d), BODY_PIVOT)), BODY_PIVOT)
            Fb = _mul(_tr(body_m), foot_m)
        d = _sub(Wb, S)
        D = _len(d)
        reach = 0.985 * (L['l1'] + L['l2'])
        if D > reach:                                   # out of reach: as far as it goes (and remember by how much)
            self.worst = max(self.worst, D - reach)
            d = [c * reach / D for c in d]
            Wb, D = _add(S, d), reach
        D = max(D, abs(L['l1'] - L['l2']) * 1.02)
        dh = [c / D for c in d]
        a = (L['l1'] ** 2 - L['l2'] ** 2 + D * D) / (2 * D)
        h = math.sqrt(max(0.0, L['l1'] ** 2 - a * a))
        pole = _sub(L['E'], S)                           # the elbow or knee bends out the way it does at rest
        k = sum(x * y for x, y in zip(pole, dh))
        pole = _unit([x - k * y for x, y in zip(pole, dh)])
        E = [S[i] + a * dh[i] + h * pole[i] for i in range(3)]
        r1, r2 = _leg_frames(s, plan, S, E, Wb)
        name = f"{L['kind']}_%s_{L['side']}"
        p[name % 'upper'] = {'rotation': self.value(name % 'upper', r1)}
        p[name % 'lower'] = {'rotation': self.value(name % 'lower', _mul(_tr(r1), r2))}
        p[name % 'foot'] = {'rotation': self.value(name % 'foot', _mul(_tr(r2), Fb))}
        for i in range(len(plan['toes'])):
            p[name % f'claw{i + 1}'] = {'rotation': [round(curl, 3), 0.0, 0.0]}


def clips():
    """(clips by name, how far a wanted foot place lay out of reach in each, in pixels)."""
    from tilapia_models import clip
    out, worst = {}, {}

    def body(p, ry=0.0, rx=0.0, dy=0.0):
        p['body'] = {'rotation': [round(rx, 3), round(ry, 3), 0.0], 'position': [0.0, round(dy, 3), 0.0]}
        return _euler_m([rx, ry, 0.0]), [0.0, dy, 0.0]

    def stand(P, p, bm, bd):
        for L in P.legs:
            P.leg(p, L, bm, bd, L['W'], L['foot'])

    def neck_turn(p, yaw, lift=0.0, extra=(0.0, 0.0, 0.0)):
        """The neck and head turned yaw (degrees, + to its right) between them, raised lift; extra pitches added."""
        for (bone, share), ex in zip((('neck1', 0.4), ('neck2', 0.3), ('head', 0.3)), extra):
            up = -0.5 * lift if bone != 'neck1' else 0.0
            p[bone] = {'rotation': [round(ex + up, 3), round(share * yaw, 3), 0.0]}

    # The walk: a lizard's trot, the diagonal legs nearly together (left fore with right hind, right fore with left
    # hind, the hind a touch late), each foot on the ground 62 percent of the stride. The body swings from side to
    # side with the stride, bringing each shoulder forward as its foot reaches; the neck turns against the swing so the
    # head stays nearly steady, and a wave runs down the tail.
    P = _Poser()
    phases = {('arm', 'left'): 0.0, ('leg', 'right'): 0.05, ('arm', 'right'): 0.5, ('leg', 'left'): 0.55}
    reach = {'arm': (-27.0, -18.5, 2.5), 'leg': (-7.0, 1.5, 3.0)}      # where the foot lands, leaves, how high it lifts
    duty = 0.62

    def walk(t):
        p = {}
        w = 2 * math.pi * t / WALK_LEN
        yaw = 6.0 * math.cos(w)
        bm, bd = body(p, ry=yaw, dy=0.25 * math.cos(2 * w))
        for L in P.legs:
            front, back, lift = reach[L['kind']]
            u = (t / WALK_LEN - phases[(L['kind'], L['side'])]) % 1.0
            if u < duty:                                 # planted, the body passing over it
                z, up = front + (back - front) * u / duty, 0.0
            else:                                        # swinging forward, lifted, toes hanging
                f = _ease((u - duty) / (1 - duty))
                z, up = back + (front - back) * f, math.sin(math.pi * f)
            wrist = [L['W'][0], L['W'][1] + lift * up, z]
            P.leg(p, L, bm, bd, wrist, _mul(L['foot'], _rx(-28 * up)), curl=18 * up)
        neck_turn(p, 0.0)
        p['neck1']['rotation'][1] = round(-0.55 * yaw, 3)
        p['neck2']['rotation'][1] = round(-0.3 * yaw, 3)
        p['head']['rotation'][1] = round(-0.1 * yaw, 3)
        for i in range(TAIL_SEGS):
            sway = (2.5 + 7.0 * i / (TAIL_SEGS - 1)) * math.sin(w - 0.45 * i - 0.6)
            if i == 0:
                sway -= 1.2 * yaw
            p[f'tail{i + 1}'] = {'rotation': [0.0, round(sway, 3), 0.0]}
        return p
    out['walk'] = clip(WALK_LEN, walk, 48)
    worst['walk'] = P.worst

    # Standing: breathing, the head turning slowly to look one way and the other, the eyes glancing, two blinks,
    # three flicks of the tongue (tasting the air, as monitors do all the time), the tail tip stirring.
    P = _Poser()
    look = ((0, 0), (1.6, 0), (2.4, 16), (4.0, 16), (4.9, -14), (7.2, -14), (8.1, 0))
    lift = ((0, 0), (1.6, 0), (2.4, 6), (4.0, 6), (4.9, 2), (7.2, 2), (8.1, 0))
    glance = ((0, 0.1), (0.8, 0.1), (1.0, -0.25), (2.6, -0.25), (2.9, 0.3), (3.8, 0.3), (4.1, -0.2), (6.5, -0.2),
              (6.8, 0.45), (7.6, 0.45), (8.0, 0.1))
    flicks = (1.0, 5.4, 5.9)

    def flick(p, t, starts):
        f, st = 0.0, 0.0
        for s0 in starts:
            v = _pulse(t, s0, 0.1, 0.3, 0.12)
            if v > f:
                f, st = v, s0
        wave = math.sin(2 * math.pi * (t - st) / 0.15)
        p['tongue'] = {'rotation': [round(5 * f + 4 * f * wave, 3), round(6 * f * wave, 3), 0.0],
                       'position': [0.0, 0.0, round(-7.0 * f, 3)]}

    def idle(t):
        p = {}
        bm, bd = body(p, dy=0.18 * math.sin(2 * math.pi * t / 2.5))
        stand(P, p, bm, bd)
        neck_turn(p, _ramp(t, look), _ramp(t, lift))
        g = _ramp(t, glance)
        blink = max(_pulse(t, 3.0, 0.06, 0.04, 0.08), _pulse(t, 8.4, 0.06, 0.04, 0.08))
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, round(g, 3)]}
            p[f'lid_{side}'] = {'scale': [1.0, round(1 + 2.4 * blink, 3), 1.0]}
        flick(p, t, flicks)
        for i in range(8, TAIL_SEGS):
            p[f'tail{i + 1}'] = {'rotation': [0.0, round(2.0 * (i - 7) / 4 * math.sin(2 * math.pi * t / 5 - 0.5 * i), 3), 0.0]}
        return p
    out['idle'] = clip(10.0, idle, 200)
    worst['idle'] = P.worst

    # The tongue alone (the game plays it over the other clips): two quick flicks.
    def tongue(t):
        p = {}
        flick(p, t, (0.2, 0.75))
        return p
    out['tongue'] = clip(2.0, tongue, 80)
    worst['tongue'] = 0.0

    # Swimming: the legs folded back flat along the sides, feet turned back, and the body and the straightened tail
    # thrown into a wave from side to side, growing toward the tip; the head held up at the surface, steady.
    P = _Poser()

    def swim(t):
        p = {}
        w = 2 * math.pi * t / SWIM_LEN
        yaw = 5.0 * math.cos(w)
        bm, bd = body(p, ry=yaw)
        for L in P.legs:
            s = L['s']
            wrist = (s * 6.3, 9.6, -10.0) if L['kind'] == 'arm' else (s * 6.8, 9.6, 8.5)
            P.leg(p, L, bm, bd, wrist, _cols((0, s, 0), (s, 0, 0), (0, 0, -1)), in_body=True, curl=20.0)
        neck_turn(p, 0.0, extra=(0.0, -3.0, -3.0))
        p['neck1']['rotation'][1] = round(-0.55 * yaw, 3)
        p['neck2']['rotation'][1] = round(-0.3 * yaw, 3)
        p['head']['rotation'][1] = round(-0.15 * yaw, 3)
        for i in range(TAIL_SEGS):
            sway = (4.0 + 7.0 * i / (TAIL_SEGS - 1)) * math.sin(w - 0.5 * i - 0.8)
            if i == 0:
                sway -= 1.4 * yaw
            p[f'tail{i + 1}'] = {'rotation': [round(-P.rest[f'tail{i + 1}'][0], 3), round(sway, 3), 0.0]}
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, -0.25]}
        return p
    out['swim'] = clip(SWIM_LEN, swim, 32)
    worst['swim'] = P.worst

    # The threat: standing tall on straightened legs, the front raised, the neck stretched up and the head tipped down
    # at the threat, the mouth gaping and closing with the hiss, the tail arched up off the ground and lashing.
    P = _Poser()

    def hiss(t):
        p = {}
        gape = 0.5 - 0.5 * math.cos(math.pi * t)
        bm, bd = body(p, rx=-3.0, dy=1.0 + 0.25 * gape)
        stand(P, p, bm, bd)
        neck_turn(p, 0.0, extra=(-7.0 - 2.0 * gape, -3.0, 5.0))
        p['jaw'] = {'rotation': [round(14 + 16 * gape, 3), 0.0, 0.0]}
        for i in range(TAIL_SEGS):
            lash = (3.0 + 6.0 * i / (TAIL_SEGS - 1)) * math.sin(2 * math.pi * t / 0.5 - 0.55 * i)
            p[f'tail{i + 1}'] = {'rotation': [5.0 if i < 3 else 0.0, round(lash, 3), 0.0]}
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, -0.25]}
        return p
    out['hiss'] = clip(2.0, hiss, 40)
    worst['hiss'] = P.worst
    return out, worst


if __name__ == '__main__':
    bs = model()
    print(len(bs), 'bones,', sum(len(b.get('cubes', [])) for b in bs), 'boxes')
    for b in bs:
        print(f"  {b['name']:16} {len(b.get('cubes', [])):3} boxes  rot {b.get('rotation', '')}")
