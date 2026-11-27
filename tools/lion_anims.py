"""African Lion Pride (Panthera leo) Bespoke Animation & BlockMoves Engine.

Parameterized directly by each pride member's measured bone pivots (Male Lion / Kalahari, Lioness, Lion Cub) so
every clip uses exact analytical IK (2-bone forelegs + 3-segment digitigrade hind legs), 2nd-order damped spring
physics (`secondary_lag`) for living mane & tufted tail inertia, living pupil micro-glances & blinks, and zero
ground/body/switch clipping.

Clip Suite (18 Core & Character Clips + 2 BlockMoves = 20 clips per pride member):
  Locomotion:  idle, walk, stalk, trot, gallop
  Action:      roar, bite, swipe, pounce
  Posture:     sit_down, sit, sit_up, rest_down, rest, rest_up
  Life:        groom, drink, death
  BlockMoves:  step_up_1000, step_down_1000
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (  # noqa: E402
    aim, clip, euler, mat_mul, mat_vec, rot, transpose, unit, vadd, vlen, vsub,
)
from motion_kit import (  # noqa: E402
    digitigrade_leg, ease, foot_at, on_path, path_note, pulse, quadruped_gait, ramp, secondary_lag, travel, two_bone,
)


def _clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


def _kscal(t, keys):
    """Smoothstep-eased scalar keyframe sampler: keys = ((t0, v0), (t1, v1), ...)."""
    if t <= keys[0][0]:
        return keys[0][1]
    for i in range(len(keys) - 1):
        t0, v0 = keys[i]
        t1, v1 = keys[i + 1]
        if t <= t1:
            u = ease((t - t0) / max(1e-6, t1 - t0))
            return v0 + (v1 - v0) * u
    return keys[-1][1]


def _kvec(t, keys):
    """Smoothstep-eased 3-vector keyframe sampler: keys = ((t0, [x,y,z]), ...)."""
    if t <= keys[0][0]:
        return list(keys[0][1])
    for i in range(len(keys) - 1):
        t0, v0 = keys[i]
        t1, v1 = keys[i + 1]
        if t <= t1:
            u = ease((t - t0) / max(1e-6, t1 - t0))
            return [v0[k] + (v1[k] - v0[k]) * u for k in range(3)]
    return list(keys[-1][1])


def _lerp_deg(a, b, w):
    """Shortest-arc component-wise Euler angle blend between a and b (prevents 360-deg IK<->FK flips)."""
    out = []
    for k in range(3):
        d = (b[k] - a[k] + 180.0) % 360.0 - 180.0
        out.append(a[k] + d * w)
    return out


def _unwrap(made):
    """Keep consecutive rotation keyframes within 180 deg so GeckoLib never interpolates the long way round."""
    for chans in made.get("bones", {}).values():
        keys = chans.get("rotation")
        if not keys:
            continue
        prev = None
        for tk in sorted(keys, key=float):
            v = list(keys[tk])
            if prev is not None:
                for i in range(3):
                    while v[i] - prev[i] > 180.0:
                        v[i] -= 360.0
                    while v[i] - prev[i] < -180.0:
                        v[i] += 360.0
            keys[tk] = [round(x, 3) for x in v]
            prev = v


class LionRig:
    """Exact pivot measurements & analytical FK/IK solver for any Lion Pride member (Male, Lioness, Cub)."""

    def __init__(self, raw_bones, kind="male"):
        self.kind = kind
        self.by_name = {b["name"]: b for b in raw_bones}
        self.has_mane = "mane_crown" in self.by_name
        self.piv = {b["name"]: list(b["pivot"]) for b in raw_bones}
        self.parent = {b["name"]: b.get("parent") for b in raw_bones}

        self.fore = {}
        for tag, side in (("fl", 1), ("fr", -1)):
            sh = self.piv[f"leg_{tag}_1"]
            el = self.piv[f"leg_{tag}_2"]
            wr = self.piv[f"leg_{tag}_paw"]
            self.fore[tag] = {
                "side": side,
                "sh": sh, "el": el, "wr": wr,
                "v1": vsub(el, sh), "v2": vsub(wr, el),
                "l1": vlen(vsub(el, sh)), "l2": vlen(vsub(wr, el)),
                "rest_y": wr[1],
            }
        self.hind = {}
        for tag, side in (("bl", 1), ("br", -1)):
            hp = self.piv[f"leg_{tag}_1"]
            kn = self.piv[f"leg_{tag}_2"]
            hk = self.piv[f"leg_{tag}_3"]
            an = self.piv[f"leg_{tag}_paw"]
            v3 = vsub(an, hk)
            rest_hock = math.degrees(math.atan2(-v3[2], max(0.01, -v3[1])))
            self.hind[tag] = {
                "side": side,
                "hp": hp, "kn": kn, "hk": hk, "an": an,
                "v1": vsub(kn, hp), "v2": vsub(hk, kn), "v3": v3,
                "l1": vlen(vsub(kn, hp)), "l2": vlen(vsub(hk, kn)), "l3": vlen(v3),
                "rest_hock": rest_hock,
                "rest_y": an[1],
            }
        self.sc = self.piv["chest"][1] / 19.0
        self.trunk_len = abs(self.fore["fl"]["sh"][2] - self.hind["bl"]["hp"][2])
        # Front-most cube edge below 16.5 px (what can meet a 1-block step face!)
        low_zs = [c["origin"][2] for b in raw_bones for c in b.get("cubes", []) if c["origin"][1] < 16.5]
        self.low_front_z = min(low_zs) if low_zs else -20.0

    def fk_world(self, pose, target_bones=("root", "body", "chest", "waist", "hips", "neck", "head")):
        ident = [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]
        mats, origs = {}, {}

        def eval_bone(name):
            if name in mats:
                return mats[name], origs[name]
            par = self.parent.get(name)
            mp, op = (ident, [0.0, 0.0, 0.0]) if par is None else eval_bone(par)
            ch = pose.get(name, {})
            r_deg = ch.get("rotation", [0.0, 0.0, 0.0])
            t_loc = ch.get("position", [0.0, 0.0, 0.0])
            rl = euler(r_deg)
            piv = self.piv[name]
            mw = mat_mul(mp, rl)
            shift_in_p = vadd(vadd(t_loc, piv), [-c for c in mat_vec(rl, piv)])
            ow = vadd(op, mat_vec(mp, shift_in_p))
            mats[name], origs[name] = mw, ow
            return mw, ow

        for b in target_bones:
            if b in self.by_name:
                eval_bone(b)
        return mats, origs

    def world_pt(self, mats, origs, bone, rest_pt):
        return vadd(origs[bone], mat_vec(mats[bone], rest_pt))

    def solve_legs(self, pose, targets):
        """Hybrid Planted-IK / Airborne-Direct-FK solver for 2-bone forelegs and 3-bone digitigrade hind legs.

        Each leg spec may provide:
          - Ground/world IK target: {"pos": [x,y,z], "toe": deg, "pole": [x,y,z], ...}
          - Direct FK override:     {"fk": (r1, r2, rp)} (or (r1, r2, r3, rp) for hind)
          - Smooth phase blend:     {"pos": ..., "fk": ..., "fk_blend": 0.0..1.0}
        """
        mats, origs = self.fk_world(pose, ("root", "body", "chest", "waist", "hips"))
        m_chest, m_hips = mats["chest"], mats["hips"]
        mt_chest, mt_hips = transpose(m_chest), transpose(m_hips)

        for tag in ("fl", "fr"):
            f = self.fore[tag]
            spec = targets.get(tag, {})
            fk = spec.get("fk")
            fb = spec.get("fk_blend", 1.0 if (fk is not None and "pos" not in spec) else 0.0)
            if fk is not None and fb >= 0.999:
                pose[f"leg_{tag}_1"] = {"rotation": list(fk[0])}
                pose[f"leg_{tag}_2"] = {"rotation": list(fk[1])}
                pose[f"leg_{tag}_paw"] = {"rotation": list(fk[2])}
                continue
            sh_w = self.world_pt(mats, origs, "chest", f["sh"])
            wr_w = list(spec.get("pos", f["wr"]))
            pole = spec.get("pole", [f["side"] * 0.28, 0.0, 1.0])
            el_w, wr_eff, _ = two_bone(sh_w, f["l1"], f["l2"], wr_w, pole, reach=0.985)
            r1 = rot(aim(f["v1"], mat_vec(mt_chest, vsub(el_w, sh_w))))
            m1_w = mat_mul(m_chest, euler(r1))
            r2 = rot(aim(f["v2"], mat_vec(transpose(m1_w), vsub(wr_eff, el_w))))
            m2_w = mat_mul(m1_w, euler(r2))
            if "paw_local" in spec:
                pl = spec["paw_local"]
                pb = spec.get("paw_blend", 1.0)
                if pb >= 0.999:
                    rp = list(pl)
                else:
                    paw_w = euler([spec.get("toe", 0.0), spec.get("paw_yaw", 0.0), spec.get("paw_roll", 0.0)])
                    rg = rot(mat_mul(transpose(m2_w), paw_w))
                    rp = _lerp_deg(rg, pl, pb)
            else:
                paw_w = euler([spec.get("toe", 0.0), spec.get("paw_yaw", 0.0), spec.get("paw_roll", 0.0)])
                rp = rot(mat_mul(transpose(m2_w), paw_w))
            if fk is not None and fb > 1e-4:
                r1 = _lerp_deg(r1, fk[0], fb)
                r2 = _lerp_deg(r2, fk[1], fb)
                rp = _lerp_deg(rp, fk[2], fb)
            pose[f"leg_{tag}_1"] = {"rotation": r1}
            pose[f"leg_{tag}_2"] = {"rotation": r2}
            pose[f"leg_{tag}_paw"] = {"rotation": rp}

        for tag in ("bl", "br"):
            h = self.hind[tag]
            spec = targets.get(tag, {})
            fk = spec.get("fk")
            fb = spec.get("fk_blend", 1.0 if (fk is not None and "pos" not in spec) else 0.0)
            if fk is not None and fb >= 0.999:
                pose[f"leg_{tag}_1"] = {"rotation": list(fk[0])}
                pose[f"leg_{tag}_2"] = {"rotation": list(fk[1])}
                pose[f"leg_{tag}_3"] = {"rotation": list(fk[2])}
                pose[f"leg_{tag}_paw"] = {"rotation": list(fk[3])}
                continue
            hp_w = self.world_pt(mats, origs, "hips", h["hp"])
            an_w = list(spec.get("pos", h["an"]))
            pole = spec.get("pole", [h["side"] * 0.22, 0.0, -1.0])
            hock_deg = spec.get("hock", h["rest_hock"])
            pts, _ = digitigrade_leg(hp_w, (h["l1"], h["l2"], h["l3"]), an_w, pole=pole,
                                     hock_deg=hock_deg, side_yaw_deg=spec.get("yaw", 0.0), reach=0.985)
            _, kn_w, hk_w, an_eff = pts
            r1 = rot(aim(h["v1"], mat_vec(mt_hips, vsub(kn_w, hp_w))))
            m1_w = mat_mul(m_hips, euler(r1))
            r2 = rot(aim(h["v2"], mat_vec(transpose(m1_w), vsub(hk_w, kn_w))))
            m2_w = mat_mul(m1_w, euler(r2))
            r3 = rot(aim(h["v3"], mat_vec(transpose(m2_w), vsub(an_eff, hk_w))))
            m3_w = mat_mul(m2_w, euler(r3))
            paw_w = euler([spec.get("toe", 0.0), spec.get("paw_yaw", 0.0), spec.get("paw_roll", 0.0)])
            rp = rot(mat_mul(transpose(m3_w), paw_w))
            if fk is not None and fb > 1e-4:
                r1 = _lerp_deg(r1, fk[0], fb)
                r2 = _lerp_deg(r2, fk[1], fb)
                r3 = _lerp_deg(r3, fk[2], fb)
                rp = _lerp_deg(rp, fk[3], fb)
            pose[f"leg_{tag}_1"] = {"rotation": r1}
            pose[f"leg_{tag}_2"] = {"rotation": r2}
            pose[f"leg_{tag}_3"] = {"rotation": r3}
            pose[f"leg_{tag}_paw"] = {"rotation": rp}

    def set_eyes(self, pose, blink=0.0, look_x=0.0, look_y=0.0, squint=0.0,
                 wide=0.0, brow_y=0.0, brow_tilt=0.0, lid_tilt=0.0, pupil_scale=1.0,
                 brow_asym=0.0, nose_sniff=0.0, nose_flare=0.0):
        """Full feline eye & nose expression rig:
          - Flush DUAL eyelids (`lid_left/right` upper + `ulid_left/right` lower cheek) meeting seamlessly in the
            middle of the eye at a crisp dark lash crease (zero floating window-wiper plate, zero rubber-band stretch!)
          - Articulated brows (`brow_left/right` height + inward/outward angle)
          - Real pupil gaze (`position`), dilation/constriction (`scale`), and clean socket-tuck behind closed lids
          - Expressive sniffing/flaring nose (`nose` upward wrinkle + nostril flare)
        """
        sc = self.sc
        b = _clamp(blink, 0.0, 1.0)
        sq = _clamp(squint, 0.0, 1.0)
        wd = _clamp(wide, 0.0, 1.0)
        is_c = (self.kind == "cub")

        # Upper lid drops 65% smoothly from behind the brow; lower lid (under_eye) rises 35% to meet it in the middle!
        up_close = _clamp(max(b, sq * 0.56), 0.0, 1.0)
        lo_close = _clamp(max(b, sq * 0.48), 0.0, 1.0)
        up_y = (0.12 * wd * (1.0 - up_close)) - ((0.84 if is_c else 0.78) * up_close)
        up_sy = 1.0 + (0.15 if is_c else 0.12) * up_close
        lo_y = 0.10 * lo_close
        lo_sy = 1.0 + (0.62 if is_c else 0.46) * lo_close
        lt = lid_tilt * (1.0 - b)

        pose["lid_left"] = {"position": [0.0, up_y, 0.0], "rotation": [0.0, 0.0, -lt], "scale": [1.0, up_sy, 1.0]}
        pose["lid_right"] = {"position": [0.0, up_y, 0.0], "rotation": [0.0, 0.0, lt], "scale": [1.0, up_sy, 1.0]}
        pose["ulid_left"] = {"position": [0.0, lo_y, 0.0], "rotation": [0.0, 0.0, 0.0], "scale": [1.0, lo_sy, 1.0]}
        pose["ulid_right"] = {"position": [0.0, lo_y, 0.0], "rotation": [0.0, 0.0, 0.0], "scale": [1.0, lo_sy, 1.0]}

        pose["brow_left"] = {
            "position": [0.0, (brow_y + brow_asym) * sc, 0.0],
            "rotation": [0.0, 0.0, -brow_tilt],
        }
        pose["brow_right"] = {
            "position": [0.0, (brow_y - brow_asym) * sc, 0.0],
            "rotation": [0.0, 0.0, brow_tilt],
        }
        px = _clamp(look_x, -0.32, 0.32) * sc
        py = _clamp(look_y, -0.18, 0.18) * sc
        # Slide each pupil ALONG its angled eye tangent plane (14 deg male, 11 deg lioness, 10 deg cub) so sideways gaze NEVER sinks behind the yellow iris!
        eye_tan = math.tan(math.radians(14.0 if self.kind == "male" else (11.0 if self.kind == "lioness" else 10.0)))
        tuck_z = 0.16 * sc * _clamp((b - 0.50) / 0.35, 0.0, 1.0)
        psx = _clamp(pupil_scale, 0.70, 1.55)
        psy = _clamp(1.0 + (psx - 1.0) * 0.55, 0.80, 1.32)
        pose["pupil_left"] = {"position": [px, py, px * eye_tan + tuck_z], "scale": [psx, psy, 1.0]}
        pose["pupil_right"] = {"position": [px, py, -px * eye_tan + tuck_z], "scale": [psx, psy, 1.0]}
        ns = _clamp(nose_sniff, 0.0, 1.2)
        nf = _clamp(nose_flare, 0.0, 1.2)
        pose["nose"] = {
            "position": [0.0, 0.14 * sc * ns, -0.06 * sc * ns],
            "rotation": [-12.0 * ns, 0.0, 0.0],
            "scale": [1.0 + 0.10 * nf, 1.0 - 0.07 * ns, 1.0 + 0.06 * nf],
        }

    def set_claws(self, pose, fl=0.0, fr=0.0, bl=0.0, br=0.0, hook=0.0):
        """Articulate locked V02 · Splaying Hunter toes & retractable 2-seg curved bone-ivory claws.
        At 0.0: compact toes & claws 100% hidden vertically inside the furry toe domes.
        At 1.0: ±13° toe splay + -14° knuckle arch + curved ivory sickles pivoting out along real phalanx arc!
        `hook=0.0` keeps tips safely clear of Y=0 when planted; `hook=1.0` adds deep predatory downward hook mid-air!
        """
        sc = self.sc
        hk = _clamp(hook, 0.0, 1.0)
        is_c = (self.kind == "cub")
        for tag, ext in (("fl", fl), ("fr", fr), ("bl", bl), ("br", br)):
            e = _clamp(ext, 0.0, 1.0)
            p_sc = sc * (0.88 if tag[0] == "b" else 1.0)
            e_sm = ease(e) if e > 0.0 else 0.0
            splay_deg = 10.5 if is_c else 13.0
            splay_dx = (0.20 if is_c else 0.28) * p_sc
            knuckle_arch = -11.0 if is_c else -14.0
            claw_rx = (60.0 + 12.0 * hk) if is_c else (64.0 + 14.0 * hk)
            claw_dy = (-0.01 - 0.08 * hk) * p_sc if is_c else (-0.02 - 0.12 * hk) * p_sc
            claw_dz = (-0.82 - 0.08 * hk) * p_sc if is_c else (-0.86 - 0.10 * hk) * p_sc
            claw_sy = (1.76 + 0.12 * hk) if is_c else (1.98 + 0.12 * hk)
            claw_sz = 1.12 if is_c else 1.20
            for i in range(4):
                frac = (i - 1.5) / 1.5
                toe_ry = -frac * splay_deg * e_sm
                toe_dx = frac * splay_dx * e_sm
                toe_rx = knuckle_arch * e_sm * (1.0 if i in (1, 2) else 0.78)
                toe_dy = 0.18 * p_sc * e_sm * (1.0 if i in (1, 2) else 0.65)
                pose[f"toe_{tag}_{i}"] = {
                    "rotation": [toe_rx, toe_ry, -frac * 3.5 * e_sm],
                    "position": [toe_dx, toe_dy, -0.10 * p_sc * e_sm * (1.0 if i in (1, 2) else 0.5)],
                }
                c_rx = (claw_rx - toe_rx * 0.42) * e_sm
                pose[f"claw_{tag}_{i}"] = {
                    "rotation": [c_rx, 0.0, 0.0],
                    "position": [0.0, claw_dy * e_sm, claw_dz * e_sm],
                    "scale": [1.0, 1.0 + (claw_sy - 1.0) * e_sm, 1.0 + (claw_sz - 1.0) * e_sm],
                }

    def apply_tail(self, pose, pitch_lift, yaw_arr, pitch_arr):
        """GeckoLib +Z tail: POSITIVE rx lifts tail UP off the ground (turn_x(-rx)*[0,0,1] = [0,+sin(rx),cos(rx)])."""
        weights = (0.42, 0.24, 0.16, 0.10, 0.08)
        for i in range(5):
            pose[f"tail_{i + 1}"] = {
                "rotation": [pitch_lift * weights[i] + pitch_arr[i], yaw_arr[i], 0.0]
            }

    def apply_mane(self, pose, crown_r, cheek_r, sh_r, chest_r, chest_t=None):
        if not self.has_mane:
            return
        pose["mane_crown"] = {"rotation": list(crown_r)}
        pose["mane_cheeks"] = {"rotation": list(cheek_r)}
        pose["mane_shoulders"] = {"rotation": list(sh_r)}
        mc = {"rotation": list(chest_r), "position": list(chest_t) if chest_t is not None else [0.0, 0.0, 0.0]}
        pose["mane_chest"] = mc


def _bake_clip(rig, length, steps, frame_fn, loop=True, tail_lag=0.09, mane_lag=0.08):
    """Bake frames with 2nd-order damped spring physics (`secondary_lag`) for tail_1..5 & mane_*."""
    is_loop = (loop is True)
    dt = length / steps
    n_eval = steps if is_loop else (steps + 1)
    frames = [frame_fn(i, i * dt) for i in range(n_eval)]
    poses = [f[0] for f in frames]
    tp_lift = [f[1] for f in frames]
    ty_drv = [f[2] for f in frames]
    tp_drv = [f[3] for f in frames]
    mane_drv = [f[4] for f in frames]

    ty_chain = [secondary_lag(ty_drv, dt, tail_lag * (0.7 + 0.35 * k), damping=0.74, loop=is_loop) for k in range(5)]
    tp_chain = [secondary_lag(tp_drv, dt, tail_lag * (0.7 + 0.32 * k), damping=0.78, loop=is_loop) for k in range(5)]

    m_crown = secondary_lag(mane_drv, dt, mane_lag * 0.75, damping=0.76, loop=is_loop)
    m_cheek = secondary_lag(mane_drv, dt, mane_lag * 0.95, damping=0.72, loop=is_loop)
    m_sh = secondary_lag(mane_drv, dt, mane_lag * 1.25, damping=0.78, loop=is_loop)
    m_chest = secondary_lag(mane_drv, dt, mane_lag * 1.45, damping=0.68, loop=is_loop)

    for i in range(n_eval):
        gains = (0.36, 0.52, 0.72, 0.92, 1.15)
        y_row = [ty_chain[k][i] * gains[k] for k in range(5)]
        p_row = [tp_chain[k][i] * gains[k] for k in range(5)]
        rig.apply_tail(poses[i], tp_lift[i], y_row, p_row)
        mc, mk, ms, mh = m_crown[i], m_cheek[i], m_sh[i], m_chest[i]
        m_ch_t = frames[i][5] if len(frames[i]) > 5 else None
        rig.apply_mane(
            poses[i],
            [mc[0] * 0.45, mc[1] * 0.35, mc[2] * 0.45],
            [mk[0] * 0.55, mk[1] * 0.55, mk[2] * 0.65],
            [ms[0] * 0.65, ms[1] * 0.40, ms[2] * 0.50],
            [mh[0] * 0.85, mh[1] * 0.45, mh[2] * 0.75],
            chest_t=m_ch_t,
        )

    def pose_at(t):
        idx = int(round(t / dt))
        if is_loop:
            idx %= steps
        else:
            idx = max(0, min(steps, idx))
        return poses[idx]

    made = clip(length, pose_at, steps, loop=loop)
    _unwrap(made)
    return made


# ====================================================================================================
# HOLDS MADE AGAINST THE PREY (the Motion Studio phase 3, step 3)
# ====================================================================================================
def _sample(made, t):
    """A baked clip's pose at time t (its keyframes, straight lines between them, as GeckoLib plays them)."""
    pose = {}
    for bone, chans in made["bones"].items():
        for ch, keys in chans.items():
            ts = sorted(keys, key=float)
            if t <= float(ts[0]):
                v = keys[ts[0]]
            elif t >= float(ts[-1]):
                v = keys[ts[-1]]
            else:
                for a, b in zip(ts, ts[1:]):
                    if float(a) <= t <= float(b):
                        u = (t - float(a)) / max(1e-9, float(b) - float(a))
                        v = [x + (y - x) * u for x, y in zip(keys[a], keys[b])]
                        break
            pose.setdefault(bone, {})[ch] = list(v)
    return pose


def _jaw(rig, pose):
    """Her jaw (the carry socket, on her head) in her model's frame."""
    mats, origs = rig.fk_world(pose, ("root", "body", "chest", "neck", "head", "carry"))
    return rig.world_pt(mats, origs, "carry", rig.piv["carry"])


_MADE_AGAINST = {}          # (holder, hold clip): the print of the buffalo's spots it is made from (tools/spots.py)


def _mark_made_against(made, holder):
    """Each clip made against the buffalo's spots says so (a "made_against buffalo <print>" note, its first timeline
    key): tools/spots.py --check (the build) then fails when the buffalo's table moves on and the clip is not made again
    (the new phase 2, idea 56, 2026-11-27)."""
    for name, clip in made.items():
        sign = _MADE_AGAINST.get((holder, name))
        if sign is not None:
            notes = clip.setdefault("timeline", {})
            notes["0.0"] = f"made_against buffalo {sign}" + (f"; {notes['0.0']}" if "0.0" in notes else "")
    return made


def _prey_heights(holder, hold_clip):
    """For the buffalo pair this hold is made for: the sizes, the prey's clips and the height of the spots it holds at
    every tick of each (the mean of those spots, in the holder's model px) and their spread (px)."""
    import json as _json
    import os as _os
    from mod_paths import model_assets  # noqa: E402  (where each model's files live)
    base = model_assets("buffalo")
    plan = _json.load(open(_os.path.join(base, "body_plans", "buffalo.json"), encoding="utf-8"))
    pair = next(p for p in plan["spots"]["pairs"] if p["holder"] == holder and p["hold"] == hold_clip)
    table = _json.load(open(_os.path.join(base, "spots", "buffalo.json"), encoding="utf-8"))["spots"]
    import spots as _spots                              # what the clip is made from, for the build's check
    _MADE_AGAINST[(holder, hold_clip)] = _spots.partner_print("buffalo", holder, hold_clip)
    names = table["names"]
    held = [names.index(n) for n in pair.get("spots", names)]
    scale = pair["size"] / pair["holder_size"]
    out = {}
    for clip in pair["clips"]:
        rows = table["clips"][clip]
        hs = [[row[3 * i + 1] * scale for i in held] for row in rows]
        out[clip] = ([sum(h) / len(h) for h in hs], max(max(h) - min(h) for h in hs))
    return pair, out


def _solve_jaw(rig, base, y, Z, guess, pitch=(-60.0, 70.0), shift=9.0):
    """Her neck's pitch and her body's shift ahead so her jaw is at height y and reach Z (Newton, two unknowns);
    base(pitch, shift) gives the pose. The limits: the pitch's range (degrees), the shift's (px at size 1, either way,
    or a (back, ahead) pair)."""
    lo, hi = (-shift, shift) if isinstance(shift, (int, float)) else shift
    npitch, dz = guess
    for _ in range(12):
        def at(n, d):
            j = _jaw(rig, base(n, d))
            return j[1] - y, j[2] - Z
        f0 = at(npitch, dz)
        if abs(f0[0]) < 0.005 and abs(f0[1]) < 0.005:
            break
        h = 0.05
        fa, fb = at(npitch + h, dz), at(npitch, dz + h)
        j11, j21 = (fa[0] - f0[0]) / h, (fa[1] - f0[1]) / h
        j12, j22 = (fb[0] - f0[0]) / h, (fb[1] - f0[1]) / h
        det = j11 * j22 - j12 * j21
        if abs(det) < 1e-9:
            break
        npitch -= (j22 * f0[0] - j12 * f0[1]) / det
        dz -= (-j21 * f0[0] + j11 * f0[1]) / det
        npitch, dz = _clamp(npitch, pitch[0], pitch[1]), _clamp(dz, lo * rig.sc, hi * rig.sc)
    return npitch, dz


def make_feeds(rig, holder, rest_frame):
    """feed for a lion form (the Motion Studio phase 3, step 5; docs/plans/motion-studio-phase3.md): lying
    at a carcass as it rests (its paws where its rest clip has them, so lying down with rest_down and getting up with
    rest_up fit either side), its jaw on the carcass's feeding spot: bitten in, tugged at with its head tilted (shearing
    with the side of its jaw), a piece torn free, its head lifted to chew, back in. Its neck's pitch is solved once, at
    its bite, so its jaw is at the spot's height (the buffalo's spot table at its first feed pair's sizes, a
    grown buffalo's carcass; a calf's feeding spots lie as high in the world, higher up its small body, so the one clip
    fits both: a lying lion cannot get its jaw down to a third of a calf's height); the game places it by its jaw at
    its bite's tick."""
    import clip_check as cc
    sc = rig.sc
    hits = cc.Animal(holder).plan_data["holds"]["clips"]
    lying = rest_frame(1.0, 0.0, False)[0]
    Z = _jaw(rig, lying)[2] - 1.0 * sc                 # a little stretched from its resting head: clear of its forepaws
    L, N = 3.0, 60
    made = {}
    for name in ("feed",):
        pair, heights = _prey_heights(holder, name)
        y = heights["carcass"][0][0]
        t_hit = hits[name] / 20.0
        state = {"at": None, "miss": 0.0}

        def frame(i, t, y=y, t_hit=t_hit, state=state):
            chew = pulse(t, 0.58 * L, 0.07 * L, 0.22 * L, 0.10 * L)     # 0 its jaws in the meat, 1 its head up chewing
            on = 1.0 - chew
            shake = on * math.sin(2.0 * math.pi * 2.0 * (t - t_hit))    # the tug (still at its bite: the game places it then)
            pull = on * (0.5 - 0.5 * math.cos(2.0 * math.pi * 2.0 * (t - t_hit)))
            chomp = chew * (0.5 - 0.5 * math.cos(2.0 * math.pi * 10.0 * t / L))
            out = {}

            def shape(p, targs):
                rx = p["body"]["rotation"][0]
                dy = p["body"]["position"][1]

                def base(npitch, dz, shake=shake, pull=pull, on=on, chew=chew):
                    p["body"] = {"position": [0.0, dy, dz], "rotation": [rx + 0.8 * pull, 0.7 * shake, 0.0]}
                    p["chest"] = {"rotation": [-1.5 + 1.2 * pull, 1.6 * shake, 1.0 * shake]}
                    p["neck"] = {"rotation": [npitch - 13.0 * chew + 2.0 * pull, 4.0 * shake, 6.0 * shake + 4.0 * on]}
                    p["head"] = {"rotation": [0.5 * npitch - 2.5 - 9.0 * chew, -3.0 * shake, 12.0 * shake + 9.0 * on]}
                    return p
                if state["at"] is None:
                    # solved once, at its bite (jaws in, the tug still), for its neck's pitch only: its body lies where
                    # its rest clip has it (its hind legs lie folded to their limit: a body shifted under them made
                    # them jump as it began to feed), so its jaw's reach is what that pitch gives (the game reads it
                    # from the clip, tools/spots.py)
                    still = lambda n, d: base(n, d, shake=0.0, pull=0.0, on=1.0, chew=0.0)
                    npitch, _ = _solve_jaw(rig, still, y, Z, (20.0, 0.0), pitch=(-40.0, 80.0), shift=(-6.0, 4.0))
                    state["at"] = (npitch, 0.0)
                    j = _jaw(rig, still(*state["at"]))
                    state["miss"], state["reach"] = abs(j[1] - y), -j[2]
                base(*state["at"])
                p["jaw"] = {"rotation": [7.0 * on - 3.0 * pull + 2.0 * chew + 10.0 * chomp, 0.0, 0.0]}
                p["tongue"] = {"position": [0.0, 0.0, 0.0], "rotation": [2.0 * chomp, 0.0, 0.0]}
                p["ear_left"] = {"rotation": [-22.0 * on - 8.0, -12.0 * on - 6.0, 8.0 * on - 8.0]}
                p["ear_right"] = {"rotation": [-22.0 * on - 8.0, 12.0 * on + 6.0, -8.0 * on + 8.0]}
                rig.set_eyes(p, squint=0.35 + 0.4 * on, blink=0.0)
                rig.set_claws(p, fl=0.7 * on, fr=0.7 * on, bl=0.0, br=0.0, hook=0.0)
                out["mane"] = [-20.0 - 4.0 * pull, -8.0 * shake, -6.0 * shake]
            got = rest_frame(1.0, 0.0, False, shape=shape)
            flick = math.sin(2.0 * math.pi * 2.0 * t / L)
            return got[0], got[1], 32.0 + 12.0 * flick, 3.0 * shake, out["mane"], got[5]
        frame(0, t_hit)                                 # (solves it at its bite before the bake)
        made[name] = _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.07, mane_lag=0.06)
        npitch, dz = state["at"]
        print(f"  {holder} {name}: jaw {y:.2f} px high at reach {state['reach']:.2f} (a carcass of size {pair['size']}), "
              f"neck about {npitch:.0f} deg, its height missed by {state['miss']:.3f} px")
    return _mark_made_against(made, holder)


def make_holds(rig, bite, rest_targets):
    """throat_hold, hold_sink and hold_down for the lioness (see the comment where they are called)."""
    import clip_check as cc                             # the blends and her bite's tick, read where the game reads them
    sc = rig.sc
    hits = cc.Animal("lioness").plan_data["holds"]["clips"]           # each hold's bite tick (her body plan's holds)
    bite_hit = hits["bite"]
    reach = _jaw(rig, _sample(bite, bite_hit / 20.0))
    Z = reach[2]                                        # her jaw's reach ahead at the bite: every hold keeps it
    stand_y = reach[1]

    def solve(base, y, guess):
        return _solve_jaw(rig, base, y, Z, guess)

    def hold_frame(t, y, low, shake, clamp_w, guess):
        """One frame of a hold: low 0 (standing, reaching) to 1 (crouched over prey on the ground); shake the tug of her
        head side to side (-1..1: under a pixel at her jaw, so her jaw stays on the hide); her jaw solved to height y
        at her bite's reach."""
        dy = -0.30 * sc - 2.6 * sc * low

        def base(npitch, dz):
            return {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, dy, dz], "rotation": [5.0 * low, 0.6 * shake, 1.2 * shake]},
                "chest": {"rotation": [3.0 + 4.0 * low, 0.8 * shake, 1.5 * shake]},
                "waist": {"rotation": [-2.0 - 3.0 * low, -1.2 * shake, -1.0 * shake]},
                "hips": {"rotation": [-1.5 - 3.0 * low, -1.0 * shake, 0.0]},
                "neck": {"rotation": [npitch, 1.0 * shake, 3.0 * shake]},
                "head": {"rotation": [0.45 * npitch - 6.0, -2.0 * shake, 7.0 * shake]},
            }
        npitch, dz = solve(base, y, guess)
        p = base(npitch, dz)
        gape = 7.0 - 3.0 * clamp_w
        p.update({
            "jaw": {"rotation": [gape, 0.0, 0.0]},
            "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [1.0, 0.0, 0.0]},
            "tongue_tip": {"rotation": [2.0, 0.0, 0.0]},
            "ear_left": {"rotation": [-32.0, -18.0, 14.0]},
            "ear_right": {"rotation": [-32.0, 18.0, -14.0]},
        })
        rig.set_eyes(p, squint=0.85, blink=0.0)
        targs = rest_targets(x_pad=1.2)                 # her paws stay put through every hold: only her body goes down
        for tag in ("fl", "fr"):
            targs[tag]["pos"][2] -= 0.8 * sc
            targs[tag]["pole"] = [rig.fore[tag]["side"] * 0.5, 0.1, 1.0]
        for tag in ("bl", "br"):
            targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + 10.0 + 10.0 * low, 12.0, 52.0)
        rig.solve_legs(p, targs)
        rig.set_claws(p, fl=0.85, fr=0.85, bl=0.5, br=0.5, hook=0.0)
        mane_drv = [-6.0 * low, -12.0 * shake, -8.0 * shake]
        return p, (npitch, dz), 20.0 + 6.0 * low, -18.0 * shake, mane_drv

    def lowness(y, y_down):
        return _clamp((stand_y - y) / max(1e-6, stand_y - y_down), 0.0, 1.0)

    made = {}
    report = []
    # the cow down first: its height sets how low "low" is
    pair_d, h_down = _prey_heights("lioness", "hold_down")
    y_down = sum(h_down["held_down"][0]) / len(h_down["held_down"][0])

    def bake(name, L, N, track, loop):
        state = {"guess": (0.0, 0.0)}
        t_hit = hits[name] / 20.0                       # her jaw centred at her bite: the game places her by it there

        def frame(i, t):
            y = track(t)
            shake = math.sin(2.0 * math.pi * 3.0 * (t - t_hit) / L) if loop else math.sin(2.0 * math.pi * 1.5 * (t - t_hit))
            clamp_w = 0.5 + 0.5 * math.sin(2.0 * math.pi * t / (L if loop else 1.0))
            p, state["guess"], tl, ty, md = hold_frame(t, y, lowness(y, y_down), shake, clamp_w, state["guess"])
            return p, tl, ty, 3.0, md
        return _bake_clip(rig, L, N, frame, loop=loop, tail_lag=0.06, mane_lag=0.05)

    # throat_hold: a standing cow's throat (1.9 s loop, one bite a time round)
    pair_t, h_throat = _prey_heights("lioness", "throat_hold")
    ys, spread = h_throat["idle"]
    y_throat = sum(ys) / len(ys)
    made["throat_hold"] = bake("throat_hold", 1.9, 38, lambda t: y_throat, True)
    report.append(f"throat_hold: jaw {y_throat:.2f} px high (her bite's {stand_y:.2f}), spots spread {spread:.2f}")
    # hold_down: a cow down (1.9 s loop)
    made["hold_down"] = bake("hold_down", 1.9, 38, lambda t: y_down, True)
    report.append(f"hold_down: jaw {y_down:.2f} px high, spots spread {h_down['held_down'][1]:.2f}")
    # hold_sink: on the cow's held_sink clock (her tick k is its tick k - 1), one tick longer so both end together
    pair_s, h_sink = _prey_heights("lioness", "hold_sink")
    track_s, spread_s = h_sink["held_sink"]
    lead = cc.Animal("buffalo").blend_ticks()[0] - cc.Animal("lioness").blend_ticks()[0]   # the cow's blend less hers
    n = len(track_s) - 1 + lead

    def sink_y(t):
        k = int(round(t * 20)) - lead
        return track_s[max(0, min(len(track_s) - 1, k))]
    made["hold_sink"] = bake("hold_sink", n / 20.0, n, sink_y, False)
    report.append(f"hold_sink: jaw {track_s[0]:.2f} down to {track_s[-1]:.2f} px over {n} ticks, spots spread {spread_s:.2f}")
    for line in report:
        print("  lioness " + line)
    return _mark_made_against(made, "lioness")


# ====================================================================================================
# ALL 20 BESPOKE LION PRIDE CLIPS (18 CORE/CHARACTER + 2 BLOCKMOVES)
# ====================================================================================================
def build_all_clips(raw_bones, kind="male"):
    rig = LionRig(raw_bones, kind)
    sc = rig.sc
    is_cub = kind == "cub"
    is_lns = kind == "lioness"
    clips = {}
    walk_speeds = {}

    def rest_targets(dy_add=0.0, x_pad=0.0):
        out = {}
        for tag in ("fl", "fr"):
            w = rig.fore[tag]["wr"]
            s = rig.fore[tag]["side"]
            out[tag] = {"pos": [w[0] + s * x_pad * sc, w[1] + dy_add, w[2]], "toe": 0.0}
        for tag in ("bl", "br"):
            a = rig.hind[tag]["an"]
            s = rig.hind[tag]["side"]
            out[tag] = {"pos": [a[0] + s * x_pad * sc, a[1] + dy_add, a[2]], "toe": 0.0}
        return out

    # 1. IDLE (3.6s loop) — Regal breathing, proud horizon scan, eye-tracking pupils, soft blinks, lazy tail flick
    def make_idle():
        L, N = 3.6, 36
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            breath = math.sin(ph)
            scan = math.sin(ph - 0.5)
            flick = pulse(t, 1.45, 0.18, 0.50, 0.30) * math.sin(2.0 * math.pi * (t - 1.45) * 2.2)
            ear_t = pulse(t, 2.35, 0.08, 0.14, 0.14)
            blink = pulse(t, 0.85, 0.07, 0.06, 0.10) + pulse(t, 2.80, 0.07, 0.05, 0.10)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, -0.18 * sc + 0.24 * sc * breath, 0.0], "rotation": [0.4 * breath, 0.0, 0.0]},
                "chest": {"rotation": [-1.2 * breath, 1.8 * scan, 0.0]},
                "waist": {"rotation": [0.6 * breath, -1.0 * scan, 0.0]},
                "hips": {"rotation": [-0.4 * breath, -0.6 * scan, 0.0]},
                "neck": {"rotation": [-1.4 * breath + 1.0 * math.sin(ph * 0.5), 4.2 * scan, 0.0]},
                "head": {"rotation": [1.2 * breath - 0.8 * math.sin(ph), 5.8 * scan, 1.6 * scan]},
                "jaw": {"rotation": [0.0, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
                "ear_left": {"rotation": [-2.0 + 9.0 * ear_t, -4.0 * scan - 12.0 * ear_t, 2.5 * breath]},
                "ear_right": {"rotation": [-2.0, 4.0 * scan, -2.5 * breath]},
            }
            sniff_i = pulse(t, 1.90, 0.10, 0.20, 0.14) * (0.5 - 0.5 * math.cos(2.0 * math.pi * (t - 1.90) * 4.2))
            rig.set_eyes(p, blink=min(1.0, blink), look_x=0.22 * scan, nose_sniff=0.45 * sniff_i, nose_flare=0.35 * sniff_i)
            rig.solve_legs(p, rest_targets())
            return p, 2.0, 7.0 * math.sin(ph) + 18.0 * flick, 2.0 * breath, [2.5 * breath, -3.5 * scan, 1.2 * scan]
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.10, mane_lag=0.09)
    clips["idle"] = make_idle()

    # Parameterized feline gaits (walk, stalk, trot, gallop)
    def make_gait(name, L, N, phases, duty, stride_len, lift_h, bounce, suspend_h, spine_flex,
                  body_drop=0.0, chest_p=0.0, neck_p=0.0, head_p=0.0, ear_pin=0.0, squint=0.0,
                  hock_stance=None, sway_yaw=3.2, sway_roll=2.2, tail_lift=0.0, tail_yaw_amp=10.0,
                  stance_x_pad=0.45):
        sl = stride_len * sc
        lh = lift_h * sc
        bnc = bounce * sc
        sus = suspend_h * sc
        bdrop = body_drop * sc
        walk_speeds[name] = round(sl / max(1e-6, duty * L), 3)

        def frame(i, t):
            u = (t / L) % 1.0
            ph = 2.0 * math.pi * u
            g = quadruped_gait(u, phases, duty, sl, lh, bounce=bnc, suspend_h=sus, spine_flex=spine_flex, cycle_s=L)
            by = -0.22 * sc + bdrop + g["body_y"]
            bp = g["body_pitch"]
            sp = g["spine_pitch"]
            sw_y = sway_yaw * math.sin(ph)
            sw_r = sway_roll * math.cos(ph)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, by, 0.0], "rotation": [bp, 0.0, 0.0]},
                "chest": {"rotation": [chest_p - sp * 0.55, sw_y, sw_r]},
                "waist": {"rotation": [sp * 0.90, -sw_y * 1.15, -sw_r * 0.5]},
                "hips": {"rotation": [-chest_p * 0.35 + sp * 0.55, -sw_y * 0.85, -sw_r]},
                "neck": {"rotation": [neck_p - bp * 0.55 + sp * 0.35, -sw_y * 0.55, -sw_r * 0.4]},
                "head": {"rotation": [head_p - bp * 0.45, -sw_y * 0.45, -sw_r * 0.3]},
                "jaw": {"rotation": [4.0 * max(0.0, math.sin(ph)) if name == "gallop" else 0.0, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
                "ear_left": {"rotation": [-ear_pin, -ear_pin * 0.45, ear_pin * 0.35]},
                "ear_right": {"rotation": [-ear_pin, ear_pin * 0.45, -ear_pin * 0.35]},
            }
            rig.set_eyes(p, blink=0.0, squint=squint)
            targs = {}
            for tag in ("fl", "fr"):
                fd = g["feet"][tag]
                w = rig.fore[tag]["wr"]
                s_sign = rig.fore[tag]["side"]
                toe = 0.0 if fd["planted"] else (22.0 * fd["lift"] * (0.4 - fd["swing"] * 0.8))
                targs[tag] = {
                    "pos": [w[0] + s_sign * stance_x_pad * sc, w[1] + fd["dy"], w[2] + fd["dz"]],
                    "toe": toe,
                    "pole": [s_sign * 0.50, 0.0, 1.0],
                }
            for tag in ("bl", "br"):
                fd = g["feet"][tag]
                a = rig.hind[tag]["an"]
                s_sign = rig.hind[tag]["side"]
                base_hk = rig.hind[tag]["rest_hock"] if hock_stance is None else hock_stance
                hk = _clamp(base_hk + fd["swing"] * 10.0 + fd["lift"] * 14.0, 8.0, 58.0)
                toe = 0.0 if fd["planted"] else (24.0 * fd["lift"] * (0.3 - fd["swing"] * 0.7))
                targs[tag] = {
                    "pos": [a[0] + s_sign * stance_x_pad * sc, a[1] + fd["dy"], a[2] + fd["dz"]],
                    "toe": toe,
                    "hock": hk,
                }
            rig.solve_legs(p, targs)
            if name == "gallop":
                # Smooth continuous sprint traction cleats (ramp out as paw lands & pushes off, retract cleanly into flight!)
                def _gclaw(ftag):
                    return 0.62 * _clamp(1.0 - g["feet"][ftag]["lift"] * 2.0, 0.0, 1.0)
                rig.set_claws(p, fl=_gclaw("fl"), fr=_gclaw("fr"), bl=_gclaw("bl"), br=_gclaw("br"), hook=0.0)
            mane_drv = [-bp * 0.9 - (8.0 if name == "gallop" else 0.0) - g["body_y"] * 2.2, -sw_y * 1.4, -sw_r * 1.8]
            tail_p_drv = -sp * 0.45 - bp * 0.35
            tail_y_drv = tail_yaw_amp * math.sin(ph)
            return p, tail_lift, tail_y_drv, tail_p_drv, mane_drv

        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.075, mane_lag=0.065)

    # 2. WALK — Regal patrol lateral-sequence stride (perkier bouncy tail for Cub!)
    walk_L = 0.94 if is_cub else (1.22 if is_lns else 1.35)
    clips["walk"] = make_gait(
        "walk", walk_L, 32,
        phases={"bl": 0.0, "fl": 0.25, "br": 0.50, "fr": 0.75},
        duty=0.66, stride_len=17.5, lift_h=3.8 if is_cub else 3.4, bounce=0.72 if is_cub else 0.45,
        suspend_h=0.0, spine_flex=5.2 if is_cub else 4.5,
        body_drop=-0.25, chest_p=0.5, neck_p=1.5, head_p=-2.0, ear_pin=0.0 if is_cub else 2.0,
        sway_yaw=3.4 if is_cub else 3.0, sway_roll=2.4 if is_cub else 2.0,
        tail_lift=24.0 if is_cub else 8.0, tail_yaw_amp=16.0 if is_cub else 11.0, stance_x_pad=0.85,
    )

    # 3. STALK — Razor-focused crouched ambush slink (extra-low silky hunter for Lioness; playful wide-eyed stalk for Cub!)
    stalk_L = 1.20 if is_cub else (1.55 if is_lns else 1.70)
    clips["stalk"] = make_gait(
        "stalk", stalk_L, 36,
        phases={"bl": 0.0, "fl": 0.25, "br": 0.50, "fr": 0.75},
        duty=0.72, stride_len=14.5, lift_h=2.2, bounce=0.22 if is_cub else 0.18, suspend_h=0.0, spine_flex=3.0,
        body_drop=-4.9 if is_lns else -4.6, chest_p=7.5, neck_p=11.0, head_p=-17.5,
        ear_pin=8.0 if is_cub else 26.0, squint=0.15 if is_cub else 0.75,
        hock_stance=44.0, sway_yaw=4.5 if is_cub else 4.0, sway_roll=1.8,
        tail_lift=30.0 if is_cub else 24.0, tail_yaw_amp=18.0 if is_cub else 14.0,
        stance_x_pad=1.10,
    )

    # 4. TROT — Brisk diagonal savanna patrol trot (bouncy high-tail cub trot!)
    trot_L = 0.62 if is_cub else (0.76 if is_lns else 0.85)
    clips["trot"] = make_gait(
        "trot", trot_L, 24,
        phases={"fl": 0.0, "br": 0.02, "fr": 0.50, "bl": 0.52},
        duty=0.54, stride_len=21.0, lift_h=4.9 if is_cub else 4.6, bounce=1.15 if is_cub else 0.85,
        suspend_h=0.5 if is_cub else 0.4, spine_flex=6.5 if is_cub else 6.0,
        body_drop=-0.45, chest_p=1.5, neck_p=3.5, head_p=-4.5, ear_pin=2.0 if is_cub else 6.0,
        sway_yaw=2.2, sway_roll=2.6, tail_lift=32.0 if is_cub else 14.0, tail_yaw_amp=12.0 if is_cub else 7.0,
        stance_x_pad=0.65,
    )

    # 5. GALLOP — Explosive rotary big-cat gallop with sprint traction cleats & streaming tail!
    gal_L = 0.60 if is_cub else (0.60 if is_lns else 0.66)
    clips["gallop"] = make_gait(
        "gallop", gal_L, 28,
        phases={"bl": 0.0, "br": 0.14, "fl": 0.48, "fr": 0.64},
        duty=0.38, stride_len=24.5, lift_h=5.6, bounce=1.1 if is_cub else 1.0, suspend_h=3.2, spine_flex=13.5,
        body_drop=-0.6, chest_p=2.0, neck_p=6.0, head_p=-7.0, ear_pin=12.0 if is_cub else 28.0,
        squint=0.15 if is_cub else 0.45,
        hock_stance=20.0, sway_yaw=1.4, sway_roll=1.6, tail_lift=44.0 if is_cub else 32.0,
        tail_yaw_amp=8.0 if is_cub else 4.0, stance_x_pad=0.55,
    )

    # 6. ROAR (Male 3.40s 2-wave patrol bellow; Lioness 2.50s sharper Pride Contact Call) / CUB_CALL (Cub 1.60s squeaky mew!)
    def make_roar():
        if is_lns:
            L, N = 2.50, 50
            def frame(i, t):
                gather = pulse(t, 0.04, 0.30, 0.06, 0.18)
                w1_flank = pulse(t, 0.32, 0.28, 0.46, 0.38)
                w1_chest = pulse(t, 0.40, 0.30, 0.50, 0.40)
                w1_head = pulse(t, 0.48, 0.30, 0.54, 0.42)
                w2_head = pulse(t, 1.56, 0.18, 0.18, 0.32)
                bellow = _clamp(w1_head + 0.52 * w2_head, 0.0, 1.0)
                jaw_deg = _clamp(44.0 * w1_head + 24.0 * w2_head, 0.0, 46.0)
                dy = -0.25 * sc - 0.85 * sc * gather - 0.55 * sc * w1_chest
                dz = 0.55 * sc * gather - 0.95 * sc * w1_chest
                p = {
                    "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                    "body": {"position": [0.0, dy, dz], "rotation": [1.5 * gather - 1.6 * w1_chest, 0.0, 0.0]},
                    "chest": {"rotation": [3.5 * gather - 5.5 * w1_chest, 0.0, 0.0]},
                    "waist": {"rotation": [-2.2 * gather + 4.2 * w1_flank, 0.0, 0.0]},
                    "hips": {"rotation": [1.2 * gather - 1.8 * w1_flank, 0.0, 0.0]},
                    "neck": {"rotation": [5.0 * gather - 6.5 * w1_head + 2.0 * w2_head, 0.0, 0.0]},
                    "head": {"rotation": [3.0 * gather - 8.5 * w1_head - 4.0 * w2_head, 0.0, 0.0]},
                    "jaw": {"rotation": [jaw_deg, 0.0, 0.0]},
                    "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [5.0 * bellow, 0.0, 0.0]},
                    "tongue_tip": {"rotation": [10.0 * bellow, 0.0, 0.0]},
                    "ear_left": {"rotation": [-22.0 * bellow, -14.0 * bellow, 10.0 * bellow]},
                    "ear_right": {"rotation": [-22.0 * bellow, 14.0 * bellow, -10.0 * bellow]},
                }
                rig.set_eyes(p, blink=pulse(t, 2.08, 0.07, 0.06, 0.10), squint=0.55 * bellow)
                rig.solve_legs(p, rest_targets(x_pad=0.30))
                rig.set_claws(p, fl=0.36 * bellow, fr=0.36 * bellow, hook=0.0)
                tail_y = 14.0 * w1_flank * math.sin(math.pi * _clamp((t - 0.30) / 1.2, 0.0, 1.0))
                return p, 16.0 * bellow, tail_y, 2.5 * w1_flank, [0.0, 0.0, 0.0]
            return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.08, mane_lag=0.08)

        L, N = 3.40, 68
        def frame(i, t):
            # Phase 1: deep lung gather
            gather = pulse(t, 0.04, 0.42, 0.08, 0.24)
            # Phase 2: slow rolling primary diaphragm wave (flanks -> chest -> neck/head megaphone)
            w1_flank = pulse(t, 0.44, 0.34, 0.50, 0.44)
            w1_chest = pulse(t, 0.56, 0.36, 0.56, 0.46)
            w1_head = pulse(t, 0.68, 0.38, 0.62, 0.46)
            # Phase 3: secondary trailing heavy bellow / grunt wave (classic real-lion cadence)
            w2_flank = pulse(t, 1.88, 0.24, 0.24, 0.36)
            w2_chest = pulse(t, 1.98, 0.26, 0.26, 0.36)
            w2_head = pulse(t, 2.08, 0.28, 0.28, 0.40)

            bellow = _clamp(w1_head + 0.68 * w2_head, 0.0, 1.0)
            jaw_deg = _clamp(52.0 * w1_head + 34.0 * w2_head, 0.0, 54.0)
            # All 4 paws stay firmly locked at y=0.00 while the chest/belly roll forward & down into the roar!
            dy = -0.25 * sc - 1.25 * sc * gather - 0.85 * sc * w1_chest - 0.65 * sc * w2_chest
            dz = 0.75 * sc * gather - 1.35 * sc * w1_chest - 0.85 * sc * w2_chest
            body_rx = 2.0 * gather - 1.8 * w1_chest + 1.4 * w2_chest
            chest_rx = 4.5 * gather - 6.5 * w1_chest - 2.5 * w2_chest
            waist_rx = -3.0 * gather + 5.5 * w1_flank + 4.5 * w2_flank
            hips_rx = 1.6 * gather - 2.4 * w1_flank - 2.0 * w2_flank
            # Megaphone throat extension: neck thrusts smoothly forward-level, head stares fiercely forward on wave 1 & nods heavy on wave 2
            neck_rx = 7.5 * gather - 3.5 * w1_head + 5.5 * w2_head
            head_rx = 4.5 * gather - 5.5 * w1_head - 3.5 * w2_head
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, dy, dz], "rotation": [body_rx, 0.0, 0.0]},
                "chest": {"rotation": [chest_rx, 0.0, 0.0]},
                "waist": {"rotation": [waist_rx, 0.0, 0.0]},
                "hips": {"rotation": [hips_rx, 0.0, 0.0]},
                "neck": {"rotation": [neck_rx, 0.0, 0.0]},
                "head": {"rotation": [head_rx, 0.0, 0.0]},
                "jaw": {"rotation": [jaw_deg, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [6.0 * bellow, 0.0, 0.0]},
                "tongue_tip": {"rotation": [12.0 * bellow, 0.0, 0.0]},
                "ear_left": {"rotation": [-30.0 * bellow, -20.0 * bellow, 16.0 * bellow]},
                "ear_right": {"rotation": [-30.0 * bellow, 20.0 * bellow, -16.0 * bellow]},
            }
            rig.set_eyes(p, blink=pulse(t, 2.92, 0.08, 0.08, 0.12), squint=0.72 * bellow)
            rig.solve_legs(p, rest_targets(x_pad=0.35))
            # Powerful front-paw brace spread during the diaphragm bellow peak, relaxing back inside sheaths at rest!
            rig.set_claws(p, fl=0.48 * bellow, fr=0.48 * bellow, hook=0.0)
            # Slow heavy authoritative tail sweep (zero jitter)
            tail_y = 18.0 * w1_flank * math.sin(math.pi * _clamp((t - 0.45) / 1.4, 0.0, 1.0)) - 14.0 * w2_flank
            mane_drv = [-11.0 * w1_chest - 6.5 * w2_chest + 4.0 * gather, 0.0, 0.0]
            return p, 14.0 * bellow, tail_y, 3.0 * w1_flank + 2.5 * w2_flank, mane_drv
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.09, mane_lag=0.085)

    def make_cub_call():
        """Adorable squeaky 'mrr-row!' cub contact call (replaces territorial male roar on Cub!)."""
        L, N = 1.60, 36
        def frame(i, t):
            mew = pulse(t, 0.16, 0.24, 0.44, 0.34)
            chirp = pulse(t, 0.96, 0.14, 0.14, 0.24)
            call = _clamp(mew + 0.65 * chirp, 0.0, 1.0)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, -0.15 * sc + 0.45 * sc * call, -0.35 * sc * call], "rotation": [-3.5 * call, 0.0, 0.0]},
                "chest": {"rotation": [-5.0 * call, 0.0, 0.0]},
                "waist": {"rotation": [2.5 * call, 0.0, 0.0]},
                "hips": {"rotation": [2.0 * call, 0.0, 0.0]},
                "neck": {"rotation": [-12.0 * call, 0.0, 0.0]},
                "head": {"rotation": [-16.0 * mew - 10.0 * chirp, 0.0, 4.0 * mew]},
                "jaw": {"rotation": [34.0 * mew + 22.0 * chirp, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [5.0 * call, 0.0, 0.0]},
                "tongue_tip": {"rotation": [10.0 * call, 0.0, 0.0]},
                "ear_left": {"rotation": [12.0 * call, 14.0 * call, 8.0 * call]},
                "ear_right": {"rotation": [12.0 * call, -14.0 * call, -8.0 * call]},
            }
            rig.set_eyes(p, wide=0.85 * call, pupil_scale=1.22, brow_y=0.20 * call, look_y=0.10 * call)
            rig.solve_legs(p, rest_targets(x_pad=0.25))
            wag = 18.0 * call * math.sin(2.0 * math.pi * t * 2.5)
            return p, 42.0 * call + 14.0, wag, 3.0 * call, [0.0, 0.0, 0.0]
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.07, mane_lag=0.07)

    if is_cub:
        clips["cub_call"] = make_cub_call()
    else:
        clips["roar"] = make_roar()

    # 7. BITE (0.75s) — Spring coil -> explosive fang lunge -> CRUSH SNAP at tick 7-8 (0.38s)!
    def make_bite():
        L, N = 0.75, 24
        def frame(i, t):
            coil = pulse(t, 0.0, 0.13, 0.03, 0.08)
            lunge = pulse(t, 0.15, 0.14, 0.16, 0.24)
            gape = ramp(t, ((0.0, 0.0), (0.14, 14.0), (0.29, 46.0), (0.38, 3.0), (0.50, 0.0), (L, 0.0)))
            snap = pulse(t, 0.36, 0.04, 0.04, 0.12)
            gf = gape / 46.0
            dy = -0.25 * sc - 2.0 * sc * coil - 0.9 * sc * lunge
            dz = 2.0 * sc * coil - 3.0 * sc * lunge
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, dy, dz], "rotation": [-2.2 * coil + 2.8 * lunge, 0.0, 0.0]},
                "chest": {"rotation": [4.0 * coil - 3.5 * gf + 4.5 * snap, 0.0, 0.0]},
                "waist": {"rotation": [-3.0 * coil + 2.0 * lunge, 0.0, 0.0]},
                "hips": {"rotation": [-2.0 * coil + 1.5 * lunge, 0.0, 0.0]},
                "neck": {"rotation": [-8.0 * coil + 11.0 * lunge + 5.0 * snap, 0.0, 0.0]},
                "head": {"rotation": [-10.0 * coil - 18.0 * gf + 9.0 * snap, 0.0, 0.0]},
                "jaw": {"rotation": [gape, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [4.0 * gf, 0.0, 0.0]},
                "tongue_tip": {"rotation": [10.0 * gf, 0.0, 0.0]},
                "ear_left": {"rotation": ([10.0 * lunge, 10.0 * lunge, 6.0 * lunge] if is_cub else [-34.0 * lunge, -18.0 * lunge, 14.0 * lunge])},
                "ear_right": {"rotation": ([10.0 * lunge, -10.0 * lunge, -6.0 * lunge] if is_cub else [-34.0 * lunge, 18.0 * lunge, -14.0 * lunge])},
            }
            if is_cub:
                rig.set_eyes(p, blink=snap * 0.5, wide=0.72 * lunge, pupil_scale=1.24)
            else:
                rig.set_eyes(p, blink=snap * 0.7, squint=0.8 * lunge)
            targs = rest_targets(x_pad=0.65)
            for tag in ("fl", "fr"):
                targs[tag]["pole"] = [rig.fore[tag]["side"] * 0.42, 0.0, 1.0]
            rig.solve_legs(p, targs)
            rig.set_claws(p, fl=(0.24 if is_cub else 0.58) * lunge, fr=(0.24 if is_cub else 0.58) * lunge, hook=0.0)
            mane_drv = [-10.0 * lunge + 12.0 * snap, 0.0, 0.0]
            return p, 16.0 * lunge, 22.0 * lunge * math.sin(2.0 * math.pi * t * 3.5), 4.0 * snap, mane_drv
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.05, mane_lag=0.045)
    clips["bite"] = make_bite()

    # 8a/8b/8c. DIRECT-FK REAL-LION SWIPE ARSENAL (3 Approved Styles — Planted 3 Legs IK, Striking Arm Pure FK!)
    def make_fk_swipe(L, N, keys):
        def frame(i, t):
            bT_raw = _kvec(t, keys["bodyT"])
            bT = [bT_raw[0] * sc, bT_raw[1] * sc, bT_raw[2] * sc]
            bR = _kvec(t, keys["bodyR"])
            cR = _kvec(t, keys["chestR"])
            nR = _kvec(t, keys["neckR"])
            hR = _kvec(t, keys["headR"])
            jw = _kscal(t, keys["jaw"])
            pin = _clamp(jw / 30.0, 0.0, 1.0)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": bT, "rotation": bR},
                "chest": {"rotation": cR},
                "waist": {"rotation": [-bR[0] * 0.30, -cR[1] * 0.45, -cR[2] * 0.35]},
                "hips": {"rotation": [-bR[0] * 0.25, -cR[1] * 0.25, 0.0]},
                "neck": {"rotation": nR},
                "head": {"rotation": hR},
                "jaw": {"rotation": [jw * (0.35 if is_cub else 1.0), 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [jw * (0.04 if is_cub else 0.16), 0.0, 0.0]},
                "tongue_tip": {"rotation": [jw * (0.08 if is_cub else 0.30), 0.0, 0.0]},
                "ear_left": {"rotation": ([10.0 * pin, 12.0 * pin, 6.0 * pin] if is_cub else [-30.0 * pin, -18.0 * pin, 14.0 * pin])},
                "ear_right": {"rotation": ([10.0 * pin, -12.0 * pin, -6.0 * pin] if is_cub else [-30.0 * pin, 18.0 * pin, -14.0 * pin])},
            }
            if is_cub:
                # Playful Cub Paw-Bat: curious wide dilated eyes, perky ears & soft 35% playful milk-claw flick!
                rig.set_eyes(p, wide=0.82 * pin, pupil_scale=1.28, brow_y=0.18 * pin)
            else:
                rig.set_eyes(p, squint=0.78 * pin)
            targs = rest_targets(x_pad=0.35)
            # Striking right foreleg stays on pure Direct FK throughout with an anatomical elbow-tuck recovery (0.00 px clip!)
            fb = _kscal(t, ((0.0, 0.0), (0.04, 1.0), (L - 0.05, 1.0), (L, 0.0)))
            targs["fr"] = {
                "pos": list(targs["fr"]["pos"]),
                "fk": (_kvec(t, keys["fr1"]), _kvec(t, keys["fr2"]), _kvec(t, keys["frP"])),
                "fk_blend": fb,
            }
            rig.solve_legs(p, targs)
            # Striking right paw stays sheathed during early lift, SNAPS OUT across the swipe arc (35% playful flick on Cub; 100% lethal on Adults!), retracts cleanly!
            c_peak = 0.35 if is_cub else 1.0
            fr_claw = c_peak * _kscal(t, ((0.0, 0.0), (0.14, 0.0), (0.24, 1.0), (0.64, 1.0), (0.76, 0.0), (L, 0.0)))
            fl_brace = (0.0 if is_cub else keys.get("fl_brace", 0.0)) * pulse(t, 0.20, 0.14, 0.34, 0.18)
            rig.set_claws(p, fl=fl_brace, fr=fr_claw, hook=1.0 if fr_claw > 0.25 else 0.0)
            mane_drv = [-9.0 * pin, cR[1] * 1.15, cR[2] * 1.10]
            return p, 16.0 * pin, -cR[1] * 1.35, 3.5 * pin, mane_drv
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.05, mane_lag=0.045)

    # 8a. SWIPE (1.00s) — Style 1 · Wide Horizontal Hook Smack (24 px sweep clear of mane!)
    clips["swipe"] = make_fk_swipe(1.00, 32, {
        "bodyT": ((0.0, [0, -0.2, 0]), (0.24, [0.9, -0.6, 0.7]), (0.37, [1.1, -0.5, -0.2]),
                  (0.50, [0.9, -0.7, -1.1]), (0.66, [0.5, -0.4, -0.5]), (0.84, [0.2, -0.2, -0.1]), (1.00, [0, -0.2, 0])),
        "bodyR": ((0.0, [0, 0, 0]), (0.24, [-2, 10, 2]), (0.37, [-2, 4, 0]),
                  (0.50, [-1, -13, -3]), (0.66, [1, -11, -2]), (1.00, [0, 0, 0])),
        "chestR": ((0.0, [0, 0, 0]), (0.24, [-2, 14, 3]), (0.37, [-3, 5, 1]),
                   (0.50, [-2, -17, -4]), (0.66, [2, -13, -2]), (1.00, [0, 0, 0])),
        "neckR": ((0.0, [0, 0, 0]), (0.24, [2, -12, -2]), (0.37, [2, -4, 0]),
                  (0.50, [3, 14, 3]), (0.66, [2, 10, 2]), (1.00, [0, 0, 0])),
        "headR": ((0.0, [0, 0, 0]), (0.24, [2, -10, -2]), (0.37, [2, -4, 0]),
                  (0.50, [2, 12, 3]), (0.66, [2, 8, 2]), (1.00, [0, 0, 0])),
        "jaw": ((0.0, 0), (0.24, 14), (0.37, 24), (0.50, 36), (0.66, 22), (1.00, 0)),
        "fr1": ((0.0, [0, 0, 0]), (0.24, [-62, 52, 18]), (0.37, [-84, 22, 10]),
                (0.50, [-78, -32, -12]), (0.66, [-58, -32, -10]), (0.82, [-26, -8, -2]), (1.00, [0, 0, 0])),
        "fr2": ((0.0, [0, 0, 0]), (0.24, [25, 65, 0]), (0.37, [8, 78, 0]),
                (0.50, [28, 82, 0]), (0.66, [26, 48, 0]), (0.82, [-24, 10, 0]), (1.00, [0, 0, 0])),
        "frP": ((0.0, [0, 0, 0]), (0.09, [0, 0, 0]), (0.24, [70, 0, 0]), (0.37, [76, 0, 0]),
                (0.50, [62, 0, 0]), (0.66, [28, 0, 0]), (0.82, [14, 0, 0]), (1.00, [0, 0, 0])),
    })

    # 8b. SWIPE_HEAVY (1.05s) — Style 2 · High Overhead-Outward Club (Male pride-fight takedown blow!)
    if kind == "male":
        clips["swipe_heavy"] = make_fk_swipe(1.05, 34, {
        "fl_brace": 0.38,
        "bodyT": ((0.0, [0, -0.2, 0]), (0.28, [1.0, -0.4, 1.0]), (0.41, [1.1, -0.4, 0.0]),
                  (0.54, [0.8, -0.9, -1.3]), (0.70, [0.4, -0.5, -0.6]), (0.88, [0.1, -0.2, -0.1]), (1.05, [0, -0.2, 0])),
        "bodyR": ((0.0, [0, 0, 0]), (0.28, [-4, 11, 3]), (0.41, [-3, 4, 1]),
                  (0.54, [2, -13, -3]), (0.70, [2, -10, -2]), (1.05, [0, 0, 0])),
        "chestR": ((0.0, [0, 0, 0]), (0.28, [-4, 15, 4]), (0.41, [-3, 5, 1]),
                   (0.54, [3, -17, -4]), (0.70, [3, -12, -2]), (1.05, [0, 0, 0])),
        "neckR": ((0.0, [0, 0, 0]), (0.28, [4, -13, -3]), (0.41, [3, -4, 0]),
                  (0.54, [1, 14, 3]), (0.70, [1, 9, 2]), (1.05, [0, 0, 0])),
        "headR": ((0.0, [0, 0, 0]), (0.28, [3, -11, -2]), (0.41, [2, -4, 0]),
                  (0.54, [1, 12, 3]), (0.70, [1, 8, 2]), (1.05, [0, 0, 0])),
        "jaw": ((0.0, 0), (0.28, 16), (0.41, 28), (0.54, 38), (0.70, 20), (1.05, 0)),
        "fr1": ((0.0, [0, 0, 0]), (0.28, [-106, 42, 24]), (0.41, [-86, 14, 12]),
                (0.54, [-66, -34, -14]), (0.70, [-52, -24, -8]), (0.88, [-24, -6, -2]), (1.05, [0, 0, 0])),
        "fr2": ((0.0, [0, 0, 0]), (0.28, [38, 55, 0]), (0.41, [10, 75, 0]),
                (0.54, [24, 78, 0]), (0.70, [10, 38, 0]), (0.88, [-24, 8, 0]), (1.05, [0, 0, 0])),
        "frP": ((0.0, [0, 0, 0]), (0.28, [65, 0, 0]), (0.41, [78, 0, 0]),
                (0.54, [44, 0, 0]), (0.70, [20, 0, 0]), (0.88, [12, 0, 0]), (1.05, [0, 0, 0])),
    })

    # 8c. SWIPE_QUICK (0.90s) — Style 3 · Fast Cat-Boxer Sparring Hook (snappy boxing cuff!)
    clips["swipe_quick"] = make_fk_swipe(0.90, 28, {
        "bodyT": ((0.0, [0, -0.2, 0]), (0.22, [0.8, -0.6, 0.6]), (0.34, [1.0, -0.5, -0.2]),
                  (0.45, [0.8, -0.5, -0.9]), (0.60, [0.4, -0.3, -0.3]), (0.76, [0.1, -0.2, -0.1]), (0.90, [0, -0.2, 0])),
        "bodyR": ((0.0, [0, 0, 0]), (0.22, [-2, 9, 2]), (0.34, [-2, 3, 0]),
                  (0.45, [-1, -12, -3]), (0.58, [0, -8, -1]), (0.90, [0, 0, 0])),
        "chestR": ((0.0, [0, 0, 0]), (0.22, [-2, 12, 3]), (0.34, [-3, 4, 1]),
                   (0.45, [-2, -16, -4]), (0.58, [1, -10, -2]), (0.90, [0, 0, 0])),
        "neckR": ((0.0, [0, 0, 0]), (0.22, [2, -10, -2]), (0.34, [2, -3, 0]),
                  (0.45, [2, 13, 3]), (0.58, [1, 8, 1]), (0.90, [0, 0, 0])),
        "headR": ((0.0, [0, 0, 0]), (0.22, [2, -9, -2]), (0.34, [2, -3, 0]),
                  (0.45, [2, 11, 3]), (0.58, [1, 7, 1]), (0.90, [0, 0, 0])),
        "jaw": ((0.0, 0), (0.22, 12), (0.34, 22), (0.45, 32), (0.58, 16), (0.90, 0)),
        "fr1": ((0.0, [0, 0, 0]), (0.22, [-68, 44, 22]), (0.34, [-88, 18, 8]),
                (0.45, [-74, -38, -14]), (0.60, [-56, -22, -6]), (0.76, [-26, -6, -2]), (0.90, [0, 0, 0])),
        "fr2": ((0.0, [0, 0, 0]), (0.22, [48, 72, 0]), (0.34, [12, 82, 0]),
                (0.45, [42, 85, 0]), (0.60, [20, 42, 0]), (0.76, [-24, 8, 0]), (0.90, [0, 0, 0])),
        "frP": ((0.0, [0, 0, 0]), (0.09, [0, 0, 0]), (0.22, [68, 0, 0]), (0.34, [78, 0, 0]),
                (0.45, [52, 0, 0]), (0.60, [22, 0, 0]), (0.76, [12, 0, 0]), (0.90, [0, 0, 0])),
    })

    # 8d. SWIPE_COMBO (1.45s) — Rearing 1-2 Pride-King Boxing Flurry (Right Hook -> Weight Shift -> Left Hook -> Slam!)
    def make_swipe_combo():
        L, N = 1.45, 44
        def frame(i, t):
            rear = _kscal(t, ((0.0, 0.0), (0.18, 0.85), (0.32, 1.0), (1.02, 0.92), (1.22, 0.0), (L, 0.0)))
            slam = pulse(t, 1.14, 0.08, 0.05, 0.16)
            # Torso twists into RIGHT hook (0.30..0.52), shifts hard across into LEFT hook (0.68..0.92)!
            bT_raw = _kvec(t, ((0.0, [0, -0.2, 0]), (0.22, [0.9, 1.4, 2.2]), (0.48, [1.2, 1.8, 0.8]),
                               (0.70, [-1.0, 1.6, 1.2]), (0.92, [-1.2, 1.3, 0.2]),
                               (1.20, [0.0, -2.0, -1.2]), (L, [0, -0.2, 0])))
            bR = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [-18, 11, 3]), (0.48, [-20, -14, -4]),
                           (0.68, [-19, -12, -3]), (0.92, [-17, 15, 4]), (1.20, [4, 0, 0]), (L, [0, 0, 0])))
            cR = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [-6, 14, 4]), (0.48, [-5, -18, -5]),
                           (0.68, [-5, -14, -4]), (0.92, [-4, 18, 5]), (1.20, [4, 0, 0]), (L, [0, 0, 0])))
            nR = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [15, -13, -3]), (0.48, [16, 16, 4]),
                           (0.68, [15, 13, 3]), (0.92, [14, -17, -4]), (1.20, [-4, 0, 0]), (L, [0, 0, 0])))
            hR = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [9, -10, -2]), (0.48, [10, 14, 3]),
                           (0.68, [10, 11, 2]), (0.92, [9, -14, -3]), (1.20, [-4, 0, 0]), (L, [0, 0, 0])))
            jw = _kscal(t, ((0.0, 0), (0.24, 22), (0.48, 40), (0.70, 26), (0.92, 42), (1.22, 10), (L, 0)))
            pin = _clamp(max(rear, jw / 32.0), 0.0, 1.0)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [bT_raw[0] * sc, bT_raw[1] * sc, bT_raw[2] * sc], "rotation": bR},
                "chest": {"rotation": cR},
                "waist": {"rotation": [4.0 * rear, -cR[1] * 0.40, -cR[2] * 0.30]},
                "hips": {"rotation": [6.0 * rear, -cR[1] * 0.20, 0.0]},
                "neck": {"rotation": nR},
                "head": {"rotation": hR},
                "jaw": {"rotation": [jw, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [jw * 0.14, 0.0, 0.0]},
                "tongue_tip": {"rotation": [jw * 0.25, 0.0, 0.0]},
                "ear_left": {"rotation": [-30.0 * pin, -18.0 * pin, 14.0 * pin]},
                "ear_right": {"rotation": [-30.0 * pin, 18.0 * pin, -14.0 * pin]},
            }
            rig.set_eyes(p, squint=0.78 * pin)
            targs = rest_targets(x_pad=0.65)
            for tag in ("bl", "br"):
                targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + 16.0 * rear + 10.0 * slam, 12.0, 56.0)
            # BOTH forelegs lift into pure Direct FK during the rearing 1-2 flurry, then hand off cleanly to IK touchdown!
            fb = _kscal(t, ((0.0, 0.0), (0.14, 1.0), (0.98, 1.0), (1.12, 0.0), (L, 0.0)))
            fr1 = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [-58, 48, 22]), (0.36, [-78, 18, 10]),
                            (0.48, [-66, -34, -14]), (0.70, [-36, 12, 8]), (0.94, [-42, 20, 12]),
                            (1.16, [-14, 0, 0]), (L, [0, 0, 0])))
            fr2 = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [38, 65, 0]), (0.36, [12, 78, 0]),
                            (0.48, [34, 82, 0]), (0.70, [52, 24, 0]), (0.94, [48, 20, 0]),
                            (1.16, [14, 0, 0]), (L, [0, 0, 0])))
            frP = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [66, 0, 0]), (0.36, [76, 0, 0]),
                            (0.48, [58, 0, 0]), (0.70, [34, 0, 0]), (0.94, [30, 0, 0]),
                            (1.12, [0, 0, 0]), (L, [0, 0, 0])))
            # Mirrored Left Foreleg (fl): -ry = outward left (+X), +ry = across right (-X), -ry on fl2 = palm inward!
            fl1 = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [-36, -16, -10]), (0.48, [-42, -24, -14]),
                            (0.68, [-62, -48, -22]), (0.80, [-80, -18, -10]), (0.92, [-68, 34, 14]),
                            (1.16, [-14, 0, 0]), (L, [0, 0, 0])))
            fl2 = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [48, -20, 0]), (0.48, [54, -26, 0]),
                            (0.68, [36, -66, 0]), (0.80, [10, -78, 0]), (0.92, [32, -82, 0]),
                            (1.16, [14, 0, 0]), (L, [0, 0, 0])))
            flP = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [34, 0, 0]), (0.48, [38, 0, 0]),
                            (0.68, [66, 0, 0]), (0.80, [76, 0, 0]), (0.92, [52, 0, 0]),
                            (1.12, [0, 0, 0]), (L, [0, 0, 0])))
            set_arc = pulse(t, 0.96, 0.08, 0.04, 0.14)
            fr_pos = [targs["fr"]["pos"][0], targs["fr"]["pos"][1] + 3.4 * sc * set_arc, targs["fr"]["pos"][2]]
            fl_pos = [targs["fl"]["pos"][0], targs["fl"]["pos"][1] + 3.4 * sc * set_arc, targs["fl"]["pos"][2]]
            targs["fr"] = {"pos": fr_pos, "fk": (fr1, fr2, frP), "fk_blend": fb}
            targs["fl"] = {"pos": fl_pos, "fk": (fl1, fl2, flP), "fk_blend": fb}
            rig.solve_legs(p, targs)
            # 1-2 Boxing Flurry: Right claws SNAP OUT on Hook 1 (0.20..0.58), Left claws SNAP OUT on Hook 2 (0.60..1.00), hind traction anchor!
            fr_c = _kscal(t, ((0.0, 0.0), (0.16, 0.0), (0.24, 1.0), (0.54, 1.0), (0.66, 0.0), (L, 0.0)))
            fl_c = _kscal(t, ((0.0, 0.0), (0.56, 0.0), (0.66, 1.0), (0.96, 1.0), (1.08, 0.0), (L, 0.0)))
            rig.set_claws(p, fl=fl_c, fr=fr_c, bl=0.50 * rear, br=0.50 * rear, hook=1.0 if max(fl_c, fr_c) > 0.25 else 0.0)
            mane_drv = [-10.0 * rear + 10.0 * slam, cR[1] * 1.15, cR[2] * 1.15]
            return p, 54.0 * rear + 12.0 * slam, -cR[1] * 1.4, 4.0 * slam, mane_drv
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.055, mane_lag=0.05)
    if kind == "male":
        clips["swipe_combo"] = make_swipe_combo()

    # 8e. JUMP_SMACK (1.00s) — Bounding Mid-Air Hook Smack! (Leap straight out of the gallop -> Mid-Air Torso-Whip Direct-FK Smack -> Land IK!)
    # The Motion Studio phase 3, step 4 (BUGS 375): it is played out of a gallop, so it carries its own travel (a path
    # note, at the gallop clip's own speed from its first tick, easing to a stop as it lands) and starts where the old
    # standing coil let go (0.20 s into the old 1.20 s timeline, whose keys are kept): a coil in place stopped a running
    # lioness dead. Its feet are solved against the path: planted where they stand, moving with the body in the air,
    # planted again where they land.
    def make_jump_smack():
        L0, T0 = 1.20, 0.20                     # the old timeline (its keys) and where the leap now starts on it
        L, N = L0 - T0, 32
        v = walk_speeds["gallop"]
        def path(t):
            return travel(t + T0, v, T0, T0, 0.72, 0.80)
        dz_end = -path(L)
        def frame(i, t):
            dz = -path(t)
            t = t + T0
            coil = pulse(t, 0.0, 0.18, 0.04, 0.10)
            u_air = _clamp((t - 0.22) / 0.56, 0.0, 1.0)
            arc = math.sin(math.pi * u_air) if 0.22 <= t <= 0.78 else 0.0
            land = pulse(t, 0.76, 0.08, 0.06, 0.22)
            # Forward-diagonal leap with mid-air torso whip INTO the apex right-paw smack!
            bT_raw = _kvec(t, ((0.0, [0.0, -0.2, 0.0]), (0.18, [0.6, -4.0, 3.0]),
                               (0.38, [1.4, 7.6, -2.5]), (0.52, [1.2, 9.6, -5.8]),
                               (0.64, [0.4, 6.2, -6.8]), (0.82, [0.0, -2.6, -3.2]), (L0, [0.0, -0.2, 0.0])))
            bR = _kvec(t, ((0.0, [0, 0, 0]), (0.18, [7, 10, 2]), (0.38, [-14, 14, 4]),
                           (0.54, [-4, -18, -5]), (0.68, [10, -14, -3]), (0.84, [5, -4, 0]), (L0, [0, 0, 0])))
            cR = _kvec(t, ((0.0, [0, 0, 0]), (0.18, [5, 12, 3]), (0.38, [-6, 16, 4]),
                           (0.54, [-2, -22, -6]), (0.68, [5, -15, -3]), (0.84, [4, -4, 0]), (L0, [0, 0, 0])))
            nR = _kvec(t, ((0.0, [0, 0, 0]), (0.18, [6, -10, -2]), (0.38, [8, -14, -3]),
                           (0.54, [4, 18, 4]), (0.68, [-4, 12, 3]), (0.84, [-4, 3, 0]), (L0, [0, 0, 0])))
            hR = _kvec(t, ((0.0, [0, 0, 0]), (0.18, [-12, -8, -2]), (0.38, [4, -12, -3]),
                           (0.54, [3, 16, 4]), (0.68, [-5, 10, 2]), (0.84, [-5, 2, 0]), (L0, [0, 0, 0])))
            jw = _kscal(t, ((0.0, 0), (0.22, 14), (0.42, 28), (0.56, 42), (0.72, 20), (0.90, 0), (L0, 0)))
            pin = _clamp(max(coil * 0.6, arc, jw / 32.0), 0.0, 1.0)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [bT_raw[0] * sc, bT_raw[1] * sc, bT_raw[2] * sc], "rotation": bR},
                "chest": {"rotation": cR},
                "waist": {"rotation": [-7.0 * coil + 10.0 * arc - 4.0 * land, -cR[1] * 0.45, -cR[2] * 0.35]},
                "hips": {"rotation": [-6.0 * coil + 8.0 * arc - 3.0 * land, -cR[1] * 0.25, 0.0]},
                "neck": {"rotation": nR},
                "head": {"rotation": hR},
                "jaw": {"rotation": [jw, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [jw * 0.14, 0.0, 0.0]},
                "tongue_tip": {"rotation": [jw * 0.25, 0.0, 0.0]},
                "ear_left": {"rotation": [-32.0 * pin, -18.0 * pin, 14.0 * pin]},
                "ear_right": {"rotation": [-32.0 * pin, 18.0 * pin, -14.0 * pin]},
            }
            rig.set_eyes(p, squint=0.80 * pin)
            targs = rest_targets(x_pad=0.55)
            # Smooth IK <-> Direct-FK phase blend strictly while airborne so takeoff & touchdown are 100% ground-IK clean!
            fb_fr = _kscal(t, ((0.0, 0.0), (0.26, 0.0), (0.34, 1.0), (0.62, 1.0), (0.72, 0.0), (L0, 0.0)))
            fb_air = _kscal(t, ((0.0, 0.0), (0.28, 0.0), (0.36, 1.0), (0.62, 1.0), (0.72, 0.0), (L0, 0.0)))
            # Right foreleg: wide outward cock mid-leap -> EXPLOSIVE horizontal cross-body apex smack!
            fr1 = _kvec(t, ((0.26, [-36, 16, 8]), (0.38, [-76, 52, 22]), (0.48, [-90, 18, 10]),
                            (0.58, [-80, -38, -14]), (0.70, [-48, -16, -4]), (0.80, [-14, 0, 0])))
            fr2 = _kvec(t, ((0.26, [44, 24, 0]), (0.38, [30, 68, 0]), (0.48, [10, 80, 0]),
                            (0.58, [28, 84, 0]), (0.70, [32, 24, 0]), (0.80, [10, 0, 0])))
            frP = _kvec(t, ((0.26, [24, 0, 0]), (0.38, [68, 0, 0]), (0.48, [78, 0, 0]),
                            (0.58, [60, 0, 0]), (0.70, [14, 0, 0]), (0.80, [0, 0, 0])))
            # Left foreleg: athletic mid-air tuck -> reaches down cleanly for landing cushion!
            fl1 = _kvec(t, ((0.28, [-22, 0, 0]), (0.42, [-42, -16, -12]), (0.58, [-52, -10, -8]), (0.74, [-22, 0, 0])))
            fl2 = _kvec(t, ((0.28, [30, 0, 0]), (0.42, [74, 0, 0]), (0.58, [54, 0, 0]), (0.74, [16, 0, 0])))
            flP = _kvec(t, ((0.28, [12, 0, 0]), (0.42, [44, 0, 0]), (0.58, [34, 0, 0]), (0.74, [0, 0, 0])))
            # Hind legs: powerful explosive backward trail mid-air -> sweep under body for landing!
            b_fk = (
                _kvec(t, ((0.28, [18, 0, 0]), (0.46, [44, 0, 0]), (0.64, [12, 0, 0]), (0.76, [-6, 0, 0]))),
                _kvec(t, ((0.28, [-10, 0, 0]), (0.46, [-18, 0, 0]), (0.64, [-24, 0, 0]), (0.76, [-8, 0, 0]))),
                _kvec(t, ((0.28, [12, 0, 0]), (0.46, [24, 0, 0]), (0.64, [28, 0, 0]), (0.76, [10, 0, 0]))),
                _kvec(t, ((0.28, [10, 0, 0]), (0.46, [34, 0, 0]), (0.64, [16, 0, 0]), (0.76, [0, 0, 0]))),
            )
            air_h = 6.0 * sc * arc
            targs["fr"] = {"pos": [targs["fr"]["pos"][0], targs["fr"]["pos"][1] + air_h, targs["fr"]["pos"][2] - 2.4 * sc * land],
                           "fk": (fr1, fr2, frP), "fk_blend": fb_fr}
            targs["fl"] = {"pos": [targs["fl"]["pos"][0], targs["fl"]["pos"][1] + air_h, targs["fl"]["pos"][2] - 2.4 * sc * land],
                           "fk": (fl1, fl2, flP), "fk_blend": fb_air}
            for tag in ("bl", "br"):
                targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + 22.0 * coil + 14.0 * land, 12.0, 56.0)
                targs[tag]["pos"][1] += 4.5 * sc * arc
                targs[tag]["pos"][2] -= 2.2 * sc * land
                targs[tag]["fk"] = b_fk
                targs[tag]["fk_blend"] = fb_air
            # against the path: a foot planted where it stood (its leg pushes back as the body goes), with the body in
            # the air, planted where it lands (it reaches forward for it)
            for tag, w in (("fr", fb_fr), ("fl", fb_air), ("bl", fb_air), ("br", fb_air)):
                targs[tag]["pos"][2] += on_path(w, t >= 0.5, dz, dz_end)
            p["root"]["position"] = [0.0, 0.0, dz]
            rig.solve_legs(p, targs)
            p["root"]["position"] = [0.0, 0.0, 0.0]
            # Hind launch cleats on coil; right smacking paw explodes 100% splayed mid-air -> retracts cleanly on landing!
            fr_c = _kscal(t, ((0.0, 0.0), (0.24, 0.0), (0.34, 1.0), (0.62, 1.0), (0.72, 0.0), (L0, 0.0)))
            fl_c = _kscal(t, ((0.0, 0.0), (0.28, 0.0), (0.38, 0.68), (0.60, 0.68), (0.70, 0.0), (L0, 0.0)))
            rig.set_claws(p, fl=fl_c, fr=fr_c, bl=0.65 * coil, br=0.65 * coil, hook=1.0 if arc > 0.2 else 0.0)
            mane_drv = [-14.0 * arc + 10.0 * land, cR[1] * 1.0, cR[2] * 1.0]
            return p, 34.0 * coil + 36.0 * arc + 14.0 * land, -cR[1] * 1.35, -5.0 * arc + 4.0 * land, mane_drv
        made = _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.05, mane_lag=0.045)
        made["timeline"] = {"0.0": path_note(L, path)}
        return made
    if not is_cub:
        clips["jump_smack"] = make_jump_smack()

    # 8f. STALK_LEAP_BITE (1.45s) — Seamless 0-deg Chain from STALK -> Torpedo Leap -> Clamping Throat Bite -> Stand!
    # The Motion Studio phase 3, step 4 (BUGS 375): the leap carries its own travel (a path note: still through the coil,
    # easing up to the gallop clip's own speed as the hind legs drive, held through the flight, easing to a stop as it
    # lands on its bite at 0.80 s), its feet solved against it: planted where they stand, moving with the body in the
    # air, planted where they land.
    def make_stalk_leap_bite():
        L, N = 1.45, 44
        v = walk_speeds["gallop"]
        def path(t):
            return travel(t, v, 0.26, 0.36, 0.70, 0.80)
        dz_end = -path(L)
        def frame(i, t):
            dz = -path(t)
            # Starts at t=0.0 in EXACT crouched `stalk` baseline (body_drop=-4.6, chest_p=7.5, neck_p=11.0, head_p=-17.5, hock=44.0)!
            st_base = _kscal(t, ((0.0, 1.0), (0.28, 1.0), (0.44, 0.0), (L, 0.0)))
            coil = pulse(t, 0.04, 0.16, 0.08, 0.10)
            u_air = _clamp((t - 0.30) / 0.48, 0.0, 1.0)
            arc = math.sin(math.pi * u_air) if 0.30 <= t <= 0.78 else 0.0
            clamp_b = pulse(t, 0.76, 0.08, 0.22, 0.28)
            # Low torpedo leap trajectory (crisp vertical launch & landing clearance) -> clamping bite -> rise to idle
            bT_raw = _kvec(t, ((0.0, [0.0, -4.82, 0.0]), (0.24, [0.0, -5.50, 2.6]),
                               (0.42, [0.0, 7.4, -2.8]), (0.58, [0.0, 9.2, -6.8]),
                               (0.70, [0.0, 5.2, -6.6]), (0.82, [0.0, -2.8, -5.6]),
                               (1.00, [0.0, -2.0, -3.4]), (L, [0.0, -0.20, 0.0])))
            bR = _kvec(t, ((0.0, [0.0, 0, 0]), (0.24, [5.0, 0, 0]), (0.44, [-15.0, 0, 0]),
                           (0.62, [8.0, 0, 0]), (0.82, [8.5, 0, 0]), (1.02, [4.0, 0, 0]), (L, [0.0, 0, 0])))
            cR = _kvec(t, ((0.0, [7.5, 0, 0]), (0.24, [9.5, 0, 0]), (0.46, [-10.0, 0, 0]),
                           (0.66, [4.0, 0, 0]), (0.84, [8.0, 0, 0]), (1.05, [4.0, 0, 0]), (L, [0.0, 0, 0])))
            wR = _kvec(t, ((0.0, [0.0, 0, 0]), (0.24, [-7.0, 0, 0]), (0.48, [15.0, 0, 0]),
                           (0.80, [-6.0, 0, 0]), (1.05, [-2.0, 0, 0]), (L, [0.0, 0, 0])))
            hP = _kvec(t, ((0.0, [-2.6, 0, 0]), (0.24, [-7.5, 0, 0]), (0.48, [11.0, 0, 0]),
                           (0.80, [-4.0, 0, 0]), (1.05, [-2.0, 0, 0]), (L, [0.0, 0, 0])))
            nR = _kvec(t, ((0.0, [11.0, 0, 0]), (0.24, [12.5, 0, 0]), (0.48, [-6.0, 0, 0]),
                           (0.68, [6.0, 0, 0]), (0.84, [18.0, 0, 0]), (1.02, [10.0, 0, 0]), (L, [0.0, 0, 0])))
            hdR = _kvec(t, ((0.0, [-17.5, 0, 0]), (0.24, [-21.0, 0, 0]), (0.50, [-16.0, 0, 0]),
                            (0.68, [-10.0, 0, 0]), (0.82, [11.0, 0, 0]), (0.98, [6.0, 0, 0]), (L, [0.0, 0, 0])))
            # Jaws explode open to 52 deg mid-leap -> CRUSH SHUT to 4 deg on touchdown at t=0.80!
            jw = _kscal(t, ((0.0, 0.0), (0.28, 6.0), (0.50, 42.0), (0.68, 52.0),
                            (0.80, 4.0), (1.00, 3.0), (1.18, 0.0), (L, 0.0)))
            gf = jw / 52.0
            pin = _clamp(max(st_base * 0.85, arc, clamp_b), 0.0, 1.0)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [bT_raw[0] * sc, bT_raw[1] * sc, bT_raw[2] * sc], "rotation": bR},
                "chest": {"rotation": cR},
                "waist": {"rotation": wR},
                "hips": {"rotation": hP},
                "neck": {"rotation": nR},
                "head": {"rotation": hdR},
                "jaw": {"rotation": [jw, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [6.0 * gf, 0.0, 0.0]},
                "tongue_tip": {"rotation": [12.0 * gf, 0.0, 0.0]},
                "ear_left": {"rotation": [-30.0 * pin, -15.0 * pin, 11.0 * pin]},
                "ear_right": {"rotation": [-30.0 * pin, 15.0 * pin, -11.0 * pin]},
            }
            rig.set_eyes(p, blink=pulse(t, 0.79, 0.04, 0.04, 0.10) * 0.65, squint=0.78 * pin)
            targs = rest_targets(x_pad=1.10 * st_base + 0.45 * (1.0 - st_base))
            # Engage Direct FK strictly once airborne (0.40..0.64) so takeoff & touchdown are 100% exact ground-IK!
            fb_air = _kscal(t, ((0.0, 0.0), (0.36, 0.0), (0.44, 1.0), (0.60, 1.0), (0.68, 0.0), (L, 0.0)))
            air_h = 7.2 * sc * arc
            # BOTH forelegs spear forward & wide open in Direct FK (palms turned inward to hook prey's shoulders!)
            for tag, s_sign in (("fl", 1), ("fr", -1)):
                f1 = _kvec(t, ((0.36, [-36, 0, 0]), (0.48, [-84, -s_sign * 18, -s_sign * 12]),
                               (0.60, [-76, -s_sign * 10, -s_sign * 6]), (0.70, [-30, 0, 0])))
                f2 = _kvec(t, ((0.36, [24, 0, 0]), (0.48, [16, -s_sign * 42, 0]),
                               (0.60, [14, -s_sign * 28, 0]), (0.70, [16, 0, 0])))
                fP = _kvec(t, ((0.36, [22, 0, 0]), (0.48, [68, 0, 0]), (0.60, [48, 0, 0]), (0.70, [0, 0, 0])))
                targs[tag]["pos"][1] += air_h
                targs[tag]["pos"][2] -= (4.2 * clamp_b + 5.5 * arc) * sc
                targs[tag]["fk"] = (f1, f2, fP)
                targs[tag]["fk_blend"] = fb_air
            # Hind legs drive explosively straight back in Direct FK during the torpedo flight!
            b_fk = (
                _kvec(t, ((0.36, [18, 0, 0]), (0.50, [46, 0, 0]), (0.62, [22, 0, 0]), (0.70, [-6, 0, 0]))),
                _kvec(t, ((0.36, [-14, 0, 0]), (0.50, [-14, 0, 0]), (0.62, [-20, 0, 0]), (0.70, [-8, 0, 0]))),
                _kvec(t, ((0.36, [14, 0, 0]), (0.50, [22, 0, 0]), (0.62, [22, 0, 0]), (0.70, [8, 0, 0]))),
                _kvec(t, ((0.36, [10, 0, 0]), (0.50, [34, 0, 0]), (0.62, [16, 0, 0]), (0.70, [0, 0, 0]))),
            )
            for tag in ("bl", "br"):
                targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + (44.0 - rig.hind[tag]["rest_hock"]) * st_base + 10.0 * coil + 12.0 * clamp_b, 12.0, 56.0)
                targs[tag]["pos"][1] += 5.5 * sc * arc
                targs[tag]["pos"][2] += (5.2 * arc - 3.6 * clamp_b) * sc
                targs[tag]["fk"] = b_fk
                targs[tag]["fk_blend"] = fb_air
            # against the path: a foot planted where it stood (its leg pushes back as the body goes), with the body in
            # the air, planted where it lands (it reaches forward for it)
            for tag in ("fl", "fr", "bl", "br"):
                targs[tag]["pos"][2] += on_path(fb_air, t >= 0.54, dz, dz_end)
            p["root"]["position"] = [0.0, 0.0, dz]
            rig.solve_legs(p, targs)
            p["root"]["position"] = [0.0, 0.0, 0.0]
            # Silent 0% during stalk -> hind cleats on launch -> BOTH front talons flare 100% mid-leap & clamp!
            f_c = _kscal(t, ((0.0, 0.0), (0.30, 0.0), (0.42, 1.0), (0.72, 1.0), (0.98, 0.72), (1.16, 0.0), (L, 0.0)))
            b_c = _kscal(t, ((0.0, 0.0), (0.16, 0.0), (0.26, 0.70), (0.38, 0.0), (L, 0.0)))
            rig.set_claws(p, fl=f_c, fr=f_c, bl=b_c, br=b_c, hook=1.0 if arc > 0.25 else 0.0)
            twitch = 20.0 * coil * math.sin(2.0 * math.pi * t * 6.5)
            mane_drv = [-14.0 * arc + 11.0 * clamp_b, 0.0, 0.0]
            # Slight mane_chest lift while ultra-low in stalk coil so chest fringe clears ground cleanly!
            m_ch_t = [0.0, 1.4 * sc * (st_base * 0.5 + coil * 0.5), -0.6 * sc * coil]
            return p, 24.0 * st_base + 14.0 * coil + 36.0 * arc + 16.0 * clamp_b, twitch, -6.0 * arc + 5.0 * clamp_b, mane_drv, m_ch_t
        made = _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.05, mane_lag=0.045)
        made["timeline"] = {"0.0": path_note(L, path)}
        return made
    if not is_cub:
        clips["stalk_leap_bite"] = make_stalk_leap_bite()

    # 8g. GALLOP_SWAT (0.95s) — Mid-Sprint Running Hindquarter Trip-Swat (forward bounding momentum + Direct-FK extended hook!)
    def make_gallop_swat():
        L, N = 0.95, 30
        def frame(i, t):
            u = t / L
            surge = math.sin(math.pi * u)
            reach_k = pulse(t, 0.16, 0.22, 0.18, 0.28)
            bT_raw = _kvec(t, ((0.0, [0.0, -0.4, 0.0]), (0.26, [0.6, 2.2, -3.2]),
                               (0.46, [0.8, 3.0, -5.2]), (0.68, [0.3, -1.2, -2.8]), (L, [0.0, -0.4, 0.0])))
            bR = _kvec(t, ((0.0, [2, 0, 0]), (0.26, [-7, 9, 2]), (0.46, [-4, -14, -4]),
                           (0.68, [5, -6, -1]), (L, [2, 0, 0])))
            cR = _kvec(t, ((0.0, [2, 0, 0]), (0.26, [-6, 12, 3]), (0.46, [-3, -18, -5]),
                           (0.68, [4, -7, -2]), (L, [2, 0, 0])))
            nR = _kvec(t, ((0.0, [5, 0, 0]), (0.26, [4, -10, -2]), (0.46, [6, 15, 3]),
                           (0.68, [4, 6, 1]), (L, [5, 0, 0])))
            hR = _kvec(t, ((0.0, [-6, 0, 0]), (0.26, [-4, -9, -2]), (0.46, [-3, 13, 3]),
                           (0.68, [-5, 5, 1]), (L, [-6, 0, 0])))
            jw = _kscal(t, ((0.0, 4), (0.28, 22), (0.48, 36), (0.72, 12), (L, 4)))
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [bT_raw[0] * sc, bT_raw[1] * sc, bT_raw[2] * sc], "rotation": bR},
                "chest": {"rotation": cR},
                "waist": {"rotation": [9.0 * surge, -cR[1] * 0.40, -cR[2] * 0.30]},
                "hips": {"rotation": [6.5 * surge, -cR[1] * 0.25, 0.0]},
                "neck": {"rotation": nR},
                "head": {"rotation": hR},
                "jaw": {"rotation": [jw, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [jw * 0.14, 0.0, 0.0]},
                "tongue_tip": {"rotation": [jw * 0.24, 0.0, 0.0]},
                "ear_left": {"rotation": [-28.0, -14.0 * reach_k, 10.0 * reach_k]},
                "ear_right": {"rotation": [-28.0, 14.0 * reach_k, -10.0 * reach_k]},
            }
            rig.set_eyes(p, squint=0.65)
            targs = rest_targets(x_pad=0.55)
            # Dynamic bounding gallop-stride on fl/bl/br + FAR-REACHING Direct-FK trip-swat on fr!
            fl_air = pulse(t, 0.14, 0.18, 0.18, 0.22)
            targs["fl"]["pos"][1] += 4.2 * sc * fl_air
            targs["fl"]["pos"][2] -= 5.0 * sc * math.sin(math.pi * _clamp((t - 0.12) / 0.60, 0.0, 1.0))
            targs["fl"]["toe"] = 22.0 * fl_air
            for tag, off in (("bl", 0.0), ("br", 0.10)):
                ha = pulse(t, 0.20 + off, 0.16, 0.18, 0.20)
                targs[tag]["pos"][1] += 4.5 * sc * ha
                targs[tag]["pos"][2] += 5.5 * sc * ha - 3.0 * sc * (1.0 - ha) * surge
                targs[tag]["hock"] = _clamp(22.0 + 14.0 * ha, 12.0, 48.0)
                targs[tag]["toe"] = 24.0 * ha
            fb = _kscal(t, ((0.0, 0.0), (0.14, 1.0), (0.50, 1.0), (0.66, 0.0), (L, 0.0)))
            fr1 = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [-74, 38, 16]), (0.38, [-96, 12, 8]),
                            (0.50, [-84, -34, -12]), (0.66, [-58, -10, -4]), (L, [0, 0, 0])))
            fr2 = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [18, 62, 0]), (0.38, [6, 78, 0]),
                            (0.50, [28, 82, 0]), (0.66, [48, 16, 0]), (L, [0, 0, 0])))
            frP = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [72, 0, 0]), (0.38, [80, 0, 0]),
                            (0.50, [52, 0, 0]), (0.66, [0, 0, 0]), (L, [0, 0, 0])))
            fr_set = pulse(t, 0.46, 0.10, 0.08, 0.16)
            fr_pos = [targs["fr"]["pos"][0], targs["fr"]["pos"][1] + 6.0 * sc * fr_set, targs["fr"]["pos"][2] - 2.6 * sc * fr_set]
            targs["fr"] = {"pos": fr_pos, "fk": (fr1, fr2, frP), "fk_blend": fb}
            rig.solve_legs(p, targs)
            # Right swatting paw explodes 100% out mid-trip-swat; grounded sprint paws flash 55% traction cleats!
            fr_c = _kscal(t, ((0.0, 0.0), (0.08, 0.0), (0.24, 1.0), (0.56, 1.0), (0.72, 0.0), (L, 0.0)))
            rig.set_claws(p, fl=0.55 * (1.0 - fl_air), fr=fr_c, bl=0.55 * (1.0 - surge), br=0.55 * (1.0 - surge),
                          hook=1.0 if fr_c > 0.30 else 0.0)
            mane_drv = [-10.0 * surge, cR[1] * 1.1, cR[2] * 1.1]
            return p, 32.0 + 8.0 * surge, -cR[1] * 1.2, -4.0 * surge, mane_drv
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.05, mane_lag=0.045)
    if not is_cub:
        clips["gallop_swat"] = make_gallop_swat()

    # 8h. TAKEDOWN_PIN (1.30s) — Heavy Double-Paw Pin + Clamped Throat Shake!
    def make_takedown_pin():
        L, N = 1.30, 40
        def frame(i, t):
            pin_w = pulse(t, 0.06, 0.16, 0.72, 0.28)
            slam = pulse(t, 0.12, 0.08, 0.08, 0.16)
            # 2 forceful predatory side-to-side head tugs while pinned!
            shake_env = pulse(t, 0.36, 0.10, 0.44, 0.16)
            shake = shake_env * math.sin(2.0 * math.pi * (t - 0.36) * 3.2)
            jw = _kscal(t, ((0.0, 0), (0.14, 44), (0.26, 7), (0.92, 6), (1.10, 0), (L, 0)))
            dy = -0.22 * sc - 3.6 * sc * pin_w - 0.8 * sc * slam
            dz = -2.8 * sc * pin_w
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, dy, dz], "rotation": [8.5 * pin_w, 3.0 * shake, 2.0 * shake]},
                "chest": {"rotation": [7.0 * pin_w, 6.0 * shake, 3.5 * shake]},
                "waist": {"rotation": [-5.0 * pin_w, -4.0 * shake, -2.0 * shake]},
                "hips": {"rotation": [-4.5 * pin_w, -2.5 * shake, 0.0]},
                "neck": {"rotation": [16.0 * pin_w, 13.0 * shake, 7.0 * shake]},
                "head": {"rotation": [11.0 * pin_w - 14.0 * (jw / 44.0), 15.0 * shake, 12.0 * shake]},
                "jaw": {"rotation": [jw, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [4.0 * (jw / 44.0), 0.0, 0.0]},
                "tongue_tip": {"rotation": [8.0 * (jw / 44.0), 0.0, 0.0]},
                "ear_left": {"rotation": [-34.0 * pin_w, -20.0 * pin_w, 16.0 * pin_w]},
                "ear_right": {"rotation": [-34.0 * pin_w, 20.0 * pin_w, -16.0 * pin_w]},
            }
            rig.set_eyes(p, squint=0.85 * pin_w)
            targs = rest_targets(x_pad=1.65 * pin_w)
            for tag in ("fl", "fr"):
                s_sign = rig.fore[tag]["side"]
                targs[tag]["pos"][2] -= 3.8 * sc * pin_w
                targs[tag]["pole"] = [s_sign * 0.55, 0.15 * pin_w, 1.0]
            for tag in ("bl", "br"):
                targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + 16.0 * pin_w, 12.0, 52.0)
            rig.solve_legs(p, targs)
            # Front talons locked 95% splayed & anchored pinning prey; hind paws 55% traction anchor!
            rig.set_claws(p, fl=0.95 * pin_w, fr=0.95 * pin_w, bl=0.55 * pin_w, br=0.55 * pin_w, hook=0.0)
            mane_drv = [-8.0 * pin_w + 8.0 * slam, -16.0 * shake, -10.0 * shake]
            m_ch_t = [0.0, 1.2 * sc * pin_w, -0.8 * sc * pin_w]
            return p, 24.0 * pin_w, -24.0 * shake, 4.0 * slam, mane_drv, m_ch_t
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.05, mane_lag=0.045)
    if not is_cub:
        clips["takedown_pin"] = make_takedown_pin()

    # 8h-bis. HOLDS MADE AGAINST THE BUFFALO (the Motion Studio phase 3, step 3; docs/plans/motion-studio-phase3.md).
    # A holder stands where her jaw at her bite meets the buffalo's spot (the game places her so). Holding a cow's throat,
    # going down with a cow brought down and holding it down, her jaw must be at the spot's height there, at the same
    # reach ahead as in her bite: then her place is the same for every hold and she never slides while the cow sinks.
    # The heights come from the buffalo's spot table (tools/spots.py, from its own clips) at the pair's sizes (its plan's
    # spots.pairs); her neck and body are solved each tick so her jaw is there. hold_sink plays with the cow's held_sink
    # on one clock (both start on the same tick; her clip starts one tick before the cow's, the blends being 4 and 5).
    if is_lns:
        clips.update(make_holds(rig, clips["bite"], rest_targets))

    # 8i. BLUFF_CHARGE (1.35s) — Territorial Threat Snarl -> Stomping Forward Lunge -> Warning Paw Slap!
    def make_bluff_charge():
        L, N = 1.35, 42
        def frame(i, t):
            snarl = pulse(t, 0.02, 0.24, 0.70, 0.30)
            lunge = pulse(t, 0.36, 0.18, 0.26, 0.38)
            stomp = pulse(t, 0.42, 0.08, 0.10, 0.16)
            bT_raw = _kvec(t, ((0.0, [0.0, -0.2, 0.0]), (0.24, [0.0, -2.4, 1.6]),
                               (0.48, [0.8, -1.6, -4.8]), (0.66, [0.7, -2.2, -5.4]),
                               (0.92, [0.2, -1.4, -2.6]), (L, [0.0, -0.2, 0.0])))
            bR = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [4, 6, 1]), (0.48, [2, 9, 2]),
                           (0.64, [3, -12, -3]), (0.90, [2, -4, 0]), (L, [0, 0, 0])))
            cR = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [5, 8, 2]), (0.48, [2, 13, 3]),
                           (0.64, [4, -16, -4]), (0.90, [3, -5, 0]), (L, [0, 0, 0])))
            nR = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [10, -7, -1]), (0.48, [8, -11, -2]),
                           (0.64, [9, 14, 3]), (0.90, [7, 4, 0]), (L, [0, 0, 0])))
            hR = _kvec(t, ((0.0, [0, 0, 0]), (0.24, [-12, -6, -1]), (0.48, [-10, -9, -2]),
                           (0.64, [-8, 12, 2]), (0.90, [-8, 3, 0]), (L, [0, 0, 0])))
            jw = _kscal(t, ((0.0, 0), (0.22, 28), (0.48, 34), (0.64, 42), (0.92, 22), (L, 0)))
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [bT_raw[0] * sc, bT_raw[1] * sc, bT_raw[2] * sc], "rotation": bR},
                "chest": {"rotation": cR},
                "waist": {"rotation": [-4.0 * snarl, -cR[1] * 0.40, -cR[2] * 0.30]},
                "hips": {"rotation": [-3.5 * snarl, -cR[1] * 0.20, 0.0]},
                "neck": {"rotation": nR},
                "head": {"rotation": hR},
                "jaw": {"rotation": [jw, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [5.0 * (jw / 42.0), 0.0, 0.0]},
                "tongue_tip": {"rotation": [10.0 * (jw / 42.0), 0.0, 0.0]},
                "ear_left": {"rotation": [-36.0 * snarl, -22.0 * snarl, 18.0 * snarl]},
                "ear_right": {"rotation": [-36.0 * snarl, 22.0 * snarl, -18.0 * snarl]},
            }
            rig.set_eyes(p, squint=0.88 * snarl)
            targs = rest_targets(x_pad=0.65 * snarl)
            # Left foreleg lifts & stomps forward onto the savanna floor; right foreleg snaps a sharp Direct-FK bluff cuff!
            fl_step = pulse(t, 0.32, 0.09, 0.04, 0.09)
            targs["fl"]["pos"][1] += 3.8 * sc * fl_step
            targs["fl"]["pos"][2] -= 4.2 * sc * lunge
            for tag in ("bl", "br"):
                targs[tag]["pos"][2] -= 2.4 * sc * lunge
                targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + 12.0 * snarl, 12.0, 50.0)
            # Right foreleg stays planted on exact IK during initial snarl, lifts cleanly into pure FK warning slap,
            # then tucks elbow down onto the ground cleanly (zero IK<->FK cancellation)!
            fb = _kscal(t, ((0.0, 0.0), (0.32, 0.0), (0.42, 1.0), (0.88, 1.0), (1.02, 0.0), (L, 0.0)))
            fr1 = _kvec(t, ((0.30, [20, 0, 0]), (0.40, [-36, 18, 10]), (0.52, [-74, 44, 20]),
                            (0.64, [-78, -34, -12]), (0.78, [-42, -14, -4]), (0.92, [-14, -2, 0]), (1.04, [10, 0, 0])))
            fr2 = _kvec(t, ((0.30, [-38, 0, 0]), (0.40, [-26, 22, 0]), (0.52, [42, 70, 0]),
                            (0.64, [30, 82, 0]), (0.78, [-28, 20, 0]), (0.92, [-26, 4, 0]), (1.04, [-18, 0, 0])))
            frP = _kvec(t, ((0.30, [0, 0, 0]), (0.40, [8, 0, 0]), (0.52, [66, 0, 0]),
                            (0.64, [52, 0, 0]), (0.78, [22, 0, 0]), (0.92, [16, 0, 0]), (1.04, [6, 0, 0])))
            targs["fr"] = {"pos": [targs["fr"]["pos"][0], targs["fr"]["pos"][1], targs["fr"]["pos"][2] - 2.6 * sc * lunge],
                           "fk": (fr1, fr2, frP), "fk_blend": fb}
            rig.solve_legs(p, targs)
            # Right warning-slap claws flash 100% out mid-cuff; left stomping paw flares 75% threat grip!
            fr_c = _kscal(t, ((0.0, 0.0), (0.36, 0.0), (0.46, 1.0), (0.78, 1.0), (0.90, 0.0), (L, 0.0)))
            fl_c = _kscal(t, ((0.0, 0.0), (0.30, 0.0), (0.42, 0.78), (0.76, 0.70), (0.94, 0.0), (L, 0.0)))
            rig.set_claws(p, fl=fl_c, fr=fr_c, hook=1.0 if fr_c > 0.35 else 0.0)
            lash = 24.0 * snarl * math.sin(2.0 * math.pi * t * 2.8)
            mane_drv = [-8.0 * lunge + 8.0 * stomp, cR[1] * 1.1, cR[2] * 1.0]
            return p, 22.0 * snarl, lash, 4.0 * stomp, mane_drv
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.055, mane_lag=0.05)
    if not is_cub:
        clips["bluff_charge"] = make_bluff_charge()

    # 8j. PRIDE_GREET (1.80s) — Affectionate Cheek/Mane Rub & High-Curling Greeting Tail!
    def make_pride_greet():
        L, N = 1.80, 44
        def frame(i, t):
            gr = pulse(t, 0.10, 0.42, 0.72, 0.46)
            rub = gr * math.sin(math.pi * _clamp((t - 0.30) / 1.10, 0.0, 1.0))
            sway = gr * math.sin(2.0 * math.pi * (t - 0.25) / 1.35)
            dy = -0.20 * sc - 1.2 * sc * gr
            dz = -1.6 * sc * rub
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [-1.2 * sc * rub, dy, dz], "rotation": [2.5 * gr, -6.0 * rub, -4.5 * rub]},
                "chest": {"rotation": [3.5 * gr, -8.0 * rub, -5.0 * rub]},
                "waist": {"rotation": [-2.0 * gr, 6.0 * rub, 3.0 * rub]},
                "hips": {"rotation": [-1.5 * gr, 5.0 * rub, 2.0 * rub]},
                "neck": {"rotation": [9.0 * gr, 16.0 * rub + 4.0 * sway, -12.0 * rub]},
                "head": {"rotation": [4.0 * gr, 18.0 * rub + 5.0 * sway, -18.0 * rub]},
                "jaw": {"rotation": [0.0, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
                "ear_left": {"rotation": [-6.0 * gr, -10.0 * gr, -10.0 * gr]},
                "ear_right": {"rotation": [-6.0 * gr, 10.0 * gr, 10.0 * gr]},
            }
            rig.set_eyes(p, blink=pulse(t, 0.75, 0.14, 0.24, 0.16) * 0.85, squint=0.52 * gr)
            targs = rest_targets(x_pad=0.30 * gr)
            step = pulse(t, 0.20, 0.16, 0.06, 0.16)
            targs["fl"]["pos"][1] += 2.2 * sc * step
            targs["fl"]["pos"][2] -= 1.8 * sc * rub
            rig.solve_legs(p, targs)
            mane_drv = [4.0 * gr, -10.0 * rub, -8.0 * rub]
            return p, 58.0 * gr, 16.0 * sway, 3.0 * gr, mane_drv
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.09, mane_lag=0.08)
    clips["pride_greet"] = make_pride_greet()

    # 9. POUNCE (1.25s) — Coiled ambush spring -> airborne leap -> heavy front-paw strike -> clean recover!
    def make_pounce():
        L, N = 1.25, 36
        def frame(i, t):
            coil = pulse(t, 0.0, 0.26, 0.06, 0.16)
            air = pulse(t, 0.28, 0.20, 0.14, 0.22)
            land = pulse(t, 0.76, 0.12, 0.08, 0.24)
            u_air = _clamp((t - 0.28) / 0.50, 0.0, 1.0)
            arc = math.sin(math.pi * u_air) if 0.28 <= t <= 0.78 else 0.0
            dy = -0.25 * sc - 4.8 * sc * coil + 9.2 * sc * arc - 2.8 * sc * land
            dz = 3.5 * sc * coil - 4.5 * sc * air - 1.8 * sc * land
            body_p = (8.0 * coil
                      - 18.0 * math.sin(math.pi * _clamp((t - 0.26) / 0.28, 0.0, 1.0))
                      + 13.0 * math.sin(math.pi * _clamp((t - 0.54) / 0.26, 0.0, 1.0))
                      + 5.0 * land)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, dy, dz], "rotation": [body_p, 0.0, 0.0]},
                "chest": {"rotation": [6.5 * coil - 11.0 * arc + 6.0 * land, 0.0, 0.0]},
                "waist": {"rotation": [-9.0 * coil + 13.0 * arc - 5.0 * land, 0.0, 0.0]},
                "hips": {"rotation": [-8.0 * coil + 10.0 * arc - 3.5 * land, 0.0, 0.0]},
                "neck": {"rotation": [10.0 * coil - 4.0 * arc + 5.0 * land, 0.0, 0.0]},
                "head": {"rotation": [-18.0 * coil + 5.0 * arc - 8.5 * land, 0.0, 0.0]},
                "jaw": {"rotation": [36.0 * arc + 14.0 * land, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [4.0 * arc, 0.0, 0.0]},
                "tongue_tip": {"rotation": [10.0 * arc, 0.0, 0.0]},
                "ear_left": {"rotation": [-30.0 * max(coil, arc), -18.0 * arc, 12.0 * arc]},
                "ear_right": {"rotation": [-30.0 * max(coil, arc), 18.0 * arc, -12.0 * arc]},
            }
            rig.set_eyes(p, squint=0.8 * max(coil, arc))
            targs = rest_targets()
            for tag in ("fl", "fr"):
                w = rig.fore[tag]["wr"]
                s_sign = rig.fore[tag]["side"]
                targs[tag] = {
                    "pos": [w[0] + s_sign * 1.4 * sc * arc, w[1] + 8.0 * sc * arc, w[2] - 8.2 * sc * arc - 1.5 * sc * land],
                    "toe": 28.0 * arc,
                }
            for tag in ("bl", "br"):
                a = rig.hind[tag]["an"]
                s_sign = rig.hind[tag]["side"]
                targs[tag] = {
                    "pos": [a[0] + s_sign * 0.8 * sc * coil, a[1] + 6.4 * sc * arc, a[2] + 7.5 * sc * arc],
                    "toe": 30.0 * arc,
                    "hock": _clamp(rig.hind[tag]["rest_hock"] + 24.0 * coil - 12.0 * arc + 12.0 * land, 10.0, 56.0),
                }
            rig.solve_legs(p, targs)
            # Silent 0% early coil -> hind launch cleats -> BOTH front paws flare 100% mid-air -> retract on landing!
            f_c = _kscal(t, ((0.0, 0.0), (0.24, 0.0), (0.34, 1.0), (0.76, 1.0), (0.94, 0.0), (L, 0.0)))
            b_c = _kscal(t, ((0.0, 0.0), (0.14, 0.0), (0.26, 0.68), (0.40, 0.0), (L, 0.0)))
            rig.set_claws(p, fl=f_c, fr=f_c, bl=b_c, br=b_c, hook=1.0 if arc > 0.20 else 0.0)
            mane_drv = [-12.0 * arc + 8.0 * land, 0.0, 0.0]
            # Tail lifted cleanly clear of ground during deep coil AND streaming during leap!
            return p, 34.0 * coil + 32.0 * arc + 12.0 * land, 16.0 * coil * math.sin(2.0 * math.pi * t * 5.0), -6.0 * arc, mane_drv
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.055, mane_lag=0.05)
    clips["pounce"] = make_pounce()

    # 10. SIT_DOWN / SIT / SIT_UP — Regal Pride Rock sentry sit (zero tail/hip ground clipping!)
    def sit_frame(f, t, loop_breath=False):
        ph = 2.0 * math.pi * t / 3.2 if loop_breath else 0.0
        br = math.sin(ph) if loop_breath else 0.0
        scan = math.sin(ph - 0.4) if loop_breath else 0.0
        blink = (pulse(t, 1.1, 0.08, 0.07, 0.10) + pulse(t, 2.5, 0.08, 0.06, 0.10)) if loop_breath else 0.0
        dy = -0.20 * sc - 4.7 * sc * f + 0.16 * sc * br * f
        dz = 1.6 * sc * f
        body_p = -20.0 * f
        p = {
            "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
            "body": {"position": [0.0, dy, dz], "rotation": [body_p, 0.0, 0.0]},
            "chest": {"rotation": [-4.5 * f - 1.0 * br * f, 2.0 * scan * f, 0.0]},
            "waist": {"rotation": [-6.5 * f, -1.0 * scan * f, 0.0]},
            "hips": {"rotation": [-7.5 * f, 0.0, 0.0]},
            "neck": {"rotation": [14.5 * f - 1.0 * br * f, 4.5 * scan * f, 0.0]},
            "head": {"rotation": [11.0 * f + 1.0 * br * f, 6.0 * scan * f, 1.5 * scan * f]},
            "jaw": {"rotation": [0.0, 0.0, 0.0]},
            "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
            "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
            "ear_left": {"rotation": [-2.0 * f, -4.0 * scan * f, 2.0 * br * f]},
            "ear_right": {"rotation": [-2.0 * f, 4.0 * scan * f, -2.0 * br * f]},
        }
        rig.set_eyes(p, blink=min(1.0, blink), look_x=0.22 * scan * f)
        targs = {}
        for tag in ("fl", "fr"):
            w = rig.fore[tag]["wr"]
            targs[tag] = {"pos": [w[0], w[1], w[2] + 2.8 * sc * f], "toe": 0.0}
        for tag in ("bl", "br"):
            a = rig.hind[tag]["an"]
            s_sign = rig.hind[tag]["side"]
            targs[tag] = {
                "pos": [a[0] + s_sign * 2.2 * sc * f, a[1], a[2] - 4.8 * sc * f],
                "toe": 0.0,
                "hock": rig.hind[tag]["rest_hock"] + 38.0 * f,
                "pole": [s_sign * 0.55, 0.25, -1.0],
            }
        rig.solve_legs(p, targs)
        flick = pulse(t, 1.6, 0.16, 0.45, 0.26) * math.sin(2.0 * math.pi * (t - 1.6) * 2.2) if loop_breath else 0.0
        tail_y = (28.0 + 8.0 * math.sin(ph) + 14.0 * flick) * f
        # Tail sweeps cleanly UP & around the seated haunch (+122 deg total lift across 5 joints -> 0.0 px ground clip!)
        return p, 122.0 * f, tail_y, -2.0 * br * f, [5.0 * f + 2.0 * br * f, -3.0 * scan * f, 0.0]

    clips["sit_down"] = _bake_clip(rig, 0.75, 22, lambda i, t: sit_frame(ease(t / 0.75), t, False), loop=False)
    clips["sit"] = _bake_clip(rig, 3.20, 36, lambda i, t: sit_frame(1.0, t, True), loop=True)
    clips["sit_up"] = _bake_clip(rig, 0.75, 22, lambda i, t: sit_frame(1.0 - ease(t / 0.75), t, False), loop=False)

    # 11. REST_DOWN / REST / REST_UP — FULL FLAT-ON-THE-GROUND Savanna Lounge! Belly, chest, elbows & hind legs ALL laid flat!
    rest_drop = 9.75 if kind == "male" else (9.35 if is_lns else 4.65)
    rest_body_rx = -6.5 if kind == "male" else (-5.8 if is_lns else -4.0)

    def rest_frame(f, t, loop_breath=False, shape=None):
        # shape(pose, leg targets): another clip made lying as it rests (the feed clips) changes the pose before its
        # legs are solved
        ph = 2.0 * math.pi * t / 3.8 if loop_breath else 0.0
        br = math.sin(ph) if loop_breath else 0.0
        blink = pulse(t, 0.9, 0.24, 1.15, 0.28) if loop_breath else 0.0
        dy = -0.20 * sc - rest_drop * f + 0.14 * sc * br * f
        p = {
            "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
            "body": {"position": [0.0, dy, 0.0], "rotation": [rest_body_rx * f + 0.4 * br * f, 0.0, 0.0]},
            "chest": {"rotation": [-1.5 * f - 0.8 * br * f, 2.0 * f, 0.0]},
            "waist": {"rotation": [-2.0 * f, -1.2 * f, 0.0]},
            "hips": {"rotation": [-2.5 * f, -1.8 * f, 0.0]},
            "neck": {"rotation": [9.5 * f - 0.7 * br * f, 3.8 * f, 0.0]},
            "head": {"rotation": [-2.5 * f + 0.7 * br * f, 5.2 * f, 2.2 * f]},
            "jaw": {"rotation": [0.0, 0.0, 0.0]},
            "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
            "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
            "ear_left": {"rotation": [-8.0 * f, -6.0 * f, -8.0 * f]},
            "ear_right": {"rotation": [-8.0 * f, 6.0 * f, 8.0 * f]},
        }
        rig.set_eyes(p, blink=blink, squint=0.35 * f)
        targs = {}
        # Forearms lie 100% FLAT & HORIZONTAL on the ground in front of the chest (0.00 px elbow clip)!
        for tag in ("fl", "fr"):
            w = rig.fore[tag]["wr"]
            s_sign = rig.fore[tag]["side"]
            z_ext = (9.8 if tag == "fl" else 8.8) * sc * f
            x_off = (0.8 if tag == "fl" else -0.2) * s_sign * sc * f
            targs[tag] = {
                "pos": [w[0] + x_off, w[1], w[2] - z_ext],
                "toe": 0.0,
                "paw_yaw": (4.0 if tag == "fl" else -8.0) * f,
                "pole": [s_sign * 0.45, 0.32 * f, 1.0],
            }
        # Hind legs: FULL DOWN FLAT ON THE GROUND! Knees drop LOW beside the flanks (negative Y pole), hocks & paws lie flat!
        for tag in ("bl", "br"):
            a = rig.hind[tag]["an"]
            s_sign = rig.hind[tag]["side"]
            rest_hk = rig.hind[tag]["rest_hock"]
            targs[tag] = {
                "pos": [a[0] + s_sign * 3.4 * sc * f, a[1] + 0.30 * sc * f, a[2] - 7.6 * sc * f],
                "toe": 0.0,
                "hock": rest_hk + (83.5 - rest_hk) * f,
                "yaw": s_sign * 14.0 * f,
                "paw_yaw": s_sign * 14.0 * f,
                "pole": [s_sign * 0.85, -0.18 * f, -0.50],
            }
        if shape:
            shape(p, targs)
        rig.solve_legs(p, targs)
        flick = pulse(t, 2.1, 0.18, 0.55, 0.30) * math.sin(2.0 * math.pi * (t - 2.1) * 2.0) if loop_breath else 0.0
        tail_y = (32.0 + 14.0 * flick) * f
        # Drape mane_chest smoothly onto the ground surface so the whole chest/belly can lie ALL THE WAY DOWN!
        mane_chest_t = [0.0, 2.4 * sc * f, -1.6 * sc * f]
        return p, 86.0 * f, tail_y, -1.0 * br * f, [-20.0 * f + 1.5 * br * f, -2.0 * f, 0.0], mane_chest_t

    clips["rest_down"] = _bake_clip(rig, 0.95, 24, lambda i, t: rest_frame(ease(t / 0.95), t, False), loop=False)
    clips["rest"] = _bake_clip(rig, 3.80, 38, lambda i, t: rest_frame(1.0, t, True), loop=True)
    clips["rest_up"] = _bake_clip(rig, 0.95, 24, lambda i, t: rest_frame(1.0 - ease(t / 0.95), t, False), loop=False)

    # 11b. FEED (a 3.0 s loop; the Motion Studio phase 3, step 5): lying at a carcass, its jaw on a feeding spot
    clips.update(make_feeds(rig, {"male": "lion", "lioness": "lioness", "cub": "lion_cub"}[kind], rest_frame))

    # 11b. REST_STRETCH_UP (3.80s) — Seamless 0-deg Chain from REST -> Slow Concave Bow-Stretch & Yawn -> Convex Cat-Arch Curl -> IDLE!
    def make_rest_stretch_up():
        L, N = 3.80, 76
        def frame(i, t):
            # Starts at t=0.0 BIT-FOR-BIT identical to rest_frame(1.0, 0.0, False)! Ends at t=L in clean standing idle!
            # Phase 1 (0.00..0.55): slow sleepy stir from rest
            # Phase 2 (0.55..1.85): deep CONCAVE play-bow scoop + long lingering 50-deg yawn & curling tongue
            # Phase 3 (1.85..3.00): gradual roll into CONVEX cat back-arch ("Halloween cat" dome curl!)
            # Phase 4 (3.00..3.80): slow relaxed uncurl & blink into proud standing idle!
            r_f = _kscal(t, ((0.0, 1.0), (0.28, 0.94), (0.72, 0.45), (1.20, 0.0), (L, 0.0)))
            bow = _kscal(t, ((0.0, 0.0), (0.32, 0.05), (0.95, 0.85), (1.35, 1.0), (1.72, 0.92), (2.20, 0.0), (L, 0.0)))
            arch = _kscal(t, ((0.0, 0.0), (1.75, 0.0), (2.25, 0.82), (2.58, 1.0), (2.95, 0.65), (3.48, 0.0), (L, 0.0)))
            yawn = _kscal(t, ((0.0, 0.0), (0.68, 0.0), (1.05, 0.88), (1.42, 1.0), (1.68, 0.92), (1.98, 0.0), (L, 0.0)))
            end_blink = pulse(t, 3.12, 0.14, 0.10, 0.16)

            dy = -0.20 * sc - rest_drop * r_f - 2.6 * sc * bow + 0.10 * sc * arch
            dz = 3.2 * sc * bow + 1.0 * sc * arch
            body_rx = rest_body_rx * r_f + 19.5 * bow - 1.5 * arch

            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, dy, dz], "rotation": [body_rx, 0.0, 0.0]},
                # Note +Z spine sign: +rx on waist/hips curls rump UP (concave bow scoop), -rx curls pelvis DOWN (convex cat-arch dome!)
                "chest": {"rotation": [-1.5 * r_f + 8.5 * bow + 11.5 * arch, 2.0 * r_f, 0.0]},
                "waist": {"rotation": [-2.0 * r_f + 12.0 * bow - 17.0 * arch, -1.2 * r_f, 0.0]},
                "hips": {"rotation": [-2.5 * r_f + 10.0 * bow - 15.0 * arch, -1.8 * r_f, 0.0]},
                "neck": {"rotation": [9.5 * r_f - 17.0 * bow + 12.0 * arch, 3.8 * r_f, 0.0]},
                "head": {"rotation": [-2.5 * r_f - 12.0 * bow - 6.0 * yawn - 10.0 * arch, 5.2 * r_f, 2.2 * r_f]},
                "jaw": {"rotation": [50.0 * yawn, 0.0, 0.0]},
                "tongue": {
                    "position": [0.0, -0.10 * sc * yawn, -2.9 * sc * yawn],
                    "rotation": [16.0 * yawn, 0.0, 0.0],
                },
                "tongue_tip": {"rotation": [-32.0 * yawn, 0.0, 0.0]},
                "ear_left": {"rotation": [-8.0 * r_f - 20.0 * yawn - 6.0 * arch, -6.0 * r_f - 12.0 * yawn, -8.0 * r_f + 10.0 * yawn]},
                "ear_right": {"rotation": [-8.0 * r_f - 20.0 * yawn - 6.0 * arch, 6.0 * r_f + 12.0 * yawn, 8.0 * r_f - 10.0 * yawn]},
            }
            rig.set_eyes(p, blink=max(0.92 * yawn, end_blink), squint=0.35 * r_f + 0.55 * bow + 0.25 * arch)
            targs = {}
            # Forepaws glide slowly forward for the deep bow, then step softly back underneath for the convex cat-arch!
            fl_step = pulse(t, 1.90, 0.16, 0.06, 0.16)
            fr_step = pulse(t, 2.12, 0.16, 0.06, 0.16)
            for tag, stp in (("fl", fl_step), ("fr", fr_step)):
                w = rig.fore[tag]["wr"]
                s_sign = rig.fore[tag]["side"]
                z_ext = ((9.8 if tag == "fl" else 8.8) * r_f + 11.4 * bow - 1.6 * arch) * sc
                x_off = ((0.8 if tag == "fl" else -0.2) * r_f + 0.5 * bow) * s_sign * sc
                targs[tag] = {
                    "pos": [w[0] + x_off, w[1] + 2.0 * sc * stp, w[2] - z_ext],
                    "toe": 10.0 * stp,
                    "paw_yaw": (4.0 if tag == "fl" else -8.0) * r_f,
                    "pole": [s_sign * (0.28 + 0.17 * max(r_f, bow)), 0.32 * max(r_f, bow), 1.0],
                }
            for tag in ("bl", "br"):
                a = rig.hind[tag]["an"]
                s_sign = rig.hind[tag]["side"]
                rest_hk = rig.hind[tag]["rest_hock"]
                # Hind legs unfold gradually from full flat rest UP onto tall stretching legs, then tuck gently under arch!
                targs[tag] = {
                    "pos": [a[0] + s_sign * (3.4 * r_f + 0.4 * bow) * sc, a[1] + 0.30 * sc * r_f,
                            a[2] - 7.6 * sc * r_f + 2.4 * sc * bow - 2.6 * sc * arch],
                    "toe": 0.0,
                    "hock": _clamp(rest_hk + (83.5 - rest_hk) * r_f - 4.0 * bow + 14.0 * arch, 12.0, 84.0),
                    "yaw": s_sign * 14.0 * r_f,
                    "paw_yaw": s_sign * 14.0 * r_f,
                    "pole": [s_sign * (0.22 + 0.63 * r_f), -0.18 * r_f, -1.0 + 0.50 * r_f],
                }
            rig.solve_legs(p, targs)
            # Iconic real-cat stretch: slowly splay both front paws & unsheathe claws at the peak of the deep bow & yawn,
            # then smoothly retract them back inside the velvet sheaths as the lion arches up and stands!
            stretch_c = _kscal(t, ((0.0, 0.0), (0.48, 0.0), (0.96, 0.88), (1.68, 0.88), (2.06, 0.0), (L, 0.0)))
            rig.set_claws(p, fl=stretch_c, fr=stretch_c, hook=0.0)
            # Drape mane_chest cleanly clear of ground during rest & deep bow (0.00 px clip on Kalahari too!)
            mane_chest_t = [0.0, (2.4 * r_f + 4.6 * bow) * sc, (-1.6 * r_f - 1.5 * bow) * sc]
            # Tail curls luxuriously high over the back during both the bow (+104 deg) AND the convex cat-arch (+132 deg -> 0.00 px clip!)
            tail_lift = 86.0 * r_f + 104.0 * bow + 132.0 * arch
            tail_p_drv = 6.0 * bow + 4.0 * arch
            mane_drv = [-20.0 * r_f - 12.0 * bow + 8.0 * arch, -2.0 * r_f, 0.0]
            return p, tail_lift, 32.0 * r_f, tail_p_drv, mane_drv, mane_chest_t
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.11, mane_lag=0.095)
    clips["rest_stretch_up"] = make_rest_stretch_up()

    # 12. GROOM (4.40s) — Ground-Up Rework: Pure Smooth Direct-FK Paw Curl + Asymmetric Upward Cat Licks + Cheek Face-Wash!
    def make_groom():
        L, N = 4.40, 88
        # Measure exact rest IK angles on leg_fr so Direct-FK offsets start & end at 0.00 deg seam with `rest`!
        rest_p0 = rest_frame(1.0, 0.0, False)[0]
        fr1_base = list(rest_p0["leg_fr_1"]["rotation"])
        fr2_base = list(rest_p0["leg_fr_2"]["rotation"])
        frP_base = list(rest_p0["leg_fr_paw"]["rotation"])

        def lick_stroke(t, t0, dur):
            """Asymmetric real-cat upward tongue stroke: quick dip -> slow upward drag -> clean retract & swallow pause."""
            if t < t0 or t > t0 + dur:
                return 0.0, 0.0, 0.0
            u = (t - t0) / dur
            # ext: tongue extension envelope (peaks smoothly through the upward drag)
            if u < 0.24:
                ext = ease(u / 0.24)
            elif u < 0.72:
                ext = 1.0
            else:
                ext = 1.0 - ease((u - 0.72) / 0.28)
            # sweep: -1 (dipped onto base of paw) -> +1 (long slow upward drag across paw fur!)
            if u < 0.24:
                sweep = -ease(u / 0.24)
            elif u < 0.72:
                sweep = -1.0 + 2.0 * ease((u - 0.24) / 0.48)
            else:
                sweep = 1.0 - ease((u - 0.72) / 0.28)
            # tip_curl: flat onto paw at start, curls smoothly upward as tongue sweeps up!
            tip = ext * (10.0 + 24.0 * max(0.0, sweep))
            return ext, sweep, tip

        def frame(i, t):
            # Starts & ends at fg = 0 (bit-for-bit identical to rest_frame(1.0, 0.0, False) -> 0.0 deg rest <-> groom blend!)
            fg = _kscal(t, ((0.0, 0.0), (0.68, 1.0), (3.72, 1.0), (L, 0.0)))
            # 3 unhurried asymmetric upward cat licks + a gentle cheek/face rub against the damp paw in the middle!
            e1, s1, c1 = lick_stroke(t, 0.72, 0.78)
            e2, s2, c2 = lick_stroke(t, 1.56, 0.80)
            e3, s3, c3 = lick_stroke(t, 3.16, 0.62)
            ext = max(e1, e2, e3)
            sweep = s1 + s2 + s3
            tip_c = c1 + c2 + c3
            # Gentle affectionate feline cheek/brow rub along the raised damp wrist (2.40..3.12s)!
            rub = pulse(t, 2.38, 0.22, 0.26, 0.26)
            rub_glide = rub * math.sin(math.pi * _clamp((t - 2.46) / 0.56, 0.0, 1.0))

            dy = -0.20 * sc - rest_drop + 0.55 * sc * fg
            neck_dip = (15.5 if is_cub else 18.0) * fg - 4.5 * sweep + 3.0 * rub
            head_dip = (13.5 if is_cub else 16.0) * fg - 7.5 * sweep + 4.0 * rub
            # Turn smoothly right (+ry) right over the low curled right forepaw; tilt cheek warmly during face-wash rub!
            neck_yaw = 3.8 + 8.0 * fg + 4.0 * rub_glide
            head_yaw = 5.2 + 8.0 * fg + 5.5 * rub_glide
            head_roll = 2.2 - 5.0 * fg - 13.0 * rub_glide

            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, dy, 0.0], "rotation": [rest_body_rx, 0.0, 0.0]},
                "chest": {"rotation": [-1.5 + 1.5 * fg, 2.0 + 4.0 * fg + 1.5 * rub_glide, 1.5 * fg]},
                "waist": {"rotation": [-2.0, -1.2 - 1.2 * fg, 0.0]},
                "hips": {"rotation": [-2.5, -1.8, 0.0]},
                "neck": {"rotation": [9.5 + neck_dip, neck_yaw, -2.0 * fg - 2.5 * rub_glide]},
                "head": {"rotation": [-2.5 + head_dip, head_yaw, head_roll]},
                "jaw": {"rotation": [26.0 * ext, 0.0, 0.0]},
                "tongue": {
                    "position": [0.0, -0.14 * sc * ext, -3.45 * sc * ext],
                    "rotation": [26.0 * ext - 6.0 * max(0.0, sweep), 0.0, 0.0],
                },
                "tongue_tip": {"rotation": [tip_c, 0.0, 0.0]},
                "ear_left": {"rotation": [-8.0 - 5.0 * fg - 4.0 * rub, -6.0 - 5.0 * fg, -8.0 - 4.0 * rub]},
                "ear_right": {"rotation": [-8.0 - 5.0 * fg - 4.0 * rub, 6.0 + 5.0 * fg, 8.0 + 4.0 * rub]},
            }
            rig.set_eyes(p, blink=0.88 * rub + pulse(t, 1.48, 0.08, 0.06, 0.10) * 0.7, squint=0.35 + 0.50 * fg)
            targs = {}
            # Left foreleg & both full-down Sleek Sprinter hind legs stay completely flat & peaceful on the ground!
            fl = rig.fore["fl"]["wr"]
            targs["fl"] = {
                "pos": [fl[0] + 0.8 * sc, fl[1], fl[2] - 9.8 * sc],
                "toe": 0.0,
                "paw_yaw": 4.0,
                "pole": [0.45, 0.32, 1.0],
            }
            # Right foreleg: VERIFIED LOW SILKY DIRECT-FK CURL directly under the chin (paw_top y=4.4, tongue y=5.0 -> 0 face clip!)
            paw_yield = 2.0 * ext * max(0.0, -sweep)
            fr1_fk = [
                fr1_base[0] - 6.0 * fg - 2.0 * rub_glide,
                fr1_base[1] + 10.0 * fg - 2.0 * rub_glide,
                fr1_base[2] - 14.0 * fg,
            ]
            fr2_fk = [
                fr2_base[0] - 1.5 * fg - 4.5 * rub_glide + paw_yield,
                fr2_base[1] - 15.0 * fg,
                fr2_base[2] + 18.0 * fg,
            ]
            frP_fk = [
                frP_base[0] - 10.0 * fg - 3.0 * rub_glide + paw_yield * 1.2,
                frP_base[1] + 14.0 * fg,
                frP_base[2] - 22.0 * fg,
            ]
            targs["fr"] = {"fk": (fr1_fk, fr2_fk, frP_fk), "fk_blend": 1.0}
            for tag in ("bl", "br"):
                a = rig.hind[tag]["an"]
                s_sign = rig.hind[tag]["side"]
                targs[tag] = {
                    "pos": [a[0] + s_sign * 3.4 * sc, a[1] + 0.30 * sc, a[2] - 7.6 * sc],
                    "toe": 0.0,
                    "hock": 83.5,
                    "yaw": s_sign * 14.0,
                    "paw_yaw": s_sign * 14.0,
                    "pole": [s_sign * 0.85, -0.18, -0.50],
                }
            rig.solve_legs(p, targs)
            tail_y = 32.0 + 7.5 * fg * math.sin(2.0 * math.pi * t / L)
            mane_chest_t = [0.0, 2.4 * sc, -1.6 * sc]
            mane_drv = [-20.0 + 2.5 * fg + 1.5 * ext, -2.0 - 3.5 * fg - 2.0 * rub_glide, 2.5 * fg + 3.0 * rub_glide]
            return p, 86.0, tail_y, 1.2 * ext, mane_drv, mane_chest_t
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.10, mane_lag=0.09)
    clips["groom"] = make_groom()

    # 13. DRINK (3.00s watering-hole cycle — dips down, laps 4x with clearly visible 2-bone backward J-scoop tongue!)
    def make_drink():
        L, N = 3.00, 44
        def frame(i, t):
            f = pulse(t, 0.05, 0.50, 1.85, 0.55)
            lap_ph = 2.0 * math.pi * (t - 0.50) * 2.15
            lap = f * (0.5 - 0.5 * math.cos(lap_ph))
            dy = -0.22 * sc - 3.4 * sc * f
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, dy, 0.0], "rotation": [9.5 * f, 0.0, 0.0]},
                "chest": {"rotation": [8.0 * f, 0.0, 0.0]},
                "waist": {"rotation": [-4.2 * f, 0.0, 0.0]},
                "hips": {"rotation": [-4.6 * f, 0.0, 0.0]},
                "neck": {"rotation": [23.0 * f + 2.2 * lap, 0.0, 0.0]},
                "head": {"rotation": [13.5 * f + 3.5 * lap, 0.0, 0.0]},
                "jaw": {"rotation": [24.0 * lap, 0.0, 0.0]},
                "tongue": {
                    "position": [0.0, -0.20 * sc * lap, -3.35 * sc * lap],
                    "rotation": [28.0 * lap, 0.0, 0.0],
                },
                "tongue_tip": {"rotation": [40.0 * lap, 0.0, 0.0]},
                "ear_left": {"rotation": [4.0 * f, -8.0 * f + 8.0 * f * math.sin(2.0 * math.pi * t / L), 0.0]},
                "ear_right": {"rotation": [4.0 * f, 8.0 * f + 8.0 * f * math.sin(2.0 * math.pi * t / L), 0.0]},
            }
            rig.set_eyes(p, blink=pulse(t, 1.6, 0.08, 0.06, 0.10), look_y=-0.15 * f)
            targs = rest_targets()
            for tag in ("fl", "fr"):
                s_sign = rig.fore[tag]["side"]
                targs[tag]["pos"][0] += s_sign * 2.0 * sc * f
                targs[tag]["pos"][2] -= 1.6 * sc * f
            rig.solve_legs(p, targs)
            return p, 8.0 * f, 10.0 * math.sin(2.0 * math.pi * t / L), 2.0 * lap, [5.5 * f + 2.0 * lap, 0.0, 0.0]
        return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.09, mane_lag=0.08)
    clips["drink"] = make_drink()

    # 14. DEATH (1.20s hold) — Heavy crumple onto ground with IK paws staying cleanly AT y=0.00 (zero underground clip!)
    def make_death():
        L, N = 1.20, 28
        def frame(i, t):
            u = ease(t / L)
            u2 = ease(_clamp((t - 0.30) / (L - 0.30), 0.0, 1.0))
            drop = (5.2 if kind == "male" else (6.6 if is_lns else 3.4)) * u
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, -0.20 * sc - drop, 0.0], "rotation": [1.5 * u, 0.0, 14.0 * u2]},
                "chest": {"rotation": [2.0 * u, -6.0 * u2, 10.0 * u2]},
                "waist": {"rotation": [-1.5 * u, 5.0 * u2, -5.0 * u2]},
                "hips": {"rotation": [-1.0 * u, 6.0 * u2, -4.0 * u2]},
                "neck": {"rotation": [8.0 * u, -10.0 * u2, 16.0 * u2]},
                "head": {"rotation": [9.0 * u, -12.0 * u2, 20.0 * u2]},
                "jaw": {"rotation": [16.0 * u2, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, -0.75 * sc * u2], "rotation": [14.0 * u2, 0.0, 10.0 * u2]},
                "tongue_tip": {"rotation": [18.0 * u2, 0.0, 8.0 * u2]},
                "ear_left": {"rotation": [-20.0 * u, -12.0 * u, -18.0 * u]},
                "ear_right": {"rotation": [-20.0 * u, 12.0 * u, 18.0 * u]},
            }
            rig.set_eyes(p, blink=u)
            targs = {}
            for tag in ("fl", "fr"):
                w = rig.fore[tag]["wr"]
                s_sign = rig.fore[tag]["side"]
                # Paws splay limply out flat ON y=0 as the lion crumples!
                targs[tag] = {
                    "pos": [w[0] + s_sign * (3.2 if s_sign > 0 else 4.6) * sc * u, w[1], w[2] - 6.8 * sc * u],
                    "toe": 0.0,
                    "pole": [s_sign * 0.65, 0.25, 1.0],
                }
            for tag in ("bl", "br"):
                a = rig.hind[tag]["an"]
                s_sign = rig.hind[tag]["side"]
                targs[tag] = {
                    "pos": [a[0] + s_sign * (3.6 if s_sign > 0 else 4.8) * sc * u, a[1], a[2] - 4.2 * sc * u],
                    "toe": 0.0,
                    "hock": rig.hind[tag]["rest_hock"] + 42.0 * u,
                    "pole": [s_sign * 0.85, 0.35, -0.75],
                }
            rig.solve_legs(p, targs)
            return p, 34.0 * u, -24.0 * u2, 0.0, [-10.0 * u, -6.0 * u2, 12.0 * u2]
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.08, mane_lag=0.08)
    clips["death"] = make_death()

    # ================================================================================================
    # 14b. REAL-CAT FACIAL EMOTIONS & BODY LANGUAGE SUITE (8 Full-Body Moods + 8 Layered Face Overlays)
    # ================================================================================================
    def _emotion_face_eval(mood, t, L):
        """Return facial expression dict (eyes kwargs, ear_l, ear_r, jaw, tongue, tongue_tip) for any of the 8 feline moods."""
        ph = 2.0 * math.pi * t / L
        s1, c1 = math.sin(ph), math.cos(ph)
        s2 = math.sin(2.0 * ph)
        if mood == "content":
            # Soft almond eyes, warm trusting 0.65s cat slow-blink, level relaxed brow, neutral ears
            sb = pulse(t, 1.05, 0.24, 0.22, 0.26)
            return {
                "eyes": dict(blink=sb * 0.96, squint=0.40, look_x=0.10 * s1, brow_y=0.06, brow_tilt=-3.5, lid_tilt=-2.0, pupil_scale=1.02),
                "ear_l": [-4.0, -7.0 - 2.0 * s1, -7.0],
                "ear_r": [-4.0, 7.0 + 2.0 * s1, 7.0],
                "jaw": 0.0, "tg_z": 0.0, "tg_rx": 0.0, "tip_rx": 0.0,
            }
        if mood == "alert":
            # Wide bright eyes, lifted focused brows, sharp steady horizon tracking, twin forward radar ears + wind-check sniff
            scan = math.sin(ph - 0.3)
            snf = pulse(t, 0.88, 0.10, 0.38, 0.12) * (0.5 - 0.5 * math.cos(2.0 * math.pi * (t - 0.88) * 4.4))
            return {
                "eyes": dict(blink=pulse(t, 2.45, 0.06, 0.04, 0.08), wide=0.88, look_x=0.26 * scan, look_y=0.05,
                             brow_y=0.26, brow_tilt=2.5, lid_tilt=0.0, pupil_scale=0.98,
                             nose_sniff=0.72 * snf, nose_flare=0.60 * snf),
                "ear_l": [10.0, 14.0 - 4.0 * scan, 8.0],
                "ear_r": [10.0, -14.0 - 4.0 * scan, -8.0],
                "jaw": 0.0, "tg_z": 0.0, "tg_rx": 0.0, "tip_rx": 0.0,
            }
        if mood == "curious":
            # Wide bright dilated eyes (1.34x), inquisitive asymmetric brow lift, independent radar ears + cute double-sniff bursts!
            look = math.sin(ph)
            tw_l = pulse(t, 0.80, 0.10, 0.18, 0.14)
            tw_r = pulse(t, 2.10, 0.10, 0.18, 0.14)
            sn1 = pulse(t, 0.45, 0.10, 0.52, 0.12) * (0.5 - 0.5 * math.cos(2.0 * math.pi * (t - 0.45) * 4.6))
            sn2 = pulse(t, 2.05, 0.10, 0.52, 0.12) * (0.5 - 0.5 * math.cos(2.0 * math.pi * (t - 2.05) * 4.6))
            snf = max(sn1, sn2)
            return {
                "eyes": dict(blink=pulse(t, 2.75, 0.07, 0.04, 0.09), wide=0.92, look_x=0.28 * look, look_y=0.08 * c1,
                             brow_y=0.30, brow_tilt=-4.0, lid_tilt=-2.0, pupil_scale=1.34, brow_asym=0.14 * math.sin(ph),
                             nose_sniff=0.92 * snf, nose_flare=0.80 * snf),
                "ear_l": [12.0 + 5.0 * tw_l, 18.0 - 12.0 * tw_l, 6.0],
                "ear_r": [12.0 + 5.0 * tw_r, -18.0 + 12.0 * tw_r, -6.0],
                "jaw": 2.5 * max(0.0, s1), "tg_z": 0.0, "tg_rx": 0.0, "tip_rx": 0.0,
            }
        if mood == "angry":
            if is_cub:
                # Kitten Spit-Hiss! Startled wide bright eyes, flat pinned ears, puffed spit-hiss & nose wrinkle!
                spit = pulse(t, 0.55, 0.14, 0.45, 0.22) + pulse(t, 1.75, 0.14, 0.35, 0.20)
                spit = _clamp(spit, 0.0, 1.0)
                return {
                    "eyes": dict(blink=pulse(t, 2.45, 0.06, 0.04, 0.06), wide=0.82, look_x=0.08 * s2, look_y=0.02,
                                 brow_y=-0.12, brow_tilt=11.0, lid_tilt=7.0, pupil_scale=1.34,
                                 nose_sniff=0.65 * spit, nose_flare=0.75 * spit),
                    "ear_l": [-36.0, -26.0, 26.0],
                    "ear_r": [-36.0, 26.0, -26.0],
                    "jaw": 10.0 + 18.0 * spit, "tg_z": 0.0, "tg_rx": 6.0 * spit, "tip_rx": 10.0 * spit,
                }
            # Hard unblinking narrow glare, heavy inward-down predator brow (\ /), constricted sharp pupils (0.76x), airplane ears, snarling nose wrinkle!
            snarl = 0.5 + 0.5 * math.sin(ph)
            return {
                "eyes": dict(blink=0.0, squint=0.72, look_x=0.04 * s2, look_y=-0.04,
                             brow_y=-0.34, brow_tilt=15.0, lid_tilt=12.5, pupil_scale=0.76,
                             nose_sniff=0.55 + 0.35 * snarl, nose_flare=0.65 + 0.35 * snarl),
                "ear_l": [-28.0 - 4.0 * snarl, -26.0, 24.0],
                "ear_r": [-28.0 - 4.0 * snarl, 26.0, -24.0],
                "jaw": 14.0 + 7.5 * snarl, "tg_z": 0.0, "tg_rx": 3.0 * snarl, "tip_rx": 6.0 * snarl,
            }
        if mood == "fearful":
            # Wide "saucer eyes", inner-up worried brow (/ \), massive dilated adrenaline pupils (1.52x), darting gaze, pinned flat ears, defensive hiss!
            dart = math.sin(2.0 * ph) * 0.26
            hiss = pulse(t, 0.65, 0.16, 0.55, 0.24)
            return {
                "eyes": dict(blink=pulse(t, 2.25, 0.05, 0.04, 0.06), wide=1.0, look_x=dart, look_y=0.04,
                             brow_y=0.22, brow_tilt=-14.5, lid_tilt=-6.0, pupil_scale=1.52,
                             nose_sniff=0.30 + 0.45 * hiss, nose_flare=0.55 + 0.45 * hiss),
                "ear_l": [-38.0, -24.0, 26.0],
                "ear_r": [-38.0, 24.0, -26.0],
                "jaw": 10.0 + 18.0 * hiss, "tg_z": 0.0, "tg_rx": 5.0 * hiss, "tip_rx": 10.0 * hiss,
            }
        if mood == "submissive":
            # Averting gaze sideways & down, soft squinted lids, inner-up appeasing brow, frequent nervous blinks, dropped sideways ears
            nb = pulse(t, 0.70, 0.07, 0.05, 0.08) + pulse(t, 1.95, 0.07, 0.06, 0.09) + pulse(t, 2.85, 0.07, 0.04, 0.08)
            return {
                "eyes": dict(blink=min(1.0, nb), squint=0.52, look_x=0.28 + 0.05 * s1, look_y=-0.15,
                             brow_y=0.14, brow_tilt=-13.5, lid_tilt=-8.5, pupil_scale=1.18),
                "ear_l": [-14.0, -18.0, -22.0],
                "ear_r": [-14.0, 18.0, 22.0],
                "jaw": 0.0, "tg_z": 0.0, "tg_rx": 0.0, "tip_rx": 0.0,
            }
        if mood == "sleepy":
            # Heavy drooping lids (84% closed), long sluggish 1.2s blink drift, soft low brow, drowsy drooping ears
            drift = pulse(t, 1.20, 0.55, 0.65, 0.55)
            return {
                "eyes": dict(blink=drift * 0.98, squint=0.84, look_x=0.04 * s1, look_y=-0.14,
                             brow_y=-0.18, brow_tilt=-4.0, lid_tilt=-3.0, pupil_scale=0.95),
                "ear_l": [-10.0 - 4.0 * drift, -12.0, -16.0 - 4.0 * drift],
                "ear_r": [-10.0 - 4.0 * drift, 12.0, 16.0 + 4.0 * drift],
                "jaw": 0.0, "tg_z": 0.0, "tg_rx": 0.0, "tip_rx": 0.0,
            }
        # mood == "flehmen" — Iconic real-lion scent-sampling face! Active nose-wrinkle sniffing bursts into the savanna breeze!
        taste = pulse(t, 1.40, 0.20, 0.35, 0.25)
        sn_f1 = pulse(t, 0.30, 0.14, 0.78, 0.16) * (0.5 - 0.5 * math.cos(2.0 * math.pi * (t - 0.30) * 3.8))
        sn_f2 = pulse(t, 2.05, 0.14, 0.72, 0.16) * (0.5 - 0.5 * math.cos(2.0 * math.pi * (t - 2.05) * 3.8))
        sn_fl = 0.42 + 0.68 * max(sn_f1, sn_f2)
        return {
            "eyes": dict(blink=pulse(t, 2.55, 0.14, 0.14, 0.18) * 0.92, squint=0.56, look_x=0.06 * s1, look_y=0.12,
                         brow_y=0.16, brow_tilt=-6.0, lid_tilt=-4.0, pupil_scale=1.08,
                         nose_sniff=sn_fl, nose_flare=0.45 + 0.55 * max(sn_f1, sn_f2)),
            "ear_l": [-6.0, -14.0, -10.0],
            "ear_r": [-6.0, 14.0, 10.0],
            "jaw": 14.0 + 5.0 * taste, "tg_z": -1.1 * sc * taste, "tg_rx": 10.0 * taste, "tip_rx": -14.0 * taste,
        }

    def make_face_overlay(mood, L, N):
        """Face-only looping overlay clip (touches ONLY brow, lid, pupil, ear, jaw, tongue) for parallel GeckoLib controller!"""
        def pose_at(t):
            fe = _emotion_face_eval(mood, t, L)
            p = {
                "ear_left": {"rotation": fe["ear_l"]},
                "ear_right": {"rotation": fe["ear_r"]},
                "jaw": {"rotation": [fe["jaw"], 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, fe["tg_z"]], "rotation": [fe["tg_rx"], 0.0, 0.0]},
                "tongue_tip": {"rotation": [fe["tip_rx"], 0.0, 0.0]},
            }
            rig.set_eyes(p, **fe["eyes"])
            return p
        made = clip(L, pose_at, N, loop=True)
        _unwrap(made)
        return made

    def make_mood_body(mood, L, N):
        """Full-body real-cat body-language & facial emotion loop (eyes + head carriage + spine posture + tail language!)."""
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            br = math.sin(ph)
            sw = math.sin(ph - 0.4)
            fe = _emotion_face_eval(mood, t, L)

            # Default standing neutral baseline
            bx, by, bz = 0.0, -0.18 * sc + 0.22 * sc * br, 0.0
            body_r = [0.3 * br, 0.0, 0.0]
            chest_r = [-1.0 * br, 1.5 * sw, 0.0]
            waist_r = [0.5 * br, -0.8 * sw, 0.0]
            hips_r = [-0.4 * br, -0.5 * sw, 0.0]
            neck_r = [-1.0 * br, 3.0 * sw, 0.0]
            head_r = [1.0 * br, 4.0 * sw, 1.5 * sw]
            x_pad = 0.20
            hock_add = 0.0
            paw_z_f, paw_z_b = 0.0, 0.0
            tail_lift, tail_y, tail_p = 6.0, 7.0 * br, 1.5 * br
            mane_drv = [2.0 * br, -2.5 * sw, 1.0 * sw]
            mane_chest_t = None

            if mood == "content":
                by = -0.24 * sc + 0.28 * sc * br
                neck_r = [1.5 - 1.2 * br, 3.5 * sw, 2.0 * sw]
                head_r = [-1.0 + 1.0 * br, 4.5 * sw, 5.0 * math.sin(ph)]
                tail_lift = 10.0 + 4.0 * br
                tail_y = 12.0 * math.sin(ph)
                mane_drv = [2.5 * br, -3.0 * sw, 2.0 * sw]
            elif mood == "alert":
                by = 0.25 * sc + 0.14 * sc * br
                body_r = [-2.2, 0.0, 0.0]
                chest_r = [-4.2 - 0.8 * br, 2.5 * sw, 0.0]
                waist_r = [1.8, -1.5 * sw, 0.0]
                hips_r = [1.0, -0.8 * sw, 0.0]
                neck_r = [-10.5 - 0.6 * br, 6.5 * sw, 0.0]
                head_r = [6.5 + 0.6 * br, 9.5 * sw, 0.0]
                x_pad = 0.15
                tail_lift = 18.0
                tip_tw = pulse(t, 1.55, 0.12, 0.30, 0.18) * math.sin(2.0 * math.pi * (t - 1.55) * 3.0)
                tail_y = 14.0 * tip_tw
                mane_drv = [-3.0, -5.0 * sw, 0.0]
            elif mood == "curious":
                # Neck cranes forward + unmistakable curious cat head tilt (+15 deg -> -15 deg!)
                tilt = math.sin(ph)
                by = -0.75 * sc + 0.18 * sc * br
                bz = -1.35 * sc - 0.30 * sc * math.cos(ph)
                body_r = [2.6, 0.0, 0.0]
                chest_r = [3.5, 2.5 * tilt, 1.5 * tilt]
                waist_r = [-1.8, -1.8 * tilt, 0.0]
                hips_r = [-1.2, -1.0 * tilt, 0.0]
                neck_r = [8.5 - 1.0 * br, 5.5 * tilt, 4.0 * tilt]
                head_r = [-8.0 + 1.5 * math.cos(ph), 7.5 * tilt, 15.0 * tilt]
                x_pad = 0.40
                paw_z_f = -0.6 * sc
                tail_lift = 16.0 + 4.0 * math.cos(ph)
                tail_y = 12.0 * math.sin(ph)
                mane_drv = [3.5, -5.5 * tilt, -4.0 * tilt]
            elif mood == "angry":
                if is_cub:
                    # Puffed-up Kitten Spit-Hiss: high convex Halloween-cat back arch, crouched wide stance & tall bristling tail!
                    puff = 0.5 + 0.5 * math.sin(2.0 * ph)
                    by = -0.35 * sc + 0.25 * sc * puff
                    bz = 0.45 * sc
                    body_r = [1.5, 0.0, 0.0]
                    chest_r = [10.0 + 2.0 * puff, 2.0 * math.sin(ph), 0.0]
                    waist_r = [-15.0 - 2.0 * puff, -2.0 * math.sin(ph), 0.0]
                    hips_r = [-11.5, 0.0, 0.0]
                    neck_r = [-6.0, 0.0, 0.0]
                    head_r = [5.0, 0.0, 0.0]
                    x_pad = 0.85
                    hock_add = 10.0
                    tail_lift = 96.0 + 8.0 * puff
                    tail_y = 14.0 * math.sin(2.0 * ph)
                    mane_drv = [0.0, 0.0, 0.0]
                else:
                    # Tall rigid dominant stance, lowered forward-thrust head guarding throat, heavy lashing tail!
                    lash = math.sin(2.0 * ph)
                    by = 0.15 * sc + 0.16 * sc * math.sin(2.0 * ph)
                    bz = -1.45 * sc
                    body_r = [2.6, 1.8 * lash, 0.0]
                    chest_r = [5.2, 3.0 * lash, 0.0]
                    waist_r = [-6.0, -3.5 * lash, 0.0]
                    hips_r = [-4.5, -2.5 * lash, 0.0]
                    neck_r = [14.0 - 1.5 * br, -2.5 * lash, 0.0]
                    head_r = [-15.5 + 1.5 * br, -2.0 * lash, 0.0]
                    x_pad = 1.15
                    paw_z_f = -1.0 * sc
                    tail_lift = 22.0 + 4.0 * abs(lash)
                    tail_y = 30.0 * lash
                    tail_p = 5.0 * abs(lash)
                    mane_drv = [-6.0 + 2.0 * br, 4.5 * lash, 0.0]
            elif mood == "fearful":
                # Pulled tightly back & crouched low with defensive convex back-arch & guarded tucked tail!
                trem = math.sin(4.0 * ph)
                by = -2.55 * sc + 0.14 * sc * br
                bz = 2.45 * sc
                body_r = [-2.5, 0.0, 0.0]
                chest_r = [8.5, 1.5 * trem, 0.0]
                waist_r = [-11.5, -1.5 * trem, 0.0]
                hips_r = [-9.5, 0.0, 0.0]
                neck_r = [-6.5, 3.0 * math.sin(ph), 0.0]
                head_r = [11.5, 4.5 * math.sin(ph), 2.5 * math.sin(ph)]
                x_pad = 0.85
                hock_add = 18.0
                paw_z_b = 0.6 * sc
                # Tail tucked low behind haunches while staying strictly above y=0 (tail_lift=68 offsets -21 deg hips curl!)
                tail_lift = 68.0
                tail_y = 6.0 * trem
                mane_drv = [5.0, -2.0 * trem, 0.0]
                mane_chest_t = [0.0, 0.8 * sc, 0.0]
            elif mood == "submissive":
                # Low compressed appeasing posture, head dipped low & turned politely away from dominant gaze!
                by = -1.45 * sc + 0.14 * sc * br
                bz = 0.60 * sc
                body_r = [1.6, -1.8, 0.0]
                chest_r = [2.4, -2.6, -1.0]
                waist_r = [-1.6, 1.6, 0.0]
                hips_r = [-1.2, 1.2, 0.0]
                neck_r = [8.5 + 1.5 * br, -6.5 - 1.2 * br, 3.0]
                head_r = [5.0 + 1.2 * br, -10.0 - 1.8 * br, 8.0]
                x_pad = 0.42
                hock_add = 10.0
                tail_lift = 18.0
                tail_y = -10.0 + 3.0 * br
                mane_drv = [3.5, 5.0, 2.0]
                mane_chest_t = [0.0, 0.4 * sc, -0.2 * sc]
            elif mood == "sleepy":
                # Heavy drooping head nods, relaxed low shoulders, deep sluggish breaths
                nod = 0.5 - 0.5 * math.cos(ph)
                by = -0.80 * sc + 0.30 * sc * br
                body_r = [1.8 + 0.6 * nod, 0.0, 0.0]
                chest_r = [2.8 + 1.0 * nod - 1.2 * br, 0.0, 0.0]
                waist_r = [-1.2 + 0.6 * br, 0.0, 0.0]
                hips_r = [-1.2, 0.0, 0.0]
                neck_r = [9.5 + 5.5 * nod, 2.0 * sw, 1.5 * sw]
                head_r = [6.5 + 7.5 * nod, 2.5 * sw, 3.0 * sw]
                x_pad = 0.22
                hock_add = 5.0
                tail_lift = 12.0
                tail_y = 4.0 * br
                mane_drv = [4.0 + 2.5 * nod, 0.0, 0.0]
            else:  # flehmen
                # Iconic lion scent-sampling: neck stretched forward & UP, nose high in the breeze, dreamy swaying head!
                sniff = math.sin(ph)
                by = -0.12 * sc + 0.18 * sc * br
                bz = -0.85 * sc
                body_r = [-2.0, 0.0, 0.0]
                chest_r = [-2.8 - 0.8 * br, 1.8 * sniff, 0.0]
                waist_r = [1.2, -1.0 * sniff, 0.0]
                hips_r = [0.8, -0.6 * sniff, 0.0]
                neck_r = [-10.5 - 1.8 * br, 4.0 * sniff, 1.2 * sniff]
                head_r = [-12.5 - 2.0 * math.cos(ph), 4.8 * sniff, 3.0 * sniff]
                x_pad = 0.25
                tail_lift = 18.0
                tail_y = 8.0 * sniff
                mane_drv = [-4.5, -3.0 * sniff, 0.0]

            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [bx, by, bz], "rotation": body_r},
                "chest": {"rotation": chest_r},
                "waist": {"rotation": waist_r},
                "hips": {"rotation": hips_r},
                "neck": {"rotation": neck_r},
                "head": {"rotation": head_r},
                "jaw": {"rotation": [fe["jaw"], 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, fe["tg_z"]], "rotation": [fe["tg_rx"], 0.0, 0.0]},
                "tongue_tip": {"rotation": [fe["tip_rx"], 0.0, 0.0]},
                "ear_left": {"rotation": fe["ear_l"]},
                "ear_right": {"rotation": fe["ear_r"]},
            }
            rig.set_eyes(p, **fe["eyes"])
            targs = rest_targets(x_pad=x_pad)
            for tag in ("fl", "fr"):
                targs[tag]["pos"][2] += paw_z_f
            for tag in ("bl", "br"):
                targs[tag]["pos"][2] += paw_z_b
                targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + hock_add, 12.0, 56.0)
            rig.solve_legs(p, targs)
            if mood == "angry":
                c_ang = 0.72 + 0.10 * abs(math.sin(2.0 * ph))
                rig.set_claws(p, fl=c_ang, fr=c_ang, hook=0.0)
            elif mood == "fearful":
                c_fr = 0.88 + 0.08 * abs(math.sin(4.0 * ph))
                rig.set_claws(p, fl=c_fr, fr=c_fr, bl=0.45, br=0.45, hook=0.0)
            if mane_chest_t is not None:
                return p, tail_lift, tail_y, tail_p, mane_drv, mane_chest_t
            return p, tail_lift, tail_y, tail_p, mane_drv

        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.08, mane_lag=0.075)

    for _mood, _len, _steps in (
        ("content", 3.80, 38),
        ("alert", 3.00, 30),
        ("curious", 3.40, 34),
        ("angry", 2.80, 32),
        ("fearful", 2.80, 32),
        ("submissive", 3.40, 34),
        ("sleepy", 4.20, 42),
        ("flehmen", 3.60, 36),
    ):
        if is_cub and _mood == "flehmen":
            continue
        clips[f"mood_{_mood}"] = make_mood_body(_mood, _len, _steps)
        clips[f"face_{_mood}"] = make_face_overlay(_mood, _len, _steps)

    # ================================================================================================
    # 14c. SIGNATURE LIONESS & CUB BEHAVIORS (Ambush Freeze, Maternal Cub Groom, Play-Pounce, Wrestle, Mama Nuzzle)
    # ================================================================================================
    if is_lns:
        # L1. AMBUSH_FREEZE (2.40s loop) — Belly-flat tall-grass hunter freeze (chains smoothly w/ stalk -> stalk_leap_bite!)
        def make_ambush_freeze():
            L, N = 2.40, 36
            def frame(i, t):
                ph = 2.0 * math.pi * t / L
                br = math.sin(ph)
                # Subtle hypnotic tail-tip hunter twitch!
                tw = math.sin(2.0 * ph) * 16.0
                dy = -5.10 * sc + 0.10 * sc * br
                p = {
                    "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                    "body": {"position": [0.0, dy, 0.0], "rotation": [1.8, 0.0, 0.0]},
                    "chest": {"rotation": [8.0 - 0.5 * br, 0.0, 0.0]},
                    "waist": {"rotation": [-3.0 + 0.4 * br, 0.0, 0.0]},
                    "hips": {"rotation": [-3.2, 0.0, 0.0]},
                    "neck": {"rotation": [12.0, 0.0, 0.0]},
                    "head": {"rotation": [-19.5, 1.5 * math.sin(ph), 0.0]},
                    "jaw": {"rotation": [0.0, 0.0, 0.0]},
                    "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                    "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
                    "ear_left": {"rotation": [-12.0, 12.0, 6.0]},
                    "ear_right": {"rotation": [-12.0, -12.0, -6.0]},
                }
                rig.set_eyes(p, wide=0.65, squint=0.35, pupil_scale=1.34, brow_y=-0.12, brow_tilt=6.0, look_x=0.06 * math.sin(ph))
                targs = rest_targets(x_pad=1.25)
                for tag in ("fl", "fr"):
                    s_sign = rig.fore[tag]["side"]
                    targs[tag]["pos"][2] -= 2.2 * sc
                    targs[tag]["pole"] = [s_sign * 0.58, 0.18, 1.0]
                for tag in ("bl", "br"):
                    targs[tag]["hock"] = 48.0
                rig.solve_legs(p, targs)
                return p, 24.0, tw, 2.0 * br, [0.0, 0.0, 0.0]
            return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.07, mane_lag=0.07)

        # L2. CUB_GROOM (3.20s) — Affectionate maternal dip & 3 slow gentle upward tongue licks onto a cub in front!
        def make_cub_groom():
            L, N = 3.20, 64
            def lick(t, t0, dur):
                if t < t0 or t > t0 + dur:
                    return 0.0, 0.0
                u = (t - t0) / dur
                if u < 0.25:
                    e = ease(u / 0.25)
                    return e, -e
                if u < 0.72:
                    return 1.0, -1.0 + 2.0 * ease((u - 0.25) / 0.47)
                e = 1.0 - ease((u - 0.72) / 0.28)
                return e, e

            def frame(i, t):
                dip = _kscal(t, ((0.0, 0.0), (0.48, 1.0), (2.68, 1.0), (L, 0.0)))
                e1, s1 = lick(t, 0.56, 0.64)
                e2, s2 = lick(t, 1.28, 0.66)
                e3, s3 = lick(t, 2.02, 0.62)
                ext = max(e1, e2, e3)
                swp = s1 + s2 + s3
                p = {
                    "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                    "body": {"position": [0.0, -0.20 * sc - 1.6 * sc * dip, -1.2 * sc * dip], "rotation": [5.5 * dip, 0.0, 0.0]},
                    "chest": {"rotation": [5.0 * dip - 1.5 * swp, 0.0, 0.0]},
                    "waist": {"rotation": [-3.0 * dip, 0.0, 0.0]},
                    "hips": {"rotation": [-2.2 * dip, 0.0, 0.0]},
                    "neck": {"rotation": [26.0 * dip - 6.5 * swp, 3.0 * dip, 0.0]},
                    "head": {"rotation": [12.0 * dip - 9.0 * swp, 4.0 * dip, 5.0 * dip], },
                    "jaw": {"rotation": [16.0 * ext, 0.0, 0.0]},
                    "tongue": {"position": [0.0, -0.06 * sc * ext, -2.2 * sc * ext], "rotation": [14.0 * ext, 0.0, 0.0]},
                    "tongue_tip": {"rotation": [(8.0 + 20.0 * max(0.0, swp)) * ext, 0.0, 0.0]},
                    "ear_left": {"rotation": [-8.0 * dip, -10.0 * dip, -10.0 * dip]},
                    "ear_right": {"rotation": [-8.0 * dip, 10.0 * dip, 10.0 * dip]},
                }
                rig.set_eyes(p, blink=pulse(t, 1.50, 0.14, 0.22, 0.16) * 0.90, squint=0.52 * dip, look_y=-0.12 * dip)
                targs = rest_targets(x_pad=0.55 * dip)
                rig.solve_legs(p, targs)
                return p, 34.0 * dip, 12.0 * dip * math.sin(2.0 * math.pi * t / L), 2.0 * dip, [0.0, 0.0, 0.0]
            return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.09, mane_lag=0.09)

        clips["ambush_freeze"] = make_ambush_freeze()
        clips["cub_groom"] = make_cub_groom()

    if is_cub:
        # C1. PLAY_POUNCE (1.65s) — Low play-crouch -> iconic rapid BUTT-WIGGLE -> high goofy spring-leap -> paw splat!
        def make_play_pounce():
            L, N = 1.65, 50
            def frame(i, t):
                crouch = _kscal(t, ((0.0, 0.0), (0.18, 1.0), (0.64, 1.0), (0.86, 0.0), (L, 0.0)))
                wig_env = pulse(t, 0.20, 0.08, 0.34, 0.10)
                wig = wig_env * math.sin(2.0 * math.pi * (t - 0.20) * 5.2)
                u_air = _clamp((t - 0.68) / 0.50, 0.0, 1.0)
                arc = math.sin(math.pi * u_air) if 0.68 <= t <= 1.18 else 0.0
                splat = pulse(t, 1.16, 0.08, 0.10, 0.24)
                dy = -0.18 * sc - 3.6 * sc * crouch + 8.4 * sc * arc - 2.2 * sc * splat
                dz = 2.0 * sc * crouch - 4.0 * sc * arc - 2.4 * sc * splat
                body_p = 10.0 * crouch - 18.0 * math.sin(math.pi * _clamp((t - 0.68) / 0.26, 0.0, 1.0)) + 13.0 * math.sin(math.pi * _clamp((t - 0.94) / 0.24, 0.0, 1.0)) + 4.0 * splat
                p = {
                    "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                    "body": {"position": [0.45 * sc * wig, dy, dz], "rotation": [body_p, 4.0 * wig, 5.0 * wig]},
                    "chest": {"rotation": [8.0 * crouch - 10.0 * arc + 5.0 * splat, -4.0 * wig, -3.0 * wig]},
                    "waist": {"rotation": [-5.0 * crouch + 12.0 * arc, 16.0 * wig, 10.0 * wig]},
                    "hips": {"rotation": [-6.0 * crouch + 10.0 * arc, 20.0 * wig, 12.0 * wig]},
                    "neck": {"rotation": [11.0 * crouch - 5.0 * arc + 4.0 * splat, -5.0 * wig, 0.0]},
                    "head": {"rotation": [-19.0 * crouch + 4.0 * arc - 6.0 * splat, -4.0 * wig, 0.0]},
                    "jaw": {"rotation": [24.0 * arc + 10.0 * splat, 0.0, 0.0]},
                    "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [4.0 * arc, 0.0, 0.0]},
                    "tongue_tip": {"rotation": [8.0 * arc, 0.0, 0.0]},
                    "ear_left": {"rotation": [12.0 * crouch - 6.0 * arc, 14.0 * crouch, 8.0 * crouch]},
                    "ear_right": {"rotation": [12.0 * crouch - 6.0 * arc, -14.0 * crouch, -8.0 * crouch]},
                }
                rig.set_eyes(p, wide=0.90 * max(crouch, arc), pupil_scale=1.36, brow_y=0.20 * crouch)
                targs = rest_targets(x_pad=0.80 * max(crouch, splat))
                for tag in ("fl", "fr"):
                    w = rig.fore[tag]["wr"]
                    s_sign = rig.fore[tag]["side"]
                    targs[tag] = {
                        "pos": [w[0] + s_sign * (0.9 * crouch + 1.8 * arc + 1.2 * splat) * sc,
                                w[1] + 7.2 * sc * arc,
                                w[2] - (1.8 * crouch + 6.8 * arc + 2.4 * splat) * sc],
                        "toe": 26.0 * arc,
                    }
                for tag in ("bl", "br"):
                    a = rig.hind[tag]["an"]
                    s_sign = rig.hind[tag]["side"]
                    targs[tag] = {
                        "pos": [a[0] + s_sign * 0.9 * sc * crouch, a[1] + 5.8 * sc * arc, a[2] + 6.2 * sc * arc],
                        "toe": 28.0 * arc,
                        "hock": _clamp(rig.hind[tag]["rest_hock"] + 22.0 * crouch - 10.0 * arc + 10.0 * splat, 12.0, 56.0),
                    }
                rig.solve_legs(p, targs)
                # Tiny playful milk claws flash 55% mid-leap & retract on splat!
                f_c = 0.55 * _kscal(t, ((0.0, 0.0), (0.68, 0.0), (0.80, 1.0), (1.14, 1.0), (1.30, 0.0), (L, 0.0)))
                rig.set_claws(p, fl=f_c, fr=f_c, hook=0.8 if arc > 0.2 else 0.0)
                return p, 52.0 * crouch + 40.0 * arc + 24.0 * splat, 32.0 * wig, -4.0 * arc, [0.0, 0.0, 0.0]
            return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.055, mane_lag=0.055)

        # C2. PLAY_WRESTLE (2.20s) — Playful side-shoulder flop & alternating front paw-bats in the air (0.00 px clip!)
        def make_play_wrestle():
            L, N = 2.20, 56
            def frame(i, t):
                # Lift front paws cleanly UP before rolling onto shoulder & unroll before planting (0.00 px clip!)
                lift_f = _kscal(t, ((0.0, 0.0), (0.18, 1.0), (1.92, 1.0), (L, 0.0)))
                flop = _kscal(t, ((0.0, 0.0), (0.14, 0.0), (0.46, 1.0), (1.64, 1.0), (1.98, 0.0), (L, 0.0)))
                bat_l = flop * math.sin(2.0 * math.pi * (t - 0.44) * 2.4)
                bat_r = flop * math.sin(2.0 * math.pi * (t - 0.44) * 2.4 + math.pi)
                p = {
                    "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                    "body": {"position": [0.7 * sc * flop, -0.18 * sc - 1.8 * sc * flop, 0.0],
                             "rotation": [-12.0 * flop, 6.0 * flop, 34.0 * flop]},
                    "chest": {"rotation": [-6.0 * flop, 8.0 * flop + 6.0 * bat_l, 10.0 * flop]},
                    "waist": {"rotation": [-4.0 * flop, -8.0 * flop, -12.0 * flop]},
                    "hips": {"rotation": [-6.0 * flop, -6.0 * flop, -14.0 * flop]},
                    "neck": {"rotation": [10.0 * flop, -14.0 * flop, -18.0 * flop]},
                    "head": {"rotation": [8.0 * flop, -10.0 * flop + 6.0 * bat_l, -20.0 * flop]},
                    "jaw": {"rotation": [22.0 * flop + 6.0 * abs(bat_l), 0.0, 0.0]},
                    "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [5.0 * flop, 0.0, 0.0]},
                    "tongue_tip": {"rotation": [10.0 * flop, 0.0, 0.0]},
                    "ear_left": {"rotation": [-12.0 * flop, -10.0 * flop, -14.0 * flop]},
                    "ear_right": {"rotation": [-8.0 * flop, 10.0 * flop, 12.0 * flop]},
                }
                rig.set_eyes(p, wide=0.88 * flop, pupil_scale=1.35, brow_y=0.16 * flop, look_x=-0.16 * flop)
                targs = rest_targets(x_pad=0.75 * flop)
                for tag in ("bl", "br"):
                    s_sign = rig.hind[tag]["side"]
                    targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + 22.0 * flop, 12.0, 56.0)
                    targs[tag]["pole"] = [s_sign * 0.60, 0.25, -0.85]
                fb = _kscal(t, ((0.0, 0.0), (0.06, 1.0), (2.06, 1.0), (L, 0.0)))
                fl1 = [-68.0 * lift_f + 16.0 * bat_l, -18.0 * flop, -14.0 * flop]
                fl2 = [34.0 * lift_f + 22.0 * bat_l, -24.0 * flop, 0.0]
                flP = [26.0 * flop + 12.0 * bat_l, 0.0, 0.0]
                fr1 = [-72.0 * lift_f + 16.0 * bat_r, 22.0 * flop, 16.0 * flop]
                fr2 = [32.0 * lift_f + 22.0 * bat_r, 26.0 * flop, 0.0]
                frP = [26.0 * flop + 12.0 * bat_r, 0.0, 0.0]
                targs["fl"] = {"pos": [targs["fl"]["pos"][0], targs["fl"]["pos"][1] + 2.8 * sc * lift_f, targs["fl"]["pos"][2]],
                               "fk": (fl1, fl2, flP), "fk_blend": fb}
                targs["fr"] = {"pos": [targs["fr"]["pos"][0], targs["fr"]["pos"][1] + 2.8 * sc * lift_f, targs["fr"]["pos"][2]],
                               "fk": (fr1, fr2, frP), "fk_blend": fb}
                rig.solve_legs(p, targs)
                rig.set_claws(p, fl=0.42 * flop * max(0.0, bat_l), fr=0.42 * flop * max(0.0, bat_r), hook=0.5)
                return p, 64.0 * flop, 24.0 * bat_l, 4.0 * flop, [0.0, 0.0, 0.0]
            return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.07, mane_lag=0.07)

        # C3. MAMA_NUZZLE (1.85s) — Happy high tail + nose stretched UP high with closed trusting eyes for affectionate chin-bumps!
        def make_mama_nuzzle():
            L, N = 1.85, 42
            def frame(i, t):
                nuz = _kscal(t, ((0.0, 0.0), (0.34, 1.0), (1.42, 1.0), (L, 0.0)))
                bump = nuz * math.sin(2.0 * math.pi * _clamp((t - 0.30) / 1.15, 0.0, 1.0))
                p = {
                    "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                    "body": {"position": [0.5 * sc * bump, -0.15 * sc + 0.55 * sc * nuz, -1.2 * sc * nuz],
                             "rotation": [-5.5 * nuz, 4.0 * bump, 3.0 * bump]},
                    "chest": {"rotation": [-6.5 * nuz, 5.0 * bump, 4.0 * bump]},
                    "waist": {"rotation": [3.5 * nuz, -3.0 * bump, -2.0 * bump]},
                    "hips": {"rotation": [2.5 * nuz, -2.0 * bump, 0.0]},
                    "neck": {"rotation": [-22.0 * nuz, 10.0 * bump, -8.0 * bump]},
                    "head": {"rotation": [-14.0 * nuz - 5.0 * abs(bump), 14.0 * bump, -14.0 * bump]},
                    "jaw": {"rotation": [0.0, 0.0, 0.0]},
                    "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                    "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
                    "ear_left": {"rotation": [-6.0 * nuz, -10.0 * nuz, -12.0 * nuz]},
                    "ear_right": {"rotation": [-6.0 * nuz, 10.0 * nuz, 12.0 * nuz]},
                }
                rig.set_eyes(p, blink=0.88 * nuz, squint=0.55 * nuz, look_y=0.14 * nuz)
                targs = rest_targets(x_pad=0.25 * nuz)
                step = pulse(t, 0.22, 0.16, 0.06, 0.14)
                targs["fl"]["pos"][1] += 1.6 * sc * step
                targs["fl"]["pos"][2] -= 1.4 * sc * nuz
                rig.solve_legs(p, targs)
                return p, 68.0 * nuz, 18.0 * bump, 4.0 * nuz, [0.0, 0.0, 0.0]
            return _bake_clip(rig, L, N, frame, loop=False, tail_lag=0.075, mane_lag=0.075)

        clips["play_pounce"] = make_play_pounce()
        clips["play_wrestle"] = make_play_wrestle()
        clips["mama_nuzzle"] = make_mama_nuzzle()

    # 14b. CLAWS_OUT OVERLAY (2.00s loop) — Claws-only parallel GeckoLib controller layer (touches ONLY toe_* & claw_*!)
    def make_claws_out_overlay():
        L, N = 2.00, 20
        def frame(t):
            pulse_c = 0.94 + 0.06 * math.sin(2.0 * math.pi * t / L)
            p = {}
            rig.set_claws(p, fl=pulse_c, fr=pulse_c, bl=0.65 * pulse_c, br=0.65 * pulse_c, hook=0.35)
            return p
        made = clip(L, frame, N, loop=True)
        _unwrap(made)
        return made

    clips["claws_out"] = make_claws_out_overlay()

    # 15 & 16. BLOCKMOVES: step_up_1000 & step_down_1000
    # NOTE: BlockMoves (and clip_check.py) ADD "path root t dy dz" onto the root at playback, so root.position
    # inside the clip itself stays [0, 0, 0] while IK solves in world coordinates using (dy, dz)!
    def make_step_up():
        B = 16.0
        L = 1.50
        steps = 30  # exact 20 Hz ticks (0.05 s)
        ahead_px = round(abs(rig.low_front_z) + 3.0, 2)
        face_z = -ahead_px
        front_z = rig.fore["fl"]["wr"][2]
        hind_z = rig.hind["bl"]["an"][2]
        travel = round((hind_z - (face_z - 5.5 * sc)), 2)

        fl_mid_z = face_z - 5.5 * sc
        fr_mid_z = face_z - 6.0 * sc
        bl_mid_z = face_z + 6.5 * sc
        br_mid_z = face_z + 7.0 * sc
        plans = {
            "fl": [
                (rig.fore["fl"]["wr"][0], rig.fore["fl"]["wr"][1], front_z, 0.0),
                (0.06, 0.36, (rig.fore["fl"]["wr"][0], rig.fore["fl"]["wr"][1] + B, fl_mid_z, 0.0)),
                (0.82, 1.16, (rig.fore["fl"]["wr"][0], rig.fore["fl"]["wr"][1] + B, front_z - travel, 0.0)),
            ],
            "fr": [
                (rig.fore["fr"]["wr"][0], rig.fore["fr"]["wr"][1], front_z, 0.0),
                (0.22, 0.52, (rig.fore["fr"]["wr"][0], rig.fore["fr"]["wr"][1] + B, fr_mid_z, 0.0)),
                (1.02, 1.38, (rig.fore["fr"]["wr"][0], rig.fore["fr"]["wr"][1] + B, front_z - travel, 0.0)),
            ],
            "bl": [
                (rig.hind["bl"]["an"][0], rig.hind["bl"]["an"][1], hind_z, 0.0),
                (0.24, 0.56, (rig.hind["bl"]["an"][0], rig.hind["bl"]["an"][1], bl_mid_z, 0.0)),
                (0.74, 1.14, (rig.hind["bl"]["an"][0], rig.hind["bl"]["an"][1] + B, hind_z - travel, 0.0)),
            ],
            "br": [
                (rig.hind["br"]["an"][0], rig.hind["br"]["an"][1], hind_z, 0.0),
                (0.40, 0.72, (rig.hind["br"]["an"][0], rig.hind["br"]["an"][1], br_mid_z, 0.0)),
                (0.92, 1.32, (rig.hind["br"]["an"][0], rig.hind["br"]["an"][1] + B, hind_z - travel, 0.0)),
            ],
        }
        path_rows = []

        def frame(i, t):
            u = t / L
            dz = -travel * ease(u)
            u_front = ease(_clamp((t - 0.06) / 0.50, 0.0, 1.0))
            u_back = ease(_clamp((t - 0.54) / 0.68, 0.0, 1.0))
            dy_f = B * u_front
            dy_b = B * u_back
            dy = 0.5 * (dy_f + dy_b)
            lip_lift = 2.0 * sc * math.sin(math.pi * _clamp((t - 0.14) / 0.62, 0.0, 1.0))
            pitch_deg = _clamp(-math.degrees(math.atan2(dy_f - dy_b, max(10.0, rig.trunk_len))), -24.0, 24.0)

            p = {
                "root": {"position": [0.0, dy + lip_lift, dz], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, -0.20 * sc, 0.0], "rotation": [pitch_deg, 0.0, 0.0]},
                "chest": {"rotation": [-pitch_deg * 0.16, 0.0, 0.0]},
                "waist": {"rotation": [pitch_deg * 0.20, 0.0, 0.0]},
                "hips": {"rotation": [-pitch_deg * 0.10, 0.0, 0.0]},
                "neck": {"rotation": [-pitch_deg * 0.45, 0.0, 0.0]},
                "head": {"rotation": [-pitch_deg * 0.45, 0.0, 0.0]},
                "jaw": {"rotation": [0.0, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
                "ear_left": {"rotation": [-6.0 * math.sin(math.pi * u), 0.0, 0.0]},
                "ear_right": {"rotation": [-6.0 * math.sin(math.pi * u), 0.0, 0.0]},
            }
            rig.set_eyes(p, look_y=0.15 * math.sin(math.pi * u))
            targs = {}
            for tag in ("fl", "fr", "bl", "br"):
                pos_w, arc, toe = foot_at(t, plans[tag], lift=5.2 * sc, reach=(tag[0] == "f"))
                targs[tag] = {"pos": pos_w, "toe": toe}
                if tag[0] == "b":
                    s_sign = rig.hind[tag]["side"]
                    targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + 16.0 * arc, 14.0, 48.0)
                    targs[tag]["pole"] = [s_sign * 0.35, 0.40, -1.0]
            rig.solve_legs(p, targs)
            p["root"] = {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]}
            path_rows.append((t, dy + lip_lift, dz))
            return p, 42.0 * math.sin(math.pi * u) + 14.0, 5.0 * math.sin(2.0 * math.pi * u), 0.0, [-pitch_deg * 0.4, 0.0, 0.0]

        made = _bake_clip(rig, L, steps, frame, loop="hold", tail_lag=0.07, mane_lag=0.06)
        rows = path_rows[: steps + 1]
        made["timeline"] = {
            "0.0": f"step {face_z:.2f} {B:.1f}",
            "0.05": "path root " + " ".join(f"{t:.4f} {dy:.3f} {dz:.3f}" for t, dy, dz in rows),
        }
        return made, ahead_px

    def make_step_down():
        B = 16.0
        L = 1.50
        steps = 30
        front_z = rig.fore["fl"]["wr"][2]
        hind_z = rig.hind["bl"]["an"][2]
        ahead_px = round(abs(front_z) + 4.5 * sc, 2)
        face_z = -ahead_px
        travel = round((hind_z - (face_z - 5.5 * sc)), 2)

        fl_mid_z = face_z - 6.0 * sc
        fr_mid_z = face_z - 6.5 * sc
        bl_mid_z = face_z + 6.5 * sc
        br_mid_z = face_z + 7.0 * sc
        plans = {
            "fl": [
                (rig.fore["fl"]["wr"][0], rig.fore["fl"]["wr"][1], front_z, 0.0),
                (0.06, 0.42, (rig.fore["fl"]["wr"][0], rig.fore["fl"]["wr"][1] - B, fl_mid_z, 0.0)),
                (0.84, 1.18, (rig.fore["fl"]["wr"][0], rig.fore["fl"]["wr"][1] - B, front_z - travel, 0.0)),
            ],
            "fr": [
                (rig.fore["fr"]["wr"][0], rig.fore["fr"]["wr"][1], front_z, 0.0),
                (0.22, 0.58, (rig.fore["fr"]["wr"][0], rig.fore["fr"]["wr"][1] - B, fr_mid_z, 0.0)),
                (1.04, 1.40, (rig.fore["fr"]["wr"][0], rig.fore["fr"]["wr"][1] - B, front_z - travel, 0.0)),
            ],
            "bl": [
                (rig.hind["bl"]["an"][0], rig.hind["bl"]["an"][1], hind_z, 0.0),
                (0.24, 0.56, (rig.hind["bl"]["an"][0], rig.hind["bl"]["an"][1], bl_mid_z, 0.0)),
                (0.68, 1.08, (rig.hind["bl"]["an"][0], rig.hind["bl"]["an"][1] - B, hind_z - travel, 0.0)),
            ],
            "br": [
                (rig.hind["br"]["an"][0], rig.hind["br"]["an"][1], hind_z, 0.0),
                (0.42, 0.74, (rig.hind["br"]["an"][0], rig.hind["br"]["an"][1], br_mid_z, 0.0)),
                (0.88, 1.30, (rig.hind["br"]["an"][0], rig.hind["br"]["an"][1] - B, hind_z - travel, 0.0)),
            ],
        }
        path_rows = []

        def frame(i, t):
            u = t / L
            dz = -travel * ease(u)
            u_front = ease(_clamp((t - 0.14) / 0.50, 0.0, 1.0))
            u_back = ease(_clamp((t - 0.68) / 0.60, 0.0, 1.0))
            dy_f = -B * u_front
            dy_b = -B * u_back
            dy = 0.5 * (dy_f + dy_b)
            lip_lift = 1.6 * sc * math.sin(math.pi * _clamp((t - 0.24) / 0.64, 0.0, 1.0))
            pitch_deg = _clamp(-math.degrees(math.atan2(dy_f - dy_b, max(10.0, rig.trunk_len))), -24.0, 24.0)

            p = {
                "root": {"position": [0.0, dy + lip_lift, dz], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, -0.20 * sc, 0.0], "rotation": [pitch_deg, 0.0, 0.0]},
                "chest": {"rotation": [-pitch_deg * 0.16, 0.0, 0.0]},
                "waist": {"rotation": [pitch_deg * 0.20, 0.0, 0.0]},
                "hips": {"rotation": [-pitch_deg * 0.10, 0.0, 0.0]},
                "neck": {"rotation": [-pitch_deg * 0.45, 0.0, 0.0]},
                "head": {"rotation": [-pitch_deg * 0.45, 0.0, 0.0]},
                "jaw": {"rotation": [0.0, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
                "ear_left": {"rotation": [-5.0 * math.sin(math.pi * u), 0.0, 0.0]},
                "ear_right": {"rotation": [-5.0 * math.sin(math.pi * u), 0.0, 0.0]},
            }
            rig.set_eyes(p, look_y=-0.15 * math.sin(math.pi * u))
            targs = {}
            for tag in ("fl", "fr", "bl", "br"):
                pos_w, arc, toe = foot_at(t, plans[tag], lift=3.8 * sc, reach=(tag[0] == "f"))
                targs[tag] = {"pos": pos_w, "toe": toe}
                if tag[0] == "b":
                    targs[tag]["hock"] = _clamp(rig.hind[tag]["rest_hock"] + 12.0 * arc, 12.0, 48.0)
            rig.solve_legs(p, targs)
            p["root"] = {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]}
            path_rows.append((t, dy + lip_lift, dz))
            # Lift tail proudly high over the upper ledge behind the lion as it steps down!
            return p, 118.0 * ease(_clamp((t - 0.50) / 0.60, 0.0, 1.0)) + 16.0, 5.0 * math.sin(2.0 * math.pi * u), 0.0, [-pitch_deg * 0.4, 0.0, 0.0]

        made = _bake_clip(rig, L, steps, frame, loop="hold", tail_lag=0.07, mane_lag=0.06)
        rows = path_rows[: steps + 1]
        made["timeline"] = {
            "0.0": f"step {face_z:.2f} {-B:.1f}",
            "0.05": "path root " + " ".join(f"{t:.4f} {dy:.3f} {dz:.3f}" for t, dy, dz in rows),
        }
        return made, ahead_px

    def make_cub_hop(up=True):
        """Playful spring-hop BlockMove for the small Lion Cub (a 1-block 16 px ledge is taller than its shoulder!)."""
        B = 16.0
        L = 0.90
        steps = 18  # exact 20 Hz ticks (0.05 s)
        ahead_px = round(abs(rig.low_front_z) + 3.0, 2) if up else round(abs(rig.fore["fl"]["wr"][2]) + 3.5, 2)
        face_z = -ahead_px
        ledge_h = B if up else -B
        hind_z = rig.hind["bl"]["an"][2]
        travel = round((hind_z - (face_z - 5.5)) + (8.0 if not up else 0.0), 2)
        path_rows = []

        def frame(i, t):
            u = t / L
            # Coil (0..0.22), airborne hop clearing ledge cleanly (0.20..0.76), soft paw-cushion settle (0.74..0.90)
            coil = pulse(t, 0.0, 0.16, 0.04, 0.10)
            u_air = _clamp((t - 0.18) / 0.56, 0.0, 1.0)
            arc = math.sin(math.pi * u_air) if 0.18 <= t <= 0.74 else 0.0
            land = pulse(t, 0.72, 0.06, 0.02, 0.10)
            dz = -travel * ease(_clamp((t - 0.16) / 0.60, 0.0, 1.0))
            if up:
                # Rise quickly before passing face_z so paws & belly clear the 16 px lip cleanly!
                dy = B * ease(_clamp((t - 0.16) / 0.44, 0.0, 1.0)) + 5.2 * arc
            else:
                # Hop forward out past face_z first, then arc smoothly down to -16 px!
                dy = -B * ease(_clamp((t - 0.34) / 0.44, 0.0, 1.0)) + 4.2 * arc
            body_p = (8.0 * coil - (20.0 if up else -14.0) * math.sin(math.pi * u_air) * (1.0 if 0.18 <= t <= 0.74 else 0.0) + 5.0 * land)
            p = {
                "root": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "body": {"position": [0.0, -0.20 * sc - 1.8 * coil - 1.2 * land, 0.0], "rotation": [body_p, 0.0, 0.0]},
                "chest": {"rotation": [-body_p * 0.15, 0.0, 0.0]},
                "waist": {"rotation": [body_p * 0.25, 0.0, 0.0]},
                "hips": {"rotation": [-body_p * 0.15, 0.0, 0.0]},
                "neck": {"rotation": [-body_p * 0.45, 0.0, 0.0]},
                "head": {"rotation": [-body_p * 0.45, 0.0, 0.0]},
                "jaw": {"rotation": [0.0, 0.0, 0.0]},
                "tongue": {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "tongue_tip": {"rotation": [0.0, 0.0, 0.0]},
                "ear_left": {"rotation": [-12.0 * arc, 0.0, 0.0]},
                "ear_right": {"rotation": [-12.0 * arc, 0.0, 0.0]},
            }
            rig.set_eyes(p, look_y=(0.18 if up else -0.18) * arc)
            # Local paw targets: planted on ground at coil & land, neatly tucked under body during the hop!
            targs = {}
            for tag in ("fl", "fr"):
                w = rig.fore[tag]["wr"]
                targs[tag] = {
                    "pos": [w[0], w[1] + 2.6 * arc, w[2] - 2.4 * arc],
                    "toe": 24.0 * arc,
                }
            for tag in ("bl", "br"):
                a = rig.hind[tag]["an"]
                targs[tag] = {
                    "pos": [a[0], a[1] + 2.8 * arc, a[2] + 2.2 * arc],
                    "toe": 26.0 * arc,
                    "hock": _clamp(rig.hind[tag]["rest_hock"] + 16.0 * coil + 10.0 * arc, 12.0, 52.0),
                }
            rig.solve_legs(p, targs)
            path_rows.append((t, dy, dz))
            return p, 36.0 * arc + (42.0 if not up else 16.0), 8.0 * math.sin(2.0 * math.pi * u), 0.0, [0.0, 0.0, 0.0]

        made = _bake_clip(rig, L, steps, frame, loop="hold", tail_lag=0.06, mane_lag=0.06)
        rows = path_rows[: steps + 1]
        made["timeline"] = {
            "0.0": f"step {face_z:.2f} {ledge_h:.1f}",
            "0.05": "path root " + " ".join(f"{t:.4f} {dy:.3f} {dz:.3f}" for t, dy, dz in rows),
        }
        return made, ahead_px

    if is_cub:
        clips["step_up_1000"], up_ahead = make_cub_hop(up=True)
        clips["step_down_1000"], dn_ahead = make_cub_hop(up=False)
    else:
        clips["step_up_1000"], up_ahead = make_step_up()
        clips["step_down_1000"], dn_ahead = make_step_down()

    meta = {
        "walks": walk_speeds,
        "step_up_ahead": up_ahead,
        "step_down_ahead": dn_ahead,
    }
    return clips, meta
