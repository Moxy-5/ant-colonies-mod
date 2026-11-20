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

## Height and the long tongue (the user, 2026-11-20)
- **The tongue reaches 2 blocks** (the user: "start with 2 block range but the height goes up to 2 blocks"; they first
  thought of 3; fun first, a cartoon tongue: real frog tongues reach a third to one body length, and the real African
  bullfrog mostly gulps with its lunge). For a male (0.8 blocks) that is 2.5 body lengths; a female's reaches less, by
  her size (the clip scales with the model; my pick). It shoots into a cone in front of the frog and above it (a fly
  behind it: the frog turns first), only along a clear line (no tongue through blocks or leaves).
- **The swarms hover high** (the user: "fly swarms hover 2 blocks in the sky and sometimes 3 blocks so they're
  unreachable"): the cloud's middle drifts slowly between about 1.7 and 3 blocks over the ground or water under it,
  mostly near 2, a rise and fall every minute or two (my picks). So a frog waits under a cloud, head tipped up, and
  flicks when the cloud sinks into reach; when it rises, it cannot eat. A frog is full after a few flies and rests a
  minute or two (its existing fed time, no new rule).
- **Landed flies** (single flies dropping out of the cloud for a few seconds) are the easy pickings: on the bank and on
  the water's surface, where frogs flick at them, barbs sip them and young crocodiles snap at them (those two never
  reach the swarm).
- **The tongue drawn as a rope (a Minecraft-coder point):** stretching the model's tongue pieces six times over would
  smear their pixels into long streaks, so the bullfrog's renderer draws the shot tongue the way Minecraft draws a lead:
  a pink ribbon from the mouth to the fly, thinner as it stretches, the sticky pad on its end, any length and way,
  wobbling like elastic on the way back. The mouth, head tilt and swallow stay a clip.

## The frog eating a fly (a Minecraft-coder point)
The flies' places exist only on the player's computer, so the server cannot see which fly is near the frog. So the
server decides it: while the cloud's lowest flies are within the tongue's 2 blocks of a bullfrog's mouth (or a landed fly
is), a fly "comes within reach" by chance every few seconds; the frog plays `tongue`, the server takes a fly from the
cloud and tells the players' computers, and the renderer steers one fly into the tongue's path and hides it as the
tongue snaps back; some flicks miss and the fly zips off (my pick: 1 in 4). The cloud refills slowly (my pick: one fly a
minute); an empty cloud drifts away and is gone.

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
- 2026-11-20: the user's heights: the tongue reaches 2 blocks (up to 2 blocks high), the swarms hover about 2 blocks up
  and sometimes 3, out of reach ("Height and the long tongue" above). The preview's clouds still hover low; they go up
  with the game step.
