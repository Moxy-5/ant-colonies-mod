# Animation engine: overnight progress (2026-10-16)

The user's overnight job: finish the animation engine (`docs/plans/animation-engine.md`) and make 5 animations with it:
1. walk and hot walk (rebuilt), 2. curious look, 3. antenna cleaning, 4. dig and carry (one), plus hooking up the
sleeps, the hot look and the steam (handed over by the peer session). Open picks: my pick, marked "my pick, to
confirm" in the notes. Branch `anim-engine` in the worktree `C:/Ant Mod film` (from `anim-camera`, with
`brain-offline` merged in). Never touch `C:/Ant Mod`; merging into `brain-offline` waits for the user's OK.

## Done
- Branch `anim-engine` made and pushed; `brain-offline` merged in (b24246e).

## Next (in order)
1. `tools/ant_motion.py`: the gait engine (legs worked out backwards from where each foot must be, so feet stay
   planted), antenna engine, body engine, bone map. `tools/ant_models.py` uses it.
2. Rebuild walk and walk_hot; `python tools/anim_lab.py check worker_ant` must pass (no slide, 3+ feet down, no
   clipping, seamless loops). Each walk clip gets a `stride` timeline note (model units moved per clip).
3. Java: controllers split into `body` (body and legs), `head` (head, mandibles, antennae), `extra` (little things);
   each ant's own clock (`AntAnimationController`); walk playback from the real distance moved and the stride.
4. Sleeps by turns while resting (`NapGoal`, asleep flag), hot look and steam.
5. Personality traits, `NoticeGoal` (ants by sight, termites by vibration), the look clip and the live head look.
6. Antenna cleaning (strigil) variants, dig and carry clips.
7. Edge curve (the rear follows a moment later) and a climb pose.
8. Game tests in a new `body` category; notes (HISTORY, CLAUDE status, DESIGN, the plan); morning report.

## Notes for whoever continues
- GeckoLib 4.9.3: when two controllers animate the same bone the later one wins, so each controller only keeps its
  own bones. Rotation in the file: X and Y are negated when loaded, Z is not.
- The scheduled task `ant-anim-engine-continue` (07:04) stands down if any file here changed in the last 20 minutes.
