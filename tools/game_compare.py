"""The game beside the approved design (2026-11-20, the bullfrog's eye fix; the model workshop's lesson in
docs/plans/model-workshop.md): renders an animal's approved viewer version from the very cameras of an in-game film
scene, and lays each film picture beside its render in one Compare picture, so a difference between what the game
draws and what the user approved in the viewer shows at a glance.

    python tools/game_compare.py bullfrog v27 tools/film/bullfrog_eyes.film \\
        --game "In the game=run-film/screenshots/bullfrog_eyes" --shots eye_side,eye_threequarter,eye_front \\
        --out references/workshop/bullfrog/eyes_game_vs_v27.png

  --game "label=folder"   a folder of the scene's pictures (several: one column each, e.g. before and after a fix)
  --px                    a model pixel in blocks in the game (the renderer's scale / 16, times the animal's own size);
                          by default the animal's body plan's "checks.px" (a grown animal of size 1)
  --shots                 which "shot" lines to compare (default all of them; bursts are clips, not compared)
  --crop x0,y0,x1,y1      cut every picture to this part (of the film's 960 x 540) before laying them out

The film's summon lines give the animal's place and turn (Rotation:[yaw, ...]), its camera lines (the camera's feet:
the eye is 1.62 above) and lens lines each shot's camera; the render is the version's look (references/workshop/
<animal>/versions/<version>/look.json and texture.png) in its rest pose, drawn by the viewers' own code
(tools/viewer_common.js) from that camera, through Edge with no window (tools/viewer_shot.py).

GeckoLib draws a model as the mirror image of what the viewer draws (it turns x over on loading). For an animal whose
two sides match, the picture from the same place is the same, so the render is the viewer's model from the camera's
place in the game's model space; the game's paint follows the viewer's only through model_parts.game_faces (the face
rule), which is what this picture checks.
"""
import argparse
import base64
import json
import math
import re
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import viewer_page
import viewer_shot

ROOT = Path(__file__).resolve().parent.parent
EYE = 1.62


def shots_of(film):
    """{shot name: (camera eye xyz, yaw, pitch, lens, (animal xyz, animal yaw))} for every "shot" line of a scene."""
    lens, animal, camera, out = 30.0, None, None, {}
    for line in Path(film).read_text(encoding='utf-8').splitlines():
        line = line.split('#', 1)[0].strip()
        if not line:
            continue
        w = line.split()
        if w[0] in ('lens', 'fov'):
            lens = float(w[1])
        elif w[0] == 'cmd' and len(w) > 5 and w[1] == 'summon':
            yaw = re.search(r'Rotation:\[\s*(-?[\d.]+)', line)
            animal = ((float(w[3]), float(w[4]), float(w[5])), float(yaw.group(1)) if yaw else 0.0)
        elif w[0] == 'camera':
            x, y, z, yaw, pitch = map(float, w[1:6])
            camera = ((x, y + EYE, z), yaw, pitch)
        elif w[0] == 'shot' and camera and animal:
            out[w[1]] = (*camera, lens, animal)
    return out


def to_model(v, animal_yaw, origin=None):
    """A world point (or, with origin None, a direction) in the game's model space: GeckoLib turns a model by
    180 - yaw about y (x' = x cos t + z sin t, z' = -x sin t + z cos t), so back by the opposite."""
    if origin is not None:
        v = [a - b for a, b in zip(v, origin)]
    t = math.radians(180 - animal_yaw)
    x, y, z = v
    return [x * math.cos(t) - z * math.sin(t), y, x * math.sin(t) + z * math.cos(t)]


def view_camera(shot, px):
    """The viewer's camera for one shot: place and target in viewer units (a model pixel a sixteenth)."""
    eye, yaw, pitch, lens, (place, animal_yaw) = shot
    p, yw = math.radians(pitch), math.radians(yaw)
    look = [-math.sin(yw) * math.cos(p), -math.sin(p), math.cos(yw) * math.cos(p)]
    pos = [c / px / 16 for c in to_model(eye, animal_yaw, place)]
    d = to_model(look, animal_yaw)
    return {'pos': pos, 'target': [a + b * 0.5 for a, b in zip(pos, d)], 'fov': lens}


def render(animal, version, shots, px, size, out_dir):
    vdir = ROOT / 'references/workshop' / animal / 'versions' / version
    look = json.loads((vdir / 'look.json').read_text(encoding='utf-8'))
    texture = 'data:image/png;base64,' + base64.b64encode((vdir / 'texture.png').read_bytes()).decode()
    with Image.open(vdir / 'texture.png') as t:
        w, h = t.size
    data = {'look': {'bones': look['bones'], 'texture': texture, 'w': w, 'h': h},
            'shots': {name: view_camera(s, px) for name, s in shots.items()}}
    page = viewer_page.build('game_compare.template.html', data, out_dir / 'page.html')
    pics = {}
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as profile:
        for name in shots:
            pics[name] = out_dir / f'{name}.png'
            viewer_shot.shoot(page, f'shot={name}', pics[name], f'{size[0]},{size[1]}', profile, 4000)
    return pics


def font(n):
    for f in ('arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(f, n)
        except OSError:
            pass
    return ImageFont.load_default()


def sheet(columns, names, crop, out, title):
    """One picture: a row per shot, a column per source (label, {name: picture path})."""
    first = Image.open(next(iter(columns[0][1].values())))
    box = crop or (0, 0, *first.size)
    cw, ch = box[2] - box[0], box[3] - box[1]
    scale = min(1.0, 640 / cw)
    cw, ch = int(cw * scale), int(ch * scale)
    pad, head, label = 8, 44, 26
    W = pad + len(columns) * (cw + pad)
    H = head + label + len(names) * (ch + pad + 20)
    s = Image.new('RGB', (W, H), 'white')
    d = ImageDraw.Draw(s)
    d.text((pad, 8), title, fill='black', font=font(24))
    for i, (lab, pics) in enumerate(columns):
        d.text((pad + i * (cw + pad), head), lab, fill='black', font=font(20))
    y = head + label
    for name in names:
        d.text((pad, y), name.replace('_', ' '), fill='#444', font=font(16))
        for i, (lab, pics) in enumerate(columns):
            if name in pics and Path(pics[name]).exists():
                im = Image.open(pics[name]).convert('RGB').crop(box).resize((cw, ch), Image.LANCZOS)
                s.paste(im, (pad + i * (cw + pad), y + 20))
        y += ch + pad + 20
    out.parent.mkdir(parents=True, exist_ok=True)
    s.save(out, optimize=True)
    print('wrote', out)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('animal')
    ap.add_argument('version')
    ap.add_argument('film')
    ap.add_argument('--px', type=float, help='a model pixel in blocks (default: the body plan checks.px)')
    ap.add_argument('--game', action='append', default=[], help='"label=folder" of the scene\'s pictures')
    ap.add_argument('--shots')
    ap.add_argument('--crop')
    ap.add_argument('--out', required=True)
    ap.add_argument('--title')
    a = ap.parse_args()
    if a.px is None:
        # one source of truth per animal (the Animal Maker Engine step A): the body plan's model pixel in blocks
        import body_plan
        a.px = json.load(open(body_plan.plan_path(a.animal), encoding='utf-8')).get('checks', {}).get('px')
        if a.px is None:
            ap.error(f'no --px given and the {a.animal} body plan has no checks.px')
    shots = shots_of(ROOT / a.film)
    if a.shots:
        shots = {n: shots[n] for n in a.shots.split(',')}
    games = []
    for g in a.game:
        lab, folder = g.split('=', 1)
        folder = Path(folder) if Path(folder).is_absolute() else ROOT / folder
        games.append((lab, {n: folder / f'{n}.png' for n in shots}))
    size = (960, 540)
    if games:
        with Image.open(next(iter(games[0][1].values()))) as im:
            size = im.size
    out = ROOT / a.out if not Path(a.out).is_absolute() else Path(a.out)
    renders = render(a.animal, a.version, shots, a.px, size, ROOT / 'references/viewer_shots/game_compare' / a.animal)
    crop = tuple(int(v) for v in a.crop.split(',')) if a.crop else None
    sheet(games + [(f'The approved design ({a.version}, the viewer)', renders)], list(shots), crop, out,
          a.title or f'{a.animal}: the game beside the approved design, from the same cameras')


if __name__ == '__main__':
    main()
