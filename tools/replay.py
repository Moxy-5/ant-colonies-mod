"""The replay page: an in-game film's recording played back in the browser, every animal of the scene together (the
Motion Studio, phase 1, 2026-11-27; docs/plans/motion-studio.md, ideas 3, 6 and 7).

    python tools/replay.py <scene folder> [--from T] [--to T] [--only name,name] [--out page.html]
    e.g. python tools/replay.py run-film/screenshots/lion_hunt       -> references/replay/lion_hunt.html
         python tools/replay.py run-film/screenshots/lion_hunt --from 1300 --to 1500 --only film-elder,film-calf

Reads every body*.jsonl.gz a film wrote with "record on n all" (client/BodyRecorder: each animal's bones as the game
drew them, its clips, its mind's facts, the blocks round it) and builds one page that opens with no internet: the
animals on the recorded ground, turn and zoom, scrub, step a moment, slow motion, follow an animal, and each one's
clips, play speed and facts at every moment.

The game is the one truth: nothing here knows the game's rules. The page draws every box from the 8 corners the game
wrote, through the matrix the game drew its bone with; nothing is blended between moments, and a moment the game did
not draw (an animal out of the camera's view) is not shown.

--from and --to are game ticks ("ft", the tick the facts and tools/pair_report.py speak). The page's clock is the
client's own tick count ("ct", one a tick the animals moved, frozen ticks left out), which every animal drawn in one
frame shares; recordings made before 2026-11-27 have none and fall back on the game tick, where a frozen burst stepped
several ticks a picture keeps only its last moment a tick.

How the numbers travel (all in the page, packed): each bone's 3x4 matrix as twelve 16-bit whole numbers (the turning
part and the moving part each scaled to the animal's largest value: a ten-thousandth of a block or finer), written as
the change from the moment before, bone by bone, and squeezed with zlib (the browser unsqueezes it with its own
DecompressionStream); the clips, facts and places as squeezed JSON.
"""
import argparse
import base64
import glob
import gzip
import json
import math
import os
import sys
import zlib
from array import array
from pathlib import Path

import viewer_page

ROOT = Path(__file__).resolve().parent.parent
ROT = (0, 1, 2, 4, 5, 6, 8, 9, 10)          # the turning (and sizing) part of a row-major 3x4 matrix
MOVE = (3, 7, 11)                              # the moving part
LIMIT = 32000                                  # the largest whole number a value is scaled to


def plan_of(entity):
    """The body plan of an entity id (antcolonies:lioness -> lioness.json), or {}."""
    name = entity.split(':')[-1]
    for path in glob.glob(str(ROOT / '*' / 'src' / 'main' / 'resources' / 'assets' / '*' / 'body_plans' / (name + '.json'))):
        with open(path, encoding='utf-8') as f:
            return json.load(f)
    return {}


def read(path):
    """(header, moments, blocks, water) of one animal's file; "more" lines' blocks added to the header's."""
    header, moments, blocks, water = None, [], [], []
    with gzip.open(path, 'rt', encoding='utf-8') as f:
        for line in f:
            if not line.strip():
                continue
            d = json.loads(line)
            if header is None:
                header = d
            elif 'more' in d:
                pass
            else:
                moments.append(d)
                continue
            blocks += d.get('blocks', [])
            water += d.get('water', [])
    return header, moments, blocks, water


def clock_of(moments):
    """Which clock the file has: "ct" (the client's ticks, 2026-11-27 on), else "ft" (the game tick), else None."""
    if moments and all('ct' in m for m in moments):
        return 'ct'
    if moments and all('ft' in m for m in moments):
        return 'ft'
    return None


def pack(moments, bones):
    """The bone matrices of these moments as 16-bit changes, bone by bone and value by value (a value's moments
    together, so the squeezing finds the smooth runs), and the two scales back to blocks."""
    big_turn = big_move = 1e-9
    for m in moments:
        for mat in m['b']:
            if mat == 0:
                continue
            for i in ROT:
                big_turn = max(big_turn, abs(mat[i]))
            for i in MOVE:
                big_move = max(big_move, abs(mat[i]))
    s_turn, s_move = LIMIT / big_turn, LIMIT / big_move
    out = array('h')
    for b in range(bones):
        column = [m['b'][b] for m in moments]
        for i in range(12):
            scale = s_move if i in MOVE else s_turn
            prev = 0
            for mat in column:
                v = 0 if mat == 0 else int(round(mat[i] * scale))
                d = v - prev
                out.append(d - 65536 if d > 32767 else d + 65536 if d < -32768 else d)
                prev = v
    return out, 1 / s_turn, 1 / s_move


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
    blob = array('h')
    origin = None
    clocks = set()
    for path in paths:
        header, moments, bl, wa = read(path)
        name = header.get('name') or Path(path).name[len('body_'):-len('.jsonl.gz')] or 'walker'
        if only and name not in only:
            continue
        clock = clock_of(moments)
        if clock is None:
            skipped.append(f'{name}: no shared clock in its file (recorded before 2026-11-27 with no mind\'s facts)')
            continue
        if start is not None or end is not None:
            moments = [m for m in moments if (start is None or m['ft'] >= start) and (end is None or m['ft'] <= end)]
        if not moments:
            skipped.append(f'{name}: not drawn in the ticks asked for')
            continue
        clocks.add(clock)
        by_key = {}
        for m in moments:                     # one moment a tick of the clock (the last drawn)
            by_key[m[clock]] = m
        moments = [by_key[k] for k in sorted(by_key)]
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
        kind_count = sum(1 for a in animals if a['entity'] == header['entity'])
        animals.append({
            'name': name, 'entity': header['entity'], 'file': Path(path).name,
            'colour': colour(header['entity'], kind_count),
            'width': header.get('width'), 'height': header.get('height'), 'scale': header.get('scale'),
            'bones': [{'name': b['name'], 'parent': b.get('parent'), 'pivot': b.get('pivot', [0, 0, 0]),
                       'cubes': [[r(v, 5) for v in c] for c in b['cubes']]} for b in header['bones']],
            'legs': plan.get('legs', {}),
            'n': len(moments), 'B': n_bones, 'offset': offset, 'turn': s_turn, 'move': s_move,
            'key': [m[clock] for m in moments], 'ft': [m.get('ft') for m in moments], 'pt': [r(m.get('pt', 0), 3) for m in moments],
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
    if len(clocks) > 1:
        notes.append('Some files have the client\'s clock and some only the game tick: they may sit a moment or two '
                     'apart. Film the scene again.')
    if clocks == {'ft'}:
        notes.append('Recorded without the client\'s clock (made before 2026-11-27), on the game tick alone: through a '
                     'frozen burst stepped several ticks a picture only the last moment of each tick is shown.')
    if sys.byteorder != 'little':
        blob.byteswap()
    ground = [[x - origin[0], y - origin[1], z - origin[2], *boxes] for (x, y, z), boxes in blocks.items()]
    wet = [[x - origin[0], y - origin[1], z - origin[2], h] for (x, y, z), h in water.items()]
    meta = {'animals': animals, 'blocks': ground, 'water': wet, 'origin': origin, 'clock': sorted(clocks)[0] if len(clocks) == 1 else 'mixed'}
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
