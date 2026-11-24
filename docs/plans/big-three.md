# The three big jobs (the user, 2026-11-23: "a lot more compute for the project")

1. **Take the red harvester ant out of the mod.** Plan: `docs/plans/harvester-removal.md`.
2. **Rewrite the Matabele raids from scratch.** Plan: `docs/plans/raid-rewrite.md`.
3. **Review the whole mod's code, system by system.** Plan: `docs/plans/code-review.md`.

## The order, and why (my recommendation)

| When | What runs | Why |
|---|---|---|
| **First** | The harvester removal, alone on the framework. Beside it: the raid rewrite's steps 1 and 2 (find the causes, write the tests) and the review's reading rounds for trees, world and animals. | The removal is the shortest job and takes 13,100 lines and 66 tests away, so both other jobs then work on less. The things beside it touch none of its files. |
| **Second** | The raid rewrite's steps 3 to 6. The review reads the framework, the termites and the Matabele (not the raids), and its fix sessions follow. | The framework is clean of harvester leftovers by now. The review skips the old raid code: reviewing code about to be deleted is wasted. |
| **Last** | The review reads the new raid code before it merges, then the tests, then the tools. The closing full run, soak and speed numbers. | A fresh pair of eyes on the rewrite; the tests are read last because every fix before changes them. |

## Rules across all three

- **One writer per set of files.** Each job has its own branch and worktree; reading rounds change no code, so any
  number of them can run at once. Framework changes go one at a time.
- **Nothing the user confirmed in-game changes** without asking.
- **Merges into `master` one at a time,** each after a full run, at the user's word; `C:\Ant Mod` is switched after
  each (the main-folder rule). `C:\Ant Mod` holds Neon's uncommitted lion work today: a merge waits until that is
  committed.
- **Neon's lane** (`tools/`, `references/`, model files) is touched only as each plan says, with a line on
  `docs/HANDOFF.md`.
- The animals (M9) can go on beside all this, in their own sessions, as long as they stay out of the framework while
  the removal and the framework fixes are running.
