# Taking the red harvester ant out of the mod (planned 2026-11-23)

**Why:** the user, 2026-11-23: "removing red harvester ants from the mod entirely". The harvester was the first species
(M1 to M6), moved into its own desert mod at the split and was waiting for a rework that is no longer wanted. The mod is
now the savanna: termites, Matabele ants, the giant trees and the river animals. This is job 1 of the three big jobs
(`docs/plans/big-three.md`): it goes first because it is the shortest and both other jobs then start from less code.

**Who builds it:** one session, its own worktree and branch (`claude/harvester-removal`).

## What the harvester is today (measured 2026-11-23)

- **The desert mod, `desert\`:** 53 Java files, about 13,100 lines; 66 of the mod's 422 game tests; about 120 model,
  texture, block and data files (the worker and queen, grama grass, deep soil, ant bread, the hill).
- **Its own colony parts** (all inside `desert\`, they go with it): the bakery, the patrol, trunk trails, the door rule,
  the hill, night soil, recruits, forager spots, searching alone.
- **Good news, measured:** almost none of the shared code is the harvester's alone. Every shared goal it uses is also
  used by the termite worker or the Matabele (`DigGoal`, `GatherSeedGoal`, `StoreSeedGoal`, `ClipGrassGoal`, `ScoutGoal`,
  `SealGoal`, `CarryCorpseGoal`, `StandByQueenGoal`, ...). So the framework keeps working; what the harvester leaves
  behind there is small: sockets only it answers (some of `ColonyHooks`, `ColonyNews` and `ColonyQuestions`, such as the
  mill, the door rule, the trails and the lone search), profile fields only its file fills, `colony/NestSite` (84 lines,
  its nest site scoring), and comments and built-in defaults that name it (38 framework files mention it).
- **Where it really matters, the tests:** two of its test classes test **shared** code with a harvester body:
  `NavigationTests` (16 tests in the `legs` category: walking, climbing, ledges, holes; the termites' climbing and
  tunnel tests and `MatabeleTests` are in `legs` too, so some of the 16 may already have a savanna twin) and `NestTests` (25 tests,
  `antnest`: digging, rooms, the plan kept over a reload, carried loads kept). Its other classes test harvester life
  (away 9, population 5, trails 3, hill 3, body 3, neighbours 2).
- **Films and tools:** the guard films `a_walk`, `b_turn`, `c_climb`, `d_ledge`, `e_ceiling`, `h_crowd` and `n_down_turn`
  are filmed with the harvester worker; they guard the shared legs and body code. `tools/anim_lab.py`,
  `tools/wall_check.py`, `tools/ant_models.py`, `tools/ant_pose_viewer.py`, `tools/grama_art.py` and others take
  `worker_ant` as their insect (13 tools name it).
- **Notes:** `CLAUDE.md` (9 mentions), `docs/DESIGN.md` (48), `docs/ROADMAP.md`, `docs/LIVING-THINGS.md`,
  `docs/STATUS.md`, `docs/TESTING.md`.

## The rules for this job

1. **Shared code keeps its tests.** A harvester test that checks a rule the termites or Matabele also live by (the legs,
   digging, the nest plan, carried loads, the shared away rules) is moved to a savanna insect before the harvester goes.
   A test of something only the harvester did (bread, the granary, the hill, patrols, trunk trails) goes with it. The
   plan's step 1 lists every one of the 66 tests as **moved** or **gone, and why** (the standing rule: never delete a
   check that once caught a real bug in code that is still there).
2. **Nothing the user confirmed in-game for termites, Matabele, trees or animals changes.** Not a number, not a look.
3. **Framework leftovers: delete what nothing runs (my pick).** A socket, profile field or class that no species uses
   after the desert mod is gone is deleted, not kept asleep "for leafcutters": code nothing runs and no test checks
   breaks quietly, and the review (job 3) would spend its time reading it. The exception is anything a planned savanna
   step needs soon (step 1 names each one kept and why).
4. **It can come back.** Before anything is deleted the last commit with the harvester gets a git tag,
   `harvester-last`, pushed to GitHub. The whole species, its models and its tests can be read or brought back from it.
5. **Old worlds:** a world with harvester nests, grama grass or deep soil in it loses them (Minecraft drops blocks and
   mobs of a mod that is gone). Test worlds are disposable (the user), so nothing is converted.

## Steps

Each step ends with `./gradlew build`, the test categories named, and a commit and push on the branch.

1. **The list, before any deleting.** Tag `harvester-last`. Write into this plan: (a) every harvester game test and unit
   test, marked moved (to which insect and scene) or gone (why); (b) every framework socket, profile field and class
   left with no user, marked delete or keep; (c) every tool and guard film that takes the harvester, and what it takes
   instead. No code changes.
2. **Move the shared tests.** The `legs` tests onto a Matabele body (and a termite worker where the rule depends on
   size: the two are 0.5 and 0.15 blocks long against the harvester's 0.3), the shared `antnest` tests onto the
   Matabele's ground nest or the termites' tunnels, the shared away checks into the termite and Matabele away tests.
   A moved test that fails on the new body is a **found bug**: it goes into `docs/BUGS.md` with a number and is fixed
   in its own step, not hidden by loosening the test. Run `legs,antnest,away,colony`.
3. **Re-film the guard scenes.** The seven harvester scenes filmed again with a Matabele minor (where no Matabele or
   termite scene already guards the same thing: `k_climb_minor`, `l_climb_major` and `o_down_turn_major` may already
   cover `c_climb` and `n_down_turn`; then the harvester scene simply goes). The user looks at the new
   `run-film/guard_films.mp4` once and approves it. The tools' default insect becomes `matabele_minor`
   (`anim_lab.py`, `wall_check.py`, the pose viewer); the harvester's own makers (`ant_models.py` worker parts,
   `grama_art.py`) go. Tools are Neon's lane (`docs/HANDOFF.md`): done only when Neon is not mid-work in those files,
   with a line on the board.
4. **Delete the desert mod.** `desert\`, its lines in `settings.gradle`, `build.gradle`, `gradle.properties`,
   `gradle/mod-common.gradle`, `tools/run_game_tests.sh`, `tools/film.sh`, `tools/mod_paths.py`. The `-PspeciesMods`
   switch stays as the socket for a later species mod, with `savanna` its only name for now. Full run.
5. **Clear the framework's leftovers** from the list of step 1(b): the unused sockets, fields and defaults, comments
   that explain a rule "for the harvester". Names born with the harvester but used by everyone are renamed only where
   they would mislead a reader (`GatherSeedGoal` and `StoreSeedGoal` carry the termites' grass and wood: my pick,
   rename to `GatherFoodGoal` and `StoreFoodGoal`; saved names in worlds are not touched). Run `legs,colony,away,
   termitefood,mound`.
6. **The notes.** `CLAUDE.md` (two mods, the species list, the size line, the roadmap, the test categories),
   `docs/DESIGN.md` (the harvester's sections move whole to `docs/archive/harvester.md`, with a pointer),
   `docs/ROADMAP.md` (M6.5's backlog and the desert mod's plans dropped), `docs/LIVING-THINGS.md` (harvester, grama
   grass: DROPPED), `docs/STATUS.md`, `docs/TESTING.md`; a bullet in `docs/HISTORY.md` and `docs/status-log.md`; the
   memory note about the harvester's rework. `docs/HISTORY.md` and `docs/BUGS.md` keep their old harvester lines (they
   are the record).
7. **Prove it and merge.** A full run on 6 servers, the guard films for every ant and termite scene, a 2-day soak on
   real savanna (`tools/soak.sh 6 2`), then merge into `master` at the user's word and switch `C:\Ant Mod`.

**Done when:** `desert\` is gone, the build has two mods, every shared rule the harvester's tests guarded is guarded by
a savanna insect, the full run is as green as before (same known flakes, no new ones), and the user plays the savanna
without a bug report from it.

## What the user should know before saying go

- **Grama grass goes too.** It lives in the desert mod and is the harvester's food plant (a North American grass, not a
  savanna one). Termitomyces, the termites' fungus, is in the savanna mod and stays.
- **No ant in the mod will dig a deep nest or gather seeds any more.** The shared digging and foraging stay, because
  the termites and Matabele use them, but the spiral nest, granary, bread and soil hill were the harvester's own.
- **The framework was proven "many species" by having two species mods.** With one left, the compiler still refuses
  framework code that names savanna code, so the rule holds, but nothing tests a second mod until the jungle mod.
- **Test runs get shorter:** 66 fewer tests, less the ones moved.

---

# Step 1: the lists (written 2026-11-23, before any deleting)

The last commit with the harvester is tagged **`harvester-last`** (commit `fae5a490`, pushed to GitHub).

**A count put right:** the desert mod has **58** game tests, not 66 (the 66 counted each test class's own header line
too): legs 15, nest 24, away 8, population 4, body 2, hill 2, trails 2, neighbours 1. And 9 unit test files.

Words used below. **Moved:** the check now runs on a savanna insect (step 2 writes it). **Covered:** a savanna test
already checks the same shared rule; step 2 reads that test and adds whatever check is missing before the harvester one
goes. **Gone:** the test checks something only the harvester did.

## (a) The tests

### `NavigationTests` (15, `legs`): all moved

All 15 test the shared legs (`AntNavigator`, `AntPathFinder`), nothing of the harvester's. They move whole into a new
savanna class `LegsTests` (category `legs`), walked by a **Matabele minor**; where the rule depends on body size the
same scene is also walked by a **termite worker** (0.15 long against the Matabele's bigger body), and the queen's walk
by a **Matabele queen** (the biggest, slowest body). The ready-made nest stays (four levels and a spiral stair from the
shared nest plan, as terrain to walk). The harvester's hill becomes a plain stepped heap of soil of the same shape,
built by the test (the legs only need its steps), and its soil block becomes vanilla dirt.

| Test | Goes to |
|---|---|
| `queenWalksFromTheRoomUpToTheSurfaceAndBackDown` | moved: Matabele queen |
| `antToldToGoToItsOwnCellSurvivesBeingMovedAway` | moved: Matabele minor |
| `antsClimbOutOfAOneBlockPit` | moved: Matabele minor and termite worker |
| `antsFromAllAroundTheHillReachTheFoodStore` | moved: Matabele minors round the stepped heap |
| `antsOnBumpyGroundOverAHalfBuiltHillReachTheFoodStore` | moved: the same, bumpy ground and a gappy heap |
| `antWalksFromTheSurfaceDownToTheDeepestRoom` | moved: Matabele minor |
| `antWalksFromTheQueensRoomDownToTheRefuseChamberAndBack` | moved: Matabele minor (third level to fourth and back) |
| `antWalksFromTheDeepestRoomUpToTheSurface` | moved: Matabele minor |
| `antsClimbFromEveryRoomThroughTheFinishedHill` | moved: Matabele minors, steadiness check kept |
| `antsStayOnTheShaftWallWhenTheirWalkEndsOrIsStopped` | moved: Matabele minor and termite worker |
| `antsHangingInTheShaftCrawlRoundToTheOtherWall` | moved: Matabele minor |
| `antsGoRoundTheOutsideEdgeOfABlock` | moved: Matabele minor and termite worker |
| `antCrawlsUpsideDownToTheMiddleOfTheRoomCeilingAndHangsThere` | moved: Matabele minor and termite worker |
| `antsGoUnderAnOverhangAndOutTheOtherSide` | moved: Matabele minor and termite worker |
| `antsOnTheShaftWallAndTheCeilingAreStillThereAfterAReload` | moved: Matabele minor |

### `NestTests` (24, `antnest`)

The category `antnest` goes with the class; what moves lands in `colony`, `mound` or `termitefood`.

| Test | Goes to |
|---|---|
| `workersDigTheWholeNestAndBuildTheMound` | gone (four levels, the hill, the diggers' day clock). The shared rule in it, "a colony digs every block of its plan and nothing else", is covered by `TermiteTests.royalPairDigsTheirChamberAndSealsTheEntrance`. |
| `theNestGrowsDeeperOnlyAsTheColonyGrows` | gone (levels unlocked by workers and food: no savanna nest has a second level). |
| `workersCloseEveryHoleInTheNestWallsAndMendDamage` | covered by `TermiteRepairTests.termitesSealOpeningsInTheirNestWithMoundSoil`; a kind of hole the termite test lacks (a crack up to the open air, a pit under a room) is added there. |
| `manyAntsWithPelletsAndOneGapDoNotJam` | gone (pellets carried onto the hill; the carry goal is the harvester's own). |
| `nestSitsOneBlockHigherWhenTheStoneIsTooClose` | gone (fitting several levels into thin soil). |
| `theNestHasFewerLevelsWhenTheSoilIsThin` | gone (the same). |
| `aLoneQueenFoundsHerColonyAndRaisesHerFirstWorkers` | gone (the founding chamber). Founding itself (sealed in, eggs, fed from her own fat, first workers open the entrance) is covered by `TermiteTests` (`aQueenWithoutAKingSealsTheEntranceHerself`, `theFoundingBatchGrowsIntoWorkersWhoOpenTheEntrance`). |
| `workersCarrySeedsAndClipGrassIntoTheFoodStore` | covered by `TermiteFoodTests.termitesGatherDeadPlantsIntoTheirFungusGardenAndRaiseTheQueensEggsOnIt` (the same gather, clip and store goals). |
| `queenIsFedAndHerEggGoesToTheNursery` | covered by the same termite test (queen fed, egg carried to the young's room); the bread is gone. |
| `richBreadStartsABurstOfEggs` | gone (rich bread and the burst of eggs). |
| `eggsBecomeLarvaeAndNursesFeedThemIntoWorkers` | covered by `TermiteTests.theFoundingBatchGrowsIntoWorkersWhoOpenTheEntrance` and the termite food test. |
| `larvaeWaitWhenTheColonyHasEnoughWorkers` | **moved** to termites (`TermitePopulationTests`): fed nymphs wait when the colony is one short of its most workers. |
| `aHungryWorkerEatsOneSeedFromTheFoodStore` | covered by `TermitePopulationTests.termiteWorkersEatNodulesWhenYoungAndOldCombWhenOlder` and `MatabeleFoodTests`. |
| `aStarvingWorkerSlowsDownAndDies` | **moved** as a check into `MatabeleFoodTests.aMatabeleColonyWithAnEmptyPreyPileStarves` (half speed first, then death and a body). |
| `aLarvaThatIsNotFedForTwoDaysStarves` | **moved** to termites (a nymph unfed for its starve time dies, a fed one lives). |
| `aQueenThatIsNotFedForFiveDaysStarvesAndHerColonyDwindles` | **moved** to termites (`TermiteTests`): the queen starves in loaded land, the colony lives on queenless until its last worker dies of old age, then ends and the mound stays. |
| `theNestPlanIsTheSameAfterAReload` | **moved** to termites: with comb lying in the rooms the reloaded colony plans the same royal chamber and gardens. |
| `whatAnAntCarriesIsKeptAfterAReload` | **moved** to a termite worker: soil, forage, a nodule, an egg and a dead nestmate survive a save and load, and the egg is still carried to its room. |
| `workersCarryTheDeadToTheRefuseChamber` | gone (the refuse chamber is the harvester's fourth level). The savanna's own ways with the dead are tested in `TermiteRaidDefenceTests.theDeadAreCarriedOutAndTheParalysedCarriedIn` and `MatabeleFoodTests`. |
| `deadAntsNobodyCanCarryAwayCrumbleToDust` | **moved**: a Matabele body nobody deals with crumbles after the profile's `corpse_decay_ticks`. |
| `queenLivesInTheFoundingChamberUntilHerOwnRoomIsDug` | gone (the founding chamber). |
| `eggsGoToTheQueensRoomWhenThereIsNoNursery` | covered: every savanna nest has no nursery level, so every termite founding test is this case. |
| `workersShareTheJobsAndTheNurseStaysByTheQueen` | covered by `TermiteJobsByAgeTests.termiteWorkersTakeTheJobOfTheirAge`; the "nurse waits by the queen" check is added there if missing. |
| `seedsLyingByAFullStoreAreLeftThereAndFetchedOnceThereIsRoom` (BUGS 44) | **moved** to termites (`TermiteFoodTests`): food lying by the mound while the gardens are full is left there, never picked up and dropped again, and fetched once there is room. The "bread wanted" half is gone with the bread. |

### `HarvesterAwayTests` (8, `away`)

| Test | Goes to |
|---|---|
| `aColonyAwayFoldsItsFrozenInsectsIntoTheNumbersAndPutsThingsBackOnReturn` | covered by `TermiteAwayTests.aFarTermiteColonyLivesAsNumbersAndComesOutWhenAPlayerIsNear`; the checks it lacks (a carried piece put back, a half-fed young one keeping what it ate, old numbers dying while away) are **moved** into it. |
| `aColonyAwayEatsMillsBreadAndRaisesItsYoungAsNumbers` | covered by `TermiteAwayTests.aTermiteColonyAwayAgesItsCombFeedsItsQueenAndEatsAsNumbers`; the bread is gone. |
| `anAntColonyAwayDigsItsRoomsAndHeapsItsHillOutOfSightOnReturn` | covered by `TermiteAwayTests.aTermiteColonyAwayForagesFromTheNightsHolesAndGrowsItsGalleriesOnReturn` (building on return, out of sight); the hill is gone. |
| `antNumbersForageOnlyOutOfSightAndFromWhatTheyRemember` | covered by `TermiteAwayTests.termiteNumbersForageKnownDryGrassOnlyAtNightOrInTheRain`; the "only out of every player's sight" check is **moved** into it if missing. |
| `antPatrolNumbersAreLostOutOfSightOnWaysThroughDanger` | gone (the patrol). Harm out of sight is covered by `TermiteAwayTests.termiteNumbersAreLostOutOfSightOnlyWhereAHostileReallyIs`. |
| `anAntColonyAwayLivesOnThroughASaveAndLoadAndCatchesUpOnReturn` | **moved**: a save and load in the middle of the termites' time away (`TermiteAwayTests`), the colony catching up on return. |
| `aStarvingAntColonyAwayLosesItsYoungAndItsQueen` | covered by `TermiteAwayTests.aStarvingTermitePairAwayDieWhenTheirLandLoads`; young starving as numbers is **moved** into it if missing. |
| `aFoundingAntQueenRaisesHerFirstWorkersWhileAway` | covered by `TermiteAwayTests.aFoundingTermitePairRaiseTheirFirstWorkersWhileAway` (the same shared check in `AwayKit`). |

### The small classes (11)

| Test | Goes to |
|---|---|
| `HarvesterPopulationTests.aCrewWorksWhileNobodyIsNearAndMoreComeOutWhenAPlayerIs` | **moved** to `TermitePopulationTests` (a crew while nobody is near, more when a player is, back in when the player goes). |
| `...insectsComeOutOnlyWhereNoPlayerCanSee` | **moved** to `TermitePopulationTests`. |
| `...workersInTheNumbersDieOfOldAgeAndAreSaved` | **moved** to `TermitePopulationTests`. |
| `...aYoungOneGrowsUpIntoTheNumbersWhenNoMoreRealOnesAreWanted` | **moved** to `TermitePopulationTests`. |
| `HarvesterBodyTests.idleWorkersNapAndWakeWhenHurt` | **moved** to a Matabele minor (new `MatabeleBodyTests`, category `body`): naps, wakes when hurt, never naps with a load, cleans its antennae. |
| `HarvesterBodyTests.insectsNoticeMovingPlayersByTheirPersonality` | **moved** to a Matabele minor (it notices by sight, as the harvester did): the bold come near, the shy back off. |
| `HarvesterHillTests.antQueenOnAHillsideWalksToLevelGroundAndFoundsThere` | gone (the harvester queen's search for a level site). |
| `HarvesterHillTests.antHillOnAStepKeepsItsRoundShape` | gone (the hill). |
| `HarvesterNeighbourTests.oldColoniesYieldToNeighboursAndYoungOnesPushOn` | gone (yielding by turning the foraging away; trunk trails). The shared half (meetings counted, old colonies yield, young push on) is covered by `TermiteNeighbourTests.neighbouringTermitesWallOffTheirGalleries`. |
| `HarvesterTrailTests.foragersWearLastingTrunkTrailsThatFadeAndGrowBack` | gone (trunk trails). |
| `HarvesterTrailTests.aFarMeadowPastTheTrailsTipIsFoundRecruitedToAndPullsTheTrailOut` | gone (trunk trails, recruits, grama grass). |

**In all:** 15 legs tests moved whole, 14 more moved (whole or as a check), 13 covered already (each read in step 2),
16 gone with what only the harvester did.

### The unit tests (`desert/src/test/`, 9 files)

| File | Goes to |
|---|---|
| `GramaLifeTest` | gone (grama grass). |
| `MoundShapeTest` | gone (the hill's shape). |
| `DoorRuleTest`, `SiteFidelityTest` | gone (the door, the forager spots). |
| `HarvesterModelFilesTest`, `WorkerAnimationsTest`, `HarvesterBodyPlansTest` | gone (the harvester's model, clips and body plan). The savanna has the same checks for its own models (`ModelFilesTest`, `TermiteAnimationsTest`, `BodyPlansTest`). |
| `HarvesterProfileTest` | gone, but for two shared rules **moved** into the savanna's `SpeciesProfileTest` if it lacks them: "foragers go out when their profile says" and "nurses are counted from the headcount". |
| `HarvesterRulesTest` | the shared rules (a worker nurses, then builds, then forages; hungry, then slow, then starved before old age) **moved** into `SpeciesProfileTest` on the termite file; the queen's burst for rich bread is gone. |

## (b) What the harvester leaves in the framework

Found by asking, for every socket, profile field and class: does the savanna mod, or the framework for a savanna
species, still use it once `desert\` is gone? **Delete** means step 5 takes it out; each delete is proved by the build
and the test categories, and what really happened is written at the end of this file.

### Sockets

| Socket | Who used it | Mark |
|---|---|---|
| `ColonyHooks.TRAILS` | the trunk trails | delete |
| `ColonyHooks.DOOR_RULE` | the door | delete |
| `ColonyHooks.NUMBERS_SEARCH` | the forager spots (numbers searching alone) | delete |
| `ColonyHooks.LONE_SEARCH`, with `SearchPlan` and `GroundPicker` | the forager spots (a real lone search) | delete, with the lone-search half of `ScoutGoal` |
| `ColonyHooks.CALLED_OUT` | the recruits | delete |
| `ColonyHooks.MILL` | the bakery | delete |
| `ColonyHooks.NURSE_MAKES` | the bakery (bread milled while away) | delete |
| `ColonyHooks.LIST_DAY` | the patrol, the door, the night soil | delete |
| `ColonyHooks.REACH`, `NURSE_FOOD`, `AWAY_BUILDER`, `DUG_SOIL`, `LIST_STORE`, `LIST_SEARCH` | the harvester and the savanna | keep (their comments lose the harvester) |
| `ColonyNews.FOOD_FOUND`, `INTO_NUMBERS`, `OUT_OF_NUMBERS`, `PIECE_TAKEN`, `PIECE_EATEN`, `NUMBERS_TRIP`, `NUMBERS_FORAGED`, `FORAGER_NUMBERS`, `SET_OUT`, `SEARCH_STARTED`, `SEARCH_OVER`, `MEMBER_ROUND` | heard only by harvester parts | delete (news nobody hears) |
| `ColonyNews.MEMBER_DIED` | heard only by harvester parts | delete unless a shared part hears it (checked in step 5; the danger memory has its own way in) |
| `ColonyQuestions.NOT_CLEARED`, `MORE_FOOD_WANTED`, `KEEPS_TRAILS`, `MUST_WAIT`, `PASSES_BY`, `ON_TRAIL` | answered only by harvester parts | delete |
| every other socket | the savanna | keep |

### Species profile fields (`SpeciesProfile`)

| Field | Mark |
|---|---|
| `food.mill_ticks`, `food.bread_stock` (`makesBread`) | delete |
| `food.day_clock` (the day forager's round, the midday rest by heat) and `body_language.hot` | delete: termites and Matabele go by `forage_at` and the raid hours |
| `food.search` with `recruit` (searching alone, site fidelity, recruiting) | delete, after checking the two places the Matabele scout code names it |
| `worker.rest_near_entrance` | delete (the Matabele use `rest_in_rooms`) |
| `nest.founding_chamber`, `founding.site_search_radius`, `founding.max_unevenness` | delete |
| `nest.level_unlocks` | delete if the levels go (below) |
| `queen.rich_meal`, `burst_eggs`, `burst_interval_ticks` (the burst of eggs for rich food) | delete if no savanna queen can start a burst (termite queens lay by their size, `laying`); checked in step 5, because the termite file fills these fields today |
| `colony.lost.patrollers` | delete |
| `colony.danger.keep_away`, `colony.neighbours.turn_degrees` (foragers kept off danger; foraging turned away from a neighbour) | delete if only the harvester's foraging read them; checked in step 5 |
| `nest.plug_block` | keep (the termites plug with mound soil) |
| `nest.room_sizes`, `nest.max_levels` | see "the levels" below |
| everything else | keep (the termite or Matabele file fills it, or the shared code runs on its default) |

### Classes, tags and defaults

| What | Mark |
|---|---|
| `colony/NestSite` (the queen's level-site scoring) | delete |
| The harvester's built-in numbers and names in the framework (`SpeciesProfiles.signUpBuiltIn`, the film camera's `worker_ant` short name, comments saying "the harvester's ...") | delete or reword |
| `AwayReport` lines `BREAD_MADE`, `HILL`, `PATROLS`, `PATROLLERS_LOST`, `NO_FORAGING_DAYS` | delete |
| Methods only harvester code or harvester tests called (`Neighbours.turnsFrom`, `Nest.nightSoilCells`, `FoodStore.findSeedPile`, `FieldLife.sendSearching`, `QueenCare.isOnSecondBatch`, test setters such as `Colony.setHeatForTests`, ...) | delete, found again after step 4 by a script that lists every framework name nothing uses |
| Item tags `colonyframework:seeds`, `rich_seeds`, and the desert's `nurse_food`, `rich_nurse_food`, block tag `old_plugs` | delete those no savanna file or code reads (checked in step 5) |
| `block/SeedPileBlock`, `block/PlugBlock` | keep: the termites' comb heap and plug are built on them. `SeedPileBlock` keeps its name (renaming a block class buys nothing). |
| `GatherSeedGoal`, `StoreSeedGoal` | keep, renamed `GatherFoodGoal` and `StoreFoodGoal` (they carry the termites' grass and wood) |

### The levels (my pick: try the cut, keep if it will not come out cleanly)

The shared nest plan can stack up to four levels joined by a spiral stair (seed store, nursery, queen, refuse chamber),
fit fewer into thin soil, and open them as the colony grows. The termites' nest is its one-level case and the Matabele
dig nothing, so after the harvester **nothing in the game digs a second level**. Step 5 tries to cut the plan down to
the one level (the stair, the unlocking, the thin-soil fitting, the seed store, nursery and refuse chamber as levels).
If that cannot be done without changing how the termite nest is planned or dug (rule 2), the levels stay, the report
says so, and the question goes to the code review (job 3) by name. The legs tests' walk-through nest is then built by
the test itself.

## (c) Tools and films that take the harvester worker

**Guard films** (`tools/film/guard/`):

| Scene | What happens |
|---|---|
| `a_walk` (three speeds), `b_turn`, `d_ledge`, `e_ceiling` | filmed again with a Matabele minor (no Matabele or termite scene guards these) |
| `c_climb` | filmed again with a Matabele minor: `k_climb_minor`, `l_climb_major` and `m_climb_king` are all termite bodies, so no Matabele climb is guarded yet |
| `n_down_turn` | filmed again with a Matabele minor (`o_down_turn_major` is a termite major) |
| `h_crowd` (400 harvester workers) | filmed again with 400 Matabele minors |
| `f`, `g`, `i` to `m`, `o`, `q`, `r`, `s` | no harvester in them: untouched |

The user looks once at the new `run-film/guard_films.mp4` before the new pictures become the approved ones.

**Other film scenes:** `tools/film/climb.film`, `poses.film`, `work.film` name the harvester: switched to a Matabele
minor, or dropped where the scene filmed something only the harvester did (decided scene by scene in step 3).

**Tools** (Neon's lane; a file with uncommitted changes in `C:\Ant Mod` is not edited, the line goes on
`docs/HANDOFF.md` instead; today that is `tools/body_plan.py`):

| Tool | What it takes instead |
|---|---|
| `anim_lab.py`, `wall_check.py`, `ant_pose_viewer.py`, `ant_motion.py`, `groom_motion.py` | default insect `matabele_minor`; harvester-only clip makers in them go |
| `ant_models.py` (the harvester worker's parts), `grama_art.py`, `grama_viewer.template.html`, `young_textures.py` (if only the harvester's young) | go |
| `matabele_anims.py`, `matabele_models.py`, `matabele_climb.py`, `matabele_viewer.template.html` | they only mention the worker ant as where a part or clip was copied from: comments reworded, nothing else |
| `mod_paths.py`, `run_game_tests.sh`, `film.sh` | the desert mod's lines go (step 4) |
| `model_viewer.py`, `tree_viewer.template.html`, `nest_sites_viewer.template.html`, `perf_scenes.py`, `bbmodel_import.py` | the harvester's entries go; the nest sites viewer goes whole (it showed the queen's site scoring) |
| `body_plan.py` | **not edited now** (Neon has uncommitted changes in it): a line on the board asks for its two `worker_ant` mentions to go |
| `references/` pages built for the harvester (`grama_viewer.html`, `nest_sites_viewer.html`, `ant_pose_viewer.html`, `references/ants`) | go with their makers, or are rebuilt for the Matabele |
