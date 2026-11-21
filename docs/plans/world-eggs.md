# Eggs and nests already in the world (planned 2026-11-20, nothing built yet)

The user, 2026-11-20: "make it so there are already eggs spawned in and crocodile nests in the world", then: "it should be different per species how common the eggs are", asking whether real crocodiles keep their eggs together on one sand bank, and pointing out that monitors stay by the water, so eggs always in termite mounds would be a problem.

Animals are fun first (the `CLAUDE.md` rule): real behaviour where it makes the game better, numbers my picks, nothing checked against sources. Today eggs appear only when animals breed near a player; new land holds none.

## What the real animals do (from what I know, not looked up)

- **Nile crocodile:** the female digs a hole nest in sand or soft soil a few metres above the water, often on a sandbank, beach or dry river bed, buries 25 to 80 eggs and guards the spot for about three months. Good nesting beaches are scarce, so **many females often nest together on the same favourite bank, nests a few metres apart, year after year**. Some nest alone. So yes: mostly bunched on the best sand banks.
- **Nile monitor:** the female usually digs into the side of a **living termite mound** and lays 20 to 60 eggs; the termites seal the hole and the mound keeps the eggs warm. I believe they sometimes use holes in river banks or hollow trees instead, but I am less sure of that. Real mounds stand on dry ground, often well back from the water, so a female walks inland to reach one.
- **Tilapia:** breeding males dig nest pits side by side on a shallow sandy bed (a nesting ground of many pits), each guarding his own.
- **Snails, crabs, catfish:** snail egg clutches are small and everywhere along the edge; crabs carry their eggs; catfish spawn in flooded shallows after rain.

## The monitor problem (the game balancer's check)

The mod keeps termite mounds off the floodplain: no mound where the ground is 2 blocks or less over the water, and 60% less likely within 14 blocks of water (`termite_mound.json`, `places`). Mounds stand 60 to 100 blocks apart, two in three alive. So the nearest living mound is often 30 to 80 blocks from the river, and the monitors keep to the banks. The life-cycle plan already says a female with no mound within 64 blocks lays in a sand bank instead.

**My recommendation (the user decides, it goes partly against the real animal):** a termite mound when a living one stands within 40 blocks of the water, otherwise a burrow dug into the river bank above the waterline. A female walking inland to a mound becomes a rare sight worth seeing; most monitor eggs a player finds are in the banks; the mound clutches still tie the river to the termites. The other choices: mounds only (the female walks up to 64 blocks inland; some rivers have no mound in reach, so their monitors never breed), or banks only (simpler, but drops the termite trick).

## How common each kind is (all my picks)

Every clutch placed with new land gets a random age within its hatching time, so they hatch spread out over the days after a player finds them, not all at once.

Spacing is measured along the river (sand banks come about one every 80 to 120 blocks of river, `worldgen/SandBanks`). The user, 2026-11-20: my first picks were too many for rivers this big ("1 bank in 4" plus lone nests, and snail clutches every 24 blocks of shore); then, on hearing a nesting beach every 400 blocks: "that's perfect, I was thinking 250 to 350 blocks apart".

| Kind | Where | How common | Comes with |
|---|---|---|---|
| Crocodile nests | dry sand on a sand bank, 2 to 6 blocks from the water | **a nest site every 250 to 350 blocks of river (the user's):** 1 sand bank in 3; most sites a nesting beach of 2 to 4 nests 3 to 6 blocks apart, 1 site in 4 a lone nest | a guarding mother at half the nests (she hisses at players); the other half unguarded (she is off in the water), so a player can find some to look at |
| Monitor clutch | a living mound near the water, or a bank burrow (the question above) | rare: about one per 500 blocks of river | nothing (monitors leave their eggs); built with life-cycle step 5 |
| Tilapia nesting ground | sandy or silty shallows 1 to 2 deep | 2 to 4 pits together, about one ground per 300 blocks of river | a male in breeding colours on each pit (a pit with no male fades in 3 days anyway) |
| Snail clutch | pearly eggs on a stone, pebble or bank at the waterline | the commonest, but about one per 60 to 80 blocks of river (either shore), 1 or 2 together | nothing |
| Catfish eggs | on algae in the shallows and pans | about one patch per 200 blocks of river | nothing (tilapia and crabs eat most) |
| Crab eggs | carried, not a block | 1 in 6 crab females the world spawns already carries her eggs | the female |
| Bullfrog | tadpoles not built yet | later | |

(All my picks but the crocodiles' spacing; each kind's number in one data file so the user can change it.)

**Balance:** a guarding mother counts in the crocodile group's cap of 5 round a player, so near a nesting beach fewer other crocodiles spawn; hatching keeps the local limit (fewer than 6 crocodiles within 48 blocks; a nest lets out at least one), so a nesting beach of 4 nests adds a few young, not a swarm. Monitors raiding nests (step 5) and catfish eating the young keep them down.

## Steps

1. **Crocodile nesting beaches** (the user's first ask): placed with new land on the sand banks (`worldgen/SandBanks`), the nests' random ages, the guarding mothers. Tests: a survey over generated river land counting beaches, lone nests and mothers against the table; an unguarded nest hatches by itself. In-game: a new world, follow a river and look for bunched heaps on the sand banks.
2. **The small eggs:** snail clutches, catfish eggs, tilapia nesting grounds with their males, crab females carrying eggs. Same kinds of tests.
3. **Monitor clutches**, with life-cycle step 5 (the monitor's eggs are not built yet), by the user's answer to the question above.
