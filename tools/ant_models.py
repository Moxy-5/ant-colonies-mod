"""
Builds the red harvester ant (Pogonomyrmex): the worker's GeckoLib model, its animations and textures (the queen uses
the same model, bigger, with her own texture), plus preview pictures.
Run from the project folder:  python tools/ant_models.py
Then run tools/bbmodel_import.py to refresh the Blockbench project.

Units are model pixels (16 = 1 block before the renderer's scale); the head points to -Z, the ground is y = 0.
Every face of a box shows one 6x6 patch of a colour swatch (per-face UV), so even the thinnest parts take their colour.

Bones (the same names as the termites', so animations and code can work on both):
  body
    forebody (head, thorax and legs, jointed where the thorax meets the waist)
      head > mandible_l, mandible_r, antenna_l_1 > antenna_l_2, antenna_r_1 > antenna_r_2, carry
      thorax
      leg_l1 > leg_l1_lower > leg_l1_foot ... leg_r3 (hip, knee, ankle)
    petiole > postpetiole > abdomen (the two waist nodes and the gaster)
'carry' has no boxes: it marks the spot between the mandible tips where a load is held (PelletLayer).

What makes it a harvester ant (from photos of Pogonomyrmex barbatus): rusty red all over, the gaster a little darker
and shinier; a big, almost square head with fine lengthwise ridges; strong dark triangular mandibles; a beard of long
golden hairs under the head (the psammophore, for carrying sand); elbowed antennae (a long first segment, then a
thinner whip that thickens a little at the tip); a humped thorax with two short spines at the back; a two-node waist
(petiole and postpetiole); legs that rise to the knee and reach down to the ground.

Preview pictures go to references/ants/preview/ (front, side, top, front from above, and four moments of a step).
"""
import json, math, os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import termite_models as tm  # the PNG writer and the little box renderer

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'antcolonies')
PREVIEW_DIR = os.path.join(HERE, '..', 'references', 'ants', 'preview')

# The model is drawn in these units and then scaled down by SCALE (about 11 pixels long after scaling; the renderer
# draws the worker at 0.65 of that: about 0.45 blocks long).
SCALE = 0.85

# --- texture: 64x64, 8x8 swatches (column, row, colour, pattern) -------------------------------------------------
SWATCH = {
    'head':    (0, 0, (150, 52, 26), 'ridges'),
    'thorax':  (1, 0, (156, 58, 28), 'noise'),
    'waist':   (2, 0, (146, 50, 25), 'noise'),
    'gaster':  (3, 0, (118, 38, 20), 'shine'),
    'leg':     (4, 0, (168, 70, 34), 'noise'),
    'antenna': (5, 0, (160, 64, 30), 'noise'),
    'jaw':     (6, 0, (58, 22, 12), 'noise'),
    'eye':     (7, 0, (22, 15, 12), 'eye'),
    'beard':   (0, 1, (214, 172, 104), 'hairs'),
    'joint':   (1, 1, (104, 34, 18), 'noise'),
}
# The queen: the same layout, a deeper red (she is drawn with the same model, bigger).
QUEEN_TINT = (0.84, 0.8, 0.8)


def make_texture(path, tint=(1.0, 1.0, 1.0)):
    rnd = random.Random(11)
    pixels = [(0, 0, 0, 0)] * (64 * 64)
    for name, (col, row, rgb, pattern) in SWATCH.items():
        base = [v * t for v, t in zip(rgb, tint)]
        for y in range(8):
            for x in range(8):
                k = rnd.uniform(-0.07, 0.07)
                if pattern == 'ridges':
                    k += -0.12 if x % 2 == 0 else 0.03  # fine lengthwise ridges
                elif pattern == 'shine':
                    k = rnd.uniform(-0.04, 0.04) + (0.22 if (x, y) in ((2, 2), (3, 2), (2, 3)) else 0.0)
                elif pattern == 'eye':
                    k = rnd.uniform(-0.05, 0.05) + (1.6 if (x, y) == (2, 2) else 0.0)  # a glint
                elif pattern == 'hairs':
                    k += 0.1 if x % 2 == 0 else -0.12
                c = tuple(max(0, min(255, int(v * (1 + k)))) for v in base)
                pixels[(row * 8 + y) * 64 + col * 8 + x] = c + (255,)
    tm.write_png(path, 64, 64, pixels)


# --- boxes --------------------------------------------------------------------------------------------------------
def r3(values):
    return [round(v * SCALE, 3) for v in values]


def box(lo, hi, swatch, pivot=None, rotation=None):
    """A box from corner lo to corner hi (design units), optionally turned by rotation about pivot."""
    col, row = SWATCH[swatch][0], SWATCH[swatch][1]
    face = {'uv': [col * 8 + 1, row * 8 + 1], 'uv_size': [6, 6]}
    c = {'origin': r3(lo), 'size': r3([b - a for a, b in zip(lo, hi)]),
         'uv': {f: dict(face) for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if rotation and any(abs(v) > 1e-6 for v in rotation):
        c['pivot'] = r3(pivot)
        c['rotation'] = [round(v, 2) for v in rotation]
    return c


def centered(cx, cy, cz, sx, sy, sz, swatch):
    return box([cx - sx / 2, cy - sy / 2, cz - sz / 2], [cx + sx / 2, cy + sy / 2, cz + sz / 2], swatch)


def rod(a, b, thick, swatch, height=None):
    """A box from point a to point b, thick x thick (or thick wide and height tall): a leg or antenna segment.

    Built along +X from a and turned onto the line a-b. The turn follows the model file's rules (GeckoLib and
    Blockbench read it the same way): X is applied first, then Y, then Z; a turn of Y swings +X toward -Z, a turn of Z
    swings +X down. So the box's +X ends up at (cos y cos z, -cos y sin z, -sin y).
    """
    d = [q - p for p, q in zip(a, b)]
    length = math.sqrt(sum(v * v for v in d))
    dx, dy, dz = (v / length for v in d)
    ry = math.degrees(math.asin(max(-1.0, min(1.0, -dz))))
    rz = math.degrees(math.atan2(-dy, dx))
    h = thick if height is None else height
    return box([a[0], a[1] - h / 2, a[2] - thick / 2], [a[0] + length, a[1] + h / 2, a[2] + thick / 2], swatch, a, [0, ry, rz])


def bone(name, parent, pivot, cubes=()):
    b = {'name': name, 'pivot': r3(pivot)}
    if parent:
        b['parent'] = parent
    if cubes:
        b['cubes'] = list(cubes)
    return b


def mirror(p):
    return (-p[0], p[1], p[2])


# --- the worker ---------------------------------------------------------------------------------------------------
# Where things join (design units, left side +X).
NECK = (0, 2.3, -2.75)
WAIST = (0, 1.95, 0.75)          # the forebody joint: thorax to petiole
POSTPETIOLE = (0, 1.95, 1.85)
GASTER = (0, 2.0, 2.75)
MANDIBLE_TIPS_Z = -6.55
CARRY = (0, 1.75, -6.9)          # between the mandible tips, a little in front

# Legs: hip, knee, ankle, toe (left side; the right side is the mirror image). The knees rise above the back, as in a
# walking ant; the front legs reach forward, the middle ones out, the hind ones (the longest) back.
LEGS = {
    1: [(0.55, 1.4, -2.15), (2.35, 2.95, -3.1), (3.55, 0.55, -4.2), (4.1, 0.0, -4.95)],
    2: [(0.65, 1.35, -0.95), (2.55, 3.05, -1.15), (4.0, 0.55, -0.85), (4.75, 0.0, -0.55)],
    3: [(0.6, 1.35, 0.1), (2.35, 3.05, 1.2), (3.7, 0.55, 3.35), (4.15, 0.0, 4.35)],
}
# Antennae: socket, elbow, then the whip's points to the tip (left side).
ANTENNA_SOCKET = (0.72, 2.75, -5.05)
ANTENNA_ELBOW = (1.65, 3.95, -3.55)
ANTENNA_WHIP = [(2.2, 4.4, -5.2), (2.75, 4.25, -6.7), (3.0, 3.75, -7.75)]


def head_bones():
    head = bone('head', 'forebody', NECK, [
        box([-1.45, 1.2, -5.3], [1.45, 3.15, -2.7], 'head'),            # the big square head
        box([-1.6, 1.45, -5.0], [1.6, 2.9, -3.0], 'head'),              # its full sides
        box([-1.2, 3.15, -5.0], [1.2, 3.4, -2.95], 'head'),             # the rounded crown
        box([-0.85, 1.35, -5.6], [0.85, 2.15, -5.3], 'head'),           # the clypeus (the plate over the jaws)
        box([1.6, 2.2, -4.45], [1.75, 2.8, -3.65], 'eye'),
        box([-1.75, 2.2, -4.45], [-1.6, 2.8, -3.65], 'eye'),
        box([-1.0, 0.85, -5.1], [1.0, 1.2, -3.4], 'beard'),             # the psammophore: a beard of long hairs
        box([-0.55, 1.75, -2.95], [0.55, 2.55, -2.6], 'thorax'),        # the neck
    ])
    bones = [head]
    for side, sx in (('l', 1), ('r', -1)):
        def m(p):
            return p if sx > 0 else mirror(p)
        base = m((0.62, 1.55, -5.3))
        lo_x, hi_x = sorted((0.25 * sx, 1.15 * sx))
        tip_lo, tip_hi = sorted((0.0, 0.7 * sx))
        bones.append(bone(f'mandible_{side}', 'head', base, [
            box([lo_x, 1.35, -6.05], [hi_x, 1.8, -5.25], 'jaw'),                  # the broad base
            box([tip_lo, 1.4, MANDIBLE_TIPS_Z], [tip_hi, 1.75, -6.0], 'jaw'),     # the toothed edge, meeting the other
        ]))
        socket, elbow = m(ANTENNA_SOCKET), m(ANTENNA_ELBOW)
        bones.append(bone(f'antenna_{side}_1', 'head', socket, [rod(socket, elbow, 0.32, 'antenna')]))
        whip, points = [], [elbow] + [m(p) for p in ANTENNA_WHIP]
        for i, (a, b) in enumerate(zip(points, points[1:])):
            whip.append(rod(a, b, 0.24 if i < 2 else 0.34, 'antenna'))       # thicker at the tip (the club)
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', elbow, whip))
    bones.append(bone('carry', 'head', CARRY))
    return bones


def thorax_bone():
    cubes = [
        box([-1.0, 1.35, -2.75], [1.0, 2.85, -1.4], 'thorax'),     # pronotum, the high front hump
        box([-0.8, 1.3, -1.45], [0.8, 2.6, -0.3], 'thorax'),       # mesonotum
        box([-0.75, 1.25, -0.35], [0.75, 2.25, 0.8], 'thorax'),    # propodeum, lower at the back
    ]
    for sx in (1, -1):
        cubes.append(rod((0.42 * sx, 2.15, 0.55), (0.5 * sx, 2.75, 1.05), 0.18, 'thorax'))  # propodeal spines
    return bone('thorax', 'forebody', (0, 2.0, -1.0), cubes)


def leg_bones():
    bones = []
    for n, (hip, knee, ankle, toe) in LEGS.items():
        for side, sx in (('l', 1), ('r', -1)):
            def m(p):
                return p if sx > 0 else mirror(p)
            h, k, a, t = m(hip), m(knee), m(ankle), m(toe)
            bones.append(bone(f'leg_{side}{n}', 'forebody', h, [
                centered(h[0], h[1], h[2], 0.55, 0.55, 0.55, 'leg'),   # the coxa
                rod(h, k, 0.42, 'leg')]))                              # the femur
            bones.append(bone(f'leg_{side}{n}_lower', f'leg_{side}{n}', k, [
                centered(k[0], k[1], k[2], 0.42, 0.42, 0.42, 'joint'),
                rod(k, a, 0.32, 'leg')]))                              # the tibia
            bones.append(bone(f'leg_{side}{n}_foot', f'leg_{side}{n}_lower', a, [rod(a, t, 0.24, 'joint')]))  # the tarsus
    return bones


def worker_model():
    bones = [bone('body', None, (0, 2.0, 0)), bone('forebody', 'body', WAIST)]
    bones += head_bones()
    bones.append(thorax_bone())
    bones += leg_bones()
    bones.append(bone('petiole', 'body', WAIST, [
        box([-0.3, 1.6, 0.75], [0.3, 2.0, 1.35], 'waist'),        # the stalk
        box([-0.5, 1.55, 1.2], [0.5, 2.6, 1.85], 'waist'),        # the node, rising
    ]))
    bones.append(bone('postpetiole', 'petiole', POSTPETIOLE, [
        box([-0.68, 1.45, 1.85], [0.68, 2.5, 2.8], 'waist'),
    ]))
    bones.append(bone('abdomen', 'postpetiole', GASTER, [
        box([-1.3, 0.95, 2.85], [1.3, 3.1, 6.15], 'gaster'),
        box([-1.48, 1.25, 3.3], [1.48, 2.8, 5.6], 'gaster'),      # its full sides
        box([-1.05, 1.3, 2.65], [1.05, 2.7, 2.9], 'gaster'),      # the front, rounded into the waist
        box([-0.95, 1.25, 6.1], [0.95, 2.6, 6.55], 'gaster'),     # the tip
        box([-1.0, 3.1, 3.4], [1.0, 3.3, 5.5], 'gaster'),         # the rounded back
    ]))
    return bones


# --- animations ---------------------------------------------------------------------------------------------------
# The same rules as the termites (Blockbench's): Y swings a left leg forward (positive) and a right leg back; a
# negative Z lifts a left leg, a positive one a right leg; a positive X tips a head or an antenna down.
IO = 'easeInOutSine'


def keys(frames, channel='rotation'):
    return {channel: {f'{t:g}': {'vector': [round(v, 2) for v in vec], 'easing': IO} for t, vec in frames}}


WALK_SECONDS, STEP_SWING, STEP_LIFT, KNEE_LIFT = 0.5, 22.0, 18.0, 14.0


def walk():
    """A tripod gait: front-left, middle-right and back-left step together, then the other three. Each leg pushes
    back on the ground, then lifts (the knee bends) and swings forward. The antennae sweep out of step, the gaster
    sways a little against the stride."""
    anim = {}
    t4 = WALK_SECONDS / 4
    for n in (1, 2, 3):
        for side in ('l', 'r'):
            left = side == 'l'
            fwd = STEP_SWING if left else -STEP_SWING
            lift = -STEP_LIFT if left else STEP_LIFT
            knee = -KNEE_LIFT if left else KNEE_LIFT
            first = (side, n) in (('l', 1), ('r', 2), ('l', 3))
            # Ground phase then swing phase, for the first group; the second group is half a step later.
            ground = [(0, [0, fwd, 0]), (t4, [0, 0, 0]), (2 * t4, [0, -fwd, 0]), (3 * t4, [0, 0, lift]), (4 * t4, [0, fwd, 0])]
            lower = [(0, [0, 0, 0]), (2 * t4, [0, 0, 0]), (3 * t4, [0, 0, knee]), (4 * t4, [0, 0, 0])]
            if not first:
                ground = [(0, [0, -fwd, 0]), (t4, [0, 0, lift]), (2 * t4, [0, fwd, 0]), (3 * t4, [0, 0, 0]), (4 * t4, [0, -fwd, 0])]
                lower = [(0, [0, 0, 0]), (t4, [0, 0, knee]), (2 * t4, [0, 0, 0]), (4 * t4, [0, 0, 0])]
            anim[f'leg_{side}{n}'] = keys(ground)
            anim[f'leg_{side}{n}_lower'] = keys(lower)
    anim['antenna_l_1'] = keys([(0, [6, 10, 0]), (2 * t4, [-4, -6, 0]), (4 * t4, [6, 10, 0])])
    anim['antenna_r_1'] = keys([(0, [-4, 6, 0]), (2 * t4, [6, -10, 0]), (4 * t4, [-4, 6, 0])])
    anim['antenna_l_2'] = keys([(0, [0, 0, 0]), (t4, [8, 0, 0]), (2 * t4, [0, 0, 0]), (3 * t4, [-6, 0, 0]), (4 * t4, [0, 0, 0])])
    anim['antenna_r_2'] = keys([(0, [-6, 0, 0]), (t4, [0, 0, 0]), (2 * t4, [8, 0, 0]), (3 * t4, [0, 0, 0]), (4 * t4, [-6, 0, 0])])
    anim['abdomen'] = keys([(0, [0, 3, 0]), (2 * t4, [0, -3, 0]), (4 * t4, [0, 3, 0])])
    anim['head'] = keys([(0, [0, -2, 0]), (2 * t4, [0, 2, 0]), (4 * t4, [0, -2, 0])])
    return anim


IDLE_SECONDS = 4.0


def idle():
    """Standing: the antennae feel about (each on its own rhythm, the whips flicking), the head turns a little, the
    gaster rises and sinks slowly (breathing), the mandibles open and close once."""
    T = IDLE_SECONDS
    return {
        'antenna_l_1': keys([(0, [0, 0, 0]), (0.7, [12, 14, 0]), (1.5, [-6, 4, 0]), (2.3, [8, -6, 0]), (3.2, [-4, 8, 0]), (T, [0, 0, 0])]),
        'antenna_r_1': keys([(0, [0, 0, 0]), (0.5, [-6, -6, 0]), (1.3, [10, -14, 0]), (2.4, [-4, 2, 0]), (3.0, [8, -8, 0]), (T, [0, 0, 0])]),
        'antenna_l_2': keys([(0, [0, 0, 0]), (0.9, [10, 0, 0]), (1.2, [-4, 0, 0]), (2.6, [6, 0, 0]), (T, [0, 0, 0])]),
        'antenna_r_2': keys([(0, [0, 0, 0]), (0.6, [-4, 0, 0]), (1.7, [10, 0, 0]), (2.0, [0, 0, 0]), (3.4, [6, 0, 0]), (T, [0, 0, 0])]),
        'head': keys([(0, [0, 0, 0]), (1.2, [3, 6, 0]), (2.0, [3, 6, 0]), (3.1, [0, -4, 0]), (T, [0, 0, 0])]),
        'abdomen': keys([(0, [0, 0, 0]), (T / 2, [-3, 0, 0]), (T, [0, 0, 0])]),
        'mandible_l': keys([(0, [0, 0, 0]), (2.4, [0, 0, 0]), (2.6, [0, -12, 0]), (2.85, [0, 0, 0]), (T, [0, 0, 0])]),
        'mandible_r': keys([(0, [0, 0, 0]), (2.4, [0, 0, 0]), (2.6, [0, 12, 0]), (2.85, [0, 0, 0]), (T, [0, 0, 0])]),
    }


def animations():
    return {'format_version': '1.8.0', 'animations': {
        'idle': {'loop': True, 'animation_length': IDLE_SECONDS, 'bones': idle()},
        'walk': {'loop': True, 'animation_length': WALK_SECONDS, 'bones': walk()}}}


# --- preview ------------------------------------------------------------------------------------------------------
def rotate(p, pivot, rot):
    """Turns point p about pivot by rot = [x, y, z] degrees, by the model file's rules (X first, then Y, then Z)."""
    x, y, z = (a - b for a, b in zip(p, pivot))
    rx, ry, rz = (math.radians(v) for v in rot)
    t = -rx
    y, z = y * math.cos(t) - z * math.sin(t), y * math.sin(t) + z * math.cos(t)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    t = -rz
    x, y = x * math.cos(t) - y * math.sin(t), x * math.sin(t) + y * math.cos(t)
    return (pivot[0] + x, pivot[1] + y, pivot[2] + z)


def model_boxes(bones, anim=None, tint=(1.0, 1.0, 1.0)):
    anim = anim or {}
    by_name = {b['name']: b for b in bones}
    colour = {(c * 8 + 1, r * 8 + 1): tuple(int(v * t) for v, t in zip(rgb, tint)) for c, r, rgb, _ in SWATCH.values()}
    boxes = []
    for b in bones:
        for c in b.get('cubes', []):
            (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
            corners = [(ox + i * sx, oy + j * sy, oz + k * sz) for i in (0, 1) for j in (0, 1) for k in (0, 1)]
            if 'rotation' in c:
                corners = [rotate(p, c['pivot'], c['rotation']) for p in corners]
            name = b['name']
            while name:
                if name in anim:
                    corners = [rotate(p, by_name[name]['pivot'], anim[name]) for p in corners]
                name = by_name[name].get('parent')
            boxes.append((corners, colour[tuple(c['uv']['north']['uv'])]))
    return boxes


def sample(bones_anim, t):
    """Every bone's rotation at time t (eased between the key frames, as GeckoLib's easeInOutSine)."""
    out = {}
    for name, channels in bones_anim.items():
        frames = sorted((float(k), v['vector']) for k, v in channels['rotation'].items())
        for (t0, r0), (t1, r1) in zip(frames, frames[1:]):
            if t0 <= t <= t1:
                f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                f = -(math.cos(math.pi * f) - 1) / 2
                out[name] = [a + (b - a) * f for a, b in zip(r0, r1)]
                break
    return out


SHEETS = [
    ('front.png', 'FRONT', 0, 0, 34.0, 470, 330, 0.85),
    ('front_above.png', 'FRONT, FROM ABOVE ANGLED DOWN', 0, -35, 30.0, 470, 420, 0.65),
    ('side.png', 'SIDE', -90, 0, 30.0, 560, 260, 0.82),
    ('top.png', 'FROM ABOVE', -90, -89.9, 30.0, 560, 420, 0.5),
]


def render(models):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    for name, title, yaw, pitch, ppx, cw, ch, floor in SHEETS:
        canvas = tm.Canvas(cw * len(models), tm.TITLE_H + ch + tm.LABEL_H)
        canvas.text(12, 12, title, scale=4)
        for k, (label, boxes) in enumerate(models):
            x0, y0 = cw * k, tm.TITLE_H
            canvas.frame(x0, y0, cw, ch + tm.LABEL_H)
            canvas.text(x0 + 10, y0 + 8, label)
            tm.draw(canvas, boxes, (x0 + 1, y0 + tm.LABEL_H, cw - 2, ch - 1), floor, math.radians(yaw), math.radians(pitch), ppx)
        canvas.save(os.path.join(PREVIEW_DIR, name))


def render_walk(bones, frames=4):
    rows = [('SIDE', -90, 0, 30.0, 230, 0.82), ('FROM ABOVE', -90, -89.9, 26.0, 330, 0.5)]
    cw = 470
    canvas = tm.Canvas(cw * frames, tm.TITLE_H + sum(r[4] + tm.LABEL_H for r in rows))
    canvas.text(12, 12, 'WALK - ONE STEP', scale=4)
    anim = animations()['animations']['walk']['bones']
    y0 = tm.TITLE_H
    for title, yaw, pitch, ppx, ch, floor in rows:
        for k in range(frames):
            t = WALK_SECONDS * k / frames
            x0 = cw * k
            canvas.frame(x0, y0, cw, ch + tm.LABEL_H)
            canvas.text(x0 + 10, y0 + 8, f'{title} {int(t * 1000)} MS')
            tm.draw(canvas, model_boxes(bones, sample(anim, t)), (x0 + 1, y0 + tm.LABEL_H, cw - 2, ch - 1), floor,
                    math.radians(yaw), math.radians(pitch), ppx)
        y0 += ch + tm.LABEL_H
    canvas.save(os.path.join(PREVIEW_DIR, 'walk.png'))


def dump(rel, data):
    path = os.path.join(ROOT, rel)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1)


if __name__ == '__main__':
    worker = worker_model()
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.worker_ant', 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 3, 'visible_bounds_height': 2, 'visible_bounds_offset': [0, 0.5, 0]},
        'bones': worker}]}
    dump('geo/entity/worker_ant.geo.json', geo)
    dump('animations/entity/worker_ant.animation.json', animations())
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'worker_ant.png'))
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'queen_ant.png'), QUEEN_TINT)
    render([('WORKER', model_boxes(worker)), ('QUEEN COLOURS', model_boxes(worker, tint=QUEEN_TINT))])
    render_walk(worker)
    print('ant model, animations, textures and preview pictures written')
