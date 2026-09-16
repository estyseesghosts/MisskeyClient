# Objective

Add a Kotlin static-analysis gate and remove the wildcard imports left
behind by the S1 and P1 moves. Keep the repository green.

This task is larger than one safe implementation slice. It divides into
Q1a and Q1b below. The fully-qualified-name cleanup is deferred.

# Completed

- Q1a — ktlint is wired with a baseline. Commit `39cfec3`. The plugin
  `org.jlleitschuh.gradle.ktlint` 14.2.0 pins ktlint 1.5.0. `.editorconfig`
  sets the `android_studio` code style, forbids wildcard imports, keeps
  Compose PascalCase naming, and disables the wrapping, signature, indent,
  and line-length rules. The baseline was generated from the committed
  tree in a separate worktree, so it matches what CI builds. Both
  workflows run `:app:ktlintCheck`.
- Q1b — Clear the six ktlint findings that the icon commit `b331dab` left
  on `main`. Commit `6c3f4f5`. The enum entries in `AppShellState.kt` move
  to separate lines, `AccountAvatar.kt` drops two unused imports, and
  `PostShareSheet.kt` and `BeelineSvgPaths.kt` drop a blank line before a
  closing brace.
- Q1b2 — The same commit left three unit tests failing. Commit `e5c77ad`.
  Restore `nav_direct_messages` to `Direct messages` and update
  `ProfileScreenTest` to walk the new two-step unfollow confirmation.
- Q1c — Remove the 12 wildcard imports from `app/src/main`. Commit
  `99bc0ed`. Files: `MisskeyAuth`, `MisskeyApi`, `Components`,
  `PalustrisApp`, `CompactOverlayMetrics`, `PostRow`. Imports only.
- Q1d — Remove the 16 wildcard imports from `app/src/test` and
  `app/src/androidTest`. Commit `37d8cac`. Imports only. The baseline now
  holds zero `no-wildcard-imports` entries, so the rule guards all Kotlin
  sources with no exceptions.

Q1 is complete. `:app:ktlintCheck`, `test assembleRelease`, and
`:app:lintDebug` pass. The 28 wildcard imports are gone. The
fully-qualified-name cleanup stays deferred.

# Current slice

None. Q1 is complete. Next is T1.

# Scope Decision

- Tool: ktlint through the `org.jlleitschuh.gradle.ktlint` Gradle plugin
  version 14.2.0. License MIT. Maintained through 2026-03. It supports
  Android source sets and baselines. ktlint is the direct fit because its
  `standard:no-wildcard-imports` rule is the behavior this task needs.
  Record the new dependency in the Q1a commit message.
- Wildcards only. Measured 28 wildcard imports in 17 files, not the 12
  the earlier handoff claimed.
- Fully-qualified names are deferred. Measured 445 occurrences in 90
  files. No ktlint or detekt rule can enforce that convention, so the
  cleanup is manual and belongs in its own task.
- The baseline must be generated after the wildcard removal, so the
  `no-wildcard-imports` rule stays enforced for new code.

# Invariants

- Keep changes behavior-neutral. Import edits and build configuration
  only. No logic change, no signature change, no string change.
- Do not reformat unrelated files. A baseline absorbs existing style
  debt. Do not run a repository-wide format.
- Do not change the `PalustrisApp` signature, `AppShellFixtures.app()`,
  stored formats, or protocol boundaries.
- One slice, one behavior, one commit. Commit only when green.
- Stage only files that belong to the slice. Preserve unrelated worktree
  changes.
- Use Beeline in user-facing text. Keep the codename out of user-facing
  content.

# Slices

| Slice | Behavior | Verification |
| --- | --- | --- |
| Q1a | Add ktlint, `.editorconfig`, and a baseline. Wire CI. | `ktlintCheck` green |
| Q1b | Remove all 28 wildcard imports. Regenerate the baseline. | `ktlintCheck` plus full gate |

# Current slice

Q1b is next but is blocked. It removes the 28 wildcard imports. Two of the
files, `ui/posts/PostRow.kt` and `ui/Components.kt`, currently hold
uncommitted edits from another author. Do not edit them until those edits
are committed or set aside. See Blockers.

# Files involved

- `build.gradle.kts` (plugin declaration)
- `app/build.gradle.kts` (plugin application and configuration)
- `.editorconfig` (new)
- `app/ktlint-baseline.xml` (new)
- `.github/workflows/android.yml` (new check step)
- `.github/workflows/release.yml` (new check step)
- Q1b only: the 17 files that hold wildcard imports.

# Wildcard Inventory

28 imports in 17 files:

- Main: `PalustrisApp.kt`, `Components.kt`, `PostRow.kt`,
  `layout/CompactOverlayMetrics.kt` (`androidx.compose.*`),
  `data/misskey/MisskeyApi.kt` (`okhttp3.*`),
  `data/auth/MisskeyAuth.kt` (`data.misskey.*`).
- Test: `NavigationTest.kt`, `SignInScreenTest.kt`, `HomeFeedTest.kt`,
  `NotificationsScreenTest.kt`, `SpringyInteractionsTest.kt`
  (`androidx.compose.ui.test.*`), `SessionViewModelTest.kt`,
  `MisskeyIntegrationTest.kt`, `CrossCuttingTest.kt`,
  `NotificationRepositoryTest.kt`, `MastodonIntegrationTest.kt`,
  `SavedPostsViewModelTest.kt`, `ProfileScreenTest.kt`,
  `ProfileViewModelTest.kt`, `org.junit.Assert.*` in several tests.
- AndroidTest: `ExampleInstrumentedTest.kt` (`org.junit.Assert.*`).

# Verification

Run per slice, focused suites first:

```powershell
$env:GRADLE_OPTS="-Dorg.gradle.daemon=false"
.\gradlew.bat --no-daemon --console=plain :app:ktlintCheck
.\gradlew.bat --no-daemon --console=plain test assembleRelease
.\gradlew.bat --no-daemon --console=plain :app:lintDebug
```

Close standard input. Set an explicit timeout for each Gradle call.

# Blockers

- No emulator or device is reachable. Connected instrumentation stays
  unverified.
- Live-server and signed-release behavior stay unverified.
- The Android 15 system-bar instrumentation failure stays in `logs/BUGS.txt`.
- `docs/decomposition_3/` is git-ignored. Do not force-add planning files.

# Last safe commit

`37d8cac` "Replace test-source wildcard imports with explicit imports".

# Deferred

- Fully-qualified-name cleanup. 445 occurrences in 90 files. Needs its
  own task-state file and careful collision handling.
