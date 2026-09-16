# Localization String Extraction

**Status:** active. Owner: agent. Last reviewed: 2026-09-16.

Goal: no user-visible text is declared in code. Every user-visible string
comes from `app/src/main/res/values/strings.xml`.

Scope for now: `values/strings.xml` only. Non-English catalogs stay removed and
are handled later. The worktree locale deletions are not part of this task.

## Rules

- Keep English text byte-identical across a move, so existing tests hold.
- Reuse an existing resource when the text is identical. Add a new key only
  when the text has no equivalent.
- Keep `values/strings.xml` sorted by name.
- Use `translatable="false"` for autonyms (language names shown in their own
  language). These must not be translated.
- Stage only localization files. Preserve unrelated worktree changes (the
  locale deletions and `appsvg/`).

## Slices

### Slice 1 — UI layer (complete)

All user-visible literals in `@Composable` code and the two ViewModels that
already held a `Context` now come from resources.

Files: `AppDestinationTopBar.kt`, `Components.kt`, `emoji/EmojiPicker.kt`,
`emoji/InlineEmojiText.kt`, `media/PostMediaCarousel.kt`,
`notifications/NotificationSettingsViewModel.kt`, `photogrid/PhotoGridScreen.kt`,
`posts/PostRow.kt`, `profile/ProfileScreen.kt`, `profile/ProfileTimelineList.kt`,
`saved/SavedPostsScreen.kt`, `settings/ContentWarningSettingsScreen.kt`,
`settings/DisplaySettingsScreen.kt`, `settings/LanguageSettingsScreen.kt`,
`settings/ModerationListScreen.kt`, `settings/PostingSettingsScreen.kt`,
`settings/PrivacyAccountsScreen.kt`, `settings/SettingsHost.kt`,
`settings/SettingsOverlayHost.kt`, `settings/SettingsScreen.kt`,
`setup/SetupScreens.kt`, and `values/strings.xml`.

Notes:
- Removed the 39 unused keys reported by the source audit. `reaction_count`
  dropped the invalid `many` quantity. The file gained an XML header and is
  sorted.
- `InlineEmojiText.buildInlineContent` is not composable, so it takes the
  `Context` and calls `context.getString`.
- `SettingsScreen` no longer prints `AppTextSize.name` or `AppLanguage.name`.
  `appTextSizeLabel` and `appLanguageLabel` resolve the labels.
- `DisplaySettingsScreen` no longer prints enum names. Background, text size,
  and font each have label resources.
- `Audience.label` in `PostingSettingsScreen` reuses `audience_*`.

Verification: `assembleDebug`, full `testDebugUnitTest`, and `ktlintCheck` pass.

### Slice 2 — ViewModels and controllers (complete)

Decision: option 1. The owning classes stay free of `Context`. Localized copy
lives in the `UiStrings` seam.

- `ui/UiStrings.kt` is new. It is an interface with the localized text that
  view models and non-composable owners need. `UiStrings.from(context)` builds
  the Android-backed implementation. `UiStrings.Default` carries no
  user-facing copy and serves tests and direct construction. It is not for
  production.
- `di/UiStringsModule.kt` is new. It binds the Android-backed implementation
  with `@ApplicationContext`.
- `sourceErrorMessage` now takes a `Context`. `UiStrings` owns the one
  production call.
- Owning classes gained `private val uiStrings: UiStrings = UiStrings.Default`
  at the end of the constructor: `FeedViewModel`, `NotificationsViewModel`,
  `ProfileViewModel`, `ProfileTimelinePager`, `SearchController`,
  `PhotoGridController`, `EmojiCatalogViewModel`, `DirectMessageViewModel`,
  `SavedPostsViewModel`, `ModerationViewModel`, `AccountManager`,
  `ComposerOwner`, and `PostActionOwner`.
- `ComposerHost` and `ConnectedSessionHost` build `UiStrings.from(context)` from
  the composition because those owners are not Hilt-created.
- The `direct messages` and `profile.details` feature codes no longer reach the
  user. `UiStrings.directMessagesUnsupported` and `profileDetailsUnsupported`
  resolve them.

Tests:
- `ComposerOwnerTest` becomes Robolectric and passes a real `UiStrings` because
  it asserts the audience message.
- `SessionViewModelTest.permissionUpgradeDoesNotReplaceAccountWhenReturnedIdentityDiffers`
  passes a real `UiStrings` to the `AccountManager` convenience constructor.
- `CrossCuttingTest.everySourceErrorHasHumanReadableUiMessage` passes a
  `Context`.
- `PostActionOwnerTest` names the `onRelationshipChanged` argument.

The `AccountManager` convenience constructor gained a defaulted `uiStrings`
parameter.

`app/ktlint-baseline.xml` is regenerated. The `UiStrings` files add no entries.
The removed entries belong to the touched files whose style debt is now fixed.

Verification: `assembleDebug`, full `testDebugUnitTest` (1070 tests),
`assembleRelease`, and `ktlintCheck` pass. Source audit: zero unused resources.

### Slice 3 — Domain and data fallback text (planned)

`data/` and `domain/` own English fallbacks such as `MisskeyNotificationMapper`
`"Reaction"`, `"Scheduled post failed"`, and `NotificationSyncOrchestrator`
`"Notification sync failed"`. The data layer has no `Context`. Decide the
mechanism before this slice. Options: a stable code that presentation maps to a
resource, or a `@StringRes` value carried on the model. Do not add `Context` to
the data layer. Also decide the remaining protocol feature codes
(`timeline:Home`, `direct.pagination`, `notifications.followRequest`) that
`sourceErrorMessage` still interpolates.

## Not In Scope

Not user-visible. Do not change: `AppIcons` `ImageVector` names, route and
overlay keys, `testTag` values, animation `label` values, semantics property
keys, `SHA-256` algorithm names, `error()` crash messages, and `toString()`
debug output.

## Risks

- Test churn: every direct-construction site of a changed ViewModel must supply
  a `Context`. Robolectric tests can use `ApplicationProvider`.
- Hidden behavior change: keep the English text identical so no assertion
  shifts.
- `SourceErrorMessage` is shared. Change it once and update every caller.
