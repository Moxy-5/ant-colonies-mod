# The brain while away: honest sizes and raids that show (planned with the user 2026-11-11)

The user, before M8.5: "the brain works but it's just janky". Raids that are out when the player comes near should
show; a Matabele colony is at most 100 ants and a termite colony 200; scouts out there should show too (the user's
add, same day). Branch `claude/brain-away-raids`.

## What was found (read in the code, 2026-11-11)

- The size limits were only planned: the species files still said 2,500 Matabele, 50,000 termites, a counted termite
  standing for 1,000.
- "Real ants or numbers" is decided by one test: a watcher within 64 blocks of the **nest's middle** (`AwayLife`).
  A raid lives up to 96 blocks from the nest.
- A raid that set out as numbers never turns real: `RaidsAway.tickHere` finishes it "on its own time".
- A numbers raid skips a feeding spot with a watcher within 64 blocks ("left for real ants"), but with the nest more
  than 64 blocks away there are no real ants: that spot is never raided while the player stands at it.
- The same for scouts: `Raids.tickAway` clears the real scouts, the numbers scout by dice once a game hour.

## Steps (one at a time, each with its own tests)

1. **Honest sizes.** Matabele at most 100 workers, termites at most 200, a counted termite is one termite
   (`stands_for` 1). A raid is at most 75 ants, all real near the player. The food made to fit (below).
2. **One rule for "is this colony watched".** A colony is here when a watcher is near its nest **or near its raid or
   scouts**, wherever they are. A feeding spot is no longer skipped because a player stands at it.
3. **A numbers raid turns real when a watcher comes near.** The brain knows when the raid left, attacks and is home,
   so where on its trail it is. Within about 64 blocks of that point the raiders are put on the trail there, out of
   sight, in the right stage (marching out, fighting, walking home laden). Leaving turns them back into numbers
   (`RaidsAway.fromColumn`, already there). Ants walking home into land not loaded fold into the numbers at its edge.
   `RaidColumn` needs a way to start partway. A full run after this step.
4. **Scouts that show.** A numbers scout out searching becomes a real scout when a watcher comes near where it is;
   the dice find is replaced, for a watched scout, by the real search.

## Step 1, the numbers (my picks; checked against each other, then measured in a soak)

| What | Was | Now | Why |
| --- | --- | --- | --- |
| Matabele `max_workers` | 2,500 | 100 | the user |
| Matabele new-land colony | 100 to 2,300 | 30 to 100, small ones common | the real spread (Yusuf 2026) shrunk to the limit |
| Matabele `real_cap`, `crew` | 50 | 75 | a raid of 75 all real (the user, 2026-11-09) |
| raid `most_real`, `most_raid` | 50, 600 | 75, 75 | the same |
| scouts | 1 per 50 workers, most 5 | 1 per 20, most 5 | a colony of 100 keeps its 5 scouts |
| termite `max_workers` | 50,000 | 200 | the user |
| termite `stands_for` | 1,000 | 1 | a counted termite is a termite |
| new-land termite colony | 20 to 700 by its mound | the same rule, at most 200 | stage 15 and up hold 200 |
| termite galleries | 1 block per 4 workers | 1 block per worker | a full mound's 200 workers must still tunnel to its dead tree 40 blocks out (the old 700 had 175 blocks; with 1 per 4 the new-land test found the tree out of reach); colonies under about 60 keep their 4 first arms as before |
| new holes' reach | full at 500 workers | unchanged | tried full at 200, taken back: it changed small colonies' tunnels |
| one termite feeds | 1 ant a day | 5 ants a day | see below |
| numbers raid's catch | 1.5 a raider, most 1,000 | 0.3 a raider, most 30 | what real ants bring home from a shown raid |

**The food.** At one termite an ant a day, 100 ants eat a 200-termite mound in two days. Real Megaponera take a few
termites an ant a raid from colonies of millions; the mod's mound has 200. So the food is the one place that cannot be
real: one termite now feeds 5 ants for a day (`termites_per_layer` 4, `workers_per_piece` 20). A colony of 100 needs
20 termites a day: one or two raids (a shown raid stings what real termites are at the spot, about 6 to 20; a numbers
raid of 30 to 75 brings 9 to 22, never more than a tenth of the termite colony). The termites keep off a raided spot
a few days, which is the brake. A known departure from the real ants, written in `docs/DESIGN.md`.

**To measure, not guess:** whether a 200-termite colony holds steady under one or two Matabele colonies (its queen's
laying against old age and raids). A soak of 10 game days after the change, sizes by day (`tools/soak.sh`).
