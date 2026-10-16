"""
Builds the red harvester ant (Pogonomyrmex): the worker's GeckoLib model, its animations and textures (the queen uses
the same model, bigger, with her own texture), plus preview pictures.
Run from the project folder:  python tools/ant_models.py
Then run tools/bbmodel_import.py to refresh the Blockbench project.

Units are model pixels (16 = 1 block before the renderer's scale); the head points to -Z, the ground is y = 0.
Every face of a box shows one 6x6 patch of a colour swatch (per-face UV), so even the thinnest parts take their colour.

Bones (the same names as the termites', so animations and code can work on both):
  body
    forebody (head, thorax and legs, jointed where the thorax meets the waist)
      head > mandible_l, mandible_r, antenna_l_1 > antenna_l_2, antenna_r_1 > antenna_r_2, carry
      thorax
      leg_l1 > leg_l1_lower > leg_l1_foot ... leg_r3 (hip, knee, ankle)
    petiole > postpetiole > abdomen (the two waist nodes and the gaster)
'carry' has no boxes: it marks the spot between the mandible tips where a load is held (PelletLayer).

What makes it a harvester ant (from photos of Pogonomyrmex barbatus): rusty red all over, the gaster a little darker
and shinier; a big, almost square head with fine lengthwise ridges; strong dark triangular mandibles; a beard of long
golden hairs under the head (the psammophore, for carrying sand); elbowed antennae (a long first segment, then a
thinner whip that thickens a little at the tip); a humped thorax with two short spines at the back; a two-node waist
(petiole and postpetiole); legs that rise to the knee and reach down to the ground.

Preview pictures go to references/ants/preview/ (front, side, top, front from above, and four moments of a step).
"""
import json, math, os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import termite_models as tm  # the PNG writer and the little box renderer
import anim_lab as lab       # the animation lab: the model as the game builds it
import ant_motion as mo      # the motion library: legs worked out from where the feet must be

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'antcolonies')
PREVIEW_DIR = os.path.join(HERE, '..', 'references', 'ants', 'preview')

# The model is drawn in these units and then scaled down by SCALE (about 11 pixels long after scaling; the renderer
# draws the worker at 0.65 of that: about 0.45 blocks long).
SCALE = 0.85

# --- texture: 64x64, 8x8 swatches (column, row, colour, pattern) -------------------------------------------------
SWATCH = {
    'head':    (0, 0, (150, 52, 26), 'ridges'),
    'thorax':  (1, 0, (156, 58, 28), 'noise'),
    'waist':   (2, 0, (146, 50, 25), 'noise'),
    'gaster':  (3, 0, (118, 38, 20), 'shine'),
    'leg':     (4, 0, (168, 70, 34), 'noise'),
    'antenna': (5, 0, (160, 64, 30), 'noise'),
    'jaw':     (6, 0, (58, 22, 12), 'noise'),
    'eye':     (7, 0, (22, 15, 12), 'eye'),
    'beard':   (0, 1, (214, 172, 104), 'hairs'),
    'joint':   (1, 1, (104, 34, 18), 'noise'),
}
# The queen: the same layout, a deeper red (she is drawn with the same model, bigger).
QUEEN_TINT = (0.84, 0.8, 0.8)


def make_texture(path, tint=(1.0, 1.0, 1.0)):
    rnd = random.Random(11)
    pixels = [(0, 0, 0, 0)] * (64 * 64)
    for name, (col, row, rgb, pattern) in SWATCH.items():
        base = [v * t for v, t in zip(rgb, tint)]
        for y in range(8):
            for x in range(8):
                k = rnd.uniform(-0.07, 0.07)
                if pattern == 'ridges':
                    k += -0.12 if x % 2 == 0 else 0.03  # fine lengthwise ridges
                elif pattern == 'shine':
                    k = rnd.uniform(-0.04, 0.04) + (0.22 if (x, y) in ((2, 2), (3, 2), (2, 3)) else 0.0)
                elif pattern == 'eye':
                    k = rnd.uniform(-0.05, 0.05) + (1.6 if (x, y) == (2, 2) else 0.0)  # a glint
                elif pattern == 'hairs':
                    k += 0.1 if x % 2 == 0 else -0.12
                c = tuple(max(0, min(255, int(v * (1 + k)))) for v in base)
                pixels[(row * 8 + y) * 64 + col * 8 + x] = c + (255,)
    tm.write_png(path, 64, 64, pixels)


# --- boxes --------------------------------------------------------------------------------------------------------
def r3(values):
    return [round(v * SCALE, 3) for v in values]


def box(lo, hi, swatch, pivot=None, rotation=None):
    """A box from corner lo to corner hi (design units), optionally turned by rotation about pivot."""
    col, row = SWATCH[swatch][0], SWATCH[swatch][1]
    face = {'uv': [col * 8 + 1, row * 8 + 1], 'uv_size': [6, 6]}
    c = {'origin': r3(lo), 'size': r3([b - a for a, b in zip(lo, hi)]),
         'uv': {f: dict(face) for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if rotation and any(abs(v) > 1e-6 for v in rotation):
        c['pivot'] = r3(pivot)
        c['rotation'] = [round(v, 2) for v in rotation]
    return c


def centered(cx, cy, cz, sx, sy, sz, swatch):
    return box([cx - sx / 2, cy - sy / 2, cz - sz / 2], [cx + sx / 2, cy + sy / 2, cz + sz / 2], swatch)


def rod(a, b, thick, swatch, height=None):
    """A box from point a to point b, thick x thick (or thick wide and height tall): a leg or antenna segment.

    Built along +X from a and turned onto the line a-b. The turn follows the model file's rules (GeckoLib and
    Blockbench read it the same way): X is applied first, then Y, then Z; a turn of Y swings +X toward -Z, a turn of Z
    swings +X down. So the box's +X ends up at (cos y cos z, -cos y sin z, -sin y).
    """
    d = [q - p for p, q in zip(a, b)]
    length = math.sqrt(sum(v * v for v in d))
    dx, dy, dz = (v / length for v in d)
    ry = math.degrees(math.asin(max(-1.0, min(1.0, -dz))))
    rz = math.degrees(math.atan2(-dy, dx))
    h = thick if height is None else height
    return box([a[0], a[1] - h / 2, a[2] - thick / 2], [a[0] + length, a[1] + h / 2, a[2] + thick / 2], swatch, a, [0, ry, rz])


def bone(name, parent, pivot, cubes=()):
    b = {'name': name, 'pivot': r3(pivot)}
    if parent:
        b['parent'] = parent
    if cubes:
        b['cubes'] = list(cubes)
    return b


def mirror(p):
    return (-p[0], p[1], p[2])


# --- the worker ---------------------------------------------------------------------------------------------------
# Where things join (design units, left side +X).
NECK = (0, 2.3, -2.75)
WAIST = (0, 1.95, 0.75)          # the forebody joint: thorax to petiole
POSTPETIOLE = (0, 1.95, 1.85)
GASTER = (0, 2.0, 2.75)
MANDIBLE_TIPS_Z = -6.55
CARRY = (0, 1.75, -6.9)          # between the mandible tips, a little in front

# Legs: hip, knee, ankle, toe (left side; the right side is the mirror image). The knees rise above the back, as in a
# walking ant; the front legs reach forward, the middle ones out, the hind ones (the longest) back.
LEGS = {
    1: [(0.55, 1.4, -2.15), (2.35, 2.95, -3.1), (3.55, 0.55, -4.2), (4.1, 0.0, -4.95)],
    2: [(0.65, 1.35, -0.95), (2.55, 3.05, -1.15), (4.0, 0.55, -0.85), (4.75, 0.0, -0.55)],
    3: [(0.6, 1.35, 0.1), (2.35, 3.05, 1.2), (3.7, 0.55, 3.35), (4.15, 0.0, 4.35)],
}
# Antennae: socket, elbow, then the whip's points to the tip (left side).
ANTENNA_SOCKET = (0.72, 2.75, -5.05)
ANTENNA_ELBOW = (1.65, 3.95, -3.55)
ANTENNA_WHIP = [(2.2, 4.4, -5.2), (2.75, 4.25, -6.7), (3.0, 3.75, -7.75)]


def head_bones():
    head = bone('head', 'forebody', NECK, [
        box([-1.45, 1.2, -5.3], [1.45, 3.15, -2.7], 'head'),            # the big square head
        box([-1.6, 1.45, -5.0], [1.6, 2.9, -3.0], 'head'),              # its full sides
        box([-1.2, 3.15, -5.0], [1.2, 3.4, -2.95], 'head'),             # the rounded crown
        box([-0.85, 1.35, -5.6], [0.85, 2.15, -5.3], 'head'),           # the clypeus (the plate over the jaws)
        box([1.6, 2.2, -4.45], [1.75, 2.8, -3.65], 'eye'),
        box([-1.75, 2.2, -4.45], [-1.6, 2.8, -3.65], 'eye'),
        box([-1.0, 0.85, -5.1], [1.0, 1.2, -3.4], 'beard'),             # the psammophore: a beard of long hairs
        box([-0.55, 1.75, -2.95], [0.55, 2.55, -2.6], 'thorax'),        # the neck
    ])
    bones = [head]
    for side, sx in (('l', 1), ('r', -1)):
        def m(p):
            return p if sx > 0 else mirror(p)
        base = m((0.62, 1.55, -5.3))
        lo_x, hi_x = sorted((0.25 * sx, 1.15 * sx))
        tip_lo, tip_hi = sorted((0.0, 0.7 * sx))
        bones.append(bone(f'mandible_{side}', 'head', base, [
            box([lo_x, 1.35, -6.05], [hi_x, 1.8, -5.25], 'jaw'),                  # the broad base
            box([tip_lo, 1.4, MANDIBLE_TIPS_Z], [tip_hi, 1.75, -6.0], 'jaw'),     # the toothed edge, meeting the other
        ]))
        socket, elbow = m(ANTENNA_SOCKET), m(ANTENNA_ELBOW)
        bones.append(bone(f'antenna_{side}_1', 'head', socket, [rod(socket, elbow, 0.32, 'antenna')]))
        whip, points = [], [elbow] + [m(p) for p in ANTENNA_WHIP]
        for i, (a, b) in enumerate(zip(points, points[1:])):
            whip.append(rod(a, b, 0.24 if i < 2 else 0.34, 'antenna'))       # thicker at the tip (the club)
        bones.append(bone(f'antenna_{side}_2', f'antenna_{side}_1', elbow, whip))
    bones.append(bone('carry', 'head', CARRY))
    return bones


def thorax_bone():
    cubes = [
        box([-1.0, 1.35, -2.75], [1.0, 2.85, -1.4], 'thorax'),     # pronotum, the high front hump
        box([-0.8, 1.3, -1.45], [0.8, 2.6, -0.3], 'thorax'),       # mesonotum
        box([-0.75, 1.25, -0.35], [0.75, 2.25, 0.8], 'thorax'),    # propodeum, lower at the back
    ]
    for sx in (1, -1):
        cubes.append(rod((0.42 * sx, 2.15, 0.55), (0.5 * sx, 2.75, 1.05), 0.18, 'thorax'))  # propodeal spines
    return bone('thorax', 'forebody', (0, 2.0, -1.0), cubes)


def leg_bones():
    bones = []
    for n, (hip, knee, ankle, toe) in LEGS.items():
        for side, sx in (('l', 1), ('r', -1)):
            def m(p):
                return p if sx > 0 else mirror(p)
            h, k, a, t = m(hip), m(knee), m(ankle), m(toe)
            bones.append(bone(f'leg_{side}{n}', 'forebody', h, [
                centered(h[0], h[1], h[2], 0.55, 0.55, 0.55, 'leg'),   # the coxa
                rod(h, k, 0.42, 'leg')]))                              # the femur
            bones.append(bone(f'leg_{side}{n}_lower', f'leg_{side}{n}', k, [
                centered(k[0], k[1], k[2], 0.42, 0.42, 0.42, 'joint'),
                rod(k, a, 0.32, 'leg')]))                              # the tibia
            bones.append(bone(f'leg_{side}{n}_foot', f'leg_{side}{n}_lower', a, [rod(a, t, 0.24, 'joint')]))  # the tarsus
    return bones


def worker_model():
    bones = [bone('body', None, (0, 2.0, 0)), bone('forebody', 'body', WAIST)]
    bones += head_bones()
    bones.append(thorax_bone())
    bones += leg_bones()
    bones.append(bone('petiole', 'body', WAIST, [
        box([-0.3, 1.6, 0.75], [0.3, 2.0, 1.35], 'waist'),        # the stalk
        box([-0.5, 1.55, 1.2], [0.5, 2.6, 1.85], 'waist'),        # the node, rising
    ]))
    bones.append(bone('postpetiole', 'petiole', POSTPETIOLE, [
        box([-0.68, 1.45, 1.85], [0.68, 2.5, 2.8], 'waist'),
    ]))
    bones.append(bone('abdomen', 'postpetiole', GASTER, [
        box([-1.3, 0.95, 2.85], [1.3, 3.1, 6.15], 'gaster'),
        box([-1.48, 1.25, 3.3], [1.48, 2.8, 5.6], 'gaster'),      # its full sides
        box([-1.05, 1.3, 2.65], [1.05, 2.7, 2.9], 'gaster'),      # the front, rounded into the waist
        box([-0.95, 1.25, 6.1], [0.95, 2.6, 6.55], 'gaster'),     # the tip
        box([-1.0, 3.1, 3.4], [1.0, 3.3, 5.5], 'gaster'),         # the rounded back
    ]))
    return bones


# --- animations ---------------------------------------------------------------------------------------------------
# The same rules as the termites (Blockbench's): Y swings a left leg forward (positive) and a right leg back; a
# negative Z lifts a left leg, a positive one a right leg; a positive X tips a head or an antenna down.
IO = 'easeInOutSine'


def keys(frames, channel='rotation'):
    return {channel: {f'{t:g}': {'vector': [round(v, 2) for v in vec], 'easing': IO} for t, vec in frames}}


_RIG = []


def rig():
    """The worker's model ready for the motion library (built once)."""
    if not _RIG:
        _RIG.append(mo.Rig(lab.Model('worker_ant', bones=worker_model())))
    return _RIG[0]


# The walk (rebuilt with the motion library, 2026-10-16). Real walking ants use the tripod gait: front and hind leg of
# one side with the middle leg of the other. Each foot's path is set and the legs are worked out backwards from it, so
# a foot on the ground stays put while the body passes over it (the old hand-set walk's feet slid 2 to 3 times the walk
# speed). The numbers, my picks from filmed ants, to confirm:
#   sweep 3.2 units: how far a planted foot travels under the body (adjacent feet touch beyond about 3.3);
#   duty 0.55: the share of a step a foot is down (real tripod walkers 0.5 to 0.65; lower when faster);
#   lift 0.9 units: how high a foot rises in the air;
#   shift: the front feet stand 0.15 units further back and the hind feet 0.6 further forward than at rest, so the
#   short legs of this model never lock straight.
# Stride: 3.2 / 0.55 = 5.8 units a step, 0.48 body lengths (real ants 0.5 to 0.7); at the game's 2.75 blocks a second
# each leg steps about 11.6 times a second (real Pogonomyrmex about 10). The game plays the clip at the speed that keeps
# the feet planted, from its stride note.
WALK_SECONDS = 0.5
WALK = dict(sweep=3.2, duty=0.55, lift=0.9, shift={1: 0.15, 3: -0.6})


def walking_antennae(track, seconds, lift=0.0):
    """The antennae while walking (played by the head's own controller at its own pace, so they do not flicker with
    the legs): held forward and a little down, the two sweeping out of step with each other, the whip (the part past
    the elbow) following the base a moment later. lift: the whole antenna raised (negative) or lowered."""
    n = 40
    for i in range(n + 1):
        t = seconds * i / n
        for side, ph, sy in (('l', 0.0, 1), ('r', 0.5, -1)):
            x = lift + 4 + mo.waves(t, seconds, [(1, 6, ph), (2, 2, ph + 0.3)])
            y = sy * (2 + mo.waves(t, seconds, [(1, 9, ph + 0.25), (3, 1.5, ph + 0.1)]))
            whip = 2 + mo.waves(t, seconds, [(1, 7, ph - 0.1), (2, 3, ph + 0.15)])
            track.put(f'antenna_{side}_1', t, [x, y, 0])
            track.put(f'antenna_{side}_2', t, [whip, 0, 0])


def walk_clip():
    """The walk: (bones, stride in units a clip)."""
    tr, stride, _ = mo.walk(rig(), WALK_SECONDS, **WALK)
    walking_antennae(tr, WALK_SECONDS)
    return tr.bones(), stride


def walk():
    return walk_clip()[0]


IDLE_SECONDS = 4.0


def idle():
    """Standing: the antennae feel about (each on its own rhythm, the whips flicking), the head turns a little, the
    gaster rises and sinks slowly (breathing), the mandibles open and close once."""
    T = IDLE_SECONDS
    return {
        'antenna_l_1': keys([(0, [0, 0, 0]), (0.7, [12, 14, 0]), (1.5, [-6, 4, 0]), (2.3, [8, -6, 0]), (3.2, [-4, 8, 0]), (T, [0, 0, 0])]),
        'antenna_r_1': keys([(0, [0, 0, 0]), (0.5, [-6, -6, 0]), (1.3, [10, -14, 0]), (2.4, [-4, 2, 0]), (3.0, [8, -8, 0]), (T, [0, 0, 0])]),
        'antenna_l_2': keys([(0, [0, 0, 0]), (0.9, [10, 0, 0]), (1.2, [-4, 0, 0]), (2.6, [6, 0, 0]), (T, [0, 0, 0])]),
        'antenna_r_2': keys([(0, [0, 0, 0]), (0.6, [-4, 0, 0]), (1.7, [10, 0, 0]), (2.0, [0, 0, 0]), (3.4, [6, 0, 0]), (T, [0, 0, 0])]),
        'head': keys([(0, [0, 0, 0]), (1.2, [3, 6, 0]), (2.0, [3, 6, 0]), (3.1, [0, -4, 0]), (T, [0, 0, 0])]),
        'abdomen': keys([(0, [0, 0, 0]), (T / 2, [-3, 0, 0]), (T, [0, 0, 0])]),
        'mandible_l': keys([(0, [0, 0, 0]), (2.4, [0, 0, 0]), (2.6, [0, -12, 0]), (2.85, [0, 0, 0]), (T, [0, 0, 0])]),
        'mandible_r': keys([(0, [0, 0, 0]), (2.4, [0, 0, 0]), (2.6, [0, 12, 0]), (2.85, [0, 0, 0]), (T, [0, 0, 0])]),
    }


# --- the midday rest and hot ground (picked by the user in the ant pose viewer, 2026-10-15) ---------------------
# Sleep and sleep_twitch are played by turns while an ant rests (real ants switch between quiet sleep and sleep with
# quick antenna flicks); idle_hot and walk_hot replace idle and walk on hot ground (with steam, the user's one
# unrealistic touch, drawn by the game, not the model).
LEG_SIDES = [(side, n) for n in (1, 2, 3) for side in ('l', 'r')]


def merged(*anims):
    """Several bone animations played together: the rotations of the same bone at the same time are added up
    (each animation must give a bone its key frames at the same times, or be a single still pose at time 0)."""
    out = {}
    for anim in anims:
        for bone, channels in anim.items():
            for channel, frames in channels.items():
                have = out.setdefault(bone, {}).setdefault(channel, {})
                if not have:
                    have.update(json.loads(json.dumps(frames)))
                    continue
                if len(frames) == 1 and '0' in frames:          # a still offset: added to every frame there is
                    add = frames['0']['vector']
                    for f in have.values():
                        f['vector'] = [round(a + b, 2) for a, b in zip(f['vector'], add)]
                elif len(have) == 1 and '0' in have:             # the other way round
                    add = have['0']['vector']
                    have.clear()
                    for t, f in frames.items():
                        have[t] = {'vector': [round(a + b, 2) for a, b in zip(f['vector'], add)], 'easing': IO}
                else:
                    raise ValueError(f'{bone} {channel}: two moving animations at different times')
    return out


def still(**bones):
    """A still pose: bone name -> rotation [x, y, z] (or 'body_pos' -> a position offset for the body)."""
    out = {}
    for name, vec in bones.items():
        if name == 'body_pos':
            out.setdefault('body', {}).update(keys([(0, vec)], 'position'))
        else:
            out[name] = keys([(0, vec)])
    return out


def side_z(side, angle):
    """A Z turn that lifts (positive angle) a leg on either side: a negative Z lifts a left leg, a positive one a right."""
    return -angle if side == 'l' else angle


# --- the midday rest ------------------------------------------------------------------------------------------------
REST_SECONDS = 6.0


def sleep_pose():
    """Real sleeping ants (fire ant workers studied by Cassill et al. 2009 nap about 250 times a day, a minute or so
    each): the body sinks onto the floor, the legs ease outward to let it down, the antennae fold back and droop so the
    whip lies on the ground, the head dips, the mandibles hang a little open. Nothing else moves but slow breathing."""
    pose = {'body_pos': [0, -0.45, 0], 'head': [8, 0, 0], 'mandible_l': [0, -9, 0], 'mandible_r': [0, 9, 0],
            'antenna_l_1': [24, -16, 0], 'antenna_r_1': [24, 16, 0], 'antenna_l_2': [34, 0, 0], 'antenna_r_2': [34, 0, 0]}
    for side, n in LEG_SIDES:
        pose[f'leg_{side}{n}'] = [0, 0, side_z(side, 14)]              # the leg lifts at the hip: the body comes down
        pose[f'leg_{side}{n}_lower'] = [0, 0, side_z(side, -10)]      # the shin turns in to keep the foot on the floor
    return still(**pose)


def breathing():
    T = REST_SECONDS
    return {'abdomen': keys([(0, [-4, 0, 0]), (T / 2, [-6.5, 0, 0]), (T, [-4, 0, 0])])}


def sleep():
    return merged(sleep_pose(), breathing())


def sleep_twitch():
    """As sleep, with the quick antenna flicks real ants show in their deeper sleep (Cassill's "rapid antennal movement"):
    now and then one whip twitches two or three times, and once a leg jerks."""
    T = REST_SECONDS
    flick_l = [(0, [0, 0, 0]), (1.4, [0, 0, 0]), (1.5, [-9, 0, 0]), (1.6, [0, 0, 0]), (1.7, [-7, 0, 0]), (1.8, [0, 0, 0]),
               (4.2, [0, 0, 0]), (4.3, [-6, 0, 0]), (4.4, [0, 0, 0]), (T, [0, 0, 0])]
    flick_r = [(0, [0, 0, 0]), (2.9, [0, 0, 0]), (3.0, [-8, 0, 0]), (3.1, [0, 0, 0]), (3.2, [-9, 0, 0]), (3.3, [0, 0, 0]),
               (3.4, [-5, 0, 0]), (3.5, [0, 0, 0]), (T, [0, 0, 0])]
    jerk = [(0, [0, 0, 0]), (5.0, [0, 0, 0]), (5.1, [0, 8, -6]), (5.3, [0, 0, 0]), (T, [0, 0, 0])]
    return merged(sleep_pose(), breathing(),
                  {'antenna_l_2': keys(flick_l), 'antenna_r_2': keys(flick_r), 'leg_l2': keys(jerk)})


# --- hot ground -----------------------------------------------------------------------------------------------------
# How high the body is held on hot ground (the peer session's pick, seen by the user in the viewer).
STILT_RAISE = 0.85


def stilts():
    """Stilting (real desert ants on hot sand, best known in Cataglyphis): the legs straighten to hold the body high
    above the hot ground, where the air is cooler. Since 2026-10-16 the legs are worked out by the motion library so
    every foot stays exactly where it stood."""
    turns, _, _ = mo.stand(rig(), {'body_pos': [0, STILT_RAISE, 0]})
    return still(body_pos=[0, STILT_RAISE, 0], **turns)


HOT_BODY = {'petiole': [14, 0, 0], 'abdomen': [12, 0, 0]}
# On stilts the straighter legs bring the knees closer together: a slightly shorter step keeps the shins apart.
HOT_SWEEP = 3.1
HOT_ANTENNAE = -10


def hot_look():
    """The gaster held up off the ground (Cataglyphis run with it raised), the antennae lifted off the hot sand."""
    return still(antenna_l_1=[HOT_ANTENNAE, 0, 0], antenna_r_1=[HOT_ANTENNAE, 0, 0], **HOT_BODY)


def walk_hot_clip():
    """Walking on hot ground: the tripod walk on stilts with the gaster up and the antennae lifted. (The steps are as
    quick as the walking speed makes them: a hot ant really does run faster, but that is the game's speed, not the
    clip's; see the notes.) Returns (bones, stride)."""
    tr, stride, _ = mo.walk(rig(), WALK_SECONDS, raise_by=STILT_RAISE, still=HOT_BODY, **dict(WALK, sweep=HOT_SWEEP))
    walking_antennae(tr, WALK_SECONDS, lift=HOT_ANTENNAE)
    return tr.bones(), stride


def walk_hot():
    return walk_hot_clip()[0]


def idle_hot():
    """Standing on hot ground: the stilt and the raised gaster, with today's idle (feelers searching) on top."""
    return merged(idle(), stilts(), hot_look())


# --- noticing (the animation engine, step 6, 2026-10-16) ----------------------------------------------------------
# A harvester ant that sees something move nearby stops, stands a little taller with its forebody tipped up, points
# both antennae at the thing and flicks them quickly (antennation: tasting the air for its smell), and turns and tilts
# its head to place it. The game turns the head toward what the ant looks at, on top of the clip. A wary ant (a shy one
# backing off) holds its head still on the thing, jaws wide open, its gaster curled a little under (where the sting is).
# My picks, to confirm: the forebody up 8 degrees (10 when wary) and the body 0.2 units higher on its legs, every foot
# where it stood (the motion library works the legs out); the antennae up and flicking 2.5 times a second (3.75 when
# wary); the head tilting about 8 degrees each way over 3.2 seconds; the jaws 24 degrees open when wary.
LOOK_SECONDS = 3.2
LOOK_RAISE = 0.2


def look_stance(lift, gaster=0):
    """Standing tall with the forebody tipped up by lift degrees (and the gaster turned by gaster), feet planted."""
    body = {'body_pos': [0, LOOK_RAISE, 0], 'forebody': [-lift, 0, 0]}
    turns, _, _ = mo.stand(rig(), body)
    pose = dict(turns, body_pos=[0, LOOK_RAISE, 0], forebody=[-lift, 0, 0])
    if gaster:
        pose['abdomen'] = [gaster, 0, 0]
    return still(**pose)


def look_antennae(track, seconds, flicks, lift, spread):
    """Both antennae held up and forward, flicking quickly out of step (flicks: cycles per clip)."""
    n = 8 * flicks
    for i in range(n + 1):
        t = seconds * i / n
        for side, ph, sy in (('l', 0.0, 1), ('r', 0.5, -1)):
            x = lift + mo.waves(t, seconds, [(flicks, 4, ph), (3, 3, ph + 0.2)])
            y = sy * (spread + mo.waves(t, seconds, [(flicks, 3, ph + 0.25), (2, 4, ph)]))
            whip = -4 + mo.waves(t, seconds, [(flicks, 5, ph - 0.1), (5, 2, ph + 0.3)])
            track.put(f'antenna_{side}_1', t, [x, y, 0])
            track.put(f'antenna_{side}_2', t, [whip, 0, 0])


def look():
    T = LOOK_SECONDS
    tr = mo.Track(rig())
    look_antennae(tr, T, flicks=8, lift=-6, spread=4)
    out = look_stance(8)
    out.update(tr.bones(tol=0.1))
    # The head: up a little, then cocked to one side, held, to the other side, held (a negative X tips it up).
    out['head'] = keys([(0, [-4, 0, 0]), (0.5, [-4, 8, -7]), (1.3, [-5, 8, -7]), (1.8, [-3, -1, 0]), (2.3, [-4, -8, 7]),
                        (2.9, [-5, -8, 7]), (T, [-4, 0, 0])])
    return out


def look_wary():
    T = LOOK_SECONDS
    tr = mo.Track(rig())
    look_antennae(tr, T, flicks=12, lift=0, spread=1)
    out = look_stance(10, gaster=-8)
    out.update(tr.bones(tol=0.1))
    out['head'] = keys([(0, [-6, 0, 0]), (1.6, [-6, 1.5, 0]), (T, [-6, 0, 0])])
    out['mandible_l'] = keys([(0, [0, -24, 0])])
    out['mandible_r'] = keys([(0, [0, 24, 0])])
    return out


# --- antenna cleaning (the animation engine, step 7, 2026-10-16) --------------------------------------------------
# Real ants clean each antenna with the comb on the front leg of the same side (the strigil: a notch of stiff bristles
# where the fore tibia meets the foot, closed by a spur; filmed in Camponotus by Hackmann et al. 2015, and described for
# ants by Farish 1972). The ant stops, bends its head down and toward that side and lowers the antenna, swung out to the
# side; it lays the whip in the comb near its base and draws it through to the tip, several strokes, the antenna
# lifting a little as it is pulled; between strokes the leg comes off and goes back to the base. Then it cleans the
# comb itself: the head turns to the leg and the leg is drawn through the open jaws (the mouthparts lick it clean).
# Then it stands as before. The other five feet stay where they stood all the while (the motion library works out the
# legs); the cleaning leg rises straight off the ground first and comes straight down onto its spot at the end.
# My picks, to confirm: 3 strokes of 0.45 s (0.3 s pulling, 0.15 s back to the base); the leg in the jaws 0.43 s (two
# draws); 2.6 s an antenna, both one after the other 5.2 s. While combing the forebody 5 degrees down and 4 toward the
# side, the head 12 down, 6 toward it and tilted 4; the antenna swung 30 out, its whip 20 down. At the jaws the
# forebody 13 up and turned 3.5 toward the leg, the head 6 up and turned 20 toward it (the leg drawn through the jaws
# from beside the head, so it never passes through them), the jaws 20 open and closing on each draw.
GROOM_SECONDS = 2.6
GROOM_STEP = 1 / 40                # the legs are worked out every 1/40 s
GROOM_LIFT = 0.8                   # how high the foot rises straight off the ground (units)
GROOM_SWING = 0.3                  # how far the ankle bows out, up and back between two holds (units)
GROOM_OUT = 0.45                   # the raised leg's thigh kept pointing at least this share of its length outward
GROOM_STROKES = [(0.1, 0.92), (0.14, 0.95), (0.1, 0.9)]   # each stroke from and to, as shares of the whip's length


def smooth(u):
    """0 to 1, easing in and out (u clamped to 0..1)."""
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def lerp(a, b, f):
    return [x + (y - x) * f for x, y in zip(a, b)]


def along(points, s):
    """The point s of the way along a line through these points (by length)."""
    lens = [mo.length(mo.sub(b, a)) for a, b in zip(points, points[1:])]
    left = sum(lens) * s
    for (a, b), ln in zip(zip(points, points[1:]), lens):
        if left <= ln:
            return mo.add(a, mo.mul(mo.sub(b, a), left / ln))
        left -= ln
    return points[-1]


def groom_side(tr, side, t0):
    """Cleaning the antenna on this side ('l' or 'r') into track tr, from time t0 for GROOM_SECONDS (see above).
    Returns the time it ends."""
    rig_ = rig()
    model = rig_.model
    s = 1 if side == 'l' else -1
    other = 'r' if side == 'l' else 'l'

    def m(x, y, z):                    # a turn given for the left side, mirrored for the right
        return [x, s * y, s * z]

    leg = rig_.legs[f'{side}1']
    stance = rig_.stance(f'{side}1')
    whip = [tuple(v * SCALE for v in (p if s > 0 else mirror(p))) for p in [ANTENNA_ELBOW] + ANTENNA_WHIP]

    def jaw(x):                        # a point between the jaws, x units out toward the cleaning side (design units)
        return tuple(v * SCALE for v in (s * x, 1.5, -6.1 + 0.33 * (x - 0.5)))

    rest = {b: [0, 0, 0] for b in ('forebody', 'head', f'antenna_{side}_1', f'antenna_{side}_2', f'antenna_{other}_1',
                                   f'antenna_{other}_2', 'mandible_l', 'mandible_r')}
    comb = dict(rest, forebody=m(5, -4, 0), head=m(12, -6, 4))
    comb.update({f'antenna_{side}_1': m(5, -30, 0), f'antenna_{side}_2': m(20, -10, 0),
                 f'antenna_{other}_1': [-6, -s * 8, 0], f'antenna_{other}_2': [-4, 0, 0]})   # the other one held up, forward
    jaws = dict(comb, forebody=m(-13, 3.5, 0), head=m(-6, -20, 1), mandible_l=[0, -20, 0], mandible_r=[0, 20, 0])
    jaws.update({f'antenna_{side}_1': m(-8, -10, 0), f'antenna_{side}_2': m(6, 0, 0)})
    pulled = {f'antenna_{side}_1': m(-6, 0, 0), f'antenna_{side}_2': m(-6, 0, 0), 'head': m(-3, 3, 0)}  # at a pull's end

    on, stroke, pull_s = 0.35, 0.45, 0.30          # the leg on the antenna; each stroke; its pull
    up = 0.14                                      # the foot clear of the ground
    mouth = on + 2 * stroke + pull_s               # the last pull ends: to the jaws
    in_jaws, draw, back = mouth + 0.3, 0.18, 0.07
    out_of_jaws = in_jaws + 2 * draw + back
    hover = GROOM_SECONDS - 0.12                   # over its spot, about to come straight down

    def with_pull(f):
        return {b: [a + f * c for a, c in zip(comb[b], pulled.get(b, [0, 0, 0]))] for b in comb}

    def body_at(t):
        """The pose of everything but the legs, and what the cleaning leg does: (pose, step, its share)."""
        if t < on:
            return {b: lerp(rest[b], comb[b], smooth(t / 0.3)) for b in rest}, 'lift', None
        if t < mouth:
            k = min(2, int((t - on) / stroke))
            u = t - on - k * stroke
            s0, s1 = GROOM_STROKES[k]
            if u < pull_s:                                         # drawing the whip through, base to tip
                f = smooth(u / pull_s)
                return with_pull(f), 'comb', (s0 + (s1 - s0) * f, 0.0)
            f = smooth((u - pull_s) / (stroke - pull_s))           # off the antenna and back to its base
            nxt = GROOM_STROKES[min(2, k + 1)][0]
            return with_pull(1 - f), 'comb', (s1 + (nxt - s1) * f, math.sin(math.pi * f))
        if t < in_jaws:
            f = smooth((t - mouth) / (in_jaws - mouth))
            start = with_pull(1)
            return {b: lerp(start[b], jaws[b], f) for b in comb}, 'to_jaws', f
        if t < out_of_jaws:
            u = t - in_jaws
            if u < draw:                                           # drawn out through the jaws, which close on it
                x, bite = 1.22 + 0.7 * smooth(u / draw), smooth(u / draw)
            elif u < draw + back:                                  # quickly back in, the jaws opening
                f = smooth((u - draw) / back)
                x, bite = 1.92 - 0.7 * f, 1 - f
            else:
                f = smooth((u - draw - back) / draw)
                x, bite = 1.22 + 0.7 * f, f
            pose = dict(jaws, mandible_l=[0, -20 + 12 * bite, 0], mandible_r=[0, 20 - 12 * bite, 0])
            return pose, 'jaws', x
        return {b: lerp(jaws[b], rest[b], smooth((t - out_of_jaws) / (GROOM_SECONDS - out_of_jaws))) for b in comb}, 'down', None

    def five(q):                       # joints with the hip's roll (0 when the solver was not asked for it)
        return tuple(q) + (0.0,) * (5 - len(q))

    def swing(qa, qb, f, pose, q_prev):
        """From joints qa to qb: the ankle carried on a straight line between where they put it, lifted outward, up and back
        on the way, the thigh kept out (so the leg never passes through the body)."""
        pa, pb = leg.points(five(qa), pose)[1], leg.points(five(qb), pose)[1]
        bump = GROOM_SWING * math.sin(math.pi * f)
        target = mo.add(lerp(pa, pb, f), (s * bump, bump, bump))    # +Z is back
        q_, _ = leg.reach_ankle(target, pose, five(q_prev), qa[3] + (qb[3] - qa[3]) * f, out=GROOM_OUT)
        return q_

    q = qs = None
    q_on = q_up = q_combed = q_in = q_out = q_over = None
    near = None
    for i in range(round(GROOM_SECONDS / GROOM_STEP) + 1):
        t = i * GROOM_STEP
        body, step, arg = body_at(t)
        pose = mo.pose_of(body)
        turns, qs, _ = mo.stand(rig_, body, guess=qs)               # every foot planted ...
        if step == 'lift':
            if q_on is None:
                start = mo.pose_of(body_at(on)[0])
                f0 = [model.world(f'antenna_{side}_2', p, start) for p in whip]
                q_on, _ = leg.reach_ankle(along(f0, GROOM_STROKES[0][0]), start, (30.0 * s, -30.0, -20.0, 15.0, 0.0), 15.0,
                                          out=GROOM_OUT)
            if t <= up:
                lift = GROOM_LIFT * smooth((t - 0.04) / (up - 0.04))
                q, _ = leg.reach((stance[0], stance[1] + lift, stance[2]), pose, q or qs[f'{side}1'])
                q = q_up = five(q)
            else:
                q = swing(q_up, q_on, smooth((t - up) / (on - up)), pose, q)
        elif step == 'comb':
            share, off = arg
            f = [model.world(f'antenna_{side}_2', p, pose) for p in whip]
            target = mo.add(along(f, share), (0.5 * s * off, -0.35 * off, 0.0))
            q, _ = leg.reach_ankle(target, pose, q, 15.0, out=GROOM_OUT)
            q_combed = q
        elif step == 'to_jaws':
            if q_in is None:
                jp = mo.pose_of(jaws)
                q_in, _ = leg.reach(model.world('head', jaw(1.22), jp), jp, (25.0 * s, -75.0, 15.0, 44.0, -34.0), 0.47, out=GROOM_OUT)
            q = swing(q_combed, q_in, arg, pose, q)
        elif step == 'jaws':
            q, _ = leg.reach(model.world('head', jaw(arg), pose), pose, q, 0.47, out=GROOM_OUT)
            q_out = q
        else:
            if q_over is None:
                q_over, _ = leg.reach((stance[0], stance[1] + GROOM_LIFT, stance[2]), mo.pose_of(body_at(hover)[0]), q_up)
                q_over = five(q_over)
            if t < hover:
                q = tuple(lerp(q_out, q_over, smooth((t - out_of_jaws) / (hover - out_of_jaws))))
            else:
                lift = GROOM_LIFT * (1 - smooth((t - hover) / (GROOM_SECONDS - hover)))
                q, _ = leg.reach((stance[0], stance[1] + lift, stance[2]), pose, q)
                q = five(q)
        bones = (leg.hip, leg.knee, leg.foot)                        # ... but the cleaning leg's
        near = leg.file_turns(q, near)
        turns.update(zip(bones, near))
        for bone, vec in list(body.items()) + list(turns.items()):
            tr.put(bone, t0 + t, vec)
    return t0 + GROOM_SECONDS


def groom_antenna(sides):
    """Cleaning the antennae on these sides, one after the other ('l', 'r' or 'lr')."""
    tr = mo.Track(rig())
    t = 0.0
    for side in sides:
        t = groom_side(tr, side, t)
    return tr.bones(tol=0.1)


def once(bones, seconds):
    """A clip played once (the game holds its last frame, the standing pose, until it changes clips)."""
    return {'loop': False, 'animation_length': seconds, 'bones': bones}


def walking(bones_stride, seconds):
    """A walking clip with its stride note (GeckoLib's timeline, read by the game's AntAnimationController)."""
    bones, stride = bones_stride
    return {'loop': True, 'animation_length': seconds, 'timeline': {'0': f'stride {stride:.4f}'}, 'bones': bones}


def animations():
    return {'format_version': '1.8.0', 'animations': {
        'idle': {'loop': True, 'animation_length': IDLE_SECONDS, 'bones': idle()},
        'walk': walking(walk_clip(), WALK_SECONDS),
        'sleep': {'loop': True, 'animation_length': REST_SECONDS, 'bones': sleep()},
        'sleep_twitch': {'loop': True, 'animation_length': REST_SECONDS, 'bones': sleep_twitch()},
        'idle_hot': {'loop': True, 'animation_length': IDLE_SECONDS, 'bones': idle_hot()},
        'walk_hot': walking(walk_hot_clip(), WALK_SECONDS),
        'look': {'loop': True, 'animation_length': LOOK_SECONDS, 'bones': look()},
        'look_wary': {'loop': True, 'animation_length': LOOK_SECONDS, 'bones': look_wary()},
        'groom_antenna_l': once(groom_antenna('l'), GROOM_SECONDS),
        'groom_antenna_r': once(groom_antenna('r'), GROOM_SECONDS),
        'groom_antenna_both': once(groom_antenna('lr'), 2 * GROOM_SECONDS)}}


# --- preview ------------------------------------------------------------------------------------------------------
def rotate(p, pivot, rot):
    """Turns point p about pivot by rot = [x, y, z] degrees, by the model file's rules (X first, then Y, then Z)."""
    x, y, z = (a - b for a, b in zip(p, pivot))
    rx, ry, rz = (math.radians(v) for v in rot)
    t = -rx
    y, z = y * math.cos(t) - z * math.sin(t), y * math.sin(t) + z * math.cos(t)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    t = -rz
    x, y = x * math.cos(t) - y * math.sin(t), x * math.sin(t) + y * math.cos(t)
    return (pivot[0] + x, pivot[1] + y, pivot[2] + z)


def model_boxes(bones, anim=None, tint=(1.0, 1.0, 1.0)):
    anim = anim or {}
    by_name = {b['name']: b for b in bones}
    colour = {(c * 8 + 1, r * 8 + 1): tuple(int(v * t) for v, t in zip(rgb, tint)) for c, r, rgb, _ in SWATCH.values()}
    boxes = []
    for b in bones:
        for c in b.get('cubes', []):
            (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
            corners = [(ox + i * sx, oy + j * sy, oz + k * sz) for i in (0, 1) for j in (0, 1) for k in (0, 1)]
            if 'rotation' in c:
                corners = [rotate(p, c['pivot'], c['rotation']) for p in corners]
            name = b['name']
            while name:
                if name in anim:
                    corners = [rotate(p, by_name[name]['pivot'], anim[name]) for p in corners]
                name = by_name[name].get('parent')
            boxes.append((corners, colour[tuple(c['uv']['north']['uv'])]))
    return boxes


def sample(bones_anim, t):
    """Every bone's rotation at time t (eased between the key frames as GeckoLib does: easeInOutSine where a key frame
    says so, else straight)."""
    out = {}
    for name, channels in bones_anim.items():
        if 'rotation' not in channels:
            continue
        frames = sorted((float(k), v['vector'] if isinstance(v, dict) else v, isinstance(v, dict) and v.get('easing') == IO)
                        for k, v in channels['rotation'].items())
        if len(frames) == 1 or t <= frames[0][0]:
            out[name] = frames[0][1]
            continue
        out[name] = frames[-1][1]
        for (t0, r0, _), (t1, r1, eased) in zip(frames, frames[1:]):
            if t0 <= t <= t1:
                f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                if eased:
                    f = -(math.cos(math.pi * f) - 1) / 2
                out[name] = [a + (b - a) * f for a, b in zip(r0, r1)]
                break
    return out


SHEETS = [
    ('front.png', 'FRONT', 0, 0, 34.0, 470, 330, 0.85),
    ('front_above.png', 'FRONT, FROM ABOVE ANGLED DOWN', 0, -35, 30.0, 470, 420, 0.65),
    ('side.png', 'SIDE', -90, 0, 30.0, 560, 260, 0.82),
    ('top.png', 'FROM ABOVE', -90, -89.9, 30.0, 560, 420, 0.5),
]


def render(models):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    for name, title, yaw, pitch, ppx, cw, ch, floor in SHEETS:
        canvas = tm.Canvas(cw * len(models), tm.TITLE_H + ch + tm.LABEL_H)
        canvas.text(12, 12, title, scale=4)
        for k, (label, boxes) in enumerate(models):
            x0, y0 = cw * k, tm.TITLE_H
            canvas.frame(x0, y0, cw, ch + tm.LABEL_H)
            canvas.text(x0 + 10, y0 + 8, label)
            tm.draw(canvas, boxes, (x0 + 1, y0 + tm.LABEL_H, cw - 2, ch - 1), floor, math.radians(yaw), math.radians(pitch), ppx)
        canvas.save(os.path.join(PREVIEW_DIR, name))


def render_walk(bones, frames=4):
    rows = [('SIDE', -90, 0, 30.0, 230, 0.82), ('FROM ABOVE', -90, -89.9, 26.0, 330, 0.5)]
    cw = 470
    canvas = tm.Canvas(cw * frames, tm.TITLE_H + sum(r[4] + tm.LABEL_H for r in rows))
    canvas.text(12, 12, 'WALK - ONE STEP', scale=4)
    anim = animations()['animations']['walk']['bones']
    y0 = tm.TITLE_H
    for title, yaw, pitch, ppx, ch, floor in rows:
        for k in range(frames):
            t = WALK_SECONDS * k / frames
            x0 = cw * k
            canvas.frame(x0, y0, cw, ch + tm.LABEL_H)
            canvas.text(x0 + 10, y0 + 8, f'{title} {int(t * 1000)} MS')
            tm.draw(canvas, model_boxes(bones, sample(anim, t)), (x0 + 1, y0 + tm.LABEL_H, cw - 2, ch - 1), floor,
                    math.radians(yaw), math.radians(pitch), ppx)
        y0 += ch + tm.LABEL_H
    canvas.save(os.path.join(PREVIEW_DIR, 'walk.png'))


def dump(rel, data):
    path = os.path.join(ROOT, rel)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1)


if __name__ == '__main__':
    worker = worker_model()
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.worker_ant', 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 3, 'visible_bounds_height': 2, 'visible_bounds_offset': [0, 0.5, 0]},
        'bones': worker}]}
    dump('geo/entity/worker_ant.geo.json', geo)
    dump('animations/entity/worker_ant.animation.json', animations())
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'worker_ant.png'))
    make_texture(os.path.join(ROOT, 'textures', 'entity', 'queen_ant.png'), QUEEN_TINT)
    render([('WORKER', model_boxes(worker)), ('QUEEN COLOURS', model_boxes(worker, tint=QUEEN_TINT))])
    render_walk(worker)
    print('ant model, animations, textures and preview pictures written')
