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
   **Two strikes, then rethink (the user, 2026-11-19; CLAUDE.md).** Two tries on the same starting idea that the user
   (or the checks) still find wrong: no third try. Write the starting idea down in one sentence (the monitor's step
   climb: "the body bends to the ground each frame, the feet are fixed up after"), ask whether the real animal moves
   that way (a climbing lizard places its foot on the step first, the body follows), and bring the user a new starting
   idea before any code. Early strikes: the same number tuned a third time (the climb's ramp, 0.2 then 0.11 then 0.08),
   or a fix whose only job is to hide an earlier fix's side effect.
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
9. **Real ideas, Minecraft looks (the user, 2026-11-19: "use realistic ideas for the motions but for the actual
   motions ... just go with whatever looks good in minecraft", above all going up blocks).** Real animals give the
   ideas (head in first, legs folding as it floats, the back's S-curve); the motion itself is whatever looks best on
   one-block steps and in Minecraft's light, never a copy of the real animal's geometry. So the course's numbers are
   evidence for the eye, not pass marks: a body easing over a step like a ramp may graze a block corner if that reads
   smoother than bending sharply round every edge. The final judge is the film. (Ants and termites keep strict
   realism.)

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

## Baseline: the measuring course on today's monitor and crocodile (2026-11-19, step 1)
Measured by `tools/body_course.py` from the game's own frame-by-frame record (3 samples a tick, every bone); reports
`run-film/course/course_<animal>.txt`, graphs `run-film/course/course_<animal>_<scene>/graph.png`. Nothing about the
looks changed. Numbers are evidence for the eye (rule 9), not pass marks. Ranked by how bad it looks, worst first.

1. **The step up (monitor; sure; the back's finding 1 confirmed and worse than suspected).** One step, in order
   (`step_up`, ticks): t 210 the front feet jump 0.3 blocks up in one tick (the leg folding finds the step); t 218 to
   222 the chest rises about 0.9 blocks and the hips 0.6 in four ticks, **16 ticks before the game's box steps**, so
   for most of a second the monitor stands on stretched hind legs with its hips about 0.8 blocks over the lower ground;
   t 228 the hind feet jump 0.35 blocks; t 238 to 241 the box climbs the block in three ticks and the whole drawn body
   rides up with it on top of the lift it already had: the head and chest **overshoot about 0.6 blocks** above where
   they end up, then sink back over about 8 ticks (a bounce); the tail tip dips and takes about 15 ticks to come up.
   The same at every step of `stairs`. The crocodile shows it smaller (a 6 px foot pop, an 11 degree chest-hips snap).
   Cause: the lift rules (whole-body lift, edge hinges, leg folding) act ahead of the box, then the bend's easing acts
   again when the box jumps; two systems each lift the body. Step down: no bounce, but the tail is drawn up to 9.5 px
   inside the upper block as the hips drop.
2. **Claws jump at every clip change (monitor; sure; the water finding 2 confirmed).** Walk to `enter` (`bank_level`,
   t 207) and walk to `swim` (`bank_high`, t 237): the front claws move about 1.3 blocks in one frame. The leg folding
   is drawn over the clip only while walking; the new clip starts from its own pose.
3. **`enter`, walk, `enter` (monitor; sure; the water finding 1 confirmed).** Walking into level water: `enter` 180.7,
   walk 204, `enter` 207, walk 230, `swim` 231, each change with the claw jump of 2. From a high bank there is no
   `enter` at all: it walks off and falls 2 blocks into the water in walking pose. The dash and the real scare go
   walk, `enter`, `swim` once (fine).
4. **Clip speed changes jump the clip (all four river animals; sure; new, BUGS 232).** GeckoLib plays a clip at "speed
   x time since it started", so changing the speed moves the clip to another moment. The monitor's tail swings 0.4
   blocks sideways in one frame as it slows at its goal (`flat` and `step_up`, t 319: hips-tail1 snaps 17 degrees,
   tail9 to 12 snap 10 to 16).
5. **The crocodile's neck at the water (sure).** Off a high bank its neck-chest joint snaps 29 degrees in one frame
   (t 150) and the neck-chest seam opens 6.8 px; its tail4-tail5 joint snaps 21 to 23 degrees going into and out of
   water (`bank_level`, `dash`, `climb_out`).
6. **Feet barely lift (monitor; sure; small).** On the flat the feet lift about 1.7 px (0.08 blocks) a step and the
   claws graze about 1 px into the ground mid-swing; planted feet hardly slide (good). Over steps the slide rises to
   0.3 to 0.4 of the body's speed.
7. **Neck seams open (monitor; sure; small).** About 1 to 1.5 px between neck1, neck2, chest and head on the flat,
   4.9 px climbing out.
8. **Turning (monitor; partly measured).** The tail tip pops up to 12 px on the turns; sweeping of the whole body
   about the hips not yet measured as such (needs a path-following check, step 2).

Dropped or not seen: none of the suspected findings was wrong. Not yet measured: the bob after falling into the water
(water finding 3; the course has no bob measure yet) and the off-screen leg states (finding 4).

**Ideas for steps, judged by this baseline:** finding 1 is two lifts stacked; (b) of plan step 2 (ease the drawn
height after the box jumps) alone would remove the bounce, and (a) (one smoothed ground line) would replace the early
lift, the hind legs left on stilts and the separate edge rules. So (a) with (b) stays my pick to try first.

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
Every animal on the master list (`docs/LIVING-THINGS.md`, looked over 2026-11-19 for this) fits one of these families.
The engine is designed for the whole list from the start (nothing in it may assume four legs, one tail or a short
body), but each family's own piece is built only with its first animal.

| Family | Animals on the list | Reuses | New for it |
|---|---|---|---|
| Sprawling four legs | Nile monitor, crocodile, helmeted terrapin, flap-necked chameleon (desert: horned lizard) | everything starts here (phase 1) | the S-curve of the back, legs folding to swim; the terrapin's shell is a back that does not bend; the chameleon's slow rocking walk, gripping feet on branches, eyes turning each on its own, the shooting tongue |
| Legless | black mamba, pythons | the body following its head's path, the steady head | a sideways wave; climbing and coiling later; almost nothing else |
| Many legs on a long body | giant African millipede (the user, 2026-11-19), centipedes | the body following its path (as the snake), the insect leg engine for the legs | dozens of legs in waves along the body (in real millipedes the waves run forward along the body, centipedes' run back; to check when built), and the millipede **curling into a tight coil when touched**: the follow-the-path chain wound into a spiral and unwound again |
| Six legs (the insect engine today) | African dung beetle, giant devil's flower mantis, antlion larvae, spider wasp, bees and mud wasps on foot | the whole insect engine | freed from the ant class (G1); the dung beetle walking backward on its front legs with its hind legs on the ball; the mantis's strike |
| Eight legs | king baboon spider, sun spider, both scorpions, savanna whipspider | the insect leg engine (feet planted, walls) | 8 legs (G1); the sun spider's feeler legs held up front, the scorpion's pincers and tail held over the back, the whipspider's long feeling legs |
| Upright four legs | lion, cheetah, leopard, hyena, wild dog, honey badger, meerkat, aardvark, aardwolf, pangolin, elephant, giraffe, rhino, zebra, warthog, hippo, antelope, buffalo, clawless otter | spine, steady head, ground bend, checks | legs of more joints bending the right way (A1); gaits from real numbers (A2): walk, trot, gallop with all feet off the ground (new to the engine), the giraffe's pace (both legs of a side together); the cheetah's flexing sprint |
| Upright four legs, extras | elephant, giraffe, meerkat, pangolin, warthog, otter | the bending, following chain (the tail's) | the elephant's trunk on that chain, ear flaps, looking heavy; the giraffe's long neck; the meerkat sitting up on guard and the pangolin walking on its hind legs (both lean on the two-legs piece); the pangolin rolling into a ball (as the millipede's coil); the warthog kneeling; the otter swimming |
| Two legs | ostrich, secretarybird (birds on the ground) | the two-legged walk (A1), the steady head | the head bob; the ostrich's run; the secretarybird's stamp |
| Fliers | every bird, bees, wasps, termite alates (M9.5), butterflies | the steady head, the checks | flight, its own job with the first flier: wingbeats, gliding, soaring (the martial eagle), the stoop |
| Hoppers | giant bullfrog, foam-nest tree frog, snake-necked frog | the checks, swimming from the fish | the hop and landing, swimming with the hind legs |
| Fish | tilapia and catfish (built, outside the engine today), tigerfish, electric catfish, bull shark, lungfish, killifish | the sideways wave of the snake, in water | bringing the built fish into the engine's checks; the tigerfish's leap; the lungfish curled in its mud cocoon |
| Shells | river snail (built), giant African land snail | little; the checks | the foot's glide, the eye stalks |

Hardest, as far as known now: flight and the gallop (moments with no feet down, new to the engine) and the many-legged
body with its coil. Cheapest: the snake (once the monitor's path-following body exists) and the eight-legged ones
(once the insect engine is freed from the ant class). The millipede is the snake's body plus the insect engine's legs,
so it comes cheap once both are there.

**Size scale, an open call the engine meets (`docs/DESIGN.md`, "The animal kit"):** animals are drawn a block a metre,
insects at 1 block = 30 mm. Small creatures that meet the insects body to body (the millipede about 30 cm, scorpions,
spiders, centipedes, frogs) are tiny at a block a metre, smaller than some ants. This decides how big their legs and
steps are for the checks; to settle with the user before the first small creature.

## The plan
**Phase 1: the monitor, the engine's first animal.** Each step builds the shared version, not a monitor-only one.
1. **The measuring course (shared, any animal).** `FilmChecks` records any animal model, not only insects (gaps
   between neighbouring body boxes, parts inside blocks, pops, feet sliding, sinking or floating, which clip plays
   each tick); the camera writes the frame-by-frame record of every part (rule 1); graphs of snout, chest, hips and
   tail tip height over time. Course scenes filmed every tick: a one-block step up, a step down, a level bank into
   deep water, a bank a block above the water, a gentle shallow slope, a scared dash in, swimming out onto a bank, a
   turn round on land. Run on today's monitor and crocodile: **a baseline report**, the findings above confirmed or
   dropped and ranked by size. Nothing about the looks changes in this step. Built for the whole list: the checks
   take the legs, feet and body chain from each model's body plan (any number of legs, none for a snake, dozens for
   the millipede), never from a four-legged layout. **Built 2026-11-19:** the camera's `BodyRecorder`
   (`framework/.../client/BodyRecorder.java`) writes every bone of the walker each sample (`record on 3` in a shot
   list; any mob can be the walker now); `tools/body_course.py films|run|check [animal]` writes the course scenes
   (`tools/film/course/`), films them and judges them (parts inside blocks, seams, joint snaps, pops, feet over the
   ground and sliding, clip changes, graphs). The legs, feet and chain come from each model's body plan (`family`,
   `chain`; monitor and crocodile plans added). The baseline is the section above. The gentle slope is `stairs` (a
   step every three blocks); a real scare is `scared`. `FilmChecks` itself is unchanged (the course does its job for
   animals).
2. **The body follows its path (shared, review A3).** One framework rule in place of the pile above: the body's
   joints are placed in the world along the path the head took, eased as places in the world (not angles against the
   box); over a block step the path is smoothed into a short ramp (rule 9: what looks good, not a sharp corner round
   every block edge, grazing a corner allowed where it reads smoother). The user, 2026-11-19: block steps up and down
   are where most of the jank is and will stay the biggest problem for every animal ("blocks aren't realistic, so we
   will have to get creative"). Ideas to weigh against the course's step scenes (my picks to test, none decided):
   (a) one smoothed ground line, the block tops blurred over about a body length, that the whole body lies on, the
   feet still reaching down or up to the real block top under them; (b) the animal's drawn height eased over a few
   ticks after its box jumps a block (the box's one-tick jump hidden, the game's movement untouched); (c) a climbing
   move timed by where the step is under each leg pair: front legs reach up, the chest rises, then the hips follow.
   Fewer rules wins: (a) and (b) together may replace most of today's pile. Fixes the step pops, turning on the spot and going in head first, from one rule. The
   crocodile moves to it too. The rules it replaces are removed, not kept beside it. Built for any chain length (the
   snake and the millipede are long chains of this; the millipede's coil winds the same chain up), and cheap enough
   for many animals on screen.
   **First tries, 2026-11-19 (not kept; saved on branch `claude/monitor-step2-tries`; the user: "its not very good I think we need to rethink the animation
   entirely the legs jump up last second everytime thats the issue of jankiness", then stopped for the day).** Built
   and filmed: (a) the smoothed ground line in world heights (`GroundBend.line`, `RAMP` 0.08, `CLEAR`
   0.3): the bounce went (biggest per-sample height jump on the course 0.11 to 0.22 blocks before, 0.015 to 0.04
   after); (b) feet reach by swinging the upper leg at the shoulder (`client/LegSwing`, up to about 10 px up, 4 down)
   instead of sliding it; the foot stands on the highest ground its toes find 0.3 blocks ahead (`GroundBend.TOES`);
   (c) the user's idea, the torso raised as one piece to higher ground ahead (`GroundBend.raise`). What the side films
   (`tools/film/look/`, low side camera) showed: the monitor is 4.5 blocks long and a step is three times its
   shoulder height, so with both pairs of feet on the ground the trunk must tilt about 45 degrees for about a block of
   walking (2 s at its stroll); the snout is a block ahead of the forefeet, so the head must rise before the feet
   arrive. The look still fails because the **feet jump up at the last moment**: each foot follows the walk clip's
   fixed stride and only finds the step when its own place crosses the edge, so it pops up as the body is already
   reared. Lesson for the rethink: the feet must be placed by where the step is (each foot's next landing chosen
   onto the step, the body following the feet), not the body bent first and the feet patched after; the climb is a
   move of its own, timed from the feet (idea (c) above), not a pose worked out each frame.
   **Rethought, 2026-11-19 (plan only, the user's go): `docs/plans/engine-step2-feet-first.md`.** Each foot holds its
   spot on the ground and picks where it lands next; the body is held up by where the feet are. Steps 3 and 4 wait for
   it (both ride on planted feet).
   **Started over, 2026-11-19 (the user: "start over completely ... from scratch" on going up and down a block; walk,
   hiss, bite, eyelids good): `docs/plans/engine-step2-climb.md`, replacing the feet-first plan (its unfinished stage A
   kept on branch `claude/monitor-feet-first-tries`).** The climb is a move of its own, made once for a one-block step
   (every Minecraft step is the same), the game moving the monitor along the move's own path; the user's idea at its
   heart: it rears up first, then pushes itself up with its feet on the block. The tail becomes a chain dragged from
   the hips (follows the path, drapes over edges). Stages: A the tail, B up, C down, D hills and clean-up.
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

## Decisions and open calls
- **Decided (the user, 2026-11-19):** step 1 is built as the shared version for every animal. Built 2026-11-19.
- **Decided (the user, 2026-11-19):** the giant African millipede and the rest of the master list are part of the
  engine's design from the start (the families table above).
- Open: which animal after the monitor (decides which family is built next).
- Open: the size scale for small creatures (above).

## Hard rule: offline and in-game checks for every animation (the user, 2026-11-19)
After the monitor's climb up came out right this way ("a combination of the animation engine and in game was perfect"), every new or changed animation or movement, any animal, is checked both ways before the user sees it: offline as the game draws it (`tools/climb_check.py` for the monitor's climbs; a checker of the same kind for other clips), then the real game frozen and stepped a few ticks a picture from three sides (`tools/film/monitor_climb_frames.film` as the pattern, `tools/film_sheet.py` for contact sheets), every phase judged, joints measured over time when a limb looks wrong, and the course scenes it touches run. How-to: `docs/plans/engine-step2-climb.md`, "How stage B was got right".
