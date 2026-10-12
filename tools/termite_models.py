"""
Builds the placeholder termite king and queen: GeckoLib models, animations and textures, plus a preview picture.
Run from the project folder:  python tools/termite_models.py

Units are model pixels (16 = 1 block before the renderer's scale); the head points to -Z.
The queen's head, thorax and legs are the king's; her abdomen is six segments (seg_1 .. seg_6), each with a pale
skin (seg_N_skin), a dark back plate (seg_N_plate) and a lighter belly plate (seg_N_belly). The renderer
(TermiteQueenModel) stretches the skin as she grows and keeps the plates their size, so they drift apart.
Keep these bone names: the Java code looks them up.

The model checker: references/termites/preview/ gets one labelled picture per camera angle (front.png looking at
her face, front_above.png, side.png, top.png), each showing the king and the queen at 0%, 50%, 75% and 100% grown, all
at the same scale.
"""
import json, math, os, random, struct, zlib

# Where the forebody (head, thorax, legs) joins the abdomen, in model pixels.
FOREBODY_JOINT = (0, 1.7, 0.4)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'antcolonies')
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
}

def write_png(path, width, height, pixels):
    raw = b''.join(b'\x00' + bytes([c for px in pixels[y * width:(y + 1) * width] for c in px]) for y in range(height))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)

def make_texture(path):
    rnd = random.Random(7)
    pixels = [(0, 0, 0, 0)] * (64 * 64)
    for name, (col, row, rgb) in SWATCH.items():
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
def front_half():
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
    upper, foot = 1.35, 0.6
    for n, z, yaw in ((1, -1.9, 40.0), (2, -0.9, -5.0), (3, 0.1, -45.0)):
        for side, sx in (('l', 1), ('r', -1)):
            a = yaw * sx  # the right side is the mirror image
            x0 = 1.0 * sx
            dx, dz = turn(upper * sx, 0.0, a)
            kx, kz = x0 + dx, z + dz  # the knee
            bones.append(bone(f'leg_{side}{n}', 'forebody', [x0, 1.4, z],
                              [cube([x0 if sx > 0 else x0 - upper, 1.25, z - 0.15], [upper, 0.3, 0.3], 'leg', [x0, 1.4, z], a)]))
            bones.append(bone(f'leg_{side}{n}_lower', f'leg_{side}{n}', [kx, 1.4, kz],
                              [cube([kx - 0.14, 0.25, kz - 0.14], [0.28, 1.25, 0.28], 'leg')]))
            bones.append(bone(f'leg_{side}{n}_foot', f'leg_{side}{n}_lower', [kx, 0.15, kz],
                              [cube([kx if sx > 0 else kx - foot, 0.0, kz - 0.12], [foot, 0.28, 0.24], 'leg', [kx, 0.15, kz], a)]))
    return bones

# --- the king's abdomen: dark plates on top, lighter belly, golden lines between segments -----------------------
KING_SEGMENTS = [(2.3, 1.7), (2.5, 1.85), (2.55, 1.9), (2.45, 1.8), (2.15, 1.6), (1.6, 1.15)]

def king_abdomen():
    cubes, z = [], 0.4
    for w, h in KING_SEGMENTS:
        cy = 1.85
        cubes.append(centered(0, cy + h * 0.2, z + 0.29, w, h * 0.6, 0.5, 'back'))
        cubes.append(centered(0, cy - h * 0.3, z + 0.29, w * 0.92, h * 0.4, 0.5, 'belly'))
        cubes.append(centered(0, cy, z + 0.56, w * 0.9, h * 0.9, 0.1, 'joint'))
        z += 0.6
    return [bone('abdomen', 'body', [0, 1.85, 0.4], cubes)]

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

def geo(identifier, bones):
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': identifier, 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 4, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]},
        'bones': bones}]}

def animations():
    swing = lambda a: {'0': [0, a, 0], '0.25': [0, -a, 0], '0.5': [0, a, 0]}
    walk = {name: {'rotation': swing(22 if name in ('leg_l1', 'leg_r2', 'leg_l3') else -22)}
            for name in ('leg_l1', 'leg_r2', 'leg_l3', 'leg_r1', 'leg_l2', 'leg_r3')}
    wiggle = lambda a: {'0': [0, 0, 0], '0.6': [a, 0, 0], '1.2': [0, 0, 0], '1.8': [-a, 0, 0], '2.4': [0, 0, 0]}
    walk['antenna_l_1'] = {'rotation': {'0': [8, 0, 0], '0.25': [-8, 0, 0], '0.5': [8, 0, 0]}}
    walk['antenna_r_1'] = {'rotation': {'0': [-8, 0, 0], '0.25': [8, 0, 0], '0.5': [-8, 0, 0]}}
    idle = {'antenna_l_1': {'rotation': wiggle(10)}, 'antenna_r_1': {'rotation': wiggle(-10)},
            'head': {'rotation': {'0': [0, 0, 0], '1.2': [0, 5, 0], '2.4': [0, 0, 0]}}}
    return {'format_version': '1.8.0', 'animations': {
        'idle': {'loop': True, 'animation_length': 2.4, 'bones': idle},
        'walk': {'loop': True, 'animation_length': 0.5, 'bones': walk}}}

def dump(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1)

# --- preview: a small software renderer (boxes, depth buffer, flat light) --------------------------------------
def model_boxes(bones, pose=None):
    """Every cube as (8 corners, swatch colour), with cube turns and the queen's bone pose applied."""
    pose = pose or {}
    by_name = {b['name']: b for b in bones}
    swatch_of = {(c * 8 + 1, r * 8 + 1): rgb for c, r, rgb in SWATCH.values()}

    def bone_transform(name, p):
        # Innermost bone first, then its parents: scale about the pivot, then move.
        while name:
            b = by_name[name]
            if name in pose:
                dy, dz, (sx, sy, sz) = pose[name][:3]
                tilt = math.radians(pose[name][3]) if len(pose[name]) > 3 else 0.0
                px, py, pz = b['pivot']
                ry, rz = (p[1] - py) * sy, (p[2] - pz) * sz
                # A tilt about X (GeckoLib setRotX): +Y turns toward +Z; a negative tilt puts the head (-Z) down.
                ry, rz = ry * math.cos(tilt) - rz * math.sin(tilt), ry * math.sin(tilt) + rz * math.cos(tilt)
                p = (px + (p[0] - px) * sx, py + ry + dy, pz + rz + dz)
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

if __name__ == '__main__':
    king = front_half() + king_abdomen()
    queen = front_half() + queen_abdomen()
    dump('geo/entity/termite_king.geo.json', geo('geometry.termite_king', king))
    dump('geo/entity/termite_queen.geo.json', geo('geometry.termite_queen', queen))
    dump('animations/entity/termite_king.animation.json', animations())
    os.makedirs(os.path.join(ROOT, 'textures', 'entity'), exist_ok=True)
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'termite_king.png'))
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'termite_queen.png'))
    render([('KING', model_boxes(king))] + [(f'QUEEN {int(s * 100)}%', model_boxes(queen, queen_pose(s)))
                                            for s in (0.0, 0.5, 0.75, 1.0)])
    print('termite models and preview pictures written')
