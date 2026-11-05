# M8: Matabele ants and the first fights (plan)

Planned with the user 2026-10-18 (no code yet). Every decision is in `docs/DESIGN.md`,
"Matabele ants and the first fights". Starts after brain step 4g is finished.
Each step is tested in-game by the user before the next; the fine details of each
step are planned with the user at its start (sizes in numbers, health, damage,
timings: all "my pick, to confirm" until then).

## The real animal (Megaponera analis, Erik Frank's studies)
- Majors about 16-18 mm, minors about 5-10 mm, few in between; colony 400-2,500 (100-2,300 by Yusuf 2026, R1);
  one wingless (ergatoid) queen; winged males.
- Eats only termites. Lone scouts find termites feeding under mud cover, lay a scent
  line home and lead a column of 200-500 back; 2-4 raids a day, morning and late
  afternoon, usually within about 20 m, at most about 50 m.
- Majors break the mud, minors go in and sting (paralyse); majors carry stacks of
  up to about 10 termites home.
- Hurt ants call for help by smell; nestmates pull termite soldiers off and carry
  them home; lightly hurt tuck their legs in, badly hurt flail and are left.
- Wound care by any nestmate (licking, a germ-killing gland liquid): infected wounds
  kill about 80% untreated, about 10% treated. Ants walk on 4-5 legs after losses.
- Nests in old termite mounds, under logs, in the ground; shallow; dig little;
  move house now and then; new colonies by splitting.
  (Yusuf 2026, R1: they build no nest, they modify spaces other animals made, about
  0.7 m down; one brood chamber, two or more entrances; move every 2-3 months.)

**Before step 1: M7.5, giant savanna trees** (user, 2026-10-18; `docs/DESIGN.md`,
"Giant savanna trees"): the fallen hollow giants are the Matabele log homes.

## Steps
1. **Dead mounds and the ant.** In pieces (planned with the user 2026-10-24, decisions in
   `docs/DESIGN.md`, "Dead mounds, details"), each tried in-game: **1a** dead mounds (the
   colony's death leaves the mound; spires crumble in days, the body to a grassy hump in
   20-40 days; *Xylaria* takes the comb, look first in the model viewer; holes open into
   the shaft and galleries); **1b** world generation of living and dead termite mounds in
   savannas; **1c** the Matabele species file, sizes and the queen; **1d** the shallow nest
   (dead mound, under a fallen tree, or the ground; digging in where needed); **1e** the
   prey pile and hunger; **1f** Matabele colonies in new land and a command. The original
   outline: Dead termite mounds (a dead colony's mound stays,
   empty, wearing down; some placed by world generation). The Matabele species
   profile (`colony_fission`), the ant with its own size from the real spread (one
   placeholder model, scaled), the queen, a shallow nest that reuses ready spaces
   (dead mound, under a fallen tree, or a few rooms in the ground), the prey pile
   (goes off in about a day), hunger, world generation in savannas, spawn egg and
   command.
   **Pieces 1c to 1f planned with the user 2026-10-24** (decisions in `docs/DESIGN.md`, "Matabele colony, details"):
   - **1c, the ant gets a colony:** `megaponera_analis.json` (castes major and minor, health and damage, how it eats and
     grows, founding `colony_fission`), the wingless (ergatoid) queen with her own model (a major's body, a swollen rear;
     the look picked in the model viewer first), her eggs hatching into larvae and then majors or minors in the real
     two-humped mix, each ant its own size inside its caste. Test: summon a queen, `/antcolonies list`, watch the
     eggs, larvae and sizes. The colony has no nest yet (1d).
   - **1d, the shallow nest (as built, after R1 and the user's last answers, 2026-10-24):** the queen picks once, within
     32 blocks, best first: a dead mound (used as it is, nothing dug, 1 colony), under a fallen log 3+ thick (one 3x3 room
     1 block down, the log its roof, 2 colonies at most), at the foot of any tree 3+ thick, dead or living (the same room,
     1 colony), else a burrow stand-in in bare ground (a tunnel sloping in to one room 3 down, 3+ blocks from another burrow, rooms
     never touching). The queen digs nothing; her workers dig the way in. Test: a queen near each (`MatabeleNestTests`).
     **Changed by the user 2026-10-24: dead mounds only for now** (logs, trees and burrows dropped; a queen with no
     dead mound within 32 blocks waits and looks again; `docs/DESIGN.md`, "Dead mounds only, for now").
     Still to add for step 1: two or more entrances, guests in the nest, returning to an old nest.
   - **1e, the prey pile and hunger:** the pile of dead termites in the nest, eaten by larvae and adults, off in about a
     day; hunger by the colony rules already built; stocked by a command until raids exist (step 3). Test: stock it
     and watch it eaten and rot; leave it empty and watch them starve.
   - **1f, new land and a command:** a few colonies placed in new savannas (dead mound, under a fallen tree, ground)
     kept away from living termite mounds, real sizes 100 to 2,300 (R1), mostly numbers; a command that makes one anywhere
     with a size you choose.
     **Built 2026-10-24 (branch `claude/m8-newland-colonies-dcbea5`):** dead mounds only (the nest rule now), 2 in 3 of
     new land's dead mounds get a colony, sizes 100 to 2,300 leaning small, a day's prey on the pile; not kept away from
     living mounds (they lie about 96 apart anyway, real nests sit among the termites); `docs/DESIGN.md`, "Step 1f".
2. **The shared fight system** (species-neutral, the harvester ants use it later
   against their neighbours): health and damage per caste from the profile, the
   sting (paralysis), termite soldiers clamping on and riding along, legs bitten
   off (saved per ant, hidden on the model, 5- and 4-legged walks), stinging a
   player who provokes them, defenders in waves who give up past their territory.
   Ideas from the user (2026-10-23, planning, liked): the attack picked by where the
   termite is (grab and sting under when it is in front, bite and stab down into a
   broken tunnel); antennae bitten off too; termite soldier heads left clamped on the
   ants' legs and pulled off at home; lost legs lying on the battlefield a while.
   Matabele heads coming off: not real, left out.
   **Decided by the user 2026-10-23:** the attack depends on where the termite is,
   sting or bite, as above (both clips already made). A "brutal sting" animation is a
   maybe for later (the user's idea), not part of step 2 unless asked.
   Dead Matabeles in the termites' tunnels (the user, 2026-10-23): never walled up
   with blocks (50 dead ants would mean 50 blocks placed); the termites carry the
   body away like their own dead. Where to (today termite bodies are eaten in the
   garden fresh or walled up where they lie): **dropped outside by the nearest hole**
   (the user, 2026-10-23), fading after a while; later food for the savanna's
   scavengers (M9.5, the user's ecosystem plan). The termites' own old dead the same
   (the user's "yea" to both, 2026-10-23), in place of walling them up; fresh ones
   still eaten in the garden.
   Replaces `Alarm`/`Soldiers` using `TermiteSoldierEntity` directly with a shared
   defender idea.
   **Pieces, planned with the user 2026-10-24** (decisions in `docs/DESIGN.md`, "The fight system, details"):
   - **2a, the core:** health and damage per caste from the species files (numbers my picks, to confirm; a major
     Matabele rarely dies to one termite, a Macrotermes major soldier is the termites' strongest), a shared defender
     idea and "who is my enemy" question in the framework in place of `TermiteSoldierEntity` named in `Alarm`/`Soldiers`.
     Nothing changes in-game (chickens and termite soldiers as today).
   - **2b, the sting:** a stung termite falls paralysed and twitches, found by its nestmates if left; the attack picked by
     where the termite is (grab and sting under in front, bite and stab down into a broken tunnel; both clips made).
     **The skirmish command (the user's pick):** `/antcolonies skirmish <ants>` drops that many Matabeles from the
     nearest colony (or new ones) at the nearest termite feeding spot (sheeting, feeding gallery, shallow gallery) to
     break in and fight; step 3's scouts and columns replace it.
   - **2a and 2b BUILT 2026-10-25** (branch `claude/m8-fights`; `docs/HISTORY.md`): the skirmish drops new Matabeles
   - **The attack clips redone 2026-10-25** (the user's report: the sting never reached): A swings the gaster forward under the body into the termite held in the jaws; B dives into a broken tunnel, hauls the termite up to the rim and stings it there; the queen has no attack until 2e.
     (they join the nearest colony within 32 blocks) rather than taking them from a colony's numbers; "found by its
     nestmates if left" moves to 2f (the termites' side); carrying the paralysed home is step 3.
   - **In-game test 1** (2a + 2b).
   - **In-game test 1 done 2026-10-25:** everything works, but not polished ("walking death": termites fall before the
     sting is seen, ten ants cleared a feeding spot in about a minute).
   - **2b+, fight polish (planned with the user 2026-10-25, all seven pieces agreed; before 2c).** First a source check
     (Yusuf 2026 review and Frank's raid papers) for the real pace: how long the fight at a feeding spot lasts, termites
     taken per ant, how fast the venom paralyses, how many termites escape; the numbers below are set from it.
     1. The sting lands when it is seen: the termite is pinned from the moment the jaws close (attack A about 0.2 s,
        B likewise) and falls only at the sting moment (A about 0.56 s); pulled away or the ant knocked off before
        then, it is a miss.
     2. One termite at a time, finished: the clip plays to its end, the ant lets go and antennates the prey a second or
        two, then turns to the next (turning takes real time).
     3. Grabs can fail: termites dodge; a soldier is harder to grab than a worker (it bites first); odds per body in
        the species file.
     4. The termites flee: workers near arriving Matabeles run for the nearest hole and go down (an early piece of
        2f).
     5. Paralysis takes a few seconds (from the source check): the termite staggers, then flips onto its back.
     6. No fighting while climbing (the user's rule): nothing grabs, stings or bites unless standing on a floor (not
        on a wall sideways or head down, not on a ceiling, not falling); shared, termite soldiers too. A soldier
        standing on the floor may still bite an ant climbing past its head, but the climbing ant never strikes back
        (agreed by the user).
     7. Pace target: ten ants against a feeding spot take several minutes, many termites escaping down the holes.
     **Round A BUILT 2026-10-25 (pieces 1, 2, 5, 6; `docs/HISTORY.md`, "M8 fight polish, round A"); round B (3, 4, 7)
     BUILT 2026-10-25 (`docs/HISTORY.md`, "M8 fight polish, round B"); the fight polish is ready for the user's in-game test.**
     **In-game test of the fight polish done 2026-10-25 (the user: "its so much better").** Found: ants camping one
     corner till a termite came by (fixed the same day, `docs/BUGS.md` 76); the targeting idea below goes to step 3.
     Tests: an ant on a wall never strikes; a termite falls only after the sting moment; a ten-ant skirmish lasts
     minutes, not seconds; `soldiers,termitefood,colony,away`; guard films if a clip changes; then the user looks
     in-game with `/antcolonies skirmish 10`.
   - **2c, clamping:** termite soldiers clamp on and ride along, slowing and hurting; a soldier killed while clamped
     leaves its head locked on the leg (shown) until a nestmate bites it off in the nest. **BUILT 2026-10-25**
     (branch `claude/great-cannon-1d028b`; `docs/DESIGN.md` "Step 2c, clamping"; not tried in-game yet).
   - **2d, lost legs (legs only, the user's pick: antennae losses are not measured for Megaponera):** saved per ant,
     the leg hidden on the model (the Matabele model already has its six leg bones), 5- and 4-legged walks from the gait
     maker, a little slower; the lost leg lies on the ground a while; 1 or 2 gone, rest about a day, then raid again.
     **BUILT 2026-10-25** (branch `claude/m8-fights`; the user's calls: the real study's recovery, about 30 percent
     slower at first and nearly full speed after a day, no forced rest; only termite soldiers cut, majors more often;
     3 or more gone, it stops fighting and limps home; details in `docs/DESIGN.md`, "Step 2d, lost legs").
   - **In-game test 2** (2c + 2d).
   - **2e, the player:** stung only when provoked (breaks into the nest, hits one, stands in a column), 2 hearts and the
     pain effect, armour as normal; defenders come out in waves and give up past the territory; the squeak.
   - **2f, the termites' side:** soldiers drum (`head_bang`), workers further in retreat and seal behind them, the
     soldiers shut out; those alive when the raid leaves wait by the seal and are let back in at night (the user's
     pick); dead Matabeles and the termites' own old dead carried out and dropped by the nearest hole, fading (fresh
     dead still eaten in the garden).
   - **2e and 2f planned with the user 2026-10-25** (details and my picks in `docs/DESIGN.md`, "Steps 2e and 2f"):
     all four provocations count (breaking the nest, hitting one, standing in a column, walking on the nest, the last
     only a few ants out); defenders in waves, giving up 16 blocks from the nest; the queen stings only in her room;
     termites carry a paralysed nestmate left behind into the nest and eat it in the garden. Build order: 2e (the
     provocations, the waves and going home, the sting's pain, the queen's sting, the squeak), then 2f (the raid
     break-in drumming down the gallery, the seal behind the soldiers, let back in at night, dead carried out, the
     paralysed eaten).
   - **In-game test 3** (2e + 2f), then merge.
   Tests: each piece runs `soldiers,termitefood,colony,away` (and `legs` for 2d, guard films for 2c and 2d); a full run
   before the merge (2a changes the shared alarm).
   - **2g, smarter hunters at the feeding spot (planned 2026-10-25, from the user's in-game report: "the ants are very
     janky how they move ... it took a minute of waiting to get them to break the mud wall and it only happened when a
     termite got right up against the mud wall ... they need to be smarter").** **BUILT 2026-10-25 with the user's OK** (branch `claude/m8-matabele-smarter-5d130b`): the new test `matabeleMajorsBreakIntoAFeedingGalleryUnderALog` opens the ground in 3 to 4 s (4 to 5 blocks), stings the first termite in 7 to 9 s, all 5 of 5 stung, 0 walks not found, 168 to 191 stops for 10 ants in 2 minutes (was: nothing opened or stung, 600 walks not found, about 300 stops); `matabelefighttests` 6 of 6 on every server; `soldiers,away` 30 of 30 on 6 servers; not tried in-game yet.
     - **Measured** (a probe game test: 5 termites in the feeding gallery under a fallen log, the log's top plastered with
       mud, 10 Matabele sent at the log; 3 runs of 3 the same): **in 2 minutes no termite stung and nothing opened.**
       Every ant tried each termite in turn, planned a walk to it, found none (they are under the soil), gave it up for
       10 s and went to the next: 600 failed route searches for 10 ants, a fresh one every 2 s each. Between those it
       searched at random round the spot, stopping dead every 3 s. With termites in the open (the first probe) it picked a
       new target 46 times in 20 s.
     - **Why:** (1) the ants hunt only termites they "see" and plan a walk straight to each, so termites under the ground
       cost a failed search apiece, again and again; (2) a major opens only mud lying right over or beside the termite it
       is chasing, or the one soil block over it with sky right above; under a log the roof is wood and the walls are
       plain soil, so nothing qualifies until a termite steps right beside a mud wall (exactly what the user saw);
       (3) every time a target is given up or a search step is picked, the ant is stopped dead, and the nearest termite
       is picked afresh every second, so it turns from one to another.
     - **What real Megaponera do** (Longhurst and Howse 1979; Yusuf 2026, section 3.1.3): the column stops a few
       centimetres short of the feeding site, spreads out and attacks at once; **the majors break open the galleries and
       sheeting** with their jaws, **the minors go into the opened galleries** and pull termites out, sting them and
       pile them by the gallery. They find the termites by smell and touch (termite scent soaks the galleries and their
       mud), not by sight.
     - **The fix (all my picks, by the real animal):**
       1. **Termite scent:** at the spot, an ant knows the termite works round it by smell (termite mud, mud casts, and
          the galleries of any termite colony there, filled or empty, plus termites felt through a block of soil), within
          the search reach. It hunts the scent, not only the termites it can walk to.
       2. **Majors break in:** each major picks its own breach on the scent (a block of soil or termite mud touching a
          gallery, over it or beside it, that it can stand by on the surface; never the mound, never wood) and bites it
          open; then the next block along the gallery while the scent holds, up to about 6 blocks a major at one spot
          (my pick, so a raid leaves a few broken trenches, not a quarry). Majors spread over different breaches. They
          dig even when no termite is right there (the scent says termites use it).
       3. **Minors go in:** a minor goes to an open breach, steps into the gallery and hunts the termites in it (the
          grab and sting as now; attack B from the edge if it cannot fit), up to a few blocks along, then comes out.
          They meet the termites' 2f plug and soldiers there, as real raiders do.
       4. **No more failed searches:** a termite with no way to it is not chased (no route search); it is left to the
          breach work. A target is kept until it is caught, lost or a much nearer one turns up (no flipping every
          second). The search round the spot walks on smoothly from one scent to the next, at one pace, never stopping
          dead between steps.
       5. **Kept as is:** the 2-minute search before giving up, the 5-minute skirmish, stings only from a floor, the
          termites fleeing down the holes, raids never into the mound proper.
     - **Test:** the probe becomes a real game test in `soldiers` (`MatabeleFightTests`): under the log a breach opens
       within 30 s, a termite stung within 60 s, at most a handful of failed route searches, no ant stopping dead more
       than about once every 5 s; the ten-ant hay test and the mud test kept. Then `soldiers,away` on 6 servers, guard
       films only if the walk code changes.
     - **In-game:** `/antcolonies skirmish 10` beside a termite colony eating a fallen log or branch by day.
3. **Scouts and raids:** lone scouts by day, the scent line home, the column to a
   termite feeding spot (sheeting, feeding gallery, shallow gallery, the holes by
   them; never the mound), majors opening the mud, minors in, termites as numbers
   in the galleries shown on break-in (`HiddenTrips`), the termites plugging and
   closing (`ClosedHoles`, danger memory), the column home with prey; 2-4 raids a
   day by hunger, morning and late afternoon.
   **Targeting (the user, 2026-10-25, from the fight polish test):** today the ants go at every termite near the spot,
   as if exploring the nest; the user wants them to go for the food (the workers at the feeding spot) before the
   soldiers pile up. Real raids: the column hits the feeding site, the minors take the workers in the opened galleries,
   and the soldiers that come up are fought only as they get in the way. To plan with the raids (the user said "part 3";
   it fits here, with the raid's target, not step 4's drawn-only ants (the step numbers were swapped later, 2026-10-25)).
   **Step 3 in pieces (planned 2026-11-03, after in-game test 4; built from the decisions already in `docs/DESIGN.md`,
   "Scouts and raids, details", and the R1 questionnaire; numbers my picks, to confirm):**
   - **3a Scouts.** A hungry colony sends lone scouts out in the raid hours; they check the colony's past good spots
     first, then search outward to 96 blocks (the real foraging range is about 95 m; the mod counts a block as a metre
     for distances, as with moving house). A scout knows a termite feeding spot when it smells termites under the mud
     there (`FeedingSpots`, the galleries round it), stops, and runs home laying a scent line (the `Recruits` line).
     Test: `/antcolonies scout`, watch one go out and come back. **BUILT 2026-11-03** (`colony/Raids`,
     `MatabeleScoutGoal`, `MatabeleRaidTests`; with the colony's own scouts going first, a fast run home and the
     finder named on the find; `docs/HISTORY.md`). Not tried in-game yet.
   - **3b The column.** At home the scout recruits by what it found, capped by the colony's size and hunger (big spots
     the most; 30 to 75 percent of the colony, the paper); up to about 50 real ants walk, the rest stay numbers till
     step 4. The scout leads; single file, then widening; the column stops a few blocks short, bunches, and attacks at
     once: majors break the mud, minors go in. **Food first** (the user, 2026-10-25): the workers at the feeding spot
     are the target; soldiers are fought only when in the way. The fights are step 2's (the skirmish command stays for
     testing). **BUILT 2026-11-03** (`colony/RaidColumn`, `MatabeleRaidGoal`, `/antcolonies raid`, `MatabeleRaidTests`;
     milling at the entrance, surges, regrouping and a column home, my picks; `docs/DESIGN.md`, "Step 3b, the column, as
     built"). Not tried in-game yet.
   - **3c Home with the prey.** Majors carry visible stacks (up to 10), minors 1 to 3; the column walks home on its own
     trail and the prey goes on the pile (the stocking command stays for tests only). An empty raid spreads round the
     spot a short while, takes any it finds, and goes home; hunger sends a scout out sooner. A lightly hurt ant nobody
     picks up limps home along the trail (carrying the hurt is step 5). **In-game test 5 here:** a whole raid seen.
     **BUILT 2026-11-03** (branch `claude/m8-prey-home-44beec`: `RaidColumn` collecting and spreading out,
     `BringPreyGoal`, `PreyPile.bringIn`, limpers in `Raids`, the stack look and `carried_termite.png`, numbers in
     `raids.carry`; `MatabeleRaidTests`; my picks in `docs/DESIGN.md`, "Step 3c, home with the prey, as built").
     Not tried in-game yet.
   - **3R The raid rework (planned 2026-11-04, before 3d; the user: "refine the scouting and raiding by a lot, I want
     it consistent and great", every idea below taken, "Yes, all of them").** In-game test 5 found the column
     freezing just out of the nest. Read in the code (not yet proven by a test): the march is the only stage with no
     time limit; its line starts at the nest's middle, which in a dead mound is the open chimney mouth, so the back
     raiders are sent into the hole; ants down the chimney still count as "at the start of the line", so the front waits
     for them for ever. Also found: the search range grows when slices are only *taken* by scouts (not found empty),
     never shrinks, and later trips search only the far band; any dug gallery cell within 5 blocks counts as a find,
     however deep, and a long gallery scores high (big raids to empty ground); the scout command makes raids leave at
     any hour; the raid writes nothing to the log; no test runs the whole chain. Real behaviour checked 2026-11-04
     (Hoelldobler et al. 1994; Frank and Linsenmair 2017; Frank's thesis; AntWiki; Yusuf 2026). Steps, in order:
     - **R1 The raid diary.** Every stage change, its time and why, who the column waits for, who dropped out and why,
       into the log (`[raid]` lines) and `/antcolonies list`.
     - **R2 The whole chain in one test.** A real dead-mound colony, a real scout finding real termites under real mud,
       40+ real raiders out of the chimney, the attack, home with the prey (no hand-made trail). It should show the
       freeze before R3 fixes it.
     - **R3 The march rebuilt as trail-following** (real columns follow the scent, each ant keeping near the one ahead;
       they halt in surges and move on). Each raider walks the trail and keeps a gap to the ant ahead; the front pauses
       a few seconds at most at a time; an ant making no headway for a while leaves the column and goes home instead of
       holding it; every stage has a hard time limit; no place is ever on an open hole (the line starts at the first
       standable cell beside the entrance); only raiders really out of the nest count for the line. Real details folded
       in: **the column's order** (the scout first, former scouts right behind, majors at the back as the rear guard
       picking up dropped termites and the hurt, Frank 2017 via AntWiki); **ants that slip off the trail rejoin the
       column by its smell in the air** if near it (Yusuf 2026's airborne odours) rather than standing lost.
     - **R4 Every raider lays scent; the trail is a thing in the world.** (**BUILT 2026-11-04**, `colony/ScentTrails`, `command/RaidView`; as built in `docs/DESIGN.md`, "R4, scent trails, as built".) Each colony keeps its scent trails with their
       age (real: all raiders lay trail on the way out, so the way home does not hang on the scout; the poison gland's
       trail lasts up to about 29 hours, Hoelldobler 1994): trails fade, later raids to the same spot follow the old
       trail, limpers use it. **The player can break a trail** (digging across it, water, a block on it): the column
       stops at the gap, the ants cast about, pick the trail up on the far side, or a former scout leads them on (a
       hypothesis in AntWiki, not tested; a trail cut is a classic ant experiment, not shown for Megaponera). A
       **debug view** `/antcolonies raid show` draws the trails and each raider's state with particles.
     - **R5 Scouts that choose.** (**BUILT 2026-11-04**, `colony/Raids`, `MatabeleScoutGoal`, `FeedingSpots.byDayPlaces`, `raids.choosing`; as built in `docs/DESIGN.md`, "R5, scouts that choose, as built".) The range grows only when scouts come back empty, trips search from near to far,
       and the range shrinks back over the days after finds. A find needs real feeding: termites there, or a feeding
       spot fed at by day, not any gallery cell. **Choosiness** (Frank and Linsenmair 2017): a find must be richer the
       farther it is, and less rich the hungrier the colony; a hungry colony sends more scouts and bigger raids.
       Real colonies have 10 to 20 scouts and scout in two sessions a day, sometimes over an hour (the user's 1 per 50
       up to 5 and all-day scouting kept, noted only).
     - **R6 The mound as a clue** (**BUILT 2026-11-04**, `MatabeleScoutGoal`, `Raids`, `FeedingSpots.waysOut`, `raids.mound_clue`; as built in `docs/DESIGN.md`, "R6, the mound as a clue, as built") (the user's idea, 2026-11-03; not described in the papers, believable as termites
       feed in galleries spreading out from their mound): a scout that notices a living mound (about 16 blocks off,
       my pick, as the feeding spots stand 10 to 30 blocks past the mound's foot) searches round it in widening rings
       out to about 30 blocks past the foot; a gallery smelt on the way is followed outward, away from the mound, to its
       end, where the feeding is.
     - **R7 Hours and sound** (**BUILT 2026-11-04**, `RaidColumn`, `Raids`, `raids.hours_and_sound`; as built in `docs/DESIGN.md`, "R7, hours and sound, as built"; also the attack ending 30 s after the last termite, at most 2 minutes, and a faster walk home, the R1 diary's open findings; BUGS 88 and 89 already fixed by review batch 1). **Raids end by dusk (the user's answer, 2026-11-04):** no raid leaves after 19:00 (my
       pick: time to reach a spot and come home); a raid still out at 19:30 stops where it is, picks up what is stung
       and walks home (my pick); a find made after 19:00 waits for the morning raid hours. Rare night raids in the hot
       season wait for Serene Seasons. The scout command keeps the hours unless told otherwise. **The column squeaks as it passes, louder as the player comes near, before anyone
       stings** (Hoelldobler 1994: the columns' stridulation is no message between ants but a warning to big
       animals). Raids heading into unloaded land turn back at its edge until drawn-only ants (step 4).
     - **R8 One scale for real distances** (**BUILT 2026-11-04**; as built in `docs/DESIGN.md`, "R8, one scale, as built" and "R8's balance pass": the three scales listed in `raids._scale_note`, no number changed; new-land colonies settled, their scouts starting at the whole range, `scouting.settled_range` 96). The mod counts distances as a block a metre (range 96 for the real 95 m)
       but bodies as a major a block (about 1.5 cm); every real distance copied in says which scale it uses (the
       column halting 20 to 50 cm short of the termites is under a block by one, 13 to 33 by the other; 4 kept).
     - **R9 The column in stage pieces** (the raid code review's finding 10, left from its batch 2 on purpose so the
       rework is not done twice; code only, nothing changes in-game): `RaidColumn` split into one small piece per stage
       (forming, march, attack, collect, home), each with its own timers, sharing the members, routes, numbers and the
       diary; `Route` in its own file; every numbers record with a default so the fallbacks go; the timings the user may
       want to tune moved into `megaponera_analis.json`.
     - **R10 Trails into the framework** (finding 13, after R9; code only, nothing changes in-game): the scent trail
       store, the route line, the stand-here rule (`RaidGround`) and the walk home along a trail (`WayHome`) moved into
       the framework under neutral names, so army ants, leafcutters and harvester neighbour fights reuse them; the
       raid stages, find scoring and prey stay in the savanna mod; check whether `Recruits`' single-file following can
       share the march's follower rule.
     - Then the raid tests and in-game test 5 again.
   - **Found by R8's balance pass (2026-11-04), not built:** (a) **a raid brings far too little**: 2 to 10 termites home
     (only the real termites at the spot; number raiders carry nothing) against 1 termite an ant a day (480 a day for the
     median colony), so every colony starves down in about 5 to 6 days however near the termites are; to fix in 3d (a
     raid takes from the termites' numbers and brings that share home) and step 4 (number raiders bring prey); (b)
     **about 1 new-land colony in 3 has no feeding spot it will take** (none within 90 blocks): moving house (step 6),
     or new land placing Matabele only in dead mounds with a living mound within about 90 (the user's call: fewer places
     with colonies).
   - **3F Feeding spots for the raids (planned with the user 2026-11-04, questionnaire; before 3d).** The problem
     (R8's balance pass): Matabele live in dead mounds, which sit in the gaps between living colonies, and each living
     colony feeds by day at only about 4 spots, so the nearest is about 80 blocks off and 1 colony in 3 has none within 90.
     Real Macrotermes feed at dozens of places at once, mostly on small dead wood (fallen branches, sticks); new land
     already puts 1 to 3 branches (and often a big limb) under every grown tree, and living trees keep shedding more, but
     the termites reach few of them. Pieces:
     - **F1 Mounds random with a safety gap (the user):** two mounds land 40 to 80 blocks apart, at random within that,
       not in loose rows as today (96-block squares, each mound in one corner). **Dead mounds too**: they are former
       colonies, part of the same pattern (the user: "because it's realistic"). Real mound fields are evenly spread but
       not in rows (neighbouring colonies fight over the ground between them). Minecraft's own spread rule only gives a
       gap per square, so the mound check also measures the real distance to its neighbours (my note).
     - **F2 Find what limits the feeding spots** (first, before changing anything): read today, a gallery piece goes only
       to rich food and comes out of the tunnel budget (1 block per 4 workers), so most branches in range never get one.
     - **F3 10 to 15 by-day feeding spots for a big colony (the user's pick)**, fewer for small ones by worker count,
       mostly mud-cased fallen branches and limbs under the trees in the colony's range; small food gets cheaper pieces
       (how, after F2). Nothing new to look at: the mud-cased branch is today's look, just more of them.
     - **F4 Lonely dead mounds stay empty (the user's pick):** new land puts a Matabele colony only in a dead mound with
       a living mound within reach (my pick to start: its foot within about 70 blocks, checked by F5); the others stay
       empty and may be moved into later (step 6).
     - **F5 The balance model again**, now counting the branches under trees and the new spacing; target: a Matabele
       nest's nearest by-day feeding spot about 20 to 50 blocks off (like real raids) and no new-land colony without one.
     - **Not in 3F (the user, 2026-11-04):** moving house stays whole in step 6 (no early starving move); **dung** as a
       termite food (real: a main termite food in African savannas, it lands in the open grass between trees) waits for
       the wildlife (M9.5); mud sheeting over dry grass dropped (ugly, and dry grass too scarce); Matabele homes in old
       termite feeding tunnels dropped (the tunnels join the nest and get packed with soil, most dead trees are not
       hollow, and it was a guess, not from the papers); a second termite species (Odontotermes, a top real Matabele
       prey; Trinervitermes, the aardwolf's food) only a "maybe one day" idea, not a to-do.
   - **3d The termites' losses.** Termites in the galleries round the spot are numbers until broken into
     (`HiddenTrips`), so a raid takes from the colony's real count; plugging, closing and danger memory (2f, 4g) dry
     the spot up, so later scouts hunt farther. Young termite colonies can die from repeated raids; big ones shrug it off.
   - **3e The raid day.** 1 to 4 raids a day by hunger, mornings (6 to 11) and evenings (16 to 19:30), none at night or
     in heavy rain; a rare night raid in hot weather (the season asked of the shared source, fixed default for now).
     **Night scouting in the hot season** (the user, 2026-11-03): scouts search at night too, but only in the hot
     season, so it waits until Serene Seasons is added (the last steps of M9) and asks the shared season source; until
     then scouts search by day only (6:00 to 19:30, built 2026-11-03).
   - **3f Out of sight.** Raids happen while the player is away too, as numbers by the same rates (termites taken,
     ants hurt or lost, heads left clamped, holes plugged); the away report lists them.
   - **3g Tests and the full run, then in-game test 6** (3d to 3f), then the merge.
4. **Drawn-only ants** (brain step 4e, pulled forward): the column far from the
   player drawn by the player's game, almost free on the server.
5. **Rescue and wound care:** the help smell, pulling clamped soldiers off, carrying
   the hurt home (legs tucked), the badly hurt left; wounds and infection in the
   nest, care by any nestmate.
6. **Moving house:** every 40 game days per colony, the first move day random from 1 to
   40 (R1, the user, 2026-10-24), and on top when prey runs out nearby, after repeated attacks, when the
   nest floods or is too small; scouts pick the site, the
   colony walks there in a column with brood and prey.

**Order to playable (the user, 2026-10-24, "I just wanna get it playable before I go off to california"):**
finish 1e (prey pile) and 1f (new-land colonies), then 2 (fights), then 4 (scouts and raids), at first with real
ants only (up to about 50 a raid; **step 4 before step 3 confirmed by the user 2026-10-24**, "do raids first i
dont want to rush drawn only"), then the test revamp (`docs/plans/test-health.md`, step 4), then 3 (big columns of
drawn-only ants), 5 and 6.
**Order changed by the user 2026-10-25: step 3 before step 4** ("i did want to move step 3 before step 4"). Now:
the rest of 2 (2c to 2f), then 3 (drawn-only ants), then 4 (scouts and raids, with the big columns from the start),
then the test revamp, then 5 and 6. This replaces "step 4 before step 3" above.
**Order changed again by the user 2026-10-25: raids before drawn ants** ("Put the raids before the drawn ants"). Now: the rest of 2 (2f, then in-game test 3 and the merge), then 3 (scouts and raids, with real ants only at first, up to about 50 a raid), then the test revamp, then 4 (drawn-only ants, built to copy the raids as they really look in the game), then 5 and 6. This replaces both orders above. **The user's idea for step 4, drawn-only ants (2026-10-25, not decided):** about 100 v 100 fighting at the breach at a time, the rest of the column waiting and carrying (real raids: only a few ants reach the breach at once); the tools for it are in `docs/research/crowds-and-performance.md`.
**Steps renumbered by the user 2026-10-25, to avoid confusion: step 3 is now scouts and raids, step 4 drawn-only ants** (the order above). Older notes that say "step 3, drawn-only" or "step 4, raids" mean the old numbers.

Later (M9): splitting into daughter colonies nearby, winged males mating in other
colonies. After M8: harvester ant territory fights with their neighbours.

**Later, the long-run balance (the user, 2026-11-04, thinking ahead, not planned):** over hundreds of hours with no player, could the Matabele wipe out the Macrotermes? Real raids almost never kill a termite colony: they take foragers at the feeding spots (a few hundred a raid from a colony of a million or more), never reach the queen, and the termites seal their galleries and wait. Already in the mod: raids hit feeding spots only, galleries are sealed, termite numbers are huge and regrow, the Matabele are capped by their own hunger. To check later: a long unattended run (world ticking for 100+ game days) counting termite colonies, their workers and Matabele colonies, and a brake if needed (termites shifting feeding away from raided spots, the danger memory already does some of it; Matabele colonies starving or moving when prey thins). Tied to M9 (new colonies founding), when numbers can grow on both sides.
