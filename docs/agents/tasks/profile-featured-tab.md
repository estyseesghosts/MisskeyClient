# Task State: Profile Featured Tab

**Status:** complete, pending commit with this record.

**Started:** 2026-09-16.

**Objective:** Show the profile pinned posts under one rule for both protocols.
Add a Featured profile tab to the left of the Posts tab. Show the tab only when
the profile has more than one pinned post. Show a single pinned post at the top
of the Posts feed and no Featured tab. Keep pinned posts out of every other
profile feed.

## Invariants

- Keep protocol differences behind adapters. The Featured tab reads the pinned
  posts the profile already loaded. It makes no new request.
- Preserve the Posts, Replies, Media, Reposts, and Liked feeds.
- Keep account scope: a profile timeline is for one account.
- One slice, one behavior, one commit. Commit only when the slice is green.
- Stage only files that belong to the slice. Preserve unrelated worktree changes.

## Decisions

1. **No new data path.** Featured renders `ProfileUiState.pinnedPosts`. It is a
   presentation category with `timelineTab = null`. The pager and the ViewModel
   ignore it, so no page loads and no cursor changes.
2. **Tab visibility.** `featuredAvailable` is `state.pinnedPosts.size > 1`. The
   tab leads the category row.
3. **Single pinned post.** With exactly one pinned post, the Posts feed renders
   that post above the page items with no title. No other feed shows it.
4. **Pinned error.** With a pinned load error, only the Posts feed shows the
   error item. The Featured tab does not exist in that state.
5. **Label.** Reuse `R.string.profile_action_likes` for the Liked tab and add
   `R.string.profile_tab_featured` for the Featured tab. Catalogs fall back to
   English until translators add the key.

## Progress

### Featured Tab

Committed in the same commit as this record.

- `ProfileCategory` gains `Featured(R.string.profile_tab_featured, null)` first.
- `profileChipEntries` takes `featuredAvailable` and adds Featured first.
- `ProfileScreen`, `ProfileTimelineList`, and `ProfileLargePresentation` pass
  `featuredAvailable = state.pinnedPosts.size > 1`.
- `ProfileTimelineList` renders one of three contents: Featured pinned posts
  with a title, the ShowMore details, or the selected page. The Posts feed
  prepends a single pinned post. The first-item scroll index follows the same
  rule.
- `profilePinnedItems` takes `posts`, `showTitle`, `showLoading`, and
  `showError`, so Featured and Posts share one pinned renderer.
- Tests: `ProfileScreenTest.largeProfileChipModelKeepsSelfActionsInTheRequestedOrder`
  checks the Featured chip leads. `ProfileScreenTest.pinnedPostsStayInFeaturedWhenMultipleAndLeadPostsWhenSingle`
  checks the single and multiple pinned behavior.

## Verification

Focused `ProfileScreenTest`, then `test assembleRelease`, `ktlintCheck`, and
`lintDebug`. All pass. The ktlint baseline keeps the same 397 (file, rule)
pairs. `git diff` inspection confirms no unrelated change.

## Blockers

- No emulator or device is reachable. Device and live-server behavior stay
  unverified.

## Last safe commit

The commit that contains this record. Run `git log -1 --oneline`.
