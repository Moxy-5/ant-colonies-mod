"""
The motion library (2026-10-16): writes insects' animations from real-insect numbers instead of hand-set key frames.
Used by tools/ant_models.py (and later tools/termite_models.py: it is species-neutral and works on any model with the
shared bone names). The plan is docs/plans/animation-engine.md, part 1.

What it does:
  Legs (inverse kinematics): from where a foot must be, it works out the hip, knee and ankle turns that put it there.
    A foot on the ground stays put while the body moves over it, and a lifted leg follows a real foot's path.
  Gait: the tripod gait of walking ants (the front and hind leg of one side step with the middle leg of the other).
    Each foot's path through a cycle comes from the sweep (how far a planted foot travels under the body), the duty
    factor (the share of the cycle a foot is down) and its moment in the step pattern. The clip's stride (the ground
    it covers) goes into the file as a timeline note, so the game plays the walk at exactly the speed that keeps the
    feet planted.
  Antennae and body: sweeps made of waves that close the loop, the whip following the base a moment later; the waist
    and gaster swaying against the stride; the body's small bob.
  Poses with planted feet: any body pose (the forebody raised to look, lowered to dig, the body on stilts) with the
    legs worked out so the feet stay where they stood.
  Bone map: clips use the standard bone names; a model with other names (the user's Blockbench worker, M2) gets a map
    from the standard names to its own (Rig(model, bonemap)).

Conventions are the lab's (tools/anim_lab.py): geo units (16 to a block before the renderer's scale), the head to -Z,
the ground at y = 0, the insect's left at +X. A file turn [x, y, z] in degrees turns X first (the other way round from
the right-hand rule), then Y, then Z (the other way round). As a matrix: M = Rz(-z) Ry(y) Rx(-x).

Self-test: python tools/ant_motion.py
"""
import math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import anim_lab as lab  # noqa: E402

UP = (0.0, 1.0, 0.0)


# --- vectors and turns ----------------------------------------------------------------------------------------------
def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mul(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def length(a):
    return math.sqrt(dot(a, a))


def unit(a):
    return mul(a, 1.0 / length(a))


def mat_mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def mat_vec(m, v):
    return tuple(m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3))


def axis_turn(axis, deg):
    """The right-handed turn by deg degrees about the unit vector axis (Rodrigues' formula)."""
    x, y, z = axis
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    t = 1 - c
    return ((t * x * x + c, t * x * y - s * z, t * x * z + s * y),
            (t * x * y + s * z, t * y * y + c, t * y * z - s * x),
            (t * x * z - s * y, t * y * z + s * x, t * z * z + c))


def file_turn(rot):
    """The matrix of a file turn [x, y, z] in degrees."""
    return mat_mul(axis_turn((0, 0, 1), -rot[2]), mat_mul(axis_turn((0, 1, 0), rot[1]), axis_turn((1, 0, 0), -rot[0])))


def to_file(m, near=(0.0, 0.0, 0.0)):
    """The file turn [x, y, z] (degrees) of matrix m, each angle the one nearest to near (so a key never jumps 360)."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    c = math.atan2(m[1][0], m[0][0])
    out = [-math.degrees(a), math.degrees(b), -math.degrees(c)]
    return [v + 360.0 * round((n - v) / 360.0) for v, n in zip(out, near)]


# --- a small solver ---------------------------------------------------------------------------------------------------
def solve_linear(a, b):
    """x with a x = b (a small dense system; Gaussian elimination with pivoting)."""
    n = len(b)
    m = [list(a[i]) + [b[i]] for i in range(n)]
    for col in range(n):
        piv = max(range(col, n), key=lambda r: abs(m[r][col]))
        m[col], m[piv] = m[piv], m[col]
        if abs(m[col][col]) < 1e-15:
            continue
        for r in range(n):
            if r != col and m[r][col]:
                f = m[r][col] / m[col][col]
                for c in range(col, n + 1):
                    m[r][c] -= f * m[col][c]
    return [m[i][n] / m[i][i] if abs(m[i][i]) > 1e-15 else 0.0 for i in range(n)]


def least_squares(residual, q0, iters=100, tol=1e-6, step=1e-4):
    """The q (degrees) that makes residual(q) as near zero as it can be: damped Newton steps (Levenberg-Marquardt)
    with the slopes measured by tiny nudges. Returns (q, the size of what is left)."""
    q = list(q0)
    r = residual(q)
    err = sum(v * v for v in r)
    mu = 1e-6
    n = len(q)
    for _ in range(iters):
        if err < tol * tol:
            break
        cols = []
        for j in range(n):
            qj = list(q)
            qj[j] += step
            cols.append([(a - b) / step for a, b in zip(residual(qj), r)])
        jtj = [[sum(x * y for x, y in zip(cols[a], cols[b])) for b in range(n)] for a in range(n)]
        g = [sum(x * y for x, y in zip(cols[a], r)) for a in range(n)]
        while True:
            damped = [[jtj[a][b] + (mu * (jtj[a][a] + 1e-9) if a == b else 0.0) for b in range(n)] for a in range(n)]
            dq = solve_linear(damped, [-v for v in g])
            qn = [a + b for a, b in zip(q, dq)]
            rn = residual(qn)
            en = sum(v * v for v in rn)
            if en < err:
                q, r, err = qn, rn, en
                mu = max(mu * 0.2, 1e-12)
                break
            mu *= 10
            if mu > 1e8:
                return q, math.sqrt(err)
    return q, math.sqrt(err)


# --- the rig: legs that reach -----------------------------------------------------------------------------------------
class Leg:
    """One leg: the hip (leg_l1), the knee (leg_l1_lower) and the ankle (leg_l1_foot), as in every insect model here.

    Its joints work like a real leg's: the hip swings the whole leg forward and back about the upright (protraction)
    and lifts or lowers it in its own plane (levation); the knee and the ankle bend in that plane. Four numbers (psi,
    lam, kap, tau, in degrees) set them all. A fifth, roll, is used only when a leg must lift clear of the body (a
    front leg cleaning an antenna): the hip rolls the leg outward and up about the body's long axis, as a real ant's
    coxa (the first leg segment, which turns in its socket) does."""

    def __init__(self, model, key):
        self.key, self.model = key, model
        self.side = key[0]
        foot_bone, ci, k = model.feet[key]
        self.hip, self.knee, self.foot = f'leg_{key}', f'leg_{key}_lower', f'leg_{key}_foot'
        if foot_bone != self.foot:
            raise ValueError(f'{key}: the motion library needs hip, _lower and _foot bones (found {foot_bone})')
        piv = lambda b: tuple(model.by[b]['pivot'])  # noqa: E731
        self.H, self.K, self.A = piv(self.hip), piv(self.knee), piv(self.foot)
        self.T = tuple(model.cubes[ci][1][k])            # the tip of the foot (the corner that touches the ground)
        self.parent = model.by[self.hip].get('parent')
        self.n = unit(cross(sub(self.K, self.H), sub(self.A, self.K)))   # the leg's plane
        self.lift0 = self.A[1] - self.T[1]              # the ankle's height over the tip when standing
        self.out = 1.0 if self.T[0] > 0 else -1.0        # which way is outward
        # The knee's bend at rest (the angle from the thigh's line to the shin's, about the leg's plane): a knee bend of
        # kap makes it phi0 + kap, and 0 is a straight leg. Real legs never lock straight or bend backward.
        f, t = sub(self.K, self.H), sub(self.A, self.K)
        self.phi0 = math.degrees(math.atan2(length(cross(f, t)), dot(f, t)))

    KNEE_MARGIN = 12.0                                   # the straightest a knee may get (degrees from straight)

    def knee_guard(self, kap):
        """Grows when the knee gets near straight (so the solver never flips the knee the other way)."""
        return 0.3 * max(0.0, self.KNEE_MARGIN - (self.phi0 + kap))

    # Solving with the roll free: the position must be met (weight 10 a unit), the thigh kept out (3 a share of its
    # length) and the roll kept as small as it can be (0.01 a degree, so a 20 degree roll weighs as 0.02 units off).
    AT, CLEAR, ROLL = 10.0, 3.0, 0.01

    def joints(self, q):
        psi, lam, kap, tau = q[:4]
        lift = axis_turn(self.n, lam)
        if len(q) > 4 and q[4]:
            lift = mat_mul(axis_turn((0.0, 0.0, self.out), q[4]), lift)    # positive: the leg rolls outward and up
        return (mat_mul(axis_turn(UP, psi), lift), axis_turn(self.n, kap), axis_turn(self.n, tau))

    def clearance(self, q, out):
        """Grows when the thigh points less than `out` of its length outward (away from the body's middle line)."""
        thigh = mat_vec(self.joints(q)[0], unit(sub(self.K, self.H)))
        return self.CLEAR * max(0.0, out - thigh[0] * self.out)

    def _with_roll(self, at, q0, tau, out):
        """Solves for psi, lam, kap and roll with residual at(q) (the position to meet), the thigh kept `out` (see
        clearance); tau is fixed, or None to solve for it too (then at must also hold the foot's slant)."""
        free_tau = tau is None
        start = list(q0[:3]) + ([q0[3]] if free_tau else []) + [q0[4] if len(q0) > 4 else 0.0]

        def full(v):
            return (v[0], v[1], v[2], v[3] if free_tau else tau, v[-1])

        def residual(v):
            q = full(v)
            return [self.AT * r for r in at(q)] + [self.knee_guard(q[2]), self.clearance(q, out), self.ROLL * q[4]]
        v, _ = least_squares(residual, start)
        return full(v)

    def points(self, q, pose):
        """The foot's tip and the ankle where joints q put them, in the model's frame, the body posed as in pose."""
        rh, rk, rf = self.joints(q)
        tip = add(self.A, mat_vec(rf, sub(self.T, self.A)))
        tip = add(self.K, mat_vec(rk, sub(tip, self.K)))
        ankle = add(self.K, mat_vec(rk, sub(self.A, self.K)))
        tip = add(self.H, mat_vec(rh, sub(tip, self.H)))
        ankle = add(self.H, mat_vec(rh, sub(ankle, self.H)))
        if self.parent:
            tip = self.model.world(self.parent, tip, pose)
            ankle = self.model.world(self.parent, ankle, pose)
        return tip, ankle

    def reach(self, target, pose, q0, lift=None, out=None):
        """Joints putting the tip at target with the ankle `lift` over it (default: as when standing, so the foot keeps
        its slant). With `out`, the hip may roll the leg outward to keep the thigh at least that far out (a share of its
        length; see clearance). Returns (q, how far off in units)."""
        lift = self.lift0 if lift is None else lift

        def at(q):
            tip, ankle = self.points(q, pose)
            return [tip[0] - target[0], tip[1] - target[1], tip[2] - target[2], (ankle[1] - tip[1]) - lift]
        if out is not None:
            q = self._with_roll(at, q0, None, out)
        else:
            q, err = least_squares(lambda q: at(q) + [self.knee_guard(q[2])], q0[:4])
        tip, ankle = self.points(q, pose)
        return q, length(sub(tip, target))

    def reach_ankle(self, target, pose, q0, tau, out=None):
        """Joints putting the ankle (where the cleaning comb of a front leg sits) at target, the foot bent by tau. With
        `out`, the hip may roll the leg outward as in reach."""
        def at(q):
            _, ankle = self.points(q, pose)
            return [ankle[0] - target[0], ankle[1] - target[1], ankle[2] - target[2]]
        if out is not None:
            q = self._with_roll(at, q0, tau, out)
        else:
            q3, _ = least_squares(lambda q3: at((q3[0], q3[1], q3[2], tau)) + [self.knee_guard(q3[2])], q0[:3])
            q = (q3[0], q3[1], q3[2], tau)
        return q, length(sub(self.points(q, pose)[1], target))

    def file_turns(self, q, near=None):
        """The hip's, knee's and foot's file turns for joints q, each near the ones given (no 360 jumps)."""
        near = near or ((0.0, 0.0, 0.0),) * 3
        return [to_file(m, nr) for m, nr in zip(self.joints(q), near)]


class Rig:
    """An insect's model ready for posing: its six legs, where its feet stand, and the bone map."""

    def __init__(self, model, bonemap=None):
        self.model = model
        self.map = dict(bonemap or {})
        self.legs = {k: Leg(model, k) for k in sorted(model.feet)}
        self.ground = min(leg.T[1] for leg in self.legs.values())

    def name(self, standard):
        return self.map.get(standard, standard)

    def stance(self, key):
        """Where a leg's foot stands at rest, on the ground."""
        t = self.legs[key].T
        return (t[0], self.ground, t[2])


def pose_of(still):
    """A lab pose (bone -> (turn, offset, scale)) from a still pose: bone -> [x, y, z] turn, 'body_pos' -> offset."""
    out = {}
    for bone, vec in still.items():
        if bone == 'body_pos':
            rot = out.get('body', ((0, 0, 0),))[0]
            out['body'] = (rot, tuple(vec), (1, 1, 1))
        else:
            pos = out.get(bone, (None, (0, 0, 0)))[1]
            out[bone] = (tuple(vec), pos, (1, 1, 1))
    return out


# --- building a clip --------------------------------------------------------------------------------------------------
def fmt_time(t):
    s = f'{t:.4f}'.rstrip('0').rstrip('.')
    return s if s not in ('', '-0') else '0'


def simplify(points, tol):
    """Drops key frames that the straight line between their neighbours already gives within tol (degrees or units)."""
    if len(points) <= 2:
        return points
    kept = [points[0]]
    a = 0
    while a < len(points) - 1:
        b = a + 1
        while b + 1 < len(points):
            ta, va = points[a]
            tb, vb = points[b + 1]
            ok = True
            for i in range(a + 1, b + 1):
                ti, vi = points[i]
                f = (ti - ta) / (tb - ta)
                if any(abs(x + (y - x) * f - z) > tol for x, y, z in zip(va, vb, vi)):
                    ok = False
                    break
            if not ok:
                break
            b += 1
        kept.append(points[b])
        a = b
    return kept


class Track:
    """Key frames being collected: bone -> channel -> {time: [x, y, z]}; written out simplified, linear between keys."""

    def __init__(self, rig):
        self.rig = rig
        self.keys = {}

    def put(self, bone, t, vec, channel='rotation'):
        self.keys.setdefault(bone, {}).setdefault(channel, {})[round(t, 5)] = [float(v) for v in vec]

    def pose(self, t, still):
        """Puts a still pose (as pose_of takes) at time t."""
        for bone, vec in still.items():
            if bone == 'body_pos':
                self.put('body', t, vec, 'position')
            else:
                self.put(bone, t, vec)

    def bones(self, tol=0.02):
        out = {}
        for bone, chans in self.keys.items():
            for ch, frames in chans.items():
                pts = simplify(sorted(frames.items()), tol)
                if all(max(abs(a - b) for a, b in zip(v, pts[0][1])) < tol for _, v in pts):
                    pts = [pts[0]]                       # never moves: one key
                out.setdefault(self.rig.name(bone), {})[ch] = {fmt_time(t): [round(v, 3) for v in vec] for t, vec in pts}
        return out


def waves(t, period, terms):
    """A sum of waves that closes on itself every period: terms are (cycles per period, size, phase 0-1)."""
    return sum(size * math.sin(2 * math.pi * (k * t / period + ph)) for k, size, ph in terms)


# --- standing with the feet planted --------------------------------------------------------------------------------
def stand(rig, still, feet=None, guess=None):
    """The legs' file turns for a body pose (a still pose as pose_of takes) with every foot where it stands at rest
    (or at feet[key]). Returns ({bone: turn}, {leg: joints}, worst miss in units)."""
    pose = pose_of(still)
    out, qs, worst = {}, {}, 0.0
    for key, leg in rig.legs.items():
        target = (feet or {}).get(key, rig.stance(key))
        q, err = leg.reach(target, pose, (guess or {}).get(key, (0.0, 0.0, 0.0, 0.0)))
        qs[key] = q
        worst = max(worst, err)
        for bone, turn in zip((leg.hip, leg.knee, leg.foot), leg.file_turns(q)):
            out[bone] = turn
    return out, qs, worst


# --- walking ----------------------------------------------------------------------------------------------------------
# The tripod gait: the moment in the cycle each foot touches down. L1, R2 and L3 step together, the other three half a
# cycle later; inside a tripod the hind foot lands a little first and the front one last (my pick from filmed ants).
TRIPOD = {'l3': 0.0, 'r2': 0.03, 'l1': 0.06, 'r3': 0.5, 'l2': 0.53, 'r1': 0.56}


def hermite(p0, p1, m0, m1, s):
    s2, s3 = s * s, s * s * s
    return (2 * s3 - 3 * s2 + 1) * p0 + (s3 - 2 * s2 + s) * m0 + (-2 * s3 + 3 * s2) * p1 + (s3 - s2) * m1


def foot_path(centre, sweep, duty, lift, out, phase, p, settle=0.12):
    """Where a foot is at moment p (0-1) of the cycle, the body standing still and the ground running back under it
    (+Z). On the ground the foot travels back the sweep in the duty share of the cycle. In the air it first rises while
    still moving with the ground (a real foot lifts before it swings, no scuff), swings forward on a smooth curve,
    and comes down moving with the ground again (the last `settle` share of the swing), its path bowing out a little
    to clear the other legs. Returns (point, on the ground?)."""
    cx, cy, cz = centre
    u = (p - phase) % 1.0
    front, back = cz - sweep / 2, cz + sweep / 2
    if u < duty:
        return (cx, cy, front + sweep * u / duty), True
    s = (u - duty) / (1 - duty)
    ground = sweep / duty * (1 - duty)                # how far the ground runs during the whole swing
    if s < settle:
        z = back + ground * s
    elif s > 1 - settle:
        z = front - ground * (1 - s)
    else:
        f = (s - settle) / (1 - 2 * settle)
        m = ground * (1 - 2 * settle)
        z = hermite(back + ground * settle, front - ground * settle, m, m, f)
    y = cy + lift * math.sin(math.pi * s) ** 0.75     # up quickly, down quickly: never dragged along the ground
    x = cx + out * math.sin(math.pi * s) ** 2      # out and back in without any sideways push at either end
    return (x, y, z), False


def ease_in(rig, leg, target, pose, q, steps=24):
    """Brings a leg from joints q to target in small steps, so the solver keeps the leg's own bend (knee up)."""
    start, _ = leg.points(q, pose)
    for k in range(1, steps + 1):
        f = k / steps
        q, _ = leg.reach(tuple(a + (b - a) * f for a, b in zip(start, target)), pose, q)
    return q


def walk(rig, seconds, cycles=1, sweep=3.4, duty=0.58, lift=0.9, phases=TRIPOD, bob=0.06, sway=2.0, gaster_sway=3.0,
         wide=0.0, shift=None, raise_by=0.0, still=None, keys=40, track=None):
    """A walking clip with every leg worked out backwards from its foot's path (see foot_path).

    seconds: the clip's length; cycles: gait cycles in it. sweep (units), duty, lift (units): the step.
    bob (units): the body's rise and fall, twice a cycle (highest when the legs stand under it, mid-stance).
    sway (degrees): the forebody turning toward the front leg on the ground; gaster_sway: the gaster against it.
    wide (units): the feet that much further out; shift: {leg number: z shift}; raise_by: the body that much higher.
    still: a still pose under it all (the hot look's raised gaster, ...).
    Returns (track, stride in units per clip, report)."""
    tr = track or Track(rig)
    still = dict(still or {})
    base_pos = still.pop('body_pos', [0.0, 0.0, 0.0])
    centres = {}
    for key, leg in rig.legs.items():
        x, y, z = rig.stance(key)
        centres[key] = (x + leg.out * wide, y, z + (shift or {}).get(int(key[1]), 0.0))
    n = keys * cycles
    q = {key: (0.0, 0.0, 0.0, 0.0) for key in rig.legs}
    turns = {key: None for key in rig.legs}
    worst = {key: 0.0 for key in rig.legs}
    first = None
    mid = TRIPOD['l1'] + duty / 2                     # tripod 1's mid-stance: the body is highest
    for warm in (True, False):                        # one cycle to settle the solver, then the clip
        for i in range(n + 1 if not warm else keys):
            eased = warm and i == 0
            t = seconds * i / n
            p = (i / keys) % 1.0
            yaw = -sway * math.sin(2 * math.pi * (p - TRIPOD['l1']))
            gaster = gaster_sway * math.sin(2 * math.pi * (p - TRIPOD['l1']))
            body_pos = [base_pos[0], base_pos[1] + raise_by + bob * math.cos(4 * math.pi * (p - mid)), base_pos[2]]
            pose_still = dict(still)
            fore = still.get('forebody', [0, 0, 0])
            pose_still['forebody'] = [fore[0], fore[1] + yaw, fore[2]]
            pose_still['body_pos'] = body_pos
            pose = pose_of(pose_still)
            for key, leg in rig.legs.items():
                target, _ = foot_path(centres[key], sweep, duty, lift, 0.25 * leg.out, phases[key], p)
                if eased:
                    q[key] = ease_in(rig, leg, target, pose, q[key])
                q[key], err = leg.reach(target, pose, q[key])
                if not warm:
                    worst[key] = max(worst[key], err)
                    turns[key] = leg.file_turns(q[key], turns[key])
                    for bone, turn in zip((leg.hip, leg.knee, leg.foot), turns[key]):
                        tr.put(bone, t, turn)
            if warm:
                continue
            if first is None:
                first = {key: list(map(list, turns[key])) for key in rig.legs}
            tr.put('body', t, body_pos, 'position')
            tr.put('forebody', t, pose_still['forebody'])
            pet = still.get('petiole', [0, 0, 0])
            ab = still.get('abdomen', [0, 0, 0])
            tr.put('petiole', t, [pet[0], pet[1] + gaster / 2, pet[2]])
            tr.put('abdomen', t, [ab[0], ab[1] + gaster / 2, ab[2]])
            for bone, vec in still.items():
                if bone not in ('forebody', 'petiole', 'abdomen'):
                    tr.put(bone, t, vec)
    # The last key is the first one again (the loop closes exactly).
    seam = 0.0
    for key, leg in rig.legs.items():
        for bone, a, b in zip((leg.hip, leg.knee, leg.foot), first[key], turns[key]):
            seam = max(seam, max(abs(x - y) for x, y in zip(a, b)))
            tr.put(bone, seconds, a)
    stride = cycles * sweep / duty
    report = {'worst_miss': max(worst.values()), 'misses': worst, 'seam': seam, 'stride': stride}
    return tr, stride, report


# --- self-test --------------------------------------------------------------------------------------------------------
def self_test():
    rot = [23.0, -41.0, 67.0]
    p = (0.3, -1.2, 2.5)
    a = lab.turn(p, rot)
    b = mat_vec(file_turn(rot), p)
    assert max(abs(x - y) for x, y in zip(a, b)) < 1e-9, (a, b)
    back = to_file(file_turn(rot))
    assert max(abs(x - y) for x, y in zip(back, rot)) < 1e-9, back
    model = lab.Model('worker_ant')
    rig = Rig(model)
    for key, leg in rig.legs.items():
        q, err = leg.reach(rig.stance(key), {}, (0.0, 0.0, 0.0, 0.0))
        assert err < 1e-4, (key, err)
        target = add(rig.stance(key), (0.0, 0.0, -1.0))
        q, err = leg.reach(target, {}, q)
        turns = leg.file_turns(q)
        pose = {leg.hip: (turns[0], (0, 0, 0), (1, 1, 1)), leg.knee: (turns[1], (0, 0, 0), (1, 1, 1)),
                leg.foot: (turns[2], (0, 0, 0), (1, 1, 1))}
        tip = model.foot_tips(pose)[key]
        assert length(sub(tip, target)) < 1e-3 and err < 1e-4, (key, tip, target, err)
    print('motion library: turns agree with the lab, every leg reaches its targets')


if __name__ == '__main__':
    self_test()
