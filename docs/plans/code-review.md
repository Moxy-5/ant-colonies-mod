# The full code review, system by system (planned 2026-11-23)

**Why:** the user, 2026-11-23: "full code review framework by framework of the entire mod". The mod was built fast, one
feature at a time, by many sessions; nobody has read all of it in one go. This is job 3 of the three big jobs
(`docs/plans/big-three.md`).

**What is read (the user, 2026-11-23): only the code that plays in the mod.** That is the Java that runs while someone
plays: the colonies, the insects, the trees, the world, the animals. **Not read:** the model and animation making
(`tools/`, the Animal Maker Engine, the workshop, the viewers, the model, texture and clip files), the filming code
(the film director, the guard films), and the tests and test kit as a subject of their own. Tests are still looked at
from the game's side: each round asks whether what the player sees is guarded by a test (4d below).

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
| 12 | **The animation engine** (the user, 2026-11-26): the offline checker, the lab and the motion kit, how the game plays clips, moves and holds, the in-game films and `verify_animal.py`; not the workshop's looks or each animal's maker | `tools/`: `clip_check`, `body_plan`, `spots`, `anim_lab`, `motion_kit`, `ant_motion`, `verify_animal`, `body_course`, `guard_films`, `game_compare`; `framework: entity/body/` (MoveRider, BlockMoves, Gaits, Spots ...), `client/Film*`, `BodyRecorder` | about 9,000 Python, 6,500 Java |

Rounds 5 and 9 are big enough to split in two sessions each if a session runs short.

**Where the rounds stand (2026-11-25):** read: 1 (the brain; its A findings fixed), 2 (the legs, 43 findings, 4 of
grade A, `docs/reviews/full/round-02-legs.md`; fixes not started), 7, 8, 9, 10 (fixed and merged) and 11. Not read
yet: 3, 4, 5, 6.

**Round 6 (2026-11-26):** read, the Matabele ants all but the raids: 33 findings, 3 of grade A, 8 waiting for the
user's call (`docs/reviews/full/round-06-matabele.md`, branch `claude/review-round-6`); fixes not started.

## What every round looks for (the user's order, 2026-11-23: lag first, then behaviours that fight, then bugs)

### 1. Lag (high tick times)

Minecraft has 50 ms for each tick. **Targets (my picks):** in a worst-case scene (a grown mound, a Matabele colony
raiding it, a full river beside them) the mod's whole share stays under about 15 ms a tick; any single tick the mod
pushes past 50 ms is an A finding; an insect costs about 0.02 ms (the old measured target).

- **a. Work done every tick that need not be:** each goal's "may I start" check (Minecraft asks it for every mob,
  again and again), colony parts, searches for blocks or mobs round each insect or animal, routes planned over and over.
- **b. Work that grows with the square of the count:** each insect looking at every other one.
- **c. Reading land that is not loaded.** It makes Minecraft load or build that land on the spot, the worst spikes there
  are. Also far colonies doing real work when they should be numbers.
- **d. Counts that can run away:** animals breeding, eggs, flies, remains, dropped items, insects that never fold back
  into the numbers, anything that is never cleared away.
- **e. Many blocks changed at once:** mound building, trees growing and falling, mud, grass; the light and neighbour
  updates each change sets off; blocks that tick by themselves.
- **f. Saving and memory:** big colony, trail, danger and tree records written too often; lists and records that only
  ever grow.
- **g. What is sent to players:** values sent every tick, particles, sounds.
- **h. Drawing (frames, not ticks):** work done every frame in the renderers, box counts, many insects on screen.
- **i. Making new land:** slow features, and features that reach into the neighbouring chunks (they can chain).
- **j. Small things that add up:** throwaway objects made in busy loops (stutter), log lines written every tick, test
  and debug watchers left running in normal play.

**Measured, never guessed:** each round uses or builds a worst-case scene for its system and reports the cost part by
part, with Minecraft's own tick readout and profiler and the mod's perf scenes. A finding of kind 1 names its number.

### 2. Behaviours that fight each other

- **a. Goals flip-flopping:** two goals taking an insect or animal in turn, or one that starts and stops every tick.
- **b. Two things steering one body:** a goal, the navigator, Minecraft's own walking, a block move.
- **c. Two places judging the same thing** (the raids' lesson), and the colony's orders against a goal's own idea.
- **d. Near and away disagreeing:** the same colony or animal coming out differently watched and unwatched; insects
  doubled or lost when they fold into the numbers and back.
- **e. Tuning numbers pulling against each other,** on/off flags, a timeout that covers another timeout.
- **f. Features meeting each other:** trees falling on mounds, rivers and mud through nests, animals among the insects,
  spawning groups filling each other's room.
- **g. Minecraft's own rules meeting ours:** fire, water, pistons, explosions, lightning, peaceful mode, sleeping
  through the night and other jumps in time, `/tick rate`, the game rules (mob griefing, mob spawning), Minecraft's
  own mobs and their spawning room.
- **h. Other mods:** TerraBlender and biome mods such as Biomes O' Plenty; anything that would break beside them.

### 3. Bugs

- **a. Crashes:** an empty or one-item list (the raid start crash), a list changed while it is being walked, world
  making touching the live world from another thread, drawing code reached on a dedicated server.
- **b. Save and reload:** anything lost, doubled or started over; land unloading under an insect mid-task; a record
  pointing at a mob that is gone.
- **c. Leftovers between worlds:** things remembered from the last world when another is opened in the same sitting.
- **d. More than one player:** code that assumes "the player", and what a second player sees.
- **e. What the player meets:** drops, names, items that can be duplicated, test commands anyone on a server can use,
  the player's own builds damaged by termites, trees or mounds.
- **f. Data files:** a missing or wrong number in a species or animal file crashing the game, or quietly using a
  default nobody chose.

### 4. The rest

- **a. The mod's own building rules:** mind, legs and body kept apart; the framework never naming a species; the
  season from one place; colony dice from `Colony.random()`; a species reaching the brain only through its parts.
- **b. Real biology, ants and termites only:** "my pick" numbers the papers can settle, drift from `docs/DESIGN.md`;
  and balance, each distance, rate and size against the others it meets in play. Animals: only that their food chains
  cannot crash or lag.
- **c. Dead and misleading code:** code nothing calls, leftovers of removed features, comments and notes that disagree
  with the code.
- **d. Test gaps:** behaviour the player sees with no test; tests that would still pass if the feature broke.
- **e. Parts grown past patching,** named plainly as candidates for a rewrite. The review never rewrites one itself.
- **f. What is solid.** Each round also lists the parts it found sound, so no later session "fixes" what works.

## How a round runs

1. **Read.** The session splits its system into pieces and reads every file of it (with helpers reading side by side:
   this is where the extra compute goes). It reads `docs/BUGS.md` for that system first, so it knows the old traps.
2. **Check every finding before writing it down.** A second pass tries to prove each finding wrong against the code:
   walk the real path through it, or write a small test that fails. Findings are marked **proven** (a failing test or
   a walked path), **likely**, or dropped. No guess is written as a fact.
3. **Write the findings** in `docs/reviews/full/<round>.md`, each with: what is wrong in plain words, what the player
   would see, where (file and line), the proof, and the fix proposed. Each is rated:
   - **A**: the player can meet it (a lag spike, a crash, something lost on reload, behaviours visibly fighting).
     (Until 2026-11-26 it got a number in `docs/BUGS.md` when fixed; now its `R` number is its only number.)
   - **B**: it will cost us when building on this (fighting rules, broken layering, a test gap on something risky).
   - **C**: tidy-ups (dead code, wrong comments, names).
4. **No code changes in a reading round.** So reading rounds can run several at once with no clashes.

## No finding is ever lost (the user, 2026-11-23)

- **Saved as it is found.** A reading session writes its findings file piece by piece and commits and pushes it to
  GitHub after each piece, so a session that stops half way (quota, a crash) loses nothing already written.
- **Never deleted, only marked.** Every finding keeps its number for good (`R7-3` is round 7's third). A fix session
  never removes or rewrites a finding; it adds a status line under it: **fixed** (the commit and the test), **open**
  (why), or **not a fault after all** (what was checked).
- **One list of them all: `docs/CODE-REVIEW-BUGS.md` (the user, 2026-11-26, "a separate bugs file for only the code
  review bugs").** What is left on top (grade A first), what is done at the bottom, the counts above. It is written
  by `python tools/review_bugs.py` from the review files and never edited by hand, so it cannot disagree with them;
  `./gradlew build` fails when it is behind (`checkReviewBugs`). So that the list reads right, **the newest status
  line under a finding begins with one of four words: `fixed`, `half fixed` (then what is left), `open` (then why),
  `not a bug`.** Run the tool after writing findings or status lines, and commit the list with them.
- **Findings that wait for the user (the user, 2026-11-26: "a separate part of the list of open bugs that take my
  confirmation").** A fix that changes how the game looks or plays, or a balance or real-biology number, is never
  built before the user says yes. Such a finding gets a line under it, `**Your call (date):** ...`: the question in
  plain words, what happens today, and "My pick: ...". The list shows these in their own part, "Waiting for your
  call", apart from the findings to fix. When the user answers, add `**Call made (date):** ...` under it (it goes
  back among the findings to fix) or, once built, its status line. A fix session that meets such a finding writes
  the line and goes on with the rest of its batch; it does not stop to ask.
- **Review bugs and other bugs are kept apart (the user, 2026-11-26, "so things don't get mixed").** A review
  finding's fix takes no number in `docs/BUGS.md` any more: its `R` number is its number (in the commit, the test's
  comment, the change log), and what was done goes in its status line. The 67 findings fixed before this keep their
  `BUGS` entries. `docs/BUGS.md` is for bugs found any other way (play, test runs, soaks), also one found while
  fixing a review finding.
- **Fixes live on their own branches**, one commit per finding or small batch, each naming its finding's number, so any
  fix can be looked at or taken back alone without touching the others or the findings.
- **The fixes are Claude's call (the user: "this will be fully up to you").** The user is asked only when a fix would
  change how the game looks or plays, or a real-biology number for the ants and termites.

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
