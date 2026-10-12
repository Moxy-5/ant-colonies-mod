"""
Builds the placeholder termite king and queen: GeckoLib models, animations and textures.
Run from the project folder:  python tools/termite_models.py

Units are model pixels (16 = 1 block before the renderer's scale); the head points to -Z.
The queen's head, thorax and legs are the king's; her abdomen is six segments (seg_1 .. seg_6), each with a pale
skin (seg_N_skin), a dark back plate (seg_N_plate) and a lighter belly plate (seg_N_belly). The renderer
(TermiteQueenModel) stretches the skin as she grows and keeps the plates their size, so they drift apart.
Keep these bone names: the Java code looks them up.
"""
import json, os, random, struct, zlib

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'antcolonies')

# --- texture: 64x64, 8x8 swatches, each a colour with a little noise -------------------------------------------
SWATCH = {
    'head':     (0, 0, (88, 44, 22)),
    'back':     (1, 0, (66, 33, 16)),    # dark chestnut back plates
    'joint':    (2, 0, (176, 128, 64)),  # golden line between the king's segments
    'belly':    (3, 0, (184, 140, 92)),  # lighter underside plates
    'leg':      (4, 0, (196, 146, 70)),
    'antenna':  (5, 0, (172, 118, 56)),
    'eye':      (6, 0, (22, 16, 16)),
    'jaw':      (7, 0, (42, 20, 10)),
    'wing':     (0, 1, (128, 110, 92)),  # wing stubs left after the wings were dropped
    'skin':     (1, 1, (238, 226, 196)), # the queen's stretched pale skin
    'thorax':   (2, 1, (78, 39, 19)),
}

def write_png(path, width, height, pixels):
    raw = b''.join(b'\x00' + bytes([c for px in pixels[y * width:(y + 1) * width] for c in px]) for y in range(height))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
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

def cube(origin, size, swatch):
    col, row, _ = SWATCH[swatch]
    face = {'uv': [col * 8 + 1, row * 8 + 1], 'uv_size': [6, 6]}
    return {'origin': [round(v, 3) for v in origin], 'size': [round(v, 3) for v in size],
            'uv': {f: dict(face) for f in ('north', 'south', 'east', 'west', 'up', 'down')}}

def centered(cx, cy, cz, sx, sy, sz, swatch):
    return cube([cx - sx / 2, cy - sy / 2, cz - sz / 2], [sx, sy, sz], swatch)

def bone(name, parent, pivot, cubes=(), rotation=None):
    b = {'name': name, 'pivot': [round(v, 3) for v in pivot]}
    if parent:
        b['parent'] = parent
    if rotation:
        b['rotation'] = rotation
    if cubes:
        b['cubes'] = list(cubes)
    return b

# --- shared front half: head, antennae, thorax, wing stubs, legs ----------------------------------------------
def front_half():
    bones = [bone('body', None, [0, 1.5, 0])]
    bones.append(bone('head', 'body', [0, 1.8, -2.3], [
        cube([-1.1, 1.0, -4.2], [2.2, 1.7, 1.9], 'head'),
        cube([-0.8, 2.7, -4.0], [1.6, 0.2, 1.5], 'head'),        # rounded top
        cube([1.1, 1.65, -3.7], [0.25, 0.6, 0.75], 'eye'),       # only kings and queens have eyes
        cube([-1.35, 1.65, -3.7], [0.25, 0.6, 0.75], 'eye'),
        cube([-0.7, 1.0, -4.45], [1.4, 0.5, 0.25], 'head'),      # upper lip
    ]))
    bones.append(bone('mandible_l', 'head', [0.4, 1.2, -4.3], [cube([0.1, 1.0, -4.95], [0.45, 0.35, 0.7], 'jaw')]))
    bones.append(bone('mandible_r', 'head', [-0.4, 1.2, -4.3], [cube([-0.55, 1.0, -4.95], [0.45, 0.35, 0.7], 'jaw')]))
    # Beaded antennae: a chain of small beads, first part up and forward, second part bending outward.
    for side, sx in (('l', 1), ('r', -1)):
        start = (0.65 * sx, 2.3, -4.0)
        beads1, p = [], list(start)
        for i in range(3):
            s = 0.38 if i % 2 == 0 else 0.3
            beads1.append(centered(p[0], p[1], p[2], s, s, s, 'antenna'))
            p = [p[0] + 0.18 * sx, p[1] + 0.32, p[2] - 0.28]
        mid = list(p)
        beads2 = []
        for i in range(6):
            s = 0.34 if i % 2 == 0 else 0.27
            beads2.append(centered(p[0], p[1], p[2], s, s, s, 'antenna'))
            p = [p[0] + 0.3 * sx, p[1] + 0.05, p[2] - 0.3]
        bones.append(bone(f'antenna_{side}_1', 'head', list(start), beads1))
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', mid, beads2))
    bones.append(bone('thorax', 'body', [0, 1.7, -1.0], [
        cube([-1.0, 1.1, -2.3], [2.0, 1.3, 1.0], 'thorax'),      # the front shield
        cube([-1.0, 1.0, -1.3], [2.0, 1.35, 0.9], 'back'),
        cube([-1.05, 1.0, -0.4], [2.1, 1.35, 0.8], 'back'),
        cube([-0.8, 0.85, -2.2], [1.6, 0.25, 2.5], 'belly'),
    ]))
    bones.append(bone('wing_stubs', 'thorax', [0, 2.35, -0.5], [
        cube([0.2, 2.33, -1.15], [0.95, 0.12, 1.15], 'wing'),
        cube([-1.15, 2.33, -1.15], [0.95, 0.12, 1.15], 'wing'),
        cube([0.25, 2.33, -0.3], [0.9, 0.12, 1.0], 'wing'),
        cube([-1.15, 2.33, -0.3], [0.9, 0.12, 1.0], 'wing'),
    ]))
    # Three pairs of short legs from the thorax: upper leg out to the side, lower leg down, foot on the ground.
    for n, z in ((1, -1.9), (2, -0.9), (3, 0.1)):
        foot_dz = {1: -0.35, 2: 0.0, 3: 0.35}[n]
        for side, sx in (('l', 1), ('r', -1)):
            x0 = 1.0 * sx
            x1 = 2.1 * sx
            bones.append(bone(f'leg_{side}{n}', 'body', [x0, 1.4, z],
                              [cube([min(x0, x1), 1.25, z - 0.15], [1.1, 0.3, 0.3], 'leg')]))
            bones.append(bone(f'leg_{side}{n}_lower', f'leg_{side}{n}', [x1, 1.4, z],
                              [cube([x1 - 0.15, 0.4, z - 0.15], [0.3, 1.0, 0.3], 'leg')]))
            bones.append(bone(f'leg_{side}{n}_foot', f'leg_{side}{n}_lower', [x1, 0.45, z],
                              [cube([x1 - 0.12, 0.0, z - 0.12 + min(0, foot_dz)], [0.25, 0.45, 0.25 + abs(foot_dz)], 'leg')]))
    return bones

# --- the king's abdomen: dark plates on top, light belly, golden lines between segments -------------------------
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
QUEEN_SEG_LENGTH = 0.65
QUEEN_WIDTH = 2.8
QUEEN_HEIGHT = 2.1
QUEEN_TAPER = [0.92, 1.0, 1.0, 0.97, 0.88, 0.7]
QUEEN_CENTER_Y = 1.8
QUEEN_START_Z = 0.4

def queen_abdomen():
    bones = [bone('abdomen', 'body', [0, QUEEN_CENTER_Y, QUEEN_START_Z])]
    for i, t in enumerate(QUEEN_TAPER):
        n = i + 1
        zc = QUEEN_START_Z + QUEEN_SEG_LENGTH * (i + 0.5)
        w, h, l = QUEEN_WIDTH * t, QUEEN_HEIGHT * t, QUEEN_SEG_LENGTH
        cy = QUEEN_CENTER_Y
        bones.append(bone(f'seg_{n}', 'abdomen', [0, cy, zc]))
        bones.append(bone(f'seg_{n}_skin', f'seg_{n}', [0, cy, zc], [centered(0, cy, zc, w - 0.15, h - 0.15, l - 0.02, 'skin')]))
        bones.append(bone(f'seg_{n}_plate', f'seg_{n}', [0, cy, zc], [centered(0, cy + h * 0.5 - h * 0.275, zc, w, h * 0.55, l * 0.84, 'back')]))
        bones.append(bone(f'seg_{n}_belly', f'seg_{n}', [0, cy, zc], [centered(0, cy - h * 0.5 + h * 0.225, zc, w * 0.9, h * 0.45, l * 0.84, 'belly')]))
    return bones

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

if __name__ == '__main__':
    dump('geo/entity/termite_king.geo.json', geo('geometry.termite_king', front_half() + king_abdomen()))
    dump('geo/entity/termite_queen.geo.json', geo('geometry.termite_queen', front_half() + queen_abdomen()))
    dump('animations/entity/termite_king.animation.json', animations())
    os.makedirs(os.path.join(ROOT, 'textures', 'entity'), exist_ok=True)
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'termite_king.png'))
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'termite_queen.png'))
    print('termite models written')
