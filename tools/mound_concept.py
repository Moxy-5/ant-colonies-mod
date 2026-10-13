"""Concept pictures for a cathedral-like termite mound (planning only: nothing here is used by the game yet).

Builds a mound out of a small set of proposed blocks and draws it the way Minecraft would show them:
  FULL     the mound soil block we have now
  SPIRE    a pointed-dripstone-like piece in 5 thicknesses (base, thick, middle, thin, tip) so spires taper to a point
  MOUTH    the tip of a spire left open (the chimney mouth the termites peek out of)
  SLOPE    a ramp piece (straight, outer corner, inner corner) that hides the stair steps
Every block is drawn as 8 x 8 x 8 little cubes (2 texture pixels each), coloured with rain streaks that run down the
mound and patches of fresh dark soil, instead of rings by height.

Writes references/termites/preview/concept/*.png. Pure Python with Pillow (no numpy).
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'references', 'termites', 'preview', 'concept')

S = 8                       # little cubes per block edge
REACH = 13                  # blocks from the middle to the edge of the picture's ground
TOP = 36                    # blocks of height room
BG = (236, 232, 222)

OLD, DRY, SETTING, FRESH = (204, 164, 116), (178, 124, 78), (156, 88, 50), (112, 60, 36)
GRASS = (166, 160, 84)
BARE = (170, 128, 84)
HOLE = (30, 16, 10)

# ---------------------------------------------------------------- noise

class Noise:
    def __init__(self, seed, size=32):
        rnd = random.Random(seed)
        self.n = size
        self.v = [rnd.random() for _ in range(size ** 3)]

    def at(self, x, y, z):
        n = self.n
        xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
        fx, fy, fz = x - xi, y - yi, z - zi
        fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
        v = self.v

        def g(a, b, c):
            return v[((a % n) * n + (b % n)) * n + (c % n)]
        x0 = g(xi, yi, zi) + (g(xi + 1, yi, zi) - g(xi, yi, zi)) * fx
        x1 = g(xi, yi + 1, zi) + (g(xi + 1, yi + 1, zi) - g(xi, yi + 1, zi)) * fx
        x2 = g(xi, yi, zi + 1) + (g(xi + 1, yi, zi + 1) - g(xi, yi, zi + 1)) * fx
        x3 = g(xi, yi + 1, zi + 1) + (g(xi + 1, yi + 1, zi + 1) - g(xi, yi + 1, zi + 1)) * fx
        y0 = x0 + (x1 - x0) * fy
        y1 = x2 + (x3 - x2) * fy
        return y0 + (y1 - y0) * fz


# ---------------------------------------------------------------- the mound's shape (in blocks)

class Spire:
    def __init__(self, x, z, height, radius, lean_dir, lean, power=0.85, mouth=False, flutes=6, phase=0.0):
        self.x, self.z, self.h, self.r = x, z, height, radius
        self.flutes, self.phase = flutes, phase
        self.ldx, self.ldz = math.cos(lean_dir), math.sin(lean_dir)
        self.lean, self.power, self.mouth = lean, power, mouth

    def centre(self, y):
        k = self.lean * (y / max(self.h, 1)) ** 1.6 * self.h
        return self.x + self.ldx * k, self.z + self.ldz * k

    def radius(self, y):
        if y >= self.h:
            return 0.0
        return self.r * (1 - y / self.h) ** self.power


class MoundShape:
    """A cathedral mound grown to fraction g (0..1): a heavy buttressed base, a tall leaning main spire and a cluster of
    lower spires on its shoulders."""

    def __init__(self, g, seed=7):
        self.g = g
        rnd = random.Random(seed)
        self.noise = Noise(seed)
        sg = math.sqrt(g)
        H = 4 + 28 * g
        self.height = H
        # a young mound's spire is small: a short stub on a low dome, catching up by about half-grown
        main_h = H * min(1.0, 0.15 + g * 1.9)
        self.spires = [Spire(0, 0, main_h, 3.6 * sg + 0.6, 0.7, 0.04, power=0.8 if g > 0.3 else 1.4, mouth=True, flutes=7)]
        # (angle, distance, height share, base radius, grows from g)
        for ang, dist, share, rb, start in ((0.35, 3.0, 0.74, 2.1, 0.3), (2.25, 3.3, 0.58, 1.9, 0.45),
                                            (3.95, 2.9, 0.84, 2.2, 0.55), (5.15, 3.6, 0.47, 1.7, 0.7),
                                            (1.35, 4.6, 0.36, 1.5, 0.85), (2.95, 5.0, 0.3, 1.3, 0.92)):
            if g < start:
                continue
            grow = min(1.0, (g - start) / 0.25 + 0.35)
            d = dist * sg
            self.spires.append(Spire(math.cos(ang) * d, math.sin(ang) * d, H * share * grow, rb * sg * (0.6 + 0.4 * grow),
                                     ang + rnd.uniform(-0.4, 0.4), 0.05, mouth=share > 0.7, flutes=5,
                                     phase=rnd.uniform(0, 6.3)))
        self.dome_r = 6.8 * sg + 0.8
        self.dome_h = 5.5 * sg + 1.0
        # buttresses: ridges running down from high on the spire to the foot
        self.ribs = []
        for k in range(9 if g > 0.5 else 6 if g > 0.25 else 0):
            ang = k * 2 * math.pi / (9 if g > 0.5 else 6) + rnd.uniform(-0.15, 0.15)
            self.ribs.append((math.cos(ang), math.sin(ang), (8.5 + rnd.uniform(-1, 1.2)) * sg, H * rnd.uniform(0.38, 0.55)))

    def solid(self, x, y, z):
        """Is the middle of block (x, y, z) inside the mound (the spires' thin tops are left to the spire pieces)?"""
        cx, cy, cz = x + 0.5, y + 0.5, z + 0.5
        bump = (self.noise.at(cx * 0.35, cy * 0.25, cz * 0.35) - 0.5) * 0.9
        # dome
        if cy < self.dome_h:
            r = self.dome_r * (1 - cy / self.dome_h) ** 1.3
            if math.hypot(cx, cz) < r + bump:
                return True
        for s in self.spires:
            r = s.radius(cy)
            if r < 0.75:
                continue
            sx, sz = s.centre(cy)
            if r > 1.1:  # vertical flutes: ridges and grooves running up the spire
                r *= 1 + 0.24 * math.cos(s.flutes * math.atan2(cz - sz, cx - sx) + s.phase)
            if math.hypot(cx - sx, cz - sz) < r + bump * 0.6:
                return True
        for dx, dz, length, high in self.ribs:
            along = cx * dx + cz * dz
            if along < 0 or along > length:
                continue
            across = abs(-cx * dz + cz * dx)
            if across > 0.62 + 0.25 * (1 - along / length):
                continue
            if cy < high * (1 - along / length) ** 1.5 + bump:
                return True
        return False


def plan_blocks(shape):
    """Block id per cell: ('full',), ('spire', bottom_w, top_w), ('mouth', ...), ('slope', heightmap)."""
    blocks = {}
    for y in range(0, TOP):
        for x in range(-REACH, REACH + 1):
            for z in range(-REACH, REACH + 1):
                if shape.solid(x, y, z):
                    blocks[(x, y, z)] = ('full',)
    # spire tops: pointed pieces stacked straight up on the spire's top full block, tapering evenly to a point
    for s in shape.spires:
        y = 0
        while y < TOP and s.radius(y + 0.5) >= 0.75:
            y += 1
        sx, sz = s.centre(y - 0.5)
        best = None
        for bx in range(math.floor(sx) - 1, math.floor(sx) + 2):
            for bz in range(math.floor(sz) - 1, math.floor(sz) + 2):
                if blocks.get((bx, y - 1, bz), ('',))[0] == 'full':
                    d = math.hypot(bx + 0.5 - sx, bz + 0.5 - sz)
                    if best is None or d < best[0]:
                        best = (d, bx, bz)
        if best is None:
            continue
        _, bx, bz = best
        n = 0
        while s.radius(y + n) > 0.05 and y + n < TOP:
            n += 1
        n = max(2 if s.r < 2 else 3, min(6, n + 1))
        for i in range(n):
            if (bx, y + i, bz) in blocks:
                break
            w0 = max(2, round(8 - 7 * i / n))
            w1 = round(8 - 7 * (i + 1) / n)
            if i == n - 1 and s.mouth:
                blocks[(bx, y + i, bz)] = ('mouth', w0)
            else:
                blocks[(bx, y + i, bz)] = ('spire', w0, 0 if i == n - 1 else w1)
    # ramps: an empty cell on a full block next to a full block at its own height
    slopes = {}
    for (x, y, z), b in blocks.items():
        if b[0] != 'full':
            continue
        above = (x, y + 1, z)
        if above in blocks or above in slopes:
            continue
        sides = [(dx, dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                 if blocks.get((x + dx, y + 1, z + dz), ('',))[0] == 'full']
        diag = [(dx, dz) for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1))
                if blocks.get((x + dx, y + 1, z + dz), ('',))[0] == 'full'
                and blocks.get((x + dx, y, z), ('',))[0] == 'full' and blocks.get((x, y, z + dz), ('',))[0] == 'full']
        if sides or diag:
            slopes[above] = ('slope', sides, diag)
    blocks.update(slopes)
    return blocks


# ---------------------------------------------------------------- blocks -> little cubes

def piece_cubes(b):
    """The little cubes (u, v, w) of one block, 0..7, v up."""
    kind = b[0]
    out = []
    if kind == 'full':
        return [(u, v, w) for u in range(S) for v in range(S) for w in range(S)]
    if kind in ('spire', 'mouth'):
        w0, w1 = b[1], (b[2] if kind == 'spire' else b[1])
        for v in range(S):
            wv = round(w0 + (w1 - w0) * (v + 0.5) / S)
            if wv <= 0:
                continue
            lo = (S - wv) // 2
            for u in range(lo, lo + wv):
                for w in range(lo, lo + wv):
                    if kind == 'mouth' and lo < u < lo + wv - 1 and lo < w < lo + wv - 1 and v >= S - 3:
                        continue   # the open mouth
                    out.append((u, v, w))
        return out
    if kind == 'slope':
        sides, diag = b[1], b[2]
        for u in range(S):
            for w in range(S):
                fu, fw = (u + 0.5) / S, (w + 0.5) / S
                t = 0.0
                for dx, dz in sides:
                    t = max(t, fu if dx == 1 else 1 - fu if dx == -1 else fw if dz == 1 else 1 - fw)
                for dx, dz in diag:
                    a = fu if dx == 1 else 1 - fu
                    c = fw if dz == 1 else 1 - fw
                    t = max(t, min(a, c))
                hgt = math.ceil(t * S - 0.5)
                for v in range(hgt):
                    out.append((u, v, w))
        return out
    return out


class Voxels:
    def __init__(self):
        self.nx = self.nz = (2 * REACH + 3) * S
        self.ny = (TOP + 2) * S
        self.off = (REACH + 1) * S
        self.grid = bytearray(self.nx * self.ny * self.nz)  # 0 empty, else colour index into self.kinds
        self.kinds = [None]

    def idx(self, x, y, z):
        return ((x + self.off) * self.ny + (y + S)) * self.nz + (z + self.off)

    def set(self, x, y, z, k):
        self.grid[self.idx(x, y, z)] = k

    def get(self, x, y, z):
        if not (-self.off <= x < self.nx - self.off and -S <= y < self.ny - S and -self.off <= z < self.nz - self.off):
            return 0
        return self.grid[self.idx(x, y, z)]


MOUND, GROUND, BAREK, PLAYER_LEGS, PLAYER_BODY, PLAYER_HEAD, MOUTHK = 1, 2, 3, 4, 5, 6, 7


def build_voxels(blocks, ground=True, player=None):
    vox = Voxels()
    for (x, y, z), b in blocks.items():
        k = MOUTHK if b[0] == 'mouth' else MOUND
        for u, v, w in piece_cubes(b):
            vox.set(x * S + u, y * S + v, z * S + w, k)
    if ground:
        for x in range(-REACH * S, (REACH + 1) * S):
            for z in range(-REACH * S, (REACH + 1) * S):
                d = math.hypot(x / S, z / S)
                if d > REACH:
                    continue
                vox.set(x, -1, z, BAREK if d < 8.5 + 1.5 * math.sin(math.atan2(z, x) * 3) else GROUND)
    if player:
        px, pz = player[0] * S, player[1] * S
        for v in range(15):
            k = PLAYER_LEGS if v < 6 else PLAYER_BODY if v < 11 else PLAYER_HEAD
            for u in range(4):
                for w in range(2 if v < 11 else 4):
                    vox.set(px + u, v, pz + w - (1 if v >= 11 else 0), k)
    return vox


# ---------------------------------------------------------------- colour

streak_noise = Noise(11)
patch_noise = Noise(23)
grain = random.Random(5)


def mound_colour(x, y, z, height):
    """Age colour per little cube: older and paler low down, but in streaks that run down the mound (rain) and patches
    of fresh dark soil where the termites are building."""
    t = y / (height * S)
    streak = streak_noise.at(x / 1.6, y / 40.0, z / 1.6) - 0.5
    patch = patch_noise.at(x / 18.0, y / 14.0, z / 18.0)
    a = t * 1.0 + streak * 0.9 - 0.1
    if patch > 0.74:
        a += (patch - 0.74) * 3.0
    a = max(0.0, min(1.0, a))
    stops = [(0.0, OLD), (0.38, DRY), (0.7, SETTING), (1.0, FRESH)]
    for (a0, c0), (a1, c1) in zip(stops, stops[1:]):
        if a <= a1:
            f = (a - a0) / (a1 - a0)
            col = [c0[i] + (c1[i] - c0[i]) * f for i in range(3)]
            break
    j = grain.uniform(0.92, 1.06)
    return tuple(int(c * j) for c in col)


def colour_of(k, x, y, z, height):
    if k in (MOUND, MOUTHK):
        return mound_colour(x, y, z, height)
    j = grain.uniform(0.9, 1.07)
    base = {GROUND: GRASS, BAREK: BARE, PLAYER_LEGS: (60, 60, 150), PLAYER_BODY: (30, 160, 165), PLAYER_HEAD: (196, 146, 108)}[k]
    return tuple(int(c * j) for c in base)


# ---------------------------------------------------------------- drawing

def render(vox, height, scale=3, elevation=30, turn=0, crop_ground=True):
    """Orthographic view from above one corner (turn: 0..3 quarter turns of the mound). Draws only the faces we see."""
    el = math.radians(elevation)
    ce, se = math.cos(el), math.sin(el)
    r2 = math.sqrt(0.5)

    def rot(x, z):
        for _ in range(turn):
            x, z = -z, x
        return x, z

    # project: right = (x - z)/sqrt2, up = y*cos(el) - (x + z)/sqrt2*sin(el)
    def proj(x, y, z):
        return ((x - z) * r2 * scale, -(y * ce - (x + z) * r2 * se) * scale)

    faces = []
    nx, ny, nz, off = vox.nx, vox.ny, vox.nz, vox.off
    g = vox.grid
    for i in range(nx):
        for j in range(ny):
            base = (i * ny + j) * nz
            row = g[base:base + nz]
            if not any(row):
                continue
            x0, y = i - off, j - S
            for kz in range(nz):
                k = row[kz]
                if not k:
                    continue
                x, z = x0, kz - off
                # the faces seen: in the turned frame the camera looks from +x, +y, +z
                rx, rz = rot(x, z)
                seen = []
                if not vox.get(x, y + 1, z):
                    seen.append(('top', (0, 1, 0)))
                for dx, dz in ((1, 0), (0, 1), (-1, 0), (0, -1)):
                    ox, oz = rot(dx, dz)
                    if (ox, oz) in ((1, 0), (0, 1)) and not vox.get(x + dx, y, z + dz):
                        seen.append(('+x' if (ox, oz) == (1, 0) else '+z', (dx, 0, dz)))
                if not seen:
                    continue
                col = colour_of(k, x, y, z, height)
                depth = (rx + rz) * r2 * ce + y * se
                for f, n in seen:
                    ao = occlusion(vox, x, y, z, n) if k not in (GROUND, PLAYER_LEGS, PLAYER_BODY, PLAYER_HEAD) else 1.0
                    faces.append((depth, rx, y, rz, f, col, k, ao))
    faces.sort(key=lambda f: f[0])
    xs, ys = [], []
    for _, rx, y, rz, *_ in faces:
        px, py = proj(rx, y, rz)
        xs.append(px)
        ys.append(py)
    minx, maxx, miny, maxy = min(xs) - 3 * scale, max(xs) + 3 * scale, min(ys) - 3 * scale, max(ys) + 3 * scale
    img = Image.new('RGB', (int(maxx - minx), int(maxy - miny)), BG)
    d = ImageDraw.Draw(img)
    shade = {'top': 1.0, '+x': 0.8, '+z': 0.64}
    corners = {
        'top': ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
        '+x': ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)),
        '+z': ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
    }
    for _, rx, y, rz, f, col, k, ao in faces:
        c = col
        sh = shade[f] * ao
        fill = tuple(max(0, min(255, int(v * sh))) for v in c)
        poly = [proj(rx + a, y + b, rz + cc) for a, b, cc in corners[f]]
        poly = [(px - minx, py - miny) for px, py in poly]
        d.polygon(poly, fill=fill, outline=fill)
    return img


def occlusion(vox, x, y, z, n):
    """Darker in grooves and creases: how much of the space in front of a face is filled (at block scale)."""
    nx, ny, nz = n
    if ny:
        t1, t2 = (1, 0, 0), (0, 0, 1)
    else:
        t1, t2 = (0, 1, 0), (-nz, 0, nx)
    filled = total = 0
    for dist in (3, 7):
        for a in (-1, 0, 1):
            for b in (-1, 0, 1):
                sx = x + nx * dist + (t1[0] * a + t2[0] * b) * dist
                sy = y + ny * dist + (t1[1] * a + t2[1] * b) * dist
                sz = z + nz * dist + (t1[2] * a + t2[2] * b) * dist
                total += 1
                if vox.get(sx, sy, sz):
                    filled += 1
    return 1.0 - 0.55 * filled / total


# ---------------------------------------------------------------- sheets

def font(size):
    for name in ('arial.ttf', 'segoeui.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def labelled(img, text, size=22):
    pad = size + 14
    out = Image.new('RGB', (img.width, img.height + pad), BG)
    out.paste(img, (0, pad))
    ImageDraw.Draw(out).text((8, 6), text, fill=(40, 30, 20), font=font(size))
    return out


def row(images, gap=20):
    h = max(i.height for i in images)
    w = sum(i.width for i in images) + gap * (len(images) + 1)
    out = Image.new('RGB', (w, h), BG)
    x = gap
    for i in images:
        out.paste(i, (x, h - i.height))
        x += i.width + gap
    return out


def pieces_sheet():
    items = [('full block', ('full',)), ('spire base', ('spire', 8, 6)), ('spire thick', ('spire', 6, 5)),
             ('spire middle', ('spire', 5, 4)), ('spire thin', ('spire', 4, 3)), ('spire tip', ('spire', 3, 0)),
             ('chimney mouth', ('mouth', 4)), ('ramp', ('slope', [(1, 0)], [])),
             ('ramp, outer corner', ('slope', [], [(1, 1)])), ('ramp, inner corner', ('slope', [(1, 0), (0, 1)], []))]
    panels = []
    for name, b in items:
        vox = Voxels()
        for u, v, w in piece_cubes(b):
            vox.set(u, v, w, MOUTHK if b[0] == 'mouth' else MOUND)
        panels.append(labelled(render(vox, 1.6, scale=12, turn=2), name, 18))
    # a stacked spire top: full blocks, then the five pieces
    vox = Voxels()
    stack = [('full',), ('full',), ('spire', 8, 6), ('spire', 6, 5), ('spire', 5, 4), ('spire', 4, 3), ('mouth', 3)]
    for y, b in enumerate(stack):
        for u, v, w in piece_cubes(b):
            vox.set(u, y * S + v, w, MOUTHK if b[0] == 'mouth' else MOUND)
    tall = labelled(render(vox, 7, scale=12, turn=2), 'a spire top: 2 full blocks, then the pieces', 18)
    return row([row(panels[:5]), ]), row(panels[5:] + [tall])


def main():
    os.makedirs(OUT, exist_ok=True)
    full = MoundShape(1.0)
    blocks = plan_blocks(full)
    print('full mound:', len(blocks), 'blocks,', round(full.height), 'tall')
    def toward_camera(turn, px=8, pz=6):
        for _ in range(turn):
            px, pz = pz, -px
        return px, pz

    for turn, name in ((2, 'full_front'), (0, 'full_back')):
        vox = build_voxels(blocks, player=toward_camera(turn))
        img = render(vox, full.height, scale=3, elevation=30, turn=turn)
        labelled(img, 'Full-grown mound, %d blocks tall (player for scale)' % round(full.height)).save(
            os.path.join(OUT, name + '.png'))
        print('wrote', name)
    vox = build_voxels(blocks, player=toward_camera(1, 9, 3))
    img = render(vox, full.height, scale=3, elevation=8, turn=1)
    labelled(img, 'From the ground: walking up to it').save(os.path.join(OUT, 'full_ground.png'))
    print('wrote full_ground')

    stages = []
    for g, text in ((0.15, 'young: low base, first spire'), (0.4, 'growing: first spires'), (0.7, 'big: buttresses, cluster'),
                    (1.0, 'full-grown')):
        sh = MoundShape(g)
        bl = plan_blocks(sh)
        v = build_voxels(bl, player=toward_camera(2))
        tall = max(y for _, y, _ in bl) + 1
        stages.append(labelled(render(v, sh.height, scale=2, elevation=30, turn=2), '%s (%d tall)' % (text, tall), 18))
        print('stage', g, len(bl), 'blocks')
    row(stages).save(os.path.join(OUT, 'stages.png'))

    top, bottom = pieces_sheet()
    w = max(top.width, bottom.width)
    sheet = Image.new('RGB', (w, top.height + bottom.height + 20), BG)
    sheet.paste(top, (0, 0))
    sheet.paste(bottom, (0, top.height + 20))
    sheet.save(os.path.join(OUT, 'pieces.png'))
    print('wrote pieces')


if __name__ == '__main__':
    main()
