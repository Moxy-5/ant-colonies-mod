# Floodplain trees in the game: the sycamore fig and the doum palm (planned 2026-11-13)

The user's asks (2026-11-13): "lets put the palm and fig in game we can plan out the spacing and rarity", "make it so dry
savanna trees no longer can grow on the greener dirt to make room for the new trees", "dead variants to be 1 in 8".
Looks picked in the model viewer the same day: **shape B and bark A for both** (`python tools/model_viewer.py floodplain`;
shapes in `tools/floodplain_viewer.template.html`, textures in `tools/floodplain_tree_art.py`), with every age and three
dead stages each.

## What the real trees do (general knowledge, not checked against papers)
- **Sycamore fig (*Ficus sycomorus*):** a riverine tree; along African savanna rivers it stands mostly **alone**, big
  trees scattered along the bank and on raised ground (termite islands in the Okavango), seldom in groves. Its seeds
  are spread by fruit bats, birds and monkeys, so young figs turn up under perches and along the water. It needs
  water near the surface: never far from the river.
- **Doum palm (*Hyphaene thebaica*):** grows in **groves and loose clumps** along seasonal rivers and on floodplains in
  dry country (the Nile, Lake Turkana). Its nuts are spread by elephants and baboons and wash down in floods, so palms
  come in patches. A clump lives on by suckers, so a whole dead clump is rare.

## The picks (my picks unless marked; the user may change any)
- **Where:** only on the floodplain biome (`antcolonies:savanna_floodplain`, the greener grass), on dry ground (never
  in water, a pool or the 4-block strip by the river).
- **Dry trees off the floodplain (the user):** no umbrella thorn, camel thorn or baobab stands or sprouts with its foot
  on floodplain ground, in new land or in play. One check in one place (`TreeKinds`, "may this kind stand here?"),
  asked by both the new-land placer and the seedling chooser (`Forest.sproutSite`), so the rule is never written twice.
- **Spacing (open grass for the wildlife, the user's wish):**
  - Fig: grown figs **one about every 60 to 80 blocks along the floodplain**, within about 30 blocks of the water;
    young figs as often again, near the water. Never two grown figs within 40 blocks.
  - Doum: **groves of 2 to 5 palms**, a palm 8 to 15 blocks from the next in its grove, groves about **every 50 to 70
    blocks**; young palms and seedlings at a grove's edge.
  - Neither within 12 blocks of a termite mound or a giant dry tree.
  - This is about the riverbank of the viewer (2 figs and 5 palms on 130 blocks of bank), a little sparser.
- **Ages:** the same spread as the dry trees (most mature, about 15 in 100 grown giants), every viewer age used.
- **Dead (the user): 1 in 8**, split over the three stages (my pick: just died 3, breaking up 3, fallen or last snag 2).
  Dry trees keep their 28 in 100.

## The hard part, said plainly: the palm crowns
Counted from the viewer (average of 4 seeds):

| Tree | full blocks | sculpted boxes | blocks holding sculpted boxes |
|---|---|---|---|
| fig, grown | 6,467 (4,888 leaves) | 929 (figs, leaf sprays) | 166 |
| fig, mature | 2,893 | 611 | 105 |
| doum, grown | 224 | **11,118** (fronds, nuts, stubs) | **1,819** |
| doum, clump | 144 | 6,909 | 1,169 |

The fig is nearly all full blocks, tapering branches, root slabs and leaves, which the game already has the kinds of
(the dry trees' `TaperingBranchBlock`, leaves). Its sculpted pieces (fig bunches, figs on the bark, leaf sprays) become
a few new small blocks with a handful of looks each, like cocoa pods on a jungle log.

The doum palm is the opposite: almost everything that makes it look like a palm (the arching fronds, the nut bunches,
the leaf-base stubs) is sculpted, about 1,800 blocks' worth of small boxes in odd places. Minecraft draws a block from
a model made in advance, so a frond cannot be put down box by box. **My pick: a frond block family** (a frond cut into
pieces made in advance: stalk, fan and hanging tip, turned to 16 directions, three droops), so a crown is built from
frond pieces like the dry trees are built from branch pieces; insects and players can walk through fronds but not
stand on them. A Minecraft model turns only in steps of 22.5 degrees, so a frond's direction snaps to the nearest of
16: close to the viewer, not exact. **Fallback if that looks wrong or costs too many frames:** one drawn crown per stem
(a block at the stem's top that draws its whole crown as one model), exact like the viewer but drawn every frame, so it
is measured first. Either way the stems are full blocks you can climb.

## Steps (each ends with `./gradlew build` and the `trees` and `worldgen` tests on 6 servers)
1. **Measure the floodplain:** how wide it is in blocks and how many blocks of it a typical river has, on the survey
   seeds (`SavannaWorldTests`), so the spacing above fits the real land (a floodplain 20 blocks wide holds a line of
   trees, not groves). Retune the spacing if needed, and say so.
2. **Dry trees off the floodplain** (the one check, new land and seedlings). Test: new land on the survey seeds has no
   dry tree foot on floodplain ground; the mound survey bands still hold (dry trees also give the termites their dead
   wood: count how many mounds near rivers lose their dead tree, and say so).
3. **The fig's blocks:** fig log and wood (bark A in its tiles, young bark, dead bark, bare grey wood), fig branch
   (tapering, the same thicknesses), fig leaves, root slabs, fig bunch, figs on the bark, leaf spray, fig seedling;
   textures written by `tools/floodplain_tree_art.py` into the game's folders. Test category `trees`.
4. **The fig's shapes in Java** (`tree/FigShape`, from `fig()`, `figSeedling`, `figSapling`, `deadFig`, `figFallen`;
   one seed one tree, as in the viewer), checked block for block against the viewer for a few seeds (a small export
   from the viewer, compared in `./gradlew test`).
5. **The doum's blocks:** stem bark A and the dead grey bark, the frond pieces, the spear, the crown cap, nut bunch
   (ripe and green), leaf-base stub, seedling and rosette. Look checked in the viewer first: the frond pieces drawn the
   way the game will draw them (snapped to 16 directions) beside today's, for the user's OK.
6. **The doum's shapes in Java** (`tree/DoumShape`, from `doum`, `doumSeedling` to `doumClump`, `deadDoum`), checked
   against the viewer the same way.
7. **New land:** a floodplain tree structure (its own set, so it never crowds the dry trees' spacing), figs alone and
   doum groves by the spacing above, every age, dead 1 in 8; new checks in `worldgen`: only on floodplain ground, the
   spacing, the dead share, no tree in water. Then the user's look in a new world.
8. **Life in play** (after the user's look; asked again first): growing through the ages, dying, the dead stages, the
   fig falling, fire (doum stems resist it, a real trait), seedlings on the floodplain only, through the tree book
   (`tree/Forest`); dead fig wood as termite food (soft, eaten quickly), doum wood hard and slow (my picks).

## For the balancer
- Dry trees leaving the floodplain take away some dead wood near rivers; mounds near rivers may have fewer feeding
  spots. Step 2 counts it.
- Dead figs become termite food in step 8, which puts some of it back near the water.
- Mounds already keep off the floodplain's lowest ground; the floodplain trees keep 12 blocks off mounds, so the two
  never fight for one spot.

## Not in this plan
The fever tree (dropped by the user), the jackalberry, the whistling thorn and its ants (ideas only), fruit eaten by
animals (M9.5), seasons (fruiting time later from the shared season source).
