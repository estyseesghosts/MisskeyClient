# App Shell Ownership

**Owner:** app-shell and feature-presentation maintainers.

**Status:** current. The shell decomposition is partially migrated.

**Last reviewed:** 2026-09-14.

**Source baseline:** `fb9d8bf`.

**Evidence:** source verified. Test verified with the full JVM suite, `assembleRelease`, and
`:app:lintDebug`. Device and live-server behavior remain unverified.

## Present Boundary

`ConnectedApp` is root composition. It collects session and account index, chooses the startup,
sign-in, or connected presentation, installs theme and application-wide content policy, and composes
three hosts. It does not write settings, assemble feature actions, or own post fan-out.

`ConnectedSessionHost` in `app/src/main/java/me/foxtails/palustris/ui/session/` owns one coherent
account/session presentation lifetime. It resolves the registered source once per connected session
and composes focused feature hosts. It keeps only the shared feed owner, the saved-collection owner,
the draft owner, the post-action owner, and the projection coordinator. It exposes no token and no
`SocialSource`.

Focused feature hosts own their model, state, actions, and projection registration:

| Host | Owner | Contract |
| --- | --- | --- |
| `ui/profile/ProfileHost.kt` | `ProfileViewModel` | `ProfileContract` |
| `ui/thread/ThreadHost.kt` | `PostThreadViewModel` | `ThreadContract` |
| `ui/notifications/NotificationsHost.kt` | `NotificationsViewModel` | `NotificationsContract` |
| `ui/notifications/NotificationSettingsHost.kt` | `NotificationSettingsViewModel` | `NotificationSettingsContract` |
| `ui/directmessages/DirectMessagesHost.kt` | `DirectMessageViewModel` | `DirectMessagesContract` |
| `ui/emoji/EmojiHost.kt` | `EmojiCatalogViewModel` | `EmojiPresentation` |

`ui/DetailActionPolicy.kt` resolves the origin-based post-action handlers. Compact and wide detail
surfaces share it, so one origin resolves to the same owner.

`SettingsOverlayHost` in `ui/settings/` owns the settings route, settings models, and settings
commands. `NotificationLaunchHost` in `ui/notifications/` owns launch delivery: it waits for the
receiving account to become active and acknowledges only the launch it accepted.

`PalustrisApp` owns navigation, adaptive layout, and surface placement. It accepts narrow feature
contracts in `app/src/main/java/me/foxtails/palustris/ui/shell/`. Each contract has one owner and one
presentation responsibility. Test code binds test-only recorders in
`app/src/test/java/me/foxtails/palustris/AppShellFixtures.kt`.

| Contract | Owner | State | Actions |
| --- | --- | --- | --- |
| `AccountSwitcher` | `AccountManager` | Account references | Switch, add, settings, sign out |
| `EmojiPresentation` | `EmojiCatalogViewModel` | Catalog and capabilities | Load, retry, group and pin preferences |
| `NotificationSettingsContract` | `NotificationSettingsViewModel` | One target account and settings | Eleven settings commands |
| `BookmarksContract` | bookmark `SavedPostsViewModel` | Bookmark collection | Refresh, paging, remove, permissions, react |
| `LikesContract` | like `SavedPostsViewModel` | Like collection | Refresh, paging, toggle, react |
| `NotificationsContract` | `NotificationsViewModel` | Notification inbox | Refresh, paging, read, dismiss, follow, query |
| `DirectMessagesContract` | `DirectMessageViewModel` | Inbox, selection, send | Refresh, paging, open, close, start, send |
| `ProfileContract` | `ProfileViewModel` | Target, categories, relationship, editor | Open, category, paging, follow, react, editor |
| `ThreadContract` | `PostThreadViewModel` | Selected thread | Activate, deactivate, paging, mutations |
| `PhotoGridContract` | Photo Grid `FeedViewModel` | Independent Photo Grid feed | Load, select, refresh, paging, hashtag, error |
| `DraftsContract` | account draft store | Saved drafts for the active account | Load, save, delete |

`ui/shell/PostProjectionCoordinator` is the single fan-out owner for normalized post updates and
accepted publications. It validates account and durable revision, excludes the origin sink, and
suppresses nested forwarding so feed and thread cannot echo. `PostProjectionCoordinatorTest` covers
origin exclusion, nested suppression, foreign accounts, old revisions, and publications.

## Removed In This Migration

- The dead outer `onOpenReactionPicker` parameter. Live `expandReactionPicker` forwarding remains.
- The duplicate shell `ownedPosts` input. Home rows come from `HomeFeedUiState.ownedPosts` only.
- `actionSource`, `draftStore`, and `postPreferences` as generic shell parameters.
- The test-only `onReply` seam. Reply behavior is asserted through composer state.
- The obsolete `moved...` import aliases.
- The pass-through wrappers `AppHomeDestinationContent`, `AppSearchDestinationContent`,
  `AppPhotoGridDestinationContent`, and `AppProfileDestinationContent`. The shell calls the feature
  presenters directly. `AppNotificationsDestinationContent` remains because it owns panel and
  direct-message integration.

## Remaining Flat Parameters

These still cross the `PalustrisApp` boundary and belong to later slices:

- Identity: `account`, `sessionGeneration`, `sessionRevision`.
- Launch: `initialNotificationRoute`.

## Deferred Work

- The Home feed, saved collections, composer, and post-action owner remain in `ConnectedSessionHost`
  because the composer, the feed projection origin, and the like reaction share the feed model. A
  `FeedHost` and `SavedCollectionsHost` would need a feed-action handoff first.
- Teardown: `NotificationsViewModel`, `DirectMessageViewModel`, `NotificationSettingsViewModel`, and
  `ModerationViewModel` expose no `stop()`. They rely on session-generation ViewModel keys, not an
  explicit release. Their owner repair belongs to `docs/decomposition_3/02.md`.
- `PalustrisApp` retains `Empty` contract defaults and remains callable with few arguments. Test
  construction was not rewritten to require explicit feature hosts.
- Navigation transitions use the pure `motionDirection` function. No separate mutable holder was
  added because the shell already owns the saveable navigation state by design.

## Invariants

- A contract carries no session secret, access token, source, repository, or ViewModel.
- `presentationGeneration` and durable `sessionRevision` stay distinct.
- The connected session resolves one registered source per session. Recomposition does not create
  replacement sources.
- Photo Grid keeps independent feed state and selection from Home.
- Active-account and selected-account notification settings stay distinct.
- Preview values (`Empty`) exist only because call sites are not fully migrated. Remove them when
  the boundary is complete.

## Limits

- Contract verification is JVM and Robolectric only.
- Live-server and physical-device behavior are unverified.
- The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
- Account removal does not delete account-scoped drafts. See `logs/BUGS.txt`.
