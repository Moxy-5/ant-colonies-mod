"""The river snail (M9 river life, the clean-up crew: it grazes the algae and is the crab's prey), four models to pick
from in the model viewer (python tools/model_viewer.py snail).

The user, 2026-11-16: "I dont care about realism for it, I only care that it looks good in minecraft and its cute;
make 4 different models; definitely smaller than the crab." So these are cute first: chunky little cubes, big round
eyes with a white shine, soft speckled bodies, bold shells. My picks, all four:
- A "Garden spiral": the classic round coiled shell standing on its edge, a spiral groove on each side round a stepped
  boss, brown and cream bands round the rim; eyes on tall stalks.
- B "Ruby ramshorn": a flat wheel of a shell, deep cherry red with tight grooves; a charcoal body; no stalks, big eyes on
  the head itself at the foot of two long floppy feelers.
- C "Striped steeple": a tall pointed spire leaning back, olive with dark brown bands; a golden flecked body; a little
  brown trapdoor (the shell's lid) riding on its tail.
- D "Mossy nerite": a low round dome with bold black and yellow zigzags, little tufts of algae growing on top; a pale
  body and short stalks.

Units are model pixels (16 a block); the boxes are given in voxels of V model pixels. The front points to -Z, up is +Y,
left is +X; y = 0 is the ground it crawls on. Bones: foot (the root), head, eye_left/eye_right (the stalks, or B's
feelers), feeler_left/feeler_right (the short lower feelers), shell (and D's tufts). The viewer scales every model to
the same length, so the voxel size only sets the grain.
"""
import copy
import math
import random

from tilapia_models import cube, mix, shade, faces, face_point, png  # noqa: F401 (png for the viewer)

V = 0.25              # model pixels a voxel
TEXELS = 4.0          # texture pixels a model pixel: one flat colour a voxel face
SIDES = (('left', 1), ('right', -1))


def box(cubes, x0, y0, z0, x1, y1, z1, part):
    """A box given in voxels (corners in any order)."""
    (x0, x1), (y0, y1), (z0, z1) = sorted((x0, x1)), sorted((y0, y1)), sorted((z0, z1))
    cubes.append(cube([x0 * V, y0 * V, z0 * V], [x1 * V, y1 * V, z1 * V], part))


def disc(c, cy, cz, r, x0, x1, part, step=1.0):
    """A round slab standing on its edge (round in the y-z plane), x0 to x1 thick: rows of boxes, equal rows merged."""
    rows = []
    n = int(math.ceil(r / step))
    for i in range(-n, n):
        y0, y1 = cy + i * step, cy + (i + 1) * step
        mid = (i + 0.5) * step
        if abs(mid) >= r:
            continue
        h = round(math.sqrt(r * r - mid * mid) * 2) / 2
        if rows and rows[-1][2] == h:
            rows[-1][1] = y1
        else:
            rows.append([y0, y1, h])
    for y0, y1, h in rows:
        box(c, x0, y0, cz - h, x1, y1, cz + h, part)


def dome_layer(c, cx, cz, rx, rz, y0, y1, part):
    """One flat layer of a dome: an oval seen from above, rows of boxes along z, equal rows merged."""
    rows = []
    n = int(math.ceil(rz))
    for i in range(-n, n):
        mid = i + 0.5
        if abs(mid) >= rz:
            continue
        h = round(rx * math.sqrt(1 - (mid / rz) ** 2) * 2) / 2
        if h < 0.5:
            continue
        if rows and rows[-1][2] == h:
            rows[-1][1] = cz + i + 1
        else:
            rows.append([cz + i, cz + i + 1, h])
    for z0, z1, h in rows:
        box(c, cx - h, y0, z0, cx + h, y1, z1, part)


# --- the soft body: a foot and a head, shared by all four ---------------------------------------------------------------
def foot(width=1.0, length=1.0):
    """The foot from above, front to back: a rounded front, a broad middle, a tail tapering to a point; a rounded top
    ridge; the sole's edge a little paler (painted by height)."""
    c = []
    w, l = width, length
    for z0, z1, hw, top in ((-9, -6, 3.4, 2.6), (-6, 4, 4.2, 2.8), (4, 8, 3.4, 2.6), (8, 11, 2.4, 2.0),
                            (11, 13.5, 1.4, 1.5)):
        box(c, -hw * w, 0, z0 * l, hw * w, top, z1 * l, 'foot')
    box(c, -3.0 * w, 2.8, -6 * l, 3.0 * w, 3.5, 6 * l, 'foot')          # the rounded back under the shell
    return c


def head(snout=1.0):
    """The head rising from the foot's front: a neck, a round head and a soft snout with a little dark mouth."""
    c = []
    box(c, -3.0, 0.4, -12.0, 3.0, 4.6, -7.0, 'head')
    box(c, -2.6, 1.0, -12.8, 2.6, 5.4, -7.6, 'head')
    box(c, -2.2, 0.8, -13.6 * snout, 2.2, 4.4, -12.0, 'head')
    box(c, -1.0, 1.0, -13.7 * snout, 1.0, 1.6, -13.4 * snout, 'mouth')
    return c


def round_eye(c, s, x, y, z, size, white=False):
    """A big round eye (a cube with its corners eased by a cross of boxes) with a white shine on its front, high on the
    outer side: the cute part. With white, a white eyeball with a big black pupil on its front (for a dark body)."""
    h = size / 2
    part = 'white' if white else 'eye'
    box(c, x - h, y - h * 0.8, z - h * 0.8, x + h, y + h * 0.8, z + h * 0.8, part)
    box(c, x - h * 0.8, y - h, z - h * 0.8, x + h * 0.8, y + h, z + h * 0.8, part)
    box(c, x - h * 0.8, y - h * 0.8, z - h, x + h * 0.8, y + h * 0.8, z + h, part)
    if white:
        p = size * 0.36
        box(c, x - p, y - p * 1.1, z - h - 0.15, x + p, y + p * 0.9, z - h + 0.2, 'eye')
    q = size * 0.34
    sx = x + s * h * 0.28
    box(c, sx - q / 2, y + h * 0.12, z - h - 0.3, sx + q / 2, y + h * 0.12 + q, z - h + 0.2, 'shine')
    box(c, x - s * h * 0.25 - q * 0.2, y - h * 0.4, z - h - 0.3, x - s * h * 0.25 + q * 0.2, y - h * 0.4 + q * 0.4,
        z - h + 0.2, 'shine')                          # a second, tiny shine low on the other side


def stalk(s, height, eye_size):
    """An eye stalk going up from its base at (s * 1.5, 4.6, -10.5), the eye on its tip."""
    c = []
    x = s * 1.5
    box(c, x - 0.6, 4.6, -11.1, x + 0.6, 4.6 + height, -9.9, 'stalk')
    box(c, x - 0.8, 4.6, -11.3, x + 0.8, 5.8, -9.7, 'stalk')            # thicker at its base
    round_eye(c, s, x, 4.6 + height + eye_size * 0.35, -10.5, eye_size)
    return c


def lower_feeler(s, length=2.6):
    c = []
    x = s * 1.6
    box(c, x - 0.5, 1.5, -12.8 - length, x + 0.5, 2.4, -12.6, 'feeler')
    box(c, x - 0.4, 1.6, -13.0 - length, x + 0.4, 2.3, -12.8 - length + 0.6, 'feeler_tip')
    return c


def soft_parts(out, eyes='stalk', stalk_h=5.5, eye_size=2.6, foot_w=1.0, foot_l=1.0, snout=1.0):
    """The foot, head, eyes and lower feelers, as bones."""
    out.append({'name': 'foot', 'pivot': [0, 0, 0], 'cubes': foot(foot_w, foot_l)})
    hd = head(snout)
    if eyes == 'head':
        for side, s in SIDES:                       # B: big eyes on the head, at the feelers' feet
            round_eye(hd, s, s * 2.5, 4.6, -10.6, eye_size, white=True)
    out.append({'name': 'head', 'parent': 'foot', 'pivot': [0, 2.4 * V, -8 * V], 'cubes': hd})
    for side, s in SIDES:
        if eyes == 'stalk':
            out.append({'name': f'eye_{side}', 'parent': 'head', 'pivot': [s * 1.5 * V, 4.6 * V, -10.5 * V],
                        'rotation': [16, 0, s * 14], 'cubes': stalk(s, stalk_h, eye_size)})
        else:                                       # B: a long thin floppy feeler where the stalk would be
            f = []
            x = s * 1.4
            box(f, x - 0.45, 4.8, -12.0, x + 0.45, 13.5, -11.1, 'feeler')
            box(f, x - 0.35, 13.5, -11.95, x + 0.35, 14.6, -11.15, 'feeler_tip')
            out.append({'name': f'eye_{side}', 'parent': 'head', 'pivot': [x * V, 4.8 * V, -11.5 * V],
                        'rotation': [34, 0, s * 26], 'cubes': f})
        out.append({'name': f'feeler_{side}', 'parent': 'head', 'pivot': [s * 1.6 * V, 2.0 * V, -12.7 * V],
                    'rotation': [-18, s * 18, 0], 'cubes': lower_feeler(s)})


# --- the four shells ---------------------------------------------------------------------------------------------------
SPIRAL_A = (0.0, 10.0, 2.0)       # A's coil centre (x, y, z), radius 7.5
WHEEL_B = (0.0, 9.6, 2.6)         # B's wheel centre, radius 7.2


def shell_a():
    c = []
    _, cy, cz = SPIRAL_A
    disc(c, cy, cz, 7.5, -3.6, 3.6, 'shell')
    disc(c, cy, cz, 6.9, -4.6, 4.6, 'shell')
    for s in (1, -1):                               # the stepped boss on each side, round the spiral's middle
        disc(c, cy, cz, 4.8, s * 4.6, s * 5.4, 'boss')
        disc(c, cy, cz, 2.8, s * 5.4, s * 6.0, 'boss')
        disc(c, cy, cz, 1.2, s * 6.0, s * 6.4, 'boss')
    box(c, -3.4, 2.6, -5.8, 3.4, 4.4, -3.4, 'lip')   # the pale lip of the opening, over the neck
    return c


def shell_b():
    c = []
    _, cy, cz = WHEEL_B
    disc(c, cy, cz, 7.2, -2.4, 2.4, 'shell')
    disc(c, cy, cz, 6.4, -3.3, 3.3, 'shell')
    disc(c, cy, cz, 3.4, -2.9, 2.9, 'boss')          # (inside the wheel: a sunken middle is painted, not built)
    box(c, -2.8, 2.6, -4.6, 2.8, 4.2, -2.6, 'lip')
    return c


CONE_C = [(5.8, 5.4), (4.3, 4.3), (3.2, 3.4), (2.4, 2.7), (1.75, 2.1), (1.25, 1.6), (0.8, 1.2)]   # (radius, height)


def shell_c():
    """Whorls stacked smaller and smaller up a spire (the bone leans it back), each a little off the last so it
    winds; rounded by a cross of boxes."""
    c = []
    y = 1.6
    for i, (r, h) in enumerate(CONE_C):
        a = i * 2.2
        ox, oz = 0.35 * r * math.cos(a), 2.0 + 0.35 * r * math.sin(a)
        part = 'shell' if i else 'shell'
        box(c, ox - r, y, oz - r * 0.72, ox + r, y + h, oz + r * 0.72, part)
        box(c, ox - r * 0.72, y, oz - r, ox + r * 0.72, y + h, oz + r, part)
        box(c, ox - r * 0.88, y + 0.15, oz - r * 0.88, ox + r * 0.88, y + h - 0.15, oz + r * 0.88, part)
        y += h * 0.92
    box(c, -0.35, y - 0.2, 1.65, 0.35, y + 0.9, 2.35, 'shell')         # the point
    return c


def shell_d():
    c = []
    for y0, y1, rx, rz in ((2.6, 5.0, 6.0, 7.4), (5.0, 6.8, 5.6, 6.9), (6.8, 8.3, 4.7, 6.0), (8.3, 9.5, 3.5, 4.7),
                           (9.5, 10.3, 2.1, 3.1)):
        dome_layer(c, 0, 2.4, rx, rz, y0, y1, 'shell')
    box(c, 1.0, 9.6, 3.6, 2.6, 10.8, 5.2, 'boss')      # the little coil at the back, off to one side
    box(c, -4.2, 2.6, -5.4, 4.2, 3.8, -3.6, 'lip')
    return c


def tufts():
    """D's algae: three tufts of soft green strands on the dome's top."""
    c = []
    for tx, tz, hs in ((-1.6, 0.2, (2.2, 3.2, 2.0)), (1.4, -1.4, (1.6, 2.4)), (0.4, 3.2, (2.6, 1.8, 2.2))):
        for j, h in enumerate(hs):
            x = tx + (j - (len(hs) - 1) / 2) * 0.7
            base = 10.0 - 0.18 * (abs(tx) + abs(tz - 2.4))
            box(c, x - 0.3, base, tz - 0.3 + 0.2 * j, x + 0.3, base + h, tz + 0.3 + 0.2 * j, 'tuft')
    return c


def model(key):
    out = []
    if key == 'A':
        soft_parts(out)
        out.append({'name': 'shell', 'parent': 'foot', 'pivot': [0, 3 * V, 2 * V], 'rotation': [-6, 0, 0], 'cubes': shell_a()})
    elif key == 'B':
        soft_parts(out, eyes='head', eye_size=2.8)
        out.append({'name': 'shell', 'parent': 'foot', 'pivot': [0, 3 * V, 2 * V], 'rotation': [-4, 0, 0], 'cubes': shell_b()})
    elif key == 'C':
        soft_parts(out, stalk_h=4.8, eye_size=2.4, foot_l=1.08)
        f = out[0]['cubes']
        box(f, -1.6, 2.6, 9.0, 1.6, 3.2, 12.2, 'operc')       # the trapdoor riding on its tail
        out.append({'name': 'shell', 'parent': 'foot', 'pivot': [0, 2.4 * V, 2.0 * V], 'rotation': [-24, 0, 0],
                    'cubes': shell_c()})
    else:
        soft_parts(out, stalk_h=3.6, eye_size=2.6, foot_w=1.08)
        out.append({'name': 'shell', 'parent': 'foot', 'pivot': [0, 3 * V, 2.4 * V], 'cubes': shell_d()})
        out.append({'name': 'tufts', 'parent': 'shell', 'pivot': [0, 10 * V, 2.4 * V], 'cubes': tufts()})
    return out


# --- colours -----------------------------------------------------------------------------------------------------------
LOOKS = {
    'A': {'name': 'Garden spiral', 'note': "the classic round coiled shell standing on its edge, a spiral groove winding "
          "in to a stepped boss on each side, cocoa and caramel bands round the rim and a pale lip; a soft grey-brown "
          "speckled body, eyes on tall stalks"},
    'B': {'name': 'Ruby ramshorn', 'note': "a flat wheel of a shell, deep cherry red with tight dark grooves and a "
          "shadowed middle; a charcoal body with pale flecks, no stalks: big eyes on the head itself, at the feet of two "
          "long floppy feelers"},
    'C': {'name': 'Striped steeple', 'note': "a tall pointed spire leaning back, olive and khaki with dark brown bands "
          "following the whorls; a golden flecked body, and the shell's little brown trapdoor riding on its tail"},
    'D': {'name': 'Mossy nerite', 'note': "a low round dome with bold black and buttery yellow zigzags fanning from a "
          "little coil at the back, three tufts of algae growing on top; a pale cream body and short stalks"},
}

BODY = {'A': ((150, 136, 118), (126, 112, 96), (176, 162, 142)),
        'B': ((70, 70, 80), (56, 56, 66), (92, 90, 100)),
        'C': ((196, 168, 104), (170, 142, 86), (214, 190, 128)),
        'D': ((214, 204, 184), (194, 182, 160), (230, 222, 204))}
FLECK = {'A': (98, 86, 74), 'B': (150, 150, 162), 'C': (238, 210, 112), 'D': (150, 136, 118)}


def dice(a, b, c):
    """0 to 1, the same for the same voxel every time."""
    n = (int(a) * 374761393 + int(b) * 668265263 + int(c) * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) / 4294967296.0


def pick(colours, r):
    return colours[min(len(colours) - 1, int(r * len(colours)))]


def spiral(dz, dy, pitch):
    """0 to 1 across one turn of a spiral seen side on (the groove where it is small)."""
    r, a = math.hypot(dz, dy), math.atan2(dy, dz)
    return (r / pitch + a / (2 * math.pi)) % 1.0


def colour(part, look, x, y, z, face, rnd):
    xv, yv, zv = x / V, y / V, z / V
    cx, cy, cz = math.floor(xv + 1e-4), math.floor(yv + 1e-4), math.floor(zv + 1e-4)
    if face in ('up', 'down'):
        r = dice(cx, 77, cz)
    elif face in ('east', 'west'):
        r = dice(55, cy, cz)
    else:
        r = dice(cx, cy, 33)
    grain = rnd.uniform(0.97, 1.03)
    if part == 'eye':
        return (24, 22, 30) if face != 'up' else (40, 38, 48)
    if part == 'shine':
        return (250, 250, 246)
    if part == 'white':
        return (236, 234, 226) if face != 'down' else (200, 198, 190)
    if part == 'mouth':
        return (60, 40, 44)
    if part in ('foot', 'head', 'stalk', 'feeler', 'feeler_tip'):
        base = BODY[look]
        c = pick([base[0], base[1], base[0], base[2]], r)
        if part == 'feeler_tip':
            c = mix(c, (255, 255, 255), 0.18)
        if part == 'foot' and yv < 0.9 and face != 'up':
            c = mix(c, (240, 236, 226), 0.3)          # the sole's paler edge
        if part == 'foot' and face == 'down':
            c = shade(base[2], 0.95)
        if dice(cx + 3, cy * 7 + 1, cz * 5) < 0.12:
            c = mix(c, FLECK[look], 0.75)              # speckles
        return shade(c, grain)
    if part == 'operc':
        return shade(pick([(120, 78, 44), (104, 66, 38), (138, 92, 52)], r), grain)
    if part == 'tuft':
        return shade(pick([(92, 150, 58), (74, 130, 50), (110, 168, 70)], r), grain)
    if part == 'lip':
        lip = {'A': (232, 214, 186), 'B': (150, 44, 52), 'C': (226, 214, 170), 'D': (238, 230, 196)}[look]
        return shade(pick([lip, mix(lip, (255, 255, 255), 0.25), lip], r), grain)
    if look == 'A':
        _, sy, sz = SPIRAL_A
        if face in ('east', 'west'):
            v = spiral(zv - sz, yv - sy, 2.3)
            c = (96, 62, 40) if v < 0.24 else pick([(176, 120, 72), (160, 106, 62), (190, 136, 84)], r)
            if part == 'boss':
                c = mix(c, (222, 196, 150), 0.25)
            if math.hypot(zv - sz, yv - sy) < 1.3:
                c = (80, 52, 34)
        else:
            ax = abs(xv)
            band = (232, 210, 170) if ax < 1.0 else (112, 72, 44) if 2.0 < ax < 3.2 else (178, 122, 74)
            c = pick([band, shade(band, 0.92), band], r)
        return shade(c, grain)
    if look == 'B':
        _, sy, sz = WHEEL_B
        d = math.hypot(zv - sz, yv - sy)
        if face in ('east', 'west'):
            v = spiral(zv - sz, yv - sy, 1.7)
            c = (104, 22, 34) if v < 0.26 else pick([(172, 40, 52), (156, 32, 46), (190, 58, 64)], r)
            c = shade(c, 0.72 + 0.28 * min(1.0, d / 4.0))   # the sunken middle in shadow
        else:
            c = pick([(196, 62, 66), (178, 46, 56), (210, 84, 80)], r)
            if abs(xv) > 2.0:
                c = shade(c, 0.85)
        return shade(c, grain)
    if look == 'C':
        # bands follow the whorls: a dark band near the bottom of each whorl, a thin dark line where whorls meet
        y = 1.6
        rel = 0.5
        for rr, h in CONE_C:
            if yv < y + h * 0.92 or rr == CONE_C[-1][0]:
                rel = (yv - y) / h
                break
            y += h * 0.92
        if rel < 0.08 or rel > 0.95:
            c = (70, 50, 30)
        elif 0.22 < rel < 0.42:
            c = pick([(98, 70, 40), (86, 60, 34)], r)
        else:
            c = pick([(150, 146, 84), (134, 132, 72), (166, 160, 98), (142, 138, 80)], r)
        return shade(c, grain)
    # D: zigzags fanning from the little coil at the back
    if part == 'boss':
        return shade(pick([(40, 36, 30), (226, 200, 100)], r), grain)
    a = math.atan2(xv - 1.8, zv - 4.4)
    dist = math.hypot(xv - 1.8, zv - 4.4) + 0.4 * max(0.0, 10.0 - yv)
    saw = abs(((dist / 2.6) % 2.0) - 1.0)
    v = (a * 4 / math.pi + 0.7 * saw) % 1.0
    c = (36, 34, 30) if v < 0.42 else pick([(232, 204, 96), (220, 190, 84), (240, 214, 112)], r)
    if face not in ('up',) and yv < 3.6:
        c = shade(c, 0.85)
    return shade(c, grain)


def paint(bones, look):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
    rnd = random.Random(41)
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


if __name__ == '__main__':
    for key in LOOKS:
        bones = model(key)
        n = sum(len(b.get('cubes', [])) for b in bones)
        _, rows, w, h = paint(bones, key)
        print(key, LOOKS[key]['name'], f'{n} boxes, texture {w} x {h}')
