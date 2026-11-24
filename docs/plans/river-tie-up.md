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
- Young crocodiles and catfish eat bullfrogs (the frog is not in their food yet). DONE (teens; catfish dropped, below).
- Barbs and young crocodiles snap up flies that land (fly clouds are eaten only by the bullfrog's tongue).
- Tilapia nibble hippo grass mats; snails and crabs graze the dying ones (reeds plan step 5's other eaters).
- Fish hide in the reeds when scared (reeds plan step 6).
- The full plan for these, planned 2026-11-23: "Finishing the food web" below.

## Finishing the food web (planned 2026-11-23; the user: "lets finish the food web of the river")

**Who eats whom today (read from the code):**
- Algae: tilapia, snails, tadpoles (barbs pick at it for looks only); catfish eggs on it: tilapia and crabs.
- Tilapia: catfish, crocodiles, otters, the bull shark. Barbs: catfish, crocodiles (hatchlings on up), bullfrogs,
  otters, the bull shark. Tadpoles: bullfrogs, hatchling crocodiles, catfish. Catfish: crocodiles, the bull shark.
- Snails: crabs, the monitor, bullfrogs. Crabs: the monitor, otters, bullfrogs (young crabs). Snail eggs: tilapia, crabs.
- Bullfrogs: teen and grown crocodiles, the monitor, otters. Remains: crabs, the monitor, catfish, crocodiles, the shark.
- Flies: only the bullfrog's tongue. Hippo grass and reed shoots: only hippos. Frog egg mats: nobody.

**The gaps:** flies, hippo grass and frog eggs each have a single eater; the reeds are only scenery to the animals; the
distichodus (the plant-eating fish) is made but not in the river. Four small steps close them, then the distichodus.
All picks are mine (animals: fun first, inspired by real life).

1. **Landed flies.** Today a fly landing is only drawn on the player's computer, so the server cannot see it. New: now
   and then (about every 30 s, my pick) the cloud itself picks a spot under it, on the water's surface or the bank, and
   one fly lands there for about 4 s (the server keeps the spot and a timer and tells the players' computers, which
   steer one fly down to it; one landed fly a cloud at a time). While it sits there:
   - a **barb** within 6 blocks rises and sips it off the surface (its existing sip at dawn and dusk gets a target, any
     time of day; barbs still have no hunger, so it changes nothing else);
   - a **hatchling or teen crocodile** within 4 blocks snaps at it (a quick head snap, its existing bite);
   - a **bullfrog** flicks its tongue at it as at a low swarm (the plan always said frogs take landed flies).
   One taker each time, 1 in 4 missing as with the tongue; a fly eaten is gone from the cloud like a tongued one. If no
   one takes it, it flies back up.
   *Balance:* at most 2 landed flies a minute against a refill of 1 a minute, so a cloud over a busy shallow empties in
   about 10 minutes and drifts off (as an empty cloud does today), and frogs still get the low swarm. No barbs or
   crocodiles in the pans (river zones), so pan frogs keep their flies to themselves.
   Tests in `river`: a barb under a cloud sips a landed fly; a hatchling snaps one; a cloud with nobody near loses none.

2. **Frog spawn is eaten, and the father earns his guarding.** Tilapia and crabs nibble a bullfrog egg mat as they
   already nibble catfish eggs and snail clutches (`EggMeals`: one rule, the frog mat added to what it knows), and the
   guarding father lunges at a tilapia or crab that comes within 2 blocks of his eggs or school (he already does it to
   players, monitors and young crocodiles). Real frog spawn is eaten by almost everything; this is what makes the
   guarding father matter.
   *Balance:* a mat is 8 to 12 eggs, a nibble takes 1; a guarded mat loses a few, an orphan mat may lose most. One mat a
   female every 4 days and the 16-tadpole cap stay. Test: an unguarded mat beside tilapia shrinks; a guarded one keeps
   most of its eggs.

3. **Hippo grass's other eaters** (reeds plan step 5's rest). The rule kept from that plan: living mats eaten lightly,
   the dying ones by the clean-up crew, never a clump killed.
   - **Tilapia** nibble the underside of young and grown mats now and then, besides algae. A nibble takes only a mat's
     outermost block, never the one touching the clump, and a mat block bitten waits a game day before another bite;
     the clump grows it back with its cycle.
   - **Snails, crabs and tadpoles** graze the dying-back mats and the reed stems as they graze algae (dying mats vanish
     on their own anyway, so this costs the plant nothing; the dying stage gets a use).
   Test in `river`: a scene of a hippo grass fringe with 10 tilapia, 6 snails and a crab watched 2 game days; the
   clump is alive at the end and its mats regrow.

4. **Hiding in the reeds** (reeds plan step 6).
   - A scared **barb or tilapia** (after a strike near it, or a swimmer) dashes to the nearest reed bed or under a hippo
     grass mat within 8 blocks and stays still about 5 to 10 s, then comes out. With no cover near it flees as today.
   - **Hatchling crocodiles** rest in reeds at the crèche's edge when there are any within 8 blocks, and dash there when
     their mother is called away or a threat comes. Croc nests are on sand banks, where no reeds grow, so this happens
     only where a bank and a bed meet: a nice find, not everywhere.
   - **Hunters see a still fish in cover only within 2 blocks** (one shared check, `RiverCover`, used by every fish
     hunter's "can I see it": catfish, crocodiles, otters, the bull shark, bullfrogs). A fish swimming out of the reeds
     is seen as usual, so hunters lose a chase, not their meals.
   Test: a chased barb with a reed bed near ends the chase in it; a hunter does not pick a still barb in the reeds 4
   blocks off.

5. **The distichodus into the river** (looks and clips done, `docs/plans/distichodus.md`; was in part 3 below). It is the
   web's missing plant eater and uses steps 3 and 4 as they are: grazes hippo grass mats and young reed shoots by the
   same light rule as the tilapia, groups of 2 to 4, bolts into the reeds (step 4) and leaps when scared; eaten by
   grown crocodiles and the bull shark. The spawning check list (CLAUDE.md), its share in the river zones, the hard
   animation rule for its clips in the game. Its own plan is read before starting.

**Ideas I am adding (say no to any):**
- **Fish round hippo dung** (real: hippo dung feeds African rivers, fish gather where hippos are): a hippo resting in
  its pool by day flicks dung now and then; barbs and tilapia within 8 blocks crowd the spot a few seconds and pick at
  it (looks only, like the barbs' sips). Cheap: the dung flick clip exists.
- **Hatchlings as prey** (real: only a few hatchlings in a hundred grow up; catfish, monitors and herons take them):
  a catfish or the monitor now and then snatches a hatchling that strays from the crèche, and the mother charges. This
  would be the crocodiles' only check on numbers from the food web; it fits best with the monitor's crocodile nest
  raids in part 3, so it waits for that step.

**Order:** 1, 2, 3, 4, then 5; each built, its tests run, committed and pushed. A full run after step 3 (three steps).

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
- **Distichodus:** looks and clips done; into the game (grazing mats and reed shoots, bolting into the reeds). Moved to
  the food web's step 5 (2026-11-23).
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
- 2026-11-23, the food web planned (below); its step 1 built: landed flies eaten by barbs, young crocodiles and
  bullfrogs (`docs/HISTORY.md`).
- 2026-11-23, the food web's step 2 built: frog spawn eaten by tilapia and crabs, the father lunging at them
  (`docs/HISTORY.md`).
- 2026-11-23, the food web's step 3 built: tilapia nibble mats' outer edge (algae first, so no day-long wait was
  needed), snails, crabs and tadpoles graze dying mats (no reed stems: a snail cannot climb one); `docs/HISTORY.md`.
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
