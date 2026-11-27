"""Is the offline pose the game's pose? (code review R12-22, 2026-11-27; the engine's rule 1: a rule that also lives in
Python gets a match check proving it gives the game's result.)

tools/clip_check.py poses every clip itself (keyframes, signs and order, pivots, the hinged trunk) and every offline
finding rests on that. This measures it against the game's own record: an animal named film:<clip> stands still playing
one clip, the camera's recorder (BodyRecorder, "record on") writes every bone's drawn matrix and how far into the clip
its player was, and here the same clip is posed offline at that moment and every box corner set beside where the game
drew it.

    python tools/pose_match.py <animal> [clips...]     films those clips (all of the plan's "switches" clips when
                                                       none are named) and compares; one launch of the game
    python tools/pose_match.py --read <scene folder>   compares the recordings already in a film's folder

A part over FLAG model pixels from where the game drew it is FLAGGED (exit 2). What it cannot see: an animal that moves
(only one standing in place is compared: the offline clip has no place in the world), and the moments a clip is blending
in. Parts the game moves on top of the clip (a dragged tail, a head turned to look) show as differences: that is the
point (R12-70 and R12-71 were such layers, drawn in the game and unknown offline). A layer that is known and wanted is
named in the body plan, "checks": {"game_turns": ["neck"]} (the otter's neck and head turn to what caught its eye): those
bones and everything on them are left out of the fit and the count, and said apart.
"""
import glob
import gzip
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import clip_check as cc  # noqa: E402

FLAG = 0.2           # model pixels (the review's pick)
TICKS = 24           # game ticks filmed of each clip
SCENE = '_pose_match'


def read(path):
    with gzip.open(path, 'rt', encoding='utf-8') as f:
        lines = [json.loads(line) for line in f if line.strip()]
    return lines[0], [line for line in lines[1:] if 'b' in line]


def apply(m, p, o=(0.0, 0.0, 0.0)):
    x, y, z = p
    return (m[0] * x + m[1] * y + m[2] * z + m[3] + o[0], m[4] * x + m[5] * y + m[6] * z + m[7] + o[1],
            m[8] * x + m[9] * y + m[10] * z + m[11] + o[2])


def fit(pairs):
    """The turn about the up axis, size and shift that best lay the offline points (model px) on the game's (blocks):
    least squares on pairs of (offline, game) points, tried plain and mirrored left to right (the game mirrors a
    model's x). Returns a function from an offline point to its place in the game's frame."""
    best = None
    for mirror in (1.0, -1.0):
        ps = [complex(mirror * p[0], p[2]) for p, _ in pairs]
        ws = [complex(w[0], w[2]) for _, w in pairs]
        n = len(pairs)
        pm, wm = sum(ps) / n, sum(ws) / n
        den = sum(abs(p - pm) ** 2 for p in ps) or 1e-12
        a = sum((w - wm) * (p - pm).conjugate() for p, w in zip(ps, ws)) / den
        b = wm - a * pm
        err = sum(abs(a * p + b - w) ** 2 for p, w in zip(ps, ws))
        if best is None or err < best[0]:
            best = (err, mirror, a, b)
    _, mirror, a, b = best
    ys = [(p[1], w[1]) for p, w in pairs]
    ym, wm = sum(y for y, _ in ys) / len(ys), sum(w for _, w in ys) / len(ys)
    den = sum((y - ym) ** 2 for y, _ in ys) or 1e-12
    s = sum((y - ym) * (w - wm) for y, w in ys) / den
    c = wm - s * ym

    def place(p):
        q = a * complex(mirror * p[0], p[2]) + b
        return (q.real, s * p[1] + c, q.imag)
    return place, abs(a)


def on_top(animal):
    """The bones the plan says the game turns on top of the clip, and everything on them."""
    roots = set(animal.checks.get('game_turns', []))
    out = set()
    for bone in animal.model.by:
        p = bone
        while p:
            if p in roots:
                out.add(bone)
                break
            p = animal.model.by.get(p, {}).get('parent')
    return out


def compare(path):
    """One recording against the offline pose. Returns (lines, flagged)."""
    header, frames = read(path)
    name = header['entity'].split(':')[-1]
    label = header.get('name', name)
    try:
        animal = cc.Animal(name)
    except (OSError, KeyError):
        return [f'{label}: no body plan for {header["entity"]}, not compared'], False
    names = [b['name'] for b in header['bones']]
    cubes = [[[c[i:i + 3] for i in range(0, 24, 3)] for c in b['cubes']] for b in header['bones']]
    # the moments its body player runs a clip the files have, standing in place, not held by another
    start = frames[0]['pos'] if frames else None
    by_clip = {}
    for f in frames:
        state = f.get('clips', {}).get('body', '- -').split()
        played = (f.get('play', {}).get('body') or [None, None])[1]
        if len(state) != 2 or state[1] != 'running' or played is None or state[0] not in animal.data or f.get('held'):
            continue
        if math.dist(f['pos'], start) > 0.01:
            continue
        by_clip.setdefault(state[0], []).append((played / 20.0, f))
    if not by_clip:
        return [f'{label}: no moment of a clip running on an animal standing in place, not compared'], False
    lines, flagged = [], False
    for clip, moments in by_clip.items():
        pl = cc.Played(animal, clip)
        looped = animal.data[clip].get('loop') in (True, 'loop', 'true')
        rows = []
        for t, f in moments:
            t = t % pl.length if looped and pl.length > 0 else min(t, pl.length)
            offline = {}
            for bone, cs, _ in pl.posed(t):
                offline.setdefault(bone, []).extend(cs)
            game = {}
            for i, bone in enumerate(names):
                m = f['b'][i]
                if m != 0 and cubes[i]:
                    game[bone] = [apply(m, p) for cube in cubes[i] for p in cube]
            rows.append((t, f, offline, game))
        turned = on_top(animal)
        shared = sorted((set(rows[0][2]) & set(rows[0][3])) - turned)
        layered = sorted((set(rows[0][2]) & set(rows[0][3])) & turned)
        if len(shared) < 3:
            lines.append(f'{label} {clip}: under 3 parts in both the files and the game, not compared')
            continue
        mean = lambda pts: tuple(sum(p[k] for p in pts) / len(pts) for k in range(3))
        pairs = [(mean(off[b]), mean(game[b])) for _, _, off, game in rows for b in shared if b in off and b in game]
        place, size = fit(pairs)
        px = size                                            # blocks to a model pixel, as fitted
        worst = {}
        for t, f, off, game in rows:
            for b in shared + layered:
                if b not in off or b not in game:
                    continue
                placed = [place(p) for p in off[b]]
                far = max(min(math.dist(g, q) for q in placed) for g in game[b]) / px
                if far > worst.get(b, (0, 0, 0))[0]:
                    worst[b] = (far, t, f['t'])
        apart = max((worst.pop(b)[0] for b in layered if b in worst), default=None)
        top = sorted(worst.items(), key=lambda e: -e[1][0])
        bad = [b for b, w in top if w[0] > FLAG]
        flagged |= bool(bad)
        only = sorted(set(rows[0][3]) - set(rows[0][2]))
        lines.append(f'{label} {clip}: {len(rows)} moments, {len(shared)} parts, a model pixel {px:.4f} blocks; '
                     f'farthest from the game\'s place (px, at clip s): '
                     + ', '.join(f'{b} {w[0]:.2f} at {w[1]:.2f}' for b, w in top[:5])
                     + (f'  FLAGGED ({len(bad)} parts over {FLAG})' if bad else '')
                     + (f'; drawn only in the game: {", ".join(only[:6])}' if only else '')
                     + (f'; turned by the game on top, not counted (its plan says so): {len(layered)} parts, '
                        f'up to {apart:.2f} px' if apart is not None else ''))
    return lines, flagged


def read_folder(folder):
    paths = sorted(glob.glob(os.path.join(folder, 'body*.jsonl.gz')))
    if not paths:
        print(f'pose_match: no recording in {folder}')
        return 1
    flagged = False
    for path in paths:
        lines, bad = compare(path)
        flagged |= bad
        print('\n'.join(lines))
    print('pose_match: ' + ('the offline pose is NOT the game\'s somewhere (FLAGGED above)' if flagged
                            else 'the offline pose is the game\'s in every moment compared'))
    return 2 if flagged else 0


def scene(animal, clips):
    """A shot list: each clip on its own still animal named film:<clip>, recorded a picture a tick."""
    entity = animal.plan_data.get('checks', {}).get('entity', f'antcolonies:{animal.name}')
    water = any(animal.kind(c) == 'water' for c in clips)
    out = ['# written by tools/pose_match.py: each clip on a still animal, recorded (code review R12-22)',
           'cmd time set noon', 'cmd weather clear', 'cmd kill @e[type=!player]',
           'cmd fill 0 -61 0 40 -50 34 minecraft:air', 'cmd fill 0 -62 0 40 -61 34 minecraft:grass_block']
    if water:
        out += ['cmd fill 14 -61 2 28 -61 12 minecraft:stone', 'cmd fill 15 -60 3 27 -57 11 minecraft:water',
                'cmd fill 14 -60 2 28 -57 2 minecraft:glass', 'cmd fill 14 -60 12 28 -57 12 minecraft:glass',
                'cmd fill 14 -60 2 14 -57 12 minecraft:glass', 'cmd fill 28 -60 2 28 -57 12 minecraft:glass']
    out += ['lens 40', 'wait 40']
    for clip in clips:
        y = -58.5 if animal.kind(clip) == 'water' else -60
        z = 7.0 if animal.kind(clip) == 'water' else 22.0
        out += ['cmd tick unfreeze', 'cmd kill @e[type=!player]', 'wait 25',
                f'cmd summon {entity} 20.5 {y} {z} {{NoAI:1b,NoGravity:1b,Invulnerable:1b,Rotation:[90f,0f],'
                f'CustomName:\'"film:{clip}"\'}}',
                f'camera 20.5 {y + 1.0} {z - 6.0} 0 8', 'wait 30', 'cmd tick freeze', 'record on 1 all',
                f'burst {clip} {TICKS} 1', 'record off']
    out.append('cmd tick unfreeze')
    return '\n'.join(out) + '\n'


def main(argv):
    if not argv:
        print(__doc__)
        return 1
    if argv[0] == '--read':
        return read_folder(argv[1])
    animal = cc.Animal(argv[0])
    clips = argv[1:] or sorted({c for pair in animal.checks.get('switches', []) for c in pair[:2] if c in animal.data})
    unknown = [c for c in clips if c not in animal.data]
    if unknown or not clips:
        print(f'pose_match: {animal.name} has no clip ' + ', '.join(unknown) if unknown else 'pose_match: no clips named')
        return 1
    import film_run
    film = os.path.join(PROJECT, 'run-film', SCENE + '.film')
    os.makedirs(os.path.dirname(film), exist_ok=True)
    with open(film, 'w', encoding='utf-8', newline='\n') as f:
        f.write(scene(animal, clips))
    with open(os.path.join(PROJECT, 'run-film', SCENE + '.log'), 'w', encoding='utf-8', errors='replace') as log:
        code, _ = film_run.run([film], PROJECT, stdout=log)
    if code != 0:
        print(f'pose_match: the film did not run (exit {code}; run-film/{SCENE}.log)')
        return 1
    return read_folder(os.path.join(PROJECT, 'run-film', 'screenshots', SCENE))


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
