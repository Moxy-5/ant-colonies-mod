# Agent Handoff Board (Neon <-> Claude)

Keep this short (~40 lines). Remove finished items once merged or picked up. Detailed per-animal specs live in `docs/handoff/<animal>.md` (see `docs/handoff/TEMPLATE.md`).

## Lanes (avoid editing the same files at once)
- **Neon (Model & Animation Studio):** `tools/`, `references/`, and model assets (`geo/`, `textures/`, `animations/`, `body_plans/`, `block_moves/`). Always present models/paint/clips via a live user-facing HTML artifact in the sidebar (`<animal>_model_viewer.html`, see `GEMINI.md`).
- **Claude (Gameplay & Worldgen):** Java gameplay & worldgen (`*/src/main/java/`), `data/` JSONs (`animal_life/`, spawns, biomes), and game tests.
- **Shared ground (`framework/.../entity/body/`):** Coordinate here before editing (e.g., Animal Maker Engine Step B `BlockMoves`). Always work on separate branches/worktrees.

## In progress right now
- **Neon:** *(ready for next animal)*
- **Claude:** Hippo into the game (`HippoEntity`, branch `claude/engine-every-animal`; v8 and its block moves approved/made 2026-11-21, `docs/handoff/hippo.md`). Hippo calf and teen looks (2026-11-21, `tools/life_looks.py` `hippo_looks`, life viewer "Hippo" groups), waiting for the user's look before they go into the game. World eggs step 2 after.

## Ready for Claude (from Neon)
<!-- Format: - **<animal or tool>** (branch `<name>`): summary + `docs/handoff/<animal>.md` -->
- **Savanna Mud & Wallowing Asset Pack (`antcolonies:savanna_mud`, `MudCoatLayer` masks)** (branch `claude/engine-every-animal`): 20 auto-generated 3D entity mud masks with clean eye/pupil halos (updated Hippo w/ expressive `pupil_left`/`pupil_right`, Crocodile, Croc Teen, Bullfrog, Monitor, Otter, Crabs), 64×64 Player mud masks, animated 4-frame `savanna_mud_soupy.png` + `.mcmeta`, 6 stage models (`0.25 / 0.50 / 0.75 / 1.00` gradual sink), 5 track tiles, lungfish cocoon, rubbing-post overlay, `savanna_mud_ball`, multipart blockstate, RGB tint table & gradual water wash-off spec + `docs/handoff/savanna-mud.md` (plan `docs/plans/savanna-mud.md`).
- **African River Otter (`antcolonies:otter`, `savanna`)** (branch `claude/engine-every-animal`): `v6` approved 2026-11-21 (2.6 blocks long, 101/250 boxes, 38 bones, 21 clips + 5 BlockMoves incl. calm `enter_water` & leaping deep-dive `dive_in`, `0/40` transitions flagged, `verify_animal --offline` passed) + `docs/handoff/otter.md`.
- **Bull Shark (`antcolonies:bull_shark`, `savanna`, `fish` family)** (branch `claude/engine-every-animal`): `v14` approved 2026-11-22 (Subtle Lean normalized to `5.50` blocks / `88 px` base for `5.0–7.0` block adult size variations, `351/400` boxes, `35` bones, smooth 2-segment pectoral wings, hollow 3D crimson mouth + vaulted palate + tongue + Serrated Apex Fangs, Look A `bull_shark.png` + Look B `bull_shark_scarred.png`, 12 custom clips [8 essential + 4 fun: `threat_display`, `bump_investigate`, `breach`, `spy_hop`], `0/23` transitions flagged, `verify_animal --offline` passed, `"parent": null` fixed in tool & guarded globally in `model_parts.py`) + `docs/handoff/bull_shark.md`.
- **African Leopard Tortoise (`antcolonies:leopard_tortoise`, `savanna`)** (branch `claude/engine-every-animal`): **`v3` approved by the user 2026-11-22** (large snug high-domed savanna tortoise `~1.48` blocks at `0.62×` adult scale / `1.00×0.80×0.77` block tailored hollow-arch carapace hugging flanks & forearm shield with zero head/knee clipping, `131/150` boxes, `23` bones, smooth horseshoe-curved 3D beak + grooved pink tongue, unified warm golden-tan scaly skin & armored legs, `12` zero-clip clips incl. Cryptodira `retract_in/hold/out` forearm shield, `bask_down/bask/bask_up` sploot, `graze`, `bite`, `drink`, `0/24` switches flagged, `body_plan.py check` OK) + `docs/handoff/leopard_tortoise.md`.

## Requests for Neon (from Claude)
<!-- Format: - **<animal>** (from `<branch>`): what you need (e.g., clip name, length, loop, event tick, bone socket, or bug). -->
- *(none right now — Bull Shark `"parent": null` bug fixed in `tools/bull_shark_models.py`, guarded globally in `tools/model_parts.py`, and re-exported cleanly with `0` nulls)*
