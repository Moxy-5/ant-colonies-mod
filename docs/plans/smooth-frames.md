# Smooth frames for every player (later plan)

Added by the user 2026-11-12, to be done with the big optimizing pass they plan for about late December 2026. Nothing built.

## What is known

- The user's "constant stuttering" while walking into new land was Java stopping the whole game to clear memory: with
  the usual cleaner (G1) about 20 to 30 ms about once a second, 160 to 250 ms in every 10 s (frame watch log,
  2026-11-12: 56 rough frames in 4,695; view distance 10 felt the same as 20).
- Switching the user's own game to Java's Z cleaner (`-XX:+UseZGC -XX:+ZGenerational` in the `client` run of
  `build.gradle`) fixed it: confirmed in-game by the user 2026-11-12 ("yep that fixed it").
- That only fixes `./gradlew runClient`. A released mod runs with the player's launcher settings (usually G1), so
  players would still stutter.

## What is not known

- Who makes the garbage while land loads: our world making (trees, mounds, ground heights), the colonies, Minecraft's
  own land making, or TerraBlender. Not measured.
- How much is ours at all: no comparison yet against a world without our mods on the same settings.

## The plan (to detail with the user then)

1. Measure first: a flight recording of what makes garbage (`tools/flight.sh` with `FLIGHT_PROFILE=1`, and a client
   run with Java's recorder), flying over new savanna; the same in a world without our mods as the yardstick.
2. Cut the biggest makers of ours (things made again and again: positions, lists, shapes; reuse them or keep them).
3. Check with the usual cleaner and a launcher's usual settings (2 to 4 GB of memory, G1): the `[frames]` lines of
   `client/FrameWatch` should show almost no rough frames while walking into new land.
4. A note for players in the mod's page: which Java settings run smoothest.

## Tools already there

- `client/FrameWatch`: `[frames]` every 10 s (rough frames, memory-clearing time), `[frame]`/`[freeze]` for long frames.
- `debug/StallWatch`: `[spike]`/`[stall]` for the world side, with the memory-clearing time.
- `./gradlew runClient -PclientGc=g1` launches with the old cleaner to compare.
