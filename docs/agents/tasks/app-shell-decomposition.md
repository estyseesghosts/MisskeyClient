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

- 01-A characterization — commit `ef8e8e6` "Characterize the app shell before decomposition".
- 01-B account switcher contract — commit `7a81eb7` "Introduce narrow account-switcher contract".

# Current slice

01-B remaining feature contracts. The account switcher is committed. Other
contracts are present as uncommitted work in progress.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt` (uncommitted edits)
- `app/src/main/java/me/foxtails/palustris/ui/AppHomeDestinationContent.kt` (uncommitted edits)
- `app/src/main/java/me/foxtails/palustris/ui/HomeFeed.kt` (uncommitted edits)
- `app/src/main/java/me/foxtails/palustris/ui/shell/AccountSwitcher.kt` (committed)
- Uncommitted shell contracts: `HomeContract.kt`, `SearchContract.kt`, `ComposerContract.kt`, `PostInteractions.kt`, `PhotoGridContract.kt`, `ProfileContract.kt`, `ThreadContract.kt`, `NotificationsContract.kt`, `NotificationSettingsContract.kt`, `DirectMessagesContract.kt`, `SavedCollections.kt`, `PostProjectionCoordinator.kt`, `EmojiPresentation.kt`
- Tests: `HomeFeedTest.kt`, `NavigationTest.kt`, `SignInScreenTest.kt`, `ReplyComposerTest.kt`, `SearchPanelRestorationTest.kt`, `WideNavigationTest.kt`

# Verification

- 01-A: `ShellCharacterizationTest` passed. The shell test group passed.
- 01-B: `NavigationTest`, `SignInScreenTest`, `HomeFeedTest`, and `ShellCharacterizationTest` passed.
- The uncommitted work in progress is not verified.

# Next

Inspect the uncommitted `ui/shell` contract files before editing. Continue one
feature contract at a time. Start with Home. Run the focused shell tests. Commit
each feature contract as its own slice.

# Blockers

- The worktree holds uncommitted contract work. Do not discard it.
- `logs/BUGS.txt` records a system-bar instrumentation failure on Android 15.
- Live-server and physical-device behavior remain unverified.

# Last safe commit

`7a81eb7` "Introduce narrow account-switcher contract".
