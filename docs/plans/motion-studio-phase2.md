# Motion Studio, phase 2: the glue between a clip and the game (planned and started 2026-11-27)

**This is the new phase 2.** The first phase 2 (gaits read from the body plans, gait changes on the matching footfall,
one drawing clock, the head turn eased out) was built 2026-11-25 and is done; on the user's word ("its a new phase
delete the old one that was already completed") its plan was deleted here. Its record stays in `docs/HISTORY.md` (the
bullet "The Motion Studio, phase 2: one source of truth for motion") and in git (this file before 2026-11-27).

The user, 2026-11-27: "lets start phase 2 of the animation engine have 4 sub agents review the code at the end of the
phase", then "check the claude drop off from neons handoff they had some good ideas", and picked **the glue plus Neon's
three small ones**: ideas 51 to 56 of `docs/plans/motion-studio.md` (part G, written after phase 3 step 5: "the clips
were the easy part; nearly every fault came from putting them into the game") and Neon's 57 to 59 (from
`docs/plans/motion-studio-neon-additions.md` on `neon/studio`, folded into the main plan). Neon's phase 3c (videos and
living motion, 60 to 64) comes after. Branch `claude/motion-glue` (from `claude/motion-phase1`, with `neon/studio`
merged first, as Neon asked: the martial eagle and the fliers' foundation came with it).

**At the end:** four subagents review the phase's code (the user's ask), their findings checked and fixed.

## Why this phase (what phase 3 step 5 showed)
Putting the feed clip into the game took more work than making it, and each fault was a different force found one bug
at a time: a cow shoving a lying feeder 0.05 blocks a tick (`LionEntity.isPushable`, `doPush`), riders drawn turned to
their mount (BUGS 421, `LionRenderer`), a skipped clip or a calm tick wherever a goal wrote its own order of clips
(`onFeet`, the feast's lying down: BUGS 376), the clip lengths typed into the Java by hand (`DOING_TICKS`), a model maker
wiping the plan's hand-added tables (`lion_models.py game`), a film that missed the moment it was made for. Each new
clip (the user plans to redo them all) would meet the same forces. This phase puts each in one place.

## The steps (in this order; my picks unless said)
1. **The tools' traps closed (58, then 56's other half).** Safest first, so no export can wipe a table again.
   - `body_plan.keep_sections` (Neon's `preserve_sections`, in place of `keep_gaits`): a maker writes what it knows;
     every section, check and list entry of the plan on disk that the maker's plan lacks is kept, in its place, and the
     maker says what it kept. Called by both plan writers (`body_plan.write_plan`, `model_parts.write_assets`), so every
     maker has it (the lion's and the buffalo's `game` too).
   - A clip made against another animal's table says so: the lion maker writes, into each holder plan, the fingerprint
     of the prey's spot table its holds were made from (`holds.made_against`); `tools/spots.py --check` (run by the
     build) fails when the prey's table has moved on, naming the clips to remake and the command.
2. **One feet rule (57; BUGS 456, 457).** The replay page's rule (a foot is the last bone of each leg in the plan with
   the bones below it; on the ground within 1 px, bearing within 0.25, sliding over 0.15 px a tick, not judged 3 ticks
   after ticks not recorded) moves into `tools/recording.py` (Python), the one place it is written. `tools/replay.py`
   measures the feet there and packs them into the page; the page only draws them (its own copy goes).
   `tools/pair_report.py` and `tools/body_course.py` judge feet through it too.
3. **The code's clip tables made from the clip files (53).** The framework reads each clip's length and whether it
   loops straight from the animal's own clip file in its jar (both sides; a stream read of two numbers a clip, never the
   bones), so the Java has no copy to fall behind. The lion's `DOING_TICKS`, its roar and cub-call lengths, its death
   length, and the buffalo's tables like them go; a one-shot is a clip its file plays once. `ClipLoopsTest` stays (it
   holds the Java's `thenLoop`/`thenPlay` to the files).
4. **One placed animal (51).** A framework piece for an animal set on a place by the game, not walking there (a holder
   on its prey, a feeder lying at its spot, a catch in jaws): while placed nothing pushes it or is pushed by it, its
   walking leftovers are cleared (route, wanted place, steering, speed), its body and head do not turn by Minecraft's
   own rules, and its place is set after the game's physics; it is drawn at its own heading (BUGS 421's fix, for any
   animal). The lion's holds and feeding, and the crocodile's and the otter's catches go through it; their own guards go.
5. **The clip map (52).** Each body plan says which clips are lying, sitting or standing (its postures) and the clip
   that takes it from one posture to another (`rest_down`, `rest_up`, `sit_down`, `sit_up`), and what a one-shot ends
   in. A goal asks for the clip it wants; the body walks the way there itself (up first, then the clip; down, then the
   feed), with no calm tick and no skipped clip. The lion first: its goals' `onFeet`, `lieDown`, the feast's lying
   down and the one-shots' ends go. `tools/clip_check.py --switches` reads the same map, so it checks the clip changes
   the game can make (each posture's ways in and out), not a hand list.
6. **The young's gaits (59; BUGS 378's tools).** `clip_check.py --size s` checks clips at a young size (the step and the
   bank a block at that size, its ground speed in blocks); a gait in the plan may name a young clip for small sizes
   (`gaits.<gait>.young`), which the game plays when the clip exists (a slot for the calf's and cub's own clips);
   `tools/pace_report.py` reports each young by size: the clip it plays, its play speed, and the clip speed its real
   pace needs. BUGS 378's play change (the calf slower than its mother) is not in this step.
7. **The whole scene checked offline, by part names (55).** `clip_check.py <prey> --scene <pose>`: everyone round a
   carcass (or a held prey) at once, at the real sizes, with the bystanders, saying which parts are in each other
   ("lion mane in buffalo ribs 1.8 px"), not "body 1.8 px"; and faster (the pair check took over 3 minutes).
8. **A test bench per interaction (54).** A short film scene for one interaction (one carcass and one or two lions; a
   hold): a camera that follows its actor close by its size, takes that start when its clip changes (`until
   doing=feed`), the recording's clock in game ticks through stepped takes. First bench: the feast (BUGS 449's lesson).

**Last:** the guard checks (offline checker, the touched animals' course scenes, the `land` and `river` game tests, a
full run), the four reviewers, their fixes, the notes, commit, push, `C:\Ant Mod` on the branch.

## Watch out for
- **Nothing changes in play by accident.** Steps 3 to 5 are moves of rules, not new rules: the numbers stay the same
  (a unit test pins the clip lengths the game used before), and the `land` and `river` tests must pass as before.
- **Fewer rules.** Each step removes copies; if a step needs a new tuning number to look right, stop (two fails, then
  rethink).
- **Lanes.** `tools/` is Neon's lane, but Neon's additions name 57 to 59 as Claude's; the shared body code
  (`framework/.../entity/body/`) is noted in `docs/HANDOFF.md`.

## Results
(Filled in step by step.)
- **Steps 1 and 2 (2026-11-27, commit 901f1577).** `body_plan.keep_sections` keeps every section, check and list entry
  of the plan on disk that a maker's plan lacks (both plan writers call it; a self-test). The lion maker marks each hold
  and feed clip with the buffalo table's print it was made against (`made_against` notes), and `tools/spots.py --check`
  fails when the prey's table moves on. One feet rule in `tools/recording.py`: the page draws what `replay.py` packs
  (43,690 of 43,692 foot moments as before, the 2 others on the 0.15 px line); `pair_report.py` and `body_course.py`
  judge feet through it. BUGS 456 and 457 fixed.
- **Step 3 (commit 76a205eb).** `framework/.../entity/body/Clips` reads each clip's length and loop from the animal's own
  clip file (a stream read, two values a clip). The lion's and the buffalo's hand tables are gone; `ClipsTest` pins the
  very numbers they had. `land` 45 of 45.
- **Step 4.** `framework/.../entity/body/Placement`: an animal placed by its own game code (`place`, each tick) is set
  there after the game's physics (`afterTick`, at the end of its tick); one carried by a mount that does not seat it is
  set by the mount (`seat`, from its `positionRider`). Either way it pushes nothing and nothing pushes it, its route,
  steering and speed are cleared, and its body and head face its heading; the shared renderer (`BoneMeshRenderer`) draws
  any such rider at its own heading (GeckoLib turns every rider to its mount, never asking `shouldRiderSit`). The lion's
  holds and feeding go through it (its `lyingAt` push guards, its renderer's turn fix and the buffalo's push guard went:
  Minecraft never pushes a rider and its mount); the crocodile's and the otter's catches are seated by it, so a buffalo
  in a crocodile's jaws is now drawn crosswise as it is placed, not along the crocodile (a looks fix). `land` and `river`
  167 of 167.
- **Step 5.** `framework/.../entity/body/ClipMap`, from each body plan's new `postures` (the lion, the lioness, the cub:
  lying with `rest`, its way down `rest_down`, its ways up `rest_up` and `rest_stretch_up`, its clips `groom` and
  `feed`; sitting; the buffalo: lying and held). The lion asks `toward(doing)` (the way there, then the clip, nothing
  between) and `upright()` (up first); its two `onFeet`, its `getUp`, the hunt's lying down, the feast's lying down into
  the feed, the chorus's, the answer's, the waking's and the cub's hide's "lying: up first" and both animals' one-shot
  end tables are the map's now. Small changes, all the same rule: a grooming lion called up gets up at once (it waited
  out the groom before in two places), and a feeding lion's goals see it lying (one `onFeet` did not). `clip_check.py
  --switches` checks the map's changes with the plan's own list (`body_plan.posture_switches`): 5 more a lion form, 2
  for the buffalo; the buffalo's `held_sink -> rest_up` (let go while going down) is flagged, the same 3.1 px tail flick
  as `held_down -> rest_up` had: BUGS 466. `ClipMapTest`, a self-test; `land` 45 of 45.
- **Step 6.** A gait may name a young clip (`gaits.<gait>.young`: its clip, the size it plays under, its own limits and
  change moments), which `Gait.at(size)` gives the lion and the buffalo (none named yet: the calf's and cub's clips are
  for the animation rework). `clip_check.py --size s` checks clips at a young size (a model pixel s times the blocks; a
  step or bank raised to a block at that size: a calf at 0.5 playing the grown step-up plows 8 to 13 px into it).
  `pace_report.py` reports each animal drawn under 0.8 of its model by size: the lion hunt film's calf (0.36) trots at
  1.79 times its clip's speed, its pace needing a trot made for 41.6 px/s against the grown 22.2 (BUGS 378's numbers;
  the play change is not in this step). `body_plan.py check` checks a young clip's name and speed; a self-test.
- **Step 7.** `clip_check.py <prey> --scene <pose>`: every spot of the prey's pairs on that clip or pose taken by each
  form the pairs allow there, at their real sizes, every 4 ticks; each against the prey, every two at their spots
  against each other, and round a carcass the full lions lying by the kill where `LionHunt.lieByKill` lays them (its
  numbers read from the Java), the carcass turned every 30 degrees. It names both parts (`Boxes.deepest_named`: "mane_chest
  in hips 2.6"). `buffalo --scene carcass` (both carcass sizes, 3 forms, 4 spots each, the bystanders) takes 3 minutes,
  what the pair check took for one holder. It found BUGS 467: neighbouring feeders 1 to 3.3 px in each other (mixed forms
  and the male's mane, never measured by BUGS 377's one-form check), ranks 0 to 2 lying into a feeder up to 2.6 px. A
  self-test.
