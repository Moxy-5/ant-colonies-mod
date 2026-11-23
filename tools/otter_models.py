"""The African River Otter (Aonyx capensis) model maker (M9 river life; Upright Four Legs + Aquatic Extras, 2026-11-21).

Workshop Stages 3 & 4: Refined 3D Detail & Pixel-Art Fur Paint!
- Smooth pitched spine & tail chains (hips -> waist -> ribs -> chest -> neck -> head, pitched rump & snout bridge,
  and 5-segment tapering rudder tail) — zero staircase blocks.
- Sturdy mammalian legs darkening into broad flat 3-fingered clawless paws.
- Rich mottled chocolate-umber Minecraft fur coat, iconic speckled cream throat & chest bib (matching Key Details),
  plush cream cheeks, velvety dark charcoal nose with nostrils, dark hollow rounded ears, crisp eyes with white glint
  and hidden eyelids, and 3 pairs of slender curved 3D white whiskers on movable cheek bones.

Size (the user's call, confirmed 2026-11-21): 2.6 blocks long (41.6 model px at 16 px = 1 block, drawn scale 1.0) —
extra 0.4 blocks (6.4 px) in the sleek arched otter torso — x ~1.0 block tall at crown (16.0 model px; ~12.3 px
arched back) x ~0.60 blocks wide (9.6 model px).
Units are model pixels: head toward -Z, up +Y, left +X, ground y = 0, middle line x = 0.
Outline x (0 = tail tip, 41.6 = snout tip) converts to model z via oz(ox) = total_length() / 2.0 - ox.

    python tools/workshop.py snapshot otter --note "..."
    python tools/workshop.py versions otter
    python tools/workshop.py serve otter
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (cube, pitch, mix, shade, paint_faces, png, geo, animations, clip,  # noqa: E402
                         write_assets, euler, mat_mul, mat_vec, vadd, vsub, block_moves, write_block_moves)
from motion_kit import two_bone, stride, ease, pulse, ramp, spline  # noqa: E402

# --- workshop sliders (editable live in tools/workshop.py serve otter) -----------------------------------------------
BODY_EXTRA = 6.4        # +0.40 blocks added in the sleek torso (0.0 -> 2.2 blocks, 6.4 -> 2.6 blocks!)
BACK_H = 12.0           # shoulder/hip back height (px)
ARCH_DEG = 3.5          # subtle natural lumbar arch across waist & ribs (deg)
BODY_HW = 4.65          # shoulder/hip half-width (px)
NECK_PITCH = 34.0       # upward sweep of the neck/chest (deg)
NECK_LEN = 4.6          # neck reach along its pitched axis (px)
HEAD_PITCH = 2.0        # slight alert head tilt (+ = nose slightly up, deg)
TAIL_LEN = 12.2         # smooth tapering tail length (px)
WHISKER_LEN = 3.2       # total length of each curved 3D whisker (px)
EAR_STYLE = "A"         # the ears: "A" picked by the user 2026-11-22 ("do A bigger for the ears"); "v6" (the old), "B", "C"

WORKSHOP_SHAPE = [
    ("BODY_EXTRA",  "Extra body length (px)",  0.0, 16.0, 0.8),
    ("BACK_H",      "Back height (px)",       10.5, 13.5, 0.25),
    ("ARCH_DEG",    "Back arch curve (deg)",   0.0,  7.0, 0.5),
    ("BODY_HW",     "Body half-width (px)",    3.8,  5.5, 0.15),
    ("NECK_PITCH",  "Neck sweep angle (deg)", 20.0, 46.0, 2.0),
    ("NECK_LEN",    "Neck length (px)",        3.4,  6.0, 0.2),
    ("HEAD_PITCH",  "Head tilt (deg)",       -10.0, 14.0, 2.0),
    ("TAIL_LEN",    "Tail length (px)",        9.5, 16.0, 0.5),
    ("WHISKER_LEN", "Whisker length (px)",     2.0,  4.4, 0.2),
]

WORKSHOP_PARTS = {
    "head":       ["head", "snout_bridge", "nose", "ear_left", "ear_right", "eye_left", "eye_right",
                   "lid_left", "lid_right", "whisker_left", "whisker_right"],
    "jaw":        ["jaw", "tongue"],
    "neck":       ["neck"],
    "chest":      ["chest"],
    "ribs":       ["ribs"],
    "waist":      ["waist"],
    "hips":       ["hips", "rump"],
    "tail":       ["tail_1", "tail_2", "tail_3", "tail_4", "tail_5"],
    "front legs": ["leg_fl_1", "leg_fr_1"],
    "hind legs":  ["leg_bl_1", "leg_br_1"],
}

WORKSHOP_LOOK = "painted"   # "painted" (Stage 4 rich mottled fur) or "plain" (Stage 2 semantic colors)
TEXELS = 2.0                # 2 texture px per model px (crisp eyes, nostrils, toes & speckled chest!)


def total_length():
    return 35.2 + BODY_EXTRA


def oz(ox):
    """Outline x (0 tail tip .. total_length() snout tip) -> GeckoLib model z (+L/2 tail .. -L/2 snout)."""
    return total_length() / 2.0 - ox


def box(cubes, x0, x1, y0, y1, ox0, ox1, part, extra=None):
    lo = [min(x0, x1), min(y0, y1), min(oz(ox0), oz(ox1))]
    hi = [max(x0, x1), max(y0, y1), max(oz(ox0), oz(ox1))]
    if hi[0] - lo[0] < 0.04 or hi[1] - lo[1] < 0.04 or hi[2] - lo[2] < 0.04:
        return None
    c = cube(lo, hi, part)
    if extra:
        c.update(extra)
    cubes.append(c)
    return c


def sym_box(cubes, hw, y0, y1, ox0, ox1, part, extra=None):
    return box(cubes, -hw, hw, y0, y1, ox0, ox1, part, extra)


def lbox(cubes, x0, x1, y0, y1, z0, z1, part, extra=None):
    """Direct model-space box [x0..x1, y0..y1, z0..z1]."""
    lo = [min(x0, x1), min(y0, y1), min(z0, z1)]
    hi = [max(x0, x1), max(y0, y1), max(z0, z1)]
    if hi[0] - lo[0] < 0.04 or hi[1] - lo[1] < 0.04 or hi[2] - lo[2] < 0.04:
        return None
    c = cube(lo, hi, part)
    if extra:
        c.update(extra)
    cubes.append(c)
    return c


def otter_slice_z(cubes, z0, z1, y0, y1, hw, part_back="fur", part_belly="belly", belly_h=1.25, belly_hw=None):
    """Clean 3-tier rounded otter cross-section in local z [z0..z1] (belly keel + wide sleek flank + beveled back)."""
    h = y1 - y0
    if h < 1.2:
        lbox(cubes, -hw, hw, y0, y1, z0, z1, part_back)
        return
    bh = min(belly_h, h * 0.24)
    bhw = round(hw * 0.72, 2) if belly_hw is None else round(belly_hw, 2)
    lbox(cubes, -bhw, bhw, y0, y0 + bh + 0.25, z0, z1, part_belly)
    y_mid_top = y1 - min(1.30, h * 0.17)
    lbox(cubes, -round(hw, 2), round(hw, 2), y0 + bh * 0.75, y_mid_top, z0, z1, part_back)
    lbox(cubes, -round(hw * 0.80, 2), round(hw * 0.80, 2), y_mid_top - 0.35, y1, z0, z1, "fur_top")


def otter_slice(cubes, ox0, ox1, y0, y1, hw, part_back="fur", part_belly="belly", belly_h=1.25, belly_hw=None):
    return otter_slice_z(cubes, oz(ox1), oz(ox0), y0, y1, hw, part_back, part_belly, belly_h, belly_hw)


def _tail_chain(parent, ox_start, axis_y, length, segs, w_scale):
    """Smooth 5-segment pitched otter tail: muscular conical base tapering into a sleek slightly flattened rudder tip."""
    z_start = oz(ox_start)
    L = length / segs

    def height_at(t):
        return 2.50 + (axis_y - 2.50) * ((1.0 - t) ** 1.85)

    def radii_at(t):
        rw = (3.25 * (1.0 - 0.74 * t) ** 0.92) * w_scale
        rh = 3.25 * (1.0 - 0.76 * t) ** 0.95
        return max(0.75, rw), max(0.70, rh)

    angles = []
    for i in range(segs):
        drop = height_at(i / segs) - height_at((i + 1) / segs)
        angles.append(math.degrees(math.asin(max(-0.85, min(0.85, drop / L)))))

    out = []
    prev_ang = 0.0
    cur_parent = parent
    for i in range(segs):
        z0, z1 = z_start + i * L, z_start + (i + 1) * L
        t = (i + 0.5) / segs
        rw, rh = radii_at(t)
        d_ang = angles[i] - prev_ang
        back = rh * 1.15 * math.tan(math.radians(abs(d_ang))) + 0.55
        c = []
        t_info = {"_t": round(t, 3), "_ay": round(axis_y, 3), "_rh": round(rh, 3)}
        if min(rw, rh) < 1.15:
            lbox(c, -rw, rw, axis_y - rh, axis_y + rh, z0 - back, z1, "tail", extra=t_info)
        else:
            lbox(c, -rw, rw, axis_y - rh * 0.62, axis_y + rh * 0.62, z0 - back, z1, "tail", extra=t_info)
            lbox(c, -rw * 0.68, rw * 0.68, axis_y - rh, axis_y + rh, z0 - back, z1, "tail_top", extra=t_info)
        name = f"tail_{i + 1}"
        out.append({
            "name": name,
            "parent": cur_parent,
            "pivot": [0.0, round(axis_y, 3), round(z0, 3)],
            "rotation": pitch(d_ang),
            "cubes": c,
        })
        prev_ang = angles[i]
        cur_parent = name
    return out


def _build_leg(side_name, s, kind, w_scale, shift_ox=0.0, dy_c=0.0, dz_c=0.0):
    """Builds one sturdy mammalian 3-bone otter leg with sculpted outer haunch/shoulder sitting proud of the tucked flank."""
    cx = s * ((3.42 if kind == "front" else 3.52) * w_scale)

    def leg_box(cubes, x0, x1, wy0, wy1, wox0, wox1, part, extra=None):
        e = {"_ly0": wy0 + dy_c, "_ly1": wy1 + dy_c, "_s": s}
        if extra:
            e.update(extra)
        return lbox(cubes, x0, x1, wy0 + dy_c, wy1 + dy_c,
                    oz(wox1 + shift_ox) + dz_c, oz(wox0 + shift_ox) + dz_c, part, extra=e)

    def side_x(d_in, d_out):
        """Returns (x0, x1) measured inward (-s) and outward (+s) from cx."""
        return (cx - d_in, cx + d_out) if s > 0 else (cx - d_out, cx + d_in)

    if kind == "front":
        sh_ox, sh_y = 23.6, 8.8
        el_ox, el_y = 24.1, 4.7
        wr_ox, wr_y = 24.4, 1.55

        u_cubes = []
        # Muscular upper arm anchored into tucked chest, standing proud on the outside
        leg_box(u_cubes, *side_x(1.18, 1.38), 4.6, 9.6, 22.0, 25.5, "leg")
        # Sculpted outer shoulder blade cap
        leg_box(u_cubes, *side_x(-0.55, 1.54), 5.3, 9.1, 22.4, 25.2, "leg")
        # Trimmed elbow joint clear of the lower sternum
        leg_box(u_cubes, *side_x(0.98, 1.24), 4.0, 6.2, 22.7, 25.6, "leg")

        l_cubes = []
        leg_box(l_cubes, *side_x(0.96, 1.20), 1.35, 5.0, 23.0, 25.7, "leg")
        # Broader lower ankle cuff transitioning into the dark paw
        leg_box(l_cubes, *side_x(0.96, 1.18), 1.35, 2.75, 23.5, 26.05, "ankle")

        p_cubes = []
        # Broad flat dark clawless palm
        leg_box(p_cubes, cx - 1.44, cx + 1.44, 0.0, 1.52, 23.4, 25.85, "paw")
        # 3 crisp separated blocky finger-toes
        for ti, tx_off in enumerate((-1.04, 0.0, 1.04)):
            t_len = 1.88 if ti == 1 else 1.65
            leg_box(p_cubes, cx + tx_off - 0.36, cx + tx_off + 0.36, 0.0, 1.30,
                    25.65, 25.65 + t_len, "paw_toe", extra={"_toe": ti})

        return [
            {"name": f"leg_{side_name}_1", "parent": "chest",
             "pivot": [round(cx, 3), round(sh_y + dy_c, 3), round(oz(sh_ox + shift_ox) + dz_c, 3)], "cubes": u_cubes},
            {"name": f"leg_{side_name}_2", "parent": f"leg_{side_name}_1",
             "pivot": [round(cx, 3), round(el_y + dy_c, 3), round(oz(el_ox + shift_ox) + dz_c, 3)], "cubes": l_cubes},
            {"name": f"leg_{side_name}_paw", "parent": f"leg_{side_name}_2",
             "pivot": [round(cx, 3), round(wr_y + dy_c, 3), round(oz(wr_ox + shift_ox) + dz_c, 3)], "cubes": p_cubes},
        ]
    else:
        hp_ox, hp_y = 14.4, 8.4
        kn_ox, kn_y = 13.0, 4.5
        an_ox, an_y = 13.4, 1.55

        u_cubes = []
        # Muscular upper hind thigh sitting proudly outside the tucked hip/groin!
        leg_box(u_cubes, *side_x(1.18, 1.38), 4.5, 9.6, 12.2, 16.5, "leg")
        # Sculpted rounded outer haunch cap (keeps the otter silhouette lush while clearing the hips!)
        leg_box(u_cubes, *side_x(-0.55, 1.58), 5.0, 9.2, 12.5, 16.2, "leg")
        # Narrower inner knee/stifle stays completely outside the ±2.95 tucked lower pelvis
        leg_box(u_cubes, *side_x(0.92, 1.24), 3.8, 5.8, 11.9, 14.8, "leg")

        l_cubes = []
        leg_box(l_cubes, *side_x(0.92, 1.18), 1.35, 4.7, 11.8, 14.4, "leg")
        leg_box(l_cubes, *side_x(0.92, 1.16), 1.35, 2.75, 12.15, 14.85, "ankle")

        p_cubes = []
        leg_box(p_cubes, cx - 1.42, cx + 1.42, 0.0, 1.52, 12.2, 14.75, "paw")
        for ti, tx_off in enumerate((-1.02, 0.0, 1.02)):
            t_len = 1.85 if ti == 1 else 1.62
            leg_box(p_cubes, cx + tx_off - 0.36, cx + tx_off + 0.36, 0.0, 1.30,
                    14.55, 14.55 + t_len, "paw_toe", extra={"_toe": ti})

        return [
            {"name": f"leg_{side_name}_1", "parent": "hips",
             "pivot": [round(cx, 3), hp_y, round(oz(hp_ox), 3)], "cubes": u_cubes},
            {"name": f"leg_{side_name}_2", "parent": f"leg_{side_name}_1",
             "pivot": [round(cx, 3), kn_y, round(oz(kn_ox), 3)], "cubes": l_cubes},
            {"name": f"leg_{side_name}_paw", "parent": f"leg_{side_name}_2",
             "pivot": [round(cx, 3), an_y, round(oz(an_ox), 3)], "cubes": p_cubes},
        ]


def build_bones():
    """Builds the 2.6-block African River Otter rig (~106 boxes, well under the 250 medium-animal budget)."""
    w_scale = BODY_HW / 4.65
    bh = BACK_H - 12.0

    waist_ox0 = 17.6
    chest_ox0_world = 22.4 + BODY_EXTRA
    arch_rad = math.radians(ARCH_DEG)
    half_dx = (chest_ox0_world - waist_ox0) / 2.0
    seg_len = half_dx / max(0.5, math.cos(arch_rad))

    # --- HIPS & PITCHED RUMP (sculpted upper saddle + tucked lower groin so outer haunches swing cleanly!) -----------
    hips_cubes = []
    hz0, hz1 = oz(18.2), oz(13.0)
    # Tucked lower pelvis & belly (clears hind stifle/shin completely!)
    lbox(hips_cubes, -2.35 * w_scale, 2.35 * w_scale, 3.75, 5.15, hz0, hz1, "belly")
    lbox(hips_cubes, -2.85 * w_scale, 2.85 * w_scale, 4.55, 6.70, hz0, hz1, "fur")
    # Rounded upper hip saddle flowing cleanly into the proud outer haunches
    lbox(hips_cubes, -4.15 * w_scale, 4.15 * w_scale, 6.15, 10.65 + bh, hz0, hz1, "fur")
    lbox(hips_cubes, -3.55 * w_scale, 3.55 * w_scale, 10.30 + bh, 11.95 + bh, hz0, hz1, "fur_top")

    rump_ox = 13.6
    rump_y = 7.4 + bh * 0.5
    rump_z = oz(rump_ox)
    rump_cubes = []
    lbox(rump_cubes, -3.85 * w_scale, 3.85 * w_scale, rump_y - 3.3, rump_y + 3.25,
         rump_z - 0.6, rump_z + 3.1, "fur")
    lbox(rump_cubes, -3.15 * w_scale, 3.15 * w_scale, rump_y + 2.8, rump_y + 4.05,
         rump_z - 0.5, rump_z + 2.9, "fur_top")

    # --- WAIST & RIBS (sleek arched otter mid-torso tapering gently into the tucked flanks) --------------------------
    sp_y = 7.55 + bh * 0.5
    w_z0 = oz(waist_ox0)
    waist_cubes = []
    otter_slice_z(waist_cubes, w_z0 - seg_len - 0.75, w_z0 + 0.85, 3.15, 11.95 + bh, 4.42 * w_scale,
                  part_back="fur", part_belly="belly", belly_h=1.35, belly_hw=3.05 * w_scale)

    r_local_z = w_z0 - seg_len
    ribs_cubes = []
    otter_slice_z(ribs_cubes, r_local_z - seg_len - 0.75, r_local_z + 0.85, 3.15, 11.95 + bh, 4.46 * w_scale,
                  part_back="fur", part_belly="belly", belly_h=1.40, belly_hw=3.10 * w_scale)

    # --- CHEST (level in world space, tucked lower sternum for front leg clearance + rich speckled chest bib!) -------
    c_local_z = r_local_z - seg_len
    c_world_z = oz(chest_ox0_world)
    dy_c = 0.0
    dz_c = c_local_z - c_world_z

    def cbox(cubes, x0, x1, wy0, wy1, wox0, wox1, part, extra=None):
        return lbox(cubes, x0, x1, wy0 + dy_c, wy1 + dy_c,
                    oz(wox1 + BODY_EXTRA) + dz_c, oz(wox0 + BODY_EXTRA) + dz_c, part, extra)

    def csym(cubes, hw, wy0, wy1, wox0, wox1, part, extra=None):
        return cbox(cubes, -hw, hw, wy0, wy1, wox0, wox1, part, extra)

    chest_cubes = []
    cz0, cz1 = oz(25.8 + BODY_EXTRA) + dz_c, oz(21.8 + BODY_EXTRA) + dz_c
    # Tucked lower sternum & cream chest core (lets front arms/elbows swing cleanly on the outside!)
    lbox(chest_cubes, -2.95 * w_scale, 2.95 * w_scale, 3.45, 6.50, cz0, cz1, "chest_bib")
    lbox(chest_cubes, -4.20 * w_scale, 4.20 * w_scale, 6.05, 10.60 + bh * 0.95, cz0, cz1, "fur")
    lbox(chest_cubes, -3.55 * w_scale, 3.55 * w_scale, 10.25 + bh * 0.95, 11.90 + bh * 0.95, cz0, cz1, "fur_top")
    # Dappled cream chest bib curving between front shoulders
    csym(chest_cubes, 3.00 * w_scale, 3.8, 8.8 + bh * 0.5, 24.8, 26.55, "chest_bib")
    csym(chest_cubes, 2.20 * w_scale, 3.6, 8.0 + bh * 0.5, 25.2, 26.95, "chest_bib")

    # --- PITCHED SMOOTH NECK (sweeps smoothly UP & FORWARD at NECK_PITCH deg) ----------------------------------------
    nk_ox_v2 = 24.8
    nk_ox = nk_ox_v2 + BODY_EXTRA
    nk_y = 8.4 + bh * 0.5
    nk_z = oz(nk_ox) + dz_c
    neck_cubes = []
    nk_info = {"_nk_y": nk_y, "_nk_z": nk_z, "_nk_len": NECK_LEN}
    # Lower speckled cream throat/chest bib flowing continuously from chin into chest!
    lbox(neck_cubes, -3.50 * w_scale, 3.50 * w_scale, nk_y - 3.35, nk_y - 0.35,
         nk_z - NECK_LEN - 1.1, nk_z + 1.1, "throat_bib", extra=nk_info)
    lbox(neck_cubes, -2.50 * w_scale, 2.50 * w_scale, nk_y - 3.70, nk_y - 2.35,
         nk_z - NECK_LEN - 0.7, nk_z + 0.9, "throat_bib", extra=nk_info)
    # Main chocolate-brown upper neck & flanks (overlapping smoothly into shoulders & behind cheeks)
    lbox(neck_cubes, -4.15 * w_scale, 4.15 * w_scale, nk_y - 1.05, nk_y + 2.65,
         nk_z - NECK_LEN - 1.1, nk_z + 1.7, "neck_fur", extra=nk_info)
    lbox(neck_cubes, -3.35 * w_scale, 3.35 * w_scale, nk_y + 2.25, nk_y + 3.40,
         nk_z - NECK_LEN - 0.8, nk_z + 1.5, "fur_top", extra=nk_info)

    # --- HEAD (child of neck, counter-rotated back to level + HEAD_PITCH!) -------------------------------------------
    hd_local_y = nk_y
    hd_local_z = nk_z - NECK_LEN
    rad = math.radians(NECK_PITCH)
    hd_world_y = nk_y + NECK_LEN * math.sin(rad)
    hd_world_ox = nk_ox + NECK_LEN * math.cos(rad)
    dy_h = hd_local_y - hd_world_y
    dz_h = hd_local_z - oz(hd_world_ox)

    def hbox(cubes, x0, x1, wy0, wy1, wox0, wox1, part, extra=None):
        e = {"_hdy": dy_h, "_hdz": dz_h, "_ox0": wox0, "_ox1": wox1, "_wy0": wy0, "_wy1": wy1}
        if extra:
            e.update(extra)
        return lbox(cubes, x0, x1, wy0 + dy_h, wy1 + dy_h,
                    oz(wox1 + BODY_EXTRA) + dz_h, oz(wox0 + BODY_EXTRA) + dz_h, part, extra=e)

    def hsym(cubes, hw, wy0, wy1, wox0, wox1, part, extra=None):
        return hbox(cubes, -hw, hw, wy0, wy1, wox0, wox1, part, extra)

    head_cubes = []
    # Broad dark-brown upper skull behind & around eyes
    hsym(head_cubes, 4.35, 12.1, 15.35, 27.6, 32.5, "head_fur")
    # Flat stepped otter crown
    hsym(head_cubes, 3.55, 15.1, 16.0, 28.0, 32.2, "fur_top")
    # Forehead bridge between carved eye sockets
    hsym(head_cubes, 2.35, 12.4, 15.0, 31.4, 33.0, "head_fur")
    # Wide plush cream cheeks & rear lower skull
    hsym(head_cubes, 4.45, 10.1, 12.45, 28.0, 32.4, "cream_cheek")
    # Chunky cream upper muzzle / whisker pads
    hsym(head_cubes, 3.78, 10.45, 12.6, 31.8, 34.80, "muzzle_cream")
    # Fuller plush side whisker cushions (gives the adorable wide otter muzzle!)
    for s in (1, -1):
        hbox(head_cubes, s * 2.15, s * 4.08, 10.55, 12.30, 32.4, 34.48, "muzzle_pad")
    # Prominent velvety charcoal-brown rectangular otter nose pad
    # (its own bone since 2026-11-22, so it can twitch and puff as it sniffs; the same box in the same place)
    nose_info = {"_nz0": oz(35.32 + BODY_EXTRA) + dz_h, "_ny0": 11.45 + dy_h, "_ny1": 12.98 + dy_h}
    nose_cubes = []
    hsym(nose_cubes, 1.40, 11.45, 12.98, 34.25, 35.32, "nose", extra=nose_info)
    nose_piv = [0.0, round(12.2 + dy_h, 3), round(oz(34.25 + BODY_EXTRA) + dz_h, 3)]   # the back of the pad, its middle
    # Recessed upper palate roof (hidden when shut)
    hsym(head_cubes, 2.4, 10.5, 11.1, 30.2, 33.8, "mouth")

    # --- SMOOTH PITCHED SNOUT BRIDGE (Stage 3 detail: flush slope from forehead down to nose, zero overhang!) -------
    sb_piv = [0.0, round(13.65 + dy_h, 3), round(oz(32.3 + BODY_EXTRA) + dz_h, 3)]
    sb_cubes = []
    # Slopes cleanly downward (-16 deg) from forehead (ox 31.8) right behind the velvety dark nose pad (ox 34.45)!
    hbox(sb_cubes, -2.55, 2.55, 12.60, 14.30, 31.8, 34.45, "snout_top")
    hbox(sb_cubes, -2.05, 2.05, 13.70, 14.68, 31.6, 33.45, "snout_top")

    # --- EYES & EYELIDS ----------------------------------------------------------------------------------------------
    eye_l_cubes, eye_r_cubes = [], []
    lid_l_cubes, lid_r_cubes = [], []
    ey0, ey1 = 12.55, 14.05
    eox0, eox1 = 31.2, 32.85
    for s, ec, lc in ((1, eye_l_cubes, lid_l_cubes), (-1, eye_r_cubes, lid_r_cubes)):
        ex_in, ex_out = 2.35, 3.95
        x0, x1 = (ex_in, ex_out) if s > 0 else (-ex_out, -ex_in)
        eye_info = {
            "s": s,
            "x_in": s * ex_in,
            "y1": ey1 + dy_h,
            "z0": oz(eox1 + BODY_EXTRA) + dz_h,
            "w_front": ex_out - ex_in,
            "w_outer": eox1 - eox0,
            "h": ey1 - ey0,
        }
        hbox(ec, x0, x1, ey0, ey1, eox0, eox1, "eye", extra={"_eye": eye_info})
        lx0, lx1 = (ex_in - 0.05, ex_out + 0.08) if s > 0 else (-(ex_out + 0.08), -(ex_in - 0.05))
        hbox(lc, lx0, lx1, ey1 + 0.05, ey1 + 1.55, eox0 - 0.05, eox1 + 0.08, "head_fur")

    # --- SMALL ROUNDED EARS WITH DARK HOLLOW CUPS (matches hero_detail.png!) -----------------------------------------
    # EAR_STYLE (2026-11-22, the user: "make the ears a little bigger or redesign them ... 3 variations"): "v6" the
    # approved ears (small, on the skull's side, they hardly show above it), "A" bigger and poking up over the crown,
    # "B" as A with a pale rim round the dark hollow (reads against the dark head), "C" rounder, set out to the sides
    # and tipped out, with a pale inside. Picked on the faces page (python tools/otter_models.py faces).
    ear_l_cubes, ear_r_cubes = [], []
    for s, ec in ((1, ear_l_cubes), (-1, ear_r_cubes)):
        if EAR_STYLE == "v6":
            hbox(ec, s * 3.7, s * 4.88, 13.45, 15.18, 27.6, 29.15, "ear")
            hbox(ec, s * 4.02, s * 4.66, 13.75, 14.82, 28.92, 29.22, "ear_hollow")
        elif EAR_STYLE in ("A", "B"):
            hbox(ec, s * 3.45, s * 5.15, 13.5, 16.4, 27.3, 29.2, "ear")
            hbox(ec, s * 3.75, s * 4.85, 16.4, 16.95, 27.5, 29.0, "ear")
            if EAR_STYLE == "B":
                hbox(ec, s * 3.6, s * 5.0, 13.85, 16.25, 29.15, 29.25, "ear_rim")
                hbox(ec, s * 3.92, s * 4.68, 14.1, 15.85, 29.2, 29.32, "ear_hollow")
            else:
                hbox(ec, s * 3.85, s * 4.75, 14.0, 15.9, 29.15, 29.27, "ear_hollow")
        else:   # "C"
            hbox(ec, s * 3.9, s * 5.4, 14.0, 16.0, 27.5, 29.2, "ear")
            hbox(ec, s * 4.05, s * 5.25, 16.0, 16.7, 27.65, 29.05, "ear")
            hbox(ec, s * 4.3, s * 5.0, 16.7, 17.1, 27.85, 28.85, "ear")
            hbox(ec, s * 4.2, s * 5.1, 14.4, 16.4, 29.15, 29.27, "ear_inner")
    ear_x, ear_y = (4.6, 14.5) if EAR_STYLE == "C" else (4.0, 14.2)
    ear_tip = 18.0 if EAR_STYLE == "C" else 0.0

    # --- LOWER JAW & TONGUE ------------------------------------------------------------------------------------------
    jaw_wox, jaw_wy = 30.2, 10.45
    jaw_cubes = []
    hsym(jaw_cubes, 2.88, 9.25, 10.55, 29.6, 34.18, "chin_cream")
    hsym(jaw_cubes, 2.18, 8.9, 9.55, 29.6, 33.4, "throat_bib")
    hsym(jaw_cubes, 2.0, 10.2, 10.65, 30.2, 33.4, "mouth")
    tongue_cubes = []
    hsym(tongue_cubes, 1.3, 10.35, 10.85, 30.5, 33.2, "tongue")

    # --- GRACEFUL CURVED 3D WHISKERS (3 per cheek; smooth continuous downward/backward sweep!) -----------------------
    wh_l_cubes, wh_r_cubes = [], []
    seg1 = WHISKER_LEN * 0.50
    seg2 = WHISKER_LEN * 0.50
    t_w = 0.16
    wh_rows = [
        (11.90, 33.75,  0.04, -0.10, -0.10),
        (11.35, 33.95,  0.00, -0.14, -0.16),
        (10.80, 33.68, -0.06, -0.18, -0.22),
    ]
    for s, wc in ((1, wh_l_cubes), (-1, wh_r_cubes)):
        base_x = s * 3.72
        for wy, wox, dy1, dy2, dox in wh_rows:
            x1 = base_x + s * seg1
            y1 = wy + dy1
            hbox(wc, base_x - s * 0.20, x1 + s * 0.10, y1 - t_w * 0.5, y1 + t_w * 0.5,
                 wox - t_w * 0.5, wox + t_w * 0.5, "whisker")
            x2 = x1 + s * seg2
            y2 = y1 + dy2
            ox2 = wox + dox
            hbox(wc, x1 - s * 0.14, x2, y2 - t_w * 0.45, y2 + t_w * 0.45,
                 ox2 - t_w * 0.45, ox2 + t_w * 0.45, "whisker")

    # --- ASSEMBLE FULL BONE TREE -------------------------------------------------------------------------------------
    mid_ox = (waist_ox0 + chest_ox0_world) / 2.0
    body_piv = [0.0, round(sp_y, 3), round(oz(mid_ox), 3)]
    bones = [
        {"name": "root",  "pivot": [0.0, 0.0, 0.0], "cubes": []},
        {"name": "body",  "parent": "root", "pivot": body_piv, "cubes": []},
        {"name": "hips",  "parent": "body", "pivot": [0.0, round(sp_y, 3), round(w_z0, 3)], "cubes": hips_cubes},
        {"name": "rump",  "parent": "hips", "pivot": [0.0, round(rump_y, 3), round(rump_z, 3)],
         "rotation": pitch(19.0), "cubes": rump_cubes},
        *_tail_chain("hips", 11.0, 6.85 + bh * 0.45, TAIL_LEN, 5, w_scale),
        {"name": "waist", "parent": "body", "pivot": [0.0, round(sp_y, 3), round(w_z0, 3)],
         "rotation": pitch(ARCH_DEG), "cubes": waist_cubes},
        {"name": "ribs",  "parent": "waist", "pivot": [0.0, round(sp_y, 3), round(r_local_z, 3)],
         "rotation": pitch(-2.0 * ARCH_DEG), "cubes": ribs_cubes},
        {"name": "chest", "parent": "ribs", "pivot": [0.0, round(sp_y, 3), round(c_local_z, 3)],
         "rotation": pitch(ARCH_DEG), "cubes": chest_cubes},
        {"name": "neck",  "parent": "chest", "pivot": [0.0, round(nk_y, 3), round(nk_z, 3)],
         "rotation": pitch(NECK_PITCH), "cubes": neck_cubes},
        {"name": "head",  "parent": "neck",  "pivot": [0.0, round(hd_local_y, 3), round(hd_local_z, 3)],
         "rotation": pitch(-NECK_PITCH + HEAD_PITCH), "cubes": head_cubes},
        {"name": "snout_bridge", "parent": "head", "pivot": sb_piv,
         "rotation": pitch(-16.0), "cubes": sb_cubes},
        {"name": "nose", "parent": "head", "pivot": nose_piv, "cubes": nose_cubes},
        {"name": "ear_left",  "parent": "head",
         "pivot": [ear_x, round(ear_y + dy_h, 3), round(oz(28.4 + BODY_EXTRA) + dz_h, 3)], "cubes": ear_l_cubes,
         **({"rotation": [0.0, 0.0, ear_tip]} if ear_tip else {})},
        {"name": "ear_right", "parent": "head",
         "pivot": [-ear_x, round(ear_y + dy_h, 3), round(oz(28.4 + BODY_EXTRA) + dz_h, 3)], "cubes": ear_r_cubes,
         **({"rotation": [0.0, 0.0, -ear_tip]} if ear_tip else {})},
        {"name": "eye_left",  "parent": "head",
         "pivot": [3.15, round(13.3 + dy_h, 3), round(oz(32.0 + BODY_EXTRA) + dz_h, 3)], "cubes": eye_l_cubes},
        {"name": "eye_right", "parent": "head",
         "pivot": [-3.15, round(13.3 + dy_h, 3), round(oz(32.0 + BODY_EXTRA) + dz_h, 3)], "cubes": eye_r_cubes},
        {"name": "lid_left",  "parent": "head",
         "pivot": [3.15, round(14.1 + dy_h, 3), round(oz(32.0 + BODY_EXTRA) + dz_h, 3)], "cubes": lid_l_cubes},
        {"name": "lid_right", "parent": "head",
         "pivot": [-3.15, round(14.1 + dy_h, 3), round(oz(32.0 + BODY_EXTRA) + dz_h, 3)], "cubes": lid_r_cubes},
        {"name": "whisker_left",  "parent": "head",
         "pivot": [3.72, round(11.35 + dy_h, 3), round(oz(33.75 + BODY_EXTRA) + dz_h, 3)],
         "rotation": [0.0, -12.0, 15.0], "cubes": wh_l_cubes},
        {"name": "whisker_right", "parent": "head",
         "pivot": [-3.72, round(11.35 + dy_h, 3), round(oz(33.75 + BODY_EXTRA) + dz_h, 3)],
         "rotation": [0.0, 12.0, -15.0], "cubes": wh_r_cubes},
        {"name": "jaw",    "parent": "head",
         "pivot": [0.0, round(jaw_wy + dy_h, 3), round(oz(jaw_wox + BODY_EXTRA) + dz_h, 3)], "cubes": jaw_cubes},
        {"name": "tongue", "parent": "jaw",
         "pivot": [0.0, round(10.4 + dy_h, 3), round(oz(31.2 + BODY_EXTRA) + dz_h, 3)], "cubes": tongue_cubes},
        # the carry point (2026-11-22): where the lips meet at the jaw tips, level with the jaw's hinge (10.45 high,
        # the upper lip's underside; 10.65 sat inside the muzzle: Neon's diagnosis), 4.3 px ahead of it (further back
        # the catch was buried in the head: clip_check.py --hold); the game opens the jaw round a catch and turns the
        # catch down about the hinge with it (HeldCatch.ride, OtterRenderer)
        {"name": "carry",  "parent": "head",
         "pivot": [0.0, round(jaw_wy + dy_h, 3), round(oz(34.5 + BODY_EXTRA) + dz_h, 3)], "cubes": []},
        *_build_leg("fl",  1, "front", w_scale, BODY_EXTRA, dy_c, dz_c),
        *_build_leg("fr", -1, "front", w_scale, BODY_EXTRA, dy_c, dz_c),
        *_build_leg("bl",  1, "hind",  w_scale),
        *_build_leg("br", -1, "hind",  w_scale),
    ]
    return bones


# --- Stage 4: Rich Pixel-Art Fur Paint (matching hero_detail.png & key_details.png!) ---------------------------------
def _hash3(ix, iy, iz, seed=0):
    """Deterministic integer hash -> [0, 1) for crisp Minecraft fur pixel patchwork."""
    n = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def _voxel_noise(px, py, pz, cell=0.95, seed=0):
    """Blocky Minecraft-scale fur pixel noise (snapped to ~0.95 model px squares like the hero reference!)."""
    return _hash3(int(math.floor(px / cell)), int(math.floor(py / cell)), int(math.floor(pz / cell)), seed)


# Core African River Otter palette sampled directly from hero_detail.png & key_details.png:
FUR_DEEP     = (64, 42, 31)     # deep umber shadow fur
FUR_DARK     = (78, 51, 37)     # rich dark cocoa
FUR_MID      = (96, 63, 46)     # signature warm otter chocolate
FUR_WARM     = (114, 76, 55)    # sunlit chestnut fur patch
FUR_LIGHT    = (128, 88, 64)    # warm highlight fleck

CREAM_BRIGHT = (232, 206, 172)  # bright sunlit muzzle/cheek cream
CREAM_MID    = (214, 184, 148)  # warm oatmeal cream chest/throat
CREAM_WARM   = (194, 160, 124)  # shaded warm buff
CREAM_TAN    = (166, 130, 96)   # golden-tan fur transition edge
BELLY_TAN    = (156, 122, 90)   # warm sandy-tan underbelly

PAW_MID      = (60, 40, 31)     # dark otter paw
PAW_DARK     = (44, 29, 22)     # deep espresso clawless toes
PAW_HIGH     = (74, 50, 39)     # subtle upper toe bevel light


def _fur_coat(px, py, pz, top_bias=0.0, dark_bias=0.0, cell=0.92, seed=1):
    """Chunky Minecraft chocolate-umber fur patchwork matching hero_detail.png."""
    # Primary blocky fur tile + subtle larger blotch so fur reads organic, not uniform checkerboard
    h1 = _voxel_noise(px, py, pz, cell=cell, seed=seed)
    h2 = _voxel_noise(px, py, pz, cell=cell * 2.1, seed=seed + 17)
    v = h1 * 0.68 + h2 * 0.32 + top_bias - dark_bias
    if v < 0.24:
        col = FUR_DEEP
    elif v < 0.48:
        col = FUR_DARK
    elif v < 0.74:
        col = FUR_MID
    elif v < 0.90:
        col = FUR_WARM
    else:
        col = FUR_LIGHT
    # Tiny per-half-pixel micro-variation (keeps texture crisp without losing the chunky square fur feel)
    micro = (_voxel_noise(px, py, pz, cell=cell * 0.5, seed=seed + 53) - 0.5) * 0.06
    return shade(col, 1.0 + micro)


def _cream_bib(px, py, pz, half_bib=2.6, cell=0.88, seed=7):
    """Iconic dappled cream throat & chest bib with organic dark-brown speckles (matches Key Details middle inset!)."""
    ax = abs(px)
    # Jagged pixel-stepped outer border where rich brown fur wraps around the sides of the cream bib
    edge_jitter = (_voxel_noise(px, py, pz, cell=cell, seed=seed + 3) - 0.5) * 1.15
    if ax > half_bib + edge_jitter:
        # Transition fur on flanks of throat/chest
        if ax < half_bib + edge_jitter + 0.75:
            return CREAM_TAN if _voxel_noise(px, py, pz, cell=cell, seed=seed + 9) > 0.45 else FUR_MID
        return _fur_coat(px, py, pz, cell=cell, seed=seed + 2)

    # Inside the cream bib: bright warm oatmeal/cream base + crisp dappled brown speckles (Key Details middle inset!)
    sp = _voxel_noise(px, py, pz, cell=cell, seed=seed + 11)
    sp_clump = _voxel_noise(px * 1.25, py * 0.72, pz, cell=cell * 1.45, seed=seed + 29)
    edge_factor = max(0.0, (ax / max(0.5, half_bib)) - 0.52) * 0.34
    center_streak = 0.08 if ax < 0.75 else 0.0
    score = sp * 0.65 + sp_clump * 0.35 - edge_factor - center_streak
    if score < 0.16:
        return FUR_MID if sp < 0.11 else FUR_WARM
    if score < 0.28:
        return CREAM_TAN
    if score < 0.52:
        return CREAM_WARM
    if score < 0.78:
        return CREAM_MID
    return CREAM_BRIGHT


def _face_shading(col, face):
    """Subtle directional shading so vanilla flat-lit Minecraft still shows crisp depth & roundness."""
    if face == "up":
        return shade(col, 1.06)
    if face == "down":
        return shade(col, 0.82)
    if face in ("north", "south"):
        return shade(col, 0.98)
    return shade(col, 0.95)


def fur_colour(c, face, px, py, pz):
    part = c["_part"]

    # --- EYES (crisp dark glossy eye + 1-px warm-white upper-inner specular glint + subtle warm dark iris) ----------
    if part == "eye":
        info = c.get("_eye")
        if info and face == "north":
            u = abs(px - info["x_in"]) / max(0.01, info["w_front"])
            v = (info["y1"] - py) / max(0.01, info["h"])
            if 0.06 <= u <= 0.44 and 0.06 <= v <= 0.44:
                return (252, 252, 255)
            if u > 0.65 and v > 0.62:
                return (42, 26, 18)
        if info and face in ("east", "west"):
            u = (pz - info["z0"]) / max(0.01, info["w_outer"])
            v = (info["y1"] - py) / max(0.01, info["h"])
            if 0.06 <= u <= 0.42 and 0.06 <= v <= 0.44:
                return (252, 252, 255)
            if u > 0.65 and v > 0.62:
                return (42, 26, 18)
        return (16, 12, 10)

    # --- VELVETY CHARCOAL-BROWN NOSE PAD WITH NOSTRILS ---------------------------------------------------------------
    if part == "nose":
        if face == "up":
            return (48, 36, 30) if abs(px) < 0.85 else (36, 26, 22)
        if face == "north":
            # Recessed dark nostril dots on left & right of front nose face
            ny0, ny1 = c.get("_ny0", py - 0.5), c.get("_ny1", py + 0.5)
            vn = (ny1 - py) / max(0.01, ny1 - ny0)
            if 0.45 <= abs(px) <= 1.05 and 0.38 <= vn <= 0.72:
                return (16, 11, 10)
            if vn < 0.25:
                return (46, 34, 28)
            return (32, 23, 19)
        if face == "down":
            return (24, 17, 14)
        return (34, 24, 20)

    # --- WHISKERS, EARS, MOUTH, TONGUE -------------------------------------------------------------------------------
    if part == "whisker":
        # Bright off-white at roots, soft crisp white tips
        return (246, 242, 235) if abs(px) > 5.0 else (238, 230, 218)
    if part == "ear":
        col = _fur_coat(px, py, pz, dark_bias=0.14, cell=0.75, seed=19)
        return _face_shading(col, face)
    if part == "ear_hollow":
        return (36, 23, 17)
    if part == "ear_rim":
        return mix((232, 210, 178), (214, 190, 156), _voxel_noise(px, py, pz, cell=0.6, seed=29))
    if part == "ear_inner":
        return mix((196, 156, 138), (178, 138, 122), _voxel_noise(px, py, pz, cell=0.6, seed=31))
    if part == "mouth":
        return (152, 82, 86)
    if part == "tongue":
        return (196, 112, 118) if face == "up" else (172, 94, 100)

    # --- HEAD CROWN, SNOUT BRIDGE, CHEEKS, MUZZLE & CHIN -------------------------------------------------------------
    if part in ("head_fur", "snout_top"):
        # Slightly warmer nose-bridge fur right near the muzzle, deeper chocolate over crown
        dorsal = 0.08 if abs(px) < 1.6 and face == "up" else 0.0
        col = _fur_coat(px, py, pz, dark_bias=dorsal, cell=0.82, seed=5)
        # Subtle warm-tan cheek rim along bottom edge of upper skull
        wy0 = c.get("_wy0")
        hdy = c.get("_hdy", 0.0)
        if part == "head_fur" and wy0 is not None and (py - hdy) < wy0 + 0.55 and abs(px) > 2.2:
            col = mix(col, CREAM_TAN, 0.35)
        return _face_shading(col, face)

    if part in ("cream_cheek", "muzzle_cream", "muzzle_pad", "chin_cream"):
        # Plush warm cream cheeks & whisker cushions (Key Details left inset!)
        h1 = _voxel_noise(px, py, pz, cell=0.80, seed=23)
        wy0 = c.get("_wy0")
        wy1 = c.get("_wy1")
        hdy = c.get("_hdy", 0.0)
        if part == "muzzle_pad":
            col = CREAM_BRIGHT if h1 > 0.35 else CREAM_MID
        elif part == "muzzle_cream":
            # Soft honey-tan band right under the dark nose pad + crisp mouth shadow at bottom edge!
            if wy1 is not None and (py - hdy) > wy1 - 0.65 and abs(px) < 1.9:
                col = CREAM_TAN if h1 < 0.65 else CREAM_WARM
            elif wy0 is not None and (py - hdy) < wy0 + 0.40 and face == "north":
                col = CREAM_TAN
            else:
                col = CREAM_BRIGHT if h1 > 0.52 else (CREAM_MID if h1 > 0.18 else CREAM_WARM)
        elif part == "chin_cream":
            col = CREAM_MID if h1 > 0.38 else CREAM_WARM
            if face == "down":
                col = shade(col, 0.90)
        else:
            # Side cheeks: warm cream feathering organically into mottled chocolate brown at the back of the cheek!
            hdz = c.get("_hdz", 0.0)
            z_back = oz(28.0 + BODY_EXTRA) + hdz
            dist_from_back = z_back - pz   # 0 at rear edge of cheek, grows forward toward muzzle
            feather = (_voxel_noise(px, py, pz, cell=0.80, seed=31) - 0.5) * 1.15
            if face in ("east", "west") and dist_from_back < 1.35 + feather:
                col = _fur_coat(px, py, pz, cell=0.85, seed=5) if dist_from_back < 0.75 + feather else CREAM_TAN
            else:
                col = CREAM_BRIGHT if h1 > 0.52 else (CREAM_MID if h1 > 0.18 else CREAM_WARM)
        return _face_shading(col, face)

    # --- DAPPLED CREAM THROAT & CHEST BIB (matches Key Details middle inset!) ----------------------------------------
    if part in ("throat_bib", "chest_bib"):
        half_b = 2.75 if part == "throat_bib" else 2.55
        col = _cream_bib(px, py, pz, half_bib=half_b, cell=0.85, seed=7)
        return _face_shading(col, face)

    # --- NECK & TORSO COAT + UNDERBELLY ------------------------------------------------------------------------------
    if part == "neck_fur":
        # Lower edge of neck sides blends organically into cream speckles (hero_detail.png!)
        nk_y = c.get("_nk_y", py)
        if py < nk_y - 0.45 and _voxel_noise(px, py, pz, cell=0.85, seed=13) > 0.48:
            col = CREAM_TAN if py > nk_y - 0.85 else CREAM_MID
        else:
            col = _fur_coat(px, py, pz, dark_bias=0.04 if abs(px) < 1.8 else 0.0, cell=0.90, seed=3)
        return _face_shading(col, face)

    if part == "belly":
        h1 = _voxel_noise(px, py, pz, cell=0.90, seed=41)
        if face == "down":
            col = CREAM_WARM if h1 > 0.4 else BELLY_TAN
        else:
            # Side of lower belly shows warm tan peeking under chocolate flanks (side view!)
            col = BELLY_TAN if h1 > 0.35 else CREAM_TAN
        return _face_shading(col, face)

    if part in ("fur", "fur_top"):
        # Subtle darker dorsal spine band along the center back + warm chestnut flanks
        dorsal = 0.10 if (part == "fur_top" and abs(px) < 1.9) else 0.0
        warm_flank = 0.06 if (part == "fur" and face in ("east", "west") and py < 7.0) else 0.0
        col = _fur_coat(px, py, pz, top_bias=warm_flank, dark_bias=dorsal, cell=0.95, seed=1)
        return _face_shading(col, face)

    # --- SMOOTH TAPERING TAIL ----------------------------------------------------------------------------------------
    if part in ("tail", "tail_top"):
        t = c.get("_t", 0.5)
        ay = c.get("_ay", py)
        # Slightly darker top tail ridge & subtly darker sleek tip, warmer underside
        dark = 0.08 * t + (0.08 if part == "tail_top" and face == "up" else 0.0)
        col = _fur_coat(px, py, pz, dark_bias=dark, cell=0.92, seed=9)
        if face == "down":
            col = mix(col, BELLY_TAN, 0.25)
        return _face_shading(col, face)

    # --- LEGS, ANKLES & DARK CLAWLESS PAWS (Key Details right inset!) ------------------------------------------------
    if part == "leg":
        # Darkens naturally down the leg toward the ankle!
        ly0, ly1 = c.get("_ly0", 1.35), c.get("_ly1", 9.6)
        grad = max(0.0, min(1.0, (9.2 - py) / 7.8))   # 0 at shoulder/hip -> 1 near wrist/ankle
        col = _fur_coat(px, py, pz, dark_bias=0.06 + 0.28 * grad, cell=0.88, seed=15)
        return _face_shading(col, face)

    if part == "ankle":
        h1 = _voxel_noise(px, py, pz, cell=0.80, seed=17)
        col = mix(FUR_DEEP, PAW_MID, 0.55)
        col = shade(col, 0.92 + 0.16 * h1)
        return _face_shading(col, face)

    if part == "paw":
        h1 = _voxel_noise(px, py, pz, cell=0.75, seed=21)
        col = PAW_MID if h1 > 0.45 else PAW_DARK
        return _face_shading(col, face)

    if part == "paw_toe":
        # Crisp dark espresso toes with subtle lighter top ridge so the 3 separated fingers pop cleanly!
        h1 = _voxel_noise(px, py, pz, cell=0.70, seed=27)
        if face == "up":
            return shade(PAW_HIGH if h1 > 0.35 else PAW_MID, 1.02)
        if face == "north":
            return shade(PAW_DARK, 0.96 + 0.08 * h1)
        return _face_shading(PAW_DARK if h1 < 0.6 else PAW_MID, face)

    return _fur_coat(px, py, pz)


# --- Stage 2 plain fallback palette (switchable via --set WORKSHOP_LOOK=plain) ---------------------------------------
PLAIN_PALETTE = {
    "fur":          (92, 62, 46),
    "fur_top":      (80, 52, 38),
    "head_fur":     (96, 65, 48),
    "snout_top":    (88, 59, 44),
    "neck_fur":     (92, 62, 46),
    "tail":         (86, 57, 42),
    "tail_top":     (76, 49, 36),
    "belly":        (168, 134, 102),
    "chest_bib":    (214, 184, 146),
    "throat_bib":   (214, 184, 146),
    "cream_cheek":  (222, 194, 158),
    "muzzle_cream": (222, 194, 158),
    "muzzle_pad":   (232, 208, 174),
    "chin_cream":   (226, 200, 166),
    "leg":          (78, 52, 38),
    "ankle":        (62, 41, 31),
    "paw":          (54, 36, 28),
    "paw_toe":      (42, 27, 21),
    "ear":          (68, 44, 32),
    "ear_hollow":   (36, 23, 17),
    "ear_rim":      (228, 206, 172),
    "ear_inner":    (190, 150, 132),
    "nose":         (36, 24, 19),
    "whisker":      (244, 240, 234),
    "mouth":        (162, 92, 96),
    "tongue":       (196, 114, 120),
    "eye":          (18, 15, 14),
}


def plain_colour(c, face, px, py, pz):
    if c["_part"] == "eye":
        return fur_colour(c, face, px, py, pz)
    return PLAIN_PALETTE.get(c["_part"], (120, 90, 70))


# --- Stage 5: Otter Animations & Block Moves (motion_kit + exact bone-chain FK/IK) ----------------------------------
# Built for the approved 2.6-block v5 rig (BODY_EXTRA = 6.4, LENGTH = 41.6 px, 16 px = 1 block).
# Clip-matched base speeds (computed from stride & cycle length):
WALK_LEN = 0.80           # 4-beat sleek otter stroll
WALK_STEP = 4.6           # half-stride travel (px); full stance = 9.2 px over 0.64 duty -> 17.969 px/s (1.123 b/s)
WALK_DUTY = 0.64
WALK_SPEED = round((2.0 * WALK_STEP) / (WALK_LEN * WALK_DUTY), 3)

BOUND_LEN = 0.60          # energetic vertical-flexing otter lope/bound!
BOUND_STEP = 4.8          # full stance = 9.6 px over 0.50 duty -> 32.000 px/s (2.000 b/s sprint)
BOUND_DUTY = 0.50
BOUND_SPEED = round((2.0 * BOUND_STEP) / (BOUND_LEN * BOUND_DUTY), 3)

WATER_SURFACE = 11.3      # floating waterline in `swim` (eyes, ears, crown & dark nose cleanly above surface!)
MOVE_SIZES = (1.0,)       # adult 1.0 (pups at 0.55 use standard Minecraft step)
STEP_FACE = 24.0          # step/bank face distance ahead of root (1.5 blocks)
DOWN_EDGE = 12.0          # drop edge ahead of root (0.75 blocks)


def _rig_cache():
    """Caches rest bone pivots, rest rotations, and leg segment lengths from build_bones()."""
    bones = build_bones()
    by = {b["name"]: b for b in bones}
    world_p, world_m = {}, {}

    def solve_rest(name):
        if name in world_p:
            return world_p[name], world_m[name]
        b = by[name]
        par = b.get("parent")
        if par:
            pp, pm = solve_rest(par)
            rel = vsub(b["pivot"], by[par]["pivot"])
            wp = vadd(pp, mat_vec(pm, rel))
            wm = mat_mul(pm, euler(b.get("rotation", [0.0, 0.0, 0.0])))
        else:
            wp = list(b["pivot"])
            wm = euler(b.get("rotation", [0.0, 0.0, 0.0]))
        world_p[name], world_m[name] = wp, wm
        return wp, wm

    for b in bones:
        solve_rest(b["name"])

    legs = {}
    for pref, par in (("leg_fl", "chest"), ("leg_fr", "chest"), ("leg_bl", "hips"), ("leg_br", "hips")):
        s_p = world_p[f"{pref}_1"]
        e_p = world_p[f"{pref}_2"]
        w_p = world_p[f"{pref}_paw"]
        u1 = vsub(e_p, s_p)
        u2 = vsub(w_p, e_p)
        l1 = math.hypot(u1[1], u1[2])
        l2 = math.hypot(u2[1], u2[2])
        # Rest pitch angle of each segment in the Y-Z plane (measured from -Y toward -Z: positive = forward!)
        a1 = math.degrees(math.atan2(-u1[2], -u1[1]))
        a2 = math.degrees(math.atan2(-u2[2], -u2[1]))
        legs[pref] = {
            "parent": par,
            "s_rest": s_p, "e_rest": e_p, "w_rest": w_p,
            "l1": l1, "l2": l2, "a1": a1, "a2": a2,
            "paw_h": w_p[1],   # 1.55 px above ground sole
        }
    return {"by": by, "world_p": world_p, "world_m": world_m, "legs": legs}


_RIG = None


def _rig():
    global _RIG
    if _RIG is None:
        _RIG = _rig_cache()
    return _RIG


def _fk_bone(pose, name):
    """Exact world (pivot_pos, rot_mat) of a bone under a clip pose dict (same math as viewer_common.js & clip_check)."""
    by = _rig()["by"]
    chain = []
    cur = name
    while cur:
        chain.append(cur)
        cur = by[cur].get("parent")
    chain.reverse()
    wp = [0.0, 0.0, 0.0]
    wm = [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]
    prev_piv = [0.0, 0.0, 0.0]
    for bname in chain:
        b = by[bname]
        ch = pose.get(bname, {})
        dp = ch.get("position", [0.0, 0.0, 0.0])
        dr = ch.get("rotation", [0.0, 0.0, 0.0])
        rr = b.get("rotation", [0.0, 0.0, 0.0])
        rel = [b["pivot"][i] - prev_piv[i] + dp[i] for i in range(3)]
        wp = vadd(wp, mat_vec(wm, rel))
        wm = mat_mul(wm, euler([rr[i] + dr[i] for i in range(3)]))
        prev_piv = b["pivot"]
    return wp, wm


def _parent_pitch_deg(wm):
    """Extracts the sagittal pitch angle (deg, positive = nose UP / -Y swung toward -Z) from a world rot matrix."""
    # Downward vector (0, -1, 0) in bone space maps to wm @ [0, -1, 0] = [-wm[0][1], -wm[1][1], -wm[2][1]]
    dy = -wm[1][1]
    dz = -wm[2][1]
    return math.degrees(math.atan2(-dz, -dy))


def _solve_leg(pose, pref, target_y_sole, target_z, paw_world_pitch=0.0, pole_z=1.0, roll_z=0.0, pole=None):
    """Solves 2-bone IK in the sagittal (Y-Z) plane for one otter leg, pinning the paw sole cleanly to (target_y_sole, target_z).

    pole_z = +1.0 bends elbow/hock backward (+Z, natural mammalian anatomy!).
    paw_world_pitch: desired world pitch of paw (deg; + = toes pitched UP, - = toes pitched DOWN).
    """
    info = _rig()["legs"][pref]
    # Exact current world position & pitch of leg socket (parent bone under current spine pose!)
    par_p, par_m = _fk_bone(pose, info["parent"])
    by = _rig()["by"]
    rel_s = vsub(by[f"{pref}_1"]["pivot"], by[info["parent"]]["pivot"])
    s_now = vadd(par_p, mat_vec(par_m, rel_s))
    par_pitch = _parent_pitch_deg(par_m)

    # Target wrist position in world space (paw_h = 1.55 px above target sole)
    w_target = [s_now[0], target_y_sole + info["paw_h"], target_z]
    e_now, w_now, _miss = two_bone(s_now, info["l1"], info["l2"], w_target, pole or [0.0, 0.0, pole_z], reach=0.985)

    v1 = vsub(e_now, s_now)
    v2 = vsub(w_now, e_now)
    ang1_w = math.degrees(math.atan2(-v1[2], -v1[1]))
    ang2_w = math.degrees(math.atan2(-v2[2], -v2[1]))

    # Convert world sagittal angles (positive = forward -Z) to GeckoLib rx (negative = forward -Z!)
    rx1 = -(ang1_w - par_pitch - info["a1"])
    rx2 = -((ang2_w - ang1_w) - (info["a2"] - info["a1"]))
    rx_paw = -(paw_world_pitch - (ang2_w - info["a2"]))

    pose[f"{pref}_1"] = {"rotation": [rx1, 0.0, roll_z]}
    pose[f"{pref}_2"] = {"rotation": [rx2, 0.0, 0.0]}
    pose[f"{pref}_paw"] = {"rotation": [rx_paw, 0.0, 0.0]}


def _set_lids(pose, closed):
    """closed: 0.0 (fully open, tucked inside brow) .. 1.0 (fully shut over eyes)."""
    y = -1.55 * max(0.0, min(1.0, closed))
    pose["lid_left"] = {"position": [0.0, y, 0.0]}
    pose["lid_right"] = {"position": [0.0, y, 0.0]}


def _set_tail(pose, pitch_fn, yaw_fn=None, roll_fn=None):
    """Sets all 5 tail segments smoothly (pitch_fn(i): positive = tail lifts UP, negative = droops DOWN)."""
    for i in range(5):
        p = pitch_fn(i)
        y = yaw_fn(i) if yaw_fn else 0.0
        r = roll_fn(i) if roll_fn else 0.0
        # Remember: for +Z tail bones, GeckoLib rx > 0 lifts tail UP, rx < 0 bends DOWN!
        pose[f"tail_{i + 1}"] = {"rotation": [p, y, r]}


def _set_whiskers(pose, twitch_l=0.0, twitch_r=0.0):
    pose["whisker_left"] = {"rotation": [0.0, twitch_l, twitch_l * 0.4]}
    pose["whisker_right"] = {"rotation": [0.0, -twitch_r, -twitch_r * 0.4]}


def _set_ears(pose, pitch_deg=0.0, yaw_deg=0.0):
    pose["ear_left"] = {"rotation": [pitch_deg, yaw_deg, 0.0]}
    pose["ear_right"] = {"rotation": [pitch_deg, -yaw_deg, 0.0]}


def _blink_curve(t, times, dur=0.18):
    v = 0.0
    for tc in times:
        if tc <= t <= tc + dur:
            u = (t - tc) / dur
            v = max(v, math.sin(math.pi * u))
    return v


def _sniff_puffs(t, bouts, rate=6.0, dur=0.11):
    """How far into a sniff the nose is (0..1) at time t: bouts of quick puffs, each bout (start, puffs), rate a second."""
    return _blink_curve(t, [b + k / rate for b, n in bouts for k in range(n)], dur=dur)


def _set_nose(pose, puff, twitch=0.0):
    """A sniff: the nose pad lifts a little and pushes forward, tipped up, its nostrils flaring wider (puff 0..1); twitch
    (-1..1) wrinkles it side to side. Real noses work this way: a quick in-breath draws the nose up and the nostrils out."""
    pose["nose"] = {"position": [0.25 * twitch, 0.38 * puff, -0.26 * puff],
                    "rotation": [-10.0 * puff, 5.0 * twitch, 0.0],
                    "scale": [1.0 + 0.22 * puff, 1.0 + 0.08 * puff, 1.0]}


# --- 1. IDLE (3.60 s, loop) ------------------------------------------------------------------------------------------
IDLE_LEN = 3.60


def _idle_pose(t):
    """The idle clip's pose at time t (the faces page draws the faces on it)."""
    L = IDLE_LEN
    if True:
        w = 2.0 * math.pi * t / L
        breath = math.sin(w)
        look_y = 9.0 * math.sin(w - 0.5) * pulse(t, 0.4, 0.5, 1.4, 0.7)
        look_p = -3.0 * math.sin(w * 2.0) * 0.5
        wh = 5.5 * math.sin(w * 6.0) * pulse(t, 1.1, 0.2, 0.5, 0.25)

        p = {
            "body":  {"position": [0.0, 0.14 * breath, 0.0], "rotation": [0.0, 0.0, 0.0]},
            "hips":  {"rotation": [0.6 * breath, 0.0, 0.0]},
            "waist": {"rotation": [-1.2 * breath, 0.0, 0.0]},
            "ribs":  {"rotation": [0.8 * breath, 0.0, 0.0]},
            "chest": {"rotation": [0.4 * breath, 0.0, 0.0]},
            "neck":  {"rotation": [-1.5 * breath + 0.35 * look_p, 0.35 * look_y, 0.0]},
            "head":  {"rotation": [1.2 * breath + 0.65 * look_p, 0.65 * look_y, 0.18 * look_y]},
            "jaw":   {"rotation": [0.8 * (0.5 + 0.5 * breath), 0.0, 0.0]},
        }
        _set_lids(p, _blink_curve(t, (0.75, 2.35, 2.60)))
        _set_whiskers(p, wh, wh)
        _set_ears(p, -2.0 * breath, 3.0 * math.sin(w * 2.0))
        _set_tail(p, lambda i: 1.4 * math.sin(w - 0.35 * i), lambda i: (2.2 + 0.9 * i) * math.sin(w - 0.45 * i))
        # Pin all 4 paws firmly at rest on the ground!
        for pref in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
            wr = _rig()["legs"][pref]["w_rest"]
            _solve_leg(p, pref, 0.0, wr[2], 0.0)
        return p


def _clip_idle():
    return clip(IDLE_LEN, _idle_pose, 36, loop=True)


# --- 2. WALK (0.80 s, loop — sleek 4-beat otter prowl with exact planted paws!) --------------------------------------
def _clip_walk():
    L = WALK_LEN
    phases = {"leg_bl": 0.00, "leg_fl": 0.25, "leg_br": 0.50, "leg_fr": 0.75}

    def at(t):
        u = (t % L) / L
        w = 2.0 * math.pi * u
        bob = -0.42 + 0.24 * math.cos(2.0 * w)
        sway_y = 0.9 * math.sin(w)
        roll = 1.4 * math.sin(w)

        p = {
            "body":  {"position": [0.0, bob, 0.0], "rotation": [1.0 * math.sin(2.0 * w), 0.0, roll]},
            "hips":  {"rotation": [1.2 * math.sin(2.0 * w + 0.6), -sway_y * 0.6, -roll * 0.3]},
            "waist": {"rotation": [-1.4 * math.cos(2.0 * w), sway_y * 0.5, 0.0]},
            "ribs":  {"rotation": [1.4 * math.cos(2.0 * w), sway_y * 0.5, 0.0]},
            "chest": {"rotation": [-0.8 * math.sin(2.0 * w), -sway_y * 0.3, -roll * 0.4]},
            "neck":  {"rotation": [3.0 + 1.2 * math.cos(2.0 * w), -sway_y * 0.3, -roll * 0.3]},
            "head":  {"rotation": [-3.0 - 1.2 * math.cos(2.0 * w), -sway_y * 0.5, -roll * 0.3]},
            "jaw":   {"rotation": [1.0, 0.0, 0.0]},
        }
        _set_lids(p, 0.0)
        _set_whiskers(p, 2.5 * math.sin(w), 2.5 * math.sin(w))
        _set_ears(p, -3.0, 2.0 * math.sin(w))
        _set_tail(p, lambda i: 3.0 + 1.1 * math.sin(2.0 * w - 0.5 * i),
                  lambda i: (3.2 + 1.4 * i) * math.sin(w - 0.55 * i + 0.8))

        for pref, ph in phases.items():
            wr = _rig()["legs"][pref]["w_rest"]
            s_norm, l_norm = stride((u + ph) % 1.0, WALK_DUTY)
            tz = wr[2] - s_norm * WALK_STEP
            s_out = 1.0 if pref in ("leg_fl", "leg_bl") else -1.0
            # Subtle outward shoulder & hip clearance keeps sculpted outer legs cleanly clear of the tucked flanks!
            rz = s_out * (-8.5 if pref in ("leg_fl", "leg_fr") else -6.5)
            _solve_leg(p, pref, l_norm * 1.9, tz, paw_world_pitch=-12.0 * l_norm, roll_z=rz)
        return p

    out = clip(L, at, 32, loop=True)
    out["timeline"] = {"0": f"stride {WALK_SPEED:.3f}"}
    return out


# --- 3. BOUND (0.60 s, loop — signature vertical-flexing otter lope/bound!) ------------------------------------------
def _clip_bound():
    L = BOUND_LEN
    # Asymmetric otter lope: hind pair pushes together (0.00 / 0.06), front pair reaches together (0.50 / 0.56)
    phases = {"leg_bl": 0.00, "leg_br": 0.06, "leg_fl": 0.50, "leg_fr": 0.56}

    def at(t):
        u = (t % L) / L
        w = 2.0 * math.pi * u
        # Low & coiled while hind/front stance pushes, slight springy lift at mid-bound
        flex = math.sin(w)
        bounce = -0.58 + 0.26 * math.sin(2.0 * w - 0.5)
        body_p = 3.5 * math.cos(w)

        p = {
            "body":  {"position": [0.0, bounce, 0.0], "rotation": [body_p, 0.0, 0.0]},
            "hips":  {"rotation": [5.5 * flex, 0.0, 0.0]},
            "waist": {"rotation": [-7.5 * flex, 0.0, 0.0]},
            "ribs":  {"rotation": [9.5 * flex, 0.0, 0.0]},
            "chest": {"rotation": [-6.0 * flex, 0.0, 0.0]},
            "neck":  {"rotation": [5.0 - 4.0 * math.cos(w), 0.0, 0.0]},
            "head":  {"rotation": [-4.5 - body_p * 0.65, 0.0, 0.0]},
            "jaw":   {"rotation": [4.0 + 3.0 * max(0.0, flex), 0.0, 0.0]},
        }
        _set_lids(p, 0.0)
        _set_whiskers(p, 4.0 * flex, 4.0 * flex)
        _set_ears(p, -8.0, 0.0)
        # Athletic balancing whip down the 5-bone tail (lifted cleanly above y > 1.0!)
        _set_tail(p, lambda i: (10.0 if i == 0 else 4.5) + (3.2 + 0.9 * i) * math.sin(w - 0.45 * i),
                  lambda i: 1.8 * math.sin(w - 0.4 * i))

        for pref, ph in phases.items():
            wr = _rig()["legs"][pref]["w_rest"]
            s_norm, l_norm = stride((u + ph) % 1.0, BOUND_DUTY)
            tz = wr[2] - s_norm * BOUND_STEP
            s_out = 1.0 if pref in ("leg_fl", "leg_bl") else -1.0
            rz = s_out * (-9.0 if pref in ("leg_fl", "leg_fr") else -7.5)
            _solve_leg(p, pref, l_norm * 2.5, tz, paw_world_pitch=-14.0 * l_norm, roll_z=rz)
        return p

    out = clip(L, at, 30, loop=True)
    out["timeline"] = {"0": f"stride {BOUND_SPEED:.3f}"}
    return out


# --- 4. SWIM (1.20 s, loop — Swimming panel: crown/eyes/ears/nose above waterline, vertical tail wave!) --------------
def _clip_swim():
    L = 1.20

    def at(t):
        u = (t % L) / L
        w = 2.0 * math.pi * u
        # Waterline sits at y = 11.3 px: lower neck ~-16 deg and counter-pitch head so face rides sleekly on top!
        p = {
            "body":  {"position": [0.0, -0.35 + 0.35 * math.sin(w), 0.0],
                      "rotation": [-2.0 + 2.2 * math.sin(w), 0.0, 1.5 * math.sin(w * 0.5)]},
            "hips":  {"rotation": [3.0 * math.sin(w - 0.4), 0.0, 0.0]},
            "waist": {"rotation": [-3.0 - 3.5 * math.sin(w - 0.1), 0.0, 0.0]},
            "ribs":  {"rotation": [6.5 + 3.0 * math.sin(w + 0.2), 0.0, 0.0]},
            "chest": {"rotation": [-3.5 - 2.0 * math.sin(w + 0.4), 0.0, 0.0]},
            "neck":  {"rotation": [16.0 - 2.5 * math.sin(w), 0.0, 0.0]},
            "head":  {"rotation": [-14.0 + 2.0 * math.sin(w), 2.5 * math.sin(w), 0.0]},
            "jaw":   {"rotation": [1.5, 0.0, 0.0]},
        }
        _set_lids(p, _blink_curve(t, (0.55,)))
        _set_whiskers(p, 5.0, 5.0)
        _set_ears(p, -10.0, -6.0)
        # Vertical undulating rudder tail just beneath the surface (lifted ~+7 deg at base so tail trails horizontally!)
        _set_tail(p, lambda i: (7.0 if i == 0 else 2.2) + (5.5 + 2.0 * i) * math.sin(w - 0.65 * i - 0.8),
                  lambda i: 2.0 * math.sin(w - 0.5 * i))
        # Forelegs swept neatly back outside flanks; hind legs kicking rhythmically back-and-out clear of hips!
        for pref, s_out, ph in (("leg_fl", 1.0, 0.0), ("leg_fr", -1.0, math.pi)):
            k = math.sin(w + ph)
            p[f"{pref}_1"] = {"rotation": [46.0 + 8.0 * k, 0.0, s_out * -9.0]}
            p[f"{pref}_2"] = {"rotation": [-32.0 + 10.0 * k, 0.0, 0.0]}
            p[f"{pref}_paw"] = {"rotation": [28.0 + 12.0 * k, 0.0, 0.0]}
        for pref, s_out, ph in (("leg_bl", 1.0, 0.0), ("leg_br", -1.0, 0.35)):
            k = math.sin(w - 0.5 + ph)
            p[f"{pref}_1"] = {"rotation": [52.0 + 18.0 * k, 0.0, s_out * -11.0]}
            p[f"{pref}_2"] = {"rotation": [-24.0 - 14.0 * k, 0.0, 0.0]}
            p[f"{pref}_paw"] = {"rotation": [32.0 + 20.0 * k, 0.0, 0.0]}
        return p

    return clip(L, at, 32, loop=True)


# --- 5. DIVE_SWIM (0.90 s, loop — Diving panel: sleek underwater torpedo, forelegs tucked, dolphin undulation!) ------
def _clip_dive_swim():
    L = 0.90

    def at(t):
        u = (t % L) / L
        w = 2.0 * math.pi * u
        p = {
            "body":  {"position": [0.0, 0.35 + 0.45 * math.sin(w), 0.0],
                      "rotation": [6.5 + 3.5 * math.sin(w), 0.0, 0.0]},
            "hips":  {"rotation": [4.5 * math.sin(w - 0.5), 0.0, 0.0]},
            "waist": {"rotation": [-3.5 - 5.0 * math.sin(w - 0.15), 0.0, 0.0]},
            "ribs":  {"rotation": [7.0 + 4.5 * math.sin(w + 0.25), 0.0, 0.0]},
            "chest": {"rotation": [-3.5 - 3.0 * math.sin(w + 0.5), 0.0, 0.0]},
            "neck":  {"rotation": [26.0 - 3.5 * math.sin(w + 0.4), 0.0, 0.0]},
            "head":  {"rotation": [-24.0 + 2.5 * math.sin(w + 0.4), 0.0, 0.0]},
            "jaw":   {"rotation": [0.5, 0.0, 0.0]},
        }
        _set_lids(p, 0.0)
        _set_whiskers(p, 10.0, 10.0)
        _set_ears(p, -20.0, -12.0)
        # Sleek vertical otter dolphin-wave propelling the tail (clean blend into swim!)
        _set_tail(p, lambda i: (7.2 if i == 0 else 2.2) + (4.8 + 1.6 * i) * math.sin(w - 0.65 * i - 0.8),
                  lambda i: 1.5 * math.sin(w - 0.5 * i))
        for pref, s_out in (("leg_fl", 1.0), ("leg_fr", -1.0)):
            p[f"{pref}_1"] = {"rotation": [62.0 + 4.0 * math.sin(w), 0.0, s_out * -9.0]}
            p[f"{pref}_2"] = {"rotation": [-24.0, 0.0, 0.0]}
            p[f"{pref}_paw"] = {"rotation": [34.0, 0.0, 0.0]}
        for pref, s_out, ph in (("leg_bl", 1.0, 0.0), ("leg_br", -1.0, 0.2)):
            k = math.sin(w - 0.6 + ph)
            p[f"{pref}_1"] = {"rotation": [58.0 + 18.0 * k, 0.0, s_out * -11.0]}
            p[f"{pref}_2"] = {"rotation": [-22.0 - 14.0 * k, 0.0, 0.0]}
            p[f"{pref}_paw"] = {"rotation": [36.0 + 20.0 * k, 0.0, 0.0]}
        return p

    return clip(L, at, 30, loop=True)


# --- 6. FORAGE_FEEL (1.80 s, loop — signature Aonyx capensis crab-hunting: feeling under stones with fingers!) -------
def _clip_forage_feel():
    L = 1.80

    def at(t):
        u = (t % L) / L
        w = 2.0 * math.pi * u
        feel_l = pulse(t, 0.12, 0.22, 0.34, 0.22)
        feel_r = pulse(t, 1.00, 0.22, 0.34, 0.22)
        pat_l = math.sin(2.0 * math.pi * (t - 0.12) / 0.24) * feel_l
        pat_r = math.sin(2.0 * math.pi * (t - 1.00) / 0.24) * feel_r

        p = {
            "body":  {"position": [0.0, -0.55, 0.0], "rotation": [4.0, 0.0, 0.0]},
            "hips":  {"rotation": [-2.5, 0.0, 0.0]},
            "waist": {"rotation": [1.8, 0.0, 0.0]},
            "ribs":  {"rotation": [1.8, 0.0, 0.0]},
            "chest": {"rotation": [2.2, 2.0 * (feel_l - feel_r), 0.0]},
            "neck":  {"rotation": [22.0 + 3.0 * math.sin(2.0 * w), 5.0 * (feel_l - feel_r), 0.0]},
            "head":  {"rotation": [-5.0 + 4.0 * math.sin(4.0 * w), 6.0 * (feel_l - feel_r), 0.0]},
            "jaw":   {"rotation": [2.5, 0.0, 0.0]},
        }
        _set_lids(p, _blink_curve(t, (0.88,)))
        _set_whiskers(p, 7.0 * math.sin(6.0 * w), 7.0 * math.sin(6.0 * w))
        _set_nose(p, _sniff_puffs(t, ((0.70, 3), (1.50, 2)), dur=0.13), 0.4 * (feel_l - feel_r))
        _set_ears(p, 4.0, 5.0)
        _set_tail(p, lambda i: 5.5 + 1.5 * math.sin(w - 0.4 * i), lambda i: (3.0 + i) * math.sin(w - 0.5 * i))

        for pref in ("leg_bl", "leg_br"):
            wr = _rig()["legs"][pref]["w_rest"]
            _solve_leg(p, pref, 0.0, wr[2], 0.0)
        for pref, s_out, f_amp, pat in (("leg_fl", 1.0, feel_l, pat_l), ("leg_fr", -1.0, feel_r, pat_r)):
            wr = _rig()["legs"][pref]["w_rest"]
            # Lifted cleanly above ground while feeling/patting (never clips toes into riverbed!)
            y_sole = f_amp * (1.55 + 0.65 * pat)
            z_tgt = wr[2] - f_amp * (3.1 + 0.6 * pat)
            _solve_leg(p, pref, y_sole, z_tgt, paw_world_pitch=8.0 * f_amp + 10.0 * pat, roll_z=s_out * -10.5)
        return p

    return clip(L, at, 72, loop=True)


# --- 7. BITE (0.60 s, once — crisp hunting lunge & snap; hit on tick 6 = 0.30 s!) ------------------------------------
def _clip_bite():
    L = 0.60

    def at(t):
        coil = pulse(t, 0.0, 0.14, 0.02, 0.10)
        lunge = pulse(t, 0.14, 0.14, 0.06, 0.24)
        jaw = ramp(t, ((0.0, 0.0), (0.14, 10.0), (0.25, 42.0), (0.30, 3.0), (0.38, 12.0), (0.60, 0.0)))
        dz = 1.2 * coil - 2.3 * lunge
        dy = -0.55 * coil - 0.35 * lunge

        p = {
            "body":  {"position": [0.0, dy, dz], "rotation": [-3.0 * coil + 4.5 * lunge, 0.0, 0.0]},
            "hips":  {"rotation": [3.0 * coil - 3.0 * lunge, 0.0, 0.0]},
            "waist": {"rotation": [-4.0 * coil + 3.0 * lunge, 0.0, 0.0]},
            "ribs":  {"rotation": [4.0 * coil - 3.0 * lunge, 0.0, 0.0]},
            "chest": {"rotation": [-2.5 * coil + 4.0 * lunge, 0.0, 0.0]},
            "neck":  {"rotation": [-8.0 * coil + 20.0 * lunge, 0.0, 0.0]},
            "head":  {"rotation": [6.0 * coil - 14.0 * lunge, 0.0, 0.0]},
            "jaw":   {"rotation": [jaw, 0.0, 0.0]},
            "tongue": {"rotation": [-0.25 * jaw, 0.0, 0.0]},
        }
        _set_lids(p, 0.35 * pulse(t, 0.24, 0.04, 0.06, 0.10))
        _set_whiskers(p, 8.0 * lunge, 8.0 * lunge)
        _set_ears(p, -14.0 * lunge, -10.0 * lunge)
        _set_tail(p, lambda i: 5.5 + 3.0 * lunge, lambda i: 2.0 * math.sin(2.0 * math.pi * t / L - 0.4 * i))
        for pref in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
            wr = _rig()["legs"][pref]["w_rest"]
            is_f = pref in ("leg_fl", "leg_fr")
            s_out = 1.0 if pref in ("leg_fl", "leg_bl") else -1.0
            step_z = -1.9 * lunge if is_f else -1.1 * lunge
            _solve_leg(p, pref, 0.0, wr[2] + step_z, 0.0, roll_z=s_out * (-10.5 if is_f else 0.0))
        return p

    return clip(L, at, 24, loop=False)


# --- GULP (0.80 s, once; 2026-11-22): a small fish swallowed at once (the user: "super small fish are going to mostly
# just be swallowed"). Only the neck, head, jaw and tongue: the game plays it on its own layer over whatever the body
# does (swimming, standing), so it keys nothing else. The nose tosses up, the jaw opens on the fish and snaps shut on it
# (0.30 s: the game takes the fish then), then two swallows, a little dip of the head with each; my picks.
def _clip_gulp():
    L = 0.80

    def at(t):
        neck = ramp(t, ((0.0, 0.0), (0.12, -14.0), (0.28, -18.0), (0.40, -6.0), (0.55, -8.0), (0.80, 0.0)))
        head = ramp(t, ((0.0, 0.0), (0.12, -12.0), (0.28, -16.0), (0.40, -4.0), (0.50, 4.0), (0.60, -2.0), (0.68, 3.0),
                        (0.80, 0.0)))
        jaw = ramp(t, ((0.0, 15.0), (0.10, 26.0), (0.26, 30.0), (0.32, 0.0), (0.44, 6.0), (0.50, 0.0), (0.58, 5.0),
                       (0.64, 0.0), (0.80, 0.0)))
        return {
            "neck":  {"rotation": [neck, 0.0, 0.0]},
            "head":  {"rotation": [head, 0.0, 0.0]},
            "jaw":   {"rotation": [jaw, 0.0, 0.0]},
            "tongue": {"rotation": [-0.25 * jaw, 0.0, 0.0]},
        }

    return clip(L, at, 32, loop=False)


# --- 8. EAT (1.60 s, loop — Fishing/Eating panel: crouching happy crunch on caught fish/crab!) -----------------------
def _clip_eat():
    L = 1.60

    def at(t):
        u = (t % L) / L
        w = 2.0 * math.pi * u
        chew = 0.5 - 0.5 * math.cos(4.0 * w)   # 4 crisp crunches per loop
        p = {
            "body":  {"position": [0.0, -0.85, 0.0], "rotation": [4.0, 0.0, 0.0]},
            "hips":  {"rotation": [-2.0, 0.0, 0.0]},
            "waist": {"rotation": [1.5, 0.0, 0.0]},
            "ribs":  {"rotation": [1.5, 0.0, 0.0]},
            "chest": {"rotation": [2.0, 0.0, 0.0]},
            "neck":  {"rotation": [18.0 + 3.5 * math.sin(2.0 * w), 3.0 * math.sin(w), 0.0]},
            "head":  {"rotation": [-4.0 + 5.0 * chew, 4.0 * math.sin(w), 3.0 * math.sin(2.0 * w)]},
            "jaw":   {"rotation": [4.0 + 20.0 * chew, 0.0, 0.0]},
            "tongue": {"rotation": [-5.0 * chew, 0.0, 0.0]},
        }
        _set_lids(p, 0.30 * chew + _blink_curve(t, (0.75,)))
        _set_whiskers(p, 5.0 * chew, 5.0 * chew)
        _set_ears(p, -4.0 + 3.0 * chew, 2.0)
        _set_tail(p, lambda i: 3.5 + 1.2 * math.sin(2.0 * w - 0.3 * i),
                  lambda i: (4.0 + 1.5 * i) * math.sin(2.0 * w - 0.45 * i))
        for pref in ("leg_bl", "leg_br"):
            wr = _rig()["legs"][pref]["w_rest"]
            _solve_leg(p, pref, 0.0, wr[2], 0.0)
        for pref in ("leg_fl", "leg_fr"):
            wr = _rig()["legs"][pref]["w_rest"]
            _solve_leg(p, pref, 0.0, wr[2] - 1.4, 0.0)
        return p

    return clip(L, at, 32, loop=True)


# --- 9-11. PLAY_UP / PLAY_REAR / PLAY_DOWN (Playing Social panel: smooth rear onto hind legs, batting paws!) ---------
def _play_pose(k, t=0.0, L=1.80):
    u = (t % L) / L if k > 0.5 else 0.0
    w = 2.0 * math.pi * u
    bob = 0.45 * math.sin(2.0 * w) * k if k > 0.95 else 0.0
    sway = 5.0 * math.sin(w) * k if k > 0.95 else 0.0
    bat_l = math.sin(3.0 * w) * k if k > 0.95 else 0.0
    bat_r = math.sin(3.0 * w + math.pi) * k if k > 0.95 else 0.0

    p = {
        "body":  {"position": [0.0, -1.15 * k + 0.30 * bob, 1.5 * k],
                  "rotation": [-10.0 * k + 2.0 * bob, sway * 0.4, 0.0]},
        "hips":  {"rotation": [10.0 * k - 1.5 * bob, 0.0, 0.0]},
        "waist": {"rotation": [-18.0 * k - 2.0 * bob, sway * 0.3, sway * 0.2]},
        "ribs":  {"rotation": [-16.0 * k - 1.5 * bob, sway * 0.3, 0.0]},
        "chest": {"rotation": [-10.0 * k, sway * 0.4, 0.0]},
        "neck":  {"rotation": [16.0 * k + 2.0 * bob, sway * 0.3, 0.0]},
        "head":  {"rotation": [30.0 * k - 2.5 * bob, sway * 0.5, -sway * 0.4]},
        "jaw":   {"rotation": [(20.0 + 7.0 * (0.5 + 0.5 * math.sin(2.0 * w))) * k, 0.0, 0.0]},
        "tongue": {"rotation": [-5.0 * k, 0.0, 0.0]},
    }
    _set_lids(p, _blink_curve(t, (0.95,)) if k > 0.95 else 0.0)
    _set_whiskers(p, 5.0 * k, 5.0 * k)
    _set_ears(p, -6.0 * k, 4.0 * k)
    # Tail lifted smoothly onto the ground plane as a sturdy tripod (NEVER clips below y = 0!)
    _set_tail(p, lambda i: ((12.0 if i == 0 else 7.0 if i == 1 else 2.5) + 1.0 * math.sin(w - 0.3 * i)) * k,
              lambda i: (3.0 + i) * math.sin(w - 0.5 * i) * k)

    for pref in ("leg_bl", "leg_br"):
        wr = _rig()["legs"][pref]["w_rest"]
        _solve_leg(p, pref, 0.0, wr[2] + 0.5 * k, 0.0)
    for pref, s_side, bat in (("leg_fl", 1, bat_l), ("leg_fr", -1, bat_r)):
        p[f"{pref}_1"] = {"rotation": [(-16.0 + 16.0 * bat) * k, 0.0, s_side * (-10.0 - 6.0 * bat) * k]}
        p[f"{pref}_2"] = {"rotation": [(-22.0 + 12.0 * bat) * k, 0.0, 0.0]}
        p[f"{pref}_paw"] = {"rotation": [(14.0 - 10.0 * bat) * k, 0.0, 0.0]}
    return p


def _clip_play_up():
    L = 0.60
    return clip(L, lambda t: _play_pose(ease(t / L), t, L), 16, loop=False)


def _clip_play_rear():
    L = 1.80
    return clip(L, lambda t: _play_pose(1.0, t, L), 36, loop=True)


def _clip_play_down():
    L = 0.60
    return clip(L, lambda t: _play_pose(1.0 - ease(t / L), t, L), 16, loop=False)


# --- 12-14. REST_DOWN / REST / REST_UP (Resting panel: sprawled flat on rocks, eyes shut, chin on paws!) ------------
def _rest_pose(k, t=0.0, L=3.60):
    """Shared cozy rest pose blended smoothly by k in [0..1] (0 = standing idle, 1 = flat sleeping otter!)."""
    breath = math.sin(2.0 * math.pi * t / L) if k > 0.5 else 0.0
    dy = -2.85 * k + 0.14 * breath * k
    p = {
        "body":  {"position": [0.0, dy, 0.0], "rotation": [0.0, 0.0, 0.0]},
        "hips":  {"rotation": [0.5 * breath * k, 0.0, 0.0]},
        "waist": {"rotation": [(-3.0 - 0.8 * breath) * k, 0.0, 0.0]},
        "ribs":  {"rotation": [(6.2 + 0.8 * breath) * k, 0.0, 0.0]},
        "chest": {"rotation": [-3.2 * k, 0.0, 0.0]},
        "neck":  {"rotation": [26.0 * k, 0.0, 0.0]},
        "head":  {"rotation": [-24.0 * k + 0.8 * breath * k, 4.0 * k, 3.0 * k]},
        "jaw":   {"rotation": [0.5 * k, 0.0, 0.0]},
    }
    _set_lids(p, k)
    _set_whiskers(p, 2.0 * k, 2.0 * k)
    _set_ears(p, -8.0 * k, -6.0 * k)
    # Tail flattens horizontally ON TOP of the rock & curls gently sideways (zero ground clipping!)
    _set_tail(p, lambda i: (19.0 if i == 0 else 9.0 if i == 1 else 3.0 if i == 2 else 0.0) * k,
              lambda i: (6.0 + 3.0 * i) * k)
    # Front legs extend forward flat on the rock under the chin; hind legs fold cleanly OUTSIDE the tucked flanks!
    for pref, s_side in (("leg_fl", 1), ("leg_fr", -1)):
        p[f"{pref}_1"] = {"rotation": [-56.0 * k, 0.0, s_side * (-10.0 * k)]}
        p[f"{pref}_2"] = {"rotation": [8.0 * k, 0.0, 0.0]}
        p[f"{pref}_paw"] = {"rotation": [48.0 * k, 0.0, 0.0]}
    for pref, s_side in (("leg_bl", 1), ("leg_br", -1)):
        p[f"{pref}_1"] = {"rotation": [54.0 * k, 0.0, s_side * (-16.0 * k)]}
        p[f"{pref}_2"] = {"rotation": [-42.0 * k, 0.0, 0.0]}
        p[f"{pref}_paw"] = {"rotation": [-12.0 * k, 0.0, 0.0]}
    return p


def _clip_rest_down():
    L = 0.85
    return clip(L, lambda t: _rest_pose(ease(t / L), t, L), 20, loop=False)


def _clip_rest():
    L = 3.60
    return clip(L, lambda t: _rest_pose(1.0, t, L), 36, loop=True)


def _clip_rest_up():
    L = 0.85
    return clip(L, lambda t: _rest_pose(1.0 - ease(t / L), t, L), 20, loop=False)


# --- 15. SHAKE_DRY (1.10 s, once — vigorous head-to-tail water shake wave with grounded legs!) ------------------------
def _clip_shake_dry():
    L = 1.10

    def at(t):
        env = pulse(t, 0.08, 0.14, 0.56, 0.24)
        freq = 2.0 * math.pi * 4.5
        sh = lambda lag, amp: amp * env * math.sin(freq * (t - lag))

        # Keep hips/body roll subtle so planted paws stay solidly grounded while chest/neck/head/tail whip vigorously!
        p = {
            "body":  {"position": [0.0, -0.35 * env, 0.0], "rotation": [0.0, 0.0, sh(0.10, 4.5)]},
            "hips":  {"rotation": [0.0, sh(0.14, 6.0), sh(0.14, 6.0)]},
            "waist": {"rotation": [0.0, sh(0.10, 7.0), sh(0.10, 9.0)]},
            "ribs":  {"rotation": [0.0, sh(0.07, 8.0), sh(0.07, 12.0)]},
            "chest": {"rotation": [0.0, sh(0.04, 9.0), sh(0.04, 13.0)]},
            "neck":  {"rotation": [10.0 * env, sh(0.02, 12.0), sh(0.02, 18.0)]},
            "head":  {"rotation": [-8.0 * env, sh(0.00, 14.0), sh(0.00, 26.0)]},
            "jaw":   {"rotation": [3.0 * env, 0.0, 0.0]},
        }
        _set_lids(p, env)
        _set_whiskers(p, sh(0.0, 12.0), sh(0.0, 12.0))
        _set_ears(p, -10.0 * env, sh(0.0, 18.0))
        _set_tail(p, lambda i: 6.0 * env, lambda i: sh(0.14 + 0.03 * i, 12.0 + 4.0 * i),
                  lambda i: sh(0.14 + 0.03 * i, 14.0))
        for pref in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
            wr = _rig()["legs"][pref]["w_rest"]
            is_f = pref in ("leg_fl", "leg_fr")
            s_out = 1.0 if pref in ("leg_fl", "leg_bl") else -1.0
            _solve_leg(p, pref, 0.0, wr[2], 0.0, roll_z=s_out * (-10.5 if is_f else 0.0))
        return p

    return clip(L, at, 36, loop=False)


# --- 16. DEATH (1.10 s, hold — clean side collapse onto left flank, zero floor clipping!) ----------------------------
def _clip_death():
    L = 1.10

    def at(t):
        k = ease(min(1.0, t / 0.85))
        # Roll onto RIGHT flank (-82 deg about body y=7.55 -> lowest flank/ear/whisker stays cleanly above y=0 at dy=-1.35)
        # Tuck bottom (right) limbs smoothly forward & up along belly so ZERO paw/shin clips below y=0!
        p = {
            "body":  {"position": [0.0, -1.20 * k, 0.0], "rotation": [0.0, 0.0, -82.0 * k]},
            "hips":  {"rotation": [4.0 * k, 0.0, 0.0]},
            "waist": {"rotation": [-3.0 * k, 0.0, 0.0]},
            "ribs":  {"rotation": [4.0 * k, 0.0, 0.0]},
            "chest": {"rotation": [0.0, 0.0, 0.0]},
            "neck":  {"rotation": [16.0 * k, 0.0, 10.0 * k]},
            "head":  {"rotation": [-12.0 * k, 0.0, 12.0 * k]},
            "jaw":   {"rotation": [12.0 * k, 0.0, 0.0]},
        }
        _set_lids(p, k)
        _set_whiskers(p, 0.0, 0.0)
        _set_ears(p, -12.0 * k, 0.0)
        _set_tail(p, lambda i: 8.0 * k, lambda i: -4.0 * k)
        # Right (underside) legs fold cleanly UP toward belly (negative roll_z swings -X leg inward/up -> 0 floor clip!)
        p["leg_fr_1"] = {"rotation": [-52.0 * k, 0.0, -56.0 * k]}
        p["leg_fr_2"] = {"rotation": [12.0 * k, 0.0, 0.0]}
        p["leg_fr_paw"] = {"rotation": [30.0 * k, 0.0, 0.0]}
        p["leg_br_1"] = {"rotation": [54.0 * k, 0.0, -56.0 * k]}
        p["leg_br_2"] = {"rotation": [-36.0 * k, 0.0, 0.0]}
        p["leg_br_paw"] = {"rotation": [16.0 * k, 0.0, 0.0]}
        p["leg_fl_1"] = {"rotation": [16.0 * k, 0.0, -12.0 * k]}
        p["leg_fl_2"] = {"rotation": [-22.0 * k, 0.0, 0.0]}
        p["leg_fl_paw"] = {"rotation": [10.0 * k, 0.0, 0.0]}
        p["leg_bl_1"] = {"rotation": [22.0 * k, 0.0, -12.0 * k]}
        p["leg_bl_2"] = {"rotation": [-26.0 * k, 0.0, 0.0]}
        p["leg_bl_paw"] = {"rotation": [10.0 * k, 0.0, 0.0]}
        return p

    return clip(L, at, 24, loop="hold")


# --- SKITTER WALKS (docs/plans/otter-play-and-fish.md, section 6b; 2026-11-22) ---------------------------------------
# Real otters on land scurry in short bursts: a dash of a block or three, a sudden stop with the head up, a sniff, a
# little hop round to a new heading, another dash. Four clips for that (numbers my picks); the game picks when.
SCURRY_LEN = 0.44         # quick choppy trot: diagonal pairs, short fast strides, twice the walk's cadence
SCURRY_STEP = 3.2         # half-stride (px): full stance 6.4 px over 0.255 s -> 25.1 px/s (1.57 b/s), walk 1.12, bound 2
SCURRY_DUTY = 0.58
SCURRY_SPEED = round((2.0 * SCURRY_STEP) / (SCURRY_LEN * SCURRY_DUTY), 3)
SNIFF_LEN = 1.20          # slow nose-down zigzag
SNIFF_STEP = 4.1          # full stance 8.2 px over 0.864 s -> 9.5 px/s (0.59 b/s)
SNIFF_DUTY = 0.72
SNIFF_SPEED = round((2.0 * SNIFF_STEP) / (SNIFF_LEN * SNIFF_DUTY), 3)
SKID_LEN = 0.40           # the little braced stop at the end of a burst (8 ticks)
HOP_LEN = 0.50            # a quick hop round to a new heading (10 ticks); the game turns its body while its feet are up
HOP_AIR = (0.15, 0.35)    # feet off the ground (s): ticks 3 to 7


def _leg_roll(pref, front=-8.5):
    s_out = 1.0 if pref in ("leg_fl", "leg_bl") else -1.0
    return s_out * (front if pref in ("leg_fl", "leg_fr") else -6.5)


def _scurry_pose(u, k=1.0):
    """The scurry's body (no legs) at phase u; k fades its bob and sway out (the skid stop starts from it)."""
    w = 2.0 * math.pi * u
    bob = -0.80 + k * 0.28 * math.cos(2.0 * w)
    roll = k * 1.0 * math.sin(w)
    p = {
        "body":  {"position": [0.0, bob, 0.0], "rotation": [1.5 + k * 1.2 * math.sin(2.0 * w), 0.0, roll]},
        "hips":  {"rotation": [-1.0 + k * 1.6 * math.sin(2.0 * w + 0.6), 0.0, -roll * 0.3]},
        "waist": {"rotation": [0.5 - k * 1.6 * math.cos(2.0 * w), 0.0, 0.0]},
        "ribs":  {"rotation": [0.5 + k * 1.6 * math.cos(2.0 * w), 0.0, 0.0]},
        "chest": {"rotation": [-1.0 - k * 1.0 * math.sin(2.0 * w), 0.0, -roll * 0.4]},
        "neck":  {"rotation": [11.0 + k * 1.5 * math.cos(2.0 * w), 0.0, -roll * 0.3]},
        "head":  {"rotation": [-9.0 - k * 1.5 * math.cos(2.0 * w), 0.0, -roll * 0.3]},
        "jaw":   {"rotation": [1.5, 0.0, 0.0]},
    }
    _set_lids(p, 0.0)
    _set_whiskers(p, 6.0 + 2.0 * math.sin(2.0 * w), 6.0 + 2.0 * math.sin(2.0 * w))
    _set_ears(p, -7.0, 0.0)
    # tail held straight out behind for balance, only a small flick at its tip
    _set_tail(p, lambda i: (6.0 if i == 0 else 1.2) + k * 0.6 * i * math.sin(2.0 * w - 0.6 * i),
              lambda i: k * (0.8 + 0.6 * i) * math.sin(w - 0.5 * i))
    return p


def _clip_scurry():
    L = SCURRY_LEN
    phases = {"leg_bl": 0.00, "leg_fr": 0.03, "leg_br": 0.50, "leg_fl": 0.53}   # a trot, the front foot a hair late
    centre = {"leg_bl": -1.0, "leg_br": -1.0, "leg_fl": 1.0, "leg_fr": 1.0}     # each foot's stride kept in its reach

    def at(t):
        u = (t % L) / L
        p = _scurry_pose(u)
        for pref, ph in phases.items():
            wr = _rig()["legs"][pref]["w_rest"]
            s_norm, l_norm = stride((u + ph) % 1.0, SCURRY_DUTY)
            _solve_leg(p, pref, l_norm * 1.4, wr[2] + centre[pref] - s_norm * SCURRY_STEP, paw_world_pitch=-12.0 * l_norm,
                       roll_z=_leg_roll(pref, -11.0))
        return p

    out = clip(L, at, 24, loop=True)
    out["timeline"] = {"0": f"stride {SCURRY_SPEED:.3f}"}
    return out


def _blend_poses(a, b, k):
    """Pose a eased toward pose b (k 0 = a, 1 = b), channel by channel."""
    out = {}
    for bone in set(a) | set(b):
        for ch in ("rotation", "position"):
            va, vb = a.get(bone, {}).get(ch), b.get(bone, {}).get(ch)
            if va is None and vb is None:
                continue
            va, vb = va or [0.0, 0.0, 0.0], vb or [0.0, 0.0, 0.0]
            out.setdefault(bone, {})[ch] = [va[i] + (vb[i] - va[i]) * k for i in range(3)]
    return out


def _clip_skid_stop():
    L = SKID_LEN

    def at(t):
        p = _blend_poses(_scurry_pose(0.0, 0.0), _idle_pose(0.0), ease(min(1.0, t / L)))
        k = 1.0 - ease(min(1.0, t / L))
        # the rock: weight pitched onto the braced front paws, then back, then settling
        rock = ramp(t, ((0.0, 0.0), (0.10, 1.0), (0.22, -0.55), (0.32, 0.15), (L, 0.0)))
        _add_rot(p, "body", [4.5 * rock, 0.0, 0.0])
        pos = p["body"]["position"]
        p["body"]["position"] = [pos[0], pos[1] - 0.35 * max(0.0, rock), pos[2] - 1.0 * rock]
        # the head comes up past level to look about
        up = pulse(t, 0.12, 0.14, 0.06, 0.08)
        _add_rot(p, "neck", [-7.0 * up, 0.0, 0.0])
        _add_rot(p, "head", [-3.0 * up + 3.0 * rock, 0.0, 0.0])
        _set_ears(p, -7.0 * k + 6.0 * up, 4.0 * up)
        _set_whiskers(p, 6.0 * k + 6.0 * up, 6.0 * k + 6.0 * up)
        tail = [p[f"tail_{i + 1}"]["rotation"] for i in range(5)]
        _set_tail(p, lambda i: tail[i][0] + (2.5 * rock if i > 0 else 0.0), lambda i: tail[i][1])
        # front paws braced ahead from the start, a small shuffle back under it at the end; the hind catch up a step
        for pref in ("leg_fl", "leg_fr"):
            wr = _rig()["legs"][pref]["w_rest"]
            back = ease(max(0.0, min(1.0, (t - 0.26) / 0.12)))
            _solve_leg(p, pref, math.sin(math.pi * back) * 0.9, wr[2] - 1.6 * (1.0 - back),
                       paw_world_pitch=8.0 * (1.0 - back) * max(0.0, rock), roll_z=_leg_roll(pref))
        for pref, t0 in (("leg_bl", 0.04), ("leg_br", 0.10)):
            wr = _rig()["legs"][pref]["w_rest"]
            f = ease(max(0.0, min(1.0, (t - t0) / 0.18)))
            _solve_leg(p, pref, math.sin(math.pi * f) * 1.0, wr[2] + 2.0 * (1.0 - f), paw_world_pitch=0.0,
                       roll_z=_leg_roll(pref))
        return p

    return clip(L, at, 16, loop=False)


def _clip_sniff_walk():
    L = SNIFF_LEN
    phases = {"leg_bl": 0.00, "leg_fl": 0.25, "leg_br": 0.50, "leg_fr": 0.75}

    def at(t):
        u = (t % L) / L
        w = 2.0 * math.pi * u
        sweep = math.sin(w)                       # nose swept left and right once a loop (the zigzag)
        sniff = _sniff_puffs(t, ((0.08, 4), (0.70, 3)))   # two bouts of quick sniffs a loop
        bob = -0.75 + 0.14 * math.cos(2.0 * w)
        p = {
            "body":  {"position": [0.0, bob, 0.0], "rotation": [2.5, 0.0, 0.3 * sweep]},
            "hips":  {"rotation": [-1.0, 0.0, 0.0]},
            "waist": {"rotation": [-0.5 - 0.8 * math.cos(2.0 * w), 0.0, 0.0]},
            "ribs":  {"rotation": [0.5 + 0.8 * math.cos(2.0 * w), 0.0, 0.0]},
            "chest": {"rotation": [1.0, 0.0, 0.0]},
            "neck":  {"rotation": [30.0 + 2.0 * math.cos(2.0 * w), 16.0 * sweep, 0.0]},
            "head":  {"rotation": [14.0 + 2.2 * sniff, 12.0 * sweep, 4.0 * sweep]},
            "jaw":   {"rotation": [0.6, 0.0, 0.0]},
        }
        _set_lids(p, 0.12 + _blink_curve(t, (0.55,)))
        q = 4.0 * sniff
        _set_whiskers(p, 13.0 + q, 13.0 + q)
        _set_nose(p, sniff, 0.5 * math.sin(w * 3.0))
        _set_ears(p, 8.0, 5.0 * sweep)
        _set_tail(p, lambda i: 2.0 + 0.6 * math.sin(w - 0.4 * i),
                  lambda i: -(1.5 + 0.9 * i) * math.sin(w - 0.5 * i))
        for pref, ph in phases.items():
            wr = _rig()["legs"][pref]["w_rest"]
            s_norm, l_norm = stride((u + ph) % 1.0, SNIFF_DUTY)
            _solve_leg(p, pref, l_norm * 1.4, wr[2] - s_norm * SNIFF_STEP, paw_world_pitch=-10.0 * l_norm,
                       roll_z=_leg_roll(pref, -11.0))
        return p

    out = clip(L, at, 96, loop=True)
    out["timeline"] = {"0": f"stride {SNIFF_SPEED:.3f}"}
    return out


def _clip_hop_turn():
    L = HOP_LEN
    a0, a1 = HOP_AIR

    def at(t):
        p = _idle_pose(0.0)
        crouch = pulse(t, 0.0, 0.12, 0.0, 0.08)               # gathers itself
        land = pulse(t, a1 - 0.02, 0.05, 0.0, L - a1 - 0.03)  # soaks up the landing
        air = math.sin(math.pi * max(0.0, min(1.0, (t - a0) / (a1 - a0))))
        dy = -0.7 * crouch + 2.6 * air - 0.6 * land     # the front legs stand nearly straight: a deep crouch folds them fast
        pos = p["body"]["position"]
        p["body"]["position"] = [pos[0], pos[1] + dy, pos[2]]
        _add_rot(p, "body", [-2.5 * air + 1.5 * crouch, 0.0, 0.0])
        _add_rot(p, "waist", [-3.0 * air + 2.0 * crouch, 0.0, 0.0])   # the back rounds up in the air
        _add_rot(p, "chest", [2.0 * air, 0.0, 0.0])
        _add_rot(p, "neck", [-4.0 * air + 3.0 * crouch, 0.0, 0.0])
        _add_rot(p, "head", [3.0 * air + 2.0 * land, 0.0, 0.0])
        _set_ears(p, 4.0 * air - 3.0 * land, 3.0 * air)
        _set_whiskers(p, 8.0 * air, 8.0 * air)
        _set_tail(p, lambda i: (7.0 * air if i == 0 else 2.5 * air) - 1.0 * crouch, lambda i: 0.0)
        # feet: planted while it gathers, tucked up a little under it in the air (front a hair later), planted to land
        for pref in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
            wr = _rig()["legs"][pref]["w_rest"]
            front = pref in ("leg_fl", "leg_fr")
            late = 0.02 if front else 0.0
            up = math.sin(math.pi * max(0.0, min(1.0, (t - a0 - late) / (a1 - a0 - late))))
            _solve_leg(p, pref, 2.9 * up, wr[2] + (-0.4 if front else 0.4) * up, paw_world_pitch=-10.0 * up,
                       roll_z=_leg_roll(pref) * 0.6)
        return p

    out = clip(L, at, 18, loop=False)
    out["timeline"] = {f"{a0:.2f}": "air", f"{a1:.2f}": "land"}
    return out


# --- 15-18. BLOCK MOVES (step_up_1000, step_down_1000, enter_water_1000, exit_water_1000) ----------------------------
# Clean athletic 2.6-block otter moves with exact `path root` and `step`/`water` timeline notes for BlockMoves.java!
def _otter_move_clip(length, steps, dy_keys, dz_keys, pitch_keys, flex_keys, fl_fn, fr_fn, bl_fn, br_fn,
                     note, wet_t=-1.0, neck_fn=None, head_fn=None, tail_fn=None, ear_fn=None, whisker_fn=None,
                     shake_end=False, wiggle_fn=None, tail_yaw_fn=None):
    times = [length * k / steps for k in range(steps + 1)]
    path = []

    def at(t, record=True):
        dy = spline(t, dy_keys)
        dz = spline(t, dz_keys)
        bp = spline(t, pitch_keys)     # body world pitch (+ = nose UP!)
        flx = spline(t, flex_keys)     # spine flexion (+ = arched up, - = draped over edge!)
        nk_add = neck_fn(t) if neck_fn else 0.0
        hd_pitch = head_fn(t, bp, nk_add) if head_fn else (-nk_add * 0.6 + bp * 0.55)
        sh = 0.0
        if shake_end and t > length - 0.45:
            u_s = (t - (length - 0.45)) / 0.45
            sh = 16.0 * math.sin(math.pi * u_s) * math.sin(2.0 * math.pi * 5.0 * u_s)

        # the rump's wiggle as it scrambles over (2026-11-22, the user's "cute ... scrambling up quickly"): the hips
        # roll one way and the chest a little the other
        wg = wiggle_fn(t) if wiggle_fn else 0.0
        # In BlockMoves clips, root stays at 0 (the game moves the entity along `path root`), so pose is relative!
        p = {
            "body":  {"position": [0.0, 0.0, 0.0], "rotation": [-bp, 0.0, sh * 0.35]},
            "hips":  {"rotation": [flx * 0.5, wg * 0.35, wg]},
            "waist": {"rotation": [-flx * 0.8, 0.0, 0.0]},
            "ribs":  {"rotation": [flx * 1.1, 0.0, 0.0]},
            "chest": {"rotation": [-flx * 0.8, 0.0, sh * 0.5 - wg * 0.3]},
            "neck":  {"rotation": [nk_add + bp * 0.35, 0.0, sh * 0.6]},
            "head":  {"rotation": [hd_pitch, 0.0, sh]},
            "jaw":   {"rotation": [1.5, 0.0, 0.0]},
        }
        _set_lids(p, 0.0)
        wh_v = whisker_fn(t) if whisker_fn else 2.0
        _set_whiskers(p, wh_v, wh_v)
        ep, ey = ear_fn(t) if ear_fn else (-4.0, 0.0)
        _set_ears(p, ep, ey)
        if tail_fn:
            _set_tail(p, lambda i: tail_fn(t, i), (lambda i: tail_yaw_fn(t, i)) if tail_yaw_fn else None)
        else:
            _set_tail(p, lambda i: 4.5 + bp * 0.25)

        for pref, fn in (("leg_fl", fl_fn), ("leg_fr", fr_fn), ("leg_bl", bl_fn), ("leg_br", br_fn)):
            spec = fn(t, dy, dz)
            is_f = pref in ("leg_fl", "leg_fr")
            s_out = 1.0 if pref in ("leg_fl", "leg_bl") else -1.0
            if spec[0] == "ik":
                # an optional fifth item: the way the joint bends (a reach up and forward bends the elbow down and
                # back; the plain backward pole is square to that reach and let the elbow flip over, 2026-11-22)
                _solve_leg(p, pref, spec[1] - dy, spec[2] - dz, paw_world_pitch=spec[3],
                           roll_z=s_out * (-8.5 if is_f else -6.5), pole=spec[4] if len(spec) > 4 else None)
            elif spec[0] == "blend":
                # Smoothly eases from live IK pose (k=0) into target swim/dive angles (k=1) — ZERO snap!
                _, k, y_s, z_t, a1, a2, ap, rz_end = spec
                rz_ik = s_out * (-8.5 if is_f else -6.5)
                _solve_leg(p, pref, y_s - dy, z_t - dz, paw_world_pitch=0.0, roll_z=rz_ik)
                r1 = p[f"{pref}_1"]["rotation"]
                r2 = p[f"{pref}_2"]["rotation"]
                rp = p[f"{pref}_paw"]["rotation"]
                p[f"{pref}_1"] = {"rotation": [r1[0] * (1.0 - k) + a1 * k, 0.0, rz_ik * (1.0 - k) + rz_end * k]}
                p[f"{pref}_2"] = {"rotation": [r2[0] * (1.0 - k) + a2 * k, 0.0, 0.0]}
                p[f"{pref}_paw"] = {"rotation": [rp[0] * (1.0 - k) + ap * k, 0.0, 0.0]}
            else:
                rz_end = spec[4] if len(spec) > 4 else s_out * (-9.0 if is_f else -10.5)
                p[f"{pref}_1"] = {"rotation": [spec[1], 0.0, rz_end]}
                p[f"{pref}_2"] = {"rotation": [spec[2], 0.0, 0.0]}
                p[f"{pref}_paw"] = {"rotation": [spec[3], 0.0, 0.0]}

        if record:
            path.append((t, dy, dz))
        return p

    made = clip(length, at, steps, loop="hold")
    # Unwrap any 360-deg angle jumps along each bone's rotation channel so GeckoLib never spins a limb!
    for bdata in made["bones"].values():
        rot_ch = bdata.get("rotation")
        if not rot_ch:
            continue
        keys_sorted = sorted(rot_ch.keys(), key=float)
        prev = list(rot_ch[keys_sorted[0]])
        for k in keys_sorted[1:]:
            cur = list(rot_ch[k])
            for ax in range(3):
                while cur[ax] - prev[ax] > 180.0:
                    cur[ax] -= 360.0
                while cur[ax] - prev[ax] < -180.0:
                    cur[ax] += 360.0
            rot_ch[k] = [round(v, 3) for v in cur]
            prev = cur
    path = path[-(steps + 1):]
    made["timeline"] = dict(note)
    if wet_t >= 0.0:
        made["timeline"]["0.1"] = f"wet {wet_t:.2f}"
    made["timeline"]["0.05"] = "path root " + " ".join(f"{t:.4f} {dy:.3f} {dz:.3f}" for t, dy, dz in path)
    return made


def _clip_step_up():
    """A quick, cute scramble up a 1-block (16 px) step at face z = -24.0 (2026-11-22, the user: "I want it to look cute
    like he's scrambling up a block but quickly"; was a 2 s climb): a hop to the face rearing up, the front paws hooking
    the top edge, the hind feet patting fast against the face as the hips come up with a little rump wiggle and the
    tail swinging for balance, then over and on. 0.85 s."""
    L = 0.85
    B = 16.0
    face = -STEP_FACE       # -24.0
    dz_end = -36.0
    dy_keys = ((0.0, 0.0), (0.08, 0.8), (0.18, 4.5), (0.30, 6.5), (0.42, 9.5), (0.56, 13.6), (0.68, B), (L, B))
    dz_keys = ((0.0, 0.0), (0.10, -2.5), (0.24, -8.5), (0.40, -15.5), (0.56, -24.0), (0.68, -31.0), (L, dz_end))
    pitch_keys = ((0.0, 0.0), (0.06, 12.0), (0.18, 34.0), (0.36, 33.0), (0.52, 17.0), (0.66, 2.0), (L, 0.0))
    flex_keys = ((0.0, 0.0), (0.10, 6.0), (0.30, -6.0), (0.50, 8.0), (0.66, -2.0), (L, 0.0))

    def _front(pref, t_reach, t_hook, t_step, t_land):
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            z0, z1 = wr[2], dz_end + wr[2]
            zh = face - 3.0                      # hooked on the top, just past the edge
            B = 16.7                             # the paw resting on the top, not into it
            if t < t_reach:
                return ("ik", 0.0, z0, 0.0)
            pole = [0.0, -1.0, 0.7]
            if t < t_hook:                      # up and over the edge in a quick reach
                u = (t - t_reach) / (t_hook - t_reach)
                return ("ik", B * ease(u) + 1.2 * math.sin(math.pi * u), z0 * (1 - ease(u)) + zh * ease(u),
                        -10.0 * math.sin(math.pi * u), pole)
            if t < t_step:
                return ("ik", B, zh, 0.0, pole)
            if t < t_land:                      # a step on along the top as the body comes over
                u = (t - t_step) / (t_land - t_step)
                return ("ik", B + 1.6 * math.sin(math.pi * u), zh * (1 - ease(u)) + z1 * ease(u), -6.0 * math.sin(math.pi * u), pole)
            return ("ik", B, z1, 0.0, pole)
        return fn

    def _hind(pref, phase):
        """A hop to the face, three quick pats up it (the scrabble), then onto the top."""
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            z0, z1 = wr[2], dz_end + wr[2]
            zg = -7.0                           # hopped forward on the ground, under the rearing body
            zf = face + 2.4                     # against the face (the paw's toes just touching it)
            t_hop, t_hop_end = 0.20 + phase, 0.32 + phase
            t_pats = 0.38 + phase
            pat = 0.075
            t_top = t_pats + 3 * pat
            t_end = t_top + 0.11
            if t < t_hop:
                return ("ik", 0.0, z0, 0.0)
            if t < t_hop_end:
                u = (t - t_hop) / (t_hop_end - t_hop)
                return ("ik", 2.2 * math.sin(math.pi * u), z0 * (1 - ease(u)) + zg * ease(u), 10.0 * math.sin(math.pi * u))
            if t < t_pats:
                u = (t - t_hop_end) / (t_pats - t_hop_end)
                return ("ik", 4.5 * ease(u), zg * (1 - ease(u)) + zf * ease(u), 14.0 * ease(u))
            if t < t_top:                       # pat, pat, pat: each a little higher up the face
                k = (t - t_pats) / pat
                i = int(k)
                u = k - i
                y0, y1 = 4.5 + 4.0 * i, 4.5 + 4.0 * (i + 1)
                return ("ik", y0 + (y1 - y0) * ease(u) + 1.5 * math.sin(math.pi * u), zf + 1.2 * math.sin(math.pi * u), 22.0)
            if t < t_end:
                u = (t - t_top) / (t_end - t_top)
                return ("ik", 16.5 * (1 - ease(u)) + B * ease(u) + 1.2 * math.sin(math.pi * u),
                        zf * (1 - ease(u)) + z1 * ease(u), 20.0 * (1 - ease(u)))
            return ("ik", B, z1, 0.0)
        return fn

    return _otter_move_clip(
        L, 17, dy_keys, dz_keys, pitch_keys, flex_keys,
        fl_fn=_front("leg_fl", 0.05, 0.26, 0.40, 0.62),
        fr_fn=_front("leg_fr", 0.09, 0.30, 0.45, 0.67),
        bl_fn=_hind("leg_bl", 0.0),
        br_fn=_hind("leg_br", 0.035),
        note={"0": f"step {face:g} {B:.3f}"},
        neck_fn=lambda t: -10.0 * pulse(t, 0.04, 0.12, 0.30, 0.20),          # looks up at the top first
        ear_fn=lambda t: (-4.0 - 8.0 * pulse(t, 0.10, 0.10, 0.40, 0.15), 0.0),
        wiggle_fn=lambda t: 9.0 * math.sin(2.0 * math.pi * 6.5 * t) * pulse(t, 0.36, 0.06, 0.22, 0.10),
        # the tail held up off the ground as the body rears (its base turned against the body's pitch), then swinging
        tail_fn=lambda t, i: (spline(t, pitch_keys) * 1.05 + 8.0 if i == 0 else 6.0 if i == 1 else 3.0)
        + (10.0 if i == 0 else 6.0 if i == 1 else 2.0) * pulse(t, 0.02, 0.12, 0.40, 0.24),
        tail_yaw_fn=lambda t, i: (10.0 if i < 2 else 16.0) * math.sin(2.0 * math.pi * 6.5 * t - 0.6 * i)
        * pulse(t, 0.34, 0.06, 0.24, 0.12),
    )


def _clip_step_down():
    """A neat front-first hop down a 1-block (16 px) ledge at edge z = -12.0 (2026-11-22; was a 2 s drop): the hind feet
    step up to the edge and the hips stay up on the top as the chest tips over, the front paws reach down and land as
    the body drops, then the hind feet hop down after and the body levels out, the tail held up clear of the edge. 0.6 s."""
    L = 0.60
    B = 16.0
    edge = -DOWN_EDGE       # -12.0
    dz_end = -40.0
    dy_keys = ((0.0, 0.0), (0.14, 0.0), (0.22, -2.0), (0.32, -10.0), (0.42, -15.0), (0.50, -16.0), (L, -B))
    dz_keys = ((0.0, 0.0), (0.10, -6.0), (0.20, -13.0), (0.30, -21.0), (0.40, -29.0), (0.52, -36.5), (L, dz_end))
    pitch_keys = ((0.0, 0.0), (0.10, -14.0), (0.20, -30.0), (0.30, -26.0), (0.40, -12.0), (0.50, -2.0), (L, 0.0))
    flex_keys = ((0.0, 0.0), (0.18, -5.0), (0.34, 5.0), (0.50, 0.0), (L, 0.0))

    def _front(pref, t_lift, t_land, t_step, t_end):
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            z0, z1 = wr[2], dz_end + wr[2]
            zl = edge - 14.0                    # down on the lower ground, past the edge
            if t < t_lift:
                return ("ik", 0.0, z0, 0.0)
            if t < t_land:
                u = (t - t_lift) / (t_land - t_lift)
                return ("ik", -B * ease(u) + 1.5 * math.sin(math.pi * u), z0 * (1 - ease(u)) + zl * ease(u), 10.0 * math.sin(math.pi * u))
            if t < t_step:
                return ("ik", -B, zl, 0.0)
            if t < t_end:
                u = (t - t_step) / (t_end - t_step)
                return ("ik", -B + 1.5 * math.sin(math.pi * u), zl * (1 - ease(u)) + z1 * ease(u), -6.0 * math.sin(math.pi * u))
            return ("ik", -B, z1, 0.0)
        return fn

    def _hind(pref, t1, t2, t3, t4):
        """A step up to the edge on the top, then the hop down."""
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            z0, z1 = wr[2], dz_end + wr[2]
            ze = edge + 2.5                     # the toes at the edge
            pole = [0.0, 0.5, 1.0]
            if t < t1:
                return ("ik", 0.0, z0, 0.0, pole)
            if t < t2:
                u = (t - t1) / (t2 - t1)
                return ("ik", 2.0 * math.sin(math.pi * u), z0 * (1 - ease(u)) + ze * ease(u), 8.0 * math.sin(math.pi * u), pole)
            if t < t3:
                return ("ik", 0.0, ze, 0.0, pole)
            if t < t4:
                u = (t - t3) / (t4 - t3)
                return ("ik", -B * ease(u) + 2.5 * math.sin(math.pi * u), ze * (1 - ease(u)) + z1 * ease(u), 8.0 * math.sin(math.pi * u), pole)
            return ("ik", -B, z1, 0.0, pole)
        return fn

    return _otter_move_clip(
        L, 12, dy_keys, dz_keys, pitch_keys, flex_keys,
        fl_fn=_front("leg_fl", 0.06, 0.34, 0.40, 0.54),
        fr_fn=_front("leg_fr", 0.09, 0.37, 0.43, 0.57),
        bl_fn=_hind("leg_bl", 0.02, 0.15, 0.20, 0.50),
        br_fn=_hind("leg_br", 0.05, 0.18, 0.23, 0.53),
        note={"0": f"step {edge:g} {-B:.3f}"},
        tail_fn=lambda t, i: (-spline(t, pitch_keys) * 0.9 + 18.0 if i == 0 else 14.0 if i == 1 else 9.0)
        * pulse(t, 0.04, 0.12, 0.40, 0.10),
    )


def _clip_enter_water():
    """Gradual, relaxed otter wade-and-glide from a level bank into surface swim (bank top 0, surface -2, bed -32)."""
    L = 2.30
    edge = -DOWN_EDGE
    end_y = -2.0 - WATER_SURFACE
    # Unhurried, calm glide off the bank: stays buoyant and gentle with zero snappy limb tucks!
    dy_keys = ((0.0, 0.0), (0.55, 0.0), (1.05, -1.6), (1.52, -7.2), (1.96, -12.2), (L, end_y))
    dz_keys = ((0.0, 0.0), (0.50, -7.5), (1.05, -19.5), (1.58, -31.5), (2.00, -38.5), (L, -40.5))
    pitch_keys = ((0.0, 0.0), (0.45, -5.0), (1.05, -13.5), (1.60, -6.5), (2.05, -1.5), (L, 0.0))
    flex_keys = ((0.0, 0.0), (0.70, -2.5), (1.55, 2.5), (L, 0.0))

    def _calm_leg(pref, t_start, dur, a1, a2, ap, rz_end):
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            if t < t_start:
                return ("ik", 0.0, wr[2], 0.0)
            k = ease(min(1.0, (t - t_start) / dur))
            if k < 1.0:
                return ("blend", k, 0.0, wr[2], a1, a2, ap, rz_end)
            return ("ang", a1, a2, ap, rz_end)
        return fn

    return _otter_move_clip(
        L, 46, dy_keys, dz_keys, pitch_keys, flex_keys,
        fl_fn=_calm_leg("leg_fl", 0.26, 0.84, 46.0, -32.0, 28.0, -9.0),
        fr_fn=_calm_leg("leg_fr", 0.48, 0.84, 46.0, -32.0, 28.0,  9.0),
        bl_fn=_calm_leg("leg_bl", 0.84, 0.88, 52.0, -24.0, 32.0, -11.0),
        br_fn=_calm_leg("leg_br", 1.04, 0.86, 52.0, -24.0, 32.0,  11.0),
        note={"0": f"water in {edge:g} 0 -2.0 -32.0"},
        wet_t=0.92,
        neck_fn=lambda t: 16.0 * ease(max(0.0, min(1.0, (t - 0.72) / 1.15))),
        head_fn=lambda t, bp, nk: -nk * 0.88 + bp * 0.45,
        ear_fn=lambda t: (-4.0 - 6.0 * ease(min(1.0, t / 1.8)), -6.0 * ease(min(1.0, t / 1.8))),
        whisker_fn=lambda t: 2.0 + 3.0 * ease(min(1.0, t / 1.8)),
        tail_fn=lambda t, i: ((26.0 if i == 0 else 16.0 if i == 1 else 8.0) * pulse(t, 0.65, 0.30, 0.65, 0.50)
                              + (7.0 if i == 0 else 2.2) * ease(min(1.0, t / 1.7))),
    )


def _clip_dive_in():
    """Playful leaping dive off a riverbank: crouch -> high graceful arc -> steep plunge deep underwater -> dive_swim!"""
    L = 2.00
    edge = -DOWN_EDGE
    # Deep dive ends at dy = -29.0 px (~1.7 blocks BELOW water surface y = -2.0!), leveling cleanly into `dive_swim`!
    dy_keys = ((0.0, 0.0), (0.28, -0.8), (0.58, 6.2), (0.90, 0.4), (1.26, -13.5), (1.64, -25.2), (L, -29.0))
    dz_keys = ((0.0, 0.0), (0.28, -2.5), (0.60, -13.5), (0.92, -25.5), (1.35, -40.0), (1.72, -49.0), (L, -52.0))
    # Launch nose-UP (+22 deg), crest at apex, plunge steeply nose-DOWN (-38 deg), then level into dive_swim (-6.5 deg)!
    pitch_keys = ((0.0, 0.0), (0.24, 10.0), (0.44, 22.0), (0.64, 0.0), (0.92, -38.0), (1.30, -24.0), (1.68, -9.0), (L, -6.5))
    flex_keys = ((0.0, 0.0), (0.26, 7.0), (0.56, -4.0), (0.94, 5.0), (1.42, -3.0), (L, 0.0))

    def _dive_front(pref, s_out):
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            if t < 0.24:
                return ("ik", 0.0, wr[2], 0.0)
            # Reach forward playfully during launch (0.24..0.52 s), then sweep sleekly back for the underwater torpedo plunge!
            if t < 0.46:
                k = ease((t - 0.24) / 0.22)
                return ("blend", k, 0.0, wr[2], -26.0, -14.0, 18.0, s_out * -8.5)
            k2 = ease(min(1.0, (t - 0.46) / 0.48))
            a1 = -26.0 * (1.0 - k2) + 62.0 * k2
            a2 = -14.0 * (1.0 - k2) + -24.0 * k2
            ap = 18.0 * (1.0 - k2) + 34.0 * k2
            return ("ang", a1, a2, ap, s_out * -9.0)
        return fn

    def _dive_hind(pref, s_out):
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            if t < 0.34:
                return ("ik", 0.0, wr[2], 0.0)
            k = ease(min(1.0, (t - 0.34) / 0.50))
            if k < 1.0:
                return ("blend", k, 0.0, wr[2], 58.0, -22.0, 36.0, s_out * -11.0)
            # Gentle first kick deep underwater right as dive transitions into dive_swim!
            kick = math.sin(2.0 * math.pi * max(0.0, t - 1.25) / 0.75) * ease(min(1.0, max(0.0, t - 1.25) / 0.35))
            return ("ang", 58.0 + 10.0 * kick, -22.0 - 8.0 * kick, 36.0 + 10.0 * kick, s_out * -11.0)
        return fn

    return _otter_move_clip(
        L, 40, dy_keys, dz_keys, pitch_keys, flex_keys,
        fl_fn=_dive_front("leg_fl",  1.0),
        fr_fn=_dive_front("leg_fr", -1.0),
        bl_fn=_dive_hind("leg_bl",   1.0),
        br_fn=_dive_hind("leg_br",  -1.0),
        note={"0": f"water in {edge:g} 0 -2.0 -52.0"},
        wet_t=0.90,
        neck_fn=lambda t: 26.0 * ease(max(0.0, min(1.0, (t - 0.50) / 0.85))),
        head_fn=lambda t, bp, nk: -nk * 0.92 + bp * 0.30 * (1.0 - ease(max(0.0, min(1.0, (t - 0.95) / 0.75)))),
        ear_fn=lambda t: (-4.0 - 16.0 * ease(max(0.0, min(1.0, (t - 0.45) / 0.55))),
                          -12.0 * ease(max(0.0, min(1.0, (t - 0.45) / 0.55)))),
        whisker_fn=lambda t: 2.0 + 8.0 * ease(max(0.0, min(1.0, (t - 0.45) / 0.55))),
        tail_fn=lambda t, i: ((36.0 if i == 0 else 22.0 if i == 1 else 10.0) * pulse(t, 0.00, 0.18, 0.65, 0.42)
                              + (7.2 if i == 0 else 2.2) * ease(max(0.0, min(1.0, (t - 0.80) / 0.80)))),
    )


AFLOAT_DY = -2.0 - WATER_SURFACE   # afloat, its feet under the water's block top (the game's float depth, -13.3 px)


def _out_clip(L, B, dy_keys, dz_keys, pitch_keys, flex_keys, front_times, hind_times, wet_t):
    """Out of the water onto a bank B px over the water's block top, quickly (2026-11-22, the user: "the coming out of
    water is really quick"): from afloat (feet 13.3 px under that top), the front paws reach onto the bank, the hind
    feet kick in the water then come up onto the top, and on it goes."""
    face = -STEP_FACE
    dz_end = -36.0

    def _front(pref, t_reach, t_hook, t_step, t_land):
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            z1 = dz_end + wr[2]
            zh = face - 3.0
            if t < t_reach:                     # still the swim's paddle, tucked
                return ("ang", 22.0, -26.0, 16.0)
            if t < t_hook:
                k = ease((t - t_reach) / (t_hook - t_reach))
                if k < 0.999:
                    return ("blend", 1.0 - k, B, zh, 22.0, -26.0, 16.0, 0.0)
                return ("ik", B, zh, 0.0)
            if t < t_step:
                return ("ik", B, zh, 0.0)
            if t < t_land:
                u = (t - t_step) / (t_land - t_step)
                return ("ik", B + 1.6 * math.sin(math.pi * u), zh * (1 - ease(u)) + z1 * ease(u), -6.0 * math.sin(math.pi * u))
            return ("ik", B, z1, 0.0)
        return fn

    def _hind(pref, t_up, t_land, side):
        def fn(t, dy, dz):
            wr = _rig()["legs"][pref]["w_rest"]
            z1 = dz_end + wr[2]
            if t < t_up:                        # kicking hard in the water
                kick = math.sin(2.0 * math.pi * t / 0.22 + side)
                return ("ang", 40.0 + 22.0 * kick, -22.0 - 10.0 * kick, 26.0 + 10.0 * kick)
            if t < t_land:
                u = (t - t_up) / (t_land - t_up)
                k = ease(u)
                if k < 0.999:
                    return ("blend", 1.0 - k, B + 1.5 * math.sin(math.pi * u), face - 3.5 + (z1 - face + 3.5) * k,
                            40.0, -22.0, 26.0, 0.0)
                return ("ik", B, z1, 0.0)
            return ("ik", B, z1, 0.0)
        return fn

    return _otter_move_clip(
        L, round(L * 20), dy_keys, dz_keys, pitch_keys, flex_keys,
        fl_fn=_front("leg_fl", *front_times[0]),
        fr_fn=_front("leg_fr", *front_times[1]),
        bl_fn=_hind("leg_bl", hind_times[0][0], hind_times[0][1], 0.0),
        br_fn=_hind("leg_br", hind_times[1][0], hind_times[1][1], math.pi),
        note={"0": f"water out {face:g} {B:.3f} -1.778 -16.0"},
        wet_t=wet_t,
        neck_fn=lambda t: -8.0 * pulse(t, 0.02, 0.10, 0.25, 0.20),
        wiggle_fn=lambda t: 7.0 * math.sin(2.0 * math.pi * 6.0 * t) * pulse(t, L * 0.45, 0.06, L * 0.18, 0.10),
        tail_fn=lambda t, i: (spline(t, pitch_keys) * 0.7 + 6.0 if i == 0 else 8.0 if i == 1 else 3.0)
        * pulse(t, 0.0, 0.10, L * 0.5, 0.20),
        tail_yaw_fn=lambda t, i: 8.0 * math.sin(2.0 * math.pi * 6.0 * t - 0.6 * i) * pulse(t, L * 0.45, 0.06, L * 0.18, 0.10),
    )


def _clip_exit_water():
    """Quick climb out onto a bank level with the water's block top (0.8 s; was 2.1 s onto a bank a block up)."""
    L = 0.80
    return _out_clip(
        L, 0.0,
        dy_keys=((0.0, AFLOAT_DY), (0.10, AFLOAT_DY + 1.0), (0.30, -7.5), (0.48, -2.6), (0.62, 0.0), (L, 0.0)),
        dz_keys=((0.0, 0.0), (0.12, -4.0), (0.30, -11.0), (0.48, -22.0), (0.64, -32.0), (L, -36.0)),
        pitch_keys=((0.0, 0.0), (0.10, 14.0), (0.28, 22.0), (0.46, 10.0), (0.62, 0.0), (L, 0.0)),
        flex_keys=((0.0, 0.0), (0.20, 5.0), (0.42, -5.0), (0.60, 2.0), (L, 0.0)),
        front_times=((0.04, 0.20, 0.44, 0.62), (0.08, 0.24, 0.48, 0.66)),
        hind_times=((0.44, 0.62), (0.48, 0.66)),
        wet_t=0.44,
    )


def _clip_exit_water_up():
    """Quick haul out onto a bank a block (16 px) over the water's block top (1.1 s): a lunge up out of the water, the
    front paws hooking the top, the hind feet kicking then coming up the face, a wiggle over."""
    L = 1.10
    B = 16.0
    return _out_clip(
        L, B,
        dy_keys=((0.0, AFLOAT_DY), (0.10, AFLOAT_DY + 2.0), (0.26, -1.5), (0.44, 8.0), (0.62, 14.5), (0.80, B), (L, B)),
        dz_keys=((0.0, 0.0), (0.14, -3.0), (0.30, -6.5), (0.50, -13.0), (0.68, -24.0), (0.86, -33.0), (L, -36.0)),
        pitch_keys=((0.0, 0.0), (0.12, 28.0), (0.28, 40.0), (0.48, 30.0), (0.66, 12.0), (0.84, 0.0), (L, 0.0)),
        flex_keys=((0.0, 0.0), (0.24, 6.0), (0.48, -6.0), (0.70, 6.0), (0.88, 0.0), (L, 0.0)),
        front_times=((0.08, 0.30, 0.44, 0.66), (0.12, 0.34, 0.48, 0.70)),
        hind_times=((0.62, 0.84), (0.66, 0.88)),
        wet_t=0.62,
    )


def move_specs():
    """The otter's moves for the shared move file (assets/antcolonies/block_moves/otter.json)."""
    return [
        {"name": "up",   "clip": lambda s: f"step_up_{round(s * 1000)}",     "ahead_px": STEP_FACE,
         "starts_afloat": False, "ends_afloat": False, "goes_down": False, "end_level": 1,  "wades": True},
        {"name": "down", "clip": lambda s: f"step_down_{round(s * 1000)}",   "ahead_px": DOWN_EDGE,
         "starts_afloat": False, "ends_afloat": False, "goes_down": True,  "end_level": -1, "wades": True},
        {"name": "in",   "clip": lambda s: f"enter_water_{round(s * 1000)}", "ahead_px": DOWN_EDGE,
         "starts_afloat": False, "ends_afloat": True,  "goes_down": True,  "end_level": 0},
        {"name": "dive", "clip": lambda s: f"dive_in_{round(s * 1000)}",     "ahead_px": DOWN_EDGE,
         "starts_afloat": False, "ends_afloat": True,  "goes_down": True,  "end_level": -1},
        {"name": "out",  "clip": lambda s: f"exit_water_{round(s * 1000)}",  "ahead_px": STEP_FACE,
         "starts_afloat": True,  "ends_afloat": False, "goes_down": False, "end_level": 0,  "wades": True},
        {"name": "out_up", "clip": lambda s: f"exit_water_up_{round(s * 1000)}", "ahead_px": STEP_FACE,
         "starts_afloat": True,  "ends_afloat": False, "goes_down": False, "end_level": 1,  "wades": True},
    ]


def clips():
    return {
        "idle":             _clip_idle(),
        "walk":             _clip_walk(),
        "bound":            _clip_bound(),
        "swim":             _clip_swim(),
        "dive_swim":        _clip_dive_swim(),
        "forage_feel":      _clip_forage_feel(),
        "bite":             _clip_bite(),
        "gulp":             _clip_gulp(),
        "eat":              _clip_eat(),
        "play_up":          _clip_play_up(),
        "play_rear":        _clip_play_rear(),
        "play_down":        _clip_play_down(),
        "rest_down":        _clip_rest_down(),
        "rest":             _clip_rest(),
        "rest_up":          _clip_rest_up(),
        "shake_dry":        _clip_shake_dry(),
        "death":            _clip_death(),
        "scurry":           _clip_scurry(),
        "skid_stop":        _clip_skid_stop(),
        "sniff_walk":       _clip_sniff_walk(),
        "hop_turn":         _clip_hop_turn(),
        "step_up_1000":     _clip_step_up(),
        "step_down_1000":   _clip_step_down(),
        "enter_water_1000": _clip_enter_water(),
        "dive_in_1000":     _clip_dive_in(),
        "exit_water_1000":  _clip_exit_water(),
        "exit_water_up_1000": _clip_exit_water_up(),
    }


# --- FACES: the otter's moods (docs/plans/otter-play-and-fish.md, section 6c; 2026-11-22) ---------------------------
# Real otter signals pushed a little bigger so they read at Minecraft distance; no cartoon brows (my pick). Two layers:
# the FACE and TAIL (offsets the game adds on top of whatever clip plays, as the head look is added today) and the
# BODY STANCE (crouch, lean, neck, back, a lifted paw: drawn with the legs re-solved so the feet stay planted, so in the
# game it goes on the standing-still clip only; moving, the walk or the bound carries the body). The user, 2026-11-22:
# "scared and wary need more body language ... include [the tail] to the emotions". Honest note: real otters hardly
# signal with the tail (it is a rudder and a prop to stand on); the tail moods are fun first, kept small.
# Numbers are my picks, in degrees and model px:
#   face:  ear_fwd ears tipped forward (+) / laid back (-)   ear_out ears turned out (+) / in (-)
#          ear_flat ears' tips folded out sideways            lids eyelids down, 0 open .. 1 shut
#          eyes eye size (1 as made)                          whiskers swung forward (+) / swept back (-)
#          jaw mouth open                                     tongue tip forward (+ px) / drawn back (-)
#          tilt head tilt (the curious one)                   nod head tipped up (+) / down (-)
#          blink seconds between blinks   flick ear flicks per 10 s   scan ears turning about   quiver whisker quiver
#   tail:  tail_lift the tail raised at its base (+) / clamped down (-)   tail_curl each piece bent further (- under)
#          tail_swish side to side (deg)   tail_speed swishes per 3.6 s   tail_still 0 idle sway .. 1 held stiff
#   body:  crouch body lowered (px)   lean weight back (+ px) / forward (-)   neck neck raised (+ deg) / drawn in (-)
#          hunch back arched up (deg)   look head turning side to side (deg)   shiver trembling (deg)
#          paw front left paw lifted (px)   bounce a little bob (px)
FACE_MOODS = ["calm", "curious", "playful", "content", "wary", "scared", "cross"]
_FACE_ZERO = {"ear_fwd": 0.0, "ear_out": 0.0, "ear_flat": 0.0, "lids": 0.0, "eyes": 1.0, "whiskers": 0.0, "jaw": 0.0,
              "tongue": 0.0, "tilt": 0.0, "nod": 0.0, "blink": 3.2, "flick": 1.0, "scan": 0.0, "quiver": 1.5,
              "tail_lift": 0.0, "tail_curl": 0.0, "tail_swish": 0.0, "tail_speed": 1, "tail_still": 0.0,
              "crouch": 0.0, "lean": 0.0, "neck": 0.0, "hunch": 0.0, "look": 0.0, "shiver": 0.0, "paw": 0.0,
              "bounce": 0.0}
FACES = {m: dict(_FACE_ZERO, **v) for m, v in {
    "calm":    {"note": "Relaxed: ears a little out, slow blinks, whiskers resting, mouth closed, tail lazy."},
    "curious": {"ear_fwd": 14.0, "ear_out": 8.0, "eyes": 1.08, "whiskers": 16.0, "tilt": 13.0, "nod": 4.0,
                "blink": 4.5, "flick": 4.0, "quiver": 5.0,
                "tail_lift": 5.0, "tail_swish": 4.0, "tail_speed": 4, "tail_still": 0.6,
                "lean": -0.7, "neck": 7.0,
                "note": "Ears pricked, whiskers bristled forward (the real otter tell), eyes wide, head tilted; leaning in "
                        "with the neck stretched, the tail lifted a little with its tip flicking."},
    "playful": {"ear_fwd": 9.0, "ear_out": 4.0, "eyes": 1.06, "whiskers": 9.0, "jaw": 17.0, "tongue": 0.9,
                "tilt": 6.0, "nod": 4.0, "blink": 1.6, "flick": 3.0, "quiver": 3.0,
                "tail_lift": 9.0, "tail_swish": 11.0, "tail_speed": 3, "tail_still": 1.0,
                "neck": 3.0, "bounce": 0.45,
                "note": "The loose open-mouthed play face, tongue tip out, quick blinks; bouncing on its toes, tail up "
                        "and swishing. Also the excited face."},
    "content": {"ear_fwd": -8.0, "ear_out": 2.0, "lids": 0.55, "whiskers": -7.0, "nod": -3.0, "blink": 2.8,
                "flick": 0.5, "quiver": 0.8,
                "tail_lift": -5.0, "tail_curl": 1.5, "tail_swish": 2.5, "tail_speed": 1, "tail_still": 1.0,
                "crouch": 0.4, "neck": -3.0,
                "note": "Eyes half shut in a squint, ears soft and back, settled low, tail lying slack. Eating, resting, "
                        "dried off."},
    "wary":    {"ear_fwd": 6.0, "ear_out": 6.0, "eyes": 1.1, "whiskers": 11.0, "jaw": -1.0, "tongue": -0.4,
                "nod": 2.0, "blink": 7.0, "flick": 0.0, "scan": 22.0, "quiver": 0.0,
                "tail_lift": -3.0, "tail_curl": 0.5, "tail_still": 1.0,
                "crouch": -0.3, "lean": 0.8, "neck": 24.0, "look": 20.0, "paw": 2.2,
                "note": "Frozen and tall: weight back, neck stretched up, one front paw lifted, head and ears turning "
                        "to find the danger, eyes wide and hardly blinking, tail held straight and stiff."},
    "scared":  {"ear_fwd": -32.0, "ear_out": -12.0, "ear_flat": 22.0, "eyes": 1.14, "whiskers": -16.0,
                "tongue": -0.4, "nod": 6.0, "blink": 6.0, "flick": 0.0, "quiver": 0.0,
                "tail_lift": -22.0, "tail_curl": -2.0, "tail_still": 1.0,
                "crouch": 2.4, "lean": 1.2, "neck": -16.0, "hunch": 7.0, "shiver": 1.3,
                "note": "Crouched low and pulled back, back hunched, head drawn in, ears flat, whiskers swept back, "
                        "eyes wide, trembling, tail clamped down to the ground."},
    "cross":   {"ear_fwd": -26.0, "ear_out": -12.0, "ear_flat": 10.0, "lids": 0.38, "whiskers": 13.0, "jaw": 24.0,
                "tongue": -0.6, "nod": -2.0, "blink": 8.0, "flick": 0.0, "quiver": 2.0,
                "tail_lift": 6.0, "tail_swish": 5.0, "tail_speed": 6, "tail_still": 1.0,
                "crouch": 0.9, "lean": -0.7, "neck": -9.0, "hunch": 5.0,
                "note": "Ears flat, eyes narrowed, snarling; head low and pushed forward, back arched, weight on the "
                        "front feet ready to lunge, tail stiff with its tip lashing."},
}.items()}
WHISKER_SIGN = 1.0      # which way "forward" turns the whisker bones (checked on the faces page)


def _flick(t, rate, seed):
    """A quick ear flick (0..1) about rate times in 10 s, at fixed made-up times so the clip loops."""
    if rate <= 0:
        return 0.0
    n = max(1, round(rate * IDLE_LEN / 10.0))
    times = [((k + 0.5) / n + 0.13 * math.sin(7.1 * k + seed)) * IDLE_LEN for k in range(n)]
    return _blink_curve(t % IDLE_LEN, times, dur=0.22)


TAIL_GROUND = [2.8, 2.3, 1.8, 1.3]   # a pressed-down tail's joints tail_2..tail_5 this high (px): thick near the base,
                                     # thin at the tip, so it lies on the ground, not in it


def _world_tail_pitch(p):
    """How far the last tail piece points down (deg, + down) in the world, from tail_4 to tail_5's joints."""
    a, b = _fk_bone(p, "tail_4")[0], _fk_bone(p, "tail_5")[0]
    return math.degrees(math.atan2(a[1] - b[1], abs(b[2] - a[2]) + 1e-6))


def _add_rot(p, bone, d):
    r = p.get(bone, {}).get("rotation", [0.0, 0.0, 0.0])
    p.setdefault(bone, {})["rotation"] = [r[0] + d[0], r[1] + d[1], r[2] + d[2]]


def face_pose(mood, t, face=True):
    """The idle body at time t with this mood on it: face, tail and stance (the faces page). face=False: the stance and
    tail only, the idle's own face kept (the game's stand_<mood> clips; the game adds the face live on every clip)."""
    f = FACES[mood]
    p = _idle_pose(t)
    w = 2.0 * math.pi * t / IDLE_LEN

    # --- the stance (pitch: a piece reaching to -z, head-wards, goes up with a negative x turn) ---
    bob = f["bounce"] * abs(math.sin(2.0 * w))
    shiver = f["shiver"] * math.sin(w * 40.0)
    pos = p.get("body", {}).get("position", [0.0, 0.0, 0.0])
    p.setdefault("body", {})["position"] = [pos[0], pos[1] - f["crouch"] + bob, pos[2] + f["lean"]]
    _add_rot(p, "body", [0.0, 0.0, shiver])
    _add_rot(p, "waist", [-f["hunch"], 0.0, 0.0])            # front of the waist up: the back humps
    _add_rot(p, "chest", [f["hunch"], 0.0, 0.0])             # shoulders back down
    _add_rot(p, "hips", [-0.5 * f["hunch"], 0.0, 0.0])       # rump tucked a little
    look = f["look"] * math.sin(w)
    _add_rot(p, "neck", [-f["neck"], 0.4 * look, 0.0])
    _add_rot(p, "head", [0.75 * f["neck"] - f["nod"], 0.6 * look, f["tilt"]])
    if mood == "playful":
        _add_rot(p, "head", [-2.0 * math.sin(w * 2.0), 0.0, 0.0])
    if mood == "curious":                                    # sniffing the air in little bouts, the nose working
        _set_nose(p, _sniff_puffs(t, ((0.4, 3), (1.9, 4), (3.0, 2))), 0.6 * math.sin(w * 2.0))
    for pref in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        wr = _rig()["legs"][pref]["w_rest"]
        lift = f["paw"] if pref == "leg_fl" else 0.0
        _solve_leg(p, pref, lift, wr[2] - 0.5 * lift, 18.0 * lift / max(f["paw"], 0.01) if lift else 0.0)

    # --- the tail: its idle sway faded out by tail_still, then lift, curl and swish ---
    idle_tail = [p.get(f"tail_{i + 1}", {}).get("rotation", [0.0, 0.0, 0.0]) for i in range(5)]
    sw = f["tail_swish"]
    _set_tail(p,
              lambda i: (1.0 - f["tail_still"]) * idle_tail[i][0] + (f["tail_lift"] if i == 0 else 0.0)
              + (f["tail_curl"] if i > 0 else 0.0),
              lambda i: (1.0 - f["tail_still"]) * idle_tail[i][1]
              + sw * (0.4 + 0.3 * i) * math.sin(w * f["tail_speed"] - 0.6 * i))

    # a tail pressed down lies along the ground instead of going into it (each piece turned up until the next joint
    # is TAIL_GROUND px over the ground)
    for i in range(1, 5):
        for _ in range(40):
            if _fk_bone(p, f"tail_{i + 1}")[0][1] >= TAIL_GROUND[i - 1]:
                break
            _add_rot(p, f"tail_{i}", [2.0, 0.0, 0.0])
    if _fk_bone(p, "tail_5")[0][1] < TAIL_GROUND[-1] + 0.4:
        _add_rot(p, "tail_5", [-_world_tail_pitch(p) * 0.8, 0.0, 0.0])

    if not face:
        return p
    # --- the face ---
    n = max(1, round(IDLE_LEN / f["blink"]))
    blink = _blink_curve(t, [((k + 0.3) / n) * IDLE_LEN for k in range(n)], dur=0.16)
    _set_lids(p, max(f["lids"], blink))
    fl, fr = _flick(t, f["flick"], 0.0), _flick(t, f["flick"], 2.3)
    scan = f["scan"] * math.sin(w * 2.0)
    p["ear_left"] = {"rotation": [f["ear_fwd"] + 14.0 * fl, f["ear_out"] + scan, f["ear_flat"]]}
    p["ear_right"] = {"rotation": [f["ear_fwd"] + 14.0 * fr, -(f["ear_out"] - scan), -f["ear_flat"]]}
    q = f["quiver"] * math.sin(w * 9.0)
    wk = WHISKER_SIGN * (f["whiskers"] + q)
    _set_whiskers(p, wk, wk)
    jaw = p.get("jaw", {}).get("rotation", [0.0, 0.0, 0.0])
    p["jaw"] = {"rotation": [max(0.0, jaw[0] + f["jaw"]), 0.0, 0.0]}
    p["tongue"] = {"position": [0.0, 0.0, -f["tongue"]]}
    for side in ("eye_left", "eye_right"):
        p[side] = {"scale": [f["eyes"]] * 3}
    return p


def face_clips():
    return {"face_" + m: clip(IDLE_LEN, lambda t, m=m: face_pose(m, t), 72, loop=True) for m in FACE_MOODS}


EAR_STYLES = {"v6": "Now (approved v6)", "A": "A: bigger", "B": "B: bigger, pale rim", "C": "C: round, set out"}


def stance_clips():
    """The game's standing-still clips, one per mood with a stance (calm stands in the idle): stance and tail."""
    return {"stand_" + m: clip(IDLE_LEN, lambda t, m=m: face_pose(m, t, face=False), 72, loop=True)
            for m in FACE_MOODS if m != "calm"}


FACE_KEYS = ["ear_fwd", "ear_out", "ear_flat", "lids", "eyes", "whiskers", "jaw", "tongue", "tilt", "nod", "blink",
             "flick", "scan", "quiver", "tail_lift", "tail_curl", "tail_swish", "tail_speed"]


def face_file():
    """assets/antcolonies/faces/otter.json: what the game's face layer (framework FaceLayer) adds on every clip."""
    return {"_made_by": "tools/otter_models.py game (FACES; the faces page: python tools/otter_models.py faces)",
            "cycle": IDLE_LEN, "lid_drop": -1.55, "ear_flick": 14.0, "whisker_roll": 0.4, "blink_time": 0.16,
            "flick_time": 0.22,
            "bones": {"ear_left": "ear_left", "ear_right": "ear_right", "lid_left": "lid_left", "lid_right": "lid_right",
                      "eye_left": "eye_left", "eye_right": "eye_right", "whisker_left": "whisker_left",
                      "whisker_right": "whisker_right", "jaw": "jaw", "tongue": "tongue", "head": "head",
                      "tail": ["tail_1", "tail_2", "tail_3", "tail_4", "tail_5"]},
            "moods": {m: {k: FACES[m][k] for k in FACE_KEYS} for m in FACE_MOODS},
            "stances": {m: "stand_" + m for m in FACE_MOODS if m != "calm"}}


def faces_page():
    """references/workshop/otter/faces.html: every mood's face side by side and one at a time, blending between them."""
    import base64
    from viewer_page import build
    global EAR_STYLE
    kept, looks = EAR_STYLE, {}
    for style in EAR_STYLES:
        EAR_STYLE = style
        bones, png_bytes, w, h = workshop_look()
        looks[style] = {"bones": bones, "w": w, "h": h,
                        "texture": "data:image/png;base64," + base64.b64encode(png_bytes).decode()}
    EAR_STYLE = kept
    data = {"looks": looks, "ears": [[k, EAR_STYLES[k]] for k in EAR_STYLES], "ear": kept,
            "clips": face_clips(),
            "moods": [{"key": m, "label": m.capitalize(), "note": FACES[m]["note"]} for m in FACE_MOODS]}
    return build("face_viewer.template.html", data, Path(__file__).resolve().parent.parent
                 / "references/workshop/otter/faces.html")


WORKSHOP_CLIPS = clips
WORKSHOP_CLIP_INFO = {
    "water": ["swim", "dive_swim"],
    "surface": WATER_SURFACE / 16.0,
    "notes": {
        "walk":        f"Low sleek 4-beat otter stroll ({WALK_SPEED:.2f} px/s = {WALK_SPEED / 16:.2f} b/s base).",
        "bound":       f"Signature vertical-flexing otter bound/lope ({BOUND_SPEED:.2f} px/s = {BOUND_SPEED / 16:.2f} b/s).",
        "swim":        "Surface swim: flat crown, eyes, ears & nose ride above the waterline while the tail undulates.",
        "dive_swim":   "Underwater torpedo dive: forelegs tucked flat back, dolphin-like vertical spine & tail wave.",
        "forage_feel": "African clawless otter signature: alternating nimble front paws feeling under stones for crabs.",
        "play_rear":   "Social play-wrestle in shallows: rearing upright on hind legs & tail tripod, paws batting, mouth open.",
        "scurry":      f"Skitter: quick choppy trot, low and long, tail straight out ({SCURRY_SPEED / 16:.2f} b/s).",
        "skid_stop":   "Skitter: the braced stop at the end of a burst, rocking forward and back, head coming up.",
        "sniff_walk":  f"Skitter: slow nose-down zigzag, whiskers forward, sniffing ({SNIFF_SPEED / 16:.2f} b/s).",
        "hop_turn":    "Skitter: a quick little hop; the game turns it to its new heading while its feet are up.",
        "rest":        "Cozy riverbank rock rest: belly flush on stone, eyelids shut, chin resting near tucked front paws.",
    },
}


def workshop_look():
    bones = build_bones()
    painter = plain_colour if WORKSHOP_LOOK == "plain" else fur_colour
    bones, rows, w, h = paint_faces(bones, TEXELS, painter)
    return bones, png(rows), w, h


def export_game():
    """Exports geo, texture, animations (with automatic model_parts.game_faces!) and BlockMoves into savanna/."""
    bones, png_bytes, w, h = workshop_look()
    all_clips = clips()
    stances = stance_clips()
    write_assets({
        "geo/entity/otter.geo.json": geo("otter", bones, w, h, (3.2, 1.6, [0, 0.6, 0])),
        "textures/entity/otter.png": png_bytes,
        "animations/entity/otter.animation.json": animations({**all_clips, **stances}),
        "faces/otter.json": face_file(),
    }, mod="savanna")
    write_block_moves("otter", block_moves(all_clips, MOVE_SIZES, lambda s: 16.0 / s, move_specs(), lead=4),
                      "tools/otter_models.py game")


if __name__ == "__main__":
    if "game" in sys.argv[1:]:
        export_game()
    elif "faces" in sys.argv[1:]:
        faces_page()
    else:
        bones = build_bones()
        all_clips = clips()
        n_boxes = sum(len(b.get("cubes", [])) for b in bones)
        print(f"otter ({WORKSHOP_LOOK}): {total_length():.1f} px ({total_length()/16:.2f} blocks), "
              f"{len(bones)} bones, {n_boxes} boxes (budget: 250), {len(all_clips)} clips")
