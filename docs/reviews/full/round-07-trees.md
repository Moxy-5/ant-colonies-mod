# Full code review, round 7: the giant savanna trees

Reading round (no code changed, no test run, nothing measured in the game). Read 2026-11-23/24 on branch
`claude/review-read-7-trees-a66aa2`. Rule book: `docs/plans/code-review.md`.

**What was read, every line:** `savanna/.../tree/` (all 23 files), the tree placing in `savanna/.../worldgen/`
(`TreePlacer`, `GiantTreeStructure`, `GiantTreePiece`, `FloodplainTreeStructure`, `FloodplainTreePiece`,
`GroundHeights`, `FallenTreeFeature`, `FallenBranchFeature`, `QuietSavannaTreesFeature`), the tree hooks in
`SavannaEvents`, the tree data files (`tree_kind/`, the structure sets), and the tree blocks in `savanna/.../block/`
(see "Pieces done" below). The five shape makers and the tree blocks were read by two helper readers; every finding of
theirs kept here was checked against the code again by the main reader.

**Pieces done (the file is saved after each):**
1. The tree book and a tree's life (`Forest`, `TreeLife`, `TreeFall`, `HeartRot`, `BranchShedding`, `TreeFire`,
   `DeadWood`, `Litter`, `RotMarks`): done.
2. Placing (`TreePlacer`, the structures and pieces, `GroundHeights`, the features): done.
3. The shape makers (`AcaciaShape`, `UmbrellaShape`, `BaobabShape`, `FigShape`, `DoumShape`, `PalmPieces`,
   `TreeShapes`, `TreeDraw`, `TreeVoxels`, `TreeKind`): done.
4. The tree blocks: **not written yet** (added in the next save).

**How to read a finding.** Rating: **A** the player can meet it, **B** it will cost us when building on this,
**C** a tidy-up. Proof: **proven** (the path was walked through the code line by line), **likely** (the code says
so, but part of the path is Minecraft's own code read from memory, or it needs a number), **to measure** (a lag
finding with no number yet: it names the scene that would give one). Nothing here was run; no finding has a failing
test yet.

## The findings at a glance

| # | Rating | Proof | In one line |
|---|---|---|---|
| R7-1 | A | proven, cost to measure | A grown tree coming back from days away counts every block under its crown once for each missed day: a freeze of a tenth to a few tenths of a second per tree |
| R7-2 | B | likely, to measure | Once a second every tree in the whole world is looked at, loaded or not, always from the start of the list; in a thunderstorm the trees at the end of the list can go unserved |
| R7-3 | B | to measure | A tree's growth step is done whole in one tick, and all loaded trees come due at the same dawn, one a second |
| R7-4 | B | proven, cost to measure | A storm breaking a hollow tree works out three whole tree shapes on the server's own thread, and may touch land that is not ready |
| R7-5 | B | to measure | A tree falling in sight makes up to 2,500 display entities in one tick and re-poses them all every 3 ticks |
| R7-6 | B | to measure | The whole tree book is written to disk at every autosave; it only ever grows |
| R7-7 | B | to measure | Four shape stores (256, 256, 512, 256 shapes) are never emptied, also not between worlds |
| R7-8 | C | proven | Each dead standing acacia shape builds the whole living tree again; the "mud" stage builds 9 shapes that are the snag's |
| R7-9 | C | proven | Small waste in busy loops (boxed numbers in the flood fill, a fire's whole-shape scan, seedling sites worked out before the dice) |
| R7-10 | A | likely | Fire: green leaves scorch to brown leaves, and brown leaves burn away, so a fire that reaches a crown can eat the crown of a living tree; it never grows back |
| R7-11 | A | proven | A falling tree smashes termite mound soil, fungus comb and the Matabele prey pile in its way, and the log is then laid through the mound |
| R7-12 | B | likely, to measure | Lightning: every bolt within 24 blocks goes to a big tree; 1 in 3 kills it, 3 in 4 of the rest get heart rot. My sum: about 1 big tree in 8 near the player each thunderstorm |
| R7-13 | B | likely, to measure | Seedlings after rain add big trees much faster than they die, and fallen logs stay for good: the open savanna closes up over 100 and more days |
| R7-14 | B | proven | Termites hollow baobabs and make them age twice as fast, though the rot code leaves baobabs out on purpose |
| R7-15 | A | proven | Trees of new land made in the last second before quitting never get into the tree book: they never grow, shed or die. A crash loses up to 5 minutes of them |
| R7-16 | A | proven | A rot-killed log on a slope crumbles only up to its flat-ground height: wood is left hanging in the air for good |
| R7-17 | A | proven | A young tree that dies leaves a branch block sunk into the ground where its grass block was |
| R7-18 | B | proven | One tree record with an unknown kind makes the whole tree book fail to load; an empty book then replaces it |
| R7-19 | B | proven | A fallen log's record never ends (unless the rot killed it), even when the log is long gone |
| R7-20 | B | proven | Felling a tree by storm or command checks only the foot's chunk, not the tree's whole box |
| R7-21 | C | proven | An unknown age name in a saved floodplain tree crashes chunk making; the tree kind files are not checked |
| R7-22 | C | likely | The fall is drawn from the tree's flat-ground shape: loose limbs round a dead tree on a slope are looked for at the wrong height |
| R7-23 | C | likely | Minecraft's own trees in a small savanna are skipped wherever grass stands on the spot: fewer than Minecraft's |
| R7-24 | B | proven | The tree kind files are read from inside the mod, not from data packs, and `umbrella_thorn.json` holds numbers nothing reads |
| R7-25 | C | proven | Trial and leftover code, and comments that no longer match |
| R7-26 | B | proven | Test gaps on the risky findings above |

---

## 1. Lag

### R7-1 (A, proven path, cost to measure). Catching up on missed days counts the ground under the whole crown once for every missed day

**What is wrong.** A grown tree drops a dead branch on most days (4 days in 10 when just grown, every day when
ancient, twice that when hollow). Before it drops one, the code counts the fallen branches already lying under the
tree, by looking at every block in a box as wide as the crown and 13 blocks high. When a tree's land was away and
comes back, the tree lives every missed day at once, in one go inside one tick, and that count is done again for every
missed day on which the dice say "drop a branch". It is done again even after 3 branches lie there and no more may
fall.

**What the player sees.** Coming back to a savanna left for many in-game days (or after `/time add`): a hitch as each
big tree's land loads. For an umbrella thorn (crown 45 to 56 blocks wide) the box is 26,000 to 41,000 blocks, each
read with a "is this land ready" check; about 3 to 5 ms a count (my estimate). 100 days away and a chance of 0.7 a day
is about 70 counts: **0.2 to 0.35 s for one tree**, and the trees of a landscape come due one after another, one a
second.

**Where.** `tree/Forest.java:548-579` (the loop over missed days; line 574-575 calls `BranchShedding.shed`),
`tree/BranchShedding.java:61-63` (`shed` counts first) and `87-100` (`lyingUnder`, the box loop with `LandReady.at`
per block).

**Proof.** `update` → `while (t.lastDay < today)` → `shedDice.nextDouble() < shedChance && BranchShedding.shed(...)`
→ `lyingUnder(level, foot, now) >= MAX_UNDER_TREE` → `BlockPos.betweenClosed(lo, hi)` over
`(maxX-minX+1) x 13 x (maxZ-minZ+1)`. Nothing limits the days; the time budget (`BUDGET_NANOS`, 4 ms) is only looked
at between trees, never inside one.

**The same count in normal play (B).** Every loaded grown tree does this count once a day (and once per storm
shake): 3 to 5 ms per tree per day by itself.

**To measure.** A game test or the stopwatch (`Perf.Part.TREES`): an ancient umbrella thorn put in the book with
`lastDay` 100 days back, one `forest.update`.

**Proposed fix.** Count once per `update`, before the loop, and keep the number in a local as branches are added
(stop asking once it reaches 3). Cheaper still: count only the columns' top blocks (the ground's surface) instead of a
13-high box.

### R7-2 (B, likely, to measure). The once-a-second walk looks at every tree in the world, and always starts from the same end

**What is wrong.** Three things in one loop:
1. Once a second the book makes a copy of the list of **all** trees ever met in the dimension (`all()`) and walks it.
   Trees in land that is not loaded are not skipped first: each still gets its storm checks and an `update` call that
   only then finds its land is away.
2. Two of those checks read the biome under the tree's foot (`level.getBiome`) while it rains or thunders
   (`BranchShedding.stormShakes`, `HeartRot.stormKills`, `Forest.stormFells`). For land that is not loaded Minecraft
   works the biome out from the world's shape each time (slow, a few thousandths of a ms each); for a chunk that is
   wanted but still being made it can make the server wait for it.
3. The walk stops when 4 ms are used up ("the rest next second"), but the next second it starts again **from the
   start of the same list**, not where it stopped. Trees late in the list are served only if the whole walk fits.

**What the player sees.** Nothing while the book is small. In a long world (thousands of trees met) in a
thunderstorm: the trees near the player may be the ones at the end of the list, so they do not shed in the storm, the
dead ones do not come down in it, and their daily growth waits until the storm is over. Storms also act in real time
on trees far away in land that is not loaded (they can be storm-killed there, each living hollow one 1 in 2,400 a
second of thunder), while their days are only caught up later: near and away disagree.

**Where.** `tree/Forest.java:426-460` (the walk; `all()` at 341; the `break` at 427-428), `436`, `447`, `453`,
`512-518`; `tree/BranchShedding.java:51-54`; `tree/HeartRot.java:142-145`.

**Proof.** `second` → `for (Tree t : all())`; no `LandReady` check before `stormShakes` / `stormKills` /
`stormFells`; `update` is where `LandReady.at(level, t.foot)` is first asked (line 525). `all()` is
`new ArrayList<>(trees.values())`, whose order is the same every second. The cost per far tree outside storms is
small (my estimate 0.1 to 0.2 thousandths of a ms), so the 4 ms are only reached by a big book in a storm: **likely,
not proven**.

**To measure.** A book of 5,000 trees with no land loaded, thunder on: the time of `Forest.second`
(`Perf.Part.TREES`) and how many trees it reaches.

**Proposed fix.** Walk only the trees of loaded chunks (the book already keeps them by chunk, `byChunk`), and keep
the place in the list between seconds so the walk goes round. Storm effects then only happen to loaded trees; a tree
away keeps to its day-by-day catch-up.

### R7-3 (B, to measure). A tree's growth step is one tick's work, and all trees come due at the same dawn

**What is wrong.** A growth step (`place` → `TreePlacer.grow`) puts the old shape in a hash map, walks the new shape
and changes every block that differs, with full block updates (neighbours told, light). It all happens inside one
tick. Every loaded living tree comes due at the same moment (the day number changes for all of them at 6:00), and the
4 ms budget then lets about one heavy tree through each second.

**What the player sees.** After each dawn, for as many seconds as there are loaded trees to step (about 160 at view
distance 12 by my sum: 40 big, 120 young), one tree-sized piece of work a second. Young trees change shape every day
(grown ones about every 4th day, the shapes being kept to the hundredth of an age). If one step is more than a few ms
this is a faint once-a-second stutter for the first minutes of every day. With a very wide loaded area (several
players spread out, or view distance 32, about 1,000 trees) one a second no longer gets through all of them in a
20-minute day; the ones left over then count as "more than one day behind" and wait until nobody looks.

**Where.** `tree/Forest.java:426-429` (budget), `944-986` (`place`); `worldgen/TreePlacer.java:1026-1093` (`grow`).

**To measure.** One `forest.update` of a young umbrella thorn from age 0.30 to 0.34 and of a nearly grown one from
0.90 to 0.91, in sight: the tick's length and the number of blocks changed.

**Proposed fix (only if the number is bad).** Spread the trees over the day by their seed (each tree's "dawn" at its
own moment), and split one big step over a few ticks.

### R7-4 (B, proven path, cost to measure). A storm breaking a hollow tree works out three shapes on the server's own thread

**What is wrong.** Everywhere else a shape that is not ready is worked out in the background and the tree waits
(`placedSoon`). `fellNow` (a thunderstorm breaking a living hollow tree, and the test command) asks for three shapes
at once and waits for them on the server's thread: the tree as it stands, shed, and fallen. Each dead standing shape
also builds the living tree again inside it (R7-8), and the fallen one builds the shed one first.

**What the player sees.** A hitch of some tens of ms at the moment a storm snaps a hollow tree (rare: 1 in 2,400 a
second of thunder for each living hollow tree).

**Where.** `tree/Forest.java:660-683`, lines 666-668 (`TreePlacer.placed` three times); called from 447-451.

**To measure.** The fall command on an ancient hollow camel thorn whose shapes are not in the store.

**Proposed fix.** Ask with `soon` and let the fall wait a second if a shape is not ready (the tree is marked to fall;
`update` already handles "its shape is being worked out").

### R7-5 (B, to measure). The fall in sight: up to 2,500 entities made in one tick

**What is wrong.** When a dead tree lets go, one tick makes a block display entity for every outer block of the
standing wood (up to `MAX_DISPLAYS` 2,500), takes every moving block out of the world, and from then on every 3rd tick
gives each display a new pose (a data packet for each display to each player in range) for 3 seconds.

**What the player sees.** Possibly a hitch at the moment the tree goes, and a dip in frames while 2,500 displays
swing. A shed dead tree is mostly trunk, so the real count is likely well under the cap; not counted.

**Where.** `tree/TreeFall.java:217-245` (`letGo`), `248-257` (`pose`), `59` (the cap).

**To measure.** The existing falling-tree test with the stopwatch on, and the number of displays for an ancient
umbrella thorn and camel thorn; frames by eye.

**Proposed fix (only if the number is bad).** A lower cap (the trunk's outside only), or one display per few blocks.

### R7-6 (B, to measure). The whole tree book is written at every autosave, and it only grows

**What is wrong.** Every `update` that gets as far as line 595 marks the book as changed, so it is always "dirty" and
Minecraft writes the whole of it (every tree of the dimension, 23 numbers each) at every autosave, on the server's
thread. Records only leave the book when a little tree dies, a trunk is cut at the foot, a baobab's heap is gone or a
rot-killed log has crumbled (see R7-19).

**What the player sees.** Nothing in a young world. In a long, widely explored one, a short hitch at each autosave
that grows with the number of trees ever met (my estimate: 5,000 trees is under 2 MB to build and squeeze, 10 to 30
ms).

**Where.** `tree/Forest.java:595` (`setDirty()` whether or not anything changed), `1288-1304` (`save`).

**To measure.** `Forest.save` on a book of 5,000 and 20,000 trees.

**Proposed fix.** Mark the book changed only when a field really changed; later, if the number is bad, save by region.

### R7-7 (B, to measure). Four shape stores are never emptied

**What is wrong.** Worked-out shapes are kept in static stores: `TreePlacer.CACHE` (256), `GiantTreePiece.FITTED`
(256) and `LITTER` (512), `FloodplainTreePiece.SHAPES` (256). They are static, so they are not emptied when the world
is closed and another is opened. A big tree's shape is about 6,000 to 13,000 blocks; each growing tree adds a new
shape every hundredth of an age.

**What the player sees.** Memory: my estimate is 20 to 85 MB held for good once the stores are full (the high end
only if most are giants). No wrong behaviour: the keys hold the seed, so another world's shapes are never used by
mistake.

**Where.** `worldgen/TreePlacer.java:56-62`, `worldgen/GiantTreePiece.java:71-82`,
`worldgen/FloodplainTreePiece.java:83-88`.

**To measure.** A heap count after a long flight over new savanna.

**Proposed fix.** Empty them when the server stops; size them by measured need.

### R7-8 (C, proven). Dead shapes do the living tree's work again

- `tree/TreeShapes.java:37-39`: every standing dead acacia shape (snag, "mud", shed) builds the **whole living tree**,
  crown and all, only to ask "was there wood here". A fallen shape builds the shed one first
  (`AcaciaShape.java:220`, `UmbrellaShape.java:279`). So a dying tree's crown is built 4 times and thrown away.
- The "mud" stage has had no mud since 2026-11-13 (`DeadWood.weather` ignores the stage; nothing makes a
  `TreePart.MUD` any more), so its shape is the snag's block for block, but `TreePlacer.between` (line 570) still
  builds it and 8 "blends" between two equal shapes, each taking a place in the store.

Not a lag the player feels (the work is in the background or in world making), but it is free to remove: a "wood
only" build for the question, and the mud stage using the snag's shape.

### R7-9 (C, proven). Small waste in busy places

- `tree/TreeVoxels.java:129-149` (`grounded`): a boxed number and a new 6-long array for every block, 1 to 3 times
  per shape.
- `tree/Forest.java:779-785` (`inFire`) and `1157-1165` (`isPart`): for each of up to 6 blocks a fire touches, a
  walk through the tree's whole shape (thousands of blocks), for every tree within 30 blocks, at every new fire block.
- `tree/Forest.java:1207`: a seedling's site is worked out (heightmap, biome, the big-savanna gate) before the dice
  say whether it takes; 4 tries in 10 are thrown away. After a very long absence one square of land can ask for up to
  1,600 sites in one tick (`RAIN_MEMORY` 400 days, 4 tries), outside the time budget.
- `tree/Forest.java:692-700` (`crumble`): for every rot-killed log, every second: its crumble day is worked out
  afresh, and while it crumbles the order of its columns is sorted again each second.

---

## 2. Behaviours that fight each other

### R7-10 (A, likely). "Char, never vanish" against "brown leaves burn away": a fire can eat a living crown

**What is wrong.** Two fire rules meet:
- A fire next to a green leaf turns it into the kind's brown, withered leaf (`TreeFire.changed`, by design: "leaves
  scorch brown").
- Brown leaves are set to burn away fast (`TreeBlocks.setFlammable`: dead leaves 60, 100; green leaves 30, 0).

So the fire first makes its own fuel: it scorches a leaf, then burns the scorched leaf, the new fire scorches the
next leaves, and so on through a crown whose leaves all touch. Our own lightning rule sends every strike near a big
tree **to the top of its crown** (R7-12), and Minecraft's lightning lights fires where it lands.

Two smaller parts of the same thing: a scorched leaf that does not burn stays brown for good on a living tree (a
growth step only changes blocks whose shape changes: `TreePlacer.grow`, `if (state.equals(was)) continue`), and
leaves burnt away are never put back for the same reason.

**What the player sees.** After a thunderstorm or a grass fire that reaches low leaves: a living giant with a hole
burnt through its crown, or bare, that never greens again (it still counts as living; it is not a dead tree's
shape). The design says most trees live through fire with a scar.

**Where.** `tree/TreeFire.java:27-57`; `registry/TreeBlocks.java:379-387`; `worldgen/TreePlacer.java:1041-1043`.

**Proof and what is unsure.** The two rules are as written. That Minecraft's fire then spreads through the crown is
read from Minecraft's fire code from memory (a block with burn odds 100 beside a fire burns in about a third of fire
ticks, and is replaced by fire or air; rain puts out only fire that can see the sky). The trunk is safe: its burn odds
are 0, so fire cannot sit beside it in the air (I checked that path too). **Likely**, to be shown by a test: a fire
set on top of a living crown, 60 seconds, count the green leaves.

**Proposed fix (the user's call, it changes the look).** My recommendation: scorched leaves on a living tree do not
burn (a "scorched" leaf that fire cannot eat, or dead leaves' burn odds 0 while the tree lives), and a living tree
puts its green leaves back over the next days. Dead trees' brown leaves may keep burning.

### R7-11 (A, proven). A falling tree breaks through a termite mound

**What is wrong.** A falling tree smashes every block in its way that is softer than stone (hardness under 1.5) and
is not ground for a shovel. Termite mound soil (0.6), mound pieces (0.6), fungus comb (0.3), the forage pile, the
Matabele prey pile (0.1) and termite remains are all under that line and none is in the shovel list. So the swinging
tree, and the place where the log comes to lie, cut straight through a living mound; the blocks drop as items, and
the log is then placed in the gap. The same happens out of sight (`clearWhereItLies`).

The dead trees new land puts by each mound for the raids stand about 22 blocks from the mound's middle; an umbrella
thorn's log is about 16 blocks long with limb stubs reaching about 40 from the stump. A tree that falls toward its
mound reaches it.

The tree code's own growing rule says the opposite: a growing tree goes round "a colony's block", never into it
(`TreePlacer.grow`'s note).

**What the player sees.** A mound with a slot knocked through it and a dead log lying in the slot, mound soil items
on the ground. What the colony then does with the hole is round 5's question (termites).

**Where.** `tree/TreeFall.java:337-346` (`smashes`), `260-282` (`sweep`), `352-371` (`clearWhereItLies`);
`registry/SavannaBlocks.java:39, 47, 65, 71, 76, 287` (the hardness numbers);
`data/minecraft/tags/block/mineable/shovel.json` (no mound block in it).

**Proof.** `smashes` returns true when `hardness >= 0 && hardness < 1.5f && !state.is(MINEABLE_WITH_SHOVEL)`;
`TermiteMoundBlock` does not change its hardness (it only overrides how fast a player digs it).

**Proposed fix (the user's call).** Real Macrotermes mound walls are as hard as brick; a real tree comes to rest on
the mound. My recommendation: mound and nest blocks stop the log like stone (one block tag, "a falling tree does not
break this"), and the log lies over them the way it already lies over a hill.

### R7-12 (B, likely, to measure). Lightning takes big trees faster than old age

**What is wrong.** Every lightning bolt that lands within 24 blocks of a big standing tree is moved to that tree
(`SavannaEvents.onJoinLevel` → `Forest.lightning`). Each strike kills a living tree 1 time in 3 (2 in 3 when hollow),
and a tree that lives through it gets heart rot 3 times in 4, which hollows it in 16 days, makes it age twice as fast
and lets a later storm break it. Half the strikes also light a fire at the foot.

**My sum (to measure).** In a thunderstorm Minecraft strikes about once every 8 seconds somewhere in the land round
a player (about 600 chunks, 1 chance in 100,000 a chunk a tick). New land has one big tree to each 64-block square, so
roughly 4 strikes in 10 land within 24 blocks of one. A 5-minute storm: about 15 strikes on trees, about 5 big trees
killed outright and most of the rest doomed, out of about 35 standing in view. That is about 1 in 8 per storm, against
lives of 75 to 250 days by old age.

**Also (Minecraft's rules meeting ours).** A player's Channeling trident makes a real bolt at the mob it hit; that
bolt is moved to the tree too, so the mob is not struck. Any `/summon lightning_bolt` near a tree goes to the tree.

**What the player sees.** Many dead and falling trees after each thunderstorm; a savanna whose big trees thin out
storm by storm (and fill up again with seedlings, R7-13). A Channeling trident that does not work near big trees.

**Where.** `SavannaEvents.java:144-152`; `tree/Forest.java:808-849` (`lightning`), `856-909` (`struck`);
`tree/TreeLife.java:226-229` (the 1 in 3); `tree/HeartRot.java:43` (0.75).

**To measure.** A soak with a watcher: big trees standing before and after each thunderstorm in a fixed square.

**Proposed fix (the user's call, a balance number).** Only one strike in a few is drawn to a tree, or a tree is
drawn to at most once a storm; bolts that did not come from the weather are left alone.

### R7-13 (B, likely, to measure). Seedlings after rain fill the savanna in

**What is wrong.** The rates do not balance against new land's own spacing:
- New land: 1 big tree and about 3 young ones to each 64-by-64 square.
- After each day with rain, each loaded square tries 4 seedlings and 6 in 10 take where the site suits
  (`SPROUT_TRIES`, `SPROUT_CHANCE`, doubled 2026-10-25 for the raids' dead wood). 1 seedling in 5 becomes young and 1
  young in 2 grown: 0.24 new grown trees per square per rain day.
- Minecraft's weather gives about one day in three some rain (less if the player sleeps through it).
- So about **0.09 new grown trees per square per day**: over 100 days about 9, against the 1 that new land put
  there. They live 75 to 250 days. And every tree that dies of age ends as a fallen log that stays "for good"
  (R7-19), about as many logs per day as trees grow up.

The only brake is that no seedling comes up under a crown or within 4 blocks of another tree's foot.

**What the player sees.** Round a long-lived base the open savanna slowly turns into closed woodland with logs lying
everywhere, crown to crown, in 100 to 200 in-game days. The design says the dying seedlings "keep the savanna open".
More trees also mean more work in R7-1 to R7-3 and more dead wood for the termites than the mound spacing was
balanced for.

**Where.** `tree/Forest.java:79-92` (the numbers), `1198-1217` (`sproutIn`); `tree/TreeLife.java:33-36`;
`data/antcolonies/worldgen/structure_set/big_savanna_trees.json`, `young_savanna_trees.json`.

**To measure.** A soak of 200 days with a count of living grown trees and fallen logs in a fixed 256-block square.

**Proposed fix (the user's call, it changes the look).** Tie the seedlings to the trees already there: a square of
land takes seedlings only up to a target count of trees (new land's own density), so the rate falls to the rate at
which trees die. And let old sound logs weather away in the end.

### R7-14 (B, proven). Termites hollow baobabs, which nobody decided

**What is wrong.** Heart rot leaves baobabs out on purpose (`HeartRot.canRot`: "baobabs hollow on their own"). The
termite rule next to it has no such check: any tree past its young look whose foot is inside a fungus-growing
colony's foraging reach gets `termites = true`, ages twice as fast, and gets the termite hollow cut into its shape.
The hollow maker was written and tested for acacia trunks (5 wide); in a baobab's trunk (9 to 13 wide) it cuts a room
about 7 wide and 7 high with a door at the foot, and in an ancient baobab it meets the baobab's own hollow.

**What the player sees.** Baobabs near termite mounds with a big cave in the trunk, dying at half their age (250 to
500 days instead of 500 to 1,000).

**Where.** `tree/Forest.java:553-557` (no kind check); `tree/TreeShapes.java:40-42`, `68-107`;
`tree/HeartRot.java:82-84` (the check the rot has).

**Proposed fix.** Ask the user; my pick is to leave baobabs out (as far as I know Macrotermes do not hollow living
baobabs, whose wood is wet fibre; not checked against a paper), one line beside the age check.

---

## 3. Bugs

### R7-15 (A, proven). New land's trees can miss the tree book

**What is wrong.** When world making puts a giant tree in, the tree is put on a waiting list in memory
(`Forest.QUEUE`). The server takes the list into the book once a second. The list is not saved, not taken in when the
world is saved or closed, and nothing ever looks at a loaded chunk's trees to see whether the book knows them. So:
- trees whose chunk was made in the last second before the player quits are never in the book;
- after a crash, every tree made since the last autosave (up to 5 minutes of flying) whose chunk did get saved is
  missing from it;
- the list is static: left-over trees from one world are written into the next world opened in the same sitting (the
  book of the next world drops most of these again when it finds no trunk there, but a fallen log's record is never
  checked, so that one stays as a ghost; R7-19).

**What the player sees.** Here and there a giant tree that is frozen for good: it never grows, drops no branches,
never dies or falls, does not draw lightning; a dead snag among them stands for ever. Nothing marks which ones.

**Where.** `tree/Forest.java:104` (the list), `299-302` (`queue`), `393-401` (taken in only when
`gameTime % 20 == 7`); `worldgen/GiantTreePiece.java:166`; `SavannaEvents.java:210-213` (the server-stopping hook
clears only the raid view).

**Proof.** The only reader of `QUEUE` is `Forest.tick`; no save, stop or chunk-load hook touches it; the structure
piece keeps the tree's kind, seed, age, stage and foot (`GiantTreePiece.addAdditionalSaveData`) but nothing reads
them back into the book.

**Proposed fix.** Take the list in before every save and at server stop, and empty it when the server stops. The
sturdy fix: when a chunk loads, look at its giant tree pieces (their records are in the chunk) and write any tree the
book does not know and whose foot still stands; that also heals worlds already hit.

### R7-16 (A, proven). A rot-killed log on a slope leaves wood hanging

**What is wrong.** A fallen log is laid on the land it falls on: each slice is moved up or down to sit on its own
ground (`TreePlacer.rest`). When a rot-killed log later crumbles to pulp, column by column, the code takes the columns
and their top heights from the log's **flat-ground** shape, not from the shape as laid. Each column is cleared from
"flat top + 1" downward. Where the ground under a column is 2 or more blocks higher than the tree's foot, the log's
upper layers sit above that start and are never cleared; the layers under them are. When the last column is done the
tree's record ends, so nothing ever comes back for them.

**What the player sees.** Where a hollow tree fell uphill: after it has crumbled, pieces of its upper wood left
hanging in the air over the pulp heaps, for good.

**Where.** `tree/Forest.java:691-711` (`crumble` takes `soon(...)`, the raw shape); `tree/HeartRot.java:264-277`
(`crumbleOrder`) and `290-327` (`crumbleColumn`, the scan from `foot.getY() + column[2] + 1` down);
`worldgen/TreePlacer.java:145-222` (`rest` moves each block by its column's need).

**Proof.** `place` lays the log with `next = TreePlacer.rest(next, ...)` (line 947) but `crumble` uses
`lying = soon(...)` with no `rest`; `rest` adds `need` (the ground's height over the foot less the log's underside) to
every block of a column; `crumbleColumn` never looks above the raw top + 1.

**Proposed fix.** `crumble` rests the shape on the land the same way before it takes the columns (the land under a
lying log does not change the answer much; or scan each column from the top of the box down).

### R7-17 (A, proven). A dead sapling leaves a stick sunk in the ground

**What is wrong.** A young tree's trunk line starts one block under the ground (y = -1). While the trunk is thinner
than a full block (an umbrella thorn up to about age 0.16, a camel thorn up to about 0.39) that underground block is a
thin branch block, and the placer puts it into the soil in place of the grass block. When the young tree dies
(`Forest.die`), `TreePlacer.remove` takes every block of it away except those under the ground ("the roots stay").
About half of all young trees die.

**What the player sees.** Where a young tree died: a one-block pit in the grass with a thin branch post in it (and
the fallen branch the dead tree leaves beside it). They add up over the days.

**Where.** `tree/AcaciaShape.java:92`, `tree/UmbrellaShape.java:126` (the line from y = -1);
`tree/TreeDraw.java:205-207` (thin wood is a branch line); `worldgen/TreePlacer.java:896-903` (placed into soil),
`1099-1113` (`remove` skips `ys < 0`); `tree/Forest.java:1052-1061`.

**Proposed fix.** `remove` puts soil back where the block under the ground is a thin branch (or the shape never puts
thin wood under y = 0).

### R7-18 (B, proven). One bad record empties the tree book

**What is wrong.** Loading the book makes each tree with `new Tree(...)`, which at once asks for its kind's file
(`TreePlacer.kind` → `TreeKind.load`); an unknown kind name throws. The error is not caught per tree, so the whole
book fails to load; Minecraft logs it and carries on with an **empty** book, which is then saved over the old file.

**What the player sees.** Only after a kind is renamed or removed in an update (or a damaged file): every tree in
the world is frozen for good, as in R7-15, with one line in the log.

**Where.** `tree/Forest.java:152-161` (the constructor calls `treeKind()`), `229-231`, `1306-1310`;
`tree/TreeKind.java:105-116`.

**Proposed fix.** Load each record inside its own try; skip and log a bad one.

### R7-19 (B, proven). A fallen log's record never ends

**What is wrong.** Once a tree lies fallen (`settled()`), the once-a-second walk skips it for good, before the check
that ends a record whose trunk is gone. Only logs of trees the rot killed crumble and end. So the record of every
other fallen tree stays for ever: after termites ate the log, after the player took it, and for ghost records
(R7-15). Each one keeps seedlings 4 blocks away from a foot that is no longer there, and adds to the walk (R7-2) and
the save (R7-6).

**Where.** `tree/Forest.java:177-180` (`settled`), `430-435` (skipped before `update`), `1260` (the spacing asks the
book).

**Proposed fix.** Now and then (once a day is plenty) a settled log whose land is loaded is checked: no block of its
own left means the record ends.

### R7-20 (B, proven). Felling checks the foot's chunk only

**What is wrong.** `fellNow` asks only whether the chunk under the foot is ready, then changes blocks over the tree's
whole box (a crown and a log reach 2 to 3 chunks). `inFire` kills a young tree and removes its blocks with no such
check at all. Everywhere else the rule is "every chunk of the box is ready" (`loaded`). Reading or setting a block in
a chunk that is wanted but still being made makes the server wait for it (the stalls of 0.5 to 3.7 s that
`LandReady` was written to end).

**What the player sees.** Rarely, a freeze when a storm breaks a hollow tree at the very edge of the land being
made.

**Where.** `tree/Forest.java:662` (only `LandReady.at(level, t.foot)`), `677-681`; `766-791` (`inFire` → `die`).

**Proposed fix.** The same `loaded(level, box)` check as `update`; if not ready, mark the tree to fall and let
`update` do it.

### R7-21 (C, proven). Data that is not checked

- `tree/FigShape.java:55-62`, `tree/DoumShape.java:50-57`: `Age.byId` gives null for an unknown name;
  `worldgen/FloodplainTreePiece.java:96-98` passes it on, and chunk making then fails with a null error. Only reached
  if an age is renamed while old chunks hold the old name.
- `tree/TreeKind.java`: a kind file with an empty `shapes` list, a one-number `fork`, `limbs` or `lifespan`, or
  `bends: 0` crashes or divides by zero when the first tree of that kind is made (`shapeFor` line 101,
  `TreeLife.lifespan` line 99, `AcaciaShape` 88, 93, 310). The three files shipped are fine.

**Proposed fix.** A clear error naming the file and the number; a floodplain piece with an unknown age places
nothing.

### R7-22 (C, likely). The fall is drawn from the flat-ground shape

`topple` hands `TreeFall` the standing tree's raw shape (`now`), while the world holds it rested on the land. The
standing trunk is the same in both, but the loose limbs lying round a shed dead tree are not on a slope: `TreeFall`
looks for them at their flat-ground height (blocks from y = 2 up count as "moving"), so a loose limb that lies
higher or lower than drawn is either left behind or drawn swinging from the wrong place. Small and only on slopes.
`tree/Forest.java:640-645`; `tree/TreeFall.java:112-119`, `234-243`. Fix: rest `now` before the fall, as `place`
does.

### R7-23 (C, likely). Minecraft's trees in a small savanna are thinned by the grass

`QuietSavannaTreesFeature` puts Minecraft's own savanna trees back where the big-savanna gate keeps ours out. It
skips a spot when the two heightmaps differ there ("water, or something already standing"). But they also differ
wherever grass or a flower stands (grass counts for one heightmap and not the other), and the feature runs after the
biome's grass in the same step. So about as many tries are dropped as the ground is grassy; Minecraft itself plants
through grass. `worldgen/QuietSavannaTreesFeature.java:51-56`. Fix: test the ground block and water directly.

---

## 4. The rest

### R7-24 (B, proven). The tree kind files are not the "numbers I can tweak" they look like

- `TreeKind.load` reads `data/antcolonies/tree_kind/<name>.json` from inside the mod's jar
  (`TreeKind.class.getResourceAsStream`), once, for good. A data pack cannot change it and `/reload` does nothing;
  the class note says "the game reads them as data later".
- `umbrella_thorn.json` has form `umbrella`, drawn by `UmbrellaShape`, which uses only the crown's radius, pad radius
  and leaf density, the trunk's radius and spread, the salt, the blocks and the lifespan. Nothing reads its
  `trunk.height` 34 (the real height is the constant 25 in `UmbrellaShape.java:47`), `height_spread`, `fork`,
  `crown.limbs` (the real count is 3 to 4, line 50), `bends`, `dome`, `thickness`, or its two `shapes`. Changing them
  changes nothing.

Against the project rule "a profile I can tweak without touching code". Fix: read what is in the file or take the
dead numbers out; load kinds with the data packs if tweaking in a world is wanted.

### R7-25 (C, proven). Leftover and trial code, comments that no longer match

- **Trial code in the game:** `TreeDraw.LimbLook` B to H, `earlyGrowth`, `straightLine`, the limb-pipe path in `seg`
  (lines 196-204), and every `TreeDraw.look == LimbLook.A` branch in `AcaciaShape`. The game always runs look A;
  only the unit tests and the viewer's export set the others. The notes still say "on trial until the user picks".
- **Mud leftovers:** `TreePart.MUD` is never made; `TreeVoxels.overBark` / `markOverBark` / `isOverBark`, the plate
  code in `TreePlacer.work` (369-386), the MUD cases in `TreeShapes.solid`, `eat` and `DeadWood.heavierSide`, and
  `DeadWood.weather`'s four unused arguments are dead.
- **`AcaciaShape` is only the camel thorn now**: its `tiers` path has no kind left; `TreeKind`'s notes still call
  tiers "the layered umbrella thorn".
- **Unused:** `TreeShapes.hollowOut(TreeVoxels, long)`; `UmbrellaShape.broken`'s `dead`; `FallenTreeFeature` is no
  longer in any biome (only the tests lay one).
- **Comments that disagree with the code:** `FallenBranchFeature` says world making stopped using it at M7.5 step
  6c; `fallen_branch.json` puts it back (M8 step 3F, about 1 chunk in 3). `TreePlacer.java:311` says a shape takes
  "tens of milliseconds"; `docs/plans/savanna-loading.md` measured 1 to 10 ms. `TreeShapes.java:56-57` says a grown
  trunk gets a hollow 1 wide; the sums give a plus shape of 5 blocks.
- **A question that changes things:** `Forest.deadStageNow` (lines 486-498) sets `t.fell = true` while only being
  asked what stage a tree is in (it is also called from `changesNow`), and does not mark the book changed. It comes
  out right today because `update` follows at once; it is a trap for the next change.
- **Test-only members in game code:** `TreeVoxels.dropped` / `dropping` / `isDropped` (one more hash-set call on
  every block put), `TreePart.isLivingLog`, `DoumShape.name`, `FigShape.name`.

### R7-26 (B, proven). Test gaps on what the player sees

Looked at from the game's side (`TreeTests`, `TreeGrowthTests`): the life cycle, the fall, fire's char and scorch,
lightning's scar, the rot stages and the catch-up are tested on flat ground with shapes worked out at once
(`Forest.shapesAtOnce`). Not guarded by any test:
- a tree made by world making and the game stopped before the book took it in (R7-15);
- a rot-killed log crumbling on a slope (R7-16); the ground under a dead young tree (R7-17);
- a tree falling onto a mound (R7-11); a fire left burning in a crown (R7-10: the test at
  `TreeGrowthTests.java:938-949` stops when the leaf has turned brown);
- how many trees a thunderstorm takes, and how many trees and logs a square of land holds after 200 days (R7-12,
  R7-13): numbers nobody has seen;
- the background shape path (`placedSoon`) that the real game always uses: the tests switch it off;
- a baobab over termites (R7-14).

### Parts grown past patching

None. `Forest.update` (lines 521-637) is the part to watch: one 120-line method that keeps five pairs of "what
should be" and "what is in the world" (`age`/`placedAge`, `stage`/`placedStage`, `step`/`placedStep`,
`termites`/`placedTermites`, rot/`placedRot`) plus `fell`, `toppling` and `pendingDays`, with seven ways out. It is
still readable and its rules do not fight, but the next feature added to a tree's life (fig and doum trees living in
play, step 8) should first split it into "live the days", "work out what should stand", "put it in the world".

---

## What I found solid (do not "fix" these)

- **Same seed, same tree, at every age and stage.** All shape dice come from `TreeRandom` seeded from the tree's own
  seed, drawn in a fixed order whatever the age; no world dice, no unseeded dice, no hash-map order reaches a shape
  (each use collects first and writes after). A tree's daily life (`TreeLife.dice(seed, day)`) is the same however
  the days are lived, at once or one by one.
- **A tree across several chunks.** Each chunk of new land works out the same tree from the piece's record and puts
  in its own part; the ground under lying wood comes from the world's shape, not from blocks, so every chunk gets the
  same answer. A tree in the book does not change until **every** chunk of its box is ready (`loaded`), so a part
  made later still fits the tree made earlier.
- **Only its own blocks.** Growing, dying and falling change only blocks that are still the tree's own
  (`TreePlacer.same`), never a player's block or wood the termites have started on; a charred or rot-marked block is
  still the tree's own and keeps its mark as the wood changes.
- **Catch-up out of sight, one step in sight.** More than one day's change waits until no player can see the tree;
  the check uses the shared sight rule.
- **Shapes off the server's thread** in normal play (`placedSoon`, with the "already being worked out" set), and the
  stores are locked properly; nothing in the shape makers is shared between threads while being built.
- **A stop in the middle of a fall.** The tree stays marked as toppling, the saved display entities are thrown away
  as they load (`SavannaEvents.onJoinLevel`), the stump keeps the record alive, and the log is placed at the next
  update. I walked this path looking for a lost tree and found none.
- **The ground heights store** (`GroundHeights`): right answers under threads (a whole int is written at once, two
  threads working out one column get the same number), bounded (about 8 MB), kept per world and dropped with it.
- **The dead stages' clock** after the BUGS 118 / 134 fixes: `daysDeadIn` keeps a new-land snag inside the stage it
  shows; I found no roll that leaves it.
- **The fall's sums** (the hinge, the swing, what it sweeps): no way to divide by zero or run off an array, also for
  a tree with nothing moving.
- **No shape can leave the world or grow without end**: the tallest is about 36 blocks over its foot, the widest an
  ancient umbrella thorn at about 56.
