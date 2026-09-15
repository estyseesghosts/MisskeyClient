I inspected:

- `AGENTS.md`
- `importantdocs/writing_style.md`
- `docs/decomp/screens.md`
- `docs/decomposition.md`
- `Screens.kt`
- `HomeFeed.kt`
- All important source callers and related tests

Key findings:

- `Screens.kt` contains four independent areas:
  - Search: `SearchScreen`, `SearchContent`, `SearchField`, `HashtagSearchResults`, `AccountSearchResults`
  - Composer: `ComposeScreen`, `Audience.label`
  - Drafts: `DraftsScreen`
- `HomeFeed.kt` contains:
  - Home orchestration: `HomeFeed`
  - Shared account UI: `AccountAvatar`
  - Shared post rendering: `PostRow`, `PostBodyText`, `PostMetadataRow`
  - Shared interaction UI: `InteractionRow`, `ReactionRow`, `InteractionSummaryRow`
  - Shared support: `PostInteractionPresentation`, `actionsForPost`, `postTimestamp`, `sharePost`, `openExternal`
- Important shared callers include:
  - `PalustrisApp.kt`
  - `SinglePostScreen.kt`
  - `ProfileTimelineList.kt`
  - `SavedPostsScreen.kt`
  - `ThreadedReplies.kt`
  - `NotificationDetailScreen.kt`
  - `MediaViewerScreen.kt`
  - `InlineEmojiText.kt`
  - Profile, notification, direct-message, and large-navigation screens
- Important tests include:
  - `HomeFeedTest.kt`
  - `NavigationTest.kt`
  - `SinglePostScreenTest.kt`
  - `SavedPostsScreenTest.kt`
  - `ProfileScreenTest.kt`
  - `SignInScreenTest.kt`

Minimal move-only split:

```text
ui/search/SearchScreen.kt
  SearchScreen
  SearchContent

ui/search/SearchControls.kt
  SearchField

ui/search/SearchResults.kt
  HashtagSearchResults
  AccountSearchResults

ui/composer/ComposeScreen.kt
  ComposeScreen
  Audience.label

ui/composer/DraftsScreen.kt
  DraftsScreen

ui/components/AccountAvatar.kt
  AccountAvatar

ui/post/PostRow.kt
  PostRow
  PostInteractionPresentation
  postTimestamp
  private PostRow-only helpers

ui/post/PostBody.kt
  PostBodyText

ui/post/PostMetadataRow.kt
  PostMetadataRow
  FilteredHashtagSummary

ui/post/PostInteractionRow.kt
  InteractionRow
  ReactionRow
  InteractionSummaryRow
  actionsForPost
  sharePost
  private interaction helpers
```

Required compatibility points:

- Preserve all existing `testTag` values.
- Preserve `rememberSaveable` keys in `PostRow`, `InteractionRow`, and `DraftsScreen`.
- Preserve `SearchScreen` and `ComposeScreen` public APIs.
- Change private helpers to `internal` only when another extracted file calls them.
- Update imports for every caller that currently imports shared symbols from `ui`.
- Keep `openExternal` in its current location during this extraction, or move it separately because `InlineEmojiText`, media, profile, notification, and link code use it.
- Do not change `HomeFeed` paging, scroll-direction reporting, bottom clearance, pull-to-refresh, or post filtering behavior.
- Do not change post interaction behavior or accessibility semantics.
