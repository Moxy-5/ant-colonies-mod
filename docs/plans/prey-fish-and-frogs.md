# Small prey fish, the African bullfrog and tadpoles, and fewer tilapia (M9 river life; planned 2026-11-20; steps 1 and 3 built 2026-11-20)

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
- 2026-11-20, **the knees-out fix approved by the user** ("yes thats perfect"). Next, the user's ask: the iconic tongue,
  detailed, and the inside of the mouth smoothed a little.
- 2026-11-20, **the tongue and a smooth mouth** (v25): the inside of the mouth was the stepped tops of the 2-pixel
  slices; now a flat floor plate on the jaw and a roof plate on the head, each tilted along the mouth line, pink with wet
  flecks, hidden 0.9 px inside the shut mouth. The tongue (three bones: root, middle, sticky pad) lies folded back on
  the floor, fixed at the front as a real frog's; pink with a darker groove down the middle, paler edges, the pad deeper
  rose with wet shine and a notch at its end. New clip `tongue` (0.7 s; for small prey close by): the mouth opens, the
  tongue flips out over the jaw stretching 2.6 times, the pad slaps down 16 px past the snout (0.3 of a block for a
  male), holds, flips back in, the mouth shuts and the eyes pull down to swallow. My addition, real: the bullfrog's three
  bony points on the lower jaw (its odontoids, what gives its bite its grip), shown when the mouth opens. The attack's
  gape now shows the smooth floor, the tongue and the points. `frog_check` checks the mouth parts hide in the shut
  mouth and where the pad lands. Not smoothed: the jaw's outer edge still shows its slice steps when open (it is the
  same stepped skin as the whole body). Waiting for the user's look.
- 2026-11-20, **the user's mouth changes** ("its not realistic but it just look better in game"; v26, 389 boxes):
  no white flecks in the mouth or on the tongue, the three bony points taken out, and the tongue's root moved to the
  back of the mouth (2.5 px from the corner): it lies forward along the floor, pad at the front, and in the `tongue`
  clip shoots straight out of the mouth (stretching 3.9 times, the mouth open 38 degrees) instead of flipping over the
  jaw; the pad lands on the ground 18.5 px past the snout. Waiting for the user's look.
- 2026-11-20, **the tongue approved by the user** ("Looks great", v26): the bullfrog's model, paint and clips are
  done offline. Left: the in-game frame check of every clip once the frog is in the game (step 3).
- 2026-11-20, the user: two little pink slits at the mouth's sides. Cause: the jaw's top face is painted as the
  mouth's floor, and in some head slices the upper lip's box was a quarter pixel narrower than the jaw's under it, so a
  strip of the floor showed. v27: the upper lip is never narrower than the jaw under it (`body_slices`), the user's own
  idea ("cover them with the top lip"); the shut mouth clean from the front, side and three quarters, the open mouth
  and tongue unchanged; still 389 boxes. Picture `references/workshop/bullfrog/lip_v27.png`.
- 2026-11-20, the user: **the tongue reaches 2 blocks** (up to 2 blocks high, aimed at the fly), for the fly clouds
  that hover about 2 blocks up and sometimes 3; drawn as a rope by the renderer, the clip keeping the mouth, head and
  swallow (`docs/plans/fly-clouds.md`, "Height and the long tongue"). Built with the bullfrog in the game (step 3).
- 2026-11-20: the barb's plain 3D shape (`tools/barb_models.py`, v2, 38 boxes of the 40 budget, 8 bones: body,
  head, tail stalk, tail fin, back fin, two breast fins), built from the approved outline and the sheet's head steps:
  2-pixel slices of two boxes each (a rounded spindle from the front), eyes set into the head's sides, a swallow tail
  of three steps a lobe. Against the sheet: side 93% covered / 90% inside, from above 86 / 91, front 67 / 98 (the
  sheet's front view is drawn 7 wide but its top view 5; the approved outline follows the top). Waiting for the
  user's look (`references/workshop/barb/plain_vs_sheet.png`, versions page).
- 2026-11-20, the user on v2: "half the eyes are covered" and "the top left of them don't connect", "make it plumper so
  there's more space for the eyes" (the eyes themselves look good). Cause: each eye sat across the edge of two head
  slices (the wider one covered its back half) and rose above the round flank beside the narrow ridge (its top corner
  hung in the air). v3: the body as wide as the sheet's front view (7 at the deepest, the head broad back from the
  eyes); each eye one block inside a single head slice, from the ridge out a quarter pixel past the cheek, its outer
  face the gold ring and pupil and its other faces head skin. Side 93 / 90, front 87 / 94, from above 94 / 72 (now
  wider than the sheet's top view, on purpose). Still 38 boxes. Waiting for the user's look
  (`references/workshop/barb/plumper_v3.png`).
- 2026-11-20: **the barb's v3 shape approved by the user ("that's perfect, color it")**, so the detail stage was
  skipped. v4 painted from the design sheet (`paint()` in `tools/barb_models.py`): the olive-grey back, a paler upper
  flank, silver sides with the thin dark line along the middle running into the dark spot at the tail's base, shaded
  silver low down, the white belly; the gill cover's edge, the mouth line, the gold eye with its black pupil; pale grey
  fins with orange tips (back fin, belly fins, the fin under the tail, breast fins) and the forked tail's red tips.
  The flash (the flank white as the fish turns) is a second look, `LOOKS['flash']`, for the game stage. Waiting for
  the user's look (`references/workshop/barb/painted_v4.png`).
- 2026-11-20: **the barb's paint approved by the user ("that's perfect, start the animations").** Clips in
  `tools/barb_models.py` (`clips()`; game files by `python tools/barb_models.py game`, no entity uses them yet): idle
  (hanging still, breast fins sculling, a bob), swim (0.45 s a beat, the wave carried back along the body, the tail fin
  most and last), turn_left and turn_right (played once on a hard turn: the body curves round it and rolls 38 degrees
  so the flank catches the light; the game swaps in the flash look `barb_flash.png`), sip (nose up 28 degrees, a gulp,
  back down), scatter (the fast start: a C bend, the tail whipping back, fins flat), flop (stranded, on its side,
  curling up) and death (the wave dies as it rolls onto its side). Checked offline with `python tools/anim_lab.py all
  barb` (film strips from four sides, every frame's jumps and turns): flop and death clean on the ground; the water
  clips dip the tail a fraction of a pixel under the model's floor, which has no meaning for a swimming fish. The
  in-game frame-by-frame films wait for the barb's entity (step 1). Overview `references/workshop/barb/clips_v4.png`.
- 2026-11-20, the user: "the mouth opening for algae". v6: the snout's last 2 pixels split at the mouth line into the
  upper lip and a lower jaw (its own bone, hinged at x 27.5, opens 40 degrees: a first try with a 1-pixel jaw opened
  to a speck, too small to read on a quarter-block fish), with the dark red inside of the mouth hidden behind the lips
  while shut; 39 boxes, 9 bones. New clip `nibble` (1.8 s, loops while grazing: nose down 30 degrees to the algae,
  three pecks, the mouth opening as the head reaches in, snapping shut on a strand and tugging back; the snout just
  meets the bed); the `sip` now opens the mouth at the gulp. Checked offline (`anim_lab.py all barb`). Picture
  `references/workshop/barb/mouth_v6.png`.
- 2026-11-20, the user: "put everything in game the flies, bullfrog and fish". **Step 1 built:** the barb in the game
  (schools of 8 to 14 in the shallows, the flash on hard turns, the burst on a strike, sips at dawn and dusk and nibbles
  on algae), the tilapia cut to 45 (groups 2 to 5), hatchling and teen crocodiles and catfish eating barbs; its spawning
  check list done (group `river_school`, cap 40). The far look is left for later (39 boxes; 40 barbs cost about a third
  of the tilapia's drawing). **Step 3 built:** the bullfrog in the game (hops, swims and floats, leaps into the water
  from a monitor or grown crocodile, lunges at barbs, snails and young crabs, the 2-block tongue at fly clouds drawn as
  a rope, puffs up and bites a player for 1 heart, digs in from 10:00 to 14:00 on dry days, males call at night and in
  rain), spawning check list done (group `bullfrog`, cap 12); the monitor eats bullfrogs. Not yet: young crocodiles and
  catfish eating frogs (no frog in their food yet), barbs and young crocodiles eating landed flies, tadpoles (step 4).
  Changes in `docs/HISTORY.md`. Not tried in-game by the user yet.
- 2026-11-20, the user: the in-game eyes are not their design. **Fixed:** GeckoLib drew each box's paint the other way round from the viewer (east and west swapped, every face's picture back to front), so the eyes' art sat on the faces inside the head and the front's was turned over. The frog's export now passes its faces through `model_parts.game_faces`; texture (v27 byte for byte), shape and clips unchanged. Filmed in the game (`tools/film/bullfrog_eyes.film`) and put beside the v27 viewer from the same cameras (`tools/game_compare.py`): the eyes match the approved C2 look pixel for pixel from the side, three quarters, front and the other side; the idle blink, the attack's swallow and the tongue move them right. Picture `references/workshop/bullfrog/eyes_game_vs_v27.png`. BUGS 245 (the other animals' smaller swaps left open).
- 2026-11-20, **the young and the sexes, design started** (the user: "lets get started on designing the tadpole and the
  baby silver barb and female and male models"). Read as both species' sexes, as both breed (my reading). All my picks,
  fun first, inspired by real barbs and bullfrogs; nothing in the game yet:
  - **Barb male and female** (`tools/barb_models.py`, `PLUMP` and `STYLES`): the approved v6 is the male (proved
    unchanged); the female (v7) is fuller, her belly dropped 1.6 px and widened round the deepest part (real female
    barbs are rounder with eggs), the belly fins moved down with it, her fins amber rather than orange; a male in
    breeding colours for the rains (v8): deeper orange-red fins, scarlet tail tips, a coppery flush above the side
    line, a few white bumps on the head (real breeding barbs grow them). Each 39 boxes. Picture
    `references/workshop/barb/sexes_v6_v7_v8.png`.
  - **Bullfrog female** (`FEMALE` in `tools/bullfrog_models.py`, v29): the same model and marks, olive-brown and duller,
    a cream throat (the male's yellow); drawn 0.65 blocks to his 0.8 (the user's sizes). Picture
    `references/workshop/bullfrog/sexes_v28_v29.png`. Her eye plates keep the approved eye art's green lid row.
  - **Barb fry and tadpole**, new models through the workshop: design sheets drawn by Claude (`tools/young_sheets.py`
    -> `references/inspiration/barb_fry.png`, `tadpole.png`), briefs (`*.brief.md`), outlines
    (`references/outlines/barb_fry.json`, `tadpole.json`; side, top, front 100/100, 95/90, 91/100 and 97/99, 99/100,
    96/100). The fry: 12 px, drawn 0.08 blocks, a glassy see-through body, big dark eyes, the silver swim bladder and
    gold gut showing, the barb's bone names so its swim clips drive it, under 12 boxes; the fry look for its first day,
    then the grown model drawn small (my pick). The tadpole: 18 px, drawn 0.15 blocks, near black, a flat oval body,
    small eyes high on the sides, the mouth underneath, a grey belly with the gut showing, a tail 1.6 times the body
    with speckled fins, hind legs as their own bones shown from day 3, under 30 boxes. Waiting for the user's look at
    the sheets and outlines.
  - Tools: `workshop.py snapshot --set NAME=value` (a slider number or `WORKSHOP_LOOK=<look>`, for keeping a sex as a
    version); the outline page draws small animals bigger; `barb_sheet.draw_map` takes its colours.
- 2026-11-20, **the fry's and the tadpole's plain 3D shapes** (the user: "Build the 3D shapes"; the sheets OKed by
  going on). `tools/barb_fry_models.py` (v1, 12 boxes, the grown barb's bone names so its clips move it: the head one
  box with the eyes painted on its sides, the body two boxes narrowing, folds on the back and belly, a small forked
  tail, tiny breast fins; against the sheet side 98 / 100, from above 88 / 87, front 79 / 97: a little narrower than
  the sheet's 3-pixel front on purpose, so the head and body are one width from above). At its real 0.08 blocks it
  is about one Minecraft texture pixel long, so it is kept a simple bar of glass. `tools/tadpole_models.py` (v1, 25
  boxes; v2 with the day-3 hind legs, 29): the body one-pixel slices of two boxes each (a flat oval from the front and
  above), the eye and the mouth underneath painted, the tail three bones (`tail_1` to `tail_3`) of muscle with thin
  fins above and below, the hind legs small buds at the tail's root on their own bones (`LEGS` 0 leaves their boxes
  out); side 96 / 97, from above 94 / 88, front 86 / 96. Plain colours, the sheets'. Pictures
  `references/workshop/barb_fry/plain_v1.png`, `references/workshop/tadpole/plain_v1_v2.png`. Waiting for the
  user's look; next the paint (my pick for the game: the fry drawn see-through).
- 2026-11-21, **the fry and the tadpole painted** (the user: "they look good we can start painting"; Neon busy
  with the hippo, so done in a worktree of its own, only the two makers touched, no shared tool). Tadpole v3 (v4
  with the day-3 legs): the sheet's near-black olive with pale and gold flecks, small eyes with a gold ring, a grey
  speckled belly with the coiled gut, the dark beak and pale lips underneath, a mottled tail muscle, clear fins
  with dark speckles, the legs' toe tips dark. Fry v2: glassy body, big dark eyes with one glint (showing from above
  and the front as on the sheet), the silver swim bladder over the gold gut, the backbone showing through the clear
  rear body, dark dots along the back and under the belly, the tail spot starting. My pick for the game: the fry's
  glass, belly and fins drawn see-through (gut, bladder, eyes, dots solid). Pictures
  `references/workshop/tadpole/paint_v3_v4.png`, `references/workshop/barb_fry/paint_v2.png`. Next: the user's
  look, then the clips (tadpole: wriggle down its three tail bones, graze; the fry reuses the barb's).
- 2026-11-21, **the fry made cuter** (the user: "make the baby fish cuter maybe its with the eyes"). Fry v3: the
  eyes are now two bulges standing half a pixel out of the head (14 boxes), a little lower and forward, each round on
  its side (a big black pupil, a gold-silver ring, the corners the head's glass) with two white shine spots up and
  to the front, their edges dark so they show big from the front and above; a smaller chin with a tiny mouth; the
  texture at 4 pixels a model pixel so the round eye has room (64 by 64). Picture
  `references/workshop/barb_fry/cuter_v2_v3.png`.

## My picks the user may change
The barb's name and look, every size and number above (the bullfrog's size is the user's call), barbs with no hunger,
grown crocodiles ignoring barbs, the bullfrog's bite of 1 heart, bullfrogs not eating colony insects.
