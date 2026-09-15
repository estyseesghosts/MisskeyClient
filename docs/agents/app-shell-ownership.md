# App Shell Ownership

**Owner:** app-shell and feature-presentation maintainers.

**Status:** current. The shell decomposition is complete. Completion slices C-01 through
C-05, C-06a, C-06b, C-06c, C-07, C-08, C-09, C-10, C-11, C-12a through C-12d4, C-13,
C-14, and C-15 are implemented and test verified. Steps 13, 14, and 15 of the progress
report are complete. No dead scaffolding remains.

**Last reviewed:** 2026-09-15.

**Source baseline:** `b629a2c` (planning). Status refreshed against `c9e06c8`.

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
commands. It publishes the validated account set and delays account models until the restored
index is validated. `NotificationLaunchHost` in `ui/notifications/` owns launch delivery. It acknowledges
a launch only when the receiving shell accepts its route. A missing account routes to the
recoverable unavailable state. The inbox carries a request epoch, so rejected pages change no
state.

`PalustrisApp` owns navigation, adaptive layout, and surface placement. It accepts narrow feature
contracts in `ui/shell/`. Back precedence is a pure policy in `ui/navigation/ShellBackPolicy.kt`.
The shell keeps the back state and the guarded dismissal, and routes dismissal plus the back and
edge-swipe conditions through the policy. Completion slice C-12c reduces the remaining assembly.
`ui/shell/ShellOverlayPresenter.kt` owns transient overlay state behind one boundary: the
post-action bubble, the emoji picker target, the media and profile-image requests, and the dialog
flags. Slice S1a. The shell reads the holder and keeps navigation, placement, and session-bound
validation. Only the profile dialog flag survives process recreation.
`ui/shell/ShellDestinationContent.kt` renders the destination scaffold with its animated
branches: notification detail, local pages, Home, search with Photo Grid, notifications, and
profile. Slice S1b. It owns no state. Saveable holders and scroll states stay with the shell
and pass through unchanged.

`ui/composer/ComposerOwner.kt` owns the composer editor for one connected account. It holds text,
warning, audience, the dirty snapshot, the drafts list, reply and quote restoration, and the publish
flow. It publishes `ComposerNavigation` requests. `PalustrisApp` applies a request by placing the
composer overlay. The shell keeps overlay placement and back precedence. The editor state uses a
saveable snapshot. A session replacement clears restored reply and quote targets.
`ui/composer/ComposerOverlayHost.kt` owns the composer sheet assembly beside the owner: the save
control, the editor bindings, publish with its confirmation message, and tracking cleanup. The
shell keeps the placement condition with open, guarded close, and emoji-picker target requests.

Test code binds test-only recorders in `app/src/test/java/me/foxtails/palustris/AppShellFixtures.kt`.

| Contract | Owner | State | Actions |
| --- | --- | --- | --- |
| `AccountSwitcher` | `AccountManager` | Account references | Switch, add, settings, sign out |
| `EmojiPresentation` | `EmojiCatalogViewModel` | Catalog and capabilities | Load, retry, group and pin preferences |
| `NotificationSettingsContract` | `NotificationSettingsViewModel` | One target account and settings | Eleven settings commands |
| `BookmarksContract` | bookmark `SavedPostsViewModel` | Bookmark collection | Refresh, paging, remove, permissions, react |
| `LikesContract` | like `SavedPostsViewModel` | Like collection | Refresh, paging, toggle, react |
| `NotificationsContract` | `NotificationsViewModel` | Notification inbox | Refresh, paging, read, dismiss, follow, query |
| `DirectMessagesContract` | `DirectMessageViewModel` | Inbox, selection, send, composer editor | Refresh, paging, open, close, start, update editor, send |
| `ProfileContract` | `ProfileViewModel` | Target, categories, relationship, editor | Open, category, paging, follow, react, editor |
| `ThreadContract` | `PostThreadViewModel` | Selected thread | Activate, deactivate, paging, mutations |
| `PhotoGridContract` | Photo Grid `FeedViewModel` | Independent Photo Grid feed | Load, select, refresh, paging, hashtag, error |
| `ComposerContract` | `ui/composer/ComposerOwner.kt` | Editor fields, dirty snapshot, drafts, reply, quote | Update editor, save, delete draft, publish |
| `DraftsContract` | account draft store | Saved drafts for the active account | Load, save, delete |

`ui/shell/PostProjectionCoordinator` is the single fan-out owner for normalized post updates and
accepted publications. It validates account and durable revision, excludes the origin sink, and
suppresses nested forwarding. It retires with its connected entry and rejects repeated publication
deliveries by created-post identity. `PostProjectionCoordinatorTest` covers origin exclusion, nested
suppression, foreign accounts, old revisions, publications, duplicate rejection, and retirement.

## Known Gaps

This table records each gap and its state. A row that names a completion slice is still open. The
acceptance matrix records the status.

| Gap | Source evidence | Completion slice |
| --- | --- | --- |
| Post-action ownership | Closed by C-07. The popup owner is built from the stable connected identity and reads the latest refresh callback without recreation. P-02 narrowed the leaf contract: `LocalPostActionOwner` provides `PostPopupPresentation`. | — |
| Composer completion | Closed by C-06a. `ComposerOwner.publish` reserves the submission and rejects obsolete save callbacks. | — |
| Draft storage boundary | Closed by C-06b. `data/auth/DraftActions.kt` owns storage and binds to one account. `ui/shell/DraftsContract.kt` carries no storage type. | — |
| Draft removal coordination | Closed by C-06c. `AccountManager.removeAccount` revokes draft writers and deletes rows in one serialized boundary. A revoked `DraftActions` writer writes nothing and reports no success. | — |
| Projection retirement | Closed by C-07. The coordinator retires with its connected entry and rejects repeated publication deliveries by created-post identity. | — |
| Home paging demand | Closed by C-08. `HomePagingDemand` tracks filter identity and the request epoch beside the row count. Only accepted pages consume the budget. | — |
| Notification launch | Closed by C-09. `NotificationLaunchHost` acknowledges a launch only when the receiving shell accepts its route. Rejected pages change no state. | — |
| Dead scaffolding | Closed by C-15. No caller remained for any removed symbol. `FeedViewModel` and `AccountManager` use the `data.notifications` names directly. | — |
| Shell assembly | Closed by C-12d3. `PalustrisApp.kt` owns navigation and placement. Composer sheet assembly moved to `ui/composer/ComposerOverlayHost.kt` in C-12a. Back precedence moved to `ui/navigation/ShellBackPolicy.kt` in C-12b. Preview-only placement moved to `ui/PalustrisAppPreview.kt` in C-12d1. Navigation state and transitions moved to `ui/navigation/ShellNavigator.kt` in C-12d2 and C-12d3. Session-bound guards stay in the shell. | — |
| Test isolation | Closed by C-12d4. `ReplyComposerTest` composes at feature level through `ComposerFeatureFixtures` since C-12c. `HomeFeedTest` and `SignInScreenTest` compose presenter behavior through `HomeFeatureFixtures` since C-12d4. Scroll clearance, detail navigation, composer, account switching, confirmation, and popup-host assertions stay on `AppShellFixtures.app`. | — |
| Cancellation | Closed by C-13. Every touched suspending path rethrows `CancellationException`. `PostInteractionMutationOwner.handleFailure` rethrows before its guarded fallback. | — |

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

## Removed Cleanup (C-15)

C-15 removed the dead scaffolding with no caller: `LegacyLargeProfilePresentation` and
`LegacyProfileHeader` (replaced by `ProfileLargePresentation.kt` and `ProfileHeader.kt`),
`LegacyMediaTransitionImage` and `LegacyMediaTransitionImageCanvas` (replaced by the media
transition layer), `MarkdownPostText` (replaced by `InlineEmojiText`), `AppBackHandler` and
`BackNavigationState` (replaced by the root back policy), `SectionTabs` (replaced by inline
tab rows), and the `AccountSyncCoordinator` aliases (replaced by the `data.notifications`
names in `FeedViewModel`, `AccountManager`, and the feed tests).

## Invariants

- A contract carries no session secret, access token, source, repository, or ViewModel.
- `sessionGeneration` and durable `sessionRevision` stay distinct.
- The connected session uses one registered source per session. Recomposition does not create
  replacement sources.
- The shell consumes one accepted connected context. It never joins separate session flows.
- `AccountManager` owns the source factory. The shell does not create a source.
- A feature model retires with its connected entry, not with a composition disposal.
- The account lifecycle issues the direct-message writer generation. A repository captures it. A
  revoked writer cannot mutate current DM storage. Network requests stay outside the lock.
- The account lifecycle issues the draft writer generation. `DraftActions` captures it. A revoked
  draft writer cannot recreate removed drafts. Removal revokes writers before deleting rows.
- The direct-message composer text stays with the direct-message feature owner. A screen does not
  hold it and does not clear it on Send.
- The composer editor stays with the composer feature owner. The shell requests transitions and
  places the overlay. It does not hold editor fields.
- Post-action family slots are typed with operation tokens. Only the owning token releases a
  slot. A busy family rejects its second caller without waiting.
- The popup owner and the projection coordinator retire with the connected entry. A retired
  popup has no authority. A retired coordinator delivers nothing.
- Photo Grid keeps independent feed state and selection from Home.
- Home paging demand resets on filter identity and request epoch changes. Only accepted pages
  consume the no-progress budget. The demand blocks while sign-in is required.
- Post commands bind to the validated account set. A removed target reports unavailable at call
  time and revokes queued writes. Failed commands are retained for explicit retry. Command errors
  resolve to resources in the shell.
- The locale owner applies first-upgrade precedence once. A moved repository exports the in-app
  choice to the platform. A moved platform imports the external choice into the repository.
  `MainActivity` serializes each locale decision with its side effect.
- Active-account and selected-account notification settings stay distinct.
- Every source-backed feature receives values from one accepted connected lifetime.

## Limits

- Contract verification is JVM and Robolectric only.
- Live-server and physical-device behavior are unverified.
- The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
