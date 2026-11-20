"""The crocodile's breeding clips checked offline, as the game draws them (animal life cycle step 4, 2026-11-20; made
like tools/water_check.py; CLAUDE.md's hard rule: every animation checked offline and in the game frame by frame).
Reads the game's own files (python tools/crocodile_models.py game first).
  python tools/croc_check.py [clip ...] [--model crocodile_teen] [--times 0,0.5,1] [--views side,three,front]
With no clip names, checks the six breeding clips: head_slap, rumble, snout_up, court_roll (in the water), hiss, dig
(on land). Prints for each: parts under the ground (land clips; the game sets standing feet down up to 5 pixels, so
feet a little over the ground are fine), where the snout, the back's top and the tail's top lie against the water's
surface (water clips), the jaw's least opening (under 0: the teeth through the lower jaw), the biggest jump of any part
between two frames and the fastest joint turn. With --times, draws a film strip (cc_croc_<clip>.png in the current
folder): the ground grey, the water's surface a blue line."""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anim_lab as lab
import json

WATER = ('head_slap', 'rumble', 'snout_up', 'court_roll', 'swim', 'float', 'lunge', 'snap_left', 'snap_right', 'hold', 'eat', 'shake',
         'young_swim', 'young_float')
LAND = ('hiss', 'dig', 'idle', 'walk', 'bask', 'flop', 'death', 'drag', 'young_walk', 'young_run', 'young_idle')
BREEDING = ('head_slap', 'rumble', 'snout_up', 'court_roll', 'hiss', 'dig')
# The water's surface over the model's floor when it rests afloat (CrocodileEntity.AFLOAT, 0.462 of its usual size in
# blocks, is 7.4 model pixels: the brows, the eyes and the back out, the flanks under).
SURFACE = 0.462 * 16.0

args = sys.argv[1:]
times, views = None, ('side', 'three', 'front')
model_name = 'crocodile'
names = []
i = 0
while i < len(args):
    if args[i] == '--times':
        times = [float(x) for x in args[i + 1].split(',')]
        i += 2
    elif args[i] == '--model':
        model_name = args[i + 1]
        i += 2
    elif args[i] == '--views':
        views = tuple(args[i + 1].split(','))
        i += 2
    else:
        names.append(args[i])
        i += 1
names = names or list(BREEDING)

model = lab.Model(model_name)
data = json.load(open(lab.model_file('crocodile', 'animations', 'entity', 'crocodile.animation.json'), encoding='utf-8'))['animations']


def top_of(posed, prefix):
    return max((p[1] for b, cs, _ in posed if b.startswith(prefix) for p in cs), default=float('nan'))


def low_of(posed, prefix):
    return min((p[1] for b, cs, _ in posed if b.startswith(prefix) for p in cs), default=float('nan'))


for name in names:
    clip = lab.Clip(name, data[name])
    water = name in WATER
    print(f'== {name}  {clip.length:g} s  ({"in the water" if water else "on land"})')
    under, jumps, last = {}, {}, None
    jaw_min = (999.0, 0.0)
    steps = round(clip.length * 80)
    for k in range(steps + 1):
        t = k / 80
        pose = clip.pose(t)
        posed = model.posed(pose)
        now = {b: cs for b, cs, _ in posed}
        jaw = pose.get('jaw', ((0, 0, 0),))[0][0]
        if jaw < jaw_min[0]:
            jaw_min = (jaw, t)
        if not water and k % 4 == 0:
            for b, cs, _ in posed:
                if b == 'carried':
                    continue
                d = -min(p[1] for p in cs)
                if d > 0.4 and not ('foot' in b and d <= 1.05):
                    under[b] = max(under.get(b, (0, 0)), (d, t))
        if last:
            for b, cs in now.items():
                if b in last:
                    d = max(math.dist(a, c) for a, c in zip(cs, last[b]))
                    if d > jumps.get(b, (0, 0))[0]:
                        jumps[b] = (d, t)
        last = now
    if not water:
        print('  under the ground (px at time): ' + (', '.join(f'{b} {d:.1f} at {t:.2f}' for b, (d, t) in sorted(under.items()))
                                                    if under else 'nothing'))
        feet = []
        for k in range(0, steps + 1, 8):
            pose = clip.pose(k / 80)
            posed = model.posed(pose)
            feet.append(max(low_of(posed, f'{kind}_foot_{s}') for kind in ('arm', 'leg') for s in ('left', 'right')))
        print(f'  highest foot over the ground (the game reaches down to 5 px): {max(feet):.1f} px')
    else:
        print('  against the surface (px over it; time: snout top, jaw low, back top, tail1 top, tail3 top):')
        for k in range(0, round(clip.length * 20) + 1, 2):
            t = k / 20
            posed = model.posed(clip.pose(t))
            print(f'    {t:4.2f}: snout {top_of(posed, "head") - SURFACE:5.1f}  jaw {low_of(posed, "jaw") - SURFACE:5.1f}'
                  f'  back {top_of(posed, "chest") - SURFACE:5.1f}  tail1 {top_of(posed, "tail1") - SURFACE:5.1f}'
                  f'  tail3 {top_of(posed, "tail3") - SURFACE:5.1f}')
    print(f'  jaw least open: {jaw_min[0]:.1f} deg at {jaw_min[1]:.2f}' + ('  TEETH THROUGH THE JAW' if jaw_min[0] < -0.5 else ''))
    top = sorted(jumps.items(), key=lambda kv: -kv[1][0])[:5]
    print('  biggest jump in 1/80 s (px at time; a walking foot moves about 1): ' + ', '.join(f'{b} {d:.1f} at {t:.2f}' for b, (d, t) in top))
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
        cw, ch, lh = 380, 260, 20
        rows_n = (len(times) + cols - 1) // cols
        cv = lab.Canvas(cw * cols, (ch + lh) * rows_n * len(views) + 30)
        cv.text(10, 8, f'{model_name.upper()} {name.upper()}  {clip.length:g} S', scale=2)
        for vi, vname in enumerate(views):
            view = lab.VIEWS[vname]
            pts = [(x, y, z) for x in (-30, 30) for y in (-4, 40) for z in (-45, 72)]
            pr = [view.project(q) for q in pts]
            bounds = ((min(a for a, _, _ in pr), max(a for a, _, _ in pr)), (min(b for _, b, _ in pr), max(b for _, b, _ in pr)))
            for k, t in enumerate(times):
                x0 = cw * (k % cols)
                y0 = 30 + (ch + lh) * (vi * rows_n + k // cols)
                cv.frame(x0, y0, cw, ch + lh)
                cv.text(x0 + 6, y0 + 5, f'{view.title} {t:.2f} S')
                fr = lab.Frame(cv, (x0 + 1, y0 + lh, cw - 2, ch - 1), view, bounds)
                pose = clip.pose(t)
                fr.boxes([c for c in model.posed(pose) if c[0] != 'carried'])
                if water:
                    for xx in (-24.0, 24.0):
                        fr.canvas.line(fr.px((xx, SURFACE, -45.0)), fr.px((xx, SURFACE, 72.0)), (40, 110, 230), fr.cell, depth=False)
                else:
                    fr.grid(0.0, 0.0, ((-24.0, 24.0), (-45.0, 72.0)), step=4.0)
        png = os.path.abspath(f'cc_{model_name}_{name}.png')
        cv.save(png)
        print('  wrote', png)
