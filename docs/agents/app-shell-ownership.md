# App Shell Ownership

**Owner:** app-shell and feature-presentation maintainers.

**Status:** current. The shell decomposition is partially migrated. Completion slices C-01 and C-02
are implemented and test verified in the working tree. Other completion slices repair the remaining
gaps.

**Last reviewed:** 2026-09-14.

**Source baseline:** `b629a2c`.

**Evidence:** source verified. Device and live-server behavior remain unverified. No test ran during
this documentation pass.

**Completion owner:** `docs/agents/tasks/decomposition-01-02-completion.md`.

## Present Boundary

`ConnectedApp` is root composition. It collects session and account index, chooses the startup,
sign-in, or connected presentation, installs theme and application-wide content policy, and composes
the session host. It does not write settings or own post fan-out.

`AccountManager` publishes one `ConnectedSessionContext` under `ui/session/`. The context joins the
visible account, the durable session revision, the runtime presentation generation, and the
registered source. `ConnectedSessionHost` in `ui/session/` reads that context. It composes focused
feature hosts, and keeps the shared feed owner, saved-collection owner, draft owner, post-action
owner, and projection coordinator. It exposes no token and no `SocialSource` to presentation.

`ui/session/ConnectedEntryStore.kt` owns the terminal teardown callbacks for one connected entry.
The store is activity-scoped. It survives activity recreation and retires feature models when the
connected lifetime retires or the owner clears. A composition can leave and return with the same
connected lifetime without stopping a retained model.

Focused feature hosts own their model, state, actions, and projection registration:

| Host | Owner | Contract |
| --- | --- | --- |
| `ui/FeedHost.kt` | Home and Photo Grid `FeedViewModel` | `HomeContract`, `SearchContract`, `PhotoGridContract`, `PostInteractions` |
| `ui/SavedCollectionsHost.kt` | bookmark and like `SavedPostsViewModel` | `SavedCollections` |
| `ui/profile/ProfileHost.kt` | `ProfileViewModel` | `ProfileContract` |
| `ui/thread/ThreadHost.kt` | `PostThreadViewModel` | `ThreadContract` |
| `ui/notifications/NotificationsHost.kt` | `NotificationsViewModel` | `NotificationsContract` |
| `ui/notifications/NotificationSettingsHost.kt` | `NotificationSettingsViewModel` | `NotificationSettingsContract` |
| `ui/directmessages/DirectMessagesHost.kt` | `DirectMessageViewModel` | `DirectMessagesContract` |
| `ui/emoji/EmojiHost.kt` | `EmojiCatalogViewModel` | `EmojiPresentation` |

`ui/DetailActionPolicy.kt` resolves the origin-based post-action handlers. Compact and wide detail
surfaces share it, so one origin resolves to the same owner.

`SettingsOverlayHost` in `ui/settings/` owns the settings route, settings models, and settings
commands. `NotificationLaunchHost` in `ui/notifications/` owns launch delivery.

`PalustrisApp` owns navigation, adaptive layout, and surface placement. It accepts narrow feature
contracts in `ui/shell/`. It also still holds composer editor state, audience, reply, quote, and
draft action state. Completion slice C-05 moves that state to a composer owner.

Test code binds test-only recorders in `app/src/test/java/me/foxtails/palustris/AppShellFixtures.kt`.

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
| `ComposerContract` | composer owner (proposed) | Fields, audience, reply, quote | Publish |
| `DraftsContract` | account draft store | Saved drafts for the active account | Load, save, delete |

`ui/shell/PostProjectionCoordinator` is the single fan-out owner for normalized post updates and
accepted publications. It validates account and durable revision, excludes the origin sink, and
suppresses nested forwarding. `PostProjectionCoordinatorTest` covers origin exclusion, nested
suppression, foreign accounts, old revisions, and publications.

## Known Gaps

Completion slices close these gaps. The acceptance matrix records the status.

| Gap | Source evidence | Completion slice |
| --- | --- | --- |
| Post-action ownership | `ConnectedSessionHost.kt:187` remembers `PostActionOwner` with the whole `profile` contract. A profile update can replace popup ownership. | C-07 |
| Composer editor state | `PalustrisApp.kt` holds editor fields, audience, reply, quote, and draft actions. | C-05, C-06 |
| Projection retirement | `PostProjectionCoordinator` has account and revision checks. It has no explicit retired state or accepted-publication identity. | C-07 |
| Shell assembly | `PalustrisApp.kt` owns navigation and still holds feature state. | C-12 |
| Test isolation | Small feature scenarios still construct the full shell. | C-12 |

## Removed In The Migration

- The dead outer `onOpenReactionPicker` parameter. Live `expandReactionPicker` forwarding remains.
- The duplicate shell `ownedPosts` input. Home rows come from `HomeFeedUiState.ownedPosts` only.
- `actionSource`, `draftStore`, and `postPreferences` as generic shell parameters.
- The test-only `onReply` seam. Reply behavior is asserted through composer state.
- The obsolete `moved...` import aliases.
- The pass-through wrappers `AppHomeDestinationContent`, `AppSearchDestinationContent`,
  `AppPhotoGridDestinationContent`, and `AppProfileDestinationContent`.
- The unregistered `sourceFactory.create(session)` fallback in `ConnectedSessionHost`.
- The separate `activeSession` and `session` inputs to the connected shell.

## Invariants

- A contract carries no session secret, access token, source, repository, or ViewModel.
- `sessionGeneration` and durable `sessionRevision` stay distinct.
- The connected session uses one registered source per session. Recomposition does not create
  replacement sources.
- The shell consumes one accepted connected context. It never joins separate session flows.
- `AccountManager` owns the source factory. The shell does not create a source.
- A feature model retires with its connected entry, not with a composition disposal.
- Photo Grid keeps independent feed state and selection from Home.
- Active-account and selected-account notification settings stay distinct.
- Every source-backed feature receives values from one accepted connected lifetime.

## Limits

- Contract verification is JVM and Robolectric only.
- Live-server and physical-device behavior are unverified.
- The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
