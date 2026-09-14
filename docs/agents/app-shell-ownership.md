# App Shell Ownership

**Owner:** app-shell and feature-presentation maintainers.

**Status:** current. The shell decomposition is partially migrated.

**Last reviewed:** 2026-09-14.

**Source baseline:** `5196d3a`.

**Evidence:** source verified. Test verified with the full JVM suite. Device and live-server
behavior remain unverified.

## Present Boundary

`PalustrisApp` accepts narrow feature contracts in `app/src/main/java/me/foxtails/palustris/ui/shell/`.
Each contract has one owner and one presentation responsibility. `ConnectedApp` binds the real
contracts. Test code binds test-only recorders in
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

The removed dead parameter `onOpenReactionPicker` and the duplicate `ownedPosts` input are gone.
Home rows now come from `feedState.ownedPosts` only.

## Remaining Flat Parameters

These still cross the `PalustrisApp` boundary and belong to later slices:

- Identity: `account`, `sessionGeneration`, `sessionRevision`.
- Services: `actionSource`, `draftStore`.
- Home and composer: `feedState`, `postPreferences`, `contentWarningRules`, `onRefresh`,
  `onLoadMore`, `onPublish`.
- Search: `onSearchAccounts`, `onLoadMoreSearch`.
- Post interactions: `onReact`, `onReply`, `onReshare`, `onBookmark`, `onReaction`.
- Launch: `initialNotificationRoute`.

`ActionSource` and `DraftStore` are data-layer services and must leave the shell. `onReply` is a
test-only observer until its tests assert composer behavior.

## Invariants

- A contract carries no session secret, access token, source, repository, or ViewModel.
- `presentationGeneration` and durable `sessionRevision` stay distinct.
- Photo Grid keeps independent feed state and selection from Home.
- Active-account and selected-account notification settings stay distinct.
- Preview values (`Empty`) exist only because call sites are not fully migrated. Remove them when
  the boundary is complete.

## Limits

- Contract verification is JVM and Robolectric only.
- Live-server and physical-device behavior are unverified.
- The full parameter removal, the connected-session host, the post projection coordinator, and the
  settings host remain in `docs/decomposition_3/01.md`.
