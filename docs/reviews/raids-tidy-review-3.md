# Raid code review 3, the tidy-up (2026-11-07)

The user's ask: "look over, examine and tidy up the raiding code, be very careful, think of everything or what could
affect other behaviours". Branch `claude/raid-tidy` (from `claude/raid-queue-openings-307d34`, commit 58ebf060).

## What was read

Every raid file, start to end: `Raids` (the colony's raid brain: scouts, finds, the raid day, starting raids),
`RaidColumn` (one raid out: forming, the ribbon march, gathering, the attack, collecting, spreading out, regrouping,
the walk home, rescue), `ScentTrails`, `WayHome`, `RaidsAway` (raids out of sight), `RaidCatch`, `RaidGround`,
`RaidCost`, `RaidRules` (the test bench's raid rules), the goals `MatabeleScoutGoal`, `MatabeleRaidGoal`,
`BringPreyGoal`, `MatabeleBiteOutGoal`, and the raid parts of `MatabeleEntity`; about 8,100 lines.

## What else the raid code touches (checked for side effects)

- **Fights** (`FightGoal`): the column hands out fight orders it owns; only it ends them, and the nest's defence keeps
  off a raider holding one (`Raids.keptFromDefence`, the colony question `keep_from_defence`). Sound.
- **Nest defence** (`NestDefence`): a scout out searching is never drafted; raiders milling at the entrance may be, and
  rejoin. Sound.
- **Eating** (`EatGoal`): an ant scouting, raiding or walking home from a raid never leaves to eat
  (`isOnColonyErrand`). Sound.
- **Rescue** (`RescueGoal`, `answersHelpCalls`): a raider in a column does not answer a hurt nestmate's call (the
  column fetches its own hurt). Sound.
- **Legs** (`AntNavigator.setSideShift`, `setPace`): only the raid goal sets a lane; its `stop()` always clears it, so
  no other goal walks with a raid lane left on. Sound.
- **Staying in** (`mayGoOut`): a Matabele leaves the nest only scouting, raiding or walking home from a raid. Sound.
- **Carrying prey home** (`BringPreyGoal`): waits until the ant has left the column. Sound.
- **Strays** (an insect stuck 3 minutes out of sight folded into the numbers): the raid brain drops a raider or scout
  that is gone (`checkRaiders`, `sweep`). Sound.

## Findings

1. **A small bug, fixed: on the walk home, ants still in the line were sent to the chimney hole.** When the front of
   the column reached the nest, every ant still walking in line behind it was told to walk to the entrance itself; in
   a dead mound that is the chimney's open mouth (the code's own note says there is no place to stand there, BUGS 85).
   They were redirected to the doorstep a moment later once close, so it rarely showed, but a far one could plan a way
   toward the hole. Now they are sent to the doorstep (the first cell beside the entrance), as the ants near home
   already were.
2. **Diary words on a Turkish-language PC.** The raid's stage names were lowercased with the PC's language rules
   ("ATTACKING" becomes "attackıng" in Turkish), which the diary, the test bench's rules and their reports read. Now
   lowercased the same on every PC. Nothing changes on the user's PC.
3. **The walk-line code (`Route`) lived inside the 2,600-line column file**, and the walk home (`WayHome`) reached into
   the column for it. Moved into its own file, `colony/Route.java`, with its two place helpers (R9's "`Route` in its own
   file"; the first half of R10).
4. **The carry numbers' defaults sat in the column** (`RaidColumn.DEFAULT_CARRY`) while every other raid numbers record
   keeps its own (`DEFAULT`); three other files reached into the column for them. Moved to
   `SavannaNumbers.CarryNumbers.DEFAULT` (R9's "every numbers record with a default"); the same numbers.
5. **Short ant names in the diary written out by hand 50 times** (`id.toString().substring(0, 8)`: `Raids` 25,
   `RaidColumn` 20 plus its own copy of the helper, the bite-out goal 4, the soak 1): one helper, `RaidGround.shortId`.
   The bite-out goal's score in the diary is now written the same on every PC too (finding 2).
6. **Three comments stuck to the wrong code** (in `Raids` and `RaidsAway`, left over from code moved earlier): put
   back above what they describe.
7. **`MatabeleEntity` asked "is it on a raid errand?" twice in the same words**: one helper now.
8. **Long written-out names** (`com.antcolonies.savanna.colony.WayHome.nearEntrance`, `java.util.Comparator`, ...) in
   the raid files and `MatabeleEntity`: imported once at the top instead (about 95 in 12 files). The raid's two
   "the raid column's update failed" lines now go to the savanna mod's log like every other `[raid]` line (they went
   to the framework's; both land in the same log file, `latest.log`, so nothing reads them differently).

Looked at and left as they are (sound): the spreading-out stage leaves without ending its fight orders, but those run
out by themselves within the spread time and the next stages wait for a raider still fighting; the column ends a hurt
raider's wait whenever it leaves the spot; the dusk and rain turn-home; the raid day count shared near and away; the
scent trail's breaks and mends; the cost stopwatch always stopped (`try`/`finally`).

## Left for later (R9 and R10 in `docs/plans/m8-matabele.md`)

- **The column in stage pieces** (R9's main part): the stages share about 80 pieces of the column's state (who is in
  it, each raider's place, the gap being cast at, the hurt waiting, the diary), so a split is a rewrite of how they
  talk, not a move; it needs its own careful step with the raid tests, not a tidy.
- **The timings into the species file** (R9): about 40 of the column's fixed numbers (paces, gaps, waits) could move
  into `megaponera_analis.json` for tuning; worth doing once the user wants to tune them.
- **Trails into the framework** (R10): `Route` is now in its own file, ready to move with the trail store, the
  stand-here rule and the walk home when army ants or leafcutters need them.
