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

## Step 2, built 2026-11-12: soldiers walk beats where the workers are

- **A post is now a beat** (`Soldiers.Post`, `Soldiers.beatReach`): its middle cell and the way trouble comes from. On it
  a soldier walks a few steps to another cell of the beat (2 blocks round the middle under cover, 1 in the open), pauses
  2 to 6 s looking roughly outward (up to 60 degrees to either side, a new side each pause), and walks on, at 0.7 of its
  walk (`WalkBeatGoal`, was `GuardPostGoal`; the next cell is found by stepping out from where it stands through cells
  of the beat, so no route search that could fail). All my picks.
- **Feeding places get soldiers by day** (`Soldiers.feedingPosts`, kind `FEEDING`, from `FeedingSpots.byDayPlaces`,
  worked out every 5 s): each by-day feeding place a minor first (before any tunnel end), a major once the royal
  doorway and the way in from the shaft are held (before the garden doorways; further feeding places after them), then
  a second minor. Only cells under the colony's cover with a floor count.
- **A soldier with no beat strolls now and then** like the workers (it stood still since 3Q).
- **Not in this step** (honest): no new clips (antenna sweep, jaws, tapping workers); the ring at a break and the
  soldiers shut out by a seal still stand still (step 3); a mud runway's cells get a beat only where the nest counts
  them as inside (not checked in the game); a beat far out that cannot be reached costs a failed route search a minute
  per soldier that tries it.
- **Tests:** `soldiersWalkBeatsAtAFeedingPlaceByDay` (new: a minor and one of three majors on beats at a feeding
  gallery by day, the other two majors at the heart); the two guard tests now check "on its own beat" and that every
  soldier has stood in at least two cells (no statues), in place of the exact cell and facing; `soldiers,raids,away` 57
  of 57 and `colony` 25 of 25 on 6 servers.
- **The measuring scene again, 6 runs: no change, as expected** (it has no feeding place on the colony's record, so no
  soldier has a beat there: 5 real soldiers, the nearest 5 to 7 blocks off, at most 1 or 2 at the break, raiders 0 or 1
  dead, 1 to 3 hurt). Step 2 changes where soldiers are and how they look; the raid's outcome is steps 3 and 5.
- **A warning for step 5, from a scene tried and dropped** (a feeding place on the record, raiders after 30 s; too
  noisy to keep, 0 to 19 raiders dead between runs): when soldiers were at the feeding place in number the raid was
  wiped out (11 soldiers within 4 blocks: 19 of 20 raiders dead), and with 3 or 4 near, 1 to 8 died. So once step 3
  brings soldiers to a trouble quickly, the fight numbers (a soldier's bite against a Matabele's health) need the
  balance pass before the user plays raids, or the termites will be far too strong.

## Step 3, built 2026-11-12: one local alarm replaces the ladder

**What it is now.** The alarm is a short list of *troubles* (`colony/Alarm`). A trouble is a place with a strength, the
distance in blocks it is felt; the strength shrinks from the last time something fed it, and the trouble is over when it
reaches nothing. Three kinds: **felt** (footsteps, a sprint or jump on the nest), **foe** (a termite hurt or killed, an
enemy seen at a hole) and **breach** (a raid breaking a gallery open, a player breaking into the mound: the same rule).
Things happening within 8 blocks of a trouble feed it instead of making a new one; a colony holds at most 8.

- **Soldiers.** The drumming runs outward from the trouble at 1.3 blocks a second. A soldier it reaches answers until
  the trouble is over: for something felt it goes to the nest cell nearest it and drums there; for a foe or a breach it
  takes its own place in a ring round it, facing out, minors first, the opening itself held by the nearest major (a
  minor if none). It lunges at an enemy within 2 blocks and never leaves its place by more than 4.
- **Soldier numbers.** When a foe's or a breach's drumming reaches the nest, every soldier the colony has as numbers
  comes out as a real one (up to a quarter of the real-mob cap). No waves, no tenth.
- **Workers.** A worker within a trouble's reach goes deeper into the nest and does not come out there; foraging stops
  only there (the brain's numbers too). Everywhere else the colony carries on.
- **The seal** (`RaidDefence`) is kept: the gallery is packed 4 blocks back behind the soldiers. It now waits a little
  while a soldier on the nest's side is within 8 blocks of it (so the ones coming get out first); a soldier shut out
  walks a beat by the seal until it opens at night.

**Gone:** the levels 1 to 4, "everyone in" for the whole colony, the raid/player switch, the lookers, the waves' tenth,
level 4 and its soldier boost, the alarm plugging holes.

**My picks:** strengths felt 6, foe 16, breach 16 blocks; fading 0.2 blocks a second (felt over in 30 s, a foe or
breach in 80 s after the last thing that fed it); drumming 1.3 blocks a second (real *Macrotermes* drumming is relayed
at about 1.3 m a second, Hager and Kirchner 2013); merge within 8; at most 8 troubles; the seal waits for soldiers
within 8 blocks. All in `macrotermes.json`, `alarm`. Command: `/antcolonies alarm calm|felt|foe|breach` (a trouble
where you stand).

**Fewer rules, decided while building:**
- **The alarm plugs no hole itself.** The danger memory (`ClosedHoles`) already plugs a hole a termite was attacked
  at; a second place doing the same shut soldiers out and judged the same thing twice.
- **The danger memory was not folded into the troubles.** It is memory over days (where not to forage); a trouble
  lasts a minute or two (who comes now). Two different questions, kept apart.

**Not done, for later steps:** raids out of sight still use the old tenth (`RaidsAway`, step 5); soldiers stay at a
dead foe's place until the trouble fades (about 80 s); number soldiers come out in the nest, not at the trouble; no new
clips (the drumming is the old `head_bang`).

**Measured (the step 1 scene, a daytime raid of 20 on a full colony, 3 runs):** most soldiers within 4 blocks of the
break 8, 6, 7 (was at most 1); soldiers answering 17, 17, 15; raiders dead 1, 11, 3 and hurt 11, 5, 7 (were 0 dead, 0
to 5 hurt); soldiers lost 12, 7, 8; workers stung 5, 7, 6 (were 1 to 18), none of the 6 feeding; sealed about 250 ticks
after the cover broke. **Warning for step 5:** raids are now swingy and the termites can be too strong (11 of 20
raiders dead in one run); the fight numbers have not been touched, that is step 5's balance pass.

**Tests:** `soldiers,raids,away,termitefood,colony` on 6 servers: only two old flakes, both 3 of 3 alone (the neighbours test, BUGS 68, and the away tuft, BUGS 80); full run on 6 servers (38 minutes): every test passed but the old harvester larva flake (BUGS 179, 3 of 3 alone) and the optional raider-going-home replay (3 of 3 alone);


## Step 4, built 2026-11-12: the held mouth

**One rule:** a cell a living soldier holds is a wall to everything it would fight, for as long as it lives and stands
in it (`fight/HeldCells`, in the framework, species-neutral). Two readers, no new judge:

- **The legs** (`LevelCellMap.isOpen`): no enemy's route goes into or through the cell. Its own nestmates pass.
- **The hunters' "is it shut in?"** (`Fights.isShutIn` for an asker, read by `FightGoal.worthChasing`): a termite
  behind the held mouth counts as shut in for the raiders, so nobody plans a walk to it that must fail (a failed
  route search is the dear one). The holder itself is fought from the cell beside it.

**Who holds:** the soldier the alarm gave the opening itself to at a foe or a breach (the nearest major, a minor if
none; step 3's rule, `Alarm.holdsMouth`). While it stands in the opening it never leaves it for a lunge: it bites what
comes within reach of its jaws (`DefendGoal`). Dead, paralysed or out of the cell, and the wall is gone that tick or
within half a second.

**Real:** a *Macrotermes* major plugs a gallery with its head and jaws (phragmosis); *Megaponera* majors go for the
soldiers first so the minors can get at the workers behind. In blocks one soldier stands for that plug: a gallery is
one cell wide, so the opening's cell is the choke point.

**My picks:** a hold lasts 10 ticks unless renewed; the holder is fought from any cell touching its own.

**Not done / limits:** only the opening itself is held (the soldiers in the ring round it do not block); a break made
through a gallery's roof where no soldier can stand in the broken cell has no holder; a second break beside the first
is a second way in until its own trouble has a holder; no new clip (the holder stands with the others' stance).
Guard films not run: the change is only read where a soldier holds a cell on alarm, which no guard scene has.

**Measured (the daytime raid of 20, 4 runs, with the held mouth):** workers stung 3, 9, 5, 1 (step 3: 5, 7, 6, 4);
raiders dead 2, 19, 5, 2; soldiers lost 12, 6, 11, 12; in the earshot scene the mouth was held 20 to 191 ticks.
Still swingy and too hard on the raiders: step 5.

**Tests:** new checks in `soldiersWithinEarshotComeToARaidsBreakTakeTheirOwnPlacesAndBite` (while a living soldier
stands in the opening it is a wall to a raider's legs and its "shut in?", never to nestmates; with no soldier in it,
to nobody; held at least a second); `soldiers,raids,away,legs` 82 of 82 on 6 servers (4 minutes).


## Step 5, part 1, built 2026-11-12: bites hurt, the raid leaves (the user's two picks)

Proposed to the user and picked by them ("these 2 sound good", and "leave them outside till night"):

1. **Soldier bites hurt, they rarely kill.** A minor soldier's bite 1 (was 3), a major's 1.5 (was 4)
   (`macrotermes.json`, `fight.bodies`). A Matabele major (10 health) goes home badly wounded after about six minor
   bites, a minor (5) after three; leg cuts and clamps unchanged. The numbers are my picks. Side effects, told to the
   user: soldiers also hurt chickens and the player less.
2. **The raid leaves once the workers are sealed away or gone.** Soldiers alone no longer keep an attack going
   (`RaidColumn.preyAbout`): 30 s after the last worker in reach the raid picks up what it stung and goes; the soldiers
   shut outside are left till night (the user's pick; real raiders also carry soldiers home, a known departure).

**The measuring scene now ends as a column does** (it was a 5-minute skirmish, which overstated every loss): badly
wounded raiders leave, the attack ends 30 s after the last prey or after 2 minutes, counted from the cover's break.

**Measured (a daytime raid of 20 on a full colony, 11 runs that had a fight):** raiders dead 0 in 9 runs, 2 and 7 in
the others (was 1 to 19); raiders hurt 2 to 11 (target 1 to 5); workers stung 1 to 11, once 33 (target 3 to 6);
soldiers lost 1 to 14, once 19 (target 0 to 2).

**Still off, and why (honest):**
- **Soldiers lost is still high.** Minor soldiers fall to one sting and 6 to 10 of them stand in the raiders' reach.
  Part of it is the scene: its raiders belong to no column, so every one fights every soldier alike, where a real
  column's minors leave soldiers that are not right in their way. Not measured on a real column yet.
- **Raiders hurt is above target** for the same reason (a melee of 15 to 20 soldiers against 20 raiders).
- **One run in 12 never sealed** (the seal was given up after 30 s, `plug_give_up_ticks` times 3, and the raid ran
  its whole 2 minutes: 33 workers stung, 19 soldiers, 7 raiders). Older than this step; to look into.
- **The scene's majors take from 4 s to 3 minutes to find the cover**, so runs differ a lot before the fight starts.

**Next in step 5 (not built):** measure on a real raid column (the whole-chain raid test with these counts); then, if
soldiers still die in numbers, the user's call between tougher soldiers (two stings to fell a minor) and fewer soldiers
coming; the seal given up; `RaidsAway` on the same rule as near; the soak (`tools/soak.sh 2 6`); full run; merge.

**Tests:** `soldiers,raids,away` on 6 servers: all passed but the old mound-ringing scout flake (BUGS 153, 3 of 3 alone) and the fight test's exact bite check, updated on purpose (a major soldier's bite takes 1.5, was 4; then 3 of 3).


## The mud gaps, fixed 2026-11-12 (the user's report while planning step 5; `docs/BUGS.md` 205)

The user: the Matabele mostly walk in without breaking any mud, as branches are not fully covered. Found in the code
and proved with a test (fails 3 of 3 on the old code): only the branch block right over a dug gallery cell was cased,
and the uncovered rest of the branch was a door into it. Now the whole branch is plastered as the first gallery block
under it is dug (`NestWalls.casingGaps`). This comes before the next idea agreed in talk, not built: **soldiers fight
from inside the hole** (they line up in the gallery behind the mouth's holder, and a soldier under cover facing its
attacker cannot be stung, only bitten at by the Matabele majors), which only means something once there is mud to
break. Not known: whether other feeding places (log tops, runways of sheeting) have gaps of their own; the user's
next look in-game will tell.


## Step 5, part 2, built 2026-11-12: soldiers fight from inside the hole (the user: "complete the last step first")

**What it is now, in one rule:** against insects (a raid), soldiers take their places only under cover, in the gallery
behind the opening or the galleries nearest the foe, and a soldier standing at its place is a wall to the raiders and
cannot be gripped, stung or hurt by them: only its armoured head shows. The raiders leave such a soldier alone. They
get at soldiers that do not hold a place: out in the open, on the way to a place, under cover that has just been
broken, or one that clamps on to a leg and is dragged out (the real sacrifice, kept).

**How it got there (measured each time with the measuring scene, 6 to 12 raids):**
1. Places under cover and "no sting from the front": soldiers still all died in half the runs. Cause found with a line
   per lost soldier: a gripped soldier is held still, so it stopped "holding" its cell after half a second and both
   its wall and its shield lapsed. Now a soldier holding its place is never gripped.
2. Bitten at for the hunter's damage instead of stung (what was agreed in talk): the line fell in seconds, two major
   bites a soldier. **My pick, changed from what we said:** no harm at all from the front; the ants cannot crunch
   through a head in a hole. The other way (majors may bite through) is one line to bring back if wanted.
3. The special "holder in the opening itself" (step 4) removed: each soldier stepping up into the open cell was stung
   in turn. Every soldier at a covered place is a wall now (`HeldCells` takes several holders a cell), so step 4's
   rule became simpler, and the raiders' "fight the holder from beside" code went with it.
4. A foe's trouble left beside a breach kept most soldiers, their places jumping with the foe: troubles within the
   merge distance fold into the worst one (`Alarm.foldInto`).
5. Spare places past the first ones were handed out afresh every half second and could be the open cell itself: they
   are kept like any place now and are under cover.
6. Against a chicken or a player nothing changes: the soldiers still stand out in the open round it and lunge
   (`Trouble.insects`; found by the chicken tests).

**Measured at the end:** measured over the last 24 raids of 20 on a full colony: the 10 that broke in cost the termites 1 to 6 soldiers (most 1 to 3; nearly all had clamped on to a leg and been dragged out, or were caught before reaching a place; before, up to 12 to 18), workers stung 5 to 6, raiders 0 to 6 dead and 1 to 14 hurt; in the other 14 the scene's raiders never found the cover. Targets were soldiers 0 to 2, workers 3 to 6, raiders 0 to 2 dead and 1
to 5 hurt: soldiers and workers about there, raiders hurt above it (soldiers biting from cover cannot be answered).

**Raids out of sight** follow it (`RaidsAway`): 6 in 10 of the soldiers come (at most 12, was a tenth), a quarter of
those are lost (was half); the kills and hurts per soldier unchanged.

**Limits and what is not known:** the measuring scene's raiders are no column, and in over half its runs they never
find the cover, so the numbers come from few raids; no soak run was made. The raid stays its full 2 minutes while
workers sit sealed within 10 blocks (older rule). A soldier holding a place cannot be hurt by ants even from behind
after a second break, until the cover over it is gone. The seal given up 1 run in 12 (part 1) was not looked into.
Tests: full run on 6 servers (44 minutes): every test passed but two, the harvester numbers test (BUGS 206, old) and the middle-hole chicken test (BUGS 207, with the minors-bite-a-chicken test, which fails about half its runs on the last commit too).

