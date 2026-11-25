# Lion step 2: the hunt (planned 2026-11-25)

The user, 2026-11-25: "start step 2, you can use 6 agents for this task to speed it up and look for bugs". Step 1 is in
`docs/plans/lion.md` (built). Animals are fun first, inspired by real life (CLAUDE.md, 2026-11-14): every number here is
my pick. Agents for this step: 2 design critics (done, their changes merged below), then at most 4 for the review
(3 readers, 1 skeptic): 6 in all.

## What real lions and buffalo do (the ideas used)
- Lionesses do most of the hunting, together, at dusk, at night and at dawn: they fan out, creep up crouched, freeze
  flat when a head comes up, creep on while heads are down grazing, then rush from close. Lions are sprinters: the rush
  is short, most hunts fail.
- They pick the weak: calves first. Two or three hold big prey (rump, throat); a calf one lioness can take.
- Buffalo run, then turn and fight: bulls and a calf's mother charge lions off a held member, tossing a lioness; lions
  back off from a charging bull.
- The kill feeds the whole pride: the others come, the male takes the best place (growls), cubs squeeze in; full, they
  lie up by a big kill the next day. A pride eats every few days.
- Buffalo calves are born one at a time, stay at the mother's side, run with her.

## Why calves come first
Herds are laid only with new land and never bred, so a hunting pride would empty the savanna (step 1's plan).

## Part A: buffalo calves
- **Breeding** (`buffalo.json`: breeds, `local_limit` 7 within 48, `breed_every_days` 4, young 3 days at 0.4, teen 3
  days from 0.7): no courting walk (a herd grazes together). Every 200 ticks a grown cow ready to breed, with room (the
  buffalo within 48 plus the calves being carried there, under 7) and a grown bull of her herd within 16, has bred (only
  she is marked: the bull is never gated). She carries `GESTATION` 48,000 lived ticks (a countdown, saved as
  `Carrying`), then her calf is born at her left side (`setUp(young)`, age 0, her herd, `Mother` saved).
- **Size and health:** a calf is drawn small (the grown look until Neon's calf model) and its health is 60 times its
  size share (a newborn about 24, a teen about 42, a cow about 54), set when it grows (`grew()`).
- **The calf** (`CalfGoal`, built): at its mother's left side at her pace (her herd's nearest cow if she is gone); lies,
  rises and grazes with her; in danger to her far side. It never warns or fights; a hurt calf brings its mother's
  charge as well as the bull's. Grown, the herd's day takes it.
- **Laid calves:** a herd of 4 or 5 gets one calf in two, beside its first cow.
- **Loot:** buffalo drop leather and beef only when a player kills them.

## Part B: the carcass (a kind of the river's remains)
- At the death clip's end a buffalo's body becomes a `RemainsEntity` of kind `BUFFALO` (a plain entity, not a dying
  mob: the critics found a kept dying buffalo drawn red, still growing and breeding, eating clicks and unkillable). Its
  meat is `38 x its size` bites (a cow about 34, a calf about 15), rotting a bite at a time over a game day, then bones
  a day (the remains' own clocks); a player's hit breaks it. It saves the den of the pride whose lion killed it.
- Drawn with the buffalo's model in a one-frame `carcass` pose (the death clip's last frame, written into
  `buffalo_carcass.animation.json` from Neon's clip; Neon may replace it), the buffalo's size, its heading.

## Part C: how buffalo see lions (one rule, `BuffaloEntity.seesLion`)
- A frozen lion (`ambush_freeze`) is never seen. A lion moving crouched (stalk) is seen within 12 by a buffalo with its
  head up (not lying, not in the head-down part of a graze). A walking lion is seen within 12, a galloping one within 24.
  The same rule drives the drawn gaze (`Attention`), WATCH and FLEE.
- **FLEE** (temper weight 2: below CHARGE, BUNCH and CALM): a buffalo that sees a hunting lion (stalking or galloping)
  runs straight away from it at its charge pace until it sees none, at most 5 s. Calves run at their mothers' sides.
  A hurt mate turns the herd to fight (the step 1 mate rules: the bull, and now a hurt calf's mother, charge).
- **Held** (a lion clinging to it): it never charges its holders (it would spin after a lion on its rump); it pulls
  away (FLEE from the nearest), its pace times `1 - 0.5 x holders` (`setSpeed`, counted fresh each tick: two stop it).

## Part D: the hunt (`HuntGoal`, between the temper and the day)
- **Hunger:** each lion counts lived ticks since its last meal (`SinceMeal`, saved). The pride is hungry when its
  leader (the first lioness in the pride's order: the elder while she lives) is past `HUNGRY` 2 game days. Laid prides
  start 0 to 2 days in; lions from eggs start hungry (so a hunt can be set up in creative).
- **When:** two tries a day, each on a standing herd: **dusk** (once the pride has drunk, until the herd lies at 22:00)
  and **dawn** (5:00 to 7:00). The hunters: the pride's lionesses. The male and the cubs lie where they are until a kill
  calls them or the try ends.
- **The prey** (the leader picks once a try, the others read her pick; never told): the nearest herd within 96 of the
  den, in land that is ticking; a calf or teen if any; a grown one only from a herd of 5 or more grown (the weakest by
  health share, then the smallest); none, no hunt.
- **Stages, one rule and one deadline each; the hunt's state lives on the lion, so a short interruption resumes it:**
  1. **SPREAD** (deadline 60 s): each hunter walks to its own start spot about 20 blocks from the prey on the den's side,
     fanned by rank (0, then 35 degrees each side, then 70), on standing ground (the day goal's stand finder). STALK
     starts for all when all are at their spots, or at the deadline.
  2. **STALK** (deadline 90 s): crouched (`stalk`) straight at the prey at about 0.7 blocks a second; while any buffalo
     of the herd within 12 has its head up it **freezes** (`ambush_freeze`; at most 10 s, then on, and likely seen).
     Reaching leap reach unseen it leaps (`stalk_leap_bite`): the strike.
  3. **RUSH** (part of the fight): when the prey's herd flees or a mate struck: all gallop at the prey.
  4. **HOLD:** its head touching the prey's hit beads it strikes (`stalk_leap_bite` from a stalk, `jump_smack` from a
     gallop) and clings at its own place (rank 0 the rump, 1 the neck, 2 and 3 the flanks), moved there with collisions,
     biting (`bite`; on a calf knocked down, `takedown_pin`). Each bite skips Minecraft's hit cooldown and does its
     attack (7). A calf (24) dies to one lioness in about 4 s, to two in about 2; a cow (54) to three in about 3, against
     the bull's charge that comes in about 3.
  5. **BACK OFF:** any lion hurt by a buffalo is never angry at it (no revenge on buffalo, ever): it lets go and backs
     off 8 blocks from that buffalo for 3 s.
  - **The fight's deadline:** 20 s from the first rush (rush, hold, back off and rush again within it). Then the try
    is over: the hunters lie down where they are.
- **The feast:** a pride member whose mate's kill (a buffalo carcass with its den) is within its mates' reach (96) goes
  to it. Places round it by the feast order (the male first, then lionesses, cubs last): the chest, the rump, the two
  flanks, two more beside; one displaced by one before it growls (`mood_angry`, 2 s) and takes the next. Feeding
  (stand-in until Neon's `feed`): `drink` (head down) and `takedown_pin` (paws on it, tugs) by turns, a meat bite every
  10 s; full after its share (male 6, lioness 4, cub 2 bites) or the meat gone; then `SinceMeal` 0 and it lies by the
  kill. A kill with meat left keeps the pride by it until 16:00 the next day (they lie up by it in daylight), then the
  day goes on as usual.

## Numbers that must fit (the game balancer)
- A pride's den is 46 to 64 from its pan: its own herd is in reach; neighbours' rarely.
- A pride is hungry every 2 days, tries twice a day; small herds lose only calves (the floor); a herd of 3 to 5 with 2
  to 4 cows makes a calf every 1 to 2 days up to 7 buffalo near. No pride can empty a herd.
- The freeze rhythm (heads up, heads down) and the 10 s freeze limit decide whether the stalkers get near unseen: most
  rushes then start from far, and most fail (gallop 7.5 against a fleeing buffalo's 5).

## Tests (category `land`)
1. A ready cow with her herd's bull carries, gives birth; the calf keeps at her side and grows; no breeding past 7.
2. A dead buffalo leaves a carcass of its size; bites eat it; it survives a save.
3. At dusk a hungry pride's lionesses spread, stalk a herd with a calf, take the calf; the pride feeds; all fed; they lie
   by the kill.
4. A stalking lioness freezes while a buffalo's head is up near her and creeps while heads are down.
5. Hunting lions send the herd running; a held cow brings the bull; a lioness hit by a buffalo backs off, never at it.
6. A fed pride does not hunt; a small herd's grown buffalo are never taken.
7. Herds are laid with calves.

## Requests for Neon
- Lion: `feed_down`, `feed` (head down tearing, a tug back), `feed_up`; a `cling` (forepaws on a standing buffalo's
  back); `pant`.
- Buffalo: a calf model (no horns, big ears, short body) or the horns as a bone; a `carcass` pose of its own; bones.

## What the critics changed (2026-11-25, 2 agents)
The carcass is a remains kind, not a kept dying buffalo; one sight rule with head-up freezing (the 6/10 numbers never
met: no freeze ever); dusk and dawn tries on a standing herd (not deep night on a lying one); the herd floor; bites skip
the hit cooldown and a calf's health follows its size (not "bites times three"); lions never take revenge on buffalo and
a held buffalo never charges its holders (no spinning brawls); the hold's slowing by one `setSpeed` rule (attribute
modifiers could crash and stick); the leader is the first lioness, not the elder flag; STALK waits for all at their
spots; the hunt's state on the lion; a buffalo's loot only for a player; the room to breed counts calves being carried;
the 14-block kill warning and the per-lion fill hunger dropped; feeding slow enough to be seen; the pride lies up by a
big kill.

## What the review changed (2026-11-25, 3 readers and 1 skeptic after the build)
- **The grip:** a lion grips at the free place nearest where it struck (the rump, the neck either side, the flanks), not
  at a place by its rank (it was shaken off at once, and ranks shifted as mates came); it holds from the strike's hit on
  (the clip's last ticks left the prey free, so a bitten cow charged its biter); it lets go only when 4 blocks off its
  place (caught on a block while the prey pulls away).
- **The rush is a sprint:** at most 6 s at a gallop without a grip, then it gives up (was a 20 s fight). Lions close about
  2.5 blocks a second on a fleeing buffalo, so a rush from about 15 blocks or more fails: most hunts should fail, as the
  plan says. Measure in a soak.
- **A try ends with the lion lying where it is for 30 s** (it was got up at once by its day). The dusk try starts once
  the whole pride has drunk (the leader alone went first); a hunter whose prey dies goes to its kill before any end of
  the try; a fight runs on past the end of the window to its own end; a mate's rush makes the spreading hunters rush too.
- **The dawn try starts at 5:30** (the herd is up), not 5:00.
- **The freeze looks at any grown buffalo near** (the same ones that would see it), not only the prey's herd.
- **A lion that stopped is no longer "galloping"** (the herd fled from lions lying by a kill).
- **Kills found again after a reload:** a lion looks for its den's carcass within 32 of itself too; it feeds at a kill
  when 5 minutes or more since its last meal (no "last feast" memory needed); the meat gone after its first bite counts
  as its meal.
- **The carcass:** left unless the buffalo dies in water deeper than half its height (a kill in the shallows of the
  pan stays); owned by the pride whose lion dealt the killing blow (a player's kill is nobody's); drawn to 96 blocks.
- **Calves:** a calf at its mother's side no longer stops her grazing (she never grazed, so her head stayed up and no
  stalk could work); calves never answer for the herd when a player comes near (a nearer calf hid the grown ones);
  a laid calf stands on a checked laying spot and lions are laid clear of it.
- **Feeding and lying places:** stepped onto with the move control (a route ends a block short, BUGS 371); a place not
  reached in 5 s is given up.
- **After the skeptic:** a held buffalo pulls one way (the way it first pulled; away from a lion on its flank it turned
  after it and circled for good); the feast keeps through a save (`FeastOf`, `Ate`, `Full`, `FeastEvening`) and a
  carcass whose land unloaded is found again; a mate counts ahead at the carcass only once it is there (the lionesses
  growled as the male set off); bare bones never hide a fresh kill; no meal without a bite; the dusk try waits for the
  leader's own drink this evening; a leap in the air at the window's end still lands.
- **Not done (minor):** the carcass's hitbox is the fish remains' small box (the monitor eats at its middle; a player
  breaks it only there); a calf can be born into water; two prides on one herd may both take the rump; a calf with no cow
  left stands still.
