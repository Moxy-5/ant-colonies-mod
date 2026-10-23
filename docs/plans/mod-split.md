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
**Rounds (the user, 2026-10-22: "go as fast as possible while still keeping the mod in a good spot"; the trip date
no longer matters).** The steps below are built in 5 rounds; each round is one `./gradlew build`, one full run on 6
servers (about 12 minutes), one commit and push. Nothing changes in-game in any round. While a round's full run goes,
the next round is started, but not committed until that run passes. One in-game test at the end, one merge.
- **Round 1: S2** (S0 and S1 done before it).
- **Round 2: S3 + S4**, all the remaining moving behind hooks, the sign-ups and the tests sorted (the same kind of
  careful moving; one full run checks it as well as two).
- **Round 3: S5 + S6**, the packages renamed and the framework its own mod (from S6 the compiler refuses species code
  in the framework, a stricter check than the wall checker, so the checker's `--strict` zero is only needed at the end
  of the round).
- **Round 4: S7 + S8 + S9**, the desert mod's name, tools and GitHub, each mod on its own (small jobs, none touch how
  the insects behave).
- **Round 5: S10**, the full check; **then the user's one in-game test** (about 30 minutes, termites only, a new
  world) and **one merge into `master`** with the user's OK. `master` stays untouched until then.
- **Not joined, on purpose:** rounds 3 and 4 (renaming the folders, making new mods and renaming the harvester at once
  would make a failure very hard to trace), and no full run is skipped.

The step texts below keep their old block headings and in-game test lines for the record; the rounds above replace
them (no test 1 and no block 1 merge).

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
no species in them) stay shared, ready for any species that digs or builds runways. **BUILT 2026-10-22** (round 1): the `galleries` part and the `Underground` and `FeedingWays` sockets (`docs/HISTORY.md`); wall checker 36 files; full run 138 of 141 on 6 servers, the socket check fixed, the other two the old flakes (BUGS 55, 2 of 6 alone as before; 62).

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
insect that does not belong, does not fit, is not pricked). **S3 and S4 BUILT 2026-10-22** (round 2, `docs/HISTORY.md`): the species rows in `SavannaParts` and `DesertParts`, three starts (`AntColoniesMod`, `SavannaMod`, `DesertMod`), the registry and event files split, the test kit species-neutral (`TestScenes`, `TestWatch`, `AwayKit`, `TestAnts.lone`; each species signs up its tests in `gametest/SavannaTests` and `DesertTests`), the mixed tests split into `Termite*` and `Harvester*` files; the data stays in one folder, `python tools/wall_check.py --data` lists the 4 files naming both sides (to split by their entries at S6). Wall checker 36 to **0** files; full run on 6 servers 140 of 143 (12 minutes): the old flake BUGS 55; the save check expecting the bare patches as a new part (box 2, fixed, `away` all pass); and the termite half of the old noticing scene, `termitesFeelNoSneakingStepsButFreezeAtAJump`, which passed 2 of 2 alone (a new flaky test, proposed as a session of its own).

**S5. Packages** (about 2 hours, mostly done by a script). Java's names for folders of code ("packages") must differ
between mods (the mod loader refuses two mods sharing one), so the species code moves to `com.antcolonies.savanna.*`
and `com.antcolonies.desert.*`; the framework keeps `com.antcolonies.*`. The wall checker reads zero. The anim lab
checks and the guard films (the body code moved) pass unchanged. **BUILT 2026-10-22** (round 3, `docs/HISTORY.md`): 173 files moved with `git mv` (savanna 118 game files and 11 unit tests, desert 40 and 4), sorted by `wall_check.py --sides` (unit tests by the species classes they name); package lines, imports and full names rewritten by a script; about 100 shared declarations that were only visible inside their old folder made public (the test kit, `ColonyPart`'s sign-up methods, `BuildLife`, `FieldLife`, `AwayLife`, ...). Mod name, registry names and save names unchanged. Checks: `./gradlew build` with its 163 unit tests, `wall_check.py --strict` 0, `anim_lab.py check` for the worker ant (0 problems) and the five termites (only the old `peek` clip's known findings). The full run is done by S6's session first (the user's rule); the guard films at the end of round 3.
**The user's in-game test 1** (about 20 minutes, termites only, a new world), see the checklist then.
**Then merge block 1 into `master`** with the user's OK.

### Block 2: three mods
**S6. The framework becomes its own mod** (about 3 to 4 hours). The folders `framework\` and `savanna\` (the code
moved with git, so its history is kept); the framework gets its own mod name and main file (its own log name);
`savanna` keeps `antcolonies` and, until S7, still holds the harvester. The root runs the game with both. The
compiler now refuses any framework file that names species code. The framework signs up nothing in the game's lists
except what is truly shared (the egg, see my picks).
**BUILT 2026-10-23** (round 3, `docs/HISTORY.md`): the code moved with `git mv` into `framework\` and `savanna\` (two
Gradle subprojects; the root only runs the game, with both mods, by the same commands and run folders; shared settings in
`gradle/mod-common.gradle`); the framework's start `ColonyFrameworkMod` (`colonyframework`, "Colony Framework", its own
log name), `SavannaMod` and `DesertMod` each with their own name constant and log (`antcolonies` both until S7);
the framework signs up only the egg (`colonyframework:ant_egg`, its texture and name moved with it); the species ids out
of the framework (`SavannaSpecies`, `DesertSpecies`); the framework's tags under its own name
(`colonyframework:diggable`, `seeds`, `rich_seeds`, `nurse_food`, `rich_nurse_food`, `old_plugs`, `tree_crowns`): the
framework's files list only Minecraft's things, the savanna mod adds its own to the same tags (the game joins them); the
termite tags into `SavannaTags`. Found by the unit tests: the framework read the body plans from a fixed `antcolonies`
folder of its own, which a separate mod cannot do; now each species mod signs its folder up (`BodyPlans.signUp`). The
4 data files naming both sides: their framework part split off; the savanna and desert entries share the savanna's
files until S7 gives the desert its own folder (one mod cannot hold two copies of one file). Five unit tests that read
species files moved to the savanna. The wall checker keeps watching savanna against desert until S7 (the compiler
guards the framework). Tools point at `savanna\src\main
esources` (S8 does the proper paths helper). Checks:
`./gradlew build` with the 163 unit tests (70 framework, 93 savanna), `soldiers,legs` 29 of 29 on 6 servers (both mods
load), anim lab unchanged (worker 0, termites only the old `peek` findings), guard films 15 of 15 scenes unchanged.
Full run owed: done first by S7's session. S5's full run (by this session first): 142 of 143, the old flake BUGS 55.

**S7. The desert mod** (about 2 to 3 hours). The harvester's code, models, textures, species file and tags into
`desert\` under the desert mod's own name (`desertcolonies:worker_ant`, `desertcolonies:red_harvester_ant`, ...).
No forwarding of old names (the user's change, 2026-10-22): a world saved before this step loses its harvester
things, as with any removed mod; termite and tree things keep their names.
**BUILT 2026-10-23** (round 4, `docs/HISTORY.md`): the folder `desert\` (a third Gradle subproject, `desert/build.gradle`,
its own mod file, `desert_mod_id=desertcolonies` and `desert_mod_name=Desert Colonies` in `gradle.properties`); the
desert's 41 code files and 4 unit tests moved there with `git mv`, its 118 asset and data files (models, textures, the
worker's geo, clips and body plan, blockstates, loot tables, grama and deep soil worldgen, the species file) moved to
`assets/desertcolonies` and `data/desertcolonies`, every `antcolonies:` name inside them renamed `desertcolonies:`;
`DesertMod.MODID` is `desertcolonies`. The 4 files naming both sides split by their entries: the framework's tags
(`diggable`, `nurse_food`, `rich_nurse_food`, `seeds`, `old_plugs`) and Minecraft's `mineable/shovel` each have a savanna
and a desert file (the game joins them), the name list (`en_us.json`, 21 harvester lines) and the body plan list
(`index.json`) one per mod. The game test arena copied into the desert mod (`data/desertcolonies/structure/arena.nbt`,
each mod's tests look in their own mod; my pick, so the desert can run alone, S9). The root runs the game with the
three mods and the game tests of both species (`gameTestNamespaces`). The framework's built-in harvester numbers name
`desertcolonies:seed_pile` and `burrowed_dirt`; the in-game camera looks an insect's name up in any mod
(`FilmDirector.insect`, so the shot lists keep `ant worker_ant`), `h_crowd.film` summons `desertcolonies:worker_ant`;
the termite garden test's "no seed heap" check matches any mod's `seed_pile`. Unit tests that read the worker ant's
files moved with it, every check kept: `SpeciesProfileTest` split into the savanna's (termite checks) and the desert's
`HarvesterProfileTest`; `WorkerAnimationsTest` moved; the worker's parts of `ModelFilesTest` and `BodyPlansTest` into
`HarvesterModelFilesTest` and `HarvesterBodyPlansTest`. The compiler now guards all three walls (savanna and desert each
see only the framework), so the wall checker is retired (`--strict` still reads 0, `--data` says retired). Checks:
`./gradlew build` with 170 unit tests (framework 70, savanna 65, desert 35; 7 more than 163 because split tests became
more, smaller ones). **No full run in this step** (the user's schedule, 2026-10-23: S8's session runs it, owed since S6).
Left for S8: the tools that read or write the worker ant's or the grama's files still point at the savanna folder
(`body_plan.py`, `anim_lab.py` through it, `grama_art.py`, `corpse_textures.py`, `young_textures.py`, `film.sh` and the
root's film watch folder).

**S8. Tools, scripts and GitHub** (about 2 to 3 hours). One small paths helper so every tool finds each model's files in
its mod: the art scripts, the anim lab, the body plans, the model and tree viewers, the guard films, `film.sh`,
`run_game_tests.sh`; same commands. The GitHub test workflow updated. The guard films and anim lab checks pass
unchanged.
**BUILT 2026-10-23** (round 4, `docs/HISTORY.md`): `tools/mod_paths.py`, the paths helper (each mod's folder, name, assets and data; which mod holds a model; `python tools/mod_paths.py` lists them), used by every tool that reads or writes game files: the anim lab and the body plans find each model in its own mod (the worker ant in the desert, the termites in the savanna; each mod's `index.json` checked), the Blockbench import with the model's own mod name, `ant_models.py` and `grama_art.py` write into the desert mod (grama's files with `desertcolonies:` names), the termite, tree, dead wood, comb and other art tools into the savanna, `young_textures.py` each texture into its model's mod, the viewers read the same; `film_swap.py` swaps all three mods' classes; the camera's watch mode (`FilmWatch`, `tools/film.sh watch`) watches and copies every mod's files (the root's `build.gradle` hands it the three folders). Same commands. GitHub: a fifth job builds the three mods with their unit tests and runs the tools' checks (`mod_paths.py`, `body_plan.py check`, the worker's anim lab check) beside the 4 test shards. **Found by the full run and fixed:** a start-up race, the savanna and desert mods signing up into the same framework lists at the same moment (1 test server of 6 failed to start; `docs/BUGS.md` 64); the five sign-ups now take turns. Found and only noted: `grama_art.py` would undo the big blue grama meadows if rerun (BUGS 63). Checks: `./gradlew build` with 170 unit tests; every art tool without Pillow rerun, each file rewritten the same (the 5 that need Pillow, not installed here, compile only); the viewers' pages the same as before S8; `body_plan.py check` 6 of 6 and its rename test; anim lab (fresh) worker 0 problems, termites only the old `peek` findings; guard films 15 of 15 scenes, 0 pictures changed; watch mode reloaded a changed desert clip file; full run on 6 servers 142 of 143 (12 minutes; also the owed check of S5 to S7), the one failure the old neighbour flake (BUGS 55), 3 of 3 alone.

**S9. Each mod on its own** (about 2 hours). Proof that the walls hold at run time too: the framework builds alone; the
framework with savanna only (no desert) starts and passes every termite and tree test; the framework with desert only
starts and passes the harvester tests. This is what makes the harvester's later departure a clean cut.
**BUILT 2026-10-23** (round 4, `docs/HISTORY.md`): `-PspeciesMods=savanna` or `desert` on the root build (and `ANTCOLONIES_MODS=savanna` or `desert` for `tools/run_game_tests.sh`) starts the game with the framework and that one species mod only, the other left off the game's classpath (both by default, same commands as before). Checks: the framework builds alone, 70 of 70 unit tests, also in a copy of the repository holding only `framework\`; framework + savanna (the logs show no `desertcolonies`): every termite and tree category, 83 of 85 on 6 servers (8 minutes), the two failures the old flakes BUGS 62 (passed 3 of 3 rerun) and 55 (2 of 3 rerun, its known 1-in-3 failure alone, the same "too few meetings" message); framework + desert (only `colonyframework` and `desertcolonies` loaded): every harvester category, 58 of 58 on 6 servers (9 minutes); `./gradlew build` with both, 170 unit tests. No game code changed; nothing changes in-game.

**S10. The full check and the merge** (about 2 hours). Full run, the stress test before and after (nothing is meant to
get faster; checked once in case), guard films, notes (`CLAUDE.md` "Tech
stack" and folders, `docs/DESIGN.md` architecture, `docs/HISTORY.md`).
**BUILT 2026-10-23** (round 5, `docs/HISTORY.md`): `./gradlew build` passes (170 unit tests); **full run on 6 servers 143 of 143** (12 minutes, also the check of S9); stress test 3 runs of `master` (before the split) and 3 of this branch taking turns, every 5-second window (228 a side, every termite ticking): colony brain 0.365 ms a tick before, 0.374 after; the whole mod without route searches 1.76 before, 1.75 after; route searches 0.56 before, 0.47 after (the known bursts): the same, as expected; guard films 15 of 15 scenes, 0 pictures changed (drawing 400 ants 33 ms a frame, 25 to 27 when first measured: the body code is untouched, so read as the PC's state; noted only); notes: `CLAUDE.md` "Tech stack" and architecture, `docs/DESIGN.md` "Architecture: three mods". No game code changed. Next: the user's one in-game test, then the merge into `master` with the user's OK.
**The user's in-game test 2** (about 30 minutes, termites only, a new world).
**Then merge block 2 into `master`** with the user's OK.

## Timing
- **With the rounds (2026-10-22): about 2 days**, 5 full runs in place of 10, no waiting between rounds, one in-game
  test. The old estimate, for the record:
- About 11 to 14 hours of building for block 1 and 11 to 14 for block 2 (the census and the forwarding dropped saved
  about 3), plus 10 full runs (about 2 hours of PC time). At the pace of Apex steps 6 to 10 (five steps in one day)
  that is **about 3 days in all**: block 1
  with test 1 and its merge in about 1.5 days, block 2 with test 2 and its merge in about 1.5 to 2 days.
- ~~**Stop rule:** block 2 starts only if at least 2 full days are left before the trip.~~ Dropped: the user, 2026-10-22,
  "the trip date doesnt matter".

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
   lab at the end of rounds 3 and 4; one speed check at S10.

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
5. **Your time:** ~~two in-game tests, after S5 and after S10~~ **one in-game test of about 30 minutes at the end
   (the rounds, the user's OK 2026-10-22)**, termites only; the same launch
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

**Confirmed in-game by the user 2026-10-23** ("everything works", termites only, a new world). One bug found on the way, not caused by the split: `docs/BUGS.md` 65.
