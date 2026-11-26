# Full code review, round 11: the new raid code (reading round, 2026-11-24)

Read on branch `claude/raid-rewrite-edbbde` at commit `9a98972d` (the rewrite's steps 1 to 5), by a session that did not
write it. Rule book: `docs/plans/code-review.md`. No code was changed. The raid game tests were run once
(`ANTCOLONIES_TESTS=raids`): **all 27 passed** in 2 minutes. No soak was run and nothing was measured in the game, so
no lag finding here has a number yet.

**What was read, every line:** in `savanna/.../colony/`: `Raid`, `Raids`, `RaidsAway`, `RaidMarch`, `RaidRules`,
`RaidGround`, `RaidCatch`, `RaidCost`, `Scouts`, `RaidDefence`; in `savanna/.../entity/goal/`: `MatabeleScoutGoal`,
`MatabeleRaidGoal`, `MatabeleWayHomeGoal`, `Headway`; `savanna/.../entity/ScoutMemory`; the framework's `ScoutGoal`; the
raid parts of `MatabeleEntity` and the raid numbers in the species file. About 8,400 lines. Three helper readers read a
piece each; the main reader then checked the findings marked "checked again" against the code lines they name.

**How to read a finding.** Rating: **A** the player can meet it, **B** it will cost us when building on this,
**C** a tidy-up. Proof: **proven** (the path was walked through the code, lines named), **likely** (one link, named in
the finding, was not checked), **to measure** (a lag finding with no number yet). A finding rated A gets its
`docs/BUGS.md` number when a fix session takes it up. Findings keep their numbers for good; a fix session adds a
status line under a finding and never rewrites it.

**Count:** 22 findings: 5 A, 12 B, 5 C.

## The short of it

No crash was found, no stage that can hang for ever, no ant doubled or lost between bodies and numbers, and the raid as
numbers reads no block in unloaded land. The rewrite's main promises hold: one rule of when a raid leaves, one judge of
"worth a raid", every stage with a deadline that fires, a raid kept through a reload.

What is wrong is at the edges: the **scouts** (they were not rewritten, only moved) still read unloaded land and search
for routes every tick; the **attack's end rule** is kept awake by a termite nobody can reach, so attacks run to their
2-minute deadline; and the **change between bodies and numbers** has loose bookkeeping and two cases a player can see.

## The five that matter most

1. **R11-1:** a scout setting out can freeze the game by reading termite galleries in land that is not loaded.
2. **R11-2:** a scout with no way to its find searches for a route every tick.
3. **R11-3:** raiders pace round a finished spot until the 2-minute deadline, because "goes for a termite" counts as work.
4. **R11-4 and R11-5:** a raid can vanish in front of the player, or take termites with no ant in sight.
5. **R11-6:** a scout counts soldiers as a find; the raid's judge does not, so the scout's trip is wasted.

---

## Grade A

### R11-1 (A, proven; checked again): a scout setting out reads blocks in land that is not loaded

**Status: fixed** (2026-11-24, BUGS 316; test `aFeedingPlaceInLandNotLoadedIsNeverRead`). The three reads ask `LandReady` first. Not done: `lookRound` still asks before its distance filter (harmless now).

**What is wrong.** At the start of every trip the scout asks every termite colony within about 133 blocks of its nest
(reach 96 + smell 5 + 32) for its by-day feeding places. That question reads blocks at the termites' galleries with no
"is this land loaded" check, and the scout's own distance filter comes after the read.

**What the player would see.** A hitch (a chunk read from disk) or a freeze of up to seconds (a chunk made new) when a
scout leaves, if a termite colony in that ring has a gallery outside the loaded land. A gallery can lie about 180 blocks
from the ant nest.

**Where.** `MatabeleScoutGoal.lookRound` 289-313 (the read at 301, the filter at 302); `FeedingSpots.galleryByDay` 61-68
(`level.getBlockState(over)` at 65, unguarded); `FoodStore.isFeedableFromInside` 551-556
(`FoodCast.isCast(level.getBlockState(next))` runs when `Nest.isDugOut` answers false for unloaded land);
`FeedingGallery.joinCasts` 93-94 (`level.getBlockState(from)`, only `next` is guarded). `Raid.termitesThere` 348 asks
the same question, guarded only by "the termite colony is here".

**Proof.** The path was walked by the reader; the main reader read the three unguarded lines. `Mound.footReach`
(`Mound.java` 238-242) carries a comment about this same path freezing the game 1 to 3 s on 2026-11-10; that fix
covered the mound's shape only. **Likely, not proven:** that `Nest.isInside` says yes for a gallery cell in unloaded
land (the reader says the roof check is skipped for them).

**Fix proposed.** Guard the three reads with `LandReady.at` (not loaded means "not by day here"). In `lookRound`, drop
a termite colony's places past the distance filter before asking for them.

### R11-2 (A, the repeat proven, its size to measure; checked again): the scout searches for a route every tick when it has no way

**Status: fixed** (2026-11-24, BUGS 317; test `aScoutWithNoWayOnAsksForARouteOnlyNowAndThen`): a half-second retry clock, about a fifth of the route searches. **Open:** no rest between a scout's failed trips; a failed search's cost in milliseconds not measured.

**What is wrong.** `AntNavigator.moveTo` plans a route at once on the main thread (`AntNavigator.begin` 403-421 ends in
`plan()`); a failed plan can run to 6,000 steps of the path finder. The scout goal calls it again on every tick the
navigator is done, with no wait after a failure. This breaks the project's own rule from BUGS 76 ("never searched
every tick"), which the raid goal and the way-home goal both keep.

**What the player would see.** Tick-time spikes while a scout stands at a find across a gap, or on ground with no way on.

**Where, all in `MatabeleScoutGoal`.** `inspect` 735-743 (every tick, for up to 160 ticks, at a find it cannot get
near); `searchOn` 396-403 with `hopToward` 594-609 (up to 3 `moveTo` a tick) and `stepToward` 520-536 (up to 8); the
limits are counts of tries, not time, so a hemmed-in scout makes up to about 150 failed plans in a second;
`walkHome` 996-1008.

**Likely, not proven.** A scout whose trip fails fast is sent out again within 20 ticks (`Scouts.mayScout` and `plan`
have no rest between trips), so the same trip can fail in a loop, each round with R11-1's read.

**To measure.** A scout summoned with its find across a 3-wide trench; the tick readout while it inspects.

**Fix proposed.** One retry clock in the scout goal (after a failed `moveTo`, wait about 20 ticks, as
`MatabeleRaidGoal.RETRY` does), and a short rest before the same scout's next trip after one cut short.

### R11-3 (A, the mechanism proven, how often in play not measured; checked again): the attack's "quiet" rule is kept awake by a termite nobody can reach

**Status: fixed** (2026-11-24, BUGS 318; test `RaidRulesTest.onlyWhatChangesSomethingAtTheSpotKeepsTheAttackAwake`).

**What is wrong.** The attack should end 15 s (`quiet_ticks` 300) after the last real work. But a raider's "goes for X"
counts as work, and the shared fight puts an unreachable termite aside for only 10 s (`FightGoal.UNREACHABLE_TICKS`
200) and then goes for it again. 200 is less than 300, so the quiet clock never runs out and only the 2-minute deadline
(`fight_ticks` 2400) ends the attack. "Its jaws close on nothing", "strikes and misses" and "grabs at X: already stung"
count as work too.

**What the player would see.** Raiders pacing round a broken spot with nothing left to sting, for up to 2 minutes.

**Where.** `Raid.fightNote` 436-445 (only "no foe", "searched", "no way", "chased" are idle); `Raid` 1854-1861;
`FightGoal` 80, 207, 325-327; `megaponera_analis.json` `quiet_ticks` 300, `fight_ticks` 2400.

**Fix proposed.** Count as work only what changes something: a grip, a sting, a breach picked or bitten open, a newly
stung termite. "Goes for" does not count. This removes a rule, it adds no number.

### R11-4 (A if met, likely; checked again): a raid the player is watching is folded into numbers when the nest's chunk unloads

**Status: fixed** (2026-11-24, BUGS 319; test `aRaidAWatcherIsNearKeepsItsBodiesWhenItsNestGoesOutOfRange`): fixed in `Raids.tickAway` (a watched raid is not sent to numbers), not in `goOnAsNumbers`.

**What is wrong.** "Is this colony watched" (`AwayLife.isHere` 153-163) answers no outright when the nest's own chunk is
not loaded, before it asks whether a watcher is near the raiders. A minute later `Raids.tickAway` 209-212 calls
`goOnAsNumbers`, which folds every raider in and discards its body (`Raid` 925-963).

**What the player would see.** Following a raid about 90 blocks from its nest on a low view distance (about 5 to 6
chunks), every ant vanishes after a minute. The raid goes on as numbers.

**Not checked.** That raiders can really be ticking beside a player while the nest's chunk is unloaded at common view
distances.

**Fix proposed.** In `goOnAsNumbers`, fold only the raiders no watcher is near; let the others go and walk home.

### R11-5 (A if met, likely; checked again): a raid as numbers takes real termites beside the player, with no ant in sight

**Status: fixed** (2026-11-24, BUGS 320; test `aRaidOutOfSightTakesNoTermiteStandingInFrontOfAWatcher`), by my pick with the user's yes: the termites in the open are left alone.

**What is wrong.** A raid as numbers is given bodies only when a watcher is within 64 blocks of its front and nobody
who can see is within 50 (`RaidsAway.show` 183-195), asked once a second. A player who arrives inside the 50 blocks
without crossing that band (a teleport, a login, a respawn, fast flight) meets a raid that stays numbers. At the attack
`Raid.takeHidden` 1725-1733 then carries off real termites standing in the open.

**What the player would see.** Termite workers at a feeding spot disappear, and no ant is there.

**Fix proposed, the user's call (it changes what is seen).** Either skip the termites in the open while someone who
can see is within 50 blocks (the catch then comes from the colony's numbers only: my pick), or let the ants pop in.

---

## Grade B

### R11-6 (B, proven; checked again): two judges of "termites here": the scout counts soldiers, the raid does not

The scout's `MatabeleScoutGoal.termitesNear` 679-687 takes any Macrotermes insect in the open, soldiers included. The
raid's judge `Raid.inTheOpen` 357-362 leaves `TermiteSoldierEntity` out ("soldiers alone are no reason to raid"). Two
soldiers within 30 blocks are worth 6, enough for a fed colony; the scout runs home, bends its memory toward that spot
(`Scouts.foundBy`), and `Raids.startRaid` then finds nobody there: no raid, the find forgotten. **The player sees** a
scout run home from soldiers on their beat and nothing follow. **Fix:** the scout counts with the judge's own test.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** one test of "a termite a raid is for" (`Raid.isPrey`: a living termite worker on its feet), asked by the raid's judge and by the scout's count; a scout no longer smells or counts soldiers.

### R11-7 (B, visible, proven; checked again): a raid shown again after going to numbers bunches as a hollow ring

`Raid.formIndex` (209, 390, 1539) gives each ant its place number in a bunch and is never cleared, so new bodies get
numbers after all the old ones. After one hide and show of 75 ants nobody stands in the middle; after about three the
places lie past the 6 blocks of `TAIL_IN`, so "the tail is in" can never be true and the halt and regroup always run to
their deadlines. **Fix:** in `takeBodies`, clear the map and number the new ants from 0, the leader first.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** `takeBodies` numbers the new ants' bunch places from 0, the leader first (checked in `MatabeleAwayTests.aRaidOutAsNumbersIsShownOnItsWayOutWhenAWatcherComesNear` and `...WithItsCatchAfterItsAttack`).

### R11-8 (B, likely): a raider called back by the leash may never fight again

A returner is sent to the halting place and fights again only within 7 blocks of the spot (`leash * COME_BACK`,
`Raid` 1835-1848). The halting place is the last trail point at least 4 blocks short of the find (`RaidMarch` 194-201),
so with trail points 3 blocks apart it can lie at 7, and the test is then never true. **The player sees** raiders
standing at the halting place for the rest of the attack. **Not checked:** how far apart a real scout's trail points
lie. **Fix:** send a returner toward the spot itself.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** a returner fights again once it is back at the halting place it is sent to (within 4 blocks of the trail's end), or within half the leash of the spot as before. Not measured: how far apart a real scout's trail points lie.

### R11-9 (B, proven; checked again): the raid's size is never lowered for real ants that leave it

`Raid.drop` 1267-1277 leaves `size` alone; `RaidsAway.show` 197 asks for `min(size, most_real, inside)` bodies and
`takeBodies` 1005 sets `numbersAlong = size - ants.size()`. A raid that lost 30 of 75 and went to numbers comes back 75
strong when shown. No ant is doubled in the colony's count. **Fix:** show `min(numbersAlong, ...)` bodies and take them
off `numbersAlong`.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** a raid shown again is as big as its numbers are now (`size = numbersAlong` in `takeBodies`; `RaidsAway.show` asks for `numbersAlong` bodies, not the size it set out with).

### R11-10 (B, proven, rare; checked again): a raid shown "in the nest" from the halt, attack or regroup starts over

On a way shorter than 8 blocks `RaidsAway.show` 187-189 treats a raid at the spot as "by the nest" (`shareOut` is 1.0
at the halt, attack and regroup, `Raid` 797-804; only the march home is left out). `takeBodies` 1018-1026 then steps it
back to muster with its catch already in its jaws, and the next halt takes a second catch. **Fix:** step back to muster
only from the muster or the march out.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** `RaidsAway.show` forms a raid in the nest only from the muster or the march out; at its spot it is shown at its spot whatever the way's length.

### R11-11 (B, proven): in the colony's away grace minute nobody steps the raid

When a colony stops being "here" its parts are not ticked at all for 60 s (`AwayLife` 404-410, `ColonyManager` 89-98).
A raid as numbers stands still for that minute and then writes "its clocks waited" with the wrong reason; a raid with
bodies keeps walking on its raiders' own asks, but dusk and rain are not called and ants let go are not sent home until
the minute is over. **Fix:** step the raid from the framework during the grace, or accept it and correct the diary line.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** accepted, the diary line corrected ("nobody near its colony, or its land not ticking"). In the grace minute nobody is near the colony by definition (a watched raid keeps its colony here), so the stall is not seen; stepping the parts in the grace would be a framework change for every part.

### R11-12 (B, proven; checked again): a scout drafted to defend the nest stalls the whole march

A drafted leader is never let go (`Raid` 1421, `!id.equals(leader)`) and stays the march's front (`RaidMarch.step`
333-334). The column waits behind a scout fighting at the nest until the march's deadline, then turns home. The
design says no stage waits on a single ant, the leader included. **Fix (my pick):** on the way out, a leader drafted
longer than a short time turns the raid home at once, as "the scout is lost" does.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** the leader is let go by a fight elsewhere after 30 s like any raider (the exception deleted); on the way out the raid then turns home by the rule it already had ("the scout is lost"), later the next in line leads.

### R11-13 (B, likely; checked again): a waiting find whose land does not tick stops all scouting until it fades

`Raids.startRaid` 922-927 answers "land not loaded" and the find goes on waiting; while any find waits no scout is sent
(`Scouts` 274). **The player sees** a hungry colony beside them doing nothing for up to 3 game days (`spot_fade_ticks`),
on a low simulation distance. **Fix:** give that wait an end (about a game hour), then the find is only remembered.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** a find whose land is not loaded does not wait: the diary says so once and the find is only remembered (a later scout finds it again), so scouting goes on. No new number. The wait is left for one case only, a raid still out of sight.

### R11-14 (B, proven; checked again): a waiting find writes a diary line every second outside the raid hours

The reason text carries the clock (`Raids` 785, `RaidGround.clock` 141-147), so it is a new text every second and the
"say it once" test at 839-842 always passes: about 250 lines between 11:00 and 16:00, 550 in a night, for each colony.
The player sees nothing; the `[raid]` diary every diagnosis reads is flooded. **Fix:** take the clock out of the reason.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** the reason is "outside the raid hours" with no clock in it (the diary line's own start gives the time), so it is said once.

### R11-15 (B, proven): a find on its way home is lost on a reload

The find, its worth and the scent line are fields of the scout goal (`MatabeleScoutGoal` 133-135, 158); `ScoutMemory`
58-69 saves only heading, reach and empty trips. Quit while a scout runs home and the find is gone, though the scout's
memory was already bent toward it. No ant is left stuck. **Fix:** save them with `ScoutMemory`, or accept it and say so.

**Status (2026-11-26, `claude/review-fix-11b`): not a bug:** left as it is on purpose and written down (`ScoutMemory`'s comment): no scout's trip under way is saved, with a find or without; the scout's memory was bent toward the spot when it chose it, so its next trip goes that way again. Saving the find alone would need the whole trip saved (its stage, its line home).

### R11-16 (B, proven as code, the bad case likely): `Scouts.mayScout` lets any ant on the out list through unchecked

`Scouts` 261-264 returns true before the "is it fit", "is it a raider", hours and hunger checks, and `Scouts.plan` then
has side effects inside a goal's "may I start" (403, 407-411). **Fix:** keep the fit and raider checks in front.

**Status (2026-11-26, `claude/review-fix-11b`): fixed:** `Scouts.mayScout` asks "fit, no raider, not going home" first; an ant on the out list that fails it gives its place up (`back`). Not changed: `Scouts.plan`'s marks made inside "may I start".

### R11-17 (B, proven, written down in step 5 as left on purpose): near and away still differ in how often a colony raids

Watched, a colony raids only the find a scout has just brought home, so each raid costs a scout trip; out of sight it
takes the best remembered find every second (`Raids` 809-843, `RaidsAway` 240-271). By the reader's sums a colony of
100 needs about 2.5 raids a day: that fits out of sight, and a watched colony may stay hungrier. **For step 6's soak to
measure.** Also: a raid may leave until 19:00 and dusk turns it at 19:30, 25 seconds later, so a watched raid leaving
late is turned home empty and still costs one of the day's raids. Stopping leaves earlier is **the user's call**.


**Your call (2026-11-26):** How late a raid may leave. Today a raid may set out until 19:00 and dusk turns every raid home at 19:30, which is 25 real seconds later, so a raid that leaves late walks out, turns round empty and the scout's find is used up. Stop raids leaving earlier (the find then waits for the morning)? My pick: yes, but first a soak measures how long a raid takes, so the hour is not a guess.

**Call made (2026-11-26):** yes; measured from the last soak's diary, not a new soak.

**Status (2026-11-26, `claude/review-fix-b6`): half fixed: the last leave is 18:00, not 19:00 (`megaponera_analis.json`, `hours`). Measured from the 2-day soak's raid diary (86 raids): from the muster to the attack's start takes 0.9 game hours at the median and 1.4 for the farthest spots (95 blocks), so by 18:00 every raid reaches its spot before dusk. Open: how often a watched colony raids against one out of sight (for a soak to measure).**

---

## Grade C

### R11-18 (C, proven): the way-home list's 5-minute timeout is covered by the 5-second sweep

`Raids` 391 drops an ant after `limp_ticks` 6000; the sweep at 471-486 puts it back 5 s later with a fresh clock, so
the timeout ends nothing. An ant 8 to 16 blocks from the entrance is neither "home" nor swept. **Fix:** one rule.

### R11-19 (C, proven as code, small, to measure): work done more often than needed

- `MatabeleWayHomeGoal.tick` 104 asks `Raids.wayHomeOrder` every tick (up to 21 stand checks with no route).
- `RaidDefence.tick` 204, 301-304 scans a 33-block box for every break every 10 ticks, sealed ones too.
- `Raid` 1505-1516, 1670-1681 works every ant's bunch place out again 4 times a second by reading blocks.
- `Raid.marchHome` 2118 sorts with `members.indexOf`.
- `MatabeleRaidGoal.march` 228-238 plans a new route each time the ant's place moved a block: 1 to 2 plans a second for
  each of up to 75 raiders (designed; for the perf run to measure).
- Every fight note of every raider is an INFO log line (`Raid` 448-452): hundreds of lines an attack.

### R11-20 (C, each read in the code): small bookkeeping

- A raider whose dead body already left the world can join the numbers (`Raid` 1383-1391).
- `turnHome` sets `turned` for a raid already walking home, so the diary says "never looked the spot over" (1223, 1244).
- A finished raid not yet handed in is not saved (`RaidsAway.save` 488): its catch is lost if the game closes then.
- The diary's "N home" never counts the numbers along in a raid with bodies.
- `carriers` is never lowered when a carrier gets home (2135).
- `waitingBy` is not saved (`Raids` 1122-1124, 1146-1150): a command's waiting find becomes the colony's own on reload.
- `ScentTrails.lay` 302-304 can drop the trail a raid is walking (no "in use" test).
- The "a raid is still out of sight" check in `startRaid` 916-921 is nearly dead code.

### R11-21 (C, proven): comments and notes that disagree with the code

`Raids` header 37-63 (old step numbers, says it keeps the scouts); `Raids` 400, 637; `Raid` 829 ("about 2 blocks a
second", the design says 2.5), 1318, 1787; `RaidMarch` header and 317-319; `RaidCatch` 22; `RaidCost` ("the column");
`MatabeleScoutGoal` 4 and 13 (an import twice), 35, 45, 959, 966; `ScoutMemory` 9; `Scouts` 256; `RaidDefence` 56;
`SavannaParts` 296-297; `megaponera_analis.json` (the scouting note says 1 scout per 50, the number is 20; the column
note still says the scout runs into the nest to call).

### R11-22 (C; two of them the user's call): numbers

- `most_real` is 75, the project's target is about 50 to 60 real mobs for a whole colony. **The user's call.**
- The plan aimed at about 30 raid numbers; `Raid` holds about 20 more as constants in code (134-168, 1689-1692).
- `RaidsAway`'s numbers are all still "my pick, to confirm"; the unseen pace is 1.5 blocks a second against 2.5 watched.

**Your call (2026-11-26):** Real ants in one raid. `most_real` is 75, while the project's aim is about 50 to 60 real mobs for a whole colony. Lower it to about 50 (the rest of a big raid goes along as numbers, as now), or keep 75 for the look of a long column? My pick: keep 75 unless you see lag in a big raid; it is your eye that decides.

---

## Questions the readers left open (not findings; nobody checked them)

- `Route.place` 53-57 falls through to `GroundTop.cell` when the chunk is not ready: does that read wait on a chunk?
- A carrier already dropped from the raid, then folded in by the framework: are its termites lost?
- A raider whose prey to take is gone (`MatabeleRaidGoal.tick` 172-204): does it stand until the stage ends?
- The raid goal returns on a null order without stopping the navigator (138-140).
- `Raids.tickAway` calls `scouts.nobodyOut()` while a real scout's goal may still run: is its find safe to hand in?
- Can another species' insect count as "termites there" in `Raid.inTheOpen`?

## What is solid (do not "fix")

- **Stages:** every stage has a rule and a deadline that fire; a fault while a raid is worked out gives the raid up
  instead of stopping the colony.
- **Crashes:** every first-item read and sub-list is guarded; the march line always has two points; no list is changed
  while walked.
- **Unloaded land:** the raid as numbers reads no block; the march, the trails and the scout's walk ask `LandReady`
  first (R11-1 is the one hole, and it is in the termites' feeding question, not the raid).
- **Bodies and numbers:** no ant doubled or lost; the real-mob cap holds when a raid is shown.
- **One rule of leaving, one judge of worth:** `whyNoRaidNow` and `worthARaid` are the only ones.
- **Save and reload:** walked field by field: stage, clocks, members, counts, catch, find and trail come back. The raid
  day is counted from the day time, so sleeping, `/time set` and `/tick rate` behave.
- **Goals:** scout, raid and way-home cannot take an ant from each other; all three walk only through the navigator;
  the framework's `ScoutGoal` names no species; `Headway` sends nobody home, so it does not pull against the deadlines.
- **Dice and statics:** colony dice from `colony.random()` everywhere; no world state in statics.
- **Lag in the raid itself:** `Raid.update` runs at most once every 5 ticks however many raiders ask; nothing grows
  with the square of the count beyond a sort of 75.
- **Balance:** scout reach 96 matches the mound spacing; the search rings leave no gap.
