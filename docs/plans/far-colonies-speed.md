# Far colonies' speed (2026-11-09)

The user's ask: "optimizing all the colonies when there off screen in the backround or loaded in the chunk but far away".
The aim: colonies no player is near cost the server as little as possible, and nothing changes in what the player sees
or in how colonies live.

## How it was measured

A new measuring run, the **stand** (`tools/flight.sh 8675309 300 stand`): a headless server makes a new world from a
fixed seed, a pretend player walks to the termite colony nearest the spawn and stands 6 blocks from its mound with view
distance 12, as the user plays. Once the land in view is made and the far colonies have had 100 seconds to go over to
numbers, the stopwatch (`/antcolonies perf`) is averaged for 5 minutes and written to `run-flight/flight-report.txt`.
The same seed gives the same land and colonies, so a fix is measured by running it again.

The stopwatch was taught three new things first (measuring only):

- **Colonies by where they are**: near (a player within 64 blocks, real insects), far with their land loaded (numbers),
  land not loaded (numbers); how many and their brains' time, and each brain part's time filed under it.
- **Insects by kind, with "its colony far"** for a real insect whose colony lives as numbers.
- **Other work**: the rule checks, labels, dead mounds, new-land mounds and raid view, which nothing timed before.

And Java's own profiler can be switched on for a run (`FLIGHT_PROFILE=1 tools/flight.sh ...`, then
`python tools/film_profile.py server`), which names the slow lines of code.

One thing the pretend player is not: a real player. It counts as a watcher for the far rule and for what may be seen,
but nothing notices it as a body (no alarm, no stings, no seedlings sprouting round it).

## What the measure found

The stand had **13 colonies loaded** (view distance 12, this seed): **1 near, 12 far**. Before any fix:

| | ms a tick |
|---|---|
| Whole server | 8.38 (worst single tick 847) |
| Our mod | 4.89 |
| The near colony: its 50 real insects | 2.66 |
| The near colony: its brain | 1.94 |
| The 12 far colonies: their brains | 0.054 |
| The 12 far colonies: their 18 real queens and kings | 0.23 |
| Giant trees, dead mounds, new-land mounds, rule checks | 0.02 |

So, honestly: **the far colonies were already cheap**, 0.28 ms a tick for all twelve, about 3 percent of the server's
tick. The far rule and the spread-out brains of 2026-11-04 do their job. Four fifths of what was left was the far
colonies' queens and kings, the only real mobs a far colony still ran.

The big find was somewhere else. Java's profiler showed **42 percent of the mod's whole server time went to one small
question, "is this block food?"**: the species file names its food blocks as text ("minecraft:dead_bush"), and that text
was read and looked up again on every single question, thousands of times a second (every block a forager looks at for
grass to clip, every column of ground the termites' tunnel planner reads). It hit the colony the player stands at
hardest: its termites re-plan their tunnels toward known food every 10 seconds, and that one check froze the server
for about a third of a second each time (the 847 ms worst tick). A near colony 60 blocks off that the player is not
looking at pays the same, which is the "in the background while I am busy at another colony" half of the ask.

Also found: a far termite queen, still a real mob, kept laying real eggs; the far colony folded each into its numbers
within 20 seconds and wrote a line to the log every time (about 36 lines a minute for 12 colonies, for ever).

## The fixes, in the order built (each measured with the same stand)

Nothing in this list changes what a colony does: each gives the same answers quicker, or stops doing a thing twice.

1. **Block names remembered.** A species file's block name is worked out once and kept
   (`SpeciesProfile.PlantFood.blockTest`). Same answers. *Our mod 4.89 to 2.96 ms; the near colony's brain 1.94 to
   0.49; worst tick 847 to 205 ms.*
2. **The tunnel planner reads each column of ground once a plan** (it read it again for every neighbour), keeps its own
   lists in maps that spread the columns properly, and a plant's wanted state ("growth: dry") is kept as a plain copy
   (`TunnelRoute.plan`, `PlantFood`). Same plans. *Colony brain 0.54 to 0.33 ms; worst tick 205 to 86 ms.*
3. **A far queen lays no real eggs** (`QueenCare.layInStream`): the away brain already lays that same egg as a number
   by the same clock. *Fold lines in the log: 53 in 8 minutes to 0.*
4. **A far colony's queen and king rest** (`AbstractAntEntity.restsWhileColonyIsFar`): while their colony lives as
   numbers their body, legs and goals do not run, and they carry on the moment a player is within 64 blocks. *The far colonies' real mobs 0.23 ms to 0; insects ticking 68 to 50.*

Tried and taken out again: the numbers' meals and old age looking only at who is due each second (they copy the whole
list of ages). It was my guess for what a colony in unloaded land spends its time on; the same flight before and after
showed no gain beyond the noise (6 colonies: 0.045 to 0.053 ms before, 0.040 to 0.045 after), so it is not in.

## Before and after (the same stand, 5 minutes)

| ms a tick | before | after |
|---|---|---|
| Whole server | 8.38 | 5.74 |
| Worst single tick | 847 | 85 |
| Our mod | 4.89 | 2.29 |
| Colony brain, all colonies | 2.00 | 0.31 |
| The near colony's brain | 1.94 | 0.26 |
| The 12 far colonies, brains and real mobs | 0.28 | 0.05 |
| Insects (the near colony's 50) | 2.66 (68 ticking) | 1.94 (50 ticking) |

**Colonies in unloaded land** (a flight east over the same seed, 6 colonies left behind): 0.045 ms a tick together,
0.0075 ms each, about twice a far colony whose land is loaded. Not made cheaper here: what it is spent on is not known
(at this size the profiler catches no samples of it).

## Could anything change in-game?

- Fixes 1, 2, 3 and 5: no. Same answers, same plans, same eggs, same meals.
- Fix 4: no. The resting queen and king stand where they are, more than 64 blocks from any player, which cannot be
  seen. Only their body, legs and goals rest: the queen's own once-a-second care still runs (she still swells with her
  colony and still dies on time if starved), found by the game test written for this fix.

## What is left (not built; the user's call)

- **The near colony's own insects are now the biggest cost by far**: 39 termite workers take 1.75 ms a tick (0.045 ms
  each; the old target was 0.02). The profiler names where: every idle worker asks again and again "is there grass to
  clip?" (13 percent of the mod's time), "is there a leak to seal?" (7), "is there a mound job?" (6), each walking over
  the nest's whole plan. These are near colonies, so outside this task; they would be the next speed step.
- **The near colony's 10-second tunnel check** still takes about 40 ms once every 10 seconds (0.19 ms a tick on
  average). It re-plans up to 6 tunnels to food the workers do not allow yet. Remembering a plan until the galleries
  change would remove it, but that is a new rule with its own ways to go stale, so it is left.
- **Colonies in unloaded land** each cost a little every second for ever (the numbers above). A world explored for
  many hours knows hundreds of them (300 would be about 2 ms a tick). The right next step is to measure it properly:
  a soak or a long flight that leaves a few hundred colonies behind, with the profiler on. The cut to small colonies
  (the other session) may change it too; unsure.
