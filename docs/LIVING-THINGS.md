# Living things: the master list

(Moved out of CLAUDE.md on 2026-11-19; CLAUDE.md keeps the spawning rule in short. Add new living things here.)

Every living thing in the mod, built or planned, plants and fungi included, in one place. **Add new ones here as they are thought of** (status, then a pointer to where the detail lives); detail stays in `docs/DESIGN.md`. Status words: **BUILT** (in the game), **LOOKS** (in the model viewer only), **PLANNED** (agreed, not built), **IDEA** (talked about, not decided), **HELD** (set aside by the user), **DROPPED**. Realism rule: colony insects strict; everything else fun first, inspired by real life.

**Rule: every animal that should spawn by itself gets the spawning check list (user, 2026-11-16, "this spawning group glitch keeps happening ... make a rule for all future sessions").** Three animals in a row looked fine with spawn eggs and never spawned in the world (the crocodile twice, BUGS 218, then the crab and snail, BUGS 224), because eggs, `/summon` and a test's `helper.spawn` skip every check the world's own spawner makes. So, for every new animal (or any change to one's spawning), before calling it built:
1. **Pick its spawning group on purpose and write it in the table below.** A group's cap is shared by every kind in it, and kinds kept near the player (the river groups keep theirs within 96 blocks) fill it for good. A rare kind never shares a group with a common kept one; a kind gets its own group when it would. New groups go in `META-INF/enumextensions.json` and `registry/SavannaMobCategories`.
2. **Make its spawn check fit Minecraft's spawner**, which: draws **one random height** for a try and keeps it for the whole group (members only move sideways, so a check that passes at one exact height, such as "the block over the bed", almost never passes and spawns groups of one: allow any height in its column and set the animal down after, as `animal/RiverBed` does); picks the kind by weight **before** checking the spot suits it; then checks the placement type, the animal's own spawn rule, room for its box, and last `checkSpawnObstruction` (a land-mob base, `PathfinderMob`, refuses any water in its box: a land animal that spawns in water needs the water animals' answer, BUGS 218).
3. **Keep the quiet-savanna gate** (`worldgen/BigSavanna`: wrap its placement in the gate in `SavannaEvents`).
4. **Prove it with the world's own checks in a game test**, never an egg: count the spots each kind in its group passes on a scene built at a real height (the test arena lies at about y -42, below the sea-level bands; build at `level.getSeaLevel()` with absolute positions, as `crabsAndSnailsSpawnOnShallowBedsByThemselves` does), using the shared `worldWouldSpawn` helper in `RiverLifeTests`; check the animal is in its group's spawn list in its biomes; and say in the report how its spots and weight compare with its group mates'.
5. **Tell the user to check it in a new world** (spawns happen only in new or reloaded land and round a player who is not a spectator).

**Spawning groups (keep this table current):**
| Group | Cap round a player | Kept | Members |
|---|---|---|---|
| `antcolonies:river_fish` | 45 | within 96, gone past 128 | tilapia (groups of 2 to 5), catfish |
| `antcolonies:river_hunter` | 5 | within 96, gone past 128 | crocodile |
| `antcolonies:bull_shark` | 2 | within 96, gone past 128 | bull shark (alone, 80 apart, in water 4 or more deep; river zones: the coast's whole cap, one near inland, none elsewhere; 2026-11-22) |
| `antcolonies:river_bed` | 24 | within 96, gone past 128 | crab, snail |
| `antcolonies:river_bank` | 2 | within 96, gone past 128 | Nile monitor (alone, 80 apart) |
| `antcolonies:river_school` | 40 | within 96, gone past 128 | river barb (schools of 8 to 14) |
| `antcolonies:bullfrog` | 4 | within 96, gone past 128 | African bullfrog (ones and twos; its tadpoles later; was cap 12 in groups of 1 to 3: the user found too many, 2026-11-20) |
| `antcolonies:hippo_pod` | 6 | kept for good | hippo: no spawn list since 2026-11-21 (BUGS 271): pods of 1 to 3 are laid with new land at pod sites about every 250 blocks of the near and mid-inland rivers (`worldgen/HippoPodFeature`, `PodSites`) and never removed; the group only keeps hippos counted apart |
| `antcolonies:river_otter` | 2 | within 96, gone past 128 | African clawless otter (ones and twos, often a mother and her pup, a quarter of them pups; was cap 6 in families of 2 to 4: the user found too many, 2026-11-22; near, mid and far inland rivers and oxbows, none at the coast or in the pans) |
| `antcolonies:fly_cloud` | 18 | within 96, gone past 128 | fly cloud (one or two at a spot, 4 to 12 flies; was cap 6, one at a time, kept within 64: tripled 2026-11-20, the user asked for more flies) |

(2026-11-21, river zones, built: every river animal's spawns also ask `worldgen/RiverZones`: none in water shut off from the sky, and each kind only up to its zone's share of its group's cap within 64 blocks, by coast, near, mid and far inland river and the still waters; the table of shares is in `RiverZones.SHARES` and `docs/DESIGN.md`, "River zones".)

(2026-11-22, oxbows and pans step 5, built: the still waters zone split in two, `OXBOW` (floodplain water: the old still waters' shares) and `PAN` (open savanna water: snails, bullfrogs and fly clouds only, which spawn there up to 24 over sea level; none of the river's fish, crocodiles, crabs or monitors); 1 in 3 deep oxbows on the hippos' rivers get a hippo pod laid with the land. Groups and caps unchanged; proved by `RiverLifeTests.pansKeepSnailsFrogsAndFliesOxbowsTheRiversAnimals`. Check in a new world.)

(2026-11-20 with the barb, built: `river_fish` cap 60 -> 45 (the user's number) and tilapia groups 2 to 5, to make room for the barbs; `docs/plans/prey-fish-and-frogs.md`.)

**1. Colony insects (strict realism; each a species profile on the framework)**
- Macrotermes (mound termite): workers, minor and major soldiers, king, queen BUILT; alates (winged) PLANNED (M9.5). Savanna mod.
- Matabele ant (Megaponera analis): majors, minors, ergatoid queen BUILT; colony splitting PLANNED (M9.5); moving house PLANNED (M8 step 6). Savanna mod.
- Red harvester ant (Pogonomyrmex): workers, queen BUILT; rework PLANNED with the desert mod. Desert mod.
- Leafcutter ants (Atta, Acromyrmex): IDEA (jungle mod).
- Army ants (Nomamyrmex, raiding leafcutters; savanna driver ants named only as a reason Matabele move): IDEA (jungle).
- Honeypot ants (Myrmecocystus, neighbours of the harvester): IDEA (desert).
- Driver ants (Dorylus, the savanna's army ants: huge nomadic swarms, one of the real reasons Matabele colonies move house; a real enemy of both our species): IDEA (user's list 2026-11-16). Savanna mod; strict realism as a colony insect. **Size of the job (talked over 2026-11-16):** likely well under the Matabele's, as the legs, bodies, fight system, prey pile, scent trails, the brain's away raids and moving house (M8 step 6, still to build, so it serves both) carry over; new are the swarm raid (a wide front sweeping all prey, no scouts or target, a new mind), the temporary underground nest, the carpet-of-ants look within the real-mob cap, and away swarms kept cheap (a moving patch worked out from the clock, as away raids are).
- Magnetic termites (Australian outback): HELD.

**2. Fungi and microbes tied to colonies**
- Termitomyces (the termites' garden fungus, comb and mushrooms on the mound): BUILT.
- Xylaria (takes a dead mound's garden, look B "antler forest"): BUILT.
- Escovopsis (garden mould of leafcutters): IDEA (jungle).

**3. Nest guests, parasites and insect enemies of colonies**
- Rove beetles in Matabele nests: PLANNED (no step yet).
- Snake-necked frog (lives in Matabele nests, breeds in pans): PLANNED with the guests.
- Phorid flies (lay eggs in leafcutter heads), guest ants Megalomyrmex, parasite ant Acromyrmex insinuator: IDEA (jungle).
- Seed-stealing guests of the harvester: IDEA (M6.5 backlog).

**4. River animals (savanna rivers, pans, oxbows; food chain algae -> tilapia -> catfish -> crocodile, side chain algae -> snail -> crab)**
- Nile tilapia (grazing schooling fish, 3 looks): BUILT; breeding (nest pit, mouthbrooding, fry) BUILT 2026-11-19.
- River catfish (eats tilapia, bites it in half): BUILT; breeding (rainy-night spawning in the shallows, eggs on the algae, dark young) BUILT 2026-11-19.
- Nile crocodile (basking, ambush, death roll, fishing, bank hauls): BUILT; males and females, courtship (head slaps, the water dancing on his back, the roll), nests in sand banks guarded by the mother, hatchlings carried to the water in her mouth, the crèche, banded young and teens: BUILT 2026-11-20 (animal life cycle step 4, not yet tried in-game); old giants (only 1 or 2 in a whole river system, huge and worn, the river's boss) PLANNED (`docs/plans/animal-life-cycle.md`).
- River crab (eats remains, scraps, algae, snails, and eggs since 2026-11-19): BUILT; breeding (big-clawed males, eggs under the female's flap, babies riding on her back) BUILT 2026-11-19; burrow with shells outside PLANNED.
- River snail (Garden spiral look): BUILT; breeding (clutches at the waterline) BUILT 2026-11-19.
- Helmeted terrapin (tears remains in groups, basks on crocodiles): PLANNED (next after the snail in the clean-up order).
- Nile monitor (eats crabs, snails and remains on the banks, hisses at players, basks; later raids crocodile nests and lays eggs in living termite mounds): BUILT 2026-11-17 (before the crocodile nests, the user's ask).
  - **For later (found by the viewer's Compare, 2026-11-19, the user: "save those for later"):** against its approved outline the 3D monitor covers 91 / 91 / 93 percent of the outline (side / top / front) with 84 / 84 / 82 percent of the model inside it: its tail curves lower than the outline's (side view), its legs splay a little wider than the outline's seen from the front, and its toes and claws stick out (the outline had none, so those are fine). Also 665 boxes, the most of any model (the crab 248, the Matabele 227). Look again with the user's picture in Compare before changing anything. (A first look also blamed a low-hanging belly in the front view: that was the tail lying behind, which the old outline picture had left out; fixed.)
- African tigerfish (fast pack hunter with big teeth, leaps at the surface, even at birds; was held back as a third hunter of the tilapia, now on the user's list; balance it against the catfish and crocodile): IDEA (user's list 2026-11-16).
- Electric catfish (slow, chunky, stuns fish and anything that touches it with a shock): IDEA (user's list 2026-11-16).
- Hippo (floodplain grazing at night, paths, hippo lawns; pods in the deep pools of the near and mid-inland rivers and oxbows, territorial, charging crocodiles that come near): BUILT 2026-11-21, not tried in-game yet (model v8 by Neon, approved; `HippoEntity`: pods in pools, afloat or on the bed by day, grazing on land at night, a warning yawn then a charge and bite, crocodiles keep clear, its own block moves). Pods rework BUILT 2026-11-21 (the user's first look: doubling, in the ground, vanishing, a barricade killing crocodiles; BUGS 271, 272): pods of 1 to 3 (a bull and his cows) laid with new land in the near and mid-inland rivers and kept for good, sharing the bull's pool; only bulls mind crocodiles, with a yawn and a bluff. Calves and breeding, hippo paths and lawns, deep oxbows (the oxbow rework) and resting on banks by day PLANNED.
- Bull shark (*Carcharhinus leucas*, comes up wide rivers from the sea; coast and near-inland deep middle channel, alone and rare; `5.5` blocks base scaling `5.0–7.5` blocks in half-block steps with Look A Clean + Look B Scarred Old Bull, smooth fins, hollow mouth + serrated apex fangs, 12 custom clips): BUILT 2026-11-22 (branch `claude/bull-shark-e387b8`; `animal/BullSharkEntity`, `BullSharkGoals`: patrols the deep channel, hunts at dusk and night with the fin out, swallows barbs and tilapia, shakes catfish apart, breaches, bumps then warns then bites a swimmer, warns off crocodiles, keeps clear of hippos, spy hops; its own spawning group; `docs/HISTORY.md`; model and clips Neon's v14, `docs/handoff/bull_shark.md`; river zones, `docs/DESIGN.md`, "River zones").
- African lungfish (seals itself in a slime cocoon in drying mud, found by digging), annual killifish (eggs wait in dry mud, hatch when it rains): IDEA (mud pits, 2026-11-17; `docs/DESIGN.md`, "Mud pits and wallows").
- African river otter / clawless otter (*Aonyx capensis*, 2.6 blocks long; playful family groups, eats crabs by feeling under stones, fish and frogs; swims, dives, plays in shallows, rests on banks): BUILT 2026-11-22, step 1 (moves, swimming, diving, resting, shaking dry, fleeing, families; `docs/plans/otter-and-bull-shark.md`); hunting, play and the pet PLANNED (workshop `references/workshop/otter/versions.html`).
- River barb (small silver schooling prey fish, shallow water, flashes as it turns, bursts from a strike; food for young crocodiles and catfish; the tilapia cut back to make room): BUILT 2026-11-20 (designed by Claude, `docs/plans/prey-fish-and-frogs.md`); breeding PLANNED.
- Six-banded distichodus (the river's big plant-eating fish: grazes hippo grass mats and young reed shoots, groups of 2 to 4, bright peach-gold with six straight dark bands and red fins, 0.8 blocks; bolts into the reeds, leaps when scared; eaten by grown crocodiles and the bull shark): LOOKS started 2026-11-22 (the user's photo, Claude's design sheet from straight lines, outline waiting for the user's OK; `docs/plans/distichodus.md`).
- African bullfrog and tadpoles (big ambush frog of pans and slow edges, half dug into the mud by day, bites a player who keeps coming, deep calling and wrestling males, the father guarding his tadpole school; tadpoles graze the algae; food for young crocodiles, the monitor and catfish): BUILT 2026-11-20 (the user's pick and picture, `references/inspiration/bullfrog.png`; same plan; was "river frog"); tadpoles and breeding PLANNED. Was on the land list as "Giant African bullfrog".
- Fly clouds (a cloud of 4 to 12 darting, buzzing flies drawn by one mob; bottle fly and house fly looks; food for the bullfrog's tongue, later barbs and young crocodiles; later on remains, dung and termite flights): BUILT 2026-11-20 (the user, "go for it"; `docs/plans/fly-clouds.md`); eaten by the bullfrog's tongue.
- Minecraft's cod, salmon, squid: kept out of savanna rivers (BUILT).

**5. Savanna land animals** (fun first; all IDEA unless marked)
- *5a. Bugs, spiders and other small creatures* (sizes beside the insects to pick by eye):
  - Giant African millipede (dead-wood and leaf-litter eater, curls up when touched): user's list 2026-11-16.
  - Giant African land snail (eats plants and rotting matter): user's list 2026-11-16.
  - King baboon spider (big burrowing tarantula): user's list 2026-11-16.
  - Giant devil's flower mantis (sits on plants, catches flying insects): user's list 2026-11-16.
  - East African lowland honey bee (wild hives in hollow trees, swarms; ties to the honey badger and honeyguide; fun first, not a colony insect): user's list 2026-11-16.
  - African spider wasp (big wasp that paralyses spiders, such as the king baboon spider, and drags them to its burrow; the user meant this, not the American tarantula hawk): user's list 2026-11-16.
  - Savanna whipspider (flat, crab-like, long whip legs, hunts at night under bark and stones; harmless): user's list 2026-11-16.
  - Sun spider (solifuge: fast, huge jaws, night hunter): user's list 2026-11-16.
  - Transvaal thick-tailed scorpion (dry savanna, sprays venom): user's list 2026-11-16.
  - Emperor scorpion (big glossy black scorpion; real home the wetter West African forest and savanna edges, a stretch for dry savanna, fine fun first): user's list 2026-11-16.
  - Antlions (larvae in sand pits that trap ants; a real link to the colony insects, pits in loose sand such as the coming sand banks): user's list 2026-11-16.
  - Dead-wood tenants, centipedes, scorpions.
  - African dung beetle (rolls dung balls away and buries them; an animal-kit proof pick; ties to the big plant eaters' dung and dung spreading acacia seed): user's list 2026-11-18.
  - Tadpole shrimp (eggs wait in dry mud), butterflies sipping at wet mud (mud-puddling, drawn as particles), mud wasps collecting mud balls: IDEA (mud pits, 2026-11-17).
- *5b. Reptiles and amphibians:*
  - African leopard tortoise (*Stigmochelys pardalis*, large dry-savanna grazer `~1.48` blocks at `0.62×`, domed chocolate-and-gold mosaic carapace, curved 3D beak, straight-back Cryptodira retraction with armored forearm shield, sunny basking sploot, 12 clips): LOOKS (ready for Java, `docs/handoff/leopard_tortoise.md`, workshop `references/workshop/leopard_tortoise/versions.html`, 2026-11-22).
  - Black mamba (fast snake, hunted by the secretarybird): user's list 2026-11-16.
  - Giant African bullfrog: moved to the river animals (PLANNED 2026-11-20).
  - Pythons (floodplain).
  - Flap-necked chameleon (slow swaying walk, eyes turning on their own, colour changes, shoots its tongue at insects from bushes and acacias): user's list 2026-11-18.
  - Southern foam-nest tree frog (whips up white foam nests on branches over pans and the river; the tadpoles drop into the water below; a link to the pans): user's list 2026-11-18.
- *5c. Birds:*
  - Sociable weaver (giant shared nests built in trees; in real life often in camel thorns, so ours can use the giant trees): user's list 2026-11-16.
  - Greater honeyguide (leads honey eaters to bee hives): user's list 2026-11-16.
  - Secretarybird (walks the grass, stamps on snakes): user's list 2026-11-16.
  - Ostrich (biggest bird, runs fast, kicks, grazes the open grass; lays huge eggs): user's list 2026-11-16.
  - Red-billed oxpecker (rides rhinos, giraffes, zebras, picking ticks): user's list 2026-11-16.
  - Martial eagle (Africa's biggest eagle; soars high, stoops on monitor lizards, small antelope and game birds; a link to our Nile monitor): user's list 2026-11-18.
  - Birds eating ants and termite flights; floodplain birds: carmine bee-eater (needs steep banks), herons, cranes, jacanas, flamingos, hornbills.
- *5d. Termite eaters:* aardvark (the user's earlier pick as first land animal; burrows reused by Matabele), aardwolf, pangolin (M9 savanna life; wear termite colonies down).
- *5e. Big plant eaters:*
  - African savanna elephant (tree shaper: knocks down and strips trees): user's list 2026-11-16.
  - Giraffe (browses the acacia crowns): user's list 2026-11-16.
  - Black rhinoceros (browser of bushes): user's list 2026-11-16.
  - Plains zebra (grazer, crocodile prey at crossings): user's list 2026-11-16.
  - Warthog (the king of mud wallowing: kneels and slides on its face; rubs on termite mounds after): IDEA (mud pits, 2026-11-17).
  - Antelope, wildebeest, buffalo; floodplain grazers lechwe, puku, waterbuck, sitatunga, bushbuck; monkeys; dung spreading acacia seed.
- *5f. Big hunters and scavengers:*
  - African lion (prides), cheetah (sprints), spotted hyena (clans, scavenge and hunt), African wild dog (packs): user's list 2026-11-16.
  - Honey badger (digs, raids bee hives, fights snakes): user's list 2026-11-16.
  - Leopard (gallery forest).
- *5g. Small mammals:*
  - Meerkat (mobs on dry open ground, a sentinel standing guard, digging burrows; eats scorpions, beetles and termites; real home the dry southern savanna, a stretch here, fine fun first): user's list 2026-11-18.

**6. Desert and jungle animals (their own mods, later)**
- Desert: horned lizard (eats harvester ants), kangaroo rat, antlion, ant-eating spiders: IDEA.
- Jungle: armadillo, giant anteater, tamandua; frogs, birds, lizards eating flying queens: IDEA.

**7. Trees**
- Giant savanna trees, umbrella thorn (N4), camel thorn, baobab, every age, dead stages, falls, heart rot: BUILT (M7.5).
- Floodplain trees, sycamore fig (with fruit) and doum palm (with nuts), every age, dead stages: BUILT in new land; growing and dying in play PLANNED (floodplain trees step 8).
- Fever tree: DROPPED (the user, 2026-11-13).
- Whistling thorn with its ants: IDEA.
- Gallery forest trees (mahogany, ebony, sausage tree): IDEA (big floodplains).

**8. Small plants, grasses and water plants**
- River algae (look C, strands bitten off): BUILT.
- Blue and black grama grass (harvester seed food, plains and badlands): BUILT.
- Dry savanna grass ("Dry Grass" block, termite food, bare patches regrowing): BUILT.
- Papyrus (stands 3 to 5 tall in shallow still water: oxbows and slow river stretches) and hippo grass (a floating fringe along slow banks and oxbows; hippos nibble it): PLANNED (the user, 2026-11-22; `docs/plans/papyrus-and-hippo-grass.md`). Reeds: IDEA.
- Elephant grass (very tall grass, 2 to 4 m, of the wetter savanna and floodplain; cover for animals): IDEA (user's list 2026-11-16).
- Water lilies on lagoons and oxbows: IDEA (big floodplains).
- Desert wildflower, refuse-pile gardens round ant nests: IDEA (custom plants list).

**9. Fungi on wood and ground**
- Bracket fungi of heart rot (knob, young, grown, old, tiers): BUILT.

**10. What life leaves behind (not alive, but part of the food web)**
- Prey pile and termite remains in Matabele nests: BUILT.
- Fish remains (catfish, torn catfish, tilapia, tilapia half; 4 stages): BUILT.
- Scraps film, crocodile droppings, broken snail shells: BUILT.
- Dead wood, pulp heaps, baobab fibre, frass (termite food): BUILT.
- Fallen figs and doum nuts: BUILT.
- Remains of big land animals (our own only, never vanilla mobs): PLANNED with those animals.
- Mud pits and wallows (spawn near water, some made after rain, dry and crack; animals roll in them, mud on their bodies, rubbing posts on trees and termite mounds, life waiting in the mud): IDEA (the user, 2026-11-17; `docs/DESIGN.md`, "Mud pits and wallows").
