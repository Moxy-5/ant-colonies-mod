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

(The findings follow, written slice by slice as each is checked.)
