# M8: Matabele ants and the first fights (plan)

Planned with the user 2026-10-18 (no code yet). Every decision is in `docs/DESIGN.md`,
"Matabele ants and the first fights". Starts after brain step 4g is finished.
Each step is tested in-game by the user before the next; the fine details of each
step are planned with the user at its start (sizes in numbers, health, damage,
timings: all "my pick, to confirm" until then).

## The real animal (Megaponera analis, Erik Frank's studies)
- Majors about 16-18 mm, minors about 5-10 mm, few in between; colony 400-2,500 (100-2,300 by Yusuf 2026, R1);
  one wingless (ergatoid) queen; winged males.
- Eats only termites. Lone scouts find termites feeding under mud cover, lay a scent
  line home and lead a column of 200-500 back; 2-4 raids a day, morning and late
  afternoon, usually within about 20 m, at most about 50 m.
- Majors break the mud, minors go in and sting (paralyse); majors carry stacks of
  up to about 10 termites home.
- Hurt ants call for help by smell; nestmates pull termite soldiers off and carry
  them home; lightly hurt tuck their legs in, badly hurt flail and are left.
- Wound care by any nestmate (licking, a germ-killing gland liquid): infected wounds
  kill about 80% untreated, about 10% treated. Ants walk on 4-5 legs after losses.
- Nests in old termite mounds, under logs, in the ground; shallow; dig little;
  move house now and then; new colonies by splitting.
  (Yusuf 2026, R1: they build no nest, they modify spaces other animals made, about
  0.7 m down; one brood chamber, two or more entrances; move every 2-3 months.)

**Before step 1: M7.5, giant savanna trees** (user, 2026-10-18; `docs/DESIGN.md`,
"Giant savanna trees"): the fallen hollow giants are the Matabele log homes.

## Steps
1. **Dead mounds and the ant.** In pieces (planned with the user 2026-10-24, decisions in
   `docs/DESIGN.md`, "Dead mounds, details"), each tried in-game: **1a** dead mounds (the
   colony's death leaves the mound; spires crumble in days, the body to a grassy hump in
   20-40 days; *Xylaria* takes the comb, look first in the model viewer; holes open into
   the shaft and galleries); **1b** world generation of living and dead termite mounds in
   savannas; **1c** the Matabele species file, sizes and the queen; **1d** the shallow nest
   (dead mound, under a fallen tree, or the ground; digging in where needed); **1e** the
   prey pile and hunger; **1f** Matabele colonies in new land and a command. The original
   outline: Dead termite mounds (a dead colony's mound stays,
   empty, wearing down; some placed by world generation). The Matabele species
   profile (`colony_fission`), the ant with its own size from the real spread (one
   placeholder model, scaled), the queen, a shallow nest that reuses ready spaces
   (dead mound, under a fallen tree, or a few rooms in the ground), the prey pile
   (goes off in about a day), hunger, world generation in savannas, spawn egg and
   command.
   **Pieces 1c to 1f planned with the user 2026-10-24** (decisions in `docs/DESIGN.md`, "Matabele colony, details"):
   - **1c, the ant gets a colony:** `megaponera_analis.json` (castes major and minor, health and damage, how it eats and
     grows, founding `colony_fission`), the wingless (ergatoid) queen with her own model (a major's body, a swollen rear;
     the look picked in the model viewer first), her eggs hatching into larvae and then majors or minors in the real
     two-humped mix, each ant its own size inside its caste. Test: summon a queen, `/antcolonies list`, watch the
     eggs, larvae and sizes. The colony has no nest yet (1d).
   - **1d, the shallow nest (as built, after R1 and the user's last answers, 2026-10-24):** the queen picks once, within
     32 blocks, best first: a dead mound (used as it is, nothing dug, 1 colony), under a fallen log 3+ thick (one 3x3 room
     1 block down, the log its roof, 2 colonies at most), at the foot of any tree 3+ thick, dead or living (the same room,
     1 colony), else a burrow stand-in in bare ground (a tunnel sloping in to one room 3 down, 3+ blocks from another burrow, rooms
     never touching). The queen digs nothing; her workers dig the way in. Test: a queen near each (`MatabeleNestTests`).
     **Changed by the user 2026-10-24: dead mounds only for now** (logs, trees and burrows dropped; a queen with no
     dead mound within 32 blocks waits and looks again; `docs/DESIGN.md`, "Dead mounds only, for now").
     Still to add for step 1: two or more entrances, guests in the nest, returning to an old nest.
   - **1e, the prey pile and hunger:** the pile of dead termites in the nest, eaten by larvae and adults, off in about a
     day; hunger by the colony rules already built; stocked by a command until raids exist (step 4). Test: stock it
     and watch it eaten and rot; leave it empty and watch them starve.
   - **1f, new land and a command:** a few colonies placed in new savannas (dead mound, under a fallen tree, ground)
     kept away from living termite mounds, real sizes 100 to 2,300 (R1), mostly numbers; a command that makes one anywhere
     with a size you choose.
     **Built 2026-10-24 (branch `claude/m8-newland-colonies-dcbea5`):** dead mounds only (the nest rule now), 2 in 3 of
     new land's dead mounds get a colony, sizes 100 to 2,300 leaning small, a day's prey on the pile; not kept away from
     living mounds (they lie about 96 apart anyway, real nests sit among the termites); `docs/DESIGN.md`, "Step 1f".
2. **The shared fight system** (species-neutral, the harvester ants use it later
   against their neighbours): health and damage per caste from the profile, the
   sting (paralysis), termite soldiers clamping on and riding along, legs bitten
   off (saved per ant, hidden on the model, 5- and 4-legged walks), stinging a
   player who provokes them, defenders in waves who give up past their territory.
   Ideas from the user (2026-10-23, planning, liked): the attack picked by where the
   termite is (grab and sting under when it is in front, bite and stab down into a
   broken tunnel); antennae bitten off too; termite soldier heads left clamped on the
   ants' legs and pulled off at home; lost legs lying on the battlefield a while.
   Matabele heads coming off: not real, left out.
   **Decided by the user 2026-10-23:** the attack depends on where the termite is,
   sting or bite, as above (both clips already made). A "brutal sting" animation is a
   maybe for later (the user's idea), not part of step 2 unless asked.
   Dead Matabeles in the termites' tunnels (the user, 2026-10-23): never walled up
   with blocks (50 dead ants would mean 50 blocks placed); the termites carry the
   body away like their own dead. Where to (today termite bodies are eaten in the
   garden fresh or walled up where they lie): **dropped outside by the nearest hole**
   (the user, 2026-10-23), fading after a while; later food for the savanna's
   scavengers (M9.5, the user's ecosystem plan). The termites' own old dead the same
   (the user's "yea" to both, 2026-10-23), in place of walling them up; fresh ones
   still eaten in the garden.
   Replaces `Alarm`/`Soldiers` using `TermiteSoldierEntity` directly with a shared
   defender idea.
   **Pieces, planned with the user 2026-10-24** (decisions in `docs/DESIGN.md`, "The fight system, details"):
   - **2a, the core:** health and damage per caste from the species files (numbers my picks, to confirm; a major
     Matabele rarely dies to one termite, a Macrotermes major soldier is the termites' strongest), a shared defender
     idea and "who is my enemy" question in the framework in place of `TermiteSoldierEntity` named in `Alarm`/`Soldiers`.
     Nothing changes in-game (chickens and termite soldiers as today).
   - **2b, the sting:** a stung termite falls paralysed and twitches, found by its nestmates if left; the attack picked by
     where the termite is (grab and sting under in front, bite and stab down into a broken tunnel; both clips made).
     **The skirmish command (the user's pick):** `/antcolonies skirmish <ants>` drops that many Matabeles from the
     nearest colony (or new ones) at the nearest termite feeding spot (sheeting, feeding gallery, shallow gallery) to
     break in and fight; step 4's scouts and columns replace it.
   - **2a and 2b BUILT 2026-10-25** (branch `claude/m8-fights`; `docs/HISTORY.md`): the skirmish drops new Matabeles
   - **The attack clips redone 2026-10-25** (the user's report: the sting never reached): A swings the gaster forward under the body into the termite held in the jaws; B dives into a broken tunnel, hauls the termite up to the rim and stings it there; the queen has no attack until 2e.
     (they join the nearest colony within 32 blocks) rather than taking them from a colony's numbers; "found by its
     nestmates if left" moves to 2f (the termites' side); carrying the paralysed home is step 4.
   - **In-game test 1** (2a + 2b).
   - **2c, clamping:** termite soldiers clamp on and ride along, slowing and hurting; a soldier killed while clamped
     leaves its head locked on the leg (shown) until a nestmate bites it off in the nest.
   - **2d, lost legs (legs only, the user's pick: antennae losses are not measured for Megaponera):** saved per ant,
     the leg hidden on the model (the Matabele model already has its six leg bones), 5- and 4-legged walks from the gait
     maker, a little slower; the lost leg lies on the ground a while; 1 or 2 gone, rest about a day, then raid again.
   - **In-game test 2** (2c + 2d).
   - **2e, the player:** stung only when provoked (breaks into the nest, hits one, stands in a column), 2 hearts and the
     pain effect, armour as normal; defenders come out in waves and give up past the territory; the squeak.
   - **2f, the termites' side:** soldiers drum (`head_bang`), workers further in retreat and seal behind them, the
     soldiers shut out; those alive when the raid leaves wait by the seal and are let back in at night (the user's
     pick); dead Matabeles and the termites' own old dead carried out and dropped by the nearest hole, fading (fresh
     dead still eaten in the garden).
   - **In-game test 3** (2e + 2f), then merge.
   Tests: each piece runs `soldiers,termitefood,colony,away` (and `legs` for 2d, guard films for 2c and 2d); a full run
   before the merge (2a changes the shared alarm).
3. **Drawn-only ants** (brain step 4e, pulled forward): the column far from the
   player drawn by the player's game, almost free on the server.
4. **Scouts and raids:** lone scouts by day, the scent line home, the column to a
   termite feeding spot (sheeting, feeding gallery, shallow gallery, the holes by
   them; never the mound), majors opening the mud, minors in, termites as numbers
   in the galleries shown on break-in (`HiddenTrips`), the termites plugging and
   closing (`ClosedHoles`, danger memory), the column home with prey; 2-4 raids a
   day by hunger, morning and late afternoon.
5. **Rescue and wound care:** the help smell, pulling clamped soldiers off, carrying
   the hurt home (legs tucked), the badly hurt left; wounds and infection in the
   nest, care by any nestmate.
6. **Moving house:** every 40 game days per colony, the first move day random from 1 to
   40 (R1, the user, 2026-10-24), and on top when prey runs out nearby, after repeated attacks, when the
   nest floods or is too small; scouts pick the site, the
   colony walks there in a column with brood and prey.

**Order to playable (the user, 2026-10-24, "I just wanna get it playable before I go off to california"):**
finish 1e (prey pile) and 1f (new-land colonies), then 2 (fights), then 4 (scouts and raids), at first with real
ants only (up to about 50 a raid; **step 4 before step 3 confirmed by the user 2026-10-24**, "do raids first i
dont want to rush drawn only"), then the test revamp (`docs/plans/test-health.md`, step 4), then 3 (big columns of
drawn-only ants), 5 and 6.

Later (M9): splitting into daughter colonies nearby, winged males mating in other
colonies. After M8: harvester ant territory fights with their neighbours.
