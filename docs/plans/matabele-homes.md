# Matabele homes beyond dead mounds (planning, 2026-11-06)

**Status: planning only, nothing built.** Look at every choice at real sizes in the home viewer:
`python tools/model_viewer.py nestsites` (writes `references/nest_sites_viewer.html`; each home cut open, the ants inside
at real size, an ant's-eye view, the dead-wood tenants against a player).

## The problem (the user, 2026-11-06)

Matabele colonies live only in dead termite mounds (the user's pick of 2026-10-24). The user likes how common those are
now, and new land should keep them exactly as they are. But when a colony moves (M8 step 6: every 40 game days, or when
its nest is wrecked or its prey runs out; later seasons too), it needs a dead mound that also has a living mound within
raid reach, and a session's simulation found that the next one can be **up to 300 blocks away**. Standing dead trees
fall within 10 to 20 days, so they are no good. Fallen trees looked solid, with no gap for the ants.

## What the real ants do

- *Megaponera analis* does not dig its own nest. It moves into spaces other animals made (Yusuf 2026, Annual Review
  of Entomology 71, section 2.2): old termite mounds, partly rotten logs, under rocks, under trees, open ground in
  old burrows. Nests are shallow, with two or more doors and one brood room.
- A study in Nigeria (73 nests) found about half in deserted *Macrotermes* mounds and most of the rest in the ground
  under bushes. **The dead mound is their favourite, not their only home.**
- Nests are 2 to 30 m apart, 4 to 22 per hectare. A move goes 10 to 30 m (at most about 95 m, their foraging reach),
  led by scouts in the evening, with stops every 5 to 10 m. Colonies may go back to an old nest months later.
- A Matabele nest is easy to find by the **heap of termite heads and body parts** outside its door.
- So in a real savanna there is nearly always a free home within 30 m. The 300-block gap is the mod's limit, not
  nature's. The fix is **more kinds of home**, with the dead mound still the first choice.

## Sizes change everything (real sizes, 2026-11-05)

A major is 0.5 blocks long, a minor 0.25, the queen 0.6, so **1 block is 30 mm of ant**. A 1-block tunnel is
3 cm wide, about a real Matabele tunnel. A room 4 x 1 x 2 blocks (12 x 3 x 6 cm in ant terms) easily holds a queen,
her brood and a few dozen real ants; the rest of a big colony are numbers anyway (up to about 50 real mobs).
So the hollow inside a fallen giant (2 blocks wide, 1 to 2 high over the frass) is a great hall for them, and the
2026-10-24 reason for leaving logs out ("the ants are too big") no longer holds.

**Fallen giants are already hollow.** `DeadWood.fallen` (`savanna/.../tree/DeadWood.java`) gives every grown dead
tree a hollow through its trunk, beetle galleries through the wood (some reach the bark: side doors) and frass on the
floors. A tree that died of old age, fire or lightning lies hollow for good. One killed by rot crumbles to pulp a few
days after it is down (a forced move, as real colonies leave rotting-away logs).

## Every choice looked at (verdicts are my picks)

| # | Home | Real? | In blocks | Lasts | Verdict |
|---|---|---|---|---|---|
| 1 | Dead termite mound | Yes, the favourite | Built | Years | **Keep as it is, first choice** |
| 2 | Hollow fallen giant | Yes ("partly rotten logs") | Already in the world | For good (rot logs: days) | **Add, second choice** |
| 3 | Room under a fallen log | Nearly (they borrow, here they dig 1 block: the departure the user liked) | Built once on 2026-10-24, switched off | The log's life, then a burrow | Maybe: cheap, logs 3+ across |
| 4 | Old aardvark burrow | Yes (ground, borrowed holes) | Sloped burrow and soil ramp built, switched off | Fills in slowly once empty | **Add: the gap fixer**, needs no mound or tree |
| 5 | Ground squirrel / springhare warren | Yes, side chambers | Small holes and tunnels | As a burrow | Later (M9.5, with the animals) |
| 6 | Root hollow at a living tree | Yes ("under trees") | Room among the roots under the trunk | As long as the tree (decades) | **Add: lasting, common by big trees** |
| 7 | Under a boulder | Yes ("under rocks"); the stone warms the brood | Our savannas have no boulders | For ever | **Your call**: a new look for the savanna |
| 8 | Under fallen branches | Shelter only, not on the list | Branches are there | Termites eat them | No |
| 9 | Old nest left behind | Yes, they return to old sites | Free: every move leaves one | The room stays, the heap fades | **Add** |
| - | Standing dead tree | Possible | Falls in 10 to 20 days | Too short | No (the user's point) |
| - | A living termite mound | Not reported for *Megaponera* | | | No |

## How it would work (Minecraft side)

- **The home book** (like the dead mound book): every possible home is a row: its kind, its room cells, its doors,
  how big, the day it was made, the day it was last lived in, who lives there now. New land fills it as it is
  made (burrows, root hollows), events add to it (a giant falls hollow, a colony moves out), and moving out of
  sight reads only the book, never the blocks of unloaded land.
- **Rooms are plain air** in soil or wood. Nothing ticks; no special block that can decay. When a chunk loads, the
  row is checked against the blocks (a player may have filled it in or dug it open); a home gone is crossed out.
- **Doors:** at least two, as real nests have. The termite-head heap goes outside the main door: it marks a lived-in
  nest for the player, and stays a while on an old one.
- **Wear (my picks, to tune):** an empty burrow fills in over 60 to 120 days, a lived-in one is kept open; a low
  burrow can flood in heavy rain (forced move); fire never reaches underground but can burn a log home (forced move
  if the log is charred through).
- **New burrows keep coming:** until the real aardvark exists (M9.5), a burrow is made now and then out of sight,
  slowly enough to keep the land's number steady. Then the aardvark digs them for real: it breaks into termite
  mounds (more dead mounds) and leaves burrows (more Matabele homes), the real savanna loop.
- **New land is unchanged:** new colonies still start only in dead mounds, so their chances stay exactly as the user
  likes them. The other homes are only for moving (step 6) and later for colonies splitting (M9).

## Choosing a home (the ant side)

The scouts look over the homes within reach in the evening, best first: a dead mound, then a hollow giant, a root
hollow, a burrow, an old nest of their own. A home scores higher when it is (my picks, from what is known of house
hunting in other ants, since it is not studied in *Megaponera*): roomy enough for the colony, dry (not low ground in
the rain), dark, with two doors, close to living termite feeding spots, and not already full (today's limit per site
stays: 1 per dead mound, 1 at a tree's foot, 2 under a long log). The colony goes in a column at dusk, carrying the
queen, brood and its nest guests, stopping every 5 to 10 blocks.

## Balance (checked before anything is built)

- **Targets:** almost every colony has 3 or more free homes within about 40 blocks (real moves 10 to 30 m), and every
  home is within raid reach (90 blocks) of a living mound. That needs about 6 possible homes per 100 x 100 blocks,
  which is still fewer than the real 4 to 22 *nests* per hectare.
- **Starting numbers (my picks):** burrows about 2 per 100 x 100 blocks; a root hollow at about 1 grown tree in 3;
  hollow giants as many as fall; old nests from every move.
- **How it is checked:** `tools/raid_balance.py` gets a "nearest free home" count over real new land, today against
  each added kind, and the report says which targets fit and which do not.

## The other dead-wood life (the user's second question; M9.5)

At real sizes a block is a room for a bug, so blocks can look good. Ideas, shown in the viewer's last two corners:
- **Bugs kept as numbers inside the log**, the way colonies already work; real ones come out at night, or when the
  player breaks the bark. No need for tiny holes everywhere.
- **Loose bark plates:** a thin plate standing a few pixels off a log face, with a gap behind (real: scorpions,
  centipedes, cockroaches hide there). **Gallery blocks:** log block models with a tunnel through them, like the
  tapering branches. Bark-beetle marks and exit holes (oval for jewel beetles, round for longhorns) as textures.
- **Real savanna tenants:** longhorn and jewel beetle grubs (they made the galleries already in our logs),
  *Scolopendra* centipedes, bark scorpions, darkling beetles, giant millipedes.
- **The scale problem (a decision for M9.5):** on the ants' scale a 13 cm centipede is 4.3 blocks long and a 20 cm
  millipede 6.7, bigger than a player is tall. Bigger animals will need a squeezed scale of their own; the viewer's
  slider shows how much.

## Steps (all part of M8 step 6, moving house, after the finish line)

- **H1 The home book** with what exists already: hollow giants (registered as they fall and in new land) and old nests
  (every move leaves one). No new blocks. Tests: a hollow giant is found and its room checked.
- **H2 Burrows** in new land (reusing the switched-off burrow code and soil ramps, the spoil fan, two doors), wear and
  slow renewal.
- **H3 Root hollows** at big trees.
- **H4 Choosing a home**: the scouts and the scoring above, feeding step 6's move.
- **H5 Balance and tests**: the balance check, a test that a colony with no dead mound in reach still finds a home.
- Optional: the room under a log (cheap); boulders (your call); warrens (M9.5).

## My picks

New land keeps dead mounds only; the home book as its own colony-free part in the savanna mod (other species can use
the same idea for their own homes later); rooms as plain air with a check on load; burrow and tree numbers above;
wear times above; the order H1 to H5 (H1 alone already gives homes almost everywhere big trees have died).

## Your calls

1. **Burrow holes in the savanna:** a burrow mouth is a visible hole with a fan of dug-out soil beside it, about
   2 per 100 x 100 blocks. They change the look of the land a little.
2. **Boulders and rock outcrops (kopjes):** a new look for the savanna, never decaying homes. Only if you want rocks.
3. Later (M9.5): how much bigger animals are squeezed down from the ants' scale.
