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
                         cube, rounded, paint_faces, geo, animations, write_assets)  # png for the viewer

TEXELS = 1.0          # texture pixels a model pixel (plain colours for now)

# --- the outline's numbers (references/outlines/monitor.json, the approved outline; python tools/workshop.py outline
# monitor draws it), turned into the model's axes: z = HIPS - outline x, the hips where the tail meets the body --------
_OUTLINE = json.loads((Path(__file__).resolve().parent.parent / "references/outlines/monitor.json").read_text(encoding="utf-8"))
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
ENTER_LEN = 1.2                        # going into the water (seconds; MonitorEntity.ENTER_TICKS keeps step with it)


def _ease(x):
    return smooth(x)


def _pulse(t, start, rise, hold, fall):
    """0 before start, easing up to 1 over rise, held, easing back to 0 over fall."""
    if t < start:
        return 0.0
    if t < start + rise:
        return _ease((t - start) / rise)
    if t < start + rise + hold:
        return 1.0
    return 1.0 - _ease((t - start - rise - hold) / fall)


def _ramp(t, keys):
    """A value easing from key to key ((time, value), ...); held before the first and after the last."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            return v0 + (v1 - v0) * _ease((t - t0) / (t1 - t0))
    return keys[-1][1]


def _bend_way(pole, dh):
    """Which way a joint between two pieces bends (a unit direction square to dh, the line from the leg's root to its
    end): pole, less its part along dh."""
    k = sum(x * y for x, y in zip(pole, dh))
    return unit([x - k * y for x, y in zip(pole, dh)])


class _Poser:
    """Turns wanted bone turns (matrices) into clip values, and bends the legs to reach wanted foot places."""

    def __init__(self):
        bones = model()
        self.rest = {b['name']: b.get('rotation', [0.0, 0.0, 0.0]) for b in bones}
        self.last = {}
        self.worst = 0.0
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

    def leg(self, p, L, body_m, body_d, wrist, foot_m, in_body=False, curl=0.0, raised=None):
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
        d = vsub(Wb, S)
        D = vlen(d)
        reach = 0.985 * (L['l1'] + L['l2'])
        if D > reach:                                   # out of reach: as far as it goes (and remember by how much)
            self.worst = max(self.worst, D - reach)
            d = [c * reach / D for c in d]
            Wb, D = vadd(S, d), reach
        D = max(D, abs(L['l1'] - L['l2']) * 1.02)
        dh = [c / D for c in d]
        a = (L['l1'] ** 2 - L['l2'] ** 2 + D * D) / (2 * D)
        h = math.sqrt(max(0.0, L['l1'] ** 2 - a * a))
        way = vsub(L['E'], S) if raised is None else [a - raised * b for a, b in zip(L['stand'], L['reach'])]
        pole = _bend_way(way, dh)
        E = [S[i] + a * dh[i] + h * pole[i] for i in range(3)]
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
# nearest). The root's travel is not in the clip but in its path ('path' note, and MonitorClimb.java made from it): the
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


def _spline(t, keys):
    """A value passing smoothly through its keys ((time, value), ...) with no overshoot (a monotone cubic); held
    before the first and after the last."""
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    n = len(keys)
    d = [(keys[i + 1][1] - keys[i][1]) / (keys[i + 1][0] - keys[i][0]) for i in range(n - 1)]
    m = [0.0] * n                                    # the first and last keys start and stop at rest
    for i in range(1, n - 1):
        m[i] = 0.0 if d[i - 1] * d[i] <= 0 else 2.0 / (1.0 / d[i - 1] + 1.0 / d[i])
    for i in range(n - 1):
        (t0, v0), (t1, v1) = keys[i], keys[i + 1]
        if t <= t1:
            h = t1 - t0
            u = (t - t0) / h
            return (v0 * (2 * u ** 3 - 3 * u ** 2 + 1) + m[i] * h * (u ** 3 - 2 * u ** 2 + u)
                    + v1 * (-2 * u ** 3 + 3 * u ** 2) + m[i + 1] * h * (u ** 3 - u ** 2))
    return keys[-1][1]


def _foot_at(t, plan, lift, reach=False):
    """Where a foot's wrist or ankle is at time t on its plan (its first place, then its steps: lift time, land time,
    place), how far it is lifted (0 to 1) and how far the foot is turned toes up (degrees, a place's fourth value). A
    hind foot stepping up a face lifts first and reaches forward over it after; a forefoot reaching up (reach) goes
    forward and up together in one arc, the rise a little ahead, so it stays in front of its shoulder (rising first on
    the spot while the body went on, it ended up above and behind the shoulder, the elbow thrown up over the back)."""
    at = list(plan[0][:3])
    pitch = plan[0][3] if len(plan[0]) > 3 else 0.0
    for t0, t1, to in plan[1:]:
        goes = to[3] if len(to) > 3 else 0.0
        if t <= t0:
            return at, 0.0, pitch
        if t < t1:
            f = (t - t0) / (t1 - t0)
            rise = to[1] - at[1]
            if rise < -2.0:                          # going down an edge: out over it first, then down (never into it)
                fy, fz = _ease(max(0.0, (f - 0.3) / 0.7)), _ease(min(1.0, f / 0.5))
            elif rise <= 2.0:
                fy = fz = _ease(f)
            elif reach:
                fy, fz = _ease(min(1.0, f / 0.65)), _ease(f)
            else:
                fy, fz = _ease(min(1.0, f / 0.6)), _ease(max(0.0, (f - 0.35) / 0.65))
            arc = math.sin(math.pi * f)
            # toes turning up onto a face follow the reach forward (never into the face); toes coming off a face
            # flatten in the last of the rise, as the foot comes over the edge (not a hand waving above it)
            # going down, the toes turn down onto a face as the foot starts down it, and flatten as it nears the ground
            turn = ((_ease(max(0.0, (fy - 0.6) / 0.4)) if goes < pitch else fz) if rise > 2.0
                    else (_ease(min(1.0, 1.5 * fy)) if goes < pitch else _ease(min(1.0, max(0.0, (fy - 0.2) / 0.6)))) if rise < -2.0
                    else _ease(f))
            return ([at[0] + (to[0] - at[0]) * _ease(f), at[1] + rise * fy + lift * arc, at[2] + (to[2] - at[2]) * fz],
                    arc, pitch + (goes - pitch) * turn)
        at, pitch = list(to[:3]), goes
    return at, 0.0, pitch


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
    phases = {('arm', 'left'): 0.0, ('leg', 'right'): 0.05, ('arm', 'right'): 0.5, ('leg', 'left'): 0.55}
    reach = {'arm': (-27.0, -18.5, 2.5), 'leg': (-7.0, 1.5, 3.0)}      # where the foot lands, leaves, how high it lifts
    duty = 0.62

    def walk(t):
        p = {}
        w = 2 * math.pi * t / WALK_LEN
        yaw = 6.0 * math.cos(w)
        bm, bd = body(p, ry=yaw, dy=0.25 * math.cos(2 * w))
        for L in P.legs:
            front, back, lift = reach[L['kind']]
            u = (t / WALK_LEN - phases[(L['kind'], L['side'])]) % 1.0
            if u < duty:                                 # planted, the body passing over it
                z, up = front + (back - front) * u / duty, 0.0
            else:                                        # swinging forward, lifted, toes hanging
                f = _ease((u - duty) / (1 - duty))
                z, up = back + (front - back) * f, math.sin(math.pi * f)
            wrist = [L['W'][0], L['W'][1] + lift * up, z]
            P.leg(p, L, bm, bd, wrist, mat_mul(L['foot'], turn_x(-28 * up)), curl=18 * up)
        neck_turn(p, 0.0)
        p['neck1']['rotation'][1] = round(-0.55 * yaw, 3)
        p['neck2']['rotation'][1] = round(-0.3 * yaw, 3)
        p['head']['rotation'][1] = round(-0.1 * yaw, 3)
        for i in range(TAIL_SEGS):
            sway = (2.5 + 7.0 * i / (TAIL_SEGS - 1)) * math.sin(w - 0.45 * i - 0.6)
            if i == 0:
                sway -= 1.2 * yaw
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
        yaw = 5.0 * math.cos(w)
        bm, bd = body(p, ry=yaw)
        for L in P.legs:
            wrist, foot = swim_leg(L)
            P.leg(p, L, bm, bd, wrist, foot, in_body=True, curl=20.0)
        neck_turn(p, 0.0, extra=(0.0, -3.0, -3.0))
        p['neck1']['rotation'][1] = round(-0.55 * yaw, 3)
        p['neck2']['rotation'][1] = round(-0.3 * yaw, 3)
        p['head']['rotation'][1] = round(-0.15 * yaw, 3)
        for i in range(TAIL_SEGS):
            f = i / (TAIL_SEGS - 1)
            sway = (4.0 + 7.0 * f) * math.sin(w - 0.5 * i - 0.8)
            if i == 0:
                sway -= 1.4 * yaw
            p[f'tail{i + 1}'] = {'rotation': [round(-P.rest[f'tail{i + 1}'][0], 3), round(sway, 3), 0.0]}
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, -0.25]}
        return p
    out['swim'] = clip(SWIM_LEN, swim, 32)
    worst['swim'] = P.worst

    # Going in (2026-11-18, the user's ask: "an actual animation of the monitor pushing down into the water before
    # taking swimming form"): at the water's edge it leans out, the front going down and the neck dipping to the water,
    # the forelegs reaching forward and down over it; the hind legs, still on the bank, straighten and shove it forward
    # and down; the forelegs sweep back along its sides first, then the hind legs as they leave the bank, the tail
    # straightening behind; it ends in the swim clip's first pose, so the swim goes on from it. The game plays it once
    # as the forelegs lose the bank over water while the hind legs still stand (MonitorModel).
    swim0 = swim(0.0)
    P = _Poser()

    def enter(t):
        p = {}
        lean = _ramp(t, ((0.0, 0.0), (0.4, 1.0)))
        push = _ramp(t, ((0.25, 0.0), (0.7, 1.0)))
        glide = _ramp(t, ((0.6, 0.0), (ENTER_LEN, 1.0)))
        fore = _ramp(t, ((0.4, 0.0), (0.85, 1.0)))        # the forelegs folding
        hind = _ramp(t, ((0.7, 0.0), (1.1, 1.0)))         # the hind legs folding, as they leave the bank
        down = max(lean, push) * (1 - glide)
        yaw = 5.0 * glide
        bm, bd = body(p, ry=yaw, rx=9.0 * down, dy=-1.4 * down, dz=-2.5 * push * (1 - glide))
        for L in P.legs:
            x, y, z = L['W']
            if L['kind'] == 'arm':                         # reaching forward and down over the water, toes hanging
                wrist, foot, fold = [x * 1.1, y - 2.5 * lean, z - 3.5 * lean], mat_mul(L['foot'], turn_x(-24 * lean)), fore
            else:                                          # planted on the bank, shoving back
                wrist, foot, fold = [x, y, z + 3.0 * push], L['foot'], hind
            # the ground place on the body's axes (as leg() works it out), then part way to the folded place
            wb = vadd(mat_vec(transpose(bm), vsub(vsub(wrist, bd), BODY_PIVOT)), BODY_PIVOT)
            fb = mat_mul(transpose(bm), foot)
            sw, sf = swim_leg(L)
            wb = [a + (b - a) * fold for a, b in zip(wb, sw)]
            P.leg(p, L, bm, bd, wb, blend_turn(fb, sf, fold), in_body=True, curl=20.0 * fold + 10.0 * lean * (1 - fold))
        dip = 7.0 * down
        neck_turn(p, 0.0, extra=(dip - 0.0 * glide, dip * 0.9 - 3.0 * glide, dip * 0.4 - 3.0 * glide))
        p['neck1']['rotation'][1] = round(-0.55 * yaw, 3)
        p['neck2']['rotation'][1] = round(-0.3 * yaw, 3)
        p['head']['rotation'][1] = round(-0.15 * yaw, 3)
        for i in range(TAIL_SEGS):
            name = f'tail{i + 1}'
            end = swim0[name]['rotation']
            swish = 3.0 * (i / (TAIL_SEGS - 1)) * math.sin(2 * math.pi * t / 0.6 - 0.5 * i) * push * (1 - glide)
            lift = (4.0 if i < 3 else 0.0) * push * (1 - glide)        # the tail base raised as the hind legs shove
            p[name] = {'rotation': [round(end[0] * glide + lift, 3), round(end[1] * glide + swish, 3), 0.0]}
        for side in ('left', 'right'):
            p[f'pupil_{side}'] = {'position': [0.0, 0.0, round(-0.25 * glide, 3)]}
        return p
    out['enter'] = clip(ENTER_LEN, enter, 36, loop='hold')
    worst['enter'] = P.worst

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

    for down in (False, True):
        for size in CLIMB_SIZES:
            name = climb_name(size, down)
            out[name], worst[name] = climb(size, down)
    return out, worst


def climb_java(made):
    """MonitorClimb.java: the climb clips' paths for the game (the server moves the box along them, the drawing the
    model), the climb up's and the climb down's."""
    def move(down):
        rises, alongs, blocks, ticks = [], [], [], None
        for size in CLIMB_SIZES:
            name = climb_name(size, down)
            B = block_px(size)
            note = next(v for v in made[name]['timeline'].values() if v.startswith('path '))
            v = list(map(float, note.split()[2:]))
            rows = [v[i:i + 3] for i in range(0, len(v) - 2, 3)]
            ticks = len(rows) - 1
            blocks.append(B)
            rises.append([dy / B for _, dy, _ in rows])         # how far up (down below 0), as a share of a block
            alongs.append([-dz for _, _, dz in rows])           # how far forward, model pixels
        return rises, alongs, blocks, ticks, made[climb_name(CLIMB_SIZES[0], down)]['animation_length']
    up, dn = move(False), move(True)

    def table(rows):
        return ',\n'.join('            {' + ', '.join(f'{round(x, 4) + 0.0:.4f}F' for x in r) + '}' for r in rows)
    return f'''package com.antcolonies.savanna.animal;

/**
 * The Nile monitor's climbs up and down a block (the Animal Maker Engine step 2, stages B and C): the paths its clips
 * {{@code climb_up_*}} and {{@code climb_down_*}} move it along, one for each of {{@link #SIZES}}. Made by
 * {{@code python tools/monitor_models.py game}} with the clips: never edit by hand.
 */
public final class MonitorClimb {{
    private MonitorClimb() {{
    }}

    /** Climbing up, the step's face this far ahead of where the climb starts (model pixels). */
    public static final double FACE_PX = {CLIMB_FACE:g};
    /** Climbing down, the edge this far ahead of where the climb starts (model pixels). */
    public static final double EDGE_PX = {DOWN_EDGE:g};
    /** The sizes a clip is made for, and a block's height in model pixels at each. */
    public static final double[] SIZES = {{{', '.join(f'{x:g}' for x in CLIMB_SIZES)}}};
    public static final double[] BLOCK_PX = {{{', '.join(f'{x:.4f}' for x in up[2])}}};
    /** The clips' length (ticks; up {up[4]:g} s, down {dn[4]:g} s). */
    public static final int TICKS_UP = {up[3]}, TICKS_DOWN = {dn[3]};
    /** Up, for each size, tick by tick: how far up the body has come (a share of a block). */
    private static final float[][] RISE_UP = {{
{table(up[0])}}};
    /** Up, for each size, tick by tick: how far forward it has come (model pixels). */
    private static final float[][] ALONG_UP = {{
{table(up[1])}}};
    /** Down, for each size, tick by tick: how far up the body has come (a share of a block, below 0 going down). */
    private static final float[][] RISE_DOWN = {{
{table(dn[0])}}};
    /** Down, for each size, tick by tick: how far forward it has come (model pixels). */
    private static final float[][] ALONG_DOWN = {{
{table(dn[1])}}};

    /** The clip made for the size nearest this one. */
    public static int nearest(double size) {{
        int best = 0;
        for (int i = 1; i < SIZES.length; i++) {{
            if (Math.abs(SIZES[i] - size) < Math.abs(SIZES[best] - size)) {{
                best = i;
            }}
        }}
        return best;
    }}

    public static String clip(boolean down, int size) {{
        return (down ? "climb_down_" : "climb_up_") + Math.round(SIZES[size] * 1000);
    }}

    /** How far ahead of where the climb starts the step's face (up) or the edge (down) is (model pixels). */
    public static double ahead(boolean down) {{
        return down ? EDGE_PX : FACE_PX;
    }}

    /** The clip's length (ticks). */
    public static int ticks(boolean down) {{
        return down ? TICKS_DOWN : TICKS_UP;
    }}

    /** How far up at this many ticks into the clip (a share of a block, below 0 going down; held before and after). */
    public static double rise(boolean down, int size, double ticks) {{
        return at((down ? RISE_DOWN : RISE_UP)[size], ticks);
    }}

    /** How far forward at this many ticks into the clip (model pixels; held before and after). */
    public static double along(boolean down, int size, double ticks) {{
        return at((down ? ALONG_DOWN : ALONG_UP)[size], ticks);
    }}

    private static double at(float[] row, double ticks) {{
        int last = row.length - 1;
        if (ticks <= 0) {{
            return row[0];
        }}
        if (ticks >= last) {{
            return row[last];
        }}
        int i = (int) ticks;
        double f = ticks - i;
        return row[i] + (row[i + 1] - row[i]) * f;
    }}
}}
'''


def write_game():
    """Writes the monitor's model, texture and clips into the savanna mod, and the climbs' paths into its code."""
    import mod_paths
    game_bones, rows, w, h = paint(model(), GAME_LOOK)
    made = clips()[0]
    write_assets({'textures/entity/monitor.png': png(rows),
                  'geo/entity/monitor.geo.json': geo('monitor', game_bones, w, h, (8, 2.5, [0, 0.75, 0])),
                  'animations/entity/monitor.animation.json': animations(made)})
    java = mod_paths.mod_folder('savanna') / 'src/main/java/com/antcolonies/savanna/animal/MonitorClimb.java'
    with open(java, 'w', encoding='utf-8', newline='\n') as f:
        f.write(climb_java(made))
    print('wrote', java.relative_to(mod_paths.PROJECT))


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['game']:
        write_game()
        sys.exit()
    bs = model()
    print(len(bs), 'bones,', sum(len(b.get('cubes', [])) for b in bs), 'boxes')
    for b in bs:
        print(f"  {b['name']:16} {len(b.get('cubes', [])):3} boxes  rot {b.get('rotation', '')}")
