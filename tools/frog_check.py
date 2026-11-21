"""The bullfrog's clips checked offline, as the game draws them (the workshop's stage 5, 2026-11-20; made like
tools/croc_check.py; CLAUDE.md's hard rule: every animation checked offline and in the game frame by frame). Reads the
game's own files (python tools/bullfrog_models.py game first).

    python tools/frog_check.py [clip ...] [--times 0,0.5,1] [--views side,three,front]

With no clip names, checks every clip. Prints for each, in model pixels (the game draws the frog at 0.32 of them for
a male): parts under the ground (on land, only while the frog is meant to be on the ground; the dig clips sink on
purpose, so for them how much of the back and the eyes still shows), the highest hand or foot over the ground while it
sits, the knees and elbows against the body's skin (none deeper in than at rest), the walk's planted feet (each should slide back at the walking speed, none sideways), the biggest jump of any
part between two frames and the fastest joint turn. With --times, draws a film strip (fc_<clip>.png in
references/anim_lab/bullfrog/): the ground grey, the water's surface a blue line on water clips."""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anim_lab as lab  # noqa: E402
import bullfrog_models as bm  # noqa: E402

WATER = ('swim', 'float')
DIG = ('dig_in', 'dug', 'dig_out')
# When each clip means the frog to be on the ground (parts of the clip; the hop and leap fly in between)
ON_GROUND = {'leap': ((0.0, 0.7),), 'hop_dive': ((0.0, 0.6),), 'climb_out': ((0.9, 1.0),)}   # it ends on the bank          # the leap ends in the water
# The hop and the leap are thrown by the game (docs/plans/prey-fish-and-frogs.md): it launches the frog as the kick
# starts and lands it as the clip's flight ends (parts of the clip), on an arc under Minecraft's gravity (32 blocks a
# second, squared). The checks and the strips raise the frog by that arc, as the game will, so the ground they judge
# against is the real one. A male is drawn at 0.32: one model pixel is 0.02 blocks.
FLIGHT = {'hop': (0.12, 0.6), 'hop_dive': (0.12, 0.6), 'leap': (0.12, 0.7)}
PX_PER_BLOCK = 16 / 0.32


def rise(name, t, length):
    if name not in FLIGHT:
        return 0.0
    a, b = (u * length for u in FLIGHT[name])
    if not a < t < b:
        return 0.0
    v = 32.0 * (b - a) / 2
    dt = t - a
    return (v * dt - 16.0 * dt * dt) * PX_PER_BLOCK
ENDS = ('hand_left', 'hand_right', 'foot_left', 'foot_right')
OUT = os.path.join(lab.PROJECT, 'references', 'anim_lab', 'bullfrog')

args = sys.argv[1:]
times, views, names = None, ('side', 'three', 'front'), []
i = 0
while i < len(args):
    if args[i] == '--times':
        times = [float(x) for x in args[i + 1].split(',')]
        i += 2
    elif args[i] == '--views':
        views = tuple(args[i + 1].split(','))
        i += 2
    else:
        names.append(args[i])
        i += 1

model = lab.Model('bullfrog')
data = json.load(open(lab.model_file('bullfrog', 'animations', 'entity', 'bullfrog.animation.json'),
                      encoding='utf-8'))['animations']
names = names or list(data)


def low_of(posed, bone):
    return min((p[1] for b, cs, _ in posed if b == bone for p in cs), default=float('nan'))


def top_of(posed, prefix):
    return max((p[1] for b, cs, _ in posed if b.startswith(prefix) for p in cs), default=float('nan'))


JOINTS = {'knee': ('leg_left_2', 'leg_right_2'), 'elbow': ('arm_left_2', 'arm_right_2')}


def skin_depth(q):
    """How far a point (the body's own unturned space) lies inside the body's skin; below 0 outside it."""
    x, y, z = q
    along = bm.LENGTH / 2 - z
    if not 0 <= along <= bm.LENGTH:
        return -99.0
    top, belly = bm.at(bm.TOP, along), bm.at(bm.BELLY, along)
    if not belly <= y <= top:
        return -min(abs(y - belly), abs(y - top))
    return bm.half_width_at(along, y) - abs(x)


def joint_depths(pose):
    """Each knee's and elbow's middle against the body's skin (the user, 2026-11-20: the knees sank into the body in
    the walk, the puff and the swim): the joint carried into the body's own space, then measured."""
    P = model.by['body']['pivot']
    Pw = model.world('body', P, pose)
    cols = [[a - b for a, b in zip(model.world('body', [P[0] + (i == 0), P[1] + (i == 1), P[2] + (i == 2)], pose), Pw)]
            for i in range(3)]
    out = {}
    for kind, bones in JOINTS.items():
        for bone in bones:
            w = model.world(bone, model.by[bone]['pivot'], pose)
            rel = [a - b for a, b in zip(w, Pw)]
            q = [P[i] + sum(c * r for c, r in zip(cols[i], rel)) / sum(c * c for c in cols[i]) for i in range(3)]
            out[bone] = skin_depth(q)
    return out


REST_DEPTH = None


def grounded(name, u):
    spans = ON_GROUND.get(name)
    return spans is None or any(a <= u <= b for a, b in spans)


REST_DEPTH = joint_depths({})
# the inside of the mouth, the tongue and the fangs must hide in the shut mouth (the frog sitting): each part's least
# depth inside the skin, at rest
MOUTH_PARTS = ('mouth_floor', 'mouth_roof', 'tongue_1', 'tongue_2', 'tongue_pad')
hidden = {}
for b, cs, _ in model.posed({}):
    if b in MOUTH_PARTS:
        hidden[b] = min([hidden.get(b, 99.0)] + [skin_depth(p) for p in cs])
print('mouth parts inside the shut mouth (least px inside the skin; below 0.3 flagged): ' + ', '.join(
    f'{b} {d:.1f}' + (' FLAGGED' if d < 0.3 else '') for b, d in hidden.items()))
for name in names:
    clip = lab.Clip(name, data[name])
    deepest = {}
    water = name in WATER
    print(f'== {name}  {clip.length:g} s  ({"in the water" if water else "digging" if name in DIG else "on land"})')
    under, jumps, last = {}, {}, None
    steps = round(clip.length * 80)
    feet_high, shows = 0.0, []
    planted = {e: [] for e in ENDS}
    for k in range(steps + 1):
        t = k / 80
        up = rise(name, t, clip.length)
        posed = [(b, [(p[0], p[1] + up, p[2]) for p in cs], c) for b, cs, c in model.posed(clip.pose(t))]
        now = {}
        for b, cs, _ in posed:
            now.setdefault(b, []).extend(cs)
        if not water and name not in DIG and grounded(name, t / clip.length) and k % 2 == 0:
            for b, cs in now.items():
                d = -min(p[1] for p in cs)
                if d > 0.4:
                    under[b] = max(under.get(b, (0, 0)), (d, t))
            if name not in ('walk', 'hop', 'leap', 'death', 'attack'):
                feet_high = max(feet_high, max(low_of(posed, e) for e in ENDS))
        if k % 4 == 0:
            for bone, d in joint_depths(clip.pose(t)).items():
                if d - REST_DEPTH[bone] > deepest.get(bone, (-99, 0))[0]:
                    deepest[bone] = (d - REST_DEPTH[bone], t)
        if name in DIG and k % 8 == 0:
            shows.append((t, top_of(posed, 'body'), top_of(posed, 'eye'), min(p[1] for cs in now.values() for p in cs)))
        if name == 'walk':
            for e in ENDS:
                cs = now[e]
                lo = min(p[1] for p in cs)
                if lo < 0.1:
                    planted[e].append((t, sum(p[0] for p in cs) / len(cs), sum(p[2] for p in cs) / len(cs)))
                else:
                    planted[e].append(None)
        if last:
            for b, cs in now.items():
                d = max(math.dist(a, c) for a, c in zip(cs, last[b]))
                if d > jumps.get(b, (0, 0))[0]:
                    jumps[b] = (d, t)
        last = now
    if not water and name not in DIG:
        print('  under the ground (px at time): ' + (', '.join(f'{b} {d:.1f} at {t:.2f}' for b, (d, t) in sorted(under.items()))
                                                    if under else 'nothing'))
        if feet_high:
            print(f'  highest hand or foot over the ground while sitting: {feet_high:.2f} px')
    if name in DIG:
        print('  dug in (time: back top, eyes top, lowest part; px against the ground):')
        for t, back, eyes, low in shows[::3] + [shows[-1]]:
            print(f'    {t:4.2f}: back {back:5.1f}  eyes {eyes:5.1f}  lowest {low:5.1f}')
    if name == 'walk':
        want = bm.WALK_SWEEP / (bm.WALK_DUTY * bm.WALK_LEN)
        print(f'  planted feet: should slide back at {want:.2f} px/s (the walking speed, '
              f'{want * 0.32 / 16:.3f} blocks/s for a male at 0.32), none sideways')
        for e, track in planted.items():
            vz, vx = [], []
            for a, b in zip(track, track[1:]):
                if a and b:
                    vz.append((b[2] - a[2]) * 80)
                    vx.append((b[1] - a[1]) * 80)
            if vz:
                print(f'    {e:11s} down {len(vz) / (len(track) - 1):4.0%} of the stride, back {min(vz):5.2f} to '
                      f'{max(vz):5.2f}, sideways {min(vx):5.2f} to {max(vx):5.2f}')
            else:
                print(f'    {e:11s} never down')
    if name == 'tongue':
        reach = []
        for k in range(0, steps + 1, 2):
            pad = [p for b, cs, _ in model.posed(clip.pose(k / 80)) if b == 'tongue_pad' for p in cs]
            reach.append((min(p[2] for p in pad), min(p[1] for p in pad), k / 80))
        far = min(reach)
        print(f'  the tongue: its pad reaches {-bm.LENGTH / 2 - far[0]:.1f} px past the snout at {far[2]:.2f}, '
              f'{far[1]:.1f} px over the ground there')
    worst = sorted(deepest.items(), key=lambda kv: -kv[1][0])[:2]
    if name in DIG or name == 'death':      # limbs tucked in on purpose: under the ground, or as it rolls over
        worst = []
    print('  knees and elbows deeper into the body than at rest (px at time; over 0.5 flagged): ' + ', '.join(
        f'{b} {d:.1f} at {t:.2f}' + (' FLAGGED' if d > 0.5 else '') for b, (d, t) in worst))
    top = sorted(jumps.items(), key=lambda kv: -kv[1][0])[:5]
    print('  biggest jump in 1/80 s (px at time): ' + ', '.join(f'{b} {d:.1f} at {t:.2f}' for b, (d, t) in top))
    turns = {}
    for bone, chans in data[name]['bones'].items():
        keys = chans.get('rotation', {})
        ts = sorted(keys, key=float)
        for a, b in zip(ts, ts[1:]):
            va = keys[a]['vector'] if isinstance(keys[a], dict) else keys[a]
            vb = keys[b]['vector'] if isinstance(keys[b], dict) else keys[b]
            d = max(abs(x - y) for x, y in zip(va, vb)) * 0.05 / max(1e-6, float(b) - float(a))
            if d > turns.get(bone, (0.0, 0.0))[0]:
                turns[bone] = (d, float(b))
    top = sorted(turns.items(), key=lambda kv: -kv[1][0])[:4]
    print('  fastest joint turn (deg in 0.05 s; over 90 flagged): ' + ', '.join(
        f'{b} {d:.0f} at {t:.2f}' + (' FLAGGED' if d > 90 else '') for b, (d, t) in top))

    if times:
        cols = min(6, len(times))
        cw, ch, lh = 330, 240, 20
        rows_n = (len(times) + cols - 1) // cols
        cv = lab.Canvas(cw * cols, (ch + lh) * rows_n * len(views) + 30)
        cv.text(10, 8, f'BULLFROG {name.upper()}  {clip.length:g} S', scale=2)
        for vi, vname in enumerate(views):
            view = lab.VIEWS[vname]
            pts = [(x, y, z) for x in (-26, 26) for y in (-12, 30 + (25 if name in FLIGHT else 0)) for z in (-30, 42)]
            pr = [view.project(q) for q in pts]
            bounds = ((min(a for a, _, _ in pr), max(a for a, _, _ in pr)), (min(b for _, b, _ in pr), max(b for _, b, _ in pr)))
            for k, t in enumerate(times):
                x0 = cw * (k % cols)
                y0 = 30 + (ch + lh) * (vi * rows_n + k // cols)
                cv.frame(x0, y0, cw, ch + lh)
                cv.text(x0 + 6, y0 + 5, f'{view.title} {t:.2f} S')
                fr = lab.Frame(cv, (x0 + 1, y0 + lh, cw - 2, ch - 1), view, bounds)
                up = rise(name, t, clip.length)
                fr.boxes([(b, [(p[0], p[1] + up, p[2]) for p in cs], c)
                          for b, cs, c in model.posed(clip.pose(min(t, clip.length)))])
                if water:
                    for xx in (-24.0, 24.0):
                        fr.canvas.line(fr.px((xx, 8.0, -30.0)), fr.px((xx, 8.0, 42.0)), (40, 110, 230), fr.cell, depth=False)
                else:
                    fr.grid(0.0, 0.0, ((-24.0, 24.0), (-30.0, 42.0)), step=4.0)
        os.makedirs(OUT, exist_ok=True)
        png = os.path.join(OUT, f'fc_{name}.png')
        cv.save(png)
        print('  wrote', png)
