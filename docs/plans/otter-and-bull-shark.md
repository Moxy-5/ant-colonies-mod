# Plan: the otter and the bull shark in the game (2026-11-22)

The user, 2026-11-22: "put the bull shark and the otter in the game with all of the special hunting animations and
entering and coming out of the water mostly for the otter and going up a block. I want the otter's animations to be
really special ... one of the players' favorites, especially as a pet." Animals rule: fun first, inspired by real life.
Assets are ready from Neon: otter v6 (`docs/handoff/otter.md`, 21 clips, 5 block moves), bull shark v14
(`docs/handoff/bull_shark.md`, 12 clips, no block moves).

## What I checked first (the coder's side and the balancer's side)

- **Shared code, not a third copy.** The monitor and the hippo each carry their own ~250 lines of glue round the shared
  `BlockMoves` player (picking a move at an edge, turning square, stepping the box along the path, wet ticks, saving the
  move). A third copy for the otter would mean three places to fix every move bug. Step 1 pulls that glue into one
  framework helper (species-neutral, so the monitor and hippo can switch to it later; they are not touched now).
- **The otter's moves are slow for an otter.** Its step up, step down, slide in, dive and climb out each take 2.0 to 2.3
  seconds (fine for the hippo's 7 s and the monitor's 3.3 s; a real otter pops up a bank in about half a second). **The
  user, 2026-11-22: "the coming out of water is really quick ... more so the going up blocks ... I want it to look cute
  like he's scrambling up a block but quickly."** So, remade with `tools/otter_models.py` in step 1 (the user's change to
  approved clips): **climbing out of the water quick** (about 0.8 s, my pick: a hop up, chest on the edge, hind feet kick
  it over, straight into `shake_dry`); **step up a cute quick scramble** (about 0.8 s, my pick: front paws reach and
  scrabble on the top edge, head up, back arching, hind feet paddling fast against the face, a little rump wiggle as it
  pulls over, tail swinging for balance); step down quickened to match (about 0.6 s, a neat front-first hop). The calm
  slide in and the leaping dive keep their lengths (a slow slide and a big leap read well). Checked offline and filmed
  from three sides in the game before showing the user.
- **A pet must keep up.** The otter's bound is 2 blocks a second; a player walks 4.3 and runs 5.6. A pet otter needs a
  faster gallop on land (the bound stretched and quickened, checked offline and filmed) and a fast swim (real otters are
  quick swimmers: about 4 to 5 blocks a second, faster than a swimming player).
- **The shark's water.** Coast rivers are about 53 wide; the shark (1.1 tall, 5.5 to 7 long) needs the deep middle
  channel. The spawn check asks for water at least 4 deep round it and keeps it there (it never leaves the water). Its
  body gets a hit chain (`HitChain`, as the crocodile) so hits land along all 5 to 7 blocks of it.
- **Food chains (game balancer).** Otters eat crabs, barbs, tilapia, bullfrogs; sharks eat catfish, tilapia, barbs.
  Both get a long full-belly rest after a meal (otter about a third of a game day, shark about a game day) so neither
  clears a river; checked in a soak with fish counts per stretch.

## The otter (steps 1 to 4)

1. **Into the game, moving well.** `OtterEntity` + renderer (GeckoLib, the v6 model, `SteadyController` with the
   4-tick blend), spawn egg, its own spawning group `river_otter` (cap 6, family groups 2 to 4, river zones near, mid,
   far and oxbows; none in pans or the coast), the full spawning check list with a `worldWouldSpawn` test. Moves: walk,
   bound (speed scales the clip), surface swim at its waterline, dive and swim underwater (bubbles, about 30 s a breath,
   then up for air), the five block moves through the shared helper: **step up, step down, calm slide in, leaping dive in
   (from a bank into water 2+ deep, when hurried, fleeing or playing), scramble out**, then **shake dry** on the bank with
   a spray of drops (its ticks 2 to 18), the mud coat (mud adapted, drawn with its mud mask). Hit chain along the body.
   Pups at 0.55 follow their mother (`AnimalLife`), stepping normally.
   Checks: `clip_check`, `body_course` (add `controller` and `course` to its body plan), 3-side frame films of every
   move in the game, `game_compare` beside v6. The quick climb-out and the scramble up (above) are made in this step.
2. **Hunting, all of it.** Crabs: wades the shallows over gravel and stone doing `forage_feel` (the paws feel under
   stones, the real clawless otter's way), catches a crab with a `bite`, carries it crosswise in its jaws (`carry`
   socket) to the bank, lies down and `eat`s it (4 crunches, tail wag), leaving a crab shell remains. Fish: spots a barb
   or tilapia, dives in (the leaping dive when on a bank), chases underwater with `dive_swim`, snaps, comes up with the
   fish held, swims to the bank, climbs out, shakes dry, eats. Frogs: pounces a bullfrog at a pan edge. Full belly, then
   rest (`rest_down`, `rest`, `rest_up` on a bank, a family resting together).
3. **Family life and the river's dangers.** Two otters in the shallows wrestle (`play_up`, `play_rear`, `play_down`,
   splashes); pups tumble after; the family travels in a line along the bank. Flees crocodiles, hippos and the bull
   shark: into the water with the leaping dive and away underwater, or out onto land if the danger is in the water.
   Players: curious (stops, watches, comes to about 4 blocks, periscope look from the water), slips away if you run at it,
   nips (`bite`) only if hit or its pup is.
4. **The pet** (waiting on the user's call, below).

## The bull shark (steps 5 to 7)

5. **Into the game.** `BullSharkEntity` (water only), renderer with sizes 5.0 to 7.0 blocks weighted round 5.5 to 6.0,
   the scarred look from 6.0 up, swim speed scaling `idle`/`swim`/`burst`, hit chain, its own spawning group cap 1 to 2
   far apart, zones: coast whole share, near a half, nowhere else; the spawning check list. Out of water (drained,
   stranded) it does `beached_flop` and slowly suffocates; death rolls belly up (`death`). Checks as the otter.
6. **Hunting.** Patrols the deep middle channel; at dusk and night hunts: `surface_prowl` with the dorsal fin cutting the
   water, a slow approach, then `burst`, `bite` (hit on tick 8, eyes shielded); a barb or tilapia is swallowed, a catfish
   is held in the jaws (`prey` socket) and torn with `thrash`, leaving remains. Rarely, prey near the surface gets the
   `breach` strike (out of the water, twist, splash down). Long rest after.
7. **Meeting others.** A player swimming in deep water: first a `bump_investigate` nudge (no damage), and if the player
   stays near or hits it, `threat_display`, then `burst` and `bite`; it never follows into water under 3 deep. A
   crocodile coming close in the channel gets a `threat_display`; it keeps clear of hippo pods. A player on the bank or
   in a boat near the channel gets a rare `spy_hop` look.

## Last

8. **Soak and full run.** A soak on a coast-to-near river with fish counts (no crash of crabs, barbs, tilapia, catfish),
   then the full game test run (the every-3-steps rule also runs after steps 3 and 6). HISTORY, LIVING-THINGS (both
   BUILT), STATUS, HANDOFF (both picked up).

## Order and time

Otter first (the user's favorite), shark after; one step at a time, each built, tested, filmed, committed and pushed.
Honest time: each step is about one long session (the films are the slow part), so 45 minutes gets step 1 well along,
not both animals finished. The shark could run beside it in a second session (its own branch; they share only the mob
list, spawning groups and language files, easy to merge).

## Open calls for the user

- **The pet** (big call: how the game plays). My pick: an otter pup (or a young otter) hand fed fish or crab several
  times turns tame (hearts); a tame otter follows you on land and swims beside you, sits or stays on command, rests
  near you, defends you with nips, and now and then brings you a fish or crab it caught (real otters share food in the
  family). Grown wild otters stay wild.
- Bull shark Look B swap point: the card says 6.0 blocks; kept.
