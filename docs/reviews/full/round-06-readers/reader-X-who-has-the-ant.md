# Round 6, reader X: "who has the ant?" (the cross reader)

Read at commit `b72cba68`, worktree `review-round-6`. No code changed, nothing run, nothing measured. Builds on
round 3's thread "a goal's life from picked to dropped" (R3-30 to R3-36) and does not repeat it. Minecraft's own
`GoalSelector` and `Mob.serverAiStep` were not opened in this session: how they ask the goals is taken from round 3's
section "How Minecraft asks the goals" (which read them in the decompiled game) and agrees with what I know of 1.21.1.

## The Matabele worker's goal table (`MatabeleEntity.registerGoals`, `MatabeleEntity.java:194-227`)

Lower number wins. A goal can push out only a running goal of a **higher** number that shares a flag with it; goals
of the same number never push each other out. "Every tick" means `requiresUpdateEveryTick`; the others tick 10 times
a second. No goal overrides `isInterruptable`, so every one can be pushed out. The column "Asked" is the order the
game asks `canUse` in one pass: the order of the `addGoal` lines, not the priority.

| Asked | Goal | Number | Flags | Every tick | What starts it (its `canUse` in short) |
|---|---|---|---|---|---|
| 1 | `FloatGoal` | 0 | JUMP | yes (Minecraft's own, from memory) | in water |
| 2 | `MatabeleBiteOutGoal` | -1 | MOVE, LOOK | yes | out of its own nest, under a roof, shut in, two looks 10 ticks apart |
| 3 | `FightGoal` | 0 | MOVE, LOOK | no | a fight order (`getFightSpot`), or bitten by something within 8 blocks; never while `fightsNoMore` |
| 4 | `EatGoal` | 1 | MOVE, LOOK | no | hungry, jaws empty, not on a raid errand, 1 chance in 10 |
| 5 | `FeedQueenGoal` | 2 | MOVE, LOOK | no | holds nurse food (always), or looks for work and claims the queen's feeding |
| 6 | `FeedLarvaGoal` | 2 | MOVE, LOOK | no | jaws empty, looks for work, a hungry young one claimed |
| 7 | `CarryEggGoal` | 2 | MOVE, LOOK | no | holds an egg (always), or looks for work and a loose egg |
| 8 | `CarryRemainsGoal` | 2 | MOVE, LOOK | no | holds remains (always), or looks for work and claims a load |
| 9 | `ClampCareGoal` | 2 | MOVE, LOOK | no | in the nest, every 40 ticks, 1 in 3, a clamped nestmate at rest, claimed |
| 10 | `RescueGoal` | 2 | MOVE, LOOK | no | holds a hurt nestmate (always), or every 20 ticks a caller within 6 blocks |
| 11 | `WoundCareGoal` | 2 | MOVE, LOOK | no | in the nest, every 40 ticks, 1 in 2, a wounded nestmate at rest, claimed |
| 12 | `MatabeleScoutGoal` | 1 | MOVE, LOOK | no | once a second: `Scouts.mayScout` and a planned trip, or a pushed-aside trip to take up again |
| 13 | `MatabeleRaidGoal` | 1 | MOVE, LOOK | yes | a member of the colony's raid, no fight order, not `fightsNoMore` |
| 14 | `MatabeleWayHomeGoal` | 1 | MOVE, LOOK | yes | on the colony's way-home list, no fight order, and hurt or not a raider |
| 15 | `BringPreyGoal` | 2 | MOVE, LOOK | no | holds stung termites, not in a column |
| 16 | `DigGoal` | 3 | MOVE, LOOK | no | looks for work and a block to dig (asks only "no soil in the jaws") |
| 17 | `GoInsideGoal` | 4 | MOVE | no | may not go out, not in a room, 1 in 10 |
| 18 | `WaitAtNestGoal` | 5 | MOVE | no | may not go out, outside and more than 5 from the entrance, 1 in 10 |
| 19 | `NapGoal` | 6 | MOVE, LOOK | yes | idle, by its dice |
| 20 | `GroomGoal` | 6 | MOVE, LOOK | yes | idle, by its dice |
| 21 | `AllogroomGoal` | 6 | MOVE, LOOK | yes | idle, a nestmate beside it |
| 22 | `AntWanderGoal` | 7 | MOVE | no | nothing else (rests first) |
| 23 | `RandomLookAroundGoal` | 8 | MOVE, LOOK (Minecraft's) | no | nothing else |

**The queen** (`MatabeleQueenEntity.java:76-88`): 0 `FloatGoal` (JUMP); 1 `FightGoal` with a fight-back reach of 3
(MOVE, LOOK; only `NestDefence.guardTheQueen` gives her an order, 40 ticks at a time); 2 `QueenToRoomGoal` (MOVE);
3 `GoInsideGoal` (MOVE); 4 `WaitAtNestGoal` (MOVE); 5 `NapGoal`, `GroomGoal` (MOVE, LOOK, every tick); 6
`AntWanderGoal` (MOVE, only while no room is finished); 7 `RandomLookAroundGoal`. No goal of hers carries, cares or
is drafted; nothing in her list can fight another.

**What stands outside the table and can take the ant anyway:**

1. **Frozen (`isImmobile`, `AbstractAntEntity.java:1501-1505`):** paralysed, held in a blow, clamping on, a clamp
   being worked on (`clamp.isHeldStill`), **calling for help**, **tended**, **carried** (riding a nestmate). While
   any holds, no goal is asked, ticked or stopped: every goal freezes as it is, with no `stop()` (round 3).
2. **The fight order** (`orderToFight`, `:1917-1935`): a spot, an end time and who gave it (`fightOrderBy`). Given by
   the raid (`Raid.java:1839`), the nest's defence (`NestDefence.java:203`, `:266`, `:353`) and the skirmish
   command. This is the only real "owner slot" the ant has, and it is for fights only.
3. **Three lists in `Raids`,** kept by the ant's id: scouts out (`Scouts`), the raid's members (`Raid`), ants going
   home (`WayHome`). The three priority-1 goals only read them.
4. **What is in the jaws** (`hasFullMandibles`): the unwritten rule that holds most of this together. Every drafter
   but the nest's defence leaves an ant with full jaws alone, and every carrying goal starts again by itself when it
   finds its load still in the jaws.
5. **Care claims on the patient** (`claimCare`, 40 ticks, renewed), **the call** (`callingUntil`), **the tender**
   (`tendedBy`, 20 to 40 ticks): all on the hurt ant, all with their own end time.
6. **Going into the numbers** (`Population.goIn`, `AwayLife.foldLoaded`, `foldInStray`).

## Findings

### X-1 (B, proven): the bite-out goal's "a foe right beside it: the fight first" never fires, so a shut-in ant with a termite soldier on it bites the wall instead of fighting

- **What is wrong:** `MatabeleBiteOutGoal` sits at -1, above the fight, on purpose (BUGS 102), and its header says it
  "stands aside while a foe is right by it (a termite in the pocket with it: the fight first)". That exception is
  `foeNear()`, which asks `ant.getTarget()`. Nothing in the insect code ever calls `setTarget`: `FightGoal` keeps
  its foe in its own field. So `foeNear()` is always false, and once the bite-out goal has the ant, nothing can take
  it back: not the fight, not the raid, not the scout trip.
- **Where:** `savanna/.../entity/goal/MatabeleBiteOutGoal.java:37-39` (the promise), `:81` and `:86` (the two uses),
  `:204-207` (`foeNear`); `framework/.../entity/goal/FightGoal.java:100`, `:140`, `:205` (the foe is a field of the
  goal); a search of both mods for `setTarget(` finds only animals (`GuardPlaceGoal`, the crocodile).
- **The proof:** `canUse` (`:73-82`): two looks 10 ticks apart find it `trapped` (out of its nest, a roof within 3,
  `Fights.isShutIn`), then `!foeNear()` is `!false`. It starts and pushes out `FightGoal` (0 is a higher number than
  -1), whose `stop` forgets its foe (`FightGoal.java:157-167`). `canContinueToUse` (`:85-93`) ends only on downed,
  `foeNear()` (never), the let-go time, or no longer trapped. `FightGoal.canUse` goes on being asked but cannot push
  out a goal of a lower number. So for as long as the ant is shut in it only bites walls.
- **What the player would see:** mostly nothing directly, because it happens in a sealed pocket: the termites seal a
  broken gallery behind the minors that went in (the raid seals), a soldier in the pocket bites, and the ants in
  there turn from the termites to the wall a second after the seal goes in. From outside: fewer termites stung than
  went in for, and raiders coming out with legs gone or not at all. Likely one part of BUGS 361's raiders lost in the
  termites' tunnels (not checked against that replay).
- **Not checked:** how often a soldier is in the pocket in a real raid (not run).
- **The same dead check elsewhere:** `AbstractAntEntity.mayGoIntoNest` (`:560-562`) asks `getTarget() == null` for
  "not after an enemy"; round 3 already wrote that down under R3-38. This is the Matabele goal's side of it.
- **The fix proposed:** one small method on the body, "the insect it is fighting or was just bitten by, if within x
  blocks" (`getLastHurtByMob` and the fight goal's foe), asked by `foeNear()`; and delete the `getTarget()` check in
  `mayGoIntoNest` or point it at the same method. No new rule: it makes the written one true.

### X-2 (B, the mechanism proven; how often in play not known): a limping ant stops to call for helpers who can never come

- **What is wrong:** two places judge "is a nestmate free to help me". The hurt ant asks `helpWithin`: any nestmate
  within 6 blocks for whom `RescueGoal.mayHelp` and `answersHelpCalls` are true. The helper's side is the goal
  table: `RescueGoal` is number 2, so it can only start in an ant that runs nothing of number 1 or 2.
  `answersHelpCalls` says no only for an ant in the raid column (`MatabeleEntity.java:970-976`). So an ant walking
  home by itself (`MatabeleWayHomeGoal`, 1), a scout (`MatabeleScoutGoal`, 1), an ant on its way to a meal
  (`EatGoal`, 1) and an ant walking to a chore with empty jaws (number 2, same number) all count as "help is near",
  and none of them can ever start `RescueGoal`: for the first three the game does not even ask its `canUse`.
- **Where:** `framework/.../entity/AbstractAntEntity.java:1287-1291` (the call: every 100 ticks, 200 ticks long, then
  not again for 600), `:1295-1304` (`helpWithin`), `:1501-1505` (calling freezes the ant);
  `framework/.../entity/goal/RescueGoal.java:53-57` (`mayHelp`), `:73-86` (`canUse` does not ask
  `answersHelpCalls`); `MatabeleEntity.java:208` and `:213-215` (the numbers).
- **The proof, the commonest case:** a raid is given up or a stage's deadline lets several raiders go
  (`Raid.abandon`, `Raid.java:1515-1524`; `letGo`, `:1320-1324`). Hurt and whole ants go onto the same way-home list
  and walk the same trail. For a whole one: `mayHelp` is true (alive, not `fightsNoMore`, no fight order, jaws
  empty) and it is in no column, so `answersHelpCalls` is true. The hurt one beside it passes line 1287, calls and
  stands frozen 200 ticks. The whole one runs `MatabeleWayHomeGoal`, which holds MOVE and LOOK at number 1, and
  walks on past it. 600 ticks later the hurt one calls again if any such ant is within 6 blocks.
- **What the player would see:** after a raid that broke up, a limping ant on the trail home stops and squeaks for
  10 seconds while its nestmates walk past it, then limps on, and does it again 30 seconds later. With X-6 it stands
  15 seconds of every 40.
- **Real biology:** real Megaponera returning from a raid do pick up a calling nestmate (Frank et al. 2017), so the
  call is right and the passing by is the fault.
- **The fix proposed (fewer rules):** make the two judges one. Either `helpWithin` asks what the table will allow
  (one method "would start a rescue now": `mayHelp` and no goal of number 1 or 2 running, the test
  `isBusyWithChore` already half makes), or better for the biology: an ant going home by itself answers a call (in
  `MatabeleWayHomeGoal.canContinueToUse`, stand aside while a caller it may help is within reach, so `RescueGoal`
  can start; the way-home list keeps it and takes it up again with the hurt one in its jaws, which already works:
  see case 3 below).
- **Owner's call:** should an ant walking home by itself after a raid stop and carry a calling nestmate? Today it
  walks past and the hurt one calls for nothing. My pick: yes (the real ants do).

### X-3 (B, proven for who is called; likely for how it plays out): the nest's defence calls ants out without asking what they hold or who holds them

- **What is wrong:** every other drafter leaves an ant with full jaws alone (`Raids.raiderFit`, `Scouts.fit`,
  `RescueGoal.mayHelp`, `ClampCareGoal.free`). `NestDefence.mayDefend` asks only: a worker, not `fightsNoMore`, not
  downed, has fight numbers, not kept by the raids. Its own comment says "the hurt stay in". They do not:
  `fightsNoMore` is 3 or more legs gone, a living clamp, or badly wounded; an ant with one or two legs gone and an
  open wound passes.
- **Where:** `framework/.../fight/NestDefence.java:211-214` (`mayDefend`), `:155-162` (those near the trouble),
  `:181-197` (`wave`, nearest the entrance first), `:199-204` (`sendOut`), `:250-268` (the order renewed every
  second); `MatabeleEntity.java:959-964` (the hurt rest in the room nearest the entrance); `RescueGoal.java:105-112`
  (`stop` keeps the carried ant unless the carry timed out).
- **Three things follow, each walked:**
  1. **A rescuer fights with its patient in its jaws.** It carries a hurt nestmate (`RescueGoal`, 2). `sendOut`
     gives it a fight order. `FightGoal.canUse` (`:136-142`) is true, pushes `RescueGoal` out; its `stop` does not
     set the nestmate down. The rescuer runs at the player and stings with the hurt ant riding in its jaws. When the
     order ends `RescueGoal.canUse` (`:77-79`) is true again and it carries on. Nothing is lost; it looks wrong.
  2. **A carried ant is counted as a defender.** A passenger with one or two legs gone passes `mayDefend`, takes
     one of the `most` places and gets its order renewed every second, while it is frozen and can do nothing. Set
     down in the hurt room it has a live fight order and walks out to fight, wound open.
  3. **The wounded go out first.** `wave` takes the nearest the entrance, and the hurt room is the room nearest the
     entrance, so the first wave is the patients and the ants licking them (the care goals are pushed out; their
     claims end by themselves).
- **Likely, not run:** a wounded defender outside calls for help (X-2's call does not ask about a fight order),
  an idle ant in the nest carries it in, its order is still renewed, it walks out again, and so on while the player
  keeps provoking.
- **What the player would see:** break into a nest after a raid came home with hurt: ants with legs missing lead
  the charge, and now and then one stings with another ant in its jaws.
- **The fix proposed:** `mayDefend` also asks: jaws empty, not carried, not carrying, wound not open (or one shared
  "free to be called" test, see X-5).
- **Owner's call:** should an ant with a fresh wound defend its nest? Today it does, first of all. My pick: no for
  the wounded and for anyone carrying or carried (the design's "the hurt rest until the wound closes"); real
  Megaponera with a leg gone do fight again, but after the wound has closed.

### X-4 (B, proven): "busy with a chore" leaves out wound care and rescue, so the ants tending the wounded are the first taken for a raid

- **What is wrong:** `MatabeleEntity.isBusyWithChore` is a hand-kept list of goal classes: `FeedQueenGoal`,
  `FeedLarvaGoal`, `CarryEggGoal`, `CarryRemainsGoal`, `ClampCareGoal`, `BringPreyGoal`. Its comment ends "...waste
  or prey, care". `WoundCareGoal` and `RescueGoal` (an ant on its way to a caller, jaws still empty) are not in it.
  The raid sorts the fit ants by "not busy first, then nearest the entrance"; the scouts refuse a busy ant.
- **Where:** `savanna/.../entity/MatabeleEntity.java:898-902`; `savanna/.../colony/Raids.java:991-996` (the sort),
  `:1039-1043` (`raiderFit`: jaws empty, so a rescuer already carrying is safe); `savanna/.../colony/Scouts.java:274`
  and `:710`.
- **The proof:** an ant licking a wound runs `WoundCareGoal`, jaws empty, no wound of its own: `raiderFit` true,
  `isBusyWithChore` false. It stands in the hurt room, which is the room nearest the entrance
  (`MatabeleEntity.java:959-964`), so it sorts to the front of the list and is among the first `real` taken
  (`Raids.java:1019-1023`). `MatabeleRaidGoal` (1) pushes `WoundCareGoal` (2) out; `stop` (`WoundCareGoal.java:82-89`)
  lets the patient go and the lick's progress is lost (up to 300 goal ticks, half a minute). The same for a major
  mid-lick and `Scouts.mayScout`.
- **What the player would see:** when a raid musters, the ants standing over the wounded are the first to walk off.
  A raid usually takes most of the real ants anyway, so it shows only with a small raid.
- **The fix proposed:** do not add two more class names. Ask the ant instead of listing its goals: "runs a goal of
  number 2 or lower" is the same question for every goal there is and will be (the table already says which goals
  are work), or the owner slot of X-5.

### X-5 (B, proven): "is this ant fit and free" is judged in nine places by nine different rules

- **Where, and what each asks:**

| Who asks | Where | Legs gone | Clamp | Badly wounded | Wound open | Needs rescue | Jaws | Fight order | Carried or carrying |
|---|---|---|---|---|---|---|---|---|---|
| `fightsNoMore` (the fight, the raid goal) | `AbstractAntEntity.java:965-967` | 3 or more | a living one | yes | no | no | no | no | no |
| `Raids.raiderFit` | `Raids.java:1039-1043` | 3 or more | any | yes | yes | yes | empty | none | by the jaws only |
| `Scouts.fit` (who may set out) | `Scouts.java:314-317` | 3 or more | any | **no** | **no** | **no** | empty | none | by the jaws only |
| `MatabeleScoutGoal.fitToGoOn` | `MatabeleScoutGoal.java:331-333` | 3 or more | any | **no** | **no** | **no** | no | none | no |
| `RescueGoal.mayHelp` (and wound care) | `RescueGoal.java:53-57` | 3 or more | any | yes | no (wound care adds "wants care") | yes | empty | none | yes |
| `ClampCareGoal.free` | `ClampCareGoal.java:66-69` | 3 or more | any | yes | no | no | empty | none | **no** |
| `NestDefence.mayDefend` | `NestDefence.java:211-214` | 3 or more | a living one | yes | **no** | **no** | **no** | no | **no** |
| `Raids.sweep` (sent home from outside) | `Raids.java:520-527` | no | no | no | no | no | empty | none | by the jaws only |
| `mayGoIntoNest`, `AwayLife.frozenOf` (into the numbers) | `AbstractAntEntity.java:560-562`, `AwayLife.java:274` | no | any (the first only) | no | no | no | empty (the first only) | no | carried (the first only) |

- **What is wrong:** each was written for its own step and each is right for that step, but together they disagree,
  and a new state (the wound, the carried ant) was added to some and not to others. What comes of it today:
  - a major with a fresh open wound may not raid ("the hurt rest until the wound closes", `Raids.java:1042`) but may
    be sent scouting, alone, for up to 4 minutes (`trip_ticks` 4800), in the 5 minutes (`infect_ticks` 6000) in
    which a lick decides whether the wound turns bad (80 in 100 unlicked against 20 in 100 licked). It leaves
    between two licks (the patient is only frozen while the carer works);
  - a scout hurt down to 40 percent of its health scouts on; the same ant in a raid is sent home;
  - X-3 (the defence) and X-4 (the chore list) are this same finding seen from two more sides;
  - `ClampCareGoal.free` does not ask "carried": harmless today, since a carried ant is frozen.
- **What the player would see:** a major with a leg missing leaving the hurt room to go scouting while its
  nestmates were licking its wound; later it dies of the infection the care was there to stop.
- **The fix proposed:** one method on the ant, "what state am I in" (whole; hurt and resting; cannot fight; carried;
  carrying), and one "may be called away now", asked by all nine. See the verdict at the end.
- **Owner's call:** may a major with an open wound go scouting? Today yes (and never raiding). My pick: no, the same
  rule as for raids, until the wound has closed or at least been licked.

### X-6 (C, proven): after each help call a limping ant stands 5 seconds more, because its "am I stuck" watch counts the call as being stuck

- **Where:** `savanna/.../entity/goal/Headway.java:27-39`; `MatabeleWayHomeGoal.java:102-107`;
  `AbstractAntEntity.java:1287-1291`, `:1501-1505`.
- **The proof:** an ant limping home is walking (`Headway` holds its place and tick). It calls for help: frozen for
  200 ticks, its goal not ticked, its route kept (nobody stops the legs). On the first tick after the call
  `headway.lost` finds: still walking, not held by the crowd (`AntNavigator.heldForCrowd`, `:516-519`, looks back 10
  ticks at most), moved less than half a block, 200 ticks since the last headway, more than `NO_PROGRESS` 160. It
  says "lost": the walk is stopped, the ant waits `RETRY` 100 ticks, and one "no way" is counted toward leaving its
  trail (`fails`, set back by the next walk that is found). The same after a nestmate pulled a clamp off it on the
  field (it was held still for `field_pull_ticks`).
- **What the player would see:** the hurt ant stands 15 seconds, not 10, each time it calls.
- **The fix proposed:** `Headway` counts ticks the goal really ticked (add one per call of `lost`) instead of
  reading `tickCount`. No new rule.

### X-7 (C, proven; rare): a rescuer that is hurt itself keeps its patient, and can be picked up with it: three ants in a stack

- **Where:** `RescueGoal.java:89-92` (while carrying it asks only "still carrying" and the time);
  `AbstractAntEntity.java:1221-1232` (`pickUpHurt` asks whether the rescuer is a vehicle, not whether the hurt one
  is), `:1123-1130` (`needsRescue` is false for a passenger, not for a vehicle).
- **The proof:** a carrier at the feeding spot loses a leg to a soldier. `needsRescue` is true for it; the raid
  drops it and has it call (`Raid.java:1437-1446`), or off a raid it calls by itself (line 1287). It stands frozen
  with its patient in its jaws. A nestmate sent for it reaches `hurt.isCarriable() && ant.pickUpHurt(hurt)`
  (`Raid.java:2060`, `RescueGoal.java:149`): `hurt.startRiding(this, true)` succeeds, the middle ant still holding
  HURT and its own rider. At home the outer one sets the middle one down, whose `RescueGoal.canUse` (carrying) is
  true, and it carries its own patient to the hurt room. It ends well; it looks like a tower.
- **The fix proposed:** an ant that comes to need rescue sets down what it carries (one line where the call
  starts), and `pickUpHurt` refuses a hurt ant that is a vehicle.

### X-8 (C): small things on this thread

- **The list is not in priority order** (`MatabeleEntity.java:202-216`): the three raid goals (1) are added after
  the seven chores (2), so in the pass in which an ant is drafted a chore can start (claiming the queen's feeding,
  an egg, a load, a patient, and planning a route in `canUse`) and be stopped by the raid goal a few lines later in
  the same pass. Harmless (every claim is given back in `stop` or runs out), one wasted route search. Round 3's
  R3-30 is the general fault; here the cure is only to add the goals in the order of their numbers.
- **`RescueGoal.start` sets `walking` to 0** (`:98-103`): each time a carry is pushed aside (a fight, the bite-out)
  and taken up again, the 2400-goal-tick limit that ends in "set it down where it is" starts over. A carrier that
  keeps being interrupted has no limit. Not seen as a fault; noted for whoever touches BUGS 175's rule.
- **The sweep lists the hurt waiting at the spot as "outside with nothing to do"** (`Raids.java:518-532`): the raid
  dropped it from its members and keeps it calling (`Raid.java:1441`, `:2101`), so it is no scout, no raider, not
  going home, jaws empty, and within 5 seconds the sweep puts it on the way-home list and writes a diary line that
  reads like a stuck ant. It stays frozen by its call, so nothing moves; when the raid lets it go it is on the list
  already. A wrong diary line only.
- **`EatGoal` can call a helper off its walk to a caller** (1 against 2, jaws empty, 1 in 10 a pass when hungry):
  the caller waits for the next one. Fine as a rule (R3-35 is the missing claim); written so nobody is surprised.

## The nine cases, each walked to its end

**1. A rescuer carrying a hurt nestmate, and a foe comes.** `FightGoal.canUse` (`FightGoal.java:136-142`) is true
only with a fight order or after a bite from something within 8 blocks (`pickFoe`, `:337-351`); a foe merely near does
nothing. Then Fight (0) pushes Rescue (2) out. `RescueGoal.stop` (`:105-112`) stops the legs and sets the nestmate
down only if the carry had timed out, so the hurt ant stays in the jaws through the fight (a passenger is frozen,
`AbstractAntEntity.java:1502`, and `needsRescue` is false for it, so it neither calls nor fights). After the fight
`RescueGoal.canUse` (`:77-79`) is true at once, `start` picks a room again, and the carry goes on. If the rescuer dies,
`dropLoadOnDeath` runs the HURT row's drop (`FrameworkLoads.java:62-63`, `ejectPassengers`) and `tickRescue`
(`:1281-1283`) gets the rider off; the hurt one then walks in by itself (`GoInsideGoal`) or, more than 16 blocks out,
is found by the raids' sweep within 5 seconds and limps home.
**Nothing is lost.** What looks wrong is fighting with a nestmate in the jaws (X-3, point 1), and a rescuer hurt in
that fight keeps its patient (X-7).

**2. An ant licking a wound, and it gets hungry, is drafted, or the nest is attacked.** Hungry: `EatGoal` (1) starts
1 pass in 10 (`EatGoal.java:62-63`), `WoundCareGoal.stop` (`:82-89`) lets the patient go at once
(`releaseCare`), the lick's progress is lost, another carer may claim the patient on its next look. Drafted: the
carer is "not busy" to the raid and the scouts (X-4), the raid goal (1) pushes the care out the same way. Attacked:
`NestDefence` gives it a fight order (X-3), Fight (0) pushes it out. In all three the patient is free again within 20
ticks (`tendedBy` runs out, `:116`) and the claim is given back or runs out in 40. **Nothing is left held.** The
patient may itself be called out to fight (X-3, point 3) or leave to scout (X-5).

**3. A hurt ant limping home, picked up by a rescuer: two things moving one body?** No. The call freezes it
(`isCallingForHelp` in `isImmobile`): its `MatabeleWayHomeGoal` is neither ticked nor stopped. `pickUpHurt`
(`:1221-1232`) stops its legs, ends its call and seats it; a rider is frozen too, so its way-home goal stays frozen
the whole carry and the game places the rider (`positionRider`, `:1262-1274`). It stays on the colony's way-home list.
Set down in the nest (`setDownHurt`, `:1235-1249`: off the rescuer, moved in front, legs stopped), its goal ticks
again, `wayHomeOrder` finds it home and takes it off the list (`WayHome.java:131-134`), and the goal ends on the next
pass. Set down outside (the carry timed out) it calls no more for this wound (`holdHelpCalls`, BUGS 175) and limps on
by the same goal. **Sound.** The two faults beside it are who it calls for (X-2) and the 5 seconds it loses after a
call (X-6).

**4. An ant carrying prey meets a hurt nestmate calling.** Off a raid: `BringPreyGoal` and `RescueGoal` are both 2,
so neither pushes the other out, and `mayHelp` (`RescueGoal.java:53-57`) is false with anything in the jaws. The prey
goes to the pile; the caller waits for an ant with empty jaws. In the raid's regroup (`Raid.java:1958-1979`) a raider
with empty jaws is given a hurt nestmate before any termite, and one that already holds termites is given neither a
hurt one nor (once full) more prey; an ant carrying a hurt one has "full jaws" and gets no termite (`hasRoom`,
`:1929-1932`). **The prey is never dropped for a nestmate, and never lost by this meeting.** (Hunger cannot take a
carrier either: `EatGoal` asks for empty jaws, `EatGoal.java:62`.)

**5. A raider drafted while it holds food for the queen or an egg.** It is not drafted: `Raids.raiderFit`
(`Raids.java:1039-1043`) and `Scouts.fit` (`Scouts.java:314-317`) both ask for empty jaws. An ant only on its way to
the food, the egg or the remains (jaws still empty) can be drafted; it is sorted behind the idle ones
(`isBusyWithChore`), and when the raid goal pushes its chore out, `stop` gives the queen's feeding and the egg back
(`FeedQueenGoal.java:100-107`, `CarryEggGoal.java:102-113`), and the claim on a load of remains runs out by itself
(`PreyPile.java:262-268`). An ant in the middle of a meal has food in its jaws and is left alone too. **Sound.**

**6. The eight goals of number 2: who gets an idle ant, can one starve the others, can two alternate.** By the list's
order: `FeedQueenGoal`, `FeedLarvaGoal`, `CarryEggGoal`, `CarryRemainsGoal`, then clamp care, rescue, wound care, last
`BringPreyGoal`. Holding a load decides first: every carrying goal's `canUse` is true whenever its own load is in the
jaws, and every other goal of number 2 asks for empty jaws, so a carrier is always taken back by its own goal (prey
included, though it is last in the list). For an ant with empty jaws none can starve the others: the four chores each
need a claim that only one ant, or one ant per item, can hold (the queen's feeder, a hungry young one, a loose egg, a
load waiting), so they are false for nearly every idle ant nearly always; the care goals look on their own clock
(every 2 seconds, 1 in 3 and 1 in 2; rescue every second with no dice). In the 2 seconds before an ant counts as idle
`looksForWork` (`AbstractAntEntity.java:597-599`) makes the chores ask on every pass, so a chore that is waiting wins
over care in that window; with more than a handful of idle ants (the usual state between raids) care still starts
within seconds. Round 3's statue (R3-31, food held and no queen) holds for a Matabele colony too, not written
again here: the colony lives on without its queen until it is empty (`Colony.queenDied`, `Colony.java:675-678`),
whatever the queen's own header comment says ("her colony ends when she dies", `MatabeleQueenEntity.java`); such a
statue is also never drafted (full jaws), so it is one of the number 2 goals holding an ant for good. Two goals of the same number cannot alternate
while one runs; one after the other they can (a lick, then a chore, then a lick), which is as meant: clamp care and
wound care on one patient have a claim each (BUGS 349). **Sound**, apart from the list not being in priority order
(X-8).

**7. A hurt scout or raider: who decides whether it goes on?** Too many, see X-5. For a raider: `Raid.observe`
(`Raid.java:1437-1482`, every 5 ticks) drops one that `needsRescue` at the spot (it calls) and lets go one that
`fightsNoMore` (it limps home); `MatabeleRaidGoal` (`:98`, `:107`) and `MatabeleWayHomeGoal.goesHome` (`:60-63`)
judge `fightsNoMore` themselves on every pass. The two agree in the end; between the goal's "no more" and the raid's
"let go" there are up to 5 ticks in which the ant belongs to nobody, and nothing lower can do harm in them (it may
not eat while a raider, `mayHelp` is false for it). For a scout: only the goal's own `fitToGoOn`
(`MatabeleScoutGoal.java:331-333`): crippled, clamped, a fight order or downed end the trip; badly wounded or an open
wound do not (X-5). A scout bitten fights back (Fight 0), its trip is kept for 30 seconds and taken up again
(BUGS 185, `:216-226`, `:336-346`). **One judge too many for raiders, harmless; one rule too few for scouts.**

**8. Night, and the colony going away, in the middle of each.** Night does nothing to the Matabele's goals: the
species file has `forage_at: []`, so a worker "may not go out" at any hour and is outside only on a raid errand
(`MatabeleEntity.mayGoOut`, `:877-883`); care, rescue and the chores never ask the clock. Dusk and rain are the raid's
and the scouts' own (round 11). Going away (no player within 64 blocks for the grace minute): `AwayLife.foldLoaded`
(`:293-322`) turns every living member that is not felled into a number: the rescuer and the ant it carries (both
folded, the hurt one comes back whole, R3-38), the carrier of prey or remains (its load is not written down, R1-28),
the carer (its claim just ends), the raid (round 11). Near a player the swap into numbers (`Population.goIn`) does
not come up for Matabele in ordinary play: the species file wants 75 real workers (`real_cap` and `crew` 75) whether
watched or not, and a colony of at most 100 puts out at most 75, so there are never more real ants than wanted.
**Nothing new here beyond R1-28 and R3-38.**

**9. `MatabeleBiteOutGoal` at -1.** It can push out every goal that holds MOVE or LOOK, which is all but `FloatGoal`
(JUMP only, so a swimming ant still floats). So it interrupts the fight, the raid walk, a scout's trip (kept and
taken up again), the walk home, a carry of a nestmate or of prey (both stay in the jaws and are taken up again). It
cannot start and stop every tick: starting needs two looks 10 ticks apart (`:74-82`), once running it ends only when
a look finds it out, when it is downed, or when it found nothing to bite, and then it stays off for 600 ticks
(`letGoUntil`, `:127-131`). The one fault is that the fight can never take the ant back from it (X-1).

## Is the table sound, patchy, or a candidate for a rewrite?

**Patchy, not a rewrite candidate.** The priority table itself is small and readable (five levels that matter:
escape, fight, raid errands and eating, work, rest), no two goals that steer the body are without a shared flag, and
nothing was found that flip-flops every tick. It holds together through three plain habits that are worth keeping:
a load in the jaws starts its own goal again; every claim has its own end time; a frozen ant keeps everything as it
is. The patchiness is all in one place: **who may take an ant away from what it is doing** is answered separately by
each taker (the raid, the scouts, the nest's defence, the rescue, the two care goals, the sweep, the help call, the
numbers), nine tests in all (X-5), kept in step by hand, plus a hand-kept list of goal classes (X-4) and four small
yes/no hooks on the ant (`isOnColonyErrand`, `answersHelpCalls`, `isInAColumn`, `KEEP_FROM_DEFENCE`). Each new state
(the wound, the carried ant) was added to some and not to the others. This is the raids' lesson in a milder form: not
rules pulling against each other every tick, but judges that quietly disagree.

**The one rule that would replace the most special cases: one owner slot per ant, and one question.**

- The ant says, in one method, **what has it** now: nothing; a chore (a goal of number 2 runs); carrying (and what);
  carried; a patient at rest; a raid; a scout trip; going home; a fight order (it already has this one, with who gave
  it). Most of it is read from what is already there (the load, the vehicle, the running goal's number, the three
  lists, the fight order), so it is a view, not new saved state.
- And **what it is fit for**: whole; hurt and resting (wound open or needs rescue); cannot fight (`fightsNoMore`).
- Every taker asks the same `mayBeCalledAway(for what)` built on those two, with a short order of who outranks whom
  (escape, defence, raid, scout, rescue, care, chores), in place of `raiderFit`, `fit`, `fitToGoOn`, `mayHelp`,
  `free`, `mayDefend`, the sweep's test, `helpWithin`'s test, `mayGoIntoNest`, `isBusyWithChore`,
  `answersHelpCalls` and `keptFromDefence`.

That fixes X-2, X-3, X-4 and X-5 by removing tests, and closes R3-38's hole (a hurt, carried or fighting ant is "not
free", so it is not folded) with the same question. It is a tidy-up of a few dozen lines across about ten files, with
the existing rescue, care and raid tests as its guard; it changes play only where the owner's calls above say so.

## Solid in this slice (do not "fix")

- **A load always finds its goal again.** Prey, remains, an egg, nurse food and a hurt nestmate each start their own
  goal the moment the jaws hold them and nothing higher runs, after a fight, the bite-out, a reload, or being let go
  by the raid (`BringPreyGoal.java:57`, `CarryRemainsGoal.java:59`, `RescueGoal.java:77-79`). No goal of number 2
  takes another's load.
- **Prey is never dropped on the floor by a change of hands.** Pushed out, `BringPreyGoal.stop` (`:88-95`) keeps the
  termites unless its own time is up, and then they are laid on the pile out of sight; a carrier that dies drops them
  as stung termites (`MatabeleLoads.dropTermites`); no way to the pile ends in three tries or a walk home first
  (`:120-132`), never a loop.
- **A carried ant is moved by one thing only** (case 3), and a carry lives through the carrier's fight, the carrier's
  death and being set down outside the nest.
- **The raid and the rescue do not fight over the hurt.** Raiders in a column do not count as helpers
  (`answersHelpCalls`), so a hurt ant on the march home does not stop to call at the column; at the spot the raid ends
  the hurt one's fight order before it has it call (`Raid.java:1438-1440`) and sends an ant with empty jaws for it
  before any prey.
- **Eating stands aside for the raid, the scout trip and the walk home through one gate** (`isOnColonyErrand`), and a
  meal already in the jaws is finished or counted as eaten (`EatGoal.java:79`, `:92-96`).
- **A scout pushed aside keeps its trip** for 30 seconds and takes it up where it was (BUGS 185); pushed aside for
  longer, or no longer fit, it is handed back to the colony properly (`finish`, `:349-357`).
- **`DigGoal` cannot throw a Matabele's load away.** Round 3's R3-19 (a first bite puts a soil pellet over what is
  held) needs the colony's "where the soil goes" hook, which only the termite mound has
  (`SavannaParts.java:67`); a Matabele that digs with prey in its jaws keeps the prey.
- **Night cannot strand an ant mid-task** (case 8): no Matabele chore, care or carry asks the clock.
- **Near a player Matabele are not swapped into the numbers** in ordinary play (case 8), so care and rescue are not
  cut short by the swap; only by the colony going away.
- **The bite-out cannot flicker** (case 9), and it does not drop what the ant holds.
- **The queen's list** has nothing that carries or is drafted; her only fight order is the defence's, 40 ticks at a
  time, and she keeps her own fight-back reach of 3 blocks.
- **Not proven, so not a finding:** (1) whether a wounded defender is really carried in and walks out again in a loop
  (X-3's "likely"); (2) how often a soldier is shut in with raiders (X-1); (3) what `MatabeleBiteOutGoal.pick` costs
  when it finds a block and then no way to stand by it (one more full look over the pocket on each following tick,
  one block struck off per tick, until none is left): to measure, the nests reader's file may have it.

## What was read

Read in full: `MatabeleEntity.java`, `MatabeleLoads.java`, `MatabeleQueenEntity.java` (from line 30),
`BringPreyGoal.java`, `CarryRemainsGoal.java`, `MatabeleBiteOutGoal.java`, `MatabeleRaidGoal.java`,
`MatabeleWayHomeGoal.java`, `Headway.java`, `RescueGoal.java`, `WoundCareGoal.java`, `ClampCareGoal.java`,
`FightGoal.java`, `EatGoal.java`, `Wounds.java` (from line 20). Read in part, only as far as the question needed:
`AbstractAntEntity.java` (lines 340-750, 860-1340, 1420-1560, 1620-1660, 1850-2020, 2185-2440); `canUse`, `stop` and
the flags of `FeedQueenGoal`, `FeedLarvaGoal`, `CarryEggGoal`, `DigGoal`, `GoInsideGoal`, `WaitAtNestGoal`,
`AntWanderGoal`, `NapGoal`, `GroomGoal`, `AllogroomGoal`; `MatabeleScoutGoal.java` lines 150-420 (its start, stop and
fitness, not its search); `NestDefence.java` lines 96-275, 300-420, 451-486; `Raids.java` lines 405-680 and 970-1045;
`Scouts.java` lines 245-320 and 690-720; `Raid.java` lines 950-970, 1320-1325, 1420-1530 and 1900-2115; `WayHome.java`
lines 40-200; `Population.java` lines 380-585; `AwayLife.java` lines 230-375; `Strays.java` lines 1-80; the rescue,
food and colony numbers of `megaponera_analis.json`; round 3's goal thread and fight thread; the headings of
`docs/CODE-REVIEW-BUGS.md`. **Not read:** the insides of the raid and the scout's search (round 11's), `Clamp.java`
beyond its hold-still lines, `Errand.java`, `QueenToRoomGoal`, the Matabele game tests (so no test gaps are claimed
here), `docs/DESIGN.md` and `docs/BUGS.md` beyond a search for the points above, and Minecraft's own goal code (taken
from round 3).

