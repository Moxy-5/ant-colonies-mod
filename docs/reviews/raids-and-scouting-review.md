# Review: Matabele raids and scouting (2026-11-04)

**Status (2026-11-04):** findings 1 to 7, 12, 14, 16 and 17 fixed on branch `claude/m8-prey-home-44beec` (the first batch; details in `docs/HISTORY.md`, "Raid code review, first batch of fixes"). Still open: 8, 9, 10, 11, 13, 15, 18, and the other points of 16 and 17.

A read-only review of the M8 raid and scout code, done the way a seasoned Java and NeoForge 1.21.1 modder would look at it. No code was changed. The review covers the code as it stood on branch `claude/m8-prey-home-44beec` on 2026-11-04. That branch includes R4, the scent trails, which was not committed yet (`ScentTrails.java` and `RaidView.java` were still untracked).

**Files read:**
- Savanna:
  - `colony/Raids.java` (842 lines)
  - `colony/RaidColumn.java` (1,243 lines)
  - `colony/ScentTrails.java`
  - `colony/SavannaParts.java`
  - `colony/PreyPile.java`
  - `entity/goal/MatabeleScoutGoal.java`
  - `entity/goal/MatabeleRaidGoal.java`
  - `entity/goal/BringPreyGoal.java`
  - `entity/MatabeleEntity.java` (the goals, `mayGoOut` and `foeRank`)
  - `command/RaidView.java`
  - `command/MatabeleCommands.java` (the raid parts)
  - `SavannaEvents.java`
  - `species/SavannaNumbers.java`
  - `megaponera_analis.json`
  - `gametest/MatabeleRaidTests.java`
- Framework:
  - `ColonyManager`
  - `Colony.tick`
  - `AwayLife`
  - `GoInsideGoal`
  - `FightGoal`
  - `NestDefence`
  - `AbstractAntEntity.orderToFight`

**Each finding has:**
- **where**: the file and line
- **what**: what is wrong
- **cost**: what goes wrong because of it
- **instead**: what a seasoned modder would do
- **size**: S (an hour or two), M (a day), or L (several days)
- **plan**: whether a planned step already covers it
  - planned: R5 to R8, 3d, 3f, or later M8 steps
  - **new**: not in any plan

Findings that a planned step already covers are only flagged, not argued again.

Line numbers are from the working copy on 2026-11-04 and may drift slightly while R4 is being finished.

---

## High: these can break raids in normal play

### 1. A raider in an unloaded chunk is dropped at once, and the raid it leaves can wipe out a good find (FIXED 2026-11-04)
- **Where:**
  - `RaidColumn.update`, L432-443: the "gone from the world" drop and "nobody left in it"
  - `Raids.raidHome`, L259-273, and the `foundEmpty` call at L271
  - `SavannaParts.RAIDS`, L295: it has no `ticksAway`
- **What:**
  - `update()` drops every member for which `level.getEntity(id)` is null. The same happens on the very first update after the ant's chunk stops being in range.
  - The framework's `AwayLife` gives a frozen insect a full minute's grace before folding it into the numbers (`GRACE_TICKS` 1200). The raid code gives none. A raid 60 to 90 blocks out, with the player walking the other way, loses its far end in one update.
  - When the last member goes, the raid ends with `carried == 0`. If the trail was not lost, `raidHome` counts that as an **empty check** (`foundEmpty`). Two of those and the colony forgets a perfectly good feeding spot.
  - Raids has no `ticksAway`, so while the colony is away the column does nothing. On return its stage timers (`stageSince`, `attackedAt` and the others) have all expired together, and the next update ends stages in a burst.
- **Cost:**
  - Raids fail silently, and the colony "learns" that a good spot is empty, whenever the player is not standing near the column.
  - Ants that come back after a short unload belong to no column. They stand where they were left, which is BUGS 88: "doing []".
  - Scouts have the same problem. `Raids.tick` drops a scout from `scouts` when it cannot be found (L226-230), so a scout in an unloaded chunk is quietly retired.
- **Instead:**
  - (a) Give a missing member the same grace as `AwayLife`. Keep it in the column, marked "unseen since T", and drop it only after about 1,200 ticks.
  - (b) Never count a raid that ended because its members were *unloaded* as an empty check. Only a raid whose members really reached the spot and found nothing should count.
  - (c) Use game-time deadlines ("ends at tick N"), not "ticks since". A colony coming back can then end a stage cleanly with a diary line, not in a cascade.
  - (d) Until 3f makes raids run as numbers out of sight, end a raid whose leader is unloaded with an explicit "lost from sight" outcome. Tell its returning ants to go home (see finding 2).
- **Size:** M.
- **Plan:** partly R7 ("raids turn back at unloaded land") and 3f (raids out of sight). The instant drop, the empty-check side effect and the scout drop are **new**. Neither R7 nor 3f as written covers them.

### 2. An ant let go far from home has no reliable way back (FIXED 2026-11-04)
- **Where:**
  - `RaidColumn.home`, L1038-1068: the raid ends at `homeTicks` and leftover ants get no order
  - the march time-out and the stragglers it drops
  - `MatabeleScoutGoal`, L383: `HOME_GIVE_UP` sets DONE
  - `BringPreyGoal`: its `Errand` falls back to `layOutOfSight`
  - framework `GoInsideGoal`
- **What:**
  - Once the column lets an ant go, nothing in the Matabele's goals takes it home except `GoInsideGoal` (priority 4).
  - `GoInsideGoal` fires in 1 of 10 checks, only when `!mayGoOut()`. It does one whole-route search to the nest, retries after 200 ticks and gives up after 1,200. It has no hopping.
  - From 30 to 90 blocks out over savanna, that search usually fails or runs out of steps. The ant then wanders (`AntWanderGoal`) or stands still.
  - The limping ant is the one case that already works. `Raids.limpOrder` walks it home in hops along the raid's route.
- **Cost:**
  - This is the most likely root cause of BUGS 88 (a raider left at the termites).
  - It also explains scouts that "never came home": a scout that gives up after 2,400 ticks is left out there, its find lost.
  - It gets worse once R8 makes distances real.
- **Instead:**
  - Write one small "go home along a trail" errand: hops along a known `Route` or scent trail, then the last stretch by a whole-route search.
  - Give it to every ant the raid code lets go: homing timeout, stragglers, scout give-up, prey carriers that cannot path.
  - `limpOrder` already is this; generalise it.
  - Make it species-neutral, so it fits the framework next to `Errand` (see finding 13).
- **Size:** M.
- **Plan:** **new**. R3 fixed the march but not what happens to ants after it.

### 3. The scout and raid goals share priority 2 with the nest chores, so a busy ant never joins (FIXED 2026-11-04)
- **Where:**
  - `MatabeleEntity.registerGoals`, L184-192
  - `Raids.sendNow`, L698-708
  - `Raids.raiderFit`, about L656
- **What:**
  - In vanilla's `GoalSelector`, a running goal can only be replaced by a goal with a *strictly lower* priority number when the two share a flag (MOVE here).
  - `FeedLarvaGoal`, `FeedQueenGoal`, `CarryEggGoal`, `CarryRemainsGoal`, `ClampCareGoal` and `BringPreyGoal` all sit at 2 with MOVE. So do the scout and raid goals.
  - An ant feeding larvae when it is picked as a recruit keeps feeding. The column counts it as a member that "did not come out" and drops it at the FORMING deadline.
  - A scout picked by `sendNow` is added to `sent` without anyone checking that the goal started.
  - `raiderFit` does not check hunger, while `EatGoal` (priority 1) outranks the raid goal. A hungry recruit is enlisted, then stops to eat mid-march.
- **Cost:**
  - Raids leave smaller than planned.
  - The scout command now and then does nothing (BUGS 89).
  - Hungry raiders fall out of the march, and `headway()` then sends them home "hurt" (finding 5).
- **Instead:**
  - Put `MatabeleScoutGoal` and `MatabeleRaidGoal` at priority 1, ahead of the chores. They are short, colony-ordered errands, so outranking chores is right.
  - Or keep 2 but have the column "claim" the ant by stopping its running goal (`goalSelector.getAvailableGoals()…stop()`), which is clumsier.
  - Use the same hunger test in `raiderFit` as in `fit()`.
  - Have `sendNow` and `startRaid` confirm, a tick later, that the goal took the ant, and log "did not take it" in the raid diary when it did not.
- **Size:** S.
- **Plan:** partly R7 (BUGS 89). The general priority clash and the hunger gap are **new**.

### 4. Nest defence can draft raiders and scouts out of the column (FIXED 2026-11-04)
- **Where:**
  - framework `NestDefence.mayDefend`, L207-209
  - `orderToFight` and `endFightOrder` in `NestDefence`
  - `AbstractAntEntity.orderToFight`, L1423-1435
- **What:**
  - `mayDefend` accepts any worker that can fight. A player or a termite near the entrance while a raid is FORMING makes `NestDefence` pick the nearest real members. Those are exactly the ants milling at the entrance.
  - `orderToFight` overwrites the ant's fight spot. `MatabeleRaidGoal.canUse` then stands aside (`getFightSpot() != null`).
  - When the wave ends, `endFightOrder` clears the fight spot. If that ant was in the ATTACKING stage, it also clears the column's own fight order.
- **Cost:**
  - Raiders vanish from the column at the start.
  - A raid attacking near its own nest can lose its fight orders early.
  - Rare in tests, likely in play with a player watching the nest.
- **Instead:**
  - Add a neutral question to the framework, "is this member on an errand that defence must leave alone?" (a `ColonyQuestions` row). Raids answers it for raiders and scouts.
  - Or let `orderToFight` remember who gave the order, so `endFightOrder` clears only its own.
  - The framework must not name raids, so it has to be a question.
- **Size:** S to M.
- **Plan:** **new**.

### 5. `headway()` sends healthy but busy ants home as "hurt" (FIXED 2026-11-04)
- **Where:** `RaidColumn.headway`, L492-513.
- **What:**
  - A raider more than 2.5 blocks from its place that moved under 1.5 blocks in 300 ticks is dropped from the column and added to `hurt`, so it limps home.
  - An ant standing still because it is eating (`EatGoal`), fighting a provoking player or termite (`FightGoal` at priority 0), or held by finding 4 matches the same test.
- **Cost:**
  - Healthy ants limp home mid-march.
  - The diary says "hurt", which hides the real cause when reading a raid.
  - Raids shrink for no visible reason.
- **Instead:**
  - Before calling an ant stuck, ask why it is not moving: a fight spot or foe set, eating, picking up a termite.
  - Only "no goal running and no progress" is *stuck*. Stuck is its own outcome, sent home by finding 2's errand, never "hurt".
- **Size:** S.
- **Plan:** **new**.

### 6. Turning back mid-march freezes the column (FIXED 2026-11-04)
- **Where:**
  - `RaidColumn.turnBack`, about L455-457
  - `march()`, L654: `target = max(own, min(aheadAt - spacing, own + LEAD_STEP))`
- **What:**
  - When a raid turns home mid-march (trail lost, time up), it switches to HOMING on the `back` route. Its leader is now the ant that was *farthest out*.
  - Every follower's place is computed as "never behind where I am now" (`max(own, …)`). Measured along the back route, every follower is already ahead of the new leader.
  - Each follower's target therefore equals where it stands. The whole column stands still until the leader has walked back through it.
  - The marching order is not reversed either.
- **Cost:**
  - A visible freeze, which is exactly the "column freezing" kind of report, every time a raid gives up on a broken trail. That is now more common with R4's trail breaks.
- **Instead:**
  - On turn-back, re-sort the members by their position along the `back` route and make the front-most one the leader.
  - Or, for HOMING, drop the "never move back" rule and give each ant its own homeward target.
- **Size:** S.
- **Plan:** **new**. It comes from R4 code.

---

## Medium: costs, fragility and clunkiness

### 7. Scent trails break for any world change, and the break events ignore cancellation (FIXED 2026-11-04)
- **Where:**
  - `ScentTrails.tick`, L254-283: the 40-tick scan of every watched point
  - `SavannaEvents.onBreak` and `onPlace`, L86-99
- **What:**
  - The design says a *player* breaking a trail makes the column cast about.
  - The 40-tick scan breaks a point on any change that makes `scentHolds` false: termites building mud sheeting, a tree falling, a dead mound wearing down, grass growing over coarse dirt, and the Matabele majors' own digging at the spot.
  - The events subscribe at default priority and do not check whether another mod cancelled the event. On a server with a land-claim mod, a player who was *refused* the break still breaks the trail.
- **Cost:**
  - Columns casting about over trails nobody touched.
  - Odd behaviour beside protection mods.
- **Instead:**
  - Subscribe with `priority = EventPriority.LOWEST` and return early if `event.isCanceled()`.
  - Decide on purpose which world changes count: a whitelist of "the ground under the point became air or liquid", not any change in the four lookups.
  - Ignore cells inside the raid's own spot radius.
- **Size:** S.
- **Plan:** **new**. It is R4's code; R4 is built but not committed.

### 8. Timing that leans on "the part ticks on multiples of 20"
- **Where:**
  - `Raids.tick`, L224: `getGameTime() % 20`, which is redundant
  - `ScentTrails.tick`, L255: `now % CHECK_EVERY` with `CHECK_EVERY` 40
  - `MatabeleScoutGoal.canUse`: `tickCount % 20`
- **What:**
  - Colony parts already tick only when `gameTime % 20 == 0`.
  - `% 40` therefore works only because 40 is a multiple of 20.
  - Change `CHECK_EVERY` to 30, or stagger colony ticks across the second (a normal optimisation once many colonies exist), and the trail checks silently stop running.
- **Cost:**
  - Nothing today.
  - A silent failure later that no test would catch.
- **Instead:**
  - Keep a `nextCheckAt` game-time field per part and run when `now >= nextCheckAt`.
  - Delete the redundant `% 20` in `Raids.tick`.
- **Size:** S.
- **Plan:** **new**.

### 9. Per-update cost grows with trail length times column size
- **Where:**
  - `RaidColumn.gapAhead`, L681: for each trail point it calls `Route.along`, a linear scan of the route, and builds lists
  - `Route.along`, L1220: called per ant per update
  - `bunch` and `standAt`: per ant every 5 ticks
  - `preyRound`, L904: an entity box query, called every update in COLLECTING and SPREADING
  - `spreadOut`, L1001: a per-ant entity query and `canSeeSky`
  - `MatabeleEntity.isInAColumn` → `raids.isRaider`: a list scan inside `foeRank`, which runs inside sort comparators
  - `level.getEntity(uuid)` for every member every update
- **What:**
  - Each piece is cheap, but they multiply: 50 real raiders × a 100-point trail × an update every 5 ticks, plus every raider calling `orderFor` every tick.
  - `gapAhead` alone is about trail × route per update.
- **Cost:**
  - Probably under a millisecond today.
  - It grows into a real cost with R8's longer, real-distance trails and with several colonies raiding at once, which the savanna will have, with 2 in 3 dead mounds holding colonies.
  - Worth measuring with `/antcolonies perf` during a raid before R8.
- **Instead:**
  - Precompute the cumulative length of each route segment once and binary-search it (O(log n)).
  - Cache each ant's `along` value per update.
  - Recompute gaps only when the trail changes (a break event), not every update.
  - Keep members in a `LinkedHashSet` (or `Set` plus `List`).
  - Cache entity references for the update.
  - Merge `preyRound`'s query into one per update.
- **Size:** M.
- **Plan:** **new**. Do it before or with R8.

### 10. `RaidColumn` is one 1,243-line class holding nine stages
- **Where:** `RaidColumn.java`: about 35 mutable fields, about 25 timing and distance constants, timers reset by hand in `next()`.
- **What:**
  - Every stage reads and writes shared fields, so the order in which they are reset matters.
  - `DEFAULT_CARRY` (L73) is a 14-argument positional record. Swapping two numbers compiles fine and is wrong.
  - The species numbers are inconsistent:
    - `TrailNumbers` has a `DEFAULT`.
    - `ScoutNumbers`, `ColumnNumbers` and `CarryNumbers` have none.
    - `Raids` keeps a nullable `numbers()` with ad-hoc fallbacks (`numbers == null ? 5 : …`, `? 3`, `? 10.0`).
- **Cost:**
  - Each fix (R3, R4) has had to read the whole class.
  - BUGS 87 came from a stage reading a shared "at place" test that missed a case.
  - The changes R5 to R8 will touch it again.
- **Instead:**
  - Split it into a small `Stage` interface with one class per stage (forming, march, attack, collect, home). Each owns its timers and gets a shared context (members, routes, numbers, diary).
  - Move `Route` into its own file and `casting` into a helper.
  - Give every numbers record a `DEFAULT` built from its codec defaults and make the species section non-null, so the fallbacks disappear.
  - Move the remaining timing and distance constants that the user might want to tune into `megaponera_analis.json`. The pure code-safety ones (`NO_WAY_TICKS`, `EVERY`) can stay as constants.
- **Size:** L.
- **Plan:** **new**. It fits after R5 to R8 rather than before, so the rework is not split across two shapes.

### 11. Small duplications that will drift apart
- **Where and what:**
  - Three "can an ant stand here" checks:
    - `RaidColumn.standAt` (L1108)
    - `ScentTrails.scentHolds` (L286, the same logic)
    - `MatabeleScoutGoal.openGround` (L287: heightmap plus `isFaceSturdy`, different)
  - Four copies of `flat()`: RaidColumn, MatabeleRaidGoal L198, MatabeleScoutGoal L407, the tests.
  - Two clock formatters for the diary (`Raids.say` and `RaidColumn.say`), plus the same formula in `ScoutNumbers.inHours`.
  - The hunger formula twice, with a hard-coded 10 that duplicates `termitesPerLayer`: `Raids.isHungry` L328 and `raidSize` L597.
  - `Raids.termiteColonies` called twice in `MatabeleScoutGoal.lookRound` (L142 and L145).
  - A Javadoc left above the wrong method: `Raids`, about L349, the `scoutsWanted` comment sits above `say()`.
- **Cost:**
  - A fix to one copy and not the others is exactly how "scouts walk where raids cannot" bugs appear.
- **Instead:**
  - One `standAt` and one "scent holds" helper. Decide on purpose whether scouts may use spots raiders cannot.
  - One `flat()` in a small geometry helper.
  - One diary clock.
  - Use `PreyPile.termitesPerLayer` in the hunger formula.
- **Size:** S.
- **Plan:** **new**. The hunger test is also touched by R5's "the hungrier, the less choosy".

### 12. How finds are kept and forgotten (FIXED 2026-11-04)
- **Where:**
  - `Raids.plan`, L462-496 and L472
  - `MatabeleScoutGoal.stop`, L181-184
  - `MatabeleScoutGoal` give-up, L383
- **What:**
  - `plan()` sets `best.checkedAt = now` when it *sends* a scout to recheck a find, not when the scout gets there. A scout that never arrives (finding 1, finding 3) keeps the find fresh for ever.
  - A scout interrupted by a fight (`stop()` calls `raids.back`) or giving up on the way home loses its find with no diary line.
  - The range-growth and "any gallery cell counts" points are the planned R5 items.
- **Cost:**
  - Stale finds that never die.
  - Real finds that vanish without trace in the diary.
- **Instead:**
  - Set `checkedAt` when the recheck *happens*. Keep a separate "scout on its way since" field to stop two scouts going at once.
  - Log every lost find.
  - When a scout with a find cannot get home, hand the find to the colony as "unconfirmed", and let a later scout confirm it.
- **Size:** S.
- **Plan:** range and worth: R5. `checkedAt` and the lost finds: **new**.

### 13. Pieces that belong in the framework
- **What:**
  - Several raid pieces are not Matabele-specific. Army ants, leafcutters (future species) and the harvester's neighbour fights will want them:
    - `ScentTrails`: lasting, breakable trails with an age
    - `RaidColumn.Route`: a polyline with "along" and "offset"
    - the column march (single file, gaps, surges)
    - `standAt`
    - the raid diary logger
    - finding 2's "home along a trail" errand
  - The framework already has `ForagingTrails`, `TrunkTrails` and `Recruits`, which has a scent line and single-file following. `ScentTrails` overlaps them in idea: trails that fade, are laid by walking, and are followed.
- **Cost:**
  - The next species copies this, against the project's own rule: "never copy a whole system to make a species version of it".
- **Instead:**
  - Move `Route`, `standAt`, the trail store and the "home along a trail" errand into the framework behind neutral names.
  - Leave the raid stages, the find scoring and the prey handling in savanna.
  - Check whether `Recruits`' single-file following can share the march's follower rule.
  - Keep it to code moves only: no species names in the framework, and nothing changes in-game.
- **Size:** M to L.
- **Plan:** **new**. It suits a small Apex-style step after M8 is playable.

### 14. The raid view keeps players in a static set for ever (FIXED 2026-11-04)
- **Where:** `RaidView.java`, L39: `private static final Set<UUID> watching`.
- **What:**
  - The set is never cleared on logout, on server stop, or when the player changes dimension.
  - In single player, one game session that opens two worlds keeps the toggle across them.
  - It holds UUIDs only, so the leak is tiny, but it is the classic static-state bug.
- **Instead:**
  - Clear it on `ServerStoppingEvent` and remove the player on `PlayerLoggedOutEvent`.
  - Or keep the flag on the player (a transient field or attachment).
- **Size:** S.
- **Plan:** **new**.

---

## Low: worth knowing, can wait

### 15. Trail housekeeping
- `ScentTrails.Trail.points()` copies the list on every call. The raid view calls it every 10 ticks per trail.
- `box()` is recomputed in full on every block event.
- Points that lie in unloaded chunks when the trail is laid are never watched.
- Trails do not store their nest end. When the colony moves house (step 6), old trails will still lead "home" to the old nest.
- **Size:** S.
- **Plan:** step 6 for the nest end; the rest is **new**.

### 16. Save and load (raidWaiting FIXED 2026-11-04; the veterans point open)
- The column is not saved (by design: a raid out when the world closes is lost).
- `raidWaiting` is saved as an *index* into the finds list. If the order or count of finds changes between saving and loading, it points at the wrong find. Save the find's position instead.
- `veterans` only lose ants that are loaded and dead (L235). A veteran that died in an unloaded chunk stays on the list. The list is capped, so the cost is only a wasted slot.
- **Size:** S.
- **Plan:** **new**. 3f will decide whether columns are saved.

### 17. Small code notes (the straight hop FIXED 2026-11-04: ants going home or catching up are given places a few blocks along the trail; the other points open)
- `MatabeleRaidGoal.walkToward` hops in a straight line toward the place, not along the trail. On a curved trail the hop cuts corners, which may go into ground the trail avoided. `limpOrder` already hops along the route; the raid goal could too.
- `BringPreyGoal` finds its colony through `ColonyManager.get(level).getColony(id)`, unlike the other goals' `ant.getColony()`.
- `Raids.outOfMound` takes a `level` it does not use.
- `MatabeleRaidGoal` calls `getLookControl().setLookAt` directly. That is fine: looking is the body layer, not steering. Every *movement* goes through `AntNavigator`, as the project's rule says.
- **Size:** S.
- **Plan:** **new**.

---

## Tests

### 18. What the raid tests do and do not prove
- **Where:** `gametest/MatabeleRaidTests.java`, 5 tests.
- **Gaps:**
  - Tests 2, 3 and 5 build the trail by hand on flat ground (`raidForTests`, `shapeGround(..., 0)`). That skips the scout, and none walks a slope, a hill or a curved trail.
  - Nothing unloads part of a raid, saves and loads during one, or lets a player or termite trigger nest defence during FORMING. Findings 1, 4 and 5 would all pass today's tests.
  - The chain test (test 4) uses `sendNow`, which skips the raid hours, the hunger check and the goal-priority path that BUGS 89 is about.
  - The chain test's "hang" limits (MARCHING 1200, ATTACKING 6600, HOMING 3900) were set from today's constants. A change that makes a stage twice as slow still passes. For example, the diary's six-game-hour attack fits inside 6,600 ticks at most of the test's speeds.
  - Test 5's "no former scout" check depends on the clock being 21:00, so no scout went out. That tests the hour as much as the rule.
  - Some checks are halves and percentages of a living colony (`cameHome * 2 >= members`). These are the kind of checks the project's 4-box rule calls too strict. Here the risk is the reverse: they are loose enough to pass a half-broken raid.
  - Missing:
    - two Matabele colonies raiding the same spot
    - a termite colony raided twice
    - a carrier dying on the way home (are its termites dropped?)
    - a scout at range 96
    - a hungry recruit
- **Instead:**
  - One test where the raid's far half is unloaded and reloaded (`level.getChunkSource().removeRegionTicket`, or move the test's ground ticket). Then check that the find is not marked empty and that every ant gets home.
  - One test where a provoking entity stands at the entrance during FORMING.
  - One test where the trail runs over a 3-block hill and round a bend, through the scout and not `raidForTests`.
  - In the chain test, assert stage times against the *species numbers* (for example "attack ends within `fightTicks` + 400"), not fixed tick counts.
  - Before adding tests, check whether an existing scene can take the new check, as the project's test rules say.
- **Size:** M.
- **Plan:** R2 (the chain test) exists. These are **new** additions. The project's planned "test revamp" after M8 step 3 is the natural home for them.

---

## In plain words, for the user

**What matters most right now.** The raids mostly break when you are **not** standing near them.

- When part of a raid walks out of the area the game is running, the code drops those ants at once. The game itself would give them a minute.
- If the whole raid goes that way, the colony thinks the termites were not there and may forget a good spot.
- Ants that come back afterwards, or that the raid lets go far from home, have no good way to walk home. They try one long route plan, which often fails from far away. This is very likely your "raider left at the termites" bug (BUGS 88).
- Three smaller things also make raids shrink or freeze for no visible reason:
  - Ants busy feeding larvae ignore the call to raid.
  - The nest's defenders can pull raiders out of the column.
  - A raid that turns back halfway stands frozen until its new leader has walked back past everyone.

All of these are fixes of an hour to a day each (findings 1 to 6). I'd do them next to R5 to R7, because they make every later raid test more trustworthy.

**What can wait.**
- The big raid file is long and hard to change. It would be cleaner split up, but that is best done *after* R5 to R8, so the rework is not done twice.
- Some raid speed costs will matter once distances are real (R8) and many colonies raid at once. Worth measuring then.
- Moving the trails and the "walk home along a trail" piece into the shared framework would let army ants and leafcutters reuse them later.
- The small duplications, the raid view's memory, and the save details can wait.

**Tests.** Today's raid tests run on flat ground with the player standing by, so they cannot catch the problems above. Adding a few of the scenes in finding 18 would.
