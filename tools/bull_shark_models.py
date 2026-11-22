"""The Bull Shark (Carcharhinus leucas) model maker (M9 river zones & savanna life; Fish family, 2026-11-22).

Workshop Stage 4c (v13): Locked Subtle-Lean Proportions (Normalized to 5.5 Blocks = 88 px) + Hollow 3D Mouth Overhaul
+ 3 Realistic Shark-Teeth Variations!
- Locked user-approved proportions:
  * Subtle Lean silhouette (1.08x length / 0.93x thickness aspect ratio = 6.0:1 slenderness), uniformly normalized to
    exact 88.0 px (5.50 blocks) base length so in-game adults scale cleanly from 5.0b (0.91x) up to 7.0b (1.27x).
  * Razor-smooth 3D-rotated 2-segment pectoral wings & smooth rotated pelvic fins (zero staircase steps).
- Complete Hollow 3D Shark Mouth Overhaul (zero gray body clipping inside the open mouth!):
  * Carved out a true hollow cranial arch above the palate + left/right muscular cheeks + fleshy labial mouth-corner
    folds + deep dark-crimson recessed pharynx/throat cavern behind the jaw hinge.
  * Sculpted vaulted pink upper palate with transverse palatal rugae, curved parabolic U-shaped upper & lower gum
    ridges, recessed lower mouth floor trough, and 3-tier muscular rounded shark tongue (`tongue` bone).
- 3 Creative Realistic Shark-Teeth Variations (`TEETH_STYLE`):
  1. `blade45`        — Broad Bull-Shark Triangles: smooth 45-deg rotated diagonal triangular blades + 2nd reserve row.
  2. `serrated_fangs` — Serrated Apex Fangs: taller, razor-sharp acute curved fangs sculpted from 3 nested 45-deg
                        diamonds with natural serrated cutting notches + glistening 2nd conveyor row (recommended!).
  3. `serrated_voxel` — Crafted Voxel Saw-Teeth: pure 4-tier inward-curved Minecraft voxel pyramids + 2nd reserve row.

Units are model pixels (16 a block): head toward -Z, up +Y, left +X, lowest pectoral tip ~ y = 0, centerline x = 0.

    python tools/workshop.py snapshot bull_shark --note "Stage 4c v13: hollow mouth + realistic shark teeth"
    python tools/workshop.py versions bull_shark
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (  # noqa: E402
    cube, mix, shade, smooth, curve, clip, paint_faces, png, geo, animations, write_assets,
    turn_x, turn_y, turn_z, mat_mul, mat_vec, rot,
)

# --- locked proportions (Var 1 Subtle Lean, normalized cleanly to exact 88.0 px = 5.50 blocks!) ----------------------
LENGTH = 88.0           # final exported length = 88.0 px (5.50 blocks) after NORM_SCALE!
LEN_MULT = 1.08         # Subtle Lean internal length factor (approved by user)
THIN_MULT = 0.93        # Subtle Lean internal thickness factor (approved by user)
NORM_SCALE = 1.0 / LEN_MULT   # uniform scale normalizing total length cleanly back to 88.0 px (5.50 blocks)
BODY_HW = 9.8
HUMP_H = 26.2
DORSAL_H = 37.8
PEC_LEN = 18.8
JAW_OPEN = 0.0          # preview-only lower jaw opening degrees (0 resting .. 48 wide bite)
LID_CLOSE = 0.0         # preview-only nictitating membrane blink (0 open .. 1 shut)
TEETH_STYLE = "serrated_fangs"  # "blade45" | "serrated_fangs" | "serrated_voxel"

WORKSHOP_SHAPE = [
    ("DORSAL_H",  "1st dorsal tip height (px)", 34.0, 41.0, 0.5),
    ("PEC_LEN",   "Smooth pectoral length (px)", 15.0, 23.0, 0.5),
    ("JAW_OPEN",  "Preview jaw gape (deg)",     0.0, 48.0, 4.0),
    ("LID_CLOSE", "Nictitating eyelid (0..1)",  0.0,  1.0, 0.25),
]

WORKSHOP_PARTS = {
    "head":       ["head", "lid_left", "lid_right"],
    "jaw":        ["jaw", "tongue"],
    "chest":      ["chest"],
    "dorsal 1":   ["dorsal_1"],
    "pectorals":  ["pectoral_left", "pectoral_tip_left", "pectoral_right", "pectoral_tip_right"],
    "torso":      ["torso"],
    "rear":       ["rear", "pelvic_left", "pelvic_right"],
    "peduncle":   ["peduncle", "dorsal_2", "anal_fin"],
    "tail":       ["tail_base", "tail_fin"],
}

WORKSHOP_LOOK = "clean"   # "clean" (Look A: Hero Render) or "scarred" (Look B: Veteran 6-7b Old Bull)
TEXELS = 2.0
AXIS_Y = 17.4             # canonical spine centerline height before NORM_SCALE


def oz(ox):
    """Outline x (0 upper tail tip .. 88 blunt snout tip) -> canonical z before NORM_SCALE."""
    return (LENGTH / 2.0 - ox) * LEN_MULT


def sy(y):
    """Maps canonical vertical height around spine centerline AXIS_Y using THIN_MULT."""
    hs = ((HUMP_H - 9.2) / (26.2 - 9.2)) * THIN_MULT
    return AXIS_Y + (y - AXIS_Y) * hs


def box(cubes, x0, x1, y0, y1, ox0, ox1, part, extra=None):
    lo = [min(x0, x1), min(y0, y1), min(oz(ox0), oz(ox1))]
    hi = [max(x0, x1), max(y0, y1), max(oz(ox0), oz(ox1))]
    if hi[0] - lo[0] < 0.03 or hi[1] - lo[1] < 0.03 or hi[2] - lo[2] < 0.03:
        return None
    c = cube(lo, hi, part)
    if extra:
        c.update(extra)
    cubes.append(c)
    return c


def lbox(cubes, x0, x1, y0, y1, z0, z1, part, extra=None):
    """Direct model-space box [x0..x1, y0..y1, z0..z1] (for rotated fins & rotated tooth-rack bones!)."""
    lo = [min(x0, x1), min(y0, y1), min(z0, z1)]
    hi = [max(x0, x1), max(y0, y1), max(z0, z1)]
    if hi[0] - lo[0] < 0.03 or hi[1] - lo[1] < 0.03 or hi[2] - lo[2] < 0.03:
        return None
    c = cube(lo, hi, part)
    if extra:
        c.update(extra)
    cubes.append(c)
    return c


def sym_box(cubes, hw, y0, y1, ox0, ox1, part, extra=None):
    return box(cubes, -hw, hw, y0, y1, ox0, ox1, part, extra)


def _oval_slice(cubes, ox0, ox1, y_bot, y_top, hw, belly_y=None, crest=True):
    """One crisp stepped-Minecraft oval cross-section (3-4 vertical tiers: pale belly -> wide flank -> upper -> crest)."""
    h = y_top - y_bot
    if h < 1.5 or hw < 0.8:
        sym_box(cubes, max(0.7, hw), y_bot, y_top, ox0, ox1, "body_dark")
        return
    by = belly_y if belly_y is not None else (y_bot + h * 0.30)
    by = max(y_bot + 1.0, min(y_top - 1.5, by))
    sym_box(cubes, round(hw * 0.78, 2), y_bot, by + 0.32, ox0, ox1, "belly")
    t2 = y_bot + h * 0.68
    sym_box(cubes, round(hw, 2), by - 0.22, t2, ox0, ox1, "flank")
    t3 = y_bot + h * 0.88 if crest else y_top
    sym_box(cubes, round(hw * 0.72, 2), t2 - 0.32, t3, ox0, ox1, "body_dark")
    if crest and (y_top - t3) >= 0.65:
        sym_box(cubes, round(hw * 0.44, 2), t3 - 0.22, y_top, ox0, ox1, "body_cap")


# =====================================================================================================================
# REALISTIC 3D SHARK-TOOTH BUILDERS (3 CREATIVE VARIATIONS!)
# =====================================================================================================================
def _rack_rot(yaw_deg, inward_deg, roll_deg):
    """Exact GeckoLib [rx, ry, rz] degrees for R = turn_y(yaw) @ turn_x(inward) @ turn_z(roll).
    Notes on right-handed axes (+X left, +Y up, -Z forward):
      - turn_z(roll)   rotates a front-facing tooth blade in its own plane (45 deg -> smooth diamond triangle!).
      - turn_x(inward) pitches a tooth tip backward (+Z) into the throat:
          * upper tooth pointing -Y: inward_deg < 0 bends tip toward +Z (into throat)!
          * lower tooth pointing +Y: inward_deg > 0 bends tip toward +Z (into throat)!
      - turn_y(yaw)    aims the tooth rack tangent cleanly around the parabolic U-arch of the jaw!
    """
    m = mat_mul(mat_mul(turn_y(yaw_deg), turn_x(inward_deg)), turn_z(roll_deg))
    return rot(m)


def _add_diamond_in_rack(cubes, pivot, roll_deg, u, v, w, side, thick, part):
    """Places a square diamond cube in a `roll_deg`-rotated rack bone so its center lands at (u, v, w) after roll."""
    cx, cy, cz = mat_vec(turn_z(-roll_deg), [u, v, w])
    px, py, pz = pivot[0] + cx, pivot[1] + cy, pivot[2] + cz
    hs = side * 0.5
    ht = thick * 0.5
    lbox(cubes, px - hs, px + hs, py - hs, py + hs, pz - ht, pz + ht, part)


def _add_corner_blade_in_rack(cubes, pivot, roll_deg, u_tip, v_tip, w, bw, bh, thick, corner_mode, part):
    """Places a tilted enamel blade whose exact corner meets the razor apex (u_tip, v_tip, w) after `turn_z(roll_deg)`.
    corner_mode:
      - 'upper_L': bottom-left  corner at tip (x in [0, +bw], y in [0, +bh]), roll > 0 -> left edge sweeps UP-LEFT!
      - 'upper_R': bottom-right corner at tip (x in [-bw, 0], y in [0, +bh]), roll < 0 -> right edge sweeps UP-RIGHT!
      - 'lower_L': top-left     corner at tip (x in [0, +bw], y in [-bh, 0]), roll < 0 -> left edge sweeps DOWN-LEFT!
      - 'lower_R': top-right    corner at tip (x in [-bw, 0], y in [-bh, 0]), roll > 0 -> right edge sweeps DOWN-RIGHT!
    """
    tx, ty, tz = mat_vec(turn_z(-roll_deg), [u_tip, v_tip, w])
    px, py, pz = pivot[0] + tx, pivot[1] + ty, pivot[2] + tz
    ht = thick * 0.5
    if corner_mode == "upper_L":
        lbox(cubes, px, px + bw, py, py + bh, pz - ht, pz + ht, part)
    elif corner_mode == "upper_R":
        lbox(cubes, px - bw, px, py, py + bh, pz - ht, pz + ht, part)
    elif corner_mode == "lower_L":
        lbox(cubes, px, px + bw, py - bh, py, pz - ht, pz + ht, part)
    elif corner_mode == "lower_R":
        lbox(cubes, px - bw, px, py - bh, py, pz - ht, pz + ht, part)


def _build_teeth_bones(style, ws, inv_l, head_c, jaw_c):
    """Builds the selected shark-teeth variation, returning extra static tooth-rack child bones (parented to head/jaw)."""
    extra_bones = []

    # Parabolic jaw arch zone specs: (name_suffix, yaw_deg, pivot_x_unscaled, pivot_ox, [(u_offset, size_scale), ...])
    # Notice: yaw_deg > 0 rotates front face (-Z) to face toward -X? Wait: verify turn_y(yaw) on (0,0,-1)!
    # turn_y(d) @ (0, 0, -1) = (-sin d, 0, -cos d).
    # So for the LEFT side of the jaw (+X, facing outward toward +X / -Z), we want (-sin d) > 0 -> yaw_deg < 0!
    # And for the RIGHT side of the jaw (-X, facing outward toward -X / -Z), we want (-sin d) < 0 -> yaw_deg > 0!
    # Also check tangent +X axis: turn_y(d) @ (+1, 0, 0) = (+cos d, 0, -sin d).
    # When yaw < 0 (left side): +u goes toward (+X, +Z) = smoothly BACKWARD along the left jaw arch! Perfect!
    # When yaw > 0 (right side): -u goes toward (-X, +Z) = smoothly BACKWARD along the right jaw arch! Perfect!

    y_up_gum = sy(14.28)
    y_lo_gum = sy(12.38)

    if style == "blade45":
        # -------------------------------------------------------------------------------------------------------------
        # VARIATION 1: BROAD BULL-SHARK TRIANGLES (Smooth 45-deg Diamond Blades + 2nd Reserve Row)
        # Authentic broad equilateral-to-90-deg Carcharhinus leucas cutting triangles curving cleanly along the U-arch!
        # -------------------------------------------------------------------------------------------------------------
        upper_zones = [
            ("ut_front",   0.0,  0.00, 80.15, [(-1.35, 0.94), (0.0, 1.00), (1.35, 0.94)],
                                              [(-0.72, 0.72), (0.72, 0.72)]),
            ("ut_fl",    -34.0,  2.88, 78.80, [(-0.56, 0.90), (0.58, 0.86)],
                                              [(0.02, 0.66)]),
            ("ut_fr",     34.0, -2.88, 78.80, [( 0.56, 0.90), (-0.58, 0.86)],
                                              [(-0.02, 0.66)]),
            ("ut_ml",    -64.0,  4.18, 76.55, [(-0.56, 0.82), (0.52, 0.76)],
                                              [(0.00, 0.60)]),
            ("ut_mr",     64.0, -4.18, 76.55, [( 0.56, 0.82), (-0.52, 0.76)],
                                              [(0.00, 0.60)]),
            ("ut_rl",    -82.0,  4.68, 74.20, [(-0.46, 0.68), (0.44, 0.60)],
                                              [(0.00, 0.48)]),
            ("ut_rr",     82.0, -4.68, 74.20, [( 0.46, 0.68), (-0.44, 0.60)],
                                              [(0.00, 0.48)]),
        ]
        for bname, yaw, px_u, pox, front_row, back_row in upper_zones:
            piv = [round(px_u * ws, 3), round(y_up_gum, 3), round(oz(pox), 3)]
            roll = 45.0 + (-5.0 if yaw < -20 else (5.0 if yaw > 20 else 0.0))
            cubes = []
            for u, sc in front_row:
                s = 1.32 * sc
                # Broad classic bull-shark cutting triangles emerging cleanly out of the pink gum!
                _add_diamond_in_rack(cubes, piv, roll, u * ws, -0.26 * sc, 0.0, s, 0.25, "tooth_blade")
            for u, sc in back_row:
                s = 0.96 * sc
                _add_diamond_in_rack(cubes, piv, roll, u * ws, -0.06, 0.68, s, 0.20, "tooth_reserve")
            extra_bones.append({
                "name": bname, "parent": "head", "pivot": piv,
                "rotation": _rack_rot(yaw, -13.0, roll), "cubes": cubes,
            })

        lower_zones = [
            ("lt_front",   0.0,  0.00, 79.40, [(-1.02, 0.94), (0.0, 1.00), (1.02, 0.94)],
                                              [(-0.52, 0.70), (0.52, 0.70)]),
            ("lt_fl",    -36.0,  2.26, 78.15, [(-0.46, 0.88), (0.48, 0.84)],
                                              [(0.00, 0.64)]),
            ("lt_fr",     36.0, -2.26, 78.15, [( 0.46, 0.88), (-0.48, 0.84)],
                                              [(0.00, 0.64)]),
            ("lt_sl",    -72.0,  3.35, 75.75, [(-0.92, 0.80), (0.00, 0.74), (0.86, 0.64)],
                                              [(-0.42, 0.54), (0.42, 0.50)]),
            ("lt_sr",     72.0, -3.35, 75.75, [( 0.92, 0.80), (0.00, 0.74), (-0.86, 0.64)],
                                              [( 0.42, 0.54), (-0.42, 0.50)]),
        ]
        for bname, yaw, px_u, pox, front_row, back_row in lower_zones:
            piv = [round(px_u * ws, 3), round(y_lo_gum, 3), round(oz(pox), 3)]
            roll = 45.0
            cubes = []
            for u, sc in front_row:
                s = 1.04 * sc
                _add_diamond_in_rack(cubes, piv, roll, u * ws, 0.28 * sc, 0.0, s, 0.22, "tooth_blade")
            for u, sc in back_row:
                s = 0.78 * sc
                _add_diamond_in_rack(cubes, piv, roll, u * ws, 0.12, 0.60, s, 0.18, "tooth_reserve")
            extra_bones.append({
                "name": bname, "parent": "jaw", "pivot": piv,
                "rotation": _rack_rot(yaw, +18.0, roll), "cubes": cubes,
            })

    elif style == "serrated_fangs":
        # -------------------------------------------------------------------------------------------------------------
        # VARIATION 2: SERRATED APEX FANGS (Recommended!)
        # Taller, razor-sharp ~44-deg curved shark fangs sculpted from 3 seamlessly nested 45-deg diamonds:
        #   * mathematically razor-sharp 0-width tip (ZERO blunt corners, ZERO bow-tie artifacts!)
        #   * subtle micro-serrated notches along the tall tapered cutting edges + slight backward hook into throat!
        #   * clean 35-bone rig + full glistening 2nd conveyor-belt reserve row!
        # -------------------------------------------------------------------------------------------------------------
        upper_zones = [
            ("ut_front",   0.0,  0.00, 80.12,  0.0, [(-1.34, 0.95), (0.0, 1.04), (1.34, 0.95)],
                                                    [(-0.70, 0.76), (0.70, 0.76)]),
            ("ut_fl",    -34.0,  2.86, 78.80, -6.0, [(-0.56, 0.92), (0.58, 0.86)],
                                                    [(0.02, 0.70)]),
            ("ut_fr",     34.0, -2.86, 78.80,  6.0, [( 0.56, 0.92), (-0.58, 0.86)],
                                                    [(-0.02, 0.70)]),
            ("ut_ml",    -64.0,  4.14, 76.55, -7.5, [(-0.56, 0.82), (0.54, 0.76)],
                                                    [(0.00, 0.64)]),
            ("ut_mr",     64.0, -4.14, 76.55,  7.5, [( 0.56, 0.82), (-0.54, 0.76)],
                                                    [(0.00, 0.64)]),
            ("ut_rl",    -82.0,  4.62, 74.20, -7.5, [(-0.46, 0.68), (0.44, 0.60)],
                                                    [(0.00, 0.52)]),
            ("ut_rr",     82.0, -4.62, 74.20,  7.5, [( 0.46, 0.68), (-0.44, 0.60)],
                                                    [(0.00, 0.52)]),
        ]
        for bname, yaw, px_u, pox, hook, front_row, back_row in upper_zones:
            piv = [round(px_u * ws, 3), round(y_up_gum, 3), round(oz(pox), 3)]
            roll = 45.0 + hook
            cubes = []
            # Subtle lateral hook shift toward jaw corners for realistic oblique bull-shark teeth
            lat_dir = 0.0 if abs(yaw) < 10 else (1.0 if yaw < 0 else -1.0)
            for u, sc in front_row:
                drop = 1.46 * sc
                # Tier 1 (Tip): razor-sharp ivory apex curved slightly backward into the throat!
                _add_diamond_in_rack(cubes, piv, roll, u * ws + lat_dir * 0.09 * sc, -drop + 0.38 * sc, 0.10 * sc, 0.54 * sc, 0.20, "tooth_tip")
                # Tier 2 (Mid serrated blade): taller acute taper bridging tip cleanly to root!
                _add_diamond_in_rack(cubes, piv, roll, u * ws + lat_dir * 0.04 * sc, -drop + 0.76 * sc, 0.04 * sc, 0.80 * sc, 0.24, "tooth_blade")
                # Tier 3 (Broad root): embedded solidly into the pink upper gum ridge!
                _add_diamond_in_rack(cubes, piv, roll, u * ws,                       -drop + 1.16 * sc, 0.00,      1.04 * sc, 0.27, "tooth_blade")
            for u, sc in back_row:
                _add_diamond_in_rack(cubes, piv, roll, u * ws, -0.24 * sc, 0.68, 0.88 * sc, 0.20, "tooth_reserve")
            extra_bones.append({
                "name": bname, "parent": "head", "pivot": piv,
                "rotation": _rack_rot(yaw, -14.0, roll), "cubes": cubes,
            })

        # Lower grasping daggers: sleek, needle-sharp 3-tier nested diamonds pitched +20 deg backward into the throat!
        lower_zones = [
            ("lt_front",   0.0,  0.00, 79.40, [(-1.02, 0.94), (0.0, 1.02), (1.02, 0.94)],
                                              [(-0.52, 0.70), (0.52, 0.70)]),
            ("lt_fl",    -36.0,  2.26, 78.15, [(-0.46, 0.88), (0.48, 0.82)],
                                              [(0.00, 0.64)]),
            ("lt_fr",     36.0, -2.26, 78.15, [( 0.46, 0.88), (-0.48, 0.82)],
                                              [(0.00, 0.64)]),
            ("lt_sl",    -72.0,  3.35, 75.75, [(-0.90, 0.80), (0.00, 0.72), (0.84, 0.64)],
                                              [(-0.42, 0.54), (0.42, 0.50)]),
            ("lt_sr",     72.0, -3.35, 75.75, [( 0.90, 0.80), (0.00, 0.72), (-0.84, 0.64)],
                                              [( 0.42, 0.54), (-0.42, 0.50)]),
        ]
        for bname, yaw, px_u, pox, front_row, back_row in lower_zones:
            piv = [round(px_u * ws, 3), round(y_lo_gum, 3), round(oz(pox), 3)]
            roll = 45.0
            cubes = []
            for u, sc in front_row:
                rise = 1.30 * sc
                # Sleek needle-sharp grasping lower fangs (3 nested tapering diamonds curving inward!)
                _add_diamond_in_rack(cubes, piv, roll, u * ws, rise - 0.28 * sc, 0.10 * sc, 0.40 * sc, 0.18, "tooth_tip")
                _add_diamond_in_rack(cubes, piv, roll, u * ws, rise - 0.62 * sc, 0.04 * sc, 0.60 * sc, 0.21, "tooth_blade")
                _add_diamond_in_rack(cubes, piv, roll, u * ws, rise - 0.96 * sc, 0.00,      0.78 * sc, 0.24, "tooth_blade")
            for u, sc in back_row:
                _add_diamond_in_rack(cubes, piv, roll, u * ws, 0.22 * sc, 0.60, 0.70 * sc, 0.17, "tooth_reserve")
            extra_bones.append({
                "name": bname, "parent": "jaw", "pivot": piv,
                "rotation": _rack_rot(yaw, +20.0, roll), "cubes": cubes,
            })

    elif style == "serrated_voxel":
        # -------------------------------------------------------------------------------------------------------------
        # VARIATION 3: CRAFTED VOXEL SAW-TEETH (Pure 4-Tier Inward-Curved Minecraft Pyramids + Reserve Row!)
        # Zero rotated tooth bones — tightly packed 4-tier serrated pyramids curving smoothly back into the mouth!
        # -------------------------------------------------------------------------------------------------------------
        upper_specs = [
            ( 0.00, 80.15, 1.05, 0.42, 1.24, True),
            ( 1.15, 79.95, 1.02, 0.42, 1.22, True), (-1.15, 79.95, 1.02, 0.42, 1.22, True),
            ( 2.25, 79.35, 0.96, 0.46, 1.16, True), (-2.25, 79.35, 0.96, 0.46, 1.16, True),
            ( 3.22, 78.40, 0.86, 0.56, 1.08, True), (-3.22, 78.40, 0.86, 0.56, 1.08, True),
            ( 4.00, 77.20, 0.70, 0.74, 0.98, False), (-4.00, 77.20, 0.70, 0.74, 0.98, False),
            ( 4.50, 75.85, 0.54, 0.80, 0.88, False), (-4.50, 75.85, 0.54, 0.80, 0.88, False),
            ( 4.78, 74.45, 0.48, 0.74, 0.74, False), (-4.78, 74.45, 0.48, 0.74, 0.74, False),
        ]
        u_tiers = [
            (0.00, 0.28, 1.00, 0.00, "tooth_blade"),
            (0.24, 0.55, 0.72, -0.10, "tooth_blade"),
            (0.50, 0.80, 0.45, -0.22, "tooth_tip"),
            (0.76, 1.00, 0.22, -0.34, "tooth_tip"),
        ]
        for tx, tox, tw, td, drop, is_front in upper_specs:
            td_s = td * inv_l
            for f0, f1, wf, curve_ox, part in u_tiers:
                if is_front:
                    wx = tw * wf * 0.5
                    dz = max(0.14, td_s * (0.55 + 0.45 * wf)) * 0.5
                    cx = tx * ws
                    cox = tox + curve_ox * inv_l
                else:
                    wx = max(0.14, tw * (0.55 + 0.45 * wf)) * 0.5
                    dz = td_s * wf * 0.5
                    cx = (tx - math.copysign(abs(curve_ox) * 0.9, tx)) * ws
                    cox = tox
                box(head_c, cx - wx * ws, cx + wx * ws,
                    sy(14.22 - drop * f1), sy(14.22 - drop * f0),
                    cox - dz, cox + dz, part)
        for rx, rox, rdrop in [
            (0.0, 79.30, 0.78), (1.28, 79.05, 0.74), (-1.28, 79.05, 0.74),
            (2.42, 78.25, 0.68), (-2.42, 78.25, 0.68), (3.30, 77.10, 0.62), (-3.30, 77.10, 0.62),
            (3.90, 75.65, 0.54), (-3.90, 75.65, 0.54),
        ]:
            box(head_c, (rx - 0.34) * ws, (rx + 0.34) * ws, sy(14.26 - rdrop * 0.55), sy(14.36),
                rox - 0.22 * inv_l, rox + 0.22 * inv_l, "tooth_reserve")
            box(head_c, (rx - 0.16) * ws, (rx + 0.16) * ws, sy(14.26 - rdrop), sy(14.26 - rdrop * 0.48),
                rox - 0.28 * inv_l, rox + 0.12 * inv_l, "tooth_reserve")

        lower_specs = [
            ( 0.00, 79.40, 0.68, 0.38, 1.08, True),
            ( 0.94, 79.20, 0.66, 0.38, 1.06, True), (-0.94, 79.20, 0.66, 0.38, 1.06, True),
            ( 1.88, 78.60, 0.62, 0.44, 1.00, True), (-1.88, 78.60, 0.62, 0.44, 1.00, True),
            ( 2.66, 77.70, 0.54, 0.54, 0.92, False), (-2.66, 77.70, 0.54, 0.54, 0.92, False),
            ( 3.22, 76.45, 0.46, 0.58, 0.82, False), (-3.22, 76.45, 0.46, 0.58, 0.82, False),
            ( 3.45, 75.10, 0.42, 0.54, 0.72, False), (-3.45, 75.10, 0.42, 0.54, 0.72, False),
        ]
        l_tiers = [
            (0.00, 0.36, 1.00, 0.00, "tooth_blade"),
            (0.30, 0.70, 0.58, -0.14, "tooth_tip"),
            (0.64, 1.00, 0.25, -0.28, "tooth_tip"),
        ]
        for tx, tox, tw, td, rise, is_front in lower_specs:
            td_s = td * inv_l
            for f0, f1, wf, curve_ox, part in l_tiers:
                if is_front:
                    wx = tw * wf * 0.5
                    dz = max(0.12, td_s * (0.55 + 0.45 * wf)) * 0.5
                    cx = tx * ws
                    cox = tox + curve_ox * inv_l
                else:
                    wx = max(0.12, tw * (0.55 + 0.45 * wf)) * 0.5
                    dz = td_s * wf * 0.5
                    cx = (tx - math.copysign(abs(curve_ox) * 0.9, tx)) * ws
                    cox = tox
                box(jaw_c, cx - wx * ws, cx + wx * ws,
                    sy(12.35 + rise * f0), sy(12.35 + rise * f1),
                    cox - dz, cox + dz, part)
        for rx, rox, rrise in [
            (0.54, 78.60, 0.58), (-0.54, 78.60, 0.58), (1.72, 77.95, 0.54), (-1.72, 77.95, 0.54),
            (2.75, 76.85, 0.48), (-2.75, 76.85, 0.48),
        ]:
            box(jaw_c, (rx - 0.16) * ws, (rx + 0.16) * ws, sy(12.32), sy(12.32 + rrise),
                rox - 0.18 * inv_l, rox + 0.16 * inv_l, "tooth_reserve")

    return extra_bones


def _normalize_bones(bones, s):
    """Uniformly scales all bone pivots and cube origins/sizes by s (normalizing Subtle Lean cleanly to 88.0 px = 5.5b)."""
    for b in bones:
        b["pivot"] = [round(v * s, 3) for v in b["pivot"]]
        for c in b.get("cubes", []):
            c["origin"] = [round(v * s, 3) for v in c["origin"]]
            c["size"] = [round(v * s, 3) for v in c["size"]]


def build_bones(teeth_style=None):
    """Builds the Bull Shark (Var 1 Subtle Lean normalized to 5.5 blocks = 88 px, smooth fins, hollow 3D mouth)."""
    style = teeth_style or TEETH_STYLE
    ws = (BODY_HW / 9.8) * THIN_MULT
    inv_l = 1.0 / max(0.8, LEN_MULT)

    # =================================================================================================================
    # 1. HEAD (ox = 61.0..88.0): Blunt Snout + HOLLOW 3D MOUTH CAVERN (ZERO GRAY CLIPPING!) + Brows, Eyes & 5 Gills
    # =================================================================================================================
    head_c = []
    # --- Blunt Bull Shark Snout Tip & Overhang (ox = 80.5..88.0) ---
    sym_box(head_c, round(4.2 * ws, 2), sy(16.6), sy(19.8), 84.5, 88.0, "body_dark")
    sym_box(head_c, round(3.5 * ws, 2), sy(15.6), sy(16.9), 83.8, 87.4, "belly")
    sym_box(head_c, round(5.7 * ws, 2), sy(15.6), sy(21.2), 80.8, 85.2, "flank")
    sym_box(head_c, round(4.2 * ws, 2), sy(20.6), sy(22.2), 80.2, 84.4, "body_cap")
    # Curved pale upper-snout overhang & front upper lip framing the tucked pink gums cleanly!
    sym_box(head_c, round(4.8 * ws, 2), sy(14.55), sy(16.0), 80.5, 84.5, "belly")
    sym_box(head_c, round(3.2 * ws, 2), sy(14.20), sy(15.05), 80.45, 81.65, "belly")
    # Recessed underside nostrils
    for sgn in (-1, 1):
        box(head_c, sgn * round(2.2 * ws, 2), sgn * round(3.15 * ws, 2), sy(15.1), sy(15.65), 84.2, 85.6, "nostril")

    # --- Forehead / Eye Cranium ABOVE the Vaulted Palate (ox = 75.8..81.4, strictly y >= sy(15.6)!) ---
    sym_box(head_c, round(7.0 * ws, 2), sy(15.6), sy(22.0), 75.8, 81.4, "flank")
    sym_box(head_c, round(5.2 * ws, 2), sy(21.4), sy(23.4), 75.2, 80.8, "body_cap")
    # Left & right curved pale upper-lip arches framing the outside of the mouth (center stays 100% hollow!)
    for sgn in (-1, 1):
        box(head_c, sgn * round(4.75 * ws, 2), sgn * round(6.35 * ws, 2), sy(14.05), sy(15.95), 75.8, 80.9, "belly")
        box(head_c, sgn * round(2.70 * ws, 2), sgn * round(4.95 * ws, 2), sy(14.15), sy(15.20), 79.4, 81.05, "belly")

    # Predatory overhanging brow + glossy dark eye + crisp square specular glint
    eye_ox = 78.6
    eye_half = 1.25 * inv_l
    glint_sz = 0.68 * inv_l
    for sgn in (-1, 1):
        box(head_c, sgn * round(6.3 * ws, 2), sgn * round(7.75 * ws, 2), sy(19.4), sy(20.6),
            eye_ox - 1.95 * inv_l, eye_ox + 2.05 * inv_l, "brow")
        box(head_c, sgn * round(6.6 * ws, 2), sgn * round(7.62 * ws, 2), sy(17.4), sy(19.5),
            eye_ox - eye_half, eye_ox + eye_half, "eye")
        box(head_c, sgn * round(7.54 * ws, 2), sgn * round(7.74 * ws, 2), sy(18.6), sy(19.3),
            eye_ox + eye_half - glint_sz - 0.25 * inv_l, eye_ox + eye_half - 0.25 * inv_l, "eye_glint")

    # --- Mid-Head HOLLOW Cranium Arch (ox = 71.8..76.4) + Throat Shell (ox = 69.0..72.2): ZERO GRAY WALL IN MOUTH! ---
    sym_box(head_c, round(8.2 * ws, 2), sy(15.6), sy(20.9), 71.8, 76.4, "flank")
    sym_box(head_c, round(5.9 * ws, 2), sy(20.5), sy(23.0), 71.8, 76.4, "body_dark")
    sym_box(head_c, round(3.6 * ws, 2), sy(22.6), sy(24.2), 71.8, 76.4, "body_cap")
    sym_box(head_c, round(8.5 * ws, 2), sy(15.6), sy(21.4), 69.0, 72.2, "flank")
    sym_box(head_c, round(6.1 * ws, 2), sy(21.0), sy(23.6), 69.0, 72.2, "body_dark")
    sym_box(head_c, round(3.8 * ws, 2), sy(23.2), sy(24.8), 69.0, 72.2, "body_cap")

    # Left & right outer cheek / jaw-joint walls + watertight muscular crimson labial mouth-corner curtains!
    for sgn in (-1, 1):
        box(head_c, sgn * round(5.55 * ws, 2), sgn * round(8.2 * ws, 2), sy(13.9), sy(16.0), 71.8, 76.4, "flank")
        # Tapered cheek corner (stays high toward front ox=74..76.2 so open jaw looks sleek & organic!)
        box(head_c, sgn * round(5.45 * ws, 2), sgn * round(7.1 * ws, 2), sy(13.2), sy(14.3), 73.6, 76.2, "belly")
        box(head_c, sgn * round(5.45 * ws, 2), sgn * round(7.2 * ws, 2), sy(11.4), sy(14.3), 71.6, 73.8, "belly")
        box(head_c, sgn * round(5.55 * ws, 2), sgn * round(8.5 * ws, 2), sy(13.8), sy(16.0), 69.0, 72.2, "flank")
        box(head_c, sgn * round(5.35 * ws, 2), sgn * round(7.2 * ws, 2), sy(10.0), sy(14.1), 69.0, 72.2, "belly")
        # Fleshy rose-crimson labial corner webs framing the jaw hinge cleanly!
        box(head_c, sgn * round(4.55 * ws, 2), sgn * round(5.65 * ws, 2), sy(10.8), sy(15.3), 70.4, 73.8, "mouth_corner")
        box(head_c, sgn * round(4.65 * ws, 2), sgn * round(5.55 * ws, 2), sy(12.6), sy(15.3), 73.4, 75.4, "mouth_corner")

    # Outer chin-throat belly skin strictly BELOW the jaw hinge (y <= sy(10.9), behind jaw!)
    sym_box(head_c, round(6.2 * ws, 2), sy(9.6), sy(10.9), 69.0, 72.0, "belly")

    # --- Deep Recessed Dark-Crimson Pharynx / Throat Cavern (recessed behind ox=72 down to ox=68.8!) ---
    sym_box(head_c, round(5.0 * ws, 2), sy(10.5), sy(15.7), 68.6, 69.8, "throat_deep")
    sym_box(head_c, round(4.9 * ws, 2), sy(15.0), sy(15.9), 69.2, 72.2, "throat")
    sym_box(head_c, round(4.9 * ws, 2), sy(10.5), sy(11.4), 69.2, 72.4, "throat")
    for sgn in (-1, 1):
        box(head_c, sgn * round(4.05 * ws, 2), sgn * round(5.5 * ws, 2), sy(10.7), sy(15.4), 69.2, 72.4, "throat")

    # --- Vaulted Sculpted Pink Upper Palate + Palatal Rugae Ridges + Tucked Parabolic Upper Gum Arch ---
    sym_box(head_c, round(5.1 * ws, 2), sy(15.25), sy(16.05), 71.8, 79.4, "palate")
    sym_box(head_c, round(3.8 * ws, 2), sy(15.00), sy(15.85), 78.8, 80.6, "palate")
    for rox, rhw in [(74.0, 4.1), (76.2, 3.8), (78.2, 3.1)]:
        sym_box(head_c, round(rhw * ws, 2), sy(15.02), sy(15.38), rox - 0.35, rox + 0.35, "palate_ridge")
    # Curved parabolic U-shaped upper gum ridge tucked cleanly just inside the pale upper lip!
    sym_box(head_c, round(2.70 * ws, 2), sy(14.12), sy(15.22), 79.3, 80.55, "gum")
    for sgn in (-1, 1):
        box(head_c, sgn * round(2.00 * ws, 2), sgn * round(4.15 * ws, 2), sy(14.08), sy(15.20), 77.6, 79.85, "gum")
        box(head_c, sgn * round(3.45 * ws, 2), sgn * round(4.95 * ws, 2), sy(14.02), sy(15.15), 75.2, 78.05, "gum")
        box(head_c, sgn * round(4.05 * ws, 2), sgn * round(5.25 * ws, 2), sy(14.02), sy(15.10), 72.5, 75.55, "gum")

    # --- Solid Posterior Head / Gill-Zone Slices (strictly BEHIND ox = 69.4 so ZERO gray body enters mouth!) ---
    _oval_slice(head_c, 65.8, 69.4, sy(9.6), sy(25.2), 8.65 * ws, belly_y=sy(14.0), crest=True)
    _oval_slice(head_c, 61.0, 66.4, sy(9.3), sy(25.9), 9.05 * ws, belly_y=sy(13.8), crest=True)

    # --- 5 Recessed 3D Gill Slits + 6 Sculpted Muscular Gill Ribs ---
    gill_oxs = [70.4, 68.5, 66.6, 64.7, 62.8]
    gill_hs  = [6.6,  7.4,  7.9,  7.5,  6.7]
    for sgn in (-1, 1):
        for idx, (gox, gh) in enumerate(zip(gill_oxs, gill_hs)):
            t_flank = (gox - 61.0) / (71.8 - 61.0)
            local_hw = (9.20 - 0.65 * t_flank) * ws
            y_mid = sy(16.0 - idx * 0.14)
            gh_s = gh * THIN_MULT
            y_lo = y_mid - gh_s * 0.5
            y_hi = y_mid + gh_s * 0.5
            box(head_c, sgn * (local_hw - 0.62 * THIN_MULT), sgn * (local_hw + 0.24 * THIN_MULT),
                y_mid - 0.16, y_hi, gox - 0.30, gox + 0.42, "gill_slit")
            box(head_c, sgn * (local_hw - 0.62 * THIN_MULT), sgn * (local_hw + 0.24 * THIN_MULT),
                y_lo, y_mid + 0.16, gox - 0.54, gox + 0.18, "gill_slit")

        rib_oxs = [71.35, 69.45, 67.55, 65.65, 63.75, 61.80]
        for idx, rox in enumerate(rib_oxs):
            t_flank = max(0.0, min(1.0, (rox - 61.0) / (71.8 - 61.0)))
            local_hw = (9.20 - 0.65 * t_flank) * ws
            rib_w = 0.50 if 0 < idx < 5 else 0.68
            box(head_c, sgn * (local_hw - 0.12 * THIN_MULT), sgn * (local_hw + 0.60 * THIN_MULT),
                sy(14.1), sy(20.6 - idx * 0.10), rox - rib_w, rox + rib_w, "gill_rib")
            box(head_c, sgn * (local_hw - 0.16 * THIN_MULT), sgn * (local_hw + 0.52 * THIN_MULT),
                sy(11.6), sy(14.3), rox - rib_w - 0.16, rox + rib_w - 0.16, "gill_rib_belly")

    def build_lid(sgn):
        c = []
        x_out = (6.25 + LID_CLOSE * 1.52) * ws
        y_off = LID_CLOSE * 1.95
        box(c, sgn * round(x_out - 0.32 * ws, 2), sgn * round(x_out, 2),
            sy(15.6 + y_off), sy(17.55 + y_off), eye_ox - eye_half + 0.05, eye_ox + eye_half - 0.05, "lid")
        return c

    # =================================================================================================================
    # 1b. SCULPTED RECESSED HINGED LOWER JAW + U-GUM ARCH + 3-TIER MUSCULAR SHARK TONGUE (`basihyal`)
    # =================================================================================================================
    jaw_c = []
    # Sleek curved pale outer chin shell (strictly ox >= 71.5 so hinge rotates cleanly without back-poke!)
    sym_box(jaw_c, round(5.2 * ws, 2), sy(9.7),  sy(11.35), 71.5, 75.8, "belly")
    sym_box(jaw_c, round(4.5 * ws, 2), sy(10.0), sy(11.45), 75.4, 78.6, "belly")
    sym_box(jaw_c, round(3.3 * ws, 2), sy(10.5), sy(11.75), 78.2, 80.7, "belly")
    sym_box(jaw_c, round(2.3 * ws, 2), sy(11.0), sy(12.25), 80.0, 81.25, "belly")
    # Sculpted outer pale lower-lip rim wrapping smoothly around the sides & front
    for sgn in (-1, 1):
        box(jaw_c, sgn * round(4.25 * ws, 2), sgn * round(5.30 * ws, 2), sy(11.0), sy(12.35), 71.7, 75.8, "belly")
        box(jaw_c, sgn * round(3.45 * ws, 2), sgn * round(4.65 * ws, 2), sy(11.1), sy(12.40), 75.4, 78.5, "belly")
        box(jaw_c, sgn * round(2.05 * ws, 2), sgn * round(3.65 * ws, 2), sy(11.3), sy(12.35), 78.0, 80.35, "belly")
    # Recessed dark-pink floor trough + hinged back-throat apron bridging into the gullet (zero gap at 48-deg gape!)
    sym_box(jaw_c, round(4.3 * ws, 2), sy(11.15), sy(11.82), 71.4, 79.3, "throat")
    sym_box(jaw_c, round(4.0 * ws, 2), sy(11.10), sy(12.25), 70.4, 72.4, "throat_deep")
    # Curved parabolic U-shaped lower gum ridge (hugging the lower teeth, leaving a real trough for the tongue!)
    sym_box(jaw_c, round(2.25 * ws, 2), sy(11.60), sy(12.50), 79.0, 80.45, "gum")
    for sgn in (-1, 1):
        box(jaw_c, sgn * round(1.75 * ws, 2), sgn * round(3.65 * ws, 2), sy(11.60), sy(12.50), 77.3, 79.55, "gum")
        box(jaw_c, sgn * round(3.15 * ws, 2), sgn * round(4.45 * ws, 2), sy(11.60), sy(12.45), 73.0, 77.75, "gum")

    # 3-tier sculpted muscular shark tongue (`basihyal`) nestled naturally inside the lower jaw trough!
    tongue_c = []
    sym_box(tongue_c, round(2.75 * ws, 2), sy(11.65), sy(12.65), 72.1, 76.2, "tongue")
    sym_box(tongue_c, round(2.15 * ws, 2), sy(11.72), sy(12.55), 75.8, 78.0, "tongue")
    sym_box(tongue_c, round(1.38 * ws, 2), sy(11.78), sy(12.36), 77.6, 79.1, "tongue")

    # Build the selected shark-teeth variation!
    teeth_bones = _build_teeth_bones(style, ws, inv_l, head_c, jaw_c)

    # =================================================================================================================
    # 2. CHEST + TALL 1ST DORSAL + RAZOR-SMOOTH 3D-ROTATED PECTORAL WINGS (ZERO STAIRCASE STEPS!)
    # =================================================================================================================
    chest_c = []
    _oval_slice(chest_c, 55.2, 62.4, sy(9.2), sy(26.1), 9.6 * ws, belly_y=sy(14.0), crest=True)
    _oval_slice(chest_c, 49.0, 56.0, sy(9.2), sy(26.2), 9.8 * ws, belly_y=sy(14.0), crest=True)

    d1_c = []
    d1_base = sy(25.0)
    d1_h = max(8.5, (DORSAL_H - 25.0) * (0.72 + 0.28 * THIN_MULT))
    sym_box(d1_c, 1.85 * (0.65 + 0.35 * THIN_MULT), d1_base - 0.8, d1_base + d1_h * 0.13, 44.5, 59.5, "body_cap")
    d1_tiers = [
        (0.08, 0.22, 53.0, 58.6, 44.8, 1.36, 0.80),
        (0.18, 0.34, 51.6, 56.8, 45.4, 1.22, 0.72),
        (0.30, 0.46, 50.2, 55.0, 45.8, 1.10, 0.64),
        (0.42, 0.58, 49.0, 53.2, 46.0, 0.98, 0.56),
        (0.54, 0.70, 47.8, 51.6, 46.0, 0.86, 0.50),
        (0.66, 0.81, 46.8, 50.2, 45.8, 0.76, 0.46),
        (0.77, 0.91, 45.8, 48.8, 45.5, 0.66, 0.42),
        (0.87, 1.00, 44.6, 47.6, 44.6, 0.58, 0.38),
    ]
    for f0, f1, lx0, lx1, tx0, hwl, hwt in d1_tiers:
        y0, y1 = d1_base + d1_h * f0, d1_base + d1_h * f1
        sym_box(d1_c, hwl, y0, y1, lx0, lx1, "fin_lead")
        sym_box(d1_c, hwt, y0, y1, tx0, lx0 + 0.8, "fin_tip" if f1 > 0.80 else "fin_dark")
    sym_box(d1_c, 0.54, d1_base + 0.2, d1_base + 1.9, 41.2, 45.4, "fin_tip")

    pec_pivot_x = round(8.2 * ws, 2)
    pec_pivot_y = round(sy(11.8), 2)
    pec_pivot_z = round(oz(57.6), 2)
    inner_len = round(PEC_LEN * 0.58, 2)
    outer_len = round(PEC_LEN * 0.45, 2)
    tip_pivot_x = round(pec_pivot_x + inner_len - 0.6, 2)

    def build_smooth_pec(sgn):
        c = []
        px0, py0, pz0 = sgn * pec_pivot_x, pec_pivot_y, pec_pivot_z
        lbox(c, px0 - sgn * 2.0, px0 + sgn * 3.0, py0 - 0.90, py0 + 1.12, pz0 - 4.2, pz0 + 4.8, "fin_lead")
        lbox(c, px0 - sgn * 2.0, px0 + sgn * 2.8, py0 - 1.55, py0 - 0.65, pz0 - 3.8, pz0 + 4.2, "fin_under")
        lbox(c, px0 + sgn * 0.2, px0 + sgn * inner_len, py0 - 0.35, py0 + 1.05, pz0 - 4.4, pz0 + 0.6, "fin_lead")
        lbox(c, px0 + sgn * 0.4, px0 + sgn * (inner_len - 0.2), py0 - 0.25, py0 + 0.62, pz0 + 0.1, pz0 + 4.5, "fin_dark")
        lbox(c, px0 + sgn * 0.4, px0 + sgn * (inner_len - 0.1), py0 - 0.92, py0 - 0.12, pz0 - 3.9, pz0 + 3.8, "fin_under")
        lbox(c, px0 - sgn * 0.2, px0 + sgn * 4.5, py0 - 0.55, py0 + 0.40, pz0 + 3.8, pz0 + 6.8, "fin_dark")
        return c

    def build_smooth_pec_tip(sgn):
        c = []
        tx0, py0, pz0 = sgn * tip_pivot_x, pec_pivot_y, pec_pivot_z
        m1, m2 = outer_len * 0.44, outer_len * 0.78
        lbox(c, tx0 - sgn * 0.6, tx0 + sgn * m1, py0 - 0.24, py0 + 0.84, pz0 - 4.2, pz0 + 0.2, "fin_lead")
        lbox(c, tx0 - sgn * 0.5, tx0 + sgn * m1, py0 - 0.18, py0 + 0.50, pz0 - 0.2, pz0 + 3.2, "fin_dark")
        lbox(c, tx0 - sgn * 0.5, tx0 + sgn * m1, py0 - 0.68, py0 - 0.10, pz0 - 3.7, pz0 + 2.6, "fin_under")
        lbox(c, tx0 + sgn * (m1 - 0.4), tx0 + sgn * m2, py0 - 0.22, py0 + 0.62, pz0 - 4.0, pz0 - 0.3, "fin_lead")
        lbox(c, tx0 + sgn * (m1 - 0.4), tx0 + sgn * m2, py0 - 0.18, py0 + 0.38, pz0 - 0.7, pz0 + 1.8, "fin_tip")
        lbox(c, tx0 + sgn * (m1 - 0.3), tx0 + sgn * m2, py0 - 0.48, py0 - 0.08, pz0 - 3.5, pz0 + 1.2, "fin_tip")
        lbox(c, tx0 + sgn * (m2 - 0.4), tx0 + sgn * outer_len, py0 - 0.20, py0 + 0.40, pz0 - 3.6, pz0 - 0.9, "fin_tip")
        return c

    # =================================================================================================================
    # 3. TORSO & 4. REAR TRUNK + SMOOTH ROTATED PELVIC FINS
    # =================================================================================================================
    torso_c = []
    _oval_slice(torso_c, 43.0, 50.5, sy(9.6), sy(25.4), 9.0 * ws, belly_y=sy(14.0), crest=True)
    _oval_slice(torso_c, 36.8, 44.0, sy(10.4), sy(24.0), 7.8 * ws, belly_y=sy(14.2), crest=True)

    rear_c = []
    _oval_slice(rear_c, 31.5, 37.8, sy(11.4), sy(22.6), 6.5 * ws, belly_y=sy(14.6), crest=True)
    _oval_slice(rear_c, 26.8, 32.4, sy(12.6), sy(21.4), 5.2 * ws, belly_y=sy(15.2), crest=True)

    pel_pivot_x = round(4.4 * ws, 2)
    pel_pivot_y = round(sy(11.6), 2)
    pel_pivot_z = round(oz(34.5), 2)

    def build_smooth_pelvic(sgn):
        c = []
        px0, py0, pz0 = sgn * pel_pivot_x, pel_pivot_y, pel_pivot_z
        lbox(c, px0 - sgn * 1.2, px0 + sgn * 2.4, py0 - 0.55, py0 + 0.68, pz0 - 2.4, pz0 + 2.8, "fin_lead")
        lbox(c, px0 + sgn * 0.2, px0 + sgn * 5.8, py0 - 0.25, py0 + 0.65, pz0 - 2.6, pz0 + 0.4, "fin_lead")
        lbox(c, px0 + sgn * 0.4, px0 + sgn * 5.4, py0 - 0.20, py0 + 0.38, pz0 + 0.0, pz0 + 2.8, "fin_dark")
        lbox(c, px0 + sgn * 0.4, px0 + sgn * 5.0, py0 - 0.62, py0 - 0.12, pz0 - 2.2, pz0 + 2.2, "fin_under")
        lbox(c, px0 + sgn * 4.8, px0 + sgn * 7.4, py0 - 0.28, py0 + 0.42, pz0 - 2.2, pz0 + 1.4, "fin_tip")
        lbox(c, px0 - sgn * 0.2, px0 + sgn * 2.8, py0 - 0.40, py0 + 0.28, pz0 + 2.4, pz0 + 4.6, "fin_tip")
        return c

    # =================================================================================================================
    # 5. CAUDAL PEDUNCLE + KEEL + 2ND DORSAL & ANAL FINS
    # =================================================================================================================
    ped_c = []
    _oval_slice(ped_c, 21.5, 28.0, sy(13.8), sy(20.4), 4.1 * ws, belly_y=sy(15.8), crest=False)
    _oval_slice(ped_c, 15.8, 22.2, sy(14.8), sy(19.6), 2.75 * ws, belly_y=sy(16.4), crest=False)
    for sgn in (-1, 1):
        box(ped_c, sgn * (2.4 * ws), sgn * (3.25 * ws), sy(16.6), sy(17.8), 16.2, 22.8, "body_dark")

    d2_c = []
    sym_box(d2_c, 0.98, sy(19.8), sy(21.8), 24.4, 28.2, "fin_lead")
    sym_box(d2_c, 0.80, sy(21.4), sy(23.5), 23.2, 26.8, "fin_lead")
    sym_box(d2_c, 0.64, sy(23.1), sy(25.2), 22.2, 25.0, "fin_tip")
    sym_box(d2_c, 0.56, sy(20.2), sy(22.6), 22.0, 24.8, "fin_dark")
    sym_box(d2_c, 0.48, sy(19.8), sy(21.1), 20.0, 22.8, "fin_tip")

    anal_c = []
    sym_box(anal_c, 0.90, sy(13.0), sy(14.8), 24.2, 27.6, "fin_lead")
    sym_box(anal_c, 0.72, sy(11.6), sy(13.4), 23.0, 26.0, "fin_dark")
    sym_box(anal_c, 0.58, sy(10.6), sy(12.2), 22.0, 24.4, "fin_tip")
    sym_box(anal_c, 0.48, sy(13.0), sy(14.2), 20.4, 23.4, "fin_tip")

    # =================================================================================================================
    # 6 & 7. HETEROCERCAL TAIL FIN
    # =================================================================================================================
    tail_hs = 0.76 + 0.24 * THIN_MULT
    def tsy(y):
        return AXIS_Y + (y - AXIS_Y) * tail_hs

    tail_base_c = []
    sym_box(tail_base_c, 1.65 * (0.65 + 0.35 * THIN_MULT), sy(14.0), sy(19.4), 12.2, 17.2, "flank")
    sym_box(tail_base_c, 1.28 * (0.65 + 0.35 * THIN_MULT), sy(14.2), sy(16.6), 12.0, 17.0, "belly")
    sym_box(tail_base_c, 0.92, sy(14.6), sy(19.2), 9.0, 12.8, "fin_dark")

    for y0, y1, lx0, lx1, tx0, hwl, hwt in [
        (11.0, 14.4, 11.8, 15.8, 9.0, 1.22, 0.72),
        ( 7.8, 11.4, 10.2, 13.8, 8.0, 1.04, 0.62),
        ( 4.4,  8.2,  8.4, 11.8, 6.8, 0.86, 0.54),
    ]:
        sym_box(tail_base_c, hwl, tsy(y0), tsy(y1), lx0, lx1, "fin_lead")
        sym_box(tail_base_c, hwt, tsy(y0), tsy(y1), tx0, lx0 + 0.8, "fin_tip" if y0 < 8.5 else "fin_dark")

    tail_fin_c = []
    for y0, y1, lx0, lx1, tx0, hwl, hwt, dark_tip in [
        (18.6, 21.8, 11.4, 16.4, 7.2, 1.28, 0.72, False),
        (21.4, 24.4,  9.2, 14.8, 5.8, 1.15, 0.65, False),
        (24.0, 27.0,  7.0, 12.8, 4.4, 1.02, 0.58, False),
        (26.6, 29.4,  4.8, 10.4, 2.8, 0.90, 0.52, True),
        (29.0, 31.6,  2.6,  8.0, 2.0, 0.80, 0.48, True),
        (31.2, 34.0,  0.2,  5.8, 0.2, 0.70, 0.42, True),
    ]:
        sym_box(tail_fin_c, hwl, tsy(y0), tsy(y1), lx0, lx1, "fin_lead")
        sym_box(tail_fin_c, hwt, tsy(y0), tsy(y1), tx0, lx0 + 0.8, "fin_tip" if dark_tip else "fin_dark")
    sym_box(tail_fin_c, 0.48, tsy(27.2), tsy(29.4), 1.4, 3.4, "fin_tip")

    bones = [
        {"name": "root",               "pivot": [0, AXIS_Y, 0],                          "cubes": []},
        {"name": "body",               "parent": "root",           "pivot": [0, AXIS_Y, oz(56.0)],                   "cubes": []},
        {"name": "chest",              "parent": "body",           "pivot": [0, AXIS_Y, oz(56.0)],                   "cubes": chest_c},
        {"name": "head",               "parent": "chest",          "pivot": [0, AXIS_Y, oz(62.2)],                   "cubes": head_c},
        {"name": "lid_left",           "parent": "head",           "pivot": [7.6 * ws, AXIS_Y, oz(eye_ox)],          "cubes": build_lid(1)},
        {"name": "lid_right",          "parent": "head",           "pivot": [-7.6 * ws, AXIS_Y, oz(eye_ox)],         "cubes": build_lid(-1)},
        {"name": "jaw",                "parent": "head",           "pivot": [0, sy(12.2), oz(71.6)],                 "cubes": jaw_c,
         **({"rotation": [JAW_OPEN, 0, 0]} if abs(JAW_OPEN) > 1e-4 else {})},
        {"name": "tongue",             "parent": "jaw",            "pivot": [0, sy(12.0), oz(74.8)],                 "cubes": tongue_c},
        {"name": "prey",               "parent": "head",           "pivot": [0, sy(12.8), oz(77.5)],                 "cubes": []},
        *teeth_bones,
        {"name": "dorsal_1",           "parent": "chest",          "pivot": [0, sy(25.2), oz(51.5)],                 "cubes": d1_c},
        {"name": "pectoral_left",      "parent": "chest",          "pivot": [pec_pivot_x, pec_pivot_y, pec_pivot_z],
         "rotation": [4.0, -24.0, 23.0],                           "cubes": build_smooth_pec(1)},
        {"name": "pectoral_tip_left",  "parent": "pectoral_left",  "pivot": [tip_pivot_x, pec_pivot_y, pec_pivot_z],
         "rotation": [2.0, -9.0, 5.0],                             "cubes": build_smooth_pec_tip(1)},
        {"name": "pectoral_right",     "parent": "chest",          "pivot": [-pec_pivot_x, pec_pivot_y, pec_pivot_z],
         "rotation": [4.0, 24.0, -23.0],                           "cubes": build_smooth_pec(-1)},
        {"name": "pectoral_tip_right", "parent": "pectoral_right", "pivot": [-tip_pivot_x, pec_pivot_y, pec_pivot_z],
         "rotation": [2.0, 9.0, -5.0],                             "cubes": build_smooth_pec_tip(-1)},
        {"name": "torso",              "parent": "chest",          "pivot": [0, AXIS_Y, oz(50.0)],                   "cubes": torso_c},
        {"name": "rear",               "parent": "torso",          "pivot": [0, AXIS_Y, oz(37.5)],                   "cubes": rear_c},
        {"name": "pelvic_left",        "parent": "rear",           "pivot": [pel_pivot_x, pel_pivot_y, pel_pivot_z],
         "rotation": [3.0, -26.0, 22.0],                           "cubes": build_smooth_pelvic(1)},
        {"name": "pelvic_right",       "parent": "rear",           "pivot": [-pel_pivot_x, pel_pivot_y, pel_pivot_z],
         "rotation": [3.0, 26.0, -22.0],                           "cubes": build_smooth_pelvic(-1)},
        {"name": "peduncle",           "parent": "rear",           "pivot": [0, sy(17.2), oz(27.5)],                 "cubes": ped_c},
        {"name": "dorsal_2",           "parent": "peduncle",       "pivot": [0, sy(20.2), oz(25.2)],                 "cubes": d2_c},
        {"name": "anal_fin",           "parent": "peduncle",       "pivot": [0, sy(13.8), oz(25.2)],                 "cubes": anal_c},
        {"name": "tail_base",          "parent": "peduncle",       "pivot": [0, sy(17.0), oz(16.2)],                 "cubes": tail_base_c},
        {"name": "tail_fin",           "parent": "tail_base",      "pivot": [0, sy(18.6), oz(11.2)],                 "cubes": tail_fin_c},
    ]
    _normalize_bones(bones, NORM_SCALE)
    return bones


# =====================================================================================================================
# STAGE 4: RICH MINECRAFT PIXEL-ART PAINT ENGINE (Detailed Enamel Teeth, Deep Gullet, Palate, Countershading & Scars!)
# =====================================================================================================================
def _hash3(ix, iy, iz, seed=0):
    n = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def _vnoise(x, y, z, cell=1.0, seed=0):
    return _hash3(int(math.floor(x / cell)), int(math.floor(y / cell)), int(math.floor(z / cell)), seed)


def _snoise(x, y, z, cell=3.0, seed=0):
    fx, fy, fz = x / cell, y / cell, z / cell
    ix, iy, iz = int(math.floor(fx)), int(math.floor(fy)), int(math.floor(fz))
    tx, ty, tz = fx - ix, fy - iy, fz - iz
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    tz = tz * tz * (3 - 2 * tz)
    c000 = _hash3(ix, iy, iz, seed);     c100 = _hash3(ix + 1, iy, iz, seed)
    c010 = _hash3(ix, iy + 1, iz, seed); c110 = _hash3(ix + 1, iy + 1, iz, seed)
    c001 = _hash3(ix, iy, iz + 1, seed); c101 = _hash3(ix + 1, iy, iz + 1, seed)
    c011 = _hash3(ix, iy + 1, iz + 1, seed); c111 = _hash3(ix + 1, iy + 1, iz + 1, seed)
    x00 = c000 + (c100 - c000) * tx; x10 = c010 + (c110 - c010) * tx
    x01 = c001 + (c101 - c001) * tx; x11 = c011 + (c111 - c011) * tx
    y0 = x00 + (x10 - x00) * ty; y1 = x01 + (x11 - x01) * ty
    return y0 + (y1 - y0) * tz


PAL_CLEAN = {
    "cap_dark":     (56, 64, 76),
    "cap_mid":      (72, 82, 96),
    "flank_dark":   (90, 100, 113),
    "flank_stone":  (122, 130, 138),
    "light_wool":   (182, 187, 189),
    "belly_white":  (228, 229, 225),
    "belly_shade":  (196, 199, 198),
    "fin_base":     (82, 92, 106),
    "fin_lead":     (64, 73, 86),
    "fin_tip":      (42, 49, 60),
    "fin_under":    (210, 213, 211),
    "gill_deep":    (15, 18, 24),
    "gill_red":     (46, 26, 30),
    "gum_light":    (204, 128, 124),
    "gum_dark":     (148, 72, 74),
    "palate_light": (192, 114, 112),
    "palate_dark":  (132, 62, 68),
    "mouth_corner": (122, 54, 60),
    "tongue":       (214, 142, 136),
    "throat":       (78, 32, 38),
    "throat_deep":  (38, 14, 20),
    "tooth_root":   (216, 208, 188),
    "tooth_enamel": (244, 242, 232),
    "tooth_tip":    (255, 254, 250),
    "tooth_serr":   (194, 188, 174),
    "tooth_res":    (224, 215, 198),
    "eye_black":    (11, 14, 19),
    "eye_iris":     (28, 34, 42),
    "eye_glint":    (248, 252, 255),
    "lid":          (226, 232, 238),
    "scar":         (206, 208, 204),
}

PAL_SCARRED = dict(PAL_CLEAN, **{
    "cap_dark":    (45, 52, 64),
    "cap_mid":     (60, 70, 84),
    "flank_dark":  (78, 88, 101),
    "flank_stone": (110, 118, 126),
    "fin_base":    (72, 81, 95),
    "fin_lead":    (54, 62, 74),
    "fin_tip":     (34, 40, 50),
})


def _scar_amount(px, py_c, pz_c):
    qx = math.floor(px / 0.75) * 0.75 + 0.375
    qy = math.floor(py_c / 0.75) * 0.75 + 0.375
    qz = math.floor(pz_c / 0.75) * 0.75 + 0.375
    ox = LENGTH / 2.0 - qz
    scars = [
        ( 1, 56.0, 20.6, 47.5, 17.6, 0.52),
        ( 1, 53.8, 22.0, 49.0, 20.0, 0.45),
        (-1, 61.5, 21.4, 53.2, 18.8, 0.52),
        (-1, 39.5, 19.6, 34.0, 17.2, 0.48),
        ( 0, 82.0, 20.8, 78.2, 19.6, 0.44),
    ]
    best = 0.0
    for sgn, x0, y0, x1, y1, w in scars:
        if sgn != 0 and (qx * sgn) < 1.5:
            continue
        dx, dy = x1 - x0, y1 - y0
        l2 = dx * dx + dy * dy
        t = max(0.0, min(1.0, ((ox - x0) * dx + (qy - y0) * dy) / max(1e-5, l2)))
        cx, cy = x0 + t * dx, y0 + t * dy
        if math.hypot(ox - cx, qy - cy) < w:
            best = max(best, 0.85 if 0.12 < t < 0.88 else 0.48)
    return best


def _countershade_split_y(ox, px, pz_c):
    if ox >= 73.0:
        base_y = 15.0 + min(2.2, (ox - 73.0) * 0.24)
    elif ox >= 50.0:
        base_y = 14.5 + (ox - 50.0) * 0.022
    elif ox >= 26.0:
        base_y = 14.5 + (50.0 - ox) * 0.055
    else:
        base_y = 15.8 + (26.0 - ox) * 0.06
    block_step = (_vnoise(abs(px) + 3.0, 0.0, pz_c, cell=2.6, seed=11) - 0.5) * 3.1
    mid_wave   = (_snoise(abs(px), 2.0, pz_c, cell=6.5, seed=23) - 0.5) * 2.2
    fine_notch = (_vnoise(px, 0.0, pz_c, cell=1.1, seed=37) - 0.5) * 1.0
    if ox >= 74.0:
        block_step *= 0.45
        mid_wave *= 0.45
    return base_y + block_step + mid_wave + fine_notch


def paint_colour(c, face, px, py, pz):
    pal = PAL_SCARRED if WORKSHOP_LOOK == "scarred" else PAL_CLEAN
    part = c.get("_part", "flank")
    # Un-normalize back to canonical build coords so countershading, gills & scars remain pixel-identical!
    rx, ry, rz = px / NORM_SCALE, py / NORM_SCALE, pz / NORM_SCALE
    pz_c = rz / max(0.5, LEN_MULT)
    py_c = AXIS_Y + (ry - AXIS_Y) / max(0.5, THIN_MULT)
    ox = LENGTH / 2.0 - pz_c

    grain_fine = (_vnoise(rx, ry, rz, cell=0.65, seed=3) - 0.5)
    grain_blk  = (_vnoise(rx, ry, rz, cell=1.80, seed=7) - 0.5)
    mottle     = (_snoise(rx, ry, rz, cell=4.20, seed=19) - 0.5)

    if part == "eye_glint":
        return pal["eye_glint"]
    if part == "eye":
        c_cy = c["origin"][1] + c["size"][1] * 0.5
        c_cz = c["origin"][2] + c["size"][2] * 0.5
        r = math.hypot((py - c_cy) / max(0.5, c["size"][1] * 0.5),
                       (pz - c_cz) / max(0.5, c["size"][2] * 0.5))
        return shade(mix(pal["eye_black"], pal["eye_iris"], max(0.0, min(1.0, (r - 0.65) * 2.0))), 1.0 + grain_fine * 0.05)
    if part == "lid":
        return shade(pal["lid"], 1.0 + grain_fine * 0.06 - (0.06 if face == "down" else 0.0))

    # --- High-detail Shark Teeth & Hollow Mouth Interior ---
    if part in ("tooth_blade", "tooth_tip", "tooth_reserve", "tooth"):
        if part == "tooth_reserve":
            return shade(pal["tooth_res"], 0.94 + grain_fine * 0.06)
        # Subtle root-to-tip ivory enamel gradient + crisp micro-serration shimmer along blade edges!
        cy0, cy1 = c["origin"][1], c["origin"][1] + c["size"][1]
        cx0, cx1 = c["origin"][0], c["origin"][0] + c["size"][0]
        u_edge = abs(((px - cx0) / max(1e-4, cx1 - cx0)) - 0.5) * 2.0
        v_grad = (py - cy0) / max(1e-4, cy1 - cy0)
        if part == "tooth_tip":
            col = mix(pal["tooth_enamel"], pal["tooth_tip"], 0.72)
        else:
            col = mix(pal["tooth_enamel"], pal["tooth_root"], 0.30 * v_grad)
        if u_edge > 0.74:
            # Fine micro-serrated cutting-edge glint/notch
            serr = 0.5 + 0.5 * math.sin((px + py) * 26.0)
            col = mix(col, pal["tooth_serr"], 0.35 * (1.0 - serr))
        return shade(col, 1.0 + grain_fine * 0.04)
    if part == "gum":
        col = mix(pal["gum_dark"], pal["gum_light"], 0.58 + mottle * 0.48 + grain_fine * 0.18)
        return shade(col, 0.88) if face == "down" else col
    if part in ("palate", "palate_ridge"):
        depth_t = max(0.0, min(1.0, (ox - 71.5) / 8.5))
        col = mix(pal["palate_dark"], pal["palate_light"], 0.32 + 0.58 * depth_t + mottle * 0.30)
        if part == "palate_ridge":
            col = shade(col, 1.08)
        return shade(col, 0.90 + grain_fine * 0.08)
    if part == "mouth_corner":
        col = mix(pal["throat"], pal["mouth_corner"], 0.65 + mottle * 0.35)
        return shade(col, 0.92 + grain_fine * 0.10)
    if part == "tongue":
        # Subtle darker median groove down the center of the shark tongue!
        groove = max(0.0, 1.0 - abs(rx) / 0.85) * 0.14
        col = mix(pal["gum_dark"], pal["tongue"], 0.76 + mottle * 0.30 + grain_fine * 0.14)
        return shade(col, (1.04 if face == "up" else 0.88) - groove)
    if part == "throat":
        return shade(pal["throat"], 1.0 + grain_fine * 0.10)
    if part == "throat_deep":
        return shade(pal["throat_deep"], 1.0 + grain_fine * 0.08)
    if part == "nostril":
        return pal["gill_deep"]
    if part == "gill_slit":
        depth_hint = max(0.0, 1.0 - abs(py_c - 15.8) / 3.8)
        return mix(pal["gill_deep"], pal["gill_red"], depth_hint * 0.28 + grain_fine * 0.08)

    if part in ("fin_lead", "fin_dark", "fin_tip", "fin_under", "fin"):
        fin_ray = (_vnoise(rx * 0.65, ry * 1.3 + rz * 0.55, rz * 0.65, cell=1.1, seed=41) - 0.5)
        if part == "fin_under":
            tip_t = max(0.0, min(1.0, (abs(rx) - 15.0) / 6.5))
            col = mix(pal["fin_under"], pal["flank_stone"], tip_t * 0.60)
            col = shade(col, 1.0 + grain_blk * 0.10 + grain_fine * 0.08 + fin_ray * 0.06)
        else:
            base = pal["fin_tip"] if part == "fin_tip" else (pal["fin_lead"] if part == "fin_lead" else pal["fin_base"])
            tip_dark = 0.0
            if py_c > 30.5:
                tip_dark = max(tip_dark, min(1.0, (py_c - 30.5) / 7.0))
            if py_c < 7.8 and ox < 16.0:
                tip_dark = max(tip_dark, min(1.0, (7.8 - py_c) / 3.5))
            if abs(rx) > 19.0:
                tip_dark = max(tip_dark, min(1.0, (abs(rx) - 19.0) / 6.0))
            col = mix(base, pal["fin_tip"], tip_dark * 0.75)
            col = shade(col, 1.0 + grain_blk * 0.16 + mottle * 0.14 + fin_ray * 0.12 + grain_fine * 0.09)
            if face == "up" and part == "fin_lead":
                col = shade(col, 1.12)
            elif face == "down":
                col = shade(col, 0.84)
        if WORKSHOP_LOOK == "scarred" and 44.0 <= ox <= 53.0 and 25.5 <= py_c <= 30.5:
            sc = max(0.0, 1.0 - abs((ox - 47.5) * 0.6 + (py_c - 27.5)) / 0.85)
            if sc > 0.15:
                col = mix(col, pal["scar"], sc * 0.55)
        return col

    split_y = _countershade_split_y(ox, rx, pz_c)
    dy = py_c - split_y
    if part == "brow":
        col = mix(pal["cap_dark"], pal["cap_mid"], 0.35 + grain_blk * 0.25)
    elif dy >= 6.2:
        col = mix(pal["cap_mid"], pal["cap_dark"], min(1.0, (dy - 6.2) / 3.8 + mottle * 0.35))
    elif dy >= 2.2:
        col = mix(pal["flank_dark"], pal["cap_mid"], (dy - 2.2) / 4.0 + grain_blk * 0.28)
    elif dy >= 0.4:
        col = mix(pal["flank_stone"], pal["flank_dark"], (dy - 0.4) / 1.8 + grain_blk * 0.35)
    elif dy >= -1.2:
        t_step = (dy + 1.2) / 1.6
        col = pal["flank_stone"] if (t_step + grain_blk * 0.45 > 0.52) else pal["light_wool"]
    else:
        edge_t = max(0.0, min(1.0, (-dy - 1.2) / 2.2))
        col = mix(pal["light_wool"], pal["belly_white"], 0.45 + 0.55 * edge_t)
        if face == "down" or py_c < 10.6:
            col = mix(col, pal["belly_shade"], 0.32)

    if 17.0 <= ox <= 61.0 and abs(rx) > 1.8 and face in ("east", "west"):
        lat_y = 18.6 - (61.0 - ox) * 0.032
        if abs(py_c - lat_y) < 0.42:
            col = shade(col, 0.85)
    if part in ("gill_rib", "gill_rib_belly"):
        col = shade(col, 0.72 if face in ("north", "south") else 1.07)

    grain_strength = 0.08 if dy < -1.2 else 0.17
    col = shade(col, 1.0 + grain_blk * grain_strength + mottle * (grain_strength * 0.8) + grain_fine * (grain_strength * 0.65))
    if face == "up" and py_c > 18.0:
        col = shade(col, 1.11)
    elif face == "down":
        col = shade(col, 0.86)
    if WORKSHOP_LOOK == "scarred" and dy > -1.0:
        sc = _scar_amount(rx, py_c, pz_c)
        if sc > 0.12:
            col = mix(col, pal["scar"], min(0.85, sc * 0.78))
    return col


# =====================================================================================================================
# STAGE 5: BESPOKE BULL SHARK ANIMATION SUITE (8 Essential + 4 For-Fun Personality Clips!)
# Carcharhinid shark biomechanics: rock-steady muscular forebody, heavy power wave growing steadily into the tall
# heterocercal caudal fin, smooth pectoral wingtip feathering, snout-lift + nictitating eye-shield blink on impact!
# =====================================================================================================================
LID_DX = round(1.52 * THIN_MULT * NORM_SCALE, 3)   # outward X travel to cover glossy eye (GeckoLib negates pos.x!)
LID_DY = round(1.95 * THIN_MULT * NORM_SCALE, 3)   # upward Y travel to shield eye


def _lid_pose(amount):
    """Returns GeckoLib bone transforms for pale nictitating eye-shield membranes (amount 0=hidden .. 1=shut)."""
    k = max(0.0, min(1.0, amount))
    return {
        "lid_left":  {"position": [round(-LID_DX * k, 3), round(LID_DY * k, 3), 0.0]},
        "lid_right": {"position": [round( LID_DX * k, 3), round(LID_DY * k, 3), 0.0]},
    }


def _env_bell(u, t0, t1, t2, t3):
    """Smooth 0 -> 1 -> 1 -> 0 envelope across normalized time u in [0, 1]."""
    if u <= t0 or u >= t3:
        return 0.0
    if u < t1:
        return smooth((u - t0) / max(1e-5, t1 - t0))
    if u <= t2:
        return 1.0
    return 1.0 - smooth((u - t2) / max(1e-5, t3 - t2))


def _shark_swim_pose(ph, amp=1.0, body_pitch=0.0, body_y=0.0, pec_sweep=0.0, pec_drop=0.0, jaw_base=4.0, jaw_amp=2.0):
    """Core carcharhinid traveling S-wave pose at phase `ph` (radians).
    Designed so world sideways swing grows cleanly from steady head -> tail_fin with monotonic backward phase lag,
    and phase-aligned (`ph0 = +2.10`) so `t = 0` crosses centerline near 0 deg for silky-smooth GeckoLib blends!
    """
    s = math.sin
    p0 = ph + 1.65  # centers rear/peduncle/tail_base cleanly near 0 deg at t = 0 for zero-pop 4-tick GeckoLib blends!
    body_yaw = 1.3 * amp * s(p0)
    body_roll = -1.2 * amp * s(p0 - 0.55)
    head_yaw = -1.8 * amp * s(p0 - 0.15)
    torso_yaw = 3.2 * amp * s(p0 - 0.58)
    rear_yaw = 5.4 * amp * s(p0 - 1.18)
    ped_yaw = 7.4 * amp * s(p0 - 1.78)
    tbase_yaw = 9.8 * amp * s(p0 - 2.38)
    tfin_yaw = 8.0 * amp * s(p0 - 2.92)

    pec_pitch = 2.0 * amp * s(p0 - 0.4)
    pec_roll = 2.2 * amp * s(p0 - 0.3)
    tip_yaw = -2.8 * amp * s(p0 - 0.9)
    tip_roll = 3.4 * amp * s(p0 - 0.9)
    pel_roll = 2.6 * amp * s(p0 - 1.5)
    jaw_deg = max(0.0, jaw_base + jaw_amp * s(ph))

    p = {
        "root":               {"position": [0.0, round(body_y + 0.30 * amp * s(ph * 2.0), 3), 0.0]},
        "body":               {"rotation": [body_pitch, body_yaw, body_roll]},
        "head":               {"rotation": [0.0, head_yaw, 0.0]},
        "jaw":                {"rotation": [jaw_deg, 0.0, 0.0]},
        "tongue":             {"rotation": [jaw_deg * 0.12, 0.0, 0.0]},
        "dorsal_1":           {"rotation": [0.0, 0.0, -1.4 * amp * s(p0 - 0.7)]},
        "pectoral_left":      {"rotation": [pec_pitch, -pec_sweep,  pec_drop + pec_roll]},
        "pectoral_right":     {"rotation": [pec_pitch,  pec_sweep, -pec_drop - pec_roll]},
        "pectoral_tip_left":  {"rotation": [0.0,  tip_yaw,  tip_roll]},
        "pectoral_tip_right": {"rotation": [0.0, -tip_yaw, -tip_roll]},
        "torso":              {"rotation": [0.0, torso_yaw, 0.0]},
        "rear":               {"rotation": [0.0, rear_yaw, 0.0]},
        "pelvic_left":        {"rotation": [0.0, 0.0,  pel_roll]},
        "pelvic_right":       {"rotation": [0.0, 0.0, -pel_roll]},
        "peduncle":           {"rotation": [0.0, ped_yaw, 0.0]},
        "dorsal_2":           {"rotation": [0.0, 0.0, -2.0 * amp * s(p0 - 1.9)]},
        "anal_fin":           {"rotation": [0.0, 0.0,  2.0 * amp * s(p0 - 1.9)]},
        "tail_base":          {"rotation": [0.0, tbase_yaw, 0.0]},
        "tail_fin":           {"rotation": [0.0, tfin_yaw, 0.0]},
    }
    p.update(_lid_pose(0.0))
    return p


def _clip_idle():
    """Essential 1: Slow ominous ram-ventilation patrol glide (sharks never freeze completely!)."""
    L = 2.6
    def pose(t):
        ph = 2.0 * math.pi * t / L
        return _shark_swim_pose(ph, amp=0.60, body_pitch=0.6 * math.sin(ph), body_y=0.35 * math.sin(ph),
                                pec_sweep=0.0, pec_drop=1.0, jaw_base=4.5, jaw_amp=2.0)
    return clip(L, pose, steps=26, loop=True)


def _clip_swim():
    """Essential 2: Steady predatory river swim (speed-matched carcharhinid S-wave, passes checks.wave!)."""
    L = 1.5
    def pose(t):
        ph = 2.0 * math.pi * t / L
        return _shark_swim_pose(ph, amp=1.00, body_pitch=0.0, body_y=0.0,
                                pec_sweep=2.0, pec_drop=0.0, jaw_base=5.0, jaw_amp=1.6)
    return clip(L, pose, steps=24, loop=True)


def _clip_burst():
    """Essential 3: Explosive ambush chase sprint (fast tail whip + swept-back low-drag pectorals!)."""
    L = 0.85
    def pose(t):
        ph = 2.0 * math.pi * t / L
        return _shark_swim_pose(ph, amp=1.32, body_pitch=1.8, body_y=-0.3,
                                pec_sweep=13.0, pec_drop=-3.0, jaw_base=9.5, jaw_amp=2.2)
    return clip(L, pose, steps=20, loop=True)


def _clip_surface_prowl():
    """Essential 4: Iconic Jaws-style high-riding surface stalk (1st dorsal slicing above waterline!)."""
    L = 1.8
    def pose(t):
        ph = 2.0 * math.pi * t / L
        return _shark_swim_pose(ph, amp=0.84, body_pitch=2.2 + 0.7 * math.sin(ph), body_y=1.5,
                                pec_sweep=-3.0, pec_drop=-3.0, jaw_base=5.5, jaw_amp=1.5)
    return clip(L, pose, steps=24, loop=True)


def _clip_bite():
    """Essential 5: Signature Bull Shark crushing bite! Snout lifts, jaw opens 46 deg, nictitating lids flick shut on hit tick 8 (0.40s)."""
    L = 0.95
    def pose(t):
        u = t / L
        # Gape curve: smooth rapid open (0.04..0.32), crushing slam shut (0.32..0.44 = hit tick 8!), settle (0.44..0.95)
        open_k = curve([(0.0, 0.0), (0.08, 0.14), (0.30, 1.0), (0.37, 0.88), (0.44, 0.06), (0.58, 0.16), (0.82, 0.04), (1.0, 0.0)], u)
        snout_k = curve([(0.0, 0.0), (0.10, 0.25), (0.32, 1.0), (0.44, -0.22), (0.62, 0.08), (1.0, 0.0)], u)
        # Nictitating eye-shield slides UP & OUT just before impact (0.16..0.28), holds through clamp, retracts (0.52..0.78)
        lid_k = _env_bell(u, 0.14, 0.26, 0.50, 0.78)
        # Explosive forward lunge & crisp tail kick right into impact
        lunge_z = curve([(0.0, 0.0), (0.24, 1.8), (0.42, -4.2), (0.68, -1.2), (1.0, 0.0)], u)
        kick = math.sin(u * math.pi * 2.0) * _env_bell(u, 0.0, 0.18, 0.72, 1.0)
        brake = curve([(0.0, 0.0), (0.28, 0.2), (0.44, 1.0), (0.75, 0.25), (1.0, 0.0)], u)

        p = {
            "root":               {"position": [0.0, round(0.8 * snout_k, 3), round(lunge_z, 3)]},
            "body":               {"rotation": [-2.5 * snout_k, 2.2 * kick, -2.5 * kick]},
            "head":               {"rotation": [-13.5 * snout_k, -2.5 * kick, 0.0]},
            "jaw":                {"rotation": [4.0 + 42.0 * open_k, 0.0, 0.0]},
            "tongue":             {"rotation": [5.5 * open_k, 0.0, 0.0]},
            "dorsal_1":           {"rotation": [0.0, 0.0, -3.5 * kick]},
            "pectoral_left":      {"rotation": [8.0 * brake, -6.0 * open_k + 8.0 * brake,  10.0 * brake]},
            "pectoral_right":     {"rotation": [8.0 * brake,  6.0 * open_k - 8.0 * brake, -10.0 * brake]},
            "pectoral_tip_left":  {"rotation": [0.0, -4.0 * brake,  8.0 * brake]},
            "pectoral_tip_right": {"rotation": [0.0,  4.0 * brake, -8.0 * brake]},
            "torso":              {"rotation": [2.2 * snout_k, 5.5 * kick, 0.0]},
            "rear":               {"rotation": [0.0, 9.0 * kick, 0.0]},
            "pelvic_left":        {"rotation": [0.0, 0.0,  5.0 * brake]},
            "pelvic_right":       {"rotation": [0.0, 0.0, -5.0 * brake]},
            "peduncle":           {"rotation": [0.0, 12.5 * kick, 0.0]},
            "tail_base":          {"rotation": [0.0, 15.0 * kick, 0.0]},
            "tail_fin":           {"rotation": [0.0, 12.0 * kick, 0.0]},
        }
        p.update(_lid_pose(lid_k))
        return p
    return clip(L, pose, steps=22, loop=False)


def _clip_thrash():
    """Essential 6: Savage head-shaking prey tear! Clamped jaws + lids shut + violent lateral sawing torque!"""
    L = 1.20
    def pose(t):
        ph = 2.0 * math.pi * (t / L) * 2.0 + 1.55  # starts tail near 0 deg for smooth thrash -> swim blend!
        s = math.sin(ph)
        s_lag = math.sin(ph - 0.75)
        s_tail = math.sin(ph - 1.55)
        p = {
            "root":               {"position": [round(1.4 * s, 3), round(0.45 * math.cos(ph), 3), 0.0]},
            "body":               {"rotation": [-2.5, 10.0 * s, -8.5 * s]},
            "head":               {"rotation": [-5.5, 17.0 * s, -6.5 * s]},
            "jaw":                {"rotation": [16.0 + 5.0 * math.sin(ph * 1.5), 0.0, 0.0]},
            "tongue":             {"rotation": [2.5, 0.0, 0.0]},
            "dorsal_1":           {"rotation": [0.0, 0.0, -5.5 * s]},
            "pectoral_left":      {"rotation": [4.0, -7.0 + 6.0 * s,  10.0 + 5.0 * s]},
            "pectoral_right":     {"rotation": [4.0,  7.0 + 6.0 * s, -10.0 + 5.0 * s]},
            "pectoral_tip_left":  {"rotation": [0.0, -4.5 * s,  6.0 * s]},
            "pectoral_tip_right": {"rotation": [0.0, -4.5 * s,  6.0 * s]},
            "torso":              {"rotation": [1.8, -8.0 * s_lag, 3.5 * s]},
            "rear":               {"rotation": [0.0, -10.5 * s_lag, 0.0]},
            "pelvic_left":        {"rotation": [0.0, 0.0,  5.0 * s_lag]},
            "pelvic_right":       {"rotation": [0.0, 0.0, -5.0 * s_lag]},
            "peduncle":           {"rotation": [0.0, -12.0 * s_tail, 0.0]},
            "tail_base":          {"rotation": [0.0, -13.5 * s_tail, 0.0]},
            "tail_fin":           {"rotation": [0.0, -10.5 * s_tail, 0.0]},
        }
        p.update(_lid_pose(1.0))
        return p
    return clip(L, pose, steps=24, loop=True)


def _clip_beached_flop():
    """Essential 7: Heavy stranded riverbank thrash (rocking wildly on belly/side above sand, ZERO ground clip!)."""
    L = 0.90
    def pose(t):
        ph = 2.0 * math.pi * t / L
        s = math.sin(ph)
        s2 = math.sin(ph - 0.50)
        hop = abs(s) * 2.2
        # Fold both pectoral wings slightly upward (-28/+28 deg dihedral) so wingtips brace cleanly ON TOP of y=0!
        roll_z = 26.0 * s
        p = {
            "root":               {"position": [0.0, round(1.8 + hop, 3), 0.0]},
            "body":               {"rotation": [2.5 * s, 6.0 * s, roll_z]},
            "head":               {"rotation": [-4.0 * abs(s), 10.0 * s, 0.0]},
            "jaw":                {"rotation": [18.0 + 14.0 * abs(s), 0.0, 0.0]},
            "tongue":             {"rotation": [3.5, 0.0, 0.0]},
            "pectoral_left":      {"rotation": [6.0 * s, -8.0, -26.0 + 8.0 * s]},
            "pectoral_right":     {"rotation": [-6.0 * s,  8.0,  26.0 + 8.0 * s]},
            "pectoral_tip_left":  {"rotation": [0.0, 0.0, -10.0 + 6.0 * s]},
            "pectoral_tip_right": {"rotation": [0.0, 0.0,  10.0 + 6.0 * s]},
            "torso":              {"rotation": [0.0, -10.0 * s2, 0.0]},
            "rear":               {"rotation": [0.0, -13.0 * s2, 0.0]},
            "pelvic_left":        {"rotation": [0.0, 0.0, -22.0]},
            "pelvic_right":       {"rotation": [0.0, 0.0,  22.0]},
            "peduncle":           {"rotation": [0.0, -14.0 * math.sin(ph - 0.9), 0.0]},
            "tail_base":          {"rotation": [0.0, -16.0 * math.sin(ph - 1.2), 0.0]},
            "tail_fin":           {"rotation": [0.0, -12.0 * math.sin(ph - 1.5), 0.0]},
        }
        p.update(_lid_pose(0.45 + 0.35 * abs(s)))
        return p
    return clip(L, pose, steps=18, loop=True)


def _clip_death():
    """Essential 8: Slow heavy belly-up roll (180 deg) with limp drooping fins and slack open jaw."""
    L = 1.60
    def pose(t):
        u = t / L
        roll_k = smooth(min(1.0, u / 0.85))
        settle = smooth(max(0.0, (u - 0.55) / 0.45))
        spasm = math.sin(u * math.pi * 3.0) * (1.0 - roll_k) * 8.0
        p = {
            "root":               {"position": [0.0, round(1.8 * roll_k, 3), 0.0]},
            "body":               {"rotation": [6.0 * roll_k, spasm * 0.4, 180.0 * roll_k]},
            "head":               {"rotation": [5.0 * roll_k, spasm * 0.6, 0.0]},
            "jaw":                {"rotation": [4.0 + 20.0 * roll_k, 0.0, 0.0]},
            "tongue":             {"rotation": [4.0 * roll_k, 0.0, 0.0]},
            "pectoral_left":      {"rotation": [4.0 * roll_k, -10.0 * roll_k, -18.0 * roll_k]},
            "pectoral_right":     {"rotation": [4.0 * roll_k,  10.0 * roll_k,  18.0 * roll_k]},
            "pectoral_tip_left":  {"rotation": [0.0, -4.0 * roll_k, -14.0 * settle]},
            "pectoral_tip_right": {"rotation": [0.0,  4.0 * roll_k,  14.0 * settle]},
            "torso":              {"rotation": [4.0 * roll_k, -spasm * 0.6, 0.0]},
            "rear":               {"rotation": [5.0 * roll_k, -spasm, 0.0]},
            "pelvic_left":        {"rotation": [0.0, 0.0, -14.0 * roll_k]},
            "pelvic_right":       {"rotation": [0.0, 0.0,  14.0 * roll_k]},
            "peduncle":           {"rotation": [6.0 * settle, 4.0 * settle, 0.0]},
            "tail_base":          {"rotation": [7.0 * settle, 6.0 * settle, 0.0]},
            "tail_fin":           {"rotation": [5.0 * settle, 4.0 * settle, 0.0]},
        }
        p.update(_lid_pose(0.65 * roll_k))
        return p
    return clip(L, pose, steps=24, loop="hold")


def _clip_threat_display():
    """Fun 1 (Real Bull Shark Biology!): Agonistic hunched-back warning display — steep dropped pectorals + double jaw snap!"""
    L = 1.80
    def pose(t):
        u = t / L
        hunch = _env_bell(u, 0.04, 0.24, 0.76, 0.98)
        swagger = math.sin(u * math.pi * 4.0) * hunch
        # Two sharp warning jaw snaps while hunched!
        snap1 = _env_bell(u, 0.22, 0.32, 0.36, 0.44)
        snap2 = _env_bell(u, 0.50, 0.60, 0.64, 0.72)
        jaw_deg = 4.0 + 30.0 * snap1 + 36.0 * snap2
        lid_k = max(0.75 * snap1, 0.90 * snap2)

        p = {
            "root":               {"position": [0.0, round(-1.2 * hunch, 3), 0.0]},
            # Back arches dramatically: chest/body tilts slightly, head lifts up, torso/rear bow downward!
            "body":               {"rotation": [-6.0 * hunch, 5.0 * swagger, -7.0 * swagger]},
            "head":               {"rotation": [-12.0 * hunch - 4.0 * (snap1 + snap2), -4.0 * swagger, 0.0]},
            "jaw":                {"rotation": [jaw_deg, 0.0, 0.0]},
            "tongue":             {"rotation": [jaw_deg * 0.12, 0.0, 0.0]},
            "dorsal_1":           {"rotation": [0.0, 0.0, -4.5 * swagger]},
            # Iconic bull shark threat posture: BOTH pectoral wings dropped steeply downward (+28 deg dihedral!)
            "pectoral_left":      {"rotation": [12.0 * hunch,  6.0 * hunch,  28.0 * hunch + 4.0 * swagger]},
            "pectoral_right":     {"rotation": [12.0 * hunch, -6.0 * hunch, -28.0 * hunch - 4.0 * swagger]},
            "pectoral_tip_left":  {"rotation": [0.0,  4.0 * hunch,  12.0 * hunch]},
            "pectoral_tip_right": {"rotation": [0.0, -4.0 * hunch, -12.0 * hunch]},
            "torso":              {"rotation": [9.0 * hunch,  9.0 * swagger, 0.0]},
            "rear":               {"rotation": [8.0 * hunch, 11.0 * swagger, 0.0]},
            "pelvic_left":        {"rotation": [0.0, 0.0,  16.0 * hunch]},
            "pelvic_right":       {"rotation": [0.0, 0.0, -16.0 * hunch]},
            "peduncle":           {"rotation": [5.0 * hunch, -10.0 * swagger, 0.0]},
            "tail_base":          {"rotation": [0.0, -14.0 * swagger, 0.0]},
            "tail_fin":           {"rotation": [0.0, -12.0 * swagger, 0.0]},
        }
        p.update(_lid_pose(lid_k))
        return p
    return clip(L, pose, steps=30, loop=False)


def _clip_bump_investigate():
    """Fun 2: Classic bull shark 'Bump & Test' nudge (curious eye-tilt -> blunt snout bump @ 0.50s -> sharp bank away!)."""
    L = 1.35
    def pose(t):
        u = t / L
        # Curious eye-roll approach -> forward snout nudge -> crisp bank turn away
        tilt = curve([(0.0, 0.0), (0.24, 16.0), (0.37, 6.0), (0.52, -20.0), (0.78, -14.0), (1.0, 0.0)], u)
        lunge = curve([(0.0, 0.0), (0.26, -1.2), (0.38, -5.2), (0.50, -2.0), (0.76, 1.0), (1.0, 0.0)], u)
        bank_yaw = curve([(0.0, 0.0), (0.28, -5.0), (0.38, 4.0), (0.64, 18.0), (0.86, 8.0), (1.0, 0.0)], u)
        lid_k = _env_bell(u, 0.28, 0.35, 0.44, 0.56)  # protective blink right on snout contact!
        tail_s = math.sin(u * math.pi * 3.0) * _env_bell(u, 0.0, 0.15, 0.80, 1.0)

        p = {
            "root":               {"position": [0.0, 0.0, round(lunge, 3)]},
            "body":               {"rotation": [2.0 * math.sin(u * math.pi), bank_yaw * 0.35, tilt]},
            "head":               {"rotation": [-4.0 * _env_bell(u, 0.18, 0.32, 0.42, 0.55), bank_yaw * 0.55, tilt * 0.25]},
            "jaw":                {"rotation": [4.0 + 7.0 * _env_bell(u, 0.15, 0.32, 0.45, 0.65), 0.0, 0.0]},
            "pectoral_left":      {"rotation": [4.0, -4.0 - bank_yaw * 0.3,  6.0 + tilt * 0.4]},
            "pectoral_right":     {"rotation": [4.0,  4.0 - bank_yaw * 0.3, -6.0 + tilt * 0.4]},
            "pectoral_tip_left":  {"rotation": [0.0, -3.0,  5.0 * tail_s]},
            "pectoral_tip_right": {"rotation": [0.0,  3.0, -5.0 * tail_s]},
            "torso":              {"rotation": [0.0, -bank_yaw * 0.35 + 4.0 * tail_s, 0.0]},
            "rear":               {"rotation": [0.0, -bank_yaw * 0.45 + 8.0 * tail_s, 0.0]},
            "peduncle":           {"rotation": [0.0, 12.0 * tail_s, 0.0]},
            "tail_base":          {"rotation": [0.0, 15.0 * tail_s, 0.0]},
            "tail_fin":           {"rotation": [0.0, 12.0 * tail_s, 0.0]},
        }
        p.update(_lid_pose(lid_k))
        return p
    return clip(L, pose, steps=26, loop=False)


def _clip_breach():
    """Fun 3: Explosive river-surface breach & dramatic mid-air half-roll twist returning cleanly to 0 deg on splashdown!"""
    L = 1.50
    def pose(t):
        u = t / L
        rise_y = curve([(0.0, 0.0), (0.14, -4.5), (0.34, 14.0), (0.54, 26.0), (0.72, 16.0), (0.88, -3.0), (1.0, 0.0)], u)
        pitch_x = curve([(0.0, 0.0), (0.14, 12.0), (0.30, -44.0), (0.52, -16.0), (0.72, 30.0), (0.88, 22.0), (1.0, 0.0)], u)
        # Dramatic 155-deg mid-air predator twist peaking at apex and unwinding smoothly back to 0 deg (zero GeckoLib snap!)
        roll_z = 155.0 * _env_bell(u, 0.22, 0.48, 0.56, 0.88)
        jaw_k = _env_bell(u, 0.26, 0.42, 0.64, 0.80)
        lid_k = _env_bell(u, 0.30, 0.40, 0.68, 0.82)
        whip = math.sin(u * math.pi * 6.0) * _env_bell(u, 0.04, 0.16, 0.78, 0.96)
        arch = math.sin(u * math.pi * 2.0) * 8.0 * _env_bell(u, 0.12, 0.26, 0.78, 0.96)

        p = {
            "root":               {"position": [0.0, round(rise_y, 3), 0.0]},
            "body":               {"rotation": [pitch_x, 3.0 * whip, roll_z]},
            "head":               {"rotation": [-8.0 * jaw_k, -3.5 * whip, 0.0]},
            "jaw":                {"rotation": [4.0 + 40.0 * jaw_k, 0.0, 0.0]},
            "tongue":             {"rotation": [5.0 * jaw_k, 0.0, 0.0]},
            "pectoral_left":      {"rotation": [6.0, -14.0 * _env_bell(u, 0.12, 0.28, 0.74, 0.92),  12.0 * jaw_k]},
            "pectoral_right":     {"rotation": [6.0,  14.0 * _env_bell(u, 0.12, 0.28, 0.74, 0.92), -12.0 * jaw_k]},
            "pectoral_tip_left":  {"rotation": [0.0, -6.0 * jaw_k,  10.0 * whip]},
            "pectoral_tip_right": {"rotation": [0.0,  6.0 * jaw_k, -10.0 * whip]},
            "torso":              {"rotation": [arch * 0.6, 7.0 * whip, 0.0]},
            "rear":               {"rotation": [arch * 0.8, 11.0 * whip, 0.0]},
            "peduncle":           {"rotation": [arch * 0.5, 15.0 * whip, 0.0]},
            "tail_base":          {"rotation": [0.0, 18.0 * whip, 0.0]},
            "tail_fin":           {"rotation": [0.0, 14.0 * whip, 0.0]},
        }
        p.update(_lid_pose(lid_k))
        return p
    return clip(L, pose, steps=30, loop=False)


def _clip_spy_hop():
    """Fun 4: Curious vertical 'Spy-Hop' peek — rises vertically above surface, scans riverbank left & right, slips back under!"""
    L = 2.20
    def pose(t):
        u = t / L
        hover = _env_bell(u, 0.04, 0.26, 0.74, 0.96)
        # Scan head smoothly left (+18 deg) then right (-18 deg) while hovering vertically!
        scan = curve([(0.0, 0.0), (0.24, 0.0), (0.40, 18.0), (0.60, -18.0), (0.76, 0.0), (1.0, 0.0)], u)
        scull = math.sin(u * math.pi * 6.0) * _env_bell(u, 0.02, 0.16, 0.84, 0.98)
        p = {
            "root":               {"position": [0.0, round(10.5 * hover + 0.8 * math.sin(u * math.pi * 4.0) * hover, 3), 0.0]},
            "body":               {"rotation": [-48.0 * hover, scan * 0.25, -scan * 0.18]},
            "head":               {"rotation": [12.0 * hover, scan * 0.75, 0.0]},
            "jaw":                {"rotation": [4.0 + 9.0 * hover, 0.0, 0.0]},
            # Wide stabilizing sculling pectoral fins keeping the shark balanced upright!
            "pectoral_left":      {"rotation": [10.0 * hover + 5.0 * scull, -4.0,  -12.0 * hover + 6.0 * scull]},
            "pectoral_right":     {"rotation": [10.0 * hover - 5.0 * scull,  4.0,   12.0 * hover - 6.0 * scull]},
            "pectoral_tip_left":  {"rotation": [0.0, -4.0 * scull,  8.0 * scull]},
            "pectoral_tip_right": {"rotation": [0.0,  4.0 * scull, -8.0 * scull]},
            "torso":              {"rotation": [6.0 * hover, 4.5 * scull, 0.0]},
            "rear":               {"rotation": [6.0 * hover, 8.0 * scull, 0.0]},
            "pelvic_left":        {"rotation": [0.0, 0.0,  6.0 * scull]},
            "pelvic_right":       {"rotation": [0.0, 0.0, -6.0 * scull]},
            "peduncle":           {"rotation": [4.0 * hover, 11.5 * scull, 0.0]},
            "tail_base":          {"rotation": [0.0, 14.5 * scull, 0.0]},
            "tail_fin":           {"rotation": [0.0, 11.5 * scull, 0.0]},
        }
        p.update(_lid_pose(0.0))
        return p
    return clip(L, pose, steps=34, loop=False)


def clips():
    """All 12 bespoke Bull Shark animations (8 essential gameplay clips + 4 fun personality clips)."""
    return {
        # Essential 8
        "idle":             _clip_idle(),
        "swim":             _clip_swim(),
        "burst":            _clip_burst(),
        "surface_prowl":    _clip_surface_prowl(),
        "bite":             _clip_bite(),
        "thrash":           _clip_thrash(),
        "beached_flop":     _clip_beached_flop(),
        "death":            _clip_death(),
        # Fun 4
        "threat_display":   _clip_threat_display(),
        "bump_investigate": _clip_bump_investigate(),
        "breach":           _clip_breach(),
        "spy_hop":          _clip_spy_hop(),
    }


WORKSHOP_CLIPS = clips
WORKSHOP_CLIP_INFO = {
    "water": ["idle", "swim", "burst", "surface_prowl", "bite", "thrash", "death",
              "threat_display", "bump_investigate", "breach", "spy_hop"],
    "surface": 24.5 / 16.0,
    "notes": {
        "idle":             "Slow ominous ram-ventilation patrol glide (heavy low-cadence tail sweep + subtle jaw breath).",
        "swim":             "Steady predatory river swim (speed-matched carcharhinid S-wave, steady head, wingtip feathering).",
        "burst":            "Explosive ambush sprint (fast tail whip + pectoral wings swept back for low drag).",
        "surface_prowl":    "Jaws-style high-riding river stalk with tall 1st dorsal fin slicing above the waterline.",
        "bite":             "Crushing bull shark bite: snout lifts, jaw opens 46 deg, pale nictitating lids flick shut on hit tick 8 (0.40s).",
        "thrash":           "Savage sawing head-shake prey tear with jaws clamped and nictitating eye-shields locked shut.",
        "beached_flop":     "Heavy side-lying stranded riverbank thrash with desperate tail slaps and gasping jaws.",
        "death":            "Slow solemn 180-deg belly-up roll with relaxed drooping fins and slack open jaw.",
        "threat_display":   "Real bull shark agonistic hunch warning: arched back, steeply dropped pectorals (+28 deg), double jaw snap!",
        "bump_investigate": "Classic bull shark 'bump & test' nudge: curious head tilt, blunt-snout contact @ 0.50s (tick 10), crisp bank away.",
        "breach":           "Explosive river-surface breach launching skyward into a full 360-deg airborne corkscrew with roaring jaws!",
        "spy_hop":          "Curious vertical spy-hop peek above the surface scanning the riverbank left & right before slipping under.",
    },
}


def workshop_look():
    """Returns (bones, PNG bytes, texture width, height) for tools/workshop.py."""
    bones = build_bones()
    bones, rows, w, h = paint_faces(bones, texels=TEXELS, colour=paint_colour)
    return bones, png(rows), w, h


def export_game():
    """Exports bull_shark geo (auto game_faces!), Clean + Scarred textures, and 12 custom animations into savanna/."""
    global WORKSHOP_LOOK
    saved_look = WORKSHOP_LOOK
    try:
        WORKSHOP_LOOK = "clean"
        bones_clean, png_clean, w, h = workshop_look()
        WORKSHOP_LOOK = "scarred"
        _, png_scarred, _, _ = workshop_look()
    finally:
        WORKSHOP_LOOK = saved_look

    all_clips = clips()
    write_assets({
        "geo/entity/bull_shark.geo.json":             geo("bull_shark", bones_clean, w, h, (5.5, 2.2, [0, 1.0, 0])),
        "textures/entity/bull_shark.png":             png_clean,
        "textures/entity/bull_shark_scarred.png":     png_scarred,
        "animations/entity/bull_shark.animation.json": animations(all_clips),
    }, mod="savanna")


if __name__ == "__main__":
    if "game" in sys.argv[1:]:
        export_game()
    else:
        bs = build_bones()
        all_clips = clips()
        n_boxes = sum(len(b["cubes"]) for b in bs)
        zs = [z for b in bs for c in b["cubes"] for z in (c["origin"][2], c["origin"][2] + c["size"][2])]
        print(f"bull_shark [{TEETH_STYLE}, {WORKSHOP_LOOK}]: {len(bs)} bones, {n_boxes} / 400 boxes, "
              f"len={max(zs)-min(zs):.2f} px ({(max(zs)-min(zs))/16:.2f}b), {len(all_clips)} clips")
