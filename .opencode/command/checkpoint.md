---
description: Run the slice checkpoint order and update the task-state file.
agent: build
---

Run the slice checkpoint in this order for the current slice.

1. Confirm that the implementation is complete.
2. Run the smallest relevant test set.
3. Inspect `git diff` and `git status`.
4. Rewrite the active task-state file in `docs/agents/tasks/`. Update `Completed`, `Current slice`, `Verification`, `Next`, `Blockers`, and `Last safe commit`. Do not append.
5. Stage only the files that belong to the slice.
6. Commit with a clear imperative message. Do not push.
7. Report the commit hash and the next slice.

If the work does not pass verification, do not commit. Record the blocker in the task-state file and `logs/BUGS.txt`.
