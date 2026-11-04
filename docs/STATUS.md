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
- **Red harvester ant:** lives in its own mod (Desert Colonies), waiting for its rework later.

## Built, not tried in-game yet (all in `master`)
- Smarter hunters at a feeding spot: majors break into the galleries they smell, minors go in, smooth searching (step 2g); ants no longer camp a corner (BUGS 76).
- Jumping on a Matabele nest no longer stops the defenders (BUGS 79).
- Termite mounds (sizes and spacing) and Matabele colonies placed in new savannas (steps 1b, 1f); young Matabele no longer purple squares (BUGS 74).
- The dead tree 10 to 30 blocks past the mound's edge.
- Heart rot: wounds, bracket fungi (knob to old, tiers, spore dust), hollow trunks, snapped trees, pulp heaps.
- The feeding spot count, `/antcolonies feedingspots`.
- Fixes: fungus garden crash (BUGS 71), a hole dug into a full garden sealed again (BUGS 70).
- Faster savanna loading: trees remember the ground's height, mounds appear within seconds and quietly (branch `claude/savanna-loading`, not in `master` yet); on the same branch, new-land mounds get their dead tree on uneven ground again (BUGS 81) and every mound stage can be built from inside (BUGS 82).

## Left before the finish line
1. **In-game test 4:** everything in the list above.
2. **Step 3, scouts and raids:** a scout finds a feeding spot, comes home, leads a column out, the fight, the carry home (real ants only at first).
3. **Step 4, drawn-only ants:** big raid columns of 200 to 500 without lag.

## Optional, after the finish line
The test revamp, rescue and wound care, moving house, polish (M8.5), flying queens (M9), the savanna animals (M9.5), more species.

## Open bugs
Only rare test flakes (`docs/BUGS.md`); none seen in play.
