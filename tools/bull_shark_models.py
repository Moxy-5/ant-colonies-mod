"""The Bull Shark (Carcharhinus leucas) model maker (M9 river zones & savanna life; Fish family, 2026-11-22).

Workshop Stage 3: Sculpted 3D Detail (v3)
- Built from the user's Stage 2 feedback: body stays clean & sturdy while ALL 7 fins, the mouth, and the 5 gills get
  hero-grade 3D Minecraft sculpting!
- Base 1.0 grown model = 5.5 blocks long (88.0 model px at 16 px = 1 block), scaling cleanly 0.91x (5.0b) .. 1.27x (7.0b).
- Fins (Priority #1):
  - Pectorals: fleshy shoulder root fairing + 7-tier 3D hydrofoil wings (thick rounded dark leading edge tapering into
    a thinner falcate swept trailing blade with pale undersides).
  - 1st Dorsal: muscular dorsal ridge root, thick leading spine, thin concave trailing blade + iconic free rear tip.
  - Heterocercal Tail: caudal peduncle keel, precaudal pit, thick leading edges on both lobes, thin swept trailing webs,
    crisp V-fork, and real bull-shark subterminal notch near the upper tip.
  - 2nd Dorsal, Pelvics & Anal: sculpted swept leading edges, thin tapered trailing webs, free rear tips, pale pelvic undersides.
- Mouth (Priority #2):
  - Underslung wide gape slightly parted even at rest so crisp 3D triangular upper & lower teeth peek past dusty-pink
    gums; full horseshoe dental arc (upper + lower teeth), sculpted pink palate & tongue, deep dark throat cavity, and
    underside nostril slits under the blunt overhanging snout.
- Gills (Priority #3):
  - 5 true 3D recessed dark vertical gill slits per flank framed by 6 proud sculpted muscular gill ribs (angled slightly
    backward, tapering top/bottom, transitioning from dark flank to pale belly).
- Eyes & Brows:
  - Predatory overhanging brow ridge, glossy dark eye with white glint, and pale nictitating-membrane eyelids
    (`lid_left`, `lid_right`) that flick shut during bites.

Units are model pixels (16 a block): head toward -Z, up +Y, left +X, lowest pectoral tip y = 0, centerline x = 0.
Outline x (0 = upper tail tip, 88 = blunt snout tip) converts to model z via oz(ox) = LENGTH / 2.0 - ox.

    python tools/workshop.py snapshot bull_shark --note "Stage 3 sculpted detail: fins, mouth & 5 gills"
    python tools/workshop.py versions bull_shark
    python tools/workshop.py serve bull_shark
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import cube, mix, shade, paint_faces, png, geo, animations, write_assets  # noqa: E402

# --- workshop sliders (editable live in tools/workshop.py serve bull_shark) ------------------------------------------
LENGTH = 88.0           # 5.5 blocks base (scales 0.91x = 5.0b .. 1.27x = 7.0b)
BODY_HW = 12.6          # muscular shoulder half-width (px)
HUMP_H = 29.2           # top of shoulder hump (px; belly sits at y=8.0)
DORSAL_H = 38.2         # 1st dorsal fin tip height (px)
PEC_SPAN = 24.5         # outer pectoral fin tip |x| (px)
PEC_DIP = 0.2           # lowest pectoral tip y (px)
JAW_OPEN = 0.0          # preview-only lower jaw opening degrees (0 resting slight-part .. 48 wide bite)
LID_CLOSE = 0.0         # preview-only nictitating membrane blink (0 open .. 1 shut pale white)

WORKSHOP_SHAPE = [
    ("BODY_HW",   "Shoulder half-width (px)", 10.5, 14.5, 0.4),
    ("HUMP_H",    "Shoulder hump height (px)", 26.5, 31.5, 0.5),
    ("DORSAL_H",  "1st dorsal tip height (px)", 34.0, 41.0, 0.5),
    ("PEC_SPAN",  "Pectoral fin span (px)",    20.0, 28.0, 0.5),
    ("PEC_DIP",   "Pectoral tip height (px)",  -2.0,  4.0, 0.4),
    ("JAW_OPEN",  "Preview jaw gape (deg)",     0.0, 48.0, 4.0),
    ("LID_CLOSE", "Nictitating eyelid (0..1)",  0.0,  1.0, 0.25),
]

WORKSHOP_PARTS = {
    "head":       ["head", "lid_left", "lid_right"],
    "jaw":        ["jaw", "tongue"],
    "chest":      ["chest"],
    "dorsal 1":   ["dorsal_1"],
    "pectorals":  ["pectoral_left", "pectoral_right"],
    "torso":      ["torso"],
    "rear":       ["rear", "pelvic_left", "pelvic_right"],
    "peduncle":   ["peduncle", "dorsal_2", "anal_fin"],
    "tail":       ["tail_base", "tail_fin"],
}

WORKSHOP_LOOK = "plain"   # Stage 3: crisp semantic tones before Stage 4 rich pixel-art paint pass
TEXELS = 1.0              # Stage 4 upgrades to 2.0 (256x256) for rich Minecraft block-grain countershading


def oz(ox):
    """Outline x (0 upper tail tip .. 88 blunt snout tip) -> GeckoLib model z (+44 tail .. -44 snout)."""
    return LENGTH / 2.0 - ox


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


def _oval_slice(cubes, ox0, ox1, y_bot, y_top, hw, belly_y=None, crest=True):
    """One crisp stepped-Minecraft oval cross-section (3-4 vertical tiers: pale belly -> wide flank -> upper -> crest)."""
    h = y_top - y_bot
    if h < 2.0 or hw < 1.0:
        sym_box(cubes, max(0.8, hw), y_bot, y_top, ox0, ox1, "body_dark")
        return
    by = belly_y if belly_y is not None else (y_bot + h * 0.28)
    by = max(y_bot + 1.4, min(y_top - 2.0, by))
    sym_box(cubes, round(hw * 0.78, 2), y_bot, by + 0.4, ox0, ox1, "belly")
    t2 = y_bot + h * 0.68
    sym_box(cubes, round(hw, 2), by - 0.3, t2, ox0, ox1, "flank")
    t3 = y_bot + h * 0.88 if crest else y_top
    sym_box(cubes, round(hw * 0.72, 2), t2 - 0.4, t3, ox0, ox1, "body_dark")
    if crest and (y_top - t3) >= 0.9:
        sym_box(cubes, round(hw * 0.42, 2), t3 - 0.3, y_top, ox0, ox1, "body_cap")


def build_bones():
    """Builds the Stage 3 Sculpted Bull Shark rig (~225 boxes, 0 slanted, well under 400 large-animal budget)."""
    ws = BODY_HW / 12.6
    hs = (HUMP_H - 8.0) / (29.2 - 8.0)

    def sy(y):
        if y <= 8.0:
            return y
        return 8.0 + (y - 8.0) * hs

    # =================================================================================================================
    # 1. HEAD: Blunt Bull-Shark Snout, Underside Nostrils, Brow Ridges, Upper Dental Arc, & 5 Sculpted 3D Gill Slits
    # =================================================================================================================
    head_c = []

    # Blunt rounded bull-shark snout tip (broad & muscular)
    sym_box(head_c, round(5.2 * ws, 2), sy(16.8), sy(21.6), 84.4, 88.0, "body_dark")
    sym_box(head_c, round(4.5 * ws, 2), sy(15.6), sy(17.4), 83.6, 87.3, "belly")
    # Snout bevel step
    sym_box(head_c, round(6.9 * ws, 2), sy(15.6), sy(22.8), 81.4, 85.2, "flank")
    sym_box(head_c, round(5.3 * ws, 2), sy(22.2), sy(24.0), 81.0, 84.8, "body_cap")
    sym_box(head_c, round(5.9 * ws, 2), sy(14.5), sy(16.4), 81.0, 84.6, "belly")
    # Recessed sleek shark nostrils (nares) tucked cleanly on the underside of the overhanging blunt snout!
    for sgn in (-1, 1):
        box(head_c, sgn * round(2.8 * ws, 2), sgn * round(3.85 * ws, 2), sy(15.2), sy(15.8), 84.4, 85.8, "gill_slit")

    # Mid-snout / orbital skull (above the underslung toothy mouth)
    sym_box(head_c, round(8.3 * ws, 2), sy(15.4), sy(23.8), 77.2, 82.2, "flank")
    sym_box(head_c, round(6.4 * ws, 2), sy(23.2), sy(25.4), 76.6, 82.0, "body_cap")
    # Upper lip cheek rim flanking the recessed mouth cavity
    sym_box(head_c, round(7.2 * ws, 2), sy(14.2), sy(16.0), 77.2, 82.2, "belly")

    # Predatory brow ridges + glossy dark shark eyes with crisp 1px glint
    for sgn in (-1, 1):
        # Sculpted angry brow ridge overhanging the eye
        box(head_c, sgn * round(7.6 * ws, 2), sgn * round(9.1 * ws, 2), sy(20.9), sy(22.3), 78.0, 82.2, "body_dark")
        # Dark recessed eye socket & glossy shark eye
        box(head_c, sgn * round(7.9 * ws, 2), sgn * round(8.95 * ws, 2), sy(18.6), sy(21.0), 78.6, 81.4, "eye")
        # Crisp bright specular eye glint (front-upper corner, matching the hero render!)
        box(head_c, sgn * round(8.88 * ws, 2), sgn * round(9.08 * ws, 2), sy(20.0), sy(20.8), 80.3, 81.1, "tooth")

    # --- UPPER MOUTH INTERIOR: Dusty-pink palate, deep dark throat, & curved 3D triangular upper teeth ---
    # Deep dark gullet / throat recess behind gums
    sym_box(head_c, round(5.2 * ws, 2), sy(12.2), sy(15.6), 72.0, 75.5, "throat")
    # Sculpted dusty-pink upper palate & gum arch (recessed cleanly inside the upper lip)
    sym_box(head_c, round(6.1 * ws, 2), sy(13.8), sy(15.0), 74.2, 81.4, "gum")
    sym_box(head_c, round(5.2 * ws, 2), sy(14.0), sy(15.2), 80.5, 82.0, "gum")

    # Upper jaw 3D teeth (9 broad triangular shark teeth curving along the overhanging horseshoe arch!)
    # Each tooth has a slightly broader gum-base + sharp pointed tip peeking cleanly below the upper lip!
    upper_teeth = [
        # (center_x, ox_center, width_x, depth_z, drop_y)
        ( 0.0,  81.3, 1.05, 0.70, 1.25),   # central symphysis tooth
        ( 1.55, 81.0, 1.05, 0.72, 1.30),
        (-1.55, 81.0, 1.05, 0.72, 1.30),
        ( 3.15, 80.2, 1.00, 0.78, 1.28),
        (-3.15, 80.2, 1.00, 0.78, 1.28),
        ( 4.65, 79.0, 0.90, 0.88, 1.18),
        (-4.65, 79.0, 0.90, 0.88, 1.18),
        ( 5.80, 77.4, 0.80, 0.95, 1.05),
        (-5.80, 77.4, 0.80, 0.95, 1.05),
        ( 6.35, 75.6, 0.75, 0.95, 0.92),
        (-6.35, 75.6, 0.75, 0.95, 0.92),
    ]
    for tx, tox, tw, td, drop in upper_teeth:
        # Upper tooth crown (wider base)
        box(head_c, (tx - tw * 0.5) * ws, (tx + tw * 0.5) * ws,
            sy(14.0 - drop * 0.55), sy(14.2), tox - td * 0.5, tox + td * 0.5, "tooth")
        # Sharp triangular tip
        box(head_c, (tx - tw * 0.28) * ws, (tx + tw * 0.28) * ws,
            sy(14.0 - drop), sy(14.0 - drop * 0.45), tox - td * 0.34, tox + td * 0.34, "tooth_tip")

    # Rear head & cheek collar behind mouth (ox = 72.0..77.6)
    _oval_slice(head_c, 72.0, 77.8, sy(11.8), sy(26.4), 9.9 * ws, belly_y=sy(15.0), crest=True)

    # --- 5 SCULPTED 3D GILL SLITS & 6 MUSCULAR GILL RIBS (ox = 61.5..72.6) ---
    # Inner core slightly inset (-0.65 px on flanks) so the 5 recessed gill slits physically sit DEEPER than the ribs!
    _oval_slice(head_c, 66.5, 72.6, sy(8.6), sy(27.8), 10.45 * ws, belly_y=sy(14.2), crest=True)
    _oval_slice(head_c, 61.5, 67.2, sy(8.2), sy(28.5), 11.05 * ws, belly_y=sy(14.0), crest=True)

    # Carve 5 deeply recessed dark gill slots + 6 proud sculpted muscular gill ribs on BOTH flanks!
    # Slit spacing from front (ox=71.2) to rear (ox=63.2), slightly taller in middle (gills 2-4), slightly swept back!
    gill_oxs = [70.9, 69.0, 67.1, 65.2, 63.3]
    gill_hs  = [8.2,  9.2,  9.8,  9.4,  8.4]
    for sgn in (-1, 1):
        # 5 deeply recessed dark charcoal gill openings (2 stacked segments shifted 0.35 px for natural backward slant!)
        for idx, (gox, gh) in enumerate(zip(gill_oxs, gill_hs)):
            t_flank = (gox - 61.5) / (72.6 - 61.5)
            local_hw = (11.25 - 0.85 * t_flank) * ws
            y_mid = sy(16.6 - idx * 0.18)
            y_lo = y_mid - gh * 0.5
            y_hi = y_mid + gh * 0.5
            # Deep dark gill opening (upper half slightly forward, lower half swept 0.35 px back!)
            box(head_c, sgn * (local_hw - 0.75), sgn * (local_hw + 0.05),
                y_mid - 0.2, y_hi, gox - 0.32, gox + 0.42, "gill_slit")
            box(head_c, sgn * (local_hw - 0.75), sgn * (local_hw + 0.05),
                y_lo, y_mid + 0.2, gox - 0.58, gox + 0.16, "gill_slit")

        # 6 proud sculpted inter-gill septum ribs flanking the 5 slits (flank upper half + pale lower half!)
        rib_oxs = [71.85, 69.95, 68.05, 66.15, 64.25, 62.30]
        for idx, rox in enumerate(rib_oxs):
            t_flank = max(0.0, min(1.0, (rox - 61.5) / (72.6 - 61.5)))
            local_hw = (11.25 - 0.85 * t_flank) * ws
            rib_w = 0.52 if 0 < idx < 5 else 0.72
            # Upper dark-flank portion of the proud gill rib
            box(head_c, sgn * (local_hw - 0.2), sgn * (local_hw + 0.58),
                sy(14.5), sy(22.2 - idx * 0.12), rox - rib_w, rox + rib_w, "flank")
            # Lower pale-belly portion of the proud gill rib (matches the hero countershading across the gills!)
            box(head_c, sgn * (local_hw - 0.25), sgn * (local_hw + 0.50),
                sy(11.4), sy(14.8), rox - rib_w - 0.18, rox + rib_w - 0.18, "belly")

    # =================================================================================================================
    # 1a. NICTITATING MEMBRANE EYELIDS (real bull shark eye shields — 100% hidden inside head at rest, flick pale white on bite!)
    # =================================================================================================================
    def build_lid(sgn):
        c = []
        # Hidden 0.5 px INSIDE the flank at LID_CLOSE=0; slides outward & up over the dark eye when LID_CLOSE -> 1!
        x_out = 7.45 + LID_CLOSE * 1.66
        y_off = LID_CLOSE * 2.15
        box(c, sgn * round((x_out - 0.35) * ws, 2), sgn * round(x_out * ws, 2),
            sy(16.6 + y_off), sy(18.75 + y_off), 78.65, 81.35, "lid")
        return c

    # =================================================================================================================
    # 1b. UNDERSLUNG HINGED LOWER JAW: Pale chin, dusty-pink lower gum ridge, 9 3D lower teeth, & fleshy tongue!
    # =================================================================================================================
    jaw_c = []
    # Lower jaw pale ventral chin (sits ~1.1 px below upper lip even at rest so teeth peek out menacingly!)
    sym_box(jaw_c, round(7.1 * ws, 2), sy(8.8), sy(12.2), 72.4, 78.6, "belly")
    sym_box(jaw_c, round(5.8 * ws, 2), sy(9.2), sy(12.3), 78.0, 81.2, "belly")
    sym_box(jaw_c, round(4.2 * ws, 2), sy(9.8), sy(12.2), 80.6, 82.0, "belly")
    # Sculpted dusty-pink lower gum arcade
    sym_box(jaw_c, round(5.8 * ws, 2), sy(11.7), sy(12.8), 73.6, 80.8, "gum")
    sym_box(jaw_c, round(4.6 * ws, 2), sy(11.8), sy(12.8), 80.0, 81.5, "gum")
    # Recessed inner mouth floor
    sym_box(jaw_c, round(4.8 * ws, 2), sy(11.4), sy(12.3), 72.8, 76.2, "throat")

    # Lower jaw 3D pointed shark teeth (9 teeth curving inside the upper arc!)
    lower_teeth = [
        ( 0.0,  80.8, 0.82, 0.62, 1.10),
        ( 1.40, 80.5, 0.80, 0.62, 1.12),
        (-1.40, 80.5, 0.80, 0.62, 1.12),
        ( 2.85, 79.7, 0.78, 0.68, 1.08),
        (-2.85, 79.7, 0.78, 0.68, 1.08),
        ( 4.20, 78.5, 0.72, 0.76, 1.00),
        (-4.20, 78.5, 0.72, 0.76, 1.00),
        ( 5.20, 76.9, 0.68, 0.82, 0.90),
        (-5.20, 76.9, 0.68, 0.82, 0.90),
    ]
    for tx, tox, tw, td, rise in lower_teeth:
        box(jaw_c, (tx - tw * 0.45) * ws, (tx + tw * 0.45) * ws,
            sy(12.4), sy(12.5 + rise * 0.55), tox - td * 0.45, tox + td * 0.45, "tooth")
        box(jaw_c, (tx - tw * 0.25) * ws, (tx + tw * 0.25) * ws,
            sy(12.5 + rise * 0.45), sy(12.5 + rise), tox - td * 0.30, tox + td * 0.30, "tooth_tip")

    # Fleshy shark tongue (child of jaw so it moves cleanly when biting/gulping!)
    tongue_c = []
    sym_box(tongue_c, round(3.8 * ws, 2), sy(11.8), sy(13.0), 73.8, 79.2, "tongue")
    sym_box(tongue_c, round(2.6 * ws, 2), sy(12.1), sy(13.1), 78.4, 80.2, "tongue")

    # =================================================================================================================
    # 2. CHEST / HEAVY MUSCULAR SHOULDERS (ox = 49.5..63.0)
    # =================================================================================================================
    chest_c = []
    _oval_slice(chest_c, 55.5, 63.0, sy(8.0), sy(29.0), 12.2 * ws, belly_y=sy(14.0), crest=True)
    _oval_slice(chest_c, 49.5, 56.5, sy(8.0), sy(29.2), 12.6 * ws, belly_y=sy(14.0), crest=True)

    # --- 2a. SCULPTED 1ST DORSAL FIN (Muscular root fairing + thick leading spine + concave trailing blade + free rear tip!) ---
    d1_c = []
    d1_base = sy(27.8)
    d1_h = max(6.5, DORSAL_H - d1_base)
    # Muscular root flare seamlessly rising out of the back hump
    sym_box(d1_c, 2.35, d1_base - 0.8, d1_base + d1_h * 0.14, 43.0, 58.2, "body_cap")
    # 7 sculpted tiers: thick dark leading spine + thinner swept concave trailing web!
    d1_tiers = [
        # (f0,   f1,   lead_back, lead_front, trail_back, hw_lead, hw_trail)
        (0.08, 0.24, 52.0, 57.6, 43.2, 1.55, 0.92),
        (0.20, 0.38, 50.6, 55.8, 43.8, 1.40, 0.82),
        (0.34, 0.52, 49.4, 54.0, 44.2, 1.25, 0.72),
        (0.48, 0.66, 48.2, 52.2, 44.3, 1.12, 0.64),
        (0.62, 0.79, 47.0, 50.6, 44.2, 0.98, 0.58),
        (0.75, 0.91, 46.0, 49.2, 44.0, 0.86, 0.52),
        (0.87, 1.00, 44.4, 47.8, 43.8, 0.74, 0.46),
    ]
    for f0, f1, lx0, lx1, tx0, hwl, hwt in d1_tiers:
        y0, y1 = d1_base + d1_h * f0, d1_base + d1_h * f1
        sym_box(d1_c, hwl, y0, y1, lx0, lx1, "fin_lead")
        sym_box(d1_c, hwt, y0, y1, tx0, lx0 + 0.8, "fin_dark")
    # Iconic sharp bull-shark "free rear tip" extending cleanly behind the dorsal base!
    sym_box(d1_c, 0.65, d1_base + 0.1, d1_base + 2.1, 39.8, 44.0, "fin_tip")

    # --- 2b. SCULPTED 3D HYDROFOIL PECTORAL FINS (Fleshy shoulder fairing + 7-tier curved wing + dark lead / pale underside!) ---
    def build_pec(sgn):
        c = []
        z_root = 10.2 * ws
        span = max(8.5, PEC_SPAN - z_root)
        y_root = sy(11.4)
        y_tip = PEC_DIP

        # Fleshy muscular shoulder fairing where the pectoral joins the flank (dark top, pale underside!)
        box(c, sgn * (z_root - 1.2), sgn * (z_root + 2.4), y_root - 1.4, y_root + 1.6, 51.8, 61.2, "flank")
        box(c, sgn * (z_root - 1.4), sgn * (z_root + 2.2), y_root - 2.6, y_root - 1.0, 52.0, 60.4, "belly")

        # 7 tight overlapping hydrofoil wing sections sweeping outward, backward & gently downward (falcate curve!)
        # (s0, s1, ox_trail, ox_lead, thick, rear_cut)
        wing_steps = [
            (0.00, 0.18, 52.4, 60.8, 3.2),
            (0.13, 0.32, 51.2, 59.2, 2.8),
            (0.27, 0.46, 50.0, 57.6, 2.45),
            (0.41, 0.60, 48.8, 55.8, 2.15),
            (0.55, 0.74, 47.6, 54.0, 1.85),
            (0.69, 0.88, 46.6, 52.2, 1.55),
            (0.83, 1.00, 45.8, 50.2, 1.25),
        ]
        for idx, (s0, s1, ox0, ox1, th) in enumerate(wing_steps):
            xm = (s0 + s1) * 0.5
            # Smooth dihedral curve: gentle near shoulder, slightly stronger outer sweep
            yc = y_root - 0.9 - (y_root - 1.4 - y_tip) * (xm ** 0.95)
            x_in = sgn * (z_root + span * s0)
            x_out = sgn * (z_root + span * s1)
            chord = ox1 - ox0
            ox_mid = ox0 + chord * 0.48
            # Thick rounded dark leading hydrofoil edge (front half of wing chord)
            box(c, x_in, x_out, yc - th * 0.22, yc + th * 0.62, ox_mid - 0.5, ox1, "fin_lead")
            # Pale underside along leading & mid blade (clean flush overlap, dark dusky tip on outer 2 steps!)
            under_part = "fin_tip" if idx >= 5 else "belly"
            box(c, x_in, x_out, yc - th * 0.52, yc - th * 0.14, ox0 + 0.6, ox1 - 0.4, under_part)
            # Thinner, sharp dark-slate swept trailing edge blade (rear half of wing chord)
            trail_part = "fin_tip" if idx >= 4 else "fin_dark"
            box(c, x_in, x_out, yc - th * 0.18, yc + th * 0.38, ox0, ox_mid + 0.5, trail_part)

        # Free rear inner corner lobe (classic shark pectoral inner notch!)
        box(c, sgn * (z_root + 0.5), sgn * (z_root + 4.4), y_root - 2.2, y_root - 0.4, 49.4, 53.2, "fin_dark")
        return c

    # =================================================================================================================
    # 3. TORSO & 4. REAR TRUNK + SCULPTED PELVIC FINS (ox = 27.5..51.0)
    # =================================================================================================================
    torso_c = []
    _oval_slice(torso_c, 43.5, 51.0, sy(8.4), sy(28.4), 11.4 * ws, belly_y=sy(13.8), crest=True)
    _oval_slice(torso_c, 37.5, 44.5, sy(9.2), sy(26.6), 9.8 * ws, belly_y=sy(14.0), crest=True)

    rear_c = []
    _oval_slice(rear_c, 32.5, 39.0, sy(10.5), sy(24.6), 8.2 * ws, belly_y=sy(14.5), crest=True)
    _oval_slice(rear_c, 27.5, 33.5, sy(12.2), sy(23.2), 6.6 * ws, belly_y=sy(15.2), crest=True)

    # Sculpted paired pelvic fins (thick leading edge + thinner trailing blade + pale underside + free rear tip!)
    def build_pelvic(sgn):
        c = []
        # Root fairing
        box(c, sgn * (4.4 * ws), sgn * (6.8 * ws), sy(10.2), sy(12.0), 32.4, 37.2, "belly")
        # Leading swept blade (3 overlapping steps)
        box(c, sgn * (5.6 * ws), sgn * (8.2 * ws), sy(9.4), sy(11.2), 33.2, 36.8, "fin_lead")
        box(c, sgn * (7.6 * ws), sgn * (10.4 * ws), sy(8.2), sy(9.8), 31.8, 35.2, "fin_tip")
        # Thin trailing web + pale underside + free rear point
        box(c, sgn * (5.2 * ws), sgn * (8.6 * ws), sy(9.0), sy(10.4), 31.0, 33.8, "fin_dark")
        box(c, sgn * (5.8 * ws), sgn * (9.2 * ws), sy(8.6), sy(9.5), 32.0, 35.6, "belly")
        box(c, sgn * (4.8 * ws), sgn * (6.8 * ws), sy(9.4), sy(10.6), 29.8, 32.2, "fin_tip")
        return c

    # =================================================================================================================
    # 5. CAUDAL PEDUNCLE + MUSCULAR KEEL + SCULPTED 2ND DORSAL & ANAL FINS (ox = 16.0..28.8)
    # =================================================================================================================
    ped_c = []
    _oval_slice(ped_c, 21.8, 28.8, sy(13.8), sy(22.0), 5.2 * ws, belly_y=sy(16.0), crest=False)
    _oval_slice(ped_c, 16.0, 22.5, sy(15.0), sy(20.6), 3.3 * ws, belly_y=sy(16.8), crest=False)
    # Subtle muscular lateral caudal keel (reinforces the powerful tail stalk!)
    for sgn in (-1, 1):
        box(ped_c, sgn * (2.9 * ws), sgn * (3.85 * ws), sy(17.1), sy(18.5), 16.4, 23.0, "body_dark")

    # Sculpted 2nd Dorsal Fin (thick leading spine + thin swept trailing blade + sharp free rear tip!)
    d2_c = []
    sym_box(d2_c, 1.15, sy(21.0), sy(22.8), 24.4, 28.0, "fin_lead")
    sym_box(d2_c, 0.90, sy(22.5), sy(24.3), 23.4, 26.6, "fin_lead")
    sym_box(d2_c, 0.72, sy(23.9), sy(25.6), 22.4, 25.0, "fin_tip")
    sym_box(d2_c, 0.65, sy(21.4), sy(23.6), 22.2, 24.8, "fin_dark")
    sym_box(d2_c, 0.55, sy(21.0), sy(22.2), 20.4, 22.8, "fin_tip")   # free rear tip

    # Sculpted Anal Fin (opposite 2nd dorsal — leading edge + concave trailing web + free rear tip!)
    anal_c = []
    sym_box(anal_c, 1.05, sy(13.2), sy(14.9), 24.4, 27.6, "fin_lead")
    sym_box(anal_c, 0.80, sy(12.0), sy(13.6), 23.2, 26.0, "fin_dark")
    sym_box(anal_c, 0.65, sy(11.3), sy(12.5), 22.4, 24.6, "fin_tip")
    sym_box(anal_c, 0.55, sy(13.2), sy(14.3), 20.8, 23.4, "fin_tip")  # free rear tip

    # =================================================================================================================
    # 6 & 7. SCULPTED HETEROCERCAL TAIL FIN (Thick leading spines, thin swept trailing blades, V-fork & subterminal notch!)
    # =================================================================================================================
    tail_base_c = []
    # Muscular caudal root + crisp central fork webbing
    sym_box(tail_base_c, 2.05, sy(14.0), sy(20.0), 12.6, 17.4, "flank")
    sym_box(tail_base_c, 1.50, sy(14.4), sy(17.2), 12.4, 17.2, "belly")
    sym_box(tail_base_c, 1.05, sy(14.8), sy(19.6), 9.4, 13.2, "fin_dark")

    # Lower caudal lobe (thick dark leading spine + thin swept trailing blade + dusky tip)
    lower_steps = [
        # (y0,   y1,   lead_x0, lead_x1, trail_x0, hwl,  hwt)
        (11.4, 14.6, 12.2, 16.0, 9.4,  1.40, 0.82),
        ( 8.6, 11.8, 10.6, 14.2, 8.6,  1.18, 0.72),
        ( 5.4,  9.0,  9.0, 12.4, 7.6,  0.96, 0.60),
    ]
    for y0, y1, lx0, lx1, tx0, hwl, hwt in lower_steps:
        sym_box(tail_base_c, hwl, sy(y0), sy(y1), lx0, lx1, "fin_lead")
        sym_box(tail_base_c, hwt, sy(y0), sy(y1), tx0, lx0 + 0.8, "fin_tip" if y0 < 9.0 else "fin_dark")

    # Upper caudal lobe (child bone `tail_fin` for fluid whip flex — thick leading spine + thin trailing web + subterminal notch!)
    tail_fin_c = []
    upper_steps = [
        # (y0,   y1,   lead_x0, lead_x1, trail_x0, hwl,  hwt,  tip_dark)
        (19.0, 22.0, 11.6, 16.6, 7.4,  1.45, 0.82, False),
        (21.6, 24.4,  9.6, 15.0, 6.2,  1.30, 0.74, False),
        (24.0, 26.8,  7.4, 13.0, 4.8,  1.16, 0.66, False),
        (26.4, 29.0,  5.0, 10.6, 3.0,  1.02, 0.58, True),
        # Subterminal notch step (distinct shark-tail rear cutout right below the upper tip!)
        (28.6, 30.8,  2.8,  8.2, 2.2,  0.92, 0.54, True),
        (30.4, 32.8,  0.2,  6.2, 0.2,  0.80, 0.48, True),
    ]
    for y0, y1, lx0, lx1, tx0, hwl, hwt, dark_tip in upper_steps:
        sym_box(tail_fin_c, hwl, sy(y0), sy(y1), lx0, lx1, "fin_lead")
        sym_box(tail_fin_c, hwt, sy(y0), sy(y1), tx0, lx0 + 0.8, "fin_tip" if dark_tip else "fin_dark")
    # Small crisp subterminal rear flap right below the notch (signature requiem-shark caudal silhouette!)
    sym_box(tail_fin_c, 0.56, sy(26.8), sy(28.8), 1.6, 3.6, "fin_tip")

    bones = [
        {"name": "root",           "parent": None,        "pivot": [0, sy(18.0), 0],               "cubes": []},
        {"name": "body",           "parent": "root",      "pivot": [0, sy(18.0), oz(56.0)],        "cubes": []},
        {"name": "chest",          "parent": "body",      "pivot": [0, sy(18.0), oz(56.0)],        "cubes": chest_c},
        {"name": "head",           "parent": "chest",     "pivot": [0, sy(18.0), oz(62.5)],        "cubes": head_c},
        {"name": "lid_left",       "parent": "head",      "pivot": [8.9 * ws, sy(18.6), oz(80.0)], "cubes": build_lid(1)},
        {"name": "lid_right",      "parent": "head",      "pivot": [-8.9 * ws, sy(18.6), oz(80.0)],"cubes": build_lid(-1)},
        {"name": "jaw",            "parent": "head",      "pivot": [0, sy(12.8), oz(72.6)],        "cubes": jaw_c,
         "rotation": [-JAW_OPEN, 0, 0]},
        {"name": "tongue",         "parent": "jaw",       "pivot": [0, sy(12.2), oz(75.5)],        "cubes": tongue_c},
        {"name": "prey",           "parent": "head",      "pivot": [0, sy(12.8), oz(78.5)],        "cubes": []},
        {"name": "dorsal_1",       "parent": "chest",     "pivot": [0, sy(28.0), oz(50.5)],        "cubes": d1_c},
        {"name": "pectoral_left",  "parent": "chest",     "pivot": [10.4 * ws, sy(11.4), oz(57.5)],"cubes": build_pec(1)},
        {"name": "pectoral_right", "parent": "chest",     "pivot": [-10.4 * ws, sy(11.4), oz(57.5)],"cubes": build_pec(-1)},
        {"name": "torso",          "parent": "chest",     "pivot": [0, sy(18.0), oz(50.2)],        "cubes": torso_c},
        {"name": "rear",           "parent": "torso",     "pivot": [0, sy(18.0), oz(38.2)],        "cubes": rear_c},
        {"name": "pelvic_left",    "parent": "rear",      "pivot": [5.0 * ws, sy(10.8), oz(35.0)], "cubes": build_pelvic(1)},
        {"name": "pelvic_right",   "parent": "rear",      "pivot": [-5.0 * ws, sy(10.8), oz(35.0)],"cubes": build_pelvic(-1)},
        {"name": "peduncle",       "parent": "rear",      "pivot": [0, sy(17.8), oz(28.2)],        "cubes": ped_c},
        {"name": "dorsal_2",       "parent": "peduncle",  "pivot": [0, sy(21.5), oz(25.5)],        "cubes": d2_c},
        {"name": "anal_fin",       "parent": "peduncle",  "pivot": [0, sy(14.0), oz(25.5)],        "cubes": anal_c},
        {"name": "tail_base",      "parent": "peduncle",  "pivot": [0, sy(17.5), oz(16.5)],        "cubes": tail_base_c},
        {"name": "tail_fin",       "parent": "tail_base", "pivot": [0, sy(19.0), oz(11.5)],        "cubes": tail_fin_c},
    ]
    return bones


# --- Stage 3 sculpted semantic palette (clean value separation showing fins, 5 gills, teeth, gums & eyes) ------------
PLAIN_PALETTE = {
    "body_cap":  (54, 63, 76),
    "body_dark": (70, 81, 96),
    "flank":     (102, 113, 126),
    "belly":     (216, 220, 222),
    "fin":       (84, 95, 110),
    "fin_lead":  (66, 76, 90),
    "fin_dark":  (78, 88, 102),
    "fin_tip":   (46, 54, 66),
    "gill_slit": (24, 29, 36),
    "gum":       (182, 114, 108),
    "tongue":    (198, 134, 128),
    "throat":    (74, 42, 46),
    "tooth":     (238, 236, 226),
    "tooth_tip": (248, 246, 238),
    "eye":       (14, 18, 24),
    "lid":       (228, 232, 236),
}


def paint_colour(c, face, px, py, pz):
    base = PLAIN_PALETTE.get(c.get("_part", ""), (110, 118, 128))
    if face == "up":
        return shade(base, 1.07)
    if face == "down":
        return shade(base, 0.85)
    return base


def workshop_look():
    """Returns (bones, PNG bytes, texture width, height) for tools/workshop.py."""
    bones = build_bones()
    bones, rows, w, h = paint_faces(bones, texels=TEXELS, colour=paint_colour)
    return bones, png(rows), w, h


if __name__ == "__main__":
    bones, png_bytes, w, h = workshop_look()
    print(f"bull_shark (Stage 3): {len(bones)} bones, {sum(len(b['cubes']) for b in bones)} boxes, texture {w}x{h}")
