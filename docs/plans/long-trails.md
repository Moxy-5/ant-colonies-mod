# Long trails: the range is the trails (brain 4c part 2, rethought)

Planned with the user 2026-10-17. Decisions in `docs/DESIGN.md`, "Brain part 4 decisions", "The range rethought".
Harvester ants first; termites underground right after, as a step of their own (the user's pick).

## The user's decisions (2026-10-17)
- **(A) The range is the trails.** No 24-block circle. A colony reaches as far as its trunk trails go. A trail grows
  from its tip: when food near the tip runs out, searchers fan out past the tip, and a find out there pulls the tip
  out to it.
- **Length: up to 96 blocks or more** for an old, big colony (the user: "Up to 96 or more"). A young colony starts at
  about 10 to 12. The 96 is a number in the species file (`trails.most_length`), so it can be raised.
- **What limits a trail: traffic, like real ants.** A trail lives only while enough trips use it, and a longer trail
  needs more trips to stay alive. Long trips are slow, so a small colony can't keep a long trail busy. No
  colony-size table.
- **(B) Recruiting to rich patches, invisible scent.** A forager that finds a dense patch runs home laying a scent
  line; nestmates follow it out. Nothing is drawn: you see the scent only by the line of ants streaming out, and later
  by the bare trunk trail once it lasts.
- **(C) Value against distance.** Ants make long trips only for a big prize.
- **Hunger only from the ants' own failed searches** (decided 2026-10-16).

## Real ants (what this copies)
- *P. barbatus* (Gordon; Hölldobler 1976): a young colony (year 1 to 2) forages a few metres out, with no lasting
  trails. An old colony (5+ years, about 10,000 workers) keeps 1 to 4 trunk trails up to about 20 m long. *P. rugosus*
  trails can reach 40 m. Foragers walk out a trail and fan out at its end. A trail moves and stretches over weeks as
  the seed near its end is used up.
- Recruiting: *P. barbatus* and *P. rugosus* forage alone for scattered seeds. At a dense patch, the finder drags its
  sting tip (poison-gland trail) on the way home. In the nest it runs about and touches nestmates, which then follow
  the line out. The line fades within minutes unless it is laid again; heavy traffic turns it into a trunk trail.

## Step 1: trails that grow tip by tip (A)
**BUILT 2026-10-17** (`docs/HISTORY.md`).
- Each trunk trail gets a **tip**: how far out its columns go, saved. A new trail is laid only out to where the trips
  that laid it found food, not to the old food radius.
- **Out and fan out:** a forager walks its trail out to the tip (or to its own spot along it, as today) and searches
  from there. A searcher at the tip that finds nothing drifts outward past the tip (today's drift, measured from the
  tip, not from the nest), up to the tip plus the stretch (8 blocks).
- **Growing:** food found past the tip, and enough trips to it, move the tip out to it (its new columns start unworn
  and wear bare with traffic, as today).
- **Shrinking (traffic):** a trail's outer part fades first. The strength a trail needs to hold its length grows with
  the length (my pick: about 1 trip a day for every 6 blocks). Below that, the tip pulls back toward where the trips
  still go. A trail with no strength left fades away as today. A small colony therefore holds short trails, and a
  big one holds long ones, without any size table.
- **No trail yet:** searches start near the nest and drift out, as today (the 2026-10-16 fix stays).
- Numbers out of sight: they forage known plants along the trails as today. The far end of a 96-block trail is often
  in unloaded land, where only the numbers forage (never force-loading chunks).

## Step 2: recruiting to rich patches (B)
**BUILT 2026-10-17 overnight** (`colony/Recruits`; `docs/HISTORY.md`). How it was built: the finder's way home is noted every 3 blocks; at home it calls out the nearest foragers waiting by the entrance (forager numbers if too few are waiting); recruits walk the line's points out (`ScoutGoal.followLine`) and pass poorer food by (`Recruits.passesBy`). Checked in the seed scene (two ripe grama side by side).
- **The user's decisions (2026-10-17, before the night's work):** how many nestmates a recruiter calls out **depends on
  how big the patch is** (real recruiting gets stronger with a richer find; my pick: 1 for every 16 pieces of food in
  the patch, at least 1, at most 8); **plants and fallen seeds both count** toward a rich patch; recruits **follow the
  scent line** closely, single file. Steps 2 and 3 are built in this session overnight; step 4 in a new linked session
  on its own branch from this one, then merged back here (the user's OK) after its tests, with a full run, so one copy
  holds everything for the morning. Nothing goes into `master`.
- **A rich patch** (my pick): a find with at least 24 pieces of food within 4 blocks of it. A ripe grama stand
  counts; a lone seed or a tuft of short grass does not. The richness needed grows with the distance home (C, below).
- **The scent line:** the finder heads straight home and the brain notes a **recruit line** from the patch to the
  nest (no block, no particle). It fades fast (my pick: half strength after about 3 minutes of game time) unless
  another ant lays it again.
- **In the nest:** the recruiter's touches at the door (today's touch rule, Gordon) send waiting foragers out, and
  each one sent this way is told the patch: it walks the line to it, an exception to "searching alone". Each recruit
  that brings food back from the patch lays the line again. Once the patch runs out, the line fades.
- **Lines become trails:** enough trips along a line make it a trunk trail (today's new-trail rule), so a meadow that
  lasts for days gets a lasting bare path.
- The numbers recruit too: a patch the colony knows of draws more of them out while it lasts.

## Step 3: value against distance (C)
**BUILT 2026-10-17 overnight**: the recruit threshold by distance (`RecruitNumbers.needed`), recruits passing poorer food by, and a trail walked twice as fast as grass in the worth of a plant (`TrunkTrails.walkTo`, `ON_TRAIL` 0.5, my pick), so a forager leaves the trail only for food worth the extra walk.
- The worth rule (food left ^ 0.75 / trip) already makes a forager pick a rich plant over a near poor one. Added: a
  forager **leaves the trail** for a plant only when the plant is worth the extra walk, and a **recruiter only
  recruits** when the patch is worth the trip (my pick: 24 pieces at 20 blocks, about 48 at 60 blocks, about 64 at 96).
- Scattered seeds far out are still picked up by a searcher who comes across one, but never recruited to.

## Step 4: making 96 blocks work (the parts that assume a 24-block circle)
**BUILT 2026-10-17 overnight** (in this session: the app could not start the linked session while the user slept). Most of it had come with step 1 (the range is the longest tip, the plant scan along the trails); added: the test fence, the far-meadow test, and a fix (`docs/BUGS.md` 41). What stays short on purpose is in `docs/HISTORY.md`.
- `Colony.foragingRadius()` becomes **the longest trail's tip** (at least the young colony's 10 to 12). Every user
  of it follows: the alarm reach, the food map, the patrol's reach, the door, the population's area, the trail wear.
- **Plant scanning:** today it scans every column in a square out to the reach (at 104 blocks that is over 40,000
  columns every few seconds: too slow). It becomes: the ground near each trail (a few blocks to the side), the tips
  plus their stretch, and the plants the food map knows.
- `food.radius` in the species file stays only as the young colony's start (renamed in the notes).

## Tests
- **The seed scene** keeps its checks; its colony has no trail longer than the arena allows.
- **A new scene, "the far meadow"** (a new scene, nothing builds it today): a ripe grama meadow about 40 blocks out
  in an arena with a finished nest. Checks: a searcher finds it; it recruits (more than one forager walks straight to
  it, the line in the brain); a trunk trail is laid toward it and its tip moves out to the meadow; once the meadow
  is cut bare the line fades and the tip pulls back within a few game days (moving test clock).
- **Unit tests:** the length a trail's strength can hold, the tip moving out and back, the recruit threshold
  against distance.
- **Arenas:** arenas are about 45 blocks apart, so a 96-block trail could reach a neighbour's arena. Tests fence a
  colony's trails at its own arena's edge (test-only, like `FoodMap.setTestArea`).
- Runs: `antnest,away,colony` after each step, on 8 servers (`tools/run_game_tests.sh 8`; 4 while the user games);
  a full run after step 4 (it touches shared parts).

## Then
- Termites underground (own plan, right after): galleries growing tip by tip toward food, new holes near what they
  find, mud runways to rich dead wood, reusing step 1's tip and traffic parts.
- Then 4g (danger memory and neighbours).
