"""The monitor's climb clip checked offline, as the game draws it (2026-11-19; the user confirmed the climb up made
with it and the frame-by-frame game films, tools/film/monitor_climb_frames.film + tools/film_sheet.py): the trunk
joints turned on their outer edge (the game's hinge), the tail dragged roughly as TailDrag does (a chain pulled after
the hips, sagging onto what is under it). Builds the clip in memory from tools/monitor_models.py, nothing written.
  python tools/climb_check.py [size] [times comma separated] [views side,three,front,top]
prints reach misses, lifts, parts inside the step; with times, draws a zoomed film strip next to this file's run
(cc_<clip>.png in the current folder)."""
import sys, math, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import monitor_models as mm
import anim_lab as lab

size = float(sys.argv[1]) if len(sys.argv) > 1 else 1.0
times = [float(x) for x in sys.argv[2].split(',')] if len(sys.argv) > 2 and sys.argv[2] else None
views = tuple(sys.argv[3].split(',')) if len(sys.argv) > 3 else ('side', 'three')
now = [0.0]
reach_log, lift_log = [], []
orig_leg, orig_foot, orig_clear = mm._Poser.leg, mm._foot_at, mm._clearance


def foot_at(t, plan, lift, reach=False):
    now[0] = t
    return orig_foot(t, plan, lift, reach)


def leg(self, p, L, body_m, body_d, wrist, foot_m, in_body=False, curl=0.0, raised=None):
    before = self.worst
    self.worst = 0.0
    orig_leg(self, p, L, body_m, body_d, wrist, foot_m, in_body, curl, raised)
    if self.worst > 0.3:
        reach_log.append((now[0], L['kind'][0] + L['side'][0], self.worst))
    self.worst = max(before, self.worst)


def clearance(places, B, margin=0.4):
    v = orig_clear(places, B, margin)
    if v > 0.05:
        lift_log.append((now[0], v))
    return v


mm._foot_at, mm._Poser.leg, mm._clearance = foot_at, leg, clearance
mm.CLIMB_SIZES = (size,)
out, worst = mm.clips()
name = mm.climb_name(size)
data = out[name]
B = mm.block_px(size)
zf = -mm.CLIMB_FACE
print(name, 'block px', round(B, 2), 'length', data['animation_length'], 'worst reach', round(worst[name], 2))


def by_time(log, every=0.1):
    rows = {}
    for t, k, v in log:
        key = round(round(t / every) * every, 2)
        rows.setdefault(key, {})
        rows[key][k] = max(rows[key].get(k, 0), v)
    return rows


if reach_log:
    print('out of reach (time: leg px):')
    for t, r in sorted(by_time(reach_log).items()):
        print(f'  {t:4.2f}: ' + ', '.join(f'{k} {v:.1f}' for k, v in r.items()))
if lift_log:
    print('lifted to clear (time: px):')
    for t, r in sorted(by_time([(t, 'lift', v) for t, v in lift_log]).items()):
        print(f'  {t:4.2f}: {r["lift"]:.1f}')


class GameModel(lab.Model):
    """The trunk's joints turned on their outer edge, as MonitorModel hinges them."""
    def transforms(self, pose, names=None):
        xf = super().transforms(pose, names)
        for bone in ('waist', 'ribs', 'chest'):
            if bone in xf and bone in pose:
                rx = pose[bone][0][0]
                if abs(rx) > 1e-6:
                    e = list(xf[bone])
                    e[1] = mm.BODY_TOP if rx > 0 else mm.BODY_BOTTOM
                    xf[bone] = tuple(e)
        return xf


model = GameModel('monitor', mm.model())
clip = lab.Clip(name, data)
tails = [b for b in model.bones if b['name'].startswith('tail')]
tails.sort(key=lambda b: int(b['name'][4:]))
rest = [b.get('rotation', [0, 0, 0])[0] for b in tails]
R0, R1 = mm.TAIL_R0, mm.TAIL_R1


def floor(z):
    return B if z < zf else 0.0


def sim():
    """The tail chain tick by tick (20 a second): each joint pulled after the one in front, sagging, never in the ground."""
    n = len(tails)
    pose0 = clip.pose(0.0)
    joints = [list(model.world(b['name'], b['pivot'], pose0)[1:]) for b in tails]
    last = list(model.world(tails[-1]['name'], [0, mm.TAIL_AXIS, tails[-1]['pivot'][2] + 4.0], pose0)[1:])
    joints.append(last)
    out = []
    steps = round(clip.length * 20)
    for k in range(steps + 1):
        t = k / 20
        pose = clip.pose(t)
        joints[0] = list(model.world(tails[0]['name'], tails[0]['pivot'], pose)[1:])
        for i in range(1, n + 1):
            r = R0 + (R1 - R0) * i / n
            y, z = joints[i]
            y -= 0.6                                  # sag
            py, pz = joints[i - 1]
            dy, dz = y - py, z - pz
            d = math.hypot(dy, dz) or 1.0
            y, z = py + 4.0 * dy / d, pz + 4.0 * dz / d
            y = max(y, floor(z) + r)
            joints[i] = [y, z]
        # elevations of each piece (degrees up, pointing back) -> clip turns
        body_x = pose['body'][0][0]
        prev = body_x
        vals = []
        for i in range(n):
            (y0, z0), (y1, z1) = joints[i], joints[i + 1]
            elev = math.degrees(math.atan2(y1 - y0, z1 - z0))
            vals.append(elev - prev - rest[i])
            prev = elev
        out.append(vals)
    return out


tail_vals = sim()


class DragClip(lab.Clip):
    def pose(self, t):
        p = super().pose(t)
        vals = tail_vals[min(len(tail_vals) - 1, max(0, round(t * 20)))]
        for b, v in zip(tails, vals):
            p[b['name']] = ((v, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
        return p


clip2 = DragClip(name, data)
hits = {}
t = 0.0
while t <= clip2.length + 1e-6:
    for bone, corners, _ in model.posed(clip2.pose(t)):
        if bone.startswith('tail'):
            continue
        pts = list(corners) + [tuple((a + b) / 2 for a, b in zip(corners[i], corners[j]))
                               for i in range(8) for j in range(i + 1, 8) if bin(i ^ j).count('1') == 1]
        for x, y, z in pts:
            d = min(B - y, zf - z) if z < zf and y < B else (-y if y < 0 else 0.0)
            if 'claw' in bone and d <= 1.05:
                continue
            if d > 0.4:
                key = (round(t, 2), bone)
                hits[key] = max(hits.get(key, 0), d)
    t += 0.05
if hits:
    print('inside the step or ground (time, bone, px):')
    rows = {}
    for (tt, b), d in sorted(hits.items()):
        rows.setdefault(tt, []).append(f'{b}:{d:.1f}')
    for tt, r in rows.items():
        print(f'  {tt:4.2f} ' + ' '.join(r[:7]) + (' ...' if len(r) > 7 else ''))
if times:
    # zoomed on the step: side (the camera's right is -z), or three-quarter
    cols = 6
    cw, ch, lh = 400, 300, 20
    rows_n = (len(times) + cols - 1) // cols
    cv = lab.Canvas(cw * cols, (ch + lh) * rows_n * len(views) + 30)
    cv.text(10, 8, f'{name}  {data["animation_length"]} S  (game hinge, dragged tail)', scale=2)
    ext = ((-16.0, 16.0), (-95.0, 50.0))
    stepb = lab.step_box(clip.step, 0.0, ext)
    for vi, vname in enumerate(views):
        view = lab.VIEWS[vname]
        if vname == 'side':
            bounds = ((-12.0, 88.0), (-3.0, 47.0))
        else:
            pts = [(x, y, z) for x in (-14, 14) for y in (0, 40) for z in (-80, 10)]
            pr = [view.project(q) for q in pts]
            bounds = ((min(a for a, _, _ in pr), max(a for a, _, _ in pr)), (min(b for _, b, _ in pr), max(b for _, b, _ in pr)))
        for k, t in enumerate(times):
            x0 = cw * (k % cols)
            y0 = 30 + (ch + lh) * (vi * rows_n + k // cols)
            cv.frame(x0, y0, cw, ch + lh)
            cv.text(x0 + 6, y0 + 5, f'{view.title} {t:.2f} S')
            cell = (x0 + 1, y0 + lh, cw - 2, ch - 1)
            fr = lab.Frame(cv, cell, view, bounds)
            pose = clip2.pose(t)
            fr.boxes([stepb])
            fr.boxes(model.posed(pose))
            fr.grid(0.0, 0.0, ext, step=21.333, bold=1)
            lab.mark_below(fr, model, pose, 0.0, clip.step)
    png = os.path.abspath(f'cc_{name}.png')
    cv.save(png)
    print('wrote', png)
