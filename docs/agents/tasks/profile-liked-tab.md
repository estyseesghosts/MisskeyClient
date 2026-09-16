# Task State: Profile Liked Tab

**Status:** in progress. The Liked timeline tab is implemented. Removing the
orphaned global Likes page and its model remains.

**Started:** 2026-09-16.

**This task is larger than one safe implementation slice.** It splits into the
tab slice and the removal slice.

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
5. **Global Likes page.** The profile Likes chip is removed. The global
   `LocalPage.Likes` page, the `LikesContract`, the liked `SavedPostsViewModel`
   instance, and `LargePostOrigin.Liked` are orphaned and must be removed.

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
- `ProfileTimelinePager` gates Liked on `likedPosts`, not `profile.timelines`.
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

Planned. Remove `LocalPage.Likes`, the `LikesContract`, the liked
`SavedPostsViewModel` creation, `LargePostOrigin.Liked`, and the orphaned
`likedPosts` source surface. Update the shell fixtures and navigation tests.

## Verification

Focused tests, then `test assembleRelease`, `ktlintCheck`, `lintDebug`.

## Blockers

- No emulator or device is reachable. Device and live-server behavior stay
  unverified.

## Last safe commit

The commit that contains this record. Run `git log -1 --oneline`.
