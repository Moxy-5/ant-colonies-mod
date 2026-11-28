"""A clip off the game's files as a video, with no game running (the Motion Studio, phase 3 part B step 2, idea 61,
2026-11-28; docs/plans/motion-studio-phase3.md). Made through tools/clip_check.py:

    python tools/clip_check.py <animal> <clips> --video [--cams side,front,above] [--slow 4] [--plays 2] [--fixed]
                               [--no-paths] [--size s] [--model <young model>]
    python tools/clip_check.py <animal> [clips] --switches --video ...      each clip change, a's end into b
    python tools/clip_check.py <holder> [holds] --pair <prey> [--spots a,b] --video ...   a pair (2026-11-28)
    python tools/clip_check.py <prey> --scene <pose> --video ...                          everyone round a prey
    e.g. python tools/clip_check.py lioness walk trot --video
         python tools/clip_check.py lioness walk --switches --video --cams side,three-quarter
         python tools/clip_check.py lioness hold_sink --pair buffalo --video
         python tools/clip_check.py buffalo --scene carcass --video

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
    references/anim_lab/<holder>/video/vid_pair_<hold>_<prey><size>_<clip>.mp4, <prey>/video/vid_scene_<pose>.mp4
    run-film/clip_video/<animal>[_size<s>]/<model>_<clip>/body_<clip>.jsonl.gz   (the recording; a pair's or a
    scene's one file an animal, in run-film/clip_video/<holder or prey>/<pair or scene>/)

Two or more animals (a pair, a scene; the end of this file): posed and placed by the checker's own Pair, every
animal a recording of its own in one folder, so the replay page and the video show them together; red where the parts
of one are in another, by the pair strips' own rule (clip_check.in_each_other).

Timing (my picks): three moments a game tick (the game's drawing at 60 frames a second, so 1x shows a new pose every
frame); a loop plays --plays times (default 2), a clip played once plays once and holds its end HOLD_TICKS; a clip
change shows up to LEAD seconds of clip a (the moment of it the check judged, R12-11), the blend, then up to LEAD of
clip b.
"""
import gzip
import json
import math
from collections import namedtuple
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


def bone_rows(model, xf, bone, to):
    """The bone's 3x4 matrix as the recorder writes it (rows; a point in the recording's bone frame, the model's
    file frame mirrored in x and over 16, as GeckoLib bakes it), from the lab's own steps (lab.place, an affine move)
    and `to`, the affine move of a posed model point (model px) to its place from where the animal is drawn (blocks)."""
    steps = model._steps(bone, xf)
    o = to(lab.place(steps, (0.0, 0.0, 0.0)))
    cols = []
    for e, sign in (((1.0, 0.0, 0.0), -16.0), ((0.0, 1.0, 0.0), 16.0), ((0.0, 0.0, 1.0), 16.0)):
        p = to(lab.place(steps, e))
        cols.append(tuple((p[i] - o[i]) * sign for i in range(3)))
    return [round(v, 6) for r in range(3) for v in (cols[0][r], cols[1][r], cols[2][r], o[r])]


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


class Flat:
    """Flat ground under a pair or a scene (the top of the blocks at y 0), over x0..x1, z0..z1 (blocks) and MARGIN round."""

    def __init__(self, x0, x1, z0, z1):
        self.blocks = {(x, -1, z): [0, 0, 0, 1, 1, 1]
                       for x in range(math.floor(x0) - MARGIN, math.ceil(x1) + MARGIN)
                       for z in range(math.floor(z0) - MARGIN, math.ceil(z1) + MARGIN)}
        self.water, self.in_water = {}, False

    def entries(self):
        return [[x, y, z, box] for (x, y, z), box in sorted(self.blocks.items())], []


# --- the recording ------------------------------------------------------------------------------------------------
# One moment of one animal: k its number (SUB a tick), its pose, `to` (a posed model point, model px, to its place
# from pos, blocks), pos (blocks, where it is drawn from), its yaw, v (blocks a tick), its clip, how far in (ticks),
# marks (the points to show red, blocks from pos)
Moment = namedtuple('Moment', 'k pose to pos yaw v clip ticks marks')


def scale_of(to):
    """A model pixel in blocks under this move."""
    return math.dist(to((0.0, 0.0, 0.0)), to((1.0, 0.0, 0.0)))


def header(animal, label, world, px, number=0):
    """The first line, as client/BodyRecorder writes it: the bones by the model's own names, their boxes; the blocks
    and water of the world (None: none, another animal's recording has them)."""
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
    blocks, water = world.entries() if world else ([], [])
    return {'header': 1, 'entity': animal.checks.get('entity', f'antcolonies:{animal.name}'), 'id': number,
            'uuid': f'offline-{number}', 'name': label, 'width': round((max(xs) - min(xs)) * px, 4),
            'height': round((max(ys) - min(ys)) * px, 4), 'scale': round(px / animal.checks.get('px', 1 / 16), 4),
            'offline': 1,
            'bones': bones, 'blocks': blocks, 'water': water}


def write(folder, world, actors):
    """The recordings, one an animal: actors [(animal, label, moments)], moments [Moment]; the first carries the
    world's blocks and water. Returns their paths."""
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=True)
    for old in folder.glob('body*.jsonl.gz'):
        old.unlink()
    paths = []
    for number, (animal, label, moments) in enumerate(actors):
        path = folder / f'body_{label}.jsonl.gz'
        model = animal.model
        names = [b['name'] for b in model.bones]
        px = scale_of(moments[0].to)
        with gzip.open(path, 'wt', encoding='utf-8') as f:
            f.write(json.dumps(header(animal, label, world if number == 0 else None, px, number),
                               separators=(',', ':')) + '\n')
            for mo in moments:
                xf = model.transforms(mo.pose)
                tick, part = divmod(mo.k, SUB)
                m = {'t': tick, 'pt': round(part / SUB, 4), 'pos': [round(v, 6) for v in mo.pos],
                     'yaw': round(mo.yaw, 4), 'pitch': 0, 'v': [round(v, 5) for v in mo.v],
                     'ground': 0 if world.in_water else 1, 'water': 1 if world.in_water else 0, 'fluid': 0,
                     'clips': {'body': f'{mo.clip} running'}, 'play': {'body': [1.0, round(mo.ticks, 4)]},
                     'ft': tick, 'ct': tick, 'b': [bone_rows(model, xf, n, mo.to) for n in names]}
                if mo.marks:
                    m['marks'] = [[round(v, 4) for v in p] for p in mo.marks[:MARKS_MAX]]
                f.write(json.dumps(m, separators=(',', ':')) + '\n')
        paths.append(path)
    return paths


def check(path, animal, moments, unit, wants):
    """The recording read back against the checker's own posing: the farthest any box corner lies from where
    clip_check puts it, in units (blocks) of a model pixel or a sixteenth of a block. wants {moment k: each posed box's
    corners as the checker places them, in model.posed's order (blocks from the moment's pos)}; each corner compared
    where it is in the world (the recorded place and the bone's matrix), so an animal drawn from the wrong place shows.
    Returns (worst, the moment)."""
    head, rows, _, _, trouble = recording.read(path)
    if trouble:
        raise SystemExit(f'the recording {path} reads badly: {trouble}')
    model = animal.model
    to_bone = model.plan.to_bone if model.plan else {}
    index = {b['name']: i for i, b in enumerate(head['bones'])}
    worst = (0.0, 0)
    for mo, row in zip(moments, rows):
        if mo.k not in wants:
            continue
        seen, at = {}, row['pos']
        for (bn, _, _), want in zip(model.posed(mo.pose), wants[mo.k]):
            i = index[to_bone.get(bn, bn)]
            n = seen.get(i, 0)
            seen[i] = n + 1
            cube = head['bones'][i]['cubes'][n]
            m = row['b'][i]
            for c in range(8):
                kk = ((1 - (c & 1)) << 2) | (c & 2) | ((c >> 2) & 1)
                x, y, z = cube[3 * c:3 * c + 3]
                got = (m[0] * x + m[1] * y + m[2] * z + m[3] + at[0], m[4] * x + m[5] * y + m[6] * z + m[7] + at[1],
                       m[8] * x + m[9] * y + m[10] * z + m[11] + at[2])
                d = math.dist(got, [w + o for w, o in zip(want[kk], mo.pos)]) / unit
                if d > worst[0]:
                    worst = (d, mo.k)
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


def held_to(worst, at, unit_word):
    """The check's line; no video when it failed."""
    print(f'    the recording against the checker\'s posing: every corner within {worst:.4f} {unit_word}'
          + ('' if worst <= CHECK_PX else f' (over {CHECK_PX} at moment {at}: FAILED)'))
    if worst > CHECK_PX:
        raise SystemExit('the offline recording does not hold the checker\'s pose; no video made')


def placed(moments, world, px):
    """One clip's moments [(k, pose, lift, travel, clip, ticks, speed a tick, marks)] (model px, the clip's own frame)
    set in the world, facing YAW from world.start along its travel, a model pixel px blocks: [Moment]."""
    w = frame(YAW, px)
    fwd = (-math.sin(math.radians(YAW)), math.cos(math.radians(YAW)))
    x0, y0, z0 = world.start
    out = []
    for k, pose, lift, travel, clip, ticks, speed, mk in moments:
        out.append(Moment(k, pose, (lambda lift: lambda p: mul(w, (p[0], p[1] + lift, p[2])))(lift),
                          (x0 + fwd[0] * travel * px, y0, z0 + fwd[1] * travel * px), YAW,
                          (fwd[0] * speed * px, 0.0, fwd[1] * speed * px), clip, ticks, [mul(w, q) for q in mk]))
    return out


def own_wants(animal, moments):
    """The checker's posing of one clip (model.posed, lifted as it flies) at every moment, for check."""
    return {mo.k: [[mo.to(p) for p in cs] for _, cs, _ in animal.model.posed(mo.pose)] for mo in moments}


def make(animal, name, label, sc, moments, cams, slow, fixed, no_paths, title, cc_kind, side=None):
    import replay_video
    # drawn at the size its scene is made for: a block is sc.block model px (a step or bank made for a small monitor
    # is a block at its size, as the game draws it; a young one's raised to a block at its size; else the plan's)
    px = 1.0 / sc.block
    world = World(animal, sc, max(m[3] for m in moments), cc_kind, px)
    ms = placed(moments, world, px)
    folder, video = out_paths(animal, name)
    path, = write(folder, world, [(animal, label, ms)])
    held_to(*check(path, animal, ms, px, own_wants(animal, ms)), 'px')
    out, ok = replay_video.make(folder, label, end=moments[-1][0] // SUB, cams=cams, slow=slow, fixed=fixed,
                                no_paths=no_paths, out=video, tick_word='clip tick', title=title, side=side)
    if not ok and side:
        raise SystemExit(f'{out}: the footage is not where its stride says it should be (above)')
    return out


def clip_video(cc, animal, name, cams, slow, plays, fixed, no_paths, ref=None):
    """One clip's video; ref = (footage file, stride start s, stride end s): the real animal beside it (Footage)."""
    pl = cc.Played(animal, name)
    if ref:
        if not pl.clip.loop:
            raise SystemExit(f'--ref: {name} plays once; real footage is matched to a looping clip (a walk, a trot)')
        import tempfile
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as work:
            side = Footage(ref[0], ref[1], ref[2], name, pl.length, Path(work))
            moments, speed = clip_moments(cc, animal, pl, plays)
            print(f'  video of {name} beside {side.words()}, {len(moments)} moments')
            return make(animal, f'{animal.model_name}_{name}_ref', name, pl.scene, moments, cams, slow, fixed,
                        no_paths, f'{animal.model_name} {name} (offline) beside real footage', animal.kind(name), side)
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


# --- real footage beside the clip (phase 3 part B step 3, idea 62, 2026-11-28) -------------------------------------
# python tools/clip_check.py <animal> <clip> --video --ref <video> --cycle <start>,<end>: a video of the real animal the
# user gives (Claude downloads nothing without asking), one stride of it marked by its start and end in seconds, set
# beside the clip's cameras in one mp4. The marked stride is stretched to the clip's own loop (cycle-matched, Neon's
# idea 62): at every moment the footage shows the point of its stride the clip is at (a quarter of the way through the
# clip's loop, a quarter of the way through the marked stride), in the real-speed pass and the slowed one alike, so the
# real cadence, weight shift and spine flow sit against the clip frame by frame. The bar says how much the stride was
# stretched (the footage played at x0.80 means the clip's stride is a quarter longer than the real one's). Its own
# check, every time (Footage.check): frames read back out of the mp4 show the footage frame of their stride point (and
# a frame half a stride off matches worse, or the timing went unproven by them).
REF_FPS = 60            # the marked stride decoded at this many frames a second (my pick: the game draws 60)
REF_MATCH = 8.0         # the footage in a frame of the mp4 against the frame it should show: mean difference under this
REF_MARGIN = 1.0        # ...and the frame half a stride off must match worse by this much (of 255)


def ffprobe_seconds(path):
    import subprocess
    done = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', str(path)],
                          capture_output=True, text=True)
    try:
        return float(done.stdout.strip().splitlines()[0])
    except (ValueError, IndexError):
        raise SystemExit(f'--ref: ffprobe cannot read {path}: {done.stderr.strip()[-300:]}')


class Footage:
    """The marked stride of a real animal's video, decoded once, a frame for any moment of a looping clip."""

    def __init__(self, path, start, end, clip, clip_len, work, tile=None):
        import shutil
        import subprocess
        from PIL import Image
        import replay_video
        if not shutil.which('ffmpeg') or not shutil.which('ffprobe'):
            raise SystemExit('--ref needs ffmpeg and ffprobe')
        self.path = Path(path)
        if not self.path.is_file():
            raise SystemExit(f'--ref: no file {path}')
        if not (0 <= start < end):
            raise SystemExit(f'--cycle {start:g},{end:g}: the stride must start at 0 s or later and end after it starts')
        length = ffprobe_seconds(self.path)
        if end > length + 1e-3:
            raise SystemExit(f'--cycle ends at {end:g} s but {self.path.name} is {length:.2f} s long')
        if clip_len <= 0:
            raise SystemExit(f'--ref: {clip} has no length')
        self.start, self.end, self.clip, self.clip_len = start, end, clip, clip_len
        self.tile = tile or replay_video.TILE
        w, h = self.tile
        frames = Path(work) / 'footage'
        frames.mkdir(parents=True, exist_ok=True)
        done = subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', f'{start:.4f}', '-i', str(self.path),
                               '-t', f'{end - start:.4f}', '-an', '-vf',
                               f'fps={REF_FPS},scale={w}:{h}:force_original_aspect_ratio=decrease,'
                               f'pad={w}:{h}:(ow-iw)/2:(oh-ih)/2:color=0x1c1a18', '-q:v', '2',
                               str(frames / 'f_%05d.jpg')], capture_output=True, text=True)
        self.files = sorted(frames.glob('f_*.jpg'))
        if done.returncode or not self.files:
            raise SystemExit(f'--ref: ffmpeg could not read the stride out of {self.path.name}: '
                             f'{done.stderr.strip()[-400:]}')
        self.Image = Image
        self.cache = {}

    def words(self):
        return (f'{self.path.name}, its stride {self.start:g} to {self.end:g} s ({len(self.files)} frames) matched to '
                f"{self.clip}'s {self.clip_len:g} s (the footage at x{self.speed():.2f})")

    def speed(self):
        """How fast the footage plays against real time in the clip's real-speed pass."""
        return (self.end - self.start) / self.clip_len

    def phase(self, row):
        """How far through its loop the row's clip is (0 to 1), or None: not drawn, or another clip."""
        import replay_strips
        c = replay_strips.clip_of(row) if row.get('drawn') else None
        if not c or c.get('name') != self.clip or c.get('ticks') is None:
            return None
        return ((c['ticks'] / 20.0) % self.clip_len) / self.clip_len

    def index(self, phase):
        return min(len(self.files) - 1, int(phase * len(self.files)))

    def seconds(self, phase):
        """Where in the real video the footage shown at this phase is."""
        return self.start + self.index(phase) / REF_FPS

    def picture(self, i):
        if i not in self.cache:
            self.cache[i] = self.Image.open(self.files[i]).convert('RGB')
        return self.cache[i].copy()

    def frame(self, row, phase=None):
        """The footage tile shown with this row: the frame of its stride point, labelled; dark when none."""
        from PIL import ImageDraw
        import replay_strips
        import replay_video
        p = self.phase(row) if phase is None else phase
        if p is None:
            shot = self.Image.new('RGB', self.tile, replay_video.DARK)
        else:
            shot = self.picture(self.index(p))
        d = ImageDraw.Draw(shot)
        top, small = replay_strips.font(16), replay_strips.font(14)
        name = 'real footage'
        d.rounded_rectangle([6, 6, 16 + d.textlength(name, font=top), 30], 5, fill=replay_video.DARK)
        d.text((11, 9), name, fill=replay_video.INK, font=top)
        words = (f'{self.seconds(p):.2f} s of {self.path.name}  (stride {self.start:g}-{self.end:g} s, x{self.speed():.2f})'
                 if p is not None else 'not this clip here')
        y = self.tile[1] - 30
        d.rounded_rectangle([6, y, 16 + d.textlength(words, font=small), y + 22], 5, fill=replay_video.DARK)
        d.text((11, y + 3), words, fill=replay_video.INK, font=small)
        return shot

    def check(self, grab, rows, starts, lengths, box):
        """The mp4's own frames (grab(seconds, name) -> picture) show, in the footage's place (box), the frame of
        their stride point: three moments of the real-speed pass, each against its own footage frame and the frame
        half a stride off. True when it passed (printed either way)."""
        import replay_video
        drawn = [k for k, r in enumerate(rows) if self.phase(r) is not None]
        if len(drawn) < 3:
            print('footage check: under 3 moments of the clip to check')
            return False
        worst, unproven = 0.0, 0
        for n, k in enumerate(drawn[len(drawn) // 4::max(1, len(drawn) // 4)][:3]):
            got = grab(starts[k] + lengths[k] / 2, f'ref{n}').crop(box)
            p = self.phase(rows[k])
            d_own = replay_video.difference(got, self.frame(rows[k]))
            d_off = replay_video.difference(got, self.frame(rows[k], (p + 0.5) % 1.0))
            worst = max(worst, d_own)
            if d_own >= REF_MATCH:
                print(f'footage check: the frame at {starts[k]:.2f} s ({p:.2f} of the stride) is {d_own:.2f} of 255 off '
                      f'its footage frame (match under {REF_MATCH:g}): FAILED')
                return False
            if d_off < d_own + REF_MARGIN:
                unproven += 1
        print(f'footage check: 3 moments, each within {worst:.2f} of 255 of the footage frame of its stride point '
              f'(match under {REF_MATCH:g})' + (f'; {unproven} looked nearly the same half a stride off, so their timing '
                                                f'went unproven by them' if unproven else ', and worse half a stride off: passed'))
        return True


# --- two or more animals: a pair and a scene round a prey (2026-11-28) ---------------------------------------------
# Posed and placed by the checker's own Pair (tools/clip_check.py --pair, --scene: the spot tables, the holder's feet
# on the ground facing in along its spot's way out, the lions lying by a kill where savanna LionHunt lays them), in
# the prey's frame (a sixteenth of a block: x to its left, y up, z toward its tail); the prey drawn from P0 facing
# YAW. Red: the parts of each in another (clip_check.in_each_other, the pair strips' own marks; a holder's head on
# the hide is its bite). The cameras round the animal checked take in the prey and every holder (replay_video's
# fit_with; not the lions lying 5 blocks and more out, which made the carcass a speck: they show where they fall in
# view); every other animal see-through, as in every video (the user's call, 2026-11-27).
P0 = (0.5, 0.0, 0.5)    # where the prey is drawn from (blocks): the middle of a block, on the ground
W16 = frame(YAW, 1.0 / 16.0)       # the prey's frame (a sixteenth of a block) into the world's blocks


def yaw_of(d):
    """The yaw of a facing d (x, z in the prey's frame) in the world (forward (-sin yaw, cos yaw))."""
    fx, _, fz = mul(W16, (d[0], 0.0, d[1]))
    return math.degrees(math.atan2(-fx, fz))


class Actor:
    """One animal of a pair or a scene: at(tk) gives (pose, to the prey's frame, its feet (x, z), its facing (x, z),
    its clip, how far in (ticks)) at any moment, tk ticks in; posed(k) the checker's own placing at a whole tick k
    ([(bone, corners in the prey's frame, colour)]); head: None for the prey, else the bones whose overlap with the
    prey is its bite."""

    def __init__(self, animal, label, head, at, posed, framed=True):
        self.animal, self.label, self.head, self.at, self.posed = animal, label, head, at, posed
        self.framed = framed               # the cameras take it in


def prey_actor(cc, pair, clip, kind):
    held = pair.poses.get(clip)
    pl = cc.Played(pair.prey, held['clip'] if held else clip)

    def at(tk):
        # (as clip_check.Pair.prey_posed: a still pose at its clip's end, a loop round, a clip played once held)
        t = pl.length if held else ((tk / 20) % pl.length if kind == 'loop' else min(tk / 20, pl.length))
        return pl.pose(t), pair.prey_point, (0.0, 0.0), (0.0, -1.0), clip, t * 20.0
    return Actor(pair.prey, f'{pair.prey.name}_{clip}', None, at, lambda k: pair.prey_posed(clip, kind, k)[0])


def row_at(pair, clip, kind, tk):
    """The prey's spot table row tk ticks in, between two ticks the way the game draws a rider between them."""
    if kind == 'loop' or clip in pair.poses:
        return pair.prey_place(clip, kind, 0)
    i = math.floor(tk)
    a, b = pair.prey_place(clip, kind, i), pair.prey_place(clip, kind, i + 1)
    f = tk - i
    return [x + (y - x) * f for x, y in zip(a, b)]


def holder_actor(cc, pair, clip, kind, spot, label):
    def at(tk):
        fr = pair.holder_frame(spot, row_at(pair, clip, kind, tk))
        ht = pair.holder_tick(tk)
        return (pair.h_play.pose(ht / 20), (lambda p: pair.holder_point(fr, p)), fr[0], fr[1], pair.spec['hold'], ht)

    def posed(k):
        return pair.holder_posed(pair.holder_frame(spot, pair.prey_place(clip, kind, k)), k)[0]
    return Actor(pair.holder, label, pair.head, at, posed)


def lying_actor(cc, pr, rank, wx, wz, label):
    """A full lion lying by the kill (clip_check.run_scene's third part, the carcass not turned): its rest clip looping."""
    pl = cc.Played(pr.holder, 'rest')
    x, z = wx * 16, -wz * 16
    L = math.hypot(x, z) or 1e-9
    d = (-x / L, -z / L)
    left = (-d[1], d[0])

    def pose_at(tk):
        return pl.pose((tk / 20) % pl.length)

    def at(tk):
        return (pose_at(tk), (lambda p: cc.place_point((x, z), d, left, pr.hs, pr.da_h, p)), (x, z), d, 'rest',
                (tk % (pl.length * 20)))
    return Actor(pr.holder, label, frozenset(), at,
                 lambda k: cc.place_points(pr.holder.model.posed(pose_at(k)), pr.hs, pr.da_h, (x, z), d, left), False)


def group_recording(cc, actors, ticks, folder):
    """Every actor through `ticks` ticks, SUB moments a tick, written (the first carries the ground): (the recordings'
    paths, each one's moments, each one's checker posing at every whole tick, for check)."""
    per = [[] for _ in actors]
    wants = [{} for _ in actors]
    lo, hi = [math.inf, math.inf], [-math.inf, -math.inf]
    prev = [None] * len(actors)
    for k in range(ticks * SUB + 1):
        tk = k / SUB
        states = [a.at(tk) for a in actors]
        placed = [[(bn, [st[1](p) for p in cs], col) for bn, cs, col in a.animal.model.posed(st[0])]
                  for a, st in zip(actors, states)]
        red = cc.in_each_other([(pl, a.head) for a, pl in zip(actors, placed)])
        for i, (a, (pose, to, feet, d, clip, ct)) in enumerate(zip(actors, states)):
            f3 = (feet[0], 0.0, feet[1])
            pos = tuple(P0[j] + v for j, v in enumerate(mul(W16, f3)))

            def rel(q, f3=f3):
                return mul(W16, (q[0] - f3[0], q[1], q[2] - f3[2]))
            v = (0.0, 0.0, 0.0) if prev[i] is None else tuple((pos[j] - prev[i][j]) * SUB for j in range(3))
            prev[i] = pos
            per[i].append(Moment(k, pose, (lambda p, to=to, rel=rel: rel(to(p))), pos, yaw_of(d), v, clip, ct,
                                 [rel(q) for q in red[i]]))
            if k % SUB == 0:
                wants[i][k] = [[rel(q) for q in cs] for _, cs, _ in a.posed(k // SUB)]
                for _, cs, _ in placed[i]:
                    for q in cs:
                        w = mul(W16, q)
                        lo = [min(lo[0], w[0] + P0[0]), min(lo[1], w[2] + P0[2])]
                        hi = [max(hi[0], w[0] + P0[0]), max(hi[1], w[2] + P0[2])]
    world = Flat(lo[0], hi[0], lo[1], hi[1])
    return write(folder, world, [(a.animal, a.label, ms) for a, ms in zip(actors, per)]), per, wants


def group_video(cc, actors, ticks, folder, video, title, cams, slow, fixed, no_paths, **_):
    """The recordings, checked against the checker's own placing, then drawn round the first actor."""
    import replay_video
    paths, per, wants = group_recording(cc, actors, ticks, folder)
    worst = max(((*check(p, a.animal, ms, 1.0 / 16.0, ws),) for p, a, ms, ws in zip(paths, actors, per, wants)),
                key=lambda w: w[0])
    held_to(*worst, 'sixteenths of a block')
    out, _ = replay_video.make(folder, actors[0].label, end=ticks, cams=cams, slow=slow, fixed=fixed,
                               no_paths=no_paths, out=video, tick_word='tick', title=title,
                               fit_with=[a.label for a in actors if a.framed])
    return out


def pair_video(pair, clip, kind, spots, cc, **filmed):
    """A pair (clip_check.py <holder> --pair <prey> --video): the prey's clip with every spot named held at once, as
    long as the pair check judges it (a clip played once, then HOLD_TICKS; a loop or a still pose, twice the holder's
    clip and its blend, or the prey's loop if longer). The cameras round the first spot's holder."""
    n = (round(cc.Played(pair.prey, clip).length * 20) + HOLD_TICKS if kind == 'once' and clip not in pair.poses
         else max(2 * (pair.h_ticks + pair.h_blend), pair.loop_ticks(clip, kind)))
    actors = [holder_actor(cc, pair, clip, kind, s, f'{pair.holder.name}_{s}') for s in spots] + [
        prey_actor(cc, pair, clip, kind)]
    every = spots == [s for s in pair.table['names'] if s in pair.spots]
    # (named by the prey's size: one hold is paired with a grown buffalo and a calf)
    name = (f'pair_{pair.spec["hold"]}_{pair.prey.name}' + ('' if pair.ps == 1.0 else f'{pair.ps:g}') + f'_{clip}'
            + ('' if every else '_' + '+'.join(spots)))
    folder = ROOT / 'run-film' / 'clip_video' / pair.holder.name / name
    video = ROOT / 'references' / 'anim_lab' / pair.holder.name / 'video' / f'vid_{name}.mp4'
    print(f'  video of {pair.holder.name} {pair.spec["hold"]} at {", ".join(spots)} of {pair.prey.name} {clip} '
          f'({kind}, sizes {pair.hs:g} and {pair.ps:g}): {n} ticks, {n * SUB + 1} moments')
    return group_video(cc, actors, n, folder, video,
                       f'{pair.holder.name} {pair.spec["hold"]} on {pair.prey.name} {clip} (offline)', **filmed)


def scene_video(prey, pose, kind, size, holders, allowed, spots_all, cc, **filmed):
    """Everyone round a prey (clip_check.py <prey> --scene <pose> --video): each spot taken by one of the forms its
    pairs allow there (my pick: the forms in turn, spot by spot; the check tries every form at every spot), and round a
    carcass the full lions lying where the game lays them (the forms in turn, rank by rank), twice the longest
    holder's clip. The cameras round the prey."""
    any_pair = next(iter(holders.values()))
    actors = [prey_actor(cc, any_pair, pose, kind)]
    for i, s in enumerate(spots_all):
        h = allowed[s][i % len(allowed[s])]
        pr = holders[h]
        actors.append(holder_actor(cc, pr, pose, kind, s, f'{pr.holder.name}_{s}'))
    if pose == 'carcass':
        lie = [pr for pr in holders.values() if 'rest' in pr.holder.data]
        forms = list({pr.holder.name: pr for pr in lie}.values())
        for rank, wx, wz in (cc.bystanders() if forms else ()):
            pr = forms[rank % len(forms)]
            actors.append(lying_actor(cc, pr, rank, wx, wz, f'{pr.holder.name}_lying{rank}'))
    n = max(2 * (pr.h_ticks + pr.h_blend) for pr in holders.values())
    name = f'scene_{pose}' + ('' if size == 1.0 else f'_size{size:g}')
    folder = ROOT / 'run-film' / 'clip_video' / prey.name / name
    video = ROOT / 'references' / 'anim_lab' / prey.name / 'video' / f'vid_{name}.mp4'
    print(f'  video of the {prey.name} {pose} scene (size {size:g}): {len(actors) - 1} round it, {n} ticks, '
          f'{n * SUB + 1} moments')
    return group_video(cc, actors, n, folder, video, f'{prey.name} {pose}, everyone round it (offline)', **filmed)
