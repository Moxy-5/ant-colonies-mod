# Motion Studio, phase 3: animals that touch (planned and started 2026-11-26)

The user, 2026-11-26: "while another branch does bug fixing lets start part 3 of improving the animal maker engine".
Phase 3 of `docs/plans/motion-studio.md` (ideas 31 to 41): the lion and the buffalo first, the hold's hand-placed oval
and fixed ticks replaced by spots on the prey's drawn body. Phase 1 (the replay page) is still open in Neon's lane; this
phase needs only the phase 0 recordings and `tools/pair_report.py`, so it does not wait for it. Animals are fun first
(CLAUDE.md, 2026-11-14): every number here is my pick unless measured. Branch `claude/motion-phase3` (worktree
`.claude\worktrees\motion-phase3`, from `claude/review-fix-2`).

## What the phase 0 films showed (the evidence, BUGS 373 to 377)
373 the holding lioness's jaws 0.4 to 1.0 blocks off the calf; 374 her planted feet skate while the calf lies still (her
box pushed toward a place each tick); 375 the strike carries her box 4 to 5 blocks while the leap clip stays in place;
376 a one-tick calm between one-shots (the grip, each feeding turn); 377 three lionesses standing through each other
round one calf. All five come from the hold being placed by hand round the prey's box, not on its drawn body.

## The picks (before building)
- **Riding, not a drawing shift (idea 34 over 35):** a holder rides its prey, as the crocodile carries its catch, and
  the prey places it each tick (`positionRider`). The server and every screen place it the same way from the same
  numbers, so the gap between jaws and hide cannot jitter with lag (each animal's position otherwise reaches a screen on
  its own schedule), and its box is never pushed, so its feet cannot skate while the prey stands still. A drawing shift
  would leave the server's box elsewhere (hits, the films' checks) and slide when large.
- **Spots in the body plan, found on the skin (idea 31, my change):** not empty marker bones in the model. A spot is a
  bone, a point inside the body and a direction out; `tools/spots.py` finds where a ray that way leaves the body's
  boxes, so a re-exported or reshaped model moves its spots with it, and nothing in the geo file has to be kept by hand.
- **Made once offline, played by the game (method M1):** `tools/spots.py` poses the prey's clips and writes where each
  spot is at every tick, and the holders' jaws at their bite, into `assets/<mod>/spots/<animal>.json` (never by hand;
  the build fails when a table is behind its clips). The game reads the table; it never poses a model itself.
- **Fewer rules:** the oval (1.9 by 0.6, the head 0.4 in), the five hold angles, "shaken off at 4 blocks" and the
  hit ticks in Java go; one table and one rule each for where a holder stands, whether a spot is free and when it lets go.

## The steps
1. **The hold on the prey's body (ideas 31, 32, 34, 43; BUGS 373, 374, 377, and 376 in the hold).** Built 2026-11-26,
   below.
2. **The pair seen offline (ideas 36, 39). Built 2026-11-26, below.** `clip_check.py <holder> --pair <prey>`: both posed
   together from the table: parts of one inside the other, the jaw against the skin at the bite (in the air or buried),
   several holders against each other; strips from three sides. Then the hunt film again with `pair_report.py`.
3. **Clips made against the partner (ideas 33, 37, 38):** what step 1's numbers show is missing (below): a `cling`
   (forepaws on a standing buffalo's haunch, jaws on), the pin aimed at a lying calf's neck, the buffalo bucking under a
   holder and sinking to its knees; each made against the spot's place per size and checked with step 2. Here the pair
   file plays both clips on one clock.
4. **The strike carries itself (idea 41, BUGS 375):** the leap's travel in its own clip (a path, as the block moves),
   aimed at the spot it will grip, so it lands there instead of gliding.
5. **Feeding on the carcass (idea 40, BUGS 376's other half):** feeding spots on the carcass, the `feed` clips aimed at
   them, the feeding turns with no calm tick between.

## Step 1, built (2026-11-26)
- **Spots** (`buffalo.json`, section `spots`): `rump` (the haunch, from behind), `neck_left`, `neck_right` (the throat
  from the front sides), `flank_left`, `flank_right`; each found on the skin by its ray. The clips a held buffalo plays
  (`idle`, the lying down, the rest, the getting up, `death`) are posed tick by tick; a loop's spot is its middle place
  (the game cannot know a loop's phase at the speed it is drawn), a one-shot's the place at its tick.
- **Holds** (`lioness.json`, `lion.json`, `lion_cub.json`, section `holds`): the clips a holder grips with (`bite` on
  a standing buffalo, `takedown_pin` on a calf knocked down) and the tick each bites (moved here from `LionHunt`'s
  `BITE_HIT` and `PIN_HIT`: one place). The table keeps the jaw (the `carry` bone's point) at that tick.
- **`tools/spots.py`:** `write` (every plan with spots or holds, or the named), `--check` (the build's
  `checkSpotTables`: fails when a table is behind its plan, model or clips), `report <holder> <prey>` (where a holder
  stands for each spot at real sizes, how high its jaw is against the spot at the bite, which spots crowd each other).
- **The game** (`framework/.../entity/body/Spots`, shared and species-neutral; `BuffaloEntity.positionRider`,
  `LionHunt.hold`): the holder rides its prey from the strike's hit; each tick it stands where its jaw, at its bite's
  tick, meets its spot, facing in along the spot's direction, its feet on the ground there; it settles there over what
  is left of its strike clip (no new number: the clip's own length). Both sides work it out from the same table, the
  prey's clip tick (now tracked on the client too) and the spot the lion holds (synced). A spot is free while no other
  holder (of any pride) has it and there is room to stand there; it lets go when its place has no room (a tree, a
  drop): the old 4-block rule goes. Between two bites it starts the next at the last one's end, never a calm tick
  between (BUGS 376 in the hold).
- **Found on the way (the still test failed 3 of 3):** each pin knocked the lying calf 0.09 blocks away and 0.04 up
  (Minecraft's knockback on every hit), and its holder with it: part of what BUGS 374 measured. A holder's bite no
  longer shoves the prey it holds (it is held, not hit from afar).
- **Holders never stand in each other (BUGS 377) by where the spots lie, checked offline, not by a rule in the game:**
  I first built a game rule (a spot free only while a lion there keeps its trunk, shoulders to hips, a trunk's width
  clear of every other holder's); the report showed no two of the five spots ever crowd at real sizes (a lioness on a
  cow or on a calf), so the rule would never fire and was taken out (fewer rules). All holders ride the same prey, so
  their places against each other are exactly the table's: the offline report is exact. A new spot or a new pair is
  checked there (step 2 makes it part of `--pair`).
- **What the numbers say (`tools/spots.py report lioness buffalo`, a lioness 0.97 on a cow 0.9 and a calf 0.36):**
  - a standing cow bitten (`bite`, tick 7): her jaw 0.98 high, the spots 1.12: 0.13 under, near enough (all five).
  - a calf lying, pinned (`takedown_pin`, tick 4): her jaw 0.42 high, the spots 0.26: 0.16 over it. The pin's jaw goes
    lowest at ticks 5 and 6 (6 px), so it nearly touches a moment after the hit.
  - a calf standing bitten: 0.54 over it (she bites the air over its back); a cow lying bitten: 0.33 over. The game
    never does either today (a calf is knocked down at the strike; a cow is bitten standing), but a cow lying down
    while held would show it: for step 3.
  - where she stands: 2.85 behind a cow's middle at the rump, 2.3 out at a flank; round a calf 1.5 to 2.2 out.
- **Tests** (`land`, `LionHuntTests`): `aHolderRidesOnHerSpotHerJawOnItAndKeepsStill` (riding the calf as it goes down
  and lies: her jaw at the bite within 0.05 of its spot every tick, still within 0.01 a tick while it lies, her pins
  bite), `aSecondHolderTakesAFreeSpotClearOfTheFirst`, `aHolderWithNoRoomAtHerSpotLetsGo` (a wall at the rump: she lets
  go at once and stands on the ground, not on the cow's back); `aHeldCowPullsAwayNeverAtHerHolderAndHerBullComes` now
  rides.

## Step 2, built (2026-11-26, the user: "Go to step 2")
- **`python tools/clip_check.py <holder> --pair <prey> [hold clips] [--spots a,b] [--times s,...]`:** for every pair the
  prey's plan lists with the holder (`spots.pairs`: the hold clip, both sizes, the prey's clips then, each "once" or
  "loop"; the lioness's `bite` on a cow of 0.9 standing or dying, her `takedown_pin` on a calf of 0.36 going down,
  lying or dying), both animals posed together tick by tick exactly as the game places them: the prey's place from
  its spot table, the holder facing in, its jaw at its bite on the spot, its bites one after another with their blend.
  Each model is drawn where the game draws it, read from the Java (each plan's `checks.drawn_ahead`, a pattern read
  out of `BuffaloEntity` or `LionEntity`, as the blend is read from the controller); `tools/spots.py report` reads it
  the same way now (its own copy of the numbers is gone). Per prey clip and spot it prints, in sixteenths of a block:
  the jaw against the hide at each bite (true 3D distance to the prey's boxes; + in the air), its head and the prey in
  each other (the bite itself: reported), its body and the prey in each other (flagged over 1), and every holder
  against every other at a bite, heads and bodies apart (bodies flagged over 1). With `--times`, pictures from three
  sides (`pair_<holder>_<hold>_<prey>_<clip>_<spot>.png` and `..._all.png`, every holder at once): the spot a green
  dot, the jaw blue, a body part inside the other red. About a minute for the lioness's two pairs.
- **In the one command:** `tools/verify_animal.py` runs it as step "pairs" for an animal in any pair (holder or prey),
  after checking the spot tables are current.
- **What it found (the lioness on the buffalo; 10 flagged; all for step 3):**
  - **Rump and flanks: right.** The jaw meets the hide at every bite (within 0.1), on a standing cow and on a calf going
    down, lying and dying; only her muzzle sits in the hide (1.4 to 3.6, a bite). Step 1's report said her pin "hovers
    0.16 over" a lying calf's spot: that was height alone; the hide goes on up the neck's side, and her jaw is on it.
  - **A cow's throat (the neck spots): her jaw stops 1.5 short** (0.09 blocks): her muzzle meets the cow's own head
    and jaw first, the throat being under it. One try moving the spots back behind the head (75 degrees out, from 3 px
    farther back) brought her jaw to 0.7 but put her body 1.5 into the cow's, and on a calf the throat holder crowded
    the flank holder: taken back (the first try's numbers kept). A throat hold is a clip made against the spot (a head
    reaching up under the jaw), not a spot tuned by numbers: step 3.
  - **A dying cow falls into her holders** (their bodies 1.2 to 2.0 in hers): they keep their places as it goes down
    on its flank. The prey's own fall and the holders stepping off are step 3 (idea 38).
  - **A calf lying down: its neck holders' bodies 1.1 into it** for a moment of the lie-down (0.4 to 0.8 once it lies).
  - **Holders against each other:** bodies never meet; on a calf the two throat holders' heads meet (1.3) and the
    flank pair's (0.7), as several lionesses on one small calf would. Reported, not flagged.
- **Next:** the hunt film again (`tools/film/lion_hunt.film`, then `python tools/pair_report.py
  run-film/screenshots/lion_hunt`): the same numbers measured in the game, before step 3.

