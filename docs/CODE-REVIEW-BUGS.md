# Code review bugs

Every finding of the full code review (`docs/plans/code-review.md`) and where it stands: what is left on
top, what is done at the bottom. **This file is written by `python tools/review_bugs.py`; never edit it by
hand.** The findings in full (what is wrong, what the player sees, the proof, the fix proposed) are in the
review files under `docs/reviews/full/`; search there for a finding's number (`R2-16`). A fix session writes
a `**Status ...**` line under the finding there and runs the tool. Bugs that did not come from the code
review stay in `docs/BUGS.md`.

**307 findings: 218 left (11 of them wait for your call), 89 done.**

| Grade | Found | Not started | Open | Half fixed | Fixed | Not a bug after all |
|---|---|---|---|---|---|---|
| A: a player can meet it | 67 | 17 | 1 | 0 | 48 | 1 |
| B: it will cost us when building on this | 143 | 104 | 3 | 4 | 31 | 1 |
| C: a tidy-up | 97 | 87 | 0 | 2 | 7 | 1 |

| Round | System | Found | Left | Done |
|---|---|---|---|---|
| 1 | the colony brain | 50 | 30 | 20 |
| 2 | the legs | 43 | 18 | 25 |
| 3 | the insect body | not read yet | | |
| 4 | how bodies are drawn | not read yet | | |
| 5 | the termites | not read yet | | |
| 6 | the Matabele ants (not raids) | not read yet | | |
| 7 | the giant trees | 36 | 27 | 9 |
| 8 | the world | 20 | 17 | 3 |
| 9 | the river hunters | 44 | 34 | 10 |
| 10 | the river's small life | 19 | 13 | 6 |
| 11 | the new raid code | 22 | 6 | 16 |
| 12 | the animation engine | 73 | 73 | 0 |

## Waiting for your call (11)

These change how the game looks or plays, or a balance number, so nothing is built until you say. Each has
what happens today and my pick. Answer whenever you like: tell Claude the number and what you want.

- **R7-12** (grade B, the giant trees) Lightning takes big trees faster than old age
  - *Your call:* Lightning near big trees. Today every bolt that lands within 24 blocks of a big tree is pulled onto it, so by my sum (not measured) one thunderstorm kills about 1 big tree in 8, and a Channeling trident thrown near big trees strikes the tree, not the mob. Change it so only the weather's own bolts are pulled, and a tree is struck at most once a storm? My pick: yes.
- **R7-13** (grade B, the giant trees) Seedlings after rain fill the savanna in
  - *Your call:* Seedlings after rain. By my sums (not measured) about 9 new grown trees come up in each 64-block square over 100 days where new land put 1, so round a long-lived base the open savanna slowly closes into woodland with fallen logs everywhere. Cap the seedlings so each square keeps about as many trees as new land gives it, and let old logs weather away in the end? My pick: yes; it keeps the savanna open, as the design says.
- **R7-14** (grade B, the giant trees) Termites hollow baobabs, which nobody decided
  - *Your call:* Termites and baobabs. Today termites hollow a baobab near their mound like any tree: a cave about 7 wide in the trunk, and the tree dies at half its age. Nobody decided that. Leave baobabs out? My pick: yes, leave them out (as far as I know Macrotermes do not hollow living baobabs; not checked against a paper).
- **R7-31** (grade B, the giant trees) A cut tree's crown hangs for good
  - *Your call:* A cut giant tree. Today a giant tree cut through at the foot keeps its trunk and whole crown hanging in the air for good, and a burnt fig or palm leaves pieces in the air. Should a tree cut through fall, the way a dead one already does (the fall, the log and the smashing are built)? My pick: yes.
- **R9-19** (grade C, the river hunters) several small things, listed in the review file
  - *Your call:* Hippo paths. Today the ground a pod walks wears to path blocks and never heals, a player's lawn by a pool too (only with mobGriefing on). Let worn paths grow back to grass when no hippo has walked them for some days? My pick: yes, slowly. (The other small things in this finding need no call.)
- **R9-24** (grade B, the river hunters) a shark hit from the bank or from a boat does nothing
  - *Your call:* The big river hunters and arrows. Today a shark shot from the bank or a boat does nothing about it, and the crocodile afloat and the hippo past 16 blocks are the same: all three can be killed from a distance with no answer. Should one hit by something it cannot reach swim off deep and fast for a while? My pick: yes, for the shark and the crocodile; the hippo charges or goes under.
- **R9-44** (grade C, the river hunters) small things in the monitor
  - *Your call:* The monitor in water and on mud. Two looks: hunting in the water it leaves by Minecraft's plain hop, not its own climbing-out move, and on soft mud it steps and hops the plain way because its moves never start there. Worth the work to give both its own moves? My pick: not now (small, and only a look); say if you have seen it and it bothers you.
- **R10-5** (grade B, the river's small life) river algae spreads into any shallow water, with nothing to stop it
  - *Your call:* River algae spreading. Today a thick mat seeds any shallow still water beside it, with nothing to stop it: over many hours it creeps out of the savanna's rivers along lake and sea shores and into a player's pond joined to a river, and grows back unless every bare speck is broken. Keep it to the savanna's waters, and patchy (no new mat where enough lie round it)? My pick: yes.
- **R10-16** (grade B, the river's small life) snail eggs within a crab's reach never hatch
  - *Your call:* Crabs and snail eggs. Today a crab goes for snail eggs before any other food, anywhere in its search box, so where both live no snail clutch ever hatches (the world's spawner still refills the snails). Leave it, or make eggs one food among the others, eaten only when the crab stands within 2 or 3 blocks? My pick: the second, so the snails' own breeding counts.
- **R11-17** (grade B, the new raid code) near and away still differ in how often a colony raids
  - *Your call:* How late a raid may leave. Today a raid may set out until 19:00 and dusk turns every raid home at 19:30, which is 25 real seconds later, so a raid that leaves late walks out, turns round empty and the scout's find is used up. Stop raids leaving earlier (the find then waits for the morning)? My pick: yes, but first a soak measures how long a raid takes, so the hour is not a guess.
- **R11-22** (grade C, the new raid code) numbers
  - *Your call:* Real ants in one raid. `most_real` is 75, while the project's aim is about 50 to 60 real mobs for a whole colony. Lower it to about 50 (the rest of a big raid goes along as numbers, as now), or keep 75 for the look of a long column? My pick: keep 75 unless you see lag in a big raid; it is your eye that decides.

## Left to do

Grade A first. *Open* means looked at and left for a reason (the reason is given); *half fixed* says what
is left; no word means nobody has started it. The findings that wait for your call are in the part above.

### A: a player can meet it (18 left)

**Round 7, the giant trees** (`docs/reviews/full/round-07-trees.md`)

- **R7-33** A palm's crown can blink out at the screen's edge
  - *Open:* waits to be seen (2026-11-24). Read from how Minecraft hides parts of the world, never seen in the game; the fix is large (sharing a crown's faces out over its blocks) and is built only if the user sees fronds blink out.

**Round 12, the animation engine** (`docs/reviews/full/round-12-animation-engine.md`)

- **R12-1** a walk's planted feet are never judged for hanging, and a walk foot that never touches the ground raises no flag
- **R12-2** most of the checker's findings never say FLAGGED, and a jump (a pop) is never judged at all, so the summary the rule book reads counts none of them
- **R12-4** a part spun a whole turn at a clip change passes the blend check on any animal with a blend of 4 ticks or more
- **R12-20** the lab's floor sinks with the lowest foot, so feet pushed into the ground are never reported
- **R12-35** verify_animal says "passed" for an animal it never filmed in the game
- **R12-36** a game crash during the films is not a hard failure
- **R12-37** a course scene with no recording, or almost none, reads as "nothing found"
- **R12-38** nothing checks that the animal did what the scene asked, and the game's own warnings are never read
- **R12-40** a finding at an approved picture is let through however much worse it is
- **R12-41** the camera's jump check cannot see a pop that repeats
- **R12-42** the watch mode films old spot tables and old body plans after they change
- **R12-58** after a step down the otter skates on about a block with its legs frozen (a move's "rest" keeps moving)
- **R12-59** an animal seen mid-move is first drawn at the move's start, then jumps to where it really is
- **R12-60** a Nile monitor more than 48 blocks away pops a block up or down on every move
- **R12-61** when the server runs slow, moves on screen rewind
- **R12-70** the monitor's drawn leg fold turns its right hind foot the wrong way (a Java copy of the swim pose, "kept in step by hand", is out of step)
- **R12-71** the spot tables and the pair check never see what the game draws on top of the clips, so a throat hold bites the air with every check green

### B: it will cost us when building on this (103 left)

**Round 1, the colony brain** (`docs/reviews/full/round-01-colony-brain.md`)

- **R1-3** a young colony that once yielded to a neighbour yields again after one meeting
- **R1-4** some numbers in the species file have no range check, and a 0 crashes
- **R1-10** a colony with no food plants still scans about 2,400 columns every 5 seconds
- **R1-11** once a second every outside worker is compared with every plant
- **R1-12** each forager's plant search counts every claim for every plant
- **R1-13** the store's room is judged in three places by three rules
- **R1-22** the catch-up digs the founders' chamber by weaker rules than the real diggers
- **R1-26** the far rule removes loaded insects without asking whether a player can see them
- **R1-27** a colony that dies out while away is not ended until a player comes near
- **R1-28** folding an insect into the numbers loses a species load it carries
- **R1-29** an insect frozen while its colony is here waits in limbo
- **R1-31** every colony ever made ticks once a second for ever and marks the file changed
- **R1-37** all the numbers of a caste share one meal minute
- **R1-46** a colony whose queen cannot be found is skipped for ever while near
- **R1-47** more reads one step past the guarded cell, and on a list up to 5 seconds old
  - *Half fixed:* mostly fixed, BUGS 390 (`FoodMap`'s 27 cells round each ant and `isEntranceCovered` left: the ant's own land is loaded).
- **R1-48** the "no route to this food" memory is not saved

**Round 2, the legs** (`docs/reviews/full/round-02-legs.md`)

- **R2-16** a search that finds no way costs about 20 ms, most termite walks run it twice, and nothing limits how many run in a tick
  - *Half fixed:* grade B now: measured in the termite stress scene (`stressTermiteColony`). A search that finds no way is rare there (0 to 2 in a minute's window), but one alone costs 36 to 57 ms and makes its tick 50 to 64 ms: one slow tick now ...
- **R2-18** big step costs fight the fixed cap, and the mud test hides it
  - *Half fixed:* measured and the mud fixed. `SoftGroundSearchTest` (framework): a band of soft ground 8 wide that must be crossed is "no way" at 12 a step with the game's 6,000 and is crossed at 4; wet mud now costs 4 ...
- **R2-29** the way through a chimney mouth still uses one body's lengths
  - *Open:* left on purpose. Checked now: only the termite worker fits the tunnels (the tag `fits_termite_tunnels`), so only it takes a mouth's tube; the king, queen and nymph pass only the old chimney crown block, which no mound builds any ...
- **R2-39** a mind pays for the same "no way" again a tick later, and a builder can ask 24 times in one tick
  - *Half fixed:* `Errand.walkOn` now treats a walk the legs gave up as "no way" (R2-2's fix): no second ask a tick later. Open: `BuildMoundGoal`'s many asks in one tick (round 5).

**Round 7, the giant trees** (`docs/reviews/full/round-07-trees.md`)

- **R7-2** The once-a-second walk looks at every tree in the world, and always starts from the same end
- **R7-3** A tree's growth step is one tick's work, and all trees come due at the same dawn
- **R7-4** A storm breaking a hollow tree works out three shapes on the server's own thread
- **R7-5** The fall in sight: up to 2,500 entities made in one tick
- **R7-6** The whole tree book is written at every autosave, and it only grows
- **R7-7** Four shape stores are never emptied
- **R7-18** One bad record empties the tree book
- **R7-19** A fallen log's record never ends
- **R7-20** Felling checks the foot's chunk only
- **R7-24** The tree kind files are not the "numbers I can tweak" they look like
- **R7-26** Test gaps on what the player sees
- **R7-30** The river animals' ground finder stops on top of a crown
- **R7-32** Fruit stands still when nobody is near

**Round 8, the world** (`docs/reviews/full/round-08-world.md`)

- **R8-1** river sites are remembered by the seed alone
- **R8-3** biome mods that are data packs lose all their biomes
- **R8-7** an oxbow site with no room does all its shape sums 8 times
- **R8-10** the flat savannas replace eleven of Minecraft's own land files
- **R8-12** twelve spawning groups are tried on every tick in every chunk, everywhere
  - *Open:* measured, grade B now, open (2026-11-24, `SpawnerCostTests.whatTheSpawnerCostsForOurGroups`, run by name, in the `stress` category). One try of a group with no spawn list costs 27 ns in solid ground (most tries: the spot's height ...
- **R8-14** the mud's own wetness rule undoes the pattern the world maker lays
- **R8-19** in a Large Biomes world the cave filling looks at the wrong map

**Round 9, the river hunters** (`docs/reviews/full/round-09-river-hunters.md`)

- **R9-2** every search for entities in the world walks every body hit box of every animal
- **R9-7** a crocodile whose basking spot lies beside a hippo swims to it and turns away, again and again
- **R9-8** every crocodile route may search four times as far as a zombie's, and failing routes are asked again and again
- **R9-9** two wide searches of the land done in a single tick
- **R9-10** a mother taken from her nest reads the nest's land wherever it is
- **R9-13** the warning yawn is remembered per hippo, not per visitor: the second one is bitten unwarned
- **R9-15** a hippo shot from more than 16 blocks never answers
- **R9-16** two "nothing found" searches with no wait after them
- **R9-17** the pool's water is read with no check that its land is loaded
- **R9-18** when the pool is gone each hippo takes its own, and "gone" is judged two ways
- **R9-21** a shark's hunt hangs still for up to 45 seconds
- **R9-22** only the three nearest fish are tried, so a school on the shelf hides every fish behind it
- **R9-23** a shark with nowhere to go asks for a new target every tick and lies still
- **R9-25** the catfish plans a new route every tick once its route has run out short of its prey
- **R9-31** while an otter on screen holds a crab or a frog, the game reads and parses a file from the mod's jar twice every frame
- **R9-32** a hunt that gives up takes the same prey again half a second later
- **R9-33** a cornered otter plans its escape again every time it is asked
- **R9-34** harm with no attacker does not make an otter run, and it wipes the fright of the otters near it
- **R9-35** what the otter is doing lives in five places, and every A finding above is a state left behind when one of them ended unplanned
- **R9-39** the mid-climb tests fail now and then because the monitor's own idle goals take it off the test's route (BUGS 255, 290, 292)
- **R9-40** after a reload, and from its spawn until its first swim, nothing keeps a monitor near its water
- **R9-42** the code that starts a staged move exists three times

**Round 10, the river's small life** (`docs/reviews/full/round-10-river-small-life.md`)

- **R10-4** catfish eggs are eaten long before they can hatch
- **R10-10** the two frog steps before breeding can end with nothing, and a hungry frog searches wide 5 times a second
- **R10-14** the crab's 20-second limit on threatening does nothing
- **R10-15** asking for a new path every tick to a spot that cannot be reached
- **R10-18** small searches that run more often than they need

**Round 12, the animation engine** (`docs/reviews/full/round-12-animation-engine.md`)

- **R12-3** clip_check always exits 0, whatever it finds
- **R12-5** the corner of a step or bank can sink into a box unseen, offline and in both game checks
- **R12-6** the pair check sees only part of a looping prey clip, at one phase, and not the blend or the walk
- **R12-7** "jaw to the hide" measures the nearest box of the prey, not the spot, so a jaw 2.6 px off its spot reads 0.0
- **R12-8** the pair check takes 75 s for one holder and prey
- **R12-9** a bone the new clip does not move goes home over GeckoLib's 5-tick reset, not the blend
- **R12-10** the blend length is read from the Java by a pattern that misses three animals and every blend changed while playing
- **R12-11** the picture drawn for a flagged clip change shows a different moment from the one flagged
- **R12-12** the offline dragged tail is its own simpler rule, never matched against the game's TailDrag
- **R12-13** a spot table can be behind its plan or the posing code and `--check` still passes
- **R12-14** names in a plan's "checks" are never checked, and a misspelt one silently switches its check off
- **R12-15** clip_check redoes every clip on every run; the monitor takes about 9 minutes
- **R12-16** pair_report names the lion's strike clips and reads the jaw from another section of the plan
- **R12-21** the loop-seam and snap checks read only rotations; a position or scale jump at the loop passes
- **R12-22** nothing measures that the offline pose is the game's pose; `match` only puts pictures side by side
- **R12-23** the lab's sizes and speeds are its own hand-typed tables, not the game's
- **R12-24** a standing clip's sliding foot is only a note, so the summary says "0 problem(s)"
- **R12-25** `two_bone` gives wrong pieces (and says "no miss") when the target is close, and crashes at the root
- **R12-26** a pole along the leg's line crashes `two_bone` and flips the knee 180 degrees as the target crosses it
- **R12-27** the kit returns a miss but every newer maker throws it away
- **R12-28** the kit's clip cache leaves out the data files a maker reads, so a changed outline keeps old clips
- **R12-32** whether a clip loops is decided twice, by the Java and by the file, and the tools read only the file
- **R12-39** the guard films' headline says "ALL CHECKS PASS" with every picture changed
- **R12-43** "frames N" does nothing for animals: N identical pictures a tick
- **R12-44** the camera's jump check measures "per tick" wrongly in three cases
- **R12-45** a fish held in a mouth is checked and recorded at the wrong place
- **R12-46** two films at once, an open watch session, and no timeout
- **R12-47** the frame watch runs in every player's game and samples the drawing thread
- **R12-48** the recorder writes the blocks only round where each animal was first drawn
- **R12-49** insects in a running scene are drawn at a stale in-between time
- **R12-50** a frame film whose checks saw no animal is still "ok"
- **R12-62** the animals' clip player drops the bit past a loop's end each time round (the insects' player was written to fix exactly this)
- **R12-63** an otter holding a crab or frog reads and parses a body plan file twice every frame
- **R12-64** a lioness's throat hold and hold-down bite on a clock the drawn clip does not keep
- **R12-65** `MoveRider`, the planned one home for block moves, lacks two lessons of the monitor's copy, and the drawing half is copied three times
- **R12-72** the move files have no staleness check (the spot tables do), and their unit test reads only the monitor's

### C: a tidy-up (86 left)

**Round 1, the colony brain** (`docs/reviews/full/round-01-colony-brain.md`)

- **R1-5** the founding egg's spot is rolled with the queen's own dice
- **R1-6** comments that still describe the harvester
- **R1-15** tidy-ups in the food store
- **R1-23** progress on a leak or a dug block is cleared only when the job finishes
- **R1-25** two comments sit above the wrong method
- **R1-32** a trip out of range shorter than the grace wipes the last away report
- **R1-33** a colony's dice start the same run after every reload
- **R1-34** comments from before the harvester left
- **R1-38** two judges of "it rains"
- **R1-39** the away brain asks the world for eggs and larvae several times a second
- **R1-40** "is the colony here" is worked out again inside parts in the middle of a tick
- **R1-41** the real queen's founding batch has no away guard, unlike her stream laying
- **R1-49** an insect or queen that changes dimension is never taken off its colony
- **R1-50** small things that reset or never clear

**Round 2, the legs** (`docs/reviews/full/round-02-legs.md`)

- **R2-4** the shared-route code is used by nothing in play
- **R2-5** log and stopwatch texts are built on every plan, wanted or not
- **R2-6** after a reload a held insect's heading, ceiling side and channel wall are gone
  - *Half fixed:* the heading is saved; the ceiling side and the channel wall are not.
- **R2-10** the shuffle can move an insect 1.5 blocks, its comment says 0.7; its wall check looks at one point
- **R2-12** a sideways crawl ends over a tunnel opening
- **R2-13** worked out again every tick: the doorway's point list, and up to 64 slope reads
- **R2-15** tiny correction moves never count as blocked
- **R2-23** the search's guess of the distance left can be too high, so a route is not always the cheapest
- **R2-25** throwaway objects in the search's inner loop
- **R2-26** small tidy-ups
- **R2-27** what no unit test covers in the planner
  - *Half fixed:* one gap closed: `aWindowSealedInFrontOfAnInsectIsNoLongerAWayInOrOut` (R2-19). The others are open.
- **R2-34** on the server "is it climbing" reads the value sent to the drawing, a tick late
- **R2-35** comments in the wrong place or saying the wrong thing
- **R2-36** gaps in `DoorwayMotionTest`

**Round 7, the giant trees** (`docs/reviews/full/round-07-trees.md`)

- **R7-8** Dead shapes do the living tree's work again
- **R7-9** Small waste in busy places
- **R7-21** Data that is not checked
- **R7-22** The fall is drawn from the flat-ground shape
- **R7-23** Minecraft's trees in a small savanna are thinned by the grass
- **R7-25** Leftover and trial code, comments that no longer match
- **R7-34** Small block slips
- **R7-35** Do palm seedlings stand on their ground?
- **R7-36** Tree blocks: unused code, old comments, missing tests

**Round 8, the world** (`docs/reviews/full/round-08-world.md`)

- **R8-4** pans and oxbows cut holes in tree crowns that stand in land made earlier
- **R8-5** the river bank's look depends on which chunk was made first
- **R8-6** the spawn search reads one row of columns from the wrong chunk
- **R8-8** a log line for every site, laid or not
- **R8-9** two small leftovers between worlds
- **R8-13** a mound place reads 13 columns of ground before it asks which biome it is in
- **R8-15** the colonies' insects count as Minecraft's farm animals
- **R8-16** algae can creep out of the savanna
- **R8-17** notes that no longer match the code
- **R8-20** small things

**Round 9, the river hunters** (`docs/reviews/full/round-09-river-hunters.md`)

- **R9-3** the guard goal searches for intruders when it has none to look for, and twice a turn when it runs
- **R9-4** small things in the kit that add up or mislead
- **R9-11** a crocodile hit while afloat does not react, and a nest mother shot from afar stands still
- **R9-12** small things in the crocodile
- **R9-14** dung on land almost never happens
- **R9-20** small things in the hippo
- **R9-26** small things in the shark
- **R9-36** small things in the otter
- **R9-43** three small behaviours that do not end the way they should

**Round 10, the river's small life** (`docs/reviews/full/round-10-river-small-life.md`)

- **R10-3** the fly clouds' cap is written as 6 in one place and is 18
- **R10-6** small things that disagree or are test-only
- **R10-7** thirteen spawning groups of our own, each tried in every chunk every tick
- **R10-11** frog and tadpole small things
- **R10-17** the snail's crawl and rest clips flicker (BUGS 250's likely cause)
- **R10-19** crab and snail small things

**Round 11, the new raid code** (`docs/reviews/full/round-11-raids.md`)

- **R11-18** the way-home list's 5-minute timeout is covered by the 5-second sweep
- **R11-19** work done more often than needed
- **R11-20** small bookkeeping
- **R11-21** comments and notes that disagree with the code

**Round 12, the animation engine** (`docs/reviews/full/round-12-animation-engine.md`)

- **R12-17** pair_report's "a tick" numbers mix in the in-between time
- **R12-18** the joint-turn check reads the keys as straight lines and skips a loop's seam
- **R12-19** small mismatches between what is judged and what is drawn
- **R12-29** any edit to `termite_models.py` or `body_plan.py` reruns every insect's lab check
- **R12-30** the kit's cache is used by the monitor only; the newer makers rebuild every clip every run
- **R12-31** `quadruped_gait` lets the feet step at different ground speeds without a word
- **R12-33** the lab's catmull-rom curve is not GeckoLib 4.9.3's
- **R12-34** small tidy-ups
- **R12-51** the frame-time meter re-sorts every frame of the launch each second
- **R12-52** a contact sheet of one burst swallows another burst that starts with the same name
- **R12-53** `film.sh` waits "successfully" after a failed reload
- **R12-54** the step marker turns every chat system message into text in normal play
- **R12-55** the report key's picture can be moved while it is still being written
- **R12-56** small miscounts in the one command's summary
- **R12-57** two names for the camera's in-between time
- **R12-66** the film watch's "reload the move files" swaps the moves under live plays
- **R12-67** client-only and removed-species code inside the framework's shared package
- **R12-68** a missing spot table is silently no table
- **R12-69** the dragged tail does all its ground reading even when it has no say
- **R12-73** the hippo's climb-out paths are not one row a tick, and the move writer never checks

## Done

Fixed, or found to be no bug after all. The number after a finding is its entry in `docs/BUGS.md`.

**Round 1, the colony brain**

- **R1-1** (A) a Matabele queen never lays while a player is near (BUGS 392)
- **R1-2** (A) a broken species file crashes the game while the log says "using the built-in numbers" (BUGS 387)
- **R1-7** (A) the new-holes check reads a far food block in land that is not loaded (BUGS 390)
- **R1-8** (A) `ForagingHoles.isOpen` reads the block before it asks whether the land is loaded (BUGS 390)
- **R1-9** (A) "is anyone still outside" looks only 32 blocks round the nest (BUGS 393)
- **R1-14** (B) a found plant far out is read with no load check once its finder is home (BUGS 390)
- **R1-16** (A) every builder looking for work scans the whole nest for leaks (BUGS 394)
- **R1-17** (A) on return the nest ledger does a whole second's changes in one tick (BUGS 395)
- **R1-18** (A) reading a cell's neighbours can load land that is not ready (BUGS 390)
- **R1-19** (A) a foraging tunnel not fitted to a hillside the first time is never fitted again (BUGS 391)
- **R1-20** (A) while no nest plan fits, the room planner runs on every call and a shaft is dug anyway (BUGS 396)
- **R1-21** (B) `nestCells` is rebuilt on every call (BUGS 394)
- **R1-24** (C) `Nest.isEntranceOpen` reads the block before asking whether the land is ready (BUGS 390)
- **R1-30** (B) one colony that fails to load wipes every colony of the dimension (BUGS 387)
- **R1-35** (A) sleeping in a bed takes the termites' foraging night away, while their hunger runs on (BUGS 397)
- **R1-36** (B) builder numbers do nothing near a player and bank work without end while away (BUGS 398)
- **R1-42** (A) one typo in a species file empties every colony of the world (R1-2 and R1-30 joined) (BUGS 387)
- **R1-43** (A) an insect already folded into the numbers is folded a second time (BUGS 388)
- **R1-44** (A) every autosave and every pause rebuilds and copies every colony's whole record
- **R1-45** (A) a minor that comes out when a number scout is shown goes back into the numbers, but its body stays (BUGS 389)

**Round 2, the legs**

- **R2-1** (B) the legs never remember a failed search, so every mind must bring its own retry clock (BUGS 400)
- **R2-2** (B) three new plans are all a walk gets, however far it got, and nobody hears the give-up (BUGS 404)
- **R2-3** (B) an insect falling more than 12 blocks beside a wall is taken for "shut in" and snapped onto the wall (BUGS 403)
- **R2-7** (C) `mayHaveGoal` asks the mind's test about twice as often as it needs
- **R2-8** (B) the standing shuffle's search for a free spot can run every tick for good (BUGS 405)
- **R2-9** (B) the stuck clock counts polite waiting, so a walker in a jam plans again and gives up (BUGS 404, 409)
- **R2-11** (C) the "no mouth any more" checks in the mouth moves can never fire, and would do the wrong thing
- **R2-14** (C) `unsqueeze` can divide by a column width of 0
- **R2-17** (B) one search reads the same blocks 50 to 60 times per step (BUGS 399)
- **R2-19** (B) a tunnel window sealed in front of an insect is not seen by the every-tick check (BUGS 407)
- **R2-20** (B) a goal cell with nothing to hold in it is searched to the cap (BUGS 399)
- **R2-21** (B) common part-blocks are neither ground nor passable (BUGS 412)
- **R2-22** (B) two static maps keep a closed world in memory (BUGS 406)
- **R2-24** (C) comments that disagree with the code
- **R2-28** (A) a frozen insect on a wall slides down it and is drawn as climbing (BUGS 402, 410)
- **R2-30** (B) nothing can knock an insect off a wall, and nowhere says that is meant (BUGS 413)
- **R2-31** (C) every insect pushes the mobs round it every tick, though nothing can push it
- **R2-32** (C) `AntMoveControl` turns to face east when the point is straight above or below
- **R2-33** (C) "stepped up close" outlives `stop()` *Not a bug after all.*
- **R2-37** (A) five goals ask the legs for a route on every tick after "no way" (BUGS 400)
- **R2-38** (A) an insect on a path block, on farmland or in a pond with a low bank can never plan a route again (BUGS 401, 411, 412, 415)
- **R2-40** (B) the "no foothold in reach, no search" shortcut helps one caller only
- **R2-41** (B) saving an insect that is walking a wall reads the world and loses its hold
- **R2-42** (B) four tests of "is this a doorway for this insect"
- **R2-43** (B) knocked into a cell near its route, an insect finds out only after 6 seconds (BUGS 413)

**Round 7, the giant trees**

- **R7-1** (A) Catching up on missed days counts the ground under the whole crown once for every missed day (BUGS 339)
- **R7-10** (A) "Char, never vanish" against "brown leaves burn away": a fire can eat a living crown (BUGS 340)
- **R7-11** (A) A falling tree breaks through a termite mound (BUGS 341)
- **R7-15** (A) New land's trees can miss the tree book (BUGS 342)
- **R7-16** (A) A rot-killed log on a slope leaves wood hanging (BUGS 343)
- **R7-17** (A) A dead sapling leaves a stick sunk in the ground (BUGS 344)
- **R7-27** (A) Every fig branch is a full cube (BUGS 345)
- **R7-28** (A) Shears on a fruit bunch: endless fruit (BUGS 346)
- **R7-29** (A) Animals do not know thorny twigs hurt (BUGS 347)

**Round 8, the world**

- **R8-2** (A) a 129-block search for animals on every spawn try *Not a bug after all.*
- **R8-11** (A) mounds of new land are lost when the game is left, and can turn up in the next world (BUGS 321)
- **R8-18** (A) ravines and caves still come into the savanna along its rivers (BUGS 322)

**Round 9, the river hunters**

- **R9-1** (A) a courting animal whose suitor dies or leaves the loaded land is "taken" for good (BUGS 323)
- **R9-5** (A) the crocodile hunts the player's own animals: tamed horses, donkeys, llamas, name-tagged and leashed stock (BUGS 324)
- **R9-6** (A) in Peaceful the crocodile still seizes the player and holds them through twelve rolls (BUGS 325)
- **R9-27** (A) a catch in the jaws is saved with the otter, but the otter forgets it: after a reload it is stuck at the snout for good (BUGS 326)
- **R9-28** (A) carrying a catch has no time limit: with no bank in reach the otter floats on the spot with it for ever (BUGS 327)
- **R9-29** (A) only the otter that started a game of play can end it: if that one is killed or vanishes mid-game the other stands in the game for good (BUGS 328)
- **R9-30** (A) the otter is the only river animal Minecraft may remove 32 blocks from the player: they vanish one at a time (BUGS 329)
- **R9-37** (A) a staged move that is stopped on its very first tick freezes the monitor in place, and starts again every tick (BUGS 330)
- **R9-38** (A) the walk into the water is cut short for 7 monitors in 8 (BUGS 331)
- **R9-41** (B) the monitor never counts its life clock (nor does the hippo); the kit leaves that to each animal to remember (BUGS 335)

**Round 10, the river's small life**

- **R10-1** (A) the world's spawner and breeding fight for the same places, and the spawner wins (BUGS 337)
- **R10-2** (B) a mate that dies or unloads in the middle of courtship leaves its partner "taken" for good (BUGS 323)
- **R10-8** (A) a bullfrog climbing out of the water can be left hanging with no gravity, for good (BUGS 333)
- **R10-9** (A) every tadpole tells every player its age every tick (BUGS 334)
- **R10-12** (A) crabs, snails and catfish never grow up and breed only once in their lives (BUGS 335)
- **R10-13** (A) a crab's meal does not count when the food ends at the first bite (BUGS 336)

**Round 11, the new raid code**

- **R11-1** (A) a scout setting out reads blocks in land that is not loaded (BUGS 316)
- **R11-2** (A) the scout searches for a route every tick when it has no way (BUGS 317)
- **R11-3** (A) the attack's "quiet" rule is kept awake by a termite nobody can reach (BUGS 318)
- **R11-4** (A) a raid the player is watching is folded into numbers when the nest's chunk unloads (BUGS 319)
- **R11-5** (A) a raid as numbers takes real termites beside the player, with no ant in sight (BUGS 320)
- **R11-6** (B) two judges of "termites here": the scout counts soldiers, the raid does not
- **R11-7** (B) a raid shown again after going to numbers bunches as a hollow ring
- **R11-8** (B) a raider called back by the leash may never fight again
- **R11-9** (B) the raid's size is never lowered for real ants that leave it
- **R11-10** (B) a raid shown "in the nest" from the halt, attack or regroup starts over
- **R11-11** (B) in the colony's away grace minute nobody steps the raid
- **R11-12** (B) a scout drafted to defend the nest stalls the whole march
- **R11-13** (B) a waiting find whose land does not tick stops all scouting until it fades
- **R11-14** (B) a waiting find writes a diary line every second outside the raid hours
- **R11-15** (B) a find on its way home is lost on a reload *Not a bug after all.*
- **R11-16** (B) `Scouts.mayScout` lets any ant on the out list through unchecked
