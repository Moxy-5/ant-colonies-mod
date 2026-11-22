# Oxbows and pans rework (the user, 2026-11-18: "there more like tiny ponds", "start measuring")

Today's oxbows and pans (`worldgen/FloodplainPoolFeature`) are tiny ponds: an oxbow is a curved ditch 3 to 5 wide and
2 deep on a loop 8 to 16 across, a pan a round puddle 5 to 12 across and 1 deep, both only on the floodplain and kept 8
blocks off the river. Real ones are much bigger, and hippos (river zones, `docs/DESIGN.md`) need water deep enough to
sink in. Animals are fun first, but these are the animals' homes, so they should look like the real thing.

## What the real ones are like (what we copy)

- **Oxbow lake:** a river bend cut off when the river took a shortcut. About as wide as the river that made it, a loop
  several river widths across, its two ends pointing back at the river with a thin neck of land between. Deep when new,
  then it silts up from the ends: open water, then a shallow muddy crescent, at last a green dry crescent in the grass
  (a "meander scar"). Found where the floodplain is wide and the river winds, not on narrow strips.
- **Pan:** a shallow round hollow on the open savanna, filled by rain, often far from any river. 20 to 100+ m across,
  deepest in the middle, a wide bare ring trampled by animals coming to drink, short grazed grass beyond. They fill in
  the wet season and dry and crack in the dry one (that waits for the seasons; for now they stay wet).

## Step 1, the measure (DONE 2026-11-18)

Test `SavannaWorldTests.oxbowAndPanRoomIsMeasured` (`worldgen`, measures only, about 4 minutes) reads the land from the
noise on the 3 ground seeds.

**Room for oxbows** (183 river banks with savanna beside them; floodplain width counted from the water's edge):

| How far inland | River width | Floodplain median | 25% | 75% | 90% | None | 48+ | 64+ | 96+ |
|---|---|---|---|---|---|---|---|---|---|
| Far inland (71 banks) | ~23 | 40 | 16 | 64 | 100 | 7% | 38% | 28% | 13% |
| Mid inland (67) | ~42 | 28 | 20 | 44 | 76 | 9% | 24% | 21% | 6% |
| Near inland (41) | ~66 | 24 | 12 | 48 | 72 | 24% | 27% | 12% | 7% |
| Coast (4, too few to trust) | ~53 | 44 | 24 | 124 | 124 | 25% | 25% | 25% | 25% |

The ground over the floodplain stays within a block for a median 12 to 20 blocks from the water, then steps up (the
floodplain is 1 block over the water out to about 12, then 2). A lake carved into it at the water's level does not
need it flatter than that.

**Room for pans** (300 dry savanna spots off the floodplain):

| Radius (pan across) | Ground within 1 / 2 / 3 blocks | No termite mound place within radius + 8 | Within 2 and clear |
|---|---|---|---|
| 10 (20 across) | 57% / 69% / 80% | 84% | 57% |
| 20 (40 across) | 36% / 49% / 58% | 53% | 26% |
| 30 (60 across) | 18% / 33% / 42% | 27% | 9% |
| 40 (80 across) | 10% / 22% / 31% | 5% | 1% |

Nearest river from those spots: median 64 blocks (25% 32, 75% 96).

**Today's pools** at 240 floodplain spots: today's oxbow would fit at 23 percent, today's pan at 53.

**What it means:**
- Real-size oxbows beside the wide rivers do not fit: a half-size oxbow (lake half the river's width, loop 1.5 to 2.5
  river widths across) needs about 35 to 58 blocks beside a far-inland river, 63 to 105 mid inland, 100 to 165 near
  inland; the floodplain is that wide at about 3 banks in 10 far inland, 2 in 10 mid inland, under 1 in 10 near inland.
- Pans up to 40 across fit well (a quarter to a half of open savanna); past 60 across the termite mounds (40 to 80
  apart) are in the way almost everywhere.
- Both are bigger than a chunk feature may write (today's cap is a 28-block box), so both become **structures**, like
  the termite mounds and the floodplain trees.

## The design (my picks, the user's calls marked)

**Oxbows:**
- Only where the floodplain is wide enough beside a bend (my pick; also the real rule, oxbows form on wide meander
  belts). Sized from the river (half real size), then shrunk to fit the room, never smaller than a lake 8 wide on a loop
  30 across; no oxbow where even that does not fit.
- Beside the river, on the outside of where the old bend ran: a crescent whose two ends point back at the river, a neck
  of floodplain 3 to 8 blocks wide between them and the river.
- Four ages (my picks): fresh 3 in 10 (open water, 4 deep in the middle, steep sides), silting 4 in 10 (3 deep in the
  middle, shallow muddy ends), old 2 in 10 (1 to 2 deep, mud all round), scar 1 in 10 (no water, a crescent of wetter
  green grass and mud patches). Fresh and silting ones are deep enough for hippos (3 deep or more).
- About one oxbow per 250 blocks of river bank where there is room (my pick, to check against the survey).
- Water at the river's level, a silt and mud bed, mud on the banks, algae in the shallows.

**Pans:**
- On the open savanna off the floodplain, at least 48 blocks from a river, clear of termite mound places by the pan's
  radius + 8 (my picks).
- 16 to 40 blocks across (most 20 to 30), deepest in the middle (2 blocks, 1 toward the edge), a wavy round shape (my
  picks).
- A bare trampled ring 3 to 6 blocks wide round the water (coarse dirt and mud, hoof-pocked), short grazed grass past
  it; the rim levelled where the ground is up to 3 blocks uneven.
- About one pan per 192 by 192 blocks of savanna (my pick), more far from rivers than near.
- Stay wet for now; filling and drying (cracked mud, the mud pit idea) wait for the seasons.

**The user's call:** the floodplain is narrow for oxbows near the coast. My recommendation: oxbows only where it is wide
enough (rarer near the coast, realistic). The other way, widening the floodplain everywhere, changes all the river land
and its loading speed, and touches the termite mound and tree surveys.

## Steps

1. **Measure** (done, above).
2. **Looks in the model viewer:** an oxbow at its four ages beside a piece of river, a pan with its rings, at the
   measured sizes, with a hippo-sized and a crocodile-sized box for scale; the user picks.
   **Built 2026-11-18:** `python tools/model_viewer.py oxbows` (`references/oxbow_viewer.html`); waiting for the user's look.
   **The user's look, 2026-11-18:** both liked, but too samey; now every oxbow and pan its own size and shape (oxbows
   0.6 to 1.35 times the plain size, 100 to 170 degrees round, squashed or drawn out, turned, one arm wider; pans an oval
   up to 1.45 long, turned any way, 2 in 5 two bowls grown together, sizes spread over 16 to 40, rings 2 to 7) and pan
   water cloudy clay brown (my picks); "Another one" rolls a new set, "Pans of every size" shows four together.
   In the game the water's colour comes from the biome, so brown pans need a small pan biome painted over each pan (as
   the river biomes give the river its colour); to settle in step 4.
3. **Oxbows in new land:** a structure (carved to the water's level, bed, banks, ages); today's oxbow feature removed;
   kept out of quiet savannas (`worldgen/BigSavanna` gate); trees and mounds kept off the water.
   **The user, 2026-11-22: "make it 16-32 with them being random so there different sizes and add them".** Built the
   same day as a feature, not a structure (16 to 32 across fits the 3 by 3 chunks a feature may write):
   `worldgen/OxbowFeature` (the viewer's shapes and ages, sized to the user's 16 to 32, open side to the nearest river
   water, neck 5 to 8, level floodplain 1 or 2 over the river, water at its lowest ground, banks cut to a block over the
   water within 4, sides and bed made solid so no cave drains it, no mound within 15, no tree foot within 3) on sites
   from `worldgen/OxbowSites` (floodplain chunks 14 to 22 blocks from the river, about 112 apart, 8 tries each);
   `[world oxbows]` log lines say where each went and why a site stayed empty. Flights on 3 seeds: 11 laid of 32 sites,
   16 to 26 across (the 27 to 32 ones need the widest floodplain, so rare), no stalls. The old small oxbow shape removed from `FloodplainPoolFeature` (it keeps the pans until step 4).
   Tests: `SavannaWorldTests` batch `savanna_oxbows` (one per age, 16, 20, 24 and 32 across) and the river banks test.
   Not yet: hippos in oxbows and the survey on real land (steps 5 and 6).
4. **Pans in new land:** a structure; today's pan feature removed; same gate; mounds and trees kept off.
   **Built 2026-11-22 as a feature** (`worldgen/PanFeature`, `PanSites`; 16 to 32 across, my pick to fit the 3 by 3
   chunks; details in `docs/HISTORY.md`). Flights: 2 laid of 17 sites, too few.
   **Next, agreed with the user 2026-11-22 ("I don't need them to be the perfect size ... it's okay if it's on a step or
   if it's misshapen, that's the beauty of natural generation"): trim, don't refuse,** for oxbows and pans: a part that
   would go past the chunks, into the river's bend, a tree, a log or a rock is left out (an arm ends short, the pan's
   edge goes round it, a pan shrinks to fit) instead of the whole thing being refused; uneven ground makes a step
   (water at the lowest, the high side a bank); kept only while most of it is left (my pick: about 60 percent), else
   nothing. Measure again with flights.
   **Built 2026-11-22:** flights on 3 seeds: oxbows 26 laid of 30 sites (15 to 28 across), pans 16 of 17 (17 to 24
   across: pans shrink to fit the chunks), no stalls. Next: the user's look in a new world (step 7), then step 5.
5. **Life in them:** algae; which animals may live there (fish and crocodiles in oxbows; pans with algae and snails,
   no catfish or crocodiles, my pick); every spawning change through the spawning check list in `CLAUDE.md`.
   **Built 2026-11-22:** river zones `OXBOW` (old still waters' shares) and `PAN` (snails, bullfrogs, fly clouds only);
   those three spawn in pans up to 24 over sea level; thin edge algae laid by the pan; 1 in 3 deep oxbows on the
   hippos' stretches get a pod. Details in `docs/HISTORY.md`. Next: papyrus and hippo grass (the user's idea, 2026-11-22).
6. **Check:** a survey test on real land (counts, sizes, depth, nothing in rivers or on mounds), the loading speed
   (`tools/flight.sh`), `worldgen,mound,trees,river` on 6 servers.
7. **The user's look** in a new world. **Done 2026-11-22: "they look great"** (after trim, don't refuse; the game first
   would not load because of the bull shark's model file, fixed).
