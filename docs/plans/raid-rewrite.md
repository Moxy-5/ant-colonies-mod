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
- **A fresh review before merging:** when step 5 is done, the full code review's round 12 (`docs/plans/code-review.md`)
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

### Why it keeps breaking (measured in step 1, 2026-11-23)

**How it was measured.** `tools/soak.sh 6 4` on this branch (old code, real savanna, six seeds, 4 game days each, the
pretend player poking at the raids), then every `[raid]` diary line sorted into one record per raid with two small
scripts (kept with their output in the sessions' shared notes folder: `raid-rewrite-soak-by-stage.txt`,
`raid-rewrite-soak-per-raid.txt`). The old raid tests were run too (`raids`, 6 servers, then every test on every server).

**The soak in numbers.** 46 raids with real ants were started; 32 ran to their end, 14 were cut off (below). No server
crashed and none hung; the start crash (BUGS 158, 198, 281, 303) did not show in 46 starts (it needs a find right beside
the nest). Rules broken: 18 not-stuck, 11 raider-stuck, 1 home-by-night, 1 prey-ends.

| | Watched raids (real ants), 32 finished | Raids out of sight (numbers), 212 home |
|---|---|---|
| Came home with no termite | **14 of 32** | **0 of 212** |
| Termites carried, a raid | middle 1, mean 3.5 (only 6 raids brought 7 or more) | mean 5.5 |
| Raid size | 9 to 69 ants, middle 27 | mean 39 |
| How far | 27 to 90 blocks, middle 54 | mean 62 |

So the same colony raiding the same land does well when nobody looks and badly when somebody does. This is the
"inconsistent" the user sees, measured.

**How long each stage lasted** (seconds of play at normal speed; a game hour is 50 s):

| Stage (old name) | Shortest | Middle | Longest | What that looks like |
|---|---|---|---|---|
| Forming at the nest | 0 | 4 | 9 | The column is gone almost as the scout arrives |
| March out | 12 | 33 | 77 | About 1.8 blocks a second; fine |
| Gathering (the halt) | 0 | 2 | 15 | No visible halt: 2 s |
| Attack | 28 | 30 | 120 | 30 s is "nobody found, give up"; 120 s is the cap, hit in 5 raids |
| Collecting | 0 | 2 | 60 | |
| Spreading out (empty spot) | 10 | 30 | 40 | In 9 raids |
| Regrouping | 0 | 4 | 20 | |
| Whole raid | 93 | about 170 | 298 | |

**Every failed or odd raid, by the stage it went wrong in** (server and game clock of each in the per-raid file):

| Stage | How many | What happened | Why (read in the diary and the code) |
|---|---|---|---|
| **The scout's find** (before the raid) | 15 of 32 raids went to a find "worth 15"; they brought 11 termites between them, 9 of the 15 none | 25 to 66 ants march 30 to 84 blocks and meet 0 to 4 termites, often only soldiers | `MatabeleScoutGoal` scores a find as 3 x (termites seen, or termites *assumed hidden* at a feeding place). "Worth 15" is a mud-cased branch with **no termite seen**. Finds worth 45 and more (termites really counted) brought 12 to 23 in 4 of 6 raids |
| **Muster** | 11 of 30 raids set out 8 to 67 game hours after the scout last looked | Old remembered finds raided again with nobody checking | Nothing asks "are termites there now" when a watched raid starts. The away raids do ask the termite colony (`keepsOff`): they turned away from empty spots 281 times in this soak, which is why none came home empty |
| **Muster / any stage** | 14 of 46 raids never ended | The server restarted or the land unloaded in mid-raid; the raiders were left "outside with nothing to do" and walked home one by one; twice 26 to 30 of them stood still over 3 minutes first (raider-stuck) | The column is not saved. A reload simply drops the raid |
| **March out** | 1 raid of 38 lost 26 of its 28 ants | At one place 87 blocks along, each ant in turn made no headway for 15 s and was sent home "stuck", until the leader was the last one and the raid ended "lost from sight" | The march's place for an ant lay 2 blocks up beside the trail there; the per-ant "no headway, go home" rule then emptied the column one at a time. The march is good but not perfect (1 of 38) |
| **Halt** | all | The halt lasts 2 s; nobody sees it | The rule ends it as soon as three quarters are "there", and the stream arrives nearly together |
| **Attack** | 26 of 33 attacks ended at "no termite about the spot for 30 s" | The attack stops while ants are still stinging: 240 termites stung in all, 111 carried | **Two judges.** The stage asks `RaidColumn.termitesAbout` (workers only, in the open, within the collect reach of the find); the ants' own fight goal picks its foes by another rule (soldiers too, under cover, 9 to 15 blocks off). One says "over" while the other fights on |
| **Attack** | about 10 raids | The whole column (up to 66 ants) meets 1 or 2 termites, often soldiers, and every ant goes for the same one | Each ant picks its own foe by nearness; no sharing out shows in the diary (not read in the fight code: that system stays) |
| **Collect** | 4 raids stung 8, 13, 22 and 29 termites and carried **none**; 1 stung termite lay past its paralysis (prey-ends) | The stung termites are left on the ground | Collecting looks within 10 blocks of the find; the ants had chased the termites farther off, and went on stinging after collecting was over (my reading of the diary; the code's reach is `carry.collect_reach`) |
| **Spread out, regroup** | 3 raids | Ants still sting, lose grips and die while "regrouping"; one ended 37 blocks off the trail | Fight orders are not all cancelled when the stage moves on, and nothing keeps a raider near the spot |
| **March home** | 8 raids lost 1 to 18 ants "stuck, no headway" (33 ants in all); 6 raids "no headway home: the line breaks up" | The column home falls apart at its start; ants are sent home alone | They start the walk home from wherever the fight left them, far off the trail ("0.0 along, 37 off"), and the 15 s rule fires before they reach it. In one raid the 18 stuck were the carriers' half of a 36-ant column |
| **Done** | 1 raid "nobody left in it at homing" | Ended with everyone already let go | The same |

**The old tests do not see any of this.** All 15 raid tests passed (one run on 6 servers); run on every server at once (90 runs) 88 passed, the 2 failures both the known scout flake of BUGS 164 ("no scout noticed the living mound yet", passed alone 3 of 3). They build their own scenes
with termites put at the spot, so the two big causes (finds with no termites, raids on old finds) cannot happen in them.
Step 2's tests must be built from the soak's cases, not only from the old scenes.

**What this says about the four guesses that stood here:**

1. *Too many places judge the same thing:* **confirmed, and it is the main cause.** "Are termites here" is judged four
   ways: the scout's score (counts assumed termites), the attack's own rule, the fight goal's choice of foe, and the away
   raids' question to the termite colony. Only the last one is right, and only raids nobody watches use it.
2. *Waiting on single ants:* **not what breaks raids today.** The old patches removed the waiting; what is left is the
   opposite fault: a 15 s "no headway" rule that throws ants out of the column one by one (59 ants in 9 raids) and stages
   so short (the halt 2 s, forming 4 s) that the raid has no shape to see.
3. *Too many numbers:* the stage times above come from a dozen limits (30 s quiet, 120 s cap, 20 s regroup, 15 s
   headway, 60 s out of sight, 10 s home headway) that fire in turn; **confirmed** in the sense that each odd raid above
   ends on one of them.
4. *Termites checked at the wrong time:* **confirmed** (11 of 30 raids on finds 8 to 67 game hours old; half the raids
   on finds where no termite was ever seen).

**New from the soak (not guessed before):**

- A raid is lost at every reload (not saved). The new machine runs as numbers too, so it is saved like an away raid.
- Stung termites are not the raid's own list: what one stage stings another cannot find. The raid must keep one list
  of what it stung, and the pile by the break is where they go.
- Nothing keeps a raider near the spot. A leash (the fight ends for an ant that leaves the spot's reach) is needed.
- The scout's score must count termites that are there, not feeding places.

## The real numbers, checked (step 1, 2026-11-23)

What could be read in full: Frank et al. 2017 (*Science Advances* 3:e1602187) and the start of Frank, Hoenle and
Linsenmair 2018 (*J. Exp. Biol.* 221:jeb174854; its results only through a machine summary). Wikipedia's *Megaponera*
page was read for what it cites from Longhurst and Howse 1979, Hoelldobler et al. 1994 and Yusuf 2010. **Not readable
(paywall or blocked):** Longhurst and Howse 1979 itself, Hoelldobler et al. 1994, Frank and Linsenmair 2017 (both
papers: only search snippets), the Dja (Cameroon) study, Frank's thesis (too big to fetch). Numbers from those are
marked "second hand".

| Question | What the sources say | How sure |
|---|---|---|
| Recruiting at the nest | 60 to 300 s from the scout's return to the column leaving (Hoelldobler 1994, second hand) | fairly |
| How far raids go | Middle about 10 m in Comoe (21 and 63 raids, Frank 2018, summary); "up to 50 m" (Frank 2017, read); mean 22 m in the Dja (snippet); columns up to 95 m (Longhurst and Howse 1979, abstract) | sure of the range, not of one typical figure |
| Marching speed | 1.9 cm a second in grass, 3.4 on open ground; the walk out a middle 7 minutes, home 7.6; the same path both ways (Frank 2018, summary) | fairly |
| The halt | 20 to 50 cm short of the termites the column stops and **bunches up in a circle round the scout until all the ants have arrived**, then rushes in in open order (Longhurst and Howse 1979, second hand) | fairly; how long it lasts is **not stated anywhere I could read** |
| The attack | 5 to 30 minutes (2 to 65 in Kenya, 15 in the Dja, 5 to 15 in Comoe; Yusuf 2026, read earlier, in DESIGN); "rarely longer than 4 to 5 minutes" (Frank and Linsenmair 2017, snippet only) | the range is sure; the sources disagree on the typical length |
| After the attack | The ants gather at the place where they halted, majors holding the termites, and the column forms again; they wait so that all gather before leaving (Frank 2017, read); after the column begins to form some ants go back to the hunting ground for another 1 to 2 minutes (Frank and Linsenmair 2017, snippet) | fairly; the length of the wait **not stated** |
| Ants that fall behind | Hurt ants "were never observed to return alone" in the wild: they call and are carried; made to walk alone 32 percent died (spiders mostly), healthy ones 10 percent; 6 fatally hurt ants in 53 raids were left at the spot (Frank 2017, read). **Healthy stragglers: no source says.** My belief that they follow the trail home stays a belief | unsure for healthy ants |
| The scout's part | The scout leads from the front; all ants lay trail on the way out; **without the scout the others cannot follow its trail** (Yusuf 2010, second hand) | fairly: so "the next ant in front leads" in this plan's design is **not real** on the way out |
| One raid, one spot | A raid is a single event; ants do not go back on their own to the same site (Longhurst 1979, second hand) | fairly |
| Raid size, day, hurt | 200 to 500 ants; 3 to 5 raids a day, 6:00 to 11:00 and 15:00 to 19:00; a middle 3 ants carried home hurt a raid, none in 1 raid in 9 (Frank 2017, read) | sure |

**What the real numbers change in the design below (my recommendation, for the user's OK):**

- **The halt waits for the column's tail, not for a quorum of three in four.** That is the real rule and it is what
  makes a halt visible. The deadline still guards it (a stuck ant never holds the raid).
- **If the scout is lost on the way out, the raid turns home** (the others cannot go on without her). On the way home
  anyone leads: the whole column laid that trail.
- **The game's raids are long by real standards** (middle 54 blocks against a real 10 to 22 m): that comes from how far
  apart mounds and feeding places lie (the balancer rule), not from the raid code, and the rewrite does not change it.

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

**Checked in step 1:** see "The real numbers, checked" above (what was read, what is second hand, what stays unsure).

### The raid in play (shown to the user 2026-11-23, waiting for their OK)

One scale for every stage, so a raid keeps its real proportions: the real walk out takes a middle 7 minutes and the
game's takes about 30 s, so **a real minute is about 4 seconds of play**. Lengths in play are my picks from that.

| Stage | Real | In play | Ends when | Never longer than |
|---|---|---|---|---|
| The scout's look | looks the termites over without touching them | counts only termites that are there now | she has counted | her trip's time |
| Muster at the nest | 1 to 5 minutes | 5 to 20 s: ants pour out and bunch at the entrance round the scout | the called ants are out | 20 s, then it leaves with who is there |
| March out | middle 7 minutes, same path both ways | as now (the kept march), about 2 blocks a second | the front reaches the halting place | the march's own limit |
| Halt | stops just short, bunches in a circle round the scout till the tail is in | 5 to 10 s, a real bunch 3 to 4 blocks short | the column's tail has arrived | 15 s |
| Attack | 5 to 30 minutes, often 5 to 15 | 20 to 120 s: majors bite the mud open, minors go in and sting, the stung are dragged to a pile by the break | no termite stung or gone for near the break for 15 s | 2 minutes |
| Regroup and load | gather where they halted, majors holding the termites; a few go back for 1 to 2 minutes | 10 to 20 s: back to the halting place, the pile picked up, the hurt picked up | the pile is empty or every jaw is full | 30 s |
| March home | middle 7.6 minutes | the kept march, a little slower laden | the front is home | the march's own limit |

Also in the picture: nobody at the spot when the scout checks again at the halt means a short look round (10 s) and home
empty, and that find is forgotten; a raid goes only to a find where termites were counted, and the termite colony is
asked once more at muster; the scout lost on the way out turns the raid home; a raider that leaves the spot's reach
stops fighting and comes back; a raid lives through a reload.

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

## Step 2 as built (2026-11-23): the tests, written first

The user's OK on the raid in play: 2026-11-23 ("Yes, build to it"), the halt waiting for the column's tail and the raid
turning home when its scout is lost on the way out included.

**The file:** `gametest/MatabeleRaidChainTests.java`, ten scenes on one driver, in their own category `raidchain`
(`ANTCOLONIES_TESTS=raidchain tools/run_game_tests.sh`) and out of full runs (`TestFilter.skipInFullRuns` in
`SavannaTests`) until step 3. They name neither `RaidColumn` nor the new `Raid`: they read the colony's `[raid]` diary
from its black box, the insects in the world, the prey pile and the watch rules, so they need no change when the old
code goes. Step 3 moves them into `raids` and takes the skip away.

**The diary the new raid must keep** (the contract; the top comment of the test file has it in full): one line on
entering a stage (`[raid] halt: begins, N ant(s)`), one on leaving with the count and the reason (`[raid] halt: ends
after S s, N ant(s): the tail is in`), a turn home as a reason starting `turns home:`, the last line `[raid] done: N
home, C termite(s) on the pile, ...`, and `[raid] no raid to X: no termite there now` when the check at muster says
no. Stage names: muster, march out, halt, attack, regroup, march home, done.

**The rules every scene checks:** stages in order, each once, none skipped unless the raid turned home; none past its
deadline (muster 20 s, halt 15 s, attack 2 minutes, regroup 30 s, a march a second a block plus half a minute; a hung
stage fails at once with the story so far); a halt of 3 s or more; no raider fighting more than 16 blocks from the spot
in the attack (the leash); nobody fighting 3 s after the attack; no stung termite left on the ground; nobody outside or
holding a fight order at the end; no watch rule broken.

| Scene | From | What it adds |
|---|---|---|
| Flat ground | the old flat and chain tests | prey on the pile |
| Tall dead mound, low dead mound | the old mound tests | 120 ants up the chimney, every stage in time |
| Lumpy ground | the user's raid of 2026-11-08, in small (steps and holes laid by hand) | |
| Ridge and pond | the old ridge test | the scout's own line |
| Empty spot at muster | the soak (15 raids to finds with no termite), BUGS 168, 202 | no raid at all, the find forgotten, the diary says why |
| The termites leave on the way | the old empty-raid test | halt, a look of 10 s at most, home empty, the find forgotten |
| Dusk at the spot | R7 (no old test) | turns home, takes what it stung |
| The scout killed on the march out | the plan's list; the real rule from the papers | turns home, never attacks, the find kept |
| Termites in the open that run | the soak (240 stung, 111 carried; a chase of 37 blocks) | all stung carried, the leash |

**Run once on the old code (expected to fail, and they do, each for the reason it should):** nine fail with "no raid
mustered yet" (the old raid ran in every scene and brought termites home, so the scenes are sound, but it keeps no
stage diary), the empty-spot scene with a raid of 30 ants attacking a spot with no termite. **Not proven yet:** the
checks behind the first one (deadlines, the leash, stung against carried) have never run against a raid that keeps the
diary; step 3 will show whether any is too strict (the 4-box rule then).

**Old checks ported** (as a rule in every scene, or a scene of its own): nobody left standing at the termites after
the raid (BUGS 88, 103); no stage hangs (87, 111, the chain test's limits); nobody standing about outside at the end
(93); the raid brings termites home and they reach the pile (189, 91's "majors bit nothing open" shows as no prey);
an empty spot counts against the find (168, 174, 202; stricter now: no raid, or one short look); the attack does not
run to its longest time for hidden termites (review 2, finding 3: now the deadline and "nobody fights after"); the
raid sets out from a tall and a low mound with its recruits (101, 141, 166: now "muster in time and every stage after");
no watch rule broken in the whole chain (the bench, B1).

**Old checks that stay in `MatabeleRaidTests` for now and move in step 3** (they need hooks into the raid itself, which
the new `Raid` and `RaidMarch` will give; none is deleted): the stream march's speed, width, crowding and personal
space (BUGS 127, 169, 171, 178, 182); the user's own lumpy savanna put back from its report (193, 194, 195); the gap
in the trail cast across and mended (R4, 197), the wide gap and the faded trail; a raider out of sight 10 s kept
(finding 1); the hungry recruit stays (finding 3); a carrier killed on the way home drops its termites (finding 18); the
hurt raider calls, is carried and licked (step 5); the limper and the badly wounded go home along the trail; raiders
called to defend the nest while mustering rejoin (finding 4); the raid's size for its find; food first for minors
(a fight rule); the bench's own test (a hanging raid caught and reported); and the six scout tests, untouched (the
scouts stay).

**Old checks dropped, and why:** "half the column bunched before the attack" (`gatheredAtAttack`) and "the empty raid
must go through the spreading stage" test the old stages themselves; the halt rule and the short look replace them.
"If the scout dies the nearest ant leads on" is not real (the papers) and is replaced by the turn-home scene. The
ribbon checks were closed with the ribbon (BUGS 187).

**For step 3, from writing the tests:** the one judge of "termites here now" must count the termites that are there
as bodies when the land is loaded (what a player sees) and the termite colony's record otherwise; the old scenes'
termites belong to no colony, and a judge that only asked a colony would call every one of them empty.

## Rules for the session doing this

- Read `CLAUDE.md` first; ants and termites are strict realism.
- **Two strikes, then rethink.** If a stage needs a fix of its own twice, stop and rethink that stage's rule; never add
  a timeout to cover another timeout.
- No new on/off flags. No second place judging the same thing.
- Keep the branch apart from `master` until step 6 passes; merge only at the user's word. At the end switch `C:\Ant Mod`
  to the branch (the main-folder rule) only if `C:\Ant Mod` has nothing uncommitted.
