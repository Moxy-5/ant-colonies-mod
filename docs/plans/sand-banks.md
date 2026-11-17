# Sand banks on the savanna rivers (planned 2026-11-16; steps 1 to 4 BUILT the same day on `claude/wide-rivers`)

The user's ask (2026-11-16): sand banks in only some parts of the river, not too many, easy for the crocodiles to gather
on, probably on one side of the river only. Planned with the user the same day ("Yes, plan it"). Looks are already
picked in the sand viewer (`python tools/model_viewer.py sand`: sand by how far inland, dry, wet, ripple and slide
sand; pebble look B). **Built after the wide rivers (`claude/wide-rivers`) are merged into `master`.**

## The idea in one paragraph

On a winding river the current slows on the inside of a bend and drops its sand there as a bar that slopes gently into
the water; the outside of the same bend is cut steep. So a sand bank lies on one side only, and the side swaps from bend
to bend. Our rivers already do this with silt (`worldgen/RiverBankFeature`, look 6: silt bars on the inside of bends, a
thin wet line on the outside). The sand banks are the biggest of those bars turned to sand, on only some sharp bends,
about one every 80 to 120 blocks of river; everywhere else stays silt, wet silt and trampled mud. Crocodiles choosing
where to bask look farther for sand and pick it over silt, so each sand bank gathers a few of them.

## Decisions

**The user's (2026-11-16):** sand banks only in some parts of the river, not too many; one side only; easy for the
crocodiles to gather on.

**My picks (to confirm in-game):**
- Sand only on the inside of sharp bends (the silt bars' own rule, a stronger bend), about one bank every 80 to 120
  blocks of river, measured on real land in step 1 and tuned to that band.
- A bank 10 to 25 blocks long and 4 to 10 wide, bigger on the wide rivers near the coast; it reaches past today's
  4-block low strip, cut flush with the water like the strip, sloping in.
- The choice of which sharp bends get sand comes from a smooth noise over the land (about 100 blocks across), so a
  whole bar shares the choice and chunks made in any order agree; where the noise fades the sand mixes into silt over
  a few blocks, as real bars grade from sand to silt downstream.
- On a bank: dry sand on top, wet sand at the waterline, ripple sand under the shallow water off its foot, the tone by
  how far inland (the viewer's three tones, mixed over a long stretch); a few crocodile slides where the bank meets
  deep water (worldgen, 1 to 3 a bank).
- Pebbles (look B) go in with the banks: thickest at a bar's upstream end and along the deep channel, more far
  inland, few near the coast (the chances already in `docs/DESIGN.md`).
- Crocodiles: when one wants the sun it looks for sand within 64 blocks and takes it over a nearer silt bank; the
  existing "join crocodiles basking within 48" rule then gathers them. The search is coarse (every 4th column at the
  water's level, at most once a minute while it wants the sun), so it costs little.
- Not in this plan: a sand island in the middle of the widest rivers (offered, left out unless the user asks); the
  nest (the crocodile's life cycle, its own step later).

**Balance, worked out (not measured yet):** up to 6 crocodiles round a player, spawned at least 24 blocks apart; a sand
bank every 80 to 120 blocks puts any crocodile within about 60 of one; so about 2 or 3 crocodiles to a bank, now and then
one lying alone on silt. Checked in step 4.

**Your calls:** none left; the user sees the result in a new world (step 5).

## Steps

1. **Measure the bends on real land.** A `worldgen` measure test (like `riverWidthIsMeasured`): along the savanna
   rivers on 3 seeds, how often the bend reading passes each strength, per 100 blocks of river, by how far inland. Check
   the bend reading still works on the wide rivers (66 across near inland; its rays look 12 blocks, which may see only
   water on broad water: the "silt bars on broad water" left open by the wide rivers). Pick the strength and noise share
   that give a bank every 80 to 120 blocks. Nothing changes in-game.
2. **The sand blocks.** River sand dry, wet and ripple in the three tones (a tone property or a block per tone, my pick
   when built), the crocodile slide, from `tools/sand_art.py`; pebble blocks in look B (7 looks, Minecraft picking one
   at random per spot, turned and nudged); creative Natural Blocks tab; a block test.
3. **Sand banks and pebbles in new land.** In `RiverBankFeature`: chosen sharp bends become sand bars (longer and wider
   than the strip, cut flush, sloping in, wet at the waterline, ripples off the foot, slides); pebbles placed on banks
   and the bed. A `worldgen` test: banks on the inside of bends only, spacing in the 80 to 120 band, none on the outside,
   chunks agree at their edges. Loading speed measured (`tools/flight.sh`) before and after.
4. **Crocodiles prefer sand.** The coarse 64-block search for sand in the basking choice; a `river` test: a crocodile
   with a silt bank near and a sand bank farther goes to the sand, and a few crocodiles end up on one bank. Balance
   check: crocodiles per bank on real land (a short soak or a test on a generated river).
5. **Check.** `worldgen,river,mound,trees` on 6 servers; the mound survey unchanged; frame watch numbers with pebbles in
   view; the user's look in a new world.

## What was built (2026-11-16, branch `claude/wide-rivers`)

Planned to come after the wide rivers were merged; built on the wide rivers' own branch (not merged yet) at the user's
go, so the two go into `master` together.

**Step 1, the bends measured** (`SavannaWorldTests.riverBendsAreMeasured`, `worldgen`, measures only, about 30 s, the
noise alone: a first version that also asked the water took 11 minutes). Rivers sit on the zero line of Minecraft's
weirdness noise, so the bend can be read from the noise anywhere (`worldgen/RiverBends`): the curvature of the zero line
from the noise's slopes read a few blocks apart. Walked along 75 savanna rivers on 3 seeds (46,532 blocks of river):
- the point reading puts the inside on the same side as the river's walked line over 64 blocks: slopes 16 apart 66 in
  100, 32 apart 82, **64 apart 94.5** (picked, `SandBanks.SPAN`);
- bends sharper than a radius, walked over 64 blocks each way: under 60 blocks one every 132 blocks of river, under 80
  one every 77 (mid inland 64, far inland 81, near inland 105, the coast 498 with few coast rivers walked), under 110
  one every 56;
- the silt bars' bend reading (rays 12 blocks out) sees only water on the broad rivers, so the sand banks read the noise
  and use the water only as a veto (a column under 0.4 of the rays finding water, clearly the outside, is never sand).

**Step 2, the blocks:** `antcolonies:red_river_sand`, `golden_river_sand`, `pale_river_sand`, each with `wet_`,
`rippled_` and `_crocodile_slide` (axis, wet) kinds, falling like sand, shovel blocks, diggable by the insects
(`block/RiverSand`, art by `tools/sand_art.py`); `antcolonies:river_pebbles` (look B, 7 looks turned and nudged by
position, waterloggable, no collision, `block/RiverPebblesBlock`); creative Natural Blocks tab; checked in the algae
test (`sandBankBlockChecks`).

**Step 3, banks in new land** (`worldgen/SandBanks`, `RiverBankFeature`): a column is sand where the noise's bend is
sharper than a radius of 190 blocks (fully sand at 90) on its inside, a smooth noise about 48 blocks across keeps about
nine bends in ten, and the column lies within 70 blocks of the river's middle line; read at the chunk's corners and
blended, so neighbouring chunks agree. The bank: wet sand at the waterline, dry sand on the low strip and laid over the
floodplain past it a block up, out to 6 (far inland) to 10 blocks (coast), mixing into silt at its ends; rippled sand on
the shallow bed off it; crocodile slides at the waterline beside deep water (8 in 100 waterline columns); pebbles on the
bank (1 to 8 in 100 columns, twice by the water, most far inland) and on the bed (0.3 to 2.5 in 100, twice in the
shallows). Tones by how far inland, dithered over about 40 blocks. All numbers my picks. **Measured: one sand bank
every 111 blocks of river** (coast 77, near inland 112, mid 108, far 117; length along the river median 40, 90 in 100
under 148); the test fails outside 70 to 140. Tuned on the way: radius 90 / full 55 / pick 0.35 gave one every 216,
130 / 70 / 0.2 every 141, 160 / 80 / 0.1 every 119. New test `sandBanksLieOnTheInsideOfBends` (`worldgen`): on a sine
river with its own inside rule, 85 of 88 inside columns sand, none outside, wet sand on 11 of 11 waterline columns, dry
sand a block up on 30 of 33 past the strip, a slide, nothing past the widest bank. Loading speed not measured (4 noise
looks a chunk plus one a column near a sharp bend).

**Step 4, crocodiles prefer sand** (`CrocodileEntity.findSandBank`, `SAND_REACH` 64, `SAND_LOOK_WAIT` 1,200 ticks):
wanting the sun with no company basking within 48, a crocodile looks at every other column within 64 blocks round the
water's surface (loaded land only, once a minute at most) for dry or wet river sand a block back from the water that no
other crocodile lies at or is heading to, remembers it, and swims there if a route reaches; else the nearest bank as
before. The company rule then lines the next ones up beside it. Resting half out is unchanged (my pick). Route reach 48
to 64. New test `aCrocodileSwimsToASandBankToBask` (`river`): beside grass banks it swam 40 blocks to the sand and
basked there 479 ticks after wanting the sun. Crocodiles per bank on real land not measured (the user's look).
