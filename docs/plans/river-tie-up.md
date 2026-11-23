# River tie-up: the animal behaviour left before the land animals (planned 2026-11-23)

The user, 2026-11-23: "I am now happy with the river, let's tie up the rest of the animal behaviour I have missed
before going land animals." The goliath tiger fish (the old "African tigerfish" idea) is the user's "later maybe".
Animals rule: fun first, inspired by real life; every number here is my pick.

Gathered from `docs/LIVING-THINGS.md`, the river plans and the open bugs. Order: what is broken now, then the small
gaps in who eats whom, then each animal's unfinished life, then the last balance check. One step at a time, each
built, checked, committed and pushed; the user's in-game look as they play.

## 1. Broken now (real failures in the river tests)
- BUGS 296: a monitor hurt mid-climb is set down and never walks on (fails alone 2 of 3).
- BUGS 290: a monitor whose step is broken mid-climb sometimes never gets onto the step.
- BUGS 279 / 298: a hungry catfish's swallowed tilapia leaves no remains (fails alone 2 of 3).
- BUGS 299: two playing otters poke, rear and rest at the wrong spacing (fails alone 3 of 3).
- The otter's `pounce_pin` filmed in the game from three sides (the hard animation rule; built, not filmed).

## 2. Small gaps in the food web
- Young crocodiles and catfish eat bullfrogs (the frog is not in their food yet).
- Barbs and young crocodiles snap up flies that land (fly clouds are eaten only by the bullfrog's tongue).
- Tilapia nibble hippo grass mats; snails and crabs graze the dying ones (reeds plan step 5's other eaters).
- Fish hide in the reeds when scared (reeds plan step 6).

## 3. Each animal's unfinished life
- **Otter:** keep-away, the paired moves and tumble wrestle, begging for a fish, then the pet (the open call in
  `docs/plans/otter-play-and-fish.md`: how taming starts).
- **Hippo:** calves and breeding (the calf and teen looks are made), deep oxbows.
- **Monitor:** life-cycle step 5 (wrestling males, eggs in a living termite mound, raiding crocodile nests, young).
- **Bullfrog:** tadpoles and breeding: BUILT 2026-11-23. **Barb:** breeding (fry and female shapes made).
- **Crab:** the burrow with shells outside.
- **Eggs already in new land** (`docs/plans/world-eggs.md` steps 2 and 3).
- **Crocodile:** the old giant (1 or 2 to a river system); its own climb (BUGS 243, 256) and the monitor's stand at a
  step (BUGS 255).
- **Distichodus:** looks and clips done; into the game (grazing mats and reed shoots, bolting into the reeds).
- **Helmeted terrapin:** planned, never started (tears remains in groups, basks on crocodiles).
- **Mud balls** (mud plan step 5).

## 4. Last check
- Life-cycle step 6: a river watched 6 game days, every kind's count levels off (no boom, no crash, no lag).
- A full test run, then land animals.

## Progress
- 2026-11-23, step 1: otter play spacing (BUGS 299), the frozen hurt monitor (296) and fish eaten leaving no flecks
  (279/298) fixed; `docs/HISTORY.md`.
- 2026-11-23, step 2 begun: teen crocodiles hunt bullfrogs in the water as well as fish (hatchlings stay on barbs: a
  grown frog is too big). **Catfish eating frogs dropped (my pick):** two tries on the catfish's chase failed the same
  way (a floating frog's body sits above the water, out of a fish's reach, and it hops out onto the bank, so the catfish
  loses it); doing it right needs a new ambush-from-below hunt for a meeting that seldom happens (frogs keep to pans and
  slow edges, catfish to water 3 deep). Frogs are eaten by grown and teen crocodiles, the monitor and otters.
- 2026-11-23, the bullfrog's tadpoles, part A built (spawning by themselves, grazing, eaten, froglets); part B, the
  frogs breeding (calling after rain, the pair, eggs in the shallows hatching into a school, the father guarding it),
  next. `docs/HISTORY.md`.
- 2026-11-23, part B built: the bullfrog on the life kit (the froglet its young stage), females coming to calling males
  in the rain, the clasp, floating egg mats hatching into a school that knows its father, the father guarding eggs and
  school; the wrestling males of the old plan left out (not asked for). `docs/HISTORY.md`.

## Part B: bullfrogs breed (my picks, fun first, from the 2026-11-20 plan in `docs/plans/prey-fish-and-frogs.md`)
1. **The bullfrog onto the life kit** (`HasLife`, a life file `animal_life/bullfrog.json`): its sexes and sizes move
   from its own code to the kit (nothing changes in the game: the user's 0.8 and 0.65 kept), local limit 4 (the user's cap).
2. **After rain, the pair:** males already call from dusk and in rain; a ready female within 16 blocks of a calling
   male comes to him in shallow water (`SeekMateGoal`); he clasps her from behind (a new `clasp` clip on both: his front
   legs round her waist, her carrying him a few seconds), the hard animation rule (offline, then filmed in the game).
3. **The eggs:** she lays a floating film of eggs at the surface of the shallow water (a new clutch block on
   `EggClutchBlock`, its own look: a pale mat of black dots), 8 to 12 eggs; they hatch in a day into a tadpole school
   (room to breed: fewer than 4 bullfrogs near and the tadpole group under its cap of 16), one female every 4 days.
4. **The father guards the school** (the kit's guard care): he stays by the eggs and then the school for its 3 days,
   puffs and lunges at anything that comes near (a player, a monitor, a young crocodile), and does not eat his own;
   other frogs still may.
5. Tests in `life` and `river`; then the user's look in a new world (rain helps).

## Left for later (not in this tie-up)
Goliath tiger fish (the user's "later maybe"), electric catfish, lungfish and killifish (ideas).
