"""African Buffalo (Syncerus caffer) Bespoke Animation & BlockMoves Engine.

Built directly on the exact 35-bone rig from tools/buffalo_models.py:
- Exact analytical 2-bone leg IK with rest-vector angle alignment (front carpus bends forward -Z;
  sculpted hind hock bends backward +Z with zero rest-pose pop and 100% grounded hooves on y=0).
- Grounded exact-IK bovine rest_down / rest / rest_up (2.4s slow, heavy, relaxed — ZERO floating legs!).
- Telescopic neck/head down -> UP & OUT real bull headbutt + chained charge_headbutt!
- Natural blinking & classic African Buffalo rapid ear-flutter across living clips + dedicated clips.
- 2nd-order damped spring physics (motion_kit.secondary_lag) for living 3-bone tail switch whip.

Clip Suite (24 Core & Character Clips + 2 BlockMoves = 26 clips):
  Locomotion:  idle, walk, trot, charge
  Combat:      headbutt, charge_headbutt, gore, paw_ground, bellow
  Fun & Life:  ear_flutter, snort, horn_rub, shake_off, graze, drink,
               rest_down, rest, rest_up, mood_alert, mood_angry, death
  Held:        held_sink, held_down, death_down (the Motion Studio phase 3: brought down by lionesses)
  BlockMoves:  step_up_1000, step_down_1000
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import (  # noqa: E402
    clip, euler, mat_mul, mat_vec, rot, transpose, unit, vadd, vlen, vsub,
)
from motion_kit import (  # noqa: E402
    ease, foot_at, pulse, quadruped_gait, secondary_lag, stride, two_bone,
)


def _clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


def _ear_flutter_wave(t, t0, dur=0.40, freq=8.5, amp=26.0):
    """Classic African Buffalo rapid ear-flutter envelope (snappy horizontal flap to shoo flies!)."""
    if t < t0 or t > t0 + dur:
        return 0.0
    u = (t - t0) / dur
    env = math.sin(math.pi * u)
    return amp * env * math.sin(2.0 * math.pi * (t - t0) * freq)


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


def _dir_to_rx_rz(d):
    """Exact GeckoLib [rx, 0, rz] (deg) mapping (0, -1, 0) onto unit vector d = (dx, dy, dz)."""
    dx, dy, dz = d
    rx = math.degrees(math.atan2(dz, math.hypot(dx, dy)))
    rz = math.degrees(math.atan2(-dx, -dy))
    if abs(rz) > 90.0:
        rz -= math.copysign(180.0, rz)
        rx = math.copysign(180.0, rx) - rx
    return [rx, 0.0, rz]


class BuffaloRig:
    """Exact measured pivots & analytical FK/IK solver for the 35-bone African Buffalo rig."""

    def __init__(self, raw_bones):
        self.by_name = {b["name"]: b for b in raw_bones}
        self.piv = {b["name"]: list(b["pivot"]) for b in raw_bones}
        self.parent = {b["name"]: b.get("parent") for b in raw_bones}

        self.legs = {}
        for tag, parent_bone, side, pole in (
            ("fl", "chest",  1, [0.0, 0.0, -1.0]),   # front carpus bends forward (-Z)
            ("fr", "chest", -1, [0.0, 0.0, -1.0]),
            ("bl", "hips",   1, [0.0, 0.0,  1.0]),   # hind hock bends backward (+Z)
            ("br", "hips",  -1, [0.0, 0.0,  1.0]),
        ):
            p1 = self.piv[f"leg_{tag}_1"]
            p2 = self.piv[f"leg_{tag}_2"]
            p3 = self.piv[f"leg_{tag}_foot"]
            v1 = vsub(p2, p1)
            v2 = vsub(p3, p2)
            a1_rest = math.degrees(math.atan2(v1[2], -v1[1]))
            a2_rest = math.degrees(math.atan2(v2[2], -v2[1]))
            self.legs[tag] = {
                "parent": parent_bone, "side": side, "pole": pole,
                "p1": p1, "p2": p2, "p3": p3,
                "l1": vlen(v1), "l2": vlen(v2),
                "a1_rest": a1_rest, "a2_rest": a2_rest,
                "rest_y": p3[1],
            }

        self.trunk_len = abs(self.legs["bl"]["p1"][2] - self.legs["fl"]["p1"][2])

    def bone_world(self, pose, bone):
        """Returns (world_pivot, world_rot_mat) for `bone` given `pose`."""
        chain = []
        cur = bone
        while cur:
            chain.append(cur)
            cur = self.parent.get(cur)
        chain.reverse()
        R = ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
        P = [0.0, 0.0, 0.0]
        prev_piv = [0.0, 0.0, 0.0]
        for b in chain:
            piv = self.piv[b]
            delta = vsub(piv, prev_piv)
            P = vadd(P, mat_vec(R, delta))
            tf = pose.get(b, {})
            pos = tf.get("position")
            if pos:
                P = vadd(P, mat_vec(R, pos))
            rdeg = tf.get("rotation")
            if rdeg and any(abs(v) > 1e-7 for v in rdeg):
                R = mat_mul(R, euler(rdeg))
            prev_piv = piv
        return P, R

    def set_face(self, pose, blink=0.0, squint=0.0, angry=0.0, jaw=0.0, jaw_yaw=0.0,
                 tongue_rx=0.0, tongue_tip_rx=0.0, ear_l=(0.0, 0.0, 0.0), ear_r=None):
        """Sets all facial bones consistently (always emits keyframes so looping blends stay seamless)."""
        if ear_r is None:
            ear_r = (ear_l[0], -ear_l[1], -ear_l[2])
        lid_y = -1.10 * blink - 0.42 * squint * (1.0 - blink)
        ulid_y = 0.48 * blink + 0.30 * squint * (1.0 - blink)
        pose["lid_left"]   = {"position": [0.0, lid_y, 0.0]}
        pose["lid_right"]  = {"position": [0.0, lid_y, 0.0]}
        pose["ulid_left"]  = {"position": [0.0, ulid_y, 0.0]}
        pose["ulid_right"] = {"position": [0.0, ulid_y, 0.0]}
        pose["brow_left"]  = {"rotation": [0.0, 0.0, -14.0 * angry], "position": [0.0, -0.35 * angry, 0.0]}
        pose["brow_right"] = {"rotation": [0.0, 0.0,  14.0 * angry], "position": [0.0, -0.35 * angry, 0.0]}
        pose["jaw"]        = {"rotation": [jaw, jaw_yaw, 0.0]}
        pose["tongue"]     = {"rotation": [tongue_rx, jaw_yaw * 0.4, 0.0]}
        pose["tongue_tip"] = {"rotation": [tongue_tip_rx, 0.0, 0.0]}
        pose["ear_left"]   = {"rotation": list(ear_l)}
        pose["ear_right"]  = {"rotation": list(ear_r)}

    def solve_legs(self, pose, targets, reach=0.992):
        """Analytical 2-bone IK for all 4 legs."""
        for tag in ("fl", "fr", "bl", "br"):
            spec = self.legs[tag]
            tgt = targets[tag]
            par_p, R_par = self.bone_world(pose, spec["parent"])
            rel = vsub(spec["p1"], self.piv[spec["parent"]])
            sock_w = vadd(par_p, mat_vec(R_par, rel))

            target_w = tgt["pos"]
            use_pole = tgt.get("pole", spec["pole"])
            knee_w, ankle_w, _ = two_bone(sock_w, spec["l1"], spec["l2"], target_w, use_pole, reach=reach)

            d1_w = unit(vsub(knee_w, sock_w))
            d1_loc = mat_vec(transpose(R_par), d1_w)
            r1_raw = _dir_to_rx_rz(d1_loc)
            r1 = [r1_raw[0] - spec["a1_rest"], 0.0, r1_raw[2]]
            R_1 = mat_mul(R_par, euler(r1))

            d2_w = unit(vsub(ankle_w, knee_w))
            d2_loc = mat_vec(transpose(R_1), d2_w)
            r2 = [math.degrees(math.atan2(d2_loc[2], -d2_loc[1])) - spec["a2_rest"], 0.0, 0.0]
            R_2 = mat_mul(R_1, euler(r2))

            toe = tgt.get("toe", 0.0)
            froll = tgt.get("roll", 0.0)
            R_ft = euler([toe, 0.0, froll])
            rf = rot(mat_mul(transpose(R_2), R_ft))

            pose[f"leg_{tag}_1"]    = {"rotation": r1}
            pose[f"leg_{tag}_2"]    = {"rotation": r2}
            pose[f"leg_{tag}_foot"] = {"rotation": rf}

    def planted_targets(self, dz_all=0.0, dx_front=0.0, dx_hind=0.0, z_front_off=0.0, z_hind_off=0.0):
        out = {}
        for tag in ("fl", "fr", "bl", "br"):
            sp = self.legs[tag]
            dx = (dx_front if tag[0] == "f" else dx_hind) * sp["side"]
            dz = dz_all + (z_front_off if tag[0] == "f" else z_hind_off)
            out[tag] = {"pos": [sp["p3"][0] + dx, sp["rest_y"], sp["p3"][2] + dz], "toe": 0.0}
        return out


def _bake_clip(rig, length, steps, frame_fn, loop=True, tail_lag=0.08):
    """Samples `frame_fn(i, t) -> (pose_dict, tail_pitch, tail_sway)` and applies 2nd-order damped
    spring physics (`secondary_lag`) along the 3-bone buffalo tail switch so whip motion feels alive!"""
    dt = length / steps
    frames = []
    t_pitch_in = []
    t_sway_in = []
    for i in range(steps + 1):
        t = length * i / steps
        p, tp, ts = frame_fn(i, t)
        # Ensure neck always has a position track so blends with telescopic headbutt stay 100% smooth!
        if "neck" in p and "position" not in p["neck"]:
            p["neck"]["position"] = [0.0, 0.0, 0.0]
        frames.append((t, p))
        t_pitch_in.append(tp)
        t_sway_in.append(ts)

    is_loop = (loop is True)
    tp1 = secondary_lag(t_pitch_in, dt, tail_lag * 0.55, damping=0.80, loop=is_loop)
    tp2 = secondary_lag(tp1,        dt, tail_lag * 0.90, damping=0.72, loop=is_loop)
    tp3 = secondary_lag(tp2,        dt, tail_lag * 1.15, damping=0.65, loop=is_loop)

    ts1 = secondary_lag(t_sway_in,  dt, tail_lag * 0.50, damping=0.72, loop=is_loop)
    ts2 = secondary_lag(ts1,        dt, tail_lag * 0.95, damping=0.62, loop=is_loop)
    ts3 = secondary_lag(ts2,        dt, tail_lag * 1.25, damping=0.55, loop=is_loop)

    for i, (t, p) in enumerate(frames):
        p1 = max(0.0, tp1[i])
        p2 = _clamp(0.35 * (tp2[i] - tp1[i]) + 0.15 * tp2[i], -12.0, 26.0)
        p3 = _clamp(0.45 * (tp3[i] - tp2[i]) + 0.12 * tp3[i], -16.0, 32.0)
        s1 = ts1[i]
        s2 = _clamp(1.15 * (ts2[i] - 0.50 * ts1[i]), -42.0, 42.0)
        s3 = _clamp(1.30 * (ts3[i] - 0.50 * ts2[i]), -48.0, 48.0)
        p["tail_1"] = {"rotation": [p1, 0.0, s1]}
        p["tail_2"] = {"rotation": [p2, 0.0, s2]}
        p["tail_3"] = {"rotation": [p3, 0.0, s3]}

    if is_loop and len(frames) > 1:
        frames[-1] = (frames[-1][0], frames[0][1])

    bones = {}
    for t, p in frames:
        tk = f"{t:.4f}".rstrip("0").rstrip(".") or "0"
        for bname, chans in p.items():
            for ch, val in chans.items():
                bones.setdefault(bname, {}).setdefault(ch, {})[tk] = [round(x, 3) for x in val]
    out = {"animation_length": round(length, 4), "bones": bones}
    if loop is True:
        out["loop"] = True
    elif loop == "hold":
        out["loop"] = "hold_on_last_frame"
    _unwrap(out)
    return out


def build_clips(raw_bones):
    """Builds the full 23-clip African Buffalo animation suite + exact walk speeds & BlockMoves metadata."""
    rig = BuffaloRig(raw_bones)
    clips = {}
    walk_speeds = {}

    # ------------------------------------------------------------------------------------------------------------------
    # 1. IDLE (4.40s loop) — Heavy savanna breathing, proud sway, double blinks, classic buffalo ear flutters & tail!
    # ------------------------------------------------------------------------------------------------------------------
    def make_idle():
        L, N = 4.40, 66
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            breath = math.sin(ph)
            dy = -0.15 + 0.12 * breath
            # Classic African Buffalo rapid double ear-flutters (left at 1.45s, both rapid flutter at 2.75s!)
            fl_l = _ear_flutter_wave(t, 1.45, 0.36, 8.5, 22.0) + _ear_flutter_wave(t, 2.75, 0.42, 9.0, 26.0)
            fl_r = _ear_flutter_wave(t, 2.80, 0.42, 9.0, 26.0)
            blink = max(pulse(t, 0.95, 0.07, 0.05, 0.09), pulse(t, 3.35, 0.07, 0.05, 0.09))
            # Tiny head twitch accompanying the double ear flutter
            htw = 2.4 * _ear_flutter_wave(t, 2.75, 0.42, 4.5, 1.0)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, dy, 0.0], "rotation": [0.45 * breath, 0.0, 0.55 * math.sin(ph * 0.5)]},
                "chest": {"rotation": [-0.75 * breath, 0.6 * math.sin(ph), -0.35 * math.sin(ph * 0.5)]},
                "neck":  {"rotation": [0.9 * breath, 2.0 * math.sin(ph), 0.0]},
                "head":  {"rotation": [1.4 * math.sin(ph - 0.5), 3.2 * math.sin(ph) + htw, 0.8 * math.sin(ph) + htw * 0.5]},
            }
            rig.set_face(p, blink=blink,
                         ear_l=(fl_l * 0.28, fl_l, fl_l * 0.45),
                         ear_r=(fl_r * 0.28, -fl_r, -fl_r * 0.45))
            rig.solve_legs(p, rig.planted_targets())
            sway = (7.0 * math.sin(ph)
                    + 26.0 * pulse(t, 0.65, 0.14, 0.45, 0.25) * math.sin(2.0 * math.pi * (t - 0.65) * 2.2)
                    + 22.0 * pulse(t, 2.70, 0.12, 0.40, 0.22) * math.sin(2.0 * math.pi * (t - 2.70) * 2.6))
            return p, 5.0 + 3.0 * abs(sway) / 28.0, sway
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.085)

    clips["idle"] = make_idle()

    # ------------------------------------------------------------------------------------------------------------------
    # 2. WALK (1.20s loop) — Heavy 4-beat lateral bovine stride (BL -> FL -> BR -> FR) + casual blink & ear flutter!
    # ------------------------------------------------------------------------------------------------------------------
    def make_walk():
        L, N = 1.20, 36
        duty = 0.72
        stride_len = 8.4   # 9.722 px/s (0.608 b/s heavy stately savanna walk)
        phases = {"bl": 0.00, "fl": 0.25, "br": 0.50, "fr": 0.75}
        walk_speeds["walk"] = round(stride_len / (duty * L), 3)

        def frame(i, t):
            u = t / L
            ph = 2.0 * math.pi * u
            g = quadruped_gait(u, phases, duty, stride_len, lift_h=2.2, bounce=0.22, spine_flex=1.6, cycle_s=L)
            hip_dy = -0.58 + g["body_y"]
            hip_rz = 1.4 * math.sin(ph)
            hip_ry = 1.6 * math.cos(ph)
            blink = pulse(t, 0.52, 0.06, 0.04, 0.08)
            ef = _ear_flutter_wave(t, 0.72, 0.30, 8.0, 14.0)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, hip_dy, 0.0], "rotation": [g["body_pitch"], hip_ry, hip_rz]},
                "chest": {"rotation": [g["spine_pitch"], -2.4 * math.cos(ph), -hip_rz * 0.45]},
                "neck":  {"rotation": [2.2 - 1.2 * math.sin(2.0 * ph), 1.4 * math.cos(ph), -hip_rz * 0.35]},
                "head":  {"rotation": [-1.4 + 1.0 * math.cos(2.0 * ph), -0.8 * math.cos(ph), -hip_rz * 0.2]},
            }
            rig.set_face(p, blink=blink,
                         ear_l=(-3.0 + 3.0 * math.sin(2.0 * ph) + ef * 0.2, ef, ef * 0.35),
                         ear_r=(-3.0 + 3.0 * math.sin(2.0 * ph), -ef * 0.4, -ef * 0.15))
            targs = {}
            for tag in ("fl", "fr", "bl", "br"):
                sp = rig.legs[tag]
                ft = g["feet"][tag]
                targs[tag] = {
                    "pos": [sp["p3"][0], sp["rest_y"] + ft["dy"], sp["p3"][2] + ft["dz"]],
                    "toe": 10.0 * ft["lift"],
                }
            rig.solve_legs(p, targs)
            sway = 14.0 * math.sin(ph - 0.5) + 5.0 * math.sin(2.0 * ph)
            return p, 8.0 + 2.5 * math.sin(2.0 * ph), sway
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.075)

    clips["walk"] = make_walk()

    # ------------------------------------------------------------------------------------------------------------------
    # 3. TROT (0.72s loop) — Energetic diagonal-pair herd trot (FL+BR, FR+BL), brisk shoulder lift!
    # ------------------------------------------------------------------------------------------------------------------
    def make_trot():
        L, N = 0.72, 24
        duty = 0.60
        stride_len = 9.6   # 22.222 px/s (1.389 b/s brisk herd trot)
        phases = {"fl": 0.00, "br": 0.04, "fr": 0.50, "bl": 0.54}
        walk_speeds["trot"] = round(stride_len / (duty * L), 3)

        def frame(i, t):
            u = t / L
            ph = 2.0 * math.pi * u
            g = quadruped_gait(u, phases, duty, stride_len, lift_h=3.2, bounce=0.45, spine_flex=2.4, cycle_s=L)
            hip_dy = -0.82 + g["body_y"]
            hip_rz = 1.6 * math.sin(ph)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, hip_dy, 0.0], "rotation": [g["body_pitch"] + 1.2, 1.4 * math.cos(ph), hip_rz]},
                "chest": {"rotation": [g["spine_pitch"] - 0.6, -2.0 * math.cos(ph), -hip_rz * 0.45]},
                "neck":  {"rotation": [5.5 - 2.0 * math.sin(2.0 * ph), 1.0 * math.cos(ph), 0.0]},
                "head":  {"rotation": [-3.0 + 1.8 * math.cos(2.0 * ph), -0.6 * math.cos(ph), 0.0]},
            }
            rig.set_face(p, ear_l=(-6.0 + 4.5 * math.sin(2.0 * ph), -6.0, 2.0))
            targs = {}
            for tag in ("fl", "fr", "bl", "br"):
                sp = rig.legs[tag]
                ft = g["feet"][tag]
                targs[tag] = {
                    "pos": [sp["p3"][0], sp["rest_y"] + ft["dy"], sp["p3"][2] + ft["dz"]],
                    "toe": 15.0 * ft["lift"],
                }
            rig.solve_legs(p, targs)
            return p, 16.0 + 5.0 * math.sin(2.0 * ph), 12.0 * math.sin(ph - 0.4)
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.065)

    clips["trot"] = make_trot()

    # ------------------------------------------------------------------------------------------------------------------
    # 4. CHARGE (0.56s loop) — Terrifying African Buffalo locomotive gallop! Head down, boss forward, angry brows!
    # ------------------------------------------------------------------------------------------------------------------
    def make_charge():
        L, N = 0.56, 24
        duty = 0.52
        stride_len = 11.4  # 39.148 px/s (2.447 b/s thunderous bull charge)
        phases = {"bl": 0.00, "br": 0.14, "fl": 0.50, "fr": 0.64}
        walk_speeds["charge"] = round(stride_len / (duty * L), 3)

        def frame(i, t):
            u = t / L
            ph = 2.0 * math.pi * u
            g = quadruped_gait(u, phases, duty, stride_len, lift_h=4.2, bounce=0.72,
                               suspend_h=0.55, spine_flex=4.6, cycle_s=L)
            hip_dy = -1.25 + g["body_y"]
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, hip_dy, 0.0], "rotation": [g["body_pitch"] + 1.5, 1.2 * math.sin(ph), 1.4 * math.sin(ph)]},
                "chest": {"rotation": [g["spine_pitch"] + 1.5, -1.6 * math.sin(ph), -0.8 * math.sin(ph)]},
                "neck":  {"rotation": [16.5 + 3.0 * math.cos(ph), 0.0, 0.0]},
                "head":  {"rotation": [14.0 - 2.5 * math.cos(ph), 0.0, 0.0]},
            }
            rig.set_face(p, squint=0.45, angry=1.0, jaw=3.0 + 2.0 * math.sin(ph),
                         ear_l=(-14.0, -24.0, 10.0))
            targs = {}
            for tag in ("fl", "fr", "bl", "br"):
                sp = rig.legs[tag]
                ft = g["feet"][tag]
                targs[tag] = {
                    "pos": [sp["p3"][0], sp["rest_y"] + ft["dy"], sp["p3"][2] + ft["dz"]],
                    "toe": 22.0 * ft["lift"],
                }
            rig.solve_legs(p, targs)
            return p, 34.0 + 8.0 * math.sin(ph), 16.0 * math.sin(ph - 0.3)
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.055)

    clips["charge"] = make_charge()

    # ------------------------------------------------------------------------------------------------------------------
    # 5. HEADBUTT (1.25s hold) — REAL bull headbutt! Dips head & boss DOWN low, then explodes UP & OUT with neck piston!
    # ------------------------------------------------------------------------------------------------------------------
    def make_headbutt():
        L, N = 1.25, 30
        def frame(i, t):
            # Phase 1 (0.00..0.44): Coiled heavy DOWN scoop — drops chest & dips helmet boss low!
            dip   = pulse(t, 0.00, 0.28, 0.10, 0.16)
            # Phase 2 (0.36..0.82): Explosive UP & OUT ram surge! Neck pistons forward & pitches up, driving boss skyward!
            surge = pulse(t, 0.36, 0.18, 0.18, 0.44)
            # Phase 3 (0.52..0.70): Subtle impact snap recoil at apex of the upward thrust
            snap  = pulse(t, 0.50, 0.08, 0.04, 0.16)

            active = max(dip, surge)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -1.65 * dip - 1.35 * surge, 1.8 * dip - 3.2 * surge],
                          "rotation": [4.0 * dip - 3.8 * surge, 0.0, 0.0]},
                "chest": {"rotation": [7.5 * dip - 8.2 * surge - 1.5 * snap, 0.0, 0.0]},
                # Neck dips DOWN (+28 deg, tucked slightly back) -> rockets OUT (z - 2.6 px) & UP (-22 deg)!
                "neck":  {"position": [0.0, -0.7 * dip + 1.0 * surge, 0.7 * dip - 2.5 * surge],
                          "rotation": [28.0 * dip - 22.0 * surge - 4.0 * snap, 0.0, 0.0]},
                # Head tucks DOWN (+24 deg) presenting boss low -> whips UP & OUT (-20 deg) like a real bull strike!
                "head":  {"rotation": [24.0 * dip - 20.0 * surge - 5.0 * snap, 0.0, 0.0]},
            }
            rig.set_face(p, squint=0.55 * active, angry=active,
                         jaw=8.0 * surge + 4.0 * snap,
                         ear_l=(-14.0 * active, -26.0 * active, 10.0 * active))
            rig.solve_legs(p, rig.planted_targets())
            return p, 10.0 + 10.0 * dip + 28.0 * surge, 16.0 * surge * math.sin(2.0 * math.pi * t * 2.5)
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.06)

    clips["headbutt"] = make_headbutt()

    # ------------------------------------------------------------------------------------------------------------------
    # 6. CHARGE_HEADBUTT (1.80s hold) — Chained Attack! 2 thunderous charging strides -> braking plant -> UP & OUT ram!
    # ------------------------------------------------------------------------------------------------------------------
    def make_charge_headbutt():
        L, N = 1.80, 44
        # 0.00..0.96: 1.75 intense charging strides (same bounding gallop phases as charge!)
        # 0.84..1.20: Hard braking plant & deep DOWN boss coil
        # 1.12..1.52: Explosive UP & OUT headbutt impact!
        # 1.48..1.80: Heavy braced recovery to standing stance
        stride_L = 0.56
        duty = 0.52
        stride_len = 10.8
        phases = {"bl": 0.00, "br": 0.14, "fl": 0.50, "fr": 0.64}

        def frame(i, t):
            # Smoothly ramp gait in/out so charge_headbutt blends cleanly from BOTH charge and idle!
            gait_w = ease(_clamp(t / 0.14, 0.0, 1.0)) * (1.0 - ease(_clamp((t - 0.78) / 0.24, 0.0, 1.0)))
            u = (t / stride_L) % 1.0
            ph = 2.0 * math.pi * u
            g = quadruped_gait(u, phases, duty, stride_len, lift_h=4.0, bounce=0.65,
                               suspend_h=0.45, spine_flex=4.2, cycle_s=stride_L)

            # Braking low coil right as hooves plant, then explosive UP & OUT headbutt scoop!
            dip   = pulse(t, 0.78, 0.22, 0.12, 0.16)
            surge = pulse(t, 1.08, 0.16, 0.18, 0.34)
            snap  = pulse(t, 1.22, 0.07, 0.04, 0.14)
            angry = ease(_clamp(t / 0.16, 0.0, 1.0)) * (1.0 - ease(_clamp((t - 1.50) / 0.28, 0.0, 1.0)))

            hip_dy = (-1.15 + g["body_y"]) * gait_w - 1.60 * dip - 1.35 * surge
            hip_dz = 1.2 * dip - 3.3 * surge
            hip_rx = (g["body_pitch"] + 1.5) * gait_w + 4.2 * dip - 3.8 * surge
            chest_rx = (g["spine_pitch"] + 1.5) * gait_w + 7.5 * dip - 8.5 * surge - 1.5 * snap
            neck_rx = (16.5 + 3.0 * math.cos(ph)) * gait_w + 29.0 * dip - 23.0 * surge - 4.0 * snap
            head_rx = (14.0 - 2.5 * math.cos(ph)) * gait_w + 25.0 * dip - 21.0 * surge - 5.0 * snap
            neck_pos = [0.0, -0.75 * dip + 1.1 * surge, 0.6 * dip - 2.7 * surge]

            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, hip_dy, hip_dz],
                          "rotation": [hip_rx, 1.2 * math.sin(ph) * gait_w, 1.3 * math.sin(ph) * gait_w]},
                "chest": {"rotation": [chest_rx, -1.5 * math.sin(ph) * gait_w, -0.8 * math.sin(ph) * gait_w]},
                "neck":  {"position": neck_pos, "rotation": [neck_rx, 0.0, 0.0]},
                "head":  {"rotation": [head_rx, 0.0, 0.0]},
            }
            rig.set_face(p, squint=0.55 * angry, angry=angry,
                         jaw=3.0 * gait_w + 10.0 * surge + 4.0 * snap,
                         ear_l=(-14.0 * angry, -26.0 * angry, 10.0 * angry))

            # Blend smoothly from galloping hooves into firmly braced front hooves during the braking headbutt!
            brace_z = -2.2 * (dip + surge)
            targs = {}
            for tag in ("fl", "fr", "bl", "br"):
                sp = rig.legs[tag]
                ft = g["feet"][tag]
                dz_leg = ft["dz"] * gait_w + (brace_z if tag[0] == "f" else 0.0)
                dy_leg = ft["dy"] * gait_w
                targs[tag] = {
                    "pos": [sp["p3"][0], sp["rest_y"] + dy_leg, sp["p3"][2] + dz_leg],
                    "toe": 20.0 * ft["lift"] * gait_w,
                }
            rig.solve_legs(p, targs)
            tail_p = 32.0 * gait_w + 16.0 * dip + 34.0 * surge + 6.0 * (1.0 - angry)
            tail_s = 16.0 * math.sin(ph) * gait_w + 20.0 * surge * math.sin(2.0 * math.pi * t * 2.5)
            return p, tail_p, tail_s
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.055)

    clips["charge_headbutt"] = make_charge_headbutt()

    # ------------------------------------------------------------------------------------------------------------------
    # 7. GORE (1.10s hold) — Signature Cape Buffalo side-coil -> explosive upward horn-hook toss!
    # ------------------------------------------------------------------------------------------------------------------
    def make_gore():
        L, N = 1.10, 26
        def frame(i, t):
            coil = pulse(t, 0.0, 0.24, 0.06, 0.14)
            toss = pulse(t, 0.30, 0.16, 0.16, 0.42)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -1.4 * coil - 1.05 * toss, 1.0 * coil - 1.2 * toss],
                          "rotation": [3.2 * coil - 2.2 * toss, -2.5 * coil + 4.0 * toss, 1.0 * coil - 1.5 * toss]},
                "chest": {"rotation": [5.5 * coil - 5.5 * toss, -3.5 * coil + 8.0 * toss, 1.2 * coil - 2.2 * toss]},
                "neck":  {"position": [0.0, 0.5 * toss, -1.2 * toss],
                          "rotation": [22.0 * coil - 24.0 * toss, -14.0 * coil + 16.0 * toss, 10.0 * coil - 12.0 * toss]},
                "head":  {"rotation": [16.0 * coil - 26.0 * toss, -12.0 * coil + 14.0 * toss, 14.0 * coil - 16.0 * toss]},
            }
            active = max(coil, toss)
            rig.set_face(p, squint=0.50 * active, angry=active, jaw=10.0 * toss,
                         ear_l=(-14.0 * active, -26.0 * active, 10.0 * active))
            targs = rig.planted_targets()
            step_fl = _clamp((t - 0.24) / 0.24, 0.0, 1.0)
            ret_fl = _clamp((t - 0.72) / 0.34, 0.0, 1.0)
            fl_adv = ease(step_fl) * (1.0 - ease(ret_fl))
            fl_lift = (1.6 * math.sin(math.pi * step_fl) if 0.24 <= t <= 0.48 else 0.0) + (
                      1.5 * math.sin(math.pi * ret_fl) if 0.72 <= t <= 1.06 else 0.0)
            sp_fl = rig.legs["fl"]
            targs["fl"] = {
                "pos": [sp_fl["p3"][0] + 1.5 * fl_adv, sp_fl["rest_y"] + fl_lift, sp_fl["p3"][2] - 2.0 * fl_adv],
                "toe": 9.0 * (fl_lift / 1.6),
            }
            rig.solve_legs(p, targs)
            return p, 10.0 + 30.0 * toss, -20.0 * coil + 32.0 * toss
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.06)

    clips["gore"] = make_gore()

    # ------------------------------------------------------------------------------------------------------------------
    # 8. PAW_GROUND (1.80s hold) — Iconic bull territorial warning! Low angry glare + two front-left hoof dirt scrapes!
    # ------------------------------------------------------------------------------------------------------------------
    def make_paw_ground():
        L, N = 1.80, 36
        def frame(i, t):
            engage = pulse(t, 0.04, 0.24, 1.24, 0.28)
            s1 = math.sin(math.pi * _clamp((t - 0.34) / 0.48, 0.0, 1.0)) if 0.34 <= t <= 0.82 else 0.0
            s2 = math.sin(math.pi * _clamp((t - 0.92) / 0.48, 0.0, 1.0)) if 0.92 <= t <= 1.40 else 0.0
            u1 = _clamp((t - 0.34) / 0.48, 0.0, 1.0)
            u2 = _clamp((t - 0.92) / 0.48, 0.0, 1.0)
            paw_arc = max(s1, s2)
            scrape_z = scrape_y = scrape_toe = 0.0
            if 0.34 <= t <= 0.82:
                scrape_z = -3.8 * math.sin(math.pi * min(1.0, u1 / 0.55)) + 3.2 * math.sin(math.pi * _clamp((u1 - 0.42) / 0.48, 0.0, 1.0))
                scrape_y = 2.8 * math.sin(math.pi * min(1.0, u1 / 0.50)) + 0.35 * math.sin(math.pi * _clamp((u1 - 0.50) / 0.50, 0.0, 1.0))
                scrape_toe = 18.0 * math.sin(math.pi * min(1.0, u1 / 0.55))
            elif 0.92 <= t <= 1.40:
                scrape_z = -4.2 * math.sin(math.pi * min(1.0, u2 / 0.55)) + 3.6 * math.sin(math.pi * _clamp((u2 - 0.42) / 0.48, 0.0, 1.0))
                scrape_y = 3.1 * math.sin(math.pi * min(1.0, u2 / 0.50)) + 0.35 * math.sin(math.pi * _clamp((u2 - 0.50) / 0.50, 0.0, 1.0))
                scrape_toe = 20.0 * math.sin(math.pi * min(1.0, u2 / 0.55))

            shift_x = -1.2 * engage - 0.6 * paw_arc
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [shift_x * 0.6, -0.75 * engage, 0.6 * engage],
                          "rotation": [2.2 * engage, -3.0 * engage, -2.5 * engage]},
                "chest": {"rotation": [4.5 * engage + 2.0 * paw_arc, 4.0 * engage, 3.0 * engage]},
                "neck":  {"rotation": [18.0 * engage + 4.0 * paw_arc, -2.0 * engage, 0.0]},
                "head":  {"rotation": [12.0 * engage - 4.0 * paw_arc, 2.0 * engage, -3.0 * engage]},
            }
            rig.set_face(p, squint=0.55 * engage, angry=engage, jaw=4.0 * paw_arc,
                         ear_l=(-10.0 * engage, -22.0 * engage, 8.0 * engage))
            targs = rig.planted_targets()
            sp_fl = rig.legs["fl"]
            targs["fl"] = {
                "pos": [sp_fl["p3"][0], sp_fl["rest_y"] + scrape_y, sp_fl["p3"][2] + scrape_z],
                "toe": scrape_toe,
            }
            rig.solve_legs(p, targs)
            return p, 14.0 + 18.0 * engage, 26.0 * engage * math.sin(2.0 * math.pi * t * 1.9)
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.07)

    clips["paw_ground"] = make_paw_ground()

    # ------------------------------------------------------------------------------------------------------------------
    # 9. BELLOW (1.80s hold) — Territorial savanna bellow! Wide 34-deg mouth opening revealing palate, teeth & tongue!
    # ------------------------------------------------------------------------------------------------------------------
    def make_bellow():
        L, N = 1.80, 36
        def frame(i, t):
            inhale = pulse(t, 0.0, 0.28, 0.06, 0.16)
            roar = pulse(t, 0.28, 0.20, 0.78, 0.44)
            trem = math.sin(2.0 * math.pi * t * 8.0) * roar
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.55 * inhale - 0.30 * roar, 0.8 * inhale - 0.6 * roar],
                          "rotation": [2.2 * inhale - 2.0 * roar, 0.0, 0.0]},
                "chest": {"rotation": [3.5 * inhale - 4.5 * roar + 0.8 * trem, 0.0, 0.0]},
                "neck":  {"position": [0.0, 0.5 * roar, -0.8 * roar],
                          "rotation": [8.0 * inhale - 14.0 * roar + 1.0 * trem, 0.0, 0.0]},
                "head":  {"rotation": [4.0 * inhale - 16.0 * roar + 1.2 * trem, 0.0, 0.0]},
            }
            jaw_open = 34.0 * roar + 1.8 * trem
            rig.set_face(p, squint=0.35 * roar, angry=0.85 * roar,
                         jaw=jaw_open, tongue_rx=-7.0 * roar + 2.0 * trem, tongue_tip_rx=11.0 * roar,
                         ear_l=(-8.0 * roar, -18.0 * roar, 6.0 * roar))
            rig.solve_legs(p, rig.planted_targets())
            return p, 12.0 + 18.0 * roar, 18.0 * roar * math.sin(2.0 * math.pi * t * 2.2)
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.07)

    clips["bellow"] = make_bellow()

    # ------------------------------------------------------------------------------------------------------------------
    # 10. EAR_FLUTTER (1.50s hold) — Classic African Buffalo rapid double ear-flutter + blink + subtle head twitch!
    # ------------------------------------------------------------------------------------------------------------------
    def make_ear_flutter():
        L, N = 1.50, 45
        def frame(i, t):
            # Two crisp classic savanna buffalo ear-flutter bursts (slight L/R offset like real oxpecker-shooing!)
            fl_l = _ear_flutter_wave(t, 0.12, 0.44, 9.5, 30.0) + _ear_flutter_wave(t, 0.78, 0.46, 9.0, 26.0)
            fl_r = _ear_flutter_wave(t, 0.16, 0.44, 9.5, 30.0) + _ear_flutter_wave(t, 0.74, 0.46, 9.0, 26.0)
            blink = max(pulse(t, 0.20, 0.06, 0.05, 0.09), pulse(t, 0.92, 0.06, 0.05, 0.09))
            tw = 3.2 * _ear_flutter_wave(t, 0.12, 0.44, 4.5, 1.0) - 2.4 * _ear_flutter_wave(t, 0.76, 0.46, 4.2, 1.0)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.15, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "chest": {"rotation": [0.0, tw * 0.25, 0.0]},
                "neck":  {"rotation": [1.5 * math.sin(math.pi * t / L), tw * 0.7, tw * 0.35]},
                "head":  {"rotation": [-2.0 * math.sin(math.pi * t / L), tw * 1.1, tw * 0.7]},
            }
            rig.set_face(p, blink=blink,
                         ear_l=(fl_l * 0.32, fl_l, fl_l * 0.50),
                         ear_r=(fl_r * 0.32, -fl_r, -fl_r * 0.50))
            rig.solve_legs(p, rig.planted_targets())
            sway = 26.0 * pulse(t, 0.15, 0.12, 0.55, 0.30) * math.sin(2.0 * math.pi * t * 2.6)
            return p, 8.0 + 6.0 * abs(sway) / 26.0, sway
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.07)

    clips["ear_flutter"] = make_ear_flutter()

    # ------------------------------------------------------------------------------------------------------------------
    # 11. SNORT (1.40s hold) — Classic bull warning huff! Snaps head alert + rapid double chest-heave exhale & ear flick!
    # ------------------------------------------------------------------------------------------------------------------
    def make_snort():
        L, N = 1.40, 36
        def frame(i, t):
            engage = pulse(t, 0.04, 0.20, 0.88, 0.26)
            huff1 = pulse(t, 0.22, 0.10, 0.06, 0.14)
            huff2 = pulse(t, 0.62, 0.10, 0.06, 0.16)
            huff = max(huff1, huff2)
            ef = _ear_flutter_wave(t, 0.66, 0.34, 8.5, 18.0)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.35 * engage - 0.45 * huff, 0.5 * huff],
                          "rotation": [-1.2 * engage + 2.0 * huff, 0.0, 0.0]},
                "chest": {"rotation": [-2.5 * engage + 3.8 * huff, 0.0, 0.0]},
                "neck":  {"position": [0.0, 0.4 * engage - 0.3 * huff, -0.6 * huff],
                          "rotation": [-8.0 * engage + 9.5 * huff, 0.0, 0.0]},
                "head":  {"rotation": [-7.0 * engage + 11.0 * huff, 0.0, 0.0]},
            }
            rig.set_face(p, squint=0.35 * huff, angry=0.65 * engage, jaw=5.0 * huff,
                         ear_l=(4.0 * engage - 12.0 * huff + ef * 0.2, 8.0 * engage - 16.0 * huff + ef, ef * 0.4),
                         ear_r=(4.0 * engage - 12.0 * huff + ef * 0.2, -8.0 * engage + 16.0 * huff - ef, -ef * 0.4))
            rig.solve_legs(p, rig.planted_targets())
            return p, 14.0 + 12.0 * huff, 16.0 * huff * math.sin(2.0 * math.pi * t * 3.0)
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.065)

    clips["snort"] = make_snort()

    # ------------------------------------------------------------------------------------------------------------------
    # 12. HORN_RUB (2.60s hold) — Fun wild behavior! Dips boss low & vigorously rubs/polishes curved horns side-to-side!
    # ------------------------------------------------------------------------------------------------------------------
    def make_horn_rub():
        L, N = 2.60, 52
        def frame(i, t):
            down = pulse(t, 0.06, 0.42, 1.56, 0.48)
            rub = math.sin(2.0 * math.pi * (t - 0.40) * 1.45) * pulse(t, 0.38, 0.20, 1.32, 0.28)
            blink = max(pulse(t, 0.85, 0.08, 0.05, 0.10), pulse(t, 1.95, 0.08, 0.05, 0.10))
            ef = _ear_flutter_wave(t, 2.02, 0.36, 8.5, 22.0)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -1.20 * down, 0.5 * down],
                          "rotation": [2.8 * down, 1.8 * rub, 0.8 * rub]},
                "chest": {"rotation": [5.0 * down, 3.0 * rub, 1.0 * rub]},
                "neck":  {"position": [0.0, -0.4 * down, -0.8 * down],
                          "rotation": [32.0 * down, 15.0 * rub, 13.0 * rub]},
                "head":  {"rotation": [16.0 * down, 17.0 * rub, 19.0 * rub]},
            }
            rig.set_face(p, blink=blink, squint=0.30 * down, angry=0.35 * down, jaw=3.0 * abs(rub),
                         ear_l=(-8.0 * down + ef * 0.25, -14.0 * down + ef, 6.0 * down + ef * 0.4),
                         ear_r=(-8.0 * down + ef * 0.25, 14.0 * down - ef, -6.0 * down - ef * 0.4))
            rig.solve_legs(p, rig.planted_targets())
            return p, 12.0 + 10.0 * down, 20.0 * rub
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.075)

    clips["horn_rub"] = make_horn_rub()

    # ------------------------------------------------------------------------------------------------------------------
    # 13. SHAKE_OFF (1.50s hold) — Travelling torsional dust/mud shake from nose & horns down through shoulders to tail!
    # ------------------------------------------------------------------------------------------------------------------
    def make_shake_off():
        L, N = 1.50, 36
        def frame(i, t):
            env = pulse(t, 0.10, 0.20, 0.80, 0.36)
            w = 2.0 * math.pi * 4.5
            sh_head  = env * math.sin(w * t)
            sh_neck  = env * math.sin(w * (t - 0.035))
            sh_chest = env * math.sin(w * (t - 0.075))
            sh_hips  = env * math.sin(w * (t - 0.120))
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.78 * env, 0.0], "rotation": [0.0, 2.2 * sh_hips, 3.4 * sh_hips]},
                "chest": {"rotation": [2.0 * env, 3.8 * sh_chest, 5.5 * sh_chest]},
                "neck":  {"rotation": [8.0 * env, 7.5 * sh_neck, 16.0 * sh_neck]},
                "head":  {"rotation": [4.0 * env, 9.0 * sh_head, 22.0 * sh_head]},
            }
            ear_lag = env * math.sin(w * (t - 0.055))
            rig.set_face(p, blink=0.85 * env,
                         ear_l=(4.0 * env, 14.0 * ear_lag, 24.0 * ear_lag),
                         ear_r=(4.0 * env, 14.0 * ear_lag, 24.0 * ear_lag))
            rig.solve_legs(p, rig.planted_targets())
            return p, 14.0 + 14.0 * env, 32.0 * sh_hips
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.055)

    clips["shake_off"] = make_shake_off()

    # ------------------------------------------------------------------------------------------------------------------
    # 14. GRAZE (4.00s hold) — Smoothly lowers muzzle to grass, chews 3 rhythmic bites, blinks, ear flutters & lifts!
    # ------------------------------------------------------------------------------------------------------------------
    def make_graze():
        L, N = 4.00, 56
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            down = pulse(t, 0.08, 0.65, 2.45, 0.72)
            chew = math.sin(2.0 * math.pi * t * 1.8) * down
            jaw_open = (6.0 + 5.5 * math.sin(2.0 * math.pi * t * 1.8)) * down
            jaw_yaw = 3.0 * math.cos(2.0 * math.pi * t * 1.8) * down
            blink = max(pulse(t, 1.35, 0.07, 0.05, 0.09), pulse(t, 2.85, 0.07, 0.05, 0.09))
            ef_l = _ear_flutter_wave(t, 1.95, 0.38, 8.5, 22.0)
            ef_r = _ear_flutter_wave(t, 2.00, 0.38, 8.5, 22.0)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.15 - 0.75 * down, 0.0], "rotation": [2.2 * down, 0.0, 0.4 * math.sin(ph)]},
                "chest": {"rotation": [4.5 * down, 1.4 * math.sin(ph) * down, 0.0]},
                "neck":  {"rotation": [(38.0 + 3.5 * math.sin(ph * 2.0)) * down, 4.5 * math.sin(ph) * down, 0.0]},
                "head":  {"rotation": [(16.0 - 2.5 * chew) * down, 5.5 * math.sin(ph) * down, 0.0]},
            }
            rig.set_face(p, blink=blink, jaw=jaw_open, jaw_yaw=jaw_yaw,
                         tongue_rx=-3.0 * (0.5 + 0.5 * chew) * down,
                         ear_l=(-6.0 * down + ef_l * 0.25, -5.0 * down + ef_l, 3.0 * down + ef_l * 0.4),
                         ear_r=(-6.0 * down + ef_r * 0.25, 5.0 * down - ef_r, -3.0 * down - ef_r * 0.4))
            rig.solve_legs(p, rig.planted_targets())
            return p, 6.0, 12.0 * math.sin(ph) + 8.0 * math.sin(2.0 * ph)
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.085)

    clips["graze"] = make_graze()

    # ------------------------------------------------------------------------------------------------------------------
    # 15. DRINK (3.60s hold) — Smoothly dips snout to water, calm sipping pulses, blinks & lifts cleanly back!
    # ------------------------------------------------------------------------------------------------------------------
    def make_drink():
        L, N = 3.60, 44
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            down = pulse(t, 0.08, 0.65, 2.05, 0.72)
            sip = (0.5 + 0.5 * math.sin(2.0 * math.pi * t * 1.4)) * down
            blink = max(pulse(t, 1.15, 0.07, 0.05, 0.09), pulse(t, 2.55, 0.07, 0.05, 0.09))
            ef = _ear_flutter_wave(t, 1.75, 0.34, 8.0, 18.0)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.15 - 1.15 * down - 0.15 * sip, 0.0], "rotation": [3.0 * down, 0.0, 0.0]},
                "chest": {"rotation": [6.0 * down, 0.0, 0.0]},
                "neck":  {"rotation": [(40.0 + 2.5 * sip) * down, 1.5 * math.sin(ph) * down, 0.0]},
                "head":  {"rotation": [(12.0 + 2.0 * sip) * down, 0.0, 0.0]},
            }
            rig.set_face(p, blink=blink, jaw=(4.0 + 3.5 * sip) * down, tongue_rx=-2.0 * sip,
                         ear_l=(-6.0 * down + ef * 0.2, -8.0 * down + ef, 2.0 * down + ef * 0.35),
                         ear_r=(-6.0 * down + ef * 0.2, 8.0 * down - ef, -2.0 * down - ef * 0.35))
            rig.solve_legs(p, rig.planted_targets())
            return p, 6.0, 10.0 * math.sin(ph)
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.085)

    clips["drink"] = make_drink()

    # ------------------------------------------------------------------------------------------------------------------
    # 16–18. REST_DOWN (2.40s), REST (4.40s), REST_UP (2.40s) — SLOW, HEAVY, GROUNDED EXACT-IK BOVINE REST!
    # ZERO FLOATING LEGS!! All 4 hooves stay planted/gliding right on y=0.05 px via exact 2-bone IK all the way down
    # as front knees lower onto the grass first and hindquarters slowly settle onto folded hocks!
    # ------------------------------------------------------------------------------------------------------------------
    def _grounded_rest_pose(rig_obj, k_fl, k_fr, k_hind, breath=0.0, chew_t=0.0,
                            blink=0.0, squint=0.35, ear_l_fl=0.0, ear_r_fl=0.0, trunk=None):
        """Exact 2-bone IK grounded bovine rest pose:
        - k_fl, k_fr, k_hind in [0..1]: 0 = full standing, 1 = relaxed folded-leg loaf on y=0.05 px!
        - Front hooves glide smoothly back under the chest at y=3.45 (sole at y=0.05) while front knees descend onto grass!
        - Hind hooves glide smoothly forward/outward beside belly at y=3.45 (sole at y=0.05) while hocks lower onto grass!
        """
        kf = 0.5 * (k_fl + k_fr)
        k_avg = 0.5 * (kf + k_hind)
        # Drop front chest & rear hips so folded front knees and hind hocks rest cleanly ON y=0.05..0.35 px (0 clip, 0 float!)
        drop_f = -7.8 * kf
        drop_h = -8.4 * k_hind
        # Exact lever weighting around hips pivot (z=+11.4): front is 14.6 px ahead, hind is 8.4 px behind!
        hip_dy = 0.25 * drop_f + 0.75 * drop_h + 0.14 * breath * k_avg
        trunk_pitch = _clamp((drop_h - drop_f) * 1.45, -18.0, 18.0)

        jaw_open = (3.5 + 3.5 * math.sin(chew_t)) * k_avg if chew_t != 0.0 else 0.0
        jaw_yaw = 3.0 * math.cos(chew_t) * k_avg if chew_t != 0.0 else 0.0
        p = {
            "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
            "hips":  {"position": [0.0, hip_dy, 0.0], "rotation": [trunk_pitch, 0.0, 0.0]},
            "chest": {"rotation": [-trunk_pitch * 0.35 - 0.6 * breath * k_avg, 0.0, 0.0]},
            "neck":  {"rotation": [-trunk_pitch * 0.45 + 3.0 * k_avg + 0.7 * breath * k_avg, 2.5 * k_avg, 0.0]},
            "head":  {"rotation": [-trunk_pitch * 0.20 - 3.0 * k_avg + 1.2 * breath * k_avg, 3.5 * k_avg, 0.0]},
        }
        rig_obj.set_face(p, blink=blink, squint=squint * k_avg, jaw=jaw_open, jaw_yaw=jaw_yaw,
                         tongue_rx=-2.0 * (0.5 + 0.5 * math.sin(chew_t)) * k_avg if chew_t != 0.0 else 0.0,
                         ear_l=(-4.0 * k_avg + ear_l_fl * 0.25, ear_l_fl, ear_l_fl * 0.45),
                         ear_r=(-4.0 * k_avg + ear_r_fl * 0.25, -ear_r_fl, -ear_r_fl * 0.45))

        # Exact 2-bone IK targets keeping ALL 4 HOOVES 100% ON THE GROUND (y = rest_y = 3.4 -> sole at y = 0.00 px!)
        targs = {}
        for tag, k_leg in (("fl", k_fl), ("fr", k_fr)):
            sp = rig_obj.legs[tag]
            s = sp["side"]
            # Front hoof glides smoothly backward under chest (+4.8 px) and slightly outward (+0.6 px) on the ground!
            # Front knee (carpus, pole -Z) smoothly lowers right down onto the grass (y_knee ~ 2.0 px)!
            targs[tag] = {
                "pos": [sp["p3"][0] + 0.65 * s * k_leg, sp["rest_y"] + 0.08 * k_leg, sp["p3"][2] + 4.8 * k_leg],
                "toe": 0.0,
                "pole": [0.12 * s * k_leg, 0.0, -1.0],
            }
        for tag in ("bl", "br"):
            sp = rig_obj.legs[tag]
            s = sp["side"]
            # Hind hoof stays 100% ON THE GROUND (sole at y=0.05 px!), gliding gently forward (-4.2 px) and slightly
            # outward (+1.35 px) beside the belly as the hindquarters slowly settle down onto the folded hocks!
            targs[tag] = {
                "pos": [sp["p3"][0] + 1.35 * s * k_hind, sp["rest_y"] + 0.08 * k_hind, sp["p3"][2] - 4.2 * k_hind],
                "toe": 0.0,
                "pole": [0.22 * s * k_hind, 0.0, 1.0],
            }
        if trunk is not None:
            trunk(p)                    # the trunk moved before the legs are solved, so the hooves stay where they are
        rig_obj.solve_legs(p, targs, reach=0.994)
        return p

    def make_rest_down():
        # Slow, heavy, relaxed 2.40s African Buffalo lie-down!
        # Front kneels slowly (fl slightly ahead of fr, 0.05..1.30s), then hindquarters settle heavily (0.90..2.25s)!
        L, N = 2.40, 48
        def frame(i, t):
            k_fl = ease(_clamp((t - 0.04) / 1.18, 0.0, 1.0))
            k_fr = ease(_clamp((t - 0.16) / 1.18, 0.0, 1.0))
            k_h  = ease(_clamp((t - 0.88) / 1.38, 0.0, 1.0))
            blink = pulse(t, 1.35, 0.09, 0.08, 0.12)
            ef = _ear_flutter_wave(t, 1.90, 0.36, 8.0, 16.0)
            p = _grounded_rest_pose(rig, k_fl, k_fr, k_h, blink=blink, squint=0.35, ear_l_fl=ef, ear_r_fl=ef * 0.4)
            # Tail curls cleanly up & aside as hindquarters lower so switch never clips y=0
            return p, 5.0 + 46.0 * k_h, 18.0 * k_h
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.09)

    def make_rest():
        # Relaxed 4.40s ruminating buffalo loaf! Slow deep breathing, cud-chew, sleepy blinks & classic ear flutters!
        L, N = 4.40, 66
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            breath = math.sin(ph)
            blink = max(pulse(t, 0.90, 0.10, 0.14, 0.14), pulse(t, 2.95, 0.10, 0.16, 0.14))
            ef_l = _ear_flutter_wave(t, 1.95, 0.42, 8.5, 24.0)
            ef_r = _ear_flutter_wave(t, 2.00, 0.42, 8.5, 24.0)
            p = _grounded_rest_pose(rig, 1.0, 1.0, 1.0, breath=breath, chew_t=ph * 5.0,
                                    blink=blink, squint=0.35, ear_l_fl=ef_l, ear_r_fl=ef_r)
            return p, 51.0 + 3.5 * math.sin(ph), 18.0 + 7.0 * math.sin(ph)
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.095)

    def make_rest_up():
        # Slow, heavy, relaxed 2.40s bovine stand-up!
        # Shifts forward onto front knees, heaves heavy hindquarters up FIRST (0.08..1.35s),
        # then pushes front-left up (1.05..2.12s) and front-right up (1.22..2.30s) into full standing!
        L, N = 2.40, 48
        def frame(i, t):
            k_h  = 1.0 - ease(_clamp((t - 0.08) / 1.28, 0.0, 1.0))
            k_fl = 1.0 - ease(_clamp((t - 1.02) / 1.12, 0.0, 1.0))
            k_fr = 1.0 - ease(_clamp((t - 1.18) / 1.12, 0.0, 1.0))
            blink = pulse(t, 1.75, 0.08, 0.06, 0.10)
            ef = _ear_flutter_wave(t, 1.92, 0.36, 8.5, 20.0)
            p = _grounded_rest_pose(rig, k_fl, k_fr, k_h, blink=blink, squint=0.35, ear_l_fl=ef, ear_r_fl=ef)
            return p, 5.0 + 46.0 * k_h, 18.0 * k_h
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.09)

    clips["rest_down"] = make_rest_down()
    clips["rest"]      = make_rest()
    clips["rest_up"]   = make_rest_up()

    # ------------------------------------------------------------------------------------------------------------------
    # 19 & 20. MOOD_ALERT & MOOD_ANGRY (Savanna herd lookout & territorial standoff!)
    # ------------------------------------------------------------------------------------------------------------------
    def make_mood_alert():
        L, N = 2.20, 34
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            scan = math.sin(ph)
            blink = pulse(t, 1.05, 0.06, 0.04, 0.08)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.10, 0.0], "rotation": [-1.2, 0.0, 0.0]},
                "chest": {"rotation": [-2.2, 1.5 * scan, 0.0]},
                "neck":  {"rotation": [-11.0, 5.5 * scan, 0.0]},
                "head":  {"rotation": [-8.0, 10.0 * scan, 0.0]},
            }
            rig.set_face(p, blink=blink, ear_l=(8.0, 16.0, -6.0))
            rig.solve_legs(p, rig.planted_targets())
            return p, 12.0, 6.0 * math.sin(ph)
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.08)

    def make_mood_angry():
        L, N = 1.80, 30
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            huff = math.sin(ph * 2.0)
            p = {
                "root":  {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.75 + 0.14 * huff, 0.4], "rotation": [2.2, 0.0, 0.0]},
                "chest": {"rotation": [4.0 - 1.2 * huff, 0.0, 0.0]},
                "neck":  {"rotation": [16.0 + 2.0 * huff, 2.5 * math.sin(ph), 0.0]},
                "head":  {"rotation": [12.0 - 1.5 * huff, 3.0 * math.sin(ph), 0.0]},
            }
            rig.set_face(p, squint=0.50, angry=1.0, jaw=3.0 + 2.0 * max(0.0, huff),
                         ear_l=(-12.0, -24.0, 8.0))
            rig.solve_legs(p, rig.planted_targets(dx_front=0.6))
            return p, 24.0 + 4.0 * huff, 24.0 * math.sin(ph * 2.0)
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.06)

    clips["mood_alert"] = make_mood_alert()
    clips["mood_angry"] = make_mood_angry()

    # ------------------------------------------------------------------------------------------------------------------
    # 21. DEATH (1.40s hold) — Heavy weighted collapse onto knees then right flank, 0.00 px ground clip!
    # ------------------------------------------------------------------------------------------------------------------
    def make_death(down=False):
        # down (death_down, the Motion Studio phase 3, step 3): dying where it lies (held down by lions, resting, a calf
        # pinned): no buckle (it is down already), a last gasp, then the same fall onto its flank and the same last pose.
        L, N = 1.40, 28
        def frame(i, t):
            if down:
                buckle = 1.0
                fall   = ease(_clamp((t - 0.30) / 0.78, 0.0, 1.0))
                b0     = 1.02
                eyes   = ease(_clamp((t - 0.30) / 0.36, 0.0, 1.0))
                gasp   = pulse(t, 0.02, 0.12, 0.08, 0.16)
            else:
                buckle = ease(_clamp(t / 0.56, 0.0, 1.0))
                fall   = ease(_clamp((t - 0.42) / 0.78, 0.0, 1.0))
                b0     = 1.14
                eyes   = ease(_clamp((t - 0.14) / 0.36, 0.0, 1.0))
                gasp   = 0.0
            bounce = 0.45 * math.sin(math.pi * _clamp((t - b0) / 0.22, 0.0, 1.0)) if b0 <= t <= b0 + 0.22 else 0.0
            p = _grounded_rest_pose(rig, buckle, buckle, buckle, squint=0.0, blink=eyes)
            base_dy = p["hips"]["position"][1]
            dy = base_dy + 0.85 * buckle + 4.2 * math.sin(math.pi * fall) + 0.4 * fall + bounce
            roll_z = -78.0 * fall
            p["hips"]  = {"position": [0.0, dy, 0.0], "rotation": [1.5 * buckle * (1.0 - 0.7 * fall), 0.0, roll_z]}
            p["chest"] = {"rotation": [0.8 * buckle, -4.0 * fall, 4.0 * fall]}
            p["neck"]  = {"rotation": [9.0 * buckle - 4.0 * fall - 10.0 * gasp, -6.0 * fall, 20.0 * fall]}
            p["head"]  = {"rotation": [5.0 * buckle - 5.0 * fall - 14.0 * gasp, -5.0 * fall, 18.0 * fall]}
            rig.set_face(p, blink=eyes,
                         jaw=12.0 * fall + 16.0 * gasp, tongue_rx=4.0 * fall,
                         ear_l=(-8.0 * fall, -14.0 * fall, 16.0 * fall),
                         ear_r=(-6.0 * fall, 10.0 * fall, -12.0 * fall))
            f_rel = ease(_clamp((fall - 0.20) / 0.80, 0.0, 1.0))
            for tag, ext in (("fl", (28.0, -32.0, 16.0,  16.0)),
                             ("fr", ( 8.0,  -8.0,  6.0, -34.0)),
                             ("bl", (26.0, -24.0, 10.0,  18.0)),
                             ("br", ( 8.0,  -8.0,  6.0, -34.0))):
                r1 = list(p[f"leg_{tag}_1"]["rotation"])
                r2 = list(p[f"leg_{tag}_2"]["rotation"])
                rf = list(p[f"leg_{tag}_foot"]["rotation"])
                p[f"leg_{tag}_1"]    = {"rotation": [r1[0] + ext[0] * f_rel, r1[1], r1[2] + ext[3] * fall]}
                p[f"leg_{tag}_2"]    = {"rotation": [r2[0] + ext[1] * f_rel, r2[1], r2[2]]}
                p[f"leg_{tag}_foot"] = {"rotation": [rf[0] + ext[2] * f_rel, rf[1], rf[2]]}
            return p, 5.0 + 36.0 * buckle - 18.0 * fall, 16.0 * buckle
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.09)

    clips["death"] = make_death()
    clips["death_down"] = make_death(down=True)

    # ------------------------------------------------------------------------------------------------------------------
    # 21b & 21c. HELD BY LIONS (the Motion Studio phase 3, step 3): held by two or more lionesses it is brought down
    # (held_sink) and struggles on the ground (held_down). All four legs buckle together, so every spot a lioness holds
    # (the haunch, the flanks, the throat) goes down together: one holder clip fits them all (tools/lion_anims.py makes
    # hold_sink against this clip's spots). Both end in, and stay near, the rest pose, so rest_up and death_down follow.
    # ------------------------------------------------------------------------------------------------------------------
    def make_held_sink():
        # 2.0 s: a struggle on its feet (its head thrown back at the lions, horns hooking side to side, a bellow, a
        # stagger), then its legs give way together (0.75..1.55 s) and it sinks onto its brisket with a thud.
        L, N = 2.00, 40
        def frame(i, t):
            k = ease(_clamp((t - 0.75) / 0.80, 0.0, 1.0))
            thud = math.sin(math.pi * _clamp((t - 1.50) / 0.20, 0.0, 1.0)) if 1.50 <= t <= 1.70 else 0.0
            toss = pulse(t, 0.06, 0.16, 0.26, 0.30)
            hook = math.sin(2.0 * math.pi * t / 0.85) * pulse(t, 0.02, 0.18, 0.95, 0.45)
            roar = pulse(t, 0.22, 0.12, 0.42, 0.30)
            stag = math.sin(2.0 * math.pi * t * 1.4) * (1.0 - k)
            def trunk(q):
                q["hips"]["position"][1] += -0.30 * thud
                q["hips"]["rotation"][2] += 2.2 * stag
                q["chest"]["rotation"][1] += 0.4 * hook
            p = _grounded_rest_pose(rig, k, k, k, squint=0.0, trunk=trunk)
            p["neck"]["rotation"][0] += -5.0 * toss
            p["neck"]["rotation"][1] += 1.0 * hook          # the hooking is in its head: its neck (the throat holders' spots) held
            p["head"]["rotation"][0] += -24.0 * toss + 6.0 * thud
            p["head"]["rotation"][1] += 16.0 * hook
            p["head"]["rotation"][2] += 8.0 * hook
            rig.set_face(p, blink=pulse(t, 1.52, 0.05, 0.06, 0.10), squint=0.30, angry=0.9 * (1.0 - 0.5 * k),
                         jaw=26.0 * roar + 4.0 * k, tongue_rx=-5.0 * roar,
                         ear_l=(-14.0, -22.0 - 6.0 * toss, 8.0))
            return p, 14.0 + 10.0 * toss, 34.0 + 10.0 * hook * (1.0 - k)   # lashing on its right, off the left haunch
        return _bake_clip(rig, L, N, frame, loop="hold", tail_lag=0.08)

    def make_held_down():
        # 2.4 s loop: on its brisket under the lions, heaving, a bellow, a try to push up on its front knees that fails.
        # Made against its holders (tools/clip_check.py --pair): its trunk barely moves (their spots), its head is held
        # (a small swing, not a sweep into the lionesses at its throat), its ears flat back, its tail clamped down over
        # its right buttock (the lioness at its haunch bites the left one, beside the tail's root). The struggle is in the head's toss, the breath and the knees.
        L, N = 2.40, 36
        def frame(i, t):
            ph = 2.0 * math.pi * t / L
            heave = math.sin(2.0 * ph)
            swing = math.sin(ph)
            roar = pulse(t, 0.30, 0.14, 0.40, 0.30)
            push = pulse(t, 1.35, 0.25, 0.20, 0.35)
            kf = 1.0 - 0.10 * push
            p = _grounded_rest_pose(rig, kf, kf, 1.0, breath=1.6 * heave, squint=0.40)
            p["neck"]["rotation"][1] += 1.5 * swing
            p["neck"]["rotation"][0] += -4.0 * roar
            p["head"]["rotation"][0] += -14.0 * roar + 5.0 * push
            p["head"]["rotation"][1] += 4.0 * swing
            p["head"]["rotation"][2] += 3.0 * swing
            rig.set_face(p, blink=pulse(t, 2.05, 0.06, 0.05, 0.10), squint=0.40, angry=0.7,
                         jaw=4.0 + 24.0 * roar, tongue_rx=-4.0 * roar,
                         ear_l=(-30.0, -34.0, 14.0))
            return p, 14.0 + 4.0 * abs(swing), 34.0 + 5.0 * swing   # its tail clamped down over its right buttock
        return _bake_clip(rig, L, N, frame, loop=True, tail_lag=0.08)

    clips["held_sink"] = make_held_sink()
    clips["held_down"] = make_held_down()

    # ------------------------------------------------------------------------------------------------------------------
    # 22 & 23. BLOCKMOVES: step_up_1000 & step_down_1000 (1-block = 16.0 px ledge climb & descent!)
    # ------------------------------------------------------------------------------------------------------------------
    def make_step_up():
        B = 16.0
        L = 1.60
        steps = 32
        ahead_px = 33.5
        face_z = -ahead_px
        front_z = rig.legs["fl"]["p3"][2]
        hind_z  = rig.legs["bl"]["p3"][2]
        travel = round(hind_z - (face_z - 8.0), 2)

        fl_mid_z = face_z - 7.5
        fr_mid_z = face_z - 8.5
        bl_mid_z = face_z + 10.0
        br_mid_z = face_z + 11.0

        plans = {
            "fl": [
                (rig.legs["fl"]["p3"][0], rig.legs["fl"]["rest_y"], front_z, 0.0),
                (0.05, 0.35, (rig.legs["fl"]["p3"][0], rig.legs["fl"]["rest_y"], face_z + 7.5, 0.0)),
                (0.45, 0.80, (rig.legs["fl"]["p3"][0], rig.legs["fl"]["rest_y"] + B, fl_mid_z, 0.0)),
                (1.12, 1.46, (rig.legs["fl"]["p3"][0], rig.legs["fl"]["rest_y"] + B, front_z - travel, 0.0)),
            ],
            "fr": [
                (rig.legs["fr"]["p3"][0], rig.legs["fr"]["rest_y"], front_z, 0.0),
                (0.18, 0.48, (rig.legs["fr"]["p3"][0], rig.legs["fr"]["rest_y"], face_z + 8.5, 0.0)),
                (0.62, 0.96, (rig.legs["fr"]["p3"][0], rig.legs["fr"]["rest_y"] + B, fr_mid_z, 0.0)),
                (1.24, 1.56, (rig.legs["fr"]["p3"][0], rig.legs["fr"]["rest_y"] + B, front_z - travel, 0.0)),
            ],
            "bl": [
                (rig.legs["bl"]["p3"][0], rig.legs["bl"]["rest_y"], hind_z, 0.0),
                (0.32, 0.68, (rig.legs["bl"]["p3"][0], rig.legs["bl"]["rest_y"], bl_mid_z, 0.0)),
                (0.95, 1.32, (rig.legs["bl"]["p3"][0], rig.legs["bl"]["rest_y"] + B, hind_z - travel, 0.0)),
            ],
            "br": [
                (rig.legs["br"]["p3"][0], rig.legs["br"]["rest_y"], hind_z, 0.0),
                (0.48, 0.82, (rig.legs["br"]["p3"][0], rig.legs["br"]["rest_y"], br_mid_z, 0.0)),
                (1.12, 1.48, (rig.legs["br"]["p3"][0], rig.legs["br"]["rest_y"] + B, hind_z - travel, 0.0)),
            ],
        }
        path_rows = []

        def frame(i, t):
            u = t / L
            dz = -travel * ease(u)
            u_front = ease(_clamp((t - 0.36) / 0.54, 0.0, 1.0))
            u_back  = ease(_clamp((t - 0.88) / 0.56, 0.0, 1.0))
            dy_f = B * u_front
            dy_b = B * u_back
            dy = 0.5 * (dy_f + dy_b)
            lip_lift = 2.4 * math.sin(math.pi * _clamp((t - 0.40) / 0.66, 0.0, 1.0))
            pitch_deg = _clamp(-math.degrees(math.atan2(dy_f - dy_b, max(12.0, rig.trunk_len))), -24.0, 24.0)

            p = {
                "root":  {"position": [0.0, dy + lip_lift, dz], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.45, 0.0], "rotation": [pitch_deg, 0.0, 0.0]},
                "chest": {"rotation": [-pitch_deg * 0.14, 0.0, 0.0]},
                "neck":  {"rotation": [-pitch_deg * 0.45 - 8.0 * pulse(t, 0.05, 0.25, 0.40, 0.35), 0.0, 0.0]},
                "head":  {"rotation": [-pitch_deg * 0.45 - 6.0 * pulse(t, 0.05, 0.25, 0.40, 0.35), 0.0, 0.0]},
            }
            rig.set_face(p, ear_l=(-6.0 * math.sin(math.pi * u), 0.0, 0.0))
            targs = {}
            for tag in ("fl", "fr", "bl", "br"):
                pos_w, arc, toe = foot_at(t, plans[tag], lift=5.0, reach=(tag[0] == "f"))
                targs[tag] = {"pos": pos_w, "toe": toe}
            rig.solve_legs(p, targs)
            p["root"] = {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]}
            path_rows.append((t, dy + lip_lift, dz))
            return p, 14.0 + 24.0 * math.sin(math.pi * u), 8.0 * math.sin(2.0 * math.pi * u)

        made = _bake_clip(rig, L, steps, frame, loop="hold", tail_lag=0.07)
        rows = path_rows[: steps + 1]
        made["timeline"] = {
            "0.0": f"step {face_z:.2f} {B:.1f}",
            "0.05": "path root " + " ".join(f"{t:.4f} {dy:.3f} {dz:.3f}" for t, dy, dz in rows),
        }
        return made, ahead_px

    def make_step_down():
        B = 16.0
        L = 1.60
        steps = 32
        front_z = rig.legs["fl"]["p3"][2]
        hind_z  = rig.legs["bl"]["p3"][2]
        ahead_px = round(abs(front_z) + 6.8, 2)
        face_z = -ahead_px
        travel = round(hind_z - (face_z - 10.5), 2)

        fl_mid_z = face_z - 7.5
        fr_mid_z = face_z - 8.5
        bl_mid_z = face_z + 6.5
        br_mid_z = face_z + 7.0

        plans = {
            "fl": [
                (rig.legs["fl"]["p3"][0], rig.legs["fl"]["rest_y"], front_z, 0.0),
                (0.08, 0.46, (rig.legs["fl"]["p3"][0], rig.legs["fl"]["rest_y"] - B, fl_mid_z, 0.0)),
                (0.88, 1.24, (rig.legs["fl"]["p3"][0], rig.legs["fl"]["rest_y"] - B, front_z - travel, 0.0)),
            ],
            "fr": [
                (rig.legs["fr"]["p3"][0], rig.legs["fr"]["rest_y"], front_z, 0.0),
                (0.24, 0.62, (rig.legs["fr"]["p3"][0], rig.legs["fr"]["rest_y"] - B, fr_mid_z, 0.0)),
                (1.06, 1.44, (rig.legs["fr"]["p3"][0], rig.legs["fr"]["rest_y"] - B, front_z - travel, 0.0)),
            ],
            "bl": [
                (rig.legs["bl"]["p3"][0], rig.legs["bl"]["rest_y"], hind_z, 0.0),
                (0.22, 0.54, (rig.legs["bl"]["p3"][0], rig.legs["bl"]["rest_y"], bl_mid_z, 0.0)),
                (0.72, 1.12, (rig.legs["bl"]["p3"][0], rig.legs["bl"]["rest_y"] - B, hind_z - travel, 0.0)),
            ],
            "br": [
                (rig.legs["br"]["p3"][0], rig.legs["br"]["rest_y"], hind_z, 0.0),
                (0.38, 0.70, (rig.legs["br"]["p3"][0], rig.legs["br"]["rest_y"], br_mid_z, 0.0)),
                (0.88, 1.32, (rig.legs["br"]["p3"][0], rig.legs["br"]["rest_y"] - B, hind_z - travel, 0.0)),
            ],
        }
        path_rows = []

        def frame(i, t):
            u = t / L
            dz = -travel * ease(u)
            u_front = ease(_clamp((t - 0.16) / 0.54, 0.0, 1.0))
            u_back  = ease(_clamp((t - 0.88) / 0.54, 0.0, 1.0))
            dy_f = -B * u_front
            dy_b = -B * u_back
            dy = 0.5 * (dy_f + dy_b)
            lip_lift = 3.2 * math.sin(math.pi * _clamp((t - 0.30) / 0.88, 0.0, 1.0))
            pitch_deg = _clamp(-math.degrees(math.atan2(dy_f - dy_b, max(12.0, rig.trunk_len))), -24.0, 24.0)

            p = {
                "root":  {"position": [0.0, dy + lip_lift, dz], "rotation": [0.0, 0.0, 0.0]},
                "hips":  {"position": [0.0, -0.35, 0.0], "rotation": [pitch_deg, 0.0, 0.0]},
                "chest": {"rotation": [-pitch_deg * 0.14, 0.0, 0.0]},
                "neck":  {"rotation": [-pitch_deg * 0.40 + 6.0 * math.sin(math.pi * u), 0.0, 0.0]},
                "head":  {"rotation": [-pitch_deg * 0.45 + 4.0 * math.sin(math.pi * u), 0.0, 0.0]},
            }
            rig.set_face(p, ear_l=(-5.0 * math.sin(math.pi * u), 0.0, 0.0))
            targs = {}
            for tag in ("fl", "fr", "bl", "br"):
                pos_w, arc, toe = foot_at(t, plans[tag], lift=5.0 if tag[0] == "b" else 4.0, reach=(tag[0] == "f"))
                targs[tag] = {"pos": pos_w, "toe": toe}
            rig.solve_legs(p, targs)
            p["root"] = {"position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0]}
            path_rows.append((t, dy + lip_lift, dz))
            tail_up = 16.0 + 72.0 * ease(_clamp((t - 0.52) / 0.55, 0.0, 1.0)) * (1.0 - 0.55 * ease(_clamp((t - 1.28) / 0.32, 0.0, 1.0)))
            return p, tail_up, 6.0 * math.sin(2.0 * math.pi * u)

        made = _bake_clip(rig, L, steps, frame, loop="hold", tail_lag=0.07)
        rows = path_rows[: steps + 1]
        made["timeline"] = {
            "0.0": f"step {face_z:.2f} {-B:.1f}",
            "0.05": "path root " + " ".join(f"{t:.4f} {dy:.3f} {dz:.3f}" for t, dy, dz in rows),
        }
        return made, ahead_px

    clips["step_up_1000"], up_ahead   = make_step_up()
    clips["step_down_1000"], dn_ahead = make_step_down()

    meta = {
        "walks": walk_speeds,
        "step_up_ahead": up_ahead,
        "step_down_ahead": dn_ahead,
    }
    return clips, meta
