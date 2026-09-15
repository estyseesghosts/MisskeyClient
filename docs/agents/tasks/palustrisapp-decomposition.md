# Objective

Split `ui/PalustrisApp.kt` (1078 lines) into a navigation and placement
shell (target about 300 lines) without changing any behavior. Meet the
Plan 01 Step 13 exit: `PalustrisApp` owns navigation and placement, not
feature implementation. This task is larger than one safe implementation
slice. It divides into S1a, S1b, and S1c below.

# Invariants

- Keep the `PalustrisApp` 19-parameter signature stable.
- Keep `AppShellFixtures.app()` stable. Every shell test runs unmodified.
- Keep the `CompositionLocalProvider` (media transition, repost
  confirmation, post-action owner) in `PalustrisApp`.
- Keep `rememberSaveableStateHolder`, list scroll states, and saveable
  dialog state at the same composition node unless a slice proves a move
  safe.
- No new state authority. Exactly one holder per extracted concern.
- Keep protocol behavior out of `ui/`. No stored-format change.
- One slice, one behavior, one commit. Commit only when green.
- Stage only files that belong to the slice. Preserve unrelated worktree
  changes. Do not bundle another author's uncommitted drafts.
- Use Beeline in user-facing text. Keep the codename out of user-facing
  content.

# Decisions

- Extract internals, not the signature. The leaf content composables
  already exist (`AppLocalPageContent`,
  `AppNotificationsDestinationContent`, `AppLargeDetailPane`, `AppDialogs`,
  `AppSelectionSheets`, `PostActionBubbleHost`, `ComposerOverlayHost`,
  `EmojiPickerHost`, `NotificationSettingsSheet`).
- New holders and presenters live in `ui/shell/`, beside the contracts.
- `ShellBackPolicy`, `ShellBackState`, and `dismissTopSurface` stay in the
  shell. They read holder values but own no feature state.
- The audit that motivates this task is
  `docs/decomposition_3/03_corrected.md` (git-ignored planning material,
  do not force-add).

# Completed

- S1a — Transient overlay state lives in
  `ui/shell/ShellOverlayPresenter.kt`. Commit `ced43f2`. The six shell
  suites, the full `test assembleRelease` gate, and `lintDebug` pass.
- S1b — The destination tree lives in
  `ui/shell/ShellDestinationContent.kt`. Commit `101be82`. The six shell
  suites, the full `test assembleRelease` gate, and `lintDebug` pass.
- S1c — Overlay, dialog, and bubble hosting lives in
  `ui/shell/ShellOverlayHost.kt`. Commit `4c97d43`. The six shell
  suites, the full `test assembleRelease` gate, and `lintDebug` pass.

S1 is complete. `ui/PalustrisApp.kt` is 622 lines and owns navigation
and placement. The next work (P1 and later) needs its own task-state
file and verification. This file stays as the completed S1 record.

# Current slice

None. S1 is complete.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/ShellOverlayPresenter.kt` (new in S1a)
- `app/src/main/java/me/foxtails/palustris/ui/shell/ShellDestinationContent.kt` (new in S1b)
- `app/src/main/java/me/foxtails/palustris/ui/shell/ShellOverlayHost.kt` (new in S1c)
- `app/src/main/java/me/foxtails/palustris/ui/PostActionBubbles.kt` (types only, read-only)
- `app/src/main/java/me/foxtails/palustris/ui/navigation/ShellNavigator.kt` (read-only)
- `app/src/test/java/me/foxtails/palustris/AppShellFixtures.kt` (must not change)

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

P1 and later work needs its own task-state file. The recorded order is:

1. P1 (finish the `ui/` package migration; group the
   `ShellDestinationContent` branch callbacks into narrow param bundles to
   replace the 37-parameter signature), Q1 (ktlint/detekt
   with baseline; fix wildcard imports and fully-qualified names), T1
   (mirror test packages to production packages; merge the two
   duplicate-named test classes), V1 (repair instrumentation tests,
   de-flake known tests), then the Plan 04 rebase. See the handoff.

# Blockers

- No emulator or device is reachable. Connected instrumentation stays
  unverified.
- Live-server and signed-release behavior stay unverified.
- The Android 15 system-bar instrumentation failure stays in `logs/BUGS.txt`.
- The worktree holds another author's uncommitted drafts
  (`docs/archive/`, wiki stubs, `documentation-inventory.md`,
  `gradle-no-daemon.md`, images). Do not commit them under this task.
- `docs/decomposition_3/` is git-ignored. Do not force-add planning files.

# Last safe commit

`4c97d43` "Extract overlay hosting into ShellOverlayHost".
