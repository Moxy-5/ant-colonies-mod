"""The Nile monitor (M9 river life), third start (2026-11-17), its 3D model in plain colours for the model viewer
(python tools/model_viewer.py monitor).

Built from the user's picture and the agreed flat outline (references/outlines/monitor.json) alone; nothing from the old
models. The user's rule for this one: only some parts need heavy detail. So:
- The tail, the neck and the face are made close to round: each is a chain of short pieces, every piece a rounded
  cross-section (three overlapping boxes, wide, tall and between, so it reads as round), and the chain is BENT a few
  degrees at every joint (bone rest rotations), so the tail curves down to the ground and the neck sweeps up into the
  head with no staircase. The head narrows into the snout in one-pixel slices, its top and bottom edges bevelled.
- The body stays a chunky box, as in the picture (that is its Minecraft look); the legs are round, tapering
  from the muscle to round joints (the user's ask, 2026-11-17).
- The paint (step 3) is worked out pixel by pixel from where each pixel lies on the model; the clips (step 4) are made here too, in clips().

Units are model pixels (16 a block), 96 long (6 blocks, the user's size for now). The head points to -Z, up is +Y,
left is +X; y = 0 is the ground. A rest rotation is given here as a pitch in degrees, positive turning the bone's
far end DOWN for the tail and UP for the neck (see pitch()).
"""
import json
import math
from pathlib import Path

from model_parts import (png, shade, smooth, pitch, mat_mul, transpose, turn_x, turn_y, rot, vsub, vadd,  # noqa: F401
                         vlen, unit, from_axes, mat_vec, euler, blend_turn, leg_frames, round_leg, bent_chain,
                         cube, rounded, paint_faces, geo, animations, write_assets,
                         block_moves, write_block_moves)  # png for the viewer

from motion_kit import (ease as _ease, pulse as _pulse, ramp as _ramp, spline as _spline,  # noqa: E402
                        bend_way as _bend_way, foot_at as _foot_at, two_bone, cached, fingerprint)

TEXELS = 1.0          # texture pixels a model pixel (plain colours for now)

# --- the outline's numbers (references/outlines/monitor.json, the approved outline; python tools/workshop.py outline
# monitor draws it), turned into the model's axes: z = HIPS - outline x, the hips where the tail meets the body --------
DATA_FILES = [Path(__file__).resolve().parent.parent / "references/outlines/monitor.json"]   # part of every clip's fingerprint
_OUTLINE = json.loads(DATA_FILES[0].read_text(encoding="utf-8"))
_PART = {p["name"]: p for p in _OUTLINE["parts"]}
HIPS = _PART["body"]["side"][0]                                     # 48: the tail is outline x 0 to 48
BODY_Z = (float(HIPS - _PART["body"]["side"][1]), float(HIPS - _PART["body"]["side"][0]))   # the body, front to back
BODY_Y = tuple(float(v) for v in _PART["body"]["side"][2:])         # belly to back
TRUNK_JOINTS = (-17.5, -22.0)  # where the front half's three pieces meet (waist | ribs | chest), back to front
BODY_HW = float(_PART["body"]["half_width"])                        # half width

TAIL_SEGS, TAIL_LEN = 12, float(HIPS)  # 12 pieces of 4 px
TAIL_AXIS = 11.0                       # the tail's middle line where it leaves the body
TAIL_R0, TAIL_R1 = 4.6, 0.6            # radius at the base and the tip
TAIL_LIES = 0.62                       # how far along the tail it is lying on the ground

NECK_SEGS = 2                          # two neck bones, each 4 px of round slices
NECK_AXIS = 11.5
NECK_Z0 = float(HIPS - _PART["neck"]["side"][0])
NECK_LEN = float(_PART["neck"]["side"][1] - _PART["neck"]["side"][0])
NECK_TURNS = (15.0, 3.0)               # how much each neck bone turns up from the one before (degrees)
HEAD_LEN = float(_PART["snout"]["side"][1] - _PART["head"]["side"][0])   # head and snout
HEAD_PITCH = -2.0                      # the head's own angle to level ground, a little nose down (degrees)


# --- the body: chunky boxes; the legs: round pieces ------------------------------------------------------------------------------
def body():
    z0, z1 = BODY_Z
    y0, y1 = BODY_Y
    zm = BODY_PIVOT[2]                                  # the body bends here: chest in front, hips behind

    def half(a, b):
        return [cube([-BODY_HW, y0, a], [BODY_HW, y1 - 1.0, b], 'skin'),
                cube([-BODY_HW + 0.7, y1 - 1.0, a], [BODY_HW - 0.7, y1, b], 'skin'),   # the back's edge softened
                cube([-4.5, 5.0, max(a, -21.0)], [4.5, y0, min(b, -7.0)], 'belly')]   # the belly hanging low
    # The root holds nothing; chest and hips turn at its pivot (the game bends them to the ground under them, so the
    # monitor curves over a bank's edge), with no rest turn, so every clip and the legs' reach work as before.
    # The front half is three short pieces, each hanging from the one behind (2026-11-18, the user: one stiff chest
    # made a peak or looked rigid going down a bank), so it bends in a curve: waist, ribs, then the chest that carries
    # the forelegs and the neck.
    za, zb = TRUNK_JOINTS
    return [{'name': 'body', 'pivot': list(BODY_PIVOT), 'cubes': []},
            {'name': 'waist', 'parent': 'body', 'pivot': list(BODY_PIVOT), 'cubes': half(za, zm)},
            {'name': 'ribs', 'parent': 'waist', 'pivot': [0.0, BODY_PIVOT[1], za], 'cubes': half(zb, za)},
            {'name': 'chest', 'parent': 'ribs', 'pivot': [0.0, BODY_PIVOT[1], zb], 'cubes': half(z0, zb)},
            {'name': 'hips', 'parent': 'body', 'pivot': list(BODY_PIVOT), 'cubes': half(zm, z1)}]


# Where each leg's joints sit (the left side; the right is the mirror), in model pixels: shoulder or hip, elbow or
# knee, wrist or ankle. The upper part runs out and down from the body, the elbow or knee sticking out to the side; the
# fore arm slants down and forward to the hand, the shin down and back to the foot (as in the user's picture), the
# feet flat and turned out, the toes fanned. Then each piece's roundness: 'upper' and 'lower' are its radius along it
# (fraction along, radius in pixels: thick at the muscle, slimmer toward the next joint), 'knob' and 'wrist' the radius
# of the round elbow or knee and wrist or ankle; then the foot's turn and palm, and the four toes (inner to outer; real monitors have five, four looks better):
# length and fan angle (outward +); every toe raised a little at the knuckle.
LEG_PLANS = {
    'arm': {'joints': ((4.0, 11.0, -21.5), (10.5, 7.5, -22.5), (10.8, 2.3, -25.0)),
            'upper': ((0.0, 2.0), (0.35, 2.2), (1.0, 1.55)), 'lower': ((0.0, 1.7), (0.25, 1.85), (1.0, 1.25)),
            'knob': 1.95, 'wrist': 1.55, 'turn': 18.0, 'palm': (4.6, 3.6),
            'toes': ((2.9, -32.0), (3.8, -9.0), (3.8, 13.0), (3.0, 36.0)), 'raise': 14.0},
    'leg': {'joints': ((4.0, 10.5, -4.0), (11.2, 8.5, -6.0), (11.8, 2.6, -2.6)),
            'upper': ((0.0, 2.3), (0.35, 2.55), (1.0, 1.8)), 'lower': ((0.0, 1.95), (0.25, 2.1), (1.0, 1.35)),
            'knob': 2.25, 'wrist': 1.75, 'hinge': True, 'turn': 38.0, 'palm': (5.0, 4.4),
            'toes': ((3.0, -28.0), (4.1, -6.0), (4.6, 17.0), (3.3, 44.0)), 'raise': 14.0},
}
CLAW_DROP = 23.0     # how far each claw bends down from its toe (degrees); the hook below curves it on to the ground
# A claw from root to point, as a hook in small steps (along its length from the toe's tip, width, bottom and top
# against the toe's middle line, colour): a dark sheath where it leaves the toe, thick and dark at the root, thinner and
# browner as it curves down, a dull horn point turning down at the end. Each step is quarter-pixel sized.
CLAW_SHAPE = (
    (-0.25, 0.30, 0.86, -0.43, 0.43, 'clawbase'),
    (0.30, 0.90, 0.64, -0.34, 0.34, 'claw'),
    (0.90, 1.40, 0.52, -0.38, 0.22, 'claw'),
    (1.40, 1.85, 0.40, -0.46, 0.02, 'clawmid'),
    (1.85, 2.20, 0.28, -0.64, -0.20, 'clawtip'),
    (2.10, 2.32, 0.16, -0.88, -0.42, 'clawtip'),
)


def legs():
    """The four legs (model_parts.round_leg): round upper and lower pieces aimed between the joints, a foot flat on the
    ground turned out, and four fanned toes, each raised a little at the knuckle with a long claw hooking down to the
    ground (its own little bone, so it can bend down while the toe lifts)."""
    return [b for s, side in ((1, 'left'), (-1, 'right')) for kind, plan in LEG_PLANS.items()
            for b in round_leg(kind, plan, s, side, 'chest' if kind == 'arm' else 'hips', CLAW_SHAPE, CLAW_DROP)]


# --- the tail: a bent chain of round pieces ----------------------------------------------------------------------------
def tail_radius(t):
    return TAIL_R0 + (TAIL_R1 - TAIL_R0) * t


def tail_height(t):
    """Where the tail's middle line should be (y) a fraction t along it: dropping from the body in a smooth curve until
    its underside rests on the ground."""
    r = tail_radius(t)
    return r + 0.25 + (TAIL_AXIS - TAIL_R0 - 0.25) * (1 - smooth(t / TAIL_LIES))


def tail():
    """A bent chain (model_parts.bent_chain) straight back from the body at TAIL_AXIS; each bone bends down by the
    angle that puts the curve where tail_height says."""
    return bent_chain('tail', 'hips', BODY_Z[1], TAIL_AXIS, TAIL_LEN, TAIL_SEGS, tail_radius, tail_height)


# --- the neck and head: round, rising, then a long smooth wedge into the snout ----------------------------------------
def neck():
    """Round one-pixel slices that change smoothly from the body's front (a broad round section, centred on the body)
    to exactly the head's back (narrower, centred higher), on two bones: the first turns up, the second a little more,
    so the neck sweeps up out of the shoulders. Each bone's first slice reaches back into the piece before it far enough
    to close the wedge its turn opens."""
    out, parent = [], 'chest'
    L = NECK_LEN / NECK_SEGS
    hw, htop, hbot = head_shape(0.0)
    start = (4.5, 4.7, (BODY_Y[0] + BODY_Y[1]) / 2 - NECK_AXIS)           # half width, half height, centre (relative)
    end = (hw, (htop - hbot) / 2, (htop + hbot) / 2)
    for i in range(NECK_SEGS):
        z0 = NECK_Z0 - i * L
        c = []
        n = 8                                   # half-pixel slices: the underside climbs 3 px, so no visible steps
        for k in range(n):
            a = z0 - k * L / n
            t = smooth((i * n + k + 0.5) / (NECK_SEGS * n))
            rw, rh, cy = (s0 + (s1 - s0) * t for s0, s1 in zip(start, end))
            back = 0.3 if k else rh * math.tan(math.radians(abs(NECK_TURNS[i]))) + 0.3
            rounded(c, 0.0, NECK_AXIS + cy, rw, rh, a - L / n, a + back, 'skin')
        name = f'neck{i + 1}'
        out.append({'name': name, 'parent': parent, 'pivot': [0.0, NECK_AXIS, z0],
                    'rotation': pitch(NECK_TURNS[i]), 'cubes': c})
        parent = name
    return out


def head_shape(t):
    """(half width, top, bottom) of the head a fraction t from its back (0) to the snout tip (1), heights relative to
    the neck's middle line. Even skull for the back third, then narrowing and sloping smoothly to a blunt snout."""
    f = max(0.0, (t - 0.3) / 0.7)
    w = 1.9 + 1.75 * (1 - smooth(f))
    top = 4.2 - 1.7 * f ** 1.25
    bottom = -1.9 + 0.9 * t
    return w, top, bottom


MOUTH = 0.5           # the mouth line, relative to the neck's middle line
GAPE = 0.14           # the mouth runs back to here (a fraction of the head), under the eye


def head():
    z0 = NECK_Z0 - NECK_LEN
    y = NECK_AXIS
    upper, jaw = [], []
    cuts = [0.0, 0.15, 0.3] + [0.3 + 0.7 * (k + 1) / 10 for k in range(10)]   # longer slices at the back, 1 px at the front
    for a, b in zip(cuts, cuts[1:]):
        t = (a + b) / 2
        w, top, bot = head_shape(t)
        if b == 1.0:                                     # the snout's very tip a little smaller: a rounded end
            w, top, bot = w * 0.85, top - 0.3, bot + 0.2
        za, zb = z0 - b * HEAD_LEN, z0 - a * HEAD_LEN + 0.2
        if a == 0.0:                                     # reach back into the neck to close the joint's wedge
            zb = z0 + top * math.tan(math.radians(abs(HEAD_PITCH - sum(NECK_TURNS)))) + 0.3
            w, top, bot = w * 0.96, top - 0.15, bot + 0.15
        rr = min(1.4, 0.42 * w)                          # the top edge, bevelled twice
        upper.append(cube([-w, y + MOUTH, za], [w, y + top - rr, zb], 'skin'))
        upper.append(cube([-(w - rr * 0.35), y + top - rr, za], [w - rr * 0.35, y + top - rr * 0.35, zb], 'skin'))
        upper.append(cube([-(w - rr), y + top - rr * 0.35, za], [w - rr, y + top, zb], 'skin'))
        rb, wj = min(1.2, 0.35 * w), w * 0.97                # the underside, bevelled twice
        target, part = (jaw if t > GAPE else upper), 'throat'     # behind the gape the throat is part of the skull
        target.append(cube([-wj, y + bot + rb, za], [wj, y + MOUTH, zb], part))
        target.append(cube([-(wj - rb * 0.4), y + bot + rb * 0.4, za], [wj - rb * 0.4, y + bot + rb, zb], part))
        target.append(cube([-(wj - rb), y + bot, za], [wj - rb, y + bot + rb * 0.4, zb], part))
    # the eyes, far back and high, under a brow ridge; the nostrils near the tip
    ez = z0 - 0.19 * HEAD_LEN
    w, top, _ = head_shape(0.19)
    extra = []
    for s, side in ((1, 'left'), (-1, 'right')):
        def xs(a, b):
            return sorted([s * a, s * b])
        # a narrow eye set in under a heavy brow: the brow juts out past it and slants down toward the snout (a hard,
        # hooded stare), a thick lower lid narrows it from below, and the pupil sits at the front, staring ahead
        def box(x0, x1, y0, y1, z0_, z1_, part, into=upper):
            into.append(cube([xs(x0, x1)[0], y + y0, ez + z0_], [xs(x0, x1)[1], y + y1, ez + z1_], part))
        box(w - 0.2, w + 0.12, 2.55, 3.2, -0.9, 0.7, 'eye')
        pupil, lid = [], []
        box(w + 0.12, w + 0.17, 2.6, 3.15, -0.62, -0.08, 'pupil', pupil)   # its own piece: it slides to glance
        box(w - 0.2, w + 0.19, 2.32, 2.6, -0.9, 0.7, 'brow', lid)          # the blinking lid, hidden in the lower lid;
        extra.append({'name': f'pupil_{side}', 'parent': 'head',            # a blink stretches it up over the eye
                      'pivot': [s * (w + 0.15), y + 2.875, ez], 'cubes': pupil})
        extra.append({'name': f'lid_{side}', 'parent': 'head', 'pivot': [s * w, y + 2.32, ez], 'cubes': lid})
        box(w - 0.2, w + 0.24, 2.3, 2.62, -1.0, 0.8, 'brow')            # the lower lid
        box(w - 1.3, w + 0.42, 3.15, 4.0, -0.2, 1.2, 'brow')            # the brow over the back of the eye
        box(w - 1.3, w + 0.42, 2.98, 3.85, -1.3, -0.2, 'brow')          # lower toward the snout: the scowl
        nw, ntop, _ = head_shape(0.88)
        nz = z0 - 0.88 * HEAD_LEN
        upper.append(cube([xs(nw - 1.1, nw - 0.35)[0], y + ntop - 0.75, nz - 0.4],      # on the top's side edge
                          [xs(nw - 1.1, nw - 0.35)[1], y + ntop + 0.1, nz + 0.4], 'nostril'))
    hinge = [0.0, y + MOUTH, z0 - GAPE * HEAD_LEN]
    # the teeth: a row of small pointed ivory teeth along each jaw, set just in from the lips (the upper row outside the
    # lower, the two staggered), hidden inside the closed mouth and showing when it opens
    k = 0
    zt = z0 - (GAPE + 0.07) * HEAD_LEN
    while zt > z0 - 0.9 * HEAD_LEN:
        t = (z0 - zt) / HEAD_LEN
        w = head_shape(min(1.0, t + 0.06))[0]            # the narrower of the slices it may touch
        for s in (1, -1):
            if k % 2 == 0:                                # an upper tooth hanging down
                upper.append(cube([min(s * (w - 0.78), s * (w - 0.33)), y + MOUTH - 0.7, zt - 0.22],
                                  [max(s * (w - 0.78), s * (w - 0.33)), y + MOUTH + 0.1, zt + 0.22], 'tooth'))
            else:                                         # a lower one pointing up, a little further in
                jaw.append(cube([min(s * (w - 1.2), s * (w - 0.78)), y + MOUTH - 0.1, zt - 0.2],
                                [max(s * (w - 1.2), s * (w - 0.78)), y + MOUTH + 0.55, zt + 0.2], 'tooth'))
        k += 1
        zt -= 0.65
    # the forked tongue, lying hidden along the mouth line inside the snout (it rides in the lower jaw, so an open
    # mouth shows it lying on the floor); a flick slides it out 7 pixels through the
    # closed lips, as a monitor's does
    tip = z0 - HEAD_LEN
    tongue = [cube([-0.25, y + MOUTH - 0.12, tip + 1.0], [0.25, y + MOUTH + 0.12, tip + 9.0], 'tongue')]
    for s in (1, -1):
        tongue.append(cube([min(s * 0.1, s * 0.42), y + MOUTH - 0.1, tip + 0.1],
                           [max(s * 0.1, s * 0.42), y + MOUTH + 0.1, tip + 1.4], 'tonguetip'))
    return [{'name': 'head', 'parent': f'neck{NECK_SEGS}', 'pivot': [0.0, y, z0],
             'rotation': pitch(HEAD_PITCH - sum(NECK_TURNS)), 'cubes': upper},
            {'name': 'jaw', 'parent': 'head', 'pivot': hinge, 'cubes': jaw},
            {'name': 'tongue', 'parent': 'jaw', 'pivot': [0.0, y + MOUTH, tip + 8.5], 'cubes': tongue}] + extra


def model():
    return body() + neck() + head() + tail() + legs()


# --- the model workshop (docs/plans/model-workshop.md, step 3): versions and sliders ----------------------------------
# The shape numbers the versions page shows a slider for (python tools/workshop.py serve monitor): the name of the
# number in this file (a tuple's item as NAME[i]), its label, lowest, highest and step. Only numbers the makers read
# when they run (not ones worked out from others at the top of the file), so moving one rebuilds the model fully.
WORKSHOP_SHAPE = [
    ('HEAD_LEN', 'Head length (model pixels)', 10, 20, 0.5),
    ('NECK_LEN', 'Neck length (model pixels)', 4, 14, 0.5),
    ('NECK_TURNS[0]', 'Neck lift at the shoulders (degrees)', 0, 30, 1),
    ('HEAD_PITCH', 'Head tilt, minus is nose down (degrees)', -10, 10, 0.5),
    ('TAIL_LEN', 'Tail length (model pixels)', 32, 64, 1),
    ('TAIL_R0', 'Tail thickness at the hips (model pixels)', 3, 6, 0.1),
    ('TAIL_LIES', 'Where the tail reaches the ground (share of its length)', 0.3, 0.9, 0.01),
]
# The parts the versions page can take from one version or the other: each part is its bones and everything below
# them, down to the next part's first bone.
WORKSHOP_PARTS = {'head': ['head'], 'neck': ['neck1'], 'body': ['body'], 'tail': ['tail1'],
                  'front legs': ['arm_upper_left', 'arm_upper_right'], 'hind legs': ['leg_upper_left', 'leg_upper_right']}


def workshop_look():
    """The model as the game has it (painted), for the workshop: bones, PNG bytes, texture width and height."""
    bones, rows, w, h = paint(model(), GAME_LOOK)
    return bones, png(rows), w, h


# --- the paint (step 3) --------------------------------------------------------------------------------------------
# Every texture pixel is coloured from where it lies on the model (each piece's own straight-out position: the tail laid
# straight back, the legs straight out and down), so a spot or band carries across the overlapping boxes of a round
# piece, and the pattern is drawn on one even grid of one model pixel everywhere, whatever a box's size.
LOOKS = {
    'nile': {'name': 'Painted', 'note': 'dark charcoal-olive scales with rows of yellow spots across the back, yellow '
             'rings down the tail, a speckled head, a pale yellow lip line, a cream throat and belly with dark bars, '
             'yellow-dotted legs, amber eyes'},
    'plain': {'name': 'Plain colours', 'note': 'shape only: charcoal skin, a cream throat and belly'},
}
GAME_LOOK = 'nile'
NOTES = [
    ('Tail', 'a chain of 12 short pieces, each a rounded cross-section of overlapping boxes, each turned a few degrees '
             'from the last, so it curves down off the hips and lies along the sand to a thin tip without stair steps'),
    ('Neck', 'round slices that change smoothly from the body to the back of the head, on two pieces tilted up, '
             'so it sweeps up from the shoulders and holds the head above the back'),
    ('Head', '14 pixels long. It narrows toward the snout in one-pixel slices, top and bottom edges bevelled, '
             'ending in a blunt rounded tip. The eyes sit far back under a brow ridge, the nostrils near the tip, '
             'and the lower jaw is its own piece, so it opens in the hiss. Inside the mouth: a pink ridged roof, a pale gum '
             'line along the lips, rows of small ivory teeth, a dark throat at the back, and the tongue lying on the floor'),
    ('Body', 'blocky on purpose, as in your picture: a deep body with the belly hanging low'),
    ('Legs', 'angled like your picture: the upper part slants out and down from the body, the elbow or knee sticking '
             'out to the side; the fore arm slants down and forward to the hand, the shin down and back to the foot'),
    ('Joints', 'each leg piece is round, cut in half-pixel slices that taper from the muscle to the next joint, and '
               'each elbow, knee, wrist and ankle is a round ball a little bigger than the ends meeting there, so '
               'nothing pokes out at the bend; the hind thigh and shin turn on one knee hinge'),
    ('Feet and claws', 'flat on the ground and turned out (the hind feet more), four long toes fanned out (real monitors have five; four picked by the user for the look), each raised '
                       'a little at the knuckle; each claw a hook in small steps, a dark sheath at the toe, thick and dark '
                       'at the root, thinner and browner as it curves down, a dull horn point touching the ground; every '
                       'toe and claw is its own piece, so the walk can spread and grip them later'),
    ('The paint', 'dark charcoal-olive scales, each pixel a shade different; rows of yellow spots across the back every '
                  '4 pixels, running down the flanks and breaking into a yellow net low on the sides; yellow rings down '
                  'the tail, spotty near the hips and solid toward the tip, the underside pale; a dark head speckled '
                  'yellow with a pale lip line; a cream throat and belly crossed by grey bars; yellow dots on the legs, '
                  'the feet darker; amber eyes with a dark pupil'),
    ('Clips', 'Walk: diagonal legs step together, the feet stay put on the ground, the body swings side to side, the '
              'head stays steady and the tail waves. Standing about: breathing, looking round, the eyes glancing, blinks '
              'and tongue flicks. Tongue: two flicks of the forked tongue. Swim: legs folded back along the body, the '
              'hind feet held out wide so the tail swings between them, the tail sweeping it along. Hiss: body raised, neck arched, mouth wide, the tongue lifting and quivering inside, the tail lashing'),
    ('Still to come', 'into the game: an attack and a death clip, and how it behaves (raiding crocodile nests)'),
]
CHARCOAL, CREAM = (62, 58, 54), (214, 196, 150)
SKIN = (52, 51, 43)          # charcoal with a little olive
SKIN_DARK = (40, 38, 33)
YELLOW = (214, 176, 58)
YELLOW_DULL = (150, 128, 62)
PALE = (222, 206, 150)       # throat and belly
BAR = (128, 120, 98)         # the grey bars across them
MOUTH_PINK = (196, 116, 118)  # inside the mouth
MOUTH_GUM = (206, 160, 140)   # the gum line along the lips
GULLET = (70, 30, 34)         # the throat at the back of the mouth


def _h(*k):
    """A steady random number 0 to 1 for a pixel of the grid (the same every build)."""
    n = 0x9E3779B1
    for v in k:
        n = ((n ^ (int(v) & 0xFFFFFFFF)) * 0x85EBCA6B) & 0xFFFFFFFF
        n ^= n >> 13
    n = (n * 0xC2B2AE35) & 0xFFFFFFFF
    return (n ^ (n >> 16)) / 0xFFFFFFFF


def _grain(c, gx, gy, gz, amount=0.07):
    return shade(c, 1 - amount + 2 * amount * _h(gx, gy, gz, 7))


def plain_colour(part, face):
    if part == 'eye':
        return (226, 186, 54)
    if part in ('nostril', 'pupil'):
        return (18, 16, 14)
    if part == 'tooth':
        return (232, 224, 196)       # ivory
    if part == 'tongue':
        return (58, 64, 92)          # a dark blue-grey tongue
    if part == 'tonguetip':
        return (84, 90, 122)
    if part == 'clawbase':
        return (28, 24, 21)
    if part == 'claw':
        return (46, 38, 33)
    if part == 'clawmid':
        return (88, 72, 55)
    if part == 'clawtip':
        return (156, 134, 102)
    if part in ('belly', 'throat') or face == 'down':
        return CREAM if face != 'up' else CHARCOAL
    return CHARCOAL


def colour(part, face, bone, x, y, z):
    """The painted colour at a point of a piece (its own straight-out model pixels)."""
    if part == 'eye':
        return (168, 92, 22)                             # a deep amber
    if part == 'brow':
        return _grain(SKIN_DARK, math.floor(abs(x)), math.floor(y), math.floor(z), 0.05)
    if part in ('nostril', 'pupil') or part.startswith(('claw', 'tongue')):
        return plain_colour(part, face)
    gx, gy, gz = math.floor(x), math.floor(y), math.floor(z)
    ax = math.floor(abs(x))                               # pixels out from the middle line (both sides alike)
    spot = _h(ax, gy, gz, 1)
    if bone in ('body', 'waist', 'ribs', 'chest', 'hips'):
        if part == 'belly' or face == 'down' or y < BODY_Y[0] + 0.6:
            return _grain(BAR if gz % 3 == 0 and _h(ax, gz, 5) < 0.8 else PALE, ax, gy, gz, 0.04)
        if y < BODY_Y[0] + 3.5:                           # low on the flank: a yellow net fading to the belly
            net = (gz % 2 == 0) != (gy % 2 == 0)
            return _grain(YELLOW_DULL if net and spot < 0.7 else SKIN, ax, gy, gz)
        if face == 'up':
            if (gz - 1) % 4 == 0 and spot < 0.62:        # rows of spots across the back, every 4 pixels
                return _grain(YELLOW, ax, gy, gz, 0.05)
        elif (gz - 1) % 4 in (0, 1) and (gy // 2 + gz // 4) % 2 == 0 and spot < 0.8:
            return _grain(YELLOW, ax, gy, gz, 0.05)      # on the flanks the rows break into 2-pixel spots
        if spot < 0.035:
            return _grain(YELLOW_DULL, ax, gy, gz)
        return _grain(SKIN_DARK if y > BODY_Y[1] - 0.5 else SKIN, ax, gy, gz)
    if bone.startswith('tail'):
        t = (z - BODY_Z[1]) / TAIL_LEN                   # 0 at the hips, 1 at the tip
        r = tail_radius(min(1.0, max(0.0, t)))
        if y - TAIL_AXIS < -0.5 * r or face == 'down':   # the pale underside, banded faintly
            return _grain(PALE if (gz // 3) % 2 else shade(PALE, 0.86), ax, gy, gz, 0.04)
        ring = gz % 6 in (1, 2) if t < 0.7 else gz % 5 in (1, 2)
        if ring and spot < 0.45 + 0.6 * t:               # spotty rings near the hips, solid toward the tip
            return _grain(YELLOW, ax, gy, gz, 0.05)
        if spot < 0.03:
            return _grain(YELLOW_DULL, ax, gy, gz)
        return _grain(SKIN, ax, gy, gz)
    if bone.startswith('neck'):
        if y - NECK_AXIS < -2.0 or face == 'down':       # the throat: cream with grey bars
            return _grain(BAR if gz % 2 == 0 and _h(ax, gz, 6) < 0.7 else PALE, ax, gy, gz, 0.04)
        if gz % 3 == 0 and (gy + gz // 3) % 2 == 0 and spot < 0.6:   # small rows of spots
            return _grain(YELLOW, ax, gy, gz, 0.05)
        return _grain(SKIN, ax, gy, gz)
    if bone in ('head', 'jaw'):
        # inside the mouth: a pink palate with ridges across it and a pink floor, a pale gum line along the lips, and a
        # dark throat at the back (seen only when the mouth opens)
        if part == 'tooth':
            return plain_colour(part, face)
        mouth_face = (face == 'down' and part == 'skin') or (face == 'up' and bone == 'jaw')
        if mouth_face and abs(y - (NECK_AXIS + MOUTH)) < 0.05:
            t = (NECK_Z0 - NECK_LEN - z) / HEAD_LEN
            if abs(x) > head_shape(min(1.0, max(0.0, t)))[0] * (0.97 if bone == 'jaw' else 1.0) - 1.0:
                return _grain(MOUTH_GUM, ax, gy, gz, 0.04)
            if bone == 'jaw':
                return _grain(MOUTH_PINK, ax, gy, gz, 0.05)
            return _grain(shade(MOUTH_PINK, 0.86) if gz % 2 == 0 else MOUTH_PINK, ax, gy, gz, 0.04)
        if part == 'throat' and bone == 'head' and face == 'north':
            return _grain(GULLET, ax, gy, gz, 0.05)
        if part == 'throat':
            if y > NECK_AXIS + MOUTH - 0.6 and face != 'down':
                return _grain(PALE, ax, gy, gz, 0.04)     # the lower lip
            return _grain(BAR if gz % 2 == 0 and _h(ax, gz, 6) < 0.6 else PALE, ax, gy, gz, 0.04)
        if y < NECK_AXIS + MOUTH + 0.35 and face != 'up':  # the pale upper lip line
            return _grain(shade(PALE, 0.92), ax, gy, gz, 0.04)
        ez = NECK_Z0 - NECK_LEN - 0.19 * HEAD_LEN        # the eye: a dark mask round it, a dark streak back from it
        if face != 'up' and ((abs(z - ez) < 2.0 and y > NECK_AXIS + 0.9) or
                             (ez < z < ez + 6.0 and NECK_AXIS + 2.0 < y < NECK_AXIS + 3.2)):
            return _grain(shade(SKIN_DARK, 0.8), ax, gy, gz, 0.05)
        if spot < 0.16:                                  # fine yellow speckles
            return _grain(YELLOW if spot < 0.08 else YELLOW_DULL, ax, gy, gz, 0.05)
        return _grain(SKIN_DARK if face == 'up' else SKIN, ax, gy, gz)
    # the legs: dark with yellow dots, the feet and toes darker
    if '_foot_' in bone or '_toe' in bone:
        if face == 'down':
            return _grain(SKIN_DARK, ax, gy, gz, 0.04)
        return _grain(YELLOW_DULL if spot < 0.06 else SKIN_DARK, ax, gy, gz)
    if spot < 0.14:
        return _grain(YELLOW if spot < 0.08 else YELLOW_DULL, ax, gy, gz, 0.05)
    return _grain(SKIN, ax, gy, gz)


def paint(bones, look='nile'):
    """(bones with every face's place on the texture, rows of (r, g, b, a), width, height)."""
    def pixel(c, face, px, py, pz):
        if look == 'plain':
            return plain_colour(c['_part'], face)
        return colour(c['_part'], face, c['_bone'], px, py, pz)
    return paint_faces(bones, TEXELS, pixel, to_byte=round)


# --- the clips (step 4): walk, idle, tongue, swim, hiss ----------------------------------------------------------------
# Every clip is worked out pose by pose from what the animal does, not keyed by hand: the feet are placed on the ground
# and the legs are bent to reach them (two-piece leg reaching: the elbow or knee bends out the way it does at rest), so
# a planted foot stays put while the body sways over it. A clip's value for a bone is its turn ADDED to the bone's rest
# turn, in the bone file's degrees (as GeckoLib and the viewer read them).
BODY_PIVOT = (0.0, TAIL_AXIS, -13.0)
WALK_LEN, SWIM_LEN = 1.2, 1.6          # one full stride, one full tail beat (seconds; the game will scale them by speed)


class _Poser:
    """Turns wanted bone turns (matrices) into clip values, and bends the legs to reach wanted foot places."""

    def __init__(self):
        bones = model()
        self.rest = {b['name']: b.get('rotation', [0.0, 0.0, 0.0]) for b in bones}
        self.last = {}
        self.worst = 0.0
        self.now, self.log = 0.0, None      # the clip's time, and (when a list) every reach miss as (time, leg, px)
        self.legs = []
        for s, side in ((1, 'left'), (-1, 'right')):
            for kind, plan in LEG_PLANS.items():
                (sx, sy, sz), (ex, ey, ez), (wx, wy, wz) = plan['joints']
                S, E, W = (s * sx, sy, sz), (s * ex, ey, ez), (s * wx, wy, wz)
                self.legs.append({'s': s, 'side': side, 'kind': kind, 'plan': plan, 'S': S, 'E': E, 'W': W,
                                  'l1': vlen(vsub(E, S)), 'l2': vlen(vsub(W, E)), 'foot': turn_y(-s * plan['turn']),
                                  'stand': _bend_way(vsub(E, S), unit(vsub(W, S))), 'reach': unit(vsub(W, S))})

    def value(self, name, m):
        """The clip value for a bone whose wanted turn (against its parent) is m: the difference from rest, kept
        within half a turn of the last value so the keys never spin round the long way."""
        r, rest = rot(m), self.rest[name]
        last = self.last.get(name, [0.0, 0.0, 0.0])
        v = []
        for a, b, l in zip(r, rest, last):
            d = a - b
            d += 360.0 * round((l - d) / 360.0)
            v.append(round(d, 3))
        self.last[name] = v
        return v

    def leg(self, p, L, body_m, body_d, wrist, foot_m, in_body=False, curl=0.0, raised=None, way=None):
        """Bends leg L so its wrist or ankle is at wrist and its foot turned foot_m (both on the ground's axes, or the
        body's if in_body), the body turned body_m about its pivot and moved body_d. The elbow or knee bends out the way
        it does at rest; or with raised (the climb) the way it points standing, raised that much higher against the
        line from the leg's root to its foot standing: so the leg stands exactly as in the walk, and a foot reaching up
        past its shoulder or hip never lines up with the way the joint bends (where it did, the elbow or knee flipped
        round to the other side between two keys, and the leg wound whole turns into the clip; 2026-11-19)."""
        s, plan, S = L['s'], L['plan'], list(L['S'])
        if in_body:
            Wb, Fb = list(wrist), foot_m
        else:
            Wb = vadd(mat_vec(transpose(body_m), vsub(vsub(wrist, body_d), BODY_PIVOT)), BODY_PIVOT)
            Fb = mat_mul(transpose(body_m), foot_m)
        if way is None:
            way = vsub(L['E'], S) if raised is None else [a - raised * b for a, b in zip(L['stand'], L['reach'])]
        E, Wb, miss = two_bone(S, L['l1'], L['l2'], Wb, way)
        if miss > 0.0:                                  # out of reach: as far as it goes (and remember by how much)
            self.worst = max(self.worst, miss)
            if self.log is not None:
                self.log.append((self.now, L['kind'][0] + L['side'][0], miss))
        r1, r2 = leg_frames(s, plan, S, E, Wb)
        name = f"{L['kind']}_%s_{L['side']}"
        p[name % 'upper'] = {'rotation': self.value(name % 'upper', r1)}
        p[name % 'lower'] = {'rotation': self.value(name % 'lower', mat_mul(transpose(r1), r2))}
        p[name % 'foot'] = {'rotation': self.value(name % 'foot', mat_mul(transpose(r2), Fb))}
        for i in range(len(plan['toes'])):
            p[name % f'claw{i + 1}'] = {'rotation': [round(curl, 3), 0.0, 0.0]}


# --- the climb up a block (the Animal Maker Engine step 2, stage B, 2026-11-19; docs/plans/engine-step2-climb.md) ------
# A move of its own, made once for a one-block step (every Minecraft step is exactly one block with square edges). The
# step is taller than the monitor's trunk is long (hip to shoulder), so it climbs it as a lizard climbs a wall: it stands
# with its snout just short of the face and looks up at the edge (a flick of the tongue), walks in rearing up on its hind
# legs until its forefeet reach up onto the top and hook over the edge, hauls itself up with its forelegs while its hind
# feet go onto the face, splayed out to the sides, and push, its body near upright against the face with the front half
# bending over the edge onto the top; then its hips roll over the edge, the hind feet step up onto the top, it walks on
# a step and stands (the tail is not the clip's: the game drags it up after the hips). In model pixels: the step is one
# block high, so its height in pixels hangs on the monitor's size (a clip for each of CLIMB_SIZES, the game plays the
# nearest). The root's travel is not in the clip but in its path ('path' note, and the move file made from it): the
# game moves the monitor along the path (its box on the server, its drawing on the client), so the end pose hands over
# to the walk with nothing to slide back. Every number my pick (second version, 2026-11-19, after the frame by frame
# films: the first looked straight up like a periscope, climbed as a stiff plank and folded the hind legs up under it).
GAME_SCALE = 0.75                       # MonitorEntity.MODEL_SCALE: the model is drawn at this share
CLIMB_SIZES = (0.85, 0.925, 1.0, 1.075, 1.15)
CLIMB_FACE = 50.0                       # the step's face this far ahead of where the climb starts (px): the snout 2 px short
CLIMB_END = 52.0                        # how far it has come at the end (px): its middle 2 px past the edge
HIP_JOINT = (0.0, 10.5, -4.0)           # the hip joint at rest (the leg plans): it rears about this
# How high the elbows and knees are held climbing (against the line from shoulder or hip to the standing foot; the
# standing pose is the walk's): picked as the tilt keeping each joint's bend farthest from every line its foot reaches
# along in the clip (at least 33 degrees off for the forelegs, 46 for the hind legs; the rest way came within 7)
CLIMB_RAISED = {'arm': 1.1, 'leg': 0.5}
UNDERSIDE = ((5.0, -21.0, -7.0), (6.0, -26.0, 0.0))   # the body's underside: the belly, then the trunk (height, from, to)
# The front half's three joints (where each piece turns, back to front, and its share of the bend over the edge). The game
# turns each on its outer edge so the pieces stay closed (MonitorModel: the top bending nose down, the bottom nose up):
# the clip is worked out turning them the same way, so the forefeet land where the clip puts them.
TRUNK_HINGES = (('waist', BODY_PIVOT[2], 0.3), ('ribs', TRUNK_JOINTS[0], 0.35), ('chest', TRUNK_JOINTS[1], 0.35))
BODY_TOP, BODY_BOTTOM = 16.0, 5.0


def block_px(size):
    """A block's height in model pixels on a monitor of this size."""
    return 16.0 / (GAME_SCALE * size)


def climb_name(size, down=False):
    return f'climb_{"down" if down else "up"}_{round(size * 1000)}'


# --- the climb down a block (stage C, 2026-11-19): the climb up's mirror, made the same way --------------------------
# It stops with its fore claws at the edge and dips its head to look down over it (a flick of the tongue), steps its
# forefeet to the lip, reaches them over and down the face (palms on the face, toes down) and on down to the lower
# ground, the chest tipping down after them while the hind legs brake on top (stepping up to the edge, toes hooked over
# it); then the hips go over the edge, the hind feet step down onto the face and on to the ground, it walks a step on
# and stands (the tail slides off the edge last, dragged by the game). In the clip's scene the top it starts on is at
# height 0 and the lower ground a block below (-B); the edge DOWN_EDGE ahead of where it starts. Every number my pick.
DOWN_EDGE = 30.0                        # the edge this far ahead of where the climb down starts (px): the fore claws at it
# How far it has come at the end (px): far enough that its box (0.35 blocks either side of its middle, 7.5 px at
# every size) is past the edge, so the game sets the box down on the lower ground with nothing of it inside the step
DOWN_END = 40.0


def _climb_keys(B):
    """The climb's course for a step B pixels high: each thing as (time, value) keys. The hind feet hold three places
    on the way up: the ground, the step's face (splayed out to the side, toes up against it), the top."""
    zf = -CLIMB_FACE
    up = B + 2.3                                     # a forefoot's wrist standing on top; a hind ankle 0.3 higher
    face = (B - 11.5, zf + 6.0, 85.0)                # a hind ankle on the face: its height, its place along, the foot turned toes up
    end = -4.0 - CLIMB_END
    k = B - block_px(1.0)                            # how much taller the step is for a smaller monitor than a middling one (px)
    return {
        # the hip joint's place along (z, forward is minus) and height; how far the hips are reared (degrees, nose up);
        # how far the front half bends down over the edge on top of that (degrees, nose down): each eased through its
        # keys without stopping at each (a monotone spline)
        'hips_z': ((0.2, -4.0), (0.7, -14.0), (1.2, -29.0), (1.6, -36.0), (1.9, -40.5), (2.45, -44.1), (2.8, -51.0), (3.15, end)),
        'hips_y': ((0.3, 10.5), (1.2, 10.8), (1.6, 12.5 + k), (1.9, 14.5 + k), (2.45, face[0] + 11.0), (2.8, B + 7.0),
                   (3.15, B + 10.5)),
        'rear': ((0.0, 0.0), (0.4, 4.0), (0.75, 9.0), (1.1, 38.0), (1.3, 46.0), (1.6, 55.0), (1.9, 64.0), (2.45, 70.0), (2.8, 35.0), (3.15, 0.0)),
        'bend': ((1.2, 0.0), (1.6, 10.0), (1.9, 25.0), (2.45, 45.0), (2.8, 25.0), (3.15, 0.0)),
        # where the neck and the head point (degrees above level, whatever the body does): looking up at the edge, then
        # across the top
        'neck': ((0.0, 18.0), (0.3, 42.0 + 3.5 * k), (0.9, 42.0 + 3.5 * k), (1.2, 28.0), (1.6, 20.0), (2.4, 16.0), (3.0, 18.0)),
        'head': ((0.0, -2.0), (0.3, 28.0 + 2.0 * k), (0.9, 26.0 + 2.0 * k), (1.2, 6.0),
                 (1.5, -2.0), (2.6, -6.0), (3.1, -2.0)),
        'flicks': (0.1, 2.75),
        # the feet: where each wrist or ankle starts, then its steps (lift, land, place); a place may carry the foot's
        # turn (degrees, toes up)
        ('arm', 'left'): ((10.8, 2.3, -25.0), (0.3, 0.55, (10.8, 2.3, -33.0)), (0.75, 1.2, (9.5, up, zf - 1.5)),
                          (2.5, 2.8, (10.3, up, -64.0)), (2.85, 3.15, (10.8, up, end - 21.0))),
        ('arm', 'right'): ((-10.8, 2.3, -25.0), (0.4, 0.65, (-10.8, 2.3, -35.0)), (0.8, 1.3, (-9.5, up, zf - 1.5)),
                           (2.6, 2.9, (-10.3, up, -66.0)), (2.95, 3.25, (-10.8, up, end - 21.0))),
        ('leg', 'left'): ((11.8, 2.6, -2.6), (0.55, 0.85, (11.8, 2.6, -16.0)), (0.95, 1.25, (11.8, 2.6, -28.0)),
                          (1.45, 1.75, (11.5, face[0], face[1], face[2])), (2.55, 2.85, (11.8, up + 0.3, end + 1.4))),
        ('leg', 'right'): ((-11.8, 2.6, -2.6), (0.4, 0.7, (-11.8, 2.6, -14.0)), (0.8, 1.1, (-11.8, 2.6, -25.0)),
                           (1.35, 1.65, (-11.5, face[0], face[1], face[2])), (2.45, 2.75, (-11.8, up + 0.3, end + 1.4))),
        'length': 3.3,
    }


def _descend_keys(B):
    """The climb down's course from a top B pixels above the lower ground (keys as _climb_keys; heights from the top)."""
    ze = -DOWN_EDGE
    low = 2.3 - B                                    # a forefoot's wrist standing on the lower ground; a hind ankle 0.3 higher
    end = -4.0 - DOWN_END
    k = B - block_px(1.0)                            # how much taller the drop is for a smaller monitor than a middling one (px)
    return {
        'hips_z': ((0.3, -4.0), (1.0, -12.0), (1.5, -20.5), (2.0, -27.5 - 0.3 * k), (2.4, -33.0 - 0.6 * k), (2.8, -38.0 - 0.3 * k), (3.15, -41.5),
                   (3.45, end)),
        'hips_y': ((0.3, 10.5), (1.0, 10.2), (1.5, 9.5), (2.0, 7.0 - 0.3 * k), (2.4, 2.0 - 0.6 * k), (2.8, -5.0 - k),
                   (3.15, 10.5 - B + 0.4), (3.45, 10.5 - B)),
        'rear': ((0.0, 0.0), (0.6, -2.0), (1.0, -8.0), (1.5, -20.0), (2.0, -38.0), (2.4, -46.0), (2.8, -36.0),
                 (3.15, -10.0), (3.45, 0.0)),
        'bend': ((0.6, 0.0), (1.0, 18.0), (1.5, 45.0), (2.0, 30.0), (2.4, 0.0), (2.8, -20.0), (3.15, -8.0), (3.45, 0.0)),
        'neck': ((0.0, 18.0), (0.3, -15.0), (0.9, -15.0), (1.3, 2.0), (2.0, -8.0), (2.4, 0.0), (2.9, 12.0), (3.4, 18.0)),
        'head': ((0.0, -2.0), (0.3, -45.0), (0.9, -50.0), (1.3, -30.0), (2.0, -15.0), (2.4, -8.0), (2.9, -2.0)),
        'flicks': (0.4, 3.0),
        ('arm', 'left'): ((10.8, 2.3, -25.0), (0.45, 0.75, (10.8, 2.3, -27.5)), (1.0, 1.4, (10.0, -4.0, ze - 3.2, -75.0)),
                          (2.05, 2.4, (10.0, low, ze - 12.0)), (2.6, 2.85, (10.3, low, -52.0)), (3.05, 3.35, (10.8, low, end - 21.0))),
        ('arm', 'right'): ((-10.8, 2.3, -25.0), (0.55, 0.85, (-10.8, 2.3, -27.5)), (1.1, 1.5, (-10.0, -4.0, ze - 3.2, -75.0)),
                           (2.15, 2.5, (-10.0, low, ze - 12.0)), (2.7, 2.95, (-10.3, low, -54.0)), (3.12, 3.42, (-10.8, low, end - 21.0))),
        ('leg', 'left'): ((11.8, 2.6, -2.6), (0.9, 1.2, (11.8, 2.6, -12.0)), (1.55, 1.85, (11.8, 2.6, ze + 5.0)),
                          (2.5 - 0.04 * k, 2.8 - 0.04 * k, (11.5, -8.5, ze - 5.0, -75.0)), (3.05, 3.35, (11.8, low + 0.3, end + 1.4))),
        ('leg', 'right'): ((-11.8, 2.6, -2.6), (0.8, 1.1, (-11.8, 2.6, -11.0)), (1.45, 1.75, (-11.8, 2.6, ze + 5.0)),
                           (2.6 - 0.04 * k, 2.9 - 0.04 * k, (-11.5, -8.5, ze - 5.0, -75.0)), (3.12, 3.42, (-11.8, low + 0.3, end + 1.4))),
        'length': 3.45,
    }


# --- going into and out of the water (the Animal Maker Engine step 4, 2026-11-19; docs/plans/engine-step4-water.md) ----
# Made as the climbs are: each a move of its own, made once (per size), the game moving the monitor along the move's own
# path; the tail is the game's (dragged, TailDrag; afloat it lies along the water's surface). A Minecraft river's water
# is source blocks, its surface 1/9 of a block under the top of its block, so a bank is either level with the water's
# block (its top a ninth of a block over the surface) or a whole number of blocks higher: one move for each. In a move's
# scene the bank's top is at height 0 (a block higher for the climb out a block up), the water's block's top at 0 too.
# Going in, it ends afloat as the swim floats it (its feet's line WATER_FLOAT under the surface) in the swim's first pose
# and gliding on, so the swim goes on from it; coming out, it starts afloat in the swim's first pose. Every number my
# pick (fun and good-looking first: the user, 2026-11-19, "it's an animal").
WATER_FLOAT = 12.0          # afloat, its feet's line this far under the surface (px; MonitorEntity.AFLOAT: 12 px at every size)
TAIL_FLOAT = 1.0            # afloat, the tail's middle line this far under the surface (the swim's straight tail; MonitorEntity.TAIL_FLOAT)
WATER_EDGE = 30.0           # going in, the bank's edge this far ahead of where it starts (px): its fore claws at it
WATER_FACE = 50.0           # coming out, the bank's face this far ahead of where it starts (px): its snout 3 px short
WATER_FACE_UP = 58.0        # climbing out a block up, further (px): room to rear as it swims the last of the way in
WATER_KINDS = ('in', 'drop', 'drop2', 'dash', 'out', 'outup')
GLIDE = 0.035               # how fast it swims calmly (blocks a tick: measured in the course, 2026-11-19); a dash, RUN on land
RUN, DASH_SWIM = 0.077, 0.11


def water_name(kind, size):
    return f'water_{kind}_{round(size * 1000)}'


def px_per_tick(blocks, size):
    """A speed in blocks a tick as model pixels a second, at this size."""
    return blocks * 20.0 * block_px(size)


def water_scene(kind, B):
    """The scene a water move is made in: the bank (its top's height, its edge or face along z, which side it is on: going
    in it is behind the edge, coming out ahead of the face), the water's surface and the bottom (one block deep under the
    water's block: the shallowest that floats it)."""
    s0 = -B / 9.0
    h = {'drop': 1, 'drop2': 2}.get(kind, 0)
    if kind in ('out', 'outup'):
        return {'kind': kind, 'edge': -(WATER_FACE_UP if kind == 'outup' else WATER_FACE), 'land_ahead': True, 'top': B if kind == 'outup' else 0.0,
                'surface': s0, 'bottom': -B, 'float': s0 - WATER_FLOAT}
    return {'kind': kind, 'edge': -(IN_EDGE if kind == 'in' else WATER_EDGE), 'land_ahead': False, 'top': 0.0, 'surface': s0 - h * B, 'bottom': -(h + 1) * B,
            'float': s0 - h * B - WATER_FLOAT}


def scene_solid(sc, y, z):
    """How deep (px) a point lies inside the bank or the bottom (0 if not)."""
    land = z < sc['edge'] if sc['land_ahead'] else z > sc['edge']
    if land:
        if y < sc['top']:
            return min(sc['top'] - y, abs(z - sc['edge']))
        return 0.0
    return max(0.0, sc['bottom'] - y)


def scene_floor(sc, z):
    """What the body's underside must clear at z: the bank's top over the bank, the bottom over the water."""
    land = z < sc['edge'] - 0.2 if sc['land_ahead'] else z > sc['edge'] + 0.2
    return sc['top'] if land else sc['bottom']


def _water_keys(kind, B, size):
    """A water move's course (keys as _climb_keys; 'fold' says how far each leg is folded for swimming, 0 to 1, and
    'swim' when the swim's own motion eases in at the end or out at the start)."""
    sc = water_scene(kind, B)
    F, ze = sc['float'], sc['edge']
    glide = px_per_tick(GLIDE, size)
    if kind == 'in':
        return _walk_in_keys(B, size)
    if kind == 'dash':
        # Running in (scared): no stop; it runs off the edge, plunging head first, the forelegs sweeping back at once,
        # the hind legs giving a last shove off the lip and folding, the body following its head under the surface and
        # gliding off fast. 1.3 s.
        v0, dash = px_per_tick(RUN, size), px_per_tick(DASH_SWIM, size)
        t_edge = 26.0 / v0
        return {
            'hips_z': ((0.0, -4.0), (t_edge, -30.0), (1.3, -30.0 - 0.75 * dash * (1.3 - t_edge) - 4.0),
                       (1.9, -30.0 - 0.75 * dash * (1.3 - t_edge) - 4.0 - 0.6 * dash)),
            'hips_z_start': -v0, 'hips_z_end': -px_per_tick(DASH_SWIM, size),
            'hips_y': ((0.0, 10.5), (0.45, 10.0), (t_edge + 0.05, 6.8), (t_edge + 0.3, F + 9.0), (1.3, F + 10.0), (1.9, F + 10.5)),
            'rear': ((0.0, 0.0), (0.35, -5.0), (t_edge, -20.0), (t_edge + 0.25, -10.0), (1.3, 0.0)),
            'bend': ((0.0, 0.0), (0.3, 14.0), (0.55, 22.0), (t_edge + 0.05, 2.0), (1.1, -6.0), (1.3, 0.0)),
            'neck': ((0.0, 18.0), (0.3, 6.0), (0.65, -10.0), (1.0, 4.0), (1.3, 14.0)),
            'head': ((0.0, -2.0), (0.35, -18.0), (0.7, -26.0), (1.0, -8.0), (1.3, -2.0)),
            'flicks': (),
            ('arm', 'left'): ((10.8, 2.3, -25.0), (0.03, 0.3, (10.8, 0.5, -36.0))),
            ('arm', 'right'): ((-10.8, 2.3, -25.0), (0.12, 0.4, (-10.8, 0.0, -38.0))),
            ('leg', 'left'): ((11.8, 2.6, -2.6), (0.12, 0.35, (11.8, 2.6, -17.0)), (0.48, t_edge - 0.05, (11.8, 2.6, -28.5))),
            ('leg', 'right'): ((-11.8, 2.6, -2.6), (0.0, 0.22, (-11.8, 2.6, -10.0)), (0.27, 0.47, (-11.8, 2.6, -19.0)),
                               (0.53, 0.74, (-11.8, 2.6, -28.0))),
            'hang': {('arm', 'left'): ((0.42, 0.0), (0.67, 1.0)), ('arm', 'right'): ((0.5, 0.0), (0.75, 1.0)),
                     ('leg', 'left'): ((t_edge + 0.14, 0.0), (t_edge + 0.39, 1.0)), ('leg', 'right'): ((t_edge + 0.11, 0.0), (t_edge + 0.36, 1.0))},
            'fold': {('arm', 'left'): ((0.9, 0.0), (1.45, 1.0)), ('arm', 'right'): ((0.95, 0.0), (1.5, 1.0)),
                     ('leg', 'left'): ((1.3, 0.0), (1.9, 1.0)), ('leg', 'right'): ((1.25, 0.0), (1.85, 1.0))},
            'swim': (1.0, 1.9, 'in'),
            'knees': KNEES, 'elbows': ELBOWS,
            'length': 1.9,
        }
    if kind == 'drop':
        # Sliding in off a bank a block over the water's block, as it climbs down a block (the climb down's course, the
        # user's OK): stops with its fore claws at the edge, looks down at the water, steps its forefeet over and down the
        # face (palms on it, toes down), the chest tipping down after them while the hind legs brake on top; the forefeet
        # reach on down into the water and find no bottom, hanging; the head and chest slide in, the hind feet come down
        # the face and let go, hanging; the body levels out afloat and glides on, the legs folding a while after. 5.1 s.
        k = B - block_px(1.0)
        return {
            'hips_z': ((0.3, -4.0), (1.0, -12.0), (1.5, -20.5), (2.0, -27.5 - 0.3 * k), (2.4, -33.0 - 0.6 * k),
                       (2.8, -38.0 - 0.3 * k), (3.2, -43.0), (3.6, -48.0), (3.9, -51.5), (5.1, -51.5 - 1.2 * glide)),
            'hips_z_end': -glide,
            'hips_y': ((0.3, 10.5), (1.0, 10.2), (1.5, 9.5), (2.0, 7.0 - 0.3 * k), (2.4, 0.0 - 0.6 * k), (2.8, -9.0 - k),
                       (3.2, F + 13.0), (3.6, F + 11.0), (3.9, F + 10.5)),
            'rear': ((0.0, 0.0), (0.6, -2.0), (1.0, -8.0), (1.5, -20.0), (2.0, -38.0), (2.4, -46.0), (2.8, -38.0),
                     (3.2, -20.0), (3.6, -5.0), (3.9, 0.0)),
            'bend': ((0.6, 0.0), (1.0, 18.0), (1.5, 45.0), (2.0, 30.0), (2.4, 0.0), (2.8, -15.0), (3.2, -10.0), (3.6, 0.0)),
            'neck': ((0.0, 18.0), (0.3, -15.0), (0.9, -15.0), (1.3, 2.0), (2.0, -8.0), (2.4, 0.0), (2.9, 12.0), (3.4, 18.0)),
            'head': ((0.0, -2.0), (0.3, -45.0), (0.9, -50.0), (1.3, -30.0), (2.0, -15.0), (2.4, -8.0), (2.9, -2.0)),
            'flicks': (0.4,),
            ('arm', 'left'): ((10.8, 2.3, -25.0), (0.45, 0.75, (10.8, 2.3, -27.5)), (1.0, 1.4, (10.0, -4.0, ze - 3.2, -75.0)),
                              (2.05, 2.4, (10.0, -B, ze - 12.0))),
            ('arm', 'right'): ((-10.8, 2.3, -25.0), (0.55, 0.85, (-10.8, 2.3, -27.5)), (1.1, 1.5, (-10.0, -4.0, ze - 3.2, -75.0)),
                               (2.15, 2.5, (-10.0, -B, ze - 12.0))),
            ('leg', 'left'): ((11.8, 2.6, -2.6), (0.9, 1.2, (11.8, 2.6, -12.0)), (1.55, 1.85, (11.8, 2.6, ze + 5.0)),
                              (2.5 - 0.04 * k, 2.8 - 0.04 * k, (11.5, -8.5, ze - 5.0, -75.0))),
            ('leg', 'right'): ((-11.8, 2.6, -2.6), (0.8, 1.1, (-11.8, 2.6, -11.0)), (1.45, 1.75, (-11.8, 2.6, ze + 5.0)),
                               (2.6 - 0.04 * k, 2.9 - 0.04 * k, (-11.5, -8.5, ze - 5.0, -75.0))),
            'hang': {('arm', 'left'): ((2.5, 0.0), (2.9, 1.0)), ('arm', 'right'): ((2.6, 0.0), (3.0, 1.0)),
                     ('leg', 'left'): ((2.95, 0.0), (3.35, 1.0)), ('leg', 'right'): ((3.0, 0.0), (3.4, 1.0))},
            'fold': {('arm', 'left'): ((3.6, 0.0), (4.4, 1.0)), ('arm', 'right'): ((3.7, 0.0), (4.5, 1.0)),
                     ('leg', 'left'): ((4.0, 0.0), (5.0, 1.0)), ('leg', 'right'): ((3.9, 0.0), (4.9, 1.0))},
            'swim': (3.9, 5.1, 'in'),
            'raised': True,             # (the elbows and knees held as the climb down holds them: low, an elbow flipped)
            'length': 5.1,
        }
    if kind == 'drop2':
        # Diving in off a bank two blocks over the water's block (hanging down a face twice its height looked wrong; a
        # monitor drops into water off banks and branches): stops at the edge, looks down at the water (a flick of the
        # tongue), steps its forefeet onto the lip and crouches; springs off head first, the legs hanging loose as it
        # falls, the chest going in first with a splash, the body going a little under and rising to float level, gliding
        # on, the legs folding a while after. 4.6 s.
        surf = sc['surface']
        return {
            'hips_z': ((0.3, -4.0), (1.0, -12.0), (1.45, -18.0), (1.75, -20.0), (2.0, -29.0), (2.25, -40.0), (2.5, -51.0),
                       (2.8, -60.0), (3.2, -67.0), (4.6, -67.0 - 1.4 * glide)),
            'hips_z_end': -glide,
            'hips_y': ((0.3, 10.5), (1.0, 10.2), (1.45, 8.5), (1.75, 7.0), (2.0, 12.0), (2.2, 9.5), (2.4, -6.0),
                       (2.6, surf - 2.0), (2.8, F + 10.0), (3.1, F + 9.2), (3.6, F + 10.2), (4.0, F + 10.5)),
            'rear': ((0.0, 0.0), (0.6, -2.0), (1.0, -8.0), (1.45, -14.0), (1.75, -18.0), (2.0, -12.0), (2.3, -30.0),
                     (2.45, -32.0), (2.6, -20.0), (2.8, -9.0), (3.1, -3.0), (3.6, 0.0)),
            'bend': ((1.0, 0.0), (1.45, 8.0), (1.75, 6.0), (2.0, 0.0), (2.5, 8.0), (2.9, -10.0), (3.3, 0.0)),
            'neck': ((0.0, 18.0), (0.3, -15.0), (0.9, -15.0), (1.45, -6.0), (1.75, -2.0), (2.4, 8.0), (3.0, 16.0), (3.4, 18.0)),
            'head': ((0.0, -2.0), (0.3, -45.0), (0.9, -50.0), (1.45, -35.0), (1.75, -25.0), (2.4, -10.0), (3.0, -2.0)),
            'flicks': (0.4,),
            ('arm', 'left'): ((10.8, 2.3, -25.0), (0.45, 0.75, (10.8, 2.3, -27.5)), (1.05, 1.35, (10.8, 2.3, ze + 1.5))),
            ('arm', 'right'): ((-10.8, 2.3, -25.0), (0.55, 0.85, (-10.8, 2.3, -27.5)), (1.15, 1.45, (-10.8, 2.3, ze + 1.0))),
            ('leg', 'left'): ((11.8, 2.6, -2.6), (0.9, 1.2, (11.8, 2.6, -11.0)), (1.3, 1.6, (11.8, 2.6, -17.5))),
            ('leg', 'right'): ((-11.8, 2.6, -2.6), (0.8, 1.1, (-11.8, 2.6, -10.0)), (1.2, 1.5, (-11.8, 2.6, -16.5))),
            'hang': {('arm', 'left'): ((1.85, 0.0), (2.2, 1.0)), ('arm', 'right'): ((1.9, 0.0), (2.25, 1.0)),
                     ('leg', 'left'): ((1.98, 0.0), (2.33, 1.0)), ('leg', 'right'): ((1.98, 0.0), (2.33, 1.0))},
            # (the forelegs tucked back as it hits the water, the dive's streamlining: hanging they struck the bottom of
            # a one-block pool)
            'fold': {('arm', 'left'): ((2.25, 0.0), (3.05, 1.0)), ('arm', 'right'): ((2.3, 0.0), (3.1, 1.0)),
                     ('leg', 'left'): ((3.6, 0.0), (4.6, 1.0)), ('leg', 'right'): ((3.55, 0.0), (4.55, 1.0))},
            'swim': (3.4, 4.6, 'in'),
            'raised': True,
            'length': 4.6,
        }
    if kind == 'out':
        # Coming out onto a level bank: it swims up to the bank, its head lifting over the top, the forefeet unfold and
        # reach out onto the top (claws in), the chest rises out of the water, it hauls; the hind feet unfold onto the
        # bank's face under water and push, the hips slide up over the lip, the hind feet step up onto the top, it
        # walks a step on and stands, the water running off it (the game's drips) and the tail dragged out last. 4.6 s.
        end = -64.0
        zf = ze
        return {
            'hips_z': ((0.0, -4.0), (0.8, -14.0), (1.4, -20.5), (2.0, -25.0), (2.6, -32.0), (3.1, -40.0), (3.6, -48.0),
                       (4.1, -56.0), (4.6, end)),
            'hips_z_start': -glide,
            'hips_y': ((0.0, F + 10.5), (1.4, F + 11.0), (2.0, F + 13.0), (2.6, F + 16.5), (3.1, 6.0), (3.6, 9.2), (4.1, 10.5)),
            'rear': ((0.0, 0.0), (0.8, 6.0), (1.4, 20.0), (2.0, 28.0), (2.6, 28.0), (3.1, 20.0), (3.6, 9.0), (4.1, 0.0)),
            'bend': ((1.4, 0.0), (2.0, 8.0), (2.6, 16.0), (3.1, 12.0), (3.6, 4.0), (4.1, 0.0)),
            'neck': ((0.0, 21.0), (0.6, 34.0), (1.4, 26.0), (2.0, 18.0), (3.0, 14.0), (4.1, 18.0)),
            'head': ((0.0, 4.0), (0.6, 8.0), (1.4, 0.0), (2.0, -4.0), (3.0, -4.0), (4.1, -2.0)),
            'flicks': (4.25,),
            ('arm', 'left'): ((10.5, 2.3, zf - 1.5), (2.6, 2.9, (10.5, 2.3, zf - 12.0)), (3.25, 3.55, (10.8, 2.3, zf - 23.0)),
                              (3.9, 4.2, (10.8, 2.3, end - 21.0))),
            ('arm', 'right'): ((-10.5, 2.3, zf - 1.0), (2.75, 3.05, (-10.5, 2.3, zf - 11.0)), (3.4, 3.7, (-10.8, 2.3, zf - 22.0)),
                               (4.05, 4.35, (-10.8, 2.3, end - 21.0))),
            # (the hind legs unfold to hanging, then come up out of the water onto the lip as the hips slide over it)
            ('leg', 'left'): ((11.8, 2.9, zf - 1.5), (3.85, 4.15, (11.8, 2.9, end + 1.4))),
            ('leg', 'right'): ((-11.8, 2.9, zf - 2.0), (4.0, 4.3, (-11.8, 2.9, end + 1.4))),
            'fold': {('arm', 'left'): ((0.9, 1.0), (1.5, 0.0)), ('arm', 'right'): ((1.0, 1.0), (1.6, 0.0)),
                     ('leg', 'left'): ((1.8, 1.0), (2.5, 0.0)), ('leg', 'right'): ((1.9, 1.0), (2.6, 0.0))},
            'hang': {('arm', 'left'): ((1.5, 1.0), (2.0, 0.0)), ('arm', 'right'): ((1.6, 1.0), (2.1, 0.0)),
                     ('leg', 'left'): ((2.9, 1.0), (3.4, 0.0)), ('leg', 'right'): ((3.0, 1.0), (3.5, 0.0))},
            'raised': True,
            'swim': (0.0, 0.8, 'out'),
            'length': 4.6,
        }
    # 'outup': coming out onto a bank a block over the water's block, as it climbs up a block: it swims up to the face,
    # its head lifting, rears up in the water against it, the forefeet unfold and reach up and hook over the top; the
    # hind feet unfold onto the face under water, then step up the face, it hauls itself up near upright, the front half
    # bending over the top; the hips roll over the edge, the hind feet step onto the top, a step on, stand. 4.5 s.
    u = WATER_FACE_UP - WATER_FACE                  # (the face further off: the course moved on by as much)
    end = -4.0 - CLIMB_END - u
    zf = ze
    k = B - block_px(1.0)
    return {
        # (rearing as it comes up to the face, the head lifting clear of it; the hips coming in close under the face for
        # the hind feet to find it: further off, the snout ran into the face and the hind feet fell short of it)
        'hips_z': ((0.0, -4.0), (0.6, -12.0), (1.1, -17.0), (1.6, -30.0), (2.0, -40.0), (2.3, -46.0),
                   (2.6, -39.5 - u), (3.1, -42.5 - u), (3.5, -45.0 - u), (3.9, -51.0 - u), (4.3, end)),
        'hips_z_start': -glide,
        'hips_y': ((0.0, F + 10.5), (0.8, F + 11.0), (1.5, F + 12.5), (1.9, F + 15.4), (2.2, 6.0 + k), (2.45, 10.6 + k), (2.7, 13.5 + k),
                   (3.1, B - 2.5), (3.5, B - 1.0), (3.9, B + 7.0), (4.3, B + 10.5)),
        'rear': ((0.0, 0.0), (0.4, 10.0), (0.8, 30.0), (1.1, 46.0), (1.4, 56.0), (1.8, 62.0), (2.2, 66.0), (2.6, 68.0), (3.1, 70.0),
                 (3.5, 62.0), (3.9, 32.0), (4.3, 0.0)),
        'bend': ((1.5, 0.0), (2.1, 14.0), (2.6, 28.0), (3.1, 42.0), (3.5, 40.0), (3.9, 22.0), (4.3, 0.0)),
        'neck': ((0.0, 21.0), (0.5, 40.0 + k), (1.4, 32.0 + 1.5 * k), (2.1, 24.0 + k), (2.6, 20.0), (3.4, 16.0), (4.1, 18.0)),
        'head': ((0.0, 4.0), (0.5, 16.0 + k), (1.4, 8.0 + 2.0 * k), (2.1, 0.0 + k), (2.6, -4.0), (3.4, -6.0), (4.1, -2.0)),
        'flicks': (),
        ('arm', 'left'): ((9.5, B + 2.3, zf - 1.5), (3.35, 3.65, (10.3, B + 2.3, -64.0 - u)), (3.95, 4.25, (10.8, B + 2.3, end - 21.0))),
        ('arm', 'right'): ((-9.5, B + 2.3, zf - 1.5), (3.45, 3.75, (-10.3, B + 2.3, -66.0 - u)), (4.05, 4.35, (-10.8, B + 2.3, end - 21.0))),
        ('leg', 'left'): ((11.5, F + 12.5 + 0.8 * k, zf + 2.5, 85.0), (2.45, 2.75, (11.5, B - 11.5, zf + 6.0, 85.0)),
                          (3.5, 3.9, (11.8, B + 2.9, end + 1.4))),
        ('leg', 'right'): ((-11.5, F + 11.5 + 0.8 * k, zf + 2.5, 85.0), (2.35, 2.65, (-11.5, B - 11.5, zf + 6.0, 85.0)),
                           (3.35, 3.85, (-11.8, B + 2.9, end + 1.4))),
        # every leg unfolds to hanging (the way it folds going in, backwards), then the forefeet reach up and over onto
        # the top and the hind feet find the face
        'fold': {('arm', 'left'): ((0.9, 1.0), (1.5, 0.0)), ('arm', 'right'): ((1.0, 1.0), (1.6, 0.0)),
                 ('leg', 'left'): ((1.0, 1.0), (1.6, 0.0)), ('leg', 'right'): ((1.0, 1.0), (1.6, 0.0))},
        'hang': {('arm', 'left'): ((1.6, 1.0), (2.2, 0.0)), ('arm', 'right'): ((1.7, 1.0), (2.3, 0.0)),
                 ('leg', 'left'): ((1.9, 1.0), (2.35, 0.0)), ('leg', 'right'): ((1.8, 1.0), (2.25, 0.0))},
        'raised': True,
        'swim': (0.0, 0.8, 'out'),
        'length': 4.5,
    }


# --- walking into the water off a level bank (remade from scratch 2026-11-22, the user: "curve down into the water
# without his back cutting, without him going into swimming form, and as he goes into the water he gradually goes into
# swimming form once he's about fully in") -----------------------------------------------------------------------------
# The old walk-in bent the back at its three trunk joints by keys of its own while the hips stayed up on the bank: the
# back broke into a step at the waist, then the hips dropped in at the end. The new starting idea in one sentence: **the
# trunk is laid along one smooth curve, its middle line on the bank, over the lip and level afloat, piece by piece, so the
# bend runs back along the body as it walks over the edge (a snake over a ledge), never a kink at one joint.** It walks
# on without stopping, each foot stepping as the walk steps until its next step would land past the lip; then that foot
# lets go and hangs; the legs fold for swimming and the swim's wave eases in only once the hips are in. Every number my
# pick. The tail is the game's (dragged, TailDrag): it slides off the lip after the hips.
IN_EDGE = 50.0              # the bank's edge this far ahead of where it starts (px): level, a stride on the bank before the curve
# The course in three parts (my picks): level on the bank; easing down over IN_LEAD so the belly is just over the lip
# (the middle line IN_LIP over the bank, the belly 5 px under it) as the body crosses it; curving down over IN_DIVE to
# afloat. Its steepest about 21 degrees. (Curving down from standing height at the lip, the hips stayed up in the air
# after the hind legs let go, tipped nose down, and the dragged tail fell away behind them in a peak: the first film,
# 2026-11-22. With the belly on the lip, the lip carries the hips as they slide over it.)
IN_LEAD, IN_LIP, IN_DIVE = 22.0, 6.6, 36.0
STROLL = 0.031              # its calm walk (blocks a tick, MonitorEntity's stroll): it walks in at this, glides off at GLIDE
FOOT_ON = 4.5               # a foot's wrist or ankle lands at least this far back from the lip (its claws on the bank)
RELEASE = 3.0               # the last foot on the bank lets go this much further behind its girdle than a walking step (px)
# The walk's gait (the walk clip's own, shared with it): a lizard's trot, each foot planted this share of the stride,
# landing and leaving at these places along the body (px) and lifted this high; when each foot steps (share of a stride)
WALK_DUTY = 0.62
WALK_REACH = {'arm': (-27.0, -18.5, 2.5), 'leg': (-7.0, 1.5, 3.0)}
WALK_PHASES = {('arm', 'left'): 0.0, ('leg', 'right'): 0.05, ('arm', 'right'): 0.5, ('leg', 'left'): 0.55}


def _hermite(u, a, b, ma, mb):
    """The cubic from a to b over u = 0 to 1, leaving a at slope ma and reaching b at slope mb (per whole u)."""
    return (a * (2 * u ** 3 - 3 * u ** 2 + 1) + ma * (u ** 3 - 2 * u ** 2 + u) + b * (-2 * u ** 3 + 3 * u ** 2)
            + mb * (u ** 3 - u ** 2))


def _in_course(B):
    """The walk-in's course for the trunk's middle line (the bend pivot's height, BODY_PIVOT): level on the bank, curving
    down over the lip and level again afloat. Returns at(s): the point (z, y) at s px along it (forward), s = 0 where the
    pivot stands at the start."""
    sc = water_scene('in', B)
    top, low, ze = BODY_PIVOT[1], sc['float'] + BODY_PIVOT[1], sc['edge']
    # (each part a cubic easing from one slope to the next: level, the lip's slope, level; the lip's slope the harmonic
    # mean of the two parts' average slopes, so neither part overshoots)
    s1, s2 = (top - IN_LIP) / IN_LEAD, (IN_LIP - low) / IN_DIVE
    m = 2.0 * s1 * s2 / (s1 + s2)

    def height(z):
        d = ze + IN_LEAD - z                         # how far past the start of the lead (px, forward)
        if d <= 0.0:
            return top
        if d < IN_LEAD:
            return _hermite(d / IN_LEAD, top, IN_LIP, 0.0, -m * IN_LEAD)
        d -= IN_LEAD
        if d < IN_DIVE:
            return _hermite(d / IN_DIVE, IN_LIP, low, -m * IN_DIVE, 0.0)
        return low
    zs, ys, ss = [], [], []
    z = BODY_PIVOT[2] + 40.0
    while z > ze - IN_DIVE - 260.0:
        zs.append(z)
        ys.append(height(z))
        ss.append(0.0 if len(ss) == 0 else ss[-1] + math.hypot(z - zs[-2], ys[-1] - ys[-2]))
        z -= 0.25
    s0 = ss[min(range(len(zs)), key=lambda i: abs(zs[i] - BODY_PIVOT[2]))]
    ss = [s - s0 for s in ss]

    def at(s):
        if s <= ss[0]:
            return zs[0] - (ss[0] - s), ys[0]
        lo, hi = 0, len(ss) - 1
        if s >= ss[hi]:
            return zs[hi] - (s - ss[hi]), ys[hi]
        while hi - lo > 1:
            mid = (lo + hi) // 2
            lo, hi = (mid, hi) if ss[mid] <= s else (lo, mid)
        f = (s - ss[lo]) / (ss[hi] - ss[lo])
        return zs[lo] + (zs[hi] - zs[lo]) * f, ys[lo] + (ys[hi] - ys[lo]) * f
    return at


def _pitch(a, b):
    """The pitch (degrees, nose up) of the line from a back to b ahead, both (z, y) with forward as -z."""
    return math.degrees(math.atan2(b[1] - a[1], a[0] - b[0]))


def _trunk_on(at, sp):
    """The trunk laid along the course with its bend pivot sp px along it: each piece (hips, waist, ribs, chest) turned
    to the chord between its ends on the course, each joint turned on its outer edge as the game turns it (as _trunk).
    Returns (rear, the body's turn and move, each front piece's clip turn, the pieces' places as _trunk gives them)."""
    ends = (BODY_Z[1], BODY_PIVOT[2], TRUNK_JOINTS[0], TRUNK_JOINTS[1], BODY_Z[0])    # back to front
    pts = [at(sp + BODY_PIVOT[2] - z) for z in ends]
    pitches = [_pitch(a, b) for a, b in zip(pts, pts[1:])]
    # (the hips are one long piece: laid on its chord, the whole of the course's bend over its length fell on the waist,
    # an 18 degree crease; tipped half way to the course's own slope at the pivot, the rest goes to the dragged tail)
    # (tipped about its back end, which stays on the course: tipped about the pivot, the tail's root rose a pixel and a
    # half over the bank, a little peak at the hips with the tail sloping down from it)
    pitches[0] = 0.5 * pitches[0] + 0.5 * _pitch(at(sp - 1.5), at(sp + 1.5))
    rear = pitches[0]
    bm = euler([-rear, 0.0, 0.0])
    a = math.radians(rear)
    q = (pts[0][0] - (BODY_Z[1] - BODY_PIVOT[2]) * math.cos(a), pts[0][1] + (BODY_Z[1] - BODY_PIVOT[2]) * math.sin(a))
    bd = [0.0, q[1] - BODY_PIVOT[1], q[0] - BODY_PIVOT[2]]
    M, t = bm, vadd(vsub(BODY_PIVOT, mat_vec(bm, BODY_PIVOT)), bd)
    turns, places = {}, [(M, t)]
    for (name, z, _), before, now in zip(TRUNK_HINGES, pitches, pitches[1:]):
        b = before - now                             # (nose down, as the clip turns it)
        turns[name] = b
        c = [0.0, BODY_TOP if b > 0 else BODY_BOTTOM, z]
        R = euler([b, 0.0, 0.0])
        t = vadd(mat_vec(M, vsub(c, mat_vec(R, c))), t)
        M = mat_mul(M, R)
        places.append((M, t))
    return rear, bm, bd, turns, places, pitches[-1]


def _walk_in_keys(B, size):
    """The walk-in's course (keys as _water_keys gives them, its trunk as a function of time: see the section's top)."""
    sc = water_scene('in', B)
    ze = sc['edge']
    at = _in_course(B)
    v0, v1 = px_per_tick(STROLL, size), px_per_tick(GLIDE, size)      # (px a second)
    # when it is about fully in: the hips' back end (the tail's root) four fifths of the way down the curve
    s_in = (BODY_PIVOT[2] - ze) + 0.8 * IN_DIVE + (BODY_Z[1] - BODY_PIVOT[2])
    t_f = s_in / v0
    fold = 1.3                                       # the legs folding, and the swim easing in, over this long (s)
    length = round((t_f + fold + 0.15) / 0.05) * 0.05

    def travel(t):                                   # how far along the course the pivot has come (its speed eases
        u = min(1.0, t / length)                     # from the stroll to the glide)
        return v0 * t + (v1 - v0) * length * (u * u * u - 0.5 * u ** 4) if t <= length else travel(length) + v1 * (t - length)

    def trunk(t):
        return _trunk_on(at, travel(t))

    def girdle(t, kind):
        """The shoulder or hip joint's place along (z) at time t."""
        rear, bm, bd, turns, places, _ = trunk(t)
        S = LEG_PLANS[kind]['joints'][0]
        if kind == 'arm':
            M, tr = places[-1]
            return vadd(mat_vec(M, list(S)), tr)[2]
        return vadd(vadd(mat_vec(bm, vsub(list(S), BODY_PIVOT)), BODY_PIVOT), bd)[2]

    # the feet: the walk's own steps (its places and rhythm, at the stroll), each foot stepping until its next step would
    # land past the lip (its claws over the water): there it lifts and lets go, hanging
    keys = {}
    for kind in ('arm', 'leg'):
        front, back, _ = WALK_REACH[kind]
        S, _, W = LEG_PLANS[kind]['joints']
        T = (back - front) / (v0 * WALK_DUTY)
        for s, side in ((1, 'left'), (-1, 'right')):
            plan = [((s * W[0], W[1], W[2]),)]
            k, hang = 0, None
            first = ((WALK_PHASES[(kind, side)] + WALK_DUTY) % 1.0) * T
            while True:
                lift = first + k * T
                land = lift + (1.0 - WALK_DUTY) * T
                # (no further than the lip: a last short step up to it; a foot already there, or with less than a
                # small step left, lets go and hangs instead, its girdle by then over the lip or near it)
                z = max(girdle(land, kind) + (front - S[2]), ze + FOOT_ON)
                if z > plan[-1][-1][2] - 2.0 or land > t_f:
                    hang = lift
                    break
                plan.append((lift, land, (s * W[0], W[1], z)))
                k += 1
            # the last foot on the bank lets go once its girdle has gone on past it as far as a walking step leaves it
            # behind, and a little more (a push off the lip): not at the walk's next beat, which after a short last step
            # held a hind foot stretched straight past its reach until its knee flipped; nor sooner, when a forefoot's
            # toes went down past the lip's top corner with its shoulder still over the bank
            hang = plan[-1][1] if len(plan) > 1 else 0.0
            while plan[-1][-1][2] - girdle(hang, kind) < back - S[2] + RELEASE and hang < t_f:
                hang += 0.05
            keys[(kind, side)] = (plan[0][0],) + tuple(plan[1:])
            keys.setdefault('hang', {})[(kind, side)] = ((hang, 0.0), (hang + 0.55, 1.0))
            # folding for swimming once it is about fully in: the forelegs first, the left a touch ahead
            f0 = t_f - (0.35 if kind == 'arm' else 0.0) + (0.0 if side == 'left' else 0.1)
            keys.setdefault('fold', {})[(kind, side)] = ((f0, 0.0), (f0 + fold - 0.1, 1.0))

    def neck(t):
        """Where the neck points (degrees above level): its rest lift, dipping with the course ahead of the chest."""
        sp = travel(t)
        n = sp + BODY_PIVOT[2] - BODY_Z[0] + 0.5 * NECK_LEN
        return 18.0 + 0.8 * _pitch(at(n - 2.0), at(n + 2.0)) * _ease(min(1.0, t / 0.5))

    def head(t):
        """The head's own angle (degrees above level): a little nose down at rest, looking down the course ahead."""
        sp = travel(t)
        h = sp + BODY_PIVOT[2] - BODY_Z[0] + NECK_LEN + 0.5 * HEAD_LEN
        return -2.0 + 0.8 * _pitch(at(h - 3.0), at(h + 3.0)) * _ease(min(1.0, t / 0.5))

    # letting go: a forefoot reaches on forward over the lip and down into the water, feeling for the bottom (hanging
    # straight down it dangled down the bank's face like a stick); a hind foot trails back, kicking off the lip
    # (in, up, back from standing (px), the toes' droop; my picks)
    hang_at = {'arm': ((1.5, -0.5, -2.5), -40.0), 'leg': ((1.5, 1.5, 4.0), -55.0)}    # (6 px ahead was out of reach)
    return {**keys, 'trunk': trunk, 'hang_at': hang_at, 'neck': neck, 'head': head, 'flicks': (0.3,),
            'swim': (t_f - 0.35, length, 'in'), 'knees': KNEES, 'elbows': ELBOWS, 'length': length}


TOES_OUT = {'arm': 80.0, 'leg': 95.0}    # half way through a fold the foot lies flat, toes this far round out to the side


def _water_clearance(places, sc, margin=0.4):
    """How far the trunk must go up (px) for its underside to clear the bank (the water does not hold it up)."""
    need = 0.0
    for yb, z0, z1 in UNDERSIDE:
        for k in range(17):
            z = z0 + (z1 - z0) * k / 16
            piece = 0 if z > TRUNK_HINGES[0][1] else 1 if z > TRUNK_HINGES[1][1] else 2 if z > TRUNK_HINGES[2][1] else 3
            M, t = places[piece]
            w = vadd(mat_vec(M, [0.0, yb, z]), t)
            need = max(need, scene_floor(sc, w[2]) + margin - w[1])
    return need


KNEES = 0.5     # my pick: going in, the knees held up this much (as CLIMB_RAISED) until the legs fold
ELBOWS = 0.5    # my pick: and the elbows (reaching forward past the shoulder into the water, an elbow flipped)
HANG = {'arm': ((1.0, 1.5, 2.5), -55.0), 'leg': ((1.5, 2.5, 0.0), -65.0)}  # my picks: in, up, back (px); toes down


def _hang_leg(L, at=None):
    """Where a leg hanging loose in the water has its wrist or ankle, and its foot's turn (on the body's axes): a little
    in, up and back from standing, the toes drooping (or as a move's own 'hang_at' has it, by leg kind)."""
    (dx, dy, dz), toes = (at or HANG)[L['kind']]
    w = L['W']
    return [w[0] - L['s'] * dx, w[1] + dy, w[2] + dz], mat_mul(L['foot'], turn_x(toes))


def _wrap(d):
    return (d + 180.0) % 360.0 - 180.0


def _slerp_turn(a, b, k):
    """A turn part way (k, 0 to 1) from matrix a to matrix b along the shortest turn between them (blend_turn mixes the
    matrices, which breaks down near half a turn apart: a folding foot flipped over)."""
    def quat(m):
        # (from the biggest of w, x, y, z: the signs taken from the others' sums held at half a turn too, where w is 0;
        # signs read off w alone lost them and turned the right forefoot's swim pose into the left's, 2026-11-19)
        tr = m[0][0] + m[1][1] + m[2][2]
        if tr > max(m[0][0], m[1][1], m[2][2]):
            r = math.sqrt(1.0 + tr) * 2.0
            return [r / 4.0, (m[2][1] - m[1][2]) / r, (m[0][2] - m[2][0]) / r, (m[1][0] - m[0][1]) / r]
        if m[0][0] >= m[1][1] and m[0][0] >= m[2][2]:
            r = math.sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]) * 2.0
            return [(m[2][1] - m[1][2]) / r, r / 4.0, (m[0][1] + m[1][0]) / r, (m[0][2] + m[2][0]) / r]
        if m[1][1] >= m[2][2]:
            r = math.sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]) * 2.0
            return [(m[0][2] - m[2][0]) / r, (m[0][1] + m[1][0]) / r, r / 4.0, (m[1][2] + m[2][1]) / r]
        r = math.sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]) * 2.0
        return [(m[1][0] - m[0][1]) / r, (m[0][2] + m[2][0]) / r, (m[1][2] + m[2][1]) / r, r / 4.0]
    qa, qb = quat(a), quat(b)
    d = sum(p * q for p, q in zip(qa, qb))
    if d < 0:
        qb, d = [-q for q in qb], -d
    th = math.acos(min(1.0, d))
    q = qa if th < 1e-6 else [(math.sin((1 - k) * th) * p + math.sin(k * th) * r) / math.sin(th) for p, r in zip(qa, qb)]
    n = math.sqrt(sum(c * c for c in q))
    w, x, y, z = (c / n for c in q)
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]


# Going in, the drift (2026-11-22, the user: the legs changed to the swim's form at once, folded in the move's last second;
# "ease into the water, legs up until fully in, 2 seconds of drifting where they still move, then gradually into the form
# over 2 seconds"). From about fully in (the swim's own motion starting to ease in, keys['swim'][0]) the legs hang loose
# for DRIFT seconds, still swinging slowly, then fold for swimming over SETTLE_IN, each along the fold's own path (a foot
# moved on a route and the leg reached to it: turning each bone straight from hanging to folded, the feet nearly half a
# turn apart, swung the hind feet up past the back, the game-side try of the same day). The body coasts on, slowing
# (COAST, seconds: how quickly the glide dies away, my pick), so the clip's longer move goes about a block further.
DRIFT, SETTLE_IN, COAST = 2.0, 2.0, 1.0
# the loose swing of a drifting leg: one swing in SWAY_BEAT s, the diagonal pairs together, the wrist or ankle this far
# fore and aft and up (px; above where it hangs, twice SWAY_Y at the top), eased in over SWAY_IN s and out as the leg folds (my picks, fun first)
SWAY_BEAT, SWAY_IN, SWAY_Z, SWAY_Y = 2.2, 0.6, 1.8, 0.9
SWAY_PHASE = {('arm', 'left'): 0.0, ('leg', 'right'): 0.0, ('arm', 'right'): 0.5, ('leg', 'left'): 0.5}


def water_move(kind, size, swim_table, swim_leg, flick, log=None):
    """One water move's clip for a monitor of this size: (clip, worst reach miss, its scene, its path as (time, up, along)
    in px). log, a list, gets every reach miss as (time, leg, px)."""
    from model_parts import clip
    P = _Poser()
    P.log = log
    B = block_px(size)
    sc = water_scene(kind, B)
    keys = _water_keys(kind, B, size)
    length = keys['length']
    path, marks = [], {}
    t0s, t1s, way = keys['swim']
    course = length                 # going in, the move's own course ends here and the body coasts on (the drift)
    if way == 'in':
        f0 = t0s + DRIFT
        # (forelegs first, the left a touch ahead, as the move's own folds were; all within SETTLE_IN; timed evenly,
        # the fold's own path easing it once: eased here too, the whole fold bunched into under a second, the first film)
        keys = {**keys, 'fold': {('arm', 'left'): ((f0, 0.0), (f0 + SETTLE_IN - 0.2, 1.0)),
                                 ('arm', 'right'): ((f0 + 0.05, 0.0), (f0 + SETTLE_IN - 0.15, 1.0)),
                                 ('leg', 'left'): ((f0 + 0.15, 0.0), (f0 + SETTLE_IN - 0.05, 1.0)),
                                 ('leg', 'right'): ((f0 + 0.2, 0.0), (f0 + SETTLE_IN, 1.0))}}
        length = t1s = round((f0 + SETTLE_IN + 0.15) / 0.05) * 0.05
    beat = round(SWIM_LEN / 0.05)
    lifts = {'arm': 3.0, 'leg': 3.0}
    if way == 'out':        # (the turns go on from the swim's own values: started afresh they came out a turn apart)
        P.last.update({bone: list(chans['rotation']) for bone, chans in swim_table[0].items() if 'rotation' in chans})

    def body_at(t):
        """The trunk on the move's course at t: (rear, bm, bd, turns, places, chest_up)."""
        if 'trunk' in keys:              # (the walk-in: the trunk laid along its course)
            rear, bm, bd, turns, places, chest_up = keys['trunk'](t)
            lift = _water_clearance(places, sc)
            marks['lift'] = max(marks.get('lift', 0.0), lift)
            if lift > 0.0:
                bd = [bd[0], bd[1] + lift, bd[2]]
                places = [(M, vadd(tr, [0.0, lift, 0.0])) for M, tr in places]
        else:
            rear, bend = _spline(t, keys['rear']), _spline(t, keys['bend'])
            hy = _spline(t, keys['hips_y'])
            hz = _spline(t, keys['hips_z'], keys.get('hips_z_start', 0.0), keys.get('hips_z_end', 0.0))
            bm, bd, turns, places = _trunk(rear, bend, hy, hz)
            lift = _water_clearance(places, sc)
            if lift > 0.0:
                bm, bd, turns, places = _trunk(rear, bend, hy + lift, hz)
            chest_up = rear - bend
        return rear, bm, bd, turns, places, chest_up
    # the speed it glides at as the course ends (px a second, forward), carried on into the coast
    glide_end = (body_at(course - 0.05)[2][2] - body_at(course)[2][2]) / 0.05 if length > course else 0.0

    def pose(t):
        P.now = t
        p = {}
        tc = min(t, course)
        rear, bm, bd, turns, places, chest_up = body_at(tc)
        if t > course:                   # coasting on after the course, slowing (the drift)
            d = glide_end * COAST * (1.0 - math.exp(-(t - course) / COAST))
            bd = [bd[0], bd[1], bd[2] - d]
            places = [(M, vadd(tr, [0.0, 0.0, -d])) for M, tr in places]
        p['body'] = {'rotation': [round(-rear, 3), 0.0, 0.0], 'position': [0.0, 0.0, 0.0]}
        for name, b in turns.items():
            p[name] = {'rotation': [round(b, 3), 0.0, 0.0]}
        path.append((t, bd[1], bd[2]))
        cm, ct = places[-1]
        cd = vsub(vadd(ct, mat_vec(cm, BODY_PIVOT)), BODY_PIVOT)
        # the wet moment: going in, the chest's underside first under the surface (a splash); coming out, the hips'
        # underside first over it (from then the water runs off)
        if 'wet' not in marks:
            if way == 'in':
                y = vadd(mat_vec(cm, [0.0, BODY_Y[0], -24.0]), ct)[1]
                if y < sc['surface']:
                    marks['wet'] = t
            else:
                hm, ht = places[0]
                if t > 0.5 and vadd(mat_vec(hm, [0.0, BODY_Y[0], -6.0]), ht)[1] > sc['surface']:
                    marks['wet'] = t
        folded = {}
        for L in P.legs:
            side = (L['kind'], L['side'])
            wrist, up, pitch_ = _foot_at(tc, keys[side], lifts[L['kind']], reach=L['kind'] == 'arm' and keys.get('raised', False))
            if way == 'in':                  # (the drift's slow fold, timed evenly: see DRIFT)
                (a0, _), (a1, _) = keys['fold'][side]
                fold = min(1.0, max(0.0, (t - a0) / (a1 - a0)))
            else:
                fold = _ramp(t, keys['fold'][side])
            fm, fd = (cm, cd) if L['kind'] == 'arm' else (bm, bd)
            # (a foot turned flat against a face lifts its claws straight off it: curled or drooped as on the ground,
            # they went into the face)
            flat_ish = max(0.0, 1.0 - abs(pitch_) / 90.0)
            foot_m = mat_mul(L['foot'], turn_x(pitch_ - 28 * up * flat_ish))
            bend_way = None
            # reaching onto the bank the elbows and knees held as the climbs hold them; stepping to the lip the knees
            # held up a little (crouched with the foot lifted near the hip, the knee flipped from front to back); each
            # eased to the swim's own way as the leg folds (held so folded, the knee started out twisted against the
            # swim's and the foot unfolded a whole turn round)
            raise_by = CLIMB_RAISED[L['kind']] if keys.get('raised') else keys.get('knees' if L['kind'] == 'leg' else 'elbows', 0.0)
            if raise_by:
                held = [a - raise_by * b for a, b in zip(L['stand'], L['reach'])]
                own = vsub(L['E'], L['S'])
                bend_way = [a + (b - a) * fold for a, b in zip(held, own)]
            # the leg on its place (the ground, the bank's face, or reaching into the water); a leg folding for swimming
            # goes from there to its folded place as a paddle stroke: a forefoot sweeps back low first, then up into its
            # place (unfolding, up and forward first); a hind foot sweeps back and up together; the foot turned along
            # the shortest turn between the two
            hangs = side in keys.get('hang', {})
            hang = _ramp(t, keys['hang'][side]) if hangs else 0.0
            P.log = log if fold < 0.999 else None
            leg_p = {}
            if fold > 0.0 or hang > 0.0:
                wb = vadd(mat_vec(transpose(fm), vsub(vsub(wrist, fd), BODY_PIVOT)), BODY_PIVOT)
                fb = mat_mul(transpose(fm), foot_m)
                sw, sf = swim_leg(L)
                if hangs:
                    # a leg that has lost the bottom hangs loose under the body, its toes drooping, and only folds
                    # for swimming a while after (folded at once the feet swung up over the back, the user's ask)
                    hw, hf = _hang_leg(L, keys.get('hang_at'))
                    if way == 'in' and t > t0s:  # drifting: the loose leg swings slowly, less as it folds
                        a = _ease(min(1.0, (t - t0s) / SWAY_IN)) * (1.0 - _ease(fold))
                        u = 2.0 * math.pi * ((t - t0s) / SWAY_BEAT - SWAY_PHASE[side])
                        # (up and down only up from where it hangs: swinging down too, the biggest monitor's hind claws
                        # went 1.3 px into the bottom of the shallowest water it floats in)
                        hw = [hw[0], hw[1] + SWAY_Y * (1.0 + math.sin(u - 1.2)) * a, hw[2] + SWAY_Z * math.sin(u) * a]
                    # (off the lip forward and out first, then down: straight down to it the foot went through the
                    # bank's edge)
                    h, hz, hy = _ease(hang), _ease(min(1.0, hang / 0.6)), _ease(max(0.0, (hang - 0.25) / 0.75))
                    wb = [wb[0] + (hw[0] - wb[0]) * hz, wb[1] + (hw[1] - wb[1]) * hy, wb[2] + (hw[2] - wb[2]) * hz]
                    # (turned only once off the ground or the face: turned on it, the toes swung through it)
                    droop = _ease(min(1.0, max(0.0, (hang - 0.4) / 0.6)))
                    w = vadd(vadd(mat_vec(fm, vsub(wb, BODY_PIVOT)), BODY_PIVOT), fd)
                    if 'trunk' in keys:
                        # (the walk-in: the toes droop only once the wrist is out past the bank's face; drooped over
                        # the lip they went 2.3 px into its top corner, 2026-11-22)
                        droop *= _ease(min(1.0, max(0.0, (sc['edge'] - w[2] - 1.0) / 5.0)))
                        # (lifting off a little first, as it lets go: lifted 5 px a forefoot rose over its crouching
                        # shoulder, claws in the air, 2026-11-22)
                        h = max(droop, 0.4 * _ease(min(1.0, hang / 0.35)))
                    fb = _slerp_turn(fb, hf, droop)
                    # never under the ground it is still over (a foot letting go lifts off first): the wrist kept its
                    # standing height over the bank's top or the bottom, more as the toes droop
                    # (its toes too, ahead of it, when it is up near a ledge's top: low against a face it keeps off it)
                    under, ahead = scene_floor(sc, w[2]), scene_floor(sc, w[2] - 6.0)
                    if 'trunk' in keys:
                        # (the walk-in: the foot kept off the bank's top until its heel too is well out past the lip,
                        # the guard easing off over 10 px: a guard that let go at once dropped the foot in a frame,
                        # and over 6 px a forefoot sweeping forward crossed it in two frames and its arm snapped over)
                        k = _ease(min(1.0, max(0.0, (sc['edge'] - w[2] - 4.0) / 10.0)))
                        under = sc['top'] + (sc['bottom'] - sc['top']) * k
                    least = (ahead if ahead > under and w[1] > ahead - 8.0 else under) + 2.3 + 5.0 * h
                    if w[1] < least:
                        w[1] = least
                        wb = vadd(mat_vec(transpose(fm), vsub(vsub(w, fd), BODY_PIVOT)), BODY_PIVOT)
                early, late = _ease(min(1.0, fold / 0.6)), _ease(max(0.0, (fold - 0.35) / 0.65))
                if L['kind'] == 'leg':           # (lifted first, the knee stood up over the hips like a horn)
                    fa = fy = _ease(fold)
                else:
                    # (unfolding to hang it goes back along the very curve it folds by; unfolding straight onto a bank,
                    # up and forward first)
                    fa, fy = (early, late) if keys['swim'][2] == 'in' or hangs else (late, early)
                wb = [wb[0] + (sw[0] - wb[0]) * fa, wb[1] + (sw[1] - wb[1]) * fy, wb[2] + (sw[2] - wb[2]) * fa]
                # the foot swung round flat, its toes out to the side half way (straight from one turn to the other it
                # stood on its toes, the claws up over the back like spikes)
                if hangs:                        # (from hanging, toes down, the turn to toes back is a short one)
                    ft = _slerp_turn(fb, sf, fold)
                    curl = 18.0 * up * flat_ish * (1.0 - hang) + (12.0 + 8.0 * fold) * hang
                else:
                    flat = turn_y(-L['s'] * TOES_OUT[L['kind']])
                    ft = _slerp_turn(fb, flat, fold / 0.5) if fold < 0.5 else _slerp_turn(flat, sf, (fold - 0.5) / 0.5)
                    curl = 18 * up * (1.0 - fold) + 20.0 * fold
                P.leg(leg_p, L, fm, fd, wb, ft, in_body=True, curl=curl, way=bend_way)
            else:
                P.leg(leg_p, L, fm, fd, wrist, foot_m, curl=18 * up * flat_ish, way=bend_way)
            p.update(leg_p)
            folded[side] = fold
        neck_up = keys['neck'](tc) if callable(keys['neck']) else _ramp(tc, keys['neck'])
        head_up = keys['head'](tc) if callable(keys['head']) else _ramp(tc, keys['head'])
        x = chest_up + 18.0 - neck_up
        for (bone, share), ex in zip((('neck1', 0.55), ('neck2', 0.45), ('head', 0.0)), (0.0, 0.0, neck_up - 20.0 - head_up)):
            p[bone] = {'rotation': [round(share * x + ex, 3), 0.0, 0.0]}
        if keys['flicks']:
            flick(p, t, keys['flicks'])
        for s in ('left', 'right'):
            p[f'pupil_{s}'] = {'position': [0.0, 0.0, -0.25]}
        # the swim's own motion (its sway, its tail's wave, its pose) easing in at the end, or out at the start
        f = min(1.0, max(0.0, (t - t0s) / (t1s - t0s)))
        w = _ease(f) if way == 'in' else 1.0 - _ease(f)
        if w > 0.0:
            u = round((t - (length - SWIM_LEN) if way == 'in' else t) / 0.05) % beat
            sp = swim_table[u]
            for bone, chans in sp.items():
                # (a leg's own fold brings it to the swim's pose; mixed with the swim's angles part way, a foot turned
                # the long way round between the two)
                limb = bone.split('_')[0], bone.rsplit('_', 1)[-1]
                if limb in folded and folded[limb] < 1.0:
                    continue
                mine = p.setdefault(bone, {})
                for ch, v in chans.items():
                    a = mine.get(ch, [1.0, 1.0, 1.0] if ch == 'scale' else [0.0, 0.0, 0.0])
                    if w >= 1.0:
                        mine[ch] = list(v)
                    elif ch == 'rotation':
                        mine[ch] = [round(x + _wrap(y - x) * w, 3) for x, y in zip(a, v)]
                    else:
                        mine[ch] = [round(x + (y - x) * w, 3) for x, y in zip(a, v)]
        return p
    steps = round(length * 20)
    made = clip(length, pose, steps, loop='hold')
    path = path[-(steps + 1):]
    made['timeline'] = {'0': f'water {kind} {sc["edge"]:g} {sc["top"]:.3f} {sc["surface"]:.3f} {sc["bottom"]:.3f}',
                        '0.05': 'path body ' + ' '.join(f'{t:.4f} {dy:.3f} {dz:.3f}' for t, dy, dz in path),
                        '0.1': f'wet {marks.get("wet", -1.0):.2f}'}
    return made, P.worst, sc, path


def _trunk(rear, bend, hy, hz):
    """The climb's trunk: the hips reared rear (degrees, nose up) with the hip joint at height hy and place hz, the front
    half bent bend more nose down over its three joints, each turned on its outer edge as the game turns it. Returns the
    body's turn and move (as body() gives them), each front piece's clip turn, and where each trunk piece is carried in
    the world as (M, t), a point p going to M p + t: the hips (the body's), then waist, ribs and chest."""
    bm = euler([-rear, 0.0, 0.0])
    bd = vsub(vsub([0.0, hy, hz], BODY_PIVOT), mat_vec(bm, vsub(HIP_JOINT, BODY_PIVOT)))
    bd[0] = 0.0
    M, t = bm, vadd(vsub(BODY_PIVOT, mat_vec(bm, BODY_PIVOT)), bd)
    turns, places = {}, [(M, t)]
    for name, z, share in TRUNK_HINGES:
        b = bend * share
        turns[name] = b
        c = [0.0, BODY_TOP if b > 0 else BODY_BOTTOM, z]
        R = euler([b, 0.0, 0.0])
        t = vadd(mat_vec(M, vsub(c, mat_vec(R, c))), t)
        M = mat_mul(M, R)
        places.append((M, t))
    return bm, bd, turns, places


def _clearance(places, B, margin=0.4, down=False):
    """How far the trunk must go up (px) for its underside to clear what is under it: the step's top in front of the
    face, the ground behind it (each underside point carried by the trunk piece it belongs to); going down, the top it
    started on behind the edge and the lower ground a block down past it."""
    need = 0.0
    for yb, z0, z1 in UNDERSIDE:
        for k in range(17):
            z = z0 + (z1 - z0) * k / 16
            piece = 0 if z > TRUNK_HINGES[0][1] else 1 if z > TRUNK_HINGES[1][1] else 2 if z > TRUNK_HINGES[2][1] else 3
            M, t = places[piece]
            w = vadd(mat_vec(M, [0.0, yb, z]), t)
            if down:
                floor = 0.0 if w[2] > -DOWN_EDGE + 0.2 else -B
            else:
                floor = B if w[2] < -CLIMB_FACE - 0.2 else 0.0     # (a point against the face is beside it, not over it)
            need = max(need, floor + margin - w[1])
    return need


# --- the back's S-curve (the Animal Maker Engine step C, 2026-11-20; old step 3) --------------------------------------
# A walking lizard's shoulders and hips turn opposite ways with each step (the trunk bends into a C one way, then the
# other, with the girdles near its ends), and with the neck turning back and the tail's wave the whole body makes an S.
# The trunk's middle line is laid as a curve through the body's bend point (its pivot) and each trunk piece placed along
# it at its own length (motion_kit.follow_path: places along the curve, never angles), so the pieces turn by what the
# curve does; the legs reach their planted feet from the turned shoulders and hips. My picks: a 0 turns it off (the old
# plank walk, byte for byte); the whole-body swing is cut to TRUNK_SWAY so the girdles do the turning.
TRUNK_S = {'walk': 7.0, 'swim': 5.0}            # how far the shoulders turn one way and the hips the other (degrees)
TRUNK_SWAY = {'walk': 2.0, 'swim': 2.0}         # the whole body's swing left with it (degrees; was 6 and 5)
GIRDLE = 8.5                                     # the shoulder joint's distance in front of the bend point (px)


def _trunk_s(p, bm, bd, turn):
    """The trunk bent into a C turning the shoulders turn degrees (+: to its right) and the hips the other way, the
    body turned bm and moved bd (as body() gives them). Writes the trunk pieces' turns into the pose p and returns the
    chest's and the hips' places as a body turn and move each (for the legs' reaching), and the chest's and the hips'
    headings (degrees, the body's swing included is the caller's)."""
    from motion_kit import follow_path, curve
    a = -math.tan(math.radians(turn)) / (2 * GIRDLE)            # x = a (z - pivot)^2: a C through the bend point
    z0 = BODY_PIVOT[2]
    ahead = follow_path(curve(lambda u: [a * u * u, 0.0, z0 - u], 0.0, 20.0, 80),
                        [z0 - TRUNK_JOINTS[0], TRUNK_JOINTS[0] - TRUNK_JOINTS[1], TRUNK_JOINTS[1] - BODY_Z[0]])
    behind = follow_path(curve(lambda u: [a * u * u, 0.0, z0 + u], 0.0, 20.0, 80), [BODY_Z[1] - z0])
    heads = [math.degrees(math.atan2(-(b[0] - c[0]), -(b[2] - c[2]))) for c, b in zip(ahead, ahead[1:])]
    hips = math.degrees(math.atan2(behind[1][0] - behind[0][0], behind[1][2] - behind[0][2]))
    M, t = bm, vadd(vsub(BODY_PIVOT, mat_vec(bm, BODY_PIVOT)), bd)
    last = 0.0
    for (name, z), h in zip((('waist', z0), ('ribs', TRUNK_JOINTS[0]), ('chest', TRUNK_JOINTS[1])), heads):
        p[name] = {'rotation': [0.0, round(h - last, 3), 0.0]}
        R = euler([0.0, h - last, 0.0])
        c = [0.0, BODY_PIVOT[1], z]
        t = vadd(mat_vec(M, vsub(c, mat_vec(R, c))), t)
        M, last = mat_mul(M, R), h
    p['hips'] = {'rotation': [0.0, round(hips, 3), 0.0]}
    hm = mat_mul(bm, euler([0.0, hips, 0.0]))
    return ((M, vsub(vadd(t, mat_vec(M, BODY_PIVOT)), BODY_PIVOT)), (hm, bd), last, hips)


def clips():
    """(clips by name, how far a wanted foot place lay out of reach in each, in pixels)."""
    from model_parts import clip
    out, worst = {}, {}

    def body(p, ry=0.0, rx=0.0, dy=0.0, dz=0.0):
        p['body'] = {'rotation': [round(rx, 3), round(ry, 3), 0.0], 'position': [0.0, round(dy, 3), round(dz, 3)]}
        return euler([rx, ry, 0.0]), [0.0, dy, dz]

    def stand(P, p, bm, bd):
        for L in P.legs:
            P.leg(p, L, bm, bd, L['W'], L['foot'])

    def neck_turn(p, yaw, lift=0.0, extra=(0.0, 0.0, 0.0)):
        """The neck and head turned yaw (degrees, + to its right) between them, raised lift; extra pitches added."""
        for (bone, share), ex in zip((('neck1', 0.4), ('neck2', 0.3), ('head', 0.3)), extra):
            up = -0.5 * lift if bone != 'neck1' else 0.0
            p[bone] = {'rotation': [round(ex + up, 3), round(share * yaw, 3), 0.0]}

    # The walk: a lizard's trot, the diagonal legs nearly together (left fore with right hind, right fore with left
    # hind, the hind a touch late), each foot on the ground 62 percent of the stride. The body swings from side to
    # side with the stride, bringing each shoulder forward as its foot reaches; the neck turns against the swing so the
    # head stays nearly steady, and a wave runs down the tail.
    P = _Poser()
    phases, reach, duty = WALK_PHASES, WALK_REACH, WALK_DUTY     # (where each foot lands, leaves, how high it lifts)

    def walk(t):
        p = {}
        w = 2 * math.pi * t / WALK_LEN
        yaw = (TRUNK_SWAY['walk'] if TRUNK_S['walk'] else 6.0) * math.cos(w)
        bm, bd = body(p, ry=yaw, dy=0.25 * math.cos(2 * w))
        frames, chest, hips = {'arm': (bm, bd), 'leg': (bm, bd)}, yaw, yaw
        if TRUNK_S['walk']:
            fa, fl, ch, hh = _trunk_s(p, bm, bd, TRUNK_S['walk'] * math.cos(w))
            frames, chest, hips = {'arm': fa, 'leg': fl}, yaw + ch, yaw + hh
        for L in P.legs:
            front, back, lift = reach[L['kind']]
            u = (t / WALK_LEN - phases[(L['kind'], L['side'])]) % 1.0
            if u < duty:                                 # planted, the body passing over it
                z, up = front + (back - front) * u / duty, 0.0
            else:                                        # swinging forward, lifted, toes hanging
                f = _ease((u - duty) / (1 - duty))
                z, up = back + (front - back) * f, math.sin(math.pi * f)
            wrist = [L['W'][0], L['W'][1] + lift * up, z]
            fm, fd = frames[L['kind']]
            P.leg(p, L, fm, fd, wrist, mat_mul(L['foot'], turn_x(-28 * up)), curl=18 * up)
        neck_turn(p, 0.0)
        p['neck1']['rotation'][1] = round(-0.55 * chest, 3)
        p['neck2']['rotation'][1] = round(-0.3 * chest, 3)
        p['head']['rotation'][1] = round(-0.1 * chest, 3)
        for i in range(TAIL_SEGS):
            sway = (2.5 + 7.0 * i / (TAIL_SEGS - 1)) * math.sin(w - 0.45 * i - 0.6)
            if i == 0:
                sway -= 1.2 * hips
            p[f'tail{i + 1}'] = {'rotation': [0.0, round(sway, 3), 0.0]}
        return p
    out['walk'] = clip(WALK_LEN, walk, 48)
    worst['walk'] = P.worst

    # Standing: breathing, the head turning slowly to look one way and the other, the eyes glancing, two blinks,
    # three flicks of the tongue (tasting the air, as monitors do all the time), the tail tip stirring.
    P = _Poser()
    look = ((0, 0), (1.6, 0), (2.4, 16), (4.0, 16), (4.9, -14), (7.2, -14), (8.1, 0))
    lift = ((0, 0), (1.6, 0), (2.4, 6), (4.0, 6), (4.9, 2), (7.2, 2), (8.1, 0))
    glance = ((0, 0.1), (0.8, 0.1), (1.0, -0.25), (2.6, -0.25), (2.9, 0.3), (3.8, 0.3), (4.1, -0.2), (6.5, -0.2),
              (6.8, 0.45), (7.6, 0.45), (8.0, 0.1))
    flicks = (1.0, 5.4, 5.9)

    def flick(p, t, starts):
        f, st = 0.0, 0.0
        for s0 in starts:
            v = _pulse(t, s0, 0.1, 0.3, 0.12)
            if v > f:
                f, st = v, s0
        wave = math.sin(2 * math.pi * (t - st) / 0.15)
        p['tongue'] = {'rotation': [round(5 * f + 4 * f * wave, 3), round(6 * f * wave, 3), 0.0],
                       'position': [0.0, 0.0, round(-7.0 * f, 3)]}

    def idle(t):
        p = {}
        bm, bd = body(p, dy=0.18 * math.sin(2 * math.pi * t / 2.5))
        stand(P, p, bm, bd)
        neck_turn(p, _ramp(t, look), _ramp(t, lift))
        g = _ramp(t, glance)
        blink = max(_pulse(t, 3.0, 0.06, 0.04, 0.08), _pulse(t, 8.4, 0.06, 0.04, 0.08))
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, round(g, 3)]}
            p[f'lid_{side}'] = {'scale': [1.0, round(1 + 2.4 * blink, 3), 1.0]}
        flick(p, t, flicks)
        for i in range(8, TAIL_SEGS):
            p[f'tail{i + 1}'] = {'rotation': [0.0, round(2.0 * (i - 7) / 4 * math.sin(2 * math.pi * t / 5 - 0.5 * i), 3), 0.0]}
        return p
    out['idle'] = clip(10.0, idle, 200)
    worst['idle'] = P.worst

    # The tongue alone (the game plays it over the other clips): two quick flicks.
    def tongue(t):
        p = {}
        flick(p, t, (0.2, 0.75))
        return p
    out['tongue'] = clip(2.0, tongue, 80)
    worst['tongue'] = 0.0

    # Swimming: the legs folded back flat along the sides, the body and the straightened tail thrown into a wave from
    # side to side, growing toward the tip; the head held up at the surface, steady. The hind feet are held out wide
    # and turned away from the tail, so the tail swings between them without touching (the user's pick of three).
    P = _Poser()

    def swim_leg(L):
        """Where a leg's wrist or ankle lies folded for swimming, and its foot's turn (both on the body's axes)."""
        s = L['s']
        if L['kind'] == 'arm':
            return (s * 6.3, 9.6, -10.0), from_axes((0, s, 0), (s, 0, 0), (0, 0, -1))
        a = math.radians(22.0)                              # toes swung out, away from the tail
        c, sn = math.cos(a), math.sin(a)
        return (s * 9.0, 9.4, 7.5), from_axes((0, s, 0), (s * c, 0, -sn), (-s * sn, 0, -c))

    def swim(t):
        p = {}
        w = 2 * math.pi * t / SWIM_LEN
        yaw = (TRUNK_SWAY['swim'] if TRUNK_S['swim'] else 5.0) * math.cos(w)
        bm, bd = body(p, ry=yaw)
        chest, hips = yaw, yaw
        if TRUNK_S['swim']:                 # (the folded legs ride on their girdles: placed on the body's axes as before)
            _, _, ch, hh = _trunk_s(p, bm, bd, TRUNK_S['swim'] * math.cos(w - 0.4))
            chest, hips = yaw + ch, yaw + hh
        for L in P.legs:
            wrist, foot = swim_leg(L)
            P.leg(p, L, bm, bd, wrist, foot, in_body=True, curl=20.0)
        neck_turn(p, 0.0, extra=(0.0, -3.0, -3.0))
        p['neck1']['rotation'][1] = round(-0.55 * chest, 3)
        p['neck2']['rotation'][1] = round(-0.3 * chest, 3)
        p['head']['rotation'][1] = round(-0.15 * chest, 3)
        # (the tail's wave only: afloat, the game drags it level along the water's surface, TailDrag, 2026-11-19;
        # until then the clip straightened it, -rest, which the drag would have bent twice)
        for i in range(TAIL_SEGS):
            f = i / (TAIL_SEGS - 1)
            sway = (4.0 + 7.0 * f) * math.sin(w - 0.5 * i - 0.8)
            if i == 0:
                sway -= 1.4 * hips
            p[f'tail{i + 1}'] = {'rotation': [0.0, round(sway, 3), 0.0]}
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, -0.25]}
        return p
    out['swim'] = clip(SWIM_LEN, swim, 32)
    worst['swim'] = P.worst

    # The swim's poses every 0.05 s (a whole beat), for the water moves to blend into and out of (going in it ends in the
    # swim's first pose, coming out it starts from it; the old "enter" clip, 2026-11-18, went with the water moves,
    # 2026-11-19). Made with the swim's own poser right after its clip, so the values are the clip's own.
    swim_table = [swim(k * 0.05) for k in range(round(SWIM_LEN / 0.05) + 1)]
    # Each folded leg's angles written the way a fold from standing arrives at them (the same pose: a foot's roll of
    # -201 degrees is one of +159), so the game, easing angle by angle from a water move's last frame into the swim,
    # never spins a foot round; read from the walk-in's fold.
    probe = water_move('in', 1.0, swim_table, swim_leg, flick)[0]['bones']
    for bone, chans in out['swim']['bones'].items():
        if not (bone.startswith('arm_') or bone.startswith('leg_')) or 'rotation' not in chans or bone not in probe:
            continue
        keys = probe[bone]['rotation']
        came = keys[sorted(keys, key=float)[-2]]
        shift = [360.0 * round((c - s) / 360.0) for c, s in zip(came, swim_table[0][bone]['rotation'])]
        if any(shift):
            for k, v in chans['rotation'].items():
                chans['rotation'][k] = [round(a + b, 3) for a, b in zip(v, shift)]
            for row in swim_table:
                row[bone]['rotation'] = [round(a + b, 3) for a, b in zip(row[bone]['rotation'], shift)]

    # The threat: standing tall on straightened legs, the front raised, the neck stretched up and the head tipped down
    # at the threat, the mouth gaping and closing with the hiss, the tail arched up off the ground and lashing.
    P = _Poser()

    def hiss(t):
        p = {}
        gape = 0.5 - 0.5 * math.cos(math.pi * t)
        bm, bd = body(p, rx=-3.0, dy=1.0 + 0.25 * gape)
        stand(P, p, bm, bd)
        neck_turn(p, 0.0, extra=(-7.0 - 2.0 * gape, -3.0, 5.0))
        p['jaw'] = {'rotation': [round(14 + 16 * gape, 3), 0.0, 0.0]}
        # The tongue lifts off the jaw's floor as the mouth opens, arches up and quivers from side to side.
        quiver = math.sin(2 * math.pi * t / 0.3)
        p['tongue'] = {'rotation': [round(-9.0 * gape + 2.0 * gape * quiver, 3), round(7.0 * gape * quiver, 3), 0.0],
                       'position': [0.0, round(0.3 * gape, 3), round(-1.0 * gape, 3)]}
        for i in range(TAIL_SEGS):
            lash = (3.0 + 6.0 * i / (TAIL_SEGS - 1)) * math.sin(2 * math.pi * t / 0.5 - 0.55 * i)
            p[f'tail{i + 1}'] = {'rotation': [5.0 if i < 3 else 0.0, round(lash, 3), 0.0]}
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, -0.25]}
        return p
    out['hiss'] = clip(2.0, hiss, 60)
    worst['hiss'] = P.worst

    # --- the game's clips (into the game, 2026-11-17) ---------------------------------------------------------------
    # Lying flat to bask: the belly down on the sand, the legs splayed out and back, the head resting low, the tail
    # straightened so it lies along the ground; slow breathing, a blink, the eyes half watching.
    P = _Poser()
    LOW = -5.4

    def flat_legs(P, p, bm, bd, slack=0.0):
        for L in P.legs:
            x, y, z = L['W']
            if L['kind'] == 'arm':
                wrist = [x * 1.25, y, z + 2.0 + slack]
            else:
                wrist = [x * 1.2, y, z + 4.5 + slack]
            P.leg(p, L, bm, bd, wrist, L['foot'], curl=4.0)

    def flat_tail(p, P, k=0.55, sway=None):
        for i in range(TAIL_SEGS):
            yaw = 0.0 if sway is None else sway(i)
            p[f'tail{i + 1}'] = {'rotation': [round(-k * P.rest[f'tail{i + 1}'][0], 3), round(yaw, 3), 0.0]}

    def bask(t):
        p = {}
        bm, bd = body(p, dy=LOW + 0.15 * math.sin(2 * math.pi * t / 4.0))
        flat_legs(P, p, bm, bd)
        neck_turn(p, _ramp(t, ((0, 0), (2.0, 0), (3.0, 10), (5.0, 10), (6.0, 0))), 0.0, extra=(7.0, 4.0, 2.0))
        flat_tail(p, P, sway=lambda i: 1.5 * (i / TAIL_SEGS) * math.sin(2 * math.pi * t / 8.0 - 0.4 * i))
        blink = _pulse(t, 4.2, 0.08, 0.1, 0.12)
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, round(_ramp(t, ((0, 0.1), (2.0, 0.1), (3.0, -0.3), (6.0, -0.3), (7.0, 0.1))), 3)]}
            p[f'lid_{side}'] = {'scale': [1.0, round(1.6 + 1.8 * blink, 3), 1.0]}
        return p
    out['bask'] = clip(8.0, bask, 96)
    worst['bask'] = P.worst

    # The bite: a quick lunge, the body thrown forward over planted feet, the neck shooting out, the jaws open wide
    # and snapping shut on the prey (shut at 0.28 s, the moment the game counts the bite).
    P = _Poser()

    def bite(t):
        p = {}
        go = _ramp(t, ((0, 0), (0.12, -0.3), (0.26, 1.0), (0.42, 1.0), (0.7, 0.0)))
        bm, bd = body(p, rx=3.0 * go, dy=0.4 * go, dz=-3.0 * go)
        stand(P, p, bm, bd)
        neck_turn(p, 0.0, extra=(-10.0 * go + 6.0 * max(go, 0), -4.0 * go, 6.0 * go))
        p['jaw'] = {'rotation': [round(_ramp(t, ((0, 0), (0.1, 6), (0.2, 34), (0.28, 0), (0.7, 0))), 3), 0.0, 0.0]}
        for i in range(TAIL_SEGS):
            p[f'tail{i + 1}'] = {'rotation': [0.0, round(4.0 * go * math.sin(0.6 * i), 3), 0.0]}
        return p
    out['bite'] = clip(0.7, bite, 28, loop=False)
    worst['bite'] = P.worst

    # The bite at a player (2026-11-17, the user's ask; venom comes later): it draws back with the mouth opening, lunges
    # up and forward with the jaws thrown wide, clamps on (shut at 0.42 s, the moment the game counts the bite), shakes
    # its head hard from side to side three times with the jaws held shut, lets go and pulls back. Timings my picks.
    P = _Poser()

    def bite_player(t):
        p = {}
        go = _ramp(t, ((0, 0), (0.22, -0.45), (0.38, 1.0), (1.0, 0.85), (1.15, 0.6), (1.4, 0.0)))
        shake = 0.0
        if 0.42 < t < 1.0:
            shake = math.sin(2 * math.pi * (t - 0.42) / 0.19) * math.sin(math.pi * (t - 0.42) / 0.58)
        bm, bd = body(p, rx=-4.0 * go, ry=3.0 * shake, dy=1.1 * max(go, 0.0), dz=-3.5 * go)
        stand(P, p, bm, bd)
        neck_turn(p, 26.0 * shake, 0.0, extra=(-13.0 * go, -7.0 * go, -2.0 * go))
        p['jaw'] = {'rotation': [round(_ramp(t, ((0, 0), (0.22, 18), (0.33, 38), (0.42, 3), (1.0, 3), (1.1, 22),
                                                  (1.25, 10), (1.4, 0))), 3), 0.0, 0.0]}
        for i in range(TAIL_SEGS):
            lash = (2.0 + 5.0 * i / (TAIL_SEGS - 1)) * math.sin(2 * math.pi * t / 0.38 - 0.55 * i) * min(1.0, 2 * abs(go))
            p[f'tail{i + 1}'] = {'rotation': [0.0, round(lash, 3), 0.0]}
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, -0.25]}
        return p
    out['bite_player'] = clip(1.4, bite_player, 56, loop=False)
    worst['bite_player'] = P.worst

    # Eating: the head held up, the jaws working the food (four chomps), then two gulps with the head tossed up and
    # forward to throw it down the throat.
    P = _Poser()

    def eat(t):
        p = {}
        bm, bd = body(p, rx=-2.0, dy=0.3)
        stand(P, p, bm, bd)
        chomp = max(0.0, math.sin(2 * math.pi * t / 0.3)) if t < 1.2 else 0.0
        toss = max(_pulse(t, 1.25, 0.12, 0.05, 0.18), _pulse(t, 1.65, 0.12, 0.05, 0.18))
        neck_turn(p, 4.0 * math.sin(2 * math.pi * t / 0.6) * (t < 1.2), 0.0,
                  extra=(-6.0 - 8.0 * toss, -4.0 - 6.0 * toss, -2.0 - 10.0 * toss))
        p['jaw'] = {'rotation': [round(10 * chomp + 18 * toss, 3), 0.0, 0.0]}
        for i in range(8, TAIL_SEGS):
            p[f'tail{i + 1}'] = {'rotation': [0.0, round(2.0 * math.sin(2 * math.pi * t / 2 - 0.5 * i), 3), 0.0]}
        return p
    out['eat'] = clip(2.0, eat, 60, loop=False)
    worst['eat'] = P.worst

    # Death: it sinks flat onto its belly, the legs slack and splayed, the head down on the sand with the mouth a
    # little open, the tail limp; held.
    P = _Poser()

    def death(t):
        p = {}
        f = _ease(min(1.0, t / 1.0))
        bm, bd = body(p, rx=1.5 * f, dy=LOW * f - 0.2 * f)
        if f < 1.0:
            for L in P.legs:
                x, y, z = L['W']
                tw = [x * 1.25, y, z + 2.0] if L['kind'] == 'arm' else [x * 1.2, y, z + 4.5]
                P.leg(p, L, bm, bd, [a + (b - a) * f for a, b in zip(L['W'], tw)], L['foot'], curl=10.0 * f)
        else:
            flat_legs(P, p, bm, bd)
        neck_turn(p, 12.0 * f, 0.0, extra=(9.0 * f, 6.0 * f, 3.0 * f))
        p['jaw'] = {'rotation': [round(8.0 * f, 3), 0.0, 0.0]}
        flat_tail(p, P, k=0.55 * f, sway=lambda i: 6.0 * f * math.sin(0.35 * i))
        for side in ('left', 'right'):
            p[f'lid_{side}'] = {'scale': [1.0, round(1 + 2.4 * f, 3), 1.0]}
        return p
    out['death'] = clip(1.2, death, 36, loop='hold')
    worst['death'] = P.worst

    # The climb up and the climb down a block, one clip for each size (see _climb_keys, _descend_keys): the path it
    # moves along is in its notes.
    def climb(size, down=False):
        P = _Poser()
        B = block_px(size)
        keys = _descend_keys(B) if down else _climb_keys(B)
        path = []
        lifts = {'arm': 3.0, 'leg': 3.0}

        def pose(t):
            p = {}
            rear, bend = _spline(t, keys['rear']), _spline(t, keys['bend'])
            hy, hz = _spline(t, keys['hips_y']), _spline(t, keys['hips_z'])
            bm, bd, turns, places = _trunk(rear, bend, hy, hz)
            lift = _clearance(places, B, down=down)
            if lift > 0.0:
                bm, bd, turns, places = _trunk(rear, bend, hy + lift, hz)
            p['body'] = {'rotation': [round(-rear, 3), 0.0, 0.0], 'position': [0.0, 0.0, 0.0]}
            for name, b in turns.items():
                p[name] = {'rotation': [round(b, 3), 0.0, 0.0]}
            path.append((t, bd[1], bd[2]))
            # the forelegs hang from the chest: its place in the world, as a body turn and move for the leg reaching
            cm, ct = places[-1]
            cd = vsub(vadd(ct, mat_vec(cm, BODY_PIVOT)), BODY_PIVOT)
            for L in P.legs:
                wrist, up, pitch = _foot_at(t, keys[(L['kind'], L['side'])], lifts[L['kind']], reach=L['kind'] == 'arm')
                fm, fd = (cm, cd) if L['kind'] == 'arm' else (bm, bd)
                P.leg(p, L, fm, fd, wrist, mat_mul(L['foot'], turn_x(pitch - 28 * up)), curl=18 * up,
                      raised=CLIMB_RAISED[L['kind']])
            # the neck and head point where wanted whatever the chest does (rest: the neck 15 and 3 degrees up from the
            # chest, the head 20 down from the neck): the neck's turn shared between its two pieces
            chest_up = rear - bend
            neck_up, head_up = _ramp(t, keys['neck']), _ramp(t, keys['head'])
            x = chest_up + 18.0 - neck_up
            neck_turn(p, 0.0, extra=(0.55 * x, 0.45 * x, neck_up - 20.0 - head_up))
            flick(p, t, keys['flicks'])
            for side in ('left', 'right'):
                p[f'pupil_{side}'] = {'position': [0.0, 0.0, -0.25]}
            return p
        length = keys['length']
        steps = round(length * 20)
        made = clip(length, pose, steps, loop='hold')
        path = path[-(steps + 1):]
        made['timeline'] = {'0': f'step {-DOWN_EDGE:g} {-B:.3f}' if down else f'step {-CLIMB_FACE:g} {B:.3f}',
                            '0.05': 'path body ' + ' '.join(f'{t:.4f} {dy:.3f} {dz:.3f}' for t, dy, dz in path)}
        return made, P.worst

    # (each remade only when this maker, the kit or model_parts changed: motion_kit.cached, 2026-11-20)
    for down in (False, True):
        for size in CLIMB_SIZES:
            name = climb_name(size, down)
            out[name], worst[name] = cached('monitor', name, fingerprint(name, files=[__file__]),
                                            lambda: list(climb(size, down)))
    # Going into and out of the water (see _water_keys), one clip for each size.
    for kind in WATER_KINDS:
        for size in CLIMB_SIZES:
            name = water_name(kind, size)
            out[name], worst[name] = cached('monitor', name, fingerprint(name, swim_table, files=[__file__]),
                                            lambda: list(water_move(kind, size, swim_table, swim_leg, flick)[:2]))
    return out, worst


# The monitor's moves in the order the game names them (MonitorEntity), for the shared move file (model_parts.block_moves).
def move_specs():
    specs = [{'name': 'up', 'clip': lambda s: climb_name(s, False), 'ahead_px': CLIMB_FACE,
              'starts_afloat': False, 'ends_afloat': False, 'goes_down': False, 'end_level': 1},
             {'name': 'down', 'clip': lambda s: climb_name(s, True), 'ahead_px': DOWN_EDGE,
              'starts_afloat': False, 'ends_afloat': False, 'goes_down': True, 'end_level': -1}]
    for kind in WATER_KINDS:
        out = kind in ('out', 'outup')
        specs.append({'name': 'out_up' if kind == 'outup' else kind, 'clip': lambda s, k=kind: water_name(k, s),
                      'ahead_px': WATER_FACE_UP if kind == 'outup' else WATER_FACE if kind == 'out' else IN_EDGE if kind == 'in' else WATER_EDGE,
                      'starts_afloat': out, 'ends_afloat': not out, 'goes_down': not out,
                      'end_level': 1 if kind == 'outup' else 0})
    return specs


def write_game():
    """Writes the monitor's model, texture and clips into the savanna mod, and its moves' paths into its move file
    (assets/antcolonies/block_moves/monitor.json, read by the game's BlockMoves)."""
    import mod_paths
    game_bones, rows, w, h = paint(model(), GAME_LOOK)
    made = clips()[0]
    write_assets({'textures/entity/monitor.png': png(rows),
                  'geo/entity/monitor.geo.json': geo('monitor', game_bones, w, h, (8, 2.5, [0, 0.75, 0])),
                  'animations/entity/monitor.animation.json': animations(made)})
    write_block_moves('monitor', block_moves(made, CLIMB_SIZES, block_px, move_specs()), 'tools/monitor_models.py game')


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['game']:
        write_game()
        sys.exit()
    bs = model()
    print(len(bs), 'bones,', sum(len(b.get('cubes', [])) for b in bs), 'boxes')
    for b in bs:
        print(f"  {b['name']:16} {len(b.get('cubes', [])):3} boxes  rot {b.get('rotation', '')}")
