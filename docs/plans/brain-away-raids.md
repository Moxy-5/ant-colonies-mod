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

## Step 3, built 2026-11-11

- **A raid out as numbers has a place.** From its own times (`RaidsAway.where`): mustering in the nest, so far along
  its way out, at the spot, or so far along its way home; the place is that point of its scent trail (or the scout's
  line; `Raids.wayTo`). A watcher within 64 blocks of it watches the colony (`Raids.outThere`).
- **Shown as real ants** (`RaidsAway.show`, `Raids.showRaid`, `RaidColumn.startPartway`), once a second while the
  colony is here: when a watcher is within 64 blocks of the raid's place and none within 50 of either end of the column
  (insects are drawn to 48 blocks, so nobody sees them appear; my pick), the raiders come out of the numbers on the
  trail, up to 75, in marching order. Not attacked yet: marching on from there. Already attacked as numbers: regrouping
  at the spot or walking home, the catch already in their jaws, the fight's losses counted; the fight is not played
  again (my pick). Just set out (within 8 blocks of the nest): the column forms in the nest's rooms as a real raid
  does. Nearly home after its attack: it walks in unseen, as numbers.
- **Back to numbers from where it is** (`RaidsAway.fromColumn`, `RaidColumn.shareOut`): a real column whose colony
  goes away goes on from its real place (it was "half the march" before, and a column on its way home began the whole
  walk home again).
- **The nest keeps that many fewer real ants while a numbers raid is out** (the raid's ants are out, not in the
  rooms; `ColonyHooks.HIDDEN_AWAY`, answered by the raids part).
- **No more skipping a feeding spot a watcher stands at** (`RaidsAway.playerNear` removed): the raid going there is
  shown on its way instead. `raids.away.player_near` in the species file is no longer read.
- **Known limits:** checked once a second, so flying fast through the band between 50 and 64 blocks can pass a raid
  unseen; a watcher nearer than 50 to where the raid is when the colony becomes watched sees no ants (the raid goes on
  as numbers; ants appearing in plain sight would be worse); BUGS 202 still open (a shown raid arriving where no real
  termite stands comes home empty).
- **Tests** (`away`): `aRaidOutAsNumbersIsShownOnItsWayOutWhenAWatcherComesNear` (a marching column of real raiders,
  none within 50 of the watcher, counted once in the day), `aRaidOutAsNumbersIsShownWithItsCatchAfterItsAttack`
  (regrouping with the catch in their jaws, home, numbers again, the catch on the pile). `away,raids` 35 of 35 on 6
  servers; full run 213 of 215 on 6 servers (23 minutes), the two failures old flakes, both 3 of 3 alone (BUGS 22, 93).

## Step 4, built 2026-11-11

- **A scout out as numbers has a place** (`RaidsAway.scoutsNow`): each of the scouts the colony would send keeps its
  own way out (route fidelity, as the real scouts' `ScoutMemory`) and walks out to the colony's reach and home again at
  the numbers' pace, trip after trip, each scout at its own point of its trip. Worked out from the game time and the
  colony's id alone: nothing kept, nothing saved (fewer rules). Only while the colony would send scouts (the scouting
  hours, dry, hungry, no find worth a raid, no raid out) and only 16 or more blocks from the nest.
- **Shown as a real scout** (`RaidsAway.showScouts`, `Raids.showScout`): a watcher within 64 blocks of that place and
  none within 50 makes the colony watched; in the same second one major comes out of the numbers on the ground there,
  with that way out as its own, already one of the colony's scouts out. The existing scout behaviour takes it within a
  second and goes on with the **real search** from where it stands, so what it finds is what is really there; the dice
  stop the moment the colony is watched (they only ever ran while it was away).
- **Places that cannot be shown do not count** (a watcher within 50, land not ticking): otherwise a player standing
  near one would hold the colony "watched" with nothing to see and its raids as numbers stopped (my pick).
- **Known limits:** as step 3 (once a second; no ants appear within 50 blocks). The number scout's place is a straight
  line out and back; the real scout then searches on its winding path.
- **Test** (`away`): `aScoutOutAsNumbersIsShownWhenAWatcherComesNear`: a real major 1 to 2 blocks from the number
  scout's place, 53 to 56 from the watcher, 20 to 31 from its nest, scouting, and it walks on (3 of 3).
