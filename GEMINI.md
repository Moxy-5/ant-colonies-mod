# Ant & Termite Colonies Mod — Neon Session Rules

Read `CLAUDE.md`, `docs/HANDOFF.md`, `docs/ANIMAL-MAKER-ENGINE.md`, and `docs/plans/model-workshop.md` before starting any animal model, texture, or animation work.

## Where you work in git (the user, 2026-11-25: "I don't want commits to keep being pushed back")
- **Your own folder and branch:** work only in `C:\Ant Mod\neon-studio`, on the branch `neon/studio`. Best: open that folder as your workspace. If you were opened on `C:\Ant Mod`, do every read, write and command inside `C:\Ant Mod\neon-studio` (run the tools from there: `cd "C:\Ant Mod\neon-studio"` first). Never edit, commit or switch branches in `C:\Ant Mod` itself: Claude's sessions switch its branch, and work left there lands on the wrong branch or is lost. If the folder is missing: `git -C "C:\Ant Mod" worktree add neon-studio neon/studio`.
- **Commit after every stage the user approves** (brief, outline, plain shape, detail, paint, each set of clips), and push: `git add <your files>`, `git commit -m "<animal>: <stage>"`, `git push origin neon/studio`. Never end a session with work not committed.
- **At the start of each session** pick up the newest tools and game: `git merge master`. If it says there is a conflict, run `git merge --abort` and leave a note for Claude under "Requests for Neon" in `docs/HANDOFF.md` instead.
- **Handoff:** list the animal under "Ready for Claude" in `docs/HANDOFF.md` with branch `neon/studio` (commit that too). Claude merges `neon/studio` from there; never merge into `master` or a `claude/...` branch yourself.

## Lane
- **Neon (Model & Animation Studio):** owns `tools/`, `references/`, and model assets (`geo/`, `textures/`, `animations/`, `body_plans/`, `block_moves/`).
- **Claude (Gameplay & Worldgen):** owns Java gameplay/worldgen, `data/` JSONs, and game tests. Coordinate via `docs/HANDOFF.md` and `docs/handoff/<animal>.md`.

## Interactive 3D Sidebar Viewer Standard (User Rule, 2026-11-21)
- **Always present models, paint passes, and animations as a live user-facing HTML artifact in the sidebar** (e.g., `<animal>_model_viewer.html` created in the conversation's artifact directory via `write_file` with `ArtifactMetadata: {UserFacing: true, RequestFeedback: false}`, following the `generative_ui` skill).
- **Never give only a `file://` link to a workspace `.html` file** (`references/workshop/<animal>/versions.html`) as the way to view a model — clicking a workspace `.html` link in the IDE opens raw HTML source code instead of rendering the viewer.
- Build the sidebar viewer as a **self-contained HTML artifact** (inline WebGL/Canvas 3D renderer + exact bone/UV/texture data, zero external CDNs other than the allowlisted gstatic Tailwind script) with orbit/zoom/pan, camera presets, version/look toggles, and animation playback once clips exist.
- Keep running the offline workshop pipeline alongside it (`tools/workshop.py snapshot`, `tools/viewer_shot.py --sheet`, `tools/clip_check.py --switches`, and `tools/game_compare.py`) so Compare scores, version history in git, and automated engine checks stay intact.
