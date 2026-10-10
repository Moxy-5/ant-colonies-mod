# Ant & Termite Colonies Mod (working title)

A Minecraft mod where ants and termites live as real colonies: they dig, build, gather food, grow, start new colonies, and fight each other. Inspired by Empires of the Undergrowth, but the player is down at ant level.

## How to work with me
- I have zero coding experience. You write all the code; I test in-game and report bugs.
- Work in small steps, one feature at a time. Don't add features that aren't in this plan without asking.
- After every change, run `./gradlew build` and fix compile errors yourself before asking me to test.
- Tell me exactly how to test each change: what to summon, where to go, what to look for.
- Explain what you changed in a few plain-English sentences. Explain any technical term you use.
- If my bug report is vague, ask me: what I did, what I expected, what actually happened.
- When something works, commit it to git with a clear message.
- Keep "Current status" at the bottom of this file up to date. If a design decision changes, ask me, then update this file.

## Tech stack (never deviate)
- Minecraft **1.21.1**, **NeoForge** (21.1.x), **Java 21**, official NeoForge MDK (ModDevGradle).
- **GeckoLib** (NeoForge 1.21.1 build) for all entity models and animations, made in Blockbench.
- Mod ID: **not chosen yet. Ask me before creating the project.**
- Avoid old Forge 1.20.1 habits:
  - Packages are `net.neoforged.*`, not `net.minecraftforge.*`.
  - Use `ResourceLocation.fromNamespaceAndPath(...)`, never `new ResourceLocation(...)`.
  - Item data uses data components, not NBT.
  - Data folders are singular in 1.21: `tags/block`, `tags/item`, `loot_table`, `recipe`.
  - Natural spawns and worldgen use NeoForge biome modifiers (JSON).
  - Only use blocks/items that exist in 1.21.1. Leaf litter, dry grass, and fallen logs (added in 1.21.5) do NOT exist.

## Architecture: one colony framework, many species
- Shared systems are written once and are species-neutral: colony brain, virtual population and spawning, trails and pathfinding, nest site scoring, digging, nest growth stages, founding, foraging, defense and going home, and (later) alarm and hazard reactions.
- Each species is a **species profile**: a JSON data file in the mod's data folder that the shared systems read. I can tweak numbers there without touching code.
- A profile holds: castes (size, health, damage, speed), biomes, diggable-block tag, food tag, nest-site scoring weights, founding type (`single_queen`, `royal_pair`, `colony_fission`), nest stages and special chambers, max depth, population and real-mob caps, chase distance, and alarm/hazard responses (for example, fire: `evacuate` vs `seal_and_wait`).
- Behavior that can't be data (fungus gardens, leaf cutting, army ant bivouacs) plugs into the shared framework through hooks. Never copy a whole system to make a species version of it.
- Adding a species should mean: a new profile, new models, and only the special code that species truly needs.
- When building any system, check: will this work for a species we haven't added yet?

## Ant minds (the user plans truly intelligent ants and termites later)
- Keep three layers apart, so a smarter mind can replace a simpler one without touching the rest:
  1. **Mind (what to do and why):** goals today (`DigGoal`, `CarryPelletGoal`, `AntWanderGoal`), the colony brain's jobs, memory, learning and communication later.
  2. **Legs (how to get there):** `AntNavigator` (walks a route cell by cell) and `AntPathFinder` (plans routes that walk, climb walls, go over ledges and drop down holes). A mind only says "go to this cell". Species-neutral, so termites reuse it.
  3. **Body:** move control, animations, renderer.
- Never put "decide what to do" logic into the navigator or the move control, and never let a goal steer an ant with vanilla navigation or `MoveControl` directly.
- When adding behaviour, ask: could a smarter mind reuse this as a building block (perceive, remember, choose, act)?

## Species (version 1)

### Red harvester ant (Pogonomyrmex)
- **Castes:** worker, queen (winged before founding, wingless after), winged males (later). Real harvester ants have no soldier caste; workers defend with stings. Optional later: a larger "defender" worker.
- **Biomes:** plains, sunflower plains, badlands (`#minecraft:is_badlands`). Desert edges allowed but score low (little food).
- **Food:** dropped seed items (wheat, beetroot, melon, pumpkin, torchflower seeds, pitcher pods), seeds from clipping `short_grass`, grama grass seed heads.
- **Farm raids:** taking seeds from fully grown wheat. Config option, default **off**.
- **Cleared circle:** workers remove `short_grass`, `tall_grass`, `fern`, `dead_bush` within a radius of the nest.
- **Special chamber:** seed granary.
- **Founding:** single queen after a nuptial flight.

### Macrotermes (African mound-building termite)
- **Castes:** worker, minor soldier, major soldier, king, queen (huge swollen abdomen, stationary), winged alates.
- **Biomes:** savanna, savanna plateau, windswept savanna (`#minecraft:is_savanna`).
- **Food (dead plant material only):** decaying leaves (`persistent=false` and `distance=7`), `dead_bush`, `short_grass`, `tall_grass`, dropped sticks/saplings/wheat, `hay_block` (big food jackpot).
- **Special chambers:** fungus garden (custom fungus comb block turns gathered food into colony food), royal chamber with king and queen.
- **Mound:** custom reddish packed-earth block. Mound grows with the colony.
- **Founding:** a king + queen pair after a nuptial flight; both drop their wings.
- **Termites eat player-built wood:** config option, default **off**.

### Later species (NOT version 1)
- Matabele ant: savanna termite raiders, march in columns, carry injured nestmates home. Wingless queens; new colonies form by the colony splitting in two.
- Leafcutter ants (cut leaf pieces, farm fungus) and army ants (living bivouacs, raiding swarms).

## Core systems

### Colony brain
- Each colony has a server-side brain storing: id, species, queen (and king) UUIDs, member count, stored food, growth stage, nest plan (chamber positions), territory center and radius.
- Must survive save/reload (use SavedData). Queens and colony members never despawn.
- Ants get jobs from the brain: dig, forage, carry, nurse, defend. The brain updates on a timer (every 1–5 seconds), not every tick.
- Config caps: max population per colony, max nest depth (~20 blocks), max chambers, minimum distance between colonies.

### Performance (build this in from the start)
Goal: a mature colony can hold 1,000+ ants or termites without lag.
- **Most of the colony is virtual.** The brain stores counts per caste. Only a small number exist as real mobs at any moment.
- **Hard cap on real mobs per colony** (config, around 40–60), no matter how big the colony is.
- **Spawn where the player can see.** Real mobs appear in the chamber the player is in or near, and on the surface where visible work happens (digging, foraging, trails, fights).
- **Spawn gradually.** When the player approaches, spawn a few per second, never all at once. Ants that are far away or out of sight go back into the virtual count.
- **Defense waves.** When a nest is attacked, defenders keep pouring out of the virtual pool in waves, up to the cap.
- **Defenders give up and go home.** Defenders only chase within the colony's territory. If the player runs past the edge, they stop, walk back to the nest, and return to the virtual count. Termite soldiers stay close to the mound; ants chase farther.
- **Trails.** The brain calculates a route once; ants follow those waypoints instead of pathfinding on their own.
- **Thinking in turns.** Each mob makes decisions on a timer, staggered so they don't all think in the same tick.
- **No pushing.** Colony members don't collide with each other.
- **Distance detail.** Ants beyond a set distance skip animations.
- **Later:** visual-only crowds drawn on the client (for marching columns and packed chambers) that cost almost nothing on the server.

### Picking a nest site
- The queen scores spots within a radius and picks the best. Scoring weights come from the species profile:
  - Soil: dirt, coarse dirt, sand, clay score high. Stone, water, flooded ground score zero.
  - Nearby food: bonus.
  - Close to another colony or termite mound: big penalty.
  - Open surface: bonus.

### Founding (nuptial flight)
- Mature colonies release winged queens (and males) when rain stops, with a random chance.
- The queen flies around, scores sites, lands, drops her wings (as an item), digs a 3–5 block shaft, seals the entrance, and waits. Her first (smaller) workers emerge after a few in-game days.
- Founding queens are fragile and most should fail (random chance, predators, players). This is the main natural population control.
- Termites: king and queen found the colony together.

### Nest growth stages
1. Founding: entrance shaft + queen chamber.
2. Early: nursery with visible egg / larva / pupa clusters.
3. Growing: food storage (granary or fungus garden) + connecting tunnels.
4. Mature: more nurseries and galleries, a refuse chamber; produces winged queens.
- A stage unlocks only with enough workers AND enough stored food.
- Starving colonies stop growing and can shrink. The colony dies when the queen dies.

### Colony life cycle (planned for M6 and M9, not started)
- Real cycle as a game loop: mature colony releases winged queens and males after rain; they mate in a nuptial flight; the queen lands, drops her wings, digs a small chamber, seals it, lays eggs and raises the first (smaller) workers alone; most founding queens fail; the colony grows over years; it ends when the queen dies or it starves.
- Prepare these before M9 (design M6 with them in mind):
  1. **Queen life stages:** winged virgin, flying, landed (wings off), sealed founding queen, settled queen of an established colony. The colony is created when she founds her nest, not when she spawns.
  2. **Nest plan as data:** shaft, tunnel and room sizes move out of `Colony` constants into a per-stage list of cells (founding plan is much smaller), later read from the species profile.
  3. **Colony clock and brood pipeline:** each colony stores its age and stage start; the brain keeps counts of eggs, larvae and pupae with timers, gated by food and queen health (numbers only, so it is cheap).
  4. **Colonies live offline:** the brain keeps ageing, growing brood and eating in unloaded chunks; physical digging only happens near the player and catches up when chunks load.
  5. **Flying legs:** a separate flying mover for winged queens and males, driven by the same mind layer as walking.
  6. **Site choice:** scoring weights from the species profile (soil, distance to other colonies, food).
- **Decisions (user):** how long a colony takes to mature is decided later, once more of the mod exists (make it a config number, "life speed"). No built-in seasons: use rain stopping, the day count and a random chance, behind one small "what season is it" switch so a seasons-mod compatibility can be added later. Males are mostly numbers, with a handful shown as real flying ants during a flight. Founding queens can be threatened by predators, rival colonies and the player. Ants fight any ant of another colony and fight termites (one rule: different colony id = enemy). An anteater mob is planned later; when it kills real ants the colony headcount drops too. Hunger and eating: for our own mobs only, not vanilla animals.

### Digging, phase 1 (current plan)
- Bite-by-bite digging using the vanilla crack overlay (`destroyBlockProgress`), dirt particles, and a bite sound.
- Several ants on one block share progress. About one block every few seconds per worker, slower for harder blocks.
- Each bite makes a soil pellet the ant carries out in its mandibles. Pellets dropped at the entrance build the mound.
- Diggable blocks come from a block tag: `dirt`, `grass_block`, `coarse_dirt`, `rooted_dirt`, `clay`, `mud`, `sand`, `red_sand`.
- Never dig stone, wood, or containers. (Player-placed dirt can't easily be told apart from natural dirt; that's fine for version 1.)
- Never dig a block if the block above it can fall (sand, red sand, gravel), except for the entrance shaft.
- Keep digging logic separate from digging visuals so phase 2 only changes the visuals.

### Digging, phase 2 (later)
- Replace the cracks with real "bitten" block stages, like cake slices.

### Custom plants
- **Grama grass (version 1):** grows in stages to seed heads in plains and badlands. Ants harvest the seeds; the plant drops back a stage and regrows.
- **Termitomyces mushroom (version 1):** sprouts on termite mounds after rain, with a rare giant variant. Players can harvest and eat it.
- Later: desert wildflower, dry savanna grass (green → dry; termites only take dry), fallen acacia branches termites eat, refuse-pile gardens (ant trash piles sprout plants), optional dung.
- Plants regrow but do not spread (except near refuse piles). Growth speed is a config option. Bone meal works on them.

### Ants vs termites
- Ant colonies and termite colonies are hostile. Territories overlap where savanna meets plains and desert.
- Version 1: defenders attack intruders near their territory. Later: organized raids.

### Alarm level and hazards (LATER, after M11)
- Each colony has an alarm level. Attacks, floods, fire, and lava raise it; it calms down over time.
- Levels: normal work → seal tunnels → evacuate the brood → move the whole colony.
- Hazards should cost the colony something lasting (lost brood, spoiled food, an abandoned nest), not just deal extra damage.
- **Water:** ants float and drift, can't attack, and paddle toward shore; they only drown after a long time. Rain washes out trails, so foragers wander and food deliveries stall. Floods ruin brood and spoil stored food. Workers plug flooded tunnels with soil and rebuild afterward.
- **Fire:** triggers rescue mode: workers carry brood out and soldiers stop attacking. Burning ants run in a panic (spreading fire to grass is a config option, default off). Termite mounds don't burn; termites seal the entrances and wait it out.
- **Lava:** still kills instantly. Ants avoid digging near lava. If lava destroys chambers, the colony moves: workers carry the brood in a procession with the queen to a new site.
- Each species' responses come from its species profile.

### Player interaction
- Ants and termites ignore the player unless their nest is disturbed, then they defend it.
- Later: an ant farm (catch a founding queen in a jar, build a glass-walled farm) and a field journal that fills in as the player observes behaviors.

## Models and animations (GeckoLib + Blockbench)
- **Size:** workers are tiny, a little smaller than a vanilla tadpole (hitbox roughly 0.3 wide × 0.2 tall). Other castes aren't sized yet; keep them proportional to the worker.
- Every mob needs: `idle`, `walk`, `attack`, `death`.
- Specials: `dig`, `carry`, `fly`, `drop_wings`, `head_bang` (termite soldiers), `pulse` (termite queen).
- Bone names are not decided yet. I'll make the first worker ant model in Blockbench first; its bone names then become the standard for every other ant and termite model, so animations can be reused. Until then, placeholder models can use any bone names.
- Walk animation speed scales with movement speed so legs never look like they're sliding.
- Until real art exists, use simple placeholder models so code is never blocked by art.

## Milestones
- [x] M0: Project setup: NeoForge 1.21.1 MDK, git, `runClient` launches the game with the mod loaded.
- [x] M1: Worker ant with a placeholder model and spawn egg that walks around.
- [ ] M2: Swap in the real worker model and animations.
- [x] M3: Queen + colony brain + saving and loading.
- [x] M4: Digging phase 1 + soil pellets + mound.
- [x] M5: Burrowing: shaft stops at block 3, then a tunnel and a room are dug under the soil.
- [ ] M6: Food gathering (seeds) + nest stages + colony growth.
- [ ] M7: Termites: worker, soldiers, king, queen, mound, fungus garden.
- [ ] M8: Ant vs termite fights.
- [ ] M9: Nuptial flights + founding new colonies.
- [ ] M10: Grama grass + Termitomyces mushrooms.
- [ ] M11: Digging phase 2 (bite stages).
- [ ] Later: alarm level + hazards, Matabele ants, leafcutters, army ants, ant farm, field journal.

## Testing tips
- Automatic tests Claude runs before asking the user to test:
  - `./gradlew test`: route planner on a pretend nest (part of `./gradlew build`).
  - `./gradlew runGameTestServer`: real ants in a headless game (about 1 minute). Walks workers and the queen through a ready-made nest, and runs the whole dig (`gametest` package). Uses its own `run-test/` folder; delete `run-test/world` first for a clean run. Debug lines about routes go to `run-test/logs/debug.log`.
- Use a creative test world, `/summon`, and F3 for coordinates.
- `/tick rate 100` speeds up time for testing growth; `/tick rate 20` sets it back to normal.

## Current status
- Mod name: **Ant & Termite Colonies**, mod ID: **antcolonies**, package `com.antcolonies`.
- M0 done: project from the official NeoForge 1.21.1 MDK, build passes, `runClient` confirmed working by the user.
- M1 done: worker ant with placeholder GeckoLib model, walk/idle animations, and spawn egg; user confirmed working in-game.
- M2 deferred: a detailed blocky placeholder worker model exists (bone names: body, abdomen, petiole, thorax, head, mandible_l/r, antenna_l/r_1/2, leg_{l,r}{1,2,3} with _lower and _foot children). User will make the real animations in Blockbench later and must keep these bone names.
- M3 done (user confirmed): queen ant (placeholder, reuses worker model, bigger) + spawn egg; colony brain in `com.antcolonies.colony` (Colony, ColonyManager as SavedData per dimension); queen creates a colony, workers within 32 blocks join it, counts update on death; colony removed when queen dies; `/antcolonies list` debug command. Shared base class `AbstractAntEntity`.
- M4 done (user confirmed): workers dig a 4-deep entrance shaft bite by bite (`DigGoal`, diggable tag), climb walls like spiders (`AbstractAntEntity`, `AntRenderer` tilts the model), carry each bite as a soil pellet shown on the "head" bone (`PelletLayer`) and drop it to build a mound 3 blocks high around the entrance (`CarryPelletGoal`). The mound is made of the new **Burrowed Dirt** block (`ModBlocks`). Ants take no fall damage.
- M5 in progress (user's design): shaft stops at block 3 (block 4 can hit stone); the shaft is never widened so its 4 walls stay solid for climbing out; a 3-block tunnel leaves the shaft bottom toward one side (fixed per colony, falls back to another side if the soil doesn't fit), ending in a 5x5 room, one block tall. Ants stop when the room is finished. Code: `Colony.findDigTarget`.
- Queen goes down and settles in the middle of the finished room (`QueenToRoomGoal`, `Colony.getFinishedRoomCenter`) and stops wandering. Climbing ants get against a wall before rising, and never cross the open shaft.
- Ant navigation rewritten (package `com.antcolonies.nav`): own route planner and walker instead of vanilla pathfinding, own `AntMoveControl`, ants climb at a steady speed along routes, wander goal replaces vanilla strolling. Pellets are never dropped on a living creature (a pellet once buried the queen). Worker speed raised to 0.25 (was 0.15, about 1 block per second; the number is in `WorkerAntEntity.createAttributes`). Verified with the automatic tests; waiting for the user to confirm in-game.
- M5 done (user confirmed "works perfectly").
- Ant hill (user's design): the mound is a round, stepped hill: 3 blocks high in the first ring around the hole, 2 in the second, 1 in the third, hole left open (64 blocks; shape in `Colony.moundHeightAt`). It is built from the bottom up, layer by layer, and then it is capped: once it is full, ants stop carrying soil out and just leave it behind. One block per pellet stays; every bite makes a pellet, so the hill is complete after the first ~11 dug blocks. The finished hill makes the hole a 3-block-deeper funnel; ants climb out of it and walk back down.
- Ants climbing over a ledge (like each hill step) now first walk up to the foot of the wall and only then rise, so they never float up in front of it (`AntNavigator`).
