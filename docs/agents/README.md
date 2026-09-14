# Beeline Agent Wiki

Status: current  
Owner: Maintainers  
Last reviewed: 2026-09-14  
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

## Current tasks

- [App-shell decomposition](tasks/app-shell-decomposition.md)
