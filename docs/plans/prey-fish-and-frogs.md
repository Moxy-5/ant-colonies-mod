# Small prey fish, the African bullfrog and tadpoles, and fewer tilapia (M9 river life; planned 2026-11-20, nothing built)

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
- **A far look (the user, 2026-11-20, "make it so the models become less detailed from far away"):** the cost of
  many small fish is mostly in drawing them: GeckoLib works out every box of every model each frame on the CPU (the
  tilapia has 105 boxes, the catfish 176, the crab 248, the monitor 665), so 40 barbs at tilapia detail would be about
  4,000 boxes a frame. The thinking each fish does on the server is small beside it (about 0.02 ms each, measured for
  the insects: 16 more fish about 0.3 ms of a tick's 50). So past a set distance (my pick: 12 blocks, where a
  0.25-block fish is a few dots on the screen) the barb is drawn with a far model of a few boxes and the same bone
  names, so its swim clip still moves it; beyond Minecraft's own entity draw distance it is not drawn at all. Built
  once as a shared renderer helper (`FarLook`: a far model per animal and its distance), so the tilapia, catfish,
  crab and monitor can take it up later with only a far model each. Checked in the game: the switch must not pop
  (the barb's far model is made from its near one, same outline and colours).

## Fewer tilapia (the user's ask; the number the user's, 2026-11-20, "lower the tilapia to 45")
- `antcolonies:river_fish` (tilapia and catfish) cap 60 -> **45** (the user's number, in place of my 36); tilapia
  spawn groups 3-6 -> **2-5**; the tilapia's local breeding limit 40 -> **30** (my pick, kept in step with the cap).
  Catfish unchanged (weight 3, alone, local limit 6).
- So round a player: about 30 tilapia, 3 to 6 catfish, up to 40 barbs, up to 12 bullfrogs and tadpoles, at most 5
  crocodiles. Fish in all about 85 against 60 today: the barbs are small, drawn with the far look past 12 blocks, and
  the soak measures the server's time per tick before and after. If it costs too much, the barbs' cap comes down first.
- Grown crocodiles and catfish still hunt tilapia; with fewer, they hunt a little less often. Neither ever starves
  (both swim on when nothing is near), so nothing crashes.

## The African bullfrog and its tadpoles (the user, 2026-11-20: "I was thinking of the African bullfrog"; was a generic river frog)
The user's picture: `references/inspiration/bullfrog.png` (views file beside it; the front feet are drawn wrong, the
user: a detailed webbed foot model comes later). Fun first, inspired by the real bullfrog's big character.
- **Look (from the picture):** a huge round frog, broad flat head, olive green with darker blotches and pale patches,
  a pale cream belly with orange-yellow at the sides and in the armpits, big golden eyes set high. Real feet for the
  later foot model: the front feet plain fingers, the hind feet webbed with a hard spade on each heel for digging
  backward into the ground.
- **Size (the user, 2026-11-20, "males 0.80 females 0.65"):** males 0.8 blocks long, females 0.65, each 0.9 to 1.1
  of that (my pick); froglets 0.25, tadpoles 0.15 (my picks). The sheet's 2.5 blocks were turned down: bigger than
  the catfish. Real males are much bigger than females, rare among frogs. The model is made at the sheet's 2.5 blocks
  (40 model pixels, room for detail) and drawn at 0.32 of that for a male.
- **Where:** pans, oxbows, the floodplain's shallows and slow river edges. By day it sits **half dug into wet mud or
  sand at the edge**, only its back and eyes showing (it shuffles backward into the ground with its hind feet); it
  comes out at dusk and in rain.
- **An ambush eater (its big character):** it sits still and lunges with a huge gape at small things passing within
  about 1.5 blocks: barbs in the shallows, snails, baby crabs, tadpoles (its own kind's too: real bullfrogs are
  cannibals). Then it is fed a while; with nothing near it waits and never starves. Not ants or termites now (that
  would change the colonies' balance). **Socket for M9.5:** real bullfrogs gorge on termite flights.
- **Bold, not shy:** a player coming near makes it puff up and gape; one who keeps coming gets a barking lunge and a
  small bite (my pick: 1 heart). It leaps into the water only from something big that can eat it (a monitor, a
  crocodile).
- **Calling:** from dusk and on rainy nights the males call, the throat swelling: a deep lowing whoop, like a
  distant cow (a sound made with a script, as `tools/matabele_squeak.py` made the Matabele's), others answering.
- **Breeding (the animal life kit):** in the rains, after rain, males gather in a shallow pan or edge, call and
  wrestle (shoving and biting) for the middle; a female comes to the male holding it; he clasps her from behind
  (amplexus) and she lays a film of eggs in the shallow water (a new clutch block). They hatch in a day into a dense
  black school of tadpoles that **graze the algae** (the tilapia's grazing goal, a bite rarely, so they never strip a
  mat): the link algae -> tadpole -> frog. **The father guards the school** (real, and the bullfrog's most famous
  habit): he stays with it and bites anything that comes near, the player too. Tadpoles grow legs on day 3, climb out
  as froglets and are grown 3 days later. Every 4 days, clutch 8 to 12 tadpoles (most are eaten), local limit 12.
- **Sockets for the seasons (M9.5, Serene Seasons):** the dry season spent buried in a cocoon underground, out with
  the first heavy rains; the father digging a channel with his hind feet to lead his tadpoles from a drying puddle to
  deeper water (needs pans that dry, which waits for the seasons).
- **Eaten by:** young crocodiles (tadpoles and froglets), the Nile monitor (bullfrogs on the bank, added to its food
  list beside crabs and snails), catfish (tadpoles). Later the marabou stork and the herons.
- **Movement:** mostly a heavy crawl and short hops on land; a long leap into the water from danger.
- **Spawning:** its own group `antcolonies:bullfrog`, cap 12, groups of 1 to 3, kept within 96. It sits on land and
  in water, so its spawn check takes the water animals' answer (`checkSpawnObstruction`, BUGS 218).
- Not the other frogs on the master list (the snake-necked frog of the Matabele nests, the foam-nest tree frog):
  those stay their own animals. Minecraft's own frog does not spawn in savannas.
- **The hard part, said plainly:** hopping on land and the jump into the water is new movement for our animals (the
  monitor's water entry took long work). Its own clips and checks, offline and frame by frame in the game, as the
  rule asks; two strikes, then rethink.
- **What Minecraft's own frog teaches (read from its code, 2026-11-20, the user's question):**
  - **Its jump clip is not worth copying:** it is one still pose held half a second (body tipped up 22.5 degrees, front
    legs swung forward, hind legs back); the whole leap is the game throwing the body along an arc. Ours gets a real
    clip: crouch, the hind legs kick out straight, the body stretched in the air with the front legs reaching, then
    landing on the front legs and folding up; its length stretched to the flight time of each leap, as walks follow
    their speed.
  - **Its jump aiming is worth using as it is:** Minecraft's `LongJumpUtil.calculateJumpVectorForAngle` takes a
    landing spot, tries launch angles of 65 to 80 degrees, works out the speed, and checks the whole arc is clear of
    blocks before the frog leaves the ground. Our escape leap calls it to pick a clear arc into the water (no frog
    smacking into a bank's lip), and falls back to a nearer spot or a plain dive in when no arc is clear.
  - **Land and water in one:** Minecraft's frog walks and swims with one amphibious route finder
    (`AmphibiousPathNavigation`) and a swimming move control (`SmoothSwimmingMoveControl`), so it never has to be
    switched between a land body and a water body; our frog starts from those (the monitor's own climbing legs are
    for its size and the bank's walls, which a frog does not need). Its floating pose in water and its 3-second croak
    with the throat swelling are its other clips; ours do the same things in our own style.

## Steps
0. **Pictures, briefs, outlines (the model workshop, `docs/plans/model-workshop.md`).** The bullfrog from the user's
   picture (`references/inspiration/bullfrog.png`, once its size is picked); the barb designed by Claude (the user,
   2026-11-20, "I let you design the small fish"): Claude draws its sheet, `references/inspiration/barb.png`. Then for
   each: brief, outline, plain 3D shape, detail, paint, animations, each with the user's OK (barb clips: swim, turn,
   sip, scatter, death; bullfrog: sit, dig in, crawl, hop, leap, swim, float, call, puff up, lunge, clasp, wrestle,
   death; tadpole: wriggle, graze; froglet). The detailed webbed hind foot is a later job (the user).
1. **The barb in the game, and fewer tilapia.** Entity, school, flash, scatter and fountain, sipping, the far look;
   the spawning check list (group, spawn check, quiet-savanna gate, a `worldWouldSpawn` test, the groups table); the
   tilapia's numbers; young crocodiles and catfish eat barbs. Tests in `river`: barbs spawn by themselves on shallow
   water, a hatchling crocodile catches a barb, a catfish takes a barb, the school bursts on a strike.
2. **The barb breeds** (`life`): the dawn spawning over algae, eggs eaten, fry. Tests: a school spawns and fry
   appear; room to breed stops at the limit.
3. **The bullfrog in the game.** Sitting dug in, crawling and hops, the leap into the water, swimming and floating,
   the ambush lunge, puffing up and biting a player, calling, the sound; the spawning check list; monitor, young
   crocodiles and catfish eat bullfrogs and tadpoles. Tests in `river`.
4. **The bullfrog breeds** (`life`): calling and wrestling, amplexus, eggs, the tadpole school guarded by its father,
   froglets climbing out. A full run here (3 steps since the last).
5. **Balance:** joins the animal life cycle's step 6 soak (counts of every river kind per game day level off; time per
   tick before and after), then the user's in-game test in a new world.

## Progress
- 2026-11-20: the barb's outline approved by the user ("the fish looks perfect"). The bullfrog's outline was hard to
  judge as flat boxes over an AI picture drawn a little in perspective (the user), so its plain 3D shape was built
  next (`tools/bullfrog_models.py`, v4) and set beside the sheet's side, angle and front views; waiting for the
  user's look.
- 2026-11-20, the remake (the user, with two real photos: "all frogs' mouths have this V shape", "the 2 front legs
  aren't connected", "it may be better for you to remake the model instead of trying to patch it"): the AI sheet's
  proportions were the wrong start (a tall box head, a straight mouth), so the model was rebuilt from the photos
  (`references/inspiration/bullfrog_photo_side.png`, `_front.png`), read on a model-pixel grid: low and long (half as
  tall as long), the head a rounded V from above with the mouth sloping from the snout tip down to its corner (the
  grumpy frown from the front), eyes high and close on top with lid ridges, eardrums, the arms down from under the jaw
  corners with the elbows back, the haunches folded at the sides; every leg a chain of turned round pieces from inside
  the body. v7, 405 boxes (round pieces in one-pixel slices for this small animal). The AI sheet stays for the colours.
- 2026-11-20, the eyes (the user: the shape "looks perfect", the eyes are on the sides, the front photo's forward
  look being its angle): moved to the upper sides of the head looking out, measured on the side photo (3 pixels long,
  2.5 tall, middle 16.3 high and 7 behind the snout), a pale iris with a wide dark pupil, a low brow knob over each,
  the eardrum behind and below, the nostrils on the snout's upper sides (v9, 363 boxes).
- 2026-11-20, the eyes at an angle (the user: "frog eyes are more complicated to put in Minecraft because they're at
  an angle, perfectly placed to look in all directions"). Rethought: both earlier tries assumed an eye is a face
  pointing along one axis (forward, then sideways), and Minecraft's boxes only face along the axes. Now each eye is its
  own bone (`eye_left`, `eye_right`), built pointing up from its base on the head's top (5.4 out from the middle, 7
  behind the snout) and turned 40 degrees out and 28 toward the snout: a short rounded eyeball whose flat face carries
  the pale iris and a wide pupil running front to back, a skin lid over its back edge (the brow), skin round its base.
  Seen from the front the pupils show under the brows (the front photo's look), from the side the pale eye with its
  pupil sits up on the head (the side photo). Own bones also let the eyes sink into the head when swallowing (real
  frogs pull their eyes down to help push food down) and blink, in the animation stage. v13, 369 boxes. Tried on the
  way: a stepped dome of round slices (read as a white pyramid), then too far out (horns from the front).
- 2026-11-20, **the body approved by the user** ("I really like the body", v13 without its eyes). **The eyes: fun
  first, not realism** (the user: "for the eyes it will be better to stray from realism and just do whatever looks
  good in Minecraft"); they get their own session, starting from v13's angled eye bones.
- 2026-11-20, **the eyes started over, Minecraft style** (the user: "throw out all realism ... remake them to be more
  like Minecraft with 3 options", and every earlier eye try deleted, nothing taken from it or any picture): straight
  square boxes on whole pixels, never tilted, the eye painted on as pixel art (white, a square black pupil, a green
  lid row); `EYE_LOOKS` in `tools/bullfrog_models.py`. A pop-up cubes on the head's top (v14), B side cubes poking
  out of the head's sides (v15), C painted on a flat plate under a square brow (v16); pictures in
  `references/workshop/bullfrog/eyes_minecraft_options.png`. Waiting for the user's pick.
- 2026-11-20, **the user picked C, painted on** ("exactly what I wanted", kept as v16, now the maker's default);
  asked for a little more life and detail, less blocky lids: C2 (v17) adds a glint in the pupil, a dark upper lid
  line, a lighter lower lid rounding the eye, and a brow tapering at its outer edge. Waiting for the user's look.
- 2026-11-20, **that C2 deleted** (the user: the detail was copied onto the front and the side face as if each were
  its own eye). **Lesson: an eye that wraps a box's corner is drawn as one picture across both faces** (`strip` in
  `EYE_LOOKS`), never the same art on each face. C2 redone so (v17): one pupil across the corner, one glint on the
  corner itself (seen from ahead, the side and between), lid lines unbroken round it, the brow tapering. C stays v16
  and the default. Waiting for the user's look.
- 2026-11-20, **the eyes approved by the user: C2** ("YES that's perfect", v17), now the official model (the
  maker's `EYE_LOOK`). Next for the frog: the workshop's paint stage, then its clips.
- 2026-11-20, **the paint** (the user: "I made the version of the eye I want ... we can start painting"): `PAINT` and
  `paint_colour` in `tools/bullfrog_models.py`, read off the side photo, Minecraft style (one texture pixel a model
  pixel, a little noise on each): a dark olive back with dark wavy dashes running front to back in rough rows (the
  photo's ridges) and a few pale warts; a brighter green head; a yellow line along the upper lip over a dark mouth
  line that follows each slice's own jaw edge (a smooth painted line crossed the slices' steps and showed twice);
  the jaw and throat yellow mottled with olive; the low flanks yellow-green to yellow, orange behind the arm and in the
  groin; a cream belly; barred legs, pale toes; a tan eardrum. The eyes keep their approved art. v20 (v18, v19 on the
  way). Waiting for the user's look. My idea for later, not built: a browner, drabber look for females (real females
  are duller than males; the life kit already has sexes).
- 2026-11-20, **the head blended in** (the user, on v20 from above: "the change from dark green to light green looks
  odd maybe tone back the dark green"): the trouble was the hard edge more than the dark, the head a flat lighter green
  starting at the head bone with no marks. Now one skin colour for the back, head and eye plates (`_top_skin`), the back
  a touch lighter, the head's green almost the back's (its flat top catches the light and read pale), and the dark marks
  thin out over the shoulders instead of stopping. v23 (v21, v22 on the way), `painted_blend.png`. Waiting for the
  user's look.
- 2026-11-20, **the paint approved by the user** ("Looks good", on v23): the paint stage is done. Next for the frog:
  the workshop's last stage, its clips (idle with the throat pumping, walk, the lunge-hop, swim, float, call, bite).
- 2026-11-20, **the clips** (the user: "Start animations"): 13 clips in `tools/bullfrog_models.py` (`clips()`, written
  into the savanna mod by `python tools/bullfrog_models.py game`; no entity uses them until step 3): idle (throat
  pumping, a blink pulling the eyes down), walk (the crawl, one leg at a time), hop, leap (into the water, holds), swim
  (the frog kick), float, call (the throat swelling into a big yellow pouch under the chin), puff (the threat), attack
  (the ambush lunge: gape, snap, the hands pushing the food in, eyes pulled down to swallow), death (onto its back),
  dig_in, dug, dig_out. Made the monitor's way: each hand and foot placed, the leg bent to reach it, each joint a real
  hinge. The inside of the mouth painted pink for the gape (v24). Checked offline by the new `tools/frog_check.py`
  (parts under the ground, planted feet sliding, jumps, joint turns, film strips in `references/anim_lab/bullfrog/`):
  nothing left under the ground but a moment's graze (the hop's toes 2 px as the legs fold; the death roll's shin 4 px
  on its side), the planted feet slide back at one speed, the fastest turns are real quick swings (an arm swept back in
  a tenth of a second), smooth key to key. **For the game step (3):** the game must launch the hop and leap as the kick
  starts and land them as the clip's flight ends (the checks assume it: the hop 0.12 to 0.6 of the clip, the leap 0.12
  to 0.7), stretching the clip to the real flight; the walk's planted feet match 5.1 model pixels a second (0.10 blocks a
  second for a male: a slow crawl; the game scales the clip by speed); the leap ends with the feet turned 160 degrees,
  so its change to the swim must be checked for a spin. **The in-game half of the hard rule (frozen frames from three
  sides) waits for step 3**, when the frog has a body in the game. The versions page now plays every clip (a Clips row,
  slow motion, the hop's arc, the water for the swim and float). Waiting for the user's look.
- 2026-11-20, **knees out** (the user: in the walk, the puff and the swim "the knees or legs go inside of the model";
  picked idea 1 of three): measured first. The elbows stayed out; the hind knees sank in up to 5.2 px (swim), 2.5
  (walk), 0.6 (puff, attack). Two causes: the thigh is built half inside the body (the hip deep in, the knee's middle
  right on the skin: fine sitting, no room to spare), and the clips bent each knee the way it bends at rest against the
  hip-to-foot line, which swung it into the flank as a foot came forward or up. Fix (clips only, the approved model
  unchanged): the knees, and the elbows a little, are pushed out to the side more the further a foot or hand moves
  from where it rests (`KNEE_OUT`, `ELBOW_OUT`, `KNEE_EASE`), so the sitting pose stays exactly the model's (pushing
  out always raised the haunch's round end even sitting); the death roll stays limp. Now no knee or elbow sits deeper in
  than at rest in any clip; `tools/frog_check.py` measures it every run (flags over 0.5 px; the dig clips and the death
  roll, tucked in on purpose, left out). Idea 2 (the haunch moved 1.5 px out in the model) kept in reserve if the
  haunch still looks swallowed. Waiting for the user's look.

## My picks the user may change
The barb's name and look, every size and number above (the bullfrog's size is the user's call), barbs with no hunger,
grown crocodiles ignoring barbs, the bullfrog's bite of 1 heart, bullfrogs not eating colony insects.
