# Small prey fish, frogs and tadpoles, and fewer tilapia (M9 river life; planned 2026-11-20, nothing built)

The user, 2026-11-20 ("these sound perfect for right now, we should reduce the amount of tilapia in the river to make
room for the smaller schooling fish"): two new prey for the river, picked from six options (terrapin, tigerfish,
hippo, otter were the others; the terrapin stays next after these). Animals rule: generalized, fun first, inspired by
real rivers; no source checking; the game-balancer rule holds. Every number here is my pick unless marked.

## Why (the gaps they fill)
- **The tilapia carries the whole chain alone.** Catfish and grown crocodiles eat only tilapia and catfish; with
  breeding on, it is the kind most likely to be fished out or to crowd the river.
- **Young crocodiles eat nothing.** Hatchlings and teens are left out of the hunt today (`CrocodileEntity` hunt goal
  skips `life().isYoung()`). Real hatchlings live on frogs, tadpoles, small fish and insects: these two are their
  first meals.
- **Later hunters need small prey:** the tigerfish, kingfishers and herons, the otter (frogs), all on the master list.

## The river barb (small schooling prey fish; generalized, inspired by African barbs and robber fish)
- **Look (to draw from the user's picture):** slim silver body, a dark spot at the tail's base, the tail fin's tips
  red, small orange-edged fins, big eye. Grown 0.25 blocks long (the tilapia 0.6), each its own size 0.85 to 1.15.
- **Where:** shallow, slower water 1 to 3 deep: the river's edges, sand-bank shallows, oxbows and pans. The tilapia
  keep to the deeper beds, so the two are seen side by side but not mixed.
- **Schools:** 8 to 14 fish, much tighter than the tilapia's, turning all at once. A turning fish tips its side to the
  light and **flashes silver** (a brighter flank while it banks), so a turning school sparkles.
- **Danger, two real school moves (one shared scatter, `HuntTilapiaGoal.scatter`, reused):** a strike into the school
  makes it **burst outward** (the "flash expansion") and re-form a few blocks on; a slow hunter or a player swimming
  through makes it **part round them and close behind** (the "fountain").
- **Food: no hunger, no blocks eaten (fewer rules).** They rise to sip at the surface at dawn and dusk (tiny rings on
  the water) and pick at algae strands for looks only. The algae stays the tilapia's, so barbs can never strip a
  river bare.
- **Eaten by:** young crocodiles (a hatchling snaps sideways at a barb passing the crèche shallows; teens too; a barb
  feeds a young crocodile 10 minutes), catfish (a snack between tilapia, fed 2 to 4 minutes). Grown crocodiles ignore
  them (too small to bother). Later the tigerfish and birds.
- **Breeding (the animal life kit):** in the rains, at dawn after rain, a school gathers over a thick algae mat in the
  shallows, the fish shiver side by side and leave a sprinkle of tiny eggs on the mat (the catfish's eggs-on-algae
  way with its own smaller look; tilapia and crabs eat them as they eat the catfish's). Hatch in a day into fry 0.08
  blocks that hide in the shallowest water, grown in 3 days. Every 2 days, clutch 10 to 16, local limit 30, no care.
- **Spawning:** its own group `antcolonies:river_school`, cap 40 round a player, kept within 96, gone past 128;
  groups of 8 to 14 (`getMaxSchoolSize` 14).

## Fewer tilapia (the user's ask)
- `antcolonies:river_fish` (tilapia and catfish) cap 60 -> **36**; tilapia spawn groups 3-6 -> **2-4**; the tilapia's
  local breeding limit 40 -> **20**. Catfish unchanged (weight 3, alone, local limit 6).
- So round a player: about 20 tilapia, 3 to 6 catfish, up to 40 barbs, up to 12 frogs and tadpoles, at most 5
  crocodiles. Fish in all about 76 against 60 today; the barbs are small and simple, and the soak measures the
  server's time per tick before and after. If it costs too much, the barbs' cap comes down first.
- Grown crocodiles and catfish still hunt tilapia; with fewer, they hunt a little less often. Neither ever starves
  (both swim on when nothing is near), so nothing crashes.

## The river frog and its tadpoles (generalized, inspired by African river frogs)
- **Look (to draw from the user's picture):** olive-brown with dark blotches, a pale stripe down the middle of the
  back, long barred hind legs, a white throat that swells into a ball while calling. Grown 0.3 blocks long sitting
  (bigger than life, as the tilapia). Tadpole 0.12 blocks; a froglet stage between (back legs, then front legs, the
  tail shrinking).
- **Where:** the waterline of pans, oxbows, slow river edges and sand banks: sitting at the edge facing the water, or
  floating in the shallows with only the eyes and nose up.
- **Escape (the real frog's one move):** anything big within 4 blocks (a player, a monitor, a crocodile) and it makes
  one long leap into the water with a plop and a ring, swims down to the bed for a few seconds, then its eyes break
  the surface again; it climbs back out later.
- **Calling:** from dusk into the night the males sit at the edge and call, the throat ballooning: a short rattling
  croak (a sound made with a script, as `tools/matabele_squeak.py` made the Matabele's). One male starts and others
  answer (a chorus). **They hush when a player comes near** and start again after a few still seconds (real, and a
  good moment in play).
- **Food: no hunger (fewer rules).** At dusk a frog snaps at a gnat over the water now and then (a tiny particle bug,
  looks only). Not ants or termites: frogs eating colony insects would change the colonies' balance. **Socket for
  M9.5:** real frogs gorge on termite flights; when alates come, frogs come to the flight holes.
- **Eaten by:** young crocodiles (frogs at the edge, tadpoles), the Nile monitor (a frog on the bank, added to its
  food list beside crabs and snails), catfish (tadpoles and frogs in the water). Later herons, the otter.
- **Breeding (the animal life kit):** rainy nights in the rains (the shared season source). A calling male draws a
  female; he clasps her from behind (amplexus) and she lays a jelly clump of eggs in the shallows (a new clutch block:
  clear jelly with dark dots). Hatch in a day into a wriggling cluster of tadpoles that **graze the algae** (the
  tilapia's grazing goal, a bite rarely, so they never strip a mat): the link algae -> tadpole -> frog. Tadpoles
  grow legs on day 3 and climb out as froglets, grown 2 days later. Every 4 days, clutch 6 to 10 tadpoles (most are
  eaten), local limit 12.
- **Spawning:** its own group `antcolonies:river_frog`, cap 12, groups of 2 to 4, kept within 96. A frog sits on land
  and in water, so its spawn check takes the water animals' answer (`checkSpawnObstruction`, BUGS 218).
- **The hard part, said plainly:** hopping on land and the jump into the water is new movement for our animals (the
  monitor's water entry took long work). Its own clips and checks, offline and frame by frame in the game, as the
  rule asks; two strikes, then rethink.
- Not the other frogs on the master list (the snake-necked frog of the Matabele nests, the bullfrog, the foam-nest
  tree frog): those stay their own animals. Minecraft's own frog does not spawn in savannas.

## Steps
0. **The user's pictures, the briefs, the outlines (the model workshop, `docs/plans/model-workshop.md`).** The user
   makes two inspiration pictures, `references/inspiration/barb.png` and `references/inspiration/river_frog.png`
   (side, top, front, back on a plain background, a posed picture, the length written on it; the frog's picture with
   a tadpole and a froglet beside it). Then for each: brief, outline, plain 3D shape, detail, paint, animations, each
   with the user's OK (barb clips: swim, turn, sip, scatter, death; frog: sit, hop, leap, swim, float, call, snap,
   clasp, death; tadpole: wriggle, graze).
1. **The barb in the game, and fewer tilapia.** Entity, school, flash, scatter and fountain, sipping; the spawning
   check list (group, spawn check, quiet-savanna gate, a `worldWouldSpawn` test, the groups table); the tilapia's
   numbers; young crocodiles and catfish eat barbs. Tests in `river`: barbs spawn by themselves on shallow water, a
   hatchling crocodile catches a barb, a catfish takes a barb, the school bursts on a strike.
2. **The barb breeds** (`life`): the dawn spawning over algae, eggs eaten, fry. Tests: a school spawns and fry
   appear; room to breed stops at the limit.
3. **The frog in the game.** Sitting, the escape leap, swimming and floating, calling and the chorus, hushing, the
   sound; the spawning check list; monitor, young crocodiles and catfish eat frogs. Tests in `river`.
4. **The frog breeds** (`life`): calling, amplexus, jelly eggs, tadpoles grazing, froglets climbing out. A full run
   here (3 steps since the last).
5. **Balance:** joins the animal life cycle's step 6 soak (counts of every river kind per game day level off; time per
   tick before and after), then the user's in-game test in a new world.

## My picks the user may change
The names (river barb, river frog), every size and number above, barbs and frogs with no hunger, grown crocodiles
ignoring barbs, frogs not eating colony insects.
