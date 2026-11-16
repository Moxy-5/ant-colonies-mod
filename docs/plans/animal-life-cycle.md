# The animal life cycle: babies, births and eggs (M9, the user, 2026-11-16; step 1 built)

The user: "something really important for the savannah in general will be giving birth or laying eggs that hatch and
making baby variants of each model, how can we make that process easier?" Picked by the user 2026-11-16: **babies are
the grown model reshaped** (bigger head, shorter legs and snout), not a separate baby model per animal.

Animals are fun first (the rule of 2026-11-14): real habits are used where they make the game better, no source checks.
The game-balancer rule still holds: births must never let a river fill up or lag.

## Why
- Every animal coming (hippos, bull sharks, the monitor, the terrapin, and more) needs young, and so do the five
  already built. Done one animal at a time, each would need its own ageing code, its own baby model and its own clips.
- Births make the savanna feel alive (a crocodile guarding her nest on a sand bank, a hippo calf at its mother's side)
  and link animals to each other (a monitor raiding crocodile eggs, a catfish taking hatchlings).

## How it works today (read in the code, 2026-11-16)
- Only the tilapia has young (`animal/TilapiaEntity`): its own age counter, half size at hatching growing to full over
  3 game days through Minecraft's size setting (`Attributes.SCALE`), a pale barred young texture, the age kept in a
  bucket. All of it lives in the tilapia's own class.
- The crocodile, catfish, crab and snail have no young. The crocodile has a size per animal (`ownSize`) but no age.
- The animals stand on different Minecraft bases (the tilapia on Minecraft's schooling fish, the crocodile on a plain
  mob), so a shared "animal" base class cannot hold the kit.
- The models already move bones by code each frame (`client/CrocodileModel`, `CrabModel`: the eyes, the ground bend,
  the planted feet), so reshaping bones by code is a tool already in use.

## The kit (my picks; one place for each rule)
**A. Growing up (the framework, species-neutral, so desert and jungle animals get it free).** A small helper each
animal holds (not a base class): its age in ticks, saved; how grown it is, 0 at birth to 1 grown; days to grow and size
at birth read from the animal's data. It sets the animal's size as the tilapia does now, and kept in a bucket. What a
young animal may do follows how grown it is (my pick: a young crocodile hunts only fish, never drags a cow; a young one's
bite is weaker), read by each animal's own goals from one number.

**B. The young shape (the user's pick).** Each animal has a young shape: how much bigger or smaller each bone is at
birth (for example head 1.4, eyes 1.5, snout 0.7, legs 0.75), in one data file per animal that the game and the model
viewer both read. A shared model step blends it to the grown shape by how grown the animal is, so a baby reshapes little
by little as it grows, never popping. Every clip is reused, because the bones are the same. A young texture is optional
(one line in the data; the tilapia's pale barred young and a banded crocodile hatchling keep theirs). A separate baby
model stays possible for an animal whose young truly look different, never the default.
- Known to check while building: a bone grown bigger grows its children too (Minecraft's models nest bones), so the
  table is written with that in mind; the crocodile's planted feet and ground bend read leg lengths and must read the
  reshaped ones.

**C. Births and the brake.** Three ways to be born, picked in the data: **live** (a calf or pup beside the mother),
**eggs** (a clutch, D), **spawn** (fry appearing in the shallows near the parent). The brake, one rule for every animal:
a birth happens only when the parent has eaten lately and fewer than a set number of its kind live within a set reach
(numbers per animal, my picks when each is built), and only in land where the animal itself lives (loaded, near a
player). A lone grown animal can give birth; no mate is needed (fewer rules; a known simplification, fun first).

**D. Eggs.** One shared egg clutch block: an egg look per animal (picked in the model viewer), a hatch time in the data,
dug into sand or silt (the crocodile's sand banks when they come, the silt bars until then), broken by a player or eaten
by a nest raider later (the Nile monitor). Hatchlings come out at the young shape's birth size.

**E. Parent care.** Each animal's own behaviour on top of the kit, through small sockets: guard the nest, young follow
the mother, a parent carries its young (the crocodile mother carrying hatchlings to the water in her mouth, already in
`docs/DESIGN.md`; tilapia mouthbrooding, the mother keeping her fry in her mouth and the fry darting back in at danger,
an option).

**F. The age slider in the model viewer.** Any animal's model shown from birth to grown with a slider and a line-up of
ages beside a player, like the trees' age line-up, reading the same young shape file the game reads. Looks are picked
there before they go in the game.

## Steps (a full run every 3 steps and before the merge)
1. **The growing-up helper in the framework, the tilapia moved onto it.** Nothing changes in-game (the tilapia grows
   exactly as now); its tests keep passing. Proves the kit on an animal that already has young.
   **BUILT 2026-11-16** (branch `claude/animal-life-cycle`): `framework/.../entity/life/GrowingUp` (a `Plan` of grow-up
   ticks and birth size; age, `grown()`, `size()`, `tick()` asking for a refresh every 200 ticks and on growing up,
   save and load under the animal's own name, `applySize` through Minecraft's size setting); the tilapia holds one
   (`TilapiaEntity.GROWTH`), its save name `TilapiaAge` kept; the plan's numbers stay in code until step 2 brings the
   animal's data file with the young shape (my pick); unit test `GrowingUpTest`.
2. **The young shape and the age slider.** The data file, the shared model step, the viewer's slider; the first young
   shape is the crocodile hatchling (the user's look in the viewer), then the others.
3. **Births and the brake.** Live and spawn births; tilapia fry first; a soak-style count test that every animal's
   numbers level off and never climb (the game-balancer check).
4. **Eggs and the crocodile's nest.** The clutch block, the mother digging and guarding a nest, hatchlings carried to
   the water.
5. **Young for every animal built so far** (my order): crocodile (eggs, step 4), catfish (spawn), crab (eggs carried
   under the female, then tiny crabs), snail (egg clutches on the bed). New animals (hippo calves at the mother's side;
   bull shark pups born live in the big river near the coast, as real ones are) come with young from the start.
6. **The check:** full run, loading and tick speed with young about (more animals in the world), the user's look in
   a new world.

## Your calls
- **Can the player breed animals by feeding them?** My pick: no for now, wild births only (so rare animals like the
  bull shark cannot be farmed, and the brake stays the only thing setting numbers). Easy to add later per animal.
- No other calls: the rest is code or my picks, listed in the report of each step.

## Not in this plan
- The habitat grid along and across the river (the user liked it, 2026-11-16; later, with the territorial predators).
- Sand banks, river plants, hippos, bull sharks: their own steps; they use this kit when built.
- Young for the ants and termites: they already have their own brood (eggs, larvae, nymphs) in the colony brain.
