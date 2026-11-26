# Full code review, round 3: the insect body (reading round, 2026-11-25)

Read on branch `claude/review-round-3-a86aa8` at commit `d67f55ae` (`claude/review-fix-1-calls`: round 1's fixes and
the user's two calls). Rule book: `docs/plans/code-review.md`. No code was changed, no test was run and nothing was
measured in the game, so no lag finding here has a number yet.

**What was read:** the framework's `entity/` (without `entity/body/`, which is round 4), `entity/goal/` and `fight/`,
50 files, about 11,500 lines. The walking half of `AbstractAntEntity` (about lines 2242 to 2835) was left to round 2,
which was being read at the same time.

**Who read it (the user's setup, 2026-11-25):** four Opus readers, every line, one slice each with its own files (the
insect itself; fighting; the goals of fighting, care and idle life; the goals of work), and two Fable readers
following a thread across the files (a goal's life from picked to dropped; a fight from first bite to death, near
and as numbers). The main session then checked each grade A finding against the lines it names; those say "checked
again". The others carry their reader's proof only.

**How to read a finding.** Rating: **A** the player can meet it, **B** it will cost us when building on this,
**C** a tidy-up. Proof: **proven** (the path was walked through the code, lines named), **likely** (one link, named in
the finding, was not checked), **to measure** (a lag finding with no number yet), **a reading** (arithmetic from the
species files, not a measurement). A finding rated A gets its `docs/BUGS.md` number when a fix session takes it up.
Findings keep their numbers for good (numbered in the order the slices came in, not by grade); a fix session adds a
status line under a finding and never rewrites it.

**Count:** 45 findings: 6 A, 17 B, 22 C. All six readers are in; the round is read. Where two or more readers met
the same fault it is written once, by the reader who walked it furthest (the list is at the end).

## The short of it

The body is in better shape than the brain was. Save and load of an insect, its loads, the carried nestmate, bodies,
eggs, larvae and lost legs (none can pile up), the claims of every work goal (all timed, all given back in `stop`),
`Errand` used by every work goal, the clamp when its other side dies or unloads, nothing steering with Minecraft's
own walking: all found sound. What is wrong sits in four places:

1. **How Minecraft runs goals, which the goals were not written for.** Minecraft asks "may I start", then stops the
   goal being pushed out, then starts the new one; and it ticks most goals only every other tick. So a walk planned
   in "may I start" is wiped before the goal begins (R3-30: fleeing termites), every goal's own clock runs at half
   speed (R3-33, R3-20: BUGS 35 and 357 fixed two cases of a whole family), and the one-second "I cannot get nearer"
   clock never runs (R3-32).
2. **The change between bodies and numbers, once more (round 1's second theme).** Going into the numbers heals
   everything: lost legs, wounds, venom (R3-38). A stung termite eaten by its nestmates is never taken off the
   count (R3-18). The same raid costs different things watched and unwatched (R3-42).
3. **A goal that cannot end.** A nurse holding the queen's food with no queen starts and stops for ever (R3-31).
4. **Searching again and again.** A fighter with no foe (R3-10), a carer with no way (R3-11), an idle nurse (R3-23,
   R3-34): searches with no "nothing there, wait a while".

## The ones that matter most

1. **R3-30:** termite workers do not really flee: the first try always fails, and a worker carrying food never
   runs. It makes raids deadlier for the termites than the numbers mean.
2. **R3-38:** a hurt ant that turns into a number comes back whole, so walking 64 blocks away undoes wounds, lost
   legs and stings (and M8's wound care with them).
3. **R3-18:** every paralysed termite eaten by its nestmates leaves a ghost in the headcount; ghosts stop the
   colony raising workers early and keep a dead colony alive.
4. **R3-37:** a player who keeps walking is almost never stung by the Matabele defending their nest.
5. **R3-10:** raiders searching a closed feeding spot run costly searches ten times a second each (to measure).
6. **R3-31:** kill a queen, and nurses freeze with food in the mouth until they starve.
7. **R3-33 with R3-20:** almost every time written in a goal lasts twice as long in the game, while the same work
   done as numbers runs at the written speed: watched colonies work at half the pace of unwatched ones.

## For the user to decide

- **R3-33 and R3-20 (half-speed goals):** this one changes how the game plays. Recommended: make the goals count real
  game time (one rule in one place), then look at each number once: the ones the user has watched and liked in-game
  keep the pace seen (their numbers are doubled in the file), the rest take the written, real pace. Until then the
  watched colony is the slow one.
- **R3-13 (termite workers hanging from ceilings):** built and shown once, lost since M8 step 3Q with no note.
  Recommended: bring it back (one rule moved into the resting stroll).
- **R3-21 (the mob griefing game rule):** recommended: colonies dig and seal their own nest whatever the rule says
  (the nest is the colony's body), written into `docs/DESIGN.md`; only eating a player's hay bale asks the rule.
- **R3-41 (the leg under a living clamp):** a reading of the numbers: the leg is kept 12 to 38 times in 100 on the
  walk home, against the 9 in 10 the species file aims at. Recommended: the squeeze only hurts, and nest care is the
  one place that decides the leg, as in the papers. A real-biology number, so the user's call.
- **R3-9 (stings never wear off in a termite soldier):** unknown biology. Recommended: leave as it is until a source
  says otherwise.
- **R3-45 (Peaceful mode):** the ants still pour out and chase but cannot sting. Recommended: leave it (bees do the
  same) and write it down.
- **R3-37 (stinging a walking player):** the fix is Claude's, but the feel is the user's: recommended: a sting on a
  player lands if the player was in reach when the jaws closed, so walking past a guarded nest hurts and running
  away still works.

## What this round did not do

Nothing was measured in the game: every lag finding is "what runs, how often, over how many things", and R3-10's
"about a million cell checks a second" is its reader's arithmetic. No test was run and no finding was proven with a
failing test. R3-30 and R3-37 were walked through the code but not watched in the game. R3-41 and R3-42 are
arithmetic from the species files. Minecraft's own goal code was read from the decompiled game by two readers and
from memory by one. The walking half of `AbstractAntEntity` and `entity/body/` were not reviewed (rounds 2 and 4).
The species goals and the raid code were read only where they call in. `docs/BUGS.md` was searched by class name,
not read whole. The main session checked the six grade A findings itself; the B and C findings were not checked a
second time. The joining of doubled findings was done by their titles and the readers' summaries: a detail a dropped
copy held and its kept copy does not may have been lost (the readers' full reports are kept outside the repository,
in this session's notes folder).

---



## Slice: the insect itself: loads, colony ties, young, corpses, save

`AbstractAntEntity.java` 1 to 1300 and 2836 to 3420, `AntCorpseEntity`, `AntEggEntity`, `AntLarvaEntity`, `LostLegEntity`,
`ColonyBorn`, `FedByNurses`, `SwellingQueen`, `WorkerHunger`, `load/CarriedLoad`, `load/FrameworkLoads`, `load/LoadKind`,
`WoundOddsTest`. Every line read. To walk paths to their end I also read `Population.java` 383 to 577, `AwayLife.java`
240 to 494, `Refuse.java` (all), `AbstractAntEntity.java` 1370 to 1504 and 2310 to 2434 (other readers' lines, only to
follow a path), and the two species' `die` methods.

### R3-1 (C, proven): a stung insect saved while staggering walks at full speed after the reload

- **Where:** `AbstractAntEntity.java:1438-1441` (`takeSting` adds the `STAGGER` slowing as a transient modifier, which
  Minecraft does not save); `2886-2888` and `2924` (`venomAt` is saved and loaded); nothing puts the slowing back.
- **What is wrong:** after a reload `venomAt` says "staggering", but the speed modifier is gone. The lost-legs slowing
  does not have this problem (`updateLimp` re-adds it once a second, 880 to 882).
- **What the player sees:** for at most the venom time (2.5 to 8 seconds, `venom_ticks` 50 to 160) a stung termite
  walks normally after a world load, then falls. Small.
- **Fix proposed:** in `tickFight`, while `isEnvenomed()` and the modifier is missing, add it (the same shape as
  `updateLimp`), or add it in `readAdditionalSaveData`.

### R3-2 (C, proven): the camera's "film:" name hook is reachable in play

- **Where:** `AbstractAntEntity.java:2995-3003` (`filmClip`), `3031-3035` (`wholeBodyClip`), static `FILM_CLIPS` at 2989.
- **What is wrong:** any insect whose custom name starts with `film:` plays only the named clip, for good. A player
  with a name tag can do this (a name tag works on any mob), and each new name adds an entry to a static map that is
  never cleared. Harmless in practice (the map holds one small object per name typed), but it is a test hook in play.
- **Fix proposed:** only when the film director is running (a client flag it sets), or leave it and note it as known.

### R3-3 (C, proven): `WoundOddsTest` is a weak copy of a savanna test

- **Where:** `framework/src/test/java/com/antcolonies/entity/WoundOddsTest.java` (all);
  `savanna/src/test/java/com/antcolonies/savanna/colony/RaidRulesTest.java:134-139` checks the same two helpers.
- **What is wrong:** `Wounds.infectChance` and `deathChance` are each one ternary (`Wounds.java:147-154`). The test
  checks those ternaries with numbers it makes up; `careNeverMakesAWoundWorse` is true by the numbers it picks, not by
  any rule in the code. It would still pass if the real roll (`Wounds.java:133-136`) counted licks wrong or never
  rolled. It is a real check of the two helpers, nothing more.
- **Fix proposed:** keep one of the two copies. If a wound rule test is wanted, test the roll itself with a seeded dice.

### R3-4 (C): comments that still describe the harvester

- **Where:** `AbstractAntEntity.java:234-238` (a seed, bread), `405-430` (ant bread, "on its way to the food store or
  the mill"), `722-724` ("real harvester ant foragers"); `FrameworkLoads.java:11-16`, `26-28`.
- **Fix proposed:** reword when these files are next touched (the methods themselves are still used by the termites'
  nodules and forage).

### Solid in this slice (do not "fix")

- **Save and load of the insect:** every saved field is loaded back (colony, age, load, stings, paralysis, venom, lost
  legs and when, clamp, wounds, clamping, personality, hold). Not saved on purpose and harmless: care claims (40 ticks),
  tending (20 to 40 ticks), help calls, `hideUntil`, the fight order, the next player sting, the blow under way. The
  youth shade is worked out again once a second; the limp is put back once a second; the paralysed flag is sent every
  tick from the saved time.
- **The carried hurt nestmate:** Minecraft saves a rider inside its carrier and seats it again on load; the `HURT` load
  is saved (`CarryingHurt`), so the pair comes back as it was. If the rider is gone the carrier lets go
  (`tickRescue`, 1278 to 1280); if the carrier's load is gone the rider gets off (1281 to 1283). A dying carrier throws
  its rider off (`FrameworkLoads.HURT`'s drop); removal of either one ends the riding (Minecraft's own `setRemoved`).
  Neither can be folded into the numbers while carrying or carried (`hasFullMandibles`, `isPassenger`).
- **Loads:** one synced slot, sent only when it changes; each row's own save words; a dying worker's load is dropped
  by its row (both species' `die` call `dropLoadOnDeath`). `LoadKind`'s list is filled at mod load only, so nothing is
  kept wrongly between worlds.
- **Bodies on death:** a body is left only on `KILLED` (also `/kill`, old age, starving, the paralysis running out),
  never on fold-in, carrying off or unloading. Bodies cannot pile up: an unplaced body crumbles after the species'
  time, one left by a hole fades, a loner's after 6000 ticks, one whose colony is gone at once; placed bodies are one
  to a refuse cell.
- **Eggs and larvae:** gone when their colony is gone; larvae starve on a clock that runs only while they still need
  food; real young are capped (`max_young` 10 in both species). Lost legs go after 6000 ticks, age saved.
- **Synced data:** everything set each tick (surface, heading, pose flags) is only sent when it changes; the larva's
  meal is set once a second and only sent when it changes.
- **Entity searches here** (`lookForHostiles`, `lookForNeighbours`, `helpWithin`) run once a second or once every 5
  seconds per insect, over 3 to 6 blocks: small.
- **`removeWhenFarAway` false** is right: colony insects leave the world only through the colony's own folding.
- **Not proven, so not a finding:** an egg or larva with no colony (only from `/summon`) never hatches, starves or goes
  away; an egg dropped outside the colony's nest area is never counted or fetched. I found no way in play to make
  either.

---



## Slice: fighting: blows, clamps, wounds, stings, orders, and the nest's defence

`AbstractAntEntity.java` 1300 to 2241 and 2283 to 2470 (background 680 to 745, 800 to 1110, and the save lines 2880 to
2934 for the fight fields), `Clamp`, `Wounds`, `fight/DefenceNumbers`, `FightDiary`, `FightNumbers`, `Fights`,
`HeldCells`, `NestDefence`, `Pain`; the `fight` and `defence` sections of `macrotermes.json` and `megaponera_analis.json`.
Every line read. Callers walked: `Population.goingIn`/`direct`/`realMembers`, `AwayLife.frozenOf`/`foldLoaded`,
`FightGoal.worthChasing`, `DefendGoal` (the one `HeldCells.hold`), `LevelCellMap`, `GroundTop.y`, `debug/Perf`.

No grade A finding: the fight code is careful about the other side dying, unloading or being folded in.

### R3-5 (C, proven): a felled insect's load is deleted, while the comment says it drops it

- **Where:** `AbstractAntEntity.java:1424-1425` (comment: "it drops what it holds"), `1456` (`setLoad(CarriedLoad.NONE)`),
  `361-368` (`setLoad` only overwrites).
- **What is wrong:** a termite worker stung while carrying grass or soil loses it into nothing; nothing is dropped.
- **Fix proposed:** either drop it the way a dying insect drops its load, or fix the comment.

### R3-6 (C, proven): the lookers' small reach at the entrance lasts one second

- **Where:** `NestDefence.java:350-354` (a looker's order: reach `look.radius + 2`, until `now + look.lasts`) and
  `259-266` (next second every defender, lookers too, gets `numbers.reach()`, 16, and `keep_going`).
- **What is wrong:** two places set the same order; the second always wins, so the look's own reach and time are dead
  numbers. The lookers cannot sting until `stinging` (`provokedBy`, 441-443), so the harm is small: while looking they
  may wander the 16-block fight reach instead of staying on the entrance.
- **Fix proposed:** in the renewal, keep a light provocation's lookers on the look's reach (or renew only full ones).

### R3-7 (C, proven): a felled striker still finishes its grip

- **Where:** `AbstractAntEntity.java:1658-1659` (`holds` in `tickAttack`: no `isDowned`/`fightsNoMore`), `1671-1675`.
- **What is wrong:** a termite soldier stung mid-blow (staggering) can still close its jaws at `gripAt` and hold its foe
  still to the clip's end. `Fights.strike` (118) refuses the landing itself, so no damage is done; only a short hold.
- **Fix proposed:** add `!isDowned()` to `holds` (or end the blow in `fallParalysed`/`takeSting`).

### R3-8 (C, to measure): a new name string for every insect every tick, for the stopwatch

- **Where:** `AbstractAntEntity.java:2392-2402` (`tick`): `Perf.begin` is true on every server tick (`Perf.java:64-76`, it
  is never switched off), so each insect each tick builds a key string (`getPath() + ...`, one more `getColony()` look-up)
  and a `long[]`; `customServerAiStep` (2313) adds a second `long[]`.
- **What is wrong:** small throwaway objects in the busiest loop (kind 1j). About 60 insects x 20 ticks: 1,200 strings a
  second, plus the same again for the arrays. Probably well under 0.01 ms a tick; not measured.
- **Fix proposed:** keep the three possible key strings per insect type (or an enum) instead of building one each tick.

### R3-9 (C): stings add up for life

- **Where:** `AbstractAntEntity.java:1431-1432` (`stingsTaken`, saved at 2880-2881, never lowered).
- **What is wrong:** a major soldier (`stings_to_fall` 2) stung once and got away falls to the next sting days later.
  Whether real venom doses wear off in a termite is not known to me; flag for the biology check, not a bug for sure.
- **Fix proposed (if the biology agrees):** forget stings after a while (a time with the count).

### Balance (the species numbers against each other)

- Fits: termite soldier bites 1 and 1.5 against a Matabele major's 10 and minor's 5 health: 4 to 10 bites, and the ant
  leaves at 40 percent (the note in `macrotermes.json`), so few raiders die in the fight, as in the papers. A sting
  on prey pricks 0.5 and never the last point (`Fights.java:129`), so a termite worker's 3 health never matters.
- To measure: a living clamp squeezes `0.33 x 1.5 = 0.5` every 20 s (`squeeze_ticks` 400), the ant slowed by 75 percent.
  A minor that left the fight at 2 health dies after about four squeezes, 80 s. Whether a clamped minor reaches home
  in 80 s at a quarter of its walk depends on how far raids go; some dying on the way is real (Frank 2017), all dying
  would not be.
- `paralysed_ticks` 24,000: a stung termite no raider takes lies a whole game day as a real mob. Raids take most of
  them; fine unless many raids leave many behind (they also take real-mob room of the termite colony meanwhile).

### Solid in this slice (do not "fix")

- **Blows and grips:** a held insect is let go the tick its holder dies, unloads, is folded in, changes target or loses
  its grip (`tickFight` 877-879); `startClamping` ends the holder's own blow (1010-1012); `Fights.strike` checks both
  sides again at the moment it lands. One holder per prey (`pinnedBy == null`). Insect bites do not fling (1716-1721).
- **Clamps:** both sides find each other again after a reload by id; a holder missing over 100 ticks is let go on both
  sides; a holder killed (removal `KILLED`) leaves its head, an unloaded one is searched for again (`Clamp.tick` 146-156);
  the dead or removed victim frees its holder (`tickClamping` 1037-1040); no fall or wall damage while dragged; neither
  side folds into the numbers while clamped (`mayGoIntoNest`); the slowing is put back on load (`Clamp.load` 331).
- **Wounds:** game-time course, once a second, saved and loaded whole; no numbers in the file, no wound.
- **Lost legs:** saved with their time; the limp is put back each second (880-882); a lost leg frees its clamp.
- **Fight orders:** every order has an end time (all callers checked: `NestDefence`, `Raid:1815`, the skirmish command,
  tests); only its giver ends it (`endFightOrder(by)`); not saved, so nothing hangs over a reload. `FightDiary` only tells.
- **Nest defence:** no static state; players kept by UUID, so a second player works; creative and spectator players
  never provoke (`fair`); in peaceful mode `Player.hurt` refuses a mob's blow, so no sting and no pain; a player gone,
  out of reach or timed out is dropped each second; jumping on the entrance handled (BUGS 79); runs once a second and
  does its one box search for members only while a player is near or a defence is on; no list is changed while walked.
- **Pain:** its static map is per player, pruned past 64, and copes with time going back in a new world (line 50).
- **Searches round each insect:** `lookForHostiles` (4 blocks for termites) and `lookForNeighbours` (3) run once a
  second, staggered by id, only on the surface outside the nest; `checkHeat` reads only the insect's own cell.
  `Fights.isShutIn` and `stepsToTheOpen` are bounded (40 cells, 2 steps) and never walk into unloaded land.
- **Test setters** (`set...ForTests`, `paralyseForTests`, `Wounds.setForTests`): called only from game tests.
- **Resting far:** only a queen or king (`nestCaste() == null`), never one hurt, falling or in water.
- **Not proven, so not a finding:** a light looker's order and the full one share `defenders`, so a player who steps
  off the entrance just as a full provocation starts might leave lookers on the light rules a second; not walked to
  the end. `FightGoal`'s use of `isSheltered` (a 2-step search per candidate) may be costly if asked every tick; that
  goal is another reader's.

---



## Slice: goals of fighting, care and idle life

`entity/goal/`: `FightGoal`, `FleeGoal`, `RescueGoal`, `ClampCareGoal`, `WoundCareGoal`, `AllogroomGoal`, `GroomGoal`,
`NoticeGoal`, `GroundShakes`, `NapGoal`, `AntWanderGoal`, `GoInsideGoal`, `WaitAtNestGoal`, `ScoutGoal`,
`StayWithQueenGoal`, `StandByQueenGoal`, `QueenToRoomGoal`. Every line read. Also read to walk paths: `Errand` (whole),
the accessors they call in `AbstractAntEntity` (care, rescue, closeIn, strike, shelter, notice, idle, mayBeAt),
`Fights` (mayAttack, isShutIn, stepsToTheOpen), `AntNavigator.moveTo/plan/isDone`, `AntPathFinder.find`, `Nest.isInside`
and `getAllRoomCells`, `QueenCare.isQueenHome`, `ModEvents.onJump/onLand`, `MatabeleEntity` (goals, breakIn,
pickBreach, scentNear, idlePlace), `TermiteWorkerEntity` and the other goal sign-ups, the two species files, and
Minecraft 1.21.1's own `Mob.serverAiStep` and `GoalSelector.tick` (from the NeoForge sources jar).

**How Minecraft asks goals (checked in the 1.21.1 source, it matters for several findings):** `Mob.serverAiStep`
(line 779) uses `tickCount + getId()`: on even values it runs the full goal check (`canUse`, `canContinueToUse`) and
then ticks every running goal; on odd values it ticks only the goals that ask for every tick. So a goal without
`requiresUpdateEveryTick` gets `tick()` once every 2 game ticks. A running goal is only replaced by one of a strictly
lower priority number (`WrappedGoal.canBeReplacedBy`, line 24); two goals of the same number never take over from each
other.

### R3-10 (A, the mechanism proven and checked again; the cost to measure): a fighter with no foe in reach runs costly searches every other tick, for minutes

- **Checked again (the main session):** `FightGoal.java:198` asks `pickFoe` whenever `foe == null`, so on every goal tick while it searches; `pickFoe` (`:349-351`) is one entity search over the fight reach with `worthChasing` on each candidate, and that (`:415-433`) ends in `Fights.isShutIn` (`Fights.java:286-316`, a flood of up to `most` cells with a new map, queue and set each time) for every termite under cover. `MatabeleEntity.pickBreach` (`:710-736`) has no "nothing here" memory. The mechanism holds. The reader's sum (about a million cell checks a second) is arithmetic, not a measurement. The fight thread's reader met the same search from the once-a-second side.
- **Where:** `FightGoal.java:198-210` (`tick`, the re-pick), `337-351` (`pickFoe`), `353-358` (`usable`), `415-433`
  (`worthChasing`), `226` (the major's `breakIn`); `Fights.java:286-316` (`isShutIn`); `MatabeleEntity.java:641-663`
  (`breakIn`), `710-736` (`pickBreach`), `803-830` (`termiteCells`, `scentCells`).
- **What is wrong:** while it searches (no foe), `foe == null` is true on every goal tick, so `pickFoe` runs every
  2 game ticks for the whole search (`give_up_ticks` 2400, and with the half-speed counting below about 4 minutes).
  Each `pickFoe` gathers every living thing in a box 25 blocks wide round the spot. For each termite that passes
  `mayAttack`, `usable` asks `isSheltered` (a nest look and a short walk of cells), and `worthChasing` asks
  `HeldCells.barred`, `isInTheOpen` and, for one under cover, `Fights.isShutIn`: a walk of up to 40 cells with 6
  neighbours each, two block reads and a land check per neighbour, plus new `HashMap`, `ArrayDeque` and `HashSet`
  each time. A termite that really is shut in (the usual case in a closed feeding gallery) always walks the full 40.
  A major also calls `breakIn` every goal tick (line 226). When it has no breach and has opened fewer than
  `OPENS_PER_SPOT`, `pickBreach` runs every time: an entity search 48 blocks wide, a second one for termites (with
  `isSheltered` each), a loop over every gallery cell of every Macrotermes colony in the level (`scentCells`), and an
  `isShutIn` walk for each scent cell within reach. Nothing remembers "nothing to break here" for a while.
- **To measure:** the attack at a termite feeding gallery, the moment most raiders search: N raiders (up to the 50
  to 60 real ones) times about 10 calls a second times T termites under cover in reach times up to 40 cells times
  about 6 neighbours. With 30 searchers and 15 shut-in termites that is roughly 30 x 10 x 15 x 240, about a million
  cell checks a second from `pickFoe` alone, before the majors' `pickBreach`. Measure with the profiler (or `Perf`)
  on a raid scene at a closed gallery: time in `FightGoal.tick`, `pickFoe`, `Fights.isShutIn`, `pickBreach`.
- **What the player sees (if the numbers are as large as the sum says):** the game stutters while a raid attacks a
  feeding spot, worst when the termites are all sealed in and the ants search on.
- **Unchecked link:** the real count of termites under cover within 12 blocks during an attack, and how long majors sit
  with no breach; not measured here.
- **Fix proposed:** one rule, not more numbers: a fighter with no foe re-picks at most once a second, like the
  20-tick re-pick it already has with a foe (line 198); keep each termite's shut-in answer for the tick (or a second)
  so 30 raiders do not walk the same 40 cells 30 times; a major that found no breach waits a second before it looks
  again (as `noWayTo` already does for a breach with no way). Then measure again.

### R3-11 (B, likely; to measure): a walk that found no route is searched again on every goal tick

- **Where:** `RescueGoal.java:127-133` (walk to the caller) and `177-180` (carry home); `ClampCareGoal.java:101-108`;
  `WoundCareGoal.java:99-107`; `AllogroomGoal.java:121-128` (this one asks for every tick, line 93-96);
  `AntWanderGoal.java:132-146` (the settle walk).
- **What is wrong:** each walks with `if (--replan <= 0 || navigator.isDone()) moveTo(...)`. A failed `moveTo` leaves
  no route, so `isDone()` is true (`AntNavigator.java:453-455`, `637-638`) and the next tick searches again. A failed
  search runs the whole budget (6,000 steps, twice when the insect has walking habits, `AntNavigator.java:629-632`).
  So a carer with no way to its patient searches about 10 times a second until `WALK_TICKS` (400 goal ticks, 40 s); a
  rescuer carrying a nestmate to an unreachable room until `CARRY_TICKS` (2400 goal ticks, 4 minutes); an allogroomer 20
  times a second for 10 s, then after a 5 s rest it picks the nearest idle nestmate again, very likely the same one.
  This is BUGS 42's old lesson (the `Errand` leave-alone memory), not used here. The settle walk in `AntWanderGoal`
  tries 6 route searches each time its 1-in-50 roll comes up (about every 5 s per insect) with no memory either: if a
  player cuts the Matabele rooms off, every idle ant outside them does this.
- **What the player sees:** lag while a patient, a nestmate or the rooms are cut off. Not seen yet in a report.
- **Unchecked link:** how often the target really is unreachable in play (a patient on a wall or a mud-crust top,
  BUGS 362; a termite in a sealed gallery section 4 blocks from another; the carry room aimed at the entrance itself
  when no room is dug, `RescueGoal.java:168`, the BUGS 269 trap).
- **Fix proposed:** one rule for all five: a failed walk ends the job (the goal stops and leaves that nestmate alone a
  while, with an `Errand` and `leaveAlone`); the settle walk waits `RETRY` ticks after 6 failures, as `GoInsideGoal`
  does (line 66-67).

### R3-12 (B, proven): termite nurses at the queen never clean themselves or groom anyone

- **Where:** `StandByQueenGoal.java:57-68` (`canContinueToUse`); `TermiteWorkerEntity.java:121-124` (StandByQueen,
  GroomGoal and AllogroomGoal all at priority 5).
- **What is wrong:** `canContinueToUse` stays true as long as the worker is a nurse with empty jaws and the queen is
  home, that is, for good. Goals of the same priority never replace a running one, so a nurse's `GroomGoal` and
  `AllogroomGoal` never start while she waits at the queen. Only jobs at 4 or lower (feeding the queen, eggs, notice)
  interrupt her.
- **What the player sees:** the queen's court stands still, while elsewhere termites clean and groom all the time.
  Real Macrotermes court workers groom and lick the queen constantly; this is where grooming should be busiest.
- **Fix proposed (my pick, a small reorder):** sign `StandByQueenGoal` up at 6 and the wander at 7 for termite
  workers, so a nurse cleans or grooms a nestmate (the queen too, if she counts as resting) and then walks back to her
  place. Check the court test after.

### R3-13 (B, proven): termite workers no longer hang from the ceilings

- **Where:** `AntWanderGoal.java:76-80` (rest first takes over), `102-105` (the ceiling choice, only in the old stroll),
  `123-167` (`rest`, which has none); `TermiteWorkerEntity.java:125` (`hangsFromCeilings(0.25F).restsFirst(1.0F)`);
  `macrotermes.json:255` (`body_language.idle`).
- **What is wrong:** with `restsFirst` and an `idle` section in the species file, `canUse` always goes to `rest()`,
  which never picks a ceiling. The old stroll with the ceiling choice is reached only with a fight order. So since
  M8 step 3Q a termite worker never hangs upside down, though its sign-up still asks for 1 stroll in 4 (the feature in
  `docs/HISTORY.md`, "ant and termite workers, 1 stroll in 4"). No note says it was dropped on purpose.
- **What the player sees:** a behaviour that was built and shown is gone.
- **Fix proposed:** give `rest()`'s stroll the same one-in-four ceiling choice underground (one rule moved, not
  added), or, if the user prefers resting termites on the floor, delete `hangsFromCeilings` and its comment.

### R3-14 (C, proven): the fleeing termite's "startled moment" is only a delay

- **Where:** `FleeGoal.java:70-79` and the class comment `22-23`.
- **What is wrong:** the comment says it "stands startled a moment, antennae up" before it bolts. In the code
  `canUse` just returns false for those 5 to 15 ticks, so the worker goes on with whatever it was doing (walking,
  carrying), with no stop and no pose. And since `canUse` is asked every 10 ticks (every 5, on the even ticks only),
  the delay is 10 or 20 ticks, never the drawn number.
- **Fix proposed:** either make it real (stop the legs and show the notice freeze, `NOTICE_FREEZE`, for the startle,
  which real blind workers do when they feel the alarm) or put the comment right.

### R3-15 (C, proven): five goals turn the body themselves, five copies of one method

- **Where:** `FightGoal.java:457-469`, `RescueGoal.java:198-208`, `ClampCareGoal.java:131-141`,
  `WoundCareGoal.java:135-145`, `AllogroomGoal.java:138-148` (`face`).
- **What is wrong:** each sets `yRot`, `yBodyRot` and `yHeadRot` straight from the mind, past the body's own turning
  (`BodyTurn`, BUGS 36). Not a vanilla navigation or `MoveControl` call, so not against the letter of the rule, but a
  mind steering the body, copied five times.
- **Fix proposed:** one "turn to face" on the body (or the legs), called by all five.

### R3-16 (C, proven): leftovers of the harvester ant

- **Where:** `GoInsideGoal.java:46-47` and `AntWanderGoal.java:86`, `96-98`, `183-191` (`restsNearEntrance`,
  `restsAt`: no species file has `rest_near_entrance` now); `StandByQueenGoal.java:31-37` (the "no size, the
  harvester" radius); `NoticeGoal.java:136-166`, `192-272` (the sight branch: look, come closer, back off; only termite
  workers sign `NoticeGoal` up, `TermiteWorkerEntity.java:120`, and they feel, so no species uses it).
- **Fix proposed:** keep the sight branch for a future seeing species if wanted, but say so in its comment; delete the
  rest-near-entrance code with its profile field when next touched.

### R3-17 (C, proven): small loose ends

- `WoundCareGoal.java:120-123`: with no rescue numbers for the patient it drops it without `releaseCare`; the claim
  then holds 40 ticks. Release it as the other ways out do.
- `AntWanderGoal.java:131-132`: `mayBeAt(here)` (asks `mayGoOut`, the colony's alarm and schedule, and the nest) is
  asked on every check, every 2 ticks for every idle insect, before the dice that usually say no. Roll first.
- `FightGoal.java:207-208`: the "goes for" diary text is built with `String.format` even when no diary listens. Build
  it inside the diary.

### Solid in this slice (do not "fix")

- The "once a second" offsets (`(tickCount + id) % 20`, `% 40`, `% 5`, and the `+4`, `+8` offsets) line up with
  Minecraft's own goal check (`Mob.serverAiStep` uses `tickCount + getId()`), so every goal is really asked.
- Claims are let go on every way out: `ClampCareGoal` and `WoundCareGoal` release in `stop` and their claims run out in
  40 ticks anyway; `AllogroomGoal` lets go only its own hold (`stop`, line 108); `tendedBy` and `groomedBy` all expire.
  A carried nestmate that dies or falls off clears the carrier's load (`AbstractAntEntity.java:1278-1279`); one that
  is carried is saved with its carrier; a carrier cannot fold into the numbers (`mayGoIntoNest` checks full jaws).
- A foe, patient, mate or player that dies, unloads or folds in reads `isAlive() == false` and the goal ends.
- `FightGoal`'s `unreachable` map is pruned on each pick, `sniffed` is capped at 12; `NoticeGoal`'s `seen` map is
  pruned past 16.
- `GoInsideGoal`, `WaitAtNestGoal` (`RETRY_TICKS`), `StayWithQueenGoal` and `QueenToRoomGoal` (`Errand.setOffFor`)
  remember a place with no way; their `canUse` rolls its dice first, so the route search is rare.
- `NoticeGoal` and `GroundShakes` with several players: each shake carries its player's id, spectators are skipped,
  a player who leaves or changes world ends the freeze; one search per jump or landing, 16 blocks.
- No goal here uses vanilla navigation or `MoveControl`; all walking goes through `AntNavigator`/`Errand`. No
  `level.getRandom()`. No species named in code (comments only).
- `RescueGoal` cannot take a raider out of a column: `MatabeleRaidGoal` (priority 1) runs for every column member
  without a fight order, and a fighter or a clamped one fails `mayHelp`.
- **Not proven, so not a finding:** `FleeGoal` searches a route inside `canUse` every 10 ticks with no "no way"
  memory (lines 82-85); it costs only while a worker outside the nest feels a hunter and has no way in, and a roofed
  gallery counts as inside, so I could not find a common case. Also: more than 5 nurses share the court's 5 places
  (`StandByQueenGoal.java:110`), so some stand on one spot; the legs' keep-apart shuffle may settle it. And BUGS 366
  (a fighter standing 30 s): no cause found in `FightGoal`; the search retries every 60 goal ticks (6 s) when all its
  walks fail, so 30 s needs about 5 failed rounds in a row.

---



## Slice: goals of work: errands, digging, carrying, feeding, sealing

`Errand`, `DigGoal`, `CarryCorpseGoal`, `CarryEggGoal`, `ClipGrassGoal`, `EatGoal`, `FeedLarvaGoal`, `FeedQueenGoal`,
`GatherFoodGoal`, `MouthToMouth`, `SealEntranceGoal`, `SealGoal`, `StoreFoodGoal` (framework `entity/goal/`) and the
test `TidySearchTest`. Every line read. Also read to walk paths: the load methods of `AbstractAntEntity` (347-628,
1445-1479, 2864-2935), `Refuse` (60-305), `Brood` (spawn, eggs, larvae, claims), `FoodStore` (105-275, 575-615),
`QueenCare.claimFeeding`, `Nest` (findDigTarget, canDig, isEntranceOpen), `NestWalls.plugSpots`, `BuildLife` (25-140),
`FieldLife` (50-60), `ColonyManager` (96-124), `Colony` (603-700, 894-905), `FightGoal` (360-407), the goal sign-ups in
`TermiteWorkerEntity`, `MatabeleEntity`, the queens and the king, and Minecraft 1.21.1's `Mob.serverAiStep` and
`GoalSelector` (from memory of the game's code: goals run in the order they were added, a running goal is ticked
only every other game tick unless it asks for every tick).

### R3-18 (A, proven; checked again): a paralysed nestmate eaten on the comb stays in the colony's headcount for ever

- **Checked again (the main session):** `CarryCorpseGoal.java:218-219` calls `releaseBody` and a plain `paralysed.discard()`. `AbstractAntEntity.remove` (`:513-528`) only leaves a body on `KILLED` and touches no count; the count's way down for one taken alive is `carriedOff` (`:1467-1479`), not called here. `macrotermes.json:124` has `eat_paralysed: true`. The fight thread's reader found the same line on its own.
- **Where:** `CarryCorpseGoal.java:217-223` (`fetchParalysed`: `paralysed.discard()` at 219); the right path exists in
  `AbstractAntEntity.java:1467-1479` (`carriedOff`, which takes it off the count); the only other count-down for a
  worker is `TermiteWorkerEntity.java:215-225` (`die`), for soldiers `TermiteSoldierEntity.java:453-462`.
- **What is wrong:** Macrotermes has `eat_paralysed: true` (`macrotermes.json:124`). A termite left paralysed by a
  Matabele sting is fetched, taken in the jaws and removed with a plain `discard()`. `discard` is not a death: `die`
  never runs, so `colony.setCount(caste, count - 1)` never happens, and it does not go into the numbers either
  (`Population.inside` is a separate count). The colony goes on counting a worker (or a soldier: `findParalysed`,
  `Refuse.java:275-276`, takes any insect of the colony) that is neither a mob nor a number. Nothing ever squares
  the count again: grep finds `setCount` only in deaths, births and `Population`.
- **What the player sees:** after each raid, every stung termite its nestmates carry in to eat leaves a ghost in the
  count (`/antcolonies` shows more workers than there are). Ghosts count toward `max_workers` (`Brood.java:339`), so
  a colony that had raids stops raising workers early and turns larvae into soldiers instead; ghost soldiers stand
  in for soldiers it never raises. A queenless colony with a ghost is never "empty" (`Colony.java:682-684`), so it
  never dies out and its nest is never abandoned.
- **Unchecked link:** whether the queen or the king can ever lie paralysed (raids are on feeding spots, far from the
  royal room). If one could, `discard` would leave the colony thinking its queen is merely "not loaded" and it would
  stop ticking near a player (`ColonyManager.java:106-108`). `findParalysed` has no guard against them.
- **Test gap:** `TermiteRaidDefenceTests.java:213-231` checks that the paralysed nestmate is fetched and leaves no
  body, never the headcount.
- **Fix proposed:** a body method "eaten by its nestmates" in `AbstractAntEntity` beside `carriedOff`: take it off
  the count of its `nestCaste()` (no fold-in: it is eaten, not carried off by a hunter), then discard. Call it at
  `CarryCorpseGoal:219`. `findParalysed` skips insects with no `nestCaste()` (queen, king). Add the headcount check
  to the test above.

### R3-19 (B, proven): `DigGoal` can start with a body in the jaws, and its first bite throws the body away

- **Where:** `DigGoal.java:67` and `93` (`ant.isCarrying()`, which is soil only, `AbstractAntEntity.java:392-394`),
  `180-181` (`setCarriedBlock` in `bite`); `AbstractAntEntity.java:361-368` (`setLoad` replaces the load, logging
  "took a load of ... while holding ..., which it let go"); `TermiteWorkerEntity.java:112` (Dig, priority 3, added
  before) and `114` (CarryCorpse, priority 3, added after).
- **What is wrong:** every other goal that picks something up asks `hasFullMandibles()`; `DigGoal` asks only "not
  holding soil". A termite builder carrying a body whose `CarryCorpseGoal` was stopped (a flee, a reload: the load is
  saved, `AbstractAntEntity.java:2876-2879`, the goal is not) is asked by `DigGoal` first, because the game checks
  goals in the order they were added and both are priority 3. One time in 20 (every time in the 2 seconds before it
  counts as idle, `looksForWork`) Dig wins, holds MOVE, and `CarryCorpseGoal` cannot take over at the same priority.
  The first bite sets a soil pellet over the body.
- **What the player sees:** now and then a body a termite was carrying vanishes without reaching the comb (and the
  comb misses its layer); a `[load]` warning in the log that the code calls "never seen".
- **Fix proposed:** `hasFullMandibles()` in place of `isCarrying()` at lines 67 and 93 (a soil pellet is a full jaw
  too).

### R3-20 (B, proven): the work goals run at half the speed their numbers say, and the numbers copied from them do not

- **Where:** no goal of this slice overrides `requiresUpdateEveryTick()`, so Minecraft ticks each running one only every
  other game tick (the trap of BUGS 35, fixed then only for grooming, naps and noticing). Counted in goal ticks:
  `DigGoal.java:29` (`BITE_INTERVAL 10`: a bite a second, so "plain dirt about 6 bites, roughly 3 seconds" at 168 is
  6 seconds), `ClipGrassGoal.java:39`, `EatGoal.java:37` ("about a second and a half", 23, is 3 s),
  `MouthToMouth.java:29-31`, `SealEntranceGoal.java:30` ("2 seconds" is 4), `SealGoal.java:31-34`, and every
  `GIVE_UP_TICKS` ("a minute" is two). Against them: `BuildLife.java:37-38` (`BITE_TICKS = 10`, "as DigGoal") and
  `FieldLife.java:55-56` (`CUT_TICKS = 50`, "as ClipGrassGoal") spend game ticks; the claims run out in game ticks
  shorter than the goals they cover: `Brood.java:37-39` (600 against `CarryEggGoal` and `FeedLarvaGoal`'s 2,400),
  `QueenCare.java:27` (1,300 against 2,400), `Refuse.java:34` (1,200 against 4,800), `FoodStore.java:46` (600
  against `GatherFoodGoal`'s 1,200).
- **What the player sees:** the builder numbers (now working near a player too, BUGS 398) and the founders while
  away dig at twice the pace of the real diggers the player watches; the forager numbers cut twice as fast. Near and
  away disagree by a factor of two. Claims expiring mid-job send a second worker after the same egg, body or larva
  (harmless: the second finds it gone and gives up).
- **Fix proposed:** one rule, not twelve number changes: these goals return true from `requiresUpdateEveryTick()`
  (they are cheap per tick; the costly part is `canUse`, which this does not change), so their numbers mean what
  their comments say and match `BuildLife` and `FieldLife`. Then re-read any timing a test pins.

### R3-21 (B, a call for the user): the insects never ask the `mobGriefing` game rule

- **Where:** `DigGoal.java:191-206` (`destroyBlock`, `setBlock`), `SealEntranceGoal.java:128`, `SealGoal.java:129`
  (`plugLeak`), `ClipGrassGoal.java:208` (`clippedGrass` re-sets and eats plants, hay bales included). The animals
  ask it (`Grazing.java:84`, `HippoEntity.java:1174`). `docs/DESIGN.md` says nothing either way.
- **What is wrong:** a server or world with `mobGriefing false` (often set to protect builds) still has termites and
  Matabele dig, plug and eat blocks, hay bales a player put down included.
- **Fix proposed (my recommendation, the user decides):** keep the colonies digging and sealing their own nest with
  the rule off (the nest is the colony's body; without it the mod does not work), and write that down in
  `docs/DESIGN.md`. Eating a player's hay bale is the one place worth asking the rule.

### R3-22 (B, likely): two goals read their target block every tick without asking whether its land is loaded

- **Where:** `ClipGrassGoal.java:96` (`level.getBlockState(grass)` in `canContinueToUse`), `DigGoal.java:94`
  (`Nest.canDig`, `Nest.java:699-704`, reads `getBlockState(pos)` with no `LandReady`).
- **What is wrong:** reading a block whose land is not loaded makes Minecraft load it on the spot (the R1-8, R1-14,
  R1-24 lesson). A forager walking to a far plant, or a digger to a far foraging hole, asks every tick.
- **Unchecked link:** whether a goal can still tick while its target's land is fully unloaded: the insect's own land
  must be ticking, and Minecraft keeps two rings of land loaded beyond that. A plant or hole more than about 3
  chunks from the insect, near the edge of a player's range, would do it; I did not measure foraging reaches.
- **Fix proposed:** `LandReady.at(level, grass) &&` in front at `ClipGrassGoal:96`, and in `Nest.canDig`.

### R3-23 (B, to measure): an idle nurse's search for a hungry young one costs three mob searches and never waits

- **Where:** `FeedLarvaGoal.java:82-93`, `109-133`; `Brood.getLarvae` (`Brood.java:151-153`), the castes'
  `fedByNurses`, `Colony.getKing` (`Colony.java:615-626`, a box search round the queen).
- **What is wrong:** when nothing is hungry `canUse` returns false without setting `nextTry` (only a failed route sets
  it), so every nurse repeats three mob searches about every 40 ticks, and on every goal pass in its last 2 seconds
  before counting as idle. `FeedQueenGoal` asks one cheap claim first; this one does the searches.
- **Fix proposed:** set `nextTry` when no hungry one is found too; or keep "who is hungry" on the colony once a
  second for all nurses.

### R3-24 (B, to measure): sealing a hole searches for a route at least twice, and once per unreachable nearer spot

- **Where:** `SealGoal.java:138-153` (`walkToAPlugSpot`).
- **What is wrong:** the one search for all spots (143, the 2026-11-10 tick-spike fix) is followed by the old loop
  (146-151), which searches again spot by spot from the nearest. A reachable spot costs a second search; each nearer
  unreachable spot costs a failed whole search, the spike the fix was for.
- **Fix proposed:** after the group search succeeds take `ant.getNavigator().getGoal()` as `stand`, as
  `Errand.setOffToNearest` does (`Errand.java:284-286`), and drop the loop.

### R3-25 (C, proven): `DigGoal`'s "soil waits" check is a harvester leftover, and half of it never runs

- **Where:** `DigGoal.java:75`, `86-89`, `95-97`; `ColonyHooks.java:239-241` (default `false`; grep finds no species
  overriding `waits`).
- **What is wrong:** no species answers the question, so it is dead code today. Its `ant.tickCount % 20 != 0` test
  also never fires for an insect with an odd id: the game asks `canContinueToUse` only on ticks where `tickCount + id`
  is even (`docs/HISTORY.md:201` notes the same trap).
- **Fix proposed:** delete the socket and its checks; if a species needs it later, check by the goal's own counter.

### R3-26 (C): wrong or stale comments

- `Errand.java:49-50`, `57`: `MOST_SPOTS` is never used and both comments link to `setOffTo`, which does not exist.
- `StoreFoodGoal.java:43`: "MillBreadGoal lets go of it": no such goal any more (the harvester).
- Harvester wording: `Errand.java:122`, `152`; `ClipGrassGoal.java:29-31`, `166-167`; `EatGoal.java:24`;
  `FeedLarvaGoal.java:30-33`; `FeedQueenGoal.java:18-20`; `GatherFoodGoal.java:22-24`; `MouthToMouth.java:33-35`;
  `StoreFoodGoal.java:16-18`, `86` (`RICH_SEEDS`).
- `ClipGrassGoal.java:210`: "so stop() does not free the tuft again", but `stop` frees by the ant's id anyway (130);
  harmless (`clippedGrass` removed the claim already, `FoodStore.java:592`).
- `MouthToMouth.java:94`: odd indent.

### R3-27 (C): `FeedLarvaGoal` and `FeedQueenGoal` carry the same three legs twice

- **Where:** `walkToPile`, `swallow`, `walkBack` and the put-back (`FeedLarvaGoal.java:179-213`, `286-317`;
  `FeedQueenGoal.java:125-159`, `211-242`): about 80 lines alike but for the rich-bread flag.
- **Fix proposed:** one shared "fetch nurse food, put it back" helper next to `MouthToMouth`, when either is next
  touched (the A finding above touches both).

### R3-28 (C): mouth to mouth turns the nurse's body from the mind

- **Where:** `MouthToMouth.java:95-104` (`setYRot`, `setYBodyRot`, `setYHeadRot` every tick of the pass).
- **What is wrong:** the body's own turn (`BodyTurn`, BUGS 36) is skipped: the nurse snaps to face. Small, but it is
  the mind steering the body.
- **Fix proposed:** ask the body to face the point and let `BodyTurn` turn it.

### R3-29 (C): `TidySearchTest` checks the rule, not the search

- **Where:** `TidySearchTest.java:22-37` against `FightGoal.java:364-384`.
- **What is wrong:** it is a real check of `nextSmells` and `remember`, the same helpers the game calls, and would
  catch a change to the "nearest unsniffed smell" rule. But its `walk` copies `searchScent`'s loop by hand and leaves
  out the step that drops a smell's neighbours when there is no way to it (381): a change in `searchScent` itself
  would pass.
- **Fix proposed:** none needed now; keep the loop in one place if `searchScent` changes.

### Solid in this slice (do not "fix")

- `Errand`: every goal of this slice walks through it, none touches Minecraft's navigation or `MoveControl`; "left
  alone" memory pruned on every read (and against a clock going back); the one-search `setOffToNearest`; `stop` only
  stops the legs.
- Claims: every claim taken in `canUse` is released in `stop` (eggs, bodies, paralysed, larvae and soldiers, the queen's
  feeding, grass, seeds, leaks); all are timed, so a claimer that dies or is unloaded frees them by itself.
- Loads survive a reload (`AbstractAntEntity.java:2876-2879`, 2921) and the carrying goals pick them up again
  (`CarryEggGoal:59-69`, `CarryCorpseGoal:77-87`, `StoreFoodGoal:44-55`); a dying carrier drops its load by its row.
  `EatGoal.stop` counts an interrupted meal as eaten; no food doubled or taken from the store and lost that I found.
- `canUse` costs: every search for work is behind `looksForWork` or a retry time; route planning inside `canUse`
  (`EatGoal`, `FeedQueenGoal`, `FeedLarvaGoal`) is gated and backs off 200 ticks on failure.
- `SealEntranceGoal`: never shuts a living thing into the hole (124-127); cheap once the colony has workers.
- No `level.getRandom()` in the slice; colony picks use `colony.random()` where the colony decides (`claimHungry`).
- `GatherFoodGoal.take` splits one item off the stack and keeps the rest lying.
- **Not proven, so not a finding:** `SealEntranceGoal` asks `errand.isIn(stand)` (the exact cell); a founder whose walk
  ends a hair outside it would re-plan every goal tick for 60 s. Not seen, not followed to the legs.
- **Not proven, so not a finding:** `DigGoal` re-plans each time a walk ends out of reach (up to 60 times); with the
  route already ending where it stands this is probably cheap.

---



## Thread: a goal's life from picked to dropped, and what it leaves behind

Read in full: every file in `framework/.../entity/goal/` (the header comments of `ScoutGoal`, `ClipGrassGoal`,
`SealGoal`, `SealEntranceGoal` and `StandByQueenGoal` were skipped, their code was read); `AbstractAntEntity.java`
lines 150 to 2245, 2290 to 2450 and 2836 to 3020 (not read: 1 to 150, the walking half 2450 to 2835 which is round
2's, the animation tail 3020 on). For how they meet: the goal sign-up lines of all six savanna insects,
`TermiteWorkerEntity`, `RetreatGoal`, parts of `MatabeleEntity`, and on the colony side the parts of `Population`,
`AwayLife`, `Colony`, `QueenCare`, `FoodStore`, `Brood`, `Refuse` and `Nest` that the goals call. Minecraft's own
`GoalSelector.tick`, `Mob.serverAiStep` and `LivingEntity.aiStep` were read from the decompiled game. **Not read at
all:** `fight/*`, `Clamp`, `Wounds`, `WorkerHunger`, the egg, larva, corpse and leg entities, `load/*`, and most
savanna goals (`BuildMoundGoal`, `ShapeMoundGoal`, `PlugHoleGoal`, `DefendGoal`, `WalkBeatGoal`, the Matabele goals).

### How Minecraft asks the goals (checked in the game's own code, needed for every finding below)

- `Mob.serverAiStep`: on every other tick the goal list is gone through in full (`goalSelector.tick()`), on the ticks
  between only the running goals tick, and of those only the ones that ask for every tick. So each goal that is not
  running has its `canUse` asked **10 times a second**, and a running goal's `tick()` runs **10 times a second**
  unless it says `requiresUpdateEveryTick`.
- `GoalSelector.tick`, in this order: stop the running goals whose `canContinueToUse` is false; then for each goal
  not running: ask `canUse`, and if true **call `stop()` on the lower goal holding its flags, then call `start()`**.
  A goal of the same priority is never pushed out.
- `LivingEntity.aiStep`: while `isImmobile()` is true (`AbstractAntEntity.java:1500-1505`: paralysed, pinned,
  clamping, held, calling for help, tended) nothing of the above runs. Goals freeze as they are, with no `stop()`.

### The priority table (lower number wins; all hold MOVE and LOOK unless marked)

| Insect | Goals by priority |
|---|---|
| Termite worker | 0 Float (JUMP), Flee, Retreat; 1 Eat, StoreFood, BuildMound; 2 ShapeMound, CarryEgg, FeedLarva, FeedQueen, PlugHole, Seal; 3 Dig, GatherFood, CarryCorpse; 4 ClipGrass, Scout, GoInside (MOVE), Notice; 5 StandByQueen, Groom, Allogroom; 6 AntWander (MOVE); 7 RandomLook |
| Termite soldier | 0 Float; 1 HoldStill (drumming, MOVE); 2 HoldStill (fed); 3 Defend; 4 GoInside; 5 WalkBeat; 6 AntWander; 7 RandomLook |
| Termite king | 0 Float; 1 SealEntrance; 2 StayWithQueen (MOVE); 3 Dig; 4 Groom; 5 AntWander; 6 RandomLook |
| Termite queen | 0 Float; 1 SealEntrance; 2 QueenToRoom (MOVE); 3 Dig; 4 AntWander; 5 RandomLook |
| Matabele worker | -1 BiteOut; 0 Float, Fight; 1 Eat, Scout, Raid, WayHome; 2 FeedQueen, FeedLarva, CarryEgg, CarryRemains, ClampCare, Rescue, WoundCare, BringPrey; 3 Dig; 4 GoInside; 5 WaitAtNest; 6 Nap, Groom, Allogroom; 7 AntWander; 8 RandomLook |
| Matabele queen | 0 Float; 1 Fight; 2 QueenToRoom; 3 GoInside; 4 WaitAtNest; 5 Nap, Groom; 6 AntWander; 7 RandomLook |

No two goals that steer the body are without a shared flag. No goal was found that can never run. Goals that tick
every tick: Nap, Notice, Groom, Allogroom, and the Matabele's BiteOut, Raid and WayHome. All others tick at half
pace (see R3-33).

### R3-30 (A, the mechanism proven and checked again; how it looks in play not run): a walk planned in `canUse` is thrown away by the goal it pushes out, so a fleeing worker's first try always fails, and a worker carrying food never flees at all

- **Checked again (the main session):** `FleeGoal.canUse` ends in `errand.setOff` (`:83-85`), `start` (`:94-97`) plans nothing, `canContinueToUse` (`:89-91`) needs `errand.isWalking()`; `Errand.stop` (`:82-84`) calls `AntNavigator.stop` (`:386-396`), which sets `route = null`; `StoreFoodGoal.stop` (`:70-71`) calls `errand.stop`. Minecraft's order (ask `canUse`, stop the lower goal, then `start`) is as I know `GoalSelector.tick` in 1.21.1; the reader read it in the decompiled game. Not run in the game: `hideUntil` is set before the walk is lost, so `GoInsideGoal` may still bring the worker in at a walk, which is why the tests pass.
- **Where:** `FleeGoal.java:57-86` (`canUse` ends with `return errand.setOff(...)`, the route is planned here),
  `:89-91` (`canContinueToUse` needs `errand.isWalking()`), `:94-97` (`start` plans nothing); `Errand.java:82-84`
  (`stop` calls the navigator's `stop`), and the same call in every other goal's `stop()` (for example
  `StoreFoodGoal.java:70-71`, `AntWanderGoal.java:199-201`); `AntNavigator.java:386-396` (`stop` forgets the route);
  Minecraft's `GoalSelector.tick` (asks `canUse`, then stops the lower goal, then calls `start`).
- **What is wrong:** all goals share one navigator. `FleeGoal.canUse` plans the way to the hole. Then Minecraft stops
  the lower goal that was running (clipping grass, gathering, storing, scouting, digging, strolling). That goal's
  `stop()` tells the shared navigator to stop, which wipes the route Flee just planned. Flee starts with no route.
  Two ticks later `canContinueToUse` is false and Flee stops. Its next try comes 10 ticks later (the `% 5` check at
  line 58 only passes on the ticks the goal list is gone through, so every 10 ticks, not 5). A worker carrying a
  seed is worse: `StoreFoodGoal.canUse` (`:42-55`) has no dice, so it starts again in the same pass in which Flee
  stopped. Every Flee try after that is wiped the same way. That worker never runs at `flee.speed()`.
  A worker carrying nothing gets away on the second try about two times in three, because `hideUntil` (line 80) is
  already set and `GoInsideGoal` (1 in 10 per ask) may start first and be the one pushed out.
- **The same shape, milder:** `GoInsideGoal.java:45-72`, `WaitAtNestGoal.java:38-56`, `QueenToRoomGoal.java:52-70`
  and `StayWithQueenGoal.java:55-75` plan in `canUse` and need a walk to go on. Each fails once when a stroll (or any
  MOVE goal) was running, wastes one route search, and tries again seconds later. `EatGoal.java:69`,
  `FeedLarvaGoal.java:95` and `FeedQueenGoal.java:78` plan in `canUse`, lose it, and plan again in `tick`: two
  searches per start. `AntWanderGoal` is safe (only RandomLook is below it, and that holds no route).
- **What the player sees:** Matabele reach a feeding spot. Termite workers there startle, twitch, stand half a
  second, and only then run, or walk in at walking pace. Workers with forage in the jaws do not run at all: they
  stop and start every half second on their way to the food store. More workers are caught than the flee numbers
  mean. It also costs about three route searches per 10 ticks for each such worker.
- **Why tests miss it:** the flee tests check only that workers end up inside. The second try or `GoInsideGoal`
  does that.
- **Fix proposed:** one rule for all goals: `canUse` only decides, `start()` plans the walk (as
  `savanna/.../RetreatGoal.java:63-67` already does). A goal that must know there is a way before it starts keeps
  what it found and sets off again in `start()`. Add a test that a worker carrying forage reaches flee speed.

### R3-31 (A, proven; checked again): in a colony without a queen, a worker holding food for her starts and stops `FeedQueenGoal` for ever and stands like a statue

- **Checked again (the main session):** `FeedQueenGoal.canUse` (`:62-67`) returns true at once for an ant holding nurse food; `walkToQueen` (`:162-167`) sets `done` when `colony.getQueen` is null and keeps the food; `stop` (`:100-107`) keeps it too. `StoreFoodGoal` asks `isCarryingSeed`, false for nurse food (`AbstractAntEntity.java:415-417`), and `FeedLarvaGoal.canUse` has no take-over for food already held. The work-goals reader found the same loop on its own, and adds: with a living queen it has no way to, each restart also runs failed route searches (the BUGS 42 pattern; likely, `:176`, `:184`).
- **Where:** `FeedQueenGoal.java:62-67` (holding nurse food: `canUse` is true whenever it has a colony, no dice, no
  claim), `:162-167` and `:190-195` (no queen: `done = true`, the food is kept), `:100-107` (`stop` keeps the food);
  `AbstractAntEntity.java:423-425` and `:409-412` (`isCarryingBread` is also true for food in the crop);
  `Colony.java:603-608` (`getQueen` is null when the queen is dead, or when her mob is not loaded), `:671-697` (a
  colony without a queen lives on until it is empty).
- **What is wrong:** the goal starts, finds no queen, ends, and keeps the food. Two ticks later `canUse` is true
  again because it still holds the food. This goes on for ever. `FeedQueenGoal` sits at priority 2 and takes MOVE and
  LOOK on every pass, so Dig, the stroll and every lower goal never run. `StoreFoodGoal` does not take it
  (`isCarryingSeed` is false for nurse food, `:415-417`). `EatGoal`, `CarryEggGoal` and `FeedLarvaGoal` need empty
  jaws. `mayGoIntoNest` (`:560-562`) is false with full jaws, so it is never folded into the numbers either.
- **How a worker gets there:** a nurse on its way to the queen when she dies (a termite queen is fed in a steady
  stream, so there is nearly always one). A nurse whose `FeedLarvaGoal` was stopped with food held (its give-up, or
  a higher goal): its comment at lines 33-34 says `FeedQueenGoal` takes the food over. A reload with food held (the
  load is saved).
- **What the player sees:** kill a termite queen. One or more workers in the nest freeze in place, food in the
  mouth, for as long as the colony lasts. They do not flee less (Flee is higher), but they do no work and never eat,
  so they starve standing.
- **Unchecked link (a second way in, likely):** `:176` and `:184`: when no way to a living queen is found the goal
  also ends with the food kept, and starts again next pass. Then each pass runs one or two failed route searches,
  10 times a second per such nurse. I did not check whether `Errand.walkOn` remembers the failed way.
- **Fix proposed:** with no queen (and when no way to her is found) put the food back, by the path `handOver`
  already has for "she wants no more" (`:211-219`: `pickReturnSpot`, else `putDownWhereItStands`). Do it in
  `canUse` too, so a worker that loads with food and no queen lets go of it.

### R3-32 (B, proven): `closeIn`'s one-second "no nearer" clock never runs for the goals that use it

- **Where:** `AbstractAntEntity.java:1602` (`closingAt < tickCount - 1` starts the clock afresh), `:1590-1591` (its
  comment: "Asked every tick while it is wanted (a gap of a tick starts it afresh)"); callers that tick every
  other tick: `Errand.java:134, 164` (used by `CarryEggGoal`, `CarryCorpseGoal`), `MouthToMouth.java:80`,
  `FightGoal.java:315`, `StandByQueenGoal.java:120`, `ClipGrassGoal.java:189`, `savanna/.../DefendGoal.java:218,
  257`.
- **What is wrong:** these goals tick every other tick, so the last call was always 2 ticks ago, and line 1602
  takes that as a gap. `closingTicks` is set to 0 on every call and never passes `CLOSE_IN_TICKS`. `NO_NEARER` can
  then only come from `stepToward` saying no. An insect pressing against something it cannot get nearer to is only
  freed by its goal's own long give-up (2 to 4 minutes, see R3-33). Only `MatabeleRaidGoal` (ticks every
  tick) gets the clock.
- **What the player sees:** a carrier or nurse a hair short of its spot keeps stepping on the spot for minutes
  instead of acting from where it stands after a second. This is likely what stood behind BUGS 145's forager, which
  was given its own second clock in `ClipGrassGoal` for the same thing.
- **Unchecked link:** whether the body keeps moving on the tick between two `stepToward` calls (round 2's code).
- **Fix proposed:** count a gap as more than 2 ticks, or better count game time. Then `ClipGrassGoal`'s own clock
  can go. A test: a carrier blocked 0.1 short of its spot acts within about a second.

### R3-33 (B, proven): goals count their own ticks at half pace, so most written times are double (the list BUGS 35 and 357 did not name)

- **Where:** `FightGoal.java` (`idleTicks` against the species' `give_up_ticks`, `look_over_ticks`, `CHASE_TICKS`
  200 with a diary line at about 306 that says 10 s, `SEARCH_EVERY`, the turn per tick); `FightGoal.java:137` and
  `FleeGoal.java:58` (`% 5` gates that really pass every 10 ticks); `DigGoal` (`BITE_INTERVAL` 10: the comment's
  "about 3 seconds" for dirt is 6; `GIVE_UP_TICKS`); `EatGoal` (`EAT_TICKS` 30: "a second and a half" is 3 s); the
  `GIVE_UP_TICKS` of `CarryEggGoal`, `CarryCorpseGoal` (2400 is 4 minutes), `FeedLarvaGoal`, `FeedQueenGoal`,
  `StoreFoodGoal`, `GatherFoodGoal`, `FleeGoal`; `ClipGrassGoal.CLIP_TICKS`; `ScoutGoal.LOOK_TICKS`;
  `SealGoal.PACK_INTERVAL`; `SealEntranceGoal.PACK_TICKS` ("2 seconds" is 4); `MouthToMouth.SWALLOW_TICKS` and
  `FEED_TICKS`.
- **What is wrong:** each counts `tick()` calls, which come 10 a second, and the comments and species numbers read
  them as game ticks. BUGS 357 found this for the care goals only. Nap, Notice, Groom and Allogroom ask for every
  tick and are right.
- **What the player sees:** nothing new: many of these were tuned by eye in the game at this pace. The cost is for
  whoever tunes a number in a species file next: it means half what it says.
- **Fix proposed:** needs the user's word, as 357 did, because looks confirmed in play would change. Either count
  game time everywhere with one small helper and halve the numbers so nothing changes in play, or write "goal
  ticks (10 a second)" on each number. Do it with 357, as one change.

### R3-34 (B, to measure): what "may I start" costs per idle worker

- **Where and how often** (each `canUse` is asked 10 times a second per mob):
  - `AbstractAntEntity.java:597-599` (`looksForWork`): in the 2 seconds before a worker counts as idle, every
    own-role goal searches on **every** ask: about 3 goals times 20 asks, 60 searches each time a worker runs out
    of work. After 5 idle seconds `mayDo` is true for every role, and each looks-for-work goal searches 1 ask in 20
    (ClipGrass and Scout 1 in 40): about 4 full searches a second per idle worker. By day nearly every termite
    worker inside is idle.
  - The dear searches: `FeedLarvaGoal.java:109-133` (`claimHungry`: all larvae, each caste's `fedByNurses`, and
    `getKing`, mob searches over the nest); `CarryCorpseGoal.java:96-98` (two searches, one a mob search for the
    paralysed); `CarryEggGoal.java:78`; `DigGoal.java:78` (walks the dig plan); `SealGoal.java:70`;
    `GatherFoodGoal.java:59` (an item search over the whole reach); `ClipGrassGoal.java:86`.
  - Steady, on every ask: `AntWanderGoal.java:70-80, 123-132` (about 3 colony look-ups, the alarm, the clock,
    maybe `nest.isInside`); `StandByQueenGoal.java:58-68` (`hasRole` and `isQueenHome` 10 times a second per
    attending nurse).
  - Throwaway lists: `GoInsideGoal.java:57` builds a set of all room cells once a second per Matabele staying in;
    `AntWanderGoal.java:170-177` and `AbstractAntEntity.java:1208` search `getAllRoomCells`, a new list on each call
    (`Nest.java:478-494`).
  - `AntWanderGoal.java:132-146` (`rest`): an insect away from its place tries up to 6 route searches about every 5
    seconds and does not remember the ways that failed (BUGS 42's lesson, it uses no `Errand`).
  - `FightGoal.java:348` (`pickFoe`): a mob search of a 25-block box per fighter each second.
- **What the player sees:** not known. With 50 to 60 real termites at about 0.02 ms each it may be nothing.
- **Fix proposed:** measure first: in a full day-time nest scene count calls and time per goal's `canUse`. If it
  shows, the colony can keep one "work on offer" answer per role per second that all idle workers read, instead of
  each worker searching for itself. Cache the room cell set in `Nest`.

### R3-35 (B, likely): `RescueGoal` has no claim on the caller, every free nestmate sets off for the same hurt ant

- **Where:** `RescueGoal.java:60-70, 73-86` (`canUse` and `start`), `:142` (the hurt ant only learns of its helper
  at its side).
- **What is wrong:** the same shape BUGS 349 fixed for care in the nest with `claimCare`: nothing says "I am going".
  All in `help_reach` walk there, one picks up, the rest give up at the spot.
- **What the player sees:** after a fight, a clump of ants running to one caller while a second caller waits.
- **Unchecked link:** how many are free in play: raiders and ants going home do not answer, so it may be few.
- **Fix proposed:** a third care job (rescue) claimed in `canUse` through the claim BUGS 349 added.

### R3-36 (C, proven): small things

- `AbstractAntEntity.java:1456` (`fallParalysed`): `setLoad(CarriedLoad.NONE)` wipes what it held. The comment at
  1421-1426 says it "drops what it holds", and death does drop it (`dropLoadOnDeath`, `:574-580`). An egg in a stung
  nurse's jaws is lost. Fix: drop through the load's own `drop`.
- Five copies of `face()` write the body's turn from a goal: `FightGoal.java:457`, `AllogroomGoal.java:138`,
  `ClampCareGoal.java:131`, `WoundCareGoal.java:135`, `RescueGoal.java:198`, and `MouthToMouth.java:95`. The mind
  writing the body. Fix: one "face this" method on the body.
- `EatGoal.java:125` holds the meal as a carried item. A save or a freeze in the middle of a meal leaves it in the
  jaws, and `StoreFoodGoal` or `FeedQueenGoal` then carries the bite away. Harmless (it eats again).
- `AbstractAntEntity.java:2996-3003` (`filmClip`): any insect named `film:<clip>` with a name tag plays that clip.
  A film hook a player can reach. Harmless. Fix: only while the film camera runs.
- `FleeGoal.java:80`: `hideUntil` is set in `canUse` before a way in is known, so a worker with no way in is told
  to stay in while outside.
- Stale words from the harvester ant: `StoreFoodGoal.java:43` ("MillBreadGoal"), and harvester lines in the
  comments of `Errand`, `AntWanderGoal` and `GoInsideGoal`.

### Solid on this thread (do not "fix")

- **Mind and legs are kept apart.** No shared goal calls Minecraft's own navigation or the move control: every walk
  goes through `Errand` and `AntNavigator`. The few places that move a body by hand (`setDownHurt`, the knock-back
  in `tickAttack:1675`, the clamp holding its holder still) are body code, not goals.
- **No claim can leak for long.** Every colony claim has its own end time (eggs, larvae, seeds and corpses 600 to
  1200 ticks, the queen's feeder 1300, a leak or a hole 600, care 40 and renewed each goal tick, a hold 40). So
  death, unloading, folding into the numbers, freezing, or the missing `stop()` while `isImmobile` never holds a
  job for more than about a minute. Do not add release calls on every way out.
- **Every carrier picks its load up again** after a higher goal or a reload: egg, body, seed, hurt nestmate. A
  rescue carry lives through the carrier's death and a reload (`tickRescue`, `:1277-1292`).
- **Goals of the same priority never push each other out**, so the many goals at 2 and 3 do not flip-flop.
- `AllogroomGoal.stop` lets go only of its own hold. `DigGoal`'s crack fades by itself.
- Nap, Groom, Allogroom and Notice use even offsets in their checks, so they really are asked.
- The queen and king frozen far from a player (`restsWhileColonyIsFar`, `:2370-2376`) simply pause; `backFromFrozen`
  needs no goal clean-up.
- `forgetGoalsForFilm` (`:3006`) is called only by the film camera, not reachable in play.
- `EatGoal` waiting while on a colony errand is one gate on purpose, not two judges.
- **Not proven, so not a finding:** a nursery with free spots but no way to them making nurses pick eggs up and put
  them down in a loop (`CarryEggGoal`); Float's jump meeting the navigator's climb in water (round 2's); whether the
  move control holds its aim on the tick between two `closeIn` calls.

---



## Thread: a fight from first bite to death and after, near and away

Read in full: `fight/*` (all 7 files), `entity/Clamp.java`, `Wounds.java`, `AntCorpseEntity.java`, `LostLegEntity.java`,
`goal/FightGoal.java`, `FleeGoal.java`, `RescueGoal.java`, `ClampCareGoal.java`, `WoundCareGoal.java`;
`AbstractAntEntity.java` lines 470-580, 660-2040, 2305-2437, 2836-2957. `goal/CarryCorpseGoal.java` lines 40-296.
Read in part, only to walk paths to their end: `colony/Population.java`, `AwayLife.java`, `Refuse.java`, `Strays.java`,
`ModEvents.java`, the savanna's `MatabeleEntity`, `TermiteSoldierEntity`, `DefendGoal`, `Raid.java` (lines 1775-1812)
and both species files. Not read line by line: the rest of `AbstractAntEntity`, the raid code, `DangerMap`.
`docs/BUGS.md` searched for clamp, FightGoal, Wounds, NestDefence, paraly, invulnerab, peaceful, HeldCells.

### The fight, in short

1. A foe is found three ways: a fight order with a spot (`orderToFight`, given by `NestDefence`, a raid or the
   skirmish command), a blow taken (`getLastHurtByMob`, read by `FightGoal.pickFoe`), or, for termite soldiers, the
   species' own `DefendGoal`. `Fights.mayAttack` is the one rule of who may be attacked.
2. `FightGoal` walks to the foe, steps the last bit (`closeIn`), turns to face it and calls `strike`.
3. A Matabele blow is a clip of 24 ticks: jaws close at tick 5, the sting lands at tick 11 if the foe stayed in
   reach all that time (`tickAttack`). A termite may twist away (`dodges`) and bite back at once.
4. A sting on prey never kills: half a point and one dose of venom (`takeSting`). With enough doses it staggers
   (`venom_ticks`), then lies paralysed until carried off (`carriedOff`), fetched by its own, or dead after a day.
5. A soldier's bite does its damage, then may clamp on to a leg (`Clamp`) or cut one (`loseLeg`, `LostLegEntity`).
6. A clamped ant fights no more and goes home; the holder is dragged along and squeezes every 20 s.
7. A lost leg opens a wound (`Wounds`): licked, maybe infected after 6 game hours, maybe deadly after a day and a half.
8. The hurt call for help (`callForHelp`); `RescueGoal` pulls clamps off and carries them home; in the nest
   `ClampCareGoal` and `WoundCareGoal` work on them, one carer of a kind at a time (`claimCare`).
9. Death: each species' `die` takes one off the headcount; `remove` leaves an `AntCorpseEntity`.
10. The colony hears of every hurt by a living attacker (`ColonyNews.MEMBER_HURT`): the danger memory, the alarm,
    and `NestDefence` if a player did it.
11. As numbers there is no fight in the framework at all. Only the savanna's raid rolls dice for it
    (`Raid.java:1794-1795`). Wounds, legs, clamps and venom live only on a body.

### R3-37 (A, likely; the code checked again, not run in the game): a player who keeps walking is almost never stung

- **Checked again (the main session):** `strike` (`:1626-1651`) starts the clip and takes the sting's wait (`nextPlayerSting`, 30 ticks) at once; `tickAttack` (`:1654-1700`) sets `gripping` at `gripAt` only if `reachesInFight` holds then, drops it on any later tick out of reach, and lands the blow only at `hitAt` while still gripping. For a Matabele (`MatabeleEntity.java:106`, grip 5, hit 11; `attackFor` gives this clip for anything not below it) the player must stay within the jaws' reach (a third of a body length past its box, about 0.17 blocks for a major) from tick 5 to tick 11. One correction to the reader: the player need not be in reach from tick 0, only from the grip. A player is never held still (`:1681-1683`), and the ant stands still through its blow (`FightGoal.java:175-182`).
- **Where:** `AbstractAntEntity.java:1626-1651` (`strike`), `1654-1700` (`tickAttack`, the `holds` test at 1658-1659,
  the grip at 1680-1682, the grip lost at 1684-1687, the hit at 1688), `1547-1550` (`reachesInFight`);
  `FightGoal.java:175-182` (it stands still for the whole blow); the Matabele's clip in
  `savanna/.../entity/MatabeleEntity.java:106` (`new Attack("attack_a", 24, 5, 11, 21)`).
- **What is wrong:** the grab-and-sting was made for termites: the foe must be in reach when the blow starts, still
  in reach at tick 5, and in reach on every tick up to tick 11, or it is "a miss". For a termite the grip holds it
  still. A player is never held. The ant itself stands still during the blow. Reach is the ant's box plus a third of
  its body length (about 0.17 blocks for a major), so the player's middle must stay within about 0.67 blocks of the
  ant for 11 ticks. A walking player covers 0.216 blocks a tick, 2.4 blocks in 11 ticks. So only a player who stands,
  sneaks or mines is stung. A jumping player is also out of reach for most of each jump (reach is 0.2 up).
  The 30-tick wait between stings (`nextPlayerSting`, line 1635) is spent when the blow starts, hit or miss.
- **What the player sees:** break into a Matabele nest, then walk away: twenty squeaking ants follow, play their
  attack clip at your heels and never hurt you. Stand still and they do. The defence looks toothless.
- **Unchecked link:** not run in the game. The defence tests keep the player still (`MatabeleDefenceTests`), so they
  cannot show it. This is arithmetic from the code and the player's known walking speed.
- **Fix proposed:** one rule, not a new number: a foe that cannot be gripped (anything that is not an insect) is hit
  at `hitAt` if it is in reach at that moment, or the blow on it lands at once as a clip-less blow does. Spend
  `nextPlayerSting` only when the sting lands.

### R3-38 (A, proven for leaving; likely for the swap near a player; checked again): a hurt ant that goes into the numbers comes back whole

- **Checked again (the main session):** `mayGoIntoNest` (`AbstractAntEntity.java:560-562`) asks only alive, empty jaws, no target, not riding, no clamp; `Population.goingIn` (`:543-568`) asks only that, sight and distance; `AwayLife.frozenOf` (`:274`) keeps out only the downed, and what it writes down (`Frozen`: caste, age, load, last meal) has no place for legs, wounds or venom. Four readers met this hole from four sides (the insect slice, the fighting slice, both threads); this is the fullest write-up. The open link is the same for all: how often a caste has more real mobs than wanted while hurt ones are about.
- **Where:** `AbstractAntEntity.java:560-562` (`mayGoIntoNest`), `2889-2897` (legs, wound and clamp are saved on the
  body only); `colony/AwayLife.java:270-287` (`frozenOf`, line 274 keeps out only the downed), `293-322`
  (`foldLoaded`); `colony/Population.java:543-577` (`goingIn`, `goIn`).
- **What is wrong:** lost legs, the open wound and its course, and a dead head on a leg exist only on the real mob.
  When the colony goes away (no player within 64 blocks, or the land unloads) `frozenOf` writes every living member
  that is not felled down as a plain adult and discards it. When it comes out again it is a new mob: six legs, no
  wound. Near a player the same happens through `Population.goIn`: `mayGoIntoNest` asks about loads, clamps and
  riding, but not about legs, wounds, venom or being held, and it prefers mobs inside the nest and out of sight,
  which is exactly where the hurt lie. So step 5's wound care (80 in 100 infected untended, 9 in 10 of those dying)
  and "3 or 4 legs gone never raids again" are undone by walking away, and by ordinary swapping when the colony has
  more real mobs than it wants. By the same gap a stung termite (staggering or paralysed) lying out of sight in a
  gallery can be swapped into the numbers near a player, cured, from under the raiders.
- **What the player sees:** hurt ants are carried home after a raid; come back later and no ant in the colony has a
  leg missing, and none ever dies of a wound. Stung termites can vanish from an opened gallery.
- **Unchecked link:** the away half is walked (line 274 is the only test). For the near half I did not check how
  often a caste has more real mobs than `targets` wants once raiders are home; when it has, the path is open.
- **Fix proposed:** one place that says "this body carries a state the numbers cannot hold" (legs gone, wound open,
  a head on a leg, venom, held in jaws), asked by both `mayGoIntoNest` and `frozenOf`. Such a body stays real, as a
  paralysed one already does in `frozenOf`. Folding the state into the numbers is a bigger job and not needed
  for the few hurt there are.

### R3-39 (B, proven): held cells are never cleared away, so "any held?" stays yes and the old world is kept

- **Where:** `fight/HeldCells.java:30` (the static map), `36-45` (`hold`), `60-63` (`any`), `69-93` (`barred`, the
  only clean-up, lines 79-82); readers `Fights.java:287`, `FightGoal.java:430`, `nav/LevelCellMap.java:47`.
- **What is wrong:** a hold lasts 10 ticks, but its entry is only removed when somebody later asks about that very
  cell. A cell nobody asks about again (the soldier left, its land unloaded, the gallery was sealed) stays in the
  map with its insect. So (1) `any(level)`, the "cheap first question", answers yes for the rest of the sitting once
  one soldier has held a cell, and every breaker then pays the extra shut-in search in `FightGoal.java:430`;
  (2) the map is keyed weakly by the world, but each kept insect points back at its world, so the old world can
  never be freed: leave the world and open another in the same sitting and the first one stays in memory.
- **What the player sees:** nothing direct. Memory that grows with each world opened in one sitting (size to
  measure), and a little raid work that never switches off.
- **Fix proposed:** sweep expired holds on a slow clock (once a second per world), and drop the world's map on
  level unload. No new rule needed.

### R3-40 (B, proven mechanism, effect to measure): only one bite in half a second lands on one body

- **Where:** `fight/Fights.java:147-151` (`strike`: clamp and leg cut only `if (hit ...)`), `172-176` (`squeeze`).
- **What is wrong:** Minecraft makes a mob that was just hurt safe from equal or smaller blows for 10 ticks. So when
  several soldiers bite one ant, only the first bite in each half second counts; the others do no damage and also
  lose their chance to clamp or cut a leg. A squeeze that falls in that window is lost too. The ants' side is not
  held back the same way: a sting's prick may be refused, but `takeSting` (line 133) always counts. So ten ants on
  one termite work at full speed, ten soldiers on one ant work like two.
- **What the player sees:** a mobbed ant survives far longer than the bite numbers say.
- **Unchecked link:** how often two bites fall within 10 ticks in a real raid; one soldier alone bites every 2 s.
- **Fix proposed:** say which is wanted. If mobbing should count, insect bites use a damage type that skips the
  cooldown (`bypasses_cooldown` tag), which is one data file, no new rule.

### R3-41 (B, a reading, not a measurement): a living clamp nearly always costs the leg before care can save it

- **Where:** `entity/Clamp.java:169-175` (squeeze every `squeeze_ticks`), `fight/Fights.java:172-181` (each squeeze
  rolls the holder's `leg_cut`); `goal/ClampCareGoal.java:48-63, 120-128`; `macrotermes.json` (`squeeze_ticks` 400,
  `leg_cut` 0.15 minor soldier, 0.30 major), `megaponera_analis.json` (`slowing` 0.75, `care_ticks` 600, `clean` 0.9).
- **The arithmetic:** a clamped ant walks at a quarter speed, about 0.6 blocks a second. From a spot 40 blocks out
  that is about 64 s, 3 squeezes. In the nest the carer needs one full round to kill the holder (600 goal ticks,
  60 s as things run, BUGS 357): 3 more squeezes. Six squeezes: the leg is kept with 0.85^6 = 38 in 100 (minor
  soldier) or 0.70^6 = 12 in 100 (major). The real figure the file aims at is 9 in 10 kept. `clean` 0.9 is only
  rolled for the head that is left, after the leg has mostly gone already. In a raid the column pulls clamps off in
  10 s, before the first squeeze, so there the leg is kept: the two paths disagree widely.
- **Also:** when a squeeze takes the leg, `legLost` lets the holder go alive (`Clamp.java:221-225`). In the nest
  that is a living termite soldier walking free among the ants, far from its mound. Nothing there orders the ants
  to fight it, and its own colony cannot fold it in (it is outside `realMembers`' area). Likely, not run.
- **Fix proposed:** fewer rules, not more: drop the leg roll from the squeeze (damage only), so `clean` is the one
  place that decides the leg, as the papers say. A holder whose leg comes off dies with it.

### R3-42 (B, a reading, to measure): the cost of a raid near and as numbers is not the same kind of thing

- **Where:** `fight/Fights.java:147-151`, `entity/Wounds.java:131-140`; the numbers side in
  `savanna/.../colony/Raid.java:1794-1797` (`lost_share` 0.01, `hurt_share` 0.03, plus 0.12 dead and 0.3 hurt per
  answering soldier).
- **The arithmetic:** near, each landed soldier bite leaves the ant clamped or a leg short with 0.2 + 0.8 x 0.15 =
  32 in 100 (minor soldier) or 0.3 + 0.7 x 0.3 = 51 in 100 (major). A major ant (10 health, home at 4) takes 4 to 6
  bites before it leaves, so nearly every ant that really fights a soldier comes home hurt (1 - 0.49^3 = 88 in 100
  after three major bites), and almost none dies in the fight. Then the wound: licked, 0.2 x 0.2 = 4 in 100 die;
  never tended, 0.8 x 0.9 = 72 in 100. As numbers: about 1 in 100 dead, 3 in 100 "hurt", and hurt is only a line in
  the away report, no wound, no later death, no rest from raiding. One-on-one times fit the raid: a worker falls in
  about 37 ticks (1 / 0.65 grabs of 24 ticks), a major soldier in about 120 (two stings at 0.4 a grab), against an
  attack of at most 2400 ticks and a quiet rule of 300. Rescue fits too: a call lasts 200 ticks, help comes from 6
  blocks in 2 to 3 s.
- **What does not fit:** deaths in the fight near (close to none) against 1 in 100 plus 12 in 100 per soldier away;
  the hurt near (many, with a real death risk after) against the hurt away (few, with none).
- **Fix proposed:** measure first: one soak line per real raid, "raiders, bitten, clamped, legs lost, dead", next to
  the away dice. Then make the away dice copy what was measured. No code change proposed before that.

### R3-43 (C, proven): small things lost over a reload in the middle of a fight

- **Where:** `AbstractAntEntity.java:1438-1441` and `2924` (the stagger's slowing is not put back: a stung termite
  loaded mid-stagger walks at full speed until it falls); `2929-2934` with `1029-1035` (a holder loaded without
  its victim walks through blocks and floats for up to 100 ticks, then lets go); `Clamp.java:330` (the squeeze
  clock restarts at 400 whatever the species file says).
- **Fix proposed:** put the stagger back in `tickFight` when `isEnvenomed()` and the modifier is missing; set
  `noPhysics` only when the victim is found.

### R3-44 (C, proven): the headcount's "one died" is written three times in species code

- **Where:** `TermiteSoldierEntity.java:453-463`, `TermiteWorkerEntity.java:215-226`, `MatabeleEntity.java:454-465`,
  against the shared `carriedOff` (`AbstractAntEntity.java:1467-1479`).
- **What is wrong:** the framework owns the headcount but leaves each body class to remember the death. A new
  species that forgets it has immortal numbers. It is also why the eaten-paralysed case above was missed.
- **Fix proposed:** `AbstractAntEntity.die` does it for any body with a `nestCaste()`; the species keep only extras.

### R3-45 (C, proven): in Peaceful the nest defence runs but can do nothing; the sting is not "2 hearts" off Normal

- **Where:** `fight/NestDefence.java:100-102` (`fair`), `fight/Fights.java:136-146`.
- **What is wrong:** the sting is a mob attack, which Minecraft scales: none in Peaceful (so no pain effect either,
  `stung` is false), 3 in Easy, 4 in Normal, 6 in Hard. In Peaceful the waves still pour out, chase and squeak.
  This is how bees behave too, so it may be wanted. It is nowhere written down.
- **Fix proposed:** the user's call. Either leave it and note it in `docs/DESIGN.md`, or have `fair` say no in
  Peaceful so the ants only come out to look.

### Solid on this thread (do not "fix")

- **Every hold on a body ends by itself.** Held in jaws (`pinnedBy`) is dropped the tick the holder dies, leaves the
  world or ends its blow (`AbstractAntEntity.java:877-879`). Care claims last 40 ticks unless renewed (`claimCare`),
  tending 20 to 40 ticks, a help call 200 ticks, a fight order has its own end time and its own giver
  (`endFightOrder(by)`), hiding and the player-sting wait are plain times. None needs the other side to say goodbye.
- **A clamp through death and reload.** Holder dies or is stung: the head stays, the holder is freed
  (`Clamp.java:157-162`). Victim dies or the leg comes off: the holder walks again (`tickClamping`, `legLost`).
  Both saved by id and found again; a side that never comes back is let go after 100 ticks.
- **All fight clocks use the game time or tick counts**, so `/tick rate`, sleeping and `/time set` cannot skip or
  stall venom, paralysis, wounds or orders.
- **Creative and spectator players** provoke nobody and are never a foe, through one test (`NestDefence.fair`),
  also when one hits an ant (`FightGoal.usable` asks `PROVOKED_BY`). Two players are kept apart by id throughout.
- **`Pain`'s static map** is safe between worlds (it drops times that lie in the future) and stays small.
- **Away and paralysed:** a felled insect is never folded when the colony goes away (`AwayLife.java:274`); it lies
  and dies by the game clock, and its death takes one off the count once.
- **A sting never kills and never fails to count** (`Fights.java:129-134`), and a felled insect is nobody's foe
  (`isFoe`, `mayAttack`), so nothing keeps stabbing a body on the ground.
- **No knockback from insect bites** (`knockback`, line 1716) works with how Minecraft orders a hit.
- **Corpses and lost legs cannot pile up:** a loose corpse crumbles, one cell of refuse holds one corpse, a leg
  piece lasts 6000 ticks.
- **Mind, legs and body are kept apart in every goal read:** they only call `getNavigator().moveTo`, `closeIn`,
  `stop`. The fight files name no species.
- **Not proven, so not a finding:** (1) a holder that walked off after 100 ticks alone could be snapped back across
  the map when its victim loads later (`Clamp.java:146-155` finds it by id anywhere); it needs the two to be saved
  in different chunks, which dragging makes rare. (2) Termites lost to raiders reach the danger memory as "hurt",
  never "killed" (a sting cannot kill, `carriedOff` tells no news); I did not read how `DangerMap` weighs the two.
  (3) Several free ants set off for one caller in `RescueGoal` (no claim until one stands beside it); they stop as
  soon as one tends it, and real ants crowd a caller too. (4) `stepsToTheOpen` reads a neighbour block before its
  loaded check (`Fights.java:251`); an insect that ticks has loaded neighbours, so I could not make it load land.

---

## Seen by more than one reader

Written once above; the other copies were left out.

- **R3-38 (going into the numbers heals):** all four of the insect slice, the fighting slice, the goal thread and the
  fight thread. The goal thread adds: `mayGoIntoNest`'s check `getTarget() == null` checks nothing, because no insect
  code ever calls `setTarget`; and it does not ask about a fight order or a raid or scout errand either (round 1's
  open question "does it fold marching raiders" is this same hole). The fighting slice adds: a wounded Matabele
  nursed out of sight skips the infection deaths.
- **R3-18 (the eaten nestmate in the headcount):** the work-goals slice and the fight thread.
- **R3-31 (the nurse with no queen):** the work-goals slice and the goal thread.
- **R3-10 (the foe search):** the fight-goals slice (every goal tick while there is no foe) and the fight thread (once
  a second with a foe, a flood search per candidate).
- **R3-33 (half-speed clocks):** all of the fight-goals slice, the work-goals slice (kept as R3-20 for the numbers
  copied from the goals), the goal thread and the fight thread. Named by them: `FightGoal`, `FleeGoal`, `ScoutGoal`;
  `give_up_ticks` 2400 lasts 4 minutes; the fight diary's seconds are wrong; the raid's deadline covers the fight's
  give-up.
- **R3-32 (`closeIn`'s clock):** the fight-goals slice and the goal thread.
- **R3-39 (held cells):** the fighting slice and the fight thread.
- **R3-1 and R3-43 (the venom's slowing lost on reload):** the insect slice, the fighting slice and the fight thread.
