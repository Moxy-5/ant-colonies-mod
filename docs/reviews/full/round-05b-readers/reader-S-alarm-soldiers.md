# Round 5b, reader S: the alarm and the soldiers

Read at commit `a7c0a9f0` in `C:\Ant Mod`, no code changed, nothing run or measured.
Short paths: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is `framework/src/main/java/com/antcolonies/...`.

## S-1 (A, the sums proven; how often it shows to see in play): the minors take the whole soldier share of the real-mob cap first, so at night beside a full colony, and whenever a raid's drumming reaches the nest, every real major is sent into the numbers

- **What is wrong:** a colony may have at most a quarter of its real-mob cap as real soldiers (50 / 4 = 12 for the
  termites). The code that shares those 12 out asks the minors first and gives them as many as they have beats for,
  up to all 12; the majors get only what is left. A full colony has 15 minors and 5 majors. At foraging time the minors'
  beats include every post outside (one by each way out and a sentry every 3 blocks along each trail), easily 12 or
  more, so the majors are given 0. When a foe's or a breach's drumming reaches the nest the rule becomes "every soldier
  the colony has", 15 minors, so again the minors take all 12 and the majors 0. The colony's population keeper then
  sends real majors that are out of a player's sight into the numbers, 2 a second, until none is left.
- **What the player would see:** near a grown colony at night, no major soldier anywhere: the royal chamber's doorway
  and the garden doorways (the majors' beats) stand empty. In a raid that reaches the nest, or when the player breaks
  into the mound, the majors that were walking their beats vanish just as the alarm starts, the very ones BUGS 210 put
  first in line against insects ("the big heads are the plug").
- **Where:** `savanna/colony/Soldiers.java:571-589` (`wantReal`: `for (Rank rank : Rank.values())`, `soldierCap - soldiers`
  shared in that order); `savanna/entity/TermiteSoldierEntity.java:78-79` (`Rank` order: `MINOR` then `MAJOR`);
  `savanna/colony/Soldiers.java:222-241` (a minor's posts: the outside posts first while `minorsOut`);
  `savanna/colony/Soldiers.java:362-416` with `savanna/colony/SentryLine.java:49` (one post a way out, a sentry every
  `sentry_spacing` 3 blocks along each trail); `framework/colony/Population.java:383-392` (`targets`), `:493-540`
  (`direct`: `have > want` sends mobs in), `:551-576` (`goingIn`: any mob out of sight, inside first);
  `framework/entity/AbstractAntEntity.java:560-563` (`mayGoIntoNest`: nothing about an alarm task; `DefendGoal` never
  sets a target).
- **The proof:** `macrotermes.json` `real_cap` 50, so `soldierCap` 12; `soldiers.per_worker` 0.1 and
  `minors_per_major` 3 give `wantedMinors(200)` 15 and `wantedMajors(200)` 5 (`SavannaNumbers.java:254-261`). Night,
  watched: `want` for minors = min(min(posts, 15), 12 - 0). With one trail of 40 blocks, its line alone holds about 12
  posts (`along`: `i * 3 <= length - 1.5`), plus one by each way out, plus the tunnel ends: 12. Majors then get
  min(min(posts, 5), 12 - 12) = 0. `called` (`reachesNest`): `posts = count`, the same result. `direct` sees 0 majors
  wanted and real ones there, and `goIn`s up to `swaps_per_second` (2) a second of those out of sight. A major walking a
  beat inside the nest is out of every player's sight.
- **Unchecked link:** how many outside posts a real grown colony has at night (the trails' count and length), so how
  often the minors' beats reach 12 by night. By day it depends on the feeding places and tunnel ends; with fewer than
  12 minor beats the majors get the rest and nothing shows.
- **The fix proposed:** no new number: share the quarter between the ranks in the colony's own ratio (3 minors to 1
  major, the species file's `minors_per_major`) before filling either, or let the majors' heart beats (the royal
  doorway and the shaft's way in, the first two of `posts(MAJOR)`) be taken first. A test: a full colony, watched, at
  night with a long trail: at least one real major on its beat after a minute; and with a breach reaching the nest, no
  real major goes into the numbers.
- **Owner's call:** today the minors get every real soldier place first. My pick: share them 3 to 1 like the colony
  itself (about 9 minors and 3 majors real), which keeps the royal doorway guarded and the majors at a raid.

## S-2 (B, proven; a new side of R5-18 and of R6-2): termite soldiers never starve and never die of age, so a colony whose workers are all gone lives on for ever as a handful of soldiers

- **What is wrong:** a soldier cannot feed itself; the nurses (workers) bring it fungus. Without them a real soldier
  slows only (`slow_after_ticks`, 2 days), it never dies of hunger, neither as a mob nor as a number in the nest. Only
  workers die of old age. A colony ends only when nobody is counted, and soldiers are counted. So when the workers
  die out (a starved garden, raids, the queen gone and no young), the colony never ends: its soldiers live on, slow,
  for ever, the colony is never turned into a dead mound, and it still counts as a living colony.
- **What the player would see:** a mound that should be dead (no workers, no queen, no building, no foraging) still
  listed as a colony in `/antcolonies list`, with slow soldiers walking its beats for ever; no dead mound for the
  Matabele to move into (DESIGN: they nest in dead termite mounds).
- **Where:** `framework/colony/Meals.java:49-57` (`dietOf`: any caste but the workers gets `starveAfter` 0, "never");
  `framework/colony/Meals.java:27-28` (the comment says so on purpose); `framework/colony/Population.java:220-232` and
  `:653-667` (old age: workers only); `savanna/entity/TermiteSoldierEntity.java:51-53`, `:310-321`, `:438-449` (a hungry
  soldier only slows); `framework/colony/Colony.java:724-734` (`queenDied`, `isEmpty`: "no workers or soldiers").
- **The proof:** walked above; no other place kills a soldier for want of food or of age (grep for `starv` and
  `lifespan` in `framework/colony`).
- **The fix proposed:** one rule, not two: soldiers get the workers' starve time in `dietOf` (they eat the nurses'
  food, so they starve when nobody brings it), as R6-2 made the Matabele queen starve. That also ends the soldier-only
  colony through the existing `isEmpty`. R5-18's king is the same question.
- **Owner's call:** today soldiers only slow when unfed. My pick: they starve like workers (real *Macrotermes* soldiers
  depend entirely on being fed by workers and die without them; I am not sure how many days a soldier lasts unfed).

## S-3 (B, proven): a soldier already answering one trouble cannot hear a second one, however near and however bad

- **What is wrong:** a soldier listens for drumming only while it is answering nothing (`hasHeard` returns early). So a
  soldier sent to the nest cell nearest a player's footsteps on the mound, or to a foe at one feeding place, does not
  hear a raid breaking a gallery open 9 blocks away until its first trouble has faded (30 s after the last footstep,
  80 s after the last foe was seen there). Troubles only join when they lie within 8 blocks (`merge_within`).
- **What the player would see:** a player running about on the mound while a raid breaks into a gallery near the
  mound: the soldiers stand at the mound drumming, the breach gets only the soldiers that had heard nothing yet. Two
  raiding parties at two feeding places: the soldiers that went to the first stay there.
- **Where:** `savanna/colony/Alarm.java:483-496` (`listen`: `if (troubles.isEmpty() || hasHeard(...)) return`),
  `:498-506` (`hasHeard` over every trouble), `:540-569` (`workOutTasks`: each soldier gets a task only from the trouble
  that holds it in `heard`).
- **The proof:** `heard` entries are only added in `listen` and moved by `foldInto`; a trouble keeps them until it is
  dropped by `live` at strength 0.
- **The fix proposed:** a soldier answers the worst trouble whose drumming reaches it now: `listen` asks every trouble
  each time and moves the soldier to a worse kind (breach over foe over felt). No new number. A test: footsteps felt
  on the mound, then a breach 10 blocks off: the soldiers by the footsteps go to the breach within a few seconds.

## S-4 (B, proven in the code; how it looks to see in play): the drumming is felt 16 blocks round the trouble through solid ground, and is never passed on by the soldiers that hear it, though the comments and the design say it is

- **What is wrong:** every soldier within 16 blocks of the trouble in a straight line hears it (43.3 blocks a second,
  so all at once), through solid soil, across unconnected galleries, from the surface into the nest. A soldier that
  hears it drums (the head bang) but nobody hears that drumming: the reach is measured from the trouble only. So the
  alarm never reaches further than 16 blocks (48 cm at the mod's scale, 1 block = 30 mm), however many soldiers stand in
  a row along the gallery. The comment on `listen` says "it drums a moment (the others feel that in turn)", and the
  plan's step 3 says the drumming "relays outward".
- **What the player would see:** a raid at a feeding gallery 30 or 40 blocks out (most feeding places): the soldiers at
  the nest never hear it and never come; `reachesNest` is never true, so no soldiers come out of the numbers. Next to
  it, a soldier in a separate gallery 10 blocks off through solid ground answers at once.
- **Where:** `savanna/colony/Alarm.java:470-475` (`reached`: distance from `trouble.at` only), `:479-496` (`listen`),
  `:449-468` (`reachesNest`); `savanna/entity/TermiteSoldierEntity.java:427-433` (each soldier asks every 5 ticks);
  `macrotermes.json` `alarm.foe`/`breach` 16.
- **Real biology:** Hager and Kirchner 2013 (as the design quotes it): one pulse fades within about 40 cm (about 13
  blocks), soldiers that feel it drum back within about 0.3 s, so the signal is relayed soldier to soldier through the
  galleries over several metres at about 1.3 m a second. The mod's 16 blocks is about one soldier's own range with no
  relay; a relayed alarm on 1.2 m (40 blocks) of gallery would reach the nest in about a second, through soldiers only.
- **The fix proposed:** one rule in place of the fixed reach: a soldier hears a trouble when the trouble or a soldier
  that has heard it (and drummed) is within the pulse's reach, so the alarm runs as far as soldiers stand. That removes
  the need for a separate `reachesNest` (the nest hears when a soldier in it hears). Keep distance through open cells
  only if cheap (a soldier in a separate gallery feels the ground too; I am not sure the real signal is limited to the
  galleries: it travels through the substrate).
- **Owner's call:** today a raid far out is answered only by the soldiers within 16 blocks of it. My pick: the relay
  (real), with a balance check, because more soldiers would reach far raids (step 5's warning: many soldiers at once
  wiped raids out).

## S-5 (B, likely): a foe's trouble jumps to whichever enemy the entity list gives last each second, and the soldiers' places round it are handed out again each time

- **What is wrong:** once a second the alarm looks for enemies near the foragers, the ways out and its troubles, and
  feeds the trouble with each one in turn. For a foe's trouble, each feed moves the trouble's place to that enemy. With
  20 raiders about a feeding place the trouble ends each second at the last raider in Minecraft's list. The places
  round it are worked out again from the new spot (a whole-nest search, see S-8), and every soldier whose cell is no
  longer in the new ring is given a new one.
- **What the player would see:** before the cover breaks (the raid at the feeding place, or a player hitting
  termites), the soldiers keep walking between places instead of holding them; their spot moves with one raider.
- **Where:** `savanna/colony/Alarm.java:334-370` (`tick`: `trouble(world, FOE, at, foe)` for each near foe),
  `:246-249` (same kind, not a breach: `near.at = at`), `:626-633` (`cellsNear` recomputed when `at` changes),
  `:587-588` (`places` drops every place no longer in the ring).
- **Unchecked link:** how far apart the raiders round one feeding place stand in play (if within a block or two, the
  ring barely changes).
- **The fix proposed:** a foe's trouble stays where it started (as a breach does) while it is fed within the merge
  distance; or moves to the middle of the foes. Fewer rules: treat every kind as the breach is treated.

## S-6 (B, likely; to measure; R5-8's shape): a soldier with no way to its alarm place is given the next place at once, so a soldier cut off from a trouble can run a failed route search every quarter second, cell after cell

- **What is wrong:** `DefendGoal` walks the soldier by asking the legs straight (not through the shared `Errand`).
  When no route is found to its place, it marks the cell bad for everyone for 10 s and drops its task; within 5 to 10
  ticks it asks again and the alarm hands it the next place, which costs another full route search. The places are the
  ring round the trouble plus every covered nest cell within 24 blocks (`SPOTS_REACH`), nearest first, so there can be
  dozens. BUGS 99 measured such failed searches at about 1 ms each (64 a second cost 73 ms).
- **What the player would see:** only when soldiers that heard cannot reach the trouble: a minor outside at night
  when the holes are shut (`ClosedHoles`) and the trouble is an insect's (places only under cover, inside); a lag
  spike while the trouble lasts.
- **Where:** `savanna/entity/goal/DefendGoal.java:254-275` (`stand`: `moveTo`, on failure `noWayTo` and `task = null`),
  `:79-89` (`canUse` every 5 ticks), `savanna/colony/Alarm.java:712-722` (`noWayTo`), `:595-608` (next free slot),
  `:695-702` (the nest cells within 24 blocks as places); `framework/nav/AntNavigator.java:469-478` (the "asked in vain
  just now" shortcut only for the very same goal).
- **Also:** `stand` re-plans whenever the legs are done but the soldier is not in its cell (`isDone() ||
  !cell.equals(walkingTo)`), without asking `Errand.gaveUp` (R2-2): a soldier on a wall in its cell (`isClimbing`), or
  one the legs gave up on, plans again every tick; the chase does the same at `:223`.
- **Unchecked link:** a real scene where soldiers that heard have no way to the places (the shut holes at night are my
  best guess); not measured.
- **The fix proposed:** walk with the shared `Errand` (`walkOn`/`gaveUp`), and on a failed search give the soldier the
  one cheapest answer: "no way to this trouble" for it (left alone for the trouble's life, as `WalkBeatGoal` does for
  beats), not the next cell. Or one route search to the nearest of all free places (`Errand.setOff(Collection)`), as
  `setOffToNearest` does for loads.

## S-7 (C, the path proven; how often likely): a soldier shut out by a seal is handed its beat by the seal even right after it found no way there, so it tries again every second or two for up to a day

- **What is wrong:** `claimPost` returns the seal beat before it looks at the soldier's "no way there" list
  (`avoid`). `WalkBeatGoal` then plans a route to it, fails, gives up for 20 ticks and asks again. The legs' own
  "asked in vain" memory lasts only 10 ticks (`AntNavigator.NO_WAY_MEMORY`), so each try is a new full route search.
- **What the player would see:** nothing, unless the cell by the seal cannot be reached (a soldier counted as shut out
  by straight-line distance that stands in another gallery or on the surface, or a broken cover mended after the raid);
  then a failed search about every 1.5 s per such soldier until the seal opens at night (at most `SEAL_MOST`, a day).
- **Where:** `savanna/colony/Soldiers.java:465-470` (`claimPost`: `bySeal` returned before `avoid`);
  `savanna/entity/goal/WalkBeatGoal.java:98-106`, `:260-269` (`giveUp`: `nextTry = now + 20`);
  `savanna/colony/RaidDefence.java:274-283` (`shutOut`: every soldier within `plugToBreak + 0.5` blocks in a straight line).
- **The fix proposed:** `claimPost` skips the seal beat when it is in `avoid`, as it skips every other post.

## S-8 (C, to measure): the alarm walks the whole nest again for each trouble about once a second, and for each player block broken next to the nest

- **What is wrong:** `NestWalls.joinedCells` is a fresh flood through every nest cell with block reads, never kept.
  `Alarm.cellsNear` calls it for each trouble every second, and again whenever a foe's trouble moves (S-5); with up to 8
  troubles, 8 walks a second. `Alarm.isBreakIn` calls it for each block a player breaks beside the nest (mining down by
  a nest: one walk a block).
- **Where:** `savanna/colony/Alarm.java:626-645`, `:286-308`; `framework/colony/NestWalls.java:97-121`.
- **The fix proposed:** one kept copy of the joined cells in `NestWalls` (refreshed when a nest cell is dug or
  filled), read by the alarm, the leaks and the rest. Measure first with a grown nest.

## S-9 (C, to measure; the comment disagrees): a soldier at an alarm task looks for foes round it every tick when there is none

- **What is wrong:** `DefendGoal.tick` scans for entities round the soldier when it has no foe now, or every 4th tick
  when it has one. The common case (no foe within 2 blocks) is every tick for every answering soldier; the field's
  comment says "looked for every few ticks".
- **Where:** `savanna/entity/goal/DefendGoal.java:69-71`, `:125-127`, `:181-200`.
- **The fix proposed:** scan every 4th tick whatever was found (a blind soldier lunging a fifth of a second later does
  not show).

## S-10 (C, proven; Owner's call): termite soldiers never bite a player, but comments and the design notes say they lunge at a player or an animal

- **What is wrong:** the soldiers' foe test is `Fights.isFoe` (the termites list no `foe_types` since the chickens went
  out, and a player is no insect) and their bite test is the `Fights.mayAttack(profile, entity)` form, which leaves out
  the "provoked by a player" question the Matabele use. So against a player who breaks into the mound or hits a
  termite, soldiers come, stand round him in the open for 80 s and drum, and never bite. The comments say otherwise:
  `DefendGoal.java:131` ("At a player or an animal it still lunges"), `Alarm.java:116-118` ("a chicken, a player"),
  the plan's step 5 part 2 item 6.
- **Where:** `savanna/entity/goal/DefendGoal.java:181-200`, `:141`, `:211`; `savanna/entity/goal/WalkBeatGoal.java:224-233`;
  `framework/fight/Fights.java:55-103`; `macrotermes.json` `fight` (no `foe_types`).
- **The design today:** `docs/DESIGN.md:84` ("Players and ants are still not attacked (M8)") and `:727` (footsteps:
  "never an attack"). So the behaviour may be what was chosen; only the comments are wrong.
- **Owner's call:** real *Macrotermes* soldiers bite a hand that breaks the mound hard enough to draw blood. Today they
  never do. My pick: let them bite a player who broke in or hit a member (the same "provoked" question the Matabele
  use, one line), for a small damage (1); or, if you prefer them harmless, fix only the comments.

## S-11 (C, proven): comments that disagree with the code

- `savanna/colony/Alarm.java:52-53`: "a major in the opening itself": the single holder was removed in step 5 part 2
  (every soldier at a covered place is a wall).
- `savanna/colony/Alarm.java:55-56`: "the holes are shut behind them (`keepsShut`)": no `keepsShut` exists; the alarm
  plugs no hole (its own row in `SavannaParts.java:253-255` says so).
- `savanna/colony/Alarm.java:330-331`: "Once a second (called by the queen)": it is the colony part's tick
  (`SavannaParts.java:250`).
- `savanna/colony/Alarm.java:480-481`: "the others feel that in turn": they do not (S-4).
- `savanna/entity/TermiteSoldierEntity.java:65-68`: a soldier holding a place "stands out in the open ... to be
  fought": since step 5 its place is under cover and it is shielded (`Fights.shielded`).
- `savanna/entity/TermiteSoldierEntity.java:154-167`, `:340-341`: damage 3 and 4 in the attributes and the comment;
  the species file's 1 and 1.5 are what counts (`AbstractAntEntity.setFightNumbers`, `:873-888`).
- `savanna/command/TermiteCommands.java:60-62`: the `age` command's comment sits above `mushrooms`; `:74-76` the
  `alarm` comment sits on `drum`.

## S-12 (C, proven): test gaps

- No test that real majors stay real when the minors have many beats, or when a breach reaches the nest (S-1).
- No test of a colony with soldiers and no workers (S-2); no test of two troubles more than 8 blocks apart (S-3).
- No test of a soldier that cannot reach its alarm place (S-6) or its seal beat (S-7): each would pass today however
  many route searches ran. `perf` scenes are the place.
- `footstepsBringTheMinorsWithinEarshotToLookAndDrum` and `breakInsAreBreachesFeltOnlyNearThem` check who comes, not
  that a soldier 17 blocks off along a row of soldiers does not (S-4); if the relay is built they need a case.

## The termite soldier's goal table (`TermiteSoldierEntity.registerGoals`, `TermiteSoldierEntity.java:170-186`)

Lower number wins. A goal can push out only a running goal of a higher number that shares a flag with it (Minecraft's
`GoalSelector`, `canBeReplacedBy`); no goal here overrides `isInterruptable`. None asks to tick every tick, so they tick
every other tick with the rest of the selector. "Asked" is the order of the `addGoal` lines.

| Asked | Goal | Number | Flags | What starts it (`canUse` in short) | What ends it |
|---|---|---|---|---|---|
| 1 | `FloatGoal` | 0 | JUMP | in water (Minecraft's) | out of water |
| 2 | `HoldStillGoal` (drumming) | 1 | MOVE | `drumTicks > 0` (24 ticks on hearing, on arrival at footsteps; the `drum` command up to 60 s) | the drumming ends |
| 3 | `HoldStillGoal` (fed) | 2 | MOVE | hungry, a nurse on her way (`hasFeeder`), and not answering a trouble | the same test turns false (default `canContinueToUse`) |
| 4 | `DefendGoal` | 3 | MOVE, LOOK | every 5 ticks, not drumming: the alarm has a task for it (`Alarm.taskFor`), not past a seal (`RaidDefence.cutOff`), its place not left alone | no task now (trouble over, place unreachable) |
| 5 | `GoInsideGoal` | 4 | MOVE | 1 in 10, may not go out (`mayGoOut`: no alarm task, and a major, or a minor while the minors are in), not inside, not waiting after a failed try (200 ticks) | inside, or 1200 ticks |
| 6 | `WalkBeatGoal` | 5 | MOVE, LOOK | 1 in 10 and past `nextTry`: `Soldiers.claimPost` gives it a beat (a seal beat first) and it is on it or a route there is found | once a second: no longer its beat, or a more important one is free (`holds`); 3 failed routes |
| 7 | `AntWanderGoal` | 6 | MOVE | idle strolls (1 a minute, scaled by restlessness), within where it may be | its stroll ends |
| 8 | `RandomLookAroundGoal` | 7 | LOOK (Minecraft's) | nothing else | Minecraft's |

**What stands outside the table and can take the soldier anyway:** frozen states of the shared body
(`AbstractAntEntity.isImmobile`: paralysed, held in a blow, clamping on, being worked on, carried: no goal is asked or
stopped while they hold, round 3); the alarm's `heard` lists and `places` (the only "orders"; kept on the colony, not
on the soldier); `Soldiers.held` (who walks which beat); `RaidDefence.shutOut` (the seal beat); the population keeper
folding it into the numbers (S-1).

**Verdict:** nothing flip-flops in the table itself. Drumming stops `DefendGoal` on purpose and it starts again after;
`drummedAt` keeps it from drumming twice at one cell. `WalkBeatGoal.stop` keeps the beat held, so a soldier called
away comes back to it. Two rubs, both outside the table: a soldier answering one trouble is deaf to a second (S-3), and
a soldier whose beat is far takes a failed route search a minute (the plan's own note, `WalkBeatGoal` `AVOID_TICKS`).
`GoInsideGoal` and `WalkBeatGoal` cannot pull a minor in turn: `mayGoOut` and `posts` both read `minorsOut`, one judge.

## What is solid

- The alarm's list of troubles cannot be changed while it is walked: every add and remove happens outside the loops
  (`tick`, `trouble`, `foldInto` with its own iterator), and the list is capped at 8.
- Nothing a soldier's goal holds is given back only in `stop` (R5-5's question): the held cell lapses in 10 ticks
  (`HeldCells`), beats are freed when the holder is gone (`pruneHolders`), alarm places are dropped for soldiers no
  longer found (`places.keySet().retainAll`), the nurse's claim has an end time.
- A spectator never sets off the alarm (R5-3's question): `footsteps` skips spectators and the once-a-second scan asks
  for sprinting on the ground.
- Colony dice and layering: no goal here steers with vanilla navigation or `MoveControl`; the beat's next cell is found
  by a short walk through beat cells (no route search that can fail); species code reaches the brain only through its
  parts (`SavannaParts` rows for the alarm and the soldiers).
- Synced flags (`DATA_DRUMMING`, `DATA_BITING`) are set every tick but Minecraft sends only changes; the drumming
  sound is quiet and short (6 taps in 24 ticks, about 10 a second, near the real 10 to 20).
- A soldier coming out of the numbers takes its meal time back (`Population.comeOut` then `setLastMeal`), and its
  health and damage come from the species file on its first tick.
- The soldier count goes down once on death (`TermiteSoldierEntity.die`) and once when carried off as prey
  (`carriedOff`), never twice.
- The commands all need permission 2 (both this root and the framework's), clamp their numbers, and stay near the
  player (64 blocks for a colony, 16 or 32 for the rest).
- The numbers fit together: 200 workers want 15 minors and 5 majors (1 in 10, 3 to 1, as the design); a real raid of
  20 to 50 meets at most 12 real soldiers; minors' outside posts and the trail lines are pure geometry with a unit test
  (`SentryLine`).

## Read

Read in full: `savanna/colony/Alarm.java` (1-821), `savanna/colony/Soldiers.java` (1-598), `savanna/colony/SentryLine.java`
(1-63), `savanna/entity/TermiteSoldierEntity.java` (1-494), `savanna/entity/goal/DefendGoal.java` (1-289),
`savanna/entity/goal/WalkBeatGoal.java` (1-276), `savanna/entity/goal/RetreatGoal.java` (1-101),
`savanna/command/TermiteCommands.java` (1-319). Read in part: `savanna/colony/RaidDefence.java` (60-399),
`savanna/colony/SavannaParts.java` (170-270), `savanna/species/SavannaNumbers.java` (236-296), `macrotermes.json`
(the colony, soldiers, alarm and fight sections), `framework/fight/Fights.java` (55-212),
`framework/colony/Population.java` (375-593), `framework/colony/Meals.java` (20-206), `framework/ModEvents.java`
(85-135), `framework/entity/AbstractAntEntity.java` (560-574, 700-725, 860-888, 1460-1503, 1880-1949),
`framework/entity/goal/GoInsideGoal.java` (all), `framework/entity/goal/Errand.java` (the walking calls),
`framework/nav/AntNavigator.java` (469-495, 897-920). The game tests in `TermiteAlarmTests`, `TermiteSoldierTests` and
`TermiteRaidDefenceTests`: their names and scenes only, not every line. `RetreatGoal` (workers) has no finding: it
walks through the shared `Errand` with a give-up time.
