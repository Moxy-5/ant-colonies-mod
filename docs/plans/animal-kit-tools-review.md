# The animal kit (M9.4): the tools review (2026-11-08)

Asked for by the user before more animals are added: "think of every way to improve the speed and quality of your work
in the model viewer and animation engine for 9.4 ... really dig deep, look at the code first". This is a review only:
nothing in the game or the tools was changed. Nothing here is decided; the user picks (see "Your calls" at the end).

It builds on the last review (`docs/plans/animation-engine-upgrade-review.md`, 2026-10-18). Its ideas M1 to M5, B1 to
B6 and B11 are built (guard films, films without relaunching, body plans and the gait maker, climbing for every body,
faster lab checks, lost legs, sockets, termite walks). Its open ideas are folded in here where they fit (each says so),
not repeated.

**The user's priority (memory, 2026-10-18, kept):** making models and clips faster comes first, never at the cost of
quality; the game's running speed second. So Parts 1 and 2 are making speed, Part 3 gets the game ready for animals,
Part 4 is realism per body family, Part 5 is running speed.

## The short version
**What I found, in four lines:**
1. **The tools run fast but the rounds are slow.** The Matabele model and its viewer page are made in under half a
   second; the Matabele look took about 15 rounds in one day (2026-10-23), nearly all on the head and jaws, each round
   the user describing a change in words and me guessing at it. The time is in the rounds, not the tools.
2. **One clip changed means every clip remade.** The Matabele clip maker remakes all three models' clips every time,
   one after another on one processor thread: **about 21 minutes** for a run (measured below). A small change to one clip
   waits for all of it.
3. **The motion library only knows insect legs.** A leg must be exactly three bones (hip, knee, ankle); there is no
   neck, no tail, no body wave, no running with all feet off the ground, and the lab's main checks only switch on for
   six legs.
4. **The game's animation code only works on colony insects.** The three animation players, the live layer and the
   renderer all need the ant class (`AbstractAntEntity`, 3,330 lines of colony insect), but use only about 15 things
   from it. An aardvark must not be an ant.

**My picks, in the order I would build them** (sizes: small = one session, medium = a few, large = a week or more of
sessions; the full order with every idea is near the end):
1. **K0 and K1, the clip maker fast** (small to medium): its three slow spots fixed (found with the profiler), then
   each finished clip kept and only changed clips remade, on many threads. A one-clip change from about 21 minutes to
   seconds, for the Matabele today and every animal after.
2. **K4 one size and speed table, G3 each model's own head bones** (small each): two traps cleared before new models
   arrive.
3. **V4, V1, V3, V2, the model viewer for looks** (small to medium): one shared 3D script that works offline, the
   photo's outline behind the model at real size, every round kept to compare, sliders for the main shape numbers.
4. **G1 and G2, an animated body for any creature** (medium, before the first animal): the animation players, live
   layer and renderer work on anything with a body plan, and an animal's clips are listed in data, not Java; nothing
   changes in-game.
5. **A1 and A2, limbs of any length and gait cards from real animals** (medium to large, the heart of the kit): legs
   of 2 to 5 parts bending the right way (a deer's knee forward, its ankle back), and each animal's walk, trot and
   gallop built from its real measured numbers, changing gait at the real speed.
6. **V5 and V6, the model kit with "outline to boxes"** (medium): part makers per family (a body built from the photo
   outline, limbs, heads, tails, wings) in one shared library in place of 13 copied picture writers, and a box budget.
7. **A3, A4, G4, spines, a steady head, the live layer for animals** (medium): the lizard's S-swing, the centipede's
   body wave, the snake following its head, the monitor's tail, a walking bird's head held still, breathing.
8. **K5, K2, K6, checks, recipes and the pose viewer for every family** (medium): feet down, balance and clipping by
   the family's own rules; everyday clips (stand, look, lie down, die, drink) written once per family.

Then smooth keys and smaller files (K3), the drawing quick wins (Part 5), the climbing copy check (K7) and guard films
the same every launch (K8); flight, hoppers and the legless mover with the first animal that needs them.

## Words used in this report
- **Bone, rig, clip, key frame, IK, gait, duty factor, live layer, LOD, baking:** as in the last review (a bone is a
  movable part; a clip is one animation stored as key frames; IK works a limb out backwards from where its foot must
  be; the duty factor is the share of a step a foot is on the ground; the live layer is what the game adds on top of
  the clips each frame; LOD draws far things simpler; baking works something out once ahead of time).
- **Cache:** a kept copy of a finished result, reused while nothing it was made from has changed.
- **Fingerprint:** a short code worked out from a thing's contents; if the contents change, so does the code. The lab
  already uses fingerprints to skip clips whose checks it has already run.
- **Thread:** one line of work a processor runs. The user's PC has 20; most of our tools use 1.
- **Interface (in code):** a list of questions an object promises to answer ("how big are you drawn, how bent is your
  spine"). Code written against the list works on any object that answers it, ant or aardvark.
- **CDN:** a website that hands out code libraries. Our viewer pages load their 3D library (three.js) from one, so
  they need the internet and break if that site changes.
- **Slider:** a bar on the page you drag to change a number and see the model change at once.
- **Loft:** a solid made by joining cross-sections along a line (how boat hulls are drawn). In boxes: a stack of boxes
  whose sizes follow the outline.
- **Segment chain:** a row of bones each hanging from the one before (a tail, a neck, a centipede's body).
- **Pole (bend direction):** the side a limb's middle joint should point to when the limb is solved (a knee forward, an
  elbow back). Without it IK may bend a knee the wrong way.
- **Froude number:** speed squared divided by (gravity times hip height). Animals of very different sizes change
  gait at about the same Froude number: walk to trot near 0.5, trot to gallop at about 2 to 3 (Alexander and Jayes
  1983). One rule gives every four-legged animal its gait changes at its real speed.
- **Inverted pendulum / spring:** walking animals vault over a stiff leg, so the body is highest in the middle of a
  step; running animals bounce on a springy leg, so the body is lowest in the middle of a step (Cavagna, Heglund and
  Taylor 1977). Today's bob is the insect's.
- **Lateral-sequence walk:** the four-legged walk of nearly all mammals and lizards: a hind foot, then the front foot
  on the same side, then the other hind, then the other front (Hildebrand 1965).
- **Metachronal wave:** a wave of steps running along a row of legs. In centipedes it runs from front to back
  (retrograde), in millipedes from back to front (direct) (Manton 1952 onward).
- **Digitigrade / unguligrade:** walking on the toes (dogs, hyenas, birds) or the tips of hoofs (antelopes, giraffes).
  The "backward knee" of a deer's hind leg is its ankle: the leg has four parts, not three.
- **Catmull-Rom curve:** a smooth curve through a row of points. GeckoLib can draw one between key frames, so the same
  motion needs far fewer keys than straight lines between them.

## What I read and what I measured (2026-11-08)
**Read:** the model viewer (`tools/model_viewer.py`, its 14 page templates in `tools/`), the model makers
(`tools/matabele_models.py`, `ant_models.py`, `termite_models.py`), the motion library (`tools/ant_motion.py`), the
Matabele clip maker (`tools/matabele_anims.py`), the anim lab (`tools/anim_lab.py`), the pose viewer
(`tools/ant_pose_viewer.py`), the body plans (`tools/body_plan.py`, `body_plans/*.json`), the climbing check
(`tools/matabele_climb.py`), the guard films (`tools/guard_films.py`), and the game's side: the animation players
(`entity/body/AntAnimationController`), the live layer (`client/BodyLanguage`), the renderer (`client/AntRenderer`,
`InsectModel`), the body plans (`entity/body/BodyPlans`), the spine and edge code, and how the insect picks its clips
(`AbstractAntEntity`).

**Measured:**
| What | Time or size |
|---|---|
| Make the three Matabele models (`matabele_models.py`) | 0.46 s |
| Build a model viewer page (`model_viewer.py matabele`, `sizes`) | 0.33 s, 0.22 s |
| Make every Matabele clip (`matabele_anims.py`, 3 models) | **about 21 minutes** (1,294 s), one thread, every clip every time |
| Of that, the major by part | resting clips (naps, cleaning) **320 s**, walks 64 s, rescue 27 s, gland dab 26 s, dying 10 s, clamped clips 7 s, the rest under 1 s: about 7.5 minutes |
| Lab check of the major's clips, from nothing / unchanged | 33 s / 0.4 s (its cache works; 0 problems) |
| Build the Matabele pose viewer page (`ant_pose_viewer.py matabele`) | 24 s |
| The Matabele climbing check (`matabele_climb.py`) | 111 s |
| Body plan check (`body_plan.py check`) | 0.6 s |
| Matabele major's clip file | **2.96 MB, 49 clips, 43,876 key frames** (minor 2.82 MB; the harvester worker had 764 KB) |
| Biggest clips | the gland dab 2,923 keys, cleaning both antennae 2,081, twisting at a clamped termite 1,995 |
| Boxes per model | Matabele major **227**, termites 65 to 86, harvester worker 63 |
| Copies of shared helpers in `tools/` | a picture (PNG) writer in **13** files, a box maker in 6, smoothing in 4 |
| Clip names written into the game's Java code | 34, in `AbstractAntEntity`, `MatabeleEntity`, `TermiteSoldierEntity` |

**Where the Matabele's making time went (from the notes):** the model took about 15 rounds on 2026-10-23 (the
status lines from "round 5" to "the model approved"): a body slimmed, the head tilted (three ways), the head remade
from nothing (five heads), the body made plain black, four jaws, four ways to hang them, the jaws remade twice, raised,
picked. Each round: the user describes the change in words, I guess at it in Python, the page is rebuilt, the user
looks. The clips then came the same day as drafts, and the climbing picks the day after. The lesson of the last
review still holds (most of the time is rounds and bugs, not the tools running); what is new is that the clip maker
itself has grown slow as the Matabele gained lost-leg walks, clamped walks, attacks and rescues.

# Part 1: FASTER TO MAKE, the clips and checks

### K0. The clip maker's three slow spots (new, found by timing it)
Timed part by part on the major: its 26 walks take 64 to 72 s, and its 6 resting clips (two naps, cleaning each
antenna and both, cleaning a nestmate) **320 s**, though they are short; the cleaning clips solve a front leg reaching
to the jaws at every key through the same solver. Python's profiler on the walks (the lost-leg gait search itself takes
0.08 s, so it is not the problem):
1. **The centre of weight, 19 s a model** (`anim_lab.Model.weights`): every box is filled with 216 test points and
   each point is tested against every box before it, so the time grows with the square of the box count. The
   Matabele's 227 boxes pay it once a model each run, in the clip maker and again in every lab check; an animal with
   300 boxes would pay about twice as much. Fix: a quick "is it even near" test before the exact one, and the answer
   kept on disk by the model's fingerprint (it only changes when the model does).
2. **The leg solver redoing the whole body, about 25 s of the 34 s of solving** (`ant_motion.Leg.points` calling
   `anim_lab.Model.world`): each of the search's 277,440 tries works out every bone's place from scratch, though only
   the leg moves and the body is the same for the whole search. Fix: work out the hip's place once per key (and with
   A1, two-bone limbs need no search at all).
3. **The antennae trailing, 16 s** (`matabele_anims.trail_antennae`, `ground_angle`): a search for the antenna angle
   that just clears the ground, 40 tries per point, each placing the whole model. Fix: the same as 2, and a
   direct answer where the antenna is a straight line.
- **Why:** these three are most of the walks' time and the same code makes every clip of every animal. My guess: the
  walks from 72 s to about 10 to 15 s, before K1's cache. **Quality kept:** the clips must come out the same (a test
  compares the old and new files number by number). **Size:** small. **Depends on:** nothing.

### K1. Clips remade only when they change, on every thread (new)
- **What today:** `python tools/matabele_anims.py` remakes all 111 Matabele clips (major, minor, queen) every run,
  one after another on one thread, and rewrites all three model files too (about 21 minutes; the major alone about 7.5
  minutes, 70 percent of it the 6 resting clips).
  The major and the minor each have 26 walks (3 gears, 21 with a leg or two lost, the turns) and 6 dragging a clamped
  termite.
- **The idea:** each clip gets a fingerprint of everything it is made from (its recipe's code, its numbers, the model,
  the body plan, the motion library's version); a finished clip is kept in a cache folder and reused while its
  fingerprint is the same. Clips that must be made are made side by side on half the PC's threads (the lab's rule).
  `--fresh` remakes everything, as the lab has.
- **Why:** a one-clip change goes from about 21 minutes to seconds, and every look-fix-look round shrinks with it. With
  many animals each with 20 to 50 clips this matters more, not less.
- **Quality kept:** a cached clip is byte for byte the clip that would be made; a test makes everything fresh once and
  compares it with the cached run (it must match exactly).
- **Size:** small to medium. **Depends on:** nothing. **The same for every clip maker** (`ant_models.py`,
  `termite_models.py`, and every animal's): the cache lives in the shared library, not in each maker.

### K2. Shared clip recipes per family (the last review's M6 and B7, still open)
- **What today:** idle, death, cleaning the antennae, napping, carrying, the gland dab and the attacks are written
  again in each species' clip file (`ant_models.py`, `termite_models.py`, `matabele_anims.py`); the Matabele maker has
  a general tool for "a posed clip" (`posed_clip`: a pose over time, the body lifted so nothing goes through the
  ground, feet planted) that belongs to every body.
- **The idea:** move the general tools (`posed_clip`, `still`, `keys`, `curve`, `lowest_body`, `trail_antennae`) into
  the motion library, then write the actions once per family as recipes with numbers: "stand and breathe", "look
  round", "lie down", "die" (each family its own real way: a spider's legs curl in as the blood pressure that
  stretches them is lost; a mammal slumps to its side), "scratch", "shake", "eat from the ground", "drink". A new
  animal lists the recipes it uses and its numbers.
- **Why:** an animal's first 10 to 15 clips come from the kit in minutes; only its special actions are written by
  hand. **Quality kept:** each recipe is checked once by the lab on every family it serves.
- **Size:** medium. **Depends on:** A1 (limbs of any length) for the four-legged recipes.

### K3. Smooth curves, fewer keys (the last review's M7, still open)
- **What today:** the motion library writes a key every 1/40 to 1/80 second with straight lines between, then drops
  keys that a straight line would put back within 0.02 (`Track.simplify`). The Matabele major's file: 43,876 keys,
  2.96 MB. GeckoLib finds "where in the clip are we" by walking from the first key every frame, so more keys cost
  drawing time too (measured in the last review).
- **The idea:** fit Catmull-Rom curves instead (GeckoLib draws them, `"lerp_mode": "catmullrom"`; the lab already
  reads them), keeping the fewest keys that stay within a set accuracy (for example 0.2 degrees on every joint and 0.05
  model units on every foot). My guess: a third to a fifth of today's keys.
- **Why:** smaller files, faster to write and load, a little cheaper to draw. **Quality kept:** the accuracy limit,
  and the lab's foot slide and clipping checks run on the curved clip, exactly as the game plays it.
- **Size:** small to medium. **Depends on:** nothing.

### K4. One size and speed table (new)
- **What today:** a model's drawn size lives in `species/InsectSizes` (the game), `RENDER_SCALE` in `anim_lab.py` (the
  lab, with a comment saying it must match), `MATABELE_*_SCALE` in `SavannaClientEvents`, and the walk speeds in the
  lab's `WALK_SPEED`; the pose viewer and the Matabele clip maker read the lab's copy. Four places, kept equal by hand,
  and the lab's notes beside them are already out of date (they still call the Matabele's size the user's to pick,
  from before the real sizes of 2026-11-05).
- **The idea:** each model's real length, drawn scale and walk speeds in one table that the game, the lab, the clip
  makers and the viewers all read (the body plan file is the natural home: every tool and the game already read it). A
  check fails the build if a model has no entry.
- **Why:** a new animal needs its size written once; a wrong size can no longer make the lab check feet at the wrong
  speed. **Size:** small. **Depends on:** nothing.

### K5. Lab checks for every family (the last review's B10, part built)
- **What today:** in `anim_lab.py`'s `check()`, the "fewest feet on the ground" rule only runs when the body has
  exactly 6 legs; balance (is the body's weight over its feet) is only a note, never a failure; the clipping check
  tests legs against the body, not a tail against the legs or a neck against the chest; the words in its messages are
  the ant's.
- **The idea:** the body plan names the family, and each family brings its rules: the fewest feet down per gait (a
  walking four-legged animal always has 2 or 3 down; a trot 2; a gallop and a hop may have none, a flight phase, but
  only for a set share of the cycle); balance a real failure for slow walks and standing (real slow walkers keep their
  weight inside their feet) and only a note for trots, gallops and hops (they balance by moving); every bone checked
  against every other it must not pass through (tail through hind legs, head through front legs, wings through the
  body), from the plan's list of parts.
- **Why:** the machine catches the bugs before the user's eye does, for every animal. **Size:** medium. **Depends
  on:** A1, A2 (gaits with flight phases).

### K6. The pose viewer for any model (new)
- **What today:** `ant_pose_viewer.py` has a hand-written list of models and a special Matabele mode; it shows clips
  side by side and can be opened at an exact frame by its address.
- **The idea:** it reads any model with a body plan (no list to edit), shows the model at its real size beside a
  person-sized marker and a block grid, plays walk gears at their real speeds with a speed slider (the legs keep pace,
  so gear changes can be watched), shows two clips side by side or one over the other, and plays the user's reference
  video or picture strip of the real animal next to the clip.
- **Why:** an animal's motion is judged against the real animal, not against memory. **Size:** small to medium.
  **Depends on:** K4.

### K7. The climbing copy checked against the game (new)
- **What today:** `tools/matabele_climb.py` does the game's climbing maths again in Python (the edge, the spine, the
  wall pose) to check the Matabele climbing without the game. Nothing compares it with the Java, so the two can drift
  apart unseen.
- **The idea:** a test feeds the same few paths to both (the Java `SpineTest` already runs the game's spine on test
  paths) and fails if their poses differ. Only for families that climb (spiders, scorpions, centipedes, geckos);
  four-legged mammals and snakes do not climb walls.
- **Size:** small. **Depends on:** nothing.

### K8. Guard films the same on every launch (`docs/BUGS.md` 137, 170)
- **What today:** some Matabele and crowd guard films change from launch to launch, so a real change can hide in the
  noise and every run needs a human look.
- **The idea:** find what still varies (the crowd's start, the Matabele's own random size, a timing race) and pin it,
  as was done for the climb film. Then add one guard scene per family (stand, walk, trot, run, a gear change, a turn, a
  slope) written from the body plan.
- **Size:** medium. **Depends on:** G1 (animals in the films).

# Part 2: FASTER TO MAKE, the models and looks

### V1. The photo outline behind the model (from the M9.4 ideas in `docs/DESIGN.md`)
- **What today:** the user's photos are looked at by me and turned into numbers by eye; the user compares the page
  with the photo in their head.
- **The idea:** the user gives a side photo and a top (or front) photo of the real animal; I trace the outline once
  (a list of points, kept in `references/outlines/<animal>.json`, with the animal's real length so it is drawn at
  scale); the viewer draws it as a thin line or a faded picture behind the model, in side, top and front views, and
  shows a number for how well the model's outline fills the photo's (the share of the photo's outline inside the
  model's, and of the model's inside the photo's).
- **Why:** "the head is too long" becomes something both of us see at once, and I can check the outline myself before
  showing the user. The Matabele's 15 rounds were mostly shape (head, jaws); this is the fix for that.
- **Size:** small to medium. **Depends on:** nothing.

### V2. Sliders for the main shape numbers (new)
- **What today:** every change is a Python edit and a rebuilt page; a "bit more" means another round.
- **The idea:** each model maker marks its main shape numbers (the Matabele's head tilt, jaw length and curve, leg
  taper, body slimness; an animal's neck length, leg length, snout length, tail length); the viewer page shows a slider
  for each and rebuilds the model in the page as it moves (the maker's shape rules written once in the page's own
  script, or the page asking a small local helper to remake the model). The user drags, reads off the numbers, and
  tells me "head tilt 27, jaw curve 0.6"; I write them into the maker.
- **Why:** many rounds become one sitting; "a bit more" is the user's hand, not my guess. **Quality kept:** the
  numbers go back into the maker, so the game's model is still made the same way and checked the same way.
- **Size:** medium (the simplest form, a page that asks a tiny local helper to rerun the maker, is small).
  **Depends on:** nothing.

### V3. Every round kept, side by side (new)
- **What today:** each round overwrites the last; going back to "the jaws of round 3" means me rebuilding them.
- **The idea:** each viewer build keeps a numbered snapshot of the model (and its numbers); the page has a "rounds"
  strip to show any two side by side or flip between them. A pick names a round ("round 7's jaws with round 9's
  head").
- **Size:** small. **Depends on:** nothing.

### V4. One shared 3D page script, working offline (new)
- **What today:** the 3D code that builds a model from its file (`setRot`, `boxGeometry`, `buildModel`,
  `loadTexture`) is copied into the Matabele, sizes, march and nest-site templates, and the march and nest-site copies
  are already a different version; the pose viewer has its own (`buildAnt`). Every page loads three.js r128 from a
  CDN, so the pages need the internet. Each scene's Python repeats the read-template, replace, write steps, and the
  reference-model loading is copied between the Matabele and queen scenes.
- **The idea:** one `tools/viewer_common.js` with the model builder, ground, real-size grid, lights, the outline
  backdrop (V1), sliders (V2) and rounds (V3), put into each page when it is built; a copy of three.js kept in the
  project and put in the page too (pages then open with no internet); one Python helper for "build a page from a
  template and its data".
- **Why:** every new viewer is a few lines; a fix to how models are drawn reaches every viewer; the pages always open.
- **Size:** small to medium. **Depends on:** nothing (best done first of V1 to V3, which then live in the shared file).

### V5. The model kit: part makers per family and "outline to boxes" (from the M9.4 ideas)
- **What today:** each model maker is its own file with its own copies of the box, bone, smoothing and picture
  helpers (13 picture writers in `tools/`, 6 box makers); the Matabele maker alone is 794 lines, about half of it its
  own head and jaws.
- **The idea:** one model kit library with the shared helpers and part makers:
  - **a body loft:** given the side and top outlines (V1's tracing), it makes the body as a row of boxes whose heights
    and widths follow the outline, rounded with the Matabele maker's `rounded_slice` (already written): a hippo, a
    monitor or a centipede's body made from its photo;
  - **limbs** of 2 to 4 parts with their real proportions (thigh, shin, foot, toes), splayed out (lizards) or under the
    body (mammals);
  - **heads** (snout, jaws, ears, horns, eyes), **tails** and **necks** as segment chains, **wings** (folded and open);
  - the body plan written from the parts as they are made (no plan to write by hand).
  An animal's maker then lists its parts and numbers and adds only its own details.
- **Why:** a new animal's first model in minutes, close to the photo from the start, so the rounds start near the
  end. **Quality kept:** the parts are only a start: any animal's maker can still shape each part by hand, as the
  Matabele's head was.
- **Size:** medium. **Depends on:** V1 (outlines); best with V4.

### V6. A box budget and a draw cost per model (new; the last review's F2 and F4 for models)
- **What today:** the Matabele major has 227 boxes, 3.6 times the harvester worker; nothing tells me a model has grown
  costly while I add detail. The last review measured the costs: slanted boxes about 10 percent of the drawing,
  GeckoLib's flat-box fix about 10 percent.
- **The idea:** `body_plan.py check` reports each model's boxes, slanted boxes and bones against a budget per family
  and size (an animal a block or more long is seen up close and may earn more boxes than an ant; a herd of 10 should
  cost no more than 50 ants); the model kit writes leg boxes straight in their bone (the bone does the slant) where it
  can.
- **Size:** small. **Depends on:** nothing.

### V7. Blockbench both ways (the last review's M8, still open)
- Only if the user wants to touch models or poses by hand in Blockbench; otherwise it can wait. Today
  `tools/bbmodel_import.py` overwrites both Blockbench projects. **Size:** medium. **Depends on:** the user's call.

# Part 3: THE GAME, READY FOR ANIMALS

### G1. An "animated body" for any creature (new, before the first animal)
- **What today:** `AntAnimationController<T extends AbstractAntEntity>`, `BodyLanguage`, `AntRenderer` and
  `InsectModel` are written for the ant class, which carries a colony, a brain, legs on walls and 3,330 lines of
  insect life. Read in the code, they use only: the drawn scale, the look weight, the spine's bends, the place, the
  box size, the wall pose (tilt, heading, wall yaw, edge shift, climbing lift), the slope tilt, the lost legs and the
  body plan.
- **The idea:** those questions become one interface (my working name `AnimatedBody`), answered by the ant class as
  today and by a new, light animal class; the players, the live layer and the renderer are written against the
  interface. Climbing and the edge stay with the bodies that climb.
- **Why:** an animal gets the stride-matched walks, the gear changes in step, the head look and the spine bend for
  free, without being an ant. **Quality kept:** nothing changes in-game for insects (guard films 0 changed pictures).
- **Size:** medium. **Depends on:** nothing.

### G2. The clips an animal plays, from data (new)
- **What today:** which clip plays is decided in `AbstractAntEntity` (`usualClip`, `activityClip`, the noticing,
  clamped, lost-leg and strike clips; 34 clip names in Java), and the walk gears are a fixed list of three names
  (`walk_slow`, `walk`, `walk_fast`).
- **The idea:** a small shared clip picker for animals: stand, the walk gears, turn, and the animal's specials, with
  the gear list read from the clip file itself (every clip with a `speeds` note is a gear, any number of them: walk,
  trot, canter, gallop). The gear change already happens at the speed band's edge and in step (the swap note); between
  unlike gaits (walk to trot) there is no matching moment, so it blends over a fifth of a second, as real animals take
  one changing stride.
- **Why:** a new animal lists its clips; no Java per animal for the usual moves. **Size:** medium. **Depends on:** G1.

### G3. Each model's own head-player bones (new, a small fix)
- **What today:** `BodyPlans` keeps one set of "head player" bone names for all models together (`HEAD_PLAYER`). A
  bone name one plan gives to the head player is the head player's in every model. With animals ("neck", "ear",
  "jaw", "tail") two plans meaning different things by the same name becomes likely, and the wrong player would move
  the bone, or two would fight over it.
- **The idea:** each player asks its own model's plan. **Size:** small. **Depends on:** nothing.

### G4. The live layer for animals (new; the last review's B9, B12, B13 in part)
- **The head look** today is limited to 40 degrees sideways and 25 up or down for every body; the limits come from
  the plan instead (an owl far more, a monitor less, a hippo very little).
- **The tail follows:** a tail is a spine piece behind the body, so the spine bend already built (`entity/body/Spine`,
  by distance walked) swings it round turns; with a spring on top it swings with each step and settles when the
  animal stops (the last review's B13).
- **A steady head** (A4 below), **breathing** (the chest rising slowly; standing still is most of an animal's time),
  **ear flicks** and **a monitor's tongue flick** as small live pieces chosen by the plan.
- **Size:** medium, piece by piece. **Depends on:** G1.

# Part 4: REAL MOVEMENT, FAMILY BY FAMILY

What each body family really does, and what the kit needs for it. Families from `docs/DESIGN.md` ("The animal kit").

| Family | What the real animals do | What the kit needs |
|---|---|---|
| Six legs (beetles, wasps on foot) | the tripod gait, slower walkers toward a wave gait (Wilson 1966); a dung beetle rolls its ball walking backwards on its front legs, head down, hind legs on the ball, and climbs on top to "dance" and take its bearings (Dacke and others) | done; the ball roll is one special clip with a socket for the ball |
| Eight legs (spiders, scorpions, sun spiders) | two groups of four step by turns (L1 R2 L3 R4, then the others); spiders stretch their legs by blood pressure, not muscle, so a dying spider's legs curl in; a scorpion holds its tail arched over its back and its claws forward | the gait maker already makes 8-leg walks (`ant_motion.py` self-test); legs of 4 bones (A1); a tail chain held as a pose (A3) |
| Many legs (centipedes, millipedes) | waves of steps run along the legs: front to back in centipedes, back to front in millipedes, whose legs of a pair step together; fast centipedes swing their body side to side, the legs riding the wave (Anderson, Shultz and Jayne 1995) | the gait maker's wave direction as a number per animal (A2); a long spine that waves and follows its path (A3) |
| Sprawling four legs (monitors, crocodiles, tortoises) | the lateral-sequence walk slow, a trot faster; the trunk swings side to side in an S with each step, lengthening it; the tail swings the other way; a monitor flicks its forked tongue as it walks; small crocodiles can gallop | limbs out to the side (A1), the trunk wave tied to the steps (A3), the tail following (G4), the tongue flick (G4) |
| Upright four legs (aardvark, hippo, pangolin, antelope, giraffe, elephant) | walk, trot and gallop, changing at about the same Froude number in all of them; the body highest mid-step walking, lowest running; the head and neck nod with the steps in walking hoofed animals; giraffes never trot, they walk with the legs of a side nearly together and gallop with a swinging neck; elephants never leave the ground with all four feet, even at their fastest (Hutchinson and others 2003); a ground pangolin walks on its hind legs with its tail as a balance | limbs of 3 to 4 parts bending the right way (A1), gait cards with the real numbers and Froude gears (A2), the right bob for walk and run (A2), a neck chain that nods (A3) |
| No legs (snakes, worm lizards) | the body follows the head's path in an S (lateral undulation); in tunnels, concertina (bunching and stretching); heavy snakes such as the puff adder creep straight on their belly scales (rectilinear); sidewinding on loose sand | a long path-following spine of 10 to 30 bones (A3) and the game moving it along a path, not on legs (G1, its own small mover) |
| Hoppers (frogs, springhares) | crouch, push off with the hind legs fully straightened, fly, land on the front legs; many frogs also walk and crawl | clips with a flight phase, the jump timed with the game's own jump (G2), checks that allow no feet down (K5) |
| Fliers (birds, termite alates, wasps) | wingbeats from a few a second (big birds) to over 100 (small insects, a blur to the eye); walking ground birds hold the head still in the air, then thrust it forward (the head bob, Frost 1978) | wings in the body plan; flap, glide, take off, land clips; a blurred wing look for fast beats; the steady head (A4). The termite alates of M9 come first and use the same pieces |

### A1. Limbs of any length, bending the right way (new; the core of the kit)
- **What today:** the motion library's leg (`ant_motion.Leg`) is exactly three bones, hip, knee and ankle, and stops
  with an error otherwise; the hip turns and lifts, the knee and ankle bend in one plane, the knee is kept from
  straightening or flipping (`knee_guard`), and every key is solved by a general search written in plain Python
  (`least_squares`). A deer's hind leg (hip, knee bending one way, ankle bending the other, a long foot, the hoof), a
  lizard's splayed leg turning at the shoulder, or a bird's leg cannot be described.
- **The idea:** a limb is a chain of 2 to 5 bones named by the plan, each joint with its kind (a hinge or a ball),
  its real limits and the way it bends (a pole: the knee forward, the ankle back); the solver starts from the last
  key's pose, as today, so the foot path stays smooth. Spiders' 7-part legs are drawn as 4 parts (the extra joints are
  small), birds' and hoofed animals' legs as 4 (thigh, shin, the long foot, toes). A two-bone limb has a direct answer
  (no search), which also makes the clip maker faster.
- **Why:** every family but the legless needs it. **Quality kept:** the lab's foot-slide and joint-limit checks on
  every new limb; the ant's own clips remade and compared (they must not change).
- **Size:** medium. **Depends on:** nothing.

### A2. Gait cards from real animals, with Froude gears (new; the last review's B2 carried on)
- **What today:** `gait()` makes each foot's moment from Wilson's rule (a lag along each side, the two sides half a
  cycle apart); the walk's duty factor and the bob are the insect's; four-legged walks and trots already come out of it
  in its self-test, but there is no gallop, no flight phase and no gait change by speed.
- **The idea:** each animal has a gait card in its body plan: for each gait (walk, trot, pace, canter, gallop, bound,
  hop) the duty factor, each foot's moment in the cycle and the stride length against speed, taken from real
  measurements (Hildebrand's gait diagrams for many mammals and lizards; Alexander's papers; the animal's own studies
  where they exist); the speeds where it changes gait from the Froude number and its hip height; the body's bob by the
  real rule (highest mid-step walking, lowest running) and its size from the leg length. The motion library makes each
  gait from its card; the game's gear list (G2) plays them.
- **Why:** realism first: each animal's legs move in its real pattern at its real speeds, and a new animal is a card,
  not code. **Size:** medium to large. **Depends on:** A1.

### A3. Spines that wave and follow (the last review's B3, its game side built)
- **What today:** the game bends spine pieces by the path walked (the ant's waist, the termites' soft abdomens, the
  Matabele queen's gaster); the motion library sways the gaster a little in the walks. Nothing makes a body wave of its
  own, and the motion library has no neck or tail.
- **The idea:** in the motion library, a segment chain (neck, back, tail, a centipede's body) that can (a) wave with
  the steps (a lizard's S, its amplitude and how many waves fit on the body from the gait card), (b) follow the path
  of its front, and (c) hold a pose with springy give (a scorpion's tail, a tortoise's neck). In the game, a legless
  body's spine is drawn along its own path every frame (the game's `Spine` already does this by distance walked; it
  needs more pieces, cheaply).
- **Size:** medium. **Depends on:** A1 for the trunk-wave link to the legs.

### A4. A steady head (new)
- **What:** real animals keep their eyes steady while the body bobs (a bird's head held in the air, a cheetah's head
  level at a gallop, a deer's head smooth while its body rocks). Baked into the clips (the neck takes the body's bob
  out, up to the neck's limits) and in the game's live layer when the body tips on a slope.
- **Size:** small once A3's neck exists. **Depends on:** A3.

### A5. The small creatures' size (the open question in `docs/DESIGN.md`)
- At a block a metre, a centipede is 0.15 to 0.25 blocks, a scorpion about 0.1, a dung beetle 0.03, the guest frog
  0.04; the insects are drawn about 30 times their size. The kit must handle both scales cleanly (the body plan says
  which scale a model is drawn at; K4's table reads it), and the proof animals' sizes must be picked first: it decides
  how big they are on screen, and so how many boxes they earn (V6).

# Part 5: RUNNING SPEED

Animals are few and big, so the server and drawing costs are not where the insects' were; the last review's drawing
work (F2 quick wins, F3 to F7 crowds) still stands for the insects. For animals:
- **F2's quick wins** (each player reading only its own bones, no slanted boxes, skipping the flat-box fix; about 9,
  10 and 10 percent of the ant's drawing cost when measured) apply to every model, so they are worth doing once before many models
  exist. Small. Guarded by the guard films' picture compare.
- **Smaller files** (K3) load faster at start-up: 2.9 MB per Matabele model today, and every animal adds its own.
- **A herd** (hippos on the floodplain, antelope) is 5 to 30 animals: a simpler far model (LOD, the last review's F4)
  for each family, made by the model kit from the same parts, when herds come (M9.5).
- **The live layer** is cheap (under 0.5 percent measured) and stays within 32 blocks; for animals a block or more long
  it should reach farther (they are seen from farther), my pick 64.

# The order I recommend (my picks)
1. **K1** clips remade only when they change, every thread. Small to medium. Pays back at once, for the Matabele too.
2. **K4** one size and speed table, and **G3** each model's own head-player bones. Small each. Clears two traps before
   new models arrive.
3. **V4** the shared viewer script, offline, then **V1** the photo outline and **V3** rounds kept. Small to medium.
4. **G1** the animated body, then **G2** clips from data. Medium. Nothing changes in-game; guard films prove it.
5. **A1** limbs of any length, then **A2** gait cards. Medium to large. The heart of the kit.
6. **V5** the model kit with "outline to boxes", **V2** sliders, **V6** the box budget. Medium.
7. **A3** spines that wave and follow, **A4** the steady head, **G4** the live layer for animals. Medium.
8. **K2** shared clip recipes, **K5** lab checks for every family, **K6** the pose viewer for any model. Medium.
9. **K3** smooth curves and fewer keys, **F2** quick wins, **K7** the climbing copy check, **K8** guard films the
   same every launch. Small to medium each.
10. **Proof:** the three animals from different families (`docs/DESIGN.md`), each timed from the first photo to the
    user's "it looks right", so the kit's gain is measured, not guessed.

Flight, hoppers and the legless mover are built with the first animal that needs them (the flight pieces with M9's
alates, which come before M9.4).

**My guess at the gain** (not measured until the proof animals): K1 takes the clip waits from about 21 minutes to seconds;
V1 to V3 should halve the look rounds (the Matabele's 15 were mostly shape guessed from words); V5, A1, A2 and K2 turn
an animal's first model and its 15 everyday clips from days into hours, leaving the time for what makes each animal
itself.

# Your calls
1. **The proof animals.** `docs/DESIGN.md` has my earlier pick: a Nile monitor (sprawling four legs), a centipede
   (many legs) and a dung beetle (six legs). With this review I would swap the dung beetle for an **aardvark** (upright
   four legs): six legs are done, the upright family is the biggest gap (A1, A2) and most of the savanna's big animals
   are in it, and the aardvark matters to the termites (M9.5). Your pick.
2. **The small creatures' size** (A5): drawn at a block a metre (a centipede 0.2 blocks, smaller than a Matabele
   major) or at the insects' enlarged scale, or picked one by one by eye. It changes how they look beside the ants and
   in the world.
3. **Blockbench by hand** (V7): if you want to touch models or poses yourself, Blockbench both ways goes into the
   order; if not, it waits.
4. **Your time:** steps 1 to 4 of the order are a few sessions with no in-game test needed except the guard films;
   from step 5 on there are things to look at in the pose viewer. Nothing in the order changes how the game plays.

# Left out on purpose
- Rewriting the motion library in Java or a faster language: its speed problem is remaking everything (K1), not the
  language.
- A general physics engine for bodies (ragdolls, simulated muscles): too slow for the game and not how the clips are
  checked; the real numbers in gait cards give realism at a fraction of the cost.
- Machine-learned motion from videos: not reliable enough to pass our checks, and no data for most savanna animals.
