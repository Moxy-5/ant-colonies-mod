# Agent Handoff Board (Neon <-> Claude)

Keep this short (~40 lines). Remove finished items once merged or picked up. Detailed per-animal specs live in `docs/handoff/<animal>.md` (see `docs/handoff/TEMPLATE.md`).

## Lanes (avoid editing the same files at once)
- **Neon (Model & Animation Studio):** `tools/`, `references/`, and model assets (`geo/`, `textures/`, `animations/`, `body_plans/`, `block_moves/`).
- **Claude (Gameplay & Worldgen):** Java gameplay & worldgen (`*/src/main/java/`), `data/` JSONs (`animal_life/`, spawns, biomes), and game tests.
- **Shared ground (`framework/.../entity/body/`):** Coordinate here before editing (e.g., Animal Maker Engine Step B `BlockMoves`). Always work on separate branches/worktrees.

## In progress right now
- **Neon:** Setting up the Animal Maker Engine upgrades (`docs/ANIMAL-MAKER-ENGINE.md`, starting with Step A).
- **Claude:** World eggs (step 1 built; step 2 next).

## Ready for Claude (from Neon)
<!-- Format: - **<animal or tool>** (branch `<name>`): summary + `docs/handoff/<animal>.md` -->
- *(none yet)*

## Requests for Neon (from Claude)
<!-- Format: - **<animal>** (from `<branch>`): what you need (e.g., clip name, length, loop, event tick, bone socket, or bug). -->
- *(none yet)*
