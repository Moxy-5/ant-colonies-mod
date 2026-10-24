"""
The animation lab (2026-10-15): ways for Claude to SEE an animation before the user does. It reads the game's own
files (the GeckoLib model, its texture and its animation file), so it checks exactly what the game will play, whatever
tool wrote them. Species-neutral: any insect with a geo file works (worker_ant, termite_worker, ...).

What it makes (pictures go to references/anim_lab/, Claude looks at them with its Read tool):
  film      a film strip: the clip at evenly spaced moments, from the side, the front, above and three-quarter.
            Under a walking insect the floor grid moves at the walk's own speed (a treadmill), so a foot on the ground
            must stay on the same grid spot from frame to frame; each foot's path over the whole cycle is drawn on the
            floor in its leg's colour (a planted foot is one dot; a sliding one smears).
  graphs    every bone's turn (x red, y green, z blue) over the clip, the footfall pattern (the gait diagram biologists
            draw: a bar while a foot is on the ground), and each foot's height.
  check     the automatic checks: feet sliding, feet out of step with each other, fewer than 3 feet down while
            walking, parts sinking into the floor, legs passing through the body or each other, loop seams (a jump
            where a looping clip starts again), snaps (a joint turning implausibly fast); plus the real-ant numbers
            (stride, duty factor, steps a second at the game's walking speed).
  compare   several clips (or the same clip at several speeds) side by side, one row each.
  sheet     a contact sheet of screenshots (from the in-game camera) with their file names.
  match     each in-game camera picture (its clip moment read from times.txt) above the lab's drawing of the same
            moment from the same side, to prove the lab and the game agree.
  all       film + graphs + check for every clip of an insect.

Run from the project folder:
  python tools/anim_lab.py all worker_ant
  python tools/anim_lab.py film worker_ant walk [--frames 8]
  python tools/anim_lab.py compare worker_ant walk idle sleep
  python tools/anim_lab.py check worker_ant [walk]
  python tools/anim_lab.py sheet run-film/screenshots/<scene> [--cols 4]
  python tools/anim_lab.py match run-film/screenshots/<scene> worker_ant
(Each film scene's pictures are in run-film/screenshots/<scene>/; read run-film/screenshots/checks.txt first: the
camera's automatic checks name the pictures with a body part in a block or a sudden jump.)

Filming without relaunching the game (tools/film.sh, the camera's watch mode, 2026-10-18; full list in its top comment):
  tools/film.sh watch climb      open a film session (once, about 50 s to 90 s) and film the scene
  python tools/ant_models.py     change a clip: the open camera reloads the animation and model files by itself (0.1 s)
  tools/film.sh shoot            and films again: waits for the new pictures and prints checks.txt (the scene's own
                                 length, 20 s for work, 55 s for climb, instead of a whole launch)
  tools/film.sh shoot poses,work other scenes from now on, in the same session
  tools/film.sh swap             Java changes (the live layer, EdgeOver, ...): compiles and swaps new method code into
                                 the open game in about 3 s, then films; a change Java cannot swap relaunches by itself
  tools/film.sh stop             close it
Short scene names: climb = tools/film/climb.film, guard = the folder tools/film/guard; also with ./gradlew runFilm
-PfilmScene=climb.
Options: --anim <file stem> to read another insect's animation file (the queen uses worker_ant's), --speed <blocks a
second> for the walking speed the treadmill and the checks assume (default: the insect's normal speed below).

Speed (2026-10-18, idea M5 of docs/plans/animation-engine-upgrade-review.md): quick enough to run after every change.
  - Clips run side by side on several processes: --jobs <n> (default: half the computer's threads, 10 on the user's
    PC; --jobs 1 runs them one after another in this process, as before). The output comes clip by clip in the same
    order as ever.
  - `check` and `all` remember each clip's fingerprint (the lab's own code, the model's geo and texture, the clip's key
    frames, the options) in references/anim_lab/.lab_cache.json, and skip a clip whose fingerprint is unchanged since
    its last run: its findings are printed from memory (the check is exact, so the same clip gives the same findings),
    and `all` keeps its pictures ("kept ...") unless they were written over since. --fresh runs every clip again.
    The last line before the problem count says how many clips ran, on how many processes, and how long it took.
  - The per-frame work is faster with the same numbers: each pose's bone turns are worked out once instead of per
    point, a box's sums once instead of per point tested, key frames found by halving, every sum still done the same
    way (Python's sum(), which since 3.12 adds floats with a correction term, so a written-out a + b + c would differ in
    the last digit). Proof kept with the change (docs/HISTORY.md): every check line and every picture of the worker and
    the five termite models came out byte for byte the same as before, and a deliberately sliding foot is still caught.
  Times for the worker's 17 clips: `check` 45 s before; now 6.4 s on one process, 1.6 s on 10, 0.35 s when nothing
  changed. `all` 115 s before; now 36 s on one process, 7 s on 10.

Conventions (the same as tools/ant_models.py, which matched the game): units are model pixels, 16 to a block before
the renderer's scale; the head points to -Z; the ground is y = 0. A bone turns about its pivot, X first, then Y, then
Z; an animation's X and Z turn the other way round from the right-hand rule (as in Blockbench). Easing between two key
frames is the later key frame's (GeckoLib's rule); a looping clip holds its last key frame until its length.
No libraries needed: the PNG reader and writer and the little renderer are plain Python.
"""
import bisect, hashlib, json, math, os, re, struct, sys, time, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import termite_models as tm  # noqa: E402  (the PNG writer, the canvas and its font)
import body_plan  # noqa: E402  (what each model's parts are: tools/body_plan.py)
import mod_paths  # noqa: E402  (which mod holds each model: tools/mod_paths.py)

PROJECT = os.path.normpath(os.path.join(HERE, '..'))


def model_file(name, *parts):
    """A file of a model or clip set in the mod that holds it (a name no mod holds: where the savanna's would be, so
    opening it gives the plain file-not-found error)."""
    try:
        base = mod_paths.model_assets(name)
    except FileNotFoundError:
        base = mod_paths.assets('savanna')
    return os.path.join(base, *parts)

OUT = os.path.join(PROJECT, 'references', 'anim_lab')

# The renderer's scale for each model (client/ClientEvents.java and TermiteQueenModel.RENDER_SCALE).
RENDER_SCALE = {'worker_ant': 0.65, 'queen_ant': 1.3, 'termite_king': 0.75, 'termite_queen': 0.75,
                'termite_worker': 0.75, 'termite_minor_soldier': 0.75, 'termite_major_soldier': 0.75,
                # the Matabele (2026-10-23, in the game since the same day, SavannaClientEvents): the major twice a termite major soldier's length,
                # the minor 45% of it, the model viewer's starting sizes (the sizes are still the user's to pick)
                'matabele_major': 0.95, 'matabele_minor': 0.455,
                # the queen (2026-10-24): her body 1.2 times a major's, the user's pick ("because it will be cool")
                'matabele_queen': 1.14}
# Normal walking speed in blocks a second (my estimate from the movement speed attribute: a ground mob's top speed is
# about attribute x 0.25 / 0.454 blocks a tick at the normal speed modifier; the worker's 0.25 gives about 2.75 b/s,
# about 6 body lengths a second, which is what real Pogonomyrmex walk). The in-game camera can measure the real one.
WALK_SPEED = {'worker_ant': 2.75, 'queen_ant': 0.9, 'termite_worker': 2.4, 'termite_king': 1.3, 'termite_queen': 0.9,
              'termite_minor_soldier': 2.6, 'termite_major_soldier': 1.9,
              # Matabele raid columns: majors and minors march together at one speed, about 2 major body lengths a
              # second (my estimate from Frank's raid films, to confirm; the game's speed is set when the ant goes in)
              'matabele_major': 3.0, 'matabele_minor': 3.0,
              # the queen walks with her colony when it moves house, slower than a raid (my pick, to confirm)
              'matabele_queen': 1.5}

LEG_COLOURS = {'l1': (220, 40, 40), 'l2': (240, 150, 20), 'l3': (200, 190, 30),
               'r1': (40, 90, 230), 'r2': (30, 170, 200), 'r3': (150, 60, 210)}
AXIS_COLOURS = [(210, 40, 40), (30, 150, 40), (40, 80, 220)]
GRID = (178, 172, 160)
GRID_BOLD = (150, 144, 132)
BAD = (230, 20, 20)

# --- the font: the termite tool's, plus the letters and signs it lacks -------------------------------------------
FONT = dict(tm.FONT)
FONT.update({
    'X': ['10001', '10001', '01010', '00100', '01010', '10001', '10001'],
    'Z': ['11111', '00001', '00010', '00100', '01000', '10000', '11111'],
    '.': ['00000', '00000', '00000', '00000', '00000', '01100', '01100'],
    ':': ['00000', '01100', '01100', '00000', '01100', '01100', '00000'],
    '/': ['00001', '00010', '00010', '00100', '01000', '01000', '10000'],
    '+': ['00000', '00100', '00100', '11111', '00100', '00100', '00000'],
    '=': ['00000', '00000', '11111', '00000', '11111', '00000', '00000'],
    '_': ['00000', '00000', '00000', '00000', '00000', '00000', '11111'],
    '>': ['01000', '00100', '00010', '00001', '00010', '00100', '01000'],
    '<': ['00010', '00100', '01000', '10000', '01000', '00100', '00010'],
})


# --- PNG reading ----------------------------------------------------------------------------------------------------
def read_png(path):
    """(width, height, pixels) with pixels a list of (r, g, b, a). 8-bit, not interlaced (what the game and our tools
    write)."""
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'{path}: not a PNG')
    pos, idat, palette, trns = 8, bytearray(), None, None
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b'IHDR':
            w, h, depth, ctype, _, _, interlace = struct.unpack('>IIBBBBB', body)
        elif kind == b'PLTE':
            palette = body
        elif kind == b'tRNS':
            trns = body
        elif kind == b'IDAT':
            idat += body
        elif kind == b'IEND':
            break
    if depth != 8 or interlace:
        raise ValueError(f'{path}: only 8-bit, non-interlaced PNGs')
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(bytes(idat))
    stride = w * ch
    prev = bytearray(stride)
    out = []
    i = 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        if f == 1:
            for x in range(ch, stride):
                line[x] = (line[x] + line[x - ch]) & 255
        elif f == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                left = line[x - ch] if x >= ch else 0
                line[x] = (line[x] + ((left + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                b = prev[x]
                c = prev[x - ch] if x >= ch else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        for x in range(0, stride, ch):
            if ctype == 6:
                out.append(tuple(line[x:x + 4]))
            elif ctype == 2:
                out.append((line[x], line[x + 1], line[x + 2], 255))
            elif ctype == 0:
                out.append((line[x], line[x], line[x], 255))
            elif ctype == 4:
                out.append((line[x], line[x], line[x], line[x + 1]))
            else:
                k = line[x]
                alpha = trns[k] if trns and k < len(trns) else 255
                out.append((palette[3 * k], palette[3 * k + 1], palette[3 * k + 2], alpha))
        prev = line
    return w, h, out


# --- the model ----------------------------------------------------------------------------------------------------
def turn(p, rot):
    """Turns p = (x, y, z) about the origin by rot = [x, y, z] degrees: X first, then Y, then Z (the model file's
    rules, as tools/ant_models.rotate)."""
    x, y, z = p
    rx, ry, rz = (math.radians(v) for v in rot)
    if rx:
        c, s = math.cos(-rx), math.sin(-rx)
        y, z = y * c - z * s, y * s + z * c
    if ry:
        c, s = math.cos(ry), math.sin(ry)
        x, z = x * c + z * s, -x * s + z * c
    if rz:
        c, s = math.cos(-rz), math.sin(-rz)
        x, y = x * c - y * s, x * s + y * c
    return (x, y, z)


def box_uv_faces(uv, size):
    """Blockbench's box UV layout: each face's (u, v, width, height) for a cube of this size."""
    u, v = uv
    sx, sy, sz = (math.floor(s) or 1 for s in size)
    return {'east': (u, v + sz, sz, sy), 'north': (u + sz, v + sz, sx, sy), 'west': (u + sz + sx, v + sz, sz, sy),
            'south': (u + 2 * sz + sx, v + sz, sx, sy), 'up': (u + sz, v, sx, sz), 'down': (u + sz + sx, v, sx, sz)}


# Corner k of a cube is (x, y, z) = bits (k >> 2 & 1, k >> 1 & 1, k & 1) of its size. Faces as corner lists.
FACE_CORNERS = {'north': (0, 2, 6, 4), 'south': (1, 5, 7, 3), 'west': (0, 1, 3, 2), 'east': (4, 6, 7, 5),
                'down': (0, 4, 5, 1), 'up': (2, 3, 7, 6)}


def inside(pt, box):
    """Whether a point is inside a box given as (a corner, its three edges, their squared lengths)."""
    o, edges, lens = box
    d = tuple(p - q for p, q in zip(pt, o))
    return all(ln > 0 and 0.0 <= sum(a * b for a, b in zip(d, e)) <= ln for e, ln in zip(edges, lens))


class Model:
    def __init__(self, name, bones=None):
        """The insect's model from the game's geo file, or from a list of bones (the motion library, tools/ant_motion.py,
        works on the model before the file is written). With a body plan (tools/body_plan.py), the bones are known by
        their role names inside the lab: a geo file's bones are renamed to their roles as they are read (a list of
        bones given here is the tools' own, already in role names)."""
        self.name = name
        self.plan = body_plan.load(name)
        if bones is None:
            geo = json.load(open(model_file(name, 'geo', 'entity', f'{name}.geo.json'), encoding='utf-8'))
            g = geo['minecraft:geometry'][0]
            desc = g['description']
            self.bones = self.plan.model_to_roles(g['bones']) if self.plan else g['bones']
        else:
            desc, self.bones = {}, bones
        self.by = {b['name']: b for b in self.bones}
        tex_path = model_file(name, 'textures', 'entity', f'{name}.png')
        tex = read_png(tex_path) if os.path.exists(tex_path) else None
        tw, th = desc.get('texture_width', 64), desc.get('texture_height', 64)
        self.scale = RENDER_SCALE.get(name, 1.0)
        self.cubes = []   # (bone name, [8 rest corners], {face: colour})
        for b in self.bones:
            for c in b.get('cubes', []):
                (ox, oy, oz), size = c['origin'], c['size']
                inf = c.get('inflate', 0)
                lo = (ox - inf, oy - inf, oz - inf)
                sz = [s + 2 * inf for s in size]
                corners = [(lo[0] + (k >> 2 & 1) * sz[0], lo[1] + (k >> 1 & 1) * sz[1], lo[2] + (k & 1) * sz[2])
                           for k in range(8)]
                if 'rotation' in c:
                    piv = c.get('pivot', [0, 0, 0])
                    corners = [tuple(q + v for q, v in zip(piv, turn(tuple(a - q for a, q in zip(p, piv)), c['rotation'])))
                               for p in corners]
                faces = c['uv'] if isinstance(c.get('uv'), dict) else box_uv_faces(c.get('uv', [0, 0]), size)
                colours = {}
                for f in FACE_CORNERS:
                    rect = faces.get(f)
                    if isinstance(rect, dict):
                        rect = (*rect['uv'], *rect.get('uv_size', [1, 1]))
                    colours[f] = average_colour(tex, rect, tw, th) if tex and rect else (150, 120, 100)
                self.cubes.append((b['name'], corners, colours))
        self.feet = self._find_feet()
        zs = [p[2] for _, cs, _ in self.cubes for p in cs]
        self.length = max(zs) - min(zs)              # body length in model units
        self.blocks_per_unit = self.scale / 16.0

    def chain(self, name):
        out = []
        while name:
            out.append(name)
            name = self.by[name].get('parent')
        return out

    def leg_of(self, bone):
        """The key of the leg (l1, ...) a bone belongs to, or None."""
        if self.plan:
            return self.plan.leg_of(bone)
        m = re.match(r'leg_([lr])(\d)(_lower|_foot)?$', bone)
        return m.group(1) + m.group(2) if m else None

    def hip_of(self, key):
        return self.plan.legs[key][0] if self.plan else f'leg_{key}'

    def is_antenna(self, bone):
        return bone in self.plan.antenna_bones() if self.plan else bone.startswith('antenna')

    def transforms(self, pose, names=None):
        """Each bone's move in this pose (or only the named bones'), worked out once (its pivot, scale, the sines and
        cosines of its turns, its offset), so that placing many points of one pose does not redo them per point.
        world() with these gives exactly the same numbers as without (the same sums in the same order)."""
        xf = {}
        for name in self.by if names is None else names:
            b = self.by[name]
            piv = b.get('pivot', [0, 0, 0])
            rot, pos, scl = pose.get(name, ((0, 0, 0), (0, 0, 0), (1, 1, 1)))
            rest = b.get('rotation')
            if rest:
                rot = tuple(a + r for a, r in zip(rot, rest))
            rx, ry, rz = (math.radians(v) for v in rot)
            xf[name] = (piv[0], piv[1], piv[2], scl[0], scl[1], scl[2],
                        (math.cos(-rx), math.sin(-rx)) if rx else None,
                        (math.cos(ry), math.sin(ry)) if ry else None,
                        (math.cos(-rz), math.sin(-rz)) if rz else None,
                        pos[0], pos[1], pos[2])
        return xf

    def _chain(self, bone):
        chains = self.__dict__.setdefault('_chains', {})
        names = chains.get(bone)
        if names is None:
            names = chains[bone] = self.chain(bone)
        return names

    def _steps(self, bone, xf):
        return [xf[n] for n in self._chain(bone)]

    def _find_feet(self):
        """Each leg's last bone (foot, else lower leg, else leg) and the rest corner of it that touches the ground."""
        legs = {}
        if self.plan:
            for key, chain in self.plan.legs.items():
                with_cubes = [b for b in chain if self.by.get(b, {}).get('cubes')]
                if with_cubes:
                    legs[key] = (0, with_cubes[-1])
        for b in self.bones if not self.plan else ():
            m = re.match(r'leg_([lr])(\d)(_lower|_foot)?$', b['name'])
            if m and b.get('cubes'):
                key = m.group(1) + m.group(2)
                rank = {'': 0, '_lower': 1, '_foot': 2}[m.group(3) or '']
                if key not in legs or rank > legs[key][0]:
                    legs[key] = (rank, b['name'])
        feet = {}
        for key, (_, bone) in sorted(legs.items()):
            best = None
            for i, (bn, corners, _) in enumerate(self.cubes):
                if bn != bone:
                    continue
                for k, p in enumerate(corners):
                    reach = p[0] * p[0] + p[2] * p[2]
                    score = (round(p[1], 2), -reach)
                    if best is None or score < best[0]:
                        best = (score, i, k)
            if best:
                feet[key] = (bone, best[1], best[2])
        return feet

    def world(self, bone, p, pose, xf=None):
        """Where point p of bone ends up in the given pose (bone -> (rot, pos, scale)); xf: the pose's transforms(),
        if already worked out."""
        return place(self._steps(bone, xf or self.transforms(pose, self._chain(bone))), p)

    def posed(self, pose, xf=None):
        """Every cube's 8 corners in the pose, with its face colours and bone."""
        xf = xf or self.transforms(pose)
        out = []
        for bn, corners, colours in self.cubes:
            steps = self._steps(bn, xf)
            out.append((bn, [place(steps, p) for p in corners], colours))
        return out

    def weights(self):
        """Each cube's weight for the centre of weight: 0 for a leg's, else its own volume less the part of it already
        inside an earlier cube, so every bit of the body counts once (2026-10-18: the termite queen's skin is boxes laid
        crossways over each other; weighed by the upright box round each, as before, her abdomen counted several times
        over and her centre of weight sat far behind where it is). Worked out once, at rest, on a grid of points in each
        cube (my pick: the parts are all equally dense; real legs weigh little)."""
        if getattr(self, '_weights', None) is None:
            boxes, out = [], []
            for bone, c, _ in self.cubes:
                o = c[0]
                edges = [tuple(b - a for a, b in zip(o, c[k])) for k in (4, 2, 1)]
                lens = [sum(e * e for e in edge) for edge in edges]
                if self.leg_of(bone) is not None:
                    out.append(0.0)
                    continue
                n = 6
                own = 0
                for i in range(n):
                    for j in range(n):
                        for k in range(n):
                            f = ((i + 0.5) / n, (j + 0.5) / n, (k + 0.5) / n)
                            pt = tuple(o[d] + sum(f[e] * edges[e][d] for e in range(3)) for d in range(3))
                            if not any(inside(pt, box) for box in boxes):
                                own += 1
                volume = math.sqrt(lens[0] * lens[1] * lens[2])
                out.append(max(1e-6, volume) * own / n ** 3)
                boxes.append((o, edges, lens))
            self._weights = out
        return self._weights

    def centre(self, pose):
        """The body's centre of weight seen from above (x, z) in a pose: the middle of every cube that is not a leg,
        each weighed by its share of the body (see weights)."""
        total = sx = sz = 0.0
        for (bone, corners, _), v in zip(self.cubes, self.weights()):
            if v <= 0.0:
                continue
            if pose:
                corners = [self.world(bone, p, pose) for p in corners]
            xs, ys, zs = zip(*corners)
            total += v
            sx += v * sum(xs) / 8
            sz += v * sum(zs) / 8
        return (sx / total, sz / total) if total else (0.0, 0.0)

    def foot_tips(self, pose, xf=None):
        xf = xf or self.transforms(pose)
        return {key: place(self._steps(bone, xf), self.cubes[i][1][k]) for key, (bone, i, k) in self.feet.items()}


def place(steps, p):
    """Point p moved through a bone chain's steps (Model.transforms), the bone itself first: per bone, scale about the
    pivot, turn X then Y then Z (as turn()), then back to the pivot plus the bone's offset. Written out by hand for
    speed; every sum is the one the old per-point code did, in the same order, so the numbers are identical."""
    x, y, z = p
    for c0, c1, c2, s0, s1, s2, rx, ry, rz, d0, d1, d2 in steps:
        x, y, z = (x - c0) * s0, (y - c1) * s1, (z - c2) * s2
        if rx:
            c, s = rx
            y, z = y * c - z * s, y * s + z * c
        if ry:
            c, s = ry
            x, z = x * c + z * s, -x * s + z * c
        if rz:
            c, s = rz
            x, y = x * c - y * s, x * s + y * c
        x, y, z = x + c0 + d0, y + c1 + d1, z + c2 + d2
    return (x, y, z)


def average_colour(tex, rect, tw, th):
    w, h, px = tex
    u, v, uw, vh = rect
    sx, sy = w / tw, h / th
    x0, y0 = int(min(u, u + uw) * sx), int(min(v, v + vh) * sy)
    x1, y1 = max(x0 + 1, int(max(u, u + uw) * sx)), max(y0 + 1, int(max(v, v + vh) * sy))
    tot, n = [0, 0, 0], 0
    for y in range(max(0, y0), min(h, y1)):
        for x in range(max(0, x0), min(w, x1)):
            r, g, b, a = px[y * w + x]
            if a > 20:
                tot[0] += r; tot[1] += g; tot[2] += b; n += 1
    return tuple(c // n for c in tot) if n else (150, 120, 100)


# --- the animation: GeckoLib's rules ---------------------------------------------------------------------------------
def ease(name, f):
    n = (name or 'linear').lower()
    if n == 'linear':
        return f
    if n == 'step':
        return 0.0 if f < 1 else 1.0
    kind = n.replace('easeinout', 'io:').replace('easein', 'i:').replace('easeout', 'o:')
    mode, _, curve = kind.partition(':')
    base = {'sine': lambda x: 1 - math.cos(x * math.pi / 2), 'quad': lambda x: x ** 2, 'cubic': lambda x: x ** 3,
            'quart': lambda x: x ** 4, 'quint': lambda x: x ** 5,
            'expo': lambda x: 0 if x == 0 else 2 ** (10 * x - 10), 'circ': lambda x: 1 - math.sqrt(max(0.0, 1 - x * x)),
            'back': lambda x: 2.70158 * x ** 3 - 1.70158 * x ** 2}.get(curve)
    if base is None or mode not in ('i', 'o', 'io'):
        raise ValueError(f'easing "{name}" is not known to the lab yet')
    if mode == 'i':
        return base(f)
    if mode == 'o':
        return 1 - base(1 - f)
    return base(2 * f) / 2 if f < 0.5 else 1 - base(2 - 2 * f) / 2


MOLANG_OK = re.compile(r'^[0-9a-z_.+\-*/()<>=!, ]*$')


def molang(expr, t):
    """A small part of Molang: numbers, + - * /, math.sin/cos (degrees), math.abs/min/max/clamp/pow/sqrt/lerp, and
    q.anim_time / query.anim_time. Enough to check clips that use formulas."""
    e = expr.lower().strip()
    if not MOLANG_OK.match(e):
        raise ValueError(f'Molang the lab cannot read: {expr}')
    e = e.replace('query.', 'q.')
    names = {'q_anim_time': t, 'math_sin': lambda a: math.sin(math.radians(a)),
             'math_cos': lambda a: math.cos(math.radians(a)), 'math_abs': abs, 'math_min': min, 'math_max': max,
             'math_clamp': lambda v, a, b: max(a, min(b, v)), 'math_pow': pow, 'math_sqrt': math.sqrt,
             'math_lerp': lambda a, b, f: a + (b - a) * f, 'math_pi': math.pi}
    e = e.replace('q.anim_time', 'q_anim_time').replace('math.', 'math_')
    return float(eval(e, {'__builtins__': {}}, names))


def value(v, t):
    if isinstance(v, (int, float)):
        return [float(v)] * 3
    if isinstance(v, str):
        return [molang(v, t)] * 3
    return [molang(x, t) if isinstance(x, str) else float(x) for x in v]


def keyframes(channel):
    """[(time, pre, post, easing)] sorted, from any of the file's forms."""
    if isinstance(channel, (list, int, float, str)):
        return Keys([(0.0, channel, channel, 'linear')])
    if 'vector' in channel:
        return Keys([(0.0, channel['vector'], channel['vector'], channel.get('easing', 'linear'))])
    out = []
    for k, f in channel.items():
        if isinstance(f, dict):
            if 'vector' in f:
                pre = post = f['vector']
            else:
                pre, post = f.get('pre', f.get('post')), f.get('post', f.get('pre'))
            easing = f.get('easing') or ('catmullrom' if f.get('lerp_mode') == 'catmullrom' else 'linear')
        else:
            pre = post = f
            easing = 'linear'
        out.append((float(k), pre, post, easing))
    return Keys(sorted(out, key=lambda x: x[0]))


class Keys(list):
    """A channel's key frames, with their times kept beside them for sample_channel's search."""
    def __init__(self, frames):
        super().__init__(frames)
        self.times = [f[0] for f in frames]


def sample_channel(frames, t):
    if t <= frames[0][0]:
        return value(frames[0][1], t)
    # The first pair of key frames with t0 <= t <= t1: since t is past the first key, that is the pair ending at the
    # first key at or after t (found by halving instead of walking every key from the start; the same pair).
    times = getattr(frames, 'times', None) or [f[0] for f in frames]
    j = bisect.bisect_left(times, t, 1)
    for i in range(j - 1, len(frames) - 1):
        t0, _, post0, _ = frames[i]
        t1, pre1, _, easing = frames[i + 1]
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            a, b = value(post0, t), value(pre1, t)
            if easing.lower() == 'catmullrom':
                pa = value(frames[max(0, i - 1)][2], t)
                pb = value(frames[min(len(frames) - 1, i + 2)][1], t)
                f2, f3 = f * f, f * f * f
                return [0.5 * (2 * q1 + (-q0 + q2) * f + (2 * q0 - 5 * q1 + 4 * q2 - q3) * f2 + (-q0 + 3 * q1 - 3 * q2 + q3) * f3)
                        for q0, q1, q2, q3 in zip(pa, a, b, pb)]
            e = ease(easing, f)
            return [x + (y - x) * e for x, y in zip(a, b)]
    return value(frames[-1][2], t)


class Clip:
    def __init__(self, name, data):
        self.name = name
        self.loop = data.get('loop') in (True, 'loop', 'true')
        self.bones = {}
        last = 0.0
        for bone, channels in data.get('bones', {}).items():
            for ch in ('rotation', 'position', 'scale'):
                if ch in channels:
                    frames = keyframes(channels[ch])
                    self.bones.setdefault(bone, {})[ch] = frames
                    last = max(last, frames[-1][0])
        self.length = float(data.get('animation_length', last)) or 1.0
        # The clip's own notes (GeckoLib's "timeline"): a walk says how far it carries the insect, in model units per
        # clip ("stride 6.03"), so the game plays it at the speed that keeps the feet planted.
        self.notes = {k: (v if isinstance(v, str) else ' '.join(v)) for k, v in data.get('timeline', {}).items()}
        self.stride = next((float(w.split()[1]) for w in self.notes.values() if w.startswith('stride ')), None)
        # A turn on the spot says how far it turns the insect in one play (degrees, positive to the right: "turn 50").
        self.turn = next((float(w.split()[1]) for w in self.notes.values() if w.startswith('turn ')), None)
        # A walk made for a band of speeds says which (model units a second: "speeds 19.56 45.23").
        self.speeds = next((tuple(map(float, w.split()[1:3])) for w in self.notes.values() if w.startswith('speeds ')), None)

    def pose(self, t):
        if self.loop:
            t = t % self.length
        out = {}
        for bone, chans in self.bones.items():
            rot = sample_channel(chans['rotation'], t) if 'rotation' in chans else (0, 0, 0)
            pos = sample_channel(chans['position'], t) if 'position' in chans else (0, 0, 0)
            scl = sample_channel(chans['scale'], t) if 'scale' in chans else (1, 1, 1)
            out[bone] = (tuple(rot), tuple(pos), tuple(scl))
        return out


def load_clips(stem, model=None, plan=None):
    """The clips of an animation file; with the model they play on, their bones renamed to the model's role names."""
    data = json.load(open(model_file(stem, 'animations', 'entity', f'{stem}.animation.json'), encoding='utf-8'))
    plan = model.plan if model is not None else plan
    return {name: Clip(name, plan.clip_to_roles(a) if plan else a) for name, a in data['animations'].items()}


# --- balance: is the body's weight over its feet ---------------------------------------------------------------------
def hull(points):
    """The corners of the smallest convex shape round 2D points, anticlockwise (Andrew's monotone chain)."""
    pts = sorted(set(points))
    if len(pts) <= 2:
        return pts

    def half(seq):
        out = []
        for p in seq:
            while len(out) >= 2 and ((out[-1][0] - out[-2][0]) * (p[1] - out[-2][1])
                                     - (out[-1][1] - out[-2][1]) * (p[0] - out[-2][0])) <= 0:
                out.pop()
            out.append(p)
        return out
    lower, upper = half(pts), half(reversed(pts))
    return lower[:-1] + upper[:-1]


def support_margin(centre, feet):
    """How far a point (x, z) lies inside the shape the feet on the ground stand round (units; negative: outside, by
    that much). Fewer than 3 feet make no shape: the point is outside by its distance from them."""
    shape = hull(feet)
    if not shape:
        return -1e9

    def seg_dist(p, a, b):
        ax, az = b[0] - a[0], b[1] - a[1]
        f = max(0.0, min(1.0, ((p[0] - a[0]) * ax + (p[1] - a[1]) * az) / max(1e-12, ax * ax + az * az)))
        return math.hypot(p[0] - a[0] - ax * f, p[1] - a[1] - az * f)
    if len(shape) < 3:
        return -min(seg_dist(centre, shape[0], shape[-1]), math.hypot(centre[0] - shape[0][0], centre[1] - shape[0][1]))
    inside = True
    near = 1e9
    for a, b in zip(shape, shape[1:] + shape[:1]):
        cross_ = (b[0] - a[0]) * (centre[1] - a[1]) - (b[1] - a[1]) * (centre[0] - a[0])
        inside = inside and cross_ >= 0
        near = min(near, seg_dist(centre, a, b))
    return near if inside else -near


# --- walking: what the feet do ---------------------------------------------------------------------------------------
class Gait:
    """The feet through one clip: planted or not, and the treadmill speed (model units a second) that keeps planted
    feet still on the ground."""
    STEPS = 120

    def __init__(self, model, clip):
        self.model, self.clip = model, clip
        n = self.STEPS
        self.times = [clip.length * i / n for i in range(n + 1)]
        self.tips = [model.foot_tips(clip.pose(t)) for t in self.times]
        legs = list(model.feet)
        self.legs = legs
        low = min(tips[k][1] for tips in self.tips for k in legs) if legs else 0.0
        self.ground = low
        # A foot is down when its tip is within 0.15 units of the lowest any foot gets (a lifting foot is up at once).
        self.down = {k: [tips[k][1] <= low + 0.15 for tips in self.tips] for k in legs}
        # The treadmill: planted feet move back (+Z) in the body's frame at the walk's speed.
        speeds = []
        dt = clip.length / n
        for k in legs:
            for i in range(n):
                if self.down[k][i] and self.down[k][i + 1]:
                    speeds.append((self.tips[i + 1][k][2] - self.tips[i][k][2]) / dt)
        moving = [s for s in speeds if abs(s) > 1e-3]
        self.speed = sum(moving) / len(moving) if moving and len(moving) > len(speeds) * 0.3 else 0.0
        # Walking only when most feet travel back while down: a clip where only the front feet move on the ground (an
        # ant raking soil while it digs) is standing, not walking.
        travelling = 0
        for k in legs:
            mine = [(self.tips[i + 1][k][2] - self.tips[i][k][2]) / dt for i in range(n) if self.down[k][i] and self.down[k][i + 1]]
            if mine and abs(sum(mine) / len(mine)) > 0.5:
                travelling += 1
        self.walking = abs(self.speed) > 0.5 and travelling > len(legs) // 2
        if not self.walking:
            self.speed = 0.0

    def stride(self):
        """Ground covered in one cycle, in model units."""
        return abs(self.speed) * self.clip.length


def gait_of(model, clip):
    """The clip's Gait on this model, worked out once and kept on the clip (film, graphs and check all need it)."""
    kept = clip.__dict__.setdefault('_gaits', {})
    g = kept.get(id(model))
    if g is None or g.model is not model:
        g = kept[id(model)] = Gait(model, clip)
    return g


# --- drawing ------------------------------------------------------------------------------------------------------
class Canvas(tm.Canvas):
    def text(self, x, y, words, scale=2, rgb=(40, 30, 25)):
        for ch in str(words).upper():
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

    def save(self, path):
        """The same PNG file, byte for byte, as termite_models.write_png writes (each pixel's colour turned into its
        bytes once per colour instead of once per pixel)."""
        w, h = self.width, self.height
        lut = {}
        rows = []
        for y in range(h):
            line = self.color[y * w:(y + 1) * w]
            for c in set(line).difference(lut):
                lut[c] = bytes(c + (255,))
            rows.append(b'\x00' + b''.join(map(lut.__getitem__, line)))
        raw = b''.join(rows)

        def chunk(kind, data):
            return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
        png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'wb') as f:
            f.write(png)

    def put(self, x, y, rgb, z=None):
        if 0 <= x < self.width and 0 <= y < self.height:
            i = y * self.width + x
            if z is None or z <= self.depth[i]:
                self.color[i] = rgb

    def line(self, a, b, rgb, clip, width=1, depth=True):
        (ax, ay, az), (bx, by, bz) = a, b
        x0, y0, w, h = clip
        n = int(max(abs(bx - ax), abs(by - ay))) + 1
        for i in range(n + 1):
            f = i / n
            x, y = ax + (bx - ax) * f, ay + (by - ay) * f
            z = az + (bz - az) * f if depth else None
            for dx in range(width):
                for dy in range(width):
                    px, py = int(x) + dx - width // 2, int(y) + dy - width // 2
                    if x0 <= px < x0 + w and y0 <= py < y0 + h:
                        self.put(px, py, rgb, z)

    def dot(self, x, y, r, rgb, clip):
        x0, y0, w, h = clip
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r + 0.5:
                    px, py = int(x) + dx, int(y) + dy
                    if x0 <= px < x0 + w and y0 <= py < y0 + h:
                        self.put(px, py, rgb)

    def rect(self, x, y, w, h, rgb):
        for py in range(y, y + h):
            for px in range(x, x + w):
                self.put(px, py, rgb)


def normalize(v):
    n = math.sqrt(sum(a * a for a in v)) or 1.0
    return tuple(a / n for a in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot3(a, b):
    # sum() of the three products, not a + b + c: since Python 3.12 sum() adds floats with a correction term
    # (compensated summation), so a written-out a + b + c can differ in the last digit and flip a pixel where two faces
    # lie exactly on each other. This is the old sum(x * y for x, y in zip(a, b)) without the slow generator.
    return sum((a[0] * b[0], a[1] * b[1], a[2] * b[2]))


class View:
    """An orthographic camera looking along `look`."""
    def __init__(self, title, look, up=(0, 1, 0)):
        self.title = title
        self.d = normalize(look)
        if abs(dot3(self.d, normalize(up))) > 0.99:
            up = (0, 0, -1)
        self.right = normalize(cross(self.d, up))
        self.up = cross(self.right, self.d)

    def project(self, p):
        return (dot3(p, self.right), dot3(p, self.up), dot3(p, self.d))


VIEWS = {
    'side': View('SIDE', (-1, 0, 0)),
    'front': View('FRONT', (0, 0, 1)),
    'top': View('ABOVE', (0, -1, 0), up=(0, 0, -1)),
    'three': View('3/4', (-0.75, -0.55, 0.75)),
}
LIGHT = normalize((0.45, 0.8, -0.4))


class Frame:
    """A cell on the canvas with a fitted projection: model point -> pixel."""
    def __init__(self, canvas, cell, view, bounds, ppx=None):
        self.canvas, self.cell, self.view = canvas, cell, view
        (x0, y0, w, h) = cell
        (lx, hx), (ly, hy) = bounds
        self.ppx = ppx or min((w - 16) / max(hx - lx, 1e-3), (h - 16) / max(hy - ly, 1e-3))
        self.cx, self.cy = x0 + w / 2 - (lx + hx) / 2 * self.ppx, y0 + h / 2 + (ly + hy) / 2 * self.ppx

    def px(self, p):
        a, b, z = self.view.project(p)
        return (self.cx + a * self.ppx, self.cy - b * self.ppx, z)

    def boxes(self, cubes, tint=None):
        cv, (x0, y0, w, h) = self.canvas, self.cell
        width = cv.width
        for _, corners, colours in cubes:
            pts = [self.px(p) for p in corners]
            for face, idx in FACE_CORNERS.items():
                a, b, c, d = (corners[i] for i in idx)
                n = normalize(cross(tuple(q - p for p, q in zip(a, b)), tuple(q - p for p, q in zip(a, d))))
                shade = 0.55 + 0.45 * abs(dot3(n, LIGHT))
                rgb = colours[face]
                if tint:
                    rgb = tuple(int(c * 0.35 + t * 0.65) for c, t in zip(rgb, tint))
                col = tuple(min(255, int(ch * shade)) for ch in rgb)
                q = [pts[i] for i in idx]
                depth, colour = cv.depth, cv.color
                for tri in ((q[0], q[1], q[2]), (q[0], q[2], q[3])):
                    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = tri
                    area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
                    if abs(area) < 1e-9:
                        continue
                    # The same sums per pixel as ever, the parts that only change per row or per column worked out
                    # once (so the picture is exactly the same).
                    xs = range(max(x0, int(min(ax, bx, cx))), min(x0 + w, int(max(ax, bx, cx)) + 1))
                    cols = [(px, px + 0.5) for px in xs]
                    cols = [(px, bx - sx, cx - sx, ax - sx) for px, sx in cols]
                    for py in range(max(y0, int(min(ay, by, cy))), min(y0 + h, int(max(ay, by, cy)) + 1)):
                        sy = py + 0.5
                        cys, bys, ays = cy - sy, by - sy, ay - sy
                        row = py * width
                        for px, bxs, cxs, axs in cols:
                            w0 = (bxs * cys - cxs * bys) / area
                            if w0 < 0:
                                continue
                            w1 = (cxs * ays - axs * cys) / area
                            if w1 < 0:
                                continue
                            w2 = 1 - w0 - w1
                            if w2 < 0:
                                continue
                            zz = w0 * az + w1 * bz + w2 * cz
                            i = row + px
                            if zz < depth[i]:
                                depth[i] = zz
                                colour[i] = col

    def grid(self, ground, shift, extent, step=1.0, bold=4):
        """The floor: lines every `step` units (every `bold`-th darker), moved `shift` units along +Z (the treadmill)."""
        (lx, hx), (lz, hz) = extent
        k0, k1 = math.floor((lz - shift) / step), math.ceil((hz - shift) / step)
        for k in range(k0, k1 + 1):
            z = k * step + shift
            col = GRID_BOLD if k % bold == 0 else GRID
            self.canvas.line(self.px((lx, ground, z)), self.px((hx, ground, z)), col, self.cell)
        for k in range(math.floor(lx / step), math.ceil(hx / step) + 1):
            x = k * step
            col = GRID_BOLD if k % bold == 0 else GRID
            self.canvas.line(self.px((x, ground, lz)), self.px((x, ground, hz)), col, self.cell)


def fit_bounds(model, clip, view, extra=()):
    xs, ys = [], []
    for i in range(9):
        for _, corners, _ in model.posed(clip.pose(clip.length * i / 8)):
            for p in corners:
                a, b, _ = view.project(p)
                xs.append(a); ys.append(b)
    for p in extra:
        a, b, _ = view.project(p)
        xs.append(a); ys.append(b)
    return (min(xs), max(xs)), (min(ys), max(ys))


def floor_extent(model, pad=1.5):
    xs = [p[0] for _, cs, _ in model.cubes for p in cs]
    zs = [p[2] for _, cs, _ in model.cubes for p in cs]
    return (min(xs) - pad, max(xs) + pad), (min(zs) - pad, max(zs) + pad)


def unit_speed(model, speed_bps):
    """Blocks a second -> model units a second."""
    return speed_bps / model.blocks_per_unit


def film(model, clip, frames=8, views=('side', 'front', 'top', 'three'), treadmill=None, label=None, cw=300, ch=210,
         times=None):
    """One canvas: `frames` moments of the clip (columns) from each view (rows); or the moments in `times` (seconds)."""
    if times is not None:
        frames = len(times)
    gait = gait_of(model, clip)
    speed = gait.speed if treadmill is None else treadmill
    ext = floor_extent(model)
    ground = gait.ground if model.feet else 0.0
    floor_pts = [(x, ground, z) for x in ext[0] for z in ext[1]]
    title_h, label_h = 34, 22
    cv = Canvas(cw * frames, title_h + (ch + label_h) * len(views))
    head = label or f'{model.name}  {clip.name}  {clip.length:g} S' + (f'  TREADMILL {speed * model.blocks_per_unit:.2f} B/S' if speed else '')
    cv.text(10, 10, head, scale=2)
    for r, vname in enumerate(views):
        view = VIEWS[vname]
        bounds = fit_bounds(model, clip, view, floor_pts)
        for k in range(frames):
            t = times[k] if times is not None else clip.length * k / frames
            x0, y0 = cw * k, title_h + (ch + label_h) * r
            cv.frame(x0, y0, cw, ch + label_h)
            cv.text(x0 + 6, y0 + 6, f'{view.title} {t:.3f} S')
            cell = (x0 + 1, y0 + label_h, cw - 2, ch - 1)
            fr = Frame(cv, cell, view, bounds)
            pose = clip.pose(t)
            fr.boxes(model.posed(pose))
            fr.grid(ground, (speed * t) % 4.0 - 4.0 if speed else 0.0, ext)
            draw_trails(fr, model, gait, t, speed)
            mark_below(fr, model, pose, ground)
    return cv


def draw_trails(fr, model, gait, t, speed):
    """Each foot's path through the cycle on the treadmill floor, seen at time t (its current spot a big dot)."""
    for k in gait.legs:
        col = LEG_COLOURS.get(k, (0, 0, 0))
        prev = None
        for i, ti in enumerate(gait.times):
            p = gait.tips[i][k]
            q = (p[0], p[1], p[2] - speed * (ti - t))      # where that foot point sits on the moving floor at time t
            s = fr.px(q)
            if prev:
                fr.canvas.line(prev, s, col, fr.cell, depth=False)
            prev = s
        cur = model.foot_tips(gait.clip.pose(t))[k]
        x, y, _ = fr.px(cur)
        fr.canvas.dot(x, y, 3, col, fr.cell)


def mark_below(fr, model, pose, ground):
    for _, corners, _ in model.posed(pose):
        for p in corners:
            if p[1] < ground - 0.35:
                x, y, _ = fr.px(p)
                fr.canvas.dot(x, y, 2, BAD, fr.cell)


def graphs(model, clip, cw=270, ch=90):
    """Every animated bone's turn (and position) over the clip, the footfall pattern and the foot heights."""
    gait = gait_of(model, clip)
    rows = [(b, c) for b in sorted(clip.bones) for c in ('rotation', 'position') if c in clip.bones[b]]
    cols = 4
    n_rows = math.ceil(len(rows) / cols)
    title_h = 34
    foot_h = 30 + 18 * max(1, len(gait.legs)) if gait.legs else 0
    cv = Canvas(cw * cols, title_h + (ch + 20) * n_rows + (foot_h + 20) * 2)
    cv.text(10, 10, f'{model.name}  {clip.name}  {clip.length:g} S   X RED  Y GREEN  Z BLUE', scale=2)
    for i, (bone, chan) in enumerate(rows):
        x0, y0 = cw * (i % cols), title_h + (ch + 20) * (i // cols)
        cv.frame(x0, y0, cw, ch + 20)
        samples = [sample_channel(clip.bones[bone][chan], clip.length * s / 200) for s in range(201)]
        lo = min(min(v) for v in samples)
        hi = max(max(v) for v in samples)
        span = max(hi - lo, 1e-6)
        cv.text(x0 + 5, y0 + 4, f'{bone} {chan[:3]} {lo:.0f} TO {hi:.0f}', scale=1)
        gx0, gy0, gw, gh = x0 + 6, y0 + 16, cw - 12, ch - 2
        zero = gy0 + gh - (0 - lo) / span * gh
        if lo <= 0 <= hi:
            cv.line((gx0, zero, 0), (gx0 + gw, zero, 0), GRID, (x0, y0, cw, ch + 20), depth=False)
        for key in clip.bones[bone][chan]:
            kx = gx0 + key[0] / clip.length * gw
            cv.line((kx, gy0, 0), (kx, gy0 + gh, 0), (200, 195, 185), (x0, y0, cw, ch + 20), depth=False)
        for axis in range(3):
            pts = [(gx0 + s / 200 * gw, gy0 + gh - (v[axis] - lo) / span * gh, 0) for s, v in enumerate(samples)]
            for a, b in zip(pts, pts[1:]):
                cv.line(a, b, AXIS_COLOURS[axis], (x0, y0, cw, ch + 20), depth=False)
    if gait.legs:
        y0 = title_h + (ch + 20) * n_rows
        width = cw * cols
        cv.frame(0, y0, width, foot_h + 20)
        cv.text(6, y0 + 4, 'FOOTFALL PATTERN: A BAR WHILE THE FOOT IS ON THE GROUND (TRIPOD: L1 R2 L3 TOGETHER, THEN R1 L2 R3)', scale=1)
        order = ['l1', 'r2', 'l3', 'r1', 'l2', 'r3']
        legs = [k for k in order if k in gait.legs] + [k for k in gait.legs if k not in order]
        for j, k in enumerate(legs):
            yy = y0 + 22 + 18 * j
            cv.text(6, yy + 3, k, scale=1)
            for i in range(len(gait.times) - 1):
                if gait.down[k][i]:
                    xa = 40 + int(i / (len(gait.times) - 1) * (width - 50))
                    xb = 40 + int((i + 1) / (len(gait.times) - 1) * (width - 50))
                    cv.rect(xa, yy, max(1, xb - xa), 12, LEG_COLOURS.get(k, (0, 0, 0)))
        y0 += foot_h + 20
        cv.frame(0, y0, width, foot_h + 20)
        cv.text(6, y0 + 4, 'FOOT HEIGHT ABOVE THE GROUND (EACH LEG ITS COLOUR)', scale=1)
        top = max(t[k][1] for t in gait.tips for k in gait.legs) - gait.ground
        for k in gait.legs:
            pts = [(40 + i / (len(gait.times) - 1) * (width - 50),
                    y0 + 16 + foot_h - 4 - (tips[k][1] - gait.ground) / max(top, 1e-6) * (foot_h - 16), 0)
                   for i, tips in enumerate(gait.tips)]
            for a, b in zip(pts, pts[1:]):
                cv.line(a, b, LEG_COLOURS.get(k, (0, 0, 0)), (0, y0, width, foot_h + 20), width=2, depth=False)
    return cv


# --- the checks -----------------------------------------------------------------------------------------------------
def inside_box(p, corners, margin=0.05):
    o = corners[0]
    ax = [tuple(c - q for c, q in zip(corners[i], o)) for i in (4, 2, 1)]
    d = tuple(a - b for a, b in zip(p, o))
    for a in ax:
        ln2 = dot3(a, a)
        if ln2 < 1e-9:
            return False
        f = dot3(d, a) / ln2
        m = margin / math.sqrt(ln2)
        if not (m < f < 1 - m):
            return False
    return True


def box_test(corners, margin):
    """inside_box's sums for one box worked out once, for testing many points against it with in_box (the same
    numbers as inside_box: the same sums, done the same way). None for a flat box, which holds no point."""
    o = corners[0]
    axes = []
    for i in (4, 2, 1):
        c = corners[i]
        a0, a1, a2 = c[0] - o[0], c[1] - o[1], c[2] - o[2]
        ln2 = sum((a0 * a0, a1 * a1, a2 * a2))              # as dot3: sum(), not +
        if ln2 < 1e-9:
            return None
        m = margin / math.sqrt(ln2)
        axes.append((a0, a1, a2, ln2, m, 1 - m))
    return o[0], o[1], o[2], axes


def in_box(p, box):
    """inside_box(p, corners, margin) for box = box_test(corners, margin)."""
    if box is None:
        return False
    o0, o1, o2, axes = box
    d0, d1, d2 = p[0] - o0, p[1] - o1, p[2] - o2
    for a0, a1, a2, ln2, m, top in axes:
        if not (m < sum((d0 * a0, d1 * a1, d2 * a2)) / ln2 < top):
            return False
    return True


def segment_points(corners, n=6):
    """Points along a cube's long middle line (a leg segment's core)."""
    o = corners[0]
    ax = [tuple(c - q for c, q in zip(corners[i], o)) for i in (4, 2, 1)]
    lengths = [math.sqrt(dot3(a, a)) for a in ax]
    long_i = lengths.index(max(lengths))
    centre = tuple(q + sum(a[j] for a in ax) / 2 for j, q in enumerate(o))
    a = ax[long_i]
    return [tuple(c + a[j] * (s / n - 0.5) * 0.9 for j, c in enumerate(centre)) for s in range(n + 1)]


def is_leg(model, bone):
    return model.leg_of(bone) is not None


def check(model, clip, speed_bps=None, quiet=False):
    """Checks one clip. Returns a list of (level, message); level 'BAD' or 'NOTE'."""
    out = []
    gait = gait_of(model, clip)
    if speed_bps is None and clip.speeds:
        # A walk for a band of speeds: judged in the middle of its band (the fast one at the game's walking speed).
        low, high = clip.speeds
        top = WALK_SPEED.get(model.name, 2.0) / model.blocks_per_unit
        speed_bps = (top if high > top else (max(low, 0.4 * high) * high) ** 0.5) * model.blocks_per_unit
    speed_bps = speed_bps if speed_bps is not None else WALK_SPEED.get(model.name, 2.0)
    n = Gait.STEPS
    dt = clip.length / n
    if clip.turn is not None and gait.legs:
        # Turning on the spot: each planted foot should go round the middle as the body turns over it (the body turns
        # right, so a foot standing still on the ground turns left round the middle in the body's frame).
        w = math.radians(clip.turn) / clip.length
        worst = {}
        for k in gait.legs:
            for i in range(n):
                if gait.down[k][i] and gait.down[k][i + 1]:
                    a, b = gait.tips[i][k], gait.tips[i + 1][k]
                    vx, vz = (b[0] - a[0]) / dt, (b[2] - a[2]) / dt
                    ex, ez = -w * a[2], w * a[0]
                    slip = math.hypot(vx - ex, vz - ez) / max(1e-6, math.hypot(ex, ez))
                    if slip > worst.get(k, (0, 0))[0]:
                        worst[k] = (slip, gait.times[i])
        for k, (slip, t) in sorted(worst.items()):
            if slip > 0.35:
                out.append(('BAD', f'foot {k} slides while turning: off by {slip * 100:.0f}% of its speed round the middle at {t:.3f} s'))
            elif slip > 0.15:
                out.append(('NOTE', f'foot {k} slips a little while turning: {slip * 100:.0f}% at {t:.3f} s'))
        down_min = min(sum(gait.down[k][i] for k in gait.legs) for i in range(n + 1))
        if down_min < 3 and len(gait.legs) == 6:
            out.append(('BAD', f'only {down_min} feet on the ground while turning (at least 3 should be down)'))
        duty = {k: sum(gait.down[k][:-1]) / n for k in gait.legs}
        out.append(('NOTE', f'turns {clip.turn:g} degrees a cycle; at 90 degrees a second each leg steps '
                            f'{90 / abs(clip.turn):.1f} times a second; duty ' + ', '.join(f'{k} {d:.2f}' for k, d in duty.items())))
    elif gait.walking:
        # Sliding: each planted foot should move back at the treadmill speed and not sideways.
        worst = {}
        for k in gait.legs:
            for i in range(n):
                if gait.down[k][i] and gait.down[k][i + 1]:
                    a, b = gait.tips[i][k], gait.tips[i + 1][k]
                    vz = (b[2] - a[2]) / dt
                    vx = (b[0] - a[0]) / dt
                    slip = math.hypot(vz - gait.speed, vx) / abs(gait.speed)
                    if slip > worst.get(k, (0, 0))[0]:
                        worst[k] = (slip, gait.times[i])
        for k, (slip, t) in sorted(worst.items()):
            if slip > 0.35:
                out.append(('BAD', f'foot {k} slides on the ground: off by {slip * 100:.0f}% of the walk speed at {t:.3f} s'))
            elif slip > 0.15:
                out.append(('NOTE', f'foot {k} slips a little: {slip * 100:.0f}% of the walk speed at {t:.3f} s'))
        down_min = min(sum(gait.down[k][i] for k in gait.legs) for i in range(n + 1))
        if down_min < 3 and len(gait.legs) == 6:
            first = next(gait.times[i] for i in range(n + 1) if sum(gait.down[k][i] for k in gait.legs) == down_min)
            out.append(('BAD', f'only {down_min} feet on the ground at {first:.3f} s (a walking insect always has at least 3 down)'))
        duty = {k: sum(gait.down[k][:-1]) / n for k in gait.legs}
        phases = {}
        for k in gait.legs:
            starts = [i for i in range(n) if gait.down[k][i] and not gait.down[k][i - 1]]
            phases[k] = (starts[0] / n) if starts else None
        stride_blocks = gait.stride() * model.blocks_per_unit
        body_blocks = model.length * model.blocks_per_unit
        cycles = speed_bps / stride_blocks if stride_blocks else 0
        out.append(('NOTE', f'stride {stride_blocks:.3f} blocks = {gait.stride() / model.length:.2f} body lengths a cycle '
                            f'(the body is {body_blocks:.2f} blocks long)'))
        out.append(('NOTE', f'at {speed_bps:g} blocks a second ({speed_bps / body_blocks:.1f} body lengths a second) the walk '
                            f'must play {cycles:.1f} cycles a second = playback speed {cycles * clip.length:.2f} '
                            f'(each leg steps {cycles:.1f} times a second)'))
        if clip.stride is None:
            out.append(('NOTE', 'no stride note in the clip: the game can only guess how fast to play it'))
        elif abs(clip.stride - gait.stride()) > 0.03 * gait.stride():
            out.append(('BAD', f"the clip's stride note says {clip.stride:.2f} units, its feet cover {gait.stride():.2f}: "
                               'the game would play it at the wrong speed and the feet would slide'))
        out.append(('NOTE', 'duty factor (share of the cycle a foot is down; real tripod walkers 0.5 to 0.65): '
                            + ', '.join(f'{k} {d:.2f}' for k, d in duty.items())))
        out.append(('NOTE', 'touch-down moment in the cycle (tripod: L1 R2 L3 together, the others half a cycle off): '
                            + ', '.join(f'{k} {p:.2f}' if p is not None else f'{k} never' for k, p in phases.items())))
        # Balance (2026-10-18): the body's centre of weight over the shape the feet on the ground stand round.
        margins = []
        for s in range(0, n + 1, 2):
            feet = [(gait.tips[s][k][0], gait.tips[s][k][2]) for k in gait.legs if gait.down[k][s]]
            margins.append(support_margin(model.centre(clip.pose(gait.times[s])), feet))
        low = min(margins)
        if low >= 0:
            out.append(('NOTE', f"balance: the body's centre of weight stays over its feet all through the cycle "
                                f'(at least {low:.2f} units inside the feet on the ground)'))
        else:
            share = sum(m < 0 for m in margins) / len(margins)
            out.append(('NOTE', f"balance: the body's centre of weight is outside its feet for {share * 100:.0f}% of the "
                                f'cycle (up to {-low:.2f} units; a fast runner can be, its speed carries it; a slow '
                                'walker should not be)'))
    elif gait.legs:
        # Standing: no foot should move along the ground at all.
        for k in gait.legs:
            moves = [math.dist(gait.tips[i][k][::2], gait.tips[i + 1][k][::2]) for i in range(n)
                     if gait.down[k][i] and gait.down[k][i + 1]]
            if moves and max(moves) / dt > 1.5:
                i = max(range(len(moves)), key=lambda j: moves[j])
                out.append(('NOTE', f'foot {k} moves along the ground while standing ({max(moves) / dt:.1f} units a second)'))
    # Sinking into the floor, clipping, loop seams, snaps: sampled through the clip.
    floor = gait.ground if model.feet else 0.0
    sunk = clipped = None
    leg_cubes = [i for i, (b, _, _) in enumerate(model.cubes) if is_leg(model, b)]
    body_cubes = [i for i, (b, _, _) in enumerate(model.cubes) if not is_leg(model, b) and not model.is_antenna(b)]
    for s in range(0, n + 1, 2):
        t = gait.times[s]
        pose = clip.pose(t)
        xf = model.transforms(pose)
        posed = model.posed(pose, xf)
        if sunk is None:
            for b, corners, _ in posed:
                low = min(p[1] for p in corners)
                if low < floor - 0.35:
                    sunk = (b, t, floor - low)
                    break
        if clipped is None:
            body_boxes = [(posed[bi][0], box_test(posed[bi][1], 0.08)) for bi in body_cubes]
            leg_boxes = [(posed[oi][0], box_test(posed[oi][1], 0.03)) for oi in leg_cubes]
            for li in leg_cubes:
                lb = posed[li][0]
                root = model.hip_of(model.leg_of(lb))                      # the hip, where the leg is meant to sit
                hip = model.world(root, model.by[root]['pivot'], pose, xf)
                for p in segment_points(posed[li][1]):
                    if math.dist(p, hip) < 1.0:
                        continue
                    hit = next((bn for bn, box in body_boxes if in_box(p, box)), None)
                    if hit is None:
                        hit = next((bn for bn, box in leg_boxes if model.leg_of(bn) != model.leg_of(lb)
                                    and in_box(p, box)), None)
                    if hit:
                        clipped = (lb, hit, t)
                        break
                if clipped:
                    break
    if sunk:
        out.append(('BAD', f'{sunk[0]} sinks {sunk[2]:.2f} units into the floor at {sunk[1]:.3f} s'))
    if clipped:
        out.append(('BAD', f'{clipped[0]} passes through {clipped[1]} at {clipped[2]:.3f} s'))
    for bone, chans in clip.bones.items():
        rot = chans.get('rotation')
        if not rot:
            continue
        vals = [sample_channel(rot, clip.length * s / 400) for s in range(401)]
        if clip.loop and len(rot) > 1:
            gap = max(abs(a - b) for a, b in zip(vals[0], vals[-1]))
            if gap > 1.0:
                out.append(('BAD', f'{bone} jumps {gap:.0f} degrees where the loop starts again'))
        fast = max(max(abs(a - b) for a, b in zip(v0, v1)) for v0, v1 in zip(vals, vals[1:])) / (clip.length / 400)
        if fast > 2500:
            out.append(('NOTE', f'{bone} turns at up to {fast:.0f} degrees a second (a snap: fine for a flick or a bite, not for a sweep)'))
    if not quiet:
        report(model.name, clip, out)
    return out


def report(model_name, clip, out):
    """Prints one clip's check results."""
    print(f'--- {model_name} {clip.name} ({clip.length:g} s{", loop" if clip.loop else ""})')
    for level, msg in out or [('OK', 'nothing found')]:
        print(f'  {level:4} {msg}')


# --- contact sheets of screenshots -----------------------------------------------------------------------------------
def sheet(folder, cols=4, width=420):
    files = sorted(f for f in os.listdir(folder) if f.lower().endswith('.png'))
    if not files:
        raise SystemExit(f'no PNGs in {folder}')
    imgs = []
    for f in files:
        w, h, px = read_png(os.path.join(folder, f))
        sh = int(h * width / w)
        small = []
        for y in range(sh):
            sy = int(y * h / sh)
            row = sy * w
            for x in range(width):
                small.append(px[row + int(x * w / width)][:3])
        imgs.append((f, sh, small))
    cell_h = max(sh for _, sh, _ in imgs) + 20
    rows = math.ceil(len(imgs) / cols)
    cv = Canvas(width * cols, cell_h * rows)
    for i, (f, sh, small) in enumerate(imgs):
        x0, y0 = width * (i % cols), cell_h * (i // cols)
        for y in range(sh):
            base = (y0 + 20 + y) * cv.width + x0
            cv.color[base:base + width] = small[y * width:(y + 1) * width]
        cv.text(x0 + 4, y0 + 4, os.path.splitext(f)[0][:60], scale=2)
    return cv


# --- the command line ------------------------------------------------------------------------------------------------
GAME_VIEWS = {'side': 'side', 'front': 'front', 'above': 'top'}


def match(folder, model, clips, cols=4, width=420):
    """The in-game camera's pictures (with the clip moments in its times.txt) each above the lab's own drawing of the
    same clip at the same moment from the same side: if the two ever disagree, the lab (or the game) has it wrong."""
    rows = []
    for line in open(os.path.join(folder, 'times.txt'), encoding='utf-8'):
        w = line.split()
        if len(w) < 3 or w[1] == '-' or w[1] not in clips:
            continue
        stem = os.path.splitext(w[0])[0]
        view = next((v for k, v in GAME_VIEWS.items() if f'_{k}' in stem), None)
        if view:
            rows.append((w[0], clips[w[1]], float(w[2]), view))
    if not rows:
        raise SystemExit(f'no pictures with clip times in {folder}/times.txt')
    lab_h = int(width * 0.6)
    cells = []
    for name, clip, t, view in rows:
        gw, gh, px = read_png(os.path.join(folder, name))
        # the game picture, cut to the middle (where the insect is) and scaled to the cell
        cut_w, cut_h = gw // 2, gh // 2
        x0, y0 = (gw - cut_w) // 2, (gh - cut_h) // 2
        game_h = int(cut_h * width / cut_w)
        lab = film(model, clip, views=(view,), times=[t], cw=width, ch=lab_h, label=f'LAB {clip.name} {t:.3f} S')
        cells.append((name, x0, y0, cut_w, cut_h, game_h, gw, px, lab))
    cell_h = 26 + cells[0][5] + cells[0][8].height
    cv = Canvas(cols * width, ((len(cells) + cols - 1) // cols) * cell_h)
    for i, (name, x0, y0, cut_w, cut_h, game_h, gw, px, lab) in enumerate(cells):
        ox, oy = (i % cols) * width, (i // cols) * cell_h
        cv.text(ox + 4, oy + 4, 'GAME ' + os.path.splitext(name)[0][:40], scale=2)
        for y in range(game_h):
            sy = y0 + y * cut_h // game_h
            for x in range(width):
                cv.color[(oy + 26 + y) * cv.width + ox + x] = px[sy * gw + x0 + x * cut_w // width][:3]
        for y in range(lab.height):
            base = (oy + 26 + game_h + y) * cv.width + ox
            cv.color[base:base + lab.width] = lab.color[y * lab.width:(y + 1) * lab.width]
    return cv


def save(cv, name, quiet=False):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    cv.save(path)
    if not quiet:
        print('wrote', os.path.relpath(path, PROJECT))
    return path


# --- running many clips: side by side, and remembering what was checked ---------------------------------------------
# A clip's fingerprint is a digest of everything its check (or its pictures) depends on: the lab's own code (this file,
# termite_models.py, which holds the font and the canvas, and body_plan.py), the model's geo file, texture and body
# plan, the clip's own key
# frames in the animation file, and the options (walking speed, frames). A clip whose fingerprint is the same as at its
# last run gets its remembered findings (the check is exact and repeatable: the same numbers in give the same findings
# out), and `all` keeps its pictures while they are still there. Any change to the clip, the model or the lab makes it
# run again; --fresh runs everything.
CACHE = os.path.join(OUT, '.lab_cache.json')
FLAGS = ('fresh',)                     # options without a value
_LOADED = {}


def _stamp(files):
    """Each picture's size and time stamp: a picture written since (by `film` at another speed, say) is redrawn."""
    out = {}
    for f in files:
        try:
            st = os.stat(os.path.join(OUT, f))
            out[f] = [st.st_size, st.st_mtime_ns]
        except OSError:
            out[f] = None
    return out


def _bytes(path):
    try:
        with open(path, 'rb') as f:
            return f.read()
    except OSError:
        return b''


def _digest(*parts):
    h = hashlib.sha256()
    for p in parts:
        h.update(p if isinstance(p, bytes) else json.dumps(p, sort_keys=True).encode('utf-8'))
        h.update(b'|')
    return h.hexdigest()


def _read_cache():
    try:
        with open(CACHE, encoding='utf-8') as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def _write_cache(updates):
    """Adds this run's entries to the remembered ones (read again first, so two runs at once keep each other's)."""
    data = _read_cache()
    for section, entries in updates.items():
        data.setdefault(section, {}).update(entries)
    os.makedirs(OUT, exist_ok=True)
    tmp = f'{CACHE}.{os.getpid()}.tmp'
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(data, f)
    os.replace(tmp, CACHE)


def _loaded(name, anim):
    if (name, anim) not in _LOADED:
        model = Model(name)
        _LOADED[(name, anim)] = (model, load_clips(anim, model))
    return _LOADED[(name, anim)]


def _work(job):
    """One clip's work, in a helper process when clips run side by side: draws what is asked and checks the clip.
    Returns the lines to print and the check's findings (None when not checked)."""
    name, anim, nm, speed, frames, do_film, film_speed, do_graphs, do_check = job
    model, clips = _loaded(name, anim)
    clip = clips[nm]
    lines = []
    if do_film:
        tread = unit_speed(model, speed) if film_speed and speed is not None else None
        path = save(film(model, clip, frames, treadmill=tread), f'{model.name}_{nm}_film.png', quiet=True)
        lines.append('wrote ' + os.path.relpath(path, PROJECT))
    if do_graphs:
        path = save(graphs(model, clip), f'{model.name}_{nm}_graphs.png', quiet=True)
        lines.append('wrote ' + os.path.relpath(path, PROJECT))
    out = check(model, clip, speed, quiet=True) if do_check else None
    return lines, out


def run_clips(cmd, name, anim, clips, names, speed, frames, jobs, fresh):
    """`film`, `graphs`, `check` or `all` for the named clips, on up to `jobs` processes at once. The output is
    printed clip by clip in the clips' order, exactly as one process prints it. `check` and `all` skip clips whose
    fingerprint has not changed since their last run (unless fresh). Returns the number of BAD findings."""
    start = time.time()
    remember = cmd in ('check', 'all')
    cache = {} if fresh or not remember else _read_cache()
    if remember:
        code = (_bytes(os.path.abspath(__file__)) + _bytes(os.path.abspath(tm.__file__))
                + _bytes(os.path.abspath(body_plan.__file__)))
        model_bytes = (_bytes(model_file(name, 'geo', 'entity', f'{name}.geo.json'))
                       + _bytes(model_file(name, 'textures', 'entity', f'{name}.png'))
                       + _bytes(body_plan.plan_path(name)))
        with open(model_file(anim, 'animations', 'entity', f'{anim}.animation.json'), encoding='utf-8') as f:
            raw = json.load(f)['animations']
    plans, updates = [], {'check': {}, 'draw': {}}
    for nm in names:
        key = f'{name}|{anim}|{nm}'
        do_check, do_film, do_graphs = cmd in ('check', 'all'), cmd in ('film', 'all'), cmd in ('graphs', 'all')
        out = fp_check = fp_draw = None
        kept = []
        if remember:
            fp_check = _digest('check', code, model_bytes, name, anim, nm, raw[nm], speed)
            seen = cache.get('check', {}).get(key)
            if seen and seen.get('fp') == fp_check:
                do_check, out = False, [tuple(x) for x in seen['out']]
        if cmd == 'all':
            fp_draw = _digest('draw', code, model_bytes, name, anim, nm, raw[nm], frames)
            files = [f'{name}_{nm}_film.png', f'{name}_{nm}_graphs.png']
            seen = cache.get('draw', {}).get(key)
            stamp = _stamp(files)
            if seen and seen.get('fp') == fp_draw and seen.get('files') == stamp and None not in stamp.values():
                do_film = do_graphs = False
                kept = ['kept ' + os.path.relpath(os.path.join(OUT, f), PROJECT) + ' (unchanged)' for f in files]
        job = (name, anim, nm, speed, frames, do_film, cmd == 'film', do_graphs, do_check)
        plans.append((nm, key, job, out, kept, fp_check, fp_draw))
    busy = lambda job: job[5] or job[7] or job[8]  # noqa: E731
    todo = [p for p in plans if busy(p[2])]
    workers = max(1, min(jobs, len(todo)))
    pool = futures = None
    if workers > 1:
        from concurrent.futures import ProcessPoolExecutor
        pool = ProcessPoolExecutor(workers)
        futures = {p[0]: pool.submit(_work, p[2]) for p in todo}
    bad = 0
    try:
        for nm, key, job, out, kept, fp_check, fp_draw in plans:
            lines, found = (futures[nm].result() if futures else _work(job)) if busy(job) else ([], None)
            for line in kept + lines:
                print(line)
            if found is not None:
                out = found
                if remember:
                    updates['check'][key] = {'fp': fp_check, 'out': out}
            if fp_draw and lines:
                files = [f'{name}_{nm}_film.png', f'{name}_{nm}_graphs.png']
                updates['draw'][key] = {'fp': fp_draw, 'files': _stamp(files)}
            if out is not None:
                report(name, clips[nm], out)
                bad += sum(1 for level, _ in out if level == 'BAD')
            sys.stdout.flush()
    finally:
        if pool:
            pool.shutdown(cancel_futures=True)
    if remember:
        if updates['check'] or updates['draw']:
            _write_cache(updates)
        print(f'lab: {len(plans)} clip(s), {len(todo)} worked on ({workers} process{"es" if workers > 1 else ""}), '
              f'{len(plans) - len(todo)} unchanged since their last run (remembered), {time.time() - start:.1f} s')
    return bad


def main(argv):
    opts, args = {}, []
    it = iter(argv)
    for a in it:
        if a in ('--help', '-h'):
            print(__doc__)
            return
        if a[2:] in FLAGS:
            opts[a[2:]] = True
        elif a.startswith('--'):
            opts[a[2:]] = next(it)
        else:
            args.append(a)
    if not args:
        print(__doc__)
        return
    cmd = args[0]
    if cmd == 'sheet':
        folder = args[1]
        save(sheet(folder, int(opts.get('cols', 4))), os.path.basename(os.path.normpath(folder)) + '_sheet.png')
        return
    if cmd == 'match':
        model = Model(args[2])
        save(match(args[1], model, load_clips(opts.get('anim', args[2]), model), int(opts.get('cols', 4))),
             os.path.basename(os.path.normpath(args[1])) + '_match.png')
        return
    name, anim = args[1], opts.get('anim', args[1])
    if not os.path.exists(model_file(name, 'geo', 'entity', f'{name}.geo.json')):
        Model(name)                      # no such model: the same error as ever
    clips = load_clips(anim, plan=body_plan.load(name))
    names = args[2:] or list(clips)
    speed = float(opts['speed']) if 'speed' in opts else None
    frames = int(opts.get('frames', 8))
    jobs = int(opts.get('jobs', max(1, (os.cpu_count() or 2) // 2)))
    fresh = opts.get('fresh', False)
    for nm in names:
        if nm not in clips:
            raise SystemExit(f'no clip "{nm}"; there are: {", ".join(clips)}')
    if cmd in ('film', 'graphs', 'all'):
        run_clips(cmd, name, anim, clips, names, speed, frames, jobs, fresh)
    elif cmd == 'check':
        bad = run_clips(cmd, name, anim, clips, names, speed, frames, jobs, fresh)
        print(f'{bad} problem(s)')
        sys.exit(1 if bad else 0)
    elif cmd == 'compare':
        model = Model(name)
        rows = [film(model, clips[nm], frames, views=(opts.get('view', 'side'),), label=nm) for nm in names]
        cv = Canvas(max(r.width for r in rows), sum(r.height for r in rows))
        y = 0
        for r in rows:
            for yy in range(r.height):
                cv.color[(y + yy) * cv.width:(y + yy) * cv.width + r.width] = r.color[yy * r.width:(yy + 1) * r.width]
            y += r.height
        save(cv, f'{model.name}_compare_{"_".join(names)}.png')
    else:
        print(__doc__)


if __name__ == '__main__':
    main(sys.argv[1:])
