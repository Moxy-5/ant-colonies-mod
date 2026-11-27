"""Spots: where one animal touches another (the Motion Studio, phase 3, step 1, 2026-11-26;
docs/plans/motion-studio-phase3.md).

A prey's body plan names spots on its body ("spots"): where a hunter grips it. A hunter's plan names the clips it grips
with ("holds") and the tick each bites. This tool poses the game's own files (the geo model, the animation file) and
writes a table the game reads, so the game never poses a model itself (the engine's method M1: made once offline,
played by the game):

    assets/<mod>/spots/<animal>.json      written here, never by hand

    python tools/spots.py write [animal ...]     every plan with spots or holds (or the named): writes its table
    python tools/spots.py --check                 fails when a table is behind its plan, model or clips, or a holder's
                                                  clip behind the prey's table it was made against (the build's
                                                  checkSpotTables runs it)
    python tools/spots.py report <holder> <prey> [--sizes 0.97:0.9,0.97:0.36]
                                                  where the holder stands for each spot at those sizes (holder:prey),
                                                  how high its jaw is against the spot at its bite, which spots crowd

A prey's "spots" (model px, the geo file's own frame: x to its left, y up, z toward its tail; forward is -z):
  skin    the bones whose boxes make its skin for the rays (its trunk, neck and head: not its legs or tail)
  clips   the clips it can play while held; each is posed every tick (20 a second)
  pairs   the holds the game makes on it: holder, hold clip, both sizes, the prey's clips then ({clip: "once" or
          "loop"}) and the spots it holds there ("spots", the best first; when left out, all that have no pose of
          their own). The game picks a holder's clip by them
          (step 3): of the holder's pairs naming the prey's clip (none: its idle) and the spot, the one whose size is
          nearest the prey's. clip_check.py --pair checks each.
  at      {name: {"bone", "from", "out"}}: the spot moves with "bone"; it lies where a ray from "from" (a point inside
          the body) going "out" (degrees from straight ahead, + to its left, flat) last leaves the skin's boxes at rest.
          The holder faces in along "out" (it stands outside the body that way).
          With "pose" (step 5, 2026-11-26: a carcass's feeding spots) the ray is cast on the body as it lies in that
          pose instead ("from" and "out" in the posed body's frame: where the animal stood, x to its left, y up from
          the ground, z toward its tail); such a spot belongs only to the pairs on its pose.
  poses   {name: {"clip", "at": "end"}}: a still pose it is held in (a carcass: its death clip's last frame); the
          table gets it as a clip of one row under that name, and a pair names it like a clip ({"carcass": "loop"}).

A holder's "holds":
  jaw     the bone whose pivot is the point that bites (the plan's role name: carry)
  clips   {clip: the tick it bites}; the game reads the tick from the table (one place)

A holder's clips made against its prey (the new phase 2, idea 56, 2026-11-27): a hold or feed clip whose jaw is solved
to the prey's spot heights (tools/lion_anims.py) carries a note "made_against <prey> <print>" (its first timeline
key), the print of what it was made from (partner_print: the prey's pair for that clip, the rows of the prey's clips
then, both blends). --check fails when the prey's table has moved on since, naming the clips to make again.

A holder's "strikes" (step 4, 2026-11-26): the clips it strikes with, each carrying its own travel in a "path root"
note made with it (tools/lion_anims.py), and the tick it hits:
  clips   {clip: the tick it hits}

The table (all model px, at size 1, the model's own frame; the game adds where it draws the model and its size):

  spots   names in order (the game syncs a spot by its index), each with "out" and its rest place
  clips   {clip: [one row a tick: x, y, z of each spot in order]}
  holds   {clip: {"hit", "jaw": the jaw at the hit, "ticks": the clip's length, "loop"}} (a loop bites at its hit
          every time round)
  pairs   [{"holder", "hold", "size", "clips": [prey clips], "spots": [spot names, the best first]}] (the plan's,
          for the game)
  trunk   the holder's shoulders and hips (z) and half its width: the line two holders keep a width apart
  strikes {clip: {"hit", "ticks": the clip's length, "lead": the blend (ticks) before its first frame shows, "reach":
          how far its path goes from the strike's start (px), "share": [one a tick from the strike's start, blend
          included: how much of its reach it has gone]}}. Through the blend it goes at its path's starting speed (a
          leap out of a gallop keeps going; one from a stand waits), so the game counts from its start and reads one
          row a tick, with no blend of its own: it places the striker that share of the way from where it set off to
          its place on its prey's spot (docs/plans/motion-studio-phase3.md, step 4).
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

FORMAT = 4                  # changes when the table's layout or the way it is worked out changes
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
        if 'spots' in data or 'holds' in data or 'strikes' in data:
            out.append(model)
    return out


def fingerprint(animal, data):
    """What a table is made from: this tool's format, the plan's sections, the model and its clips."""
    a = cc.Animal(animal)
    h = hashlib.sha1()
    spots = {k: v for k, v in data.get('spots', {}).items() if k != 'about'}   # what the table is made from
    spots['pairs'] = game_pairs(spots.get('pairs', []), spots.get('at', {}))
    blend = a.blend_ticks()[0] if 'strikes' in data else None                   # read from the Java: a strike's lead
    # (review R12-13: the rows are posed through the plan's hinge, tail drag and chain too; a change there left the
    # table behind with the check passing. The posing code itself is still FORMAT's, bumped by hand.)
    checks = data.get('checks') or {}
    posed_by = [checks.get('hinge'), checks.get('tail_drag'), checks.get('px'), data.get('chain')]
    h.update(json.dumps([FORMAT, spots, data.get('holds'), data.get('strikes'), blend, data.get('legs'), data.get('bones'), posed_by],
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


def held_poses(animal, spots):
    """The still poses its plan names ({name: (its clip played, the time)}): a pose is a clip held at its end."""
    out = {}
    for name, spec in spots.get('poses', {}).items():
        if name in animal.data or spec.get('at') != 'end' or spec.get('clip') not in animal.data:
            raise SystemExit(f'{animal.name}: pose {name} must be {{"clip": one of its clips, "at": "end"}} and no clip\'s name')
        pl = cc.Played(animal, spec['clip'])
        out[name] = (pl, pl.length)
    return out


def posed_point(animal, name, spec, skin, pose):
    """A spot found on the body as it lies in a pose: where the ray from 'from' going 'out' (both in the posed body's
    frame) last leaves the skin's posed boxes, as a point of its bone at rest (so it moves with the bone like any spot)."""
    boxes = [corners for bone, corners, _ in animal.model.posed(pose) if bone in skin]
    start, d = spec['from'], direction(spec['out'])
    if cc.body_depth(start, boxes) <= 0:
        raise SystemExit(f'{animal.name}: spot {name} starts outside the skin at {start} in its pose')
    last = None
    for k in range(int(RAY_REACH / RAY_STEP) + 1):
        p = tuple(s + d[i] * k * RAY_STEP for i, s in enumerate(start))
        if cc.body_depth(p, boxes) > 0:
            last = p
    # back into the bone's own frame: the pose moves a point of the bone by a turn and a shift (o + A p)
    bone = spec['bone']
    o = animal.model.world(bone, (0.0, 0.0, 0.0), pose)
    cols = [[w - oc for w, oc in zip(animal.model.world(bone, e, pose), o)] for e in ((1, 0, 0), (0, 1, 0), (0, 0, 1))]
    b = [lc - oc for lc, oc in zip(last, o)]
    a = [[cols[c][r] for c in range(3)] for r in range(3)]
    det = (a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
           + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]))
    out = []
    for c in range(3):                      # Cramer's rule
        m = [[b[r] if k == c else a[r][k] for k in range(3)] for r in range(3)]
        out.append((m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                    + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])) / det)
    return [round(v, 3) for v in out]


def spot_table(animal, spots):
    boxes = skin_boxes(animal, spots['skin'])
    names = list(spots['at'])
    poses = held_poses(animal, spots)
    rest = {}
    for n in names:
        spec = spots['at'][n]
        if 'pose' in spec:
            if spec['pose'] not in poses:
                raise SystemExit(f'{animal.name}: spot {n} names the pose {spec["pose"]}, which its plan does not list (spots.poses)')
            pl, t = poses[spec['pose']]
            rest[n] = posed_point(animal, n, spec, spots['skin'], pl.pose(t))
        else:
            rest[n] = skin_point(animal, n, spec, boxes)
    clips = {}
    for name, (pl, t) in poses.items():     # a pose: a clip of one row
        pose = pl.pose(t)
        clips[name] = [[round(v, 2) for n in names for v in animal.model.world(spots['at'][n]['bone'], rest[n], pose)]]
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
        out[clip] = {'hit': hit, 'jaw': [round(v, 2) for v in pl.world(jaw, pivot, hit / 20)],
                     'ticks': round(pl.length * 20), 'loop': animal.data[clip].get('loop') is True}
    return out


def strike_table(animal, strikes):
    """Each strike's hit, length, lead (the blend), reach and share of it a tick from the strike's start."""
    lead = animal.blend_ticks()[0]
    out = {}
    for clip, hit in strikes['clips'].items():
        if clip not in animal.data:
            raise SystemExit(f'{animal.name}: no clip {clip} for its strikes')
        pl = cc.Played(animal, clip)
        if not pl.clip.path:
            raise SystemExit(f'{animal.name}: strike {clip} has no "path root" note (its travel)')
        n = round(pl.length * 20)
        if hit > n:
            raise SystemExit(f'{animal.name}: {clip} hits at tick {hit}, past its end')
        along = [-pl.clip.travel(k / 20)[1] for k in range(n + 1)]
        start = along[1] - along[0]                                 # through the blend at its starting speed
        along = [along[0] - j * start for j in range(lead, 0, -1)] + along
        reach = along[-1] - along[0]
        if reach <= 0:
            raise SystemExit(f'{animal.name}: strike {clip} goes nowhere')
        out[clip] = {'hit': hit, 'ticks': n, 'lead': lead, 'reach': round(reach, 2),
                     'share': [round((v - along[0]) / reach, 4) + 0.0 for v in along]}
    return out


def pair_spots(pair, at):
    """The spots a pair holds, the best first: its own list, or every spot with no pose of its own."""
    return list(pair['spots']) if 'spots' in pair else [n for n in at if 'pose' not in at[n]]


def game_pairs(pairs, at):
    """The pairs as the game reads them: who holds with which clip, at which prey size, on which clips and spots."""
    return [{'holder': p['holder'], 'hold': p['hold'], 'size': p['size'], 'clips': list(p['clips']),
             'spots': pair_spots(p, at)} for p in pairs]


# --- writing and checking ---------------------------------------------------------------------------------------------
def build(animal):
    data = plan_data(animal)
    a = cc.Animal(animal)
    table = {'about': 'Written by tools/spots.py from the body plan\'s spots and holds, never by hand (model px, size 1, '
                      'the geo file\'s frame: x to the left, y up, z toward the tail).',
             'fingerprint': fingerprint(animal, data)}
    if 'spots' in data:
        table['spots'] = spot_table(a, data['spots'])
        table['spots']['pairs'] = game_pairs(data['spots'].get('pairs', []), data['spots']['at'])
    if 'holds' in data:
        table['holds'] = hold_table(a, data['holds'])
        table['trunk'] = trunk(a)
    if 'strikes' in data:
        table['strikes'] = strike_table(a, data['strikes'])
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
        if 'strikes' in table:
            what.append(', '.join(f"{c} reaching {v['reach']} px" for c, v in table['strikes'].items()))
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
        held = json.load(open(path, encoding='utf-8'))
        if held.get('fingerprint') != fingerprint(animal, plan_data(animal)):
            out.append(f'{animal}: its table is behind its plan, model or clips (python tools/spots.py write {animal})')
        elif json.loads(json.dumps(build(animal))) != held:
            # the fingerprint holds only the plan's sections and the two files; the rows are also made by the posing
            # code and the plan's hinge, tail and chain (code review R12-13), so the table is made again and compared:
            # "would write the same file" is the whole truth
            out.append(f'{animal}: its table is not what would be written now (the posing code or the checks of the plan '
                       f'changed: python tools/spots.py write {animal})')
    return out


def partner_print(prey, holder, hold, table=None):
    """What a holder's clip made against its prey depends on (a short hash): the prey's pair for that clip (its sizes,
    clips and spots), the rows of the prey's spots in those clips in the prey's written table, and both animals' blends
    (read from the Java; hold_sink is laid on the cow's clock by them). None: the prey has no such pair. table: the
    prey's written table (a test hands one in), else read from its file."""
    plan = plan_data(prey)
    pair = next((p for p in plan.get('spots', {}).get('pairs', []) if p['holder'] == holder and p['hold'] == hold), None)
    path = table_path(prey)
    if table is None and os.path.exists(path):
        table = json.load(open(path, encoding='utf-8'))
    if pair is None or table is None:
        return None
    table = table['spots']
    rows = {c: table['clips'].get(c) for c in pair['clips']}
    blends = [cc.Animal(prey).blend_ticks()[0], cc.Animal(holder).blend_ticks()[0]]
    h = hashlib.sha1(json.dumps([pair, table['names'], rows, blends], sort_keys=True).encode())
    return h.hexdigest()[:12]


def made_against(clip):
    """A clip's "made_against <prey> <print>" note as (prey, print), or None."""
    for note in (clip.get('timeline') or {}).values():
        if isinstance(note, str) and note.startswith('made_against '):
            _, prey, sign = note.split()[:3]
            return prey, sign.rstrip(';')
    return None


def partners_stale(prey_tables=None):
    """Each holder's clips made against a prey whose table has moved on since (or no longer has their pair).
    prey_tables: {prey: its table} in place of the files (a test)."""
    out = []
    for animal in sorted(with_spots()):
        if 'holds' not in plan_data(animal):
            continue
        a = cc.Animal(animal)
        path = cc.lab.model_file(a.anim_stem, 'animations', 'entity', f'{a.anim_stem}.animation.json')
        clips = json.load(open(path, encoding='utf-8')).get('animations', {})
        behind = {}
        for name, clip in clips.items():
            made = made_against(clip)
            if made and partner_print(made[0], animal, name, (prey_tables or {}).get(made[0])) != made[1]:
                behind.setdefault(made[0], []).append(name)
        for prey, names in behind.items():
            out.append(f'{animal}: {", ".join(sorted(names))} were made against {prey}\'s spots as they were; they have '
                       f'changed since (make the clips again: python tools/{maker_of(animal)} clips; then python '
                       f'tools/spots.py write {animal})')
    return out


def maker_of(animal):
    """The maker that makes an animal's clips (its tool's name), for the message."""
    for name in (f'{animal}_models.py', f'{animal.split("_")[0]}_models.py', {'lioness': 'lion_models.py'}.get(animal, '')):
        if name and os.path.exists(os.path.join(HERE, name)):
            return name
    return '<its maker>'


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
    # where the game draws each model (model px toward its head), read from the game's Java (the plans' checks.drawn_ahead)
    draw = {holder: cc.Animal(holder).drawn_ahead(), prey: cc.Animal(prey).drawn_ahead()}
    tr = ht['trunk']
    at = plan_data(prey)['spots']['at']
    standing = [n for n in pt['spots']['names'] if 'pose' not in at[n]]     # (a pose's spots: clip_check.py --pair)
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
                for spot in standing:
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
        found = stale() + partners_stale()
        for line in found:
            print(line)
        if found:
            print('The spot tables or the clips made against them are behind (tools/spots.py): run the commands above '
                  'and commit what they write.')
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
