# The termites onto the motion library (B11 of the animation upgrade review)

Planned with the user 2026-10-18 (session from the animation upgrade work). Builds on the body plans and the gait maker
(`docs/plans/body-plans-and-gaits.md`, branch `claude/nostalgic-engelbart-1cf92f`, commit 23cfb2b), so this branch starts
from there. **Built 2026-10-18 after the user's go** (what was built and the picks: `docs/HISTORY.md`, "Termite walks (B11)"). Changes from the plan while building: the queen got her own clip file too; the lab's centre of weight was fixed (it counted overlapping and slanted boxes several times, which put the queen's balance line far off); the major soldier also needed its front feet 0.6 units further back and its own wider turn.

## The real termite gait (what the plan is based on)

- **No one has measured a termite's leg pattern yet.** A 2026 study timing ten termite species (bioRxiv
  10.64898/2026.03.15.711937) says a force plate for termite gait "is underway, but this remains an outstanding research
  task". So the tripod for Macrotermes is the best-supported guess, not a measurement.
- **The best evidence is the cockroaches'.** Termites are social cockroaches (they evolved from wood-eating cockroaches
  like *Cryptocercus*). Cockroaches walk an alternating tripod at all but the slowest speeds; below about 3 steps a
  second the steps run as a wave from the back legs to the front with five feet down (Pearson and Iles 1973; Delcomyn
  1971; Kozacik 1981). Every six-legged walker measured follows the same back-to-front rule (Wilson 1966), which is the
  gait maker's rule. So: the tripod at walking speed, looser when slow, as for the ant.
- **Speed:** termites walk slower than ants for their size: mostly 1.8 to 3.6 body lengths a second, ants 3.3 to 5.2
  (the same 2026 study). Ours walk faster (worker 5.4); see "Later" below.
- **Blind walkers:** termites keep touching the floor and the tunnel walls ahead with their antennae, and those touches
  steer them (tunnel studies on *Coptotermes* and *Reticulitermes*, e.g. J. Insect Science 12:64, 2012, and Florida Entomologist 96:3, 2013).

## What the lab measured today (2026-10-18)

`python tools/anim_lab.py check <model> walk --anim <file>`:

| model | feet slide | stride | steps a second at game speed | balance |
|---|---|---|---|---|
| termite_worker | 373 to 382% of the walk speed | 0.14 body lengths | 38 | fine |
| termite_king | 350 to 356% | 0.15 | 16 | fine |
| termite_queen (small) | 350 to 356% | 0.15 | 11 | outside the feet 69% of the cycle |
| termite_minor_soldier | 375 to 380% | 0.13 | 41 | outside 23% |
| termite_major_soldier | 335 to 340% | 0.14 | 18.5 | outside 75% |

A trial with the motion library (scratch script, not kept): the worker's legs reach a sweep of 1.8 units with the
feet exactly planted (0.00 units off) and 2.2 with 0.14 off; the king's 2.2 exactly. The major soldier stays outside its
feet 60% of the cycle even with its front feet planted 1 unit forward (its legs then miss by 0.14), because its legs sit
too far back under the huge head.

## The user's decisions (2026-10-18)

1. **Walks now, speed later.** The new walks are made for today's game speeds. Bringing workers and soldiers down to a
   real speed (about 3 body lengths a second) is its own step right after, planned with the user, because it changes
   foraging trips and the game tests.
2. **The soldiers' legs move forward** on the placeholder models, under the head's weight, so the balance check passes.
   The new model pictures are shown to the user before it goes in.
3. **Walking antennae tap the ground ahead:** held low and forward, the two tips touching down in front by turns, in
   place of today's small wiggle.
4. **All the termites at once:** worker, minor and major soldier, king, and the queen while she is small enough to walk.

## Steps

1. **The motion library for any waist.** `ant_motion.walk` and `turn` put the gaster's sway on the waist bones of the
   body plan (`waist`, with their shares) instead of the fixed names `petiole` and `abdomen`; the termites' waist is
   their `abdomen`. Proof: every ant file the tools write is byte for byte the same.
2. **The soldiers' bodies.** In `tools/termite_models.py`, the soldiers' thorax and legs sit further forward under the
   head (the head, jaws and abdomen unchanged), until `ant_motion.balance` keeps the centre of weight inside the feet
   through the whole slow and normal walk. The preview pictures (`references/termites/preview/`) are shown to the user.
3. **The walks.** In `tools/termite_models.py`, made with `ant_motion.walk` from each model's body plan (as
   `tools/ant_models.py` does for the ant): `walk_slow` (the loose tripod, `gait(lag=0.58)`), `walk` (`gait(lag=0.53)`)
   and `walk_fast`, each with its stride, swap and speed-band notes. Sweeps as long as each model's legs reach with the
   feet planted (about 1.8 units for the worker), duty factors as the ant's (0.68 / 0.62 / 0.55, the middle legs
   longer); the speed bands from each caste's walking speed in body lengths a second. The small queen's hind feet
   planted further back so her bigger abdomen stays over her feet. Numbers are my picks, to confirm.
4. **Turning on the spot.** `turn_r` and `turn_l` for every termite, made with `ant_motion.turn`.
5. **The tapping antennae.** A termite walking-antennae track: held low and forward, the tips touching down ahead in
   turn, the whip following. Played by the head's player, as for the ant.
6. **One clip file for each model**, so each stride note fits its own size: `termite_worker`, `termite_king` (the queen
   keeps using the king's: her front half is the king's), `termite_minor_soldier`, `termite_major_soldier` (each with the
   soldiers' idle, `head_bang` and `bite`). `client/ClientEvents` points each renderer at its own file. The idle, peek
   and freeze clips stay as they are.
7. **The game.** The turning clips are played for any model whose file has them (today `hasWorkClips` turns on turning,
   carrying, cleaning and digging together, and it is false for termites); the turn's reach (how far the outer feet stand
   from the middle, today the ant's 5.8 units, `TURN_REACH`) comes from a note in the turn clip. The walks for each speed
   already work for any file that has them (`AntAnimationController.gearFor`).
8. **Guard scenes** for the new bodies: the major soldier walking and turning, the king walking slowly (both in
   `tools/film/guard/`), next to the worker's `f_termite`.

## Proof (before asking the user to test)

- `python tools/anim_lab.py check <model> --anim <clip file>` for each termite: no foot slides, no seams, no clipping,
  the balance line inside the feet for the slow and normal walks.
- `python tools/ant_motion.py` (self-test) and the ant's tool output byte for byte the same (step 1).
- `./gradlew build` (with `BodyPlansTest`).
- `python tools/guard_films.py run termite` (and the new scenes): every check passes; the changed pictures looked at,
  then approved with the user.
- Game tests: `legs` (the client change in `AbstractAntEntity` touches walking only on the client, so no other category).

## How the user will test it

Summon each termite (worker, both soldiers, king, a young queen) on flat ground and on a mound's ramps; watch the feet
from close up: they should stay put while the body moves over them, three at a time, and the antennae tap the ground
ahead. Make one turn on the spot (stand still and move round it): it should step round, not skate.

## Later (not in this plan)

- Real termite speeds (the user's decision 1): its own step, planned with the user.
- Termite digging, carrying and cleaning clips (their `hasWorkClips` part).
- A hot walk is not needed: Macrotermes forage at night or under mud sheeting.
