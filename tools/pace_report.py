"""Each animal's gaits as the game really played them, from a film's recordings (the Motion Studio, phase 2, step 2,
2026-11-25; docs/plans/motion-studio-phase2.md). Reads every body_*.jsonl.gz a film wrote with "record on 1 all" and,
for each animal whose body plan has gaits (its "gaits" section, the clip speeds in its "checks.walks"), reports per gait
clip:

  time      seconds in the clip, and its size (calves and cubs are drawn smaller: their clips' feet move less)
  play      the clip's play speed: middle, lowest, highest; the share of time under 0.8 or over 1.25 (the clip looks
            hurried or dragged: a looks question, for the clip's maker) and at its plan's limits (there the play speed no
            longer follows the stride)
  feet      how the ground went under it against how fast the clip moves its feet: 1.0 the feet stay put on the ground;
            the share of time off by more than a fifth either way (its feet slide, or tread on the spot)
  changes   gait changes, and the quick ones back within a second (the gait flickering on the line between two); each
            change, where in the old clip it came against its plan's moment (gaits.<old>.change.<new>: the game
            changes gait as the old clip passes it, the Motion Studio phase 2, step 3)

and, for every clip that is not a gait, the time it moved faster than half a block a second (a body gliding on a clip
made in place: a strike or a pounce whose run the clip does not show).

    python tools/pace_report.py <scene folder> [<scene folder> ...]
    e.g. python tools/pace_report.py run-film/screenshots/lion_hunt run-film/screenshots/buffalo_frames

The game is the one truth: speeds are measured from where each animal was drawn, a tick apart, never from its mind.
Writes the report to pace_report.txt in each folder too.
"""
import glob
import gzip
import json
import math
import os
import statistics
import sys

from pair_report import plan_of

SLIDE = 0.2           # the feet are off by more than this share either way: a slide (or a tread on the spot)
HURRIED, DRAGGED = 1.25, 0.8
GLIDE = 0.5 / 20.0    # blocks a tick: faster than this on a clip made in place shows as gliding
QUICK_BACK = 20       # ticks: a gait changed back within this flickers


def read(path):
    """The header and one moment a tick (the one drawn nearest the tick's start), oldest first."""
    with gzip.open(path, 'rt', encoding='utf-8') as f:
        lines = [json.loads(line) for line in f if line.strip()]
    by_tick = {}
    for m in lines[1:]:
        if 'pos' not in m:
            continue
        best = by_tick.get(m['t'])
        if best is None or m['pt'] < best['pt']:
            by_tick[m['t']] = m
    return lines[0], [by_tick[t] for t in sorted(by_tick)]


class Clip:
    def __init__(self, name, gait, scale):
        self.name = name
        self.gait = gait            # its plan's gait (madeFor blocks a tick drawn at this size, min, max), or None
        self.ticks = 0
        self.plays = []
        self.limit = 0
        self.ratios = []
        self.moving = 0


def moments_of(plan):
    """The plan's gait change moments: (old, new) -> seconds of the old clip."""
    out = {}
    for name, g in plan.get('gaits', {}).items():
        if isinstance(g, dict):
            for new, at in g.get('change', {}).items():
                out[(name, new)] = at
    return out


def gaits_of(plan):
    """The plan's gaits: clip -> (its feet's speed, blocks a tick at size 1; min; max), or {}."""
    gaits = plan.get('gaits')
    walks = plan.get('checks', {}).get('walks', {})
    if not gaits:
        return {}
    out = {}
    for name, g in gaits.items():
        if name in ('order', 'about') or name not in walks:
            continue
        out[name] = (walks[name] / 16.0 / 20.0, g['min'], g['max'])
    return out


def animal_report(path):
    header, moments = read(path)
    plan = plan_of(header['entity'])
    gaits = gaits_of(plan)
    if not gaits or len(moments) < 2:
        return None
    planned = moments_of(plan)
    changed = []                                  # (tick, old, new, seconds into the old clip, the plan's moment)
    scale = header.get('scale', 1.0)
    size = max(0.3, scale)
    name = header.get('name') or os.path.basename(path)
    clips = {}
    changes, flickers = 0, 0
    last_gait, last_change = None, None
    previous_gait = None
    for before, now in zip(moments, moments[1:]):
        if now['t'] - before['t'] != 1:
            last_gait = None                      # a gap between bursts: nothing measured across it
            continue
        body = now['clips'].get('body', '- -').split(' ')
        clip, state = body[0], body[1] if len(body) > 1 else ''
        was = before['clips'].get('body', '- -').split(' ')[0]
        if was != clip and was in gaits and clip in gaits:
            rate, into = (before.get('play', {}).get('body') or [None, None])[:2]
            span = None if into is None else (into / 20.0, (into + (rate or 0) * (now['t'] + now['pt'] - before['t'] - before['pt'])) / 20.0)
            changed.append((now['t'], was, clip, span, planned.get((was, clip))))
        play = (now.get('play', {}).get('body') or [1.0, None])[0]
        dx, dz = now['pos'][0] - before['pos'][0], now['pos'][2] - before['pos'][2]
        elapsed = max(0.05, 1.0 + now['pt'] - before['pt'])  # ticks between the two drawings (each its tick's earliest)
        speed = math.hypot(dx, dz) / elapsed      # blocks a tick, as drawn
        gait = gaits.get(clip)
        c = clips.setdefault(clip, Clip(clip, gait, scale))
        c.ticks += 1
        if gait is None:
            if speed > GLIDE:
                c.moving += 1
            continue
        if state == 'transitioning':
            continue                              # easing between two clips: neither one's feet
        feet, low, high = gait
        c.plays.append(play)
        if abs(play - low) < 1e-3 or abs(play - high) < 1e-3:
            c.limit += 1
        foot_speed = feet * size * play           # how fast the clip moves its feet past the body, drawn at its size
        if foot_speed > 1e-6:
            c.ratios.append(speed / foot_speed)
        elif speed > GLIDE:
            c.ratios.append(math.inf)             # still feet, a moving body: gliding
        if clip != last_gait:
            if last_gait is not None:
                changes += 1
                if clip == previous_gait and last_change is not None and now['t'] - last_change <= QUICK_BACK:
                    flickers += 1
                previous_gait = last_gait
                last_change = now['t']
            last_gait = clip
    return name, header['entity'], scale, clips, changes, flickers, changed


def share(values, test):
    return sum(1 for v in values if test(v)) / len(values) if values else 0.0


def lines_for(result):
    name, entity, scale, clips, changes, flickers, changed = result
    out = [f'{name}  ({entity}, size {scale:.2f}{", counted as 0.3" if scale < 0.3 else ""})']
    for c in sorted(clips.values(), key=lambda c: -c.ticks):
        if c.gait is None:
            continue
        secs = c.ticks / 20.0
        if not c.plays:
            out.append(f'  {c.name:8} {secs:6.1f} s  (only easing in or out)')
            continue
        p = c.plays
        line = (f'  {c.name:8} {secs:6.1f} s   play {statistics.median(p):.2f} ({min(p):.2f} to {max(p):.2f}),'
                f' hurried {share(p, lambda v: v > HURRIED):4.0%}, dragged {share(p, lambda v: v < DRAGGED):4.0%},'
                f' at a limit {c.limit / len(p):4.0%}')
        finite = [r for r in c.ratios if math.isfinite(r)]
        if c.ratios:
            off = share(c.ratios, lambda r: not (1 - SLIDE <= r <= 1 + SLIDE))
            glide = share(c.ratios, lambda r: math.isinf(r))
            mid = statistics.median(finite) if finite else float('nan')
            line += f'   feet {mid:.2f}, off {off:4.0%}' + (f' (gliding {glide:.0%})' if glide else '')
        out.append(line)
    gliders = [c for c in clips.values() if c.gait is None and c.moving]
    for c in sorted(gliders, key=lambda c: -c.moving):
        out.append(f'  {c.name:8} {c.moving / 20.0:6.1f} s moving faster than {GLIDE * 20:.1f} blocks a second on a clip made in place'
                   f' (of {c.ticks / 20.0:.1f} s)')
    out.append(f'  gait changes {changes}, changed back within {QUICK_BACK / 20:.0f} s: {flickers}')
    for t, was, clip, into, at in changed:
        where = ('its clip time not recorded' if into is None
                 else f'between {into[0]:.2f} and {into[1]:.2f} s of {was} (its last moment recorded and the next)')
        out.append(f'    tick {t}: {was} -> {clip} {where}' + (f' (the plan: {at:.2f} s)' if at is not None else ' (no moment in the plan)'))
    return out


def report(folder):
    paths = sorted(glob.glob(os.path.join(folder, 'body.jsonl.gz')) + glob.glob(os.path.join(folder, 'body_*.jsonl.gz')))
    out = [f'Pace report: {folder}', '']
    found = 0
    for path in paths:
        result = animal_report(path)
        if result is None:
            continue
        found += 1
        out += lines_for(result)
        out.append('')
    if not found:
        out.append('(no recorded animal whose body plan has gaits)')
    out += ['play: the clip\'s play speed (1.0 as made). hurried/dragged: over 1.25 or under 0.8. feet: the ground\'s',
            'speed under the body against the clip\'s feet (1.0 they stay put); off: more than a fifth either way.']
    text = '\n'.join(out)
    with open(os.path.join(folder, 'pace_report.txt'), 'w', encoding='utf-8') as f:
        f.write(text + '\n')
    return text


def main(argv):
    if not argv:
        print(__doc__)
        return 1
    for folder in argv:
        if not os.path.isdir(folder):
            print(f'no folder {folder} (a film run clears run-film/screenshots before it starts)')
            continue
        print(report(folder))
        print()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
