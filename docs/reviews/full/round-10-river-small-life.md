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
