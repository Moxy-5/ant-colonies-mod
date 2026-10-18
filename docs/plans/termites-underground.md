# Termites underground: the range is the galleries

Planned with the user 2026-10-17 (the termite half of `docs/plans/long-trails.md`). Decisions in `docs/DESIGN.md`,
"Termites underground". Step 0 built (branch `termites-underground`).

## Real Macrotermes (what this copies)
- Under a mound lies a web of thin foraging galleries just below the surface, radiating out tens of metres (about
  50 m in a dug-up *M. michaelseni* nest in Kenya, Darlington). The workers keep extending them outward.
- Along them lie many small foraging holes. On a given night only some are opened; the foragers graze a short way
  round each and plug it again. The grazing is scattered over a wide area, and almost nobody crosses open ground.
- Galleries last for years and are kept up; unused ones slowly fill in.

## Today (what changes)
- 2 foraging holes 8 blocks out (`NestPlan.foragingTunnels`); foragers scout and graze on the surface round them.
- A tunnel to rich food only (branch, tree, hay; `ForagingHoles.tick`, `TunnelRoute`), its hole plugged for good once
  the food is gone; mud runways to rich food near a hole.
- The reach is a size table (`nest.new_holes`: 10 blocks young, 40 at 500 workers; `Colony.foragingRadius`).

## The user's decisions (2026-10-17)
- **Do it, in small steps that lower the risk** (the user worried about it after hearing it is risky).
- **Hidden trips:** a termite going into a long gallery (my pick: over about 12 blocks) becomes a number and comes out
  at the far hole after the walk's time; short ones are walked for real. **If the player breaks into a gallery, every
  termite travelling in it as a number shows up as a real mob where it would be along the way** (the user's rule).
- **Old galleries last; a far branch nobody used for many game days slowly fills in** (my pick: about 10 days).
- **Reach: about 50 blocks** for an old big colony (real ~50 m), a species file number.

## How the risk is lowered
1. **A switch** in `macrotermes.json` (`nest.galleries.enabled`): the old holes, runways and new-hole tunnels stay
   untouched until the user confirms the new ones in-game; if something breaks, flip it back.
2. **Its own branch** from `master`; nothing merged until confirmed.
3. **Prove before the termites use it** (like the mound's "never stuck" proof): a unit test plans galleries over
   hundreds of made-up landscapes (hills, stone, water, caves) and checks every gallery cell can be walked, every hole
   reached, no gallery open to the air; a failure names the block.
4. **Smallest visible change first**, each step confirmed in-game before the next.

## Step 0: the safety net (no change in game)
**BUILT 2026-10-17** (`docs/HISTORY.md`); the proof found and fixed a real bug (`docs/BUGS.md` 45).
- The proof test above (pure route code, like `TunnelRoute`'s unit tests).
- A stuck watcher in the game tests: a termite with no progress in a tunnel for a while fails the test, with where.
- A long-walk scene: termites walk a ready-made 40-block gallery out and back (real mobs, the switch's walking part).

## Step 1: holes take turns
**Decided with the user 2026-10-17** (the real behaviour explained first; all four recommended answers picked):
- **More holes, no new digging:** holes about **4 blocks apart** along the tunnels the colony already has (the nest's
  own 2 tunnels and the new-hole tunnels to food), each bitten up through the tunnel's roof (the one block of soil over
  it). Today's tunnels are 8 blocks long, so only about 2 each for now; step 2's growing galleries bring many more.
- **Which open each night: food-led.** Holes near food found on earlier nights (the food map: food within a few blocks
  of the hole, my pick 6) open again while that food lasts; plus 1 or 2 holes rested longest (or not yet opened) to
  explore. Real foragers go back to good spots and keep probing new ones.
- **How many: by foragers and rain.** One open hole for every so many foragers going out (my pick: 6), at least 1;
  more on rainy nights (my pick: half as many again), as real ones open more after rain.
- **Old holes stay:** a plugged hole stays usable for ever; filling in comes with step 3's far branches.
- **4 tunnels, a bit longer (user, 2026-10-17, asked while building):** the full mound (radius up to about 7.6)
  leaves no room for holes along the 8-block tunnels, so the 2 side tunnels go on out to about 16 blocks
  (`arm_length`) and 2 more arms branch off them past the mound to the front and back; the holes are spaced along
  those arms (none within the foraging-hole distance of the nest middle).
- Each night's choice is made once, when foraging starts, and kept until the holes are plugged (saved). A tunnel dug
  out to rich food (new holes) opens every night while its food lasts, also when it was finished after the night's
  turns were picked; a hole with a runway of sheeting stays open as before.
- Soldiers keep their posts in the nest's own 2 tunnels; out along the arms they guard only the holes in use.
- Behind the switch `nest.galleries.enabled` (off: today's 2 holes, both open).
- **Parts that use the holes and must work with any open set:** the alarm (run to the nearest open hole, plug it),
  the soldiers' sentry line, runways (only from a hole that is open), bare patches, mushrooms, the food map and
  scouting, going inside and plugging, the middle hole of a young mound, and the numbers foraging while away
  (`FieldLife`: from the holes chosen that night).
- **Tests (added to existing scenes where one builds the same world):** the holes are spaced and roofed and walkable
  (the gallery proof grows a check for holes along a tunnel); on a night only the chosen holes open and all are
  plugged after; food-led: a hole by a known food patch reopens the next night and an explore hole opens too; more open
  on a rainy night; the switch off keeps today's behaviour. Then `termitefood,mound,soldiers,away` on 6 servers.
- In game: from above the nest at night, holes open in a few spots round the mound, different ones on different
  nights, grazing (bare patches) spreading out instead of two worn circles.

## Step 2: galleries grow tip by tip
**Decided with the user 2026-10-17** (real behaviour explained first): split into **2a** (the galleries grow, tested
in-game) and **2b** (hidden trips).
- **Reach by colony size:** more workers dig more: the galleries' total length grows with the workers (my pick: 1 block
  of gallery per 4 workers, numbers counted; the step 1 arms are the least), no tip past about 50 blocks from the
  middle (`reach`). A young colony stays close; a big old one (thousands) reaches 50. Fits the balance goal (termites
  slow early, huge late).
- **Spread evenly:** the shortest tip grows first, each piece about 4 blocks (`piece_length`), wandering a little
  (my pick: up to 20 degrees) and steered away from the nearest other tip, so the web covers the ground round the mound
  like spokes. Step 3 then pulls tips toward food.
- **Forks every 8 to 12 blocks** of a tip's growth (my pick, rolled with the colony's dice): the tip splits in two,
  about 30 degrees either side of its way.
- One piece (or a fork's two) planned at a time, once everything planned is dug; dug like the rest of the nest (soil to
  the mound), day and night; holes laid out along each new piece as in step 1, and they take turns.
- **The range** (`Colony.foragingRadius`, scouts, the alarm's reach) is the farthest hole plus the graze round it
  (`food_radius`), in place of the size table, while the switch is on. Today's new-hole tunnels to rich food keep
  their own reach until step 3 makes them one system.
- **Ready for the Matabele ants (M8, the user's reason for this update):** galleries stay 1 block under the ground (an
  ant major can break the roof at a feeding spot); the holes in turn and the feeding spots stay on record for an ant
  scout to read; in 2b the termites travelling as numbers show up as real ones when **anything** breaks in (player,
  ant, any mob), species-neutral, so a raid finds real termites to fight and carry off.
- **Tests:** the gallery proof grows whole webs (to 50 blocks) over its landscapes: every piece walkable out and back,
  never open to the sky, no tip past the reach, every side of the mound reached, forks present; a game scene with a big
  colony (numbers) on a full 65-wide ground: tips grow past the arms, a fork, holes along the new pieces, the total
  length kept to the budget (a small colony stops growing).

### 2b: hidden trips
- A termite going into a long gallery (my pick: over about 12 blocks) becomes a number and comes out at the far hole
  after the walk's time; breaking in (anything, see above) shows them where they would be along the way.
- **BUILT 2026-10-17** (`colony/HiddenTrips`, `nav/HiddenWays`, `AntNavigator.goUnder`/`cameUp`; switch
  `nest.galleries.hidden_trip`, 0 = off). Not a number in the population: the same insect, taken out of the world and
  put back (its job, load and route kept), counted among the real mobs meanwhile. Break-ins looked for once a second.
  Game test `hiddenTripsGoUnseenAndShowUpWhereTheyAreWhenTheGalleryIsBrokenInto`. To be tried in-game with 2a.

## Step 3: growing toward food
- Food found from a hole pulls the nearest tip toward it; rich food gets its feeding gallery as today. Today's
  separate "new hole to rich food" becomes part of this (one system).
- Unused far branches fill in after about 10 days.

## Step 4: away and speed
- The numbers forage from the galleries while away; galleries grow while away (`BuildLife`).
- The plant scan only along the galleries (as the ant trails' step 4).
- Then the switch is removed once the user is happy, with the old code it replaces.

## Tests
- After each step: `termitefood,mound,soldiers,away` on 6 servers; full run before merging (it touches shared parts).
- Every old check of the new-hole, runway, bare patch and away tests stays (merged, never deleted).
