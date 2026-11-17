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


def legs():
    """Each leg three bones (upper, lower, foot), bracing the body: the upper part straight out from the side, the
    lower straight down, the foot flat with five toes and pale claws, turned out (the hind feet more)."""
    out = []
    for s, side in ((1, 'left'), (-1, 'right')):
        for kind, (uz, lz, fz, ux, lx, fx, turn) in (
                ('arm', ((-24, -19), (-24, -20), (-25, -19), (4.5, 10), (8, 12), (7.5, 13), 15)),
                ('leg', ((-8, -1), (-6, -1), (-6, 0), (4.5, 11), (9, 13), (8.5, 14.5), 35))):
            def x(a, b):
                return sorted([s * a, s * b])
            u = [cube([x(*ux)[0], 9.0, uz[0]], [x(*ux)[1], 13.0, uz[1]], 'leg')]
            low = [cube([x(*lx)[0], 2.0, lz[0]], [x(*lx)[1], 10.0, lz[1]], 'leg')]
            foot = [cube([x(*fx)[0], 0.0, fz[0]], [x(*fx)[1], 2.0, fz[1]], 'leg')]
            width = fx[1] - fx[0]
            for i, reach in enumerate((2.2, 3.0, 3.6, 3.2, 2.4)):           # five toes, the middle ones longest
                tx = fx[0] + 0.35 + i * (width - 1.5) / 4
                a, b = x(tx, tx + 0.8)
                foot.append(cube([a, 0.0, fz[0] - reach], [b, 1.0, fz[0]], 'leg'))
                a, b = x(tx + 0.1, tx + 0.7)
                foot.append(cube([a, 0.0, fz[0] - reach - 1.1], [b, 0.8, fz[0] - reach], 'claw'))
            mid = (lambda r: s * (r[0] + r[1]) / 2)
            out.append({'name': f'{kind}_upper_{side}', 'parent': 'body',
                        'pivot': [s * ux[0], 11.0, (uz[0] + uz[1]) / 2], 'cubes': u})
            out.append({'name': f'{kind}_lower_{side}', 'parent': f'{kind}_upper_{side}',
                        'pivot': [mid(lx), 10.0, (lz[0] + lz[1]) / 2], 'cubes': low})
            out.append({'name': f'{kind}_foot_{side}', 'parent': f'{kind}_lower_{side}',
                        'pivot': [mid(fx), 1.0, (fz[0] + fz[1]) / 2], 'rotation': [0.0, -s * turn, 0.0],
                        'cubes': foot})
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
                   'pale claws; the pattern comes in step 3'}}
GAME_LOOK = 'plain'
NOTES = [
    ('Tail', 'a chain of 12 short pieces, each a rounded cross-section of overlapping boxes, each turned a few degrees '
             'from the last, so it curves down off the hips and lies along the sand to a thin tip without stair steps'),
    ('Neck', 'round slices that change smoothly from the body to the back of the head, on two pieces tilted up, '
             'so it sweeps up from the shoulders and holds the head above the back'),
    ('Head', '14 pixels long. It narrows toward the snout in one-pixel slices, top and bottom edges bevelled, '
             'ending in a blunt rounded tip. The eyes sit far back under a brow ridge, the nostrils near the tip, '
             'and the lower jaw is its own piece for the clips later'),
    ('Body and legs', 'blocky on purpose, as in your picture: a deep body with the belly hanging low, the legs out '
                      'from the sides, then straight down, with flat feet, five toes and pale claws (the hind feet '
                      'turned out more)'),
    ('Still to come', 'step 3, the paint (yellow spots, the banded tail, cream throat, one pixel size everywhere); '
                      'step 4, the animations (walk, tongue, swim)'),
]
CHARCOAL, CREAM = (62, 58, 54), (214, 196, 150)


def colour(part, face):
    if part == 'eye':
        return (226, 186, 54)
    if part == 'nostril':
        return (18, 16, 14)
    if part == 'claw':
        return (226, 218, 196)
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
