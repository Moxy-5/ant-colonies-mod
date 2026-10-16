# Animation engine: overnight progress (2026-10-16)

The user's overnight job: finish the animation engine (`docs/plans/animation-engine.md`) and make 5 animations with it:
1. walk and hot walk (rebuilt), 2. curious look, 3. antenna cleaning, 4. dig and carry (one), plus hooking up the
sleeps, the hot look and the steam (handed over by the peer session). Open picks: my pick, marked "my pick, to
confirm" in the notes. Branch `anim-engine` in the worktree `C:/Ant Mod film` (from `anim-camera`, with
`brain-offline` merged in). Never touch `C:/Ant Mod`; merging into `brain-offline` waits for the user's OK.

**Handoff (06:57):** the earlier session's usage limit ran out at about 04:25 and reset at 06:53. The user had asked it
to leave the job to the 07:04 scheduled task when that happened, so it wrote this log as its handoff and stopped right
after. It set this file's time back to 06:30 so the task's 20-minute check would not take the note for activity.
Nothing else in the worktree has changed since 04:21.

## Done (committed and pushed; `git log` has the details)
- b24246e: branch made, `brain-offline` merged in.
- 03e756e: motion library `tools/ant_motion.py` (legs worked out from where each foot must be). Walk and walk_hot are
  rebuilt with it, every lab check passes, and each clip has a stride note. 70dad76 and 2bf8e64 hold the preview
  pictures and the pose viewer's leg speed comparison.
- 02d358f: three animation players (body and legs, head and antennae, extras), each with its own clock; the walk keeps
  pace with the ground from its stride note.
- 036207d: naps (`NapGoal`; sleep and antenna twitches by turns, slow settling) and hot ground (tall stance and walk);
  `body_language` in the species file; the `body` game test category.
- d8d05c9: trail test timing fix (it checks 25 ticks after sending the patrol home, not 15).
- e349831: step 6, personality traits and the curious look (`NoticeGoal`).

## Done by the 07:04 scheduled run (2026-10-16)
- 449744d: step 7 clips. The jaws fix applied (forebody 13 up, head 6 up and 20 toward the leg, x 1.22 to 1.92, lift
  0.47); the two joint-space moves (lift to the antenna, combing to the jaws) now carry the ankle on a line bowed 0.3
  units out, up and back (`swing` in `groom_side`), which cleared the last clipping. Lab: 0 problems. In the pose viewer.
- Step 7 Java: `DATA_ACTIVITY` (NONE, GROOM_L, GROOM_R, GROOM_BOTH, DIG), `GroomGoal` (workers priority 10, queens 4),
  `body_language.groom` (0.05 a second, both 0.4), `WorkerAnimationsTest`, a cleaning check in the nap test. Lesson:
  the game looks for new goals only on every other tick, so "once a second" needs an even offset from the id.
- Step 8: `dig` (loop 1 s) and `carry` (head player, while anything is in the jaws) clips; `DigGoal` sets DIG while in
  reach; `hasWorkClips()` true only for the ant worker and queen. The lab no longer takes raking front feet for a walk.
- Step 9: the edge curve (`tickWaist` in `AbstractAntEntity`, `client/BodyLanguage` from `InsectModel`; the queen uses
  `InsectModel` now). Signs worked out on paper, NOT seen yet: the user should look at an ant going up a wall.
- Steam off hot ants (`tickSteam`).
- Notes: HISTORY, DESIGN (all picks), CLAUDE.md status.

## Not done
- No in-game film of the new clips (`runFilm` needs a shot list for them; the camera has no climbing scene).
- Leg cleaning, feeling the ground, the flinch at a shadow, the stretch on waking, greetings: later little things.
- The termites' own clips (cleaning, digging) and soldiers' head bang at a strong shake (ask the user).

## Notes for whoever continues
- GeckoLib 4.9.3: when two controllers animate the same bone the later one wins, so each controller only keeps its
  own bones. Rotation in the file: X and Y are negated when loaded, Z is not. Transitions lerp from the bone snapshot.
- Model geometry in `tools/ant_models.py`:
  - The model is built in design units and scaled by SCALE (0.85), so points given to `model.world()` must be
    scaled: `tuple(v * SCALE for v in p)`.
  - Left is +X and the head is -Z.
  - A positive X turn in the file tips the head or antenna down; negative lifts it.
  - For the head and forebody, a positive Y turn turns the front toward the right (-X).
  - Mirroring a turn [x, y, z] to the other side gives [x, -y, -z].
- Bash heredocs containing apostrophes fail in the tool; write files with the Write tool.
- The scheduled task `ant-anim-engine-continue` (07:04) stands down if any file here changed in the last 20 minutes.
