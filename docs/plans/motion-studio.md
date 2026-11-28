# Motion Studio: seeing the game's real motion, and animals that touch (ideas, 2026-11-25)

**Ideas, then phase 0 built 2026-11-25 (below).** The user's ask (2026-11-25): now that the land animals are coming, the
animation lab should show a motion the way it really looks in the game, not a clip playing in place on a stand-in, and
should handle animations where two models touch, like a lion biting an African buffalo. The user has plenty of time for
it; land animals are getting harder to make look right. Two read-only agents went through the code (one the offline
studio tools, one the game side); Claude checked their main claims in the code. Read with `docs/ANIMAL-MAKER-ENGINE.md`
(the engine's rules and methods M1 to M4) and `docs/plans/lion-hunt.md`.

## What we found (checked in the code)

**The lab and the game show two different animals.** A clip is made and checked standing still on flat ground. The game
then adds a lot on top, and none of it is seen offline:
- **Moving through the world.** Clips walk on the spot; the game slides the animal's box along and turns it up to 20°
  a tick (`LionEntity.java` 112, 606-613), like a turntable: the spine never bends into a turn.
- **Clip speed.** The game picks walk, trot or gallop and speeds each clip up or down to match how fast the animal
  goes (`BuffaloEntity.java` 1001-1014). The clips' own speeds are typed a second time into the Java (`WALK_CLIP`,
  `Measures`). From those numbers (not filmed): the buffalo's ordinary pace plays its walk about 1.65 times too fast,
  its charge about 2 times.
- **Gait changes.** Every new clip starts at its first frame (`SteadyController.java`, `shouldResetTick`), whatever
  foot was down, so walk to trot can jump.
- **Body tilt on slopes and steps, and the head turning to look** (`StandTilt`, `LionModel` 81-113). The lion's head
  turn switches off at a clip change (likely a snap of up to 35°, not measured).
- **Size.** Calves and cubs are the grown model shrunk; clips are only ever checked at grown size.
- **Step moves made but never played.** `block_moves/lion*.json` and `buffalo.json` are exported and checked, but only
  the hippo, monitor and otter use block moves (`BlockMoves.of`).
- **The game's frame films are stand-ins too.** Most scenes summon a frozen animal (`NoAI`, named `film:<clip>`)
  looping one clip at speed 1: no travel, no speed matching, no real clip changes, no head look. The game's automatic
  picture checks (parts inside blocks, sudden jumps) run only for ants (`FilmDirector.noteRendered`, called only by
  the ant renderer). The body recorder (`client/BodyRecorder`, which saves every bone's place as the game drew it)
  records only the one "film-walker". There are no lion films at all.
- **The drawing eases run on the computer's clock, not game ticks** (`StandTilt.java` 54-64): a frozen-and-stepped
  picture more than half a second after the last one snaps the tilt to its end, so films never show a body mid-ease.

**Two animals touching is hand-placed today** (`LionHunt.java`):
- A gripping lion stands on the ground at a point on a fixed oval round the buffalo (1.9 by 0.6 of its size, the head
  0.4 in; 910-920), is pushed there flat each tick and faces the buffalo's middle. The oval does not know the buffalo is
  drawn 10 px forward of its box (`DRAW_AHEAD_PX`).
- The bite lands on a fixed clip tick (`BITE_HIT` 7); the clip ends, the lion goes calm one tick and the bite restarts:
  a visible reset about every second.
- During the leap the game pushes the lion's box forward at gallop speed while the leap clip stays in place (likely a
  glide, not filmed).
- Only the server knows there is a hold; each animal's position reaches the player's screen on its own schedule, so the
  gap between jaws and hide will jitter.
- Feeding borrows the `drink` clip at fixed angles round the carcass; the carcass gets no ground tilt.
- What already works and points the way: the otter's fish is drawn in the otter's `carry` bone from body plan numbers
  the offline checker also reads (`HeldCatch`, `clip_check.py --hold`); the crocodile carries prey by riding, so both
  move together on screen. Both are one-animal-holds-a-thing; nothing yet handles two live bodies on one clock.

**Not seen, only expected from the code:** the leap glide, feet skating during the hold, the bite reset, the muzzle
missing the carcass, the head snap. The first job is to film them (see "The phases").

## The two big ideas

1. **The game is the one truth: record it and replay it in the lab.** Instead of teaching the lab the game's rules
   (a second copy that drifts), record every animal in a real game scene, every bone as the game drew it each frame,
   with what the game was doing (clip, speed, gait, hunt stage), and play that recording in the studio viewer: turn
   the camera, scrub, slow motion, both animals together. The recorder already exists for one animal.
2. **Partner moves: the block-move method, with the other animal as the edge.** The monitor's climb came right only
   when the move was made once offline and played by the game on one clock (method M1); bending the body live failed
   twice. Do the same for touching: one file holds both clips (the lion's `cling`, the buffalo's `held_buck`) and where
   the lion sits on the buffalo each tick, made once offline per size, both animals played on one shared clock, the
   lion placed on a named spot (a socket) of the buffalo's body. It replaces the oval, the 0.4, the fixed bite tick and
   the restart loop with one table.

## Every idea (S small, M medium, L large; lane: Claude = game code, Neon = models, clips, viewer)

**A. Seeing the game's real motion (record and replay)**
1. Record every animal in a film scene, not only the walker (`BodyRecorder.java` 107). S, Claude.
2. Add the game's own facts to each recorded moment: clip, how far in, play speed, gait, hunt stage, hold target,
   hit moments. S, Claude.
3. Replay page: the recording played in the studio viewer, both animals and the ground blocks, any angle, scrub, slow
   motion. M, Neon (`viewer_common.js`).
4. Studio beside game: the clip as made next to the same clip as the game drew it, in step. M, Neon.
5. Picture strips of a recording for Claude to check before the user sees it (`body_course.py` reads the file already). S.
6. Footfall chart from a recording: one bar per foot, dark while planted; sliding, gait order and gait changes at a
   glance. S. My pick.
7. Ghost frames: see-through copies of the last few frames in the replay, to see arcs and jerks. S.
8. Footprint dots in the game while filming: a mark where each planted foot stands; a sliding foot smears. S.
9. **The replay key:** in real play the user presses a key and the last 20 to 30 seconds of every animal nearby are
   saved with the F7 report; Claude replays exactly what the user saw. Fits how the user tests (quick looks, bug
   reports). M, Claude. Risk: the cost of keeping the last seconds while playing.
10. A saved recording turned into a game test (the replay tests exist). M.

**B. Films with the animals' minds on**
11. An `actor` film step: the animal with its real AI and a fixed random seed, the same take every run. S, Claude.
12. One take, three cameras: the frozen game photographed from three sides before each step (today each side is a new
    take, so a live animal does something different each time). S.
13. The follow camera (exists for the walker) for any actor, and for the pair. S.
14. A hunt rehearsal film: dusk, hungry lionesses, a herd with a calf, recorded and checked. S once 1, 11, 12 exist.
15. Commands to jump to a moment (grip now, knock down, start the stalk), from the test hooks. S.
16. The automatic picture checks for every animal, not only ants. S. Risk: the jump limit was set for ants.
17. A label over each animal while filming: clip, clip tick, play speed, gait, stride. S.
18. The server's ghost: the true box and heading beside the drawn animal (shows network lag). S.
19. A contact overlay: the lion's jaw point, the buffalo's socket, a line between them coloured by the gap. S-M.

**C. Making the game's motion match the clips**
20. One gait table in each body plan (clip, the speed it is made for, when to switch, play-speed limits), read by the
    game and the checker; the copies in Java deleted. M. Fewer rules: one place instead of two.
21. A pace check: each gait's play speed at the speeds the game really uses, flagged outside about 0.8 to 1.25. S.
22. Foot slide checked across each gait's whole speed band, not only its made-for speed. S.
23. **Gait changes in step:** the new gait starts at the same point of its stride the old one was at, so the feet carry
    on. S-M, through `SteadyController`'s clock. My pick.
24. One drawing clock: every drawing ease counted in game ticks, so frozen films, slow motion and play all agree. S-M;
    the course scenes of the animals it touches are rerun.
25. The head turn eased to nothing at a clip change, not switched off. S.
26. Clips checked at calf and cub sizes too. S.
27. Turns that bend: the spine curving into a turn instead of turning like a turntable (the ants' `BodyTurn` is the
    pattern). M. Risk: a new live rule; see it in the replay first, then decide.
28. Starts and stops: a stride or two of speeding up instead of a straight blend from standing to gallop. M.
29. The lion and buffalo step moves: play them (as the hippo does) or stop making them. S.
30. The lion on its family's course, and the hunt's clip changes added to the lion plans (gallop to `jump_smack`,
    `stalk_leap_bite` to `bite`, `drink` and `takedown_pin`, `stalk` and `ambush_freeze`). S.

**D. Two animals touching**
31. Sockets: empty marker bones on the buffalo where lions grab (rump, neck each side, flanks, back, throat, muzzle) and
    on the carcass where they feed; on the lion the jaw tip and forepaws. S, Neon.
32. The hold told to the players' screens (which prey, which socket). S, Claude. Needed by most of the rest.
33. **Partner moves** (the second big idea). L, both.
34. The cling as riding: the lion rides the buffalo while it holds on, placed by the socket, so both move together on
    screen (the crocodile carries prey this way). M. Risk: riding's side effects (stride, getting off).
35. Or a drawing-only fix: the lion drawn shifted so its jaw meets the socket each frame (the otter's fish pattern). M.
    Risk: a big shift looks like sliding.
36. `clip_check --pair`: both animals posed together offline: parts inside the other body, bites in the air, the ground,
    strips from three sides. M, grows from `--hold`.
37. Clips made against the partner: the maker aims the lion's paws and jaws at the buffalo's moving socket, per size, so
    the contact is in the clip, not bent live. M-L, Neon.
38. The prey reacts: the buffalo bucks, stumbles under the weight, sinks to its knees as it weakens, falls. S-M.
    Inspired by real hunts: lionesses bring a buffalo down by weight on the rump and back, one sometimes clamping the
    muzzle.
39. Several lions on one buffalo, each on its own socket, checked against each other too. S once 36 exists.
40. Feeding on the carcass: feeding sockets, the carcass tilted to the ground, feed clips aimed at them. Inspiration:
    lions feed lying down, holding with the forepaws, shearing with the side of the jaw. S-M.
41. Strikes that carry themselves: the leap holds its own travel and the box follows it, aimed at where the prey will
    be, so the lion does not glide. M.
42. The drag: a lion walking backwards pulling a calf, the calf's limp body dragged like the monitor's tail. M.

**E. Game tests that judge motion**
43. The hold: the jaw stays near its socket and never jumps far in one tick. S.
44. The strike: at the hit moment the head is within the clip's lunge of the buffalo's body. S.
45. A gait's play speed never sits at its limit through a run. S.
46. Same seed, same hunt: two seeded hunts give the same timeline, so films repeat. M.

**F. Reference pictures and the user's eye**
47. Reference video beside a clip, frames cut at the clip's own times. S-M.
48. Key-pose Compare: a held key pose against a reference still (`verify_animal.py` cannot compare poses yet). M.
49. A rehearsal page per pair (lion and buffalo): reference, the offline pair, the game replay, the contact sheet. M.
50. Duet watch: the pair scene refilmed by itself when a clip changes (`film.sh watch`). S.

**G. The glue between a clip and the game (added 2026-11-26 after phase 3 step 5; the user: "add your ideas to the
plan"). The clips were the easy part of step 5; nearly every fault came from putting them into the game.**
51. **One "placed animal" mode in the framework.** Riding, holding, feeding and lying on a spot go through one piece
    that turns off pushing, walking leftovers and body turning and sets the place after the game's own physics, on the
    server and every screen alike. Today each force is found one bug at a time (the mother cow shoving feeders 0.05 a
    tick; riders drawn at the mount's heading, BUGS 421). M, Claude.
52. **A clip map in data that the game follows.** Each animal's plan says which clip is entered from which and left
    by which ("feed" from lying, out by getting up). A goal asks only for the clip it wants; the body walks the path
    itself. The offline checker reads the same map, so it checks exactly the changes the game can make. Removes the
    hand-written orders in each goal (`onFeet`, the feast's lying down), where skipped clips and calm ticks come from. L.
53. **The code's clip tables made from the clip files.** Clip lengths, which clips loop, the bite ticks: one source,
    the build failing when the code is behind (as `checkSpotTables` does for the spots). Today they are typed into
    `LionEntity` by hand; a counted loop also played at a random pace on screen until step 5. S-M.
54. **A small test bench per interaction.** A 20-second scene (one carcass, one or two lions), a camera that follows
    the actor up close by its size, takes that start when its clip changes, not after a guessed wait (step 5's film
    missed the feeding and stood too far); the recording's clock in game ticks through stepped takes. M.
55. **A whole-scene offline check that names the parts.** Everyone round the carcass at once at the real sizes, with
    the bystanders, saying which parts are in each other ("mane in ribs", not "body 1.8 px": step 5 changed a reach
    for nothing). Also faster (a pair check takes over 3 minutes). M.
56. **The tools' traps closed.** `lion_models.py game` and `buffalo_models.py game` keep a plan's hand-added parts;
    a clip that depends on another animal's clip (the lioness's holds on the buffalo's death) says so when that clip
    changes. S.

*Neon's small additions to G (2026-11-27, written by Neon on `neon/studio` after reading phases 0 to 3; folded in
here, and on 2026-11-27 written out again in full: the first fold had cut their detail):*
57. **One shared feet rule in `tools/recording.py`** (Neon's; fixes BUGS 456, 457): the replay page's proven foot rule
    (a foot's height in the animal's own model pixels, how near counts as touching, how far a tick counts as sliding,
    the few ticks not judged after ticks not recorded) moved into the one reader, so `replay.py`, `replay_strips.py`,
    `pair_report.py` and `body_course.py` all report the same planted and sliding feet. S, Claude. **Built (phase 2).**
58. **Every maker keeps the plan's other sections** (Neon's; extends 56): one helper (Neon's `preserve_sections`,
    built as `body_plan.keep_sections`) that every maker goes through on export (`lion_models.py`, `buffalo_models.py`,
    the shared writers in `model_parts.py`), keeping `gaits`, `spots`, `holds`, `strikes`, `checks.controller`,
    `checks.drawn_ahead`, `course`, `verify` and added `switches`, so re-exporting a model or its clips never wipes
    Claude's tables. Neon's order: this one first, so exports are safe at once. S, Claude. **Built (phase 2).**
59. **The young's gaits** (Neon's; BUGS 378): calves and cubs play the grown clips shrunk to 0.36 to 0.55, so their
    play speed sits at its cap and their feet skate. `clip_check.py` at a young size, young gait slots in `Gaits` and
    the pace report, so Neon can make stride-matched calf and cub gaits and check them against the young's real speeds
    in the game. S, Claude. **Built (phase 2): the slots and the checks; the young clips themselves wait for the
    animation rework.**

**H. Video review (Neon's; added 2026-11-27; phase 3, part B).** Neon can watch an `.mp4` straight off the disk and
judge timing, weight and rhythm, and the small faults that fall between the frames of a contact sheet (a one-tick pop,
a hitch in a blend).
60. **A recording to video** (Neon's; `tools/replay_video.py`): any replay recording drawn into one mp4, several
    cameras in one frame (side, three quarters, front or above), the world cameras held still as an option (`--fixed`),
    every animal but the picked one see-through as in the strips; played at 1x and slowed (Neon: 1/2 or 1/4), back to
    back, with a bar: tick, clip, play speed, a red mark for each sliding foot. Slow motion gives more pictures to each
    stride or wingbeat, so one-tick pops and blend hitches show. M, Claude. **Built 2026-11-27 (phase 3 part B step 1).**
61. **An offline clip to video, no game** (Neon's): the same mp4 (several cameras, 1x and slowed) straight from the
    model and clip files: one clip on a floor that moves under it at its own stride (a treadmill), a `--switches`
    blend, a `--pair` scene of two animals with the ground and red marks for parts inside each other and for sliding
    feet. In seconds, without Minecraft, so Neon can watch every animation moving before filming it (`runFilm`) or
    building the user's viewer page. M, Claude or Neon. **Built 2026-11-28 for clips and clip changes (phase 3 part B
    step 2: `clip_check.py <animal> <clips> --video [--switches]`, drawn by step 1's own video maker; the `--pair`
    scene left).**
62. **Real footage beside the clip** (Neon's; `--ref <video>`, grows idea 47): a trimmed real-animal video the user
    gives, set beside the drawn clip in one mp4, its stride stretched to the clip's length (cycle-matched), so the real
    cadence, weight shift, spine flow and wingbeat timing are compared with the clip directly instead of from slices of
    stills. S-M, Claude.

**I. Living motion (Neon's; promotes 27 and 28; phase 3, part C).**
63. **The spine bending into turns** (Neon's; promotes 27): one shared rule in the framework's body code
    (`framework/.../entity/body/`, after the ants' `BodyTurn`), driven by each animal's spine, neck and tail chain in
    its body plan: in a turn the body curves into the arc, instead of a 4-block animal spinning up to 20 degrees a tick
    like a statue on a turntable; eased on `DrawClock` (game ticks), so films and play agree. M, Claude.
64. **Weight in starts, stops and banked turns** (Neon's; promotes 28): an optional layer per body plan (limits
    `stop_pitch`, `turn_lean`, `accel_stride`) adding a small wind-up and settle on quick starts, sudden stops and sharp
    banked turns, for runners and fliers alike, eased on `DrawClock`; each animal keeps its own weight (the heavy
    buffalo, the springy lion, the banking eagle) from its plan, with no species rules in the code. M, Claude.
*Claude's note on 63 and 64 (2026-11-27):* the ants' `BodyTurn` bends no spine (it eases the whole body's heading
after the head), and bending a body live and fixing the feet after is the idea that failed twice on the monitor (M1).
So part C measures first (a turning bench), then the user picks between Neon's live layers (63, 64 as written) and
my pick, turn, start and stop clips the game chooses as it chooses a gait. Both stay in the plan till the numbers are
in (`docs/plans/motion-studio-phase3.md`, part C).

## The phases (remade 2026-11-27 with all of Neon's ideas; the user: "Could remake the phase list with all of neons ideas")
Every idea above has one place here. **(Neon's)** marks an idea Neon wrote (57 to 64, and Neon's own order notes);
**(Neon's lane)** one in Neon's lane (the viewer pages, the models, the clips) that Claude wrote.

**Built**
- **Phase 0, eyes on the real hunt** (2026-11-25): 1, 2, 11, 12, 14, 16, 29, 30. 13 is there as the film's `aim` and
  `cam` (cameras round any named animal, sized to it since phase 2), 15 as the film's `until <fact>` (films only: no
  command for the user in play).
- **Phase 1, the lab shows the game** (2026-11-27): 3, 5, 6, 7 (Neon's lane, built by Claude while Neon was out); 8 as
  foot paths in the replay, not dots in the game.
- **The first phase 2, one source of truth for motion** (2026-11-25): 20 to 26.
- **Phase 2, the glue** (2026-11-27): 51 to 56, and 57, 58, 59 (Neon's).
- **Phase 3 part A, partner moves** (2026-11-26): 31 to 34, 36 to 41, and the tests 43 and 44.
- **Phase 3 part B, step 1** (2026-11-27): 60 (Neon's).
- **Phase 3 part B, step 2** (2026-11-28): 61 (Neon's) for one clip and a clip change; its `--pair` scene left.

**Next, in this order**
1. **Step 0, the branches in step** (Neon's "branch sync first"): **Claude's half is done** (2026-11-27, the user's yes,
   branch `claude/master-step0`: the full run, the guard films approved again, `master` holds the Motion Studio's
   phases 1, 2 and 3 part A). **Left, Neon's half:** Neon merges `master` into `neon/studio` before any new clip,
   keeping both the Motion Studio's work and its own flier foundation (`motion_kit.py`, `clip_check.py`,
   `body_plan.py`, `viewer_common.js`); 58 (built) then keeps the plans' tables safe through Neon's exports. Part B
   step 1 and this list (`claude/motion-video`) are not in `master` yet, nor step 2 (`claude/motion-clip-video`).
2. **Phase 3 part B, motion as video** (Neon's): 61's `--pair` scene, then 62 (47 folded into it). Neon's note: 60 and 61 are tools only,
   so they may run beside other work.
3. **Phase 3 part C, living motion** (Neon's 63 and 64, promoting 27 and 28): the turning bench first (how fast the
   lion and buffalo really turn, start and stop in the game, and how far their feet slide then); then turns, then
   starts and stops, built the way the bench's numbers and the user decide: Neon's live layers or my clips.

**Later**
4. **Phase 4, tests and the replay key** (on hold, the user 2026-11-26): 9 (the replay key: the last 20 to 30 seconds
   of every animal near the player saved with the F7 report), 10 (a saved recording turned into a game test, on 9's
   recordings), 18 (the server's ghost, the true box beside the drawn animal: recorded with 9, shown on the replay
   page), 45 (a gait's play speed never stuck at its limit through a run), 46 (same seed, same hunt).
5. **Phase 5, the studio and the user's eye** (new 2026-11-27; Neon's lane): 4 (the clip as made beside the clip as
   the game drew it, in step), 48 (a held key pose against a reference still), 49 (a rehearsal page per pair:
   reference, the offline pair, the game replay, the contact sheet), 50 (the duet watch: the pair refilmed by itself
   when a clip changes), and two overlays moved from the game onto the replay page, where every recording already is
   (my pick): 17 (each animal's clip, clip tick, play speed, gait and stride over it) and 19 (the contact: the jaw
   point, the spot and a line between coloured by the gap).
6. **The animation rework** (every clip redone with these tools; after the Motion Studio): 42 (the drag), part A's
   leftovers (the `cling`, forepaws on the carcass, the looks bugs 466 and 467), the young's own clips (BUGS 378, on
   59's slots), and the clips asked of Neon on `docs/HANDOFF.md` (the lions' `pant`, `cling`, `drag`, the buffalo calf).

**Not kept:** 35 (the drawing-only hold: 34, riding, was built in its place); 8 in the game (the replay's foot paths
show the same).

## Phase 0, built (2026-11-25, branch `claude/motion-studio`, the user's go-ahead)
**What was built (ideas 1, 2, 11, 12, 14, 16, 30; 29 looked at):**
- **Every animal recorded** (`client/BodyRecorder`): `record on 1 all` writes each animal drawn into its own file
  (`body_<its name>.jsonl.gz`, streamed, the walker's `body.jsonl.gz` unchanged for the course), each moment with its
  clips' play speed and how far into each clip, every bone's pivot, and its mind's facts.
- **The mind's facts** (`debug/FilmFacts`, `client/FilmFactsBoard`): an animal says what its mind is doing on the
  game's side (the lion: doing, clip tick, part of the day, temper, hunt stage, prey, holding and where, hunger; the
  buffalo: doing, temper, health, how many hold it, grown or not), read once a game tick while filming.
- **Scenes with their minds on** (`debug/FilmStages`, film step `stage`): a species signs up a scene; the lions' dusk
  hunt is `savanna/.../gametest/LionScenes.duskHunt`, the same code the game test `atDuskAHungryPrideTakesACalfAndFeeds`
  now builds its scene with. Film steps `seed` (the dice set, so takes repeat: three takes started the stalk and rush
  within a tick of each other once the scene is built in the frozen game), `until <fact>=<value> <ticks>` (the game runs
  until a mind reaches that point), `aim` and `cam` (cameras round a named animal; every picture of a burst taken from
  each camera before the game is stepped: one take, three sides).
- **The camera's picture checks for every animal** (`client/FilmChecks`, until now ants only): parts inside blocks (a
  model pixel allowed, as offline), and jumps no longer measured across two takes.
- **The hunt film** `tools/film/lion_hunt.film` (about 3 minutes) and **the report** `tools/pair_report.py` (jaws
  against the prey's body, planted feet sliding, the strike's travel, clip changes, empty clip players).
- **The lion plans** got the clip changes the hunt and feast really make, checked offline: 0 flagged.

**What the films showed (BUGS 372 to 377; numbers from three takes):**
- **372, the worst:** after her first freeze a stalking lioness has no clip at all, so she stands up to full height
  (1.02 blocks over her feet against 0.74 crouched) and glides forward with still legs for 9 to 18 seconds, every
  freeze. A plain bug, not a design question.
- **373:** a holding lioness's jaws are 0.4 to 1.0 blocks off the calf (they touch for 2 to 4 moments in about 22).
- **374:** her planted feet slide 0.10 to 0.26 blocks a tick while she holds a calf that lies still.
- **375:** the strike carries her box 4.1 to 4.8 blocks while the leap clip stays in place (a crouched slide).
- **376:** a one-tick `idle` or `trot` between one-shots (each feeding turn, the grip): a twitch.
- **377:** three lionesses stand through each other round one small calf.
- **The buffalo film (idea 29):** walking up a step with no step moves, its hind feet go up to 0.18 blocks into the
  step: its step moves (buffalo step 2) are needed. The lion was not filmed on a step yet.
- **The checks on big animals:** quiet on gaits (the buffalo's walk 11 flags in three bursts, trot 3); loud on films
  that loop a one-shot clip (the loop's snap back to its start is a real jump in that film, not a game fault) and on
  bursts stepped 4 ticks a picture. Read the hunt's jump flags with the report beside them, not alone.

**What phase 0 did not do:** the replay page (phase 1, Neon's viewer); no fix to the hunt itself (373 to 377 are the
evidence for phase 3's partner moves; 372 is a plain bug for its own session).

## Phase 1, built (2026-11-27, branch `claude/motion-phase1`, Claude covering Neon's lane; the user's go-ahead)
**What was built (ideas 3, 5, 6, 7; foot paths in the replay in place of idea 8's dots in the game), in three stages,
each reviewed by three subagents and their findings fixed:**
- **The replay page (3):** `python tools/replay.py run-film/screenshots/<scene>` writes `references/replay/<scene>.html`,
  one file that works offline: every animal of a film's recording on the recorded blocks and water, each part where the
  game drew it (the recorded matrices, nothing re-imagined), any angle, play at 1/10 to 2 times, a moment at a time,
  the scrub bar, cameras round a picked animal, follow, labels, the pieces, hide an animal. Each camera turns with the
  animal and stands at one distance for its whole recording: fitted to the space the animal takes up in its own frame
  over the recording (a fit to one moment's pose made the zoom jump from moment to moment and a turn crop it, the
  stage 3 review; a strip is fitted to its own moments). Follow keeps a lit camera on the animal, turning with it.
  Click an animal to read its clips, play speed, how far in, standing, and its mind's facts. The page's address holds
  the moment, the animal, the camera and the traces: a link brings back the same view. Game ticks run but not drawn show
  as gaps ("after 61 game ticks not recorded").
- **One clock:** the recorder now writes the client's own tick count (`ct`, counted only while the game runs: not while
  frozen or paused) beside the game time (`ft`); animals are lined up on `ct` plus the partial tick. Older recordings
  fall back to `ft` (the page says so).
- **One reader:** `tools/recording.py` reads every recording (the page, `tools/pair_report.py`, `tools/body_course.py`;
  their outputs checked unchanged).
- **The footfall chart (6):** under the scene, the picked animal's clip and each foot moment by moment (dark on the
  ground, red sliding, light lifted, a dashed blue line for ticks not recorded), three seconds each side of now or the
  whole recording; press or drag on it to go to a moment.
- **Ghosts (7)** (4 or 8, a tenth of a second apart, never across ticks not recorded) and **foot paths** (each foot's
  middle over the last 1.5 s, coloured the same way).
- **Picture strips (5):** `python tools/replay_strips.py <scene> --pick <animal> --from <tick> --to <tick> --every N
  [--cams side,front,above] [--fixed] [--list]` lays the animal at every N-th of its own moments from each camera on
  sheets of ten rows with its clip, play speed and feet (heights in model pixels, a sliding row framed red), its foot
  paths in the pictures, the chart under the last sheet, and a text file: a summary (clip changes, ticks not recorded,
  slide stretches, the body's biggest step) and every moment with clips, feet, the body's drawn step and turn a tick
  and the mind's facts (`references/replay/strips/`). `--fixed` keeps each camera where its first picture was taken (the
  animal's own travel shows); `--list` writes only the text, to find the ticks worth a picture. The page is built afresh
  for each run. For checking a motion before the user sees it.
- **The feet rule (my picks, set from the lion hunt's numbers):** a foot is the last bone of each leg in the body plan
  with the bones below it (as the offline checker); every corner is measured against the recorded ground under it, in
  the animal's own model pixels: on the ground within 1 pixel, touching within 0.25, sliding when its touching corners
  move more than 0.15 a tick (still feet drift under 0.04); not judged in the 3 ticks after ticks not recorded (the game
  eases an animal to its new place over 3 ticks).

**What the page shows in the lion hunt film (to look into, nothing fixed here):**
- Still clips are quiet: graze, rest and feed slide on fewer than 1 standing moment in 100.
- The walk slides on a fifth to a half of its standing moments, the stalk on about 1 in 10; the buffalo's charge and
  trot skate.
- Each switch from `stalk` to `ambush_freeze` slides all four feet 0.2 to 2.7 pixels a tick for about 6 ticks, and early
  in the freeze all four slide the same 0.19 pixels a tick: the body still gliding while the clip stands still.
- `rest_down` and `rest_up` slide on about half their standing moments (paws dragged as she lies down or gets up).
- In `jump_smack` and `stalk_leap_bite` a lioness's front claw reaches 1.5 to 1.7 blocks to either side of her (98 in
  100 of her moments stay within 0.8): a paw flung wide, or something to look at in the clip.
- The cow's `mood_alert`: about 1 foot moment in 8 slides (likely turning on the spot on planted hooves).
- The film itself misses the hold (BUGS 449). Two measuring tools have their own feet rules with faults (BUGS 456,
  457); later, one Python feet rule in `tools/recording.py` matching the page's.

**Other animals in the strips (the user's call, 2026-11-27, "Yes, see-through"):** two tries at judging which animal
stands in the way failed (the prey faded; then crowding pride-mates kept solid), so in a strip every animal but the
picked one is drawn as one even layer of glass (40 in 100, only its nearest surface, no shadow): none hides the animal
checked, and the shape of its prey or holder still shows. The page itself draws them solid.

**What phase 1 did not do:** idea 4 (the clip as made beside the clip as drawn); idea 8 in the game.

## Watch out for
- **Never copy the game's rules into Python.** A lab that re-imagines the game drifts from it; the recording is the
  truth, the lab only plays it.
- **No live bending to reach contact.** Bending the monitor to the ground every frame failed twice (M1); contact is made
  once offline, per size, and played.
- **Fewer rules.** A partner move replaces several hand numbers; if it ever needs a tuning number of its own to look
  right, stop and rethink (two fails, then rethink).
- **Do not design every pair before the second exists.** Build the partner move for the lion and buffalo; the crocodile
  and its prey, a leopard and a tree carcass, or a mother and calf, test whether it is truly shared later.
- **Lanes.** The recorder, film steps, sockets in the game, sync and tests are Claude's; the replay page, the sockets in
  the models, the pair clips and `--pair` sit in Neon's lane (`tools/`, the viewer). `framework/.../entity/body/` is
  shared ground: agree on it in `docs/HANDOFF.md` first.
