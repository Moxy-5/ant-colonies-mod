# The Animal Maker Engine step 4: the Nile monitor into and out of the water (2026-11-19)

The user's ask (2026-11-19): the monitor going into and coming out of the water remade from scratch, the way the climbs
up and down a block were made (`engine-step2-climb.md`, "How stage B was got right"). The climbs, the walk, swim, hiss,
bite and eyelids stay. Fun and good-looking first (it is an animal). Every number below is my pick unless marked.

## The look (the user's words, then the user's rethink)
- Going in: it wades in with the legs walking until each pair loses the bottom, the body settles and floats, the legs
  fold back along the body and the tail starts its swimming sweep; no pop, bob or jump.
- Coming out: the forefeet find the bank first, it hauls itself out, the water runs off the body, the tail is dragged
  out last.
- **The user's rethink (mid-build, after the first in-game film):** the swim pose came on too hard in the back legs.
  "They hang down as they first go into the water, then gradually take full form after a bit of being in the water."
  So every leg that loses the bottom now **hangs** loose under the body, toes drooping, and **folds** for swimming only
  a while after (about a second after it floats). Real monitors and crocodiles do the same: limbs trailing at first,
  tucked once they swim.

## How it is built (as the climbs)
- Each move is made once per size (the five `CLIMB_SIZES`) in `tools/monitor_models.py` (`_water_keys`, `water_move`)
  and written into the animation file and `MonitorClimb.java` (its path table) by `python tools/monitor_models.py game`.
  The game moves the monitor along the move's own path (`MonitorEntity.startMove`, `climbOn`), the drawing follows it.
- Six moves (`WATER_KINDS`):
  | move | when | length | notes |
  |---|---|---|---|
  | `in` | walking into water off a level bank | 6.5 s | **remade 2026-11-22** (see "The walk-in remade"): the body bends down one smooth course into the water, the legs walk on until each pair loses the lip, hang, then fold once it is about fully in |
  | `dash` | running in (speed over `DASH_SPEED`) | 1.9 s | no stop: forefeet reach out, the body drops in past the edge, legs hang, fold from 0.9 s |
  | `drop` | a bank a block over the water's block | 5.1 s | the climb down's course (confirmed) until the forefeet reach the water; they hang, the chest slides in, the hind feet come down the face and let go |
  | `drop2` | two blocks over | 4.6 s | **a dive** (my pick: hanging down a face twice its height looked wrong; monitors drop into water off banks and branches): looks down, forefeet on the lip, crouches, springs, falls head first, the forelegs tucking as it hits (they struck the bottom of a one-block pool hanging), levels out |
  | `out` | swimming out onto a level bank | 4.6 s | legs unfold to hanging, forefeet reach up and over onto the top, it hauls, the hind feet come up out of the water onto the lip, a step on, stands; drips run off |
  | `outup` | a bank a block over the water's block | 4.5 s | rears as it swims the last of the way in (starts 58 px from the face, `WATER_FACE_UP`, so the head clears it), forefeet hook over the top, hind feet from hanging onto the underwater face, step up it, haul, stand |
- Going in a move ends afloat in the swim's first pose, gliding at the swim's speed, so the swim goes on from it; coming
  out it starts afloat in the swim's first pose. Both ends are checked: going in, the first frame against standing and
  the last against the swim; coming out, the reverse.
- The tail is the game's (`TailDrag`): afloat each joint eases to just under the surface (`TAIL_FLOAT`), so it slides
  off the bank into the water and is dragged out after the hips.

## The walk-in remade (2026-11-22, the user's ask)
The user: the old walk-in was janky ("I made it before we have this whole system"), so it was deleted and made new:
**curve down into the water without the back cutting, no snap into swimming form, ease into it once about fully in.**
- **The starting idea:** the trunk (hips, waist, ribs, chest) is laid on one course, a level line that bends down over
  the lip into the water (`_in_course`: level, then a smooth curve to the lip, then a smooth curve down to floating
  height, joined with matching slopes), and the body slides along it at a stroll that eases up to the glide. Each joint
  bends by the course's turn only, so the back is never cut (joint bends at most 7.8 degrees in every size).
- **The belly rides the lip** (6.6 px over the water's block, steepest about 21 degrees): a course that kept the hips at
  standing height until past the edge left the back of the body held up by nothing in the game (a "tent" in the first
  film); a body sliding off a bank rests on the lip.
- **The legs walk** on the module's walk numbers (`WALK_PHASES`, `WALK_REACH`, `WALK_DUTY`, the same as the walk clip):
  each foot steps as long as it has ground (never past 4.5 px back from the lip), lets go when its shoulder or hip has
  passed its last place by its reach plus 3 px, **hangs** (forefeet forward and down, hind feet back and down, `hang_at`),
  and folds into the swim from about 0.35 s before the body is floating (forelegs first, each right side 0.1 s after).
  The swim's tail sweep and the neck and head ease in with the body.
- **The game:** the move file says it starts 50 px back from the face (`IN_EDGE`), and `MonitorEntity.roomToEnter` now
  checks the bank is solid and open all that way back.
- **The tail** (`TailDrag`, framework): a new ceiling at the tail's root: no joint rests higher than the root's own
  drawn height, so the tail no longer arches up from hips sunk below the bank (a test proves it). What is left is
  BUGS 278 (a W in the tail at the lip as the hips go under), the shared tail drag's, not the clip's; fixed the same day:
  over a low lip the tail bridges from its root to the lip like a rope (`TailDrag.lip`, `BRIDGE`), off a high bank it
  still hangs down the face.
- Checks: `python tools/clip_check.py monitor 'water_in_*' --switches` (all five sizes: nothing inside the bank, no
  planted foot hanging, fastest joint turn 53 degrees, no clip change flagged); the course scene `bank_level`; the
  film `tools/film/monitor_water_in_frames.film` from the side, front and behind.

## Rules learned (each from a check that flagged it)
1. **Hang, then fold** (the user): `'hang'` ramps per leg (stand to hang) and `'fold'` ramps (hang to swim); coming out
   the same curves backwards. A leg's fold follows the very curve it folds by, both ways (unfolding by a different curve
   flipped the forearm, 158 degrees in 0.05 s).
2. **A foot letting go lifts off first**: off the lip forward and out, then down; never under the ground it is still
   over (its wrist kept its standing height over the bank or bottom, more as the toes droop; its toes too near a
   ledge's top). Changing the hind foot's timing three times did not fix it (two strikes): the starting idea was wrong
   (a straight slide from standing to hanging passes through the bank), the rule replaced it.
3. **A foot turns only off the surface**: from hanging (toes down) to a face (toes up) the toes swung through the face;
   turned once off it, they clear. A foot flat on a face lifts its claws straight off it (no droop, no curl).
4. **Elbows and knees held up, eased to the swim's own way as the leg folds**: going in a little (`KNEES`, `ELBOWS`
   0.5), the drops and the climbs out as the climbs hold them (`CLIMB_RAISED`). Held while folded, the knee started out
   twisted against the swim's and the foot unfolded a whole turn round, which the game's 4-tick blend into standing
   would have spun.
5. **The swim's angles mixed only into folded legs**: mixed part way into a folding leg, a foot turned the long way.
6. **Quaternions from the biggest component** (`_slerp_turn`): signs read off w alone lost them at half a turn and made
   the right forefoot's swim pose the left's.

## Game-side fixes found by the frame-by-frame films
- **The dash never played**: the running test read `getX() - xo` in `travel()`, before the tick's move, always 0; a
  running monitor stopped at the edge and walked in. It now reads the eased step `tick()` keeps (`stride`).
- **The climb out a block up never started**: Minecraft's route maker cannot step up out of the water onto a bank that
  high, so the route stopped at the last water square and the move check (which reads the route) never saw the bank.
  Afloat, a route ending at the water's edge short of its goal now has the bank square past its end read as its next
  node (`outPastRoute`). The room check also wants nothing over the water it rears out of.
- **The tail's end stood propped up against the bank's face** after the body had slid into the water (about 1.5 s):
  the dragged tail's "ground rising behind" rule held a joint up for a top the rest of the tail no longer reached.
  Ground past the tail's own reach now counts less and less (`TailDrag.BEYOND`, framework; a sharp cut-off made the
  climb down's tip snap, caught by `TailDragTest`). Off a one-block bank the tail now slants over the lip and slides in
  (about 2.5 s, fine). **Off two blocks (the dive) it still stands up the face about a second: BUGS 237, open.** A second
  try (reach up and along) broke the climb down's tail test, so it was put back; two strikes, the new idea (the tail
  slides off once more of it hangs than lies on top) waits for the user.
- **Climbed out a block up, it walked straight back into the water** (the course's `climb_out`): the route it
  followed was the one made in the water, which ended at the last water square (`outPastRoute` reads the bank past
  it); after the move it walked back to that square, fell in with no move and stayed against the face. A move that
  started afloat now has its route made afresh from the bank when it ends.
- **Hiding under the water after a scare, the tail zig-zagged and snapped** (the course's `scared`: the monitor swims
  half a block under, the tail's joints still floated to the surface, each bent its most up from the sunk hips):
  afloat, a joint now lies no higher than a little over the hips (a quarter of the first piece's length, as it lies
  swimming at the surface). `TailDragTest.swimmingUnderTheTailStaysDownWithTheHips`.
- The course's `climb_out` scene and my first climb-out film had the water under the flat world's grass layer (a
  covered channel): the air over it added.

## The offline checker (`tools/water_check.py`, since 2026-11-20 `tools/clip_check.py`)
(Since the Animal Maker Engine step A, 2026-11-20: `python tools/clip_check.py monitor 'water_*' [--switches] [--times ...]`,
the one checker for every animal, reading the moves from the game's files; every check below lives on there, the two
ends against the clips they hand over to as its blend check.) It was
`python tools/water_check.py <in|dash|drop|drop2|out|outup> [size] [times] [views]` (run in a scratch folder): reach
misses, parts inside the bank or bottom, the tail inside the bank (a rough offline drag: the game's is the truth), the
biggest jump of a part, **the fastest joint turn** (degrees in 0.05 s; flagged over 90, the confirmed climbs reach 80;
added after an elbow flip of 280 degrees in 0.2 s moved no part far enough to show as a jump), the body against the
surface, the path's speeds and end height, and both ends against the clip it hands over to.

What is left, on purpose: claws gripping the lip or face by up to 2.8 px (claws dig into a dirt bank), and the smallest
monitor's chin brushing the face for a moment as it rears out (1.2 px).

## Films
`tools/film/monitor_water_<move>_frames.film` (in, dash, drop, drop2, out, outup), each frozen and stepped from the side,
from the water in front and from behind; contact sheets with `tools/film_sheet.py`.
