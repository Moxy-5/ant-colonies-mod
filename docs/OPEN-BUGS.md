# Bugs left

Every bug still left in one list: the ones in the game graded by how much they hurt play, then the calls
waiting for you, the code review's findings that work behind the scenes, and the faults in the tools. The
tests that fail now and then are kept apart, in `docs/TEST-FLAKES.md`. **This file is written by
`python tools/open_bugs.py`; never edit it by hand.** A plain number (**306**) is an entry in
`docs/BUGS.md`; an R number (**R6-20**) is a code review finding, in full in `docs/reviews/full/` (search
for the number there).

| Part | Left |
|---|---|
| In the game: A, it breaks play | 1 |
| In the game: B, it looks or plays wrong | 34 |
| In the game: C, small or rare | 54 |
| Waiting for your call | 27 |
| Behind the scenes (code review) | 206 |
| Tool faults | 6 |
| *Test flakes, in `docs/TEST-FLAKES.md`* | *59* |

## A: it breaks play (1)

Something a player meets goes wrong in a way that matters: a crash, a freeze, a system that does not do its job.

- **306** The raids were rewritten from scratch (2026-11-24) and three long unattended runs are done: every raid ends, none hangs or crashes, and since the fix of the tail left behind (BUGS 450) the raiding colonies bring home enough to hold their size (151 termites in the last run, 23 and 57 before). Still to do: you watch raids in-game; the smaller raid bugs left are on this list.

## B: it looks or plays wrong (34)

A player will likely see it, and the game goes on.

- **176** A hurt ant carried down a wall (the way into every Matabele nest) sticks straight out from its carrier's head instead of lying along the wall.
- **183** Minecraft's own, not the mod's: flying fast over new land, the game can freeze 8 to 13 seconds near a trial chamber.
- **237** A Nile monitor diving off a bank 2 blocks high: its tail stands straight up the bank's face for about a second.
- **253** The bullfrog's feet jump at the start of its hops, its hind feet sink into the ground when it sits, and its walk flickers in between hops.
- **256** Young crocodiles draw their tails and heads deep inside blocks at steps and banks.
- **361** Matabele that chase termites into the termites' holes and tunnels during an attack can be left with no way back to the column: in the last long run 13 raiders stood in one termite hole till night, and 8 stood half a minute at one place twice (a major walled in by the termites now bites its way out). *(half fixed)* *(half fixed)*
- **378** A buffalo calf's feet slide when it runs with the herd.
- **416** Ants and termites cannot step onto a low flat piece (a lying mud-crust plate, a carpet, a bottom slab) that hangs a block over the ground; raiders told to stand on one stand 30 seconds.
- **427** The bullfrog's feet and hands whip the long way round when it climbs out of the water or starts to swim.
- **452** Some raids still reach termites they can smell but not get at (shut in under ground or wood beside an open gallery): the ants search 15 seconds and go home empty.
- **R3-38** A Matabele that lost a leg has all six again after it goes into the nest out of sight and comes out. *(half fixed)*
- **R4-18** A busy river costs frames: about 6.5 ms a frame for 103 river animals in view (it was 13.3). *(half fixed)*
- **R4-26** A bullfrog (or a land animal later) caught by a crocodile hangs upright and still while the crocodile rolls it.
- **R5-7** A short hitch as each mound of new land appears while exploring, worst for big living mounds and for old dead ones; one can go in every half second while places wait. *(half fixed)*
- **R5-14** After a player digs into a garden the colony list keeps showing comb that is gone, and the nest unlocks levels from food it no longer has; away, workers can starve beside old comb.
- **R5-19** After one `/time set day` in a world 60 days old, no dead mound wears and no dead comb rots for 60 days of play; with the daylight cycle off they never do.
- **R5-20** Round every mound that died in play, little patches of coarse dirt round the old foraging holes never green over, while the holes themselves and the mound beside them grass over.
- **R5-21** Now and then a mound of new land whose rooms are flooded (a river bank, a buried pool, a cave), or lava running into a nest.
- **R5-23** Coming back to a mound that died long ago: it stands as it was left, then drops a slice every 5 seconds, top first, with a burst of breaking sounds, until weeks of wear are done in a minute or two.
- **R5-24** A mound whose colony has been dead for 100 days stands fresh and only begins to wear on arrival; until then Matabele scouts walk out to a mound with nobody in it.
- **R5-56** At a raided feeding spot the termite workers stay in the roofed part of the gallery beside the break, some still feeding, and are stung and carried off one by one; they do not pour back down the gallery while the soldiers hold the opening, as real Macrotermes workers do. *(half fixed)*
- **R5-59** A server that gets slower beside grown termite mounds whose web has used up its length while known logs or hay lie farther out, the usual state of a grown colony. Not measured. *(half fixed)*
- **R5-60** On a grey day with no rain falling in the savanna the foraging holes open and termites walk out at noon, where the Matabele's day raids and the player find them; and there is never a rainy night with more holes open.
- **R5-61** At night beside a grown mound termites trail far across open ground away from any open hole, and lone scouts wander tens of blocks out, where the design means a tight ring of grazers round each open hole; these far walkers are the ones caught out at dawn.
- **R5-63** At dusk by a growing colony few or no holes open for minutes while the builders dig; by a grown one the holes open one after another across the land instead of together; and a hole no builder can reach leaves the holes after it shut all night, with builders standing idle.
- **R5-64** A raid at a log where the termites had stopped feeding still finds termites; at a spot with one open cell most of the termites brought out stand shut in under the wood, counted as there, with raiders pacing above them (the picture of BUGS 452).
- **R5-65** A raid whose arrival alarms the termites before the attack starts (a hole still open at dawn, a forager still out) is told nobody feeds there: the column looks round and goes home and the find is forgotten, or it attacks and the termites under the mud are not brought out for it.
- **R5-68** The same raid on the same colony is a hard fight unwatched (60% of the soldiers answer, up to 12, a quarter of them taken) and a light one watched (the one to three soldiers at the spot); and a watched raid shuts the spot for about three and a half days where an unwatched one shuts it for two.
- **R5-69** A mound that should be dead (no workers, no queen, no building, no foraging) stays listed as a living colony with slow soldiers walking its beats for good, and never becomes a dead mound for the Matabele to move into.
- **R5-73** Colonies far from the player fill up and reach the cap of 200 however bare their land, while the colony the player watches is held by its real grass and wood; after a long absence the far colonies are the big ones.
- **R5-75** A full mound keeps about 6 feeding spots at branches where 10 to 15 were chosen for a big colony, so there are fewer by-day places for the Matabele scouts to find; and its galleries stop at about 35 blocks out, not the 50 in the species file.
- **R5-80** Watching in spectator, an eaten fallen tree leaves its log-shaped mud cast standing for good, leftover plates of a filled gallery stay, every crust stays fresh (lightest colour, softest to break), pulp heaps do not sink and remains do not weather.
- **R6-20** Likely, not proven: one block you put in a Matabele nest's chimney (a torch) makes the colony lose all its rooms.
- **R8-3** Likely, not proven: with a biome mod that is a data pack (Terralith, say) its biomes may not appear at all.

## C: small or rare (54)

Hard to notice, or it needs an unusual case.

- **243** No crocodile can climb out onto a bank a full block above the water (the mod's own rivers have banks level with the water, so it is rare).
- **250** The river snail flickers between crawling and standing as it goes.
- **254** Small looks on the catfish, barb, crab and snail: the crab's planted feet skate as it walks sideways, and a few smaller ones.
- **269** A hippo walking from a level bank into deep water: its hips show inside the bank for a moment.
- **308** Small faults on the Matabele minor's body: a leg pops at the change to the fast walk, the head jumps at the top of a climb, the body dips into the roof going onto a ceiling.
- **330** A monitor can stop in front of a step for a moment when its climb is cut on its first tick (the main cause is fixed). *(half fixed)*
- **342** Giant trees in land made just before the game crashes (not a normal quit) stay frozen for good (a normal quit is fixed). *(half fixed)*
- **357** Wound care takes twice as long as the species file says: a 30-second care round takes a minute (still within real Megaponera times).
- **358** In a dead mound that keeps wearing down, the Matabele nest's entrance stays where it was, up to 5 blocks up in the air; nothing seen to break yet.
- **359** Matabele coming home rest (and are tended) at the edge of their first room instead of spreading through it.
- **385** A lioness holding or eating prey has a back paw 1 to 2 pixels into the grass.
- **426** Some ant and termite clips push feet or antennae under the floor: grooming, a bite, the termites' peek and their paralysed fall (some may be meant).
- **428** A few animal loops jump back at the end of each round (crocodile eating, crab cracking, a barb, a distichodus), and some feet hang in the air (bullfrog, hippo, lion and lioness clips).
- **432** When the game lags, an otter's block move can show late and then jump forward (it mends itself within a second).
- **453** After some attacks the raid waits 30 seconds for stung termite soldiers nobody can reach, and a rescuer can chase a hurt ant that is already walking home.
- **461** A raid that was on its way out when you left the game starts from the nest again when you come back, usually runs out of time and walks home without reaching the termites.
- **462** Walk away from a raid in the middle of its attack and the attack can last half a minute longer than its limit before the ants turn home.
- **466** When lions let a buffalo go, its tail and ears snap to a new place in a moment as it starts to get up.
- **467** At a kill, lions feeding side by side push legs and manes a little into each other, and the full ones lying nearby can lie into a feeder's tail.
- **R3-10** Big fights cost the server more than they need: a fighter with no foe near still looks round once a second. *(half fixed)*
- **R3-30** Some ants lose one try (a short pause) when they set off to go in, wait at the nest or follow the queen. *(half fixed)*
- **R4-13** Fly clouds go silent after F3+T or a new sound device, until you walk 48 blocks away and back.
- **R4-27** The termite queen's walk and turn lose their body bob.
- **R4-28** After F3+T the monitor's tail rest pose and the otter's faces stay as they were.
- **R4-30** Coming within 48 or 64 blocks of a crocodile or monitor, its whole pose snaps into place in one frame.
- **R5-10** Part of the tick time beside a big mound with many real workers, most by day. Not measured.
- **R5-12** Rarely, a stray channel through the mound; a termite crawling in it by day may be sent home as if it were outside.
- **R5-15** A small stutter on coming back to a big mound after a long time away. Not measured.
- **R5-16** A colony founded and grown far from the player keeps the founding queen's 10 eggs a day instead of reaching 80; far colonies grow more slowly than watched ones, by days.
- **R5-18** A live king in the royal chamber of a mound whose colony has died, for good; if a queen with no king is ever within 32 blocks he walks over and joins her.
- **R5-25** Rarely, an ant or small animal standing at a dead mound's foot is suddenly inside a block of soil and takes "stuck in a wall" damage; the likeliest victims are Matabele ants living in a mound that died less than about 14 days before.
- **R5-27** Today only with a spawn egg: a queen put beside a living mound, or on a mound that died a week ago, and the two fight over the same cells, block out, block in; on the dead mound the new colony's first comb turns black.
- **R5-71** A player running about on the mound while a raid breaks into a gallery 10 blocks off: the soldiers stand at the mound drumming and the breach gets only the soldiers that had heard nothing yet.
- **R5-72** A lag spike while a trouble lasts, only when soldiers that heard cannot reach it (a minor outside at night with the holes shut). Not measured.
- **R5-74** A little food from nothing where two colonies' ranges meet (mounds 42 to 82 blocks apart, each foraging out to about 56).
- **R5-76** Over many days a colony that eats many fallen branches stops spreading its galleries though its web is far shorter than its workers allow.
- **R5-77** After a Matabele raid near the galleries the termites' web stops growing everywhere for days, not only toward the raid.
- **R5-78** Two colonies' galleries joined by a piece out to a log lying between them, or a new gallery dug straight across the place a raid just hit; the upkeep never walls the joined piece off while its food lasts.
- **R5-79** A steady cost by day beside a big colony, growing with the web and the number of real builders. Not measured.
- **R5-81** Standing 70 to 120 blocks from a mound, its holes left open from the last night stay open by day, day after day; on coming within 64 blocks by day a row of workers plugs holes in front of the player, and by night tonight's holes are bitten open as the player walks up.
- **R5-83** Part of the tick time beside a termite colony, more while a trouble lasts. Not measured.
- **R5-84** Rarely, a termite put back inside a block a player placed in a gallery where a hidden termite "was"; it suffocates or walks out oddly.
- **R5-88** Over the first weeks the grazed band round a colony loses its tall dry grass; only short tufts come back.
- **R6-4** A termite soldier clamped on a raider's leg vanishes when its mound is far from every player.
- **R6-8** The ants tending the wounded are the first taken for a raid.
- **R6-9** A shut-in Matabele with a termite soldier on it bites the wall instead of fighting.
- **R6-11** In a raid a clamped ant is fetched only after the attack, so the clamp squeezes all through it.
- **R6-15** Beside a raid column you are squeaked at by two rules at once.
- **R6-16** A Matabele with no colony lives for ever; one whose colony is gone joins any colony near it.
- **R6-19** The prey pile can show fewer termites than the colony counts (after you break a heap, say).
- **R7-5** A big tree falling in sight may stutter the game (up to 2,500 pieces made in one tick; to measure).
- **R7-33** A palm's crown might blink out at the edge of the screen (never seen).
- **R9-18** When a hippo pool dries up, each hippo picks its own new spot instead of the pod going together. *(half fixed)*
- **R9-23** A bull shark that spawned in a hole too small to swim in lies still. *(half fixed)*

## Waiting for your call (27)

Code review findings whose fix changes how the game looks or plays, or a balance number, so nothing is
built until you say. Each has what happens today and my pick. Answer whenever you like: tell Claude the
number and what you want.

- **R3-20** (the insect body) The work goals run at half the speed their numbers say, and the numbers copied from them do not
  - *Your call:* The pace of work near you and away. Today a real digger bites once a second and a real forager cuts 5 seconds at a plant (their goals count every other game tick), but the colony's numbers (builders and foragers while you are away or out of sight) bite and cut twice as fast. Make the numbers work at the pace you see (away digging and cutting at half today's speed, so mounds and stores grow a little slower while you are away), or speed the real workers up to the numbers (twice as fast as what you have watched)? My pick: the numbers slow down to what you see, then I run the away tests and tell you what moved.
- **R3-21** (the insect body) The insects never ask the `mobGriefing` game rule
  - *Your call:* The mobGriefing game rule. Today termites and Matabele ants dig, plug and eat blocks whatever that rule says (the animals obey it). Keep the colonies digging and sealing their own nests with the rule off (without it the mod does not work), and let the rule only stop termites eating hay bales a player put down? My pick: yes, and I write it in the design notes.
- **R3-33** (the insect body) Goals count their own ticks at half pace, so most written times are double (the list BUGS 35 and 357 did not name)
  - *Your call:* Times written in the insects' goals. Most goals count their own ticks, which come 10 a second, so every written time is really double (a give-up of "a minute" is two, a chase of "10 seconds" is 20). Nothing looks wrong in play: these were tuned by eye. Leave the pace as it is and write the real times on the numbers (no change in play), or halve the waits so they mean what they say (insects give up twice as fast)? My pick: leave the pace, fix the notes; it goes with R3-20.
- **R3-40** (the insect body) Only one bite in half a second lands on one body
  - *Your call:* Many soldiers on one ant. Today only one bite in half a second counts on one body (Minecraft's rule for any mob just hurt), so ten termite soldiers on one ant bite like two, while ten ants on one termite all count. Should every soldier's bite count (a mobbed ant dies about as fast as the numbers say; raids near you cost more ants)? My pick: yes, it is what real soldiers do, but it makes watched raids costlier, so it is your call.
- **R3-41** (the insect body) A living clamp nearly always costs the leg before care can save it
  - *Your call:* A termite soldier clamped on a leg. Today each squeeze (every 20 seconds) may cut the leg, so an ant that walks home clamped keeps its leg only about 1 time in 3 (minor soldier) or 1 in 8 (major), against the 9 in 10 the papers give when nestmates take the head off in the nest. Take the leg roll out of the squeeze (it only hurts), so the nest's care is the one place that decides the leg? My pick: yes.
- **R3-45** (the insect body) In Peaceful the nest defence runs but can do nothing; the sting is not "2 hearts" off Normal
  - *Your call:* The ants in Peaceful. Today the nest's defenders still pour out, chase and squeak at you in Peaceful, but their sting does nothing (Minecraft switches mob damage off). Leave it (bees do the same) and note it, or have them only come out and look in Peaceful? My pick: leave it, note it in the design notes.
- **R4-9** (how bodies are drawn) A mud coat on one of Minecraft's own animals is kept and sent but never drawn
  - *Your call:* should Minecraft's own animals (pigs, cows, horses) show mud after walking through it? Today they carry the mud as numbers and shed bits of it, but look clean. My pick: leave them clean (a mask per vanilla animal is art work for little gain) and stop giving them a coat at all.
- **R4-29** (how bodies are drawn) A crocodile's or monitor's drawn head over a bank is up to about a block from its head hit box
  - *Your call:* should a crocodile's and monitor's hit boxes follow the body where it lies over a bank (head dipped to the water, chest up on a step)? Today the hit boxes stay level at the animal's own height, so a swing or arrow at a dipped head can pass through it. My pick: yes, but only after it is seen in play to matter; it is server work for every crocodile and monitor every tick.
- **R4-32** (how bodies are drawn) A dying buffalo or lion on a step drops its tilt at once and dies with its legs hanging in the air
  - *Your call:* should a buffalo or lion killed on a step fall along the slope it stood on? Today it straightens level in a fifth of a second and its death plays with one end hanging over the lower ground. My pick: yes, keep its tilt through the death.
- **R5-9** (the termites) Three "the termites are stuck" judges all fire long before a fresh layer of soil has set
  - *Your call:* should a mound's stage wait for its soil to set before the next stage starts? Today the stage moves on after 1 and 5 idle minutes whether or not anything is stuck, so drying soil sets the pace and termites build from outside most nights. My pick: yes, the stage waits (your own rule that soil must set first), and building from outside comes only when a spot is truly cut off; a watched mound's stages may then move on more slowly.
- **R5-11** (the termites) The mound's growth rate was set for 60 workers; with today's 200 a full mound takes about a third of the 20 hours asked for
  - *Your call:* how long should a full colony take to raise a full mound? Today a colony of 200 workers earns 52 blocks a day, so the full mound stands after about 6.5 hours of play. My pick: 0.075 blocks a day for each worker (17 a day at 200 workers, the 20 hours you asked for; a young colony of 20 builds 3.5 a day), which keeps "weak early, growing over many hours".
- **R5-17** (the termites) The foragers lay the comb themselves; real comb is built by the young workers from their droppings
  - *Your call:* who should build the fungus comb? Today the old foragers walk down into the garden rooms and lay the comb themselves, and the young nurses never build it. My pick: the young workers lay it, as the design says and as real Macrotermes do; it is a real detail the player can watch.
- **R5-22** (the termites) The dead mounds near the player all turn into humps within about a month of play, while new land keeps showing standing ones
  - *Your call:* how fast should a dead mound slump? Today spires go in about 5 days and the whole mound is a 4-block grassy hump after about a month of play, so the land you live in ends up with only humps. My pick: spires and comb as today, but the body's slump about ten times slower, so a standing dead cathedral stays a landmark and a Matabele home for a long game, as real ones do for years.
- **R5-62** (the termites) Beside a spectator the counted foragers cut the grass in plain view, so most of a watched night's foraging is done by termites that are not there
  - *Your call:* in spectator, should grass be cut by termites that are not there? Today the counted foragers eat the grass in front of a spectator's camera, because on 2026-11-13 you said things may appear in front of a spectator (for the raids). My pick: in front of any player, spectator too, only real termites cut grass; the numbers keep to what nobody looks at. Insects appearing for a raid in front of a spectator stay as you chose.
- **R5-66** (the termites) Termite soldiers never bite a player, though the comments and the plan say they lunge
  - *Your call:* should termite soldiers bite a player who breaks into the mound? Today they never do: they come, drum and stand. Real Macrotermes majors bite whatever comes into a breach and draw blood from a hand. My pick: yes, a soldier bites a player who stands within reach of a breach he made or who hit a termite, for small damage (1), through the nest defence part the Matabele already use (it leaves creative players and spectators alone).
- **R5-67** (the termites) The drumming is felt 16 blocks round the trouble through solid ground and is never passed on, and the nest's "does it reach" is a second rule, so the colony calls out soldiers that never hear
  - *Your call:* should the alarm be passed on from soldier to soldier, as real termites drum it along their galleries? Today a raid far out is answered only by the soldiers within 16 blocks of it. My pick: yes, the relay (it is the real behaviour), built with a balance check on a soak, because more soldiers would then reach far raids and too many soldiers at once wiped raids out before.
- **R5-87** (the termites) Leaf litter is in the diet on paper only; in the mod's savannas there are almost no leaves it matches
  - *Your call:* should termites eat the giant trees' dead leaves? Today they eat no leaves in your savannas (only vanilla leaves cut off a tree count). Real Macrotermes eat a lot of leaf litter. My pick: yes, the dead leaves lying under the crowns, 2 pieces each, not "rich" food (no gallery is dug out to them).
- **R6-5** (the Matabele ants (not raids)) A limping ant stops to call for helpers who can never come
  - *Your call:* After a raid that broke up, a limping ant on the way home stops and squeaks for 10 seconds every 40 while healthy nestmates walking home beside it go past: they count as "help is near" but their walk home outranks the rescue. Should an ant walking home by itself stop and carry a calling nestmate? My pick: yes (real Megaponera coming back from a raid do pick up a caller, Frank et al. 2017). The other way is the small fix: the hurt one no longer calls for them and just limps on.
- **R6-6** (the Matabele ants (not raids)) The nest's defence calls out the wounded first, and ants carrying a hurt nestmate or carried themselves
  - *Your call:* Break into a Matabele nest after a raid came home with hurt ants: today the ants with a fresh wound are the first to charge (their room is the one nearest the entrance), and now and then an ant stings with a nestmate hanging in its jaws. Should an ant with a fresh wound defend its nest? My pick: no for the wounded and for anyone carrying or carried (the design's "the hurt rest until the wound closes"; real Megaponera with a leg gone do fight again, but after the wound has closed).
- **R6-12** (the Matabele ants (not raids)) One continuous size spread, but two bodies decide everything, and 77 in 100 workers get the full major's numbers
  - *Your call:* Each ant has its own real length (5 to 18 mm), but only how big it is drawn and how much it carries follow it. Health, bite, breaking the termites' mud open, scouting and leading a raid all go by which of the two models is nearer, split at 10.6 mm: a 10.5 mm ant has half the health of a 10.7 mm one, and 77 in 100 workers count as full majors. Should an ant's strength follow its own length? My pick: yes: health and damage by length (as the carry load already is), breaking in and scouting from about 12.5 mm up (about 60 in 100).
- **R6-13** (the Matabele ants (not raids)) The sting can kill an unarmoured player standing at the nest in about 2.5 seconds
  - *Your call:* How dangerous should a Matabele swarm be? Today each sting is 2 hearts and up to 20 defenders sting, so a player standing still at a provoked nest with no armour dies in about 2.5 seconds (iron about 5, diamond about 10), while a player who keeps walking is almost never stung (R3-37). The real sting is among the most painful of any ant and not dangerous. My pick: painful, rarely deadly: keep 2 hearts a sting but let the whole colony land at most one sting a second on a player, with the pain (slowness, the red edge) growing with each sting; build it together with R3-37's fix.
- **R6-17** (the Matabele ants (not raids)) The colony's hunger counts only the adults, while a new worker costs 11 termites
  - *Your call:* A new worker costs 11 termites (6 for the queen's egg, 5 for the larva), while a grown worker eats 3 in its whole life; yet "hungry", which sends out the scouts and raids, counts only a day of the adults' food. So the queen and the young empty the pile and the colony flickers between fed and hungry. My pick: keep the 11 (no paper figure for what a Megaponera larva eats is known to me), measure a 10-day soak first, then let "hungry" count the whole need: the adults, the queen's next egg and the hungry larvae. No new number.
- **R6-18** (the Matabele ants (not raids)) Egg and brood times are minutes on a scale where a game day is a real day
  - *Your call:* An egg comes 3 minutes after the queen's full meal, hatches in 1 minute, and the larva grows as fast as it is fed, so a fed colony turns termites into workers within minutes, while a worker lives 60 game days and a wound takes a day. Real ponerine brood takes weeks. My pick: look up the real Megaponera times and put them on the same day-for-a-day scale as the rest (then the young limit of 10 and the queen's rate need a second look with R6-17), or, if fast brood is wanted for play, write that down in the species file.
- **R6-33** (the Matabele ants (not raids)) A dead Matabele worker leaves no body
  - *Your call:* A Matabele killed in the field vanishes in a puff today. Should it lie there as a body, as the termites' dead do? My pick: yes (real dead raiders are left where they fall); it is one line, and the body crumbles by the rules already there.
- **R9-19** (the river hunters) Several small things, listed in the review file
  - *Your call:* Hippo paths. Today the ground a pod walks wears to path blocks and never heals, a player's lawn by a pool too (only with mobGriefing on). Let worn paths grow back to grass when no hippo has walked them for some days? My pick: yes, slowly. (The other small things in this finding need no call.)
- **R9-44** (the river hunters) Small things in the monitor
  - *Your call:* The monitor in water and on mud. Two looks: hunting in the water it leaves by Minecraft's plain hop, not its own climbing-out move, and on soft mud it steps and hops the plain way because its moves never start there. Worth the work to give both its own moves? My pick: not now (small, and only a look); say if you have seen it and it bothers you.
- **R11-22** (the new raid code) Numbers
  - *Your call:* Real ants in one raid. `most_real` is 75, while the project's aim is about 50 to 60 real mobs for a whole colony. Lower it to about 50 (the rest of a big raid goes along as numbers, as now), or keep 75 for the look of a long column? My pick: keep 75 unless you see lag in a big raid; it is your eye that decides.

## Behind the scenes (206)

Code review findings no player sees: code that will cost us when building on it (speed not yet measured,
rules in two places, gaps in the checks) and tidy-ups. *Open* means looked at and left for the reason
given; *half fixed* says what is left; no word means nobody has started it.

### Will cost us when building on it (48)

**Round 1, the colony brain** (`docs/reviews/full/round-01-colony-brain.md`)

- **R1-27** A colony that dies out while away is not ended until a player comes near
  - *Open:* left on purpose. Ending a colony writes down its dead mound, combs and works by reading their blocks (`DeadMounds.addMound`, `addCombs`, `DeadWorks.record`), so it cannot be done as numbers in land that is not loaded; ending it ...
- **R1-31** Every colony ever made ticks once a second for ever and marks the file changed
  - *Open:* to measure, in a big explored world (not done; no such world at hand). Checked now: marking the file only on a change saves nothing, since a colony's clocks (ages, meals, the garden) change every second it lives as numbers; the ...

**Round 2, the legs** (`docs/reviews/full/round-02-legs.md`)

- **R2-16** A search that finds no way costs about 20 ms, most termite walks run it twice, and nothing limits how many run in a tick
  - *Half fixed:* grade B now: measured in the termite stress scene (`stressTermiteColony`). A search that finds no way is rare there (0 to 2 in a minute's window), but one alone costs 36 to 57 ms and makes its tick 50 to 64 ms: one slow tick now ...
- **R2-18** Big step costs fight the fixed cap, and the mud test hides it
  - *Half fixed:* measured and the mud fixed. `SoftGroundSearchTest` (framework): a band of soft ground 8 wide that must be crossed is "no way" at 12 a step with the game's 6,000 and is crossed at 4; wet mud now costs 4 ...
- **R2-29** The way through a chimney mouth still uses one body's lengths
  - *Open:* left on purpose. Checked now: only the termite worker fits the tunnels (the tag `fits_termite_tunnels`), so only it takes a mouth's tube; the king, queen and nymph pass only the old chimney crown block, which no mound builds any ...
- **R2-39** A mind pays for the same "no way" again a tick later, and a builder can ask 24 times in one tick
  - *Half fixed:* `Errand.walkOn` now treats a walk the legs gave up as "no way" (R2-2's fix): no second ask a tick later. Open: `BuildMoundGoal`'s many asks in one tick (round 5).

**Round 3, the insect body** (`docs/reviews/full/round-03-insect-body.md`)

- **R3-34** What "may I start" costs per idle worker
  - *Open:* to measure. Nobody has counted the calls and the time of each goal's `canUse` in a full day-time nest; R3-23's wait takes the dearest of them (the nurse's three searches) down to once a second.
- **R3-42** The cost of a raid near and as numbers is not the same kind of thing
  - *Open:* to measure. A soak line per real raid (raiders, bitten, clamped, legs lost, dead) beside the away dice; no code change before that, as the finding says.

**Round 4, how bodies are drawn** (`docs/reviews/full/round-04-bodies-drawn.md`)

- **R4-2** Every muddy animal near the camera is drawn two or three times over
- **R4-3** Every young worker is drawn twice at any distance
- **R4-4** The clamped-head layer finds the leg by the drawn model's plan, not the full model's, and builds the plan's name for every bone
- **R4-11** What the player sees of these parts has no automatic check outside the films
- **R4-14** The bullfrog's head tip relies on every clip it plays keying the head, and nothing checks it
- **R4-19** Each walking insect re-reads its walk clip's speed note as text every frame
- **R4-20** Every insect the client knows searches for edges and corners every tick, drawn or not
- **R4-21** The crocodile and the monitor read the ground under every joint every frame
- **R4-31** A doum grove makes each chunk rebuild redo every crown, and a ripening bunch redoes a whole crown

**Round 5, the termites** (`docs/reviews/full/round-05-termites.md`)

- **R5-13** The proof and the game judge "what job is here" in two places that already differ
- **R5-26** "may the colony change this block?" is answered in about a dozen places, each a little differently
- **R5-28** What the player sees and no test checks
- **R5-70** "a foe is near", "is this termite safe", "does the trouble reach" and "the trouble is over" are each judged in three to five places with different distances and clocks
- **R5-82** What the player sees of this half and no test checks

**Round 6, the Matabele ants (not raids)** (`docs/reviews/full/round-06-matabele.md`)

- **R6-7** "is this ant fit and free" is judged in nine places by nine different rules
- **R6-10** Out of a raid column any nestmate carries any hurt ant; the size rule lives only in the raid
- **R6-14** A misspelt key or section in the species file quietly uses a number nobody chose, or switches a whole system off
- **R6-21** R1-28's Matabele side: which carriers lose their termites in a fold, and a meal that folds in as a whole new termite
- **R6-22** What the player sees of the Matabele's food, care and defence has few tests, and two tests pass if the hurt ant dies

**Round 7, the giant trees** (`docs/reviews/full/round-07-trees.md`)

- **R7-3** A tree's growth step is one tick's work, and all trees come due at the same dawn
  - *Open:* to measure. Nobody has timed one growth step of a young and of a grown tree in sight, and the fix proposed (each tree its own "dawn", a big step split over ticks) is only worth its rules if the number is bad. The walk's 4 ms ...
- **R7-6** The whole tree book is written at every autosave, and it only grows
  - *Half fixed:* the book counts as changed only when a tree's record really changed (`Forest.update` compares the record before and after; the line that marked it on every look is gone), so a tree waiting to be out of sight no longer has the ...
- **R7-24** The tree kind files are not the "numbers I can tweak" they look like
  - *Half fixed:* the umbrella thorn's file now holds the numbers its shape really uses and the shape reads them (`UmbrellaShape`: height 25, spread 2.5, fork 0.56, limbs 3 to 4, thickness 5, dome 2; the same trees as before, block for block by ...
- **R7-26** Test gaps on what the player sees
  - *Half fixed:* tests now guard a tree of new land the book missed (R7-15), a rot-killed log crumbling on a slope (R7-16), the ground under a dead young tree (R7-17), a log resting on a mound (R7-11), a scorched leaf (R7-10) and a fallen log's ...

**Round 8, the world** (`docs/reviews/full/round-08-world.md`)

- **R8-7** An oxbow site with no room does all its shape sums 8 times
  - *Open:* to measure. The work is on the world-making threads, not the tick; nobody has summed the oxbow feature's time on a flight along a river. To do first: `tools/flight.sh` with the feature's time summed; the fix (find the river's ...
- **R8-12** Twelve spawning groups are tried on every tick in every chunk, everywhere
  - *Open:* measured, grade B now, open (2026-11-24, `SpawnerCostTests.whatTheSpawnerCostsForOurGroups`, run by name, in the `stress` category). One try of a group with no spawn list costs 27 ns in solid ground (most tries: the spot's height ...
- **R8-19** In a Large Biomes world the cave filling looks at the wrong map
  - *Open:* proof owed, and only Large Biomes worlds. That such a world reads the same two cave files is from memory of Minecraft's noise settings (mine agrees with the review's), and nobody has looked at one. The fix is a density function ...

**Round 9, the river hunters** (`docs/reviews/full/round-09-river-hunters.md`)

- **R9-2** Every search for entities in the world walks every body hit box of every animal
  - *Open:* to measure. The cost of the body hit boxes is a sum from counts (0.3 to 0.6 ms a tick for a full river), never measured, and fewer beads changes where each animal can be hit, which is how it plays. To do first: the full-river ...
- **R9-8** Every crocodile route may search four times as far as a zombie's, and failing routes are asked again and again
  - *Open:* to measure. A follow range of 24 to 32 with far sand reached in legs is a change to how crocodiles find their banks, and the cost of the long searches was never measured. To do first: the scene named (a hungry crocodile, a cow on ...
- **R9-9** Two wide searches of the land done in a single tick
  - *Open:* to measure. Both searches read loaded land only; their cost (5 to 15 ms once, by sum) was never timed, and the fix (rings, a search spread over ticks, eggs lost after some failed tries) adds rules and one change in play (a female ...
- **R9-35** What the otter is doing lives in five places, and every A finding above is a state left behind when one of them ended unplanned
  - *Open:* left on purpose, a design note. The bout and the held catch as their own classes with one "end, whatever happened" door is the first step of the next thing added to the otter (breeding, the pet); done now it would move approved ...
- **R9-42** The code that starts a staged move exists three times
  - *Open:* left on purpose for the Motion Studio. Moving the monitor and the hippo onto `MoveRider` touches approved moves, so it needs the monitor's course and its frame films; it belongs with the next change to their staged moves.

**Round 10, the river's small life** (`docs/reviews/full/round-10-river-small-life.md`)

- **R10-10** The two frog steps before breeding can end with nothing, and a hungry frog searches wide 5 times a second
  - *Half fixed:* the hungry frog's search box is 3 high, not as high as it is wide, and a guard told to see nobody off searches for nobody (`GuardPlaceGoal`). Tried and taken out again: a one-second wait after a look that found nothing; in the ...
- **R10-18** Small searches that run more often than they need
  - *Half fixed:* a snail looks round for what it fears 4 times a second, not every other tick, and a mother crab counts the crabs round her only once her eggs are due. Open: the crab's three avoid goals as one look (small, not measured).

**Round 11, the new raid code** (`docs/reviews/full/round-11-raids.md`)

- **R11-17** Near and away still differ in how often a colony raids
  - *Half fixed:* the last leave is 18:00, not 19:00 (`megaponera_analis.json`, `hours`). Measured from the 2-day soak's raid diary (86 raids): from the muster to the attack's start takes 0.9 game hours at the median and 1.4 for the farthest spots ...

**Round 12, the animation engine** (`docs/reviews/full/round-12-animation-engine.md`)

- **R12-6** The pair check sees only part of a looping prey clip, at one phase, and not the blend or the walk
  - *Half fixed:* a looping prey clip is posed for at least one whole loop, and each spot's first bite is also measured against every moment of the prey's loop (flag: "the bite misses at other moments of the loop"). Left: the blend into the hold ...
- **R12-12** The offline dragged tail is its own simpler rule, never matched against the game's TailDrag
  - *Open:* the label part was done with R12-2 (the dragged tail offline says "rough, not counted"). Matching it against the game's `TailDrag` was not started: it needs the game's rule moved into a table both read.
- **R12-22** Nothing measures that the offline pose is the game's pose; `match` only puts pictures side by side
  - *Half fixed:* for the animals there is now a measure, `python tools/pose_match.py <animal> [clips]` (films each clip on a still animal with the recorder on, poses the same moment offline, sets every box corner beside the game's; over 0.2 px ...
- **R12-25** `two_bone` gives wrong pieces (and says "no miss") when the target is close, and crashes at the root
  - *Half fixed:* a target nearer than the limb can fold to is reported as a miss, and a target at the root itself no longer crashes. The shape for a too-close target is back as it always was: `claude/review-fix-b6`'s right shape changed the ...
- **R12-26** A pole along the leg's line crashes `two_bone` and flips the knee 180 degrees as the target crosses it
  - *Half fixed:* a pole exactly along the leg's line no longer divides by nothing (a second way takes over). The knee still changes side as the target crosses that line: `claude/review-fix-b6`'s easing near the line changed the monitor's approved ...
- **R12-65** `MoveRider`, the planned one home for block moves, lacks two lessons of the monitor's copy, and the drawing half is copied three times
  - *Half fixed:* `MoveRider` has the two lessons: its room checks for up, down and out ask for the middle line too (no change for the otter: its feet stand 0.22 blocks out, so the middle is always one of the feet's columns), and a move cut on its ...

### Tidy-ups (158)

**Round 1, the colony brain** (`docs/reviews/full/round-01-colony-brain.md`)

- **R1-5** The founding egg's spot is rolled with the queen's own dice
- **R1-6** Comments that still describe the harvester
- **R1-15** Tidy-ups in the food store
- **R1-23** Progress on a leak or a dug block is cleared only when the job finishes
- **R1-25** Two comments sit above the wrong method
- **R1-32** A trip out of range shorter than the grace wipes the last away report
- **R1-33** A colony's dice start the same run after every reload
- **R1-34** Comments from before the harvester left
- **R1-38** Two judges of "it rains"
- **R1-39** The away brain asks the world for eggs and larvae several times a second
- **R1-40** "is the colony here" is worked out again inside parts in the middle of a tick
- **R1-41** The real queen's founding batch has no away guard, unlike her stream laying
- **R1-49** An insect or queen that changes dimension is never taken off its colony
- **R1-50** Small things that reset or never clear

**Round 2, the legs** (`docs/reviews/full/round-02-legs.md`)

- **R2-4** The shared-route code is used by nothing in play
- **R2-5** Log and stopwatch texts are built on every plan, wanted or not
- **R2-6** After a reload a held insect's heading, ceiling side and channel wall are gone
  - *Half fixed:* the heading is saved; the ceiling side and the channel wall are not.
- **R2-10** The shuffle can move an insect 1.5 blocks, its comment says 0.7; its wall check looks at one point
- **R2-12** A sideways crawl ends over a tunnel opening
- **R2-13** Worked out again every tick: the doorway's point list, and up to 64 slope reads
- **R2-15** Tiny correction moves never count as blocked
- **R2-23** The search's guess of the distance left can be too high, so a route is not always the cheapest
- **R2-25** Throwaway objects in the search's inner loop
- **R2-26** Small tidy-ups
- **R2-27** What no unit test covers in the planner
  - *Half fixed:* one gap closed: `aWindowSealedInFrontOfAnInsectIsNoLongerAWayInOrOut` (R2-19). The others are open.
- **R2-34** On the server "is it climbing" reads the value sent to the drawing, a tick late
- **R2-35** Comments in the wrong place or saying the wrong thing
- **R2-36** Gaps in `DoorwayMotionTest`

**Round 3, the insect body** (`docs/reviews/full/round-03-insect-body.md`)

- **R3-1** A stung insect saved while staggering walks at full speed after the reload
- **R3-2** The camera's "film:" name hook is reachable in play
- **R3-3** `WoundOddsTest` is a weak copy of a savanna test
- **R3-4** Comments that still describe the harvester
- **R3-5** A felled insect's load is deleted, while the comment says it drops it
- **R3-6** The lookers' small reach at the entrance lasts one second
- **R3-7** A felled striker still finishes its grip
- **R3-8** A new name string for every insect every tick, for the stopwatch
- **R3-9** Stings add up for life
- **R3-14** The fleeing termite's "startled moment" is only a delay
- **R3-15** Five goals turn the body themselves, five copies of one method
- **R3-16** Leftovers of the harvester ant
- **R3-17** Small loose ends
- **R3-25** `DigGoal`'s "soil waits" check is a harvester leftover, and half of it never runs
- **R3-26** Wrong or stale comments
- **R3-27** `FeedLarvaGoal` and `FeedQueenGoal` carry the same three legs twice
- **R3-28** Mouth to mouth turns the nurse's body from the mind
- **R3-29** `TidySearchTest` checks the rule, not the search
- **R3-36** Small things
- **R3-43** Small things lost over a reload in the middle of a fight
- **R3-44** The headcount's "one died" is written three times in species code

**Round 4, how bodies are drawn** (`docs/reviews/full/round-04-bodies-drawn.md`)

- **R4-5** An insect not yet drawn keeps the worker's body numbers and size 1 for its edge pose, its spine and its turning steps
- **R4-6** Small per-frame allocations
- **R4-7** The texture-exists caches are never cleared on a resource reload
- **R4-8** A lost leg of a species no longer signed up throws an exception every frame it is drawn
- **R4-10** Wrong or stale comments
- **R4-15** Fly clouds do per-fly work each frame that could be done once
- **R4-16** Small fly and tongue mismatches
- **R4-17** A muddy footprint can float over the edge of a block
- **R4-22** Three animal models build their file names from text on every call, and the crab re-registers its bones between looks
- **R4-33** Two writes to shared bones that do not leave GeckoLib's marks as they were
- **R4-34** A dying hippo keeps turning its head and pupils toward what it watches
- **R4-35** Throwaway objects made every frame per animal
- **R4-36** Hand-copied numbers and files read past the resource system
- **R4-37** Wrong or misplaced comments

**Round 5, the termites** (`docs/reviews/full/round-05-termites.md`)

- **R5-29** The mushrooms go by a second judge of rain, come up only for a colony that was near when the rain stopped, and have no socket for the season
- **R5-30** The mound's building pace ignores rain and the season, and leaves no socket for the season source
- **R5-31** The saved soil counts are trusted for good, even after the mound's plan has changed
- **R5-32** After `/reload` with a new `max_height`, a watched mound keeps its old plan
- **R5-33** A mound plan that fails to grow is retried every second with no log line, and the plan stores are never cleared between worlds
- **R5-34** `max_height` 0 in the species file crashes the colony's tick, and a species with a garden part and no garden numbers crashes the colony list
- **R5-35** Comments and notes that disagree with the code
- **R5-36** The "clear a piece" branches of shaping can never run
- **R5-37** Log lines at INFO level in normal play
- **R5-38** Reshaping "through a window" never bites one
- **R5-39** A termite in a spire's channel is buried when the piece is reshaped into a solid tip
- **R5-40** Small block leftovers
- **R5-41** Every nurse's "is there food" rebuilds the garden's cell list and reads every comb, and looks for the king by a scan for entities
- **R5-42** Small things in the garden
- **R5-43** A settled dead mound keeps every column of its wear for good, and the whole book is written at every save while any mound changes
- **R5-44** The "already put in" check of new land can never be reached
- **R5-45** A dead colony of new land is given its days since death as its age
- **R5-46** The dead works' list of "the nest, never filled" leaves out the side rooms
- **R5-48** The mound's allowance for a whole absence is paid at the head count on the day of return
- **R5-49** The bare patches catch up all in one tick on return
- **R5-50** Two block reads with no loaded-land check, reachable today only by the kill command
- **R5-51** Three places where the mound meets the player's own digging and building
- **R5-85** Small leftovers in the web's and the holes' books
- **R5-86** A closed hole stays shut by game time, the foraging hours run on the day clock
- **R5-89** The wood call measures every piece of dead wood in its ring every minute, known or not
- **R5-90** Two lists that must be kept in step by hand
- **R5-91** The savanna number records check no ranges, and some defaults in code differ from the file's sense
- **R5-92** Every gallery piece stores its whole way from the nest's shaft
- **R5-93** Filling a piece in reads the blocks beside each filled block with no loaded-land check
- **R5-94** The termites' fight clocks run at half the speed their names say
- **R5-95** Beyond 256 mud casts the wood eaten through is no longer "inside", so a big dead giant's far parts stop being eaten by day
- **R5-96** The grass's drying and regrowth have no socket for the shared season
- **R5-97** Comments that disagree with the code

**Round 6, the Matabele ants (not raids)** (`docs/reviews/full/round-06-matabele.md`)

- **R6-23** After each help call a limping ant stands 5 seconds more, because its "am I stuck" watch counts the call as being stuck
- **R6-24** A rescuer that is hurt itself keeps its patient, and can be picked up with it: three ants in a stack
- **R6-25** Small things in the goals' turns
- **R6-26** Every idle ant in the nest searches for callers and patients when nobody is hurt
- **R6-27** The sting's pain list is kept across worlds
- **R6-28** A homeless queen floods every near dead mound every 10 s, the taken ones too; the dead mound book only grows
- **R6-29** Small things in the pile and the nest
- **R6-30** The model lengths are written in two places
- **R6-31** What a worker loses on going into the numbers: its length, its scout memory
- **R6-32** Comments, notes and numbers that disagree with the code, and dead leftovers

**Round 7, the giant trees** (`docs/reviews/full/round-07-trees.md`)

- **R7-8** Dead shapes do the living tree's work again
- **R7-9** Small waste in busy places
- **R7-21** Data that is not checked
- **R7-22** The fall is drawn from the flat-ground shape
- **R7-23** Minecraft's trees in a small savanna are thinned by the grass
- **R7-25** Leftover and trial code, comments that no longer match
- **R7-34** Small block slips
- **R7-35** Do palm seedlings stand on their ground?
- **R7-36** Tree blocks: unused code, old comments, missing tests

**Round 8, the world** (`docs/reviews/full/round-08-world.md`)

- **R8-4** Pans and oxbows cut holes in tree crowns that stand in land made earlier
- **R8-5** The river bank's look depends on which chunk was made first
- **R8-6** The spawn search reads one row of columns from the wrong chunk
- **R8-8** A log line for every site, laid or not
- **R8-9** Two small leftovers between worlds
- **R8-13** A mound place reads 13 columns of ground before it asks which biome it is in
- **R8-15** The colonies' insects count as Minecraft's farm animals
- **R8-16** Algae can creep out of the savanna
- **R8-17** Notes that no longer match the code
- **R8-20** Small things

**Round 9, the river hunters** (`docs/reviews/full/round-09-river-hunters.md`)

- **R9-3** The guard goal searches for intruders when it has none to look for, and twice a turn when it runs
- **R9-4** Small things in the kit that add up or mislead
- **R9-11** A crocodile hit while afloat does not react, and a nest mother shot from afar stands still
- **R9-12** Small things in the crocodile
- **R9-14** Dung on land almost never happens
- **R9-20** Small things in the hippo
- **R9-26** Small things in the shark
- **R9-36** Small things in the otter
- **R9-43** Three small behaviours that do not end the way they should

**Round 10, the river's small life** (`docs/reviews/full/round-10-river-small-life.md`)

- **R10-3** The fly clouds' cap is written as 6 in one place and is 18
- **R10-6** Small things that disagree or are test-only
- **R10-7** Thirteen spawning groups of our own, each tried in every chunk every tick
- **R10-11** Frog and tadpole small things
- **R10-17** The snail's crawl and rest clips flicker (BUGS 250's likely cause)
- **R10-19** Crab and snail small things

**Round 11, the new raid code** (`docs/reviews/full/round-11-raids.md`)

- **R11-18** The way-home list's 5-minute timeout is covered by the 5-second sweep
- **R11-19** Work done more often than needed
- **R11-20** Small bookkeeping
- **R11-21** Comments and notes that disagree with the code

**Round 12, the animation engine** (`docs/reviews/full/round-12-animation-engine.md`)

- **R12-18** The joint-turn check reads the keys as straight lines and skips a loop's seam
- **R12-19** Small mismatches between what is judged and what is drawn
- **R12-29** Any edit to `termite_models.py` or `body_plan.py` reruns every insect's lab check
- **R12-30** The kit's cache is used by the monitor only; the newer makers rebuild every clip every run
- **R12-31** `quadruped_gait` lets the feet step at different ground speeds without a word
- **R12-33** The lab's catmull-rom curve is not GeckoLib 4.9.3's
- **R12-34** Small tidy-ups
- **R12-51** The frame-time meter re-sorts every frame of the launch each second
- **R12-56** Small miscounts in the one command's summary
- **R12-57** Two names for the camera's in-between time
- **R12-66** The film watch's "reload the move files" swaps the moves under live plays
- **R12-67** Client-only and removed-species code inside the framework's shared package
- **R12-69** The dragged tail does all its ground reading even when it has no say
- **R12-73** The hippo's climb-out paths are not one row a tick, and the move writer never checks

## Tool faults (6)

Faults in the test and film tools, not in the game.

- **114** The side-by-side test run hangs after every server has finished, one server stuck saving its world as it shuts down
- **170** The guard film `r_crowd_space` (eight Matabele majors in a pen) comes out different on every launch again
- **295** Frozen, stepped films draw a clip's clock two ticks a picture while the body moves one
- **429** On the course the monitor, the bullfrog and the crocodiles stop short of where a scene sends them
- **435** The six walker guard scenes come out different on every launch
- **449** The lion hunt film misses the hold; its "hold" burst films the feast instead

## The code review so far

519 findings: 294 left (above), 225 done (below). The plan: `docs/plans/code-review.md`.

| Round | System | Found | Left | Done |
|---|---|---|---|---|
| 1 | the colony brain | 50 | 16 | 34 |
| 2 | the legs | 43 | 18 | 25 |
| 3 | the insect body | 45 | 32 | 13 |
| 4 | how bodies are drawn | 37 | 32 | 5 |
| 5 | the termites | 97 | 84 | 13 |
| 6 | the Matabele ants (not raids) | 33 | 30 | 3 |
| 7 | the giant trees | 36 | 15 | 21 |
| 8 | the world | 20 | 14 | 6 |
| 9 | the river hunters | 44 | 19 | 25 |
| 10 | the river's small life | 19 | 8 | 11 |
| 11 | the new raid code | 22 | 6 | 16 |
| 12 | the animation engine | 73 | 20 | 53 |

**Done:** fixed, or found to be no bug after all (the grade is the review's own: A a player can meet it, B it would cost us, C a tidy-up; the number after it is its entry in `docs/BUGS.md`).

*Round 1, the colony brain*

- **R1-1** (A) A Matabele queen never lays while a player is near (BUGS 392)
- **R1-2** (A) A broken species file crashes the game while the log says "using the built-in numbers" (BUGS 387)
- **R1-3** (B) A young colony that once yielded to a neighbour yields again after one meeting
- **R1-4** (B) Some numbers in the species file have no range check, and a 0 crashes
- **R1-7** (A) The new-holes check reads a far food block in land that is not loaded (BUGS 390)
- **R1-8** (A) `ForagingHoles.isOpen` reads the block before it asks whether the land is loaded (BUGS 390)
- **R1-9** (A) "is anyone still outside" looks only 32 blocks round the nest (BUGS 393)
- **R1-10** (B) A colony with no food plants still scans about 2,400 columns every 5 seconds
- **R1-11** (B) Once a second every outside worker is compared with every plant
- **R1-12** (B) Each forager's plant search counts every claim for every plant
- **R1-13** (B) The store's room is judged in three places by three rules
- **R1-14** (B) A found plant far out is read with no load check once its finder is home (BUGS 390)
- **R1-16** (A) Every builder looking for work scans the whole nest for leaks (BUGS 394)
- **R1-17** (A) On return the nest ledger does a whole second's changes in one tick (BUGS 395)
- **R1-18** (A) Reading a cell's neighbours can load land that is not ready (BUGS 390)
- **R1-19** (A) A foraging tunnel not fitted to a hillside the first time is never fitted again (BUGS 391)
- **R1-20** (A) While no nest plan fits, the room planner runs on every call and a shaft is dug anyway (BUGS 396)
- **R1-21** (B) `nestCells` is rebuilt on every call (BUGS 394)
- **R1-22** (B) The catch-up digs the founders' chamber by weaker rules than the real diggers
- **R1-24** (C) `Nest.isEntranceOpen` reads the block before asking whether the land is ready (BUGS 390)
- **R1-26** (B) The far rule removes loaded insects without asking whether a player can see them
- **R1-28** (B) Folding an insect into the numbers loses a species load it carries
- **R1-29** (B) An insect frozen while its colony is here waits in limbo
- **R1-30** (B) One colony that fails to load wipes every colony of the dimension (BUGS 387)
- **R1-35** (A) Sleeping in a bed takes the termites' foraging night away, while their hunger runs on (BUGS 397)
- **R1-36** (B) Builder numbers do nothing near a player and bank work without end while away (BUGS 398)
- **R1-37** (B) All the numbers of a caste share one meal minute
- **R1-42** (A) One typo in a species file empties every colony of the world (R1-2 and R1-30 joined) (BUGS 387)
- **R1-43** (A) An insect already folded into the numbers is folded a second time (BUGS 388)
- **R1-44** (A) Every autosave and every pause rebuilds and copies every colony's whole record
- **R1-45** (A) A minor that comes out when a number scout is shown goes back into the numbers, but its body stays (BUGS 389)
- **R1-46** (B) A colony whose queen cannot be found is skipped for ever while near
- **R1-47** (B) More reads one step past the guarded cell, and on a list up to 5 seconds old (BUGS 390)
- **R1-48** (B) The "no route to this food" memory is not saved

*Round 2, the legs*

- **R2-1** (B) The legs never remember a failed search, so every mind must bring its own retry clock (BUGS 400)
- **R2-2** (B) Three new plans are all a walk gets, however far it got, and nobody hears the give-up (BUGS 404)
- **R2-3** (B) An insect falling more than 12 blocks beside a wall is taken for "shut in" and snapped onto the wall (BUGS 403)
- **R2-7** (C) `mayHaveGoal` asks the mind's test about twice as often as it needs
- **R2-8** (B) The standing shuffle's search for a free spot can run every tick for good (BUGS 405)
- **R2-9** (B) The stuck clock counts polite waiting, so a walker in a jam plans again and gives up (BUGS 404, 409)
- **R2-11** (C) The "no mouth any more" checks in the mouth moves can never fire, and would do the wrong thing
- **R2-14** (C) `unsqueeze` can divide by a column width of 0
- **R2-17** (B) One search reads the same blocks 50 to 60 times per step (BUGS 399)
- **R2-19** (B) A tunnel window sealed in front of an insect is not seen by the every-tick check (BUGS 407)
- **R2-20** (B) A goal cell with nothing to hold in it is searched to the cap (BUGS 399)
- **R2-21** (B) Common part-blocks are neither ground nor passable (BUGS 412)
- **R2-22** (B) Two static maps keep a closed world in memory (BUGS 406)
- **R2-24** (C) Comments that disagree with the code
- **R2-28** (A) A frozen insect on a wall slides down it and is drawn as climbing (BUGS 402, 410)
- **R2-30** (B) Nothing can knock an insect off a wall, and nowhere says that is meant (BUGS 413)
- **R2-31** (C) Every insect pushes the mobs round it every tick, though nothing can push it
- **R2-32** (C) `AntMoveControl` turns to face east when the point is straight above or below
- **R2-33** (C) "stepped up close" outlives `stop()` *Not a bug after all.*
- **R2-37** (A) Five goals ask the legs for a route on every tick after "no way" (BUGS 400)
- **R2-38** (A) An insect on a path block, on farmland or in a pond with a low bank can never plan a route again (BUGS 401, 411, 412, 415)
- **R2-40** (B) The "no foothold in reach, no search" shortcut helps one caller only
- **R2-41** (B) Saving an insect that is walking a wall reads the world and loses its hold
- **R2-42** (B) Four tests of "is this a doorway for this insect"
- **R2-43** (B) Knocked into a cell near its route, an insect finds out only after 6 seconds (BUGS 413)

*Round 3, the insect body*

- **R3-11** (B) A walk that found no route is searched again on every goal tick
- **R3-12** (B) Termite nurses at the queen never clean themselves or groom anyone
- **R3-13** (B) Termite workers no longer hang from the ceilings
- **R3-18** (A) A paralysed nestmate eaten on the comb stays in the colony's headcount for ever
- **R3-19** (B) `DigGoal` can start with a body in the jaws, and its first bite throws the body away
- **R3-22** (B) Two goals read their target block every tick without asking whether its land is loaded
- **R3-23** (B) An idle nurse's search for a hungry young one costs three mob searches and never waits
- **R3-24** (B) Sealing a hole searches for a route at least twice, and once per unreachable nearer spot
- **R3-31** (A) In a colony without a queen, a worker holding food for her starts and stops `FeedQueenGoal` for ever and stands like a statue
- **R3-32** (B) `closeIn`'s one-second "no nearer" clock never runs for the goals that use it
- **R3-35** (B) `RescueGoal` has no claim on the caller, every free nestmate sets off for the same hurt ant
- **R3-37** (A) A player who keeps walking is almost never stung
- **R3-39** (B) Held cells are never cleared away, so "any held?" stays yes and the old world is kept

*Round 4, how bodies are drawn*

- **R4-1** (A) A screen that guesses "missed" never takes the server's "gripped", so a lioness is drawn standing on the ground while the buffalo carries her off
- **R4-12** (A) A reed or a hippo grass mat that the server marks "pushed" but the client gives no spring is drawn by nobody: it vanishes
- **R4-23** (A) A crocodile, a Nile monitor and an otter vanish at the screen's edge while most of their body is still in view
- **R4-24** (A) A player or animal held by a crocodile gets a shadow stood on edge (and spun round in the death roll)
- **R4-25** (A) On a slow or stalling server the crocodile's death roll jumps back, and a held player's view spins back with it

*Round 5, the termites*

- **R5-1** (A) Asking a far mound for its stage or its cells still grows its whole plan on the game's own thread, a freeze of one to two seconds a mound
- **R5-2** (A) While a colony is away its fungus garden only "sees" the comb left over after eating, so nodules stop growing, the young starve and then the queen
- **R5-3** (A) Mound soil sets and dries only on Minecraft's random ticks, so it stands still near a spectator, while the colony is away, and at random tick speed 0
- **R5-4** (A) Shaping hands every builder only the one lowest job; when no termite can reach that one from inside, no termite carves or reshapes anything
- **R5-5** (A) A window a builder bites in a tunnel stays open for ever if the builder dies, is folded into the numbers, or its land unloads while it works through it
- **R5-6** (A) Dead mounds are worn right at the edge of loaded land with only each block's own chunk checked, and every change makes Minecraft read the blocks beside it, which loads the next chunk on the spot
- **R5-8** (A) The soil carrier walks by its own rules, with up to 12 route searches in one tick and no cheap "no way there"
- **R5-52** (A) A night skipped with `/time set day`, or a day clock that stands still, takes the termites' foraging, and the fix for the slept night does not see it
- **R5-53** (A) Eaten dry grass grows back green and only dries on Minecraft's random ticks, so near a spectator, and far from every player, a colony's grass never becomes food again
- **R5-54** (A) One gallery block the termites cannot dig stops the whole web from growing, for good
- **R5-55** (A) A termite alarm never ends while one living enemy insect stays within 4 blocks of it, and it walks along with that enemy
- **R5-57** (A) The minors take every real soldier place first, so at night beside a full colony, and whenever a raid's drumming reaches the nest, every real major is sent into the numbers
- **R5-58** (A) A creature in a gallery puts the hidden termites back, and they go under again on the very next tick, so they blink and slip past it unseen

*Round 6, the Matabele ants (not raids)*

- **R6-1** (A) A second player joining over LAN, or anyone on a server, is thrown out (or crashes) when a Matabele worker comes into view
- **R6-2** (A) A Matabele colony whose workers all die keeps its queen for ever, and its dead mound stays taken
- **R6-3** (A) A major with a fresh wound, or badly wounded, is sent out scouting alone

*Round 7, the giant trees*

- **R7-1** (A) Catching up on missed days counts the ground under the whole crown once for every missed day (BUGS 339)
- **R7-2** (B) The once-a-second walk looks at every tree in the world, and always starts from the same end
- **R7-4** (B) A storm breaking a hollow tree works out three shapes on the server's own thread
- **R7-7** (B) Four shape stores are never emptied
- **R7-10** (A) "Char, never vanish" against "brown leaves burn away": a fire can eat a living crown (BUGS 340)
- **R7-11** (A) A falling tree breaks through a termite mound (BUGS 341)
- **R7-12** (B) Lightning takes big trees faster than old age
- **R7-13** (B) Seedlings after rain fill the savanna in
- **R7-14** (B) Termites hollow baobabs, which nobody decided
- **R7-15** (A) New land's trees can miss the tree book (BUGS 342)
- **R7-16** (A) A rot-killed log on a slope leaves wood hanging (BUGS 343)
- **R7-17** (A) A dead sapling leaves a stick sunk in the ground (BUGS 344)
- **R7-18** (B) One bad record empties the tree book
- **R7-19** (B) A fallen log's record never ends
- **R7-20** (B) Felling checks the foot's chunk only
- **R7-27** (A) Every fig branch is a full cube (BUGS 345)
- **R7-28** (A) Shears on a fruit bunch: endless fruit (BUGS 346)
- **R7-29** (A) Animals do not know thorny twigs hurt (BUGS 347)
- **R7-30** (B) The river animals' ground finder stops on top of a crown
- **R7-31** (B) A cut tree's crown hangs for good
- **R7-32** (B) Fruit stands still when nobody is near

*Round 8, the world*

- **R8-1** (B) River sites are remembered by the seed alone
- **R8-2** (A) A 129-block search for animals on every spawn try *Not a bug after all.*
- **R8-10** (B) The flat savannas replace eleven of Minecraft's own land files *Not a bug after all.*
- **R8-11** (A) Mounds of new land are lost when the game is left, and can turn up in the next world (BUGS 321)
- **R8-14** (B) The mud's own wetness rule undoes the pattern the world maker lays
- **R8-18** (A) Ravines and caves still come into the savanna along its rivers (BUGS 322)

*Round 9, the river hunters*

- **R9-1** (A) A courting animal whose suitor dies or leaves the loaded land is "taken" for good (BUGS 323)
- **R9-5** (A) The crocodile hunts the player's own animals: tamed horses, donkeys, llamas, name-tagged and leashed stock (BUGS 324)
- **R9-6** (A) In Peaceful the crocodile still seizes the player and holds them through twelve rolls (BUGS 325)
- **R9-7** (B) A crocodile whose basking spot lies beside a hippo swims to it and turns away, again and again
- **R9-10** (B) A mother taken from her nest reads the nest's land wherever it is
- **R9-13** (B) The warning yawn is remembered per hippo, not per visitor: the second one is bitten unwarned
- **R9-15** (B) A hippo shot from more than 16 blocks never answers
- **R9-16** (B) Two "nothing found" searches with no wait after them
- **R9-17** (B) The pool's water is read with no check that its land is loaded
- **R9-21** (B) A shark's hunt hangs still for up to 45 seconds
- **R9-22** (B) Only the three nearest fish are tried, so a school on the shelf hides every fish behind it
- **R9-24** (B) A shark hit from the bank or from a boat does nothing
- **R9-25** (B) The catfish plans a new route every tick once its route has run out short of its prey
- **R9-27** (A) A catch in the jaws is saved with the otter, but the otter forgets it: after a reload it is stuck at the snout for good (BUGS 326)
- **R9-28** (A) Carrying a catch has no time limit: with no bank in reach the otter floats on the spot with it for ever (BUGS 327)
- **R9-29** (A) Only the otter that started a game of play can end it: if that one is killed or vanishes mid-game the other stands in the game for good (BUGS 328)
- **R9-30** (A) The otter is the only river animal Minecraft may remove 32 blocks from the player: they vanish one at a time (BUGS 329)
- **R9-31** (B) While an otter on screen holds a crab or a frog, the game reads and parses a file from the mod's jar twice every frame
- **R9-32** (B) A hunt that gives up takes the same prey again half a second later
- **R9-33** (B) A cornered otter plans its escape again every time it is asked
- **R9-34** (B) Harm with no attacker does not make an otter run, and it wipes the fright of the otters near it
- **R9-37** (A) A staged move that is stopped on its very first tick freezes the monitor in place, and starts again every tick (BUGS 330)
- **R9-38** (A) The walk into the water is cut short for 7 monitors in 8 (BUGS 331)
- **R9-40** (B) After a reload, and from its spawn until its first swim, nothing keeps a monitor near its water
- **R9-41** (B) The monitor never counts its life clock (nor does the hippo); the kit leaves that to each animal to remember (BUGS 335)

*Round 10, the river's small life*

- **R10-1** (A) The world's spawner and breeding fight for the same places, and the spawner wins (BUGS 337)
- **R10-2** (B) A mate that dies or unloads in the middle of courtship leaves its partner "taken" for good (BUGS 323)
- **R10-4** (B) Catfish eggs are eaten long before they can hatch
- **R10-5** (B) River algae spreads into any shallow water, with nothing to stop it
- **R10-8** (A) A bullfrog climbing out of the water can be left hanging with no gravity, for good (BUGS 333)
- **R10-9** (A) Every tadpole tells every player its age every tick (BUGS 334)
- **R10-12** (A) Crabs, snails and catfish never grow up and breed only once in their lives (BUGS 335)
- **R10-13** (A) A crab's meal does not count when the food ends at the first bite (BUGS 336)
- **R10-14** (B) The crab's 20-second limit on threatening does nothing
- **R10-15** (B) Asking for a new path every tick to a spot that cannot be reached
- **R10-16** (B) Snail eggs within a crab's reach never hatch

*Round 11, the new raid code*

- **R11-1** (A) A scout setting out reads blocks in land that is not loaded (BUGS 316)
- **R11-2** (A) The scout searches for a route every tick when it has no way (BUGS 317)
- **R11-3** (A) The attack's "quiet" rule is kept awake by a termite nobody can reach (BUGS 318)
- **R11-4** (A) A raid the player is watching is folded into numbers when the nest's chunk unloads (BUGS 319)
- **R11-5** (A) A raid as numbers takes real termites beside the player, with no ant in sight (BUGS 320)
- **R11-6** (B) Two judges of "termites here": the scout counts soldiers, the raid does not
- **R11-7** (B) A raid shown again after going to numbers bunches as a hollow ring
- **R11-8** (B) A raider called back by the leash may never fight again
- **R11-9** (B) The raid's size is never lowered for real ants that leave it
- **R11-10** (B) A raid shown "in the nest" from the halt, attack or regroup starts over
- **R11-11** (B) In the colony's away grace minute nobody steps the raid
- **R11-12** (B) A scout drafted to defend the nest stalls the whole march
- **R11-13** (B) A waiting find whose land does not tick stops all scouting until it fades
- **R11-14** (B) A waiting find writes a diary line every second outside the raid hours
- **R11-15** (B) A find on its way home is lost on a reload *Not a bug after all.*
- **R11-16** (B) `Scouts.mayScout` lets any ant on the out list through unchecked

*Round 12, the animation engine*

- **R12-1** (A) A walk's planted feet are never judged for hanging, and a walk foot that never touches the ground raises no flag
- **R12-2** (A) Most of the checker's findings never say FLAGGED, and a jump (a pop) is never judged at all, so the summary the rule book reads counts none of them (BUGS 428)
- **R12-3** (B) Clip_check always exits 0, whatever it finds
- **R12-4** (A) A part spun a whole turn at a clip change passes the blend check on any animal with a blend of 4 ticks or more (BUGS 427)
- **R12-5** (B) The corner of a step or bank can sink into a box unseen, offline and in both game checks
- **R12-7** (B) "jaw to the hide" measures the nearest box of the prey, not the spot, so a jaw 2.6 px off its spot reads 0.0
- **R12-8** (B) The pair check takes 75 s for one holder and prey
- **R12-9** (B) A bone the new clip does not move goes home over GeckoLib's 5-tick reset, not the blend
- **R12-10** (B) The blend length is read from the Java by a pattern that misses three animals and every blend changed while playing
- **R12-11** (B) The picture drawn for a flagged clip change shows a different moment from the one flagged
- **R12-13** (B) A spot table can be behind its plan or the posing code and `--check` still passes
- **R12-14** (B) Names in a plan's "checks" are never checked, and a misspelt one silently switches its check off
- **R12-15** (B) Clip_check redoes every clip on every run; the monitor takes about 9 minutes
- **R12-16** (B) Pair_report names the lion's strike clips and reads the jaw from another section of the plan
- **R12-17** (C) Pair_report's "a tick" numbers mix in the in-between time
- **R12-20** (A) The lab's floor sinks with the lowest foot, so feet pushed into the ground are never reported (BUGS 426)
- **R12-21** (B) The loop-seam and snap checks read only rotations; a position or scale jump at the loop passes
- **R12-23** (B) The lab's sizes and speeds are its own hand-typed tables, not the game's
- **R12-24** (B) A standing clip's sliding foot is only a note, so the summary says "0 problem(s)"
- **R12-27** (B) The kit returns a miss but every newer maker throws it away
- **R12-28** (B) The kit's clip cache leaves out the data files a maker reads, so a changed outline keeps old clips
- **R12-32** (B) Whether a clip loops is decided twice, by the Java and by the file, and the tools read only the file
- **R12-35** (A) Verify_animal says "passed" for an animal it never filmed in the game
- **R12-36** (A) A game crash during the films is not a hard failure
- **R12-37** (A) A course scene with no recording, or almost none, reads as "nothing found"
- **R12-38** (A) Nothing checks that the animal did what the scene asked, and the game's own warnings are never read (BUGS 429)
- **R12-39** (B) The guard films' headline says "ALL CHECKS PASS" with every picture changed
- **R12-40** (A) A finding at an approved picture is let through however much worse it is
- **R12-41** (A) The camera's jump check cannot see a pop that repeats
- **R12-42** (A) The watch mode films old spot tables and old body plans after they change
- **R12-43** (B) "frames N" does nothing for animals: N identical pictures a tick
- **R12-44** (B) The camera's jump check measures "per tick" wrongly in three cases
- **R12-45** (B) A fish held in a mouth is checked and recorded at the wrong place
- **R12-46** (B) Two films at once, an open watch session, and no timeout
- **R12-47** (B) The frame watch runs in every player's game and samples the drawing thread
- **R12-48** (B) The recorder writes the blocks only round where each animal was first drawn
- **R12-49** (B) Insects in a running scene are drawn at a stale in-between time
- **R12-50** (B) A frame film whose checks saw no animal is still "ok"
- **R12-52** (C) A contact sheet of one burst swallows another burst that starts with the same name
- **R12-53** (C) `film.sh` waits "successfully" after a failed reload
- **R12-54** (C) The step marker turns every chat system message into text in normal play
- **R12-55** (C) The report key's picture can be moved while it is still being written
- **R12-58** (A) After a step down the otter skates on about a block with its legs frozen (a move's "rest" keeps moving) (BUGS 432)
- **R12-59** (A) An animal seen mid-move is first drawn at the move's start, then jumps to where it really is
- **R12-60** (A) A Nile monitor more than 48 blocks away pops a block up or down on every move
- **R12-61** (A) When the server runs slow, moves on screen rewind (BUGS 380, 432)
- **R12-62** (B) The animals' clip player drops the bit past a loop's end each time round (the insects' player was written to fix exactly this)
- **R12-63** (B) An otter holding a crab or frog reads and parses a body plan file twice every frame
- **R12-64** (B) A lioness's throat hold and hold-down bite on a clock the drawn clip does not keep *Not a bug after all.*
- **R12-68** (C) A missing spot table is silently no table
- **R12-70** (A) The monitor's drawn leg fold turns its right hind foot the wrong way (a Java copy of the swim pose, "kept in step by hand", is out of step)
- **R12-71** (A) The spot tables and the pair check never see what the game draws on top of the clips, so a throat hold bites the air with every check green
- **R12-72** (B) The move files have no staleness check (the spot tables do), and their unit test reads only the monitor's
