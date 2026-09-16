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

- [Plan 04 utility ownership and retention](tasks/plan04-utility-retention.md) is in progress. 04-A through 04-D and 04-E1 through 04-E3 are complete.
- [Localization string extraction](tasks/localization-string-extraction.md) is complete through slice 4.

## Historical tasks

- [Plan 03 protocol and notification persistence](tasks/plan03-protocol-notifications.md)
- [Plan 03 gate partials](tasks/plan03-gate-partials.md)
- [Decomposition 01 and 02 completion](tasks/decomposition-01-02-completion.md)
- [Archive index](../archive/README.md). It holds the app-shell decomposition, state and
  lifecycle repair, cancellation and shell continuation, and reference localization task
  states.

## Pages

- [Acceptance matrix](decomposition-01-02-acceptance-matrix.md)
- [App shell ownership](app-shell-ownership.md)
- [Protocol and session ownership](protocol-and-session-ownership.md)
