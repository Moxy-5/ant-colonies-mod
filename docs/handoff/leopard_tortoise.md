# Handoff Card: African Leopard Tortoise (*Stigmochelys pardalis*)

- **Status:** **`v3` approved by the user** (`2026-11-22`, "alright that is perfect"; tailored snug high-domed hollow-arch carapace wrapping flanks & forearm shield with zero head/knee clipping, 12 verified clips, workshop snapshot `leopard_tortoise v3`) | **Ready for Claude**
- **Mod & ID:** `savanna` (`antcolonies:leopard_tortoise`) | **Family:** `four_legged_long_body` (sprawling/columnar savanna land tortoise)

## Asset Paths
- **Geo:** `savanna/src/main/resources/assets/antcolonies/geo/entity/leopard_tortoise.geo.json` (`131 / 150` boxes, `23` bones, zero `"parent": null`, `game_faces()` applied automatically)
- **Texture:** `savanna/src/main/resources/assets/antcolonies/textures/entity/leopard_tortoise.png` (`256×256` crisp pixel art: seamless Voronoi chocolate-and-gold mosaic carapace, golden marginal rim, cream-ochre plastron, unified warm golden-sandy-tan African tortoise head/neck/tail & armored osteoderm-plated legs)
- **Animations:** `savanna/src/main/resources/assets/antcolonies/animations/entity/leopard_tortoise.animation.json` (`12` clips, all `0` floor clipping & `0/24` transitions flagged)
- **Body plan:** `savanna/src/main/resources/assets/antcolonies/body_plans/leopard_tortoise.json` (registered in `body_plans/index.json`)
- **Model & clip maker:** `tools/leopard_tortoise_models.py`

## Scale & Movement Numbers
- **Working model size:** `38.0 × 20.6 × 19.8` model px (`16 px = 1 model block`)
- **Drawn scale:** adult **`0.62`** (`1 model px = 0.03875 blocks`) → **`~1.48` blocks total outstretched** (`snug domed carapace ~1.00L × 0.80W × 0.77H blocks`); giant old bulls up to **`0.72`** (`~1.72` blocks), medium young adults **`0.52`** (`~1.24` blocks), hatchlings/juveniles scaling via `AnimalLife`.
- **Suggested hitbox (width × height):** `1.00 × 0.84` blocks (adult at `0.62×`; tighten/widen slightly if scaling up to `0.72×`)
- **Clip-matched base walking speed:**
  - `walk`: `4.167 px/s` (`0.161 blocks/s` at `0.62×` — slow, heavy tortoise plod; scale smoothly with `SteadyController` to match actual entity velocity)
- **Recommended `SteadyController` blend:** `5` ticks (`0.25 s`) — all 24 clip switches verified clean at `blend = 5`

## Clips (12 total)
| Clip | Length (s) | Loop | Notes / Exact Event Ticks (20 tps) |
|---|---|---|---|
| `idle` | `4.40` | `true` | Rhythmic tortoise breath pulse, curious head scan, gentle double blinks (`t=1.25s, 3.45s`), solid planted feet |
| `walk` | `1.60` | `true` | Heavy 4-beat lateral-sequence plod (`BL→FL→BR→FR`), `0.78` duty factor, weighted shell roll & exact ground-locked feet |
| `graze` | `2.60` | `true` | Low savanna grass & succulent grazing; curved 3D beak chomps twice per cycle (`t=0.35s, 1.55s`) |
| `bite` | `0.95` | `false` | Defensive hiss & curved-beak snap: coil (`0..0.20s`), wide `+40°` lunge revealing grooved pink tongue, **beak snap on tick 10 (`0.50s`)** |
| `drink` | `2.80` | `true` | Pan / waterhole drinking: neck dips low to waterline with slow rhythmic throat-gulp sips |
| `retract_in` | `0.65` | `false` | Smooth `idle → retract_hold`: plastron settles onto ground, straight-back Cryptodira head tuck under golden visor, armored forearms fold across front opening |
| `retract_hold` | `3.20` | `true` | Armored fortress hold flush on ground with subtle breath pulse & eyes shut |
| `retract_out` | `0.85` | `false` | Cautious peek-out (`0..0.38s` eyes open + careful left-right head peek behind forearms) then smooth stand-up (`0.38..0.85s`) |
| `bask_down` | `0.80` | `false` | Smooth `idle → bask` settling plastron down onto warm savanna sand |
| `bask` | `4.20` | `true` | Sunny savanna tortoise basking sploot: neck stretched low, hind legs splooted back-outward, slow drowsy blinks |
| `bask_up` | `0.80` | `false` | Smooth push-up `bask → idle` |
| `death` | `1.20` | `hold` | Heavy collapse onto ground with slight tilt, drooped limp head & eyes shut, limbs relaxed outward |

## Special Bones (for Java / Renderer)
- **Head look:** `head` (optional slight share on `neck_2` when not retracted)
- **Mouth / jaw:** `jaw` (pivots cleanly at jaw hinge; opening `+36°..+40°` reveals smooth horseshoe curved 3D beak & seamless grooved pink tongue)
- **Carry socket:** `carry` (empty child bone of `head` right inside the curved beak — great for carrying grass/succulents!)
- **Eyelids:** `lid_left`, `lid_right` (animated via `position.y: 0 -> -1.62` in clips; tucked inside scaly brow at rest)
- **Legs:** `leg_fl_1/2/3`, `leg_fr_1/2/3`, `leg_bl_1/2/3`, `leg_br_1/2/3` (tear-proof deep sockets + overlapping rounded knee/ankle cuffs)
- **Tail:** `tail_1`, `tail_2`

## Verification
- [x] `body_plan.py check` OK (`leopard_tortoise: OK`, `131 / 150` small-animal box budget, `23` bones)
- [x] `model_parts.game_faces()` applied automatically on export (`leopard_tortoise_models.py game`)
- [x] Offline `clip_check.py leopard_tortoise` passed: **12/12 clips `inside the ground: nothing`**, `0.00 px` standing foot hover, `0.0 px` knee-into-shell penetration, max joint turn `32 deg / 0.05s`
- [x] Offline `clip_check.py leopard_tortoise --switches` passed: **`0/24` clip transitions flagged** at `blend = 5`
- [x] Key animation poses visually inspected (`walk`, `graze`, `bite`, `drink`, `retract_hold`, `retract_out`, `bask`, `death`)
- [x] Workshop snapshot `leopard_tortoise v1` saved (`references/workshop/leopard_tortoise/versions.html`)

## Notes for Java
- **Land savanna grazer (NOT aquatic):** Real *Stigmochelys pardalis* is a dry savanna tortoise! Spawn on open savanna grass & near dry edges/pans (drinking at pans with `drink`, grazing dry savanna grass with `graze`, sunbathing on warm open ground with `bask_down → bask → bask_up`).
- **Armored fortress defense:** When threatened by a predator or player hitting it, play `retract_in → retract_hold` (grant high damage resistance / knockback bounce while retracted!), then wait for danger to clear and play `retract_out → idle`. If cornered up close, trigger a quick warning `bite` hiss!
- Once `LeopardTortoiseEntity.java` lands, add `"controller"` and `"course"` to `body_plans/leopard_tortoise.json`.
