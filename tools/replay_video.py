"""A game recording as a video, for the user's eye and Neon's (the Motion Studio, phase 3 part B step 1, idea 60,
2026-11-27; docs/plans/motion-studio-phase3.md).

    python tools/replay_video.py <scene folder> --pick <animal> [--from T] [--to T] [--cams side,front,above]
                                 [--slow 4] [--fixed] [--no-paths] [--check]
    e.g. python tools/replay_video.py run-film/screenshots/feast_bench --pick film-lioness --from 130 --to 190

The scene's replay page (tools/replay.py, built afresh into a folder of its own) drawn moment by moment in one
headless browser, as tools/replay_strips.py takes its pictures: the recorded parts where the game drew them, nothing
re-imagined. The cameras round the picked animal stand side by side in one frame (fitted to the space it takes up
over the range, as the strips; --fixed: each stays where it stood at the first moment, so the animal's own travel and
turns show), every other animal see-through, the picked one's foot paths drawn (--no-paths: none). The range plays at
the game's own speed, then again --slow times slower (1: no slow pass). Under the pictures a bar: the pass, the
animal's own game tick and the seconds into the range, its clip with its play speed and how far in, each foot (dark on
the ground, red sliding with how far a tick, light lifted, tan no ground recorded), and a line along the whole range
with a red mark where any foot slides, a blue one after game ticks not recorded, and the place now. Feet are judged by
the one feet rule (tools/recording.py), as on the page and the strips.

    references/replay/video/<scene>_<animal>_<from>-<to>_<cams>[_fixed]_s<slow>.mp4

Timing: each moment is shown for the scene's clock till the next (the client's ticks: one a tick through a frozen
burst stepped a tick a picture, so the video's 1x is the game's speed), a step of more than GAP_HOLD ticks (game ticks
not recorded, a film's "wait") cut to GAP_HOLD with the bar saying so; 60 frames a second, so a moment a tick lasts 3
frames, never 1 then 2. Without --to the range is 200 of the animal's game ticks (10 s).

--check: after the video, its own check: the frames of the range's middle moment and of its moment 4 game ticks before are
read back out of the mp4 and set against tools/replay_strips.py's picture of the middle moment (the same range, so the
same cameras): the middle frame must match it (mean difference under CHECK_MATCH of 255, both shrunk alike), and
the frame 4 game ticks before must match it worse by CHECK_MARGIN (when the animal hardly moved between, the two look
alike and the check says that timing went unproven by them). The numbers are printed.

Needs ffmpeg (as tools/guard_films.py). Claude cannot watch a video: it checks the same frames as picture strips
(tools/replay_strips.py); the videos are for the user and Neon.
"""
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageStat

import replay
import replay_strips
import viewer_shot

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'references' / 'replay' / 'video'
TILE = (640, 480)          # each camera's picture: the page's drawing at this size (its canvas is the window less 34
                           # pixels wide and 64 in 100 of its height, tools/replay_viewer.template.html's #wrap)
WINDOW = f'{TILE[0] + 34},{round(TILE[1] / 0.64)}'
BAR_H = 96
FPS = 60
GAP_HOLD = 4               # a step of the clock longer than this many ticks (ticks not recorded) plays as this
END_HOLD = 0.5             # seconds the real-speed pass rests on its last moment before the slow pass
DEFAULT_TICKS = 200        # the range without --to: 10 s of the game
JPEG = 0.92                # the pictures read back from the page as JPEG of this quality (quicker than PNG)
CHECK_MATCH = 8.0          # --check: the mean difference (of 255) under which a video frame matches the strip's
CHECK_OFF = 4              # --check: the moment this many game ticks before the middle, which must match worse...
CHECK_MARGIN = 1.0         # --check: ...by at least this much (of 255), or the timing went unproven by the two
CHECK_SIZE = (320, 240)    # --check: both pictures shrunk to this before they are set side by side

DARK, INK, MUTED = (28, 26, 24), (240, 236, 228), (165, 158, 146)
RED, BLUE, AMBER = (214, 58, 40), (70, 140, 220), (232, 170, 60)
FOOT_FILL = {'down': (78, 74, 68), 'sliding': RED, 'lifted': (200, 196, 188), 'none': (190, 160, 110), 'hidden': (110, 106, 100)}


def foot_state(f):
    if not f['drawn']:
        return 'hidden'
    if f['sliding']:
        return 'sliding'
    if f['planted']:
        return 'down'
    return 'lifted' if f['groundKnown'] else 'none'


def foot_chip(f):
    s = foot_state(f)
    h = f.get('heightPx')
    if s == 'sliding':
        words = f"slides {f.get('slidePx') or 0:.2f} px"
    elif s == 'lifted':
        words = f'up {h:.1f} px' if h is not None else 'up'
    else:
        words = {'down': 'down', 'hidden': 'not drawn', 'none': 'no ground'}[s]
    return f"{f['name']} {words}", s


def pass_words(slow):
    if slow == 1:
        return 'real speed'
    return f'1/{slow:g} speed' if slow == int(slow) else f'{1 / slow:.2f}x speed'


class Bar:
    """The bar under the pictures: the same for every frame of a moment in one pass."""

    def __init__(self, width, rows, name, tick_word='game tick'):
        self.w, self.rows, self.name, self.tick_word = width, rows, name, tick_word
        self.big, self.mid, self.small = replay_strips.font(22), replay_strips.font(17), replay_strips.font(14)
        drawn = [r['tick'] for r in rows if r['drawn']]
        self.ends = (drawn[0], drawn[-1]) if drawn else (rows[0].get('sceneTick'), rows[-1].get('sceneTick'))
        n = len(rows)
        self.line = Image.new('RGB', (width - 24, 14), (60, 56, 52))       # the whole range, marks along it
        d = ImageDraw.Draw(self.line)
        for i, r in enumerate(rows):
            x = round(i * (self.line.width - 1) / max(1, n - 1))
            if r['drawn'] and any(f['sliding'] for f in r.get('feet', [])):
                d.line([x, 2, x, 11], fill=RED)
            if r.get('gapBefore', 0) > replay_strips.GAP:
                d.line([x, 0, x, 13], fill=BLUE, width=2)
            if not r['drawn']:
                d.line([x, 6, x, 7], fill=MUTED)

    def draw(self, i, slow, seconds):
        r = self.rows[i]
        bar = Image.new('RGB', (self.w, BAR_H), DARK)
        d = ImageDraw.Draw(bar)
        label = pass_words(slow)
        lw = d.textlength(label, font=self.big) + 20
        d.rounded_rectangle([10, 8, 10 + lw, 38], 6, fill=AMBER if slow != 1 else (70, 66, 60))
        d.text((20, 11), label, fill=DARK if slow != 1 else INK, font=self.big)
        x = 24 + lw
        if r['drawn']:
            c = replay_strips.clip_of(r)
            head = f"{self.name}   {self.tick_word} {r['tick']}   {seconds:.2f} s   {replay_strips.clip_words(c)}"
        else:
            head = f"{self.name} not drawn here   {self.tick_word} {r.get('sceneTick')}   {seconds:.2f} s"
        d.text((x, 13), head, fill=INK, font=self.mid)
        gap = r.get('gapBefore', 0)
        if gap > replay_strips.GAP:
            note = f'after {gap} game ticks not recorded'
            d.text((self.w - 14 - d.textlength(note, font=self.mid), 13), note, fill=BLUE, font=self.mid)
        span = f'the line: {self.tick_word}s {self.ends[0]} to {self.ends[1]}'
        right = self.w - 14 - d.textlength(span, font=self.small)
        d.text((right, 50), span, fill=MUTED, font=self.small)
        x = 10
        for f in (r.get('feet') or []) if r['drawn'] else []:
            words, s = foot_chip(f)
            w = d.textlength(words, font=self.small) + 14
            if x + w > right - 10:
                break
            d.rounded_rectangle([x, 46, x + w, 68], 5, fill=FOOT_FILL[s])
            d.text((x + 7, 49), words, fill=DARK if s in ('lifted', 'none') else INK, font=self.small)
            x += w + 6
        bar.paste(self.line, (12, 76))
        cx = 12 + round(i * (self.line.width - 1) / max(1, len(self.rows) - 1))
        d.polygon([(cx - 5, 74), (cx + 5, 74), (cx, 80)], fill=INK)
        d.line([cx, 76, cx, 89], fill=INK, width=2)
        return bar


def times_of(rows):
    """Each moment's start in the range's own seconds (the scene's clock, steps over GAP_HOLD ticks cut to it) and how
    long it lasts (to the next; the last a tick)."""
    starts, t = [0.0], 0.0
    for a, b in zip(rows, rows[1:]):
        t += min(max(b['clock'] - a['clock'], 0.0), GAP_HOLD) / 20
        starts.append(t)
    lengths = [b - a for a, b in zip(starts, starts[1:])] + [1 / 20]
    return starts, lengths


def take(page, a, cams, frames):
    """Open the page, draw every moment of the range from each camera and lay out its frames (one per pass) in the
    folder frames; returns (rows, passes, what drew them). Drawn on the graphics card where the browser finds one
    (about 20 times quicker), else in software; the page's own drawing loop held, each picture drawn once."""
    for gpu in (True, False):
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as profile:
            b = viewer_shot.Browser(profile, WINDOW, gpu=gpu)
            try:
                try:
                    first, last, rows = replay_strips.opened(b, page, a.pick, cams, a.start, a.end,
                                                             ticks_after=DEFAULT_TICKS if a.end is None else None,
                                                             fit_with=a.fit_with)
                except SystemExit as e:
                    if gpu and 'did not open' in str(e):
                        continue                    # (no 3D on the graphics card here: the software drawing)
                    raise
                drew = b.evaluate("(() => { const g = document.createElement('canvas').getContext('webgl'), "
                                  "e = g && g.getExtension('WEBGL_debug_renderer_info'); "
                                  "return e ? g.getParameter(e.UNMASKED_RENDERER_WEBGL) : 'unknown'; })()")
                b.evaluate('Replay.hold(true)')
                b.evaluate(f'Replay.pick({json.dumps(a.pick)})')
                b.evaluate(f'Replay.traces({json.dumps(not a.no_paths)}, 0)')
                b.evaluate('Replay.fadeOthers(true)')
                width = TILE[0] * (len(cams) + (1 if a.side else 0))
                bar = Bar(width, rows, a.title or a.pick, a.tick_word)
                starts, _ = times_of(rows)
                passes = [1.0] + ([a.slow] if a.slow != 1 else [])
                label = replay_strips.font(16)
                snaps = json.dumps(cams) + f'.map(c => Replay.snap(c, {json.dumps(a.fixed)}, {JPEG}))'
                for i, r in enumerate(rows):
                    shots = b.evaluate(f"(() => {{ Replay.show({r['index']}); return {snaps}; }})()")
                    pictures = Image.new('RGB', (width, TILE[1] + BAR_H), DARK)
                    for k, cam in enumerate(cams):
                        shot = replay_strips.image(shots[k])
                        if shot.size != TILE:
                            shot = shot.resize(TILE, Image.LANCZOS)
                        d = ImageDraw.Draw(shot)
                        d.rounded_rectangle([6, 6, 16 + d.textlength(cam, font=label), 30], 5, fill=DARK)
                        d.text((11, 9), cam, fill=INK, font=label)
                        pictures.paste(shot, (k * TILE[0], 0))
                        if k:
                            ImageDraw.Draw(pictures).line([k * TILE[0], 0, k * TILE[0], TILE[1]], fill=DARK, width=2)
                    if a.side:                  # one more picture beside the cameras (a real animal's footage)
                        x = len(cams) * TILE[0]
                        pictures.paste(a.side.frame(r), (x, 0))
                        ImageDraw.Draw(pictures).line([x, 0, x, TILE[1]], fill=DARK, width=2)
                    for p, slow in enumerate(passes):
                        pictures.paste(bar.draw(i, slow, starts[i]), (0, TILE[1]))
                        pictures.save(frames / f'p{p}_{i:05d}.jpg', quality=93)
                return rows, passes, drew
            finally:
                b.close()
    raise SystemExit('the page could not draw in either browser')


def encode(frames, rows, passes, out):
    """The frames into the mp4: each pass after the other, each moment for its time (times the pass's slowness)."""
    _, lengths = times_of(rows)
    lines = []
    for p, slow in enumerate(passes):
        for i, length in enumerate(lengths):
            hold = END_HOLD if (p == 0 and len(passes) > 1 and i == len(lengths) - 1) else 0
            lines.append(f"file '{(frames / f'p{p}_{i:05d}.jpg').as_posix()}'\nduration {length * slow + hold:.6f}\n")
    lines.append(f"file '{(frames / f'p{len(passes) - 1}_{len(lengths) - 1:05d}.jpg').as_posix()}'\n")
    listing = frames / 'frames.txt'
    listing.write_text(''.join(lines), encoding='utf-8')
    done = subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', str(listing),
                           '-vf', f'fps={FPS},format=yuv420p', '-c:v', 'libx264', '-crf', '22', '-preset', 'medium',
                           '-movflags', '+faststart', str(out)], capture_output=True, text=True)
    if done.returncode or not out.exists():
        raise SystemExit(f'ffmpeg could not make the video: {done.stderr.strip()[-600:]}')


def frame_at(video, seconds, work, name):
    """The video's frame shown at this time, as a picture."""
    path = work / f'{name}.png'
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', f'{seconds:.4f}', '-i', str(video), '-frames:v', '1',
                    str(path)], check=True)
    return Image.open(path).convert('RGB')


def difference(a, b):
    a, b = a.resize(CHECK_SIZE, Image.LANCZOS), b.resize(CHECK_SIZE, Image.LANCZOS)
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3


def check(page, a, cams, rows, video, work):
    """The video's own check (see the top of this file): True when it passed."""
    own = [k for k, r in enumerate(rows) if r['drawn'] and r['own']]
    if len(own) < 3 or rows[own[-1]]['tick'] - rows[own[0]]['tick'] < 2 * CHECK_OFF:
        print(f'check: the range is too short to check (under {2 * CHECK_OFF} game ticks of its own moments)')
        return False
    m = own[len(own) // 2]
    off = max((k for k in own if rows[k]['tick'] <= rows[m]['tick'] - CHECK_OFF), default=own[0])
    every = own.index(m)               # (the strips picture the range's own moments 0, every, 2 every...: so m)
    t0, t1 = rows[own[0]]['tick'], rows[own[-1]]['tick']
    _, shots, _ = replay_strips.take(page, a.pick, t0, t1, every, cams[:1], a.fixed, not a.no_paths, WINDOW)
    strip = shots.get(rows[m]['index'], {}).get(cams[0])
    if strip is None:
        print('check: the strips did not picture the middle moment')
        return False
    starts, lengths = times_of(rows)
    tile = (0, 0, TILE[0], TILE[1])
    mid = frame_at(video, starts[m] + lengths[m] / 2, work, 'mid').crop(tile)
    near = frame_at(video, starts[off] + lengths[off] / 2, work, 'off').crop(tile)
    d_mid, d_off = difference(mid, strip), difference(near, strip)
    words = (f"check: tick {rows[m]['tick']}'s frame against the strip's picture of it: {d_mid:.2f} of 255 apart "
             f"(match under {CHECK_MATCH:g}); tick {rows[off]['tick']}'s frame: {d_off:.2f}")
    if d_mid >= CHECK_MATCH:
        print(words + ': FAILED, the frame does not match its moment')
        return False
    if d_off < d_mid + CHECK_MARGIN:
        print(words + ': the picture matches, but the animal looks nearly the same at both ticks, so the timing '
                      'went unproven by them')
        return True
    print(words + ': passed')
    return True


def make(folder, pick, start=None, end=None, cams=('side', 'front', 'above'), slow=4.0, fixed=False, no_paths=False,
         check_it=False, out=None, tick_word='game tick', title=None, fit_with=(), side=None):
    """One video of a scene folder's recordings (the command below, callable: tools/clip_video.py makes its offline
    recordings and hands them here, so a clip off the files and a game film are drawn by one drawing). out: the mp4's
    path (default references/replay/video/<made name>.mp4); tick_word names the bar's clock ("clip tick" offline);
    title the animal's name on the bar (default its name in the recording); fit_with: the cameras take in these
    animals (names) too, not the picked animal alone (an offline pair or scene). Returns (the mp4, its check passed)."""
    if slow < 1:
        raise SystemExit('--slow must be 1 or more')
    if start is not None and end is not None and start > end:
        raise SystemExit(f'--from {start} is after --to {end}')
    if not shutil.which('ffmpeg'):
        raise SystemExit('ffmpeg is needed for the video and was not found')
    folder = Path(folder) if Path(folder).is_absolute() else ROOT / folder
    if not folder.is_dir():
        raise SystemExit(f'no folder {folder}')
    cams = [c.strip() for c in cams if c.strip()]
    if not cams:
        raise SystemExit('name at least one camera')
    a = argparse.Namespace(pick=pick, start=start, end=end, slow=slow, fixed=fixed, no_paths=no_paths,
                           tick_word=tick_word, title=title, fit_with=fit_with, side=side)
    started = time.time()
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as work:
        work = Path(work)
        page = work / f'{folder.name}.html'
        replay.build(folder, out=page)
        frames = work / 'frames'
        frames.mkdir()
        rows, passes, drew = take(page, a, cams, frames)
        drawn = [r for r in rows if r['drawn']]
        if not drawn:
            raise SystemExit('no moments drawn to show')
        t0, t1 = drawn[0]['tick'], drawn[-1]['tick']
        if out is None:
            name = replay_strips.safe(f'{folder.name}_{pick}_{t0}-{t1}_{"+".join(cams)}'
                                      + ('_fixed' if fixed else '') + f'_s{slow:g}')
            out = OUT / f'{name}.mp4'
        out = Path(out)
        out.parent.mkdir(parents=True, exist_ok=True)
        encode(frames, rows, passes, out)
        starts, lengths = times_of(rows)
        seconds = (starts[-1] + lengths[-1]) * sum(passes) + (END_HOLD if len(passes) > 1 else 0)
        made = time.time() - started
        print(f'wrote {viewer_shot.shown(out)} ({len(rows)} moments, {tick_word}s {t0} to {t1}, {len(cams)} camera{"s" if len(cams) > 1 else ""}, '
              f'{seconds:.1f} s of video, {out.stat().st_size // 1024} KB, made in {made:.0f} s, drawn by {drew})')
        ok = check(page, a, cams, rows, out, work) if check_it else True
        if side:                        # its own check, every time: the footage shown where it should be
            starts, lengths = times_of(rows)
            ok = side.check(lambda s, n: frame_at(out, s, work, n), rows, starts, lengths,
                            (len(cams) * TILE[0], 0, (len(cams) + 1) * TILE[0], TILE[1])) and ok
    return out, ok


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('folder', help="the scene's folder, e.g. run-film/screenshots/feast_bench")
    ap.add_argument('--pick', required=True, help='the animal, by name (as the page lists them)')
    ap.add_argument('--from', dest='start', type=int, help="its first game tick (default: its first moment)")
    ap.add_argument('--to', dest='end', type=int, help=f'its last game tick (default: {DEFAULT_TICKS} ticks on)')
    ap.add_argument('--cams', default='side,front,above', help='cameras round it, side by side (side, front, behind, '
                                                              'above, three-quarter, wide)')
    ap.add_argument('--slow', type=float, default=4, help='the second pass this many times slower (1: none)')
    ap.add_argument('--fixed', action='store_true', help='each camera stays where it stood at the first moment')
    ap.add_argument('--no-paths', action='store_true', help='no foot paths drawn')
    ap.add_argument('--check', action='store_true', help="the video's own check against the strips' picture")
    ap.add_argument('--out', help='the mp4 to write (default references/replay/video/<made name>.mp4)')
    a = ap.parse_args(argv)
    if a.slow < 1:
        ap.error('--slow must be 1 or more')
    if a.start is not None and a.end is not None and a.start > a.end:
        ap.error(f'--from {a.start} is after --to {a.end}')
    _, ok = make(a.folder, a.pick, a.start, a.end, a.cams.split(','), a.slow, a.fixed, a.no_paths, a.check, a.out)
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
