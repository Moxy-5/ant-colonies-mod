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
