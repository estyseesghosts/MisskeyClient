# Beeline Agent Wiki

Status: current  
Owner: Maintainers  
Last reviewed: 2026-09-15  
Stale when: The state-recovery process changes.

This directory holds agent-facing engineering documentation.

## Layers

- `AGENTS.md` holds permanent project law.
- `docs/agents/tasks/<task>.md` holds the current truth of one long task.
- Git commits hold verified history.
- `docs/agents/documentation-inventory.md` classifies every maintained document.

## Rule

- Treat repository state as authoritative.
- Treat conversation context as disposable.

## Task state

- Keep one task-state file for each long task.
- Rewrite the file at each slice boundary.
- Do not append to the file.
- Start each file from [tasks/_template.md](tasks/_template.md).

## State recovery

Read these items at the start of a session, after compaction, and when you are unsure:

1. `AGENTS.md`.
2. The active task-state file.
3. `git status`.
4. Recent relevant commits.
5. The current diff.

Rebuild the TODO list from these items.

## Tooling

- `.opencode/plugins/compaction-state.ts` adds the task-state files to the compaction prompt.
- `/checkpoint` runs the slice checkpoint order.
- `/resume` runs the state recovery order.

## Current tasks

- [Decomposition 01 and 02 completion](tasks/decomposition-01-02-completion.md)

## Historical tasks

- [App-shell decomposition](tasks/app-shell-decomposition.md)
- [State and lifecycle repair](tasks/state-and-lifecycle-repair.md)
- [Cancellation and shell continuation](tasks/cancellation-and-shell-continuation.md)
- [Reference localization](tasks/reference-localization.md)

## Pages

- [Acceptance matrix](decomposition-01-02-acceptance-matrix.md)
- [App shell ownership](app-shell-ownership.md)
- [Protocol and session ownership](protocol-and-session-ownership.md)
- [Documentation inventory](documentation-inventory.md)
- [Cleanup progress report](cleanup-progress-report.md) (historical)
