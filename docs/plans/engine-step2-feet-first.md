# Animal Maker Engine step 2, rethought: feet first (plan, 2026-11-19)

Not built. The user asked for the rethink as a plan, no code (2026-11-19, after the first tries failed; the "two
strikes, then rethink" rule came from it). Read `docs/ANIMAL-MAKER-ENGINE.md` (step 2 and its first tries) first.

## The old starting idea, and why it failed
**Old idea in one sentence:** the body bends to the ground every frame, and the feet are fixed up after.
The walk clip moved every foot on its own fixed rhythm, as if the ground were flat; the code then bent the body over the
step and pushed each foot up or down to meet the ground under it. A foot only "learned" about the step when its own spot
crossed the edge, so it jumped up at the last moment, after the body had already reared. Every try (ramp length tuned
0.2, 0.11, 0.08; toes reaching 0.3 blocks ahead; the leg swung at the shoulder; the torso raised) patched that idea, so
none could fix it.

**Does the real animal work that way? No.** A monitor (any lizard) climbing a ledge looks at it first, then reaches up
and hooks its front claws over the top, pulls its chest up and over (the belly may drag on the edge), the hind feet push
from below and then step up one at a time, and the tail drags up last. Stepping down, the front feet reach down first,
the chest tips down after them, the hind legs brake, the tail slides over last. **The feet choose where they go; the body
goes where the feet hold it.**

## The new starting idea in one sentence
**Each foot holds its spot on the ground and picks where it lands next; the body is held up by where the feet are.**

This is how the monitor's own clip maker already works on flat ground (`tools/monitor_models.py`: each wrist is put on a
landing spot and the leg's joints are worked out to reach it). The game does the same thing live, on the real blocks.

## The numbers that shape it (the balancer check)
- **Leg reach vs the step.** The front leg is about 14 model pixels at full stretch and the shoulder stands about 11
  pixels up, so a leg reaches only about 1.3 times the shoulder height. A block step is about three times the shoulder
  height (the first tries' films). So **a front foot cannot reach the top of a step from a level body**: the chest must
  rise first. That is the real monitor's rear-up, and in this plan it is not a separate rule: the shoulders rise because
  the next landing is out of reach (part 3). About how high and for how long gets measured in stage B, not guessed.
- **Foot spacing.** Front and hind feet are about a block apart (`LEG_PAIRS` -25 and -3 pixels, at its size), so the
  climb lasts about a block of walking: about 2 seconds at its stroll (the first tries' films). Fits a real lizard's
  careful climb.
- **The walk.** The confirmed walk clip is a trot (left fore with right hind, a touch late), each foot down 62 percent
  of a 1.2 s stride, front feet landing at -27 and leaving at -18.5 pixels (`monitor_models.py`). The new feet use
  exactly these numbers, so **on flat ground the walk the user confirmed must look the same** (no change to a confirmed
  look).
- **The box.** The monitor's box steps up a whole block in one tick (`STEP_HEIGHT` 1). Planted feet are spots in the
  world, so the box's jump moves none of them; the body's drawn height is kept as a height in the world (the first tries'
  `anchorY`, which took the bounce from 0.2 blocks to 0.04, is kept for this).

## The design: four parts, one framework piece
Lives in the framework (`entity/body/`, name to pick when built, e.g. `Footing`), species-neutral: it reads the legs
from the body plan (any number of legs) and each animal's numbers (stride, gait order and timing, how high a foot lifts,
standing height, leg lengths) from its own data. The monitor first; the crocodile moves onto it once the monitor is OK'd.

1. **Feet held in the world.** Each foot has a spot in the world. Down (stance), it stays exactly there while the body
   moves over it: no sliding, whatever the speed. It lifts when its "home" (its usual place under the shoulder or hip)
   has moved more than half a stride past it, in the clip's order (a foot never lifts while its diagonal partner is
   still in the air too long, so the animal never looks unsupported). It lands half a stride ahead of home.
   - **The landing is picked on the real ground:** the top of whatever block is there. Never on a corner: if the
     landing falls within a toe's length of a step's edge, it lands on top if the leg can reach that high, else just
     short of the step (the foot waits at the step's foot until the body brings the top within reach).
   - **The swing goes over what is in the way:** the arc's top is above the highest ground between take-off and
     landing, plus the clip's lift. So the foot rises **before** it reaches the step, as a real foot does, never at
     the last moment.
   - Turning on the spot: homes swing round with the body, so feet step to follow (the old "turning on the spot" jank).
   - Stopping: feet finish their step and stand; starting: feet start from where they stand.
2. **The legs reach their feet.** For each leg the joint angles are worked out from where the foot is (the same "reach"
   maths as the clip maker, two bones plus the foot): elbows and knees bend out to the side as a sprawling lizard's do,
   the foot lies flat on its block, the toes curl over an edge they rest on. Replaces the walk clip's leg keys; the clip
   still moves everything else (the side-to-side swing, the head, the tail wave).
3. **The body follows the feet.** The shoulders sit at the standing height above the front feet, the hips above the
   hind feet; the trunk spans them. **One extra rule only:** if the next landing of a foot is out of the leg's reach,
   its girdle rises (or lowers) toward it, as far as the trunk can bend. That gives the rear-up on a step up and the
   front dipping on a step down, from the same rule. The neck and head lead: the head looks at the ground a body-length
   ahead (it rises at a step before the feet get there; the snout is a block ahead of the forefeet). The tail follows
   the path the hips took (the existing rear chain).
4. **Pace.** The box keeps its speed at first. Stage B measures whether the feet keep up over a step (a foot that
   cannot find a reachable landing in time would be left behind). Only if they cannot: the monitor slows near a step
   (the real one does), as a body speed, never a goal steering it.

## What it removes (removed, not kept beside it)
`MonitorModel.plant` (feet pushed to the ground) and `PLANT`, the land part of the shoulder lift, the walk clip's leg
keys, and from the side branch `LegSwing`, `TOES` and `raise` (not merged). Kept until step 4 (water entry): the legs
folding over deep water and the swim clips; step 4 then uses this plan's "a foot finds no bottom in reach" as its signal,
so it builds on this. Kept for later: the back's S-curve (step 3) rides on top of planted feet, as in a real lizard.

## Minecraft side
- Feet are worked out each frame on the client (smooth between ticks), kept per monitor (bones are shared by every
  monitor drawn), only within the model's 48-block drawing reach; coming into reach, or moved more than 2 blocks in a
  frame (teleport, knockback), the feet are put down fresh at home.
- Ground is read with the blocks' real collision tops (slabs, stairs, snow), as `GroundBend.support` does.
- Cheap: four feet, a few block reads each, a small joint solve; checked against many monitors on screen in stage C.

## Stages, each with the user's look (no stage starts before the last is OK'd)
- **A. Flat ground.** Feet held and stepping, legs reaching them; walk, turn on the spot, stop and start, beside the
  old walk clip. Checks: planted feet slide 0, no foot moves outside its swing, the course's level scenes. The user
  looks at a side film: it must look like the confirmed walk.
- **B. Steps.** Landings on the real ground, the swing over the step, the girdles rising for reach, the head leading.
  The course's `step_up`, `step_down`, `stairs` and the side films (`tools/film/look/` from the side branch). New
  check for this bug: **no foot rises outside its swing, and every swing that ends higher started rising in its first
  half** (catches the "jump at the last moment"). The user looks at the side films.
- **C. Clean up and prove.** The old rules removed, the crocodile moved over, the course before and after, the user
  tries it in-game. Animal-only work, so no guard films (the user's rule).

**Two strikes:** if two tries inside a stage still fail the user's look, stop and rethink that stage before a third.

## Not known yet (honest list)
- How far the chest must rise and whether the belly scraping the edge reads well in Minecraft blocks (stage B measures).
- Whether the box's speed lets the feet keep up over a step (part 4).
- Whether the clip's side-to-side swing and the reaching legs fight each other (stage A shows it at once).
- The cost of the joint solve per monitor in Java (expected small; measured in stage C).
