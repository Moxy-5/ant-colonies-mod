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
