"""African Buffalo (Syncerus caffer) model maker — Stage 3 Shape Detail (v6, 2026-11-24).

Built directly on the user-approved Stage 2 Plain 3D Shape (v5):
- Preserves exact approved Savanna Ecosystem scale (3.83 x 2.27 x 2.29 blocks, 16 px = 1 block),
  Compare silhouette (Side ~91/90%, Front ~92/94%), seamless central helmet boss, and smooth 14-segment mitered horns.
- Stage 3 Shape Details added:
  1. Facial expression rig: articulated heavy brow shelves (brow_left/right), flush dual eyelids (lid_*/ulid_*),
     recessed nostrils with outer alae rims & philtrum groove, subtle upper lip bevel & chin-beard tuft.
  2. Sealed 3D bovine mouth interior (100% hidden when jaw=0 deg, revealed when grazing/bellowing):
     dark throat cavity, vaulted ridged palate, upper bovine dental pad, chunky ivory lower incisors,
     rear grinding cheek molars, and an articulated 2-bone muscular tongue (tongue + tongue_tip).
  3. Corrugated Cape Buffalo horn annuli (subtle stepped transverse growth rings on inner/mid horn curve)
     and sculpted boss crown ridge framing the central forehead cleft.
  4. Cupped leaf ear rim & inner-fold depth.
  5. Torso muscular planes: terraced withers neck-ruff crest, sculpted scapula/shoulder plates, deep brisket dewlap
     fold, pelvic hip crests, flank-to-thigh groin bridge, and ventral belly midline contour.
  6. Limbs, hooves & tail: knee caps, angled Achilles hock tendons, coronary hoof-rim bands, paired rounded rear
     dewclaws (2 per hoof), and a layered coarse diamond tail switch (strictly minY = 0.000 px).

    python tools/buffalo_models.py
    python tools/workshop.py snapshot buffalo --note "..."
    python tools/workshop.py versions buffalo
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (  # noqa: E402
    cube, rot_cube, mix, paint_faces, png, geo, animations, write_assets,
    block_moves, write_block_moves,
    from_axes, unit, cross, vsub, vlen, rot, world_point,
)

# --- Workshop sliders (main shape numbers for tools/workshop.py serve buffalo) ----------------------------------------
LENGTH = 60.8          # 3.80 blocks (nose tip to tail drop)
HEIGHT = 36.2          # 2.26 blocks at peak of shoulder withers hump
BARREL_HW = 9.7        # half-width of deep ribcage/shoulders (19.4 px = 1.21 b)
HUMP_BOOST = 0.0       # extra vertical lift on shoulder withers hump (px)
HORN_SPAN = 16.7       # outer horn curve half-span (px; ~35.6 px total = ~0.99 H)
HORN_DIP = 0.0         # extra downward dip on sweeping handlebar elbow (px)
MUZZLE_LEN = 5.6       # forward muzzle projection ahead of skull (px)
LEG_H = 13.4           # belly clearance height (px)
JAW_OPEN = 0.0         # preview-only jaw opening degrees (0 shut, 38 bellow)

WORKSHOP_SHAPE = [
    ("HEIGHT",     "Shoulder hump height (px)", 32.0, 40.0, 0.5),
    ("BARREL_HW",  "Barrel half-width (px)",     8.0, 12.0, 0.25),
    ("HUMP_BOOST", "Extra hump height (px)",    -2.0,  3.0, 0.5),
    ("HORN_SPAN",  "Horn half-span (px)",       13.5, 19.5, 0.5),
    ("HORN_DIP",   "Horn handlebar dip (px)",   -2.0,  2.5, 0.25),
    ("MUZZLE_LEN", "Muzzle length (px)",         4.5,  7.5, 0.25),
    ("LEG_H",      "Belly clearance (px)",      11.5, 15.5, 0.25),
    ("JAW_OPEN",   "Preview jaw open (deg)",     0.0, 45.0, 2.0),
]

WORKSHOP_PARTS = {
    "head":       ["head", "brow_left", "brow_right", "ear_left", "ear_right",
                   "eye_left", "eye_right", "lid_left", "lid_right", "ulid_left", "ulid_right",
                   "pupil_left", "pupil_right"],
    "jaw":        ["jaw", "tongue", "tongue_tip"],
    "neck":       ["neck"],
    "chest":      ["chest"],
    "hips":       ["hips"],
    "tail":       ["tail_1", "tail_2", "tail_3"],
    "front legs": ["leg_fl_1", "leg_fr_1"],
    "hind legs":  ["leg_bl_1", "leg_br_1"],
}

WORKSHOP_LOOK = "var_b"   # Stage 4 Minecraft-first paint ("var_a", "var_b", "var_c", "plain", "clay")
EYE_VARIANT = "b"         # Approved by user 2026-11-24: "b" Angled 32-deg Bovine Socket
TEXELS = 2.0              # 2 texels per model px -> crisp 1-px Minecraft fur mottling + sharp 0.5-px face/horn details!


def oz(ox):
    """Outline x (0 = tail drop .. LENGTH = snout tip) -> GeckoLib model z (+LENGTH/2 .. -LENGTH/2)."""
    return LENGTH * 0.5 - ox


def box(cubes, x0, x1, y0, y1, ox0, ox1, part, rotation=None, pivot=None, **extra):
    z0, z1 = oz(ox1), oz(ox0)
    lo = [min(x0, x1), min(y0, y1), min(z0, z1)]
    hi = [max(x0, x1), max(y0, y1), max(z0, z1)]
    if hi[0] - lo[0] < 0.04 or hi[1] - lo[1] < 0.04 or hi[2] - lo[2] < 0.04:
        return None
    c = rot_cube(lo, hi, part, rotation=rotation, pivot=pivot, **extra) if rotation else cube(lo, hi, part)
    if not rotation and extra:
        c.update(extra)
    cubes.append(c)
    return c


def sym(cubes, hw, y0, y1, ox0, ox1, part, rotation=None, pivot=None, **extra):
    return box(cubes, -hw, hw, y0, y1, ox0, ox1, part, rotation=rotation, pivot=pivot, **extra)


def _scale_y(y):
    """Maps canonical 36.2-px-tall profile heights onto current HEIGHT while honoring LEG_H belly clearance."""
    base_belly = 13.4
    if y <= base_belly:
        return y * (LEG_H / base_belly)
    return LEG_H + (y - base_belly) * ((HEIGHT - LEG_H) / (36.2 - base_belly))


def _barrel_slice(cubes, ox0, ox1, y_bot, y_top, hw, belly_hw=None, top_part="back_crest",
                  body_part="body", belly_part="underbelly", ridge=True):
    """Chunky stepped Minecraft bovine barrel slice (4 clean vertical tiers)."""
    yb, yt = _scale_y(y_bot), _scale_y(y_top)
    h = yt - yb
    if h < 2.2:
        return
    t1 = yb + max(2.0, h * 0.14)
    bhw = round(hw * 0.78, 2) if belly_hw is None else round(belly_hw, 2)
    sym(cubes, bhw, yb, t1 + 0.35, ox0, ox1, belly_part)
    t2 = yb + h * 0.74
    sym(cubes, round(hw, 2), t1 - 0.35, t2, ox0, ox1, body_part)
    t3 = yb + h * 0.89 if ridge else yt
    sym(cubes, round(hw * 0.83, 2), t2 - 0.4, t3, ox0, ox1, body_part)
    if ridge and yt - t3 >= 0.8:
        sym(cubes, round(hw * 0.56, 2), t3 - 0.35, yt, ox0, ox1, top_part)


def _catmull(pts, t):
    """Smooth Catmull-Rom interpolation across a polyline of 3D points + cross-section tuples."""
    n = len(pts) - 1
    u = max(0.0, min(1.0, t)) * n
    i = min(n - 1, int(u))
    f = u - i
    p0 = pts[max(0, i - 1)]
    p1 = pts[i]
    p2 = pts[min(n, i + 1)]
    p3 = pts[min(n, i + 2)]
    f2, f3 = f * f, f * f * f
    out = []
    for k in range(len(p1)):
        v = 0.5 * ((2 * p1[k]) +
                   (-p0[k] + p2[k]) * f +
                   (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * f2 +
                   (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * f3)
        out.append(v)
    return out


def _aligned_limb_box(cubes, p0, p1, w, d, part, pad=0.45):
    """Creates a rotated box aligned strictly along segment p0 -> p1."""
    vec = vsub(p1, p0)
    length = vlen(vec)
    if length < 0.08:
        return
    ly = unit(vsub(p0, p1))
    lz = unit(cross([1.0, 0.0, 0.0], ly))
    lx = unit(cross(ly, lz))
    R = from_axes(lx, ly, lz)
    mid = [(p0[k] + p1[k]) * 0.5 for k in range(3)]
    hl = length * 0.5 + pad
    cubes.append(rot_cube(
        [mid[0] - w * 0.5, mid[1] - hl, mid[2] - d * 0.5],
        [mid[0] + w * 0.5, mid[1] + hl, mid[2] + d * 0.5],
        part, rotation=rot(R), pivot=mid,
    ))


def _build_horns(head_cubes, hy):
    """Approved v5 seamless helmet boss + smooth 14-segment mitered sweeping handlebar horns,
    enhanced in Stage 3 with sculpted boss crown ridge texture and subtle corrugated bovine growth rings (annuli)!"""
    span_s = HORN_SPAN / 16.7
    dip = HORN_DIP

    # 1. Clean double-lobed central helmet boss (zero dark brow clip!) + Stage 3 sculpted bossing ridges
    sym(head_cubes, 0.55, hy(29.55), hy(33.55), 49.4, 55.1, "horn_boss_crease")
    for s in (1, -1):
        # Main inner boss lobe sitting flush atop the dark brow at y = 29.55
        box(head_cubes, s * 0.38, s * 5.05, hy(29.55), hy(34.15), 49.2, 55.4, "horn_boss")
        # Gentle forward rounded boss brow-roll
        box(head_cubes, s * 0.48, s * 4.85, hy(29.80), hy(33.55), 54.8, 55.90, "horn_boss")
        # Soft domed crown step on top of each lobe
        box(head_cubes, s * 0.65, s * 4.70, hy(33.85), hy(34.65), 49.8, 55.0, "horn_boss_top")
        # Stage 3 detail: clean organic brow-roll bevel across the front & back of each boss dome (no stud step!)
        box(head_cubes, s * 0.55, s * 4.55, hy(30.20), hy(33.25), 55.55, 56.02, "horn_boss_ridge")
        box(head_cubes, s * 0.70, s * 4.45, hy(34.45), hy(34.82), 51.0, 53.6, "horn_boss_top")

    # 2. Smooth 14-segment mitered sweeping handlebar horns + corrugated bovine growth rings (annuli) on segments 1..7
    ctrl = [
        ( 3.60,          31.95, 52.3, 4.70, 5.80),
        ( 5.60 * span_s, 31.35, 51.4, 4.55, 5.40),
        ( 8.10 * span_s, 29.20, 49.9, 4.25, 4.75),
        (10.80 * span_s, 26.65 - dip, 48.3, 3.85, 4.15),
        (13.80 * span_s, 25.55 - dip, 47.0, 3.45, 3.65),
        (16.10 * span_s, 27.10 - dip * 0.5, 46.4, 3.05, 3.18),
        (16.85 * span_s, 29.85 - dip * 0.2, 46.2, 2.68, 2.78),
        (16.10 * span_s, 32.55, 46.5, 2.26, 2.34),
        (14.20 * span_s, 34.60, 47.1, 1.78, 1.84),
        (11.60 * span_s, 35.65, 47.9, 1.12, 1.16),
    ]

    segs = 14
    for s in (1, -1):
        pts = []
        for i in range(segs + 1):
            t = i / segs
            x, yc, ox, wy, dz = _catmull(ctrl, t)
            pts.append(([s * x, hy(yc), oz(ox)], wy, dz, t))

        tangents = []
        for i in range(segs):
            v = vsub(pts[i + 1][0], pts[i][0])
            tangents.append(unit(v) if vlen(v) > 1e-4 else [s, 0.0, 0.0])

        for i in range(segs):
            p0, wy0, dz0, t0 = pts[i]
            p1, wy1, dz1, t1 = pts[i + 1]
            vec = vsub(p1, p0)
            seg_len = vlen(vec)
            if seg_len < 0.05:
                continue
            lx = tangents[i]
            ref_z = [0.0, 0.0, -1.0]
            ly = unit(cross(ref_z, lx))
            if s < 0:
                ly = [-ly[0], -ly[1], -ly[2]]
            lz = unit(cross(lx, ly))
            R = from_axes(lx, ly, lz)
            R_deg = rot(R)

            mid = [(p0[k] + p1[k]) * 0.5 for k in range(3)]
            t_mid = (t0 + t1) * 0.5
            anti_z = 0.025 if (i % 2 == 0) else -0.025
            w_cross = (wy0 + wy1) * 0.5 + anti_z
            d_cross = (dz0 + dz1) * 0.5 + anti_z

            def miter_ext(a, b, r):
                dot = max(-0.99, min(0.99, a[0]*b[0] + a[1]*b[1] + a[2]*b[2]))
                ang = math.acos(dot)
                return min(r * 0.42, 0.52 * r * math.tan(ang * 0.5) + 0.08)

            ext_prev = miter_ext(tangents[i - 1], lx, w_cross * 0.5) if i > 0 else 0.15
            ext_next = miter_ext(lx, tangents[i + 1], w_cross * 0.5) if i < segs - 1 else 0.05
            shift = (ext_next - ext_prev) * 0.5
            mid_s = [mid[k] + lx[k] * shift for k in range(3)]
            hl = (seg_len + ext_prev + ext_next) * 0.5

            if t_mid < 0.22:
                part = "horn_boss"
            elif t_mid < 0.42:
                part = "horn_grad1"
            elif t_mid < 0.62:
                part = "horn_mid"
            elif t_mid < 0.80:
                part = "horn_grad2"
            else:
                part = "horn_tip"

            head_cubes.append(rot_cube(
                [mid_s[0] - hl, mid_s[1] - w_cross * 0.5, mid_s[2] - d_cross * 0.5],
                [mid_s[0] + hl, mid_s[1] + w_cross * 0.5, mid_s[2] + d_cross * 0.5],
                part, rotation=R_deg, pivot=mid_s,
            ))

            # Stage 3 detail: crisp subtle bovine horn growth rings (annuli) on segments 1..7!
            # Narrow ring band (+0.22 px proud, 0.55 px thick along tangent) centered cleanly on segment -> zero cuts!
            if 1 <= i <= 7:
                ring_hl = 0.28
                ring_w = w_cross + 0.36
                ring_d = d_cross + 0.36
                ring_part = "horn_boss_ridge" if i <= 3 else "horn_ring"
                head_cubes.append(rot_cube(
                    [mid[0] - ring_hl, mid[1] - ring_w * 0.5, mid[2] - ring_d * 0.5],
                    [mid[0] + ring_hl, mid[1] + ring_w * 0.5, mid[2] + ring_d * 0.5],
                    ring_part, rotation=R_deg, pivot=mid,
                ))


def build_bones():
    """Builds the full Stage 3 African Buffalo rig (~222 boxes / 400 budget, 33 bones)."""
    w_s = BARREL_HW / 9.7
    hy = _scale_y
    muzzle_end = 55.2 + MUZZLE_LEN   # canonical 60.8

    # --- HIPS (rear barrel + muscular haunches + pelvic crests + flank fold + ventral contour: ox 1.8..29.2) ---------
    hips_cubes = []
    _barrel_slice(hips_cubes, 1.8, 4.8, 17.5, 30.2, 7.2 * w_s, belly_hw=4.8 * w_s, ridge=False)
    _barrel_slice(hips_cubes, 4.5, 9.5, 14.8, 32.8, 8.8 * w_s, belly_hw=4.7 * w_s, ridge=True)
    _barrel_slice(hips_cubes, 9.2, 15.8, 13.8, 33.4, 9.3 * w_s, belly_hw=4.8 * w_s, ridge=True)
    _barrel_slice(hips_cubes, 15.5, 23.5, 13.4, 33.5, 9.6 * w_s, belly_hw=7.4 * w_s, ridge=True)
    _barrel_slice(hips_cubes, 23.0, 29.2, 13.2, 34.4, 9.7 * w_s, belly_hw=7.6 * w_s, ridge=True)
    # Sculpted muscular haunch pads + Stage 3 upper pelvic hip-bone bevels & flank-to-thigh groin bridge
    for s in (1, -1):
        box(hips_cubes, s * (6.8 * w_s), s * (9.6 * w_s), hy(15.6), hy(30.4), 4.8, 14.4, "body_muscle")
        # Crisp upper pelvic hip crest (catches top light cleanly in 3/4 Back & Side views!)
        box(hips_cubes, s * (5.6 * w_s), s * (8.8 * w_s), hy(29.8), hy(32.5), 6.2, 14.0, "body_highlight")
        # Angled muscular groin flank fold bridging rear belly into stifle (zero hard box seam!)
        box(hips_cubes, s * (5.0 * w_s), s * (8.4 * w_s), hy(14.0), hy(18.6), 13.2, 18.8, "underbelly",
            rotation=[16.0, 0.0, 0.0], pivot=[s * 6.7 * w_s, hy(16.2), oz(16.0)])
    # Authentic subtle bull ventral belly contour ahead of groin (matches Side reference silhouette!)
    sym(hips_cubes, 2.2 * w_s, hy(12.6), hy(13.9), 15.2, 19.0, "underbelly")

    # --- TAIL (3 bones: docked base -> slender shaft -> Stage 3 layered diamond coarse hair switch!) -----------------
    t1_cubes, t2_cubes, t3_cubes = [], [], []
    sym(t1_cubes, 1.35, hy(27.2), hy(30.0), 1.0, 3.8, "tail")
    sym(t1_cubes, 1.00, hy(22.2), hy(28.0), 0.2, 2.2, "tail")
    sym(t2_cubes, 0.88, hy(15.0), hy(22.8), 0.1, 1.9, "tail")
    # Layered coarse hair switch tassel (diamond center + side/front wisps + tapered brush tip)
    sym(t3_cubes, 1.55, hy(11.6), hy(15.6), -0.35, 2.35, "tail_tuft")
    sym(t3_cubes, 1.85, hy(12.3), hy(14.5),  0.05, 1.95, "tail_tuft_dark")
    sym(t3_cubes, 1.15, hy(10.0), hy(12.2),  0.05, 1.95, "tail_tuft")
    sym(t3_cubes, 0.65, hy(8.9),  hy(10.4),  0.40, 1.60, "tail_tuft_dark")

    # --- CHEST (massive shoulder withers hump + Stage 3 scapula plates, withers ruff & brisket dewlap!) --------------
    chest_cubes = []
    hb = HUMP_BOOST
    _barrel_slice(chest_cubes, 27.2, 31.8, 12.6, 35.8 + hb * 0.8, 9.9 * w_s, belly_hw=6.6 * w_s,
                  top_part="hump_crest", ridge=True)
    _barrel_slice(chest_cubes, 31.4, 38.8, 11.4, 36.2 + hb, 10.0 * w_s, belly_hw=4.8 * w_s,
                  top_part="hump_crest", belly_part="brisket", ridge=True)
    _barrel_slice(chest_cubes, 38.4, 43.6, 14.2, 34.8 + hb * 0.5, 9.2 * w_s, belly_hw=5.6 * w_s,
                  top_part="hump_crest", belly_part="brisket", ridge=True)
    # Chunky outer shoulder muscle blocks + Stage 3 sculpted scapula shoulder blade relief
    for s in (1, -1):
        box(chest_cubes, s * (7.2 * w_s), s * (10.2 * w_s), hy(15.2), hy(31.2), 29.8, 39.4, "body_muscle")
        # Sculpted upper scapula plate & shoulder boss catching directional sunlight (matches Hero 3/4!)
        box(chest_cubes, s * (7.8 * w_s), s * (10.55 * w_s), hy(22.2), hy(30.4), 31.2, 38.2, "body_highlight")
        # Subtle ribcage flank step behind shoulder
        box(chest_cubes, s * (8.6 * w_s), s * (10.15 * w_s), hy(17.5), hy(27.5), 27.6, 30.8, "body_muscle")
    # Stage 3 terraced dark withers hair crest along the shoulder hump spine
    sym(chest_cubes, 3.6 * w_s, hy(35.5 + hb * 0.7), hy(36.55 + hb), 31.0, 38.6, "hump_ruff")
    sym(chest_cubes, 3.2 * w_s, hy(34.3 + hb * 0.4), hy(35.35 + hb * 0.5), 38.2, 42.4, "hump_ruff")
    # Deep central brisket keel + sculpted forward chest dewlap fold (tucked strictly between forelegs x in [-4.5..4.5]!)
    sym(chest_cubes, 4.5 * w_s, hy(11.0), hy(16.4), 31.8, 41.2, "brisket")
    sym(chest_cubes, 3.1 * w_s, hy(10.3), hy(13.8), 35.0, 41.8, "dewlap")

    # --- NECK (short, enormously thick bull neck + extra 2.2 px internal rear overlap for telescopic headbutt surge!) -
    neck_cubes = []
    sym(neck_cubes, 7.6 * w_s, hy(18.8), hy(33.0 + hb * 0.25), 39.6, 46.8, "neck")
    sym(neck_cubes, 6.8 * w_s, hy(19.2), hy(31.6), 46.2, 51.0, "neck")
    sym(neck_cubes, 5.6 * w_s, hy(31.2), hy(34.2 + hb * 0.25), 39.8, 46.8, "hump_crest")
    # Stage 3 neck dorsal ruff bridging hump toward boss
    sym(neck_cubes, 3.4 * w_s, hy(33.2), hy(34.5 + hb * 0.2), 40.4, 46.4, "hump_ruff")
    # Taut muscular throat & clean central vertical dewlap skin fold
    sym(neck_cubes, 4.9 * w_s, hy(16.0), hy(19.8), 40.0, 46.4, "brisket")
    sym(neck_cubes, 4.3 * w_s, hy(17.8), hy(20.4), 45.8, 50.2, "brisket")
    sym(neck_cubes, 2.2 * w_s, hy(15.2), hy(18.0), 40.8, 47.4, "dewlap")

    # --- HEAD (glowering skull, carved sockets, sculpted muzzle, flared nostrils, sealed 3D upper mouth & HORNS!) ----
    head_cubes = []
    # Main cranium & heavy cheeks (extended 1.2 px back inside neck -> zero gap on deep down->up-and-out headbutt!)
    sym(head_cubes, 5.7, hy(19.2), hy(24.90), 46.8, 55.6, "head")
    # Rear upper skull behind the eyes (stops cleanly at y = 29.60 below horn boss -> ZERO brow clip!)
    sym(head_cubes, 5.7, hy(24.8), hy(29.60), 46.8, 53.0, "head")
    # Narrower forehead bridge BETWEEN left & right carved eye sockets
    sym(head_cubes, 3.45, hy(24.8), hy(26.6), 52.8, 55.7, "head")
    # Heavy overhanging dark brow ridge cleanly seating the horn boss above (top flush at y = 29.62!)
    sym(head_cubes, 6.05, hy(26.40), hy(29.62), 49.0, 56.2, "head_brow")
    # Sculpted cheek masseter muscle pads & subtle zygomatic ridge below eyes
    for s in (1, -1):
        box(head_cubes, s * 4.3, s * 6.05, hy(19.2), hy(24.8), 49.0, 54.4, "head")
        box(head_cubes, s * 4.6, s * 6.18, hy(23.5), hy(24.85), 51.8, 55.2, "head_brow")

    # Broad rectangular upper muzzle stepping cleanly forward from the face (drops to y=19.85 -> 100% airtight lip seal!)
    sym(head_cubes, 4.15, hy(19.85), hy(24.9), 54.8, muzzle_end - 0.6, "muzzle")
    # Subtle side upper-lip cheek cushions (cleanly overlap shut jaw seam back to ox=52.6 -> ZERO pink leak anywhere!)
    for s in (1, -1):
        box(head_cubes, s * 2.90, s * 4.28, hy(19.55), hy(22.2), 52.6, muzzle_end - 0.5, "muzzle")
    # Sloped/stepped nasal bridge rising cleanly from muzzle back into forehead brow
    sym(head_cubes, 3.55, hy(24.4), hy(26.6), 54.6, 58.2, "head")
    sym(head_cubes, 3.25, hy(24.3), hy(25.6), 57.6, muzzle_end - 1.2, "muzzle")
    # Broad square dark nose-pad / rhinarium at front tip of snout + Stage 3 upper lip ridge & subtle philtrum crease!
    sym(head_cubes, 4.25, hy(19.85), hy(24.7), muzzle_end - 1.1, muzzle_end, "nose_pad")
    # Subtle central vertical philtrum crease between nostrils (toned dark slate, never reads as a 3rd nostril!)
    sym(head_cubes, 0.25, hy(20.0), hy(21.9), muzzle_end - 0.15, muzzle_end + 0.08, "nose_crease")
    sym(head_cubes, 3.70, hy(19.65), hy(20.45), muzzle_end - 0.9, muzzle_end + 0.08, "nose_pad")
    # Two bold square dark nostrils with subtle sculpted outer nostril rims (alae)
    for s in (1, -1):
        box(head_cubes, s * 1.45, s * 3.10, hy(21.45), hy(23.15), muzzle_end - 0.35, muzzle_end + 0.12, "nostril")
        # Outer nostril rim wing
        box(head_cubes, s * 3.05, s * 3.85, hy(21.30), hy(23.45), muzzle_end - 0.45, muzzle_end + 0.16, "nose_rim")
        # Upper nostril arch brow
        box(head_cubes, s * 1.25, s * 3.55, hy(23.10), hy(23.75), muzzle_end - 0.55, muzzle_end + 0.14, "nose_rim")

    # --- SEALED 3D UPPER MOUTH INTERIOR (100% inset inside upper muzzle/cheeks -> ZERO poke-through at JAW_OPEN=0!) ---
    # Vaulted pink ridged palate roof (y = 20.15..20.85, strictly inside hw=2.65 and ox <= muzzle_end - 1.6!)
    sym(head_cubes, 2.55, hy(20.15), hy(20.85), 51.4, muzzle_end - 1.6, "mouth_palate")
    # Tough upper bovine dental pad (real buffalo have a firm pale-pink pad in place of upper front incisors!)
    sym(head_cubes, 2.25, hy(20.00), hy(20.55), muzzle_end - 2.6, muzzle_end - 1.45, "mouth_gum")
    # Deep dark throat wall at back of oral cavity (inset strictly inside skull cheeks!)
    sym(head_cubes, 2.65, hy(19.40), hy(22.20), 49.8, 51.6, "throat_dark")
    # Upper cheek grinding molars (left & right rows, tucked cleanly inside cheeks!)
    for s in (1, -1):
        box(head_cubes, s * 1.80, s * 2.45, hy(19.85), hy(20.45), 52.2, 55.2, "teeth")

    # Approved v5 Horns + Stage 3 Bossing & Corrugated Bovine Growth Rings!
    _build_horns(head_cubes, hy)

    # --- ARTICULATED HEAVY BROW SHELVES (brow_left, brow_right, parent head — for fierce angry/alert moods!) ---------
    brow_l_cubes, brow_r_cubes = [], []
    for s, bc in ((1, brow_l_cubes), (-1, brow_r_cubes)):
        x0, x1 = (3.35, 6.18) if s > 0 else (-6.18, -3.35)
        box(bc, x0, x1, hy(26.28), hy(27.55), 52.4, 56.38, "head_brow")

    # --- JAW & 2-BONE BOVINE TONGUE (100% sealed when shut, rich 3D mouth interior when open!) -----------------------
    jaw_cubes = []
    # Main blocky lower jaw & firm square chin (clean outer walls enclose gums/teeth on all sides!)
    sym(jaw_cubes, 3.60, hy(18.0), hy(19.85), 49.8, 56.2, "jaw")
    sym(jaw_cubes, 3.20, hy(18.2), hy(19.80), 55.8, muzzle_end - 1.1, "jaw")
    # Stage 3 subtle coarse chin-beard tuft under lower jaw
    sym(jaw_cubes, 2.45, hy(17.45), hy(18.35), 52.6, 57.6, "dewlap")
    # Pink mouth floor / gums (strictly inside |x| <= 2.45 and y <= 19.72 -> ZERO leak when shut!)
    sym(jaw_cubes, 2.40, hy(19.20), hy(19.72), 51.2, muzzle_end - 1.6, "mouth_gum")
    # Chunky flat ivory lower bovine incisors across front of lower jaw + left/right lower cheek molars!
    sym(jaw_cubes, 2.05, hy(19.35), hy(19.90), muzzle_end - 2.35, muzzle_end - 1.50, "teeth")
    for s in (1, -1):
        box(jaw_cubes, s * 1.75, s * 2.35, hy(19.35), hy(19.88), 52.2, 55.0, "teeth")

    # 2-bone articulated pink muscular bovine tongue (sits neatly inside mouth floor at rest!)
    tongue_cubes, tongue_tip_cubes = [], []
    sym(tongue_cubes, 1.70, hy(19.30), hy(19.82), 51.5, 56.0, "tongue")
    sym(tongue_tip_cubes, 1.35, hy(19.32), hy(19.78), 55.6, muzzle_end - 2.4, "tongue")

    # --- EYES, DUAL FLUSH EYELIDS & PUPILS (3 clean single-eye variants — zero double-eye illusion!) ----------------
    eye_l_cubes, eye_r_cubes = [], []
    lid_l_cubes, lid_r_cubes = [], []
    ulid_l_cubes, ulid_r_cubes = [], []
    pupil_l_cubes, pupil_r_cubes = [], []
    ey0, ey1 = hy(24.95), hy(26.40)
    ez_ox0, ez_ox1 = 53.0, 55.55
    ex_in, ex_out = 3.45, 5.70

    for s, ec, lc, uc, pc in ((1, eye_l_cubes, lid_l_cubes, ulid_l_cubes, pupil_l_cubes),
                              (-1, eye_r_cubes, lid_r_cubes, ulid_r_cubes, pupil_r_cubes)):
        x0, x1 = (ex_in, ex_out) if s > 0 else (-ex_out, -ex_in)
        lx0, lx1 = (ex_in - 0.05, ex_out + 0.14) if s > 0 else (-(ex_out + 0.14), -(ex_in - 0.05))

        if EYE_VARIANT == "a":
            # VARIATION A — Corner-Wrapped Single Pupil:
            # ONE unbroken dark pupil wraps cleanly around the front-outer 90-deg corner (zero split!),
            # white sclera sits behind it on the side + crisp inner-front white corner toward the nose!
            box(ec, x0, x1, ey0, ey1, ez_ox0, ez_ox1, "eye_sclera")
            # Continuous dark pupil block wrapping outer-front corner -> ONE solid dark iris from Front, 3/4 & Side!
            px0, px1 = (ex_in + 0.65, ex_out + 0.08) if s > 0 else (-(ex_out + 0.08), -(ex_in + 0.65))
            box(pc, px0, px1, ey0 + 0.03, ey1 - 0.03, ez_ox1 - 1.50, ez_ox1 + 0.08, "eye_pupil")
            # Upper & lower flush eyelids
            box(lc, lx0, lx1, ey1 + 0.06, ey1 + 1.50, ez_ox0 - 0.05, ez_ox1 + 0.14, "head_brow")
            box(uc, lx0, lx1, ey0 - 0.90, ey0 - 0.06, ez_ox0 - 0.05, ez_ox1 + 0.12, "head")

        elif EYE_VARIANT == "b":
            # VARIATION B — Angled 36-deg Bovine Single-Plane Socket:
            # Real Cape Buffalo eyes sit on ONE flat 36-deg angled cheek plane recessed inside a dark orbital rim!
            # Non-overlapping [BLACK inner][WHITE outer] slabs on the exact same plane -> zero z-fighting,
            # preserving the fierce Front look while reading as ONE unified eye from Front, 3/4 AND Side!
            box(head_cubes, x0, x1, ey0 - 0.06, ey1 + 0.06, ez_ox0 - 0.1, ez_ox1 - 1.05, "head_brow")
            epiv = [s * 4.55, (ey0 + ey1) * 0.5, oz(55.05)]
            erot = [0.0, s * -32.0, 0.0]
            # Inner dark pupil (inner 64%) + outer crisp white sclera (outer 36%) cleanly side-by-side!
            px0_b, px1_b = (3.42, 4.88) if s > 0 else (-4.88, -3.42)
            sx0_b, sx1_b = (4.88, 5.76) if s > 0 else (-5.76, -4.88)
            box(pc, px0_b, px1_b, ey0, ey1, 55.02, 55.42, "eye_pupil", rotation=erot, pivot=epiv)
            box(ec, sx0_b, sx1_b, ey0, ey1, 55.02, 55.39, "eye_sclera", rotation=erot, pivot=epiv)
            # Dark outer orbital rim capping the rear-outer edge cleanly
            cx0_b, cx1_b = (5.76, 6.02) if s > 0 else (-6.02, -5.76)
            box(head_cubes, cx0_b, cx1_b, ey0 - 0.05, ey1 + 0.05, 54.88, 55.40, "head_brow", rotation=erot, pivot=epiv)
            # Flush angled upper & lower eyelids
            lx0_b, lx1_b = (3.32, 5.90) if s > 0 else (-5.90, -3.32)
            box(lc, lx0_b, lx1_b, ey1 + 0.06, ey1 + 1.50, 54.96, 55.50, "head_brow", rotation=erot, pivot=epiv)
            box(uc, lx0_b, lx1_b, ey0 - 0.90, ey0 - 0.06, 54.96, 55.48, "head", rotation=erot, pivot=epiv)

        else:
            # VARIATION C — Front-Set Hooded Socket (Exact Front Look + Dark Sculpted Outer Eyelid Rim):
            # Keeps the exact [BLACK inner][WHITE outer] front eye from v7 and wraps a dark orbital rim flush
            # to the outer corner so there is ZERO white side slab — just one fierce recessed forward eye!
            box(head_cubes, x0, x1, ey0 - 0.05, ey1 + 0.05, ez_ox0 - 0.1, ez_ox1 - 0.22, "head")
            # Dark outer corner hood/eyelid rim capping the side edge of the sclera
            rx0, rx1 = (ex_out - 0.14, ex_out + 0.08) if s > 0 else (-(ex_out + 0.08), -(ex_out - 0.14))
            box(head_cubes, rx0, rx1, ey0 - 0.04, ey1 + 0.04, ez_ox1 - 0.45, ez_ox1 + 0.02, "head_brow")
            # Clean non-overlapping side-by-side inner dark pupil + outer crisp white sclera!
            px0, px1 = (ex_in, ex_out - 0.78) if s > 0 else (-(ex_out - 0.78), -ex_in)
            sx0, sx1 = (ex_out - 0.78, ex_out - 0.14) if s > 0 else (-(ex_out - 0.14), -(ex_out - 0.78))
            box(pc, px0, px1, ey0, ey1, ez_ox1 - 0.22, ez_ox1 + 0.04, "eye_pupil")
            box(ec, sx0, sx1, ey0, ey1, ez_ox1 - 0.22, ez_ox1 + 0.01, "eye_sclera")
            # Flush upper & lower eyelids covering the front-set eye
            box(lc, lx0, lx1, ey1 + 0.06, ey1 + 1.50, ez_ox1 - 0.45, ez_ox1 + 0.14, "head_brow")
            box(uc, lx0, lx1, ey0 - 0.90, ey0 - 0.06, ez_ox1 - 0.45, ez_ox1 + 0.12, "head")

    # --- EARS (broad, cupped, slightly drooping leaf ears + Stage 3 ear rim & inner fold relief!) --------------------
    ear_l_cubes, ear_r_cubes = [], []
    for s, ec in ((1, ear_l_cubes), (-1, ear_r_cubes)):
        epiv = [s * 5.4, hy(25.2), oz(50.2)]
        erot = [5.0, s * -12.0, s * 26.0]
        box(ec, s * 5.0, s * 10.6, hy(21.4), hy(25.8), 48.6, 50.4, "ear_outer", rotation=erot, pivot=epiv)
        box(ec, s * 5.2, s * 11.4, hy(24.6), hy(26.1), 49.0, 51.1, "head_brow", rotation=erot, pivot=epiv)
        box(ec, s * 10.0, s * 12.2, hy(22.2), hy(25.3), 48.8, 50.3, "ear_outer", rotation=erot, pivot=epiv)
        box(ec, s * 5.8, s * 10.8, hy(21.8), hy(24.8), 50.1, 51.0, "ear_inner", rotation=erot, pivot=epiv)
        # Stage 3 detail: subtle dark fringed lower ear rim + inner cartilaginous fold
        box(ec, s * 5.6, s * 10.9, hy(21.15), hy(21.95), 49.2, 50.9, "head_brow", rotation=erot, pivot=epiv)
        box(ec, s * 6.4, s * 9.8,  hy(23.10), hy(23.95), 50.65, 51.15, "ear_fold", rotation=erot, pivot=epiv)

    # --- FOUR STURDY PILLAR LEGS & DETAILED TWIN-TOED CLOVEN HOOVES (minY = 0.000 px!) -------------------------------
    def make_front_leg(prefix, side):
        cx = side * 5.75 * w_s
        ox_mid = 33.6
        cz = oz(ox_mid)
        y_sh = hy(21.2)
        y_kn = hy(11.0)
        y_an = hy(3.4)

        u_cubes, l_cubes, f_cubes = [], [], []
        # Heavy muscular upper forearm + Stage 3 bicep/elbow relief
        box(u_cubes, cx - 2.9, cx + 2.9, hy(15.0), hy(23.8), ox_mid - 3.9, ox_mid + 3.9, "leg_upper")
        box(u_cubes, cx - 2.6, cx + 2.6, y_kn - 0.5, hy(15.8), ox_mid - 3.1, ox_mid + 3.1, "leg_upper")
        # Subtle protruding elbow point at rear of upper forearm
        box(u_cubes, cx - 2.0, cx + 2.0, hy(13.8), hy(17.4), ox_mid - 4.35, ox_mid - 3.0, "leg_upper")
        # Carpal knee cap + sturdy vertical cannon shin
        box(l_cubes, cx - 2.45, cx + 2.45, y_kn - 1.4, y_kn + 1.2, ox_mid - 2.65, ox_mid + 2.65, "leg_lower")
        box(l_cubes, cx - 1.85, cx + 1.85, y_kn - 0.9, y_kn + 0.9, ox_mid + 2.35, ox_mid + 2.95, "leg_joint")
        box(l_cubes, cx - 2.25, cx + 2.25, y_an - 0.3, y_kn - 0.6, ox_mid - 2.40, ox_mid + 2.40, "leg_lower")
        # Fetlock crown + Stage 3 coronary band rim + twin-toed cloven hoof (minY = 0.000!)
        box(f_cubes, cx - 2.40, cx + 2.40, 2.2, y_an + 0.5, ox_mid - 2.60, ox_mid + 2.70, "hoof_crown")
        box(f_cubes, cx - 2.62, cx + 2.62, 2.05, 2.65, ox_mid - 2.70, ox_mid + 3.00, "hoof_rim")
        # Twin cloven hoof toes with crisp central front cleft!
        box(f_cubes, cx - 2.55, cx - 0.18, 0.0, 2.25, ox_mid - 2.65, ox_mid + 3.15, "hoof")
        box(f_cubes, cx + 0.18, cx + 2.55, 0.0, 2.25, ox_mid - 2.65, ox_mid + 3.15, "hoof")
        box(f_cubes, cx - 1.2, cx + 1.2, 0.0, 2.15, ox_mid - 2.60, ox_mid + 0.8, "hoof")
        # Paired left/right rounded rear dewclaws perched behind fetlock!
        for ds in (-1, 1):
            dx0, dx1 = sorted([cx + ds * 0.55, cx + ds * 1.95])
            box(f_cubes, dx0, dx1, 2.25, 3.45, ox_mid - 3.25, ox_mid - 2.15, "hoof")

        return [
            {"name": f"{prefix}_1", "parent": "chest", "pivot": [round(cx, 2), round(y_sh, 2), round(cz, 2)], "cubes": u_cubes},
            {"name": f"{prefix}_2", "parent": f"{prefix}_1", "pivot": [round(cx, 2), round(y_kn, 2), round(cz, 2)], "cubes": l_cubes},
            {"name": f"{prefix}_foot", "parent": f"{prefix}_2", "pivot": [round(cx, 2), round(y_an, 2), round(cz, 2)], "cubes": f_cubes},
        ]

    def make_hind_leg(prefix, side):
        cx = side * 5.75 * w_s
        ox_hip = 10.6
        ox_hock = 6.9
        ox_foot = 7.0
        y_hip = hy(22.4)
        y_hock = hy(11.2)
        y_an = hy(3.4)

        u_cubes, l_cubes, f_cubes = [], [], []
        # Heavy muscular upper hind thigh + angled gaskin + Stage 3 sharp Achilles hock tendon!
        box(u_cubes, cx - 2.95, cx + 2.95, hy(16.6), hy(24.6), ox_hip - 4.4, ox_hip + 4.0, "leg_upper")
        _aligned_limb_box(u_cubes, [cx, hy(18.5), oz(10.0)], [cx, y_hock, oz(ox_hock)], 5.1, 6.4, "leg_upper", pad=0.65)
        # Sculpted Achilles tendon ridge down back of hind thigh into hock point
        _aligned_limb_box(u_cubes, [cx, hy(17.8), oz(7.4)], [cx, y_hock + 0.2, oz(ox_hock - 1.6)], 2.8, 2.2, "leg_joint", pad=0.4)
        # Hock joint cap + sturdy vertical hind cannon shin
        box(l_cubes, cx - 2.45, cx + 2.45, y_hock - 1.3, y_hock + 1.3, ox_hock - 2.85, ox_hock + 2.55, "leg_lower")
        box(l_cubes, cx - 1.75, cx + 1.75, y_hock - 0.6, y_hock + 1.2, ox_hock - 3.30, ox_hock - 2.20, "leg_joint")
        box(l_cubes, cx - 2.25, cx + 2.25, y_an - 0.3, y_hock - 0.5, ox_foot - 2.40, ox_foot + 2.40, "leg_lower")
        # Fetlock crown + Stage 3 coronary band rim + twin-toed cloven hoof (minY = 0.000!)
        box(f_cubes, cx - 2.40, cx + 2.40, 2.2, y_an + 0.5, ox_foot - 2.60, ox_foot + 2.70, "hoof_crown")
        box(f_cubes, cx - 2.62, cx + 2.62, 2.05, 2.65, ox_foot - 2.70, ox_foot + 3.00, "hoof_rim")
        box(f_cubes, cx - 2.55, cx - 0.18, 0.0, 2.25, ox_foot - 2.65, ox_foot + 3.15, "hoof")
        box(f_cubes, cx + 0.18, cx + 2.55, 0.0, 2.25, ox_foot - 2.65, ox_foot + 3.15, "hoof")
        box(f_cubes, cx - 1.2, cx + 1.2, 0.0, 2.15, ox_foot - 2.60, ox_foot + 0.8, "hoof")
        for ds in (-1, 1):
            dx0, dx1 = sorted([cx + ds * 0.55, cx + ds * 1.95])
            box(f_cubes, dx0, dx1, 2.25, 3.45, ox_foot - 3.25, ox_foot - 2.15, "hoof")

        return [
            {"name": f"{prefix}_1", "parent": "hips", "pivot": [round(cx, 2), round(y_hip, 2), round(oz(ox_hip), 2)], "cubes": u_cubes},
            {"name": f"{prefix}_2", "parent": f"{prefix}_1", "pivot": [round(cx, 2), round(y_hock, 2), round(oz(ox_hock), 2)], "cubes": l_cubes},
            {"name": f"{prefix}_foot", "parent": f"{prefix}_2", "pivot": [round(cx, 2), round(y_an, 2), round(oz(ox_foot), 2)], "cubes": f_cubes},
        ]

    # --- Assemble full clean 33-bone hierarchy (zero "parent": null!) -------------------------------------------------
    jaw_piv = [0, round(hy(19.8), 2), round(oz(50.2), 2)]
    bones = [
        {"name": "root", "pivot": [0, 0, 0], "cubes": []},
        {"name": "hips", "parent": "root", "pivot": [0, round(hy(22.0), 2), round(oz(19.0), 2)], "cubes": hips_cubes},
        {"name": "tail_1", "parent": "hips", "pivot": [0, round(hy(29.0), 2), round(oz(2.2), 2)], "cubes": t1_cubes},
        {"name": "tail_2", "parent": "tail_1", "pivot": [0, round(hy(22.6), 2), round(oz(1.1), 2)], "cubes": t2_cubes},
        {"name": "tail_3", "parent": "tail_2", "pivot": [0, round(hy(15.2), 2), round(oz(1.0), 2)], "cubes": t3_cubes},
        {"name": "chest", "parent": "hips", "pivot": [0, round(hy(22.5), 2), round(oz(28.2), 2)], "cubes": chest_cubes},
        {"name": "neck", "parent": "chest", "pivot": [0, round(hy(24.0), 2), round(oz(42.4), 2)], "cubes": neck_cubes},
        {"name": "head", "parent": "neck", "pivot": [0, round(hy(24.8), 2), round(oz(49.0), 2)], "cubes": head_cubes},
        {"name": "brow_left",  "parent": "head", "pivot": [ 4.7, round(hy(26.9), 2), round(oz(54.4), 2)], "cubes": brow_l_cubes},
        {"name": "brow_right", "parent": "head", "pivot": [-4.7, round(hy(26.9), 2), round(oz(54.4), 2)], "cubes": brow_r_cubes},
        {"name": "jaw", "parent": "head", "pivot": jaw_piv,
         "rotation": [round(JAW_OPEN, 2), 0, 0] if JAW_OPEN else [0, 0, 0], "cubes": jaw_cubes},
        {"name": "tongue",     "parent": "jaw", "pivot": [0, round(hy(19.7), 2), round(oz(52.0), 2)], "cubes": tongue_cubes},
        {"name": "tongue_tip", "parent": "tongue", "pivot": [0, round(hy(19.7), 2), round(oz(55.8), 2)], "cubes": tongue_tip_cubes},
        {"name": "ear_left",   "parent": "head", "pivot": [ 5.4, round(hy(25.2), 2), round(oz(50.2), 2)], "cubes": ear_l_cubes},
        {"name": "ear_right",  "parent": "head", "pivot": [-5.4, round(hy(25.2), 2), round(oz(50.2), 2)], "cubes": ear_r_cubes},
        {"name": "eye_left",   "parent": "head", "pivot": [ 4.6, round((ey0 + ey1) * 0.5, 2), round(oz(54.2), 2)], "cubes": eye_l_cubes},
        {"name": "eye_right",  "parent": "head", "pivot": [-4.6, round((ey0 + ey1) * 0.5, 2), round(oz(54.2), 2)], "cubes": eye_r_cubes},
        {"name": "lid_left",   "parent": "head", "pivot": [ 4.6, round(ey1, 2), round(oz(54.2), 2)], "cubes": lid_l_cubes},
        {"name": "lid_right",  "parent": "head", "pivot": [-4.6, round(ey1, 2), round(oz(54.2), 2)], "cubes": lid_r_cubes},
        {"name": "ulid_left",  "parent": "head", "pivot": [ 4.6, round(ey0, 2), round(oz(54.2), 2)], "cubes": ulid_l_cubes},
        {"name": "ulid_right", "parent": "head", "pivot": [-4.6, round(ey0, 2), round(oz(54.2), 2)], "cubes": ulid_r_cubes},
        {"name": "pupil_left",  "parent": "head", "pivot": [ 4.1, round((ey0 + ey1) * 0.5, 2), round(oz(ez_ox1), 2)], "cubes": pupil_l_cubes},
        {"name": "pupil_right", "parent": "head", "pivot": [-4.1, round((ey0 + ey1) * 0.5, 2), round(oz(ez_ox1), 2)], "cubes": pupil_r_cubes},
    ]
    bones += make_front_leg("leg_fl",  1)
    bones += make_front_leg("leg_fr", -1)
    bones += make_hind_leg("leg_bl",  1)
    bones += make_hind_leg("leg_br", -1)
    return bones


# --- Stage 3 Shape Detail clean solid colours (readable sculptural planes before Stage 4 Paint!) ---------------------
PLAIN_COLOURS = {
    "body":             (74, 56, 48),
    "body_muscle":      (84, 64, 54),
    "body_highlight":   (92, 70, 59),
    "back_crest":       (62, 47, 41),
    "hump_crest":       (58, 44, 38),
    "hump_ruff":        (48, 36, 32),
    "underbelly":       (56, 42, 37),
    "brisket":          (52, 39, 34),
    "dewlap":           (45, 34, 30),
    "neck":             (64, 48, 42),
    "head":             (60, 46, 40),
    "head_brow":        (46, 35, 31),
    "muzzle":           (72, 58, 54),
    "nose_pad":         (56, 48, 48),
    "nose_rim":         (64, 55, 55),
    "nose_crease":      (42, 35, 36),
    "nostril":          (22, 18, 20),
    "jaw":              (52, 40, 36),
    "mouth_palate":     (168, 96, 102),
    "mouth_gum":        (184, 112, 118),
    "tongue":           (196, 106, 116),
    "throat_dark":      (36, 18, 22),
    "teeth":            (230, 222, 202),
    "ear_outer":        (56, 43, 38),
    "ear_inner":        (124, 76, 58),
    "ear_fold":         (104, 62, 48),
    "horn_boss":        (164, 148, 130),
    "horn_boss_top":    (172, 156, 138),
    "horn_boss_ridge":  (152, 136, 118),
    "horn_boss_crease": (124, 110, 96),
    "horn_grad1":       (144, 130, 114),
    "horn_mid":         (120, 108, 96),
    "horn_ring":        (106, 95, 85),
    "horn_grad2":       (94, 85, 78),
    "horn_tip":         (64, 58, 54),
    "eye_sclera":       (238, 236, 230),
    "eye_pupil":        (20, 16, 18),
    "leg_upper":        (68, 52, 45),
    "leg_lower":        (56, 43, 38),
    "leg_joint":        (64, 50, 44),
    "hoof_crown":       (48, 39, 36),
    "hoof_rim":         (54, 46, 44),
    "hoof":             (42, 38, 38),
    "tail":             (54, 42, 37),
    "tail_tuft":        (36, 28, 26),
    "tail_tuft_dark":   (28, 22, 20),
}

CLAY_COLOURS = {
    "horn_boss":        (198, 192, 182),
    "horn_boss_top":    (204, 198, 188),
    "horn_boss_ridge":  (188, 182, 172),
    "horn_boss_crease": (162, 156, 148),
    "horn_grad1":       (190, 184, 175),
    "horn_mid":         (182, 176, 168),
    "horn_ring":        (170, 164, 156),
    "horn_grad2":       (166, 160, 154),
    "horn_tip":         (148, 142, 136),
    "hoof":             (126, 122, 118),
    "hoof_rim":         (142, 138, 134),
    "nostril":          (90, 86, 84),
    "nose_crease":      (132, 128, 124),
    "eye_sclera":       (228, 226, 222),
    "eye_pupil":        (82, 78, 76),
    "ear_inner":        (158, 148, 142),
    "mouth_palate":     (178, 152, 154),
    "mouth_gum":        (184, 158, 160),
    "tongue":           (190, 156, 160),
    "teeth":            (218, 214, 206),
}


# --- Stage 4 Minecraft Pixel-Art Painting (sampled directly from references/inspiration/buffalo.png!) ----------------
def _h3(ix, iy, iz, seed=0):
    """Deterministic hash in [0, 1) on integer voxel coordinates."""
    n = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 1597334677) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536.0


def _vnoise(x, y, z, scale=1.0, seed=0):
    """Smooth 3D value noise in [0, 1] at world coordinate (x, y, z) / scale."""
    fx, fy, fz = x / scale, y / scale, z / scale
    ix, iy, iz = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = fx - ix, fy - iy, fz - iz
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    tz = tz * tz * (3 - 2 * tz)
    c000 = _h3(ix,     iy,     iz,     seed)
    c100 = _h3(ix + 1, iy,     iz,     seed)
    c010 = _h3(ix,     iy + 1, iz,     seed)
    c110 = _h3(ix + 1, iy + 1, iz,     seed)
    c001 = _h3(ix,     iy,     iz + 1, seed)
    c101 = _h3(ix + 1, iy,     iz + 1, seed)
    c011 = _h3(ix,     iy + 1, iz + 1, seed)
    c111 = _h3(ix + 1, iy + 1, iz + 1, seed)
    x00 = c000 + (c100 - c000) * tx
    x10 = c010 + (c110 - c010) * tx
    x01 = c001 + (c101 - c001) * tx
    x11 = c011 + (c111 - c011) * tx
    y0 = x00 + (x10 - x00) * ty
    y1 = x01 + (x11 - x01) * ty
    return y0 + (y1 - y0) * tz


# Exact sampled palette from references/inspiration/buffalo.png (Texture Palette + Flank Swatch + Hero Close-ups!)
PAL_DEEP_BLACK  = (26, 20, 19)     # deepest hair crease / shadow tufts
PAL_ESPRESSO    = (42, 33, 31)     # rich dark charcoal-espresso coat
PAL_UMBER_MID   = (62, 47, 44)     # mid warm chocolate-umber hide
PAL_WARM_BROWN  = (86, 62, 53)     # sunlit warm earthy brown patches (dominant in Flank Swatch!)
PAL_RUSSET_DUST = (108, 78, 64)    # warm russet-dusty coat highlights (pops in shade & sun!)
PAL_SAND_DUST   = (124, 104, 88)   # dry savanna dust on lower ankles/elder highlights

PAL_BOSS_LIGHT  = (188, 170, 152)  # sunlit pale sandstone-bone crown (crisp reference contrast!)
PAL_BOSS_MID    = (146, 128, 112)  # weathered taupe central boss
PAL_BOSS_DARK   = (92, 76, 66)     # boss cracks, pits & central cleft
PAL_HORN_MID    = (118, 104, 94)   # weathered grey-taupe mid-horn curve
PAL_HORN_GROOVE = (72, 62, 58)     # recessed dark transverse growth rings
PAL_HORN_TIP    = (42, 36, 36)     # polished dark charcoal-slate hooked tips

PAL_EAR_INNER   = (122, 74, 58)    # warm reddish-terracotta inner ear cup
PAL_EAR_DEEP    = (74, 46, 40)     # deeper shadowed ear canal

PAL_MUZZLE_MID  = (62, 52, 55)     # cool slate-charcoal bovine nose-pad
PAL_MUZZLE_HI   = (84, 72, 76)     # damp sheen along upper nose bridge & nostril rims
PAL_MUZZLE_DARK = (38, 31, 33)     # nose pores & crease
PAL_NOSTRIL     = (18, 15, 17)     # deep black nostril opening

PAL_HOOF_DARK   = (42, 37, 39)     # dark keratin cloven hoof base
PAL_HOOF_MID    = (62, 54, 58)     # subtle slate keratin vertical striations
PAL_HOOF_DUST   = (88, 76, 62)     # subtle dry savanna soil along lower hoof rim


def _clamp3(r, g, b):
    return (max(0.0, min(255.0, r)), max(0.0, min(255.0, g)), max(0.0, min(255.0, b)))


def _shade(col, k):
    return _clamp3(col[0] * k, col[1] * k, col[2] * k)


def make_painter(look="var_b"):
    """Stage 4 Minecraft-first pixel-art painter with 3 clean cohesive variations:
      - 'var_a' : Vanilla+ Clean   (closest to Plain Detail + crisp subtle ±6% Minecraft pixel grain & clean gradients)
      - 'var_b' : Crafted Fur      (Plain Detail hierarchy + gentle vertical fur-drape strokes & sweeping horn grain)
      - 'var_c' : Bold Savanna     (stylized chunky 3-step Minecraft shading: warm sunlit shoulders, dark espresso face, bold horns)
      - 'plain' : Stage 3 Plain Detail solid blocks
      - 'clay'  : Sculptor neutral clay
    """
    if look == "painted":
        look = "var_b"

    def colour(c, face, x, y, z):
        part = c.get("_part", "body")
        if look in ("plain", "clay"):
            base = CLAY_COLOURS.get(part, (172, 166, 158)) if look == "clay" else PLAIN_COLOURS.get(part, (74, 56, 48))
            k = {"up": 1.06, "down": 0.84, "north": 1.00, "south": 0.94, "east": 0.97, "west": 0.97}.get(face, 1.0)
            return _shade(base, k)

        # Clean Minecraft directional face shading
        fk = {"up": 1.05, "down": 0.84, "north": 1.00, "south": 0.95, "east": 0.97, "west": 0.97}.get(face, 1.0)
        base = PLAIN_COLOURS.get(part, (74, 56, 48))

        # Integer 1-model-px grid (crisp Minecraft pixel scale)
        gx, gy, gz = math.floor(x + 0.01), math.floor(y + 0.01), math.floor(z + 0.01)
        h1 = _h3(gx, gy, gz, seed=13)              # 1-px pixel grain [0..1]
        # Vertically-draped fur stroke grid (stretched ~2x vertically for natural hair flow, zero static!)
        sgx, sgy, sgz = math.floor(x * 1.15 + 0.01), math.floor(y * 0.52 + 0.01), math.floor(z * 1.15 + 0.01)
        fur_v = _h3(sgx, sgy, sgz, seed=29)        # vertical strand [0..1]
        # Smooth broad body shading noise (very low frequency — zero noisy speckling!)
        broad = _vnoise(x, y * 0.85, z, scale=9.5, seed=17)

        # --- EYES (crisp iconic Minecraft eye — super clean white sclera + deep glossy obsidian pupil!) ---
        if part == "eye_sclera":
            lid_sh = 0.86 if y > 26.08 else 1.0
            return _shade((238, 236, 230), lid_sh)
        if part == "eye_pupil":
            return (18, 15, 18)

        # --- SEALED 3D BOVINE MOUTH INTERIOR ---
        if part == "mouth_palate":
            ridge = 0.5 + 0.5 * math.sin(z * 4.2)
            col = mix((154, 84, 92), (178, 104, 112), 0.35 + 0.55 * ridge)
            return _shade(col, fk)
        if part == "mouth_gum":
            return _shade(mix((172, 100, 108), (190, 118, 124), 0.35 + 0.4 * h1), fk)
        if part == "tongue":
            groove = 0.88 if abs(x) < 0.35 else 1.0
            col = mix((186, 98, 108), (206, 114, 124), 0.35 + 0.4 * h1)
            return _shade(col, groove * fk)
        if part == "teeth":
            return _shade(mix((218, 210, 188), (236, 230, 212), 0.4 + 0.4 * h1), fk)
        if part == "throat_dark":
            return (32, 16, 20)

        # --- WARM TERRACOTTA INNER EARS ---
        if part == "ear_inner":
            out_f = max(0.0, min(1.0, (abs(x) - 5.8) / 4.8))
            inner_hi = (138, 84, 62) if look == "var_c" else (128, 78, 58)
            inner_lo = (84, 52, 42)
            g = (h1 - 0.5) * (0.08 if look == "var_a" else 0.12)
            col = mix(inner_lo, inner_hi, max(0.0, min(1.0, 0.28 + 0.62 * out_f + g)))
            return _shade(col, fk)
        if part == "ear_fold":
            col = mix((88, 54, 44), (110, 66, 50), 0.45 + 0.25 * h1)
            return _shade(col, fk)

        # --- MUZZLE & NOSE PAD ---
        if part == "nostril":
            return (20, 16, 18)
        if part == "nose_crease":
            return _shade((40, 34, 35), fk)
        if part == "nose_pad":
            top_sheen = 1.12 if (face == "up" or y > 23.75) else 1.0
            if look == "var_a":
                col = _shade((56, 48, 49), (0.96 + 0.08 * h1) * top_sheen)
            elif look == "var_b":
                col = mix((48, 42, 44), (66, 57, 60), 0.32 + 0.45 * h1)
                col = _shade(col, top_sheen)
            else:  # var_c
                step = 0.85 if h1 > 0.68 else (0.15 if h1 < 0.22 else 0.50)
                col = mix((46, 40, 44), (72, 62, 66), step)
                col = _shade(col, 1.16 if (face == "up" or y > 23.75) else 1.0)
            return _shade(col, fk)
        if part == "nose_rim":
            amp = 0.06 if look == "var_a" else 0.10
            col = _shade((66, 57, 58), 1.0 - amp * 0.5 + amp * h1)
            return _shade(col, fk)

        # --- HORNS & CENTRAL BOSS ---
        if part.startswith("horn"):
            # Sweeping grain stretched gently along the horn's X/Z run so it flows cleanly without spots!
            hg = _h3(math.floor(x * 0.75 + 0.01), math.floor(y * 1.15 + 0.01), math.floor(z * 0.75 + 0.01), seed=59)
            hs = _vnoise(x * 0.8, y * 1.1, z * 0.8, scale=3.2, seed=71)

            if look == "var_a":
                # Vanilla+ Clean: exact Plain Detail horn gradient + gentle ±5.5% pale bone grain (zero harsh pits!)
                val = 0.945 + 0.11 * (0.65 * h1 + 0.35 * hs)
                if part in ("horn_boss", "horn_boss_top") and abs(x) < 0.95:
                    val *= 0.93  # gentle forehead cleft shading
                if part == "horn_ring":
                    val *= 0.92
                return _shade(base, val * fk)

            elif look == "var_b":
                # Crafted Fur: subtle sweeping fibrous keratin striations along the horn + soft sunlit crown!
                t = 0.55 * hg + 0.45 * hs
                col = _shade(base, 0.91 + 0.18 * t)
                if part in ("horn_boss", "horn_boss_top"):
                    if face == "up" or y > 33.2:
                        col = mix(col, (186, 170, 152), 0.28)
                    if abs(x) < 1.15:
                        col = mix(col, (118, 104, 92), 0.30 * (1.0 - abs(x) / 1.15))
                elif part == "horn_ring":
                    col = mix(col, (82, 72, 65), 0.36)
                elif part == "horn_tip":
                    col = mix(col, (48, 42, 40), 0.32)
                return _shade(col, fk)

            else:  # var_c: Bold Savanna — crisp graphic 3-step horn shading so the horns pop in Minecraft!
                q = 1.0 if (0.55 * hg + 0.45 * hs) > 0.64 else (0.0 if (0.55 * hg + 0.45 * hs) < 0.30 else 0.5)
                if part in ("horn_boss", "horn_boss_top", "horn_boss_ridge"):
                    lo = (146, 130, 114) if part != "horn_boss_ridge" else (128, 114, 98)
                    hi = (190, 174, 154) if part == "horn_boss_top" else (178, 162, 142)
                    col = mix(lo, hi, q)
                    if abs(x) < 1.15:
                        col = mix(col, (112, 98, 86), 0.38)
                elif part == "horn_boss_crease":
                    col = (106, 94, 82)
                elif part == "horn_grad1":
                    col = mix((132, 118, 104), (160, 144, 126), q)
                elif part == "horn_mid":
                    col = mix((106, 94, 84), (132, 118, 106), q)
                elif part == "horn_ring":
                    col = (84, 74, 68)
                elif part == "horn_grad2":
                    col = mix((76, 68, 64), (100, 90, 82), q)
                else:  # horn_tip
                    col = mix((42, 37, 37), (58, 52, 50), q)
                return _shade(col, fk)

        # --- CLOVEN HOOVES & ANKLE RIM ---
        if part == "hoof":
            stripe = _h3(math.floor(x * 1.8), 0, math.floor(z * 1.8), seed=101)
            if look == "var_a":
                col = _shade((42, 38, 39), 0.94 + 0.12 * stripe)
            elif look == "var_b":
                col = mix((38, 34, 36), (54, 48, 50), 0.25 + 0.60 * stripe)
                if y < 0.65:
                    col = mix(col, (76, 66, 56), (0.65 - y) / 0.65 * 0.28)
            else:  # var_c
                col = mix((36, 32, 35), (56, 50, 54), 0.8 if stripe > 0.55 else 0.15)
                if y < 0.70:
                    col = mix(col, (84, 72, 58), 0.30)
            return _shade(col, fk)
        if part == "hoof_rim":
            col = _shade((58, 50, 48), 0.95 + 0.10 * h1)
            return _shade(col, fk)

        # --- MAIN COAT, HUMP, NECK, HEAD, MUZZLE, LEGS & TAIL ---
        # Preserve Plain Detail's crisp anatomical part colors as the anchor!
        sun_t = max(0.0, min(1.0, (y - 9.0) / 25.0))  # 0 at lower legs/belly -> 1 at top of back/hump

        if look == "var_a":
            # VARIATION A — Vanilla+ Clean:
            # Ultra-clean Minecraft pixel polish right on top of Plain Detail!
            # Tight ±6.5% 1-px grain + gentle sun/belly shading (zero patchy blotches).
            grain = 0.935 + 0.13 * h1
            grad = 0.94 + 0.12 * sun_t
            if part == "muzzle":
                snout_f = max(0.0, min(1.0, (-z - 26.5) / 3.8))
                col = mix(base, (60, 50, 50), snout_f * 0.45)
                return _shade(col, grain * fk)
            if part in ("tail_tuft", "tail_tuft_dark"):
                return _shade(base, (0.92 + 0.14 * fur_v) * fk)
            return _shade(base, grain * grad * fk)

        elif look == "var_b":
            # VARIATION B — Crafted Fur:
            # Plain Detail hierarchy + gentle downward-draping Minecraft fur strokes & warm sunlit top / cool dark belly!
            stroke = 0.68 * fur_v + 0.32 * h1
            # Slight warm sunlit shift on upper body/shoulders, cooler darker espresso lower down
            col = base
            if part in ("body", "body_muscle", "body_highlight", "leg_upper"):
                warm_top = (min(255, base[0] * 1.13 + 4), min(255, base[1] * 1.07 + 2), base[2] * 1.01)
                cool_low = (base[0] * 0.84, base[1] * 0.84, base[2] * 0.88)
                col = mix(cool_low, warm_top, 0.25 + 0.75 * sun_t)
                # Subtle organic broad tonal breath (never spotted!)
                col = _shade(col, 0.94 + 0.12 * broad)
            elif part == "muzzle":
                snout_f = max(0.0, min(1.0, (-z - 26.2) / 4.0))
                col = mix(base, (58, 49, 51), snout_f * 0.50)
            elif part in ("leg_lower", "leg_joint", "hoof_crown"):
                if y < 4.5:
                    col = mix(base, (82, 68, 58), ((4.5 - y) / 4.5) * 0.22)
            # Clean vertical fur-strand grain (±9.5%)
            amp = 0.19 if part not in ("head", "head_brow", "muzzle") else 0.13
            col = _shade(col, (1.0 - amp * 0.5) + amp * stroke)
            return _shade(col, fk)

        else:
            # VARIATION C — Bold Savanna:
            # Clean, iconic, stylized Minecraft mob palette — deliberate 3-step chunky pixel shading!
            # Rich warm chestnut-umber sunlit body & sculpted shoulders contrasting against deep dark espresso head/mane/legs.
            col = base
            if part in ("body", "body_muscle", "body_highlight"):
                # Broad, clean warm sunlit shoulder/flank zone (high scale = smooth cohesive blocks, zero speckles!)
                zone = max(0.0, min(1.0, 0.58 * sun_t + 0.42 * broad))
                # Quantize softly into 3 clean Minecraft pixel-art steps
                step_v = 0.72 * zone + 0.28 * (0.65 * fur_v + 0.35 * h1)
                if step_v < 0.36:
                    c_lo, c_hi = (52, 39, 35), (68, 50, 43)
                    col = mix(c_lo, c_hi, 0.35 if step_v < 0.20 else 0.85)
                elif step_v < 0.70:
                    col = (80, 58, 48) if part == "body" else (92, 67, 55)
                else:
                    col = (96, 70, 56) if part == "body" else (106, 77, 61)
            elif part in ("back_crest", "hump_crest", "hump_ruff"):
                col = _shade(base, 0.86 if fur_v < 0.35 else (1.04 if fur_v > 0.72 else 0.95))
            elif part in ("underbelly", "brisket", "dewlap"):
                col = _shade((46, 35, 31), 0.90 if fur_v < 0.4 else 1.04)
            elif part in ("neck", "head", "head_brow", "jaw", "ear_outer"):
                # Deep dramatic dark-espresso head/neck framing so eyes & pale boss horns POP!
                dark_base = _shade(base, 0.90)
                col = _shade(dark_base, 0.92 if h1 < 0.35 else (1.07 if h1 > 0.72 else 1.00))
            elif part == "muzzle":
                snout_f = max(0.0, min(1.0, (-z - 26.0) / 4.2))
                col = mix((60, 48, 45), (56, 48, 51), snout_f * 0.60)
                col = _shade(col, 0.94 if h1 < 0.4 else 1.05)
            elif part in ("leg_upper", "leg_lower", "leg_joint", "hoof_crown"):
                col = _shade(base, 0.90 if fur_v < 0.35 else (1.08 if fur_v > 0.72 else 0.99))
                if y < 5.0:
                    col = mix(col, (86, 72, 60), ((5.0 - y) / 5.0) * 0.22)
            elif part in ("tail", "tail_tuft", "tail_tuft_dark"):
                col = _shade(base, 0.88 if fur_v < 0.4 else 1.06)
            return _shade(col, fk)

    return colour


def workshop_look(look=None):
    """Entry point for tools/workshop.py snapshot/versions/serve."""
    mode = look or WORKSHOP_LOOK
    raw = build_bones()
    bones, rows, w, h = paint_faces(raw, TEXELS, make_painter(mode), world_coords=True)
    return bones, png(rows), w, h


def clips():
    """Returns the 19-clip African Buffalo animation dict (for workshop viewer & game export)."""
    import buffalo_anims
    all_clips, _ = buffalo_anims.build_clips(build_bones())
    return all_clips


WORKSHOP_CLIPS = clips
WORKSHOP_CLIP_INFO = {
    "notes": {
        "idle": "Heavy savanna breathing with natural blinks, classic buffalo rapid ear flutters, and fly-swatting tail.",
        "walk": "Heavy 4-beat lateral savanna walk (0.61 b/s, 100% ground-locked hooves) with casual blinks & ear flutter.",
        "trot": "Purposeful diagonal-pair herd trot (1.39 b/s).",
        "charge": "Thunderous African Buffalo locomotive gallop (2.45 b/s): head lowered, helmet boss forward, angry brows.",
        "headbutt": "Authentic bull headbutt: dips head & boss down low, then explodes UP and OUT with full neck/chest surge!",
        "charge_headbutt": "Chained attack: 2 thunderous charge strides into a braking plant & explosive down->up-and-out headbutt!",
        "gore": "Signature upward horn-hook toss: coils low to the side then explodes upward.",
        "paw_ground": "Territorial threat warning: low glare & two front-left hoof scrapes across the dirt.",
        "bellow": "Deep territorial bellow opening jaw 34 deg to reveal ridged palate, teeth & tongue.",
        "ear_flutter": "Classic African Buffalo rapid double ear-flutter with natural blink & tail flick against flies.",
        "snort": "Sharp warning huff: head snaps alert with rapid double chest-heave snort & ear flicks.",
        "horn_rub": "Savanna bull rubbing & polishing its massive curved horns side-to-side against the dirt.",
        "shake_off": "Travelling torsional dust/fly shake from nose & horns down to tail switch.",
        "graze": "Lowers muzzle cleanly to grass with rhythmic bovine cud-chewing, blinks & ear flutters.",
        "drink": "Braced watering-hole stance with calm sipping pulses & blinks.",
        "rest_down": "Slow, heavy, grounded bovine lie-down (2.4s): front knees lower onto grass, hind legs fold without floating.",
        "rest": "Relaxed folded-leg bovine ruminating loaf with slow breathing, cud chew, blinks & ear flutters.",
        "rest_up": "Slow, heavy bovine stand-up (2.4s): heaves hindquarters up first, then front legs one by one.",
        "step_up_1000": "Heavy 1-block ledge climb (16 px) played on the game's BlockMoves path.",
        "step_down_1000": "Careful 1-block ledge descent (16 px) lifting tail safely clear of the upper lip.",
    },
}


def build_body_plan(raw_bones, meta):
    """Builds savanna/src/main/resources/assets/antcolonies/body_plans/buffalo.json."""
    bnames = [b["name"] for b in raw_bones]
    switches = [
        ["idle", "walk"], ["walk", "idle"],
        ["walk", "trot"], ["trot", "walk"],
        ["trot", "charge"], ["charge", "trot"],
        ["charge", "charge_headbutt"], ["charge_headbutt", "idle"],
        ["idle", "charge_headbutt"],
        ["charge", "gore"], ["charge", "headbutt"],
        ["idle", "paw_ground"], ["paw_ground", "idle"],
        ["paw_ground", "charge"],
        ["idle", "bellow"], ["bellow", "idle"],
        ["idle", "gore"], ["gore", "idle"],
        ["idle", "headbutt"], ["headbutt", "idle"],
        ["idle", "ear_flutter"], ["ear_flutter", "idle"],
        ["idle", "snort"], ["snort", "idle"],
        ["idle", "horn_rub"], ["horn_rub", "idle"],
        ["idle", "shake_off"], ["shake_off", "idle"],
        ["idle", "graze"], ["graze", "idle"],
        ["idle", "drink"], ["drink", "idle"],
        ["idle", "mood_alert"], ["mood_alert", "idle"],
        ["idle", "mood_angry"], ["mood_angry", "idle"],
        ["mood_angry", "charge"],
        ["idle", "rest_down"], ["rest_down", "rest"], ["rest", "rest_up"], ["rest_up", "idle"],
        ["idle", "death"],
    ]
    return {
        "model": "buffalo",
        "about": "African Buffalo (Syncerus caffer) body plan (Upright Four Legs family): 3.83 x 2.28 x 2.29 blocks at scale 1.0 (16 px = 1 block).",
        "family": "upright_four_legs",
        "root": "root",
        "forebody": "chest",
        "head": {
            "bone": "head",
            "jaws": ["jaw"],
        },
        "chain": ["head", "neck", "chest", "hips", "tail_1", "tail_2", "tail_3"],
        "legs": {
            "l1": ["leg_fl_1", "leg_fl_2", "leg_fl_foot"],
            "r1": ["leg_fr_1", "leg_fr_2", "leg_fr_foot"],
            "l2": ["leg_bl_1", "leg_bl_2", "leg_bl_foot"],
            "r2": ["leg_br_1", "leg_br_2", "leg_br_foot"],
        },
        "bones": {n: n for n in bnames},
        "checks": {
            "about": "Offline checks for tools/clip_check.py (canonical adult scale 1.0: 1 px = 0.0625 blocks). Update controller once BuffaloEntity.java lands.",
            "px": 0.0625,
            "blend": 5,
            "feet_parts": ["foot"],
            "feet_free": [
                "walk", "trot", "charge", "charge_headbutt",
                "paw_ground", "gore",
                "rest_down", "rest", "rest_up", "death",
                "step_up_1000", "step_down_1000",
            ],
            "tuck_ok": [
                "rest_down", "rest", "rest_up", "death",
                "step_up_1000", "step_down_1000",
            ],
            "body_bones": ["hips", "chest"],
            "walks": meta["walks"],
            "switches": switches,
        },
    }


def export_game():
    """Exports buffalo.geo.json (with game_faces), buffalo.png (approved Crafted Fur var_b),
    buffalo.animation.json (19 clips), body_plans/buffalo.json, and block_moves/buffalo.json into savanna!"""
    import json
    import buffalo_anims
    import body_plan

    raw = build_bones()
    bones, rows, tw, th = paint_faces(raw, TEXELS, make_painter("var_b"), world_coords=True)
    all_clips, meta = buffalo_anims.build_clips(raw)

    write_assets({
        "geo/entity/buffalo.geo.json": geo("buffalo", bones, tw, th, (4.8, 2.8, [0, 1.2, 0])),
        "textures/entity/buffalo.png": png(rows),
        "animations/entity/buffalo.animation.json": animations(all_clips),
    }, mod="savanna", faces=True)

    plan_data = build_body_plan(raw, meta)
    body_plan.write_plan("buffalo", plan_data)

    move_specs = [
        {"name": "up", "clip": lambda s: "step_up_1000", "ahead_px": meta["step_up_ahead"],
         "starts_afloat": False, "ends_afloat": False, "goes_down": False, "end_level": 1, "wades": False},
        {"name": "down", "clip": lambda s: "step_down_1000", "ahead_px": meta["step_down_ahead"],
         "starts_afloat": False, "ends_afloat": False, "goes_down": True, "end_level": -1, "wades": False},
    ]
    write_block_moves("buffalo", block_moves(all_clips, [1.0], lambda s: 16.0 / s, move_specs, lead=4),
                      "tools/buffalo_models.py game")
    print(f"Exported African Buffalo to savanna: {len(raw)} bones, {sum(len(b['cubes']) for b in raw)} boxes, "
          f"{tw}x{th} Crafted Fur texture, {len(all_clips)} clips, body_plan + block_moves.")


def audit():
    """Geometry, mouth-seal & sanity audit: bounds, minY, box budget, bone count, parent integrity."""
    bones = build_bones()
    n_bones = len(bones)
    n_cubes = sum(len(b.get("cubes", [])) for b in bones)
    n_rot = sum(1 for b in bones for c in b.get("cubes", []) if c.get("rotation"))
    xs, ys, zs = [], [], []
    body_xs = []
    bad_parents = []
    bnames = {b["name"] for b in bones}
    mouth_parts = {"mouth_palate", "mouth_gum", "tongue", "throat_dark", "teeth"}
    mouth_leaks = []
    for b in bones:
        if "parent" in b and (b["parent"] is None or b["parent"] not in bnames):
            bad_parents.append((b["name"], b["parent"]))
        for c in b.get("cubes", []):
            ox, oy, oz_ = c["origin"]
            sx, sy, sz = c["size"]
            part = c.get("_part", "")
            for dx in (0, sx):
                for dy in (0, sy):
                    for dz in (0, sz):
                        px, py, pz = world_point(c, ox + dx, oy + dy, oz_ + dz)
                        xs.append(px); ys.append(py); zs.append(pz)
                        if not b["name"].startswith("tail") and not part.startswith("horn"):
                            body_xs.append(px)
                        if part in mouth_parts:
                            if abs(px) > 3.10 or py < 18.4 or py > 22.4 or pz < oz(59.6) or pz > oz(49.2):
                                mouth_leaks.append((part, round(px, 2), round(py, 2), round(pz, 2)))
    L = max(zs) - min(zs)
    H = max(ys) - min(ys)
    W = max(xs) - min(xs)
    BW = max(body_xs) - min(body_xs)
    print(f"African Buffalo (Stage 5 Ready · Crafted Fur var_b):")
    print(f"  Bones: {n_bones} | Boxes: {n_cubes} / 400 budget ({n_rot} rotated) | Bad parents: {bad_parents}")
    print(f"  Full bounds:  L={L:.2f} px ({L/16:.2f} b)  H={H:.2f} px ({H/16:.2f} b)  W_horns={W:.2f} px ({W/16:.2f} b)")
    print(f"  Body width:   W_body={BW:.2f} px ({BW/16:.2f} b)  |  minY={min(ys):.3f} px  maxY={max(ys):.2f} px")
    print(f"  Ratios:       L/H={L/H:.3f} (ref side 1.697)  |  W_horns/H={W/H:.3f} (ref front 0.992)")
    print(f"  Mouth seal:   {len(mouth_leaks)} leaks when shut (0 required)")
    assert not bad_parents, f"Invalid bone parents: {bad_parents}"
    assert not mouth_leaks, f"Mouth interior pokes out when shut: {mouth_leaks[:5]}"
    assert abs(min(ys)) < 0.02, f"Hooves not flush on y=0! minY={min(ys)}"
    assert n_cubes <= 400, f"Over 400 large-animal box budget: {n_cubes}"


if __name__ == "__main__":
    if "game" in sys.argv[1:]:
        audit()
        export_game()
    else:
        audit()
