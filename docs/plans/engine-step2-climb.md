# Animal Maker Engine step 2, started over: the climb is a move, the tail is dragged (plan, 2026-11-19)

The user, 2026-11-19: "lets completely start over on the animations of the lizard going up and down a block from
scratch"; the walk, the hiss, the bite and the eyelids are good and stay. Their idea, kept as the heart of this plan:
**going up a block, the monitor raises itself first, then pushes itself up the block with its feet on the block; and
the tail must look good.** Replaces `docs/plans/engine-step2-feet-first.md` (not built; its unfinished stage A is kept
on branch `claude/monitor-feet-first-tries`, and the first tries on `claude/monitor-step2-tries`). Read
`docs/ANIMAL-MAKER-ENGINE.md` (rules, the baseline, step 2's first tries) first.

## Why the earlier ideas failed (two strikes)
- First tries: "the body bends to the ground every frame, the feet are fixed up after."
- The feet-first rethink: "each foot finds the step for itself every frame as it walks."
Both work the climb out live, every frame, from where the game's box happens to be: a 0.7-block box at the middle of a
4.5-block animal that jumps a whole block up in one tick (`STEP_HEIGHT` 1). Every fix chased that jump. A real lizard
does not climb by a pose worked out moment to moment; it does an ordered move: look, rear up, hook the claws over,
haul, step the hind feet up, the tail dragged last.

## The new starting idea in one sentence
**Climbing a block is a move of its own, made once; the game moves the monitor along that move instead of the move
chasing the game.**

Why it fits Minecraft: every step is exactly one block high with square edges, so a climb made once for a one-block
step fits every step exactly (no real ledge is like that). The clip maker (`tools/monitor_models.py`) already puts each
foot where it is wanted and bends the leg to reach it (the walk the user confirmed is made that way); the climb is
made the same way with the step built into its scene.

## The moves (my picks; timings measured in the films, not guessed)
**Step up** (about 2.5 s at a stroll):
1. It walks up to the step and stops with its forefeet at the face (real monitors pause at a ledge).
2. Looks: the neck lifts, the head goes over the top, a tongue flick (tasting the ledge).
3. Rears: forelegs off the ground, the chest rises to about 45 degrees on the hind legs, **the tail base pressed on
   the ground as a third leg** (monitors stand on hind legs and tail as a tripod; it makes the rear-up look strong).
4. Hooks: the forefeet reach forward onto the top, claws over the edge.
5. Hauls: the chest slides forward over the edge (the belly may brush it), the hind legs push.
6. Steps: the hind feet go up one at a time, the hips over the edge; it stands on top and walks on.
7. The tail drags up over the edge last (the tail chain, below).

**Step down**, the mirror: stops at the edge, the head dips to look down, the forefeet reach down the face onto the
lower ground, the chest tips down, the hind legs brake on top, the hind feet step down, the tail slides off last.

**Running away:** no stop; the same move played about twice as fast, a scramble (my pick).

## Minecraft side: one path for the box and the drawing
- The climb is a state of the monitor's own (server), with the step's edge, its facing and its start time synced to
  the clients. While it lasts the climb moves the box along the move's own path (made by the clip maker with the clip,
  a small table of forward and up over time), squared to the edge; navigation waits. No one-tick jump of a block.
- Minecraft sends a mob's place to the players only every few ticks and smooths in between; so the drawing works the
  monitor's place out from the same path and start time itself. The clip and the drawn place can never drift apart.
- Each climb starts and ends standing still, so the clip changes land on standing poses (no leg jumps at the switch).
- Checked before starting: room on top for the body; the top under both forefeet (a narrow step: the start slides
  sideways along the edge, up to half a block, to find it).
- Half-block steps (slabs) stay a plain step; only whole blocks climb.

## The tail: dragged, not drawn by the clip
What is behind the monitor changes (flat ground, another step, water), so no clip can own the tail. It becomes a chain
dragged from the hips (framework, species-neutral, any number of pieces):
- **Each joint is a point held in the world.** The first rides on the hips; each next one is pulled after the one in
  front to keep its length, so it trails along the path the hips took (on turns the tail sweeps after the body
  instead of swinging round as a stiff stick) and eases slowly back toward straight.
- **Its height is a height in the world**, eased toward lying on what is under it as the model's own tail lies on
  flat ground (the base carried, the rest on the ground), never inside a block, each joint bent at most so far. When
  the hips go up a block, the joints behind keep their place and are pulled up after them: the tail drapes from the
  edge and slides up and over; off a drop it falls softly after the hips.
- **On flat ground and walking straight it is exactly the tail the user confirmed:** the chain's shape there is the
  model's rest shape, and the walk clip's side to side wave rides on top of the chain unchanged.
- The hiss, bite, bask, eat, death and swim clips keep their own tail; the chain eases in and out (about 0.25 s).

## What goes, what stays
- **Goes (stage D, once the climb is in):** everything that bent the monitor over land steps: its use of `GroundBend`
  on land (the whole-body lift, the trunk and neck bend, the joint closing on an edge, the edge hinges), `plant`.
- **Stays:** the walk, idle, hiss, bite, eyelid and look work; going into and out of the water as it is today
  (the engine's step 4 redoes it the same way, a move with its own path and the dragged tail, after this).
- The crocodile keeps `GroundBend` until the monitor's new climb is OK'd; then it moves over.

## Stages, each waiting for the user's look
- **A. The tail chain.** Flat walk (must look exactly as now), turns (the tail sweeps after the body), standing with
  the hips on a block and the tail lying down over the edge. The steps themselves still have the old body until B.
- **B. The climb up.** The move, its clip and path, the tail dragged up after it.
- **C. The climb down.**
- **D. Hills and the rest.** Steps one after another (Minecraft hills are staircases), the scramble when running away,
  the old step rules removed, the course before and after, the user tries it in-game.

Checks every stage: the measuring course (`python tools/body_course.py run monitor`), its findings read before the
user sees anything; the side films. Animal-only work: no guard films. Two strikes inside a stage: stop and rethink.

### Stage B as built (2026-11-19; second version after frame-by-frame films, waiting for the user's look)
- **First version (4.4 s):** the in-game films frame by frame (`tools/film/monitor_climb_frames.film`, side, front on
  top, behind, 2 ticks apart) showed it looking straight up like a periscope, climbing as one stiff plank and folding
  its hind legs up under it, slowly, its tail hanging.
- **Second version (the user gave full freedom over the look; my picks): a wall climb, 3.3 s.** A one-block step is
  taller than the monitor's back, so it climbs it as monitors climb a trunk:
  1. A short look (to 0.9 s): neck up, head over the edge, a tongue flick.
  2. It walks in rearing, the tail flat on the ground.
  3. The chest rears first; the forefeet reach forward and up in one arc and hook over the top once the shoulders
     are about level with it (0.75 to 1.3 s). They never rise on the spot (that threw the elbows up over the back).
  4. The hind feet go onto the face half way up, splayed, toes up against it (1.35 to 1.75 s).
  5. The hips rise close to upright (70 degrees) with the front half bent over the top: the spine bends at its three
     trunk joints (waist, ribs, chest; `TRUNK_HINGES`), each turned on its outer edge as the game turns it.
  6. The hips roll over the edge, the hind feet step onto the top (toes flattening as each foot comes over the edge),
     one walking step, stand.
- **The maker:** `_climb_keys(B)`, `_trunk` (the trunk's pieces placed as the game draws them) and `_clearance` (the
  hips lifted if any underside point would go into the step) in `tools/monitor_models.py`.
- **Checked offline** for every size: reach misses at most 1.3 px; nothing inside the step except the smallest
  monitor's jaw (0.7 px at 0.8 s) and a claw tip (1.2 px) for one frame.
- **The game side is as before:** the path in `MonitorClimb.java`, `MonitorEntity.Climb` moving the box,
  `MonitorModel.place` drawing along it, and the clip played on the climb's own clock.
- **Starts** only when it arrives straight (within 40 degrees) at the climb's start; arriving too close or at an angle
  it steps up the old way (stage D: turn and back up to the start instead).
- **Climbing, the elbows and knees bend the way they point standing, raised** (`CLIMB_RAISED`): the rest way lay along
  a reaching foot's line, and the joint flipped round between two keys.
- **After the climb** it stands 0.4 s on top (`CLIMB_REST`) before walking on. The tail drapes down off the edge, its
  tip on the ground, and slides up as it walks.

## The numbers (the balancer check)
- At its size: 4.5 blocks long; the box 0.7 wide at the middle of the body; the snout 2.2 blocks ahead of the box,
  the forefeet about 0.55, the hind feet about 0.45 behind, the tail 2.25 blocks from about 0.6 behind.
- The back stands about 0.75 blocks high, the shoulder joint about 0.5; a foreleg reaches about two thirds of a block.
  So a front foot cannot reach the top from a level body: **it must rear to about 45 degrees**, as the user's idea
  says. Not a style choice: the body needs it.
- At its stroll (about half a block a second) a climb is about 2.5 s; running, about 1.2 s.

## Not known yet (honest list)
- Hills: steps every one to three blocks are common; a pause at every step may feel slow there (stage D shows it).
- Steps closer than its body length: the head may have to lift higher to clear the next one.
- A step only one block wide: a forefoot over air (the sideways slide is my first answer).
- How fast the tail should ease back to straight when it stands after a turn (films decide; real tails stay curved).
