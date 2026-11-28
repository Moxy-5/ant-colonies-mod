"""A clip off the game's files as a video, with no game running (the Motion Studio, phase 3 part B step 2, idea 61,
2026-11-28; docs/plans/motion-studio-phase3.md). Made through tools/clip_check.py:

    python tools/clip_check.py <animal> <clips> --video [--cams side,front,above] [--slow 4] [--plays 2] [--fixed]
                               [--no-paths] [--size s] [--model <young model>]
    python tools/clip_check.py <animal> [clips] --switches --video ...      each clip change, a's end into b
    e.g. python tools/clip_check.py lioness walk trot --video
         python tools/clip_check.py lioness walk --switches --video --cams side,three-quarter

The clip is posed exactly as the checker poses it (tools/clip_check.py: the clip files, the body plan, the move's path,
the dragged tail, a thrown flight; a clip change through the checker's own Blend), written as a recording in the
game's own format (client/BodyRecorder: each bone's 3x4 matrix a moment, the boxes' corners in the bone's frame, the
blocks round it), and that recording is drawn by tools/replay_video.py, the drawing every game film's video uses: the
cameras side by side, played at the game's speed then slowed, the bar with the clip, how far in, and each foot judged
by the one feet rule (tools/recording.py). So an offline clip and a game film look alike and are judged alike, and the
recording can be opened on the replay page too (python tools/replay.py <its folder>) to turn and zoom.

The ground: a walk (the plan's checks.walks, else its stride note: clip_check.ground_speed) walks across still ground
at its own ground speed, and the cameras follow it, so the ground's block lines move under it like a treadmill: a
planted foot that slides shows against them, red on the bar and in the foot paths. A clip that does not walk stands
still; a move with its own path (a climb, into the water) carries itself across its scene's real blocks (the step, the
bank and the water, the perch) as the checker places them, a block high at its size. A clip change eases the ground's
speed from clip a's to clip b's over the blend (my pick: the game's body slows over a few ticks as its mind stops it).

Its own check, every time: the recording read back (tools/recording.py) gives every box corner where the checker
poses it, within CHECK_PX model pixels (printed); so the video draws what clip_check judged.

    references/anim_lab/<animal>[_size<s>]/video/vid_<model>_<clip>.mp4   and   vid_sw_<a>_<b>.mp4
    run-film/clip_video/<animal>[_size<s>]/<model>_<clip>/body_<clip>.jsonl.gz   (the recording)

Timing (my picks): three moments a game tick (the game's drawing at 60 frames a second, so 1x shows a new pose every
frame); a loop plays --plays times (default 2), a clip played once plays once and holds its end HOLD_TICKS; a clip
change shows up to LEAD seconds of clip a (the moment of it the check judged, R12-11), the blend, then up to LEAD of
clip b.
"""
import gzip
import json
import math
from pathlib import Path
from types import SimpleNamespace

import anim_lab as lab
import recording

ROOT = Path(__file__).resolve().parent.parent
SUB = 3                 # moments a game tick (the game draws three frames a tick at 60 frames a second)
YAW = -90.0             # the animal faces east (+x), Minecraft's yaw -90
HOLD_TICKS = 10         # a clip played once holds its end this long
LEAD = 1.5              # seconds of each clip round a clip change, at most
CHECK_PX = 0.01         # the recording's corners must lie within this many model pixels of the checker's
WATER_TOP = 8.0 / 9.0   # a full water block's surface (Minecraft: 8/9 of the block)
MARGIN = 3              # blocks of ground round the animal's way, past its own length
MARKS_MAX = 600         # red marks a moment at most (the page draws as many)


# --- the game's frame ---------------------------------------------------------------------------------------------
def frame(yaw, px):
    """The game's turn of an offline point (model px; forward is -z) to its place from where the animal is drawn
    (blocks), at this yaw: measured on the feast bench's recording (2026-11-28, every moment of a lying lioness to 1e-9):
    x mirrored, turned by 180 degrees and the yaw, a model pixel px blocks."""
    th = math.radians(180.0 + yaw)
    c, s = math.cos(th), math.sin(th)
    return ((-c * px, 0.0, -s * px), (0.0, px, 0.0), (-s * px, 0.0, c * px))


def mul(m, v):
    return tuple(sum(m[r][i] * v[i] for i in range(3)) for r in range(3))


def bone_rows(model, xf, bone, lift, w):
    """The bone's 3x4 matrix as the recorder writes it (rows; a point in the recording's bone frame, the model's
    file frame mirrored in x and over 16, as GeckoLib bakes it), from the lab's own steps (lab.place, an affine move)."""
    steps = model._steps(bone, xf)
    o = lab.place(steps, (0.0, 0.0, 0.0))
    cols = []
    for e, sign in (((1.0, 0.0, 0.0), -16.0), ((0.0, 1.0, 0.0), 16.0), ((0.0, 0.0, 1.0), 16.0)):
        p = lab.place(steps, e)
        cols.append(mul(w, tuple((p[i] - o[i]) * sign for i in range(3))))
    t = mul(w, (o[0], o[1] + lift, o[2]))
    return [round(v, 6) for r in range(3) for v in (cols[0][r], cols[1][r], cols[2][r], t[r])]


def rec_corners(corners):
    """A lab box's 8 corners (k = 4 x + 2 y + z of its rest box) in the recorder's order (c = x + 2 y + 4 z) and frame
    (x mirrored, over 16): its low x is the lab's high x."""
    out = []
    for c in range(8):
        k = ((1 - (c & 1)) << 2) | (c & 2) | ((c >> 2) & 1)
        x, y, z = corners[k]
        out += [round(-x / 16.0, 6), round(y / 16.0, 6), round(z / 16.0, 6)]
    return out


# --- the world round the clip -------------------------------------------------------------------------------------
class World:
    """The blocks and water of the clip's scene and where the animal stands at the start (blocks), its way +x."""

    def __init__(self, animal, sc, travel, clip_kind, px):
        L = animal.model.length * px
        travel *= px
        self.blocks, self.water = {}, {}
        x0, y0 = 0.5, 0.0
        far = math.ceil(L + MARGIN)
        lo_x, hi_x = -far, math.ceil(travel) + far
        side = math.ceil(L / 2 + MARGIN)
        zs = range(-side, side + 1)

        def solid(x, y, z, box=(0, 0, 0, 1, 1, 1)):
            self.blocks[(x, y, z)] = list(box)

        def wet(x, y, z, h):
            self.water[(x, y, z)] = round(h, 4)

        kind = sc.kind
        if kind == 'step':
            zf, h = sc.step
            x0 = px * zf                               # the face on the block line x = 0
            up = h > 0
            for x in range(lo_x, hi_x + 1):
                for z in zs:
                    if up:
                        solid(x, -1, z)
                        if x >= 0:
                            solid(x, 0, z)
                    else:
                        solid(x, -2, z)
                        if x < 0:
                            solid(x, -1, z)
        elif kind == 'water':
            b = sc.bank
            x0 = px * b['edge']                        # the bank's edge on the block line x = 0
            y0 = -px * b['top']                        # the bank's top at y = 0
            bed = round(y0 + px * b['bottom'])
            surface = y0 + px * sc.surface
            for x in range(lo_x, hi_x + 1):
                land = (x >= 0) if b['land_ahead'] else (x < 0)
                for z in zs:
                    if land:
                        for y in range(bed - 1, 0):
                            solid(x, y, z)
                    else:
                        solid(x, bed - 1, z)
                        y = bed
                        while y < surface - 1e-6:
                            wet(x, y, z, min(1.0, surface - y))
                            y += 1
        elif kind == 'open_water':
            y0 = WATER_TOP - px * sc.surface           # the surface 8/9 up the water block at y = 0
            for x in range(lo_x, hi_x + 1):
                for z in zs:
                    solid(x, -3, z)
                    for y in (-2, -1):
                        wet(x, y, z, 1.0)
                    wet(x, 0, z, WATER_TOP)
        elif kind == 'perch':
            p = sc.perch
            x0 = 0.5 + px * p['z']                      # the bar's middle on the middle of the block line x = 0
            y0 = 0.75 - px * p['y']                     # its top 3/4 up the block at y = 0
            r = px * p['r']
            for z in zs:
                solid(0, 0, z, (0.5 - r, 0.75 - 2 * r, 0, 0.5 + r, 0.75, 1))
            for x in range(lo_x, hi_x + 1):
                for z in zs:
                    solid(x, -5, z)
        elif kind == 'air':
            for x in range(lo_x, hi_x + 1):
                for z in zs:
                    solid(x, -4, z)
        else:                                          # flat ground (land and digging clips)
            for x in range(lo_x, hi_x + 1):
                for z in zs:
                    solid(x, -1, z)
        self.start = (x0, y0, 0.5)
        self.in_water = kind in ('open_water',) or clip_kind == 'water'

    def entries(self):
        return ([[x, y, z, box] for (x, y, z), box in sorted(self.blocks.items())],
                [[x, y, z, h] for (x, y, z), h in sorted(self.water.items())])


# --- the recording ------------------------------------------------------------------------------------------------
def header(animal, label, world, px):
    """The first line, as client/BodyRecorder writes it: the bones by the model's own names, their boxes."""
    model = animal.model
    to_bone = model.plan.to_bone if model.plan else {}
    by_bone = {}
    for bn, corners, _ in model.cubes:
        by_bone.setdefault(bn, []).append(rec_corners(corners))
    bones = []
    for b in model.bones:
        entry = {'name': to_bone.get(b['name'], b['name'])}
        if b.get('parent'):
            entry['parent'] = to_bone.get(b['parent'], b['parent'])
        piv = b.get('pivot', [0, 0, 0])
        entry['pivot'] = [round(-piv[0] / 16.0, 6), round(piv[1] / 16.0, 6), round(piv[2] / 16.0, 6)]
        entry['cubes'] = by_bone.get(b['name'], [])
        bones.append(entry)
    xs = [p[0] for _, cs, _ in model.cubes for p in cs]
    ys = [p[1] for _, cs, _ in model.cubes for p in cs]
    blocks, water = world.entries()
    return {'header': 1, 'entity': animal.checks.get('entity', f'antcolonies:{animal.name}'), 'id': 0,
            'uuid': 'offline', 'name': label, 'width': round((max(xs) - min(xs)) * px, 4),
            'height': round((max(ys) - min(ys)) * px, 4), 'scale': round(px / animal.checks.get('px', 1 / 16), 4),
            'offline': 1,
            'bones': bones, 'blocks': blocks, 'water': water}


def write(folder, animal, label, world, moments, px):
    """The recording: moments [(k, pose, lift, travel, clip name, clip ticks, speed a tick, marks)], k the moment's
    number (SUB a tick), the travel and speed in model px, marks the points to show red (model px, the clip's own
    frame), drawn a model pixel px blocks. Returns its path."""
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=True)
    for old in folder.glob('body*.jsonl.gz'):
        old.unlink()
    path = folder / f'body_{label}.jsonl.gz'
    w = frame(YAW, px)
    fwd = (-math.sin(math.radians(YAW)), math.cos(math.radians(YAW)))
    x0, y0, z0 = world.start
    model = animal.model
    names = [b['name'] for b in model.bones]
    with gzip.open(path, 'wt', encoding='utf-8') as f:
        f.write(json.dumps(header(animal, label, world, px), separators=(',', ':')) + '\n')
        for k, pose, lift, travel, clip, ticks, speed, marks in moments:
            xf = model.transforms(pose)
            tick, part = divmod(k, SUB)
            m = {'t': tick, 'pt': round(part / SUB, 4),
                 'pos': [round(x0 + fwd[0] * travel * px, 6), round(y0, 6), round(z0 + fwd[1] * travel * px, 6)],
                 'yaw': YAW, 'pitch': 0, 'v': [round(fwd[0] * speed * px, 5), 0, round(fwd[1] * speed * px, 5)],
                 'ground': 0 if world.in_water else 1, 'water': 1 if world.in_water else 0, 'fluid': 0,
                 'clips': {'body': f'{clip} running'}, 'play': {'body': [1.0, round(ticks, 4)]},
                 'ft': tick, 'ct': tick, 'b': [bone_rows(model, xf, n, lift, w) for n in names]}
            if marks:
                m['marks'] = [[round(v, 4) for v in mul(w, p)] for p in marks[:MARKS_MAX]]
            f.write(json.dumps(m, separators=(',', ':')) + '\n')
    return path


def check(path, animal, moments, px):
    """The recording read back against the checker's own posing: the farthest any box corner lies from where
    clip_check puts it (model px). Returns (worst, the moment)."""
    head, rows, _, _, trouble = recording.read(path)
    if trouble:
        raise SystemExit(f'the recording {path} reads badly: {trouble}')
    model = animal.model
    to_bone = model.plan.to_bone if model.plan else {}
    index = {b['name']: i for i, b in enumerate(head['bones'])}
    w = frame(YAW, px)
    worst = (0.0, 0)
    for (k, pose, lift, travel, *_), row in zip(moments, rows):
        posed = model.posed(pose)
        seen = {}
        for bn, corners, _ in posed:
            i = index[to_bone.get(bn, bn)]
            n = seen.get(i, 0)
            seen[i] = n + 1
            cube = head['bones'][i]['cubes'][n]
            m = row['b'][i]
            for c in range(8):
                kk = ((1 - (c & 1)) << 2) | (c & 2) | ((c >> 2) & 1)
                x, y, z = cube[3 * c:3 * c + 3]
                got = (m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7],
                       m[8] * x + m[9] * y + m[10] * z + m[11])
                p = corners[kk]
                want = mul(w, (p[0], p[1] + lift, p[2]))
                d = math.dist(got, want) / px
                if d > worst[0]:
                    worst = (d, k)
    return worst


# --- the videos ---------------------------------------------------------------------------------------------------
def out_paths(animal, name):
    size = '' if animal.size == 1.0 else f'_size{animal.size:g}'
    folder = ROOT / 'run-film' / 'clip_video' / f'{animal.name}{size}' / name
    video = ROOT / 'references' / 'anim_lab' / f'{animal.name}{size}' / 'video' / f'vid_{name}.mp4'
    return folder, video


def clip_moments(cc, animal, pl, plays):
    """One clip as moments: a loop plays `plays` times, a clip played once plays once and holds its end."""
    speed = cc.ground_speed(animal, pl.name, pl.clip) if not pl.clip.path else None
    v = speed or 0.0                                   # model px a second
    ticks = round(pl.length * 20 * (plays if pl.clip.loop else 1)) + (0 if pl.clip.loop else HOLD_TICKS)
    out = []
    for k in range(ticks * SUB + 1):
        t = k / SUB / 20.0
        tc = (t % pl.length) if pl.clip.loop and pl.length > 0 else min(t, pl.length)
        pose, lift = pl.pose(tc), pl.rise(tc)
        out.append((k, pose, lift, v * t, pl.name, tc * 20.0, v / 20.0, marks(cc, animal, pl, pose, lift)))
    return out, speed


def marks(cc, animal, pl, pose, lift):
    """The parts inside a block at this moment, as the checker's strips mark them red (clip_check.inside_points)."""
    posed = animal.model.posed(pose)
    if lift:
        posed = [(b, [(q[0], q[1] + lift, q[2]) for q in cs], c) for b, cs, c in posed]
    return cc.inside_points(animal, pl, posed)


def switch_moments(cc, animal, a, b, ticks, phase):
    """A clip change as moments: clip a for up to LEAD before it is left (at the moment the check judged), the
    blend, then clip b for up to LEAD; the ground's speed eased from a's to b's over the blend."""
    la, lb = (float(animal.data[n].get('animation_length', 1.0)) for n in (a, b))
    lead = min(LEAD, la)
    bl = cc.Blend(animal, a, b, ticks, phase, lead)
    lead = min(lead, bl.ta) if not bl.ca.loop else lead
    va, vb = ((cc.ground_speed(animal, n, c) or 0.0) for n, c in ((a, bl.ca), (b, bl.cb)))   # model px a second
    after = min(LEAD, lb) if bl.cb.loop else lb + HOLD_TICKS / 20.0
    k0, k1 = -round(lead * 20 * SUB), round((ticks + after * 20) * SUB)
    flat = SimpleNamespace(scene=flat_scene(cc, animal), kind='land')      # (a change is shown on flat ground)
    out, travel, prev = [], 0.0, None
    for k in range(k0, k1 + 1):
        kt = k / SUB                                  # ticks after clip a is left
        f = 0.0 if kt <= 0 else min(1.0, kt / ticks)
        v = va + (vb - va) * f
        if prev is not None:
            travel += v / 20.0 / SUB
        prev = v
        clip, ct = (a, (bl.ta + kt / 20.0) * 20.0) if kt <= 0 else (b, max(0.0, kt - ticks))
        if kt <= 0 and bl.ca.loop:
            ct = ct % (bl.ca.length * 20.0)
        pose = bl.at(kt)
        out.append((k - k0, pose, 0.0, travel, clip, ct, v / 20.0, marks(cc, animal, flat, pose, 0.0)))
    return out, (va, vb), flat.scene


def flat_scene(cc, animal):
    return cc.Scene(lab.Clip('flat', {'animation_length': 1}), 'land', animal)


def make(animal, name, label, sc, moments, cams, slow, fixed, no_paths, title, cc_kind):
    import replay_video
    # drawn at the size its scene is made for: a block is sc.block model px (a step or bank made for a small monitor
    # is a block at its size, as the game draws it; a young one's raised to a block at its size; else the plan's)
    px = 1.0 / sc.block
    travel = max(m[3] for m in moments)
    world = World(animal, sc, travel, cc_kind, px)
    folder, video = out_paths(animal, name)
    path = write(folder, animal, label, world, moments, px)
    worst, at = check(path, animal, moments, px)
    words = (f'    the recording against the checker\'s posing: every corner within {worst:.4f} px'
             + ('' if worst <= CHECK_PX else f' (over {CHECK_PX} at moment {at}: FAILED)'))
    print(words)
    if worst > CHECK_PX:
        raise SystemExit('the offline recording does not hold the checker\'s pose; no video made')
    out, _ = replay_video.make(folder, label, end=moments[-1][0] // SUB, cams=cams, slow=slow, fixed=fixed,
                               no_paths=no_paths, out=video, tick_word='clip tick', title=title)
    return out


def clip_video(cc, animal, name, cams, slow, plays, fixed, no_paths):
    pl = cc.Played(animal, name)
    moments, speed = clip_moments(cc, animal, pl, plays)
    ground = (f'walking at {speed:.2f} px/s ({speed / pl.scene.block:.3f} blocks/s) over still ground' if speed
              else 'carried by its own path' if pl.clip.path else 'standing')
    print(f'  video of {name}: {pl.scene.kind} scene, {ground}, {len(moments)} moments')
    if not speed and not pl.clip.path and animal.matches(name, 'walk_clips'):
        print(f'    (a walk, the plan\'s walk_clips, with no ground speed: neither checks.walks nor a stride note gives '
              f'one, so it walks on the spot and its planted feet show as sliding at its own pace)')
    return make(animal, f'{animal.model_name}_{name}', name, pl.scene, moments, cams, slow, fixed, no_paths,
                f'{animal.model_name} {name} (offline)', animal.kind(name))


def switch_video(cc, animal, a, b, ticks, phase, cams, slow, fixed, no_paths):
    moments, (va, vb), sc = switch_moments(cc, animal, a, b, ticks, phase)
    print(f'  video of {a} -> {b}: {ticks}-tick blend' + (f', {a} left at {phase:.2f} s' if phase is not None else '')
          + f', the ground {va * animal.px:.3f} to {vb * animal.px:.3f} blocks/s, {len(moments)} moments')
    return make(animal, f'sw_{a}_{b}', f'{a}-{b}', sc, moments, cams, slow, fixed, no_paths,
                f'{animal.model_name} {a} -> {b} (offline)', 'land')
