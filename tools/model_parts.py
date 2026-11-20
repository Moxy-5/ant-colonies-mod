"""The parts library (docs/plans/model-workshop.md, step 4, 2026-11-19): the one shared home of what the animal makers
(tools/<animal>_models.py) build with, so the next animal starts from approved parts instead of copies.

What is in it, top to bottom:
- Basics: the box maker (cube), a box's faces and where a texture pixel lies on one, colour mixing, smooth curves, the
  one picture (PNG) writer, clip sampling. These lived in tilapia_models.py and every animal maker imported them.
- The texture: paint_faces() lays every face of every box out on one texture and colours it pixel by pixel (word for
  word the same in seven makers before); geo() and write_assets() write the game's files.
- Turns: 3x3 turn matrices in the viewer's axes (three.js; GeckoLib turns bones Z, then Y, then X) and rot(), which
  turns one into a bone's rest rotation in the file's degrees (x and z negated, as the viewer reads them).
- Approved parts, from the Nile monitor (the user's look, 2026-11-17/18), each with settings:
    rounded()     a round cross-section of three boxes (wide, tall, between): tails, necks, bodies;
    disc(), limb(), ball()   quarter-pixel round slices: a tapering round piece and a round joint;
    bent_chain()  a tail (or any long round piece) as a chain of bones, each turned so the chain follows a curve;
    round_leg()   a whole leg from three joints: round upper and lower pieces, round joints, a flat foot turned out,
                  any number of fanned toes, each with a hooked claw (its own bone, to flex).
  Not moved yet (the plan: each new animal adds what it needs): the monitor's jaw with teeth and tongue, its eyes with
  lids; the crocodile's parts.

Units are model pixels (16 a block), the head to -Z, up +Y, left +X. A part maker appends boxes to a list (or returns
bones); a box carries its paint part name in '_part' (and paint_faces adds '_bone'); keys starting with '_' never reach
the game's files.
"""
import copy
import json
import math
import struct
import zlib


# --- basics ----------------------------------------------------------------------------------------------------------
def curve(points, t):
    """A value eased smoothly between points ((t, value), ...), t from 0 to 1."""
    t = max(0.0, min(1.0, t))
    for (t0, v0), (t1, v1) in zip(points, points[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0)
            f = f * f * (3 - 2 * f)
            return v0 + (v1 - v0) * f
    return points[-1][1]


def smooth(x):
    """0 to 1 eased in and out (x clamped to 0 to 1)."""
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def mix(a, b, f):
    return tuple(p + (q - p) * f for p, q in zip(a, b))


def shade(c, k):
    return tuple(v * k for v in c)


def cube(lo, hi, part):
    """A box from corner lo to corner hi, painted as part."""
    return {'origin': [round(v, 3) for v in lo], 'size': [round(b - a, 3) for a, b in zip(lo, hi)], '_part': part}


def faces(c):
    """Each face of a box and its size (across, down)."""
    sx, sy, sz = c['size']
    return {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}


def face_point(c, face, u, v):
    """Where texture pixel (u, v) (0 to 1 across the face, v down from the top) lies on the box, in the box's own
    model space (the viewer's and the game's face layout)."""
    (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
    if face == 'east':
        return ox + sx, oy + sy * (1 - v), oz + sz * (1 - u)
    if face == 'west':
        return ox, oy + sy * (1 - v), oz + sz * u
    if face == 'up':
        return ox + sx * u, oy + sy, oz + sz * v
    if face == 'down':
        return ox + sx * u, oy, oz + sz * (1 - v)
    if face == 'south':
        return ox + sx * u, oy + sy * (1 - v), oz + sz
    return ox + sx * (1 - u), oy + sy * (1 - v), oz


def png(rows):
    """A PNG file's bytes from rows of (r, g, b, a)."""
    height, width = len(rows), len(rows[0])
    raw = b''.join(b'\x00' + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')


def clip(length, pose_at, steps, loop=True):
    """A clip from pose_at(t) -> {bone: {'rotation': [..], 'position': [..]}} sampled at steps + 1 times; loop True,
    False or 'hold' (stays on its last frame)."""
    bones = {}
    for k in range(steps + 1):
        t = length * k / steps
        for bone, chans in pose_at(t).items():
            for ch, v in chans.items():
                bones.setdefault(bone, {}).setdefault(ch, {})[f'{t:.4f}'.rstrip('0').rstrip('.') or '0'] = [round(x, 3) for x in v]
    out = {'animation_length': round(length, 4), 'bones': bones}
    if loop is True:
        out['loop'] = True
    elif loop == 'hold':
        out['loop'] = 'hold_on_last_frame'
    return out


def rot_only(pose):
    return {b: {'rotation': r} for b, r in pose.items()}


# --- the texture and the game's files --------------------------------------------------------------------------------
def _byte(v):
    return max(0, min(255, int(v)))


def paint_faces(bones, texels, colour, to_byte=_byte):
    """Lays every face of every box out on one texture (texels texture pixels a model pixel; the tallest faces first,
    in shelves, on the narrowest square-ish texture they fit) and colours each pixel with colour(box, face, x, y, z),
    (x, y, z) being where the pixel lies on the model. Returns (bones with every face's place on the texture, rows of
    (r, g, b, a), width, height); the bones are a copy, without the '_' keys."""
    bones = copy.deepcopy(bones)
    patches = []
    for b in bones:
        for c in b.get('cubes', []):
            c['uv'] = {}
            c['_bone'] = b['name']
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
        for j in range(h):
            for i in range(w):
                px, py, pz = face_point(c, face, (i + 0.5) / w, (j + 0.5) / h)
                rows[y0 + j][x0 + i] = tuple(to_byte(v) for v in colour(c, face, px, py, pz)) + (255,)
    for b in bones:
        for c in b.get('cubes', []):
            for k in [k for k in c if k.startswith('_')]:
                c.pop(k)
    return bones, rows, width, height


def game_faces(bones):
    """The bones with every face's place on the texture as GeckoLib needs it, so the game draws each face the way the
    viewers do (2026-11-20, the bullfrog's eyes: in the game their art was on the faces inside the head).

    GeckoLib turns a model over left to right on loading (a box from x to x + w is drawn from -(x + w) to -x), and the
    viewers draw that mirror image too (tools/viewer_common.js), but it does not turn the paint over: it lays each face's
    picture out by its own rule in the turned-over model. So, read from its own code (4.9.3: BakedModelFactory,
    VertexSet, GeoQuad.build): the face named east is drawn on the box's side toward the middle line where the viewer
    draws it on the far side (east and west swap), and every face's picture comes out back to front (side faces and
    the front and back left to right, the top and bottom front to back). A box that is the same on both its sides
    never shows it; one-sided art (an eye painted round its outer corner) does. This hands each face the other side's
    picture, read the other way (a negative size reads a picture backwards; GeckoLib and Blockbench's own Bedrock files
    do the same), so the game draws the exact mirror image of the viewer's model, paint and all: for an animal whose
    two sides match, the same animal. The texture is not touched. Only for the game's files: the viewers draw the
    faces as they are."""
    out = copy.deepcopy(bones)
    for b in out:
        for c in b.get('cubes', []):
            uv = c.get('uv')
            if not isinstance(uv, dict):
                continue
            turned = {}
            for face, source in (('east', 'west'), ('west', 'east'), ('north', 'north'), ('south', 'south'),
                                 ('up', 'up'), ('down', 'down')):
                if source in uv:
                    (x, y), (w, h) = uv[source]['uv'], uv[source]['uv_size']
                    turned[face] = ({'uv': [x, y + h], 'uv_size': [w, -h]} if face in ('up', 'down')
                                    else {'uv': [x + w, y], 'uv_size': [-w, h]})
            c['uv'] = turned
    return out


def geo(name, bones, w, h, bounds):
    """A GeckoLib model file: bounds is (visible width, visible height, [offset x, y, z]) in blocks."""
    bw, bh, offset = bounds
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{name}', 'texture_width': w, 'texture_height': h,
                        'visible_bounds_width': bw, 'visible_bounds_height': bh, 'visible_bounds_offset': offset},
        'bones': bones}]}


def animations(clips):
    return {'format_version': '1.8.0', 'animations': clips}


def write_assets(files, mod='savanna'):
    """Writes {path inside the mod's assets: bytes or data for a JSON file} into the mod, saying each file."""
    import mod_paths
    root = mod_paths.assets(mod)
    for rel, data in files.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(data, bytes):
            path.write_bytes(data)
        else:
            with open(path, 'w', encoding='utf-8', newline='\n') as f:
                json.dump(data, f, indent=1)
        print('wrote', path.relative_to(mod_paths.PROJECT))


# --- turns -----------------------------------------------------------------------------------------------------------
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def transpose(a):
    """A turn matrix's undoing (its transpose)."""
    return [list(r) for r in zip(*a)]


def mat_vec(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def turn_x(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def turn_y(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def turn_z(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def vadd(a, b):
    return [x + y for x, y in zip(a, b)]


def vsub(a, b):
    return [x - y for x, y in zip(a, b)]


def vlen(v):
    return math.sqrt(sum(x * x for x in v))


def unit(v):
    n = vlen(v)
    return [c / n for c in v]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def from_axes(x, y, z):
    """A turn matrix from where its three axes go."""
    return [[x[i], y[i], z[i]] for i in range(3)]


def aim(a, b):
    """The smallest turn taking direction a onto direction b."""
    na, nb = math.sqrt(sum(v * v for v in a)), math.sqrt(sum(v * v for v in b))
    a, b = [v / na for v in a], [v / nb for v in b]
    v = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
    c = sum(x * y for x, y in zip(a, b))
    k = [[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]]
    k2 = mat_mul(k, k)
    return [[(i == j) + k[i][j] + k2[i][j] / (1 + c) for j in range(3)] for i in range(3)]


def rot(m):
    """A turn matrix as a bone's rest rotation in degrees."""
    ay = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(m[2][0]) < 0.9999999:
        ax, az = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
    else:
        ax, az = 0.0, math.atan2(-m[0][1], m[1][1])
    return [round(-math.degrees(ax), 3), round(math.degrees(ay), 3), round(-math.degrees(az), 3)]


def euler(r):
    """A bone's turn in file degrees back to a matrix (the viewer's setRot: x and z negated, the order Z, Y, X)."""
    return mat_mul(mat_mul(turn_z(-r[2]), turn_y(r[1])), turn_x(-r[0]))


def blend_turn(a, b, k):
    """A turn part way (k, 0 to 1) from matrix a to matrix b: the two mixed, then squared up again."""
    m = [[a[i][j] * (1 - k) + b[i][j] * k for j in range(3)] for i in range(3)]
    c0 = unit([m[0][0], m[1][0], m[2][0]])
    c1 = [m[0][1], m[1][1], m[2][1]]
    d = sum(x * y for x, y in zip(c0, c1))
    c1 = unit([x - d * y for x, y in zip(c1, c0)])
    return from_axes(c0, c1, cross(c0, c1))


def pitch(deg):
    """A bone's rest rotation from a pitch (degrees): for a piece reaching to +Z (a tail) positive bends it down,
    for one reaching to -Z (neck, head) positive raises it (GeckoLib's x turn is the viewer's negated)."""
    return [-deg, 0.0, 0.0]


# --- round parts (from the Nile monitor) -----------------------------------------------------------------------------
def rounded(cubes, cx, cy, rw, rh, z0, z1, part):
    """A round cross-section from z0 to z1, rw wide and rh tall (radii): a wide box, a tall box and one between (an
    eight-sided outline); one plain box when it is thinner than about a pixel."""
    if min(rw, rh) < 0.9:
        cubes.append(cube([cx - rw, cy - rh, z0], [cx + rw, cy + rh, z1], part))
        return
    cubes.append(cube([cx - rw, cy - rh * 0.55, z0], [cx + rw, cy + rh * 0.55, z1], part))
    cubes.append(cube([cx - rw * 0.55, cy - rh, z0], [cx + rw * 0.55, cy + rh, z1], part))
    cubes.append(cube([cx - rw * 0.85, cy - rh * 0.85, z0], [cx + rw * 0.85, cy + rh * 0.85, z1], part))


# A finer round cross-section for legs (the user, 2026-11-17: the legs needed more pixels and to be round to look
# good): four boxes at quarter-pixel sizes (wide, tall and two between), close to a circle. A piece is cut into
# half-pixel slices along its own line, each slice's radius read off its profile and rounded to a quarter pixel, and
# slices of the same size joined into one, so a taper costs boxes only where it really changes.
ROUND = ((1.0, 0.4), (0.4, 1.0), (0.93, 0.68), (0.68, 0.93))


def quarter(r):
    return round(r * 4) / 4


def disc(cubes, axis, centre, r, a0, a1, part):
    """One round slice of radius r along an axis (0 x, 1 y, 2 z) from a0 to a1, its middle at centre (a point)."""
    u, v = [i for i in range(3) if i != axis]
    lo, hi = sorted((a0, a1))
    for fu, fv in (ROUND if r >= 0.9 else ((1.0, 1.0),)):
        a, b = list(centre), list(centre)
        a[axis], b[axis] = lo, hi
        a[u], b[u] = centre[u] - quarter(r * fu), centre[u] + quarter(r * fu)
        a[v], b[v] = centre[v] - quarter(r * fv), centre[v] + quarter(r * fv)
        if a[u] < b[u] and a[v] < b[v]:
            cubes.append(cube(a, b, part))


def profile(prof, t):
    """A radius read off a profile ((fraction along, radius), ...), eased between its points."""
    for (t0, r0), (t1, r1) in zip(prof, prof[1:]):
        if t <= t1:
            return r0 + (r1 - r0) * smooth((t - t0) / (t1 - t0))
    return prof[-1][1]


def limb(cubes, axis, centre, a0, a1, prof, part, lead=0.0):
    """A round piece from a0 (fraction 0) to a1 (fraction 1) along an axis, its radius following prof; lead runs the
    first slice that far back past a0 (into the body)."""
    n = max(1, round(abs(a1 - a0) / 0.5))
    runs = []
    for i in range(n):
        r = quarter(profile(prof, (i + 0.5) / n))
        s0, s1 = a0 + (a1 - a0) * i / n, a0 + (a1 - a0) * (i + 1) / n
        if runs and runs[-1][0] == r:
            runs[-1][2] = s1
        else:
            runs.append([r, s0, s1])
    if lead:
        runs[0][1] -= lead * (1 if a1 > a0 else -1)
    for r, s0, s1 in runs:
        disc(cubes, axis, centre, r, s0, s1, part)


def ball(cubes, axis, centre, r, part):
    """A round joint: half-pixel slices along an axis, each as wide as the ball is there."""
    n = max(2, round(2 * r / 0.5))
    runs = []
    for i in range(n):
        z0, z1 = -r + 2 * r * i / n, -r + 2 * r * (i + 1) / n
        zm = max(abs(z0), abs(z1)) if i not in (n // 2 - 1, n // 2) else 0.0
        rr = quarter(math.sqrt(max(0.0, r * r - (zm * 0.85) ** 2)))
        if runs and runs[-1][0] == rr:
            runs[-1][2] = z1
        else:
            runs.append([rr, z0, z1])
    for rr, z0, z1 in runs:
        disc(cubes, axis, centre, rr, centre[axis] + z0, centre[axis] + z1, part)


# --- a bent chain: a tail ----------------------------------------------------------------------------------------------
def bent_chain(prefix, parent, z_start, axis_y, length, segs, radius, height, part='skin', taller=0.2):
    """A long round piece reaching back (+Z) from z_start at height axis_y, as segs bones named prefix1, prefix2, ...
    each hanging from the one before (the first from parent). Each piece is a rounded() section of radius(t) (t the
    fraction along), a little taller than wide toward the tip (taller); each bone is turned down by the angle that puts
    the chain's middle line at height(t) (the monitor's tail: dropping from the body until it lies on the ground). Each
    piece reaches back into the one before far enough to close the wedge its turn opens."""
    L = length / segs
    angles = []
    for i in range(segs):
        drop = height(i / segs) - height((i + 1) / segs)
        angles.append(math.degrees(math.asin(max(-0.9, min(0.9, drop / L)))))
    out, prev = [], 0.0
    for i in range(segs):
        z0, z1 = z_start + i * L, z_start + (i + 1) * L
        t = (i + 0.5) / segs
        r = radius(t)
        c = []
        back = r * 1.2 * math.tan(math.radians(abs(angles[i] - prev))) + 0.3     # close the joint's wedge
        rounded(c, 0.0, axis_y, r, r * (1 + taller * t), z0 - back, z1, part)
        name = f'{prefix}{i + 1}'
        out.append({'name': name, 'parent': parent, 'pivot': [0.0, axis_y, z0],
                    'rotation': pitch(angles[i] - prev), 'cubes': c})
        prev, parent = angles[i], name
    return out


# --- a round leg with clawed toes ---------------------------------------------------------------------------------------
# A leg plan (the settings; the monitor's LEG_PLANS are the approved examples):
#   'joints'  shoulder or hip, elbow or knee, wrist or ankle (the left side; the right is the mirror), model pixels;
#   'upper', 'lower'  each piece's radius along it ((fraction along, radius), ...): thick at the muscle, slimmer toward
#             the next joint; 'knob', 'wrist'  the radius of the round elbow or knee and of the wrist or ankle;
#   'hinge'   True for a leg that folds a long way at the knee (thigh and shin turn about one hinge line);
#   'turn'    how far the flat foot turns out (degrees); 'palm'  its width and length;
#   'toes'    (length, fan angle outward +) for each toe, inner to outer: as many as wanted;
#   'raise'   how far every toe lifts at the knuckle (degrees).
# A claw shape: steps of a hook from the toe's tip ((from, to) along it, width, bottom and top against the toe's middle
# line, paint part), each step quarter-pixel sized; claw_drop bends the whole claw down from its toe (degrees).
def leg_frames(s, plan, S, E, W):
    """The upper and lower pieces' turns for joints S, E, W (s = 1 left, -1 right; shared by the model and the
    clips)."""
    r1 = aim((s, 0, 0), vsub(E, S))                         # the upper part's own axis is straight out
    r2 = aim((0, -1, 0), vsub(W, E))                        # the lower part's is straight down
    if plan.get('hinge'):
        # A leg folding a long way at the knee: the thigh and shin share one hinge line there (the line across both),
        # so their boxes turn about it like a real knee instead of twisting against each other (the user's report on
        # the monitor: the hind leg looked broken). The ankle block then sits in the shin's line.
        d1, d2 = unit(vsub(E, S)), unit(vsub(W, E))
        h = unit(cross(d1, d2))
        x1 = [s * c for c in d1]
        r1 = from_axes(x1, cross(h, x1), h)
        y2 = [-c for c in d2]
        r2 = from_axes(cross(y2, h), y2, h)
    return r1, r2


def round_leg(kind, plan, s, side, parent, claw_shape, claw_drop, part='leg'):
    """One leg's bones (named <kind>_upper_<side>, _lower_, _foot_, _toe1_..., _claw1_...): an upper piece and a lower
    one aimed between its joints, a foot flat on the ground turned out, and the fanned toes, each raised a little at the
    knuckle with a claw hooking down from its tip to the ground (a claw is its own little bone so it can bend down
    while the toe lifts, and so the clips can flex it). s = 1 for the left leg, -1 for the right."""
    out = []
    (sx, sy, sz), (ex, ey, ez), (wx, wy, wz) = plan['joints']
    S, E, W = (s * sx, sy, sz), (s * ex, ey, ez), (s * wx, wy, wz)
    l1, l2 = vlen(vsub(E, S)), vlen(vsub(W, E))
    r1, r2 = leg_frames(s, plan, S, E, W)
    r3 = turn_y(-s * plan['turn'])                           # the foot: flat, turned out
    e_rest = (S[0] + s * l1, S[1], S[2])                     # the joints before the turns are applied
    w_rest = (e_rest[0], e_rest[1] - l2, e_rest[2])

    # Round pieces tapering from the muscle to the next joint, and a round ball at each joint a little bigger than the
    # ends meeting there, both pieces ending at its middle, so nothing pokes out at the bend.
    ey_ = e_rest[1]
    up, low = [], []
    limb(up, 0, S, S[0], S[0] + s * l1, plan['upper'], part, lead=1.0)
    ball(up, 0, e_rest, plan['knob'], part)                                              # elbow or knee
    limb(low, 1, e_rest, ey_, ey_ - l2, plan['lower'], part)
    pw, pl = plan['palm']
    g = -W[1]                                                # the ground, below the wrist
    fx, fy, fz = w_rest
    ankle = []
    ball(ankle, 1, w_rest, plan['wrist'], part)                                          # wrist or ankle
    if plan.get('hinge'):
        low += ankle
    foot = ([] if plan.get('hinge') else ankle) + [
            cube([fx - pw / 2, fy + g, fz - pl + 1.0], [fx + pw / 2, fy + g + 1.6, fz + 1.4], part),             # palm
            cube([fx - pw / 2 + 0.5, fy + g + 1.6, fz - pl + 1.6], [fx + pw / 2 - 0.5, fy + g + 2.4, fz + 1.2], part)]

    def name(piece):
        return f'{kind}_{piece}_{side}'
    out.append({'name': name('upper'), 'parent': parent, 'pivot': list(S), 'rotation': rot(r1), 'cubes': up})
    out.append({'name': name('lower'), 'parent': name('upper'), 'pivot': list(e_rest),
                'rotation': rot(mat_mul(transpose(r1), r2)), 'cubes': low})
    out.append({'name': name('foot'), 'parent': name('lower'), 'pivot': list(w_rest),
                'rotation': rot(mat_mul(transpose(r2), r3)), 'cubes': foot})
    n = len(plan['toes'])
    for i, (tl, fan) in enumerate(plan['toes']):
        tx = fx + s * (-pw / 2 + 0.55 + i * (pw - 1.1) / (n - 1))
        ty, tz = fy + g + 0.55, fz - pl + 1.2                # the knuckle, at the palm's front edge
        toe = [cube([tx - 0.55, ty - 0.55, tz - tl * 0.55], [tx + 0.55, ty + 0.55, tz + 0.4], part),
               cube([tx - 0.42, ty - 0.45, tz - tl], [tx + 0.42, ty + 0.4, tz - tl * 0.5], part)]
        cy, cz = ty + 0.05, tz - tl + 0.1                    # the claw's root, at the toe's tip
        claw = [cube([tx - w / 2, cy + lo, cz - z1], [tx + w / 2, cy + hi, cz - z0], p)
                for z0, z1, w, lo, hi, p in claw_shape]
        out.append({'name': name(f'toe{i + 1}'), 'parent': name('foot'), 'pivot': [tx, ty, tz],
                    'rotation': rot(mat_mul(turn_y(-s * fan), turn_x(plan['raise']))), 'cubes': toe})
        out.append({'name': name(f'claw{i + 1}'), 'parent': name(f'toe{i + 1}'), 'pivot': [tx, cy, cz],
                    'rotation': rot(turn_x(-claw_drop)), 'cubes': claw})
    return out
