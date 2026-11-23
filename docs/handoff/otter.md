# Handoff Card: African River Otter (*Aonyx capensis*)

- **Status:** In the game (step 1, 2026-11-22: `OtterEntity`, moves through the shared `MoveRider`). Was: Ready for Java (`v6` look + sculpted outer haunches & all 21 clips / 5 BlockMoves approved by user 2026-11-21) | Approved workshop version: `v6`
- **Mod & ID:** `savanna` (`antcolonies:otter`) | **Family:** `upright_four_legs` (Upright Four Legs + Aquatic Extras)

## Asset Paths
- **Geo:** `savanna/src/main/resources/assets/antcolonies/geo/entity/otter.geo.json`
- **Textures:** `savanna/src/main/resources/assets/antcolonies/textures/entity/otter.png` (256×128 nearest-neighbor, `texels=2.0`)
- **Animations:** `savanna/src/main/resources/assets/antcolonies/animations/entity/otter.animation.json`
- **Body plan:** `savanna/src/main/resources/assets/antcolonies/body_plans/otter.json` (registered in `body_plans/index.json`)
- **Block moves:** `savanna/src/main/resources/assets/antcolonies/block_moves/otter.json`
- **Model maker:** `tools/otter_models.py`

## Scale & Movement Numbers
- **Model size:** `41.6 × 16.0 × 9.6` model px (**`2.60` blocks long** × `1.00` block tall at head crown / `0.77` at back arch × `0.60` blocks wide; `16 px = 1 block`)
- **Drawn scale:** adult `1.0` (`1 model px = 0.0625 blocks`), suggested pup `0.55` (growing smoothly via `AnimalLife`)
- **Suggested hitbox (width × height):** `0.75 × 0.85` blocks (adult; sleek enough for 1-block bank gaps)
- **Clip-matched base speeds:**
  - `walk`: `17.969 px/s` (**`1.123 blocks/s`** — scale animation speed with `SteadyController` to match actual entity velocity)
  - `bound`: `32.000 px/s` (**`2.000 blocks/s`** — signature vertical-flexing otter bound/lope; scale smoothly with `SteadyController`)
  - Waterline when floating in `swim`: **`y = 11.3` model px** (`0.706` blocks above feet; keeps flat crown, eyes, ears, and dark nose pad cleanly above the surface)
- **Recommended `SteadyController` blend:** **`4` ticks (`0.20 s`)** — all `40/40` clip transitions checked clean at `blend = 4`

## Clips (21 total: 16 behavior clips + 5 BlockMoves)
| Clip | Length (s) | Loop | Notes / Exact Event Ticks (20 tps) |
|---|---|---|---|
| `idle` | `3.60` | `true` | Alert arched stance, subtle breathing, head look, double-blinks, whisker twitch & gentle tail sway |
| `walk` | `0.80` | `true` | Sleek 4-beat stroll (`BL→FL→BR→FR`), `1.123 b/s` base; scale with `SteadyController` |
| `bound` | `0.60` | `true` | Signature vertical-flexing otter lope/bound (`2.000 b/s` base), arching spine & whipping tail |
| `swim` | `1.20` | `true` | Surface swim at waterline `11.3 px`: eyes/ears/crown/nose above surface, forelegs tucked, vertical tail undulation |
| `dive_swim` | `0.90` | `true` | Streamlined underwater dive: neck extended straight, forelegs flat back, dolphin-like vertical spine/tail wave + hind kicks (add bubble particles!) |
| `forage_feel` | `1.80` | `true` | Signature *Aonyx capensis* crab hunting: low crouch sniffing stones while alternating nimble clawless front paws feel underneath (`tick 7` left, `tick 25` right) |
| `bite` | `0.60` | `false` | Crisp coil (`0..0.14 s`), lunge + 42° gape (`0.14..0.28 s`), **snap hit on tick 6 (`0.30 s`)**, recovery |
| `eat` | `1.60` | `true` | Bank/shallow munching on caught crab/fish: 4 rhythmic crunches with happy tail wag |
| `play_up` | `0.60` | `false` | Smooth transition `idle → play_up → play_rear` |
| `play_rear` | `1.80` | `true` | Social play-wrestle in shallows: upright on hind legs & tail tripod, mouth open, front paws batting (add splash particles!) |
| `play_down` | `0.60` | `false` | Smooth transition `play_rear → play_down → idle` |
| `rest_down` | `0.85` | `false` | Smooth transition `idle → rest_down → rest` |
| `rest` | `3.60` | `true` | Belly-flat riverbank rock rest: eyelids fully shut, chin resting near forward-tucked paws, tail curled flat |
| `rest_up` | `0.85` | `false` | Smooth transition `rest → rest_up → idle` |
| `shake_dry` | `1.10` | `false` | Vigorous head-to-tail water shake wave (`0.08..0.90 s`, ticks `2..18` for water-droplet splash hook after exiting water!) |
| `death` | `1.10` | `hold` | Clean collapse onto right flank with tucked underside legs, eyelids shut, zero floor clipping |
| `step_up_1000` | `0.85` | `false` | Block move (`up`): quick cute scramble (remade by Claude 2026-11-22 at the user's word: front paws hook the top, three hind-foot pats up the face, rump wiggle; `ahead_px = 24.0`, `end_level = 1`, `wades = true`) |
| `step_down_1000` | `0.60` | `false` | Block move (`down`): quick front-first hop (remade 2026-11-22; `ahead_px = 12.0`, `end_level = -1`, `wades = true`) |
| `enter_water_1000` | `2.30` | `false` | Block move (`in`): gradual, relaxed riverbank glide into surface swim (`ahead_px = 12.0`, `ends_afloat = true`, `wet_tick = 18`, max turn 11°/tick) |
| `dive_in_1000` | `2.00` | `false` | Block move (`dive`): leaping arc off a bank plunging deep underwater (`-29 px` / `~1.8` blocks below surface) straight into `dive_swim` (`ahead_px = 12.0`, `ends_afloat = true`, `end_level = -1`, `wet_tick = 18` for splash burst!) |
| `exit_water_1000` | `0.80` | `false` | Block move (`out`): quick climb out onto a bank level with the water's top (remade 2026-11-22, starts at the game's float depth -13.3 px) |
| `exit_water_up_1000` | `1.10` | `false` | Block move (`out_up`): quick haul out onto a bank a block up (new 2026-11-22) |

**Block moves** (`block_moves/otter.json`, played by framework `BlockMoves`): size `1.0` (`block_px = 16.0`, `lead_ticks = 4`). Pups (`0.55`) skip block moves and use standard stepping (`size >= 0.80` plays moves).

## Special Bones (for Java / Renderer)
- **Head look:** `head` (optional slight share on `neck`)
- **Mouth / jaw:** `jaw` (plus `tongue` child of `jaw`)
- **Prey carry socket:** `carry` (empty child bone of `head` right at the front of the jaws — attach caught `crab`, `tilapia`, or `barb` crosswise!)
- **Eyelids:** `lid_left`, `lid_right` (animated via position `y: 0 → -1.55` in clips; hidden cleanly inside brow at rest)
- **Whiskers & Ears:** `whisker_left`, `whisker_right`, `ear_left`, `ear_right`
- **Tail:** `tail_1..tail_5` (5-bone tapering rudder tail; hooks cleanly into framework `TailDrag` so resting/walking otters drape their tails over riverbank edges!)
- **Legs:** `leg_fl_1/2/paw`, `leg_fr_1/2/paw`, `leg_bl_1/2/paw`, `leg_br_1/2/paw`

## Verification
- [x] User approved `v6` look (2.6-block sculpted outer haunches & tucked flanks) and all 21 animations / 5 BlockMoves (2026-11-21: "That looks perfect i think we are done")
- [x] `body_plan.py check` OK & box budget clean (`101` boxes vs `250` medium-animal budget, `0` slanted, `38` bones)
- [x] `model_parts.game_faces()` applied automatically on export (`otter_models.py game`)
- [x] Offline `clip_check.py otter --switches`: **`0/40` clip transitions flagged**, **zero ground/bank clipping** across all 16 standard clips + `enter_water_1000` + `dive_in_1000`, **zero joint turns > 90°/tick** across all 21 clips
- [x] `verify_animal.py otter --offline` passed
- [ ] In-game `game_compare.py`, `body_course.py`, & 3-angle frame check (once `OtterEntity` exists)

## Notes for Java (Gameplay & Ecosystem)
- **Spawning checklist (`CLAUDE.md`):** Own dedicated spawning group `antcolonies:river_otter` (suggested cap **6** around a player, kept within 96 / gone past 128), spawning in small family romps of **2–4** (adults + occasional `0.55`-scale pup) along savanna rivers, oxbows, and shallow banks; prove with `worldWouldSpawn` in `RiverLifeTests`.
- **Food web:** Hunts **river crabs** (play `forage_feel` over shallow pebbles/riverbed!), **river barbs**, **tilapia**, and **bullfrogs/tadpoles**; carries caught prey to the bank (`carry` socket) and plays `eat`. Solid hunger cooldown so otters never clear a river.
- **Social & flavor hooks:** Pair two nearby otters in shallow water for `play_up → play_rear → play_down` with water-splash particles; trigger `shake_dry` after `exit_water_1000`; use `rest_down → rest → rest_up` on riverbank rocks/sandbars; flee **Nile crocodiles** and territorial **hippos**; curious toward players, only nips (`bite`) if provoked or a pup is attacked.
- Once `OtterEntity.java` exists, add `"controller"` and `"course"` to `body_plans/otter.json` so `clip_check.py` and `body_course.py` pick it up automatically.
