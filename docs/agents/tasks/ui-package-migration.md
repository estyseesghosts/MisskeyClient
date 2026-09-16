# Objective

Finish the `ui/` package migration and group the destination callbacks.
Move the flat feature files out of the `ui/` root into feature packages
without changing any behavior. Group the `ShellDestinationContent`
branch callbacks into narrow param bundles. This task is larger than one
safe implementation slice. It divides into P1a through P1f below.

# Invariants

- Keep the `PalustrisApp` 19-parameter signature stable.
- Keep `AppShellFixtures.app()` stable. Every shell test runs unmodified
  apart from import path updates.
- Keep moves behavior-neutral. Move files and fix imports only. No logic
  change, no signature change, no string change.
- Do not move test files. T1 owns test package mirroring. Update test
  imports only.
- Keep Hilt wiring working. Assisted factories move with their owners.
- Keep protocol behavior out of `ui/`. No stored-format change.
- One slice, one behavior, one commit. Commit only when green.
- Stage only files that belong to the slice. Preserve unrelated worktree
  changes.
- Use Beeline in user-facing text. Keep the codename out of user-facing
  content.

# Decisions

- New homes live beside the existing feature packages.

| Slice | Files | New home |
| --- | --- | --- |
| P1a | `FeedState`, `FeedHost`, `FeedViewModel`, `HomeFeed`, `HomePagingDemand` | `ui/feed/` |
| P1b | `PhotoGridController`, `PhotoGridFeedState`, `PhotoGridScreen` | `ui/photogrid/` |
| P1c | `SearchController`, `SearchScreen` | `ui/search/` |
| P1d | `SavedCollectionsHost`, `SavedPostsScreen`, `SavedPostsState`, `SavedPostsViewModel`, `DraftsScreen` | `ui/saved/`, drafts to `ui/composer/` |
| P1e | `AccountManager` | `ui/session/` |
| P1f | Callback bundles for the destination content | `ui/shell/` |

- `FeedHost` keeps its `ui/shell/` contract edge after the move. Feature
  owners implement shell contracts.
- `AccountManager` publishes `ConnectedSessionContext`. `ui/session/`
  already owns that context and the entry store.
- `DraftsScreen` shows the composer draft list. `ui/composer/` owns
  draft editing and the composer owner.
- P1f defines three narrow bundles beside the destination content:
  post callbacks, draft callbacks, and navigation callbacks. The overlay
  host keeps its three explicit action params.

# Completed

- P1a — The feed group lives in `ui/feed/`. Commit `5cb8342`. The six
  shell suites, the full `test assembleRelease` gate, and `lintDebug`
  pass.
- P1b — The Photo Grid group lives in `ui/photogrid/`. Commit `f485281`.
  The six shell suites, the full `test assembleRelease` gate, and
  `lintDebug` pass.
- P1c — The search group lives in `ui/search/`. Commit `f646b20`. The
  six shell suites, the full `test assembleRelease` gate, and
  `lintDebug` pass. One full-gate run hit a `MediaViewerScreenTest`
  timing flake; the class passes in isolation and the gate is green on
  re-run.
- P1d — The saved group (`SavedCollectionsHost`, `SavedPostsViewModel`,
  `SavedPostsScreen`, `SavedPostsState`) lives in `ui/saved/`, and
  `DraftsScreen` lives in `ui/composer/`. Commit `c54e7cb`. The six
  shell suites, the full `test assembleRelease` gate, and `lintDebug`
  pass. The P1d table row already named all five files; the slice now
  matches it.

# Current slice

P1e — Move `AccountManager` into `ui/session/`. Update the package
declaration and every importing file in `main` and `test`. `ui/session/`
already owns `ConnectedSessionHost` and the entry store, so most
references stay in-package. Check `ui/saved/SavedCollectionsHost.kt`,
which imports `ui.AccountManager` after P1d.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/feed/` (new in P1a)
- `app/src/main/java/me/foxtails/palustris/ui/photogrid/` (new in P1b)
- `app/src/main/java/me/foxtails/palustris/ui/search/` (new in P1c)
- `app/src/main/java/me/foxtails/palustris/ui/saved/` (new in P1d)
- `app/src/main/java/me/foxtails/palustris/ui/composer/DraftsScreen.kt` (moved in P1d)
- `app/src/main/java/me/foxtails/palustris/ui/session/AccountManager.kt` (moved in P1e)
- `app/src/main/java/me/foxtails/palustris/ui/shell/ShellDestinationContent.kt` (P1f only)
- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt` (P1f only)
- `app/src/test/` import paths only. No test file moves.

# Verification

Run per slice, focused suites first:

```powershell
$env:GRADLE_OPTS="-Dorg.gradle.daemon=false"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "*NavigationTest" --tests "*WideNavigationTest" --tests "*SignInScreenTest" --tests "*HomeFeedTest" --tests "*ShellNavigatorTest" --tests "*ShellBackPolicyTest" --tests "*ShellCharacterizationTest"
.\gradlew.bat --no-daemon --console=plain test assembleRelease
.\gradlew.bat --no-daemon --console=plain :app:lintDebug
```

Close standard input. Set an explicit timeout for each Gradle call.
No emulator is reachable. Connected instrumentation stays unverified.

# Next

1. P1e as defined in Current slice above.
2. P1f — Group the destination callbacks into post, draft, and
   navigation bundles.
3. After P1: Q1 (ktlint/detekt with baseline; fix wildcard imports and
   fully-qualified names), T1 (mirror test packages; merge duplicate
   test classes), V1 (repair instrumentation tests), then the Plan 04
   rebase. See the handoff.

# Blockers

- No emulator or device is reachable. Connected instrumentation stays
  unverified.
- Live-server and signed-release behavior stay unverified.
- The Android 15 system-bar instrumentation failure stays in `logs/BUGS.txt`.
- `docs/decomposition_3/` is git-ignored. Do not force-add planning files.

# Last safe commit

`c54e7cb` "Move saved group into ui.saved and drafts into ui.composer".
