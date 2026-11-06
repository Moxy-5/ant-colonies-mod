# The raid test bench (planned 2026-11-05)

**Why (the user, 2026-11-05):** every in-game raid test turns up a new bug ("if there are this many bugs that i am seeing
everytime i cant imagine the bugs 100 hours into the world"). The latest: a raid's attackers and the termite soldiers
stuck together in a pit cut off from the colony (`docs/BUGS.md` 111). The user asked for "a simulation or something to
really streamline bug fixing for these raids", thinking of everything.

**Why the bugs keep coming.** Our raid tests are hand-built flat arenas, one scene each, each proving one feature
alone. Real play has slopes, pits, ponds, chunk edges, a player, saving and loading, and several features meeting at
once (majors biting in, the termites sealing behind, soldiers waiting at the seal, the walk home). Most raid bugs live
where two features meet, and no flat arena builds that. And one in-game test shows one or two raids; 100 hours of play
is hundreds. So the rare bugs only show up in the user's game, one at a time, each costing a play session, a picture
and a guess.

**The idea in one line:** the game checks itself against a list of rules a raid must never break, saves a "black box"
the moment one breaks, a black box becomes a repeatable test with one command, and a sped-up game on real generated
savanna runs hundreds of raids unattended, with a pretend player poking at them, and lists every rule broken.

Words used here: a **rule** is something that must always be true (an "invariant" in coder talk); a **soak run** is a
long unattended run looking for rare failures; a **black box** is a saved snapshot of the place, the insects and the
last few minutes, like a plane's flight recorder.

Nothing in this plan changes how the game plays, except step B7 (see "Your calls").

## The steps

### B1. The raid rules, checked all the time
One checker (`RaidRules` in the savanna mod for the raid rules; the general ones in the framework, `gametest`'s
`StuckWatcher` grown up into `colony/watch/`) that the game tests, the soak runs and the user's own game all use. Each
rule has a short name for the log (`[rule] stage-limit broken: ...`). The rules:

1. **stage-limit:** no raid stage lasts past its own limit plus a small grace (forming, marching, gathering, attacking
   2 minutes at most, collecting, spreading, regrouping, homing). *Would have caught BUGS 111.*
2. **heartbeat:** every raid not done is updated at least once a second while its colony is loaded. *BUGS 111's log
   suggests exactly this broke: the attack outlived even its 2-minute cap, so the raid may have stopped being updated.*
3. **not-stuck:** no raider stands within a third of a block of one spot for 20 s while its stage needs it walking
   (the underground `StuckWatcher`, now above ground too, and for raid orders, not only routes).
4. **home-by-night:** by an hour after dusk every raider is home, dead, or let go with a way home it is walking; no
   Matabele is left out at night.
5. **not-walled-in:** no insect stays 60 s in a pocket from which no walkable way leads to its nest or the open air
   (a flood fill over the cells an insect can walk, 32 blocks round). *BUGS 102 and 111.*
6. **not-in-a-block:** no insect sits inside a solid block for more than 2 s.
7. **soldiers-go-back:** termite soldiers called to a break go back in, or wait by a seal that will open, within a few
   minutes of the last raider leaving; none waits for ever outside.
8. **seals-open:** a raid seal opens by the next night (the design), unless the danger is still there.
9. **books-add-up:** a colony's real ants plus its numbers plus its raid's numbers only change by births and deaths
   the diary names; no ant lost between the books.
10. **prey-ends:** every stung termite is carried, eaten or dies within a day; a termite picked up arrives on the pile
    or is dropped where its carrier fell.
11. **speed:** during a raid, route searches a second and milliseconds a tick stay under limits (BUGS 99's lesson).
12. **no-endless-lists:** the colony's lists (scent trails, danger spots, dead mounds, finds, seals) stay under a size,
    and their save stays under a size (the 100-hour worry).

New rules are added whenever a bug is found that none of them would have caught: **every fixed bug either breaks an
existing rule or adds a new one.**

### B2. The black box
Each colony keeps the last 3 minutes (a ring that forgets the oldest): its `[raid]` diary lines and, once a second, each
raider's line (stage, goal, where it was ordered, its route's state, why it last gave up). When a rule breaks, or the
user types **`/antcolonies report [a few words]`**, a folder `run/reports/<date>-<rule or "player">/` is written:
- the blocks round the spot (as a structure file, up to 96 x 48 x 96), with the nest and the find if near;
- every insect there, saved whole (its goals, loads, legs, clamps), and the colony's own save, the termite colony's too;
- the last 3 minutes of the diary and the raiders' lines;
- a screenshot (when the player typed it);
- `report.txt`, a short plain-words summary: what broke, who, where, what each stuck ant was waiting for.

In the user's game the rules run and write their black box quietly; one chat line says "a raid rule broke, report
saved" (see "Your calls"). The user then only has to say "check the reports", no description needed.

### B3. A black box becomes a test
`python tools/report_to_test.py <report>` copies the snapshot into the game tests and writes a test that puts the
place and the insects back exactly and runs with every rule on until the raid is over. `/antcolonies replay <report>`
does the same in a creative world so the user (or a film) can watch it. Exact replay cannot be promised (the dice
differ), so the test runs the scene several times with different dice; a bug that comes from the place shows again.
Once fixed, the test stays: that bug can never come back unnoticed.

### B4. Soak runs on real savanna
`tools/soak.sh [servers] [game days]`: headless servers (no screen), each on a different world seed, real world
generation, at full speed (`/tick sprint`). A soak driver in the mod:
- finds Matabele colonies with termites in reach, keeps the land round them loaded (chunk tickets, no player needed)
  and switches the far rule off for them, so they raid as real ants as if a player stood there;
- lets them raid by their own scouts in the raid hours (and some by command at odd times);
- **a pretend player** that does what the user does, at random, seeded: breaks a trail block, digs into a nest or a
  break, puts blocks in a column's way, stands in a column, kills a raider, unloads and reloads the land mid-raid,
  saves and reloads the whole world mid-raid (a big source of 100-hour bugs);
- runs every rule and writes black boxes;
- ends with `run-soak/soak-report.txt`: raids run, stages reached, rules broken grouped by rule (how often, report
  folders), milliseconds a tick over time, list and save sizes over the days.

Side by side like the game tests (6 servers, different seeds). A first soak of a few game days takes about an hour.

### B5. The 100-hour check
The same soak run for about 100 game days, overnight, watching the slow things: ticks getting slower, lists and saves
growing, colonies starving down or blowing up, trees and mounds piling up. The other systems' rules (termite holes,
galleries, trees) can join the same bench later; raids first.

### B6. Labels over the ants
`/antcolonies raid show labels`: each raider (and each termite soldier at a break) shows a short line above it: its
stage and what it waits for ("attacking: waiting for termites", "homing: no way, 12 s"). A screenshot like the one of
BUGS 111 then says the cause by itself.

### B7. Nothing waits for ever
Every wait in the raid code and the termites' raid side (the seal, soldiers by the seal, the bite-out, spreading,
regrouping, waiting for a straggler) gets a time limit and an escape: go home along the trail, or bite out (BUGS 102's
way), or, for termites, go back in by the nearest hole. Written as a table in this file when built (each wait, its
limit, its escape), and proved by rules 1 to 7 in the soak.

### B8. Then the bugs
With the bench built: BUGS 111 is its first case (a black box from a soak, or rebuilt by hand from the picture), then
the open raid flakes (BUGS 93, 97, 103, 106, 108) run through the soak and replay, then soak until a few game days run
clean, then the user's in-game test 5 again. From then on every raid change ends with a short soak, the way a change
ends with its test category today.

## Order (rounds, as in Apex)
1. **Round 1:** B1 rules, B2 black box, B6 labels. Useful at once in the user's own play.
2. **Round 2:** B3 report to test, B4 soak runs.
3. **Round 3:** B7 waits, B5 the long soak, B8 the bugs.

One in-game look at the end of round 1 (the user plays a raid with the rules on and tries `/antcolonies report`).

## Round 1, BUILT 2026-11-05 (branch `claude/m8-still-when-idle-07d154`)
What was built, and how it differs from the steps above (my picks):
- **The checker** is `framework/.../watch/Watch.java` (not `colony/watch/`): once a second, on each colony's own tick of
  the second and only for colonies the brain ticked, every signed-up rule looks at the colony and its insects within 120
  blocks. A rule broken: a `[rule] <name> broken at x, y, z (colony ...)` warning in the log, a line in the colony's
  black box, a gold `[ant rules]` chat line (rules `on`), red labels over its insects for a minute, and, at most once in
  10 minutes per rule and colony and 20 a game, a report. The same thing is told again only after 5 minutes. A rule that
  throws is switched off for the game (logged), so a checker's bug never crashes the game. On the game test server the
  rules watch only colonies a test asks for (`Watch.watchForTests`).
- **Rules built (12):** general, in the framework (`watch/GeneralRules`): **not-stuck** (a route but still 30 s),
  **not-in-a-block** (inside a solid block over 3 s), **not-walled-in** (still a minute, out of the nest, in a closed
  pocket: a flood over every cell that is not a full block finds no sky, no nest and no unloaded land within 32 blocks),
  **prey-ends** (paralysed a minute past its paralysis' own end), **save-size** (a colony's save over 2 MB, looked at
  every 5 minutes). Raid rules, in the savanna mod (`colony/RaidRules`): **stage-limit** (a stage 30 s past its own
  longest, the same table as the whole-chain test), **heartbeat** (a raid not worked out for 10 s while its colony is
  ticked), **raider-stuck** (a raider still 30 s more than 3 blocks from where it was told to go; one going home still a
  minute and not home), **home-by-night** (from 21:00 to 5:00 a Matabele out of the nest a minute, not fighting and not
  walking home; only while the queen is in her nest), **soldiers-go-back** (a termite soldier out in the open by day, 14
  or more blocks from its mound, no alarm, no Matabele within 16, for 3 minutes), **seals-open** (a raid seal still shut
  a day and a quarter after the last foe near it), **raid-lists** (over 64 trails, 64 finds or 300 ants going home).
- **Moved to round 3:** books-add-up (the books are spread over the population, the away life and the raids; worth its
  own careful step) and speed (route searches and milliseconds, best measured in the soak).
- **The black box** (`watch/BlackBox`): each watched colony keeps 3 minutes: its diary lines (`[raid]` lines from
  `RaidColumn.say` and `Raids.say`), each insect's line every 3 seconds (kind, id, place, health, paralysed, route or
  standing, seconds still, fight spot, goals, and the species' words: raid stage, leader, its place's distance, casting,
  called to defend, scouting, going home and why; a termite soldier shut out by a seal), and the rules broken.
- **The report** (`watch/Reports`, `/antcolonies report [words]`): `reports/<date>-<rule or player>/` in the game
  folder (`run/reports/` for `runClient`) with `report.txt` (what broke or your words, when, the colonies within 112
  blocks as in `/antcolonies list`, the rules broken lately, every insect within 24 blocks), `diary.txt` (the black boxes
  merged in time order), `colonies.nbt`, `place.nbt` (96 x 48 x 96 with the insects, 48 x 32 x 48 if the land is not all
  loaded, none if still not) and, for your own report in single player, `screenshots/screenshot.png`. The newest 50 kept.
  The world is read at once; the files are written in the background.
- **Labels:** `/antcolonies labels` (not `raid show labels`: every insect, not only raiders), each insect within 32
  blocks of you shows its line (the species' words, else its goals; "no way" when its last route search failed; seconds
  still), red while a rule it broke is fresh. They are name tags changed twice a second, never saved, and an insect with a
  name tag of its own keeps it.
- **Commands:** `/antcolonies report [words]`, `/antcolonies rules` (the list and the last rules broken),
  `/antcolonies rules on|quiet|off`, `/antcolonies labels`.
- **First catch, a false alarm, fixed the same day:** the first run broke not-stuck and raider-stuck in the whole-chain
  test: raiders standing rightly at their place in the homing queue (waiting their turn down the chimney) and a major
  resting in the nest were each given a new place and counted as stuck the next second, their standing time already
  counted. Now not-stuck counts stillness only from when the insect got its route, raider-stuck only from when it was put
  off its place. The black box showed it at a glance (the ant's lines: "standing, place 0 off" for 45 s, then a new
  place 8 off), which is the bench doing its job.
- **The report's picture taken first (the user, 2026-11-05: "that screenshot was delayed so it didnt capture the issue"):**
  typing the command took seconds, and the picture was taken after the place was copied. Now the **report key, F7**
  (changeable under Controls, "Ant Colonies"), takes the picture the moment it is pressed and sends
  `/antcolonies report` itself; a typed report takes its picture first thing (`client/ReportShot`).
- **Its first real catches in the user's game (2026-11-05):** termites standing in their nests with a route (BUGS 112)
  and a Matabele shut in with nothing it may bite holding still for ever (BUGS 113).
- **Tests:** the whole-chain raid test now runs with the rules watching and fails on any rule broken; a new test,
  `theRulesCatchAHangingRaidAndSaveAReport`, ages a raid's stage past its longest, checks the stage-limit rule saves a
  report with its diary and place, and checks the pocket finder on a hollow in stone (closed) and with a shaft (open);
  `BlackBoxTest` in `./gradlew test`.

## My picks (code choices; the user may skip this list)
- The general watchers (stuck, walled in, in a block, books, black box, the report command, the soak driver) in the
  framework, so army ants and leafcutters get them for nothing; the raid rules in the savanna mod, signed up like
  colony parts.
- The black box keeps 3 minutes, a line a second per raider: small (well under a megabyte a colony).
- Report folders: the last 50 kept, older ones deleted by themselves.
- The soak uses a dedicated server with real world generation, not the game test server (whose arenas are the problem).
- Rule limits start a little loose (better a missed rare bug than a flood of false alarms), then tighten.
- Seeds for the soak: a fixed list of savanna seeds, so a soak's failures can be rerun the same way.

## Your calls
1. **B7 is a change you would see:** ants and termites that today could wait for ever (as in BUGS 111) would give up
   after a limit and go home, bite out, or go back in. My recommendation: yes; it is what real ants do (a raid that
   cannot reach its prey goes home), and it is the safety net under every bug we have not found yet.
2. **A chat line in your own game when a rule breaks** ("a raid rule broke, report saved"). My recommendation: on in
   your test games (`runClient`), so nothing slips past; it can be switched off.
3. **Your PC's time:** a soak takes about an hour on 6 servers; the 100-day soak overnight. Disk: a few megabytes a
   report.
4. **Order:** the bench before more raid fixes, so BUGS 111 is fixed through it and stays tested. In-game test 5 waits
   for round 3.

## What the bench cannot do
- It cannot see what only looks wrong (jerky animation, legs through blocks): the guard films do that.
- The pretend player is not you: you will still find things, but far fewer, and each one comes with a black box.
- A replay is the same place, not the same dice: a bug of pure chance may need several runs to show again.

## Round 2, BUILT 2026-11-05 (B3 and B4)
- B3: `tools/report_to_test.py`, `ReplayTests` (category `replays`), `/antcolonies replay`. My picks: replays at the report's own coordinates; rules near the box's edge (8 blocks) and walled-in pockets reaching it not counted; report boxes shifted to take in the nests; 3 runs of 180 s for the first two replays.
- B4: `tools/soak.sh`, `watch/Soak`, `colony/RaidSoak`. My picks: an 8-chunk patch and 8 chunks round each watched colony kept loaded; at most 3 Matabele colonies; pokes every 1 to 5 game minutes, raids weight 3; a restart about once a game day while a raid is out; 12 places tried; fixed seed list; tick rate 1000; "stand in a column" skipped (needs a real player); any number of days.
- First soak (2 servers, 2 days): BUGS 120 fixed, BUGS 121 open; one seed found no colonies (now 12 places tried).
- Round 3 (B5 the 100-day check, B7): built, see below.

## Round 3, BUILT 2026-11-05 (B7, B5, B8 begun)

### B7: every wait, its limit and its escape
| Who waits | For what | Limit | Then |
|---|---|---|---|
| The column, each stage (forming to homing) | its stage to finish | the stage's own limit (`stageMostTicks`) | given up; every raider let go home along the trail (`WayHome`) |
| Forming | recruits to come out | 15 s for the scout's recruiting, the stage's limit for the pour-out | leaves with those out; latecomers follow |
| The march's front | the ants behind | 4 s | walks on |
| A raider on the march | a way on | 15 s stuck (no way 10 s) | goes home alone |
| A broken trail | the trail picked up past the gap | the casting limit (R4) | a former scout leads on, else home |
| The attack | termites to fight | 30 s after the last one, 2 minutes at most | collecting, then home |
| An empty raid, spreading | a termite found | 30 s | home |
| The walk home | the line to move | 10 s stuck | the line breaks up, each ant home its own way |
| A raider out of sight | the player to look away | a minute's grace | folded in or let go |
| An ant let go (going home alone) | a hop toward home | 20 s between tries (100 s when limping); then hops turned 45 and 90 degrees to each side, at its own height too (**new**, BUGS 121) | after `limpTicks` the raid sweep sends it again |
| A shut-in Matabele (bite-out) | something it may bite | 30 s with nothing to bite | lets go for 30 s, the walk home tries |
| A scout | its trip | its trip time (`trip_ticks`, 4 minutes) | home straight |
| The raid's update itself | (an error in its code) | at once (**new**) | the raid given up, everyone let go home, the error in the log and the diary |
| A termite seal | the foe to leave, then foraging time | as before, and now **one game day at most** (`RaidDefence.SEAL_MOST`, **new**) | the seal opens |
| Termite soldiers shut out by a seal | the seal to open | the seal's limit (a day at most, **new**) | back in through it |
| Termite workers retreating | the danger to pass | 15 s (`RetreatGoal`) | back to work |
| Termite plugging | a hole to plug | `plugGiveUpTicks` × 3 and the wave wait | gives up |
| A paralysed termite | to be carried off | a day | dies |
| **Any insect** out of its nest, walking a route or with its last search failed (**new**, `colony/Strays`) | to get anywhere | not 1.5 blocks moved in 3 minutes, out of every player's sight | folded into the colony's numbers, as a real ant comes home unseen; a `[stray]` line in the log and the black box, and counted in the soak's trend |

My picks: the seal a day at most (real termites reopen sealed galleries the next night); the stray net 3 minutes and
only out of sight (a player never sees an ant vanish), never the queen or king, a paralysed, downed or clamped insect,
or one inside its nest (the nest has its own rules). The stray net is a safety net, not a fix: each fold-in is a bug
to look at, so the soak's report flags any.

### B5: the 100-day check
The soak writes one line a game day to `soak-trend.txt` (the day's mean ms a tick, real insects, every colony's save
together, strays folded in, colonies by species, each watched colony's members and save), and the soak report gets a
"Slow things" part comparing the first days with the last, with "LOOK:" before anything past its limit (my picks: ticks
1.5 times slower and 2 ms more; saves still growing over 5 percent a day in the last half; insects or a watched colony's
members doubled or halved; a watched colony gone; any stray). The 100-day run itself is your PC's time overnight:
`tools/soak.sh 4 100`.

### B8 begun
- BUGS 121 fixed (the scout home over a ravine's lip) and its replay required, counting only the Matabele's rules (a
  replay may now name its species, `replay.json` `"species"`, `report_to_test.py --species`).
- BUGS 111: the limits above cover every wait it could have been stuck in (the seal, the soldiers, the attack, the
  update itself); its cause is still to be caught in a black box.
- Found: BUGS 122 (a termite worker standing with a route to a high mound job).
- First short soak with round 3 (`tools/soak.sh 2 2`, 7 minutes): no strays, saves and colonies steady; found BUGS 123 (scouts out at night break home-by-night); fixed the same day: the trend's first line was taken at the soak's start (0 ms, insects not out yet), a false "LOOK"; now the first line comes after the first day.
- Moved rules still open: books-add-up and speed (route searches and ms per raid), next round.
