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
