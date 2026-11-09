"""
The march model (the user's plan of 2026-11-08 for the raid's way out: the scout goes down into the nest, comes out
first, and the raiders coming out meet up behind it, the nearest first).

The way out is all movement, so it can be worked out on paper: when each raider reaches the ground at the foot of the
nest's hump, how fast the front walks, how fast a raider behind its place catches up, how far apart the raiders are. This
tool reruns that sum with the game's own numbers and sets it beside what the game really did in the march tests (the
two dead-mound tests, the stream since 2026-11-08; older logs of the ribbon tests read too):

  - the paces are read from RaidColumn.java (FRONT_PACE, FILLING_PACE, CATCH_UP_PACE, PACE_PER_BLOCK,
    LEAST_MARCH_PACE, PASSED_PACE) and the spacing from the Matabele species file (column.spacing), so they never drift;
    the stream has one place per raider, each a spacing behind the one ahead (side by side is left to the legs);
  - from the test log it reads, for each march test: when the front stood on the ground past the hump's foot, when each
    raider came down on the ground (the way out, raider by raider), the front's free walking speed, and the crowding the
    test measured on the flat (how much of its asked speed a raider really got, catching up and keeping its place, the
    share of the time it stood held for the crowd, and how near its nearest nestmate was);
  - it runs the model twice, without crowding (every ant walks exactly its pace) and with the crowding the game measured,
    and prints both beside the game: how far past the hump's foot the front was when every raider was within a row of
    its place (closed up), and the column's length as the front passed 10, 20 and 30 blocks past the foot (a perfect
    column is the raiders times the spacing; the game counts every raider out of the nest, the model only the ones down on
    the ground, so the game's lengths run longer while raiders are still coming down the hump);
  - with --draw it draws the column from above every 5 s of the march, from the test's "march at" lines: one strip per
    look, the trail running left to right (a character a half block), three lanes (left of the trail, on it, right of it),
    S the front, 1 to 9 the raiders in a half block of a lane.

Run (after `ANTCOLONIES_TESTS=deadmoundstreams tools/run_game_tests.sh` or any run that held the two march tests):

    python tools/march_model.py                 (every march test found in run-test-shards/*/logs and run-test/logs)
    python tools/march_model.py --draw          (and draw each march)
    python tools/march_model.py some/latest.log (a log of your own)
    python tools/march_model.py --try FRONT_PACE=1.0 CATCH_UP_PACE=1.35   (try other paces on the same way out)
    python tools/march_model.py --try FILLING_PACE=0.9   (a slower front till the column closes up: tried in the game
                                                          on 2026-11-08 and taken out, the crowd never closes up fully)

It is a model, not the game: raiders start at the hump's foot the moment they are down on the ground, walk the trail as a
line (no sideways steps into lanes), and crowding is one share of the asked speed for all of them. When the model with
crowding and the game agree, the model can be trusted to try new paces; when they do not, read why in the drawing.
"""
import argparse
import glob
import json
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COLUMN_JAVA = os.path.join(ROOT, 'savanna', 'src', 'main', 'java', 'com', 'antcolonies', 'savanna', 'colony', 'RaidColumn.java')
SPECIES = os.path.join(ROOT, 'savanna', 'src', 'main', 'resources', 'data', 'antcolonies', 'species', 'megaponera_analis.json')
PACES = ('FRONT_PACE', 'FILLING_PACE', 'CATCH_UP_PACE', 'PACE_PER_BLOCK', 'LEAST_MARCH_PACE', 'PASSED_PACE')


def game_numbers():
    """The march's paces from RaidColumn.java and the spacing from the species file."""
    src = open(COLUMN_JAVA, encoding='utf-8').read()
    nums = {}
    for name in PACES:
        m = re.search(r'static final double ' + name + r'\s*=\s*([0-9.]+)', src)
        if m:
            nums[name] = float(m.group(1))
    nums.setdefault('FILLING_PACE', nums['FRONT_PACE'])  # (older code: the front at one pace all the way)
    species = json.load(open(SPECIES, encoding='utf-8'))
    column = find_key(species, 'column')
    nums['spacing'] = float(column['spacing'])
    return nums


def find_key(node, key):
    if isinstance(node, dict):
        if key in node and isinstance(node[key], dict) and 'spacing' in node[key]:
            return node[key]
        for v in node.values():
            found = find_key(v, key)
            if found is not None:
                return found
    elif isinstance(node, list):
        for v in node:
            found = find_key(v, key)
            if found is not None:
                return found
    return None


def fields(line):
    """'name value name value ...' after the colon into a dict of floats (NaN kept)."""
    words = line.split(':', 1)[1].replace(' of ', ' total ').split()
    out = {}
    for i in range(0, len(words) - 1, 2):
        try:
            out[words[i]] = float(words[i + 1])
        except ValueError:
            pass
    return out


def read_runs(paths):
    """Each march test in the logs: its way out, closing up, crowding and march drawings."""
    runs = []
    for path in paths:
        run = None
        for raw in open(path, encoding='utf-8', errors='replace'):
            tag = '[march test]' if '[march test]' in raw else ('[ribbon test]' if '[ribbon test]' in raw else None)
            if tag is None:
                continue
            line = raw.split(tag, 1)[1].strip()
            if re.match(r'\d+ raiders, entrance', line):
                run = {'log': path, 'marches': [], 'title': line}
                runs.append(run)
            if run is None:
                continue
            if line.startswith('march at '):
                run['marches'].append(line)
            elif line.startswith('the way out, raider by raider'):
                run['ways'] = [tuple(float(x) for x in m.groups()) for m in re.finditer(r'([0-9.]+)>([0-9.]+)\(([0-9.]+)\)', line)]
            elif line.startswith('closing up:'):
                run['closing'] = fields(line)
            elif line.startswith('crowding:'):
                run['crowding'] = fields(line)
            elif line.startswith('march speed:'):
                m = re.search(r'the hump ends ([0-9.]+) along', line)
                run['hump'] = float(m.group(1)) if m else 0.0
    return [r for r in runs if 'ways' in r and 'closing' in r and 'crowding' in r]


def model(nums, v, front_foot, ground, catch_eff=1.0, keep_eff=1.0, dt=0.05):
    """The way out from the hump's foot: closed up where (front past the foot), and the length at 10, 20, 30."""
    ground = sorted(ground)
    n = len(ground)
    rows = list(range(n))  # (the stream: one place per raider, a spacing apart)
    sp = nums['spacing']
    pos = [None] * n
    s = 0.0
    t = min(front_foot, ground[0]) if ground else front_foot
    tight = None
    length = {}
    while t < front_foot + 240.0 and s < 200.0:
        front_pace = nums['FRONT_PACE'] if tight is not None else nums['FILLING_PACE']
        if t >= front_foot:
            s += front_pace * v * dt
        worst = 0.0
        for i in range(n):
            if t < ground[i]:
                worst = math.inf
                continue
            if pos[i] is None:
                pos[i] = 0.0
            gap = s - (rows[i] + 1) * sp - pos[i]
            pace = max(nums['LEAST_MARCH_PACE'], min(nums['CATCH_UP_PACE'], front_pace + nums['PACE_PER_BLOCK'] * gap))
            if pos[i] > s - sp * 0.5:
                pace = nums.get('PASSED_PACE', 0.4)
            pos[i] += pace * v * dt * (catch_eff if gap > sp else keep_eff)
            worst = max(worst, gap)
        for mark in (10, 20, 30):
            if mark not in length and s >= mark:
                length[mark] = s - min(p for p in pos if p is not None) if any(p is not None for p in pos) else 0.0
        if tight is None and worst <= sp and t >= front_foot:
            tight = s
        t += dt
    ideal = (rows[-1] + 1) * sp if rows else 0.0
    return tight, length, ideal


def fmt(x):
    return '  -  ' if x is None or (isinstance(x, float) and math.isnan(x)) else '%5.1f' % x


def draw(run, half=0.5):
    """The column from above, every look of the test (every 5 s): three lanes, S the front, digits the raiders."""
    print('  the march from above (a character a half block along the trail; lanes: left, on it, right):')
    for line in run['marches']:
        m = re.match(r'march at (\d+) s: front along ([-0-9.]+)( \(in the nest\))?(.*)', line)
        if not m:
            continue
        sec, front, nest, rest = int(m.group(1)), float(m.group(2)), m.group(3), m.group(4)
        ants = [(float(a), float(b)) for a, b in re.findall(r'; ([-0-9.]+) ([-+0-9.]+)', rest)]
        hi = max([front] + [a for a, _ in ants] + [1.0])
        width = int(hi / half) + 2
        lanes = [[0] * width for _ in range(3)]
        for along, side in ants:
            col = max(0, int(along / half))
            lane = 0 if side < -0.2 else (2 if side > 0.2 else 1)
            lanes[lane][min(col, width - 1)] += 1
        fcol = max(0, int(front / half))
        print('  %3d s, front %5.1f%s' % (sec, front, ' (in the nest)' if nest else ''))
        for li, lane in enumerate(lanes):
            chars = ['.' if c == 0 else (str(c) if c < 10 else '#') for c in lane]
            if li == 1 and not nest:
                chars[min(fcol, width - 1)] = 'S'
            print('        |' + ''.join(chars))
        print()


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[1])
    ap.add_argument('logs', nargs='*', help='test logs (default: run-test-shards/*/logs/latest.log and run-test/logs/latest.log)')
    ap.add_argument('--draw', action='store_true', help='draw each march from above')
    ap.add_argument('--try', dest='tries', nargs='*', default=[], metavar='NAME=VALUE', help='other paces to try (e.g. CATCH_UP_PACE=1.35)')
    args = ap.parse_args()
    paths = args.logs or sorted(glob.glob(os.path.join(ROOT, 'run-test-shards', '*', 'logs', 'latest.log'))) \
        + sorted(glob.glob(os.path.join(ROOT, 'run-test', 'logs', 'latest.log')))
    nums = game_numbers()
    for t in args.tries:
        k, v = t.split('=')
        nums[k] = float(v)
    slow = '' if nums['FILLING_PACE'] == nums['FRONT_PACE'] else ' (%.2f till the column closes up)' % nums['FILLING_PACE']
    print('Paces: front %.2f%s, catch-up up to %.2f, %.2f more per block behind, no slower than %.2f;'
          ' raiders %.2f apart in the stream' % (nums['FRONT_PACE'], slow, nums['CATCH_UP_PACE'],
                                               nums['PACE_PER_BLOCK'], nums['LEAST_MARCH_PACE'], nums['spacing']))
    runs = read_runs(paths)
    if not runs:
        print('No march test with the closing-up and crowding lines in: ' + ', '.join(paths or ['(no logs)']))
        print('Run the march tests first: ANTCOLONIES_TESTS=deadmoundstreams tools/run_game_tests.sh')
        return 1
    for run in runs:
        c, w = run['closing'], run['crowding']
        v = w.get('free_speed', 2.34)
        ground = [g for _, g, _ in run['ways']]
        front_foot = c.get('front_foot_s', ground[0] if ground else 0.0)
        catch_eff = w.get('catch_up_got', 1.0)
        keep_eff = w.get('keeping_got', 1.0)
        catch_eff = 1.0 if math.isnan(catch_eff) else catch_eff
        keep_eff = 1.0 if math.isnan(keep_eff) else keep_eff
        free = model(nums, v, front_foot, ground)
        crowded = model(nums, v, front_foot, ground, catch_eff, keep_eff)
        print()
        print('%s  (%s)' % (run['title'], os.path.relpath(run['log'], ROOT)))
        spread = (max(ground) - min(ground)) if ground else 0.0
        print('  way out: the front on the ground past the hump %.1f s into the march; %d raiders down on the ground over %.1f s'
              ' (first %.1f s, last %.1f s); the hump ends %.1f blocks along' % (front_foot, len(ground), spread,
                                                                                min(ground or [0]), max(ground or [0]), run.get('hump', 0.0)))
        print('  crowding on the flat: free walk %.2f blocks a second; raiders catching up got %d%% of their asked speed (%d looks),'
              ' keeping their place %d%% (%d); held for the crowd %.1f%% of the time; nearest nestmate: closest %.2f, 1 in 10 under %.2f,'
              ' median %.2f blocks; %d of %d looks under 0.4 (inside personal space)'
              % (v, round(catch_eff * 100), w.get('catch_up_ticks', 0), round(keep_eff * 100), w.get('keeping_ticks', 0),
                 w.get('held_share', 0.0) * 100, w.get('nearest_min', math.nan), w.get('nearest_p10', math.nan),
                 w.get('nearest_median', math.nan), w.get('nearest_under_0.4', 0), w.get('total', 0)))
        perfect = c.get('perfect', free[2])
        print('  a perfect column: %.1f blocks long%s' % (free[2], '' if abs(perfect - free[2]) < 0.05 else ' (the game says %.1f)' % perfect))
        print('                           closed up at   length at 10   at 20   at 30   (blocks past the hump\'s foot)')
        print('    model, no crowding        %s          %s      %s   %s' % (fmt(free[0]), fmt(free[1].get(10)), fmt(free[1].get(20)), fmt(free[1].get(30))))
        print('    model, game\'s crowding    %s          %s      %s   %s' % (fmt(crowded[0]), fmt(crowded[1].get(10)), fmt(crowded[1].get(20)), fmt(crowded[1].get(30))))
        print('    the game                  %s          %s      %s   %s   (at the end %s, %.0f%% of a perfect one)'
              % (fmt(c.get('tight_past_foot')), fmt(c.get('length_at_10')), fmt(c.get('length_at_20')), fmt(c.get('length_at_30')),
                 fmt(c.get('length_end')).strip(), 100.0 * c.get('length_end', math.nan) / free[2] if free[2] else math.nan))
        if args.draw:
            draw(run)
    return 0


if __name__ == '__main__':
    sys.exit(main())
