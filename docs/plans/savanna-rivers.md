# Savanna rivers: floodplains, pools and murky water (planned with the user 2026-11-07)

Branch `claude/savanna-rivers-a9c96a`, from `claude/flat-savannas` (145390fb). Nothing built yet; build only on the user's go.

## What the user asked for
- "in those videos the rivers are still on a slope": after the flat savannas the plain is flat, but every river sits at the bottom of a sloped valley that drops several blocks to the water.
- The user's picture (2026-11-07): "a river right up to the savannah ... like the nile river ... no drop off at all to the river it just runs through the savanna" (later wildlife and ambience by the water; easier to build on).
- Floodplain pools, real shapes, "those sound really cool yea add them".
- "the water in these floodplains had a different texture probably greener ... murky".

## The real thing
- Big savanna rivers (Nile, Okavango, Zambezi, Luangwa) run through a wide, flat **floodplain**: the land a metre or two above the dry-season water, grass and reeds to the edge, sandy bars and beds. Steep-banked gullies (dongas) are the small seasonal streams, not the big rivers.
- Floodplains hold two kinds of still water: **oxbows**, a cut-off river bend left as a crescent lake, and **pans**, shallow round hollows that fill with rain and flood water, kept bare and muddy by animals drinking and wallowing. Many pans dry out in the dry season.
- The water is **murky**: rivers carry silt (olive-brown to brown-green), pans and oxbows grow algae (greener), and you see only a little way into either.
- Macrotermes need well-drained ground: floodplains that flood hold few mounds (the ones there stand on raised ground, "termite islands").

## Why the slope is there today
- Minecraft lowers the land toward a river by the "peaks and valleys" number (`ridges_folded`): near 0 the terrain splines pull `offset` (the land's height) down to the valley floor, across a band about 20 to 40 blocks each side. The river biome itself is only the bottom of that band.
- The flat savannas lift the plain about 4 blocks (`offset` +0.03 inside the savanna mask, so flat ground just above the sea did not fill every dip with water). The lift reaches the river too: the drop is deeper and the river bed is 4 blocks higher than Minecraft's (some savanna rivers may run shallow or broken; step 1 measures it).

## The plan (my picks marked; the user's calls already taken above)
**Step 0, the water colours, picked in the model viewer.** Two or three looks side by side, beside plain Minecraft water and swamp water for scale: the river silty olive-brown, the pools greener with algae (my pick: the river and the pools differ a little, as in real life). The user picks; nothing in the game until then.

**Step 1, measure the river edges.** A river-edge measure in the `worldgen` tests (`BiomeMaps`): many crossings of savanna rivers, the ground's height against the distance from the water, the river's depth. Gives today's numbers, then guards the new ones.

**Step 2, the floodplain.** Inside the savanna mask, near rivers (peaks and valleys near its valley end), in data like the flat savannas (a new generator in `tools/flat_savannas/`, the JSON never hand-edited):
- the plain's 4-block lift fades out toward the river, and the land eases down so gently it reads as flat (my pick: about 1 block per 15 to 20 blocks), ending **1 block above the water** at the edge: no bank, no drop;
- the small lumps (`sloped_cheese`) kept smaller still on the floodplain, so no dip of it falls below the water line (no stray puddles: the old problem);
- the river bed keeps Minecraft's depth (the lift no longer raises it).
- Checks: the bank at the water 0 to 1 block high on nearly every crossing; floodplain spots under water rare; `savannaGroundIsGentle` still passes.
- Biomes do not move: they are picked by erosion and peaks and valleys, which this step leaves alone (it changes only the height). Rivers keep their paths and the river biome its width; the water may spread a little wider where the land is lowest.

**Step 3, a savanna river and murky water.**
- A new biome, `antcolonies:savanna_river`, takes the river's place in the savanna's climate in our TerraBlender region: the colour picked in step 0, murky underwater (tag `has_closer_water_fog`, as swamps), sand on the bed and at the water's edge (my pick; real sandy beds and bars), Minecraft's river plants and spawns copied, in `#is_river` so other mods' river things still come.
- The savanna biomes' own water (the pools, any pond) the greener colour from step 0, by a biome modifier in code, as `RainModifier` does.
- Known gap: the few small savannas Minecraft's own regions still make keep plain rivers (their river is Minecraft's biome); single-biome savanna worlds have no river biome, their water is savanna water, so murky green.

**Step 4, the pools.** Placed on purpose, like fallen trees (not dips left in the land), only on the floodplain (savanna ground within about 2 blocks of the water line, flat):
- **oxbows**: crescents 10 to 30 blocks long, 3 to 5 wide, 1 block deep, 2 in the middle, a little mud or sand at the ends; rare, about one per few hundred blocks of river (my picks);
- **pans**: rough ovals 5 to 12 blocks across, 1 deep, a thin ring of mud (vanilla mud block) round the water; a few scattered (my picks);
- never cut into a termite mound or a dead mound (a pool skips a spot with mound blocks near);
- full all year for now; drying and refilling with the seasons later, from the one shared season source (M9's Serene Seasons steps).

**Step 5, mounds and the survey.** Mounds already lose 60 percent of their chance with water within 14 blocks (`water_cut`, `water_look`). New, my pick from the real termites: no mound on the floodplain's lowest ground (within about 2 blocks of the water line), only on ground that drains. Then the mound survey again on the new land: dead mounds seen and Matabele colonies must stay in the bands `MatabeleNewLandTests` checks (Matabele 18 to 35 percent; last survey: 1 mound in 3.0 looks dead, 1 in 3.5 Matabele); retune `dead_share` if they drift.

**Step 6, checks.** `./gradlew build`; `ANTCOLONIES_TESTS=worldgen,mound,colony,raids tools/run_game_tests.sh 6`; a full run before any merge into `master` (the every-3-steps rule); river maps in `references/`; the user's in-game test in a new world (old worlds get a step where new land meets old).

## What it touches, for the game balancer
- Termite feeding spots, scout reach and mound spacing: the plain stays flat, so the numbers checked in `tools/raid_balance.py` should not move; the survey in step 5 confirms.
- Raids and foraging near water: tunnels already never dig into a block touching water (`GroundReader.touchesFluid`), so a floodplain gallery cannot flood.
- Wildlife later (M9.5): the flat banks and the pans are where savanna animals drink and wallow.

## The user's picks in the colour viewer (2026-11-07, `python tools/model_viewer.py rivers`)
- River water **A olive silt** (`#5C6E45`, underwater fog `#1E2414`); pool water **B deep green** (`#3E5F33`, fog `#111C0D`); floodplain grass **B deep green** (`#6F9B45`).
- The greener grass needs the floodplain to be a biome of its own (grass colour is per biome): `antcolonies:savanna_floodplain`, the band of the floodplain along the savanna rivers (it follows the same "peaks and valleys" band as the terrain, so land and colour line up), fading into the savanna's grass over a few blocks (the game's biome blend). The pools are placed only in it. Mounds treat it as savanna for the rules, but keep off its lowest ground (step 5).
- River edges: the plain sand stripe "sticks out so much" and grass to the water "doesn't look good"; new looks in the viewer: a new **silt** block (wet, darker at the waterline) as bars on the inside of bends, sloping into the water, and none on the outside (real rivers drop sand and silt where the current slows, the inside of a bend, and cut grassy banks on the outside); waiting for the user's pick.
- Bank height: neither "1 block above" nor "flush" (the user); new versions in the viewer: the plain 1 block above the water, a 1-block step down 1 to 5 blocks back from the water, then a low strip flush with the water (real rivers have this: a low active floodplain beside the channel and a higher terrace behind a small step). The bars lie on the low strip, never on the plain, with shallow water over their foot on sharp bends; **the user picked the step 4 blocks back (2026-11-07).**
- River edges, two more looks at the user's ask (mud or sand with the silt, all earlier looks kept): 6 silt bars with trampled mud at the waterline and in the hollows between the bar's ridges (real: animals come down to drink on the inside of bends, the low swales hold the finest mud; my pick); 7 graded bars, sand at the upstream head, silt toward the tail, mud at the water's edge in the tail (real: the current drops the coarse sand first). Waiting for the user's pick.
- Mud: the user asked whether Minecraft's mud looks right; my answer, no: it is a cool grey-purple swamp mud, while savanna river mud is the river's own silt, olive-brown and darker where wet, drying paler and cracking into plates up the bank. A new **river mud** block (two states, wet by the water and dried-cracked further up, my pick D; looks A wet, B with hoof prints, C cracked) in the viewer, Minecraft's mud pattern recoloured to the silt's colours so silt and mud sit together; random rotation in the game so the cracks do not repeat. **The user picked A, the wet river mud, without the light speckles (2026-11-07): "almost perfect".**

## Not in this plan (said so, can come later)
- Gallery forest (green trees along rivers), reeds and papyrus, crocodiles and hippos: M9.5 wildlife or later, to plan with the user.
- Seasonal drying of rivers and pans: with the seasons.
