# Handoff Card: <Animal Name>

- **Status:** Ready for Java | Approved workshop version: `v<N>` | Branch: `<branch>`
- **Mod & ID:** `savanna` (`antcolonies:<animal>`) | **Family:** `<family>`

## Asset Paths
- **Geo:** `assets/antcolonies/geo/entity/<animal>.geo.json`
- **Textures:** `assets/antcolonies/textures/entity/<animal>.png` (variants: `<female>`, `<young>`, `<teen>`, ...)
- **Animations:** `assets/antcolonies/animations/entity/<animal>.animation.json`
- **Body plan:** `assets/antcolonies/body_plans/<animal>.json`
- **Block moves (if any):** `assets/antcolonies/block_moves/<animal>.json`

## Scale & Movement Numbers
- **Model length:** `<N>` model px (`16 px = 1 block` before scale)
- **Drawn scale:** male `<x>`, female `<y>`, young `<z>` (1 model px = `<p>` blocks at size 1.0)
- **Hitbox (width x height):** `<w> x <h>` blocks
- **Clip-matched speeds:** walk `<v>` b/s, run/dash `<v>` b/s, swim `<v>` b/s (waterline at `<y>` model px)

## Clips
| Clip | Length (s) | Loop | Notes / Exact Event Ticks (20 tps) |
|---|---|---|---|
| `idle` | | `true` | |
| `walk` | | `true` | Scale speed with `SteadyController` |
| `attack` | | `false` | Hit/bite on tick `<N>` |
| `death` | | `hold` | |

## Special Bones (for Java / Renderer)
- **Head look:** `<bone>` | **Carry / mouth socket:** `<bone>` | **Extra layers (eyelids, throat, tail drag):** `<bones>`

## Verification
- [ ] `body_plan.py check` & box budget checked (`<N>` boxes)
- [ ] `model_parts.game_faces()` applied on export
- [ ] Offline `clip_check.py` (including clip transitions) passed
- [ ] In-game `game_compare.py` & 3-angle frame check (once entity exists)

## Notes for Java
- *(Any quirks, e.g., when to trigger `BlockMoves`, whether young skip climbs, or sound hooks.)*
