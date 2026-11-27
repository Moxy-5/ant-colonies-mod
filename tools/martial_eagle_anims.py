"""Martial Eagle (Polemaetus bellicosus) animation suite, body plan, and game-asset exporter (Stage 5, 2026-11-25).

Exports all game assets for antcolonies:martial_eagle (savanna mod, flier family):
  - savanna/src/main/resources/assets/antcolonies/geo/entity/martial_eagle.geo.json  (349/400 boxes, faces=True)
  - savanna/src/main/resources/assets/antcolonies/textures/entity/martial_eagle.png  (512x512 crisp pixel-art)
  - savanna/src/main/resources/assets/antcolonies/animations/entity/martial_eagle.animation.json  (18 clips)
  - savanna/src/main/resources/assets/antcolonies/body_plans/martial_eagle.json  (flier + Motion Studio Phase 2 gaits)
  - savanna/src/main/resources/assets/antcolonies/body_plans/index.json

Dual-Wing Scale-Switching Architecture:
  - Perched / ground clips show sculpted folded wing capes (wing_fold_* scale 1) and hide spread wings (scale 0).
  - Flight clips show 3.32-block spread airfoil wings (wing_upper_* scale 1) and hide folded capes (scale 0).
  - takeoff and land_flare smoothly cross-scale wings mid-beat so every clip-to-clip blend is 100% seamless.

Run from C:\\Ant Mod\\neon-studio:
    python tools/martial_eagle_anims.py
    python tools/body_plan.py check
    python tools/clip_check.py martial_eagle --gait-changes --write
    python tools/clip_check.py martial_eagle --switches
"""
import json
import math
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import body_plan  # noqa: E402
import martial_eagle_models as me  # noqa: E402
from model_parts import clip, animations, geo, write_assets  # noqa: E402
from motion_kit import (  # noqa: E402
    ease, wing_cycle, soar_bank, head_stabilize, bird_look, biped_bird_gait,
)

# --- Exact skeleton rest constants (matching martial_eagle_models.py mode='game') -------------------------------------
HIP_Y, HIP_Z = 17.6, -1.2
KNEE_Y, KNEE_Z = 9.4, -0.6
ANKLE_Y, ANKLE_Z = 2.3, -2.9

L1 = math.hypot(KNEE_Y - HIP_Y, KNEE_Z - HIP_Z)       # 8.2219 px (hip -> knee)
L2 = math.hypot(ANKLE_Y - KNEE_Y, ANKLE_Z - KNEE_Z)   # 7.4632 px (knee -> ankle)
TH1_REST = math.degrees(math.atan2(KNEE_Z - HIP_Z, -(KNEE_Y - HIP_Y)))      # +4.184 deg
TH2_REST = math.degrees(math.atan2(ANKLE_Z - KNEE_Z, -(ANKLE_Y - KNEE_Y)))  # -17.949 deg

# Base rest pitch of body/neck/head in perched mode='game'
PERCH_BODY_PITCH = me.BODY_PITCH  # 48.0 deg

FOLD_WING_BONES = ('wing_fold_left', 'wing_fold_right')
SPREAD_WING_BONES = ('wing_upper_left', 'wing_upper_right')


def _clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


def _lerp(a, b, t):
    return a + (b - a) * t


def _smoothstep(a, b, x):
    if x <= a:
        return 0.0
    if x >= b:
        return 1.0
    return ease((x - a) / (b - a))


def bell(u, a, peak, b):
    """Smooth 0 -> 1 -> 0 envelope rising from a to peak and falling from peak to b."""
    if u <= a or u >= b:
        return 0.0
    if u <= peak:
        return ease((u - a) / max(1e-6, peak - a))
    return 1.0 - ease((u - peak) / max(1e-6, b - peak))


def _solve_bird_leg(dy_ankle, dz_ankle, hip_y_shift=0.0):
    """Exact 2-link avian leg IK returning (d_hip_x, d_knee_x, d_foot_x) degrees so ankle lands at
    (ANKLE_Y + dy_ankle, ANKLE_Z + dz_ankle) while foot stays level with ground (0 deg world pitch).
    hip_y_shift accounts for vertical root position shift."""
    ty = (ANKLE_Y + dy_ankle) - (HIP_Y + hip_y_shift)
    tz = (ANKLE_Z + dz_ankle) - HIP_Z
    r = math.hypot(ty, tz)
    max_r = (L1 + L2) - 0.12
    min_r = abs(L1 - L2) + 0.5
    if r > max_r:
        scale = max_r / r
        ty *= scale
        tz *= scale
        r = max_r
    elif r < min_r:
        scale = min_r / max(1e-4, r)
        ty *= scale
        tz *= scale
        r = min_r
    phi = math.atan2(tz, -ty)
    cos_a = _clamp((L1 * L1 + r * r - L2 * L2) / (2.0 * L1 * r), -1.0, 1.0)
    # Bird intertarsal joint bends BACKWARD (+Z -> +alpha)
    th1 = phi + math.acos(cos_a)
    dy2 = ty + L1 * math.cos(th1)
    dz2 = tz - L1 * math.sin(th1)
    th2 = math.atan2(dz2, -dy2)
    d_hip = math.degrees(th1) - TH1_REST
    d_knee = (math.degrees(th2) - math.degrees(th1)) - (TH2_REST - TH1_REST)
    d_foot = -(d_hip + d_knee)
    return d_hip, d_knee, d_foot


def _set(pose, bone, rot=None, pos=None, scale=None):
    ent = pose.setdefault(bone, {})
    if rot is not None:
        ent['rotation'] = [float(rot[0]), float(rot[1]), float(rot[2])]
    if pos is not None:
        ent['position'] = [float(pos[0]), float(pos[1]), float(pos[2])]
    if scale is not None:
        ent['scale'] = [float(scale[0]), float(scale[1]), float(scale[2])]


def _wings_visibility(pose, fold_s=1.0, spread_s=0.0):
    """Explicitly sets folded vs spread wing scale channels on every keyframe."""
    for b in FOLD_WING_BONES:
        _set(pose, b, scale=[fold_s, fold_s, fold_s])
    for b in SPREAD_WING_BONES:
        _set(pose, b, scale=[spread_s, spread_s, spread_s])


def _blink_at(pose, amt):
    """Natural smooth avian blink/squint on aligned eyelids (amt in 0..1)."""
    if amt <= 1e-4:
        for side in ('left', 'right'):
            _set(pose, f'ulid_{side}', pos=[0.0, 0.0, 0.0], scale=[1.0, 1.0, 1.0])
            _set(pose, f'lid_{side}', pos=[0.0, 0.0, 0.0], scale=[1.0, 1.0, 1.0])
        return
    for side in ('left', 'right'):
        _set(pose, f'ulid_{side}', pos=[0.0, -amt * 0.42, 0.0], scale=[1.0, 1.0 + amt * 2.0, 1.0])
        _set(pose, f'lid_{side}', pos=[0.0, amt * 0.36, 0.0], scale=[1.0, 1.0 + amt * 1.7, 1.0])


def _blink_pulse(t, times, dur=0.18):
    best = 0.0
    for tc in times:
        dt = abs(t - tc)
        if dt < dur * 0.5:
            best = max(best, math.cos(math.pi * dt / dur) ** 1.6)
    return best


def _set_eyes(pose, brow_deg=0.0, pupil_z=0.0, blink=0.0):
    """Expressive raptor brow glare (+deg lowers fierce brow) + pupil scan + blink."""
    _blink_at(pose, blink)
    for s, side in ((1, 'left'), (-1, 'right')):
        _set(pose, f'brow_{side}', rot=[brow_deg * 0.45, 0.0, -s * brow_deg * 0.65])
        _set(pose, f'pupil_{side}', pos=[0.0, 0.0, -pupil_z * 0.30])


def _set_crest(pose, raise_delta=0.0):
    """Raises (+deg -> negative pitch) or sleekly flattens (-deg -> positive pitch) the 2-tier occipital crest."""
    _set(pose, 'crest_c', rot=[-raise_delta, 0.0, 0.0])
    for side in ('left', 'right'):
        _set(pose, f'crest_{side}', rot=[-raise_delta * 0.85, 0.0, 0.0])


def _set_tail(pose, pitch=0.0, yaw=0.0, roll=0.0, spread=0.0):
    """Articulates the 7-feather graduated tail fan (spread in -0.2..+1.0 -> delta fan yaw)."""
    _set(pose, 'tail_base', rot=[pitch, yaw, roll])
    _set(pose, 'tail_left', rot=[0.0, spread * 22.0, -spread * 4.0])
    _set(pose, 'tail_right', rot=[0.0, -spread * 22.0, spread * 4.0])


def _set_perch_grip(pose, curl=14.0):
    """Subtle curved perch grip wrapping talons gently around a horizontal branch bar."""
    for side in ('left', 'right'):
        for idx in (1, 2, 3):
            _set(pose, f'leg_toe{idx}_{side}', rot=[curl * 0.45, 0.0, 0.0])
            _set(pose, f'leg_talon{idx}_{side}', rot=[curl * 0.55, 0.0, 0.0])
        _set(pose, f'leg_hallux_{side}', rot=[-curl * 0.45, 0.0, 0.0])
        _set(pose, f'leg_hallux_talon_{side}', rot=[-curl * 0.55, 0.0, 0.0])


def _set_flight_base(pose, body_pitch_extra=0.0, body_roll=0.0, body_yaw=0.0, body_y=0.0,
                     head_yaw=0.0, head_pitch=0.0, leg_tuck=1.0, toe_clench=1.0,
                     tail_pitch=0.0, tail_yaw=0.0, tail_roll=0.0, tail_spread=0.45):
    """Sets horizontal flight torso (+48 deg delta from upright perch rest), tucked feathered legs,
    gimbal-stabilized neck/head, and tail trim. Spread wings visible, folded capes hidden."""
    _wings_visibility(pose, fold_s=0.0, spread_s=1.0)
    _set(pose, 'root', pos=[0.0, body_y, 0.0])
    _set(pose, 'body', rot=[PERCH_BODY_PITCH + body_pitch_extra, body_yaw, body_roll])

    # Cancel perched neck/head rest counter-pitch (-48 deg total) + apply gimbal head stabilization!
    stab = head_stabilize(body_pitch_extra, body_yaw, body_roll, neck_weights=(0.24, 0.34, 0.42), gain=0.88)
    _set(pose, 'neck_1', rot=[-PERCH_BODY_PITCH * 0.04 + stab[0][0] + head_pitch * 0.20,
                              stab[0][1] + head_yaw * 0.25, stab[0][2]])
    _set(pose, 'neck_2', rot=[-PERCH_BODY_PITCH * 0.24 + stab[1][0] + head_pitch * 0.32,
                              stab[1][1] + head_yaw * 0.35, stab[1][2]])
    _set(pose, 'head',   rot=[-PERCH_BODY_PITCH * 0.72 + stab[2][0] + head_pitch * 0.48,
                              stab[2][1] + head_yaw * 0.40, stab[2][2]])

    # Tucked aerodynamic eagle legs swinging cleanly BACKWARD (+78 deg!) under the tail
    for s, side in ((1, 'left'), (-1, 'right')):
        _set(pose, f'leg_upper_{side}', rot=[78.0 * leg_tuck, 0.0, s * 3.5 * leg_tuck])
        _set(pose, f'leg_lower_{side}', rot=[-10.0 * leg_tuck, 0.0, 0.0])
        _set(pose, f'leg_foot_{side}',  rot=[12.0 * leg_tuck, 0.0, 0.0])
        for idx in (1, 2, 3):
            _set(pose, f'leg_toe{idx}_{side}', rot=[48.0 * toe_clench, 0.0, 0.0])
            _set(pose, f'leg_talon{idx}_{side}', rot=[18.0 * toe_clench, 0.0, 0.0])
        _set(pose, f'leg_hallux_{side}', rot=[-44.0 * toe_clench, 0.0, 0.0])
        _set(pose, f'leg_hallux_talon_{side}', rot=[-16.0 * toe_clench, 0.0, 0.0])

    _set_tail(pose, pitch=-3.5 + tail_pitch, yaw=tail_yaw, roll=tail_roll, spread=tail_spread)


def _apply_spread_wing(pose, s, side, sh_roll=0.0, sh_yaw=0.0, sh_pitch=0.0,
                       el_roll=0.0, el_yaw=0.0, ha_roll=0.0, ha_yaw=0.0, ha_pitch=0.0,
                       tip_roll=0.0, tip_yaw=0.0, alula_pitch=0.0, alula_roll=0.0):
    """Applies anatomically consistent left (+1) / right (-1) spread-wing joint angles.
    Sign convention verified for GeckoLib ZYX:
      - Positive sh_roll raises wing UP (+Y on both sides via -s * roll on Z!).
      - Positive sh_yaw sweeps wing FORWARD (-Z on both sides via +s * yaw on Y!).
      - Positive sh_pitch tilts leading edge UP (supination/brake via -pitch on X!)."""
    _set(pose, f'wing_upper_{side}', rot=[-sh_pitch, s * sh_yaw, -s * sh_roll])
    _set(pose, f'wing_fore_{side}',  rot=[-sh_pitch * 0.25, s * el_yaw, -s * el_roll])
    _set(pose, f'wing_hand_{side}',  rot=[-ha_pitch, s * ha_yaw, -s * ha_roll])
    _set(pose, f'wing_tips_{side}',  rot=[-ha_pitch * 0.35, s * tip_yaw, -s * tip_roll])
    _set(pose, f'alula_{side}',      rot=[-alula_pitch, s * (alula_pitch * 0.35), -s * alula_roll])


# ======================================================================================================================
# 1. PERCHED / INTELLIGENT SENTINEL CLIPS
# ======================================================================================================================
def make_perch_idle():
    """4.8 s sentinel loop: crisp avian saccadic gaze holds, curious head-tilts, natural blinks, breathing."""
    L = 4.8
    waypoints = [
        (0.0,   0.0,   0.0,   0.0, 0.20,  2.0),
        (0.75, 24.0,   4.0,  -8.0, 0.35,  4.0),  # crisp left horizon scan
        (1.65, 22.0,  10.0, -13.0, 0.48,  6.0),  # curious tilt peeking down at savanna grass
        (2.45, -26.0,  2.0,   7.0, 0.30,  3.0),  # rapid saccade right!
        (3.45, -18.0, -6.0,   4.0, 0.42,  2.0),  # brief sky check
        (4.25,  0.0,   0.0,   0.0, 0.20,  2.0),  # return to regal center lock
        (4.80,  0.0,   0.0,   0.0, 0.20,  2.0),
    ]

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        _set_perch_grip(p, curl=14.0)
        br = math.sin(2.0 * math.pi * t / 2.4)
        yaw, pit, roll, cr, brow = bird_look(t, waypoints, snap_s=0.18)
        _set(p, 'root', pos=[0.0, 0.0, 0.0])
        _set(p, 'body', rot=[br * 1.1, yaw * 0.08, roll * 0.08])
        _set(p, 'neck_1', rot=[-br * 0.4 + pit * 0.22, yaw * 0.28, roll * 0.15])
        _set(p, 'neck_2', rot=[-br * 0.4 + pit * 0.33, yaw * 0.34, roll * 0.30])
        _set(p, 'head',   rot=[-br * 0.3 + pit * 0.45, yaw * 0.38, roll * 0.55])
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=(cr - 0.20) * 26.0)
        _set_eyes(p, brow_deg=brow, pupil_z=yaw / 30.0, blink=_blink_pulse(t, (1.15, 3.15, 4.45)))
        _set_tail(p, pitch=-br * 1.4, yaw=-yaw * 0.12, spread=0.0)
        for s, side in ((1, 'left'), (-1, 'right')):
            _set(p, f'wing_fold_{side}', rot=[0.0, 0.0, -s * br * 0.8])
        return p

    return clip(L, pose, steps=48, loop=True)


def make_perch_alert():
    """2.0 s locked-on hunting posture: leaned forward, high spiked crest, fierce glare, subtle ready shrug."""
    L = 2.0

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        _set_perch_grip(p, curl=18.0)
        th = 2.0 * math.pi * t / L
        pulse = math.sin(th)
        scan = math.sin(th * 2.0) * 5.0
        # Slight athletic crouch leaning forward over the branch
        d_hip, d_knee, d_foot = _solve_bird_leg(0.0, 0.0, hip_y_shift=-0.85)
        _set(p, 'root', pos=[0.0, -0.85, 0.0])
        for side in ('left', 'right'):
            _set(p, f'leg_upper_{side}', rot=[d_hip, 0.0, 0.0])
            _set(p, f'leg_lower_{side}', rot=[d_knee, 0.0, 0.0])
            _set(p, f'leg_foot_{side}',  rot=[d_foot, 0.0, 0.0])
        _set(p, 'body', rot=[11.0 + pulse * 1.2, scan * 0.2, 0.0])
        _set(p, 'neck_1', rot=[-2.5, scan * 0.3, 0.0])
        _set(p, 'neck_2', rot=[-3.5, scan * 0.3, 0.0])
        _set(p, 'head',   rot=[-4.0 + pulse * 1.0, scan * 0.4, math.sin(th) * 4.0])
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=20.0 + pulse * 3.0)
        _set_eyes(p, brow_deg=11.0, pupil_z=0.45 + 0.25 * math.sin(th * 2.0), blink=_blink_pulse(t, (1.45,)))
        _set_tail(p, pitch=-6.0 - pulse * 1.5, spread=0.18)
        for s, side in ((1, 'left'), (-1, 'right')):
            _set(p, f'wing_fold_{side}', rot=[-4.0, s * 2.5, -s * (3.5 + pulse * 1.2)])
        return p

    return clip(L, pose, steps=24, loop=True)


def make_perch_call():
    """1.8 s piercing territorial eagle whistle: throws head skyward, beak opens wide showing tongue, crest spikes!"""
    L = 1.8

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        _set_perch_grip(p, curl=16.0)
        u = t / L
        # Crisp anticipation -> high proud cry -> resonant hold -> smooth return to perch_idle!
        cry = bell(u, 0.14, 0.42, 0.84)
        trem = math.sin(t * 28.0) * 1.6 * bell(u, 0.22, 0.48, 0.78)
        _set(p, 'root', pos=[0.0, 0.0, 0.0])
        _set(p, 'body', rot=[-6.0 * cry, 0.0, 0.0])
        _set(p, 'neck_1', rot=[-5.0 * cry, 0.0, 0.0])
        _set(p, 'neck_2', rot=[-9.0 * cry, 0.0, 0.0])
        _set(p, 'head',   rot=[-16.0 * cry + trem, 0.0, 0.0])
        _set(p, 'jaw',    rot=[30.0 * cry + abs(trem) * 1.2, 0.0, 0.0])
        _set(p, 'tongue', rot=[-6.0 * cry, 0.0, 0.0])
        _set_crest(p, raise_delta=26.0 * cry)
        _set_eyes(p, brow_deg=7.0 * cry, pupil_z=0.25 * cry, blink=_blink_pulse(t, (0.12, 1.52)))
        _set_tail(p, pitch=-9.0 * cry, spread=0.35 * cry)
        for s, side in ((1, 'left'), (-1, 'right')):
            _set(p, f'wing_fold_{side}', rot=[-3.0 * cry, s * 3.0 * cry, -s * (6.5 * cry)])
        return p

    return clip(L, pose, steps=36, loop=False)


def make_perch_preen():
    """3.2 s intelligent feather care: bends neck cleanly to left shoulder coverts, nibbles 3x, returns to rest."""
    L = 3.2

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        _set_perch_grip(p, curl=14.0)
        u = t / L
        reach = bell(u, 0.08, 0.28, 0.88)
        nibble = max(0.0, math.sin((u - 0.24) * 3.0 * 2.0 * math.pi)) * bell(u, 0.24, 0.55, 0.80)
        _set(p, 'root', pos=[0.0, 0.0, 0.0])
        _set(p, 'body', rot=[4.0 * reach, 8.0 * reach, 3.0 * reach])
        _set(p, 'neck_1', rot=[12.0 * reach, 24.0 * reach, 4.0 * reach])
        _set(p, 'neck_2', rot=[18.0 * reach, 30.0 * reach, 6.0 * reach])
        _set(p, 'head',   rot=[22.0 * reach + nibble * 5.0, 26.0 * reach, -14.0 * reach])
        _set(p, 'jaw',    rot=[12.0 * nibble, 0.0, 0.0])
        _set_crest(p, raise_delta=-6.0 * reach)
        _set_eyes(p, brow_deg=-4.0 * reach, blink=0.45 * reach + _blink_pulse(t, (2.85,)))
        _set_tail(p, pitch=2.0 * reach, yaw=-6.0 * reach, spread=0.05 * reach)
        _set(p, 'wing_fold_left', rot=[-4.0 * reach, 6.0 * reach, -8.0 * reach])
        _set(p, 'wing_fold_right', rot=[0.0, 0.0, 0.0])
        return p

    return clip(L, pose, steps=48, loop=False)


def make_perch_rouse():
    """2.2 s iconic eagle feather-rouse: fluffs crest, briskly shakes folded wings & tail fan, settles sleek."""
    L = 2.2

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        _set_perch_grip(p, curl=16.0)
        u = t / L
        puff = bell(u, 0.10, 0.42, 0.86)
        shake = math.sin(t * 28.0) * bell(u, 0.22, 0.50, 0.76)
        _set(p, 'root', pos=[0.0, 0.0, 0.0])
        _set(p, 'body', rot=[3.0 * puff, shake * 4.0, shake * 7.5])
        _set(p, 'neck_1', rot=[-2.0 * puff, -shake * 3.0, -shake * 4.0])
        _set(p, 'neck_2', rot=[-2.0 * puff, -shake * 2.0, -shake * 3.0])
        _set(p, 'head',   rot=[2.0 * puff, shake * 6.0, -shake * 5.0])
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=24.0 * puff)
        _set_eyes(p, brow_deg=-2.0 * puff, blink=0.75 * bell(u, 0.26, 0.50, 0.72))
        _set_tail(p, pitch=-4.0 * puff, yaw=shake * 10.0, roll=shake * 8.0, spread=0.45 * puff)
        for s, side in ((1, 'left'), (-1, 'right')):
            _set(p, f'wing_fold_{side}', rot=[-4.0 * puff, s * (5.0 * puff), -s * (11.0 * puff + shake * 6.0)])
            _set(p, f'wing_fold_tip_{side}', rot=[0.0, s * (4.0 * puff), -s * (shake * 5.0)])
        return p

    return clip(L, pose, steps=44, loop=False)


# ======================================================================================================================
# 2. GROUND SENTINEL, BIPEDAL WALK, BOUNDING HOP, CARCASS TEAR & DEATH
# ======================================================================================================================
def make_idle():
    """3.6 s flat-ground standing idle: feet planted flat at y=0.00 px, crisp avian gaze scans & blinks."""
    L = 3.6
    waypoints = [
        (0.0,   0.0,  0.0,  0.0, 0.15, 2.0),
        (0.85, 20.0,  3.0, -6.0, 0.25, 3.0),
        (1.90, -22.0, 4.0,  7.0, 0.30, 4.0),
        (2.90,  0.0,  0.0,  0.0, 0.15, 2.0),
        (3.60,  0.0,  0.0,  0.0, 0.15, 2.0),
    ]

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        br = math.sin(2.0 * math.pi * t / 1.8)
        yaw, pit, roll, cr, brow = bird_look(t, waypoints, snap_s=0.16)
        _set(p, 'root', pos=[0.0, 0.0, 0.0])
        _set(p, 'body', rot=[br * 0.9, yaw * 0.06, roll * 0.06])
        _set(p, 'neck_1', rot=[-br * 0.3 + pit * 0.22, yaw * 0.28, roll * 0.15])
        _set(p, 'neck_2', rot=[-br * 0.3 + pit * 0.33, yaw * 0.34, roll * 0.30])
        _set(p, 'head',   rot=[-br * 0.3 + pit * 0.45, yaw * 0.38, roll * 0.55])
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=(cr - 0.15) * 20.0)
        _set_eyes(p, brow_deg=brow, pupil_z=yaw / 28.0, blink=_blink_pulse(t, (1.25, 3.05)))
        _set_tail(p, pitch=-br * 1.1, yaw=-yaw * 0.10, spread=0.0)
        return p

    return clip(L, pose, steps=36, loop=True)


WALK_CYCLE_S = 1.0
WALK_DUTY = 0.64
WALK_STRIDE = 11.0
WALK_SPEED_PX_S = WALK_STRIDE / (WALK_DUTY * WALK_CYCLE_S)  # 17.1875 px/s


def make_walk():
    """1.0 s proud bipedal eagle ground strut: exact 2-link IK keeps planted talons dead flat at y=0.00 px,
    sliding smoothly backward at WALK_SPEED_PX_S with ZERO ground clip, subtle raptor waddle & avian head-bob!"""
    L = WALK_CYCLE_S

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        ph = (t / L) % 1.0
        g = biped_bird_gait(ph, duty=WALK_DUTY, stride_len=WALK_STRIDE, lift_h=3.0,
                            waddle_roll=4.5, body_pitch_amp=2.6, head_bob_amp=2.0, cycle_s=L)
        hip_drop = -0.65 + g['body_y'] * 0.55
        _set(p, 'root', pos=[0.0, hip_drop, 0.0])
        _set(p, 'body', rot=[4.0 + g['body_pitch'], 0.0, g['body_roll']])

        # Classic eagle ground head-bob + counter-roll stabilization
        bob_pit = g['head_dz'] * 2.2
        _set(p, 'neck_1', rot=[-2.0 - g['body_pitch'] * 0.3 + bob_pit * 0.35, 0.0, -g['body_roll'] * 0.35])
        _set(p, 'neck_2', rot=[-1.0 - g['body_pitch'] * 0.3 + bob_pit * 0.45, 0.0, -g['body_roll'] * 0.35])
        _set(p, 'head',   rot=[-1.0 - g['body_pitch'] * 0.4 - bob_pit * 0.80, 0.0, -g['body_roll'] * 0.30])
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=3.0)
        _set_eyes(p, brow_deg=3.0, blink=_blink_pulse(t, (0.72,)))
        _set_tail(p, pitch=-3.0 - g['body_pitch'] * 0.5, yaw=-g['body_roll'] * 0.7, spread=0.05)

        for key, s, side in (('l', 1, 'left'), ('r', -1, 'right')):
            ft = g['feet'][key]
            d_hip, d_knee, d_foot = _solve_bird_leg(ft['dy'], ft['dz'], hip_y_shift=hip_drop)
            # Subtle toe curl during swing lift, 100% flat splayed pad when planted!
            lift = ft['lift']
            _set(p, f'leg_upper_{side}', rot=[d_hip, 0.0, -s * g['body_roll'] * 0.35])
            _set(p, f'leg_lower_{side}', rot=[d_knee, 0.0, 0.0])
            _set(p, f'leg_foot_{side}',  rot=[d_foot + lift * 14.0, 0.0, s * g['body_roll'] * 0.35])
            for idx in (1, 2, 3):
                _set(p, f'leg_toe{idx}_{side}', rot=[lift * 16.0, 0.0, 0.0])
            _set(p, f'leg_hallux_{side}', rot=[-lift * 14.0, 0.0, 0.0])
            # Hitch folded wingtips cleanly UPWARD (+X on +Z trailing wing!) so flight feathers clear the ground!
            _set(p, f'wing_fold_{side}', rot=[6.0, 0.0, -s * (2.6 + abs(g['body_roll']) * 0.35)])
            _set(p, f'wing_fold_tip_{side}', rot=[4.0, 0.0, 0.0])
        return p

    return clip(L, pose, steps=32, loop=True)


HOP_CYCLE_S = 0.65
HOP_STRIDE = 16.5
HOP_SPEED_PX_S = HOP_STRIDE / HOP_CYCLE_S  # 25.38 px/s


def make_hop():
    """0.65 s bounding ground eagle hop-run: crisp two-footed spring with folded wings flared slightly for balance."""
    L = HOP_CYCLE_S

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        u = (t / L) % 1.0
        # 0.00..0.32 crouch/push planted; 0.32..0.82 airborne arc; 0.82..1.00 cushioned land!
        if u < 0.32:
            c = math.sin(math.pi * (u / 0.32))
            hip_y = -2.2 * c
            foot_dy = 0.0
            foot_dz = _lerp(-3.5, 4.2, u / 0.32)
            air = 0.0
        elif u < 0.82:
            f = (u - 0.32) / 0.50
            air = math.sin(math.pi * f)
            hip_y = 4.6 * air
            foot_dy = 4.2 * air
            foot_dz = _lerp(4.2, -3.5, ease(f))
        else:
            f = (u - 0.82) / 0.18
            hip_y = -1.6 * math.sin(math.pi * f)
            foot_dy = 0.0
            foot_dz = -3.5
            air = 0.0

        _set(p, 'root', pos=[0.0, hip_y, 0.0])
        bp = 10.0 - 7.0 * air
        _set(p, 'body', rot=[bp, 0.0, 0.0])
        _set(p, 'neck_1', rot=[-bp * 0.30, 0.0, 0.0])
        _set(p, 'neck_2', rot=[-bp * 0.35, 0.0, 0.0])
        _set(p, 'head',   rot=[-bp * 0.35, 0.0, 0.0])
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=8.0 * air)
        _set_eyes(p, brow_deg=6.0)
        _set_tail(p, pitch=-8.0 - 6.0 * air, spread=0.35 * air)

        d_hip, d_knee, d_foot = _solve_bird_leg(foot_dy, foot_dz, hip_y_shift=hip_y)
        for s, side in ((1, 'left'), (-1, 'right')):
            _set(p, f'leg_upper_{side}', rot=[d_hip, 0.0, 0.0])
            _set(p, f'leg_lower_{side}', rot=[d_knee, 0.0, 0.0])
            _set(p, f'leg_foot_{side}',  rot=[d_foot + air * 18.0, 0.0, 0.0])
            for idx in (1, 2, 3):
                _set(p, f'leg_toe{idx}_{side}', rot=[air * 18.0, 0.0, 0.0])
            _set(p, f'wing_fold_{side}', rot=[-6.0 * air, s * (4.0 * air), -s * (14.0 * air + 3.0)])
        return p

    return clip(L, pose, steps=26, loop=True)


def make_eat_tear():
    """2.2 s powerful raptor feeding cycle (ends cleanly upright so eat_tear -> idle has 0.0 px blend jump!)."""
    L = 2.2

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        u = _clamp(t / L, 0.0, 1.0)
        # 0.00..0.34 plunge beak down to talons; 0.34..0.52 bite & clamp; 0.52..0.76 powerful upward tear pull; 0.76..1.00 gulp & settle!
        if u < 0.34:
            f = ease(u / 0.34)
            lean = 26.0 * f
            neck_d = 32.0 * f
            jaw = 18.0 * math.sin(math.pi * f)
            pull = 0.0
        elif u < 0.52:
            f = (u - 0.34) / 0.18
            lean = 26.0 + 3.0 * math.sin(math.pi * f)
            neck_d = 32.0 + 4.0 * math.sin(math.pi * f)
            jaw = 4.0 * (1.0 - f)
            pull = 0.0
        elif u < 0.76:
            f = ease((u - 0.52) / 0.24)
            lean = _lerp(26.0, 2.0, f)
            neck_d = _lerp(32.0, -14.0, f)
            jaw = 2.0
            pull = math.sin(math.pi * f)
        else:
            f = ease((u - 0.76) / 0.24)
            lean = _lerp(2.0, 0.0, f)
            neck_d = _lerp(-14.0, 0.0, f)
            jaw = 8.0 * math.sin(math.pi * f)
            pull = 0.0

        hip_drop = -1.6 * (lean / 26.0)
        d_hip, d_knee, d_foot = _solve_bird_leg(0.0, 0.0, hip_y_shift=hip_drop)
        _set(p, 'root', pos=[0.0, hip_drop, 0.0])
        _set(p, 'body', rot=[lean, 0.0, 0.0])
        _set(p, 'neck_1', rot=[neck_d * 0.28, 0.0, 0.0])
        _set(p, 'neck_2', rot=[neck_d * 0.34, 0.0, 0.0])
        _set(p, 'head',   rot=[neck_d * 0.38, pull * 8.0, pull * -6.0])
        _set(p, 'jaw',    rot=[jaw, 0.0, 0.0])
        _set_crest(p, raise_delta=6.0 * math.sin(math.pi * u) + 10.0 * pull)
        _set_eyes(p, brow_deg=8.0 * math.sin(math.pi * u), blink=_blink_pulse(t, (0.45, 1.85)))
        _set_tail(p, pitch=-lean * 0.45, spread=0.22 * pull)
        for s, side in ((1, 'left'), (-1, 'right')):
            _set(p, f'leg_upper_{side}', rot=[d_hip, 0.0, 0.0])
            _set(p, f'leg_lower_{side}', rot=[d_knee, 0.0, 0.0])
            _set(p, f'leg_foot_{side}',  rot=[d_foot, 0.0, 0.0])
            _set(p, f'wing_fold_{side}', rot=[-4.0 * pull, 0.0, -s * (5.0 * math.sin(math.pi * u) + 7.0 * pull)])
        return p

    return clip(L, pose, steps=44, loop=False)


def make_death():
    """1.4 s natural collapse onto the savanna ground (hold_on_last_frame, 0.00 px ground clip, eyelids shut!)."""
    L = 1.4

    def pose(t):
        p = {}
        _wings_visibility(p, 1.0, 0.0)
        u = ease(_clamp(t / L, 0.0, 1.0))
        # Phase 1 (0..0.55): Legs buckle smoothly keeping feet on y=0.0; Phase 2 (0.55..1.0): slumps cleanly onto ground!
        sink = _smoothstep(0.0, 0.55, u)
        slump = _smoothstep(0.45, 1.0, u)
        hip_y = -6.2 * sink - 2.6 * slump  # -8.8 px total: breast & folded wings rest flush on savanna ground!
        d_hip, d_knee, d_foot = _solve_bird_leg(1.4 * slump, 2.2 * sink, hip_y_shift=hip_y)
        _set(p, 'root', pos=[0.0, hip_y, 0.0])
        _set(p, 'body', rot=[42.0 * u, 0.0, 26.0 * slump])
        _set(p, 'neck_1', rot=[4.0 * u, -10.0 * u, 6.0 * slump])
        _set(p, 'neck_2', rot=[6.0 * u, -12.0 * u, 8.0 * slump])
        _set(p, 'head',   rot=[-26.0 * u, -12.0 * u, 14.0 * slump])
        _set(p, 'jaw',    rot=[10.0 * u, 0.0, 0.0])
        _set_crest(p, raise_delta=-14.0 * u)
        _set_eyes(p, brow_deg=-4.0 * u, blink=u)
        _set_tail(p, pitch=12.0 * u, spread=0.18 * u)
        for s, side in ((1, 'left'), (-1, 'right')):
            _set(p, f'leg_upper_{side}', rot=[d_hip, 0.0, s * (10.0 * slump)])
            _set(p, f'leg_lower_{side}', rot=[d_knee, 0.0, 0.0])
            _set(p, f'leg_foot_{side}',  rot=[d_foot - 6.0 * slump, 0.0, 0.0])
            for idx in (1, 2, 3):
                _set(p, f'leg_toe{idx}_{side}', rot=[-6.0 * slump, 0.0, 0.0])
            _set(p, f'leg_hallux_{side}', rot=[6.0 * slump, 0.0, 0.0])
            _set(p, f'wing_fold_{side}', rot=[10.0 * u, s * 10.0 * u, -s * (16.0 * u)])
            _set(p, f'wing_fold_tip_{side}', rot=[6.0 * u, 0.0, 0.0])
        return p

    return clip(L, pose, steps=28, loop='hold')


# ======================================================================================================================
# 3. FLIGHT, SOARING, THERMAL BANKING, HUNTING STOOP & LANDING SUITE
# ======================================================================================================================
def _fly_flap_pose(ph, amp=36.0, heave=2.4):
    """Shared majestic slow-cadence Martial Eagle flapping pose at phase ph in [0, 1)."""
    p = {}
    wc = wing_cycle(ph, flap_amp=amp, up_fold=30.0, wrist_lead=0.14, feather_twist=18.0,
                    asymmetry=0.44, body_heave=heave, tip_flex=16.0)
    _set_flight_base(
        p, body_pitch_extra=wc['body_pitch'] * 0.65, body_y=wc['body_y'],
        leg_tuck=1.0, toe_clench=1.0,
        tail_pitch=-wc['body_pitch'] * 0.55 - 2.0,
        tail_spread=0.35 + wc['power'] * 0.20,
    )
    _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
    _set_crest(p, raise_delta=-10.0)
    _set_eyes(p, brow_deg=6.0)

    for s, side in ((1, 'left'), (-1, 'right')):
        _apply_spread_wing(
            p, s, side,
            sh_roll=wc['shoulder_roll'],
            sh_yaw=wc['shoulder_yaw'],
            sh_pitch=wc['shoulder_pitch'],
            el_roll=wc['elbow_roll'],
            el_yaw=-wc['elbow_fold'] * 0.85,
            ha_roll=wc['wrist_flex'],
            ha_yaw=wc['elbow_fold'] * 0.30,
            ha_pitch=wc['wrist_twist'],
            tip_roll=wc['wrist_flex'] * 0.55 + wc['power'] * 8.0,
            tip_yaw=wc['primary_splay'] * 6.0,
            alula_pitch=wc['power'] * 8.0,
            alula_roll=wc['power'] * 5.0,
        )
    return p


def make_fly_flap():
    """1.0 s deep, authoritative Martial Eagle powered wingbeat (passes wing_wave with clean outer-tip lag!)."""
    L = 1.0
    return clip(L, lambda t: _fly_flap_pose((t / L) % 1.0), steps=32, loop=True)


def make_soar_glide():
    """3.2 s effortless thermal soaring on 3.32-block wings: raised dihedral, periodic tip gusts, scanning head."""
    L = 3.2
    waypoints = [
        (0.0,   0.0,  6.0,  0.0, 0.0, 6.0),
        (0.95, 18.0, 12.0, -5.0, 0.0, 8.0),
        (2.05,-16.0, 10.0,  5.0, 0.0, 8.0),
        (2.90,  0.0,  6.0,  0.0, 0.0, 6.0),
        (3.20,  0.0,  6.0,  0.0, 0.0, 6.0),
    ]

    def pose(t):
        p = {}
        # Exact periodic harmonic thermal micro-gusts (0.0 px loop seam at t=0 == t=L!)
        th = 2.0 * math.pi * (t / L)
        g1 = math.sin(th) * 0.65 + math.sin(th * 2.0 + 0.7) * 0.35
        g2 = math.sin(th + 1.4) * 0.65 + math.sin(th * 2.0 + 2.1) * 0.35
        hyaw, hpit, hroll, _, brow = bird_look(t, waypoints, snap_s=0.22)
        _set_flight_base(
            p, body_pitch_extra=-1.5 + math.sin(th) * 1.2,
            body_roll=g1 * 1.1, body_y=math.sin(th) * 0.9,
            head_yaw=hyaw, head_pitch=hpit,
            tail_pitch=-4.0 - g1 * 1.0, tail_yaw=g1 * 1.5, tail_roll=-g1 * 0.8,
            tail_spread=0.58,
        )
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=-12.0)
        _set_eyes(p, brow_deg=brow, pupil_z=hyaw / 25.0, blink=_blink_pulse(t, (1.65,)))
        for s, side in ((1, 'left'), (-1, 'right')):
            g = g1 if s > 0 else g2
            w_roll = 7.5 + g * 2.2
            w_tip = 8.0 + g * 2.6
            _apply_spread_wing(
                p, s, side,
                sh_roll=w_roll, sh_yaw=-2.5, sh_pitch=3.0,
                el_roll=w_roll * 0.25, el_yaw=-2.0,
                ha_roll=w_tip * 0.45, ha_pitch=2.0,
                tip_roll=w_tip * 0.85, tip_yaw=4.0,
                alula_pitch=4.0, alula_roll=3.0,
            )
        return p

    return clip(L, pose, steps=40, loop=True)


def make_bank(direction):
    """1.6 s graceful thermal banking turn ('left' = -18 deg, 'right' = +18 deg) with gimbal-leveled head & tail rudder!"""
    L = 1.6
    bank_deg = -18.0 if direction == 'left' else 18.0

    def pose(t):
        p = {}
        th = 2.0 * math.pi * (t / L)
        g1 = math.sin(th) * 0.70 + math.sin(th * 2.0 + 0.6) * 0.30
        g2 = math.sin(th + 1.2) * 0.70 + math.sin(th * 2.0 + 1.9) * 0.30
        b_norm = bank_deg / 35.0
        look_into = 14.0 if direction == 'left' else -14.0
        _set_flight_base(
            p, body_pitch_extra=0.5, body_roll=bank_deg + g1 * 1.2,
            body_y=math.sin(th) * 0.7,
            head_yaw=look_into, head_pitch=8.0,
            tail_pitch=-5.5, tail_yaw=b_norm * 10.0, tail_roll=-bank_deg * 0.50,
            tail_spread=0.72,
        )
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=-14.0)
        _set_eyes(p, brow_deg=8.0, pupil_z=0.35 if direction == 'left' else -0.35)
        for s, side in ((1, 'left'), (-1, 'right')):
            g = g1 if s > 0 else g2
            w_roll = 8.0 + g * 1.8 - s * b_norm * 3.5
            w_tip = 8.0 + g * 2.2
            _apply_spread_wing(
                p, s, side,
                sh_roll=w_roll, sh_yaw=-3.5, sh_pitch=3.5,
                el_roll=w_roll * 0.25, el_yaw=-2.5,
                ha_roll=w_tip * 0.45, ha_pitch=2.5,
                tip_roll=w_tip * 0.85, tip_yaw=5.0,
                alula_pitch=6.0, alula_roll=4.0,
            )
        return p

    return clip(L, pose, steps=24, loop=True)


def make_stoop_dive():
    """1.4 s high-speed hunting stoop: nose-down +20 deg, wings swept into crisp M-delta (ZERO midline crossing!)."""
    L = 1.4

    def pose(t):
        p = {}
        th = 2.0 * math.pi * (t / L)
        trem = math.sin(th * 2.0) * 1.2
        _set_flight_base(
            p, body_pitch_extra=20.0 + trem * 0.4, body_roll=math.sin(th) * 2.0,
            head_pitch=-6.0, leg_tuck=1.02, toe_clench=1.0,
            tail_pitch=-2.0, tail_spread=-0.08,
        )
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=-20.0)
        _set_eyes(p, brow_deg=14.0, pupil_z=0.55)
        for s, side in ((1, 'left'), (-1, 'right')):
            _apply_spread_wing(
                p, s, side,
                sh_roll=12.0 + trem, sh_yaw=-18.0, sh_pitch=-6.0,
                el_roll=-6.0, el_yaw=-14.0,
                ha_roll=-6.0 + trem * 0.6, ha_yaw=-10.0, ha_pitch=-4.0,
                tip_roll=-4.0 + trem, tip_yaw=-6.0,
                alula_pitch=0.0, alula_roll=0.0,
            )
        return p

    return clip(L, pose, steps=24, loop=True)


def make_stoop_strike():
    """1.1 s terminal prey snatch: starts flush from stoop_dive, flares wings & shoots talons wide forward to snatch!"""
    L = 1.1

    def pose(t):
        p = {}
        u = _clamp(t / L, 0.0, 1.0)
        # Starts cleanly from exact stoop_dive angles (0.0 px switch jump!) -> flares & snatches -> pulls into fly_flap!
        thrust = bell(u, 0.06, 0.46, 0.84)
        clench = _smoothstep(0.46, 0.58, u) * (1.0 - _smoothstep(0.75, 0.98, u))
        flare = bell(u, 0.08, 0.50, 0.88)
        f_out = _smoothstep(0.0, 0.35, u)
        pitch_ex = _lerp(20.0, -22.0, _smoothstep(0.0, 0.48, u)) + _lerp(0.0, 22.0, _smoothstep(0.55, 1.0, u))
        leg_tuck = _lerp(1.02, 1.0, f_out) - thrust * 1.48  # +78 deg tucked -> -37 deg shot forward!
        toe_open = 1.0 - thrust * 1.15 + clench * 1.35

        _set_flight_base(
            p, body_pitch_extra=pitch_ex, body_y=2.2 * flare,
            head_pitch=_lerp(-6.0, 14.0, thrust), leg_tuck=leg_tuck, toe_clench=_clamp(toe_open, -0.35, 1.15),
            tail_pitch=_lerp(-2.0, 22.0, flare), tail_spread=_lerp(-0.08, 0.88, flare),
        )
        _set(p, 'jaw', rot=[18.0 * bell(u, 0.25, 0.48, 0.72), 0.0, 0.0])
        _set_crest(p, raise_delta=_lerp(-20.0, 16.0, flare))
        _set_eyes(p, brow_deg=14.0, pupil_z=0.55)
        for s, side in ((1, 'left'), (-1, 'right')):
            _apply_spread_wing(
                p, s, side,
                sh_roll=_lerp(12.0, 32.0, flare), sh_yaw=_lerp(-18.0, 14.0, flare), sh_pitch=_lerp(-6.0, 20.0, flare),
                el_roll=_lerp(-6.0, -4.0, flare), el_yaw=_lerp(-14.0, 0.0, flare),
                ha_roll=_lerp(-6.0, 12.0, flare), ha_yaw=_lerp(-10.0, 0.0, flare), ha_pitch=_lerp(-4.0, 16.0, flare),
                tip_roll=_lerp(-4.0, 14.0, flare), tip_yaw=_lerp(-6.0, 10.0, flare),
                alula_pitch=22.0 * flare, alula_roll=12.0 * flare,
            )
        return p

    return clip(L, pose, steps=30, loop=False)


def make_takeoff():
    """1.2 s explosive launch: crouched perch coil -> upward leap & wing pop (fold 1->0, spread 0->1) -> power beats!"""
    L = 1.2

    def pose(t):
        p = {}
        u = t / L
        if u < 0.26:
            # Phase 1: Athletic crouch coil on perch/ground (folded wings active, feet planted cleanly!)
            c = ease(u / 0.26)
            hip_y = -2.8 * c
            d_hip, d_knee, d_foot = _solve_bird_leg(0.0, 0.0, hip_y_shift=hip_y)
            _wings_visibility(p, fold_s=1.0, spread_s=0.0)
            _set(p, 'root', pos=[0.0, hip_y, 0.0])
            _set(p, 'body', rot=[22.0 * c, 0.0, 0.0])
            _set(p, 'neck_1', rot=[-6.0 * c, 0.0, 0.0])
            _set(p, 'neck_2', rot=[-8.0 * c, 0.0, 0.0])
            _set(p, 'head',   rot=[-8.0 * c, 0.0, 0.0])
            _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
            _set_crest(p, raise_delta=-8.0 * c)
            _set_eyes(p, brow_deg=10.0 * c)
            _set_tail(p, pitch=-8.0 * c, spread=0.25 * c)
            for s, side in ((1, 'left'), (-1, 'right')):
                _set(p, f'leg_upper_{side}', rot=[d_hip, 0.0, 0.0])
                _set(p, f'leg_lower_{side}', rot=[d_knee, 0.0, 0.0])
                _set(p, f'leg_foot_{side}',  rot=[d_foot, 0.0, 0.0])
                _set(p, f'wing_fold_{side}', rot=[-8.0 * c, s * 6.0 * c, -s * (18.0 * c)])
            return p

        # Phase 2 & 3 (u >= 0.26): Dual-wing pop & powerful accelerating launch downstrokes -> seamless into fly_flap(0)!
        f_air = _smoothstep(0.26, 0.44, u)
        # Fast 2-tick clean cross-scale right as the eagle springs upward
        pop = _smoothstep(0.26, 0.34, u)
        fold_s = 1.0 - pop
        spread_s = pop

        # Blend smoothly from launch stroke into exact _fly_flap_pose(0.0) at u=1.0!
        f_end = _smoothstep(0.42, 1.0, u)
        ph_beat = (u - 0.30) / 0.70  # 1 full powerful launch cycle ending at ph=1.0 == fly_flap(0.0)!
        wc = wing_cycle(ph_beat, flap_amp=42.0 - 6.0 * f_end, up_fold=28.0, wrist_lead=0.14,
                        feather_twist=20.0, asymmetry=0.44, body_heave=2.8, tip_flex=16.0)

        tuck = _smoothstep(0.32, 0.88, u)
        _set_flight_base(
            p,
            body_pitch_extra=_lerp(-24.0, wc['body_pitch'] * 0.65, f_air),
            body_y=_lerp(-1.2, wc['body_y'], f_air),
            leg_tuck=tuck, toe_clench=tuck,
            tail_pitch=-wc['body_pitch'] * 0.55 - 2.0,
            tail_spread=_lerp(0.60, 0.35 + wc['power'] * 0.20, f_end),
        )
        _wings_visibility(p, fold_s=fold_s, spread_s=spread_s)
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=-10.0)
        _set_eyes(p, brow_deg=8.0)
        for s, side in ((1, 'left'), (-1, 'right')):
            _apply_spread_wing(
                p, s, side,
                sh_roll=wc['shoulder_roll'], sh_yaw=wc['shoulder_yaw'], sh_pitch=wc['shoulder_pitch'],
                el_roll=wc['elbow_roll'], el_yaw=-wc['elbow_fold'] * 0.85,
                ha_roll=wc['wrist_flex'], ha_yaw=wc['elbow_fold'] * 0.30, ha_pitch=wc['wrist_twist'],
                tip_roll=wc['wrist_flex'] * 0.55 + wc['power'] * 8.0, tip_yaw=wc['primary_splay'] * 6.0,
                alula_pitch=wc['power'] * 14.0, alula_roll=wc['power'] * 8.0,
            )
        return p

    return clip(L, pose, steps=36, loop=False)


def make_land_flare():
    """1.2 s dramatic aerodynamic braking flare -> cushioned touchdown -> clean wing fold -> regal perched settle!"""
    L = 1.2

    def pose(t):
        p = {}
        u = t / L
        if u < 0.60:
            # Phase 1: Full aerodynamic braking flare (body upright, alula raised, wide tail fan, talons thrust forward!)
            f = ease(u / 0.60)
            flare = math.sin(math.pi * min(1.0, u / 0.56))
            # Transition smoothly from horizontal flight (+48 delta) -> upright perch (0 delta)
            body_ex = _lerp(0.0, -PERCH_BODY_PITCH + 4.0, f)
            leg_tuck = _lerp(1.0, -0.42, f)
            toe_cl = _lerp(1.0, -0.22, f)
            _set_flight_base(
                p, body_pitch_extra=body_ex, body_y=_lerp(2.4, 0.0, f),
                head_pitch=6.0 * flare, leg_tuck=leg_tuck, toe_clench=toe_cl,
                tail_pitch=24.0 * flare, tail_spread=0.85 * flare + 0.15,
            )
            # Begin fast clean wing-fold cross-scale right as talons touch perch (u=0.52..0.60)
            fold_t = _smoothstep(0.52, 0.60, u)
            _wings_visibility(p, fold_s=fold_t, spread_s=1.0 - fold_t)
            _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
            _set_crest(p, raise_delta=12.0 * flare)
            _set_eyes(p, brow_deg=8.0 * flare)
            for s, side in ((1, 'left'), (-1, 'right')):
                _apply_spread_wing(
                    p, s, side,
                    sh_roll=34.0 * flare + 14.0 * fold_t, sh_yaw=16.0 * flare, sh_pitch=28.0 * flare,
                    el_roll=-8.0 * flare, el_yaw=-20.0 * fold_t,
                    ha_roll=14.0 * flare, ha_pitch=20.0 * flare,
                    tip_roll=16.0 * flare, tip_yaw=10.0 * flare,
                    alula_pitch=24.0 * flare, alula_roll=12.0 * flare,
                )
                _set(p, f'wing_fold_{side}', rot=[-8.0 * (1.0 - fold_t), 0.0, -s * (16.0 * (1.0 - fold_t))])
            return p

        # Phase 2 (u >= 0.60): Cushioned knee absorb on touchdown -> regal perched settle matching perch_idle(0)!
        f2 = (u - 0.60) / 0.40
        absorb = math.sin(math.pi * min(1.0, f2 * 1.35)) * (1.0 - f2)
        hip_y = -2.2 * absorb
        d_hip, d_knee, d_foot = _solve_bird_leg(0.0, 0.0, hip_y_shift=hip_y)
        _wings_visibility(p, fold_s=1.0, spread_s=0.0)
        _set_perch_grip(p, curl=14.0 * ease(f2))
        _set(p, 'root', pos=[0.0, hip_y, 0.0])
        _set(p, 'body', rot=[6.0 * absorb, 0.0, 0.0])
        _set(p, 'neck_1', rot=[-2.0 * absorb, 0.0, 0.0])
        _set(p, 'neck_2', rot=[-2.0 * absorb, 0.0, 0.0])
        _set(p, 'head',   rot=[-2.0 * absorb, 0.0, 0.0])
        _set(p, 'jaw', rot=[0.0, 0.0, 0.0])
        _set_crest(p, raise_delta=10.0 * absorb)
        _set_eyes(p, brow_deg=2.0, blink=_blink_pulse(t, (1.02,)))
        _set_tail(p, pitch=-8.0 * absorb, spread=0.35 * (1.0 - ease(f2)))
        for s, side in ((1, 'left'), (-1, 'right')):
            _set(p, f'leg_upper_{side}', rot=[d_hip, 0.0, 0.0])
            _set(p, f'leg_lower_{side}', rot=[d_knee, 0.0, 0.0])
            _set(p, f'leg_foot_{side}',  rot=[d_foot, 0.0, 0.0])
            _set(p, f'wing_fold_{side}', rot=[-4.0 * absorb, 0.0, -s * (10.0 * absorb)])
        return p

    return clip(L, pose, steps=36, loop=False)


# ======================================================================================================================
# BODY PLAN (Flier Family + Motion Studio Phase 2 Gaits)
# ======================================================================================================================
def build_body_plan(bones):
    """Builds the authoritative body_plans/martial_eagle.json matching body_plan.py + Motion Studio Phase 2."""
    bone_map = {b['name']: b['name'] for b in bones}
    return {
        '_note': 'Martial Eagle (Polemaetus bellicosus) — flier family + Motion Studio Phase 2 gaits, exported by tools/martial_eagle_anims.py',
        'model': 'martial_eagle',
        'family': 'flier',
        'root': 'root',
        'forebody': 'body',
        'chain': ['head', 'neck_2', 'neck_1', 'body', 'tail_base', 'tail_c'],
        'legs': {
            'l1': ['leg_upper_left', 'leg_lower_left', 'leg_tarsus_left', 'leg_foot_left'],
            'r1': ['leg_upper_right', 'leg_lower_right', 'leg_tarsus_right', 'leg_foot_right'],
        },
        'wings': {
            'l': ['wing_upper_left', 'wing_fore_left', 'wing_hand_left', 'wing_tips_left'],
            'r': ['wing_upper_right', 'wing_fore_right', 'wing_hand_right', 'wing_tips_right'],
        },
        'head': {
            'bone': 'head',
            'jaws': ['jaw'],
            'eyes': ['eye_left', 'eye_right'],
            'pupils': ['pupil_left', 'pupil_right'],
            'lids': ['ulid_left', 'lid_left', 'ulid_right', 'lid_right'],
            'brows': ['brow_left', 'brow_right'],
            'crest': ['crest_c', 'crest_left', 'crest_right'],
        },
        'head_player': [
            'head', 'jaw', 'tongue', 'crest_c', 'crest_left', 'crest_right',
            'brow_left', 'brow_right', 'eye_left', 'eye_right',
            'pupil_left', 'pupil_right', 'ulid_left', 'ulid_right', 'lid_left', 'lid_right',
        ],
        # Motion Studio Phase 2 single source of truth for ground locomotion gaits
        # (clip_check.py martial_eagle --gait-changes --write populates exact `change` moments!)
        'gaits': {
            'order': ['walk', 'hop'],
            'walk': {'from': 0.0, 'min': 0.5, 'max': 1.6},
            'hop':  {'from': 21.0, 'min': 0.6, 'max': 1.8},
        },
        'bones': bone_map,
        'checks': {
            'px': round(me.MODEL_SCALE / 16.0, 6),  # 0.03125 blocks/px (MODEL_SCALE = 0.50)
            'blend': 8,  # Smooth 0.40s large-raptor SteadyController blend for graceful 3.32b wing transitions!
            'air': [
                'takeoff', 'fly_flap', 'soar_glide', 'bank_left', 'bank_right',
                'stoop_dive', 'stoop_strike', 'land_flare',
            ],
            'perch': ['perch_*'],
            'perch_bar': {'y': 0.0, 'r': 1.8, 'z': -1.6},
            'wing_wave': ['fly_flap'],
            'wing_tuck_ok': [
                'perch_*', 'idle', 'walk', 'hop', 'eat_tear', 'death', 'takeoff', 'land_flare',
            ],
            'tuck_ok': [
                'fly_flap', 'soar_glide', 'bank_left', 'bank_right',
                'stoop_dive', 'stoop_strike', 'takeoff', 'land_flare', 'death',
            ],
            'feet_free': ['hop', 'death'],
            'feet_parts': ['foot', 'toe', 'talon', 'hallux'],
            'body_bones': ['body', 'neck_1', 'neck_2'],
            'hide_in': ['head', 'jaw'],
            'hidden_at_rest': ['tongue'],
            'jaw': {'bone': 'jaw', 'flag_below': -0.5},
            'walks': {
                'walk': round(WALK_SPEED_PX_S, 3),
                'hop':  round(HOP_SPEED_PX_S, 3),
            },
            'switches': [
                ['perch_idle', 'perch_alert'],
                ['perch_alert', 'perch_idle'],
                ['perch_idle', 'perch_call'],
                ['perch_call', 'perch_idle'],
                ['perch_idle', 'perch_preen'],
                ['perch_preen', 'perch_idle'],
                ['perch_idle', 'perch_rouse'],
                ['perch_rouse', 'perch_idle'],
                ['perch_idle', 'takeoff'],
                ['perch_alert', 'takeoff'],
                ['idle', 'takeoff'],
                ['takeoff', 'fly_flap'],
                ['fly_flap', 'soar_glide'],
                ['soar_glide', 'fly_flap'],
                ['soar_glide', 'bank_left'],
                ['bank_left', 'soar_glide'],
                ['soar_glide', 'bank_right'],
                ['bank_right', 'soar_glide'],
                ['soar_glide', 'stoop_dive'],
                ['stoop_dive', 'stoop_strike'],
                ['stoop_strike', 'fly_flap'],
                ['soar_glide', 'land_flare'],
                ['fly_flap', 'land_flare'],
                ['land_flare', 'perch_idle'],
                ['land_flare', 'idle'],
                ['idle', 'walk'],
                ['walk', 'idle'],
                ['idle', 'hop'],
                ['hop', 'idle'],
                ['idle', 'eat_tear'],
                ['eat_tear', 'idle'],
                ['idle', 'death'],
            ],
        },
        'verify': {
            'compare': {
                'version': 'v9',
            },
        },
    }


def build_all_clips():
    """Builds all 18 intelligent Martial Eagle clips."""
    return {
        'perch_idle':   make_perch_idle(),
        'perch_alert':  make_perch_alert(),
        'perch_call':   make_perch_call(),
        'perch_preen':  make_perch_preen(),
        'perch_rouse':  make_perch_rouse(),
        'idle':         make_idle(),
        'walk':         make_walk(),
        'hop':          make_hop(),
        'eat_tear':     make_eat_tear(),
        'takeoff':      make_takeoff(),
        'fly_flap':     make_fly_flap(),
        'soar_glide':   make_soar_glide(),
        'bank_left':    make_bank('left'),
        'bank_right':   make_bank('right'),
        'stoop_dive':   make_stoop_dive(),
        'stoop_strike': make_stoop_strike(),
        'land_flare':   make_land_flare(),
        'death':        make_death(),
    }


def register_plan_index(name='martial_eagle', mod='savanna'):
    idx_path = Path(body_plan.index_path(mod))
    data = json.loads(idx_path.read_text(encoding='utf-8'))
    if name not in data['plans']:
        data['plans'].append(name)
        idx_path.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8', newline='\n')
        print(f'registered {name} in {idx_path.relative_to(HERE.parent)}')


def export_all():
    """Exports painted game geo (faces=True), 512x512 texture PNG, 18-clip animation JSON, and body plan."""
    bones, png_bytes, tw, th = me.make_look('painted', mode='game', head_style='C', beak_style='1')
    clips = build_all_clips()

    # Visible bounds: generous soaring wingspan (3.4 blocks wide, 2.0 tall) so frustum culling never pops wings!
    geo_data = geo('martial_eagle', bones, tw, th, (4.0, 2.4, [0.0, 0.9, 0.0]))
    anim_data = animations(clips)

    write_assets({
        'geo/entity/martial_eagle.geo.json': geo_data,
        'textures/entity/martial_eagle.png': png_bytes,
        'animations/entity/martial_eagle.animation.json': anim_data,
    }, mod='savanna', faces=True)

    plan = build_body_plan(bones)
    body_plan.write_plan('martial_eagle', plan)
    register_plan_index('martial_eagle', mod='savanna')
    print(f'Exported martial_eagle: {len(bones)} bones, {sum(len(b.get("cubes", [])) for b in bones)}/400 boxes, '
          f'{tw}x{th} texture, {len(clips)} clips')


if __name__ == '__main__':
    export_all()
