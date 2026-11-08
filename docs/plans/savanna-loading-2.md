# Savanna loading, round 2 (planned 2026-11-07, nothing built)

The user's in-game test of the savanna rivers (2026-11-07, "I am in love it looks amazing"): making the world is slow at
first, then the game runs at normal speed, but **chunks load quite slowly**. Round 1 (`docs/plans/savanna-loading.md`,
2026-11-03) made the giant trees remember ground heights; this round is about what the flat savannas and the rivers
added since. Branch: to be made from `claude/savanna-rivers-a9c96a` **after BUGS 181 is in** (its session changes the
same terrain files).

## What the logs show (the user's runs of 2026-11-07, `run/logs`)

| | before flat savannas (14:20, 16:49) | flat savannas (18:52) | rivers (22:30) |
|---|---|---|---|
| one ground height worked out (giant trees, mound places) | 2.5 ms | 7.0 ms | 7.6 to 8.8 ms |
| finding the savanna spawn | 6.6 to 9.5 s | 22.9 s | 28.6 s |
| preparing the spawn area | about 8 s | 8.0 s | 12.3 s |
| view distance | 12 | 20 | 12 |

1. **The land costs about 3 times as much to work out since the flat savannas** (the rivers added about 15 percent). Every
   ask of "how high is the ground here" before a chunk exists, and every biome sample, now takes 3 times as long. The
   trees ask 300 to 1,100 times every 5 seconds, so in some 5-second windows they spent 5 to 9 seconds of world-builder
   time on heights alone. That is the slow chunk loading. The 3 times longer spawn search is the slow start.
2. **Why (my reading, step L1 proves it):** Minecraft keeps ("caches") the land's numbers only while it builds a whole
   chunk. When it answers a single question (one height, one biome), every part of the recipe is worked out again each
   time it is used. Our flat-savanna recipe uses the same parts many times over:
   - the savanna mask (two climate noises) inside the erosion number;
   - the raw erosion twice in the new erosion;
   - the whole land-height curve twice in the new land height (the floodplain).

   Minecraft's land-height curve reads erosion and peaks-and-valleys dozens of times (its curves are nested), and each
   of those reads now does several noises where Minecraft's did one.
3. **Server stalls (the game's own tick frozen; "Can't keep up!" in the log):**
   - 6.5 s inside the giant trees' once-a-second update; the cause can't be seen in the log;
   - 1.6 s putting a new-land termite colony in (`[new-land] living half-grown termite colony ... in 1645.8 ms`, the
     others take 8 to 30 ms);
   - 0.8 s in the colony part `foraging_holes` and 0.6 s in `away_life`;
   - 1.2 to 2.5 s with our mod nearly idle.

   My reading: most of these are the server thread reaching into land that isn't made yet, so it must stop and wait
   for the slow world builder.
4. The 18:52 run had view distance 20 (2.7 times the chunks of 12); tonight's was 12, so that is not tonight's cause.
   Worth knowing: 20 makes any slowness far worse.

## The steps

Nothing here should change what is seen: the same land, trees, mounds and colonies, only sooner. Each step is checked
block for block against the land as it is today.

**L1, measure (no change in the game).**
- **A speed test in `worldgen`:** time 2,000 ground heights and 2,000 biome samples on the real world builder, in savanna
  and outside it. Then time them again with each new recipe part swapped back to Minecraft's (in the test only), to name
  the costly parts and count how often each one is worked out per height.
- **One short soak under Java Flight Recorder** (Java's built-in sampler, which shows the busiest code), to confirm
  where world-builder time goes.
- **A main-thread watch:**
  - the log names any time the server thread itself has to make or load a chunk, and which of our code asked;
  - any single tree update, colony part or mound put-in over 100 ms writes what it was doing.

  Then one in-game run by the user (or a soak) catches the stalls above.
- Count how many height asks land in each chunk (for L3).

**L2, the same recipe, worked out once per spot.** A small piece of our own in the recipe that remembers its last answer
for the spot being asked about, so a part used 30 times in one question is worked out once (my pick; it works for single
questions and whole chunks alike). Wrapped round the savanna mask, the new erosion and peaks-and-valleys, the floodplain
and the raw land-height curve. Two more options if L1 shows they help:
- the recipe rewritten so each part appears once (the generator scripts in `tools/flat_savannas/`, never the JSON by hand);
- the savanna mask's two noises read once per spot and shared.

The guard is a new test: heights and biomes at 2,000 columns, on 3 seeds, compared to numbers saved before the change;
they must match exactly. Target: a ground height at about Minecraft's 2.5 ms or less, the spawn search under 10 s.

**L3, heights a chunk at a time (if L1's counts say it pays).**
- **What changes:** the first height asked in a chunk works out all 256 of that chunk's heights in one go, sharing the
  work the way Minecraft builds a chunk's shape. Today each column is worked out on its own.
- **Why it pays:** a fallen log fits itself to 120 to 490 columns, mostly in the same few chunks.
- **Guard:** checked equal to the one-column answer on every column of the test.

**L4, the stalls.** Fix what L1's watch names; most likely code that reads blocks in land not made yet:
- the tree update;
- the new-land colony put-in;
- foraging holes;
- away life.

Each will ask whether the land is loaded first, and wait (or spread the work over several seconds) where it isn't. Each
fix gets a test where it can.

**L5, the full check.**
- Builds and tests: `./gradlew build`, then `worldgen,trees,mound,colony,away,raids` on 6 servers, then the full run
  (the every-3-steps rule).
- Speed: the numbers above before and after, and `/antcolonies perf`'s new-land lines.
- In-game: the user's test in a new world, at view distance 12.

## For the game balancer
Nothing moves: no distance, rate or size changes. Faster loading means mounds, Matabele colonies and their dead trees
appear sooner after a chunk shows (today they wait for the land round them, which waits for the slow builder).

## My picks
- Measure first (L1), then L2: it fixes both the slow start and the slow chunks at the root, with no change to the land.
- L3 only if L1's counts show many asks per chunk.
- Built after BUGS 181 merges, since both touch the terrain recipe.

## Your calls
None, unless L2 cannot make the land exactly the same: then I'll show you the difference (pictures in
`references/`) before anything goes in.
