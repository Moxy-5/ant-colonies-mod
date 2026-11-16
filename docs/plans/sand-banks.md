# Sand banks on the savanna rivers (planned 2026-11-16; nothing built)

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
