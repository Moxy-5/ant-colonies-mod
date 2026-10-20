# Project Apex (roadmap 3c in CLAUDE.md)

Not planned in full yet: each part is planned with the user before it is built. Notes so far:

## Part 1, the brain: the next file is `colony/Colony.java` (measured 2026-10-20, branch `errand-helper`)
The user asked which file should get "treatment like the errand helper" next. Answer: `Colony.java`, the brain's hub.
- The most changed file in the project (85 commits), 872 lines.
- It builds the same 35 parts for every colony whatever the species (Nest, NestWalls, Hill, FoodStore, FoodMap,
  Bakery, QueenCare, KingCare, Brood, Refuse, Mound, ForagingHoles, ForagingTrails, TrunkTrails, Patrol, ForagerSpots,
  Door, Recruits, DangerMap, ClosedHoles, Neighbours, HiddenTrips, FungusGarden, Mushrooms, Soldiers, Alarm, Population,
  AwayLife, NestLedger, NestLife, FieldLife, BuildLife, WorkerFood): a harvester colony carries a termite mound, fungus
  garden, mushrooms, soldiers and king care, a termite colony a bakery and a hill; each part checks inside whether it
  applies.
- Each part is written in by hand in 4 to 5 places (field, constructor, `tick()`, `save()`, `load()`), in lists of
  different orders that do not hold the same parts; a forgotten save line loses that part on reload.
- The Matabele ant (M8) would be a third species getting all 35.

The idea to plan with the user: shared parts every colony has, species parts plugged in (by the species profile or a
small species hook); one list the brain ticks, saves and loads; a clean way to ask for a part a species may not have;
old saved worlds still load; small steps, a full run each, nothing changing in-game; speed measured with the stress
test before and after (averaged over every 5-second window of each stage over several runs: the numbers swing a lot).

After it: `colony/FoodStore.java` (1,077 lines, the seed granary and the fungus comb in one; 23 of the 57 colony files
mix harvester-only and termite-only work). Smaller: errand helper step 4 (`ScoutGoal` in three); later, in the
termite and ant parts, `entity/AbstractAntEntity.java` (1,837 lines; every carried load its own switch, could be one
"what I carry" slot).

## Also found 2026-10-20
The termite stress test shows, in old and new code alike, bursts of failed route searches about once a second for a
minute (800 to 1,300 steps each, up to 27 ms the longest); cause not found yet. Worth finding (a leave-alone memory is
the likely fix, `docs/BUGS.md` 42).
