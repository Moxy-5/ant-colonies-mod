# The Animal Maker Engine (the user's name, 2026-11-19)

**Read this before making or changing any animal's model, clips or body movement**, as CLAUDE.md is read before any
work. It is this engine's own rule book: what it is, how to work with it, what is wrong today, and the plan.

The engine is not one program. It is **one way of working that joins three things already in the project**: the
model workshop (the looks), the animation engine (clips, checks, films) and the game's body code (how an animal moves
in the world). They are written in three languages (Python makers, a JavaScript viewer, Java in the game), so the
code cannot be one; the **systems** are one: the same rules, the same checks and the same pictures at every stage.

Animals are fun first, inspired by real life (CLAUDE.md); colony insects stay strictly real. The engine serves both:
its checks are about things that look broken (gaps, sliding feet, pops), never about matching a paper.

## Rules (for Claude, every new animal)
1. **The game is the one truth.** What the game draws is what counts. The in-game camera writes down where every
   body part was drawn, frame by frame; the checks and the viewer read that record, so the viewer and the game can never
   disagree. A rule that also lives in a Python maker gets a "match" check proving it gives the game's result (as
   `anim_lab.py match` does for ants).
2. **Checks run by themselves, every time.** Every clip written runs the lab checks; every change to an animal's
   body code (Java) runs its course scenes (below) and their checks. Claude reads the findings before showing the user
   anything. Never judge movement by eye from a few still pictures alone (the monitor's lesson, below).
3. **Measure before fixing.** A movement bug is first caught by a check or a course scene (a number, a graph, a
   flagged frame), then fixed, then the same check proves it gone. A fix that needs a fix of its own: stop and rethink
   (CLAUDE.md, "prefer fewer rules").
4. **Built family by family, when the first animal of that family arrives.** No building the whole engine first.
   Each new animal pays for its own new piece; the rest it reuses (the families table below).
5. **Share ways of building, never the motion itself** (memory "unique animals, shared methods"). Every animal keeps
   its own clips and its own numbers (stride, sway, head carriage, how heavy it looks). The engine only makes sure
   nothing breaks. If two animals start to move alike, that is a bug.
6. **Species-neutral in the framework.** Shared body rules live in `framework\` and never name a species; an animal's
   own code only fills in its numbers and its body plan (as the colony framework's sockets).
7. **The steps with the user stay the workshop's** (`docs/plans/model-workshop.md`: outline, plain 3D shape, detail,
   paint, animations, each with the user's OK). The engine adds to the animations stage: the clip in the viewer with
   the checks drawn on it (a red mark where the back opens or a foot slides), and the course film.
8. **Old checks stay.** A check that once caught a real bug is never removed; each new body bug gets its own course
   scene or guard film.

## What exists today (2026-11-19)
- **Looks, built (the model workshop, steps 1 to 4):** `tools/workshop.py` (brief, starter outline, outline, versions,
  snapshot, serve), `tools/viewer_common.js` + `tools/viewer_page.py` (one offline 3D viewer with Compare),
  `tools/viewer_shot.py` (Claude takes its own pictures of a page), `tools/model_parts.py` (the parts library: box
  maker, painter, game-file writer, approved parts), `tools/body_plan.py` (bone roles, box budget).
- **Animation, built for insects only:** `tools/anim_lab.py` (films, graphs, the checks: feet sliding, parts in the
  floor, legs through the body, loop seams, snaps), `tools/ant_motion.py` (the motion library: 3-bone insect legs),
  the pose viewer, `tools/guard_films.py`, the in-game camera (`./gradlew runFilm`, `tools/film.sh watch`) and its
  automatic checks `framework/.../client/FilmChecks.java` (part inside a block, sudden jump).
- **Game body code for animals, one copy per animal:** `framework/.../entity/body/GroundBend.java` (shared: each
  piece of a long body lies on what is under it; used by the crocodile and the monitor; unit tests in
  `GroundBendTest`), and each animal's own model class with its own extra rules (`MonitorModel`: leg folding, foot
  planting, joint closing, head look; `CrocodileModel`: its own). Clips are made per animal by its own maker
  (`tools/monitor_models.py`, `tools/crocodile_models.py`, ...).
- **Planned, not built:** the tools review `docs/plans/animal-kit-tools-review.md` (2026-11-08) holds the animation
  half of this engine as ideas: K1 clips remade only when changed, K5 lab checks for every family, K6 the pose viewer
  for any model, G1 an animated body for any creature (not an ant), G2 an animal's clips listed in data, G4 the live
  layer for animals, A1 limbs of any length bending the right way, A2 gaits from real animals, A3 spines that wave and
  follow, A4 a steady head. Its V1 to V6 became the model workshop (built). Read the review for the detail of each.

## Findings: the Nile monitor (2026-11-19, read from the code and clip files, no films looked at)
The user: rather than fixing the monitor's body report by report, find what is wrong and what is best. Marked
**sure** (seen in the code or measured from the clip files) or **suspected** (follows from the code; to be measured
by the course before anything is fixed).

**The testing gap (sure, the biggest finding).** The camera's automatic checks (`FilmChecks`) only record colony
insects (`FilmDirector.noteRendered` takes an `AbstractAntEntity`), so no monitor or crocodile film ever had a check;
the lab's checks only know insect legs. Every monitor fix so far was judged by eye from still pictures, which is why
reports kept coming back.

**The back**
1. **The body pops at every step up (suspected, likely).** The game moves the monitor as a 0.7-block box at its hips
   while it is drawn 4.5 blocks long. With a step height of 1 the box rises a full block in one tick, but the bend
   eases each piece's angle against that box over about 0.15 s (`GroundBend.EASE`); for a moment the tail sticks out
   a block up in the air and the chest points too high. Stepping down, the hips hang over the drop until the small box
   clears the edge, then the body falls. Root cause: the bend eases angles relative to a root that jumps, not places in
   the world.
2. **It walks and swims like a stiff plank (sure).** In the `walk` and `swim` clips the side to side swing is all on
   the `body` bone (6 and 5 degrees): chest and hips turn the same way as one piece; `waist`, `ribs`, `chest` and `hips`
   have no sideways turn. The neck turns back against it (head held steady: good) and a wave runs down the tail (good).
   A real monitor's shoulders and hips turn opposite ways with each step, so the back curves one way then the other.
3. **It turns like a pivoting stick (sure).** The whole drawn body turns about the small box at the hips, so head and
   tail sweep sideways; a real lizard walks a curve and its body follows the path its head took.
4. **Too many rules on the back (sure).** The bend, a whole-body lift, the waist's joint closing on a moving edge, an
   edge hinge on every trunk joint, leg folding and foot planting: each was a fix for the fix before (2026-11-18). The
   warning sign of CLAUDE.md's "prefer fewer rules".

**Going into the water**
1. **A fixed 1.2 s `enter` clip laid over a move it does not control (sure), which can repeat (suspected).** The
   drawing code starts it (`MonitorModel.legs` sets `enterTick`) when the forelegs lose the bank over water; a stroll
   and a scared dash get the same clip. If the box is still on the bank when it ends, `swimClip` needs `!onGround`, so
   the walk clip comes back, the hind legs stand, and the start rule can fire again: enter, walk, enter.
2. **Legs may flick at a clip change (suspected).** The model's own leg folding stops the moment the `enter` or
   `swim` clip starts, and GeckoLib blends (4 ticks) from the last clip's pose, not the folded pose the model drew.
3. **It falls in, then bobs (sure from the code; size to measure).** Off the edge it falls; in the water there is no
   gravity and speed only fades (0.9 a tick, `MonitorEntity.travel`), so it sinks on, then the float pulls it back up
   (at most 0.03 a tick). Climbing out is a hop (0.3 a tick up against a bank).
4. **Pieces that are only drawn when on screen (sure; small).** Leg folding and the `enter` start live in the drawing
   code, so a monitor not drawn keeps old leg states; turning the camera to it can show a short jump.

**What a real monitor does (the target).** Calm, it walks in: the head goes in first and the body slides in behind
along the same line; each leg keeps walking on the bottom until the water lifts it, then folds back along the body;
the tail's walking sway turns into the swimming wave, legs folded, head up at the surface. Startled, it dashes and
plunges in head first and swims off under the water. No separate dive move: smooth entry comes from the body
following its own path.

## Concerns
1. **Scope.** The whole engine is weeks of tools. Building it all before the next animal would stall the fun part.
   Answer: rule 4, grow it through real animals, the monitor first.
2. **Three languages.** Rules duplicated in Python and Java can drift apart. Answer: rule 1 (the game's record is the
   truth, match checks for anything duplicated).
3. **The insect engine is welded to the ant class.** The animation players, live layer and renderer need
   `AbstractAntEntity` (3,330 lines of colony insect) but use about 15 things from it (review G1). Animals cannot reuse
   them until that is cut loose; insects must not change (guard films: 0 changed pictures).
4. **Everything moving the same.** A shared kit can make every animal feel like the same robot in a different skin.
   Answer: rule 5.
5. **The crocodile shares `GroundBend`.** Any change to the bend moves the crocodile too; it goes through the same
   course before and after.
6. **Running speed.** The bend and leg rules run every frame for every animal drawn. Keep them cheap and skip them
   far away (as `MonitorModel` does past 48 blocks); measure with many animals on screen.
7. **Box budget.** The monitor is 665 boxes against a budget of 400 (`body_plan.py`); a lot of animals on screen will
   cost frames. Not urgent; noted.
8. **Unsure until measured.** Every "suspected" finding above is a reading of the code, not a measurement. The course
   comes first so fixes target what is really there.

## Body families (built when each first animal arrives)
| Family | Animals planned or asked about | Reuses | New for it |
|---|---|---|---|
| Sprawling four legs | Nile monitor, crocodile | everything below starts here | the S-curve of the back, legs folding to swim |
| Legless | snakes | the body following its head's path, the steady head | a sideways wave; almost nothing else |
| Eight legs | king baboon spider | the insect leg engine (feet planted, walls) | the engine freed from the ant class (G1); 8 legs instead of 6 |
| Upright four legs | lion, African elephant | spine, steady head, ground bend, checks | legs of more joints bending the right way (A1), gaits from real numbers (A2): the elephant's walk, the lion's trot and gallop with all feet off the ground (new for the engine) |
| Elephant extras | African elephant | the bending, following chain (the tail's) | the trunk on that chain, ear flaps, looking heavy |
| Two legs and wings | birds | two-legged walk (A1), steady head | the head bob; flight, its own job, with the first bird |

Hardest, as far as known now: bird flight and the lion's gallop (moments with no feet down, new to the engine).
Cheapest: the snake and the spider.

## The plan
**Phase 1: the monitor, the engine's first animal.** Each step builds the shared version, not a monitor-only one.
1. **The measuring course (shared, any animal).** `FilmChecks` records any animal model, not only insects (gaps
   between neighbouring body boxes, parts inside blocks, pops, feet sliding, sinking or floating, which clip plays
   each tick); the camera writes the frame-by-frame record of every part (rule 1); graphs of snout, chest, hips and
   tail tip height over time. Course scenes filmed every tick: a one-block step up, a step down, a level bank into
   deep water, a bank a block above the water, a gentle shallow slope, a scared dash in, swimming out onto a bank, a
   turn round on land. Run on today's monitor and crocodile: **a baseline report**, the findings above confirmed or
   dropped and ranked by size. Nothing about the looks changes in this step.
2. **The body follows its path (shared, review A3).** One framework rule in place of the pile above: the body's
   joints are placed in the world along the path the head took, never through the ground, eased as places in the world
   (not angles against the box). Fixes the step pops, turning on the spot and going in head first, from one rule. The
   crocodile moves to it too. The rules it replaces are removed, not kept beside it.
3. **The back curves sideways.** The `walk` and `swim` clips swing shoulders and hips opposite ways across the three
   trunk pieces (`tools/monitor_models.py`), the S-curve; the lab's checks extended to four legs and a tail (K5).
4. **Smooth water entry.** The `enter` clip and its start rule go; legs and tail blend from walking to swimming by
   how much of the body is really afloat, each leg pair walking until its own feet lose the bottom; the fall and bob
   into the water and the hop out made a steady glide.
5. **Prove it.** The course again, before and after side by side; guard films for the monitor and crocodile; then the
   user tries it in-game.

**Phase 2 onward: one family per new animal**, in whatever order the user picks animals (the table above), each
starting with the workshop's stages and ending with its family's piece built, checked by the course, and its own
course scenes added. The review's ideas come in with the animal that needs them: G1 and G4 with the first animal
that needs the insect engine or the live layer, A1 and A2 with the first upright four-legged one, flight with the
first bird, K1 and K6 as soon as clip making feels slow.

## Open calls for the user
- Whether step 1 is built shared (my pick) or monitor only first.
- Which animal after the monitor (decides which family is built next).
