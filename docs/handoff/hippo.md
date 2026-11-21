# Handoff Card: Hippopotamus

- **Status:** v8 approved by the user (2026-11-21, "V8 looks good"); block moves made 2026-11-21; **in the game** (`HippoEntity`, `HippoModel`, `HippoRenderer`, 2026-11-21, picked up by Claude) | Workshop version: `v8`
- **Mod & ID:** `savanna` (`antcolonies:hippo`) | **Family:** `upright_four_legs`

## Asset Paths
- **Geo:** `savanna/src/main/resources/assets/antcolonies/geo/entity/hippo.geo.json`
- **Textures:** `savanna/src/main/resources/assets/antcolonies/textures/entity/hippo.png` (256x256 nearest-neighbor; approved Eye Variant B "River-Boss Amber")
- **Animations:** `savanna/src/main/resources/assets/antcolonies/animations/entity/hippo.animation.json`
- **Body plan:** `savanna/src/main/resources/assets/antcolonies/body_plans/hippo.json` (registered in `body_plans/index.json`)
- **Model maker:** `tools/hippo_models.py`

## Scale & Movement Numbers
- **Model size:** `80 x 32 x 31` model px (`5.0` blocks long x `2.0` blocks tall at back dome x `1.94` blocks wide; `16 px = 1 block`)
- **Drawn scale:** adult bull `1.0` (`1 model px = 0.0625 blocks`), suggested cow `0.92`, calf `0.45` (growing smoothly via `AnimalLife`)
- **Suggested hitbox (width x height):** `2.1 x 1.9` blocks (adult)
- **Clip-matched base speeds:**
  - `walk`: `6.944 px/s` (`0.434 blocks/s` — scale animation speed with `SteadyController` to match actual entity velocity)
  - `charge`: `22.321 px/s` (`1.395 blocks/s` — fast diagonal trot; scale smoothly with `SteadyController`)
  - Waterline when floating: `y = 21.5` model px (`1.34` blocks above feet; keeps nostrils, River-Boss Amber eyes, ears, and back ridge cleanly above the surface)
- **Recommended `SteadyController` blend:** `5` ticks (`0.25 s`) — all 24 transitions checked clean at `blend = 5`

## Clips (20 total: the 14 approved ones and 6 block moves)
| Clip | Length (s) | Loop | Notes / Exact Event Ticks (20 tps) |
|---|---|---|---|
| `idle` | `4.00` | `true` | Heavy breathing, subtle head look, double blinks, occasional ear twitch |
| `walk` | `1.20` | `true` | Lateral 4-beat stroll (`BL->FL->BR->FR`), `0.434 b/s` base; scale speed with `SteadyController` |
| `charge` | `0.56` | `true` | Fast diagonal trot, ears pinned, jaw cracked open, `1.395 b/s` base |
| `float` | `3.60` | `true` | Submerged river rest at waterline `21.5 px`; eyes/nostrils/ears above surface, legs drifting below |
| `underwater_walk` | `1.80` | `true` | Buoyant slow-motion riverbed bounding/gliding (real hippos bound off the riverbed rather than dog-paddle) |
| `bite` | `0.75` | `false` | Coil (`0..0.18s`), lunge + wide gape (`0.18..0.32s`), **crushing hit on tick 8 (`0.40s`)** |
| `death` | `1.40` | `hold` | Heavy side-roll collapse onto left flank with tucked underside legs, zero floor clipping |
| `yawn` | `2.60` | `false` | Signature 118-deg territorial threat display: peak open `0.70..1.80s` (ticks `14..36`), bellow shake, **jaw slam on tick 40 (`2.00s`)** |
| `ear_flick` | `1.10` | `false` | Surfacing ear-propeller spin + head shake (`0.15..0.85s`, great trigger right when emerging or idling in water) |
| `graze` | `2.40` | `true` | Nighttime riverbank grazing: sweeping snout right over grass blades with rhythmic jaw munch |
| `rest_down` | `0.90` | `false` | Smooth transition `idle -> rest_down -> rest` (lowers belly flush onto sand) |
| `rest` | `3.60` | `true` | Belly-down riverbank sunbathing loaf, legs folded neatly forward, slow drowsy blinks |
| `rest_up` | `0.90` | `false` | Smooth transition `rest -> rest_up -> idle` |
| `dung_flick` | `1.60` | `false` | Territorial rear squat + high-speed paddle-tail propeller (`0.22..1.20s`, ticks `4..24` for splash/particle hook!) |
| `step_up_1000` / `step_up_920` | `6.90` | `false` | Block move: up one block. Body holds back while the forefeet go up, then the hind legs push up (hind feet land about 5.2 s) |
| `step_down_1000` / `step_down_920` | `7.00` | `false` | Block move: down one block. Forefeet reach down first, hind feet hold the edge, tail kept up until it clears |
| `out_1000` / `out_920` | `8.28` | `false` | Block move: out of the water onto a bank one block up (the step-up stretched 1.2 times, ears flick and blink at the end) |

**Block moves** (`block_moves/hippo.json`, played by the framework's `BlockMoves` like the monitor's): sizes `0.92` (cow) and `1.0` (bull); the clip name ends in `round(size*1000)`. Each clip's first timeline note gives the step (`step <face z> <height>` or `water out ...`), the `path root` notes give the root's height and forward travel per tick (the game moves the root; the clip keeps it at 0). Up and out need the step face `42` px ahead (`2.6` blocks); down starts with the edge `16` px ahead. **The calf (`0.45`) gets no moves:** play moves only at size of about `0.85` and up; smaller hippos use the plain step-up.

## Special Bones (for Java / Renderer)
- **Head look:** `head` (optional slight share on `neck`)
- **Mouth / jaw:** `jaw` (plus `tongue` child of `jaw`)
- **Eyelids:** `lid_left`, `lid_right` (animated via position `y: 0 -> -2.65` in clips; hidden inside brow at rest)
- **Ears & Tail:** `ear_left`, `ear_right`, `tail_1`, `tail_2`, `tail_3` (3-bone muscular dock -> flat paddle -> coarse bristled fan tip; Java can also layer live turn-inertia swing on `tail_1/2/3`!)
- **Legs:** `leg_fl_1/2/foot`, `leg_fr_1/2/foot`, `leg_bl_1/2/foot`, `leg_br_1/2/foot`

## Verification
- [x] `body_plan.py check` OK & box budget checked (`131` boxes vs `400` large-animal budget, `0` slanted, `28` bones)
- [x] `model_parts.game_faces()` applied automatically on export (`hippo_models.py game`)
- [x] Offline `clip_check.py hippo --switches` checked: `0/24` clip transitions flagged, zero floor clipping and zero leg-into-pink-belly clipping across all 14 clips, all film strips visually inspected
- [x] User approved `v8` (2026-11-21: "V8 looks good", seen in the versions viewer)
- [x] Block moves checked offline (2026-11-21): `clip_check.py hippo 'step_*' 'out_*' --switches` (40 switches, 0 flagged; no joint turns over 90 deg, biggest 79), film strips `references/anim_lab/hippo/cc_hippo_<move>.png` judged; `verify_animal.py hippo --offline` passed. Small leftovers kept: foot and shin grazes of about 2 to 2.7 px at the lip at takeoff and landing, hind knees 0.6 to 1.3 px deeper than at rest at the hind landings, the cow's 2.4 px hover at 4.26 s on the way down
- [x] `HippoEntity` exists (2026-11-21): body plan has `controller`, `course` and `verify.films` (`tools/film/hippo_moves_frames.film`, the three moves from three sides); clip blend 5 ticks (`lead_ticks` 5 in the move file, all 40 clip changes clean at 5, one flagged at 4)
- [ ] `game_compare.py` (needs a `verify.compare` entry: a game_faces-style film of the hippo)

## Notes for Java
- Once `HippoEntity.java` exists, add `"controller": "savanna/src/main/java/..."` and `"course"` to `body_plans/hippo.json` so `clip_check.py` reads the exact Java blend constant and `body_course.py` can film it.
- Use `rest_down -> rest -> rest_up` rather than switching directly `idle <-> rest` so the 2-ton hippo lowers and rises smoothly.
- Great flavor hooks: trigger `ear_flick` when surfacing from `underwater_walk` into `float`, `yawn` as territorial warning before `charge`, and `dung_flick` when marking riverbanks!
