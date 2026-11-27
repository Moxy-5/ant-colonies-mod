"""Picture strips of a game recording, for Claude to check a motion before the user sees it (the Motion Studio,
phase 1, 2026-11-27; docs/plans/motion-studio.md, idea 5).

    python tools/replay_strips.py <scene folder> --pick <animal> [--from T] [--to T] [--every N] [--cams a,b,c]
    e.g. python tools/replay_strips.py run-film/screenshots/lion_hunt --pick film-elder --from 1455 --to 1500 --every 3

Builds the scene's replay page (tools/replay.py; again only when a recording is newer than the page) and takes, in one
headless browser launch (tools/viewer_shot.Browser), the picked animal at every N-th moment between the two game
ticks from each camera round it (default side, front and above; the page's cameras turn with the animal, as the
offline checker's strips do), and lays them on one sheet: a row a moment, labelled with its moment, game tick, clip
and feet, the page's footfall chart for those moments under them. Beside the sheet, a text file with every moment's
clips, play speed, feet and facts, so a check can be read as well as seen.

    references/replay/strips/<scene>_<animal>_<from>-<to>.png   and   .txt

The pictures are the page's own drawing of the game's numbers: what the game drew, never a re-imagining of it.
"""
import argparse
import base64
import io
import json
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import replay
import viewer_shot

ROOT = Path(__file__).resolve().parent.parent
SHOT_SIZE = "1280,900"


def font(n):
    for f in ('arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(f, n)
        except OSError:
            pass
    return ImageFont.load_default()


def page_for(folder):
    """The scene's replay page, built again when any recording is newer than it."""
    page = ROOT / 'references' / 'replay' / f'{folder.name}.html'
    newest = max((p.stat().st_mtime for p in folder.glob('body*.jsonl.gz')), default=0)
    template = (ROOT / 'tools' / 'replay_viewer.template.html').stat().st_mtime
    if not page.exists() or page.stat().st_mtime < max(newest, template):
        replay.build(folder)
    return page


def image(data_url):
    return Image.open(io.BytesIO(base64.b64decode(data_url.split(',', 1)[1]))).convert('RGB')


def take(page, pick, start, end, every, cams, size):
    """[(moment's state, {cam: picture})], the chart picture, in one browser launch."""
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as profile:
        b = viewer_shot.Browser(profile, size)
        try:
            b.call('Page.navigate', url=page.resolve().as_uri() + f'#pick={pick}&labels=0')
            t0 = time.time()
            while not b.evaluate('!!(window.Replay && window.Replay.ready)'):
                failed = b.evaluate("(document.getElementById('loading')||{}).textContent || ''")
                if 'Could not' in failed or time.time() - t0 > 60:
                    raise SystemExit(f'the page did not open: {failed or "no answer in 60 s"}')
                time.sleep(0.2)
            if not b.evaluate(f'Replay.has({json.dumps(pick)})'):
                raise SystemExit(f'no animal named {pick} in {page.name}: ' + ', '.join(b.evaluate('Replay.names()')))
            span = b.evaluate(f'Replay.span({json.dumps(pick)}, {start if start is not None else -1}, '
                              f'{end if end is not None else -1})')
            if not span:
                raise SystemExit(f'{pick} is not drawn between game ticks {start} and {end}')
            first, last = span
            rows = []
            for t in range(first, last + 1, every):
                state = b.evaluate(f'Replay.show({t}), Replay.about({json.dumps(pick)})')
                if not state or not state.get('drawn'):
                    continue
                pics = {cam: image(b.evaluate(f'Replay.snap({json.dumps(cam)})')) for cam in cams}
                rows.append((state, pics))
            chart = b.evaluate(f'Replay.chart({first}, {last})')
            return rows, image(chart) if chart else None
        finally:
            b.close()


def describe(state):
    """One moment in words: its clip, play speed and feet (a slide in the animal's model pixels a tick)."""
    feet = ', '.join(f"{f['name']} " + (f"slides {f['slidePx']:.2f} px" if f['sliding'] else 'down' if f['planted']
                                        else 'up' if f['groundKnown'] else 'no ground')
                     for f in state.get('feet', []))
    gap = state.get('gapBefore') or 0
    return (f"moment {state['moment']}  tick {state['tick']}  {state['clip']} x{state['speed']:.2f}"
            + (f"  {state['clipTicks']:.1f} in" if state.get('clipTicks') is not None else '')
            + (f"  (after {gap:.0f} game ticks not recorded)" if gap > 3 else '') + (f'  {feet}' if feet else ''))


def sheet(rows, chart, cams, title, out):
    tw = 420
    first = next(iter(rows[0][1].values()))
    th = int(tw * first.height / first.width)
    label, pad, head = 22, 6, 34
    W = pad + len(cams) * (tw + pad)
    chart_h = int(chart.height * (W - 2 * pad) / chart.width) if chart else 0
    H = head + 22 + len(rows) * (th + label + pad) + (chart_h + 30 if chart else 0)
    s = Image.new('RGB', (W, H), (243, 240, 234))
    d = ImageDraw.Draw(s)
    d.text((pad, 6), title, fill=(40, 30, 25), font=font(20))
    for i, cam in enumerate(cams):
        d.text((pad + i * (tw + pad), head), cam, fill=(90, 80, 60), font=font(15))
    y = head + 22
    for state, pics in rows:
        d.text((pad, y + 2), describe(state), fill=(40, 30, 25), font=font(14))
        for i, cam in enumerate(cams):
            s.paste(pics[cam].resize((tw, th), Image.LANCZOS), (pad + i * (tw + pad), y + label))
        y += th + label + pad
    if chart:
        d.text((pad, y + 6), 'Footfall over these moments (dark: on the ground; red: on the ground and sliding; light: '
                             'lifted; tan: no ground recorded; hatched: not drawn; dashed blue: game ticks not recorded)',
               fill=(40, 30, 25), font=font(14))
        s.paste(chart.resize((W - 2 * pad, chart_h), Image.LANCZOS), (pad, y + 28))
    out.parent.mkdir(parents=True, exist_ok=True)
    s.save(out, optimize=True)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('folder', help="the scene's folder, e.g. run-film/screenshots/lion_hunt")
    ap.add_argument('--pick', required=True, help='the animal, by name (as the page lists them)')
    ap.add_argument('--from', dest='start', type=int, help='first game tick (default: its first moment)')
    ap.add_argument('--to', dest='end', type=int, help='last game tick (default: 40 moments on)')
    ap.add_argument('--every', type=int, default=2, help='a picture every N moments (default 2)')
    ap.add_argument('--cams', default='side,front,above', help='cameras round it (side, front, behind, above, '
                                                              'three-quarter, wide)')
    ap.add_argument('--size', default=SHOT_SIZE, help='the browser window, "w,h"')
    a = ap.parse_args(argv)
    folder = Path(a.folder) if Path(a.folder).is_absolute() else ROOT / a.folder
    page = page_for(folder)
    cams = a.cams.split(',')
    started = time.time()
    rows, chart = take(page, a.pick, a.start, a.end, max(1, a.every), cams, a.size)
    if not rows:
        raise SystemExit('no moments drawn to show')
    t0, t1 = rows[0][0]['tick'], rows[-1][0]['tick']
    out = ROOT / 'references' / 'replay' / 'strips' / f'{folder.name}_{a.pick}_{t0}-{t1}.png'
    sheet(rows, chart, cams, f'{folder.name}: {a.pick}, game ticks {t0} to {t1}, every {a.every} moments', out)
    lines = [f'{folder.name}: {a.pick}, game ticks {t0} to {t1}, every {a.every} moments'] + \
            [describe(s) + '  facts ' + json.dumps(s.get('facts', {})) for s, _ in rows]
    out.with_suffix('.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'wrote {viewer_shot.shown(out)} and its .txt ({len(rows)} moments, {len(cams)} cameras, '
          f'{time.time() - started:.0f} s)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
