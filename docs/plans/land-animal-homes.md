# Land animals: where they live and how common they are (planned 2026-11-26, nothing built)

The user, 2026-11-26: "a difficult part of the land animals will be where they spawn and how common they each are. It's hard for me to give an image because in real life it's just what feels right. The rivers were easy because they just swim and explore the river." On hearing this plan's idea: "Yes that sounds good, I'm gonna start working on the animals again after I've fixed the code." So this waits until the code review's fixes are done; it is the first thing to read before building any land animal.

Animals are fun first (the `CLAUDE.md` rule): generalized, inspired by real life, numbers my picks, no source checking. Nothing here touches the ants or termites.

## The idea in one line

Every land animal gets **one or two home places** the world already has, **one step on a five-step ladder** of how common it is, and **one way of appearing** (laid with the land, or from Minecraft's spawner). The user judges the result **on a map and in a new world**, never by picturing it: Claude picks from real life, the user says "too many giraffes" or "that part feels empty", the way the river was judged.

## Why the river was easy and land is not

The river came with its own map: one line of water the animals stay in, with places along it that differ (`worldgen/RiverZones`: the coast, near, mid and far inland, the oxbows, the pans), so "where" answered itself. A Minecraft savanna looks the same everywhere: flat grass with trees dotted on it. On land there is no answer until we name the places.

## The places (real life sorts animals mostly by water and cover)

All but the last row already exist in new land, each from the seed and the land's noise alone (like `worldgen/RiverSites`, `PanSites`, `NestSites`), so a map can be drawn without building the land.

| Place | What makes it in our world | How often today | Real life it is inspired by | Who lives there (planned) |
|---|---|---|---|---|
| **Water's edge** | pans (`PanSites`, at least 160 apart, about one per 192 x 192 blocks), rivers and oxbows (`RiverZones`), river mud (about 60 apart along rivers), dry wallows (about 150 apart on open savanna) | everywhere within a walk | most big grazers drink every day or two, so they live within walking reach of water, and hunters stay near it | buffalo (built), lions (built), elephants, zebra and antelope come to drink, warthogs at the wallows |
| **Open grass** | everything that is none of the others: most of a big savanna | most of the ground | short-grass plains: grazers that run, hunters that sprint | zebra, wildebeest, ostrich, cheetah, secretarybird, meerkat |
| **Trees** | giant trees (one per 6 x 6 chunks, about every 96 blocks), young trees (one per 3 x 3 chunks), floodplain figs and doum palm groves | about 15 percent of the ground under a giant's crown, more with the young trees | browsers eat the crowns and bushes; climbers and nesters need branches; hollow trees hold hives | giraffe, black rhino, leopard, chameleon, mantis, sociable weaver nests, the bee hive (heart-rot hollows exist), the martial eagle's perch |
| **Termite mounds** | mounds about every 96 blocks (`MoundPlaces`), two in three alive, dead ones too | everywhere, evenly spread | mounds are meeting places: aardvarks dig into them, pangolins and aardwolves eat their termites, warthogs and hyenas sleep in old aardvark holes, black mambas live in old mounds, cheetahs climb them to look out, monitors lay eggs in them | aardvark, pangolin, aardwolf, warthog dens, hyena dens, black mamba, the cheetah's lookout; ties the land animals straight back to the termites |
| **Dead wood** | fallen trees, fallen branches, dead standing trees (M7.5) | scattered, a little near every giant tree | logs are homes for small hunters and rotting-wood eaters | millipede, scorpions, whipspider, centipedes |
| **Floodplain** | the floodplain band of the big rivers | along every big river | wet grass by the water | lechwe, waterbuck, puku, hippo lawns at night (built), pythons |
| **Moving places** (later) | dung (buffalo, hippo; built), remains of big land animals (planned) | wherever animals go | a kill draws scavengers; dung draws beetles and flies | dung beetle, hyenas to a kill, fly clouds (built) |

**One reader for all of it (my pick, the fewer-rules way):** `worldgen/LandPlaces` answers "what place is this spot?" with one of the rows above, checked in that order (water's edge within 24 blocks of water, a mound within 12, a tree within 16, the floodplain, dead wood within 6, else open grass; my picks). The big animals' homes and the small animals' spawns both ask it, the way every river animal asks `RiverZones`. A home ranges round its place: grazers walk out to graze and back to drink, as the buffalo already do (out to about 24 blocks from their pan).

## How common: one ladder

Lots of small animals, fewer big ones, very few hunters: the real pyramid, used as a feel, not a census.

| Step | Feels like in play | Examples (my picks) |
|---|---|---|
| **Everywhere** | always some near your feet | dung beetles, millipedes, flies |
| **Common** | most walks, often more than one group in view | zebra, wildebeest, one antelope, warthogs, the floodplain grazers on the floodplain |
| **Sometimes** | now and then | giraffe, elephant family, ostrich, buffalo (built), meerkats |
| **Rare** | a lucky find | lion pride (built), black rhino, cheetah, leopard, hyena clan, wild dog pack, aardvark, pangolin, honey badger |
| **One or two in a big area** | a story to tell | the old giants (the crocodile's is planned; an old tusker elephant) |

## Two ways an animal appears (the Minecraft side)

- **Laid with the land, kept for good** (big animals with a herd, pack or territory): put down once when new land is made, never removed, on vanilla `MobCategory.MISC` so the spawner never tries or counts them. Exactly how the buffalo and lions work today, and how vanilla Minecraft itself places cows and sheep (mostly when a chunk is made, kept). Why: a herd is still at "its" pan when you come back; groups have homes you can learn; nothing pops in or out in view. And it works in spectator, where the spawner makes nothing (the user explores in spectator). The cost: only new land gets them, and a group that is hunted down comes back only by breeding, so every hunted animal's plan gives it young faster than the hunters take them (checked in a soak, as the lions and buffalo calves are).
- **From the spawner, kept near the player** (small life: bugs, spiders, scorpions, chameleons): Minecraft keeps topping them up round the player and removes them far away, like the river groups (kept within 96, gone past 128). Every such kind follows the spawning check list in `docs/LIVING-THINGS.md` (its own group, one random height, the quiet-savanna gate, the world's own checks in a test), and its spawns ask `LandPlaces` for their share, as the river's ask `RiverZones`.

## The game balancer's check

- **How far you see an animal:** Minecraft draws a mob out to about 64 blocks times its box's average side (at the default entity distance), cut at its tracking range (10 chunks, 160 blocks, for the buffalo and lions). So a buffalo (box 1.6 x 1.9) shows to about 110 blocks; a giraffe or elephant, with a much taller box, to the full 160. Big animals feel more common than their spacing says.
- **The big-animal budget (my pick):** standing anywhere in a big savanna, you usually see **3 to 5 groups of big animals** at once. That is about **one group's home per 110 x 110 blocks** in all, every kind together. Each new kind takes a share of that budget by its step and its places (weights: common 10, sometimes 4, rare 1; my picks), so adding zebra never doubles the animals in view: it shares the same sites.
- **No zoo at the pans:** homes keep at least 40 blocks apart (my pick), so a pan holds its buffalo herd and its sometimes-lions, and other kinds' homes sit further out and walk in to drink. Different kinds drink at different times (elephants midday, grazers morning and afternoon, hunters at dusk), each in its own animal's plan.
- **Hunters and prey:** with those weights about 1 new home in 20 is a hunter's (cheetahs, leopards, hyenas, wild dogs together), beside the lions at one pan in 3, and lions already hunt buffalo. Each hunter's plan checks prey per hunt against the prey's young, in a soak.
- **The server:** at the default 12 chunks of simulation, about 13 big-animal homes tick round the player (about 50 animals; my rough sum) beside the buffalo and lions; a stress test measures it when the first new big kind is built (budget: a few milliseconds of the tick's 50).
- **Against the other spacings:** pans 160+ apart, mounds about 96, giant trees about 96, wallows about 150, crocodile nest beaches 250 to 350 along rivers, hippo pods about 250 along rivers. A home every 110 blocks fits between them; it is a little denser than the mounds, so most mounds have a big animal's home within a short walk, and most homes are within about 135 blocks of a pan.

## The planned land animals' rows (the census; all my picks, all to be judged on the map)

Big animals (laid with the land):

| Animal | Places | Group | Step |
|---|---|---|---|
| Cape buffalo (BUILT) | pans | 3 to 5 and calves | sometimes (one herd per pan, kept as built) |
| African lion (BUILT) | pans, tree shade | a pride | rare (one pan in 3, kept as built) |
| Plains zebra | open grass, water's edge | 5 to 10 | common |
| Wildebeest | open grass (often beside zebra: real ones graze together) | 6 to 12 | common |
| One antelope (generalized) | tree edges, open grass | 5 to 10 | common |
| Warthog | open grass, mounds, wallows | a sow and 2 to 4 young | common |
| Floodplain grazers (lechwe, waterbuck) | floodplain only | 4 to 10 | common there |
| Giraffe | trees | 3 to 6 | sometimes |
| Elephant | water's edge, trees | a family of 4 to 8 | sometimes |
| Ostrich | open grass | 1 to 5 | sometimes |
| Meerkat | open grass (a burrow laid with them) | a mob of 5 to 12 | sometimes |
| Monkeys | trees near water | a troop | sometimes |
| Leopard tortoise (LOOKS) | open grass | 1 | sometimes |
| Black rhino | trees | 1, or a mother and calf | rare |
| Cheetah | open grass, mound lookouts | 1 to 3 | rare |
| Leopard | giant trees | 1 | rare |
| Spotted hyena | a den in an old burrow near a mound | a clan of 4 to 8 | rare |
| African wild dog | ranges far wider than the others | a pack of 6 to 10 | rare |
| Aardvark | living mounds (digs at a different one each night) | 1 | rare |
| Pangolin, aardwolf | mounds | 1 | rare |
| Honey badger | anywhere, bee hives | 1 | rare |
| Black mamba, python | old mounds and dead wood; the floodplain | 1 | rare |
| Secretarybird, martial eagle | open grass; giant tree perches | 1 or 2 | rare |
| Old tusker elephant | water's edge | 1 | one or two in a big area |

Laid with their place, not with a home of their own: sociable weaver nests (giant camel thorns), the bee hive (heart-rot hollows), the king baboon spider's burrow (open ground). Riding on others: oxpeckers (on buffalo, giraffe, zebra, rhino).

Small life (from the spawner, kept near the player):

| Animal | Places | Step |
|---|---|---|
| Millipede, giant land snail | dead wood, trees; more after rain | everywhere |
| Dung beetle | at dung | everywhere near dung |
| Whipspider, centipedes | dead wood, at night | common |
| Antlions | loose sand (sand banks) | common in sand |
| Scorpions (thick-tailed; emperor rarer, near the floodplain), sun spider | open grass and dead wood, at night | sometimes |
| Chameleon, flower mantis | young trees and bushes | sometimes |
| Foam-nest tree frog | branches over pans, in rain | sometimes at pans |
| Spider wasp | open ground near spider burrows | rare |
| Fly clouds (BUILT) | water, remains, dung | as built |

## Steps

1. **The census and the map** (nothing in the game changes; the user judges every planned animal before any is built):
   - The census as a data file in the savanna mod (one row an animal: places, group size, step, way), so numbers change without code. A row for an animal not built yet lays nothing.
   - `worldgen/LandPlaces`, the one place reader.
   - A game test (category `worldgen`, kept out of full runs, as the biome maps in `SavannaWorldTests` are a tool) that asks the real site rules for a 1,024 x 1,024 area of 3 or 4 seeds and writes what it finds: the ground and biome, rivers and floodplain, pans, oxbows, wallows, mounds, trees, and every animal's home as the game's own rules would place it. Where a rule can only be answered by building the land, the test builds those chunks (slower; I am not sure yet which rules need it).
   - The page `references/land_animals_map.html` from the shared viewer script (`tools/viewer_page.py`), checked with `tools/viewer_shot.py` before the user sees it: the map, one colour a kind (built ones solid, planned ones hollow), a legend with each kind's count per 1,000 x 1,000 blocks, a switch per kind, and a click anywhere drawing the circle you would see from that spot. The game's rules place the dots; the page only draws them (one judge, never a copy of the rules in the page).
   - The user looks and says what feels wrong; Claude changes the census and runs it again.
2. **The big animals' homes in the game**, built with the first new big animal: one grid of home sites over new land (one per about 110 x 110, at least 40 apart), each site's kind picked by weight from what `LandPlaces` says it is; the buffalo and lions keep their pan rules (approved in play) and their sites count in the budget. A survey test counts homes per kind on the map's seeds against the census; a stress test measures the ticking animals. Each later big animal only adds its row.
3. **The small life's spawning groups**, built with the first small animal: its own groups and `LandPlaces` shares, by the spawning check list.
4. **The moving places**, with the dung beetle and the hyena: dung and remains as spots the small life and scavengers come to.

## Ideas not on the master list (the user decides; not in the steps)

- Dwarf mongooses living in termite mounds (real, and a strong mound link).
- Vultures circling over a kill, so a player can find it from afar.
- The midday watering-hole time, every kind walking in to drink at its own hour (real, and a sight worth stopping for).
