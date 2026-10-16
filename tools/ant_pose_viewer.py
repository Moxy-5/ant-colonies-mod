"""The ant pose viewer (2026-10-15): a web page with the worker ant model in 3D, every candidate animation playing
side by side on a sandy floor, to turn, zoom and compare before an animation goes into the game. A sister of the model
viewer (tools/model_viewer.py), which is for blocks.

First used for the midday rest and the heat (2026-10-15): the user picked both sleeps (played by turns) and the hot look
with the gaster up, which now live in ant_models.py. For new candidates, write them here (or in ant_models.py) and add
them to CANDIDATES; once picked, move them into ant_models.py's animations().

The model and the animations come straight from ant_models.py (the same bones, boxes and colours as the game), so the
page shows what the game would. Each box takes its swatch's colour (flat, Minecraft-style face shading).
The page needs the internet for three.js (from cdnjs/jsdelivr).

Since the animation lab (2026-10-15): soft shadows from an overhead sun (a foot touches its shadow only on the floor),
each foot's path over the floor (a planted foot is one dot, a sliding one smears), pause, slow motion, frame steps,
walking across the floor at the walk's own speed, fixed cameras, and settings from the address so Claude can take exact
screenshots: references/ant_pose_viewer.html#focus=1&cam=low&t=0.2&pause=1 (or lab.set({...}) in the console).

Run from the project folder:  python tools/ant_pose_viewer.py [out.html]   (default references/ant_pose_viewer.html)
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ant_models as am  # noqa: E402
import anim_lab as lab  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent


def length(anim):
    return max(float(t) for channels in anim.values() for frames in channels.values() for t in frames)


CANDIDATES = [
    ('Idle (now)', 'today\'s idle: feelers searching, slow breathing', am.idle()),
    ('Walk (now)', 'today\'s walk: the tripod gait', am.walk()),
    ('Sleep', 'sinks onto the floor, feelers folded and drooping, only breathing', am.sleep()),
    ('Sleep with twitches', "as Sleep, with quick feeler flicks now and then (real ants' deep sleep)", am.sleep_twitch()),
    ('Hot, standing', 'stilting with the gaster up, feelers searching', am.idle_hot()),
    ('Hot, walking', 'stilting with the gaster up, quicker steps', am.walk_hot()),
]


def colour(cube):
    u, v = cube['uv']['north']['uv']
    for col, row, rgb, _ in am.SWATCH.values():
        if (col * 8 + 1, row * 8 + 1) == (u, v):
            return '#%02x%02x%02x' % rgb
    return '#ff00ff'


def entry(name, note, anim):
    """One option for the page, with its feet worked out by the animation lab (tools/anim_lab.py): the walk's own speed
    (model units a second; 0 for a standing clip) and each foot's tip through the cycle, for the foot trails."""
    clip = lab.Clip(name, {'loop': True, 'animation_length': length(anim), 'bones': anim})
    gait = lab.Gait(MODEL, clip)
    tips = {leg: [[round(v, 3) for v in t[leg]] for t in gait.tips] for leg in gait.legs}
    return {'name': name, 'note': note, 'length': clip.length, 'bones': anim, 'speed': gait.speed,
            'times': [round(t, 4) for t in gait.times], 'tips': tips}


MODEL = lab.Model('worker_ant')


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'references' / 'ant_pose_viewer.html'
    bones = am.worker_model()
    for b in bones:
        for c in b.get('cubes', []):
            c['colour'] = colour(c)
    data = {'bones': bones, 'anims': [entry(n, note, a) for n, note, a in CANDIDATES]}
    page = (ROOT / 'tools' / 'ant_pose_viewer.template.html').read_text(encoding='utf-8')
    out.write_text(page.replace('/*DATA*/null', json.dumps(data)), encoding='utf-8')
    print(f'wrote {out}')


if __name__ == '__main__':
    main()
