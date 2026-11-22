"""The African Leopard Tortoise (Stigmochelys pardalis) model & animation maker (M9 savanna life, 2026-11-22).

Workshop Stage 5: Final Approved Model + 12-Clip Animation Suite!
- High-domed chocolate-and-gold mosaic carapace sculpted from 4 continuous phase-centered faceted oval rings with a
  natural vaulted cervical indentation & shadowed neck cave, stepped golden marginal brim, and cream-ochre plastron.
- Fuller Classic Leopard Tortoise head (zero flat brown cap) flowing organically from a thick horizontal-arching
  wrinkled neck, delicate high-set nostrils, glossy dark eyes with eyelids, continuous horseshoe-curved 3D keratinous
  upper & lower beak (zero buck tooth!), and one seamless grooved pink tongue inside.
- Unified warm golden-sandy-tan African tortoise skin across head, neck, throat, armored osteoderm-plated columnar
  elephant legs (tear-proof deep sockets + rounded elbow/knee/ankle joints), ivory nails, and stubby tail.
- Real large adult scale: 38.0 model px working grid -> ~1.48 blocks total outstretched at MODEL_SCALE = 0.62
  (carapace alone ~1.00 block long x 0.87 blocks wide x 0.71 blocks tall; old giant bulls up to ~1.72 blocks at 0.72x).

Units are model pixels: head toward -Z, up +Y, left +X, ground y = 0, middle line x = 0.
Outline x (0 = tail tip, 38.0 = snout tip) converts to model z via oz(ox) = TOTAL_LEN / 2.0 - ox.

    python tools/leopard_tortoise_models.py game
    python tools/workshop.py snapshot leopard_tortoise --note "..."
    python tools/clip_check.py leopard_tortoise --switches
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (cube, pitch, mix, shade, paint_faces, png, geo,  # noqa: E402
                         animations, clip, write_assets, rot, euler, mat_vec, vsub, vadd, vlen, unit)
from motion_kit import two_bone, stride, ease, pulse, ramp  # noqa: E402

# --- workshop sliders (editable live in tools/workshop.py serve leopard_tortoise) -----------------------------------
TOTAL_LEN = 38.0       # working length in model px (2.38 model blocks -> ~1.48 blocks in-game at MODEL_SCALE=0.62!)
MODEL_SCALE = 0.62     # adult Leopard Tortoise in-game scale (~1.48 blocks long, snug tall shell ~1.00L x 0.80W x 0.77H blocks)
SHELL_LEN = 25.8       # carapace length along ox (ox ~1.0..26.8 -> ~1.00 block shell length at 0.62x)
SHELL_HW = 10.3        # snug real Leopard Tortoise carapace half-width (hugs body & forearms cleanly, px)
DOME_H = 19.8          # tall steep Leopard Tortoise haystack dome peak height (px -> ~0.77 blocks tall at 0.62x!)
BELLY_Y = 3.6          # natural columnar elephant-leg plastron clearance above ground (px, matches Photo 2!)
MARGIN_FLARE = 4.0     # steep tucked tortoise flanks (NO wide aquatic umbrella flare — hugs plastron snugly, deg)
NECK_EXT = 0.0         # extra neck forward reach tweak (px)
NECK_PITCH = 6.0       # gentle real tortoise horizontal-arching neck lift (deg)
HEAD_LEN = 7.6         # fuller Classic Leopard tortoise skull length (px)
HEAD_TILT = 0.0        # level alert head pitch (deg)
LEG_SPLAY = 8.4        # sturdy upright columnar leg stance squarely beneath the dome (px)
TAIL_LEN = 3.2         # visible stubby tail length behind rear shell pocket (px)

WORKSHOP_SHAPE = [
    ("SHELL_LEN",    "Shell length (px)",         24.0, 30.0, 0.4),
    ("SHELL_HW",     "Shell half-width (px)",     10.0, 13.6, 0.2),
    ("DOME_H",       "Dome peak height (px)",     15.5, 21.0, 0.3),
    ("BELLY_Y",      "Plastron clearance (px)",    2.0,  4.5, 0.2),
    ("MARGIN_FLARE", "Marginal rim flare (deg)",   8.0, 28.0, 2.0),
    ("NECK_EXT",     "Neck extension tweak (px)", -2.5,  3.0, 0.3),
    ("NECK_PITCH",   "Neck upward sweep (deg)",    6.0, 28.0, 2.0),
    ("HEAD_LEN",     "Head length (px)",           7.2, 10.4, 0.2),
    ("HEAD_TILT",    "Head tilt (deg)",          -10.0, 14.0, 2.0),
    ("LEG_SPLAY",    "Leg splay width (px)",       8.0, 11.6, 0.2),
    ("TAIL_LEN",     "Tail length (px)",           2.2,  5.5, 0.2),
]

WORKSHOP_PARTS = {
    "shell":      ["shell"],
    "neck":       ["neck_1", "neck_2"],
    "head":       ["head", "jaw", "lid_left", "lid_right"],
    "tail":       ["tail_1", "tail_2"],
    "front legs": ["leg_fl_1", "leg_fr_1"],
    "hind legs":  ["leg_bl_1", "leg_br_1"],
}

WORKSHOP_LOOK = "painted"  # "painted" (Stage 4 mosaic scutes & scaly skin) or "plain" (Stage 3 semantic)
HEAD_VARIANT = "leopard"   # "leopard" (Photo 2 classic), "hooked" (Photo 1 rugged serrated beak), "round" (compact dome)
TEXELS = 2.0               # 2 texture px per model px (crisp mosaic scutes, scales, nostrils, beak, eyes & claws)


def oz(ox):
    """Outline x (0 = tail tip .. 40.0 = snout tip) -> GeckoLib model z (+20.0 tail .. -20.0 snout)."""
    return TOTAL_LEN / 2.0 - ox


def box(cubes, x0, x1, y0, y1, ox0, ox1, part, rot_deg=None, piv=None):
    """Append a cube given in outline-x (ox0 < ox1), with optional cube-local pivot & rotation."""
    z0, z1 = oz(ox1), oz(ox0)
    c = cube([min(x0, x1), min(y0, y1), min(z0, z1)],
             [max(x0, x1), max(y0, y1), max(z0, z1)], part)
    if rot_deg is not None:
        c["rotation"] = [round(v, 3) for v in rot_deg]
        c["pivot"] = [round(v, 3) for v in (piv if piv is not None else [
            (x0 + x1) * 0.5, (y0 + y1) * 0.5, (z0 + z1) * 0.5
        ])]
    cubes.append(c)
    return c


def _ellipse_ring(cubes, n_segs, cx, cz, rx0, rz0, y0, rx1, rz1, y1, thick, part,
                  overlap=1.18, y_arch=0.0, front_lift=0.0, front_indent=0.0,
                  leg_arch=0.0, rear_lift=0.0, hi_lift_scale=0.72, phase_center=True):
    """Build a closed smooth faceted oval ring of n_segs panels connecting lower ellipse (rx0, rz0, y0)
    to upper ellipse (rx1, rz1, y1). Sculpted with a WIDE, TALL vaulted real-tortoise front archway
    spanning both the neck AND both front knees (|theta| < ~65 deg), plus clean rear leg arches!"""
    def rim_pt(theta, rx, rz, y_base, lift_scale, indent_scale):
        c_th = math.cos(theta)
        s_th = math.sin(theta)
        # Smooth real Leopard Tortoise front archway (Photo 1 & Photo 2!):
        # Hugs snugly above the head & shoulders (|theta| < ~58 deg), curving steeply down to hug the flanks!
        front_w = max(0.0, c_th) ** 2.15
        indent_w = max(0.0, c_th) ** 2.6
        # Clean tailored arches over front knees (~+-50 deg) and hind knees (~+-135 deg)
        fl_w = math.exp(-((abs(abs(theta) - 0.88)) ** 2) / 0.14)
        rl_w = math.exp(-((abs(abs(theta) - 2.34)) ** 2) / 0.16)
        rear_w = max(0.0, -c_th) ** 3.2

        # Radial front recession carves a clean natural arched doorway into the hollow front cave
        z_ind = front_indent * indent_scale * indent_w
        x = cx + rx * s_th
        z = cz - rz * c_th + z_ind

        # Vertical contour: snug front cavern arch + shoulder/hip leg arches + rear tail arch
        arch = y_arch * (s_th ** 2)
        y = (y_base + arch
             + front_lift * lift_scale * front_w
             + leg_arch * lift_scale * (0.85 * fl_w + 1.00 * rl_w)
             + rear_lift * lift_scale * rear_w)
        return (x, y, z)

    for i in range(n_segs):
        off = -0.5 if phase_center else 0.0
        t0 = 2.0 * math.pi * (i + off) / n_segs
        t1 = 2.0 * math.pi * (i + 1 + off) / n_segs
        tm = 0.5 * (t0 + t1)
        tm_n = math.atan2(math.sin(tm), math.cos(tm))

        x_lo, y_lo, z_lo = rim_pt(tm_n, rx0, rz0, y0, lift_scale=1.0, indent_scale=1.0)
        x_hi, y_hi, z_hi = rim_pt(tm_n, rx1, rz1, y1, lift_scale=hi_lift_scale, indent_scale=0.68)

        t0_n = math.atan2(math.sin(t0), math.cos(t0))
        t1_n = math.atan2(math.sin(t1), math.cos(t1))
        rxm, rzm = 0.5 * (rx0 + rx1), 0.5 * (rz0 + rz1)
        ym = 0.5 * (y0 + y1)
        mid_ls = 0.5 * (1.0 + hi_lift_scale)
        pa = rim_pt(t0_n, rxm, rzm, ym, lift_scale=mid_ls, indent_scale=0.84)
        pb = rim_pt(t1_n, rxm, rzm, ym, lift_scale=mid_ls, indent_scale=0.84)

        tx, ty_c, tz = pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2]
        chord = math.sqrt(tx * tx + ty_c * ty_c + tz * tz) * overlap
        tl = math.sqrt(tx * tx + ty_c * ty_c + tz * tz) + 1e-9
        ux = [tx / tl, ty_c / tl, tz / tl]

        vx, vy, vz = x_hi - x_lo, y_hi - y_lo, z_hi - z_lo
        panel_h = math.sqrt(vx * vx + vy * vy + vz * vz) * 1.14
        vl = math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-9
        uy = [vx / vl, vy / vl, vz / vl]

        uz = [ux[1] * uy[2] - ux[2] * uy[1],
              ux[2] * uy[0] - ux[0] * uy[2],
              ux[0] * uy[1] - ux[1] * uy[0]]
        uzl = math.sqrt(sum(c * c for c in uz)) + 1e-9
        uz = [c / uzl for c in uz]
        uy = [uz[1] * ux[2] - uz[2] * ux[1],
              uz[2] * ux[0] - uz[0] * ux[2],
              uz[0] * ux[1] - uz[1] * ux[0]]

        M = [
            [ux[0], uy[0], uz[0]],
            [ux[1], uy[1], uz[1]],
            [ux[2], uy[2], uz[2]],
        ]
        r_deg = rot(M)

        mx = 0.5 * (x_lo + x_hi) - uz[0] * (thick * 0.45)
        my = 0.5 * (y_lo + y_hi) - uz[1] * (thick * 0.45)
        mz = 0.5 * (z_lo + z_hi) - uz[2] * (thick * 0.45)

        c = cube([mx - chord * 0.5, my - panel_h * 0.5, mz - thick * 0.5],
                 [mx + chord * 0.5, my + panel_h * 0.5, mz + thick * 0.5], part)
        c["pivot"] = [round(mx, 3), round(my, 3), round(mz, 3)]
        c["rotation"] = r_deg
        c["_seg"] = i
        c["_nsegs"] = n_segs
        cubes.append(c)


def build_shell_cubes():
    """High-domed real African Leopard Tortoise carapace with a WIDE, TALL, GENUINELY HOLLOW front cave archway!
    - Front rim sweeps high (+6.4 px) across the entire front half (-65..+65 deg) over BOTH the neck and front knees!
    - Inner body core sits cleanly behind the front cave so the head & folded armored forearms retract deep inside
      with ZERO clipping through the carapace or inner walls (matches real Photo 1 & Photo 2!)."""
    cubes = []
    ox_rear = 1.0
    ox_front = ox_rear + SHELL_LEN
    ox_mid = 0.5 * (ox_rear + ox_front)
    cz = oz(ox_mid)
    rz_full = SHELL_LEN * 0.5
    rx_full = SHELL_HW

    rim_y = BELLY_Y + 2.2   # 5.8 px at side bridges — meets plastron seamlessly with zero side wings!
    dome_span = DOME_H - rim_y  # 14.0 px tall steep haystack dome!

    # --- 1. Deep Hollow Front Cave Architecture! ---
    # Solid dark rear/mid torso core pulled WAY back (ox_front - 11.4) so the ENTIRE front third of the shell
    # behind the snug arched doorway is 100% OPEN HOLLOW AIR for the neck, head, and retracted armored forelegs!
    box(cubes, -rx_full * 0.58, rx_full * 0.58, rim_y + 0.3, rim_y + dome_span * 0.56,
        ox_rear + 5.2, ox_front - 11.4, "shell_core")
    box(cubes, -rx_full * 0.44, rx_full * 0.44, rim_y + 1.2, rim_y + dome_span * 0.72,
        ox_rear + 6.6, ox_front - 10.6, "shell_core")
    # High vaulted dark inner cavern ceiling tucked at y = 13.4..15.2 (cleanly above retracted head crown!)
    box(cubes, -rx_full * 0.52, rx_full * 0.52, rim_y + 7.6, rim_y + 9.4,
        ox_front - 11.4, ox_front - 3.6, "shell_core")

    # --- 2. Flat Recessed Cream-Ochre Plastron (underside) + Seamless Lateral Shell Bridges ---
    pl_top = rim_y + 0.30
    box(cubes, -rx_full * 0.76, rx_full * 0.76, BELLY_Y, pl_top,
        ox_rear + 5.8, ox_front - 5.8, "plastron")
    box(cubes, -rx_full * 0.60, rx_full * 0.60, BELLY_Y + 0.15, pl_top,
        ox_rear + 3.4, ox_front - 3.4, "plastron")
    # Gently upturned front gular lobe forming the floor shelf of the hollow front cave
    box(cubes, -rx_full * 0.46, rx_full * 0.46, BELLY_Y + 0.25, pl_top - 0.15,
        ox_front - 4.0, ox_front - 1.5, "plastron",
        rot_deg=[-8.0, 0.0, 0.0], piv=[0.0, BELLY_Y + 0.45, oz(ox_front - 3.6)])
    box(cubes, -rx_full * 0.44, rx_full * 0.44, BELLY_Y + 0.35, pl_top + 0.05,
        ox_rear + 1.8, ox_rear + 4.2, "plastron",
        rot_deg=[8.0, 0.0, 0.0], piv=[0.0, BELLY_Y + 0.55, oz(ox_rear + 4.0)])
    # Seamless lateral bridges closing the gap cleanly between plastron and steep vertical marginal flanks!
    for side in (-1, 1):
        box(cubes, side * (rx_full * 0.66), side * (rx_full * 0.96), BELLY_Y + 0.35, rim_y + 1.35,
            ox_rear + 8.2, ox_front - 8.2, "bridge",
            rot_deg=[0.0, 0.0, side * 12.0],
            piv=[side * rx_full * 0.78, BELLY_Y + 1.1, cz])

    # --- 3. Ring 0: Steep Snug Golden-Trimmed Marginal Wall (NO wide flared umbrella wings!) ---
    # Wraps tightly down around the flanks to meet the plastron bridge, and arches snugly (+5.6 px -> y=11.4)
    # right above the retracted head & folded armored forearms!
    skirt_h = 3.1
    flare_out = math.tan(math.radians(MARGIN_FLARE)) * skirt_h
    _ellipse_ring(
        cubes, n_segs=12, cx=0.0, cz=cz,
        rx0=rx_full + 0.10, rz0=rz_full + 0.15, y0=rim_y,
        rx1=rx_full - flare_out, rz1=rz_full - flare_out * 0.85, y1=rim_y + skirt_h,
        thick=2.25, part="marginal", overlap=1.18, y_arch=0.0,
        front_lift=5.55, front_indent=1.45, leg_arch=1.55, rear_lift=1.05, hi_lift_scale=0.76, phase_center=True
    )

    # --- 4. Ring 1: Steep Rounded Lower Costal Dome Wall (smooth continuous transition from flanks!) ---
    y_r1_lo = rim_y + skirt_h - 0.40
    y_r1_hi = rim_y + dome_span * 0.58
    _ellipse_ring(
        cubes, n_segs=12, cx=0.0, cz=cz,
        rx0=rx_full - flare_out * 0.95, rz0=rz_full - flare_out * 0.85, y0=y_r1_lo,
        rx1=rx_full * 0.76, rz1=rz_full * 0.79, y1=y_r1_hi,
        thick=2.2, part="carapace_low", overlap=1.20, y_arch=0.25,
        front_lift=3.85, front_indent=1.05, leg_arch=0.80, rear_lift=0.55, hi_lift_scale=0.40, phase_center=True
    )

    # --- 5. Ring 2: Tall Steep Upper Costal Roof Slope (classic rounded Leopard Tortoise dome!) ---
    y_r2_lo = y_r1_hi - 0.45
    y_r2_hi = rim_y + dome_span * 0.84
    _ellipse_ring(
        cubes, n_segs=10, cx=0.0, cz=cz,
        rx0=rx_full * 0.77, rz0=rz_full * 0.80, y0=y_r2_lo,
        rx1=rx_full * 0.44, rz1=rz_full * 0.51, y1=y_r2_hi,
        thick=1.95, part="carapace_mid", overlap=1.25, y_arch=0.10,
        front_lift=1.35, front_indent=0.50, leg_arch=0.20, rear_lift=0.15, hi_lift_scale=0.35, phase_center=True
    )

    # --- 6. Smooth Convex Vertebral Crown Cap (8-facet dome cap + apex vertebral crest) ---
    y_cap_lo = y_r2_hi - 0.40
    y_cap_hi = DOME_H - 0.25
    _ellipse_ring(
        cubes, n_segs=8, cx=0.0, cz=cz,
        rx0=rx_full * 0.46, rz0=rz_full * 0.53, y0=y_cap_lo,
        rx1=rx_full * 0.16, rz1=rz_full * 0.22, y1=y_cap_hi,
        thick=1.6, part="carapace_top", overlap=1.30, y_arch=0.0,
        front_lift=0.30, front_indent=0.20, hi_lift_scale=0.25, phase_center=True
    )
    box(cubes, -rx_full * 0.24, rx_full * 0.24, y_cap_hi - 0.9, DOME_H + 0.1,
        ox_mid - 3.2, ox_mid + 2.8, "carapace_top")

    return cubes


def build_neck_and_head(head_variant=None):
    """Articulated tear-proof Leopard Tortoise neck + fuller Classic Leopard head with smooth curved 3D beak!
    Matches real Stigmochelys pardalis proportions (Photo 2 & Photo 3):
      - Shorter, thicker, gently horizontal-arching scaly neck flowing smoothly into the rounded skull
      - Larger, fuller domed tortoise head with continuous forehead-to-nose curve & subtle high-set nostrils
      - Smooth continuous horseshoe-curved 3D upper & lower keratinous beak (zero buck-tooth cube!)
      - Seamless plump rounded pink tongue + vaulted dark-rose palate & cheek gums revealed when jaw opens!
    """
    ox_front = 1.0 + SHELL_LEN
    # Root thicker tortoise neck cleanly deep inside the snug hollow front cave!
    n1_ox = ox_front - 5.2
    n1_y = BELLY_Y + 4.05
    n1_len = 3.9 + NECK_EXT * 0.5
    n2_len = 3.3 + NECK_EXT * 0.5

    ang = math.radians(NECK_PITCH)
    n2_ox = n1_ox + math.cos(ang) * (n1_len - 0.5)
    n2_y = n1_y + math.sin(ang) * (n1_len - 0.5)

    head_ox = n2_ox + math.cos(ang * 0.78) * (n2_len - 0.5)
    head_y = n2_y + math.sin(ang * 0.78) * (n2_len - 0.5)
    hl = HEAD_LEN  # 7.6 px fuller Classic Leopard head

    # --- neck_1 (hefty, rounded horizontal-arching tortoise neck emerging from cervical cave) ---
    p1 = [0.0, n1_y, oz(n1_ox)]
    n1_cubes = []
    box(n1_cubes, -2.80, 2.80, n1_y - 2.15, n1_y + 2.15, n1_ox - 2.8, n1_ox + n1_len + 1.1, "neck",
        rot_deg=pitch(NECK_PITCH), piv=p1)
    box(n1_cubes, -2.20, 2.20, n1_y - 2.55, n1_y + 2.55, n1_ox - 2.5, n1_ox + n1_len + 0.8, "neck",
        rot_deg=pitch(NECK_PITCH), piv=p1)
    box(n1_cubes, -2.65, 2.65, n2_y - 2.20, n2_y + 2.20, n2_ox - 1.5, n2_ox + 1.5, "neck")
    neck_1 = {"name": "neck_1", "parent": "shell", "pivot": [round(v, 3) for v in p1], "cubes": n1_cubes}

    # --- neck_2 (thick scaly upper neck flowing seamlessly into rear tortoise skull!) ---
    p2 = [0.0, n2_y, oz(n2_ox)]
    n2_cubes = []
    box(n2_cubes, -2.72, 2.72, n2_y - 2.10, n2_y + 2.10, n2_ox - 1.5, n2_ox + n2_len + 1.05, "neck",
        rot_deg=pitch(NECK_PITCH * 0.72), piv=p2)
    box(n2_cubes, -2.12, 2.12, n2_y - 2.48, n2_y + 2.48, n2_ox - 1.3, n2_ox + n2_len + 0.85, "neck",
        rot_deg=pitch(NECK_PITCH * 0.72), piv=p2)
    box(n2_cubes, -2.02, 2.02, n2_y - 2.32, n2_y - 1.25, n2_ox - 1.1, n2_ox + n2_len + 0.95, "throat",
        rot_deg=pitch(NECK_PITCH * 0.72), piv=p2)
    neck_2 = {"name": "neck_2", "parent": "neck_1", "pivot": [round(v, 3) for v in p2], "cubes": n2_cubes}

    # --- head (fuller Classic Leopard Tortoise skull with smooth curved horseshoe beak!) ---
    beak_y = head_y - 0.15
    ph = [0.0, head_y + 0.85, oz(head_ox)]
    h_cubes = []

    # 1. Seamless curved occipital nape & full rounded tortoise cheeks (flows organically from neck_2!)
    box(h_cubes, -2.86, 2.86, head_y - 0.10, head_y + 3.45, head_ox - 2.10, head_ox + 0.90, "head_side",
        rot_deg=pitch(13.0), piv=[0.0, head_y + 1.65, oz(head_ox - 0.60)])
    # Full rear/mid tortoise skull
    box(h_cubes, -3.18, 3.18, head_y - 0.05, head_y + 3.40, head_ox - 0.95, head_ox + hl * 0.50, "head_side")
    # Smoothly arched cranial dome (highest softly right behind/above the eyes — Photo 3!)
    box(h_cubes, -2.62, 2.62, head_y + 2.75, head_y + 3.98, head_ox - 1.05, head_ox + hl * 0.64, "head_dome")
    # Continuous rounded forehead-to-nose curve (zero boxy step down to nose!)
    box(h_cubes, -2.20, 2.20, head_y + 2.40, head_y + 3.84, head_ox + hl * 0.40, head_ox + hl * 0.94, "head_dome",
        rot_deg=pitch(-14.0), piv=[0.0, head_y + 3.68, oz(head_ox + hl * 0.46)])
    # Tapered mid-face & softly rounded blunt tortoise nose
    box(h_cubes, -2.82, 2.82, head_y + 0.88, head_y + 3.25, head_ox + hl * 0.34, head_ox + hl * 0.72, "head_side")
    box(h_cubes, -2.28, 2.28, beak_y + 1.02, head_y + 3.16, head_ox + hl * 0.58, head_ox + hl * 0.96, "snout")
    # Soft rounded nose tip bevel (eliminates flat brick nose face — matches Photo 3!)
    box(h_cubes, -1.72, 1.72, beak_y + 1.08, head_y + 2.95, head_ox + hl * 0.80, head_ox + hl + 0.14, "snout")

    # 2. Expressive tortoise eyes with subtle rounded scaly eyelid rims (Photo 3!)
    ex0, ex1 = head_ox + hl * 0.40, head_ox + hl * 0.63
    ey0, ey1 = head_y + 1.52, head_y + 3.04
    eye_x = 2.98
    for side in (-1, 1):
        box(h_cubes, side * 2.10, side * 2.96, ey1 - 0.18, ey1 + 0.42, ex0 - 0.32, ex1 + 0.28, "head_brow")
        box(h_cubes, side * 2.60, side * eye_x, ey0, ey1, ex0, ex1, "eye")

    # 3. Recessed vaulted fleshy palate, dark throat & inner cheek gums (100% hidden when closed!)
    box(h_cubes, -1.68, 1.68, beak_y + 0.94, beak_y + 1.52, head_ox + hl * 0.14, head_ox + hl * 0.78, "mouth_inner")
    box(h_cubes, -1.52, 1.52, beak_y + 0.30, beak_y + 1.32, head_ox - 0.35, head_ox + hl * 0.28, "mouth_throat")
    for side in (-1, 1):
        box(h_cubes, side * 1.50, side * 2.18, beak_y + 0.18, beak_y + 1.04, head_ox + hl * 0.16, head_ox + hl * 0.50, "mouth_gum")

    # 4. SMOOTH CURVED HORSESHOE 3D UPPER BEAK (continuous graceful arch — ZERO single-tooth block!)
    # Left & right cheek beak blades curving gently downward-forward (-5 deg) and tapering inward (10 deg)
    for side in (-1, 1):
        box(h_cubes, side * 1.94, side * 2.52, beak_y + 0.02, beak_y + 1.18, head_ox + hl * 0.26, head_ox + hl * 0.78, "beak_upper",
            rot_deg=[-5.0, side * 10.0, 0.0], piv=[side * 2.22, beak_y + 0.58, oz(head_ox + hl * 0.52)])
        # Angled front-left & front-right curved corner bevels rounding the horseshoe beak into the nose!
        box(h_cubes, side * 1.48, side * 2.08, beak_y - 0.16, beak_y + 1.08, head_ox + hl * 0.66, head_ox + hl * 0.98, "beak_upper",
            rot_deg=[-8.0, side * 32.0, 0.0], piv=[side * 1.78, beak_y + 0.45, oz(head_ox + hl * 0.82)])
    # Full-width smooth curved front beak arch spanning seamlessly between left & right bevels!
    box(h_cubes, -1.58, 1.58, beak_y - 0.28, beak_y + 1.06, head_ox + hl * 0.82, head_ox + hl + 0.18, "beak_front",
        rot_deg=pitch(-8.0), piv=[0.0, beak_y + 0.40, oz(head_ox + hl * 0.94)])

    head = {"name": "head", "parent": "neck_2", "pivot": [round(v, 3) for v in ph],
            "rotation": pitch(HEAD_TILT), "cubes": h_cubes}

    # --- blink eyelids (parked tucked inside upper scaly brow at rest) ---
    lids = []
    for side, lname in ((1, "lid_left"), (-1, "lid_right")):
        lx0, lx1 = min(side * 2.64, side * 2.96), max(side * 2.64, side * 2.96)
        lids.append({
            "name": lname, "parent": "head",
            "pivot": [round(side * 2.92, 3), round(ey1, 3), round(oz(0.5 * (ex0 + ex1)), 3)],
            "cubes": [cube([lx0, ey1 - 0.10, oz(ex1 - 0.04)], [lx1, ey1 + 0.36, oz(ex0 + 0.04)], "head_brow")]
        })

    # --- 5. Smoothly Curved Lower Jaw, Nested Curved U-Beak & Seamless Rounded Pink Tongue! ---
    pj = [0.0, beak_y + 0.24, oz(head_ox + hl * 0.16)]
    jaw_front = head_ox + hl * 0.90
    j_cubes = []
    # Smooth convex tortoise chin sweeping cleanly upward from throat to rounded lower beak (Photo 3!)
    box(j_cubes, -2.06, 2.06, beak_y - 1.24, beak_y - 0.08, head_ox - 0.60, head_ox + hl * 0.56, "jaw")
    box(j_cubes, -1.66, 1.66, beak_y - 1.04, beak_y - 0.02, head_ox + hl * 0.42, jaw_front - 0.16, "jaw",
        rot_deg=pitch(16.0), piv=[0.0, beak_y - 0.52, oz(head_ox + hl * 0.50)])
    # Curved horseshoe lower keratin beak rim (nests cleanly inside upper beak with authentic overbite!)
    for side in (-1, 1):
        box(j_cubes, side * 1.38, side * 1.84, beak_y - 0.26, beak_y + 0.48, head_ox + hl * 0.26, jaw_front - 0.65, "beak_lower",
            rot_deg=[0.0, side * 10.0, 0.0], piv=[side * 1.60, beak_y + 0.10, oz(head_ox + hl * 0.54)])
        box(j_cubes, side * 1.02, side * 1.50, beak_y - 0.30, beak_y + 0.50, jaw_front - 0.95, jaw_front + 0.02, "beak_lower",
            rot_deg=[8.0, side * 32.0, 0.0], piv=[side * 1.24, beak_y + 0.10, oz(jaw_front - 0.46)])
    # Smooth full-width curved front lower beak edge
    box(j_cubes, -1.12, 1.12, beak_y - 0.38, beak_y + 0.52, jaw_front - 0.62, jaw_front + 0.14, "beak_lower",
        rot_deg=pitch(15.0), piv=[0.0, beak_y - 0.06, oz(jaw_front - 0.26)])
    # ONE seamless plump rounded pink tongue (with soft central groove painted smoothly on top — zero split bars!)
    box(j_cubes, -1.16, 1.16, beak_y - 0.34, beak_y + 0.14, head_ox + hl * 0.20, jaw_front - 0.72, "tongue")
    box(j_cubes, -0.82, 0.82, beak_y - 0.32, beak_y + 0.12, jaw_front - 0.95, jaw_front - 0.42, "tongue")

    jaw = {"name": "jaw", "parent": "head", "pivot": [round(v, 3) for v in pj], "cubes": j_cubes}

    # --- carry socket inside curved beak ---
    carry = {"name": "carry", "parent": "head",
             "pivot": [0.0, round(beak_y + 0.12, 3), round(oz(head_ox + hl * 0.80), 3)],
             "cubes": []}

    return [neck_1, neck_2, head, jaw, *lids, carry]


def build_tail():
    """Short stubby pointed tail peeking behind the rear marginals."""
    ox_base = 3.8
    ty = BELLY_Y + 2.5
    p1 = [0.0, ty, oz(ox_base)]
    t1_len = TAIL_LEN * 0.58
    t2_len = TAIL_LEN * 0.52

    t1 = {
        "name": "tail_1", "parent": "shell",
        "pivot": [round(v, 3) for v in p1],
        "cubes": [
            box([], -1.35, 1.35, ty - 1.0, ty + 1.0, ox_base - t1_len - 0.4, ox_base + 1.0, "tail",
                rot_deg=pitch(14.0), piv=p1),
        ],
    }
    p2 = [0.0, ty - 0.4, oz(ox_base - t1_len + 0.3)]
    t2 = {
        "name": "tail_2", "parent": "tail_1",
        "pivot": [round(v, 3) for v in p2],
        "cubes": [
            cube([-0.8, p2[1] - 0.7, p2[2] - 0.6], [0.8, p2[1] + 0.7, p2[2] + t2_len], "tail"),
        ],
    }
    return [t1, t2]


def _build_leg(prefix, parent, side, hip, knee, ankle, foot_yaw, is_front):
    """Build one tear-proof splayed 3-bone columnar turtle leg with deep socket overlap & rounded elbow/ankle cuffs."""
    hx, hy, hz = hip
    kx, ky, kz = knee
    ax, ay, az = ankle

    # --- 1. Upper arm/thigh (deeply rooted inside shell core + generous rounded elbow/knee ball!) ---
    dx, dy, dz = kx - hx, ky - hy, kz - hz
    u_len = math.sqrt(dx * dx + dy * dy + dz * dz)
    r_u = 1.90 if is_front else 1.82
    splay_deg = math.degrees(math.atan2(abs(dx), max(0.5, -dy)))
    fwd_deg = math.degrees(math.atan2(-dz, math.hypot(abs(dx), max(0.5, -dy))))
    # Shaft penetrates +2.0 px deep into shell pocket above hip AND +1.1 px past knee for zero shoulder/elbow tearing!
    u_shaft = cube([hx - r_u, hy - u_len - 1.1, hz - r_u],
                   [hx + r_u, hy + 2.0, hz + r_u], "leg_upper")
    u_shaft["pivot"] = [round(hx, 3), round(hy, 3), round(hz, 3)]
    u_shaft["rotation"] = [round(-fwd_deg, 3), 0.0, round(-side * splay_deg, 3)]
    # Centered rounded elbow/knee sphere bridging upper -> lower cleanly through +-45 deg bends
    r_k = 1.92 if is_front else 1.85
    knee_ball = cube([kx - r_k, ky - r_k * 0.92, kz - r_k],
                     [kx + r_k, ky + r_k * 0.92, kz + r_k], "leg_upper")
    # Rounded shoulder/hip root collar hidden inside pocket so extreme swings never expose air under shell
    hip_ball = cube([hx - 2.0, hy - 1.7, hz - 2.0], [hx + 2.0, hy + 1.9, hz + 2.0], "leg_upper")

    b_upper = {
        "name": f"{prefix}_1", "parent": parent,
        "pivot": [round(hx, 3), round(hy, 3), round(hz, 3)],
        "cubes": [u_shaft, knee_ball, hip_ball],
    }

    # --- 2. Lower columnar spotted forearm/shin (overlaps +1.55 px into elbow & +1.15 px into ankle!) ---
    ldx, ldy, ldz = ax - kx, ay - ky, az - kz
    l_len = math.sqrt(ldx * ldx + ldy * ldy + ldz * ldz)
    l_splay = math.degrees(math.atan2(abs(ldx), max(0.5, -ldy)))
    l_fwd = math.degrees(math.atan2(-ldz, math.hypot(abs(ldx), max(0.5, -ldy))))
    # Slightly narrower (0.14 px inset) than knee_ball so zero z-fighting at overlap!
    r_l = 1.78 if is_front else 1.72
    l_shaft = cube([kx - r_l, ky - l_len - 0.48, kz - r_l],
                   [kx + r_l, ky + 1.55, kz + r_l], "leg_lower")
    l_shaft["pivot"] = [round(kx, 3), round(ky, 3), round(kz, 3)]
    l_shaft["rotation"] = [round(-l_fwd, 3), 0.0, round(-side * l_splay, 3)]
    # Centered rounded ankle cuff on lower bone sealing the wrist/ankle joint (trimmed above y=1.12 -> zero ground graze!)
    r_a = 1.74 if is_front else 1.68
    ankle_cuff = cube([ax - r_a, ay - 0.48, az - r_a], [ax + r_a, ay + 1.35, az + r_a], "leg_lower")

    b_lower = {
        "name": f"{prefix}_2", "parent": f"{prefix}_1",
        "pivot": [round(kx, 3), round(ky, 3), round(kz, 3)],
        "cubes": [l_shaft, ankle_cuff],
    }

    # --- 3. Broad planted foot + overlapping ankle dome + 3 crisp ivory claws (flush on y = 0.0!) ---
    f_cubes = []
    fw = 2.05 if is_front else 1.95
    fl_back = 1.85
    fl_front = 2.45 if is_front else 2.25
    fp = [round(ax, 3), round(ay, 3), round(az, 3)]
    yaw_r = side * foot_yaw

    # Snug overlapping ankle dome rising +0.9 px above ankle pivot inside ankle_cuff (0.14 px inset -> zero tear!)
    f_dome = cube([ax - (r_a - 0.14), 0.4, az - (r_a - 0.14)],
                  [ax + (r_a - 0.14), ay + 0.95, az + (r_a - 0.14)], "foot")
    f_dome["pivot"] = fp
    f_dome["rotation"] = [0.0, round(yaw_r, 3), 0.0]
    f_cubes.append(f_dome)

    # Broad webbed foot pad (flush on y = 0.0 .. 1.95)
    pad = cube([ax - fw, 0.0, az - fl_front], [ax + fw, 1.95, az + fl_back], "foot")
    pad["pivot"] = fp
    pad["rotation"] = [0.0, round(yaw_r, 3), 0.0]
    f_cubes.append(pad)

    # 3 distinct crisp ivory-cream claws flush on y = 0.0
    for cx_off in (-1.35, 0.0, 1.35):
        claw = cube([ax + cx_off - 0.38, 0.0, az - fl_front - 0.90],
                    [ax + cx_off + 0.38, 1.35, az - fl_front + 0.30], "claw")
        claw["pivot"] = fp
        claw["rotation"] = [0.0, round(yaw_r, 3), 0.0]
        f_cubes.append(claw)

    b_foot = {
        "name": f"{prefix}_3", "parent": f"{prefix}_2",
        "pivot": [round(ax, 3), round(ay, 3), round(az, 3)],
        "cubes": f_cubes,
    }
    return [b_upper, b_lower, b_foot]


def build_bones(head_variant=None):
    """Assemble the complete African Leopard Tortoise bone hierarchy (zero parent:null keys)."""
    root = {"name": "root", "pivot": [0.0, 0.0, 0.0], "cubes": []}

    shell = {
        "name": "shell", "parent": "root",
        "pivot": [0.0, round(BELLY_Y + 4.0, 3), round(oz(1.0 + SHELL_LEN * 0.48), 3)],
        "cubes": build_shell_cubes(),
    }

    neck_head = build_neck_and_head(head_variant=head_variant)
    tail = build_tail()

    ox_front_leg = 1.0 + SHELL_LEN * 0.81
    ox_hind_leg = 1.0 + SHELL_LEN * 0.20

    legs = []
    for side, tag in ((1, "leg_fl"), (-1, "leg_fr")):
        hip = (side * (SHELL_HW * 0.48), BELLY_Y + 3.5, oz(ox_front_leg - 1.1))
        knee = (side * (LEG_SPLAY - 0.7), BELLY_Y + 2.0, oz(ox_front_leg - 0.1))
        ankle = (side * LEG_SPLAY, 1.6, oz(ox_front_leg + 1.0))
        legs.extend(_build_leg(tag, "shell", side, hip, knee, ankle, foot_yaw=-14.0, is_front=True))

    for side, tag in ((1, "leg_bl"), (-1, "leg_br")):
        hip = (side * (SHELL_HW * 0.46), BELLY_Y + 3.3, oz(ox_hind_leg + 1.3))
        knee = (side * (LEG_SPLAY - 0.9), BELLY_Y + 1.9, oz(ox_hind_leg + 0.3))
        ankle = (side * (LEG_SPLAY - 0.3), 1.6, oz(ox_hind_leg - 0.4))
        legs.extend(_build_leg(tag, "shell", side, hip, knee, ankle, foot_yaw=-20.0, is_front=False))

    return [root, shell, *neck_head, *tail, *legs]


# --- Stage 3 Plain semantic colors ----------------------------------------------------------------------------------
PLAIN_COLOURS = {
    "carapace_top": (84, 62, 48),
    "carapace_mid": (108, 82, 62),
    "carapace_low": (74, 54, 44),
    "marginal":     (194, 148, 82),
    "shell_core":   (52, 40, 34),
    "plastron":     (216, 189, 134),
    "bridge":       (172, 145, 98),
    "neck":         (144, 126, 114),
    "throat":       (194, 173, 136),
    "head_crown":   (156, 138, 110),
    "head_dome":    (160, 140, 112),
    "head_brow":    (148, 128, 100),
    "head_side":    (166, 148, 120),
    "snout":        (154, 136, 108),
    "beak_upper":   (132, 114, 84),
    "beak_front":   (122, 104, 76),
    "beak_hook":    (112, 96, 72),
    "beak_lower":   (138, 120, 92),
    "jaw":          (188, 168, 132),
    "mouth_inner":  (132, 62, 68),
    "mouth_throat": (72, 34, 40),
    "mouth_gum":    (204, 148, 146),
    "tongue":       (194, 116, 124),
    "tongue_groove":(218, 156, 164),
    "eye":          (20, 22, 26),
    "leg_upper":    (132, 116, 106),
    "leg_lower":    (146, 128, 116),
    "foot":         (128, 112, 102),
    "claw":         (224, 206, 166),
    "tail":         (134, 118, 106),
}


def _hash3(ix, iy, iz):
    """Deterministic integer hash -> [0.0, 1.0) for crisp Minecraft pixel-art grain & spots."""
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65536.0


def _world_pt(c, x, y, z):
    """Transform cube-local (x, y, z) by the cube's own rotation around its pivot so shell paint wraps seamlessly!"""
    r = c.get("rotation")
    if not r or not any(abs(v) > 1e-6 for v in r):
        return x, y, z
    px, py, pz = c["pivot"]
    M = euler(r)
    nx, ny, nz = mat_vec(M, [x - px, y - py, z - pz])
    return px + nx, py + ny, pz + nz


# Precomputed Voronoi scute centers in normalized oval coords (nx = x / SHELL_HW, nu = (ox - ox_mid) / (SHELL_LEN/2))
# 5 central vertebral scutes + 4 pairs of flanking costal scutes (symmetric left/right!)
_SCUTE_CENTERS = [
    # 5 Vertebral scutes along the spine midline
    (0.0, -0.72, "v"),
    (0.0, -0.36, "v"),
    (0.0,  0.00, "v"),
    (0.0,  0.36, "v"),
    (0.0,  0.72, "v"),
    # 4 Pairs of flanking costal scutes
    (-0.56, -0.54, "c"), (0.56, -0.54, "c"),
    (-0.62, -0.18, "c"), (0.62, -0.18, "c"),
    (-0.62,  0.18, "c"), (0.62,  0.18, "c"),
    (-0.55,  0.54, "c"), (0.55,  0.54, "c"),
]


def plain_colour(c, face, x, y, z):
    """Stage 3 clean semantic colors (returns 3-tuple RGB)."""
    part = c.get("_part", "carapace_mid")
    base = PLAIN_COLOURS.get(part, (140, 120, 105))
    k = {"up": 1.07, "down": 0.84, "north": 1.00, "south": 0.94, "east": 0.97, "west": 0.97}.get(face, 1.0)
    if part == "marginal":
        oy, sy = c["origin"][1], c["size"][1]
        if (y - oy) / max(0.01, sy) > 0.42:
            base = mix((80, 58, 44), base, 0.28)
    if part == "eye" and face in ("east", "west", "north"):
        oy, sy = c["origin"][1], c["size"][1]
        oz0, sz = c["origin"][2], c["size"][2]
        if (y - oy) / max(0.01, sy) > 0.56 and (z - oz0) / max(0.01, sz) > 0.52:
            return (242, 246, 250)
    return shade(base, k)


def terrapin_colour(c, face, x, y, z):
    """Stage 4 Detailed Pixel-Art Paint matching references/inspiration/terrapin_sheet.png (returns 3-tuple RGB)."""
    part = c.get("_part", "carapace_mid")
    wx, wy, wz = _world_pt(c, x, y, z)
    ox = TOTAL_LEN * 0.5 - wz

    # Crisp Minecraft pixel grid (1 texture px = 0.5 model px at TEXELS=2; quantize pattern on ~0.85 px Minecraft blocks)
    gx, gy, gz = int(math.floor(wx * 1.45)), int(math.floor(wy * 1.45)), int(math.floor(wz * 1.45))
    n1 = _hash3(gx, gy, gz)
    n2 = _hash3(gx + 31, gy + 17, gz + 53)
    # Finer 1-texel micro-grain
    tx, ty, tz = int(math.floor(wx * 2.0)), int(math.floor(wy * 2.0)), int(math.floor(wz * 2.0))
    n_fine = _hash3(tx + 97, ty + 41, tz + 13)

    # Palette constants sampled directly from terrapin_sheet.png
    DARK_GROOVE = (40, 30, 26)      # deep scute seam chocolate
    DARK_CHOC   = (58, 44, 37)      # dark carapace chocolate
    MID_UMBER   = (92, 68, 50)      # warm umber scute mid
    WARM_TAN    = (140, 102, 62)    # mottled warm tan burst
    OCHRE_GOLD  = (204, 152, 74)    # vibrant golden-ochre highlight
    OCHRE_RIM   = (224, 178, 96)    # bright luminous marginal rim edge
    PLASTRON_LT = (224, 198, 138)   # warm cream-ochre belly scute
    PLASTRON_MD = (204, 176, 118)   # slightly shaded belly grain
    PLASTRON_SM = (138, 110, 68)    # crisp plastron scute seam line
    SKIN_GREY   = (156, 144, 134)   # warm pebble grey-tan skin
    SKIN_LIGHT  = (186, 170, 146)   # paler lower cheek / lower neck
    CREAM_CHIN  = (214, 192, 144)   # warm cream throat & smiling lower jaw
    SPOT_DARK   = (46, 40, 39)      # crisp dark charcoal skin spot
    SPOT_MID    = (86, 76, 72)      # softer secondary freckle
    CROWN_DARK  = (62, 48, 41)      # dark mottled head crown
    CLAW_TIP    = (236, 222, 184)   # bright ivory claw tip
    CLAW_BASE   = (176, 158, 126)   # shaded claw root

    # --- 1. CARAPACE DOME: Seamless Voronoi 5-Vertebral + 4-Pair Costal Mosaic Scutes! ---
    if part in ("carapace_top", "carapace_mid", "carapace_low"):
        ox_mid = 1.0 + SHELL_LEN * 0.5
        # Quantize scute lookup gently on crisp Minecraft voxel steps so scute edges have authentic blocky charm!
        qnx = (round(wx * 1.6) / 1.6) / max(1.0, SHELL_HW)
        qnu = (round((ox - ox_mid) * 1.6) / 1.6) / max(1.0, SHELL_LEN * 0.5)
        # Find nearest & second-nearest Voronoi scute center (slightly weighted in x for hexagonal vertebral shape)
        d1, d2, best_kind, best_dx, best_du = 99.0, 99.0, "v", 0.0, 0.0
        for sx, su, kind in _SCUTE_CENTERS:
            ddx = (qnx - sx) * 1.15
            ddu = (qnu - su) * 1.00
            d = math.hypot(ddx, ddu)
            if d < d1:
                d2 = d1
                d1, best_kind, best_dx, best_du = d, kind, ddx, ddu
            elif d < d2:
                d2 = d
        edge_dist = d2 - d1

        # Crisp dark chocolate scute groove separating every vertebral & costal plate!
        if edge_dist < 0.055:
            col = mix(DARK_GROOVE, DARK_CHOC, n_fine * 0.35)
        else:
            # Inside each scute: radiating golden-ochre + warm tan mottled ring around a darker mottled core!
            # Matches the hero render & Top view where each scute glows with warm ochre patches inside dark borders
            ring = math.exp(-((d1 - 0.16) ** 2) / 0.012)
            center_glow = math.exp(-(d1 ** 2) / 0.022)
            costal_boost = 0.14 if part == "carapace_low" else 0.0
            # Chunky blocky mosaic variation
            mottle = 0.55 * n1 + 0.30 * n2 + 0.15 * n_fine
            warmth = max(0.0, min(1.0, 0.44 * ring + 0.34 * center_glow + 0.52 * mottle + costal_boost - 0.20))

            if warmth < 0.28:
                col = mix(DARK_CHOC, MID_UMBER, warmth / 0.28)
            elif warmth < 0.58:
                col = mix(MID_UMBER, WARM_TAN, (warmth - 0.28) / 0.30)
            elif warmth < 0.80:
                col = mix(WARM_TAN, OCHRE_GOLD, (warmth - 0.58) / 0.22)
            else:
                col = mix(OCHRE_GOLD, OCHRE_RIM, (warmth - 0.80) / 0.20)

            # Subtle dark concentric growth-ring rim right inside the scute groove
            if edge_dist < 0.095 and n1 < 0.55:
                col = mix(col, DARK_CHOC, 0.55)

        # Gentle dome ambient occlusion near the bottom edge of lower costals
        if part == "carapace_low" and wy < BELLY_Y + 6.5:
            col = shade(col, 0.92)
        return col

    # --- 2. FLARED "HELMET" MARGINAL SKIRT: 12 crisp plates with bright golden-ochre lower rim! ---
    if part == "marginal":
        oy, sy = c["origin"][1], max(0.01, c["size"][1])
        ox_c, sx_c = c["origin"][0], max(0.01, c["size"][0])
        # Vertical position along the marginal plate: 0 = bottom lip, 1 = top junction with costal dome
        t_up = (y - oy) / sy
        # Horizontal position across this marginal panel (0..1) -> dark vertical divider seam at both ends!
        u_pan = (x - ox_c) / sx_c
        near_seam = (u_pan < 0.07 or u_pan > 0.93)

        if near_seam and t_up > 0.12:
            return mix(DARK_GROOVE, DARK_CHOC, n_fine * 0.4)
        if t_up < 0.24:
            # Bright glowing golden-ochre lower helmet lip!
            col = mix(OCHRE_GOLD, OCHRE_RIM, 0.45 + 0.45 * n1)
        elif t_up < 0.46:
            # Warm ochre-to-tan transition band (stepped blocky edge!)
            if n1 > 0.42:
                col = mix(WARM_TAN, OCHRE_GOLD, n2)
            else:
                col = mix(MID_UMBER, WARM_TAN, n2)
        elif t_up > 0.88:
            # Dark horizontal groove separating marginals from costal dome above
            col = mix(DARK_GROOVE, DARK_CHOC, n_fine * 0.35)
        else:
            # Upper dark-chocolate mottled marginal plate
            col = mix(DARK_CHOC, MID_UMBER, 0.35 * n1 + 0.25 * n2)
            if n1 > 0.76:
                col = mix(col, WARM_TAN, 0.55)
        if face == "down":
            col = shade(col, 0.85)
        return col

    # --- 3. CREAM-OCHRE PLASTRON & SHELL BRIDGES (matching Bottom view!) ---
    if part == "plastron":
        ox_mid = 1.0 + SHELL_LEN * 0.5
        # Symmetric central longitudinal seam + 4 transverse paired scute seams (gular/humeral/pectoral/abdominal/femoral/anal)
        is_midline = abs(wx) < 0.42
        seam_oxs = (ox_mid - 7.8, ox_mid - 3.6, ox_mid + 0.6, ox_mid + 4.8, ox_mid + 8.6)
        is_trans = any(abs(ox - sox) < 0.40 for sox in seam_oxs)
        # Slightly darker beveled outer rim along plastron edges
        r_edge = math.hypot(wx / (SHELL_HW * 0.72), (ox - ox_mid) / (SHELL_LEN * 0.44))
        col = mix(PLASTRON_MD, PLASTRON_LT, 0.40 + 0.45 * n1 + 0.15 * n_fine)
        if is_midline or is_trans:
            col = mix(PLASTRON_SM, col, 0.25)
        elif r_edge > 0.88:
            col = mix(col, PLASTRON_SM, 0.40)
        return shade(col, 0.94 if face == "down" else 0.88)

    if part == "bridge":
        col = mix(PLASTRON_SM, PLASTRON_MD, 0.45 + 0.4 * n1)
        if n2 > 0.72:
            col = mix(col, DARK_CHOC, 0.35)
        return col

    if part == "shell_core":
        return mix(DARK_GROOVE, DARK_CHOC, n_fine * 0.3)

    # --- Unified Real African Leopard Tortoise Skin Palette (Photo 2 & Photo 3!) ---
    # Warm sun-baked sandy-gold / straw-tan pebbled & armored tortoise scales across head, neck, legs & tail!
    TOR_BASE    = (184, 156, 112)  # warm golden-sandy tortoise scale skin
    TOR_HI      = (212, 188, 140)  # sunlit creamy-straw scale highlight
    TOR_OLIVE   = (156, 138, 98)   # weathered dusty sage-olive scale patch (Photo 3!)
    TOR_SEAM    = (114, 90, 60)    # earthy warm brown scale crevice / skin wrinkle
    TOR_DEEP    = (84, 66, 44)     # deep osteoderm plate groove on armored legs
    TOR_CREAM   = (208, 186, 138)  # warm pale straw-cream throat & chin
    BEAK_BODY   = (136, 118, 86)   # weathered olive-horn tortoise beak body
    BEAK_EDGE   = (76, 64, 46)     # smooth dark smoky-horn curved cutting edge (Photo 3!)

    # --- 4. WRINKLED WARM SANDY-TAN TORTOISE NECK & THROAT (matches Head & Legs seamlessly!) ---
    if part == "neck":
        oy, sy = c["origin"][1], max(0.01, c["size"][1])
        t_up = (y - oy) / sy
        base = mix(TOR_CREAM, TOR_BASE, max(0.0, min(1.0, (t_up - 0.12) / 0.55)))
        # Subtle tortoise neck wrinkles (transverse creases along Z + fine pebble scale texture!)
        wrinkle = math.sin(wz * 2.6 + n1 * 1.4)
        sc = _hash3(int(math.floor(wx * 2.2)), int(math.floor(wy * 2.2)), int(math.floor(wz * 2.2)))
        if t_up > 0.18 and face != "down":
            if wrinkle < -0.68 or sc < 0.18:
                base = mix(base, TOR_SEAM, 0.52)
            elif sc > 0.78:
                base = mix(base, TOR_HI, 0.42)
            elif 0.35 < sc < 0.50:
                base = mix(base, TOR_OLIVE, 0.32)
        return mix(base, shade(base, 0.94 + 0.12 * n_fine), 0.5)

    if part == "throat":
        col = mix(TOR_CREAM, TOR_HI, 0.28 * n1 + 0.15 * n_fine)
        if math.sin(wz * 2.4) < -0.72:
            col = mix(col, TOR_SEAM, 0.25)
        return shade(col, 0.92 if face == "down" else 1.0)

    # --- 5. CLASSIC LEOPARD TORTOISE HEAD SCALES, DELICATE NOSTRILS, CURVED BEAK & EYES (Photo 3!) ---
    if part in ("head_dome", "head_brow", "head_crown"):
        # Sun-baked golden-tan pebbled tortoise head plates with subtle dusty-olive & cream mottling
        sc = _hash3(int(math.floor(wx * 2.3)), int(math.floor(wy * 2.3)), int(math.floor(wz * 2.3)))
        col = mix(TOR_BASE, TOR_HI, 0.32 * n1 + 0.22 * n_fine)
        if sc < 0.22:
            col = mix(col, TOR_SEAM, 0.54)
        elif sc > 0.76:
            col = mix(col, TOR_HI, 0.48)
        elif 0.38 < sc < 0.54:
            col = mix(col, TOR_OLIVE, 0.38)
        if part == "head_brow":
            col = mix(col, TOR_SEAM, 0.14 if face != "up" else 0.04)
        return col

    if part in ("head_side", "snout"):
        oy, sy = c["origin"][1], max(0.01, c["size"][1])
        t_up = (y - oy) / sy
        # Two tiny delicate pinprick nostrils set high on the front nose tip (Photo 3!)
        if part == "snout" and face == "north":
            if 0.50 < t_up < 0.72 and 0.32 < abs(wx) < 0.76:
                return (42, 34, 26)
            if 0.44 < t_up < 0.78 and 0.22 < abs(wx) < 0.88:
                return mix(TOR_SEAM, (78, 64, 46), 0.42)
        sc = _hash3(int(math.floor(wx * 2.2)), int(math.floor(wy * 2.2)), int(math.floor(wz * 2.2)))
        base = mix(TOR_CREAM, TOR_BASE, max(0.0, min(1.0, (t_up + 0.12) / 0.80)))
        if sc < 0.22:
            base = mix(base, TOR_SEAM, 0.50)
        elif sc > 0.76:
            base = mix(base, TOR_HI, 0.45)
        elif 0.36 < sc < 0.50:
            base = mix(base, TOR_OLIVE, 0.34)
        return mix(base, shade(base, 0.94 + 0.12 * n_fine), 0.5)

    if part in ("beak_upper", "beak_front", "beak_hook"):
        oy, sy = c["origin"][1], max(0.01, c["size"][1])
        t_up = (y - oy) / sy
        if face == "down":
            # Smooth keratinous inner biting rim visible when mouth opens wide
            return mix((186, 168, 134), BEAK_BODY, 0.40 + 0.18 * n_fine)
        # Smooth organic transition from warm head scales above into smoky dark-horn curved beak edge below (Photo 3!)
        if t_up < 0.24:
            col = mix(BEAK_EDGE, BEAK_BODY, t_up / 0.24 * 0.45)
        elif t_up < 0.66:
            col = mix(BEAK_BODY, TOR_OLIVE, (t_up - 0.24) / 0.42 * 0.65)
        else:
            col = mix(TOR_OLIVE, TOR_BASE, (t_up - 0.66) / 0.34)
        return mix(col, shade(col, 0.93 + 0.14 * n_fine), 0.45)

    if part == "beak_lower":
        oy, sy = c["origin"][1], max(0.01, c["size"][1])
        t_up = (y - oy) / sy
        # Curved lower keratin beak rim: dark horn outer lower lip with clean paler-horn sharp upper crest
        if face == "up":
            return mix((182, 162, 128), BEAK_BODY, 0.40 + 0.18 * n_fine)
        if t_up > 0.72:
            col = mix(BEAK_BODY, BEAK_EDGE, (t_up - 0.72) / 0.28 * 0.85)
        else:
            col = mix(TOR_SEAM, BEAK_BODY, t_up / 0.72)
        return mix(col, shade(col, 0.93 + 0.14 * n_fine), 0.45)

    if part == "jaw":
        sc = _hash3(int(math.floor(wx * 2.2)), int(math.floor(wy * 2.2)), int(math.floor(wz * 2.2)))
        col = mix(TOR_CREAM, TOR_BASE, 0.32 + 0.22 * n1)
        if sc < 0.24:
            col = mix(col, TOR_SEAM, 0.46)
        elif sc > 0.78:
            col = mix(col, TOR_HI, 0.38)
        return shade(col, 0.90 if face == "down" else 1.0)

    if part == "mouth_inner":
        return mix((128, 58, 64), (152, 74, 80), n_fine * 0.45)

    if part == "mouth_throat":
        return mix((58, 26, 32), (82, 38, 44), n_fine * 0.35)

    if part == "mouth_gum":
        return mix((202, 144, 144), (220, 166, 164), 0.35 + 0.4 * n_fine)

    if part == "tongue":
        # ONE seamless plump fleshy pink tortoise tongue with a soft paler central V-groove painted smoothly!
        cx_dist = abs(wx)
        if face == "up" and cx_dist < 0.26:
            return mix((218, 158, 166), (232, 178, 186), 0.35 + 0.35 * n_fine)
        if face == "up" and cx_dist < 0.42:
            return mix((198, 126, 134), (214, 146, 154), 0.4 + 0.3 * n_fine)
        return mix((184, 104, 114), (202, 122, 132), 0.35 + 0.4 * n_fine)

    if part == "eye":
        # Glossy jet-black tortoise eye with warm scaly eyelid corners & crisp white glint (Photo 3!)
        oy, sy = c["origin"][1], max(0.01, c["size"][1])
        oz0, sz = c["origin"][2], max(0.01, c["size"][2])
        tu = (y - oy) / sy
        tf = 1.0 - (z - oz0) / sz
        if face in ("east", "west"):
            dx = (tf - 0.5) * 2.0
            dy = (tu - 0.5) * 2.0
            if dx * dx + dy * dy > 1.15:
                return mix(TOR_SEAM, TOR_BASE, 0.45)
            if tu > 0.52 and tf > 0.48 and tu < 0.88 and tf < 0.88:
                return (246, 248, 252)
            if tu < 0.16 or tu > 0.88:
                return (38, 30, 24)
            return (16, 16, 20)
        if face == "north" and tu > 0.58 and tu < 0.84:
            return (225, 232, 240)
        return (24, 20, 18)

    # --- 6. ARMORED GOLDEN-TAN COLUMNAR TORTOISE LEGS, FEET, IVORY NAILS & TAIL (Photo 1 & 2!) ---
    if part in ("leg_upper", "leg_lower", "foot", "tail"):
        if face == "down":
            return shade(TOR_CREAM, 0.84)
        # Chunky armored tortoise osteoderm scales on the outer & front faces of the columnar legs!
        is_armor_face = (part in ("leg_lower", "foot") and face in ("north", "east", "west"))
        if is_armor_face:
            # Staggered blocky armored tortoise scale plates (warm golden-tan plates with earthy-brown grooves!)
            row = int(math.floor(wy * 1.15))
            off = 0.5 if (row % 2 == 1) else 0.0
            u_sc = (wx + wz) * 0.85 + off
            v_sc = wy * 1.15
            fu = u_sc - math.floor(u_sc)
            fv = v_sc - math.floor(v_sc)
            plate_h = _hash3(int(math.floor(u_sc)), row, int(math.floor(wz * 0.7)))
            if fu < 0.18 or fv < 0.18:
                # Deep warm brown armored scale crevice (no cold black polka dots!)
                return mix(TOR_DEEP, TOR_SEAM, 0.35 + 0.35 * n_fine)
            # Warm sunlit golden-ochre armored scale plate
            col = mix(TOR_BASE, TOR_HI, 0.25 + 0.55 * plate_h)
            if plate_h < 0.28:
                col = mix(col, TOR_OLIVE, 0.42)
            return mix(col, shade(col, 0.94 + 0.12 * n_fine), 0.45)
        # Upper legs, inner legs & stubby tail: warm pebbled & wrinkled sandy-tan tortoise skin
        sc = _hash3(int(math.floor(wx * 2.0)), int(math.floor(wy * 2.0)), int(math.floor(wz * 2.0)))
        col = mix(TOR_BASE, TOR_HI, 0.28 * n1 + 0.18 * n_fine)
        if sc < 0.24:
            col = mix(col, TOR_SEAM, 0.54)
        elif sc > 0.78:
            col = mix(col, TOR_HI, 0.42)
        elif 0.36 < sc < 0.52:
            col = mix(col, TOR_OLIVE, 0.35)
        if part == "foot":
            col = shade(col, 0.94)
        return col

    if part == "claw":
        oz0, sz = c["origin"][2], max(0.01, c["size"][2])
        tf = 1.0 - (z - oz0) / sz
        col = mix((168, 146, 110), CLAW_TIP, max(0.0, min(1.0, tf * 1.15)))
        return shade(col, 0.92 if face == "down" else 1.0)

    return PLAIN_COLOURS.get(part, (164, 140, 102))


# ======================================================================================================================
# Stage 5: 3D World-Space Leg IK + 12 Biologically Faithful African Leopard Tortoise Animation Clips
# ======================================================================================================================
WALK_LEN = 1.60         # lumbering heavy tortoise stride period (s)
WALK_DUTY = 0.78        # high duty factor: 3-4 feet solidly planted at all times!
WALK_SWEEP = 2.60       # half-stride sweep (px) -> ground speed = 2 * 2.60 / (0.78 * 1.60) = 4.1667 px/s
WALK_SPEED = 2.0 * WALK_SWEEP / (WALK_DUTY * WALK_LEN)  # 4.1667 px/s (~0.161 blocks/s at MODEL_SCALE=0.62)

_OX_FL = 1.0 + SHELL_LEN * 0.81
_OX_BL = 1.0 + SHELL_LEN * 0.20
_SHELL_PIV = [0.0, round(BELLY_Y + 4.0, 3), round(oz(1.0 + SHELL_LEN * 0.48), 3)]

# Exact rest-pose leg specs: (prefix, side, hip, knee, ankle, pole_world, foot_yaw)
_LEG_SPECS = []
for _side, _tag in ((1, "leg_fl"), (-1, "leg_fr")):
    _hip = [_side * (SHELL_HW * 0.48), BELLY_Y + 3.5, oz(_OX_FL - 1.1)]
    _knee = [_side * (LEG_SPLAY - 0.7), BELLY_Y + 2.0, oz(_OX_FL - 0.1)]
    _ankle = [_side * LEG_SPLAY, 1.6, oz(_OX_FL + 1.0)]
    # Pole points along exact rest elbow direction (outward + slightly up/back) so rest IK == exact [0, 0, 0]!
    _mid = [0.5 * (_hip[i] + _ankle[i]) for i in range(3)]
    _pole = unit(vsub(_knee, _mid))
    _LEG_SPECS.append((_tag, _side, _hip, _knee, _ankle, _pole, -14.0))

for _side, _tag in ((1, "leg_bl"), (-1, "leg_br")):
    _hip = [_side * (SHELL_HW * 0.46), BELLY_Y + 3.3, oz(_OX_BL + 1.3)]
    _knee = [_side * (LEG_SPLAY - 0.9), BELLY_Y + 1.9, oz(_OX_BL + 0.3)]
    _ankle = [_side * (LEG_SPLAY - 0.3), 1.6, oz(_OX_BL - 0.4)]
    _mid = [0.5 * (_hip[i] + _ankle[i]) for i in range(3)]
    _pole = unit(vsub(_knee, _mid))
    _LEG_SPECS.append((_tag, _side, _hip, _knee, _ankle, _pole, -20.0))


def _mat_T(M):
    return [[M[j][i] for j in range(3)] for i in range(3)]


def _mat_mul(A, B):
    return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _rot_from_to(a, b):
    """Exact Rodrigues shortest-arc rotation matrix mapping unit vector a -> unit vector b (zero twist!)."""
    c = max(-1.0, min(1.0, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]))
    vx = a[1] * b[2] - a[2] * b[1]
    vy = a[2] * b[0] - a[0] * b[2]
    vz = a[0] * b[1] - a[1] * b[0]
    s2 = vx * vx + vy * vy + vz * vz
    if s2 < 1e-12:
        return [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]
    k = 1.0 / (1.0 + c)
    return [
        [1.0 - k * (vy * vy + vz * vz), -vz + k * vx * vy,              vy + k * vx * vz],
        [vz + k * vx * vy,              1.0 - k * (vx * vx + vz * vz), -vx + k * vy * vz],
        [-vy + k * vx * vz,             vx + k * vy * vz,              1.0 - k * (vx * vx + vy * vy)],
    ]


def _solve_tortoise_legs(pose, targets):
    """Exact 3D world-space two-bone IK for all 4 splayed columnar tortoise legs.
    targets[prefix] = (dx, y_ankle, dz, toe_pitch) relative to rest ankle (x, 1.6, z).
    Guarantees planted feet stay 100% locked flat on y = 0.00 with zero floor clipping or slip!"""
    sh = pose.get("shell", {})
    sh_pos = sh.get("position", [0.0, 0.0, 0.0])
    sh_rot = sh.get("rotation", [0.0, 0.0, 0.0])
    R_sh = euler(sh_rot)
    R_sh_T = _mat_T(R_sh)

    for prefix, side, hip, knee, ankle, pole, _ in _LEG_SPECS:
        dx, y_ank, dz, toe = targets[prefix]
        # World position of hip pivot after shell rotation & translation
        rel_h = vsub(hip, _SHELL_PIV)
        hip_w = vadd(vadd(_SHELL_PIV, mat_vec(R_sh, rel_h)), sh_pos)
        tgt_w = [ankle[0] + dx, y_ank, ankle[2] + dz]

        u_rest = vsub(knee, hip)
        l_rest = vsub(ankle, knee)
        L1, L2 = vlen(u_rest), vlen(l_rest)
        u_hat, l_hat = unit(u_rest), unit(l_rest)

        # Keep knee/elbow bending cleanly outward along shell-rotated rest pole
        pole_w = unit(mat_vec(R_sh, pole))
        knee_w, ank_w, _ = two_bone(hip_w, L1, L2, tgt_w, pole_w, reach=0.992)

        d1_loc = unit(mat_vec(R_sh_T, vsub(knee_w, hip_w)))
        R1 = _rot_from_to(u_hat, d1_loc)
        r1 = rot(R1)
        R_leg1 = _mat_mul(R_sh, euler(r1))

        d2_loc = unit(mat_vec(_mat_T(R_leg1), vsub(ank_w, knee_w)))
        R2 = _rot_from_to(l_hat, d2_loc)
        r2 = rot(R2)
        R_leg2 = _mat_mul(R_leg1, euler(r2))

        # Keep foot pad flush horizontal (or pitched toe-up during swing) in world space!
        R_foot_w = euler([toe, 0.0, 0.0])
        r3 = rot(_mat_mul(_mat_T(R_leg2), R_foot_w))

        pose[f"{prefix}_1"] = {"rotation": r1}
        pose[f"{prefix}_2"] = {"rotation": r2}
        pose[f"{prefix}_3"] = {"rotation": r3}


def _planted_legs(pose):
    targets = {pref: (0.0, 1.6, 0.0, 0.0) for pref, *_ in _LEG_SPECS}
    _solve_tortoise_legs(pose, targets)


def _blink_pose(pose, amt):
    """Slide scaly eyelids smoothly down (-1.62 px) to cover the glossy dark eyes."""
    y = -1.62 * amt
    pose["lid_left"] = {"position": [0.0, y, 0.0]}
    pose["lid_right"] = {"position": [0.0, y, 0.0]}


def _clip_idle():
    # Alert savanna stance: slow tortoise breathing pulse, curious head scan, gentle double blinks, planted solid!
    L = 4.4
    def pose_at(t):
        ph = 2.0 * math.pi * t / L
        breath = math.sin(ph)
        scan = math.sin(ph * 0.5)
        blink = max(pulse(t, 1.25, 0.09, 0.05, 0.11), pulse(t, 3.45, 0.09, 0.05, 0.11))
        p = {
            "root": {"position": [0.0, 0.0, 0.0]},
            "shell": {"position": [0.0, -0.04 + 0.07 * breath, 0.0],
                      "rotation": [0.35 * breath, 0.0, 0.30 * scan]},
            "neck_1": {"position": [0.0, 0.0, 0.0],
                       "rotation": [1.0 * breath, 2.2 * scan, 0.0]},
            "neck_2": {"position": [0.0, 0.0, 0.0],
                       "rotation": [-0.8 * breath, 2.4 * scan, 0.0]},
            "head": {"rotation": [1.4 * math.sin(ph - 0.6), 3.5 * scan, 0.6 * scan]},
            "jaw": {"rotation": [0.0, 0.0, 0.0]},
            "tail_1": {"rotation": [0.0, 4.5 * math.sin(ph), 0.0]},
            "tail_2": {"rotation": [0.0, 6.0 * math.sin(ph - 0.5), 0.0]},
        }
        _blink_pose(p, blink)
        _planted_legs(p)
        return p
    return clip(L, pose_at, 44, loop=True)


def _clip_walk():
    # Authentic heavy tortoise lateral-sequence 4-beat plod (BL -> FL -> BR -> FR), duty=0.78, zero foot slip!
    L, duty, sweep = WALK_LEN, WALK_DUTY, WALK_SWEEP
    phases = {"leg_bl": 0.00, "leg_fl": 0.25, "leg_br": 0.50, "leg_fr": 0.75}
    def pose_at(t):
        u = t / L
        ph = 2.0 * math.pi * u
        sh_dy = -0.22 + 0.08 * math.cos(4.0 * ph)
        sh_rz = 2.0 * math.sin(ph)
        sh_ry = 1.6 * math.cos(ph)
        sh_rx = 0.45 * math.sin(2.0 * ph)
        targets = {}
        for prefix, *_ in _LEG_SPECS:
            sw, lift = stride(u + phases[prefix], duty)
            dz = -sw * sweep
            y_ank = 1.62 + (1.85 * (lift ** 0.70) if lift > 0.0 else 0.0)
            toe = 6.0 * lift
            targets[prefix] = (0.0, y_ank, dz, toe)
        p = {
            "root": {"position": [0.0, 0.0, 0.0]},
            "shell": {"position": [0.0, sh_dy, 0.0], "rotation": [sh_rx, sh_ry, sh_rz]},
            "neck_1": {"position": [0.0, 0.0, 0.0],
                       "rotation": [1.4 * math.sin(2.0 * ph), -2.2 * math.cos(ph), -sh_rz * 0.45]},
            "neck_2": {"position": [0.0, 0.0, 0.0],
                       "rotation": [-1.0 * math.sin(2.0 * ph - 0.4), -1.4 * math.cos(ph), -sh_rz * 0.35]},
            "head": {"rotation": [-1.2 * math.sin(2.0 * ph - 0.6), -1.0 * math.cos(ph), -sh_rz * 0.20]},
            "jaw": {"rotation": [0.0, 0.0, 0.0]},
            "tail_1": {"rotation": [0.0, 8.0 * math.sin(ph - 0.4), 0.0]},
            "tail_2": {"rotation": [0.0, 10.0 * math.sin(ph - 0.9), 0.0]},
        }
        _blink_pose(p, 0.0)
        _solve_tortoise_legs(p, targets)
        return p
    return clip(L, pose_at, 40, loop=True)


def _clip_graze():
    # Low savanna grass & succulent grazing: neck reaches down, curved 3D beak chomps twice per cycle (zero ground clip)!
    L = 2.6
    def pose_at(t):
        ph = 2.0 * math.pi * t / L
        sweep = math.sin(ph)
        chomp = max(pulse(t, 0.35, 0.28, 0.14, 0.14), pulse(t, 1.55, 0.28, 0.14, 0.14))
        blink = pulse(t, 2.05, 0.08, 0.05, 0.10)
        p = {
            "root": {"position": [0.0, 0.0, 0.0]},
            "shell": {"position": [0.0, -0.14, 0.0], "rotation": [1.3, 0.6 * sweep, 0.5 * sweep]},
            "neck_1": {"position": [0.0, 0.0, 0.0], "rotation": [12.5 + 2.0 * chomp, 6.0 * sweep, 0.0]},
            "neck_2": {"position": [0.0, 0.0, 0.0], "rotation": [8.0 + 1.5 * chomp, 5.5 * sweep, 0.0]},
            "head": {"rotation": [2.5 - 4.0 * chomp, 6.0 * sweep, -2.0 * sweep]},
            "jaw": {"rotation": [22.0 * chomp, 0.0, 0.0]},
            "tail_1": {"rotation": [0.0, 5.0 * sweep, 0.0]},
            "tail_2": {"rotation": [0.0, 6.5 * math.sin(ph - 0.4), 0.0]},
        }
        _blink_pose(p, blink)
        _planted_legs(p)
        return p
    return clip(L, pose_at, 40, loop=True)


def _clip_bite():
    # Defensive tortoise hiss & beak snap: coil (0..0.20s), wide +40 deg curved-beak gape lunge, SNAP on tick 10 (0.50s)!
    L = 0.95
    def pose_at(t):
        coil = pulse(t, 0.0, 0.16, 0.04, 0.10)
        lunge = pulse(t, 0.20, 0.18, 0.16, 0.28)
        gape = ramp(t, ((0.0, 0.0), (0.16, 12.0), (0.38, 40.0), (0.49, 2.0), (0.58, 0.0), (L, 0.0)))
        snap = pulse(t, 0.47, 0.04, 0.05, 0.14)
        gf = gape / 40.0
        p = {
            "root": {"position": [0.0, 0.0, 0.0]},
            "shell": {"position": [0.0, -0.10 - 0.24 * coil - 0.14 * lunge, 0.60 * coil - 0.90 * lunge],
                      "rotation": [-1.2 * coil + 1.0 * lunge, 0.0, 0.0]},
            "neck_1": {"position": [0.0, 0.0, 1.3 * coil - 0.7 * lunge],
                       "rotation": [-8.0 * coil + 5.0 * lunge, 0.0, 0.0]},
            "neck_2": {"position": [0.0, 0.0, 0.6 * coil - 0.4 * lunge],
                       "rotation": [6.0 * coil - 3.5 * lunge, 0.0, 0.0]},
            "head": {"rotation": [-4.0 * coil - 9.0 * gf + 5.0 * snap, 0.0, 0.0]},
            "jaw": {"rotation": [gape, 0.0, 0.0]},
            "tail_1": {"rotation": [4.0 * lunge, 0.0, 0.0]},
            "tail_2": {"rotation": [4.0 * lunge, 0.0, 0.0]},
        }
        _blink_pose(p, snap)
        _planted_legs(p)
        return p
    return clip(L, pose_at, 32, loop=False)


def _clip_drink():
    # Pan / waterhole drinking: neck dips right down to the waterline with slow rhythmic throat-gulp jaw sips!
    L = 2.8
    def pose_at(t):
        ph = 2.0 * math.pi * t / L
        sip = 0.5 - 0.5 * math.cos(3.0 * ph)
        blink = pulse(t, 1.60, 0.10, 0.08, 0.12)
        p = {
            "root": {"position": [0.0, 0.0, 0.0]},
            "shell": {"position": [0.0, -0.16, 0.0], "rotation": [1.5, 0.0, 0.0]},
            "neck_1": {"position": [0.0, 0.0, 0.0], "rotation": [15.0 + 1.0 * sip, 0.0, 0.0]},
            "neck_2": {"position": [0.0, 0.0, 0.0], "rotation": [9.0 + 1.2 * sip, 0.0, 0.0]},
            "head": {"rotation": [2.0 - 1.8 * sip, 0.0, 0.0]},
            "jaw": {"rotation": [10.0 * sip, 0.0, 0.0]},
            "tail_1": {"rotation": [0.0, 3.0 * math.sin(ph), 0.0]},
            "tail_2": {"rotation": [0.0, 4.0 * math.sin(ph - 0.4), 0.0]},
        }
        _blink_pose(p, blink)
        _planted_legs(p)
        return p
    return clip(L, pose_at, 36, loop=True)


def _retract_pose(f, breath=0.0, peek=0.0, blink_ov=None):
    """Shared Cryptodira straight-back tortoise retraction + armored forearm shield tuck (f=0 alert -> f=1 fortress).
    Plastron settles flush onto y=0.20, head pulls straight back cleanly INSIDE the snug hollow front cavern
    (zero shell clipping!), and both armored forelegs fold snugly inside the arched doorway!"""
    f = max(0.0, min(1.0, f))
    nf = max(0.0, min(1.0, f - 0.35 * peek))
    sh_dy = -0.04 + (-3.40 - (-0.04)) * f + 0.05 * breath * f
    p = {
        "root": {"position": [0.0, 0.0, 0.0]},
        "shell": {"position": [0.0, sh_dy, 0.0], "rotation": [0.5 * f, 0.0, 0.0]},
        "neck_1": {"position": [0.0, -0.55 * nf, 4.4 * nf], "rotation": [-4.0 * nf, 0.0, 0.0]},
        "neck_2": {"position": [0.0, -0.50 * nf, 3.4 * nf], "rotation": [6.0 * nf, 4.0 * peek, 0.0]},
        "head": {"rotation": [-2.0 * nf, 10.0 * peek, 0.0]},
        "jaw": {"rotation": [0.0, 0.0, 0.0]},
        "tail_1": {"position": [0.0, 0.25 * f, -1.2 * f], "rotation": [-12.0 * f, 0.0, 0.0]},
        "tail_2": {"rotation": [-10.0 * f, 0.0, 0.0]},
    }
    # Armored forelegs fold inward horizontally snugly inside the arched front doorway (zero knee or floor clipping!)
    for side, tag in ((1, "leg_fl"), (-1, "leg_fr")):
        p[f"{tag}_1"] = {"position": [-side * 1.15 * f, 3.35 * f, 0.55 * f],
                         "rotation": [-6.0 * f, side * 46.0 * f, side * 10.0 * f]}
        p[f"{tag}_2"] = {"rotation": [10.0 * f, side * 26.0 * f, -side * 10.0 * f]}
        p[f"{tag}_3"] = {"rotation": [-4.0 * f, 0.0, 0.0]}
    for side, tag in ((1, "leg_bl"), (-1, "leg_br")):
        p[f"{tag}_1"] = {"position": [-side * 1.05 * f, 3.45 * f, -0.85 * f],
                         "rotation": [6.0 * f, -side * 34.0 * f, side * 12.0 * f]}
        p[f"{tag}_2"] = {"rotation": [8.0 * f, 0.0, -side * 10.0 * f]}
        p[f"{tag}_3"] = {"rotation": [-4.0 * f, 0.0, 0.0]}
    blink = f if blink_ov is None else blink_ov
    _blink_pose(p, blink)
    return p


def _clip_retract_in():
    L = 0.65
    return clip(L, lambda t: _retract_pose(ease(t / L)), 22, loop=False)


def _clip_retract_hold():
    L = 3.2
    return clip(L, lambda t: _retract_pose(1.0, breath=math.sin(2.0 * math.pi * t / L)), 24, loop=True)


def _clip_retract_out():
    L = 0.85
    def pose_at(t):
        # 0..0.38s: crack eyes open & peek head cautiously out left-right; 0.38..0.85s: stand smoothly back up!
        u = t / L
        f = 1.0 - ease(max(0.0, (u - 0.36) / 0.64))
        peek = pulse(t, 0.08, 0.14, 0.14, 0.22) * math.sin(2.0 * math.pi * (t - 0.08) * 1.8)
        blink = 1.0 - ease(min(1.0, u / 0.24))
        return _retract_pose(f, peek=peek, blink_ov=blink)
    return clip(L, pose_at, 28, loop=False)


def _bask_pose(f, breath=0.0, blink=0.0):
    """Sunny savanna tortoise basking sploot (f=0 alert -> f=1 cozy belly-down sunbath).
    Solved with exact 3D leg IK so plastron settles gently onto warm sand while all 4 splayed feet rest flat on y=0!"""
    f = max(0.0, min(1.0, f))
    sh_dy = -0.04 + (-3.05 - (-0.04)) * f + 0.07 * breath * f
    p = {
        "root": {"position": [0.0, 0.0, 0.0]},
        "shell": {"position": [0.0, sh_dy, 0.0], "rotation": [0.5 * f, 0.0, 0.0]},
        "neck_1": {"position": [0.0, 0.0, -0.7 * f], "rotation": [7.0 * f + 0.8 * breath * f, 0.0, 0.0]},
        "neck_2": {"position": [0.0, 0.0, -0.4 * f], "rotation": [4.0 * f, 0.0, 0.0]},
        "head": {"rotation": [-5.0 * f, 2.0 * breath * f, 0.0]},
        "jaw": {"rotation": [0.0, 0.0, 0.0]},
        "tail_1": {"rotation": [3.0 * f, 2.5 * breath * f, 0.0]},
        "tail_2": {"rotation": [2.0 * f, 3.0 * breath * f, 0.0]},
    }
    y_splay = 1.60 + 0.72 * f
    targets = {
        "leg_fl": ( 1.45 * f, y_splay, -1.80 * f, 0.0),
        "leg_fr": (-1.45 * f, y_splay, -1.80 * f, 0.0),
        "leg_bl": ( 1.55 * f, y_splay,  2.40 * f, 0.0),
        "leg_br": (-1.55 * f, y_splay,  2.40 * f, 0.0),
    }
    _solve_tortoise_legs(p, targets)
    _blink_pose(p, blink)
    return p


def _clip_bask_down():
    L = 0.80
    return clip(L, lambda t: _bask_pose(ease(t / L), blink=0.35 * ease(t / L)), 24, loop=False)


def _clip_bask():
    L = 4.2
    def pose_at(t):
        ph = 2.0 * math.pi * t / L
        blink = max(0.35, pulse(t, 1.2, 0.35, 1.2, 0.45))
        return _bask_pose(1.0, breath=math.sin(ph), blink=blink)
    return clip(L, pose_at, 32, loop=True)


def _clip_bask_up():
    L = 0.80
    return clip(L, lambda t: _bask_pose(1.0 - ease(t / L), blink=0.35 * (1.0 - ease(t / L))), 24, loop=False)


def _clip_death():
    # Heavy collapse flush onto ground, head drooped limp with eyes shut, limbs relaxed outward on y=0.00!
    L = 1.20
    def pose_at(t):
        f = ease(t / L)
        p = {
            "root": {"position": [0.0, 0.0, 0.0]},
            "shell": {"position": [0.0, -2.95 * f, 0.0], "rotation": [1.0 * f, 0.0, 2.2 * f]},
            "neck_1": {"position": [0.0, 0.0, -0.4 * f], "rotation": [10.0 * f, 6.0 * f, 0.0]},
            "neck_2": {"position": [0.0, 0.0, 0.0], "rotation": [6.5 * f, 5.0 * f, 0.0]},
            "head": {"rotation": [1.5 * f, 4.0 * f, 6.0 * f]},
            "jaw": {"rotation": [7.0 * f, 0.0, 0.0]},
            "tail_1": {"rotation": [5.0 * f, 0.0, 0.0]},
            "tail_2": {"rotation": [4.0 * f, 0.0, 0.0]},
        }
        y_splay = 1.60 + 0.72 * f
        targets = {
            "leg_fl": ( 1.80 * f, y_splay, -1.20 * f, 0.0),
            "leg_fr": (-1.80 * f, y_splay, -1.20 * f, 0.0),
            "leg_bl": ( 1.85 * f, y_splay,  1.80 * f, 0.0),
            "leg_br": (-1.85 * f, y_splay,  1.80 * f, 0.0),
        }
        _solve_tortoise_legs(p, targets)
        _blink_pose(p, f)
        return p
    return clip(L, pose_at, 28, loop="hold_on_last_frame")


def clips():
    return {
        "idle":         _clip_idle(),
        "walk":         _clip_walk(),
        "graze":        _clip_graze(),
        "bite":         _clip_bite(),
        "drink":        _clip_drink(),
        "retract_in":   _clip_retract_in(),
        "retract_hold": _clip_retract_hold(),
        "retract_out":  _clip_retract_out(),
        "bask_down":    _clip_bask_down(),
        "bask":         _clip_bask(),
        "bask_up":      _clip_bask_up(),
        "death":        _clip_death(),
    }


WORKSHOP_CLIPS = clips


def workshop_look(head_variant=None):
    bones = build_bones(head_variant=head_variant)
    painter = plain_colour if WORKSHOP_LOOK == "plain" else terrapin_colour
    bones, rows, w, h = paint_faces(bones, TEXELS, painter)
    return bones, png(rows), w, h


def export_game():
    """Writes geo, texture, and animations into the savanna mod (write_assets auto-applies model_parts.game_faces!)."""
    bones, png_bytes, w, h = workshop_look()
    all_clips = clips()
    write_assets({
        "geo/entity/leopard_tortoise.geo.json": geo("leopard_tortoise", bones, w, h, (2.6, 1.4, [0, 0.6, 0])),
        "textures/entity/leopard_tortoise.png": png_bytes,
        "animations/entity/leopard_tortoise.animation.json": animations(all_clips),
    }, mod="savanna")


if __name__ == "__main__":
    if "game" in sys.argv[1:]:
        export_game()
    else:
        bones = build_bones()
        n_boxes = sum(len(b.get("cubes", [])) for b in bones)
        all_clips = clips()
        print(f"leopard_tortoise ({WORKSHOP_LOOK}): {TOTAL_LEN:.1f} px -> "
              f"~{TOTAL_LEN*MODEL_SCALE/16:.2f} blocks at {MODEL_SCALE}x (shell ~{SHELL_LEN*MODEL_SCALE/16:.2f}x{DOME_H*MODEL_SCALE/16:.2f} blocks), "
              f"{len(bones)} bones, {n_boxes} boxes (small budget: 150), {len(all_clips)} clips")
