# Full code review, round 6: the Matabele ants, all but the raids (reading round, 2026-11-26)

Read on branch `claude/review-round-6` at commit `b72cba68` (the tip of `claude/review-fix-12`, the branch `C:\Ant Mod`
was on), by a session that did not write the code. Rule book: `docs/plans/code-review.md`. No code was changed; no
gradle, game, game test or film was run, so **nothing here is measured**: every lag finding is "to measure" unless
the path through the code proves its cost.

**What was read, every line:** in `savanna/.../colony/`: `MatabeleNest`, `MatabeleColonies`, `PreyPile`, the Matabele
rows of `SavannaParts`, `WayHome`, and `DeadMounds` and `DeadWorks` for what a Matabele colony living in a dead mound
meets; in `savanna/.../entity/`: `MatabeleEntity`, `MatabeleQueenEntity`, `MatabeleLoads`; in
`savanna/.../entity/goal/`: `BringPreyGoal`, `CarryRemainsGoal`, `MatabeleBiteOutGoal`, `MatabeleWayHomeGoal` and
`Headway` (for a hurt ant and a carrier; round 11 read them for the raids); `block/PreyPileBlock`,
`TermiteRemainsBlock`; `species/InsectSizes` and the Matabele, non-raid records of `SavannaNumbers`; the species file
`megaponera_analis.json` (all but the raid, scout and trail numbers); the Matabele's registration, tags and spawn
eggs; where new land gives a dead mound its colony (`NewLandMounds`, `TermiteMoundStructure`, in the parts that name
the Matabele). The rescue and care code is the framework's, with the Matabele its only user, so it was read here as
they use it: `RescueGoal`, `WoundCareGoal`, `ClampCareGoal`, `Wounds`, `Clamp`, `LostLegEntity`, `LostLegs`, `Pain`,
`NestDefence`, `DefenceNumbers`, `FightGoal`, `EatGoal`, and the wound, clamp, carry and size parts of
`AbstractAntEntity`. About 5,500 lines read in full, and the calls out of them followed as far as each finding
needed (each reader's file ends with its exact list, and with what it did not finish). Not read (other rounds): the
raid code (round 11), the termites (round 5), the framework as such (rounds 1 to 4).

**Who read it.** Three readers with a slice each (nests, queen, brood and the prey pile; rescue, wound care, clamps
and lost legs; the entities, the size spread, defence, the sting and the species file) and one reader across all
of them on one question: **"who has the ant?"**, that is, can two goals, or the colony's orders and a goal, take one
ant in turn or at once, and is anything dropped when an ant changes hands. Their own files are kept in
`docs/reviews/full/round-06-readers/` (their findings carry the letters N, C, D and X there). The lead session then
read each grade A finding's lines itself (each says "checked again by the lead" and what was read), merged what two
readers found from two sides, and changed four grades: two of the readers' grade A findings are grade B here
(R6-4, a new side of R1-26; R6-21, which round 1 already lists as R1-28).

**Not written again here:** what rounds 1, 2, 3 and 11 already list. Round 3 (the insect body, on the branch
`claude/review-round-3-a86aa8`, not merged here) read the same framework goals and wrote R3-35 (rescue has no claim on
the caller), R3-37 (a walking player is almost never stung), R3-38 (a hurt ant that goes into the numbers comes back
whole), R3-41 (a clamp nearly always costs the leg before care) and R3-30 to R3-36 on the goals' turns. Where a
finding here is a new side of one of those, it says so.

**How to read a finding.** Rating: **A** the player can meet it, **B** it will cost us when building on this,
**C** a tidy-up. Proof: **proven** (the path was walked through the code, lines named), **likely** (one link, named in
the finding, was not checked), **to measure** (a lag finding with no number yet). A finding's `R` number is its only
number. Findings keep their numbers for good; a fix session adds a status line under a finding and never rewrites it.
Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`.

**Count:** 33 findings: 3 A, 19 B, 11 C (40 in the readers' files; 7 were the same thing found from two or three
sides and are merged, each merged finding says so). Eight of them wait for the user's call.

## The short of it

**No lag was found.** No Matabele goal searches blocks, mobs or routes every tick; the ant's own tick does two cheap
checks; nest sites and the prey pile's away side never read land that is not loaded. The one place where work grows
with the square of the ants (idle ants looking for callers and patients, R6-26) is cheap and only to measure. This is
the first round with no lag finding of grade A or B.

**No crash in single player, nothing doubled, nothing left hanging.** A load in the jaws always finds its goal again;
a carried ant is moved by one thing only; wounds, lost legs, clamps and a carried nestmate all live through a reload.
The goal table is sound: nothing flip-flops.

What is wrong lies in three places:

1. **Multiplayer was never met (R6-1).** A second player joining a world is thrown out (or crashes) when a Matabele
   worker comes into view: the worker asks for its size spread from a file only the server has. One line mends it.
2. **"Is this ant fit and free" is asked in nine places by nine rules (R6-7),** and each new state (the wound, the
   carried ant) was added to some and not to others. So a major with a fresh wound may not raid but is sent scouting
   alone, past the minutes in which a lick decides its wound (R6-3); the nest's defence calls out the wounded first
   and ants with a nestmate in their jaws (R6-6); the ants licking the wounded are the first taken for a raid (R6-8);
   a limping ant stops to call for nestmates that can never come (R6-5). One shared question would replace them all.
3. **The colony's food sums do not close.** A colony that lost every worker keeps its queen for ever and its mound
   stays taken (R6-2); "hungry" counts only the adults, though the queen and the young eat about four fifths of the
   food (R6-17); the pile's count and its heaps can drift apart (R6-19); eggs become workers in minutes (R6-18).

**No part is a candidate for a rewrite.** The goal table is "patchy, not a rewrite" (its own section below): a
tidy-up of a few dozen lines across about ten files, the one shared "may this ant be called away" question.

## The four that matter most

1. **R6-1:** a second player (LAN guest, or anyone on a server) is thrown out when a Matabele worker comes into view.
2. **R6-3 with R6-6, R6-7 and R6-8:** the hurt are not left to rest: sent scouting, called out first to defend, and
   their carers taken for raids. The care the user saw built is skipped for the ants it is for.
3. **R6-2:** a Matabele colony that lost every worker never ends: a lone queen for ever, the dead mound taken for good.
4. **R6-13 (the user's call):** standing still at a provoked nest without armour, a player dies in about 2.5 seconds;
   walking, almost never stung (R3-37). The defence is either harmless or deadly.

## The Matabele worker's goal table (`MatabeleEntity.registerGoals`, `MatabeleEntity.java:194-227`)

Lower number wins. A goal can push out only a running goal of a **higher** number that shares a flag with it; goals
of the same number never push each other out. "Every tick" means `requiresUpdateEveryTick`; the others tick 10 times
a second. No goal overrides `isInterruptable`, so every one can be pushed out. The column "Asked" is the order the
game asks `canUse` in one pass: the order of the `addGoal` lines, not the priority.

| Asked | Goal | Number | Flags | Every tick | What starts it (its `canUse` in short) |
|---|---|---|---|---|---|
| 1 | `FloatGoal` | 0 | JUMP | yes (Minecraft's own, from memory) | in water |
| 2 | `MatabeleBiteOutGoal` | -1 | MOVE, LOOK | yes | out of its own nest, under a roof, shut in, two looks 10 ticks apart |
| 3 | `FightGoal` | 0 | MOVE, LOOK | no | a fight order (`getFightSpot`), or bitten by something within 8 blocks; never while `fightsNoMore` |
| 4 | `EatGoal` | 1 | MOVE, LOOK | no | hungry, jaws empty, not on a raid errand, 1 chance in 10 |
| 5 | `FeedQueenGoal` | 2 | MOVE, LOOK | no | holds nurse food (always), or looks for work and claims the queen's feeding |
| 6 | `FeedLarvaGoal` | 2 | MOVE, LOOK | no | jaws empty, looks for work, a hungry young one claimed |
| 7 | `CarryEggGoal` | 2 | MOVE, LOOK | no | holds an egg (always), or looks for work and a loose egg |
| 8 | `CarryRemainsGoal` | 2 | MOVE, LOOK | no | holds remains (always), or looks for work and claims a load |
| 9 | `ClampCareGoal` | 2 | MOVE, LOOK | no | in the nest, every 40 ticks, 1 in 3, a clamped nestmate at rest, claimed |
| 10 | `RescueGoal` | 2 | MOVE, LOOK | no | holds a hurt nestmate (always), or every 20 ticks a caller within 6 blocks |
| 11 | `WoundCareGoal` | 2 | MOVE, LOOK | no | in the nest, every 40 ticks, 1 in 2, a wounded nestmate at rest, claimed |
| 12 | `MatabeleScoutGoal` | 1 | MOVE, LOOK | no | once a second: `Scouts.mayScout` and a planned trip, or a pushed-aside trip to take up again |
| 13 | `MatabeleRaidGoal` | 1 | MOVE, LOOK | yes | a member of the colony's raid, no fight order, not `fightsNoMore` |
| 14 | `MatabeleWayHomeGoal` | 1 | MOVE, LOOK | yes | on the colony's way-home list, no fight order, and hurt or not a raider |
| 15 | `BringPreyGoal` | 2 | MOVE, LOOK | no | holds stung termites, not in a column |
| 16 | `DigGoal` | 3 | MOVE, LOOK | no | looks for work and a block to dig (asks only "no soil in the jaws") |
| 17 | `GoInsideGoal` | 4 | MOVE | no | may not go out, not in a room, 1 in 10 |
| 18 | `WaitAtNestGoal` | 5 | MOVE | no | may not go out, outside and more than 5 from the entrance, 1 in 10 |
| 19 | `NapGoal` | 6 | MOVE, LOOK | yes | idle, by its dice |
| 20 | `GroomGoal` | 6 | MOVE, LOOK | yes | idle, by its dice |
| 21 | `AllogroomGoal` | 6 | MOVE, LOOK | yes | idle, a nestmate beside it |
| 22 | `AntWanderGoal` | 7 | MOVE | no | nothing else (rests first) |
| 23 | `RandomLookAroundGoal` | 8 | MOVE, LOOK (Minecraft's) | no | nothing else |

**The queen** (`MatabeleQueenEntity.java:76-88`): 0 `FloatGoal` (JUMP); 1 `FightGoal` with a fight-back reach of 3
(MOVE, LOOK; only `NestDefence.guardTheQueen` gives her an order, 40 ticks at a time); 2 `QueenToRoomGoal` (MOVE);
3 `GoInsideGoal` (MOVE); 4 `WaitAtNestGoal` (MOVE); 5 `NapGoal`, `GroomGoal` (MOVE, LOOK, every tick); 6
`AntWanderGoal` (MOVE, only while no room is finished); 7 `RandomLookAroundGoal`. No goal of hers carries, cares or
is drafted; nothing in her list can fight another.

**What stands outside the table and can take the ant anyway:**

1. **Frozen (`isImmobile`, `AbstractAntEntity.java:1501-1505`):** paralysed, held in a blow, clamping on, a clamp
   being worked on (`clamp.isHeldStill`), **calling for help**, **tended**, **carried** (riding a nestmate). While
   any holds, no goal is asked, ticked or stopped: every goal freezes as it is, with no `stop()` (round 3).
2. **The fight order** (`orderToFight`, `:1917-1935`): a spot, an end time and who gave it (`fightOrderBy`). Given by
   the raid (`Raid.java:1839`), the nest's defence (`NestDefence.java:203`, `:266`, `:353`) and the skirmish
   command. This is the only real "owner slot" the ant has, and it is for fights only.
3. **Three lists in `Raids`,** kept by the ant's id: scouts out (`Scouts`), the raid's members (`Raid`), ants going
   home (`WayHome`). The three priority-1 goals only read them.
4. **What is in the jaws** (`hasFullMandibles`): the unwritten rule that holds most of this together. Every drafter
   but the nest's defence leaves an ant with full jaws alone, and every carrying goal starts again by itself when it
   finds its load still in the jaws.
5. **Care claims on the patient** (`claimCare`, 40 ticks, renewed), **the call** (`callingUntil`), **the tender**
   (`tendedBy`, 20 to 40 ticks): all on the hurt ant, all with their own end time.
6. **Going into the numbers** (`Population.goIn`, `AwayLife.foldLoaded`, `foldInStray`).

---

## Grade A

### R6-1 (A, likely; the one link not run is named): a second player joining over LAN, or anyone on a server, is thrown out (or crashes) when a Matabele worker comes into view

**Checked again by the lead.** Read: the constructor (`AbstractAntEntity.java:286-290`, `applyBodySize()` runs on
both sides), `applyBodySize` (`:305-313`, calls `ownSize()`), `MatabeleEntity.ownSize` (`:295-299`: its first line is
`sizes()`, before it looks at `lengthMm`), `sizes()` (`:236-239`, `SpeciesProfiles.get(SPECIES)`), and
`SpeciesProfiles` (`:24` the map starts empty, `:76-81` `get` throws, the class comment "Server side only"). No code
in either mod's `client/` or network code fills the map on a client (searched). It holds. Still "likely", not
"proven", for one reason: nobody ran a second client, so whether the guest is disconnected or crashes is not known.

- **What is wrong:** the worker's constructor asks the species file for its size spread, and the species files exist
  only on the server. On a game that is only a client (a friend joining a LAN world, anyone on a dedicated server) the
  answer is not there and the code throws.
- **What the player would see:** single player and the LAN host are fine (the client shares the server's memory). A
  second player joining the world is disconnected or crashes the moment a Matabele major or minor is sent to them, so
  in practice as soon as they come near a Matabele nest. (A guest who opened a single-player world earlier in the same
  sitting would not crash: the numbers from that world stay in memory, see the static below.)
- **Where:** `framework/.../entity/AbstractAntEntity.java:286-290` (the constructor calls `applyBodySize()` on both
  sides), `:305-313` (`applyBodySize` asks `ownSize()` whenever the mob has the SCALE attribute, which every living
  mob has); `savanna/.../entity/MatabeleEntity.java:295-299` (`ownSize` calls `sizes()` before it looks at
  `lengthMm`), `:236-239` (`sizes()` calls `SpeciesProfiles.get(SPECIES)`); `framework/.../species/SpeciesProfiles.java:76-81`
  (`get` throws when the species is missing), `:17-20` ("Server side only") and `ColonyFrameworkMod.java:56` (the
  profiles are a server data reload listener, `AddReloadListenerEvent`).
- **The proof:** on a client, `Mob`'s constructor skips `registerGoals` but `AbstractAntEntity`'s own constructor still
  runs `applyBodySize()` (line 289). `getAttribute(Attributes.SCALE)` is not null for a living mob, so line 308 calls
  `MatabeleEntity.ownSize()`, whose first act is `sizes()`, which calls `SpeciesProfiles.get`, which throws
  `IllegalStateException("No species profile for antcolonies:megaponera_analis ...")` because `profiles` is empty on a
  client that never ran a server. No other insect's `ownSize` reads a profile (the base returns 1); the client code
  (`framework/.../client`, `savanna/.../client`) reads no profile either, so this one line is the only such path found.
- **Unchecked link:** what the 1.21.1 client does with an exception while making an entity from a packet (I expect a
  disconnect with a network error; it may be a crash). The game tests run on a server only and `runClient` is an
  integrated server, so no test or session of ours could have met it.
- **Fix proposed:** `ownSize` returns 1 while `lengthMm` is 0, before it asks for the sizes (a client never has a
  length; its SCALE comes from the server, which sends it). One line, no new rule.

**Status (2026-11-26, `claude/review-fix-a-4-6`): fixed: `MatabeleEntity.ownSize` answers 1 while the worker has no length yet, before it asks for the species file, so a screen (which never has a length, and has no species files) no longer throws; its size comes from the server's scale as before. Not run with a second client: no test or session of ours can (the game tests are a server, `runClient` shares the server's memory).**

### R6-2 (A, proven): a Matabele colony whose workers all die keeps its queen for ever, and its dead mound stays taken

**Checked again by the lead.** Read `Colony.isFounding` (`Colony.java:763-765`), `QueenCare.royalsGetHungry`
(`:458-461`), `isSpent` (`:263-266`), `hungerRunsOut` (`:441-449`), the queen's only death (`MatabeleQueenEntity.java:121-124`)
and `Colony.tick` (`:697`: a colony ends only queenless and empty); the species file has no `founding` section
(searched). The sum is as the reader says: with no workers, "satisfied" is always true and her clock starts over every
second.

- **What is wrong:** once every worker of a Matabele colony is dead (starved with no termites in reach, raids failing,
  old age), the queen alone counts as "founding", and a founding queen never gets hungry. She lives on in her room for
  ever. Real Megaponera queens are wingless and cannot found alone (fission only); a queen with no workers dies.
- **What the player would see:** a dead mound with a lone Matabele queen in it that never dies, year after year. Since
  one colony holds one mound (`MatabeleNest.java:118`), no other queen can ever settle there. In a world where raids
  often fail (BUGS 306), starving colonies are not rare.
- **Where:** `framework/colony/Colony.java:763-765` (`isFounding`: not queenless and no members besides co-founders);
  `framework/colony/QueenCare.java:458-461` (`royalsGetHungry` is false while founding), `:263-266` (`isSpent` is false
  with no `founding` section, which Megaponera has none of), `:441-449` (`hungerRunsOut`: `satisfied` true, so the clock
  restarts every second); `savanna/entity/MatabeleQueenEntity.java:121-124` (her only death is `queenStarves`).
- **The proof:** workers dead, so `getMemberCount() - coFounderCount() == 0` and `isFounding` is true; `royalsGetHungry`
  false; `isSpent` false (no founding); so `satisfied = !(false || false) = true`; `queenLastFed = now`; never starves.
  `Colony.tick` ends a colony only when queenless and empty (`:697`), so the colony too lives on.
- **Why it is so:** the rule was made for a spawn-egg queen waiting for spawn-egg workers to join her (design 2026-10-24;
  the test `MatabeleColonyTests.java:72` asserts it). Nobody thought of a colony that ran down to its queen.
- **The fix proposed:** one rule for a `colony_fission` species, no new number: a queen whose colony has had workers
  (its `bornAt` and any `eggsLaid`, or simply a count that once was above 0) and has none left is not founding: she
  starves on her own clock like any queen (5 days). The spawn-egg case keeps its grace. A test: a colony of 5 whose
  workers are killed; its queen dies within `starve_ticks` and the mound is free again.

**Your call (2026-11-26):** A Matabele colony has lost every worker (starved, killed in raids, old age). Today its queen lives on alone for ever, the colony never ends, and no other colony can take that dead mound. My pick: she starves on the file's own clock (5 days) once a colony that has had workers has none; a queen put down with a spawn egg, still waiting for her first workers, keeps her grace.

**Call made (2026-11-26):** yes, she starves (the user).

**Status (2026-11-26, `claude/review-fix-a-4-6`): fixed. `QueenCare` remembers whether the colony has ever had a worker (`hadWorkers`, saved; noted by her hunger clock once a second; an older save counts a queen who has laid as having had them). For a species with no founding of its own, `isSpent` is true once such a colony has none left, so `hungerRunsOut` runs her clock (the file's `starve_ticks`, 5 days) here and away alike; she dies, `ColonyManager.queenDied` ends the colony and `MatabeleNest.pick` gives the mound out again. `Colony.isFounding` is unchanged (36 other places read it). A queen still waiting for her first workers keeps her grace. Test: `MatabeleColonyTests` (the queen alone does not starve past her time; her 8 workers killed, she dies at her starve time, her colony ends, her mound is free). Not judged here: a queen holding a carried egg or beside a full nursery is not hungry, as for every queen.**

### R6-3 (A, the path proven; how often to measure): a major with a fresh wound, or badly wounded, is sent out scouting alone

**Checked again by the lead.** Read `Scouts.fit` (`Scouts.java:314-317`) beside `Raids.raiderFit`
(`Raids.java:1039-1043`): the raid's test has `!fightsNoMore()`, `!wounds().isOpen()` and `!needsRescue()`, the
scouts' has none of the three; a search of `Scouts.java` and `MatabeleScoutGoal.java` for the wound finds nothing. It
holds. The reader across the slices found the same from its side (the table in R6-7). **No call needed:** the design
already says "the hurt rest until the wound closes" (the comment at `Raids.java:1042`); the fix makes the scouts keep
the rule the raids keep.

- **What is wrong:** two places judge "fit to go out" and disagree. A raid never takes an ant with an open wound, a
  head on its leg or one badly wounded (`Raids.raiderFit`, `Raids.java:1039-1043`: `!fightsNoMore()`,
  `!wounds().isOpen()`, `!needsRescue()`, the comment "the hurt rest until the wound closes"). The scouts' own test
  asks only "a major, not crippled, no clamp, not downed, no fight spot, empty jaws" (`Scouts.fit`,
  `Scouts.java:314-316`). So a major with one or two legs gone and a fresh wound, or one at 40 percent of its health
  or less (`MatabeleEntity.badlyWounded`, `:362-364`), is a fit scout. The colony's old scouts go first
  (`mayScout`, `:281-288`, `veteranReady`), and the old scout is the one that led the last raid (`find.scout =
  leader`, `Raids.java:1035`), the ant most likely to come home hurt.
- **What the player sees:** the morning after a raid, a major limping on five legs walks out of the nest alone for a
  trip of up to 4 minutes (`trip_ticks` 4800). Out there nobody licks it, so when its infection is decided (6,000
  ticks after the wound) it gets the untended odds, 8 in 10 infected instead of 2 in 10; an infected one that is out
  past 11,000 ticks also misses its gland dab. The care the user saw built is skipped for exactly the ants it is for.
  A badly wounded scout fights nothing on the way (`FightGoal.canUse` says no while `fightsNoMore`).
- **Where:** `savanna/.../colony/Scouts.java:261-289` (`mayScout`), `314-316` (`fit`), `707` (the scout command uses
  `fit` too); `savanna/.../entity/goal/MatabeleScoutGoal.java:204` (asks `mayScout`), `330-333` (`fitToGoOn`: the same
  holes, so a wound opened while out does not end the trip either, which is right); against
  `savanna/.../colony/Raids.java:1039-1043`.
- **The proof:** `MatabeleScoutGoal.canUse` (190-210) asks `part.scouts().mayScout(level, ant)`; `mayScout` asks
  `fit(ant)` and the colony's hunger and hours; `fit` has no wound, `needsRescue` or `fightsNoMore` test. Nothing else
  on the way checks the wound (grep of `wounds` in `Scouts.java` and `MatabeleScoutGoal.java`: none).
- **Fix proposed:** one test of "may leave the nest for a colony errand", used by both: `Scouts.fit` calls
  `raiderFit` plus "a major" (fewer rules: one judge, not two). Then the scout command finds another major.

**Status (2026-11-26, `claude/review-fix-a-4-6`): fixed: one judge of "may leave the nest on a colony errand": `Scouts.fit` is `Raids.raiderFit` and a major, so a major with an open wound, badly wounded or wanting rescue is no scout and the scout command takes another. A trip already under way still goes on when the scout is wounded out there (`MatabeleScoutGoal.fitToGoOn`, as the finding says is right). Checked in `eachScoutKeepsItsOwnWay...` (the command passes every wounded major over; a freshly wounded one may not set out).**

---

## Grade B

### R6-4 (B, the path proven; how often in play likely): a termite soldier clamped on a raider's leg vanishes when its mound is far from every player (a new side of R1-26)

**In play (2026-11-27):** C. A termite soldier clamped on a raider's leg vanishes when its mound is far from every player.

**The lead's grade: B, not the reader's A.** This is R1-26 (grade B, not started) seen at the clamp: the far rule
folds a colony's loaded members with no "can a player see it" check. R1-26's fix (ask before folding) mends this
case too; the reader's one-line guard below is worth adding with it. The lead read `AwayLife.frozenOf` (`:270-276`:
only the downed are kept out) and `foldLoaded` (`:293-317`).

- **What is wrong:** R1-26 found that a far colony's `foldLoaded` discards its members anywhere in loaded land with no
  sight check. For the clamp it is worse than "termites at a hole vanish": the soldier riding on a Matabele's leg is
  still a member of its termite colony, wherever the ant has dragged it. Once no player has been within 80 blocks of
  the mound for the grace minute, `foldLoaded` (every 400 ticks) takes it: alive, a caste, not downed, so `frozenOf`
  writes it down as an adult at home and `discard`s it. The ant's clamp then finds no holder (`Clamp.tick`, the
  holder is removed with `DISCARDED`, not `KILLED`) and after 100 ticks lets go.
- **What the player sees:** following a raid home (a Matabele nest is 60 to 90 blocks from a feeding spot, the mound
  a further 10 to 50 past it), a raider crawling home at a quarter speed with a soldier on its leg suddenly walks free,
  the soldier gone with no body; the soldier is back alive in its mound's count. The same for a soldier the clamp let
  go alive inside the ants' nest (R3-41's "living termite soldier walking free among the ants"): it vanishes. No
  care is needed and no leg is lost for those ants: near and away disagree.
- **Where:** `framework/.../colony/AwayLife.java:293-317` (`foldLoaded`, `level.getAllEntities()` at 300, `discard` at
  315), `270-276` (`frozenOf`: only the downed are kept out), `413-415`; `framework/.../entity/Clamp.java:146-154`
  (holder missing, `letGo` after `MISSING_TICKS` 100); `AwayLife.java:65-67` (`NEAR` 64, `NEAR_KEEP` 80);
  `SpeciesProfile.java:134` (`numbers_when_far` on by default, so for Macrotermes too).
- **Unchecked link:** how often the mound really is 80 blocks or more from the player while a clamped raider walks
  home in sight: the distances say often, not measured.
- **Fix proposed:** `frozenOf` keeps out an insect that is clamping on (`clampingOn() != null`), as it keeps out the
  downed: its fate belongs to the ant it holds now. One line, beside R1-26's sight check.

### R6-5 (B, the mechanism proven; how often in play not known): a limping ant stops to call for helpers who can never come

**Found by two readers** (the care reader and the cross reader); the cross reader's walk is given, the care reader's
simpler fix after it.

- **What is wrong:** two places judge "is a nestmate free to help me". The hurt ant asks `helpWithin`: any nestmate
  within 6 blocks for whom `RescueGoal.mayHelp` and `answersHelpCalls` are true. The helper's side is the goal
  table: `RescueGoal` is number 2, so it can only start in an ant that runs nothing of number 1 or 2.
  `answersHelpCalls` says no only for an ant in the raid column (`MatabeleEntity.java:970-976`). So an ant walking
  home by itself (`MatabeleWayHomeGoal`, 1), a scout (`MatabeleScoutGoal`, 1), an ant on its way to a meal
  (`EatGoal`, 1) and an ant walking to a chore with empty jaws (number 2, same number) all count as "help is near",
  and none of them can ever start `RescueGoal`: for the first three the game does not even ask its `canUse`.
- **Where:** `framework/.../entity/AbstractAntEntity.java:1287-1291` (the call: every 100 ticks, 200 ticks long, then
  not again for 600), `:1295-1304` (`helpWithin`), `:1501-1505` (calling freezes the ant);
  `framework/.../entity/goal/RescueGoal.java:53-57` (`mayHelp`), `:73-86` (`canUse` does not ask
  `answersHelpCalls`); `MatabeleEntity.java:208` and `:213-215` (the numbers).
- **The proof, the commonest case:** a raid is given up or a stage's deadline lets several raiders go
  (`Raid.abandon`, `Raid.java:1515-1524`; `letGo`, `:1320-1324`). Hurt and whole ants go onto the same way-home list
  and walk the same trail. For a whole one: `mayHelp` is true (alive, not `fightsNoMore`, no fight order, jaws
  empty) and it is in no column, so `answersHelpCalls` is true. The hurt one beside it passes line 1287, calls and
  stands frozen 200 ticks. The whole one runs `MatabeleWayHomeGoal`, which holds MOVE and LOOK at number 1, and
  walks on past it. 600 ticks later the hurt one calls again if any such ant is within 6 blocks.
- **What the player would see:** after a raid that broke up, a limping ant on the trail home stops and squeaks for
  10 seconds while its nestmates walk past it, then limps on, and does it again 30 seconds later. With R6-23 it stands
  15 seconds of every 40.
- **Real biology:** real Megaponera returning from a raid do pick up a calling nestmate (Frank et al. 2017), so the
  call is right and the passing by is the fault.
- **The fix proposed (fewer rules):** make the two judges one. Either `helpWithin` asks what the table will allow
  (one method "would start a rescue now": `mayHelp` and no goal of number 1 or 2 running, the test
  `isBusyWithChore` already half makes), or better for the biology: an ant going home by itself answers a call (in
  `MatabeleWayHomeGoal.canContinueToUse`, stand aside while a caller it may help is within reach, so `RescueGoal`
  can start; the way-home list keeps it and takes it up again with the hurt one in its jaws, which already works:
  see case 3 below).

- **The care reader's fix (simpler, no change to what a walking ant does):** `answersHelpCalls` is false while the
  ant is on any raid errand (scouting, in a column, going home), and `RescueGoal.canUse` asks it too. Then a hurt ant
  out in the field calls only near the nest, where idle nestmates really come out to it (which works today).
- **Time lost today:** 10 seconds standing in every 40 (15 with R6-23), a quarter or more of a limper's way home.

**Your call (2026-11-26):** After a raid that broke up, a limping ant on the way home stops and squeaks for 10 seconds every 40 while healthy nestmates walking home beside it go past: they count as "help is near" but their walk home outranks the rescue. Should an ant walking home by itself stop and carry a calling nestmate? My pick: yes (real Megaponera coming back from a raid do pick up a caller, Frank et al. 2017). The other way is the small fix: the hurt one no longer calls for them and just limps on.

### R6-6 (B, who is called proven; how it plays out likely, not run): the nest's defence calls out the wounded first, and ants carrying a hurt nestmate or carried themselves

**Found by two readers**; the cross reader's walk is given. The care reader adds: `FightGoal` has no test of a
load at all (`FightGoal.java:137-146`; a search of it and of `fight/` for the jaws finds none), and the hurt who get
up to defend lose their care while the infection's clock runs (a lick needs them back at rest on a room cell).

- **What is wrong:** every other drafter leaves an ant with full jaws alone (`Raids.raiderFit`, `Scouts.fit`,
  `RescueGoal.mayHelp`, `ClampCareGoal.free`). `NestDefence.mayDefend` asks only: a worker, not `fightsNoMore`, not
  downed, has fight numbers, not kept by the raids. Its own comment says "the hurt stay in". They do not:
  `fightsNoMore` is 3 or more legs gone, a living clamp, or badly wounded; an ant with one or two legs gone and an
  open wound passes.
- **Where:** `framework/.../fight/NestDefence.java:211-214` (`mayDefend`), `:155-162` (those near the trouble),
  `:181-197` (`wave`, nearest the entrance first), `:199-204` (`sendOut`), `:250-268` (the order renewed every
  second); `MatabeleEntity.java:959-964` (the hurt rest in the room nearest the entrance); `RescueGoal.java:105-112`
  (`stop` keeps the carried ant unless the carry timed out).
- **Three things follow, each walked:**
  1. **A rescuer fights with its patient in its jaws.** It carries a hurt nestmate (`RescueGoal`, 2). `sendOut`
     gives it a fight order. `FightGoal.canUse` (`:136-142`) is true, pushes `RescueGoal` out; its `stop` does not
     set the nestmate down. The rescuer runs at the player and stings with the hurt ant riding in its jaws. When the
     order ends `RescueGoal.canUse` (`:77-79`) is true again and it carries on. Nothing is lost; it looks wrong.
  2. **A carried ant is counted as a defender.** A passenger with one or two legs gone passes `mayDefend`, takes
     one of the `most` places and gets its order renewed every second, while it is frozen and can do nothing. Set
     down in the hurt room it has a live fight order and walks out to fight, wound open.
  3. **The wounded go out first.** `wave` takes the nearest the entrance, and the hurt room is the room nearest the
     entrance, so the first wave is the patients and the ants licking them (the care goals are pushed out; their
     claims end by themselves).
- **Likely, not run:** a wounded defender outside calls for help (R6-5's call does not ask about a fight order),
  an idle ant in the nest carries it in, its order is still renewed, it walks out again, and so on while the player
  keeps provoking.
- **What the player would see:** break into a nest after a raid came home with hurt: ants with legs missing lead
  the charge, and now and then one stings with another ant in its jaws.
- **The fix proposed:** `mayDefend` also asks: jaws empty, not carried, not carrying, wound not open (or one shared
  "free to be called" test, see R6-7).

**Your call (2026-11-26):** Break into a Matabele nest after a raid came home with hurt ants: today the ants with a fresh wound are the first to charge (their room is the one nearest the entrance), and now and then an ant stings with a nestmate hanging in its jaws. Should an ant with a fresh wound defend its nest? My pick: no for the wounded and for anyone carrying or carried (the design's "the hurt rest until the wound closes"; real Megaponera with a leg gone do fight again, but after the wound has closed).

### R6-7 (B, proven): "is this ant fit and free" is judged in nine places by nine different rules

The root of R6-3, R6-5, R6-6 and R6-8. The fix proposed is in the section "Is the goal table sound" below. The question its reader put to the user (may a wounded major scout) is answered by the design: see R6-3.

- **Where, and what each asks:**

| Who asks | Where | Legs gone | Clamp | Badly wounded | Wound open | Needs rescue | Jaws | Fight order | Carried or carrying |
|---|---|---|---|---|---|---|---|---|---|
| `fightsNoMore` (the fight, the raid goal) | `AbstractAntEntity.java:965-967` | 3 or more | a living one | yes | no | no | no | no | no |
| `Raids.raiderFit` | `Raids.java:1039-1043` | 3 or more | any | yes | yes | yes | empty | none | by the jaws only |
| `Scouts.fit` (who may set out) | `Scouts.java:314-317` | 3 or more | any | **no** | **no** | **no** | empty | none | by the jaws only |
| `MatabeleScoutGoal.fitToGoOn` | `MatabeleScoutGoal.java:331-333` | 3 or more | any | **no** | **no** | **no** | no | none | no |
| `RescueGoal.mayHelp` (and wound care) | `RescueGoal.java:53-57` | 3 or more | any | yes | no (wound care adds "wants care") | yes | empty | none | yes |
| `ClampCareGoal.free` | `ClampCareGoal.java:66-69` | 3 or more | any | yes | no | no | empty | none | **no** |
| `NestDefence.mayDefend` | `NestDefence.java:211-214` | 3 or more | a living one | yes | **no** | **no** | **no** | no | **no** |
| `Raids.sweep` (sent home from outside) | `Raids.java:520-527` | no | no | no | no | no | empty | none | by the jaws only |
| `mayGoIntoNest`, `AwayLife.frozenOf` (into the numbers) | `AbstractAntEntity.java:560-562`, `AwayLife.java:274` | no | any (the first only) | no | no | no | empty (the first only) | no | carried (the first only) |

- **What is wrong:** each was written for its own step and each is right for that step, but together they disagree,
  and a new state (the wound, the carried ant) was added to some and not to others. What comes of it today:
  - a major with a fresh open wound may not raid ("the hurt rest until the wound closes", `Raids.java:1042`) but may
    be sent scouting, alone, for up to 4 minutes (`trip_ticks` 4800), in the 5 minutes (`infect_ticks` 6000) in
    which a lick decides whether the wound turns bad (80 in 100 unlicked against 20 in 100 licked). It leaves
    between two licks (the patient is only frozen while the carer works);
  - a scout hurt down to 40 percent of its health scouts on; the same ant in a raid is sent home;
  - R6-6 (the defence) and R6-8 (the chore list) are this same finding seen from two more sides;
  - `ClampCareGoal.free` does not ask "carried": harmless today, since a carried ant is frozen.
- **What the player would see:** a major with a leg missing leaving the hurt room to go scouting while its
  nestmates were licking its wound; later it dies of the infection the care was there to stop.
- **The fix proposed:** one method on the ant, "what state am I in" (whole; hurt and resting; cannot fight; carried;
  carrying), and one "may be called away now", asked by all nine. See the verdict at the end.

### R6-8 (B, proven): "busy with a chore" leaves out wound care and rescue, so the ants tending the wounded are the first taken for a raid

**In play (2026-11-27):** C. The ants tending the wounded are the first taken for a raid.

**Found by three readers.** The lead read the list (`MatabeleEntity.java:899-902`): `ClampCareGoal` is in it,
`WoundCareGoal` and `RescueGoal` are not, though its comment ends "care".

- **What is wrong:** `MatabeleEntity.isBusyWithChore` is a hand-kept list of goal classes: `FeedQueenGoal`,
  `FeedLarvaGoal`, `CarryEggGoal`, `CarryRemainsGoal`, `ClampCareGoal`, `BringPreyGoal`. Its comment ends "...waste
  or prey, care". `WoundCareGoal` and `RescueGoal` (an ant on its way to a caller, jaws still empty) are not in it.
  The raid sorts the fit ants by "not busy first, then nearest the entrance"; the scouts refuse a busy ant.
- **Where:** `savanna/.../entity/MatabeleEntity.java:898-902`; `savanna/.../colony/Raids.java:991-996` (the sort),
  `:1039-1043` (`raiderFit`: jaws empty, so a rescuer already carrying is safe); `savanna/.../colony/Scouts.java:274`
  and `:710`.
- **The proof:** an ant licking a wound runs `WoundCareGoal`, jaws empty, no wound of its own: `raiderFit` true,
  `isBusyWithChore` false. It stands in the hurt room, which is the room nearest the entrance
  (`MatabeleEntity.java:959-964`), so it sorts to the front of the list and is among the first `real` taken
  (`Raids.java:1019-1023`). `MatabeleRaidGoal` (1) pushes `WoundCareGoal` (2) out; `stop` (`WoundCareGoal.java:82-89`)
  lets the patient go and the lick's progress is lost (up to 300 goal ticks, half a minute). The same for a major
  mid-lick and `Scouts.mayScout`.
- **What the player would see:** when a raid musters, the ants standing over the wounded are the first to walk off.
  A raid usually takes most of the real ants anyway, so it shows only with a small raid.
- **The fix proposed:** do not add two more class names. Ask the ant instead of listing its goals: "runs a goal of
  number 2 or lower" is the same question for every goal there is and will be (the table already says which goals
  are work), or the owner slot of R6-7.

### R6-9 (B, proven; how often a soldier is shut in with raiders not known): the bite-out goal's "a foe right beside it: the fight first" never fires, so a shut-in ant with a termite soldier on it bites the wall instead of fighting

**In play (2026-11-27):** C. A shut-in Matabele with a termite soldier on it bites the wall instead of fighting.

The lead read `MatabeleBiteOutGoal.foeNear` (`:204-207`, it asks `ant.getTarget()`) and searched both mods for `setTarget(`: only the animals call it. It holds. It may be part of BUGS 361 (raiders caught in termite tunnels, open); not checked against that replay.

- **What is wrong:** `MatabeleBiteOutGoal` sits at -1, above the fight, on purpose (BUGS 102), and its header says it
  "stands aside while a foe is right by it (a termite in the pocket with it: the fight first)". That exception is
  `foeNear()`, which asks `ant.getTarget()`. Nothing in the insect code ever calls `setTarget`: `FightGoal` keeps
  its foe in its own field. So `foeNear()` is always false, and once the bite-out goal has the ant, nothing can take
  it back: not the fight, not the raid, not the scout trip.
- **Where:** `savanna/.../entity/goal/MatabeleBiteOutGoal.java:37-39` (the promise), `:81` and `:86` (the two uses),
  `:204-207` (`foeNear`); `framework/.../entity/goal/FightGoal.java:100`, `:140`, `:205` (the foe is a field of the
  goal); a search of both mods for `setTarget(` finds only animals (`GuardPlaceGoal`, the crocodile).
- **The proof:** `canUse` (`:73-82`): two looks 10 ticks apart find it `trapped` (out of its nest, a roof within 3,
  `Fights.isShutIn`), then `!foeNear()` is `!false`. It starts and pushes out `FightGoal` (0 is a higher number than
  -1), whose `stop` forgets its foe (`FightGoal.java:157-167`). `canContinueToUse` (`:85-93`) ends only on downed,
  `foeNear()` (never), the let-go time, or no longer trapped. `FightGoal.canUse` goes on being asked but cannot push
  out a goal of a lower number. So for as long as the ant is shut in it only bites walls.
- **What the player would see:** mostly nothing directly, because it happens in a sealed pocket: the termites seal a
  broken gallery behind the minors that went in (the raid seals), a soldier in the pocket bites, and the ants in
  there turn from the termites to the wall a second after the seal goes in. From outside: fewer termites stung than
  went in for, and raiders coming out with legs gone or not at all. Likely one part of BUGS 361's raiders lost in the
  termites' tunnels (not checked against that replay).
- **Not checked:** how often a soldier is in the pocket in a real raid (not run).
- **The same dead check elsewhere:** `AbstractAntEntity.mayGoIntoNest` (`:560-562`) asks `getTarget() == null` for
  "not after an enemy"; round 3 already wrote that down under R3-38. This is the Matabele goal's side of it.
- **The fix proposed:** one small method on the body, "the insect it is fighting or was just bitten by, if within x
  blocks" (`getLastHurtByMob` and the fight goal's foe), asked by `foeNear()`; and delete the `getTarget()` check in
  `mayGoIntoNest` or point it at the same method. No new rule: it makes the written one true.

### R6-10 (B, proven): out of a raid column any nestmate carries any hurt ant; the size rule lives only in the raid

- **What is wrong:** the design says "minors carry minors, majors anyone" (`docs/DESIGN.md`, "Step 5, as built"), and
  the raid keeps it (`Raid.java:138`, `RESCUER_SIZE` 0.8, used at `1961-1962`). `RescueGoal` and `pickUpHurt` have no
  size test, so at the doorstep a 7.5 mm minor (`InsectSizes.java:48`) picks up and carries an 18 mm major. Two places
  should judge one thing; one forgot.
- **What the player sees:** a small minor walking into the nest with a major more than twice its length hanging
  under its head.
- **Where:** `framework/.../entity/AbstractAntEntity.java:1221-1232` (`pickUpHurt`); `RescueGoal.java:60-70`, `149`;
  against `savanna/.../colony/Raid.java:1961-1962`.
- **Fix proposed:** the size rule moves into `pickUpHurt` (one place, the raid's filter then asks it); `RescueGoal`'s
  `callers` skips a caller it could not carry unless it only needs a clamp pulled. As far as I know the papers do not
  say how rescuer size matches the hurt one's; the 0.8 is the user-era pick and is kept.

### R6-11 (B, proven): in a raid the hurt are fetched only after the attack, so a clamp squeezes all through it (a correction to R3-41)

**In play (2026-11-27):** C. In a raid a clamped ant is fetched only after the attack, so the clamp squeezes all through it.

Round 3 is not merged here, so this is written as a finding of its own; a fix session should read it with R3-41.

- **What is wrong:** R3-41 says "in a raid the column pulls clamps off in 10 s, before the first squeeze, so there the
  leg is kept". It does not: rescuers are handed out only in `regroup` (`Raid.java:1948-1970`, the only callers of
  `hurtWaiting` and `taking.put`). A raider clamped early in the attack stands calling (`tendHurt`, `2101`, re-called
  every update, so immobile) until the attack ends, which is up to `fight_ticks` 2400 (2 minutes) or 300 ticks after
  the last termite. A living major soldier squeezes every 400 ticks (`macrotermes.json`), each squeeze rolling its
  `leg_cut` 0.30 (`Fights.squeeze`): up to six rolls, the leg kept about 12 times in 100, before the 200-tick pull even
  starts. Meanwhile the caller stands still among the soldiers, which may bite it again (it cannot dodge or answer).
- **Real side:** waiting till the raid gathers to leave fits what I know of Frank et al. 2017 (the hurt are helped
  when the raiders regroup at the hunting site), but I could not check the paper's wording; the leg loss does not
  fit their "9 in 10 kept".
- **Where:** `savanna/.../colony/Raid.java:1404-1405`, `1941-1970`, `2088-2103`; `framework/.../entity/Clamp.java:169-175`.
- **Fix proposed:** R3-41's own (the squeeze does damage only; `clean` alone decides the leg). No new rule here; R3-41's
  numbers should be read with this in mind.

### R6-12 (B, proven): one continuous size spread, but two bodies decide everything, and 77 in 100 workers get the full major's numbers

- **What is wrong:** each worker rolls a real length (5 to 18 mm), and that length only sets how big it is drawn and,
  inside its body, how many termites it carries. Everything else follows which of the two models is nearer, with the
  split at 10.6 mm (the root of 15 x 7.5): health and damage (`fight.bodies.major` 10 and 3, `minor` 5 and 1.5),
  breaking the termites' mud open (`breaksIn`, `openWayTo`, `breakIn`), scouting and leading a raid (majors only), the
  carry range (4 to 10 against 1 to 3). Speed is the same for all (the user's pick, kept). With the file's groups,
  every in-between worker (11.5 to 13.5 mm, a quarter of the colony) and the top of the minors' group are full majors:
  77 in 100 workers are majors in play, though the file's own note says "about half majors, a quarter in between, a
  quarter minors".
- **What the player would see:** a 10.5 mm ant has half the health and half the bite of a 10.7 mm one beside it, and
  only the second bites mud open. Raids are three quarters "majors" while the drawn sizes look like a smooth spread.
- **Where:** `MatabeleEntity.java:229-234` (`pickBody`), `:273-275` (`hasMajorBody`, the type alone), `:472-476`
  (`fightBody`), `:495-498`, `:628-631`, `:641-644` (break-in for majors only); `SavannaNumbers.java:83-85`
  (`majorModelFor`, the split), `:759-767` (`loadFor`, the one thing that follows length); `AbstractAntEntity.java:849-864`
  (health and damage from the body's name only); `megaponera_analis.json:5-14` and `:82-85`; the 77 in 100 is pinned by
  `MatabeleColonyTests.java:152-153`.
- **The proof:** triangle (5, 10, 11.5): the share above 10.6 mm is 0.9^2 / (6.5 x 1.5) = 0.083 of the minors' quarter,
  so 0.02 + 0.25 + 0.5 = 0.77 (the test asserts 0.77).
- **Real biology:** Megaponera workers are one caste of continuous sizes (the brief's Villet 1990; the file cites
  Longhurst and Howse 1979); what a worker does at a raid goes with its size (big ones break in and carry bundles,
  small ones go in and sting). A step in strength at one length is the game's, not the ant's.
- **Fix proposed (fewer rules):** keep the two models for drawing only, and let length set the rest the way `loadFor`
  already does: health and damage on the same `t` along the spread between the minor's and the major's numbers;
  "breaks in" and "may scout" from a length in the file (for example 12.5 mm, the middle of the in-between group)
  rather than from the model.

**Your call (2026-11-26):** Each ant has its own real length (5 to 18 mm), but only how big it is drawn and how much it carries follow it. Health, bite, breaking the termites' mud open, scouting and leading a raid all go by which of the two models is nearer, split at 10.6 mm: a 10.5 mm ant has half the health of a 10.7 mm one, and 77 in 100 workers count as full majors. Should an ant's strength follow its own length? My pick: yes: health and damage by length (as the carry load already is), breaking in and scouting from about 12.5 mm up (about 60 in 100).

### R6-13 (B, proven by the code and Minecraft's hurt rules, not run): the sting can kill an unarmoured player standing at the nest in about 2.5 seconds

One link is from memory, not read: Minecraft's half-second hurt cooldown and how difficulty scales mob damage. `docs/DESIGN.md` chose 2 hearts a sting; it never chose how many stings a second.

- **What is wrong:** each sting is 4 points (2 hearts) of mob damage, each ant may sting once every 30 ticks, and up
  to 20 defenders are out. The only limit on the total is Minecraft's half-second hurt cooldown (a second hit of equal
  damage inside 10 ticks is refused), so three ants at a player's feet already land two stings a second: 8 points a
  second on Normal, 12 on Hard (R3-45: difficulty scales it). With R3-37 (a walking player is almost never stung) the
  defence has two settings, harmless and deadly, and nothing between.
- **What the player would see:** dig into a Matabele nest with a shovel without armour and you die in about 2.5 seconds
  once the first wave is at your feet; in full iron (about half the damage taken off) about 5 seconds; in diamond about
  10. Walk and nothing happens at all.
- **Where:** `megaponera_analis.json:244` (`sting.damage 4`, `every_ticks 30`), `:238` (`most 20`), `:235-236`
  (`first_wave 5`, `wave 5`); `framework/.../fight/Fights.java:136-145` (one sting = `player.hurt(mobAttack, 4)`);
  `AbstractAntEntity.java:1635` (the per-ant wait); Minecraft's `LivingEntity.hurt` (the 10-tick cooldown, from memory
  of the game's code) and `Player.hurt` (difficulty).
- **Real biology:** the Megaponera sting is one of the most painful of any ant but is not dangerous to a person; the
  pain lasts hours. `docs/DESIGN.md` (the player, line 72 of the Matabele section) chose 2 hearts a sting with
  "diamond with Protection makes a swarm manageable", so a deadly swarm may be wanted; the rate was not written down.
- **Fix proposed:** the pain is the real part, the damage the game part. Either a sting of 1 (half a heart) with the
  pain doing the work (slowness and the red edge, longer with each sting), or keep 4 and let a player be stung by the
  colony at most once a second (one number in `Pain`, which already keeps each player's stings). Do it with R3-37's fix,
  which will make stings land far more often.

**Your call (2026-11-26):** How dangerous should a Matabele swarm be? Today each sting is 2 hearts and up to 20 defenders sting, so a player standing still at a provoked nest with no armour dies in about 2.5 seconds (iron about 5, diamond about 10), while a player who keeps walking is almost never stung (R3-37). The real sting is among the most painful of any ant and not dangerous. My pick: painful, rarely deadly: keep 2 hearts a sting but let the whole colony land at most one sting a second on a player, with the pain (slowness, the red edge) growing with each sting; build it together with R3-37's fix.

### R6-14 (B, proven): a misspelt key or section in the species file quietly uses a number nobody chose, or switches a whole system off

- **What is wrong:** R1-2 and R1-42 made a broken file stop loudly. A misspelt optional key is not broken: every
  `optionalFieldOf` takes its default and unknown keys are ignored. A misspelt or missing section is just "not there".
  For the Matabele that means:
  - `defence` misspelt: the `nest_defence` part is still listed and made, but `NestDefence.numbers()` is null and every
    method returns at once: the nest is never defended, with no message (`NestDefence.java:95-97, 115, 139-141, 219-221, 433-435`).
  - `worker_sizes` misspelt: `pickBody` makes every new worker a major (`MatabeleEntity.java:232`, `sizes == null`)
    and no ant gets a length.
  - `fight.prey` misspelt (or a wrong species id in it): `Fights.listed` is false, so the Matabele may not attack
    termites at all (`Fights.java:89-103, 198-200`): raids walk out and catch nothing.
  - Any single key (`keep_going`, `sting.damage`, `queen_reach` ...) falls back to the number in code; most equal the
    file today, but `queen_reach` does not (code 0: the queen never stings).
- **What the player would see:** after a tuning edit with one typo, a system silently stops or changes; the log says
  nothing.
- **Where:** `DefenceNumbers.java:33-46`, `Pain.java:36-43`, `FightNumbers.java:60-70`, `SavannaNumbers.java:59-64`
  (`WorkerSizes`, required keys: a typo there is loud, but a typo in the section's name is quiet), `SpeciesSection.java:76-82`.
- **Fix proposed (one check, not many):** at load, list every key in a species file that no section's codec read and
  log it as an error (or refuse the file, as R1-42 does for a broken one); and a part that cannot work without its
  section (`nest_defence` without `defence`) says so at load, as `ColonyParts.checkSpecies` already does for an unknown
  part name. This is framework-wide (the termites' file has the same shape); I found no earlier finding on it.

### R6-15 (B, proven): two rules squeak at a player beside a raid column

**In play (2026-11-27):** C. Beside a raid column you are squeaked at by two rules at once.

- **What is wrong:** raid rework R7 made one squeak rule for a column (`Raid.squeak`: the raider nearest each player
  squeaks, louder and more often the nearer). The older rule in `NestDefence.touchesAColumn` is still there: every
  member in a column or on a fight order within 3 blocks of a player squeaks with a 1-in-4 chance each second. They
  also disagree on who hears: `Raid.squeak` squeaks at creative players, `NestDefence` does not.
- **What the player would see:** walking beside a column, the measured warning squeak of R7 plus a random chatter
  from every ant within 3 blocks (with 10 ants near, about 2 or 3 more squeaks a second).
- **Where:** `framework/.../fight/NestDefence.java:400-402`; `savanna/.../colony/Raid.java:2198-2230`.
- **Fix proposed:** delete lines 400-402 (the touch that provokes, 392-399, stays); the column's squeak is the raid's.

### R6-16 (B, proven for the code; how often in play likely): a Matabele with no colony lives for ever, and one whose colony is gone joins any colony of its kind near it

**In play (2026-11-27):** C. A Matabele with no colony lives for ever; one whose colony is gone joins any colony near it.

- **What is wrong:** an ant of no colony is never hungry (`WorkerHunger.java:63-70`), never grows old (`AbstractAntEntity.java:652-669`
  needs a colony), never despawns (`:2973-2976`), never defends or is provoked (`Fights.java:90-93` needs a colony),
  and keeps its goals ticking. On each load it searches again and joins the nearest Matabele colony within 32 blocks
  of its centre (`MatabeleEntity.java:437-449`), adding one to that colony's count with no check of `max_workers`.
- **Who gets there:** a spawn egg or `/summon` more than 32 blocks from any Matabele colony; the 1 to 60 ants of
  `/antcolonies skirmish` (`MatabeleCommands.java:237-246`: made with no colony, they join a colony within 32 blocks on
  their first tick, so a 5-minute test fight leaves up to 60 permanent members); an ant whose colony record is gone (a
  world opened without it); an ant that went through a portal (its colony is in another world's list, so its death
  never takes it off the count).
- **What the player would see:** lone ants that wander for ever and cannot be hurt into defending themselves; a nearby
  colony that grows past its cap after a skirmish. Creative and op paths mostly, so B, not A.
- **Real biology:** a Megaponera worker cut off from its colony does not live long, and a foreign worker is not taken in.
- **Fix proposed:** let a worker of no colony starve on its species' starve time (the hunger clock runs without a
  colony; framework-wide, so the termites too), and the skirmish ants keep out of colonies (`keepOutOfColonies`, as
  the tests do). Joining a colony on load only if it is the ant's own.

### R6-17 (B, a reading of the numbers, not measured): the colony's hunger counts only the adults, while a new worker costs 11 termites

- **The arithmetic** (`megaponera_analis.json`): a termite is one layer (`raids.carry.termites_per_layer` 1) and feeds
  20 workers for a day (`worker.diet.workers_per_piece` 20, `meal_interval_ticks` 24000): 100 workers eat 5 termites a
  day. The queen takes `queen.meal` 6 termites for each egg; a larva `brood.larva_meal` 5. So one new worker costs 11
  termites, while a worker eats 3 termites in its whole 60-day life. A colony of 100 loses about 1.7 workers a day to
  old age (`lifespan_ticks` 1,440,000), so holding steady costs about 18 termites a day for the young and 5 for the
  adults: the young eat nearly four fifths of the food.
- **What does not fit:** "hungry" (`Raids.isHungry`/`fedShare`, `savanna/colony/Raids.java:700-720`) is the pile's
  layers times 20 against the workers: one day of adult food only. With 5 termites on the pile a colony of 100 counts as
  fed, sends no scouts and wants 1 raid, while its queen alone wants 6 for one egg. The pile is emptied by the queen and
  the larvae and the colony flickers between fed and hungry; raids follow the flicker, not the colony's real need.
- **How it came to be:** step 1e set the queen's 6 and the larva's 5 in layers when a layer was 10 termites feeding 10
  ants (`docs/DESIGN.md` line 195: "to confirm against how many termites that is"); honest sizes (2026-11-11) made a layer
  1 termite feeding 20, but `queen.meal` and `brood.larva_meal` were left as they were. In worker-days a new worker went
  from 1,100 to 220; nobody checked it against the raids' catch (`raids.catch.per_raider` 0.1, `most_catch` 10, at most
  4 raids a day: about 3 to 30 termites a day as numbers).
- **Fix proposed:** measure first (a 10-day soak line per colony: termites in, eaten by workers, queen, larvae; workers
  born and died). Then one rule: `fedShare` counts what the colony eats in a day, adults plus the queen's next egg and
  the hungry larvae, so the raids answer the whole need. No new number.

**Your call (2026-11-26):** A new worker costs 11 termites (6 for the queen's egg, 5 for the larva), while a grown worker eats 3 in its whole life; yet "hungry", which sends out the scouts and raids, counts only a day of the adults' food. So the queen and the young empty the pile and the colony flickers between fed and hungry. My pick: keep the 11 (no paper figure for what a Megaponera larva eats is known to me), measure a 10-day soak first, then let "hungry" count the whole need: the adults, the queen's next egg and the hungry larvae. No new number.

### R6-18 (B, real biology): egg and brood times are minutes on a scale where a game day is a real day

Honest about what is known: neither reader had a checked Megaponera development time; "weeks" is from ponerine ants in general.

- **Where:** `megaponera_analis.json` `queen.incubation_ticks` 3600 (3 minutes from a full meal to the egg),
  `brood.hatch_ticks` 1200 (the egg hatches in 1 minute); the larva grows as fast as nurses bring 5 termites (minutes).
  Against them, on the file's own TIME scale (`raids._scale_note`: a game day a real day), the worker lives 60 days and
  a wound heals in a day.
- **What is wrong:** real ponerine brood takes weeks from egg to adult (I know this for ponerines in general, about one
  to two months, not as a checked Megaponera figure). Here food is the only limit: a fed colony turns termites into
  workers within minutes, and `max_young` 10 is the only brake.
- **What the player would see:** after a good raid, eggs, larvae and new workers within a few minutes of play.

**Your call (2026-11-26):** An egg comes 3 minutes after the queen's full meal, hatches in 1 minute, and the larva grows as fast as it is fed, so a fed colony turns termites into workers within minutes, while a worker lives 60 game days and a wound takes a day. Real ponerine brood takes weeks. My pick: look up the real Megaponera times and put them on the same day-for-a-day scale as the rest (then the young limit of 10 and the queen's rate need a second look with R6-17), or, if fast brood is wanted for play, write that down in the species file.

### R6-19 (B, proven): the prey pile's count and its heaps drift apart, and nothing squares them

**In play (2026-11-27):** C. The prey pile can show fewer termites than the colony counts (after you break a heap, say).

- **What is wrong:** the count of termites (`edible`) is "the truth" and the heaps only follow it. When a heap goes
  without the count knowing (a player breaks it, a piston, water washing it out: the block has `noCollission`,
  `PushReaction.DESTROY`, `canSurvive` needs a floor, and no loot table), or when a bundle is counted with no room for a
  layer (`bringIn`, `PreyPile.java:121-130`: the layer is skipped, the count still rises), the count stays higher than
  what lies there. Nothing ever compares them.
- **What the player would see:** break the prey pile in a Matabele nest: the colony still counts itself fed, so no
  scouts go out and no raid for up to a day (`Raids.isHungry` reads the count, `Raids.java:709-711`); the real ants near
  the player find nothing to eat (`findFood` needs a heap, `PreyPile.java:422-428`) and go hungry; the numbers inside go
  on eating termites that are not there (`takeCounted`). After a day the ghost termites rot, are "carried out" from an
  empty room and the count is 0 again.
- **Where:** `savanna/colony/PreyPile.java:117-133` (`bringIn`), `:189-194` (`rot`), `:289-305` (`pickUpLoad`: a rotten
  layer with no heap goes to the ledger), `:422-439`; `framework/colony/NestLedger.java:217-229` (a negative ledger with
  no heap just resets to 0); `savanna/registry/SavannaBlocks.java:63-66`.
- **The fix proposed:** one place, no new number: `lookAfter` already walks every heap once a second; let it count the
  layers it finds and, when they are fewer than `edible + rotten` less what the ledger still owes, take the newest
  pieces out of the count (`forget`). A test: stock 6, break the heap, the colony is hungry within a second.

### R6-20 (B, likely; three links not read, named): one block a player puts anywhere in the chimney hides every room of the nest

**In play (2026-11-27):** B. Likely, not proven: one block you put in a Matabele nest's chimney (a torch) makes the colony lose all its rooms.

**Status (2026-11-27, `claude/review-fix-b8`): fixed for any block the ants walk through: a way in (the shaft, the stairs, a tunnel to a room, a Matabele nest's old chimney) is open while every cell is dug out or holds something with no collision and no fluid (`Nest.isWayOpen`), so a torch down the chimney leaves every room as it was; what is dug or filled (`isDugOut`) is unchanged. A solid block still shuts the way, as a stone would shut real ants in (digging round it is not built). Test `aTorchDownTheChimneyLeavesTheMatabeleRoomsOpen`.**

The lead read `Nest.getRoomCells` (`Nest.java:798-806`) and `isDugOut` (`:775-781`): every way-in cell must be air, a heap or a ramp, or the room has no cells. The rule holds; that a torch in a dead mound's chimney is on the way in of every room, and what the ants then do, were not walked, so it stays "likely" and grade B. If it holds in play it is a grade A (a player can meet it with one torch).

- **What is wrong:** a room counts only while every cell of its way in is open (`Nest.getRoomCells`,
  `framework/colony/Nest.java:798-806`, `isDugOut` 775-781: air, a heap or a ramp). A Matabele nest's way in is the
  whole old chimney through the hump (`MatabeleNest.fromMound`, `MatabeleNest.java:185-218`), shared by all its rooms.
  The workers dig out soil there (`readyDigTarget`, `Nest.java:341-355`, `firstDiggable` takes soil only), but a torch,
  a cobblestone, planks or a slab stay for good.
- **What the player would see:** put a torch down a dead mound's chimney to look in: the colony loses all its rooms. The
  queen is "not home", so nobody feeds her and she lays nothing (`QueenCare.isQueenHome` 100-105, `needsFeeding`); the
  prey pile can no longer be found (`PreyPile.rooms`), so the real ants starve beside a full pile; eggs have no spot;
  the ants that keep to the rooms have none to go to.
- **Unchecked link:** whether `getHomeChamber` (`Nest.java:387`, behind `getFinishedRoomCenter` and so `isQueenHome`)
  judges "finished" by the same open-way rule (likely, not read line by line); what the ants' idle and "go inside" goals
  do with no room cells (round 3's ground); whether `isShaftOpen` with depth 0 also looks at the entrance cell.
- **The fix proposed:** either the workers clear any non-soil block a player put in their way in (as they bite a plant
  out of the entrance), or a room is reached while a walk to it exists, not only while every planned cell is air. The
  first is one rule in `readyDigTarget`; the second is a bigger change of one judge.

### R6-21 (B, the path proven; how often likely): R1-28's Matabele side: which carriers lose their termites in a fold, and a meal that folds in as a whole new termite

**The lead's grade: B, not the reader's A, and mostly not new.** Round 1 already lists this as **R1-28** (B, not
started: "folding an insect into the numbers loses a species load it carries", with the same fix). It is written
here only for what round 1 did not have: exactly which Matabele carriers are not covered, and that a worker's meal
folds in as a whole new termite. The lead read `AwayLife.loadOf` (`:357-371`: egg, body, nurse food, seed, else
nothing). Fix it as R1-28; a fix session may well grade R1-28 as A for the Matabele (a raid's whole catch is about a
day's food).

- **What is wrong:** a raider walking home or down to the pile with a bundle of stung termites (`MatabeleLoads.TERMITES`)
  that is folded into the colony's numbers loses the bundle. The fold only knows the framework's own loads.
- **What the player would see:** a raid comes home laden; the player walks off (or the land unloads, or a stuck carrier
  is tidied away) while the carriers are still on their way down; the pile never gets those termites. A raid's whole
  catch is often a day's food for the colony (5 termites for 100 workers), so the colony goes hungry and raids again
  for nothing.
- **Where:** `framework/colony/AwayLife.java:357-371` (`loadOf`: egg, body, nurse food, seed, else `NONE`),
  `:274-276` (`frozenOf` writes that load), `:293-317` (`foldLoaded` folds and discards every member of a far colony),
  `:343-354` (`foldInStray`), `:427-450` (`foldIn`: `NONE` adds nothing); `savanna/entity/MatabeleLoads.java:29-33`
  (the TERMITES load is its own kind, not an ITEM load); `framework/entity/AbstractAntEntity.java:409-417`
  (`getCarriedSeed` is empty for it, so `isCarryingSeed`/`isCarryingBread` are false).
- **The proof:** `loadOf` asks `isCarryingEgg`, `isCarryingCorpse`, `isCarryingBread`, `isCarryingSeed`; a TERMITES load
  answers no to all (`getCarriedSeed` reads only ITEM and CROP loads), so `Load.NONE`; `foldIn` case `NONE` does nothing;
  `foldLoaded` then `entity.discard()`. The raid itself is guarded: `Raid.toNumbers` (`savanna/colony/Raid.java:953-969`)
  reads the jaws first and moves them to `hiddenCatch`. Not guarded: every carrier no longer in a column, that is from
  the raid's end (`Raid.end`, `:1501`) until `BringPreyGoal` lays the bundle (`BringPreyGoal.java:111-113`), the hurt
  following the trail home alone (`limp_ticks` 6000), and carriers sent home "with no way to the pile"
  (`BringPreyGoal.java:123-127`). Folded by: a far colony's look (`foldLoaded`, once on going away, then every
  `FAR_LOOK_TICKS`), the land unloading past the grace (`freeze`, then `tickAway` 401-406 or `cameBack` 490), and a
  stray tidied by `Strays`.
- **Unchecked link:** how long the window is in play (the walk from the entrance down the chimney to the queen's room;
  longer for limpers and stragglers). Not measured.
- **Also on this path:** a TERMITE_REMAINS load (`MatabeleLoads.java:24-26`) folds as nothing too, so its layer never
  reaches the heap (harmless: a heap layer). A real worker folded in the 3 s it holds a "meal" look item
  (`EatGoal.java:123-125`, `WorkerFood.look`, `PreyPile.piece()`) folds as NURSE_FOOD and is counted in as a whole new
  termite (`foldIn` 439-445, `PreyPile.countedIn`): a termite from nothing, rarely. Round 3's R3-36 noted the meal held
  as an item; this is its Matabele side.
- **The fix proposed:** no new rule, one socket in the existing load row: `LoadKind` already has a `Drop` handler for a
  death; give it a "folded into the numbers" handler beside it, and let `loadOf`/`foldIn` ask the load's own kind first.
  TERMITES then counts its termites onto the pile (`PreyPile.bringInCounted(now, count)`, the path raids as numbers use),
  TERMITE_REMAINS calls `loadDropped`. A test: a far colony folds a carrier holding 5 termites; the pile's count rises by 5.

### R6-22 (B, test gaps): what the player sees of the Matabele's food, care and defence has few tests, and two tests pass if the hurt ant dies

What the player sees of the Matabele that no test guards, or that a test would still pass with the feature broken
(the three slice readers' lists, merged):

**Nests and food.**

- No test raises young from the pile: nurses feeding the queen termites from the heap, an egg, a larva fed termites,
  a new worker (`MatabeleFoodTests.aFedMatabeleQueenLaysHerEggWhileAPlayerIsNear` feeds her by hand, line 262-264). If
  `FeedQueenGoal` or `FeedLarvaGoal` stopped working for the Matabele every test would still pass.
- No test of the pile's count against its heaps (R6-19), of a carrier's bundle through a fold (R6-21), of a colony that
  starved down to its queen (R6-2; `aMatabeleColonyWithAnEmptyPreyPileStarves` stops once the workers are gone).
- No test that the same colony eats, lays and grows alike near and away (the away tests are all raids).

**Rescue and care.**

- No game test of the rescue out of a column (a hurt ant at the doorstep calling, a nestmate coming out, carrying it
  to the hurt room); `RescueGoal`'s field branch is guarded by nothing. grep of `isCallingForHelp`, `RescueGoal` in the
  game tests: none.
- `MatabeleRaidTests.java:491-494` passes when the hurt raider died (`!hurt[0].isAlive() ||`), so a rescue that kills
  it would pass; the wound-care test (`MatabeleFoodTests.java:79-106`) has the same escape and sets the wound's times
  by hand.
- No test that a hurt ant is not sent out (R6-3), not drafted by the defence (R6-6), or keeps its clamp while its
  mound is far (R6-4).

**Defence and the entities.**

- `MatabeleDefenceTests` is one scene with a player held still. Nothing checks that a wave stings (only
  `defendersOut()`), that the queen stings in her room, the sting's damage after armour, a sneaking player crossing the
  entrance unprovoking, a player touching a column, or two players at once. A test that the defence lands stings on a
  moving player belongs with R3-37's fix.
- Nothing can catch R6-1: every game test runs on a server. A small check that makes each signed-up insect type on a
  level with the species profiles emptied (or a client-side `create`) would.
- `MatabeleBodyTests` (naps) and `MatabeleTests` (climbing) touch nothing of the size code; the length's save and the
  body mix are guarded in `MatabeleColonyTests.java:86-103, 136-154` (good).

---

## Grade C

### R6-23 (C, proven): after each help call a limping ant stands 5 seconds more, because its "am I stuck" watch counts the call as being stuck

- **Where:** `savanna/.../entity/goal/Headway.java:27-39`; `MatabeleWayHomeGoal.java:102-107`;
  `AbstractAntEntity.java:1287-1291`, `:1501-1505`.
- **The proof:** an ant limping home is walking (`Headway` holds its place and tick). It calls for help: frozen for
  200 ticks, its goal not ticked, its route kept (nobody stops the legs). On the first tick after the call
  `headway.lost` finds: still walking, not held by the crowd (`AntNavigator.heldForCrowd`, `:516-519`, looks back 10
  ticks at most), moved less than half a block, 200 ticks since the last headway, more than `NO_PROGRESS` 160. It
  says "lost": the walk is stopped, the ant waits `RETRY` 100 ticks, and one "no way" is counted toward leaving its
  trail (`fails`, set back by the next walk that is found). The same after a nestmate pulled a clamp off it on the
  field (it was held still for `field_pull_ticks`).
- **What the player would see:** the hurt ant stands 15 seconds, not 10, each time it calls.
- **The fix proposed:** `Headway` counts ticks the goal really ticked (add one per call of `lost`) instead of
  reading `tickCount`. No new rule.

### R6-24 (C, proven; rare): a rescuer that is hurt itself keeps its patient, and can be picked up with it: three ants in a stack

- **Where:** `RescueGoal.java:89-92` (while carrying it asks only "still carrying" and the time);
  `AbstractAntEntity.java:1221-1232` (`pickUpHurt` asks whether the rescuer is a vehicle, not whether the hurt one
  is), `:1123-1130` (`needsRescue` is false for a passenger, not for a vehicle).
- **The proof:** a carrier at the feeding spot loses a leg to a soldier. `needsRescue` is true for it; the raid
  drops it and has it call (`Raid.java:1437-1446`), or off a raid it calls by itself (line 1287). It stands frozen
  with its patient in its jaws. A nestmate sent for it reaches `hurt.isCarriable() && ant.pickUpHurt(hurt)`
  (`Raid.java:2060`, `RescueGoal.java:149`): `hurt.startRiding(this, true)` succeeds, the middle ant still holding
  HURT and its own rider. At home the outer one sets the middle one down, whose `RescueGoal.canUse` (carrying) is
  true, and it carries its own patient to the hurt room. It ends well; it looks like a tower.
- **The fix proposed:** an ant that comes to need rescue sets down what it carries (one line where the call
  starts), and `pickUpHurt` refuses a hurt ant that is a vehicle.

### R6-25 (C, each read in the code): small things in the goals' turns

- **The list is not in priority order** (`MatabeleEntity.java:202-216`): the three raid goals (1) are added after
  the seven chores (2), so in the pass in which an ant is drafted a chore can start (claiming the queen's feeding,
  an egg, a load, a patient, and planning a route in `canUse`) and be stopped by the raid goal a few lines later in
  the same pass. Harmless (every claim is given back in `stop` or runs out), one wasted route search. Round 3's
  R3-30 is the general fault; here the cure is only to add the goals in the order of their numbers.
- **`RescueGoal.start` sets `walking` to 0** (`:98-103`): each time a carry is pushed aside (a fight, the bite-out)
  and taken up again, the 2400-goal-tick limit that ends in "set it down where it is" starts over. A carrier that
  keeps being interrupted has no limit. Not seen as a fault; noted for whoever touches BUGS 175's rule.
- **The sweep lists the hurt waiting at the spot as "outside with nothing to do"** (`Raids.java:518-532`): the raid
  dropped it from its members and keeps it calling (`Raid.java:1441`, `:2101`), so it is no scout, no raider, not
  going home, jaws empty, and within 5 seconds the sweep puts it on the way-home list and writes a diary line that
  reads like a stuck ant. It stays frozen by its call, so nothing moves; when the raid lets it go it is on the list
  already. A wrong diary line only.
- **`EatGoal` can call a helper off its walk to a caller** (1 against 2, jaws empty, 1 in 10 a pass when hungry):
  the caller waits for the next one. Fine as a rule (R3-35 is the missing claim); written so nobody is surprised.

### R6-26 (C, to measure): every idle ant in the nest searches for callers and patients when nobody is hurt

The only place in this round where work grows with the square of the ants.

- **Where:** `RescueGoal.java:80-85` (once a second, a mob search 6 blocks round each free ant), `WoundCareGoal.java:47-61`
  and `ClampCareGoal.java:50-61` (every 2 s, 1 in 2 and 1 in 3, 8 blocks).
- **What is wrong:** in a nest every ant is within reach of every other, so the work grows with the square of the real
  ants (about 60 x 60 cheap predicate checks a second for rescue alone). Each check is cheap (the colony id and a time
  first), so probably well under 0.1 ms a tick.
- **Fix proposed:** only if it measures: the colony keeps a count of members calling or wanting care (set by
  `callForHelp`, `Wounds.open`, `Clamp.clampOn`), and the three `canUse`s return at once while it is 0.

### R6-27 (C, proven): the sting's pain list is kept across worlds

- **Where:** `framework/.../fight/Pain.java:24` (a static map by player id, never cleared on a world change; an entry
  is pruned only when that player is stung again).
- **What is wrong:** stings in one world count toward a longer pain in the next world opened in the same sitting if
  its clock happens to sit within 600 ticks after the old one (the "from the future" check at `:50` catches the rest).
  Harmless and rare; a leftover between worlds (kind 3c).
- **Fix proposed:** clear the map on server stop, or keep it on the level.

### R6-28 (C, to measure): a homeless queen floods every near dead mound every 10 s, the taken ones too; the dead mound book only grows

- `MatabeleNest.nearestMound` (`MatabeleNest.java:108-123`) walks every dead mound in the world, then for each one
  within 32 blocks floods up to 3,000 cells (`fromMound`, 157-179) before asking whether a colony lives there (118). A
  queen with taken mounds round her repeats it every `LOOK_AGAIN` 200 ticks. Ask "taken?" by the mound's middle first
  (the entrance has the same x and z). Only spawn-egg queens do this.
- `DeadMounds` keeps every dead mound for good (`DeadMounds.java:37-40`, `mounds` never pruned), each with its columns,
  shaft and works; it grows with exploration. Cheap per mound once settled (`isChanging`), but the save grows. Size to
  measure.

### R6-29 (C, each read in the code): small things in the pile and the nest

- `PreyPile.countedIn` (`:522-525`) stamps the piece with `lastTime`, which is not saved: folded in before the pile's
  first tick after a load it is laid at time 0 and rots at once.
- `putBack`/`putDownWhereItStands` (`PreyPile.java:464-504`, used by `FeedQueenGoal` 214, 228, 238 and `FeedLarvaGoal`)
  can lay a heap outside the rooms or on a cell an egg lies on (only `addPieceAt` asks about brood). Outside the rooms
  `piles()` never sees it: a twitching heap there for good, its termite counted and eaten off another heap.
- A colony that ends leaves its heaps as they were: `lookAfter` stops, so a fresh heap twitches for ever.
- `heapSpot` (`:317-345`) wants ground within 3 of the saved entrance; with the hump worn 2 to 5 below that entrance
  (BUGS 358) the ring round it may have no spot, every load is "scattered and gone" and the midden never shows. A new
  side of BUGS 358.
- Real ants cannot eat the meals left in a started termite once the pile is empty (`findFood` returns null), the
  numbers can (`WorkerFood.eatForNumbers` 123-125): near and away differ by up to 19 meals.

### R6-30 (C, proven): the model lengths are written in two places

- `InsectSizes.java:47-48` (15 and 7.5 mm, "in code, not in the species file", `:21-22`) and
  `megaponera_analis.json:7-8` (`major_model_mm`, `minor_model_mm`). The size factor drawn comes from the first, the
  ant's own size (`ownSize`, `MatabeleEntity.java:298`) and the model split from the second. Changing the file alone
  draws every ant at the wrong size. **Fix:** read the model lengths from `InsectSizes` and drop the two keys (or check
  at load that they agree).

### R6-31 (C, proven; a new side of R3-38): what a worker loses on going into the numbers: its length, its scout memory

- Its own length (it comes back a new size, maybe the other body, `Population.java:618-640` with `pickBody`), its
  `ScoutMemory` (its favourite direction and reach, `MatabeleEntity.java:366-367, 406-412`), and its personality (said
  on purpose, `AbstractAntEntity.java:2127-2129`). The length and the personality are fine as "the numbers carry no
  individuals". The scout memory is a scout's learning: a scout folded in the nest between trips starts over.
  Likely harmless (the colony's finds live in `Raids`); say so in `ScoutMemory`'s comment, or keep scouts real.

### R6-32 (C, each read in the code): comments, notes and numbers that disagree with the code, and dead leftovers

**Rescue and care.**

- `RescueGoal.java:27-28` ("pulls a clamped foe off ... a living one killed first") and `Raid.java:2036-2038`
  ("Freed and whole, the hurt one walks home"): a living holder is killed and its head stays on; `needsRescue` turns
  false (`AbstractAntEntity.java:1129`, a head does not count) and the rescuer stops, so the ant walks home with the
  head, which `ClampCareGoal` takes off in the nest. The behaviour fits the design (BUGS 173); the words do not.
- `MatabeleEntity.java:347`: "wound care by nestmates is step 5" (built). `LostLegs.java:15`: "step 5's rescue carries
  it, later" (built).
- `megaponera_analis.json:95`: "5 or 6 never happen in the game". They can, rarely: a crippled ant (3 or more gone) is
  still bitten by soldiers it passes, a living clamp on it still squeezes, and `ClampCareGoal`'s failed `clean` takes a
  leg (`Clamp.java:249`). Then `needsRescue` is false and it walks home uncalled, which is the paper's triage, so only
  the note is wrong.
- `ClampCareGoal.java:17-19`: "removed ... in 9 cases out of 10 within a day, the leg kept"; see R3-41 and R6-11.

**Nests and food.**

- `megaponera_analis.json` `_note` (line 2): "max_workers 2500", "a layer about 10 termites", "workers_per_piece 10
  meals", "a dead termite mound first, then under a fallen log, else the ground (a shaft and two rooms)": the file says
  100, 1, 20, and only dead mounds exist (`MatabeleNest`).
- `PreyPile.java:34-37` and `PreyPileBlock.java:12` "about 10 termites, one big ant's load"; `MatabeleColonies.dayOfFood`
  (`:108-111`) "one termite an ant, about 10 termites a layer": at today's numbers it gives 2.2 days' food for 100
  workers, not a day (the test only checks "no more than `dayOfFood`").
- `SavannaNumbers.CarryNumbers.DEFAULT` (`species/SavannaNumbers.java:744-745`) says "the same as Megaponera's" but has
  `termites_per_layer` 10 against the file's 1, and the codec and `PreyPile.termitesPerLayer` (`:549-552`) fall back to
  10: a species file missing the number quietly gives a tenth of the food.
- `queen.rich_meal`, `burst_eggs`, `burst_interval_ticks` are never used by the Matabele: prey is never rich
  (`takeFoodAt` and `takeCounted` give plain pieces). `food.pile` is a fungus comb stand-in.
- `MatabeleNest` class doc: a garden room with comb "is taken up once the comb is gone"; in the code it is taken at once
  as a nursery, food or refuse room (`fromMound` 205-210), its comb cells just not used until they rot.

**The entities and the species file.**

- `SavannaTags.MATABELE_NEST_LOGS` and `tags/block/matabele_nest_logs.json` are used nowhere (log nests were dropped,
  `MatabeleNest.java:31-32`); the species file's `_note` still says "then under a fallen log" and "colony.max_workers
  2500" (the value is 100).
- A dead Matabele worker leaves no body (`MatabeleEntity.java:161-165`, "No body of its own kind yet (M8 step 5 has the
  dead carried home)"): step 5 is built, the dead are not carried, and `MATABELE_CORPSE` is used only for dead larvae
  and dropped loads. **(Asked under R6-33.)** should a raider killed in the field lie there as a body (real dead
  raiders are left where they fall)? Today it vanishes in a puff. Reader's pick: yes, `leavesBody` true; the refuse
  rules already crumble it.
- `SavannaEntities.java:89-90` ("a major about 1.7 blocks long ... the user picks the real ones later"), `:305-306`;
  `SavannaSpecies.java:8` ("its species file still to come"); `MatabeleEntity.java:77-89` (steps "to come") and `:347`
  ("wound care by nestmates is step 5").
- `MatabeleCommands.java:535`: `/antcolonies prey` says "about laid x 10" termites; a layer is 1 termite now
  (`raids.carry.termites_per_layer 1`).
- `MatabeleEntity.java:172-185`: health and damage written again in code (only the first tick's values; the file's win).
- `fight.bodies.*.damage` (3, 1.5, 3) is never used by a Matabele: a sting on a player uses `defence.sting.damage`, on
  termites the prick, and the file has no `foe_types`, so nothing else is ever a foe (a wolf, a fox or any mod animal
  biting an ant is never bitten back and does not provoke the nest). Its note says it counts "on a chicken". Either
  say the number is unused or add the foes the real ants sting (real Megaponera sting vertebrate intruders; the user
  may want that only with the savanna's land animals).

### R6-33 (C, proven): a dead Matabele worker leaves no body

- **What is wrong:** `MatabeleEntity.leavesBody` (`MatabeleEntity.java:161-165`) answers no, with the comment "No
  body of its own kind yet (M8 step 5 has the dead carried home)". Step 5 is built; the dead are not carried, and the
  Matabele body entity (`MATABELE_CORPSE`) is used only for dead larvae and dropped loads.
- **What the player would see:** a raider killed by termite soldiers vanishes in a puff where the termites it killed
  lie as bodies.
- **Real biology:** dead raiders are left where they fall.
- **Fix proposed:** `leavesBody` true; the refuse rules already crumble a body in time.

**Your call (2026-11-26):** A Matabele killed in the field vanishes in a puff today. Should it lie there as a body, as the termites' dead do? My pick: yes (real dead raiders are left where they fall); it is one line, and the body crumbles by the rules already there.

---

## The nine cases of "who has the ant", each walked to its end

The cross reader walked nine cases through the code to their end, whether or not they turned out to be faults. Kept
in full, because the sound ones tell a later session what not to "fix".

**1. A rescuer carrying a hurt nestmate, and a foe comes.** `FightGoal.canUse` (`FightGoal.java:136-142`) is true
only with a fight order or after a bite from something within 8 blocks (`pickFoe`, `:337-351`); a foe merely near does
nothing. Then Fight (0) pushes Rescue (2) out. `RescueGoal.stop` (`:105-112`) stops the legs and sets the nestmate
down only if the carry had timed out, so the hurt ant stays in the jaws through the fight (a passenger is frozen,
`AbstractAntEntity.java:1502`, and `needsRescue` is false for it, so it neither calls nor fights). After the fight
`RescueGoal.canUse` (`:77-79`) is true at once, `start` picks a room again, and the carry goes on. If the rescuer dies,
`dropLoadOnDeath` runs the HURT row's drop (`FrameworkLoads.java:62-63`, `ejectPassengers`) and `tickRescue`
(`:1281-1283`) gets the rider off; the hurt one then walks in by itself (`GoInsideGoal`) or, more than 16 blocks out,
is found by the raids' sweep within 5 seconds and limps home.
**Nothing is lost.** What looks wrong is fighting with a nestmate in the jaws (R6-6, point 1), and a rescuer hurt in
that fight keeps its patient (R6-24).

**2. An ant licking a wound, and it gets hungry, is drafted, or the nest is attacked.** Hungry: `EatGoal` (1) starts
1 pass in 10 (`EatGoal.java:62-63`), `WoundCareGoal.stop` (`:82-89`) lets the patient go at once
(`releaseCare`), the lick's progress is lost, another carer may claim the patient on its next look. Drafted: the
carer is "not busy" to the raid and the scouts (R6-8), the raid goal (1) pushes the care out the same way. Attacked:
`NestDefence` gives it a fight order (R6-6), Fight (0) pushes it out. In all three the patient is free again within 20
ticks (`tendedBy` runs out, `:116`) and the claim is given back or runs out in 40. **Nothing is left held.** The
patient may itself be called out to fight (R6-6, point 3) or leave to scout (R6-7).

**3. A hurt ant limping home, picked up by a rescuer: two things moving one body?** No. The call freezes it
(`isCallingForHelp` in `isImmobile`): its `MatabeleWayHomeGoal` is neither ticked nor stopped. `pickUpHurt`
(`:1221-1232`) stops its legs, ends its call and seats it; a rider is frozen too, so its way-home goal stays frozen
the whole carry and the game places the rider (`positionRider`, `:1262-1274`). It stays on the colony's way-home list.
Set down in the nest (`setDownHurt`, `:1235-1249`: off the rescuer, moved in front, legs stopped), its goal ticks
again, `wayHomeOrder` finds it home and takes it off the list (`WayHome.java:131-134`), and the goal ends on the next
pass. Set down outside (the carry timed out) it calls no more for this wound (`holdHelpCalls`, BUGS 175) and limps on
by the same goal. **Sound.** The two faults beside it are who it calls for (R6-5) and the 5 seconds it loses after a
call (R6-23).

**4. An ant carrying prey meets a hurt nestmate calling.** Off a raid: `BringPreyGoal` and `RescueGoal` are both 2,
so neither pushes the other out, and `mayHelp` (`RescueGoal.java:53-57`) is false with anything in the jaws. The prey
goes to the pile; the caller waits for an ant with empty jaws. In the raid's regroup (`Raid.java:1958-1979`) a raider
with empty jaws is given a hurt nestmate before any termite, and one that already holds termites is given neither a
hurt one nor (once full) more prey; an ant carrying a hurt one has "full jaws" and gets no termite (`hasRoom`,
`:1929-1932`). **The prey is never dropped for a nestmate, and never lost by this meeting.** (Hunger cannot take a
carrier either: `EatGoal` asks for empty jaws, `EatGoal.java:62`.)

**5. A raider drafted while it holds food for the queen or an egg.** It is not drafted: `Raids.raiderFit`
(`Raids.java:1039-1043`) and `Scouts.fit` (`Scouts.java:314-317`) both ask for empty jaws. An ant only on its way to
the food, the egg or the remains (jaws still empty) can be drafted; it is sorted behind the idle ones
(`isBusyWithChore`), and when the raid goal pushes its chore out, `stop` gives the queen's feeding and the egg back
(`FeedQueenGoal.java:100-107`, `CarryEggGoal.java:102-113`), and the claim on a load of remains runs out by itself
(`PreyPile.java:262-268`). An ant in the middle of a meal has food in its jaws and is left alone too. **Sound.**

**6. The eight goals of number 2: who gets an idle ant, can one starve the others, can two alternate.** By the list's
order: `FeedQueenGoal`, `FeedLarvaGoal`, `CarryEggGoal`, `CarryRemainsGoal`, then clamp care, rescue, wound care, last
`BringPreyGoal`. Holding a load decides first: every carrying goal's `canUse` is true whenever its own load is in the
jaws, and every other goal of number 2 asks for empty jaws, so a carrier is always taken back by its own goal (prey
included, though it is last in the list). For an ant with empty jaws none can starve the others: the four chores each
need a claim that only one ant, or one ant per item, can hold (the queen's feeder, a hungry young one, a loose egg, a
load waiting), so they are false for nearly every idle ant nearly always; the care goals look on their own clock
(every 2 seconds, 1 in 3 and 1 in 2; rescue every second with no dice). In the 2 seconds before an ant counts as idle
`looksForWork` (`AbstractAntEntity.java:597-599`) makes the chores ask on every pass, so a chore that is waiting wins
over care in that window; with more than a handful of idle ants (the usual state between raids) care still starts
within seconds. Round 3's statue (R3-31, food held and no queen) holds for a Matabele colony too, not written
again here: the colony lives on without its queen until it is empty (`Colony.queenDied`, `Colony.java:675-678`),
whatever the queen's own header comment says ("her colony ends when she dies", `MatabeleQueenEntity.java`); such a
statue is also never drafted (full jaws), so it is one of the number 2 goals holding an ant for good. Two goals of the same number cannot alternate
while one runs; one after the other they can (a lick, then a chore, then a lick), which is as meant: clamp care and
wound care on one patient have a claim each (BUGS 349). **Sound**, apart from the list not being in priority order
(R6-25).

**7. A hurt scout or raider: who decides whether it goes on?** Too many, see R6-7. For a raider: `Raid.observe`
(`Raid.java:1437-1482`, every 5 ticks) drops one that `needsRescue` at the spot (it calls) and lets go one that
`fightsNoMore` (it limps home); `MatabeleRaidGoal` (`:98`, `:107`) and `MatabeleWayHomeGoal.goesHome` (`:60-63`)
judge `fightsNoMore` themselves on every pass. The two agree in the end; between the goal's "no more" and the raid's
"let go" there are up to 5 ticks in which the ant belongs to nobody, and nothing lower can do harm in them (it may
not eat while a raider, `mayHelp` is false for it). For a scout: only the goal's own `fitToGoOn`
(`MatabeleScoutGoal.java:331-333`): crippled, clamped, a fight order or downed end the trip; badly wounded or an open
wound do not (R6-7). A scout bitten fights back (Fight 0), its trip is kept for 30 seconds and taken up again
(BUGS 185, `:216-226`, `:336-346`). **One judge too many for raiders, harmless; one rule too few for scouts.**

**8. Night, and the colony going away, in the middle of each.** Night does nothing to the Matabele's goals: the
species file has `forage_at: []`, so a worker "may not go out" at any hour and is outside only on a raid errand
(`MatabeleEntity.mayGoOut`, `:877-883`); care, rescue and the chores never ask the clock. Dusk and rain are the raid's
and the scouts' own (round 11). Going away (no player within 64 blocks for the grace minute): `AwayLife.foldLoaded`
(`:293-322`) turns every living member that is not felled into a number: the rescuer and the ant it carries (both
folded, the hurt one comes back whole, R3-38), the carrier of prey or remains (its load is not written down, R1-28),
the carer (its claim just ends), the raid (round 11). Near a player the swap into numbers (`Population.goIn`) does
not come up for Matabele in ordinary play: the species file wants 75 real workers (`real_cap` and `crew` 75) whether
watched or not, and a colony of at most 100 puts out at most 75, so there are never more real ants than wanted.
**Nothing new here beyond R1-28 and R3-38.**

**9. `MatabeleBiteOutGoal` at -1.** It can push out every goal that holds MOVE or LOOK, which is all but `FloatGoal`
(JUMP only, so a swimming ant still floats). So it interrupts the fight, the raid walk, a scout's trip (kept and
taken up again), the walk home, a carry of a nestmate or of prey (both stay in the jaws and are taken up again). It
cannot start and stop every tick: starting needs two looks 10 ticks apart (`:74-82`), once running it ends only when
a look finds it out, when it is downed, or when it found nothing to bite, and then it stays off for 600 ticks
(`letGoUntil`, `:127-131`). The one fault is that the fight can never take the ant back from it (R6-9).

## Is the goal table sound, patchy, or a candidate for a rewrite?

**Patchy, not a rewrite candidate.** The priority table itself is small and readable (five levels that matter:
escape, fight, raid errands and eating, work, rest), no two goals that steer the body are without a shared flag, and
nothing was found that flip-flops every tick. It holds together through three plain habits that are worth keeping:
a load in the jaws starts its own goal again; every claim has its own end time; a frozen ant keeps everything as it
is. The patchiness is all in one place: **who may take an ant away from what it is doing** is answered separately by
each taker (the raid, the scouts, the nest's defence, the rescue, the two care goals, the sweep, the help call, the
numbers), nine tests in all (R6-7), kept in step by hand, plus a hand-kept list of goal classes (R6-8) and four small
yes/no hooks on the ant (`isOnColonyErrand`, `answersHelpCalls`, `isInAColumn`, `KEEP_FROM_DEFENCE`). Each new state
(the wound, the carried ant) was added to some and not to the others. This is the raids' lesson in a milder form: not
rules pulling against each other every tick, but judges that quietly disagree.

**The one rule that would replace the most special cases: one owner slot per ant, and one question.**

- The ant says, in one method, **what has it** now: nothing; a chore (a goal of number 2 runs); carrying (and what);
  carried; a patient at rest; a raid; a scout trip; going home; a fight order (it already has this one, with who gave
  it). Most of it is read from what is already there (the load, the vehicle, the running goal's number, the three
  lists, the fight order), so it is a view, not new saved state.
- And **what it is fit for**: whole; hurt and resting (wound open or needs rescue); cannot fight (`fightsNoMore`).
- Every taker asks the same `mayBeCalledAway(for what)` built on those two, with a short order of who outranks whom
  (escape, defence, raid, scout, rescue, care, chores), in place of `raiderFit`, `fit`, `fitToGoOn`, `mayHelp`,
  `free`, `mayDefend`, the sweep's test, `helpWithin`'s test, `mayGoIntoNest`, `isBusyWithChore`,
  `answersHelpCalls` and `keptFromDefence`.

That fixes R6-5, R6-6, R6-8 and R6-7 by removing tests, and closes R3-38's hole (a hurt, carried or fighting ant is "not
free", so it is not folded) with the same question. It is a tidy-up of a few dozen lines across about ten files, with
the existing rescue, care and raid tests as its guard; it changes play only where the owner's calls above say so.

## Balance: the numbers against each other and against real Megaponera

The food sums are in R6-17 and R6-18, the size spread in R6-12, the sting in R6-13. The rest, as the readers summed
them (readings of the numbers, nothing measured):

**Rescue and care.**

- **Can care come in time?** Yes, narrowly, for a raider the column carries home. The wound is decided 6,000 ticks
  after it opens. Worst case: hurt at the attack's start, attack 2,400, regroup up to 600, a march of 60 to 90 blocks at
  2.5 a second (about 500 to 750), the carry down to the hurt room (about 200), a carer found (each free ant looks every
  2 s, 1 in 2) and one lick of `lick_ticks` 300 goal ticks (600 game ticks, BUGS 357): about 4,600 to 4,900. About a
  minute to spare. It breaks if the attack grows, and R6-3, R6-5 and R6-8 each eat into it. The limper left at the spot
  (fresh slowing 30 percent, about 1.75 blocks a second) gets home in about a minute from 90 blocks: fits. A
  living-clamped ant (a quarter speed, 0.6 a second) takes about 150 s from 90 blocks, inside WayHome's 6,000-tick
  `limp_ticks`.
- **The dab window** (11,000 to 36,000 ticks, about 21 real minutes) is wide: fits.
- **The odds against the papers:** untended 0.8 x 0.9 = 72 in 100 die (real: about 80 to 90); licked and never dabbed
  0.2 x 0.9 = 18; licked and dabbed 0.2 x 0.2 = 4 (real with care about 10 to 22, Frank 2018, 2023). Close enough,
  a little kind to the cared-for; no change proposed.
- **Two time scales meet here and nowhere says so:** the wound runs on the TIME scale (a game day a real day,
  `megaponera_analis.json:95`), the raid's stages on game lengths ("a real attack of 10 to 20 minutes would be 8 to 17
  game seconds", `:101`). The lick window is 5 real minutes only because that sum happens to fit; a comment tying
  `infect_ticks` to the raid's longest attack would keep the next tuner from breaking it.

**The species file.**

- Worker lengths 5 to 18 mm, majors about 15: real (the file's groups). The 77 in 100 majors in play: R6-12.
- `colony.max_workers 100` against real colonies of about 400 to 2,300: a written departure ("Honest sizes",
  `docs/DESIGN.md`), kept.
- `lifespan_ticks` 60 game days (a game day a real day): real workers live many months; fits the colony's balance
  note.
- Health 10 (major) against the termite major soldier (12 health, 1.5 bite, two stings to fall, dodge 0.6): one on
  one the ant wins in about five grabs while the soldier needs about four bites (more with clamps): the ant should
  and does win, as design and the real raids say (R3-42 has the near/away side).
- Sting 2 hearts every 1.5 s per ant: R6-13.
- `defence.reach 16` within `watch_radius 48`; `look.radius 2` over a 3-block `nest_radius`: fit together.
- The brood times (`queen.incubation_ticks 3600`, `brood.hatch_ticks 1200`) are hours on the TIME scale where a real
  ponerine takes weeks from egg to worker; I did not follow how `Brood` uses them, so whether they ever bind against
  food and `max_young 10` is unread. Not a finding.

## Solid (do not "fix")

**Nests, the queen, the brood and the prey pile.**

- **Nest sites never read unloaded land:** `MatabeleNest.fromMound` checks the whole reach is loaded first (`isLoadedRound`,
  93-103, 132); new land waits for 24 blocks round (`NewLandMounds.LOADED_ROUND`), more than the 11 it needs.
- **The pile's away side touches no block:** `tickAway` only rots and counts; `bringInCounted` never reads a block; the
  heaps catch up out of sight through the ledger, both ways, and give up cleanly when there is nowhere to lay or nothing
  to take (`NestLedger.applyNurseFood`).
- **Near and away eat by one rule:** real workers and numbers share `WorkerFood`'s started piece; the queen is fed by
  the same `feedQueen` near (goal) and away (`NestLife.nurse`); rot is on game time both ways.
- **Saving the pile:** every number that matters is saved (`edible` with each piece's time, rotten, scraps, owed heap
  layers, the loose termites); `broughtIn` is only for the list and tests, as its comment says.
- **The raid's own catch through a fold** is kept (`Raid.toNumbers` reads the jaws before folding).
- **A carrier that dies** drops its termites as stung termites where it fell (`MatabeleLoads.dropTermites`).
- **The new-land dice** are one seed asked twice the same way (`wouldSettle` and `inNewLand`), so world making and the
  put agree; freshly dead mounds that will hold ants are aged past the capped chimney (`TermiteMoundStructure` 274-280).
- **The remains heap** weathers by random ticks (about a layer in 2 days) against about 1 load a day: it stays small.
- **No goal of this slice scans blocks or mobs every tick:** `BringPreyGoal` and `CarryRemainsGoal` search only when
  carrying or when a load is claimed, with retry waits; the pile's once-a-second look reads only the room cells.

**Rescue, wound care, clamps and lost legs.**

- **Reloads:** wounds, lost legs and their time, the clamp and its holder's id are saved (`AbstractAntEntity.java:2913-2918`,
  `2950-2953`; `Wounds.save/load`, `Clamp.save/load`); the slowings are put back on load (`Clamp.load` calls
  `changed`, `updateLimp` every second); a carried nestmate is saved inside its carrier and rides on after the load,
  and the carrier's `HURT` load is kept; the raid's hurt waiting at the spot are saved (`Raid.java:1121`, `1206`).
  Care claims and "held still" are not saved and need not be (40 ticks).
- **No one held in the air:** a carrier that dies drops its nestmate (`FrameworkLoads.HURT` row: `ejectPassengers`); a
  rider whose carrier lost its load gets off (`tickRescue:1281-1283`); a carrier whose rider is gone lets go of the
  load (`:1278-1280`). A carrier or a carried ant never goes into the numbers near a player (`mayGoIntoNest`: full jaws,
  riding).
- **One carer of a kind** (BUGS 349's claims) holds: picked and claimed in `canUse`, renewed each tick, released on
  `stop` and on done; a patient that dies, is carried or heals ends the care at once (`canContinueToUse`).
- **The wound's course** is on game time, so it runs on while the land is unloaded and is settled on the first
  second back; a second leg lost renews it (the stated pick).
- **The carrier home:** a carrier the column lets go walks home by `MatabeleWayHomeGoal` (priority 1, ahead of
  `RescueGoal`), and `RescueGoal` takes the last stretch to the hurt room; `CARRY_TICKS` sets it down if the room is
  out of reach, and BUGS 175's hold stops the pick-up loop.
- **Lost-leg pieces cannot pile up:** each lies 6,000 of its own ticks (saved) and goes; they are made only per leg
  lost. Drawn from a synced species and length; the renderer falls back to a plain look on a bad save.
- **Sync:** lost legs and the clamp's leg, kind, look and size are synced entity data; the wound is server-only and
  nothing draws it yet (the darker stump is not built, as `docs/DESIGN.md` says).
- **Triage as the paper has it:** 5 or more legs gone never calls and is never carried (`needsRescue`,
  `call_legs_max` 4); a dead head does not call (BUGS 173).
- `MatabeleBiteOutGoal`: read in full; for a hurt ant it is sound: it never runs while the ant is carried (riding is
  immobile), never in its own nest's rooms, lets go for 30 s when nothing may be bitten (BUGS 113), and its route
  tries are spaced (`NO_WAY_TICKS`). Its per-second shut-in look is the raid round's to weigh.

**The entities, the sizes, the defence and the sting.**

- **The length is saved and sent:** `BodyLength` is written and read (`MatabeleEntity.java:406-422`), and the size
  reaches every player through the SCALE attribute, which the game syncs and saves; `readAdditionalSaveData` sets the
  scale after the base class's model-size pass, so the order is right. A test guards the reload (`MatabeleColonyTests:86-103`).
- **The body and the length agree:** the colony picks the body with `Colony.random()` (`Population.java:680-681`),
  then the ant re-rolls its length for that body by rejection (`SavannaNumbers.java:88-96`), which gives exactly the
  spread's share on each side of the split; empty or one-sided groups fall back without a crash.
- **`NestDefence` per player:** every map is keyed by player id; a player who logs out, changes dimension, or goes
  creative or spectator is dropped within a second (`:225-231`); creative and spectator never provoke and are never a
  foe (`fair`, also asked by `provokedBy`); a jump does not reset the wait (`OFF_FOR`); a sneaking player crosses the
  entrance freely. Two colonies near each other each judge their own nest.
- **During a raid the column is in the defence's reach:** `Raids.reach()` widens `foragingRadius`, so
  `realMembers` covers the column and a player touching it far out is still seen.
- **`Pain`:** small, safe between worlds (R3's note), the pain grows with stings in a window and caps at 15 s.
- **`StingPainOverlay`** is client-only (`Dist.CLIENT`), draws only while the effect is on, and reads nothing from the
  server side.
- **Goal list (Matabele side):** BiteOut at -1 checks `(tickCount + id) % 10` on the ticks the goal list is gone
  through (Minecraft runs the full list when `tickCount + id` is even, so the check is really asked); Float and Fight
  share 0 with no shared flag clash; chores at 2 give way to scouting and raids at 1 as finding 3 of the raid review
  wanted; Eat shares 1 with them, so a raider eating finishes its 3 s meal first (fine).
- **Every tick per ant:** `tick()` does two cheap checks after the first tick, `customServerAiStep` one hunger tick a
  second and one heal check a minute; nothing synced or sounded every tick; no searches. The per-ant searches
  (`pickBreach`, `scentCells`, `mayBiteOut`'s loop over all colonies) run only in raids and the bite-out reflex
  (round 11's ground); `getParts` is cached by stage.
- **Commands:** every `/antcolonies` Matabele command needs permission level 2, and each number argument has bounds,
  so a bad argument is refused by the game before the code sees it.
- **Spawn eggs** are only in the creative tab; the Matabele have no natural spawns (no biome modifier), so the
  CREATURE category's cap never meets them.

**Who has the ant.**

- **A load always finds its goal again.** Prey, remains, an egg, nurse food and a hurt nestmate each start their own
  goal the moment the jaws hold them and nothing higher runs, after a fight, the bite-out, a reload, or being let go
  by the raid (`BringPreyGoal.java:57`, `CarryRemainsGoal.java:59`, `RescueGoal.java:77-79`). No goal of number 2
  takes another's load.
- **Prey is never dropped on the floor by a change of hands.** Pushed out, `BringPreyGoal.stop` (`:88-95`) keeps the
  termites unless its own time is up, and then they are laid on the pile out of sight; a carrier that dies drops them
  as stung termites (`MatabeleLoads.dropTermites`); no way to the pile ends in three tries or a walk home first
  (`:120-132`), never a loop.
- **A carried ant is moved by one thing only** (case 3), and a carry lives through the carrier's fight, the carrier's
  death and being set down outside the nest.
- **The raid and the rescue do not fight over the hurt.** Raiders in a column do not count as helpers
  (`answersHelpCalls`), so a hurt ant on the march home does not stop to call at the column; at the spot the raid ends
  the hurt one's fight order before it has it call (`Raid.java:1438-1440`) and sends an ant with empty jaws for it
  before any prey.
- **Eating stands aside for the raid, the scout trip and the walk home through one gate** (`isOnColonyErrand`), and a
  meal already in the jaws is finished or counted as eaten (`EatGoal.java:79`, `:92-96`).
- **A scout pushed aside keeps its trip** for 30 seconds and takes it up where it was (BUGS 185); pushed aside for
  longer, or no longer fit, it is handed back to the colony properly (`finish`, `:349-357`).
- **`DigGoal` cannot throw a Matabele's load away.** Round 3's R3-19 (a first bite puts a soil pellet over what is
  held) needs the colony's "where the soil goes" hook, which only the termite mound has
  (`SavannaParts.java:67`); a Matabele that digs with prey in its jaws keeps the prey.
- **Night cannot strand an ant mid-task** (case 8): no Matabele chore, care or carry asks the clock.
- **Near a player Matabele are not swapped into the numbers** in ordinary play (case 8), so care and rescue are not
  cut short by the swap; only by the colony going away.
- **The bite-out cannot flicker** (case 9), and it does not drop what the ant holds.
- **The queen's list** has nothing that carries or is drafted; her only fight order is the defence's, 40 ticks at a
  time, and she keeps her own fight-back reach of 3 blocks.
- **Not proven, so not a finding:** (1) whether a wounded defender is really carried in and walks out again in a loop
  (R6-6's "likely"); (2) how often a soldier is shut in with raiders (R6-9); (3) what `MatabeleBiteOutGoal.pick` costs
  when it finds a block and then no way to stand by it (one more full look over the pocket on each following tick,
  one block struck off per tick, until none is left): to measure, the nests reader's file may have it.

## What was not read, and what is not known

- Each reader's file ends with the exact lines it read in full, in part, and not at all. Not read line by line by
  anyone: the bodies of `MatabeleNewLandTests`, most of `MatabeleFightTests`, `MatabeleTests` and `MatabeleBodyTests`
  (searched, not read), `FoodStore.java` (the Matabele's store is an unused stand-in), `Errand.java`,
  `QueenToRoomGoal`, and the parts of `NewLandMounds`, `TermiteMoundStructure`, `DeadMounds` and `Nest` that do not
  meet the Matabele.
- Minecraft's own `GoalSelector` was not opened in this round; how it asks the goals is taken from round 3's section
  (which read it).
- Nothing was run. Not known: what a second client does on R6-1 (disconnect or crash); how often R6-3, R6-4, R6-5 and
  R6-21 come up in play; whether R6-20 plays out as read; the cost of R6-26 and R6-28.
- No checked paper figure was at hand for: Megaponera brood times (R6-18), what a larva eats (R6-17), how rescuer size
  matches the hurt one's (R6-10), and the wording of Frank et al. 2017 on when the hurt are fetched (R6-11).
