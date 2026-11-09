# The test revamp (plan, 2026-11-09; `docs/plans/test-health.md` step 4)

The user's ask (2026-11-09): run the test revamp overnight in linked sessions, fully automated. Part 1 (this branch,
`claude/test-revamp`, from `claude/empty-raid-189-001096`) builds R0 to R3; part 2 builds R4 to R6 on top of it.
**Test work only: nothing changes in-game.** Game code may only get test-only seams that change no behaviour.

## Why (from `test-health.md`)

A whole colony is chaotic, so tests that check exact numbers, find "their" colony by looking in the arena's box, or
build the same scene by hand in a dozen slightly different ways break when an unrelated feature lands, and a failure
in a run of 200 tests says nothing about whether it is real until someone reruns it alone.

## Survey of today's tests (2026-11-09, branch `claude/empty-raid-189-001096`)

### Count per category (game tests, `@GameTest` methods; the replays are made one per saved report)

| category | tests | classes |
|---|---|---|
| legs | 25 | TermiteClimbing 5, TermiteTunnel 3, Matabele 2, Navigation 15 (harvester) |
| antnest | 24 | NestTests (harvester) |
| termitefood | 25 | TermiteFood 9, TermiteForaging 4, TermiteNewHole 8, Runway 1, BarePatch 1, Termitomyces 2 |
| colony | 25 | Termite 4, JobsByAge 1, TermitePopulation 2, TermiteNeighbour 1, MatabeleColony 1, MatabeleNest 2, MatabeleFood 2, MatabeleNewLand 3, Idle 2, HarvesterPopulation 4, HarvesterTrail 2, HarvesterNeighbour 1 |
| soldiers | 19 | TermiteSoldier 3, TermiteAlarm 5, MatabeleFight 6, TermiteRaidDefence 4, MatabeleDefence 1 |
| trees | 18 | Tree 4, TreeGrowth 14 |
| away | 17 (+ founders made by `AwayKit`) | TermiteAway 7, MatabeleAway 2, HarvesterAway 8 |
| mound | 15 | TermiteMound 7, Cathedral 2, Repair 1, TermiteNewLand 5 |
| raids | 14 | MatabeleRaid 14 |
| replays | 14 | one per folder in `savanna/.../data/antcolonies/replays/` |
| hills | 6 | TermiteHill 4, HarvesterHill 2 |
| worldgen | 6 | SavannaWorld 5, SavannaLoading 1 |
| body | 4 | TermiteBody 2, HarvesterBody 2 |
| stress | 1 | only when named |

About 212 in a full run (the logs say "of 213" with the stress test). Quick tests with no Minecraft
(`./gradlew test`): about 170, mostly route planning, shapes, galleries and body plans; almost none for the colony's
own rules (jobs, feeding, foraging, hunger).

### Time

The logs do not say how long each test takes, only when each **batch** starts. The newest full-run logs per server
(`run-test-shards/<n>/logs/`, 2026-11-07, from several runs) give batch times:

- the long ones are the **soak replays** (`replay_soak4_termite_in_cast_going_home` 20 min, which is its time limit,
  `replay_soak4_raider_going_home` 13 min, the others 2 to 3.5 min) and the **harvester seed test** (`seeds_alone`,
  9.3 min) and the user's half-built mound (`usermound`, 6.1 min);
- each server's `defaultBatch` (26 to 27 short tests side by side) takes 4.7 to 10.7 min;
- most batches of their own take under a minute.

So a full run is held up by its two or three longest tests, not by the many short ones. R1 makes each test log its
own time (`[test-time]` lines) and the run script lists the slowest, so part 2 and later surveys have exact numbers.

### Tests that check exact numbers from a living colony

Counted roughly (`assertTrue(... == n ...)`, so it includes fair exact checks like "2 tunnels planned"):
HarvesterAway 28, NestTests 37, TreeGrowth 20, TermiteAway 17, MatabeleRaid 15, HarvesterPopulation 9, HarvesterTrail
9, MatabeleFight 9, MatabeleFood 8, TermiteFood 7, TermiteForaging 7, TermiteTests 6, about 40 more spread thin. The
known breakers so far: the job counts, the seed count, the larva and body counts (`docs/BUGS.md` 179, 184), the
neighbours' meetings (68). R4 (part 2) goes through them.

### Tests that find their colony by the arena box

- `TestScenes.colony` / `colonies` (framework): the colony of a species whose middle lies in the arena's box, with a
  living queen. Used about 170 times through four wrappers: `TermiteMoundTests.colony` (101 calls, also fences the
  food map and the alarm to the arena), `NestTests.colony`, `HarvesterPopulationTests.antColony`, `TermiteTests`,
  `HarvesterHillTests`, `MatabeleNewLandTests.matabeleNear`.
- About 70 places count insects with `getEntitiesOfClass(..., helper.getBounds() ...)`: NestTests 30, TermiteFood 5,
  TermiteSoldier 5, TermiteAlarm 4, TermiteNewHole 4, the rest 1 to 3. Some are about blocks or dropped items (fine),
  some count a colony's own insects by box (the leak of `docs/BUGS.md` 32: a forager out past the edge is missed).
- Good already: the away tests find their colony by the queen they spawned (`c.getQueenId().equals(queen)`), 18
  times, each written out by hand.

### Scenes built by hand

- **A termite colony with workers**: deep ground, a queen at the middle, after 5 ticks a row of workers, after 10 the
  colony looked up; about 30 tests, each with its own worker count and spots. 25 of them then dig the nest at once
  with `TermiteSoldierTests.digNestAtOnce`.
- **A Matabele colony in a dead mound**: flat ground 45 wide, `NewLandMounds.put(... pickFor("dead", 30, 5L))`,
  `MatabeleCommands.makeColony(... NEST + 6 ..., n)`, then its raids part and prey pile; 12 times in
  MatabeleRaidTests, 6 more in the colony, food, nest, defence and away tests.
- **A termite feeding spot**: `feedingUnderALog` (a log with two mud casts over a gallery, termites in it), private
  to MatabeleRaidTests, used 13 times; the fight tests build their own spots.
- **A harvester nest already dug**: `HarvesterPopulationTests.startAntColony` (queen, 10 ticks, nest dug, brain on);
  NestTests and the neighbour test dig the nest their own way (`NestTests.digNestAtOnce`, `TestScenes.digNestAtOnce`).
- **A living termite mound at stage N** is made only by new land (`NewLandMounds.pickFor("full"|"half"|"young")`,
  stages 30, 16, 6).

### Open flakes (old, not to be blamed on the revamp; `docs/BUGS.md`)

22 (middle hole after a chicken alarm), 49 (full-store seed test), 62 (fallen tree up a bowl), 68 (harvester
neighbours), 75 (minors at the holes), 77 (three Matabele fight tests), 80 (away termite tuft), 136 (fallen log on a
step), 151 (half-built stage 14), 153 and 164 (mound-ringing scout), 158 (raid column crash beside the nest), 161
(raider by fallen branches, replay), 163 (termite eating test timeout), 166 (raider stuck on a tall dead mound), 171
(whole-chain march check), 178 and 182 (low-mound ribbon checks), 179 and 184 (harvester larvae), 180 (harvester rich
patch), 187 (ribbon side by side), 192 (ridge-and-pond scent line), 197 (trail point still broken).

## The steps

### Part 1 (this branch)

**R0. This plan.**

**R1. Rerun failures alone, automatically.** At the end of `tools/run_game_tests.sh`, every failed test is run again
on its own, 3 times (one rerun on 3 servers at once, `ANTCOLONIES_EVERY_SHARD`), and the summary says **real**
(failed alone at least once) or **flake** (passed alone 3 of 3) next to each failure. Exact names: a word starting
with `=` in `ANTCOLONIES_TESTS` picks only the test of exactly that name (a plain word picks every test whose name
holds it). `ANTCOLONIES_RERUN=0` skips the reruns. Also each test logs its own time (`[test-time]`), and the summary
lists the 10 slowest. Same commands and defaults (6 servers).

**R2. Find a test's colonies by their owner, not by the arena box.** `TestIsolation` already knows which test owns
each colony (the test in whose arena it was founded, fixed at founding). New helpers: the colonies a test made, its
colony of a species, the colony of a queen the test spawned, a colony's queen, its insects. `TestScenes.colony` and
`colonies` and the species wrappers move onto them, and the insect counts that count a colony's own insects by box
move to `insectsOf`.

**R3. The scene kit.** Ready-made scenes a test asks for in one line; the framework part never names a species.
- framework (`TestScenes`): a queen and a row of workers, the colony looked up after a set time (species types handed in).
- savanna (`TermiteScenes`): a termite colony with N workers (and the king), its nest dug at once; a living mound at
  stage N with its garden and galleries (new land's own maker, any stage).
- savanna (`MatabeleScenes`): a Matabele colony of N in a dead mound with its prey pile; a termite feeding spot
  under a log with termites at it.
- desert (`HarvesterScenes`): a harvester nest already dug with N workers.
Tests that build these scenes by hand move onto the kit, every check kept. Then a full run.

### Part 2 (the next session, on top of this branch)

**R4. Exact numbers into the rules they mean** (the 4-box rule's box 3): start with the known breakers (jobs, seeds,
larvae and bodies, neighbours' meetings), then the biggest counts above; a range or a rule ("no seed vanishes", "more
food, more foragers") in place of a number that a living colony only happens to hit.

**R5. Quick rule tests with no Minecraft for the colony's rules** (seconds, `./gradlew test`): jobs by age, feeding
the queen, the seed count, the numbers foraging, hunger, the raid day's count; each rule's decision pulled into a
plain method a quick test can call (test-only seams, no behaviour change).

**R6. Tests on new land**: mounds and trees made by world-gen (the real savanna land, not a flat arena), checking the
things the user's worlds showed: dead mounds holding Matabele, the dead tree by each mound, feeding spots in a raid's
reach, no colony in water.

## Later (not in this revamp)

- **Handing the clock and weather in instead of the "ForTests" switches** (about 99 in the game code). It touches game
  code everywhere, so it waits for its own plan with the user.
- Fixed dice for the insects (each insect still rolls Minecraft's own dice, so a test is not exactly the same each
  run).

## My picks (code choices, one line each)

- R1 reruns with `ANTCOLONIES_EVERY_SHARD=1` on 3 servers: one server start per failure gives the 3 alone runs at once.
- R1 runs the reruns one failure after another (Gradle cannot run two builds of one folder at once).
- R1: a shard that crashed or was stopped by the watchdog has tests that never finished; they are listed, not rerun
  (the log does not say which ones were left).
- R1: exact test names by a leading `=` in `ANTCOLONIES_TESTS`, so a failing test whose name is part of another's
  never drags the other along.
- R1: `[test-time]` lines from `TestIsolation`'s tick (it already sees every running test), in ticks and seconds.
- R2: a colony's owner stays the test whose arena held its middle at founding (as `TestIsolation` already decides for
  removing it); a colony founded this very tick is given its owner on the spot when asked.
- R2: `TestScenes.colony` keeps its meaning (a living queen and the species) so its 170 callers need no change.
- R3: the kit keeps each moved test's own tick timings (spawn at 0, workers at 5, colony at 10), so the colonies'
  lives do not shift.
- R3: savanna scenes in `TermiteScenes` and `MatabeleScenes`, desert in `HarvesterScenes`, each in its mod's gametest
  folder; shared bits in the framework's `TestScenes`.
- R3: a living mound at any stage from a `TermiteMoundStructure.Pick` built in the test (the record is public), no
  game code touched.

## Your calls

None for part 1: nothing changes in-game, no test is dropped, and runs take about the same time (the reruns add a
few minutes only when something fails).
