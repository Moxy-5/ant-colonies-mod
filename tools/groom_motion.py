"""Cleaning clips for any insect model (M8 step 3Q, 2026-11-05): antenna cleaning with the front leg of the same side,
and cleaning a nestmate, made from the model itself with the shared motion library (tools/ant_motion.py), for the
Matabele (tools/matabele_anims.py) and the termites (tools/termite_models.py).

The harvester ant's antenna cleaning (tools/ant_models.py, groom_side, confirmed in-game) was worked out by hand for its
own model: where its antenna's whip runs, where the point between its jaws is. Here the same moves are measured from any
model: the whip runs from the antenna's elbow (the pivot of its second bone) out to the farthest corner of its boxes;
the point the leg is drawn through lies beside the head at the middle of the jaws (between their hinges and tips); and
every distance (how high the foot lifts, how far it bows out) is scaled by the front leg's length against the
harvester's. The turns of the body and the antennae are the harvester's (they are angles: the same on any body).

The real behaviour (as in ant_models.py): an ant stops, bends its head down and toward one side and lowers that
antenna, swung out; it lays the whip in the comb on its front leg near the base and draws it through to the tip, three
strokes; then it turns its head to the leg and draws the leg through its open jaws (Farish 1972; Hackmann et al. 2015).
Termites clean their antennae the same way with their front legs (they have no strigil comb, but draw the antenna
through the leg and the mouthparts; my reading of the descriptions, the same clip).

Cleaning a nestmate (allogrooming): real ants lick their nestmates; termites do it constantly (it spreads the colony's
smell and clears fungus spores off). The groomer stands at the other's side or end, forebody and head bowed, the
mouthparts working over the other's body in small nibbles, the antennae down on it; the feet stay put.

Every number is my pick, to confirm, unless it says real. Clips are made in role names.
"""
import math

import ant_motion as mo

HARVESTER_LEG = 4.64        # the harvester worker's front leg, thigh plus shin (model units): the scale of 1
STEP = 1 / 40               # the legs worked out every 1/40 s

# As the harvester's (tools/ant_models.py, GROOM_*), the distances at its size.
GROOM = dict(seconds=2.6, lift=0.8, swing=0.3, out=0.45, strokes=[(0.1, 0.92), (0.14, 0.95), (0.1, 0.9)],
             comb=dict(forebody=(5, -4, 0), head=(12, -6, 4), a1=(5, -30, 0), a2=(20, -10, 0), o1=(-6, -8, 0), o2=(-4, 0, 0)),
             jaws=dict(forebody=(-13, 3.5, 0), head=(-6, -20, 1), a1=(-8, -10, 0), a2=(6, 0, 0), open=20),
             pulled=dict(a1=(-6, 0, 0), a2=(-6, 0, 0), head=(-3, 3, 0)),
             beside=(1.22, 1.92), ankle_lift=0.47)

# The settings per kind (my picks, found by checking the cleaning leg reaches the antenna along every stroke):
# Matabele: their antennae are longer for the leg than the harvester's: the whip bent further down (45 degrees) and
#   combed from a third of the way out (nearer, the leg of a major or the queen ran into the head) to a little short of
#   its tip.
# Termites (the worker and the king; the soldiers' great heads hang over their front legs, so they do not clean
#   themselves: the workers clean them): their antennae start far ahead of their short front legs (a worker's leg 2.2
#   units, the antennae's base 2.7 ahead of its hip), so, as real termites do, the antenna is swung down, out and back
#   to the leg and the mouth (its base 55 down and 50 out), the head held level, the leg combs its middle, and the leg
#   swings wide round the head and holds the antenna well out beside the mouth (nearer, it ran into the head).
MATABELE = dict(comb=dict(GROOM['comb'], a2=(45, -10, 0)), strokes=[(0.3, 0.85), (0.32, 0.88), (0.3, 0.84)])
TERMITE = dict(comb=dict(GROOM['comb'], a1=(55, -50, 0), a2=(45, -10, 0), head=(0, -6, 4), forebody=(0, -4, 0)),
               strokes=[(0.35, 0.7), (0.4, 0.75), (0.35, 0.65)], mouth=0.15, swing=0.7,
               beside=(1.8, 2.5))


def smooth(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def lerp(a, b, f):
    return [x + (y - x) * f for x, y in zip(a, b)]


def along(points, s):
    """The point s of the way along a line through these points (by length)."""
    lens = [mo.length(mo.sub(b, a)) for a, b in zip(points, points[1:])]
    left = sum(lens) * s
    for (a, b), ln in zip(zip(points, points[1:]), lens):
        if left <= ln and ln > 0:
            return mo.add(a, mo.mul(mo.sub(b, a), left / ln))
        left -= ln
    return points[-1]


def size(rig):
    """The model's size against the harvester's, by the front leg."""
    leg = rig.legs[rig.front_left]
    return (mo.length(mo.sub(leg.K, leg.H)) + mo.length(mo.sub(leg.A, leg.K))) / HARVESTER_LEG


def farthest(model, bone, start):
    """The corner of this bone's boxes farthest from start (at rest)."""
    corners = [q for b, cs, _ in model.cubes if b == bone for q in cs]
    return max(corners, key=lambda q: mo.length(mo.sub(q, start)))


def whip(model, side):
    """Points along an antenna's whip at rest: its elbow, then three on to its tip."""
    a2 = model.plan.antennae[side][1]
    elbow = tuple(model.by[a2]['pivot'])
    tip = farthest(model, a2, elbow)
    return [elbow] + [tuple(e + (t - e) * f for e, t in zip(elbow, tip)) for f in (1 / 3, 2 / 3, 1.0)]


def jaw_point(model, s, k, share=0.5):
    """A point beside the head along the jaws (share of the way from their hinges to their tips: the middle for an ant,
    near the hinges for a termite soldier, whose long jaws stand out far ahead of its mouth), x (the harvester's design
    units, scaled) out toward side s."""
    hinge = tuple(model.by['mandible_l']['pivot'])
    tip = farthest(model, 'mandible_l', hinge)
    mid = [h + (t - h) * share for h, t in zip(hinge, tip)]
    half = abs(hinge[0])

    def at(x):
        # The harvester's points ran from 1.22 to 1.92 design units out (its jaws' hinges 0.62 out): past the hinge by
        # about 1.3 to 2.1 hinge widths, here the same share of this head's hinge width (at least the leg's size).
        out = half + (x - 0.62) * max(half / 0.62, 0.6 * k) * 0.85
        return (s * out, mid[1], mid[2])
    return at


REACH = {}                  # the worst miss of the cleaning leg, combing and in the jaws (for checking a new model)


GAP = 0.05                  # how near the floor the body, the jaws and the antennae may come (units)


def lowest(model, pose, keep):
    """The lowest point of the posed boxes whose bone keep(bone) accepts."""
    xf = model.transforms(pose)
    return min((p[1] for b, corners, _ in model.posed(pose, xf) if keep(b) for p in corners), default=1e9)


def settle(rig, body, gap=GAP):
    """The pose kept off the floor (in place, returned): the body raised on its legs just enough that nothing but the
    legs and the antennae comes nearer the floor than gap (a bowing ant rises on its legs, its jaws never in the ground),
    then each antenna raised at its base (both its joints a little less down) until its whip clears the floor too."""
    model = rig.model
    floor = rig.ground + gap
    need = floor - lowest(model, mo.pose_of(body), lambda b: model.leg_of(b) is None and not model.is_antenna(b))
    if need > 0:
        x, y, z = body.get('body_pos', [0, 0, 0])
        body['body_pos'] = [x, y + need, z]
    for side in ('l', 'r'):
        a1, a2 = model.plan.antennae[side]
        start1, start2 = list(body.get(a1, [0, 0, 0])), list(body.get(a2, [0, 0, 0]))

        def low(d):
            body[a1] = [start1[0] - d, start1[1], start1[2]]
            body[a2] = [start2[0] - d / 2, start2[1], start2[2]]
            return lowest(model, mo.pose_of(body), lambda b: b in (a1, a2))
        if low(0.0) >= floor:
            body[a1], body[a2] = start1, start2
            continue
        lo, hi = 0.0, 90.0
        for _ in range(30):
            mid = (lo + hi) / 2
            if low(mid) >= floor:
                hi = mid
            else:
                lo = mid
        low(hi)
    return body


def groom_side(rig, tr, side, t0, numbers=None):
    """Cleaning the antenna on this side ('l' or 'r') into track tr, from time t0 (see the top). Returns the end time
    and the worst miss of a planted foot (units)."""
    g = dict(GROOM, **(numbers or {}))
    T = g['seconds']
    model = rig.model
    k = size(rig)
    s = 1 if side == 'l' else -1
    other = 'r' if side == 'l' else 'l'
    a1, a2 = model.plan.antennae[side]
    o1, o2 = model.plan.antennae[other]
    lift, bow, out_ = g['lift'] * k, g['swing'] * k, g['out']

    def m(v):                          # a turn given for the left side, mirrored for the right
        return [v[0], s * v[1], s * v[2]]

    leg = rig.legs[f'{side}1']
    stance = rig.stance(f'{side}1')
    points = whip(model, side)
    jaw = jaw_point(model, s, k, g.get('mouth', 0.5))
    names = {'forebody': 'forebody', 'head': 'head', 'a1': a1, 'a2': a2, 'o1': o1, 'o2': o2}
    rest = {b: [0, 0, 0] for b in ('forebody', 'head', a1, a2, o1, o2, 'mandible_l', 'mandible_r')}
    comb = dict(rest)
    for key, v in g['comb'].items():
        comb[names[key]] = m(v) if key not in ('o1', 'o2') else [v[0], -s * abs(v[1]) if key == 'o1' else v[1], v[2]]
    jaws = dict(comb)
    for key, v in g['jaws'].items():
        if key != 'open':
            jaws[names[key]] = m(v)
    jaws['mandible_l'], jaws['mandible_r'] = [0, -g['jaws']['open'], 0], [0, g['jaws']['open'], 0]
    pulled = {names[key]: m(v) for key, v in g['pulled'].items()}

    on, stroke, pull_s = 0.35, 0.45, 0.30          # the leg on the antenna; each stroke; its pull
    up = 0.14
    mouth = on + 2 * stroke + pull_s
    in_jaws, draw, back = mouth + 0.3, 0.18, 0.07
    out_of_jaws = in_jaws + 2 * draw + back
    hover = T - 0.12
    x0, x1 = g['beside']

    def with_pull(f):
        return {b: [a + f * c for a, c in zip(comb[b], pulled.get(b, [0, 0, 0]))] for b in comb}

    def raw_at(t):
        if t < on:
            return {b: lerp(rest[b], comb[b], smooth(t / 0.3)) for b in rest}, 'lift', None
        if t < mouth:
            n = min(2, int((t - on) / stroke))
            u = t - on - n * stroke
            s0, s1 = g['strokes'][n]
            if u < pull_s:
                f = smooth(u / pull_s)
                return with_pull(f), 'comb', (s0 + (s1 - s0) * f, 0.0)
            f = smooth((u - pull_s) / (stroke - pull_s))
            nxt = g['strokes'][min(2, n + 1)][0]
            return with_pull(1 - f), 'comb', (s1 + (nxt - s1) * f, math.sin(math.pi * f))
        if t < in_jaws:
            f = smooth((t - mouth) / (in_jaws - mouth))
            start = with_pull(1)
            return {b: lerp(start[b], jaws[b], f) for b in comb}, 'to_jaws', f
        if t < out_of_jaws:
            u = t - in_jaws
            if u < draw:
                x, bite = x0 + (x1 - x0) * smooth(u / draw), smooth(u / draw)
            elif u < draw + back:
                f = smooth((u - draw) / back)
                x, bite = x1 - (x1 - x0) * f, 1 - f
            else:
                f = smooth((u - draw - back) / draw)
                x, bite = x0 + (x1 - x0) * f, f
            o = g['jaws']['open']
            pose = dict(jaws, mandible_l=[0, -o + 0.6 * o * bite, 0], mandible_r=[0, o - 0.6 * o * bite, 0])
            return pose, 'jaws', x
        return {b: lerp(jaws[b], rest[b], smooth((t - out_of_jaws) / (T - out_of_jaws))) for b in comb}, 'down', None

    def body_at(t):
        body, step, arg = raw_at(t)
        return settle(rig, body), step, arg

    def five(q):
        return tuple(q) + (0.0,) * (5 - len(q))

    def swing(qa, qb, f, pose, q_prev):
        pa, pb = leg.points(five(qa), pose)[1], leg.points(five(qb), pose)[1]
        b = bow * math.sin(math.pi * f)
        target = mo.add(lerp(pa, pb, f), (s * b, b, b))
        q_, _ = leg.reach_ankle(target, pose, five(q_prev), qa[3] + (qb[3] - qa[3]) * f, out=out_)
        return q_

    q = qs = None
    q_on = q_up = q_combed = q_in = q_out = q_over = None
    near = None
    worst = 0.0
    for i in range(round(T / STEP) + 1):
        t = i * STEP
        body, step, arg = body_at(t)
        pose = mo.pose_of(body)
        feet = {key: rig.stance(key) for key in rig.legs if key != f'{side}1'}
        turns, qs, miss = mo.stand(rig, body, feet, guess=qs)
        worst = max(worst, miss if step in ('lift', 'down') else max(
            mo.length(mo.sub(rig.legs[key].points(qs[key], pose)[0], feet[key])) for key in feet))
        if step == 'lift':
            if q_on is None:
                start = mo.pose_of(body_at(on)[0])
                f0 = [model.world(a2, p, start) for p in points]
                q_on, _ = leg.reach_ankle(along(f0, g['strokes'][0][0]), start, (30.0 * s, -30.0, -20.0, 15.0, 0.0), 15.0, out=out_)
            if t <= up:
                h = lift * smooth((t - 0.04) / (up - 0.04))
                q, _ = leg.reach((stance[0], stance[1] + h, stance[2]), pose, q or qs[f'{side}1'])
                q = q_up = five(q)
            else:
                q = swing(q_up, q_on, smooth((t - up) / (on - up)), pose, q)
        elif step == 'comb':
            share, off = arg
            f = [model.world(a2, p, pose) for p in points]
            target = mo.add(along(f, share), (0.5 * k * s * off, -0.35 * k * off, 0.0))
            q, err = leg.reach_ankle(target, pose, q, 15.0, out=out_)
            REACH['comb'] = max(REACH.get('comb', 0.0), err)
            q_combed = q
        elif step == 'to_jaws':
            if q_in is None:
                jp = mo.pose_of(jaws)
                q_in, _ = leg.reach(model.world('head', jaw(x0), jp), jp, (25.0 * s, -75.0, 15.0, 44.0, -34.0),
                                    g['ankle_lift'] * k, out=out_)
            q = swing(q_combed, q_in, arg, pose, q)
        elif step == 'jaws':
            q, err = leg.reach(model.world('head', jaw(arg), pose), pose, q, g['ankle_lift'] * k, out=out_)
            REACH['jaws'] = max(REACH.get('jaws', 0.0), err)
            q_out = q
        else:
            if q_over is None:
                q_over, _ = leg.reach((stance[0], stance[1] + lift, stance[2]), mo.pose_of(body_at(hover)[0]), q_up)
                q_over = five(q_over)
            if t < hover:
                q = tuple(lerp(q_out, q_over, smooth((t - out_of_jaws) / (hover - out_of_jaws))))
            else:
                h = lift * (1 - smooth((t - hover) / (T - hover)))
                q, _ = leg.reach((stance[0], stance[1] + h, stance[2]), pose, q)
                q = five(q)
        near = leg.file_turns(q, near)
        turns.update(zip((leg.hip, leg.knee, leg.foot), near))
        for bone, vec in list(body.items()) + list(turns.items()):
            if bone == 'body_pos':
                tr.put('body', t0 + t, vec, 'position')
            else:
                tr.put(bone, t0 + t, vec)
    return t0 + T, worst


def groom_antenna(rig, sides, numbers=None):
    """The clip's bones: cleaning the antennae on these sides one after the other ('l', 'r' or 'lr'), and the worst miss
    of a planted foot."""
    tr = mo.Track(rig)
    t, worst = 0.0, 0.0
    for side in sides:
        t, miss = groom_side(rig, tr, side, t, numbers)
        worst = max(worst, miss)
    return tr.bones(tol=0.1), worst


def groom_clips(rig, numbers=None, limit=0.05, reach=0.15):
    """The three cleaning clips (the names the game plays: groom_antenna_l, _r, _both), each checked: no planted foot
    off its spot by more than limit units, and the cleaning leg on the antenna and at the mouth within reach units."""
    seconds = dict(GROOM, **(numbers or {}))['seconds']
    clips = {}
    for name, sides in (('groom_antenna_l', 'l'), ('groom_antenna_r', 'r'), ('groom_antenna_both', 'lr')):
        REACH.clear()
        bones, miss = groom_antenna(rig, sides, numbers)
        if miss > limit:
            raise ValueError(f'{name}: a planted foot misses its place by {miss:.3f}')
        if max(REACH.values()) > reach:
            raise ValueError(f'{name}: the cleaning leg misses by {REACH}')
        clips[name] = {'loop': False, 'animation_length': seconds * len(sides), 'bones': bones}
    return clips


# --- cleaning a nestmate ----------------------------------------------------------------------------------------
# Standing at the other's side, bowed over it: the forebody down NESTMATE['fore'], the head down NESTMATE['head'] and
# nodding a few degrees with each nibble, the jaws opening and closing NESTMATE['nibbles'] times a loop, the antennae
# reaching down onto the other's body and tapping it by turns. The feet stay where they stand (the body may sink a
# little to reach). A loop of NESTMATE['seconds'].
NESTMATE = dict(seconds=2.0, fore=8.0, head=22.0, nod=5.0, nibbles=4, jaw=14.0, antennae=24.0, tap=8.0)


def groom_nestmate(rig, numbers=None):
    """The clip (groom_nestmate) and the worst miss of a planted foot."""
    n = dict(NESTMATE, **(numbers or {}))
    T = n['seconds']
    model = rig.model
    tr = mo.Track(rig)
    (l1, l2), (r1, r2) = model.plan.antennae['l'], model.plan.antennae['r']
    qs, worst = None, 0.0
    for i in range(round(T / STEP) + 1):
        t = i * STEP
        on = 1.0                       # held bowed all through the loop (the game eases into and out of a clip)
        bite = 0.5 - 0.5 * math.cos(2 * math.pi * n['nibbles'] * t / T)
        tap_l = max(0.0, math.sin(2 * math.pi * 2 * t / T))
        tap_r = max(0.0, math.sin(2 * math.pi * 2 * t / T + math.pi))
        body = {'forebody': [n['fore'] * on, 0, 0],
                'head': [(n['head'] + n['nod'] * bite) * on, 0, 0],
                'mandible_l': [0, -n['jaw'] * bite * on, 0], 'mandible_r': [0, n['jaw'] * bite * on, 0],
                l1: [n['antennae'] * on + n['tap'] * tap_l * on, 8 * on, 0], l2: [10 * on, 0, 0],
                r1: [n['antennae'] * on + n['tap'] * tap_r * on, -8 * on, 0], r2: [10 * on, 0, 0]}
        settle(rig, body)
        turns, qs, miss = mo.stand(rig, body, guess=qs)
        worst = max(worst, miss)
        tr.pose(t, dict(body, **turns))
    return {'loop': True, 'animation_length': T, 'bones': tr.bones(tol=0.05)}, worst
