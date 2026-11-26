"""Spots: where one animal touches another (the Motion Studio, phase 3, step 1, 2026-11-26;
docs/plans/motion-studio-phase3.md).

A prey's body plan names spots on its body ("spots"): where a hunter grips it. A hunter's plan names the clips it grips
with ("holds") and the tick each bites. This tool poses the game's own files (the geo model, the animation file) and
writes a table the game reads, so the game never poses a model itself (the engine's method M1: made once offline,
played by the game):

    assets/<mod>/spots/<animal>.json      written here, never by hand

    python tools/spots.py write [animal ...]     every plan with spots or holds (or the named): writes its table
    python tools/spots.py --check                 fails when a table is behind its plan, model or clips (the build's
                                                  checkSpotTables runs it)
    python tools/spots.py report <holder> <prey> [--sizes 0.97:0.9,0.97:0.36]
                                                  where the holder stands for each spot at those sizes (holder:prey),
                                                  how high its jaw is against the spot at its bite, which spots crowd

A prey's "spots" (model px, the geo file's own frame: x to its left, y up, z toward its tail; forward is -z):
  skin    the bones whose boxes make its skin for the rays (its trunk, neck and head: not its legs or tail)
  clips   the clips it can play while held; each is posed every tick (20 a second)
  at      {name: {"bone", "from", "out"}}: the spot moves with "bone"; it lies where a ray from "from" (a point inside
          the body) going "out" (degrees from straight ahead, + to its left, flat) last leaves the skin's boxes at rest.
          The holder faces in along "out" (it stands outside the body that way).

A holder's "holds":
  jaw     the bone whose pivot is the point that bites (the plan's role name: carry)
  clips   {clip: the tick it bites}; the game reads the tick from the table (one place)

The table (all model px, at size 1, the model's own frame; the game adds where it draws the model and its size):
  spots   names in order (the game syncs a spot by its index), each with "out" and its rest place
  clips   {clip: [one row a tick: x, y, z of each spot in order]}
  holds   {clip: {"hit", "jaw": the jaw at the hit}}
  trunk   the holder's shoulders and hips (z) and half its width: the line two holders keep a width apart
"""
import hashlib
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import body_plan  # noqa: E402
import clip_check as cc  # noqa: E402

FORMAT = 1                  # changes when the table's layout or the way it is worked out changes
RAY_STEP, RAY_REACH = 0.05, 64.0


def table_path(animal):
    return os.path.join(body_plan.assets(animal), 'spots', f'{animal}.json')


def plan_data(animal):
    return json.load(open(body_plan.plan_path(animal), encoding='utf-8'))


def with_spots():
    """Every plan (not drafts) that has spots or holds."""
    out = []
    for model, _mod in body_plan.all_plans(drafts=False):
        data = plan_data(model)
        if 'spots' in data or 'holds' in data:
            out.append(model)
    return out


def fingerprint(animal, data):
    """What a table is made from: this tool's format, the plan's sections, the model and its clips."""
    a = cc.Animal(animal)
    h = hashlib.sha1()
    h.update(json.dumps([FORMAT, data.get('spots'), data.get('holds'), data.get('legs'), data.get('bones')],
                        sort_keys=True).encode())
    for path in (cc.lab.model_file(a.model_name, 'geo', 'entity', f'{a.model_name}.geo.json'),
                 cc.lab.model_file(a.anim_stem, 'animations', 'entity', f'{a.anim_stem}.animation.json')):
        with open(path, encoding='utf-8') as f:      # the contents, not the bytes: line endings differ between checkouts
            h.update(json.dumps(json.load(f), sort_keys=True).encode())
    return h.hexdigest()[:16]


# --- the prey's spots -------------------------------------------------------------------------------------------------
def direction(out):
    """A flat direction 'out' degrees from straight ahead (-z), + to the left (+x)."""
    r = math.radians(out)
    return (math.sin(r), 0.0, -math.cos(r))


def skin_boxes(animal, skin):
    """The skin bones' boxes at rest, in the geo file's frame (no bone of theirs may carry a rest turn)."""
    model = animal.model
    for bone in skin:
        for name in model.chain(bone):
            rot = model.by[name].get('rotation')
            if rot and any(rot):
                raise SystemExit(f'{animal.name}: bone {name} turns at rest; spots on it are not supported yet')
    return [corners for bone, corners, _ in model.cubes if bone in skin]


def skin_point(animal, name, spec, boxes):
    """Where the ray from the spot's 'from' going 'out' last leaves the skin's boxes."""
    start, d = spec['from'], direction(spec['out'])
    if cc.body_depth(start, boxes) <= 0:
        raise SystemExit(f'{animal.name}: spot {name} starts outside the skin at {start}')
    last = None
    for k in range(int(RAY_REACH / RAY_STEP) + 1):
        p = tuple(s + d[i] * k * RAY_STEP for i, s in enumerate(start))
        if cc.body_depth(p, boxes) > 0:
            last = p
    return [round(v, 3) for v in last]


def spot_table(animal, spots):
    boxes = skin_boxes(animal, spots['skin'])
    names = list(spots['at'])
    rest = {n: skin_point(animal, n, spots['at'][n], boxes) for n in names}
    clips = {}
    for clip in spots['clips']:
        if clip not in animal.data:
            raise SystemExit(f'{animal.name}: no clip {clip} for its spots')
        pl = cc.Played(animal, clip)
        rows = []
        for k in range(round(pl.length * 20) + 1):
            pose = pl.pose(k / 20)
            row = []
            for n in names:
                row += [round(v, 2) for v in animal.model.world(spots['at'][n]['bone'], rest[n], pose)]
            rows.append(row)
        clips[clip] = rows
    return {'names': names,
            'at': {n: {'out': spots['at'][n]['out'], 'rest': rest[n]} for n in names},
            'clips': clips}


# --- the holder's holds -----------------------------------------------------------------------------------------------
def trunk(animal):
    """Its shoulders and hips (z of its front and hind legs' tops) and half its width (its trunk's boxes)."""
    legs = animal.plan_data['legs']
    by = animal.model.by
    front = [by[legs[k][0]]['pivot'][2] for k in legs if k.endswith('1')]
    hind = [by[legs[k][0]]['pivot'][2] for k in legs if not k.endswith('1')]
    chain = [b for b in animal.plan_data.get('chain', []) if not b.startswith(('tail', 'head', 'neck'))]
    half = max(abs(p[0]) for bone, corners, _ in animal.model.cubes if bone in chain for p in corners)
    return {'shoulders': round(sum(front) / len(front), 2), 'hips': round(sum(hind) / len(hind), 2), 'half_width': round(half, 2)}


def hold_table(animal, holds):
    jaw = holds['jaw']
    pivot = animal.model.by[jaw]['pivot']
    out = {}
    for clip, hit in holds['clips'].items():
        if clip not in animal.data:
            raise SystemExit(f'{animal.name}: no clip {clip} for its holds')
        pl = cc.Played(animal, clip)
        if hit > round(pl.length * 20):
            raise SystemExit(f'{animal.name}: {clip} bites at tick {hit}, past its end')
        out[clip] = {'hit': hit, 'jaw': [round(v, 2) for v in pl.world(jaw, pivot, hit / 20)]}
    return out


# --- writing and checking ---------------------------------------------------------------------------------------------
def build(animal):
    data = plan_data(animal)
    a = cc.Animal(animal)
    table = {'about': 'Written by tools/spots.py from the body plan\'s spots and holds, never by hand (model px, size 1, '
                      'the geo file\'s frame: x to the left, y up, z toward the tail).',
             'fingerprint': fingerprint(animal, data)}
    if 'spots' in data:
        table['spots'] = spot_table(a, data['spots'])
    if 'holds' in data:
        table['holds'] = hold_table(a, data['holds'])
        table['trunk'] = trunk(a)
    return table


def write(animals):
    for animal in animals:
        table = build(animal)
        path = table_path(animal)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(table, f, separators=(',', ':'))
            f.write('\n')
        what = []
        if 'spots' in table:
            s = table['spots']
            what.append(f"{len(s['names'])} spots on {len(s['clips'])} clips ({sum(len(r) for r in s['clips'].values())} ticks)")
        if 'holds' in table:
            what.append(f"{len(table['holds'])} holds")
        print(f'{animal}: ' + ', '.join(what) + f' -> {os.path.relpath(path, body_plan.PROJECT)}')


def stale():
    """The tables behind their plan, model or clips (or missing), and tables with no plan left."""
    out = []
    wanted = set(with_spots())
    for animal in sorted(wanted):
        path = table_path(animal)
        if not os.path.exists(path):
            out.append(f'{animal}: no table (python tools/spots.py write {animal})')
            continue
        held = json.load(open(path, encoding='utf-8')).get('fingerprint')
        if held != fingerprint(animal, plan_data(animal)):
            out.append(f'{animal}: its table is behind its plan, model or clips (python tools/spots.py write {animal})')
    return out


# --- the report -------------------------------------------------------------------------------------------------------
def placement(prey_table, spot, row, prey_size, hold, holder_size, draw_ahead_prey, draw_ahead_holder):
    """Where the holder stands (flat, in the prey's frame, blocks: forward, left) and faces (degrees, + left of the
    prey's heading), with its jaw at the bite on the spot: the same sums as the game's Spots.place."""
    names = prey_table['names']
    i = names.index(spot)
    x, y, z = row[3 * i:3 * i + 3]
    fwd, left, up = (-z + draw_ahead_prey) * prey_size / 16, x * prey_size / 16, y * prey_size / 16
    face = prey_table['at'][spot]['out'] + 180.0
    jx, jy, jz = hold['jaw']
    ahead, side = (-jz + draw_ahead_holder) * holder_size / 16, jx * holder_size / 16
    r = math.radians(face)
    fx, lx = math.cos(r), math.sin(r)          # the holder's forward in the prey's (forward, left)
    pos = (fwd - fx * ahead + lx * side, left - lx * ahead - fx * side)
    return pos, face, up, jy * holder_size / 16


def report(holder, prey, sizes):
    ht, pt = build(holder), build(prey)
    # where the game draws each model (model px toward its head): BuffaloEntity.DRAW_AHEAD_PX, LionEntity.Measures
    draw = {'buffalo': 10.0, 'lioness': -2.85, 'lion': -3.2, 'lion_cub': -0.85}
    tr = ht['trunk']
    for hs, ps in sizes:
        print(f'\n{holder} at size {hs} on {prey} at size {ps} (blocks):')
        for clip, hold in ht['holds'].items():
            for pclip in ('idle', 'rest'):
                rows = pt['spots']['clips'].get(pclip)
                if not rows:
                    continue
                mid = [sum(r[k] for r in rows) / len(rows) for k in range(len(rows[0]))]
                print(f'  {clip} (bites at tick {hold["hit"]}) on a {pclip} {prey}:')
                placed = {}
                for spot in pt['spots']['names']:
                    pos, face, sh, jh = placement(pt['spots'], spot, mid, ps, hold, hs, draw[prey], draw[holder])
                    placed[spot] = (pos, face)
                    print(f'    {spot:12s} stands {pos[0]:+5.2f} ahead {pos[1]:+5.2f} left, facing {face % 360:5.1f}; '
                          f'the spot {sh:4.2f} high, its jaw {jh:4.2f}: {"over" if jh > sh else "under"} it by {abs(jh - sh):4.2f}')
                crowd = []
                names = list(placed)
                for i, a in enumerate(names):
                    for b in names[i + 1:]:
                        gap = trunk_gap(placed[a], placed[b], tr, hs, draw[holder])
                        if gap < 2 * tr['half_width'] * hs / 16:
                            crowd.append(f'{a}+{b} {gap:.2f}')
                print('    crowding (trunks closer than a trunk\'s width): ' + (', '.join(crowd) if crowd else 'none'))


def trunk_gap(a, b, tr, size, draw_ahead):
    """The least distance between two holders' trunk lines (shoulders to hips), each placed (pos, face)."""
    def ends(p):
        (f, l), face = p
        r = math.radians(face)
        fx, lx = math.cos(r), math.sin(r)
        s = (-tr['shoulders'] + draw_ahead) * size / 16
        h = (-tr['hips'] + draw_ahead) * size / 16
        return (f + fx * s, l + lx * s), (f + fx * h, l + lx * h)
    return segment_gap(*ends(a), *ends(b))


def segment_gap(p1, p2, q1, q2):
    def point_seg(p, a, b):
        ax, ay = b[0] - a[0], b[1] - a[1]
        L = ax * ax + ay * ay
        t = 0.0 if L == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * ax + (p[1] - a[1]) * ay) / L))
        return math.hypot(p[0] - a[0] - t * ax, p[1] - a[1] - t * ay)
    def crosses(a, b, c, d):
        def side(p, q, r):
            return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
        return side(a, b, c) * side(a, b, d) < 0 and side(c, d, a) * side(c, d, b) < 0
    if crosses(p1, p2, q1, q2):
        return 0.0
    return min(point_seg(p1, q1, q2), point_seg(p2, q1, q2), point_seg(q1, p1, p2), point_seg(q2, p1, p2))


def main(argv):
    if argv and argv[0] == '--check':
        found = stale()
        for line in found:
            print(line)
        if found:
            print('The spot tables are behind (tools/spots.py): run the commands above and commit the tables.')
            return 1
        return 0
    if argv and argv[0] == 'write':
        write(argv[1:] or with_spots())
        return 0
    if len(argv) >= 3 and argv[0] == 'report':
        sizes = [(0.97, 0.9), (0.97, 0.36)]
        if '--sizes' in argv:
            sizes = [tuple(map(float, s.split(':'))) for s in argv[argv.index('--sizes') + 1].split(',')]
        report(argv[1], argv[2], sizes)
        return 0
    print(__doc__)
    return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
