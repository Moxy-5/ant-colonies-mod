# Handoff Card: Bull Shark (*Carcharhinus leucas*)

- **Status:** Ready for Java | Approved workshop version: `v13` / `v14` (Stage 5 complete) | Branch: `claude/engine-every-animal`
- **Mod & ID:** `savanna` (`antcolonies:bull_shark`) | **Family:** `fish`

## Asset Paths
- **Geo:** `savanna/src/main/resources/assets/antcolonies/geo/entity/bull_shark.geo.json` (`351 / 400` boxes, `35` bones, `0` slanted cubes, `model_parts.game_faces()` applied, `0` nulls)
- **Textures:**
  - Standard (Look A, Clean River Bull Shark): `savanna/src/main/resources/assets/antcolonies/textures/entity/bull_shark.png` (`512×256`, nearest-neighbor voxel pixel art)
  - Veteran Giant (Look B, Scarred Old Bull): `savanna/src/main/resources/assets/antcolonies/textures/entity/bull_shark_scarred.png` (darker slate + battle scars; recommended for large `>= 6.0` block giants!)
- **Animations:** `savanna/src/main/resources/assets/antcolonies/animations/entity/bull_shark.animation.json` (12 bespoke clips: 8 essential + 4 personality clips)
- **Body plan:** `savanna/src/main/resources/assets/antcolonies/body_plans/bull_shark.json` (registered in `body_plans/index.json` and `tools/body_plan.py` `BOX_KIND`)
- **Block moves:** None (pure aquatic `fish` family)
- **Source tool:** `tools/bull_shark_models.py` (`python tools/bull_shark_models.py game` regenerates all 4 game assets cleanly)

## Scale & Movement Numbers
- **Model length:** `87.8` model px (`~5.50` blocks at `1.00×` scale; `1 model px = 0.0625` blocks)
- **Adult size variations (user request):** Adults should vary naturally in size from **`5.0` blocks (`0.91×`) up to `7.0` blocks (`1.27×`)** (e.g., `5.0b / 5.5b / 6.0b / 6.5b / 7.0b`, weighted around `5.5–6.0b` with rare `6.5–7.0b` giants using `bull_shark_scarred.png`). Pups (if bred later): `~0.45–0.55×` (`2.5–3.0` blocks).
- **Hitbox (width × height at `1.00×`):** `1.55 × 1.10` blocks (scales cleanly with entity size; `visible_bounds` `5.5 × 2.2`).
- **Clip-matched speeds (`SteadyController`, 4-tick blend passes `0/23` flagged):**
  - `idle` (ram-ventilation slow patrol): `~1.2` b/s
  - `swim` (cruise): `~3.3` b/s (`bpt ~ 0.165`)
  - `burst` (ambush sprint / chase): `~6.0` b/s
  - `surface_prowl` (Jaws dorsal-fin stalk): `~2.4` b/s — waterline at **`24.5` model px** (`1.53` blocks) so `dorsal_1` (tip at `34.3` px) slices `~0.6` blocks cleanly above the river surface!

## Clips (12 Total: 8 Essential + 4 Personality)
| Clip | Length (s) | Loop | Notes / Exact Event Ticks (20 tps) |
|---|---|---|---|
| `idle` | `2.60` | `true` | Slow ominous ram-ventilation patrol glide (sharks never freeze completely); passes `checks.wave` |
| `swim` | `1.50` | `true` | Steady predatory river swim; scale speed with `SteadyController`; passes `checks.wave` |
| `burst` | `0.85` | `true` | High-speed ambush chase sprint, pectoral wings swept back for low drag; passes `checks.wave` |
| `surface_prowl` | `1.80` | `true` | High-riding surface stalk (`dorsal_1` above waterline at `24.5` px); passes `checks.wave` |
| `bite` | `0.95` | `false` | Signature crushing bite: snout lifts, jaw opens `46°`, pale nictitating eye-shields flick shut right before **hit tick 8 (`0.40s`)** |
| `thrash` | `1.20` | `true` | Violent lateral sawing head-shake tearing prey (`prey` socket), jaws clamped, nictitating lids locked shut |
| `beached_flop` | `0.90` | `true` | Heavy stranded riverbank thrash (on land; `0` ground clipping, folded bracing pectorals, gasping jaw) |
| `death` | `1.60` | `hold` | Slow solemn `180°` belly-up roll with relaxed drooping fins & slack open jaw |
| `threat_display` | `1.80` | `false` | Real bull shark agonistic hunch warning: arched back, steeply dropped pectorals (`+28°` dihedral), double warning jaw snap (ticks `6–7` & `12–13`) before charging |
| `bump_investigate` | `1.35` | `false` | Classic shark "bump & test" nudge on unfamiliar targets/boats/players: curious head tilt, brief eyelid blink on **blunt-snout contact tick 10 (`0.50s`)**, crisp bank turn away |
| `breach` | `1.50` | `false` | Explosive river-surface breach launching skyward into a `155°` mid-air predator twist with roaring jaws (`0.50–0.95s`), plunging cleanly back to `0°` roll (zero GeckoLib unwind pop) |
| `spy_hop` | `2.20` | `false` | Curious vertical spy-hop peek above the surface scanning left (`0.88s`) & right (`1.32s`) before slipping back under |

## Special Bones (for Java / Renderer)
- **Head look:** `head`
- **Mouth / prey carry socket:** `prey` (child of `head`, centered inside the hollow jaws at `[0, 12.15, -33.5]`)
- **Mouth & eye layers:** `jaw`, `tongue`, `lid_left`, `lid_right` (pale nictitating membranes tucked invisibly inside `head` at rest and animated directly by `bite` / `thrash` / `bump_investigate` / `threat_display` / `death` — no extra Java code needed!)
- **Static tooth-rack child bones:** `ut_front`, `ut_fl`, `ut_fr`, `ut_ml`, `ut_mr`, `ut_rl`, `ut_rr` (under `head`) and `lt_front`, `lt_fl`, `lt_fr`, `lt_sl`, `lt_sr` (under `jaw`) — pure geometry child bones that ride `head` and `jaw` automatically.
- **2-segment smooth pectoral wings:** `pectoral_left/right` → `pectoral_tip_left/right` (already animated smoothly in every clip).

## Verification
- [x] `body_plan.py check` (`bull_shark: OK`) & box budget checked (`351 / 400` boxes, `large animal`, `0` slanted cubes)
- [x] `model_parts.game_faces()` applied on export & verified `0` `null` values in `bull_shark.geo.json` (fixed the `"parent": null` root bug both in `tools/bull_shark_models.py` and globally in `tools/model_parts.py`)
- [x] Offline `clip_check.py bull_shark --switches` & `verify_animal.py bull_shark --offline` passed (`12` clips, `0` flags, `0` parts inside a block, `0 / 23` transitions flagged, all 4 swim waves grow cleanly head-to-tail)
- [ ] In-game `game_compare.py` & 3-angle frame check (once `BullSharkEntity` exists)

## Notes for Java
- **River zones & spawning (`docs/LIVING-THINGS.md` spawning checklist):** Coast and Near-Inland deep middle river channels ONLY (`worldgen/RiverZones`), alone and rare. Give the Bull Shark its own small spawning group (e.g., `antcolonies:bull_shark`, cap `1`–`2`, kept within 96 / gone past 128) so common river fish or crocodiles cannot starve its spawn cap; wrap placement in the `BigSavanna` quiet-savanna gate and prove natural spawning with a real sea-level `worldWouldSpawn` game test.
- **Suggested AI flavor using the 4 fun clips:**
  - Play `bump_investigate` on first close curiosity approach toward an unfamiliar player/boat/hippo before escalating!
  - Play `threat_display` (hunch + dropped pectorals + double jaw snap) as a territorial warning when a rival bull shark, crocodile, or player lingers too close in deep water before triggering `burst` + `bite`!
  - Play `surface_prowl` when patrolling deep channels near the surface, and occasionally trigger `spy_hop` (when curious about riverbank mobs/players) or `breach` (rare dramatic ambush strike at surface prey!).
- **Look B Scarred Old Bull (`bull_shark_scarred.png`):** Swap texture in the renderer when entity scale `>= 1.09` (`>= 6.0` blocks) so giant veteran bulls visibly carry pale battle scars and darker slate hides.
