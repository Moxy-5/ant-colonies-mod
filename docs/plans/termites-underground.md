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
- More holes along the tunnels the colony already has (spaced, my pick about 6 blocks apart). Each night only a few
  open (my pick: 2 to 4 by colony size), others next time, so grazing and bare patches spread out. No new digging.
- Soldiers' sentry line, the alarm (plug on alarm), runways and the middle hole keep working with any open hole.

## Step 2: galleries grow tip by tip
- Builders extend gallery tips outward a few blocks at a time (soil to the mound, as all digging); new holes appear
  along them. The range is the farthest tip (replaces the size table; up to about 50). Branching: a tip now and then
  forks. Uses `TunnelRoute` for each piece, wall first with mud crust.
- Hidden trips start here (galleries get long): number in, out at the far hole; breaking in shows them.

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
