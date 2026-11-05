# Raids and fights, review 2 (2026-11-04)

The user's in-game test of the raids (2026-11-04, `/antcolonies scout` near a Matabele dead mound) ended in a crash, and
before it the user saw "column stuck or messy" and "fighting looked wrong". The user asked for "a full code review of the
raid behaviour and fighting while raiding specifically", the way a senior Minecraft coder would: looking for behaviours
that step on each other and for finicky code. This file lists what was found, most serious first. The first review is
`raids-and-scouting-review.md` (its findings 1 to 18 were fixed before this one).

Read: `colony/Raids`, `RaidColumn`, `ScentTrails`, `WayHome`, `RaidGround`, `MatabeleRaidGoal`, `FightGoal`, `Fights`,
`EatGoal`'s start rule, `RaidDefence` (the termites' seals), and the user's game log (`[raid]` lines) and crash report.

## In plain words

- The game crashed in the termites' fungus garden, not in the raid (fixed, BUGS 95).
- The raid column moved in jerks because each ant followed the ant "ahead of it" in the order they were called, not the
  ant really ahead of it on the trail (fixed).
- In the user's dead mound, 5 recruits were given milling places down in the mound's open pit, stood exactly there, and
  were left home for "not being out" (fixed).
- The attack counted termites hidden in their nest or shut in under the logs, which no raider goes for, so it went on
  with nobody fighting until dusk (fixed).
- Smaller things are listed at the end, left for later with the reason.

## Findings

### 0. The crash: forage put on fungus comb with no floor (fixed, BUGS 95)

Not raid code, but it ended the user's raid during its pick-up. A termite worker put forage on a garden cell whose comb
had no floor under it; the comb fell away in the same moment and the game read a heap that was no longer there. Fixed
in `FoodStore` (a heap is only put where it can stand) and `FungusCombBlock.laid` (reads the block again). Test:
`termiteWorkersEatNodulesWhenYoungAndOldCombWhenOlder` now also builds floorless comb (failed on the old code).

### 1. The march: each ant followed the wrong ant (fixed; "column stuck or messy")

`RaidColumn.march` gave each raider a place a gap behind "the ant ahead of it", taken from the order the raiders were
called (scout, former scouts, then the rest, the majors last). That order was never matched to where they really were on
the way out (it was on the way home, R7). The ants came out of the chimney in any order, so an ant could be ahead on the
trail of the ant it "followed": its place came out behind itself, so it stood still until that ant walked past it. Many
ants did this at once: the line moved in jerks, ants overtaking ants and stopping dead. The side-by-side lanes were
taken from the place in the line too, so once sorted the ants would have stepped from side to side each time two passed.

Fix: the line is sorted by where each ant really is along the trail at every update (the scout still leads), and each ant
keeps its own lane (from its id). Real columns: the scout leads, the rest fall in as they come out (Longhurst and Howse
1979; Frank et al. 2017 on the scouts in front); no strict order behind.

### 2. Milling places down a dead mound's pit (fixed; the user's 5 left home)

The log: `forming: 48b54fbf stays home, not out at the entrance in time: at -23, 69, 149, its place -23, 69, 149 (0.0
off)`, with the entrance at y 72. `form()` asked for a standing place round the entrance "near its height", which looks
up to 3 blocks down. A dead mound's open chimney is a pit a few blocks across, so some places were 3 blocks under the
entrance, in the pit; the "is it out?" check wanted an ant no more than a block under the entrance. The ants did exactly
what they were told and were left home for it (one more was given no place at all and stayed in the rooms).

Fix: a milling place is only on open ground, no more than a block under the entrance and never inside the nest; farther
out is tried, then the start of the trail. Test: the whole-chain raid test from a dead mound checks that no place is given
in the nest or under the entrance and nobody is left home at its place (`RaidColumn.formFaults`).

### 3. The attack counted termites nobody goes for (fixed; "fighting looked wrong")

The attack ends 30 s after the last termite "about the spot" (R7). That count took every living termite within 10
blocks and 6 up or down, including termites in their own nest past the feeding ways and termites shut in under mud, soil
or wood. `FightGoal` leaves both alone (raids hit feeding sites; only a major breaking in goes after shut-in ones). So
once the termites had fled down their holes the count never fell to zero: the raiders searched round on their own with
nothing to find until the attack's longest time. In the user's raid the attack ran from 18:20 to dusk at 19:30.

Fix: the count follows the fighters' own rule: a termite in its nest never counts, one shut in counts only while a major
still holds its fight order (still breaking in). Test: the whole-chain raid test checks the attack ends before its
longest time.

### 4. Checked, no problem found

- **Eating on a raid:** `EatGoal` does not start for an ant on a colony errand (raiders, scouts): no ant leaves a column
  to eat (first review, finding 3, still holds).
- **Defending the nest while raiding:** scouts out and raiders on the column's own fight are never drafted; raiders
  milling at the entrance may be and rejoin (first review, finding 4, holds).
- **The column vs the fight goal:** `MatabeleRaidGoal` stands aside while its ant has a fight spot; the fight goal has
  the higher priority; an ant held by a fight, a pick-up, eating or casting is never dropped as "stuck".
- **Termite seals vs majors breaking in:** a seal is packed once, 4 blocks back; a major bites at most 6 blocks; no
  endless bite-and-refill.

### 5. Smaller things, left for later

- **A fight with no way to its spot wanders round where the ant is** (`FightGoal`, "no way to the spot: it searches round
  where it is"). With the column bunched 4 blocks short of the find this is rare in a raid; for a straggler far behind it
  would be better to walk the trail up. Left: no case seen.
- **Fighting back on the march:** an ant hurt on the march fights back with no fight spot; that does not count as "held",
  so a long chase could see it dropped as stuck after 15 s (it then goes home along the trail, no harm). Left: real ants
  that get stung do drop out.
- **The scout command's message** says "Its find raids at once, whatever the hour" also when a raid is already out (the
  log: "a scout is home ..., no raid: one is out already"); the find is kept and raided later, so only the words mislead.
  Left: words only.
- **BUGS 93** (the whole-chain test's own moments, open) is unchanged by this review.
