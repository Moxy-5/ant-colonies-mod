"""Generates all game-ready Phase 1–4 Savanna Mud block, item, and player coat assets.

Writes directly into `savanna/src/main/resources/assets/antcolonies/`:
  - `textures/block/savanna_mud_*.png` (greyscale/white base tinted by BlockColor `tintindex: 0`)
  - `textures/block/savanna_mud_soupy.png` (16x64 animated 4-frame strip) + `.png.mcmeta`
  - `textures/block/savanna_mud_sticky_{hoof,heavy,paw,reptile,boot}.png`
  - `textures/block/savanna_mud_cocoon.png` (cracked mud with lungfish breathing pore)
  - `textures/block/savanna_mud_rub_overlay.png` (shoulder-height rubbing post band)
  - `textures/item/savanna_mud_ball.png` (throwable regional mud ball icon)
  - `textures/entity/player_mud_{low,full}.png` (64x64 player skin mud overlay masks)
  - `models/block/tinted_cube_bottom_top.json` + all mud stage, track, cocoon & rub models
  - `blockstates/savanna_mud.json` (multipart, clean across any `tone` property)
  - `models/item/savanna_mud.json` & `models/item/savanna_mud_ball.json`

Usage:
  python tools/mud_block_assets.py
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image

import mod_paths


def _hash2(x: int, y: int, seed: int) -> float:
    n = ((x * 374761393) + (y * 668265263) + (seed * 1442695041)) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return (n & 0xFFFFFF) / float(0x1000000)


def _tile_noise(x: float, y: float, period: int, seed: int) -> float:
    """Smooth tileable 2D value noise over [0, period)."""
    ix = math.floor(x) % period
    iy = math.floor(y) % period
    ix1 = (ix + 1) % period
    iy1 = (iy + 1) % period
    fx = x - math.floor(x)
    fy = y - math.floor(y)
    ux = fx * fx * (3.0 - 2.0 * fx)
    uy = fy * fy * (3.0 - 2.0 * fy)
    a = _hash2(ix, iy, seed)
    b = _hash2(ix1, iy, seed)
    c = _hash2(ix, iy1, seed)
    d = _hash2(ix1, iy1, seed)
    return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy


def _clamp_u8(v: float) -> int:
    return max(0, min(255, int(round(v))))


def _make_img(w: int, h: int, fn) -> Image.Image:
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = fn(x, y)
    return img


def _grey(v: float, a: int = 255) -> tuple[int, int, int, int]:
    c = _clamp_u8(v)
    return (c, c, c, _clamp_u8(a))


def make_soupy_strip() -> Image.Image:
    """16x64 4-frame animated seamless rippling soupy wallow texture (tinted via BlockColor)."""
    frames = 4
    def px(x, y):
        f = y // 16
        ly = y % 16
        phase = (f / frames) * math.tau
        # Soft diagonal liquid-mud ripples + subtle organic silt sheen
        w1 = math.sin((x / 16.0) * math.tau + (ly / 16.0) * math.tau + phase)
        w2 = math.cos((x / 16.0) * math.tau * 2.0 - (ly / 16.0) * math.tau - phase)
        n = _tile_noise(x * 0.5, ly * 0.5, 8, 101)
        grain = (_hash2(x, ly, 103) - 0.5) * 14.0
        shimmer = 14.0 if (w1 + 0.5 * w2) > 1.05 else 0.0
        v = 205.0 + w1 * 13.0 + w2 * 7.0 + (n - 0.5) * 26.0 + grain + shimmer
        return _grey(v)
    return _make_img(16, 16 * frames, px)


def _sticky_val(x: int, y: int) -> float:
    n1 = _tile_noise(x * 0.375, y * 0.375, 6, 201)
    n2 = _tile_noise(x * 0.75, y * 0.75, 12, 209)
    grain = (_hash2(x, y, 211) - 0.5) * 22.0
    return 210.0 + (n1 - 0.5) * 38.0 + (n2 - 0.5) * 24.0 + grain


def make_sticky(track: str | None = None) -> Image.Image:
    """16x16 glossy churned sticky mud, optionally stamped with an animal track."""
    def px(x, y):
        v = _sticky_val(x, y)
        if track == "hoof":
            # Pair of cloven antelope/warthog/buffalo hooves pointing north (-Y)
            for cx, cy in ((5.0, 5.5), (11.0, 11.0)):
                dx, dy = x - cx, y - cy
                d = math.hypot(dx * 1.15, dy * 0.85)
                if d < 2.5 and not (abs(dx) < 0.65 and dy < 0.8):
                    v -= 62.0
                elif 2.5 <= d < 3.4:
                    v += 16.0
        elif track == "heavy":
            # Big hippo / elephant / rhino round pad with 3 toe dimples at front (-Y)
            dx, dy = x - 8.0, y - 8.5
            d = math.hypot(dx, dy)
            toes = min(math.hypot(x - tx, y - ty) for tx, ty in ((5.0, 4.5), (8.0, 3.8), (11.0, 4.5)))
            if d < 4.2 or toes < 1.65:
                v -= 64.0
            elif d < 5.3 or toes < 2.3:
                v += 18.0
        elif track == "paw":
            # Clawed otter / monitor / predator pad + 4 toe prints
            for cx, cy in ((5.5, 5.5), (11.0, 11.5)):
                pad = math.hypot(x - cx, y - (cy + 1.0))
                toes = min(math.hypot(x - (cx + ox), y - (cy + oy)) for ox, oy in ((-1.8, -1.0), (-0.6, -1.8), (0.7, -1.8), (1.8, -1.0)))
                if pad < 1.4 or toes < 0.95:
                    v -= 58.0
                elif pad < 2.2:
                    v += 12.0
        elif track == "reptile":
            # Crocodile / monitor sinuous tail drag groove + splayed side claw marks
            center_x = 8.0 + math.sin(y * 0.4) * 1.2
            dist_line = abs(x - center_x)
            if dist_line < 1.55:
                v -= 56.0
            elif dist_line < 2.6:
                v += 16.0
            # Side claw dashes
            for cx, cy in ((3.2, 4.0), (12.8, 10.5)):
                if math.hypot(x - cx, y - cy) < 1.35:
                    v -= 52.0
        elif track == "boot":
            # Player boot sole with tread ridges
            if 5 <= x <= 10 and 3 <= y <= 12:
                ridge = (y % 2 == 0)
                v -= 60.0 if ridge else 34.0
            elif 4 <= x <= 11 and 2 <= y <= 13:
                v += 14.0
        return _grey(v)
    return _make_img(16, 16, px)


def make_drying() -> Image.Image:
    """16x16 matte firming rim mud with subtle early hairline shrinkage lines."""
    pts = [(2.5, 3.0), (10.5, 2.5), (6.5, 9.5), (13.5, 11.0), (3.0, 14.0)]
    def px(x, y):
        n = _tile_noise(x * 0.5, y * 0.5, 8, 301)
        grain = (_hash2(x, y, 307) - 0.5) * 24.0
        d1, d2 = 99.0, 99.0
        for cx, cy in pts:
            dx = min(abs(x - cx), 16.0 - abs(x - cx))
            dy = min(abs(y - cy), 16.0 - abs(y - cy))
            d = math.hypot(dx, dy)
            if d < d1:
                d2, d1 = d1, d
            elif d < d2:
                d2 = d
        hairline = -32.0 if (d2 - d1) < 0.75 and _hash2(x, y, 311) > 0.30 else 0.0
        v = 222.0 + (n - 0.5) * 28.0 + grain + hairline
        return _grey(v)
    return _make_img(16, 16, px)


def make_cracked(cocoon: bool = False) -> Image.Image:
    """16x16 sun-baked Voronoi clay plates; `cocoon=True` adds a subtle lungfish breathing pore."""
    pts = [(3.0, 2.5), (11.0, 2.0), (6.0, 8.5), (13.5, 10.5), (2.0, 13.5), (10.0, 14.5)]
    def px(x, y):
        n = _tile_noise(x * 0.5, y * 0.5, 8, 401)
        grain = (_hash2(x, y, 409) - 0.5) * 20.0
        d1, d2 = 99.0, 99.0
        for cx, cy in pts:
            dx = min(abs(x - cx), 16.0 - abs(x - cx))
            dy = min(abs(y - cy), 16.0 - abs(y - cy))
            d = math.hypot(dx, dy)
            if d < d1:
                d2, d1 = d1, d
            elif d < d2:
                d2 = d
        gap = d2 - d1
        if gap < 1.15:
            v = 112.0 + grain * 0.6
        elif gap < 1.8:
            v = 244.0 + grain * 0.5  # curled pale plate lip
        else:
            v = 228.0 + (n - 0.5) * 24.0 + grain
        if cocoon:
            # Subtle dark air-hole pore + raised dry mud rim right in the center
            dp = math.hypot(x - 7.5, y - 7.5)
            if dp < 1.6:
                v = 62.0 + grain * 0.3
            elif dp < 2.7:
                v = min(252.0, v + 22.0)
        return _grey(v)
    return _make_img(16, 16, px)


def make_rub_overlay() -> Image.Image:
    """16x16 shoulder-height mud-polished band overlay for giant trees & termite mounds."""
    def px(x, y):
        # Horizontal smeared mud band across middle of the block with jagged edges
        n = _tile_noise(x * 0.5, y * 0.5, 8, 501)
        dist = abs(y - 7.5) / 5.5 + (n - 0.5) * 0.55
        if dist > 1.05:
            return (0, 0, 0, 0)
        alpha = max(0.0, min(1.0, (1.05 - dist) / 0.35)) * (0.78 + 0.22 * _hash2(x, y, 503))
        streak = math.sin(x * 0.9 + y * 0.25) * 14.0
        v = 212.0 + streak + (_hash2(x, y, 509) - 0.5) * 24.0
        return _grey(v, int(round(alpha * 235)))
    return _make_img(16, 16, px)


def make_mud_ball_item() -> Image.Image:
    """16x16 chunky pixel-art throwable Savanna Mud Ball item texture (tinted by soil tone)."""
    def px(x, y):
        dx, dy = (x - 7.5) / 5.2, (y - 7.8) / 4.8
        ang = math.atan2(dy, dx)
        lump = 1.0 + 0.11 * math.sin(ang * 3.0 + 0.8) + 0.07 * math.cos(ang * 5.0)
        d = math.hypot(dx, dy) / lump
        if d > 1.0:
            return (0, 0, 0, 0)
        # Pixel shading: bright top-left highlight, darker bottom-right underside, dark 1px outline
        if d > 0.84:
            return _grey(138.0 + (_hash2(x, y, 601) - 0.5) * 18.0, 255)
        shade = 212.0 - (dx * 28.0 + dy * 38.0) + (_hash2(x, y, 607) - 0.5) * 28.0
        if dx < -0.15 and dy < -0.25 and d < 0.6:
            shade += 24.0  # wet sheen highlight
        return _grey(shade, 255)
    return _make_img(16, 16, px)


def make_player_mud_masks() -> tuple[Image.Image, Image.Image]:
    """Generate 64x64 standard Steve/Alex UV mud overlay masks (`low` = boots/legs, `full` = body, clean eyes)."""
    low = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    full = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    lpx, fpx = low.load(), full.load()

    def paint_rect(px_map, x0, y0, w, h, y_bias, seed):
        for y in range(y0, y0 + h):
            ty = (y - y0) / max(1.0, h - 1.0)  # 0 top -> 1 bottom of limb
            for x in range(x0, x0 + w):
                n = _tile_noise(x * 0.35, y * 0.35, 16, seed)
                score = ty + y_bias + (n - 0.5) * 0.55
                if score > 0.48:
                    a = min(1.0, (score - 0.48) / 0.18) * 235
                    lum = _clamp_u8(206 + (_hash2(x, y, seed + 5) - 0.5) * 36)
                    px_map[x, y] = (lum, lum, lum, _clamp_u8(a))

    # Right leg (0..16, 16..32) + Left leg (16..32, 48..64)
    for rx, ry in ((0, 20), (16, 52)):
        paint_rect(lpx, rx, ry, 16, 12, -0.02, 701)   # muddy boots + shins
        paint_rect(fpx, rx, ry, 16, 12, 0.45, 709)    # full muddy legs
    # Sole bottoms of legs
    for rx, ry in ((8, 16), (24, 48)):
        paint_rect(lpx, rx, ry, 4, 4, 0.8, 711)
        paint_rect(fpx, rx, ry, 4, 4, 0.8, 713)

    # Torso (16..40, 20..32) + Arms (40..56, 20..32) & (32..48, 52..64)
    paint_rect(lpx, 16, 20, 24, 12, -0.35, 721)       # slight belt/hem splashes on low coat
    paint_rect(fpx, 16, 20, 24, 12, 0.35, 723)        # heavy torso wallow coat
    for ax, ay in ((40, 20), (32, 52)):
        paint_rect(lpx, ax, ay, 16, 12, -0.25, 731)   # muddy cuffs on low coat
        paint_rect(fpx, ax, ay, 16, 12, 0.32, 733)    # muddy sleeves/hands on full coat

    # Head on full coat ONLY: light cheek/chin/back-of-head splatters, keeping eyes (y=11..13 on face x=8..16) clean!
    for y in range(8, 16):
        for x in range(0, 32):
            is_eye_band = (8 <= x < 16) and (10 <= y <= 13)
            if is_eye_band:
                continue
            n = _tile_noise(x * 0.4, y * 0.4, 16, 741)
            if n > 0.62:
                a = min(1.0, (n - 0.62) / 0.22) * 210
                lum = _clamp_u8(205 + (_hash2(x, y, 743) - 0.5) * 32)
                fpx[x, y] = (lum, lum, lum, _clamp_u8(a))

    return low, full


def _write_json(path: Path, obj: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def main():
    base = mod_paths.assets("savanna")
    tb = base / "textures/block"
    ti = base / "textures/item"
    te = base / "textures/entity"
    mb = base / "models/block"
    mi = base / "models/item"
    bs = base / "blockstates"
    for d in (tb, ti, te, mb, mi, bs):
        d.mkdir(parents=True, exist_ok=True)

    # 1. Block textures + soupy animation mcmeta
    make_soupy_strip().save(tb / "savanna_mud_soupy.png")
    _write_json(tb / "savanna_mud_soupy.png.mcmeta", {"animation": {"frametime": 8, "interpolate": True}})

    make_sticky(None).save(tb / "savanna_mud_sticky.png")
    for trk in ("hoof", "heavy", "paw", "reptile", "boot"):
        make_sticky(trk).save(tb / f"savanna_mud_sticky_{trk}.png")

    make_drying().save(tb / "savanna_mud_drying.png")
    make_cracked(False).save(tb / "savanna_mud_cracked.png")
    make_cracked(True).save(tb / "savanna_mud_cocoon.png")
    make_rub_overlay().save(tb / "savanna_mud_rub_overlay.png")

    # 2. Item texture & player coat masks
    make_mud_ball_item().save(ti / "savanna_mud_ball.png")
    p_low, p_full = make_player_mud_masks()
    p_low.save(te / "player_mud_low.png")
    p_full.save(te / "player_mud_full.png")

    # 3. Reusable tinted cube parent (tintindex: 0 on all 6 faces -> 1 texture set covers all 4 soil tones!)
    _write_json(mb / "tinted_cube_bottom_top.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#side"},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down":  {"texture": "#bottom", "cullface": "down",  "tintindex": 0},
                "up":    {"texture": "#top",    "cullface": "up",    "tintindex": 0},
                "north": {"texture": "#side",   "cullface": "north", "tintindex": 0},
                "south": {"texture": "#side",   "cullface": "south", "tintindex": 0},
                "west":  {"texture": "#side",   "cullface": "west",  "tintindex": 0},
                "east":  {"texture": "#side",   "cullface": "east",  "tintindex": 0},
            },
        }],
    })

    def mud_cube(name: str, top: str, side: str, bottom: str | None = None):
        _write_json(mb / f"{name}.json", {
            "parent": "antcolonies:block/tinted_cube_bottom_top",
            "textures": {
                "top": f"antcolonies:block/{top}",
                "side": f"antcolonies:block/{side}",
                "bottom": f"antcolonies:block/{bottom or side}",
            },
        })

    mud_cube("savanna_mud_cracked", "savanna_mud_cracked", "savanna_mud_cracked")
    mud_cube("savanna_mud_drying", "savanna_mud_drying", "savanna_mud_drying")
    mud_cube("savanna_mud_sticky", "savanna_mud_sticky", "savanna_mud_sticky")
    for trk in ("hoof", "heavy", "paw", "reptile", "boot"):
        mud_cube(f"savanna_mud_sticky_{trk}", f"savanna_mud_sticky_{trk}", "savanna_mud_sticky")
    mud_cube("savanna_mud_soupy_shallow", "savanna_mud_soupy", "savanna_mud_sticky")
    mud_cube("savanna_mud_soupy_mid", "savanna_mud_soupy", "savanna_mud_soupy", "savanna_mud_sticky")
    mud_cube("savanna_mud_soupy_deep", "savanna_mud_soupy", "savanna_mud_soupy")
    mud_cube("savanna_mud_cocoon", "savanna_mud_cocoon", "savanna_mud_cracked")

    # Rubbing-post thin decal model (faces north by default; rotate via blockstate y=0/90/180/270)
    _write_json(mb / "savanna_mud_rub_band.json", {
        "ambientocclusion": False,
        "textures": {"overlay": "antcolonies:block/savanna_mud_rub_overlay", "particle": "antcolonies:block/savanna_mud_sticky"},
        "elements": [{
            "from": [0, 0, 0.2],
            "to": [16, 16, 0.2],
            "shade": False,
            "faces": {
                "south": {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0},
            },
        }],
    })

    # 4. Blockstate JSON (multipart so `tone` property works automatically without 4x variant duplication!)
    def rot4(model_name: str):
        return [
            {"model": f"antcolonies:block/{model_name}"},
            {"model": f"antcolonies:block/{model_name}", "y": 90},
            {"model": f"antcolonies:block/{model_name}", "y": 180},
            {"model": f"antcolonies:block/{model_name}", "y": 270},
        ]

    _write_json(bs / "savanna_mud.json", {
        "multipart": [
            {"when": {"stage": "cracked"}, "apply": rot4("savanna_mud_cracked")},
            {"when": {"stage": "drying"}, "apply": rot4("savanna_mud_drying")},
            {"when": {"stage": "sticky"}, "apply": rot4("savanna_mud_sticky")},
            {"when": {"stage": "soupy_shallow"}, "apply": rot4("savanna_mud_soupy_shallow")},
            {"when": {"stage": "soupy_mid"}, "apply": rot4("savanna_mud_soupy_mid")},
            {"when": {"stage": "soupy_deep"}, "apply": rot4("savanna_mud_soupy_deep")},
        ],
    })

    # 5. Item models
    _write_json(mi / "savanna_mud.json", {"parent": "antcolonies:block/savanna_mud_sticky"})
    _write_json(mi / "savanna_mud_ball.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "antcolonies:item/savanna_mud_ball"},
    })

    print("generated savanna mud block, track, cocoon, rub-band, player coat, and mud-ball assets!")


if __name__ == "__main__":
    main()
