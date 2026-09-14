# Objective

Implement `docs/decomposition_3/01.md`. Give `PalustrisApp` and `ConnectedApp`
narrow, distinct responsibilities. Remove feature operations from unrelated
shell layers.

# Invariants

- Keep `MainActivity` as the Android entry point.
- Keep `ConnectedApp` for root composition.
- Keep `PalustrisApp` for navigation and layout.
- Keep protocol behavior out of the shell.
- Keep Home and Photo Grid state independent.
- Keep compact and wide navigation behavior.
- Preserve unrelated worktree changes.
- Preserve encrypted account-scoped drafts and settings.

# Decisions

- Use one immutable state snapshot and one narrow action contract for each feature.
- Group actions by ownership, not by position in the old argument list.
- Do not create one aggregate feature bag.
- Keep runtime generation, durable session revision, and notification registry generation distinct.
- Keep every committed slice usable.
- Tests bind real test-only contracts in `AppShellFixtures`. Do not add a
  production default that reports fake success.

# Completed

- 01-A characterization — `ef8e8e6`.
- 01-B account switcher — `7a81eb7`.
- 01-B emoji presentation — `afc77ef`.
- 01-B notification settings — `eabbeb2`.
- 01-B saved collections — `04d31db`.
- 01-B notification inbox — `57a1753`.
- 01-B direct messages — `3816bd8`.
- 01-B profile — `5dfa74b`.
- 01-B thread — `d77e58c`.
- 01-B photo grid — `56fb1f0`.
- 01-B boundary cleanup — `5196d3a`.
- 01-D post projection coordinator — `1ce9842`, `27d2f7d`.
- 01-C post-action host — `0de4585`, `e39eace`.
- 01-B Home, Search, composer, post interactions — `b8037af`.
- 01-B reply seam and content policy removal — `32313eb`.
- 01-C draft host — `2379b44`.

# Current slice

01-E connected-session host and 01-F settings/launch handoff. No work is
uncommitted. The next slice moves identity parameters and launch routing behind
owners.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt`
- `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/` contracts
- `app/src/test/java/me/foxtails/palustris/AppShellFixtures.kt`
- `app/test` shell tests: `NavigationTest.kt`, `HomeFeedTest.kt`,
  `ShellCharacterizationTest.kt`, `SignInScreenTest.kt`, `ReplyComposerTest.kt`,
  `WideNavigationTest.kt`, `SearchPanelRestorationTest.kt`

# Verification

- `2379b44` passed focused suites (`NavigationTest`, `HomeFeedTest`,
  `ShellCharacterizationTest`, `SignInScreenTest`), the full `test` suite, and
  `lintDebug`.
- `app-shell-ownership.md` records the present boundary.
- Live-server and physical-device behavior remain unverified.

# Next

Implement 01-E: introduce the connected-session host and remove
`sessionGeneration`/`sessionRevision` pass-through where a host can own them.
Then 01-F: move settings and launch routing behind owners and remove
`initialNotificationRoute` and the `settingsViewModelUpdate` writes from
`ConnectedApp`. Then 01-G shell assembly and 01-H test construction and docs.
Run `test assembleRelease` before declaring the plan complete.

# Blockers

- `logs/BUGS.txt` records a system-bar instrumentation failure on Android 15.
- Live-server and physical-device behavior remain unverified.
- Account removal still does not delete account-scoped drafts. See `logs/BUGS.txt`.

# Last safe commit

`2379b44` "Move draft persistence behind the host". Earlier slice commits
`b8037af` and `32313eb` are also on `main`.
