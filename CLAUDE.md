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
- Keep the short "Current status" at the bottom of this file up to date, and add a bullet for every new feature or fix to `docs/HISTORY.md` (the full change log). If a design decision changes, ask me, then update this file.

## Tech stack (never deviate)
- Minecraft **1.21.1**, **NeoForge** (21.1.x), **Java 21**, official NeoForge MDK (ModDevGradle).
- **GeckoLib** (NeoForge 1.21.1 build) for all entity models and animations, made in Blockbench.
- Mod ID: **antcolonies** (chosen by the user).
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
- **Decisions (user, termite planning session):** the mound is a **tall tower plus a few smaller side chimneys** (living rooms in a wide base, partly underground), with a **simple custom block for the hole** (the user's picture: reddish packed earth with dark crack marks on the sides and a small crown of hollow chimney spires on top, a custom block model, textures cut from the picture). It sits at the top of the tower, the top of each side chimney and the entrances around the base. It is **solid for the player and every other creature, but termites walk through it** (they vanish inside, as if going down the hole); the route planner treats it as a doorway for termites only (species-neutral: a "doorway for this species" check). Ants cannot pass (maybe raiders force their way in, M8). Young: **egg, then nymph** (a small pale nymph the nurses feed fungus food; it grows into a worker or soldier; reuses the ant larva system). Queen about **1 block long**. Soldiers **exist in M7 but do not fight yet** (they guard the entrances; all fighting waits for M8).
- **Step 2 decisions (user):** the queen **starts small and swells as her colony grows** (up to about 1 block long; once big she can no longer walk). **Two spawn eggs** (queen and king); a king near a queen joins her, like ant workers join. **The pair digs the royal chamber themselves** (short shaft and small room, like the lone ant queen), then seals themselves in. The user will send inspiration images for the models.
- **Step 3 decisions (user):** first workers come from a **founding batch**: after sealing, the queen lays about 5 eggs that the royal pair raise on their own fat (no food); egg, then nymph (a small pale termite), then a small worker; the first workers dig the entrance open and start building; later eggs need fungus food (step 5). **Mound realistic: main tower up to about 8 blocks, base about 7x7, 2 to 3 shorter side chimneys** (real Macrotermes mounds are 2 to 9 m). **The mound grows in stages by colony size** (small dome, short tower, full tower with chimneys; worker counts per stage in `macrotermes.json`). **Inside: solid mound blocks with hollow chimney shafts** running down into it (rooms in the base come with the fungus garden, step 5). Soil for the mound comes from galleries the workers dig underground (each bite a pellet, reusing the ant digging and carrying). Blocks: the termite mound block (reddish packed earth with dark cracks, from the sides of the user's hole-block picture, `references/termites/hole_block.webp`) and the hole block (chimney crown) at the tower top, chimney tops and entrances, solid for players, open for termites. Worker look from the real photo (`references/termites/worker_real_photo.png`): pale cream, round yellowish head, no eyes, beaded antennae, greyish abdomen. Built in two parts: 3a workers and the founding batch (eggs, nymphs, workers that open the entrance), 3b the mound blocks and the mound growing in stages.
- **Food, step 4 (my picks while the user was away, not yet confirmed by the user):** foragers bring dead plant material (pieces of forage) into a food store room beside the nest shaft; the numbers per plant are in `macrotermes.json` (details in `docs/HISTORY.md`).
- **Mound plan, step 3b (user's decisions, planning session after the ceilings; REPLACES the "8 blocks, base 7x7" size above):**
  - **Size and time:** a full mound is **20+ blocks tall** (a wide base and 2 to 3 side chimneys) and takes **20+ hours of play** at normal speed (the growth speed is a profile number, sped up for testing).
  - **Grows block by block,** driven by **colony age and size together:** an old small colony keeps slowly adding to it, and a big colony builds faster (after the brain, huge colonies make it shoot up).
  - **Inside:** thick walls around hollow chimney shafts (like real mounds, which breathe through them; less soil needed), not solid.
  - **Soil:** one dug block underground gives enough soil for one full mound block; termites dig a wide gallery network under the mound (later the fungus garden rooms).
  - **The mound block grows in 5 layers** (CHANGED by the user from 4: one soil pellet per layer; solid up to its height, like snow layers). Layers 1 to 4 each add a quarter of the height and are cracked wet soil, cracked the most on layer 1 (more than the old picture), a bit less on 2, less on 3, much less on 4 (already full height); on layer 5 the termites seal the cracks and the block is finished. A half-built block is still a block, just easy to break. Only finished blocks age.
  - **It hardens and dries with age:** fresh (dark damp red-brown, breaks like dirt), setting (red-orange), dry (tan with rain-worn flutes, needs a pickaxe), old (pale, sun-baked, like stone). The textures are drawn by `tools/termite_blocks.py`, not cut from the user's hole-block picture (user: it is AI made, make it better).
  - **User's rule:** wet soil must firm up before more is stacked on it, so a block can only be built on once the block under it is finished and has set (age setting or older). This makes the mound grow at a natural pace.
  - **Savanna:** spawn eggs work anywhere for testing; natural spawning and queens choosing savanna come with M9.
  - **Build order:** blocks first (the growing, hardening mound block and the hole block, placeable and testable by hand), then termites building the mound with them.
  - **Idea (user, LATER, not decided):** animate a termite coming out of the chimney: it pokes out of a hollow spire on top of the hole block and climbs out (and goes back in the same way). Best added as its own small polish step right after the termites use the hole block as a doorway, as one more move kind of the legs (an exact motion like the climbing moves), so it can be dropped without touching the doorway if it turns out buggy.
- **Cathedral mound look (user's decision, concept pictures approved, not built yet; REPLACES the stepped look of step 3b):**
  - Concept pictures in `references/termites/preview/concept/` (drawn by `tools/mound_concept.py`, planning only, nothing in the game uses it). In-game blocks must keep the same colours and look.
  - **Shape:** a heavy base with buttresses (ridges running from high on the tower down to the ground), a tall main spire with vertical ridges and grooves (fluting), and a cluster of about 6 lower spires of different heights; about 32 blocks tall when full-grown (was 22). Every mound varies a little.
  - **New blocks:** a **spire** piece in 5 thicknesses (like pointed dripstone) so spires narrow to a point; a **chimney mouth** piece at the tip of the tallest spires (where the termites peek out); **ramps** (straight, outer corner, inner corner) that hide the stair steps.
  - **Colour:** age colours in streaks running down the mound (rain), plus patches of fresh dark soil where they build, not rings by height.
  - **Young stage (user):** a low base with only a small, stubby spire (about 5 blocks tall in all); the spires catch up as the mound grows.
  - **Built from inside (user picked "option A" after step 1):** termites cannot climb on the cathedral pieces (not full faces), so the pieces are only the outer skin and the spire tips, over a mound of full blocks with hollow shafts inside. Termites travel and build from inside, like real termites (they rarely walk on the outside of a mound). The spire pieces and the chimney mouth let termites through (like the chimney block): a termite crawls up inside a spire as a hidden tube, and a spire grows by a termite inside its top piece adding the next one. The chimney trip becomes a **peek**: up inside the spire, head out of the mouth, look left and right, back down inside (no climbing down the outside). The big shaft up the middle stays a real hollow full-block shaft (real mounds have one big central chimney). Soldiers (step 6) can later swarm the outside when the player breaks in, if wanted. (Rejected for now: teaching the legs to climb the real shapes, "option B"; dropping the chimney, "option C".)
  - **Step 2 decisions (user):** first get the **shape** perfect: the user spawns it with `/antcolonies mound <height> instant` and looks; how the termites build it (and reach the skin) is left until the tunnel block, which comes straight after. **Chimney mouths** on the main spire and the 2 tallest side spires (3 on a full-grown mound). **No entrances at the foot** any more (termites go out by the foraging holes; the foot stays closed like a real mound). The mound **stops using the old chimney crown block** (the mouth replaces it; the block stays in the game so old worlds load).
  - **Termite tunnel block (user's idea, PLANNED, comes AFTER the cathedral is set up):** a mound block with a small channel through it, so the inside of the mound is full of termite-sized tunnels (as in a real mound cut open) instead of whole-block passages. The channel is **6 pixels wide and 6 tall** (a worker is about 5 by 3; the user will look and resize if needed) and can open out of any of the 6 sides, joining like a fence (straight, bend, T, turning upward). A termite bites a mound block and it opens just enough for it, toward the side it came from, and the tunnel grows bite by bite; termites can also build new blocks with the channel already in. Inside the channel is **darker and smoother** than the soil (worn by traffic; user: yes). The tunnels are for the **builders** to reach every spot they need; **soldiers do not fit** (user), they only use the 1-block spaces (the shaft, rooms). Ideas that came with it: the spires' hidden tube becomes the same channel up to the mouth; termites rush to seal a tunnel the player breaks into; termites deep in tunnels can't be seen, so later the brain can turn them back into numbers there; much later, termite-sized galleries underground too (a big nest change, only an idea). Suggested order: (1) the block, placed by hand, joining up, ageing, the cut-open look; (2) termites crawl through it (a new move along the channel's middle, and stepping in and out of the shaft); (3) termites carve tunnels and build them in; (4) the cathedral's under-skin network uses it.
  - **Tunnel decisions (user, after seeing the cathedral in-game):** tunnel blocks **join up on their own** with the blocks around them (like fences), and the channels lead into the big shaft, which is the way back down to the nest. The user's main worry: **the termites must really be able to build the whole cathedral and never get stuck** (the hardest part). Reach: **normally 1 block** (building from the channels looks natural); a **2-block reach only as a fallback** when a spot cannot be reached any other way.
  - **More decisions (user said yes to all):** the tunnel network is part of each stage's plan, laid out so every outside block (soil, ramp, spire piece) is next to a channel, with channels up the middle of each spire to the mouths. An **automatic test proves every stage of many mounds can be built** by pretending to be the termites (a block only when it has something to stand on and a termite can reach it from a channel or the shaft it can get to; a dead end fails the test and names the block); the termites in the game follow the same rules. The 2-block fallback is only for surprises from the world (a player breaking blocks, terrain); the plan itself must never need it. **Windows:** to build the outside, a termite bites a small hole in a tunnel block at night or after rain, pokes out, adds soil or shapes a ramp beside it, and plugs it again (like real termites). Tunnel blocks join up with tunnel blocks and the shaft, but **never open to the outside air on their own** (only a termite's window does, and it is plugged afterwards).
  - **Climbing the outside (user: "should be possible no matter what"):** termites must be able to walk and climb on the outside of the mound at night, including on the cathedral pieces (ramps, spire pieces, the mouth). This partly brings back "option B": the legs learn the real shape of the pieces. It also brings back the full chimney trip (out of the mouth, down the outside of the spire). Not built yet.
- **Realism pass (user's decisions, questionnaire after step 4; for the next steps, not built yet):**
  - **Exits:** the main way in and out are **foraging holes**: small holes in the ground a few blocks out from the mound, joined to the nest by underground tunnels, opened when foraging starts and plugged with soil afterwards. The chimney is only a fun extra: about **1 in 10** trips out or in a termite goes by the chimney with the peek animation.
  - **Runways:** a new thin **soil sheeting** block (a carpet of mud) laid over the path to a food source; termites walk in its cover; it crumbles when the path is no longer used.
  - **Schedule:** forage mostly **at night, and on rainy or overcast days**; stay in during sunny days.
  - **Food path:** once the fungus garden exists, food goes **straight into the garden** (the young workers make comb from it); the step 4 food store room is removed then.
  - **Food:** a new **Dry Grass** plant (savanna; green, then dry; termites only take it dry), pulled forward from M10; **saplings are removed** from the food list (sticks stay); a new **fallen branch** block (dry acacia branch lying in savannas, spawns naturally); termites **slowly eat into old natural logs** (never player-built wood unless the config option is on). Green short grass: the user picked Dry Grass; how much green grass still counts is to settle when it is built.
  - **Jobs by age:** young workers stay inside (garden, nymphs, inside building), older ones forage and build the mound outside; each worker's age is saved. Replaces the half-and-half split.
  - **Fungus garden:** several small **garden chambers around the royal chamber** (the nest body under and in the mound base), more as the colony grows. A new sponge-like **fungus comb** block built up from forage; white **fungus nodules** grow on it over time; workers pick them to feed the queen and nymphs; old comb is eaten and rebuilt.
  - **Termitomyces mushrooms** come with the garden (pulled forward from M10): after rain they sprout on and around a mound with a healthy garden; players can pick and eat them; rare giant ones.
  - **Fungus garden decisions (user, after tunnel plan step 4):** the queen lays a **steady stream** of eggs while the workers keep feeding her nodules, faster the more she has swollen, and stops when unfed (not the ant queen's "meal per egg"). Workers carry each egg into a **garden room** and lay it on the comb; the nymphs grow up there, beside their food. The **food store room is removed**: new colonies dig garden rooms round the royal chamber instead (old colonies keep their store as an unused room). Built in parts, each tested before the next: **5a** garden rooms, comb, nodules, feeding the queen and nymphs, eggs; **5b** jobs by age; **5c** Termitomyces mushrooms.
  - **Jobs by age decisions (user, start of 5b, 2026-10-13):** **3 stages**: young = nurse (garden, nymphs, feeding the queen), middle = builder (digging, building the mound from inside **including through windows**: a termite at a window never leaves its tunnel; the user chose this after hearing that giving windows to old workers would break the "never gets stuck" proof), old = outside worker (foraging through the holes at night, the rare outside trips). **Flexible:** age sets each worker's usual job, but when a job is short-handed the colony moves the workers nearest in age over (real insects do this). **Several days per stage:** nurse 3 in-game days, builder 5 more, then old (numbers in the species file). **Young workers look paler** and darken as they age. **The ants get jobs by age too** (young nurse, middle dig, old forage). `/antcolonies list` shows the age groups and jobs.
  - **Termitomyces decisions (user, start of 5c, 2026-10-13):** mushrooms come up **mostly on the ground in a ring round the mound, sometimes on its lower slopes**; a rare **giant is a plant 2 to 3 blocks tall** with a wide cap overhanging the blocks round it (it grows on the surface, so the termites dig nothing for it; built 3 tall); **raw is a little food, cooked a good meal** (furnace, smoker, campfire); they **wilt after about 2 to 3 in-game days** if nobody picks them.
  - **Dead wood worth more (user's idea, 2026-10-13, not decided):** fallen branches and old logs should feed the colony more than dead grass, so a big colony sends long lines of foragers far out to them (it looks great). Fits the planned fallen branch block and log eating; needs the bigger foraging range of "Smart foraging" (routes round obstacles, food map) and the virtual population for 100s of termites; the sentry line can then follow the real route instead of a straight line. Settle the numbers when it is built.
  - **Finding food (user, 2026-10-13, after seeing termites ignore a hay bale for grass):** a first small piece of "Smart foraging": foragers weigh food by how much it is worth against the walk, and a rich find draws a line of them. The user chose **finding** over knowing: a worker must come near a plant to find it, and the colony knows it once that worker is back inside (recruiting). Built on branch `m7-food-finding`, details in `docs/HISTORY.md`.
  - **Clearing:** termites leave bare **patches**, and dry grass regrows over time, so the savanna is never stripped for good.
- **M7 step plan:** (1) species profiles: the harvester ant's numbers move into `red_harvester_ant.json`, no change in game (BUILT); (2) termite king and queen (placeholder models, royal pair starts the colony, queen sits in a royal chamber and pulses) (BUILT); (3) termite workers and the mound block, the mound grows with the colony (BUILT); (4) food gathering (dead plant material) (BUILT, confirmed in-game; details in `docs/HISTORY.md`); (5) fungus garden turns it into colony food that feeds the queen and nymphs (5a garden BUILT and confirmed, 5b jobs by age and 5c Termitomyces BUILT and confirmed in-game); (6) minor and major soldiers (guarding only; see "Soldier decisions"; 6a and 6b BUILT and CONFIRMED in-game by the user; 6c BUILT, waiting for the user to test it in-game, on branch `m7-alarm`; details in `docs/HISTORY.md`). After 6c, outside the numbered steps: **finding food** (BUILT on branch `m7-food-finding`, made from `m7-alarm`, waiting for the user to test it in-game). **Git:** test 6c first, then food finding; merge `m7-alarm` into master once 6c works, then `m7-food-finding`. With 6c confirmed, M7 is finished; next in the roadmap is tidying up both species.
- **Chickens peck at workers (user's idea, 2026-10-13, BUILT as part of step 6c, details in `docs/HISTORY.md`):** chickens (vanilla) walk up to ant and termite workers that are out on the surface and peck at them, like real birds eating insects. A natural predator outside the nest, a reason to forage by night or under cover, and later a target for the defenders (M8).
- **Soldier decisions (user, start of step 6, 2026-10-13, after a research questionnaire; not built yet):** as realistic as possible (from field notes on Macrotermes: minors escort foragers and are the first line of defence, majors mostly guard the nest and stand behind the minors at a breach; soldiers drum their heads to raise the alarm, about 11 times a second, others feel it through the walls, drum too and run toward it).
  - **Look:** the real look: pale body like the worker, large shiny orange-brown armoured head, long curved black mandibles, no eyes. **Minor** about worker size with a bigger head; **major** about twice a worker's size with a huge head. Neither fits the tunnel blocks (shaft, rooms, doorways and the surface only).
  - **Roles (real split):** **minors** go out at foraging time: they stand at the open foraging holes and form a **sentry line** along both sides of the foragers' path (one every few blocks, facing out), and walk back in last, behind the workers, when foraging ends. **Majors** stay inside guarding the shaft, the royal chamber doorway and the garden doorways; at a breach they stand behind the minors as the second line.
  - **Alarm ladder** (each level calms down step by step after a few minutes): (1) **footsteps** (a player sprinting or jumping on the mound; sneaking does not): a few minors come to the nearest opening, look and drum briefly, then calm down. (2) **a termite is hit, or an enemy (ants of another colony, a chicken) comes near the foragers or holes:** the alarm spreads to the minors outside, the sentry line closes in round the foragers and the foragers hurry home early. (3) **breaking into the mound or nest:** the full alarm spreads through the whole nest by drumming soldier to soldier, minors rush to the breach with majors behind, workers **seal the breach behind them even if that shuts soldiers outside** (shut-out soldiers come back later by a foraging hole), foraging stops for a while. (4) **repeated break-ins:** everybody stays in for a whole day and the colony raises more soldiers.
  - **Where they come from:** **nymphs on demand**: when the colony has fewer soldiers than it wants, a nymph grows into a soldier instead of a worker (a major needs a bigger meal).
  - **How many:** about **1 in 10** termites a soldier, **3 minors to 1 major**, more after big alarms (level 4). Soldiers cannot feed themselves (jaws too big): **nurses bring them fungus nodules**, like the queen.
  - Still no fighting (M8): soldiers only guard, drum and block.
  - **Build order** (user: "sounds good"): **6a** the two soldier mobs (models, head-bang animation), nymphs becoming soldiers on demand, nurses feeding them, majors guarding inside (BUILT); **6b** minors at the holes and the sentry line with the foragers (BUILT); **6c** the alarm ladder (drumming that spreads, rush to the breach, sealing them out, level 4) (BUILT, with the chickens and the bite).
  - **Chickens and a simple bite in 6c (user, 2026-10-13):** chickens hunt workers out on the surface (the easy way to test alarm level 2), and minor soldiers fight back, against chickens only for now: a simple bite of **3 damage** (a chicken has 4 health, so 2 bites kill it) with a quick jaws-shut animation (simple placeholder, refined later). Players and ants are still not attacked (M8). Still to settle when 6c starts: how often chickens hunt and how hard they peck, whether a chicken eats a worker it kills, whether majors bite too.

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
  - User's plan (M9, flying queen): every area she flies over gets points for how good it is for a nest. For the red harvester ant, grass nearby is a must (food), OR a village nearby counts too (later, more detail wanted).
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
- **Starvation (user's design, built):** a larva that gets no bread for 2 days dies (the brood is most sensitive); the queen, who is fatter, dies after 5 days without bread. The colony then ends and its nest is left **abandoned**.
- **Tidy-up decisions (user, 2026-10-13; built on branch `tidy-up`, see `docs/HISTORY.md`):** **realism over everything** (the user's rule for every design choice). Worker cap **60** for every colony. **Dead termites leave bodies**: a fresh one is eaten in the fungus garden, an old one walled up with soil where it lies (like real termites). **Nest stages**: rooms are dug only as the colony grows (workers and stored food per level in the profile). **Queenless colonies carry on and dwindle**: the workers keep working and raise the brood already there, no new eggs, workers die of old age one by one, then the nest is abandoned. The chicken is only a fun predator for the soldiers to kill (no change).
- **Abandoned nests (user's idea, LATER, not built):** a nest left behind (queen starved or killed) should stay in the world as a ready-made nest that another queen of the same species can move into (for example a winged queen after a nuptial flight, M9), instead of digging her own. Needs: a record of abandoned nests, a nest-site score bonus for them, and a check that the rooms are still intact. Species-neutral, so termites can reuse it.

### Colony life cycle (planned for M6 and M9, not started)
- Real cycle as a game loop: mature colony releases winged queens and males after rain; they mate in a nuptial flight; the queen lands, drops her wings, digs a small chamber, seals it, lays eggs and raises the first (smaller) workers alone; most founding queens fail; the colony grows over years; it ends when the queen dies or it starves.
- Prepare these before M9 (design M6 with them in mind):
  1. **Queen life stages:** winged virgin, flying, landed (wings off), sealed founding queen, settled queen of an established colony. The colony is created when she founds her nest, not when she spawns.
  2. **Nest plan as data:** shaft, tunnel and room sizes move out of `Colony` constants into a per-stage list of cells (founding plan is much smaller), later read from the species profile. (Started: `NestPlan` lists the rooms; sizes are still constants in it, and all rooms are dug at once. Next: pick rooms per nest stage, then read them from the profile.)
  3. **Colony clock and brood pipeline:** each colony stores its age and stage start; the brain keeps counts of eggs, larvae and pupae with timers, gated by food and queen health (numbers only, so it is cheap).
  4. **Colonies live offline:** the brain keeps ageing, growing brood and eating in unloaded chunks; physical digging only happens near the player and catches up when chunks load.
  5. **Flying legs:** a separate flying mover for winged queens and males, driven by the same mind layer as walking.
  6. **Site choice:** scoring weights from the species profile (soil, distance to other colonies, food).
- **Decisions (user):** how long a colony takes to mature is decided later, once more of the mod exists (make it a config number, "life speed"). Seasons: CHANGED, see "Seasons and daily schedule (FUTURE PLAN)" below (Serene Seasons becomes required). Males are mostly numbers, with a handful shown as real flying ants during a flight. Founding queens can be threatened by predators, rival colonies and the player. Ants fight any ant of another colony and fight termites (one rule: different colony id = enemy). An anteater mob is planned later; when it kills real ants the colony headcount drops too. Hunger and eating: for our own mobs only, not vanilla animals.

### Seasons and daily schedule (FUTURE PLAN, user's decision, not built)
- **Serene Seasons is a required dependency** (the user checked: it has a NeoForge 1.21.1 build). It replaces the old "no built-in seasons" decision. All season questions ("what season is it", "how hot is it") go through one small species-neutral part of our code, so if Serene Seasons breaks or stops updating only that one spot needs fixing.
- **Two clocks run together:** seasons decide *what* the colony does this time of year; day and night decide *when* in the day it does it.
- **Yearly clock (ideas):** spring the colony wakes and the queen lays again; summer busy foraging and nuptial flights after rain; autumn filling the granary; winter deep in the nest, living off stored seeds, no foraging. Termites slow down in the dry season. Serene Seasons also changes biome temperature by season, which can feed a heat idea (deeper rooms in summer, brood moving up in spring).
- **Daily clock (ideas):** harvester ants send morning scouts first, then foragers; go inside in the midday heat; a smaller late-afternoon round; everyone in at night, entrance partly plugged, nurses move brood deeper. Termites avoid the sun and work at night or in covered tunnels.
- **To settle later:** colony life speed should fit the Serene Seasons year length (it is a setting in its config), for example a colony needs about one year before it releases winged queens. Check how other mods read its season when we build it.

### Smart foraging: scouts, trails and colonies that live offline (FUTURE PLAN, user's idea, not built)
- **Goal (user):** it should feel natural. A colony running low on food sends scouts, finds a rich patch, and a trail of ants grows to it, whether the player is there or not; the player just happens to walk by and sees the column marching seeds home. Nothing is staged for the player.
- **Smart as a group, not one by one:** like real ants, each ant follows simple rules and the colony brain does the bit of thinking on its timer. Cheaper and more realistic than genius single ants. Species-neutral (termites forage too, army ants raid, leafcutters need trails).
- **Food map:** the brain remembers food patches (where, how rich, how empty they are getting). As nearby food runs out, the foraging range slowly grows.
- **Scouts:** a few ants range farther than the rest; a scout that finds something good lays a trail home, and more foragers are sent the better the find.
- **Trails:** a trail fades unless ants keep using it; a used one gets stronger; a dead patch's trail disappears. Real harvester ants keep main trails for years (worn paths out from the hill).
- **How many foragers go out (real harvester ant rule, Deborah Gordon):** no ant is in charge; the colony sends more foragers when foragers come back with food quickly, fewer when returns are slow.
- **Village seed raids:** scouts *discover* villages and farms (no scanning the map for villages: costly and feels like cheating); the brain weighs reward (rich seeds the queen wants) against distance and danger. Ties to the farm-raid config option (default off).
- **Danger memory:** the brain remembers where ants died and avoids that area for a while.
- **Offline trails, the timetable:** the trail is a list of points in the brain; each forager on it is a timetable line (left the hill at time X, speed, carrying or not). Where an ant is now is worked out from the time, so nothing ticks while unloaded. When the player's chunks load near the trail, real ants are spawned exactly where the timetable says, facing the right way, ideally just out of view; when they leave the loaded area they turn back into lines BEFORE they reach unloaded ground (Minecraft freezes mobs in unloaded chunks). Never force-load chunks.
- **Golden rule: one source of truth.** The timetable is the truth; a real trail ant is only a view of one line and carries its id. Real ant dies: line removed, headcount drops. Real ant picks up a seed: line updated. Never both a line and a real mob (no duplicates), never removed without going back into a line (no lost ants). An ant that leaves the trail (fight, chasing a seed) becomes a normal ant. Frozen leftovers are cleaned up and counted back when their chunk loads.
- **Finding food in unloaded land:** (1) memory of what the colony saw while that land was loaded; (2) the biome, worked out from the world seed without loading the chunk (believed possible, check at the PC), so "plains or forest, probably grass with seeds" plus a chance roll for how rich; (3) the guess is replaced by the real blocks once that land loads (a "lush patch" that is really a lake: trail fades, the colony learns). The world may change while unloaded (a wall across the trail, the patch eaten): trust memory until loaded, then check, reroute or drop.
- **Seeds in vanilla don't lie on the ground:** the "lush area" means lots of short grass to clip, or later a grama grass meadow (M10), which makes that plant matter.
- **Where "too much" really shows up:** the player must be able to see it (visible trails and scouts, maybe the field journal explains what the colony is doing); unloaded land (handled by the timetable); systems tangling (build one at a time with tests); balance.
- **Order:** virtual population first (colonies are real mobs up to 20 workers today), then trails and the timetable, then scouts and far foraging, then raids and danger memory. Big feature overall. Timetable math gets unit tests; spawning and despawning gets game tests.
- **Reading (for the user):** Deborah Gordon, *Ant Encounters* (red harvester ants); Hölldobler & Wilson, *The Ants* and *The Superorganism*; J. Scott Turner, *The Extended Organism* (how Macrotermes mounds breathe).

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

## Roadmap order (user's decision, after the ceilings)
1. Finish the termites (M7): 3b the mound (planning first), 4 food gathering, 5 fungus garden, 6 soldiers (guarding only).
2. Tidy up both species (termite bodies, nest stages, growing past the 20-worker cap, other loose ends).
3. The brain: virtual population, colonies live offline (this also fixes the hunger clocks that run on in unloaded chunks; the user only tests with loaded colonies, so no stopgap fix now), scouts, trails, food map.
4. Fighting (M8), right after the brain (defense waves need the virtual population).
5. Flying queens (M9): winged queens fly, score the land (food, biome, soil, distance to other colonies) and settle; species-neutral, termite royal pairs use it too.
6. Other species on the same framework.
- **Balance goal (user, "like League"):** ants are strong early, great mid game, and fall off late to the termites; Macrotermes are weak early and scale very hard (like real life: a slow, fragile start, then a huge colony and mound). The termite mound should keep growing over many hours of play.

## Milestones
- [x] M0: Project setup: NeoForge 1.21.1 MDK, git, `runClient` launches the game with the mod loaded.
- [x] M1: Worker ant with a placeholder model and spawn egg that walks around.
- [ ] M2: Swap in the real worker model and animations. (A detailed harvester ant model and animations by Claude are in, `tools/ant_models.py`; waiting for the user.)
- [x] M3: Queen + colony brain + saving and loading.
- [x] M4: Digging phase 1 + soil pellets + mound.
- [x] M5: Burrowing: shaft stops at block 3, then a tunnel and a room are dug under the soil.
- [ ] M6: Food gathering (seeds) + nest stages + colony growth.
- [ ] M6.5 (user's decision): Polish the red harvester ant as far as possible BEFORE any termites, with features built species-neutral so the other ant colonies can reuse them. Backlog (from the user's list of harvester ant facts, in the order we pick from): ant bread (seeds milled into bread that feeds queen and brood; queen part and larvae done, waiting for the user to confirm larvae in-game), rich bread burst (BUILT, confirmed in-game; worker hunger of 1 seed a day BUILT, confirmed in-game; colony starvation BUILT, confirmed in-game), "deep soil" (user's idea; BUILT, waiting for the user to confirm in-game, see `docs/HISTORY.md`), age-based jobs (young nurse, middle-aged dig, old forage), cleared circle around the hole, seed kinds with different values, morning scouts and a day/night/heat schedule, pebble cap on the hill, refuse chamber (dead ants: BUILT and confirmed in-game; seed husks still open), warm/cool nursery moves, sting-and-grip fight, alarm pheromone, territory fights, horned lizard predator, seed-stealing guests, rain events (plug the hole, dry wet seeds). M9 (nuptial flight, claustral founding) stays as planned.
- [ ] M7: Termites: worker, soldiers, king, queen, mound, fungus garden.
- [ ] M8: Ant vs termite fights.
- [ ] M9: Nuptial flights + founding new colonies.
- [ ] M10: Grama grass + Termitomyces mushrooms.
- [ ] M11: Digging phase 2 (bite stages).
- [ ] Later: alarm level + hazards, Matabele ants, leafcutters, army ants, ant farm, field journal.

## Testing tips
- Automatic tests Claude runs before asking the user to test:
  - `./gradlew test`: route planner on a pretend nest (part of `./gradlew build`).
  - Only some game tests (much quicker while working): set the environment variable `ANTCOLONIES_TESTS` to a comma-separated list of test class names (such as `termiteclimbingtests`) or parts of test names, for example `ANTCOLONIES_TESTS=termiteclimbingtests ./gradlew runGameTestServer` in Git Bash (`gametest/TestFilter`; the log then says `ONLY running n of m game tests`). Without it every test runs.
  - `./gradlew runGameTestServer`: real ants in a headless game (about 20 minutes for all 98 tests now; it was about 1 minute early on). Walks workers and the queen through a ready-made nest, and runs the whole dig (`gametest` package). Uses its own `run-test/` folder; delete `run-test/world` first for a clean run. Debug lines about routes go to `run-test/logs/debug.log`.
  - **Tests getting in each other's way (decided by the user 2026-10-13, built, NOT run yet):** a colony reaches up to 32 blocks (food radius 24, workers join a colony within 32, the alarm looks 32 out), more than the old 25-block arenas, so neighbouring tests' colonies interfered and full runs failed for no real bug. The `arena` template (`data/antcolonies/structure/arena.nbt`, used by every test) is now 65 x 16 x 65: the old arena in its north-west corner (coordinates unchanged, middle at 12, 12) plus 40 empty blocks east and south, so arenas are about 45 blocks apart. `helper.getBounds()` is the whole 65-block square. About 5 times as much ground is loaded; if the test world runs short of memory, lower the padding. After the first full run: check the time and that the neighbour failures are gone, then remove the test-only workarounds one by one (`FoodMap.setTestArea`, `Alarm.setTestArea`, `knowForTests`, the "alone" batches, `keepOutOfColonies`).
  - Until then: when a test fails only in a full run, run it on its own (or with its class) before calling it a bug. Today every full-run failure passed on its own.
- Use a creative test world, `/summon`, and F3 for coordinates.
- `/tick rate 100` speeds up time for testing growth; `/tick rate 20` sets it back to normal.

## Current status (short; the full change log is in `docs/HISTORY.md`)
- Mod name **Ant & Termite Colonies**, mod id `antcolonies`, package `com.antcolonies`.
- **Backup:** private GitHub repository `https://github.com/urbad150-hub/ant-colonies-mod` (remote `origin`, every branch). Push after every commit (`git push`).
- **Done and confirmed in-game:** M0, M1, M3, M4, M5; the harvester ant's nest (deep spiral nest, founding chamber, sealing, repairs, hill), seeds, ant bread and rich bread, eggs, larvae, worker hunger, colony starvation, dead ants and the refuse chamber, species profiles (`data/antcolonies/species/*.json`), surface walking and ceilings. Termites (M7): king and queen, workers and founding batch, mound blocks, the cathedral mound with tunnel blocks built from inside, climbing the outside pieces, crawling through tunnels, food gathering, foraging holes and the day/night schedule, fungus garden and the queen's egg stream (5a), jobs by age for both species (5b), Termitomyces mushrooms (5c), soldiers guarding (6a) and minors out with the foragers (6b).
- **Built, waiting for the user to test in-game:**
  - **6c, the alarm ladder** (footsteps, enemies at the foragers, break-ins, repeated break-ins), chickens hunting termite workers, minors biting chickens. Branch `m7-alarm`.
  - **Finding food** (food map: find, tell, know; foragers choose by worth; scouts). Branch `m7-food-finding`, made from `m7-alarm`, so it holds 6c too.
  - **Git plan:** test 6c, then food finding; then merge `m7-food-finding` into `master` (that brings both). `master` is behind both. `cathedral-tunnels` and `m7-soldiers` are already merged into `master` and can be deleted. With 6c confirmed, M7 is finished.
- **Game tests:** arenas spaced to 65 x 65 so neighbouring tests' colonies can't reach each other (see Testing tips); a full run on this has NOT been done yet. Next run: check the time and that the neighbour failures are gone, then remove the test-only workarounds one by one.
- **Still open from earlier milestones:** M2 (the real worker model and animations, waiting for the user's Blockbench work); hunger clocks run on in unloaded chunks (fixed by the brain).
- **Unconfirmed picks:** many numbers in the change log are marked "my pick, to confirm" (food per plant, mushroom timings, soldier counts, ramps costing 1 pellet, old spire pieces crumbling, ...). Search `docs/HISTORY.md` for "my pick" before relying on one.
- **Known code issues** (from the full review, 2026-10-13; the fixed ones are in `docs/HISTORY.md`, "Tidy-up"): chickens near the holes can keep the alarm at 2 and stop foraging for long (needs the user's decision); performance at 40 to 60 mobs (6000-step failed route searches); `Alarm`/`Soldiers` use `TermiteSoldierEntity` directly (make a shared defender idea when the ant defenders are designed, M8); nest room sizes are still in code; `tools/bbmodel_import.py` overwrites both Blockbench projects (ask before rerunning once the user edits them).
- **Tidy-up started** on branch `tidy-up` (from `m7-food-finding`): part 1 done, game tests not run yet. Part 2 (cap 60, termite bodies, nest stages, queenless colonies, old age, the colony's own clock) done; game tests not run yet.
- **Next in the roadmap:** after the tidy-up, the brain (virtual population, colonies living offline).

## Other notes files (read only when the task needs them)
- `docs/HISTORY.md`: the full change log of every feature (class names, numbers, tests, the user's confirmations) and the old handover notes for a code reviewer. Older bullets name methods as `Colony.something`: since the code review split they live in the colony's parts (`Nest`, `FoodStore`, `Brood`, ...).
- `docs/BUGS.md`: every bug we have had and its lesson. **Read it before touching `AntNavigator`, `AntPathFinder`, the goals, or anything that places blocks.**
