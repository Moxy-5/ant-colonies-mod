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
