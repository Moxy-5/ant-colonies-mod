"""Builds the self-contained Savanna Mud & Wallowing Studio 3D viewer.

Generates fresh mud masks via `tools/mud_masks.py` for Hippo, Nile Crocodile,
African Bullfrog, and African River Otter, packs their exact rest-pose GeckoLib
meshes as compact Float32 base64 + base64 PNG textures/masks, and writes:
  - `references/mud_viewer.html` (offline workshop copy)
  - optional `--artifact <path>` (live sidebar HTML artifact)

Usage:
  python tools/mud_viewer.py
  python tools/mud_viewer.py --artifact <path/to/savanna_mud_viewer.html>
"""
from __future__ import annotations

import argparse
import array
import base64
import json
import math
from pathlib import Path

import mod_paths
import mud_masks

MODELS = [
    ("hippo", "Hippo (v8 — 5.0b model)"),
    ("crocodile", "Nile Crocodile"),
    ("bullfrog", "African Bullfrog"),
    ("monitor", "Nile Monitor"),
    ("otter", "African River Otter (v6)"),
]

EXPORTED_ASSETS = [
    ("Soupy Wallow (4f anim)", "block/savanna_mud_soupy.png", 16, 16, 4, "wet"),
    ("Sticky Mud", "block/savanna_mud_sticky.png", 16, 16, 0, "wet"),
    ("Sticky + Hoof Track", "block/savanna_mud_sticky_hoof.png", 16, 16, 0, "wet"),
    ("Sticky + Heavy Pad", "block/savanna_mud_sticky_heavy.png", 16, 16, 0, "wet"),
    ("Sticky + Clawed Paw", "block/savanna_mud_sticky_paw.png", 16, 16, 0, "wet"),
    ("Sticky + Reptile Drag", "block/savanna_mud_sticky_reptile.png", 16, 16, 0, "wet"),
    ("Sticky + Player Boot", "block/savanna_mud_sticky_boot.png", 16, 16, 0, "wet"),
    ("Drying Rim Mud", "block/savanna_mud_drying.png", 16, 16, 0, "mid"),
    ("Cracked Hard Pan", "block/savanna_mud_cracked.png", 16, 16, 0, "dry"),
    ("Lungfish Cocoon Pore", "block/savanna_mud_cocoon.png", 16, 16, 0, "dry"),
    ("Rubbing Post Band", "block/savanna_mud_rub_overlay.png", 16, 16, 0, "mid"),
    ("Savanna Mud Ball Item", "item/savanna_mud_ball.png", 16, 16, 0, "wet"),
    ("Player Boots/Legs Mask", "entity/player_mud_low.png", 64, 64, 0, "wet"),
    ("Player Full Coat Mask", "entity/player_mud_full.png", 64, 64, 0, "mid"),
]

START_MARK = "/*__MUD_DATA__*/"
END_MARK = "/*__END_MUD_DATA__*/"


def _b64_png(path: Path) -> str:
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


def _f32_b64(vals: list[float]) -> str:
    buf = array.array("f", vals)
    return base64.b64encode(buf.tobytes()).decode("ascii")


def _bake_animal(name: str, label: str) -> dict:
    low_p, full_p = mud_masks.generate_masks(name, game=False)
    base = mod_paths.model_assets(name)
    geo = json.loads((base / f"geo/entity/{name}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    desc = geo["description"]
    tw, th = float(desc["texture_width"]), float(desc["texture_height"])
    bones = geo["bones"]
    transforms = mud_masks._bone_transforms(bones)

    verts_raw = []
    tris = []

    for b in bones:
        R, t = transforms[b["name"]]
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            sx, sy, sz = max(sx, 0.02), max(sy, 0.02), max(sz, 0.02)
            c_rot = c.get("rotation")
            c_piv = c.get("pivot", [ox + sx / 2, oy + sy / 2, oz + sz / 2])
            Rc = mud_masks._rot_zyx(*c_rot) if c_rot and any(c_rot) else None
            faces = mud_masks._box_uv_faces(c)

            for fname, (u0, v0, uw, vh) in faces.items():
                if abs(uw) < 1e-4 or abs(vh) < 1e-4:
                    continue
                p0, du, dv, nloc = mud_masks._face_corners_local(ox, oy, oz, sx, sy, sz, fname)
                if Rc:
                    nloc = mud_masks._mat_vec3(Rc, nloc)
                nw = mud_masks._mat_vec3(R, nloc)
                nlen = math.sqrt(nw[0] ** 2 + nw[1] ** 2 + nw[2] ** 2) or 1.0
                nw = (nw[0] / nlen, nw[1] / nlen, nw[2] / nlen)

                def xform(tu, tv):
                    lx = p0[0] + du[0] * tu + dv[0] * tv
                    ly = p0[1] + du[1] * tu + dv[1] * tv
                    lz = p0[2] + du[2] * tu + dv[2] * tv
                    if Rc:
                        rel = mud_masks._mat_vec3(Rc, (lx - c_piv[0], ly - c_piv[1], lz - c_piv[2]))
                        lx, ly, lz = c_piv[0] + rel[0], c_piv[1] + rel[1], c_piv[2] + rel[2]
                    wx, wy, wz = mud_masks._mat_vec3(R, (lx, ly, lz))
                    return ((wx + t[0]) / 16.0, (wy + t[1]) / 16.0, (wz + t[2]) / 16.0)

                q0, q1, q2, q3 = xform(0.0, 0.0), xform(1.0, 0.0), xform(1.0, 1.0), xform(0.0, 1.0)
                verts_raw.extend((q0, q1, q2, q3))

                ua, ub = u0 / tw, (u0 + uw) / tw
                va, vb = v0 / th, (v0 + vh) / th
                tris.append((q0, q1, q2, nw, (ua, va), (ub, va), (ub, vb)))
                tris.append((q0, q2, q3, nw, (ua, va), (ub, vb), (ua, vb)))

    xs = [v[0] for v in verts_raw]
    ys = [v[1] for v in verts_raw]
    zs = [v[2] for v in verts_raw]
    cx, cy, cz = (min(xs) + max(xs)) * 0.5, min(ys), (min(zs) + max(zs)) * 0.5
    height = max(ys) - min(ys)
    radius = max(max(xs) - min(xs), max(zs) - min(zs), height) * 0.5

    pos, norm, uv = [], [], []
    for v0, v1, v2, nw, uv0, uv1, uv2 in tris:
        for v, u in ((v0, uv0), (v1, uv1), (v2, uv2)):
            pos.extend((v[0] - cx, v[1] - cy, v[2] - cz))
            norm.extend(nw)
            uv.extend(u)

    return {
        "label": label,
        "radius": round(radius, 3),
        "height": round(height, 3),
        "pos_b64": _f32_b64(pos),
        "norm_b64": _f32_b64(norm),
        "uv_b64": _f32_b64(uv),
        "tex_base": _b64_png(base / f"textures/entity/{name}.png"),
        "tex_low": _b64_png(low_p),
        "tex_full": _b64_png(full_p),
    }


def inject(html_text: str, payload_json: str) -> str:
    i0 = html_text.find(START_MARK)
    i1 = html_text.find(END_MARK)
    if i0 < 0 or i1 < 0 or i1 < i0:
        raise ValueError("Missing /*__MUD_DATA__*/ markers in template HTML")
    return html_text[: i0 + len(START_MARK)] + payload_json + html_text[i1:]


def main():
    ap = argparse.ArgumentParser(description="Build Savanna Mud & Wallowing 3D Viewer.")
    ap.add_argument("--artifact", type=Path, default=None, help="Live HTML artifact path to write")
    args = ap.parse_args()

    template = (Path(__file__).resolve().parent / "mud_viewer.template.html").read_text(encoding="utf-8")
    animals = {}
    for name, label in MODELS:
        animals[name] = _bake_animal(name, label)
        print(f"baked {name}: h={animals[name]['height']}b, r={animals[name]['radius']}b")

    tex_root = mod_paths.assets("savanna") / "textures"
    assets = []
    for label, rel, w, h, frames, tint in EXPORTED_ASSETS:
        p = tex_root / rel
        if p.exists():
            assets.append({"label": label, "file": rel, "w": w, "h": h, "frames": frames, "tint": tint, "uri": _b64_png(p)})

    payload = json.dumps({"animals": animals, "assets": assets}, separators=(",", ":"))
    out_html = inject(template, payload)

    ref_out = mod_paths.PROJECT / "references/mud_viewer.html"
    ref_out.parent.mkdir(parents=True, exist_ok=True)
    ref_out.write_text(out_html, encoding="utf-8")
    print(f"wrote {ref_out.relative_to(mod_paths.PROJECT)} ({len(out_html) // 1024} KB)")

    if args.artifact:
        args.artifact.parent.mkdir(parents=True, exist_ok=True)
        args.artifact.write_text(out_html, encoding="utf-8")
        print(f"wrote artifact {args.artifact} ({len(out_html) // 1024} KB)")


if __name__ == "__main__":
    main()
