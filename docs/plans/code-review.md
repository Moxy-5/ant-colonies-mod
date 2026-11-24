# The full code review, system by system (planned 2026-11-23)

**Why:** the user, 2026-11-23: "full code review framework by framework of the entire mod". The mod was built fast, one
feature at a time, by many sessions; nobody has read all of it in one go. This is job 3 of the three big jobs
(`docs/plans/big-three.md`).

**What is read (the user, 2026-11-23): only the code that plays in the mod.** That is the Java that runs while someone
plays: the colonies, the insects, the trees, the world, the animals. **Not read:** the model and animation making
(`tools/`, the Animal Maker Engine, the workshop, the viewers, the model, texture and clip files), the filming code
(the film director, the guard films), and the tests and test kit as a subject of their own. Tests are still looked at
from the game's side: each round asks whether what the player sees is guarded by a test (question 7).

**What it is for (the user's aims, `apex-aims`):** fewer bugs in play, no lag, and code that is quick to build the next
feature on. Smaller code is never the goal by itself.

## How big it is (measured 2026-11-23)

| | Java files | Lines |
|---|---|---|
| Colony Framework (`framework\`) | 229 | 52,400 |
| Savanna mod (`savanna\`) | 362 | 112,200 (27,400 of them game tests) |
| Desert mod (`desert\`) | 53 | 13,100 (gone before the review reads the framework) |

About 130,000 lines play in the mod once the harvester is gone and the game tests and test bench are set aside. The biggest files:
`AbstractAntEntity` 3,409,
`OtterEntity` 2,939, `CrocodileEntity` 2,699, `AntNavigator` 2,579, `RaidColumn` 2,326 (being rewritten).

## The rounds (one system each)

Each round is one session that only reads and writes its findings into `docs/reviews/full/<round>.md`. Sizes are lines
of Java.

| # | System | Where | Size |
|---|---|---|---|
| 1 | The colony brain: parts, food, brood, away life, neighbours, species files, the season source | `framework: colony/, species/, season/` | 17,200 |
| 2 | Legs: the navigator, the path finder, the crowd, doorways | `framework: nav/` and the walking half of `AbstractAntEntity` | 6,000 |
| 3 | The insect body: `AbstractAntEntity`, the shared goals, fights | `framework: entity/, entity/goal/, fight/` | 11,500 |
| 4 | How bodies move and are drawn in play: the game's body code and the renderers, only for what a player can meet (a wrong pose, a part in a block, lag from drawing); not the engine's design, the clips or the filming code | `framework: entity/body/, client/, coat/`; `savanna: client/` | about 11,000 |
| 5 | Termites: mound, galleries, fungus, feeding, alarm, soldiers, new-land mounds | `savanna: colony/` (not raids), `block/`, termite entities and goals | about 24,000 |
| 6 | Matabele, all but the raids: nests, prey pile, rescue, wound care, defence | `savanna: colony/`, `entity/`, `entity/goal/` | about 5,000 |
| 7 | Giant trees | `savanna: tree/` | 7,600 |
| 8 | World: big savannas, rivers, sand banks, mud, spawning groups | `savanna: worldgen/, registry/` | 9,400 |
| 9 | River hunters: crocodile, otter, hippo, monitor, bull shark | `savanna: animal/`; `framework: animal/` | about 13,000 |
| 10 | River small life and the life cycle: fish, frogs, crab, snail, flies, eggs, breeding, remains | `savanna: animal/` | about 11,000 |
| 11 | **The new raid code**, read last, after the rewrite (job 2) is done | `savanna: colony/Raid*`, the raid goals | new |

Rounds 5 and 9 are big enough to split in two sessions each if a session runs short.

## What every round looks for (the same eight questions)

1. **Bugs a player can meet.** Wrong behaviour, crashes, things lost on save and reload, land unloading under an
   insect, the 20-minute day, a second player, a dedicated server (client-only code reached from the server).
2. **Lag.** Work done every tick that could be done less often, scans of the world, lists rebuilt in hot loops, too
   many real mobs. Measured where it matters (the perf scenes; the target is about 0.02 ms per insect), not guessed.
3. **Rules fighting each other.** Two places judging the same thing, tuning numbers pulling against each other, on/off
   flags, a fix whose only job is to hide another fix (the raid march's lesson, 2026-11-08).
4. **The mod's own building rules.** Mind, legs and body kept apart; the framework never naming a species; the season
   from one place; colony dice from `Colony.random()`; a species reaching the brain only through its parts.
5. **Real biology, for ants and termites only.** Numbers marked "my pick" that the papers can settle; behaviour that
   drifted from `docs/DESIGN.md`. And **balance**: distances, rates and sizes checked against each other in play.
   Animals are not checked for realism (fun first), only that their food chains cannot crash or lag.
6. **Dead and misleading code.** Code nothing calls, leftovers of removed features, comments that no longer say what
   the code does, notes (`DESIGN.md`, `HISTORY.md`) that disagree with the code.
7. **Test gaps.** Behaviour the player sees with no test; tests that would still pass if the feature broke.
8. **Parts grown past patching.** Named plainly for the user as a candidate for a rewrite, like the raids. The review
   never rewrites one itself.

## How a round runs

1. **Read.** The session splits its system into pieces and reads every file of it (with helpers reading side by side:
   this is where the extra compute goes). It reads `docs/BUGS.md` for that system first, so it knows the old traps.
2. **Check every finding before writing it down.** A second pass tries to prove each finding wrong against the code:
   walk the real path through it, or write a small test that fails. Findings are marked **proven** (a failing test or
   a walked path), **likely**, or dropped. No guess is written as a fact.
3. **Write the findings** in `docs/reviews/full/<round>.md`, each with: what is wrong in plain words, what the player
   would see, where (file and line), the proof, and the fix proposed. Each is rated:
   - **A**: the player can meet it (bug, crash, lag, lost on reload). Gets a number in `docs/BUGS.md`.
   - **B**: it will cost us when building on this (fighting rules, broken layering, a test gap on something risky).
   - **C**: tidy-ups (dead code, wrong comments, names).
4. **No code changes in a reading round.** So reading rounds can run several at once with no clashes.

## How the fixes run

- A **fix session per round**, after its findings are written, on its own branch (`claude/review-fix-<round>`).
- **A findings:** each fixed with a test that failed first. **B:** in small batches, each batch one idea. **C:** only
  the cheap ones; the rest stay listed.
- **Nothing the user confirmed in-game changes** without asking. A fix that changes how the game looks or plays, or a
  real-biology number, is put to the user first (one or two questions, with a recommendation).
- **Two strikes, then rethink** holds: a fix that needs a fix twice stops and goes back to the findings.
- Each batch runs its categories; a full run every 3 batches and before every merge into `master`. Guard films only
  for scenes whose insect the fix can move.
- **One writer per set of files.** Fix sessions for systems that share no files (animals and termites) may run side by
  side; framework fixes (rounds 1 to 4) go one at a time, because everything stands on them.

## Order

0. **The baseline, before round 1:** one full run, a 2-day soak (`tools/soak.sh 6 2`) and the perf scenes, their
   numbers written at the top of `docs/reviews/full/SUMMARY.md`. The same three are run at the end; the review must
   leave them no worse.
1. **Now, while the harvester is being removed and the raids rewritten:** the reading rounds neither job touches:
   7 (trees), 8 (world), 9 and 10 (animals).
2. **After the harvester removal merges:** the framework rounds, 1 to 4, then 5 and 6. (Reading them earlier would
   mean reading harvester leftovers that are about to go.)
3. **Fix sessions** follow each round's findings, framework first.
4. **Round 11 (the new raid code)** when the rewrite's step 5 is done, before it merges: a fresh pair of eyes on it.

## What the user gets

- **`docs/reviews/full/SUMMARY.md`**, one page in plain words, kept current: for each system, how healthy it is, the
  A findings and whether each is fixed, the candidates for a rewrite, and the before and after numbers (tests, soak,
  speed).
- After each round, a short report: the three or four findings that matter, and any question that is the user's call.
- No in-game test list: a fix is tested by its own failing-then-passing test; the user plays as always and reports
  only bugs.

**Done when:** every round has its findings file, every A finding is fixed or listed open with its reason, the
baseline numbers are no worse, and `SUMMARY.md` says in one page where the code stands.

## My picks (say no to any)

- Findings first, fixes after, never both in one session: a reader that may also fix stops reading at the first bug.
- The harvester and the old raid code are not reviewed at all (both are going).
- Round 4 keeps the game's own body and drawing code, because a fault there shows in play; it is read only for what
  the player can meet. Say the word and round 4 goes too.
- The `/antcolonies` command and the test bench (`framework: command/, watch/, debug/, gametest/`) are not read:
  they are for testing, not for play.
- 20 worktrees and 129 branches have piled up on this PC; tidying the merged ones is offered at the end, done only at
  the user's word.
