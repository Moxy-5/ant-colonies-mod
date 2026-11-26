"""Martial Eagle (Polemaetus bellicosus) model maker — Stage 3 Shape Detail (v6, 2026-11-25).

First savanna bird & first true Flier (antcolonies:martial_eagle, flier family).
Working model grid at MODEL_SCALE = 0.50 in-game (32 model px = 1 real block):
  - Perched standing height: ~1.25 blocks (40.0 px)
  - Perched shoulder width:  ~0.62 blocks (19.8 px)
  - Soaring wingspan:        ~3.32 blocks (106.2 px wingtip-to-wingtip)
  - Soaring body length:     ~1.55 blocks (49.6 px beak-to-tail)

Dual-Wing Architecture (Approved Stage 2, Enhanced Stage 3 Detail):
  - Dedicated sculpted folded wing capes (wing_fold_left/right + wing_fold_tip_left/right)
    with overlapping carpal shoulder scallops, folded alula tuft, terraced covert shingles,
    and 4 staggered tapered primary tips resting sleekly over the tail.
  - Dedicated sculpted spread soaring wings (wing_upper -> wing_fore -> wing_hand -> wing_tips + alula)
    with scapular wing-root fairings, cambered airfoil under-coverts, 3-feather fan alula,
    gap-free overlapping secondaries, and 7 fine-tapered slotted eagle primary tips.
  - Sculpted raptor head with 2-tier occipital crest, double-bevelled predator brows, recessed
    orbital eye-rings, feathered cheek ear-coverts, nape hackles, V-scalloped breast bib,
    layered upper/under tail coverts, flared feathered eagle trousers, and scaly tarsus/toe pads.

    python tools/martial_eagle_models.py
    python tools/workshop.py snapshot martial_eagle --note "..."
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (  # noqa: E402
    cube, rot_cube, rounded, mix, shade, hash3, paint_faces, png, geo, write_assets,
    feather_grain, feather_fan, bird_beak, bird_eye, bird_talon_foot,
    euler, mat_mul, mat_vec, vadd, vsub,
)

# --- Workshop sliders (main shape numbers for tools/workshop.py serve martial_eagle) ----------------------------------
MODEL_SCALE = 0.50        # 32 model px = 1 real in-game block (px = 0.03125 blocks)
BODY_PITCH = 48.0         # Perched upright torso tilt (deg; 0 = horizontal soaring flight)
CHEST_HW = 6.5            # Torso ribcage half-width (px)
WING_SPAN_SCALE = 1.00    # Multiplier on soaring wing span (1.00 -> ~106 px = 3.31 blocks)
CREST_RAISE = 0.38        # 0.0 = sleek streamlined dive, 1.0 = fully raised alert occipital crest
TAIL_SPREAD = 0.15        # 0.0 = narrow perched wedge, 1.0 = wide braking rudder fan
BEAK_LEN = 4.4            # Compact powerful hooked raptor beak length (px)
TALON_SCALE = 1.00        # Scale on curved gripping talons
JAW_OPEN = 0.0            # Preview-only beak opening (deg)
HEAD_STYLE = 'C'          # Locked Stage 3 user choice: 'C' (Bold Heavy hooded eyes)
BEAK_STYLE = '1'          # Stage 4 beak variation: '1' (Natural Compact ★), '2' (Deep Curved Scimitar), '3' (Chunky Classic)

WORKSHOP_SHAPE = [
    ("BODY_PITCH",      "Perched torso pitch (deg)",    38.0, 54.0, 1.0),
    ("CHEST_HW",        "Torso half-width (px)",         4.8,  7.0, 0.2),
    ("WING_SPAN_SCALE", "Soaring wingspan scale",       0.85, 1.15, 0.02),
    ("CREST_RAISE",     "Occipital crest raise",        0.00, 1.00, 0.05),
    ("TAIL_SPREAD",     "Tail fan spread",              0.00, 1.00, 0.05),
    ("BEAK_LEN",        "Hooked beak length (px)",       4.2,  6.4, 0.2),
    ("TALON_SCALE",     "Talon length scale",           0.80, 1.25, 0.05),
    ("JAW_OPEN",        "Preview beak open (deg)",       0.0, 38.0, 2.0),
]

WORKSHOP_PARTS = {
    "head":         ["head", "brow_left", "brow_right", "crest_c", "crest_left", "crest_right",
                     "eye_left", "eye_right", "lid_left", "lid_right", "ulid_left", "ulid_right",
                     "pupil_left", "pupil_right", "jaw", "tongue"],
    "neck":         ["neck_1", "neck_2"],
    "body":         ["body"],
    "folded wings": ["wing_fold_left", "wing_fold_tip_left", "wing_fold_right", "wing_fold_tip_right"],
    "spread wings": ["wing_upper_left", "wing_fore_left", "wing_hand_left", "wing_tips_left", "alula_left",
                     "wing_upper_right", "wing_fore_right", "wing_hand_right", "wing_tips_right", "alula_right"],
    "tail":         ["tail_base", "tail_c", "tail_left", "tail_right"],
    "legs":         ["leg_upper_left", "leg_lower_left", "leg_tarsus_left", "leg_foot_left",
                     "leg_upper_right", "leg_lower_right", "leg_tarsus_right", "leg_foot_right"],
}

WORKSHOP_LOOK = "painted"  # Stage 4 Paint ("painted", "plain", "clay")
TEXELS = 2.0


def _lerp(a, b, t):
    return a + (b - a) * t


def _rounded_x(cubes, x0, x1, cy, cz, ry, rz, part):
    """Smooth 8-sided rounded cross-section extruding along X (used for aerodynamic leading-edge wing spars)."""
    cubes.append(cube([x0, cy - ry * 0.55, cz - rz], [x1, cy + ry * 0.55, cz + rz], part))
    cubes.append(cube([x0, cy - ry, cz - rz * 0.55], [x1, cy + ry, cz + rz * 0.55], part))


def _rounded_y(cubes, cx, y0, y1, rx, rz, cz, part):
    """Smooth 8-sided rounded vertical column (used for muscular feathered eagle thighs & knee cuffs!)."""
    if min(rx, rz) < 0.85:
        cubes.append(cube([cx - rx, y0, cz - rz], [cx + rx, y1, cz + rz], part))
        return
    cubes.append(cube([cx - rx,        y0, cz - rz * 0.55], [cx + rx,        y1, cz + rz * 0.55], part))
    cubes.append(cube([cx - rx * 0.55, y0, cz - rz],        [cx + rx * 0.55, y1, cz + rz],        part))
    cubes.append(cube([cx - rx * 0.84, y0, cz - rz * 0.84], [cx + rx * 0.84, y1, cz + rz * 0.84], part))


def _build_folded_wing(s, side, by, bz, hw):
    """Dedicated sculpted teardrop folded wing cape for perched & ground poses (10/10 sleek raptor silhouette!).
    Authored cleanly in body-local space (+X left, +Y dorsal back, -Z chest/front, +Z tail/back) so when body
    pitches up 46 deg at rest, the wing hugs the flanks smoothly and sweeps diagonally down-back along the rump."""
    # Shoulder joint on upper side of chest/back
    p_wf = [s * (hw * 0.84), by + 1.4, bz - 5.2]
    cubes = []

    def add_panel(x_in, x_out, y0, y1, z0, z1, part, rot_deg=None, piv=None):
        lo = [min(s * x_in, s * x_out), y0, z0]
        hi = [max(s * x_in, s * x_out), y1, z1]
        if rot_deg:
            p = piv if piv is not None else [(lo[0] + hi[0]) * 0.5, (lo[1] + hi[1]) * 0.5, (lo[2] + hi[2]) * 0.5]
            cubes.append(rot_cube(lo, hi, part, rotation=rot_deg, pivot=p))
        else:
            cubes.append(cube(lo, hi, part))

    # 1. Rounded muscular carpal shoulder bend (the proud forward-outer shoulder curve seen in front & 3/4 views!)
    add_panel(hw * 0.60, hw * 1.32, by - 3.0, by + 5.6, bz - 8.8, bz - 2.0, 'wing_covert',
              rot_deg=[5.0, s * -6.0, s * -7.0])
    # Upper shoulder cap rounding cleanly into the dark dorsal mantle (strictly s*X >= 0.25 -> zero midline cross!)
    add_panel(1.05, hw * 1.12, by + 3.2, by + 6.9, bz - 8.0, bz - 0.2, 'wing_covert',
              rot_deg=[4.0, s * -3.0, s * -16.0])
    # Front-lower carpal edge rounding smoothly toward the white chest (tapered, NOT a flat box wall!)
    add_panel(hw * 0.70, hw * 1.24, by - 4.8, by - 1.4, bz - 7.4, bz - 1.0, 'wing_covert',
              rot_deg=[8.0, s * -7.0, s * 10.0])
    # Stage 3: Carpal lesser-covert shoulder scallop tiers + nested folded alula tuft at wrist bend!
    add_panel(hw * 0.88, hw * 1.39, by + 0.4, by + 4.4, bz - 7.8, bz - 2.6, 'wing_covert_hi',
              rot_deg=[4.0, s * -7.0, s * -8.0])
    add_panel(hw * 0.94, hw * 1.36, by - 3.4, by + 0.6, bz - 6.8, bz - 1.4, 'wing_covert',
              rot_deg=[6.0, s * -5.0, s * 6.0])
    # Nested folded alula feather tuft hugging the leading lower edge of the folded wrist
    add_panel(hw * 0.88, hw * 1.24, by - 4.9, by - 2.6, bz - 4.8, bz + 1.6, 'wing_primary',
              rot_deg=[-5.0, s * 4.0, s * 14.0])

    # 2. Upper & median covert armor shell (broadest curved mid-flank contour, tapering inward toward rump!)
    add_panel(hw * 0.56, hw * 1.38, by - 3.4, by + 5.9, bz - 3.6, bz + 4.2, 'wing_covert',
              rot_deg=[-3.0, s * 6.0, s * -6.0])
    # Scapular / dorsal cape shingle bridging neatly onto the back spine (strictly on side s!)
    add_panel(1.15, hw * 0.98, by + 3.4, by + 7.2, bz - 2.0, bz + 5.8, 'wing_covert',
              rot_deg=[-5.0, s * 7.0, s * -18.0])
    # Stage 3: Sculpted terraced median-covert plumage shingles curving down the outer wing flank!
    add_panel(hw * 0.98, hw * 1.44, by + 0.8, by + 4.9, bz - 2.4, bz + 3.6, 'wing_covert_hi',
              rot_deg=[-4.0, s * 7.0, s * -7.0])
    add_panel(hw * 0.94, hw * 1.42, by - 2.6, by + 1.4, bz - 1.6, bz + 4.4, 'wing_covert',
              rot_deg=[-4.0, s * 8.0, s * 3.0])

    # 3. Terraced greater coverts shingle band (gives that crisp layered eagle wing plumage step!)
    add_panel(hw * 0.52, hw * 1.30, by - 4.0, by + 4.8, bz + 2.0, bz + 9.0, 'wing_covert_hi',
              rot_deg=[-6.0, s * 10.0, s * -5.0])
    # Lower flank belly-hugging feather edge (smoothly sweeps down and back along the white thigh top!)
    add_panel(hw * 0.62, hw * 1.18, by - 5.4, by - 1.8, bz - 1.8, bz + 6.6, 'wing_covert_hi',
              rot_deg=[-4.0, s * 8.0, s * 12.0])
    # Stage 3: Outer sculpted greater-covert ridge & tertial dorsal overlap shingle
    add_panel(hw * 0.92, hw * 1.35, by - 1.4, by + 3.8, bz + 2.8, bz + 8.6, 'wing_covert',
              rot_deg=[-6.0, s * 11.0, s * -6.0])
    add_panel(1.10, hw * 0.90, by + 2.8, by + 6.0, bz + 3.2, bz + 10.6, 'wing_covert_hi',
              rot_deg=[-7.0, s * 11.0, s * -14.0])

    # 4. Sculpted broad folded secondaries & tertials curving smoothly over the lower back & hips
    add_panel(1.00, hw * 1.12, by - 1.4, by + 5.2, bz + 6.0, bz + 14.2, 'wing_flight',
              rot_deg=[-8.0, s * 13.0, s * -10.0])
    add_panel(hw * 0.48, hw * 1.12, by - 4.0, by + 1.8, bz + 5.4, bz + 13.4, 'wing_flight',
              rot_deg=[-7.0, s * 12.0, s * 5.0])
    # Stage 3: Individual overlapping outer secondary feather steps hugging the lower flank
    add_panel(hw * 0.78, hw * 1.18, by - 2.6, by + 2.4, bz + 6.8, bz + 14.0, 'wing_flight',
              rot_deg=[-8.0, s * 13.0, s * -2.0])
    add_panel(hw * 0.68, hw * 1.08, by - 4.2, by - 0.8, bz + 6.6, bz + 14.8, 'wing_primary',
              rot_deg=[-7.0, s * 12.0, s * 9.0])

    fold_bone = {
        'name': f'wing_fold_{side}', 'parent': 'body', 'pivot': p_wf,
        'rotation': [0.0, 0.0, 0.0], 'cubes': cubes,
    }

    # 5. Articulated folded primary wingtip spear — Stage 3: 5 staggered tapered overlapping primary tips!
    p_wt = [s * (hw * 0.66), by - 0.2, bz + 12.2]
    tip_cubes = []

    def add_tip(x_in, x_out, y0, y1, z0, z1, part, rot_deg=None):
        lo = [min(s * x_in, s * x_out), y0, z0]
        hi = [max(s * x_in, s * x_out), y1, z1]
        p = [(lo[0] + hi[0]) * 0.5, (lo[1] + hi[1]) * 0.5, (lo[2] + hi[2]) * 0.5]
        tip_cubes.append(rot_cube(lo, hi, part, rotation=rot_deg, pivot=p) if rot_deg else cube(lo, hi, part))

    # Stacked dark primary outer web tapering cleanly in graduated feather tiers to a sharp eagle spear tip!
    add_tip(hw * 0.34, hw * 0.90, by - 2.6, by + 3.0, bz + 11.4, bz + 19.2, 'wing_primary',
            rot_deg=[-6.0, s * 12.0, s * -5.0])
    add_tip(hw * 0.52, hw * 0.92, by - 3.0, by + 0.6, bz + 12.4, bz + 19.8, 'wing_primary',
            rot_deg=[-6.0, s * 11.0, s * 4.0])
    add_tip(hw * 0.18, hw * 0.68, by - 1.8, by + 2.2, bz + 17.2, bz + 23.8, 'wing_primary',
            rot_deg=[-4.0, s * 10.0, s * -4.0])
    # Staggered outer P9/P8 pointed spears stepping smoothly back along the upper tail
    add_tip(hw * 0.22, hw * 0.60, by - 2.2, by + 0.8, bz + 18.4, bz + 25.0, 'wing_tip',
            rot_deg=[-4.0, s * 9.0, s * 2.0])
    add_tip(0.55, hw * 0.48, by - 1.2, by + 1.3, bz + 22.0, bz + 27.4, 'wing_tip',
            rot_deg=[-3.0, s * 8.0, s * -2.0])
    add_tip(0.55, hw * 0.30, by - 0.9, by + 0.6, bz + 25.2, bz + 28.4, 'wing_tip',
            rot_deg=[-2.0, s * 6.0, s * -1.0])

    tip_bone = {
        'name': f'wing_fold_tip_{side}', 'parent': f'wing_fold_{side}', 'pivot': p_wt,
        'rotation': [0.0, 0.0, 0.0], 'cubes': tip_cubes,
    }
    return [fold_bone, tip_bone]


def _build_spread_wing(s, side, by, bz, ws):
    """Dedicated sculpted soaring airfoil wing (10/10 continuous eagle silhouette, 3.25 blocks wingspan!).
    Every joint angle is computed directly from exact joint vectors:
      - Smooth forward-arched leading edge (shoulder -> elbow -> wrist -> smoothly curved hand, ZERO bowtie kink!)
      - Continuous overlapping covert + secondary airfoil sheet (ZERO Venetian-blind gaps!)
      - Seamlessly fanned inner primaries bridging wrist to outer tips (ZERO triangle hole!)
      - 7 gracefully curved, staggered slotted eagle primary tips (broad overlapping bases -> notched aero tips!)."""
    wy = by + 2.1

    # Exact smooth avian wing spar waypoints (body-local space, +X left, -Z forward, +Z trailing):
    p_sh = [s * 4.8, wy, bz - 4.2]
    p_el = [s * (4.8 + 11.4 * ws), wy + 0.2, bz - 5.4]   # sweeps forward +6.0 deg
    p_wr = [s * (4.8 + 24.4 * ws), wy + 0.4, bz - 6.6]   # forward-most wrist bend (+5.3 deg)
    p_ha = [s * (4.8 + 33.4 * ws), wy + 0.2, bz - 5.0]   # smoothly sweeps back -10.1 deg (NO kink!)
    p_al = [s * (4.8 + 24.8 * ws), wy + 0.85, bz - 7.1]

    # Exact yaw for a +s*X spar bar from a to b (turn_y(d) maps (1,0,0)->(cos d, 0, -sin d))
    def bar_yaw(a, b):
        dx = abs(b[0] - a[0])
        dz = b[2] - a[2]
        return s * math.degrees(math.atan2(-dz, dx))

    yaw_up = bar_yaw(p_sh, p_el)   # ~ s * +6.0 deg (sweeps forward)
    yaw_fo = bar_yaw(p_el, p_wr)   # ~ s * +5.3 deg (continues smooth forward arch)
    yaw_ha = bar_yaw(p_wr, p_ha)   # ~ s * -10.1 deg (smoothly curves back!)

    # --- 1. wing_upper (humerus spar + scapular fairing + cambered covert dome + 4 wide overlapping tertials) ---
    up_cubes = []
    ux0, ux1 = sorted((p_sh[0] - s * 1.2, p_el[0] + s * 1.0))
    # Thick cambered leading-edge spar (rounded airfoil nose + Stage 3 patagium under-camber & scapular root fillet!)
    up_cubes.append(rot_cube([ux0, wy - 1.25, p_sh[2] - 2.1], [ux1, wy + 1.25, p_sh[2] + 2.3], 'wing_covert',
                             rotation=[0.0, yaw_up, 0.0], pivot=p_sh))
    up_cubes.append(rot_cube([ux0, wy - 0.65, p_sh[2] - 2.55], [ux1, wy + 0.65, p_sh[2] - 1.4], 'wing_covert',
                             rotation=[0.0, yaw_up, 0.0], pivot=p_sh))
    # Stage 3: Scapular wing-root fairing blending smoothly into the dark mantle + cambered upper/under covert rows!
    sx0, sx1 = sorted((p_sh[0] - s * 2.2, p_sh[0] + s * 5.6 * ws))
    up_cubes.append(rot_cube([sx0, wy + 0.35, p_sh[2] - 1.4], [sx1, wy + 1.75, p_sh[2] + 5.6], 'hood',
                             rotation=[4.0, yaw_up, s * 6.0], pivot=p_sh))
    up_cubes.append(rot_cube([ux0, wy - 1.50, p_sh[2] - 1.4], [ux1, wy - 0.55, p_sh[2] + 3.4], 'wing_covert',
                             rotation=[-2.0, yaw_up, 0.0], pivot=p_sh))
    # Overlapping upper & greater covert sheets (solid airfoil cambered dome)
    up_cubes.append(rot_cube([ux0, wy - 0.35, p_sh[2] + 1.4], [ux1, wy + 1.05, p_sh[2] + 6.6], 'wing_covert',
                             rotation=[3.5, yaw_up, 0.0], pivot=p_sh))
    up_cubes.append(rot_cube([ux0, wy - 0.52, p_sh[2] + 5.2], [ux1, wy + 0.55, p_sh[2] + 10.0], 'wing_covert_hi',
                             rotation=[5.0, yaw_up, 0.0], pivot=p_sh))
    # 4 wide overlapping tertials / inner flight feathers (width 4.1 on 2.85 spacing = 100% GAP-FREE continuous curve!)
    for ti in range(4):
        frac = ti / 3.0
        fx = _lerp(p_sh[0] + s * 1.4, p_el[0] - s * 0.6, frac)
        fz = _lerp(p_sh[2], p_el[2], frac) + 6.8
        tlen = 13.0 + math.sin(frac * math.pi * 0.85) * 2.2
        tw = 4.15
        eps = ((ti % 2) - 0.5) * 0.06
        up_cubes.append(rot_cube([fx - tw * 0.5, wy - 0.62 + eps, fz],
                                 [fx + tw * 0.5, wy + 0.08 + eps, fz + tlen], 'wing_flight',
                                 rotation=[5.5, s * (-4.0 + frac * 6.0), 0.0], pivot=[fx, wy, fz]))

    b_up = {'name': f'wing_upper_{side}', 'parent': 'body', 'pivot': p_sh,
            'rotation': [0.0, 0.0, s * -5.0], 'cubes': up_cubes}

    # --- 2. wing_fore (ulna leading arch + terraced coverts + 7 GAP-FREE overlapping broad secondaries!) ---
    fo_cubes = []
    fx0, fx1 = sorted((p_el[0] - s * 0.9, p_wr[0] + s * 1.1))
    # Cambered leading edge spar + rounded nose + Stage 3 underwing covert camber & median covert step!
    fo_cubes.append(rot_cube([fx0, wy - 1.10, p_el[2] - 1.95], [fx1, wy + 1.15, p_el[2] + 2.2], 'wing_covert',
                             rotation=[0.0, yaw_fo, 0.0], pivot=p_el))
    fo_cubes.append(rot_cube([fx0, wy - 0.58, p_el[2] - 2.40], [fx1, wy + 0.58, p_el[2] - 1.3], 'wing_covert',
                             rotation=[0.0, yaw_fo, 0.0], pivot=p_el))
    fo_cubes.append(rot_cube([fx0, wy - 1.32, p_el[2] - 1.2], [fx1, wy - 0.45, p_el[2] + 3.2], 'wing_covert',
                             rotation=[-2.0, yaw_fo, 0.0], pivot=p_el))
    # Median & greater covert sheets (3 crisp overlapping plumage terraces!)
    fo_cubes.append(rot_cube([fx0, wy + 0.15, p_el[2] + 0.4], [fx1, wy + 1.12, p_el[2] + 4.4], 'wing_covert',
                             rotation=[3.0, yaw_fo, 0.0], pivot=p_el))
    fo_cubes.append(rot_cube([fx0, wy - 0.25, p_el[2] + 2.6], [fx1, wy + 0.86, p_el[2] + 6.9], 'wing_covert',
                             rotation=[3.8, yaw_fo, 0.0], pivot=p_el))
    fo_cubes.append(rot_cube([fx0, wy - 0.45, p_el[2] + 5.2], [fx1, wy + 0.52, p_el[2] + 9.9], 'wing_covert_hi',
                             rotation=[5.0, yaw_fo, 0.0], pivot=p_el))
    # 7 broad overlapping secondaries (width 3.55 px on 1.95 px spacing -> smooth scalloped continuous trailing edge!)
    for si in range(7):
        frac = si / 6.0
        sx = _lerp(p_el[0] + s * 0.8, p_wr[0] - s * 0.2, frac)
        sz = _lerp(p_el[2], p_wr[2], frac) + 6.4
        slen = 15.2 + math.sin(frac * math.pi) * 2.2 - frac * 1.0
        sw = 3.55
        eps = ((si % 3) - 1) * 0.05 + si * 0.012
        fo_cubes.append(rot_cube([sx - sw * 0.5, wy - 0.58 + eps, sz],
                                 [sx + sw * 0.5, wy + 0.08 + eps, sz + slen], 'wing_flight',
                                 rotation=[5.5, s * (2.0 + frac * 14.0), 0.0], pivot=[sx, wy, sz]))

    b_fo = {'name': f'wing_fore_{side}', 'parent': f'wing_upper_{side}', 'pivot': p_el,
            'rotation': [0.0, 0.0, s * -2.0], 'cubes': fo_cubes}

    # --- 3. wing_hand (smooth curved carpometacarpus + primary coverts + 3 SEAMLESS inner primaries P1..P3!) ---
    ha_cubes = []
    hx0, hx1 = sorted((p_wr[0] - s * 0.9, p_ha[0] + s * 1.4))
    ha_cubes.append(rot_cube([hx0, wy - 0.88, p_wr[2] - 1.65], [hx1, wy + 0.92, p_wr[2] + 2.2], 'wing_covert',
                             rotation=[0.0, yaw_ha, 0.0], pivot=p_wr))
    ha_cubes.append(rot_cube([hx0, wy - 0.45, p_wr[2] - 2.05], [hx1, wy + 0.48, p_wr[2] - 1.0], 'wing_covert',
                             rotation=[0.0, yaw_ha, 0.0], pivot=p_wr))
    # Stage 3: Stepped primary covert rows bridging cleanly onto the slotted finger bases
    ha_cubes.append(rot_cube([hx0, wy + 0.05, p_wr[2] + 0.6], [hx1, wy + 0.85, p_wr[2] + 4.4], 'wing_covert',
                             rotation=[3.5, yaw_ha, 0.0], pivot=p_wr))
    ha_cubes.append(rot_cube([hx0, wy - 0.25, p_wr[2] + 1.8], [hx1, wy + 0.62, p_wr[2] + 7.4], 'wing_covert_hi',
                             rotation=[4.2, yaw_ha, 0.0], pivot=p_wr))
    for pi in range(3):
        frac = pi / 2.0
        px = _lerp(p_wr[0] + s * 0.4, p_ha[0] - s * 2.2, frac)
        pz = _lerp(p_wr[2] + 4.4, p_ha[2] + 3.0, frac)
        plen = 15.6 - frac * 0.9
        pw = 3.55 - frac * 0.25
        eps = ((pi % 3) - 1) * 0.05 + pi * 0.015
        pyaw = s * (18.0 + frac * 16.0)
        ha_cubes.append(rot_cube([px - pw * 0.5, wy - 0.52 + eps, pz],
                                 [px + pw * 0.5, wy + 0.08 + eps, pz + plen], 'wing_primary',
                                 rotation=[4.8, pyaw, 0.0], pivot=[px, wy, pz]))

    b_ha = {'name': f'wing_hand_{side}', 'parent': f'wing_fore_{side}', 'pivot': p_wr,
            'rotation': [0.0, 0.0, s * 2.0], 'cubes': ha_cubes}

    # --- 3b. Stage 3 Sculpted 3-Feather Alula (bastard wing thumb-fan at wrist leading edge — flares on landing!) ---
    al_cubes = []
    for ai, (alen, az, ayaw, aw) in enumerate(((6.6, -0.55, 6.0, 1.35), (5.5, 0.25, 1.5, 1.25), (4.3, 0.95, -3.5, 1.15))):
        ax0, ax1 = sorted((p_al[0] - s * 0.4, p_al[0] + s * alen * ws))
        aeps = (ai - 1) * 0.05
        al_cubes.append(rot_cube([ax0, p_al[1] - 0.26 + aeps, p_al[2] + az - aw * 0.5],
                                 [ax1, p_al[1] + 0.28 + aeps, p_al[2] + az + aw * 0.5], 'wing_primary',
                                 rotation=[0.0, yaw_ha + s * ayaw, s * (-4.5 - ai * 0.8)], pivot=p_al))
    b_al = {'name': f'alula_{side}', 'parent': f'wing_hand_{side}', 'pivot': p_al,
            'rotation': [0.0, 0.0, 0.0], 'cubes': al_cubes}

    # --- 4. wing_tips — 7 Majestic Swept, Fine-Tapered Slotted Eagle Primary Feathers (P10..P4 -> +/-53.5 px!) ---
    # Stage 3: 3-tier feathered taper on the 6 outer fingers (broad inner web -> notched vane -> fine aero point!)
    ti_cubes = []
    finger_specs = [
        ( -4.0, 14.8 * ws, -1.6, 2.30, 1.10, 0.62, -9.5),  # P10 shorter leading slotted primary
        ( -9.5, 17.8 * ws, -0.7, 2.45, 1.16, 0.66, -8.5),  # P9
        (-15.5, 19.6 * ws,  0.2, 2.55, 1.20, 0.68, -7.5),  # P8 longest majestic eagle wingtip (~ +/-53.5 px = 3.34b!)
        (-22.0, 19.2 * ws,  1.0, 2.55, 1.20, 0.68, -6.5),  # P7
        (-28.5, 18.0 * ws,  1.8, 2.55, 1.20, 0.68, -5.5),  # P6
        (-35.5, 16.4 * ws,  2.5, 2.50, 1.22, 0.72, -4.5),  # P5
        (-43.0, 14.6 * ws,  3.1, 2.55, 1.28, 0.00, -3.5),  # P4 seamlessly bridging into inner primary P3!
    ]
    for fi, (ang_x, flen, dz, w_base, w_tip, w_pt, up_roll) in enumerate(finger_specs):
        fyaw = s * ang_x
        froll = s * up_roll
        eps = ((fi % 3) - 1) * 0.045 + fi * 0.015
        fz = p_ha[2] + dz
        fx_base = p_ha[0] - s * 3.2
        mid_l = flen * 0.46
        pt_l = flen * 0.80 if w_pt > 0 else flen
        fx_mid = fx_base + s * mid_l
        fx_pt = fx_base + s * pt_l
        fx_end = fx_base + s * flen
        fpiv = [fx_base, wy + eps, fz]

        x_a0, x_a1 = sorted((fx_base, fx_mid + s * 0.35))
        x_b0, x_b1 = sorted((fx_mid - s * 0.25, fx_pt + s * (0.20 if w_pt > 0 else 0.0)))
        ti_cubes.append(rot_cube([x_a0, wy - 0.36 + eps, fz - w_base * 0.5],
                                 [x_a1, wy + 0.14 + eps, fz + w_base * 0.5], 'wing_primary',
                                 rotation=[0.0, round(fyaw, 3), round(froll * 0.50, 3)], pivot=fpiv))
        ti_cubes.append(rot_cube([x_b0, wy - 0.26 + eps, fz - w_tip * 0.5],
                                 [x_b1, wy + 0.12 + eps, fz + w_tip * 0.5], 'wing_tip',
                                 rotation=[0.0, round(fyaw, 3), round(froll * 0.90, 3)], pivot=fpiv))
        if w_pt > 0:
            x_c0, x_c1 = sorted((fx_pt - s * 0.20, fx_end))
            ti_cubes.append(rot_cube([x_c0, wy - 0.20 + eps, fz - w_pt * 0.5],
                                     [x_c1, wy + 0.10 + eps, fz + w_pt * 0.5], 'wing_tip',
                                     rotation=[0.0, round(fyaw, 3), round(froll * 1.15, 3)], pivot=fpiv))

    b_ti = {'name': f'wing_tips_{side}', 'parent': f'wing_hand_{side}', 'pivot': p_ha,
            'rotation': [0.0, 0.0, s * -5.0], 'cubes': ti_cubes}

    return [b_up, b_fo, b_ha, b_al, b_ti]


def build_bones(mode='perched', pitch_override=None, crest_override=None, tail_override=None,
                jaw_override=None, head_style=None, beak_style=None):
    """Builds the complete Martial Eagle skeleton & Stage 3/4 sculpted 3D geometry.
    mode:
      - 'perched': upright 48 deg perched pose, folded sculpted wing capes active (for Workshop Compare!)
      - 'soaring': horizontal 0 deg soaring pose, legs tucked cleanly BACKWARD, spread wings active
      - 'game':    full unified GeckoLib rig containing BOTH wing sets (cross-scaled by animation clips!)"""
    is_soar = (mode == 'soaring')
    include_folded = (mode in ('perched', 'game'))
    include_spread = (mode in ('soaring', 'game'))
    hs = head_style or HEAD_STYLE
    bs = beak_style or BEAK_STYLE

    wf = 0.0 if is_soar else 1.0
    bp = (BODY_PITCH * wf) if pitch_override is None else pitch_override
    cr = CREST_RAISE if crest_override is None else crest_override
    ts = TAIL_SPREAD if tail_override is None else tail_override
    jo = JAW_OPEN if jaw_override is None else jaw_override
    ws = WING_SPAN_SCALE
    hw = CHEST_HW

    bones = []
    bones.append({'name': 'root', 'pivot': [0.0, 0.0, 0.0], 'cubes': []})

    # --- 1. SMOOTH SCULPTED ROUNDED RAPTOR FUSELAGE (no boxy flat steps!) ---
    by = 22.0
    bz = -0.8
    body_cubes = []

    # Upper dark Martial Eagle bib / collar (tapered rounded oval flowing cleanly from neck)
    rounded(body_cubes, 0.0, by - 0.2, hw * 0.74, 4.3, bz - 11.6, bz - 7.4, 'hood')
    rounded(body_cubes, 0.0, by - 0.4, hw * 0.92, 5.2, bz - 8.4,  bz - 3.4, 'hood')
    # Deep rounded muscular breast (proud snow-white lower chest keel curving smoothly into thighs!)
    rounded(body_cubes, 0.0, by - 2.6, hw * 0.90, 5.1, bz - 7.2,  bz + 2.8, 'chest_white')
    rounded(body_cubes, 0.0, by - 4.4, hw * 0.74, 3.6, bz - 5.2,  bz + 3.8, 'chest_white')
    # Broad muscular dorsal back / mantle dome
    rounded(body_cubes, 0.0, by + 1.4, hw * 0.96, 5.1, bz - 4.4,  bz + 3.6, 'mantle')
    # Smoothly tapered waist & lower back
    rounded(body_cubes, 0.0, by + 0.6, hw * 0.84, 4.4, bz + 2.4,  bz + 8.4, 'mantle')
    rounded(body_cubes, 0.0, by - 2.2, hw * 0.72, 3.4, bz + 2.2,  bz + 8.2, 'chest_white')
    # Sleek tapered rump & undertail coverts
    rounded(body_cubes, 0.0, by - 0.1, hw * 0.64, 3.3, bz + 7.4,  bz + 12.2, 'mantle')
    rounded(body_cubes, 0.0, by - 1.7, hw * 0.52, 2.2, bz + 7.6,  bz + 12.6, 'chest_white')
    rounded(body_cubes, 0.0, by - 0.3, hw * 0.48, 2.2, bz + 11.2, bz + 14.4, 'mantle')

    # Stage 3: Iconic dark Martial Eagle V-scalloped breast bib feathers lapping over the snow-white chest!
    for bi, (bx, bdz, bw, bl, broll) in enumerate((
        ( 0.0, -5.0, 3.6, 3.4,  0.0),
        ( 2.8, -5.6, 2.9, 3.1, -12.0),
        (-2.8, -5.6, 2.9, 3.1,  12.0),
        ( 4.8, -6.2, 2.3, 2.8, -22.0),
        (-4.8, -6.2, 2.3, 2.8,  22.0),
    )):
        body_cubes.append(rot_cube([bx - bw * 0.5, by - 7.1, bz + bdz],
                                   [bx + bw * 0.5, by - 5.4, bz + bdz + bl], 'hood',
                                   rotation=[12.0, 0.0, broll], pivot=[bx, by - 6.2, bz + bdz]))

    bones.append({
        'name': 'body', 'parent': 'root', 'pivot': [0.0, by, bz],
        'rotation': [round(-bp, 3), 0.0, 0.0], 'cubes': body_cubes,
    })

    # --- 2. PROUD ROUNDED S-CURVE NECK & FIERCE SCULPTED MARTIAL EAGLE HEAD ---
    n1_p = [0.0, by + 1.5, bz - 9.8]
    n1_cubes = []
    rounded(n1_cubes, 0.0, n1_p[1] - 0.1, 3.55, 3.45, n1_p[2] - 4.0, n1_p[2] + 2.2, 'hood')
    # Stage 3: Sculpted dorsal nape hackle shingles sweeping smoothly onto the mantle!
    n1_cubes.append(rot_cube([-2.4, n1_p[1] + 2.7, n1_p[2] - 2.6], [2.4, n1_p[1] + 3.9, n1_p[2] + 3.0], 'crest',
                             rotation=[-10.0, 0.0, 0.0], pivot=[0.0, n1_p[1] + 3.3, n1_p[2] - 1.0]))
    for s in (1, -1):
        nx0, nx1 = sorted((s * 1.4, s * 3.6))
        n1_cubes.append(rot_cube([nx0, n1_p[1] + 1.2, n1_p[2] - 2.4], [nx1, n1_p[1] + 3.2, n1_p[2] + 2.6], 'hood',
                                 rotation=[-8.0, s * -8.0, s * -16.0], pivot=[s * 2.5, n1_p[1] + 2.2, n1_p[2]]))
    bones.append({'name': 'neck_1', 'parent': 'body', 'pivot': n1_p,
                  'rotation': [round(bp * 0.04, 3), 0.0, 0.0], 'cubes': n1_cubes})

    n2_p = [0.0, n1_p[1] + 0.7, n1_p[2] - 3.3]
    n2_cubes = []
    rounded(n2_cubes, 0.0, n2_p[1] + 0.0, 3.05, 3.00, n2_p[2] - 3.8, n2_p[2] + 1.8, 'hood')
    # Stage 3: Upper nape ruff feathers bridging head crest smoothly into neck
    n2_cubes.append(rot_cube([-2.0, n2_p[1] + 2.3, n2_p[2] - 2.4], [2.0, n2_p[1] + 3.4, n2_p[2] + 2.6], 'crest',
                             rotation=[-12.0, 0.0, 0.0], pivot=[0.0, n2_p[1] + 2.8, n2_p[2] - 0.8]))
    bones.append({'name': 'neck_2', 'parent': 'neck_1', 'pivot': n2_p,
                  'rotation': [round(bp * 0.24, 3), 0.0, 0.0], 'cubes': n2_cubes})

    # --- 2b. RESHAPED AERODYNAMIC RAPTOR SKULL + REUSABLE BIRD_BEAK & BIRD_EYE VARIATIONS (A / B / C) ---
    # Sleek flat-crowned Martial Eagle cranium: fuller rounded rear temples tapering sharply past the binocular
    # eye sockets into a narrow loral bridge & sloped forehead flowing FLUSH onto the pale cere (ZERO forehead cliff!).
    hp = [0.0, n2_p[1] + 0.8, n2_p[2] - 3.1]
    head_cubes = []
    # 1. Rounded rear cranium & temples (smooth 8-sided oval, full behind the eyes)
    rounded(head_cubes, 0.0, hp[1] + 0.15, 2.98, 2.42, hp[2] - 2.4, hp[2] + 1.9, 'hood')
    # 2. Narrowed mid-orbital skull core (stops cleanly behind anterior eye corner so binocular eyes shine in Front view!)
    rounded(head_cubes, 0.0, hp[1] + 0.10, 2.22, 2.25, hp[2] - 4.25, hp[2] - 1.7, 'hood')
    # 3. Narrow tapered loral bridge connecting skull cleanly straight into the pale cere base!
    rounded(head_cubes, 0.0, hp[1] + 0.25, 1.46, 1.65, hp[2] - 5.40, hp[2] - 3.70, 'hood')
    # 4. Streamlined flat-topped eagle crown cap + flush forehead ramp meeting cere top seamlessly!
    head_cubes.append(cube([-2.22, hp[1] + 1.78, hp[2] - 4.3], [2.22, hp[1] + 2.78, hp[2] + 1.4], 'hood'))
    head_cubes.append(rot_cube([-1.42, hp[1] + 1.40, hp[2] - 5.95], [1.42, hp[1] + 2.58, hp[2] - 3.1], 'hood',
                               rotation=[11.0, 0.0, 0.0], pivot=[0.0, hp[1] + 2.22, hp[2] - 4.2]))
    # 5. Rear occipital crown wedge flowing directly into the 2-tier Martial Eagle crest
    head_cubes.append(rot_cube([-2.02, hp[1] + 1.25, hp[2] - 0.2], [2.02, hp[1] + 2.74, hp[2] + 2.8], 'hood',
                               rotation=[-12.0, 0.0, 0.0], pivot=[0.0, hp[1] + 2.1, hp[2] + 0.6]))
    # 6. Dark feathered throat & sculpted swept cheek/malar plumes tapering cleanly to the beak gape!
    head_cubes.append(cube([-2.18, hp[1] - 2.60, hp[2] - 4.2], [2.18, hp[1] - 1.15, hp[2] + 1.3], 'hood'))
    for s in (1, -1):
        # Swept ear-covert cheek plumes behind & below the angled eye socket
        cx0, cx1 = sorted((s * 2.18, s * 3.16))
        head_cubes.append(rot_cube([cx0, hp[1] - 1.65, hp[2] - 3.0], [cx1, hp[1] + 0.30, hp[2] + 1.5], 'hood',
                                   rotation=[-8.0, s * -10.0, s * 6.0], pivot=[s * 2.7, hp[1] - 0.6, hp[2] - 0.9]))
        # Tapered malar cheek wedge sloping inward under the eye straight to the corner of the beak gape!
        mx0, mx1 = sorted((s * 1.05, s * 2.38))
        head_cubes.append(rot_cube([mx0, hp[1] - 1.98, hp[2] - 5.35], [mx1, hp[1] - 0.65, hp[2] - 2.2], 'hood',
                                   rotation=[6.0, s * -22.0, 0.0], pivot=[s * 1.75, hp[1] - 1.32, hp[2] - 3.6]))

    # 3 Distinct Reusable Beak + Eye Presets ('A' Clean, 'B' Hyper-Sculpted Apex, 'C' Bold Iconic Heavy)
    HEAD_PRESETS = {
        'A': dict(
            beak=dict(length=BEAK_LEN * 0.94, width=2.28, height=2.76, arch=0.18, hook_drop=1.00,
                      hook_pow=3.10, keel_ratio=0.54, cere_frac=0.29, festoon=0.18, segments=4, tip_w_frac=0.15),
            eye=dict(x=2.48, y=0.72, z=-3.68, ry=0.76, rz=0.90, yaw=22.0, pitch=2.0,
                     brow_pitch=10.0, brow_roll=-13.0, brow_overhang=0.45, ring_detail='clean', pupil_scale=0.46),
        ),
        'B': dict(
            beak=dict(length=BEAK_LEN * 1.00, width=2.40, height=2.96, arch=0.20, hook_drop=1.18,
                      hook_pow=3.20, keel_ratio=0.46, cere_frac=0.30, festoon=0.28, segments=6, tip_w_frac=0.13),
            eye=dict(x=2.50, y=0.72, z=-3.72, ry=0.80, rz=0.95, yaw=26.0, pitch=2.5,
                     brow_pitch=12.0, brow_roll=-16.0, brow_overhang=0.52, ring_detail='full', pupil_scale=0.44),
        ),
        'C': dict(
            beak=dict(length=BEAK_LEN * 0.98, width=2.62, height=3.20, arch=0.22, hook_drop=1.30,
                      hook_pow=3.35, keel_ratio=0.56, cere_frac=0.33, festoon=0.24, segments=5, tip_w_frac=0.15),
            eye=dict(x=2.54, y=0.74, z=-3.70, ry=0.86, rz=1.00, yaw=24.0, pitch=2.0,
                     brow_pitch=14.0, brow_roll=-18.0, brow_overhang=0.58, ring_detail='bold', pupil_scale=0.45),
        ),
    }
    # 3 Distinct Arc-Length Curved Beak Variations (ZERO hanging nob / ZERO tip overhang!):
    BEAK_VARIANTS = {
        # 1. Natural Compact Eagle (Real Martial Eagle photo-faithful: tight curved hook, sleek tapered point!)
        '1': dict(length=4.05, width=2.48, height=2.96, arch=0.14, hook_drop=0.68, hook_pow=2.65,
                  keel_ratio=0.52, cere_frac=0.31, festoon=0.16, segments=6, tip_w_frac=0.14, hook_curl=0.22),
        # 2. Deep Curved Apex Scimitar (Sleeker 7-seg crescent arch curving cleanly back into a sharp raptor fang!)
        '2': dict(length=4.32, width=2.38, height=3.08, arch=0.18, hook_drop=1.06, hook_pow=2.45,
                  keel_ratio=0.44, cere_frac=0.28, festoon=0.28, segments=7, tip_w_frac=0.10, hook_curl=0.34),
        # 3. Chunky Classic Minecraft Raptor (Shorter, wider, bolder 4-seg Minecraft-crisp bill & tight hook!)
        '3': dict(length=3.75, width=2.76, height=3.22, arch=0.15, hook_drop=0.60, hook_pow=2.95,
                  keel_ratio=0.64, cere_frac=0.34, festoon=0.12, segments=4, tip_w_frac=0.20, hook_curl=0.16),
    }

    hp_cfg = HEAD_PRESETS.get(hs, HEAD_PRESETS['C'])
    b_cfg = BEAK_VARIANTS.get(str(bs), hp_cfg['beak'])
    e_cfg = hp_cfg['eye']

    # Sculpted curved keeled raptor beak via reusable bird_beak() system!
    jaw_cubes, tongue_cubes = [], []
    jaw_piv, tongue_piv = bird_beak(
        head_cubes, jaw_cubes, tongue_cubes,
        cx=0.0, y_base=hp[1] - 0.72, z_base=hp[2] - 5.15,
        style='raptor_hook', **b_cfg,
        cere_part='cere', beak_part='beak', tip_part='beak_tip',
        inner_part='mouth_inner', tongue_part='tongue', nostril_part='nostril',
    )

    bones.append({'name': 'head', 'parent': 'neck_2', 'pivot': hp,
                  'rotation': [round(bp * 0.72, 3), 0.0, 0.0], 'cubes': head_cubes})
    bones.append({'name': 'jaw', 'parent': 'head', 'pivot': jaw_piv,
                  'rotation': [round(jo, 3), 0.0, 0.0], 'cubes': jaw_cubes})
    bones.append({'name': 'tongue', 'parent': 'jaw', 'pivot': tongue_piv, 'cubes': tongue_cubes})

    # Iconic 2-Tier Martial Eagle Occipital Crest (lush shorter front crown plumes + long trailing crest!)
    crest_pitch = -8.0 - cr * 28.0
    crest_c_cubes = []
    feather_fan(crest_c_cubes, (0.0, hp[1] + 2.38, hp[2] + 0.5), count=3, length=5.1, width=1.25,
                thickness=0.44, spread_deg=24.0, camber_deg=7.0, pitch_deg=14.0, axis='crest',
                stagger=0.05, part='crest')
    feather_fan(crest_c_cubes, (0.0, hp[1] + 2.66, hp[2] - 0.7), count=3, length=3.4, width=1.05,
                thickness=0.38, spread_deg=20.0, camber_deg=6.0, pitch_deg=18.0, axis='crest',
                stagger=0.04, part='crest')
    bones.append({'name': 'crest_c', 'parent': 'head', 'pivot': [0.0, hp[1] + 2.38, hp[2] + 0.5],
                  'rotation': [round(crest_pitch, 3), 0.0, 0.0], 'cubes': crest_c_cubes})
    for s, side in ((1, 'left'), (-1, 'right')):
        cc = []
        feather_fan(cc, (s * 1.40, hp[1] + 2.05, hp[2] + 0.6), count=2, length=4.5, width=1.05,
                    thickness=0.38, spread_deg=18.0, camber_deg=6.0, pitch_deg=10.0, axis='crest',
                    side=s, stagger=0.05, part='crest')
        cc.append(rot_cube([min(s * 1.00, s * 1.98), hp[1] + 2.15, hp[2] - 0.7],
                           [max(s * 1.00, s * 1.98), hp[1] + 2.55, hp[2] + 2.2], 'crest',
                           rotation=[16.0, s * 10.0, s * -6.0], pivot=[s * 1.50, hp[1] + 2.35, hp[2] - 0.6]))
        bones.append({'name': f'crest_{side}', 'parent': 'head', 'pivot': [s * 1.40, hp[1] + 2.05, hp[2] + 0.6],
                      'rotation': [round(crest_pitch * 0.85, 3), round(s * 14.0, 3), round(s * -8.0, 3)], 'cubes': cc})

    # Sculpted binocular-angled raptor eyes + dark orbital rings + brows via reusable bird_eye() system!
    for s, side in ((1, 'left'), (-1, 'right')):
        bones.extend(bird_eye(
            s, side, 'head', pos=(s * e_cfg['x'], hp[1] + e_cfg['y'], hp[2] + e_cfg['z']),
            radius_y=e_cfg['ry'], radius_z=e_cfg['rz'], yaw_deg=e_cfg['yaw'], pitch_deg=e_cfg['pitch'],
            brow_pitch=e_cfg['brow_pitch'], brow_roll=e_cfg['brow_roll'],
            brow_overhang=e_cfg['brow_overhang'], ring_detail=e_cfg['ring_detail'],
            eye_part='eye', pupil_part='pupil', lid_part='eyelid', brow_part='hood',
            ring_part='eyelid', pupil_scale=e_cfg['pupil_scale'],
        ))

    # --- 3. DUAL SCULPTED WING ARCHITECTURE ---
    # 3A. Dedicated sculpted teardrop folded wing capes (active when perched / on ground)
    if include_folded:
        for s, side in ((1, 'left'), (-1, 'right')):
            bones.extend(_build_folded_wing(s, side, by, bz, hw))

    # 3B. Dedicated sculpted soaring airfoil wings (active in flight / flapping / soaring)
    if include_spread:
        for s, side in ((1, 'left'), (-1, 'right')):
            bones.extend(_build_spread_wing(s, side, by, bz, ws))

    # --- 4. ARTICULATED 7-FEATHER TAIL FAN + STAGE 3 UPPER & UNDER TAIL COVERTS ---
    tp = [0.0, by - 0.5, bz + 12.2]
    tb_cubes = []
    rounded(tb_cubes, 0.0, tp[1], 3.4, 1.55, tp[2] - 1.2, tp[2] + 3.6, 'mantle')
    # Stage 3: Layered dark uppertail covert shingles + crisp snow-white undertail covert feathers!
    tb_cubes.append(rot_cube([-2.3, tp[1] + 0.45, tp[2] + 1.2], [2.3, tp[1] + 1.25, tp[2] + 7.2], 'mantle',
                             rotation=[-3.0, 0.0, 0.0], pivot=[0.0, tp[1] + 0.8, tp[2] + 1.2]))
    for s in (1, -1):
        ux0, ux1 = sorted((s * 0.8, s * 3.2))
        tb_cubes.append(rot_cube([ux0, tp[1] + 0.25, tp[2] + 0.8], [ux1, tp[1] + 0.95, tp[2] + 6.4], 'mantle',
                                 rotation=[-2.0, s * 10.0, s * -4.0], pivot=[s * 1.8, tp[1] + 0.6, tp[2] + 0.8]))
    tb_cubes.append(rot_cube([-2.4, tp[1] - 1.25, tp[2] + 0.8], [2.4, tp[1] - 0.40, tp[2] + 6.6], 'chest_white',
                             rotation=[4.0, 0.0, 0.0], pivot=[0.0, tp[1] - 0.8, tp[2] + 0.8]))
    bones.append({'name': 'tail_base', 'parent': 'body', 'pivot': tp,
                  'rotation': [round(3.5 * wf, 3), 0.0, 0.0], 'cubes': tb_cubes})

    tc_cubes = []
    feather_fan(tc_cubes, (0.0, tp[1], tp[2] + 0.8), count=3, length=15.4, width=2.75, thickness=0.62,
                spread_deg=11.0 + ts * 14.0, camber_deg=3.0, pitch_deg=-2.0, axis='tail', taper=0.05,
                stagger=0.04, part='tail')
    bones.append({'name': 'tail_c', 'parent': 'tail_base', 'pivot': [0.0, tp[1], tp[2] + 0.8], 'cubes': tc_cubes})

    for s, side in ((1, 'left'), (-1, 'right')):
        side_cubes = []
        feather_fan(side_cubes, (s * 1.45, tp[1] - 0.10, tp[2] + 0.6), count=2, length=14.3, width=2.65, thickness=0.58,
                    spread_deg=9.0, camber_deg=2.5, pitch_deg=-2.0, axis='tail', taper=0.07,
                    stagger=0.04, part='tail')
        bones.append({'name': f'tail_{side}', 'parent': 'tail_base', 'pivot': [s * 1.45, tp[1] - 0.10, tp[2] + 0.6],
                      'rotation': [0.0, round(s * (6.5 + ts * 20.0), 3), round(s * (-2.0 - ts * 4.0), 3)],
                      'cubes': side_cubes})

    # --- 5. ROUNDED MUSCULAR FEATHERED RAPTOR LEGS, PLUSH TROUSERS & SCALY 3+1 TALONS ---
    tuck = 1.0 - wf
    for s, side in ((1, 'left'), (-1, 'right')):
        hip = [s * 3.80, 17.6, -1.2]
        knee = [s * 3.80, 9.4, -0.6]
        ankle = [s * 3.75, 2.3, -2.9]

        # Smooth rounded snow-white feathered eagle trousers + Stage 3 flared outer thigh flag feathers!
        u_cubes = []
        _rounded_y(u_cubes, hip[0], hip[1] - 5.2, hip[1] + 2.4, 2.70, 3.65, hip[2] - 0.2, 'thigh_white')
        _rounded_y(u_cubes, hip[0], hip[1] - 8.8, hip[1] - 4.2, 2.15, 2.80, hip[2] + 0.1, 'thigh_white')
        # Outer trouser feather flag sweeping gracefully down the side of the thigh
        tx0, tx1 = sorted((hip[0] + s * 1.35, hip[0] + s * 2.85))
        u_cubes.append(rot_cube([tx0, hip[1] - 7.8, hip[2] - 1.9], [tx1, hip[1] - 1.0, hip[2] + 2.1], 'thigh_white',
                                rotation=[6.0, 0.0, s * -6.0], pivot=[hip[0] + s * 2.1, hip[1] - 3.0, hip[2]]))
        bones.append({'name': f'leg_upper_{side}', 'parent': 'root', 'pivot': hip,
                      'rotation': [round(tuck * 78.0, 3), 0.0, round(s * -3.5 * wf, 3)], 'cubes': u_cubes})

        # Rounded feathered lower knee cuff + Stage 3 scalloped feathered ankle skirt lapping over scaly tarsus!
        l_cubes = []
        _rounded_y(l_cubes, knee[0], knee[1] - 2.3, knee[1] + 1.4, 1.75, 2.00, knee[2], 'thigh_white')
        l_cubes.append(rot_cube([knee[0] - 1.35, knee[1] - 3.6, knee[2] - 1.9],
                                [knee[0] + 1.35, knee[1] - 1.2, knee[2] + 0.6], 'thigh_white',
                                rotation=[-16.0, 0.0, 0.0], pivot=[knee[0], knee[1] - 1.4, knee[2] - 0.4]))
        l_cubes.append(rot_cube([knee[0] - 1.30, knee[1] - 3.2, knee[2] - 0.2],
                                [knee[0] + 1.30, knee[1] - 1.0, knee[2] + 1.9], 'thigh_white',
                                rotation=[12.0, 0.0, 0.0], pivot=[knee[0], knee[1] - 1.2, knee[2] + 0.6]))
        bones.append({'name': f'leg_lower_{side}', 'parent': f'leg_upper_{side}', 'pivot': knee,
                      'rotation': [round(-tuck * 10.0, 3), 0.0, 0.0], 'cubes': l_cubes})

        # Sturdy scaly pale yellow-ochre tarsometatarsus + Stage 3 anterior scute ridge & sculpted ankle knuckle!
        t_piv = [knee[0], knee[1] - 0.9, knee[2]]
        t_cubes = [
            rot_cube([t_piv[0] - 1.12, t_piv[1] - 6.5, t_piv[2] - 1.15],
                     [t_piv[0] + 1.12, t_piv[1] + 0.6, t_piv[2] + 1.15], 'tarsus',
                     rotation=[-19.5, 0.0, 0.0], pivot=t_piv),
            # Front scaly scute ridge (gives cylindrical octagonal raptor shin feel up close!)
            rot_cube([t_piv[0] - 0.72, t_piv[1] - 6.3, t_piv[2] - 1.42],
                     [t_piv[0] + 0.72, t_piv[1] - 0.8, t_piv[2] + 1.36], 'tarsus',
                     rotation=[-19.5, 0.0, 0.0], pivot=t_piv),
            # Rounded ankle knuckle hub above the gripping toes
            cube([ankle[0] - 1.25, ankle[1] - 0.65, ankle[2] - 1.25],
                 [ankle[0] + 1.25, ankle[1] + 0.65, ankle[2] + 1.25], 'tarsus'),
        ]
        bones.append({'name': f'leg_tarsus_{side}', 'parent': f'leg_lower_{side}', 'pivot': t_piv,
                      'rotation': [round(tuck * 22.0, 3), 0.0, 0.0], 'cubes': t_cubes})

        # 3+1 Anisodactyl gripping talons resting cleanly at y = +0.02 px + Stage 3 sculpted toe knuckle pads!
        foot_bones = bird_talon_foot(
            s, side, f'leg_tarsus_{side}', ankle=ankle, foot_y=0.78,
            toe_lens=(3.6, 4.7, 3.5, 3.1), toe_fans=(-28.0, 0.0, 28.0),
            toe_r=0.52, claw_len=2.1 * TALON_SCALE, claw_drop=3.5,
            part='tarsus', claw_part='talon', prefix='leg',
        )
        # Add subtle dorsal knuckle-scale bumps on middle & rear hallux toes for fierce close-up raptor grip!
        for fb in foot_bones:
            if fb['name'] == f'leg_toe2_{side}':
                p2 = fb['pivot']
                fb['cubes'].append(cube([p2[0] - 0.56, 0.98, p2[2] - 2.6], [p2[0] + 0.56, 1.88, p2[2] - 1.5], 'tarsus'))
            elif fb['name'] == f'leg_hallux_{side}':
                ph = fb['pivot']
                fb['cubes'].append(cube([ph[0] - 0.52, 0.96, ph[2] + 0.9], [ph[0] + 0.52, 1.82, ph[2] + 2.0], 'tarsus'))
        # In soaring flight, clench toes backward cleanly into aerodynamic tucked fists!
        if tuck > 0.01:
            for fb in foot_bones:
                if '_toe' in fb['name']:
                    r = list(fb.get('rotation', [0.0, 0.0, 0.0]))
                    r[0] = round(r[0] + tuck * 52.0, 3)
                    fb['rotation'] = r
                elif '_hallux_' in fb['name'] and 'talon' not in fb['name']:
                    r = list(fb.get('rotation', [0.0, 0.0, 0.0]))
                    r[0] = round(r[0] - tuck * 48.0, 3)
                    fb['rotation'] = r
        bones.extend(foot_bones)

    return bones


# --- Stage 2 Clean Plain & Clay Palettes (for pure 3D shape judging before Stage 4 Paint!) ---------------------------
PLAIN_PALETTE = {
    'hood':           (58, 44, 38),     # Deep Martial Eagle chocolate hood/bib
    'mantle':         (74, 58, 49),     # Rich dark umber back & rump
    'crest':          (48, 36, 31),     # Dark occipital crest
    'wing_covert':    (64, 49, 41),     # Upperwing lesser/median coverts
    'wing_covert_hi': (88, 70, 58),     # Greater covert shingle tier (shows feather layers cleanly!)
    'wing_flight':    (110, 96, 84),    # Broad secondaries & tertials
    'wing_primary':   (52, 43, 39),     # Outer primaries & alula
    'wing_tip':       (34, 28, 26),     # Slotted dark finger tips
    'tail':           (72, 62, 56),     # Graduated 7-feather tail fan
    'chest_white':    (228, 221, 210),  # Snow-white belly & breast
    'thigh_white':    (222, 215, 204),  # Feathered eagle trousers
    'cere':           (148, 146, 138),  # Pale slate-grey cere
    'beak':           (60, 62, 68),     # Dark slate hooked beak
    'beak_tip':       (32, 33, 38),     # Sharp charcoal hook tip
    'nostril':        (26, 24, 24),
    'mouth_inner':    (128, 66, 68),
    'tongue':         (176, 98, 102),
    'eye':            (242, 188, 24),   # Blazing golden-amber eagle eye
    'pupil':          (16, 14, 16),
    'eyelid':         (52, 40, 35),
    'tarsus':         (210, 194, 136),  # Pale yellow-grey scaly legs & toes
    'talon':          (30, 28, 32),     # Curved black scimitar talons
}

CLAY_PALETTE = {
    k: ((238, 188, 32) if k == 'eye' else (24, 24, 28) if k == 'pupil' else (188, 182, 172))
    for k in PLAIN_PALETTE
}


def _painted_colour(c, face, x, y, z):
    """Stage 4 Minecraft-first pixel-art Martial Eagle plumage (TEXELS = 2.0):
    - Deep chocolate-slate hood/bib/mantle (solid dark hood wrapping under chin, no white chin patch).
    - Scalloped pale-edged dark dorsal upperwing coverts (solid dark dorsal upperwings, no white dorsal stripe!).
    - Snow-white breast & plush feathered trousers speckled with iconic dark chocolate teardrop spots.
    - High-contrast creamy-barred soaring underwings vs. dark leading under-coverts & charcoal wingtip fingers.
    - Barred 7-feather tail fan with broad dark subterminal band + thin pale terminal tip.
    - Living radial golden-amber eagle iris with limbal rim & crisp specular glint, pale blue-grey cere,
      glossy horn-charcoal beak, scaly yellow-ochre tarsi with horizontal scutes, and glossy black talons."""
    part = c.get('_part', 'mantle')
    bone = c.get('_bone', 'body')
    ox, oy, oz = c['origin']
    sx, sy, sz = c['size']
    lx = (x - ox) / max(1e-4, sx)
    ly = (y - oy) / max(1e-4, sy)
    lz = (z - oz) / max(1e-4, sz)

    # Crisp Minecraft-texel quantized coordinates (2 texels per model px) for clean, non-blurry pixel art
    qx = math.floor(x * 2.0) * 0.5
    qy = math.floor(y * 2.0) * 0.5
    qz = math.floor(z * 2.0) * 0.5
    h_fine = hash3(int(math.floor(x * 2.0)), int(math.floor(y * 2.0)), int(math.floor(z * 2.0)))

    # Directional face lighting bias (keeps volume readable in Minecraft flat/smooth shading)
    fk = {'up': 1.08, 'down': 0.85, 'north': 1.03, 'south': 0.93, 'east': 0.98, 'west': 0.95}.get(face, 1.0)

    # --- 1. Living Raptor Eye (radial golden-amber iris + dark limbal rim + crisp specular glint!) ---
    if part == 'eye':
        is_outer = (face == 'east' and 'left' in bone) or (face == 'west' and 'right' in bone)
        if is_outer:
            # Local iris coords: v=ly (0 bottom..1 top), u_fwd (0 back..1 front toward -Z!)
            u_fwd = 1.0 - lz
            du = u_fwd - 0.50
            dv = ly - 0.50
            r = math.hypot(du, dv)
            ang = math.atan2(dv, du)
            rad_streak = 0.5 + 0.5 * math.sin(ang * 10.0 + h_fine * 2.2)
            # Crisp white specular catchlight glint at upper-front quadrant of the iris!
            if abs(u_fwd - 0.68) < 0.14 and abs(ly - 0.72) < 0.14:
                return (255, 252, 236)
            if r < 0.27:
                col = mix((255, 224, 48), (248, 196, 26), rad_streak * 0.45)
            elif r < 0.41:
                t = (r - 0.27) / 0.14
                col = mix((244, 186, 22), (206, 130, 14), t)
                col = mix(col, (254, 212, 40), rad_streak * 0.22)
            elif r < 0.48:
                col = mix((164, 92, 14), (58, 38, 22), (r - 0.41) / 0.07)
            else:
                col = (40, 28, 22)
            return col
        return shade((210, 152, 20), fk)

    if part == 'pupil':
        # Jet-black pupil with tiny crisp upper-front pinpoint highlight
        if (face == 'east' and 'left' in bone) or (face == 'west' and 'right' in bone):
            u_fwd = 1.0 - lz
            if u_fwd > 0.62 and ly > 0.62:
                return (210, 218, 228)
        return (14, 13, 17)

    if part == 'eyelid':
        g = feather_grain(qx, qy, qz, scale=2.4, flow=(0.0, 0.1, 1.0))
        return shade(mix((34, 26, 24), (52, 40, 35), 0.5 + 0.35 * g), fk)

    # --- 2. Mottled Smoky-Ash Cere, Glossy Striated Slate-Horn Hooked Beak, & Fleshy Inner Mouth ---
    if part == 'cere':
        # Organic mottled smoky-ash / blue-grey cere blending smoothly from dark facial feathers (+Z) into horn (-Z)!
        g_c = feather_grain(qx, qy, qz, scale=2.8, flow=(0.0, 0.0, 1.0))
        base = mix((112, 116, 122), (156, 160, 164), 0.45 + 0.32 * g_c + 0.18 * h_fine)
        # Feathered charcoal-brown loral blend along the rear collar (+Z) where skull meets cere
        if lz > 0.62:
            t_col = (lz - 0.62) / 0.38
            base = mix(base, (58, 48, 44), t_col * (0.68 + 0.20 * h_fine))
        # Subtle darker waxy crease right along the front cere/beak seam (-Z)
        elif lz < 0.16:
            base = mix(base, (74, 78, 86), (0.16 - lz) / 0.16 * 0.45)
        if face == 'up':
            base = mix(base, (184, 188, 192), 0.32)
        elif face == 'down':
            base = shade(base, 0.78)
        return shade(base, fk)

    if part == 'nostril':
        return (16, 15, 18)

    if part in ('beak', 'beak_tip'):
        # Continuous keratin gradient from smoky-blue-ash base -> glossy dark slate-horn -> obsidian hook point!
        bf = c.get('_beak_f', 0.25 if part == 'beak' else 0.78)
        # Fine longitudinal horn-growth micro-striations along the curved beak surface
        stri = 0.5 + 0.5 * math.sin((y * 4.5 + z * 2.2) + h_fine * 1.4)
        # Smooth transition from smoky slate-blue at base to jet obsidian at razor tip
        horn_base = mix((78, 84, 94), (20, 22, 28), min(1.0, bf * 1.12))
        horn_lt = mix((108, 114, 126), (44, 48, 58), min(1.0, bf * 1.05))
        base = mix(horn_base, horn_lt, stri * 0.36 + h_fine * 0.16)
        # Soft smoky-ash blend right at the very back of segment 0 meeting the cere
        if bf < 0.22 and lz > 0.55:
            base = mix(base, (124, 128, 134), ((lz - 0.55) / 0.45) * 0.45)
        if face == 'up':
            # Crisp sunlit glossy horn sheen along the curved dorsal culmen arch (like the real photo!)
            ridge = max(0.0, 1.0 - abs(lx - 0.5) * 2.2)
            glint = mix((158, 166, 178), (94, 100, 114), bf)
            base = mix(base, glint, (0.48 if bf < 0.75 else 0.36) * ridge)
        elif face in ('east', 'west'):
            # Subtle curved specular highlight band along upper-mid cheek of beak, darker cutting rim below
            if ly > 0.62:
                base = mix(base, (112, 118, 130), 0.26 * (1.0 - bf * 0.6))
            elif ly < 0.22:
                base = shade(base, 0.76)
        elif face == 'down':
            base = shade(base, 0.72)
        return shade(base, fk)

    if part == 'mouth_inner':
        return shade(mix((114, 48, 54), (146, 68, 74), 1.0 - lz), fk)

    if part == 'tongue':
        ridge = max(0.0, 1.0 - abs(lx - 0.5) * 2.2)
        col = mix((168, 86, 92), (212, 128, 132), 0.45 * ridge + 0.25 * (1.0 - lz))
        return shade(col, fk)

    # --- 3. Deep Chocolate-Slate Hood, Crest & V-Scalloped Chest Bib (NO white chin patch!) ---
    if part in ('hood', 'crest'):
        g = feather_grain(qx, qy, qz, scale=2.2, flow=(0.0, -0.25, 1.0))
        dark = (38, 28, 24)
        mid = (56, 42, 35)
        warm = (82, 62, 49)
        col = mix(dark, warm, 0.45 + 0.38 * g)
        # Subtle paler warm-bronze crown/crest feather tips (iconic Martial Eagle textured crest!)
        if part == 'crest':
            tip = max(0.0, (lz - 0.62) / 0.38)
            col = mix(col, (108, 86, 68), tip * 0.55)
            if abs(lx - 0.5) < 0.14:
                col = shade(col, 0.84)  # dark central quill shaft
        elif bone in ('neck_1', 'neck_2'):
            # Layered hackle scallop tips around the thick eagle neck
            tile_z = (z * 0.52 + (0.5 if int(math.floor(x * 0.55)) % 2 else 0.0)) % 1.0
            if tile_z > 0.78:
                col = mix(col, (96, 76, 60), 0.42)
        elif bone == 'body':
            # Dark V-scalloped breast bib: crisp pale-edged feather scallops along lower border
            tile_z = (z * 0.46 + (0.5 if int(math.floor(x * 0.45)) % 2 else 0.0)) % 1.0
            if tile_z > 0.80:
                col = mix(col, (98, 78, 62), 0.36)
        elif bone == 'head':
            # Subtle warm ear-covert & brow feather highlights around the dark raptor face
            if y > 26.2 and abs(x) < 2.2:
                col = mix(col, (76, 58, 46), 0.35)
            elif face == 'down':
                col = shade(mid, 0.78)  # solid dark throat under chin!
        return shade(col, fk)

    # --- 4. Mantle & Back (Scalloped Dark Umber-Chocolate Plumage with Subtle Pale Feather Edges) ---
    if part == 'mantle':
        g = feather_grain(qx, qy, qz, scale=2.0, flow=(0.0, -0.1, 1.0))
        col = mix((46, 35, 29), (74, 57, 46), 0.50 + 0.36 * g)
        # Staggered overlapping dorsal mantle feather scallops (warm buff-taupe crescent tips!)
        col_i = int(math.floor(x * 0.42))
        row_u = (z * 0.36 + (0.5 if col_i % 2 else 0.0)) % 1.0
        cell_x = abs((x * 0.42) - math.floor(x * 0.42) - 0.5) * 2.0
        scallop = row_u + cell_x * 0.26
        if 0.84 <= scallop <= 1.04:
            col = mix(col, (116, 98, 82), 0.52)
        elif cell_x < 0.14:
            col = shade(col, 0.86)  # subtle central shaft
        return shade(col, fk)

    # --- 5. Snow-White Breast, Belly & Feathered Eagle Trousers with Iconic Dark Teardrop Spots! ---
    if part in ('chest_white', 'thigh_white'):
        g = feather_grain(qx, qy, qz, scale=2.1, flow=(0.0, -0.85, 0.52))
        ivory = mix((226, 220, 208), (242, 237, 227), 0.50 + 0.35 * g)
        # Undertail coverts stay snowy-clean white
        if bone == 'tail_base':
            return shade(ivory, fk)
        # Organic staggered Martial Eagle dark chocolate teardrop spots (face-projected so ZERO side-face striping!)
        if part == 'chest_white':
            if face in ('up', 'down'):
                sx_cell, sy_cell = x * 0.40, z * 0.36
            elif face in ('east', 'west'):
                sx_cell, sy_cell = z * 0.36, y * 0.42
            else:
                sx_cell, sy_cell = x * 0.40, y * 0.42
            ci = int(math.floor(sx_cell))
            ri = int(math.floor(sy_cell + (0.5 if ci % 2 else 0.0)))
            fx = sx_cell - ci - 0.5
            fy = (sy_cell + (0.5 if ci % 2 else 0.0)) - ri - 0.5
            # Slight deterministic jitter per feather spot so it never looks mechanical!
            jx = (hash3(ci, ri, 11) - 0.5) * 0.26
            jy = (hash3(ci, ri, 23) - 0.5) * 0.24
            keep = hash3(ci, ri, 37)
            dx = fx - jx
            dy = fy - jy
            # Crisp teardrop / spade spot (slightly taller along feather flow!)
            dist = math.hypot(dx * 1.28, dy * 0.92)
            # Denser spots on upper/mid breast, sparser on far flanks & lower vent
            thresh = 0.27 if z < 2.0 else 0.22
            if keep > 0.24 and dist < thresh:
                spot = mix((42, 32, 28), (64, 49, 41), dist / thresh * 0.55)
                return shade(spot, fk)
            elif keep > 0.24 and dist < thresh + 0.06:
                return shade(mix(ivory, (156, 144, 132), 0.38), fk)
            return shade(ivory, fk)
        else:
            # Feathered eagle trousers: crisp teardrop speckles on upper/mid thigh, snow-white ankle skirt!
            if y > 9.6:
                if face in ('up', 'down'):
                    sx_cell, sy_cell = x * 0.46, z * 0.46
                elif face in ('east', 'west'):
                    sx_cell, sy_cell = z * 0.46, y * 0.44
                else:
                    sx_cell, sy_cell = x * 0.46, y * 0.44
                ci = int(math.floor(sx_cell))
                ri = int(math.floor(sy_cell + (0.5 if ci % 2 else 0.0)))
                fx = sx_cell - ci - 0.5
                fy = (sy_cell + (0.5 if ci % 2 else 0.0)) - ri - 0.5
                jx = (hash3(ci, ri, 41) - 0.5) * 0.24
                jy = (hash3(ci, ri, 53) - 0.5) * 0.22
                keep = hash3(ci, ri, 67)
                dist = math.hypot((fx - jx) * 1.25, (fy - jy) * 0.94)
                thresh = 0.25 if y > 12.2 else 0.19
                if keep > 0.34 and dist < thresh:
                    return shade(mix((44, 34, 30), (68, 53, 44), dist / thresh * 0.5), fk)
            # Soft pale feather-fringe shadow at bottom edge of ankle skirt
            if bone.startswith('leg_lower') and ly < 0.22:
                ivory = mix(ivory, (194, 186, 174), (0.22 - ly) / 0.22 * 0.45)
            return shade(ivory, fk)

    # --- 6. Wings — Solid Dark Scalloped Dorsal Upperwings vs. High-Contrast Barred Soaring Underwings! ---
    is_spread = bone.startswith(('wing_upper', 'wing_fore', 'wing_hand', 'wing_tips', 'alula'))
    is_fold = bone.startswith('wing_fold')

    if part in ('wing_covert', 'wing_covert_hi'):
        g = feather_grain(qx, qy, qz, scale=2.1, flow=(0.0, 0.0, 1.0))
        if is_spread and face == 'down':
            # Iconic Martial Eagle DARK speckled underwing leading coverts!
            col = mix((44, 34, 29), (64, 50, 42), 0.50 + 0.32 * g)
            # Crisp small creamy-white under-covert flecks
            ci = int(math.floor(x * 0.55))
            ri = int(math.floor(z * 0.55))
            if hash3(ci, ri, 79) > 0.72:
                fx = (x * 0.55) - ci - 0.5
                fz = (z * 0.55) - ri - 0.5
                if math.hypot(fx, fz) < 0.26:
                    col = (212, 204, 188)
            return shade(col, 0.94)
        # Solid dark chocolate-slate dorsal upperwing coverts with crisp pale-buff scalloped feather rims!
        base_dk = (44, 34, 28) if part == 'wing_covert' else (56, 44, 36)
        base_lt = (74, 58, 47) if part == 'wing_covert' else (90, 72, 58)
        col = mix(base_dk, base_lt, 0.48 + 0.36 * g)
        # Terraced covert feather scallops
        if is_fold:
            # Diagonal feather shingle edges across the folded cape
            col_i = int(math.floor((y * 0.45 + x * 0.25)))
            row_u = (z * 0.34 + (0.5 if col_i % 2 else 0.0)) % 1.0
            if row_u > 0.80 or lz > 0.88:
                col = mix(col, (132, 114, 96) if part == 'wing_covert_hi' else (106, 88, 72), 0.60)
            elif row_u < 0.14:
                col = shade(col, 0.80)
        else:
            # Spread upperwing covert tiers: crisp pale-buff trailing tip fringe on each covert row!
            col_i = int(math.floor(x * 0.42))
            row_u = (lz * 2.0 + (0.5 if col_i % 2 else 0.0)) % 1.0
            if lz > 0.82 or row_u > 0.84:
                col = mix(col, (128, 110, 92) if part == 'wing_covert_hi' else (102, 86, 72), 0.58)
            elif abs(((x * 0.42) - col_i) - 0.5) < 0.12:
                col = shade(col, 0.85)
        return shade(col, fk)

    if part in ('wing_flight', 'wing_primary', 'wing_tip'):
        # Flight feathers (secondaries, primaries, alula, and slotted finger tips!)
        g = feather_grain(qx, qy, qz, scale=2.2, flow=(0.0, 0.0, 1.0))
        if is_spread and face == 'down':
            # HIGH-CONTRAST SOARING UNDERWING: creamy-silver barred flight feathers + jet-black finger tips!
            if part == 'wing_tip':
                # Pale barred base -> crisp dark charcoal-black outer slotted fingers!
                if lz < 0.34:
                    bar = 0.5 + 0.5 * math.sin(lz * 22.0)
                    col = mix((54, 46, 42), (206, 198, 184), bar * 0.85)
                else:
                    t = min(1.0, (lz - 0.34) / 0.22)
                    col = mix((68, 60, 56), (26, 24, 28), t)
                    if abs(lx - 0.5) < 0.14:
                        col = mix(col, (92, 86, 82), 0.35 * (1.0 - t))
                return shade(col, 0.96)
            # Broad underwing secondaries & inner primaries: creamy-buff with 4-5 crisp dark slate raptor bars!
            bar_phase = math.sin(lz * 5.0 * 2.0 * math.pi)
            is_bar = bar_phase > 0.18
            pale = mix((204, 196, 182), (224, 216, 202), 0.5 + 0.3 * g)
            dark = mix((50, 42, 38), (68, 58, 52), 0.5 + 0.3 * g)
            col = dark if is_bar else pale
            # Crisp dark trailing-edge band + subtle pale feather-tip edge
            if 0.84 <= lz <= 0.96:
                col = (40, 34, 32)
            elif lz > 0.96:
                col = (198, 190, 176)
            # Pale ivory central rachis quill shaft!
            if abs(lx - 0.5) < 0.10:
                col = mix(col, (236, 230, 216), 0.55)
            return shade(col, 0.96)

        # DORSAL flight feathers & FOLDED wingtip spears (solid dark slate-chocolate, subtle raptor barring!)
        if part == 'wing_tip':
            col = mix((24, 22, 24), (44, 38, 36), (1.0 - lz) * 0.65 + 0.25 * g)
            if abs(lx - 0.5) < 0.12:
                col = mix(col, (76, 68, 62), 0.35)
            return shade(col, fk)
        bar = 0.5 + 0.5 * math.sin(lz * 4.5 * 2.0 * math.pi)
        dk = (36, 30, 28) if part == 'wing_primary' else (48, 40, 36)
        lt = (66, 56, 48) if part == 'wing_primary' else (88, 76, 66)
        col = mix(dk, lt, (0.35 + 0.35 * bar) + 0.22 * g)
        if 0.85 <= lz <= 0.95:
            col = (34, 28, 26)
        elif lz > 0.95:
            col = mix(col, (146, 134, 118), 0.62)  # crisp pale trailing feather rim!
        if abs(lx - 0.5) < 0.10:
            col = shade(col, 0.78)  # dark central quill shaft
        return shade(col, fk)

    # --- 7. Graduated 7-Feather Barred Raptor Tail Fan ---
    if part == 'tail':
        g = feather_grain(qx, qy, qz, scale=2.2, flow=(0.0, 0.0, 1.0))
        bar_wave = math.sin(lz * 4.5 * 2.0 * math.pi)
        if face == 'down':
            # Crisp silver-cream barred undertail visible in soaring flight!
            col = (46, 40, 38) if bar_wave > 0.12 else mix((192, 184, 172), (212, 204, 192), 0.5 + 0.25 * g)
            if 0.78 <= lz <= 0.94:
                col = (34, 29, 28)   # broad dark subterminal band!
            elif lz > 0.94:
                col = (224, 218, 206)  # crisp thin white tail-tip edge!
            if abs(lx - 0.5) < 0.09:
                col = mix(col, (230, 224, 212), 0.50)
            return shade(col, 0.95)
        # Dorsal tail: dark slate-chocolate with crisp ash-taupe transverse bars + broad jet subterminal band!
        col = mix((46, 38, 34), (60, 50, 44), 0.5 + 0.3 * g) if bar_wave > 0.05 else mix((98, 86, 76), (118, 106, 94), 0.5 + 0.25 * g)
        if 0.78 <= lz <= 0.94:
            col = (30, 25, 24)
        elif lz > 0.94:
            col = (182, 172, 158)
        if abs(lx - 0.5) < 0.09:
            col = shade(col, 0.78)
        return shade(col, fk)

    # --- 8. Scaly Pale Yellow-Ochre Tarsi & Toes (Horizontal Reptilian Scutes!) + Glossy Jet-Black Talons ---
    if part == 'tarsus':
        base = mix((200, 180, 114), (222, 204, 138), h_fine * 0.45)
        if bone.startswith('leg_tarsus'):
            # Crisp horizontal scute scale bands wrapping around the eagle's scaly shins!
            band = (y * 1.05) % 1.0
            if band < 0.22:
                base = mix(base, (132, 112, 62), 0.65)
            elif band > 0.76:
                base = mix(base, (238, 222, 162), 0.32)
        else:
            # Transverse toe-pad scale bands across every gripping toe!
            band = (z * 1.15) % 1.0
            if face == 'down':
                # Rough textured dark-ochre gripping sole pads underneath toes
                base = mix((144, 126, 78), (168, 150, 96), h_fine * 0.6)
            elif band < 0.24:
                base = mix(base, (128, 110, 62), 0.65)
            elif band > 0.78:
                base = mix(base, (236, 220, 160), 0.30)
        return shade(base, fk)

    if part == 'talon':
        base = mix((20, 20, 24), (34, 34, 40), h_fine * 0.35)
        if face == 'up':
            ridge = max(0.0, 1.0 - abs(lx - 0.5) * 2.2)
            base = mix(base, (82, 86, 98), 0.52 * ridge)  # glossy keratin talon highlight!
        return shade(base, fk)

    return shade(PLAIN_PALETTE.get(part, (150, 140, 130)), fk)


def make_look(look_name=None, **build_kwargs):
    """Builds painted bones + PNG bytes + texture dims for workshop / viewer / game."""
    lk = look_name or WORKSHOP_LOOK
    bones = build_bones(**build_kwargs)

    if lk == 'painted':
        painted, rows, w, h = paint_faces(bones, TEXELS, _painted_colour, world_coords=False)
        return painted, png(rows), w, h

    pal = CLAY_PALETTE if lk == 'clay' else PLAIN_PALETTE

    def colour(c, face, x, y, z):
        base = pal.get(c.get('_part', 'mantle'), (150, 140, 130))
        k = {'up': 1.07, 'down': 0.84, 'north': 1.02, 'south': 0.92, 'east': 0.97, 'west': 0.95}.get(face, 1.0)
        if face == 'down' and c.get('_part') in ('wing_flight', 'wing_primary', 'tail'):
            base = mix(base, (226, 216, 198), 0.55)
            k = 0.96
        return shade(base, k)

    painted, rows, w, h = paint_faces(bones, TEXELS, colour, world_coords=False)
    return painted, png(rows), w, h


def workshop_look():
    """Hook read by tools/workshop.py snapshot & serve (perched silhouette for Compare!)."""
    return make_look(WORKSHOP_LOOK, mode='perched')


def inspect_poses():
    """Mathematically verifies box count, bone count, ground clearance, and perched vs soaring bounding boxes."""
    for label, mode in (
        ('PERCHED (folded wings)', 'perched'),
        ('SOARING (spread wings)', 'soaring'),
        ('FULL GAME RIG (both wing sets)', 'game'),
    ):
        bones = build_bones(mode=mode)
        by = {b['name']: b for b in bones}
        world_xform = {}

        def get_xf(name):
            if name in world_xform:
                return world_xform[name]
            b = by[name]
            p = b.get('parent')
            piv = b['pivot']
            R_loc = euler(b.get('rotation', [0.0, 0.0, 0.0]))
            if p is None:
                world_xform[name] = (R_loc, piv)
            else:
                Rp, pp = get_xf(p)
                parent_piv = by[p]['pivot']
                rel = vsub(piv, parent_piv)
                pos = vadd(pp, mat_vec(Rp, rel))
                world_xform[name] = (mat_mul(Rp, R_loc), pos)
            return world_xform[name]

        def bone_pts(b):
            Rb, pb = get_xf(b['name'])
            bpiv = b['pivot']
            pts = []
            for c in b.get('cubes', []):
                o, sz = c['origin'], c['size']
                crot = c.get('rotation')
                cpiv = c.get('pivot', [0, 0, 0])
                Rc = euler(crot) if crot else None
                for dx in (0, sz[0]):
                    for dy in (0, sz[1]):
                        for dz in (0, sz[2]):
                            q = [o[0] + dx, o[1] + dy, o[2] + dz]
                            if Rc is not None:
                                q = vadd(cpiv, mat_vec(Rc, vsub(q, cpiv)))
                            pts.append(vadd(pb, mat_vec(Rb, vsub(q, bpiv))))
            return pts

        all_pts = [pt for b in bones for pt in bone_pts(b)]
        xs = [p[0] for p in all_pts]
        ys = [p[1] for p in all_pts]
        zs = [p[2] for p in all_pts]
        ncubes = sum(len(b.get('cubes', [])) for b in bones)
        print(f'=== {label}: {len(bones)} bones, {ncubes}/400 boxes ===')
        print(f'  Bounds px: X=[{min(xs):.1f}, {max(xs):.1f}] (W={max(xs)-min(xs):.1f}), '
              f'Y=[{min(ys):.2f}, {max(ys):.1f}] (H={max(ys)-min(ys):.1f}), '
              f'Z=[{min(zs):.1f}, {max(zs):.1f}] (L={max(zs)-min(zs):.1f})')
        print(f'  In-game @0.5x: W={(max(xs)-min(xs))/32:.2f}b, H={(max(ys)-min(ys))/32:.2f}b, L={(max(zs)-min(zs))/32:.2f}b')
        for bname in ('head', 'wing_fold_left', 'wing_fold_tip_left', 'wing_upper_left', 'wing_fore_left',
                      'wing_hand_left', 'wing_tips_left', 'tail_c', 'leg_tarsus_left', 'leg_talon2_left'):
            if bname in by:
                pts = bone_pts(by[bname])
                if pts:
                    print(f'    {bname:18s}: X=[{min(p[0] for p in pts):+5.1f},{max(p[0] for p in pts):+5.1f}] '
                          f'Y=[{min(p[1] for p in pts):+5.1f},{max(p[1] for p in pts):+5.1f}] '
                          f'Z=[{min(p[2] for p in pts):+5.1f},{max(p[2] for p in pts):+5.1f}]')


if __name__ == '__main__':
    inspect_poses()
