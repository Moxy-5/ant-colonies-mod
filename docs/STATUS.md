# Where we are (one page)

Keep this short. The full story is in `docs/HISTORY.md`; the long status is at the bottom of `CLAUDE.md`.

## Finish line
**M8 playable = the mod done for now** (the user, 2026-10-25): Matabele scouts find termites, raid columns go out, fight, and carry prey home. Everything after that is optional.

## Works, confirmed in-game
- **Termites (Macrotermes):** king and queen, workers, soldiers, the cathedral mound, fungus garden and mushrooms, foraging holes, underground galleries, eating dead wood from below, mud sheeting, the alarm.
- **Giant savanna trees:** umbrella thorn, camel thorn, baobab; growing, dying, falling, fire, lightning, dead wood eaten by termites.
- **The colony brain:** colonies keep living while you are away, trails, danger memory, neighbours.
- **Dead termite mounds:** wear down over days, *Xylaria* fungus on the dead garden.
- **Matabele ants:** model, animations, climbing, the queen, the colony living in a dead mound, the prey pile.
- **Fights:** the sting (paralysed termites), termites dodging and hiding, lost legs, termite soldiers clamping on, the Matabele defending their nest against the player (2e), the termites' side of a raid (2f).
- **Dead wood for the raids, parts 1 to 3:** more trees, faster tree lives, a dead tree by every new-land mound.
- **Confirmed by the user 2026-11-03 (in-game test 4):** smarter hunters breaking into galleries (2g) and no corner camping (BUGS 76); jumping on a nest (BUGS 79); termite mounds and Matabele colonies in new savannas (1b, 1f) and young Matabele colours (BUGS 74); the dead tree 10 to 30 blocks out; heart rot, brackets, hollow and snapped trees, pulp heaps; `/antcolonies feedingspots`; the garden fixes (BUGS 70, 71); faster savanna loading (steps 1 to 3) and the two mound fixes (BUGS 81, 82).
- **Red harvester ant:** lives in its own mod (Desert Colonies), waiting for its rework later.

## Left before the finish line
1. **Step 3, scouts and raids:** a scout finds a feeding spot, comes home, leads a column out, the fight, the carry home (real ants only at first). **3a, the scouts, built 2026-11-03** (branch `claude/m8-raids`), not tried in-game yet; **3b, the column, built 2026-11-03** (branch `claude/m8-raid-column-20c0fc`: the scout leads a raid out, it stops short, attacks, comes home empty-handed), not tried in-game yet; **3c, home with the prey, built 2026-11-03** (branch `claude/m8-prey-home-44beec`: the raiders carry the stung termites home in their jaws onto the prey pile, an empty raid looks round first, the hurt limp home), not tried in-game yet; **scouts search all day** (6:00 to 19:30, 1 per 50 workers up to 5; raids still leave in the raid hours, a midday find waits for them), not tried in-game yet; in-game test 5 (2026-11-03) found the raid janky and freezing just out of the nest, so the **raid rework (3R, R1 to R8, planned 2026-11-04)** comes first: a raid diary, a whole-chain test, the march rebuilt to follow the trail, lasting scent trails the player can break, choosier scouts, the mound as a clue, the squeak and the hours; then in-game test 5 again; **R1 (the raid diary) and R2 (the whole-chain test) built 2026-11-04**: the freeze did not happen on flat ground, so the next clue is your own log with the diary on. **R3 (the march rebuilt) built 2026-11-04**, not tried in-game yet. **R4 (lasting scent trails you can break, `/antcolonies raid show`) built 2026-11-04**, not tried in-game yet.
2. **Step 4, drawn-only ants:** big raid columns of 200 to 500 without lag.

## Optional, after the finish line
Savanna loading step 4 (mounds made with the world itself), the test revamp, rescue and wound care, moving house, polish (M8.5), flying queens (M9), the savanna animals (M9.5), more species.

## Open bugs
Only rare test flakes (`docs/BUGS.md`); in play: the raid column freezing just out of a dead mound (in-game test 5, to be fixed in the raid rework).
