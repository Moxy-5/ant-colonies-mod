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
   in today's order. "A hole opened or closed" has no wiring today (parts ask when they need to): now step 7 below.
   `DangerMap.avoids` still asks the trunk trails (a harvester part) by name: now step 6 below. Tests: `colony,away,termitefood,antnest,soldiers`
   86 of 88, both failures passing 3 of 3 alone; the full run is step 5.
5. **Full run and speed.** The stress test before and after; the expected gain is small (each colony skips the parts it
   does not have instead of each checking and returning); the main wins are fewer places for a bug and a new species
   with no copied code.
   **BUILT 2026-10-22** (branch `claude/colony-parts-step5-2c5f2c`: steps 1 to 4 together, step 4's branch
   fast-forwarded in; not merged, waiting for the user's in-game test): full run on 8 servers **141 of 141**, 10
   minutes. Speed, the stress test 3 runs of `master` and 3 of the branch taking turns, every 5-second window of each
   stage averaged (153 windows a side): colony brain **0.170 ms a tick before, 0.163 after** (about 4 percent less,
   within the noise); whole mod 1.72 before, 1.96 after, but the gap is all route searches (0.31 to 0.48 ms), the
   bursts of failed searches noted below that land in some runs and not others, old and new alike (one run of each
   had none; the worst stage of one new run was 2.6 ms of searches alone); without the route searches the mod is 1.41
   before, 1.48 after, within the run-to-run swing. As expected: no speed gain to see; the wins are fewer places for
   a bug and a new species with no copied code.

**Before the split (found while building step 4, 2026-10-22; added at the user's ask, to plan with the user):**
6. **"May we forage here?" asked of every part.** `DangerMap.avoids` (a shared part) still asks the neighbours, the
   termites' "close the holes" rule and the harvester's trunk trails (`ColonyParts.TRUNK_TRAILS`) by name. After the
   split the framework could not name a desert-mod part, so this has to change first: a shared question that each part
   answers for itself, the way the news lets each part hear for itself (the danger map, the neighbours, the closed holes
   and the trunk trails each say "keep off" or nothing). Same answers as today; nothing changes in-game.
   **Planned with the user 2026-10-22** (the user's OK of the plan below; the picks are mine, agreed). Found while
   planning: `Colony.mayForageAt` also names the harvester's patrol (`ColonyParts.PATROL`), so it changes too. Today
   there are two questions, so there stay two: **"keep off?"** (lasting reasons: danger, neighbours, attacked ground;
   asked from many places, also by termites eating from inside and by the numbers) and **"not cleared yet today?"**
   (only the patrol: no patroller back from that way). "May we forage here?" is "no" to both. One question would
   change things in-game (the recruits' scent lines would wait for the patrol), so not one.
   - **How:** `colony/ColonyQuestions`, a small yes/no question kind like the news kinds; a part's row in `ColonyParts`
     says what it answers and at which place (`ColonyPart.answers`), as it says what it hears; the colony signs its
     parts up when made; a question is "yes" when any part says yes (asked in place order, the first yes ends it).
   - **Who answers "keep off?":** the neighbours (past a place the colony yields to), the closed holes (where members
     were attacked: the termites' rule moves out of the danger map into the termite part, with its `close_holes`
     switch), the danger map (dangerous places, `keep_away`), the trunk trails (the trail out there passes danger,
     `keep_away`). **"Not cleared yet today?":** the patrol. In today's order (cheapest first).
   - `colony.keepsOff(level, pos)` in place of `colony.danger().avoids(...)`, which goes; `Colony.mayForageAt` asks the
     two questions and names no part; `colony.whoKeepsOff(level, pos)` names the part that said yes, for the test
     messages and the logs.
   - **Checks** in scenes that exist (no new scene): the trail danger scene (`TrailTests`), the termite killed by the
     bush (`TermiteForagingTests`) and the neighbours (`NeighbourTests`) say which part keeps off; the save and load
     scenes check each colony's answerers are its own parts. Same answers as today; nothing changes in-game. One speed
     check (the question is asked in the plant scans).
7. **The hole questions** (was "the hole news"; changed with the user 2026-10-22). Found while planning: the hole news
   has nobody to hear it, even in M8 (news stays inside one colony: the Matabele ants see termites, they cannot hear a
   termite colony's news, so its first listener would be a termite part not designed yet, and building it now means
   guessing its shape: does the harvester's sealed entrance count, is a hole "closed" before its plug is packed). What
   really blocks the split round holes is a question: the shared foraging holes and galleries ask the termite part
   `ClosedHoles` by name in 12 lines. So, on step 6's machinery, two more questions answered by the closed holes:
   **"is this hole kept shut?"** (closed for danger or left for good: never opened, plugged at once, no turn, no tip,
   no food fetched through it) and **"may a gallery grow through here?"** (not toward where members were attacked),
   with a quick "is any hole kept shut?" for the plug search. Same answers; nothing changes in-game. **The hole news
   moves to M8**, designed with its first listener.
   Steps 6 and 7 are built together on branch `claude/apex-questions` (from `master`); the user tests in-game before
   the merge.
   **Steps 6 and 7 BUILT 2026-10-22** (branch `claude/apex-questions`, not merged; details in `docs/HISTORY.md`):
   `colony/ColonyQuestions`, `ColonyPart.answers`, `colony.keepsOff` / `whoKeepsOff` / `isHoleKeptShut`;
   `DangerMap.avoids` and `ClosedHoles.isClosedIn` gone; the danger map, `Colony.mayForageAt`, the foraging holes and
   the galleries name no species part for these questions any more. Tests on 6 servers (the user's ask):
   `colony,antnest,away,termitefood,mound,soldiers,hills` 101 of 101; full run 139 of 141, the two failures the old
   flakes beside other tests (`docs/BUGS.md` 55 and 62), each 3 of 3 alone. Speed (the user asked for a quick check after all): 3 stress runs of each, only the runs where every termite ticked compared (2 a side, see below): colony brain 0.40 ms a tick on `master`, 0.39 on the branch; the whole mod without route searches 1.90 before, 1.77 after; route searches swing 0.08 to 0.81 between runs (the known bursts): no change, as expected.
8. **The rest of the shared code that names a species part** (found while planning step 6, 2026-10-22; to plan with
   the user). Steps 6 and 7 fix only the forage question and the holes. Counted on `master` 2026-10-22: about 81 code
   lines in 15 of the 22 shared colony files still name a harvester or termite part (`FieldLife` 12, `NestLife` 8,
   `ForagingHoles` 7, `FoodStore` 7, `BuildLife` 7, `Galleries` 7, `Colony` 7, `Population` 6, `Nest` 4, and a few
   more), plus about 50 lines in the goals (many of them goals that will move to a species mod with their part, so
   they are fine). After the split the framework could not name those parts, so each line must become a question, a
   piece of news or a hook a species part plugs into, or move into the species part. First a sorted list of every line
   (which can be a question like step 6, which news, which a hook, which simply moves), planned with the user, then
   built in batches. Nothing changes in-game.
   **Planned with the user 2026-10-22** (the user's OK: three batches, my picks, the species code moved and not only
   put behind a socket; branch `claude/apex-step8-e27231` from `claude/apex-questions`). Counted again on that branch:
   **60 lines in 14 shared colony files** (`FieldLife` 11, `NestLife` 8, `BuildLife` 7, `FoodStore` 7, `Colony` 6,
   `Population` 6, `Nest` 4, `AwayLife`, `Brood`, `NestLedger`, `WorkerFood` 2 each, `ForagingHoles`, `Galleries`,
   `NestWalls` 1 each; the rest of the 81 were species files, which move with their species). Every line, by batch:
   - **8a, questions and news** (step 6's and step 4's machinery, new kinds only):
     - questions: "does the alarm keep everyone in?" (`Colony.isForagingTime`, `FieldLife.forageWith`; the alarm);
       "not cleared yet?" (`FieldLife.forageAlone`, the patrol: the kind is step 6's); "does the colony build a mound?"
       (`Colony.buildsMound`) and "is the mound being built now?" (`ForagingHoles.middleHole`; the mound); "is this a
       block the builders want cleared?" (`Nest.canDig`, `Nest.standSpotsFor`; the mound); "is this cell a tunnel
       built inside the structure?" (`NestWalls.wallFirst`; the mound); "is more food wanted in, past the store?"
       (`FoodStore.wantsSeeds`; the bakery: bread to mill); "does the colony keep trails?" (`FoodStore.plants`; the
       trunk trails); "is the store known to hold nothing the workers eat?" (`WorkerFood.oldCombKnown`; the garden).
     - news: "a member went into the numbers" and "came out of them" (`AwayLife.foldIn`, `Population.goIn` and
       `comeOut`; the forager spots); "nurse food counted in" (`AwayLife.foldIn`, `NestLedger` loading; the bakery);
       "a rich piece taken for milling" and "a piece eaten" (`FoodStore`; the bakery's rich millers); "a comb eaten
       away" (`FoodStore.takeOneLayer`; the garden's nodules); "the numbers made a trip" (`FieldLife.work`; the trunk
       trails); "the numbers' foraging second" (`FieldLife.forage`; the door); "how many forager numbers there are"
       (`FieldLife.forageWith`; the forager spots trim their held spots). "A block dug" (`BuildLife.dig`; the mound)
       too.
   - **8b, colony hooks that give answers** (new `colony/ColonyHooks`: a kind names what is asked, a part's row plugs
     in its answer, `colony.hooks(kind)` in place order; a colony without the part gets today's default):
     the colony's reach (`Colony.foragingRadius`; the trunk trails' longest tip); the walk by trail and the trail
     columns to scan (`FoodStore`; the trunk trails); how many forager numbers may go out (`FieldLife`, twice; the
     door); the founders beside the queen (`NestLife.founders`; the king); the nurses' food (`Colony.nurseFood`,
     `NestLife.nurseFoodLeft` and `takePiece`, `WorkerFood.look`; the garden or the bakery, through `NurseFood`); a
     young one's soldier meal and the soldier wanted (`Brood`; the soldiers); insects away on hidden trips
     (`Population`, `Galleries`; the hidden trips); the structure over the nest, its blocks, tunnels and next dig
     (`Colony.moundParts`, `Nest`, twice; the mound); the comb cells for ageing on return (`NestLedger`; the garden).
   - **8c, the species work moved into its parts** (behind hooks that do work):
     - `BuildLife` (building on return): the hill's night soil and heaping the dug soil (into the hill part) and the
       whole mound building with its soil from the galleries (into the mound part); `BuildLife` keeps the builders'
       time, the nest's digging, the galleries and the founders, and lends the moved code its helpers.
     - `FieldLife` (the numbers forage): searching alone, going back to a spot, out along a trunk trail and the
       recruits' call (into the forager spots part); `FieldLife` keeps the plants it remembers and the trips.
     - `NestLife` (nest life while away): the king living and fed, the bakery milling bread, the garden growing its
       nodules (into those parts, at the same moments in the same order); and `Population.targets`' soldiers at their
       posts and the soldiers in the colony list (into the soldiers part).
   - **Checks:** each batch adds checks in scenes that exist (no new scene) that the right part answers or hears,
     as steps 4, 6 and 7 did (`AwayTests.partsAreTheSpecies` and others); after each batch its categories, at the end
     a full run and the stress test before and after, on 6 servers (the user's ask). Same answers; nothing changes
     in-game.
   - **Left on purpose** (found while listing, for step 9 or later): shared files still name species *types* (not
     parts): `MoundPlan.Part` in `Colony.moundParts` and its callers, `TermiteSoldierEntity.Rank` in `Brood` and
     `Population`, `FungusCombBlock` and `TermiteMoundBlock` in `FoodStore`, `NestLedger`, `NestLife`, `BuildLife`,
     `TermiteKingEntity` in `BuildLife`; and two termite-only pieces live inside shared parts: the galleries inside the
     nest, the fungus comb inside the food store (`FoodStore` was already next after Apex part 1).
   **BUILT 2026-10-22** on branch `claude/apex-step8-e27231` (8a `d8b413e`, 8b and 8c `a994164`; not merged, the
   user tests in-game first): every line above as listed; no shared colony file names a species part any more (the
   parts still named are only in species files). Full run on 6 servers 139 of 141 (BUGS 62 and 55, old flakes);
   speed the same (colony brain 0.390 ms before, 0.396 after). Details in `docs/HISTORY.md`.
9. **The commands, the event file and the ant body** (found planning step 8, 2026-10-22; the user's pick: its own
   step, to plan with the user after step 8). Outside the colony folder, shared files that name species parts too:
   the `/antcolonies` commands (`command/ColonyCommands`, about 15 lines, mostly the `list` read-out, which could ask
   each part to describe itself), `ModEvents` (1 line, the alarm) and the shared ant body (`entity/AbstractAntEntity`,
   4 lines: the trunk trails, the hidden trips, the door, the recruits); and the species types still named in shared
   files (step 8's "left on purpose"). Each must go before the split. Nothing changes in-game.
   Also the goals (counted 2026-10-22 when step 8 was built): about 60 lines in 18 goals name a species part. Most are
   goals of one species that move with it (`ClipGrassGoal`, `PatrolGoal`, `MillBreadGoal`, `ShapeMoundGoal`,
   `GuardPostGoal`, ...); the shared ones need questions or hooks: `ScoutGoal` (16 lines, errand helper step 4 anyway),
   `DigGoal` (5), `GatherSeedGoal` (5), `GroomGoal` (2), `CarryPelletGoal`, `FeedLarvaGoal`, `DefendGoal`, `NapGoal`,
   `NoticeGoal` (1 each); and the species bodies (`TermiteKingEntity` 6, `TermiteSoldierEntity` 4, `AntLarvaEntity` 1),
   which move with their species.
   **Planned with the user 2026-10-22** (the user's OK: three batches, my picks; branch `claude/apex-step9-642594` from
   step 8). Counted again on the branch (comments, imports and the species game test scenes not counted). Corrections:
   `ClipGrassGoal`, `GatherSeedGoal` and `StoreSeedGoal` are given to both species' workers (shared); `DefendGoal` and
   `CarryPelletGoal` are one species' goals (move whole); `GroomGoal` and `NapGoal` are body language meant for every
   species (shared). Found too: 4 lines in shared colony files ask "is this Macrotermes?" (`Brood` twice, `Population`,
   `Refuse`), about 35 shared lines borrow the word "worker" from the harvester's body (`WorkerAntEntity.CASTE`) and 7
   the word "king" from the termite king.
   - **9a, the shared goals, the ant body and the event file** (about 45 lines): a forager's trip (scout, seeds, grass,
     carrying in, the body's walk home) through questions, news and hooks any species uses: "must it wait for touches?"
     (the door), "leave this food to a called nestmate?" (the recruits), news "set out", "trip over", "came home" (the
     door, the recruits, the forager spots), the way along a trail (the trunk trails), where a search goes (the forager
     spots), a place for a seed besides the store (the bakery); "alarmed?" for grooming, napping and noticing (the
     alarm); news "a player stamped or broke a block nearby" (the alarm, from `ModEvents`); where dug soil goes (the
     hill, the mound); the members the nurses feed besides the young (the soldiers); "on our trail?" for meeting a
     neighbour (the trunk trails); the hidden ways (the hidden trips); the co-founder sealing the entrance (the king).
   - **9b, the commands:** the `list` read-out asks each part to describe itself in its slot (same text, same order);
     the termite-only commands (`mound`, `mushrooms`, `drum`, `alarm`, `queensize`) move into a termite commands file,
     same names; `population`'s soldiers through the castes hook.
   - **9c, species names in the shared colony files:** one shared word for "worker" and the king through his part; the
     soldier ranks a plain caste name (saves unchanged); a table of each species' bodies in place of "is this
     Macrotermes?"; the mound's block map through its hook (`MoundPlan.Part`, the tunnel blocks); the fungus comb, the
     mud crust and the swelling termite queen as small shared shapes the termite blocks and bodies fill in, the comb's
     ageing on return into the garden part.
   - **Not in step 9:** registering blocks, items and mobs, the renderers, whole species files (the split's own job);
     the termite pieces inside shared parts (galleries, foraging holes, the comb in the food store: part 3); errand
     helper step 4 (`ScoutGoal` in three).
   - **Checks** in scenes that exist (`AwayTests.partsAreTheSpecies` and others); each batch its categories, at the end
     a full run on 6 servers (the user, 2026-10-22) and the stress test before and after. Nothing changes in-game.
   **BUILT 2026-10-22** on branch `claude/apex-step9-642594` (9a, 9b and 9c's first half `1eb1a89`, 9c's second half
   `84b5bc2`; not merged, the user tests in-game after step 10): every line above as listed. No shared goal, command or
   colony file, `ModEvents` or the ant body names a species part, and no shared colony file names a species type
   except the part 3 pieces (galleries and mud casts in `Nest` and `ForagingHoles`, the comb in `FoodStore`). New
   shared pieces: `colony/Caste`, `species/SpeciesBodies`, `entity/ColonyBorn`, `entity/SwellingQueen`,
   `block/FacedWall`, `block/GardenBed`, `command/TermiteCommands`, `colony/ForagerSearch` (in the forager spots).
   Full run on 6 servers 139 of 141 (BUGS 62 and 55, old flakes; both 3 of 3 alone, before and after); speed the same within the noise (colony brain 0.401 ms before, 0.406 after; 6 stress runs a side). Details in
   `docs/HISTORY.md`.
10. **The shared young body names the termite soldier rank** (found building step 9c, 2026-10-22; to plan with the
   user). `entity/AntLarvaEntity` is the young of both species (the harvester larva, the termite nymph), and it still
   keeps "what it grows into" as a `TermiteSoldierEntity.Rank` (synced so a nymph growing into a soldier gets its big
   head, saved as a byte `soldier`). Step 9c put a caste name in front of it (`getGrowsInto`, `setGrowsInto`), but the
   body underneath still names the termite type, so the framework could not hold the young body after the split. Fix:
   the castes hook gives each of its castes a small number for syncing and saving (the soldiers' today's 1 and 2, so
   saved worlds keep loading), the larva keeps only that number and asks the colony for the caste, and the renderer
   reads the number (the renderer stays with the species). Nothing changes in-game. Small (one body, about 10 lines,
   plus the renderer's read). The other shared-body leftover, `AbstractAntEntity` naming the bread and the fungus
   nodule as "nurse food it carries" (1 line), is the "what I carry" slot already listed below for part 3.
   **BUILT 2026-10-22** on branch `claude/apex-step10` (from step 9, not merged; planned with the user, my picks: the
   save word stays `soldier`, each caste's number written out in the hook, not its place in the list): the castes hook's
   `numberOf` and `casteOf` (the soldiers' 1 and 2), `Colony.casteNumber` and `casteOfNumber`, the young body keeps
   only the number and asks its colony (`getGrowsInto(colony)`), the nymph renderer reads the number. No shared file
   names the soldier rank now (only the soldiers' own files, the registry rows and the renderer). Checks in
   `AwayTests.partsAreTheSpecies` and `TermiteSoldierTests`; `soldiers,away,colony` 36 of 37 (the neighbours flake, BUGS 55, 3 of 3 alone) on 6 servers. Nothing
   changes in-game. **Steps 6 to 10 confirmed in-game by the user 2026-10-22** ("everything is working"; tried on termites only: the user plans to take the red harvester ant out of this mod for other mods, and to rework its behaviour when those are planned).
   Full run 139 of 141 on 6 servers (BUGS 62 and the half-built stage 14 test, both 3 of 3 alone); speed the same
   within the noise (whole mod without route searches 1.85 ms on `master`, 1.82 on the branch). **Merged into `master`
   2026-10-22 with the user's OK** (steps 6 to 10).

Then the split's own plan (`docs/plans/mod-split.md`) can draw the framework's wall along the registry: shared parts
in, species parts out. **Planned 2026-10-22** (`docs/plans/mod-split.md`, steps S0 to S10, waiting for the user's
calls). Found while planning it (the "ideas found along the way" rule):

**Before the split:**
11. **The termite pieces inside shared parts block the split** (left "for part 3" in step 9): the galleries
   (`colony/Galleries`, 2,199 lines) held and called by the shared `Nest`, `ForagingHoles` and `BuildLife`, with the
   feeding galleries, mud casts and tunnel blocks; the comb, dead wood and bare patches in `FoodStore`. The framework
   could not compile without them, so they move in the split itself: steps S2 and S3 of `mod-split.md`. Nothing
   changes in-game.

**Before part 3 (the termites), its first step (the user's call 2026-10-22, `mod-split.md` call 4):**
12. **Each species' numbers read by its own part.** `species/SpeciesProfile` (1,562 lines) holds every species' number
   groups (mound, galleries, garden, soldiers, alarm; door, patrol, search, recruits, trunk trails, night soil). Only
   numbers, so the wall holds, but a new species (the Matabele ant) would need an edit in the framework's reader. Fix:
   a part's row brings the reader for its own section, the species files keep their exact layout. Nothing changes
   in-game.

**Before the harvester ant leaves the mod (the user's later plan):**
13. **The legs and body tests ride on the harvester ant.** The walking scenes (`NavigationTests`, 769 lines), half of
   `BodyTests` and the trail scenes use harvester bodies; the shared legs would lose most of their walking tests with
   it. Fix: those scenes on a savanna body (a termite worker, later the Matabele ant) first. Nothing changes in-game.

After it: `colony/FoodStore.java` (1,077 lines, the seed granary and the fungus comb in one; 23 of the 57 colony files
mix harvester-only and termite-only work). Smaller: errand helper step 4 (`ScoutGoal` in three); later, in the
termite and ant parts, `entity/AbstractAntEntity.java` (1,837 lines; every carried load its own switch, could be one
"what I carry" slot).

## Also found 2026-10-22 (the speed check of steps 6 and 7)
In 1 stress run in 3, on `master` and the branch alike, most of the 61 or 101 real termites stop ticking a few
seconds in (the stopwatch counts 12 to 15 insects ticking, the test counts all of them alive): their bodies are there
but not run. **Cause found the same day (a test-only cause, not the colony code):** the game test server puts each
test at a random place in the world and loads (with ticking) only the chunks under the 65 x 65 arena. The termite
nest sits 12 blocks from the arena's west and north edges, and its gallery arms reach about 14 to 16 blocks, so the
west and north arm ends lie outside the arena. When the arena's edge falls near the edge of its chunk (the random
place decides it, about 1 run in 3), those arm ends are in a chunk Minecraft keeps but does not run; each worker that
walks there stands still for the rest of the test. Proved with a log of every termite in a stalled run: all 48 that
stopped stood at one spot 2 blocks west of the arena, in the chunk west of it, which reported "not ticking"; every
termite that still moved stood in the arena's chunks. **Fixed the same day** (the user's OK): every test's ground within 32 blocks of its arena runs
(`TestIsolation.GROUND_ROUND`); stress test 8 of 8 clean. The old gallery flakes (`docs/BUGS.md` 55) were not this. In a real game the same thing can happen at the edge of the player's simulation distance: a worker
walking into the outermost ring of loaded chunks stands still there without being written down as away
(`AwayLife.freeze` only hears of chunks that unload), and keeps a real-mob place. Rare and harmless today; worth a
look when real mobs are spawned only where the player can see.

## Also found 2026-10-20
The termite stress test shows, in old and new code alike, bursts of failed route searches about once a second for a
minute (800 to 1,300 steps each, up to 27 ms the longest); cause not found yet. Worth finding (a leave-alone memory is
the likely fix, `docs/BUGS.md` 42).
**Found 2026-10-23 (part 3, T4):** most bursts were builders searching for a mound job nobody can work from (fixed,
no change in behaviour); the rest, a nurse cut off from the queen, is a "your call" in `docs/plans/apex-termites.md`.

## Part 3, the termites: planned 2026-10-23 in `docs/plans/apex-termites.md` (steps 12 and the route bursts are in it)

## Timing (user, 2026-10-21)
The user leaves for California in 6 days, about 5 days of work left (weekly quota resets 2026-10-21 10 PM); "I don't
want to rush the project, so whatever you think is best". My plan, agreed: part 1 (the brain, about 12 to 18 steps)
and part 2 (the split, about 6 to 10 steps) at a careful pace, about 3 days; then part 3 (the termites, the Matabele
ants' prey) with the days left. Everything merged into `master` with a full run before the trip, never a half-done
branch left behind; the Matabele ants (M8) start after the trip.
