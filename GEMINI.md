# Ant & Termite Colonies Mod — Neon Session Rules

Read `CLAUDE.md`, `docs/HANDOFF.md`, `docs/ANIMAL-MAKER-ENGINE.md`, and `docs/plans/model-workshop.md` before starting any animal model, texture, or animation work.

## Lane
- **Neon (Model & Animation Studio):** owns `tools/`, `references/`, and model assets (`geo/`, `textures/`, `animations/`, `body_plans/`, `block_moves/`).
- **Claude (Gameplay & Worldgen):** owns Java gameplay/worldgen, `data/` JSONs, and game tests. Coordinate via `docs/HANDOFF.md` and `docs/handoff/<animal>.md`.

## Interactive 3D Sidebar Viewer Standard (User Rule, 2026-11-21)
- **Always present models, paint passes, and animations as a live user-facing HTML artifact in the sidebar** (e.g., `<animal>_model_viewer.html` created in the conversation's artifact directory via `write_file` with `ArtifactMetadata: {UserFacing: true, RequestFeedback: false}`, following the `generative_ui` skill).
- **Never give only a `file://` link to a workspace `.html` file** (`references/workshop/<animal>/versions.html`) as the way to view a model — clicking a workspace `.html` link in the IDE opens raw HTML source code instead of rendering the viewer.
- Build the sidebar viewer as a **self-contained HTML artifact** (inline WebGL/Canvas 3D renderer + exact bone/UV/texture data, zero external CDNs other than the allowlisted gstatic Tailwind script) with orbit/zoom/pan, camera presets, version/look toggles, and animation playback once clips exist.
- Keep running the offline workshop pipeline alongside it (`tools/workshop.py snapshot`, `tools/viewer_shot.py --sheet`, `tools/clip_check.py --switches`, and `tools/game_compare.py`) so Compare scores, version history in git, and automated engine checks stay intact.
