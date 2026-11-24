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
