# Full code review, round 12: the animation engine (reading round, 2026-11-26)

Read on branch `claude/review-round-12` at commit `2a765dee`, by sessions that did not write it. Rule book:
`docs/plans/code-review.md`. No code was changed, no game, gradle or film was run. The readers ran the offline Python
tools and small scripts of their own in a scratchpad (not kept), and read GeckoLib 4.9.3 from its own jar with `javap`
(no sources jar is in the gradle cache) and Minecraft 1.21.1 from the NeoForge sources jar.

**What was read, every line:** in `tools/`: `clip_check.py`, `body_plan.py`, `spots.py`, `pair_report.py`,
`anim_lab.py`, `motion_kit.py`, `ant_motion.py`, `verify_animal.py`, `body_course.py`, `guard_films.py`,
`game_compare.py`, `film_sheet.py`, `film.sh`, `film_swap.py`, `film_profile.py` (and the kit calls of the lion,
buffalo and monitor makers); in the framework's `entity/body/`: `MoveRider`, `BlockMoves`, `Gaits`, `GaitShift`,
`DrawClock`, `SteadyController`, `TailDrag`, `Spots`, `BodyPlans`, `AntAnimationController`, `OwnBones`; in its
`client/`: `FilmDirector`, `FilmChecks`, `FilmStep`, `FilmWatch`, `BodyRecorder`, `FrameWatch`, `FilmFrameMeter`,
`FilmFactsBoard`, `ReportShot`, `DraggedTail`, `BoneFrames`, `PoseMemory`, `BoneMesh`, the film hook in `AntRenderer`;
the `runFilm` setup in `build.gradle`; and on the savanna side as far as the thread reached (the otter's, monitor's
and hippo's moves and models, `LionHunt`, `LionEntity`, `BuffaloEntity`, `BuffaloModel`, `StandTilt`, `HeldCatch`).
About 9,000 lines of Python and 6,500 of Java.

**Who read it.** Four readers with a slice each (the offline checker; the lab and the authoring kit; the in-game
films and the one command; how the game plays clips and moves) and one reader across all of them on one question:
"is the game the one truth", that is, do the offline tools give the game's answer. The first session ran out of
quota after the readers finished; a second session merged their files, checked every grade A finding itself against
the lines it names, and wrote this file.

**How to read a finding.** Rating: **A** the player can meet it, or a check the rule book relies on says "fine" when it
is not (a "false green": a broken animation reaches the user unseen), or a tool bug that blocks making animations;
**B** it will cost us when building on this; **C** a tidy-up. Proof: **proven** (the path walked through the code,
lines named, or run), **likely** (one link not checked, named), **to measure**. A finding's `R` number is its only
number (no `docs/BUGS.md` number since 2026-11-26). Findings keep their numbers for good; a fix session adds a status
line under a finding and never rewrites it.

**Count:** 73 findings: 17 A, 36 B, 20 C (86 in the readers' files; 13 were the same thing found from two sides and
are merged, each merged finding says so).

## The short of it

The engine's maths is right. Every reader who checked it against GeckoLib's own code found the poses, rotation order
and signs, pivots, easing, blends into a clip, the spot tables' placement and the block moves' paths giving the game's
answer. The game-side players (`SteadyController`, `DrawClock`, `GaitShift`, `BlockMoves`) are sound in ordinary play.
Nothing that is solid needs a rewrite.

What is wrong is **what the checks say**, more than what they compute:

1. **The checks often say "fine" when they have not looked, or looked and not judged.** The offline checker prints
   findings without the word FLAGGED, so the one command's summary counts none of them (R12-2); it never judges a
   walk's planted feet (R12-1); a spin the blend check was built for passes on most animals (R12-4); the insect lab's
   floor sinks with a sunk foot (R12-20). The one command says "passed" for an animal never filmed, after a game crash,
   or for a scene where the animal never moved (R12-35 to R12-38). The worse the failure, the cleaner the report.
2. **The game draws layers the offline tools do not know.** The buffalo turns its head and tilts on slopes while held,
   so a throat hold bites the air with every check green (R12-71); the monitor's leg fold is a hand copy of the swim
   clip, out of step (R12-70); GeckoLib takes 5 ticks, not the blend, to put a dropped bone home (R12-9); the offline
   tail is a simpler rule than the game's (R12-12). No check compares the offline pose with the game's record
   (R12-22), which would have caught all of these.
3. **Four things a player can see in the block moves:** the otter skates on with frozen legs after a step down
   (R12-58), an animal first seen mid-move is drawn at the move's start and jumps (R12-59), a far monitor pops a block
   on every move (R12-60), and on a slow server moves rewind (R12-61).
4. **Making animations is slower than it needs to be:** the checker re-runs every clip every time (the monitor about
   9 minutes, R12-15), the pair check takes 75 s (R12-8), and the watch mode films old spot tables and gait moments
   after they change (R12-42).

## The ones that matter most

1. **R12-2 with R12-1, R12-4 and R12-3:** make every check of `clip_check.py` say FLAGGED when it fails, judge walk
   feet and jumps, catch a whole-turn spin at any blend length, and give a count and an exit code. This is one
   change to one file and turns most of the checker's silent findings into flags.
2. **R12-35 to R12-38:** `verify_animal.py` must never say "passed" without a game film of the animal, after a
   crash, with no recording, or when the scene's walker never got where it was sent.
3. **R12-71 and R12-70:** the two places where the game draws something the offline pose does not have, both on
   animals the Motion Studio is working on now.
4. **R12-58 to R12-61:** the four block move faults a player can see.
5. **R12-20:** the insect lab's floor, the one A in the ants' and termites' tools (Matabele grooming and the termites'
   fall have feet sunk 1.2 to 2 units today with a clean check).
6. **R12-22:** one measured match between the offline pose and the game's own recording, run on the frame films the
   one command already makes. It turns every drawn-only layer into a number instead of a surprise.
7. **R12-42 and R12-15:** the two biggest costs to the speed of making animations.

## For the user to decide

Nothing in this round needs the user's call before a fix. The fixes that touch looks (R12-71: a held buffalo stops
turning its head toward its holders; R12-64: the hold loops play at an even pace) bring the game to what was already
meant; a fix session that judges otherwise writes a `**Your call**` line under the finding.

## What this round did not do

- **Nothing was run in the game.** Every game-side finding is from the code (and GeckoLib's bytecode); R12-61 is
  "likely" because no slow server was run, R12-59 names its one unchecked link (that GeckoLib works out a culled
  animal's clips only when it is drawn). The four block move faults have not been filmed.
- **The readers' scratch scripts are gone** (they lived in a session scratchpad). Each finding names what the script
  did, so it can be written again in a minute.
- **The first session's own run of `clip_check.py` on every animal stopped part way** when its quota ran out (the
  monitor took 535 s, the crocodile 20, the bullfrog 141, the hippo 25; the otter's run was cut off).
- **The workshop's looks and each animal's maker** were outside this round (only their calls into the kit were read).
- **One reader across the parts, not two.** The second thread was left out; the four slice readers covered its files.
- The second session checked all 18 grade A candidates against their lines. Three changed: the guard films' "never
  fail" is how the tool is written (its header says changed pictures are "listed, to look at"), so R12-39 is B; the
  pair check's loop coverage (R12-6) was measured clean on today's clips, so B; and the thread reader's "the checker
  never measures a loop's seam" is wrong (`Played.posed` passes `t = length` to `Clip.pose`, which wraps a loop to 0,
  so the seam step is measured; R12-2 keeps the half that holds: it is never judged).
- `docs/BUGS.md` was searched by file and class name, not read whole.

---

## Slice: the offline checker (`clip_check.py`, `body_plan.py`, `spots.py`, `pair_report.py`)

### R12-1 (A, proven): a walk's planted feet are never judged for hanging, and a walk foot that never touches the ground raises no flag
**What is wrong:** the "planted feet hanging over the ground" check (it catches a foot place out of its leg's reach)
only looks at a foot while it stands still in the clip's own frame (moves under 0.15 px in 0.05 s). A walk, trot or
charge clip is made as a treadmill: the body stays put and a planted foot slides back at the walking speed (the
checker itself prints "should slide back at 9.72 px/s", 0.49 px in 0.05 s). So a planted walk foot is never "still"
and the hanging check never looks at it. The walk lines that do see it ("leg_bl_foot never down", "down 57% of the
stride") are printed only: no FLAGGED, no flagged moment, no red frame, and `verify_animal.py` counts nothing.
**What the user or player would see:** a walk with a foot floating over the ground, or a foot that never lands, passes
the offline check with "planted feet hanging: none" and reaches the game.
**Where:** `tools/clip_check.py:510-516` (the "standing still" gate), `:533-549` (the walk lines, print only),
`tools/verify_animal.py:87-92` (counts only FLAGGED and "parts inside").
**Proof:** run by the reader: the buffalo's `walk` copied with its whole hind left leg lifted 3 px for the whole clip:
"planted feet hanging over the ground ...: none", "leg_bl_foot never down", no FLAGGED anywhere. Checked again by the
main session: the gate at line 513 (`math.dist(track[i - 2][2], track[i + 2][2]) >= 0.15: continue`).
**Fix proposed:** judge planted walk feet in the walk's own moving frame: a foot counts as planted while it moves back
at the walking speed (within a tolerance) and is low; flag a walk foot that is "never down" or whose planted part sits
more than `feet_reach_down` over the ground; print FLAGGED on those lines so the summary counts them.

**Status (2026-11-26, `claude/review-fix-12`): fixed: a walk's feet are judged in the walk's own moving frame (a foot going back at the walk's speed is planted): one that hangs over 1 px, or is never down, says FLAGGED (`clip_check.py`, `WALK_PLANTED`, `WALK_HANG`, `WALK_DOWN`; self-test `walk_foot_held_off_the_ground_is_flagged`). A rule for a planted foot sliding sideways was tried and taken out again (it flagged the otter's real walks).**

### R12-2 (A, proven): most of the checker's findings never say FLAGGED, and a jump (a pop) is never judged at all, so the summary the rule book reads counts none of them
(Also found by the thread reader.)
**What is wrong:** `verify_animal.py` (the one command the rule book names) turns clip_check's output into "N clips, K
with a flag, M with parts inside a block, J clip changes flagged" by searching for the word FLAGGED and the "inside
the ground" header. Only four checks ever print FLAGGED: knees and elbows, joint turns, the swim wave, clip changes.
These never do, so they never reach the summary:
- planted feet hanging (prints a list, never FLAGGED);
- a walk foot never down or sliding sideways (R12-1);
- the biggest jump of a part in 1/80 s (a pop): printed as numbers, with no limit at all, never added to the
  flagged moments, so the strip's promised "red frame round a picture taken within a frame of a flagged jump" can
  never appear;
- the jaw's teeth through the lower jaw (prints "TEETH THROUGH THE JAW", not FLAGGED);
- the dragged tail inside the ground (its own line, no flag);
- parts that must hide in the body at rest (`hidden_at_rest` prints FLAGGED, but before the first "== " section, and
  `verify_animal.py` throws that part away with `re.split(...)[1:]`).
A loop's seam (its last frame back to its first) is sampled (the last sample at `t = length` wraps to 0), but, like
every jump, never judged.
**What the user or player would see:** a clip with a 10 px pop, a pop at its loop's seam every round, a foot out of
reach, teeth through the jaw or a tail in the ground reads "0 with a flag" in the summary; Claude, reading the summary
as the rule book asks, shows it to the user.
**Where:** `tools/clip_check.py:504-529` (feet), `:595-603` (jaw), `:496-498` (tail), `:620-623` (jumps, no limit),
`:1503-1505` (`hidden_at_rest` printed before the sections), `:17-18` (the docstring's promise),
`tools/verify_animal.py:85-92`.
**Proof:** walked: the only `flagged.add` calls are lines 493, 561, 634; the only "FLAGGED" strings are lines 558, 631,
668/670, 733, 807. Run on today's monitor (`clip_check.py monitor '*' --switches`, as verify runs it): FLAGGED twice
(two clip changes), while 24 of its 50 clips have a part jumping more than 5 px in 1/80 s (the dragged tail up to
38.6 px in `water_drop_850`; whether that is real or the checker's own tail rule, R12-12, nobody is told either way)
and three water clips have the dragged tail up to 3.1 px in the ground. Elsewhere today, unflagged: the bullfrog's
`attack` "planted feet hanging: l1 3.6 at 0.41"; the death clips of the frog, crocodile and crab list feet 2 to 16 px
up (lying on their backs: right, but there is no `feet_free`-style list for it, so a real fault would sit among
expected ones). Checked again by the main session: lines 620-623 print `jumps` with no test; `Played.posed` (296-298)
passes `min(t, length)` to `Clip.pose`, which takes `t % length` for a loop (`anim_lab.py:613-615`).
**Fix proposed:** give each check one "bad" rule and one wording: every judged line ends with FLAGGED when it fails
(feet hanging over `feet_reach_down`; a jump over a limit from the clip's own motion, the course's and switch
check's rule: more than 2 times its usual move and over 3 px; teeth through the jaw; tail in the ground), add their
moments to `flagged`, print `hidden_at_rest` inside a section (or count it in verify), and end with a count line and
an exit code (R12-3). Give the death clips a list like `feet_free`.

**Status (2026-11-26, `claude/review-fix-12`): fixed: a foot hanging and teeth through the jaw say FLAGGED; a part's jump inside a clip (over 3 px and 3 times its moves round it) and a loop's open seam (its last pose against its first) are judged and say FLAGGED; the summary line counts the flagged clips and the exit code is 2 when anything is flagged (self-tests `pop_inside_a_clip_is_flagged`, `loop_seam_left_open_is_flagged`). A foot held up on purpose is named in the body plan (`checks.feet_up_ok`, written by the lion and buffalo makers too). The tail line stays unflagged and now says why (rough offline; the course judges it). What it found in today's clips: BUGS 428.**

### R12-3 (B, proven): clip_check always exits 0, whatever it finds
**What is wrong:** `main` returns 0 on every path (and `run_pairs` returns 0 even with flags), so a caller can only
learn the result by searching the printed words. Every check that does not print the exact word FLAGGED is lost to
any script.
**What the user or player would see:** nothing directly; it is why the summary misses so much (R12-2).
**Where:** `tools/clip_check.py:1436`, `:1542`.
**Proof:** walked: both return statements are `return 0`; the switch count `bad` is printed, never returned.
**Fix proposed:** end with "N flagged" for clips like the pairs do, and return 1 when anything is flagged (verify can
keep treating findings as not hard by reading the count).

**Status (2026-11-26, `claude/review-fix-12`): fixed with R12-2: exit code 2 when any clip, clip change or pair is flagged (0 clean, 1 a call mistake or a crash); `verify_animal.py` reads it.**

### R12-4 (A, proven): a part spun a whole turn at a clip change passes the blend check on any animal with a blend of 4 ticks or more
**What is wrong:** BUGS 249's lesson is that one pose written two ways (an angle and the same angle plus 360) spins
the long way round through GeckoLib's blend. The blend check flags a change only when the fastest turn beats 90
degrees a tick, or a part's jump beats both 3 px and twice the clips' own biggest move. With a 4-tick blend a full
turn is exactly 90 a tick (not over 90), with the crocodile's 8 ticks only 45; and a small part (a claw, a toe, an
ear, the jaw) moves too little for the jump rule. So the spin the check was built to catch passes on the lion family
(4), buffalo and hippo (5), bull shark and distichodus (6) and crocodile (8). It still works on the frog (3 ticks),
where it was proven. The "angles apart" list does print the 340 or 360, but unflagged.
**What the user or player would see:** a jaw, ear, claw or paw whirling round once every time the animal changes
clip, shown to the user as "0 clip changes flagged".
**Where:** `tools/clip_check.py:789-801` (turn per tick and the flag rule).
**Proof:** run by the reader: the lioness's `walk` copied with one bone written a turn on (the same pose), then
`check_switch(idle, walk_copy)`: jaw +360 "fastest turn 90 deg a tick (jaw)", jump 6.3 against 3.2, not flagged; ear,
claw, toe +360 not flagged; the paw at +340 (6.3 px) not flagged. Checked again by the main session: `d = max(...) /
ticks` and `flag = jump > max(2.0 * own, 3.0) or turn > 90` (lines 790-801).
**Fix proposed:** flag every bone whose angle gap between the two poses is over 180 degrees on any axis (no real blend
needs the long way round; GeckoLib always takes it), whatever the blend's length; keep the per-tick limit for the rest.

**Status (2026-11-26, `claude/review-fix-12`): fixed: a part whose two poses are over 180 degrees apart at any moment of the blend is flagged whatever the blend's length, named "the long way round" (self-test `spin_a_whole_turn_at_a_clip_change_is_flagged`). It found five real ones on the bullfrog: BUGS 427.**

### R12-5 (B, proven and measured): the corner of a step or bank can sink into a box unseen, offline and in both game checks
(Merged: the checker reader for `clip_check.py`, the films reader for the course and the camera's checks.)
**What is wrong:** "parts inside the ground, the step or the bank" tests only each box's 8 corners and 12 edge middles
(`clip_check.py`), its 8 corners (the course, `body_course.py`), or a 3 x 3 x 3 grid measured to the nearest face of
one block (the camera's `FilmChecks`). On flat ground that is enough (a box's lowest point is a corner). A step or
bank has a jutting edge (its lip), and a box riding over that lip (a belly climbing a step, a chest sliding over a
bank's edge) can take the lip deep into one of its faces while every tested point stays outside. The big animals'
boxes make it worse (hippo hips 31 x 15.5 x 13.5 px, buffalo chest 20 x 15 x 7.4 px).
**What the user or player would see:** a climbing or bank-crossing body visibly cut by the block's edge in the game,
with every check reading "nothing".
**Where:** `tools/clip_check.py:436-451`, `:369-372` (`with_edges`), `:209-226` (`Scene.solid`);
`tools/body_course.py:383-397` (corners only); `client/FilmChecks.java:65-66`, `157-178` (GRID 3), `193-206` (depth to
the nearest face of one block).
**Proof:** run by the checker reader: a 20 x 6 x 6 px belly box pitched up 20 degrees across a step 8 px high: the
checker's own `Scene.solid` gives 0.0 px inside; the lip line lies 2.9 px inside the box. Measured on every step and
bank clip of seven animals: the hippo's hips 0.6 to 0.7 px into the lip on six step and out clips with the checker
reading 0.0; the otter's ribs 0.7 (`step_up_1000`); the monitor's dragged tail up to 1.7 px where the checker reads
0.5; buffalo, lion, lioness, crocodile none. The films reader worked an example for the camera's grid (a lip 0.11
blocks inside a box's bottom edge, missed by the corners and by the middle grid point measured to the step's side).
Graded B because today's measured depths are small; it would be A on a body that rides a lip harder.
**Fix proposed:** one exact test, shared in spirit by all three: a turned box against each nearby block's box (the
separating-axis test, 15 axes, cheap), giving the overlap's depth; or at least test the scene's lip line across the
box with `box_depth`, as the reader's script did, and report the deeper of the two.

### R12-6 (B, proven coverage; measured clean today): the pair check sees only part of a looping prey clip, at one phase, and not the blend or the walk
(Merged: the checker reader, and the thread reader's "the hold checks sample fewer moments than the game shows".)
**What is wrong:** for a prey clip that loops, `check_pair` poses both animals for `2 x (hold clip + blend)` ticks, a
number taken from the holder's clip, not the prey's. The buffalo's `idle` is 88 ticks: with the lioness's 15-tick
`bite` only ticks 0 to 38 of it are ever posed (43 %), with `takedown_pin` on `rest` 60 of 88. It also checks only one
lining up of the two clocks (the hold starting on the prey's tick 0), while in the game a lioness takes hold at any
moment of the prey's loop. Three more small blind spots: through the 4 to 5 ticks of the prey's blend into a one-shot
the game places the holder on the clip's first row (`Spots.at` clamps a negative tick to 0) while the drawn body is
still blending, and the check starts at the clip's first frame; and a held cow walking or trotting is held at the
`idle` middle (a clip not in the table), never checked.
**What the user or player would see:** a buffalo idle that swings its head or tail into a holding lioness late in its
loop would pass the pair check. Measured gaps today (model px): the spot moves at most 2.4 between a clip's end and
the next one's first row (`held_down` to `death_down`), 0.6 to 0.8 over the idle and `held_down` loops, 1.2 (walk) and
2.5 (trot) from the idle middle.
**Where:** `tools/clip_check.py:1147` (`n`), `:1079-1104` (one alignment); `framework/.../entity/body/Spots.java:191-200`;
`savanna/.../animal/BuffaloEntity.java:318-334, 524-526`.
**Proof:** walked; prey clip ticks against ticks checked: bite/idle 88 vs 38, throat_hold/idle 88 vs 84,
hold_down/held_down 48 vs 84, hold_down/rest 88 vs 84, takedown_pin/rest 88 vs 60. Over the whole loops, today's
bite/idle and takedown_pin/rest find the same depths as the part checked (nothing hidden now), which is why this is B.
**Fix proposed:** for a loop, pose at least one whole prey loop and judge the bite at every prey tick a bite could fall
on; start each one-shot's check at minus the blend, posed as `switch_poses` does; list walk and trot in the buffalo's
`spots.clips` with a pair for them.

### R12-7 (B, proven): "jaw to the hide" measures the nearest box of the prey, not the spot, so a jaw 2.6 px off its spot reads 0.0
**What is wrong:** the pair check's bite number is the jaw's distance to the nearest face of any prey box. The holder
is placed so the jaw is over the spot flat; what can go wrong is height, and a jaw above or below the spot slides
along the same flank, rump or neck face, so it still reads about 0. Any prey box counts too (a leg, a horn), so a bite
into the air beside a leg reads as on the hide.
**What the user or player would see:** a holder whose jaw sits well above or below its spot gets a clean line; the
lines look reassuring while the phase 3 aim (jaw at its bite on the spot) is not met.
**Where:** `tools/clip_check.py:1162-1163`, `:1040-1042` (`Boxes.gap`).
**Proof:** run by the reader: at every bite, the jaw's distance to the spot against the printed gap: takedown_pin on
`rest` every spot 2.5 to 2.6 px off (the jaw above it) with gaps -0.06 to 0.0; bite on `idle` 2.1 to 2.2 px under the
spot, gaps -0.07 to +0.21.
**Fix proposed:** print and judge the jaw-to-spot distance (and its height difference) at each bite beside the gap to
the skin; the skin gap only says "in the air" or "buried".

**Status (2026-11-26, `claude/review-fix-b6`): fixed: each bite's line also says how far the jaw is from the spot itself and how much of that is height, and over 4 px is flagged "jaw off its spot" (`clip_check.SPOT_OFF`, my pick). It found a real fault at once: the lioness's `hold_down` and `hold_sink` on a buffalo lying down or sinking put the jaw 5.5 to 5.9 px above every spot (15 lines); left for the animation rework.**

### R12-8 (B, measured): the pair check takes 75 s for one holder and prey
**What is wrong:** `Boxes.depth` tests each of thousands of box points against each box whose bounds hold it, for every
spot, every second tick and every pair: 6.3 million calls, 78 of 95 profiled seconds.
**What the user or player would see:** `verify_animal.py lioness --offline` (and the buffalo's) waits over a minute on
this step alone, every time; the rule book promises the offline part in "a few seconds to a minute".
**Where:** `tools/clip_check.py:1021-1038`, `:1150-1175`.
**Proof:** `time python tools/clip_check.py lioness --pair buffalo`: 75 s; profiled: `depth` 6,346,800 calls, 77.9 s.
**Fix proposed:** test a box's 20 points against the other body's bounds once and drop the outside ones before
`deepest`; keep the prey's posed boxes per tick across pairs (several pairs share a prey clip); or test box against box
(R12-5's separating axes) instead of point against box.

### R12-9 (B, proven by GeckoLib's own code): a bone the new clip does not move goes home over GeckoLib's 5-tick reset, not the blend
(Also found by the thread reader.)
**What is wrong:** the blend check eases every bone from the old pose to the new clip's first frame over the blend's
ticks, a bone the new clip has no keys for going to rest. GeckoLib does not blend such a bone: it blends only the
bones the new clip keys, and `AnimationProcessor` lerps the others back to their rest over `getBoneResetTime()`, 5 ticks
by default (no animal overrides it). So for those bones the checker's per-tick numbers are wrong: too gentle for the
crocodile (blend 8, real 5: 1.6 times the jump), the bull shark and distichodus (6); too harsh for the frog, catfish,
barb and tadpole (3). It is common: 80 of the monitor's 116 checked changes drop a bone (`walk` to `idle`: chest,
hips, ribs), 10 of the crocodile's 21, 24 of the hippo's 42, 20 or 21 of each lion's (their claws).
**What the user or player would see:** a dropped bone snapping home faster in the game than the offline check says
(crocodile), or flags the game would not show (frog). Measured on the crocodile with the game's rule: no change
crosses the flag today (the biggest moves 1.9 to 2.1 px).
**Where:** `tools/clip_check.py:737-757` (`rest` for a bone missing from clip b).
**Proof:** `javap -c` on GeckoLib 4.9.3's `AnimationController.process` (transition points only for the new clip's
bones) and `AnimationProcessor.tickAnimation` (bones not changed lerped from their snapshot by `(animTime -
lastResetRotationTick) / getBoneResetTime()`); `GeoAnimatable.getBoneResetTime()` returns `5.0d`; no
`getBoneResetTime` in the mod's Java.
**Fix proposed:** in `switch_poses` ease a bone missing from clip b over 5 ticks (read from the Java if an animal ever
overrides it), and name it in the change's line.

### R12-10 (B, proven): the blend length is read from the Java by a pattern that misses three animals and every blend changed while playing
(Also found by the thread reader.)
**What is wrong:** `blend_ticks` reads the word after `"body",` in the controller's line. The monitor, hippo and otter
write `MOVES.lead()` there (the blend is the block moves' `lead_ticks`, a JSON file); the pattern takes `MOVES.lead`,
finds no number, and quietly uses the plan's `checks.blend` (or 4), labelled "the plan". Today the copies match
(hippo 5, otter 4, monitor default 4), but a change to `lead_ticks` would leave the checker on the old number with no
warning, against the file's own "never a copy". And a blend changed while playing is never seen: `DistichodusEntity`
sets 3 ticks for BOLT and LEAP (`transitionLength(...)`); the checker uses the constructor's 6.
**What the user or player would see:** after a `lead_ticks` change, blends checked at the wrong length; today a
distichodus's swim to leap blend jumps twice as far a tick as checked (tail fin 5.4 px a tick against the 2.7 checked;
not over the flag).
**Where:** `tools/clip_check.py:119-135`; `MonitorEntity.java:1647`, `HippoEntity.java:1993`, `OtterEntity.java:2844`;
`DistichodusEntity.java:337, 355`.
**Proof:** ran `blend_ticks()` for all 17 animals (monitor, hippo, otter "the plan"); ran `check_switch` for the
distichodus's four bolt and leap changes at 6 and at 3 ticks.
**Fix proposed:** the blend in one place both sides read without patterns: the plan's `checks.blend` (or the move
file's `lead_ticks`) is what the Java reads too (`BodyPlans` already loads plans for `Gaits`); per-clip blends (the
distichodus's 3) as a plan table `blends: {clip: ticks}` read by both. Until then, stop with an error when nothing can
be read instead of a quiet fallback.

### R12-11 (B, proven): the picture drawn for a flagged clip change shows a different moment from the one flagged
**What is wrong:** `check_switch` judges a looping clip at the worst of four moments, or a gait change at its moment
from the plan; `switch_strip` (drawn whenever a change is flagged) always leaves the loop at 0 s.
**What the user or player would see:** Claude opens `sw_<a>_<b>.png` to look at a flagged blend and sees a calm one.
**Where:** `tools/clip_check.py:1373-1374` (no phase passed), `:766-799`.
**Proof:** walked: `switch_poses(animal, a, b, ticks)` with `phase=None` gives `ta = 0.0` for a loop.
**Fix proposed:** have `check_switch` return the worst phase and pass it to `switch_strip`; write it in the strip's title.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: `check_switch` keeps the moment it judged (`WORST_PHASE`) and `switch_strip` draws that moment and writes it in the title.**

### R12-12 (B, proven by reading both): the offline dragged tail is its own simpler rule, never matched against the game's TailDrag
(Also found by the thread reader.)
**What is wrong:** on clips with a path the checker drags the tail itself (`Played._drag`, "as TailDrag pulls it"):
each joint pulled a link behind the one in front, sagging 0.6 px a tick, eased halfway to the surface a tick over
water, kept the plan's radius above the floor, worked out once a tick and held for the tick's four 1/80 s samples.
The game's `TailDrag` eases each joint in world time (0.15 s) toward the model's rest heights, bends each joint at most
30 degrees up or down and 20 sideways (`MAX_JOINT`, `MAX_SIDE`), no piece steeper than 60 (`MAX_STEEP`), folds at most
85 at a block's face (`MAX_FOLD`), bridges low lips as a rope, caps joints by the root's carried height, straightens
over 3 s, and is worked out every drawn frame. The rule book's rule 1 asks a rule kept twice to have a match check;
this one has none.
**What the user or player would see:** the tail lines of the check describe a tail the game never draws: today the
monitor's dragged tail shows jumps up to 38.6 px in 1/80 s and 3.1 px in the ground offline, and nobody can say
whether the game's does that (the tick-held rows alone make every tick's move show as one jump, four times too big).
**Where:** `tools/clip_check.py:286-294`, `:307-348`; `framework/.../entity/body/TailDrag.java:1-140`, `:173-314`;
`framework/.../client/DraggedTail.java:66-99`; the only user: `monitor.json`'s `checks.tail_drag`.
**Proof:** walked both; `_drag` has no joint limit; `TailDrag` clamps with `MAX_SIDE` (line 210) and `MAX_STEEP` (224).
**Fix proposed:** run the real rule offline (port `TailDrag.update` once, with a test that feeds both the same joint
path and compares), or record the game's tail in a frame film and compare (R12-22); at least interpolate the rows
between ticks, and until then label the tail line "rough, the course judges it" and do not count it.

### R12-13 (B, proven): a spot table can be behind its plan or the posing code and `--check` still passes
(Merged: the checker reader, and the thread reader's "the fingerprint leaves out the posing code".)
**What is wrong:** the table's fingerprint (what `spots.py --check` and the build's `checkSpotTables` compare) holds
the plan's `spots`, `holds`, `strikes`, `legs`, `bones`, the blend for strikes, and the geo and animation files. But
the rows are posed through `clip_check.Played` and `HingedModel`, which also read the plan's `checks.hinge`,
`checks.tail_drag` and the clip's path notes; `trunk()` reads `chain`; and the posing code itself (`clip_check.py`,
`anim_lab.py`) is not in it (`FORMAT` must be bumped by hand). A change to any of these changes what the table should
hold without changing the fingerprint.
**What the user or player would see:** today nothing (the buffalo and lions have no hinge or tail drag). On the next
prey with a hinge (a monitor or crocodile as prey) the game would place holders on stale spots after a plan change,
with the build green.
**Where:** `tools/spots.py:63`, `:85-98` (fingerprint), `:140-147` (rows through `Played`), `:155-163`;
`tools/clip_check.py:160-170` (`HingedModel.transforms` reads `checks.hinge`).
**Proof:** walked: `fingerprint` hashes `[FORMAT, spots, holds, strikes, blend, legs, bones]` and the two files.
**Fix proposed:** hash the whole `checks` section, `chain`, and a digest of `clip_check.py` and `anim_lab.py` (as
anim_lab's own cache does); or build the table and compare it with the file ("would write the same file").

**Status (2026-11-26, `claude/review-fix-b6`): half fixed: the fingerprint now takes the plan's `checks.hinge`, `checks.tail_drag`, `checks.px` and `chain`; the four tables were written again (only their fingerprints changed). Left on purpose: the posing code is still `FORMAT`'s, bumped by hand; hashing `clip_check.py` would put every table behind at each edit of the checker.**

### R12-14 (B, proven; measured clean today): names in a plan's "checks" are never checked, and a misspelt one silently switches its check off
**What is wrong:** `body_plan.py check` checks the bones table, legs, spine and chain, but not the bone names and clip
patterns in `checks`. A misspelt name does not fail; it turns the check off: `body_bones` misspelt makes the knee check
compare against no boxes and print "0.0"; `jaw.bone` misspelt reads 0 degrees for ever ("jaw least open 0.0", never
flagged); `hinge.bones`, `feet_parts`, `skip_bones`, `feet_free`, `tuck_ok`, `water`, `dig` and `switches` patterns
that match nothing simply do nothing.
**What the user or player would see:** after a model rename or a typo, a check that reads "fine" for ever.
**Where:** `tools/body_plan.py:267-308` (`problems`, no `checks`), `tools/clip_check.py:416-420`, `:595-603`.
**Proof:** walked; measured: every name and pattern in all 18 animal plans' `checks` exists today.
**Fix proposed:** add the `checks` names (bones must be roles of the model, clip patterns must match a clip, switch
patterns must match on both sides) to `problems`, so `body_plan.py check` and the build fail on them.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: `body_plan.checks_problems` checks every bone name and clip pattern in a plan's `checks` (run by `body_plan.py check`); all 18 plans are clean, and a misspelt jaw bone or clip is named.**

### R12-15 (B, measured): clip_check redoes every clip on every run; the monitor takes about 9 minutes
**What is wrong:** `anim_lab.py` keeps a cache and skips clips unchanged since their last run (2 s for the worker);
clip_check has none, and `verify_animal.py` runs it on every clip and every change each time. Inside a clip the costs
are posing the whole model 80 times a second of clip (and again for the knees with `pl.world`, again for the surface
marks), and `with_edges` rebuilding its list of 12 edges from 28 corner pairs for every box at every sample.
**What the user or player would see:** `verify_animal.py monitor --offline` waits minutes after a one-clip change; the
user's first priority for the engine is making animations fast.
**Where:** `tools/clip_check.py:428-478`, `:369-372`, `tools/verify_animal.py:75-93`.
**Proof:** `clip_check.py monitor '*' --switches`: 535 s (partly beside another run, so somewhat high); crocodile 20 s.
Profile of one monitor clip (`water_drop_850`): 27 s, `place` 3.6 million calls, `with_edges` 215,000 calls (7.5 s).
**Fix proposed:** keep each clip's lines by a hash of its clip data, the model, the plan's checks and clip_check's own
file (as anim_lab does); work out the 12 edge pairs once; pose once per sample and read the knees from it.

### R12-16 (B, proven): pair_report names the lion's strike clips and reads the jaw from another section of the plan
**What is wrong:** a shared tool picks strike moments by the lion family's clip names written into it (`jump_smack`,
`stalk_leap_bite`), a copy of the plan's `strikes.clips`; and it finds the jaws through `checks.carry.bone` (the
otter's carry section) with a default of `carry`, while the holder's jaw is the plan's `holds.jaw`. Both agree today
only because the lioness's jaw happens to be called `carry`.
**What the user or player would see:** the next holder (another cat, a crocodile) gets no strike section, or jaws read
from the wrong bone, in the film's pair report with no warning.
**Where:** `tools/pair_report.py:162`, `:205`.
**Proof:** walked; lioness plan: `strikes.clips` = `stalk_leap_bite`, `jump_smack`; `holds.jaw` = `carry`; no `checks.carry`.
**Fix proposed:** read `strikes.clips` and `holds.jaw` from the holder's plan.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: `pair_report` reads the strike clips from the holder's `strikes.clips` and the jaw from its `holds.jaw`.**

### R12-17 (C, proven): pair_report's "a tick" numbers mix in the in-between time
**What is wrong:** with `record on 1 all` each tick holds one moment drawn at some in-between time, and the place read
is the drawn (in-between) place. The planted-feet slide and the box's move are taken between moments of neighbouring
ticks without dividing by the real time between them (anywhere from near 0 to near 2 ticks). With more than one moment
a tick, moments inside a tick are never compared.
**What the user or player would see:** the "blocks a tick" slide and move figures can be off by up to about two times.
**Where:** `tools/pair_report.py:224-244`; `framework/.../client/BodyRecorder.java:301-305`.
**Proof:** walked.
**Fix proposed:** divide each difference by `(ft2 + pt2) - (ft1 + pt1)`.

### R12-18 (C, proven): the joint-turn check reads the keys as straight lines and skips a loop's seam
**What is wrong:** `fastest_turns` takes each pair of neighbouring keys and divides the change by their time apart. An
eased key (`easeInOutSine` peaks at 1.57 times the average, `step` is instant) and a loop's last key to its first are
never judged. All animal clips are linear today, so only the ants' and termites' eased clips are affected (if
clip_check is ever run on them). It also reads the file's own bone names while the leg list is in role names (equal
today).
**Where:** `tools/clip_check.py:705-716`, `:624-626`.
**Proof:** walked; every animation file grepped: only the Matabele and termite files use an easing.
**Fix proposed:** sample the posed rotations (from `Clip.pose`, every 1/80 s, the seam included) instead of the keys.

### R12-19 (C, proven): small mismatches between what is judged and what is drawn
- The strips draw a part red when it is 0.6 px inside a block, the check lists it from 0.4 (`clip_check.py:1354`
  against `:448`): a listed part may show no red dot.
- The strips colour a planted foot's trail red when it moves 0.3 px in 1/80 s, which on a fast walk is the right
  planted speed (the buffalo's charge 39 px/s is 0.49 px a sample): correct feet drawn red (`:1366`).
- `spots.py stale()`'s comment promises "tables with no plan left"; the code never looks for them (`spots.py:248-260`).
- `spots.py` writes a hold's `loop` only when it `is True`, while the lab also counts `"loop"` and `"true"` as looping
  (`spots.py:177`); every file uses `true` today.
- A spot table for a prey clip with a path would include the path's travel in its rows, on top of the game moving the
  animal along it; no prey clip has a path today (`spots.py:140-147`).
- `gait_changes --write` raises a KeyError if `gaits.order` names a gait with no section of its own (`clip_check.py:860`).

### Solid in this slice (do not "fix")
- The pose maths the checker shares with anim_lab: X then Y then Z turns, X and Z mirrored as Blockbench, a looping
  clip sampled modulo its length (so the seam step is measured), the later key's easing, GeckoLib's linear blend
  into the new clip's first frame (which is why BUGS 249's spin shows in "angles apart" at all).
- The blend is played at real time whatever the clip's speed, matching `SteadyController.adjustTick` since BUGS 372.
- The pair geometry matches the game: `Pair.holder_frame`/`holder_point` and `spots.placement` give the same sums as
  `Spots.world`, `Spots.holderAt` and `holderYaw`; a loop's prey place is its middle row in both; the "together" clock
  is the game's `clipTick() = doingFor() - BLEND` on both animals.
- The spot rays: start inside the skin is enforced, the ray steps 0.05 px over 64 px, a skin bone with a rest turn is
  refused rather than placed wrongly.
- The spot fingerprint hashes the files' JSON contents, not bytes, so a line-ending difference does not fail the build.
- `drawn_ahead` and the blend (when written as a number or a constant) are read from the Java, and a `drawn_ahead`
  pattern that is not found stops the tool instead of guessing.
- `verify_animal.py` treats a crash or a non-zero exit of a tool as a hard failure and searches for "Traceback".
- `body_plan.rewrite_plan` keeps a plan's own file style; `keep_gaits` stops the model makers dropping the gait table.
- `body_plan.check` catches bones named but missing, bones left out, roles not in the table, odd leg keys, legs
  without partners, spine shares that do not add to 1, and plan files not in the index.
- The flat-ground inside check, the knee and elbow check against rest, the wave check, the gait-change moment search,
  and the four-moment worst case for leaving a loop.

---

## Slice: the animation lab and the authoring kit (`anim_lab.py`, `motion_kit.py`, `ant_motion.py`)

### R12-20 (A, proven): the lab's floor sinks with the lowest foot, so feet pushed into the ground are never reported
**What is wrong:** `Gait` puts the floor at `min(lowest foot of the whole clip, foot at rest)`. Whenever a foot goes
below its resting height the floor goes down with it, so the "sinks into the floor" check (BAD past 0.35 units)
measures every part against a floor that has dropped too: that foot is never caught, and nor is any other part above
it. The same moved floor blinds two more checks: a foot counts as "down" only within 0.15 of that lowest foot, so
while one foot is sunk every other planted foot counts as lifted and the standing foot-slide check skips them; and the
film and `match` pictures draw the floor line at the sunk foot, so the picture shows the foot standing on the floor.
**What the user or player would see:** an ant's or termite's leg tip sunk into the ground in the game while
`anim_lab.py check` prints "0 problem(s)". Live today:
- matabele_minor `groom_antenna_l`: the l1 foot 2.05 units under the rest floor (`_r` and `_both` 1.8 to 1.9; the
  major and the queen 1.2);
- termite_major_soldier `paralysed_fall`: the r1 foot 1.27 under (the worker 0.64, the king and queen 0.75);
- matabele_minor `attack_b`: part of the l1 foot box 2.27 under the floor just behind its own "hole" line.
`check` gives no BAD on any of them. A probe that pushes the whole idle ant 3 units into the floor gives no BAD either.
**Where:** `tools/anim_lab.py:691-697` (the floor and "down"), `:1247` (the sink check's floor), `:948` (film's floor).
**Proof:** walked `Gait.__init__`: `low` = the lowest tip, `ground = min(low, rest)`, `down` measured against `low`;
`check` uses `floor = gait.ground`. Runs by the reader on the clips above. Checked again by the main session (lines
691-697, 1247). The game's floor is the entity's own height (model y = 0; the rest foot tip is at -0.13 on the
Matabele and 0 on the termites).
**Fix proposed:** make the floor the game's floor: model y = 0, or the rest foot level when that is lower, never the
lowest foot. Keep the 0.35 margin and the `hole` note's exemption; measure "down" against that floor too (the
paralysed termite on its back still works: its floor stays at rest). Then run `check` on the eight insects and fix
what it finds (grooming and the fall first). Add one test: a clip with every part lowered 1 unit must give BAD.
These are ant and termite clips: a fix that changes how they move is checked with the guard films of those scenes.

**Status (2026-11-26, `claude/review-fix-12`): fixed: the lab's floor is where the feet stand at rest (never under 0), not the lowest a foot gets in the clip (`anim_lab.py`, `Gait`; self-test `lab_body_pushed_into_the_floor_is_flagged`). Walks read as before. It now reports sunk parts in several ant and termite clips: BUGS 426 (not mended: the insects are on hold).**

### R12-21 (B, proven): the loop-seam and snap checks read only rotations; a position or scale jump at the loop passes
**What is wrong:** `check` samples only each bone's `rotation` for the seam ("jumps N degrees where the loop starts
again") and for snaps. A bone that moves (position) or swells (scale) and jumps where a looping clip starts again is
never looked at. The tool's own header promises "loop seams" with no exception. B only because no insect clip has
such a jump today.
**What the user or player would see:** a body or abdomen popping up or down every loop with a clean check. (Outside
the insects the same scan finds the crab's `crack`, `held_shell` jumping 0.875 px at the seam, and the bullfrog's
`dug`, `throat` scale 0.096: R12-2's jump judging is where those belong.)
**Where:** `tools/anim_lab.py:1285-1296`.
**Proof:** `rot = chans.get('rotation'); if not rot: continue`. Probe: the idle clip with the body moved from 0 to 2.5
units between t=0 and the loop's end gives no BAD.
**Fix proposed:** run the same seam test over `position` (BAD over about 0.3 units) and `scale` (over about 0.03), and
add position to the snap note.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: a looping clip's seam is judged for position (over 0.3 units) and scale (over 0.03) too; no insect clip has such a jump today.**

### R12-22 (B, proven): nothing measures that the offline pose is the game's pose; `match` only puts pictures side by side
(Merged: the lab reader for the insects' `match`, the thread reader for the animals, who have no match at all.)
**What is wrong:** the engine's rule 1 says a rule that also lives in Python gets a match check proving it gives the
game's result. For the insects, `anim_lab.py match` (whose docstring says it proves "the lab and the game agree")
pastes the middle half of each game screenshot above a lab drawing of the same clip moment and leaves the judging to
the eye. It reads only the first insect on each `times.txt` line; it ignores the live layer's waist and spine bends
that `FilmDirector.noteRendered` writes on the same line for exactly this purpose; it draws the floor at the lowest
foot (R12-20); and the game's time is `ticks % length` even for clips played once. For the animals there is nothing:
`clip_check.py`'s whole pose engine (keyframes, signs and order, pivots, `HingedModel`, the tail drag, the blend) is
never compared with the game's own record, although `BodyRecorder` writes every bone's drawn matrix and the clip time
each controller is at.
**What the user or player would see:** nothing directly; offline findings Claude trusts can differ from the game with no
warning. The drawn-only layers of R12-70 and R12-71, R12-9's reset and R12-12's tail are exactly what such a check
would have caught.
**Where:** `tools/anim_lab.py:1341-1377`; `framework/.../client/FilmDirector.java:1022-1057`;
`framework/.../client/BodyRecorder.java:300-351`.
**Proof:** read: `match` holds no numeric comparison, `w[3:]` (drawn tick, waist, spine) is never read; the only readers
of `body.jsonl.gz` (`body_course.py`, `pair_report.py`) never call the lab's posing.
**Fix proposed:** `clip_check.py --match <recording>`: for each recorded moment whose body controller is RUNNING a clip
(not blending), pose that clip at the recorded clip time, place it with the recorded position and yaw, compare every
box corner with the recorded matrices, print the worst bone and FLAG over 0.2 px. Run it in `verify_animal.py` on the
frame films it already makes (no new launch). For the insects, make `match` measure the same way (with the recorded
waist and spine bends), or rename it to what it is and stop calling it proof.

### R12-23 (B, proven): the lab's sizes and speeds are its own hand-typed tables, not the game's
**What is wrong:** `RENDER_SCALE` and `WALK_SPEED` are species tables typed into the shared lab (shared pieces never
name a species). They copy only the renderer's `withScale`; since real sizes (2026-11-05) each insect is also drawn at
its real-size scale (`InsectSizes`, `applyBodySize`), and the real-sizes plan left the lab untouched. So every "blocks"
number the check prints is off by 2 to 3 times: the lab makes the termite worker 0.442 blocks long against 0.15 in
the game, the Matabele major 1.143 against 0.5. That covers stride in blocks, body lengths a second, cycles a second
and play speed. The game caps a clip at 20 playbacks (the termite worker already runs at that cap); the lab never
checks it. `worker_ant` and `queen_ant` are leftovers of the harvester taken out on 2026-11-23.
**What the user or player would see:** Claude judging a walk's step rate (strictly real for insects) from note lines
that are wrong, for example "plays 3 cycles a second" when the game plays about 7. No BAD flips; these are notes.
**Where:** `tools/anim_lab.py:93-109`, `:1144-1149`, `:1207-1214`; `docs/plans/real-sizes.md:49`.
**Proof:** a run of `lab.Model(n).length * blocks_per_unit`: termite_worker 0.442, matabele_major 1.143,
matabele_minor 0.776, termite_major_soldier 0.746 blocks, against the real-sizes table.
**Fix proposed:** read each insect's drawn scale and walking speed from data the game also reads (its body plan, or a
table made from `InsectSizes` and the renderer, as clip_check reads the blend from the Java). Delete the two tables and
the harvester rows. Add a note, or a BAD, when the play speed needed is over the game's cap of 20.

### R12-24 (B, proven): a standing clip's sliding foot is only a note, so the summary says "0 problem(s)"
**What is wrong:** for clips that are not walks or turns, a planted foot moving along the ground faster than 1.5 units
a second gives a NOTE only. `bad` counts BAD lines only, so `check` exits 0 and prints "0 problem(s)" for an idle whose
foot skates.
**What the user or player would see:** a foot skating in an idle, look or dig clip, passed as clean.
**Where:** `tools/anim_lab.py:1238-1245`.
**Proof:** read: `out.append(('NOTE', ...moves along the ground while standing...))`; `run_clips` counts `level == 'BAD'`.
**Fix proposed:** BAD above a limit (for example the walk's 35% slip as units a second, or over 0.5 units of travel
while down), NOTE below it. Do it after R12-20, since today's "down" is measured against the wrong floor.

**Status (2026-11-26, `claude/review-fix-b6`): open: tried 2026-11-26 as a fault over half a unit of slide and taken back: it flagged the approved grooming, clamp, head bang and falling clips, whose working or falling feet read as "down". The note now says how far the foot went in all. It needs a way for a clip to name the feet it works with first.**

### R12-25 (B, proven): `two_bone` gives wrong pieces (and says "no miss") when the target is close, and crashes at the root
**What is wrong:** when the target is nearer the root than `|l1 - l2|`, the code raises the distance to the minimum but
keeps dividing the old, shorter one by it. The "unit" direction then is not unit, the joint lands in the wrong place,
both pieces come out shorter than their lengths, and `miss` stays 0. A target exactly at the root with equal pieces
divides by zero. `limb` and `digitigrade_leg` inherit it.
**What the user or player would see:** a tightly folded leg (resting, swimming, death, held clips) posed with the wrong
bone directions while the maker's miss says it reached; slower animation making when the clip check then shows it.
**Where:** `tools/motion_kit.py:94-107`.
**Proof:** run: l1=3, l2=4, target 0.5 below gives the thigh 1.586 and the shin 2.049 (should be 3 and 4), miss 0.0;
l1=l2=4 with the target at the root gives ZeroDivisionError.
**Fix proposed:** when the target is too close, push it out along the direction (or straight down if there is none) to
the minimum distance, set `target` and `miss` from that as the too-far branch does, then work out the rest from the new
distance. Add both cases to the self-check.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: an end nearer than the pieces fold to goes out to the nearest place they reach, with the miss saying by how much; an end at the root no longer divides by zero. Both cases are in the kit's self-check. Clips were not made again: a maker run again changes only poses that were wrong.**

### R12-26 (B, proven): a pole along the leg's line crashes `two_bone` and flips the knee 180 degrees as the target crosses it
**What is wrong:** `bend_way` takes the pole's part square to the root-target line. When the target lies on the pole's
line, that part is zero: `unit()` divides by zero and the maker crashes. Near that line, the knee jumps to the other
side as the target crosses it. The docstring promises a joint that "always bends the way it is told".
**What the user or player would see:** a maker crash, or a knee or elbow snapping through in one frame when a foot
passes the pole's line (a leg stretched straight forward or back in a pounce or a push-off). Rare with today's poles.
**Where:** `tools/motion_kit.py:82-86`, `:106`; `tools/model_parts.py:407-409`.
**Proof:** run: pole (0, 0, -1), target (0, y, -6): for y = 0.05 or 0.01 the knee is at y -2.6; for -0.01 or -0.05 at
+2.6. A pole along the line raises ZeroDivisionError.
**Fix proposed:** when the pole's square part is short (under about 0.2 of its length), blend toward a fallback way
(the last frame's, or a second pole the maker gives) instead of normalising a near-zero vector; return a flag so the
maker can say which frames were near it.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: when the limb's line runs along the pole (its square part under a fifth of it), the bend eases over to a second way square to the line, so nothing divides by zero and the joint does not flip sides; in the self-check. No flag is returned to the maker.**

### R12-27 (B, proven): the kit returns a miss but every newer maker throws it away
**What is wrong:** `two_bone` and `digitigrade_leg` say how far a foot fell short of its target, the cheapest early
warning that a pose asks a leg for more than it has. The buffalo, hippo, lion and tortoise makers all bind it to `_`.
Only the otter keeps it, and only the monitor has a per-clip "worst" report (its own `_Poser`). The kit gives no shared
way to collect it.
**What the user or player would see:** a hoof or paw silently left short of the ground, found only later by the clip
check or a film: slower animation making.
**Where:** `buffalo_anims.py:162`, `hippo_models.py:636`, `lion_anims.py:183, 223`, `leopard_tortoise_models.py:1056`;
`tools/motion_kit.py:89-154`.
**Proof:** grep of the maker calls listed.
**Fix proposed:** a small collector in the kit (`Misses()` with `.add(clip, foot, t, miss)` and a printed worst per clip
over a limit), printed by the shared bake step, as the monitor's `worst` does.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: the kit tallies every limb miss over 0.05 units itself and says the worst when the maker ends, by clip where the maker calls `clip_now(name)` (no maker calls it yet: one line for the run).**

### R12-28 (B, proven): the kit's clip cache leaves out the data files a maker reads, so a changed outline keeps old clips
**What is wrong:** `fingerprint` hashes the kit and `model_parts.py` plus the files the maker passes in. The monitor
passes only `__file__`, yet its climb and water clips are built from `references/outlines/monitor.json` (read at
import, used by `_trunk`). Change the outline and `cached` returns the old climb and water clips, which the maker
writes back into the game file. The same goes for any helper module a maker imports.
**What the user or player would see:** after an outline change, the monitor's climbs still move for the old body. The
clip check reads the game files and would show the mismatch: a wasted round of puzzling rather than a hidden fault.
**Where:** `tools/motion_kit.py:329-353`; `tools/monitor_models.py:34-40`, `:1812`, `:1818`.
**Proof:** walked the import-time read of the outline and the two `cached(...)` calls with `files=[__file__]`.
**Fix proposed:** have `fingerprint` hash every module from `tools/` in `sys.modules` (the maker and all it imports),
and let makers name their data files once (`DATA_FILES = [...]`).

**Status (2026-11-26, `claude/review-fix-b6`): fixed: the clip cache's fingerprint takes every tools module loaded (the maker and all it imports), and the monitor names its outline file.**

### R12-29 (C, measured): any edit to `termite_models.py` or `body_plan.py` reruns every insect's lab check
**What is wrong:** the lab's cache key includes the whole of `termite_models.py` (used here only for its font and
canvas) and the whole of `body_plan.py`. Any edit to the termites' maker reruns every clip of every insect.
**What the user or player would see:** about 25 seconds instead of under a second per insect after an unrelated edit.
**Where:** `tools/anim_lab.py:1484-1485`.
**Proof:** measured `check matabele_minor --fresh`: 49 clips in 25.5 s on 10 processes, against 0.1 s remembered.
**Fix proposed:** move the font and canvas into a small module of their own and hash that. Changed clips are still
never skipped: the clip's own JSON is in its key (`:1498`).

### R12-30 (C, to measure): the kit's cache is used by the monitor only; the newer makers rebuild every clip every run
**What is wrong:** the lion (20 clips times 3 pride members), buffalo, hippo and otter never call `cached`, so every run
rebuilds every clip. `fingerprint(..., default=repr)` would also never hit for a part holding an object (its repr
carries a memory address).
**What the user or player would see:** slower clip making for the big animals (not measured: running a maker writes
game files, which a reading round may not do).
**Where:** `tools/motion_kit.py:329-353`; the makers.
**Proof:** grep: `cached(` appears only in `monitor_models.py`.
**Fix proposed:** time one full maker run; if it is over a few seconds, wrap each clip in `cached` (after R12-28).

### R12-31 (C, proven): `quadruped_gait` lets the feet step at different ground speeds without a word
**What is wrong:** with a duty or stride given per foot, a planted foot moves at `stride[k] / duty[k]`, so unequal
ratios make planted feet slide against each other; `speed_px_s` averages them. Every call passes single numbers today.
**Where:** `tools/motion_kit.py:158-196`.
**Proof:** read; the buffalo and lion calls pass single numbers.
**Fix proposed:** in per-foot mode, warn (or raise) when `stride[k] / duty[k]` differ by more than 2%.

### R12-32 (B, proven): whether a clip loops is decided twice, by the Java and by the file, and the tools read only the file
(Merged: the lab reader for the insects, the thread reader for the animals.)
**What is wrong:** GeckoLib plays a clip with the loop type the Java asks for (`thenLoop`, `thenPlay`,
`thenPlayAndHold`), the file's `loop` only when the Java asks for its default. The lab (`Clip.loop`, the seam check and
how time wraps) and the checker (switch phases, a loop left at four moments, the pair check's prey) read the file's
flag only. The insects agree today (`AbstractAntEntity`); four animal clips do not: `BarbEntity` and `TadpoleEntity`
play `scatter` with `thenLoop` and `thenPlay` (the file says once); `BullfrogEntity` plays `call` once (the file says
loop); `CrabEntity` plays `crack` and holds it (the file says loop).
**What the user or player would see:** today little (the barb's `scatter` looped has a seam step of 2.7 px against 5.4
px of its own last tick, no pop; the tadpole's 0.0). A new clip played the other way from its file would be checked as
the wrong kind.
**Where:** `tools/anim_lab.py:569`; `tools/clip_check.py:746, 767`; `framework/.../entity/AbstractAntEntity.java:79-133`;
`BarbEntity.java:323-324`, `TadpoleEntity.java:342-343`, the bullfrog's and crab's `RawAnimation`s.
**Proof:** a scan of every `then(Loop|Play|PlayAndHold)("name")` in the entities against the files' `loop`.
**Fix proposed:** one source: the Java plays each clip by the file's own loop type (through one helper), and a clip that
must play both ways becomes two clips; or a unit test that compares the Java's loop types with the files.

### R12-33 (C, proven): the lab's catmull-rom curve is not GeckoLib 4.9.3's
(Also found by the thread reader.)
**What is wrong:** `anim_lab.sample_channel` draws "catmullrom" keys as a textbook spline through the two neighbour
keys. GeckoLib 4.9.3 uses control points it stores in each key (`easingArgs`, filled by `addSplineArgs` at load) and,
without them, an ease of its own over a straight line. Whether the two give the same numbers was not proven, nor how
GeckoLib reads separate `pre` and `post` values. No clip uses catmull-rom or pre/post today (only linear and
`easeInOutSine`); the earlier engine review suggested moving to catmull-rom to cut file sizes.
**Where:** `tools/anim_lab.py:514-563`; GeckoLib `EasingType$CatmullRomEasing`, `BakedAnimationsAdapter.addSplineArgs`.
**Fix proposed:** before any maker writes catmull-rom keys, make the lab do what GeckoLib's loader does and compare one
test clip in the game (R12-22); or keep baking curves into straight keys, as the makers do now.

### R12-34 (C, proven): small tidy-ups
- `motion_kit` self-check: no too-close target and no pole along the line (R12-25 and R12-26 slip through). The
  `secondary_lag` loop test allows a 3.0 gap on a wave of size 10. The header's list misses `digitigrade_leg`,
  `quadruped_gait` and `secondary_lag`.
- `ant_motion.Leg` uses the rest corners and pivots without the bone's rest rotation: right only while leg bones have
  no `rotation` in the geo (true for all eight insects today, unguarded). Add an assert in `Leg.__init__`.
- `anim_lab.main`: `a[2:] in FLAGS` treats a word such as a clip named `xxfresh` as the `--fresh` flag. Test
  `a == '--fresh'`.

### Solid in this slice (do not "fix")
- The lab's bone maths matches GeckoLib's order and signs: per bone the offset, then the pivot, a turn X first then Y
  then Z with X and Z reversed, scale, then back; the rest rotation added axis by axis as GeckoLib adds the start pose.
  `ant_motion.self_test` proves `file_turn` and `to_file` against `lab.turn` and `foot_tips`.
- Key-frame rules: held before the first key and after the last, easing taken from the later key, `easeInOutSine` and
  `linear` as GeckoLib's. Unknown easings and unknown Molang raise an error instead of being skipped.
- The `check`/`all` cache never skips a changed clip (its key holds the clip's own JSON, the geo, texture and body plan,
  the lab's code and the speed); pictures are redrawn when their size or time stamp changes; the cache file is written
  atomically after a merge; `--fresh` reruns everything.
- `limb` parallel and with end directions keep piece lengths exactly; `follow_path` keeps every piece at its length;
  `spline` never overshoots; `stride`, `foot_path` and `gait()` are right (the self-tests pin the tripod tables,
  spider, walk, trot, lost legs and centipede). `secondary_lag` sub-steps to stay stable at any sample rate.
- `ant_motion.walk` and `turn`: one warm-up cycle, then the seam closed exactly. `Track.simplify` drops only keys a
  straight line already gives, as GeckoLib's linear play does.
- The lab and clip_check read only the game's files, and every animal's checker builds on the lab's `Clip` and
  `sample_channel`, so a fix there reaches every animal. Keep that one place.

---

## Slice: the in-game films and the one command (`verify_animal.py`, `body_course.py`, `guard_films.py`, the film Java)

### R12-35 (A, proven): verify_animal says "passed" for an animal it never filmed in the game
**What is wrong:** steps 3 to 5 each return "ok" when there is nothing to do: no course entry ("no course entry in its
plan"), no frame film ("no frame film"), no approved version ("no approved version named"). None of them is a failure,
so an animal with none of the three goes through a whole game launch and comes out "passed". The launch itself is not
empty either: `-PfilmScene=` with nothing after it is an empty string, which Groovy reads as false, so `build.gradle`
falls back to `tools/film/poses.film` and films the insects' poses scene instead.
**What the user or player would see:** `python tools/verify_animal.py lioness` (or `lion`, `lion_cub`: the Motion
Studio's animals today, whose plans have no `course` and no `verify.films`) prints `lioness: passed`. The hard rule
("every animation checked offline and in the game") looks met, but no picture of a lioness was taken.
**Where:** `tools/verify_animal.py:195-198`, `229-231`, `252-255`, `310-317`, `321`; `build.gradle:205`.
**Proof:** walked: `launch([])` runs `runFilm -PfilmScene=`; `build.gradle:205` takes the fallback for `''`; each
`check_*` returns a text, `step` writes `ok`, the verdict is `passed`. Checked again by the main session, with the
plans read: lion (only `verify.models`), lioness and lion_cub have no course and no frame film.
**Fix proposed:** an animal "passes" the game part only if at least one in-game scene of it was filmed (course or frame
film); otherwise say `passed offline only (no game film: add a course entry or <animal>_*_frames.film)` and return
non-zero. Never launch with an empty scene list.

**Status (2026-11-26, `claude/review-fix-12`): fixed: an animal with no course scene and no frame film ends `NOT PASSED: ... NO GAME FILM OF ITS MOVES` and the command returns 2; the game is never launched with no scene (`launch` refuses; self-test `verify_stops_at_a_crash_and_never_launches_nothing`). The lion, lioness and cub get that verdict today, which is true: they have no course entry and no frame film named in their plans.**

### R12-36 (A, proven): a game crash during the films is not a hard failure
**What is wrong:** `launch()` returns the game's exit code, but `main` only prints "(runFilm ended with exit N ...)"
after the checks. A frame film counts as filmed when its folder holds at least one picture. So a game that crashed
half way through a frame film (pictures 1 to 40 of 120 written) passes the films step, and the compare step passes if
its film was made before the crash. A crash also leaves no `checks.txt`, reported as `game checks: no checks`, "ok".
**What the user or player would see:** "passed" with a short film; the crash shows only as one line in the middle of
the output. The docstring promises the opposite ("stopping an animal at its first hard failure (... a crash ...)").
**Where:** `tools/verify_animal.py:182-192`, `236-238`, `313-319`.
**Proof:** walked; checked again by the main session: `code = launch(...)`, then the three steps run whatever `code` is.
**Fix proposed:** a non-zero exit is a hard failure of the `course` step before anything is read, and each frame film
must have as many pictures as its shot list asks for (counted from the `.film` file).

**Status (2026-11-26, `claude/review-fix-12`): fixed: a game that ended with an error, or whose camera never finished its shot lists, is a hard failure of a new `game` step before anything is read; a frame film must hold every picture its shot list asks for (bursts times frames a tick times cameras; self-test `verify_film_cut_short_is_a_hard_failure`); a film with no `checks.txt` says so in capitals.**

### R12-37 (A, proven): a course scene with no recording, or almost none, reads as "nothing found"
**What is wrong:** `check_scene` returns `"<scene>: no recording"` or `"only N moments recorded"` with an empty findings
list. Those lines go into `course_<animal>.txt` only; `verify_animal.check_course` prints `0 findings, worst: nothing
found` and "ok", because its only guard is that the scene's folder exists, and the folder exists as soon as one picture
is saved. `body_course.py run` is worse: it ignores the game's exit code (`check=False`) and silently skips any scene
folder that is missing.
**What the user or player would see:** a course "with nothing found" for a scene where the walker was never summoned (a
bad `data` tag in the plan's course entry fails with no error the tools see), was never drawn (`BodyRecorder` records
only while the animal is in view), or a typo in a shot list (logged as "unknown step" and skipped). The worse the
failure, the cleaner the report.
**Where:** `tools/body_course.py:675-680`, `801-808`, `811-814`; `tools/verify_animal.py:201-216`;
`client/BodyRecorder.java:142-151`; `client/FilmDirector.java:598`.
**Proof:** walked; checked again by the main session (`body_course.py:675-680`, `verify_animal.py:212-214`).
**Fix proposed:** "no recording" or "under N moments" is a finding of the worst size (a `Hard` in verify), printed
first; `body_course run` fails on a non-zero exit or any missing folder.

**Status (2026-11-26, `claude/review-fix-12`): fixed: no recording, a recording that cannot be read and one of under 5 moments are the worst finding (`body_course.NOT_RUN`), on top of the report and a hard failure in `verify_animal.py`; `body_course.py run` clears a scene's old recording first, fails on the game's exit code, a camera that never finished or a missing scene, and returns 2 (self-test `course_scene_with_no_recording_is_the_worst_finding`).**

### R12-38 (A, proven): nothing checks that the animal did what the scene asked, and the game's own warnings are never read
**What is wrong:** the course sends the walker to places (`go x y z`), but `analyse` never looks at whether it got
there, how far it went, or whether it moved at all. A walker that found no route (`Film: walker to ...: NO ROUTE`), a
scene whose `until` was never met, a stage that does not exist, a missing walker, an unknown step: all are only
warnings in `run-film/logs/latest.log`. `verify_animal.py` and `body_course.py` never open that log (`guard_films.py`
reads two of the seven warnings).
**What the user or player would see:** a step-up scene where the monitor stood still at the foot of the step has no
parts in blocks, no pops, no sliding feet: "nothing found". The scene that should catch climbing faults passes because
there was no climbing.
**Where:** `tools/body_course.py:373-580`; `tools/verify_animal.py`; `client/FilmDirector.java:351-356`, `549-551`,
`598`, `650`, `856`; `client/FilmStep.java:231`.
**Proof:** read every check in `analyse` (inside, seams, bends, pops, feet, clips): none uses the scene's places.
Grepped the tools for `latest.log`: only `guard_films.py:211`.
**Fix proposed:** per scene, compare the last recorded place with the last `go` target (within its width) and the
distance walked with the straight line; a scene that does not arrive is a finding at the top. One shared log reader
(in `body_course.py`, used by all three tools) that turns every `Film:` warning of the launch into a finding of its scene.

**Status (2026-11-26, `claude/review-fix-12`): fixed: each scene's report says how far from the last place it was sent the animal ended; over 2.5 blocks is a finding on top (`THE ANIMAL NEVER GOT WHERE IT WAS SENT`) and the verdict is `NOT PASSED` (not a hard failure: what it did walk is still judged). One reader of the camera's warnings (`body_course.camera_warnings`: every WARN or ERROR line and NO ROUTE, by scene) for the course (hard), the frame films (`NOT PASSED`) and the guard films (self-tests `course_animal_that_stood_at_the_foot_of_the_step_is_flagged`, `camera_warnings_are_read_by_scene`). Not done: the distance walked against the straight line. Run on the 48 recordings on disk it found 15 that end short: BUGS 429.**

### R12-39 (B, proven): the guard films' headline says "ALL CHECKS PASS" with every picture changed
**What is wrong:** `compare()` only appends to `notes`, never to `problems`. Every picture changed, every approved
picture missing ("missing (was approved)"), or a film half as long: the report still starts `Guard films: ALL CHECKS
PASS` and `run`/`check` exit 0. The tool's header says changed pictures are "listed, to look at", so this is how it was
written (graded B, not A, for that), but the headline and the exit code are what a reader or a script sees first.
**What the user or player would see:** a body change that moves every insect's legs passes the guard run unless the
notes are read by eye.
**Where:** `tools/guard_films.py:14-15` (header), `316-343`, `467`, `474`, `498-500`.
**Proof:** walked `compare`; checked again by the main session.
**Fix proposed:** keep the list, but count changed and missing pictures in the headline ("N pictures changed: look,
then approve") and return non-zero, so `approve` is the way past a change on purpose.

**Status (2026-11-26, `claude/review-fix-b6`): half fixed: approved pictures not filmed again are a problem now (a film cut short), and the headline counts the changed pictures ("N PICTURE(S) CHANGED in M scene(s): look, then approve"). Left: changed pictures alone still exit 0, as the tool was written.**

### R12-40 (A, proven): a finding at an approved picture is let through however much worse it is
**What is wrong:** in `film_checks`, when every picture where a part is flagged is a picture already in the approved
findings, the loop `continue`s before the "worse" test. The films are the same moment for moment run to run, so a
regression usually shows exactly at the approved pictures: an abdomen approved 0.015 deep at `up_12` that is now 0.30
deep at `up_12` is skipped.
**What the user or player would see:** a part sinking deep into a block in a guard film the user approved with a tiny
overlap passes as "approved".
**Where:** `tools/guard_films.py:255-268`.
**Proof:** walked; checked again by the main session: `continue` at line 261 comes before the `WORSE_FINDING` test at 267.
**Fix proposed:** keep the "same moment" shortcut only for the count; always run the "worse" size test (store each
approved moment's size, not only the part's worst).

**Status (2026-11-26, `claude/review-fix-12`): fixed: a finding at an approved picture is still judged by its size (only how often it shows is left out for the same moments), and another part over the line at an approved picture is judged against what was approved at that picture (self-test `guard_part_much_deeper_at_an_approved_picture_is_flagged`). No guard film run (no insect body changed).**

### R12-41 (A, proven): the camera's jump check cannot see a pop that repeats
(Merged: the films reader, and the second half of the thread reader's "the film's jump check is weaker".)
**What is wrong:** a jump must be over 1.5 times the part's "big move" of the whole scene: the move 9 in 10 of its moves
are under, or, for a track under about 20 pictures, its second biggest. A pop that comes back every cycle (a loop's
seam, a clip change at every stride) is more than 1 in 10 of the moves once the cycle is under 10 pictures, and in a
short burst two pops make the second biggest move a pop: then no pop is flagged. The local test (3 times the moves
round it) would catch them; the scene-wide one cancels it.
**What the user or player would see:** a clip that pops at its loop point, filmed a few ticks a picture, gives
`0 jump(s)` in `checks.txt`, and the verify summary repeats that. (The course's own pop check, local only, is not blind
to this; the camera's check, which the rule book names for frame films, is.)
**Where:** `client/FilmChecks.java:56-59`, `242-245`, `272-282`.
**Proof:** read `bigMoves`: `sorted[Math.min(sorted.length - 2, (int)(sorted.length * 0.9))]`; with two equal pops in
a 12-picture track the bar is the pop. Checked again by the main session.
**Fix proposed:** judge "big" from the same part's moves in other bursts, or from moves one cycle away; simplest: drop
the scene-wide test for moves more than 6 times the local middle.

**Status (2026-11-26, `claude/review-fix-12`): fixed the review's way: a move over 6 times the middle of its neighbours is a jump whatever the part's big moves in the scene are (`FilmChecks.SURE_TIMES`, `FilmChecks.jump`; `FilmChecksTest`: a pop every five pictures, a part out and back in a take of 12, a quick swing still no jump). One more number, the review's pick: to watch for false alarms in the next frame films. **Seen in the first frame film since (the otter's moves, 2026-11-26):** the new rule also flags the first stepped picture of every take that starts mid-walk (the body moves about 0.2 blocks in that tick, the game catching up at the freeze; the old rule let it by). True of the film, not a fault of the animal: a few lines of noise a take, left as it is for now; the guard films' next run will show them as new findings to look at once.**

### R12-42 (A, proven): the watch mode films old spot tables and old body plans after they change
**What is wrong:** `tools/film.sh watch` copies a changed file into the build and reloads: for `animations/` and `geo/`
GeckoLib, for `block_moves/` `BlockMoves.reloadAll()`, for everything else Minecraft's full resource reload. But the
spot tables (read by `Spots.of` into the static `LOADED` map) and the body plans (read once by `BodyPlans.signUp` into
`PLANS`, with the gait table and the gait-change moments the game plays) are read with `getResourceAsStream` and kept
for ever; the full reload does not touch them.
**What the user or player would see:** after `tools/spots.py` rewrites a table or `clip_check.py --gaits` writes new
change moments, the watch session reshoots (after a slow full reload), reports "Pictures ready", and films the old
holds and the old gait changes. The Motion Studio's phase 3 is exactly this loop.
**Where:** `client/FilmWatch.java:282-292`; `entity/body/Spots.java:32`, `163-165`; `entity/body/BodyPlans.java:103`,
`116-118`.
**Proof:** grepped: no `LOADED.clear` or `PLANS.clear` anywhere; `signUp` returns at once for a folder already read.
Checked again by the main session.
**Fix proposed:** a `reloadAll()` on `Spots` and `BodyPlans` (as `BlockMoves` has), called by `FilmWatch` for
`spots/` and `body_plans/` (and those two never trigger the full reload).

**Status (2026-11-26, `claude/review-fix-12`): fixed: `Spots.reloadAll()` (each table read whole and swapped into the same object) and `BodyPlans.reloadAll()` (a counter tells the two drawers that keep a plan to ask again), called by `FilmWatch` for `spots/` and `body_plans/` with a GeckoLib reload, not the full one. An animal keeps the gaits it was made with (the camera summons its animals new every shoot). Not tried in a watch session.**

### R12-43 (B, proven): "frames N" does nothing for animals: N identical pictures a tick
**What is wrong:** only `AntRenderer` draws at the camera's in-between time (`FilmDirector.drawPartial()`); every
animal is drawn at Minecraft's own, and a frozen game draws every frozen entity at 1.0 (`DeltaTracker`). BUGS 381's own
fix note confirms "all 1,020 pictures of the gait film drawn at 1.0". So `frames 4` gives four copies of the same
moment for an animal, and `FilmChecks` then multiplies its per-tick moves by 4.
**What the user or player would see:** `otter_bound_frames`, `otter_dance_frames`, `otter_zig_frames` ("four pictures
a tick") and `bullfrog_tongue` (`frames 2`) show no in-between moments, only repeats, so a pop between ticks is never
seen; the contact sheets look smooth because they repeat.
**Where:** `client/FilmDirector.java:236-238`, `372`, `376`, `392-394`; `client/AntRenderer.java:136-146`; Minecraft
`DeltaTracker.java:114-116`, `LevelRenderer.java:1040`.
**Proof:** grepped `drawPartial` in `savanna/src`: no animal renderer uses it; read the two Minecraft sources.
**Fix proposed:** draw every GeckoLib animal at the camera's time in films too (one shared hook in the framework's
renderer path); until then, refuse `frames N > 1` in a scene without insects, with a warning.

### R12-44 (B, proven): the camera's jump check measures "per tick" wrongly in three cases
(Merged: the films reader, and the first half of the thread reader's "the film's jump check is weaker".)
**What is wrong:** (1) the moves are scaled by one `framesPerTick` per scene, the value left at the scene's end, so
scenes that mix `frames 2` or `3` with `frames 1` judge their multi-frame bursts at half or a third of their size. (2) A
move between pictures several ticks apart is not divided by the ticks: a pop of size P on a usual move v a tick is
flagged when P > 2v at one tick a picture, but P > 6v at three and P > 8v at four (the hard rule's films step "a few
ticks a picture"). (3) Pictures more than 8 ticks apart are never compared (`TAKE_GAP`), so the six bursts that step
10 ticks a picture have no jump check at all, and still report `0 jump(s)`.
**What the user or player would see:** fewer jumps flagged than are there; nothing says the check was off.
**Where:** `client/FilmChecks.java:61-64`, `237-241`; `client/FilmDirector.java:294`, `378-400`.
**Proof:** read; grepped the scenes for `frames` and for burst gaps.
**Fix proposed:** keep each sample's real in-between time and tick, size each move by the time between its two
samples; write "jump check off: pictures N ticks apart" into `checks.txt` when a burst is too sparse.

### R12-45 (B, proven): a fish held in a mouth is checked and recorded at the wrong place
**What is wrong:** the camera's checks and the recorder read GeckoLib's "local space" matrices and add the animal's own
position. GeckoLib makes that matrix relative to the pose the renderer had when it started drawing that animal. A held
fish is drawn by its holder's renderer (catfish holding a tilapia, bull shark holding a catfish, otter holding a fish),
from inside the holder's jaw pose, so its local space leaves out the whole jaw transform; the checks then place an
upright fish at the fish's own position.
**What the user or player would see:** "inside" and "jump" findings for held fish that are not there, and real ones
missed; `record on 1 all` writes held-fish bones at the wrong place for `pair_report.py`.
**Where:** `client/FilmChecks.java:106-116`, `134`; `client/BodyRecorder.java:301`, `341`;
`CatfishRenderer.java:101-122`, `BullSharkRenderer.java:75-91`, `OtterRenderer.java:73-115`; GeckoLib 4.9.3
`GeoEntityRenderer.preRender` and `renderRecursively` (bytecode).
**Proof:** read the bytecode: `entityRenderTranslations = new Matrix4f(poseStack.last().pose())` at the start of
`preRender`; local = that pose's inverse times the bone's.
**Fix proposed:** build the world-space matrix for these checks (the frame's camera-relative pose plus the camera's
place), or have a holder's renderer tell the film code the held animal's root pose; skip held animals until then, with
a note.

### R12-46 (B, proven absence, likely effects): two films at once, an open watch session, and no timeout
**What is wrong:** nothing stops a second `runFilm` while another is running or a watch session is open: the three
film tools never look at `run-film/film-watch/status.txt` or a lock. The second launch deletes `run-film/saves/film` and
`run-film/screenshots` under the running game (on Windows the open world's files are locked, so the delete half fails
without an error) and then makes a world of the same name, which (likely) fails on the world lock and leaves the
second game at an error screen. No tool gives `subprocess.run` a timeout, and `FilmDirector.onTick` does nothing while
any screen is open, so such a stall hangs the one command for good. A watch session has no idle limit either.
**What the user or player would see:** a verify or guard run that never ends, pictures vanishing from an open watch
session, the PC slowed for hours. The memory rule "films one at a time" is kept only by hand.
**Where:** `build.gradle:297-309`; `tools/verify_animal.py:188`; `tools/guard_films.py:87`; `tools/body_course.py:801`;
`client/FilmDirector.java:320-322`; `client/FilmWatch.java`.
**Proof:** grepped the three tools for `film-watch`, `status.txt`, `timeout`: none. The lock failure was not run (likely).
**Fix proposed:** one small shared film lock in `tools/` (a lock file with the game's pid) that every film tool takes
and checks; a timeout from the scene count (about two minutes a scene) after which the tool stops the game and fails;
the watch session closes itself after about 30 minutes with no request or change.

### R12-47 (B, to measure): the frame watch runs in every player's game and samples the drawing thread
**What is wrong:** `FrameWatch` is always on, also in the released mod: a helper thread wakes every 5 ms for the whole
session, and during any frame over 20 ms it takes the drawing thread's whole stack every 5 ms (taking another thread's
stack pauses it), keeping up to 400 stacks; each frame over 100 ms writes a warning with up to 30 stack lines.
**What the user or player would see:** on a slow PC slow frames get a little slower and the log fills with `[frame]`
warnings during chunk loading. Probably small, but it is measuring code paid for by every player.
**Where:** `client/FrameWatch.java:24-25`, `65-98`, `156-175`.
**Proof:** read; no guard for the released game. The cost of `getStackTrace` on the drawing thread is not measured.
**Fix proposed:** measure it once; keep the frame count (free) on, the stack sampling behind a config switch or for
development only.

### R12-48 (B, proven): the recorder writes the blocks only round where each animal was first drawn
**What is wrong:** the recording's blocks are those 16 blocks round, 5 below and 10 above the animal's first recorded
spot. With `record on 1 all` (the hunt films) an animal first drawn 20 or 40 blocks from the kill has no blocks written
where the hold and the feeding happen; `pair_report.py` then has no ground under it and its feet-while-holding line
simply does not appear.
**What the user or player would see:** a hunt report with no foot-sliding line for the lionesses that came from far,
read as "nothing to say".
**Where:** `client/BodyRecorder.java:64-65`, `245-272`; `tools/pair_report.py:63-68`, `224-240`.
**Proof:** read both; whether a given hunt run lost its lines was not checked.
**Fix proposed:** write the scene's blocks once per scene folder (a fixed box round the stage, or every block a
recorded animal came near), not per animal at its first frame; have the report say "no ground known here".

### R12-49 (B, proven): insects in a running scene are drawn at a stale in-between time
**What is wrong:** while filming, `AntRenderer` always draws insects at `FilmDirector.drawPartial()`. Outside bursts that
value is set to 0 only on a tick with no wait and is not touched during `wait`, `until` or a running game; after a burst
it keeps the burst's last value. So in a running scene every frame draws insects at the same in-between time, and
`record on 3` records one moment a tick for an insect walker.
**What the user or player would see:** running insect films (the raid march, scenes with `until`) cannot show a fault
between ticks; insect recordings get a third of the samples asked for.
**Where:** `client/AntRenderer.java:136-146`; `client/FilmDirector.java:252-254`, `359-365`, `407`;
`client/BodyRecorder.java:153`.
**Proof:** walked `onTick`: `waitTicks > 0` returns at line 364 before line 407.
**Fix proposed:** while the game is running (not frozen) draw insects at the game's own in-between time; the camera's
time only in frozen bursts.

### R12-50 (B, proven): a frame film whose checks saw no animal is still "ok"
**What is wrong:** `FilmDirector.endScene` writes no `checks.txt` when the checks recorded nothing (an animal never in
view, every `record` failing into a logged warning, the camera aimed at nothing). `verify_animal.check_films` then
writes `game checks: no checks` and "ok"; `guard_films.film_checks` writes the note "no insect checked by the camera".
A broken checker and an empty film look the same as a clean one.
**Where:** `client/FilmDirector.java:291-293`, `700-704`, `1025-1029`; `tools/verify_animal.py:246-248`;
`tools/guard_films.py:250-252`.
**Proof:** read.
**Fix proposed:** always write `checks.txt` (with "0 moments checked"); a frame film with 0 moments checked is a hard
failure in verify and a problem in the guard report.

### R12-51 (C, to measure): the frame-time meter re-sorts every frame of the launch each second
**What is wrong:** every 60 frames `FilmFrameMeter.write` copies and sorts the full frame list of every scene so far
(an 80-minute launch is about 290,000 frames) on the drawing thread. Two `RenderFrameEvent.Post` handlers (the meter's
and the camera's screenshot read-back) run in an unset order, so a read-back can fall inside the measured drawing time.
**Where:** `client/FilmFrameMeter.java:66-127`; `client/FilmDirector.java:994-1010`.
**Fix proposed:** keep a running summary per scene or write only at a scene's end; give the meter's handler the lowest
priority.

### R12-52 (C, proven): a contact sheet of one burst swallows another burst that starts with the same name
**What is wrong:** sheets take every picture starting with `<burst>_`, so a burst `walk` takes the pictures of
`walk_fast` too.
**Where:** `tools/verify_animal.py:242-245`; `tools/film_sheet.py:10`.
**Fix proposed:** match `<burst>_` followed by digits.

### R12-53 (C, proven): `film.sh` waits "successfully" after a failed reload
**What is wrong:** when the reload failed and the scene was filmed with the old files, `wait_for` prints `ERROR: ...`
but returns 0, so a calling script carries on.
**Where:** `tools/film.sh:46-51`, `54-73`.
**Fix proposed:** return 1 when `error=` is not empty.

### R12-54 (C, proven): the step marker turns every chat system message into text in normal play
**What is wrong:** `FilmStep.onMessage` calls `event.getMessage().getString()` before checking `FilmDirector.active()`,
in every player's game: the only film handler that does work before its guard.
**Where:** `client/FilmStep.java:282-291`.
**Fix proposed:** check `active()` first.

### R12-55 (C, likely): the report key's picture can be moved while it is still being written
**What is wrong:** `ReportShot.take` waits until `next.png` exists, then moves it; Minecraft writes the screenshot on a
background thread straight into that file, so "exists" can be "half written" (on Windows the move then fails and the
picture is lost, with a warning).
**Where:** `client/ReportShot.java:84-91`.
**Fix proposed:** wait until the file's size stops changing (or have the grab's callback mark it done).

### R12-56 (C, proven): small miscounts in the one command's summary
**What is wrong:** (1) `check_clips` counts every `== ` section of `clip_check.py` that is not "clip changes" as a clip
(a `== gait changes` section would count as a clip named "gait"); (2) `check_compare` fails only when the first game
picture is missing; any other missing shot is a blank cell with "ok"; (3) `--quick` picks the newest frame film by
file time, which is arbitrary after a fresh checkout.
**Where:** `tools/verify_animal.py:85-87`, `144-145`, `257-265`; `tools/game_compare.py:129-131`.
**Fix proposed:** match clip sections by the clip header's shape; list missing compare shots as a failure; pick
`--quick` films by git's last change, or name them in the plan.

### R12-57 (C, proven): two names for the camera's in-between time
**What is wrong:** `AntRenderer` and `BodyRecorder` use `FilmDirector.drawPartial()` (with the step's ticks taken off),
the checks in `noteRendered` and `followWalker` use the field `drawPartial` (without). They are equal whenever a picture
is taken today, but the next change to the stepping could part them silently.
**Where:** `client/FilmDirector.java:252-254`, `901`, `1026`.
**Fix proposed:** one accessor everywhere.

### Solid in this slice (do not "fix")
- `FilmChecks` and `BodyRecorder` rebuild each box exactly as GeckoLib draws it (checked against the 4.9.3 bytecode);
  each record runs right after its own animal is drawn; a model's first sighting only turns tracking on.
- Insects: `AntRenderer` draws at the camera's time and the checks use the place at that same time, so the drawn place
  and the checked place agree. Animals: the checks and the recorder use the in-between time they were drawn at.
- BUGS 381's fix (one more client tick after a step arrives before the first picture) and the moment de-duplication
  across cameras.
- `FilmStep`'s marker protocol and the held game thread: stepped ticks run only when the server's news for them is in,
  a 3-second let-go, `letGo` on close, a logged give-up. Deterministic stepping is what everything else stands on.
- Film code does nothing for players: `SCENE` is null in the released game, every film handler returns on `active()`
  first (but R12-54), and the camera never runs in a normal `runClient`.
- The 60 fps cap with vsync off; the frame meter times drawing alone, so the cap does not bend its numbers.
- `verify_animal.py`: one launch per animal (the 80-minute launch lesson), old scene folders cleared before a launch, a
  tool's crash is a hard failure, missing course or frame-film folders are hard failures.
- `body_course.py`: a recording cut short by a crash is a broken gzip that raises (a hard failure); its pop check uses
  only the moves round each moment, so it does catch repeating pops; it reads the body plan only, no animal named.
- `guard_films.py`: a non-zero game exit fails the run; "not filmed" is a problem; `approve --bug` keeps a known bug
  named on every report.
- `FilmWatch`: waits until the files stay still for two looks, skips files rewritten with the same contents, re-tracks
  every model's bones after a GeckoLib reload, reloads block moves; `film_swap.py` refuses a swap jdb rejects.
- `game_compare.py`'s turn back into model space is the exact inverse of GeckoLib's `180 - yaw` turn.

---

## Slice: how the game plays clips and moves (the framework's `entity/body/` and the animals' moves)

### R12-58 (A, proven): after a step down the otter skates on about a block with its legs frozen (a move's "rest" keeps moving)
**What is wrong:** after a block move that ends on land, the animal is meant to stand still for a moment (the move
file's `rest`, 8 ticks by default) so every player's screen catches up before it walks on. But during those ticks
`BlockMoves.step` keeps placing the box with `Move.along(size, t)`, and `along` past the end of the path "goes on at its
last speed" (meant for the glide into the swim). So the box keeps sliding forward at the move's end speed for 8 more
ticks, and the drawing follows it (drawn from the same path, whose clip time also runs on past the end). Meanwhile the
move's clip is held on its last frame: the body slides with the legs frozen. When the rest ends the otter's own walking
starts from a standing start (`step` set its speed to zero every tick), so the slide ends in a lurch.
**What the user or player would see:** an otter coming down a step bounds down, then glides on about 0.9 blocks in 0.4
s with its legs still, then lurches into its walk. Smaller after a climb up (0.32 blocks) and out of the water (0.27
blocks). The monitor (0.05 blocks after a step down) and the hippo (under 0.01) barely show it. Not filmed.
**Where:** `framework/.../entity/body/BlockMoves.java:405-418` (the rest is inside the move, `along` read with
`m.along`), `:141-148` (going on past the end), `MoveRider.java:245` (rest asked for when no hill is ahead);
`block_moves/otter.json` (no `rest_ticks`: 8).
**Proof:** walked: `step` returns DONE only when `ticks > m.ticks(size) + rest`; before that, for a land move,
`along = m.along(size, ticks)`, which for `t > last` returns `row[last] + (row[last] - row[last-1]) * (t - last)`; the
box is set there and the speed zeroed. The otter's `down` ends at 1.79 px a tick (8 x 1.79 / 16 = 0.9 blocks), `up`
0.64, `out` 0.54. Checked again by the main session.
**Fix proposed:** during the rest hold the place at the path's end (read `along` clamped, as `rise` already is) for
moves that end on land; keep going on only for the glide of a move ending afloat. One line in `step` and the same in
`drawnAt` (or a flag on `along`). Then film the otter's step down from the side.

**Status (2026-11-26, `claude/review-fix-12`): fixed: after its clip a move that ends on land stands where its path ends (`Move.along` goes on at the last speed only for a move ending afloat), the box and the drawing alike (`BlockMovesFileTest.afterItsClipALandMoveStandsAndAWaterMoveGlidesOn`; the game test `anOtterScramblesUpAStepAndHopsBackDown` now fails if the otter moves 0.05 blocks through the stand after a move). **Filmed in the game (2026-11-26):** the otter's quick course (its drawn body reaches the end of its path as the clip ends and stands dead still through the rest, then walks on; before, it slid on) and the frame film `otter_moves_frames` twice, the hop down from three sides: it lands and stands, no slide. One take of three had a jump forward that is not this fix's (BUGS 432, a busy PC).**

### R12-59 (A, proven; one link named): an animal seen mid-move is first drawn at the move's start, then jumps to where it really is
**What is wrong:** the place an animal on a block move is drawn at comes from how far its move clip has played
(`MoveRider.clipTicks`, and the monitor's and hippo's `climbClipTicks` copies). While the clip player is still easing
into the move clip (GeckoLib's blend) that answer is 0, so the model is drawn at the move's start. That is right when
the blend starts the moment the move starts. But GeckoLib works an animal's clips out only when it is drawn, so an
animal that started its move while off screen (behind the player, just come into tracking range) eases into the move
clip when it is first looked at, and for those 4 or 5 ticks it is drawn at the move's start, up to 2 or 3 blocks behind
its box. When the blend ends the clip jumps to the move's real moment and the model jumps forward with it.
**What the user or player would see:** turn to look at a hippo climbing out of the water (an 8-second move) or a monitor
walking into the river: for a quarter second it stands back where the move began, then snaps to its real place and
pose. Otter, hippo and monitor alike.
**Where:** `MoveRider.java:178-185` (0 while TRANSITIONING), `:655-664`; `MonitorEntity.java:385-393`,
`HippoModel.java:93` with `HippoEntity.climbClipTicks`; `SteadyController.java:60`.
**Proof:** walked: during the blend `clipTicks` returns 0.0 and `drawnShift` draws the move's start; the server's box is
at the real `ticks`. GeckoLib 4.9.3 starts every new clip, the first one included, with a blend (bytecode). The one
link not run: that GeckoLib's `handleAnimations` runs only for a drawn (not culled) animal, which is how it is built,
not checked in a game. Checked again by the main session (`MoveRider.java:178-185`).
**Fix proposed:** take the drawn place from the move's own time, not the clip player's: `s.moves.ticks(p, gameTime +
partialTick)` at least 0 (what `MoveRider.clock` already works out). The two agree by design except in this case; a
late blend then only blends the pose, never the place. Same change in the monitor's and hippo's copies (or move them
onto `MoveRider`, R9-42 and R12-65).

**Status (2026-11-26, `claude/review-fix-12`): fixed: the drawn place comes from the move's own time (`BlockMoves.drawnTicks`), the same number its clip plays at, never from how far the clip player has got; `MoveRider.clipTicks` and the monitor's and hippo's copies of it are gone (`BlockMovesFileTest.aMoveIsDrawnAtItsOwnTime`). The pose still eases in from the clip's start when first looked at mid-move (only the place was wrong by blocks). Not seen in the game (it needs a move started behind the player).**

### R12-60 (A, proven): a Nile monitor more than 48 blocks away pops a block up or down on every move
**What is wrong:** `MonitorModel.setCustomAnimations` returns before `place(...)` whenever the camera is farther than
`NEAR` (48 blocks), on a move or not. The otter's and hippo's models keep drawing on the move's path while a move is on
(`&& !onMove`, `&& climb == null`); the monitor's copy never got that guard. So a far monitor on a move is drawn at its
box, which climbs a step all at once at the face and drops into the water all at once at the edge, while the move clip
plays in place.
**What the user or player would see:** watching monitors on a river bank from 50 or more blocks (the user explores in
spectator): one climbing a step rears up at the bottom, then jumps a whole block; one walking into the river slides
along the bank top, then drops 0.6 blocks in one tick.
**Where:** `savanna/.../client/MonitorModel.java:129-138` against `OtterModel.java:44-48` and `HippoModel.java:47-51`.
**Proof:** walked; checked again by the main session: the far branch `return`s before `place`; the otter's far test has
`&& !onMove`.
**Fix proposed:** add the same guard as the otter and hippo (`&& climb == null` in the far test), or better, one shared
drawn-place helper the three models call (R12-65).

**Status (2026-11-26, `claude/review-fix-12`): fixed: the far test has the otter's and hippo's guard (`&& monitor.climb() == null`), so a far monitor on a move is drawn on the move's path. No test (drawing only).**

### R12-61 (A, likely): when the server runs slow, moves on screen rewind
**What is wrong:** a move's clip and its drawn place are timed by the world's game time on the player's computer
(`level().getGameTime()` in `MoveRider.clock`, `MonitorEntity.climbClock`, `HippoEntity.climbClock`, and
`Play.turning`). On the player's side that clock adds one every client tick (20 a second, whatever the server does) and
is set straight back to the server's time by the server's time packet, once every 20 server ticks. When the server
falls behind 20 ticks a second or stalls (an autosave), the client's time runs ahead and is then set back.
`SteadyController` follows the move's clock both ways, so the clip and the drawn place jump back with it.
**What the user or player would see:** on a server at 15 ticks a second, every monitor, otter or hippo on a move jumps
back about a quarter second along its climb once a second; during a one-second save stall, a move plays a second ahead
and then replays from a second earlier. Nothing else on screen rewinds like that (lions and buffalo count their own
ticks).
**Where:** `MoveRider.java:188-194`, `MonitorEntity.java:396-402`, `HippoEntity.java:397-403`, `BlockMoves.java:327-329`;
`SteadyController.java:60-67`.
**Proof:** walked in Minecraft 1.21.1's sources: `ClientLevel.tick` adds 1 to the game time each client tick;
`ClientPacketListener.handleSetTime` sets it straight to the packet's value. Not seen in play (no slow server was run),
hence "likely".
**Fix proposed:** on the player's side let a move's clock never go back (hold the furthest value, as `DrawClock` does
for the eases), or count a move's time from when its tag arrived. One place if the three clocks are made one (R12-65).

**Status (2026-11-26, `claude/review-fix-12`): fixed: one clock for a move's drawn time (`MoveClock`, used by `MoveRider`, the monitor and the hippo): it follows the game time and a step back within a tick (the film camera's frame order, BUGS 380), but a rewind of over a tick is not followed: the move stands at the furthest moment drawn until the time is there again (`MoveClockTest`). On a slow server a move now pauses a moment instead of jumping back. Not seen in play (no slow server run). **The other half, seen in a film:** a player's side whose time is behind the server's draws a move late and then jumps it forward (BUGS 432); this clock does not mend that.**

### R12-62 (B, proven): the animals' clip player drops the bit past a loop's end each time round (the insects' player was written to fix exactly this)
(Merged: the playback reader, and the thread reader's "a looping hold's bites drift behind its drawn bites".)
**What is wrong:** when a looping clip passes its end, GeckoLib starts it again at exactly 0 and throws away how far
past the end the frame was. `SteadyController` (every animal's body player, 15 animals) lets that happen: its "a clip
starts" branch sets `played = 0`. So each time round the clip loses up to one frame of its time (times its play
speed). The insects' own player, `AntAnimationController`, keeps the overshoot (`clock %= length`) and its header names
the fault. Two clip clocks in the framework now answer the same question two ways.
**What the user or player would see:** a tiny hitch at each loop's seam and legs that turn over a little slower than the
body moves: at 60 frames a second about half a frame lost a loop, about 1 to 3 percent foot slide on the walks and
gallops, more at low frame rates and fast play speeds. Over a long lion hold, the server's bites (which count exact
rounds) come earlier and earlier than the drawn clamp: about 1/6 tick a round at 60 frames a second, a quarter to half
a second after a minute (see also R12-64).
**Where:** `framework/.../entity/body/SteadyController.java:70-74`; compare `AntAnimationController.java:290-292`;
`Spots.java:40-42`, `LionHunt.java:1071`.
**Proof:** walked in GeckoLib 4.9.3's bytecode: `processCurrentAnimation` on a loop past its length sets
`shouldResetTick` and uses what `adjustTick` returns; `SteadyController.adjustTick` in that branch sets `played = 0`. The
size is arithmetic (to measure on a film of a long hold).
**Fix proposed:** in the reset branch, when the reset is the same loop going round, carry `played - length` instead of
0, as the insects' player does. Better still, one clip clock for both players, so a lesson learned on one cannot be
missing in the other.

### R12-63 (B, proven defect, cost to measure): an otter holding a crab or frog reads and parses a body plan file twice every frame
**What is wrong:** `HeldCatch.fish` keeps each prey's measures with `FISH.computeIfAbsent(name, ...)`, but for a prey
with no `checks.held` (a crab, a bullfrog) the function returns null, and Java's `computeIfAbsent` keeps nothing for
null. So every call opens the prey's body plan in the mod's jar and parses its JSON again, inside a `synchronized`
method. `OtterRenderer.render` calls it every frame for every otter, and `OtterModel` again while the otter holds
anything.
**What the user or player would see:** nothing at once; a little frame time spent reading files, twice a frame per
otter holding a crab (crabs are its favourite prey). Not measured.
**Where:** `savanna/.../animal/HeldCatch.java:51-63`, `75-86`; `client/OtterRenderer.java:77`, `client/OtterModel.java:74`.
**Proof:** walked; only `barb.json` and `tilapia.json` have `checks.held`.
**Fix proposed:** keep "none" too (an `Optional`, or a marker), so each prey kind is read once.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: `HeldCatch` keeps "none" too, so each prey kind's plan is read once (the fix R9-31 already made in batch 5; the same fault found twice).**

### R12-64 (B, proven): a lioness's throat hold and hold-down bite on a clock the drawn clip does not keep
**What is wrong:** in a hold the server bites when `hold.bitesAt(l.clipTick())`: `clipTick` counts game ticks since the
doing began, and a looping hold bites at `clipTick % ticks == hit` (38 and 10 for `throat_hold` and `hold_down`). But
those two holds are loop doings, and `LionEntity.animate` plays a loop doing at the lion's own `loopRate()`, 0.92 to
1.08 by her UUID. So the drawn squeeze and the server's bite drift apart by up to 3 ticks a loop (plus R12-62's loss),
and after a few loops the bite (the prey's hurt flash and sound) lands anywhere in the clip. One-shot holds play at 1.0
and stay in step.
**What the user or player would see:** during a long throat hold the buffalo flinches out of time with the lioness's
jaw clamp after the first two or three squeezes: the very timing phase 3 measured into the spot tables.
**Where:** `savanna/.../animal/LionHunt.java:1070-1072`, `LionEntity.java:1103-1108`, `:101-102`;
`framework/.../entity/body/Spots.java:40-42`; `spots/lioness.json`.
**Proof:** walked: `oneShot(THROAT_HOLD)` is false, so `animate` sets `setAnimationSpeed(loopRate())`; `setDoing` returns
early for a loop already on, so `clipTick` counts on at speed 1.
**Fix proposed:** play the hold loops at 1.0 (their timing is a contact with another body, not a mood loop): one line,
one rule.

### R12-65 (B, proven): `MoveRider`, the planned one home for block moves, lacks two lessons of the monitor's copy, and the drawing half is copied three times
**What is wrong:** R9-42 asks to move the monitor and the hippo onto the shared `MoveRider`. Two things would be lost as
written. (1) BUGS 330 / R9-37: the monitor's room checks ask for a whole block on the middle line too, because the move
relies on the middle line's block; `MoveRider`'s `roomUp`, `roomDown` and `roomOut` check only the two feet. For the
otter that is enough (feet 0.22 blocks out), but the monitor's feet stand 0.56 blocks out, so its middle line can be a
third column: on `MoveRider` the monitor's freeze before a slab or path block comes back. (2) The monitor's "not again
from the same stand" guard (`cutAt`): `MoveRider` lets a first-tick cut fall through to ordinary walking, the very try
BUGS 330 says broke the angled climb one run in six; and each tick of such a start-and-cut zeroes the animal's speed
(the otter walks at about half pace there). The hippo's copy has neither guard. And the drawing half (`drawnShift`, the
hand-over) is copied in `MoveRider`, `MonitorModel.place` and `HippoModel.place`; the copies have already drifted
(R12-60).
**What the user or player would see:** today the far monitor pop (R12-60) and an otter slowed near some steps; after
R9-42 done as written, the monitor's freeze (BUGS 330) back.
**Where:** `MoveRider.java:526-628`, `221-232`, `480-514`, `655-686`; `MonitorEntity.java:616-718`, `415-420`,
`777-786`; `HippoEntity.java:622-630`; `MonitorModel.java:167-210`; `HippoModel.java:90-130`.
**Proof:** walked each copy side by side; `CLIMB_FEET_OUT = 12.0` (`MonitorEntity.java:245`), `feetOut(3.5)` for the otter.
**Fix proposed:** before R9-42's move, put the middle line into `MoveRider`'s room checks (as `roomIn` already does)
and its first-tick guard, and give `MoveRider` the client half as the one drawing helper the three models call. Then
move the monitor and the hippo over, guarded by their course and frame films.

### R12-66 (C, likely): the film watch's "reload the move files" swaps the moves under live plays
**What is wrong:** `BlockMoves.reloadAll` replaces a file's data, but plays already running hold a move number and size
number into the old list, each animal's `Setup.edges` and the monitor's `UP`, `DOWN` constants were looked up once by
name, and in single player the server thread reads `data` several times inside one `step` while the drawing thread
swaps it.
**What the user or player would see:** only while filming in watch mode after a maker added, removed or reordered a
move: the wrong move played at an edge, or an index error on the server thread mid-film.
**Where:** `BlockMoves.java:78-82`, `MoveRider.java:44-61`, `MonitorEntity.java:236-237`.
**Proof:** walked; not run.
**Fix proposed:** have the watch mode refuse a reload that changes the move names or their order (and say so in the film
log), and read `data` once per `step`.

### R12-67 (C, proven): client-only and removed-species code inside the framework's shared package
**What is wrong:** `OwnBones` imports `net.minecraft.client.Minecraft` and GeckoLib's client cache but sits in
`com.antcolonies.entity.body`, a package the server also loads (safe only because nothing on the server touches it).
`BodyPlans.WORKER_WAIST` and `WORKER_CLIMB` are "the harvester worker's" numbers, still the framework's fallback for any
plan without a `climb` part, though the harvester was taken out (2026-11-23).
**Where:** `framework/.../entity/body/OwnBones.java:1-20`, `BodyPlans.java:95-101, 222`.
**Fix proposed:** move `OwnBones` to `com.antcolonies.client`; drop the worker numbers for a neutral default worked out
from the model's size (what `liftFor` and `intoWallFor` already do), or make `climb` required for plans that climb.

### R12-68 (C, proven): a missing spot table is silently no table
**What is wrong:** `Spots` returns an empty table when `assets/<mod>/spots/<animal>.json` is missing, with no log line.
A lion form without its file finds no hold and no free spot and never strikes, and the log does not say why. Also
`Spots.at` builds a fresh zero row on every call (`getOrDefault("idle", new double[...])`).
**Where:** `framework/.../entity/body/Spots.java:96-99, 195`.
**Fix proposed:** log a warning once for a missing table; build the fallback row once.

### R12-69 (C, proven): the dragged tail does all its ground reading even when it has no say
**What is wrong:** `DraggedTail.pose` calls `TailDrag.update` (several ground reads per tail piece, 12 pieces for the
monitor) before it looks at the drag's weight, and returns right after when the weight is 0 (basking, hissing, biting,
eating, dying). That work is thrown away every frame of those clips.
**Where:** `framework/.../client/DraggedTail.java:66-70`, `TailDrag.java:173-314`.
**Fix proposed:** skip the update while the weight is 0 and `on` is false (after a gap of over half a second `TailDrag`
lays the tail out afresh anyway).

### Solid in this slice (do not "fix")
- **The move is one synced tag, set only at its start and end**, read back by identity (a pointer compare a tick); the
  server places the box from the same path the client draws, so box and drawing never drift apart in ordinary play.
- **The box is checked every tick** (`BlockMoves.step`: the step's own block on the middle line, no block in the box's
  way, death, a land move in water) and a cut sets it down cleanly and pushes it out of any block.
- **Reload mid-move is safe without saving the move:** the box always stands where `noCollision` allowed it; a lioness
  riding her prey is let go on reload by design (the hunt is not saved).
- **A move's length is its own size's row** (R9-38); the file reader is strict and fails loudly at start.
- **`SteadyController`'s blend on real time (BUGS 372) and following the clock both ways (BUGS 380)** are right for play
  and for the frozen film (R12-61 is about the clock it is given, not the player).
- **`DrawClock`** never goes back and treats a step back of over a tick as a reset.
- **`GaitShift`** waits for the footfall only while the old gait carries the speed (BUGS 384), follows the clip across
  loop restarts, and is pinned by `BodyPlansTest`; `Gaits` is the one place the gait numbers live.
- **`Spots.world` / `holderAt`** use the same frame for prey and holder, and the holder is placed by `positionRider` on
  both sides from synced data, so jaws and hide agree on every screen.
- **`OwnBones`** rebakes its copy after a resource reload.
- **`BoneFrames` and `DraggedTail`** use GeckoLib's own matrix order and signs; `setTurn` and the models' `nudge` leave
  GeckoLib's change marks as they were, so the shared bones never build up turns from frame to frame.
- **`TailDrag`** eases with `1 - exp(-dt/time)` (the same at any frame rate; its test runs several) and lays the tail
  out afresh after a teleport or a half-second gap.
- **`AntAnimationController`** keeps the loop overshoot, swaps walks in step at their swap notes, blends on real time
  and lets go of bones it does not own; **`BoneMesh`** draws exactly GeckoLib's corners; **`PoseMemory`** reuses a far
  insect's pose only on alternate frames and only while frames are quick.

---

## Thread: "the game is the one truth" (a Fable reader, across all the files)

The thread reader followed a clip from its file to a drawn pose on both sides. Most of what it found is merged above
(R12-2, R12-6, R12-9, R12-10, R12-12, R12-13, R12-22, R12-32, R12-33, R12-41, R12-44, R12-62). Its own findings:

### R12-70 (A, proven): the monitor's drawn leg fold turns its right hind foot the wrong way (a Java copy of the swim pose, "kept in step by hand", is out of step)
**What is wrong:** when a monitor's hind feet lose the ground over water and no block move fits, `MonitorModel` folds
the legs toward the swim clip's first pose, drawn over the walk. It does not read the swim clip: it holds a hand-copied
table `FOLD` of the **left** legs' angles and makes the right side by mirroring them ("the right side mirrors y and
z"). For the arms that is true in the clip; for the hind legs it is not: the swim clip's right hind lower leg and foot
carry the **same** angles as the left (`leg_lower_right [0, 0, 19.231]`, `leg_foot_right [-56.661, 24.419, -131.013]`),
because the maker solved each foot to a mirrored target and the solver landed on a different set of angles for the
same mirrored pose. Mirroring those angles a second time gives a different foot.
**What the user or player would see:** wading into water where no move fits (a bank too short, flowing water's slope):
as the hind pair folds, the right hind foot turns round, its foot box drawn about 6 px from where the swim clip puts
it and its toes and claws 14 to 21 px (up to a block); when the swim clip takes over, that foot swings back through
the blend. The same family as BUGS 235 ("a hind foot twisting round backwards"), from a different cause.
**Where:** `savanna/.../client/MonitorModel.java:94-102` (the table and its "kept in step with it by hand"),
`:305-313` (`turnToward(bone, -f[0], -s * f[1], s * f[2], fold)` for the right side, s = -1); the swim clip in
`animations/entity/monitor.animation.json`; its maker `tools/monitor_models.py:1559-1566`.
**Proof:** read both sides; posed through the lab: the swim clip's right hind leg against the mirror of its left is a
mirror pair (1.1 to 1.7 px apart below the hip, the body's sway); the Java rule's right hind leg against the same
mirror: lower 3.0, foot 5.7, toes 13.6 to 16.1, claws 17.4 to 21.4 px. Checked again by the main session: the swim
clip's first keys read from the file (`leg_lower_left` and `leg_lower_right` both `[0, 0, 19.231]`, `leg_foot_*` both
`[-56.661, 24.419, -131.013]`; the arms mirrored, `arm_lower_right [121.979, -45.687, -27.192]`).
**Fix proposed:** never copy clip angles into Java by hand: the model reads the fold pose from the swim clip itself at
load (GeckoLib's baked animation, the first key of each leg bone, both sides as the file has them), so there is no
mirroring rule and nothing to keep in step. Until then, store both sides in `FOLD`. Add the rule-1 match: a unit test
that compares the Java fold table with the swim clip's first keys.

**Status (2026-11-26, `claude/review-fix-12`): fixed: the fold is the swim clip's own first pose, both sides as the clip has them, read from the clip's file when the model is made (`client/SwimFold`); the hand-copied table and its mirror rule are gone (`SwimFoldTest`: the arms are mirror images in the clip, the hind lower leg and foot are not). Not filmed (a wade where no move fits has no film scene yet).**

### R12-71 (A, proven): the spot tables and the pair check never see what the game draws on top of the clips, so a throat hold bites the air with every check green
**What is wrong:** `tools/spots.py` poses the buffalo's spots from its clips alone, and `clip_check.py --pair` measures
the lioness's jaw against that same clip-only body. The game then draws two more things on the buffalo, after the
clips, that move the spots: its **neck turns up to 20 degrees toward what it watches** (`BuffaloModel.eyes`, any
doing but the hits and lying down or getting up, so a held cow too; `catchesEye` watches any lion that is not frozen,
so the lions holding it), and its **trunk tilts up to 35 degrees** on uneven ground (`StandTilt`; the holding lioness
30). The holder is placed on the clip-only spot (`LionHunt.placeOn` -> `BuffaloEntity.spotAt` -> `Spots.at`), on the
server and every screen alike. The game test that guards the hold (`aHolderRidesOnHerSpotHerJawOnItAndKeepsStill`, jaw
within 0.05 of `calf.spotAt`) measures the table against itself, so it cannot see either layer.
**What the user or player would see:** two lionesses clamped on a standing cow's throat (`throat_hold`): within about a
second the cow's head turns 20 degrees toward one of them; the hide slides about 3/16 of a block under both jaws, and
the far one's jaw floats about 1/16 of a block off the neck, past the pair check's own "bites the air" limit (1 px). On
a slope, every hold is off by the tilt (a rump spot 18 px behind the middle rises or sinks up to about 10 px at 35
degrees).
**Where:** `tools/spots.py:132-151`, `tools/clip_check.py:1095-1131`; `savanna/.../client/BuffaloModel.java:54-55,
63-86`; `framework/.../animal/Attention.java:116-130`; `savanna/.../animal/BuffaloEntity.java:199-216`, `:318-334`;
`savanna/.../client/StandTilt.java:66-77`; `savanna/.../gametest/LionHuntTests.java:310-312`.
**Proof:** walked the code path (the held doings are not excluded from the head turn; a holding lioness is not
`frozen()`; her direction from the cow's eyes is well within the 120 degrees that let the head turn). Posed offline:
buffalo `idle` at t 0, neck turned 20 degrees about its own Y as `BuffaloModel` turns it: each neck spot slides 3.37
model px; the table's spot lies 1.16 px outside the turned hide on the side turned from. Checked again by the main
session (`catchesEye` lines 201-216, `BuffaloModel` neck `setRotY(... - headAim)`, `Attention.java:128`).
**Fix proposed:** decide per layer, in one place: (1) a held animal does not turn its head toward its holders (the
simplest and the most real: a clamped throat cannot turn): the attention's head turn off while held (`heldBy` is known
on both sides through the passengers). (2) For the tilt, holds turn it off for the prey and its holders while held (my
pick: flat holds are what the pair check measures), or the holder is placed from the drawn ground too. Give the pair
check the same switch, so the offline check and the game agree by construction; add a course scene of a hold on a
1-block step.

**Status (2026-11-26, `claude/review-fix-12`): fixed, both layers off while held: a buffalo with a lion on it does not turn its head (`BuffaloEntity.held`, both sides) and is drawn level, and a lion on its prey (holding or feeding) is drawn level (`StandTilt.place(..., level)`), so the game draws the same clip-only body the spot tables and the pair check measure; nothing to change offline. Not done: a course scene of a hold on a 1-block step. On a slope a held cow and her holders now stand level about their boxes (my pick; to see in play).**

### R12-72 (B, proven): the move files have no staleness check (the spot tables do), and their unit test reads only the monitor's
(Merged: the thread reader, and the playback reader's "the move-file unit test reads only the monitor's file".)
**What is wrong:** a move file (`block_moves/<animal>.json`) is the game's copy of each move clip's `path` note, written
by the maker's `game` command from clips in memory. Nothing checks it against the animation file the game and the
checker read: a clip remade (or a Neon clip taken) without rewriting the move file plays on an old path, while the
checker shows the new one. `spots.py --check` does exactly this for the spot tables, in the build. And
`BlockMovesFileTest` loads only `monitor.json`; the otter's and hippo's files (both in play) are checked by nothing but
the game starting, and four move files (`buffalo`, `lion`, `lioness`, `lion_cub`) are loaded by no Java code yet.
**What the user or player would see:** a climb drawn off its step (the body rises at the wrong ticks) with a clean
offline report; a broken lion move file found only the day it is wired in.
**Where:** `tools/model_parts.py:302-334`; `build.gradle:79-82` (only spots);
`savanna/src/test/java/com/antcolonies/savanna/entity/body/BlockMovesFileTest.java:16`.
**Proof:** a comparison of all 7 move files with their clips' `path` notes: all agree today (rise within 0.0001, along
exact), but for the hippo's row timing (R12-73).
**Fix proposed:** a `--check` in the move writer (rebuild each file's rows from the animation file's notes and compare),
run by the build beside `checkSpotTables`; one unit test that loads every move file in the folder and checks each move
(the end level reached, `rise` and `along` rows of one length, one clip a size, and, with R12-58, a land move's end
speed near zero or its rest 0).

**Status (2026-11-26, `claude/review-fix-b6`): half fixed: `BlockMovesFileTest.everyAnimalsMoveFileLoads` loads all seven move files as the game does and checks each move of each size. Left: the staleness check against the animation files.**

### R12-73 (C, proven): the hippo's climb-out paths are not one row a tick, and the move writer never checks
**What is wrong:** the game reads a move's path rows as ticks (row index = tick); `block_moves` drops the rows' own
times. The hippo's `out_920` and `out_1000` have 167 rows over 8.28 s (a row every 0.0499 s), so the game's path runs
166 ticks against the clip's 165.6, ending 0.4 tick behind the clip.
**What the user or player would see:** nothing visible today (0.4 tick at a slow climb).
**Where:** `tools/model_parts.py:321-324`; `framework/.../entity/body/BlockMoves.java:135-148, 232-243`.
**Proof:** the comparison above (`max |t - k/20|` 0.02 s for those two clips, 0 for every other move clip).
**Fix proposed:** `block_moves` asserts each row's time is `k / 20` (or resamples to ticks).

### Solid on this thread (do not "fix")
- **The keyframe maths match GeckoLib 4.9.3** (bytecode read): times in seconds x20 to ticks; the easing between two
  keys is the later key's; before the first key its value, after the last its value; rotation keys x and y negated,
  radians, the bone's rest turn added, which with GeckoLib mirroring the model in x is exactly the lab's "X and Z the
  other way, Y as is"; GeckoLib turns Z, then Y, then X on the matrix, a point turned X first; scale about the pivot
  before the turn; units model px, 16 a block.
- **The blend into a clip** is GeckoLib's: a straight per-axis ease from each bone's last value to the new clip's value
  at tick 0, then the clip from 0; `SteadyController` makes it the blend length in real time at any play speed (BUGS
  372), exactly what `switch_poses` assumes. Its long-way spins are real in the game too.
- `SteadyController` (rate, never position; both ways of the clock, BUGS 380) and `DrawClock` are sound.
- The spot tables and the game place the holder with the same sums (`Spots.world`, `holderYaw`, `holderAt` against
  `spots.placement` and `Pair.holder_frame`, checked by hand at yaw 0 and 90); the prey's scale is the same on both
  sides; `clipTick = doingFor - BLEND` is the drawn clip's time.
- `BlockMoves` / `MoveRider`: the box and the drawing play the same path from the same start; the drawing uses the drawn
  clip's own time, so path and pose never drift (but R12-59's first blend); `drawnShift`'s model-frame shift matches
  the renderer. All 7 move files agree with their clips' path notes today.
- `Gaits` / `GaitShift`: one source (the plan) for speeds and change moments, read by both sides.
- `BodyRecorder` records the drawn truth (all layers, every bone, the clip time), and `body_course.py` reads its rows
  the way they are written. `motion_kit.py`'s self-check passes.
