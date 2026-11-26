# Code review bugs

Every finding of the full code review (`docs/plans/code-review.md`) and where it stands: what is left on
top, what is done at the bottom. **This file is written by `python tools/review_bugs.py`; never edit it by
hand.** The findings in full (what is wrong, what the player sees, the proof, the fix proposed) are in the
review files under `docs/reviews/full/`; search there for a finding's number (`R2-16`). A fix session writes
a `**Status ...**` line under the finding there and runs the tool. Bugs that did not come from the code
review stay in `docs/BUGS.md`.

**234 findings: 167 left, 67 done.**

| Grade | Found | Not started | Open | Half fixed | Fixed | Not a bug after all |
|---|---|---|---|---|---|---|
| A: a player can meet it | 51 | 0 | 2 | 5 | 43 | 1 |
| B: it will cost us when building on this | 106 | 86 | 1 | 3 | 16 | 0 |
| C: a tidy-up | 77 | 68 | 0 | 2 | 6 | 1 |

| Round | System | Found | Left | Done |
|---|---|---|---|---|
| 1 | the colony brain | 50 | 32 | 18 |
| 2 | the legs | 43 | 25 | 18 |
| 3 | the insect body | not read yet | | |
| 4 | how bodies are drawn | not read yet | | |
| 5 | the termites | not read yet | | |
| 6 | the Matabele ants (not raids) | not read yet | | |
| 7 | the giant trees | 36 | 28 | 8 |
| 8 | the world | 20 | 17 | 3 |
| 9 | the river hunters | 44 | 35 | 9 |
| 10 | the river's small life | 19 | 13 | 6 |
| 11 | the new raid code | 22 | 17 | 5 |

## Left to do

Grade A first. *Open* means looked at and left for a reason (the reason is given); *half fixed* says what
is left; no word means nobody has started it.

### A: a player can meet it (7 left)

**Round 1, the colony brain** (`docs/reviews/full/round-01-colony-brain.md`)

- **R1-20** while no nest plan fits, the room planner runs on every call and a shaft is dug anyway
  - *Half fixed:* BUGS 396 (the planner waits 40 ticks; the shaft with no plan is left).
- **R1-44** every autosave and every pause rebuilds and copies every colony's whole record
  - *Open:* to measure first; marking the file changed less often gains little, since an away colony's numbers change every second; the size (`FieldLife`'s plants) is the lever.

**Round 2, the legs** (`docs/reviews/full/round-02-legs.md`)

- **R2-16** a search that finds no way costs about 20 ms, most termite walks run it twice, and nothing limits how many run in a tick
  - *Half fixed:* fixed in part, BUGS 399: no second search when no habit had a say in the first (test `askingAgainAfterNoWayCostsNoSearch`); with R2-1, R2-20 and R2-17. Measured in that test, a search that finds no way on open flat ground: 19.5 ...
- **R2-38** an insect on a path block, on farmland or in a pond with a low bank can never plan a route again
  - *Half fixed:* small half fixed, BUGS 401: a start one step up beside it, corners too, and the shuffle no longer moves an insect onto ground the planner refuses (test `anInsectOnAPathBlockWalksOffIt`). The path half fixed after, BUGS 412 ...

**Round 7, the giant trees** (`docs/reviews/full/round-07-trees.md`)

- **R7-15** New land's trees can miss the tree book
  - *Half fixed:* (2026-11-24, BUGS 342). The waiting list is taken into the book as a world closes (and the book written once more) and emptied when the server stops; each second every waiting tree of the world is taken, not only those at the ...
- **R7-33** A palm's crown can blink out at the screen's edge
  - *Open:* waits to be seen (2026-11-24). Read from how Minecraft hides parts of the world, never seen in the game; the fix is large (sharing a crown's faces out over its blocks) and is built only if the user sees fronds blink out.

**Round 9, the river hunters** (`docs/reviews/full/round-09-river-hunters.md`)

- **R9-37** a staged move that is stopped on its very first tick freezes the monitor in place, and starts again every tick
  - *Half fixed:* open (2026-11-24, BUGS 330). Done: the room checks for a climb up, a climb down and a climb out ask for a whole block under both feet and on the middle line, the block the move itself relies on (as the walk into the water already ...

### B: it will cost us when building on this (90 left)

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

- **R2-2** three new plans are all a walk gets, however far it got, and nobody hears the give-up
  - *Half fixed:* BUGS 404: new plans are counted since the last finished step. Open: `Errand` still does not read `hasFailed()` (a change to what `walkOn` means for every goal; left for round 3's fixes), and the landing check still uses ...
- **R2-18** big step costs fight the fixed cap, and the mud test hides it
- **R2-29** the way through a chimney mouth still uses one body's lengths
- **R2-30** nothing can knock an insect off a wall, and nowhere says that is meant
- **R2-39** a mind pays for the same "no way" again a tick later, and a builder can ask 24 times in one tick
  - *Half fixed:* covered in part by R2-1 (BUGS 400): `Errand`'s second ask a tick later costs no search when the walk had no costs of the mind's own. `BuildMoundGoal`'s many asks in one tick are open (round 5).
- **R2-41** saving an insect that is walking a wall reads the world and loses its hold
- **R2-42** four tests of "is this a doorway for this insect"
- **R2-43** knocked into a cell near its route, an insect finds out only after 6 seconds

**Round 7, the giant trees** (`docs/reviews/full/round-07-trees.md`)

- **R7-2** The once-a-second walk looks at every tree in the world, and always starts from the same end
- **R7-3** A tree's growth step is one tick's work, and all trees come due at the same dawn
- **R7-4** A storm breaking a hollow tree works out three shapes on the server's own thread
- **R7-5** The fall in sight: up to 2,500 entities made in one tick
- **R7-6** The whole tree book is written at every autosave, and it only grows
- **R7-7** Four shape stores are never emptied
- **R7-12** Lightning takes big trees faster than old age
- **R7-13** Seedlings after rain fill the savanna in
- **R7-14** Termites hollow baobabs, which nobody decided
- **R7-18** One bad record empties the tree book
- **R7-19** A fallen log's record never ends
- **R7-20** Felling checks the foot's chunk only
- **R7-24** The tree kind files are not the "numbers I can tweak" they look like
- **R7-26** Test gaps on what the player sees
- **R7-30** The river animals' ground finder stops on top of a crown
- **R7-31** A cut tree's crown hangs for good
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
- **R9-24** a shark hit from the bank or from a boat does nothing
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
- **R10-5** river algae spreads into any shallow water, with nothing to stop it
- **R10-10** the two frog steps before breeding can end with nothing, and a hungry frog searches wide 5 times a second
- **R10-14** the crab's 20-second limit on threatening does nothing
- **R10-15** asking for a new path every tick to a spot that cannot be reached
- **R10-16** snail eggs within a crab's reach never hatch
- **R10-18** small searches that run more often than they need

**Round 11, the new raid code** (`docs/reviews/full/round-11-raids.md`)

- **R11-6** two judges of "termites here": the scout counts soldiers, the raid does not
- **R11-7** a raid shown again after going to numbers bunches as a hollow ring
- **R11-8** a raider called back by the leash may never fight again
- **R11-9** the raid's size is never lowered for real ants that leave it
- **R11-10** a raid shown "in the nest" from the halt, attack or regroup starts over
- **R11-11** in the colony's away grace minute nobody steps the raid
- **R11-12** a scout drafted to defend the nest stalls the whole march
- **R11-13** a waiting find whose land does not tick stops all scouting until it fades
- **R11-14** a waiting find writes a diary line every second outside the raid hours
- **R11-15** a find on its way home is lost on a reload
- **R11-16** `Scouts.mayScout` lets any ant on the out list through unchecked
- **R11-17** near and away still differ in how often a colony raids

### C: a tidy-up (70 left)

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
- **R2-11** the "no mouth any more" checks in the mouth moves can never fire, and would do the wrong thing
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
- **R9-19** several small things, listed in the review file
- **R9-20** small things in the hippo
- **R9-26** small things in the shark
- **R9-36** small things in the otter
- **R9-43** three small behaviours that do not end the way they should
- **R9-44** small things in the monitor

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
- **R11-22** numbers

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
- **R1-21** (B) `nestCells` is rebuilt on every call (BUGS 394)
- **R1-24** (C) `Nest.isEntranceOpen` reads the block before asking whether the land is ready (BUGS 390)
- **R1-30** (B) one colony that fails to load wipes every colony of the dimension (BUGS 387)
- **R1-35** (A) sleeping in a bed takes the termites' foraging night away, while their hunger runs on (BUGS 397)
- **R1-36** (B) builder numbers do nothing near a player and bank work without end while away (BUGS 398)
- **R1-42** (A) one typo in a species file empties every colony of the world (R1-2 and R1-30 joined) (BUGS 387)
- **R1-43** (A) an insect already folded into the numbers is folded a second time (BUGS 388)
- **R1-45** (A) a minor that comes out when a number scout is shown goes back into the numbers, but its body stays (BUGS 389)

**Round 2, the legs**

- **R2-1** (B) the legs never remember a failed search, so every mind must bring its own retry clock (BUGS 400)
- **R2-3** (B) an insect falling more than 12 blocks beside a wall is taken for "shut in" and snapped onto the wall (BUGS 403)
- **R2-7** (C) `mayHaveGoal` asks the mind's test about twice as often as it needs
- **R2-8** (B) the standing shuffle's search for a free spot can run every tick for good (BUGS 405)
- **R2-9** (B) the stuck clock counts polite waiting, so a walker in a jam plans again and gives up (BUGS 404, 409)
- **R2-14** (C) `unsqueeze` can divide by a column width of 0
- **R2-17** (B) one search reads the same blocks 50 to 60 times per step (BUGS 399)
- **R2-19** (B) a tunnel window sealed in front of an insect is not seen by the every-tick check (BUGS 407)
- **R2-20** (B) a goal cell with nothing to hold in it is searched to the cap (BUGS 399)
- **R2-21** (B) common part-blocks are neither ground nor passable (BUGS 412)
- **R2-22** (B) two static maps keep a closed world in memory (BUGS 406)
- **R2-24** (C) comments that disagree with the code
- **R2-28** (A) a frozen insect on a wall slides down it and is drawn as climbing (BUGS 402, 410)
- **R2-31** (C) every insect pushes the mobs round it every tick, though nothing can push it
- **R2-32** (C) `AntMoveControl` turns to face east when the point is straight above or below
- **R2-33** (C) "stepped up close" outlives `stop()` *Not a bug after all.*
- **R2-37** (A) five goals ask the legs for a route on every tick after "no way" (BUGS 400)
- **R2-40** (B) the "no foothold in reach, no search" shortcut helps one caller only

**Round 7, the giant trees**

- **R7-1** (A) Catching up on missed days counts the ground under the whole crown once for every missed day (BUGS 339)
- **R7-10** (A) "Char, never vanish" against "brown leaves burn away": a fire can eat a living crown (BUGS 340)
- **R7-11** (A) A falling tree breaks through a termite mound (BUGS 341)
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
