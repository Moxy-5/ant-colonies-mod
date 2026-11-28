"""Picture strips of a game recording, for Claude to check a motion before the user sees it (the Motion Studio,
phase 1, 2026-11-27; docs/plans/motion-studio.md, idea 5).

    python tools/replay_strips.py <scene folder> --pick <animal> [--from T] [--to T] [--every N] [--cams a,b,c]
                                  [--fixed] [--no-paths] [--list]
    e.g. python tools/replay_strips.py run-film/screenshots/lion_hunt --pick film-elder --from 940 --to 960 --every 2

Builds the scene's replay page (tools/replay.py, afresh each run, into a folder of its own: never a page built for
something else) and takes, in one headless browser launch (tools/viewer_shot.Browser), the picked animal at every
N-th of its own recorded moments between two of its game ticks, from each camera round it: side, front and above by
default. The cameras turn with the animal and stand at one distance from it for the strip (fitted to the space it
takes up over the strip's moments), so its pictures keep one size; with --fixed each camera stays where its first
picture was taken, so the animal's own travel and turns show. Each picture shows its feet's paths over the last 1.5
seconds (dark on the ground, red sliding, grey lifted) unless --no-paths. Every other animal is drawn see-through
and casts no shadow (the user's call, 2026-11-27): a neighbour never hides the animal checked, and the shape of what
it touches, its prey or its holder, still shows. The pictures go on sheets of ten rows, each labelled with its moment, the animal's
own game tick, its clip and feet; a row where a foot slides is framed red. Under the last sheet, the page's footfall
chart for those moments.

Beside the sheets, a text file: a summary (clip changes, ticks not recorded, moments not drawn, stretches where a
foot slides, the body's biggest step), then every moment of the animal in the range, pictured or not (marked *), with
its clips, feet, the body's drawn step and turn a tick, and its mind's facts. --list writes only that text file (no
pictures: a quick way to find the ticks worth a picture).

    references/replay/strips/<scene>_<animal>_<from>-<to>_e<N>_<cams>[_p<k>].png   and   .txt

Units: px is one of the animal's own model pixels (a slide in px a tick, a foot's height in px over the ground);
"in" is how far into its clip, in clip ticks; the body's step is in blocks and degrees a tick. The pictures are the
page's own drawing of the game's numbers: what the game drew, never a re-imagining of it.
"""
import argparse
import base64
import io
import json
import re
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import replay
import viewer_shot

ROOT = Path(__file__).resolve().parent.parent
SHOT_SIZE = "1280,900"
ROWS_A_SHEET = 10
SHEET_WIDTH = 1272        # a sheet is at least this wide: one or two cameras get bigger pictures, never cut words
GAP = 3                   # game ticks not recorded worth saying (the page's own GAP)


def font(n):
    for f in ('arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(f, n)
        except OSError:
            pass
    return ImageFont.load_default()


def image(data_url):
    return Image.open(io.BytesIO(base64.b64decode(data_url.split(',', 1)[1]))).convert('RGB')


def clip_of(row):
    """The row's main clip player: its body's, else its first (a carcass has only a pose)."""
    clips = row.get('clips') or {}
    return clips.get('body') or next(iter(clips.values()), None)


def clip_words(c):
    if c is None:
        return '-'
    return (c['name'] + (f" ({c['state']})" if c['state'] not in ('', 'running') else '')
            + (f" x{c['speed']:.2f}" if c['speed'] is not None else '') + (f" {c['ticks']:.1f} in" if c['ticks'] is not None else ''))


def foot_words(f):
    if not f['drawn']:
        return f"{f['name']} not drawn"
    h = f.get('heightPx')
    sunk = f' ({-h:.1f} px into the ground)' if h is not None and h < -0.5 else ''
    if f['sliding']:
        return f"{f['name']} SLIDES {f['slidePx']:.2f} px{sunk}"
    if f['planted']:
        return f"{f['name']} down{sunk}"
    if not f['groundKnown']:
        return f"{f['name']} (no ground recorded)"
    return f"{f['name']} up {h:.1f} px" if h is not None else f"{f['name']} up"


def unrecorded_of(row):
    """Game ticks not recorded just before this moment of the animal's (0 when under the page's GAP)."""
    n = (row.get('ticksSince') or 1) - 1
    return n if n > GAP else 0


def describe(row, unrecorded=0, lines=False):
    """One moment in words: its clip, play speed, how far in, the body's step, its feet (two lines for a sheet)."""
    step = row.get('step')
    body = f"  body {step['move']:.3f} blocks {step['turn']:+.1f} deg" if step else ''
    feet = ', '.join(foot_words(f) for f in row.get('feet', []))
    head = (f"moment {row['moment']}  tick {row['tick']}  {clip_words(clip_of(row))}{body}"
            + (f"  (after {unrecorded} game ticks not recorded)" if unrecorded else ''))
    return (head, feet) if lines else head + (f'  {feet}' if feet else '')


def summary(rows, name):
    """The range in a few lines: clip changes, ticks not recorded, moments not drawn, slides, the biggest step."""
    out, last_clip, slide, missing, biggest = [], None, None, None, None

    def end_slide():
        if slide:
            out.append(f"  feet slide from tick {slide['from']} to {slide['to']} ({slide['n']} moments, during "
                       f"{', '.join(slide['clips'])}; worst {slide['worst'][1]} {slide['worst'][0]:.2f} px a tick)")

    for r in rows:
        if not r['drawn']:
            missing = missing or [r['moment'], r['moment']]
            missing[1] = r['moment']
            continue
        if missing:
            out.append(f"  {name} not drawn from moment {missing[0]} to {missing[1]}")
            missing = None
        if not r['own']:
            continue
        c = clip_of(r)
        label = c['name'] if c else '-'
        if label != last_clip:
            out.append(f"  tick {r['tick']}: {label}" + (f" ({c['state']})" if c and c['state'] not in ('', 'running') else ''))
            last_clip = label
        if unrecorded_of(r):
            out.append(f"  tick {r['tick']}: after {unrecorded_of(r)} game ticks not recorded")
        if r.get('step') and (biggest is None or r['step']['move'] > biggest[0]):
            biggest = (r['step']['move'], r['tick'])
        slides = [(f['slidePx'], f['name']) for f in r.get('feet', []) if f['sliding']]
        if slides:
            if not slide:
                slide = {'from': r['tick'], 'n': 0, 'clips': [], 'worst': (0, '')}
            slide['to'], slide['n'] = r['tick'], slide['n'] + 1
            if label not in slide['clips']:
                slide['clips'].append(label)
            slide['worst'] = max(slide['worst'], max(slides))
        elif slide:
            end_slide()
            slide = None
    end_slide()
    if missing:
        out.append(f"  {name} not drawn from moment {missing[0]} to {missing[1]}")
    if biggest:
        out.append(f"  the body's biggest step: {biggest[0]:.3f} blocks a tick, at tick {biggest[1]}")
    return out


def tile_size(n):
    pad = 6
    w = max(420, (SHEET_WIDTH - pad) // n - pad)
    return w, int(w * 574 / 1246)          # (the page's drawing is about 1246 by 574 in the default browser size)


def opened(b, page, pick, cams, start, end, ticks_after=None):
    """The replay page opened in browser b (a viewer_shot.Browser), its cameras and the animal checked, the cameras
    fitted to the animal's moments between its game ticks start and end (None: its first; None: 40 of its moments
    on, or with ticks_after that many game ticks after the first): (first, last, rows), the range's first and last
    moments (indexes) and the page's row of every moment between. Shared with tools/replay_video.py."""
    b.call('Page.navigate', url=page.resolve().as_uri() + '#labels=0')
    t0 = time.time()
    while not b.evaluate('!!(window.Replay && window.Replay.ready)'):
        failed = b.evaluate("(document.getElementById('loading')||{}).textContent || ''")
        if 'Could not' in failed or 'cannot draw' in failed or time.time() - t0 > 60:
            raise SystemExit(f'the page did not open: {failed or "no answer in 60 s"}')
        time.sleep(0.2)
    known = b.evaluate('Replay.cams()')
    wrong = [c for c in cams if c not in known]
    if wrong:
        raise SystemExit(f'no camera named {", ".join(wrong)}: the cameras are {", ".join(known)}')
    if not b.evaluate(f'Replay.has({json.dumps(pick)})'):
        raise SystemExit(f'no animal named {pick} in this recording: ' + ', '.join(b.evaluate('Replay.names()')))

    def span(to):
        got = b.evaluate(f'Replay.span({json.dumps(pick)}, {-1 if start is None else start}, {to})')
        if 'why' in got:
            raise SystemExit(got['why'])
        return got['first'], got['last']
    first, last = span(-1 if end is None else end)
    if end is None and ticks_after is not None:
        first, last = span(b.evaluate(f'Replay.rows({json.dumps(pick)}, {first}, {first})')[0]['tick'] + ticks_after)
    rows = b.evaluate(f'Replay.rows({json.dumps(pick)}, {first}, {last})')
    b.evaluate(f'Replay.fit({json.dumps(pick)}, {first}, {last})')   # (the cameras fitted to these moments)
    return first, last, rows


def take(page, pick, start, end, every, cams, fixed, paths, size, pictures=True):
    """Every moment in the range (the page's rows), the pictured ones' {cam: picture} (shrunk as taken) and the chart
    picture, in one browser launch."""
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as profile:
        b = viewer_shot.Browser(profile, size)
        try:
            first, last, rows = opened(b, page, pick, cams, start, end)
            shots, chart = {}, None
            if pictures:
                b.evaluate('Replay.hold(true)')         # (each picture is drawn by snap: the page's own loop held)
                b.evaluate(f'Replay.pick({json.dumps(pick)})')
                b.evaluate(f'Replay.traces({json.dumps(paths)}, 0)')
                b.evaluate('Replay.fadeOthers(true)')    # (every other animal see-through: none hides it)
                tile = tile_size(len(cams))
                for r in [r for r in rows if r['drawn'] and r['own']][::every]:
                    b.evaluate(f"Replay.show({r['index']})")
                    shots[r['index']] = {cam: image(b.evaluate(f'Replay.snap({json.dumps(cam)}, {json.dumps(fixed)})'))
                                         .resize(tile, Image.LANCZOS) for cam in cams}
                c = b.evaluate(f'Replay.chart({first}, {last})')
                chart = image(c) if c else None
            return rows, shots, chart
        finally:
            b.close()


def sheets(rows, shots, chart, cams, title, base):
    """The pictured moments on sheets of ten rows, the chart under the last; the files written."""
    pictured = [r for r in rows if r['index'] in shots]
    tw, th = next(iter(shots[pictured[0]['index']].values())).size
    label, pad, head = 40, 6, 34
    W = max(SHEET_WIDTH, pad + len(cams) * (tw + pad))
    pages = [pictured[i:i + ROWS_A_SHEET] for i in range(0, len(pictured), ROWS_A_SHEET)]
    # the game ticks not recorded since the pictured moment before (with --every a gap may fall between two rows)
    unrecorded, total = {}, 0
    for r in rows:
        if r['drawn'] and r['own']:
            total += unrecorded_of(r)
            if r['index'] in shots:
                unrecorded[r['index']], total = total, 0
    written = []
    for k, page in enumerate(pages):
        chart_h = int(chart.height * (W - 2 * pad) / chart.width) if chart and k == len(pages) - 1 else 0
        H = head + 22 + len(page) * (th + label + pad) + (chart_h + 30 if chart_h else 0)
        s = Image.new('RGB', (W, H), (243, 240, 234))
        d = ImageDraw.Draw(s)
        d.text((pad, 6), title + (f'  (sheet {k + 1} of {len(pages)})' if len(pages) > 1 else ''), fill=(40, 30, 25), font=font(20))
        for i, cam in enumerate(cams):
            d.text((pad + i * (tw + pad), head), cam, fill=(90, 80, 60), font=font(15))
        y = head + 22
        for r in page:
            sliding = any(f['sliding'] for f in r.get('feet', []))
            colour = (170, 40, 25) if sliding else (40, 30, 25)
            top, feet = describe(r, unrecorded.get(r['index'], 0), lines=True)
            d.text((pad, y + 1), top, fill=colour, font=font(14))
            d.text((pad, y + 19), feet, fill=colour, font=font(13))
            for i, cam in enumerate(cams):
                x = pad + i * (tw + pad)
                s.paste(shots[r['index']][cam], (x, y + label))
                if sliding:                     # (as the offline checker frames a fault)
                    d.rectangle([x - 2, y + label - 2, x + tw + 1, y + label + th + 1], outline=(210, 50, 30), width=3)
            y += th + label + pad
        if chart_h:
            d.text((pad, y + 6), 'Footfall over these moments (dark: on the ground; red: on the ground and sliding; light: '
                                 'lifted; tan: no ground recorded; hatched: not drawn; dashed blue: game ticks not recorded)',
                   fill=(40, 30, 25), font=font(14))
            s.paste(chart.resize((W - 2 * pad, chart_h), Image.LANCZOS), (pad, y + 28))
        out = base.with_name(base.name + (f'_p{k + 1}' if len(pages) > 1 else '') + '.png')
        s.save(out, optimize=True)
        written.append(out)
    return written


def text(rows, shots, title, name):
    lines = [title, 'Units: px is one of the animal\'s own model pixels (a slide in px a tick, a height in px over the '
                    'ground); "in" is how far into the clip, in clip ticks; body is its drawn step and turn a tick (a '
                    'step far over its gait\'s just after ticks not recorded is the game easing it on to where it is, '
                    'not the animal moving: its feet then read as sliding); * marks a pictured moment.', '', 'Summary:'] + (summary(rows, name) or ['  nothing to say']) + ['', 'Every moment:']
    for r in rows:
        if not r['drawn'] or not r['own']:
            continue
        main = clip_of(r)
        more = ''.join(f'  {k}: {clip_words(c)}' for k, c in (r.get('clips') or {}).items() if c is not main)
        lines.append(('* ' if r['index'] in shots else '  ') + describe(r, unrecorded_of(r)) + more
                     + '  facts ' + json.dumps(r.get('facts', {})))
    return '\n'.join(lines) + '\n'


def safe(s):
    return re.sub(r'[^\w.+-]', '_', str(s))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('folder', help="the scene's folder, e.g. run-film/screenshots/lion_hunt")
    ap.add_argument('--pick', required=True, help='the animal, by name (as the page lists them)')
    ap.add_argument('--from', dest='start', type=int, help="its first game tick (default: its first moment)")
    ap.add_argument('--to', dest='end', type=int, help='its last game tick (default: 40 of its moments on)')
    ap.add_argument('--every', type=int, default=2, help='a picture every N of its moments (default 2)')
    ap.add_argument('--cams', default='side,front,above', help='cameras round it (side, front, behind, above, '
                                                              'three-quarter, wide)')
    ap.add_argument('--fixed', action='store_true', help='each camera stays where its first picture was taken')
    ap.add_argument('--no-paths', action='store_true', help='no foot paths drawn in the pictures')
    ap.add_argument('--list', action='store_true', help='only the text file (no pictures)')
    ap.add_argument('--size', default=SHOT_SIZE, help='the browser window, "w,h"')
    a = ap.parse_args(argv)
    if a.every < 1:
        ap.error('--every must be 1 or more')
    if a.start is not None and a.end is not None and a.start > a.end:
        ap.error(f'--from {a.start} is after --to {a.end}')
    folder = Path(a.folder) if Path(a.folder).is_absolute() else ROOT / a.folder
    if not folder.is_dir():
        raise SystemExit(f'no folder {folder}')
    cams = [c.strip() for c in a.cams.split(',') if c.strip()]
    started = time.time()
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as work:
        page = Path(work) / f'{folder.name}.html'     # built afresh for this run: never a page built for something else
        replay.build(folder, out=page)
        rows, shots, chart = take(page, a.pick, a.start, a.end, a.every, cams, a.fixed, not a.no_paths, a.size,
                                  pictures=not a.list)
    own = [r for r in rows if r['drawn'] and r['own']]
    if not own:
        raise SystemExit('no moments drawn to show')
    t0, t1 = own[0]['tick'], own[-1]['tick']
    every = 'every moment' if a.every == 1 else f'every {a.every} moments'
    title = f'{folder.name}: {a.pick}, game ticks {t0} to {t1}, {every}' + (', cameras fixed' if a.fixed else '')
    base = ROOT / 'references' / 'replay' / 'strips' / safe(f'{folder.name}_{a.pick}_{t0}-{t1}_e{a.every}_{"+".join(cams)}')
    base.parent.mkdir(parents=True, exist_ok=True)
    base.with_name(base.name + '.txt').write_text(text(rows, shots, title, a.pick), encoding='utf-8')
    written = [] if a.list else sheets(rows, shots, chart, cams, title, base)
    print('wrote ' + ', '.join(str(viewer_shot.shown(p)) for p in written + [base.with_name(base.name + '.txt')])
          + f' ({len(own)} moments, {len(shots)} pictured, {len(cams)} cameras, {time.time() - started:.0f} s)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
