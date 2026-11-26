# Round 10: the river's small life and the animal life cycle (reading round, 2026-11-23/24)

**What was read:** the shared life kit (`framework: animal/` `AnimalLife`, `AnimalLives`, `LifeNumbers`, `RoomToBreed`,
`SeekMateGoal`, `Carried`, `ClutchClock`, `EggClutchBlock`, `EggClutchBlockEntity`, `HasLife`), the life files
(`data/antcolonies/animal_life/`), the spawning groups and spawn lists of the small animals, and in `savanna: animal/`
the tilapia, catfish, barb, distichodus, bullfrog, tadpole, crab, snail, fly clouds, remains, the egg and algae blocks
and the river helpers. No code changed, no test run (the rule of a reading round). Lag findings could not be measured
tonight (other sessions had the test servers): each says which scene would measure it and is marked **to measure**.

**Status of this file:** written piece by piece. A finding is **proven** (the path through the code walked, lines
named), **likely**, or dropped. Ratings: **A** the player can meet it, **B** it will cost us when building on this,
**C** tidy-up.

---

## Piece 1: the shared life kit and the fish

### R10-1 (A, proven; its size in play to measure): the world's spawner and breeding fight for the same places, and the spawner wins

**What is wrong.** An animal may court, and its eggs may hatch, only while its spawning group is under its cap where it
is (`RoomToBreed.groupHasRoom`). But Minecraft's own spawner tries every tick, in every chunk round a playing player,
to fill that same group up to that same cap, and the river animals are kept near the player, so the group sits at its
cap nearly all the time (the project's own notes say so: BUGS 224 "fill it for good"; `HuntTilapiaGoal`'s comment "the
river fish group of 45 ... refills from natural spawning"). So with a player playing (creative or survival) the answer
to "is there room to breed" is almost always no. With only a spectator watching, the cap rule is switched off on
purpose (`groupHasRoom` returns true when no counting player is near) and only the local limit holds. The game tests
have no player, so they take the spectator's path and pass.

**What the player would see.** Playing normally: hardly any courtship, tilapia males that almost never fan a pit,
tilapia mothers whose eggs vanish at hatching time, egg clutches (snail, catfish, frog) that sit for 10 game days and
then turn to empty shells. Watching in spectator: lots of breeding, up to each kind's local limit. Then, switching
back to creative, a river over its cap in which nothing new spawns until the extra animals have gone.

**Where.**
- `framework/.../animal/RoomToBreed.java:32-37` (`here` asks the group cap), `:65-79` (`groupHasRoom`; true with no
  counting player, lines 66-69).
- `framework/.../animal/SeekMateGoal.java:45` (no courtship without room).
- `savanna/.../animal/TilapiaNestGoal.java:72` (no nest pit without room).
- `savanna/.../animal/TilapiaEntity.java:394-400`: at hatching time, one single check; with no room that tick
  `eggs = 0`: the whole brood is thrown away, never retried.
- `framework/.../animal/EggClutchBlock.java:57-60` (`roomFor`: a clutch hatches only with group room at the instant of
  its random tick, about once every 68 s), `ClutchClock.java:39` (else empty shells after 10 days).
- `savanna/src/main/resources/META-INF/enumextensions.json` (the caps: river fish 45, river bed 24, bullfrog 4,
  tadpole 16) against the local limits in the life files (tilapia 30, catfish 6, crab 12, snail 10, bullfrog 4);
  `worldgen/RiverZones.java:66-78`: tilapia, catfish, snail, bullfrog have a share of 1 (the whole cap) in their main
  zones, so the spawner stops only at the cap itself.

**Proof (walked).** `ServerChunkCache.tickChunks` calls `NaturalSpawner.spawnForChunk` for every spawnable chunk every
tick for a group that is not "persistent" (ours are made with `false`), while `canSpawnForCategory` is true, the very
method `RoomToBreed` asks by reflection (`RoomToBreed.java:103`). So "room for the spawner" and "room to breed" are the
same yes or no, and the spawner asks it about 290 times a tick against a courting animal's once in 40 ticks
(`SeekMateGoal.LOOK_EVERY`) or a clutch's once in about 68 s. Whenever a place opens (a fish eaten), the spawner takes
it within a second or so where the river has spawn spots. The bullfrog is the clearest case: cap 4, local limit 4,
zone share 1 in pans and oxbows.

**To measure:** a creative (not spectator) player beside a near-inland river for 3 game days, in rain for the catfish
and frogs: count nest pits, matings and hatchings (the `[life]` lines or a counter), then the same in spectator.

**Proposed fix (changes how the game plays: the user's call, with a recommendation).** One rule should own each place,
not two. Recommended: let breeding be the main way a river refills. Breeding and hatching ask only the local limit
(which already cannot run away, R10 solid parts), and the spawner is held back to a share of each cap (say half) through
`RiverZones.room`, so it seeds empty water and breeding fills the rest. Also: a tilapia mother with no room keeps her
eggs and asks again (or lets out what fits) instead of losing the brood on one unlucky tick. And add a game test with a
real counting player (a fake player in survival near the scene) so the playing path is tested, not only the spectator's.

**Status: fixed** (2026-11-24, BUGS 337; the user: "Yes, breeding refills"). Breeding and hatching ask only the kind's local limit (`RoomToBreed`; the group-cap question and its reflection deleted, so a playing player and a spectator now get the same answer and the tests run the playing path); the world's spawner fills a kind that breeds only to half its count in a zone (`RiverZones.SPAWNER_SHARE`, `mostBySpawner`); a tilapia mother with no room keeps her eggs and asks again each second, up to the clutch clock's 10 days. Tests `aTilapiaMotherWithNoRoomKeepsHerEggsUntilThereIs`, `theSpawnerLeavesHalfOfABreedingKindsCountToBreeding`. Not measured in play: how full a river settles (the balance soak).

### R10-2 (B, proven by walked path): a mate that dies or unloads in the middle of courtship leaves its partner "taken" for good

**What is wrong.** When courtship starts, both animals are marked as each other's partner
(`SeekMateGoal.start`, lines 63-64). The mark is taken off in the seeker's `stop()` (lines 70-73) or by `bred()`. But a
mob that dies or is unloaded never has its goals stopped, so if the seeker goes first, the one it was courting keeps the
dead one's id as its partner. Nothing else clears it (every use of `setPartner` is in `SeekMateGoal`); it is not saved,
so it lasts until that animal's chunk is reloaded. A partner mark means: no other suitor may pick it
(`SeekMateGoal.java:122`).

**What the player would see.** A tilapia male (he never seeks, he waits on his pit, `TilapiaEntity.seeksMate`) whose
courting female is caught by a catfish, crocodile or otter over his pit: he stops guarding (`TilapiaNestGoal.hold`
returns early at line 154 while he has a partner: no rival chasing, no keeping station), and no female can ever choose
him again until the area is reloaded. For a catfish the same stale mark also stops it hunting
(`HuntTilapiaGoal.java:64`) until its next own courtship. Courting pairs over a nest pit are exactly where hunters strike.

**Proof.** `SeekMateGoal.java:63-64` sets both; `:70-73` is the only clearing besides `AnimalLife.bred` (`:246`);
goals are stopped only by the goal selector's tick, which a dead or removed mob no longer runs.

**Proposed fix.** Clear a partner mark by itself: in `AnimalLife.tick` (or where `partner()` is read), drop a partner
whose mob is gone, dead or farther than the mate reach, checked once a second.

**Status: fixed** (2026-11-24, BUGS 323, with R9-1: the same fault; test `aCourtedAnimalWhoseSuitorDiesOrVanishesIsFreeAgain`).

### R10-3 (C, proven): the fly clouds' cap is written as 6 in one place and is 18

`registry/SavannaMobCategories.java:53-56` says "Cap 6 round a player"; `META-INF/enumextensions.json` gives
`ANTCOLONIES_FLY_CLOUD` 18, and `FlyCloudEntity.java:44-46` says 18 (tripled 2026-11-20). Fix the comment (and check
the groups table in `docs/LIVING-THINGS.md` says 18).

---

## Piece 2: the other fish, flies, remains, algae and the egg blocks

### R10-4 (B, likely; to measure): catfish eggs are eaten long before they can hatch

**What is wrong.** A catfish female scatters 6 to 10 eggs over algae mats, 2 to 4 a mat, and they need a full game day
(20 minutes) to hatch. Every hungry tilapia goes for eggs before any algae (`GrazeAlgaeGoal.EGGS_FIRST = 20`, the
"best of all"), looks 8 blocks round, takes 2 eggs a bite (`AlgaeBlock.EGGS_A_BITE`), and is hungry again every 2 to 4
minutes. Catfish spawn in the same water as the tilapia (the same spawning group), where 15 or more tilapia are within
reach of a mat. By these numbers a whole spawning is gone in a minute or two; the 20 minutes are never reached. Crabs
eat them too. On top of this the hatching needs room (R10-1).

**What the player would see.** Catfish courting on rainy nights, eggs on the algae, tilapia crowding them, and never a
young catfish.

**Where.** `savanna/.../animal/GrazeAlgaeGoal.java:139-149` (eggs worth 20 against a lush mat's 8), `:30` (hungry
every 2400 to 4800 ticks); `block/AlgaeBlock.java:222-233, 254`; `animal_life/catfish.json` (`hatch_days: 1`,
`clutch: [6, 10]`); `CatfishScatterEggsGoal.java:29` (2 to 4 a mat).

**Proof.** Numbers only, not watched in play: mark **likely**. Scene to measure: the catfish breeding test's pond with
a school of 15 hungry tilapia added; count eggs left after 2, 5 and 20 minutes.

**Proposed fix (balance, my pick unless the user says otherwise).** Make eggs a find, not a magnet: worth no more than
a lush mat (so a fish eats them only when they are the nearest good food), or let a tilapia notice eggs only within 3
blocks. Keep the life file's hatch time.

**Your call (2026-11-26):** Catfish eggs and tilapia. Today every hungry tilapia goes for catfish eggs before any other food from 8 blocks off and takes 2 a bite, so by the numbers (not watched in play) a whole spawning is eaten in a minute or two and no catfish ever hatches from eggs (the world's spawner still refills them). Make eggs a find, not a magnet: a tilapia eats them only when they are the nearest good food? My pick: yes.

**Call made (2026-11-26):** yes, my pick.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: eggs are a find, not a magnet. They add nothing to the worth of the mat they lie on, and alone they are worth a thin mat (`GrazeAlgaeGoal.EGGS` 2, was 20 on top), so a tilapia goes to the thickest mat near it as always and eats eggs only where it grazes. Not measured: the scene with 15 hungry tilapia and a count of eggs left.**

### R10-5 (B, proven path; its speed to measure): river algae spreads into any shallow water, with nothing to stop it

**What is wrong.** A thick or lush mat seeds bare bed beside it on 1 random tick in 10 (`AlgaeBlock.spread`). The only
conditions are still water on a sturdy floor. There is no check for the biome, for the savanna, or for how much algae is
already there, and a grazed mat never goes away (growth 0 stays as a block). In water 1 to 4 deep a seeded mat grows
thick and spreads on. So algae creeps out of the savanna's rivers along any connected shallow bed: lake shores, sea
shores, another biome's river, a player's pond or water garden joined to a river.

**What the player would see.** Over many hours near a base: green mats over every shallow bed, also outside the
savanna, and growing back after being cleared unless every last bare speck is broken. No lag (a mat is a plain block).

**Where.** `savanna/.../block/AlgaeBlock.java:187-189` (the chance), `:193-204` (the spread; only `isLoaded`, water
source and `canSurvive`), `:134-137` (thick only in water up to 4 deep, so deep channels stop it).

**Proof.** Walked: `randomTick` → `spread` → `setBlock(to, bare)`; `mayPlaceOn` (`:88-90`) takes any sturdy top but
magma. About 17 random ticks a day × 1/10 × the share of sides that fit: a front of roughly a block every 2 or 3 game
days where ticked (to measure in a long soak: count algae blocks in a fenced pool over 30 days).

**Proposed fix (looks: the user's call).** Recommended: spread only inside the savanna's waters (the biome tag the
world's own algae feature uses), and only while fewer than some number of mats lie in the 5 by 5 round the new spot, so
beds stay patchy as new land lays them.

**Your call (2026-11-26):** River algae spreading. Today a thick mat seeds any shallow still water beside it, with nothing to stop it: over many hours it creeps out of the savanna's rivers along lake and sea shores and into a player's pond joined to a river, and grows back unless every bare speck is broken. Keep it to the savanna's waters, and patchy (no new mat where enough lie round it)? My pick: yes.

**Call made (2026-11-26):** yes, my pick.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: a mat seeds a new one only in the savanna's own waters (the biome tag the world's algae feature uses) and only while at most 8 mats lie in the 5 by 5 round the new spot (`AlgaeBlock.roomToSpread`, `PATCH_MOST`, my pick). Not measured: the 30-day count in a fenced pool.**

### R10-6 (C, proven): small things that disagree or are test-only

- `TilapiaFryGoal.java:17, 23, 42`: the note says a fry whose mother is gone is on its own after 10 seconds; the count
  goes up once per goal check, and goals are checked every other tick, so it is 20 seconds.
- `AnimalLife.java:225-231`: `rainForTests`, a test's own weather, is a static switch inside the play code that every
  world in the same sitting shares. If a test run ever leaves it set, breeding weather is wrong in the next world.
  Move it behind the test kit.
- `TilapiaEntity.java:108` (`wantsNestNow`) and `:647` (`followedForTests`, which the real `leaveSchool` at `:368` also
  uses): test hooks the game relies on; rename the second, it is not for tests only.
- `BarbEntity.java:243-245` and `DistichodusEntity.java:239-242`: `getBucketItemStack` gives a water bucket though
  `mobInteract` refuses every bucket; dead code that would hand out free buckets if the refusal were ever lifted.

### R10-7 (C, to measure; belongs with round 8): thirteen spawning groups of our own, each tried in every chunk every tick

Every group in `META-INF/enumextensions.json` is made non-persistent, so Minecraft tries it in every chunk near a
player every tick while it is under its cap. Groups that can never fill where the player stands (river groups on the
open plain, the hippo group, which has no spawn list at all) are tried for ever. Each try is small (a random spot,
most are underground and end at once), but there are 13 groups × about 290 chunks a tick. Measure with the profiler on
the open savanna away from water; if it shows, the hippo group at least can be made a group the spawner skips. Noted
here because the small animals' groups are most of the thirteen.

---

## Piece 3: the bullfrog and the tadpole (read by a helper, each finding checked again against the code)

### R10-8 (A, proven): a bullfrog climbing out of the water can be left hanging with no gravity, for good

**What is wrong.** The climb-out switches the frog's gravity off (`BullfrogEntity.climbOut`, line 324) and only the
climb's own tick switches it back on (`climbTick`, lines 331 and 343), which runs only while the frog's action is
"climb out" (line 457). The threat goal does not wait for a climb to end: `ThreatGoal.canUse`
(`BullfrogGoals.java:729-742`) refuses only a leap, a froglet and a clasping pair, and its tick sets the action to
"puff" over whatever it was (`:846-848`). `act` (`BullfrogEntity.java:226-230`) and `stopActing` never touch gravity.
So a frog that starts puffing in the 16 ticks of a climb keeps no gravity for ever; Minecraft saves the no-gravity
mark with the mob, so it survives a reload too. On land its legs wait for the ground (`BullfrogMoveControl:77-78`), so
it never hops again. Two more doors to the same hole: a female's courtship seats a male who is mid-climb
(`BullfrogEntity.java:685-686`), and a save or unload inside the 16 ticks (the action is not saved, the mark is).
The escape leap's "no air drag" mark (`BullfrogGoals.java:225`, cleared only at `BullfrogEntity.java:461-463`) has the
same weakness, smaller.

**What the player would see.** In survival, standing within 3 blocks of a frog hauling itself onto the bank: a frog
frozen part way up, floating, puffed, that never moves again. (Not in creative or spectator: the frog ignores those.)

**Proof.** The lines above, walked; `grep setNoGravity` over the frog's files finds only lines 324, 331, 343.

**Proposed fix.** Do not use the saved no-gravity mark for a 16-tick move: clear no-gravity and no-drag in `act()` /
`stopActing()` for any action but the one that needs them, and on load; and make `ThreatGoal.canUse` refuse a climb.
Test first: a frog mid-climb with a survival player 2 blocks off must stand on the bank with gravity 2 seconds later.

**Status: fixed** (2026-11-24, BUGS 333; test `aBullfrogLeftWithoutGravityGetsItBack`), with one rule in the frog's own tick in place of the proposed three: "no gravity" is taken off whenever its action is not the climb out, "no air drag" whenever it is not the leap. That covers the puff, the clasp and a save in mid-climb alike, and mends a frog already left hanging in an old world. `ThreatGoal.canUse` was left alone (a puff may still cut a climb short: the frog then drops into the water).

### R10-9 (A, proven that it is sent; the cost to measure): every tadpole tells every player its age every tick

**What is wrong.** A tadpole's age is synced data and is counted up by one every tick
(`TadpoleEntity.java:179-181` → `setAge`, `:135-137`). The players' computers need one fact from it (leg buds from day
2). Synced data that changes every tick marks the mob as changed every tick, so the server sends a data packet for it
every tick and, in Minecraft's tracker, its position goes out every tick too instead of every third.

**What the player would see.** Nothing directly: more network and server work the more tadpoles and players there are
(16 to about 26 tadpoles in a pool: some 300 to 500 extra packets a second for each player near). It matters most in
multiplayer.

**To measure:** 24 tadpoles in a pool, one player 10 blocks off: packets a second (F3's network chart, or the debug
"tx" count) with and without the change.

**Proposed fix.** Keep the age a plain server field (it is already saved) and sync one "has legs" flag, set once.

**Status: fixed** (2026-11-24, BUGS 334; the old test `tadpolesGrowLegBudsThenBecomeFroglets`' checks still hold): the age is the server's own count (saved as before), one "has legs" flag is synced and changes once. The packets saved were not counted.

### R10-10 (B, proven): the two frog steps before breeding can end with nothing, and a hungry frog searches wide 5 times a second

- **Courtship ends on a timer, not at the water** (`SeekMateGoal.java:100-110`, `BullfrogEntity.java:698-708`): after
  16 seconds both are marked as bred, then she lays only if she is within 3 blocks of her laying spot. A pair that had
  to go round a bank part on land with no eggs, and she waits 4 days. How often: not known (to watch in play). Fix:
  count the courtship as done only once she is at the spot, with the kit's own give-up time.
- **The hungry frog's search has no rest** (`BullfrogGoals.java:328-349`): about every 4 ticks it lists every living
  thing in a box 21 blocks each way and casts sight lines, and a frog that finds nothing stays hungry, so it goes on
  for ever. Harmless at the cap of 4 frogs; a cost if the cap is ever raised. Fix: after an empty search wait a second
  or two, and make the box 3 high.
- **The father's guard goal searches for intruders it is told never to find** (`BullfrogGoals.java:97`,
  `framework/.../GuardPlaceGoal.java:41-49`): made with a see-off distance of 0 and a test that is always false, it
  still runs its search each check. Skip the search when the distance is 0. (C)

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): half fixed: a hungry frog that saw nothing to eat looks again a second later, in a box 3 high (`BullfrogGoals`, `lookAgain`), and a guard told to see nobody off searches for nobody (`GuardPlaceGoal`). Open: a courtship that ends on its timer away from the laying spot (how often is not known; to watch in play first).**

### R10-11 (C, proven): frog and tadpole small things

- `TadpoleEntity.java:275` says a tadpole hatched from eggs is kept for good; nothing in `FrogEggsBlock.hatch`
  (`:126-148`) marks it kept, so a father's school fades past 96 blocks like a wild one. Despawning is the safer choice
  for counts: correct the comment and drop the dead copy of the mark at `:211-213`.
- `BullfrogEntity.java:669-672, 709`: "takes a mate" is true for a male riding anything, so a male held in an otter's
  jaws may be courted and pulled free by `stopRiding`. Unsure it can last long enough; use "riding a bullfrog".
- A `frog_eggs` block placed from the creative menu has no mother, never hatches, and puts a water source where it is
  placed (`FrogEggsBlock.java:98-100`). Creative only.
- In peaceful mode the frog still puffs and lunges at a survival player, and the bite does nothing.
- The film hooks (`filmTick`, `filmed()`) read the frog's name every tick in normal play (`BullfrogEntity.java:475-483`).

**Frog eggs and R10-1:** a frog's eggs hatch only while the tadpole group is under its cap of 16
(`FrogEggsBlock.java:119-124`), and the world's own tadpole schools (6 to 10 a school) fill that cap; a tadpole becomes
a froglet only while the frog group is under its 4 (`TadpoleEntity.java:205-206`), which the world's own frogs fill.
The frog's whole life cycle is the clearest case of R10-1.

## Piece 4: crab and snail (and one finding that also covers the catfish)

Files read: `CrabEntity`, `CrabPickGoal`, `CrabThreatGoal`, the crab's avoid goals, `CrabShellBlock`, `SnailEntity`,
`SnailGrazeGoal`, `SnailHideGoal`, `SnailLayGoal`, `SnailEggsBlock`, `ScrapsBlock`, `GoExactly`, `crab.json`,
`snail.json`, `catfish.json`, and their tests in `AnimalLifeTests` and `RiverLifeTests`.

### R10-12 (A, proven): crabs, snails and catfish never grow up and breed only once in their lives

**What is wrong.** Every animal with a life keeps two clocks in the life kit: its age (how grown it is) and its rest
before the next breeding. Both clocks move only when the animal's own code calls the kit's `tick()` once a game tick.
The tilapia, the bullfrog, the crocodile and the otter do. **The crab, the snail and the catfish never do.**

**What the player would see.**
- A baby crab stays a 0.15-size baby for ever, a baby snail a quarter-size baby for ever, a young catfish a
  0.2-size youngster for ever. The young catfish also never starts to hunt and never leaves its 6-block hiding patch
  (`CatfishEntity.java:474-479` frees it only once it is no longer young).
- Each grown crab, snail or catfish breeds once. After that its rest clock is set (2 or more days, `AnimalLife.bred`,
  `:244-245`) and never counts down, so it is never ready again.
- The babies that never grow still count in the local limit (12 crabs, 10 snails, 6 catfish within 48 blocks) and in
  their group's cap, so a stretch of river slowly fills with babies that can never breed, and the world's spawner is
  kept from adding grown ones there (see R10-1). Bred animals are not kept for good, so they fade when the player
  walks away; near a base the player stays at, they pile up to the limit and stop there. No runaway, but a dead end.

**Where.** `AnimalLife.tick()` (`framework/.../animal/AnimalLife.java:116-130`) is called only at
`TilapiaEntity.java:495`, `BullfrogEntity.java:443`, `CrocodileEntity.java:838` and `OtterEntity.java:737`. There is no
call in `CrabEntity`, `SnailEntity` or `CatfishEntity`, and no shared place that ticks every animal's life (searched
both mods for `life.tick()`, `life().tick()` and any entity tick event).

**The proof.** The search above; `CatfishEntity.tick()` and the crab's and the snail's `tick()`/`aiStep()` read whole.
No game test would have caught it: the tests check that babies are born at the right size
(`AnimalLifeTests.java:372, 453`) and stop there; none waits for a baby to grow or for a parent to breed a second time.
(The hippo and the monitor also never call it, but neither breeds nor has young yet: round 9's to note.)

**Proposed fix.** One line in the server part of each of the three `tick()` methods (`life.tick()`; re-set the size
when it returns true, as the tilapia does at `:495-497`). Better, and fewer rules: let the kit tick every `HasLife`
animal from one place, so a new animal cannot forget it. Add one test per kind: a baby set to one tick before grown
becomes grown, and a parent's rest runs out. **Mind what this switches on:** today breeding of these three kinds is
mostly asleep, so R10-1 (spawner against breeding), R10-4 (catfish eggs eaten) and R10-16 below are hidden. Fix this
one first, then judge those in play.

**Status: fixed** (2026-11-24, BUGS 335; test `everyAnimalWithALifeGrowsUpAndRestsAfterBreeding`), the "better" way: the kit counts every animal's life clock from one place (`AnimalLife.tickEvery`, after each animal's own tick), with a `HasLife.grew()` hook for an animal that changes its look when it grows (tilapia, crocodile); the four animals' own calls are gone. The hippo and the monitor are counted too (round 9's R9-41). **Mind:** crabs, snails and catfish now breed again and again and their young grow up; R10-1, R10-4 and R10-16 are no longer hidden and want a look in play.

### R10-13 (A, logic proven; how often to measure): a crab's meal does not count when the food ends at the first bite

**What is wrong.** A crab picking at food bites at set moments. If the food is gone at the first bite (an algae mat
one layer thin, a snail-egg clutch, scraps on their last piece), the meal is marked "not fed"
(`CrabPickGoal.java:155-160`: `fed = picking > BITE_TICKS[0]`, false at the first bite), and a crab that was not fed
is hungry again in 5 to 15 seconds (`:110-112`) instead of the minutes a fed crab waits.

**What the player would see.** A crab on thin algae strips mat after mat with hardly a pause, or eats snail eggs and
then a snail one after another: one crab can clear a patch of bed far faster than meant, the snails with it. Scene to
measure: 3 crabs, 12 one-layer mats and 6 snails for a game day; count what is left.

**Proposed fix.** A bite that ate something counts as fed, whatever number bite it was (`fed = true` when `bite()`
succeeded once, or when the first bite removed the last of the food). One rule fewer, not one more.

**Status: fixed** (2026-11-24, BUGS 336; test `aCrabWhoseFirstBiteTakesTheLastOfTheFoodIsFed`): a meal with at least one bite down counts as fed when it ends.

### R10-14 (B, proven): the crab's 20-second limit on threatening does nothing

`CrabThreatGoal.java:52` stops the threat display after `MOST` (400 ticks, 20 s), but `canUse` (`:37-48`) starts it
again at the next check whenever a survival player still stands within reach. The player sees a crab that holds its
claws up at them for as long as they stand there, and never eats or moves on. Fun for a minute; wrong beside a base.
Fix: after a full threat, the crab does not threaten the same player again for a while (it sidles off instead, which
its avoid goal already does).

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): fixed: after a full 20-second display a crab does not threaten a player who only stands near for a minute (`CrabThreatGoal.notBefore`, my pick); a hit is still answered at once.**

### R10-15 (B, likely; to measure): asking for a new path every tick to a spot that cannot be reached

`SeekMateGoal.java:85-94` (all breeding animals) and `SnailLayGoal.java:96-103` (through `GoExactly`) ask the path
finder for a route again on every goal tick while the animal is not there yet. When the mate or the laying spot
cannot be reached (a mate behind a ledge, a waterline spot with no way up), every try is a full failed search, the
dearest kind, for the whole give-up time. Scene to measure: 10 snail pairs with a wall between each pair, 1 minute,
time per tick against the same snails idle. Fix: ask again only every 10 to 20 ticks, or when the target has moved a
block (vanilla's own follow goals do this), and give up early after two failed plans.

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): fixed: `SeekMateGoal` and `SnailLayGoal` ask for a new route on their own beat, or when the route has run out, but then at most twice a second, not on every tick. Not measured. Giving up early after two failed plans is not built (the kit's give-up time still ends it).**

### R10-16 (B, likely): snail eggs within a crab's reach never hatch

A crab looks for eggs first, before algae or scraps (`CrabPickGoal.java:290-301`), within its whole search box and one
block up or down. Snail clutches sit at the waterline for 2 days. Any clutch on a bed with a crab near is eaten well
inside that time, and with R10-13 the crab is hungry again at once. Crabs and snails share most zones
(`RiverZones.java:73-74`), so where both live the snails can only be replaced by the world's spawner, never by their
own eggs. Not a crash of the chain (the spawner refills), but snail breeding is for show there. The user's call:
leave it (fun first, the spawner covers it), or make eggs one food among the others (not first) and only when the
crab stands within 2 or 3 blocks.

**Your call (2026-11-26):** Crabs and snail eggs. Today a crab goes for snail eggs before any other food, anywhere in its search box, so where both live no snail clutch ever hatches (the world's spawner still refills the snails). Leave it, or make eggs one food among the others, eaten only when the crab stands within 2 or 3 blocks? My pick: the second, so the snails' own breeding counts.

**Call made (2026-11-26):** the second: eggs only when the crab is close.

**Status (2026-11-26, `claude/review-fix-b6`): fixed: a crab picks at eggs only within 3 blocks of where it stands (`CrabPickGoal.EGG_REACH`), not from its whole search box.**

### R10-17 (C/B, likely): the snail's crawl and rest clips flicker (BUGS 250's likely cause)

`SnailEntity.java:296-302` picks crawl or rest from how far the snail moved since the last tick, with a cut-off of
0.002 blocks. A snail's walk is so slow (speed 0.05, with stops and turns on the way) that its step falls under the
cut-off between strides, so the clip can swap many times a second. Fix: decide from "does it have a path it is
walking" (sent with the state it already syncs) rather than from the measured step, or keep crawl on for half a
second after the last movement.

### R10-18 (B/C, to measure): small searches that run more often than they need

- The crab has three avoid goals, each looking round for its own animal on every check (every other tick). One look
  for "anything I fear", shared, is cheaper and one rule.
- `SnailHideGoal.scared()` (`:86-87`) asks the world for every entity in a box round the snail, then filters; asked
  every other tick by every snail. Use the by-class search with the smaller box first, and check every 5 to 10 ticks
  (a snail pulling in half a second late looks the same).
- A mother crab asks `RoomToBreed.here` (a 48-block count of crabs) once a second for the whole 2 days before her
  eggs are old enough to hatch (`CrabEntity.java:207-213`); the answer is only needed once the eggs are due.
  Compute the room only when the clock says hatching time.
Scene to measure all three: 24 crabs and snails (the group's cap) round one player, against an empty river.

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): half fixed: a snail looks round for what it fears 4 times a second, not every other tick, and a mother crab counts the crabs round her only once her eggs are due. Open: the crab's three avoid goals as one look (small, not measured).**

### R10-19 (C): crab and snail small things

- A crab whose food cannot be reached gives up, then picks the same food again after its short wait: no memory of
  "could not get there".
- A female crab gets her eggs when the male's courtship ends even if she never took part (she was only marked).
- Baby crabs riding their mother still run their own goals (they look for food while carried).
- A snail being cracked by a crab when the chunk unloads is left whole but still marked as held: check on load.
- The mob-griefing game rule is never asked by any animal that eats or changes blocks (kit-wide; say so in the
  notes, or ask it once in one shared place).
- A `snail_eggs` block from the creative menu never hatches (no clock set). Creative only.
- Comments that disagree with the code: `CrabEntity.java:130-131, 204-205`; `SnailLayGoal`'s class comment;
  `snail.json` says the snails touch heads 8 seconds, but 160 goal ticks is 16 seconds (goal ticks run every other
  game tick; the catfish file says so rightly). A few unused imports.
- A flaky test's "snails stuck: laying true" may be the test's snails fading mid-test (test animals are not kept and
  the test has no player); unsure, to check when it next fails.
- Good, to copy: the mother crab keeps trying to hatch for the kit's 10 days (`eggsTick` with `ClutchClock`); the
  tilapia throws her eggs away on one failed check (R10-1).

## Second pass: what stands

| Rating | Findings |
|---|---|
| A | R10-1 (breeding against the spawner; size to measure), R10-8 (frog left floating), R10-9 (tadpole age sent every tick; cost to measure), R10-12 (crab, snail, catfish never grow), R10-13 (crab's uncounted meal; size to measure) |
| B | R10-2, R10-4, R10-5, R10-10, R10-14, R10-15 (to measure), R10-16, R10-18 (to measure) |
| C | R10-3, R10-6, R10-7 (to measure, round 8's), R10-11, R10-17, R10-19 |

Nothing dropped; the ones marked "likely" (R10-4, R10-15, R10-16, R10-17) are read from the code and not seen
happening. Order to fix: R10-12 first (it wakes breeding up), then R10-8 and R10-9, then measure R10-1, R10-4 and
R10-16 in play with breeding really running, since those three are one question: should the world's spawner or the
animals' own young fill the river. The A findings still need numbers in `docs/BUGS.md` (`python tools/bug_number.py`);
left to the fix session, as this round writes only this file.

## Parts found solid

- **No count can run away.** Every birth is capped twice: the local limit and the group's cap before laying, and
  again at hatching (`EggClutchBlock.hatch`, `CrabEntity.letOutBabies`, the tilapia's mouthful). Eggs become empty
  shells after 10 days; remains rot (a day fresh, a day rotten); scraps, shells, clutches and nest pits all fade.
- **No forced loading of land.** Every block search stays within 8 to 13 blocks of the animal or checks that the
  place is loaded first.
- **Looking for food is throttled.** Tilapia, catfish, crab and snail look every 200 to 300 ticks and wait longer
  after finding nothing; the mate search runs every 40 ticks, spread over the animals so they do not all look in the
  same tick.
- **Fly clouds** cost next to nothing (no path, no search) and cannot be eaten out for good.
- **Algae cannot be stripped for good:** a mat's last layer grows back, and grazers leave the bed block.
- **Nobody starves to death;** hunger only changes what an animal does, so no food shortage can empty the river.
- **Clocks run on the world's game time,** so sleeping and `/time set` do not break eggs or rests.
- **Saving:** hunger, eggs carried, young in the mouth, the frog's guard spot and dug-in state all come back after a
  reload; a mouth-brooding tilapia that dies lets its young out.
- **Spectators** are handled the same way in every place I checked (not feared, not counted as near).
- **The spawning check list is followed:** each kind has its group, the quiet-savanna gate, the zone shares, and a
  test through the world's own checks.
- **No leftovers between worlds,** except the test-only rain switch (R10-6); the life files reload with each world.
