# Objective

Add a Kotlin static-analysis gate and remove the wildcard imports left
behind by the S1 and P1 moves. Keep the repository green.

This task is larger than one safe implementation slice. It divides into
Q1a and Q1b below. The fully-qualified-name cleanup is deferred.

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

Q1a. In progress.

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

# Deferred

- Fully-qualified-name cleanup. 445 occurrences in 90 files. Needs its
  own task-state file and careful collision handling.
