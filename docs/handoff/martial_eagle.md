# Handoff Card: Martial Eagle (*Polemaetus bellicosus*)

- **Status:** Ready for Java | Approved workshop version: `v9` | Branch: `neon/studio`
- **Mod & ID:** `savanna` (`antcolonies:martial_eagle`) | **Family:** `flier` (first true savanna Flier!)

## Asset Paths
- **Geo:** `savanna/src/main/resources/assets/antcolonies/geo/entity/martial_eagle.geo.json` (`349/400` boxes, `62` bones, `game_faces=True`)
- **Texture:** `savanna/src/main/resources/assets/antcolonies/textures/entity/martial_eagle.png` (`512x512`, `TEXELS = 2.0`)
- **Animations:** `savanna/src/main/resources/assets/antcolonies/animations/entity/martial_eagle.animation.json` (`18` clips)
- **Body plan:** `savanna/src/main/resources/assets/antcolonies/body_plans/martial_eagle.json` (registered in `body_plans/index.json`; includes Motion Studio Phase 2 `gaits` table + `change` moments)
- **Makers:** `tools/martial_eagle_models.py`, `tools/martial_eagle_anims.py` (plus reusable bird systems `bird_beak`, `bird_eye`, `bird_talon_foot`, `feather_fan`, `feather_grain` in `tools/model_parts.py` and `wing_cycle`, `soar_bank`, `head_stabilize`, `bird_look`, `biped_bird_gait` in `tools/motion_kit.py`)

## Scale & Movement Numbers
- **Drawn scale:** `0.50` (`32` model px = `1` real block; `px = 0.03125` blocks/px at size `1.0`). Female Martial Eagles are ~10% larger (`scale ~0.55`, male `0.50`, fledged juvenile `0.42`).
- **Real in-game dimensions (at `scale = 0.50`):**
  - Perched standing height: `~1.25` blocks (`40.0 px`), shoulder width `~0.62` blocks (`19.8 px`).
  - Soaring wingspan: `~3.32` blocks (`106.2 px` wingtip-to-wingtip), body length `~1.55` blocks (`49.6 px`).
- **Suggested Hitbox (width x height):** perched `0.70 x 1.20` blocks; soaring `1.10 x 0.70` blocks (wings overhang visually like a real raptor).
- **Clip-matched ground speeds (in `body_plans/martial_eagle.json` `gaits` + `checks.walks`):**
  - `walk`: `17.188 px/s` (`0.537` blocks/s at size `1.0`), `from: 0.0`, `min: 0.5`, `max: 1.6`, `change.hop: 0.37 s`.
  - `hop`: `25.385 px/s` (`0.793` blocks/s at size `1.0`), `from: 21.0 px/s`, `min: 0.6`, `max: 1.8`, `change.walk: 0.06 s`.
- **Body controller blend:** **`8` ticks (`0.40 s`)** via `new SteadyController<>(this, "body", 8, ...)` — verified `0/34` switches flagged at 8 ticks!

## Clips (18 Total)
| Clip | Length (s) | Loop | Category | Notes / Exact Event Ticks (20 tps) |
|---|---|---|---|---|
| `perch_idle` | `4.8` | `true` | `perch` | Saccadic avian scanning, curious head-tilts, natural blinks, gripping branch (`y=0, r=1.8, z=-1.6`) |
| `perch_alert` | `2.0` | `true` | `perch` | Prey spotted below: athletic crouch lean, raised occipital crest, fierce predator brow glare |
| `perch_call` | `1.8` | `false` | `perch` | Territorial whistle: head throws skyward, beak opens wide showing tongue; sound hook at tick `7` (`0.35 s`) |
| `perch_preen` | `3.2` | `false` | `perch` | Curls neck to left wing coverts, nibbles plumage 3x (`0.8–2.4 s`), settles back to rest |
| `perch_rouse` | `2.2` | `false` | `perch` | Classic eagle feather-rouse: crest puffs, brisk wing/tail shake (`0.5–1.6 s`), sleek settle |
| `idle` | `3.6` | `true` | `land` | Ground standing sentinel (`0.00 px` flat contact), saccadic scanning & blinks |
| `walk` | `1.0` | `true` | `land` | Proud bipedal IK ground strut (`17.19 px/s`, `0.00 px` clip, hitched folded wingtips, head-bob); `Gaits` #1 |
| `hop` | `0.65` | `true` | `land` | Bounding two-footed ground hop-run (`25.39 px/s`); takeoff tick `6`, landing tick `11`; `Gaits` #2 |
| `eat_tear` | `2.2` | `false` | `land` | Braces feet over carcass, plunges beak down, clamps (tick `9`), rips sharply upward (tick `13`), swallows |
| `takeoff` | `1.2` | `false` | `air` | Crouch coil (`0–5` ticks) -> upward leap & dual-wing pop (`5–7` ticks) -> launch beat ending flush at `fly_flap(0)` |
| `fly_flap` | `1.0` | `true` | `air` | Deep majestic wingbeats with outer-primary wrist whip lag (`wing_wave` verified) & gimbal-leveled head |
| `soar_glide` | `3.2` | `true` | `air` | Effortless 3.32b thermal soaring with raised dihedral, micro tip gusts, tail rudder trim & ground scan |
| `bank_left` | `1.6` | `true` | `air` | Graceful `-18°` left thermal bank with counter-tilted tail rudder & horizon-locked gimbal head |
| `bank_right` | `1.6` | `true` | `air` | Graceful `+18°` right thermal bank with counter-tilted tail rudder & horizon-locked gimbal head |
| `stoop_dive` | `1.4` | `true` | `air` | High-speed `+20°` nose-down hunting stoop with M-delta swept wings (`0` midline crossings) |
| `stoop_strike` | `1.1` | `false` | `air` | Starts flush from `stoop_dive`; flares wings & shoots talons wide forward, **strike/grab at tick `10` (`0.50 s`)** |
| `land_flare` | `1.2` | `false` | `air` | Stall-braking flare (alula up, wide tail fan, forward talons) -> touchdown & wing-fold (`10–12` ticks) -> perch settle |
| `death` | `1.4` | `hold` | `land` | Natural ground collapse (`0.00 px` ground clip), eyelids shut, relaxed wings |

## Special Bones (for Java / Renderer)
- **Dual-Wing Architecture (ZERO Java work needed!):** Both folded capes (`wing_fold_left/right` + `wing_fold_tip_*`) and spread soaring wings (`wing_upper_* -> wing_fore_* -> wing_hand_* -> wing_tips_* + alula_*`) live in the single `.geo.json` (`349/400` boxes). **Every clip sets their `scale` channels directly (`[1,1,1]` vs `[0,0,0]`)**, and `takeoff` / `land_flare` cross-scale them mid-beat!
- **Head look / `Attention`:** `head` (plus `neck_1`, `neck_2` for soft distributed tracking; see `head_player` list in `body_plans/martial_eagle.json`).
- **Expressive face bones:** `brow_left/right`, `eye_left/right`, `pupil_left/right`, `ulid_left/right`, `lid_left/right`, `crest_c/left/right`, `jaw`, `tongue`.
- **Talon grip / carried prey socket:** `leg_foot_left` & `leg_foot_right` (when flying with small savanna prey like monitor/hare/cub, hang prey cleanly beneath the clenched ankles!).

## Verification
- [x] `body_plan.py check` OK & box budget checked (`349/400` large animal, `62` bones)
- [x] `model_parts.game_faces()` applied automatically on export (`faces=True`)
- [x] `clip_check.py --gait-changes --write` populated `walk <-> hop` matching-footfall switch moments (`0.37 s`, `0.06 s`)
- [x] Offline `clip_check.py` (all 18 clips + `wing_wave` + `perch` + `air` + `hidden_at_rest: tongue -0.1`) & `--switches` (`0/34` flagged!) passed
- [ ] In-game `game_compare.py` & 3-angle frame check (once `MartialEagleEntity` exists)

## Notes for Java
- Use `SteadyController<>(this, "body", 8, ...)` so blends match the verified 8-tick offline check (and `Gaits` from `BodyPlans.gaitsOf("martial_eagle")` for `walk`/`hop` ground locomotion once `claude/gait-rule` merges).
- Visible bounds in `martial_eagle.geo.json` are set generously (`4.0 x 2.4` blocks) so soaring wingtips never frustum-cull near screen edges.
- `perch_*` clips align the eagle's gripping talons onto a horizontal branch top at `y = 0.00` (centered around `z = -1.6 px` = `-0.05` blocks) — snapping perched entities directly onto acacia branch tops will look dead-on!
- Note on `tools/body_plan.py` & `tools/clip_check.py`: `neon/studio` already includes our Bird/Flier upgrades AND clean ports of Motion Studio Phase 2 (`keep_gaits`, `rewrite_plan`, `plan_text`, `--gait-changes --write`) so merging `neon/studio` with `claude/motion-phase2` / `claude/gait-rule` will be super clean!
