"""Reading the game's body recordings: the one reader (the Motion Studio, phase 1, 2026-11-27).

client/BodyRecorder writes, while a film's "record on n [all]" is in force, one gzipped file an animal
(run-film/screenshots/<scene>/body.jsonl.gz for the walker, body_<name>.jsonl.gz for each other animal), one JSON line
a moment. The first line is the header (the animal's kind, name, size, each bone with its parent, pivot and boxes as 8
corners in the bone's frame, and the blocks round its first place); a line {"more": 1, "blocks": [...], "water": [...]}
adds the blocks round a new place it went on to (R12-48); every other line is a moment (t, pt, pos, yaw, clips, play,
facts, ft the game tick, ct the client's tick count, b each bone's 3x4 matrix or 0 for a hidden bone).

tools/replay.py, tools/pair_report.py and tools/body_course.py read the files through here, so the format is read in
one place.

    header, moments, blocks, water, trouble = read(path)
        blocks: every [x, y, z, box, ...] entry of the header and the "more" lines, in order; water: every [x, y, z,
        height]; trouble: None, or why the file stopped early (a game closed before "record off" leaves a cut file:
        the moments before the cut are kept)
    header, has_client_clock = peek(path)          the header and whether the moments carry "ct", without reading on
"""
import gzip
import json


def lines_of(path):
    """Each JSON line of a file; then, if it stopped early, (None, why)."""
    try:
        with gzip.open(path, 'rt', encoding='utf-8') as f:
            for line in f:
                if line.strip():
                    yield json.loads(line)
    except (EOFError, OSError, ValueError) as e:      # a cut gzip stream, a broken line (JSONDecodeError)
        yield None, f'the file ends early ({type(e).__name__}): the moments before the cut are kept'


def peek(path):
    """(header, whether its moments carry the client's clock "ct") from the file's first lines; header None if empty."""
    header = None
    for d in lines_of(path):
        if isinstance(d, tuple):
            break
        if header is None:
            header = d
        elif 'more' not in d:
            return header, 'ct' in d
    return header, False


def read(path):
    """(header, moments, blocks, water, trouble) of one animal's file (see the top of this file)."""
    header, moments, blocks, water, trouble = None, [], [], [], None
    for d in lines_of(path):
        if isinstance(d, tuple):
            trouble = d[1]
            break
        if header is None:
            header = d
        elif 'more' not in d:
            moments.append(d)
            continue
        blocks += d.get('blocks', [])
        water += d.get('water', [])
    return header, moments, blocks, water, trouble


def clock(moment):
    """A moment's time in ticks for spacing moments: the client's tick count and the in-between time when it has
    them (every animal drawn in one frame shares both; it goes on one a tick through a frozen burst stepped several
    ticks a picture), else the game tick and the in-between time (recordings made before 2026-11-27)."""
    return (moment['ct'] if 'ct' in moment else moment['ft']) + moment.get('pt', 0.0)
