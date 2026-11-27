"""The motion kit (the Animal Maker Engine step C, 2026-11-20): the shared ways of building an animal's clips, so the
first upright mammal, spider or snake does not start its leg maths over.

Ways of building only, never an animal's numbers (the engine's rule 5, memory "unique animals, shared methods"): each
animal's maker keeps its own strides, sways, timings and poses and calls these to work them out. Pulled out of the
Nile monitor's maker (_Poser.leg's reach, _bend_way, _foot_at, _spline, _ease, _pulse, _ramp) and the crocodile's
(the hatchlings' _stride), the game files proved byte for byte the same after the move. The insects' legs
(tools/ant_motion.py, a trial-and-error search) are not touched.

- limbs of 2, 3 or 4 bones reaching a foot place, solved directly with trigonometry (no search), with a pole direction
  per joint so an elbow or knee always bends the way it is told and never folds into the body: two_bone, limb;
- feet on a plan of steps (foot_at) and a stride's timing (stride);
- a chain following a path (follow_path, headings): each piece placed along the path the head took, eased as places,
  not angles (the monitor's S-curve today; later the snake, tails, the trunk, the millipede);
- easing helpers (ease, pulse, ramp, spline);
- strikes and lunges that carry themselves (the Motion Studio phase 3, step 4, 2026-11-26; first the lioness): the
  travel's speed eased in and out (travel), written as the clip's "path root" note the game reads through the animal's
  spot table (path_note), and a foot target kept planted on the ground while the body goes along the path (on_path);
- per-clip fingerprints (cached): a clip is rebuilt only when what it is made from changed (the tools review's K1).

Units are whatever the maker uses (model pixels in every maker so far); points are [x, y, z] lists.
"""
import atexit
import hashlib
import json
import math
import sys
from pathlib import Path

from model_parts import smooth, vsub, vadd, vlen, unit

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / 'run-film' / 'clip_cache'


# --- strikes that carry themselves (the Motion Studio phase 3, step 4) ------------------------------------------------
# A strike (a leap, a lunge, a pounce) is made once with its own travel (the engine's method M1, as the block moves):
# the clip poses the body with its root kept at 0; its travel goes into a "path root" note; tools/spots.py writes the
# travel's share a tick into the animal's spot table (its plan's "strikes": the clip and its hit tick), and the game
# places the animal that share of the way from where it set off to its place on its prey (framework Strikes). How to
# make one: compute dz = -travel(t, ...) each frame, add on_path(...) to each foot's target z, set the root to dz while
# the legs are solved, set it back to 0, and put path_note(L, path) in the clip's timeline.
def travel(t, v, start, up, down_from, end):
    """How far the body has gone forward at time t (the maker's units, usually px): still until start, its speed easing
    up to v (units a second) by up, held, easing down to 0 from down_from to end (each ease a smoothstep of the speed,
    so the travel has no jolt). start == up: it sets off at v (a leap out of a run)."""
    a, b = up - start, end - down_from
    if t <= start:
        return 0.0
    if t <= up:
        u = (t - start) / a
        return v * a * (u ** 3 - u ** 4 / 2.0)
    if t <= down_from:
        return v * a / 2.0 + v * (t - up)
    u = min(1.0, (t - down_from) / b)
    return v * a / 2.0 + v * (down_from - up) + v * b * (u - u ** 3 + u ** 4 / 2.0)


def path_note(length, path):
    """A clip's travel as its note ("path root t dy dz ...", one row a game tick, forward as -z), path(t) being how far
    it has gone forward at time t; the lab and clip_check.py add it back to the root."""
    n = round(length * 20)
    return "path root " + " ".join(f"{k / 20:.4f} 0.000 {-path(k / 20):.3f}" for k in range(n + 1))


def on_path(free, landed, dz, dz_end):
    """What to add to a foot target's z (forward as -z) on a clip with travel, so a planted foot stays where it is in the
    world: 0 where it stood before the travel, dz_end where it lands, the body's own dz while the foot is free (free:
    its leg's air or FK weight, 0 to 1; landed: whether it is past the middle of the flight)."""
    return (1.0 - free) * (dz_end if landed else 0.0) + free * dz


# --- easing ------------------------------------------------------------------------------------------------------------
def ease(x):
    """0 to 1 eased in and out (smoothstep)."""
    return smooth(x)


def pulse(t, start, rise, hold, fall):
    """0 before start, easing up to 1 over rise, held, easing back to 0 over fall."""
    if t < start:
        return 0.0
    if t < start + rise:
        return ease((t - start) / rise)
    if t < start + rise + hold:
        return 1.0
    return 1.0 - ease((t - start - rise - hold) / fall)


def ramp(t, keys):
    """A value easing from key to key ((time, value), ...); held before the first and after the last."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            return v0 + (v1 - v0) * ease((t - t0) / (t1 - t0))
    return keys[-1][1]


def spline(t, keys, start=0.0, end=0.0):
    """A value passing smoothly through its keys ((time, value), ...) with no overshoot (a monotone cubic); held
    before the first and after the last (going on at the end slope after it, when one is given)."""
    if t <= keys[0][0]:
        return keys[0][1] + start * (t - keys[0][0])
    if t >= keys[-1][0]:
        return keys[-1][1] + end * (t - keys[-1][0])
    n = len(keys)
    d = [(keys[i + 1][1] - keys[i][1]) / (keys[i + 1][0] - keys[i][0]) for i in range(n - 1)]
    m = [0.0] * n                                    # the first and last keys start and stop at rest (or at the slopes given)
    m[0], m[-1] = start, end
    for i in range(1, n - 1):
        m[i] = 0.0 if d[i - 1] * d[i] <= 0 else 2.0 / (1.0 / d[i - 1] + 1.0 / d[i])
    for i in range(n - 1):
        (t0, v0), (t1, v1) = keys[i], keys[i + 1]
        if t <= t1:
            h = t1 - t0
            u = (t - t0) / h
            return (v0 * (2 * u ** 3 - 3 * u ** 2 + 1) + m[i] * h * (u ** 3 - 2 * u ** 2 + u)
                    + v1 * (-2 * u ** 3 + 3 * u ** 2) + m[i + 1] * h * (u ** 3 - u ** 2))
    return keys[-1][1]


# --- limbs ---------------------------------------------------------------------------------------------------------------
# Review R12-27: every limb solved tells how far its end fell short, and the makers threw that away. The kit keeps the
# worst itself, under the clip the maker says it is on (clip_now; none said: one line for the whole run), and says so
# when the maker ends: a hoof left short of the ground is heard of here, not later in the clip check or a film.
MISS_SAID = 0.05                                     # units; smaller is the reach limit's rounding
_MISSES = {}                                         # clip -> [the worst miss, how many poses fell short]
_CLIP_NOW = ['']


def clip_now(name):
    """A maker says which clip it is posing now, so the misses are told by clip."""
    _CLIP_NOW[0] = name


def _missed(miss):
    if miss > MISS_SAID:
        row = _MISSES.setdefault(_CLIP_NOW[0], [0.0, 0])
        row[0], row[1] = max(row[0], miss), row[1] + 1
    return miss


def misses():
    """The worst miss and the number of poses that fell short, by clip, since the maker began."""
    return {k: tuple(v) for k, v in _MISSES.items()}


def _say_misses():
    for name, (worst, n) in sorted(_MISSES.items()):
        print(f'motion_kit: {name or "this run"}: {n} limb pose(s) fell short of their target, the worst by {worst:.2f} units '
              '(the pose asks the limb for more than it has)')


atexit.register(_say_misses)


def bend_way(pole, dh):
    """Which way a joint between two pieces bends (a unit direction square to dh, the line from the leg's root to its
    end): pole, less its part along dh."""
    k = sum(x * y for x, y in zip(pole, dh))
    square = [x - k * y for x, y in zip(pole, dh)]
    size, whole = vlen(square), vlen(pole)
    if size >= 0.2 * whole:
        return unit(square)
    # Review R12-26: the limb's line runs (nearly) along the pole: its square part is too short to trust (exactly along
    # it, a division by zero; just off it, a knee flipping sides as the end crosses the line). The way eases over to
    # a second way square to the line (from the world axis least along it), so it changes smoothly across the line.
    axis = min(([1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]), key=lambda ax: abs(sum(x * y for x, y in zip(ax, dh))))
    j = sum(x * y for x, y in zip(axis, dh))
    second = unit([x - j * y for x, y in zip(axis, dh)])
    w = size / (0.2 * whole) if whole > 1e-12 else 0.0
    mixed = [(s / size * w if size > 1e-12 else 0.0) + f * (1.0 - w) for s, f in zip(square, second)]
    return unit(mixed) if vlen(mixed) > 1e-6 else second


def two_bone(root, l1, l2, target, pole, reach=0.985):
    """A two-piece limb from root reaching target, its joint bending toward pole (any direction; only its part square
    to the root-target line counts). Solved directly (the triangle's sides are known). Returns (joint, end, miss): the
    end is target, or as far toward it as the limb goes (reach: the share of its full length it may straighten to, so
    a joint never locks straight and flips), and miss how far short it fell (0 within reach)."""
    d = vsub(target, root)
    D = vlen(d)
    most = reach * (l1 + l2)
    miss = 0.0
    if D > most:                                     # out of reach: as far as it goes
        miss = D - most
        d = [c * most / D for c in d]
        target, D = vadd(root, d), most
    least = max(abs(l1 - l2) * 1.02, 1e-6)
    if D < least:
        # Review R12-25: nearer than the pieces can fold to: the end goes out to the nearest place they reach (along
        # the same line, or straight down from the root with no line), and the miss says by how much. Before, the
        # shorter line was divided by the raised distance: both pieces came out short with the miss at 0, and an end
        # at the root itself divided by zero.
        line = [c / D for c in d] if D > 1e-9 else [0.0, -1.0, 0.0]
        miss = least - D
        d = [c * least for c in line]
        target, D = vadd(root, d), least
    dh = [c / D for c in d]
    a = (l1 ** 2 - l2 ** 2 + D * D) / (2 * D)
    h = math.sqrt(max(0.0, l1 ** 2 - a * a))
    way = bend_way(pole, dh)
    return [root[i] + a * dh[i] + h * way[i] for i in range(3)], target, _missed(miss)


def limb(root, lengths, target, pole, ends=None, parallel=False, reach=0.985):
    """A limb of 2, 3 or 4 pieces from root reaching target, solved directly, never by a search:
    - 2 pieces: two_bone;
    - more, with ends: the last pieces point the given ways (unit directions, root to tip: a foot set flat, a hoof
      upright), so the first two reach for what is left (an upright mammal's foot and toe);
    - 3 pieces, parallel: the third piece stays parallel to the first (a pantograph, as a cat's or a dog's hind leg: the
      shin and the long foot turn together), which is two pieces of l1 + l3 and l2.
    pole is the way the first joint bends; with ends the later joints follow from the given directions. Returns (the
    joint places root first, tip last, and the miss)."""
    n = len(lengths)
    if n == 2:
        j, e, miss = two_bone(root, lengths[0], lengths[1], target, pole, reach)
        return [list(root), j, e], miss
    if parallel and n == 3 and not ends:
        l1, l2, l3 = lengths
        j, e, miss = two_bone(root, l1 + l3, l2, target, pole, reach)
        u = unit(vsub(j, root))
        j1 = vadd(root, [c * l1 for c in u])
        j2 = vadd(j1, vsub(e, j))
        return [list(root), j1, j2, e], miss
    if not ends or len(ends) != n - 2:
        raise ValueError(f'a limb of {n} pieces needs {n - 2} end directions (or parallel for 3)')
    tail = [0.0, 0.0, 0.0]
    for l, d in zip(lengths[2:], ends):
        tail = vadd(tail, [c * l for c in unit(d)])
    j, e, miss = two_bone(root, lengths[0], lengths[1], vsub(target, tail), pole, reach)
    out = [list(root), j, e]
    for l, d in zip(lengths[2:], ends):
        out.append(vadd(out[-1], [c * l for c in unit(d)]))
    return out, miss


def digitigrade_leg(root, lengths, target, pole=(0.0, 0.0, -1.0), hock_deg=22.0, side_yaw_deg=0.0, reach=0.985):
    """A 3-segment digitigrade mammal/feline hind leg (thigh, shin, metatarsal/hock -> ankle) solved directly.
    hock_deg controls how far the hock juts back (+Z) from vertical: 0 deg = straight sprint push-off, ~22 deg = alert
    feline stance, 60..75 deg = deep crouched stalk or sit. Returns (joints [hip, knee, hock, ankle], miss)."""
    l1, l2, l3 = lengths
    hr = math.radians(hock_deg)
    yr = math.radians(side_yaw_deg)
    # Direction from hock down to ankle (down -Y, forward -Z, optional slight outward yaw)
    meta_dir = unit([math.sin(hr) * math.sin(yr), -math.cos(hr), -math.sin(hr) * math.cos(yr)])
    hock_target = vsub(target, [c * l3 for c in meta_dir])
    knee, hock, miss = two_bone(root, l1, l2, hock_target, pole, reach)
    ankle = vadd(hock, [c * l3 for c in meta_dir])
    return [list(root), knee, hock, ankle], miss


# --- quadruped gaits & secondary motion (Animal Maker Engine upgrade, 2026-11-23) ---------------------------------------
def quadruped_gait(phase, phases, duty, stride_len, lift_h, bounce=0.0, suspend_h=0.0, spine_flex=0.0,
                   cycle_s=1.0):
    """Parameterized 4-legged gait evaluator (walk, stealth stalk, trot, rotary gallop / bound with airborne flight).
    Every animal passes its own timings and amplitudes (Rule 5: shared methods, unique animals).

    phases:     dict of phase offsets (0..1) for ('bl', 'fl', 'br', 'fr')
    duty:       float or dict per foot (fraction of cycle planted; < 0.5 creates airborne suspension in a gallop)
    stride_len: float or dict per foot (ground travel while planted, model px)
    lift_h:     float or dict per foot (peak paw lift height during swing, model px)
    Returns {'feet', 'body_y', 'body_pitch', 'spine_pitch', 'airborne', 'speed_px_s'}."""
    feet = {}
    planted_count = 0
    for key in ('bl', 'fl', 'br', 'fr'):
        ph = (phase + phases[key]) % 1.0
        d = duty[key] if isinstance(duty, dict) else duty
        sl = stride_len[key] if isinstance(stride_len, dict) else stride_len
        lh = lift_h[key] if isinstance(lift_h, dict) else lift_h
        sw, lf = stride(ph, d)
        planted = ph < d
        if planted:
            planted_count += 1
        feet[key] = {'swing': sw, 'dz': -sw * (sl * 0.5), 'lift': lf, 'dy': lf * lh, 'planted': planted}
    front_sw = 0.5 * (feet['fl']['swing'] + feet['fr']['swing'])
    hind_sw = 0.5 * (feet['bl']['swing'] + feet['br']['swing'])
    air_frac = max(0.0, 1.0 - planted_count / 2.0)  # 0 when 2+ feet down, 0.5 at 1 foot, 1.0 full airborne flight
    # Spine gathers (arches up) when hind feet swing forward under the belly and extends when front feet reach forward
    spine_pitch = spine_flex * 0.5 * (hind_sw - front_sw)
    body_pitch = -spine_flex * 0.35 * (front_sw + hind_sw) * 0.5
    body_y = bounce * math.sin(2.0 * math.pi * (phase * 2.0)) + suspend_h * ease(air_frac)
    avg_duty = sum(duty.values()) / len(duty) if isinstance(duty, dict) else duty
    avg_stride = sum(stride_len.values()) / len(stride_len) if isinstance(stride_len, dict) else stride_len
    return {
        'feet': feet,
        'body_y': body_y,
        'body_pitch': body_pitch,
        'spine_pitch': spine_pitch,
        'airborne': planted_count == 0,
        'speed_px_s': avg_stride / max(1e-6, avg_duty * cycle_s),
    }


def secondary_lag(samples, dt, lag_s, damping=0.82, loop=True):
    """2nd-order damped spring lag over a sequence of scalars or vectors (for living mane, tail tuft, and ear inertia).
    When loop=True, pre-settles for 2 cycles so the last frame blends seamlessly into the first with zero pop."""
    if not samples or lag_s <= 1e-6:
        return [list(s) if isinstance(s, (list, tuple)) else s for s in samples]
    is_vec = isinstance(samples[0], (list, tuple))
    vecs = [list(s) for s in samples] if is_vec else [[float(s)] for s in samples]
    dim = len(vecs[0])
    omega = 2.0 / max(lag_s, dt * 1.5)
    zeta = max(0.35, min(1.5, damping))
    y = list(vecs[0])
    v = [0.0] * dim
    n = len(vecs)
    # Sub-step for unconditional numerical stability even at coarse sample rates
    sub = max(1, int(math.ceil(dt * omega * 2.5)))
    h = dt / sub
    passes = 3 if loop else 1
    out = []
    for p in range(passes):
        cur = []
        for idx in range(n):
            tgt = vecs[idx]
            for _ in range(sub):
                for j in range(dim):
                    acc = omega * omega * (tgt[j] - y[j]) - 2.0 * zeta * omega * v[j]
                    v[j] += acc * h
                    y[j] += v[j] * h
            cur.append(list(y))
        out = cur
    return out if is_vec else [c[0] for c in out]


# --- feet over time ------------------------------------------------------------------------------------------------------
def stride(ph, duty):
    """One foot's step at phase ph (0 to 1): its swing (+1 forward, -1 back) and lift (0 to 1). Planted for the first
    duty share, going back evenly (so it stays put on the ground while the body goes on), then lifted and swung forward."""
    ph %= 1.0
    if ph < duty:
        return 1.0 - 2.0 * ph / duty, 0.0
    u = (ph - duty) / (1.0 - duty)
    return -1.0 + 2.0 * (0.5 - 0.5 * math.cos(math.pi * u)), math.sin(math.pi * u)


def foot_at(t, plan, lift, reach=False):
    """Where a foot's wrist or ankle is at time t on its plan (its first place, then its steps: lift time, land time,
    place), how far it is lifted (0 to 1) and how far the foot is turned toes up (degrees, a place's fourth value). A
    hind foot stepping up a face lifts first and reaches forward over it after; a forefoot reaching up (reach) goes
    forward and up together in one arc, the rise a little ahead, so it stays in front of its shoulder (rising first on
    the spot while the body went on, it ended up above and behind the shoulder, the elbow thrown up over the back).
    Places are (x, height, z) with z forward as negative (every maker's axes); a rise or drop over 2 units is a step."""
    at = list(plan[0][:3])
    pitch = plan[0][3] if len(plan[0]) > 3 else 0.0
    for t0, t1, to in plan[1:]:
        goes = to[3] if len(to) > 3 else 0.0
        if t <= t0:
            return at, 0.0, pitch
        if t < t1:
            f = (t - t0) / (t1 - t0)
            rise = to[1] - at[1]
            if rise < -2.0:                          # going down an edge: out over it first, then down (never into it)
                fy, fz = ease(max(0.0, (f - 0.3) / 0.7)), ease(min(1.0, f / 0.5))
            elif rise <= 2.0:
                fy = fz = ease(f)
            elif reach:
                fy, fz = ease(min(1.0, f / 0.65)), ease(f)
            else:
                fy, fz = ease(min(1.0, f / 0.6)), ease(max(0.0, (f - 0.35) / 0.65))
            arc = math.sin(math.pi * f)
            # toes turning up onto a face follow the reach forward (never into the face); toes coming off a face
            # flatten in the last of the rise, as the foot comes over the edge (not a hand waving above it)
            # going down, the toes turn down onto a face as the foot starts down it, and flatten as it nears the ground
            turn = ((ease(max(0.0, (fy - 0.6) / 0.4)) if goes < pitch else fz) if rise > 2.0
                    else (ease(min(1.0, 1.5 * fy)) if goes < pitch else ease(min(1.0, max(0.0, (fy - 0.2) / 0.6)))) if rise < -2.0
                    else ease(f))
            return ([at[0] + (to[0] - at[0]) * ease(f), at[1] + rise * fy + lift * arc, at[2] + (to[2] - at[2]) * fz],
                    arc, pitch + (goes - pitch) * turn)
        at, pitch = list(to[:3]), goes
    return at, 0.0, pitch


# --- chains following a path ---------------------------------------------------------------------------------------------
def follow_path(path, lengths):
    """A chain laid along a path: path is a line of points from the head back (where the head is now, then where it
    was; or any curve sampled finely), lengths the pieces from the front back. Each joint is placed ON the path exactly
    its piece's length (straight line) behind the joint before, so pieces never stretch and the body goes where its head
    went: places are eased along the path, never angles. Past the path's end the last direction carries on. Returns the
    joints, front first (len(lengths) + 1 points)."""
    pts = [list(p) for p in path]
    out = [pts[0]]
    i = 0                                            # the path's segment the last joint lies on (pts[i] to pts[i + 1])
    for L in lengths:
        c = out[-1]
        found = None
        while i < len(pts) - 1:
            a, b = pts[i], pts[i + 1]
            # where along a-b (0 to 1) the point lies L from c, past the joint's own place on it
            ab, ca = vsub(b, a), vsub(a, c)
            A = sum(x * x for x in ab)
            B = 2 * sum(x * y for x, y in zip(ab, ca))
            C = sum(x * x for x in ca) - L * L
            disc = B * B - 4 * A * C
            if A > 1e-12 and disc >= 0:
                s = (-B + math.sqrt(disc)) / (2 * A)
                if 0.0 <= s <= 1.0:
                    found = [a[k] + s * ab[k] for k in range(3)]
                    pts[i] = found                   # the next joint looks on from here
                    break
            i += 1
        if found is None:                            # off the end: straight on the last way
            a, b = pts[-2], pts[-1]
            d = unit(vsub(b, a)) if vlen(vsub(b, a)) > 1e-9 else [0.0, 0.0, 1.0]
            found = vadd(c, [x * L for x in d])
            pts.append(found)
            i = len(pts) - 2
        out.append(found)
    return out


def headings(joints):
    """Each piece's heading from front-first joints, as atan2(dx, dz) in degrees of the line from a joint to the one
    behind it (0 when the piece lies straight back along +z): for turning a chain's bones to lie along it."""
    return [math.degrees(math.atan2(b[0] - a[0], b[2] - a[2])) for a, b in zip(joints, joints[1:])]


def curve(fn, s0, s1, n=64):
    """A curve sampled finely for follow_path: fn(s) gives a point for s from s0 to s1 (front first)."""
    return [fn(s0 + (s1 - s0) * k / n) for k in range(n + 1)]


# --- per-clip fingerprints (K1) -------------------------------------------------------------------------------------------
def fingerprint(*parts, files=()):
    """A short fingerprint of what a clip is made from: any values (written as JSON) and the source files that make it
    (the maker, this kit, model_parts), so a change to any of them makes a new one."""
    h = hashlib.sha1()
    for p in parts:
        h.update(json.dumps(p, sort_keys=True, default=repr).encode())
    # (review R12-28: every tools module loaded now counts, the maker and all it imports, not only this kit and
    # model_parts: a helper changed kept the old clips. A maker names the data files it reads in files.)
    tools = Path(__file__).resolve().parent
    loaded = sorted({Path(m.__file__).resolve() for m in list(sys.modules.values())
                     if getattr(m, '__file__', None) and Path(m.__file__).resolve().parent == tools})
    for f in sorted({Path(f).resolve() for f in files} | set(loaded) | {Path(__file__).resolve(), tools / 'model_parts.py'}):
        h.update(f.read_bytes())
    return h.hexdigest()[:16]


def cached(animal, name, key, build):
    """build() made only when its fingerprint (key, from fingerprint) is new; otherwise its last result read back
    (run-film/clip_cache/<animal>/<name>.json, never committed). The result must be plain JSON data."""
    path = CACHE / animal / f'{name}.json'
    try:
        held = json.loads(path.read_text(encoding='utf-8'))
        if held.get('key') == key:
            return held['made']
    except (OSError, ValueError):
        pass
    made = build()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({'key': key, 'made': made}), encoding='utf-8')
    return made


# --- a self-check: python tools/motion_kit.py (pieces keep their lengths, joints bend toward their poles) -------------------
if __name__ == '__main__':
    def near(a, b, eps=1e-6):
        return abs(a - b) < eps
    for lengths, kw in (([3.0, 4.0], {}), ([3.0, 4.0, 2.0], {'parallel': True}),
                        ([3.0, 4.0, 1.0, 1.0], {'ends': [[0, -1, 0], [0, 0, -1]]})):
        for target in ([0.0, -5.0, 0.0], [1.0, -6.0, -1.5], [0.0, -40.0, 0.0]):
            joints, miss = limb([0.0, 0.0, 0.0], lengths, target, [0.0, 0.0, -1.0], **kw)
            assert all(near(vlen(vsub(b, a)), L) for a, b, L in zip(joints, joints[1:], lengths)), (lengths, target)
            assert joints[1][2] <= 1e-9, ('the first joint bends toward its pole', lengths, target)
            assert miss > 0 or near(vlen(vsub(joints[-1], target)), 0.0), (lengths, target, miss)
    # (review R12-25) an end too near the root: the pieces keep their lengths and the miss says so; at the root itself too
    for l1, l2, end in ((3.0, 4.0, [0.0, -0.5, 0.0]), (4.0, 4.0, [0.0, 0.0, 0.0]), (3.0, 4.0, [0.0, 0.0, 0.0])):
        joint, tip, miss = two_bone([0.0, 0.0, 0.0], l1, l2, end, [0.0, 0.0, -1.0])
        assert near(vlen(joint), l1) and near(vlen(vsub(tip, joint)), l2), ('pieces keep their lengths', l1, l2, end)
        assert (miss > 0) == (vlen(end) < abs(l1 - l2) * 1.02 or l1 == l2 and vlen(end) < 1e-6), (l1, l2, end, miss)
    # (review R12-26) an end on the pole's line: no crash, and the joint does not jump sides as the end crosses it
    two_bone([0.0, 0.0, 0.0], 3.0, 4.0, [0.0, 0.0, -6.0], [0.0, 0.0, -1.0])
    knees = [two_bone([0.0, 0.0, 0.0], 3.0, 4.0, [0.0, y, -6.0], [0.0, 0.0, -1.0])[0] for y in (0.05, 0.01, -0.01, -0.05)]
    assert all(vlen(vsub(a, b)) < 0.6 for a, b in zip(knees, knees[1:])), ('the joint jumps as the end crosses the pole', knees)
    pts = curve(lambda s: [2 * math.sin(s / 5), 0.0, s], 0.0, 40.0, 200)
    js = follow_path(pts, [4.0] * 6)
    assert all(near(vlen(vsub(b, a)), 4.0) for a, b in zip(js, js[1:]))
    assert near(stride(0.0, 0.6)[0], 1.0) and near(stride(0.6, 0.6)[1], 0.0)
    # Digitigrade 3-segment feline hind leg: exact lengths & knee bends forward (-Z), hock sits behind (+Z)
    for hock_deg in (0.0, 24.0, 62.0):
        dj, dmiss = digitigrade_leg([0.0, 14.0, 0.0], [6.5, 6.0, 4.0], [0.0, 0.5, -1.0], hock_deg=hock_deg)
        assert all(near(vlen(vsub(b, a)), L) for a, b, L in zip(dj, dj[1:], [6.5, 6.0, 4.0]))
        assert dmiss == 0.0 and dj[1][2] <= dj[2][2] + 1e-6
    # Quadruped rotary gallop has real airborne suspension frames
    gallop_phases = {'bl': 0.0, 'br': 0.12, 'fl': 0.45, 'fr': 0.58}
    air_frames = [quadruped_gait(k / 40.0, gallop_phases, 0.28, 22.0, 5.0, suspend_h=3.0, spine_flex=14.0)['airborne']
                  for k in range(40)]
    assert any(air_frames) and not all(air_frames), 'gallop must mix planted and airborne suspension frames'
    # Secondary lag smooths a wave and closes a loop cleanly
    wave = [math.sin(2.0 * math.pi * k / 24.0) * 10.0 for k in range(24)]
    lagged = secondary_lag(wave, 1 / 24.0, 0.12, loop=True)
    assert len(lagged) == len(wave) and abs(lagged[0] - lagged[-1]) < 3.0
    print('motion_kit: limbs, digitigrade_leg, quadruped_gait, secondary_lag, path chain and stride all check out')
