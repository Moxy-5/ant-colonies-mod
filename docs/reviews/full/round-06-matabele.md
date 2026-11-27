# Full code review, round 6: the Matabele ants, all but the raids (reading round, 2026-11-26)

Read on branch `claude/review-round-6` at commit `b72cba68` (the tip of `claude/review-fix-12`, the branch `C:\Ant Mod`
was on), by a session that did not write the code. Rule book: `docs/plans/code-review.md`. No code was changed; no
gradle, game, game test or film was run, so **nothing here is measured**: every lag finding is "to measure" unless
the path through the code proves its cost.

**What was read, every line:** in `savanna/.../colony/`: `MatabeleNest`, `MatabeleColonies`, `PreyPile`, the Matabele
rows of `SavannaParts`, `WayHome`, and `DeadMounds` and `DeadWorks` for what a Matabele colony living in a dead mound
meets; in `savanna/.../entity/`: `MatabeleEntity`, `MatabeleQueenEntity`, `MatabeleLoads`; in
`savanna/.../entity/goal/`: `BringPreyGoal`, `CarryRemainsGoal`, `MatabeleBiteOutGoal`, `MatabeleWayHomeGoal` (for a
hurt ant and a carrier; round 11 read it for the raids); `block/PreyPileBlock`, `TermiteRemainsBlock`;
`species/InsectSizes` and the Matabele, non-raid records of `SavannaNumbers`; the species file
`megaponera_analis.json` (all but the raid, scout and trail numbers); the Matabele's registration, tags and spawn
eggs; where new land gives a dead mound its colony (`NewLandMounds`, `TermiteMoundStructure`). The rescue and care
code is the framework's, with the Matabele its only user, so it was read here as they use it: `RescueGoal`,
`WoundCareGoal`, `ClampCareGoal`, `Wounds`, `Clamp`, `LostLegEntity`, `LostLegs`, `Pain`, `NestDefence`,
`DefenceNumbers`, and the wound, clamp and carry parts of `AbstractAntEntity`. Not read (other rounds): the raid code
(round 11), the termites (round 5), the framework as such (rounds 1 to 4).

**Who read it.** Three readers with a slice each (nests, queen, brood and the prey pile; rescue, wound care, clamps
and lost legs; the entities, the size spread, defence, the sting and the species file) and one reader across all
of them on one question: **"who has the ant?"**, that is, can two goals, or the colony's orders and a goal, take one
ant in turn or at once, and is anything dropped when an ant changes hands. Their own files are kept in
`docs/reviews/full/round-06-readers/`. The lead session then checked every grade A finding itself against the lines
it names before writing it here.

**Not written again here:** what rounds 1, 2, 3 and 11 already list. Round 3 (the insect body, on the branch
`claude/review-round-3-a86aa8`, not merged here) read the same framework goals and wrote R3-35 (rescue has no claim on
the caller), R3-37 (a walking player is almost never stung), R3-38 (a hurt ant that goes into the numbers comes back
whole), R3-41 (a clamp nearly always costs the leg before care) and R3-30 to R3-36 on the goals' turns. Where a
finding here is a new side of one of those, it says so.

**How to read a finding.** Rating: **A** the player can meet it, **B** it will cost us when building on this,
**C** a tidy-up. Proof: **proven** (the path was walked through the code, lines named), **likely** (one link, named in
the finding, was not checked), **to measure** (a lag finding with no number yet). A finding's `R` number is its only
number. Findings keep their numbers for good; a fix session adds a status line under a finding and never rewrites it.

*(The findings are being written piece by piece; this file is not complete until its "Count" line is filled in.)*
