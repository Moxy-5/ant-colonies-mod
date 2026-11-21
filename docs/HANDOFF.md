# Agent Handoff Board (Neon <-> Claude)

Keep this short (~40 lines). Remove finished items once merged or picked up. Detailed per-animal specs live in `docs/handoff/<animal>.md` (see `docs/handoff/TEMPLATE.md`).

## Lanes (avoid editing the same files at once)
- **Neon (Model & Animation Studio):** `tools/`, `references/`, and model assets (`geo/`, `textures/`, `animations/`, `body_plans/`, `block_moves/`). Always present models/paint/clips via a live user-facing HTML artifact in the sidebar (`<animal>_model_viewer.html`, see `GEMINI.md`).
- **Claude (Gameplay & Worldgen):** Java gameplay & worldgen (`*/src/main/java/`), `data/` JSONs (`animal_life/`, spawns, biomes), and game tests.
- **Shared ground (`framework/.../entity/body/`):** Coordinate here before editing (e.g., Animal Maker Engine Step B `BlockMoves`). Always work on separate branches/worktrees.

## In progress right now
- **Neon:** *(ready for next animal)*
- **Claude:** Hippo into the game (`HippoEntity`, branch `claude/engine-every-animal`; v8 and its block moves approved/made 2026-11-21, `docs/handoff/hippo.md`). World eggs step 2 after.

## Ready for Claude (from Neon)
<!-- Format: - **<animal or tool>** (branch `<name>`): summary + `docs/handoff/<animal>.md` -->
- **African River Otter (`antcolonies:otter`, `savanna`)** (branch `claude/engine-every-animal`): `v6` approved 2026-11-21 (2.6 blocks long, 101/250 boxes, 38 bones, 21 clips + 5 BlockMoves incl. calm `enter_water` & leaping deep-dive `dive_in`, `0/40` transitions flagged, `verify_animal --offline` passed) + `docs/handoff/otter.md`.

## Requests for Neon (from Claude)
<!-- Format: - **<animal>** (from `<branch>`): what you need (e.g., clip name, length, loop, event tick, bone socket, or bug). -->
- *(none yet)*
