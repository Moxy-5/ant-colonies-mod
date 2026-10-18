# Animation engine: the upgrade review (2026-10-18)

Asked for by the user before choosing the savanna creatures: the coming animals will be far more animation-heavy than
the ants and termites, so first find **every way to make the animation engine better and faster**. This is a review
only: nothing in the game or the tools was changed. Nothing here is decided; the user picks.

It builds on the first review (`docs/plans/animation-engine-review.md`, 2026-10-16), whose top three (walks at three
speeds, the waist bending by distance walked, stepping to turn) are built; their items 4 to 13 that are still open are
folded in here where they fit, not repeated.

## Words used in this report
- **Bone:** one movable part of a model (the head, one leg segment). A **rig** is the set of bones and how they hang
  together (the foot hangs from the shin, the shin from the thigh).
- **Clip:** one animation (walk, idle, dig). It is stored as **key frames**: the turn of each bone at certain moments;
  the game fills in the moments between.
- **IK (inverse kinematics):** working a leg out backwards: "the foot must be here, so the hip, knee and ankle turn
  like this". The motion library already does this in Python.
- **Gait:** the pattern in which the legs step (the tripod gait of ants). **Duty factor:** the share of a step a foot
  is on the ground.
- **Live layer:** what the game adds on top of the clips every frame, worked out from what the insect is doing right
  then (`client/BodyLanguage`: the head look, the waist bend).
- **Frame:** one picture the game draws; at 60 frames a second each frame may take at most 16.7 milliseconds (ms).
- **Render thread:** the part of the game that draws the pictures. If it is busy all the time, the frame rate drops.
- **Profiler:** a tool that looks at what the game is doing about 100 times a second and counts where the time goes.
- **Vertex:** a corner of a box face. Every frame, every corner of every box of every visible insect is worked out
  again on the main processor (the CPU) and handed to the graphics card.
- **Baking:** working something out once ahead of time and keeping the answer, instead of every frame.
- **LOD (level of detail):** drawing far things more simply (fewer boxes, no animation), because they are a few pixels.
- **Instancing:** telling the graphics card "draw this one shape 2,000 times at these places" in one go.
- **Culling:** not drawing what can't be seen (behind the player, too far, underground).

## What I measured (2026-10-18)
- **The scene:** the in-game camera (`./gradlew runFilm` with a scene written only for this, not added to the mod)
  put **400 worker ants** on a flat floor 3 to 15 blocks from the camera, all playing the walk, and the Java profiler
  (JFR, part of Java) recorded the game for 30 seconds without ants and 50 seconds with them. Picture: all 400 drawn.
- **Server cost:** the mod's own stopwatch (`[perf]` in the log): 400 standing ants (no mind) cost 1.7 ms a tick,
  **0.004 ms each**. Working insects measured earlier: about 0.02 ms each. So the server is not where the coming crowds
  hurt; drawing is.
- **Drawing cost, without ants:** the render thread spent most of its time waiting (the frame limit): the game had
  time to spare.
- **Drawing cost, with 400 ants:** the render thread was busy all the time, and **93% of its work was drawing the
  ants**:
  - **40% working out the poses from the clips** (GeckoLib's animation players). Inside that, about **9%** is caused by
    our own players: each one throws away the bones it does not own every frame (`AntAnimationController.process`),
    so GeckoLib rebuilds its per-bone lists every frame (`createInitialQueues` 6.6%, the throwing away 2.2%). Also,
    GeckoLib finds "where in the clip are we" by walking through a bone's key frames **from the first one, every frame,
    separately for x, y and z** (checked in the library's code, `getCurrentKeyFrameLocation`), so a clip with many key
    frames costs more to play, not only more disk space.
  - **45% bone and corner maths** (GeckoLib's `renderRecursively`): 18% working out the corners, **9.7% turning the 28
    boxes that are set at an angle inside their bone** (the legs are slanted boxes), 10% a per-box check GeckoLib does
    for flat boxes (`fixInvertedFlatCube`, run on every box although the model has no flat ones).
  - Our live layer (head look, waist bend): too small to show (under 0.5%). The extra layers (carried soil, pale young):
    0.7%. The insects' own client tick: 3%. The film camera's own checks: 4% (only while filming).
  - **Not measured: the frame rate itself.** Nothing in the mod or the film camera writes the frame time down, and the
    profiler cannot count frames. This is the first thing to add (idea F1).
- **The files:** the worker's animation file is **764 KB with 10,067 key frames in 17 clips** (the first review saw
  400 KB; the three walks and the turns nearly doubled it). The biggest clips: cleaning both antennae 1,495 keys, the
  walk 1,098, the slow walk 1,028. The worker model: 32 bones, 63 boxes, 28 of them set at an angle.
- **The tools:** making a clip with the motion library takes 0.3 to 0.5 seconds; the lab's full check of the 17
  worker clips takes **38 seconds** (plain Python drawing every frame, one clip after another).
- **How far insects are drawn today (from the game's rule, worked out, not tried in game):** Minecraft stops drawing a
  creature beyond 64 times the average side of its box (times the player's "entity distance" setting, 100% by default).
  The worker ant's box is 0.3 × 0.2 × 0.3, so **it disappears beyond about 17 blocks**; a termite worker about 14.
  A Matabele major with a box about 0.5 × 0.3 would show to about 28 blocks. A raid column 50 blocks long would be
  invisible beyond its first stretch unless the drawn-only ants get their own rule.
- **How big an ant is on screen (worked out, 1920 × 1080 screen, the normal field of view):** a worker (0.5 blocks
  long as drawn) is about 24 pixels long at 16 blocks, 12 at 32, 6 at 64 and 4 at 96. A Matabele major (0.7 blocks)
  about 1.4 times that. Beyond about 32 blocks an ant's legs are a pixel or less: no need to animate them there.

# Part 1: BETTER (more realistic, more reusable)

Ranked by value for the coming work (Matabele ants and fights first, then the savanna creatures). Sizes: **small** =
one session, **medium** = a few sessions, **large** = a week or more of sessions.

### B1. Body plans: describe any creature's body once, in a file
- **What:** today the motion library knows only one body: six legs named `leg_l1` to `leg_r3`, each exactly three
  bones (`leg_x`, `_lower`, `_foot`), the gait tables written for those six names, the middle legs found by the digit
  `2`, the waist as three fixed bone names (`petiole`, `postpetiole`, `abdomen`) in `BodyLanguage`, the head player's
  bones found by name start (`head`, `mandible`, `antenna`, `carry`). A **body plan** would say, per creature: its
  limbs (any number, each a chain of any number of segments, which end touches the ground, which side), its spine (a
  chain of segments: an ant's waist, a lizard's back and tail, a centipede's 21 body rings), its head, its feelers or
  ears, its jaws, and which bones belong to which animation player. Every tool (the motion library, the lab's checks,
  the live layer, the animation players) reads the plan instead of the names.
- **Why:** the coming list holds 4-legged walkers (aardvark, mongoose, monitor, skinks, agamas, toads), 2-legged
  walkers (birds, and the ground pangolin, which really walks on its hind legs with its front legs and tail held off
  the ground), 8-legged spiders, a centipede with 21 pairs of legs (the savanna's *Scolopendra morsitans*), and ants
  with 5 or 4 legs. Without plans, each would be hand-written again; with them, a new creature is a plan plus numbers.
- **Size:** medium (Python side), small (Java side: the player bone lists and the waist names come from the plan).
- **Depends on:** the M2 worker model's bone names become the insect standard: the plan format should be settled
  together with the user's Blockbench naming, or at least so that M2 only needs a new plan (the motion library already
  has a bone map for this, `Rig(model, bonemap)`; the plan replaces and extends it).

### B2. A gait maker for any number of legs, including lost legs
- **What:** one function that makes a walk for any leg layout from a few rules instead of the hand-typed tripod table
  (`TRIPOD`, `LOOSE_TRIPOD` in `tools/ant_motion.py`): each leg's moment in the cycle from its place and side, the duty
  factor, and the speed. It would cover:
  - **6 legs:** the tripod as now (slow: the looser tripod).
  - **Lost legs (Matabele):** real insects re-time their legs when one is gone. Cockroaches whose middle legs were cut
    off walk with the front and hind legs in diagonal pairs (Hughes 1957); stick insects do the same (Grabowska et al.
    2012). Frank's rescue study on *Megaponera* (2017) reports that ants missing one or two legs soon run again almost
    normally (to check the exact numbers before building). The real rule underneath is **staying balanced**: the
    body's centre must stay inside the feet on the ground. So the maker would choose each lost-leg gait by that rule and
    the lab would check it (B10).
  - **4 legs (lizards, toads walking, mammals):** lizards and monitors trot with diagonal pairs while the back bends
    from side to side (the spine does much of the stride); mammals walk with each foot in turn (left hind, left fore,
    right hind, right fore) and trot or gallop faster; the dwarf mongoose runs in a bounding gallop.
  - **8 legs (spiders):** alternating sets of four, like a double tripod.
  - **Many legs (the centipede):** a wave of steps running along the body. In centipedes the wave runs from the head
    backward, and fast ones throw the body into side-to-side waves (Manton's studies); millipedes the other way. The
    leg count is just a number.
  - **2 legs:** birds walk or hop (many ground birds, like the flicker, hop); the ground pangolin's slow upright walk.
- **Why:** realism (every gait from the real animal's rule) and speed of making (a new body gets its walk, slow walk,
  fast walk and turns for free, each with the stride note so its feet never slide).
- **Size:** medium. **Depends on:** B1.

### B3. The spine: "follow the leader" for any chain of segments
- **What:** the waist bend already works by distance walked: the gaster lies the way the front lay over the stretch of
  path it covers (`AbstractAntEntity.tickWaist`, built in the first review). Make it general: **each segment of a
  spine takes the heading the path had at its own distance behind the head.** A centipede's 21 rings, a monitor's back
  and long tail, a skink's tail, an ant's waist, a termite's abdomen and a pangolin's tail all then bend exactly along
  the ground they walked over, round corners, over edges and up walls, at any speed. A spring term on top gives the
  tail its real swing (a monitor's tail swings with each stride).
- **Why:** this is the single most visible thing for long bodies: a stiff centipede or lizard looks like a plank.
  It reuses what already works (the path memory, the edge rules) instead of a new system per creature.
- **Size:** medium. **Depends on:** B1 (the plan names the spine chain). The edge-over code (`EdgeOver`) uses the
  worker's foot distances (`FRONT_FEET`, `HIND_FEET`); those would come from the plan too.

### B4. Sizes per ant, done the real way
- **What:** each Matabele ant gets its own size (the plan: one model, scaled). The stride already scales with the
  drawn size (`getDrawScale` in the walk player), so feet stay planted at any size. Two more real things:
  - **Proportions change with size (allometry):** in many polymorphic ants the bigger workers have relatively bigger
    heads and jaws (to check for *Megaponera* in Frank's measurements). A per-ant "head share" that scales the head
    and jaw bones a little more than the body would show majors and minors as two real castes, not one ant zoomed.
  - **Bigger ants step slower at the same speed**, because each stride is longer (already true through the stride
    note), and they walk a little faster overall (a species-file number).
- Also: the climbing lift and wall push (`AntRenderer.withClimbPose`) and the edge distances are per model today;
  they must scale with each ant's size.
- **Size:** small. **Depends on:** M8 step 1 (the size spread).

### B5. Lost legs on the model
- **What:** GeckoLib 4.9.3 can hide a bone and everything hanging from it (`GeoBone.setHidden`, checked in the
  library). A lost leg = a six-bit "legs present" number saved per ant and sent to the player's game; the model hides
  those bones (a stump: hide from the shin down, keep the thigh); the walk player picks the gait made for that leg set
  (B2). Clips for one or two legs lost can all be made ahead by the gait maker (6 one-leg cases, mirrored to 3; 15
  two-leg cases) in seconds. Badly hurt ants (5 legs gone) flail and are left behind: a flailing clip.
- **Why:** the user asked for it (M8 step 2) and it is real (Matabele ants come home on 4 or 5 legs).
- **Size:** small (hiding, saving) plus medium (the gaits, from B2). **Depends on:** B2, and the M2 leg bones (the plan
  says a placeholder until then; the placeholder works the same way, since the leg bone names are already standard).

### B6. Sockets: things held by, stuck on, or riding a body part
- **What:** named places on the body (a "socket": the jaws, each foot, the back) that other things attach to and
  follow every frame. Uses:
  - **Termite soldiers clamped on an ant's leg**, riding along, swinging with the leg, until pulled off (M8). The
    soldier is drawn at the leg socket's place, in a "clamped" pose (jaws shut, legs loose).
  - **Majors carrying stacks of up to ~10 dead termites** in the jaws: one baked stack shape per count at the jaw
    socket; the head held higher and the walk a little slower with a big load (as the first plan already lists the
    loaded gait as "later").
  - **Carrying a hurt nestmate:** the carried ant drawn at the carrier's jaws in the real "pupal position" (lightly
    hurt *Megaponera* tuck their legs and antennae in and keep still to be carried; Frank et al. 2017).
  - **The assassin bug's backpack of dead ants** (*Acanthaspis petax*, East Africa, sticks ant bodies on its back;
    shown to fool jumping spiders, Jackson & Pollard 2007): dead ants placed at back sockets.
  - Seeds, soil pellets and eggs today use the `carry` bone plus a render layer (`PelletLayer`); sockets make that the
    one shared way.
- **How:** GeckoLib can report a bone's place in the world after posing (the same matrices the film checks already
  read, `FilmChecks`); the rider is drawn there. Simple and cheap.
- **Size:** medium. **Depends on:** B1 (sockets named in the plan); M2 bone names for the real model.

### B7. Shared action clips ("verbs"), made from motion rules
- **What:** short actions every creature with the right parts can do, written once as rules and fitted to each body by
  the motion library: **sting** (the gaster curls forward under the body; *Megaponera* and *Pogonomyrmex* both sting),
  **bite / grip** (jaws open, lunge, close and hold), **clamp** (a termite soldier's lock-on), **head bang** (the termite
  soldiers' drumming, now hand-set), **squeak** (the Matabele's loud stridulation when disturbed, made by rubbing the
  gaster against the waist: a quick gaster pump played with the sound, in step), **pull off** (a nestmate tugging a
  clamped termite away), **lick a wound**, **carry**, **flinch**, **die** (CLAUDE.md asks every mob for `attack` and
  `death`; there are still none: a dead insect lies with its legs drawn in under it), **dig** for the burrowers
  (the aardvark and pangolin dig with front claws; the antlion larva digs its pit walking backward in a spiral and flicks
  sand with its head).
- **Why:** the fights need half of these at once; each made by hand for each creature would be slow and would drift
  apart.
- **Size:** medium for the first five, then small each. **Depends on:** B1; the reach-a-target part of the motion
  library already exists (`Leg.reach`, `reach_ankle`, used by antenna cleaning).

### B8. Two bodies in step (fights and rescues)
- **What:** a fight or a pull-off is two bodies acting on each other: the attacker's lunge must meet the target's
  flinch at the right moment and place. A small "paired clip" rule: both players start their clips on the same tick,
  the target faces the attacker, and the hit moment in the attacker's clip (a note, like the existing `stride` and
  `swap` notes) triggers the target's reaction from the live layer. Hit reactions as an added springy jolt on the hit
  part, so no two look alike.
- **Why:** without it, fights look like two animals playing clips next to each other.
- **Size:** medium. **Depends on:** B7, M8 step 2 (the fight system).

### B9. Senses and body language for every body
- **What:** `NoticeGoal` and the head look are built for "an ant looks" and "a termite freezes". Generalise to a few
  real reactions any body plan can show: **look** (neck turn within the plan's real neck reach), **freeze**, **stand
  up to watch** (the dwarf mongoose's sentinel standing upright on its hind legs), **display** (agamas' head bobs and
  push-ups), **flatten** (a horned lizard pressing flat), **roll up** (the ground pangolin rolling into a ball, armour
  out, head and belly inside), **tongue flick** (monitors, which smell with the tongue). Each is a pose or clip plus
  which sense sets it off (sight, vibration, smell), already a species-file key (`body_language.sense`).
- **Why:** these are what make the creatures read as the real animal. **Size:** small each. **Depends on:** B1.
- Note for choosing the creatures: the horned lizard and the northern flicker are American (they live with the
  harvester ants, not the Matabele); the others on the list are African savanna animals.

### B10. Checks for any body
- **What:** the lab's checks (foot slide, feet down, clipping, loop seams, snaps) assume six legs with the names
  `l1` to `r3` (the leg colours, the footfall diagram, "fewer than 3 feet down"). Read the limbs from the plan, and
  replace "at least 3 feet down" with the real rule: **the body's centre must stay over the area between the feet on
  the ground** (true for walking insects and slow walkers; a trotting or galloping mammal or a hopping bird is allowed
  moments in the air, set in the plan). This check also chooses the lost-leg gaits (B2). The film checks
  (`FilmChecks`: parts inside blocks, sudden jumps) already work for any model.
- **Why:** every new creature gets its checks for free, and the lost-leg gaits get a real test.
- **Size:** small to medium. **Depends on:** B1.

### B11. The termites onto the motion library
- **What:** the termites still walk with the old hand-set walk (`walk_legs` in `tools/termite_models.py`: feet slide,
  no stride note, the game guesses the playback speed), and have no turning, cleaning or digging clips
  (`hasWorkClips` is false for them). With B1 and B2 they get the planted-feet walks and turns in an afternoon.
- **Why:** realism now, and the Matabele raids will put termites and ants side by side, where the difference shows.
- **Size:** small once B1/B2 exist (medium without them). **Depends on:** nothing new.

### B12. Live legs (feet placed on the real blocks), near the camera only
- **What:** the first review's "big idea": the leg solver in the game, so each foot reaches for the real block under
  it (stairs, slabs, edges, the mud of a runway, a termite mound's lumps), each foot stepping when it gets too far from
  its spot, with the gait rules. Only for the few insects close to the camera; everything else keeps the baked clips.
- **Why:** the most realistic of all, and it would also make lost legs, sizes and odd bodies "just work" up close. It
  is now worth more than before because of the uneven savanna (giant trees, mounds).
- **Size:** large. **Depends on:** B1, B2; best after the M2 model.

### B13. Springy parts and breathing for soft bodies
- **What:** insects are hard (no squash, as the user decided), but the antennae's whips are springy, and the coming
  vertebrates have soft parts: a monitor's tail, a mongoose's tail, a toad's throat pumping, a bird's head bob, ears.
  One shared "spring" in the live layer (a part lags behind its parent and settles) covers them all.
- **Size:** small. **Depends on:** B1.

# Part 2a: FASTER TO MAKE

### M1. The body plan and gait maker (B1, B2) are also the biggest time saver
A new creature becomes: a model, a plan, a species file's numbers, and the motion library writes its walks, turns,
idles, looks and verbs. Listed again here only so the ranking is clear.

### M2. Fewer key frames: smooth curves instead of a key every 1/40 second
- **What:** the motion library writes a key every 1/40 to 1/80 second with straight lines between (that is why the
  file is 764 KB). GeckoLib 4.9.3 can draw smooth curves between keys (`EasingType.CATMULLROM`, found by the first
  review), so the same motion fits in far fewer keys; the motion library would pick the fewest keys that stay within a
  small error of the true motion (it already simplifies straight-line runs, `simplify` in `ant_motion.py`). The lab and
  the pose viewer must learn the curve first, so what they show is still what the game plays.
- **Why:** smaller files, faster loading, and **faster playing** (GeckoLib searches the keys from the start every
  frame: fewer keys, less searching). It matters more once every creature and every lost-leg gait has its own clips.
- **Size:** small to medium. **Depends on:** nothing.

### M3. Motion rules instead of hand-written clip code
- **What:** today each clip is its own Python function (antenna cleaning alone is about 130 lines in
  `ant_models.py`, written for the ant). A small set of building blocks (plant the feet; reach a limb to a target;
  look at a point; hold a pose; blend two poses; a rhythm of waves) and clips as short recipes of them, with numbers
  in a table, would let one recipe serve every body (cleaning an antenna and a spider cleaning its palps are the same
  recipe on different limbs).
- **Size:** medium. **Depends on:** B1.

### M4. Faster checks
- **What:** the lab's full check takes 38 seconds for 17 clips. Check the clips side by side (the PC has 20 threads:
  about 5 seconds), and only re-check clips whose numbers changed (remember a fingerprint of each). With many creatures
  and lost-leg gaits (dozens of clips each) this keeps the check under a minute.
- **Size:** small. **Depends on:** nothing.

### M5. Films without relaunching the game
- **What:** each `runFilm` starts the whole game (about a minute before the first picture). GeckoLib reloads animation
  files when the game reloads its resources, so a film session could stay open: change a clip, the camera reloads the
  files and shoots the scene again. Also: a ready-made scene per creature (stand, walk past, turn, climb, every clip),
  written from its plan, so no scene is typed by hand.
- **Size:** small to medium. **Depends on:** nothing.

### M6. Blockbench both ways, safely
- **What:** `tools/bbmodel_import.py` overwrites both Blockbench projects (a known issue). Make it ask, keep the user's
  hand edits, and add the way back: a clip the user animates in Blockbench (which has its own IK with null objects)
  goes into the game and through the lab's checks automatically, and clips the motion library made can be opened in
  Blockbench to tweak. For the M2 model: a small checker that reads the user's Blockbench model and says which bones
  the body plan needs are missing or misnamed before anything is generated.
- **Size:** medium. **Depends on:** M2 (the user's model).

### M7. One clip set for many sizes and kin
- **What:** clips made in body lengths, not blocks, so one set serves minors and majors (already true for the stride);
  for proportions that change with size (B4), the motion library re-solves the legs for two or three size classes
  automatically. Ants of different species with the same body plan share the clip recipes with their own numbers.
- **Size:** small. **Depends on:** B1, B4.

# Part 2b: FASTER TO RUN

What the measurements say: the server is cheap (0.004 to 0.02 ms an insect); **drawing is the cost**, split between
working out poses from the clips (40%) and working out every box corner on the main processor (45%). A few hundred
fully animated insects fill a frame on the user's fast PC. A raid column of 500 plus the termites can't be drawn the
way the 60 real mobs are; the drawn-only ants need their own cheap way, and the real mobs can be made cheaper too.

### F1. A frame-time meter (do this first)
- **What:** the film camera writes the frame time (and the time spent drawing insects) to its log, and a stress scene
  in `tools/film/` (like the one used for this review: 100, 400, 1,000 ants) measures it. In a normal game, the same
  numbers on `/antcolonies perf` (client side).
- **Why:** every speed idea below needs a before-and-after number; today there is none.
- **Size:** small. **Depends on:** nothing.

### F2. Quick wins on the real mobs (no change in look)
- **Each player reads only its own bones' clips.** Split each clip into its body part and its head part when the
  file is written (the first review's item 12), so no player throws bones away each frame: about **9%** of the drawing
  time measured, plus less key searching. Small.
- **Fewer key frames** (M2): less searching each frame. Small to medium.
- **No slanted boxes:** bake each box's own angle into its corners when the model is written, or model legs as boxes
  straight in their bone (the bone does the slant): about **10%** measured (`rotateMatrixAroundCube`). Small, but it
  touches the model files (for M2, a rule for the user: give leg boxes no angle of their own, slant the bone instead).
- **Skip GeckoLib's flat-box fix** for models with no flat boxes (an override in our renderer): about **10%**
  measured. Small.
- **Fewer boxes:** 63 boxes for a body 24 pixels long is generous; a "mid distance" model (F4) helps more.
- Together these could plausibly take a third or more off the drawing cost of each real mob (to measure with F1).

### F3. The crowd drawer for drawn-only ants (M8 step 3)
- **What:** drawn-only ants are not creatures at all: the server sends each column's route and timetable once, and the
  player's game works out each ant's place from the clock, and draws it with its own simple drawer, not GeckoLib. No
  server cost, no network per ant.
- **Pose from distance walked:** each ant's leg pose is just "how far it has walked divided by its stride", plus its
  own offset so no two step together (the stride note already holds the rule). Feet stay planted for free, with no
  animation player.
- **Baked poses:** for each clip, the model's corners worked out ahead at 16 to 32 moments of the cycle, kept as ready
  shapes; drawing an ant = one ready shape moved, turned and scaled into place. This removes both measured costs (the
  40% of pose working-out and almost all of the 45% of bone maths) for these ants. Each body part baked separately
  (body, each leg) makes **lost legs free** (skip the part) and keeps **sizes free** (the scale goes in the move).
  Carried stacks, a clamped termite: one more baked part at the socket's baked place.
- **No fights in the crowd:** fights only happen with real mobs near the player (as the real ones are the only ones
  the player can see closely); far fights stay numbers. So the crowd drawer needs only walking, carrying and standing.
- **Size:** large. **Depends on:** M8 step 3 (drawn-only ants), F1, ideally F4.

### F4. Level of detail by distance
- **What:** three tiers, chosen per insect each frame by distance and the "most drawn insects" setting:
  - **Near (up to about 16 blocks):** real mobs, full GeckoLib model, live layer, little things.
  - **Middle (about 16 to 40):** baked poses (F3) with a simpler model (about 15 boxes: one box per leg segment, no
    jaws, no antenna elbows), fewer moments per cycle. A real mob this far can be drawn this way too.
  - **Far (40 and beyond):** 3 boxes or a flat picture, no legs; beyond the limit, nothing. At 64 blocks a worker is
    about 6 pixels long, so this looks the same.
- Also fix the vanishing at 17 blocks (the measured rule above): a drawn rule of our own, so a raid column is seen
  as far as its far tier reaches.
- **Why:** this is what lets thousands be on screen: most of a column is far away.
- **Size:** medium. **Depends on:** F3 for the middle and far tiers; the M2 model for the simpler mid model (it can be
  made automatically from the full one: merge the boxes of each part).

### F5. The "most drawn insects" setting, and a budget that adjusts itself
- **What:** a client setting (NeoForge client config: a file and a settings screen) with the most drawn insects, the
  near and middle distances, and "little things on/off". Better still, a budget: the game watches its frame time (F1)
  and moves the tier distances in or out to keep the frame rate, so a weak PC draws fewer at full detail without the
  player choosing numbers.
- **Size:** small (setting), medium (self-adjusting). **Depends on:** F1, F4.

### F6. Culling for the crowd
- **What:** the crowd drawer skips ants outside the view (the game's own view test), behind hills, and underground
  (an ant inside a gallery can't be seen unless the player is there; the hidden trips already do this for termites).
  Real mobs already get the game's view test.
- **Size:** small (part of F3). **Depends on:** F3.

### F7. Drawing many copies in one go (instancing), only if F3 to F6 are not enough
- **What:** handing the graphics card the baked shape once and the 2,000 places separately. Minecraft 1.21.1 has no
  ready way for mods to do this; it would need our own graphics code, which can clash with shader packs (Iris) and
  speed mods (Sodium). The baked-pose drawer already puts all ants of one look into one batch the normal way, so I
  would try F3 to F6 first and measure (F1).
- **Size:** large, with compatibility risk. **Depends on:** F3, F1.

### F8. Shared clocks: not needed
- Sharing one animation clock among many ants saves almost nothing once poses come from distance walked (F3), and it
  would put them in step, which the engine avoids on purpose for realism. So: no.

# My suggested top picks and order

1. **F1, the frame-time meter and a stress scene** (small): every later speed step can then show its gain.
2. **F2, the quick wins on the real mobs** (small each): split clips per player, skip the flat-box fix, no slanted
   boxes, then fewer key frames (M2). Measured: about 30% of today's drawing time is in the parts these remove.
3. **B1 + B2, body plans and the gait maker** (medium): the base for everything new. Settle the plan's names together
   with the user's M2 bone names (ask the user how far the Blockbench worker is first).
4. **B5 + B10 + B4, lost legs, the balance check and sizes** (small to medium): exactly what M8 steps 1 and 2 need.
5. **B6 + B7 + B8, sockets, verbs and paired clips** (medium): clamping, carrying stacks, stings, squeaks, rescue,
   death; for M8 step 2 and 5.
6. **F3 + F4 + F5 + F6, the crowd drawer with distance tiers and the setting** (large): for M8 step 3, the drawn-only
   column.
7. **B3, the spine chain** (medium) and **B9, senses and body language for every body** (small each): when the first
   long-bodied or 4-legged creature is chosen.
8. **B11, termites onto the motion library** (small once 3 is done).
9. Later: **M3 to M7** (tools) as the clip count grows, **B12 live legs** (large, after M2), **B13 springy parts**
   with the vertebrates, **F7 instancing** only if measured necessary.

## How the measurement was made (to repeat it)
- A scene file (not in the repo) summoned 400 `worker_ant` with `NoAI` and the name `film:walk` in a 20 × 20 grid
  (0.6 blocks apart) in front of a camera 9 blocks away, lens 70, waited 30 s, then 60 s.
- JFR was started on the running game with `jcmd <pid> JFR.start settings=profile` and dumped with `JFR.dump`; the
  samples on the render thread were counted by the methods in their stack (who calls whom). The shares are of the
  render thread's working time; a sampling profiler is approximate (a few percent).
- The server numbers are the mod's own `[perf]` lines in the log.

## Sources
(From my reading; the links were not opened in this session, so check each before a number from it goes into the
game.)
- Hughes 1957, cockroaches walking after leg loss, *Journal of Experimental Biology* 34.
- Grabowska et al. 2012, stick insects walking on four legs (diagonal pairs), *Journal of Experimental Biology* 215.
- Frank et al. 2017, "Saving the injured: rescue behavior in the termite-hunting ant *Megaponera analis*",
  *Science Advances* 3: e1602187 (injured ants keep still with legs tucked to be carried; walking after leg loss).
- Manton (1950s to 1977), the leg waves and body waves of centipedes and millipedes.
- Jackson & Pollard 2007, *Acanthaspis petax*, the assassin bug that wears dead ants, *Journal of Natural History*.
- Ground pangolin walking on its hind legs, rolling into a ball: Kingdon, *The Kingdon Field Guide to African Mammals*.
- GeckoLib 4.9.3 (the library itself, read with Java's `javap`): `GeoBone.setHidden`, `EasingType.CATMULLROM`,
  `AnimationController.getCurrentKeyFrameLocation`, `RenderUtil.fixInvertedFlatCube`, `rotateMatrixAroundCube`.
- Minecraft's draw distance rule: `Entity.shouldRenderAtSqrDistance` (64 × the box's average side × the entity
  distance setting).
