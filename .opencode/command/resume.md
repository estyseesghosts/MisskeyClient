---
description: Recover long-task state from the repository and Git.
agent: build
---

Recover the task state from the repository. Do not trust conversation memory.

Read these items in order.

1. `AGENTS.md`.
2. The active task-state file in `docs/agents/tasks/`.
3. `git status`.
4. Recent relevant commits.
5. The current diff.

Report these items.

- The objective.
- The current slice.
- The last safe commit.
- Completed slices.
- Blockers.
- The exact next action.

Rebuild the TODO list from these items. Stop implementation and reconstruct state when the information is incomplete.
