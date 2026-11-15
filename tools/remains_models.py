"""Remains (M9 river life; the user, 2026-11-15: "there needs to be meat on the bones", no blood): what is left of a
kill, in four stages that show how much food is left, for the user to look at in the model viewer
(python tools/model_viewer.py remains).

Stages (docs/DESIGN.md, "Remains, the crab and the Nile monitor"):
  1 fresh       the whole animal lying on its side, one open patch of meat where the hunter fed
  2 half-eaten  the skin and flesh gone from the upper side, thick meat with the ribs and spine showing
  3 picked over mostly the frame of bones with scraps of meat left on ribs, spine and legs (the crabs' stage)
  4 bones       a bare, bleached skeleton, sinking into the bed

Three animals:
  - the river catfish (the main river remains: a crocodile tears a catfish and leaves the rest), built from the game's
    own catfish model (catfish_models.py) so it is the same fish, cut open;
  - the tilapia (a fish that died of old age; hunters swallow tilapia whole), from tilapia_models.py;
  - a cow (a big land kill a crocodile hauls in), a Minecraft-style cow made here, since the game's cow model is
    Minecraft's own.
Meat in Minecraft's raw-meat style: blocky pink-red with lighter streaks and fat, never blood, splatter or guts.

The animal lies on its left side, so its right side (+X in model space) faces up; the viewer rolls it over.
Units are model pixels (16 a block); fish heads point to -Z, up is +Y.
"""
import copy
import inspect
import math
import random

import catfish_models as cm
import tilapia_models as tm
from tilapia_models import cube, faces, face_point, mix, shade

STAGES = ['Fresh', 'Half-eaten', 'Picked over', 'Bones']

MEAT = (186, 58, 60)
MEAT_LIGHT = (222, 104, 104)
MEAT_DARK = (138, 40, 46)
FAT = (236, 206, 194)
BONE = (230, 222, 200)
BONE_OLD = (212, 210, 200)
SOCKET = (40, 26, 26)


# --- painting ------------------------------------------------------------------------------------------------------------
def meat_colour(x, y, z, stage, rnd):
    """Minecraft raw-meat style: red with lighter streaks running across, a fat streak now and then; older meat (stage 3)
    darker and browner, as it dries."""
    k = math.sin(z * 2.1 + y * 0.9 + x * 0.7) + 0.6 * math.sin(z * 4.7 - y * 1.9)
    c = MEAT_LIGHT if k > 0.9 else MEAT_DARK if k < -1.0 else MEAT
    if math.sin(z * 1.3 + y * 3.1) > 0.94:
        c = FAT
    if stage >= 3:
        c = shade(mix(c, (120, 64, 50), 0.35), 0.86)
    return shade(c, rnd.uniform(0.93, 1.06))


def bone_colour(x, y, z, stage, rnd):
    c = BONE_OLD if stage >= 4 else BONE
    if stage >= 4 and math.sin(x * 3.0 + z * 2.3 + y * 1.7) > 0.8:
        c = shade(c, 0.88)                          # weathering
    return shade(c, rnd.uniform(0.94, 1.04))


def paint(bones, colour, texels):
    """Lays every face out on one texture and colours it pixel by pixel: colour(cube, face, x, y, z, edge, rnd)."""
    rnd = random.Random(13)
    bones = copy.deepcopy(bones)
    patches = []
    for b in bones:
        for c in b.get('cubes', []):
            c['uv'] = {}
            for face, (w, h) in faces(c).items():
                patches.append((max(1, round(w * texels)), max(1, round(h * texels)), c, face))
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
        lo, hi = c['origin'][1], c['origin'][1] + c['size'][1]
        for j in range(h):
            for i in range(w):
                px, py, pz = face_point(c, face, (i + 0.5) / w, (j + 0.5) / h)
                edge = (py - lo) / max(0.1, hi - lo)
                rgb = colour(c, face, px, py, pz, edge, rnd)
                rows[y0 + j][x0 + i] = tuple(max(0, min(255, int(v))) for v in rgb) + (255,)
    for b in bones:
        for c in b.get('cubes', []):
            for k in [k for k in c if k.startswith('_')]:
                c.pop(k)
    return bones, rows, width, height


# --- fish ----------------------------------------------------------------------------------------------------------------
FISH = {
    # module, the part of the body that is eaten (z), the wound in stage 1 (z), the ribs' span (z), the tail fin's part
    'catfish': {'mod': cm, 'eaten': (-12.0, 15.0), 'wound': (-7.0, 1.5), 'ribs': (-10.0, 6.0), 'end': cm.HIND_END},
    'tilapia': {'mod': tm, 'eaten': (-5.0, 8.4), 'wound': (-2.5, 3.0), 'ribs': (-4.2, 4.0), 'end': tm.PEDUNCLE},
}
BODY_PARTS = ('body', 'head', 'jaw', 'helmet', 'gill')
EYE_PARTS = ('iris', 'pupil')


def fish_remains(kind, stage, look='A'):
    """(bones, rows, width, height) of a fish's remains at stage 1 to 4."""
    spec = FISH[kind]
    mod = spec['mod']
    rnd = random.Random(31 + stage)
    bones = mod.model()
    z0, z1 = spec['eaten']
    rz0, rz1 = spec['ribs']
    mid = mod.MID

    def half(z):
        top, bot, w = mod.at(max(mod.SNOUT, min(z, spec['end'])))
        return top, bot, w

    for b in bones:
        name = b['name']
        keep = []
        for c in b.get('cubes', []):
            part = c['_part']
            (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
            cz = oz + sz / 2
            if stage == 1:
                if part in BODY_PARTS and spec['wound'][0] <= cz <= spec['wound'][1] and ox + sx > 0.2:
                    cut = ox + sx * rnd.uniform(0.55, 0.78)         # a ragged bite out of the upper flank
                    c['size'][0] = round(cut - ox, 3)
                    c['_meat'] = {'east'}
                keep.append(c)
            elif stage == 2:
                if part in BODY_PARTS and z0 <= cz <= z1 and part not in ('head', 'helmet', 'jaw'):
                    cut = rnd.uniform(-0.15, 0.35) * (sx / 2)
                    if rz0 <= cz <= rz1:
                        cut = rnd.uniform(-0.25, -0.05)             # eaten down to the ribs, which stand proud
                    if oy + sy < mid - 0.2 and rz0 <= cz <= rz1:
                        cut = ox + sx * 0.3                         # the belly cavity, eaten deeper
                        c['_cavity'] = True
                    if cut > ox + 0.05:
                        c['size'][0] = round(cut - ox, 3)
                        c['_meat'] = {'east'}
                        keep.append(c)
                    continue
                if part in ('dorsal', 'anal') and z0 <= cz <= z1 and rnd.random() < 0.5:
                    continue                                        # fins torn
                keep.append(c)
            elif stage == 3:
                if part in ('head', 'helmet', 'jaw', 'lip', 'gill'):
                    c['_dull'] = True
                    keep.append(c)
                elif part in EYE_PARTS:
                    if part == 'iris':
                        c['_part'] = 'socket'
                        keep.append(c)
                elif part in BODY_PARTS and z0 <= cz <= z1:
                    if rnd.random() < 0.45:                         # a strip of skin left lying on the bed, meat up
                        c['size'][0] = round(min(sx, 0.45), 3)
                        c['_meat'] = {'east'}
                        c['_dull'] = True
                        keep.append(c)
                elif part in BODY_PARTS:
                    c['_dull'] = True
                    keep.append(c)
                elif part in ('caudal',) and rnd.random() < 0.7:
                    c['_dull'] = True
                    keep.append(c)
            else:   # bones
                if part in ('head', 'helmet', 'jaw'):
                    c['_part'] = 'skull'
                    keep.append(c)
                elif part == 'iris':
                    c['_part'] = 'socket'
                    keep.append(c)
                elif part == 'caudal':
                    c['_part'] = 'ray'
                    keep.append(c)
        b['cubes'] = keep
    # the fins, barbels and eyes of the upper side go first; in the end every soft part
    drop = set()
    # the down side's fins and barbels lie pressed under the body, unseen
    drop |= {b['name'] for b in bones if b['name'].endswith('_left')}
    if stage >= 3:
        drop |= {b['name'] for b in bones if b['name'].startswith(('barbel', 'pectoral', 'pelvic'))}
    for b in bones:
        if b['name'] in drop:
            b['cubes'] = []
    # the skeleton showing: a spine down the eaten part, ribs, and (stage 3) scraps of meat on them
    if stage >= 2:
        frame = []
        sz0 = z0 if stage == 2 else z0 + 1.0
        sz1 = spec['end'] if stage >= 3 else z1
        frame.append(cube([-0.45, mid - 0.4, sz0], [0.45 if stage >= 3 else 0.55, mid + 0.4, sz1], 'bone'))
        z = rz0
        while z <= rz1:
            top, bot, w = half(z)
            lo = mid + bot * 0.78
            sides = (1,) if stage == 2 else (1, -1)
            for s in sides:
                if stage == 2:
                    frame.append(cube([-0.35, lo, z], [0.3, mid - 0.2, z + 0.4], 'bone'))
                else:
                    xs = sorted([s * 0.3, s * w * 0.7])
                    frame.append(cube([xs[0], mid - 0.5, z], [xs[1], mid - 0.2, z + 0.32], 'bone'))   # out from the spine
                    xw = sorted([s * w * 0.7, s * (w * 0.7 + 0.32)])
                    frame.append(cube([xw[0], lo, z], [xw[1], mid - 0.2, z + 0.32], 'bone'))          # and down
                    if stage == 3 and rnd.random() < 0.4:
                        h = rnd.uniform(0.6, 1.4)
                        y0 = rnd.uniform(lo, mid - 0.2 - h)
                        frame.append(cube([xw[0] - 0.2, y0, z - 0.25], [xw[1] + 0.2, y0 + h, z + 0.55], 'scrap'))
            z += 1.3 if kind == 'catfish' else 1.0
        if stage >= 3:      # the tail's bones and the back fin's spines along the top of the spine
            z = (rz1 + 1.0)
            while z < spec['end']:
                frame.append(cube([-0.25, mid + 0.4, z], [0.25, mid + 1.4, z + 0.3], 'bone'))
                frame.append(cube([-0.25, mid - 1.4, z], [0.25, mid - 0.4, z + 0.3], 'bone'))
                z += 1.3
            if stage == 3:
                for _ in range(6 if kind == 'catfish' else 3):
                    z = rnd.uniform(z0 + 1.5, rz1)
                    frame.append(cube([-0.6, mid + 0.35, z], [0.6, mid + 0.95, z + rnd.uniform(0.8, 1.8)], 'scrap'))
        bones.append({'name': 'frame', 'parent': 'body', 'pivot': [0, mid, 0], 'cubes': frame})

    takes_face = 'face' in inspect.signature(mod.colour).parameters

    def colour(c, face, x, y, z, edge, rnd2):
        part = c['_part']
        if part in ('bone', 'skull', 'ray'):
            col = bone_colour(x, y, z, stage, rnd2)
            if part == 'skull' and math.sin(x * 1.7) * math.cos(z * 1.3) > 0.7:
                col = shade(col, 0.9)
            return col
        if part == 'scrap':
            return meat_colour(x, y, z, stage, rnd2)
        if part == 'socket':
            return SOCKET
        if face in c.get('_meat', ()):
            col = meat_colour(x, y, z, stage, rnd2)
            if c.get('_cavity'):
                col = shade(col, 0.8)
            return col
        if takes_face:
            col = mod.colour(part, look, x, y, z, face, edge, rnd2)
        else:
            col = mod.colour(part, look, x, y, z, edge, rnd2)
        if c.get('_dull'):
            col = shade(mix(col, (150, 140, 120), 0.3), 0.85)       # skin drying and greying
        return col

    return paint(bones, colour, mod.TEXELS)


# --- the cow -------------------------------------------------------------------------------------------------------------
# A Minecraft-style cow: body 12 wide, 10 tall, 18 long; legs 4 by 4 by 12; head 8 by 8 by 6 with horns and a muzzle.
# Its head points to -Z like the fish; standing, its feet are at y = 0.
BODY_Y0, BODY_Y1 = 12.0, 22.0
BODY_Z0, BODY_Z1 = -8.0, 10.0
CHEST = (-6.0, 4.0)          # where the ribs run (z)


def cow_parts(stage):
    rnd = random.Random(57 + stage)
    cubes = []

    def add(lo, hi, part, **extra):
        c = cube(lo, hi, part)
        c.update(extra)
        cubes.append(c)
        return c

    # the body in slabs 2 long, each cut as the stage wants
    z = BODY_Z0
    while z < BODY_Z1 - 1e-6:
        b = z + 2.0
        cz = (z + b) / 2
        if stage == 1:
            if -2.0 <= cz <= 6.0:   # one bite into the upper flank, behind the ribs
                cut = 6.0 - rnd.uniform(1.4, 2.6)
                add([-6, BODY_Y0, z], [cut, BODY_Y1, b], 'hide', _meat={'east'})
            else:
                add([-6, BODY_Y0, z], [6, BODY_Y1, b], 'hide')
        elif stage == 2:
            if z >= BODY_Z0 + 1.9 and b <= BODY_Z1 - 1.9:
                cut = rnd.uniform(-0.8, 1.2)
                belly = CHEST[0] <= cz <= CHEST[1]
                add([-6, BODY_Y0, z], [cut - (3.0 if belly else 0.0), BODY_Y0 + 3.5, b], 'hide', _meat={'east', 'up'})
                add([-6, BODY_Y0 + 3.5, z], [cut, BODY_Y1, b], 'hide', _meat={'east'})
            else:
                add([-6, BODY_Y0, z], [6, BODY_Y1, b], 'hide', _meat={'north' if z < 0 else 'south'})
        elif stage == 3:
            if rnd.random() < 0.7:  # the hide lying under the frame, meat side up
                add([-6, BODY_Y0 + 1, z], [-5.2, BODY_Y1 - 1, b], 'hide', _meat={'east'}, _dull=True)
        z = b
    # the frame of bones
    if stage >= 2:
        add([-1, BODY_Y1 - 2, BODY_Z0 + 1], [1 if stage >= 3 else 1.6, BODY_Y1, BODY_Z1 - 1], 'bone')      # spine
        z = CHEST[0]
        while z <= CHEST[1]:
            sides = (1,) if stage == 2 else (1, -1)
            for s in sides:
                xs = sorted([s * 1.0, s * 5.0])
                if stage == 2:
                    add([-0.5, BODY_Y0 + 2.0, z], [1.6, BODY_Y1 - 1.5, z + 1.0], 'bone')    # ribs across the cut
                else:
                    add([xs[0], BODY_Y1 - 2, z], [xs[1], BODY_Y1 - 1, z + 1.0], 'bone')
                    xw = sorted([s * 5.0, s * 6.0])
                    add([xw[0], BODY_Y0 + 1.5, z], [xw[1], BODY_Y1 - 1, z + 1.0], 'bone')
                    if stage == 3 and rnd.random() < 0.45:
                        h = rnd.uniform(1.5, 3.5)
                        y0 = rnd.uniform(BODY_Y0 + 1.5, BODY_Y1 - 1 - h)
                        add([xw[0] - 0.4, y0, z - 0.4], [xw[1] + 0.4, y0 + h, z + 1.4], 'scrap')
            z += 2.0
        if stage >= 3:
            add([-4, BODY_Y0 + 4, BODY_Z1 - 4], [4, BODY_Y1 - 1, BODY_Z1 - 1], 'bone')      # the hips
            add([-5, BODY_Y0 + 5, BODY_Z0 + 1], [-3.5, BODY_Y1 - 1, BODY_Z0 + 4], 'bone')   # shoulder blades
            add([3.5, BODY_Y0 + 5, BODY_Z0 + 1], [5, BODY_Y1 - 1, BODY_Z0 + 4], 'bone')
            if stage == 3:
                for _ in range(4):
                    z = rnd.uniform(BODY_Z0 + 1, BODY_Z1 - 3)
                    add([-1.4, BODY_Y1 - 1, z], [1.4, BODY_Y1, z + rnd.uniform(1.5, 3)], 'scrap')
    # the legs: front pair at z -7..-3, hind pair at 5..9; the upper side's (+X) legs go first
    for lx, lz in ((-6, -7), (2, -7), (-6, 5), (2, 5)):
        upper = lx > 0
        if stage == 1 or (stage == 2 and not upper):
            add([lx, 0, lz], [lx + 4, 12, lz + 4], 'hide')
            add([lx, 0, lz], [lx + 4, 0.01, lz + 4], 'hoof')
        elif stage == 2:     # stripped to meat on the bone
            add([lx + 0.6, 3, lz + 0.6], [lx + 3.4, 12, lz + 3.4], 'hide', _meat=set(faces({'size': [1, 1, 1]})))
            add([lx + 1.2, 0, lz + 1.2], [lx + 2.8, 3, lz + 2.8], 'bone')
        else:
            add([lx + 1.2, 0.5, lz + 1.2], [lx + 2.8, 12, lz + 2.8], 'bone')
            add([lx + 0.8, 10, lz + 0.8], [lx + 3.2, 12, lz + 3.2], 'bone')     # the joint's knob
            if stage == 3 and not upper:
                add([lx + 0.7, 5, lz + 0.7], [lx + 3.3, 9, lz + 3.3], 'scrap')
    # the head (8 wide, 8 tall, 6 long) at the front, a muzzle, horns
    hz0, hz1, hy0 = -14.0, -8.0, 16.0
    if stage <= 3:
        part = 'face'
        add([-4, hy0, hz0], [4, hy0 + 8, hz1], part, _dull=(stage == 3))
        add([-3, hy0, hz0 - 1], [3, hy0 + 3, hz0], 'muzzle', _dull=(stage == 3))
        for s in (1, -1):
            xs = sorted([s * 4, s * 5])
            add([xs[0], hy0 + 7, hz0 + 2], [xs[1], hy0 + 10, hz0 + 3], 'horn')
            add([s * 4 - (0 if s > 0 else 0.01), hy0 + 4, hz0 + 1], [s * 4 + (0.01 if s > 0 else 0), hy0 + 6, hz0 + 3],
                'socket' if (stage >= 2 and s > 0) or stage == 3 else 'eye')
    else:
        add([-3.4, hy0 + 0.5, hz0], [3.4, hy0 + 6.5, hz1], 'skull')
        add([-2.2, hy0 + 0.5, hz0 - 3], [2.2, hy0 + 3, hz0], 'skull')
        for s in (1, -1):
            xs = sorted([s * 3.4, s * 4.4])
            add([xs[0], hy0 + 5.5, hz0 + 2], [xs[1], hy0 + 8.5, hz0 + 3], 'horn')
            add([s * 3.4 - (0 if s > 0 else 0.01), hy0 + 3, hz0 + 1], [s * 3.4 + (0.01 if s > 0 else 0), hy0 + 5, hz0 + 3], 'socket')
    return cubes


def cow_remains(stage):
    cubes = cow_parts(stage)
    bones = [{'name': 'body', 'pivot': [0, 0, 0], 'cubes': cubes}]

    def colour(c, face, x, y, z, edge, rnd):
        part = c['_part']
        if part in ('bone', 'skull'):
            return bone_colour(x, y, z, stage, rnd)
        if part == 'scrap':
            return meat_colour(x, y, z, stage, rnd)
        if part == 'socket':
            return SOCKET
        if part == 'eye':
            return (20, 20, 22)
        if part == 'horn':
            return shade((214, 206, 180), rnd.uniform(0.92, 1.04))
        if part == 'hoof':
            return (60, 50, 44)
        if face in c.get('_meat', ()):
            return meat_colour(x, y, z, stage, rnd)
        if part == 'muzzle':
            col = (186, 160, 150)
        else:   # brown with big white patches, like Minecraft's cow
            n = math.sin(x * 0.35 + z * 0.42) + math.sin(y * 0.5 - z * 0.27 + 1.3)
            col = (236, 232, 226) if n > 1.0 else (78, 54, 40)
            col = shade(col, rnd.uniform(0.92, 1.06))
        if c.get('_dull'):
            col = shade(mix(col, (150, 140, 120), 0.3), 0.85)
        return col

    return paint(bones, colour, 1.0)


def all_remains():
    """{'catfish': [stage 1..4], 'tilapia': [...], 'cow': [...]}, each (bones, rows, width, height)."""
    return {'catfish': [fish_remains('catfish', s) for s in (1, 2, 3, 4)],
            'tilapia': [fish_remains('tilapia', s) for s in (1, 2, 3, 4)],
            'cow': [cow_remains(s) for s in (1, 2, 3, 4)]}


if __name__ == '__main__':
    for kind, stages in all_remains().items():
        print(kind, [sum(len(b.get('cubes', [])) for b in s[0]) for s in stages], 'boxes')
