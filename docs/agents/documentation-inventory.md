# Documentation Inventory

**Status:** current.

**Owner:** Maintainers.

**Last reviewed:** 2026-09-15.

**Stale when:** A document is added, removed, moved, or reclassified.

This page classifies every reviewed document. Use one of these classes: `current`, `planned`,
`historical`, `reference`, `stale`, or `unreviewed`.

- `current`: the content matches source and is maintained.
- `planned`: the content describes future work.
- `historical`: the content records past work. Git history and `docs/archive/` hold the record.
- `reference`: the content provides evidence or external material.
- `stale`: the content contradicts current source or a newer document.
- `unreviewed`: the content is outside this review scope.

## Agent Wiki

| Path | Class | Notes |
| --- | --- | --- |
| `docs/agents/README.md` | current | Agent wiki index. |
| `docs/agents/documentation-inventory.md` | current | This page. |
| `docs/agents/app-shell-ownership.md` | current | Current shell ownership and gaps. |
| `docs/agents/protocol-and-session-ownership.md` | current | Current source and session ownership. |
| `docs/agents/decomposition-01-02-acceptance-matrix.md` | current | Status for every 01 and 02 exit condition. |
| `docs/agents/handoff.md` | current | Short pointer to the active task. |
| `docs/agents/tasks/_template.md` | current | Task-state template. |
| `docs/agents/tasks/plan03-protocol-notifications.md` | historical | Completed Plan 03 task. Kept as the durable record. |
| `docs/agents/tasks/plan03-gate-partials.md` | historical | Completed gate task. Kept as the slice record. |
| `docs/agents/tasks/decomposition-01-02-completion.md` | historical | Completed 01/02 task. Kept as the durable record. |
| `docs/agents/tasks/gradle-no-daemon.md` | current | Uncommitted Gradle wrapper task. Its config files are in the worktree. |
| `docs/archive/README.md` | current | Archive index for superseded material. |

The former task files `app-shell-decomposition.md`, `state-and-lifecycle-repair.md`,
`cancellation-and-shell-continuation.md`, and `reference-localization.md`, plus
`cleanup-progress-report.md`, now live in `docs/archive/agents/`.

## Decomposition Plans

| Path | Class | Notes |
| --- | --- | --- |
| `docs/decomposition_3/progressreport.md` | current | Completion specification for Plan 01 and Plan 02. |
| `docs/decomposition_3/decomposing.md` | current | Documentation reconciliation plan. |
| `docs/decomposition_3/01.md` | historical | Shell plan. Implemented through the completion slices. Plan 01 exits are met. Kept in place as the planning record. Status: acceptance matrix and `03_corrected.md`. |
| `docs/decomposition_3/02.md` | historical | State and lifecycle plan. Implemented through the completion slices. Plan 02 exits are met except blocked device verification. Kept in place as the planning record. Status: acceptance matrix and `03_corrected.md`. |
| `docs/decomposition_3/03.md` | historical | Protocol and notification persistence plan. Rebased at `b715430`. Every chunk is implemented and test verified. Kept in place as the planning record. Status: `03_corrected.md`. |
| `docs/decomposition_3/03_corrected.md` | reference | Corrected completion audit for Plans 01, 02, and 03. Source verified at `beefcb0`. |
| `docs/decomposition_3/04.md` | planned | Utility ownership and retention plan. Rebase pending. |

The earlier `docs/decomp/`, `docs/decomposition_2/`, and `docs/decomposition.md` material is
historical and archived under `docs/archive/`. Do not cite it as current architecture.

## Human Wiki

| Path | Class | Notes |
| --- | --- | --- |
| `docs/wiki/README.md` | current | Human wiki index. |
| `docs/wiki/overview.md` | current | Product overview. |
| `docs/wiki/architecture.md` | current | Layer and owner map. |
| `docs/wiki/accounts-and-sessions.md` | planned | Stub. No body yet. |
| `docs/wiki/server-compatibility.md` | planned | Stub. No body yet. |
| `docs/wiki/data-and-privacy.md` | planned | Stub. No body yet. |
| `docs/wiki/ui-and-navigation.md` | planned | Stub. No body yet. |
| `docs/wiki/notifications-and-direct-messages.md` | planned | Stub. No body yet. |
| `docs/wiki/build-test-and-release.md` | planned | Stub. No body yet. |
| `docs/wiki/troubleshooting-and-contribution.md` | planned | Stub. No body yet. |

## Root Documents

| Path | Class | Notes |
| --- | --- | --- |
| `README.md` | current | User-facing readme. Its TODO section is partially stale. |
| `importantdocs/writing_style.md` | current | Required writing style. |
| `docs/archive/` | historical | Superseded plans, reports, roadmaps, and finished task states. See `docs/archive/README.md`. |
| `docs/README` assets | reference | Images, fonts, and i18n spreadsheets. |

The root plans `decomposition.md`, `0.2.2.cont.md`, `LetsPolish.md`, `FIXTHISSHIT2.md`, and
`interactionparity.md`, and the `docs/roadmaps/` plans, now live in `docs/archive/`.

## Logs

| Path | Class | Notes |
| --- | --- | --- |
| `logs/BUGS.txt` | current | Open limits and corrections. |
| `logs/<date>.txt` | historical | Per-task logs. |

`logs/DONE.txt` and `logs/TODO.txt` are retired. Git history and the task-state files hold
that record.

## Disposition Rules

- Keep a `current` page accurate. Update it in the same slice as the behavior it describes.
- Give a `planned` page an owner, a review date, and a stale condition.
- Mark a superseded page `historical`. Move it to `docs/archive/` and name its replacement.
- Do not cite a `historical` or `stale` page as proof of current behavior.
- Delete a `stale` page when its owner confirms it has no current or historical value.
