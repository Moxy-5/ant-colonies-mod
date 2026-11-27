# Motion Studio: Neon's Plan Additions (2026-11-27)

Written by Neon (`neon/studio`) after reviewing Phases 0–3 (`docs/plans/motion-studio.md`, `motion-studio-phase2.md`, `motion-studio-phase3.md`) as Neon takes over animal animations going forward.

> **Merge note for Claude:** `docs/plans/motion-studio.md` currently lives on `claude/motion-phase1` (not yet merged into `neon/studio`). Per `GEMINI.md`, Neon did not edit `C:\Ant Mod` directly — fold sections **Phase 3b additions (57–59)** and **Phase 3c (60–64)** into `docs/plans/motion-studio.md` on your branch, then remove this addendum file once folded.

---

## Small additions to fold into Phase 3b ("G. The glue")

These are small, touch the exact same tool/glue layer as items 51–56, and remove daily friction before redoing or scaling up animations:

57. **One shared feet-sliding rule in `tools/recording.py` (fixes BUGS 456, 457).** Move the replay page's proven foot-contact and sliding rule (height in model px, touching threshold, slide threshold, post-gap grace ticks) into `tools/recording.py` so `replay.py`, `replay_strips.py`, `pair_report.py`, and `body_course.py` all report the exact same planted/sliding truth. **S, Claude.**
58. **Universal body-plan preservation on export (extends 56).** Generalize `body_plan.keep_gaits` into a single `body_plan.preserve_sections(old_plan, new_plan)` helper called automatically by every maker (`lion_models.py`, `buffalo_models.py`, and shared `model_parts` writers) so re-exporting a model or clips always keeps `gaits`, `spots`, `holds`, `strikes`, `checks.controller`, `checks.drawn_ahead`, `course`, `verify`, and extra `switches` — eliminating the risk of wiping Claude's tables when exporting. **S, Claude.**
59. **Multi-scale / youth gait checks and slots (`clip_check.py`, `pace_report.py`, `Gaits`).** Today calves and cubs play adult clips shrunk down (`0.36×`–`0.55×`), pinning play speed at the cap and skating (BUGS 378). Add `--scale` support to `clip_check.py` and clean youth-gait slot support in `Gaits` / `pace_report.py` so Neon can author and verify stride-matched calf/cub gaits against real in-game young speeds. **S, Claude.**

---

## New Phase: Phase 3c — Video Review Studio (for Neon) & Living Motion

Why a separate phase: these are bigger than Phase 3b's glue fixes, don't block Phase 3b, and unlock two high-impact upgrades — **native video review** (Neon can watch `.mp4` / `.webm` directly off disk via `read_file` to judge timing, weight, rhythm, and subtle motion bugs that slip between contact-sheet frames) and **living quadruped/multi-bone motion** in-game.

### H. Video review pipeline (tailored for Neon's video vision)

60. **Replay-to-video exporter (`tools/replay_video.py`, or `--video` on `replay_strips.py`).** Render any `replay.py` recording window directly into a clean **multi-angle `.mp4`** using the existing headless Edge + `ffmpeg` pipeline:
    - Synced multi-view layout in one frame (side + 3/4 + front or top), optional `--fixed` world camera, and the Phase 1 see-through non-picked animal rendering.
    - **Built-in speed passes:** export **1× real-time** and **0.5× (or 0.25×) slow-motion** (side-by-side or back-to-back in one short `.mp4`), with a compact HUD bar showing tick, clip name, play speed, and red foot-slide indicator. Slow-mo multiplies effective temporal detail per gait/wingbeat cycle so Neon catches single-tick pops, blend hitches, and weight/timing feel immediately.
    - **M, Claude.**
61. **Fast offline clip-to-video preview (`tools/clip_video.py` / `clip_check.py --video`).** Same multi-angle 1× + 0.5× `.mp4` export directly from offline `.geo.json` + `.animation.json` — covering single clips (on a moving treadmill floor matching the stride), `--switches` blends, and `--pair` two-animal scenes (with ground plane and red collision/slide overlays). Runs in seconds without launching Minecraft so Neon can watch every animation in motion before running `runFilm` or building the user's interactive sidebar viewer. **M, Claude/Neon.**
62. **Real-footage side-by-side comparison (`--ref <video.mp4>`, upgrades idea 47).** Optionally composite a trimmed real-animal reference `.mp4` beside the rendered clip (cycle-length / speed-matched) into one `.mp4` so Neon can directly compare real cadence, weight shift, spine flow, and wingbeat timing against the Minecraft clip instead of slicing stills. **S-M, Claude.**

### I. Living motion in the framework (promotes ideas 27 & 28)

63. **Live spine bending into turns (promotes idea 27).** Shared framework rule (`framework/.../entity/body/`, patterned on the ants' `BodyTurn`) driven by any animal's body plan spine/neck/tail chain: turning curves the body into the arc instead of spinning a 4-block quadruped up to 20°/tick like a statue on a turntable. Eased on `DrawClock` game ticks so films and play agree. **M, Claude.**
64. **Live start/stop & banked-turn weight cues (promotes idea 28).** Optional, per-plan body-language layer (`stop_pitch`, `turn_lean`, `accel_stride` caps in the body plan) adding subtle anticipation/settle on rapid starts, sudden stops, and sharp banked turns (for runners and fliers alike), driven by `DrawClock`. Keeps each animal's unique weight (heavy buffalo vs. springy lion vs. banking eagle) without hard-coded species rules. **M, Claude.**

---

## Updated Phase Order (suggested)

- **Branch sync first (prerequisite before Neon starts new clips):** Merge Claude's Motion Studio branches (`phase1`–`phase3`) onto `master` and sync `neon/studio` cleanly, preserving **both** Motion Studio Phases 0–3 and Neon's Flier/Martial Eagle foundation (`motion_kit.py`, `clip_check.py`, `body_plan.py`, `viewer_common.js`).
- **Phase 3b (next, Claude):** the glue — items **51, 52, 53**, plus **54–56** and small additions **57, 58, 59**. (Item **58** first if quick, so exports are immediately safe.)
- **Phase 3c (after 3b, Claude — or items 60–61 in parallel since they are pure `tools/`):**
  - **Part 1 (Video Studio, 60–62):** gives Neon multi-angle 1× + slow-mo `.mp4` review both offline and on real game replays.
  - **Part 2 (Living Motion, 63–64):** spine bending into turns + start/stop/bank weight cues across all land/air animals.
- **Phase 4 (still on hold):** the replay key (**9**) and motion tests (**43–46**), once Phase 3b item **51** settles placement.
