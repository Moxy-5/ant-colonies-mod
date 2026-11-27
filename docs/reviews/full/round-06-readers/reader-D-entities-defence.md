# Round 6, reader D: the Matabele entities, the size spread, the nest's defence and the sting, the species file

Read at `b72cba68` (worktree `review-round-6`). No code changed, nothing run. Round 3's findings (R3-1 to R3-45) and
round 11's (R11-1 to R11-22) were read first and are not repeated; where a finding here is a new side of one, it says
which.

## Findings

### D-1 (A, likely; one link not run): a player joining over LAN or a server crashes (or is thrown out) when a Matabele worker comes into view

- **What is wrong:** the worker's constructor asks the species file for its size spread, and the species files exist
  only on the server. On a game that is only a client (a friend joining a LAN world, anyone on a dedicated server) the
  answer is not there and the code throws.
- **What the player would see:** single player and the LAN host are fine (the client shares the server's memory). A
  second player joining the world is disconnected or crashes the moment a Matabele major or minor is sent to them, so
  in practice as soon as they come near a Matabele nest. (A guest who opened a single-player world earlier in the same
  sitting would not crash: the numbers from that world stay in memory, see the static below.)
- **Where:** `framework/.../entity/AbstractAntEntity.java:286-290` (the constructor calls `applyBodySize()` on both
  sides), `:305-313` (`applyBodySize` asks `ownSize()` whenever the mob has the SCALE attribute, which every living
  mob has); `savanna/.../entity/MatabeleEntity.java:295-299` (`ownSize` calls `sizes()` before it looks at
  `lengthMm`), `:236-239` (`sizes()` calls `SpeciesProfiles.get(SPECIES)`); `framework/.../species/SpeciesProfiles.java:76-81`
  (`get` throws when the species is missing), `:17-20` ("Server side only") and `ColonyFrameworkMod.java:56` (the
  profiles are a server data reload listener, `AddReloadListenerEvent`).
- **The proof:** on a client, `Mob`'s constructor skips `registerGoals` but `AbstractAntEntity`'s own constructor still
  runs `applyBodySize()` (line 289). `getAttribute(Attributes.SCALE)` is not null for a living mob, so line 308 calls
  `MatabeleEntity.ownSize()`, whose first act is `sizes()`, which calls `SpeciesProfiles.get`, which throws
  `IllegalStateException("No species profile for antcolonies:megaponera_analis ...")` because `profiles` is empty on a
  client that never ran a server. No other insect's `ownSize` reads a profile (the base returns 1); the client code
  (`framework/.../client`, `savanna/.../client`) reads no profile either, so this one line is the only such path found.
- **Unchecked link:** what the 1.21.1 client does with an exception while making an entity from a packet (I expect a
  disconnect with a network error; it may be a crash). The game tests run on a server only and `runClient` is an
  integrated server, so no test or session of ours could have met it.
- **Fix proposed:** `ownSize` returns 1 while `lengthMm` is 0, before it asks for the sizes (a client never has a
  length; its SCALE comes from the server, which sends it). One line, no new rule.

### D-2 (B, proven; Owner's call): one continuous size spread, but two bodies decide everything, and 77 in 100 workers get the full major's numbers

- **What is wrong:** each worker rolls a real length (5 to 18 mm), and that length only sets how big it is drawn and,
  inside its body, how many termites it carries. Everything else follows which of the two models is nearer, with the
  split at 10.6 mm (the root of 15 x 7.5): health and damage (`fight.bodies.major` 10 and 3, `minor` 5 and 1.5),
  breaking the termites' mud open (`breaksIn`, `openWayTo`, `breakIn`), scouting and leading a raid (majors only), the
  carry range (4 to 10 against 1 to 3). Speed is the same for all (the user's pick, kept). With the file's groups,
  every in-between worker (11.5 to 13.5 mm, a quarter of the colony) and the top of the minors' group are full majors:
  77 in 100 workers are majors in play, though the file's own note says "about half majors, a quarter in between, a
  quarter minors".
- **What the player would see:** a 10.5 mm ant has half the health and half the bite of a 10.7 mm one beside it, and
  only the second bites mud open. Raids are three quarters "majors" while the drawn sizes look like a smooth spread.
- **Where:** `MatabeleEntity.java:229-234` (`pickBody`), `:273-275` (`hasMajorBody`, the type alone), `:472-476`
  (`fightBody`), `:495-498`, `:628-631`, `:641-644` (break-in for majors only); `SavannaNumbers.java:83-85`
  (`majorModelFor`, the split), `:759-767` (`loadFor`, the one thing that follows length); `AbstractAntEntity.java:849-864`
  (health and damage from the body's name only); `megaponera_analis.json:5-14` and `:82-85`; the 77 in 100 is pinned by
  `MatabeleColonyTests.java:152-153`.
- **The proof:** triangle (5, 10, 11.5): the share above 10.6 mm is 0.9^2 / (6.5 x 1.5) = 0.083 of the minors' quarter,
  so 0.02 + 0.25 + 0.5 = 0.77 (the test asserts 0.77).
- **Real biology:** Megaponera workers are one caste of continuous sizes (the brief's Villet 1990; the file cites
  Longhurst and Howse 1979); what a worker does at a raid goes with its size (big ones break in and carry bundles,
  small ones go in and sting). A step in strength at one length is the game's, not the ant's.
- **Fix proposed (fewer rules):** keep the two models for drawing only, and let length set the rest the way `loadFor`
  already does: health and damage on the same `t` along the spread between the minor's and the major's numbers;
  "breaks in" and "may scout" from a length in the file (for example 12.5 mm, the middle of the in-between group)
  rather than from the model.
- **Owner's call:** should an ant's strength follow its own length? Today: two fixed bodies, 77 in 100 count as majors.
  Reader's pick: yes, health and damage by length, break-in and scouting from about 12.5 mm up (about 60 in 100).

### D-3 (B, proven by the code and Minecraft's rules, not run; Owner's call): the sting can kill an unarmoured player standing at the nest in about 2.5 seconds

- **What is wrong:** each sting is 4 points (2 hearts) of mob damage, each ant may sting once every 30 ticks, and up
  to 20 defenders are out. The only limit on the total is Minecraft's half-second hurt cooldown (a second hit of equal
  damage inside 10 ticks is refused), so three ants at a player's feet already land two stings a second: 8 points a
  second on Normal, 12 on Hard (R3-45: difficulty scales it). With R3-37 (a walking player is almost never stung) the
  defence has two settings, harmless and deadly, and nothing between.
- **What the player would see:** dig into a Matabele nest with a shovel without armour and you die in about 2.5 seconds
  once the first wave is at your feet; in full iron (about half the damage taken off) about 5 seconds; in diamond about
  10. Walk and nothing happens at all.
- **Where:** `megaponera_analis.json:244` (`sting.damage 4`, `every_ticks 30`), `:238` (`most 20`), `:235-236`
  (`first_wave 5`, `wave 5`); `framework/.../fight/Fights.java:136-145` (one sting = `player.hurt(mobAttack, 4)`);
  `AbstractAntEntity.java:1635` (the per-ant wait); Minecraft's `LivingEntity.hurt` (the 10-tick cooldown, from memory
  of the game's code) and `Player.hurt` (difficulty).
- **Real biology:** the Megaponera sting is one of the most painful of any ant but is not dangerous to a person; the
  pain lasts hours. `docs/DESIGN.md` (the player, line 72 of the Matabele section) chose 2 hearts a sting with
  "diamond with Protection makes a swarm manageable", so a deadly swarm may be wanted; the rate was not written down.
- **Fix proposed:** the pain is the real part, the damage the game part. Either a sting of 1 (half a heart) with the
  pain doing the work (slowness and the red edge, longer with each sting), or keep 4 and let a player be stung by the
  colony at most once a second (one number in `Pain`, which already keeps each player's stings). Do it with R3-37's fix,
  which will make stings land far more often.
- **Owner's call:** how dangerous should a Matabele swarm be? Today: about 2.5 s to death unarmoured if you stand
  still, nothing if you walk. Reader's pick: painful, rarely deadly: a sting of 1 point plus longer pain.

### D-4 (B, proven): a misspelt key or section in the species file quietly uses a number nobody chose, or switches a whole system off

- **What is wrong:** R1-2 and R1-42 made a broken file stop loudly. A misspelt optional key is not broken: every
  `optionalFieldOf` takes its default and unknown keys are ignored. A misspelt or missing section is just "not there".
  For the Matabele that means:
  - `defence` misspelt: the `nest_defence` part is still listed and made, but `NestDefence.numbers()` is null and every
    method returns at once: the nest is never defended, with no message (`NestDefence.java:95-97, 115, 139-141, 219-221, 433-435`).
  - `worker_sizes` misspelt: `pickBody` makes every new worker a major (`MatabeleEntity.java:232`, `sizes == null`)
    and no ant gets a length.
  - `fight.prey` misspelt (or a wrong species id in it): `Fights.listed` is false, so the Matabele may not attack
    termites at all (`Fights.java:89-103, 198-200`): raids walk out and catch nothing.
  - Any single key (`keep_going`, `sting.damage`, `queen_reach` ...) falls back to the number in code; most equal the
    file today, but `queen_reach` does not (code 0: the queen never stings).
- **What the player would see:** after a tuning edit with one typo, a system silently stops or changes; the log says
  nothing.
- **Where:** `DefenceNumbers.java:33-46`, `Pain.java:36-43`, `FightNumbers.java:60-70`, `SavannaNumbers.java:59-64`
  (`WorkerSizes`, required keys: a typo there is loud, but a typo in the section's name is quiet), `SpeciesSection.java:76-82`.
- **Fix proposed (one check, not many):** at load, list every key in a species file that no section's codec read and
  log it as an error (or refuse the file, as R1-42 does for a broken one); and a part that cannot work without its
  section (`nest_defence` without `defence`) says so at load, as `ColonyParts.checkSpecies` already does for an unknown
  part name. This is framework-wide (the termites' file has the same shape); I found no earlier finding on it.

### D-5 (B, proven): two rules squeak at a player beside a raid column

- **What is wrong:** raid rework R7 made one squeak rule for a column (`Raid.squeak`: the raider nearest each player
  squeaks, louder and more often the nearer). The older rule in `NestDefence.touchesAColumn` is still there: every
  member in a column or on a fight order within 3 blocks of a player squeaks with a 1-in-4 chance each second. They
  also disagree on who hears: `Raid.squeak` squeaks at creative players, `NestDefence` does not.
- **What the player would see:** walking beside a column, the measured warning squeak of R7 plus a random chatter
  from every ant within 3 blocks (with 10 ants near, about 2 or 3 more squeaks a second).
- **Where:** `framework/.../fight/NestDefence.java:400-402`; `savanna/.../colony/Raid.java:2198-2230`.
- **Fix proposed:** delete lines 400-402 (the touch that provokes, 392-399, stays); the column's squeak is the raid's.

### D-6 (B, proven for the code; how often in play likely): a Matabele with no colony lives for ever, and one whose colony is gone joins any colony of its kind near it

- **What is wrong:** an ant of no colony is never hungry (`WorkerHunger.java:63-70`), never grows old (`AbstractAntEntity.java:652-669`
  needs a colony), never despawns (`:2973-2976`), never defends or is provoked (`Fights.java:90-93` needs a colony),
  and keeps its goals ticking. On each load it searches again and joins the nearest Matabele colony within 32 blocks
  of its centre (`MatabeleEntity.java:437-449`), adding one to that colony's count with no check of `max_workers`.
- **Who gets there:** a spawn egg or `/summon` more than 32 blocks from any Matabele colony; the 1 to 60 ants of
  `/antcolonies skirmish` (`MatabeleCommands.java:237-246`: made with no colony, they join a colony within 32 blocks on
  their first tick, so a 5-minute test fight leaves up to 60 permanent members); an ant whose colony record is gone (a
  world opened without it); an ant that went through a portal (its colony is in another world's list, so its death
  never takes it off the count).
- **What the player would see:** lone ants that wander for ever and cannot be hurt into defending themselves; a nearby
  colony that grows past its cap after a skirmish. Creative and op paths mostly, so B, not A.
- **Real biology:** a Megaponera worker cut off from its colony does not live long, and a foreign worker is not taken in.
- **Fix proposed:** let a worker of no colony starve on its species' starve time (the hunger clock runs without a
  colony; framework-wide, so the termites too), and the skirmish ants keep out of colonies (`keepOutOfColonies`, as
  the tests do). Joining a colony on load only if it is the ant's own.

### D-7 (C, proven): the model lengths are written in two places

- `InsectSizes.java:47-48` (15 and 7.5 mm, "in code, not in the species file", `:21-22`) and
  `megaponera_analis.json:7-8` (`major_model_mm`, `minor_model_mm`). The size factor drawn comes from the first, the
  ant's own size (`ownSize`, `MatabeleEntity.java:298`) and the model split from the second. Changing the file alone
  draws every ant at the wrong size. **Fix:** read the model lengths from `InsectSizes` and drop the two keys (or check
  at load that they agree).

### D-8 (C, proven; a new side of R3-38): what a worker loses on going into the numbers

- Its own length (it comes back a new size, maybe the other body, `Population.java:618-640` with `pickBody`), its
  `ScoutMemory` (its favourite direction and reach, `MatabeleEntity.java:366-367, 406-412`), and its personality (said
  on purpose, `AbstractAntEntity.java:2127-2129`). The length and the personality are fine as "the numbers carry no
  individuals". The scout memory is a scout's learning: a scout folded in the nest between trips starts over.
  Likely harmless (the colony's finds live in `Raids`); say so in `ScoutMemory`'s comment, or keep scouts real.

### D-9 (C, proven): `isBusyWithChore` says "care" but leaves out two care goals

- `MatabeleEntity.java:898-902` names `ClampCareGoal` but not `WoundCareGoal` or `RescueGoal`. Its callers
  (`Scouts.java:274`, `Raids.java:995`) then take a nestmate licking a wound, or one on its way to a hurt caller, as a
  scout or raider first among equals; the raid and scout goals (priority 1) push the care goal (2) aside mid-lick. Small:
  the care claim runs out in 40 ticks and another carer takes over. **Fix:** add the two goals.

### D-10 (C): dead and stale

- `SavannaTags.MATABELE_NEST_LOGS` and `tags/block/matabele_nest_logs.json` are used nowhere (log nests were dropped,
  `MatabeleNest.java:31-32`); the species file's `_note` still says "then under a fallen log" and "colony.max_workers
  2500" (the value is 100).
- A dead Matabele worker leaves no body (`MatabeleEntity.java:161-165`, "No body of its own kind yet (M8 step 5 has the
  dead carried home)"): step 5 is built, the dead are not carried, and `MATABELE_CORPSE` is used only for dead larvae
  and dropped loads. **Owner's call (looks):** should a raider killed in the field lie there as a body (real dead
  raiders are left where they fall)? Today it vanishes in a puff. Reader's pick: yes, `leavesBody` true; the refuse
  rules already crumble it.
- `SavannaEntities.java:89-90` ("a major about 1.7 blocks long ... the user picks the real ones later"), `:305-306`;
  `SavannaSpecies.java:8` ("its species file still to come"); `MatabeleEntity.java:77-89` (steps "to come") and `:347`
  ("wound care by nestmates is step 5").
- `MatabeleCommands.java:535`: `/antcolonies prey` says "about laid x 10" termites; a layer is 1 termite now
  (`raids.carry.termites_per_layer 1`).
- `MatabeleEntity.java:172-185`: health and damage written again in code (only the first tick's values; the file's win).
- `fight.bodies.*.damage` (3, 1.5, 3) is never used by a Matabele: a sting on a player uses `defence.sting.damage`, on
  termites the prick, and the file has no `foe_types`, so nothing else is ever a foe (a wolf, a fox or any mod animal
  biting an ant is never bitten back and does not provoke the nest). Its note says it counts "on a chicken". Either
  say the number is unused or add the foes the real ants sting (real Megaponera sting vertebrate intruders; the user
  may want that only with the savanna's land animals).

### D-11 (B, test gaps)

- `MatabeleDefenceTests` is one scene with a player held still. Nothing checks that a wave stings (only
  `defendersOut()`), that the queen stings in her room, the sting's damage after armour, a sneaking player crossing the
  entrance unprovoking, a player touching a column, or two players at once. A test that the defence lands stings on a
  moving player belongs with R3-37's fix.
- Nothing can catch D-1: every game test runs on a server. A small check that makes each signed-up insect type on a
  level with the species profiles emptied (or a client-side `create`) would.
- `MatabeleBodyTests` (naps) and `MatabeleTests` (climbing) touch nothing of the size code; the length's save and the
  body mix are guarded in `MatabeleColonyTests.java:86-103, 136-154` (good).

## Numbers of the file against real Megaponera and against each other (not findings unless named above)

- Worker lengths 5 to 18 mm, majors about 15: real (the file's groups). The 77 in 100 majors in play: D-2.
- `colony.max_workers 100` against real colonies of about 400 to 2,300: a written departure ("Honest sizes",
  `docs/DESIGN.md`), kept.
- `lifespan_ticks` 60 game days (a game day a real day): real workers live many months; fits the colony's balance
  note.
- Health 10 (major) against the termite major soldier (12 health, 1.5 bite, two stings to fall, dodge 0.6): one on
  one the ant wins in about five grabs while the soldier needs about four bites (more with clamps): the ant should
  and does win, as design and the real raids say (R3-42 has the near/away side).
- Sting 2 hearts every 1.5 s per ant: D-3.
- `defence.reach 16` within `watch_radius 48`; `look.radius 2` over a 3-block `nest_radius`: fit together.
- The brood times (`queen.incubation_ticks 3600`, `brood.hatch_ticks 1200`) are hours on the TIME scale where a real
  ponerine takes weeks from egg to worker; I did not follow how `Brood` uses them, so whether they ever bind against
  food and `max_young 10` is unread. Not a finding.

## Solid in this slice (do not "fix")

- **The length is saved and sent:** `BodyLength` is written and read (`MatabeleEntity.java:406-422`), and the size
  reaches every player through the SCALE attribute, which the game syncs and saves; `readAdditionalSaveData` sets the
  scale after the base class's model-size pass, so the order is right. A test guards the reload (`MatabeleColonyTests:86-103`).
- **The body and the length agree:** the colony picks the body with `Colony.random()` (`Population.java:680-681`),
  then the ant re-rolls its length for that body by rejection (`SavannaNumbers.java:88-96`), which gives exactly the
  spread's share on each side of the split; empty or one-sided groups fall back without a crash.
- **`NestDefence` per player:** every map is keyed by player id; a player who logs out, changes dimension, or goes
  creative or spectator is dropped within a second (`:225-231`); creative and spectator never provoke and are never a
  foe (`fair`, also asked by `provokedBy`); a jump does not reset the wait (`OFF_FOR`); a sneaking player crosses the
  entrance freely. Two colonies near each other each judge their own nest.
- **During a raid the column is in the defence's reach:** `Raids.reach()` widens `foragingRadius`, so
  `realMembers` covers the column and a player touching it far out is still seen.
- **`Pain`:** small, safe between worlds (R3's note), the pain grows with stings in a window and caps at 15 s.
- **`StingPainOverlay`** is client-only (`Dist.CLIENT`), draws only while the effect is on, and reads nothing from the
  server side.
- **Goal list (Matabele side):** BiteOut at -1 checks `(tickCount + id) % 10` on the ticks the goal list is gone
  through (Minecraft runs the full list when `tickCount + id` is even, so the check is really asked); Float and Fight
  share 0 with no shared flag clash; chores at 2 give way to scouting and raids at 1 as finding 3 of the raid review
  wanted; Eat shares 1 with them, so a raider eating finishes its 3 s meal first (fine).
- **Every tick per ant:** `tick()` does two cheap checks after the first tick, `customServerAiStep` one hunger tick a
  second and one heal check a minute; nothing synced or sounded every tick; no searches. The per-ant searches
  (`pickBreach`, `scentCells`, `mayBiteOut`'s loop over all colonies) run only in raids and the bite-out reflex
  (round 11's ground); `getParts` is cached by stage.
- **Commands:** every `/antcolonies` Matabele command needs permission level 2, and each number argument has bounds,
  so a bad argument is refused by the game before the code sees it.
- **Spawn eggs** are only in the creative tab; the Matabele have no natural spawns (no biome modifier), so the
  CREATURE category's cap never meets them.

## Read

In full: `MatabeleEntity.java` (989 lines), `megaponera_analis.json` (all, the raid/scout/trail sections only skimmed
as told), `SavannaNumbers.java` lines 1-140 and 740-768, `InsectSizes.java`, `BodySizes.java`, `NestDefence.java`,
`DefenceNumbers.java`, `Pain.java`, `StingPainOverlay.java`, `SavannaEntities.java`, `MatabeleDefenceTests.java`, the
two Matabele tags and `matabele_food.json`, `SpeciesProfiles.java`, `WorkerHunger.java`. In part, to follow calls:
`AbstractAntEntity` (constructor, size, fight numbers, hurt, age, remove, save), `Fights.java` 30-200,
`FightNumbers.java` codecs, `Population.java` 396-401 and 600-681, `AwayLife.java` 265-285, `Colony.java` 726-792,
`Raid.java` 2194-2230, `Raids.java` 913-919 and 975-1003, `Scouts.java` 262-283, `MatabeleBiteOutGoal.java` 40-150,
`MatabeleCommands.java` (permissions, `skirmish`, `prey`), the Matabele lines of `SavannaItems`, `SavannaSounds`,
`SavannaEvents`, `SavannaMod`, `SavannaSpecies`, `SavannaTags`, `ModEvents.onBreak`, `ModEffects`. Test files
`MatabeleBodyTests` and `MatabeleTests` read for their test names and what they set up, not line by line.
`SpeciesProfile.java` read only where `section` and `read` meet.
