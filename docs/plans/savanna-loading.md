# Savanna loading speed (planned 2026-11-03, nothing built)

The user's report (2026-11-03): new savanna loads slowly, and a termite mound sometimes pops into a loaded chunk about
20 seconds late.

## What is wrong (the diagnosis, from the user's game log of 2026-11-03 and a timing test)

**1. Most of the slow loading is the giant trees, on Minecraft's one world-making lane.** Minecraft makes chunks in
steps. The heavy one (the ground's shape) runs on many threads, but two steps run one at a time on a single queue (the
"worldgen" mailbox in `ChunkMap`): working out where structures start (`findGenerationPoint`) and putting things into a
chunk (`postProcess`, the features step). Our giant trees do both of these. The `/antcolonies perf` lines in the log
show, for each 5 seconds, 2.9 to 3.9 seconds of that one queue spent on our trees: "15 planned, 129 ms each", "9
planned, 359 ms each, longest 1940 ms", "33 put down, 65 ms each, longest 1078 ms". (Minecraft's own trees take well
under a millisecond.) While a tree is planned, no chunk anywhere gets on.

**The tree shapes themselves are not the slow part.** A timing test (2026-11-03, deleted after) built every kind, age
and stage: 1 to 10 ms each. **The slow part is asking how high the ground is** (`getFirstFreeHeight`). Before a chunk is
made there are no blocks to look at, so Minecraft works the ground's height out from the world's shape maths each time,
and nothing keeps the answer. How many times a tree asks:
- a fallen tree checks the ground under its log: 120 to 490 columns (the timing test), every second one, and up to 4
  fallen trees are tried before one lies flat enough;
- the tree's foot: 10 asks for each of up to 8 places (`GiantTreeStructure.SPOTS`, since the mounds came, 2026-10-24);
- each place checks every termite mound place near it (`TermiteMoundStructure.crowds`): 18 asks per mound place;
- then **every chunk the tree crosses asks again**: the dead wood under a tree and a fallen log are fitted to the
  ground anew in each chunk (`GiantTreePiece.putDown`, `litter`, `lying`); only the last 16 fitted logs and the last 48
  shapes are kept (`FITTED`, `TreePlacer.CACHE`), while about 200 trees are being made round a player at view distance
  12, so most trees are worked out again for each chunk.

(Asking for the ground's height being the slow part is my reading: the shapes are timed, the asks are counted, the
asks' own time is not yet measured. Step 1 below measures it first.)

**2. The mound pops in late on purpose, and waits for that slow lane.** A mound is not put in while the world is made:
world making only notes its place (`TermiteMoundPiece`), and the server puts the colony and mound in later
(`NewLandMounds.tick`), into chunks the player already sees, so it appears. It waits until:
1. every chunk 64 blocks round it is fully loaded (`LOADED_ROUND`: 9 by 9 chunks, room for its dead tree), which with
   the trees choking the lane takes many seconds, longer near the edge of view;
2. its mound's shape is grown in the background (about a second, `MoundPlan.grownSoon`), only started once (1) is done;
3. its dead tree's shapes are worked out (also only started then).
A dead mound is then built whole and worn down to its age at once, every block broken with its sound and particles
(`DeadMounds.agedForNewLand`): the log has 138 "Maximum sound pool size 247 reached" lines from one such moment.

**Not the problem:** the server's own tick (8 to 19 ms of its 50, the colonies and trees about 1 to 5 ms); heart rot
(0.00 to 0.11 ms a tick); putting a mound in (6 to 95 ms, once).

## Ways to speed it up

Steps 1 to 3 change nothing in what is seen except speed (the same trees in the same places, the same mounds sooner).
Step 4 changes how mounds are made and needs the user's OK.

**Step 1, measure.** Split the tree stopwatch (`Perf.NewLand`) into: shapes, ground height asks (how many, how long),
mound checks, fitting. One look in-game at the new numbers confirms the diagnosis.

**Step 2, the trees: ask once, keep the answers.**
- **One shared memory of ground heights** for world making: each column's height worked out once and kept (it never
  changes before the chunk is made: it comes from the world's seed alone), shared by every tree, every place tried,
  every chunk and the mound checks. A column is 8 bytes; 100,000 kept is under 2 MB.
- **A fitted tree kept for all its chunks:** the fallen log and the dead wood under a tree fitted to the ground once,
  kept until all its chunks are done (in place of 16 kept, kept by the trees still being made).
- **Enough shapes kept** for the trees round a player (about 300 in place of 48; small trees are small), and two
  threads never working out the same tree at once.
- **The mound check first and cheaper:** a tree's place is checked against the mound places before its fallen log and
  dead wood are fitted, not after.
Expected: tree planning from about 100 ms to a few ms each; putting down from 15 to 65 ms a chunk to about 1 to 3.

**Step 3, the mounds: sooner, and quietly.**
- Start growing the mound's shape and its dead tree's shapes the moment world making notes its place, not once the
  land round it is loaded (they are ready by the time the land is).
- Put the mound in once its own land is loaded (about 2 chunks round it); its dead tree goes in on its own once its
  land is (it stands 10 to 30 blocks off).
- A dead mound of new land worn to its age without breaking sounds and particles (nobody saw it standing), so no burst.
Expected: from about 20 s to a few seconds after the chunk shows, and no sound burst.

**Step 4 (the real fix for popping in, the user's call): mounds made with the world.** The mound's blocks written into
each chunk while it is made, like the trees, so it is there the moment the chunk first shows; the colony (queen, king,
numbers, galleries, the Matabele) added after, underground and out of sight. A shape grown by the termites' rules takes
about a second, too slow for world making, so new land's mounds would come from a stock of shapes grown in the
background at the start and kept on disk (for example 32 grown mounds, each turned 4 ways and mirrored: 256 looks);
dead ones written already worn to their day. What the user would see: no mound ever popping in; but two mounds of new
land can share a shape (turned), where today each is its own. Mounds the termites build in play stay their own.

## Found while looking (separate from the speed)
- **The dead tree by the mound is missing at most new mounds:** 16 of the 21 mounds in the log say "no room for a
  dead tree", mostly "something in the way: Grass Block" (`NewLandMounds.blocked` counts a raised bit of ground as in
  the way, where trees elsewhere simply go into it). A bug, for its own session.
- **A mound shape that cannot be built:** "[mound] stage 30 of a mound cannot be built from inside: (SPIRE: no termite
  can get to it to piece)" on the mound grower thread. A bug, for its own session.
