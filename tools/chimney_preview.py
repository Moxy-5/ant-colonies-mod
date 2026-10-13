"""Draws a termite coming out of a chimney block's mouth, frame by frame, to check the animation without the game.

It replays what the game does: the server moves the termite (position, the surface it holds, where its head points,
and whether it is looking round), the client turns the drawn body smoothly toward the synced surface and heading
(AbstractAntEntity.turnBody) and lags the position a little (as the game does with mob positions), and the renderer
lays the model on the wall (AntRenderer: face the wall, lift, push into the wall, tip upright, turn to the heading). The
head's look round comes from the 'peek' animation in the king's animation file (termite_models.py).

The motion itself is read from build/chimney/come_out.txt, which the unit test DoorwayMotionTest writes from the same
code the game uses (nav/DoorwayMotion.java): run  ./gradlew test  first.

Writes references/termites/preview/chimney.png (frames in a grid, the block cut open so the inside of the spire shows),
chimney.gif (the whole thing as an animation, from the side and from the front) and chimney_closeup.gif (a close-up
from a three-quarter angle). With arguments  build/chimney/go_in.txt chimney_in  it draws the way in instead.

Run with Python 3 and Pillow from the project folder:  python tools/chimney_preview.py
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import termite_models as tm  # noqa: E402

ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'antcolonies')
OUT = os.path.join(ROOT, 'references', 'termites', 'preview')
KEYS = os.path.join(ROOT, 'build', 'chimney', 'come_out.txt')

# The game's numbers (keep in step with the Java).
SCALE = 0.75                 # TermiteQueenModel.RENDER_SCALE: termites are drawn at 3/4 size
CLIMB_LIFT, CLIMB_INTO_WALL = 0.25, 0.17   # AntRenderer
BODY_TURN_SPEED = 30.0       # AbstractAntEntity: degrees a tick the drawn body turns
CLIMB_SPEED = 0.22 * 0.24    # a termite worker's speed attribute x AntNavigator.CLIMB_FACTOR, blocks a tick
UPDATE_INTERVAL = 3          # EntityType default: mobs are sent to the client every 3 ticks
EAST = 'E'                   # the termite comes out toward the east (+X); the wall on that side is 'E', the far one 'W'
YAW = {'E': -90.0, 'W': 90.0, 'S': 0.0, 'N': 180.0}

SOIL = (150, 82, 50)
SPIRE = (170, 98, 60)
NEXT = (128, 70, 44)


# --- the motion: keys from the Java (DoorwayMotion), walked one tick at a time as AntNavigator does ---------------
def read_keys(path):
    """Each line: out y surface speed hold peek [slowTurn]  (out: blocks east of the block's middle; y: up from its bottom)."""
    keys = []
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            parts = line.split()
            out, y, surface, speed, hold, peek = parts[:6]
            slow = len(parts) > 6 and parts[6] == 'true'
            keys.append((float(out), float(y), surface, float(speed), int(hold), peek, slow))
    return keys


def wall_of(surface):
    return {'INSIDE': 'E', 'OUTSIDE': 'W', 'TOP': 'DOWN'}[surface]


def server_ticks(keys, start, surface='E', yaw=None):
    """Per tick: (x, y, z, surface, heading, body yaw, peeking) as the server has it, walking from key to key as
    AntNavigator does: the surface changes when the walk to a key starts (from a wall onto a top the body turns to
    face the way its head pointed), on a wall the head turns the way it moves, on a top the body faces the way it moves,
    and at a key it may stay a while (looking round if the key says so)."""
    x, y = start
    z = 0.5
    states = []
    heading = 0.0
    yaw = YAW[surface] if yaw is None else yaw
    for out, ky, ksurface, speed, hold, peek, slow in keys:
        tx, ty = 0.5 + out, ky
        wall = wall_of(ksurface)
        if wall == 'DOWN' and surface != 'DOWN':
            yaw = YAW[surface] - heading
        surface = wall
        step = CLIMB_SPEED * speed
        while True:
            dx, dy = tx - x, ty - y
            dist = math.hypot(dx, dy)
            mx, my = (dx, dy) if dist <= step else (dx * step / dist, dy * step / dist)
            x, y = x + mx, y + my
            if wall != 'DOWN':
                yaw = YAW[wall]
                left = {'E': 0, 'W': 0}[wall]  # sideways along these walls is north-south: none here
                if abs(my) > 1e-5:
                    heading = math.degrees(math.atan2(left, my))
            elif abs(mx) > 1e-8:
                yaw = -90.0 if mx > 0 else 90.0
            states.append((x, y, z, wall, heading, yaw, False, slow))
            if dist <= step:
                break
        for t in range(hold):
            h = heading
            if peek == 'wiggle':  # the first version: the whole body turned from side to side
                h = 35.0 * math.sin(t * 0.35)
            states.append((x, y, z, wall, h, yaw, peek == 'true', slow))
    return states


# --- the client: synced one tick late, position lagged, body turned smoothly (AbstractAntEntity.turnBody) ----------
def wrap(a):
    return (a + 180.0) % 360.0 - 180.0


def approach(frm, to, speed=BODY_TURN_SPEED):
    return frm + max(-speed, min(speed, wrap(to - frm)))


def client_frames(states):
    """What the client draws each tick, as AbstractAntEntity.turnBody does it."""
    frames = []
    pos = None
    wall_pose, tilt, tilt_o, wall_yaw, body_heading = False, 0.0, 0.0, 0.0, 0.0
    peek_start = None
    synced = states[0]
    target = synced[:3]
    lerp_steps = 0
    body_yaw = synced[5]
    floor_yaw = body_yaw
    for i, state in enumerate(states):
        # A mob's position and data reach the client every 3 ticks (its update interval); the position and the body
        # yaw are eased in over the next three ticks, the data (surface, heading, looking round) takes effect at once.
        if i % UPDATE_INTERVAL == 0:
            synced = state
            target = state[:3]
            lerp_steps = 3
        x, y, z, wall, heading, yaw, peek, slow = synced
        turn = BODY_TURN_SPEED / 2 if slow else BODY_TURN_SPEED
        if pos is None:
            pos = target
        elif lerp_steps > 0:
            pos = tuple(p + (t - p) / lerp_steps for p, t in zip(pos, target))
            body_yaw = body_yaw + wrap(yaw - body_yaw) / lerp_steps
            lerp_steps -= 1
        tilt_o = tilt
        if wall != 'DOWN':
            if not wall_pose:
                # Tipping up from the floor: from the way the body lay there last tick.
                wall_pose = True
                wall_yaw = YAW[wall]
                body_heading = wrap(wall_yaw - floor_yaw)
                tilt = 0.0 if i > 0 else 90.0
                if i == 0:
                    body_heading = heading
            if tilt <= 90.0 and abs(wrap(YAW[wall] - wall_yaw)) > 90.0:
                # Onto a wall facing the other way: lie flat first, then the same pose against the new wall.
                if tilt <= 0.0 and tilt_o <= 0.0:
                    shift = wrap(YAW[wall] - wall_yaw)
                    wall_yaw += shift
                    body_heading += shift
                else:
                    tilt = max(0.0, tilt - turn)
            else:
                wall_yaw = approach(wall_yaw, YAW[wall], turn)
                body_heading = approach(body_heading, heading, turn)
                tilt = min(90.0, tilt + turn)
        elif wall_pose:
            tilt = max(0.0, tilt - turn)
            body_heading = approach(body_heading, wall_yaw - body_yaw, turn)
            if tilt <= 0.0 and abs(wrap(body_heading - (wall_yaw - body_yaw))) < 1.0:
                wall_pose = False
        if not wall_pose:
            floor_yaw = body_yaw
        if peek and peek_start is None:
            peek_start = i
        elif not peek:
            peek_start = None
        anim_time = None if peek_start is None else (i - peek_start) / 20.0
        frames.append(dict(pos=pos, wall_pose=wall_pose, tilt=tilt, wall_yaw=wall_yaw, heading=body_heading,
                           yaw=body_yaw, peek=anim_time))
    return frames


# --- the 'peek' animation, with GeckoLib's easing --------------------------------------------------------------
def ease(kind, t):
    if kind in (None, 'linear'):
        return t
    if kind == 'easeInOutSine':
        return -(math.cos(math.pi * t) - 1) / 2
    if kind == 'easeOutSine':
        return math.sin(t * math.pi / 2)
    if kind == 'easeInSine':
        return 1 - math.cos(t * math.pi / 2)
    if kind == 'easeInOutQuad':
        return 2 * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 2 / 2
    return t


def sample_animation(bones_anim, t):
    out = {}
    for name, channels in bones_anim.items():
        frames = []
        for k, v in channels['rotation'].items():
            if isinstance(v, dict):
                frames.append((float(k), v['vector'], v.get('easing')))
            else:
                frames.append((float(k), v, None))
        frames.sort(key=lambda f: f[0])
        if t <= frames[0][0]:
            out[name] = frames[0][1]
            continue
        if t >= frames[-1][0]:
            out[name] = frames[-1][1]
            continue
        for (t0, r0, _), (t1, r1, e1) in zip(frames, frames[1:]):
            if t0 <= t <= t1:
                f = ease(e1, 0 if t1 == t0 else (t - t0) / (t1 - t0))
                out[name] = [a + (b - a) * f for a, b in zip(r0, r1)]
                break
    return out


# --- placing the model in the world, as AntRenderer does ---------------------------------------------------------
def rot_y(p, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    x, y, z = p
    return (x * c + z * s, y, -x * s + z * c)


def rot_x(p, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    x, y, z = p
    return (x, y * c - z * s, y * s + z * c)


def termite_boxes(bones, frame, peek_anim):
    anim = sample_animation(peek_anim, frame['peek']) if frame['peek'] is not None else {}
    boxes = tm.model_boxes(bones, anim=anim)
    px, py, pz = frame['pos']
    placed = []
    for corners, rgb in boxes:
        world = []
        for p in corners:
            q = (p[0] / 16.0, p[1] / 16.0, p[2] / 16.0)
            if frame['wall_pose']:
                share = min(frame['tilt'], 90.0) / 90.0
                q = rot_y(q, frame['heading'])
                q = rot_x(q, frame['tilt'])
                q = (q[0], q[1] + CLIMB_LIFT * share, q[2] - CLIMB_INTO_WALL * share)
                q = rot_y(q, 180.0 - frame['wall_yaw'])
            else:
                q = rot_y(q, 180.0 - frame['yaw'])
            q = tuple(c * SCALE for c in q)
            world.append(((px + q[0]) * 16, (py + q[1]) * 16, (pz + q[2]) * 16))
        placed.append((world, rgb))
    return placed


def block_boxes(cut):
    """The chimney block at x 0..16 and the soil block east of it; cut = only the back half (to see inside)."""
    model = json.load(open(os.path.join(ASSETS, 'models', 'block', 'termite_hole.json')))
    boxes = []
    for e in model['elements']:
        (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
        if cut:
            z0 = max(z0, 8)
            if z0 >= z1:
                continue
        rgb = SOIL if y1 <= 16 else SPIRE
        boxes.append(([(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], rgb))
    for x0, rgb in ((16, NEXT), (-16, NEXT)):
        z0 = 8 if cut else 0
        boxes.append(([(x, y, z) for x in (x0, x0 + 16) for y in (0, 16) for z in (z0, 16)], rgb))
    return [(corners, rgb) for corners, rgb in boxes]


def centred(boxes, cx, cy, cz):
    return [([(x - cx, y - cy, z - cz) for x, y, z in corners], rgb) for corners, rgb in boxes]


VIEWS = {
    # name: (camera turn, tilt, zoom, cut open?)
    'side': (0.0, -12.0, 9.0, True),
    'front': (-90.0, -25.0, 9.0, False),
}


def render_frame(canvas, cell, bones, frame, peek_anim, view):
    yaw, pitch, ppx, cut = VIEWS[view]
    boxes = centred(block_boxes(cut) + termite_boxes(bones, frame, peek_anim), 8, 22, 8)
    tm.draw(canvas, boxes, cell, 0.5, math.radians(yaw), math.radians(pitch), ppx)


def to_image(canvas):
    img = Image.new('RGB', (canvas.width, canvas.height))
    img.putdata(canvas.color)
    return img


def main():
    # Optional: another keys file and another name for the pictures (to compare versions).
    keys_path = sys.argv[1] if len(sys.argv) > 1 else KEYS
    name = sys.argv[2] if len(sys.argv) > 2 else 'chimney'
    if not os.path.exists(keys_path):
        sys.exit('no ' + keys_path + ': run ./gradlew test first (DoorwayMotionTest writes it)')
    keys = read_keys(keys_path)
    bones = tm.worker_model()
    peek_anim = tm.animations()['animations'].get('peek', {'bones': {}})['bones']
    if 'go_in' in os.path.basename(keys_path):
        # Going in: it starts on the top of the block east of the chimney block, facing west.
        states = server_ticks(keys, (0.5 + 0.7, 1.002), 'DOWN', 90.0)
    else:
        # Coming out: it starts inside the block, clinging to its east wall at mid height.
        states = server_ticks(keys, (0.5 + 0.5 - 0.15 - 0.002, 0.5))
    frames = client_frames(states)
    print(len(frames), 'ticks')
    os.makedirs(OUT, exist_ok=True)

    # The sheet: every few ticks, side view cut open.
    every = 3
    picks = list(range(0, len(frames), every))
    cols = 8
    cw, ch = 220, 300
    rows = (len(picks) + cols - 1) // cols
    sheet = tm.Canvas(cw * cols, tm.TITLE_H + (ch + tm.LABEL_H) * rows)
    title = 'GOING IN' if 'go_in' in os.path.basename(keys_path) else 'COMING OUT OF THE CHIMNEY'
    sheet.text(12, 12, title + ' - SIDE, CUT OPEN, EVERY 3 TICKS', scale=4)
    for k, i in enumerate(picks):
        x0, y0 = cw * (k % cols), tm.TITLE_H + (ch + tm.LABEL_H) * (k // cols)
        sheet.frame(x0, y0, cw, ch + tm.LABEL_H)
        label = f'T {i}' + (' PEEK' if frames[i]['peek'] is not None else '')
        sheet.text(x0 + 8, y0 + 8, label)
        render_frame(sheet, (x0 + 1, y0 + tm.LABEL_H, cw - 2, ch - 1), bones, frames[i], peek_anim, 'side')
    sheet.save(os.path.join(OUT, name + '.png'))

    # The animation: side (cut open) and front, every tick.
    images = []
    for i, frame in enumerate(frames):
        canvas = tm.Canvas(2 * 300, 360)
        for v, view in enumerate(('side', 'front')):
            render_frame(canvas, (v * 300, 0, 300, 360), bones, frame, peek_anim, view)
        canvas.text(8, 8, f'T {i}', scale=2)
        images.append(to_image(canvas))
    images[0].save(os.path.join(OUT, name + '.gif'), save_all=True, append_images=images[1:], duration=50, loop=0)

    # A close-up from a three-quarter angle, from when the termite comes into view (as it looks in the game).
    close = []
    first = next((i for i, f in enumerate(frames) if f['pos'][1] > 1.15), 0)
    for frame in frames[first:]:
        canvas = tm.Canvas(420, 420)
        boxes = centred(block_boxes(False) + termite_boxes(bones, frame, peek_anim), 10, 24, 8)
        tm.draw(canvas, boxes, (0, 0, 420, 420), 0.55, math.radians(-62), math.radians(-18), 17.0)
        close.append(to_image(canvas))
    close[0].save(os.path.join(OUT, name + '_closeup.gif'), save_all=True, append_images=close[1:] + [close[-1]] * 20,
                  duration=50, loop=0)
    print('wrote', name + '.png,', name + '.gif and', name + '_closeup.gif')


if __name__ == '__main__':
    main()
