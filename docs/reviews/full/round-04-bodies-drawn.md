# Full code review, round 4: how bodies move and are drawn in play (reading round, 2026-11-26)

Read on branch `claude/review-round-4` at commit `b72cba68` (made from `claude/review-fix-12`, the branch `C:\Ant Mod`
was on), by sessions that did not write the code. Rule book: `docs/plans/code-review.md`. No code was changed; no
gradle, game, game test or film was run. Nothing was measured: every lag finding says "to measure" unless the path
through the code proves the cost.

**What round 4 is:** the game's own body and drawing code, read only for what a player can meet (a wrong pose, a part
in a block, a body sliding or popping, lag from drawing, a crash on a dedicated server), not the engine's design, the
clips or the filming code.

**The four folders, and what was read.** Round 12 (the animation engine) read about half of them, every line; those
files were not read again here and its findings are not repeated.

- `framework: entity/body/` (19 files, 4,223 lines). Round 12 read: `MoveRider`, `BlockMoves`, `Gaits`, `GaitShift`,
  `DrawClock`, `SteadyController`, `TailDrag`, `Spots`, `BodyPlans`, `AntAnimationController`, `OwnBones`.
  **Read here, every line:** `BodySizes`, `BodyTurn`, `EdgeOver`, `GroundBend`, `LostLegs`, `MoveClock`, `Spine`,
  `Strikes` (1,253 lines).
- `framework: client/` (29 files, 5,042 lines). Round 12 read: `FilmDirector`, `FilmChecks`, `FilmStep`, `FilmWatch`,
  `FilmFrameMeter`, `FilmFactsBoard`, `ReportShot`, `BodyRecorder`, `FrameWatch`, `DraggedTail`, `BoneFrames`,
  `PoseMemory`, `BoneMesh`, and the film hook in `AntRenderer`.
  **Read here, every line:** `AntRenderer` (the rest of it), `AntCorpseRenderer`, `AntEggRenderer`, `AntLarvaRenderer`,
  `BodyLanguage`, `CarriedLoadLayer`, `ClampedHeadLayer`, `ClientEvents`, `FaceLayer`, `InsectModel`, `LoadLooks`,
  `LostLegRenderer`, `MudCoatLayer`, `MudCoatLook`, `StingPainOverlay`, `YoungPaleLayer` (about 1,500 lines).
- `framework: coat/`: **read here:** `MudCoat` (186 lines).
- `savanna: client/` (40 files, 5,069 lines). Round 12 followed its thread into `BuffaloModel`, `StandTilt` and parts
  of the otter's, monitor's and hippo's models. **Read here, every line, all 40:** `BarbRenderer`, `BloodMistParticle`,
  `BuffaloModel`, `BuffaloRenderer`, `BullSharkRenderer`, `BullfrogRenderer`, `CatfishRenderer`, `CrabModel`,
  `CrabRenderer`, `CrocodileModel`, `CrocodileRenderer`, `CrocodileRollClient`, `DistichodusRenderer`, `DoumModel`,
  `FlyBuzzSound`, `FlyCloudRenderer`, `FlySwarm`, `HippoModel`, `HippoRenderer`, `LionModel`, `LionRenderer`,
  `MonitorModel`, `MonitorRenderer`, `MudFootprintParticle`, `MudFootprints`, `MudPlayerLayer`, `OtterModel`,
  `OtterRenderer`, `PlantPushClient`, `RemainsRenderer`, `SavannaClientEvents`, `SavannaMudClient`, `SnailRenderer`,
  `StandTilt`, `SwimFold`, `TadpoleRenderer`, `TermiteNymphRenderer`, `TermiteQueenModel`, `TermiteQueenRenderer`,
  `TilapiaRenderer`.

About 8,000 lines read fresh. Also read first: `docs/BUGS.md` for the body and drawing bugs (by class and animal
name), round 12's file, and round 3's file (the insect body, on `claude/review-round-3-a86aa8`, not merged here), so
that neither round's findings are repeated.

**Who read it.** Three readers with a slice each (the framework's body code, renderers and coat; the savanna's big
animal models and the termite royals; the savanna's small animals and client effects) and one reader across all of
them on one question: "what is done every frame for every insect and animal on screen, and every tick sent to each
player, and what does it cost with 60 insects and a full river in view". The leading session then checked every
grade A finding itself against the lines it names.

**How to read a finding.** Rating: **A** the player can meet it; **B** it will cost us when building on this; **C** a
tidy-up. Proof: **proven** (the path walked through the code, lines named), **likely** (one link not checked, named),
**to measure**. A finding's `R` number is its only number. Findings keep their numbers for good; a fix session adds a
status line under a finding and never rewrites it.

---

## Slice: the framework's body code, the insect renderers and layers, the mud coat

Read: `entity/body/` `BodySizes`, `BodyTurn`, `EdgeOver`, `GroundBend`, `LostLegs`, `MoveClock`, `Spine`, `Strikes`; `client/` `AntRenderer` and the fifteen files beside it named at the top; `coat/MudCoat`. Calls followed into `AbstractAntEntity`, `LionEntity`, `LionHunt.ride` and GeckoLib 4.9.3 (`javap`).

### R4-1 (A, the mechanism proven; how often it happens likely): a screen that guesses "missed" never takes the server's "gripped", so a lioness is drawn standing on the ground while the buffalo carries her off
**What is wrong:** when a lioness strikes, the server decides "grip" or "miss" and sends its word to the screens; each
screen also guesses on its own a tick early. The code says the server's word wins ("a screen: the server's word stands
over its own guess"), but it only wins one way round: if the screen guessed "gripped" and the server says "missed", the
screen is corrected; if the screen guessed "missed" first, the ride never looks at the server's word again.
**What the player would see:** a lioness that really gripped the buffalo is drawn landing where she aimed and standing
there on the ground (a passenger drawn away from her mount) while the buffalo walks or runs off; she jumps back onto it
only when her doing changes. The reverse case was the one BUGS 423 saw in the hunt film and fixed.
**Where:** `framework/src/main/java/com/antcolonies/entity/body/Strikes.java:101-128` (`Ride.target`), called from
`savanna/.../animal/LionHunt.java:422-431` (`ride`).
**Proof:** `target` begins `if (missed == 1) return aim;` (line 103). The screen's own guess is made at line 117-122 the
first tick `sinceHit >= -1` (or when a word has come). The override `if (told >= 0 && missed >= 0) missed = told;` is at
line 123-125, after the early return. So once a screen's guess sets `missed = 1`, every later call returns at line 103,
and `told(0)` (LionHunt.java:426, called before `target` each tick) is stored but never read. When the guess comes first:
a screen starts its clock when the doing packet arrives (`LionEntity.onSyncedDataUpdated`, `doingSince = tickCount`,
lines 642-647), and the server's word is sent the tick the server decides (`strikeWord(settle.word())`, LionHunt.java:430);
both are a packet's delay late, so the screen reaches its own `sinceHit == -1` about when the word arrives. If the word's
packet is one tick slower than the doing's (the integrated server and the client do not tick in step, and a second
player's ping varies), the screen decides alone. The screen guesses from the prey "a tick late and smoothed" (the
comment at lines 136-139), so near the `GRIP` edge its guess differs from the server's. Not run in the game: how often
the word comes late is to be seen.
**Checked by the leading session:** `Strikes.java:103` returns before line 123 can take the server's word, and `LionHunt.ride` (lines 425-427) passes the word in on a screen before each `target` call; the mechanism holds. How often the word comes after the screen's own guess was not seen in a game.
**Fix proposed:** apply the server's word before the early return (move lines 123-125 to the top of `target`, and on a
word that turns a miss into a grip, set `gapX/gapZ` from the aim to the place so she eases onto her place), or simpler:
a screen never decides; until the word comes it keeps flying to the aim (it already does until `sinceHit >= 0`), and holds
the aim one or two ticks more if the word is late. Add a plain unit test of `Ride` (screen guesses miss, then told 0, must
grip; and the reverse), none exists today.

### R4-2 (B, to measure; the extra drawing proven): every muddy animal near the camera is drawn two or three times over
(Also found by the thread reader, who counts a pod of six muddy hippos at 2,400 boxes instead of 800.)
**What is wrong:** the mud coat is drawn by drawing the whole model again with the mask texture (GeckoLib's `reRender`),
once for each of the two masks it shows. Coverage 3 and 4 show both masks, so the animal is drawn three times; 1, 2 and
5 to 7, twice. Within 56 blocks, always, whatever the box count.
**What the player would see:** frame rate falling round a wallow or a muddy river bank. Box counts (the geo files): monitor
665, bullfrog 393, crab 248 to 276, buffalo 229, crocodile 192, hippo 133. A monitor at coverage 3 or 4 is about 2,000
boxes a frame; the mod's own measured cost is about a millionth of a second a box (`InsectModel.farLook`'s comment,
`tools/perf_scenes.py`), so about 1.3 ms a frame more for one monitor, and a herd of muddy buffalo (when they spawn and
wallow) a few ms.
**Where:** `framework/.../client/MudCoatLayer.java:23-35`, `MudCoatLook.java:20-37, 50-56`; the layer is on the buffalo,
bullfrog, crab, crocodile, hippo, monitor and otter renderers (`savanna/.../client/*Renderer.java`).
**Proof:** `render` loops `i` over the two masks and calls `getRenderer().reRender(bakedModel, ...)` for each with an alpha
above 0 (`alphas`: coverage 3 gives {0.95, 0.42}, 4 {0.95, 0.72}). `reRender` is the whole model again. `shows` cuts it off
only past `FAR` (56 blocks). Not measured in the game.
**Fix proposed:** measure with a scene of muddy monitors and hippos (`tools/perf_scenes.py`). Then: one mask at a time
(blend the low and full masks into the one texture per coverage step, made by `tools/mud_masks.py`, so coverage 3 and 4
draw once more, not twice), and a nearer cut-off for the big models (or fade the coat out past about 24 blocks, where a
tint over a whole body is all that shows).

### R4-3 (B, to measure; the extra drawing proven): every young worker is drawn twice at any distance
(Also found by the thread reader, who rated it C: about 1 worker in 20 is young in a steady colony, more right after a hatching wave.)
**What is wrong:** young (pale) workers are drawn by drawing the whole model a second time with the pale texture, at
any distance, while every other extra (the live layer at 32 blocks, the mud coat at 56, the Matabele's far look at 4.5
and 7) stops further off. Termites have no far look, so a young termite worker's 65 boxes are drawn twice out to the
draw distance (`AbstractAntEntity.DRAW_DISTANCE`, 48 blocks times the player's setting).
**What the player would see:** a mound's nurses and young workers (a large share of a growing colony's workers on screen)
costing twice what the old workers cost to draw; at a mound with many young near the surface, a lower frame rate.
**Where:** `framework/.../client/YoungPaleLayer.java:33-47`.
**Proof:** `render` returns only for `youth <= 0.01` or no pale texture, then `reRender`s the whole model. No distance
check. Each call also makes a new `ResourceLocation` and string (`texture.withPath(path -> path.replace(...))`) to look it
up in `EXISTS`. Not measured.
**Fix proposed:** measure in a mound scene with young workers; stop the pale copy past a distance where its difference
(a lighter tint) is under about a pixel (the far look's reasoning; say 16 to 24 blocks), and keep the young texture's name
per renderer instead of building it every frame.

### R4-4 (B, proven, harmless today): the clamped-head layer finds the leg by the drawn model's plan, not the full model's, and builds the plan's name for every bone
**What is wrong:** on a far-drawn Matabele (the far look) the layer asks for the plan of the far model
(`matabele_major_far`), which has none, so it falls back to the role's own name (`leg_l1_lower`); the leg hider
(`AntRenderer.planOf`) and the live layer (`InsectModel.planResource`) ask for the full model's plan on purpose. Today the
Matabele plans map every leg role to the same name, so nothing shows; renaming a bone in the plan (which `body_plan.py
rename-test` promises "changes nothing") would make the dead heads vanish on far-drawn ants. Also, while a head is on,
`BodyPlans.ofGeo` (two new strings and a lookup) runs for every bone (227 on a Matabele), every frame.
**What the player would see:** nothing today.
**Where:** `framework/.../client/ClampedHeadLayer.java:65-73`.
**Proof:** line 69 `BodyPlans.ofGeo(getGeoModel().getModelResource(ant))`; `getGeoModel()` of the far renderer is the far
`InsectModel` whose model resource is the far geo (`InsectModel.farLook`, `withAltModel(farSubpath)`); compare
`AntRenderer.planOf` lines 182-191, which uses `insectModel.planResource(ant)`. `renderForBone` is called by GeckoLib for
every bone of the model for every layer.
**Fix proposed:** take the plan as `AntRenderer.planOf` does (once per frame, by `planResource`), and look up the leg's bone
name once, before the bones are walked (e.g. cache it in the layer by renderer and plan reads).

### R4-5 (C, proven): an insect not yet drawn keeps the worker's body numbers and size 1 for its edge pose, its spine and its turning steps
**What is wrong:** the drawn size, the climbing numbers and the spine's pieces the client tick uses (`drawScale`, `climb`,
`rear`, `drawLift`, `drawIntoWall`) are set only by the renderer when it draws the insect. An insect the camera has not
looked at yet since it came into the client's world is ticked with size 1 and the (harvester) worker's numbers.
**What the player would see:** the first frame or tick an insect at an edge or on a wall comes into view, its edge pose or
its spine's bend can be off for a tick (the spine's pieces are made afresh at the first draw, from 0 bend); a one-time
small pop, likely too small to notice.
**Where:** `AbstractAntEntity.java:3345-3351` (defaults), `3417-3445` (setters), used by `turnBody`/`overAnEdge` (2744-2754),
`tickWaist` (2490-2501), `measureWalk` (3380-3394); set by `AntRenderer.render` lines 128-136 only.
**Proof:** frustum-culled entities are not rendered, so the setters do not run; the client tick runs regardless.
**Fix proposed:** work the size out on the client tick from the entity (`getScale()` and the renderer's per-type scale,
known at sign-up) and take the plan by the model resource, so the renderer sets nothing the tick reads; or leave it.

### R4-6 (C, proven): small per-frame allocations
- `AbstractAntEntity.lostWalk` (3137-3141) calls `LostLegs.walkClip` (a list and a `String.join`) every animation frame for
  an ant with lost legs, before the cached `LOST_WALKS` lookup; key the cache by the lost bits instead.
- `MudCoatLayer.maskFor` (36-44) builds up to four `ResourceLocation`s and strings a frame per muddy animal
  (`withPath(replace)`, `substring`, `replace`).
- `FaceLayer.draw` (124-189) makes about 15 lambdas and two `String[]` a frame per otter.
- `GroundBend.update` (115-161) makes 8 arrays a frame per near crocodile or monitor and reads about 60 block columns
  (a monitor's 20 joints, 3 reads each, each up to 8 block and fluid reads). To measure; only within each model's `NEAR`.

### R4-7 (C, proven): the texture-exists caches are never cleared on a resource reload
(Also found by the thread reader.)
**What is wrong:** `YoungPaleLayer.EXISTS` and `MudCoatLook.EXISTS` remember whether a texture exists for the whole sitting.
A resource pack (or F3+T after editing) that adds or removes a `_young.png` or `_mud_*.png` keeps the old answer:
removed, the missing-texture squares (the BUGS 74 look) until the game restarts; added, not shown.
**Where:** `YoungPaleLayer.java:22, 37-39`; `MudCoatLook.java:27, 53-56`.
**Fix proposed:** clear both on the client's resource reload (a `RegisterClientReloadListenersEvent` listener), or keep
the maps per renderer (renderers are rebuilt on a reload).

### R4-8 (C, proven): a lost leg of a species no longer signed up throws an exception every frame it is drawn
**What is wrong:** `LostLegRenderer.getTextureLocation` asks `SpeciesBodies.of`, which throws for a species with no
bodies, and catches it, every frame. An old world with a harvester's lost leg lying about (the harvester was taken out
2026-11-23) builds an exception (with its stack trace) per frame per piece until it is cleared after `LIES_TICKS`.
**Where:** `client/LostLegRenderer.java:51-57`; `species/SpeciesBodies.java:52-58`.
**Fix proposed:** a lookup that returns null (`SpeciesBodies.find`) or check on load and discard the piece
(`LostLegEntity.readAdditionalSaveData`). Old test worlds are disposable (the user), so tidy-up only.

### R4-9 (C, proven): a player's mud coat is kept and sent but never drawn
**What is wrong:** `SavannaMudBlock.muddy` puts mud on any living thing but insects ("a player in soupy mud gets muddy legs
(2)"), and the coat is synced to the player, but only GeckoLib renderers carry `MudCoatLayer`, so a player (or any
vanilla mob: the pig in `MudTests`) wears mud nobody sees; only its shed bits fly. Also the coat attachment is not
copied on death, and a dimension change gives the client a new player entity with no coat until the next change.
**Where:** `savanna/.../block/SavannaMudBlock.java:245-252`; `coat/MudCoat.java:83-96, 144-149`.
**Fix proposed:** say in `docs/DESIGN.md` that vanilla bodies carry the coat as data only, or leave players out of `muddy`.

### R4-10 (C): wrong or stale comments
- `AntRenderer.java:40` and `InsectModel.java:16`: "the queen draws the worker's model, so she gets the worker's plan" (the
  harvester's queen; every queen has her own model now).
- `ClientEvents.java:13-15`: names `DesertClientEvents` (the desert mod was taken out).
- `BodySizes.java:15-16, 52`: "the red harvester ant until its rework", "(the red harvester ant)".
- `Strikes.java:123-125`: "a screen: the server's word stands over its own guess" says more than the code does (R4-1).

### R4-11 (B, proven): what the player sees of these parts has no automatic check outside the films
**What is wrong:** none of the drawing here (lost legs hidden, the clamped head, the load in the jaws, the young pale copy,
the mud coat, the face) has a check that runs in `./gradlew build` or the game tests (game tests run on a server, no
drawing). Only `SpineTest`, `MoveClockTest`, `GroundBendTest` (the solver), `OtterFaceFileTest` (the file) and the mud
coat's server rules (`MudTests.mudComesOnInMudDriesAndWashesOffInWater`) are tested. The strike ride's screen side
(`Strikes.Ride.told`, R4-1) has no test at all, though it is pure maths and easy to unit test. The mud coat's save and
reload is not tested (the attachment's codec is the whole save).
**Fix proposed:** a unit test of `Strikes.Ride` (both orders of guess and word); a game test that saves and reloads a
muddy pig and a Matabele with lost legs and a clamped head (the synced values come back).

### Solid in this slice (do not "fix")
- **No client code reachable on a dedicated server:** outside `client/` only `coat/MudCoat` (its `Client` nested class,
  loaded only when a client payload is handled), `OwnBones` (round 12, R12-67) and `watch/Reports` (guarded by
  `FMLEnvironment.dist.isClient()`) name client classes; every `entity/body` file of this slice imports only common code.
- **The live layer's "put the marks back" rule** (`BodyLanguage.turnBy`, `FaceLayer.change`): checked against GeckoLib
  4.9.3's `handleAnimations` (javap): `tickAnimation` runs for every instance drawn, paused or not, and returns early only
  for the same instance drawn twice in one frame (then the custom layer is skipped too), so the additions never pile up.
- **Lost legs on shared bones** (`AntRenderer.showLegs`): every insect drawn after one with a hidden leg sets all six
  again; each renderer (and the far look's own renderer, its own model file) keeps its own flag. Saved and synced
  (`LostLegs`, `LostLegAt`).
- **The clamped head comes off with its leg** (`loseLeg` -> `Clamp.legLost` -> `letGo`), so no head on a hidden shin.
- **MudCoat's syncing:** sent only on a change (`set` compares the packed byte), to trackers and self, and to a player who
  starts tracking; the tick costs one `hasData` for a clean creature; saved by the attachment's codec.
- **Synced values the body reads** (`DATA_SURFACE`, `DATA_HEADING`, `DATA_POSE_FLAGS`, `DATA_STRIKE_WORD`) are set every
  tick but `SynchedEntityData.set` sends only changes.
- **`MoveClock`** (R12-61's fix): holds through a time set-back, follows the camera's one-tick step, starts afresh per move;
  unit tested.
- **`BodyTurn`**: smooth on-the-spot pivot, capped, never past the game's head limit; runs on both sides as the game's
  own control does.
- **`Spine`**: no allocation per tick but when the piece count changes; bounded memory (64 marks); heals itself after a
  jump in position (old marks fall behind the path).
- **`EdgeOver`**: only reads blocks the client has (no land loaded); null outside the plain cases; per tick, not per frame.
- **`GroundBend`**: per animal (a field of the entity), eases by real time and snaps after a pause over 0.5 s; gated by
  each model's `NEAR`.
- **`Strikes.inReach` and the aim** cap the leap at its reach and never steer in the air (BUGS 422's fix holds).
- **Per-entity client state** (`FaceLayer` in a `WeakHashMap` keyed by the entity, the body angles in the entity): nothing
  kept per "the player", nothing left between worlds but the small static lookup maps (R4-7).
- **`StingPainOverlay`**: reads only the local player's effect; cheap.

### What this reader did not check
- Nothing was run or measured (R4-2, R4-3, R4-6 are "to measure"); R4-1's frequency is not seen in a game.
- GeckoLib's `reRender`/layer order and `applyRotations` being run again for a re-render were taken as working (the pale
  layer has been in play for months); not re-read in bytecode.
- `PoseMemory`, `BoneMesh`, `BodyPlans`, `Spots` (round 12) only as far as the calls above; `Spots.Strike.share` with an
  empty share table would throw (`Mth.clamp(…, 0, -1)` gives -1), a data-file crash in round 12's file, not proven reachable.
- The savanna renderers and models beyond the lines named (crocodile, monitor, lion) and the film hook in `AntRenderer`.
- Whether the client's `ClientboundBundlePacket` for a new entity is always handled before a `MudCoat.Sync` sent right
  after it (NeoForge's ordering of custom payloads against vanilla packets); assumed in order.

---

## Slice: the savanna's small animals and client effects

Read: the fish, frog, tadpole, snail and remains renderers, the fly cloud (`FlyCloudRenderer`, `FlySwarm`, `FlyBuzzSound`), `BloodMistParticle`, the mud footprints and the player's mud, `PlantPushClient`, `SavannaClientEvents`, `SavannaMudClient`. Calls followed into the blocks, the entities, GeckoLib 4.9.3 (`javap`) and NeoForge's `ClientHooks`.

### R4-12 (A, proven): a reed or a hippo grass mat that the server marks "pushed" but the client gives no spring is drawn by nobody: it vanishes
**What is wrong:** When something big walks through reeds or hippo grass, the server marks each plant it touches as
"pushed". A pushed plant's normal block look is switched to an empty one, and the player's computer is meant to draw it
bent instead. But the computer only draws the plants it has given a "spring" of its own, and it gives one only to plants
whose middle lies within a circle round the pusher's box, and only for pushers within 64 blocks of the camera. The server
marks every block the box overlaps (a square, not a circle) for pushers at any distance. Every plant in the gap is marked,
hidden, and never drawn.
**What the player would see:** reeds and mats blinking out of sight next to a walking player or animal: (a) the plant at
the corner of the box when it walks diagonally past (the corner of a square lies outside the circle), for about 2 seconds
(10 ticks pushed, then 30 settling, 40 if it stays on the corner); (b) the whole patch of reeds round a hippo, crocodile or
monitor more than 64 blocks away (a hippo is drawn to about 120 blocks and tracked to 160), a hole in the reed bed that
follows it; (c) likely also the row just ahead of a moving animal for a tick or two, since the server's box is a little
ahead of the box the player's computer draws (not measured), and plants already marked when a player arrives or
looks at them (the client joined mid-push).
**Where:** `PlantPushClient.java:62` (`NEAR = 64`), `:111` (pushers only within NEAR), `:159-162` (`close <= 0` skipped:
no spring), `:195-199` (only plants with a spring are drawn); the class comment `:45-46` says "A marked plant with no
spring yet (the client joined mid-push) is drawn upright": the code does not do that. Server side: `ReedBlock.java:131-143`,
`HippoGrassBlock.java:162-173` (marked from `entityInside`, any pusher, any distance), `PlantPush.java:98-109`.
**Proof:** the hidden look: `blockstates/reeds.json` and `hippo_grass.json` map every `push=1` and `push=2` state to
`reeds_hidden` / `hippo_grass_hidden`. Minecraft calls `entityInside` for every block position inside the entity's box
(`Entity.checkInsideBlocks`, no shape test; reeds and mats have no collision, `SavannaBlocks` `.noCollission()`), so the
four blocks a box straddling a corner overlaps are all marked. The client's closeness is `1 - d / half` with `half` = half
the box width + 0.5 and `d` the straight-line distance from the box's middle to the plant's middle (`:157-159`). A player
(0.6 wide) with its box from 0.99 to 1.59 in x and z overlaps block (0,0); that block's middle is 0.79 off in x and in z,
`d` = 1.12, `half` = 0.8, closeness below 0: no spring (`:161-162`). `onRender` walks only `SWAYS` (`:195`), so the block,
hidden by its state, is drawn by nothing. Then `ReedBlock`'s tick keeps it pushed while the column box still meets the
player (`PlantPush.touched`), and 30 ticks of settling after. For (b), `onTick` feels only entities within 64 blocks of the
camera (`:111`), while the server marks for any pusher. Link not checked: the size of the lead in (c).
**Checked by the leading session:** the server's side (`ReedBlock.entityInside`, `PlantPush.eachBlock`: every block the box overlaps), the hidden models for `push=1` and `push=2` in both blockstate files, and `PlantPushClient.java:159-162` and `:195`: a block the box overlaps whose middle lies further than half the width plus 0.5 from the box's middle gets no spring and is drawn by nothing. For a player that is any overlapped block more than 0.8 away, not only exact diagonals. Not seen in the game.
**Fix proposed:** make "marked" always drawn: in `onTick`, for each pusher's boxes (inflated by a block, no distance cut
or a cut at the entity render distance), give every marked reed or mat found there a spring (want 0 when it is not near
enough to lean, so it stands upright), and drop the `close <= 0` skip for blocks the box overlaps (clamp closeness to a
small least lean instead). That is the doc's own promise ("drawn upright"), with one rule: every marked plant the client
can see has a spring. Fix the class comment. A game test cannot see the drawing; a unit test of `feel` with a box on a
block corner can (every overlapped block gets a spring).

### R4-13 (B, likely): a fly cloud goes silent for good after the sound system reloads
(The reader graded it A. The leading session made it B: the link through Minecraft's sound engine was not opened, the trigger is rare in play (a resource reload or a new sound device) and walking away and back mends it.)
**What is wrong:** A cloud starts its buzz once, the first tick the player's computer has it, and then remembers "I am
buzzing" for as long as it exists. When Minecraft reloads its sound system, every playing sound is dropped, but the cloud
still thinks it is buzzing and never starts again.
**What the player would see:** after pressing F3+T (reload resource packs), changing the sound output device, or turning
on a resource pack, the fly clouds near them are silent until they walk away more than 48 blocks and come back (a new copy
of the cloud). Small, but it can be met.
**Where:** `FlyCloudEntity.java:326-330` (`buzzing` set once), `FlyBuzzSound.java:27-29`.
**Proof:** the start is guarded only by the entity's own `buzzing` flag (`FlyCloudEntity.tick`, client side); nothing
sets it back. Link not checked: that Minecraft 1.21.1's `SoundManager` reload runs `SoundEngine.reload` and that this
stops and clears ticking sounds (my memory of the vanilla code, `stopAll` clearing `tickingSounds`); not opened here.
**Fix proposed:** keep the sound instance on the cloud (an `Object` field, as `clientSwarm`) and start a new one when
`!Minecraft.getInstance().getSoundManager().isActive(instance)` (checked every second or so, not every tick); or drop the
flag and ask `isActive` each time.

### R4-14 (B, proven): the bullfrog's head tip relies on every clip it plays keying the head, and nothing checks it
**What is wrong:** `headOnFly` tips the head up by adding to whatever turn the clip gave it this frame. GeckoLib only puts
an unkeyed bone back each frame if no one touched it last frame, and touching it here marks it touched (GeckoLib clears
the marks before `setCustomAnimations`, not after). So on a clip that does not key the head, the extra tip is added on
top of last frame's tip, frame after frame: the head turns further and further. Today every clip the frog can play while
it aims, flicks or sits keys the head (checked: all body clips but `blink`, which is its own controller), so it does not
happen; the TONGUE clip is play-once and its end meets the action's end (14 ticks both), leaving a tick or two in which
the tip is already near zero. The carcass code in `RemainsRenderer` met the same trap and handles it (it puts the mark
back with `resetStateChanges`); the frog does not.
**What the player would see:** nothing today. With any new clip for a sitting, aiming or flicking frog made without a
head key (a new idle, a croak), the head would spin up and over while the frog looks at a fly.
**Where:** `BullfrogRenderer.java:158-175` (`head.setRotX(head.getRotX() + ...)`), the guard `:171`.
**Proof:** GeckoLib 4.9.3 `GeoModel.handleAnimations` calls `AnimationProcessor.tickAnimation` then `setCustomAnimations`;
`tickAnimation` ends with `resetBoneTransformationMarkers` and resets an unkeyed bone only when `!hasRotationChanged()`
(bytecode, offsets 632-857 and 1250). `bullfrog.animation.json`: `head` rotation keyed in all 18 body clips, not in
`blink`.
**Fix proposed:** the carcass's pattern (note whether the head was marked before, and `resetStateChanges` if it was
not), or add the tip to the clip's own value read fresh each frame; and a line in `tools/clip_check.py`'s rules (or a
unit test over the animation file) that every body clip keys `head` while the frog's head tip is additive.

### R4-15 (C, to measure): fly clouds do per-fly work each frame that could be done once
**What is wrong:** For each fly, in each of its two drawing passes, `poseFly` builds eight bone names from strings and
searches the model's bone tree for each (GeckoLib's `getBone` walks the tree, it is not a map), allocates a new string
array, and `drawBone` makes a new vector for every corner and every face of every box. Worst case near a player: 18 clouds
of up to 12 flies (R10-3), 216 flies, about 3,500 bone searches and 150,000 small objects a frame. GeckoLib's own drawing
also makes a vector per corner, so the vectors may be no worse than a mob's; the bone searches are this renderer's own.
**What the player would see:** to measure; most likely nothing, at worst a little stutter beside a carcass with many
clouds (garbage collection).
**Where:** `FlyCloudRenderer.java:174-199` (`getBone("wing_" + side)`, `"leg_" + side + "_" + i`, `new String[]`),
`:222-232` (`new Vector3f` per vertex and per quad); also `BullfrogRenderer.java:185-186` and `TadpoleRenderer.java:54-55`
(two bone searches per animal per frame, small).
**Proof:** `BakedGeoModel.getBone` bytecode: iterate `topLevelBones`, `searchForChildBone` recursively. `fly.geo.json`:
10 bones, 23 boxes.
**Fix proposed:** find the eight bones once when the model is (re)loaded (where `restKept` is set, `:65-71`) and keep
them in an array; reuse one scratch `Vector3f` for positions and one for normals. Measure first with the frame meter in a
scene with 18 full clouds.

### R4-16 (C, proven): small fly and tongue mismatches
**What is wrong:** (a) A fly the player's computer showed caught on the tongue, but the server did not take (its checks
at tick 7 failed: the landed fly's claim lost, or the cloud emptied), is hidden "on the tongue" for 1.5 s after the tongue
is back in, then flies out of the frog's mouth. (b) The client catches a fly only on a frame inside the 1.7 ticks the
tongue is fully out (`reach >= 0.999`); below about 12 frames a second it can miss that window, and when the server then
takes one fly, the last fly in the list vanishes in mid air instead.
**What the player would see:** rare: a fly coming out of a frog's mouth; on a very slow computer a fly blinking out.
**Where:** `BullfrogRenderer.java:200-201` (stops drawing the held fly once the tongue is in), `:240-244`;
`FlySwarm.java:263-267` (`HELD_LONGEST`), `:96-125` (the last fly removed when no fly is held).
**Proof:** walked: `render` returns at `out <= 0.001` before `drawOne`; the fly stays `ON_TONGUE` (not drawn by the
cloud, `FlyCloudRenderer.java:112`) until `letGo` at 1.5 s from `heldAt`, at the pad's last place.
**Fix proposed:** (a) when the tongue is in and the fly is still held, let it go at once (or remove it if the swallow was
seen); (b) catch on `age >= the tongue's full-out time` rather than on a frame inside the window.

### R4-17 (C, proven): a muddy footprint can float over the edge of a block
**What is wrong:** A print is laid at the player's feet height, a quarter block to one side, with no check of what is
under it and no physics. A player standing on the edge of a block (Minecraft lets the box hang half over the drop) leaves
a print over the air.
**What the player would see:** a brown square hanging in the air beside a ledge or a step, for 45 seconds.
**Where:** `MudFootprints.java:61-71`, `MudFootprintParticle.java:39-40`.
**Proof:** `printsHere` checks only the block at the player's middle (`:81-84`); the print's own spot (`x`, `z` at
`:67`) is never checked.
**Fix proposed:** check the block under the print's own spot (solid top within a few hundredths of `player.getY()`),
skip the print if not.


### Solid in this slice (do not "fix")

- **Dedicated server safety:** no common class imports a client class (grep over the savanna mod: nothing outside
  `client/`); entities hold the client's objects as plain `Object` fields (`FlyCloudEntity.clientSwarm`,
  `BullfrogEntity.clientFly`), and the buzz reaches the client through a no-op static hook filled in client setup
  (`SavannaClientEvents.java:67`). Every subscriber here is `Dist.CLIENT`.
- **No state shared wrongly between entities:** each cloud's swarm lives on the cloud itself; the catfish's and shark's
  `preyPose`/`preySeen` live on the shared renderer but are reset at the start of each `render` and used right after it,
  in one thread.
- **Leftovers between worlds:** `PlantPushClient` clears its springs when the level changes (also a dimension change);
  `MudFootprints` keys players in a `WeakHashMap`; `FlyCloudRenderer.restKept` is a model, renewed on a resource reload.
- **Time:** the flies run on game ticks plus the partial tick (stand still when paused or frozen, speed up with
  `/tick rate`), split into 1/60 s steps, a long gap capped at 0.25 s; the plant springs and footprints skip paused and
  frozen ticks.
- **Culling of the fly cloud:** its culling box covers the cloud's spread and the landing spots 3.5 blocks under it; drawn
  to 26 blocks, each fly to 24, legs to 4.
- **The fish's own pitch** is worked out on the client from the drawn motion, eased, zero out of the water and during
  the distichodus' leap and the shark's actions; held fish skip their own render and turn (`shouldRender`,
  `applyRotations`).
- **The held catfish and tilapia** are fake entities kept per holder and drawn after the holder from a captured matrix,
  so the two models' buffers never mix.
- **The carcass's root shift** (`RemainsRenderer.java:79-88`) keeps GeckoLib's "changed" mark as it was: the right way to
  edit a bone after the clips.
- **Blood mist:** one packet per puff, 6 to 60 wisps, gone at once out of the water.
- **The buzz:** a static, non-streamed sound with a 5-block reach, stopped when the cloud is removed.
- **Second players:** footprints and mud masks are made on each computer for every player it sees, from the coat the
  server already sends to trackers (`MudCoat.startTracking`); nothing is "the player" only.

### What this reader did not check

- Nothing was measured or seen in the game (a reading round): R4-12's lead at the front of a moving animal, R4-15's cost.
- Minecraft's own sound engine code for R4-13 (from memory); whether a looping sound's subtitle shows once at the start.
- That the event's pose stack at `AFTER_ENTITIES` is plain camera space, as the reed bend assumes (it has been filmed).
- That NeoForge 21.1.256 sends the mixed mod-bus and game-bus listeners of `SavannaMudClient` and `SavannaClientEvents`
  to the right bus (the game starts and the arm mud is drawn, so it does).
- Shader mods (shadow passes calling `render` twice: the fly wings' "every frame" flap would double).
- `OtterRenderer`, `CrocodileRollClient`, `MudCoatLayer` (other slices).

---

## Thread: what a frame and a tick cost with 60 insects and a full river in view (a Fable reader, across all the files)

Nothing was measured: every cost below is a count of work (boxes, block reads, objects made, files read), never a time. GeckoLib 4.9.3 was read from its jar with `javap`. Three of this reader's eight findings were the same thing a slice reader found and are merged there: the mud coat's re-draws (R4-2), the young worker drawn twice (R4-3; this reader rated it C: about 1 worker in 20 is young in a steady colony), the picture-exists memories (R4-7).

### The short answer

- **The insects are in good shape.** Their drawing has three savings built in (ready-made faces, a far look with a
  quarter of the boxes, far insects reusing their last pose every other frame), their layers stop at once when there is
  nothing to draw, and the server sends almost nothing about them beyond Minecraft's own position packets.
- **The animals have none of those three savings**, have more boxes each than any insect, and are told to be drawn
  further away than Minecraft would. A full river is the heaviest thing the mod can put on screen by a wide margin
  (my count: 10,000 to 20,000 boxes against about 4,000 to 5,000 for a two-colony raid). That is R4-18, the one finding
  this reader graded A.
- **The server side is quiet.** I found no value sent every tick for any insect or animal; all synced values send only
  when they change.

### The paths walked

#### 1. One insect, one frame

`AntRenderer.render` (framework `client/AntRenderer.java:120-140`): picks the far look (`drawnFar`, line 92), sets three
numbers on the insect, hides lost legs (stops at once when none is lost), then GeckoLib's `defaultRender`:

1. **Clips** (`InsectModel.handleAnimations` -> `PoseMemory`): a far insect reuses its last pose every other frame while
   frames are quick. Otherwise GeckoLib's `tickAnimation`: for each of the 3 players (body, head, extra) a new queue
   object for every bone, then for each bone of the clip 9 look-ups that each scan the key list from its start and make
   two small objects; then a pass over all bones with look-ups by name.
2. **The live layer** (`BodyLanguage.apply`): skipped past 32 blocks; a handful of bone look-ups; no block reads.
3. **One pass over the bones** drawing boxes through `BoneMesh.draw` (`AntRenderer.java:82-90`): ready-made faces, no
   objects made per box.
4. **Three layers** (`AntRenderer.java:116-118`), each asked once per bone per insect: `CarriedLoadLayer` and
   `ClampedHeadLayer` stop on the first line when nothing is carried or clamped; `YoungPaleLayer` stops when the insect
   is not young, otherwise draws the whole model a second time (R4-3).

Per frame vs per tick: turning, slope tilt, the edge pose, naps, the waist are worked out **per tick** in
`AbstractAntEntity` (about lines 2442-2460) and only blended per frame. Good.

Boxes: termite worker 65, soldiers 67, king 75, queen 86; Matabele major and minor 227 near, **54 far**.
60 termites = about 4,000 boxes; a 60-ant Matabele column mostly far = about 3,000 to 5,000; near, all 227 = 13,600
(only when the player stands inside the column).

#### 2. One animal, one frame

Every animal renderer is a plain `GeoEntityRenderer`. Per frame per animal: GeckoLib's full clip pass (as above, with
1 or 2 players), the model's own code, then GeckoLib's **plain box drawing**: for every box a matrix push and pop,
three quaternion turns (made even for a box that is not turned), a copied matrix, 6 normal vectors and 24 corner
vectors, all new objects (about 33 a box a frame; `GeoRenderer.renderCube`, `RenderUtil.rotateMatrixAroundCube`).

Boxes (from the model files, script `boxes.py` in the scratchpad): monitor **665**, bullfrog **393**, bull shark 366,
lion 322, lioness 280, crab 248-276, lion cub 244, buffalo 229, crocodile 192, catfish 176, hippo 133, tilapia 105,
otter 103, snail 81, distichodus 67, barb 39, tadpole 29, fly 23 (not GeckoLib-drawn), remains 71-147.

A 20-tilapia school: 2,100 boxes, 20 full clip passes, 20 shadows. A full river by the spawn caps
(`docs/LIVING-THINGS.md` 17-29: 45 tilapia and catfish, 40 barbs, 24 crabs and snails, 16 tadpoles, 8 grazers, 6
hippos, 5 crocodiles, 4 bullfrogs, 2 monitors, 2 otters, 2 sharks, 18 fly clouds): **about 10,000 to 20,000 boxes** if
all of it were in view at once (it rarely all is; the caps are for the land kept within 96 blocks).

Extra per-frame work in the animals' own code: the crocodile and monitor read the ground under every joint of spine and
tail (`GroundBend.update`), lions and buffalo read the ground under four feet (`StandTilt.place`), three models build
their file names afresh each call (R4-22).

#### 3. Per client tick

- `PlantPushClient` (`savanna client/PlantPushClient.java:110-112`): loops every entity the client knows, big ones
  within 64 blocks read the blocks they overlap. Few big animals exist, so small. Its render handler stops when no
  plant sways.
- `MudFootprints`: players only. `SavannaMudClient`, `MudPlayerLayer`: one block check a frame. Small.
- `FrameWatch` (always on): per frame a clock read, a walk over the Java cleaner's counters and a list copy; a helper
  thread wakes every 5 ms for ever. This is **R12-47 (open)**: part of every frame's cost, to measure.
- `FilmDirector`, `FilmFrameMeter`, `FilmStep`, `ReportShot`: each stops on its first check in normal play.
- Every insect's own client tick: the edge search (R4-20).
- `FlySwarm`: only stepped when its cloud is drawn.

#### 4. What the server sends

Every `entityData.set` in `savanna/animal`, `savanna/entity`, `framework/entity`, `framework/fight`, `framework/coat`
was listed (script `sets.py`). Each is either inside a setter called when something happens (a load picked up, a
strike, a clamp, a lost leg, a mood) or sets a state value each tick that Minecraft sends **only when it changed**
(`AbstractAntEntity` 2446-2448: surface, heading, pose flags; `TermiteSoldierEntity` 422, 426; `TilapiaEntity`
251-252; `CatfishEntity` 467 once a second). The mud coat sends its own packet only when its packed byte changes.
No packet of the mod's own goes out every tick.

**Values changed per tick, steady state: about 0 for 60 walking insects on flat ground, about 0 for a resting river.**
What does travel is Minecraft's own position, speed and head packets (one set per moving entity per tick or two, to
each player in range), and bursts on events: an insect climbing a wall sends surface and heading as they change.
One per-tick sound: a drumming termite soldier plays a note every other tick for 12 of each 20 ticks
(`TermiteSoldierEntity.java:414-420`); with many soldiers alarmed that is a few sound packets a tick per player.
I mark it "to measure" in the table and leave judging it to the round that reads the alarm.

#### 5. Static caches

`BoneMesh.MESHES` (emptied at 4,096 bones), `DrawClock.FURTHEST`, `FaceLayer.states`, `MudFootprints` (all weak, freed
with their entity), `PlantPushClient.SWAYS` (emptied on a world change), `BodyPlans` (read once). Two small maps are
never emptied on a resource reload (R4-7). Nothing grows without limit.

### R4-18 (A, the mechanism proven; the size to measure): every animal is drawn the slow way the insects were moved off, with more boxes and from further away
**What is wrong:** The insects were given three savings because drawing a crowd was too slow: their boxes are turned
into ready-made faces once (`BoneMesh`), far ones use a model with a quarter of the boxes, and far ones reuse their
last pose every other frame. The animals have none of the three. Each animal box is drawn by GeckoLib's own path: a
matrix saved and restored, three turns worked out even when the box is not turned, and about 33 small objects made
and thrown away, per box, per frame. And the animals have more boxes than any insect (monitor 665, bullfrog 393,
shark 366, lion 322, crab about 250, catfish 176, tilapia 105), and most are told to be drawn further than Minecraft
would draw something their size (a 105-box tilapia to 64 blocks, a crab to 48, a crocodile to 96, lions, buffalo and
hippos to 128). A full river in view is 10,000 to 20,000 such boxes a frame, so several hundred thousand short-lived
objects a frame, against about 4,000 to 5,000 cheap boxes for a whole raid.
**What the player would see:** Not proven by reading. What the path says to expect: a lower frame rate when looking
along a busy river than when watching a raid, and short hitches as Java clears the thrown-away objects. The size is
not known until it is measured.
**Where:** every renderer in `savanna/src/main/java/com/antcolonies/savanna/client/` that extends `GeoEntityRenderer`
(`TilapiaRenderer`, `BarbRenderer`, `CatfishRenderer`, `CrabRenderer`, `BullfrogRenderer`, `MonitorRenderer`,
`CrocodileRenderer`, `HippoRenderer`, `OtterRenderer`, `LionRenderer`, `BuffaloRenderer`, `BullSharkRenderer`,
`SnailRenderer`, `TadpoleRenderer`, `DistichodusRenderer`); the insect savings they lack:
`framework/.../client/AntRenderer.java:65, 82-90`, `BoneMesh.java`, `PoseMemory.java`; the draw distances:
`shouldRenderAtSqrDistance` in each animal (`TilapiaEntity.java:633`, `CrabEntity.java:362`, `CrocodileEntity.java:1126`,
`LionEntity.java:919`, ...).
**Proof:** GeckoLib 4.9.3 read with `javap`: `GeoRenderer.renderCube` pushes the pose, calls
`RenderUtil.translateToPivotPoint` and `rotateMatrixAroundCube` (three `new Quaternionf` and three `mulPose`, no test
for a zero turn), copies the matrix, then makes a normal vector per face and a `Vector4f` per corner. No animal
renderer overrides `renderCubesOfBone`; only `AntRenderer` does. `BoneMesh`'s own comment says GeckoLib's box turning
and corner writing was "about 38%" of the drawing time of an insect crowd, and `InsectModel.farLook`'s comment gives
"about a millionth of a second a box" for insects: both are the code's notes from insect scenes, not measurements of
animals. Box counts are from the model files. **Not checked:** whether any animal texture has see-through pixels on a
box that hides another box's face (`BoneMesh` drops buried faces and its comment says it assumes solid textures; the
bullfrog's and fly's see-through parts would need care); how many river animals are really in view at once in play.
**Checked by the leading session:** only `AntRenderer` and `InsectModel` name `renderCubesOfBone`, `BoneMesh` or `PoseMemory` (a search of both mods), and `TilapiaEntity.java:633` sets its own draw distance; the mechanism holds. It stays A as round 3's R3-10 did (a proven mechanism whose size is not measured); if the measurement finds a river cheap, it becomes B.
**Fix proposed:** Measure first (see the table). Then, in order of gain for the work: (1) a shared base renderer for
animals that draws through `BoneMesh` as `AntRenderer` does (one method, after checking the textures); (2) the pose
reuse for far animals (`PoseMemory` is already species-neutral in what it does); (3) far looks or shorter draw
distances for the small many-boxed kinds (bullfrog, crab, tilapia, catfish), since a 393-box frog 40 blocks off is a
few pixels. This is reuse of what exists, not a new system.

### R4-19 (B, to measure): each walking insect re-reads its walk clip's speed note as text every frame
**What is wrong:** To pick which walk clip fits an insect's speed, the code reads a note written inside the clip
("speeds 0.02 0.05"). It reads it as text, cuts it into words and turns the words into numbers every time it asks,
and it asks at least once per walking insect per frame (up to three times when near a band's edge). The stride and
turn notes beside it are remembered; this one is not.
**What the player would see:** Nothing visible; a steady small cost and thrown-away text for every walking insect in
view, most of the 60 in a raid.
**Where:** `framework/.../entity/body/AntAnimationController.java:129-140` (`notesOf`: `trim`, `substring`,
`split("\\s+")`, `parseDouble`), `201-236` (`gearFor`, `bandOf`); called from
`framework/.../entity/AbstractAntEntity.java:3105` inside the per-frame `animateBody`.
**Proof:** `animateBody` is the controller's handler, which GeckoLib calls once per frame per controller
(`AnimationController.process`); when the clip is the walk it calls `gearFor`, which calls `bandOf` for the current
gear and, near a band's edge, for a neighbour; `bandOf` calls `notesOf` with no memory. `split` with that pattern
compiles a pattern each call. **Not checked:** how many notes a walk clip carries (the loop is over all of them).
**Fix proposed:** Remember the band per clip the way `rate()` remembers the stride (a small map from clip to its two
numbers, emptied when clips reload).

### R4-20 (B, to measure): every insect the client knows searches for edges and corners every tick, drawn or not
**What is wrong:** The edge-over pose (an insect folding over a ledge or into a corner) is looked for on the client
every tick for every insect it has been told about, about 20 to 30 block reads each on flat ground (my count from the
loops), whether or not the insect is on screen, behind the player, underground or past the 48 blocks it is drawn to.
The client is told about insects further away than it draws them.
**What the player would see:** Nothing visible; client tick time that grows with the number of insects nearby, in
view or not. With two colonies near (100 or more insects known to the client) that is a few thousand block reads a
tick.
**Where:** `framework/.../entity/AbstractAntEntity.java:2642, 2740-2753` (`overAnEdge` -> `EdgeOver.find`, then
`EdgeOver.findCorner`); `framework/.../entity/body/EdgeOver.java` about 60-190; the 48 blocks:
`AbstractAntEntity.java:316-327`.
**Proof:** `turnBody` runs in the client tick and calls `overAnEdge`; its early ways out are a very young insect, one
in a channel, one turning slowly or peeking; a plain walker on a floor goes through `edgeAlong` twice (up to 3 columns,
up to 4 block reads each) and `findCorner` (9 cells, up to 3 reads each), making small objects. **Not checked:** the
default distance the client is told about insects (no range is set in `SavannaEntities.java:47-107`; I took
Minecraft's default without opening its source), and whether a real search is cheaper than my count because most
reads stop early.
**Fix proposed:** Skip the search on the client when the insect is past its draw distance from the camera (the pose
cannot be seen there), and/or set the insects' tracking range to fit the 48 blocks. One check, no new number: reuse
`DRAW_DISTANCE`.

### R4-21 (B, to measure): the crocodile and the monitor read the ground under every joint every frame
**What is wrong:** To lay spine and tail along the ground, the crocodile (within 64 blocks) and the monitor (within
48) ask the world for the ground height three times under every joint, each ask reading water and block shape down
several blocks, and then again under each foot. That is some hundreds of block reads per animal per **frame**, not per
tick, so it doubles when the frame rate doubles, though the ground can only change per tick.
**What the player would see:** Nothing visible; cost that grows with frame rate and with crocodiles near (up to 5 by
the cap, more round a carcass).
**Where:** `framework/.../entity/body/GroundBend.java` (`update`, `under`, `support` at 277);
`savanna/.../client/CrocodileModel.java:101-102, 316`; `MonitorModel.java` (its `NEAR = 48`); also
`StandTilt.place` for lions and buffalo (4 asks a frame; `LionModel.java:73` makes a new `StandTilt` each frame).
**Proof:** `setCustomAnimations` is called by GeckoLib once per frame per animal drawn; both models call
`GroundBend.update` there inside their near limit. **Not checked:** the exact joint count per chain, so "hundreds" is
an estimate from the chain lengths in the body plans, not a count.
**Fix proposed:** Read the ground once per tick per animal (keep the heights, blend per frame as the insects do with
their slope tilt). The shape of the fix already exists in `AbstractAntEntity.tiltOnSlope`.

### R4-22 (C, proven small): three animal models build their file names from text on every call, and the crab re-registers its bones between looks
**What is wrong:** `CrocodileModel`, `CrabModel` and `LionModel` make a new name object (and the text for it) each time
GeckoLib asks which model, picture or clip file to use, several times per animal per frame. The crab uses one model
object for four looks (plain, male, female, with eggs), so whenever two crabs drawn one after the other differ,
GeckoLib empties and refills its table of 40 bones.
**What the player would see:** Nothing.
**Where:** `savanna/.../client/CrocodileModel.java:48, 57-67`; `CrabModel.java` (its `getModelResource`);
`LionModel.java` (its three `get...Resource` methods).
**Proof:** GeckoLib's `GeoModel.getBakedModel` calls `AnimationProcessor.setActiveModel` (clear and re-register every
bone) when the model differs from the last one drawn; it does not restart clips. The fish renderers that switch looks
(tilapia mouth full, catfish plump) do the same on 11 and 23 bones.
**Fix proposed:** Static name tables as `RemainsRenderer` already has. Lowest priority.

### Open round 12 findings that are part of the frame's cost (not repeated, only placed)

- **R12-63 (open; proven):** while an otter holds a crab or a bullfrog, that animal's body plan file is opened and read
  twice every frame, inside a locked method (`savanna/.../animal/HeldCatch.java:52, 79-86`: nothing is remembered for
  an animal with no "held" part in its plan, and only the barb and tilapia have one). The only place found where a
  **file is read every frame**. Small in the table only because at most 2 otters exist; it is the cheapest proven fix
  on this list.
- **R12-47 (open):** `FrameWatch` always on (above, path 3).
- **R12-69 (open):** the dragged tail is updated before its weight is checked (monitor only).
- R12-9, R12-49, R12-51, R12-54, R12-62, R12-65, R12-68: open, checks or tools, not part of the game's frame.

### The costs, biggest first

| # | Cost | Per | Proven or to measure | What to measure, with what scene |
|---|------|-----|----------------------|----------------------------------|
| 1 | Animals' plain box drawing, 10-20 thousand boxes, about 33 objects a box (R4-18) | frame | mechanism proven, size to measure | A river at its caps, camera along it; `FrameWatch`'s `[frames]` lines (frame time, ms clearing memory) and the F3 profiler's entity share; the same with fish only, then banks only |
| 2 | Animals' clip pass with no pose reuse (lion clip 10,934 keys, monitor 9,639, crab 4,235; key lists scanned from the start each frame) | frame | to measure | Same scene; compare a far school with clips frozen (`/tick freeze` does not stop clips; use the film camera's step) |
| 3 | Mud coat re-draws (R4-2) | frame | to measure | Scene 1 with animals muddy vs clean; one coated monitor near |
| 4 | Insects' clip pass, near ones (3 players, new queues per bone per frame) | frame | to measure; the code's note says clips are about half of drawing an insect | A raid, two colonies, camera 6 blocks off (inside `PoseMemory`'s near range) vs 30 blocks |
| 5 | Edge search for every known insect (R4-20) | client tick | to measure | Two colonies within 80 blocks, camera looking away; client tick time in F3 |
| 6 | Ground reads per frame, crocodile and monitor (R4-21) | frame | to measure | 5 crocodiles on a bank within 64 blocks, at 60 and at 144 fps |
| 7 | Walk speed note read as text (R4-19) | frame | to measure | Scene 4, a memory profile for `String.split` |
| 8 | Fly clouds: per fly, name text and small objects per corner (18 clouds, 4-12 flies, 23 boxes) | frame | to measure, bounded by the 24-block limit | A carcass with clouds, camera 5 blocks off |
| 9 | Shadows: a block read under every animal with a shadow, each frame | frame | to measure (Minecraft's own) | Scene 1 with entity shadows off in the options |
| 10 | `FrameWatch` always on (R12-47) | frame + a 5 ms thread | to measure | Any scene, on and off |
| 11 | Otter holding a crab or frog: a file read twice a frame (R12-63) | frame | **proven** (the read), size to measure | An otter with a crab |
| 12 | Drumming soldiers' notes | server tick, per player | to measure | A mound alarmed, 10 or more soldiers, packet count |
| 13 | Young workers drawn twice (R4-3); file names built per call (R4-22) | frame | proven small | none needed |
| 14 | Synced values the mod sends | server tick | **proven** about 0 in steady state | none needed |

**Order to measure:** 1, then 3 on the same scene (one launch, animals muddy and clean), then 4 and 5 on one raid scene
(camera near, far, and turned away). Those three runs settle which of R4-18 to R4-21 are worth building. Rows 6 to 12 only
if those leave time unexplained.

### Solid on this thread (do not "fix")

- **The insect drawing path**: `BoneMesh` (no objects per box, buried faces dropped, bounded at 4,096 bones), the far
  look (227 to 54 boxes), `PoseMemory` (reuse only while frames are quick). It is the model for R4-18's fix.
- **Per tick, not per frame**: the insect body's turning, slope, edge pose, naps and waist are worked out in the tick
  and only blended in the frame.
- **Layers stop early**: carried load, clamped head and young-pale each cost one check per bone or per insect when
  there is nothing to show.
- **GeckoLib works out a bone's matrix once per frame**, and tracks matrices only for bones asked to (one bullfrog
  bone).
- **The server sends changes only**: every synced value; the mud coat's packet; no per-tick packet of the mod's own.
- **Film and debug handlers** (`FilmDirector`, `FilmFrameMeter`, `FilmStep`, `ReportShot`) cost one check each in play.
- **Fly clouds** are simulated only when drawn and flies are culled at 24 blocks, legs at 4.
- **`PlantPushClient`** empties its sways per world and its render handler stops when none sway.
- **Body plans** are read once at sign-up; `InsectModel` and `AntRenderer` remember their plan.
- **Static memories** are bounded or weak (except R4-7's two small maps).
- **Corpses** (`AntCorpseRenderer`) are 15 plain boxes with no shadow and a once-a-second update.
- **The termite queen's model code**: about 26 bone look-ups a frame for one animal per colony; nothing to gain.

### What this reader did not check

- Nothing was measured; every size above is a count or the code's own comment.
- Minecraft's default tracking range for the insects (R4-20 leans on it).
- Whether animal textures have see-through pixels that would break `BoneMesh`'s buried-face rule (R4-18's fix).
- How many river animals are in view at once in real play (the caps are per kept land, not per screen).
- `TailDrag.update`'s inside, `MoveRider.drawnShift`, `Spots`, `OwnBones`, `SwimFold`, `BodyRecorder`, the egg, larva
  and nymph renderers, `StingPainOverlay`, the doum palm's model, the mod's particles.
- The animals' own client `tick` bodies (round 9's ground) and `Attention.tick`'s entity search.
- The cost of Minecraft's shadows and of its own position packets.
- `StallWatch.gcMillis` was read (`framework/.../debug/StallWatch.java:69-78`: a walk over the cleaner's counters, a
  few objects a frame); its real cost is part of R12-47's measurement.
- The joint counts behind R4-21's "hundreds" and the block reads behind R4-20's "20 to 30" are my estimates from the
  loops, not counts from a run.
