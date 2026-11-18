# Animal life cycle: males, females, mating, eggs and young (M9, planned 2026-11-18, nothing built)

The user, 2026-11-18: "round up the ecosystem and add a system to make it easier for future animals; start with the mating and egg laying, female and male variants, planned for each animal."

Animals are fun first, inspired by real life (rule in `CLAUDE.md`): real behaviours are used where they make the game better, the numbers are my picks, nothing checked against sources. The one exception is the Nile monitor's eggs in a living termite mound, which touches the termites: the termites' side of it stays real (they seal a breach, as they already do).

## What is there today

- Six river animals, each written on its own (`savanna/.../animal/`): tilapia, catfish, crocodile, crab, snail, monitor.
- Every one rolls its own size (`ownSize`, 0.8 to 1.15) and saves it itself; only the tilapia has sexes (`male`), young (`GROW_UP_TICKS`, 3 days, born at half size) and a male look (breeding colours). Nobody mates or lays.
- All of them are kept by Minecraft's spawner: kept within 96 blocks of a player, gone past 128, a cap per spawning group (table in `CLAUDE.md`). Nothing lives while away; new land and the spawner refill.
- No shared "what season is it" source exists yet, though the rule in `CLAUDE.md` says everything that changes by season must ask one.

## The shared system: the animal life kit (framework, species-neutral)

Written once in the framework (`com.antcolonies.animal`), so the desert and jungle mods' animals use it too. Each animal says what it does in a data file, as the colony species do; only truly special behaviour (a mouth full of fry, a mother carrying hatchlings) is code, plugged in through a few hooks. Pieces:

1. **Life (`AnimalLife`)**: one small part every animal holds: sex (male, female, or none for animals that are both), stage (young, grown), age, its own size, and whether it is ready to breed; saved and synced once, for every animal. Replaces the six copies of the size code and the tilapia's own young code.
2. **The life file (`data/<mod>/animal_life/<animal>.json`)**: numbers the user can change without code: grown size of males and females, days to grow up, size at hatching, how often it can breed, clutch size, days to hatch, breeding season and weather, how near a mate must be, the local limit, and the kind of care (`none`, `guard`, `carry_young`, `carry_eggs`).
3. **Room to breed (one rule, the game-balancer guard)**: an animal breeds only while fewer than its local limit of its own kind live within 48 blocks **and** its spawning group is under its cap. So breeding can only fill a river up to what the spawner already allows; it can never boom and lag. Young count in their parents' spawning group like any member.
4. **Finding a mate (`SeekMateGoal`)**: a ready animal walks or swims to the nearest ready one of the other sex within reach, through its own legs, then plays its courtship (a clip and a time from the life file, or a species hook for the special ones).
5. **Clutches (`EggClutchBlock` base)**: eggs are a block (cheap, saved with the chunk, nothing ticking): the time laid, the count, the mother's kind. They hatch from the game time when their land is loaded **and** a player is near, through room-to-breed (no young appear in empty land); a clutch left more than 10 game days unwatched has already hatched and gone, leaving empty shells that crumble. Each animal brings its own egg look.
6. **Care hooks**: guard a place (stay near, see off what comes close), carry young or eggs (the young ride along and are let out again). The tilapia's mouth, the crab's egg flap and the crocodile's jaws all use the one carrying hook with their own looks.
7. **The season (shared, framework)**: a new `Seasons` source with a fixed default answer ("the rains", so nothing waits) until Serene Seasons comes in M9.5. Breeding asks it (and the rain) instead of guessing the time of year itself.

A future animal then needs: a life file, its egg look (or none), its male and female looks, and its courtship clip. Its spawn eggs and summons get `{sex:"female",young:true}` for testing.

**Spawning check list still applies** (the rule in `CLAUDE.md`): hatching is not the world's spawner, so each animal's spawning test also counts that young fit the group and the local limit holds.

## Each animal (my picks, fun first)

### Nile tilapia
- **Males and females:** already there. Grown males wear breeding colours (look C) only while they hold a nest; other males and all females wear the everyday look A (changes today's "1 male in 3 for life").
- **Mating:** a male digs a round **nest pit** in a shallow silt or sand bed (a saucer a block across, a new bed look, fading in 3 days), wears his breeding colours and chases other males off it. A ready female visits, they circle over the pit, she lays.
- **Eggs and young:** she takes the eggs into her **mouth** (mouthbrooding, the real tilapia's best trick): her throat swells, she does not eat for a day, then she lets out 4 to 6 tiny fry that stay round her head and **dart back into her mouth** when a catfish, crocodile or player comes near (the carrying hook). After 2 days they school on their own as today's young (look B), growing up in 3.
- **Food chain:** fry are catfish and crab food.

### River catfish
- **Males and females:** females a little bigger (sizes from the life file); a female ready to spawn is plump (a rounder belly, the same model swelled a little). No colour difference.
- **Mating:** after rain, at night, the catfish of a stretch gather in the **flooded shallows** and chase in pairs, the male curling round the female's tail (a new clip), splashing at the surface.
- **Eggs and young:** she scatters **sticky eggs on the algae** (an egg-speckled look on the algae mat, the clutch block); no care, as real catfish. They hatch in a day into tiny dark catfish young that hide in the shallows, growing to the full fish in 4 days.
- **Food chain:** tilapia and crabs eat the eggs off the algae (a strand bite with an egg puff), so few hatch.

### Nile crocodile
- **Males and females:** males are the big ones (0.95 to 1.15 of 7.5 blocks), females smaller (0.7 to 0.85). Same look, the size tells them apart.
- **Mating:** in the water, a male raises his head and tail, **head-slaps** the surface with a bang and makes the water **dance on his back** (droplets jumping off his scutes from his rumble). Males that meet push and gape at each other; the smaller backs off. Mating is in the water, the pair rolling at the surface.
- **Nest:** the female digs a pit in a **sand bank** above the waterline (the dug-open nest look from the sand viewer, then the covered heap), lays 6 to 10 eggs (fewer than real 40, for balance) and **guards it**, basking beside it and hissing at, lunging at and chasing off anything that comes near: players, monitors.
- **Hatching:** after 4 game days the eggs chirp, she digs them open, **carries the hatchlings to the water in her mouth** a few at a time (the carrying hook), and keeps them in a **crèche** in the shallows for 2 days, coming at anything that hurts one.
- **Young:** small (about 0.5 blocks), boldly banded (new look), eat tiny fish, crabs' leftovers and insects at the surface; catfish, monitors and adult crocodiles eat them. They grow to the smallest grown size in 6 game days.

### River crab
- **Males and females:** **males have big claws** (both bigger, the right one much bigger), **females small claws and a broad belly flap**. One model, the claw bones scaled.
- **Mating:** the male holds the female face to face for a while with his claws (a short clip).
- **Eggs and young:** she carries an **orange egg clutch under her flap** (shown under her body, the carrying hook) for 2 days, then lets out 4 to 8 **tiny crabs** that ride on her for a moment and scatter. No larvae, as real freshwater crabs. Babies grow up in 3 days.
- **Later:** with the burrow (planned), she broods inside it and the babies come out of the hole.

### River snail
- **No males or females:** every snail is both (my pick, as many snails): any two ready snails meet, touch and both lay.
- **Eggs:** a clutch of **pearly eggs** stuck on a stone, a pebble or the bank just at the waterline (a small block on the side of a block, the clutch block). Hatch in 2 days into tiny snails. Crabs and tilapia nibble the clutches.

### Nile monitor
- **Males and females:** males bigger (0.95 to 1.15) with a heavier head, females smaller (0.75 to 0.9).
- **Mating:** two males that meet **wrestle standing up**: they rear on their hind legs, chest to chest, and grapple until one is pushed over and runs (a two-animal clip, the monitor's showpiece). The winner courts a female with tongue flicks and nudges.
- **Eggs in a living termite mound:** a female ready to lay digs a hole into the side of a **living Macrotermes mound** within 64 blocks, lays, and leaves; the **termites seal the hole** as they repair any breach (their own behaviour, real: real Nile monitors lay in termite mounds, and the termites close the eggs in). The clutch is kept inside the mound's wall. After 6 game days the hatchlings **dig out** (a small breach the termites seal again). With no mound in reach she lays in a sand bank like the crocodile.
- **Nest raider:** monitors dig up a crocodile's nest while the mother is away or in the water, eat eggs, and flee from her when she comes.
- **Young:** small, bright yellow-banded (new look), eat snails, crab babies and insects, grow up in 6 days.

## Growth stages and old giants (the user, 2026-11-18, after seeing step 0: "they all look perfect")

The user, with photos of a huge old Nile crocodile: a **teenage stage** for the monitor, the crocodile and future bigger animals, and **old crocodiles**, bigger and worn. "I honestly don't think meters transfer well into blocks ... in person is a lot crazier."

- **Stages in the kit:** `young`, `teen`, `grown`, and `old` for the animals whose life file has one. `AnimalLife` keeps the stage; the life file gives each stage's size and days. Small animals (tilapia, crab, snail) skip teen; their young grow straight up as today.
- **Teens (my picks):** crocodile 2.5 to 4 blocks, the hatchling bands fading into the adult olive, slim, keeps to the edges and the crèche's old shallows, hunts fish but never big prey or players; grows up in about 8 game days. Monitor 1.8 to 2.8 blocks, the bright yellow bands dulling toward the adult's, shy of players, hunts snails and crab babies; about 6 days. Catfish stay young -> grown (a fish's teen looks the same as the grown fish).
- **Why the photos look so much bigger than our crocodile (my read):** a real giant grows **bulk far more than length**. A 5 m old male is as long as a 4 m one plus a bit, but his head is twice as broad, his neck and body much thicker and taller, his tail base heavier. Our 7.5-block crocodile is already about 1.5 times a real one's length (a block a metre), so making it longer still would only make a longer animal; making it heavier is what reads as "a lot crazier" in person.
- **Old crocodiles (my picks, fun first):** **very rare (the user, 2026-11-18): usually only 1 or 2 in a whole river system** (my pick for how, in its step: a crocodile turns old only at a small chance and only with no other old giant within about 1,000 blocks, kept in a saved world list so it holds while land unloads); always a male; **10 to 11 blocks long** (1.4 times a grown male) but **1.6 to 1.8 times as wide and tall**, so it towers over a player crouched beside it as in the photos. Its own model from the crocodile's, not a scaled copy: a broad heavy head with knobbly lumps on the snout and over the eyes, big uneven teeth hanging over the lips (some broken or missing), heavy jowls and a thick neck, scutes worn blunt and flatter, the grown crocodile's own colours only a very little lighter (the user, "very slightly") with pale old scars, a nicked tail tip or a missing toe, a duller eye. In play: the river's boss, slower but its bite and death roll much stronger, other crocodiles give way to it at a bask or a kill, it takes the best sand bank, mates first. It counts in the crocodile's spawning group (5 round a player) like any crocodile.
- **To check (the game balancer):** an 11-block crocodile in the narrow far-inland rivers (a median 23 wide) is about half the river's width: fine for a rare one; its box and jaws follow its size number already (one size number since 2026-11-17); a far model may be needed, as the monitor's 650 boxes.
- **Looks first:** the crocodile and monitor teens and the old crocodile go into the life viewer (step 0b) for the user's look before step 1.
- **The old giant's clips checked (2026-11-18, the user's ask):** all 14 crocodile game clips played on the old giant in the crocodile clip player (`python tools/model_viewer.py crocclips`, `references/croc_clips_viewer.html`) and measured: they all fit its bulkier body, so the giant uses the same clip file as every crocodile (no clips of its own). Ground contact as the grown male's, feet dip at most 0.44 model pixels mid-stride, no tooth through the shut jaw; afloat its back shows a little less (3.9 pixels against 5.2), lying lower and heavy, kept. For step 4: an old giant's slower pace is its walking speed, its float depth the crocodile's one size number.

## Steps

0. **Looks in the model viewer, the user picks** (DONE 2026-11-18, all confirmed by the user, "they all look perfect"): each animal's male and female side by side, young and eggs (tilapia nest pit and swollen throat; catfish plump female, eggs on algae, young; crocodile hatchling, nest heap; crab big-claw male, flap female with eggs, babies; snail egg clutch, babies; monitor hatchling, egg chamber in a mound cut open).
0b. **Teens and the old crocodile in the viewer** (crocodile teen, monitor teen, the old crocodile beside a grown male and a player). **DONE 2026-11-18, confirmed by the user ("everything looks good"); its clips checked the same day, all fit** (`python tools/model_viewer.py life`, "Old giant"; teens in the Crocodile and Monitor groups).
1. **The kit:** `AnimalLife`, the life files, room to breed, the clutch block base, `SeekMateGoal`, the care hooks, `Seasons`; the six animals moved onto it with **nothing changing in-game** (sizes and the tilapia's young kept); new test category `life`. **BUILT 2026-11-18** (framework `com.antcolonies.animal`, `com.antcolonies.season.Seasons`; life files `data/antcolonies/animal_life/*.json`, all `"breeds": false`; tests `AnimalLifeTests`, `LifeRulesTest`).
2. **Tilapia and snail** (one hard, one simple): nest pit, mouthbrooding; snail clutches. Tests: a pair breeds, young appear, room to breed stops at the limit.
3. **Crab and catfish:** claws, egg flap, babies; rain spawning, eggs on the algae, eaten eggs.
4. **Crocodile:** males and females, head slap and back dance, nest, guarding, hatching, carrying, crèche, young.
5. **Monitor:** wrestling, eggs in a termite mound (with the `mound` tests: the termites seal it, the mound still grows right), crocodile nest raids, young.
6. **Balance soak:** a river watched 6 game days, counts of each kind per day must level off, never boom or crash (`tools/soak.sh` with river counts added).
7. **Full run, the user's in-game test** (a new world).

Full run after step 3 and step 6 (the every-3-steps rule).

## My picks (code, the user may skip)
- The kit in the framework, not the savanna mod: the desert and jungle animals will need it, and the framework may not name species, so it stays neutral.
- Eggs as blocks, not mobs: blocks cost nothing while waiting and are saved with the land; mobs would be removed past 128 blocks.
- One "carrying" hook for mouths, flaps and jaws: three animals, one rule.
- Numbers in data files, as the colony species do.

## Your calls
- **Players breeding animals** (feeding them like cows): not planned; wild breeding only. A big gameplay change, so the user's call if wanted later.
- **The monitor laying in a living termite mound** touches the termites (a breach they seal). Already in the design notes; it adds a stranger to the mound, nothing else changes for the colony.
- **The tilapia's breeding colours** move from "1 male in 3 for life" to "while he holds a nest", so fewer red-throated males will be seen at once.
- **Time:** step 0's looks are the slow part (six animals, about 15 new looks); the kit (step 1) changes nothing in-game and needs no in-game test of its own.
