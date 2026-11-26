# Motion Studio: seeing the game's real motion, and animals that touch (ideas, 2026-11-25)

**Ideas only, nothing built or decided.** The user's ask (2026-11-25): now that the land animals are coming, the
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
missing the carcass, the head snap. The first job is to film them (see "The order").

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

## The order (my recommendation)
- **Phase 0, eyes on the real hunt (a day or two, Claude):** 1, 2, 11, 12, 16, 14, plus the quick fixes 29 and 30.
  Then look at the films and the recording before changing anything ("find the cause before fixing jank").
- **Phase 1, the lab shows the game (Neon's viewer):** 3, 5, 6, 7 on the phase 0 recordings. After this the user can
  study any real motion from any angle.
- **Phase 2, one source of truth for motion:** 20, 21, 22, 23, 24, 25, 26. Each removes a copy or a hidden difference.
- **Phase 3, partner moves (the lion and buffalo first):** 31, 32, then 36 and 37 offline, then 33 with 34 or 35 in
  the game (pick after seeing the phase 0 films), then 38, 40, 41. The hunt's oval and fixed ticks go.
- **Phase 4, the replay key (9)** and the tests (43 to 46).

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
