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

- None. S1 is approved and not started.

# Current slice

S1a — Extract transient overlay state into
`ui/shell/ShellOverlayPresenter.kt`. Move `postActionBubbleTarget`,
`pendingExpandedReactionTarget`, `postReactionHandler`,
`pendingEmojiInsertion`, `emojiPickerTarget`, `mediaRequest`,
`profileImageRequest`, `profileDialog`, and `signOutDialog` into the
holder. Move `clearPostActionBubble`, `openHashtagBubble`,
`openReactionBubble`, `expandReactionPicker`, `openMedia`, and
`openProfileImage` into the holder. Keep the account-change and
session-change clearing effects calling into the holder.

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

1. S1a as defined in Current slice above.
2. S1b — Extract the destination tree into
   `ui/shell/ShellDestinationContent.kt` (destination scaffold, destination
   branches, local pages, notifications destination, profile, search, Photo
   Grid, Home wiring). Keep saveable holders and scroll states in the shell
   unless the slice proves a move safe.
3. S1c — Extract overlay, dialog, and bubble hosting into
   `ui/shell/ShellOverlayHost.kt` (bubble host, share sheet, media viewer,
   image viewer, selection sheet, composer overlay, edit-profile sheet,
   emoji picker host, notification settings sheet and its back handler,
   dialogs).
4. After S1: P1 (finish the `ui/` package migration), Q1 (ktlint/detekt
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

`beefcb0` "Record 03-J commit in task state and handoff".
