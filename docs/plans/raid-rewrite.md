# The Matabele raid rewrite (planned 2026-11-23)

**Why:** the user, 2026-11-23: the raids are "still completely broken and inconsistent: the march works but the raids
do not". They spent two weeks patching the raid code and moved on to the rivers; the plan was always to have Fable rewrite
it from scratch. The notes had called M8 closed (2026-11-10); that was wrong for the raids, and the notes now say so
(BUGS 306).

**Who builds it:** a fresh session on **Fable** (`claude-fable-5-2`), in its own worktree and branch
(`claude/raid-rewrite`), so the river and animal work on `master` is never touched. Read this plan, then
`docs/DESIGN.md` "Matabele ants and the first fights", then the raid entries in `docs/BUGS.md` (search "raid") before
any code.

## Update, 2026-11-23 (later the same day): one of the three big jobs

The user has much more compute for the project and named this as one of three big jobs (order and shared rules:
`docs/plans/big-three.md`). The design and the steps below stand. What changes:

- **Order:** steps 1 and 2 (find the causes, write the tests) run beside the harvester removal
  (`docs/plans/harvester-removal.md`); they touch none of its files. Step 3 starts from `master` after the removal has
  merged (merge `master` into `claude/raid-rewrite` first), so the new code is never built on harvester leftovers.
- **More compute, spent on proof, not on more code:** step 1's soak is 6 servers for 4 days, not 2, with one helper
  per raid stage reading the `[raid]` diaries side by side, and the papers checked in the same step. Step 6's soak is
  run twice (two seeds). The plan's aim of about 30 tuning numbers and one judge per question does not grow.
- **A fresh review before merging:** when step 5 is done, the full code review's round 11 (`docs/plans/code-review.md`)
  reads the new raid code in a session that did not write it. Its A findings are fixed before step 6's soak.
- **The old raid code is not reviewed** by the full review: it is being deleted.
- **Still wanted from the user, whenever they next watch a raid:** their own list below (what they did, what they
  expected, what happened). Step 1 does not wait for it.

## What we have now (measured 2026-11-23)

- **About 6,500 lines of raid code** in six files, plus about 2,400 lines of raid tests:
  `colony/RaidColumn.java` 2,326 (one column, 9 stages, 53 fields of state), `colony/Raids.java` 1,885 (scouting
  bookkeeping, finds, recruiting, the raid day), `entity/goal/MatabeleScoutGoal.java` 1,032, `colony/RaidsAway.java` 790
  (raids out of sight as numbers, its own copy of the rules), `entity/goal/MatabeleRaidGoal.java` 510,
  `colony/RaidDefence.java` 446 (the termites' side), and `gametest/MatabeleRaidTests.java` 2,404. All under
  `savanna/src/main/java/com/antcolonies/savanna/`.
- **119 tuning numbers** in the species file's `raids` section (`data/antcolonies/species/megaponera_analis.json`:
  `scouting`, `choosing`, `search`, `mound_clue`, `away`, `catch`, `hours_and_sound`, `column`, `carry`, `trails`).
- **More than 100 bug entries mention raids.** The notes show over a dozen fix rounds in one week (2026-11-03 to 11-09),
  each patching what the last one broke. This is the case the rules warn about: a part grown past fixing by patches.

### What goes wrong (from the notes; the user adds what they see)

- **The raid start crash** (BUGS 158, 198, 281, 303): `IndexOutOfBoundsException` in `RaidColumn.<init>` when a scout's
  trail has one point. Still kills a test server in most full runs.
- **Raids to empty spots** (168, 202): the column marches to a feeding place where no termite is now, paces about, comes
  home empty.
- **Stuck crowds that never end** (111): attackers and termite soldiers stuck together in a pit, cut off, the attack
  never over. Raiders left standing at the termites after the raid went home (103). Raiders "stuck" on a tall mound's
  side (166).
- **Inconsistent results**: the whole-chain raid test flakes in half a dozen ways (93, 171, 189, 197, 212): carriers
  missing, ants running ahead of the scout, a trail point left broken, nobody home with prey.
- **Near and away disagree**: `RaidsAway` copies the raid's rules as numbers, so a raid watched and a raid not watched
  can come out differently.
- **The user's own list:** _to fill in when the user next watches a raid (one line each: what they did, what they
  expected, what happened)._

### Why it keeps breaking (my reading of the code, to confirm in step 1)

1. **Too many places judge the same thing.** Whether a find is worth a raid, whether termites are there, whether a stage
   is over, and whether an ant is stuck are each decided in two to four places (the scout goal, `Raids`, `RaidColumn`,
   `RaidsAway`, the raid goal and the fight goal stepping aside). When they disagree, ants freeze or pace.
2. **Waiting on single ants.** Stages wait for the leader, for stragglers, for "everyone out" or "everyone gathered". One
   ant stuck on a ledge holds up the whole raid. Patches then added timeouts, then exceptions to the timeouts.
3. **Too many tuning numbers pulling against each other** (119), the same trap as the march's 15 speed rules on
   2026-11-08.
4. **The termites at the spot are checked at the wrong time.** The raid trusts what the scout saw, sometimes hours
   earlier, instead of asking the termite colony where its workers are feeding now.

## What stays (works, or belongs to other systems)

- **The march** (the user: it works): the stream out of the nest behind the scout, `form`, `march`, `comeOut`,
  `walkedAlong` and the gap casting in `RaidColumn` (about lines 1328 to 1760). Lift it out into its own class
  (`RaidMarch`) unchanged in behaviour; the new raid calls it for both walks, out and home.
- **Scent trails** (`colony/ScentTrails.java`, `/antcolonies raid show`), the player breaking a trail.
- **The fight system** (framework `entity/goal/FightGoal`, `fight/`): the sting, clamps, lost legs, squeaks, the fight
  diary. **Rescue and wound care** (`RescueGoal`). **The prey pile and carrying home** (`PreyPile`, `BringPreyGoal`).
- **The termites' side**: `RaidDefence` (drumming, the seal behind the soldiers), the termite alarm, feeding places
  (`FeedingSpots`, `FeedingGallery`), mud plates mended after a raid.
- **The scouts' walk** (`MatabeleScoutGoal`) stays at first: its last in-game bugs (185, 191, 200) are fixed. Only its
  "is this find worth a raid" judging moves to the one new place (below). If the scouts still misbehave after step 5,
  they are the next rewrite, not part of this one.
- **The raid test bench** (`/antcolonies report`, F7, the black box, replays, `tools/soak.sh`) and the `[raid]` diary.

## The real raid the rewrite is built from

The ants are strict realism (CLAUDE.md). Real *Megaponera analis* (Longhurst and Howse 1979; Hoelldobler et al. 1994;
Frank et al. 2017 to 2018; Yusuf 2026), as the notes record it:

1. **A scout finds termites feeding** under mud sheeting or dead wood, looks them over, and runs home laying a scent
   line.
2. **She recruits at the nest**, and a column forms and leaves behind her within a few minutes.
3. **The column follows her line**, 2 to 3 wide, never stopping to wait for stragglers.
4. **It halts a short way before the termites** and bunches up.
5. **The attack:** majors bite the mud open, minors go in and sting. The paralysed termites are dragged out and pile up
   round the break.
6. **Regroup:** the ants gather at the halting place; majors pick up bundles of stung termites (up to about 10), minors
   1 to 3. Hurt nestmates call and are carried.
7. **Home along the same line**, a little slower laden; the prey goes on the pile.
8. **Raid size and day:** 20 to 500 ants (the game shows at most 50 to 100, the user's 2026-11-08 call), 2 to 4 raids a
   day in the morning and late afternoon, none at night or in heavy rain.

**To check in step 1 (I am not sure of these, so they need the papers, not my memory):** how long the halt, the attack
and the regroup last in real raids; how far raids go (my memory says most within about 50 m of the nest); what happens to
ants that fall behind (my belief: they are left and find their own way home along the trail).

## The new design (the rules to build to)

One raid is **one small stage machine owned by one class** (`Raid`), stepped by the colony brain. Each stage has:

- **one rule to start**, **one rule to end**, and **one deadline** (a stage never lasts past its deadline: it moves on
  with whoever is there);
- **a quorum, never "everyone"**: a stage ends when enough ants are ready (say 3 in 4 of those still in the raid) or
  the deadline passes. Ants left behind are let go and walk home along the trail by themselves (this already exists:
  `Raids.wayHomeOrder`). **No stage ever waits on a single ant**, the leader included: if the scout dies or sticks, the
  next ant in front leads.
- **one diary line** on entering it, with the count of ants and the reason it ended.

The stages: **Muster** (at the entrance) → **March out** (the kept march) → **Halt** (bunch short of the spot) →
**Attack** → **Regroup and load** → **March home** (the kept march, laden) → **Done**. A **Turn home** edge leads
from any stage to March home (dusk, rain, the trail lost, the unloaded edge of the world, the colony attacked).

Other rules:

- **One judge of "termites here now".** Before mustering and again at the halt, the raid asks the termite colony's own
  record of where its workers feed right now (`FeedingSpots`, the danger map). Nobody there at muster: no raid, the find
  is forgotten. Nobody there at the halt: a short look round (the real ants do), then home. No other class judges this.
- **One judge of "worth a raid".** The scout reports what it saw; only `Raid` decides. `Raids` keeps only the list of
  finds and the raid day (how many raids are left today).
- **Near and away are the same machine.** Out of sight the same stages run as numbers: each stage's time comes from
  the distance and the deadline, the catch from the same rule. `RaidsAway` stops having its own copy of the raid; it
  only steps the machine without bodies. Showing a raid when the player comes near puts the bodies at the stage's place.
- **A trail always has at least two points** (the scout's start and the find), built when the scout comes home, so the
  start crash cannot happen.
- **The fight hands back cleanly.** At Attack each raider gets a fight order; Attack ends on its rule (no termite
  stung for a set time near the break, or the deadline), and then every fight order is cancelled at once. No raider can
  stay behind fighting after the raid has moved on.
- **Aim: about 30 tuning numbers** in the new `raids` section, not 119, each with its real source or "my pick".
  Fewer numbers is a goal here, not a side effect.

## Steps

Each step ends with `./gradlew build`, the `raids,soldiers,away` test categories, and a commit and push on
`claude/raid-rewrite`. Full run at steps 3 and 6.

1. **Find the causes before writing code.** Run the existing raid scenes and a short soak on real savanna
   (`tools/soak.sh 6 2`), read every `[raid]` diary line, and write a table: for each failed or odd raid, which stage it
   was in and why. Check the real numbers above in the papers. Fill in this plan's "why it keeps breaking" with what was
   measured, not guessed. Show the user the real-raid picture (the stages above with the checked numbers) and get one OK:
   it decides how raids look in play.
2. **The tests first.** One new whole-chain test per scene the old tests built (flat ground, a tall dead mound, a low
   dead mound, lumpy ground, the ridge and pond, an empty spot, dusk mid-raid, the scout killed mid-march), each
   checking the stage rules above: every stage ends by its deadline, nobody is left fighting, prey reaches the pile.
   **Port every old check that once caught a real bug** (the CLAUDE.md rule: never delete one); list in this plan which
   old checks were ported and which were dropped because they only tested an old patch, and why.
3. **Build `Raid` and lift out `RaidMarch`.** The new stage machine, the march moved unchanged into its own class, the
   raid goal kept thin (it walks where `Raid` says). `Raids` shrinks to finds and the raid day. Full run.
4. **Away raids on the same machine.** `RaidsAway` steps `Raid` without bodies; showing a raid as the player comes near.
5. **Delete the old code and numbers.** `RaidColumn` (all but the march, now in `RaidMarch`), the old judging in
   `MatabeleScoutGoal` and `Raids`, the unused `raids` numbers. Close the BUGS entries the rewrite removes, each with a
   line on why it cannot happen any more.
6. **Prove it.** A 4-day soak on real savanna (every raid reaches Done, none hangs, the colonies eat), the raid guard
   film `r_crowd_space`, and a full run. Then the user watches raids in-game (`/antcolonies scout` near a dead mound
   with living termites near it, in the morning).

**Done when:** in the soak every raid ends in Done within its deadlines, no raid marches to a spot with no termites,
the start crash is gone, near and away raids bring home the same catch for the same find (within dice), and the user
moves on from watching raids in-game without a bug report.

## Rules for the session doing this

- Read `CLAUDE.md` first; ants and termites are strict realism.
- **Two strikes, then rethink.** If a stage needs a fix of its own twice, stop and rethink that stage's rule; never add
  a timeout to cover another timeout.
- No new on/off flags. No second place judging the same thing.
- Keep the branch apart from `master` until step 6 passes; merge only at the user's word. At the end switch `C:\Ant Mod`
  to the branch (the main-folder rule) only if `C:\Ant Mod` has nothing uncommitted.
