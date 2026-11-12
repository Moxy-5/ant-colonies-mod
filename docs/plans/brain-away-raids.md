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
| one termite feeds | 1 ant a day | 20 ants a day (5 at first) | see below, and the soak |
| numbers raid's catch | 1.5 a raider, most 1,000 | 0.1 a raider, most 10 (0.3 and 30 at first) | what a mound of 200 can spare |
| workers' lifespan, both species | 15 game days | 60 | at 15 a colony of 200 lost 13 a day to old age |

**The food.** At one termite an ant a day, 100 ants eat a 200-termite mound in two days. Real Megaponera take a few
termites an ant a raid from colonies of millions; the mod's mound has 200. So the food is the one place that cannot be
real: one termite now feeds 5 ants for a day (`termites_per_layer` 4, `workers_per_piece` 20). A colony of 100 needs
20 termites a day: one or two raids (a shown raid stings what real termites are at the spot, about 6 to 20; a numbers
raid of 30 to 75 brings 9 to 22, never more than a tenth of the termite colony). The termites keep off a raided spot
a few days, which is the brake. A known departure from the real ants, written in `docs/DESIGN.md`.

**To measure, not guess:** whether a 200-termite colony holds steady under one or two Matabele colonies (its queen's
laying against old age and raids). A soak of 10 game days after the change, sizes by day (`tools/soak.sh`).

## Step 1, what the soak found (2026-11-11)

The first 10-day soak: every far colony died, Matabele near the player starved, full mounds slid from 220 to 145.
Found, with the new `soak-others.txt` (a line a day per far colony):

- Far termite colonies never foraged: the brain remembered no plant (it looked only while "here") and had no hole to
  graze round (holes are laid out along tunnels real workers dig). Fixed (BUGS 203; `FieldLife.lookOnLeaving`,
  `lookAway`, and where no plant can be worked the colony forages by the land,
  `forageByTheLand`). The 100-day soak of 2026-11-06 shows the same on the old code.
- Far Matabele found no termites (the number scouts read tunnels planned only near a player). Fixed (BUGS 204).
- A scout home in a tall dead mound never told of its find. Fixed (BUGS 201).
- Old age: 15-day lives cost a colony of 200 13 workers a day. Now 60 days.
- The food: 1 to 3 termites made no pile layer; two far colonies took 116 termites from one mound in 5 days. Now a
  termite is a layer and feeds 20 ants; a numbers raid takes at most 10.
- Open, BUGS 202: a shown raid to a feeding place with no termite at it comes home empty.
- Far Matabele beside a termite mound whose colony is not made yet (it is put in when a player first comes near)
  hunt by the land (`RaidsAway.noTermitesOnRecord`).
- Measured after: far termite colonies 20 of 20 alive after 8 days (0 of 20 before), steady where not raided.

## Step 2, built 2026-11-11

- **The rule:** a colony is watched when a watcher is within the far rule's reach (64 blocks, kept till 80) of its
  nest's middle **or of any of its real ants out on a raid's business**: the raiders of the column out, the scouts out,
  the ants walking home by themselves (`AwayLife.playerNear`, the new neutral socket `ColonyHooks.OUT_THERE`, answered
  by `Raids.outThere`). Before, a player who followed a raid more than 80 blocks from its nest saw the column handed to
  the numbers and its ants leave the world a minute later; a scout the same.
- **Left for step 3 (my pick, it differs from the list above):** a raid of numbers does not count as "out there" yet,
  and a numbers raid still skips a feeding spot a watcher stands at. Both only make sense once a numbers raid can turn
  real (step 3): until then, counting its point would make the nest's ants real with nothing to see, and dropping the
  skip would let a raid happen unseen in front of the player (the rule of 2026-11-08: no hidden raid where one watches).
  Step 3 adds the numbers raid's point on its trail to `Raids.outThere` and drops `RaidsAway.playerNear`.
- **Side effect:** while a player stands by a raid far from its nest, the nest's ants stay real too (up to 75, not seen;
  the same cost as standing by the nest).
- **Test:** `aWatcherNearAnAntOutInTheFieldWatchesItsColony` (`away`): a watcher 84 blocks from the nest but near an ant
  walking home keeps the colony here past the grace; once the ant is home the colony goes over to numbers.
