# Task State: Profile Liked Tab

**Status:** complete. The Liked timeline tab is implemented and the orphaned
global Likes page and its model are removed.

**Started:** 2026-09-16.

**This task is larger than one safe implementation slice.** It split into the
tab slice and the removal slice. Both are complete.

## Objective

Add a Liked profile tab. Likes are Mastodon favourites and Misskey reactions.
Place the tab to the right of Reposts. Move the Replies tab to the right of
Posts. Replace the separate global Likes page with the tab.

## Invariants

- Keep protocol differences behind adapters.
- Do not expose raw protocol codes to the user.
- Preserve existing profile timeline behavior for Posts, Replies, Media, and
  Reposts.
- Keep account scope: a profile timeline is for one account.
- One slice, one behavior, one commit. Commit only when the slice is green.
- Stage only files that belong to the slice. Preserve unrelated worktree changes.

## Decisions

1. **Availability.** The Liked tab appears for the signed-in account on either
   protocol, and for another account only on Misskey, where `users/reactions`
   accepts the viewed user id. Another Mastodon account has no Liked tab because
   Mastodon exposes favourites only for the signed-in account.
2. **Adapter resolution.** `MastodonProfileService.likedTimeline` calls
   `/api/v1/favourites` for the signed-in account and throws
   `SourceError.Unsupported("profile.liked")` otherwise.
   `MisskeyProfileService.likedTimeline` calls `users/reactions` with the viewed
   user id. Both return a page keyed by their own cursor.
3. **Classification.** Liked is resolved by the adapter. `matchesProfileTimeline`
   returns false for Liked, because liked posts are authored by other accounts.
4. **Capability.** The Liked tab is gated by `ServerCapabilities.likedPosts` for
   self and by the session protocol for another account.
5. **Global Likes page.** The profile Likes chip is removed. The removal slice
   deletes the orphaned global page and model instead of keeping a dead surface.

## Progress

### Liked Tab

Committed in the same commit as this record.

- `ProfileTimelineTab` gains `Liked`.
- `ProfileCategory` order is Posts, Replies, Media, Reposts, Liked, ShowMore.
  The later Featured tab adds a category before Posts; see
  `docs/agents/tasks/profile-featured-tab.md`.
  `profileChipEntries` takes `likedAvailable` instead of `includeLikes`.
- `ProfileUiState` gains `likedAvailable`. `ProfileViewModel` computes it from
  the target and the session protocol.
- `ProfileTimelinePager` gates Liked on `ServerCapabilities.likedPosts`, not
  `profile.timelines`.
- `ProfileScreen`, `ProfileTimelineList`, and `ProfileLargePresentation` render
  the tab from the state flag. The profile Likes chip is gone.
- The Liked tab reuses `R.string.profile_action_likes` as its label, so the
  existing translated catalogs localize the tab. No `profile_tab_likes` string
  was added. `R.string.profile_action_likes_description` is removed from the
  default catalog; it was not present in any catalog, so no catalog test broke.
- Tests: `ProfileScreenTest` chip order and availability.
  `ProfileViewModelTest` availability and Liked paging. `ProfileSourceContractTest`
  and `MastodonIntegrationTest` Liked endpoints. `MisskeyIntegrationTest` Liked
  reactions. `ProfileTimelineClassifierTest` Liked classification.

### Remove The Global Likes Page

Committed in the same commit as this record.

- `LocalPage` no longer has `Likes`. `LargePostOrigin` no longer has `Liked`.
  `likedCollectionTitle` is gone.
- `LikesContract` and its empty actions are gone. `SavedCollections` carries
  only the bookmarks contract. `SavedCollectionsHost` creates one bookmarks
  `SavedPostsViewModel`.
- `SavedPostsCollection` and the collection branch are gone.
  `SavedPostsViewModel` owns bookmarks only. `SavedPostsState.kt` is renamed to
  `SavedPostsUiState.kt` and `SavedCollections.kt` to `BookmarksContract.kt`, so
  the ktlint `standard:filename` rule stays clean.
- `SocialSource.likedPosts` and the Mastodon and Misskey overrides are gone. The
  Liked profile tab uses `profileTimeline` with `ProfileTimelineTab.Liked`.
- The save-only rows keep `saved = true`. The removed like rows are no longer
  special-cased.
- Tests: the two adapter `likedPosts` tests are removed because
  `ProfileSourceContractTest`, `MastodonIntegrationTest`, and
  `MisskeyIntegrationTest` already cover the Liked profile timeline. The
  collection test cases are rewritten for the single bookmarks collection.
- The `ServerCapabilities.likedPosts` flag stays: it gates the Liked tab.
  `AccessScope.LikedPostsRead` stays for the Mastodon favourites request.

## Verification

Focused tests, then `test assembleRelease`, `ktlintCheck`, `lintDebug`.

## Blockers

- No emulator or device is reachable. Device and live-server behavior stay
  unverified.

## Last safe commit

The commit that contains this record. Run `git log -1 --oneline`.
