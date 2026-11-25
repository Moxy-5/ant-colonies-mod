"""African Lion Pride (Panthera leo) model maker — Locked Stalker King Male (3 Looks) + Lioness + Cub.

Approved Architecture (2026-11-23):
  - Male Lion (4.66 x 2.03 blocks): Locked Head F ("Savanna Stalker King") + sweeping arched teardrop mane +
    sculpted Monarch/Kalahari body & digitigrade limbs across 3 in-game coat/mane variants:
      * photo_king  ("Savanna Pride King", default): lion.geo.json + lion.png
      * monarch     ("Golden Savanna Monarch"):      lion.geo.json + lion_monarch.png (100% shared geo!)
      * kalahari    ("Kalahari Black-Maned Patriarch"): lion_kalahari.geo.json + lion_kalahari.png (heavier build + belly fringe)
  - Lioness (4.06 x 1.70 blocks): Bespoke sleek female hunter on the shared bone rig — graceful 4-slice curving neck,
    creamy brisket, athletic shoulder/flank tuck, refined Stalker-King feminine face, prominent black-backed ears,
    lithe limbs & tight tail tuft.
  - Lion Cub (2.15 x 1.18 blocks): Authentic playful cub proportions — big expressive head & luminous eyes, oversized
    rounded fuzzy ears, plump milk-fed belly, chunky "grow-into-them" paws, perky curled tail & soft cub rosette spots!

All pride members share the exact bone naming convention (and sealed 3D feline mouths, sliding pupils, animated eyelids,
articulated ears, digitigrade hind legs, and 5-bone tails) so every animation clip plays cleanly across the whole pride.
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (  # noqa: E402
    cube, rot_cube, mix, shade, paint_faces, png, geo, animations, write_assets,
    block_moves, write_block_moves,
    hash3, fur_grain, stamp_2d, padded_paw, feline_mouth,
    from_axes, unit, cross, vsub, vlen, rot, world_point,
)

TOTAL_LEN = 73.6        # Male Lion: 4.60 blocks base (4.66 w/ Stalker King muzzle)
LIONESS_LEN = 65.0      # Lioness: ~4.06 blocks
CUB_LEN = 34.4          # Lion Cub: ~2.15 blocks
TEXELS = 2.0

LOOKS = {
    "photo_king": {
        "title": "Savanna Pride King (Default)",
        "short": "Pride King",
        "kind": "male",
        "palette": "photo_king",
        "body_bulk": 1.05, "flank_tuck": 1.04, "mane_fullness": 1.08,
        "mane_back_reach": 1.14, "back_ext": 1.5, "drop_ext": 0.8, "keel_bot": 10.0,
        "belly_fringe": False, "elbow_tufts": True, "elbow_len": 1.8, "head_breadth": 1.00,
    },
    "monarch": {
        "title": "Golden Savanna Monarch",
        "short": "Golden Monarch",
        "kind": "male",
        "palette": "savanna_monarch",
        # Shares exact geometry with photo_king so lion_monarch.png is a zero-cost texture swap on lion.geo.json!
        "body_bulk": 1.05, "flank_tuck": 1.04, "mane_fullness": 1.08,
        "mane_back_reach": 1.14, "back_ext": 1.5, "drop_ext": 0.8, "keel_bot": 10.0,
        "belly_fringe": False, "elbow_tufts": True, "elbow_len": 1.8, "head_breadth": 1.00,
    },
    "kalahari": {
        "title": "Kalahari Black-Maned Patriarch",
        "short": "Kalahari Patriarch",
        "kind": "male",
        "palette": "kalahari_dark",
        "body_bulk": 1.11, "flank_tuck": 0.88, "mane_fullness": 1.14,
        "mane_back_reach": 1.24, "back_ext": 3.8, "drop_ext": 2.2, "keel_bot": 8.8,
        "belly_fringe": True, "elbow_tufts": True, "elbow_len": 2.6, "head_breadth": 1.04,
    },
    "lioness": {
        "title": "African Lioness (Serengeti Tawny Hunter)",
        "short": "Lioness",
        "kind": "lioness",
        "palette": "lioness_tawny",
    },
    "lioness_kalahari": {
        "title": "African Lioness (Kalahari Pale Sand)",
        "short": "Kalahari Lioness",
        "kind": "lioness",
        "palette": "lioness_kalahari",
    },
    "lioness_elder": {
        "title": "African Lioness (Prime Matriarch)",
        "short": "Elder Lioness",
        "kind": "lioness",
        "palette": "lioness_elder",
    },
    "cub": {
        "title": "African Lion Cub (Spotted Young, 2–5 mo)",
        "short": "Spotted Cub",
        "kind": "cub",
        "palette": "cub_spotted",
    },
    "cub_subadult": {
        "title": "African Lion Cub (Fading Juvenile, 6–12 mo)",
        "short": "Juvenile Cub",
        "kind": "cub",
        "palette": "cub_subadult",
    },
}


def oz(ox, total_len=TOTAL_LEN):
    return total_len * 0.5 - ox


def box(cubes, x0, x1, y0, y1, ox0, ox1, part, rotation=None, pivot=None, total_len=TOTAL_LEN, **extra):
    z0, z1 = oz(ox1, total_len), oz(ox0, total_len)
    lo = [min(x0, x1), min(y0, y1), min(z0, z1)]
    hi = [max(x0, x1), max(y0, y1), max(z0, z1)]
    if hi[0] - lo[0] < 0.04 or hi[1] - lo[1] < 0.04 or hi[2] - lo[2] < 0.04:
        return None
    c = rot_cube(lo, hi, part, rotation=rotation, pivot=pivot, **extra) if rotation else cube(lo, hi, part)
    if not rotation and extra:
        c.update(extra)
    cubes.append(c)
    return c


def sym(cubes, hw, y0, y1, ox0, ox1, part, rotation=None, pivot=None, total_len=TOTAL_LEN, **extra):
    return box(cubes, -hw, hw, y0, y1, ox0, ox1, part, rotation=rotation, pivot=pivot, total_len=total_len, **extra)


def tapered_slice(cubes, ox0, ox1, y0, y1, hw, belly_h=1.5, total_len=TOTAL_LEN):
    if y1 - y0 < 1.2:
        return
    top_pad = min(1.5, (y1 - y0) * 0.22)
    sym(cubes, hw * 0.74, y0, y0 + belly_h, ox0, ox1, "belly", total_len=total_len)
    sym(cubes, hw, y0 + belly_h * 0.70, y1 - top_pad, ox0, ox1, "coat", total_len=total_len)
    sym(cubes, hw * 0.84, y1 - top_pad * 1.2, y1 - top_pad * 0.24, ox0, ox1, "coat", total_len=total_len)
    sym(cubes, hw * 0.62, y1 - top_pad * 0.48, y1, ox0, ox1, "coat", total_len=total_len)


def muscular_limb(cubes, p0, p1, w0, d0, w1, d1, part, overlap=0.7):
    vec = vsub(p1, p0)
    if vlen(vec) < 0.1:
        return
    ly = unit(vsub(p0, p1))
    lz = unit(cross([1.0, 0.0, 0.0], ly))
    lx = unit(cross(ly, lz))
    R = from_axes(lx, ly, lz)
    for t0, t1, fw in ((0.0, 0.58, 0.25), (0.42, 1.0, 0.80)):
        a = [p0[k] + vec[k] * t0 for k in range(3)]
        b = [p0[k] + vec[k] * t1 for k in range(3)]
        mid = [(a[k] + b[k]) * 0.5 for k in range(3)]
        sl = vlen(vsub(b, a)) * 0.5 + overlap * 0.65
        w = w0 + (w1 - w0) * fw
        d = d0 + (d1 - d0) * fw
        cubes.append(rot_cube(
            [mid[0] - w * 0.5, mid[1] - sl, mid[2] - d * 0.5],
            [mid[0] + w * 0.5, mid[1] + sl, mid[2] + d * 0.5],
            part, rotation=rot(R), pivot=mid,
        ))


def aligned_seg(cubes, p0, p1, t0, t1, w, d, part, z_off=0.0, x_off=0.0, **extra):
    """Build a single clean muscle block strictly parallel to bone axis p0 -> p1 (zero colliding unrotated squares!)."""
    vec = vsub(p1, p0)
    if vlen(vec) < 0.1:
        return
    ly = unit(vsub(p0, p1))
    lz = unit(cross([1.0, 0.0, 0.0], ly))
    lx = unit(cross(ly, lz))
    R = from_axes(lx, ly, lz)
    a = [p0[k] + vec[k] * t0 for k in range(3)]
    b = [p0[k] + vec[k] * t1 for k in range(3)]
    mb = [(a[k] + b[k]) * 0.5 for k in range(3)]
    mid = [mb[k] + lx[k] * x_off + lz[k] * z_off for k in range(3)]
    hl = vlen(vsub(b, a)) * 0.5
    cubes.append(rot_cube(
        [mid[0] - w * 0.5, mid[1] - hl, mid[2] - d * 0.5],
        [mid[0] + w * 0.5, mid[1] + hl, mid[2] + d * 0.5],
        part, rotation=rot(R), pivot=mid, **extra,
    ))


# Locked V02 · Splaying Hunter feline crescent toe layout (middle toes jut forward ahead of outer toes!)
SPLAY_TOES = (
    (-0.74, 0.64, 0.42, 0.60, 0.96,  8.0),
    (-0.26, 0.78, 0.46, 0.68, 1.02,  2.5),
    ( 0.26, 0.78, 0.46, 0.68, 1.02, -2.5),
    ( 0.74, 0.64, 0.42, 0.60, 0.96, -8.0),
)


def splaying_hunter_paw(tag, parent_bone, wr_piv, cx, ground_y, cz, width, length, height, lean_cub=False):
    """Builds the locked V02 · Splaying Hunter articulated big-cat paw:
      - Broad plush rounded metacarpal/metatarsal cushion + 3-lobe clover palm pad on `leg_{tag}_paw`
      - 4 independent articulated toe bones `toe_{tag}_0..3` (furry rounded toe dome + teardrop toe bean)
      - 4 independent curved 2-segment laterally-compressed bone-ivory sickle claws `claw_{tag}_0..3`
        tucked 100% vertically inside each furry toe dome at bind pose (rx=0 -> 0.00 px poke-through!).
    """
    hw = width * 0.5
    sc_p = width / 5.30
    pad_h = max(0.28, 0.42 * sc_p)
    paw_cubes = []

    # 1. Plush wrist-to-metacarpal cushion & rounded flanks (lower edges cleared above pad_h so minY=0.00 strictly on pads!)
    paw_cubes.append(cube([cx - hw * 0.78, ground_y + pad_h * 0.95, cz - length * 0.12],
                          [cx + hw * 0.78, ground_y + height * 1.04, cz + length * 0.44], "paw_fur"))
    paw_cubes.append(rot_cube(
        [cx - hw * 0.90, ground_y + pad_h * 1.25, cz - length * 0.48],
        [cx + hw * 0.90, ground_y + height * 0.92, cz + length * 0.18],
        "paw_fur", rotation=[14.0, 0.0, 0.0], pivot=[cx, ground_y + height * 0.48, cz - length * 0.12],
    ))
    paw_cubes.append(rot_cube(
        [cx - hw * 0.88, ground_y + pad_h * 1.35, cz - length * 0.56],
        [cx + hw * 0.88, ground_y + height * 0.76, cz - length * 0.14],
        "paw_fur", rotation=[20.0, 0.0, 0.0], pivot=[cx, ground_y + height * 0.45, cz - length * 0.32],
    ))
    for s in (-1, 1):
        x0, x1 = sorted([cx + s * hw * 0.56, cx + s * hw * 0.96])
        paw_cubes.append(rot_cube(
            [x0, ground_y + pad_h * 1.35, cz - length * 0.40],
            [x1, ground_y + height * 0.76, cz + length * 0.24],
            "paw_fur", rotation=[6.0, s * -7.0, s * -8.0],
        ))

    # 2. Realistic 3-lobed clover metacarpal palm pad (flush on ground_y)
    z_sh = 0.04 * length
    paw_cubes.append(cube([cx - hw * 0.42, ground_y, cz - length * 0.28 + z_sh],
                          [cx + hw * 0.42, ground_y + pad_h + 0.05, cz + length * 0.12 + z_sh], "paw_pad_clover"))
    if not lean_cub:
        paw_cubes.append(cube([cx - hw * 0.26, ground_y, cz + z_sh],
                              [cx + hw * 0.26, ground_y + pad_h + 0.04, cz + length * 0.26 + z_sh], "paw_pad_clover"))
    for s in (-1, 1):
        x0, x1 = sorted([cx + s * hw * 0.14, cx + s * hw * 0.66])
        paw_cubes.append(rot_cube(
            [x0, ground_y, cz - length * 0.12 + z_sh],
            [x1, ground_y + pad_h + 0.04, cz + length * 0.22 + z_sh],
            "paw_pad_clover", rotation=[0.0, s * -11.0, 0.0],
        ))

    # 3. Four independent splaying toes + 2-segment curved bone-ivory sickle claws!
    extra_bones = []
    for i, (x_frac, z_reach, len_frac, h_scale, w_scale, fan_yaw) in enumerate(SPLAY_TOES):
        tx = cx + x_frac * hw
        t_len = length * len_frac
        t_front = cz - length * z_reach
        t_back = t_front + t_len
        t_hw = (width / 4.0) * 0.47 * w_scale
        t_h = height * h_scale
        knuckle_piv = [tx, ground_y + t_h * 0.52, t_back - 0.35 * sc_p]

        tcubes = []
        if lean_cub:
            tcubes.append(rot_cube(
                [tx - t_hw, ground_y + pad_h * 1.25, t_front - 0.04 * sc_p],
                [tx + t_hw, ground_y + t_h * 0.98, t_back],
                "toe_fur", rotation=[12.0, fan_yaw, 0.0], pivot=knuckle_piv,
            ))
        else:
            tcubes.append(rot_cube(
                [tx - t_hw, ground_y + pad_h * 1.12, t_front + 0.10 * sc_p],
                [tx + t_hw, ground_y + t_h * 0.90, t_back],
                "toe_fur", rotation=[8.0, fan_yaw, 0.0], pivot=knuckle_piv,
            ))
            tcubes.append(rot_cube(
                [tx - t_hw * 0.90, ground_y + pad_h * 1.48, t_front - 0.10 * sc_p],
                [tx + t_hw * 0.90, ground_y + t_h * 1.02, t_front + t_len * 0.64],
                "toe_fur", rotation=[22.0, fan_yaw, 0.0],
                pivot=[tx, ground_y + t_h * 0.58, t_front + t_len * 0.30],
            ))
        bean_w = t_hw * 0.78
        bean_h = pad_h * 1.12
        tcubes.append(rot_cube(
            [tx - bean_w, ground_y, t_front + 0.22 * sc_p],
            [tx + bean_w, ground_y + bean_h, t_back - 0.26 * sc_p],
            "paw_pad_bean", rotation=[0.0, fan_yaw, 0.0],
            pivot=[tx, ground_y + bean_h * 0.5, (t_front + t_back) * 0.5],
        ))

        cpiv = [tx, ground_y + pad_h + (0.14 if lean_cub else 0.18) * sc_p, t_front + (1.28 if lean_cub else 0.92) * sc_p]
        max_safe_len = max(0.38 * sc_p, (t_h - (pad_h + 0.18 * sc_p) - (0.48 if lean_cub else 0.34) * sc_p) * (0.70 if lean_cub else 0.84))
        c_len = min(1.08 * sc_p * (1.04 if i in (1, 2) else 0.90), max_safe_len)
        cw = 0.44 * sc_p
        bw, bd = cw * 0.44, cw * 0.82
        l1, l2 = c_len * 0.58, c_len * 0.62
        base_p, curve_d = 4.0, 36.0
        a1 = math.radians(base_p)
        my = cpiv[1] + l1 * math.cos(a1) * 0.84
        mz = cpiv[2] - l1 * math.sin(a1) * 0.84
        ccubes = [
            rot_cube(
                [tx - bw, cpiv[1] - 0.08 * sc_p, cpiv[2] - bd * 0.92],
                [tx + bw, cpiv[1] + l1, cpiv[2] + bd * 0.86],
                "claw_ivory", rotation=[base_p, fan_yaw, 0.0], pivot=cpiv, _claw_t=0.20,
            ),
            rot_cube(
                [tx - bw * 0.52, my - 0.05 * sc_p, mz - bd * 0.48],
                [tx + bw * 0.52, my + l2, mz + bd * 0.32],
                "claw_ivory", rotation=[base_p + curve_d, fan_yaw, 0.0], pivot=[tx, my, mz], _claw_t=0.92,
            ),
        ]
        tname = f"toe_{tag}_{i}"
        cname = f"claw_{tag}_{i}"
        extra_bones.append({"name": tname, "parent": f"leg_{tag}_paw", "pivot": knuckle_piv, "cubes": tcubes})
        extra_bones.append({"name": cname, "parent": tname, "pivot": cpiv, "cubes": ccubes})

    return [
        {"name": f"leg_{tag}_paw", "parent": parent_bone, "pivot": wr_piv, "cubes": paw_cubes},
        *extra_bones,
    ]


def mane_lock(cubes, cx, yt, yb, oxf, oxb, w, part, layer, lock_id, ring_id=0,
              pitch_deg=0.0, yaw_deg=0.0, roll_deg=0.0, piv_y=None):
    if yt - yb < 0.5 or oxf - oxb < 0.35:
        return
    meta = {"_layer": round(layer, 3), "_lock": lock_id, "_ring": ring_id}
    r = [pitch_deg, yaw_deg, roll_deg] if any(abs(v) > 0.3 for v in (pitch_deg, yaw_deg, roll_deg)) else None
    piv = [cx, yt if piv_y is None else piv_y, oz((oxf + oxb) * 0.5)] if r else None
    box(cubes, cx - w * 0.5, cx + w * 0.5, yb, yt, oxb, oxf, part, rotation=r, pivot=piv, **meta)


# ====================================================================================================
# 1. LOCKED MALE LION (STALKER KING HEAD F + SWEEPING TEARDROP MANE + 3 LOOKS)
# ====================================================================================================
def build_stalker_king_head(hb):
    head_cubes, jaw_cubes, tongue_cubes, tongue_tip_cubes = [], [], [], []
    ear_l, ear_r, brow_l, brow_r, lid_l, lid_r, ulid_l, ulid_r, pup_l, pup_r, nose_cubes = [], [], [], [], [], [], [], [], [], [], []
    cheek_clear = 3.82 * hb
    ear_piv_x, ear_piv_y, ear_piv_ox = 4.15 * hb, 27.9, 64.6
    eye_piv_x, eye_piv_y, eye_piv_ox = 2.40 * hb, 25.84, 70.20
    jaw_piv = [0.0, 21.58, oz(66.3)]

    sym(head_cubes, 3.82 * hb, 21.8, 27.4, 62.7, 69.0, "head")
    sym(head_cubes, 3.42 * hb, 26.85, 28.12, 63.3, 69.35, "forehead", rotation=[-4.0, 0.0, 0.0])
    sym(head_cubes, 2.45 * hb, 27.8, 28.76, 63.1, 68.35, "forehead", rotation=[-8.0, 0.0, 0.0])
    for s in (-1, 1):
        box(head_cubes, s * 2.12 * hb, s * 4.32 * hb, 23.9, 26.6, 64.2, 68.8, "head_cheek", rotation=[0, s * -14, s * -7])
        box(head_cubes, s * 1.62 * hb, s * 3.40 * hb, 21.4, 24.1, 64.9, 69.3, "head_cheek", rotation=[0, s * -16, 0])
        box(brow_l if s > 0 else brow_r, s * 1.02 * hb, s * 3.96 * hb, 26.58, 27.60, 67.3, 70.35, "brow", rotation=[5, s * -14, s * -11])
        box(head_cubes, s * 1.32 * hb, s * 3.52 * hb, 25.06, 26.60, 68.25, 70.05, "eye_socket", rotation=[2, s * -14, s * -9])
        box(head_cubes, s * 1.44 * hb, s * 3.38 * hb, 25.20, 26.48, 69.04, 70.24, "eye_piercing", rotation=[2, s * -14, s * -9])
        exm = s * 2.40 * hb
        box(pup_l if s > 0 else pup_r, exm - 0.30, exm + 0.30, 25.43, 26.25, 70.10, 70.30, "pupil", rotation=[2, s * -14, s * -9])
        # Flush dual eyelids (zero floating wiper plate!): upper lid tucks neatly behind brow (70.31 < brow 70.35) with dark lash rim,
        # lower lid (under_eye) rises up 35% to meet it seamlessly in the middle of the eye!
        lc = lid_l if s > 0 else lid_r
        box(lc, s * 1.40 * hb, s * 3.42 * hb, 26.48, 27.26, 69.04, 70.31, "lid", rotation=[2, s * -14, s * -9])
        box(lc, s * 1.40 * hb, s * 3.42 * hb, 26.40, 26.52, 69.06, 70.32, "eye_lash", rotation=[2, s * -14, s * -9])
        box(ulid_l if s > 0 else ulid_r, s * 1.40 * hb, s * 3.44 * hb, 24.40, 25.20, 68.45, 70.31, "under_eye", rotation=[4, s * -14, s * -6])
        box(head_cubes, s * 1.24 * hb, s * 1.66 * hb, 23.92, 25.78, 69.4, 70.55, "tear_line", rotation=[16, s * -7, 0])
        ec = ear_l if s > 0 else ear_r
        ex, ey, er = s * 4.16 * hb, 27.9, [5, s * -8, s * -8]
        box(ec, ex - 1.22, ex + 1.22, ey, ey + 2.52, 64.1, 66.3, "ear_outer", rotation=er)
        box(ec, ex - 0.86, ex + 0.86, ey + 2.32, ey + 3.06, 64.3, 66.1, "ear_outer", rotation=er)
        box(ec, ex - 0.68, ex + 0.68, ey + 0.44, ey + 2.18, 65.7, 66.44, "ear_inner", rotation=er)

    sym(head_cubes, 1.64 * hb, 24.95, 26.70, 67.7, 74.20, "snout_bridge", rotation=[12.5, 0, 0], pivot=[0, 25.8, oz(70.9)])
    for s in (-1, 1):
        box(head_cubes, s * 1.20 * hb, s * 2.74 * hb, 22.30, 25.40, 67.9, 73.50, "muzzle", rotation=[6, s * -14, 0])
    sym(head_cubes, 2.34 * hb, 22.10, 25.45, 67.9, 73.40, "muzzle")
    sym(nose_cubes, 1.74 * hb, 23.60, 24.32, 73.25, 74.30, "nose")
    sym(nose_cubes, 1.18 * hb, 23.16, 23.68, 73.30, 74.24, "nose")
    sym(nose_cubes, 0.62 * hb, 22.82, 23.24, 73.35, 74.20, "nose")
    sym(head_cubes, 0.22, 21.58, 22.88, 73.25, 74.06, "lip_cleft")
    for s in (-1, 1):
        box(head_cubes, s * 0.20 * hb, s * 2.72 * hb, 21.58, 23.48, 69.4, 74.00, "whisker_pad", rotation=[0, s * -13, 0])
        box(head_cubes, s * 0.20 * hb, s * 2.62 * hb, 21.32, 21.68, 69.2, 73.90, "lip_dark", rotation=[0, s * -13, 0])
        box(head_cubes, s * 2.62 * hb, s * 5.5, 22.74, 22.92, 72.0, 72.16, "whisker", rotation=[0, s * 17, s * -4])
        box(head_cubes, s * 2.56 * hb, s * 5.3, 22.22, 22.40, 71.6, 71.76, "whisker", rotation=[0, s * 21, s * -10])
        box(head_cubes, s * 2.46 * hb, s * 4.8, 21.76, 21.92, 71.1, 71.26, "whisker", rotation=[0, s * 23, s * -15])
    sym(jaw_cubes, 2.32 * hb, 20.12, 21.78, 65.8, 72.75, "jaw")
    sym(jaw_cubes, 1.88 * hb, 19.72, 21.44, 70.2, 73.48, "chin")
    sym(jaw_cubes, 1.30 * hb, 19.42, 20.12, 70.6, 73.15, "chin")
    tongue_tip_piv = feline_mouth(
        head_cubes, jaw_cubes, tongue_cubes, 0.0, 22.32, oz(66.7), oz(72.2), 3.0 * hb, 1.38,
        tongue_tip_cubes=tongue_tip_cubes, detailed=True,
    )
    return {
        "head": head_cubes, "jaw": jaw_cubes, "tongue": tongue_cubes, "tongue_tip": tongue_tip_cubes,
        "nose": nose_cubes,
        "ear_left": ear_l, "ear_right": ear_r,
        "brow_left": brow_l, "brow_right": brow_r,
        "lid_left": lid_l, "lid_right": lid_r,
        "ulid_left": ulid_l, "ulid_right": ulid_r,
        "pupil_left": pup_l, "pupil_right": pup_r,
        "jaw_pivot": jaw_piv,
        "tongue_tip_pivot": tongue_tip_piv,
        "ear_piv": (ear_piv_x, ear_piv_y, ear_piv_ox),
        "eye_piv": (eye_piv_x, eye_piv_y, eye_piv_ox),
        "cheek_clear": cheek_clear,
    }


def build_male_lion(look_key="photo_king"):
    cfg = LOOKS[look_key]
    bulk, tuck, mfull, hb = cfg["body_bulk"], cfg["flank_tuck"], cfg["mane_fullness"], cfg["head_breadth"]
    back_ext, drop_ext, keel_bot = cfg["back_ext"], cfg["drop_ext"], cfg["keel_bot"]

    chest_cubes, waist_cubes, hips_cubes = [], [], []
    chest_hw = 7.1 * bulk
    tapered_slice(chest_cubes, 54.4, 60.4, 12.2, 25.4, chest_hw * 0.98, belly_h=1.7)
    tapered_slice(chest_cubes, 48.8, 54.45, 11.8, 25.1, chest_hw * 1.04, belly_h=1.9)
    tapered_slice(chest_cubes, 44.8, 48.85, 12.8, 24.4, chest_hw * 0.94, belly_h=1.6)
    sym(chest_cubes, chest_hw * 0.64, 13.4, 22.4, 58.6, 61.0, "coat")
    for s in (-1, 1):
        box(chest_cubes, s * (chest_hw * 0.50), s * (chest_hw * 1.08), 16.4, 24.5, 50.2, 58.0, "coat")

    waist_hw = 5.65 * bulk / (0.86 + 0.14 * tuck)
    flank_y = 14.4 + 1.55 * (tuck - 1.0)
    tapered_slice(waist_cubes, 40.0, 45.1, flank_y - 0.6, 23.8, waist_hw * 1.05, belly_h=1.4)
    tapered_slice(waist_cubes, 35.0, 40.1, flank_y + 0.4, 23.5, waist_hw * 0.97, belly_h=1.3)

    hip_hw = 6.45 * bulk
    tapered_slice(hips_cubes, 29.8, 35.2, flank_y + 0.2, 24.1, hip_hw * 1.01, belly_h=1.3)
    tapered_slice(hips_cubes, 25.4, 29.9, 15.2, 23.7, hip_hw * 0.94, belly_h=1.3)
    sym(hips_cubes, hip_hw * 0.78, 16.6, 22.8, 23.6, 25.6, "coat")
    sym(hips_cubes, hip_hw * 0.56, 18.0, 21.9, 22.8, 24.2, "coat")
    for s in (-1, 1):
        box(hips_cubes, s * (hip_hw * 0.52), s * (hip_hw * 1.08), 15.6, 23.2, 25.6, 33.8, "coat")

    if cfg["belly_fringe"]:
        sym(chest_cubes, 3.0 * bulk, 9.8, 13.8, 45.2, 57.0, "mane_black", _layer=0.96)
        sym(waist_cubes, 2.2 * bulk, flank_y - 1.5, flank_y + 1.1, 38.0, 46.2, "mane_black", _layer=0.97)
        sym(waist_cubes, 1.5 * bulk, flank_y - 0.9, flank_y + 1.0, 34.8, 38.8, "mane_black", _layer=0.98)

    neck_cubes = []
    sym(neck_cubes, 5.0 * bulk, 18.0, 26.2, 57.2, 61.4, "coat")
    sym(neck_cubes, 4.3 * bulk, 19.8, 27.6, 60.4, 64.4, "coat")

    hd = build_stalker_king_head(hb)
    cc = hd["cheek_clear"]

    mane_crown, mane_cheeks, mane_shoulders, mane_chest = [], [], [], []
    sym(mane_crown, 2.8 * mfull, 28.6, 31.5, 61.4, 66.2, "mane_crown", _layer=0.04)
    sym(mane_crown, 4.4 * mfull, 27.6, 30.8, 58.6, 64.0, "mane_crown", _layer=0.10)
    for s in (-1, 1):
        box(mane_cheeks, s * cc, s * (cc + 2.4 * mfull), 22.6, 28.4, 61.2, 65.8, "mane_crown",
            rotation=[0.0, s * -8.0, s * -10.0], _layer=0.18)
        box(mane_cheeks, s * (cc - 0.15), s * (cc + 4.1 * mfull), 17.8, 26.6, 58.0, 63.8, "mane_mid",
            rotation=[4.0, s * -6.0, s * -15.0], _layer=0.44)
        box(mane_cheeks, s * 2.0 * hb, s * 6.0 * mfull, 16.6, 20.4, 60.2, 65.4, "mane_black",
            rotation=[0.0, 0.0, s * -10.0], _layer=0.80)
    sym(mane_cheeks, 3.6 * mfull, 16.4, 19.6, 61.2, 65.8, "mane_black", _layer=0.84)

    cape_back = 49.4 - back_ext
    sym(mane_shoulders, 7.2 * mfull, 22.0, 30.2, 54.4, 60.4, "mane_mid", rotation=[14.0, 0.0, 0.0], _layer=0.36)
    sym(mane_shoulders, 7.5 * mfull, 18.8, 28.4, 50.8, 57.2, "mane_mid", rotation=[16.0, 0.0, 0.0], _layer=0.52)
    sym(mane_shoulders, 6.8 * mfull, 21.4, 26.8, cape_back, 53.6, "mane_mid", rotation=[14.0, 0.0, 0.0], _layer=0.64)
    sym(mane_shoulders, 4.9 * mfull, 23.4, 25.8, cape_back - 3.4, cape_back + 1.8, "mane_mid", rotation=[10.0, 0.0, 0.0], _layer=0.72)
    sym(mane_shoulders, 2.8 * mfull, 24.4, 25.3, cape_back - 5.8, cape_back - 1.8, "mane_mid", _layer=0.78)

    sym(mane_chest, 6.4 * mfull, 13.8, 20.4, 55.8, 63.0, "mane_black", _layer=0.86)
    sym(mane_chest, 4.8 * mfull, keel_bot + 1.6, 15.8, 55.8, 61.8, "mane_black", _layer=0.92)
    sym(mane_chest, 3.0 * mfull, keel_bot, keel_bot + 2.8, 55.4, 60.4, "mane_black", _layer=0.98)
    for s in (-1, 1):
        box(mane_chest, s * 5.5 * bulk, s * 8.2 * mfull, 14.6 - drop_ext * 0.5, 23.0, 52.8, 59.4,
            "mane_black", rotation=[4.0, 0.0, s * -10.0], _layer=0.78)

    crown_locks = [
        (0.0, 32.0, 28.8, 66.6, 61.8, 2.2, 14, 0, 0, 0.00),
        (1.8, 31.4, 28.2, 66.4, 61.6, 2.1, 14, -6, -12, 0.04),
        (-1.8, 31.4, 28.2, 66.4, 61.6, 2.1, 14, 6, 12, 0.04),
        (3.5, 30.4, 27.4, 65.2, 60.0, 2.1, 15, -10, -18, 0.10),
        (-3.5, 30.4, 27.4, 65.2, 60.0, 2.1, 15, 10, 18, 0.10),
        (0.0, 31.6, 28.5, 62.6, 56.4, 3.0, 18, 0, 0, 0.14),
        (2.6, 30.8, 27.6, 62.2, 56.0, 2.4, 18, -6, -12, 0.18),
        (-2.6, 30.8, 27.6, 62.2, 56.0, 2.4, 18, 6, 12, 0.18),
    ]
    for k, (cx, yt, yb, oxf, oxb, w, pd, yd, rd, lay) in enumerate(crown_locks):
        mane_lock(mane_crown, cx * mfull, yt, yb, oxf, oxb, w, "mane_crown", lay, k, 1, pd, yd, rd, yb)

    pad_x = max(0.0, cc - 3.5 * hb)
    side_cascades = [
        (4.8 + pad_x, 28.4, 22.0, 65.6, 62.2, 2.0, 6, -12, -16, 0.14, "mane_crown", mane_cheeks),
        (5.8 + pad_x, 29.0, 18.8, 63.2, 58.8, 2.2, 9, -8, -18, 0.30, "mane_crown", mane_cheeks),
        (6.6 + pad_x * 0.7, 28.4, 15.2 - drop_ext * 0.6, 60.6, 55.2, 2.5, 12, -5, -16, 0.52, "mane_mid", mane_shoulders),
        (7.1 + pad_x * 0.5, 27.2, 16.4 - drop_ext * 0.6, 56.4, 50.2 - back_ext, 2.5, 14, -3, -10, 0.68, "mane_mid", mane_shoulders),
        (6.2 + pad_x * 0.3, 26.0, 19.4 - drop_ext * 0.4, 52.0 - back_ext * 0.4, 45.8 - back_ext, 2.4, 16, -2, -6, 0.78, "mane_mid", mane_shoulders),
        (6.4 + pad_x * 0.4, 20.6, 12.8 - drop_ext * 0.7, 62.6, 57.6, 2.3, 4, -6, -12, 0.88, "mane_black", mane_chest),
        (5.1 + pad_x * 0.3, 19.6, 11.4 - drop_ext * 0.7, 63.4, 58.8, 2.2, 2, -8, -6, 0.92, "mane_black", mane_chest),
    ]
    for s in (-1, 1):
        for k, (xt, yt, yb, oxf, oxb, w, pd, yd, rd, lay, ptag, tgt) in enumerate(side_cascades):
            mane_lock(tgt, s * xt * mfull, yt, yb, oxf, oxb, w, ptag, lay, k * 2 + (1 if s > 0 else 0), 2,
                      pd, s * yd, s * rd, yt)

    for k, (cx, yt, yb, oxf, oxb, w, lay) in enumerate([
        (0.0, 29.6, 26.6, 57.6, 51.0 - back_ext * 0.5, 4.2 * mfull, 0.42),
        (2.9 * mfull, 28.4, 25.0, 56.8, 49.8 - back_ext * 0.7, 2.8, 0.56),
        (-2.9 * mfull, 28.4, 25.0, 56.8, 49.8 - back_ext * 0.7, 2.8, 0.56),
        (0.0, 27.0, 24.5, 52.4 - back_ext * 0.4, 45.4 - back_ext, 3.6 * mfull, 0.68),
    ]):
        mane_lock(mane_shoulders, cx, yt, yb, oxf, oxb, w, "mane_mid", lay, k, 3, 18, 0, 0, yt)

    for k, (cx, yt, yb, oxf, oxb, w, pd, lay) in enumerate([
        (0.0, 19.6, keel_bot + 0.4, 63.6, 58.8, 3.0, -14, 0.90),
        (2.7 * mfull, 20.2, keel_bot + 1.6, 63.2, 58.4, 2.5, -12, 0.88),
        (-2.7 * mfull, 20.2, keel_bot + 1.6, 63.2, 58.4, 2.5, -12, 0.88),
        (0.0, 14.8, keel_bot - 0.1, 61.4, 57.2, 2.2, -18, 0.98),
        (2.0 * mfull, 15.4, keel_bot + 0.9, 61.2, 57.0, 2.0, -15, 0.95),
        (-2.0 * mfull, 15.4, keel_bot + 0.9, 61.2, 57.0, 2.0, -15, 0.95),
    ]):
        mane_lock(mane_chest, cx, yt, yb, oxf, oxb, w, "mane_black", lay, k, 4, pd, 0, 0, yt)

    leg_bones = []
    for side, tag in ((1, "fl"), (-1, "fr")):
        sx, ex, wx = side * (6.2 * bulk), side * (5.8 * bulk), side * (5.3 * bulk)
        sh, el, wr = [sx, 20.2, oz(54.2)], [ex, 12.4, oz(51.8)], [wx, 3.0, oz(53.4)]
        up, low = [], []
        # Clean bone-aligned upper arm & forearm (zero colliding 0-deg shoulder squares during high swipes!)
        aligned_seg(up, sh, el, -0.16, 0.48, 4.6 * bulk, 5.6 * bulk, "coat", z_off=0.15)
        aligned_seg(up, sh, el, 0.20, 1.06, 4.2 * bulk, 4.8 * bulk, "leg_upper")
        if cfg["elbow_tufts"]:
            t1_tuft = 1.16 + max(0.0, (cfg["elbow_len"] - 1.8) * 0.14)
            aligned_seg(up, sh, el, 0.72, t1_tuft, 2.8 * bulk, 3.0 * bulk, "mane_black", z_off=2.1 * bulk, _layer=0.95)
        aligned_seg(low, el, wr, -0.08, 0.56, 4.0 * bulk, 4.6 * bulk, "leg_lower")
        aligned_seg(low, el, wr, 0.44, 1.06, 3.4 * bulk, 3.8 * bulk, "leg_lower")
        leg_bones.extend([
            {"name": f"leg_{tag}_1", "parent": "chest", "pivot": sh, "cubes": up},
            {"name": f"leg_{tag}_2", "parent": f"leg_{tag}_1", "pivot": el, "cubes": low},
            *splaying_hunter_paw(tag, f"leg_{tag}_2", wr, wx, 0.0, oz(54.3),
                                 width=5.10 * bulk, length=5.80 * bulk, height=3.20),
        ])

    # Locked Variation B ("Sleek Athletic Sprinter") bone-aligned hind leg muscle sculpt!
    for side, tag in ((1, "bl"), (-1, "br")):
        hx, kx, ax = side * (5.7 * bulk), side * (5.6 * bulk), side * (5.1 * bulk)
        hip, knee, hock, ankle = [hx, 20.0, oz(29.0)], [kx, 14.6, oz(32.6)], [ax, 8.2, oz(24.2)], [ax, 2.8, oz(26.6)]
        thigh, shin, meta = [], [], []
        aligned_seg(thigh, hip, knee, -0.14, 0.36, 3.9 * bulk, 5.5 * bulk, "coat", z_off=0.28)
        aligned_seg(thigh, hip, knee, 0.12, 0.78, 4.3 * bulk, 6.1 * bulk, "leg_upper", z_off=0.14)
        aligned_seg(thigh, hip, knee, 0.60, 1.10, 3.6 * bulk, 4.3 * bulk, "leg_upper", z_off=-0.18)
        aligned_seg(shin, knee, hock, -0.06, 0.46, 3.6 * bulk, 4.7 * bulk, "leg_lower", z_off=0.34)
        aligned_seg(shin, knee, hock, 0.36, 1.04, 2.75 * bulk, 2.95 * bulk, "leg_lower", z_off=-0.10)
        aligned_seg(shin, knee, hock, 0.88, 1.11, 2.85 * bulk, 3.15 * bulk, "leg_lower", z_off=0.15)
        aligned_seg(meta, hock, ankle, -0.08, 0.58, 2.75 * bulk, 2.95 * bulk, "leg_lower")
        aligned_seg(meta, hock, ankle, 0.42, 1.06, 2.60 * bulk, 2.75 * bulk, "leg_lower")
        leg_bones.extend([
            {"name": f"leg_{tag}_1", "parent": "hips", "pivot": hip, "cubes": thigh},
            {"name": f"leg_{tag}_2", "parent": f"leg_{tag}_1", "pivot": knee, "cubes": shin},
            {"name": f"leg_{tag}_3", "parent": f"leg_{tag}_2", "pivot": hock, "cubes": meta},
            *splaying_hunter_paw(tag, f"leg_{tag}_3", ankle, ax, 0.0, oz(27.4),
                                 width=4.45 * bulk, length=5.20 * bulk, height=3.00),
        ])

    tail_bones = []
    t_pts = [(23.8, 20.6), (19.2, 15.6), (14.4, 10.6), (9.4, 7.8), (4.6, 8.4), (1.0, 10.4)]
    t_w = [(1.60, 1.38), (1.38, 1.22), (1.22, 1.08), (1.08, 0.98), (0.98, 0.92)]
    prev = "hips"
    for i in range(5):
        p0, p1 = [0.0, t_pts[i][1], oz(t_pts[i][0])], [0.0, t_pts[i + 1][1], oz(t_pts[i + 1][0])]
        tc = []
        muscular_limb(tc, p0, p1, t_w[i][0] * 2.0, t_w[i][0] * 2.0, t_w[i][1] * 2.0, t_w[i][1] * 2.0, "tail", overlap=0.55)
        if i == 4:
            yb = t_pts[5][1]
            sym(tc, 1.65, yb - 1.65, yb + 1.65, 3.0, 5.8, "tail_tuft", rotation=[-22.0, 0.0, 0.0])
            sym(tc, 2.20, yb - 2.20, yb + 2.20, 1.2, 4.4, "tail_tuft", rotation=[-22.0, 0.0, 0.0])
            sym(tc, 1.55, yb - 1.55, yb + 1.55, 0.35, 2.0, "tail_tuft", rotation=[-22.0, 0.0, 0.0])
            sym(tc, 0.95, yb - 0.95, yb + 0.95, 0.0, 1.0, "tail_tuft")
        tname = f"tail_{i + 1}"
        tail_bones.append({"name": tname, "parent": prev, "pivot": p0, "cubes": tc})
        prev = tname

    ex_p, ey_p, eox_p = hd["ear_piv"]
    px_p, py_p, pox_p = hd["eye_piv"]
    return [
        {"name": "root", "pivot": [0.0, 0.0, 0.0], "cubes": []},
        {"name": "body", "parent": "root", "pivot": [0.0, 18.5, oz(44.0)], "cubes": []},
        {"name": "chest", "parent": "body", "pivot": [0.0, 19.0, oz(45.5)], "cubes": chest_cubes},
        {"name": "waist", "parent": "body", "pivot": [0.0, 18.8, oz(44.0)], "cubes": waist_cubes},
        {"name": "hips", "parent": "waist", "pivot": [0.0, 18.8, oz(35.0)], "cubes": hips_cubes},
        {"name": "neck", "parent": "chest", "pivot": [0.0, 21.6, oz(58.0)], "cubes": neck_cubes},
        {"name": "head", "parent": "neck", "pivot": [0.0, 24.8, oz(63.4)], "cubes": hd["head"]},
        {"name": "jaw", "parent": "head", "pivot": hd["jaw_pivot"], "cubes": hd["jaw"]},
        {"name": "tongue", "parent": "jaw", "pivot": [0.0, 21.3, oz(67.2)], "cubes": hd["tongue"]},
        {"name": "tongue_tip", "parent": "tongue", "pivot": hd["tongue_tip_pivot"], "cubes": hd["tongue_tip"]},
        {"name": "carry", "parent": "head", "pivot": [0.0, 21.65, oz(70.5)], "cubes": []},
        {"name": "nose", "parent": "head", "pivot": [0.0, 23.95, oz(73.30)], "cubes": hd["nose"]},
        {"name": "ear_left", "parent": "head", "pivot": [ex_p, ey_p, oz(eox_p)], "cubes": hd["ear_left"]},
        {"name": "ear_right", "parent": "head", "pivot": [-ex_p, ey_p, oz(eox_p)], "cubes": hd["ear_right"]},
        {"name": "brow_left", "parent": "head", "pivot": [px_p, py_p + 1.25, oz(pox_p - 1.4)], "cubes": hd["brow_left"]},
        {"name": "brow_right", "parent": "head", "pivot": [-px_p, py_p + 1.25, oz(pox_p - 1.4)], "cubes": hd["brow_right"]},
        {"name": "lid_left", "parent": "head", "pivot": [px_p, 27.26, oz(69.65)], "cubes": hd["lid_left"]},
        {"name": "lid_right", "parent": "head", "pivot": [-px_p, 27.26, oz(69.65)], "cubes": hd["lid_right"]},
        {"name": "ulid_left", "parent": "head", "pivot": [px_p, 24.40, oz(69.36)], "cubes": hd["ulid_left"]},
        {"name": "ulid_right", "parent": "head", "pivot": [-px_p, 24.40, oz(69.36)], "cubes": hd["ulid_right"]},
        {"name": "pupil_left", "parent": "head", "pivot": [px_p, py_p, oz(pox_p)], "cubes": hd["pupil_left"]},
        {"name": "pupil_right", "parent": "head", "pivot": [-px_p, py_p, oz(pox_p)], "cubes": hd["pupil_right"]},
        {"name": "mane_crown", "parent": "head", "pivot": [0.0, 28.0, oz(63.4)], "cubes": mane_crown},
        {"name": "mane_cheeks", "parent": "head", "pivot": [0.0, 24.0, oz(63.2)], "cubes": mane_cheeks},
        {"name": "mane_shoulders", "parent": "chest", "pivot": [0.0, 23.0, oz(56.5)], "cubes": mane_shoulders},
        {"name": "mane_chest", "parent": "chest", "pivot": [0.0, 17.5, oz(60.2)], "cubes": mane_chest},
        *tail_bones,
        *leg_bones,
    ]


# ====================================================================================================
# 2. AFRICAN LIONESS — SLEEK ATHLETIC HUNTER (~4.06 x 1.70 BLOCKS, SHARED RIG)
# ====================================================================================================
def build_lioness():
    TL = LIONESS_LEN

    def loz(ox):
        return oz(ox, TL)

    def lbox(cubes, x0, x1, y0, y1, ox0, ox1, part, rotation=None, pivot=None, **extra):
        return box(cubes, x0, x1, y0, y1, ox0, ox1, part, rotation=rotation, pivot=pivot, total_len=TL, **extra)

    def lsym(cubes, hw, y0, y1, ox0, ox1, part, rotation=None, pivot=None, **extra):
        return sym(cubes, hw, y0, y1, ox0, ox1, part, rotation=rotation, pivot=pivot, total_len=TL, **extra)

    # 1. Sleek athletic torso — prominent shoulder blades, deep tucked waist, taut curved haunches
    chest_cubes, waist_cubes, hips_cubes = [], [], []
    chest_hw = 5.65
    tapered_slice(chest_cubes, 47.6, 52.8, 11.4, 22.2, chest_hw * 0.96, belly_h=1.5, total_len=TL)
    tapered_slice(chest_cubes, 42.8, 47.7, 11.0, 22.0, chest_hw * 1.03, belly_h=1.7, total_len=TL)
    tapered_slice(chest_cubes, 39.2, 42.9, 12.0, 21.4, chest_hw * 0.92, belly_h=1.4, total_len=TL)
    # Visible sculpted creamy brisket & rounded front chest (silky smooth taper, zero stair-teeth!)
    lsym(chest_cubes, chest_hw * 0.80, 12.2, 21.0, 51.4, 54.0, "coat")
    lsym(chest_cubes, chest_hw * 0.64, 12.0, 19.6, 52.8, 54.8, "belly")
    lsym(chest_cubes, chest_hw * 0.48, 13.2, 19.2, 54.0, 55.4, "belly")
    # Sculpted muscular shoulder-blade contours (scapulae)
    for s in (-1, 1):
        lbox(chest_cubes, s * (chest_hw * 0.46), s * (chest_hw * 1.07), 14.8, 21.8, 44.2, 51.2, "coat")
        lbox(chest_cubes, s * (chest_hw * 0.30), s * (chest_hw * 0.88), 20.6, 22.3, 45.2, 50.8, "coat")

    # Deep athletic lioness flank tuck!
    waist_hw = 4.45
    tapered_slice(waist_cubes, 35.0, 39.4, 13.4, 21.0, waist_hw * 1.04, belly_h=1.25, total_len=TL)
    tapered_slice(waist_cubes, 30.8, 35.1, 14.2, 20.8, waist_hw * 0.96, belly_h=1.15, total_len=TL)

    # Lithe curved hunter haunches
    hip_hw = 5.35
    tapered_slice(hips_cubes, 26.2, 31.0, 13.8, 21.5, hip_hw * 1.01, belly_h=1.2, total_len=TL)
    tapered_slice(hips_cubes, 22.4, 26.3, 14.4, 21.2, hip_hw * 0.93, belly_h=1.2, total_len=TL)
    lsym(hips_cubes, hip_hw * 0.76, 15.6, 20.5, 20.8, 22.6, "coat")
    lsym(hips_cubes, hip_hw * 0.52, 16.8, 19.7, 20.0, 21.4, "coat")
    for s in (-1, 1):
        lbox(hips_cubes, s * (hip_hw * 0.48), s * (hip_hw * 1.07), 14.6, 20.8, 22.6, 29.8, "coat")

    # 2. Graceful sleek curving feline neck + smooth creamy throat curve (zero zig-zag steps!)
    neck_cubes = []
    lsym(neck_cubes, 4.40, 15.4, 22.5, 49.4, 53.6, "coat", rotation=[7, 0, 0])
    lsym(neck_cubes, 3.85, 17.0, 23.6, 52.2, 55.8, "coat", rotation=[7, 0, 0])
    lsym(neck_cubes, 3.35, 18.6, 24.5, 54.6, 57.8, "coat", rotation=[4, 0, 0])
    # Smooth creamy throat-line flowing cleanly from brisket up under the jaw!
    lsym(neck_cubes, 3.10, 14.8, 18.8, 52.6, 55.8, "belly")
    lsym(neck_cubes, 2.65, 16.8, 20.0, 54.8, 58.2, "belly")
    for s in (-1, 1):
        lbox(neck_cubes, s * 2.1, s * 4.05, 17.4, 22.2, 51.4, 56.0, "coat", rotation=[4, s * -8, 0])

    # 3. Refined Stalker-King Feminine Head (lifted +0.5 px for proud graceful stance, shining hunter eyes!)
    head_cubes, jaw_cubes, tongue_cubes, tongue_tip_cubes = [], [], [], []
    ear_l, ear_r, brow_l, brow_r, lid_l, lid_r, ulid_l, ulid_r, pup_l, pup_r, nose_cubes = [], [], [], [], [], [], [], [], [], [], []
    lsym(head_cubes, 3.30, 19.9, 24.8, 55.6, 61.0, "head")
    lsym(head_cubes, 2.85, 20.4, 24.4, 54.5, 56.6, "head", rotation=[-5, 0, 0])
    lsym(head_cubes, 2.95, 24.3, 25.45, 56.0, 61.3, "forehead", rotation=[-4.0, 0.0, 0.0])
    lsym(head_cubes, 2.08, 25.1, 25.92, 55.8, 60.3, "forehead", rotation=[-7.0, 0.0, 0.0])
    lsym(head_cubes, 2.45, 18.9, 20.3, 55.8, 59.8, "belly")

    for s in (-1, 1):
        # Smooth graceful feminine cheekbones
        lbox(head_cubes, s * 1.80, s * 3.66, 21.7, 24.1, 56.8, 60.8, "head_cheek", rotation=[0, s * -13, s * -4])
        lbox(head_cubes, s * 1.38, s * 2.88, 19.5, 22.0, 57.4, 61.3, "head_cheek", rotation=[0, s * -15, 0])
        # Calm, relaxed, noble savanna queen brow (promoted to brow_left/right for live expressions!) & open luminous golden eyes
        lbox(brow_l if s > 0 else brow_r, s * 0.96, s * 3.36, 24.36, 25.06, 59.5, 62.38, "brow", rotation=[0, s * -11, 0])
        lbox(head_cubes, s * 1.24, s * 2.94, 22.86, 24.22, 60.45, 61.88, "eye_socket", rotation=[0, s * -11, 0])
        lbox(head_cubes, s * 1.18, s * 3.00, 22.88, 24.18, 61.22, 62.28, "eye_lioness", rotation=[0, s * -11, 0])
        exm = s * 2.08
        lbox(pup_l if s > 0 else pup_r, exm - 0.28, exm + 0.28, 23.12, 23.92, 62.14, 62.34, "pupil", rotation=[0, s * -11, 0])
        # Flush dual eyelids meeting smoothly in the middle with crisp dark lash seam!
        lc = lid_l if s > 0 else lid_r
        lbox(lc, s * 1.16, s * 3.02, 24.18, 24.92, 61.18, 62.35, "lid", rotation=[0, s * -11, 0])
        lbox(lc, s * 1.16, s * 3.02, 24.10, 24.22, 61.20, 62.36, "eye_lash", rotation=[0, s * -11, 0])
        lbox(ulid_l if s > 0 else ulid_r, s * 1.18, s * 3.02, 22.18, 22.90, 60.58, 62.35, "under_eye", rotation=[2, s * -11, 0])
        lbox(head_cubes, s * 1.06, s * 1.38, 21.80, 23.38, 61.4, 62.40, "tear_line", rotation=[14, s * -6, 0])
        # ICONIC prominent rounded lioness ears in a calm, relaxed poise
        ec = ear_l if s > 0 else ear_r
        ex, ey, er = s * 3.45, 24.7, [2, s * -12, s * -14]
        lbox(ec, ex - 1.22, ex + 1.22, ey, ey + 2.55, 56.4, 58.4, "ear_outer", rotation=er)
        lbox(ec, ex - 0.88, ex + 0.88, ey + 2.30, ey + 3.10, 56.6, 58.2, "ear_outer", rotation=er)
        lbox(ec, ex - 0.68, ex + 0.68, ey + 0.42, ey + 2.18, 57.9, 58.52, "ear_inner", rotation=er)

    # Sleek 12° Roman nose bridge & agile hunter muzzle
    lsym(head_cubes, 1.40, 22.65, 24.20, 59.8, 65.25, "snout_bridge", rotation=[12.0, 0, 0], pivot=[0, 23.4, loz(62.5)])
    for s in (-1, 1):
        lbox(head_cubes, s * 1.02, s * 2.34, 20.30, 23.05, 60.0, 64.65, "muzzle", rotation=[6, s * -14, 0])
    lsym(head_cubes, 2.00, 20.15, 23.10, 60.0, 64.55, "muzzle")
    lsym(nose_cubes, 1.48, 21.45, 22.08, 64.45, 65.38, "nose")
    lsym(nose_cubes, 1.00, 21.06, 21.52, 64.50, 65.33, "nose")
    lsym(nose_cubes, 0.52, 20.76, 21.14, 64.55, 65.28, "nose")
    lsym(head_cubes, 0.20, 19.68, 20.82, 64.45, 65.16, "lip_cleft")
    for s in (-1, 1):
        lbox(head_cubes, s * 0.18, s * 2.34, 19.68, 21.35, 61.0, 65.10, "whisker_pad", rotation=[0, s * -13, 0])
        lbox(head_cubes, s * 0.18, s * 2.24, 19.45, 19.78, 60.8, 65.00, "lip_dark", rotation=[0, s * -13, 0])
        lbox(head_cubes, s * 2.24, s * 4.7, 20.72, 20.88, 63.4, 63.54, "whisker", rotation=[0, s * 17, s * -4])
        lbox(head_cubes, s * 2.18, s * 4.5, 20.24, 20.40, 63.0, 63.14, "whisker", rotation=[0, s * 21, s * -10])
        lbox(head_cubes, s * 2.10, s * 4.1, 19.82, 19.96, 62.6, 62.74, "whisker", rotation=[0, s * 23, s * -15])
    lsym(jaw_cubes, 1.98, 18.35, 19.88, 58.0, 64.00, "jaw")
    lsym(jaw_cubes, 1.60, 17.98, 19.56, 61.9, 64.65, "chin")
    lsym(jaw_cubes, 1.08, 17.70, 18.35, 62.2, 64.35, "chin")
    tongue_tip_piv = feline_mouth(
        head_cubes, jaw_cubes, tongue_cubes, 0.0, 20.36, loz(58.9), loz(63.5), 2.55, 1.20,
        tongue_tip_cubes=tongue_tip_cubes, detailed=True,
    )

    # 4. Lithe fast hunter limbs (bone-aligned Sleek Athletic Sprinter sculpt) & Splaying Hunter paws (zero elbow tufts!)
    leg_bones = []
    for side, tag in ((1, "fl"), (-1, "fr")):
        sx, ex, wx = side * 5.05, side * 4.75, side * 4.35
        sh, el, wr = [sx, 17.8, loz(47.8)], [ex, 10.8, loz(45.6)], [wx, 2.55, loz(47.2)]
        up, low = [], []
        aligned_seg(up, sh, el, -0.16, 0.48, 3.85, 4.70, "coat", z_off=0.12)
        aligned_seg(up, sh, el, 0.20, 1.06, 3.45, 3.95, "leg_upper")
        aligned_seg(low, el, wr, -0.08, 0.56, 3.25, 3.75, "leg_lower")
        aligned_seg(low, el, wr, 0.44, 1.06, 2.70, 3.05, "leg_lower")
        leg_bones.extend([
            {"name": f"leg_{tag}_1", "parent": "chest", "pivot": sh, "cubes": up},
            {"name": f"leg_{tag}_2", "parent": f"leg_{tag}_1", "pivot": el, "cubes": low},
            *splaying_hunter_paw(tag, f"leg_{tag}_2", wr, wx, 0.0, loz(47.9),
                                 width=4.15, length=4.90, height=2.75),
        ])

    for side, tag in ((1, "bl"), (-1, "br")):
        hx, kx, ax = side * 4.75, side * 4.65, side * 4.20
        hip, knee, hock, ankle = [hx, 17.6, loz(25.6)], [kx, 12.8, loz(28.8)], [ax, 7.1, loz(21.4)], [ax, 2.4, loz(23.5)]
        thigh, shin, meta = [], [], []
        aligned_seg(thigh, hip, knee, -0.14, 0.36, 3.35, 4.75, "coat", z_off=0.24)
        aligned_seg(thigh, hip, knee, 0.12, 0.78, 3.70, 5.25, "leg_upper", z_off=0.12)
        aligned_seg(thigh, hip, knee, 0.60, 1.10, 3.10, 3.70, "leg_upper", z_off=-0.15)
        aligned_seg(shin, knee, hock, -0.06, 0.46, 3.05, 4.00, "leg_lower", z_off=0.28)
        aligned_seg(shin, knee, hock, 0.36, 1.04, 2.35, 2.55, "leg_lower", z_off=-0.08)
        aligned_seg(shin, knee, hock, 0.88, 1.11, 2.45, 2.70, "leg_lower", z_off=0.12)
        aligned_seg(meta, hock, ankle, -0.08, 0.58, 2.35, 2.55, "leg_lower")
        aligned_seg(meta, hock, ankle, 0.42, 1.06, 2.25, 2.40, "leg_lower")
        leg_bones.extend([
            {"name": f"leg_{tag}_1", "parent": "hips", "pivot": hip, "cubes": thigh},
            {"name": f"leg_{tag}_2", "parent": f"leg_{tag}_1", "pivot": knee, "cubes": shin},
            {"name": f"leg_{tag}_3", "parent": f"leg_{tag}_2", "pivot": hock, "cubes": meta},
            *splaying_hunter_paw(tag, f"leg_{tag}_3", ankle, ax, 0.0, loz(24.2),
                                 width=3.65, length=4.40, height=2.55),
        ])

    # 5. Sleek S-curved hunter tail with refined smaller teardrop tuft
    tail_bones = []
    t_pts = [(21.0, 18.2), (17.0, 13.8), (12.6, 9.4), (8.2, 6.9), (4.0, 7.5), (0.8, 9.4)]
    t_w = [(1.32, 1.14), (1.14, 1.00), (1.00, 0.88), (0.88, 0.80), (0.80, 0.74)]
    prev = "hips"
    for i in range(5):
        p0, p1 = [0.0, t_pts[i][1], loz(t_pts[i][0])], [0.0, t_pts[i + 1][1], loz(t_pts[i + 1][0])]
        tc = []
        muscular_limb(tc, p0, p1, t_w[i][0] * 2.0, t_w[i][0] * 2.0, t_w[i][1] * 2.0, t_w[i][1] * 2.0, "tail", overlap=0.45)
        if i == 4:
            yb = t_pts[5][1]
            lsym(tc, 1.32, yb - 1.32, yb + 1.32, 2.5, 4.9, "tail_tuft", rotation=[-22.0, 0.0, 0.0])
            lsym(tc, 1.72, yb - 1.72, yb + 1.72, 1.0, 3.6, "tail_tuft", rotation=[-22.0, 0.0, 0.0])
            lsym(tc, 1.18, yb - 1.18, yb + 1.18, 0.30, 1.7, "tail_tuft", rotation=[-22.0, 0.0, 0.0])
            lsym(tc, 0.72, yb - 0.72, yb + 0.72, 0.0, 0.85, "tail_tuft")
        tname = f"tail_{i + 1}"
        tail_bones.append({"name": tname, "parent": prev, "pivot": p0, "cubes": tc})
        prev = tname

    return [
        {"name": "root", "pivot": [0.0, 0.0, 0.0], "cubes": []},
        {"name": "body", "parent": "root", "pivot": [0.0, 16.4, loz(38.5)], "cubes": []},
        {"name": "chest", "parent": "body", "pivot": [0.0, 16.8, loz(40.0)], "cubes": chest_cubes},
        {"name": "waist", "parent": "body", "pivot": [0.0, 16.6, loz(38.5)], "cubes": waist_cubes},
        {"name": "hips", "parent": "waist", "pivot": [0.0, 16.6, loz(31.0)], "cubes": hips_cubes},
        {"name": "neck", "parent": "chest", "pivot": [0.0, 19.0, loz(51.0)], "cubes": neck_cubes},
        {"name": "head", "parent": "neck", "pivot": [0.0, 22.3, loz(56.0)], "cubes": head_cubes},
        {"name": "jaw", "parent": "head", "pivot": [0.0, 19.65, loz(58.5)], "cubes": jaw_cubes},
        {"name": "tongue", "parent": "jaw", "pivot": [0.0, 19.4, loz(59.4)], "cubes": tongue_cubes},
        {"name": "tongue_tip", "parent": "tongue", "pivot": tongue_tip_piv, "cubes": tongue_tip_cubes},
        {"name": "carry", "parent": "head", "pivot": [0.0, 19.72, loz(62.2)], "cubes": []},
        {"name": "nose", "parent": "head", "pivot": [0.0, 21.75, loz(64.50)], "cubes": nose_cubes},
        {"name": "ear_left", "parent": "head", "pivot": [3.45, 24.9, loz(57.2)], "cubes": ear_l},
        {"name": "ear_right", "parent": "head", "pivot": [-3.45, 24.9, loz(57.2)], "cubes": ear_r},
        {"name": "brow_left", "parent": "head", "pivot": [2.16, 24.71, loz(60.71)], "cubes": brow_l},
        {"name": "brow_right", "parent": "head", "pivot": [-2.16, 24.71, loz(60.71)], "cubes": brow_r},
        {"name": "lid_left", "parent": "head", "pivot": [2.09, 24.92, loz(61.74)], "cubes": lid_l},
        {"name": "lid_right", "parent": "head", "pivot": [-2.09, 24.92, loz(61.74)], "cubes": lid_r},
        {"name": "ulid_left", "parent": "head", "pivot": [2.10, 22.18, loz(61.44)], "cubes": ulid_l},
        {"name": "ulid_right", "parent": "head", "pivot": [-2.10, 22.18, loz(61.44)], "cubes": ulid_r},
        {"name": "pupil_left", "parent": "head", "pivot": [2.08, 23.52, loz(62.28)], "cubes": pup_l},
        {"name": "pupil_right", "parent": "head", "pivot": [-2.08, 23.52, loz(62.28)], "cubes": pup_r},
        *tail_bones,
        *leg_bones,
    ]


# ====================================================================================================
# 3. AFRICAN LION CUB — BIG EYES, OVERSIZED FUZZY EARS, CHUNKY PAWS, SOFT ROSETTE SPOTS (~2.15 x 1.18 BLOCKS)
# ====================================================================================================
def build_cub():
    TL = CUB_LEN

    def coz(ox):
        return oz(ox, TL)

    def cbox(cubes, x0, x1, y0, y1, ox0, ox1, part, rotation=None, pivot=None, **extra):
        return box(cubes, x0, x1, y0, y1, ox0, ox1, part, rotation=rotation, pivot=pivot, total_len=TL, **extra)

    def csym(cubes, hw, y0, y1, ox0, ox1, part, rotation=None, pivot=None, **extra):
        return sym(cubes, hw, y0, y1, ox0, ox1, part, rotation=rotation, pivot=pivot, total_len=TL, **extra)

    # 1. Soft plump cub torso (milk-fed belly, minimal waist tuck!)
    chest_cubes, waist_cubes, hips_cubes = [], [], []
    chest_hw = 4.05
    tapered_slice(chest_cubes, 23.0, 26.5, 6.2, 13.0, chest_hw * 0.96, belly_h=1.2, total_len=TL)
    tapered_slice(chest_cubes, 20.0, 23.1, 6.0, 12.9, chest_hw * 1.02, belly_h=1.3, total_len=TL)
    csym(chest_cubes, chest_hw * 0.78, 6.8, 12.2, 25.4, 27.3, "coat")
    csym(chest_cubes, chest_hw * 0.62, 6.6, 11.4, 26.2, 27.8, "belly")
    for s in (-1, 1):
        cbox(chest_cubes, s * 1.9, s * (chest_hw * 1.06), 8.2, 12.6, 21.0, 25.6, "coat")

    waist_hw = 3.65
    tapered_slice(waist_cubes, 17.6, 20.2, 6.5, 12.5, waist_hw * 1.03, belly_h=1.2, total_len=TL)
    tapered_slice(waist_cubes, 15.2, 17.7, 6.8, 12.4, waist_hw * 0.98, belly_h=1.1, total_len=TL)

    hip_hw = 3.85
    tapered_slice(hips_cubes, 12.8, 15.4, 6.9, 12.7, hip_hw * 1.00, belly_h=1.1, total_len=TL)
    tapered_slice(hips_cubes, 10.6, 12.9, 7.4, 12.5, hip_hw * 0.92, belly_h=1.0, total_len=TL)
    csym(hips_cubes, hip_hw * 0.72, 8.2, 11.9, 9.7, 10.8, "coat")
    for s in (-1, 1):
        cbox(hips_cubes, s * 1.8, s * (hip_hw * 1.05), 7.8, 12.1, 11.0, 14.8, "coat")

    # 2. Short fluffy cub neck flowing smoothly into big cub head
    neck_cubes = []
    csym(neck_cubes, 3.25, 8.8, 13.4, 24.4, 27.4, "coat", rotation=[6, 0, 0])
    csym(neck_cubes, 2.95, 10.2, 14.5, 26.0, 28.4, "coat", rotation=[4, 0, 0])
    csym(neck_cubes, 2.60, 8.4, 11.4, 26.0, 28.8, "belly")

    # 3. Big adorable expressive cub head (huge warm luminous eyes, light delicate lashes, oversized fuzzy ears!)
    head_cubes, jaw_cubes, tongue_cubes, tongue_tip_cubes = [], [], [], []
    ear_l, ear_r, brow_l, brow_r, lid_l, lid_r, ulid_l, ulid_r, pup_l, pup_r, nose_cubes = [], [], [], [], [], [], [], [], [], [], []
    csym(head_cubes, 3.30, 11.4, 16.0, 26.6, 31.3, "head")
    csym(head_cubes, 2.85, 11.8, 15.6, 25.6, 27.4, "head", rotation=[-6, 0, 0])
    csym(head_cubes, 2.95, 15.5, 16.65, 27.0, 31.5, "forehead", rotation=[-5.0, 0.0, 0.0])
    csym(head_cubes, 2.15, 16.3, 17.05, 27.0, 30.6, "forehead", rotation=[-8.0, 0.0, 0.0])
    csym(head_cubes, 2.50, 10.6, 11.8, 26.8, 30.6, "belly")

    for s in (-1, 1):
        # Soft plump cub cheeks
        cbox(head_cubes, s * 1.75, s * 3.55, 12.6, 15.2, 27.4, 31.2, "head_cheek", rotation=[0, s * -10, s * -4])
        cbox(head_cubes, s * 1.35, s * 3.00, 11.2, 13.0, 28.0, 31.5, "head_cheek", rotation=[0, s * -12, 0])
        # Soft curious brow (promoted to brow_left/right!) + HUGE warm bright cub eyes with delicate lash border
        cbox(brow_l if s > 0 else brow_r, s * 0.85, s * 3.25, 15.26, 15.92, 29.8, 32.22, "brow", rotation=[4, s * -10, s * -4])
        cbox(head_cubes, s * 1.12, s * 2.96, 13.70, 15.24, 30.6, 31.95, "eye_socket", rotation=[2, s * -10, s * -3])
        cbox(head_cubes, s * 1.15, s * 2.93, 13.76, 15.16, 31.20, 32.12, "eye_cub", rotation=[2, s * -10, s * -3])
        exm = s * 2.04
        cbox(pup_l if s > 0 else pup_r, exm - 0.34, exm + 0.34, 14.00, 14.88, 32.00, 32.18, "pupil", rotation=[2, s * -10, s * -3])
        # Flush dual eyelids meeting smoothly in the middle with crisp dark lash seam!
        lc = lid_l if s > 0 else lid_r
        cbox(lc, s * 1.12, s * 2.96, 15.16, 15.90, 31.16, 32.19, "lid", rotation=[2, s * -10, s * -3])
        cbox(lc, s * 1.12, s * 2.96, 15.08, 15.20, 31.18, 32.20, "eye_lash", rotation=[2, s * -10, s * -3])
        cbox(ulid_l if s > 0 else ulid_r, s * 1.12, s * 2.96, 13.12, 13.78, 30.70, 32.19, "under_eye", rotation=[4, s * -10, s * -3])
        cbox(head_cubes, s * 0.96, s * 1.24, 12.90, 14.20, 31.4, 32.18, "tear_line", rotation=[14, s * -6, 0])
        # BIG oversized fuzzy rounded cub ears (classic irresistible cub silhouette!)
        ec = ear_l if s > 0 else ear_r
        ex, ey, er = s * 3.22, 15.6, [5, s * -10, s * -16]
        cbox(ec, ex - 1.32, ex + 1.32, ey, ey + 2.65, 27.3, 29.4, "ear_outer", rotation=er)
        cbox(ec, ex - 0.98, ex + 0.98, ey + 2.35, ey + 3.25, 27.5, 29.2, "ear_outer", rotation=er)
        cbox(ec, ex - 0.78, ex + 0.78, ey + 0.42, ey + 2.30, 28.85, 29.52, "ear_inner", rotation=er)

    # Shorter cute button snout & plump round cub whisker puffs
    csym(head_cubes, 1.32, 13.40, 14.75, 30.5, 34.15, "snout_bridge", rotation=[10.0, 0, 0], pivot=[0, 14.05, coz(32.3)])
    for s in (-1, 1):
        cbox(head_cubes, s * 0.92, s * 2.22, 11.70, 13.95, 30.6, 33.85, "muzzle", rotation=[5, s * -12, 0])
    csym(head_cubes, 1.92, 11.55, 14.00, 30.6, 33.75, "muzzle")
    # Cute compact cub nose (promoted to nose bone for sniffing twitches!)
    csym(nose_cubes, 1.26, 12.78, 13.32, 33.55, 34.42, "nose")
    csym(nose_cubes, 0.84, 12.42, 12.84, 33.60, 34.36, "nose")
    csym(nose_cubes, 0.44, 12.16, 12.48, 33.65, 34.32, "nose")
    csym(head_cubes, 0.18, 11.35, 12.22, 33.55, 34.22, "lip_cleft")
    for s in (-1, 1):
        cbox(head_cubes, s * 0.16, s * 2.24, 11.38, 12.78, 31.1, 34.18, "whisker_pad", rotation=[0, s * -11, 0])
        cbox(head_cubes, s * 0.16, s * 2.14, 11.18, 11.46, 30.9, 34.08, "lip_dark", rotation=[0, s * -11, 0])
        cbox(head_cubes, s * 2.15, s * 3.9, 12.26, 12.40, 32.8, 32.94, "whisker", rotation=[0, s * 16, s * -4])
        cbox(head_cubes, s * 2.10, s * 3.7, 11.82, 11.96, 32.5, 32.64, "whisker", rotation=[0, s * 20, s * -10])
    csym(jaw_cubes, 1.84, 10.42, 11.58, 28.8, 33.25, "jaw")
    csym(jaw_cubes, 1.48, 10.12, 11.30, 31.5, 33.80, "chin")
    csym(jaw_cubes, 0.98, 9.88, 10.38, 31.8, 33.55, "chin")
    tongue_tip_piv = feline_mouth(
        head_cubes, jaw_cubes, tongue_cubes, 0.0, 12.00, coz(29.3), coz(32.7), 2.15, 0.88,
        tongue_tip_cubes=tongue_tip_cubes, detailed=True,
    )

    # 4. Chunky soft bone-aligned limbs & BIG "grow-into-them" Splaying Hunter cub paws!
    leg_bones = []
    for side, tag in ((1, "fl"), (-1, "fr")):
        sx, ex, wx = side * 3.50, side * 3.40, side * 3.20
        sh, el, wr = [sx, 10.0, coz(23.6)], [ex, 5.9, coz(22.4)], [wx, 1.85, coz(23.6)]
        up, low = [], []
        aligned_seg(up, sh, el, -0.16, 0.48, 2.75, 3.25, "coat", z_off=0.08)
        aligned_seg(up, sh, el, 0.20, 1.06, 2.48, 2.85, "leg_upper")
        aligned_seg(low, el, wr, -0.08, 0.56, 2.45, 2.75, "leg_lower")
        aligned_seg(low, el, wr, 0.44, 1.06, 2.25, 2.50, "leg_lower")
        leg_bones.extend([
            {"name": f"leg_{tag}_1", "parent": "chest", "pivot": sh, "cubes": up},
            {"name": f"leg_{tag}_2", "parent": f"leg_{tag}_1", "pivot": el, "cubes": low},
            *splaying_hunter_paw(tag, f"leg_{tag}_2", wr, wx, 0.0, coz(24.2),
                                 width=3.50, length=4.00, height=2.15, lean_cub=True),
        ])

    for side, tag in ((1, "bl"), (-1, "br")):
        hx, kx, ax = side * 3.40, side * 3.40, side * 3.10
        hip, knee, hock, ankle = [hx, 9.9, coz(13.2)], [kx, 7.1, coz(15.2)], [ax, 4.1, coz(11.1)], [ax, 1.75, coz(12.5)]
        thigh, shin, meta = [], [], []
        aligned_seg(thigh, hip, knee, -0.14, 0.36, 2.55, 3.55, "coat", z_off=0.16)
        aligned_seg(thigh, hip, knee, 0.12, 0.78, 2.80, 3.90, "leg_upper", z_off=0.08)
        aligned_seg(thigh, hip, knee, 0.60, 1.10, 2.40, 2.80, "leg_upper", z_off=-0.10)
        aligned_seg(shin, knee, hock, -0.06, 0.46, 2.35, 2.95, "leg_lower", z_off=0.18)
        aligned_seg(shin, knee, hock, 0.36, 1.04, 1.95, 2.15, "leg_lower", z_off=-0.06)
        aligned_seg(shin, knee, hock, 0.88, 1.11, 2.00, 2.20, "leg_lower", z_off=0.08)
        aligned_seg(meta, hock, ankle, -0.08, 0.58, 1.95, 2.15, "leg_lower")
        aligned_seg(meta, hock, ankle, 0.42, 1.06, 1.90, 2.05, "leg_lower")
        leg_bones.extend([
            {"name": f"leg_{tag}_1", "parent": "hips", "pivot": hip, "cubes": thigh},
            {"name": f"leg_{tag}_2", "parent": f"leg_{tag}_1", "pivot": knee, "cubes": shin},
            {"name": f"leg_{tag}_3", "parent": f"leg_{tag}_2", "pivot": hock, "cubes": meta},
            *splaying_hunter_paw(tag, f"leg_{tag}_3", ankle, ax, 0.0, coz(13.1),
                                 width=3.20, length=3.70, height=2.05, lean_cub=True),
        ])

    # 5. Perky playful curled cub tail with soft dark tip
    tail_bones = []
    t_pts = [(10.2, 10.6), (8.0, 8.4), (5.8, 7.0), (3.6, 7.2), (1.8, 8.8), (0.6, 10.8)]
    t_w = [(1.02, 0.90), (0.90, 0.80), (0.80, 0.72), (0.72, 0.66), (0.66, 0.62)]
    prev = "hips"
    for i in range(5):
        p0, p1 = [0.0, t_pts[i][1], coz(t_pts[i][0])], [0.0, t_pts[i + 1][1], coz(t_pts[i + 1][0])]
        tc = []
        muscular_limb(tc, p0, p1, t_w[i][0] * 2.0, t_w[i][0] * 2.0, t_w[i][1] * 2.0, t_w[i][1] * 2.0, "tail", overlap=0.35)
        if i == 4:
            yb = t_pts[5][1]
            csym(tc, 0.86, yb - 0.86, yb + 0.86, 0.2, 1.8, "tail_tuft", rotation=[-25.0, 0.0, 0.0])
            csym(tc, 0.56, yb - 0.56, yb + 0.56, 0.0, 0.8, "tail_tuft")
        tname = f"tail_{i + 1}"
        tail_bones.append({"name": tname, "parent": prev, "pivot": p0, "cubes": tc})
        prev = tname

    return [
        {"name": "root", "pivot": [0.0, 0.0, 0.0], "cubes": []},
        {"name": "body", "parent": "root", "pivot": [0.0, 9.6, coz(19.0)], "cubes": []},
        {"name": "chest", "parent": "body", "pivot": [0.0, 9.8, coz(20.0)], "cubes": chest_cubes},
        {"name": "waist", "parent": "body", "pivot": [0.0, 9.7, coz(19.0)], "cubes": waist_cubes},
        {"name": "hips", "parent": "waist", "pivot": [0.0, 9.7, coz(15.2)], "cubes": hips_cubes},
        {"name": "neck", "parent": "chest", "pivot": [0.0, 11.4, coz(25.2)], "cubes": neck_cubes},
        {"name": "head", "parent": "neck", "pivot": [0.0, 13.4, coz(27.5)], "cubes": head_cubes},
        {"name": "jaw", "parent": "head", "pivot": [0.0, 11.35, coz(29.2)], "cubes": jaw_cubes},
        {"name": "tongue", "parent": "jaw", "pivot": [0.0, 11.15, coz(29.8)], "cubes": tongue_cubes},
        {"name": "tongue_tip", "parent": "tongue", "pivot": tongue_tip_piv, "cubes": tongue_tip_cubes},
        {"name": "carry", "parent": "head", "pivot": [0.0, 11.42, coz(31.8)], "cubes": []},
        {"name": "nose", "parent": "head", "pivot": [0.0, 13.05, coz(33.60)], "cubes": nose_cubes},
        {"name": "ear_left", "parent": "head", "pivot": [3.22, 15.8, coz(28.2)], "cubes": ear_l},
        {"name": "ear_right", "parent": "head", "pivot": [-3.22, 15.8, coz(28.2)], "cubes": ear_r},
        {"name": "brow_left", "parent": "head", "pivot": [2.05, 15.59, coz(30.88)], "cubes": brow_l},
        {"name": "brow_right", "parent": "head", "pivot": [-2.05, 15.59, coz(30.88)], "cubes": brow_r},
        {"name": "lid_left", "parent": "head", "pivot": [2.04, 15.90, coz(31.66)], "cubes": lid_l},
        {"name": "lid_right", "parent": "head", "pivot": [-2.04, 15.90, coz(31.66)], "cubes": lid_r},
        {"name": "ulid_left", "parent": "head", "pivot": [2.04, 13.12, coz(31.42)], "cubes": ulid_l},
        {"name": "ulid_right", "parent": "head", "pivot": [-2.04, 13.12, coz(31.42)], "cubes": ulid_r},
        {"name": "pupil_left", "parent": "head", "pivot": [2.04, 14.44, coz(32.12)], "cubes": pup_l},
        {"name": "pupil_right", "parent": "head", "pivot": [-2.04, 14.44, coz(32.12)], "cubes": pup_r},
        *tail_bones,
        *leg_bones,
    ]


def build_member(look_key="photo_king"):
    kind = LOOKS[look_key]["kind"]
    if kind == "male":
        return build_male_lion(look_key)
    if kind == "lioness":
        return build_lioness()
    return build_cub()


# ====================================================================================================
# PALETTES & PROCEDURAL PAINTERS (MALE 3 LOOKS + LIONESS + ROSETTE-SPOTTED CUB)
# ====================================================================================================
PALETTES = {
    "photo_king": {
        "coat_top": (210, 168, 108), "coat_mid": (190, 148, 92), "coat_shade": (154, 116, 68),
        "belly": (232, 208, 164), "face_gold": (216, 174, 112), "cheek_tawny": (176, 140, 96),
        "under_eye": (236, 218, 184), "muzzle": (226, 198, 156), "chin": (248, 244, 234),
        "mane_fire": (214, 122, 38), "mane_mid": (128, 64, 28), "mane_black": (28, 24, 26),
        "ear_back": (36, 26, 20), "nose": (32, 24, 24), "paw_pad": (42, 32, 28), "claw": (34, 28, 24), "tail_tuft": (30, 22, 18),
    },
    "savanna_monarch": {
        "coat_top": (220, 170, 96), "coat_mid": (202, 150, 80), "coat_shade": (164, 116, 58),
        "belly": (236, 210, 158), "face_gold": (224, 176, 104), "cheek_tawny": (190, 144, 84),
        "under_eye": (240, 220, 178), "muzzle": (232, 200, 148), "chin": (248, 242, 226),
        "mane_fire": (216, 142, 52), "mane_mid": (146, 74, 28), "mane_black": (66, 32, 18),
        "ear_back": (42, 26, 18), "nose": (40, 26, 24), "paw_pad": (44, 32, 26), "claw": (34, 26, 22), "tail_tuft": (48, 24, 14),
    },
    "kalahari_dark": {
        "coat_top": (204, 158, 96), "coat_mid": (184, 138, 82), "coat_shade": (148, 108, 60),
        "belly": (224, 200, 152), "face_gold": (208, 162, 100), "cheek_tawny": (166, 128, 84),
        "under_eye": (232, 212, 174), "muzzle": (220, 190, 144), "chin": (246, 240, 226),
        "mane_fire": (156, 86, 34), "mane_mid": (68, 38, 22), "mane_black": (22, 18, 20),
        "ear_back": (26, 18, 16), "nose": (30, 22, 22), "paw_pad": (38, 28, 24), "claw": (30, 24, 20), "tail_tuft": (24, 18, 16),
    },
    "lioness_tawny": {
        "coat_top": (216, 174, 114), "coat_mid": (196, 154, 98), "coat_shade": (158, 120, 72),
        "belly": (238, 216, 174), "face_gold": (220, 180, 118), "cheek_tawny": (182, 144, 96),
        "under_eye": (242, 226, 192), "muzzle": (232, 206, 164), "chin": (250, 246, 236),
        "mane_fire": (214, 122, 38), "mane_mid": (128, 64, 28), "mane_black": (28, 24, 26),
        "ear_back": (28, 22, 18), "nose": (72, 46, 46), "paw_pad": (42, 32, 28), "claw": (36, 28, 24), "tail_tuft": (34, 24, 18),
    },
    "lioness_kalahari": {
        "coat_top": (228, 200, 148), "coat_mid": (210, 180, 128), "coat_shade": (172, 144, 98),
        "belly": (246, 232, 198), "face_gold": (232, 204, 152), "cheek_tawny": (196, 168, 122),
        "under_eye": (248, 238, 212), "muzzle": (240, 222, 186), "chin": (252, 250, 242),
        "mane_fire": (214, 122, 38), "mane_mid": (128, 64, 28), "mane_black": (28, 24, 26),
        "ear_back": (26, 20, 18), "nose": (42, 32, 32), "paw_pad": (38, 30, 27), "claw": (34, 26, 22), "tail_tuft": (28, 20, 18),
    },
    "lioness_elder": {
        "coat_top": (204, 152, 86), "coat_mid": (184, 134, 72), "coat_shade": (146, 102, 52),
        "belly": (232, 204, 156), "face_gold": (208, 158, 92), "cheek_tawny": (168, 124, 72),
        "under_eye": (238, 218, 178), "muzzle": (224, 194, 148), "chin": (248, 242, 230),
        "mane_fire": (214, 122, 38), "mane_mid": (128, 64, 28), "mane_black": (28, 24, 26),
        "ear_back": (24, 18, 16), "nose": (30, 22, 24), "paw_pad": (36, 28, 25), "claw": (30, 24, 20), "tail_tuft": (24, 18, 16),
    },
    "cub_spotted": {
        "coat_top": (226, 192, 138), "coat_mid": (208, 172, 118), "coat_shade": (170, 134, 86),
        "spot_dark": (112, 72, 38),
        "belly": (246, 232, 198), "face_gold": (230, 194, 140), "cheek_tawny": (194, 158, 108),
        "under_eye": (248, 236, 210), "muzzle": (240, 218, 184), "chin": (253, 249, 242),
        "mane_fire": (214, 122, 38), "mane_mid": (128, 64, 28), "mane_black": (28, 24, 26),
        "ear_back": (44, 32, 24), "nose": (144, 86, 86), "paw_pad": (78, 54, 48), "claw": (224, 214, 202), "tail_tuft": (48, 34, 24),
    },
    "cub_subadult": {
        "coat_top": (218, 178, 118), "coat_mid": (198, 158, 102), "coat_shade": (160, 122, 74),
        "spot_dark": (136, 98, 58),
        "belly": (240, 220, 180), "face_gold": (222, 182, 122), "cheek_tawny": (184, 146, 98),
        "under_eye": (244, 228, 196), "muzzle": (234, 208, 168), "chin": (251, 247, 238),
        "mane_fire": (214, 122, 38), "mane_mid": (128, 64, 28), "mane_black": (28, 24, 26),
        "ear_back": (34, 24, 19), "nose": (96, 62, 62), "paw_pad": (56, 42, 38), "claw": (204, 186, 162), "tail_tuft": (38, 26, 20),
    },
}

EYE_STAMP = ["DDDDDD", "DGGGGD", "DGGHGD", "DGGGAD", "DDGGDD"]
EYE_PIERCING = ["DDDDDD", "DGGHGD", "DGGGGD", "DGGGGD", "DDGGDD"]
EYE_LIONESS = ["DDDDDD", "GGHHGG", "GGGGGG", "AGGGGA", "DGGGGD"]
EYE_CUB = ["DDDDDD", "GGHHGG", "GGGGGG", "GGGGGG", "AGGGGA", "DGGGGD"]
EYE_PAL = {"D": (24, 18, 14), "A": (204, 146, 44), "G": (246, 198, 72), "H": (255, 248, 196)}
EYE_CUB_PAL = {"D": (34, 24, 18), "A": (212, 152, 52), "G": (250, 200, 78), "H": (255, 254, 228)}


def cub_rosette_weight(x, y, z):
    """Authentic soft fading cub rosette & speckle pattern."""
    gx, gy, gz = x * 0.56, y * 0.58, z * 0.52
    ix, iy, iz = int(math.floor(gx)), int(math.floor(gy)), int(math.floor(gz))
    best = 9.0
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = ix + dx, iy + dy, iz + dz
                if hash3(cx, cy, cz + 71) < 0.52:
                    continue
                px = cx + 0.18 + 0.64 * hash3(cx, cy, cz + 13)
                py = cy + 0.18 + 0.64 * hash3(cx, cy, cz + 29)
                pz = cz + 0.18 + 0.64 * hash3(cx, cy, cz + 47)
                d = math.sqrt((gx - px) ** 2 + (gy - py) ** 2 + (gz - pz) ** 2)
                if d < best:
                    best = d
    if best < 0.48:
        return max(0.0, 1.0 - best / 0.48) * 0.88
    return 0.0


def make_painter(look_key="photo_king"):
    cfg = LOOKS[look_key]
    kind = cfg["kind"]
    pal = PALETTES[cfg["palette"]]
    nose_y_lo, nose_y_hi = (23.0, 23.85) if kind == "male" else ((20.5, 21.3) if kind == "lioness" else (12.4, 13.1))
    nose_x_lo, nose_x_hi = (0.40, 1.20) if kind == "male" else ((0.32, 1.00) if kind == "lioness" else (0.26, 0.80))
    body_y0, body_yh = (13.0, 12.0) if kind == "male" else ((11.5, 10.5) if kind == "lioness" else (6.5, 6.5))
    spine_y = 23.2 if kind == "male" else (20.6 if kind == "lioness" else 12.0)
    paw_y = 1.8 if kind == "male" else (1.5 if kind == "lioness" else 1.2)
    leg_pale_y = 6.5 if kind == "male" else (5.5 if kind == "lioness" else 3.6)

    def colour(c, face, x, y, z):
        part = c["_part"]
        fg = fur_grain(x, y, z, scale=1.25 if kind != "cub" else 1.6, stretch=3.6, flow=(0.0, -0.15, 1.0))
        if part in ("eye", "eye_piercing", "eye_lioness", "eye_cub"):
            (ox0, oy0, _), (sx, sy, _) = c["origin"], c["size"]
            if face == "north":
                u = max(0.0, min(0.999, (x - ox0) / max(0.01, sx)))
                v = max(0.0, min(0.999, 1.0 - (y - oy0) / max(0.01, sy)))
                if part == "eye_cub":
                    return stamp_2d(EYE_CUB, EYE_CUB_PAL, u, v, (242, 188, 68))
                if part == "eye_lioness":
                    return stamp_2d(EYE_LIONESS, EYE_PAL, u, v, (246, 198, 72))
                return stamp_2d(EYE_PIERCING if part == "eye_piercing" else EYE_STAMP, EYE_PAL, u, v, (246, 198, 72))
            return (36, 26, 20) if part == "eye_lioness" else (24, 18, 14)
        if part == "pupil":
            return (14, 11, 10)
        if part in ("eye_socket", "eye_lash", "lip_cleft", "lip_dark"):
            if part == "eye_socket" and kind == "cub":
                return (48, 34, 24)
            if part == "eye_socket" and kind == "lioness":
                return (38, 28, 22)
            if part == "eye_lash":
                return (34, 24, 20)
            return (28, 20, 18)
        if part == "tear_line":
            return shade(pal["cheek_tawny"], 0.50 if kind == "male" else (0.64 if kind == "lioness" else 0.66))
        if part == "under_eye":
            return shade(pal["under_eye"], 0.97 + 0.04 * fg)
        if part == "nose":
            if face == "north" and nose_x_lo < abs(x) < nose_x_hi and nose_y_lo < y < nose_y_hi:
                return (16, 11, 11)
            nb = pal["nose"]
            # Real lion noses develop dark melanin freckles with age (young lioness & subadult juvenile)!
            if look_key in ("lioness", "cub_subadult") and hash3(int(x * 5.0), int(y * 5.0), int(z * 5.0)) > 0.68:
                nb = mix(nb, (28, 20, 20), 0.58)
            return shade(nb, (1.18 if face == "up" else 0.95) * (0.96 + 0.04 * fg))
        if part == "whisker":
            return (248, 244, 236)
        if part == "tooth":
            return shade((250, 246, 234), 0.94 + 0.07 * max(0.0, min(1.0, (y - (body_y0 + 5.0)) / 3.0)))
        if part == "incisor":
            tooth_sep = 0.80 if abs(math.sin(x * 7.8)) < 0.16 else 1.0
            return shade((246, 242, 228), tooth_sep * (0.95 + 0.04 * fg))
        if part == "mouth_palate":
            ridge = 0.92 + 0.10 * math.sin(z * 5.2)
            return shade((144, 62, 70), (0.88 if face == "down" else 1.0) * ridge)
        if part == "mouth_throat":
            return shade((46, 14, 20), 0.92 + 0.08 * fg)
        if part == "mouth_gum":
            return shade((42, 24, 26), 0.94 + 0.06 * fg)
        if part == "mouth_inner":
            return shade((96, 32, 38), 0.85 if face == "down" else 1.0)
        if part == "tongue":
            groove = 0.85 if abs(x) < 0.24 else (0.95 if abs(x) < 0.42 else 1.04)
            pap = 0.95 + 0.08 * hash3(int(x * 7), int(y * 7), int(z * 7))
            return shade((226, 112, 122), groove * pap * (1.05 if face == "up" else 0.92))
        if part in ("paw_pad", "paw_pad_clover", "paw_pad_bean"):
            pebble = 0.88 + 0.22 * hash3(int(x * 7.5), int(y * 7.5), int(z * 7.5))
            rim_tgt = (118, 84, 78) if look_key == "cub" else ((88, 66, 60) if kind == "cub" else (72, 58, 52))
            pad_rim = mix(pal["paw_pad"], rim_tgt, 0.55)
            if face == "down":
                return shade(mix(pal["paw_pad"], pad_rim, 0.14), pebble * 0.96)
            return shade(mix(pal["paw_pad"], pad_rim, 0.36), pebble * 1.05)
        if part == "claw_ivory":
            t_seg = c.get("_claw_t", 0.5)
            oy0, sy = c["origin"][1], max(0.01, c["size"][1])
            u_local = max(0.0, min(1.0, (y - oy0) / sy))
            u = max(0.0, min(1.0, t_seg * 0.65 + u_local * 0.45))
            stria = 0.96 + 0.05 * math.sin(x * 16.0 + z * 11.0)
            if kind == "cub":
                # Soft translucent pearl-white milk claws for cubs!
                col = mix((226, 210, 198), (254, 252, 248), u)
            else:
                col = mix((186, 162, 128), (248, 242, 226), u)
            ao = 1.07 if face in ("north", "east", "west") else (0.86 if face == "down" else 1.0)
            return shade(col, stria * ao)
        if part == "claw":
            return shade(pal["claw"], 0.95 + 0.06 * fg)
        if part == "ear_outer":
            if face == "north":
                return shade(pal["face_gold"], 0.95 + 0.05 * fg)
            if face == "south":
                return shade(pal["ear_back"], 0.94 + 0.08 * fg)
            return shade(mix(pal["ear_back"], pal["coat_shade"], 0.36 if kind == "lioness" else 0.42), 0.95 + 0.06 * fg)
        if part == "ear_inner":
            return shade(mix(pal["muzzle"], pal["cheek_tawny"], 0.35 if kind == "cub" else 0.45), 0.88 + 0.06 * fg)
        if part == "tail_tuft":
            mg = fur_grain(x, y, z, scale=1.5, stretch=3.6, flow=(0.0, 0.1, 1.0))
            return shade(pal["tail_tuft"], 0.92 + 0.20 * (0.5 + 0.5 * mg))
        if part in ("mane_crown", "mane_mid", "mane_black"):
            mg = fur_grain(x, y, z, scale=1.6, stretch=4.6, flow=(0.0, -0.78, 0.62))
            layer = c.get("_layer", 0.92 if part == "mane_black" else (0.12 if part == "mane_crown" else 0.50))
            h_frac = max(0.0, min(1.0, (30.5 - y) / 18.5))
            back_dark = max(0.0, min(0.28, (z - oz(62.5)) / 24.0)) if look_key == "photo_king" else 0.0
            mt = max(0.0, min(1.0, layer * 0.54 + h_frac * 0.48 + back_dark))
            if part == "mane_black":
                mt = max(0.84, mt)
            base = mix(pal["mane_fire"], pal["mane_mid"], mt / 0.48) if mt < 0.48 else mix(pal["mane_mid"], pal["mane_black"], (mt - 0.48) / 0.52)
            lv = 0.86 + hash3(c.get("_lock", 0) * 11, c.get("_ring", 0) * 19, 5) * 0.28
            ao = 0.84 if face == "down" else (1.08 if face == "up" else 0.98)
            return shade(base, lv * ao * (0.90 + 0.18 * mg))
        if part == "forehead":
            blaze = 1.0 - min(1.0, abs(x) / 2.4)
            base = mix(pal["coat_mid"], pal["face_gold"], 0.45 + 0.55 * blaze)
            if kind == "cub":
                # Subtle classic cub forehead flecks!
                sp = cub_rosette_weight(abs(x) * 1.35, y * 1.35, z * 1.35)
                if sp > 0.22:
                    base = mix(base, pal["spot_dark"], sp * 0.55)
            return shade(base, 0.96 + 0.05 * fg)
        if part == "snout_bridge":
            return shade(mix(pal["face_gold"], pal["cheek_tawny"], min(1.0, abs(x) / 1.8) * 0.45), 0.96 + 0.05 * fg)
        if part == "brow":
            return shade(mix(pal["cheek_tawny"], pal["face_gold"], 0.78 if kind == "lioness" else 0.60), 0.96 if kind == "lioness" else 0.94 + 0.05 * fg)
        if part == "head_cheek":
            return shade(pal["cheek_tawny"], 0.95 + 0.05 * fg)
        if part in ("head", "lid"):
            return shade(pal["face_gold"], 0.96 + 0.05 * fg)
        if part == "whisker_pad":
            base = mix(pal["muzzle"], pal["chin"], 0.55)
            if hash3(int(abs(x) * 3.8), int(y * 4.2), int(z * 3.8)) > 0.87 and abs(x) > 0.8:
                return shade(pal["cheek_tawny"], 0.78)
            return shade(base, 0.97 + 0.04 * fg)
        if part == "chin":
            return shade(pal["chin"], 0.97 + 0.04 * fg)
        if part == "jaw":
            return shade(mix(pal["muzzle"], pal["chin"], 0.65), 0.94 + 0.04 * fg)
        if part == "muzzle":
            return shade(pal["muzzle"], 0.95 + 0.05 * fg)
        if part == "belly":
            return shade(pal["belly"], (0.92 if face == "down" else 0.99) * (0.96 + 0.04 * fg))
        if part in ("paw", "paw_fur", "toe_fur"):
            base = mix(pal["coat_mid"], pal["belly"], 0.50 if part == "toe_fur" else (0.40 if y < paw_y else 0.18))
            if face == "down":
                base = shade(base, 0.88)
            return shade(base, 0.96 + 0.05 * fg)
        if part in ("leg_upper", "leg_lower"):
            lg = fur_grain(x, y, z, scale=1.35, stretch=3.6, flow=(0.0, -1.0, 0.0))
            inn = 0.24 if ((x > 0 and face == "west") or (x < 0 and face == "east")) else 0.0
            base = mix(pal["coat_mid"], pal["belly"], inn)
            if y < leg_pale_y:
                base = mix(base, pal["muzzle"], 0.26)
            if kind == "cub" and part == "leg_upper" and inn == 0.0:
                sp = cub_rosette_weight(x * 1.15, y * 1.15, z * 1.15)
                if sp > 0.15:
                    base = mix(base, pal["spot_dark"], sp * (0.36 if look_key == "cub_subadult" else 0.66))
            return shade(base, 0.96 + 0.06 * lg)
        h_norm = max(0.0, min(1.0, (y - body_y0) / body_yh))
        base = mix(pal["coat_shade"], pal["coat_top"], h_norm)
        if kind == "cub":
            # Authentic cub rosettes: bold on young 2-5mo cub, fading into faint flank ghost spots on 6-12mo juvenile!
            sp = cub_rosette_weight(x, y, z)
            if look_key == "cub_subadult":
                if sp > 0.18 and y < 10.6:
                    base = mix(base, pal["spot_dark"], min(0.42, sp * 0.40))
            elif sp > 0.08:
                base = mix(base, pal["spot_dark"], min(0.84, sp * 0.82))
        elif kind == "lioness" and y < 15.2 and face in ("east", "west"):
            # Ultra-subtle vestigial tawny flank dappling on real young adult lionesses
            sp = cub_rosette_weight(x * 0.6, y * 0.6, z * 0.6)
            if sp > 0.35:
                base = mix(base, pal["coat_shade"], sp * 0.24)
        if abs(x) < 1.3 and y > spine_y:
            base = mix(base, pal["coat_shade"], 0.14)
        ao = 0.86 if face == "down" else (1.03 if face == "up" else 0.98)
        return shade(base, ao * (0.96 + (0.04 if face == "up" else 0.07) * fg))

    return colour


def audit_member(look_key, raw_bones):
    all_pts, mouth_pts, head_jaw_pts = [], [], []
    n_boxes = n_slanted = 0
    for b in raw_bones:
        bname = b["name"]
        for c in b.get("cubes", []):
            n_boxes += 1
            if c.get("rotation") and any(abs(v) > 1e-6 for v in c["rotation"]):
                n_slanted += 1
            (ox0, oy0, oz0), (sx, sy, sz) = c["origin"], c["size"]
            corners = [world_point(c, ox0 + (k >> 2 & 1) * sx, oy0 + (k >> 1 & 1) * sy, oz0 + (k & 1) * sz) for k in range(8)]
            all_pts.extend(corners)
            if c.get("_part") in ("tooth", "incisor", "mouth_inner", "mouth_palate", "mouth_throat", "mouth_gum", "tongue"):
                mouth_pts.extend(corners)
            elif bname in ("head", "jaw") and c.get("_part") in ("muzzle", "whisker_pad", "jaw", "chin", "head"):
                head_jaw_pts.extend(corners)
    xs, ys, zs = [p[0] for p in all_pts], [p[1] for p in all_pts], [p[2] for p in all_pts]
    hx0, hx1 = min(p[0] for p in head_jaw_pts), max(p[0] for p in head_jaw_pts)
    hy0, hy1 = min(p[1] for p in head_jaw_pts), max(p[1] for p in head_jaw_pts)
    hz0, hz1 = min(p[2] for p in head_jaw_pts), max(p[2] for p in head_jaw_pts)
    mouth_poke = max((max(hx0 - mx, mx - hx1, hy0 - my, my - hy1, hz0 - mz, mz - hz1) for mx, my, mz in mouth_pts), default=0.0)
    return {
        "look_key": look_key,
        "bones": len(raw_bones),
        "boxes": n_boxes,
        "slanted": n_slanted,
        "W": round((max(xs) - min(xs)) / 16.0, 2),
        "L": round((max(zs) - min(zs)) / 16.0, 2),
        "H": round((max(ys) - min(ys)) / 16.0, 2),
        "minY": round(min(ys), 2),
        "mouth_poke": round(mouth_poke, 2),
    }


def export_pride():
    out_dir = Path(__file__).resolve().parent.parent / "references" / "workshop" / "lion" / "pride"
    out_dir.mkdir(parents=True, exist_ok=True)
    for lk in LOOKS:
        raw = build_member(lk)
        rep = audit_member(lk, raw)
        bones, rows, tw, th = paint_faces(raw, TEXELS, make_painter(lk), world_coords=True)
        vis = (5, 3, [0, 1.2, 0]) if LOOKS[lk]["kind"] != "cub" else (3, 2, [0, 0.7, 0])
        (out_dir / f"{lk}.geo.json").write_text(json.dumps(geo(f"lion_{lk}", bones, tw, th, vis), indent=1), encoding="utf-8")
        (out_dir / f"{lk}.png").write_bytes(png(rows))
        budget = 250 if LOOKS[lk]["kind"] == "cub" else 400
        print(f"  {lk:12} | bones={rep['bones']:2d} | boxes={rep['boxes']:3d}/{budget} (slanted={rep['slanted']:2d}) | "
              f"L={rep['L']:.2f}b H={rep['H']:.2f}b W={rep['W']:.2f}b minY={rep['minY']:.2f} mouth_poke={rep['mouth_poke']:.2f}")


WORKSHOP_LOOK = "photo_king"
WORKSHOP_PARTS = {
    "head": ["head", "jaw", "tongue", "tongue_tip", "nose", "ear_left", "ear_right", "brow_left", "brow_right", "lid_left", "lid_right", "ulid_left", "ulid_right", "pupil_left", "pupil_right"],
    "mane": ["mane_crown", "mane_cheeks", "mane_shoulders", "mane_chest"],
    "body": ["chest", "waist", "hips", "neck"],
    "legs": [
        "leg_fl_1", "leg_fl_2", "leg_fl_paw", "leg_fr_1", "leg_fr_2", "leg_fr_paw",
        "leg_bl_1", "leg_bl_2", "leg_bl_3", "leg_bl_paw", "leg_br_1", "leg_br_2", "leg_br_3", "leg_br_paw",
        *(f"{k}_{tag}_{i}" for tag in ("fl", "fr", "bl", "br") for i in range(4) for k in ("toe", "claw")),
    ],
    "tail": ["tail_1", "tail_2", "tail_3", "tail_4", "tail_5"],
}


def workshop_look(look=None):
    """Entry point for tools/workshop.py snapshot/versions (returns bones, png_bytes, w, h)."""
    lk = look or WORKSHOP_LOOK
    raw = build_member(lk)
    bones, rows, tw, th = paint_faces(raw, TEXELS, make_painter(lk), world_coords=True)
    return bones, png(rows), tw, th


def _build_body_plan(model_name, raw_bones, meta, kind="male", verify_models=None, clip_names=None):
    bnames = [b["name"] for b in raw_bones]
    is_cub = kind == "cub"
    is_lns = kind == "lioness"
    have = set(clip_names) if clip_names is not None else None
    desc = ("African Lion (Panthera leo) male body plan: 4.66 x 2.03 blocks at scale 1.0 (16 px = 1 block)." if kind == "male"
            else ("African Lioness body plan: 4.09 x 1.75 blocks at scale 1.0." if is_lns
                  else "African Lion Cub body plan: 2.16 x 1.20 blocks at scale 1.0."))
    all_switches = [
        ["idle", "walk"], ["walk", "idle"],
        ["walk", "trot"], ["trot", "walk"],
        ["trot", "gallop"], ["gallop", "trot"],
        ["walk", "stalk"], ["stalk", "walk"],
        ["stalk", "ambush_freeze"], ["ambush_freeze", "stalk"],
        ["ambush_freeze", "stalk_leap_bite"], ["ambush_freeze", "idle"],
        ["stalk", "pounce"], ["pounce", "idle"],
        ["stalk", "play_pounce"], ["play_pounce", "idle"],
        ["stalk", "stalk_leap_bite"], ["stalk_leap_bite", "idle"],
        ["idle", "roar"], ["roar", "idle"],
        ["idle", "cub_call"], ["cub_call", "idle"],
        ["idle", "bite"], ["bite", "idle"],
        ["gallop", "bite"], ["gallop", "gallop_swat"], ["gallop_swat", "gallop"],
        ["idle", "swipe"], ["swipe", "idle"],
        ["idle", "swipe_heavy"], ["swipe_heavy", "idle"],
        ["idle", "swipe_quick"], ["swipe_quick", "idle"],
        ["idle", "swipe_combo"], ["swipe_combo", "idle"],
        ["idle", "jump_smack"], ["jump_smack", "idle"],
        ["pounce", "takedown_pin"], ["takedown_pin", "idle"],
        ["idle", "bluff_charge"], ["bluff_charge", "idle"],
        ["idle", "pride_greet"], ["pride_greet", "idle"],
        ["idle", "cub_groom"], ["cub_groom", "idle"],
        ["idle", "play_wrestle"], ["play_wrestle", "idle"],
        ["idle", "mama_nuzzle"], ["mama_nuzzle", "idle"],
        ["idle", "mood_content"], ["mood_content", "idle"],
        ["idle", "mood_alert"], ["mood_alert", "idle"],
        ["idle", "mood_curious"], ["mood_curious", "idle"],
        ["idle", "mood_angry"], ["mood_angry", "idle"],
        ["idle", "mood_fearful"], ["mood_fearful", "idle"],
        ["idle", "mood_submissive"], ["mood_submissive", "idle"],
        ["idle", "mood_sleepy"], ["mood_sleepy", "idle"],
        ["idle", "mood_flehmen"], ["mood_flehmen", "idle"],
        ["idle", "sit_down"], ["sit_down", "sit"], ["sit", "sit_up"], ["sit_up", "idle"],
        ["idle", "rest_down"], ["rest_down", "rest"], ["rest", "rest_up"], ["rest_up", "idle"],
        ["rest", "groom"], ["groom", "rest"],
        ["rest", "rest_stretch_up"], ["rest_stretch_up", "idle"],
        ["idle", "drink"], ["drink", "idle"],
        ["idle", "death"],
    ]
    switches = [s for s in all_switches if have is None or (s[0] in have and s[1] in have)]
    all_feet_free = [
        "walk", "stalk", "ambush_freeze", "trot", "gallop", "pounce", "play_pounce", "play_wrestle",
        "swipe", "swipe_heavy", "swipe_quick", "swipe_combo", "jump_smack", "stalk_leap_bite",
        "gallop_swat", "takedown_pin", "bluff_charge", "rest_stretch_up", "pride_greet",
        "groom", "cub_groom", "cub_call", "mama_nuzzle",
        "mood_content", "mood_alert", "mood_curious", "mood_angry", "mood_fearful",
        "mood_submissive", "mood_sleepy", "mood_flehmen",
        "sit_down", "sit", "sit_up", "rest_down", "rest", "rest_up", "death",
        "step_up_1000", "step_down_1000",
    ]
    all_tuck_ok = [
        "sit_down", "sit", "sit_up", "rest_down", "rest", "rest_up", "rest_stretch_up", "death",
        "pounce", "play_pounce", "play_wrestle", "gallop", "stalk", "ambush_freeze",
        "swipe", "swipe_heavy", "swipe_quick", "swipe_combo", "jump_smack", "stalk_leap_bite",
        "gallop_swat", "takedown_pin", "bluff_charge", "pride_greet", "groom", "cub_groom",
        "cub_call", "mama_nuzzle",
        "mood_content", "mood_alert", "mood_curious", "mood_angry", "mood_fearful",
        "mood_submissive", "mood_sleepy", "mood_flehmen",
    ]
    feet_free = [c for c in all_feet_free if have is None or c in have]
    tuck_ok = [c for c in all_tuck_ok if have is None or c in have]
    plan = {
        "model": model_name,
        "about": desc,
        "family": "upright_four_legs",
        "root": "root",
        "forebody": "chest",
        "head": {
            "bone": "head",
            "jaws": ["jaw"],
            "sockets": {"carry": "carry"},
        },
        "chain": ["head", "neck", "chest", "waist", "hips", "tail_1", "tail_2", "tail_3", "tail_4", "tail_5"],
        "legs": {
            "l1": ["leg_fl_1", "leg_fl_2", "leg_fl_paw"],
            "r1": ["leg_fr_1", "leg_fr_2", "leg_fr_paw"],
            "l2": ["leg_bl_1", "leg_bl_2", "leg_bl_3", "leg_bl_paw"],
            "r2": ["leg_br_1", "leg_br_2", "leg_br_3", "leg_br_paw"],
        },
        "bones": {n: n for n in bnames},
        "checks": {
            "px": 0.0625,
            "blend": 4,
            "feet_parts": ["paw"],
            "feet_free": feet_free,
            "tuck_ok": tuck_ok,
            "body_bones": ["chest", "waist", "hips"],
            "carry": {
                "bone": "carry",
                "jaw": "jaw",
                "grip_ahead": 3.9 if kind == "male" else (3.7 if is_lns else 2.6),
                "grip_reach": 0.55,
                "slant": 18.0,
                "jaw_fill": 0.72,
                "jaw_min": 14.0,
                "jaw_max": 48.0,
                "jaw_other": 22.0,
                "swallow_under": 0.55,
                "swallow_chance": 0.0,
            },
            "jaw": {"bone": "jaw", "flag_below": -3.0},
            "walks": meta["walks"],
            "switches": switches,
        },
    }
    if verify_models:
        plan["verify"] = {"models": list(verify_models)}
    return plan


def export_game():
    """Export all approved Lion Pride geos (with game_faces=True), textures, animations, BlockMoves, and body plans."""
    import lion_anims

    assets_out = {}

    # 1. Male Lion (photo_king default + monarch shared-geo texture + kalahari heavier patriarch geo/texture)
    raw_male = build_member("photo_king")
    b_male, r_male, tw, th = paint_faces(raw_male, TEXELS, make_painter("photo_king"), world_coords=True)
    geo_male = geo("lion", b_male, tw, th, (5, 3, [0, 1.2, 0]))

    raw_mon = build_member("monarch")
    b_mon, r_mon, tw_m, th_m = paint_faces(raw_mon, TEXELS, make_painter("monarch"), world_coords=True)
    assert (tw, th) == (tw_m, th_m), "monarch texture dimensions must match photo_king for shared lion.geo.json!"

    raw_kal = build_member("kalahari")
    b_kal, r_kal, tw_k, th_k = paint_faces(raw_kal, TEXELS, make_painter("kalahari"), world_coords=True)
    geo_kal = geo("lion_kalahari", b_kal, tw_k, th_k, (5, 3, [0, 1.2, 0]))

    clips_male, meta_male = lion_anims.build_all_clips(raw_male, kind="male")
    assets_out["geo/entity/lion.geo.json"] = geo_male
    assets_out["textures/entity/lion.png"] = png(r_male)
    assets_out["textures/entity/lion_monarch.png"] = png(r_mon)
    assets_out["geo/entity/lion_kalahari.geo.json"] = geo_kal
    assets_out["textures/entity/lion_kalahari.png"] = png(r_kal)
    assets_out["animations/entity/lion.animation.json"] = animations(clips_male)
    assets_out["body_plans/lion.json"] = _build_body_plan(
        "lion", raw_male, meta_male, kind="male", verify_models=["lion_kalahari"],
        clip_names=list(clips_male.keys()),
    )

    # 2. Lioness (default Serengeti tawny + shared-geo Kalahari Pale Sand & Prime Matriarch Elder textures!)
    raw_lns = build_member("lioness")
    b_lns, r_lns, tw_l, th_l = paint_faces(raw_lns, TEXELS, make_painter("lioness"), world_coords=True)
    _, r_lns_k, tw_lk, th_lk = paint_faces(build_member("lioness_kalahari"), TEXELS, make_painter("lioness_kalahari"), world_coords=True)
    _, r_lns_e, tw_le, th_le = paint_faces(build_member("lioness_elder"), TEXELS, make_painter("lioness_elder"), world_coords=True)
    assert (tw_l, th_l) == (tw_lk, th_lk) == (tw_le, th_le), "lioness texture variants must match lioness.geo.json!"
    clips_lns, meta_lns = lion_anims.build_all_clips(raw_lns, kind="lioness")
    assets_out["geo/entity/lioness.geo.json"] = geo("lioness", b_lns, tw_l, th_l, (4.5, 2.5, [0, 1.0, 0]))
    assets_out["textures/entity/lioness.png"] = png(r_lns)
    assets_out["textures/entity/lioness_kalahari.png"] = png(r_lns_k)
    assets_out["textures/entity/lioness_elder.png"] = png(r_lns_e)
    assets_out["animations/entity/lioness.animation.json"] = animations(clips_lns)
    assets_out["body_plans/lioness.json"] = _build_body_plan(
        "lioness", raw_lns, meta_lns, kind="lioness", clip_names=list(clips_lns.keys()),
    )

    # 3. Lion Cub (default 2-5mo spotted cub + shared-geo 6-12mo fading juvenile texture!)
    raw_cub = build_member("cub")
    b_cub, r_cub, tw_c, th_c = paint_faces(raw_cub, TEXELS, make_painter("cub"), world_coords=True)
    _, r_cub_s, tw_cs, th_cs = paint_faces(build_member("cub_subadult"), TEXELS, make_painter("cub_subadult"), world_coords=True)
    assert (tw_c, th_c) == (tw_cs, th_cs), "cub_subadult texture must match lion_cub.geo.json!"
    clips_cub, meta_cub = lion_anims.build_all_clips(raw_cub, kind="cub")
    assets_out["geo/entity/lion_cub.geo.json"] = geo("lion_cub", b_cub, tw_c, th_c, (3, 2, [0, 0.7, 0]))
    assets_out["textures/entity/lion_cub.png"] = png(r_cub)
    assets_out["textures/entity/lion_cub_subadult.png"] = png(r_cub_s)
    assets_out["animations/entity/lion_cub.animation.json"] = animations(clips_cub)
    assets_out["body_plans/lion_cub.json"] = _build_body_plan(
        "lion_cub", raw_cub, meta_cub, kind="cub", clip_names=list(clips_cub.keys()),
    )

    write_assets(assets_out, mod="savanna", faces=True)

    # 4. BlockMoves files (step_up_1000 & step_down_1000) for all 3 Pride body plans
    for stem, clips_d, meta_d in (("lion", clips_male, meta_male),
                                  ("lioness", clips_lns, meta_lns),
                                  ("lion_cub", clips_cub, meta_cub)):
        bm = block_moves(
            clips_d,
            sizes=[1.0],
            block_px=lambda s: 16.0 / s,
            moves=[
                {"name": "step_up", "clip": lambda s: "step_up_1000", "ahead_px": meta_d["step_up_ahead"],
                 "starts_afloat": False, "ends_afloat": False, "goes_down": False, "end_level": 1},
                {"name": "step_down", "clip": lambda s: "step_down_1000", "ahead_px": meta_d["step_down_ahead"],
                 "starts_afloat": False, "ends_afloat": False, "goes_down": True, "end_level": -1},
            ],
            lead=4,
        )
        write_block_moves(stem, bm, "tools/lion_models.py", mod="savanna")

    print(f"Exported full African Lion Pride game assets (lion={len(clips_male)}, lioness={len(clips_lns)}, cub={len(clips_cub)} clips).")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "game":
        export_game()
    else:
        print("African Lion Pride Audit (Stalker King Male x3 + Lioness + Cub):")
        export_pride()
