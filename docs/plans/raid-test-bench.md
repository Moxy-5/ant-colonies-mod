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
