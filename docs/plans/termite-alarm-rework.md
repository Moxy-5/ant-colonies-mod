# The termite alarm rework (M8.5)

Planned with the user 2026-11-12 ("the alarm system is overall just outdated and doesn't work well and it puts the
termites at a big disadvantage"; "the majors just stand still and guard, I want them all to be walking around, in
general or nearby the feeding post"; "everything you proposed sounds great"). Branch `claude/termite-alarm-rework`.

## What is wrong today (read in the code, 2026-11-12)

1. **Only about 2 soldiers answer a raid.** `Alarm.startRaidWave`: a tenth of the colony's soldiers come, 1 to 12. It
   was written for colonies with hundreds of soldiers. Since honest sizes (a colony at most 200, so about 20 soldiers)
   that is 2, against a raid of 30 to 75 Matabele. `RaidsAway` uses the same tenth for raids out of sight.
2. **By day the soldiers are in the wrong place.** Raids come by day at the feeding spots. Minors go out only at
   foraging time (night, rain); by day their posts are one block before the end of the nest's own foraging tunnels and
   of holes in turn (`Soldiers.posts`), the majors' posts the royal doorway, the shaft tunnel and the garden doorways.
   A feeding spot 40 blocks out usually has no soldier within the drumming's 16 blocks. (Not checked in the game:
   whether a minor ever gets a post at a daytime feeding gallery.)
3. **The statues.** A soldier at its post (`GuardPostGoal`) or at its place at a break (`DefendGoal.stand`) only turns
   to face one block. The majors stand in their doorways all day, every day (the user's report). After a raid the ring
   stands about 3 minutes (level 3's calm time) and the shut-out ones by the seal until night.
4. **One alarm for the whole colony.** One level (1 to 4) and one trouble spot. A chicken at one hole or a raid at one
   far feeding spot stops all foraging (`keepsEveryoneIn`, level 2 or more); two troubles at once cannot both be held
   (the spot moves to the newest); a player's break-in during a raid sets `waveReach` to 0 and the raid's defence is
   gone.
5. **Too many rules judging the same thing.** The alarm level, the raid seals (`RaidDefence`), the closed holes
   (`ClosedHoles`), the danger map and the workers' fleeing each decide "is it dangerous here" on their own; about 15
   tuning numbers (`STAND_LEASH`, `CHASE_LEASH`, `NEAR_ENEMY`, `LUNGE`, `LOOKERS`, `BREACH_REACH`, `RING`, the wave
   numbers...), and a switch between "raid mode" and "player mode" inside level 3.
6. **An out-of-date balance target.** Step 3S aimed at 2 to 8 soldiers stung per raid; of 20 soldiers that empties a
   colony in three raids.

## What is real

- *Macrotermes* minor soldiers go with the working parties and stand at the edges of where workers feed, heads out,
  antennae waving, shifting about; major soldiers are fewer there and mostly near the heart of the nest (both castes
  are found in foraging parties, the minors far more: my reading, not checked against a count).
- The alarm is local: a soldier that feels trouble drums its head on the ground (10 to 20 beats a second); soldiers
  within about 40 cm drum back within 0.3 s and walk toward where it comes from, so it runs along the gallery at about
  1.3 m a second and fades with distance; the workers there run deeper (Hager and Kirchner 2013). The rest of the
  colony carries on.
- Soldiers do not win against a *Megaponera* raid. Their defence is time: a soldier's head fills a gallery, the
  workers get away behind it and seal the gallery, shutting the soldiers out. The soldiers are the raid's cost (most
  hurt raiders lost a leg to one, Frank et al. 2017).

## The steps

Each step runs `soldiers,raids` (and `away` where colony life changes); a full run after step 3 and before the merge.
One in-game test after step 3 (soldiers walking about, the local alarm) and one at the end.

1. **Measure first.** A measuring scene, `TermiteRaidDefenceTests.aDaytimeRaidOnAFullColonyIsMeasured`: a colony of
   200 (180 workers, 15 minor and 5 major soldiers) by day with a pretend watcher, termites feeding in a gallery under
   mud at a tunnel's end, 20 Matabele sent at it. One `[alarm-measure]` line: how many soldiers answered and how soon,
   the first bite, when the gallery was sealed, workers stung and escaped, raiders hurt and dead, soldiers lost. Every
   later step is compared with it.
2. **Soldiers go where the workers are, and walk.** The fixed posts go. Each soldier gets a *beat*: a short stretch
   (2 to 4 blocks) at a place the colony's workers are working, shared out by how many workers are there: feeding
   galleries by day, the open holes and trails at night, the nest's heart. Minors mostly out with the parties; most
   majors near the royal chamber and the gardens, and one major to a busy feeding spot once the colony can spare it
   (the user's ask). On its beat a soldier walks a few steps, pauses, sweeps its antennae, opens its jaws, taps a
   passing worker, and is groomed and fed by the workers. `GuardPostGoal` and `Soldiers.posts`/`held` are replaced,
   not added to.
3. **One local alarm replaces the ladder.** A *trouble* is a place with a strength (something felt, a nestmate hurt,
   a wall broken) that fades on its own; a colony may have several. Soldiers within earshot drum and come, the
   drumming relays outward a few blocks a second and fades with distance, so near soldiers come first and far ones
   later with no "tenth" rule. Workers within it go deeper; only the holes and galleries near it stop foraging. A
   player breaking into the mound is the same rule where most soldiers live. Gone: the 1 to 4 level, the whole-colony
   "everyone in", the raid/player switch, the lookers, the waves' share. Kept: the seal behind the soldiers, the
   shut-out soldiers let in at night (they walk their beat by the seal, no statues). Checked while building: whether
   `ClosedHoles` and the danger map can read the troubles instead of judging on their own (fewer rules).
4. **The held mouth.** A living soldier standing in a gallery's mouth blocks it: raiders must fell it before going
   in, while the workers escape and the seal is packed behind it. A walking-code change (read `docs/BUGS.md` first;
   guard films).
5. **Balance and the raids out of sight.** One pass over the numbers with the measuring scene and a soak
   (`tools/soak.sh 2 6`); `RaidsAway` reads the same rule as near. Full run, in-game test, merge.

## Balance targets (my picks)

Per raid on a full colony: the ants take about 3 to 6 termites (a colony of 100 eats about 5 a day), 1 to 5 raiders
come home hurt, 0 to 2 die, the termites lose 0 to 2 soldiers. A young colony with 2 or 3 soldiers stays fragile; a
full one makes every raid cost something ("weak early, strong late").

## Your calls (answered 2026-11-12)

- **Level 4 goes** (repeated break-ins no longer lock the whole colony in for a day; the attacked place is shut and
  avoided, and the colony raises more soldiers after losses): yes ("everything you proposed sounds great").
- **Majors walk about too**, in the nest or near a feeding spot: the user's ask.

## My picks

- Majors: most stay near the nest's heart (real), one goes to a busy feeding spot when the colony has a spare.
- The drumming's speed and fading are set from Hager and Kirchner's numbers on the ground scale (a block a metre),
  tuned in step 5.
- The soldier boost after losses stays (a colony short of soldiers raises more, as "on demand" already does).

## Measured (step 1, 2026-11-12, the old alarm; 6 runs of the measuring scene)

Colony of 186 workers, 14 minor and 5 major soldiers; 20 raiders (10 majors, 10 minors); 3 minutes.

- **Soldiers called by the rule: 2** every run (the tenth of 19).
- **Soldiers real when the raiders came: 5** (2 minors, 3 majors), the nearest 6 to 7 blocks from the feeding place.
- **12 or 13 soldiers "alarmed", but never more than 1 within 4 blocks of the break**, in any run.
- **Sealed 40 to 55 ticks (2 to 3 s) after the cover broke.** The cover broke 4 to 38 s after the raiders came.
- **Raiders: 0 dead in every run; 0 to 5 hurt (0, 1, 1, 2, 3, 5); one leg lost in six raids.**
- **Soldiers lost: 1 in five runs, 4 in one.**
- **Workers stung: 1 to 18 (1, 6, 14, 14, 15, 18)**, almost none of them the six at the break (0 or 1): which
  workers these are and where they were stung is not known yet (to look at in step 2: likely real workers elsewhere in
  the tunnel or coming out of the numbers by the opening).

Against the targets: the raid costs the ants next to nothing (target 1 to 5 hurt, 0 to 2 dead), and takes up to three
times the 3 to 6 termites it should. A new ant's health settles in its first ticks, so the scene counts "hurt" as health
lost after tick 20.
