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
- per-clip fingerprints (cached): a clip is rebuilt only when what it is made from changed (the tools review's K1).

Units are whatever the maker uses (model pixels in every maker so far); points are [x, y, z] lists.
"""
import hashlib
import json
import math
from pathlib import Path

from model_parts import smooth, vsub, vadd, vlen, unit

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / 'run-film' / 'clip_cache'


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
def bend_way(pole, dh):
    """Which way a joint between two pieces bends (a unit direction square to dh, the line from the leg's root to its
    end): pole, less its part along dh."""
    k = sum(x * y for x, y in zip(pole, dh))
    return unit([x - k * y for x, y in zip(pole, dh)])


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
    D = max(D, abs(l1 - l2) * 1.02)
    dh = [c / D for c in d]
    a = (l1 ** 2 - l2 ** 2 + D * D) / (2 * D)
    h = math.sqrt(max(0.0, l1 ** 2 - a * a))
    way = bend_way(pole, dh)
    return [root[i] + a * dh[i] + h * way[i] for i in range(3)], target, miss


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
    for f in (*files, __file__, Path(__file__).with_name('model_parts.py')):
        h.update(Path(f).read_bytes())
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
    pts = curve(lambda s: [2 * math.sin(s / 5), 0.0, s], 0.0, 40.0, 200)
    js = follow_path(pts, [4.0] * 6)
    assert all(near(vlen(vsub(b, a)), 4.0) for a, b in zip(js, js[1:]))
    assert near(stride(0.0, 0.6)[0], 1.0) and near(stride(0.6, 0.6)[1], 0.0)
    print('motion_kit: limbs of 2, 3 and 4 pieces, the path chain and the stride all check out')
