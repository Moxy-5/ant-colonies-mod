# Fly clouds (planned with the user 2026-11-20)

The user: flies for the bullfrog's tongue first; later flies all over remains on land ("this texture will be really
important"); not little black dots, and no dropped frames. Animals are fun first (CLAUDE.md): a generalized fly,
inspired by real flies.

## The idea
- **One cloud is one mob.** The server knows a spot in the air, how many flies it holds (my pick: 6 to 10) and the
  area it hangs round. The flies are not mobs: the cloud's own renderer (a plain `EntityRenderer`, not GeckoLib) draws
  every fly each frame from the fly model's boxes, the cheap way Minecraft draws particles.
- **The look (`tools/fly_models.py`, made 16 model pixels long, drawn 0.1 blocks; my pick, so it looks right beside
  the 0.8-block bullfrog: on the ants' real-size scale a house fly would be 0.23 blocks):** 23 boxes: a head with big
  compound eyes (tiny facets, a shine), a silvery face, a mouthpart, a humped thorax, a rounded abdomen in segments, two
  see-through veined wings with a faint rainbow shimmer (their outline cut by the texture), six jointed legs. Two looks,
  my picks: **A bottle fly** (metallic green, gold where the light catches, the classic fly of carcasses and dung) and
  **B house fly** (grey, four dark stripes, buff sides). The metallic sheen is painted (Minecraft has no shine).
- **The movement (worked out on the player's computer every frame from the cloud's seed, so no network messages per
  fly and smooth at any frame rate):** each fly zips to a new spot in the cloud every 0.18 to 0.6 s on a springy dash
  that overshoots a little, stops and hovers with a wobble, turns on the spot; leans into its dashes and turns; the
  wings swap between two angles every frame (a buzzing blur); now and then a fly lands nearby for 1.5 to 4.5 s, shuffles
  round and often rubs its front legs together, then takes off. The cloud drifts slowly. (All my picks, tuned in the
  preview.)
- **Cost:** a fly is 23 boxes close up (a cloud of 8 about 1.75 tilapia), 11 past 4 blocks (no legs, no mouthpart), and
  nothing past 12 blocks (it is under a screen pixel there). To measure in the game.
- **Preview:** `python tools/fly_models.py cloud` writes `references/workshop/fly/cloud.html` (offline) and
  `cloud_publish.html`, published for the user at https://claude.ai/artifact/1YqPfzWhZtsy9rALV3i5ri (views: the whole
  cloud, follow one fly, a still fly on a stone; looks A, B or both; 1 to 12 flies; slow motion).

## The frog eating a fly (a Minecraft-coder point)
The flies' places exist only on the player's computer, so the server cannot see which fly is near the frog. So the
server decides it: while a cloud's middle is within about 0.8 blocks of a bullfrog's mouth, a fly "comes within reach"
by chance every few seconds (the tongue reaches 0.32 blocks for a male); the frog plays `tongue`, the server takes a
fly from the cloud and tells the players' computers, and the renderer steers one fly into the tongue's path and hides it
as the tongue snaps back. The cloud refills slowly (my pick: one fly a minute); an empty cloud drifts away and is gone.
Barbs can sip a fly off the surface the same way; young crocodiles snap at low flies.

## Where and when
- **For now (the user, 2026-11-20: "make them around all day for now just for testing"):** over the shallows, pans,
  oxbows and slow river edges at any time of day. Own spawning group `antcolonies:fly_cloud` (my pick: cap 6 round a
  player, kept within 64), with the spawning check list (CLAUDE.md) and the quiet-savanna gate.
- **Later, as more is added:** remains on land and dung (the user: "flies are all over the savanna on remains"),
  termite flights (M9.5), clouds of midges over the water at dusk, the seasons from the shared season source.

## Steps
1. **The look (now):** the fly model and its two paints in the preview; the user picks A, B or both and says what to
   change.
2. **In the game:** `FlyCloudEntity` (no gravity, no collision, not pushed, a small box, saved fly count), its renderer
   drawing the flies from `geo/entity/fly.geo.json` with the see-through wings and the distance cut-offs, a quiet buzz
   heard within 4 blocks, spawning (group, spawn check, a `worldWouldSpawn` test), `/summon antcolonies:fly_cloud`.
   Filmed in the game frame by frame (the animation rule).
3. **Eaten:** the bullfrog's tongue once the bullfrog is in the game (prey plan step 3), barbs sipping, young
   crocodiles snapping; tests in `river`.

## Progress
- 2026-11-20: planned with the user; the fly model (23 boxes, 10 bones) with looks A and B, and the moving preview
  published. Waiting for the user's look.
- 2026-11-20: **both looks approved by the user** ("wow those look amazing, both sound good"): the clouds mix bottle
  flies and house flies (my pick for the mix: half and half, each cloud its own mix). Step 1 done; next, the clouds in
  the game (step 2).
