# Matabele colonies uncommon, not rare (planned 2026-11-06)

The user's report (2026-11-06): "matabele dead mounds are no longer showing up". The log of their world (New World
(161)) showed nothing broken: 44 mounds made, 13 dead, 6 of those with a Matabele colony, and the ants came out and
scouted. They are just rare (about 1 mound in 7 to 10), so near spawn there was one. The user's answer: "more common,
but not too common: uncommon, not rare".

## Why they are rare today

A mound of new land is dead by its dice (half of them). A dead mound then gets Matabele by a second dice
(`MatabeleColonies.SHARE`, 2 in 3), but only if a mound living by its dice has its foot within 70 blocks
(`MoundPlaces.LIVING_NEAR`, checked again at put time by `NewLandMounds.ANTS_NEAR_LIVING`); a Matabele dead mound with
no living mound near turns into a living one instead (BUGS 104). On Minecraft's rolling savanna only about 1 place in
3 holds a mound, so most dead mounds have no living one within 70, and most of the would-be Matabele mounds end up
living. Result: about 1 mound in 10 holds Matabele.

## Why it broke twice ("the code must be finicky")

Both times a change to how many mounds stand where (3F's spacing, then the ground rules) changed how many living
mounds stand near dead ones, and the Matabele rule leans on that without anyone noticing. Two weak spots:
1. **The guard test's floor is too low.** The survey test (`MatabeleNewLandTests.deadMoundsOfRealSavannaHaveLivingTermitesInReach`)
   only asks for 8 Matabele mounds in its survey of 140 mounds; today it finds 14, so they could get rarer still
   without a failure.
2. **The same rule lives in two places with two numbers.** World making asks `MoundPlaces.LIVING_NEAR` (70, foot to
   foot); the server asks `NewLandMounds.ANTS_NEAR_LIVING` (70, middle to the living foot). Change one and not the
   other, and colonies are quietly turned away ("no living termite mound within 70 blocks" in the log).

## Measured (2026-11-06)

A one-off measuring test over the survey's real land (5 world seeds, 8 savanna stretches each, 140 mounds; Minecraft's
own terrain from the noise), trying the dials together. Share = the part of all mounds holding Matabele.

| Setting | Living | Matabele | Empty dead | 1 mound in |
|---|---|---|---|---|
| Today (dice 2 in 3, within 70) | 71% | 10% | 19% | 10 |
| Dice 0.85, within 70 | 79% | 14% | 7% | 7.4 |
| Dice 2 in 3, within 90 | 64% | 17% | 19% | 5.8 |
| **Dice 0.85, within 90 (my pick)** | **71%** | **21%** | **7%** | **4.7** |
| Dice 1.0, within 90 | 76% | 24% | 0% | 4.2 |
| Dice 0.85, within 110 | 69% | 24% | 7% | 4.1 |
| Dice 0.85, within 90, dead mounds turned living counted as neighbours | 64% | 29% | 7% | 3.4 |
| Dice 1.0, within 110, counted | 61% | 39% | 0% | 2.6 (common) |

In the same land the part of the savanna within 200 blocks of a Matabele mound goes from about a quarter today to
about half with my pick (the stretches are small, so the real numbers are better than these; only the change counts).

## The target (my pick)

**About 1 mound in 4 to 5 holds Matabele (18 to 30 percent of mounds), about twice today.** Still fewer than the
living termite mounds (about 7 in 10), still a few empty dead mounds (about 1 in 14) for the look of an old, abandoned
mound. That is "uncommon": flying across a savanna you pass a few; standing anywhere, one is often within a couple of
hundred blocks.

Real: dead Macrotermes mounds among living colonies are prized homes (warthogs, porcupines, many insects; almost half
of 73 Matabele nests in a Nigerian survey were in deserted *M. bellicosus* mounds), and Matabele forage out to about
95 m (Yusuf 2026), so a dead mound with termites within about 90 blocks is very likely taken. Raising the dice and the
reach follows the real animal.

## Steps

1. **One number, one place.** `LIVING_NEAR` and `ANTS_NEAR_LIVING` become one number, measured one way (foot to
   foot), in the structure's data file next to the other mound numbers (`worldgen/structure/termite_mound.json`, a
   `matabele` block with `share` and `living_near`), read by both world making and the server, with
   `MatabeleColonies.SHARE` moved there too. The user can then tweak them without code. Nothing changes in-game.
2. **The new numbers:** the dice 2 in 3 to 0.85, the reach 70 to 90 (my picks, from the table).
3. **The guard test checks the target band.** The survey test fails if Matabele mounds fall outside 18 to 30 percent
   of the survey's mounds (too rare or too common), with a message naming the numbers to look at, and if any
   would-be Matabele mound is turned away at put time (the two checks disagreeing). Any later change to mound spacing,
   ground rules or the dice that makes them rare again fails this test in the next run.
4. **Balance check.** `tools/raid_balance.py --within 90` (run while planning, 2026-11-06): a Matabele nest's nearest
   termite feeding spot a median 55 blocks (was 51), 9 in 10 within 80, about 1 nest in 20 with none within 90 (was 1
   in 40). Scouts reach 96, so a few new-land colonies may find no spot and starve; that is the gap moving house
   (M8 step 6) fills. Run the model again after step 2 and say what fits and what does not.
5. **Tests:** the survey test, `colony,raids,mound,away` on 6 servers.
6. **In-game test (a new world):** fly across a savanna; `/antcolonies newmounds` says how many mounds and how many
   with a Matabele colony; `/antcolonies list` gives each colony's place. Old worlds keep the mounds already made.

## Your calls

None beyond the target itself (you asked for "uncommon, not rare"; 1 in 4 to 5 is my reading of it). Only new land
changes; land already made keeps its mounds.
