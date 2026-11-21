"""The Hippopotamus model maker (M9 river zones & savanna life; first Upright Four Legs animal, 2026-11-21).

Blended from the user's two inspiration sheets (references/inspiration/hippo.png):
- Cleaner blocky stepped silhouette, head/brow/eyes and sturdy pillar legs from hippo_smooth.jpg;
- Wide barrel top view, deep chunky open-mouth interior (tongue + 4 tusks) and mud/terracotta-pink palette from
  hippo_blocky.jpg.

Size (the user's call, 2026-11-21): 5.0 blocks long (80 model px) x 2.0 blocks tall at the back dome (32 model px)
x ~1.94 blocks wide across the barrel (31 model px), drawn at scale 1.0 (16 model px = 1 block).
Units are model pixels: head toward -Z, up +Y, left +X, ground y = 0, middle line x = 0.
Outline x (0 = tail tip, 80 = snout tip) converts to model z via oz(ox) = 40.0 - ox.

    python tools/workshop.py snapshot hippo --note "..."
    python tools/workshop.py versions hippo
    python tools/hippo_models.py game
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import cube, curve, mix, paint_faces, png, geo, animations, clip, write_assets  # noqa: E402
from motion_kit import two_bone, stride, ease, pulse, ramp  # noqa: E402

# --- workshop sliders (the main shape numbers, editable in tools/workshop.py serve hippo) -----------------------------
LENGTH = 80.0           # 5.0 blocks
HEIGHT = 32.0           # 2.0 blocks at the back dome
BARREL_HW = 15.5        # 31 px = 1.94 blocks wide across the belly barrel
MUZZLE_LEN = 11.0       # snout length forward of head
MUZZLE_HW = 10.0        # snout half-width
LEG_H = 13.0            # pillar leg height up into belly
JAW_OPEN = 0.0          # preview-only jaw opening degrees (0 shut, 110 threat yawn)

WORKSHOP_SHAPE = [
    ("HEIGHT",     "Back height (px)",    28.0, 38.0, 1.0),
    ("BARREL_HW",  "Barrel half-width",   13.0, 18.0, 0.5),
    ("MUZZLE_LEN", "Muzzle length",        8.0, 14.0, 0.5),
    ("MUZZLE_HW",  "Muzzle half-width",    8.0, 12.0, 0.5),
    ("LEG_H",      "Leg height",          10.0, 16.0, 0.5),
    ("JAW_OPEN",   "Preview yawn (deg)",   0.0, 125.0, 5.0),
]

WORKSHOP_PARTS = {
    "head":       ["head", "ear_left", "ear_right", "eye_left", "eye_right", "lid_left", "lid_right"],
    "jaw":        ["jaw", "tongue"],
    "neck":       ["neck"],
    "chest":      ["chest"],
    "hips":       ["hips"],
    "tail":       ["tail_1", "tail_2", "tail_3"],
    "front legs": ["leg_fl_1", "leg_fr_1"],
    "hind legs":  ["leg_bl_1", "leg_br_1"],
}

WORKSHOP_LOOK = "bull"
EYE_STYLE = "b"         # approved by user 2026-11-21: "b" River-Boss Amber ("a" warm hazel, "c" classic, "v3" original)


def oz(ox):
    """Outline x (0 tail .. 80 snout) -> GeckoLib model z (+40 tail .. -40 snout)."""
    return LENGTH / 2.0 - ox


def box(cubes, x0, x1, y0, y1, ox0, ox1, part, extra=None):
    """Adds a box using outline x [ox0..ox1] (tail->snout), height [y0..y1], width [x0..x1]."""
    lo = [min(x0, x1), min(y0, y1), min(oz(ox0), oz(ox1))]
    hi = [max(x0, x1), max(y0, y1), max(oz(ox0), oz(ox1))]
    if hi[0] - lo[0] < 0.05 or hi[1] - lo[1] < 0.05 or hi[2] - lo[2] < 0.05:
        return None
    c = cube(lo, hi, part)
    if extra:
        c.update(extra)
    cubes.append(c)
    return c


def sym_box(cubes, hw, y0, y1, ox0, ox1, part, extra=None):
    return box(cubes, -hw, hw, y0, y1, ox0, ox1, part, extra)


# --- body profile (scaled cleanly with HEIGHT and BARREL_HW) ----------------------------------------------------------
def _scale_y(y):
    """Maps canonical 32-px-tall profile heights onto current HEIGHT while keeping belly clearance steady."""
    belly = max(5.5, LEG_H - 6.0)
    if y <= 7.0:
        return y * (belly / 7.0)
    return belly + (y - 7.0) * ((HEIGHT - belly) / (32.0 - 7.0))


def _barrel_slice(cubes, ox0, ox1, y_bot, y_top, hw, part_body="hide", part_belly="pink_belly", ridge=True,
                  belly_hw=None, hide_bot=None):
    """One clean stepped-Minecraft barrel segment (4 vertical tiers -> rounded front silhouette, ~4 boxes).
    belly_hw lets leg-well slices tuck the pink underbelly cleanly between the legs while keeping the dark hide flank full!"""
    yb, yt = _scale_y(y_bot), _scale_y(y_top)
    h = yt - yb
    if h < 2.0:
        return
    # Tier 1: dusty-pink rounded belly keel underneath
    hb = _scale_y(hide_bot) if hide_bot is not None else yb + max(2.5, h * 0.16)
    t1 = max(yb + 1.8, hb)
    bhw = round(hw * 0.80, 2) if belly_hw is None else round(belly_hw, 2)
    sym_box(cubes, bhw, yb, t1 + 0.3, ox0, ox1, part_belly)
    # Tier 2: wide chunky main barrel flank
    t2 = yb + h * 0.76
    sym_box(cubes, round(hw, 2), t1 - 0.5, t2, ox0, ox1, part_body)
    # Tier 3: stepped upper shoulder / back bevel
    t3 = yb + h * 0.91 if ridge else yt
    sym_box(cubes, round(hw * 0.82, 2), t2 - 0.5, t3, ox0, ox1, part_body)
    # Tier 4: narrow spine crown ridge (matches hippo_smooth's clean stepped top!)
    if ridge and yt - t3 >= 1.0:
        sym_box(cubes, round(hw * 0.58, 2), t3 - 0.4, yt, ox0, ox1, "hide_top")


def build_bones():
    """Builds the full hippopotamus rig (~160-200 boxes, well under the 400 large-animal budget)."""
    w_scale = BARREL_HW / 15.5
    s_ox_end = 69.0 + MUZZLE_LEN            # canonical 80.0
    m_hw = MUZZLE_HW
    # Keep head heights proportional to HEIGHT
    hy = lambda y: _scale_y(y)

    # --- HIPS (rear half of the massive barrel: ox 2.5..36) -----------------------------------------------------------
    # Pink underbelly narrows cleanly inside the hind-leg well (ox 6..18.5, inner face x=5.6) and flares smoothly
    # into the full plump dusty-pink mid-belly (ox 21..36) so hind legs never slice through pink belly boxes!
    hips_cubes = []
    # Rump rear cap (rounded back curve)
    _barrel_slice(hips_cubes, 2.5, 6.0, 10.8, 25.5, 10.5 * w_scale, ridge=False, belly_hw=5.3 * w_scale, hide_bot=12.6)
    _barrel_slice(hips_cubes, 6.0, 10.0, 9.8, 28.5, 13.0 * w_scale, ridge=True,  belly_hw=5.2 * w_scale, hide_bot=12.0)
    # Hind-leg well (ox 10..19.5): pink keel tucked cleanly between left & right hind legs (x <= 5.2 < leg inner 5.6)
    _barrel_slice(hips_cubes, 10.0, 19.5, 9.4, 30.8, 14.6 * w_scale, ridge=True, belly_hw=5.2 * w_scale, hide_bot=11.5)
    # Groin curve transition into full mid-belly (ox 19.5..22.5)
    _barrel_slice(hips_cubes, 19.5, 22.5, 8.2, 31.8, 15.2 * w_scale, ridge=True, belly_hw=8.2 * w_scale, hide_bot=11.1)
    # Rear mid-belly dome (widest & tallest — full plump dusty-pink keel hanging cleanly between front & hind legs!)
    _barrel_slice(hips_cubes, 22.5, 36.0, 7.2, 32.0, 15.5 * w_scale, ridge=True, belly_hw=11.2 * w_scale, hide_bot=11.0)
    # Subtle muscular hip bulges over hind legs (gives Image 1's chunky sculpted flank, cleanly above knee!)
    for s in (1, -1):
        box(hips_cubes, s * (13.0 * w_scale), s * (15.8 * w_scale),
            hy(11.4), hy(22.5), 6.5, 15.5, "hide")

    # --- TAIL (3 bones: muscular dock -> laterally-flattened hippo paddle -> dark coarse-bristled fan tip!) ----------
    t1_cubes, t2_cubes, t3_cubes = [], [], []
    # Base dock rooted seamlessly into rump so wide whipping never leaves a gap
    sym_box(t1_cubes, 2.0, hy(18.6), hy(22.6), 1.8, 5.2, "hide")
    sym_box(t1_cubes, 1.55, hy(15.6), hy(20.6), 0.2, 3.2, "hide")
    # Middle paddle: flatter side-to-side, broader front-to-back like a real hippo oar/propeller
    sym_box(t2_cubes, 1.15, hy(11.4), hy(16.2), -0.6, 2.5, "tail_paddle")
    # Bristled fan tip: dark coarse hippo tail-hair fringe
    sym_box(t3_cubes, 0.85, hy(8.5), hy(11.8), -0.8, 2.2, "tail_bristle")
    sym_box(t3_cubes, 0.55, hy(7.2), hy(10.6), -1.6, 2.8, "tail_bristle")

    # --- CHEST (front half of the barrel + massive shoulders: ox 34.2..57.5; 1.8 px seamless overlap into hips!) ------
    chest_cubes = []
    # Front half of plump mid-belly (between the legs)
    _barrel_slice(chest_cubes, 34.2, 39.5, 7.4, 31.65, 15.05 * w_scale, ridge=True, belly_hw=11.0 * w_scale, hide_bot=11.1)
    # Armpit transition curve tapering behind front legs (ox 39.5..42.5)
    _barrel_slice(chest_cubes, 39.5, 42.5, 8.4, 31.1, 14.6 * w_scale, ridge=True,   belly_hw=8.0 * w_scale, hide_bot=11.4)
    # Front-leg well (ox 42.5..54.5): pink keel tucked cleanly between left & right front legs (x <= 5.2 < leg inner 5.6)
    _barrel_slice(chest_cubes, 42.5, 54.5, 9.5, 30.5, 14.2 * w_scale, ridge=True,   belly_hw=5.2 * w_scale, hide_bot=11.8)
    # Front brisket ahead of front legs (ox 54.5..57.5: stays tucked inside front-leg swing envelope!)
    _barrel_slice(chest_cubes, 54.5, 57.5, 10.4, 28.6, 12.2 * w_scale, ridge=True,  belly_hw=5.3 * w_scale, hide_bot=12.6)
    # Chunky shoulder muscle pads over front legs
    for s in (1, -1):
        box(chest_cubes, s * (12.2 * w_scale), s * (14.8 * w_scale),
            hy(11.5), hy(23.5), 45.0, 54.2, "hide")

    # --- NECK (thick muscular neck + dusty-pink skin folds: ox 56..63.5) ----------------------------------------------
    neck_cubes = []
    # Main dark upper neck
    sym_box(neck_cubes, 9.5 * w_scale, hy(16.0), hy(27.2), 56.5, 63.5, "hide")
    sym_box(neck_cubes, 7.6 * w_scale, hy(26.5), hy(28.0), 56.5, 62.0, "hide_top")
    # Dusty-pink muscular neck/throat roll (iconic hippo jowl crease from Image 1 & 2, clear of front leg forward swing!)
    sym_box(neck_cubes, 10.2 * w_scale, hy(11.8), hy(18.0), 57.0, 63.2, "pink_fold")
    sym_box(neck_cubes, 8.2 * w_scale, hy(10.8), hy(12.6), 57.0, 62.5, "pink_belly")

    # --- HEAD (skull, cheeks, carved eye sockets, stepped brows, broad blocky upper muzzle, nostril bumps) ------------
    head_cubes = []
    # Lower skull & heavy cheeks (stops cleanly at y=23.65 BELOW the 3-row eye sockets so nothing ever covers the eyes!)
    sym_box(head_cubes, 9.5, hy(15.2), hy(23.65), 61.5, 70.0, "head_hide")
    # Rear upper skull behind the eye sockets (ox 61.5..66.2)
    sym_box(head_cubes, 9.5, hy(23.6), hy(26.3), 61.5, 66.2, "head_hide")
    # Narrower forehead bridge BETWEEN the two eye sockets (x -6.0..+6.0 -> eyes sit in real 3D carved sockets!)
    sym_box(head_cubes, 6.0, hy(23.6), hy(26.4), 66.0, 69.6, "head_hide")
    sym_box(head_cubes, 6.0, hy(26.2), hy(27.5), 62.0, 69.2, "hide_top")
    # Dusty-pink cheek / jowl cushion at rear sides of skull (matches Image 1!)
    for s in (1, -1):
        box(head_cubes, s * 7.8, s * 10.2, hy(14.0), hy(22.5), 61.5, 66.5, "pink_fold")
    # Stepped brow ridges directly over the eyes (0.1 px proud of lid so full 3-row lid hides 100% inside at rest!)
    for s in (1, -1):
        box(head_cubes, s * 4.2, s * 9.85, hy(26.3), hy(29.0), 64.5, 69.8, "hide_top")
        # Pink fleshy cushion cleanly behind & below eye socket (never overlaps eye!)
        box(head_cubes, s * 6.5, s * 9.75, hy(22.0), hy(26.25), 64.0, 66.15, "pink_eye")
        box(head_cubes, s * 5.2, s * 9.65, hy(22.0), hy(23.65), 65.8, 69.7, "pink_eye")
    # Broad, heavy rectangular upper muzzle / snout (ox 69..s_ox_end)
    # Main upper muzzle block (flares cleanly wider than neck & cheeks!)
    sym_box(head_cubes, m_hw, hy(14.5), hy(22.0), 69.0, s_ox_end, "muzzle")
    # Slightly narrower upper snout bridge stepping up toward brow
    sym_box(head_cubes, m_hw - 1.2, hy(21.5), hy(23.5), 68.8, s_ox_end - 2.0, "muzzle_top")
    # Subtle side lip bulges (heavy hippo whisker pads from Image 1 & 2!)
    for s in (1, -1):
        box(head_cubes, s * (m_hw - 1.5), s * (m_hw + 0.6), hy(14.5), hy(20.5), 71.5, s_ox_end - 0.5, "muzzle")
    # Raised nostril bumps (aligned high on the snout >= 21.5 px waterline!)
    for s in (1, -1):
        nx0, nx1 = s * 3.0, s * 7.0
        box(head_cubes, nx0, nx1, hy(21.5), hy(24.2), s_ox_end - 6.0, s_ox_end - 1.2, "muzzle_top")
        # Dark recessed nostril opening on front-upper face
        box(head_cubes, s * 3.8, s * 6.0, hy(21.8), hy(23.6), s_ox_end - 1.5, s_ox_end - 0.9, "nostril")

    # Upper mouth interior (pink palate roof + dark throat wall + 2 chunky upper tusks — hidden when shut!)
    # Inset cleanly by >= 1.5 px inside upper lip so ZERO pink shows when mouth is closed!
    sym_box(head_cubes, m_hw - 2.0, hy(14.6), hy(15.8), 64.5, s_ox_end - 2.2, "mouth_roof")
    # Dark throat cavity wall at the back of the mouth
    sym_box(head_cubes, 6.2, hy(13.2), hy(20.5), 62.5, 65.2, "throat_dark")
    # Two thick square ivory upper tusks (positioned cleanly inside the shut jaw!)
    for s in (1, -1):
        box(head_cubes, s * 4.2, s * 6.6, hy(12.2), hy(15.2), s_ox_end - 5.2, s_ox_end - 2.8, "tusk")

    # --- EYES & EYELIDS (own bones! 3x3 pixel-art sockets + full-height lids hidden in brow at rest!) -----------------
    eye_l_cubes, eye_r_cubes = [], []
    lid_l_cubes, lid_r_cubes = [], []
    ey0, ey1 = hy(23.7), hy(26.3)   # 2.6 px -> rounds to 3 crisp Minecraft pixel rows!
    ez_ox0, ez_ox1 = 66.2, 69.5     # 3.3 px -> 3 crisp columns front & side
    for s, ec, lc in ((1, eye_l_cubes, lid_l_cubes), (-1, eye_r_cubes, lid_r_cubes)):
        ex_in, ex_out = 6.2, 9.6
        x0, x1 = (ex_in, ex_out) if s > 0 else (-ex_out, -ex_in)
        eye_info = {
            "s": s,
            "x_in": s * ex_in,
            "y1": ey1,
            "z0": oz(ez_ox1),
            "w_front": ex_out - ex_in,
            "w_outer": ez_ox1 - ez_ox0,
            "h": ey1 - ey0,
        }
        box(ec, x0, x1, ey0, ey1, ez_ox0, ez_ox1, "eye", extra={"_eye": eye_info})
        # Full-height eyelid tucked 100% inside brow at rest; +0.15 px outside eye so dropping -2.65 px covers eye cleanly!
        lx0, lx1 = (ex_in - 0.1, ex_out + 0.15) if s > 0 else (-(ex_out + 0.15), -(ex_in - 0.1))
        box(lc, lx0, lx1, ey1 + 0.05, ey1 + 2.65, ez_ox0 - 0.05, ez_ox1 + 0.15, "hide_top")

    # --- EARS (own bones! Small upright ears with dusty-pink inner cups, perched high!) -------------------------------
    ear_l_cubes, ear_r_cubes = [], []
    for s, ec in ((1, ear_l_cubes), (-1, ear_r_cubes)):
        # Outer dark ear rim
        box(ec, s * 5.0, s * 8.6, hy(26.2), hy(31.2), 59.5, 62.2, "hide_top")
        # Dusty-pink recessed inner ear facing forward
        box(ec, s * 5.7, s * 7.9, hy(27.0), hy(30.3), 61.9, 62.5, "pink_ear")

    # --- LOWER JAW & TONGUE (own bones! Pivoted far back for a huge 120-140 deg threat yawn!) -------------------------
    jaw_pivot_ox = 63.5
    jaw_pivot_y = hy(14.5)
    jaw_cubes = []
    # Main dusty-pink lower jaw & chin (narrower than upper muzzle so upper lip completely hides mouth seam when shut!)
    jaw_hw = m_hw - 1.5
    sym_box(jaw_cubes, jaw_hw, hy(10.8), hy(14.5), 63.0, s_ox_end - 1.5, "pink_jaw")
    # Darker outer lip edge along front and sides of lower jaw
    sym_box(jaw_cubes, jaw_hw + 0.2, hy(12.2), hy(14.4), s_ox_end - 3.2, s_ox_end - 1.2, "muzzle")
    for s in (1, -1):
        box(jaw_cubes, s * (jaw_hw - 1.2), s * (jaw_hw + 0.1), hy(12.5), hy(14.5), 65.5, s_ox_end - 2.0, "jaw_rim")
    # Pink mouth floor inside lower jaw (inset so never leaks outside when shut)
    sym_box(jaw_cubes, jaw_hw - 1.5, hy(13.8), hy(14.7), 64.5, s_ox_end - 2.5, "mouth_floor")
    # Two HUGE chunky ivory lower tusks (famous hippo canines! Hidden inside muzzle when shut, fierce when open!)
    for s in (1, -1):
        box(jaw_cubes, s * 4.0, s * 6.4, hy(14.2), hy(18.6), s_ox_end - 5.2, s_ox_end - 2.8, "tusk")
    # Two smaller lower front incisors between the tusks (detail from Image 2 Open Mouth!)
    for s in (1, -1):
        box(jaw_cubes, s * 1.1, s * 2.5, hy(14.4), hy(16.6), s_ox_end - 4.4, s_ox_end - 3.0, "tusk")

    # Chunky pink tongue (own bone, parent jaw!)
    tongue_cubes = []
    sym_box(tongue_cubes, 3.8, hy(14.5), hy(16.4), 65.5, s_ox_end - 6.0, "tongue")
    sym_box(tongue_cubes, 3.2, hy(14.5), hy(16.1), s_ox_end - 6.0, s_ox_end - 4.8, "tongue")

    # --- FOUR UPRIGHT 3-BONE PILLAR LEGS (upper -> lower -> foot with 3 pale ivory toenails!) -------------------------
    def make_leg(prefix, ox_mid, side):
        """Builds a chunky 3-bone upright pillar leg (upper, lower, foot) set cleanly on the outer lower flank."""
        cx = side * 9.6 * w_scale
        cz = oz(ox_mid)
        y_hip = hy(18.0)
        y_knee = 7.5
        y_ankle = 2.6

        # Upper leg (extends deep inside dark hide flank to y_hip+2.0 and cleanly just past knee)
        u_cubes = []
        box(u_cubes, cx - 4.0, cx + 4.0, y_knee - 1.1, y_hip + 2.0, ox_mid - 4.0, ox_mid + 4.0, "leg")

        # Lower leg (clean square pillar + inset knee & ankle joint plugs so bending NEVER gaps or juts!)
        l_cubes = []
        box(l_cubes, cx - 3.7, cx + 3.7, y_ankle + 0.8, y_knee + 0.9, ox_mid - 3.7, ox_mid + 3.7, "leg")
        # Inset knee joint plug (hidden inside upper leg at rest; seals knee smoothly without poking corners!)
        box(l_cubes, cx - 3.1, cx + 3.1, y_knee - 0.2, y_knee + 1.8, ox_mid - 3.1, ox_mid + 3.1, "leg")
        # Inset ankle joint plug (narrower & raised slightly so tilting in walk OR splayed rest stays above ground!)
        box(l_cubes, cx - 2.5, cx + 2.5, y_ankle + 0.1, y_ankle + 2.1, ox_mid - 2.4, ox_mid + 2.4, "leg")

        # Broad foot pad + inset ankle sleeve (hidden inside shin at rest; bridges wedges smoothly!)
        f_cubes = []
        box(f_cubes, cx - 3.9, cx + 3.9, 0.0, y_ankle + 1.3, ox_mid - 4.0, ox_mid + 4.0, "foot")
        box(f_cubes, cx - 3.45, cx + 3.45, y_ankle + 0.6, y_ankle + 2.5, ox_mid - 3.45, ox_mid + 3.45, "leg")
        for nx_off in (-2.4, 0.0, 2.4):
            box(f_cubes, cx + nx_off - 0.9, cx + nx_off + 0.9, 0.0, 2.0,
                ox_mid + 3.2, ox_mid + 4.25, "nail")
        # Outer side nail accent (like Image 1's 3/4 view)
        box(f_cubes, cx + side * 3.0, cx + side * 4.05, 0.0, 2.0, ox_mid - 0.5, ox_mid + 1.6, "nail")

        return [
            {"name": f"{prefix}_1", "parent": "chest" if ox_mid > 35 else "hips",
             "pivot": [round(cx, 2), round(y_hip, 2), round(cz, 2)], "cubes": u_cubes},
            {"name": f"{prefix}_2", "parent": f"{prefix}_1",
             "pivot": [round(cx, 2), round(y_knee, 2), round(cz, 2)], "cubes": l_cubes},
            {"name": f"{prefix}_foot", "parent": f"{prefix}_2",
             "pivot": [round(cx, 2), round(y_ankle, 2), round(cz, 2)], "cubes": f_cubes},
        ]

    # --- Assemble full bone tree --------------------------------------------------------------------------------------
    bones = [
        {"name": "root", "pivot": [0, 0, 0], "cubes": []},
        {"name": "hips", "parent": "root", "pivot": [0, round(hy(18.0), 2), round(oz(24.0), 2)], "cubes": hips_cubes},
        {"name": "tail_1", "parent": "hips", "pivot": [0, round(hy(21.4), 2), round(oz(3.2), 2)], "cubes": t1_cubes},
        {"name": "tail_2", "parent": "tail_1", "pivot": [0, round(hy(15.9), 2), round(oz(1.4), 2)], "cubes": t2_cubes},
        {"name": "tail_3", "parent": "tail_2", "pivot": [0, round(hy(11.5), 2), round(oz(0.8), 2)], "cubes": t3_cubes},
        {"name": "chest", "parent": "hips", "pivot": [0, round(hy(18.5), 2), round(oz(36.0), 2)], "cubes": chest_cubes},
        {"name": "neck", "parent": "chest", "pivot": [0, round(hy(19.0), 2), round(oz(56.0), 2)], "cubes": neck_cubes},
        {"name": "head", "parent": "neck", "pivot": [0, round(hy(19.5), 2), round(oz(62.5), 2)], "cubes": head_cubes},
        {"name": "ear_left",  "parent": "head", "pivot": [6.8, round(hy(26.5), 2), round(oz(61.0), 2)], "cubes": ear_l_cubes},
        {"name": "ear_right", "parent": "head", "pivot": [-6.8, round(hy(26.5), 2), round(oz(61.0), 2)], "cubes": ear_r_cubes},
        {"name": "eye_left",  "parent": "head", "pivot": [7.8, round((ey0 + ey1) / 2, 2), round(oz(67.5), 2)], "cubes": eye_l_cubes},
        {"name": "eye_right", "parent": "head", "pivot": [-7.8, round((ey0 + ey1) / 2, 2), round(oz(67.5), 2)], "cubes": eye_r_cubes},
        {"name": "lid_left",  "parent": "head", "pivot": [7.8, round(ey1, 2), round(oz(67.5), 2)], "cubes": lid_l_cubes},
        {"name": "lid_right", "parent": "head", "pivot": [-7.8, round(ey1, 2), round(oz(67.5), 2)], "cubes": lid_r_cubes},
        {"name": "jaw", "parent": "head", "pivot": [0, round(jaw_pivot_y, 2), round(oz(jaw_pivot_ox), 2)],
         "rotation": [round(JAW_OPEN, 2), 0, 0] if JAW_OPEN else [0, 0, 0], "cubes": jaw_cubes},
        {"name": "tongue", "parent": "jaw", "pivot": [0, round(hy(14.8), 2), round(oz(66.5), 2)], "cubes": tongue_cubes},
    ]
    bones += make_leg("leg_fl", 50.0,  1)
    bones += make_leg("leg_fr", 50.0, -1)
    bones += make_leg("leg_bl", 10.5,  1)
    bones += make_leg("leg_br", 10.5, -1)
    return bones


# --- Minecraft-style pixel painting (mud / grey-terracotta hide, dusty terracotta-pink underside, speckled muzzle) ----
def _hash(x, y, z):
    """Deterministic pixel noise in [-1, 1] on a 1-model-pixel Minecraft grid."""
    ix, iy, iz = int(math.floor(x + 0.01)), int(math.floor(y + 0.01)), int(math.floor(z + 0.01))
    n = (ix * 374761393 + iy * 668265263 + iz * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (((n ^ (n >> 16)) & 0xFFFF) / 32767.5) - 1.0


def _jitter(rgb, x, y, z, amt=9.0):
    n = _hash(x, y, z) * amt
    return (rgb[0] + n, rgb[1] + n * 0.92, rgb[2] + n * 0.96)


# Core palette sampled directly from the user's two inspiration sheets!
C_HIDE_DARK  = (82, 66, 72)      # upper back / spine (dark muddy slate-plum)
C_HIDE_MID   = (102, 83, 89)     # main flanks & legs (warm grey-brown terracotta)
C_HIDE_LIGHT = (116, 94, 100)    # sunlit flank highlights
C_MUZZLE     = (95, 78, 84)      # warm slate-mud snout (bright enough for nostrils to pop!)
C_MUZZLE_DOT = (68, 55, 60)      # subtle speckled pore dots on front of snout
C_NOSTRIL    = (36, 28, 32)      # recessed nostril hole
C_PINK_BELLY = (162, 110, 114)   # dusty terracotta-pink underbelly & throat
C_PINK_FOLD  = (146, 98, 102)    # slightly deeper dusty-pink neck/cheek crease
C_PINK_EAR   = (186, 122, 124)   # warm pink inner ear
C_PINK_EYE   = (156, 104, 108)   # fleshy pink cushion behind/below eye
C_MOUTH_ROOF = (182, 104, 108)   # fleshy rose-pink palate
C_MOUTH_FLR  = (194, 116, 118)   # warm pink mouth floor/gums
C_TONGUE     = (210, 122, 128)   # chunky pink tongue
C_TONGUE_MID = (176, 94, 102)    # darker tongue groove
C_THROAT     = (44, 22, 28)      # deep dark throat inside open mouth
C_TUSK       = (238, 228, 206)   # warm bone-ivory tusks
C_TUSK_BASE  = (208, 192, 166)   # slightly darker ivory root
C_NAIL       = (194, 184, 166)   # warm bone-grey toenails
C_EYE_BLACK  = (18, 16, 20)      # jet-black Minecraft eye
C_EYE_GLINT  = (244, 244, 248)   # crisp white highlight pixel!


# Expressive big-mammal eye variants: one unbroken 6x3 pixel strip across the front (cols 0..2, inner->outer)
# and outer side face (cols 3..5, front->back), rows 0..2 top->bottom (bullfrog C2 wrapped-corner rule!).
EYE_PAL = {
    "P": (20, 16, 20),       # deep espresso/black pupil
    "G": (248, 246, 242),    # bright white catchlight glint
    "C": (222, 232, 244),    # soft cool catchlight glint
    "W": (232, 224, 214),    # warm cream sclera
    "B": (240, 238, 234),    # crisp classic Minecraft white sclera
    "I": (118, 76, 56),      # warm chestnut-hazel iris
    "A": (196, 134, 62),     # glowing river-boss amber-gold iris
    "H": (152, 96, 46),      # deeper warm amber iris edge
    "D": (58, 44, 50),       # hooded upper-lid shadow under brow
    "R": (166, 112, 116),    # soft dusty-pink lower eye rim
    "K": (152, 102, 106),    # fleshy pink eye-socket skin
}

EYE_STYLES = {
    # A: Soulful warm mammal — cream sclera on inner edge, rich espresso pupil + warm hazel iris + corner glint
    "a": {
        "front": ["WPG", "WPP", "RIP"],
        "outer": ["GPK", "PIK", "IRK"],
    },
    # B: Territorial river boss — hooded dark brow shadow, glowing warm amber-gold iris + sharp pupil + ivory glint
    "b": {
        "front": ["DDD", "APG", "HPP"],
        "outer": ["DDK", "GPA", "PHR"],
    },
    # C: Expressive Minecraft classic — bold white sclera + big glossy dark pupil + clean catchlight (high readability!)
    "c": {
        "front": ["BCP", "BPP", "BPP"],
        "outer": ["PPK", "PPK", "PRK"],
    },
    # v3: Original dark square eye with corner white glint
    "v3": {
        "front": ["PPG", "PPP", "PPP"],
        "outer": ["GPK", "PPK", "PPK"],
    },
}


def paint_colour(c, face_, x, y, z):
    part = c["_part"]
    ox = LENGTH / 2.0 - z

    # Crisp Minecraft wrapped-corner eye (one unbroken art strip across front + outer faces!)
    if part == "eye":
        e = c["_eye"]
        style = EYE_STYLES.get(EYE_STYLE, EYE_STYLES["a"])
        outer = "east" if e["s"] > 0 else "west"
        row = max(0, min(2, int(math.floor((e["y1"] - y) / (e["h"] / 3.0)))))
        if face_ == "north":
            col = max(0, min(2, int(math.floor(((x - e["x_in"]) * e["s"]) / (e["w_front"] / 3.0)))))
            return EYE_PAL[style["front"][row][col]]
        if face_ == outer:
            col = max(0, min(2, int(math.floor((z - e["z0"]) / (e["w_outer"] / 3.0)))))
            return EYE_PAL[style["outer"][row][col]]
        return _jitter(C_HIDE_DARK, x, y, z, 6.0)

    if part == "nostril":
        return _jitter(C_NOSTRIL, x, y, z, 5.0)
    if part == "throat_dark":
        return _jitter(C_THROAT, x, y, z, 5.0)
    if part == "mouth_roof":
        return _jitter(C_MOUTH_ROOF, x, y, z, 8.0)
    if part == "mouth_floor":
        return _jitter(C_MOUTH_FLR, x, y, z, 8.0)
    if part == "tongue":
        base = C_TONGUE_MID if abs(x) < 0.7 else C_TONGUE
        return _jitter(base, x, y, z, 8.0)
    if part == "tusk":
        # Subtle warm root shading near gum line
        t = max(0.0, min(1.0, (y - c["origin"][1]) / max(0.1, c["size"][1])))
        base = mix(C_TUSK_BASE, C_TUSK, t if c["_bone"] == "jaw" else 1.0 - t)
        return _jitter(base, x, y, z, 5.0)
    if part == "nail":
        return _jitter(C_NAIL, x, y, z, 7.0)
    if part == "pink_ear":
        return _jitter(C_PINK_EAR, x, y, z, 7.0)
    if part == "pink_eye":
        return _jitter(C_PINK_EYE, x, y, z, 6.0)
    if part == "pink_belly":
        return _jitter(C_PINK_BELLY, x, y, z, 8.0)
    if part == "pink_jaw":
        if face_ == "up":
            return _jitter(C_MOUTH_FLR, x, y, z, 7.0)
        return _jitter(C_PINK_BELLY, x, y, z, 8.0)
    if part == "jaw_rim":
        return _jitter(mix(C_MUZZLE, C_PINK_FOLD, 0.35), x, y, z, 7.0)
    if part == "pink_fold":
        # Blend dusty pink smoothly up into dark hide near top of neck/cheek (no harsh line!)
        f = max(0.0, min(1.0, (y - _scale_y(16.0)) / 7.0))
        dither = 0.12 * _hash(x, y, z)
        base = mix(C_PINK_FOLD, C_HIDE_MID, max(0.0, min(1.0, f + dither)))
        return _jitter(base, x, y, z, 8.0)
    if part in ("muzzle", "muzzle_top"):
        # Warm mottled mud snout with subtle pore dots on the front/sides (matches Image 1 & 2!)
        base = C_MUZZLE if part == "muzzle" else mix(C_MUZZLE, C_HIDE_MID, 0.45)
        if face_ == "north" or (part == "muzzle" and ox > 74.5):
            if _hash(x * 1.7, y * 1.7, z * 1.7) > 0.56:
                return _jitter(C_MUZZLE_DOT, x, y, z, 5.0)
        return _jitter(base, x, y, z, 8.0)
    if part == "tail_paddle":
        # Smooth transition from hide at top of paddle to darker muscular edge at bottom
        f = max(0.0, min(1.0, (_scale_y(16.0) - y) / 5.0))
        base = mix(C_HIDE_MID, C_HIDE_DARK, 0.35 + 0.55 * f)
        if ox < 0.0 or ox > 1.8:
            base = mix(base, C_NOSTRIL, 0.28)
        return _jitter(base, x, y, z, 7.0)
    if part == "tail_bristle":
        # Coarse dark charcoal-brown hippo tail bristles with vertical hair-strand variation
        strand = _hash(x * 2.5, y * 0.4, z * 2.5)
        base = mix(C_NOSTRIL, C_HIDE_DARK, 0.25 + 0.20 * max(0.0, strand))
        return _jitter(base, x, y, z, 5.0)
    if part in ("leg", "foot"):
        # Slight dusty warmth on inner leg & darker ankle near ground
        f = max(0.0, min(1.0, y / max(1.0, LEG_H)))
        base = mix(C_HIDE_DARK, C_HIDE_MID, 0.35 + 0.55 * f)
        if abs(x) < 5.5 and y > 5.0:
            base = mix(base, C_PINK_FOLD, 0.25)
        return _jitter(base, x, y, z, 8.0)

    # Main hide / head_hide / hide_top: rich Minecraft mud/terracotta shading from dark spine to warm flank + soft pink lower edge
    h_frac = max(0.0, min(1.0, (y - _scale_y(8.0)) / max(1.0, HEIGHT - _scale_y(8.0))))
    if part == "hide_top" or face_ == "up":
        base = mix(C_HIDE_MID, C_HIDE_DARK, 0.55 + 0.35 * h_frac)
    else:
        base = mix(C_HIDE_LIGHT, C_HIDE_DARK, h_frac * 0.72)
    # Soft dappled pink blush right above the belly band (organic transition, no harsh horizontal stripe!)
    if part == "hide" and y < _scale_y(12.2):
        blush = max(0.0, min(1.0, (_scale_y(12.2) - y) / 3.0 + 0.25 * _hash(x, y, z)))
        base = mix(base, C_PINK_FOLD, blush * 0.55)
    # Very subtle vertical skin crease lines behind shoulder & front of hip
    if part == "hide" and (abs(ox - 43.0) < 0.6 or abs(ox - 16.5) < 0.6) and y < _scale_y(24.0):
        base = mix(base, C_HIDE_DARK, 0.16)
    return _jitter(base, x, y, z, 9.0)


# --- Stage 5: Animations (14 essential + fun hippo clips, built with tools/motion_kit.py) -----------------------------
# Upright-mammal leg geometry (canonical size 1.0: 5.0 x 2.0 blocks, 16 px = 1 block)
_Y_HIP = 18.0
_Y_KNEE = 7.5
_Y_ANKLE = 2.6
_L1 = _Y_HIP - _Y_KNEE          # 10.5
_L2 = _Y_KNEE - _Y_ANKLE        # 4.9
_HIPS_PIV = (0.0, 18.0, 16.0)
_CHEST_PIV = (0.0, 18.5, 4.0)
_LEG_SPECS = [
    ("leg_fl", "chest",  9.6, -10.0, [0.0, 0.0, -1.0]),   # front-left: front knee (carpus) bends FORWARD (-Z), away from belly!
    ("leg_fr", "chest", -9.6, -10.0, [0.0, 0.0, -1.0]),   # front-right: front knee (carpus) bends FORWARD (-Z), away from belly!
    ("leg_bl", "hips",   9.6,  29.5, [0.0, 0.0, -1.0]),   # hind-left: knee bends gently FORWARD (-Z)
    ("leg_br", "hips",  -9.6,  29.5, [0.0, 0.0, -1.0]),   # hind-right: knee bends gently FORWARD (-Z)
]

# Exact clip-matched walk & charge speeds (model px / s): 2 * sweep / (length * duty)
WALK_LEN, WALK_DUTY, WALK_SWEEP = 1.20, 0.72, 3.0
WALK_SPEED_PX = 2.0 * WALK_SWEEP / (WALK_LEN * WALK_DUTY)       # 6.944 px/s = 0.434 b/s heavy stately walk

CHARGE_LEN, CHARGE_DUTY, CHARGE_SWEEP = 0.56, 0.56, 3.5
CHARGE_SPEED_PX = 2.0 * CHARGE_SWEEP / (CHARGE_LEN * CHARGE_DUTY)   # 22.321 px/s = 1.395 b/s charging locomotive!


def _mat_rot(rot):
    """3x3 rotation matrix matching GeckoLib / anim_lab.turn: Rz(-rz) * Ry(ry) * Rx(-rx)."""
    rx, ry, rz = (math.radians(v) for v in rot)
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(-rz), math.sin(-rz)
    Rx = ((1.0, 0.0, 0.0), (0.0, cx, -sx), (0.0, sx, cx))
    Ry = ((cy, 0.0, sy), (0.0, 1.0, 0.0), (-sy, 0.0, cy))
    Rz = ((cz, -sz, 0.0), (sz, cz, 0.0), (0.0, 0.0, 1.0))
    return _mat_mul(_mat_mul(Rz, Ry), Rx)


def _mat_mul(A, B):
    return tuple(tuple(sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def _mat_T(A):
    return tuple(tuple(A[j][i] for j in range(3)) for i in range(3))


def _mv(A, v):
    return [A[0][0] * v[0] + A[0][1] * v[1] + A[0][2] * v[2],
            A[1][0] * v[0] + A[1][1] * v[1] + A[1][2] * v[2],
            A[2][0] * v[0] + A[2][1] * v[1] + A[2][2] * v[2]]


def _unit(v):
    n = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) or 1.0
    return [v[0] / n, v[1] / n, v[2] / n]


def _dir_to_rx_rz(d):
    """Exact GeckoLib [rx, 0, rz] (deg) turning rest leg (0, -1, 0) onto unit vector d = (dx, dy, dz).
    A leg pointing up past level (reaching forward onto a step, 2026-11-21) is turned past 90 degrees in rx, never
    swung round 180 in rz (the other answer, which flipped the leg in one frame)."""
    dx, dy, dz = d
    rx = math.degrees(math.atan2(dz, math.hypot(dx, dy)))
    rz = math.degrees(math.atan2(-dx, -dy))
    if abs(rz) > 90.0:
        rz -= math.copysign(180.0, rz)
        rx = math.copysign(180.0, rx) - rx
    return [rx, 0.0, rz]


def _mat_to_euler(M):
    """Decomposes M = Rz(-rz) * Ry(ry) * Rx(-rx) back to GeckoLib [rx, ry, rz] degrees."""
    b = math.asin(max(-1.0, min(1.0, -M[2][0])))
    a = math.atan2(M[2][1], M[2][2])
    c = math.atan2(M[1][0], M[0][0])
    return [-math.degrees(a), math.degrees(b), -math.degrees(c)]


def _solve_upright_legs(pose, leg_targets):
    """Exact 3D world-space FK + motion_kit.two_bone IK for all four 3-bone upright legs.
    Reads hips pos/rot and chest rot directly from `pose` so planted feet stay 100% locked to y=0 with zero slide!
    leg_targets[prefix] = (dz_from_rest, y_ankle, toe_pitch_deg)
                       or (dz_from_rest, y_ankle, toe_pitch_deg, dx_from_rest, pole_override)."""
    h_pos = pose.get("hips", {}).get("position", (0.0, 0.0, 0.0))
    h_rot = pose.get("hips", {}).get("rotation", (0.0, 0.0, 0.0))
    c_rot = pose.get("chest", {}).get("rotation", (0.0, 0.0, 0.0))
    R_h = _mat_rot(h_rot)
    R_c = _mat_mul(R_h, _mat_rot(c_rot))
    hp_w = [_HIPS_PIV[i] + h_pos[i] for i in range(3)]
    cp_rel = [_CHEST_PIV[i] - _HIPS_PIV[i] for i in range(3)]
    cp_w = [hp_w[i] + _mv(R_h, cp_rel)[i] for i in range(3)]

    for prefix, parent, lx, lz, pole in _LEG_SPECS:
        spec = leg_targets[prefix]
        dz_t, y_t, toe_t = spec[0], spec[1], spec[2]
        dx_t = spec[3] if len(spec) > 3 else 0.0
        use_pole = spec[4] if len(spec) > 4 else pole
        if parent == "chest":
            rel = [lx - _CHEST_PIV[0], _Y_HIP - _CHEST_PIV[1], lz - _CHEST_PIV[2]]
            off = _mv(R_c, rel)
            sock_w = [cp_w[i] + off[i] for i in range(3)]
            R_par = R_c
        else:
            rel = [lx - _HIPS_PIV[0], _Y_HIP - _HIPS_PIV[1], lz - _HIPS_PIV[2]]
            off = _mv(R_h, rel)
            sock_w = [hp_w[i] + off[i] for i in range(3)]
            R_par = R_h

        target_w = [lx + dx_t, y_t, lz + dz_t]
        knee_w, ankle_w, _ = two_bone(sock_w, _L1, _L2, target_w, use_pole, reach=0.996)

        d1_w = _unit([knee_w[i] - sock_w[i] for i in range(3)])
        d1_loc = _mv(_mat_T(R_par), d1_w)
        r1 = _dir_to_rx_rz(d1_loc)
        R_1 = _mat_mul(R_par, _mat_rot(r1))

        d2_w = _unit([ankle_w[i] - knee_w[i] for i in range(3)])
        d2_loc = _mv(_mat_T(R_1), d2_w)
        if len(spec) > 3 and abs(dx_t) > 0.01:
            # Splayed resting leg: 2-axis bend (shin always points downward, zero flip risk!)
            r2 = _dir_to_rx_rz(d2_loc)
        else:
            # Upright walking/standing knee/elbow: pure X hinge, can NEVER 180-deg flip!
            r2 = [math.degrees(math.atan2(d2_loc[2], -d2_loc[1])), 0.0, 0.0]
        R_2 = _mat_mul(R_1, _mat_rot(r2))

        R_ft = _mat_rot((toe_t, 0.0, 0.0))
        rf = _mat_to_euler(_mat_mul(_mat_T(R_2), R_ft))

        pose[f"{prefix}_1"] = {"rotation": r1}
        pose[f"{prefix}_2"] = {"rotation": r2}
        pose[f"{prefix}_foot"] = {"rotation": rf}


def _blink_pose(pose, amt):
    """Full-height eyelids drop -2.65 px out of the brow to cover the glowing amber eyes cleanly.
    Always emits keyframes (even at 0) so looping clips never interpolate across loop boundaries!"""
    y = -2.65 * amt
    pose["lid_left"] = {"position": [0.0, y, 0.0]}
    pose["lid_right"] = {"position": [0.0, y, 0.0]}


def _planted_idle_legs(pose):
    """Keeps all 4 feet planted cleanly on y=0 at their exact rest world positions."""
    targets = {p: (0.0, _Y_ANKLE, 0.0) for p, *_ in _LEG_SPECS}
    _solve_upright_legs(pose, targets)


def _tail_physics(pose, base_lift, whip_fn, t, lag=0.075, g2=1.15, g3=1.35, twist=0.14, wrap=None):
    """3-bone hippo paddle-and-bristle tail physics: travelling phase-lagged wave + velocity blade twist + lift!
    whip_fn(ts) returns the driving lateral angle (deg, around Z) at time ts."""
    def sample(ts):
        if wrap:
            ts = ts % wrap
        return whip_fn(ts)
    w1 = sample(t)
    w2 = sample(t - lag)
    w3 = sample(t - 2.0 * lag)
    vel1 = (sample(t + 0.02) - sample(t - 0.02)) / 0.04
    vel2 = (sample(t - lag + 0.02) - sample(t - lag - 0.02)) / 0.04
    # Centrifugal lift flares tail out behind the rump when whipping fast
    cent = min(14.0, 0.025 * abs(vel1))
    # Blade pitches naturally into the airflow during a whip
    ry1 = max(-22.0, min(22.0, vel1 * twist * 0.55))
    ry2 = max(-26.0, min(26.0, vel2 * twist * 0.75))
    ry3 = max(-28.0, min(28.0, vel2 * twist * 0.85))
    # Local segment bends: delayed follow-through creates fluid whip S-curves!
    rz1 = w1
    rz2 = g2 * (w2 - 0.55 * w1)
    rz3 = g3 * (w3 - 0.55 * w2)
    pose["tail_1"] = {"rotation": [base_lift + cent, ry1, rz1]}
    pose["tail_2"] = {"rotation": [base_lift * 0.35 + cent * 0.4, ry2, rz2]}
    pose["tail_3"] = {"rotation": [base_lift * 0.25 + cent * 0.3, ry3, rz3]}


def _clip_idle():
    L = 4.0
    def tail_drive(ts):
        # Relaxed sway + two casual wild-hippo fly-swatting whip bursts!
        base = 5.0 * math.sin(2.0 * math.pi * ts / L)
        b1 = 24.0 * pulse(ts, 0.55, 0.14, 0.70, 0.24) * math.sin(2.0 * math.pi * (ts - 0.55) * 2.4)
        b2 = 28.0 * pulse(ts, 2.45, 0.12, 0.52, 0.20) * math.sin(2.0 * math.pi * (ts - 2.45) * 3.3)
        return base + b1 + b2

    def pose_at(t):
        ph = 2.0 * math.pi * t / L
        breath = math.sin(ph)
        dy = -0.18 + 0.12 * breath
        chest_rx = -0.7 * breath
        head_rx = 1.4 * math.sin(ph - 0.5)
        head_ry = 2.2 * math.sin(ph * 0.5)
        ear_w = 10.0 * pulse(t, 2.7, 0.08, 0.05, 0.14) * math.sin(2.0 * math.pi * (t - 2.7) * 5.0)
        blink = max(pulse(t, 1.15, 0.08, 0.05, 0.10), pulse(t, 3.25, 0.08, 0.05, 0.10))
        p = {
            "root": {"position": [0.0, 0.0, 0.0]},
            "hips": {"position": [0.0, dy, 0.0], "rotation": [0.35 * breath, 0.0, 0.4 * math.sin(ph * 0.5)]},
            "chest": {"rotation": [chest_rx, 0.0, 0.0]},
            "neck": {"rotation": [0.6 * breath, 0.8 * math.sin(ph * 0.5), 0.0]},
            "head": {"rotation": [head_rx, head_ry, 0.0]},
            "ear_left": {"rotation": [0.0, ear_w, ear_w * 0.4]},
            "ear_right": {"rotation": [0.0, -ear_w, -ear_w * 0.4]},
        }
        _tail_physics(p, 5.0, tail_drive, t, lag=0.075, wrap=L)
        _blink_pose(p, blink)
        _planted_idle_legs(p)
        return p
    return clip(L, pose_at, 48, loop=True)


def _clip_walk():
    # Classic lateral-sequence four-legged mammal walk (Hildebrand 1965): BL -> FL -> BR -> FR
    # Heavy graviportal hippo stride: tall sturdy column legs with smooth gentle knee flex, zero belly clipping!
    L, duty, sweep = WALK_LEN, WALK_DUTY, WALK_SWEEP
    phases = {"leg_bl": 0.00, "leg_fl": 0.25, "leg_br": 0.50, "leg_fr": 0.75}
    def tail_drive(ts):
        ph = 2.0 * math.pi * ts / L
        # Pendulum inertia trailing hips sway + relaxed hippo stride whip
        return 14.0 * math.sin(ph - 0.5) + 6.0 * math.sin(2.0 * ph - 0.3)

    def pose_at(t):
        u = t / L
        ph = 2.0 * math.pi * u
        # Gentle weight-bearing heave: slightly lower at stride extremes so all 4 planted feet stay 100% locked to y=0!
        hip_dy = -0.56 - 0.06 * math.cos(4.0 * ph)
        hip_rz = 1.1 * math.sin(ph)
        hip_ry = 1.3 * math.cos(ph)
        chest_ry = -2.0 * math.cos(ph)
        chest_rx = 0.4 * math.sin(2.0 * ph)
        targets = {}
        for prefix, *_ in _LEG_SPECS:
            sw, lift = stride(u + phases[prefix], duty)
            dz = -sw * sweep
            y_ank = _Y_ANKLE + 1.05 * lift
            toe = 6.0 * lift
            targets[prefix] = (dz, y_ank, toe)
        p = {
            "hips": {"position": [0.0, hip_dy, 0.0], "rotation": [0.35 * math.sin(2.0 * ph), hip_ry, hip_rz]},
            "chest": {"rotation": [chest_rx, chest_ry, -hip_rz * 0.4]},
            "neck": {"rotation": [1.8 - 0.4 * math.sin(2.0 * ph), 1.0 * math.cos(ph), -hip_rz * 0.4]},
            "head": {"rotation": [-1.5 + 0.4 * math.cos(2.0 * ph), -0.4 * math.cos(ph), -hip_rz * 0.2]},
            "ear_left": {"rotation": [-4.0 + 3.0 * math.sin(2.0 * ph), 0.0, 0.0]},
            "ear_right": {"rotation": [-4.0 + 3.0 * math.sin(2.0 * ph), 0.0, 0.0]},
        }
        _tail_physics(p, 7.0, tail_drive, t, lag=0.075, wrap=L)
        _solve_upright_legs(p, targets)
        return p
    return clip(L, pose_at, 36, loop=True)


def _clip_charge():
    # Terrifying heavy fast trot/charge: diagonal pairs (FL+BR, FR+BL), lowered head, ears pinned, jaw cracked!
    L, duty, sweep = CHARGE_LEN, CHARGE_DUTY, CHARGE_SWEEP
    phases = {"leg_fl": 0.0, "leg_br": 0.05, "leg_fr": 0.5, "leg_bl": 0.55}
    def tail_drive(ts):
        return 18.0 * math.sin(2.0 * math.pi * ts / L - 0.4)

    def pose_at(t):
        u = t / L
        ph = 2.0 * math.pi * u
        hip_dy = -0.74 + 0.14 * abs(math.sin(ph))
        hip_rx = 1.2 * math.cos(2.0 * ph)
        hip_rz = 1.4 * math.sin(ph)
        chest_rx = -0.9 * math.cos(2.0 * ph)
        targets = {}
        for prefix, *_ in _LEG_SPECS:
            sw, lift = stride(u + phases[prefix], duty)
            dz = -sw * sweep
            y_ank = _Y_ANKLE + 1.30 * lift
            toe = 8.0 * lift
            targets[prefix] = (dz, y_ank, toe)
        p = {
            "hips": {"position": [0.0, hip_dy, 0.0], "rotation": [hip_rx, 1.6 * math.sin(ph), hip_rz]},
            "chest": {"rotation": [chest_rx, -2.2 * math.sin(ph), -hip_rz * 0.4]},
            "neck": {"rotation": [7.5, 0.0, -hip_rz * 0.3]},
            "head": {"rotation": [-5.0 + 2.0 * math.sin(2.0 * ph), 0.0, 0.0]},
            "jaw": {"rotation": [22.0 + 4.0 * math.sin(2.0 * ph), 0.0, 0.0]},
            "ear_left": {"rotation": [-34.0, -18.0, -10.0]},
            "ear_right": {"rotation": [-34.0, 18.0, 10.0]},
        }
        _tail_physics(p, 24.0, tail_drive, t, lag=0.055, wrap=L)
        _solve_upright_legs(p, targets)
        return p
    return clip(L, pose_at, 32, loop=True)


def _clip_float():
    # Submerged river rest: body hangs calmly below waterline (21.5 px) so nostrils, glowing amber eyes & ears peek out!
    L = 3.6
    def tail_drive(ts):
        return 12.0 * math.sin(2.0 * math.pi * ts / L)

    def pose_at(t):
        ph = 2.0 * math.pi * t / L
        bob = 0.55 * math.sin(ph)
        blink = pulse(t, 1.9, 0.10, 0.06, 0.12)
        ear_w = 18.0 * pulse(t, 0.7, 0.08, 0.08, 0.12) * math.sin(2.0 * math.pi * (t - 0.7) * 5.0)
        p = {
            "hips": {"position": [0.0, bob, 0.0], "rotation": [2.2 + 0.8 * math.sin(ph - 0.4), 0.0, 1.2 * math.sin(ph * 0.5)]},
            "chest": {"rotation": [-1.8, 0.0, -0.6 * math.sin(ph * 0.5)]},
            "neck": {"rotation": [-4.5, 1.5 * math.sin(ph * 0.5), 0.0]},
            "head": {"rotation": [-6.5 - 0.6 * math.cos(ph), 1.5 * math.sin(ph * 0.5), 0.0]},
            "ear_left": {"rotation": [0.0, ear_w, ear_w * 0.5]},
            "ear_right": {"rotation": [0.0, -ear_w, -ear_w * 0.5]},
        }
        _tail_physics(p, 12.0, tail_drive, t, lag=0.22, g2=1.2, g3=1.4, wrap=L)
        for i, (prefix, *_) in enumerate(_LEG_SPECS):
            s = math.sin(ph + i * 1.4)
            p[f"{prefix}_1"] = {"rotation": [12.0 + 4.0 * s, 0.0, 0.0]}
            p[f"{prefix}_2"] = {"rotation": [8.0 + 3.0 * math.cos(ph + i), 0.0, 0.0]}
            p[f"{prefix}_foot"] = {"rotation": [10.0, 0.0, 0.0]}
        _blink_pose(p, blink)
        return p
    return clip(L, pose_at, 36, loop=True)


def _clip_underwater_walk():
    # Iconic real hippo move: slow-motion buoyant bounding/gliding along the riverbed!
    L = 1.8
    def tail_drive(ts):
        return 16.0 * math.sin(2.0 * math.pi * ts / L - 0.4)

    def pose_at(t):
        u = t / L
        ph = 2.0 * math.pi * u
        glide = max(0.0, math.sin(ph))
        hip_dy = -0.3 + 3.2 * (glide ** 1.3)
        pitch = -5.5 * math.cos(ph)
        p = {
            "hips": {"position": [0.0, hip_dy, 0.0], "rotation": [pitch, 3.0 * math.sin(ph), 2.2 * math.cos(ph)]},
            "chest": {"rotation": [-pitch * 0.4, -4.0 * math.sin(ph), -1.4 * math.cos(ph)]},
            "neck": {"rotation": [-3.0 - pitch * 0.3, 0.0, 0.0]},
            "head": {"rotation": [-4.0, 0.0, 0.0]},
            "ear_left": {"rotation": [-22.0, -10.0, 0.0]},
            "ear_right": {"rotation": [-22.0, 10.0, 0.0]},
        }
        _tail_physics(p, 18.0, tail_drive, t, lag=0.14, g2=1.25, g3=1.5, wrap=L)
        for prefix, parent, lx, lz, pole in _LEG_SPECS:
            is_front = parent == "chest"
            lp = ph + (0.0 if is_front else 1.2) + (0.35 if lx < 0 else 0.0)
            swing = math.sin(lp)
            bend = max(0.0, -math.cos(lp))
            p[f"{prefix}_1"] = {"rotation": [5.0 + 11.5 * swing, 0.0, 0.0]}
            p[f"{prefix}_2"] = {"rotation": [(18.0 if is_front else -18.0) * bend, 0.0, 0.0]}
            p[f"{prefix}_foot"] = {"rotation": [10.0 * swing, 0.0, 0.0]}
        return p
    return clip(L, pose_at, 36, loop=True)


def _clip_yawn():
    # Signature 118-deg hippo threat yawn: rears head, opens massive jaw showing all 4 tusks & tongue, shakes, slams shut!
    L = 2.6
    def tail_drive(ts):
        return 14.0 * pulse(ts, 0.50, 0.25, 1.0, 0.35) * math.sin(2.0 * math.pi * (ts - 0.50) * 2.6)

    def pose_at(t):
        open_a = pulse(t, 0.15, 0.55, 1.10, 0.32)
        slam = pulse(t, 1.98, 0.10, 0.02, 0.25)
        shake = pulse(t, 0.85, 0.15, 0.70, 0.18) * math.sin(2.0 * math.pi * (t - 0.85) * 6.5)
        dy = -0.18 - 0.75 * open_a
        chest_rx = -2.6 * open_a + 1.8 * slam
        neck_rx = -12.4 * open_a + 4.2 * slam
        head_rx = -31.0 * open_a + 6.0 * slam + 1.8 * shake
        jaw_rx = 72.0 * open_a
        p = {
            "hips": {"position": [0.0, dy, 0.0], "rotation": [-1.2 * open_a, 0.0, 0.0]},
            "chest": {"rotation": [chest_rx, 1.5 * shake, 0.0]},
            "neck": {"rotation": [neck_rx, 2.5 * shake, 0.0]},
            "head": {"rotation": [head_rx, 3.0 * shake, 1.5 * shake]},
            "jaw": {"rotation": [jaw_rx, 0.0, 0.0]},
            "tongue": {"rotation": [-9.0 * open_a, 0.0, 0.0]},
            "ear_left": {"rotation": [-28.0 * open_a, -18.0 * open_a, 0.0]},
            "ear_right": {"rotation": [-28.0 * open_a, 18.0 * open_a, 0.0]},
        }
        _tail_physics(p, 5.0 + 10.0 * open_a, tail_drive, t, lag=0.07)
        _blink_pose(p, pulse(t, 1.92, 0.06, 0.06, 0.10))
        _planted_idle_legs(p)
        return p
    return clip(L, pose_at, 52, loop=False)


def _clip_bite():
    # Fast crushing attack: coil (0..0.18s), explosive lunge + wide raised gape (0.18..0.32s), CRUSH SNAP on tick 8 (0.40s)!
    L = 0.75
    def tail_drive(ts):
        return 20.0 * pulse(ts, 0.12, 0.10, 0.20, 0.25) * math.sin(2.0 * math.pi * (ts - 0.12) * 3.0)

    def pose_at(t):
        coil = pulse(t, 0.0, 0.14, 0.04, 0.08)
        lunge = pulse(t, 0.16, 0.14, 0.18, 0.24)
        gape = ramp(t, ((0.0, 0.0), (0.14, 14.0), (0.30, 56.0), (0.39, 4.0), (0.50, 0.0), (L, 0.0)))
        snap_jolt = pulse(t, 0.37, 0.04, 0.04, 0.12)
        g_frac = gape / 56.0
        dz_body = 1.0 * coil - 1.8 * lunge
        dy = -0.25 - 0.38 * coil - 0.28 * lunge
        chest_rx = -1.6 * coil - 1.0 * g_frac + 2.0 * snap_jolt
        p = {
            "hips": {"position": [0.0, dy, dz_body], "rotation": [-1.2 * coil + 1.0 * lunge, 0.0, 0.0]},
            "chest": {"rotation": [chest_rx, 0.0, 0.0]},
            "neck": {"rotation": [-6.0 * coil - 9.0 * g_frac + 4.0 * snap_jolt, 0.0, 0.0]},
            "head": {"rotation": [-10.0 * coil - 22.0 * g_frac + 6.0 * snap_jolt, 0.0, 0.0]},
            "jaw": {"rotation": [gape, 0.0, 0.0]},
            "tongue": {"rotation": [-6.0 * g_frac, 0.0, 0.0]},
            "ear_left": {"rotation": [-32.0 * lunge, -16.0 * lunge, 0.0]},
            "ear_right": {"rotation": [-32.0 * lunge, 16.0 * lunge, 0.0]},
        }
        _tail_physics(p, 5.0 + 18.0 * lunge, tail_drive, t, lag=0.055)
        _blink_pose(p, snap_jolt)
        targets = {pref: (0.0, _Y_ANKLE, 0.0) for pref, *_ in _LEG_SPECS}
        _solve_upright_legs(p, targets)
        return p
    return clip(L, pose_at, 30, loop=False)


def _clip_ear_flick():
    # Iconic surfacing ear-spin + crisp head shake + happy counter-flick of the tail!
    L = 1.1
    def tail_drive(ts):
        return 18.0 * pulse(ts, 0.20, 0.12, 0.40, 0.25) * math.sin(2.0 * math.pi * (ts - 0.20) * 3.2)

    def pose_at(t):
        spin = pulse(t, 0.15, 0.12, 0.55, 0.22)
        w = spin * math.sin(2.0 * math.pi * (t - 0.15) * 6.0)
        shake = pulse(t, 0.25, 0.10, 0.35, 0.20) * math.sin(2.0 * math.pi * (t - 0.25) * 4.5)
        dy = -0.18
        p = {
            "hips": {"position": [0.0, dy, 0.0], "rotation": [0.0, 0.0, 0.0]},
            "chest": {"rotation": [-1.2 * spin, 0.0, 0.0]},
            "neck": {"rotation": [-5.0 * spin, 4.0 * shake, 0.0]},
            "head": {"rotation": [-6.0 * spin, 8.0 * shake, 6.0 * shake]},
            "ear_left": {"rotation": [16.0 * w, 32.0 * w, 20.0 * w]},
            "ear_right": {"rotation": [-16.0 * w, -32.0 * w, -20.0 * w]},
        }
        _tail_physics(p, 5.0 + 6.0 * spin, tail_drive, t, lag=0.065)
        _blink_pose(p, pulse(t, 0.18, 0.07, 0.22, 0.12))
        _planted_idle_legs(p)
        return p
    return clip(L, pose_at, 32, loop=False)


def _clip_graze():
    # Nighttime floodplain grazing: wide lip sweeps right over the grass while tail casually swats flies!
    L = 2.4
    def tail_drive(ts):
        ph = 2.0 * math.pi * ts / L
        return 10.0 * math.sin(ph) + 22.0 * pulse(ts, 0.9, 0.14, 0.55, 0.22) * math.sin(2.0 * math.pi * (ts - 0.9) * 3.2)

    def pose_at(t):
        ph = 2.0 * math.pi * t / L
        sweep = math.sin(ph)
        munch = 0.5 - 0.5 * math.cos(4.0 * ph)
        dy = -0.25
        p = {
            "hips": {"position": [0.0, dy, 0.0], "rotation": [0.6, 0.0, 0.0]},
            "chest": {"rotation": [1.5, 2.0 * sweep, 0.0]},
            "neck": {"rotation": [12.5, 8.0 * sweep, 0.0]},
            "head": {"rotation": [9.0 + 1.8 * munch, 10.0 * sweep, -3.0 * sweep]},
            "jaw": {"rotation": [8.5 * munch, 0.0, 0.0]},
            "ear_left": {"rotation": [-8.0, 6.0 * sweep, 0.0]},
            "ear_right": {"rotation": [-8.0, 6.0 * sweep, 0.0]},
        }
        _tail_physics(p, 6.0, tail_drive, t, lag=0.07, wrap=L)
        _blink_pose(p, pulse(t, 1.5, 0.08, 0.06, 0.10))
        _planted_idle_legs(p)
        return p
    return clip(L, pose_at, 36, loop=True)


def _rest_pose_blend(f, breath=0.0, blink=0.0, t_tail=0.0, tail_wrap=None, step_lift=0.0):
    """Shared belly-down resting pose parameterized by f in [0 (standing idle) .. 1 (cozy bank lounge)].
    Uses exact 3D world-space IK with outward poles so all 4 legs stay 100% OUTSIDE the barrel and all 4 feet
    rest 100% FLAT on y = 0.00 throughout Lie Down, Rest, and Stand Up!"""
    dy = -0.18 + (-6.35 - (-0.18)) * f + 0.14 * breath * f
    p = {
        "hips": {"position": [0.0, dy, 0.0], "rotation": [0.4 * breath * f, 0.0, 0.0]},
        "chest": {"rotation": [-0.6 * breath * f, 2.0 * f, 0.0]},
        "neck": {"rotation": [2.0 * f, 4.5 * f, 0.0]},
        "head": {"rotation": [(2.5 + 0.6 * breath) * f, 5.5 * f, 0.0]},
        "ear_left": {"rotation": [-12.0 * f, -8.0 * f, -10.0 * f]},
        "ear_right": {"rotation": [-12.0 * f, 8.0 * f, 10.0 * f]},
    }
    # Feet slide cleanly outward (x += ±8.8*f) & slightly forward/back, knees bending OUTWARD & UP away from barrel & floor!
    y_a = _Y_ANKLE + step_lift
    targets = {
        "leg_fl": (-3.4 * f, y_a, 0.0,  8.8 * f, [ 0.85 * f + 0.05, 0.45 * f, -1.0 + 0.55 * f]),
        "leg_fr": (-3.4 * f, y_a, 0.0, -8.8 * f, [-0.85 * f - 0.05, 0.45 * f, -1.0 + 0.55 * f]),
        "leg_bl": ( 3.2 * f, y_a, 0.0,  9.0 * f, [ 0.85 * f + 0.05, 0.45 * f, -1.0 + 0.55 * f]),
        "leg_br": ( 3.2 * f, y_a, 0.0, -9.0 * f, [-0.85 * f - 0.05, 0.45 * f, -1.0 + 0.55 * f]),
    }
    _solve_upright_legs(p, targets)
    def rest_tail(ts):
        return 18.0 * f + 14.0 * f * pulse(ts, 1.4, 0.16, 0.45, 0.25) * math.sin(2.0 * math.pi * (ts - 1.4) * 2.4)
    _tail_physics(p, 5.0 + 18.0 * f, rest_tail, t_tail, lag=0.09, wrap=tail_wrap)
    _blink_pose(p, blink)
    return p


def _clip_rest_down():
    # Smooth heavy lowering onto the sandy riverbank: legs glide cleanly outward on y=0, zero belly or floor clip!
    L = 0.9
    def pose_at(t):
        f = ease(t / L)
        return _rest_pose_blend(f, breath=0.0, blink=pulse(t, 0.35, 0.12, 0.15, 0.15),
                                t_tail=0.0, step_lift=1.05 * math.sin(math.pi * f))
    return clip(L, pose_at, 24, loop=False)


def _clip_rest():
    # Heavy belly-down bank lounge: legs splayed cleanly outside the flanks, flat on sand, slow breathing & tail flick!
    L = 3.6
    def pose_at(t):
        ph = 2.0 * math.pi * t / L
        breath = math.sin(ph)
        blink = pulse(t, 0.8, 0.20, 0.90, 0.25)
        return _rest_pose_blend(1.0, breath=breath, blink=blink, t_tail=t, tail_wrap=L)
    return clip(L, pose_at, 36, loop=True)


def _clip_rest_up():
    # Heavy lumbering push back up onto all four pillar legs: feet stay flat on y=0 and pull smoothly inward!
    L = 0.9
    def pose_at(t):
        f = 1.0 - ease(t / L)
        return _rest_pose_blend(f, breath=0.0, blink=0.0, t_tail=0.0, step_lift=1.05 * math.sin(math.pi * f))
    return clip(L, pose_at, 24, loop=False)


def _clip_dung_flick():
    # Iconic real hippo territorial move: rear squat + wild 3-segment paddle-tail propeller whip with full physics!
    L = 1.6
    def tail_drive(ts):
        whip = pulse(ts, 0.18, 0.14, 0.92, 0.26)
        return 30.0 * whip * math.sin(2.0 * math.pi * (ts - 0.18) * 4.6)

    def pose_at(t):
        squat = pulse(t, 0.10, 0.22, 0.96, 0.28)
        wag = pulse(t, 0.18, 0.14, 0.92, 0.26) * math.sin(2.0 * math.pi * (t - 0.18) * 4.6)
        dy = -0.18 - 0.42 * squat
        hip_rx = -1.8 * squat
        chest_rx = 1.8 * squat
        p = {
            "hips": {"position": [0.0, dy, 0.0], "rotation": [hip_rx, 1.8 * wag, 0.9 * wag]},
            "chest": {"rotation": [chest_rx, -1.2 * wag, -0.5 * wag]},
            "neck": {"rotation": [-1.2 * squat, 0.0, 0.0]},
            "head": {"rotation": [1.5 * squat, 0.0, 0.0]},
            "ear_left": {"rotation": [-10.0 * squat, 0.0, 0.0]},
            "ear_right": {"rotation": [-10.0 * squat, 0.0, 0.0]},
        }
        _tail_physics(p, 5.0 + 30.0 * squat, tail_drive, t, lag=0.055, g2=1.25, g3=1.45, twist=0.14)
        _planted_idle_legs(p)
        return p
    return clip(L, pose_at, 48, loop=False)


def _clip_death():
    # Heavy collapse: legs buckle & tuck inward as massive barrel rolls cleanly onto its left side (zero floor clip)!
    L = 1.4
    def pose_at(t):
        tuck = ease(t / 0.55)
        roll = ease((t - 0.15) / 1.05)
        rz = 86.0 * roll
        dy = -0.18 + 4.4 * math.sin(math.pi * roll) - 0.55 * roll
        p = {
            "hips": {"position": [0.0, dy, 0.0], "rotation": [1.5 * math.sin(math.pi * roll), 0.0, rz]},
            "chest": {"rotation": [1.0 * roll, -3.0 * roll, 1.0 * roll]},
            "neck": {"rotation": [-3.0 * roll, -4.0 * roll, 1.5 * roll]},
            "head": {"rotation": [-5.0 * roll, -5.0 * roll, 1.5 * roll]},
            "jaw": {"rotation": [16.0 * roll, 0.0, 0.0]},
            "ear_left": {"rotation": [-18.0 * roll, 0.0, -22.0 * roll]},
            "ear_right": {"rotation": [-18.0 * roll, 0.0, 22.0 * roll]},
            "tail_1": {"rotation": [14.0 * roll, 10.0 * roll, 18.0 * roll]},
            "tail_2": {"rotation": [10.0 * roll, 12.0 * roll, 16.0 * roll]},
            "tail_3": {"rotation": [8.0 * roll, 10.0 * roll, 14.0 * roll]},
            # Underside (+X left) legs fold forward & INWARD early (+rz swings +X leg toward x=0, cleanly off the floor!)
            "leg_fl_1": {"rotation": [-52.0 * tuck, 0.0, 34.0 * tuck]},
            "leg_fl_2": {"rotation": [46.0 * tuck, 0.0, 0.0]},
            "leg_fl_foot": {"rotation": [12.0 * tuck, 0.0, 0.0]},
            "leg_bl_1": {"rotation": [-48.0 * tuck, 0.0, 34.0 * tuck]},
            "leg_bl_2": {"rotation": [40.0 * tuck, 0.0, 0.0]},
            "leg_bl_foot": {"rotation": [12.0 * tuck, 0.0, 0.0]},
            # Topside (-X right) legs hang loosely limp
            "leg_fr_1": {"rotation": [-18.0 * roll, 0.0, 14.0 * roll]},
            "leg_fr_2": {"rotation": [24.0 * roll, 0.0, 0.0]},
            "leg_fr_foot": {"rotation": [10.0 * roll, 0.0, 0.0]},
            "leg_br_1": {"rotation": [14.0 * roll, 0.0, 14.0 * roll]},
            "leg_br_2": {"rotation": [-18.0 * roll, 0.0, 0.0]},
            "leg_br_foot": {"rotation": [8.0 * roll, 0.0, 0.0]},
        }
        _blink_pose(p, ease(t / 0.40))
        return p
    return clip(L, pose_at, 32, loop="hold")


# --- Stage 5c: block moves (up a block, down a block, out of the water; 2026-11-21) ------------------------------------
# Made the Nile monitor's way (docs/ANIMAL-MAKER-ENGINE.md, "M1. Moves of their own"): each move made once for each
# size, and the game carries the hippo along the move's own path (the framework's BlockMoves, reading
# block_moves/hippo.json). The clip turns the trunk and places the legs; the root's travel is the clip's "path root"
# note, its scene the "step" or "water" note (what tools/clip_check.py reads).
# The hippo's problem: its pillar legs are 15.4 px long and a block is 16, so it cannot step up as a horse does. What it
# does instead (fun first, from real hippos hauling out up steep river banks; every number my pick):
#   up:   chin lifted over the top, a crouched shuffle to the face, one forefoot then the other hauled up onto the top,
#         the belly slid over the lip as the hind legs push, the hind feet scrambling up the face, a walk on and a settle;
#   down: a look over the edge, the forefeet let down one at a time, the belly sliding over the lip as the front drops,
#         the hind feet stepping down after, a jolt on landing;
#   out:  the climb up from the shallows (standing on the riverbed a block under the bank: real hippos walk out, they
#         do not swim), slower and wetter, ending with the ear flick that shakes the water off.
# A calf (0.45) gets none: a block is as tall as it is; it keeps the game's own step (my pick).
MOVE_SIZES = (0.92, 1.0)            # the cow and the bull
_LEG_REACH = 0.996 * (_L1 + _L2)    # as far as a leg straightens in _solve_upright_legs
_HIND_SOCK = 29.5 - _HIPS_PIV[2]    # the hind leg sockets this far behind the hips' pivot (px)
_TRUNK_LEN = 29.5 - (-10.0)         # fore to hind sockets (px)
_HEAD_BONES = ("neck", "head", "jaw")


def move_block_px(size):
    """A block's height in model pixels on a hippo of this size."""
    return 16.0 / size


_RIG = {}


def _rig():
    """The bones' pivots and the trunk's underside points (y, z) per bone, read once from the model."""
    if not _RIG:
        bones = build_bones()
        _RIG["pivot"] = {b["name"]: b["pivot"] for b in bones}
        _RIG["parent"] = {b["name"]: b.get("parent") for b in bones}
        under = {}
        for b in bones:
            if b["name"] in ("hips", "chest"):
                pts = []
                for c in b["cubes"]:
                    y0, z0, z1 = c["origin"][1], c["origin"][2], c["origin"][2] + c["size"][2]
                    n = max(1, int((z1 - z0) / 1.5))
                    pts += [(y0, z0 + (z1 - z0) * k / n) for k in range(n + 1)]
                under[b["name"]] = pts
        _RIG["under"] = under
        _RIG["corners"] = {b["name"]: [(c["origin"][1] + dy, c["origin"][2] + dz) for c in b["cubes"]
                                       for dy in (0.0, c["size"][1]) for dz in (0.0, c["size"][2])]
                           for b in bones if b["name"] in _HEAD_BONES}
    return _RIG


def _placed(pose, dy, dz, bone):
    """(M, t): a point p of this bone drawn at M p + t, the root moved up dy and along dz (model px)."""
    rig = _rig()
    chain = []
    b = bone
    while b and b != "root":
        chain.append(b)
        b = rig["parent"][b]
    M = ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
    t = [0.0, dy, dz]
    for b in reversed(chain):
        piv = rig["pivot"][b]
        R = _mat_rot(pose.get(b, {}).get("rotation", (0.0, 0.0, 0.0)))
        pos = pose.get(b, {}).get("position", (0.0, 0.0, 0.0))
        rp = _mv(R, piv)
        local = [piv[i] - rp[i] + (-pos[0] if i == 0 else pos[i]) for i in range(3)]
        t = [t[i] + _mv(M, local)[i] for i in range(3)]
        M = _mat_mul(M, R)
    return M, t


def _at(Mt, p):
    M, t = Mt
    q = _mv(M, p)
    return [q[i] + t[i] for i in range(3)]


class _Scene:
    """What is solid in a move's clip (its own model px, forward -z): the ground the hippo starts on at 0 and the
    ground past the face (z < face) at `ahead` (+B up a step or out onto a bank, -B down one)."""

    def __init__(self, face, ahead):
        self.face, self.ahead = face, ahead

    def floor(self, z):
        return self.ahead if z < self.face else 0.0

    def depth(self, y, z):
        """How deep a point is inside the ground or the step (0 if not)."""
        hi, lo = max(self.ahead, 0.0), min(self.ahead, 0.0)
        d = max(0.0, lo - y)
        high = z < self.face if self.ahead > 0 else z > self.face
        if high and y < hi:
            d = max(d, min(hi - y, abs(z - self.face)))
        return d


def _clearance(pose, dy, dz, sc, margin):
    """How far (px) the trunk must go up for its underside to be `margin` over what is under it."""
    need = -99.0
    for bone, pts in _rig()["under"].items():
        Mt = _placed(pose, dy, dz, bone)
        for y, z in pts:
            w = _at(Mt, [0.0, y, z])
            floor = sc.floor(w[2]) if abs(w[2] - sc.face) > 0.2 else max(sc.floor(w[2] - 0.3), sc.floor(w[2] + 0.3))
            need = max(need, floor + margin - w[1])
    return need


_SPRING = 0.99 * (_L1 + _L2)       # a planted leg carries its socket at most this far from its ankle (knees never locked)
_GIRDLE = {"chest": ("leg_fl", "leg_fr"), "hips": ("leg_bl", "leg_br")}


def _trunk(th, bend, roll, dy, dz):
    return {"root": {"position": [0.0, 0.0, 0.0]},
            "hips": {"position": [0.0, 0.0, 0.0], "rotation": [-th, 0.0, roll]},
            "chest": {"rotation": [-bend, 0.0, -0.4 * roll]}}


def _carried(keys, t, feet, sc):
    """The trunk at time t carried by its planted feet (the hippo's legs are pillars: the body rides as high as they
    reach, `sag` lower so the knees give): the hind and fore sockets' heights a leg's reach over the planted feet
    under them, the pitch from the two, the underside lifted clear of the ground and the lip. Returns (pitch, dy, lift,
    the two heights' limits)."""
    from motion_kit import spline
    dz = spline(t, keys["dz"])
    bend = spline(t, keys.get("bend", ((0.0, 0.0),)))
    roll = spline(t, keys.get("roll", ((0.0, 0.0),)))
    th, dy = 0.0, 0.0
    for _ in range(4):                                     # the sockets' places depend on the pitch: settle it
        p = _trunk(th, bend, roll, dy, dz)
        tops = {}
        for girdle, legs in _GIRDLE.items():
            Mt = _placed(p, dy, dz, girdle)
            best = None
            for prefix, parent, lx, lz, pole in _LEG_SPECS:
                if prefix not in legs:
                    continue
                at, up, catch = feet[prefix]
                if up > 0.0:                               # a foot in the air carries nothing
                    continue
                sz = _at(Mt, [lx, _Y_HIP, lz])[2]
                h = at[1] + math.sqrt(max(0.0, _SPRING ** 2 - (sz - at[2]) ** 2))
                best = h if best is None else min(best, h)
            tops[girdle] = best
        sag = keys.get("sag", ((0.0, 0.5),))
        yh = None if tops["hips"] is None else tops["hips"] - spline(t, keys.get("sag_h", sag))
        yf = None if tops["chest"] is None else tops["chest"] - spline(t, keys.get("sag_f", sag))
        # an end with no foot down rests on its belly: on the lip (or the ground), as low as the underside lets it
        if yh is None and yf is None:
            raise ValueError(f"no foot down at either end at {t:.2f} s: give each end a foot down at every moment")
        if yh is None or yf is None:
            def need(v):
                a, b = (v, yf) if yh is None else (yh, v)
                th_ = math.degrees(math.asin(max(-0.95, min(0.95, (b - a) / _TRUNK_LEN))))
                dy_ = a - _Y_HIP + _HIND_SOCK * math.sin(math.radians(th_))
                return _clearance(_trunk(th_, bend, roll, dy_, dz), dy_, dz, sc, keys.get("margin", 0.0))
            lo, hi = -40.0, 45.0
            for _ in range(30):
                mid = 0.5 * (lo + hi)
                lo, hi = (mid, hi) if need(mid) > 0.0 else (lo, mid)
            # dropping to lower ground, the legs reach down and catch it: no lower than they will stand on landing
            girdle = "hips" if yh is None else "chest"
            Mt = _placed(_trunk(th, bend, roll, dy, dz), dy, dz, girdle)
            for prefix, parent, lx, lz, _ in _LEG_SPECS:
                catch = feet[prefix][2]
                if prefix in _GIRDLE[girdle] and catch is not None:
                    sz = _at(Mt, [lx, _Y_HIP, lz])[2]
                    h = catch[1] + math.sqrt(max(0.0, _SPRING ** 2 - (sz - catch[2]) ** 2)) - spline(t, sag)
                    hi = max(hi, h)
            yh, yf = (hi, yf) if yh is None else (yh, hi)
        th = math.degrees(math.asin(max(-0.95, min(0.95, (yf - yh) / _TRUNK_LEN))))
        dy = yh - _Y_HIP + _HIND_SOCK * math.sin(math.radians(th))
    lift = _clearance(_trunk(th, bend, roll, dy, dz), dy, dz, sc, keys.get("margin", 0.0))
    return th, dy, lift, (yh, yf), (tops["hips"] is None, tops["chest"] is None)


def _smooth_low(vals, w=3, free=None):
    """A series eased without ever going over itself (a running least, then a running mean). Ticks marked free (an
    end resting on its belly or falling, not on its feet) are left as they are and never looked at by the others: a
    standing end must not sink early toward the drop that follows it."""
    n = len(vals)
    free = free or [False] * n

    def near(i):
        return [j for j in range(max(0, i - w), min(n, i + w + 1)) if free[j] == free[i]]
    lo = [vals[i] if free[i] else min(vals[j] for j in near(i)) for i in range(n)]
    return [lo[i] if free[i] else sum(lo[j] for j in near(i)) / len(near(i)) for i in range(n)]


def _move_clip(keys, sc, note, log=None):
    """A move's clip from its keys (each (time, value) pairs through a monotone spline, model px and degrees):
      dz      the root's travel (forward is minus); bend  the chest bent up off the hips; look  the head's pitch in the
              world (nose up); roll  the trunk's lean (to its left +); jaw; tail  the tail's lift; sag  how far under
              its legs' reach the trunk rides
      feet    {leg: plan} for motion_kit.foot_at: (x, ankle height, z[, toes up]) then steps (lift, land, place)
    and its scene. The trunk's height and pitch follow from the planted feet (_carried); the underside is kept `margin`
    clear of what is under it by lifting the root (the belly rests on a lip at 0). Returns the clip with its path and
    scene notes."""
    from motion_kit import spline, foot_at
    margin = keys.get("margin", 0.0)
    length = keys["length"]
    steps = round(length * 20)
    times = [length * k / steps for k in range(steps + 1)]

    def feet_at(t):
        """Each foot (place, lift, toes up); a foot reaching for a place marked "kick" is lifted all the way (it
        carries nothing: the hind legs scrabbling as the belly slides over the lip)."""
        out = {}
        for prefix, parent, *_ in _LEG_SPECS:
            plan = keys["feet"][prefix]
            # a step's lift (px): a forefoot reaching onto a top keeps low (higher, its reach lines up with its knee's
            # bend and flips it), a hind foot lifts clear of the lip
            at, up, toe = foot_at(t, plan, 2.5 if parent == "chest" else 3.5, reach=parent == "chest")
            paw, before = None, plan[0]
            for s0, s1, place in plan[1:]:
                if len(place) > 4 and place[4] == "kick" and s0 <= t < s1 + 1e-6:
                    first = t < 0.5 * (s0 + s1)
                    # the hanging pose is handed back to the reach before the last of the landing, which it makes
                    # alone: down from just over its place (climbing, from in front of the face and over the lip,
                    # never through the lip's corner)
                    paw = (max(0.0, min(1.0, (t - s0) / 0.25, (s1 - 0.15 - t) / 0.25)), 2.0 * math.pi * 2.2 * (t - s0),
                           place if place[1] < before[1] - 2.0 else None, s0 if first else None)
                    up = max(up, 1e-3)
                    # aimed only where it took off (carried along with its hip, as a foot let go drops with the
                    # body), then where it lands (both within reach): the hanging pose covers the change in the
                    # middle, and the knee never chases a path swinging past the hip
                    at = list((before if first else place)[:3])
                    if first:                          # lifted straight up off its place before it swings
                        at[1] += 3.5 * ease(min(1.0, (t - s0) / 0.15))
                        if place[1] < before[1] - 2.0:     # letting go of an edge: slid forward off it first
                            off = min(at[2], sc.face - 4.6)
                            at[2] += (off - at[2]) * ease(min(1.0, (t - s0) / 0.12))
                    else:
                        e = ease(max(0.0, min(1.0, (t - (s1 - 0.3)) / 0.3)))
                        back = max(0.0, sc.face + 1.0 - at[2]) if place[1] > before[1] + 2.0 else 0.0
                        at = [at[0], at[1] + 3.5 * (1.0 - e), at[2] + back * (1.0 - ease(min(1.0, 2.0 * e)))]
                if s1 <= t:
                    before = place
            out[prefix] = (at, up, toe, paw)
        return out

    # the trunk carried tick by tick, eased (never higher than its legs reach), then lifted clear of the lip
    raw = [_carried(keys, t, {k: (v[0], v[1], v[3][2] if v[3] else None) for k, v in feet_at(t).items()}, sc)
           for t in times]
    # knees bend forward, and up as a foot reaches ahead (never flipping)
    poles = {"chest": _unit([0.0, 0.7, -1.0]), "hips": _unit([0.0, 0.7, -1.0])}
    yh = _smooth_low([r[3][0] for r in raw], free=[r[4][0] for r in raw])
    yf = _smooth_low([r[3][1] for r in raw], free=[r[4][1] for r in raw])
    rise = keys.get("rise", 0.9)                       # px a tick at most (a 2-ton body heaves, it does not spring)
    for ys in (yh, yf):
        for i in range(1, len(ys)):
            ys[i] = min(ys[i], ys[i - 1] + rise)
    trunk = []

    def placed(t, a, b):
        th = math.degrees(math.asin(max(-0.95, min(0.95, (b - a) / _TRUNK_LEN))))
        dy = a - _Y_HIP + _HIND_SOCK * math.sin(math.radians(th))
        dz = spline(t, keys["dz"])
        p = _trunk(th, spline(t, keys.get("bend", ((0.0, 0.0),))), spline(t, keys.get("roll", ((0.0, 0.0),))), dy, dz)
        return th, dy, _clearance(p, dy, dz, sc, margin)

    for t, a, b, r in zip(times, yh, yf, raw):
        th, dy, lift = placed(t, a, b)
        free_h, free_f = r[4]
        if lift > 0.0 and (free_h or free_f):
            # the end resting on its belly is raised back onto the lip (the easing must not sink it in)
            lo, hi = (a, a + 30.0) if free_h else (b, b + 30.0)
            for _ in range(30):
                mid = 0.5 * (lo + hi)
                need = placed(t, mid, b)[2] if free_h else placed(t, a, mid)[2]
                lo, hi = (mid, hi) if need > 0.0 else (lo, mid)
            th, dy, lift = placed(t, hi, b) if free_h else placed(t, a, hi)
        trunk.append([th, dy, max(0.0, lift)])
    lifts = [-v for v in _smooth_low([-r[2] for r in trunk])]          # eased, never under what is needed
    for r, l in zip(trunk, lifts):
        r[1] += l
        r[2] = l
    # nothing drops faster than it would fall (Minecraft's 0.08 blocks a tick, each tick): a rump sliding off the lip
    # falls over a few ticks onto its legs, never in one
    g = 0.08 * 16.0 * keys.get("block", 16.0) / 16.0
    falls = [0.0, 0.0]
    prev = None
    for r in trunk:
        th, dy = r[0], r[1]
        yh = dy + _Y_HIP - _HIND_SOCK * math.sin(math.radians(th))
        yf = yh + _TRUNK_LEN * math.sin(math.radians(th))
        if prev:
            ys = [yh, yf]
            for i in (0, 1):
                floor = prev[i] - (falls[i] + g)
                if ys[i] < floor - 1e-6:
                    falls[i] += g
                    ys[i] = floor
                else:
                    falls[i] = max(0.0, prev[i] - ys[i])
            yh, yf = ys
            th = math.degrees(math.asin(max(-0.95, min(0.95, (yf - yh) / _TRUNK_LEN))))
            r[0], r[1] = th, yh - _Y_HIP + _HIND_SOCK * math.sin(math.radians(th))
        prev = (yh, yf)
    path = []

    def pose_at(t, record=True):
        k = min(range(len(times)), key=lambda i: abs(times[i] - t))
        th, dy, lift = trunk[k]
        dz = spline(t, keys["dz"])
        bend = spline(t, keys.get("bend", ((0.0, 0.0),)))
        roll = spline(t, keys.get("roll", ((0.0, 0.0),)))
        p = _trunk(th, bend, roll, dy, dz)
        # the head pitched in the world: shared between neck and head, whatever the trunk does
        x = spline(t, keys["look"]) - (th + bend)
        p["neck"] = {"rotation": [-0.45 * x, 0.0, -0.3 * roll],
                     "position": [0.0, spline(t, keys.get("neck_lift", ((0.0, 0.0),))), 0.0]}
        p["head"] = {"rotation": [-0.55 * x, 0.0, -0.2 * roll]}
        p["jaw"] = {"rotation": [spline(t, keys.get("jaw", ((0.0, 0.0),))), 0.0, 0.0]}
        ears = keys.get("ears")
        ew = ears(t) if ears else 0.0
        p["ear_left"] = {"rotation": [-6.0, ew, ew * 0.4]}
        p["ear_right"] = {"rotation": [-6.0, -ew, -ew * 0.4]}
        _blink_pose(p, keys["blink"](t) if "blink" in keys else 0.0)
        # the tail hangs (its lift against the trunk's pitch) and swings a little
        _tail_physics(p, spline(t, keys.get("tail", ((0.0, 6.0),))) + 0.8 * th,
                      lambda ts: 9.0 * math.sin(2.0 * math.pi * ts / 1.6), t, lag=0.09)
        # the legs reach their feet's places (in the root's frame)
        targets, misses = {}, {}
        feet = feet_at(t)
        for prefix, parent, lx, lz, _ in _LEG_SPECS:
            at, up, toe, paw = feet[prefix]
            sock = _at(_placed(p, dy, dz, parent), [lx, _Y_HIP, lz])
            if paw:
                if paw[3] is not None:                 # let go: the foot goes where its hip goes since it let go
                    i0 = min(range(len(times)), key=lambda i: abs(times[i] - paw[3]))
                    th0, dy0, _ = trunk[i0]
                    b0, r0 = spline(times[i0], keys.get("bend", ((0.0, 0.0),))), spline(times[i0], keys.get("roll", ((0.0, 0.0),)))
                    dz0 = spline(times[i0], keys["dz"])
                    sock0 = _at(_placed(_trunk(th0, b0, r0, dy0, dz0), dy0, dz0, parent), [lx, _Y_HIP, lz])
                    at = [at[i] + sock[i] - sock0[i] for i in range(3)]
                # the reach toward a place it cannot touch yet stays inside the leg's reach (a straight leg aimed past
                # its reach flips its knee); the hanging pose below takes over from it
                v = [at[i] - sock[i] for i in range(3)]
                d = math.sqrt(sum(c * c for c in v)) or 1.0
                if d > 0.88 * _LEG_REACH:         # eased in and out with the hanging pose, so nothing jumps
                    k = 1.0 - paw[0] * (1.0 - 0.88 * _LEG_REACH / d)
                    at = [sock[i] + v[i] * k for i in range(3)]
            targets[prefix] = (at[2] - dz - lz, at[1] - dy, -toe, 0.0, poles[parent])   # foot_at's toes up is GeckoLib's minus
            misses[prefix] = (math.dist(sock, [lx, at[1], at[2]]) - _LEG_REACH) if up == 0.0 else None
        _solve_upright_legs(p, targets)
        for prefix, parent, *_ in _LEG_SPECS:
            paw = feet[prefix][3]
            if paw:
                _hang(p, prefix, parent == "chest", paw[0], paw[1], keys.get("hang"))
        if record:
            path.append((t, dy, dz))
            if log is not None:
                head = max(sc.depth(*_at(_placed(p, dy, dz, b), [0.0, y, z])[1:]) for b in _HEAD_BONES
                           for y, z in _rig()["corners"][b])
                log.append((t, dy, dz, th, max(0.0, lift), misses, head))
        return p

    made = clip(length, pose_at, steps, loop="hold")
    _unwrap(made)
    path = path[-(steps + 1):]
    made["timeline"] = dict(note)
    if "surface" in keys:
        # the wet moment: the last tick the hips' underside is still in the water (the drip and splash's hook)
        wet = 0.0
        for (t, dy, dz) in path:
            p = pose_at(t, record=False)
            Mt = _placed(p, dy, dz, "hips")
            if min(_at(Mt, [0.0, y, z])[1] for y, z in _rig()["under"]["hips"]) < keys["surface"]:
                wet = t
        made["timeline"]["0.1"] = f"wet {wet:.2f}"
    made["timeline"]["0.05"] = "path root " + " ".join(f"{t:.4f} {dy:.3f} {dz:.3f}" for t, dy, dz in path)
    return made


def _hang(p, prefix, fore, amp, w, style=None):
    """A leg off the ground that cannot reach it yet (the 2026-11-21 rethink after two strikes: aimed at the ground
    like a standing leg, a nearly straight leg flipped its knee): posed by its own joint angles, hanging from its
    girdle and pawing (the thigh swinging, the knee folding with it), blended over the reach's pose by amp (0 at its
    start and landing, so it takes off and lands where the reach puts it). My picks: folded well up (a hippo's thick
    shins hanging straight would reach the ground), the forelegs a little ahead."""
    thigh, knee, foot = (style or {}).get("fore" if fore else "hind", (-24.0, 34.0, 6.0) if fore else (-18.0, 30.0, 6.0))
    hang = {"1": [thigh + 10.0 * math.sin(w), 0.0, 0.0],
            "2": [knee + 10.0 * (1.0 - math.cos(w)), 0.0, 0.0],
            "foot": [foot + 8.0 * math.sin(w), 0.0, 0.0]}
    for part, r in hang.items():
        bone = f"{prefix}_{part}"
        old = p[bone]["rotation"]
        p[bone] = {"rotation": [o + (n - o) * amp for o, n in zip(old, r)]}


def _unwrap(made):
    """Every turn key kept within 180 degrees of the one before (adding or taking 360), so GeckoLib's straight lines
    between keys never swing a joint the long way round."""
    for chans in made["bones"].values():
        keys = chans.get("rotation")
        if not keys:
            continue
        prev = None
        for t in sorted(keys, key=float):
            v = list(keys[t])
            if prev is not None:
                for i in range(3):
                    while v[i] - prev[i] > 180.0:
                        v[i] -= 360.0
                    while v[i] - prev[i] < -180.0:
                        v[i] += 360.0
            keys[t] = [round(x, 3) for x in v]
            prev = v


def _gait(dz_keys, schedule, sc, k=1.0):
    """Each leg's foot plan (for motion_kit.foot_at) from its steps: (lift, land, where), times in seconds (scaled by
    k), where "here" (the ground it starts on), "there" (the ground past the face or edge), "face" (pressed on the face)
    or an explicit (x, height, z[, toes up]); a "!" after it (as "there!") makes the step a slow reach that carries no
    weight until it lands (legs scrabbling while the belly slides over the lip). A footfall lands where its socket will
    be halfway through the stance that follows (so a pillar leg reaches it all through), kept on its own ground: the
    foot's 4.25 px front and 4 px heel never in a face, and on a top it may stand with its toes over the lip."""
    from motion_kit import spline
    up = sc.ahead > 0
    plans = {}
    for prefix, parent, lx, lz, pole in _LEG_SPECS:
        steps = schedule[prefix]
        plan = [(lx, _Y_ANKLE, lz)]
        for i, (t0, t1, where) in enumerate(steps):
            t0, t1 = t0 * k, t1 * k
            if isinstance(where, tuple):
                plan.append((t0, t1, where))
                continue
            kick = where.endswith("!")
            where = where.rstrip("!")
            nxt = steps[i + 1][0] * k if i + 1 < len(steps) else t1 + 0.6 * k
            z = lz + spline(0.5 * (t1 + nxt), dz_keys)
            if where == "face":         # the sole against the face: up a step facing forward (toes up), down one back
                place = (lx, min(sc.ahead, 0.0) + 0.5 * abs(sc.ahead), sc.face + (2.9 if up else -2.9), 80.0 if up else -80.0)
            elif where == "there":
                place = (lx, sc.ahead + _Y_ANKLE, min(z, sc.face - (3.0 if up else 4.5)))
            else:
                place = (lx, _Y_ANKLE, max(z, sc.face + (4.75 if up else 1.5)))
            if kick:
                place = tuple(place) + ((place[3] if len(place) > 3 else 0.0), "kick")
            plan.append((t0, t1, place))
        plans[prefix] = tuple(plan)
    return plans


def _step_up_keys(B, water=False):
    """The climb up a block B px high (or out of the shallows onto the bank, `water`: slower, wetter); the face
    STEP_FACE ahead. One foot of each end lifted at a time (a heavy animal's careful climb), but for the scramble: the
    chest held against the face while the forefeet go up, then the belly slid over the lip with the hind legs kicking
    for the top."""
    k = 1.2 if water else 1.0
    T = lambda keys: tuple((round(t * k, 3), v) for t, v in keys)
    dz = T(((0.0, 0.0), (0.5, -5.0), (1.0, -12.0), (1.5, -18.0), (1.8, -20.5), (2.9, -21.0), (3.4, -29.0),
            (3.9, -38.0), (4.4, -47.0), (4.9, -56.0), (5.3, -63.0), (5.8, -72.0), (6.3, -81.0), (6.6, -84.0),
            (6.9, -84.5)))
    sc = _Scene(-STEP_FACE, B)
    feet = _gait(dz, {
        "leg_fl": ((0.15, 0.55, "here"), (1.05, 1.45, "here"), (1.85, 2.35, "there"), (3.2, 3.6, "there"),
                   (4.1, 4.5, "there"), (5.0, 5.4, "there"), (5.85, 6.25, "there")),
        "leg_fr": ((0.6, 1.0, "here"), (1.45, 1.8, "here"), (2.45, 2.95, "there"), (3.65, 4.05, "there"),
                   (4.55, 4.95, "there"), (5.45, 5.85, "there"), (6.25, 6.6, "there")),
        "leg_bl": ((0.0, 0.4, "here"), (0.95, 1.35, "here"), (2.95, 3.35, "here"), (3.8, 5.15, "there!"),
                   (5.6, 6.0, "there"), (6.45, 6.85, "there")),
        "leg_br": ((0.5, 0.9, "here"), (1.4, 1.8, "here"), (3.35, 3.75, "here"), (4.05, 5.3, "there!"),
                   (5.95, 6.35, "there")),
    }, sc, k)
    return {
        "length": 6.9 * k,
        "dz": dz,
        "feet": feet,
        # the legs stay pillars (a hippo's thick shins bent more than this dig into the ground): it rides a little low
        "sag": T(((0.0, 0.5), (0.4, 1.5), (6.2, 1.5), (6.9, 0.5))),
        # the head stretched up and forward as it nears the face, clear of the top until the front has risen
        "neck_lift": T(((0.0, 0.0), (0.45, 6.0 + 0.75 * (B - 16.0)), (2.6, 6.0 + 0.75 * (B - 16.0)), (3.3, 0.0))),
        "bend": T(((0.0, 0.0), (2.4, 0.0), (2.9, 5.0), (3.9, 2.0), (4.9, -2.0), (5.6, 0.0))),
        "look": T(((0.0, 0.0), (0.3, 25.0), (2.3, 25.0), (2.9, 10.0), (3.9, -8.0), (4.9, -14.0), (5.6, -4.0),
                   (6.3, 0.0))),
        "roll": T(((0.0, 0.0), (1.9, 3.0), (2.5, -3.0), (2.9, 0.0), (4.6, 3.0), (5.2, -3.0), (5.6, 0.0))),
        "tail": T(((0.0, 6.0), (3.9, 12.0), (6.0, 6.0))),
        # out of the water: the surface 8/9 of a block over the riverbed it starts on; once out, the ears spun to throw
        # the water off (real hippos do) and a blink
        **({"surface": B * 8.0 / 9.0,
            "ears": lambda t: 24.0 * pulse(t, 6.9 * k - 1.3, 0.08, 0.55, 0.15) * math.sin(2.0 * math.pi * (t - 6.9 * k + 1.3) * 6.0),
            "blink": lambda t: pulse(t, 6.9 * k - 0.6, 0.06, 0.05, 0.1)} if water else {}),
    }


def _step_down_keys(B):
    """The way down a block B px high, the edge DOWN_EDGE ahead (its forefeet a step short of it). A hippo's legs are
    shorter than the block, so it never stands one foot up and one down: it looks over, slides its chest over the lip
    on its belly with both forelegs reaching down until they touch, walks its front on while its hind end comes to the
    edge, then the rump slides over the same way, the hind legs reaching down, and lands with a jolt."""
    dz = ((0.0, 0.0), (0.6, -4.0), (1.2, -10.0), (2.0, -22.0), (2.4, -28.0), (3.0, -35.0), (3.6, -42.0), (4.2, -50.0),
          (4.6, -55.0), (5.0, -58.0), (5.6, -61.5), (6.2, -66.0), (6.7, -69.5), (7.0, -70.0))
    sc = _Scene(-DOWN_EDGE, -B)
    feet = _gait(dz, {
        "leg_fl": ((0.1, 0.5, "here"), (1.3, 3.0 + 0.12 * (B - 16.0), "there!"), (3.3, 3.65, "there"), (4.1, 4.45, "there"),
                   (4.9, 5.3, "there"), (5.95, 6.3, "there")),
        "leg_fr": ((0.45, 0.85, "here"), (1.45, 3.15 + 0.12 * (B - 16.0), "there!"), (3.7, 4.05, "there"), (4.5, 4.85, "there"),
                   (5.35, 5.7, "there"), (6.35, 6.7, "there")),
        "leg_bl": ((0.25, 0.65, "here"), (1.4, 1.8, "here"), (2.3, 2.7, "here"), (3.15, 3.5, "here"), (4.3, 4.85, "there!"),
                   (5.05, 5.4, "there"), (5.85, 6.2, "there")),
        "leg_br": ((0.7, 1.1, "here"), (1.85, 2.25, "here"), (2.75, 3.1, "here"), (3.55, 3.9, "here"), (4.35, 4.95, "there!"),
                   (5.45, 5.8, "there"), (6.25, 6.6, "there")),
    }, sc)
    return {
        "length": 7.0,
        "dz": dz,
        "feet": feet,
        "sag": ((0.0, 0.5), (0.4, 1.5), (6.3, 1.5), (7.0, 0.5)),
        "sag_f": ((0.0, 0.5), (0.4, 1.5), (3.0, 1.5), (3.2, 2.2), (3.5, 1.5), (6.3, 1.5), (7.0, 0.5)),
        "sag_h": ((0.0, 0.5), (0.4, 1.5), (4.65, 1.5), (4.85, 2.2), (5.2, 1.5), (5.8, 0.5)),
        "look": ((0.0, 0.0), (0.6, -30.0), (1.6, -28.0), (2.2, -20.0), (2.6, -5.0), (3.4, 6.0), (4.6, 2.0), (5.3, 0.0)),
        "roll": ((0.0, 0.0), (1.0, 2.5), (1.6, -2.5), (2.2, 0.0), (3.2, -2.5), (3.8, 2.5), (4.4, 0.0)),
        # the tail held out level as the rump slides off, so it drapes over the edge instead of hanging into it
        "tail": ((0.0, 6.0), (2.0, 14.0), (3.6, 24.0), (4.3, 60.0), (4.6, 88.0), (6.0, 88.0), (6.5, 30.0), (6.9, 6.0)),
        # going down, a leg that lets go reaches forward and down for the ground ahead (folded back, it went into the
        # face behind it): thigh, knee, foot (degrees)
        "hang": {"fore": (-38.0, 14.0, 4.0), "hind": (-40.0, 12.0, 4.0)},
    }


DOWN_EDGE = 16.0        # down: the edge this far ahead of where the move starts (px): the fore toes 1.75 px short of it
STEP_FACE = 42.0        # up: the step's face this far ahead of where the move starts (px): the snout 2 px short of it


def move_name(kind, size):
    return f"{kind}_{round(size * 1000)}"


def _move_clips():
    """Every move for every size: step_up, step_down and out (of the shallows onto the bank), as <kind>_<size*1000>."""
    out = {}
    for size in MOVE_SIZES:
        B = move_block_px(size)
        out[move_name("step_up", size)] = _move_clip(_step_up_keys(B), _Scene(-STEP_FACE, B),
                                                     {"0": f"step {-STEP_FACE:g} {B:.3f}"})
        out[move_name("step_down", size)] = _move_clip(_step_down_keys(B), _Scene(-DOWN_EDGE, -B),
                                                       {"0": f"step {-DOWN_EDGE:g} {-B:.3f}"})
        out[move_name("out", size)] = _move_clip(_step_up_keys(B, water=True), _Scene(-STEP_FACE, B),
                                                 {"0": f"water out {-STEP_FACE:g} {B:.3f} {B * 8.0 / 9.0:.3f} 0"})
    return out


def move_specs():
    """The hippo's moves for the shared move file (model_parts.block_moves; the game's BlockMoves)."""
    return [
        {"name": "up", "clip": lambda s: move_name("step_up", s), "ahead_px": STEP_FACE,
         "starts_afloat": False, "ends_afloat": False, "goes_down": False, "end_level": 1},
        {"name": "down", "clip": lambda s: move_name("step_down", s), "ahead_px": DOWN_EDGE,
         "starts_afloat": False, "ends_afloat": False, "goes_down": True, "end_level": -1},
        # out of the shallows: wading on the riverbed (not afloat), onto a bank a block up from the bed
        {"name": "out", "clip": lambda s: move_name("out", s), "ahead_px": STEP_FACE,
         "starts_afloat": False, "ends_afloat": False, "goes_down": False, "end_level": 1},
    ]


def clips():
    return {
        "idle":            _clip_idle(),
        "walk":            _clip_walk(),
        "charge":          _clip_charge(),
        "float":           _clip_float(),
        "underwater_walk": _clip_underwater_walk(),
        "yawn":            _clip_yawn(),
        "bite":            _clip_bite(),
        "ear_flick":       _clip_ear_flick(),
        "graze":           _clip_graze(),
        "rest_down":       _clip_rest_down(),
        "rest":            _clip_rest(),
        "rest_up":         _clip_rest_up(),
        "dung_flick":      _clip_dung_flick(),
        "death":           _clip_death(),
        **_move_clips(),
    }


WORKSHOP_CLIPS = clips
WORKSHOP_CLIP_INFO = {
    # (the viewer's format: water clips by name and the surface in blocks over the feet; notes shown under the clips)
    "water": ["float", "underwater_walk"],
    "surface": 21.5 / 16.0,
    "notes": {
        "step_up_1000": "Up a block (the bull): chin up and a squat back on its haunches, the head stretched up clear of "
                        "the top, a forefoot hauled onto the lip, then the other; the belly slides over the edge while "
                        "the hind legs kick for the top; it walks on and settles. Played on the game's own path.",
        "step_down_1000": "Down a block: a look over the edge, the chest slid over the lip on its belly with both "
                          "forelegs reaching down, the front walked on, then the rump slides off and lands on bent "
                          "hind legs.",
        "out_1000": "Out of the shallows onto the bank (wading on the riverbed, the water to its flanks): the climb up, "
                    "slower and heavier, ending with the ears spun to throw the water off.",
    },
}


def workshop_look():
    """Returns (bones, PNG bytes, texture width, height) for tools/workshop.py."""
    bones = build_bones()
    bones, rows, w, h = paint_faces(bones, texels=1, colour=paint_colour)
    return bones, png(rows), w, h


def export_game():
    """Writes geo, texture and animations into the savanna mod (write_assets auto-applies model_parts.game_faces!),
    and the moves' paths into its move file (assets/antcolonies/block_moves/hippo.json, read by the game's BlockMoves)."""
    from model_parts import block_moves, write_block_moves
    bones, png_bytes, w, h = workshop_look()
    all_clips = clips()
    write_assets({
        "geo/entity/hippo.geo.json": geo("hippo", bones, w, h, (5.5, 2.5, [0, 1.1, 0])),
        "textures/entity/hippo.png": png_bytes,
        "animations/entity/hippo.animation.json": animations(all_clips),
    }, mod="savanna")
    write_block_moves("hippo", block_moves(all_clips, MOVE_SIZES, move_block_px, move_specs()),
                      "tools/hippo_models.py game")


if __name__ == "__main__":
    if "game" in sys.argv[1:]:
        export_game()
    else:
        bones, png_bytes, w, h = workshop_look()
        all_clips = clips()
        print(f"hippo: {len(bones)} bones, {sum(len(b['cubes']) for b in bones)} boxes, texture {w}x{h}, {len(all_clips)} clips")
