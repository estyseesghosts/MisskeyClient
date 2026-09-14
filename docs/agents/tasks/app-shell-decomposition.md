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

# Current slice

01-B remaining contracts and 01-C composer host. The worktree holds
uncommitted work for the Home contract, the Search contract, the composer
contract, and post interactions.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt` (uncommitted edits)
- `app/src/main/java/me/foxtails/palustris/ui/AppHomeDestinationContent.kt` (uncommitted edits)
- `app/src/main/java/me/foxtails/palustris/ui/HomeFeed.kt` (uncommitted edits)
- Uncommitted shell contracts: `HomeContract.kt`, `SearchContract.kt`, `ComposerContract.kt`, `PostInteractions.kt`
- Committed shell contracts: `AccountSwitcher.kt`, `EmojiPresentation.kt`, `NotificationSettingsContract.kt`, `SavedCollections.kt`, `NotificationsContract.kt`, `DirectMessagesContract.kt`, `ProfileContract.kt`, `ThreadContract.kt`, `PhotoGridContract.kt`, `PostProjectionCoordinator.kt`
- Tests: `HomeFeedTest.kt`, `NavigationTest.kt`, `SignInScreenTest.kt`, `ReplyComposerTest.kt`, `SearchPanelRestorationTest.kt`, `WideNavigationTest.kt`

# Verification

- Every committed slice passed its focused tests. See `logs/DONE.txt` lines 616-694.
- The uncommitted work in progress is not verified.

# Next

Inspect the uncommitted contract files before editing. Finish the Home and
Search contracts. Finish the composer host. Run the focused shell tests. Commit
each contract as its own slice.

# Blockers

- The worktree holds uncommitted contract work. Do not discard it.
- `logs/BUGS.txt` records a system-bar instrumentation failure on Android 15.
- Live-server and physical-device behavior remain unverified.

# Last safe commit

`e39eace` "Record post-action host ownership". Later commits on `main`
(`826e516` through `7431883`) change documentation and tooling only. They do
not change application behavior.
