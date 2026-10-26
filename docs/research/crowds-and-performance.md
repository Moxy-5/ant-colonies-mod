# Big crowds at 60 fps: every tool we could use (research, 2026-10-25)

Research only, no game code. It was written for the session planning M8 step 4 (numbered step 3 until 2026-10-25), the **drawn-only** insects: raid columns of 200 to 500 Matabele ants, the termites they fight, and colonies of thousands. The user's decisions so far (2026-10-25): 60 frames a second in a big raid; insects within 16 blocks of a player are real mobs; the fight front mixes real and drawn insects, the drawn ones playing fights the server has worked out; a drawn insect turns real in the same spot, so nothing blinks in view.

## Where we stand today (measured)

- **Drawing:** 400 walking ants take **25 to 27 ms a frame** to draw (guard films, `client/FilmFrameMeter`), about **63 microseconds an ant**. At 60 frames a second the whole frame has **16.7 ms**, and the world itself (chunks, sky, the player) takes some of that. So 400 ants drawn the way we draw today hold the game under 40 frames a second on their own. *(A microsecond is a thousandth of a millisecond.)*
- **Server:** a real insect costs about **0.02 ms a tick**. 50 real mobs a colony is about 1 ms of the server's 50 ms tick. The server is not the problem; drawing is.
- **How an insect is drawn today:** every real insect is a Minecraft entity drawn by GeckoLib (`client/AntRenderer`, a `GeoEntityRenderer`). Each frame GeckoLib works out the animation for that one insect, walks its tree of bones (the Matabele major has about 30), and writes every corner of every box of the model into a buffer on the main processor (the CPU), one insect at a time. Then our own extras run: the live layer (`BodyLanguage`: waist bend, edge tilt), the young-pale layer, carried loads, clamped heads, lost legs. Almost all of the 63 microseconds is this per-insect CPU work, not the graphics card (the GPU).
- **The three kinds of insect** (used all through this report):
  - **Real mobs:** full Minecraft entities, up to about 50 a colony. They can be hit, think, walk, fight.
  - **Drawn-only (planned):** drawn by the player's game only, moved by a script the server sent, not hittable, no thinking on the client.
  - **Numbers:** counts in the colony brain (`colony/Population`), not drawn at all.

Words used below:
- **Draw call:** one order from the CPU to the graphics card, "draw this mesh here". Each has a fixed cost, so thousands of them are slow even for tiny meshes.
- **Mesh:** the list of corners (vertices) and faces that makes a 3D shape.
- **Instancing:** one draw call that draws the same mesh many times, each copy with its own place and look, read from a list on the graphics card.
- **Shader:** a small program that runs on the graphics card for each corner or each pixel.
- **LOD (level of detail):** drawing far things more simply than near ones.
- **Shaderpacks / Iris / Oculus:** mods that replace Minecraft's lighting with fancy shaders. They take over how everything is drawn, so anything that draws in its own unusual way may vanish or look wrong under them. **Sodium / Embeddium:** mods that make chunk drawing much faster; many players use them.

---

## 1. Ready to use (a library or a known pattern)

### 1.1 Entity tracking settings (how often the server sends each mob's moves)
- **What:** every entity type says how far away players get its updates and how often (`clientTrackingRange`, `updateInterval` on the `EntityType` builder in `SavannaEntities`). Fewer, smaller updates for real mobs that do not need tick-perfect motion.
- **Who uses it:** vanilla itself (arrows update every 20 ticks, item frames rarely); every mob mod.
- **Works on NeoForge 1.21.1 / GeckoLib:** yes, built in.
- **Buys:** network and a little server time. Not frames.
- **Risks:** too slow an update makes far mobs stutter; keep near ones at 1 to 2 ticks.
- **Fits:** real mobs.
- **Verdict:** check our values once, five minutes of work. **Effort: tiny.**

### 1.2 Client-side tick culling and occlusion culling: Entity Culling (tr7zw)
- **What:** a client mod that skips drawing entities hidden behind blocks (occlusion culling: "is a wall between me and it?") and skips their client ticks when not visible (tick culling).
- **Who:** [Entity Culling](https://curseforge.com/minecraft/mc-mods/entityculling) ([Modrinth](https://modrinth.com/mod/entityculling)); it has NeoForge 1.21.1 builds and is in most performance packs.
- **Works with us:** yes, it works on any entity, GeckoLib ones included. It does nothing for our drawn-only insects unless we cull them ourselves (they are not entities).
- **Buys:** client frames, a lot underground: a player inside the nest cannot see the column on the surface, and vice versa.
- **Risks:** none for us; it is the player's choice to install. We must not depend on it.
- **Fits:** real mobs. **For drawn-only we copy the idea** (see 2.6).
- **Verdict:** recommend it to players; test once that our insects behave with it. **Effort: tiny.**

### 1.3 Animation throttling for far mobs (a known pattern; GeckoLib Animation Optimizer)
- **What:** far mobs work out their animation less often (every 2nd or 4th frame) and reuse the last pose in between.
- **Who:** the add-on [GeckoLib Animation Optimizer](https://www.modpackindex.com/mod/164844/geckolib-animation-optimizer) does distance throttling, view culling and block occlusion for GeckoLib mobs. Big game engines call it "animation LOD" or "update rate optimisation" (Unreal's URO).
- **Works with us:** the pattern yes (we can do it in `AntRenderer` and `BodyLanguage` ourselves; we already let the live layer and the animation have their own clocks). The add-on itself I could not confirm for NeoForge 1.21.1 or for our custom renderer; treat it as a source of ideas.
- **Buys:** client frames on real mobs past about 12 blocks. Maybe a third of the 63 microseconds for those; the vertex writing still happens every frame.
- **Risks:** far legs look slightly choppy; must keep walk speed matching the stride (CLAUDE.md rule).
- **Fits:** real mobs.
- **Verdict:** cheap, do it with the real-mob tidy-up. **Effort: small (a day).**

### 1.4 Minecraft's own particle engine as far-away insects (impostors)
- **What:** an **impostor** is a flat picture that stands in for a 3D model far away. Minecraft's particles are exactly that: tiny flat squares, always turned to face the camera, drawn in big batches, very cheap (the game happily draws thousands; the engine allows 16,384 per kind). A custom particle with a little sheet of ant pictures (seen from 8 directions, a 2 to 4 picture walk cycle) is a ready-made impostor.
- **Who:** every particle-heavy mod (falling leaves, fireflies, rain and snow mods; vanilla's own spore blossoms and ash). Real-time strategy games draw far units this way ("billboards").
- **Works with us:** yes, fully supported in NeoForge 1.21.1 (`ParticleProvider`, `TextureSheetParticle`), drawn by Sodium and Iris like vanilla particles (shaderpacks light them correctly).
- **Buys:** client frames: about 1 microsecond an insect or less, so 400 far insects cost well under 1 ms.
- **Risks:** a flat picture looks flat close up, so only past about 32 to 40 blocks (an ant there is a few pixels high anyway). Particles are not meant to follow a path exactly; we would drive each one ourselves each tick (that is fine, a particle has its own `tick`). Facing the camera means it never shows a side view of the body at a slant; at a distance nobody can tell. One subtle thing: a particle cannot lie flat on a wall, so climbing far ants would look odd (columns climb rarely).
- **Fits:** drawn-only, the far part of the column.
- **Verdict:** **the cheapest way to show hundreds of far ants, and safe with shaders.** **Effort: small to medium (2 to 4 days with the pictures).**

### 1.5 Our own fake entities on the client only
- **What:** the client may hold entities the server never heard of (added to the client's world with an id the server will never use). They are drawn by the normal renderers.
- **Who:** cosmetic mods do this (client-side critters such as [Cosy Critters](https://modrinth.com/mod/cosy-critters), which has a NeoForge 1.21.1 build and runs its birds' flocking only on the player's game); armour-stand and replay mods.
- **Works with us:** yes, and it is the **quickest start for drawn-only near the player**: a fake `MatabeleWorkerEntity` on the client reuses `AntRenderer`, GeckoLib, `BodyLanguage`, the clips, everything. Its "brain" is only the script the server sent.
- **Buys:** server cost close to zero (nothing ticks on the server, nothing is tracked). Client frames: **none saved**: it costs the same 63 microseconds as a real one, plus vanilla entity upkeep.
- **Risks:** entity id clashes if done carelessly; code that loops over "all entities" on the client sees them (our own layers must skip them); other mods may try to talk about them to the server (rare). Multiplayer: each player's game makes its own, from the same script, so all players see the same column.
- **Fits:** drawn-only within about 16 to 32 blocks, and the moment of "turning real" (2.5).
- **Verdict:** a good first step for drawn-only (the look is exactly the real one), but not enough alone for 500. **Effort: medium (about a week with the script player).**

### 1.6 NeoForge custom network messages (one message for a whole column)
- **What:** in place of 500 entities each sending its moves 20 times a second, the server sends one small message: "column 7 leaves the nest at tick T, along this path, 312 ants, seed S". The client works out where every ant is from the clock. (`CustomPacketPayload`, `PayloadRegistrar` in NeoForge 1.21.1; vanilla's bundle packet groups messages.)
- **Who:** every modern mod with custom sync; RTS games call it **deterministic simulation** or "lockstep" (both sides run the same rules from the same start, so only the start needs sending).
- **Works with us:** yes. It also matches the design already written: DESIGN "Marching columns": the forager numbers on a trail are **timetable lines** (left at time X, which trail, speed, carrying or not).
- **Buys:** network and server (nothing per ant per tick).
- **Risks:** both sides must agree exactly; use our own seeded dice (as `Colony.random()` already does), never the client's random. A player who joins mid-raid gets the column's message on arrival.
- **Fits:** drawn-only (all of it) and the numbers' trail traffic.
- **Verdict:** **the backbone of the drawn-only plan.** **Effort: medium (a week, with tests).**

### 1.7 Lithium / Canary / Radium, ModernFix, FerriteCore, Sodium / Embeddium
- **What:** general optimisation mods. Lithium (Canary and Radium are its Forge-side ports) speeds up the server's entity, block and path code; ModernFix speeds up loading and trims memory; FerriteCore trims memory; Sodium (it has NeoForge builds for 1.21.1) and Embeddium (the older Forge-side port) make chunk drawing much faster.
- **Who:** nearly every performance modpack.
- **Works with us:** they are the player's choice; our mod must simply work beside them (we should test with Sodium + Iris once).
- **Buys:** Sodium gives back frame time from chunks, which leaves more for insects. None of them makes GeckoLib entities much cheaper: Sodium's entity speed-ups target vanilla models, not GeckoLib's own path.
- **Risks:** compatibility only.
- **Fits:** none directly.
- **Verdict:** not a solution for crowds; test compatibility once. **Effort: tiny (a test).**

### 1.8 One crowd sound in place of 500 small ones
- **What:** one looping sound (a dry rustle and patter, the real Matabele column is audible: they **stridulate**, a chirping hiss from a file on the gaster, and the column's "marching" rustle is famous) placed on the part of the column nearest the player, louder for more ants. Single ants near the player keep their own small sounds.
- **Who:** every RTS and crowd game (ambience by density); Minecraft's own rain and cave ambience use one moving sound. In Minecraft: a `TickableSoundInstance` that moves itself each tick.
- **Works with us:** yes, built in; safe for every graphics mod.
- **Buys:** avoids the sound engine's limit (Minecraft can play only about 250 sounds at once; 500 ants each squeaking would cut off other sounds).
- **Risks:** none much; realism: needs a recording or a careful made sound.
- **Fits:** drawn-only and numbers on trails (you can hear a column before you see it).
- **Verdict:** small, big feel. **Effort: small (2 days plus the sound).**

---

## 2. Worth building ourselves

### 2.1 Baked poses: draw each insect from ready-made meshes (the big win)
- **What:** GeckoLib works out every bone of every insect every frame. But a drawn-only ant in a column only ever plays a few clips (walk, carry, fight). So, once at start-up, we play each clip through GeckoLib and **save the finished mesh of every frame** ("baking"), for example 24 frames of the walk. Each frame, drawing an ant is then just "take mesh number 7, place it here, turn it this way". No bones, no animation maths, no layers. Two ways to draw the baked meshes:
  - **(a) Copy on the CPU:** copy the saved corners into Minecraft's normal buffer with the ant's position applied. Simple, drawn through Minecraft's normal entity drawing (`RenderType.entityCutout`), so **Sodium and shaderpacks treat it like any entity**. Guess: 5 to 10 microseconds an ant (a plain copy of about 400 corners).
  - **(b) Saved on the graphics card:** each baked frame kept on the graphics card as a vertex buffer (Minecraft's own `VertexBuffer`), drawn with one call per ant, the place passed in. Guess: 2 to 4 microseconds an ant. Needs testing under Iris (it may draw these through its own shaders if we use the vanilla entity shader, or may not).
- **Who:** GPU Gems 3, "Animated Crowd Rendering" ([NVIDIA](https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch02.html)) is the classic; RTS games and Total War do this. In Minecraft, Create's contraptions bake their block meshes once and move them as a whole (through Flywheel, 3.1).
- **Works with us:** yes, our own code beside GeckoLib, nothing in GeckoLib changes. The baker reuses our models and clips, so every new clip is baked for free. A **per-insect size** (the Matabele size spread) is only a scale on the place, free. Young-pale colour: a colour on the whole mesh, also free.
- **Buys:** client frames: 5 to 10 times cheaper per drawn ant than today's path.
- **Risks:** no live layer (no waist bend over edges, no edge tilt): fine on flat ground, ugly on a climb, so climbing ants stay real or full GeckoLib. Lost legs and clamped heads need their own baked meshes or stay on full-detail ants. Memory: 24 frames × a few clips × 3 bodies × about 400 corners is a few megabytes, fine.
- **Fits:** drawn-only within about 16 to 40 blocks; also could draw **far real mobs** (past 12 blocks) cheaply.
- **Verdict:** **the most important piece to build for 60 fps.** Do (a) first, try (b) only if (a) is not enough. **Effort: medium (1 to 2 weeks).**

### 2.2 Phase groups: share one pose among many ants
- **What:** a column of 300 ants walking does not need 300 different walk timings; 8 or 12 timings ("phases"), shuffled so neighbours differ, look natural. Each phase's pose is worked out once a frame and shared.
- **Who:** crowd systems in films and games (Massive, Unity and Unreal crowd samples); bird and fish schools in nature shows.
- **Works with us:** goes with 2.1 (baking already gives this for free) or even with full GeckoLib (one animation per phase, then only the vertex writing per ant).
- **Buys:** client frames (the animation work drops from 300 to 8).
- **Risks:** if too few phases, a viewer sees ants stepping in step; 8 or more is usually enough.
- **Fits:** drawn-only, maybe far real mobs.
- **Verdict:** a small part of 2.1. **Effort: small.**

### 2.3 Distance bands (level of detail)
- **What:** each insect is drawn in the cheapest way that still looks right from where the player stands:

  | Distance | Kind | How drawn | Cost each (guess) |
  |---|---|---|---|
  | 0 to 16 blocks | real mob | full GeckoLib + live layer | 63 µs |
  | 16 to 40 | drawn-only | baked pose (2.1) | 5 to 10 µs |
  | 40 to 96 | drawn-only | impostor sprite (1.4) | about 1 µs |
  | past 96 | drawn-only | the dark line (2.8) | almost nothing |

  The switch from band to band happens with a short fade or at a moment the ant is small on screen, and a little "give" (switch at 40 going out, 36 coming in) so an ant at the edge does not flicker between looks.
- **Who:** every open-world and RTS game; [Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/distant-horizons/files/5390029/dependencies) does it for terrain.
- **Works with us:** yes, all ours.
- **Buys:** client frames, the main lever.
- **Risks:** the bands must match well enough that a change is not seen; 16 blocks in a Minecraft view is where a 1-block ant becomes small. Realism: none lost.
- **Fits:** all drawn ones; the real mobs are the first band.
- **Verdict:** **the frame of the whole plan.** **Effort: comes with 2.1 and 1.4.**

### 2.4 The column as a ribbon: place along a path, not each ant thinking
- **What:** a raid column is a line on a known path (the trail). Each drawn ant is just two numbers: **how far along the path** it is, and **how far to the side** of the middle. Its place each frame is worked out from the clock: no route search, no thinking, no pushing. The real column's shape comes from the rule: Matabele raids march in a **column 2 to 3 abreast, the scouts in front**, stopping to regroup before the attack, then fanning out round the feeding spot; on the way home the big workers carry termites stacked in their jaws.
- **Who:** called "formation along a spline" in strategy games; "data-oriented crowds" (a crowd stored as plain lists of numbers, gone through in one loop: Unity's DOTS and ECS samples run tens of thousands this way).
- **Works with us:** yes. The path is our trail (or a gallery), already known by the brain. The same numbers run on the server (for the brain: when the head of the column arrives) and on every client (for the look), from one message (1.6).
- **Buys:** server and client (each ant is a few sums a frame).
- **Risks:** ants on a path cannot step round a new block a player put down. Fix: the client checks the ground under each drawn ant (cheap: one block read) and lifts it onto a placed block; anything bigger, the drawn ant turns real (2.5).
- **Fits:** drawn-only (all), numbers' trail traffic (the server half).
- **Verdict:** **how the drawn column moves.** **Effort: medium (a week).**

### 2.5 Turning real in the same spot ("handover")
- **What:** when a drawn ant comes within 16 blocks of a player, the server makes a real mob at exactly the spot the script says the drawn one is, facing the same way, in the same step of its walk, with the same size and the same "who am I" number. The real mob's spawn message carries that number, and the client hides the drawn ant **the same frame the real one is first drawn**. Going the other way (a real ant walking away past 16 blocks), the real one hands its place back to the script: it is given its spot on the path and becomes drawn again.
- **Who:** open-world games' "crowd to agent" swaps (Assassin's Creed crowds, GTA pedestrians); MMO "proxy" entities.
- **Works with us:** yes. `Population` already swaps real mobs and numbers, only out of sight; this is the new in-sight swap the user asked for (it replaces "swaps only out of sight" for the drawn ones).
- **Buys:** nothing in speed; it is what makes the mix believable.
- **Risks:** this is the **hardest piece to get right**: (1) the real mob's first frames are interpolated by Minecraft from where it appeared, so we must spawn it already in place and send its walk phase; (2) network delay: the real mob's spawn reaches the client a moment after the server decided, so the client must keep drawing the scripted ant until the real one appears, and the script must be identical on both sides; (3) multiplayer: one player near, another far: the real mob is real for both (it is a server entity), the far player simply sees it as a real mob far away (cheap with throttling, 1.3).
- **Fits:** the border between drawn-only and real.
- **Verdict:** needed; build and film it early, with a guard film of a column walking past the camera. **Effort: medium to large (1 to 2 weeks, mostly testing).**

### 2.6 Our own culling for drawn insects
- **What:** draw a drawn ant only if it is (1) inside the camera's view cone ("frustum culling": things behind the player are skipped), and (2) not obviously behind solid ground (a cheap check: is its chunk section visible this frame? Minecraft already knows which chunk sections it draws).
- **Who:** Entity Culling (1.2) for entities; every engine.
- **Works with us:** yes; our draw code goes through Minecraft's level drawing event (`RenderLevelStageEvent`), where the camera and the visible sections are at hand.
- **Buys:** client frames: half the column is often behind the player or under a hill.
- **Risks:** none much.
- **Fits:** drawn-only.
- **Verdict:** a must, a day's work. **Effort: small.**

### 2.7 Scripted fights worked out by the server
- **What:** at the fight front the server decides each drawn duel with the real rules and odds (from `fight` in the species files: grab odds, soldiers biting first, the sting's delay, lost legs), and sends only "ant 41 fights termite 9 at this spot from tick T, the ant wins at T+60, termite paralysed". Every client plays the clips at the given times. A drawn fight near a real mob or a player can turn real (2.5) in the middle, from the same state.
- **Who:** strategy games with "resolved" battles shown by animation (Total War's far units; Mount & Blade's far battles); our own `/antcolonies skirmish` already holds the rules.
- **Works with us:** yes; it reuses the fight clips (attack A and B, the termite flip, `clamp_hold`).
- **Buys:** server: a duel is a few numbers, not two thinking mobs. Client: drawn fighters use baked clips.
- **Risks:** drawn fights must use the same odds as real fights so a raid's result does not depend on where the player stands. Test this (the user's realism rule): the same raid seen and unseen should kill about the same termites.
- **Fits:** drawn-only at the front; numbers for unseen raids.
- **Verdict:** part of the plan already; build after the column. **Effort: medium to large (2 weeks).**

### 2.8 The dark moving line far away
- **What:** past about 96 blocks a raid column is no longer ants; it is a thin dark band on the ground moving along the trail, thicker where the ants are packed, its front edge ragged. Drawn as a long strip of quads (flat squares) lying on the ground, a "ribbon", textured with a dark speckled picture that scrolls along it. Real Matabele columns from a distance look exactly like this: a black thread across the savanna.
- **Who:** RTS minimaps and far armies; trail mods drawing paths on the ground.
- **Works with us:** yes, our own drawing in `RenderLevelStageEvent`; put a small "polygon offset" (a nudge toward the camera) so the band does not flicker against the ground.
- **Buys:** client frames: a whole column for the cost of one strip.
- **Risks:** shaderpacks may light the strip oddly if drawn with an unusual shader; draw it with a vanilla render type (translucent or cutout) to stay safe. Must hug the ground over bumps (sample the ground height along the trail, as `GroundReader` does).
- **Fits:** drawn-only (very far), numbers on trails (a big colony's trunk trails busy with numbers could show a faint traffic line).
- **Verdict:** a lovely touch, cheap. **Effort: small to medium (3 to 5 days).**

### 2.9 Trails and runways as ground decals
- **What:** a **decal** is a picture laid on top of a surface (like a sticker), here on the ground. Trails could show their traffic as faint wear marks, scattered termite remains on the way home from a raid, the raid's battle spot as a darker scuffed ring.
- **Who:** most games (bullet holes, footprints); in Minecraft, vanilla's block-breaking cracks are decals.
- **Works with us:** yes, as 2.8. We already change real blocks for trails (coarse dirt); decals would be for things that should not be blocks (short-lived marks).
- **Buys:** looks, at almost no cost.
- **Risks:** realism: Matabele trails are pheromone, invisible; worn ground only on long-used harvester trails. So: use sparingly, for real visible things only (remains, scuffs).
- **Fits:** all.
- **Verdict:** optional polish for M8.5. **Effort: small.**

### 2.10 Spatial hashing on the server (who is near whom)
- **What:** the ground is cut into a grid of cells (say 4 blocks); each insect is listed in its cell. "Who is near me?" then looks only at the 9 cells round it, not all insects. Our fights, alarm and noticing all ask such questions.
- **Who:** every crowd and physics engine; Minecraft itself groups entities by chunk section.
- **Works with us:** yes; for real mobs Minecraft's own section lists already do it (`getEntitiesOfClass` with a small box). For **drawn and numbers at the front** (hundreds of duellists) a small grid of our own keeps the fight script cheap.
- **Buys:** server.
- **Risks:** none.
- **Fits:** drawn-only fight front, numbers.
- **Verdict:** use when the fight script is built. **Effort: small.**

### 2.11 Flow fields (one map of "which way" for a whole crowd)
- **What:** in place of each ant searching its own route, the ground round a target gets one map where every cell holds an arrow toward the target. Any number of ants just follow the arrows. Strategy games (Supreme Commander 2, Planetary Annihilation) move thousands of units this way.
- **Who:** [the Supreme Commander 2 flow field talk](https://howtorts.github.io/2014/01/04/basic-flow-fields.html) is the usual reference; Elijah Emerson's chapter in *Game AI Pro*.
- **Works with us:** yes, but our columns follow trails, so the ribbon (2.4) does the job for the march. A flow field fits **the fan-out round a feeding spot** and the **rush home**, where hundreds leave one point for one hole.
- **Buys:** server (one map shared by hundreds in place of hundreds of route searches); also good for real mobs at the front (each real ant asks the field, not the path finder).
- **Risks:** our legs (`AntNavigator`, `AntPathFinder`) climb walls and drop down holes; a flow field on the ground plane does not. Keep it to the open ground round a fight, and let the legs do the rest (the mind says "go to this cell", CLAUDE.md layers).
- **Fits:** drawn-only at the front; maybe real mobs.
- **Verdict:** only if the fan-out proves costly. **Effort: medium.**

### 2.12 Cheaper real mobs
- **What:** the 63 microseconds of a real mob can come down too: skip `BodyLanguage` and the layers when they cannot show (flat ground, nothing carried); throttle the animation past 12 blocks (1.3); draw real mobs past 12 blocks with baked poses (2.1) when they are only walking on the flat.
- **Who:** our own code.
- **Buys:** client frames: 50 real mobs at full price are about 3 ms; at the front, two colonies' real mobs together could be 100, about 6 ms, too much. Trimmed to maybe 30 µs, 100 real mobs fit in 3 ms.
- **Risks:** the guard films must stay picture for picture the same near the camera.
- **Fits:** real mobs.
- **Verdict:** worth it once the drawn ones are in. **Effort: small to medium.**

---

## 3. Long shots

### 3.1 Flywheel (Create's instanced drawing engine)
- **What:** [Flywheel](https://github.com/Engine-Room/Flywheel) is the library Create uses to draw thousands of moving parts (gears, belts, trains) with GPU instancing (one draw call for many copies). Mods define "visuals" for their entities and Flywheel draws them in batches.
- **Who:** Create 6 (it includes Flywheel 1.0 inside), Create add-ons. A 2025 change made Flywheel draw everything in one pass and share instancers between entities, block entities and effects ([commit notes, mirror](https://git.grimmauld.de/mirrors/Flywheel/src/commit/e36dbe12d58e5c0e49fb104e5050796afb0f060f)).
- **Works with us:** NeoForge 1.21.1 builds exist. But Flywheel instances **rigid pieces**; our insects bend (bones). We would make each bone an instance (30 per ant) or bake poses (2.1) into instance meshes. It has no GeckoLib bridge.
- **Buys:** client frames, possibly the lowest cost per ant of anything here (well under a microsecond).
- **Risks:** a heavy new dependency for every player; its API has broken between versions; **under shaderpacks it needs a separate bridge mod** ([Iris & Oculus Flywheel Compat](https://www.curseforge.com/minecraft/mc-mods/iris-flywheel-compat/files/6518788/dependencies), [Colorwheel](https://www.curseforge.com/minecraft/mc-mods/colorwheel/files/8990905)) or the insects vanish or look wrong. Players would need the bridge too.
- **Fits:** drawn-only.
- **Verdict:** only if 2.1 + 1.4 cannot reach 60 fps; too much dependency for now. **Effort: large (3 to 4 weeks, plus compatibility work).**

### 3.2 Our own GPU instancing with a custom shader, or vertex animation textures
- **What:** the same idea as Flywheel, written by us: the baked frames (or the bones' movements) stored in a texture on the graphics card (a **vertex animation texture**, VAT), one draw call for all 500 ants, each ant's place and frame in a list. Games draw tens of thousands of animated units this way ([GPU Gems 3](https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch02.html); [Unigine forum write-up](https://developer.unigine.com/forum/topic/7555-instanced-animation-rendering): about 10,000 meshes at 150 fps).
- **Works with us:** technically yes (OpenGL instancing is available to mods), but Minecraft has no such path; we would write raw graphics code.
- **Buys:** client frames, the lowest possible.
- **Risks:** **breaks under shaderpacks** (Iris replaces shaders; our custom one would be skipped or need Iris's own bridge), no shadow pass, hard to debug for a project whose user cannot read code. Sodium is fine with it.
- **Fits:** drawn-only.
- **Verdict:** not needed for 600 insects; maybe for army ants with thousands on screen (later species). **Effort: large.**

### 3.3 Distant Horizons hooks
- **What:** [Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/distant-horizons/files/5390029/dependencies) draws terrain far past the normal view. Its [API](https://www.mado-hosting.com/en/minecraft-mods/distanthorizonsapi) (bundled in the mod, 1.21.1 supported) lets other mods talk to it. Newer versions can draw some non-terrain things far out (I recall clouds and beacon beams through a "generic rendering" hook; I could not confirm that hook for 1.21.1, check before use).
- **Works with us:** maybe, as an optional extra: show the dark line (2.8) or a huge mound's outline on DH's far land.
- **Buys:** looks for DH players only.
- **Risks:** optional dependency, API changes, only some players have it.
- **Fits:** drawn-only, very far.
- **Verdict:** a "nice if they have it" later. **Effort: medium.**

### 3.4 Client-side swarms with their own little minds (boids)
- **What:** **boids** are the classic three-rule flock (keep apart, line up, stay together). A swarm can run only on the player's game, untied to the server.
- **Who:** [Cosy Critters](https://modrinth.com/mod/cosy-critters/version/v0.2.0+1.21.1-neoforge) (client-only birds, NeoForge 1.21.1), [Flock](https://modrinth.com/mod/flock), the Boids mod (server-side fish).
- **Works with us:** yes, but ants do not flock: columns follow pheromone, not neighbours. Good only for **flying** things: alates on a nuptial flight (M9), termite swarms after rain, the birds that follow them.
- **Buys:** server cost zero for pure cosmetics.
- **Risks:** different players see different swarms (fine for cosmetics, not for anything that matters).
- **Fits:** drawn-only, flights in M9.
- **Verdict:** keep for M9's flights. **Effort: small to medium.**

### 3.5 The mob-heavy mods (Alex's Mobs, Ice and Fire, the Mowzie's mods, MCA)
- **Looked at:** these show many detailed mobs but **none of them solves big crowds**: their trick is having few of them (Alex's Mobs' crows and flies spawn in small groups; Ice and Fire's dragons are one at a time; the Mowzie's mods are bosses; MCA's villagers are ordinary entities). Their lesson is negative: full mobs do not scale, which our 63-microsecond measure already tells us. **Verdict: nothing to take.**

### 3.6 Empires of the Undergrowth
- **Looked at:** Slug Disco's devlogs and Steam posts say the game moved from its own engine to Unreal Engine and that the design keeps the player on "the bigger picture rather than individuals" ([Slug Disco](https://slugdisco.com/?p=951), [newsletter](https://slugdisco.itch.io/empires-of-the-undergrowth/devlog/199487/november-2020-newsletter)); one changelog says ants stopped walking through each other in fights and **the front line now limits how many attackers can reach a point** ([Steam discussion](https://steamcommunity.com/app/463530/discussions/0/3211505894143241195)). No public notes on how they draw so many ants. **What to take:** the front-line limit is a good rule for our fight script (a termite can be reached by at most 2 or 3 ants at once, as in real fights). **Verdict: a design hint, no tech.**

---

## Creative doors (not about speed)

- **Hearing a raid before you see it** (1.8): the column's rustle and the stridulation hiss grow as it nears; the alarm squeak of a hurt ant cuts through.
- **Watching from afar:** the dark thread crossing the savanna (2.8), splitting at the feeding spot into a dark smudge, coming back slower and lumpier (ants carrying termites stacked in their jaws, the realest part of a Matabele raid).
- **Scripted fights at the front** (2.7), settled by the same odds as real fights, turning real the moment the player steps close.
- **Remains on the battlefield:** termite heads and legs left where the raid was (they fade after a day), a scuffed patch at the feeding spot (2.9).
- **Packed chambers:** the same baked-pose drawing fills a big termite nest's galleries with hundreds of drawn termites when the player breaks in (the termites-underground plan already says the hidden travellers come out where they would be).
- **A column you can follow:** a player can walk beside a 400-strong raid from the nest to the termites and see the whole thing, with only the 16 blocks round them made of real mobs.

---

## Recommended stack for the drawn-only plan (M8 step 4)

**Who is what:**
- **Real mobs** (full GeckoLib + live layer): every insect within 16 blocks of a player, the user's rule; at the fight front, cap it at about 60 to 80 real insects in all near one player (both sides), the rest drawn. Cheapened by 2.12 (animation throttled past 12 blocks, live layer skipped when it cannot show).
- **Drawn, near band (16 to 40 blocks):** baked poses (2.1a) with phase groups (2.2), through Minecraft's normal entity drawing, so Sodium and shaderpacks are fine.
- **Drawn, far band (40 to 96 blocks):** impostor particles (1.4).
- **Drawn, very far (past 96 blocks):** the dark line (2.8).
- **Numbers:** everything unseen; their raids settled by the same fight rules (2.7) as numbers.

**How the drawn ones move and fight:** one message per column (1.6), each ant a place along the trail and to the side (2.4), worked out from the clock with the colony's seeded dice; fights settled on the server and played as clips (2.7); turning real at the same spot (2.5); culled when behind the player or under ground (2.6); one crowd sound per column (1.8).

**The frame budget for about 600 insects on screen (guesses, to measure with `FilmFrameMeter` and `/antcolonies perf`):**

| Band | How many | Each | Total |
|---|---|---|---|
| Real, within 16 blocks | 60 | about 40 µs (trimmed) | 2.4 ms |
| Drawn, 16 to 40 blocks | 150 | about 8 µs | 1.2 ms |
| Drawn, 40 to 96 blocks | 390 | about 1 µs | 0.4 ms |
| The dark line, sound, script | | | 0.3 ms |
| **Insects in all** | **600** | | **about 4.3 ms** |

That leaves about 12 ms of the 16.7 for the world, which a normal savanna with Sodium uses comfortably. Today the same 600 would cost about 38 ms (26 frames a second). Even if the guesses are twice too low, it fits.

**Order to build (each step filmed and measured before the next):**
1. The column message and the ribbon (1.6, 2.4), drawn first as client-only fake entities (1.5) so it looks right at once. Measure: it will be slow, that is fine.
2. Baked poses for the near band (2.1a, 2.2) and culling (2.6). Measure the cost per ant.
3. Impostor particles for the far band (1.4), then the dark line (2.8).
4. Turning real in the same spot (2.5), with a guard film of a column passing the camera.
5. Scripted fights (2.7), with a test that a raid kills about the same termites seen and unseen.
6. The crowd sound (1.8); real-mob trimming (2.12) if the front is still heavy.
7. Test once with Sodium + Iris and a common shaderpack, and with Entity Culling.

**Not now:** Flywheel (3.1), our own GPU instancing (3.2), Distant Horizons (3.3). Keep them for the army ants and leafcutters (later species with thousands on screen), if the measured numbers ever say we need them.

**Uncertain, to check while building:** the per-ant costs above are estimates from how the code works, not measured; whether `VertexBuffer` drawing (2.1b) survives Iris; the GeckoLib Animation Optimizer's NeoForge 1.21.1 support; the Distant Horizons generic-rendering hook on 1.21.1.

## Sources
- Flywheel: [GitHub](https://github.com/Engine-Room/Flywheel), [mirror commit notes](https://git.grimmauld.de/mirrors/Flywheel/src/commit/e36dbe12d58e5c0e49fb104e5050796afb0f060f); shader bridges [Iris & Oculus Flywheel Compat](https://www.curseforge.com/minecraft/mc-mods/iris-flywheel-compat/files/6518788/dependencies), [Colorwheel](https://www.curseforge.com/minecraft/mc-mods/colorwheel/files/8990905)
- [Entity Culling, CurseForge](https://curseforge.com/minecraft/mc-mods/entityculling), [Modrinth](https://modrinth.com/mod/entityculling)
- [GeckoLib Animation Optimizer](https://www.modpackindex.com/mod/164844/geckolib-animation-optimizer)
- [Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/distant-horizons/files/5390029/dependencies), [Distant Horizons API](https://www.mado-hosting.com/en/minecraft-mods/distanthorizonsapi)
- [Cosy Critters (NeoForge 1.21.1)](https://modrinth.com/mod/cosy-critters/version/v0.2.0+1.21.1-neoforge), [Flock](https://modrinth.com/mod/flock)
- [GPU Gems 3, Animated Crowd Rendering](https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch02.html); [Unigine: instanced animation rendering](https://developer.unigine.com/forum/topic/7555-instanced-animation-rendering); [luma.gl GPU-animated crowds](https://luma.gl/docs/api-reference/gltf/gltf-crowd-usage); [Virginia Tech crowd LOD paper](https://people.cs.vt.edu/~yongcao/publication/pdf/chao2011_MIG.pdf)
- [Flow fields (howtorts)](https://howtorts.github.io/2014/01/04/basic-flow-fields.html)
- Empires of the Undergrowth: [Slug Disco](https://slugdisco.com/?p=951), [November 2020 newsletter](https://slugdisco.itch.io/empires-of-the-undergrowth/devlog/199487/november-2020-newsletter), [Steam discussion](https://steamcommunity.com/app/463530/discussions/0/3211505894143241195)
- Our own measures: `docs/HISTORY.md` ("Guard films", 400 ants 25 to 27 ms), `docs/DESIGN.md` ("Marching columns", "Performance").
