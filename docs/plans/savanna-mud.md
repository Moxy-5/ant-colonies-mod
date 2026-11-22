# Savanna Mud & Wallowing System

**Status:** Phase 0 approved & Phase 1 Asset Pack exported — ready for Claude Java Phase 1 & Phase 2 — 2026-11-21.
**User decisions (2026-11-21):**
- Keep existing `antcolonies:river_mud` as firm riverbank mud for now (subtle ankle mud when walked on; any deeper merger planned later).
- **Spread-out quarter-block sinking gradient (`0.25 → 0.50 → 0.75 → 1.00`):** Four smooth sinking steps (`0.25` early sticky ankle drop, `0.50` shallow soupy wading shelf, `0.75` mid-soupy middle slope, `1.00` deep center wallow pocket) so basins slope naturally instead of sudden cliffs (slow wading, easy jumping out, never frustrating quicksand traps).
- **Gradual water wash-off:** Water (rivers, oxbows, pans, rain) washes mud off **gradually step-by-step**, never snapping clean instantaneously — 1 `coverage` step every ~25–30 ticks (~1.2–1.5 s) while wading in water (~15 ticks when swimming/submerged), emitting swirling brown silt-cloud particles; heavy rain softens dry mud and slowly rinses it down over ~10–15 seconds.
- Animals are fun first, inspired by real life; termite mound rubbing links stay realistic; seasons always query `Seasons.now(level, pos)`.

---

## 1. Zero-Lag Architecture (Core Rules)

1. **No flowing mud fluids:** Soupy wallows are solid/recessed blocks (`SavannaMudBlock`) with a 4-frame animated vanilla surface texture (GPU-only). Zero fluid ticks, zero neighbor cascades.
2. **Zero skylight recalculations:** Every wetness stage shares the same light opacity, so drying/wetting never triggers Minecraft's lighting engine.
3. **One `WallowRegistry` (`SavedData`) per dimension:** One lightweight record per pit (center, radius, deep-pocket offsets, wetness float, soil tone, virtual life counts). Ticked once per second (20 ticks). Animals query nearest wallow in O(1) instead of scanning blocks.
4. **Budgeted radial trickle updates:** Max 2–4 block updates per tick per pit, drying/wetting from outer rim inward — zero chunk-mesh rebuild stutter spikes.
5. **1 packed byte per entity (`MudCoat`):**
   - `coverage` (3 bits, `0..7`): `0` clean → `1..2` feet/belly → `3..5` flanks → `6..7` full wallow coat.
   - `dryness` (3 bits, `0..7`): `0..1` dark glossy wet → `2..4` matte tacky → `5..7` pale dusty cracked flaking off.
   - `tone` (2 bits, `0..3`): `0` floodplain olive, `1` far-inland Kenya red-ochre, `2` mid-inland golden silt, `3` coastal pale.
   - Updated on a staggered 40–100 tick timer (`(tick + entityId) % 60 == 0`); synced only when a visible step changes.
6. **Zero-shader GeckoLib `MudCoatLayer` (in `framework/`):**
   - Early-out when `coverage == 0` (0 ms for clean mobs) and distance LOD past ~56 blocks.
   - Uses auto-generated greyscale UV masks (`<animal>_mud_low.png`, `<animal>_mud_full.png` from `tools/mud_masks.py`) rendered via `RenderType.entityTranslucent` + dynamic ARGB vertex tinting (same proven pattern as `YoungPaleLayer`, 100% Sodium/Iris/shader-pack safe).
7. **Zero BlockEntities on mud blocks:** Tracks are compact blockstates that smooth away in rain or when drying.
8. **Colony-Brain trick for hidden mud life:** Tadpoles, shrimp, killifish eggs, and puddling butterflies live as numbers in the Wallow Book and draw as client-only particles when a player walks close.

---

## 2. Mud Block Stages & Depths

| Stage | State Name | Sink Depth | Movement & Feel | Captures Tracks? |
| :--- | :--- | :--- | :--- | :--- |
| `0` | `cracked` | `0.00` blocks (`16px` box) | Hard baked clay plates, crunchy sound | No (tracks dry/erase) |
| `1` | `drying` | `0.00` blocks (`16px` box) | Matte firming rim, dull thud | No |
| `2` | `sticky` | **`0.25` blocks (`12px` box)** | Early quarter-block ankle drop, glossy soft mud, squelch, mild drag | **Yes** (hoof, heavy pad, paw, reptile drag, boot) |
| `3` | `soupy_shallow` | **`0.50` blocks (`8px` box)** | Animated wet surface, shin/knee wading shelf, ankle/belly mud coat | No |
| `4` | `soupy_mid` | **`0.75` blocks (`4px` box)** | Animated wet surface, middle transition slope between shallow and deep | No |
| `5` | `soupy_deep` | **`1.00` block (`0–1px` box)** | Animated wet surface, chest/back deep wallow pocket, brown fog if eyes dip under | No |

- `#antcolonies:mud_adapted` mobs (hippos, crocodiles, monitors, bullfrogs, otters, crabs, future warthogs/buffaloes/elephants/rhinos/pangolins) pathfind cleanly through mud without getting bogged down.
- Regional soil tones follow `SandBanks.TONE_FROM` (far inland red-ochre, mid inland golden, coastal pale) + floodplain olive silt.

---

## 3. Ecosystem Loops

- **Midday heat & wallowing:** Hot midday ticks nudge wallowers to visit nearby wallows from `WallowRegistry`.
- **Fly Cloud repulsion:** Fresh mud coats (`coverage >= 3`, `dryness <= 4`) repel existing `FlyCloudEntity` swarms; as mud dries and flakes off, flies return.
- **Rubbing posts:** Dry-mudded animals (`dryness >= 5`) rub on giant savanna trees and Macrotermes termite mounds, shedding dust flakes, leaving shoulder-height mud polish, and briefly alerting nearby termite soldiers.
- **Self-cleaning rain puddles:** Rain spawns 1–2 small temporary wallows in natural dips near loaded players; once dried out, they cleanly revert to grass/dirt so terrain never scars permanently.
- **Player utility:** Scoop regional **Savanna Mud Balls** (throw to splat mobs/players, plaster on yourself for fly-repellent + stealth/cooling, or dry into regional savanna adobe).

---

## 4. Phased Rollout

- **Phase 0 (Neon):** `tools/mud_masks.py` (auto-generate `_mud_low.png` and `_mud_full.png` from `.geo.json` + `body_plans/`, excluding eyes/pupils/teeth/tongue) + interactive 3D sidebar viewer (`savanna_mud_viewer.html`) showing Hippo, Crocodile, Bullfrog, Otter + 0.5-block / 1.0-block wallow cutaway diorama.
- **Phase 1 (Claude):** `SavannaMudBlock`, `WallowRegistry`, worldgen wallow sites (respecting `BigSavanna` gate & mounds), ephemeral rain puddles, `tools/flight.sh` chunk-load benchmark. *(Replaced by section 5: no `WallowRegistry`, no rain puddles.)*
- **Phase 2 (Framework + Savanna):** 1-byte `MudCoat` attachment in `framework/`, `MudCoatLayer` + `MudPlayerLayer`, passive wading, water wash, drying flakes, throwable `MudBallItem`.
- **Phase 3 (Neon + Claude):** `WallowGoal`, wallow/shake clips, `FlyCloudEntity` repulsion, tree/termite-mound rubbing posts, sticky-mud footprints.
- **Phase 4 (Claude + Neon):** Client-side mud-puddling butterflies & tadpole/shrimp particles, buried lungfish cocoon block, rain-hatched annual killifish.

---

## 5. Claude's build plan: where the mud goes and how it is built (2026-11-21; the user picked "river mud plus dry wallows" the same day)

Neon's assets are kept as they are (the 6 looks, the 4 tones, the masks, the tint table, the gradual wash-off). What
changes is the Java underneath, made smaller (the "fewer rules" rule): no per-world pit book, no budgeted trickle
updates. Minecraft already has a cheap, proven way for a block to dry and get wet again (farmland), and a cheap way to
find the nearest special block (the points of interest villagers use for beds). Numbers are my picks unless marked.

### 5.1 Where the mud is (three kinds of place)

| Place | Where exactly | Size | How often | Tone | Stays wet? |
|---|---|---|---|---|---|
| **River mud areas** (the user's idea) | strips on the low flat strip of savanna rivers, right at the waterline, where animals come out of the water; never on a sand bank or a steep outer bend | 8 to 16 blocks along the river, 3 to 6 back from the water; sticky round the edge, soupy in the middle, a deep middle in the bigger ones | about one per 120 blocks of river, all zones, spaced the shared river-site way (`RiverSites`); plus one at each hippo pod's pool, where the pod comes out to graze | olive (river silt) | **always**: every block is within 4 of the water, so none ever dries (until seasons lower the river) |
| **Pan wallows** | one end of each of today's floodplain pans (the rest of the pan keeps its `river_mud` ring, the user's call); the reworked big pans later get 1 or 2 in their trampled ring | 4 to 7 across | every pan | olive | yes, the pan's water keeps them wet |
| **Dry savanna wallows** | open flat savanna inside big savannas (the quiet-savanna gate), off the floodplain, 10+ blocks from any termite mound place, 4+ from trees, logs and nests | 4 to 7 across, a 1x1 or 2x2 deep middle in half of them | about one per 150 blocks | the land's own: red far inland, golden mid inland, pale near the coast (the sand banks' bands) | no: cracked hollows most of the time, coming alive in every storm |

Real, and why these three: mud forms where clay soil holds water: at river edges where animals come down to drink (kept
wet from below by the river; it cracks only when the river drops in the dry season, so with seasons later),
in pans, and in dips on the open plain that fill after rain (warthog and buffalo wallows). Their mud is the local soil,
so an animal's coat shows where it wallowed (Tsavo's red elephants). The dry wallows sit in the gaps between termite
mounds (mounds 42 to 82 apart, wallows 150 apart and 10 off any mound place: they fit without crowding).

### 5.2 The block: one block, three settings

`antcolonies:savanna_mud` with
- **wetness:** cracked, drying, sticky, soupy;
- **depth:** 1 to 4, how many quarter blocks its floor sits down when fully wet (the rim ring 1, then 2, 3, the deep
  middle 4). Set when the wallow is made and **never lost when it dries**, so a dried pit comes back the same shape after
  rain (Neon's plan needed the pit book for this);
- **tone:** olive, red, golden, pale.

**How far you sink (changed by the user, 2026-11-21: "sink 0.25 to 0.50 depending on how heavy they are"):** in soupy
mud a light creature (a crab, a bullfrog) sinks a quarter block, a heavy one (a hippo) half a block, by the size of its
body (a player about 0.375); sticky mud half that; drying and cracked are firm, flat ground. The depth no longer changes
the sinking: it is the soupy look (darker toward the middle) and how slowly the mud dries. Was: soupy sinks by its depth
(0.25, 0.5, 0.75, 1.0), sticky 0.25. Neon's six looks map straight on (soupy at depth 1 looks sticky). A dried wallow is a flat patch of cracked clay
level with the grass (my pick: no new models needed; a real one is a shallow dish).

Feel (Neon's numbers): drag on anything not tagged `#antcolonies:mud_adapted` (sticky a little, shallow 0.75, mid 0.6,
deep 0.5), squelch sounds, brown screen if a player's eyes go under. Every ring is a quarter block lower than the last,
so you always walk out: never a trap. Adapted now: hippo, crocodile, monitor, bullfrog, crab (the otter when it comes
in). The sinking is real: the mob's legs are hidden inside the mud block, like powder snow.

### 5.3 Getting wet and drying (like farmland)

Each mud block, on its own random tick (in loaded land only, about once a minute a block):
- water within 4 blocks, or rain falling on it: one step wetter (up to soupy);
- otherwise, by chance, one step drier, **the shallower the faster**: the rim cracks first and the deep middle stays
  soupy longest, so a pit dries from the edge inward with no extra code. Soupy to cracked takes about one game day for a
  rim block, two for the deep middle;
- a storm (half a game day or more) brings a dry wallow fully back.

The block change is spread out by the random ticks themselves (a few blocks a minute), so no stutter. When seasons come
the drying speed asks the shared season source; nothing else changes.

### 5.4 Mud on bodies (Neon's Phase 2, kept)

The one-byte mud coat in the framework, the coat layer for the 8 animals with masks and the player's legs, picked up by
walking in sticky or soupy mud, washed off step by step in water and rain, drying pale and flaking with time. A fresh wet
coat keeps fly clouds off (real: that is one of the reasons animals wallow). The coat is sent to players only when its
look changes.

### 5.5 Ants and termites (realism rule)

- **The ant legs go round wet mud (sticky, soupy)** (BUILT 2026-11-21 as a cost, not a wall: each foothold on wet mud
  costs a route about 12 blocks of walking, `nav/SoftGround`, so a column goes round and an ant set down in it, or with no
  other way, still crosses; ants and termites do not sink in mud). Dry mud (drying, cracked) is plain ground. Real: Matabele ants keep off wet ground and water; termites never cross
  open wet mud, they forage under their own runways. A raid column meeting a wallow goes round it.
- No wallow on a mound place, a Matabele nest or a crocodile nest site.
- Rubbing posts on mounds (later): **no soldier alarm.** Real Macrotermes soldiers pour out when the wall is broken, and a
  mound's wall is rock-hard clay that an elephant can rub without breaching. The polish line stays.

### 5.6 Who uses the mud now (honest)

The real wallowers (warthog, buffalo, elephant, rhino, pangolin) are not in the game yet. Until one comes:
- every animal and the player gets muddy walking through; hippos cross the bank wallows going out to graze at night;
- the bullfrog digs in dry and sticky mud as well as river mud (real: it buries itself in mud through the dry season);
- **no deliberate wallowing goal yet.** Real hippos do not roll in mud (they spend the day in water), crocodiles do not
  either; the "wallow in the midday heat" goal comes with the first real wallower (the warthog, the king of wallowing).

### 5.7 Mud balls (small)

Breaking sticky or soupy mud with a shovel gives mud balls in that tone. Thrown like a snowball: a brown splat and a
muddy coat on what it hits. Used on yourself: a coat (keeps flies off). Dry mud gives the block back. (Neon's adobe and
the stealth bonus are left out: not asked for; ideas for later.)

### 5.8 Steps

1. **The block** (BUILT 2026-11-21, `MudTests`, film `tools/film/savanna_mud.film`; no path costs: a first try marking
   deep mud as honey trapped a pig, since Minecraft never plans a step up out of honey) (wetness, depth, tone; collisions, drag, sounds, brown screen, colour handlers, item, the
   `mud_adapted` tag, path types) and the drying/wetting tick. Blockstate file rewritten for the new settings (I write
   it; Neon's tool should not overwrite it). Light fix: the block lets light through and hides only the faces it shares
   with other mud (as glass does), else a small animal sunk in it would be drawn black. Tests: sink depths, drag, adapted
   mobs unhurried, a player-sized mob walks out of the deep middle, a pit dries from the rim and comes back after rain.
2. **The ant legs and wet mud** (BUILT 2026-11-21: `nav/SoftGround`, test `antsGoRoundWetMudButCrossItDry`; `legs` and
   `raids` run).
3. **Placing the wallows in new land** (the three places above, spaced from the seed like the other sites); `worldgen`
   tests (off mounds, flat ground, spaced, olive by the river, the land's tone off it); chunk-making time measured.
   Full test run here (3 steps).
4. **Mud on bodies** (framework coat, coat layer, player legs, wash-off, fly clouds keeping off); the river animals
   checked walking in and out of every depth (`river`); clip checks for the sinking look.
5. **Mud balls.**
6. **In-game test by the user** (a new world).

Later, not in this build: tracks in sticky mud, rubbing posts, the wallowing goal (with the warthog), life in the mud
(lungfish, killifish, tadpole shrimp, puddling butterflies), the big pans' wallows (with the pans rework).

### 5.9 The user's call

- **Decided (the user, 2026-11-21): river mud plus dry wallows.** The river mud areas stay wet always, the dry wallows
  are the ones that dry and come back in storms. No new puddles made at play time (Neon's rain puddles left out): the
  dry wallows give the mud made by rain. The reasoning kept below.
- Was: **rain puddles on new ground near players** (Neon's Phase 1) or **only the world-made dry wallows filling with rain**
  (my recommendation). The dry wallows already give "mud made by rain": a cracked hollow turns soupy in a storm and dries
  away after. Making new puddles at play time means changing the player's ground while they play (near farms and
  builds too) and one more system to keep cheap; I would leave it out unless the dry wallows feel too few.
