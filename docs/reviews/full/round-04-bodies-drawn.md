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

**Count:** 37 findings: 6 A, 16 B, 15 C (48 in the readers' files; 6 were the same thing found from two sides and are
merged, each merged finding says so; the rest of the difference is the readers' own numbering). The findings are in
the order the readers finished: the framework slice (R4-1 to R4-11), the small animals and effects (R4-12 to R4-17),
the thread (R4-18 to R4-22), the big animals (R4-23 to R4-37).

## The short of it

The insects' drawing is in good shape and the server sends next to nothing: no value of the mod's goes out every
tick for any insect or animal, the insect renderer has its three savings (ready-made faces, a far model, far poses
reused), its layers stop at once when there is nothing to show, and the body's turning, slope and edge pose are
worked out per tick and only blended per frame. No drawing code can be reached on a dedicated server. Nothing here is
a candidate for a rewrite.

What is wrong lies almost all on the animals' side, the newer code:

1. **The animals are drawn the slow way** (R4-18): none of the insects' three savings, more boxes each than any insect
   (monitor 665, bullfrog 393, lion 322, crab about 250), and draw distances pushed out to 64 to 128 blocks. A full
   river is two to four times the boxes of a two-colony raid, each box several times dearer. With it: a muddy animal
   drawn two or three times over (R4-2) and the crocodile and monitor reading some hundreds of blocks every frame
   (R4-21). None of it is measured.
2. **Five things a player can see:** reeds and hippo grass vanishing for about two seconds beside whoever walks
   through them (R4-12); a crocodile, monitor or otter popping out of sight at the screen's edge with much of its body
   still in view (R4-23); the shadow of whatever a crocodile holds stood on edge or spun round (R4-24); a lioness
   drawn standing on the ground while the buffalo she grips carries her off, when the server's word arrives after the
   screen's own guess (R4-1); the death roll rewinding on a slow server (R4-25).
3. **Small traps for the next clip or model:** code that adds to a bone and relies on every clip keying that bone
   (R4-14, R4-33), the termite queen's model throwing away her walk clips' bob (R4-27), tables kept for the whole
   sitting that a reload does not renew (R4-7, R4-28).

## The ones that matter most

1. **R4-12:** reeds and hippo grass blink out of sight beside a walking player or animal, and round big animals more
   than 64 blocks off. One rule mends it: every marked plant the client can see gets a spring.
2. **R4-23:** the crocodile (7.5 blocks long, culled by a box 0.9 wide), the monitor and the otter vanish at the
   screen's edge. The buffalo's and lion's fix, never given to these three.
3. **R4-18 with R4-2 and R4-21:** measure a full river first (one scene, animals muddy and clean); then give the
   animals the insects' drawing path. This is the round's one lag risk.
4. **R4-24 and R4-25:** the crocodile's hold: the held one's shadow, flames and name tag turned with it, and the roll
   on the wrong clock.
5. **R4-1:** the strike's hit or miss must be the server's word both ways round, with the unit test `Strikes.Ride`
   never had.

## For the user to decide

Three findings carry a `**Your call**` line; none blocks the others' fixes.
- **R4-9:** should Minecraft's own animals show mud? My pick: no, and stop giving them a coat.
- **R4-29:** should a crocodile's and monitor's hit boxes follow the body over a bank? My pick: yes, once it is seen
  to matter in play.
- **R4-32:** should a buffalo or lion killed on a step fall along the slope? My pick: yes.
If R4-18's measurement says the river is dear, shorter draw distances for the small many-boxed animals (a frog 40
blocks off is a few pixels) would be a fourth question, asked then with the numbers.

## What this round did not do

- **Nothing was run or measured.** Every lag finding is a count of work, never a time. The five visible faults are
  walked through the code, not filmed or seen in play; R4-1 and R4-25 are "likely" (each names its unchecked link).
- **The leading session checked the six grade A candidates' lines itself** (each finding says what was checked) and one
  more that a reader graded A: the silent fly cloud (R4-13) is B (one link unchecked, a rare trigger, mended by walking
  away). One C finding was corrected: a player's mud is drawn (`MudPlayerLayer`); only Minecraft's own animals carry a
  coat nobody draws (R4-9). The B and C findings are the readers' own, read but not walked a second time.
- **The NeoForge lines behind R4-24** (where the "before drawing" event is posted) were read by the slice reader in the
  21.1.256 sources; the leading session did not open them again.
- **Round 12's files were not read again** (listed at the top), nor the entities' own client ticks (rounds 3 and 9), nor
  the particles' inside beyond the blood mist and the footprint.
- `docs/BUGS.md` was searched by class and animal name, not read whole.
- The readers' scratch scripts (box counts from the model files, the list of every synced value set) lived in a session
  scratchpad and are gone; each finding names what its script did.

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

**Status (2026-11-26, `claude/review-fix-a-4-6`): fixed: `Strikes.Ride.target` takes the server's word before anything else, whichever came first; told it grips after its own "missed", the screen's lioness eases from the aim she was drawn at onto her place from then on (`late`). Unit test `StrikesRideTest` (both ways round, the word before the guess, the server's own rule). Not seen in a game: how often the word comes late is still not known.**

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

### R4-9 (C, proven for Minecraft's own animals): a mud coat on one of Minecraft's own animals is kept and sent but never drawn
(Corrected by the leading session: the reader's "a player wears mud nobody sees" is wrong. `MudPlayerLayer`, added to the player renderers in `SavannaMudClient.java:55`, draws a player's mud. What holds: Minecraft's own animals (a pig, a cow) carry a coat that nothing draws, and the two leftovers named at the end, which the leading session did not check.)
**What is wrong:** `SavannaMudBlock.muddy` puts mud on any living thing but insects ("a player in soupy mud gets muddy legs
(2)"), and the coat is synced to the player, but only GeckoLib renderers carry `MudCoatLayer`, so a player (or any
vanilla mob: the pig in `MudTests`) wears mud nobody sees; only its shed bits fly. Also the coat attachment is not
copied on death, and a dimension change gives the client a new player entity with no coat until the next change.
**Where:** `savanna/.../block/SavannaMudBlock.java:245-252`; `coat/MudCoat.java:83-96, 144-149`.
**Fix proposed:** say in `docs/DESIGN.md` that vanilla bodies carry the coat as data only, or leave players out of `muddy`.

**Your call (2026-11-26):** should Minecraft's own animals (pigs, cows, horses) show mud after walking through it? Today they carry the mud as numbers and shed bits of it, but look clean. My pick: leave them clean (a mask per vanilla animal is art work for little gain) and stop giving them a coat at all.

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

**Status (2026-11-26, `claude/review-fix-a-4-6`): fixed: one rule, `PlantPush.spring`: every plant in a block a pusher's box overlaps gets a spring (upright when its middle is too far to lean), and every marked plant within a block of the box too (the server's box runs a little ahead of the drawn one); the 64-block cut is gone, so plants round far pushers are drawn. A plant on the pusher's very middle with no way to lean gets an upright spring too. Left: a plant marked with no pusher the client knows near it (joined mid-push, the pusher gone) still has none for its last two seconds. Unit test `PlantPushSpringTest` (a player's box on a block corner). Not seen in the game.**

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
(Also found by the big animals' reader as "the big animals have no far model": a herd of buffalo or a pride 100 blocks off costs as much to draw as one beside the player; that reader's test scene: 20 buffalo and a pride at 40, 80 and 120 blocks.)
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

**Status (2026-11-26, `claude/review-fix-a-4-6`): half fixed: measured, then two of the insects' savings given to every animal. Measured (new drawing-cost scenes `f_river`, 103 river animals of about 14,400 boxes all in view, and `g_savanna`, 12 buffalo and a pride of 6; `tools/perf_scenes.py`; the frame meter's middle drawing time less the empty scene's): the river cost 13.3 ms a frame and the herd with the pride 4.8 ms, so the finding holds (a whole frame at 60 a second is 16.7 ms). (1) Every animal renderer is the framework's `BoneMeshRenderer` (the insects' ready-made faces): 8.9 ms and 3.1 ms. (2) `BoneMesh` leaves out the faces of whole, solid boxes turned away from the eye, about half of all faces (writing corners was 58 percent of what was left, by `-PfilmProfile`): 6.5 ms and 2.3 ms, half of what it was; a crowd of 400 Matabele 46.5 ms where it was 61.6 (one launch each). Pictures: the river and savanna scenes look the same before and after, and the ten guard scenes that repeat (the termites, the Matabele climbs, the clamped head, the carry) are the approved ones to the pixel. Unit test `BoneMeshTexturesTest` (every model's own pictures solid on every box face, which both savings need). Left: the clips (about 4 in 10 of what is left; the insects' pose reuse for far ones is not given to the animals yet, their models pose themselves each frame so it needs a look at each), and far looks or shorter draw distances for the small many-boxed kinds (a look, so the user's call, with these numbers). Not measured in a real river in play.**

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
(Also found by the big animals' reader, who counted it: crocodile 9 pieces by 3 columns, 2 footings and 4 planted feet, about 150 block lookups a frame; monitor 20 by 3 columns, footings, water checks, 4 feet and the dragged tail's 12 joints, about 400 a frame. `StandTilt`'s 4 reads a frame for a lion or buffalo are fine as they are.)
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
(Also found by the big animals' reader, who adds: the crab joins bone names and looks four bones up each frame, the monitor joins 4 to 12 bone names a frame, the termite queen 24.)
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

---

## Slice: the savanna's big animals and the termite royals (models and renderers)

Read: the crocodile's, monitor's, hippo's, lion's, buffalo's, otter's and crab's models and renderers, `CrocodileRollClient`, `DoumModel`, `StandTilt`, `SwimFold`, the termite queen's model and renderer, `TermiteNymphRenderer`. Calls followed into the entities, the framework, GeckoLib 4.9.3 (`javap`) and Minecraft 1.21.1 in the NeoForge 21.1.256 sources. Three of this reader's eighteen findings were the same thing the thread reader found and are merged there: the ground read every frame (R4-21), no far model for the animals (R4-18), names built every frame (R4-22).

### R4-23 (A, proven): a crocodile, a Nile monitor and an otter vanish at the screen's edge while most of their body is still in view
**What is wrong:** Minecraft decides whether to draw an animal at all from a "culling box": if that box is outside the
screen, nothing of the animal is drawn. By default the culling box is the animal's walking box (plus half a block). The
crocodile's walking box is 0.9 blocks wide but its drawn body is 7.5 blocks long; the monitor's box is 0.7 wide for a
body 4.5 long; the otter's 0.75 for a body 2.6 long. The hippo, buffalo and lion were given a bigger culling box for
exactly this (BUGS 271: "its head and rump blinked away at the screen's edge"); the crocodile, monitor and otter never
were.
**What the player would see:** turn the camera so a crocodile's middle goes just off the side of the screen: its whole
body, head or tail still well on screen (up to about 2.8 blocks of it), pops out of sight; it pops back when the middle
comes back. Same at the top or bottom edge (looking down on one basking below you), and when its middle is behind a
corner of the view. Smaller for the monitor (about 1.4 blocks of tail) and the otter (about 0.4 blocks).
**Where:** no `getBoundingBoxForCulling` in `savanna/.../animal/CrocodileEntity.java`, `MonitorEntity.java`,
`OtterEntity.java` (all three are multipart: `isMultipartEntity` true, `getParts()` = their hit chains); against
`HippoEntity.java:854-857`, `BuffaloEntity.java:971-981`, `LionEntity.java:905-916`. Sizes: `SavannaEntities.java:190`
(crocodile 0.9), `:211` (monitor 0.7), `:271` (otter 0.75); lengths `CrocodileEntity.java:175-176`,
`MonitorEntity.java:262`, `OtterEntity.java:291`.
**Proof:** Minecraft 1.21.1 (NeoForge 21.1.256 sources) `EntityRenderer.shouldRender:52-58` draws only if
`getBoundingBoxForCulling().inflate(0.5)` is in the view; `Entity.getBoundingBoxForCulling:3030` is `getBoundingBox()`;
GeckoLib 4.9.3's `GeoEntityRenderer` does not override `shouldRender` (checked with javap); none of the three sets
`noCulling` (grep). The hit chains' parts are not drawn, so they do not widen it either.
**Checked by the leading session:** a search of both mods finds `getBoundingBoxForCulling` only in the buffalo, lion, hippo, fly cloud and remains, and no `noCulling` anywhere; the crocodile, monitor and otter have none. Not seen in the game.
**Fix proposed:** the buffalo's and lion's rule for all three: the walking box joined with every hit bead's box
(`chain.parts()`), inflated a little for the snout and tail tip past the last bead, and a fixed inflate on the first
tick before the beads are placed. One shared helper on the hit chain (the four copies would become one), e.g.
`HitChain.cullingBox(box, extra)`.

**Status (2026-11-26, `claude/review-fix-a-4-6`): fixed: `HitChain.cullingBox`: the walking box joined with every hit bead and a little more; the crocodile, the monitor and the otter have it now, and the buffalo's and the lion's own copies are this one helper (the chain's whole reach before the beads are first placed). Not seen in the game.**

### R4-24 (A, proven): a player or animal held by a crocodile gets a shadow stood on edge (and spun round in the death roll)
**What is wrong:** `CrocodileRollClient` tips the held one's drawing (laid flat along the snout, leaning back in the tug
of war, spun round the spine in the roll) by changing the drawing's frame in NeoForge's "before drawing a living thing"
event. That event comes before the animal's own renderer saves the frame, and nothing puts the frame back afterwards. So
everything Minecraft draws for that animal after its body with the same frame is tipped too: its shadow on the ground,
the flames if it burns, its name tag, and the F3+B hit box.
**What the player would see:** a friend held on the bank by a crocodile (laid flat along its snout, turned 90 degrees):
their round ground shadow is turned with them and drawn standing upright in the air, a dark see-through patch about a
block wide, about a block (half their height) out from their middle toward or away from the snout. A cow in the tug of war has its shadow tilted 12 to 18 degrees, half sunk into the ground and half in the
air, rocking with each heave. In a roll in the shallows a shadow on the river bed is swung round in circles. A held
friend's name tag is turned with them (out in front, sideways).
**Where:** `savanna/.../client/CrocodileRollClient.java:41-75` (`onDraw`: `pose.translate` / `mulPose` with no push or
pop), `:83-109` (`layAlong`, `struggle`).
**Proof:** NeoForge 21.1.256's patched `LivingEntityRenderer.render:52-54` posts `RenderLivingEvent.Pre` before its own
`poseStack.pushPose()`; its `popPose` at `:136` only undoes its own push; `super.render` (the name tag) and the `Post`
event run after that pop at `:137-139`. `EntityRenderDispatcher.render:160-183` then draws the flame (`:164`), the
shadow (`renderShadow`, `:174`, its quads made from `poseStack.last()`, `:374`) and the hit box with the same stack before
its own `popPose`. Not filmed.
**Checked by the leading session:** `CrocodileRollClient.onDraw` (lines 41-75), `layAlong` and `struggle` change the pose stack with no push and no pop, and there is no `Post` handler. The NeoForge lines (the `Pre` event posted before the renderer's own push; the shadow drawn after with the same stack) are the reader's reading of the sources, which agrees with how the leading session knows that code; not opened a second time.
**Fix proposed:** `pose.pushPose()` at the start of each turning branch of `onDraw` and a `RenderLivingEvent.Post`
handler that pops it for the same entity (keep a flag on the event's entity or test the same condition again). The body
and its layers are still turned; the shadow, flame and name tag are not. (Or cancel the held one's shadow while held.)

**Status (2026-11-26, `claude/review-fix-a-4-6`): fixed: `CrocodileRollClient.onDraw` saves the frame before it turns the held one (and runs last of all handlers), and it is put back when the held one's name is about to be drawn (`RenderNameTagEvent`, which Minecraft posts right after the body; the drawing's end as a second chance), so the name, the flames, the shadow and the F3+B box are upright. Not filmed.**

### R4-25 (A, likely): on a slow or stalling server the crocodile's death roll jumps back, and a held player's view spins back with it
**What is wrong:** the roll's angle is worked out from the world's game time on the player's computer minus the tick the
roll started on the server (`rollAngle`). As R12-61 proved for block moves, the player's side adds one to that time every
one of its own ticks (20 a second whatever the server does) and is set straight back to the server's time by a packet
once a second. R12-61's fix (`MoveClock`, never follow a rewind) covers block moves only; the roll was missed ("lions and
buffalo count their own ticks" -- the crocodile does not).
**What the player would see:** on a server at 15 ticks a second each 2-second roll plays out on screen in about 1.5 s,
the crocodile lies upright still while the server is still rolling, then the time packet sets the clock back and it
flips back into mid-roll (a 5-tick step back of a three-turn roll is over 100 degrees). The held player's camera does
the same (`onCamera`): the world spins, stops, and spins backwards. During an autosave stall the same, bigger.
**Where:** `savanna/.../animal/CrocodileEntity.java:600-611` (`(int) level().getGameTime() - start + partialTick`), read
by `CrocodileModel.java:91-99`, `CrocodileRollClient.java:30-38` and `:48`.
**Proof:** walked; the client clock behaviour is R12-61's walk in Minecraft 1.21.1 (`ClientLevel.tick`,
`ClientPacketListener.handleSetTime`). Not seen in play (no slow server run), hence "likely".
**Checked by the leading session:** `CrocodileEntity.rollAngle` (lines 600-611) reads `level().getGameTime()` on the screen; the mechanism is R12-61's, fixed there for block moves only. "Likely" as R12-61 was: no slow server was run.
**Fix proposed:** the roll's drawn time through one never-rewinding clock per crocodile (the framework's `MoveClock`,
keyed by the roll's start tick), used by the model, the held one's drawing and the camera alike.

**Status (2026-11-26, `claude/review-fix-a-4-6`): fixed: on a screen `CrocodileEntity.rollAngle` reads the roll's own `MoveClock`, keyed by the roll's start tick, so the model, the held one and a held player's view never follow the game time back. As R12-61: no slow server was run.**

### R4-26 (B, proven): a GeckoLib animal held by a crocodile is never turned with the roll, laid along or made to struggle
**What is wrong:** the held one's roll, lay-along and struggle are done in NeoForge's `RenderLivingEvent.Pre`, which only
Minecraft's own `LivingEntityRenderer` fires. Every animal of this mod is drawn by GeckoLib's `GeoEntityRenderer`, which
extends `EntityRenderer` and fires GeckoLib's own `GeoRenderEvent.Entity.Pre` instead.
**What the player would see:** today the crocodile's big prey that it drags and rolls are Minecraft's animals and
players, which work. A bullfrog (an `Animal`, so big prey by `isPrey`) or any of the planned savanna land animals made
huntable is drawn upright and still in the jaws while the crocodile spins three turns round it.
**Where:** `savanna/.../client/CrocodileRollClient.java:41-56`; `CrocodileEntity.java:2446-2478` (`isPrey`: any `Animal`).
**Proof:** GeckoLib 4.9.3 bytecode (javap): `GeoEntityRenderer.firePreRenderEvent` calls `GeckoLibEvents.fireEntityPreRender`,
whose NeoForge side (`GeckoLibEventsNeoForge`) posts `GeoRenderEvent$Entity$Pre`; no `RenderLivingEvent` anywhere in
the renderer.
**Fix proposed:** also listen to `GeoRenderEvent.Entity.Pre`/`Post` (same turn, with R4-24's push and pop), or turn the
held one from the crocodile's own renderer as `OtterRenderer` draws its fish.

### R4-27 (B, proven): the termite queen's own posing overwrites her walk and turn clips' body bob and forebody swing
**What is wrong:** the queen's model sets her `body` bone's height and length place and her `forebody` bone's turn to
fixed values every frame (her settling and droop). It sets them outright instead of adding to what the clip put there.
Her walk clips (`walk_slow`, `walk`, `walk_fast`) move `body` (the bob) and turn `forebody`, her turn clips turn
`forebody`, and `paralysed` / `paralysed_fall` move `body`. All of that is thrown away for the queen.
**What the player would see:** a young queen that walks (one put down with a spawn egg, and later a founding queen in
M9.5) glides with a rigid body: no bob, no swing of the front of her body as she walks or turns, unlike the king beside
her, who plays the same clips. Her six abdomen segments are set outright too, but no clip moves them.
**Where:** `savanna/.../client/TermiteQueenModel.java:78-85` (`setPosY(lift)`, `setPosZ(-forward)`, `setRotX(-droop)`);
the clips in `animations/entity/termite_queen.animation.json` (checked with a script: `body` position and `forebody`
rotation in the walks and turns).
**Proof:** read both. The queen's `lift`, `forward` and `droop` are all 0 at size 0, so a small queen loses the clip's
values to zeros.
**Fix proposed:** add to the clip's values (`bone.getPosY() + lift`, `bone.getRotX() - droop`) with the marks left as
they were (the crocodile's `turnUp` pattern), and keep the absolute set only for the bones no clip moves.

### R4-28 (B, proven): resource reload does not refresh the monitor's tail rest pose or the otter's faces
**What is wrong:** two drawing tables are kept in `static` fields read once for the whole run of the game:
`MonitorModel.TAIL` (a `DraggedTail` that reads the 12 tail bones' rest pose from the first baked model and never again,
`ready` stays true) and `OtterModel.FACE` (`FaceLayer.of` keeps the face file in a static map, read from the jar).
Minecraft makes the renderers and models anew on a resource reload (F3+T, a resource pack, the film watch mode's reload)
and GeckoLib bakes new bones, but these two keep the old data, and each monitor's `tail` drag keeps the old lengths too.
R12-42 fixed this kind of staleness for the spot tables and body plans; these two were not covered.
**What the player would see:** in normal play nothing (the files do not change). In the film watch mode after a remade
monitor model or a changed otter face file, the films show the old tail lengths and the old faces until the game is
restarted (the look the user is shown is not the one on disk). A resource pack's otter face or monitor model is ignored
by these parts.
**Where:** `savanna/.../client/MonitorModel.java:104-105, 250-251`; `framework/.../client/DraggedTail.java:93-96`;
`savanna/.../client/OtterModel.java:29`; `framework/.../client/FaceLayer.java:104-116`.
**Proof:** walked: `prepare` returns at once once `ready`; `LOADED.computeIfAbsent` never cleared (no other writer, grep).
That the film watch mode reloads renderers is read from R12-42's notes, not checked again here.
**Fix proposed:** make both instance fields of the model (made anew with it, as `fold` already is), and drop an
entity's `tail` drag when its model changes (keep the model's identity beside it).

### R4-29 (B, likely): a crocodile's or monitor's drawn head over a bank is up to about a block from its head hit box
**What is wrong:** the hit beads (`HitChain`) were added so "a swing or an arrow at what you see lands", and are set at
the box's height plus a fixed height, along its heading. But the drawn body lies on what is under it (`GroundBend`): the
shoulders lift onto a bank up to 1.6 blocks, and the neck and head tip down over a bank's edge up to 50 degrees. Neither
moves the beads.
**What the player would see:** a crocodile lying at the water's edge with its head dipped to the water: aim at its snout
and the swing or arrow passes through it (the bead is up at the bank's height); aim at the air above it and it hits.
Same for one rising onto a bank, and for the monitor. Not tried in a game.
**Where:** `framework/.../animal/HitChain.java:66-70`; `CrocodileModel.java:105-124`; `MonitorModel.java:146-165`;
`GroundBend.java:45` (`MAX_LIFT = 1.6`), `:31` (`MAX_STEEP = 50`).
**Proof:** walked: the beads take only `owner.getY() + link.up() * scale`; the bend is client-only drawing. How far apart
in a real bank scene is not measured (it depends on the bank), hence "likely".
**Fix proposed:** worth the user's eye first (how often a player fights a crocodile at a bank). If so, the beads could
take the same ground heights on the server (`GroundBend.support` at each bead, eased per tick), which also fixes arrows
at a crocodile half up a bank.

**Your call (2026-11-26):** should a crocodile's and monitor's hit boxes follow the body where it lies over a bank (head dipped to the water, chest up on a step)? Today the hit boxes stay level at the animal's own height, so a swing or arrow at a dipped head can pass through it. My pick: yes, but only after it is seen in play to matter; it is server work for every crocodile and monitor every tick.

### R4-30 (B, proven): crossing the near line (48 or 64 blocks) snaps a crocodile's or monitor's whole pose
**What is wrong:** beyond `NEAR` the models skip the body's lie, the legs and the tail drag and reset their eased state
(`legNanos = 0`, `legStand` set, `drawnNanos = 0`); coming inside, the first frame eases with "a whole second has passed"
(`dt = 1.0`, so `k = 1`): the bend, the folded or standing legs and the dragged tail all appear at once. Leaving, they
vanish at once.
**What the player would see:** walking or flying toward a monitor lying over a step or wading at a bank: at 48 blocks
its chest and head drop onto the step, its legs fold and its tail swings down over the edge in one frame (the user
watches from far in spectator). The crocodile the same at 64 blocks (its head dipping to the water). Small on flat
ground (nothing to bend).
**Where:** `MonitorModel.java:130-139`; `CrocodileModel.java:101-104` (the bend kept and eased by `GroundBend`, but
`legs` and `eyes` read `legNanos`/`eyeNanos` from far frames too; the bend's own `lastNanos` gap > 0.5 s snaps).
**Proof:** walked: `GroundBend.update:118-120` (`dt > 0.5` gives `k = 1`), `MonitorModel.legs:280-282` same.
**Fix proposed:** on the first near frame after a gap ease in from the clip pose over the usual ease (start the eased
values at their "none" and let `dt` be one frame), or keep the cheap parts (the bend at a lower rate) out to the draw
distance so there is no line.

### R4-31 (B, to measure): a doum grove makes each chunk rebuild redo every crown, and a ripening bunch redoes a whole crown
**What is wrong:** the doum palm pieces' quads are cached by look, at most 24. Each grown palm's look is its own (its
dice and its place in the tree's grid), and a crown, each of its 2 or 3 bunches, and its ground fronds are separate
keys, so a grove of more than about 6 palms overflows the cache and every chunk rebuild works the boxes out again. A
bunch block works out the whole crown (`PalmPieces.crown`) and keeps only its own bunch; its key holds its fruit stage,
so every ripening step (a block change, which rebuilds the chunk section) works out a crown again for each bunch.
**What the player would see:** chunk sections with palms take longer to redraw on the chunk builder threads; possibly a
late-appearing section when flying fast over a palm grove. Not measured.
**Where:** `savanna/.../client/DoumModel.java:60-71, 91-103`; `block/DoumLook.java:45-63`.
**Proof:** walked; cost of `PalmPieces.crown` not measured.
**Fix proposed:** measure a chunk rebuild in a grove; if it shows, a bigger cache (crowns are a few hundred quads each),
or a bunch key without the crown's own frond boxes (build the bunch from the crown's plan only).

### R4-32 (B, likely): a dying buffalo or lion on a step drops its tilt at once and dies with its legs hanging in the air
**What is wrong:** `StandTilt` tilts a standing body to the ground under its front and hind feet only while the animal
is alive; dead (`isAlive()` false from the killing blow) the wanted heights jump to the box's own height, eased over
0.2 s. On a step the death clip then plays level about the box, which stands on the higher block.
**What the player would see:** a buffalo killed with its front feet up a 1-block step straightens in a fifth of a second,
then falls over with its hind end hanging up to a block over the lower ground, for the 1.5 s of its death clip, before
its carcass appears. The lion the same (1.2 s).
**Where:** `StandTilt.java:61-64`; `BuffaloEntity.java:120, 934-939`; `LionEntity.java:126`.
**Proof:** walked. Not filmed.
**Fix proposed:** keep the last tilt through the death (hold `drawn.frontY/hindY` when `!isAlive()`), so the death clip
falls on the slope it stood on; the carcass (`RemainsEntity`, not in this slice) would want the same ground.

**Your call (2026-11-26):** should a buffalo or lion killed on a step fall along the slope it stood on? Today it straightens level in a fifth of a second and its death plays with one end hanging over the lower ground. My pick: yes, keep its tilt through the death.

### R4-33 (C, proven): two writes to shared bones that do not leave GeckoLib's marks as they were
**What is wrong:** the bones are shared by every animal of a kind, and GeckoLib puts a bone back to its own animal's clip
pose next frame only if nothing marked it; every other added turn in these models keeps the marks as they were. Two do
not: the otter's jaw held open round a catch (`jaw.setRotX(Math.min(...))`) and the crocodile's eyelids
(`lid.setScaleY`). Both are harmless today only by luck of the data: every one of the otter's 42 clips keys the jaw
(checked with a script), and every crocodile drawn sets its lids every frame.
**What the player would see:** nothing today. An otter clip made without a jaw key would leave the next otter drawn with
the first one's jaw open.
**Where:** `OtterModel.java:70-77`; `CrocodileModel.java:164-170`.
**Fix proposed:** the same `marked` / `resetStateChanges` pattern as the rest of each model.

### R4-34 (C, proven): a dying hippo keeps turning its head and pupils toward what it watches
**What is wrong:** `HippoModel.eyes` has no alive check: the head turn (up to 18 degrees) and the pupils keep following
what caught its eye (the killer, often) through the death clip, as the body rolls onto its side. The crocodile, the
monitor, the buffalo, the lion and the otter all stop or ease out when dying.
**What the player would see:** for the one second of its death, the hippo's head stays turned to the player and its
pupils follow them while it rolls over.
**Where:** `HippoModel.java:55, 64-87`; `HippoEntity.java:657-660` (the client keeps ticking `attention` while dying).
**Fix proposed:** `turnHead && hippo.isAlive()`, and the pupils left to the clip when dead (as the buffalo).

### R4-35 (C, proven): throwaway objects made every frame per animal
**What is wrong:** small per-frame allocations (rule book 1j): `new String[] {...}` round each pupil and lid loop
(crocodile, monitor, hippo, crab), `new StandTilt(...)` for every lion every frame (the buffalo keeps one), `new GeoBone[]`
and `new boolean[]` in the monitor, two `Quaternionf` per folding leg bone, a `Vector3d` per placed animal, `new
FaceLayer.Frame` per otter. Each is small; together they are steady garbage with many animals on screen.
**Where:** `CrocodileModel.java:164, 200`; `MonitorModel.java:119, 284, 385-386, 471`; `HippoModel.java:69`;
`CrabModel.java:70`; `LionModel.java:73`; `OtterModel.java:68`.
**Fix proposed:** static name arrays, the lion's `StandTilt` kept on its `Measures`, a scratch quaternion pair; fold into
R4-22's tidy-up.

### R4-36 (C, proven): hand-copied numbers and files read past the resource system
**What is wrong:** `TermiteQueenModel` holds the abdomen's build numbers (`SEG_LENGTH`, `HEIGHT`, `CENTER_Y`, `TAPER`)
"kept in step" by hand with `tools/termite_models.py` (they agree today), the very pattern R12-70 found drifted for the
monitor; no test compares them. `SwimFold` (and `FaceLayer`) read their files from the mod's jar with
`getResourceAsStream`, so a resource pack that changes the monitor's swim clip changes the clip but not the fold drawn
toward it.
**Where:** `TermiteQueenModel.java:24-28`; `SwimFold.java:40-46`.
**Fix proposed:** a unit test reading the queen's geo for the six segments' sizes (or read them at load); `SwimFold`
through the resource manager is optional (resource packs are not a goal of the mod).

### R4-37 (C, proven): wrong or misplaced comments
- `MonitorModel.java:101-103`: the javadoc "The tail, base to tip, dragged" stands above `HAND_OVER`'s own javadoc;
  `TAIL` (line 104) has none.
- `MonitorModel.java:128-129`: "never on a move" reads as "far off it is never on a move"; it means the far rule is not
  used on a move.
- `CrocodileModel.java:18-21` (class doc) says nothing of the legs, eyes, lids, roll and carried young it also draws;
  `HippoRenderer`/`HippoModel` docs fine.

### Solid in this slice (do not "fix")
- **No drawing code is reachable on a dedicated server:** nothing outside `savanna/.../client/` imports a class in it
  (grep); `CrocodileRollClient` and `SavannaClientEvents` are `Dist.CLIENT` event subscribers; entities only mention
  `CrocodileRollClient` in a comment.
- **Per-animal drawing state lives on the entity**, never in the shared model or renderer: blink and eye clocks, head
  and eye aims, leg stands, the drawn place and hand-over, `DrawnGround`, `GroundBend`, `TailDrag`, `Attention`.
  The models' added turns keep GeckoLib's change marks as they were (but R4-33's two), so one animal's pose never leaks
  into the next; the monitor puts its trunk hinges back before every monitor.
- **`OtterRenderer`'s scratch fields** are written and read inside one `render` call and reset at its start; the held
  fish is drawn only through the otter (its own renderer refuses while held), at the head's real drawn frame.
- **The eases run on `DrawClock` game time** with `1 - exp(-dt/ease)`: the same at any frame rate, frozen when paused
  or `/tick freeze`; a gap over half a second snaps (R4-30 is the one place this is seen).
- **The hippo, buffalo and lion culling boxes** take in the hit beads (BUGS 271); R4-23 is only that the other three
  never got it.
- **`LionRenderer.applyRotations`** keeps a rider at its own heading (the hunt film's jaw 1.4 blocks off); the buffalo
  held and its holders drawn level (R12-71) agree with the spot tables.
- **`DoumModel`**: quads baked into the chunk (no per-frame cost), the cache locked round get and put with the work
  outside (several chunk threads), a fresh cache with each resource reload; the block entity always gives model data.
- **`SwimFold`** reads both sides from the clip and fails loudly on a missing bone (R12-70 fixed; `SwimFoldTest`).
- **`TermiteNymphRenderer`**: the meal is never 0 (`setMeal` keeps it at least 1); a worker nymph's `null` rank is
  handled.
- **The teen crocodile's model** has the grown one's joint places, so the bend and legs fit it; its missing `carried`
  bone is null-checked.
- **`CrocodileRollClient.onCamera`** turns only the viewer's own camera, from synced data, so each player sees their
  own hold; other players see the held one turned (for Minecraft-drawn bodies, R4-26).

### What this reader did not check
- No frame cost was measured (R4-21, R4-18, R4-22, R4-31 are "to measure"); no game, film or test was run.
- That GeckoLib 4.9.3 applies the entity's own `getScale()` (assumed, as round 12 did).
- `MudCoatLayer` / `MudCoatLook`, `FaceLayer.draw`, `InsectModel`/`BodyLanguage`, `AntRenderer` (the queen's culling
  and far pose) and `RemainsEntity` (the carcass): framework or other slices, read only as far as a finding needed.
- The entities' client ticks (`Attention.tick`'s entity search every 5 ticks) and their synced data: game logic, not
  read for cost here.
- Whether the film watch mode's reload remakes the renderers (R4-28's one link, taken from R12-42's notes).
- Multiplayer was not run: R4-24's upright shadow and R4-25's rewind are walked in the code, not seen.
