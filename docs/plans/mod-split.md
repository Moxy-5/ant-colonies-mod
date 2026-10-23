# The split into three mods (Project Apex part 2, roadmap 3c)

**Status:** planned with the user 2026-10-22; **the user's calls made 2026-10-22: every recommendation taken**
("Yes, go with your recommended calls"): the names "Colony Framework" (`colonyframework`), the savanna mod keeps
`antcolonies` and "Ant & Termite Colonies", "Desert Colonies" (`desertcolonies`); the in-game changes that come with
the split accepted (three mods in the list, the harvester's names in commands, item order within a tab); old
harvester worlds forwarded (dropped by the user's change below: old worlds need not load); the species numbers moved in Apex part 3 (call 4 (a), `apex.md` step 12). The trip date
is still to come from the user (it sets the stop rule). Nothing built.

## What the user decided (2026-10-22)
- Three mods in **one repository**: three folders (Gradle subprojects: one build split into parts that are built and
  tested together), built and tested by one command:
  - the **colony framework**: brain, legs, bodies and animations, species profiles, foraging, trails, digging, tools,
    the test kit;
  - the **savanna** mod: the termites, the Matabele ants later, the giant trees;
  - the **North American desert** mod: the red harvester ant.
- **The framework cannot see species code** (the compiler refuses it, not only a habit).
- **Nothing changes in-game.** ~~Old saved worlds keep loading.~~ **Changed by the user 2026-10-22** (while S1 was
  being built): "it's fine to just delete my past worlds, I only used them for testing, it would be a pain to try and
  preserve them". So no work goes into keeping old worlds loading (the census and the name forwarding are dropped);
  the user's in-game tests use new worlds, and old test worlds may be deleted when they are in the way.
- The harvester ant moves to the desert mod as it is; the user will take it out later and rework it then. In-game
  tests are on termites only (memory `harvester-cut-later`).
- Small steps, each with a full test run on 6 servers; an in-game test by the user every 5 steps, nothing merged into
  `master` before that test; decisions in two lists ("my picks", "your calls"); everything merged before the user's
  trip, no half-done branch left behind.

## What the map shows today (measured 2026-10-22 on `claude/apex-questions`)
258 Java files. Sorted by where they belong (my sorting, by what each file does):

| future mod | code files | lines | game test files | lines |
|---|---|---|---|---|
| framework | 120 | 30,300 | 16 (5 the test kit, 11 scenes of both species) | 8,200 |
| savanna | 84 | 20,200 | 15 | 7,000 |
| desert | 23 | 6,500 | 0 (its scenes sit in the 11 mixed files) | 0 |

Apex part 1 cleaned the brain, but **30 of the 120 framework files still name savanna or desert code**. They fall into
four kinds:
1. **The sign-up and event files** (`AntColoniesMod`, `registry/ModBlocks`, `ModItems`, `ModEntities`, `ModFeatures`,
   `ModStructures`, `ModEvents`, `client/ClientEvents`): every block, mob and renderer of both species signed up in one
   list. Mechanical: each mod gets its own.
2. **Termite pieces still inside shared parts** (Apex step 9 left them "for part 3", but they block the split, so they
   move here): the galleries (`colony/Galleries`, 2,199 lines) are held and called by the shared `Nest` (14 lines),
   `ForagingHoles` (14) and `BuildLife` (4), with the feeding galleries, mud casts and tunnel blocks; the shared
   `FoodStore` (20 lines) names the fungus comb, mud casts, bare patches and the dead tree blocks.
3. **Small leftovers:** `ColonyParts` (the parts registry) still holds every species' rows; the species reader
   `SpeciesProfile` names the night soil and the mound block (2 lines); `PelletLayer` picks the termite corpse look
   by asking "is this a termite?"; the tree commands live in the shared `ColonyCommands`; `TermiteWorkerEntity` borrows
   the word "worker" from the harvester body; the shared egg, young and corpse renderers name species textures; the
   mod's name (`AntColoniesMod.MODID`) is used 76 times in 55 files, for framework and species things alike.
4. **Tests:** 11 shared test files hold scenes of both species (`AwayTests`, `PopulationTests`, `NeighbourTests`,
   `BodyTests`, `HillTests`, ...); `TestAnts` and `TestFilter` name both species; 4 termite or tree scenes put a
   harvester ant down as "an insect from outside" (the thorny twig, the royal pair, the tunnel, the chimney).

What the game saves by name (since the user's change above this no longer has to be kept; the savanna mod keeps its
names anyway, which costs nothing):
- every block, item and mob is saved as `modname:thing` (`antcolonies:termite_mound`, `antcolonies:worker_ant`);
- the giant tree structures are saved in the chunks by name (`antcolonies:...`);
- each colony saves its species as `antcolonies:macrotermes` / `antcolonies:red_harvester_ant`, and each part under
  its part name;
- two save files, `antcolonies_colonies` (the colonies) and `antcolonies_forest` (the tree book).

## The shape after the split
```
C:\Ant Mod\
  build.gradle, settings.gradle   the root: runs the game with all three mods (runClient, runGameTestServer,
                                  runGameTestShard1..n, runFilm, build), same commands, same run folders
  framework\src\main, src\test    colony framework   (mod name to pick, my suggestion "colonyframework")
  savanna\src\main, src\test      the savanna mod    (keeps the name "antcolonies")
  desert\src\main, src\test       the desert mod     (mod name to pick, my suggestion "desertcolonies")
  run\, run-test\, run-test-shards\, run-film\   unchanged (the user's worlds stay in run\saves)
  tools\, docs\, references\, Blockbench\        unchanged
```
- savanna and desert each depend on the framework; the framework on neither; savanna and desert never see each other
  (so the desert folder can later be deleted, or moved to its own repository, without touching anything else).
- **The wall is drawn along the parts registry** (as `apex.md` planned): the 18 shared parts stay in the framework;
  king care, mound, closed holes, hidden trips, fungus garden, mushrooms, soldiers, alarm (and the galleries, step S2)
  go to savanna; hill, bakery, trunk trails, patrol, forager spots, door, recruits go to desert. Each species mod signs
  its parts up when the game starts (`ColonyParts.register`, built for this in Apex step 2); a part's tick places
  travel with it, so the tick order stays exactly today's.

## Steps
Every step: `./gradlew build`, a full run on 6 servers (about 12 minutes), committed and pushed. Nothing changes
in-game in any step. Block 1 (S0 to S5) still builds one mod and only gets the code ready; block 2 (S6 to S10) makes the
three mods.

### Block 1: ready to cut (one mod still)
**S0. Clean start** (15 minutes). This branch, `claude/apex-questions`, holds 10 commits that are not on `master`: the
test ground fix (`TestIsolation.GROUND_ROUND`, the termites standing still in an unticked chunk) and the M8 design
notes from the questionnaire. With the user's OK, merge them into `master`; the split's branch,
`claude/mod-split`, starts from there.

**S1. The wall checker** (`tools/wall_check.py`, about half an hour): lists every file meant for the framework that
names savanna or desert code, and any savanna file naming desert code or the reverse. **BUILT 2026-10-22** (branch
`claude/mod-split`): 39 files today, the 30 framework files above plus 9 termite test files that borrow the
harvester's word "worker" (`WorkerAntEntity.CASTE`). It must reach zero by S5 (`--strict` from then); from S6 the
compiler takes over and the checker is retired. No game code changed, so no full run of its own: the next one is
S2's. (The old-world census planned here was built and dropped the same day, after the user's change above.)

**S2. The galleries out of the shared parts** (about 4 to 5 hours, the biggest step). The galleries become a savanna
part of their own (`galleries`, listed in `macrotermes.json`, saved where they are saved today, so old saves load
as they are); `Nest`, `ForagingHoles` and `BuildLife` reach them only through hooks and questions (Apex steps 6 to 8's
machinery): "where are the hidden ways", "may a hole open here", "the builders' time on return". The feeding galleries,
the mud casts and the tunnel blocks move with them. `TunnelRoute` and `RunwayRoute` (a route that follows the ground;
no species in them) stay shared, ready for any species that digs or builds runways.

**S3. The other leftovers** (about 2 to 3 hours). `FoodStore`'s comb, dead wood and bare patches through hooks; the
species rows of `ColonyParts` signed up from a species file of code (still one mod); `SpeciesProfile`'s 2 lines;
`PelletLayer` and the egg, young and corpse renderers take their looks from the species' body table
(`SpeciesBodies`); the tree commands into a savanna commands file (same words, `/antcolonies trees ...`); the termite
worker uses the shared worker word.

**S4. Sign-ups and tests sorted** (about 3 to 4 hours). The sign-up and event files split into framework, savanna and
desert files (still one mod name); the language file, tags, loot tables and the rest of the data sorted the same way
(still in one folder). Tests: the test kit made species-neutral (`TestAnts`, `TestFilter`: each species signs up its
own test classes and categories); the 11 mixed test files split scene by scene into savanna and desert files, every
check kept; the 4 scenes that used a harvester ant as an outsider use a termite from no colony (same meaning: an
insect that does not belong, does not fit, is not pricked).

**S5. Packages** (about 2 hours, mostly done by a script). Java's names for folders of code ("packages") must differ
between mods (the mod loader refuses two mods sharing one), so the species code moves to `com.antcolonies.savanna.*`
and `com.antcolonies.desert.*`; the framework keeps `com.antcolonies.*`. The wall checker reads zero. The anim lab
checks and the guard films (the body code moved) pass unchanged.
**The user's in-game test 1** (about 20 minutes, termites only, a new world), see the checklist then.
**Then merge block 1 into `master`** with the user's OK.

### Block 2: three mods
**S6. The framework becomes its own mod** (about 3 to 4 hours). The folders `framework\` and `savanna\` (the code
moved with git, so its history is kept); the framework gets its own mod name and main file (its own log name);
`savanna` keeps `antcolonies` and, until S7, still holds the harvester. The root runs the game with both. The
compiler now refuses any framework file that names species code. The framework signs up nothing in the game's lists
except what is truly shared (the egg, see my picks).

**S7. The desert mod** (about 2 to 3 hours). The harvester's code, models, textures, species file and tags into
`desert\` under the desert mod's own name (`desertcolonies:worker_ant`, `desertcolonies:red_harvester_ant`, ...).
No forwarding of old names (the user's change, 2026-10-22): a world saved before this step loses its harvester
things, as with any removed mod; termite and tree things keep their names.

**S8. Tools, scripts and GitHub** (about 2 to 3 hours). One small paths helper so every tool finds each model's files in
its mod: the art scripts, the anim lab, the body plans, the model and tree viewers, the guard films, `film.sh`,
`run_game_tests.sh`; same commands. The GitHub test workflow updated. The guard films and anim lab checks pass
unchanged.

**S9. Each mod on its own** (about 2 hours). Proof that the walls hold at run time too: the framework builds alone; the
framework with savanna only (no desert) starts and passes every termite and tree test; the framework with desert only
starts and passes the harvester tests. This is what makes the harvester's later departure a clean cut.

**S10. The full check and the merge** (about 2 hours). Full run, the stress test before and after (nothing is meant to
get faster; checked once in case), guard films, notes (`CLAUDE.md` "Tech
stack" and folders, `docs/DESIGN.md` architecture, `docs/HISTORY.md`).
**The user's in-game test 2** (about 30 minutes, termites only, a new world).
**Then merge block 2 into `master`** with the user's OK.

## Timing (and the trip)
- About 11 to 14 hours of building for block 1 and 11 to 14 for block 2 (the census and the forwarding dropped saved
  about 3), plus 10 full runs (about 2 hours of PC time). At the pace of Apex steps 6 to 10 (five steps in one day)
  that is **about 3 days in all**: block 1
  with test 1 and its merge in about 1.5 days, block 2 with test 2 and its merge in about 1.5 to 2 days.
- **Stop rule:** block 2 starts only if at least 2 full days are left before the trip. If not, block 1 stays merged
  (a clean place to stop: one mod, the code ready to cut, nothing half-done) and block 2 waits until after the trip.
- Apex part 3 (the termites) most likely waits until after the trip.

## My picks (code choices; you may skip this list)
1. **Savanna keeps the name `antcolonies`**: every termite, mound, garden and tree block keeps its saved name, so
   a termite world made today still opens after the split (free, so kept); the harvester's things get new names.
2. **The root only runs the game**: `./gradlew runClient`, `runGameTestServer`, the shard runs, `runFilm` and `build`
   keep their words and their folders (`run\`, `run-test\`, ...); `ANTCOLONIES_TESTS` and the category names stay.
3. **Save names kept**: `antcolonies_colonies` and `antcolonies_forest` stay the save file names, each part saves
   under its part name as today.
4. **One shared egg** signed up by the framework (both species lay the same egg today, one mob kind), as
   `colonyframework:ant_egg`; every other body (larvae, nymphs, corpses, castes) goes with its species.
5. **Species files keep their layout**; each sits in its own mod's data folder; the harvester's tags move under the
   desert name and its file points to them.
6. **One `/antcolonies` command**: the framework holds it, each species mod adds its own sub-commands to it (Minecraft
   merges them); every command keeps its words.
7. **Packages:** the framework keeps `com.antcolonies.*`, the species move under `savanna` and `desert` (fewest
   changed lines; required by the mod loader, see S5).
8. **Shared, not species:** `TunnelRoute`, `RunwayRoute`, `GroundShakes` (feeling footsteps), `MouthToMouth`,
   `SealEntranceGoal`, `StandByQueenGoal` and the store pile block (`SeedPileBlock`, the base of the fungus comb too)
   stay in the framework: none names a species, and many species do these things.
9. **Unit tests** (`./gradlew test`) go with the code they test; the framework has no game test scenes of its own
   (its systems are tested through the species' scenes, all run by the one command).
10. **The 4 outsider scenes** use a termite from no colony in place of the harvester ant; the ant-shaped check ("an
   ant cannot get into a termite tunnel") comes back with the Matabele ant in M8 step 1.
11. **Within the savanna mod**, the Matabele ants may know the termites directly (predator and prey of one
   ecosystem); the shared fight system (M8 step 2) is built in the framework, so harvester neighbour fights (and the
   jungle's army ants later) reuse it.
12. **Checks:** the wall checker until S6, the compiler after; the guard films and anim
   lab at S5 and S8; one speed check at S10.

## Your calls (in ant terms; **decided 2026-10-22: every recommendation below taken**)
1. **The names.** Three mods show up in the game's mod list. Names to pick:
   - the framework: my suggestion "Colony Framework" (`colonyframework`);
   - the savanna mod: keeps `antcolonies` (my pick 1, needed for old termite worlds); its shown name stays "Ant &
     Termite Colonies" unless you want another (the shown name can change freely, the saved name cannot);
   - the desert mod: my suggestion "Desert Colonies" (`desertcolonies`).
2. **What you will see change in-game (red flag; these come with any split, nothing else should change):**
   - three mods in the mod list instead of one;
   - the harvester's things get the desert mod's name in commands: `/summon antcolonies:worker_ant` becomes
     `/summon desertcolonies:worker_ant` (queen, seeds, grama, seed and bread piles alike). Termite, mound, garden and
     tree names do not change;
   - the spawn eggs and blocks stay in the same creative tabs, but harvester and termite items may swap places within
     a tab.
3. ~~**Old harvester worlds are forwarded**~~ **Changed by the user 2026-10-22: old worlds need not keep loading**
   (they were test worlds); no forwarding, no census. An old world opened after S7 loses its harvester nests and ants
   (and after S6 its eggs); termite colonies, mounds and trees open as they are.
4. **What it means for the Matabele ants (M8):**
   - they go in the savanna mod beside the termites and the trees, so their scouts can find termite feeding spots and
     their raids can break runways without any extra plumbing: **easier**;
   - the fight system goes in the framework, so the harvester's neighbour fights and the jungle's army ants reuse it.
     A fight feature is then written in two folders, still one change and one test run: **no extra time for you**;
   - **one choice:** today the shared species-file reader holds every species' number groups (mound, galleries,
     soldiers; door, patrol, recruits). After the split they still sit in the framework (they are only numbers, so the
     wall holds). Until they are moved, adding the Matabele's numbers (raid size, scouts) means one edit in the
     framework's reader. **(a) My recommendation:** move them in Apex part 3, as its first step, before M8 (keeps
     the split about half a day shorter); **(b)** move them in the split, as one more step. Your species files look
     the same either way.
5. **Your time:** two in-game tests of about 20 to 30 minutes, after S5 and after S10, termites only; the same launch
   command (`./gradlew runClient` in `C:\Ant Mod`), your worlds where they are. Full runs take as long as today (same
   tests), in new worlds. And: **when exactly do you leave?** The stop rule above depends on it.

## Found while planning (the "ideas found along the way" rule)
- **The termite pieces inside shared parts** (galleries in `Nest` and `ForagingHoles`, the comb in `FoodStore`) were
  left for part 3, but they block the split: now steps S2 and S3 here (`apex.md` step 11).
- **The species numbers in the shared reader** (your call 4): `apex.md` step 12, planned for part 3 unless you pick (b).
- **The legs and body tests ride on the harvester ant:** the walking scenes (`NavigationTests`, 769 lines), half of
  `BodyTests` and the trail scenes use harvester bodies. When the harvester leaves, the shared legs would lose most of
  their walking tests; before it leaves, those scenes need a savanna body (a termite worker, later the Matabele ant).
  `apex.md` step 13.
- **10 commits on this branch are not on `master`** (the test ground fix, the M8 design notes): step S0.
