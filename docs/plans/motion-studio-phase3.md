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
3. **Clips made against the partner (ideas 33, 37, 38). Built 2026-11-26 (the user: "Go to step 3"), below.** what step 1's numbers show is missing (below): a `cling`
   (forepaws on a standing buffalo's haunch, jaws on), the pin aimed at a lying calf's neck, the buffalo bucking under a
   holder and sinking to its knees; each made against the spot's place per size and checked with step 2. Here the pair
   file plays both clips on one clock.
4. **The strike carries itself (idea 41, BUGS 375). Built 2026-11-26 (the user: "Go to step 4"), below.** the leap's
   travel in its own clip (a path, as the block moves), aimed at the spot it will grip, so it lands there instead of
   gliding.
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

## Step 3 (started 2026-11-26, the user: "Go to step 3")
**What the numbers said first.** Before choosing clips I measured where a lioness stands for each spot on a standing cow
and on a cow lying down (`spots.py`'s placement, a lioness 0.97 on a cow 0.9): her place moves 0.00 to 0.03 blocks; only
the height of the spots changes (1.12 blocks standing, 0.65 lying). So a holder never needs to move while the cow goes
down: only her head and body go down with it. And `bite` on a cow lying puts her jaw on the hide but her body 1.1 to 2.5
px into the cow at the rump and the throat (the flanks are clear); the calf's pin is right already (step 2).

**The picks (mine; animals are fun first, inspired by real hunts: lionesses bring a buffalo down by their weight, one
clamps the throat):**
- **The cow brought down (idea 38):** held by two or more lionesses that have finished their strike, a grown buffalo
  goes down: `held_sink` (a struggle, the head tossed at them, then all four legs buckle together and it sinks onto its
  brisket; all its spots drop together, so one holder clip fits every spot), then `held_down` (on its brisket, heaving,
  tossing its horns, bellowing, a try to rise that fails). Fewer than two: it gets up (`rest_up`) and pulls away again
  (the bull driving one off frees it). The calf is knocked down at the strike, as now.
- **Dying lying down:** a buffalo that dies lying (held down, resting, a calf pinned) plays `death_down` (it rolls onto
  its flank from its brisket: the death's own fall, ending in the same last pose) instead of standing up first into
  `death`. Its holders let go as it dies (they are placed on its spots only while it lives).
- **Three new lioness clips, made against the buffalo's spots (idea 37):** `throat_hold` (a standing cow's throat: her
  jaw clamped at the spot's height, tugging), `hold_sink` (played with the cow's `held_sink` on one clock: her jaw goes
  down with the spots tick by tick, the blends' one-tick difference counted) and `hold_down` (a cow down: her jaw at the
  lying spots' height, body low and braced). The lion maker reads the buffalo's spot table and solves her body and
  neck so her jaw is at the spot's height, at the same reach ahead as her `bite`: so her place is the same for every
  hold clip, and she never slides while the cow goes down. The rump and flanks of a standing cow keep `bite`.
- **One table picks the hold clip:** the pairs in the buffalo's plan say which hold clip goes with which of its clips
  and spots, at which size; `spots.py` writes them into its table and the game reads them (the old rule "grown: bite,
  else pin" goes). A prey clip no pair names counts as its idle; of the pairs that fit, the one nearest the prey's size.
- **Not in this step:** the rearing `cling` on a standing cow's haunch (the cow now goes down as soon as a second lioness
  holds it, so a lone lioness on a walking cow is the only standing hold left); a third lioness gripping mid-sink plays
  `hold_sink` from its start, behind the cow's clock (a moment, said here, not patched).

### Step 3, built (2026-11-26)
- **The buffalo** (`tools/buffalo_anims.py`): `held_sink` (2.0 s: its head thrown back and hooking at the lions, a
  bellow, a stagger, then all four legs buckle together and it sinks onto its brisket), `held_down` (2.4 s loop: heaving,
  bellowing, a push up on its front knees that fails; its head held, a small swing only; its ears flat back; its tail
  clamped down over its right buttock) and `death_down` (the death's own fall from its brisket, the same last pose, so
  the carcass matches). Its rest pose maker takes a `trunk` hook so a trunk move comes before the legs are solved (the
  hooves stay put). The old clips are unchanged (checked: the same keyframes).
- **The lioness** (`tools/lion_anims.py` `make_holds`): `throat_hold` and `hold_down` (1.9 s loops) and `hold_sink`
  (41 ticks, one more than the cow's 40: the blends are 4 and 5 ticks, so both end on one tick). Each frame her neck's
  pitch and her body's shift ahead are solved (Newton, two unknowns) so her jaw is at the spots' height (the buffalo's
  spot table, at the pair's sizes) at her bite's reach; her paws stay put through every hold; her head tugs side to
  side under a pixel at her jaw, centred at each clip's bite tick. Her bite's tick and both blends are read where the
  game reads them (her plan's holds, the Java), not copied. Her jaw at each hold's bite: x 0, 32.65 px ahead in all
  four (bite, throat_hold, hold_sink, hold_down), so her place is the same whatever she plays.
- **The pairs drive the game** (`spots.pairs` in `buffalo.json`, written into its table by `tools/spots.py`, format 2;
  read by the framework's `Spots.holdClip`): a standing cow: `bite` at the haunch and flanks, `throat_hold` at the
  throat; going down: `hold_sink` ("together": both clips start on one tick); down or caught lying: `hold_down`; a
  calf: `takedown_pin`. The holds table now has each clip's length and whether it loops (a loop bites at its hit each
  time round). `LionHunt.holdClip` asks the table (the old "grown: bite, else pin" is gone); a lion with no pair there
  cannot hold.
- **The game** (`BuffaloEntity`, `LionEntity`, `LionHunt`, `BuffaloGoals`): a grown buffalo with two holders past
  their strike (`steadyHolders`, its passengers) plays `held_sink`, then `held_down`; fewer than two, `rest_up` (one
  rule in its own tick). While down, and getting up with a holder still on, it neither pulls nor turns (it would swing
  its holders round, or slide on its knees). It dies lying down
  with `death_down` (`clipName`: also a resting buffalo or a pinned calf). Its holders let go as it dies. A holder
  switches to the clip that goes with the prey's on the tick the prey's changes (riders tick after what they ride), so
  `hold_sink` plays on the cow's clock with nothing kept in step by hand.
- **The rump spot moved onto the left haunch** (`from` 5 px to its left): on the tail's root the cow's tail ran through
  the holder's head (2.0 to 2.5 px), as a real lioness bites the haunch beside the tail.
- **Measured (`clip_check.py lioness --pair buffalo`, 11 flagged, was 10):** every jaw on the hide at every bite (-0.4
  to +0.2 px), the throat too (step 2's 1.5 px short is gone); the haunch and the flanks clear of the cow standing, going
  down and down; no two holders touch. Left: at the throat the cow's cheek and ears press 1.2 to 1.8 px into the
  holder's neck and shoulders (standing, going down and down): real contact for a throat hold, and moving the throat
  spots already failed once (step 2), so it stays as it is until the film shows whether it looks wrong; a cow caught
  resting has its tail out behind (its rest clip) and 1.5 px in the haunch holder; the calf's throat holders 1.1 px (as
  in step 2). The new clips pass the clip checks (feet planted, nothing in the ground, every clip change smooth).
- **Not done here:** a third lioness gripping mid-sink plays `hold_sink` from its start, behind the cow's clock (said, not
  patched); the cling on a standing cow's haunch; holders stepping clear as the cow rolls over dying (they let go and
  stand; the one on its right side may have the cow's back over her paws for the last half second).
- **Tests** (`land`, `LionHuntTests`): `twoLionessesBringACowDownAndGoDownWithItOnItsClock` (it goes down under two;
  each tick both jaws on their spots within 0.05, both within 0.08 of their places, `hold_sink` exactly a tick ahead of
  `held_sink`, then `hold_down`; their clamps bite; one let go, the cow gets up) and
  `aBuffaloHeldDownDiesWhereItLiesAndItsHoldersLetGo` (a cow held down and a pinned calf die with `death_down`; all three
  holders off at once). Found on the way: her head's tug had her jaw 1 to 2 px to the side at the bite ticks, which
  moved her place 0.11 blocks when the cow went from sinking to lying (the test caught it): the tug is now centred there.
- **The full run** (6 servers, 36 minutes): 451 of 458 required passed; the 7 are termite, raid, crocodile and tilapia
  tests (BUGS 418: 5 passed alone 3 of 3, 2 failed alone 1 of 3); every `land` test passed, and again (42 of 42) after
  the last fix.
- **Next:** the hunt film (`tools/film/lion_hunt.film`), asked first; then step 4 (the strike carries itself).

## Step 4, built (2026-11-26, the user: "Go to step 4")
**The starting idea (one sentence):** a strike is a move made once offline with its clip (method M1): the clip holds its
own travel as a path, and the game places the lioness along that path from where she set off to her place on her
prey's spot, so the drawn leap and her box can never part (BUGS 375: the move control slid her box 4 to 5 blocks while
the clip leapt on the spot).
- **The clips** (`tools/lion_anims.py`, both lion forms): each strike has a `path root` note (one row a game tick, as the
  block moves), its speed the gallop clip's own (95 px a second at size 1), eased in and out (a smoothstep of the speed,
  so no jolt). Its feet are solved against the path: planted where they stand (a leg pushes back as the body goes), with
  the body in the air, planted where they land (a foreleg reaches forward for it). The joints in the air are the old
  clips' own.
  - `stalk_leap_bite`: still through its coil (from a creep), off at 0.26 s, a stop as it lands on its bite at 0.80 s:
    41.8 px (2.5 blocks for a lioness: the old `LEAP_FROM` by chance).
  - `jump_smack` is played out of a gallop, so its standing coil went (it stopped a running lioness dead): the clip now
    starts where the coil let go (0.20 s into the old 1.20 s, keys kept; 1.00 s, 20 ticks, its hit at tick 7, the apex
    swat), on the move from its first tick, landing at 0.58 s: 53.2 px, 72.2 with the blend (4.4 blocks).
  - Checked offline (`clip_check.py lioness|lion stalk_leap_bite jump_smack --switches`, and each paw's height and
    slide in the world tick by tick): nothing in the ground, no foot hanging, turns under 90, the 14 clip changes clean;
    a planted paw slides at most 1 to 2 px in a tick, as it lands (the old clips' own landing shuffle).
- **The table** (`tools/spots.py`, format 3): a holder's plan lists its strikes and the tick each hits (moved here from
  `LionHunt`'s `LEAP_HIT` and `SMACK_HIT`: one place); its spot table gets each strike's reach (px at size 1) and its
  share of it a tick from the strike's start, the blend included: through the blend it goes at its path's starting
  speed (the leap waits, the smack keeps running). So the game reads one row a tick with no blend rule of its own. The
  blend is read from the Java and is in the fingerprint.
- **The game** (`Spots.Strike`, `LionHunt.strikeSpot`, `setOff`, `ride`):
  - **One rule when to strike, for both:** the free spot nearest her, her place there, where that will be when she
    lands (the prey going on as it goes now), within the strike's reach at her size. `LEAP_FROM` (2.5 head gap) and
    `TOUCH` (the smack once touching) are gone.
  - **She rides from the strike's start:** placed each tick the strike's share of the way from where she set off to her
    place at the spot, her heading turning with it, on the server and on every screen alike (a screen that learns of the
    strike a tick late carries on from where it draws her). So she stands through the coil, flies, and lands on her
    place however the prey moved (it homes in: a strike always lands unless cut).
  - **From the hit she grips** (its first bite; a calf knocked down); before it she is on her way and does not slow the
    prey. The leap's hit is its landing; the smack's is its swat in the air, two thirds of the way.
  - **A block in her way:** her box placed into a block, she lets go (the same rule as no room at her spot), and the
    clip plays out where she is.
  - The leap from the stalk starts the fight at once (her mates rush in 0.8 s sooner than before).
- **Tests** (`land`): `aLeapCarriesItselfOntoItsSpotAndBites` (one out of reach does not set off; one in reach stands
  through the coil, never faster in a tick than the leap's fastest tick, lands with her jaw on the calf's spot within
  0.05 the tick the travel ends, having gone no farther than its reach; no grip before the hit; at the hit a bite, the
  calf down and held) and `aSmackOutOfAGallopLandsOnAWalkingCow` (on the move from its first tick, never back, her jaw on
  the walking cow's spot within 0.05 as she lands, grips from the hit). Measured: the smack 4.05 blocks in 16 ticks onto
  a cow that walked 1.29 meanwhile.
- **Not done here (said, not patched):** the 4-tick blend out of the gallop shows a still pose sliding at gallop speed
  for 0.2 s (every clip change while moving does); her paw at the smack's hit is not checked against the hide offline
  (`--pair` checks holds only); a strike's jaw at its hit is not in the table. The film (`tools/film/lion_hunt.film`,
  asked first) is the judge.
- **Next:** the hunt film, then step 5 (feeding on the carcass).
