# The animation engine: ants with personality (planned with the user, 2026-10-15)

From the planning session (2026-10-15). The user asked Gemini how to build a framework for lively animations; the
cartoon parts of that advice (squash and stretch, wind-ups) were dropped for real insect body language. The user's
aim, in their words: "the whole body animated like when an ant goes up its body properly curves and maybe an ant stops
and looks at you turning his head left to right curiously; ants are really cute and have a lot of personality ... its
the little things". More little things will be added to this plan later (the user's wish).

## Real behaviour this is built on (as explained to the user)
- **A hard body with joints.** Head, chest (mesosoma) and gaster are rigid; the bends are at the neck, the waist
  (petiole and postpetiole) and the elbowed antennae. Nothing squashes. Follow-through only where those joints are.
- **Over an edge.** Going from floor to wall the front tips up first, the joints bend so the body hugs the corner for a
  moment, then the rear follows. The head stays turned to the surface; the legs spread wider on a wall.
- **Gait.** Tripod gait at walking pace (front and back leg of one side with the middle leg of the other). Carrying a
  heavy load, ants keep more legs on the ground at once (slower, closer to a wave gait). At walking pace a real ant's
  legs step about 10 times a second: to our eyes they flicker.
- **Noticing.** Harvester ants (Pogonomyrmex) forage by day and have fairly good eyes; they react to movement. A worker
  freezes, lifts its head and forebody, points both antennae at the moving thing and waves them, turning its head to
  place it; a still shape is soon ignored. Macrotermes workers are **blind**: they feel vibration through the ground
  and freeze with antennae raised.
- **Personality is real.** Workers of one colony differ consistently in boldness, activity and exploration (animal
  personality studies in ants, e.g. Temnothorax, Myrmica). Foragers (older ants) are bolder than nurses.
- **The little things (first list, all real):** cleaning an antenna by pulling it through the comb on the front leg
  (the strigil); running a leg through the mandibles; tapping antennae with a nestmate met on the way (often a quick
  check and on); a short stop to feel the ground; a flinch at a passing shadow; a stretch on waking (reported in ant
  sleep research, Cassill et al. 2009; less well studied).

## The user's decisions (2026-10-15)
1. **Leg speed: compare in the viewer first.** Real speed (legs flicker) and slower ants with visible steps, side by
   side in the ant pose viewer; the user picks. Either way feet must not slide.
2. **Noticing: movement only.** An ant notices a player who moves nearby; a player standing still is soon ignored and
   the ant goes on. (Real.)
3. **What it does depends on personality.** Most just look; a bold ant steps closer with antennae out; a shy one backs
   off. (Real.)
4. **Termites feel, not see.** Walking near makes termite workers freeze and feel with raised antennae; sprinting or
   jumping shakes the ground more and sets them off from further. They never turn to look at the player.

## How it fits the three layers (CLAUDE.md "Ant minds")
- **Mind:** what is noticed and what the ant does about it: `NoticeGoal` (stop, orient, look, step closer or back off)
  and later `GreetGoal` (two nestmates tap antennae). These only say "look at that" or "go to this cell" (the legs do
  the walking, as always). Personality traits live on the insect so a smarter mind can read them later (bold ants
  forage further, fight harder in M8, ...).
- **Legs:** unchanged (`AntNavigator`, `AntPathFinder`).
- **Body:** everything else in this plan: the clips, the layers, the live bends.

## The engine's parts
### 1. The motion library (Python, `tools/ant_motion.py`)
One library that writes every insect's animations from real-ant numbers, used by `tools/ant_models.py` and
`tools/termite_models.py` (species-neutral: termites get the same gait engine with their own numbers).
- **Gait engine:** tripod and loaded gait; stride length from the leg's real length and swing, so the walk's speed per
  cycle is known exactly (the game then sets the playback speed from the real walking speed: no sliding).
- **Antenna engine:** sweeps, ground taps, pointing at a target, the elbow lagging the base.
- **Body engine:** waist sway against the stride, gaster pumping (breathing), head turns.
- **Little things:** short one-shot clips (`groom_antenna_l`, `groom_leg`, `feel_ground`, `flinch`, `stretch`, ...),
  each in a few variants so no two ants look the same.
- **Bone map:** clips name bones through a lookup table, so the user's Blockbench worker (M2) only needs a new table,
  not new clips. The user's bone names become the standard (CLAUDE.md).

### 2. Checks before the user sees anything (`tools/anim_check.py` plus the viewer)
- **Foot slide:** works out where each foot tip is through a walk cycle; while a foot is on the ground it must stay put
  relative to the ground at the walk's speed. Fails with the leg and the moment.
- **Clipping:** a leg passing through the body or another leg.
- **Loop seams:** a looping clip ends where it starts (no jump each cycle).
- **Joint limits:** no joint bends further than a real ant's can.
- **Looking:** I open `references/ant_pose_viewer.html` in my built-in browser and look at frames myself before
  showing the user (catches what numbers can't: does it read as curious?).

### 3. Layers in the game (client side)
- **Split the one animation controller into parts** (GeckoLib lets several controllers run at once, each moving its own
  bones): `legs` (the six legs), `body` (forebody, waist, gaster), `head` (head, antennae, mandibles), `extra` (the
  little things, played now and then). Walking legs and a looking head then play together. To check in step 1: when
  two controllers touch the same bone, the later one wins (so each bone belongs to one controller).
- **The live layer** (`client/BodyLanguage`, run from the model's `setCustomAnimations`, as `TermiteQueenModel` already
  does for the swelling queen): adds on top of the clips, every frame:
  - **each ant's own clock:** its own start time and a slightly different speed, so 50 ants never move in step;
  - **walk speed:** playback from the real distance moved and the stride (replaces today's guess
    `limbSwingAmount * 6`);
  - **the edge curve:** the rear of the body follows the front's tilt a moment later (the gaster uses the body tilt
    from a few ticks before), so the waist bends while going over an edge, onto a wall, onto a ceiling or up a ramp;
  - **the head look:** head and antennae turned toward what the ant is looking at, in the body's own frame (so it
    works on walls and ceilings too), within the neck's real reach; beyond it the forebody turns as well.
- **Skipped far away:** ants beyond a set distance skip the live layer and the little things (DESIGN.md already has
  "Ants beyond a set distance skip animations").

### 4. Personality (traits on each real insect)
- Three traits, each 0 to 1: **boldness** (stay, step closer or back off), **curiosity** (how often and how long it
  looks), **restlessness** (how often it fidgets and grooms, how long it pauses).
- Drawn when an insect becomes real, from ranges in the species file (new section `body_language`, species-neutral);
  older ants lean bolder (foragers vs nurses). Saved with the insect. An ant that goes back into the numbers (the
  virtual population) loses its traits; a new real one draws new ones (the numbers carry no individuals).
- Synced to the client (the live layer and the little things use them).

### 5. Noticing (`NoticeGoal`, mind layer)
- **Ants (sight):** a player within the species' sight range (my pick: about 6 blocks) who moved since the last check.
  Chance and look time by curiosity. The ant stops, lifts its forebody, points its antennae, turns its head to follow
  the player; head tilts left and right while the player keeps moving. The player stands still: interest fades in a
  few seconds and the ant goes on. Bold: a few steps closer (stops about a block off). Shy: backs off a step or two,
  mandibles open. Never while carrying, in an alarm, or on a job with a deadline (my picks).
- **Termites (vibration):** walking within about 3 blocks, sprinting or jumping within about 6 (my picks): freeze,
  antennae up and trembling, a second or two, then go on. Soldiers: maybe a head bang at a strong shake (ask the user
  first; the alarm already has its own rules).
- The look target is synced (an entity id), so the client's head look follows it.
- Species file: `body_language.sense` = `sight` or `vibration`, plus the ranges.

### 6. The little things, batch 1
Antenna cleaning (strigil), leg cleaning, feeling the ground, flinch at a passing shadow (a player or mob moving over
it), stretch on waking (with the sleeps already picked). Picked by the user in the viewer first, a few variants each.
They play only when the ant is idle or pausing, never mid-step.

### 7. Hook up what is already picked
`sleep` and `sleep_twitch` by turns while resting; `idle_hot` and `walk_hot` on hot ground (the animations exist in
`tools/ant_models.py`; the steam is drawn by the game, not the model).

## Build order (each step small, tested by the user before the next)
1. **Groundwork:** the motion library and checks; the controllers split; each ant's own clock; walk speed from the real
   stride; the leg speed comparison in the viewer (decision 1); hook up the sleeps and the hot look. Test in game: 10
   ants standing about never move in step; feet don't slide at any speed; resting ants sleep.
2. **The edge curve:** waist bends over edges, onto walls and ceilings, up ramps; legs wider on walls.
3. **Noticing:** `NoticeGoal`, the head look, personality traits (needed for bold/shy); termites' vibration freeze.
4. **The little things, batch 1** (viewer pick first).
5. **Greetings:** two nestmates meeting tap antennae (this is also what the door rule of brain step 4b counts, so the
   touches it counts can be seen).
- **Later, not planned in detail:** feet that follow uneven ground (each leg reaching to the block under it), the
  loaded gait with heavy seeds, the termites' own little things, the M2 model swap (only the bone map changes).

## Tests
- Game tests (server side, what can be checked headless; a new category `body`, added to `TestFilter.CATEGORIES`):
  traits drawn, saved and loaded; an ant notices a moving player (a fake player) and not a still one; a bold ant
  steps closer, a shy one backs off; a carrying ant or one in an alarm does not stop; a termite freezes at a jump
  nearby and ignores a still player.
- Python checks (`tools/anim_check.py`): foot slide, clipping, loop seams, joint limits for every clip, run each time
  the animations are written.
- The look itself: the viewer (me first, then the user), then the user in game.

## Open, to ask the user when we get there
- The leg speed (decision 1, after the viewer comparison).
- Sight range, look time, how close a bold ant comes (my picks above).
- Soldiers' reaction to a strong shake.
- More little things (the user will add ideas).
