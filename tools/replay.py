"""The replay page: an in-game film's recording played back in the browser, every animal of the scene together (the
Motion Studio, phase 1, 2026-11-27; docs/plans/motion-studio.md, ideas 3, 6 and 7).

    python tools/replay.py <scene folder> [--from T] [--to T] [--only name,name] [--out page.html]
    e.g. python tools/replay.py run-film/screenshots/lion_hunt       -> references/replay/lion_hunt.html
         python tools/replay.py run-film/screenshots/lion_hunt --from 1300 --to 1500 --only film-elder,film-calf

Reads every body*.jsonl.gz a film wrote with "record on n all" (client/BodyRecorder: each animal's bones as the game
drew them, its clips, its mind's facts, the blocks round it) and builds one page that opens with no internet: the
animals on the recorded ground, turn and zoom, scrub, step a moment, slow motion, follow an animal, and each one's
clips, play speed and facts at every moment.

The game is the one truth: nothing here knows the game's rules. The scene's line of moments and each animal's feet (on
the ground, lifted, sliding) are worked out in tools/recording.py, the one place that rule is written (the new phase
2, idea 57, 2026-11-27); the page only draws them. The page draws every box from the 8 corners the game
wrote, through the matrix the game drew its bone with; nothing is blended between moments, and a moment the game did
not draw (an animal out of the camera's view) is not shown.

--from and --to are game ticks ("ft", the tick the facts and tools/pair_report.py speak). The page's clock is the
client's own tick count ("ct", one a tick the animals moved, frozen ticks left out) plus how far into the tick the
picture was drawn, which every animal drawn in one frame shares (a film recording n moments a tick keeps all n).
Recordings made before 2026-11-27 have no "ct": then every file is laid on the game tick, where a frozen burst stepped
several ticks a picture keeps only its last moment a tick (the page says so).

How the numbers travel (all in the page, packed): each bone's 3x4 matrix as twelve 16-bit whole numbers (the turning
part and the moving part each scaled to that bone's largest value; checked on the lion hunt: every box corner within
0.0002 blocks of the game's), written as the change from the moment before, bone by bone, and squeezed with zlib (the
browser unsqueezes it with its own DecompressionStream); the clips, facts and places as squeezed JSON.
"""
import argparse
import base64
import glob
import json
import math
import sys
import zlib
from array import array
from pathlib import Path

import body_plan
import recording
import viewer_page

ROOT = Path(__file__).resolve().parent.parent
ROT = (0, 1, 2, 4, 5, 6, 8, 9, 10)          # the turning (and sizing) part of a row-major 3x4 matrix
MOVE = (3, 7, 11)                              # the moving part
LIMIT = 32000                                  # the largest whole number a value is scaled to


def plan_of(entity):
    """The body plan of an entity id (antcolonies:lioness -> lioness.json, found through tools/mod_paths.py), or {}."""
    try:
        with open(body_plan.plan_path(entity.split(':')[-1]), encoding='utf-8') as f:
            return json.load(f)
    except (OSError, ValueError):                   # no plan (a carcass), or no mod holds the model
        return {}


def pack(moments, bones):
    """The bone matrices of these moments as 16-bit changes, bone by bone and value by value (a value's moments
    together, so the squeezing finds the smooth runs), and each bone's two scales back to blocks (each bone its own,
    so one far bone, a held fish, coarsens no other). A matrix with a number that is not a number is left out (drawn
    as hidden)."""
    out, turn, move = array('h'), [], []
    for b in range(bones):
        column = [m['b'][b] for m in moments]
        column = [0 if mat == 0 or not all(math.isfinite(v) for v in mat) else mat for mat in column]
        big_turn = max([abs(mat[i]) for mat in column if mat != 0 for i in ROT] or [1.0])
        big_move = max([abs(mat[i]) for mat in column if mat != 0 for i in MOVE] or [1.0])
        s_turn, s_move = LIMIT / max(big_turn, 1e-9), LIMIT / max(big_move, 1e-9)
        turn.append(1 / s_turn)
        move.append(1 / s_move)
        for i in range(12):
            scale = s_move if i in MOVE else s_turn
            prev = 0
            for mat in column:
                v = 0 if mat == 0 else int(round(mat[i] * scale))
                d = v - prev
                out.append(d - 65536 if d > 32767 else d + 65536 if d < -32768 else d)
                prev = v
    return out, turn, move


def colour(entity, n):
    """A plain colour per kind (the recording has no paint): the cats tawny, the buffalo dark, a carcass red-brown."""
    kind = entity.split(':')[-1]
    if kind.startswith('lion'):
        return ['#c99a55', '#d6a960', '#b98a4a', '#c4a070'][n % 4]
    if kind == 'buffalo':
        return ['#5a524b', '#6a6058'][n % 2]
    if kind == 'remains':
        return '#8c5444'
    return ['#7f9c5a', '#5f8fb0', '#b0786a', '#9a7ab8', '#c0a040'][n % 5]


def r(v, n=4):
    return round(v, n) if isinstance(v, float) else v


def build(folder, start=None, end=None, only=None, out=None):
    folder = Path(folder)
    paths = sorted(glob.glob(str(folder / 'body*.jsonl.gz')))
    if not paths:
        raise SystemExit(f'no body*.jsonl.gz in {folder}: film the scene with "record on 1 all" first')
    animals, blocks, water, skipped, notes = [], {}, {}, [], []
    kept = []                                       # each shown animal's header and moments, for its feet
    blob = array('h')
    origin = None
    # each file's name (its custom name; the file's own when two share one) and whether it has the client's clock
    files, names = [], set()
    for path in paths:
        header, has_ct = recording.peek(path)
        label = Path(path).name[len('body_'):-len('.jsonl.gz')] if Path(path).name.startswith('body_') else 'walker'
        if header is None:
            skipped.append(f'{label}: an empty file (nothing was recorded)')
            continue
        name = header.get('name') or label
        if name in names:
            name = label
        names.add(name)
        if only and name not in only:
            continue
        files.append((path, name, has_ct))
    # one clock for every file: the client's when all have it, else the game tick for all (two clocks counted from
    # different starts cannot share one line)
    clock = 'ct' if files and all(has for _, _, has in files) else 'ft'
    if clock == 'ft' and any(has for _, _, has in files):
        notes.append('Some files have the client\'s clock and some not, so all are laid on the game tick: through a '
                     'frozen burst stepped several ticks a picture only the last moment of each tick is shown.')
    elif clock == 'ft':
        notes.append('Recorded without the client\'s clock (made before 2026-11-27), on the game tick alone: through a '
                     'frozen burst stepped several ticks a picture only the last moment of each tick is shown.')
    for path, name, _ in files:
        header, moments, bl, wa, trouble = recording.read(path)
        if trouble:
            notes.append(f'{name}: {trouble}.')
        if not moments:
            skipped.append(f'{name}: no moments recorded (never drawn while recording)')
            continue
        if any(m.get('ft') is None for m in moments):
            skipped.append(f'{name}: no game tick in its file (recorded before 2026-11-27 with no mind\'s facts)')
            continue
        if start is not None or end is not None:
            moments = [m for m in moments if (start is None or m['ft'] >= start) and (end is None or m['ft'] <= end)]
        if not moments:
            skipped.append(f'{name}: not drawn in the ticks asked for')
            continue
        # each moment's place on the clock: the client's tick and how far into it the picture was drawn (a film
        # recording n moments a tick has n a tick; every animal drawn in one frame shares both); on the game tick
        # alone, the last moment of each tick
        by_key = {}
        for m in moments:
            by_key[round(m['ct'] + m.get('pt', 0), 4) if clock == 'ct' else m['ft']] = m
        keys = sorted(by_key)
        moments = [by_key[k] for k in keys]
        if origin is None:
            origin = [math.floor(moments[0]['pos'][0]), math.floor(moments[0]['pos'][1]), math.floor(moments[0]['pos'][2])]
        for entry in bl:
            blocks[tuple(entry[:3])] = entry[3:]
        for entry in wa:
            water[tuple(entry[:3])] = entry[3]
        n_bones = len(header['bones'])
        mats, s_turn, s_move = pack(moments, n_bones)
        offset = len(blob)
        blob.extend(mats)
        plan = plan_of(header['entity'])
        clip_table, clip_index = [], {}
        clips = []
        for m in moments:
            key = json.dumps(m.get('clips', {}), sort_keys=True)
            if key not in clip_index:
                clip_index[key] = len(clip_table)
                clip_table.append(m.get('clips', {}))
            clips.append(clip_index[key])
        kept.append((header, moments, keys, plan))
        kind_count = sum(1 for a in animals if a['entity'] == header['entity'])
        animals.append({
            'name': name, 'entity': header['entity'], 'file': Path(path).name,
            'colour': colour(header['entity'], kind_count),
            'width': header.get('width'), 'height': header.get('height'), 'scale': header.get('scale'),
            'bones': [{'name': b['name'], 'parent': b.get('parent'), 'pivot': b.get('pivot', [0, 0, 0]),
                       'cubes': [[r(v, 5) for v in c] for c in b['cubes']]} for b in header['bones']],
            # each leg from the body outward, its role names turned into the model's own bone names (the plan's
            # "bones" table: a bone renamed in the model changes only that table)
            'legs': {k: [(plan.get('bones') or {}).get(r, r) for r in chain] for k, chain in (plan.get('legs') or {}).items()},
            'n': len(moments), 'B': n_bones, 'offset': offset, 'turn': s_turn, 'move': s_move,
            'key': keys, 'ft': [m.get('ft') for m in moments], 'pt': [r(m.get('pt', 0), 3) for m in moments],
            'pos': [[r(m['pos'][0] - origin[0]), r(m['pos'][1] - origin[1]), r(m['pos'][2] - origin[2])] for m in moments],
            'yaw': [r(m.get('yaw', 0), 2) for m in moments],
            'ground': [m.get('ground', 0) for m in moments], 'water': [m.get('water', 0) for m in moments],
            'fluid': [r(m.get('fluid', 0), 3) for m in moments], 'held': [m.get('held', 0) for m in moments],
            'v': [[r(x) for x in m.get('v', [0, 0, 0])] for m in moments],
            'clips': clips, 'clipTable': clip_table,
            'play': [m.get('play', {}) for m in moments], 'facts': [m.get('facts', {}) for m in moments],
        })
        print(f'  {name:18s} {header["entity"]:26s} {len(moments):5d} moments, {n_bones} bones, clock {clock}')
    if not animals:
        raise SystemExit('nothing to show: ' + '; '.join(skipped))
    # the scene's one line of moments and each animal's feet, worked out in tools/recording.py (the one feet rule: the
    # page only draws them; the new phase 2, idea 57)
    timeline, slots, tick_at, gap_before, since_gap = recording.line_up([k for _, _, k, _ in kept],
                                                                        [[m.get('ft') for m in ms] for _, ms, _, _ in kept])
    ground = recording.Ground([[x, y, z, *boxes] for (x, y, z), boxes in blocks.items()])
    for a, (header, moments, keys, plan), sl in zip(animals, kept, slots):
        feet = recording.feet(header, moments, recording.plan_legs(plan), ground, keys, [since_gap[t] for t in sl], origin)
        a['px'] = recording.model_px(header, moments)
        a['footData'] = [{'key': f['key'], 'name': f['name'], 'state': f['state'],
                          'slide': [None if v is None else round(v, 4) for v in f['slide']],
                          'height': [None if v is None else round(v, 3) for v in f['height']],
                          'mid': [None if v is None else [round(c, 4) for c in v] for v in f['mid']]} for f in feet]
    line = {'timeline': timeline, 'tickAt': tick_at, 'gapBefore': gap_before,
            'rule': {'gap': recording.GAP, 'downPx': recording.DOWN_PX, 'contactPx': recording.CONTACT_PX,
                     'slidePx': recording.SLIDE_PX, 'catchUp': recording.CATCH_UP}}
    if sys.byteorder != 'little':
        blob.byteswap()
    ground_list = [[x - origin[0], y - origin[1], z - origin[2], *boxes] for (x, y, z), boxes in blocks.items()]
    wet = [[x - origin[0], y - origin[1], z - origin[2], h] for (x, y, z), h in water.items()]
    meta = {'animals': animals, 'blocks': ground_list, 'water': wet, 'origin': origin, 'clock': clock, 'line': line}
    meta_bytes = zlib.compress(json.dumps(meta, separators=(',', ':')).encode('utf-8'), 9)
    mat_bytes = zlib.compress(blob.tobytes(), 9)
    scene = folder.name
    data = {'title': scene.replace('_', ' '), 'scene': str(folder), 'notes': notes, 'skipped': skipped,
            'meta': base64.b64encode(meta_bytes).decode('ascii'), 'mats': base64.b64encode(mat_bytes).decode('ascii')}
    out = Path(out) if out else ROOT / 'references' / 'replay' / f'{scene}.html'
    viewer_page.build('replay_viewer.template.html', data, out)
    print(f'{len(animals)} animals, {len(blocks)} blocks; packed {len(blob) * 2 / 1e6:.1f} MB of matrices into '
          f'{len(mat_bytes) / 1e6:.1f} MB, the rest {len(meta_bytes) / 1e6:.2f} MB; page {out.stat().st_size / 1e6:.1f} MB')
    for s in skipped:
        print('  not shown:', s)
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('folder', help='the scene\'s folder, e.g. run-film/screenshots/lion_hunt')
    ap.add_argument('--from', dest='start', type=int, help='first game tick (ft) to keep')
    ap.add_argument('--to', dest='end', type=int, help='last game tick (ft) to keep')
    ap.add_argument('--only', help='animals to keep, by name (comma-separated)')
    ap.add_argument('--out', help='the page (default references/replay/<scene>.html)')
    a = ap.parse_args(argv)
    folder = Path(a.folder) if Path(a.folder).is_absolute() else ROOT / a.folder
    build(folder, a.start, a.end, set(a.only.split(',')) if a.only else None, a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())
