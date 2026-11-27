"""Two animals touching, measured from the game's own recordings (the Motion Studio, phase 0, 2026-11-25;
docs/plans/motion-studio.md). Reads every body_*.jsonl.gz a film wrote with "record on 1 all" (each animal's bones as
the game drew them, its clips and its mind's facts) and reports, for each hunter that held its prey:

  timeline   its hunt stages and clip changes, tick by tick (a clip that restarts shows as a change to itself)
  jaws       where its jaws (its plan's "carry" bone) were against the prey's body while holding: the gap to the
             nearest box of the prey (+ in the air, - into the body), smallest, middle and largest
  feet       its planted feet sliding over the ground while holding (blocks a tick; a planted foot should not move)
  strike     its box's travel through each strike clip, tick by tick (since phase 3 step 4 the clip carries its own
             travel and the box rides it from the strike's start: still through a coil, then the flight, then still)
  prey       the prey's clips and doings meanwhile

    python tools/pair_report.py <scene folder> [--prey <name>]      (default prey: film-calf)
    e.g. python tools/pair_report.py run-film/screenshots/lion_hunt

The game is the one truth: this only reads what the game drew. Moments of two animals are matched by the client's tick
count the recorder wrote beside each ("ct", 2026-11-27 on; the game tick "ft" in older files) and the in-between time,
and timed by them (tools/recording.py); an animal is in the files only while it was drawn.
Writes the report to pair_report.txt in the folder too.
"""
import glob
import json
import math
import os
import statistics
import sys

import recording

HERE = os.path.dirname(os.path.abspath(__file__))


def plan_of(entity):
    """The body plan of an entity id (antcolonies:lioness -> lioness.json), or {}."""
    name = entity.split(':')[-1]
    for path in glob.glob(os.path.join(os.path.dirname(HERE), '*', 'src', 'main', 'resources', 'assets', '*', 'body_plans', name + '.json')):
        with open(path, encoding='utf-8') as f:
            return json.load(f)
    return {}


class Animal:
    def __init__(self, path):
        header, moments, blocks, _, trouble = recording.read(path)
        if trouble:
            print(f'{os.path.basename(path)}: {trouble}', file=sys.stderr)
        self.file = os.path.basename(path)
        self.header = header
        self.frames = [f for f in moments if 'ft' in f]
        self.name = self.header.get('name', self.file)
        self.entity = self.header['entity']
        self.plan = plan_of(self.entity)
        bones = self.header['bones']
        self.names = [b['name'] for b in bones]
        self.index = {n: i for i, n in enumerate(self.names)}
        self.pivot = [tuple(b.get('pivot', (0, 0, 0))) for b in bones]
        self.cubes = [[tuple(c[i:i + 3] for i in range(0, 24, 3)) for c in b['cubes']] for b in bones]
        self.tops = {}                     # each column's block tops (the ground and anything on it)
        # (the first line's blocks, and those of each "more" line written as the animal went on: R12-48)
        for entry in blocks:
            x, y, z = entry[:3]
            for box in entry[3:]:
                self.tops.setdefault((x, z), []).append(y + box[4])
        self.by_time = {}
        for f in self.frames:
            self.by_time.setdefault(f['ft'], []).append(f)
        # (the replay page's review, 2026-11-27: two animals' moments drawn in one frame share the client's tick
        # count "ct" and the in-between time; the game tick is read live, so through a frozen step of k ticks all k
        # moments read its end, and a sprinting server reads a few ticks apart within one frame)
        self.by_clock = {}
        for f in self.frames:
            if 'ct' in f:
                self.by_clock.setdefault(f['ct'], []).append(f)

    def ground_under(self, p):
        """The highest block top under this point and at most half a block above it (flat ground: the grass top), or
        None outside the blocks written down round where it was first drawn."""
        tops = self.tops.get((math.floor(p[0]), math.floor(p[2])), [])
        below = [t for t in tops if t <= p[1] + 0.5]
        return max(below) if below else None

    @staticmethod
    def apply(m, p, o):
        x, y, z = p
        return (m[0] * x + m[1] * y + m[2] * z + m[3] + o[0],
                m[4] * x + m[5] * y + m[6] * z + m[7] + o[1],
                m[8] * x + m[9] * y + m[10] * z + m[11] + o[2])

    def point(self, f, bone):
        """The bone's pivot in the world at this moment (an empty bone's place: the jaws' grip), or None."""
        i = self.index.get(bone)
        if i is None or f['b'][i] == 0:
            return None
        return self.apply(f['b'][i], self.pivot[i], f['pos'])

    def boxes(self, f, bones=None):
        """Every box (8 world corners) of these bones (all when None) at this moment."""
        out = []
        for i, n in enumerate(self.names):
            if bones is not None and n not in bones:
                continue
            m = f['b'][i]
            if m == 0:
                continue
            for cube in self.cubes[i]:
                out.append([self.apply(m, p, f['pos']) for p in cube])
        return out

    def clip(self, f):
        return f['clips'].get('body', '-').split(' ')[0]

    def at(self, other):
        """Its moment drawn with another animal's moment: the same client tick when both have one (the nearest
        in-between time in it), else the same game tick, or None."""
        frames = self.by_clock.get(other['ct']) if 'ct' in other and self.by_clock else self.by_time.get(other['ft'])
        if not frames:
            return None
        return min(frames, key=lambda f: abs(f['pt'] - other['pt']))


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def box_gap(p, corners):
    """Distance from p to a box given by 8 corners (bit 0 x, bit 1 y, bit 2 z): + outside, - inside (to the nearest face)."""
    c0 = corners[0]
    axes = [sub(corners[1], c0), sub(corners[2], c0), sub(corners[4], c0)]
    d = sub(p, c0)
    outside = 0.0
    inside = 1e9
    for ax in axes:
        length = math.sqrt(dot(ax, ax))
        if length < 1e-9:
            continue
        u = dot(d, ax) / length            # along this edge, in blocks
        if u < 0:
            outside += u * u
        elif u > length:
            outside += (u - length) ** 2
        else:
            inside = min(inside, u, length - u)
    return math.sqrt(outside) if outside > 0 else -inside


def feet_of(animal):
    legs = animal.plan.get('legs', {})
    return [chain[-1] for chain in legs.values() if chain]


def report(folder, prey_name):
    animals = [Animal(p) for p in sorted(glob.glob(os.path.join(folder, 'body_*.jsonl.gz')))]
    tops = {}                              # the ground under every animal's first place, shared (each header has its own)
    for a in animals:
        for k, v in a.tops.items():
            tops.setdefault(k, set()).update(v)
    for a in animals:
        a.tops = tops
    out = []
    say = out.append
    say(f'Pair report for {folder}: {len(animals)} animals recorded')
    for a in animals:
        span = (min(a.by_time), max(a.by_time)) if a.by_time else (None, None)
        say(f'  {a.name:16s} {a.entity:24s} {len(a.frames):5d} moments, game ticks {span[0]} to {span[1]}')
    prey = next((a for a in animals if a.name == prey_name), None)
    if prey is None:
        say(f'no prey named {prey_name}')
        return out
    for a in animals:
        # (review R12-16: the holder's own strike clips, from its plan; the lion family's names were written in here)
        striking = tuple((a.plan.get('strikes') or {}).get('clips', {}))
        holds = [f for f in a.frames if f.get('facts', {}).get('holding') and a.clip(f) not in striking]
        strikes = [f for f in a.frames if a.clip(f) in striking]
        if not holds and not strikes:
            continue
        say('')
        say(f'== {a.name} ({a.entity})')
        # timeline: stage and clip changes
        last, last_ticks, last_doing = None, None, None
        changes = []
        for f in a.frames:
            facts = f.get('facts', {})
            key = (facts.get('stage'), a.clip(f), facts.get('holding'))
            ticks = facts.get('clip_tick')
            restarted = last is not None and facts.get('doing') == last_doing and ticks is not None and last_ticks is not None                 and ticks < last_ticks
            if key != last or restarted:
                changes.append(f"  tick {f['ft']}: stage {key[0]}, clip {key[1]}{' (its doing restarted)' if restarted else ''}, "
                               f"holding {key[2]}, doing {facts.get('doing')}, speed {f.get('play', {}).get('body', ['?'])[0]}")
            last, last_ticks, last_doing = key, ticks, facts.get('doing')
        empty = [f['ft'] for f in a.frames if f['clips'].get('body', '').startswith('- ')]
        if empty:
            say(f'NO CLIP on its body player at {len(empty)} moments (drawn in its rest pose), ticks {empty[0]} to {empty[-1]}')
        say(f'timeline ({len(changes)} changes):')
        out.extend(changes[:80])
        if len(changes) > 80:
            say(f'  ... {len(changes) - 80} more')
        # the strike: how far the box went during the strike clip
        if strikes:
            runs = []
            run = [strikes[0]]
            for f in strikes[1:]:
                if f['ft'] - run[-1]['ft'] <= 2 and a.clip(f) == a.clip(run[-1]):
                    run.append(f)
                else:
                    runs.append(run)
                    run = [f]
            runs.append(run)
            for run in runs:
                p0, p1 = run[0]['pos'], run[-1]['pos']
                dist = math.hypot(p1[0] - p0[0], p1[2] - p0[2])
                ticks = run[-1]['ft'] - run[0]['ft']
                steps, seen = [], {}
                for f in run:
                    seen.setdefault(f['ft'], f['pos'])
                ts = sorted(seen)
                for t0, t1 in zip(ts, ts[1:]):
                    q0, q1 = seen[t0], seen[t1]
                    steps.append(math.hypot(q1[0] - q0[0], q1[2] - q0[2]) / max(1, t1 - t0))
                say(f'strike {a.clip(run[0])} at ticks {run[0]["ft"]} to {run[-1]["ft"]}: its box went {dist:.2f} blocks; '
                    f'a tick: ' + ' '.join(f'{v:.2f}' for v in steps))
        if not holds:
            continue
        # (review R12-16: the jaw it holds with is the plan's holds.jaw; the otter's carry section was read before)
        carry = (a.plan.get('holds') or {}).get('jaw') or a.plan.get('checks', {}).get('carry', {}).get('bone', 'carry')
        gaps = []
        rows = []
        for f in holds:
            jaw = a.point(f, carry)
            pm = prey.at(f)
            if jaw is None or pm is None:
                continue
            g = min(box_gap(jaw, box) for box in prey.boxes(pm))
            gaps.append(g)
            rows.append((f['ft'], g, a.clip(f), prey.clip(pm), pm.get('facts', {}).get('doing')))
        if gaps:
            say(f'jaws ({carry}) against {prey.name} while holding, {len(gaps)} moments: smallest {min(gaps):+.2f}, '
                f'middle {statistics.median(gaps):+.2f}, largest {max(gaps):+.2f} blocks (+ in the air, - into its body)')
            for r in rows[::max(1, len(rows) // 12)]:
                say(f'  tick {r[0]}: gap {r[1]:+.2f}, lion clip {r[2]}, prey clip {r[3]} (doing {r[4]})')
        else:
            say('jaws: no moments with both animals drawn')
        # feet: planted feet sliding
        slides = []
        prev = {}
        unknown = 0
        for f in holds:
            for foot in feet_of(a):
                boxes = a.boxes(f, {foot})
                if not boxes:
                    continue
                low = min((p for box in boxes for p in box), key=lambda p: p[1])
                ground = a.ground_under(low)
                unknown += ground is None
                planted = ground is not None and low[1] - ground < 0.1
                key = foot
                # a tick's worth: the drawn places are in-between ones, so by the real time between the two moments
                # (review R12-17: neighbouring ticks' moments lie anywhere from near 0 to near 2 ticks apart)
                now = recording.clock(f)
                if planted and key in prev and prev[key][0] is not None and 0.25 <= now - prev[key][0] <= 2.0:
                    slides.append(math.hypot(low[0] - prev[key][1][0], low[2] - prev[key][1][2]) / (now - prev[key][0]))
                prev[key] = (now, low) if planted else (None, None)
        if slides:
            say(f'feet while holding: {len(slides)} planted-foot ticks, slide middle {statistics.median(slides):.3f}, '
                f'9 in 10 under {sorted(slides)[int(len(slides) * 0.9)]:.3f}, most {max(slides):.3f} blocks a tick')
        elif unknown:
            say(f'feet while holding: NO GROUND KNOWN under its feet at {unknown} foot moments (no blocks recorded '
                'there): sliding not judged')
        apart = lambda f1, f2: recording.clock(f2) - recording.clock(f1)
        moved = [math.hypot(f2['pos'][0] - f1['pos'][0], f2['pos'][2] - f1['pos'][2]) / apart(f1, f2)
                 for f1, f2 in zip(holds, holds[1:]) if 0.25 <= apart(f1, f2) <= 2.0]
        if moved:
            say(f'its box while holding moved middle {statistics.median(moved):.3f}, most {max(moved):.3f} blocks a tick')
    return out


def main(argv):
    if not argv:
        print(__doc__)
        return 1
    folder = argv[0]
    prey = argv[argv.index('--prey') + 1] if '--prey' in argv else 'film-calf'
    lines = report(folder, prey)
    text = '\n'.join(lines)
    print(text)
    with open(os.path.join(folder, 'pair_report.txt'), 'w', encoding='utf-8') as f:
        f.write(text + '\n')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
