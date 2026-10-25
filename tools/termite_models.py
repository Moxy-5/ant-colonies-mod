"""
Builds the placeholder termite king and queen: GeckoLib models, animations and textures, plus a preview picture.
Run from the project folder:  python tools/termite_models.py

Units are model pixels (16 = 1 block before the renderer's scale); the head points to -Z.
The queen's head, thorax and legs are the king's; her abdomen is six segments (seg_1 .. seg_6), each with a pale
skin (seg_N_skin), a dark back plate (seg_N_plate) and a lighter belly plate (seg_N_belly). The renderer
(TermiteQueenModel) stretches the skin as she grows and keeps the plates their size, so they drift apart.
'carry' (no boxes) marks where a load is held in the mandibles. Keep these bone names: the Java code looks them up.

The worker (termite_worker) is the king's skeleton made pale, blind and wingless, a little smaller. The soldiers
(termite_minor_soldier, termite_major_soldier) are the worker's pale body with a big orange-brown armoured head and long
black crossing jaws, their legs set further forward under the head's weight. Each model has its own animation file of
the same name (2026-10-18): idle, its walks and turns made by the motion library for its own legs (see walk_clips), and
the look round and the freeze (worker, king, queen) or head_bang (the drumming) and the bite (soldiers). The nymph's texture (termite_nymph.png) is four colour bands for TermiteNymphRenderer.

The model checker: references/termites/preview/ gets one labelled picture per camera angle (front.png looking at
her face, front_above.png, side.png, top.png), each showing the king, the worker and the queen at 0%, 50%, 75% and
100% grown, all at the same scale, and walk.png with four moments of a step.
"""
import json, math, os, random, struct, sys, zlib
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)

# Where the forebody (head, thorax, legs) joins the abdomen, in model pixels.
FOREBODY_JOINT = (0, 1.7, 0.4)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = str(mod_paths.assets('savanna'))
PREVIEW_DIR = os.path.join(HERE, '..', 'references', 'termites', 'preview')

# --- texture: 64x64, 8x8 swatches, each a colour with a little noise -------------------------------------------
SWATCH = {
    'head':     (0, 0, (88, 44, 22)),
    'back':     (1, 0, (66, 33, 16)),    # dark chestnut back plates
    'joint':    (2, 0, (150, 100, 50)),  # golden-brown seam between segments
    'belly':    (3, 0, (150, 104, 62)),  # lighter underside plates
    'leg':      (4, 0, (196, 146, 70)),
    'antenna':  (5, 0, (172, 118, 56)),
    'eye':      (6, 0, (22, 16, 16)),
    'jaw':      (7, 0, (42, 20, 10)),
    'wing':     (0, 1, (92, 56, 30)),    # wing stubs (scales) left after the wings were dropped
    'skin':     (1, 1, (238, 226, 196)), # the queen's stretched pale skin
    'thorax':   (2, 1, (78, 39, 19)),
    # The worker (from the real photo): pale and soft, a round yellowish head, food showing grey through the abdomen.
    'w_head':    (0, 2, (212, 172, 96)),
    'w_body':    (1, 2, (236, 226, 200)),
    'w_belly':   (2, 2, (226, 214, 188)),
    'w_gut':     (3, 2, (178, 170, 154)),
    'w_leg':     (4, 2, (228, 212, 170)),
    'w_antenna': (5, 2, (214, 186, 128)),
    'w_jaw':     (6, 2, (132, 86, 42)),
    # The soldiers (step 6a): a pale soft body like the worker's, a big shiny orange-brown armoured head, darker at the
    # front where the long black jaws join. Only the soldiers' texture has these (see make_texture).
    's_head':    (0, 3, (178, 94, 36)),
    's_shine':   (1, 3, (218, 138, 64)),   # the light catching the top of the head
    's_front':   (2, 3, (118, 54, 22)),
    's_jaw':     (3, 3, (24, 15, 11)),
    's_body':    (4, 3, (238, 230, 208)),
    's_belly':   (5, 3, (226, 216, 192)),
    's_leg':     (6, 3, (232, 218, 182)),
    's_antenna': (7, 3, (200, 140, 76)),
}

# The nymph's texture (TermiteNymphRenderer): four colour bands of 8 rows each, 32x32.
NYMPH_BANDS = [(222, 196, 140), (240, 232, 210), (196, 190, 176), (234, 222, 192)]

def write_png(path, width, height, pixels):
    raw = b''.join(b'\x00' + bytes([c for px in pixels[y * width:(y + 1) * width] for c in px]) for y in range(height))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)

def make_texture(path, soldier=False):
    """The 64x64 swatch texture. The soldiers' colours (s_...) are only drawn into the soldiers' texture, so the
    other textures stay exactly as they were."""
    rnd = random.Random(7)
    pixels = [(0, 0, 0, 0)] * (64 * 64)
    for name, (col, row, rgb) in SWATCH.items():
        if name.startswith('s_') and not soldier:
            continue
        for y in range(8):
            for x in range(8):
                k = rnd.uniform(-0.09, 0.09) if name != 'eye' else rnd.uniform(-0.03, 0.12)
                if name == 'skin':
                    k = rnd.uniform(-0.04, 0.03)
                c = tuple(max(0, min(255, int(v * (1 + k)))) for v in rgb)
                pixels[(row * 8 + y) * 64 + col * 8 + x] = c + (255,)
    write_png(path, 64, 64, pixels)

def cube(origin, size, swatch, pivot=None, yaw=0.0):
    """A box. yaw: turned this many degrees about the vertical through pivot; as in Blockbench, a positive turn
    swings +X toward -Z (a left leg forward)."""
    col, row, _ = SWATCH[swatch]
    face = {'uv': [col * 8 + 1, row * 8 + 1], 'uv_size': [6, 6]}
    c = {'origin': [round(v, 3) for v in origin], 'size': [round(v, 3) for v in size],
         'uv': {f: dict(face) for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if yaw:
        c['pivot'] = [round(v, 3) for v in pivot]
        c['rotation'] = [0, round(yaw, 2), 0]
    return c

def turn(x, z, yaw):
    """Where the point (x, z) ends up after a turn of yaw degrees about the origin (the same rule as cube())."""
    a = math.radians(yaw)
    return x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)

def centered(cx, cy, cz, sx, sy, sz, swatch):
    return cube([cx - sx / 2, cy - sy / 2, cz - sz / 2], [sx, sy, sz], swatch)

def bone(name, parent, pivot, cubes=()):
    b = {'name': name, 'pivot': [round(v, 3) for v in pivot]}
    if parent:
        b['parent'] = parent
    if cubes:
        b['cubes'] = list(cubes)
    return b

# --- shared front half: head, antennae, thorax, wing stubs, legs ----------------------------------------------
# The legs: (pair, where it joins the thorax along the body, how far it turns forward in degrees, upper leg length,
# and optionally the hip's height, 1.4 if left out).
KING_LEGS = ((1, -1.9, 40.0, 1.35), (2, -0.9, -5.0, 1.35), (3, 0.1, -45.0, 1.35))

def front_half(legs=KING_LEGS):
    # forebody: head, thorax and legs together, jointed where the thorax meets the abdomen (the queen's front droops
    # around this joint when she is full grown).
    bones = [bone('body', None, [0, 1.5, 0]), bone('forebody', 'body', list(FOREBODY_JOINT))]
    bones.append(bone('head', 'forebody', [0, 1.8, -2.3], [
        cube([-1.1, 1.0, -4.2], [2.2, 1.7, 1.9], 'head'),
        cube([-0.8, 2.7, -4.0], [1.6, 0.2, 1.5], 'head'),        # rounded top
        cube([1.1, 1.65, -3.7], [0.25, 0.6, 0.75], 'eye'),       # only kings and queens have eyes
        cube([-1.35, 1.65, -3.7], [0.25, 0.6, 0.75], 'eye'),
        cube([-0.7, 1.0, -4.45], [1.4, 0.5, 0.25], 'head'),      # upper lip
    ]))
    bones.append(bone('mandible_l', 'head', [0.4, 1.2, -4.3], [cube([0.1, 1.0, -4.95], [0.45, 0.35, 0.7], 'jaw')]))
    bones.append(bone('mandible_r', 'head', [-0.4, 1.2, -4.3], [cube([-0.55, 1.0, -4.95], [0.45, 0.35, 0.7], 'jaw')]))
    # No boxes: the spot just in front of the mandible tips where a load is held (CarriedLoadLayer).
    bones.append(bone('carry', 'head', [0, 1.15, -5.35]))
    # Beaded antennae, held forward and a little outward: rising slightly at first, the tips curving down.
    for side, sx in (('l', 1), ('r', -1)):
        start = (0.65 * sx, 2.3, -4.1)
        beads1, p = [], list(start)
        for i in range(3):
            s = 0.38 if i % 2 == 0 else 0.3
            beads1.append(centered(p[0], p[1], p[2], s, s, s, 'antenna'))
            p = [p[0] + 0.14 * sx, p[1] + 0.16, p[2] - 0.34]
        mid = list(p)
        beads2 = []
        for i in range(7):
            s = 0.34 if i % 2 == 0 else 0.27
            beads2.append(centered(p[0], p[1], p[2], s, s, s, 'antenna'))
            drop = 0.02 if i < 3 else -0.14
            p = [p[0] + 0.2 * sx, p[1] + drop, p[2] - 0.3]
        bones.append(bone(f'antenna_{side}_1', 'head', list(start), beads1))
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', mid, beads2))
    bones.append(bone('thorax', 'forebody', [0, 1.7, -1.0], [
        cube([-1.0, 1.1, -2.3], [2.0, 1.3, 1.0], 'thorax'),      # the front shield
        cube([-1.0, 1.0, -1.3], [2.0, 1.35, 0.9], 'back'),
        cube([-1.05, 1.0, -0.4], [2.1, 1.35, 0.8], 'back'),
        cube([-0.8, 0.85, -2.2], [1.6, 0.25, 2.5], 'belly'),
    ]))
    # Wing stubs: what is left of the four wings after they were dropped. Small dark triangular scales on the
    # sides of the thorax, pointing back (a wide base and a narrow tip).
    stubs = []
    for sx in (1, -1):
        for z0 in (-1.25, -0.35):
            for inner, outer, z1, z2 in ((0.55, 1.12, z0, z0 + 0.45), (0.75, 1.1, z0 + 0.45, z0 + 0.75)):
                lo, hi = (inner, outer) if sx > 0 else (-outer, -inner)
                stubs.append(cube([lo, 2.32, z1], [hi - lo, 0.12, z2 - z1], 'wing'))
    bones.append(bone('wing_stubs', 'thorax', [0, 2.35, -0.5], stubs))
    # Three pairs of legs from the thorax, splayed: the front pair reaches forward, the middle pair out to the side,
    # the back pair backward. Upper leg out (turned), lower leg down to the ground, foot pointing on outward.
    foot = 0.6
    for n, z, yaw, upper, *rest in legs:
        hy = rest[0] if rest else 1.4  # the hip's height (the thigh runs level out from it)
        for side, sx in (('l', 1), ('r', -1)):
            a = yaw * sx  # the right side is the mirror image
            x0 = 1.0 * sx
            dx, dz = turn(upper * sx, 0.0, a)
            kx, kz = x0 + dx, z + dz  # the knee
            bones.append(bone(f'leg_{side}{n}', 'forebody', [x0, hy, z],
                              [cube([x0 if sx > 0 else x0 - upper, hy - 0.15, z - 0.15], [upper, 0.3, 0.3], 'leg', [x0, hy, z], a)]))
            bones.append(bone(f'leg_{side}{n}_lower', f'leg_{side}{n}', [kx, hy, kz],
                              [cube([kx - 0.14, 0.25, kz - 0.14], [0.28, hy - 0.15, 0.28], 'leg')]))
            bones.append(bone(f'leg_{side}{n}_foot', f'leg_{side}{n}_lower', [kx, 0.15, kz],
                              [cube([kx if sx > 0 else kx - foot, 0.0, kz - 0.12], [foot, 0.28, 0.24], 'leg', [kx, 0.15, kz], a)]))
    return bones

# --- the king's abdomen: dark plates on top, lighter belly, golden lines between segments -----------------------
KING_SEGMENTS = [(2.3, 1.7), (2.5, 1.85), (2.55, 1.9), (2.45, 1.8), (2.15, 1.6), (1.6, 1.15)]

def abdomen_bone(i):
    """The name of the abdomen's i-th segment bone (0 the front one): abdomen, abdomen_2, abdomen_3, ..."""
    return 'abdomen' if i == 0 else f'abdomen_{i + 1}'

def king_abdomen():
    """The abdomen of the king, the worker and the soldiers (2026-10-18, the user's pick, soft segments): a real termite
    has no waist, and its soft abdomen curves along its length over an edge or round a turn. So each segment (its back
    plate, belly plate and the seam behind) is its own bone, hung off the one in front at its front edge, in a chain:
    the clips that sway `abdomen` sway the whole of it, and the live layer bends each joint along the path."""
    bones, z, parent = [], 0.4, 'body'
    for i, (w, h) in enumerate(KING_SEGMENTS):
        cy = 1.85
        cubes = [centered(0, cy + h * 0.2, z + 0.29, w, h * 0.6, 0.5, 'back'),
                 centered(0, cy - h * 0.3, z + 0.29, w * 0.92, h * 0.4, 0.5, 'belly'),
                 centered(0, cy, z + 0.56, w * 0.9, h * 0.9, 0.1, 'joint')]
        bones.append(bone(abdomen_bone(i), parent, [0, cy, round(z, 3)], cubes))
        parent = abdomen_bone(i)
        z += 0.6
    return bones

# --- the queen's abdomen: six segments that the renderer stretches ----------------------------------------------
# Keep these numbers in step with TermiteQueenModel.java.
QUEEN_SEG_LENGTH = 0.65
QUEEN_WIDTH = 2.8
QUEEN_HEIGHT = 2.1
QUEEN_TAPER = [0.8, 0.95, 1.0, 1.0, 0.93, 0.75]
QUEEN_CENTER_Y = 1.8
QUEEN_START_Z = 0.4
# Full grown: about 2 blocks long in the game, about four and a half times as long as wide (a real queen's shape).
GROW_LENGTH, GROW_WIDTH, GROW_HEIGHT, SINK = 9.5, 3.0, 3.4, 0.7
# Between these sizes she settles: her abdomen comes to rest on the floor and her head and thorax rise to sit on its
# front end (THORAX_HOLD of the way up), legs dangling, as in a real queen too heavy to walk.
SETTLE_FROM, SETTLE_TO, THORAX_Y, THORAX_HOLD = 0.45, 0.65, 1.7, 0.55
# From DROOP_FROM to DROOP_TO her front (forebody) tips forward and down around the joint, up to DROOP_DEGREES, and
# stays so: hanging off the front of the abdomen.
DROOP_FROM, DROOP_TO, DROOP_DEGREES = 0.65, 0.75, 30.0

def queen_abdomen():
    bones = [bone('abdomen', 'body', [0, QUEEN_CENTER_Y, QUEEN_START_Z])]
    for i, t in enumerate(QUEEN_TAPER):
        n = i + 1
        zc = QUEEN_START_Z + QUEEN_SEG_LENGTH * (i + 0.5)
        w, h, l = QUEEN_WIDTH * t, QUEEN_HEIGHT * t, QUEEN_SEG_LENGTH
        cy = QUEEN_CENTER_Y
        # Small (just founded) the plates cover the skin completely, so she looks like the king: each segment's
        # plates reach from its front edge to a thin golden seam at its back (the last segment's go to the end).
        last = n == len(QUEEN_TAPER)
        plate_l = l if last else l * 0.9
        pz = zc - (l - plate_l) / 2
        seam = [] if last else [centered(0, cy, zc + l * 0.44, w - 0.05, h - 0.05, l * 0.12, 'joint')]
        bones.append(bone(f'seg_{n}', 'abdomen', [0, cy, zc], seam))
        # The skin is two crossed boxes (a wide flat one and a narrow tall one), so the swollen body looks round.
        bones.append(bone(f'seg_{n}_skin', f'seg_{n}', [0, cy, zc], [
            centered(0, cy, zc, w - 0.15, (h - 0.15) * 0.72, l - 0.02, 'skin'),
            centered(0, cy, zc, (w - 0.15) * 0.72, h - 0.15, l - 0.02, 'skin')]))
        bones.append(bone(f'seg_{n}_plate', f'seg_{n}', [0, cy, zc], [centered(0, cy + h * 0.5 - h * 0.275, pz, w, h * 0.55, plate_l, 'back')]))
        bones.append(bone(f'seg_{n}_belly', f'seg_{n}', [0, cy, zc], [centered(0, cy - h * 0.5 + h * 0.225, pz, w, h * 0.45, plate_l, 'belly')]))
    return bones

def queen_pose(size):
    """What TermiteQueenModel does to the bones at this size (no pulse): bone name -> (posY, posZ, scale xyz)."""
    length = 1 + (GROW_LENGTH - 1) * size
    width = 1 + (GROW_WIDTH - 1) * size
    height = 1 + (GROW_HEIGHT - 1) * size
    bottom = QUEEN_CENTER_Y - QUEEN_HEIGHT / 2 - SINK * size
    rise = bottom + QUEEN_HEIGHT * height / 2 - QUEEN_CENTER_Y
    t = min(1.0, max(0.0, (size - SETTLE_FROM) / (SETTLE_TO - SETTLE_FROM)))
    settle = t * t * (3 - 2 * t)
    lift = settle * max(0.0, bottom + QUEEN_HEIGHT * height * THORAX_HOLD - THORAX_Y)
    # The whole body slides forward by half of what the abdomen grew, so she stays centred on her spot; settled, it
    # is lifted onto the front of the abdomen (and the abdomen lowered by as much, so it stays on the floor).
    pose = {'body': (lift, -QUEEN_SEG_LENGTH * len(QUEEN_TAPER) * (length - 1) / 2, (1, 1, 1))}
    d = min(1.0, max(0.0, (size - DROOP_FROM) / (DROOP_TO - DROOP_FROM)))
    pose['forebody'] = (0, 0, (1, 1, 1), -DROOP_DEGREES * d * d * (3 - 2 * d))
    for i, t in enumerate(QUEEN_TAPER):
        n = i + 1
        plate_lift = QUEEN_HEIGHT * t * (height - 1) / 2
        pose[f'seg_{n}'] = (rise - lift, QUEEN_SEG_LENGTH * (length - 1) * (i + 0.5), (1, 1, 1))
        pose[f'seg_{n}_skin'] = (0, 0, (width, height, length))
        pose[f'seg_{n}_plate'] = (plate_lift, 0, (1, 1, 1))
        pose[f'seg_{n}_belly'] = (-plate_lift, 0, (1, 1, 1))
    return pose

WORKER_SCALE = 0.85
WORKER_COLOURS = {'head': 'w_head', 'thorax': 'w_body', 'back': 'w_body', 'belly': 'w_belly', 'leg': 'w_leg',
                  'antenna': 'w_antenna', 'jaw': 'w_jaw', 'joint': 'w_body'}
WORKER_ABDOMEN_COLOURS = {'back': 'w_gut', 'belly': 'w_belly', 'joint': 'w_body'}

def worker_model():
    """The worker: the king's bones and boxes, a little smaller, pale, blind (no eyes) and wingless."""
    name_of = {(c * 8 + 1, r * 8 + 1): name for name, (c, r, _) in SWATCH.items()}
    bones = []
    for b in front_half() + king_abdomen():
        if b['name'] == 'wing_stubs':
            continue
        colours = WORKER_ABDOMEN_COLOURS if b['name'].startswith('abdomen') else WORKER_COLOURS
        nb = {k: v for k, v in b.items() if k != 'cubes'}
        nb['pivot'] = [round(v * WORKER_SCALE, 3) for v in b['pivot']]
        cubes = []
        for c in b.get('cubes', []):
            swatch = name_of[tuple(c['uv']['north']['uv'])]
            if swatch == 'eye':
                continue
            nc = cube([v * WORKER_SCALE for v in c['origin']], [v * WORKER_SCALE for v in c['size']], colours.get(swatch, swatch),
                      [v * WORKER_SCALE for v in c['pivot']] if 'pivot' in c else None, c['rotation'][1] if 'rotation' in c else 0.0)
            cubes.append(nc)
        if cubes:
            nb['cubes'] = cubes
        bones.append(nb)
    return bones

# --- the soldiers: the worker's pale body with a big armoured head and long crossing jaws ---------------------
# Minor: about a worker's size, with a bigger head. Major: about twice a worker's length, with a huge head. Both are
# blind like the worker. Bones: the worker's (the king's) names, so the walk and idle animations work; the head, jaws,
# antennae and the carry spot are the soldier's own. The whole model is moved back along its length so its middle
# sits over the middle of the hitbox (the long head would otherwise stick far out in front of it).
MINOR_SCALE, MAJOR_SCALE = 0.85, 1.2
SOLDIER_COLOURS = {'thorax': 's_body', 'back': 's_body', 'belly': 's_belly', 'leg': 's_leg', 'antenna': 's_antenna', 'joint': 's_belly'}
SOLDIER_ABDOMEN_COLOURS = {'back': 's_body', 'belly': 's_belly', 'joint': 's_belly'}
HEAD_BONES = ('head', 'mandible_l', 'mandible_r', 'carry', 'antenna_l_1', 'antenna_l_2', 'antenna_r_1', 'antenna_r_2')
NECK_Z = -2.3  # where the head meets the thorax (the king's head pivot)

# The soldiers' legs (B11, 2026-10-18, the user's decision): the big armoured head is heavy, and with the king's legs
# its weight sat in front of the feet (the walk's centre of weight outside the feet on the ground 23% of the cycle for
# the minor, 75% for the major). Real soldiers' front legs reach forward along the sides of the head; here the front
# legs join at the front of the thorax, turn further forward and are longer, and the middle legs turn a little forward,
# until the motion library's balance check keeps the centre over the feet in every walk. The major's front legs are
# the longest and join at the very front, a little lower (1.2), turned 45 degrees forward: of 140 layouts tried, the
# only one whose front feet reach the whole step with the thighs clear of the head (my picks, to confirm).
MINOR_LEGS = ((1, -2.1, 55.0, 1.6), (2, -1.0, 10.0, 1.4), (3, 0.1, -45.0, 1.35))
MAJOR_LEGS = ((1, -2.3, 45.0, 3.2, 1.2), (2, -1.0, 25.0, 1.6), (3, 0.1, -45.0, 1.35))

def soldier_head(major):
    """The soldier's head, jaws, antennae and carry spot, in king units: a big rounded capsule, shiny orange-brown,
    darker at the front where the jaws join; the jaws run straight forward and then curve in, crossing at the tips
    (real Macrotermes soldiers' jaws cross). The major's head is bigger for its body and its jaws longer."""
    w, h, l = (3.3, 2.5, 3.9) if major else (2.7, 2.05, 3.1)
    jaw, tip = (1.9, 1.35) if major else (1.5, 1.1)
    y0 = 0.8 if major else 0.95
    front = NECK_Z - l
    bones = [bone('head', 'forebody', [0, y0 + h / 2, NECK_Z], [
        cube([-w / 2, y0, front + 0.5], [w, h, l - 0.5], 's_head'),
        cube([-w / 2 - 0.12, y0 + 0.3, front + 0.8], [w + 0.24, h - 0.6, l - 1.2], 's_head'),   # rounded sides
        cube([-w / 2 + 0.4, y0 + h, front + 0.9], [w - 0.8, 0.15, l - 1.5], 's_shine'),        # shiny top
        cube([-w / 2 + 0.3, y0 + 0.15, front], [w - 0.6, h * 0.55, 0.5], 's_front'),           # front, where the jaws join
        cube([-0.45, y0 + 0.45, front - 0.35], [0.9, 0.35, 0.35], 's_front'),                  # upper lip
    ])]
    for side, sx in (('l', 1), ('r', -1)):
        x = 0.55 * sx
        y = y0 + 0.32 + (0.0 if side == 'l' else 0.04)  # a hair apart where they cross
        base = cube([x - 0.25, y, front - jaw], [0.5, 0.36, jaw], 's_jaw')
        tip_pivot = [x, y + 0.18, front - jaw]
        tip_cube = cube([x - 0.17, y + 0.02, front - jaw - tip], [0.34, 0.3, tip], 's_jaw', tip_pivot, 40.0 * sx)
        bones.append(bone(f'mandible_{side}', 'head', [x, y + 0.18, front], [base, tip_cube]))
    bones.append(bone('carry', 'head', [0, y0 + 0.5, front - jaw]))
    # Beaded antennae from the sides of the head's front, held forward and out, the tips curving down.
    for side, sx in (('l', 1), ('r', -1)):
        start = ((w / 2 - 0.25) * sx, y0 + h * 0.62, front + 0.35)
        beads1, p = [], list(start)
        for i in range(3):
            size = 0.4 if i % 2 == 0 else 0.32
            beads1.append(centered(p[0], p[1], p[2], size, size, size, 's_antenna'))
            p = [p[0] + 0.2 * sx, p[1] + 0.12, p[2] - 0.3]
        mid = list(p)
        beads2 = []
        for i in range(6):
            size = 0.34 if i % 2 == 0 else 0.27
            beads2.append(centered(p[0], p[1], p[2], size, size, size, 's_antenna'))
            drop = 0.02 if i < 2 else -0.14
            p = [p[0] + 0.22 * sx, p[1] + drop, p[2] - 0.26]
        bones.append(bone(f'antenna_{side}_1', 'head', list(start), beads1))
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', mid, beads2))
    return bones

def soldier_model(major, legs=None):
    """A soldier: the king's thorax, legs and abdomen made pale (no eyes, no wing stubs), the soldier's own head, all
    scaled to its size and moved back so the middle of its length sits over the hitbox's middle."""
    name_of = {(c * 8 + 1, r * 8 + 1): name for name, (c, r, _) in SWATCH.items()}
    scale = MAJOR_SCALE if major else MINOR_SCALE
    legs = legs or (MAJOR_LEGS if major else MINOR_LEGS)
    raw = [b for b in front_half(legs) + king_abdomen() if b['name'] not in HEAD_BONES and b['name'] != 'wing_stubs']
    head = soldier_head(major)
    # The length: from the jaw tips to the end of the abdomen (the jaw tips are turned in, so their corners count).
    zs = []
    for corners, _ in model_boxes([b for b in raw if b['name'] in ('body', 'forebody')] + head):
        zs += [p[2] for p in corners]
    zs += [c['origin'][2] + c['size'][2] for b in raw for c in b.get('cubes', [])]
    shift = -(min(zs) + max(zs)) / 2
    def moved(v):
        return [v[0] * scale, v[1] * scale, (v[2] + shift) * scale]
    bones = []
    for b in raw + head:
        own = b['name'] in HEAD_BONES
        colours = {} if own else SOLDIER_ABDOMEN_COLOURS if b['name'].startswith('abdomen') else SOLDIER_COLOURS
        nb = {k: v for k, v in b.items() if k != 'cubes'}
        nb['pivot'] = [round(v, 3) for v in moved(b['pivot'])]
        cubes = []
        for c in b.get('cubes', []):
            swatch = name_of[tuple(c['uv']['north']['uv'])]
            if swatch == 'eye':
                continue
            cubes.append(cube(moved(c['origin']), [v * scale for v in c['size']], colours.get(swatch, swatch),
                              moved(c['pivot']) if 'pivot' in c else None, c['rotation'][1] if 'rotation' in c else 0.0))
        if cubes:
            nb['cubes'] = cubes
        bones.append(nb)
    # In the king's bone order (every parent before its children).
    order = [b['name'] for b in front_half() + king_abdomen()]
    bones.sort(key=lambda b: order.index(b['name']))
    return bones

# The head bang (drumming): a soldier warns the colony by striking the ground with its head in quick bursts (real
# Macrotermes soldiers about 11 times a second; others feel it through the walls and drum too). One burst of
# DRUM_STRIKES strikes, DRUM_GAP seconds apart, then a short rest; it loops while the soldier drums. The head tips
# down hard (positive X: the face toward the belly), the forebody jerks with it, the jaws gape, the antennae are
# pulled back.
DRUM_SECONDS, DRUM_STRIKES, DRUM_GAP, DRUM_TIP = 1.0, 6, 0.1, 10.0

def head_bang():
    head, fore = {}, {}
    for k in range(DRUM_STRIKES):
        t = k * DRUM_GAP
        head[f'{t:g}'] = [0, 0, 0]
        head[f'{t + 0.04:g}'] = [DRUM_TIP, 0, 0]
        fore[f'{t:g}'] = [0, 0, 0]
        fore[f'{t + 0.04:g}'] = [3, 0, 0]
    end = DRUM_STRIKES * DRUM_GAP
    for d in (head, fore):
        d[f'{end:g}'] = [0, 0, 0]
        d[f'{DRUM_SECONDS:g}'] = [0, 0, 0]
    def keys(open_):
        # Open for the burst, shut again for the rest.
        return {'rotation': {'0': [0, 0, 0], '0.08': open_, f'{end - 0.05:g}': open_, f'{end + 0.1:g}': [0, 0, 0],
                             f'{DRUM_SECONDS:g}': [0, 0, 0]}}
    return {'head': {'rotation': head}, 'forebody': {'rotation': fore},
            'mandible_l': keys([0, -14, 0]), 'mandible_r': keys([0, 14, 0]),
            'antenna_l_1': keys([-25, -10, 0]), 'antenna_r_1': keys([-25, 10, 0])}

# The bite (step 6c, a simple first version to be refined later): the head pulls back a little while the jaws open
# wide, then lunges forward and down as the jaws snap shut past each other, and settles. Played once per bite.
BITE_SECONDS = 0.35

def bite():
    def keys(back, lunge):
        return {'rotation': {'0': [0, 0, 0], '0.12': back, '0.2': lunge, f'{BITE_SECONDS:g}': [0, 0, 0]}}
    return {'head': keys([-8, 0, 0], [8, 0, 0]), 'forebody': keys([-2, 0, 0], [3, 0, 0]),
            'mandible_l': keys([0, -30, 0], [0, 6, 0]), 'mandible_r': keys([0, 30, 0], [0, -6, 0])}

# Clamped on (M8 step 2c; real Macrotermes soldiers bite a raider's leg or antenna and hang on, dragged along, Frank
# et al. 2017): the jaws locked shut past each other on the leg, the head tipped up to it and held, the antennae pulled
# back; dragged sideways, the body jerks and rolls a little and the abdomen swings behind; the legs scrabble for a hold
# by turns, each one swinging and pawing and planted again, out of step (a dragged insect bracing, not walking). One
# loop of CLAMP_SECONDS; the leg moves (degrees) and their moments my picks, to confirm.
CLAMP_SECONDS = 1.6
CLAMP_SCRABBLE = {'l1': (0.1, 8), 'r3': (0.35, -14), 'r1': (0.55, -8), 'l2': (0.8, 16), 'l3': (1.05, 12), 'r2': (1.3, -16)}

def clamp_hold():
    T = f'{CLAMP_SECONDS:g}'
    jerk = lambda a, b: {'0': [0, 0, 0], '0.4': [0, a, b], '0.5': [0, -a * 0.5, -b], '1.0': [0, a * 0.6, b * 0.5],
                         '1.2': [0, -a, -b * 0.6], T: [0, 0, 0]}
    bones = {'body': {'rotation': jerk(3, 3)},
             'head': {'rotation': {'0': [-10, 0, 0], '0.45': [-13, 3, 0], '0.55': [-9, -2, 0], '1.15': [-12, -3, 0], T: [-10, 0, 0]}},
             'forebody': {'rotation': {'0': [-3, 0, 0], T: [-3, 0, 0]}},
             'mandible_l': {'rotation': {'0': [0, 10, 0], '0.6': [0, 12, 0], '0.7': [0, 10, 0], T: [0, 10, 0]}},
             'mandible_r': {'rotation': {'0': [0, -10, 0], '0.6': [0, -12, 0], '0.7': [0, -10, 0], T: [0, -10, 0]}},
             'antenna_l_1': {'rotation': {'0': [-25, -15, 0], '0.7': [-15, -10, 0], '0.8': [-28, -18, 0], T: [-25, -15, 0]}},
             'antenna_r_1': {'rotation': {'0': [-25, 15, 0], '1.1': [-18, 10, 0], '1.2': [-28, 18, 0], T: [-25, 15, 0]}}}
    n = len(KING_SEGMENTS)
    for i in range(n):
        bone = abdomen_bone(i)
        swing = 1.5                     # each segment on the one before: the whole abdomen swings in a curve
        bones[bone] = {'rotation': {'0': [0, 0, 0], '0.5': [0, swing, 0], '1.2': [0, -swing, 0], T: [0, 0, 0]}}
    for leg, (at, swing) in CLAMP_SCRABBLE.items():
        side = 1 if leg[0] == 'l' else -1
        a, b, c = f'{at:g}', f'{at + 0.08:g}', f'{at + 0.2:g}'
        bones[f'leg_{leg}'] = {'rotation': {'0': [0, 0, 0], a: [0, 0, 0], b: [0, swing, 12 * side], c: [0, swing * 0.4, 0],
                                            f'{at + 0.32:g}': [0, 0, 0], T: [0, 0, 0]}}
        bones[f'leg_{leg}_lower'] = {'rotation': {'0': [0, 0, 0], a: [0, 0, 0], b: [0, 0, -15 * side], c: [0, 0, 0], T: [0, 0, 0]}}
    return bones

def soldier_animations(name, bones):
    """A soldier's clip file: its own walks and turns, the worker's idle (a slow jaw twitch added), the head bang and the
    bite."""
    clips = animations(name, bones)['animations']
    idle = dict(clips['idle']['bones'])
    idle['mandible_l'] = {'rotation': {'0': [0, 0, 0], '1.6': [0, 0, 0], '1.8': [0, -8, 0], '2.0': [0, 0, 0], '2.4': [0, 0, 0]}}
    idle['mandible_r'] = {'rotation': {'0': [0, 0, 0], '1.6': [0, 0, 0], '1.8': [0, 8, 0], '2.0': [0, 0, 0], '2.4': [0, 0, 0]}}
    out = {'idle': {'loop': True, 'animation_length': 2.4, 'bones': idle}}
    out.update({k: v for k, v in clips.items() if k.startswith(('walk', 'turn'))})
    out['head_bang'] = {'loop': True, 'animation_length': DRUM_SECONDS, 'bones': head_bang()}
    out['bite'] = {'loop': False, 'animation_length': BITE_SECONDS, 'bones': bite()}
    out['clamp_hold'] = {'loop': True, 'animation_length': CLAMP_SECONDS, 'bones': clamp_hold()}
    out['paralysed_fall'] = clips['paralysed_fall']
    out['paralysed'] = clips['paralysed']
    return {'format_version': '1.8.0', 'animations': out}

def render_head_bang(bones, path, frames=6):
    """The head bang from the side at a few moments of the burst (strike, rest), so the motion can be checked."""
    anim = head_bang()
    cw, ch = 360, 240
    canvas = Canvas(cw * frames, TITLE_H + ch + LABEL_H)
    canvas.text(12, 12, 'HEAD BANG - SIDE, ONE BURST', scale=4)
    for k in range(frames):
        t = [0.0, 0.04, 0.07, 0.14, 0.3, 0.8][k]
        x0 = cw * k
        canvas.frame(x0, TITLE_H, cw, ch + LABEL_H)
        canvas.text(x0 + 10, TITLE_H + 8, f'{int(t * 1000)} MS')
        draw(canvas, model_boxes(bones, anim=sample(anim, t)), (x0 + 1, TITLE_H + LABEL_H, cw - 2, ch - 1), 0.8,
             math.radians(-90), 0.0, 14.0)
    canvas.save(path)

def make_nymph_texture(path):
    pixels = []
    for y in range(32):
        rgb = NYMPH_BANDS[y // 8]
        pixels += [rgb + (255,)] * 32
    write_png(path, 32, 32, pixels)

def geo(identifier, bones):
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': identifier, 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 4, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]},
        'bones': bones}]}

# The walks (B11, planned with the user 2026-10-18, docs/plans/termite-walks.md): made by the motion library
# (tools/ant_motion.py) from each model's body plan, as the ant's are, so a foot on the ground stays put while the body
# passes over it (the old hand-set walk's feet slid 3.4 to 3.8 times the walk speed). No one has measured a termite's
# leg pattern yet; termites are social cockroaches, and cockroaches walk a tripod at all but the slowest speeds (a
# looser, wave-like stepping when slow: Pearson and Iles 1973, Delcomyn 1971), so the termites get the ant's three
# walks: walk_slow (the loose tripod), walk and walk_fast, picked by speed in body lengths a second like the ant's.
# Each model gets its own clip file, so each stride note fits its own size. My picks, to confirm:
#   sweep (units, slow / walk / fast): as long as each model's legs reach with the feet planted exactly;
#   duty factors the ant's (0.68 / 0.62 / 0.55, the middle legs 0.72 / 0.67 in the slower two);
#   the small queen's middle and hind feet 0.3 and 0.4 units further back and her steps shorter, so her bigger abdomen
#   stays over her feet (she walks only while young; her front half is the king's);
#   the major soldier's front feet 0.6 units further back (planted further forward, its long front thighs rise into
#   the big head as the foot reaches ahead);
#   a foot lifts half its hip's height (any lower and it is still a hair off the ground as it starts to swing out,
#   which the lab sees as a slide); the forebody sways 1.5 degrees and the abdomen 2 against it (a termite's waist is
#   broad, so less than an ant's 2 and 3); the body bobs 0.04 units; key frames 120 / 60 / 40 a cycle, and 80 for
#   the turns (fewer, and a foot lifting off falls between two of them and seems to skid).
WALK_SECONDS = 0.5
WALK_BANDS = {'walk_slow': (0.0, 1.6), 'walk': (1.6, 3.7), 'walk_fast': (3.7, 999.0)}
GAITS = {'walk_slow': dict(duty=0.68, middle_duty=0.72, slow=True, keys=120),
         'walk': dict(duty=0.62, middle_duty=0.67, keys=60),
         'walk_fast': dict(duty=0.55, keys=40)}
SWEEPS = {'termite_worker': (1.4, 1.6, 1.8), 'termite_king': (1.6, 1.9, 2.2), 'termite_queen': (1.2, 1.4, 1.6),
          'termite_minor_soldier': (1.4, 1.6, 1.6), 'termite_major_soldier': (1.8, 2.0, 2.2)}
SHIFTS = {'termite_queen': {2: 0.3, 3: 0.4}, 'termite_major_soldier': {1: 0.6}}
LIFT_SHARE, BOB, SWAY, ABDOMEN_SWAY = 0.5, 0.04, 1.5, 2.0
# Turning on the spot (as the ant's): the legs keep the tripod and step round, 40 degrees a cycle, the front feet 0.4
# units wider and the others 0.2 (so they clear the head as they swing in under it), the forebody leading 2 degrees.
TURN_DEGREES = 40.0
TURN = dict(duty={1: 0.4, 2: 0.6, 3: 0.6}, wide={1: 0.4, 2: 0.2, 3: 0.2}, lead=2.0, keys=80)
# A model's own turn where the usual one does not fit: the major soldier's long front legs step round 0.8 units wider
# and stay down half the cycle (the others 0.3 wider), as the ant's do; with the usual turn its front thighs swung
# into its head and only 2 feet were down at a time.
TURNS = {'termite_major_soldier': dict(duty={1: 0.5, 2: 0.6, 3: 0.6}, wide={1: 0.8, 2: 0.3, 3: 0.3})}

# The antennae while walking (the user's decision, 2026-10-18): real termites are blind and keep touching the floor
# and the tunnel walls ahead with their antennae, and those touches steer them. Held low and forward, each antenna
# taps the ground ahead once a cycle, the two by turns (the head's player plays it at the insect's own pace: about 2
# taps a second each). Down, the tip rests TAP_REST units over the ground for a moment (it does not skid), held a
# little in toward the middle; up, it lifts TAP_LIFT degrees and swings out. My picks, to confirm.
TAP_LIFT, TAP_IN, TAP_OUT, TAP_WHIP, TAP_REST = 16.0, 6.0, 12.0, 10.0, 0.1


def rig_of(name, bones):
    """A model ready for the motion library (imported here: anim_lab imports this file)."""
    import ant_motion as mo
    import anim_lab as lab
    return mo.Rig(lab.Model(name, bones=bones))


def tap_angle(rig, side):
    """The antenna's base turn (X, degrees, positive down) at which its tip rests TAP_REST over the ground, the base
    turned TAP_IN toward the middle and the whip bent TAP_WHIP down."""
    import ant_motion as mo
    model = rig.model
    sy = 1 if side == 'l' else -1
    base, whip = f'antenna_{side}_1', f'antenna_{side}_2'

    def low(x):
        pose = mo.pose_of({base: [x, sy * TAP_IN, 0], whip: [TAP_WHIP, 0, 0]})
        return min(model.world(bn, p, pose)[1] for bn, corners, _ in model.cubes if bn == whip for p in corners)
    lo, hi = -30.0, 80.0
    for _ in range(50):
        mid = (lo + hi) / 2
        if low(mid) > rig.ground + TAP_REST:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def tapping_antennae(track, rig, seconds):
    """The walking antennae: each taps the ground ahead once a cycle, by turns (see TAP_LIFT)."""
    n = 40
    for side, phase, sy in (('l', 0.0, 1), ('r', 0.5, -1)):
        touch = tap_angle(rig, side)
        for i in range(n + 1):
            u = (i / n - phase) % 1.0
            d = min(abs(u - 0.35), 1 - abs(u - 0.35))          # how far from the moment of the tap
            if d < 0.05:
                down = 1.0
            elif d < 0.3:
                down = 0.5 + 0.5 * math.cos(math.pi * (d - 0.05) / 0.25)
            else:
                down = 0.0
            up = 1.0 - down
            t = seconds * i / n
            track.put(f'antenna_{side}_1', t, [touch - TAP_LIFT * up, sy * (TAP_IN + (TAP_OUT - TAP_IN) * up), 0])
            track.put(f'antenna_{side}_2', t, [TAP_WHIP - 6.0 * up, 0, 0])


def walk_clips(name, bones):
    """The walks and the turns of one model, with their notes (stride, swap, speeds; turn, swap, reach), in role names."""
    import ant_motion as mo
    rig = rig_of(name, bones)
    lift = LIFT_SHARE * rig.legs['l2'].H[1]
    clips = {}
    for gear, sweep in zip(('walk_slow', 'walk', 'walk_fast'), SWEEPS[name]):
        g = dict(GAITS[gear])
        phases = mo.LOOSE_TRIPOD if g.pop('slow', False) else mo.TRIPOD
        tr, stride, report = mo.walk(rig, WALK_SECONDS, sweep=sweep, lift=lift, phases=phases, bob=BOB, sway=SWAY,
                                     gaster_sway=ABDOMEN_SWAY, shift=SHIFTS.get(name), **g)
        tapping_antennae(tr, rig, WALK_SECONDS)
        band = WALK_BANDS[gear]
        body = rig.model.length
        clips[gear] = {'loop': True, 'animation_length': WALK_SECONDS,
                       'timeline': {'0': f'stride {stride:.4f}', '0.001': f'swap {report["swap"]:.4f}',
                                    '0.002': f'speeds {band[0] * body:.2f} {band[1] * body:.2f}'},
                       'bones': tr.bones()}
    for clip, degrees in (('turn_r', TURN_DEGREES), ('turn_l', -TURN_DEGREES)):
        tr, report = mo.turn(rig, WALK_SECONDS, degrees, lift=lift, shift=SHIFTS.get(name), **dict(TURN, **TURNS.get(name, {})))
        tapping_antennae(tr, rig, WALK_SECONDS)
        clips[clip] = {'loop': True, 'animation_length': WALK_SECONDS,
                       'timeline': {'0': f'turn {degrees:g}', '0.001': f'swap {report["swap"]:.4f}',
                                    '0.002': f'reach {report["reach"]:.2f}'},
                       'bones': tr.bones()}
    return clips

# The look round out of a chimney's mouth (AntNavigator holds the body still meanwhile, DoorwayMotion.PEEK_TICKS long).
# The body stands upright in the mouth, so the head first tips forward until it faces straight out, level (the user's
# wish), then turns to its left, stops, turns to its right, stops, and tips back up; the antennae follow the look and
# feel the air; the mandibles open and close at each stop. Key frames ease in and out (GeckoLib's easing), so the head
# moves like a head and not like a dial. In Blockbench terms a positive X tips the face down toward the belly (with
# the body upright on the spire's wall: forward, level, out over the rim), and with the head tipped like that a turn
# round the body's long axis (Z) swings the face left and right.
PEEK_SECONDS, PEEK_TURN, PEEK_TIP = 2.8, 50.0, 85.0

def peek():
    def keys(frames):
        return {'rotation': {f'{t:g}': {'vector': v, 'easing': e} for t, v, e in frames}}
    io = 'easeInOutSine'
    L, R = -PEEK_TURN, PEEK_TURN
    T = PEEK_TIP
    head = keys([(0, [0, 0, 0], io), (0.45, [T, 0, 0], io), (0.95, [T, 0, L], io),
                 (1.3, [T, 0, L], io), (1.95, [T, 0, R], io), (2.3, [T, 0, R], io),
                 (PEEK_SECONDS, [0, 0, 0], io)])
    def antenna(side):
        # Both antennae swing the way the head looks, and wave up and down (out of step with each other).
        wave = 12.0 if side == 'l' else -12.0
        return keys([(0, [0, 0, 0], io), (0.45, [wave, 0, 0], io), (0.7, [-wave, L * 0.4, 0], io),
                     (0.95, [wave, L * 0.5, 0], io), (1.15, [-wave * 0.6, L * 0.5, 0], io), (1.3, [wave, L * 0.4, 0], io),
                     (1.6, [0, 0, 0], io), (1.95, [wave, R * 0.5, 0], io), (2.15, [-wave * 0.6, R * 0.5, 0], io),
                     (2.3, [wave, R * 0.4, 0], io), (PEEK_SECONDS, [0, 0, 0], io)])
    def mandible(side):
        # Open (tips apart) and shut once at each stop: tasting the air.
        open_ = -15.0 if side == 'l' else 15.0
        return keys([(0, [0, 0, 0], io), (1.0, [0, 0, 0], io), (1.1, [0, open_, 0], io), (1.2, [0, 0, 0], io),
                     (2.0, [0, 0, 0], io), (2.1, [0, open_, 0], io), (2.2, [0, 0, 0], io), (PEEK_SECONDS, [0, 0, 0], io)])
    return {'head': head, 'antenna_l_1': antenna('l'), 'antenna_r_1': antenna('r'),
            'mandible_l': mandible('l'), 'mandible_r': mandible('r')}

# Freezing at a shake of the ground (the animation engine, 2026-10-16; my pick, to confirm). Real Macrotermes workers
# are blind and feel the ground: a footstep nearby makes one stop dead, the head up a little and the antennae raised
# and quivering (about 6 times a second, out of step with each other), for a second or two. It never turns to look.
FREEZE_SECONDS = 1.0

def freeze():
    def quiver(phase):
        n = 24
        return {'rotation': {f'{FREEZE_SECONDS * i / n:g}': [round(-22 + 3.0 * math.sin(2 * math.pi * (6 * i / n + phase)), 2), 0, 0]
                             for i in range(n + 1)}}
    return {'antenna_l_1': quiver(0.0), 'antenna_r_1': quiver(0.5), 'head': {'rotation': {'0': [-6, 0, 0]}}}

# Lying paralysed by a Matabele's sting (M8 step 2b; my pick, to confirm). A stung termite falls over onto its back
# and lies there alive, its legs drawn up toward the belly, now and then one leg or an antenna jerking, the jaws
# slowly working: the look of the paralysed termites in the Matabele's prey pile (look B, the user's pick, 2026-10-24,
# many on their backs with legs in the air). The whole body turns over on its long axis round its pivot (which sits at
# the floor in the game's reckoning, so the body turns below the floor); LIFT puts its back on the floor, measured
# with the animation lab (python tools/anim_lab.py check <model> paralysed: nothing sinking into the floor).
PARALYSED_SECONDS = 3.0
PARALYSED_LIFT = {'termite_minor_soldier': 0.18, 'termite_major_soldier': 0.64}
# When each leg jerks (seconds into the loop) and how far (degrees, the leg swinging forward or back).
PARALYSED_JERKS = {'l1': (0.4, -18), 'r2': (0.9, -16), 'l3': (1.5, 14), 'r1': (1.9, 20), 'l2': (2.3, 15), 'r3': (2.65, -12)}

def paralysed(name):
    lift = PARALYSED_LIFT.get(name, 0.0)
    rock = lambda a: {'0': [0, 0, 180], '1.5': [0, 0, 180 + a], f'{PARALYSED_SECONDS:g}': [0, 0, 180]}
    bones = {'body': {'rotation': rock(4), 'position': {'0': [0, lift, 0]}}}
    for leg, (at, swing) in PARALYSED_JERKS.items():
        side = 1 if leg[0] == 'l' else -1
        curl = [0, 0, 40 * side]
        bones[f'leg_{leg}'] = {'rotation': {'0': [0, 0, 0], f'{at:g}': [0, 0, 0], f'{at + 0.06:g}': [0, swing, 0],
                                            f'{at + 0.2:g}': [0, 0, 0], f'{PARALYSED_SECONDS:g}': [0, 0, 0]}}
        bones[f'leg_{leg}_lower'] = {'rotation': {'0': curl}}
    def flick(at, a):
        return {'rotation': {'0': [10, 0, 0], f'{at:g}': [10, 0, 0], f'{at + 0.05:g}': [10 + a, 0, 0],
                             f'{at + 0.18:g}': [10, 0, 0], f'{PARALYSED_SECONDS:g}': [10, 0, 0]}}
    bones['antenna_l_1'] = flick(1.1, 25)
    bones['antenna_r_1'] = flick(2.5, 25)
    work = lambda o: {'rotation': {'0': [0, 0, 0], '0.8': [0, o, 0], '1.6': [0, 0, 0], f'{PARALYSED_SECONDS:g}': [0, 0, 0]}}
    bones['mandible_l'] = work(-8)
    bones['mandible_r'] = work(8)
    return bones

# Falling over as the venom takes hold (M8 fight polish, round A; my pick, to confirm): the stung termite has staggered
# a few seconds on its feet (the game slows it); now its legs give way, it rolls over onto its side and on onto its
# back, the legs pawing the air and drawing up, ending in the first pose of the paralysed loop, which then plays on.
# The roll is about the body's long axis at the floor, so mid-roll the body is lifted (FALL_LIFT, model units, measured
# with the animation lab so nothing sinks into the floor).
FALL_SECONDS = 0.6
FALL_LIFT = {'termite_worker': 2.0, 'termite_king': 3.0, 'termite_queen': 3.0, 'termite_minor_soldier': 2.5,
             'termite_major_soldier': 3.5}

def paralysed_fall(name):
    lift = PARALYSED_LIFT.get(name, 0.0)
    mid = FALL_LIFT.get(name, 2.0)
    end = f'{FALL_SECONDS:g}'
    bones = {'body': {'rotation': {'0': [0, 0, 0], '0.12': [0, 0, -8], '0.3': [0, 0, 95], end: [0, 0, 180]},
                      'position': {'0': [0, 0, 0], '0.12': [0, 0, 0], '0.3': [0, mid, 0], '0.45': [0, mid * 0.5, 0],
                                   end: [0, lift, 0]}}}
    for i, leg in enumerate(('l1', 'r2', 'l3', 'r1', 'l2', 'r3')):
        side = 1 if leg[0] == 'l' else -1
        paw = 22 if i % 2 == 0 else -22
        # pawing the air: up and down, not fore and aft (a front leg swung forward would go through a soldier's head)
        kick = abs(paw) * side
        bones[f'leg_{leg}'] = {'rotation': {'0': [0, 0, 0], '0.2': [0, 0, kick], '0.35': [0, 0, -kick * 0.5], '0.5': [0, 0, kick * 0.5],
                                            end: [0, 0, 0]}}
        bones[f'leg_{leg}_lower'] = {'rotation': {'0': [0, 0, 0], '0.3': [0, 0, 15 * side], end: [0, 0, 40 * side]}}
    bones['antenna_l_1'] = {'rotation': {'0': [0, 0, 0], '0.25': [30, 0, 0], end: [10, 0, 0]}}
    bones['antenna_r_1'] = {'rotation': {'0': [0, 0, 0], '0.3': [25, 0, 0], end: [10, 0, 0]}}
    bones['mandible_l'] = {'rotation': {'0': [0, 0, 0], '0.2': [0, -10, 0], end: [0, 0, 0]}}
    bones['mandible_r'] = {'rotation': {'0': [0, 0, 0], '0.2': [0, 10, 0], end: [0, 0, 0]}}
    return bones

def animations(name='termite_king', bones=None):
    """A termite's clip file (in role names): idle, the walks and turns made for its own body (walk_clips, when its
    bones are given), the look round out of a chimney and the freeze."""
    wiggle = lambda a: {'0': [0, 0, 0], '0.6': [a, 0, 0], '1.2': [0, 0, 0], '1.8': [-a, 0, 0], '2.4': [0, 0, 0]}
    idle = {'antenna_l_1': {'rotation': wiggle(10)}, 'antenna_r_1': {'rotation': wiggle(-10)},
            'head': {'rotation': {'0': [0, 0, 0], '1.2': [0, 5, 0], '2.4': [0, 0, 0]}}}
    clips = {'idle': {'loop': True, 'animation_length': 2.4, 'bones': idle}}
    if bones is not None:
        clips.update(walk_clips(name, bones))
    clips['peek'] = {'loop': False, 'animation_length': PEEK_SECONDS, 'bones': peek()}
    clips['freeze'] = {'loop': True, 'animation_length': FREEZE_SECONDS, 'bones': freeze()}
    clips['paralysed_fall'] = {'loop': False, 'animation_length': FALL_SECONDS, 'bones': paralysed_fall(name)}
    clips['paralysed'] = {'loop': True, 'animation_length': PARALYSED_SECONDS, 'bones': paralysed(name)}
    return {'format_version': '1.8.0', 'animations': clips}

def sample(bones_anim, t):
    """The rotation of every bone of an animation at time t (straight lines between the key frames)."""
    out = {}
    for name, channels in bones_anim.items():
        frames = sorted((float(k), v['vector'] if isinstance(v, dict) else v) for k, v in channels.get('rotation', {}).items())
        for (t0, r0), (t1, r1) in zip(frames, frames[1:]):
            if t0 <= t <= t1:
                f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                out[name] = [a + (b - a) * f for a, b in zip(r0, r1)]
                break
    return out

def dump(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1)

# --- preview: a small software renderer (boxes, depth buffer, flat light) --------------------------------------
def model_boxes(bones, pose=None, anim=None):
    """Every cube as (8 corners, swatch colour), with cube turns, the queen's bone pose and an animation frame
    (bone -> [x, y, z] degrees, as in the animation file) applied."""
    pose = pose or {}
    anim = anim or {}
    by_name = {b['name']: b for b in bones}
    swatch_of = {(c * 8 + 1, r * 8 + 1): rgb for c, r, rgb in SWATCH.values()}

    def bone_transform(name, p):
        # Innermost bone first, then its parents: scale about the pivot, then move.
        while name:
            b = by_name[name]
            if name in pose or name in anim:
                dy, dz, (sx, sy, sz) = pose[name][:3] if name in pose else (0, 0, (1, 1, 1))
                # Tilt about X as GeckoLib's setRotX (+Y turns toward +Z; a negative tilt puts the head down); an
                # animation's X is the other way round, as in Blockbench.
                tilt = math.radians(pose[name][3]) if name in pose and len(pose[name]) > 3 else 0.0
                ax, ay, az = anim.get(name, (0, 0, 0))
                tilt -= math.radians(ax)
                px, py, pz = b['pivot']
                x, y, z = (p[0] - px) * sx, (p[1] - py) * sy, (p[2] - pz) * sz
                y, z = y * math.cos(tilt) - z * math.sin(tilt), y * math.sin(tilt) + z * math.cos(tilt)
                x, z = turn(x, z, ay)
                # Z as in Blockbench: a positive tilt lowers +X (a left leg's foot) and raises -X.
                c, s = math.cos(math.radians(-az)), math.sin(math.radians(-az))
                x, y = x * c - y * s, x * s + y * c
                p = (px + x, py + y + dy, pz + z + dz)
            name = b.get('parent')
        return p

    boxes = []
    for b in bones:
        for c in b.get('cubes', []):
            (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
            corners = [(ox + dx * sx, oy + dy * sy, oz + dz * sz) for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
            if 'rotation' in c:
                px, _, pz = c['pivot']
                turned = []
                for x, y, z in corners:
                    tx, tz = turn(x - px, z - pz, c['rotation'][1])
                    turned.append((px + tx, y, pz + tz))
                corners = turned
            corners = [bone_transform(b['name'], p) for p in corners]
            boxes.append((corners, swatch_of[tuple(c['uv']['north']['uv'])]))
    return boxes

FACES = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
BACKGROUND = (205, 200, 190)
LIGHT = (0.45, 0.8, -0.4)

# A tiny 5x7 pixel font for the labels.
FONT = {
    'A': ['01110', '10001', '10001', '11111', '10001', '10001', '10001'],
    'B': ['11110', '10001', '10001', '11110', '10001', '10001', '11110'],
    'C': ['01110', '10001', '10000', '10000', '10000', '10001', '01110'],
    'D': ['11110', '10001', '10001', '10001', '10001', '10001', '11110'],
    'E': ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
    'F': ['11111', '10000', '10000', '11110', '10000', '10000', '10000'],
    'G': ['01110', '10001', '10000', '10111', '10001', '10001', '01111'],
    'H': ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
    'I': ['01110', '00100', '00100', '00100', '00100', '00100', '01110'],
    'J': ['00111', '00010', '00010', '00010', '00010', '10010', '01100'],
    'K': ['10001', '10010', '10100', '11000', '10100', '10010', '10001'],
    'L': ['10000', '10000', '10000', '10000', '10000', '10000', '11111'],
    'M': ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
    'N': ['10001', '11001', '10101', '10011', '10001', '10001', '10001'],
    'O': ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
    'P': ['11110', '10001', '10001', '11110', '10000', '10000', '10000'],
    'Q': ['01110', '10001', '10001', '10001', '10101', '10010', '01101'],
    'R': ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
    'S': ['01111', '10000', '10000', '01110', '00001', '00001', '11110'],
    'T': ['11111', '00100', '00100', '00100', '00100', '00100', '00100'],
    'U': ['10001', '10001', '10001', '10001', '10001', '10001', '01110'],
    'V': ['10001', '10001', '10001', '10001', '10001', '01010', '00100'],
    'W': ['10001', '10001', '10001', '10101', '10101', '10101', '01010'],
    'Y': ['10001', '10001', '01010', '00100', '00100', '00100', '00100'],
    '0': ['01110', '10001', '10011', '10101', '11001', '10001', '01110'],
    '1': ['00100', '01100', '00100', '00100', '00100', '00100', '01110'],
    '2': ['01110', '10001', '00001', '00010', '00100', '01000', '11111'],
    '3': ['11110', '00001', '00001', '01110', '00001', '00001', '11110'],
    '4': ['00010', '00110', '01010', '10010', '11111', '00010', '00010'],
    '5': ['11111', '10000', '11110', '00001', '00001', '10001', '01110'],
    '6': ['00110', '01000', '10000', '11110', '10001', '10001', '01110'],
    '7': ['11111', '00001', '00010', '00100', '01000', '01000', '01000'],
    '8': ['01110', '10001', '10001', '01110', '10001', '10001', '01110'],
    '9': ['01110', '10001', '10001', '01111', '00001', '00010', '01100'],
    '%': ['11001', '11010', '00010', '00100', '01000', '01011', '10011'],
    '-': ['00000', '00000', '00000', '11111', '00000', '00000', '00000'],
    '(': ['00010', '00100', '01000', '01000', '01000', '00100', '00010'],
    ')': ['01000', '00100', '00010', '00010', '00010', '00100', '01000'],
    ',': ['00000', '00000', '00000', '00000', '00110', '00100', '01000'],
    ' ': ['00000'] * 7,
}

class Canvas:
    def __init__(self, width, height):
        self.width, self.height = width, height
        self.color = [BACKGROUND] * (width * height)
        self.depth = [float('inf')] * (width * height)

    def text(self, x, y, words, scale=3, rgb=(40, 30, 25)):
        for ch in words.upper():
            glyph = FONT.get(ch, FONT[' '])
            for gy, line in enumerate(glyph):
                for gx, bit in enumerate(line):
                    if bit == '1':
                        for dy in range(scale):
                            for dx in range(scale):
                                px, py = x + gx * scale + dx, y + gy * scale + dy
                                if 0 <= px < self.width and 0 <= py < self.height:
                                    self.color[py * self.width + px] = rgb
            x += 6 * scale

    def frame(self, x0, y0, w, h, rgb=(170, 165, 155)):
        for x in range(x0, x0 + w):
            for y in (y0, y0 + h - 1):
                if 0 <= x < self.width and 0 <= y < self.height:
                    self.color[y * self.width + x] = rgb
        for y in range(y0, y0 + h):
            for x in (x0, x0 + w - 1):
                if 0 <= x < self.width and 0 <= y < self.height:
                    self.color[y * self.width + x] = rgb

    def save(self, path):
        write_png(path, self.width, self.height, [c + (255,) for c in self.color])

def draw(canvas, boxes, cell, floor, yaw, pitch, ppx):
    """Draws the boxes inside the cell (x0, y0, w, h), the model's origin at the middle and `floor` of the way down."""
    x0, y0, w, h = cell
    ox, oy = x0 + w / 2, y0 + h * floor
    def view(p):
        x, y, z = p
        x1 = x * math.cos(yaw) - z * math.sin(yaw)
        z1 = x * math.sin(yaw) + z * math.cos(yaw)
        y2 = y * math.cos(pitch) - z1 * math.sin(pitch)
        z2 = y * math.sin(pitch) + z1 * math.cos(pitch)
        return x1, y2, z2
    width = canvas.width
    for corners, rgb in boxes:
        v = [view(p) for p in corners]
        for f in FACES:
            a, b, c, d = (v[i] for i in f)
            ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
            wx, wy, wz = d[0] - a[0], d[1] - a[1], d[2] - a[2]
            nx, ny, nz = uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx
            ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
            shade = 0.55 + 0.45 * abs((nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]) / ln)
            col = tuple(int(ch * shade) for ch in rgb)
            pts = [(ox + q[0] * ppx, oy - q[1] * ppx, q[2]) for q in (a, b, c, d)]
            for tri in ((pts[0], pts[1], pts[2]), (pts[0], pts[2], pts[3])):
                (ax, ay, az), (bx, by, bz), (cx, cy, cz) = tri
                area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
                if abs(area) < 1e-9:
                    continue
                for py in range(max(y0, int(min(ay, by, cy))), min(y0 + h, int(max(ay, by, cy)) + 1)):
                    for px in range(max(x0, int(min(ax, bx, cx))), min(x0 + w, int(max(ax, bx, cx)) + 1)):
                        sx, sy = px + 0.5, py + 0.5
                        w0 = ((bx - sx) * (cy - sy) - (cx - sx) * (by - sy)) / area
                        w1 = ((cx - sx) * (ay - sy) - (ax - sx) * (cy - sy)) / area
                        w2 = 1 - w0 - w1
                        if w0 < 0 or w1 < 0 or w2 < 0:
                            continue
                        zz = w0 * az + w1 * bz + w2 * cz
                        i = py * width + px
                        if zz < canvas.depth[i]:
                            canvas.depth[i] = zz
                            canvas.color[i] = col

# One picture per camera angle: (file name, title, turn, tilt, zoom, cell width, cell height, floor, side by side?)
SHEETS = [
    ('front.png', 'FRONT - LOOKING AT HER FACE', 0, 0, 30.0, 470, 380, 0.85, True),
    ('front_above.png', 'FRONT, FROM ABOVE ANGLED DOWN', 0, -35, 18.0, 470, 640, 0.6, True),
    ('side.png', 'SIDE', -90, 0, 18.0, 900, 230, 0.78, False),
    ('top.png', 'FROM ABOVE', -90, -89.9, 18.0, 900, 250, 0.55, False),
]
TITLE_H = 50
LABEL_H = 34

def render_walk(bones, frames=4):
    """walk.png: the king at a few moments of one step, from the front (top row) and from above (bottom row)."""
    rows = [('FRONT', 0, 0, 34.0, 300, 0.85), ('FROM ABOVE', -90, -89.9, 26.0, 330, 0.5)]
    cw = 420
    canvas = Canvas(cw * frames, TITLE_H + sum(r[4] + LABEL_H for r in rows))
    canvas.text(12, 12, 'WALK - ONE STEP, FRONT AND FROM ABOVE', scale=4)
    walk = animations('termite_king', bones)['animations']['walk']['bones']
    y0 = TITLE_H
    for title, yaw, pitch, ppx, ch, floor in rows:
        for k in range(frames):
            t = WALK_SECONDS * k / frames
            x0 = cw * k
            canvas.frame(x0, y0, cw, ch + LABEL_H)
            canvas.text(x0 + 10, y0 + 8, f'{title} {int(t * 1000)} MS')
            draw(canvas, model_boxes(bones, anim=sample(walk, t)), (x0 + 1, y0 + LABEL_H, cw - 2, ch - 1), floor,
                 math.radians(yaw), math.radians(pitch), ppx)
        y0 += ch + LABEL_H
    canvas.save(os.path.join(PREVIEW_DIR, 'walk.png'))

def render(models):
    """Writes one labelled picture per camera angle into references/termites/preview/. models: [(label, boxes)]."""
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    for name, title, yaw, pitch, ppx, cw, ch, floor, across in SHEETS:
        cols, rows = (len(models), 1) if across else (1, len(models))
        canvas = Canvas(cw * cols, TITLE_H + (ch + LABEL_H) * rows)
        canvas.text(12, 12, title, scale=4)
        for k, (label, boxes) in enumerate(models):
            col, row = (k, 0) if across else (0, k)
            x0, y0 = cw * col, TITLE_H + (ch + LABEL_H) * row
            canvas.frame(x0, y0, cw, ch + LABEL_H)
            canvas.text(x0 + 10, y0 + 8, label)
            draw(canvas, boxes, (x0 + 1, y0 + LABEL_H, cw - 2, ch - 1), floor, math.radians(yaw), math.radians(pitch), ppx)
        canvas.save(os.path.join(PREVIEW_DIR, name))

def dump_clips(name, data):
    """Writes a model's clip file: the clips are made in role names, its body plan renames them to its bone names."""
    import body_plan
    dump(f'animations/entity/{name}.animation.json', body_plan.load(name).animations_to_bones(data))

def write_clips():
    """Only the five clip files (`python tools/termite_models.py clips`): the models, textures and pictures untouched."""
    king = front_half() + king_abdomen()
    queen = front_half() + queen_abdomen()
    worker = worker_model()
    minor, major = soldier_model(False), soldier_model(True)
    dump_clips('termite_king', animations('termite_king', king))
    dump_clips('termite_queen', animations('termite_queen', queen))
    dump_clips('termite_worker', animations('termite_worker', worker))
    dump_clips('termite_minor_soldier', soldier_animations('termite_minor_soldier', minor))
    dump_clips('termite_major_soldier', soldier_animations('termite_major_soldier', major))

if __name__ == '__main__' and sys.argv[1:] == ['clips']:
    write_clips()
    print('termite clips written')
elif __name__ == '__main__':
    king = front_half() + king_abdomen()
    queen = front_half() + queen_abdomen()
    dump('geo/entity/termite_king.geo.json', geo('geometry.termite_king', king))
    dump('geo/entity/termite_queen.geo.json', geo('geometry.termite_queen', queen))
    os.makedirs(os.path.join(ROOT, 'textures', 'entity'), exist_ok=True)
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'termite_king.png'))
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'termite_queen.png'))
    worker = worker_model()
    dump('geo/entity/termite_worker.geo.json', geo('geometry.termite_worker', worker))
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'termite_worker.png'))
    make_nymph_texture(os.path.join(ROOT, 'textures', 'entity', 'termite_nymph.png'))
    minor, major = soldier_model(False), soldier_model(True)
    for rank, model in (('minor', minor), ('major', major)):
        dump(f'geo/entity/termite_{rank}_soldier.geo.json', geo(f'geometry.termite_{rank}_soldier', model))
        make_texture(os.path.join(ROOT, 'textures', 'entity', f'termite_{rank}_soldier.png'), soldier=True)
    # One clip file for each model (B11, 2026-10-18), so each walk's stride note fits its own body.
    dump_clips('termite_king', animations('termite_king', king))
    dump_clips('termite_queen', animations('termite_queen', queen))
    dump_clips('termite_worker', animations('termite_worker', worker))
    dump_clips('termite_minor_soldier', soldier_animations('termite_minor_soldier', minor))
    dump_clips('termite_major_soldier', soldier_animations('termite_major_soldier', major))
    render([('KING', model_boxes(king)), ('WORKER', model_boxes(worker)), ('MINOR SOLDIER', model_boxes(minor)),
            ('MAJOR SOLDIER', model_boxes(major))]
           + [(f'QUEEN {int(s * 100)}%', model_boxes(queen, queen_pose(s))) for s in (0.0, 0.5, 0.75, 1.0)])
    render_walk(king)
    render_head_bang(major, os.path.join(PREVIEW_DIR, 'head_bang.png'))
    print('termite models, clips and preview pictures written')
