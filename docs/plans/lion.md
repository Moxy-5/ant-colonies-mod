# The lion pride (planned 2026-11-25)

The user, 2026-11-25: "the lion is a very complex animal"; the looks and clips are "completely designed in all its glory"
(Neon's models in three male, three lioness and two cub coats; 47 / 47 / 41 clips; face overlays: `docs/handoff/lion.md`);
"just go off of your knowledge of how real lions behave, go". Animals are fun first, inspired by real life (CLAUDE.md,
2026-11-14): every number below is my pick unless marked. The plan was checked before building by four critics
(Minecraft coder, game balancer, looks and fun, fewer rules; 2026-11-25, 4 agents); their changes are merged in, and the
list of what they changed is at the end.

## What real lions do (the ideas used)
- Live in prides: related lionesses and their cubs, held by one male or a coalition of two or three brothers. The
  lionesses often give birth about together and raise the cubs in a creche, any lioness minding any cub.
- Rest about 20 hours a day, in shade in the heat, lying close together, often touching; the males often a little apart.
  Cubs are the busy ones at a resting pride: pouncing on each other and on their mothers' tails.
- Stir in the late afternoon: stretch and yawn, greet each other with head and cheek rubs as they pass, groom one another.
- Roar mostly at dusk, in the night and at dawn, together (one starts, the rest join); a roar carries for kilometres and
  tells other lions whose land this is. Males sniff and scent-mark the land (the flehmen face).
- Travel at night in single file, resting in between; the lionesses do most of the hunting (step 2).
- Toward a person on foot: they lift their heads and stare, then growl with flattened ears, then make mock charges that
  stop short; running away sets off the chase reflex (a lion is faster than a running person), standing still mostly
  ends it. A lioness with small cubs is the touchiest. Lions are sprinters: a chase lasts only a hundred metres or two.
  A pride defends a hurt member, but they swat and back off in a standoff rather than all piling in.
- Cubs keep near the lionesses, call with a little "mrr-row", hiss when frightened, and hide while the adults deal with it.

## Steps (each one session-sized, built and tested before the next)
1. **The pride in the game** (this plan, below).
2. **The hunt:** lionesses stalk, freeze and leap on buffalo; the buffalo herd mobs lions; the kill, the pride eating,
   remains of big land animals. **Waits for buffalo calves (buffalo step 2):** buffalo are laid only with new land and
   never bred, so a hunting pride would empty the savanna for good. Lions join the big-body `MoveRider` then too.
3. **Pride life:** cubs grow (the subadult coat as the life kit's teen stage, then a lioness or a young male through
   `convertTo`, which must carry life, den, coat and flags, tested over a save and reload), young males leave, males of
   two prides bluff and fight.

## Step 1: the pride in the game

### Bodies
- Three entity types sharing one class `LionEntity extends PathfinderMob implements GeoEntity, HasLife`: `lion` (the
  male), `lioness`, `lion_cub`: three spawn eggs, three `/summon` names, each its own box. One goal set for all three,
  asking the lion's form for its clip names (the cub's `cub_call` for `roar`, no fighting clips).
- Boxes (Neon's card): male 1.45 wide x 1.85 tall, lioness 1.20 x 1.55, cub 0.78 x 0.95. Routes 2 x 2 and 2 high for the
  adults, 1 x 1 for a cub. `STEP_HEIGHT` 1.0. `FOLLOW_RANGE` 32 with Minecraft's random bonus taken off in
  `finalizeSpawn` (as the buffalo). Every trip longer than 20 blocks walks in legs (the buffalo's `LEG`, `legPoint`).
- Turning: at most 20 degrees a tick while moving, 10 standing; turning in place by more than 3 degrees a tick plays
  `walk` slowly, so the rump does not skate on planted feet.
- Drawing: the buffalo model's front and hind ground heights (the trunk tilts to the ground under each pair of feet,
  eased over 0.2 s), lifted out of `BuffaloModel` into a shared savanna model part used by both. The `Attention` head
  turn (head and neck toward what caught its eye, about 35 degrees, and a neck lift of about 15 degrees while lying and
  watching a player); the pupils are left to the clips (they move them themselves).
- Hit beads (`HitChain`) at the head and the rump for the adults, measured from the geo; the culling box joins the beads;
  drawn to 128 blocks; `clientTrackingRange(10)`.
- Life files `animal_life/lion.json`, `lioness.json`, `lion_cub.json`: males 0.97 to 1.03, lionesses 0.95 to 1.0, cubs
  0.85 to 1.0 of the cub model, all the spotted coat; no growing or breeding yet (step 3).
- Coats: a byte rolled once (when laid or at `finalizeSpawn`), saved and synced, read by the renderer: male Pride King
  1 in 2, Golden Monarch 1 in 3, Kalahari black mane 1 in 6 (its own geo, `lion_kalahari`); lionesses tawny, 1 in 4
  pale sand. The **elder** (a saved flag, the first lioness laid, or the oldest-ranked if she is gone) wears the elder
  coat and leads every trip.
- Health and hits, at Normal difficulty: male 50 health, armour 3, hit 9; lioness 40, armour 2, hit 7; cub 10, no hits.
  Knockback resistance 0.6 adults. Drops none (the remains come with the hunt).
- Death: the body stays for the `death` clip (1.2 s); Minecraft's tip-over off.
- Water: floats and swims out; water keeps its normal route cost so routes go round it.
- No block moves in step 1 (the shared `MoveRider` is sized for the otter); Minecraft's own 1-block step with the tilt.

### Shared with the buffalo (lifted, not copied; the buffalo then calls them and its `land` tests run again)
- `animal/HomeGroup`: mates by a shared home (for lions the den), rank by UUID, nearest of the group to someone (ties by
  id), the egg joining the nearest home within a reach given by the caller, a dice spot from a home and a number.
- `animal/WarnMemory`: anger with an expiry, creative and spectator players left out, per-player "warned" memory.
- Trips: `legPoint` and the leg numbers, the staggered setting-off by rank, the stand finder (dry `GroundTop` ground
  within 4, never a log, litter, mound top or sand, its columns clear), the shore stand, `SwimOutGoal`.
- Each species keeps its own temper goal and stages (lions have BLUFF, buffalo BUNCH: one machine for both would cost
  more than it gives).

### Clips and the face
- Body controller: `SteadyController`, 4-tick blend (Neon's card). Loops play at 0.92 to 1.08 of their speed, fixed per
  lion from its id.
- Face controller: plays `face_<mood>` only while the body plays a plain loop (`idle`, `walk`, `trot`, `stalk`,
  `gallop`, `sit`, `rest`); during any one-shot clip and any whole-body `mood_*` clip it stops (`PlayState.STOP`, as the
  otter's head layer), because every lion clip keys the face bones and the later controller's keys win: a face over the
  roar would hold the jaw shut. A check (clip_check or a film): a roar with the face set to alert shows the jaw open at
  0.7 to 1.75 s.
- No claws controller in step 1: the gallop and strike clips carry their own claws.
- The mood is a lookup, not a judge: from the temper stage, the day's act and the form. No temper: lying is `sleepy`,
  else `content`; greeting or nuzzling `content`; a male at a patrol stop `flehmen`; WATCH `alert`; WARN, BLUFF, ATTACK,
  CALM and the ring `angry`; a cub during a fight `fearful`; a cub watched by a player `curious`. A calmer mood waits 3 s
  so the face does not flicker. Standing still in a temper stage plays the whole-body `mood_*` clip.
- Hit and sound moments count from the entity's own tick, and goals run every tick (`requiresUpdateEveryTick`).
- `swipe_combo`'s hits land at ticks 9 and 19 (Minecraft ignores a second hit within 10 ticks).

### The pride, laid with the land
- One pan in three (a dice roll from the pan's middle) also gets a pride, laid by `LionPrides.lay` right after the pan's
  buffalo herd (`PanFeature`); a pride about every 330 blocks of big savanna, rarer than the herds, as real lions are.
- A pride: one male (one pride in four two brothers), 2 to 4 lionesses, and in two prides out of three 2 to 4 cubs (2 to
  3 with two males): at most 9 lions.
- Its den: the shade of a living savanna tree whose foot lies 46 to 64 blocks from the pan's middle, past the buffalo's
  farthest grazing. Read from the tree starts directly (`level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS)
  .getAllStarts()` for chunks within 4 of the pan's chunk; never the references beyond the 3 x 3, which crash
  worldgen), keeping `GiantTreePiece` and `FloodplainTreePiece` trees that are alive and standing, measured from
  `foot()`; the den is the foot plus the trunk's half-width plus 3, away from the pan. No such tree: an open spot 52
  from the pan's middle at a dice angle. Only the spot is saved; its ground is checked in game when the lions get there.
- Every member saves the den and the pan (its water); the den is the pride's name, as the buffalo's home.
- The lions are laid on spots like the herd's (whole body clear, dry ground, inside the chunks the pan may write), with a
  tree margin of 3 (a lion is 4.66 long) and at least 3 from every buffalo laid in the same call; too few spots: fewer
  lionesses (never fewer than one male and two), else no pride, logged as the herd's "no room". They then walk to the
  den. Kept for good, spawning group vanilla `MISC` (the spawner never tries it); the world's spawner never makes lions.
- A spawn egg's or `/summon`'s lion takes the den of the nearest lion within 16 (the buffalo's reach) that has one, else
  the den is where it stands and it has no water.

### A pride's day (one goal, four acts, read from the shared clock `Colony.dayTimeAt`; tick 0 is 6:00)
- **Rest, from arrival at the den (or 7:00) to 16:00.** Beds in a row under the shade, one heading for the whole pride
  (along the shade, away from the trunk): side by side 1.7 apart, a cub 1.0 from the lioness beside it, the males at the
  row's end 3 farther out; a second row 5 behind for a big pride. Order by rank: the elder in the middle, the lionesses
  out from her, cubs between lionesses, males last. Each bed must be flat (front and hind feet columns at one height) and
  pass the stand finder; one that fails moves outward along the row. Lying: `rest_down`, `rest`; every 90 to 200 s an
  adult gets up (`rest_up`, sometimes `rest_stretch_up`), shifts, sits or grooms (`groom` blends from `rest`), and lies
  down again.
- **Cubs at the den:** every 20 to 40 s a cub gets up and does one thing, then lies down: `play_pounce` at a sibling or a
  lioness's tail 2 blocks off, facing it; `play_wrestle` beside a sibling; or `mama_nuzzle` at a sitting or lying
  lioness's chest, and she answers with `cub_groom`. Any lioness mothers any cub (the creche).
- **Waking, 16:00 to 17:30:** one by one (a few seconds apart, by rank) they `rest_stretch_up`; greetings happen between
  two standing lions only, both playing `pride_greet` at once, side by side facing opposite ways, heads level about 1
  block apart on the side the clip sweeps to (the real head rub as two lions pass); each adult greets one or two mates.
  Then they sit (`sit_down`, `sit`) facing out.
- **To the water, 17:30 until all have drunk (21:00 at the latest):** every trip uses one rule: all set off for the same
  leg points, one after another in rank order (the elder first, the lionesses, the cubs, the males last), a lion's body
  length plus 1 block apart (about 4 s at a walk); more than 16 blocks from its place a lion trots, walking the last 16
  (cubs walk with their `walk` clip sped up to the adults' pace). No lion paths to another lion. Each drinks at its own
  shore spot (`drink` once or twice), then sits by the water. No pan: the act is skipped.
- **Night, after drinking until the den is reached at dawn:** a patrol to three or four points 30 to 60 blocks from the
  den (dice from the den and the night's number, no world read), the last one the den itself; at each point they lie or
  sit a while; the males stop to sniff (`mood_flehmen`).
- **The roar chorus:** at 18:30, about 0:00 and 5:30, wherever they are. The elder starts (a male when there is no
  elder); each other member starts its own roar at its own delay of 1 to 5 s (rolled from its id, not chained), so the
  whole pride roars within 6 s; cubs `cub_call`. A walking lion stops to roar. Waking from a bed skips the clock: the
  dawn chorus also fires on the first tick inside 5:00 to 7:00 if the pride has not roared since 0:00. Volume: male 8
  (heard about 128 blocks off), lioness 4, cub 1.
- A place it cannot reach: wait at least 30 s before trying again, meanwhile by its nearest mate; land not loaded: wait
  (the buffalo's rules, review R9-16).
- **Lions and buffalo look at each other** (the hunt and the mobbing wait for step 2): the buffalo's `Attention` gives a
  lion a strong pull within 24, and its WATCH stage takes a lion within 12 (stops grazing, faces it, `mood_alert`), with
  no warning or charge; the lions' `Attention` gives buffalo a strong pull, so they stare at the herd as they pass.

### Temper toward players (one goal, stages with one rule and one deadline each, as the buffalo's)
- Never angry at a pride mate; creative and spectator players are only looked at. Distances to WATCH and WARN are from
  the lion's middle; BLUFF and ATTACK reach are the gap from its head (the buffalo's `headGap`), because a male's head
  is 2.3 ahead of its middle.
- **WATCH:** a player noticed within 24 (half when crouching): a lying lion lifts and turns its head and stays down, a
  standing one stops and faces them (`mood_alert`); back to its day after 10 s unless the player comes nearer.
  Sprinting beyond 16 sets nothing off.
- **WARN:** a player within 10 of the adult pride member nearest them (for a lioness in a pride with cubs, measured to
  her or to the nearest cub, whichever is nearer): that lion gets up, `mood_angry`, a growl; remembered per player for
  30 s.
- **BLUFF:** a player still within 6 after the warning: `bluff_charge` (male and lioness; a short gallop first if the
  lunge cannot reach), stopping at a head gap of 2; it never touches a player standing still. Then CALM. A lioness in a
  pride with cubs skips the bluff and goes to ATTACK.
- **ATTACK** (one stage, the chase folded in): started by a head gap under 1 that the player closed (never the end of
  the lion's own bluff), by a player back within 6 whom it bluffed in the last 30 s, or by a player moving away faster
  than 5 blocks a second within 12 of a lion that is warning, bluffing, attacking or calming at them (sprinting, a horse,
  a boat: the chase reflex). It gallops at about 7.5 blocks a second (the gallop clip sped up to the measured pace) and
  strikes with `swipe`, `swipe_quick`, `bite`, a male also `swipe_heavy` and `swipe_combo`, from a gallop `gallop_swat`
  or `pounce` (struck from about 4 blocks, carried forward over the leap); the hit lands at the clip's hit tick if the
  head bead touches. After each strike it backs off half a block and stands `mood_angry` for 1 to 1.5 s. Ends: a player
  who only came near, or ran, gets 1 strike (2 from a lioness with cubs) or 4 s of galloping (3 s at first: a lion gains only about 1.7 blocks a second on a sprinting player, measured 2026-11-25), whichever is first; a lion
  that was hurt fights for 8 s (the buffalo's `REVENGE_TICKS`) and gives up only then or past the follow range of 32
  (review R9-15), so an archer at 20 is answered. A plain sprint started within about 8 blocks is caught once; from 12 it
  gets away; a sprint-jumper gets away.
- **A pride defends:** when a lion is hurt, it and one mate fight the attacker (the male if within 24, else the nearest
  lioness; the male hurt: the nearest lioness). The other adults stand, close round the hurt one and face the attacker
  (`mood_angry`, the buffalo's bunch radius), each by seeing a mate is angry, never by being told; a ringing lion steps
  in only if a fighter dies or the attacker hurts it. Cubs run to the nearest lioness, hiss (`mood_angry`) and lie flat.
- **CALM:** 10 s standing its ground (`mood_angry`, tail lashing), then back to its day.
- Speeds are measured in a game test, never computed (BUGS 246); each clip's rate follows the measured stride.

### Sounds (vanilla, re-pitched, my picks)
- Roar: the ravager's roar at pitch about 0.7; growl: the wolf's growl at about 0.5; cub call: the cat's purreow at about
  1.3; hurt and death: the ravager's hurt and death pitched; steps: the wolf's step, soft.

### Tests (category `land`, class `LionTests`)
- A pride is laid with one of a set of pans in new land: one or two males, 2 to 4 lionesses, at most 9 lions, every
  body clear and 3 from the buffalo, kept for good; its den in a living tree's shade when one is near, and no buffalo
  grazing spot (any bout) within 8 of the den.
- At midday they lie at the den: the beds as turned rectangles (body length x width) never overlap and never touch the
  trunk; cubs get up and play; a nuzzle is answered with a groom.
- Waking: stretches and greetings with both lions standing; the chorus at 18:30 has every member roaring within 6 s.
- To the water: no two middles in the file under 4 apart; every lion drinks before the patrol starts.
- At night they stay together (none more than 16 from the elder) and are lying at the den by 7:00; the clock jumped
  from 4:00 to 6:00 gives one dawn chorus.
- A roar with the face set to alert has the jaw open (the face layer stops).
- A survival player 16 off: lying lions turn their heads and stay down.
- A player walking up: one lion warns at 10, bluffs at 6, the bluff never hurts a player standing still; closing to a
  head gap under 1: exactly one strike lands, then it calms; a second player gets their own warning.
- Running: a player moved away at 5.6 b/s from 6 is struck exactly once; from 12, never.
- A lion shot from 20 reaches the archer; only it and one mate strike; the other adults ring and face; cubs go flat by a
  lioness.
- A lioness in a pride with cubs warns when the player is 10 from a cub and does not bluff.
- Gallop about 7.5 blocks a second, walk about 1.2 (measured); swims out of deep water; the death clip plays out; an egg
  lion joins a pride; coat and elder survive a save and reload.
- The buffalo stop and face a lion walking past at 10; the buffalo's own `land` tests still pass after the lift.

### Requests for Neon (nice to have, not blocking)
- A `pant` loop (after a chase, in the heat), a `scent_mark` (the male's scrape and spray), a `roar_lying` (lions often
  roar lying down), a cub `rest` that tucks against a lioness.

### What the critics changed (2026-11-25)
- Minecraft coder: the den read from tree starts within 4 chunks (the old way found almost no trees or crashed
  worldgen); the face layer stops during one-shot clips (it would have shut the jaw in the roar); no lion follows
  another (stalls, jams, reload); trips in legs with a follow range of 32; coat and elder saved, not worked out from the
  id; tree margin 3 and room for the buffalo.
- Game balancer: only two lions fight, the rest ring and face (a whole pride killed an iron player in 2 s); strikes
  paced with a back-off, one strike at a player who only came near; the chase at 7.5 not 6 (6 never caught anyone);
  bluff and attack measured from the head; the den past the buffalo's grazing; trotting on long trips; revenge for 8 s
  past 16 blocks; at most 9 lions.
- Looks and fun: waking moved to 16:00 so it happens in daylight; the file spaced by body length; beds side by side in
  rows, flat; the buffalo's ground tilt and the head turn; cubs playing at the den now (from step 3); greetings placed
  for the clip; buffalo and lions look at each other; the dawn chorus after sleeping in a bed; the male's roar heard far.
- Fewer rules: chase folded into attack; one travel rule for every trip; the mood a lookup from the temper stage; a
  creche instead of saved mothers; roar delays not chained; dawn merged into the night (four acts); the group
  bookkeeping lifted into shared parts the buffalo uses too; cubs one coat and one size band in step 1.

## Agent budget (the user's quota; read before every workflow)
The user, 2026-11-25: "you can honestly use a natural workflow whenever you want to this session ... just keep it to 6
agents and below". Agents only where an independent read is worth it; code is written by one mind (parallel writers
clash and lose the thread). The four critics above cost about 500,000 tokens between them (they read the buffalo code
deeply), so a review is worth it once per step, not per change.
- **Design critics: 4 agents, once per step.** Minecraft coder, game balancer, looks and fun, fewer rules.
- **Building: 0 agents.** The game tests, `clip_check.py` and films are scripts, not agents.
- **Review after each step: at most 6 agents.** Three readers (bugs, Minecraft coder and lag, the house rules), then
  one skeptic each for the grade A findings only, at most 3.
- **Never:** loop-until-dry, 3-vote juries, a review of a review, agents for searches Claude can do with a few reads.
