# The red harvester ant (archive)

The red harvester ant (*Pogonomyrmex*), its desert mod (`desert\`, mod id `desertcolonies`), grama grass and ant bread were taken out of the mod on 2026-11-23 (the user: it does not fit the savanna). Nothing is lost: the code, models, tools and tests are in git at the tag `harvester-last` (`git checkout harvester-last` shows the mod as it was). What was moved, deleted and kept is in `docs/plans/harvester-removal.md`; the change log of everything built for it stays in `docs/HISTORY.md`, and the shared-system notes in `docs/DESIGN.md` still tell the harvester's part of their story.

Below, word for word, the sections moved out of `docs/DESIGN.md`.

## The species

### Red harvester ant (Pogonomyrmex)
- **Castes:** worker, queen (winged before founding, wingless after), winged males (later). Real harvester ants have no soldier caste; workers defend with stings. Optional later: a larger "defender" worker.
- **Biomes:** plains, sunflower plains, badlands (`#minecraft:is_badlands`). Desert edges allowed but score low (little food).
- **Food:** dropped seed items (wheat, beetroot, melon, pumpkin, torchflower seeds, pitcher pods), seeds from clipping `short_grass`, grama grass seed heads.
- **Farm raids:** taking seeds from fully grown wheat. Config option, default **off**.
- **Cleared circle:** workers remove `short_grass`, `tall_grass`, `fern`, `dead_bush` within a radius of the nest.
- **Special chamber:** seed granary.
- **Founding:** single queen after a nuptial flight.

## Grama grass (from "Custom plants")

- **Grama grass (version 1):** grows in stages to seed heads in plains and badlands. Ants harvest the seeds; the plant drops back a stage and regrows. **Replaced by the decisions below (2026-10-16).**
- **Grama grass, the user's decisions (2026-10-16; pulled forward from M10 and built before the foraging range):** the harvester ants had no rich food worth a long walk (the termites have fallen branches and dead trees), and the next brain step (the range made of trails, recruiting to rich patches, value against distance; see "Brain part 4 decisions") needs one. Real: grama grasses (*Bouteloua*) are staple seeds of *Pogonomyrmex*; a stand in seed is the kind of dense patch where Hölldobler (1976) saw *P. barbatus* and *P. rugosus* recruit nestmates with a poison-gland scent trail.
  - **Two kinds, each in its real place:** **Blue grama** (*B. gracilis*, the grass of the shortgrass prairie: low fine leaves in mats, curved "eyelash" seed heads) in **Plains, Sunflower Plains and Meadow** (it grows up to about 3,000 m in the Rockies; no harvester ants live in Meadow, so there only decoration), as **meadows**, patches several blocks across. **Black grama** (*B. eriopoda*, the Chihuahuan desert grassland, the red harvester ant's own home) in **Badlands, Wooded Badlands and Eroded Badlands**, as **scattered bunches** (wiry, greyish) on red sand, coarse dirt and dirt, often round dead bushes. Not in savanna (Minecraft's is African, the termites' home) or desert (too dry).
  - **Seed after rain, like real grama:** grown plants send up seed stalks after rain, so a whole meadow ripens together: heads ripen over about 2 days, hold seed about 3 more days while shedding, then dry off and go back to leaves. With no rain for about 5 days they seed anyway. A meadow in seed is a sudden jackpot.
  - **The ants take seeds both ways:** they climb and cut seeds off ripe heads, and ripe heads now and then drop a seed on the ground under the plant (kept few, so no lag), which the ants gather like any seed. A head cut bare dries off like a shed one (the plant is never broken by the ants).
  - **The player:** breaking a plant in seed gives that kind's grama seeds, which can be planted (a young plant grows into leaves); bone meal pushes it a stage on. It does not spread by itself. Grama seeds are ant food like other seeds (seed kinds with different values stay in the M6.5 backlog).
  - My picks, to confirm (in the species and block numbers): about 2 days from rain to ripe, 3 days ripe, about 2 days dry stalks before leaves again, seeding anyway after 5 days without rain; **seed balance (the user's decision, 2026-10-17): a tuft of short grass is gone after one take (it grows back), and a ripe grama plant, blue or black, lasts 10 takes** (the plains are full of short grass, so it must not be rich food; the grama meadow is the prize); a young planted plant reaches leaves in about a day; blue grama meadows mostly 5 to 9 blocks across, and about 1 meadow in 8 a big one 15 to 20 across (the user, 2026-10-17: real blue grama covers whole stretches of prairie; the big ones are worth a long trunk trail); black grama bunches loose, a few round a dead bush. **Looks picked by the user (2026-10-16, `references/grama_viewer.html`, drawn by `tools/grama_art.py`): B, "Field detail", for both kinds** (more blades, the comb of spikelets with yellow anthers in flower, the white wool and dark runners of black grama); dried black grama made darker at the user's ask, weathered dark grey-brown, as real old black grama stems are (the "black" in its name).
