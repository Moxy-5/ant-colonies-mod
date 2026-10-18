# Brain step 4g: danger memory and neighbours (plan, 2026-10-17)

The user's decisions are in `docs/DESIGN.md` ("4g decisions"). In short: the memory fades over 2 to 3 days; danger is
any insect hurt or killed by a living thing (mobs or the player) plus hostile mobs seen close; old colonies yield to
neighbours, young ones push on (Gordon & Kulig); termites plug an attacked hole and close its gallery piece, then reopen.
Numbers marked "my pick" go in the species files and are to be confirmed by the user.

## What is there today
- `Patrol`: a patroller hurt, near a hostile mob (4 blocks) or near another colony's ants shuts its way **for the day**
  (`Patrol.danger`, `Patrol.neighbours`, called from `AbstractAntEntity.hurt` and `PatrolGoal.lookForTrouble`).
- `Alarm` (termites): a hurt member raises the alarm; workers seal a breach (`NestWalls.plugLeak`).
- `ForagingHoles.plug`: termites already plug holes at dawn and in an alarm (`holesToPlug`).
- Nothing is remembered past the day; numbers out of sight always come back.
- No colony age is saved (needed for "old colonies yield").

## Step 1: the danger memory (both species, no behaviour change yet)
- New `colony/DangerMap` (one per colony, saved): spots with a strength, a time and what it was (a mob type or "player").
  - A member **hurt** by a living thing that is not its own colony: +1 at the spot (my pick).
  - A member **killed** that way: +3 (my pick).
  - A **hostile mob seen** within 4 blocks of a surface worker (foragers, patrollers, scouts; checked once a second, cheap
    box search): +1, at most once a minute per mob (my pick).
  - Spots within 4 blocks merge (my pick). Strength halves every game day; below 0.5 the spot is forgotten. So a hurt is
    remembered about 1 day, a death about 2.5 days, two deaths about 3.5 days (the user's "2 to 3 days, longer with more
    deaths"). Fading runs while away (by the clock, nothing ticks).
  - Fall damage, fire, drowning, starving are not danger here (hazards are their own later feature).
  - `dangerAt(pos)`: the summed strength within 6 blocks (my pick); `isDangerous(pos)`: that sum at least 1.
- Hooks: `AbstractAntEntity.hurt` (next to the alarm and patrol calls), the workers' and termites' death, a cheap look in
  the surface goals. The patrol keeps its own day rule and also writes to the map.
- `/antcolonies list` gets a short "danger:" line (spots, strongest, what); the away report counts spots that faded.
- Species file: `danger` block (`hurt`, `killed`, `seen`, `merge_within`, `reach`, `half_life_ticks`, `forget_below`).
- Tests: a worker killed by a zombie and one hurt by a player in an arena: the spots, the strengths, the fade
  (`ageForTests`), saved and loaded.

## Step 2: harvester ants keep away (real *Pogonomyrmex*: the memory lives in the foragers and the trails)
- `TrunkTrails`: a trip whose trail passes a dangerous spot picks another trail (or none); with no traffic the trail weakens
  and fades by the rules it has. No new trail is laid toward a dangerous spot. A search (`pickForSearch`) skips such trails.
- `ForagerSpots`: an ant hurt at or near its spot drops it (as two empty trips do today) and searches another way.
- `ScoutGoal` and `FieldLife.searchOnce`: a search drifting into a dangerous spot turns away from it.
- `Recruits`: no recruiting to a patch inside a dangerous spot; recruits already on a line to one go home.
- Tests: in the seed scene, a worker killed at a trail's food: that trail's trips stop, the others go on; after the fade
  the trail is used again.

## Step 3: termites plug and close (real Macrotermes after a raid)
- An attack (a danger spot) within 4 blocks of a foraging hole or along its gallery piece (my pick): that hole is
  **plugged with soil** at once by the nearest workers (the `ForagingHoles.plug` they already do) and stays plugged; its
  piece is **closed**: not in the night's turns (`Galleries.pickTurns`), no tip grows toward the spot (`growTips`), hidden
  trips do not run through it.
- When the memory fades: the hole comes back into the turns (it is reopened as any rested hole is). If the spot was hit
  again and again (strength once over 6, my pick), the hole is left and the galleries open a new hole on the same piece at
  least 6 blocks from the spot (my pick).
- Tests: a minor at a hole killed by a zombie at night: the hole plugged and out of the turns; after the fade back in.
- **BUILT 2026-10-18** (`colony/ClosedHoles`; `docs/HISTORY.md`). Changed from the plan while building (my picks, to
  confirm): holes close within 6 blocks of an attack (the memory's `reach`, as far as the termites graze round a hole),
  not 4; only a hurt or a kill closes a hole, not a hostile seen; a hole is left for good when it lies within 4 blocks of a
  place once as strong as 6 (two deaths, or a death and a hurt, close together in time); the new hole is at least 7
  blocks from the place (past the closing reach, so it opens), on the left hole's gallery if it has room, otherwise on the
  nearest other gallery, never reached through the sealed stretch, never beside another hole.

## Step 4: harm out of sight (numbers can be lost)
- The numbers avoid dangerous spots by the same rules (they go by the same trails and holes).
- A number trip that still goes through a remembered danger spot (no other way, or a patrol checking it) is lost with a
  chance by its strength (my pick: 10% per point, at most 50%). Patrol numbers toward a dangerous way can be lost, as
  a real patroller can.
- The away report: "lost in danger".
- Tests: an `away` test: a colony with a strong danger spot on its only trail, away a day: some numbers lost, the trail
  used less.

## Step 5: neighbours
- **Meetings:** any surface worker (not only patrollers) within 3 blocks of another colony's insect, or walking onto
  another colony's trunk trail, records a meeting at that spot (direction from home, strength) in the same memory, kind
  "neighbour" (a meeting is not a danger: nobody is hurt, no harm to numbers). Termites: a gallery tip coming within 3
  blocks of another colony's gallery records a meeting there.
- **Yielding (Gordon & Kulig):** an old colony turns away after 1 meeting, a young one only after several (my pick:
  meetings needed from 4 at day 0 to 1 at day 8 and older; the colony's age is game days since its queen founded it,
  a new saved `bornAt`; older saves without it count from their first load).
- **Turning away:** harvester ants: trails in that direction stop growing (their tip held short of the meeting spot) and
  no new trail or search goes within 30 degrees of it (my pick), so the trails turn aside over the next days. Termites: the
  tip stops and the meeting cell is filled with soil (real: neighbouring Macrotermes wall off their galleries); no tip
  grows toward it. Fades like danger (my pick: a little slower, half every 2 days, as real borders last).
- No fighting (M8 builds fights on this).
- Tests: two harvester colonies about 30 blocks apart in one arena, both with trails toward each other: the old one turns
  away first, the young one later; two termite colonies' galleries stop short and the meeting cell is walled.
- **BUILT 2026-10-18** on branch `claude/pensive-moore-530c7e` (`colony/Neighbours`, `NeighbourTests`; `docs/HISTORY.md`).
  The user's answers before building: old at day 8, same species only, half every 2 days, 30 degrees. Changed while
  building (my picks, to confirm): the meetings are their own memory (`Neighbours`), not places in `DangerMap`; a colony
  that turned away stays away until the place has faded to half what it needed (else an old colony would yield for only
  about 90 seconds after one meeting); the termite "wall" packs the whole piece reaching toward the neighbour with soil
  back to its fork (as filling in does), the 4 arms only stop; the webs never join.

## Order, branch and tests
- One step at a time, each tested in-game by the user before the next. Steps 1 and 2 together are the first thing to see
  in game (kill an ant on a trail, watch the trail go quiet).
- New test class `DangerTests`, category `colony` in `TestFilter` (no new category). Run `colony,antnest,away` after
  ant steps, `termitefood,away` after termite steps; full run at the end (it changes shared systems).
- Branch: from `master` once `termites-underground` has its owed full run and is merged (user's OK), else from
  `termites-underground`.
