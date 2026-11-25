# The Cape buffalo, the first savanna land animal (planned 2026-11-25)

The user, 2026-11-25: Neon's buffalo is "perfect" (the model, paint and all 23 clips of workshop v12; this sentence is the
clips' approval); "we can add the buffalo, then after we can add the lion which will be more complicated so that will be a
separate session". Animals are fun first, inspired by real life (CLAUDE.md, 2026-11-14), so the numbers here are picks
("my pick"), not researched facts. The plan was checked before building by four critics (Minecraft coder, game balancer,
looks and fun, fewer rules, 2026-11-25); their changes are in it.

## What a real Cape buffalo does (the ideas used)
- Lives in herds round water it reaches every day; drinks at midday, never far from a river or pan.
- Grazes in the cool morning and evening; lies down to chew the cud in the midday heat and the deep night, close together.
- Watches, snorts and paws, then charges; a hurt buffalo turns on whoever hurt it. The herd bunches and faces a threat
  instead of every animal attacking (also BUGS 272: every member running the same attack rule multiplies it).
- Bulls are bigger than cows. Wallowing, calves and lions come later (step 2 and the lion session).

## Step 1 (2026-11-25): the buffalo in the game

### Body
- `BuffaloEntity extends PathfinderMob implements GeoEntity, HasLife` (like the hippo: `CrocodileEntity.isPrey` takes only a
  vanilla `Animal`, so crocodiles never drag a grown buffalo in; a crocodile against a calf is a later choice made there).
- Goals in `animal/BuffaloGoals.java` (the bull shark's shape); `client/BuffaloModel`, `client/BuffaloRenderer`.
- Box 1.6 wide x 1.9 tall at size 1: every size gets 2 x 2 routes, 2 high (the biggest bull, 1.03, is 1.96 tall). The
  hump shows 0.3 blocks through a 2-high roof: accepted. `STEP_HEIGHT` 1.0 said out loud (the hippo's; the default 0.6
  makes it hop). Follow range 32.
- Two hit beads (`HitChain`) at size 1: head ahead 1.35, up 0.2, 1.1 wide, 2.05 tall; rump ahead -1.3, up 0.1, 1.0 wide,
  2.05 tall (measured from the geo by the Minecraft-coder critic). The culling box is the walking box joined with the
  beads (BUGS 271 cause 4: a long body blinks out at the screen edge); drawn to 128 blocks; `clientTrackingRange(10)`.
- The model's origin sits at the front legs (front hooves 0.2 ahead of it, hind hooves 1.46 behind): the model draws
  10 px toward the head so the middle between the feet sits on the box's middle; the trunk tilts to the ground under the
  front and hind feet (two ground heights, eased over about 0.2 s), so stepping up a block the chest goes up and the hips
  follow instead of the hind legs hanging in the air. The shared `GroundBend` if it fits a standing body, else the
  two-point tilt in the model.
- Eyes and head from the shared `Attention` (it sees spectators too, so a watching user gets looked at).
- Loops (idle, rest, mood_*) play at 0.92 to 1.08 of their speed, fixed per buffalo from its id, so a herd does not
  breathe and flick its ears in step.
- Turning at most about 20 degrees a tick (vanilla allows 90, which swings a 3.8-block head 3 blocks in one tick).
- Water: Minecraft's float goal and `setCanFloat(true)`; it floats low (back and head out, the walk clip at swimming
  pace); drowning stays on. Water keeps its normal route cost, so routes go round water.
- Health 60, armour 4, knockback resistance 0.8, attack 8 (my picks: tough, below the hippo's 80 and bite 10; an iron
  sword needs about 10 hits). Drops 3 to 5 raw beef and 1 to 3 leather (remains of big land animals come with the lions).
- Death: the body stays for the whole 1.4 s `death` clip (`tickDeath` 30 ticks, as the bull shark's); Minecraft's own
  tip-over off (`getDeathMaxRotation` 0: the clip rolls it).
- Sounds: vanilla ones re-pitched (my picks): grunts and the bellow from the cow at about pitch 0.55, the snort from a
  horse's, the hooves and the hit from the ravager's.
- Life file `animal_life/buffalo.json`: bulls 0.97 to 1.03, cows 0.86 to 0.93; born grown, no breeding yet. The life clock
  is the kit's (`AnimalLife.tickEvery`); the buffalo never calls `life.tick()` itself.

### Herds: laid with pans, kept for good
- `PanFeature.place` lays a herd right after it lays a pan (as `OxbowFeature` lays a hippo pod after an oxbow). Pans are
  already about one to every 192 x 192 blocks and keep 48 from rivers, so no new site rule is needed and a herd's water
  always exists. River herds wait for step 2 (crossing water, hippo banks, crocodiles).
- A herd of 3 to 5 (my pick): one bull, the rest cows, each on a spot inside the pan's own chunk at least 3 from its edges,
  on dry grass past the pan's ring, with its whole body clear (3 x 3 columns, 3 high: BUGS 271 cause 2).
- The herd's home is the pan's middle and its across (saved by every member). The home is the herd's only name: a mate is
  a buffalo with the same home. Home never moves; if the pan has no water at midday the herd lies down without drinking.
- Kept for good (`removeWhenFarAway` false). Spawning group: vanilla `MobCategory.MISC` (my pick, from the fewer-rules
  critic: the spawner never tries or counts MISC, unlike a group with no spawn list, which it still tries in every chunk,
  review R8-12). The world's spawner never makes buffalo; only land made after this has herds.
- From a spawn egg or `/summon`: it takes the home of the nearest buffalo within 16 blocks that has one, else its home is
  where it stands (across 0: no water, it grazes and lies down round that spot).

### A buffalo's day (one goal, read from the shared clock `Colony.dayTimeAt`; tick 0 is 6:00)
- The day in hours, wrapping past midnight: graze 5:00 to 11:00 and 15:00 to 22:00; midday 11:00 to 15:00 (drink, then lie
  down by the water); deep night 22:00 to 5:00 (lie down where the evening grazing ended).
- The bout number goes up at 5:00 and at 15:00: `b = 2*floor((t+1000)/24000) + ((t+1000)%24000 >= 10000 ? 1 : 0)`. The
  herd's grazing spot comes from dice only, `RandomSource.create(home.asLong() ^ b * K)`: an angle and a distance
  `across/2 + 8 + roll*14` from home (past the pan's ring, about 24 blocks out at most), no world read, so every mate gets
  the same spot whatever land is loaded.
- Each buffalo's place is the act's centre plus its own offset: an angle from its id spread by the golden angle and a
  radius per act (graze 6, lie down 3, bunch 2.5): mates end up about 4 or more apart grazing, 3.5 lying (bodies are 3.8
  long). Its own stand is the nearest dry grass ground within 4 of its place (`GroundTop`, grass or dirt under it, never a
  mound top, log or sand); if none, it stands by its nearest mate.
- Leaving for a new place, mates set off one after another (2 s apart, in the order of their ids), so the herd strings
  out instead of starting as a block. Trips longer than a route search walk in legs. More than 8 blocks from its place
  it trots.
- Grazing: 2 or 3 `graze` clips on one stand, then a step of 1 to 3 blocks to the nearest green plant near its place; the
  little things (`ear_flutter`, `shake_off`, `snort`, a rare bull `bellow`) between grazes, never cutting one. At the
  chewing part of a graze the plant at its jaws (about 1.05 x size ahead of its middle) is cropped: a tall plant to a
  short one, a short one left (grass never grows back in Minecraft, so a herd never bares the ground). What it eats is
  the hippo's rule (`edible`: vanilla grass and ferns, the mod's tufts only while green or drying; dry ones are the
  termites'), lifted with the cropping into one shared savanna part (`animal/Grazing`) that the hippo now calls too.
- Midday: each walks to its own spot on the pan's shore (the shore stand nearest its offset round the pan, water
  straight ahead along a block axis), plays `drink` once or twice, then lies down by the shore (`rest_down`, `rest`,
  `rest_up`). Lying long (midday and night), each gets up now and then (about every 60 to 120 s: `rest_up`, a
  `shake_off` or a few steps, `rest_down`).
- A place it cannot reach: it waits at least 30 s before trying again (review R9-16) and meanwhile stands by its nearest
  mate. Land not loaded: it waits; it never rolls another spot (rolling again would split the herd).
- Clip moments (the crop, the hits) count from the entity's own tick, and every goal runs every tick
  (`requiresUpdateEveryTick`), because goals stepped every other tick miss moments and run timers at half speed
  (HISTORY 2026-11, review R9-19).

### Temper (one goal, stages with one rule and one deadline each, as the raid rewrite)
- Never angry at another buffalo; its hits land only on its target.
- WATCH: a player (not creative or spectator) noticed within 12 blocks (`Attention.playerInReach`, nearer when
  crouching): the buffalo stops and faces them (`mood_alert`) for up to 10 s, then goes back to its day with only its
  eyes on them, unless they came 3 blocks nearer or sprinted.
- WARN: within 7 of a buffalo that is the herd's nearest member to that player (each works it out from its own mate
  list, ties by id): `snort` then `paw_ground`, remembered per player for 30 s (review R9-13: one timer per animal let a
  second player be charged unwarned).
- CHARGE: still within 5 of that buffalo after the warning, or within 3 at any time: it gallops at about 5 blocks a second
  (my pick: faster than a walking player at 4.3, slower than a sprint at 5.6, so sprinting away works). When the target is
  about pace x 1.2 s ahead it plays `charge_headbutt` and keeps running until about its tick 16, easing off to the hit at
  tick 24; the hit lands if the head bead touches the target then. Already within reach: `headbutt` (hit about tick 9) or
  `gore` (about tick 8). A charge at a player who only came too near ends after one hit, with a big toss (knockback about
  1.5 and a little lift); then CALM.
- CALM: 10 s of standing its ground (`mood_angry`, a `paw_ground`), then back to its day.
- Hurt by anything (but another buffalo): that buffalo charges whoever hurt it, as many hits as it can land in 8 s, and
  gives up only by that time or past the follow range (32): never at 16 blocks (review R9-15: a hippo shot from 17 blocks
  never answers). Its herd's bull joins the charge; the cows stop, bunch round the hurt one and face the attacker
  (`mood_angry`), each by seeing that a mate has a target, never by being told.
- Speeds are measured in a game test, never computed (BUGS 246): Minecraft's push goes about with the square of the speed
  setting, so 5 blocks a second is about 1.7 x a 0.2 base, the walk clip's 0.61 about 0.6 x. The clips' rate follows the
  measured stride (`charge` made for 2.45 b/s plays about 2x; walk for grazing steps, walk at about 1.6x for travel, trot
  to catch up).

### Steps
- No block moves in step 1 (the Animal Maker Engine's step 5, "or a reason it needs none"): the shared `MoveRider` was
  sized for the otter (one block of headroom, the face read from a 1-wide route) and would start climbs it then cuts under
  a low tree, and `step_up_1000` carries the body 4 blocks in 1.6 s, faster than its charge. Minecraft's own 1-block step
  with the trunk tilt instead.

### Tests (a new category `land`, class `BuffaloTests`)
- A herd is laid with a pan in new land, every member on dry ground with its whole body clear, kept for good.
- Mates graze apart (no two middles under 3.5), crop tall grass to short and never bare a block; dry tufts untouched.
- At midday they walk to the pan, drink at the shore and lie down by it; at night they lie down where they grazed.
- A survival player standing 10 blocks off: the herd stares, then grazes again within about 15 s.
- A player walking up: one buffalo warns, then charges once and the charge's hit lands on a player standing still; a
  second player gets their own warning.
- A buffalo shot from 20 blocks charges the archer; only the bull joins; cows bunch and face.
- The charge's measured pace is about 5 blocks a second (4.5 to 5.5), the travel walk about 1 block a second.
- A buffalo knocked into deep water floats and swims out.
- The death clip plays out before the body goes.
- An egg buffalo next to a herd takes its home.
- Crocodiles never take a grown buffalo.

## Step 2 (later)
- Block moves: `MoveRider` made fit for big bodies (body length and height, the face read from the ground), which also
  moves the hippo onto it (review R9-42); Neon slows `step_up_1000` and adds a cow size.
- Wallowing in the pan's wallow and river mud at midday, rubbing on trees and mounds (`horn_rub`), mud against flies.
- River herds; calves (live birth); a crocodile taking a calf at the water; lions hunting the herd (the lion session).
- The hippo's territory goal onto the buffalo's temper stages (fixing R9-13 and R9-15 for the hippo too).

## Requests for Neon (in `docs/HANDOFF.md`)
- `death` ends with the chest 0.21 blocks over the ground: land it.
- `block_moves/buffalo.json` `lead_ticks` 4 against the plan's blend 5: export with 5.
- Mud masks (`buffalo_mud_low.png`, `buffalo_mud_full.png`) from `tools/mud_masks.py`.
- Nice to have: `graze` split into `graze_down`, a looping `graze_loop` (head down, chewing, a slow shuffle step) and
  `graze_up`, like the rest clips; a long-stride `charge` made for about 5 blocks a second; a slower `step_up_1000` and a
  cow size for step 2. Check in the film whether `drink`'s muzzle reaches a flush water surface.
