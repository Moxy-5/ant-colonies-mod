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

### Planned with the user 2026-10-20: colony parts
The user's reasons (2026-10-20): (1) make the split into three mods (Apex part 2) easy and clean; (2) the Matabele ant's
behaviour (scouts, raid columns, rescue, wound care, moving house) will be complicated, so it must plug in without
touching the brain. The user left every pick to me ("just go with your picks whatever will make it the best and
easiest to stop bugs from happening and push updates easier"); so these are my picks, agreed by the user.

**Decisions**
1. **A species chooses its parts in its species file**, a `parts` list (`red_harvester_ant.json`: `"parts": ["hill",
   "bakery", "trunk_trails", ...]`), seen and changed without code. Each part is known by a name in a **parts
   registry** (name -> how to make it); the shared parts every colony has (nest, food map, brood, population, away life,
   danger map, door, ...) are not listed, they are always there. Why the registry: after the split the framework mod
   holds the registry and its shared parts, and each species mod signs up its own parts (the mound, the fungus garden
   and soldiers in the savanna mod; the hill and bakery in the desert mod), so the framework never names a species part.
   A part name in a species file that nothing signed up stops the game loading with a clear message (no silent typo).
2. **Asking for a part a species may not have is a yes/no**, `colony.part(Mound.KEY)` giving "none" for a harvester
   colony, never a do-nothing stand-in (a stand-in hides mistakes: an ant "building a mound" that is not there). The
   code that only runs for one species (most of the 91 `colony.mound()` calls are inside termite goals) asks once at
   the top. A test fails if any part runs for a species that did not list it.
3. **Old saved worlds keep loading**: each part saves under the same name as today; a part in an old save that the
   species no longer has is skipped (and logged once), a listed part missing from a save starts fresh, as today.
4. **Order:** the steps below, each a full run, nothing changing in-game until step 2 (and then only what a species
   should never have had), speed measured before and after with the stress test (every 5-second window of each stage
   averaged over 3 runs of the old code and 3 of the new: the numbers swing a lot).

**Steps**
1. **One parts list.** A small `ColonyPart` shape (name, tick, tick while away, save, load, its place in the tick
   order); `Colony` holds one ordered list and ticks, saves and loads it in one loop each, in exactly today's tick
   order (written down once, not in 4 or 5 places). Every species still gets every part in this step. New check: a
   save-and-load test that fills every part with something, saves, loads and compares, so a forgotten save line can
   never happen again. Nothing changes in-game.
   **BUILT 2026-10-21** (branch `claude/colony-parts-e4f872`, not merged; details and the kept differences in
   `docs/HISTORY.md`): `colony/ColonyPart`, one row per part in `Colony`'s constructor with its two tick places
   (loaded and away are different orders today, so each part has both), save and load or why not saved; the check in
   the two `away` save-and-load scenes with a test sample for every part a scene leaves empty. For step 2: the part
   names are already in the rows (`nest`, `food_store`, `mound`, ...), and the tick places travel with the part, so a
   species mod's part can slot in by its place without the framework naming it.
2. **Species pick their parts.** The `parts` lists in both species files and the registry; the harvester colony stops
   making the termite parts (mound, fungus garden, mushrooms, soldiers, king care, hidden trips, closed holes,
   galleries) and the termite colony the ant parts (hill, bakery, trunk trails, recruits, forager spots, patrol, ...).
   The exact two lists are drawn up at the start of this step from what each part's own "do I apply?" check says today.
   **BUILT 2026-10-21** (same branch; details in `docs/HISTORY.md`): the registry `colony/ColonyParts` (the step 1
   rows moved there, each kind shared or listed), `"parts"` in both species files, checked when they load. Shared (18):
   nest, nest walls, food store, food map, queen care, brood, refuse, foraging holes, foraging trails, danger map,
   neighbours, population, away life, nest ledger, nest life, field life, build life, worker food. Harvester: hill,
   bakery, trunk trails, patrol, forager spots, door, recruits. Macrotermes: king care, mound, closed holes, hidden
   trips, fungus garden, mushrooms, soldiers, alarm. (The door is listed, not shared; the foraging holes and trails are
   shared: the harvester uses them too.) An unlisted part is never ticked, saved or loaded, but still made, for the old
   getters. **For step 3:** stop making unlisted parts (`Colony`'s constructor makes every kind today and keeps the
   unlisted ones in `unused`), give `Colony` a `part(ColonyParts.MOUND)` that is empty for a species without it, move
   the callers over and drop the getters; `Colony.unusedPartsWrittenTo` and `AwayTests.partsAreTheSpecies` then become
   the check that no part runs for a species that did not list it.
3. **Ask before use.** The callers moved to the yes/no, a batch of goals or parts at a time, a category's tests after
   each batch; the parts' own "do I apply to this species?" checks go once nothing needs them.
   **BUILT 2026-10-22** (same branch; details in `docs/HISTORY.md`): `Colony.part(type)` / `has(type)`, the constructor
   makes only the shared and listed parts, the 15 getters gone (about 450 calls moved), each answer without the part
   the one the part gave a species without its numbers. Only `FungusGarden.exists()` was a pure species check (gone);
   the `numbers() == null` checks read the species file's numbers and stay. Old saves: an unlisted part's keys come
   from a throwaway part made only while loading. Full run 139 of 141 (the two old seed flakes, both pass alone).
4. **Colony news (my pick for the Matabele ant):** a small shared way for parts to hear what happened without knowing
   who else is listening: "a member was hurt or killed here", "food found here", "a hole opened or closed", "a
   neighbour met". Today such things are wired by hand from part to part (the danger map, closed holes, neighbours,
   recruits). The Matabele parts (scouts reporting termite feeding spots, the raid caller, rescue) then plug in by
   listening, and the brain needs no new line for them. Only the wiring that exists today is moved onto it; nothing new
   happens in-game. **Agreed by the user 2026-10-20** ("I like that colony news"), who also saw the speed side: an ant waiting for something (a raider by a termite feeding spot) is told when it happens instead of checking every tick, so waiting costs next to nothing. Built so: a listener is called only when its news comes, and news is only sent while someone listens.
   **BUILT 2026-10-22** (branch `claude/colony-news-d4bc74`, from step 3, not merged; details in `docs/HISTORY.md`):
   `colony/ColonyNews` (`colony.news()`), kinds "a member hurt", "a member died", "food found", "a neighbour met"; a
   part's row in `ColonyParts` says what it hears and at which place (`ColonyPart.hears`), the colony signs its parts
   up when made. Moved over: the hurt insect (alarm, patrol, danger map, forager spots), the dying worker ant (patrol,
   forager spots, recruits), food found (forager spots, recruits), neighbours met (by insect, trail or gallery), each
   in today's order. "A hole opened or closed" has no wiring today (parts ask when they need to): left for its first
   listener in M8, my pick, to confirm. For the split: `DangerMap.avoids` still asks the trunk trails (a harvester
   part) by name: a shared "may we forage here?" vote would take it out. Tests: `colony,away,termitefood,antnest,soldiers`
   86 of 88, both failures passing 3 of 3 alone; the full run is step 5.
5. **Full run and speed.** The stress test before and after; the expected gain is small (each colony skips the parts it
   does not have instead of each checking and returning); the main wins are fewer places for a bug and a new species
   with no copied code.

Then the split's own plan (`docs/plans/mod-split.md`) can draw the framework's wall along the registry: shared parts
in, species parts out.

After it: `colony/FoodStore.java` (1,077 lines, the seed granary and the fungus comb in one; 23 of the 57 colony files
mix harvester-only and termite-only work). Smaller: errand helper step 4 (`ScoutGoal` in three); later, in the
termite and ant parts, `entity/AbstractAntEntity.java` (1,837 lines; every carried load its own switch, could be one
"what I carry" slot).

## Also found 2026-10-20
The termite stress test shows, in old and new code alike, bursts of failed route searches about once a second for a
minute (800 to 1,300 steps each, up to 27 ms the longest); cause not found yet. Worth finding (a leave-alone memory is
the likely fix, `docs/BUGS.md` 42).

## Timing (user, 2026-10-21)
The user leaves for California in 6 days, about 5 days of work left (weekly quota resets 2026-10-21 10 PM); "I don't
want to rush the project, so whatever you think is best". My plan, agreed: part 1 (the brain, about 12 to 18 steps)
and part 2 (the split, about 6 to 10 steps) at a careful pace, about 3 days; then part 3 (the termites, the Matabele
ants' prey) with the days left. Everything merged into `master` with a full run before the trip, never a half-done
branch left behind; the Matabele ants (M8) start after the trip.
