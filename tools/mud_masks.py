"""Auto-generates seamless 3D-projected mud coat masks for any GeckoLib animal model.

Reads `<animal>.geo.json` and `<animal>.png`, transforms every cube face into true
rest-pose 3D space, samples seamless 3D organic splatter/clot noise, excludes eyes,
pupils, mouth, tongue, tusks, whiskers and carried sockets, and writes two greyscale
alpha masks:
  - `<animal>_mud_low.png`  (wading coat: feet, legs, lower belly, tail underside)
  - `<animal>_mud_full.png` (full wallow coat: heavy mottled coverage, eyes/mouth clean)

At runtime `MudCoatLayer` draws the active mask via `RenderType.entityTranslucent`
tinted dynamically by regional soil colour (olive, Kenya red, golden, pale) and
wetness/dryness — zero hand-painting per animal and zero custom GLSL shaders.

Usage:
  python tools/mud_masks.py hippo crocodile bullfrog otter
  python tools/mud_masks.py --all --game   # export directly into mod textures/entity/
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from PIL import Image

import mod_paths

# Bone name substrings that never receive mud (keeps faces & expressive moving eyes readable).
CLEAN_BONE_KEYS = (
    "eye", "pupil", "iris", "sclera", "lid", "tongue", "mouth", "tooth", "teeth",
    "tusk", "whisker", "carry", "carried", "claw",
)
EYE_HALO_KEYS = ("eye", "pupil", "iris")

FACES = ("east", "west", "up", "down", "south", "north")


def _rot_zyx(rx_deg: float, ry_deg: float, rz_deg: float):
    """Bedrock/GeckoLib bone rotation matrix: x and z flipped, applied Z then Y then X."""
    rx = math.radians(-rx_deg)
    ry = math.radians(ry_deg)
    rz = math.radians(-rz_deg)
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    # R = Rx * Ry * Rz
    return (
        (cy * cz, -cy * sz, sy),
        (cx * sz + sx * sy * cz, cx * cz - sx * sy * sz, -sx * cy),
        (sx * sz - cx * sy * cz, sx * cz + cx * sy * sz, cx * cy),
    )


def _mat_mul3(a, b):
    return tuple(
        tuple(sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3))
        for r in range(3)
    )


def _mat_vec3(m, v):
    return (
        m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
        m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
        m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2],
    )


def _bone_transforms(bones):
    """Return {bone_name: (R3x3, t3)} mapping rest-model-space coords into posed rest space."""
    by_name = {b["name"]: b for b in bones}
    cache = {}
    I = ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))

    def get(name):
        if name in cache:
            return cache[name]
        b = by_name[name]
        p = b.get("pivot", [0.0, 0.0, 0.0])
        r = b.get("rotation", [0.0, 0.0, 0.0])
        R_loc = _rot_zyx(r[0], r[1], r[2]) if any(r) else I
        # Local transform around pivot p: v' = R_loc*(v - p) + p
        rp = _mat_vec3(R_loc, p)
        t_loc = (p[0] - rp[0], p[1] - rp[1], p[2] - rp[2])
        parent = b.get("parent")
        if parent and parent in by_name:
            Rp, tp = get(parent)
            R = _mat_mul3(Rp, R_loc)
            rt = _mat_vec3(Rp, t_loc)
            t = (tp[0] + rt[0], tp[1] + rt[1], tp[2] + rt[2])
        else:
            R, t = R_loc, t_loc
        cache[name] = (R, t)
        return R, t

    for b in bones:
        get(b["name"])
    return cache


def _hash3(ix: int, iy: int, iz: int, seed: int) -> float:
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 1274126177) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFFFF) / float(0x1000000)


def _noise3(x: float, y: float, z: float, seed: int) -> float:
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    ux = fx * fx * (3.0 - 2.0 * fx)
    uy = fy * fy * (3.0 - 2.0 * fy)
    uz = fz * fz * (3.0 - 2.0 * fz)
    c000 = _hash3(ix, iy, iz, seed)
    c100 = _hash3(ix + 1, iy, iz, seed)
    c010 = _hash3(ix, iy + 1, iz, seed)
    c110 = _hash3(ix + 1, iy + 1, iz, seed)
    c001 = _hash3(ix, iy, iz + 1, seed)
    c101 = _hash3(ix + 1, iy, iz + 1, seed)
    c011 = _hash3(ix, iy + 1, iz + 1, seed)
    c111 = _hash3(ix + 1, iy + 1, iz + 1, seed)
    x00 = c000 + (c100 - c000) * ux
    x10 = c010 + (c110 - c010) * ux
    x01 = c001 + (c101 - c001) * ux
    x11 = c011 + (c111 - c011) * ux
    y0 = x00 + (x10 - x00) * uy
    y1 = x01 + (x11 - x01) * uy
    return y0 + (y1 - y0) * uz


def _fbm3(x: float, y: float, z: float, seed: int) -> float:
    return (
        0.55 * _noise3(x, y, z, seed)
        + 0.30 * _noise3(x * 2.13, y * 2.13, z * 2.13, seed + 17)
        + 0.15 * _noise3(x * 4.7, y * 4.7, z * 4.7, seed + 53)
    )


def _is_clean_bone(bone_name: str) -> bool:
    low = bone_name.lower()
    return any(k in low for k in CLEAN_BONE_KEYS)


def _box_uv_faces(cube):
    """Normalize either per-face dict UV or Bedrock box UV [u, v] to {face: (u0, v0, uw, vh)}."""
    uv = cube.get("uv")
    if isinstance(uv, dict):
        out = {}
        for f in FACES:
            spec = uv.get(f)
            if spec:
                u0, v0 = spec["uv"]
                uw, vh = spec["uv_size"]
                out[f] = (float(u0), float(v0), float(uw), float(vh))
        return out
    if isinstance(uv, (list, tuple)) and len(uv) >= 2:
        u, v = float(uv[0]), float(uv[1])
        w, h, d = (math.floor(cube["size"][0]), math.floor(cube["size"][1]), math.floor(cube["size"][2]))
        return {
            "north": (u + d, v + d, w, h),
            "south": (u + d + w + d, v + d, w, h),
            "west": (u, v + d, d, h),
            "east": (u + d + w, v + d, d, h),
            "up": (u + d, v, w, d),
            "down": (u + d + w, v, w, d),
        }
    return {}


def _face_corners_local(ox: float, oy: float, oz: float, sx: float, sy: float, sz: float, face: str):
    """Return (origin3, du3, dv3, normal3) matching Three.js/GeckoLib BoxGeometry UV orientation."""
    x0, x1 = ox, ox + sx
    y0, y1 = oy, oy + sy
    z0, z1 = oz, oz + sz
    if face == "east":    # +X, u: z1 -> z0, v: y1 -> y0
        return (x1, y1, z1), (0, 0, -sz), (0, -sy, 0), (1, 0, 0)
    if face == "west":    # -X, u: z0 -> z1, v: y1 -> y0
        return (x0, y1, z0), (0, 0, sz), (0, -sy, 0), (-1, 0, 0)
    if face == "up":      # +Y, u: x0 -> x1, v: z0 -> z1
        return (x0, y1, z0), (sx, 0, 0), (0, 0, sz), (0, 1, 0)
    if face == "down":    # -Y, u: x0 -> x1, v: z1 -> z0
        return (x0, y0, z1), (sx, 0, 0), (0, 0, -sz), (0, -1, 0)
    if face == "south":   # +Z, u: x0 -> x1, v: y1 -> y0
        return (x0, y1, z1), (sx, 0, 0), (0, -sy, 0), (0, 0, 1)
    # north (-Z), u: x1 -> x0, v: y1 -> y0
    return (x1, y1, z0), (-sx, 0, 0), (0, -sy, 0), (0, 0, -1)


def generate_masks(model_name: str, out_dir: Path | None = None, game: bool = False) -> tuple[Path, Path]:
    """Generate `<model>_mud_low.png` and `<model>_mud_full.png` and return their paths."""
    base = mod_paths.model_assets(model_name)
    geo_path = base / f"geo/entity/{model_name}.geo.json"
    tex_path = base / f"textures/entity/{model_name}.png"
    geo = json.loads(geo_path.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    desc = geo["description"]
    tw, th = float(desc["texture_width"]), float(desc["texture_height"])
    bones = geo["bones"]
    transforms = _bone_transforms(bones)

    src_img = Image.open(tex_path).convert("RGBA")
    iw, ih = src_img.size
    sx_uv, sy_uv = iw / tw, ih / th
    src_px = src_img.load()

    # Pass 1: find true 3D bounding box, reserve UV pixels of clean bones, and locate eye sockets for clean halos.
    y_min, y_max = 1e9, -1e9
    eye_centers: list[tuple[float, float, float, float]] = []  # (cx, cy, cz, radius)
    reserved_uv: set[tuple[int, int]] = set()

    for b in bones:
        bname = b["name"]
        low_name = bname.lower()
        is_clean = _is_clean_bone(bname)
        is_eye = any(k in low_name for k in EYE_HALO_KEYS)
        R, t = transforms[bname]
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            c_rot = c.get("rotation")
            c_piv = c.get("pivot", [ox + sx / 2, oy + sy / 2, oz + sz / 2])
            Rc = _rot_zyx(*c_rot) if c_rot and any(c_rot) else None

            if is_clean:
                # Reserve every texel used by eyes, pupils, lids, tongue, mouth so mud can never bleed onto them.
                for _, (u0, v0, uw, vh) in _box_uv_faces(c).items():
                    if abs(uw) < 1e-4 or abs(vh) < 1e-4:
                        continue
                    x_a, x_b = u0 * sx_uv, (u0 + uw) * sx_uv
                    y_a, y_b = v0 * sy_uv, (v0 + vh) * sy_uv
                    px0 = max(0, math.floor(min(x_a, x_b)))
                    px1 = min(iw - 1, math.ceil(max(x_a, x_b)) - 1)
                    py0 = max(0, math.floor(min(y_a, y_b)))
                    py1 = min(ih - 1, math.ceil(max(y_a, y_b)) - 1)
                    for py in range(py0, py1 + 1):
                        for px in range(px0, px1 + 1):
                            reserved_uv.add((px, py))
                if is_eye:
                    mx, my, mz = ox + sx * 0.5, oy + sy * 0.5, oz + sz * 0.5
                    if Rc:
                        dv = _mat_vec3(Rc, (mx - c_piv[0], my - c_piv[1], mz - c_piv[2]))
                        mx, my, mz = c_piv[0] + dv[0], c_piv[1] + dv[1], c_piv[2] + dv[2]
                    wx, wy, wz = _mat_vec3(R, (mx, my, mz))
                    rad = max(sx, sy, sz) * 0.65
                    eye_centers.append((wx + t[0], wy + t[1], wz + t[2], max(rad, 1.2)))
                continue

            for dx in (0.0, sx):
                for dy in (0.0, sy):
                    for dz in (0.0, sz):
                        vx, vy, vz = ox + dx, oy + dy, oz + dz
                        if Rc:
                            dv = _mat_vec3(Rc, (vx - c_piv[0], vy - c_piv[1], vz - c_piv[2]))
                            vx, vy, vz = c_piv[0] + dv[0], c_piv[1] + dv[1], c_piv[2] + dv[2]
                        wx, wy, wz = _mat_vec3(R, (vx, vy, vz))
                        wy += t[1]
                        if wy < y_min:
                            y_min = wy
                        if wy > y_max:
                            y_max = wy

    height = max(y_max - y_min, 1.0)
    # Spatial noise scale normalized to body height so big hippos and tiny frogs get crisp clots.
    noise_scale = 3.2 / max(height * 0.25, 1.0)
    seed = sum(ord(ch) * (i + 1) for i, ch in enumerate(model_name))

    low_img = Image.new("RGBA", (iw, ih), (0, 0, 0, 0))
    full_img = Image.new("RGBA", (iw, ih), (0, 0, 0, 0))
    low_px = low_img.load()
    full_px = full_img.load()

    # Pass 2: rasterize each cube face into UV space using seamless 3D world coords.
    for b in bones:
        bname = b["name"]
        if _is_clean_bone(bname):
            continue
        is_head = any(k in bname.lower() for k in ("head", "snout", "jaw", "ear"))
        R, t = transforms[bname]
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            c_rot = c.get("rotation")
            c_piv = c.get("pivot", [ox + sx / 2, oy + sy / 2, oz + sz / 2])
            Rc = _rot_zyx(*c_rot) if c_rot and any(c_rot) else None
            faces = _box_uv_faces(c)
            for fname, (u0, v0, uw, vh) in faces.items():
                if abs(uw) < 1e-4 or abs(vh) < 1e-4:
                    continue
                p0, du, dv, nloc = _face_corners_local(ox, oy, oz, sx, sy, sz, fname)
                if Rc:
                    nloc = _mat_vec3(Rc, nloc)
                nw = _mat_vec3(R, nloc)
                ny = nw[1]  # +1 top, -1 underside, 0 side

                # Pixel bounds in image coordinates
                x_a, x_b = u0 * sx_uv, (u0 + uw) * sx_uv
                y_a, y_b = v0 * sy_uv, (v0 + vh) * sy_uv
                px0 = max(0, math.floor(min(x_a, x_b)))
                px1 = min(iw - 1, math.ceil(max(x_a, x_b)) - 1)
                py0 = max(0, math.floor(min(y_a, y_b)))
                py1 = min(ih - 1, math.ceil(max(y_a, y_b)) - 1)
                if px1 < px0 or py1 < py0:
                    continue

                for py in range(py0, py1 + 1):
                    tv = ((py + 0.5) - y_a) / (y_b - y_a) if abs(y_b - y_a) > 1e-5 else 0.5
                    tv = max(0.0, min(1.0, tv))
                    for px in range(px0, px1 + 1):
                        if (px, py) in reserved_uv or src_px[px, py][3] < 20:
                            continue
                        tu = ((px + 0.5) - x_a) / (x_b - x_a) if abs(x_b - x_a) > 1e-5 else 0.5
                        tu = max(0.0, min(1.0, tu))
                        lx = p0[0] + du[0] * tu + dv[0] * tv
                        ly = p0[1] + du[1] * tu + dv[1] * tv
                        lz = p0[2] + du[2] * tu + dv[2] * tv
                        if Rc:
                            rel = _mat_vec3(Rc, (lx - c_piv[0], ly - c_piv[1], lz - c_piv[2]))
                            lx, ly, lz = c_piv[0] + rel[0], c_piv[1] + rel[1], c_piv[2] + rel[2]
                        wx, wy, wz = _mat_vec3(R, (lx, ly, lz))
                        wx += t[0]
                        wy += t[1]
                        wz += t[2]

                        # Natural blink-wiped clean halo around expressive moving eyes/pupils
                        eye_fade = 1.0
                        if eye_centers:
                            for ex, ey, ez, erad in eye_centers:
                                d_eye = math.sqrt((wx - ex) ** 2 + (wy - ey) ** 2 + (wz - ez) ** 2)
                                if d_eye < erad * 1.15:
                                    eye_fade = 0.0
                                    break
                                if d_eye < erad * 2.15:
                                    f = (d_eye - erad * 1.15) / erad
                                    if f < eye_fade:
                                        eye_fade = f
                            if eye_fade <= 0.02:
                                continue

                        y_norm = (wy - y_min) / height  # 0 at feet, 1 at back/crown
                        n_macro = _fbm3(wx * noise_scale, wy * noise_scale * 1.15, wz * noise_scale, seed)
                        n_splat = _noise3(wx * noise_scale * 3.1, wy * noise_scale * 2.7, wz * noise_scale * 3.1, seed + 101)
                        n_grain = _noise3(wx * noise_scale * 6.5, wy * noise_scale * 6.5, wz * noise_scale * 6.5, seed + 211)
                        # Crackle ridge for authentic mud clots & drying lines
                        ridge = abs(n_splat - 0.5) * 2.0  # 0 near crack lines, 1 inside plates
                        lum = int(max(165, min(255, 195 + (n_grain - 0.5) * 44 + (ridge - 0.45) * 32)))

                        # --- LOW MASK: wading mud on feet, legs, lower belly & splash droplets ---
                        under_bonus = max(0.0, -ny) * 0.16
                        low_cutoff = 0.27 + under_bonus + (n_macro - 0.48) * 0.34
                        if is_head:
                            low_cutoff -= 0.16  # heads stay cleaner during shallow wading
                        low_margin = low_cutoff - y_norm
                        if low_margin > 0.0:
                            a_low = min(1.0, low_margin / 0.10) * (0.80 + 0.20 * n_grain)
                        elif low_margin > -0.14 and n_splat > 0.72:
                            # Organic mud splashes flicked slightly higher on legs/belly
                            a_low = ((n_splat - 0.72) / 0.28) * 0.78
                        else:
                            a_low = 0.0

                        a_low *= eye_fade
                        if a_low > 0.06:
                            alpha_i = int(max(0, min(255, round(a_low * 245))))
                            if alpha_i > low_px[px, py][3]:
                                low_px[px, py] = (lum, lum, lum, alpha_i)

                        # --- FULL MASK: heavy wallow coat with organic bare-skin patches ---
                        # Undersides & flanks get thick caked mud; top of back/head shows mottled patches
                        top_penalty = max(0.0, ny) * 0.15
                        head_penalty = 0.17 if is_head else 0.0
                        full_score = 0.88 - y_norm * 0.22 - top_penalty - head_penalty + (n_macro - 0.5) * 0.68
                        # Crisp organic edge transition
                        if full_score > 0.42:
                            a_full = min(1.0, (full_score - 0.42) / 0.14)
                            # Keep slight natural texture variation across thick mud
                            a_full *= (0.86 + 0.14 * ridge)
                        elif full_score > 0.30 and n_splat > 0.68:
                            a_full = ((n_splat - 0.68) / 0.32) * 0.75
                        else:
                            a_full = 0.0

                        a_full *= eye_fade
                        if a_full > 0.06:
                            alpha_i = int(max(0, min(255, round(a_full * 248))))
                            if alpha_i > full_px[px, py][3]:
                                full_px[px, py] = (lum, lum, lum, alpha_i)

    target_dir = (base / "textures/entity") if game else (out_dir or (mod_paths.PROJECT / "references/mud_preview"))
    target_dir.mkdir(parents=True, exist_ok=True)
    low_path = target_dir / f"{model_name}_mud_low.png"
    full_path = target_dir / f"{model_name}_mud_full.png"
    low_img.save(low_path)
    full_img.save(full_path)
    return low_path, full_path


def main():
    ap = argparse.ArgumentParser(description="Auto-generate 3D-projected mud coat masks for animal models.")
    ap.add_argument("models", nargs="*", help="Model names (e.g. hippo crocodile bullfrog otter)")
    ap.add_argument("--all", action="store_true", help="Generate masks for all savanna animal models")
    ap.add_argument("--game", action="store_true", help="Write directly into mod textures/entity/ instead of references/mud_preview/")
    args = ap.parse_args()
    names = args.models
    if args.all and not names:
        names = [
            "hippo", "crocodile", "crocodile_teen", "bullfrog", "monitor", "otter",
            "crab", "crab_male", "crab_female", "crab_berried",
        ]
    if not names:
        names = ["hippo", "crocodile", "bullfrog", "otter"]
    for name in names:
        low_p, full_p = generate_masks(name, game=args.game)
        print(f"generated {low_p.relative_to(mod_paths.PROJECT)} & {full_p.relative_to(mod_paths.PROJECT)}")


if __name__ == "__main__":
    main()
