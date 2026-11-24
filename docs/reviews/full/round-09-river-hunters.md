# Round 9: the river hunters (reading round, 2026-11-23 to 24)

**What was read:** the shared animal kit (`framework/src/main/java/com/antcolonies/animal/`, 17 files, 1,680 lines) and, in
`savanna/src/main/java/com/antcolonies/savanna/animal/`, the crocodile, otter, hippo, Nile monitor and bull shark with
their goal classes and helpers. No code was changed, no test or soak was run (other sessions had the test servers), so
**no lag number here is measured**: every lag finding says which scene would measure it and is marked "to measure".

**How to read a finding:** each has a number that is kept for good (`R9-3`), what is wrong in plain words, what the
player would see, where, the proof, the fix proposed, and a rating: **A** the player can meet it, **B** it will cost us
when building on this, **C** a tidy-up. **Proven** means the path was walked through the code line by line; **likely**
means one link could not be checked without running the game (the finding says which).

Two facts about Minecraft that many findings below rest on (checked in the patched 1.21.1 sources in the Gradle cache):

- **How often a goal is asked.** `Mob.serverAiStep` runs the full goal check (`canUse` of every goal that is not
  running, `canContinueToUse` of every one that is) every second tick, and on the ticks between only ticks the running
  goals that ask for every tick. So "every time it is asked" below means 10 times a second for each animal.
- **Reading land far away.** An animal that is ticking has loaded land for at least 32 blocks round it. A block read
  farther off than that can land in a chunk that is not loaded, and `level.getBlockState` then loads or makes that
  chunk on the spot, on the game's main thread. `level.isLoaded(pos)` guards against that, but it also says yes for a
  chunk that is still being made, and the read then waits for it.

---

## Part 1: the shared animal kit (`framework: animal/`)

### R9-1 (A, proven): a courting animal whose suitor dies or leaves the loaded land is "taken" for good

**What is wrong.** When an animal goes to a mate, the kit marks both of them as courting each other
(`AnimalLife.partner`). The mark on the one being courted is only ever taken off by the suitor's own goal when it stops
(`SeekMateGoal.stop`) or when the pair breed. If the suitor dies on the way or during the courtship, or its chunk
unloads, its goal never stops (Minecraft does not stop a dead or unloaded mob's goals), so the mark stays. A marked
animal is passed over by every other suitor, and several animals' own rules read the mark as "I am courting now":
a crocodile does not hunt, a tilapia male does not behave as free on his nest, a courting fish is not hunted.

**What the player would see.** Kill (or let something eat) a male crocodile in the middle of his courtship show: the
female never hunts again and never takes another mate until the world is reloaded. The same for a tilapia male on his
nest pit whose female is eaten on her way to him (common: crocodiles, catfish, otters and the shark all eat tilapia):
he holds his pit but no female can ever pick him, so that pit never breeds. Reloading the world cures it (the mark is
not saved).

**Where.** `framework/.../animal/SeekMateGoal.java:59-76` (`start`, `stop`), `:116-122` (`findMate`'s filter);
`AnimalLife.java:46-47, 244-257`. Read by `CrocodileEntity.java:1856` (no hunt while `partner() != null`),
`CrocFlySnapGoal.java:47`, `CrocodileBreeding.java:191-200, 256`, `TilapiaNestGoal.java:154`, `HuntTilapiaGoal.java:64`,
`CatfishEntity.java:468-471`, `CrabEntity.java:303`, `BullfrogEntity.java:764-767`.

**The proof.** `start()` line 63-64 sets `animal.partner = mate` and `mate.partner = animal`. The only places that
clear a partner are `stop()` lines 70-73 (run by the suitor's own goal) and `AnimalLife.bred()` line 246 (a search for
`setPartner(` in all main code finds no other). A dead mob runs no goals (`LivingEntity.aiStep`: the AI step is skipped
when it is dying) and nothing calls a goal's `stop()` on death, removal or unload. `findMate` line 122 only takes an
animal whose `partner()` is null or is the searcher itself. A kind where the courted one never seeks on its own
(`seeksMate()` false: the crocodile female, `CrocodileEntity.java:389-391`; the tilapia male, `TilapiaEntity.java:322`)
has no goal of its own that would overwrite the mark, so it stays until the animal is loaded anew.

**Fix proposed.** Make the mark prove itself: `AnimalLife.partner()` answers null (and clears the mark) when the
partner is not a live, loaded animal that still names this one as its partner (`ServerLevel.getEntity(uuid)`; one
cheap look-up). One place, no new timer. Test: a male crocodile killed mid-courtship; the female hunts and can be
courted again.

### R9-2 (B, likely, to measure): every search for entities in the world walks every body hit box of every animal

**What is wrong.** Since 2026-11-22 six animals carry a chain of hit boxes along the body (`HitChain`: crocodile 10,
plus the hippo, monitor, bull shark, catfish and otter; 35 links in all across the six kinds). NeoForge keeps every
such box of the whole dimension in one list, and **every** entity search any code makes (`Level.getEntities`,
`getEntitiesOfClass`, also Minecraft's own: each living mob looks for mobs to push every tick) walks that whole list
after its normal search. So the cost of the boxes is paid by every mob in the world, not only by the animals that
have them: it grows as (all hit boxes loaded) times (all entity searches a tick).

**What the player would see.** Nothing by itself; a little tick time lost that grows as the river fills (catfish are
common and each carries a chain). My rough sum for a full river (about 120 boxes, 300 to 500 searches a tick): 0.3 to
0.6 ms a tick. That is a guess from counts, not a measurement.

**Where.** NeoForge's `Level.getEntities` (patched `net/minecraft/world/level/Level.java:880-884` and `:927-935`:
`for (PartEntity<?> p : this.getPartEntities())`), `ServerLevel.getPartEntities` (`dragonParts.values()`);
`framework/.../animal/HitChain.java`, `BodyPart.java`; the chains at `CrocodileEntity.java:295-305`,
`HippoEntity.java:254`, `MonitorEntity.java:262`, `BullSharkEntity.java:192`, `CatfishEntity.java:143`,
`OtterEntity.java:288-291`.

**The proof.** The two loops above were read in the patched sources; they run for every call, whatever the search box.
The one link not checked: the real cost per box (it needs the profiler).

**To measure.** The full-river perf scene with Minecraft's profiler: the `getEntities` counter and the tick time with
the chains on, against the same scene with `isMultipartEntity()` answering false.

**Fix proposed (only if the number is worth it).** Fewer beads where they buy little (the crocodile's six tail beads
could be three; the catfish, a common fish, may not need a chain at all). Not a rewrite: the chain itself is sound.

### R9-3 (C, proven): the guard goal searches for intruders when it has none to look for, and twice a turn when it runs

**What is wrong.** `GuardPlaceGoal.canUse` searches for living things round the guarded place every time it is asked,
and while it runs it searches twice (once in `canContinueToUse`, once more in `tick`). Two of its three users pass a
see-off reach of 0 and an intruder test that always says no (the young crocodile's crèche, the bullfrog father), so
their search can never find anything, yet it still runs 10 times a second for every young crocodile with a crèche.

**What the player would see.** Nothing; a small cost (a search of a one-block box).

**Where.** `framework/.../animal/GuardPlaceGoal.java:41-49, 52-58, 72-76`; users `CrocodileEntity.java:331, 347`,
`BullfrogGoals.java:97`.

**Fix proposed.** Skip the search when the see-off reach is 0; keep the last search's answer for `tick`.

### R9-4 (C, proven): small things in the kit that add up or mislead

- **`AnimalLife.numbers()` is looked up several times a tick for every animal** (`AnimalLife.java:70-72`, called from
  `tick`, `stageNow`, `ripe`...): each is a registry look-up and a map look-up. Tiny; keep the answer per animal and
  drop it on `/reload`.
- **A clutch of eggs counts its kind and its watchers before it looks at its own age** (`EggClutchBlock.java:44-48`):
  a 96-block-wide entity count on every random tick of every clutch, most of them days from hatching. Ask the age first.
- **`LifeNumbers.check` does not check the size ranges against `max_size`** (`LifeNumbers.java:209-216`,
  `AnimalLife.java:328`): a life file whose `size` goes over its `max_size` (default 1.5) loads with no error, and
  each such animal quietly shrinks to `max_size` the first time it is saved and loaded. No file today has this (all
  nine were read); it is a trap for the next edit.
- **`Attention`'s class comment says the crocodile "still keeps its own copy"** of the eye rules, and it does
  (`CrocodileEntity.lookAround`, lines 958-1006, the same logic): two places to change for one behaviour.
- **`Attention.tick` (the client) searches every quarter second for as long as nothing has caught the eye**
  (`Attention.java:66-82`): a 32-block-wide search per animal on screen, 4 times a second. Drawing side only.

### What is solid in the kit

- **`RoomToBreed`:** both limits (own kind within 48 blocks, and Minecraft's own group cap) are asked at mating and
  again at hatching, and a hatching is capped to the room left. The spectator case and the "no spawner count yet" case
  are handled on purpose. Breeding cannot run away through the kit.
- **`SeekMateGoal`'s pacing:** it looks for a mate only every 40 ticks, spread by the animal's id, and asks the cheap
  questions first. No lag here.
- **`Carried`:** young are saved with the parent and cannot be doubled (the young is discarded as it is taken, and let
  out again with its own saved data).
- **`ClutchClock` / `EggClutchBlock`:** nothing ticks while eggs wait; the game time decides. Sound.
- **`BodyPart.getType`** (BUGS 284's fix) keeps the boxes out of searches by animal type.
- **`AnimalLives`:** a broken life file is logged and the default used, never a crash; reloaded with each world.

---

## Part 2: the crocodile (`CrocodileEntity`, `CrocodileBreeding`, `CrocFlySnapGoal`, `RiverCover`)

All four files were read whole. Line numbers are `CrocodileEntity.java`'s unless another file is named.

### R9-5 (A, proven): the crocodile hunts the player's own animals: tamed horses, donkeys, llamas, name-tagged and leashed stock

**What is wrong.** The hunt is meant to leave pets alone ("no tame pets"), but the rule only knows wolves, cats and
parrots (Minecraft's `TamableAnimal`). A tamed horse, donkey, mule, llama or camel is a different kind of class and
counts as wild. So do farm animals with a name tag or on a lead (fish with a name are spared, other animals are not).
Small ones (a named chicken or rabbit, a calf) are "swallowed whole": deleted with no drops.

**What the player would see.** Park a tamed, saddled horse within 4 blocks of the river and get off: a hungry
crocodile comes out, hauls it into the water and death-rolls it. A pen of named cows by the water is emptied over
time (one big kill feeds a crocodile for a day).

**Where.** `isPrey`, lines 2435-2469 (the pet test is line 2465); `isSmall` 2510-2512; the swallow, 1973-1980.

**The proof.** Line 2465: `!(e instanceof Animal) || e instanceof TamableAnimal pet && pet.isTame()` is the only pet
test. In Minecraft 1.21.1 `AbstractHorse extends Animal implements ... OwnableEntity` (read in the patched sources): it
is not a `TamableAnimal`, so a tamed horse passes. Line 2436 spares only an animal being ridden or riding. Nothing
asks `hasCustomName()` or `isLeashed()` except for fish (2521) and frogs and tadpoles (2445, 2453).

**Fix proposed.** One "the player's own" test used for every prey: owned (`OwnableEntity` with an owner), named,
leashed, or saddled. Test: a tamed horse and a named cow at the water's edge beside a hungry crocodile are left alone.
(The bull shark shares `isFish` and has its own prey rule: see part 6.)

### R9-6 (A, proven; the user's call): in Peaceful the crocodile still seizes the player and holds them through twelve rolls

**What is wrong.** No river animal asks the world's difficulty. In Peaceful, Minecraft turns a mob's bite into no
damage at all, but the crocodile's grab is not damage: it seats the player in its jaws, the game is told the player
cannot step off, and it rolls twelve times before letting go.

**What the player would see.** Playing in Peaceful, a player at the water's edge is grabbed, dragged out and rolled
for about 42 seconds, taking no damage, unable to get off except by hitting the crocodile for 4 hearts; half a minute
later it may do it again. A nest mother's bite knocks the player back with no damage.

**Where.** `isPrey` 2459-2462 (only creative and spectator are spared); `HuntGoal.grab` 2279-2295; `roll` 2333-2345
(12 rolls of 70 ticks); `SavannaEvents.onMount` (`SavannaEvents.java:197-203`, no stepping off).

**The proof.** `Player.hurt` (patched sources, line 903-919) scales a mob's attack by difficulty and answers "not
hurt" when the result is 0. `grab()` does not look at that answer: line 2286 only asks whether the prey is still
alive, then seats it (2291). The dismount is cancelled for as long as the crocodile holds it. Rolls deal 0, the player
never dies, so only the 12-roll limit (2341) ends it: 12 x (40 + 30) ticks = 42 s.

**Fix proposed, for the user to pick.** My recommendation: in Peaceful the crocodile does not hunt players and a
mother only hisses (animals and fish are hunted as always). The same question goes for the hippo's and the bull
shark's bites (parts 4 and 6).

### R9-7 (B, likely): a crocodile whose basking spot lies beside a hippo swims to it and turns away, again and again

**What is wrong.** Two rules judge the same water's edge and do not know of each other. The crocodile picks where to
bask (a remembered sand bank, a row of basking crocodiles, the nearest bank, the half-out edge) without looking for
hippos; and it keeps 7 blocks clear of any hippo. The sand spot is remembered and "kept going back to", so with a
hippo resting within 7 blocks of it the crocodile sets off for the sand, is turned away 12 blocks, waits about 10
seconds, and sets off for the same spot again, for as long as the hippo lies there and the sun is up.

**What the player would see.** By a pod's pool, a crocodile shuttling toward the sand bank and away from it every 15
to 20 seconds all day instead of basking or settling somewhere else. If it does get to lie down first, each time it
is moved on it has a 1 in 4 chance of leaving droppings.

**Where.** `AshoreGoal.canUse` 1773-1792; `findSandBank` 1209-1247 (line 1213 keeps the old spot); `taken` 1179-1201
(crocodiles only); `KeepClearGoal` 1417-1503 (`NEAR` 7, `AWAY` 12, `CLEAR` 10; priority -1 at line 338);
`BaskGoal.stop` 1339-1353.

**The proof.** Walked: `AshoreGoal` (priority 3) runs only in the water with the sun wanted; it paths to `sandAt`.
`KeepClearGoal` (priority -1, the same MOVE flag) starts when a hippo is within 7 blocks times its size and ends past
10; it stops `AshoreGoal` without touching `sunIn` or `sandAt`. Back in the water `AshoreGoal.canUse` is true again
after its wait of 100 asks (200 ticks) and `findSandBank` line 1213 answers the same spot, since `taken` knows no
hippos. The link not checked in play: how often a pod rests within 7 blocks of river sand (the hippo's bank rest and
pool are by the same banks, so it should be common).

**Fix proposed.** One shared "may I lie here" test for all four spot pickers that also refuses a spot within the
keep-clear reach of a hippo (and forgets `sandAt` when a hippo turned it away). No new timer.

### R9-8 (B, to measure): every crocodile route may search four times as far as a zombie's, and failing routes are asked again and again

**What is wrong.** To reach far sand banks the crocodile's "follow range" was set to 64 blocks. Minecraft uses that
one number as the size of every route search the animal makes: up to 1,024 squares looked at for each search (a
zombie: 560), over a copy of the land 72 blocks round it. A search to somewhere reachable ends early; one to
somewhere it cannot get to runs to the limit. Several goals ask again every few ticks:

- the charge up the bank asks a new route to the prey every 5 ticks for up to 4 seconds (2030-2031);
- the stalk asks every 10 ticks for up to 30 seconds (2014-2016, 2152-2153), and a stalk that timed out picks the
  same prey again after 10 seconds (`giveUp(200)`, 1996): the lesson of BUGS 230 (the monitor) is not applied here;
- the walk to a nest spot asks every 20 ticks for up to a minute (`CrocodileBreeding.java:512-516`).

BUGS 243 (open) says no crocodile can climb a bank a full block over the water, so "prey or a nest spot it cannot
reach" is a known, real case: a cow on a two-block bank is charged at with 16 full-length searches in 4 seconds.

**What the player would see.** Short stutters when a crocodile goes for something up a bank it cannot climb. Not
measured.

**Where.** `createAttributes` 317-321 (`FOLLOW_RANGE` 64); Minecraft's `PathNavigation` (patched sources line 59:
`FOLLOW_RANGE * 16` squares) and `PathFinder` line 67.

**To measure.** A scene with a hungry crocodile in a pool and a cow on a bank two blocks over the water; the
profiler's `pathfind` section and the tick time during the charge.

**Fix proposed.** Keep the follow range at a normal 24 to 32 and reach far sand in two or three legs; let a hunt
remember the prey it failed to reach for a minute (as the monitor and catfish do); ask the charge's route every 10
ticks.

### R9-9 (B, to measure): two wide searches of the land done in a single tick

**What is wrong.**
- **The nest spot** (`CrocodileBreeding.nestSite`, lines 544-583): every column within 40 blocks (about 5,000), up
  to 5 heights each, and for each open bank cell a check for water within 3 blocks that reads up to 98 blocks when
  there is none. With no river sand in reach it never stops early (the cut-off at line 556 needs both a sand and a
  bank spot found). My sum: 100,000 to 250,000 block reads in one tick, maybe 5 to 15 ms. It runs once when a female
  starts to nest, again after each spot that failed, and **every 30 seconds for as long as she finds no spot at
  all** (`waitSite = 600`, line 344); her eggs never time out, so that can go on for good.
- **The sand bank** (`findSandBank`, 1209-1247): every other column within 64 blocks (about 3,300), 4 heights each,
  at most once a minute for each crocodile that wants the sun and has no company: about 13,000 block reads, maybe
  1 to 2 ms.

Both guard far land with `level.isLoaded`, which is right, but it also says yes for a chunk still being made while
a player travels, and the read then waits for it.

**What the player would see.** A hitch now and then near rivers, worst where a ripe female has nowhere to nest.

**To measure.** A game test or perf scene that calls each search on a wide river with no sand, timed.

**Fix proposed.** Search outward in rings and stop at the first ring with an answer; check "water within 3" only for
cells that already passed the cheap tests and whose column is near water; spread a wide search over several ticks.
After a few failed tries let the eggs be lost (she goes back to her life).

### R9-10 (B, the read proven, the stall likely): a mother taken from her nest reads the nest's land wherever it is

**What is wrong.** A mother asks her nest's block once a second (`MotherGoal.pick`, `CrocodileBreeding.java:273`) and
when she thinks of basking (`motherWork`, 444) with no check that the nest's land is loaded. At home she is always
near it. But Minecraft lets a player put a lead on a crocodile (it is not a monster) and scoop one into a boat (its
box is under a boat's width). Led or boated more than about 32 blocks from her nest, each read loads the nest's
chunk on the spot, once a second, and her guard goal plans a full-length route home every second
(`GuardPlaceGoal.java:62-63`). The hippo's pool has the same hole (R9-17).

**What the player would see.** Lag while a mother crocodile is led or boated away from her nest. A rare thing to do.

**Fix proposed.** Ask `level.isLoaded(nestAt)` first and treat a nest out of loaded land as "no work now"; give up
guarding from beyond some distance. Bigger question for the user, noted in the report: whether crocodiles and hippos
should take a lead at all.

### R9-11 (C, proven): a crocodile hit while afloat does not react, and a nest mother shot from afar stands still

- `hurt`, line 935: only a crocodile on land or basking is startled by a hit. The comment on `startle` (942-945) says
  a startled one in the water "sinks out of sight a few seconds", but a hit in the water never startles it: a
  crocodile floating at rest can be killed with a sword and never dives, flees or bites.
- `hurt`, 916-919, with `ChaseOffGoal.seen` (`CrocodileBreeding.java:626-634`): a mother hit at her nest takes her
  attacker as target and returns before the startle, but she only goes for a target within 9 blocks of the nest. Shot
  from 10 blocks she neither flees nor answers. (The hippo has the same gap at 16 blocks, R9-15.)

**Fix proposed.** A hit in the water startles it too (it sinks, the comment's own rule); for the mother it is a
design pick, noted only.

### R9-12 (C, proven): small things in the crocodile

- **Not saved:** `hatchSite` and `waitForMother` (save 1044-1069). Reload in the middle of a hatching and the young no
  longer wait in the nest: they run for the water alone; the mother still picks up those within 8 blocks of her.
- **A startled crocodile on land with no water within 12 blocks** searches all 5,600 cells round it every time its
  plunge is asked, for the 5 seconds the fright lasts, and does nothing (`PlungeGoal.canUse` 1547-1553, `waterAim`).
- **`openWater`** (2477-2507) checks up to 121 cells round every water cell within 12 blocks, in no order, once for
  each big grab: a one-off cost, maybe a few ms. To measure with R9-9.
- **A player who logs out while held** counts as eaten: the crocodile is fed for a day (`roll` 2310-2316).
- **The fly-snap goal sits at priority 0, above the hunt (1)**, though its comment (343) says "never over a hunt". It
  works only because its own `canUse` refuses while hunting (`CrocFlySnapGoal.java:46`): the priority and the comment
  disagree.
- **Droppings on every ended bask, also one cut short by a fright** (`BaskGoal.stop` 1342). They crumble by
  themselves (`CrocDroppingsBlock` ages on random ticks), so they cannot pile up.
- **The class comment (62-73) is from the first step** ("for now it harms nothing", "about 5 blocks long").

### What is solid in the crocodile

- **The goals' searches are paced:** the hunt looks once a second, keep-clear twice a second, basking company and
  the half-out edge every 10 seconds, the mother's work once a second; `canUse` asks its cheap questions first.
- **The hunt cannot be cut off with prey in the jaws** (`isInterruptable`, 1917): no half-eaten states; whatever ends
  it runs `stop`, which lets go.
- **What is held is never lost or doubled on a reload:** prey is a rider, saved with it, and let go on load
  (858-860); carried young are saved with the mother and let out if she dies (BUGS 264 holds).
- **Crèche and care times use the game time** and are saved; the old fixes (BUGS 218, 267, 273, 283) are all in place.
- **Counts:** crocodiles count in their spawning group whatever they are doing, so mothers kept near cannot fill the
  world; hatchings are capped by the room left. One fish feeds a crocodile 5 minutes, a big kill a day: it cannot
  fish a river out.
- **No leftovers between worlds:** the only static state is the list of sand blocks and clip names.
- **Two things steering:** the float rule in `tick` (870-875) and the hunt's dive both set the up-and-down speed, but
  the dive is set last before each move and wins; walked through, no fight.

---

## Part 4: the hippo (`HippoEntity`)

(Parts are in the order they were finished; part 3 is the otter.) The file was read whole by a helper reader, with
`worldgen/HippoPodFeature` and the kit classes it calls; I then walked each finding below through the code again
myself. Line numbers are `HippoEntity.java`'s. **No A finding.**

### R9-13 (B, proven): the warning yawn is remembered per hippo, not per visitor: the second one is bitten unwarned

**What is wrong.** A hippo warns with a yawn, and for 10 seconds after a yawn it charges whoever is close without
yawning again. That memory is one timer on the hippo; it does not remember who was warned.

**What the player would see.** With a friend: one player gets the yawn, the other walks up within 10 seconds and is
charged and bitten (5 hearts) with no warning. Alone: a bull that has just yawned at a crocodile charges the player
with no yawn.

**Where and proof.** `warnWait` (field 187; its comment says "at the same player"); `TerritoryGoal.start` 1520 sets
it; `canUse` 1473-1486: the target `p` is whichever crocodile or nearest player is found now, and line 1482
(`charge = ... && h.warnWait > 0`) asks only the timer. The test for the yawn (`RiverLifeTests`, about line 2877) has
one target.

**Fix proposed.** Keep who was warned (a UUID beside the timer); only that one is charged without a yawn.

### R9-14 (C, proven): dung on land almost never happens

The 1 in 600 roll for flicking dung while grazing sits inside `if (h.doing() == CALM)` (`GrazeGoal.tick` 1943-1949),
and the other branch sets it to grazing at once, so the dice are rolled on about one tick a bout: about one flick in
a hundred bouts. The comments (75, 1862) promise "now and then". **Fix:** roll at the end of each graze loop.

### R9-15 (B, proven): a hippo shot from more than 16 blocks never answers

**What is wrong.** A hit makes it angry and starts a charge, but the charge's own keep-going rule is the territory
rule: the target must be within 16 blocks. From 17 blocks the charge starts and ends at the next check, and ending
it wipes the anger and starts the 10-second charge rest.

**What the player would see.** The river's most dangerous animal stands and takes arrows from 17 blocks until it is
dead (80 health): it neither charges nor flees.

**Where and proof.** `hurt` 798-803 (`angryAt`, `angryUntil = tickCount + 400`, `chargeWait = 0`);
`TerritoryGoal.canUse` 1465-1469 starts the charge; `canContinueToUse` 1536
(`target.distanceTo(h) < giveUp(target)`) ends it; `stop` 1583-1588 (`chargeWait = CHARGE_REST; angryAt = null`).

**Fix proposed.** For a target it is angry at, give up by time and its follow range (32), and keep the anger until
`angryUntil`; or let it make for deep water when it cannot reach what hurts it.

### R9-16 (B/C, likely, to measure): two "nothing found" searches with no wait after them

- **No way home by day** (`HomeGoal.canUse` 1617-1622, `canContinueToUse` 1636): when the route to the pool fails or
  ends short (fenced in by a player, a pit), the goal ends at once and starts again on a 1 in 10 roll with no wait:
  a 40-column pool search and a full route search for a 2-block-wide body about once a second, all day, for each
  stuck hippo. The bank rest and the nibble already wait 600 ticks after "none" (1685, 1791); this one does not.
- **No grass in reach at night** (`lawns()` 1029-1040, from `GrazeGoal.canUse` 1884): with no lawn found the list
  stays empty, so the whole search (360 seeded column probes, the same ones with the same answer) runs again about
  once a second all night. Small cost.

**To measure.** A hippo penned on a bank by day; the profiler's `pathfind` and tick time. **Fix:** a wait after a
failed go, and remember "no lawns here" a few minutes.

### R9-17 (B, the read proven, the stall likely): the pool's water is read with no check that its land is loaded

`waterTop()` (1111) and `poolGone()` (2042) read the saved pool block directly; every other far read in the file is
guarded. At home it is always loaded. A hippo led away on a lead (Minecraft allows it) or drawn off charge after
charge, then standing in other water, loads its pool's chunk on the spot at each rest's end, nibble look and bank
look. The same hole as the crocodile's nest (R9-10). **Fix:** `level().isLoaded(pool)` first in both.

### R9-18 (B/C, proven in the code): when the pool is gone each hippo takes its own, and "gone" is judged two ways

- `PoolGoal.tick` 2020-2022: each hippo whose pool has no water left makes the spot it stands on its new pool, at
  its own time, and `setPool` (865-868) clears its lawns. The pod stops sharing one pool and one pair of lawns (the
  small form of BUGS 272's drift; it happens once, not for ever).
- `poolGone` (2042) wants no water at the pool block **and** none over it; `waterTop` (1111) gives up when the pool
  block alone has no water. One block landing on the bed at the bull's spot (a player's block, falling sand or
  gravel) leaves the pool "not gone" while `waterTop` answers "none" for good: the landing, the bank rest and the
  nibbling all stop without a word (1088, 1281, 1348, 1380).

**Fix proposed.** One judge of the pool's water (the block or the one over it), and a new pool every pod mate finds
the same way.

### R9-19 (C, proven; looks and comments, the user's call where it shows)

- **"The lawn it grazes tonight" is rolled again every 20 to 40 seconds** (`GrazeGoal.canUse` 1886): half the time
  the hippo marches off to its other lawn, 10 to 48 blocks away, and back, all night, wearing a third path between
  them. At dawn it goes home by the landing of the night's first lawn (1629), which may be the far one.
- **A lawn dropped as out of reach is not replaced** (`lawns()` 1030 only searches when both are gone; the comment
  at 1972 says another is found).
- **Night is `level.isDay()`** (1433-1435; the comment says "the shared day clock"). Minecraft's `isDay` has the
  weather in it: in a thunderstorm at noon it answers "not day" (the sky's darkness is 5, day needs under 4; worked
  out from `Level.updateSkyBrightness`), so the whole pod walks out to graze and goes home when the storm ends. The
  crocodile (`CrocodileEntity.java:742`) and the monitor read the same call. It also means these animals do not read
  a test's clock, hence the hippo's own `heldNight` and `heldCool` switches.
- **Timers in goals that are stepped every other tick run at half speed** (`HomeGoal`, `PoolGoal`): a rest is 20 s
  to 2 min 20 s, not the comment's "half a minute to a minute and a half" (1985); dung in the pool comes about every
  5 minutes a hippo, not "2 to 3" (137).
- **Paths only ever grow** (`wearPath` 1228-1265): a quarter of the blocks a foot comes onto off the lawn turn to
  path and never heal, charges too; a player's lawn by a pool is worn as well. All behind `mobGriefing`; bounded by
  the pod's range. A looks matter.

### R9-20 (C): small things in the hippo

- **The bed-step hop never happens:** `travel` sets an upward speed of 0.35 against a step (640) and `tick`, later in
  the same game tick, sets it back to falling while it walks (695). Its step height of 1.0 does the climbing. Two
  places steering; the hop can go.
- **Nothing calls `life.tick()`** (the crocodile and otter do): harmless while hippos are born grown, the first thing
  calves will need (a summoned `young:true` hippo never grows).
- **A name tag starting `film:`** makes any hippo play that clip for good (2092-2095), and the crocodile the same
  (`CrocodileEntity.java:2604-2608`): the film camera's hook is reachable by a player with a name tag.
- **Test switches live in the game class** (`led`, `heldNight`, `heldCool`, `dungNext`..., 1402-1430; `led` is saved).
- **No loot table for the hippo**: it drops nothing. A question, not a fault.

### What is solid in the hippo

- **A pod is only "hippos whose saved pool is the same block"**: no pod object, no link to the bull by id. Nothing
  breaks when the bull dies, unloads or reloads.
- **Nothing heavy runs every tick:** every search is behind a dice roll or a timer (crocodile search: a 5.5-block
  box about 3 times a second for a bull; the bank spot search at most every 30 s).
- **The pool does not move while it has water**; one bite, then a minute's peace for crocodiles: BUGS 271, 272 and
  273 all hold. A nest mother near a bull is not worn down: her keep-clear goal outranks her guard.
- **Counts:** never spawned by the spawner, never removed, never breeds, in a spawning group of its own: hippos can
  only go down in number. Dung is particles and one remembered spot, never an entity or block: it cannot pile up.
- **Blocks:** one plant about every 5 seconds a grazing hippo, no drops, `mobGriefing` asked at all three places.
- **Creative and spectator players** are left out at every door; boats are never attacked.
- **Saved state** loses and doubles nothing; no entity is held by id.

---

## Part 6: the bull shark (`BullSharkEntity`, `BullSharkGoals`, `BloodMist`) and the catfish's hunt (`HuntTilapiaGoal`)

All four files were read whole by a helper reader; I walked each finding below through the code again. Line numbers
are `BullSharkGoals.java`'s unless another file is named. **Three things the code is not, though the notes say so:**
the shark does not react to blood (`BloodMist` is only a puff of particles, nothing reads it); `HuntTilapiaGoal` is
the catfish's hunt alone (the shark and the crocodile use only its `scatter`); and the shark hunts fish only, its one
other bite is a swimming player. So R9-5 (the player's animals) does not touch the shark.

### R9-21 (B, proven; A if it has been seen in play): a shark's hunt hangs still for up to 45 seconds

**What is wrong.** The shark picks only prey over deep water with a clear deep line to it, but once the hunt runs
nothing asks that again. A fish that swims onto the shallow shelf is not "lost"; the shark's swimming stops at the
shelf by its own rule (it never enters shallow water); and the hunt only ends when its 900-tick clock runs out.

**What the player would see.** A shark lying dead still at the edge of the deep water, fin out, pointing at a fish in
the shallows, for up to 45 seconds; then 10 seconds of patrol and maybe the same with the next fish. The shark's own
rule (its class comment) is that it never stops swimming.

**Where and proof.** `lost()` 274-277 asks only: dead, gone, out of the water, over 28 blocks, hidden in cover.
`BullSharkEntity.java:835-837`: `if (shark.shallowAhead()) speed = 0`. `prowl` 280-301 and `close` 304-320 otherwise
only count `left` down from 900 (243). Fish wander freely during a hunt (a hunting shark does not part them).

**Fix proposed.** "Cannot reach" becomes part of `lost()`: the prey's column too shallow, or the swimming blocked for
about 3 seconds, ends the hunt with the short wait. One rule in one place.

### R9-22 (B, proven): only the three nearest fish are tried, so a school on the shelf hides every fish behind it

`HuntGoal.canUse` 226-236 sorts all prey within 20 blocks by distance and checks only the nearest three for depth and
a deep line. A school sits together: if the three nearest are in the shallows the shark hunts nothing, though fish it
could take swim farther off; the same three are tried again every second. **The player would see** a hungry shark
patrolling past fish all night. **Fix:** put the cheap depth test into the search's filter, then try the nearest
three of those.

### R9-23 (B, the search proven, the stuck shark likely; to measure): a shark with nowhere to go asks for a new target every tick and lies still

**What is wrong.** `PatrolGoal.tick` 169-176: with no target it calls `deepPoint` twice (a hippo search 40 blocks
wide and 16 tries, each a water-column scan and a deep-line scan), and if both fail the same again next tick, with no
wait; with no target it is not told to swim at all. `KeepClearGoal.tick` 846 has the same shape. It happens to a
shark in a deep hole too small for a run (the spawn rule looks at one column only, `BullSharkEntity.java:693`; an egg
in a pond does the same) or hemmed in by hippos (`deepPoint` refuses every point within 20 blocks of one, 98-104, and
the two share the near-inland river).

**What the player would see.** A shark lying still in a hole for good. The cost is small by count (a few hundred
block reads and two searches a tick for each stuck shark, two sharks at most); not measured.

**To measure.** `BullSharkTests` with a shark in a 5 by 5 deep hole: tick time and whether it moves.
**Fix proposed.** A wait (say 40 ticks) after a failed pick; a spawn rule that asks for two or three deep columns in
a row.

### R9-24 (B, proven; the user's call): a shark hit from the bank or from a boat does nothing

`BullSharkEntity.hurt` 671-679 only remembers who hit it; the one reader (`MeetGoal.canUse` 531) needs that player to
be swimming in deep water. A player with a bow on the bank or in a boat kills a 50-health shark while it patrols
past: no flight, no dive, no change of pace. With R9-11 (the crocodile afloat) and R9-15 (the hippo past 16 blocks)
this is one pattern: **all three big hunters can be killed from a distance with no answer.** Whether that matters is
the user's call (fun first). **Fix if wanted:** hit by something it cannot reach, it swims away deep and fast a while
(the call its swim-off already uses).

### R9-25 (B, the call proven, the cost likely; the catfish's file, for round 10's fix session): the catfish plans a new route every tick once its route has run out short of its prey

`HuntTilapiaGoal.java:126-129`: `if (ticks % 10 == 1 || fish.getNavigation().isDone())` then `moveTo`. While the
route is finished and the prey is still more than 1.3 blocks from its mouth, a new route is planned every tick for up
to the chase's 600 ticks. Cheap when the prey is right beside it; a full failed search every tick when the prey
cannot be reached. The link not checked: how often a catfish sees prey it cannot reach (its pick needs a line of
sight). This may be part of the old catfish hunting flakes (BUGS 217, 236, 279). **Fix:** plan on the 10-tick beat
only.

### R9-26 (C): small things in the shark

- **A miss with the prey gone skips the wait** (`miss` 474: `lost() ? DONE : CLOSE`) and the next start resets the
  three-miss count (244), so "three misses and it gives up half a minute" can be walked round. Rare.
- **The catfish in its jaws is lost with nothing left** when the 54-tick thrash is cut short by a reload, by leaving
  the water or by the shark's death: the fish was removed at the bite (413-417) and the remains and the meal come
  only at the thrash's end (450-465). One catfish, a 2.7 second window.
- **The spawn rule's 80-block shark search runs before the cheap zone test** (`BullSharkEntity.java:696-697`,
  `SavannaEvents.java:120-123`); the group's cap is almost never reached, so the spawner keeps asking. Small.
- **A name tag starting `film:`** takes over the animation (`BullSharkEntity.java:878-881`), as for the crocodile and
  hippo (R9-20).
- **Comments against code:** the spy hop "every 5 s" is every 10 (48; goals are asked every other tick); the class
  comment says it keeps to the coast and near-inland rivers, but only its spawning is zoned, its patrol has no home;
  `BloodMist` says "undead and golems give none" and leaves out every hostile mob; the entity list says 5 to 7
  blocks, the code's longest is 7.5.
- **Test hooks and a diary live in the game class** (`strikeLuck`, `breachNext`, `spyNext`, `mind`).
- **Prey is removed, not killed** (415, 423; `HuntTilapiaGoal.java:215`; the crocodile's swallow too): no death
  event, no drops, and whatever a fish's own death would do (a mouth-brooding tilapia letting out her fry, a
  courting pair's mark: R9-1) does not run. For round 10 to weigh.
- **No loot table:** the shark drops nothing but experience. A question, not a fault.

### What is solid in the shark

- **No reads into land that is not loaded:** the farthest read is about 28 blocks from the shark. Keep any new reach
  under 32.
- **Mind and legs are cleanly apart:** goals only say "swim there"; one rule for shallow water, asked in one place.
- **`free()` is the one gate** every goal passes before it starts, so no goal breaks in on another's bite, bump,
  breach or spy hop; no pair of goals flips tick by tick.
- **Counts:** its own spawning group with a cap of 2, 80 blocks apart, removed past 96 to 128 blocks; one meal per 16
  to 24 minutes, at night only: it cannot pile up and cannot fish a river out.
- **Players:** creative and spectator players are never met or bitten; more than one player is handled; a player who
  leaves the deep water is let go in every phase. In Peaceful the bite does no harm (the bump still shoves: R9-6).
- **Nothing is synced or logged every tick; no action or phase is saved, so a reload leaves no stuck state.**
- **`BloodMist`** has no state at all; **the catfish's give-up** rests 10 to 60 seconds and cannot pick again at once.

---

## Part 3: the otter (`OtterEntity`, `HeldCatch`)

Read: `OtterEntity.java` (2,939 lines) and `HeldCatch.java` (148), every line, and as far as a finding needed
`client/OtterRenderer`, `client/OtterModel`, the otter's spawning group in `META-INF/enumextensions.json`, its row in
`docs/LIVING-THINGS.md`, the body plan files. The otter never attacks anything but its prey, so Peaceful mode and
ranged attacks (R9-6, R9-24) do not come up here. Four A findings: all four are "something ended in a way nobody
planned and a state was left behind".

### R9-27 (A, proven): a catch in the jaws is saved with the otter, but the otter forgets it: after a reload it is stuck at the snout for good

**What is wrong.** A catch is a real rider on the otter, made helpless while it is held: its own will is turned off and
it cannot be hurt. Minecraft saves a rider inside the animal it rides, with those two switches as they are, and seats
it again on load. The otter's own note of what it holds (`held`) is not saved, and nothing looks for a rider after a
load. So the otter no longer knows it holds anything: it never eats it and can never let it go.

**What the player would see.** Quit the world (or walk away so the land unloads) while an otter carries or eats a
catch. Back again: the otter has a frozen fish, crab or frog fixed at its snout for good. When it is hungry it hunts
again and takes a second catch on top of the first. When that otter later dies or is removed, the old catch drops off
still frozen and impossible to kill: a statue of a fish left in the river (and a target the crocodile, catfish and
shark can pick but never hurt).

**Where and proof.** `OtterEntity.java:957-969` saves only the life and `HungryIn`. `grab` 1009-1022:
`prey.startRiding(this, true)`, `mob.setNoAi(true)`, `prey.setInvulnerable(true)`. The only check in the tick, 748,
needs `held != null`; no other server line reads the rider. `remove` 1092-1098 lets go, but Minecraft does not call it
when land unloads: `PersistentEntitySectionManager.processChunkUnload` (line 225) writes the animals first and then
calls `setRemoved` directly (234-235), and `Entity.saveWithoutId` writes the riders (`"Passengers"`, 1811) and
`EntityType.loadEntityRecursive` seats them again with force (1138-1144). All checked in the patched sources. A catch
is held for 10 to 20 seconds of every 5 to 7.5 minutes (longer under R9-28), so a few saves in a hundred near an
otter; each one leaves damage that stays in the world.

**Fix proposed.** On its first tick after a load, an otter with a rider that is prey and no `held` takes it as `held`
again (or lets it go properly: off, will on, can be hurt). A game test: grab, save, load, tick. The bullfrog already
handles "a world saved mid-clasp" (`BullfrogEntity` 760): the same idea.

### R9-28 (A, proven in the code; how often is for play to show): carrying a catch has no time limit: with no bank in reach the otter floats on the spot with it for ever

**What is wrong.** While the otter holds a catch its hunt can never end by itself: the give-up clock is skipped. In
the water it looks for a bank to haul out on within 12 blocks (16 lines out over the water), then for any dry spot
within 10. Over open water both find nothing; no route is set; the hunt holds the otter's legs so nothing else may
move it; and the same two searches run again every time the goal is asked (10 times a second). The same loop runs
when a bank is found but cannot be reached.

**What the player would see.** An otter hanging in mid-river with a fish in its mouth, never eating, until something
hurts it or a crocodile or hippo comes within 9 blocks. The fish stays frozen and cannot be killed all that time.

**Where and proof.** `HuntGoal.canContinueToUse` 1491-1493: `if (otter.held != null) return true;` comes before the
give-up count on 1494. The carry, 1633-1641: `bank = nearestBank(otter, 12.0); if (bank == null) bank = landSpot(otter,
10.0, null); if (bank != null) moveTo(...)`. `landSpot` 1204-1219 cannot answer over water (its ground must have no
water under it, `roomyGround` 1268). **Does it fit the game:** `docs/DESIGN.md` gives the inland rivers about 53 to 66
blocks of water across, so mid-river is up to 33 blocks from either bank; a hunt starts within 12 blocks of the prey
and the chase carries the otter on. Otters spawn within 6 blocks of a bank, so most catches are near one: this is
the wide-river case, not the everyday one.

**Fix proposed.** One carry limit (about 30 seconds, then it eats afloat on its back, which real otters do, or lets
the catch go); the bank search at most once a second, reaching wider after a miss; and with no bank found, swim toward
the nearest column that is not water.

### R9-29 (A, proven): only the otter that started a game of play can end it: if that one is killed or vanishes mid-game the other stands in the game for good

**What is wrong.** A game of play (a bout) is one shared object held by both otters, and it has good limits: every
stage has a clock, and "is it still on" checks both otters. But only the starter's goal runs those checks. The invited
otter's goal just asks "do I still have a bout". If the starter stops thinking without its goal being closed properly,
nobody ever ends the bout: killed in one blow (a dying animal runs no goals), removed by Minecraft for being far from
the player (R9-30 makes that common), or its land unloaded while the mate's is not.

**What the player would see.** The other otter frozen where it was: reared up batting at nothing, or standing or
floating still with its play face on. Its play goal outranks hunting, resting, swimming and strolling, so it never
does any of them again. Only a hit, or a crocodile or hippo within 9 blocks, frees it.

**Where and proof.** `PlayGoal.canContinueToUse` 2494-2496: `return otter.bout != null;`. `PlayGoal.tick` 2499-2504:
`if (b != null && b.lead == otter) b.tick();`. `Bout.on()` 1877-1886 is called only from `Bout.tick` 1889. `remove`
1092-1098 does not end a bout; `hurt` 907 scares the family only `if (isAlive())`, so a killing blow scares nobody.
The other way round is handled: the invited otter dead or gone is seen by `on()` (1880). A reload is fine too (the
bout is not saved, both come back calm).

**Fix proposed.** Either otter's play goal ends a bout that is no longer `on()`, and `remove()` and `die()` end the
bout the otter is in. One "end it, whatever happened" door.

### R9-30 (A, proven): the otter is the only river animal Minecraft may remove 32 blocks from the player: they vanish one at a time

**What is wrong.** Every other river animal has the rule "kept while a player is within 96 blocks" (`removeWhenFarAway`
in the crocodile, hippo, monitor, shark, crab, snail, bullfrog, the fish, the fly cloud). The otter has none, so it
gets Minecraft's own rule for mobs: once no player has been within 32 blocks for 30 seconds it is removed at random,
about 40 seconds later on average. `docs/LIVING-THINGS.md` (the groups table) says the otters are kept "within 96,
gone past 128"; the code has only the 128.

**What the player would see.** Watching otters from across a wide river, or from a hide 35 blocks off, they pop out of
existence: a mother before her pup, or one playmate before the other (which sets off R9-29). New ones spawn (their
group has its own cap of 2), so the river is not emptied, but a pup needs three days to grow up and will almost never
live that long.

**Where and proof.** No `removeWhenFarAway`, `requiresCustomPersistence` or `checkDespawn` anywhere in
`OtterEntity.java`; it extends `PathfinderMob`, whose default answers yes. `Mob.checkDespawn` 744-747 (patched
sources): `noActionTime > 600 && random.nextInt(800) == 0 && d0 > 32 * 32 && removeWhenFarAway(d0)`. The otter's
`getNoActionTime` returning 0 (951-954) does not stop it: that line reads the field, not the method (the project's
own comments in `TilapiaEntity` 593 say so). The group: `enumextensions.json` 143-151 (cap 2, 128).

**Fix proposed.** The same three lines as the others: gone only past 96 blocks. And the check the spawning check list
asks for (a test that `removeWhenFarAway` at 64 blocks says no). Balance: the cap of 2 means keeping them cannot pile
them up.

### R9-31 (B, the read proven; to measure): while an otter on screen holds a crab or a frog, the game reads and parses a file from the mod's jar twice every frame

**What is wrong.** How a held fish lies in the jaws comes from measures in its body plan file, looked up once and
remembered. A crab and a bullfrog have no such measures, and "none" is not remembered (the store used keeps nothing
for an empty answer). So for those two the whole file (4 to 6 KB) is opened from the jar and parsed again on every
call, and the drawing code calls it twice a frame for every otter on screen with such a catch, under one shared lock.
Crabs are the otter's favourite prey.

**What the player would see.** Maybe a dip in frames whenever an otter in view carries or eats a crab or a frog; for
good under R9-27 or R9-28. Not measured. **The scene to measure it:** one otter with a crab in its jaws in view,
the client's frame time with and without (the F3 frame graph, or a film scene with a crab catch).

**Where and proof.** `HeldCatch.java:79-89`: `FISH.computeIfAbsent(name, n -> { ...; return h == null ? null : ...})`,
and a `HashMap` stores nothing when the function gives null. `checks` 52-63 opens and parses the file. Callers:
`OtterRenderer.java:77` and `OtterModel.java:74`, each frame. Only `barb.json` and `tilapia.json` have "held"
(checked by search).

**Fix proposed.** Remember "none" too (store an empty answer). One line.

### R9-32 (B, proven that nothing stops it; how often not known): a hunt that gives up takes the same prey again half a second later

**What is wrong.** When a hunt gives up (its clock runs out) the goal just stops: no wait, no memory of the prey it
could not get. The next look round (about every 10 ticks) takes the nearest prey again, the same one. A hungry grown
otter does not rest or play, so one prey it cannot reach (a crab in a gap, a fish under a ledge) holds it in one
endless hunt, with a new route every 16 ticks.

**Where and proof.** `HuntGoal.canUse` 1459-1477 (nearest prey, nothing remembered), 1494 (`--left > 0`), `stop`
1653-1664 (sets no wait). A fish hidden in cover is the one exception (left out of the search, 1469). The catfish's
hunt already does this right (rests 10 to 60 seconds after a give-up, R9-26's solid list).

**Fix proposed.** After a give-up, a wait of some seconds and "not that one" for a minute. Use the same small rule for
the shark's R9-22 and R9-23: one shared "gave up on this, wait" helper in the kit instead of each hunter's own.

### R9-33 (B, likely, to measure): a cornered otter plans its escape again every time it is asked

**What is wrong.** Fleeing picks a spot and a route, to be picked again after 60 counts "or when the route is done".
When no spot is found, or the route fails at once, the route is "done" straight away, so the whole search (up to 24
water spots and 24 land spots, each some block reads, and a route plan) runs 10 times a second for the 8 to 10
seconds of the fright, for every frightened otter of the family at once.

**Where and proof.** `FleeGoal.tick` 1364-1366: `if (--left <= 0 || otter.getNavigation().isDone()) route();`,
`route` 1330-1342. Small numbers (cap 2), so a cost and not a spike. **The scene to measure it:** an otter in a
walled one-block pool with a crocodile beside it, the server's tick time.

**Fix proposed.** After a `route()` that set no route, wait 10 ticks. (The same "no wait after nothing found" shape
as R9-16 and R9-23.)

### R9-34 (B, proven): harm with no attacker does not make an otter run, and it wipes the fright of the otters near it

**What is wrong.** "Scared" needs a thing to be scared of. Fire, lava, a cactus, a berry bush, falling, being stuck in
a wall have no attacker, so a burning otter is not scared and does not flee: it drops its catch and stands there.
Worse, every hit also tells the otters within 12 blocks "be scared of that attacker", and with no attacker that
replaces what they were scared of with nothing: an otter that was fleeing a crocodile stops fleeing. And a killing
blow scares nobody at all, because the family is only told while the hit otter is still alive.

**Where and proof.** `hurt` 902-915: `scare(source.getEntity(), 200)` and for the family `mate.scare(source.getEntity(),
160)`, both inside `if (isAlive())`. `scared()` 556-558: `scare != null && tickCount < scaredUntil`. `scare()`
560-562 overwrites.

**Fix proposed.** "Scared" runs on its clock alone, the scarer may be empty (`FleeGoal.route` 1331-1333 already copes
with none: it just goes somewhere), a fright with no scarer never replaces one that has one, and the family is told
before the alive check.

### R9-35 (B, a design note): what the otter is doing lives in five places, and every A finding above is a state left behind when one of them ended unplanned

**What is wrong.** `OtterEntity` is one file with the animal, nine goals and the 580-line play bout. "What am I doing"
is kept in five places that meet only through "am I calm" checks: the `doing` number (20 values, started in goals and
in the bout, ended by one long chain in the tick, 759-764), the bout's 14 stages, the skitter's 6 phases, the dance
counter, and the block move it may be riding. Clocks run at three speeds (every tick, every goal tick, every goal
check), and besides routes the code moves the body by hand in five places (787, 1524, 2165, 2207, 2249), one of them
moving the *other* otter. No two of them were found pulling against each other today, and the tests cover the happy
paths. But R9-27, R9-28, R9-29 and the stale rest spot in R9-36 all have the same cause, and it is the part most
likely to break when the otter gets more (breeding, the pet, biting back).

**Fix proposed (for the fix session to weigh, not a patch).** Before adding to the otter: the bout and the held catch
each become their own small class with one "end, whatever happened" door, called from the goal's stop, from `remove`
and from `die`. That one change fixes R9-27 and R9-29 at the root.

### R9-36 (C): small things in the otter

- **A rest-together spot never used is kept (proven).** A bout that ends in a rest gives the invited otter a spot
  beside the other (2376-2383). The rest is refused while a player stands within 5 blocks (1713, 1727-1730), which is
  just when the player is watching. The 100-tick offer runs out (797-799) but the spot is only cleared when a rest
  starts (1743-1750): at its next ordinary rest, maybe minutes later, the otter first walks 3 seconds in a straight
  line toward where its playmate once lay. Fix: clear the spot when the offer runs out.
- **Otters and hippos share pools, and a hippo within 9 blocks is a danger (212, 1282; likely, for the balance soak).**
  An otter beside a pod is frightened again within half a second of each 8-second fright ending, with a new startled
  freeze each time on land, and cannot hunt, play or rest meanwhile. Does an otter in a hippo pool ever do anything
  else? Watch one in the soak; if not, drop the hippo from the dangers unless it yawns or charges.
- **Clocks counted in goal checks last twice what their comments say:** the hunt's give-up "20 s" is 40 s (223-224,
  1494), the startle 10 is 20 ticks (146, 1356), a rest "20 to 60 s" is 40 to 120 s (1695, 1790), the swim about 300
  is 600 (2736), the flee's 60 is 120. The skitter met this and was fixed (2546); these were not. Not harmful, but
  every number is twice what a reader and the balance think. (The hippo has the same, R9-19.)
- **A pup looks for a grown otter within 20 blocks every time it is asked, with no gate** (`FollowFamilyGoal.canUse`
  1389-1403); the hunt has a 1-in-5 gate, this has none. A cheap search, a pup or two: add the same gate.
- **Every otter searches for otters within 3 blocks every tick** (`keepApart` 591-645). Small box, cap 2: a note.
- **Film-camera hooks live in the game code:** the tags `caught.<n>`, `doing.eat`, `doing.gulp` (711-723), `dance.now`
  (776), `play.tag`, `play.rest`, `play.now` (2059, 2359, 2467), the names `mood:<mood>` and `film:<clip>` (506-510,
  2822-2825), and 14 strings built every 5 ticks per otter to match them (505-511). A player's name tag "film:swim"
  works on any otter (the same on the crocodile, hippo and shark: R9-20, R9-26). A tag `caught.x` on an otter with a
  rider crashes the server (`Float.parseFloat`, 714; only an operator can add tags).
- **`getNoActionTime` returning 0 (951-954) does nothing here:** the stroll is built not to read it (336) and
  despawning reads the field (R9-30).
- **A missing number in `otter.json`'s carry measures** is a crash at the first fish bitten, not at start-up
  (`HeldCatch.carry` 66-75). The file is complete today. Fix: read it at start-up.
- **Dead code and old comments:** `soakedFor()` (2936-2938) is used nowhere; the attack damage attribute (318) is
  unused; the class comment still says "Not yet: hunting ... play-wrestling" (75) and "families of two to four" (66);
  a comment block at 1802-1806 describes a goal 900 lines further down.
- **Questions for the user, small:** a name-tagged fish or frog is eaten like any other (989-991; foxes do the same);
  an otter in a boat can still be picked as a playmate and is then moved by the bout (418, 428; not walked in full);
  a bout begun in the shallows ends at the first step onto dry land, because the wet otter shakes (808, 1881).

### What is solid in the otter

- **Breath:** it cannot drown afloat and cannot stay under for ever: up after its longest dive, then 80 ticks in
  which diving is refused (389-391, 817-819), whatever goal asked for the dive.
- **The hunt's look-round gate** (1460-1464): cheap checks first, then 1 in 5, then the search. Its comment on why
  not "every 10th tick" is right.
- **Bite and pounce timing** land on the right tick although goals step every other tick (1519, 1528, 1535).
- **The play bout's own safety:** every stage has a clock, rounds only count down, the invited otter dead, hurt,
  scared or busy ends it for both, a fright ends it through the goal's stop, the swimming chase's 30-tick start
  (BUGS 297), the keep-apart push switched off while they touch on purpose (BUGS 299).
- **The catch while the otter stays loaded:** hurt, dead, hunt broken off, eaten, knocked off, two otters after one
  prey: never lost, never doubled, no drops.
- **A block move is never fought by a goal** (every goal asks `rider.play()` first).
- **No reads into land that is not loaded:** every block read is within 14 blocks. Nothing is spawned, no list
  grows, nothing is logged, no synced value changes every tick, no shared state tied to one world.
- **The food chain:** one catch per otter per 5 to 7.5 minutes, at most 2 otters round a player: it cannot fish a
  river out, and an otter never starves (hunger only decides what it does).
- **Test gaps (for the fix session):** no test saves and loads with a catch held, makes a catch far from a bank,
  removes a bout's starter mid-bout, checks the keep-near rule, or hurts an otter with fire.

---
