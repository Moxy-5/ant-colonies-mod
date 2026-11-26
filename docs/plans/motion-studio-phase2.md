# Motion Studio, phase 2: one source of truth for motion (planned and started 2026-11-25)

The user's go-ahead 2026-11-25 ("lets go to step 2"); Neon is out for about a week, so Claude does the tools side too.
Phase 0 (`docs/plans/motion-studio.md`) gave the eyes: real hunts filmed with every mind on, every animal recorded. Phase
2 makes the game's motion and the lab's agree, one place for each rule. Ideas 20 to 26 of the plan, trimmed:
- **Folded in:** BUGS 372 (step 1), because it lives in the same clip-start code as step 3.
- **Merged:** 22 (foot slide across the speed band) and 26 (calf and cub sizes) into the pace report (step 2): a
  clip played at the speed its stride needs never slides; feet slide only where the play speed sits at a limit, and the
  calves' play speeds are in the recordings.
- **Scope (my pick):** the gait table for the lion and the buffalo now; the hippo, otter, monitor, crocodile and frog
  keep their own gait code (each tuned and approved in play, with quirks: the otter's bound never under 0.7, the
  crocodile's run with two switch points) and move onto the table one by one when next touched.

## Step 1. BUGS 372: blends run on real time
**Cause (found 2026-11-25 by reading GeckoLib 4.9.3's `AnimationController`, decompiled):** GeckoLib starts a blend into
a new clip by taking the next clip off its queue on any frame whose blend clock reads exactly 0, and its blend clock runs
at the clip's play speed. The lion plays its stalk at speed 0 while standing still between creeping steps, and a
lioness ending a freeze has not moved yet: the clock stays at 0 for two frames, the second frame takes again from the
now empty queue, and the player is left with no clip, stopped. It never recovers, because the lion keeps asking for the
same stalk, which GeckoLib takes as already set.
**Fix:** `SteadyController` (the shared clip player, already overriding GeckoLib's timing for BUGS 232 and 234) runs a
blend on real time, whatever the clip's play speed: a blend is the controller's transition length in ticks, as the
offline checker has always assumed. Every animal on `SteadyController` (15) blends in its set ticks at any play speed
(before: a clip at 2x blended in half the time, at 0.5x in twice, at 0 never).
**Check:** the hunt film's report shows no empty clip player; the stalk's body height stays crouched between freezes;
the buffalo film's checks no worse; `land` tests.

## Step 2. The gait table and the pace report
- **The gait table:** each body plan's `checks.walks` (each gait clip's own speed, px a second at size 1) already exists
  and the offline checker reads it; the game kept copies in Java (`LionEntity.Measures` walk, trot, gallop and stalk
  speeds; `BuffaloEntity.WALK_CLIP`, `TROT_CLIP`, `CHARGE_CLIP`). A new `gaits` section beside it holds, per gait, the
  speed it starts at and its play-speed limits; the framework's `Gaits` reads both from the plan (as `BodyPlans` reads
  the rest), and the lion and buffalo pick their gait and play speed through it. Their Java copies go. The numbers stay
  what they are today, so nothing changes in play.
- **The pace report** (`tools/pace_report.py`): from any film's recordings, each animal's time in each gait clip at
  each play speed, flagged where it plays under 0.8 or over 1.25 of its made-for speed or sits at a limit (there its
  feet slide), and by size (calves and cubs). Run on the hunt film and the buffalo film. What it finds about the clips
  (for instance the buffalo's walk, by its numbers played at about 1.65x at its usual pace) is a looks question:
  reported with a recommendation, a clip remade only with the animation checks (CLAUDE.md's hard rule).

## Step 3. Gait changes on the matching footfall
GeckoLib always blends into the new clip's first frame. A walk left at a moment whose legs are far from the trot's first
frame swings its legs through each other in the blend (the offline checker: walk to trot "angles apart" up to 108
degrees). Real animals change gait at a set point of the stride. So: the gait table names, for each change of gait, the
moment of the old clip whose pose is nearest the new clip's first frame (found by the offline checker, written by it into
the plan, never by hand), and the game makes the change when the old clip passes that moment (at most one stride late;
the play speed keeps the feet right meanwhile). The checker then judges each change at that moment.
**Check:** the checker's blend numbers for the lion's and buffalo's gait changes before and after; the hunt film's jump
flags at gait changes; `land` tests. **Full run after this step.**

## Step 4. One drawing clock
`StandTilt`, `Attention`, the lion's neck, `MoveRider`, the crocodile's, monitor's and hippo's models ease on the
computer's clock (`System.nanoTime`): a frozen-and-stepped film never shows a body mid-ease (a gap over 0.5 s snaps it),
and slow motion lies. Each eases on game time instead (ticks and the in-between time), the same seconds in play.
**Check:** the hunt and buffalo films stepped a tick a picture show the tilt easing; the touched animals' quick
courses.

## Step 5. The head turn eased out
The lion's and buffalo's head turn toward what they watch is applied only on plain loops and drops to nothing at once
at a clip change (up to 35 degrees). It eases in and out instead. **Check:** the recordings' head bone across clip
changes; the films' jump flags.

**Last:** a full run, the notes (HISTORY, status, CLAUDE.md), commit, push, `C:\Ant Mod` switched.

## Results
- **Step 1 (2026-11-25): built and checked.** `SteadyController.adjustTick` runs a blend at rate 1 whatever the clip's
  play speed (BUGS 372 fixed, its cause and lesson there). The hunt film again: no empty clip player in any lioness (was
  9 to 18 seconds each), every lioness's body 0.74 blocks over the ground through every freeze and stalk; the buffalo
  film's checks the same (82 parts inside against 81, 521 jumps against 544).
- **Step 2 (2026-11-25): built.** `framework/.../entity/body/Gaits` reads each plan's `gaits` section with its
  `checks.walks`; `BodyPlans.gaitsOf(model)`; each gait carries its loop clip. The lion (`LionEntity.gaits`, by its
  form's clips: `Form.clips`) and the buffalo (`BuffaloEntity.gaits`) pick their gait and play speed through it, and
  `galloping()`, `LionGoals`' gallop swat and `BuffaloGoals`' calf pace and run-up butt read the plan's switch points:
  the Java copies are gone (`Measures`' four clip speeds, `WALK_CLIP`, `TROT_CLIP`, `CHARGE_CLIP`, `TROT_FROM`,
  `CHARGE_FROM`). The numbers are the same (a unit test, `BodyPlansTest.theLionsAndBuffalosGaitsAreTheOnesTheGameUsedBefore`,
  pins them), except the lioness's and cub's stalk speeds, now their plans' measured 0.718 and 0.541 blocks a second
  (were 0.70 and 0.53: their stalk plays 2 to 3 % slower, its feet a little truer). The gallop swat's "galloping" is the
  drawn gallop now (one rule; before it had its own sum without the size floor).
  **The pace report** (`tools/pace_report.py`) on the hunt film: the stalk plays at 1.02 with its feet right (0.97,
  2 % off); the buffalo calf's charge at its top play speed the whole time, its feet sliding (the ground 2.1 times as
  fast as its legs: BUGS 378, with a recommendation); the cow's charge always about 2.1 (hurried, feet right) and the
  cub's walk about 1.4 (it walks at the grown lions' pace): clips made slower than the game moves them, a looks question
  for whoever next remakes them; the male changed gait 4 times, 2 of them straight back within a second (the walk and
  trot line, step 3's ground). Clips made in place that moved: the strikes (BUGS 375), and short slides on `graze`,
  `ambush_freeze` and `takedown_pin`.
- **Step 3 (2026-11-25): built.** `tools/clip_check.py --gait-changes [--write]` finds, for each change between two of
  a plan's gaits, the moment of the old clip whose pose is nearest the new clip's first frame (the farthest any box
  corner lies from its place in the other) and writes it into the plan (`gaits.<old>.change.<new>`, seconds; never by
  hand); `--switches` judges a gait change at that moment. The game: `framework/.../entity/body/GaitShift` (one per
  animal, drawing only) holds the drawn gait until its clip, followed as played (both ways, its loops unrolled), comes
  to the moment's next coming round after the change was wanted (at most one loop; at once from any clip that is not
  a gait, held while still easing into the drawn gait), through which the lion and the buffalo draw their gaits. (Its
  first form read a clip time going back as a new loop and changed up to a third of a stride early in films: one fail,
  then the rule above.) The poses the blend joins, before (the worst of the four moments the checker tried)
  and after: lion walk to trot 98 to 23 degrees apart at most (the fastest joint turn 24 to 6 degrees a tick), trot to
  walk 108 to 41, trot to gallop 110 to 57, gallop to trot 141 to 76; buffalo charge to trot 75 to 43, trot to charge 51
  to 37, walk to trot 35 to 14 (trot to walk 40 to 52: that one was already gentle and stays so, 10 degrees a tick at
  most). Nothing flagged. `tools/body_plan.py rewrite_plan` keeps a plan's file style when a tool changes it.
  The film: `tools/film/gait_changes.film` (a lioness and a bull sent at a walk, a trot, a gallop or charge, a trot and
  a walk, a tick a picture from three sides, each recorded); `tools/pace_report.py` lists each gait change against its
  plan's moment. **In the game:** all 8 changes (4 each) came inside the one-tick window holding their plan's moment
  (lioness walk to trot between 0.73 and 0.83 s of the walk, the plan 0.83; bull trot to charge 0.57 to 0.65, the plan
  0.64), none went back and forth, and each gait's planted feet swept back at the body's own speed (1.00 to 1.06).
- **Step 4 (2026-11-25): built.** `framework/.../entity/body/DrawClock.nanos(animal, partialTick)`: game time (the
  animal's ticks and the in-between time) in place of the computer's clock at every drawing ease: `StandTilt` (lion,
  buffalo), `Attention.aim` (lion, buffalo, hippo, otter), the lion's neck, `MoveRider.drawnShift` (otter), the
  crocodile's bend, lids, eyes and legs, the monitor's bend, hand over, tail, legs and look, the hippo's hand over and
  the frog's head. Each keeps its own easing (tuned in play); with no time passed (a frozen game) an ease holds; it never
  goes back (a stepped film's frames a tick back hold the furthest time drawn).
- **Found on the way (2026-11-25), both fixed: the frame films lied about time.** BUGS 380: every frame film played the
  animals' clips at twice their speed since BUGS 232 (2026-11-19), `SteadyController` counting a stepped tick twice
  (`tools/film/clip_clock.film`: 1.00 with the game running, 1.98 stepped; after the fix 1.00 both); the clip now follows
  the clock both ways. BUGS 381: a take's first camera was shot on the frame drawn as the step ended, the others a tick
  later; the camera now waits a tick after each step (all 1,020 pictures of the gait film at the same settled moment;
  the termite guard films unchanged, 0 of 580 pictures). The films are now the game's own time: what they showed
  before about clip speed and feet sliding (in the hunt films and the animals' courses) was half wrong.
- **Step 5 (2026-11-25): built.** The head turn toward what an animal watches is drawn always (lion, buffalo, hippo,
  otter): when it should not turn its head (a strike, a rest, a move) `Attention`'s aim eases to straight (0.6 s)
  instead of the turn vanishing at once. The hippo and otter had the same snap: one rule for all four.
  **In the hunt film:** at every clip change the neck turns against the chest at most 1.3 to 2.7 degrees a tick (six of
  the seven animals; the elder 11.4, inside her strikes, whose clips turn it up to 18 a tick on their own): no snap.
- **The last checks (2026-11-25).** The buffalo's frame film: every clip burst after the first showed one bull lying
  still, whatever the clip; the film of the code before phase 2 (another copy, by a helper) moved in every burst. Cause
  and fix in BUGS 383: the carcass a killed bull leaves shared the living bull's bones, and the camera's settled
  picture (BUGS 381) is a still frame where GeckoLib reuses them; the carcass now has bones of its own (`OwnBones`), and
  the film waits out each death and clears the carcass. After it: no burst still; the parts inside blocks only in the
  step scenes (the known step issue, buffalo step 2), the jumps in the step scenes, the fast doings and the looped
  one-shot clips' seam. **The hunt film's pace report:** all 14 gait changes on their planned moment (the cub's 9, the
  male's 4, the calf's 1); the stalk at 1.00 with its feet planted (1.00); but at each start from standing the gait
  rule held a walk while the animal already ran (the cow's feet sliding 4.2 times, the calf's 4.7): BUGS 384, step 3's
  rule's second fail, so a new starting idea goes to the user before any code (proposed in BUGS 384: wait for the
  footfall only while the old gait can carry the speed). The cub still changed walk to trot and back within a second
  twice (the male once): the speed hovering on the line, as in phase 0.
- **The review (2026-11-25, a workflow of 4 agents: three readers and a skeptic):** 8 findings, 7 confirmed. Fixed:
  the hippo kept its head turned through a block move (its aim now eases straight there, as the others'); in a burst
  of several frames a tick the camera's extra settling tick (BUGS 381) left the insects drawn at the last frame's
  in-between time, so their clips ran a tick ahead (the camera now sets the next picture's time); the pace report
  measured speed over a whole tick between drawings up to a third of a tick apart (now over the true time: the
  steady gaits' feet off 2 to 3 % instead of 11); the buffalo film's step scenes killed each bull frozen, so its
  carcass still landed on the course; the model makers (`buffalo_models.py`, `lion_models.py`) rewrote the plans
  without the gait table, which the game cannot do without (`body_plan.keep_gaits`, used by both writers). Two are
  about the gait rule, so they go with BUGS 384's rethink: a lion turning in place after a run, and a long frame
  hitch read as going back. Refuted: a plan's style change that no tool can reach.
