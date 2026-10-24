"""The Matabele ant's clips (M8 step 1, 2026-10-23): the walks, the turns, standing, the attack, dying and carrying
termites, for the major and the minor, made from the model the user approved (tools/matabele_models.py) with the
shared motion library (tools/ant_motion.py) and its gait maker, the way the termites' walks were made
(docs/plans/termite-walks.md). The clips are written next to the model in the savanna mod (drafts in drafts/savanna/
until the user put the ant into the game, 2026-10-23), where the game, the animation lab and the pose viewer read them.

Run from the project folder:
  python tools/matabele_anims.py                 writes the draft models (matabele_models.py), then every clip
  python tools/anim_lab.py check matabele_major  the lab's checks (and matabele_minor); `all` for the pictures
  python tools/ant_pose_viewer.py matabele       the pose viewer page with every clip playing, for the user

The real animal (Megaponera analis; Erik Frank's studies in Ivory Coast, docs/DESIGN.md "Matabele ants and the first
fights") and what each clip takes from it:
  walks     Ants walk an alternating tripod (L1 R2 L3, then R1 L2 R3), faster mostly by longer strides and a smaller
            share of each step on the ground, looser when slow (Wahl et al. 2015); the ant's three walks, the same
            gait maker numbers (lag 0.58 slow, 0.53), each picked by speed in body lengths a second as the other
            species'. The Matabele's long legs reach far: its strides are longer for its body than the harvester's.
            Raid columns march fast and tight, the antennae low, sweeping the scent trail on the ground ahead.
            Majors and minors march together at one speed, so a minor plays the fast walk where a major walks.
  idle      Standing: the antennae feeling about, the head turning a little, the jaws working once, the gaster
            slowly breathing.
  attack    Majors bite (they break the termites' mud and crush), minors sting (the sting paralyses); either can do
            both. A ponerine stings by curling its gaster forward under its body, the sting reaching ahead between
            its legs to the prey in its jaws. Two ways to pick from (see ATTACKS).
  death     The legs kick, give way and curl in under the body (a dead insect's legs fold as its muscles contract),
            the antennae and the gaster curl, and it tips onto its side. The clip does the whole fall itself, so in
            the game the renderer's own tip-over must be switched off for this ant (when it goes in).
  carry     Majors carry a stack of up to about 10 paralysed termites crosswise in their jaws, the head held high so
            the stack clears the ground; minors 1 or 2, the head lower (Frank). Played over the walk by the head's
            own player, like the harvester's carry; the termites themselves are a load the game draws at the jaws.

Conventions are the motion library's: geo units, the head to -Z, left is +X; a positive X turn tips a forward-pointing
part (head, antenna, forebody) down and a backward-pointing one (the gaster) up; a positive Y turn swings a left
antenna in toward the middle; the left jaw opens with a negative Y turn. Clips are made in role names (the body plan's)
and renamed to the model's bone names as they are written. Every number is my pick, to confirm, unless it says real.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ant_motion as mo  # noqa: E402
import anim_lab as lab  # noqa: E402
import body_plan  # noqa: E402
import matabele_climb  # noqa: E402
import matabele_models as mm  # noqa: E402

MODELS = tuple(mm.MODELS)
_RIGS = {}


def rig(name):
    """The model ready for the motion library (read from its draft geo file, built once)."""
    if name not in _RIGS:
        _RIGS[name] = mo.Rig(lab.Model(name))
    return _RIGS[name]


IO = 'easeInOutSine'


def keys(frames, channel='rotation', easing=IO):
    """Key frames (time, [x, y, z]) eased between, as GeckoLib plays them."""
    return {channel: {f'{t:g}': {'vector': [round(v, 2) for v in vec], 'easing': easing} for t, vec in frames}}


def still(**bones):
    """A still pose: bone -> turn [x, y, z] (or 'body_pos' -> the body's offset)."""
    out = {}
    for name, vec in bones.items():
        if name == 'body_pos':
            out.setdefault('body', {}).update(keys([(0, vec)], 'position'))
        else:
            out[name] = keys([(0, vec)])
    return out


# --- the antennae following the trail ---------------------------------------------------------------------------
# A raiding Matabele follows the scent line its scout laid, the antennae held low and forward, sweeping from side to
# side just over the ground (trail-following ants sweep the trail with their antennae, the two out of step). The whip
# is bent TRAIL_WHIP down, the tip at its lowest TRAIL_REST over the ground, the sweep TRAIL_SWEEP each way about a
# base turned TRAIL_OUT outward.
TRAIL_WHIP, TRAIL_REST, TRAIL_SWEEP, TRAIL_OUT = 14.0, 0.25, 9.0, 2.0


def lowest(rig_, side, pose):
    """The lowest point of a side's antenna in a pose (a still pose as mo.pose_of takes)."""
    model, p = rig_.model, mo.pose_of(pose)
    chain = model.plan.antennae[side]
    return min(model.world(bn, q, p)[1] for bn, corners, _ in model.cubes if bn in chain for q in corners)


def ground_angle(rig_, side, base_y, whip, rest, extra=None):
    """The antenna's base turn down (X, degrees) that puts its lowest point `rest` over the ground, the base swung
    base_y (Y) and the whip bent `whip` (X); extra: other bones of the pose (the head raised, ...)."""
    a1, a2 = rig_.model.plan.antennae[side]
    lo, hi = -60.0, 90.0
    for _ in range(40):
        mid = (lo + hi) / 2
        pose = dict(extra or {}, **{a1: [mid, base_y, 0], a2: [whip, 0, 0]})
        if lowest(rig_, side, pose) > rig_.ground + rest:
            lo = mid
        else:
            hi = mid
    return lo


def trail_antennae(track, rig_, seconds, raise_by=0.0, extra=None, n=40):
    """The walking antennae: low over the trail, sweeping side to side out of step (see TRAIL_WHIP). raise_by: the
    whole antenna that much higher (degrees). The sweep's outermost point is where the tip comes lowest."""
    for side, ph, sy in (('l', 0.0, 1), ('r', 0.5, -1)):
        a1, a2 = rig_.model.plan.antennae[side]
        down = ground_angle(rig_, side, -sy * (TRAIL_OUT + TRAIL_SWEEP), TRAIL_WHIP, TRAIL_REST, extra)
        down = min(down, ground_angle(rig_, side, -sy * (TRAIL_OUT - TRAIL_SWEEP), TRAIL_WHIP, TRAIL_REST, extra))
        for i in range(n + 1):
            t = seconds * i / n
            sweep = mo.waves(t, seconds, [(1, TRAIL_SWEEP, ph)])
            bob = mo.waves(t, seconds, [(2, 1.5, ph + 0.1)])
            track.put(a1, t, [down - raise_by - 1.5 + bob, -sy * (TRAIL_OUT + sweep), 0])
            track.put(a2, t, [TRAIL_WHIP + mo.waves(t, seconds, [(1, 4, ph - 0.12)]), 0, 0])


# --- the walks ------------------------------------------------------------------------------------------------------
# Three walks as the other species' (WALK_BANDS in body lengths a second, the same bands): the Matabele's long legs
# reach a sweep of about 7 units with the feet planted (0.4 body lengths a step, the harvester's model 0.48 at most),
# so slow 4.4, walking 5.6, fast 6.6 units (0.27, 0.33, 0.40 body lengths a stride with the antennae counted, longer
# than the harvester's for the body, as long-legged runners take). Duty factors the ant's (0.68 / 0.62 / 0.55, the middle
# legs longer in the slower two). A foot lifts a third of its hip's height (the long legs swing high enough with that);
# the forebody sways 1.5 degrees and the gaster 2.5 against it (a slender runner, steadier than the harvester's 2 and 3),
# the body bobs 0.05 units. Key frames 80 / 60 / 40 a cycle.
WALK_SECONDS = 0.5
WALK_BANDS = {'walk_slow': (0.0, 1.6), 'walk': (1.6, 3.7), 'walk_fast': (3.7, 999.0)}
GAITS = {'walk_slow': dict(sweep=4.4, duty=0.68, middle_duty=0.72, phases=mo.LOOSE_TRIPOD, keys=80),
         'walk': dict(sweep=5.6, duty=0.62, middle_duty=0.67, keys=60),
         'walk_fast': dict(sweep=6.6, duty=0.55, keys=40)}
LIFT_SHARE, BOB, SWAY, GASTER_SWAY = 0.33, 0.05, 1.5, 2.5
# Turning on the spot (the ant's way, 40 degrees a cycle): the long front legs step round 1.0 unit wider and stay down
# a little less (so they clear the jaws), the others 0.4 wider; the forebody leads 2 degrees into the turn.
TURN_DEGREES = 40.0
TURN = dict(duty={1: 0.45, 2: 0.6, 3: 0.6}, wide={1: 1.0, 2: 0.4, 3: 0.4}, lead=2.0, keys=80)


def walk_clips(name):
    """The walks and the turns of one model, with their notes (stride, swap, speeds; turn, swap, reach)."""
    r = rig(name)
    lift = LIFT_SHARE * r.legs['l2'].H[1]
    body = r.model.length
    clips = {}
    for gear, g in GAITS.items():
        tr, stride, report = mo.walk(r, WALK_SECONDS, lift=lift, bob=BOB, sway=SWAY, gaster_sway=GASTER_SWAY, **g)
        if report['worst_miss'] > 0.05:
            raise ValueError(f'{name} {gear}: a foot misses its path by {report["worst_miss"]:.3f}')
        trail_antennae(tr, r, WALK_SECONDS)
        band = WALK_BANDS[gear]
        clips[gear] = {'loop': True, 'animation_length': WALK_SECONDS,
                       'timeline': {'0': f'stride {stride:.4f}', '0.001': f'swap {report["swap"]:.4f}',
                                    '0.002': f'speeds {band[0] * body:.2f} {band[1] * body:.2f}'},
                       'bones': tr.bones()}
    for clip, degrees in (('turn_r', TURN_DEGREES), ('turn_l', -TURN_DEGREES)):
        tr, report = mo.turn(r, WALK_SECONDS, degrees, lift=lift, **TURN)
        if report['worst_miss'] > 0.05:
            raise ValueError(f'{name} {clip}: a foot misses its path by {report["worst_miss"]:.3f}')
        trail_antennae(tr, r, WALK_SECONDS)
        clips[clip] = {'loop': True, 'animation_length': WALK_SECONDS,
                       'timeline': {'0': f'turn {degrees:g}', '0.001': f'swap {report["swap"]:.4f}',
                                    '0.002': f'reach {report["reach"]:.2f}'},
                       'bones': tr.bones()}
    return clips


# --- clips built pose by pose ---------------------------------------------------------------------------------------
# The standing clips (attack, death) are written as smooth curves of the body's pose over time; at each moment the legs
# are solved so the feet stand where they should (planted, or lifted and folded in dying), and the whole body is
# raised just enough that nothing but the legs goes under the floor (a gaster curled under lifts the ant onto its toes,
# as a stinging ant rises).
FRAME = 0.02            # seconds between solved poses


def curve(frames, t):
    """A vector eased between key frames [(time, [x, y, z]), ...] (the first and the last held outside them)."""
    if t <= frames[0][0]:
        return list(frames[0][1])
    for (t0, a), (t1, b) in zip(frames, frames[1:]):
        if t <= t1:
            s = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
            s = 0.5 - 0.5 * math.cos(math.pi * s)
            return [x + (y - x) * s for x, y in zip(a, b)]
    return list(frames[-1][1])


def lowest_body(rig_, still_):
    """The lowest point of the body without its legs and antennae in a still pose."""
    m = rig_.model
    pose = mo.pose_of(still_)
    xf = m.transforms(pose)
    return min(p[1] for b, corners, _ in m.posed(pose, xf)
               if m.leg_of(b) is None and not m.is_antenna(b) for p in corners)


def posed_clip(name, seconds, body_at, feet_at=None, antennae_at=None, gap=0.2, loop=False, tol=0.005):
    """A clip solved pose by pose: body_at(t) -> a still pose of the body (body_pos and role -> turn, no legs);
    feet_at(t) -> {leg: where its foot is} (default: planted where it stands); antennae_at(t, pose) -> the antennae's
    turns for the finished body pose. The body is lifted so nothing but legs and antennae comes nearer the floor than
    gap. Returns the clip and the worst foot miss (units)."""
    r = rig(name)
    tr = mo.Track(r)
    n = max(2, round(seconds / FRAME))
    guess, worst = None, 0.0
    for i in range(n + 1):
        t = seconds * i / n
        body = body_at(t)
        need = r.ground + gap - lowest_body(r, body)
        if need > 0:
            x, y, z = body.get('body_pos', [0, 0, 0])
            body['body_pos'] = [x, y + need, z]
        turns, guess, miss = mo.stand(r, body, feet_at(t) if feet_at else None, guess)
        worst = max(worst, miss)
        pose = dict(body, **turns)
        if antennae_at:
            pose.update(antennae_at(t, pose))
        tr.pose(t, pose)
    return {'loop': loop, 'animation_length': seconds, 'bones': tr.bones(tol)}, worst


def jaws(open_by):
    """The jaws turned open_by degrees each from their rest (open 16 at rest; -16 shuts them, tip to tip)."""
    return {'mandible_l': [0, -open_by, 0], 'mandible_r': [0, open_by, 0]}


def gaster(curl):
    """The gaster curled curl degrees under the body (shared petiole 0.4, the gaster's joint 0.35, its pinch 0.25 as
    the body plan's waist; a backward-pointing part curls under with a negative X turn)."""
    return {'petiole': [-0.4 * curl, 0, 0], 'gaster_front': [-0.35 * curl, 0, 0], 'gaster_back': [-0.25 * curl, 0, 0]}


# --- standing ---------------------------------------------------------------------------------------------------------
# Standing about (the harvester's idle, a Matabele's slower: a big ponerine stands still more): the antennae feel about,
# each on its own rhythm, the head turns a little to each side, the jaws work once, the gaster breathes.
IDLE_SECONDS = 4.0


def idle():
    T = IDLE_SECONDS
    return {
        'antenna_l_1': keys([(0, [0, 0, 0]), (0.8, [10, 12, 0]), (1.7, [-5, 3, 0]), (2.5, [7, -5, 0]), (3.3, [-3, 7, 0]), (T, [0, 0, 0])]),
        'antenna_r_1': keys([(0, [0, 0, 0]), (0.6, [-5, -5, 0]), (1.4, [9, -12, 0]), (2.5, [-3, 2, 0]), (3.1, [7, -7, 0]), (T, [0, 0, 0])]),
        'antenna_l_2': keys([(0, [0, 0, 0]), (1.0, [8, 0, 0]), (1.3, [-4, 0, 0]), (2.7, [5, 0, 0]), (T, [0, 0, 0])]),
        'antenna_r_2': keys([(0, [0, 0, 0]), (0.7, [-4, 0, 0]), (1.8, [8, 0, 0]), (2.1, [0, 0, 0]), (3.5, [5, 0, 0]), (T, [0, 0, 0])]),
        'head': keys([(0, [0, 0, 0]), (1.2, [2, 7, 0]), (2.0, [2, 7, 0]), (3.0, [0, -5, 0]), (T, [0, 0, 0])]),
        'gaster_front': keys([(0, [0, 0, 0]), (T / 2, [-3, 0, 0]), (T, [0, 0, 0])]),
        'mandible_l': keys([(0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.65, [0, 10, 0]), (2.95, [0, 0, 0]), (T, [0, 0, 0])]),
        'mandible_r': keys([(0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.65, [0, -10, 0]), (2.95, [0, 0, 0]), (T, [0, 0, 0])]),
    }


# --- the attack, two ways to pick from ---------------------------------------------------------------------------
# A Matabele grabs a termite in its jaws and stings it: the sting paralyses (real; Frank's raids bring the termites home
# alive and paralysed). Both ways draw back, lunge with the jaws flung wide and snap them shut on the prey, then sting.
# The model's gaster is long for its legs (as the real one's: about a third of the body), so a ponerine stinging under
# itself tips its rear up, tilting at the waist, and rises on its legs (the clip lifts it just enough that nothing but
# the feet touches the floor):
#   attack_a  stand tall and sting forward: the head pulls the prey down and back, the rear tips up 35 degrees and the
#             gaster curls under as far as it goes (the sting under the hind legs' hips), two jabs, then back.
#   attack_b  a quick bite and stab down: the rear tips up less (25 degrees), the gaster stabs down behind the hind legs
#             twice, the ant staying low.
ATTACKS = ('attack_a', 'attack_b')


def tipped(pitch):
    """The rear tipped up by pitch degrees (the whole body turned nose down, the forebody turned back level)."""
    return {'body': [pitch, 0, 0], 'forebody': [-pitch, 0, 0]}


def attack(name, T, pos, fb, hd, jw, pitch, cu):
    """An attack from its curves: body offset, forebody, head (turns), jaws (open by), rear tip, gaster curl."""
    def body(t):
        pose = dict(tipped(curve(pitch, t)[0]), body_pos=curve(pos, t), head=curve(hd, t), **jaws(curve(jw, t)[0]),
                    **gaster(curve(cu, t)[0]))
        pose['forebody'] = [pose['forebody'][0] + curve(fb, t)[0], 0, 0]
        return pose
    return posed_clip(name, T, body)


def attack_a(name):
    T = 1.2
    return attack(name, T,
                  pos=[(0, [0, 0, 0]), (0.15, [0, 0, 0.7]), (0.25, [0, 0, -1.2]), (0.45, [0, 0, -0.4]), (0.95, [0, 0, -0.4]), (T, [0, 0, 0])],
                  fb=[(0, [0]), (0.15, [-8]), (0.25, [2]), (0.45, [4]), (0.95, [4]), (T, [0])],
                  hd=[(0, [0, 0, 0]), (0.15, [-8, 0, 0]), (0.25, [4, 0, 0]), (0.45, [14, 0, 0]), (0.95, [14, 0, 0]), (T, [0, 0, 0])],
                  jw=[(0, [0]), (0.15, [26]), (0.23, [26]), (0.27, [-14]), (1.0, [-14]), (T, [0])],
                  pitch=[(0, [0]), (0.3, [0]), (0.5, [35]), (0.95, [35]), (T, [0])],
                  cu=[(0, [0]), (0.3, [0]), (0.52, [72]), (0.6, [84]), (0.68, [72]), (0.76, [84]), (0.86, [72]), (T, [0])])


def attack_b(name):
    T = 0.85
    return attack(name, T,
                  pos=[(0, [0, 0, 0]), (0.1, [0, 0, 0.5]), (0.18, [0, 0, -1.0]), (0.65, [0, 0, -0.6]), (T, [0, 0, 0])],
                  fb=[(0, [0]), (0.1, [-7]), (0.18, [2]), (0.65, [2]), (T, [0])],
                  hd=[(0, [0, 0, 0]), (0.1, [-6, 0, 0]), (0.18, [6, 0, 0]), (0.65, [6, 0, 0]), (T, [0, 0, 0])],
                  jw=[(0, [0]), (0.1, [26]), (0.16, [26]), (0.2, [-14]), (0.68, [-14]), (T, [0])],
                  pitch=[(0, [0]), (0.2, [0]), (0.32, [25]), (0.65, [25]), (T, [0])],
                  cu=[(0, [0]), (0.22, [0]), (0.34, [40]), (0.4, [52]), (0.46, [40]), (0.52, [52]), (0.6, [40]), (T, [0])])


# --- dying --------------------------------------------------------------------------------------------------------
# A dying insect's legs kick, then give way and fold in as its muscles go (dead insects lie with the legs curled in),
# the jaws gape, the gaster jerks under once (the sting reflex), then the body settles on the ground: the gaster lies
# flat and the head tips back onto its jaws (the long jaws reach the ground below the body), the antennae and the head droop to
# the ground. Played once (the game holds the last frame). The game's renderer tips every dying mob onto its side on
# top of the clip, so the clip itself does not; the pose viewer shows the tip as the game does.
DEATH_SECONDS = 1.4


def death(name):
    r = rig(name)
    T = DEATH_SECONDS
    hip = r.legs['l2'].H[1]
    pos = [(0, [0, 0, 0]), (0.35, [0, 0.25, 0]), (0.9, [0, -hip, 0]), (T, [0, -hip, 0])]
    fb = [(0, [0, 0, 0]), (0.15, [-6, 0, 0]), (0.35, [-2, 0, 0]), (0.9, [-6, 0, 0]), (T, [-7, 0, 0])]
    hd = [(0, [0, 0, 0]), (0.15, [-6, 0, 0]), (0.35, [0, 3, 0]), (0.9, [-14, 5, 0]), (T, [-15, 6, -4])]
    jw = [(0, [0]), (0.12, [20]), (0.4, [20]), (0.9, [4]), (T, [2])]
    cu = [(0, [0]), (0.2, [30]), (0.45, [10]), (0.9, [-18]), (T, [-20])]

    def body(t):
        return dict(body_pos=curve(pos, t), forebody=curve(fb, t), head=curve(hd, t), **jaws(curve(jw, t)[0]),
                    **gaster(curve(cu, t)[0]))

    # The feet: two quick kicks by tripods (lifted and thrown forward or back), then drawn in toward the body and up
    # as the legs fold (the front legs only half in, still reaching forward); one hind leg twitches late.
    fold_in, fold_up = 0.9, 0.15 * hip

    def feet(t):
        out = {}
        for k in r.legs:
            x, y, z = r.stance(k)
            tri = k in ('l1', 'r2', 'l3')
            kick = [(0, [0]), (0.08 if tri else 0.16, [0]), (0.15 if tri else 0.23, [1]), (0.24 if tri else 0.32, [0])]
            lift = curve(kick, t)[0]
            fold = curve([(0, [0]), (0.35, [0]), (1.0, [1])], t)[0]
            if k == 'r3':
                fold -= 0.35 * curve([(0, [0]), (1.1, [0]), (1.2, [1]), (1.32, [0])], t)[0]
            front = k[1] == '1'                 # the front legs stay reaching forward, the others fold in
            fx = x * (1 - (1 - fold_in) * fold * (0.5 if front else 1))
            fz = z * (1 + (0.08 if front else -0.15) * fold) + (0.8 if int(k[1]) < 3 else -0.8) * lift
            out[k] = (fx, r.ground + 0.6 * hip * lift + fold_up * fold, fz)
        return out

    def antennae(t, pose):
        down = curve([(0, [0]), (0.4, [0]), (1.1, [1])], t)[0]
        out = {}
        for side in ('l', 'r'):
            a1, a2 = r.model.plan.antennae[side]
            base = ground_angle(r, side, 0, 20, 0.1, pose)
            out[a1] = [base * down + curve([(0, [0]), (0.1, [-14]), (0.3, [4]), (0.4, [0])], t)[0], 0, 0]
            out[a2] = [20 * down, 0, 0]
        return out

    return posed_clip(name, T, body, feet, antennae)


# --- carrying termites --------------------------------------------------------------------------------------------
# Played on the head over a walk (the body plan's head player), like the harvester's carry: a major's stack of
# paralysed termites held crosswise in the jaws opened a little wider round it, the head raised so the stack clears the
# ground; carry_small, a minor's (or a major's with one or two): the jaws nearly shut on them, the head less raised. The
# antennae still sweep the trail, as low as the raised head lets them.
CARRY = {'carry': dict(head=-18, jaws=6), 'carry_small': dict(head=-9, jaws=-9)}


def carry(name, clip):
    r = rig(name)
    c = CARRY[clip]
    tr = mo.Track(r)
    trail_antennae(tr, r, WALK_SECONDS, extra={'head': [c['head'], 0, 0]})
    out = tr.bones(tol=0.1)
    out.update(still(head=[c['head'], 0, 0], **jaws(c['jaws'])))
    return {'loop': True, 'animation_length': WALK_SECONDS, 'bones': out}


# --- feeling a wall ahead (climbing, 2026-10-23) --------------------------------------------------------------------
# An ant walking up to a wall touches it with its antennae before its head gets there and lifts its head a little, then
# climbs. The walk's antennae reach low and far ahead over the trail (past the jaws), so walking up to a wall the
# major's went into the stone (tools/matabele_climb.py found it, up to 0.09 blocks); here they are raised in a V, the
# whip bent up, the tips reaching just past the jaws, and they tap the wall ahead in turn (left, then right, twice a
# second). Played by the head's own player at the foot of a wall until the body stands up it (in the game the
# Matabele's entity would pick it there, as the carry is picked; the climbing preview shows where).
CLIMB_FEEL = dict(scape=-60.0, out=25.0, whip=-15.0, tap_scape=12.0, tap_whip=10.0, head=-5.0, seconds=1.0)


def climb_feel():
    c = CLIMB_FEEL
    T = c['seconds']
    out = {'head': keys([(0, [c['head'], 0, 0])])}
    for side, start, sy in (('l', 0.05, 1), ('r', 0.55, -1)):
        a1, a2 = f'antenna_{side}_1', f'antenna_{side}_2'
        rest1, rest2 = [c['scape'], -sy * c['out'], 0], [c['whip'], 0, 0]
        tap1, tap2 = [c['scape'] + c['tap_scape'], -sy * c['out'], 0], [c['whip'] + c['tap_whip'], 0, 0]
        out[a1] = keys([(0, rest1), (start, rest1), (start + 0.12, tap1), (start + 0.25, rest1), (T, rest1)])
        out[a2] = keys([(0, rest2), (start, rest2), (start + 0.12, tap2), (start + 0.25, rest2), (T, rest2)])
    return {'loop': True, 'animation_length': T, 'bones': out}


def animations(name):
    clips = {}
    clips.update(walk_clips(name))
    clips['idle'] = {'loop': True, 'animation_length': IDLE_SECONDS, 'bones': idle()}
    for clip, make in (('attack_a', attack_a), ('attack_b', attack_b), ('death', death)):
        made, miss = make(name)
        if miss > 0.05 and clip != 'death':
            raise ValueError(f'{name} {clip}: a planted foot misses its place by {miss:.3f}')
        clips[clip] = made
    for clip in CARRY:
        clips[clip] = carry(name, clip)
    clips['climb_feel'] = climb_feel()
    return {'format_version': '1.8.0', 'animations': clips}


def write(name, data):
    """Writes a model's clip file next to its draft model, the clips renamed to its bone names by its body plan."""
    path = os.path.join(mm.DRAFTS, 'animations', 'entity', f'{name}.animation.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(body_plan.load(name).animations_to_bones(data), f, indent=1)
    print('wrote', os.path.relpath(path, mm.mod_paths.PROJECT))


# --- the pose viewer page ------------------------------------------------------------------------------------------
# python tools/ant_pose_viewer.py matabele: every clip playing side by side, the major and the minor at their sizes
# (the minor drawn 0.455 / 0.95 of the major, the anim lab's starting sizes, still the user's to pick), the walks at a
# raid column's speeds, the attack's two ways, dying with and without the game's tip-over, and the carries with
# paralysed termites in the jaws (drawn by the page only: in the game the load is drawn at the jaws by the game).
MINOR_SIZE = lab.RENDER_SCALE['matabele_minor'] / lab.RENDER_SCALE['matabele_major']
TERMITE = dict(length=7.0, thick=1.9)       # a Macrotermes worker in the major's units: 0.3 of its body long (real)
TERMITE_BODY, TERMITE_HEAD = '#eadcb8', '#b06a26'


def termites(name, count):
    """Paralysed termites held crosswise at the jaws' tips (the carry socket), in the model's units: in rows of two, one
    row under the other, the heads to alternate sides, as Matabele majors carry them (up to about 10, Frank)."""
    piv = next(b['pivot'] for b in rig(name).model.bones if b['name'] == 'carry')
    k = 1.0 / MINOR_SIZE if mm.MODELS[name] else 1.0
    body, w = TERMITE['length'] * 0.82 * k, TERMITE['thick'] * k
    head = TERMITE['length'] * 0.18 * k
    cubes = []
    for i in range(count):
        row, col = divmod(i, 2)
        x0 = piv[0] - (body + head) / 2
        y0 = piv[1] - w * 0.6 - row * w           # the rows hang below the jaws' tips
        z0 = piv[2] - w / 2 + ((col - 0.5) * w if count > 1 else 0)
        right = i % 2 == 1
        cubes.append({'origin': [x0 + (head if right else 0), y0, z0], 'size': [body, w, w], 'colour': TERMITE_BODY})
        cubes.append({'origin': [x0 if right else x0 + body, y0 + w * 0.1, z0 + w * 0.1], 'size': [head, w * 0.8, w * 0.8],
                      'colour': TERMITE_HEAD})
    return {'bone': 'carry', 'cubes': cubes}


def viewer_bones(name):
    """The draft model's bones for the page, each box with a flat colour (the average of its painted faces)."""
    geo = json.load(open(os.path.join(mm.DRAFTS, 'geo', 'entity', f'{name}.geo.json'), encoding='utf-8'))
    bones = geo['minecraft:geometry'][0]['bones']
    colours = iter(lab.Model(name).cubes)
    for b in bones:
        for c in b.get('cubes', []):
            faces = list(next(colours)[2].values())
            c['colour'] = '#%02x%02x%02x' % tuple(round(sum(f[i] for f in faces) / len(faces)) for i in range(3))
    return bones


def viewer_data(entry):
    """The page's data in ant_pose_viewer.py's format; entry(name, note, bones, bps, model) makes one option."""
    out = {'title': 'Matabele pose viewer', 'scale': lab.RENDER_SCALE['matabele_major'], 'view': 1.6,
           'intro': 'The Matabele ant (a draft, not in the game): the major and, smaller, the minor, every clip '
                    'playing side by side on a floor with a block grid; behind them, each climbing a stone pillar.',
           'models': {n: viewer_bones(n) for n in MODELS}, 'anims': []}
    for name in MODELS:
        minor = mm.MODELS[name]
        who = 'Minor' if minor else 'Major'
        model = lab.Model(name)
        with open(os.path.join(mm.DRAFTS, 'animations', 'entity', f'{name}.animation.json'), encoding='utf-8') as f:
            clips = json.load(f)['animations']
        body_blocks = model.length * model.blocks_per_unit
        plan = body_plan.load(name)
        head = {plan.to_bone.get(r, r) for r in plan.head_player}

        def add(label, note, bones, bps=None, **more):
            e = entry(f'{who}: {label}', note, bones, bps, model)
            e.update(model=name, **more)
            if minor:
                e['size'] = round(MINOR_SIZE, 4)
            out['anims'].append(e)

        def over_walk(walk, carry_clip):
            return dict({b: c for b, c in clips[walk]['bones'].items() if b not in head}, **clips[carry_clip]['bones'])

        add('standing', 'feelers searching, the head turning a little, the jaws working once, the gaster breathing',
            clips['idle']['bones'])
        for clip, bl, label, words in (
                ('walk_slow', 1.0, 'slow walk', '1 body length a second: short strides, a looser tripod'),
                ('walk', 2.5, 'walk', '2.5 body lengths a second'),
                ('walk_fast', 4.5, 'fast walk', 'a raid column, 4.5 body lengths a second: long strides')):
            add(label, words + '; the feelers low, sweeping the scent trail', clips[clip]['bones'], bl * body_blocks)
        add('turning', 'stepping round to the right on the spot, 40 degrees a step cycle', clips['turn_r']['bones'])
        add('attack A', 'grab and sting under: draws back, lunges, the jaws snap shut, the rear tips up and the gaster '
                        'curls under as far as it goes, two jabs', clips['attack_a']['bones'], once=0.6)
        add('attack B', 'a quick bite and stab down: the jaws snap shut, the gaster stabs down behind the hind legs '
                        'twice', clips['attack_b']['bones'], once=0.6)
        add('dying, as the game tips it', "the clip with Minecraft's own tip-over of a dying mob onto its side "
                                          '(about its middle, at the floor)', clips['death']['bones'], once=2.0, tip=True)
        add('dying, no tip-over', 'the clip alone: kicks, the legs giving way, the body settling on the ground',
            clips['death']['bones'], once=2.0)
        if minor:
            add('carrying one termite', 'a minor with its one: the head a little raised, the jaws nearly shut',
                over_walk('walk_fast', 'carry_small'), 4.5 * body_blocks, load=termites(name, 1))
        else:
            add('carrying a stack', 'six termites crosswise in the jaws, opened a little round them, the head raised',
                over_walk('walk', 'carry'), 2.5 * body_blocks, load=termites(name, 6))
            add('carrying two', 'only two: the jaws nearly shut, the head a little raised, as a minor carries',
                over_walk('walk', 'carry_small'), 2.5 * body_blocks, load=termites(name, 2))
        # The climbing course (2026-10-23, tools/matabele_climb.py): the game's climbing maths worked out tick by tick.
        course = matabele_climb.viewer_course(name)
        course.update(feel=clips['climb_feel']['bones'], lane=1 if minor else 0)
        add('climbing', 'the slow walk, 1 body length a second, up a 3-block pillar, over the top edge, along the top, '
                        "head first down the far side and round the corner at its foot (the game's climbing maths; "
                        'the buttons under the scene pick the gaster and the antennae versions)',
            clips['walk_slow']['bones'], climb=course, tips={})
    return out


if __name__ == '__main__':
    mm.write_drafts()
    for model in MODELS:
        write(model, animations(model))
