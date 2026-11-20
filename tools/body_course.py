"""
The measuring course (the Animal Maker Engine, phase 1 step 1, 2026-11-19; docs/ANIMAL-MAKER-ENGINE.md).

The same course for every animal: the in-game camera sends the animal over a flat walk, a step up, a step down, a
stair slope, into water from a level bank and from a high one, a dash into water, a climb out and a turn, and a real
scare into water with its own mind; while it goes, the game writes down every bone where it drew it
(client/BodyRecorder.java, "record on" in a shot list). This tool judges those recordings; the game decides nothing.

The checks (any body: they read the legs, feet and body chain from the animal's body plan, any number of legs):
  inside   a part drawn inside a block (its box corners in the blocks' collision boxes), deepest first
  seams    a gap opening between two neighbours of the body chain (head to tail): the corners of one that touch the
           other most of the time, how far they come away from it at worst
  bends    each chain joint's bend, at most and how fast it changes (a joint snapping round shows as a fast one)
  pops     a part jumping between two moments much further than it moves just before and after (and the whole
           animal jumping, apart from its body)
  feet     each foot's sole over the ground under it while the animal is on land: floating, sunk, and how far a foot
           slides while it stands planted, against how fast the body goes
  clips    a clip that plays for only a moment between two others (a flicker)

Run from the project folder:
  python tools/body_course.py films [animal ...]   (re)writes the course's shot lists, tools/film/course/course_<animal>_<scene>.film
  python tools/body_course.py run [animal ...]     films the course (./gradlew runFilm) and checks it; about a minute a scene
  python tools/body_course.py check [animal ...]   checks the recordings already made (run-film/course/course_*/)
A run moves its scenes' folders from run-film/screenshots/ (which the guard films clear) into run-film/course/. Each
animal gets run-film/course/course_<animal>.txt (every finding, worst first) and one picture a scene,
run-film/course/<scene>/graph.png: heights over time (the animal, its head, chest, hips and tail tip, and the
ground or water under it), the chain's bends, the feet over the ground, pops marked in red.

An animal joins the course with a line in ANIMALS (its id, its summon data, its walking and dashing speeds) and a body
plan with a family, a chain and legs (tools/body_plan.py).
"""
import gzip, json, math, os, statistics, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, '..'))
sys.path.insert(0, HERE)
import body_plan  # noqa: E402

COURSE = os.path.join(PROJECT, 'tools', 'film', 'course')
SHOTS = os.path.join(PROJECT, 'run-film', 'screenshots')
# the course's own folder: each run's scenes are moved here from the camera's screenshots (which other films clear)
KEPT = os.path.join(PROJECT, 'run-film', 'course')

# The animals on the course: id, extra summon data (its size fixed, so every run is the same animal), its own walking
# speed (as its goals send it) and its running one (its flight to water).
ANIMALS = {
    # bpt: blocks a tick it walks at speed 1.0, measured on the course (the monitor strolls at 0.8: 0.031 a tick)
    'monitor': dict(id='antcolonies:monitor', data='OwnSize:1.0f,HungryIn:99999,SunIn:99999', walk=0.8, dash=1.7, bpt=0.039),
    'crocodile': dict(id='antcolonies:crocodile', data='OwnSize:1.0f', walk=1.0, dash=1.9, bpt=0.059),
    # the young (animal life cycle step 4, 2026-11-20): a hatchling just out of the egg (0.07 of the grown size) and a
    # teen a day into its teens (0.43); the grown body plan (same bones); bpt by CrocodileEntity.sizePace
    'croc_hatchling': dict(id='antcolonies:crocodile', plan='crocodile', walk=1.0, dash=1.9, bpt=0.059 * (0.07 / 0.7) ** 0.5,
                           data='Life:{Sex:"female",Age:0,Grown:0b,OwnSize:1.0f}'),
    # the bullfrog (the hoppers, the polish's step 1, 2026-11-20): a male of size 1, never hungry on the course; it
    # hops, so its pace is hops and rests (about 0.7 blocks a hop, a hop and rest about 18 ticks; my measure to check)
    # (filmed at 8:00: at noon by water it digs in, at night males call)
    'bullfrog': dict(id='antcolonies:bullfrog', data='Male:1b,HungryIn:99999', walk=1.0, dash=1.6, bpt=0.035,
                     setup=['cmd time set 2000']),
    'croc_teen': dict(id='antcolonies:crocodile', plan='crocodile', walk=1.0, dash=1.9, bpt=0.059 * (0.43 / 0.7) ** 0.5,
                      data='Life:{Sex:"female",Age:168000,Grown:0b,OwnSize:1.0f}'),
}


def plan_of(animal):
    """The body plan's name for a course animal (its own name, or the grown one's for the young)."""
    return ANIMALS[animal].get('plan', animal)

# The flat world's ground: grass at y -61, so an animal stands at -60. The camera clears x and z -12..12 each scene.
G = -60
WATER_POOL = 'cmd fill 1 -63 -7 12 -61 7 minecraft:water'
SCENES = {
    # name: (what it tests, setup commands, start (x, y, z), the places it is sent one after the other, speed key);
    # it is given the time its own speed needs for each leg and a little more (ticks_for)
    'flat': ('a level walk (the gait, the feet, the back at rest)', [], (-6.5, G, 0.5), [(3, G, 0)], 'walk'),
    'step_up': ('a one-block step up', ['cmd fill 1 -60 -7 12 -60 7 minecraft:grass_block'], (-5.5, G, 0.5),
                [(7, G + 1, 0)], 'walk'),
    'step_down': ('a one-block step down', ['cmd fill -12 -60 -7 0 -60 7 minecraft:grass_block'], (-5.5, G + 1, 0.5),
                  [(7, G, 0)], 'walk'),
    'stairs': ('a slope of one-block steps every three blocks', [
        'cmd fill 0 -60 -7 12 -60 7 minecraft:grass_block', 'cmd fill 3 -59 -7 12 -59 7 minecraft:grass_block',
        'cmd fill 6 -58 -7 12 -58 7 minecraft:grass_block', 'cmd fill 9 -57 -7 12 -57 7 minecraft:grass_block'],
               (-3.5, G, 0.5), [(11, G + 4, 0)], 'walk'),
    'bank_level': ('into water from a bank level with it, walking', [WATER_POOL], (-4.5, G, 0.5), [(8, -61, 0)], 'walk'),
    'bank_high': ('into water from a bank two blocks above it', ['cmd fill -12 -60 -7 0 -59 7 minecraft:grass_block', WATER_POOL],
                  (-4.5, G + 2, 0.5), [(8, -61, 0)], 'walk'),
    'dash': ('running into water from a level bank', [WATER_POOL], (-4.5, G, 0.5), [(8, -61, 0)], 'dash'),
    'climb_out': ('swimming to a bank one block above the water and climbing out', [
        'cmd fill -12 -63 -7 0 -62 7 minecraft:water', 'cmd fill -12 -61 -7 0 -61 7 minecraft:air'], (-6.5, -62, 0.5),
        [(6, G, 0)], 'walk'),    # (the air: the grass layer left over the water made it a covered channel, 2026-11-19)
    'turn': ('a walk with two right-angle turns (the back bending sideways)', [], (-6.5, G, -4.5),
             [(2, G, -5), (2, G, 4), (-6, G, 4)], 'walk'),
}


def ticks_for(animal, speed, start, goal):
    """The ticks this animal needs to walk from start to goal at that speed (straight, a third more) and 2 s more."""
    a = ANIMALS[animal]
    return int(math.dist(start, goal) / (a['bpt'] * a[speed]) * 1.3) + 40


def scene_name(animal, scene):
    return f'course_{animal}_{scene}'


# --- the shot lists ---------------------------------------------------------------------------------------------------
def write_film(path, lines):
    with open(path, 'w', encoding='utf-8', newline=chr(10)) as f:
        f.write(chr(10).join(lines) + chr(10))


def write_films(animals):
    os.makedirs(COURSE, exist_ok=True)
    out = []
    for animal in animals:
        a = ANIMALS[animal]
        for scene, (about, setup, start, places, speed) in SCENES.items():
            x, y, z = start
            lines = [f'# The measuring course ({about}) for the {animal}: written by tools/body_course.py films, do not edit.',
                     'lens 50'] + a.get('setup', []) + setup + [
                f'walker {a["id"]} {x} {y} {z} -90 {a["data"]}',
                'follow 0 3.5 -6 0 25',                  # from the north, a little above: the side of it, walking east
                'cmd tick unfreeze',
                'record on 3']
            pictures = 0
            at = start
            for place in places:
                lines.append(f'go {place[0]} {place[1]} {place[2]} {a[speed]}')
                left = ticks_for(animal, speed, at, (place[0] + 0.5, place[1], place[2] + 0.5))
                at = (place[0] + 0.5, place[1], place[2] + 0.5)
                while left > 0:                         # a picture every 3 seconds, for the eye
                    step = min(60, left)
                    lines.append(f'wait {step}')
                    left -= step
                    lines.append(f'shot p{pictures:02d}')
                    pictures += 1
            lines.append('record off')
            path = os.path.join(COURSE, scene_name(animal, scene) + '.film')
            write_film(path, lines)
            out.append(path)
        # the real thing: its own mind, scared on a level bank, runs for the water
        lines = [f'# The measuring course (a real scare into water: its own mind, not sent) for the {animal}: written by '
                 'tools/body_course.py films, do not edit.', 'lens 50'] + a.get('setup', []) + [WATER_POOL, 'cmd tick unfreeze', 'wait 20',
                 f'cmd summon {a["id"]} -4.5 {G} 0.5 {{{a["data"]},PersistenceRequired:1b,Rotation:[-90f,0f],'
                 "CustomName:'\"film-walker\"'}",
                 'wait 30', 'follow 0 3.5 -6 0 25', 'record on 3',
                 f'cmd execute positioned -4.5 {G} 0.5 run damage @e[name=film-walker,limit=1,sort=nearest] 0.5 minecraft:cactus']
        for i in range(5):
            lines += ['wait 60', f'shot p{i:02d}']
        lines.append('record off')
        path = os.path.join(COURSE, scene_name(animal, 'scared') + '.film')
        open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
        out.append(path)
    return out


def scenes_of(animal):
    return list(SCENES) + ['scared']


# --- reading a recording ----------------------------------------------------------------------------------------------
class Recording:
    def __init__(self, path):
        with gzip.open(path, 'rt', encoding='utf-8') as f:
            lines = [json.loads(line) for line in f if line.strip()]
        self.header = lines[0]
        self.frames = lines[1:]
        self.names = [b['name'] for b in self.header['bones']]
        self.index = {n: i for i, n in enumerate(self.names)}
        self.parent = {b['name']: b.get('parent') for b in self.header['bones']}
        self.cubes = [[tuple(c[i:i + 3] for i in range(0, 24, 3)) for c in b['cubes']] for b in self.header['bones']]
        # each bone's middle (the mean of its boxes' corners) in its own frame
        self.center = []
        for cubes in self.cubes:
            pts = [p for c in cubes for p in c]
            self.center.append(tuple(sum(p[k] for p in pts) / len(pts) for k in range(3)) if pts else (0.0, 0.0, 0.0))
        self.blocks = {}
        for entry in self.header['blocks']:
            self.blocks[tuple(entry[:3])] = [tuple(b) for b in entry[3:]]
        self.water = {tuple(w[:3]): w[3] for w in self.header['water']}
        # one model pixel in blocks: the root bone's scale over 16
        first = next(f for f in self.frames if f['b'][0] != 0)
        m = first['b'][0]
        self.px = math.sqrt(m[0] ** 2 + m[4] ** 2 + m[8] ** 2) / 16.0 or 1 / 16.0

    def time(self, f):
        return f['t'] + f['pt']

    def descendants(self, name):
        """The bone and every bone hanging off it (a foot's toes and claws)."""
        out = [name]
        for n in self.names:
            p = self.parent.get(n)
            while p is not None:
                if p == name:
                    out.append(n)
                    break
                p = self.parent.get(p)
        return out

    # where a point in a bone's frame was drawn, in the world
    @staticmethod
    def apply(m, p, origin):
        x, y, z = p
        return (m[0] * x + m[1] * y + m[2] * z + m[3] + origin[0],
                m[4] * x + m[5] * y + m[6] * z + m[7] + origin[1],
                m[8] * x + m[9] * y + m[10] * z + m[11] + origin[2])

    def corners(self, f, bone):
        """Each box of the bone as its 8 corners in the world at this moment (none for a bone not drawn)."""
        i = self.index[bone]
        m = f['b'][i]
        if m == 0:
            return []
        o = f['pos']
        return [[self.apply(m, p, o) for p in cube] for cube in self.cubes[i]]

    def pivot(self, f, bone):
        """The middle of the bone's boxes in the world (a point that moves with the bone), or None."""
        i = self.index[bone]
        m = f['b'][i]
        if m == 0:
            return None
        return self.apply(m, self.center[i], f['pos'])

    def rotation(self, f, bone):
        """The bone's turn (3x3, columns normalised) or None."""
        m = f['b'][self.index[bone]]
        if m == 0:
            return None
        cols = []
        for c in range(3):
            v = (m[c], m[4 + c], m[8 + c])
            n = math.sqrt(sum(x * x for x in v)) or 1.0
            cols.append(tuple(x / n for x in v))
        return cols                                     # cols[c] = the bone's axis c in the world

    # the blocks
    def depth_in(self, p):
        key = (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))
        boxes = self.blocks.get(key)
        if not boxes:
            return 0.0
        x, y, z = p[0] - key[0], p[1] - key[1], p[2] - key[2]
        best = 0.0
        for b in boxes:
            if b[0] < x < b[3] and b[1] < y < b[4] and b[2] < z < b[5]:
                best = max(best, min(x - b[0], b[3] - x, y - b[1], b[4] - y, z - b[2], b[5] - z))
        return best

    def ground_under(self, x, y, z, reach=1.5):
        """The highest solid top in this column at or below y + 0.3 (down to reach blocks below), or None."""
        bx, bz = math.floor(x), math.floor(z)
        top = None
        for by in range(math.floor(y + 0.3), math.floor(y - reach) - 1, -1):
            for b in self.blocks.get((bx, by, bz), ()):
                if b[0] <= x - bx <= b[3] and b[2] <= z - bz <= b[5]:
                    t = by + b[4]
                    if t <= y + 0.3 and (top is None or t > top):
                        top = t
            if top is not None:
                return top
        return top

    def surface_at(self, x, z, y):
        """The water's top in this column round height y, or None."""
        bx, bz = math.floor(x), math.floor(z)
        top = None
        for by in range(math.floor(y) - 3, math.floor(y) + 3):
            h = self.water.get((bx, by, bz))
            if h is not None:
                top = by + h
        return top


def wrap(angle):
    """An angle difference brought into -180..180 degrees."""
    return (angle + 180.0) % 360.0 - 180.0


def box_distance(p, box):
    """How far point p is outside a box given by its 8 corners (corner 0 lowest; 1 x, 2 y, 4 z along its edges)."""
    c0 = box[0]
    d = (p[0] - c0[0], p[1] - c0[1], p[2] - c0[2])
    total = 0.0
    for k in (1, 2, 4):
        e = (box[k][0] - c0[0], box[k][1] - c0[1], box[k][2] - c0[2])
        length = math.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2])
        if length < 1e-9:
            continue                                    # a flat box: no thickness that way
        proj = (d[0] * e[0] + d[1] * e[1] + d[2] * e[2]) / length
        out = max(0.0, -proj, proj - length)
        total += out * out
    return math.sqrt(total)


# --- the checks -------------------------------------------------------------------------------------------------------
def analyse(rec, plan):
    frames = rec.frames
    times = [rec.time(f) for f in frames]
    px = rec.px
    chain = [plan['bones'].get(r, r) for r in plan.get('chain', [])]
    chain = [b for b in chain if b in rec.index]
    legs = {k: [plan['bones'].get(r, r) for r in v] for k, v in plan.get('legs', {}).items()}
    out = {'frames': len(frames), 'seconds': (times[-1] - times[0]) / 20.0 if frames else 0, 'px': px}
    drawn = [n for n in rec.names if any(rec.cubes[rec.index[n]])]

    # inside blocks: every box corner, every moment
    inside = {}
    for fi, f in enumerate(frames):
        for bone in drawn:
            for box in rec.corners(f, bone):
                for p in box:
                    d = rec.depth_in(p)
                    if d > 0.5 * px:
                        worst = inside.get(bone)
                        n = (worst[3] if worst else 0) + 1
                        if worst is None or d > worst[0]:
                            inside[bone] = [d, times[fi], p, n]
                        else:
                            worst[3] = n
                        break
    out['inside'] = inside

    # seams between chain neighbours: the corners of one that touch the other most of the time (found on about 60
    # moments spread over the scene, with the 3 boxes of the other nearest each), then how far they come away at worst
    seams = []
    sample = list(range(0, len(frames), max(1, len(frames) // 60)))
    for a, b in zip(chain, chain[1:]):
        for one, other in ((a, b), (b, a)):
            near = {}                                    # corner (cube, k) -> its distances to each box of other
            for fi in sample:
                ca, co = rec.corners(frames[fi], one), rec.corners(frames[fi], other)
                if not ca or not co:
                    continue
                for ci, cube in enumerate(ca):
                    for k, p in enumerate(cube):
                        near.setdefault((ci, k), []).append([box_distance(p, box) for box in co])
            touching = {}
            for corner, rows in near.items():
                if statistics.median(min(r) for r in rows) < 1.0 * px:
                    mean = [sum(r[j] for r in rows) / len(rows) for j in range(len(rows[0]))]
                    touching[corner] = sorted(range(len(mean)), key=lambda j: mean[j])[:3]
            if not touching:
                continue
            worst, at = -1.0, times[0]
            for fi, f in enumerate(frames):
                ca, co = rec.corners(f, one), rec.corners(f, other)
                if not ca or not co:
                    continue
                g = max(min(box_distance(ca[ci][k], co[j]) for j in boxes) for (ci, k), boxes in touching.items())
                if g > worst:
                    worst, at = g, times[fi]
            seams.append((one, other, worst, at, len(touching)))
    out['seams'] = seams

    # bends of chain joints: the angle between neighbours' turns, its yaw and pitch
    bends = []
    series = {}
    for a, b in zip(chain, chain[1:]):
        vals = []
        for f in frames:
            ra, rb = rec.rotation(f, a), rec.rotation(f, b)
            if ra is None or rb is None:
                vals.append(None)
                continue
            # b's front-back axis (z) seen in a's frame
            fz = rb[2]
            x = sum(fz[i] * ra[0][i] for i in range(3))
            y = sum(fz[i] * ra[1][i] for i in range(3))
            z = sum(fz[i] * ra[2][i] for i in range(3))
            vals.append((math.degrees(math.atan2(x, z)), math.degrees(math.atan2(y, math.hypot(x, z)))))
        # the yaw carried on past +-180 (a bone made facing the other way reads near 180), then each bend measured
        # from the joint's usual one in this scene
        last = None
        for i, v in enumerate(vals):
            if v is None:
                continue
            if last is not None:
                v = (last + wrap(v[0] - last), v[1])
                vals[i] = v
            last = v[0]
        known = [v for v in vals if v is not None]
        if len(known) < 3:
            continue
        mid = (statistics.median(v[0] for v in known), statistics.median(v[1] for v in known))
        vals = [None if v is None else (v[0] - mid[0], v[1] - mid[1]) for v in vals]
        series[(a, b)] = vals
        ok = [(t, v) for t, v in zip(times, vals) if v is not None]
        max_yaw = max(abs(v[0]) for _, v in ok)
        max_pitch = max(abs(v[1]) for _, v in ok)
        # a snap: the bend changing between two drawn moments far more than it changes just before and after
        moves = [math.hypot(v1[0] - v0[0], v1[1] - v0[1]) for (_, v0), (_, v1) in zip(ok, ok[1:])]
        rate, rate_at = 0.0, None
        for i, m in enumerate(moves):
            around = moves[max(0, i - 4):i] + moves[i + 1:i + 5]
            if around and m > 4 * statistics.median(around) + 1.0 and m > rate:
                rate, rate_at = m, ok[i + 1][0]
        bends.append((a, b, max_yaw, max_pitch, rate, rate_at))
    out['bends'] = bends
    out['bend_series'] = series

    # pops: a part's move between two moments far over its moves just before and after
    pops = []
    rel = {}
    for bone in drawn:
        c = rec.center[rec.index[bone]]
        pts = []
        for f in frames:
            m = f['b'][rec.index[bone]]
            pts.append(None if m == 0 else rec.apply(m, c, (0, 0, 0)))   # its middle, from where the animal is drawn
        rel[bone] = pts
    def pop_scan(label, pts, floor):
        steps = []
        for i in range(1, len(pts)):
            if pts[i] is None or pts[i - 1] is None:
                steps.append(None)
                continue
            dt = max(times[i] - times[i - 1], 1e-3)
            steps.append(math.dist(pts[i], pts[i - 1]) / dt)
        for i, s in enumerate(steps):
            if s is None:
                continue
            around = [x for x in steps[max(0, i - 4):i] + steps[i + 1:i + 5] if x is not None]
            if not around:
                continue
            usual = statistics.median(around)
            jump = s * max(times[i + 1] - times[i], 1e-3)
            if jump > floor and s > 4 * usual + floor:
                pops.append((label, jump, times[i + 1], usual))
    for bone, pts in rel.items():
        pop_scan(bone, pts, 1.5 * px)
    pop_scan('(the whole animal)', [tuple(f['pos']) for f in frames], 0.08)
    # keep each part's biggest few
    pops.sort(key=lambda p: -p[1])
    out['pops'] = pops

    # feet: the foot bone's own sole (its toes and claws are left to the inside check: they may hang lower while it
    # swings) over the ground under it, while the animal is on land; a step is the sole leaving the ground by over a
    # pixel and coming back; a planted foot (sole within half a pixel) should stay put in the world
    feet = {}
    foot_series = {}
    pos_speed = [0.0] + [math.hypot(frames[i]['pos'][0] - frames[i - 1]['pos'][0], frames[i]['pos'][2] - frames[i - 1]['pos'][2])
                         / max(times[i] - times[i - 1], 1e-3) for i in range(1, len(frames))]
    for key, bones in legs.items():
        if len(bones) < 3 or bones[2] not in rec.index:
            continue
        foot = bones[2]
        hovers, planted_slide, body_speed, ts, lifts = [], [], [], [], []
        last, swing = None, None
        for fi, f in enumerate(frames):
            pts = [p for box in rec.corners(f, foot) for p in box]
            if not pts:
                last = None
                continue
            sole = min(pts, key=lambda p: p[1])
            ground = rec.ground_under(sole[0], sole[1], sole[2])
            land = f['ground'] and not f['water'] and ground is not None
            h = sole[1] - ground if ground is not None else None
            ts.append((times[fi], h if land else None))
            piv = rec.pivot(f, foot)
            if land:
                hovers.append(h)
                if h > 1.0 * px:
                    swing = max(swing or 0.0, h)
                elif swing is not None and h < 0.5 * px:
                    lifts.append(swing)
                    swing = None
                if last is not None and h < 0.5 * px and last[2] < 0.5 * px:
                    dt = max(times[fi] - last[0], 1e-3)
                    planted_slide.append(math.hypot(piv[0] - last[1][0], piv[2] - last[1][2]) / dt)
                    body_speed.append(pos_speed[fi])
            last = (times[fi], piv, h if land else 99)
        foot_series[key] = ts
        if hovers:
            pairs = [(s, b) for s, b in zip(planted_slide, body_speed) if b > 0.005]
            feet[key] = dict(
                median=statistics.median(hovers), low=min(hovers), high=max(hovers),
                floating=sum(1 for h in hovers if h > 3 * px) / len(hovers),
                sunk=sum(1 for h in hovers if h < -1.0 * px) / len(hovers),
                slide=(sum(s for s, _ in pairs) / sum(b for _, b in pairs)) if pairs else None,
                planted=len(planted_slide), steps=len(lifts),
                lift=statistics.median(lifts) if lifts else None)
    out['feet'] = feet
    out['foot_series'] = foot_series

    # clips: a clip on for under 5 ticks between two others
    flickers = []
    runs = {}
    for f, t in zip(frames, times):
        for name, clip in f['clips'].items():
            r = runs.setdefault(name, [])
            clip = clip.split()[0]                          # the clip's name (a blend into it is not a new clip)
            if not r or r[-1][0] != clip:
                r.append([clip, t, t])
            else:
                r[-1][2] = t
    for name, r in runs.items():
        for i in range(1, len(r) - 1):
            if r[i][2] - r[i][1] < 5 and r[i - 1][0] != r[i][0] != r[i + 1][0]:
                flickers.append((name, r[i][0], r[i][1], r[i][2] - r[i][1]))
    out['clips'] = {name: [(c, round(t0, 1), round(t1 - t0, 1)) for c, t0, t1 in r] for name, r in runs.items()}
    out['flickers'] = flickers
    out['times'] = times
    return out


# --- the graph --------------------------------------------------------------------------------------------------------
def graph(rec, res, plan, path, title):
    from PIL import Image, ImageDraw
    W, H = 1400, 960
    img = Image.new('RGB', (W, H), 'white')
    d = ImageDraw.Draw(img)
    times = res['times']
    if len(times) < 2:
        return
    t0, t1 = times[0], times[-1]
    L, R = 70, W - 20
    panels = [(40, 400, 'heights (blocks): animal, head, chest, hips, tail tip; ground grey, water blue'),
              (430, 690, 'chain bends (degrees): pitch solid, yaw thin'),
              (720, 940, 'feet over the ground (model px, on land)')]
    def tx(t):
        return L + (R - L) * (t - t0) / max(t1 - t0, 1e-6)
    d.text((10, 10), title, fill='black')
    colors = ['#d62728', '#1f77b4', '#2ca02c', '#9467bd', '#ff7f0e', '#8c564b', '#e377c2', '#17becf', '#bcbd22',
              '#7f7f7f', '#393b79', '#637939', '#8c6d31', '#843c39', '#7b4173', '#3182bd', '#e6550d', '#31a354']

    def panel(top, bottom, label, lines, fixed=None):
        d.rectangle((L, top, R, bottom), outline='#999')
        d.text((L + 4, top + 2), label, fill='black')
        vals = [v for _, pts in lines for _, v in pts if v is not None]
        if fixed:
            vals += list(fixed)
        if not vals:
            return
        lo, hi = min(vals), max(vals)
        if hi - lo < 1e-6:
            hi = lo + 1
        pad = (hi - lo) * 0.08
        lo, hi = lo - pad, hi + pad
        def ty(v):
            return bottom - (bottom - top) * (v - lo) / (hi - lo)
        for k in range(5):
            v = lo + (hi - lo) * k / 4
            d.text((4, ty(v) - 6), f'{v:.2f}', fill='#555')
            d.line((L, ty(v), R, ty(v)), fill='#eee')
        if lo < 0 < hi:
            d.line((L, ty(0), R, ty(0)), fill='#bbb')
        for (style, pts) in lines:
            color, width = style
            prev = None
            for t, v in pts:
                if v is None:
                    prev = None
                    continue
                p = (tx(t), ty(v))
                if prev:
                    d.line((prev, p), fill=color, width=width)
                prev = p
    frames = rec.frames
    head = plan['head']['bone'] if 'head' in plan else 'head'
    chain = plan.get('chain', [])
    tip = chain[-1] if chain else None
    lines = [(('black', 2), [(t, f['pos'][1]) for t, f in zip(times, frames)])]
    lines.append((('#999', 1), [(t, rec.ground_under(f['pos'][0], f['pos'][1], f['pos'][2], 4)) for t, f in zip(times, frames)]))
    lines.append((('#39f', 1), [(t, rec.surface_at(f['pos'][0], f['pos'][2], f['pos'][1])) for t, f in zip(times, frames)]))
    for i, bone in enumerate([head, plan.get('forebody', 'chest'), 'hips', tip]):
        if bone and bone in rec.index:
            lines.append(((colors[i], 2), [(t, (rec.pivot(f, bone) or (None, None))[1]) for t, f in zip(times, frames)]))
    panel(*panels[0], lines)
    blines = []
    for i, ((a, b), vals) in enumerate(res['bend_series'].items()):
        c = colors[i % len(colors)]
        blines.append(((c, 2), [(t, v[1] if v else None) for t, v in zip(times, vals)]))
        blines.append(((c, 1), [(t, v[0] if v else None) for t, v in zip(times, vals)]))
    panel(*panels[1], blines)
    flines = [((colors[i], 2), [(t, (h / res['px']) if h is not None else None) for t, h in pts])
              for i, (k, pts) in enumerate(sorted(res['foot_series'].items()))]
    panel(*panels[2], flines, fixed=(0, 2))
    for p in res['pops'][:40]:
        x = tx(p[2])
        d.line((x, 30, x, 40), fill='red', width=2)
    # the clips along the bottom
    y = 944
    for name, runs in res['clips'].items():
        for clip, start, length in runs:
            d.text((tx(start), y), clip.split()[0][:12], fill='#444')
        break
    img.save(path)


# --- the report -------------------------------------------------------------------------------------------------------
def check_scene(job):
    """One scene of the course checked (run side by side, one scene a process): its report lines, its findings
    (size, sentence) and the size of a model pixel."""
    animal, scene = job
    plan = json.load(open(body_plan.plan_path(plan_of(animal)), encoding='utf-8'))
    lines, summary = [], []
    folder = os.path.join(KEPT, scene_name(animal, scene))
    path = os.path.join(folder, 'body.jsonl.gz')
    if not os.path.exists(path):
        return [f'{scene}: no recording ({path})'], [], None
    rec = Recording(path)
    if len(rec.frames) < 5:
        return [f'{scene}: only {len(rec.frames)} moments recorded'], [], None
    res = analyse(rec, plan)
    px = res['px']
    P = lambda blocks: blocks / px                     # noqa: E731  (blocks in model px)
    about = SCENES[scene][0] if scene in SCENES else 'a real scare into water (its own mind)'
    lines.append(f'== {scene}: {about}  ({res["frames"]} moments over {res["seconds"]:.1f} s)')
    ins = sorted(res['inside'].items(), key=lambda e: -e[1][0])
    if ins:
        lines.append('  inside blocks (deepest, when, how many moments):')
        for bone, (depth, t, p, n) in ins[:8]:
            lines.append(f'    {bone}: {P(depth):.1f} px at t {t:.1f} ({p[0]:.2f} {p[1]:.2f} {p[2]:.2f}), {n} moments')
        summary.append((P(ins[0][1][0]), f'{scene}: {ins[0][0]} drawn {P(ins[0][1][0]):.1f} px inside a block ({len(ins)} parts in all)'))
    seams = sorted(res['seams'], key=lambda s: -s[2])
    if seams:
        lines.append('  seams of the chain (worst gap, when):')
        for one, other, gap, t, n in seams:
            flag = '  <-- opens' if gap > 0.5 * px else ''
            lines.append(f'    {one} from {other}: {P(gap):.2f} px at t {t:.1f} ({n} touching corners){flag}')
        if seams[0][2] > 0.5 * px:
            summary.append((P(seams[0][2]), f'{scene}: the seam {seams[0][0]}/{seams[0][1]} opens {P(seams[0][2]):.1f} px'))
    if res['bends']:
        lines.append('  chain bends (from its usual bend: most yaw, most pitch; the biggest snap between two moments, degrees):')
        for a, b, my, mp, snap, at in res['bends']:
            when = f' at t {at:.1f}' if at is not None else ''
            lines.append(f'    {a}-{b}: yaw {my:.1f}, pitch {mp:.1f}, snap {snap:.1f}{when}')
        fast = max(res['bends'], key=lambda b: b[4])
        if fast[4] > 3:
            summary.append((fast[4] / 2, f'{scene}: the joint {fast[0]}-{fast[1]} snaps {fast[4]:.0f} deg between two moments at t {fast[5]:.1f}'))
    pops = res['pops']
    if pops:
        lines.append(f'  pops ({len(pops)}; biggest first: part, jump, when, its usual move per tick):')
        seen = set()
        for label, jump, t, usual in pops:
            if label in seen or len(seen) >= 10:
                continue
            seen.add(label)
            size = f'{jump:.2f} blocks' if label.startswith('(') else f'{P(jump):.1f} px'
            lines.append(f'    {label}: {size} at t {t:.1f} (usually {usual:.3f} blocks/tick)')
        whole = [p for p in pops if p[0].startswith('(')]
        parts = [p for p in pops if not p[0].startswith('(')]
        if whole:
            summary.append((whole[0][1] / px, f'{scene}: the whole animal jumps {whole[0][1]:.2f} blocks at t {whole[0][2]:.1f} ({len(whole)} jumps)'))
        if parts:
            summary.append((P(parts[0][1]), f'{scene}: {parts[0][0]} pops {P(parts[0][1]):.1f} px at t {parts[0][2]:.1f} ({len(parts)} pops of parts)'))
    if res['feet']:
        lines.append('  feet on land (the foot bone sole over the ground, px: middle, lowest, highest; share floating over 3 px; share sunk; how far a planted foot slides against how far the body goes; steps and their usual lift):')
        for key, ft in sorted(res['feet'].items()):
            slide = f'{ft["slide"]:.2f}' if ft['slide'] is not None else '-'
            lift = f'{P(ft["lift"]):.1f} px' if ft['lift'] is not None else '-'
            lines.append(f'    {key}: {P(ft["median"]):.1f}, {P(ft["low"]):.1f}, {P(ft["high"]):.1f}; floating {ft["floating"]:.0%}; '
                         f'sunk {ft["sunk"]:.0%}; slide {slide} ({ft["planted"]} planted moments); {ft["steps"]} steps lifting {lift}')
            if ft['slide'] is not None and ft['slide'] > 0.35:
                summary.append((ft['slide'] * 4, f'{scene}: foot {key} slides while planted ({ft["slide"]:.2f} of the body\'s speed)'))
            if ft['floating'] > 0.4:
                summary.append((ft['floating'] * 4, f'{scene}: foot {key} floats over 3 px {ft["floating"]:.0%} of the time on land'))
    lines.append('  clips: ' + '; '.join(f'{name}: ' + ', '.join(f'{c.split()[0]} {t0}+{n}' for c, t0, n in runs[:14])
                                       + (' ...' if len(runs) > 14 else '') for name, runs in res['clips'].items()))
    for name, clip, t, n in res['flickers']:
        lines.append(f'  FLICKER {name}: {clip} for {n:.1f} ticks at t {t:.1f}')
    if res['flickers']:
        summary.append((3, f'{scene}: {len(res["flickers"])} clip flicker(s)'))
    graph(rec, res, plan, os.path.join(folder, 'graph.png'), f'{animal} {scene}: {about}')
    lines.append('')
    return lines, summary, px


def report(animal):
    plan = json.load(open(body_plan.plan_path(plan_of(animal)), encoding='utf-8'))
    lines = [f'The measuring course for the {animal} (tools/body_course.py; 1 model px = {{px}} blocks)', '']
    summary = []
    px = None
    from concurrent.futures import ProcessPoolExecutor
    with ProcessPoolExecutor(max_workers=max(1, min(10, (os.cpu_count() or 4) - 2))) as pool:
        for scene_lines, scene_summary, scene_px in pool.map(check_scene, [(animal, s) for s in scenes_of(animal)]):
            lines += scene_lines
            summary += scene_summary
            px = scene_px or px
    lines[0] = lines[0].replace('{px}', f'{px:.4f}' if px else '?')
    summary.sort(key=lambda s: -s[0])
    lines[1:1] = ['Worst first:'] + ['  ' + s for _, s in summary[:25]] + ['']
    out = os.path.join(KEPT, f'course_{animal}.txt')
    open(out, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    return out, summary


def main(args):
    if not args or args[0] not in ('films', 'run', 'check'):
        print(__doc__)
        return 1
    animals = args[1:] or list(ANIMALS)
    for a in animals:
        if a not in ANIMALS:
            print(f'no animal {a} on the course (ANIMALS has {", ".join(ANIMALS)})')
            return 1
    if args[0] in ('films', 'run'):
        films = write_films(animals)
        print(f'{len(films)} shot lists in {COURSE}')
    if args[0] == 'run':
        films = [os.path.relpath(p, PROJECT).replace('\\', '/') for a in animals for p in
                 [os.path.join(COURSE, scene_name(a, s) + '.film') for s in scenes_of(a)]]
        gradle = os.path.join(PROJECT, 'gradlew.bat' if os.name == 'nt' else 'gradlew')
        subprocess.run([gradle, 'runFilm', '-PfilmScene=' + ','.join(films)], cwd=PROJECT, check=False)
        import shutil
        os.makedirs(KEPT, exist_ok=True)
        for film in films:
            name = os.path.basename(film)[:-len('.film')]
            if os.path.isdir(os.path.join(SHOTS, name)):
                shutil.rmtree(os.path.join(KEPT, name), ignore_errors=True)
                shutil.move(os.path.join(SHOTS, name), os.path.join(KEPT, name))
    if args[0] in ('run', 'check'):
        for a in animals:
            path, summary = report(a)
            print(f'{a}: {path}')
            for _, s in summary[:10]:
                print('  ' + s)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
