"""The monitor's water moves checked offline, as the game draws them (the Animal Maker Engine step 4, 2026-11-19; made
like tools/climb_check.py, which checks the climbs): the trunk's joints turned on their outer edge (the game's hinge),
the tail dragged roughly as TailDrag does (pulled after the hips, lying on the bank, afloat along the water's surface).
Builds the clip in memory from tools/monitor_models.py, nothing written.
  python tools/water_check.py <in|drop|drop2|dash|out|outup> [size] [times comma separated] [views side,three,front]
Prints: reach misses (a foot place out of its leg's reach), parts inside the bank or the bottom, the tail inside them,
where the head, back and hips lie against the water's surface over time, the path's speed at its start and end, the
biggest jump of any part between two frames, and how far the last (going in) or first (coming out) frame is from the
swim's; with times, draws a film strip (wc_<clip>.png in the current folder): the bank grey, the water's surface and
bottom blue lines, parts inside the bank red dots."""
import sys, math, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import monitor_models as mm
import anim_lab as lab

kind = sys.argv[1]
size = float(sys.argv[2]) if len(sys.argv) > 2 else 1.0
times = [float(x) for x in sys.argv[3].split(',')] if len(sys.argv) > 3 and sys.argv[3] else None
views = tuple(sys.argv[4].split(',')) if len(sys.argv) > 4 else ('side', 'three')

reach_log, got = [], {}
orig_move = mm.water_move


def water_move(k, s, swim_table, swim_leg, flick, log=None):
    made = orig_move(k, s, swim_table, swim_leg, flick, log=reach_log)
    got['scene'], got['path'], got['swim'] = made[2], made[3], swim_table
    return made


mm.water_move = water_move
mm.WATER_KINDS, mm.CLIMB_SIZES = (kind,), (size,)
out, worst = mm.clips()
name = mm.water_name(kind, size)
data = out[name]
sc = got['scene']
B = mm.block_px(size)
going_in = kind not in ('out', 'outup')
print(f'{name}  block {B:.2f} px  length {data["animation_length"]} s  worst reach {worst[name]:.2f} px')
print(f'  scene: edge z {sc["edge"]:g}, bank top {sc["top"]:.1f}, surface {sc["surface"]:.2f}, bottom {sc["bottom"]:.1f},'
      f' afloat feet at {sc["float"]:.2f}')


def by_time(log, every=0.1):
    rows = {}
    for t, k, v in log:
        key = round(round(t / every) * every, 2)
        rows.setdefault(key, {})
        rows[key][k] = max(rows[key].get(k, 0), v)
    return rows


if reach_log:
    print('out of reach (time: leg px):')
    for t, r in sorted(by_time([x for x in reach_log if x[2] > 0.3]).items()):
        print(f'  {t:4.2f}: ' + ', '.join(f'{k} {v:.1f}' for k, v in r.items()))


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
tails = sorted((b for b in model.bones if b['name'].startswith('tail')), key=lambda b: int(b['name'][4:]))
rest = [b.get('rotation', [0, 0, 0])[0] for b in tails]
R0, R1 = mm.TAIL_R0, mm.TAIL_R1


def land(z):
    return z < sc['edge'] if sc['land_ahead'] else z > sc['edge']


def tail_floor(z, r):
    """The least height a tail joint lies at (its thickness over the bank or the bottom)."""
    return (sc['top'] if land(z) else sc['bottom']) + r


def sim():
    """The tail chain tick by tick: each joint pulled after the one in front, sagging onto the bank, afloat along the
    water's surface (TailDrag with its afloat ground), never in the bank."""
    n = len(tails)
    pose0 = clip.pose(0.0)
    joints = [list(model.world(b['name'], b['pivot'], pose0)[1:]) for b in tails]
    joints.append(list(model.world(tails[-1]['name'], [0, mm.TAIL_AXIS, tails[-1]['pivot'][2] + 4.0], pose0)[1:]))
    if not going_in:          # coming out it starts afloat, the tail level along the surface behind
        y0, z0 = joints[0]
        joints = [[sc['surface'] - mm.TAIL_FLOAT, z0 + 4.0 * i] for i in range(n + 1)]
    rows = []
    for k in range(round(clip.length * 20) + 1):
        pose = clip.pose(k / 20)
        joints[0] = list(model.world(tails[0]['name'], tails[0]['pivot'], pose)[1:])
        for i in range(1, n + 1):
            r = R0 + (R1 - R0) * i / n
            y, z = joints[i]
            if land(z):
                y -= 0.6                              # sag onto the bank
            else:
                y += (sc['surface'] - mm.TAIL_FLOAT - y) * 0.5      # afloat: eased to the surface
            py, pz = joints[i - 1]
            dy, dz = y - py, z - pz
            d = math.hypot(dy, dz) or 1.0
            y, z = py + 4.0 * dy / d, pz + 4.0 * dz / d
            y = max(y, tail_floor(z, r))
            joints[i] = [y, z]
        body_x = pose['body'][0][0]
        prev, vals = body_x, []
        for i in range(n):
            (y0, z0), (y1, z1) = joints[i], joints[i + 1]
            elev = math.degrees(math.atan2(y1 - y0, z1 - z0))
            vals.append(elev - prev - rest[i])
            prev = elev
        rows.append(vals)
    return rows


tail_vals = sim()


class DragClip(lab.Clip):
    def pose(self, t):
        p = super().pose(t)
        vals = tail_vals[min(len(tail_vals) - 1, max(0, round(t * 20)))]
        for b, v in zip(tails, vals):
            yaw = p.get(b['name'], ((0, 0, 0),))[0][1]
            p[b['name']] = ((v, yaw, 0.0), (0, 0, 0), (1, 1, 1))
        return p


clip2 = DragClip(name, data)

# parts inside the bank or the bottom, and the biggest jump of a part between two frames
hits, tail_hits, jumps = {}, {}, {}
last = None
steps = round(clip2.length * 20)
for k in range(steps * 4 + 1):         # four times a key: the game eases each angle between keys, a spin shows there
    t = k / 80
    now = {}
    for bone, corners, _ in model.posed(clip2.pose(t)):
        now[bone] = corners
        pts = list(corners) + [tuple((a + b) / 2 for a, b in zip(corners[i], corners[j]))
                               for i in range(8) for j in range(i + 1, 8) if bin(i ^ j).count('1') == 1]
        worst_d = max(mm.scene_solid(sc, y, z) for x, y, z in pts)
        if k % 4:
            continue
        if bone.startswith('tail'):
            if worst_d > 0.5:
                tail_hits[bone] = max(tail_hits.get(bone, (0, 0)), (worst_d, t))
            continue
        if ('claw' in bone or 'toe' in bone) and worst_d <= 1.05:
            continue
        if worst_d > 0.4:
            key = (round(t, 2), bone)
            hits[key] = max(hits.get(key, 0), worst_d)
    if last:
        for bone, corners in now.items():
            if bone in last:
                d = max(math.dist(a, b) for a, b in zip(corners, last[bone]))
                if d > jumps.get(bone, (0, 0))[0]:
                    jumps[bone] = (d, t)
    last = now
if hits:
    print('inside the bank or the bottom (time, bone, px):')
    rows = {}
    for (tt, b), d in sorted(hits.items()):
        rows.setdefault(tt, []).append(f'{b}:{d:.1f}')
    for tt, r in rows.items():
        print(f'  {tt:4.2f} ' + ' '.join(r[:7]) + (' ...' if len(r) > 7 else ''))
else:
    print('inside the bank or the bottom: nothing')
if tail_hits:
    print('the tail inside the bank (bone: px at time): ' + ', '.join(f'{b} {d:.1f} at {t:.2f}' for b, (d, t) in sorted(tail_hits.items())))
top = sorted(jumps.items(), key=lambda kv: -kv[1][0])[:6]
print('biggest jump of a part in 1/80 s (px at time; a walking foot moves about 1): ' + ', '.join(f'{b} {d:.1f} at {t:.2f}' for b, (d, t) in top))
# the fastest turn of a leg's joints between two keys (degrees in 0.05 s, the biggest angle of the change): an elbow or
# knee flipping round barely moves a part's place, so the jumps above miss it (the drop's forearm swung 280 degrees in a
# fifth of a second, 2026-11-19); a walking foot turns about 37, the confirmed climbs a foot up to 80
turns = {}
for bone, chans in data['bones'].items():
    if not any(bone.startswith(f'{k}_{j}_') for k in ('arm', 'leg') for j in ('upper', 'lower', 'foot')):
        continue
    keys = chans.get('rotation', {})
    ts = sorted(keys, key=float)
    for a_, b_ in zip(ts, ts[1:]):
        d = max(abs(x - y) for x, y in zip(keys[a_]['vector'] if isinstance(keys[a_], dict) else keys[a_],
                                           keys[b_]['vector'] if isinstance(keys[b_], dict) else keys[b_]))
        d *= 0.05 / max(1e-6, float(b_) - float(a_))
        if d > turns.get(bone, (0.0, 0.0))[0]:
            turns[bone] = (d, float(b_))
top = sorted(turns.items(), key=lambda kv: -kv[1][0])[:4]
print('fastest joint turn (degrees in 0.05 s at time; over 90 flagged; the confirmed climbs reach 80): ' + ', '.join(
    f'{b} {d:.0f} at {t:.2f}' + (' FLAGGED' if d > 90 else '') for b, (d, t) in top))

# against the water's surface: the snout's tip, the top of the back (chest), the hips' top
print('against the surface (px over it; time: snout, chest top, hips top, root):')
for k in range(0, steps + 1, 5):
    t = k / 20
    pose = clip2.pose(t)
    snout = model.world('head', [0, 11.5, -48.0], pose)
    chest = model.world('chest', [0, mm.BODY_TOP, -24.0], pose)
    hips = model.world('hips', [0, mm.BODY_TOP, -6.0], pose)
    dy, dz = clip.travel(t)
    s = sc['surface']
    print(f'  {t:4.2f}: snout {snout[1] - s:6.1f} (z {snout[2]:6.1f})  chest {chest[1] - s:6.1f}  hips {hips[1] - s:6.1f}'
          f'  root {dy - s:6.1f} at z {dz:6.1f}')

# the path's speeds (px a tick; blocks a tick)
path = got['path']
per_block = mm.block_px(size)
v_start = -(path[1][2] - path[0][2])
v_end = -(path[-1][2] - path[-2][2])
print(f'path speed: start {v_start:.2f} px/tick ({v_start / per_block:.3f} blocks/tick), end {v_end:.2f} px/tick'
      f' ({v_end / per_block:.3f} blocks/tick); end height {path[-1][1]:.2f} px (afloat {sc["float"]:.2f})')

# how far its last (going in) or first (coming out) frame is from the swim's first frame
swim0 = got['swim'][0]
frame = data['bones']
far = []
for bone, chans in swim0.items():
    if bone.startswith('tail'):
        continue
    for ch, v in chans.items():
        keys = frame.get(bone, {}).get(ch)
        if not keys:
            continue
        ks = sorted(keys, key=float)
        mine = keys[ks[-1] if going_in else ks[0]]
        d = max(abs(a - b) for a, b in zip(mine, v))
        if d > 0.5:
            far.append(f'{bone}.{ch} {d:.1f}')
print(('last' if going_in else 'first') + ' frame against the swim\'s first: ' + (', '.join(far) if far else 'the same'))
# and the other end against standing (the idle's first frame): an angle far off spins as the game eases into or out of it
idle = out['idle']['bones']
far = []
for bone, chans in frame.items():
    keys = chans.get('rotation')
    if not keys or bone.startswith('tail'):
        continue
    ks = sorted(keys, key=float)
    mine = keys[ks[0] if going_in else ks[-1]]
    ik = idle.get(bone, {}).get('rotation')
    ref = ik[sorted(ik, key=float)[0]] if ik else [0.0, 0.0, 0.0]
    d = max(abs(a - b) for a, b in zip(mine, ref))
    if d > 60.0:
        far.append(f'{bone} {d:.0f}')
print(('first' if going_in else 'last') + ' frame against standing (angles over 60 deg apart): ' + (', '.join(far) if far else 'none'))

if times:
    cols = 6
    cw, ch, lh = 400, 300, 20
    rows_n = (len(times) + cols - 1) // cols
    cv = lab.Canvas(cw * cols, (ch + lh) * rows_n * len(views) + 30)
    cv.text(10, 8, f'{name}  {data["animation_length"]} S  (game hinge, dragged tail)', scale=2)
    lo_z, hi_z = -110.0, 50.0
    if sc['land_ahead']:
        bank = [(-16.0, sc['bottom'] - 6, lo_z), (16.0, sc['top'], sc['edge'])]
    else:
        bank = [(-16.0, sc['bottom'] - 6, sc['edge']), (16.0, sc['top'], hi_z)]
    (bx0, by0, bz0), (bx1, by1, bz1) = bank
    bank_box = ('bank', [(bx0 if not q >> 2 & 1 else bx1, by0 if not q >> 1 & 1 else by1, bz0 if not q & 1 else bz1)
                         for q in range(8)], {f: lab.STEP_GREY for f in lab.FACE_CORNERS})
    lo_y = min(sc['bottom'], sc['float']) - 4.0
    hi_y = max(sc['top'], 0.0) + 40.0
    for vi, vname in enumerate(views):
        view = lab.VIEWS[vname]
        if vname == 'side':
            bounds = ((-30.0, 95.0), (lo_y, hi_y))
        else:
            pts = [(x, y, z) for x in (-14, 14) for y in (lo_y, hi_y) for z in (-95, 20)]
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
            fr.boxes([bank_box])
            fr.boxes(model.posed(pose))
            # the water: its surface and bottom, on the water's side of the edge
            wz0, wz1 = (sc['edge'], hi_z) if sc['land_ahead'] else (lo_z, sc['edge'])
            for yy, col in ((sc['surface'], (40, 110, 230)), (sc['bottom'], (20, 60, 140))):
                for xx in (-12.0, 12.0):
                    fr.canvas.line(fr.px((xx, yy, wz0)), fr.px((xx, yy, wz1)), col, fr.cell, depth=False)
            for bone, corners, _ in model.posed(pose):
                for p in corners:
                    if mm.scene_solid(sc, p[1], p[2]) > 0.6:
                        x, y, _ = fr.px(p)
                        fr.canvas.dot(x, y, 2, lab.BAD, fr.cell)
    png = os.path.abspath(f'wc_{name}.png')
    cv.save(png)
    print('wrote', png)
