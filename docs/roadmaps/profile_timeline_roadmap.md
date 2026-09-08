# Profile timeline roadmap

## Objective

Replace the placeholder profile timeline with real feeds backing the first four profile categories for both the signed-in account and accounts opened from a post or account search. The profile UI and chip order are defined by `docs/roadmaps/profile_ui_roadmap.md` and must remain exactly:

1. **Posts** — only top-level posts authored by the displayed account. Quote-reposts belong here when the server exposes them as authored posts.
2. **Media** — only posts authored by the displayed account that contain one or more uploaded media attachments. A boosted/reposted post with somebody else's media is not media authored by the displayed account.
3. **Reposts** — only pure reposts/renotes/boosts performed by the displayed account. Quote-reposts must not appear here.
4. **Replies** — only replies authored by the displayed account **to another account's post or reply**. Self-replies must not appear.
5. **Show more...** — a details category with no feed request; it renders the loaded profile details inline.

The profile header, biography, self-only profile actions, and inline profile details remain. The retired profile-fields dialog and former conventional tab row are not part of this feature. This is deliberately a protocol-neutral UI and ViewModel feature: protocol selection and endpoint details remain in the `SocialSource` adapters.

## Definition of the four classifications

Use one shared, testable definition throughout the implementation. The target account below is the account whose profile is open, not necessarily the currently authenticated account.

| Tab | Inclusion rule | Explicit exclusions |
| --- | --- | --- |
| Posts | `post.author.id == targetId`, `post.replyTo == null`, and `post.resharedBy == null` | Replies, pure reposts, posts authored by anyone else. A quote-repost is included because it is an authored post with a `quote`, not a pure repost. |
| Replies | `post.author.id == targetId`, `post.replyTo != null`, and `post.replyToAuthorId != targetId` | Root posts, boosts/renotes, replies to the target's own post or reply, and a reply whose parent author cannot be determined. |
| Media | `post.author.id == targetId` and `post.attachments.isNotEmpty()` | Pure reposts, because the visible attachment belongs to the original author; no-media posts. Replies with the target's own uploads are included, matching the stated requirement that this tab contains posts by the user with media uploads. |
| Reposts | `post.resharedBy?.id == targetId` | Quote-reposts, root/reply posts authored by the target, and reposts by anyone else. |

The adapter must apply this filter after mapping its server response even when it also asks the server to filter. Server flags are an efficiency optimization, not an authorization or correctness guarantee—especially for Misskey-family forks and Mastodon-compatible implementations.

## Architectural approach

### 1. Add a protocol-neutral profile-timeline contract

Introduce a domain query instead of leaking Mastodon URL parameters or Misskey request bodies above the adapter boundary:

```kotlin
enum class ProfileTimelineTab { Posts, Media, Reposts, Replies }

data class ProfileTimelineQuery(
    val profileId: AccountId,
    val tab: ProfileTimelineTab,
)
```

Add the following default operation to `SocialSource`:

```kotlin
suspend fun profileTimeline(
    query: ProfileTimelineQuery,
    cursor: String? = null,
): Page<Post> = unsupported("profile timeline")
```

The cursor remains an opaque `String`, just as it is for existing timelines and hashtag search. The ViewModel must pass it back verbatim; it must never build a pagination URL or synthesize a `untilId`.

### 2. Preserve enough post metadata to enforce the replies requirement

The existing `replyTo` ID establishes that a post is a reply but does not identify the parent author, so it cannot distinguish a reply to another person from a self-reply. Extend `Post` with:

```kotlin
val replyToAuthorId: AccountId? = null,
```

Place it next to `replyTo` and retain a default value to avoid breaking existing call sites and test fixtures.

Populate it from protocol response metadata:

- **Mastodon:** map `in_reply_to_account_id` to an `AccountId` using the adapter's authenticated connection/origin.
- **Misskey:** prefer `replyUserId` when present; otherwise use `reply.user.id` when the expanded reply object is supplied. Construct the ID with the displayed account's connection/origin, as the rest of `MisskeyMapper` does.

For privacy, redaction, older server versions, or partial federated data, that identity may be absent. The client must omit an indeterminate reply from the Replies tab rather than accidentally showing a self-reply. It is preferable to under-display an unverifiable reply than violate the tab's strict promise.

### 3. Keep profile loading separate from the home feed

Do not overload `FeedViewModel` or `FeedState` with a growing map of profile IDs and four independent paginated lists. `FeedViewModel` represents one account's home/local/etc. feed and is already responsible for account sync, publish state, and feed actions.

Create an assisted `ProfileTimelineViewModel` for the active authenticated session. It receives the current authenticated `AccountId` (for `OwnedPost`) and a `SocialSource`, but it does **not** register another account-sync worker. Its job is only explicitly requested profile pages.

Its immutable UI state should be keyed by the open profile and tab, for example:

```kotlin
data class ProfileTimelinePageState(
    val posts: List<Post> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
)

data class ProfileTimelineState(
    val profileId: AccountId? = null,
    val pages: Map<ProfileTimelineTab, ProfileTimelinePageState> = emptyMap(),
)
```

Required ViewModel operations:

- `refresh(profileId, tab)`: cancel/replace the in-flight initial request for that exact `(profileId, tab)` pair, clear that tab's page, call `source.profileTimeline(query)`, deduplicate by `Post.id`, and publish the returned opaque cursor.
- `loadMore(profileId, tab)`: reject calls when the target does not match state, when loading is already in progress, or when `nextCursor` is null; append and deduplicate; suppress a repeated cursor with `takeUnless { it == requestedCursor }` as the home-feed code already does.
- `clearForProfileChange(profileId)`: cancel jobs and atomically clear pages before requesting data for a different profile. Every coroutine must verify that its target still matches state before writing a result, preventing a late response for Alice from appearing on Bob's profile.
- `stop()`: cancel all profile jobs from the `DisposableEffect` that owns the ViewModel.

Map errors with the existing `sourceErrorMessage` and `requiresSignIn` helpers. A 401 must render a retry/sign-in-again action rather than leaving a permanent spinner.

Convert the displayed page to `OwnedPost(authenticatedAccountId, post)` at the UI boundary. This means action availability reflects the session that fetched the data, while `post.author` correctly remains the profile owner or original author.

## Adapter implementation details

### Mastodon adapter

Implement `MastodonSource.profileTimeline` with `GET /api/v1/accounts/{id}/statuses`, a page size of 40, Bearer authentication, and Link-header pagination through the existing `getPage`/origin-validation path.

Build the initial request with an `HttpUrl.Builder` and `addPathSegment(query.profileId.localId)` rather than string interpolation. IDs are opaque values and must not become executable path or query syntax. Continue to accept only the validated same-origin Link URL on subsequent calls.

Use the following server-side request hints, then enforce the shared classification table after `MastodonMapper.post`:

| Tab | Mastodon request parameters |
| --- | --- |
| Posts | `exclude_replies=true`, `exclude_reblogs=true`, `limit=40` |
| Replies | `exclude_replies=false`, `exclude_reblogs=true`, `limit=40` |
| Media | `only_media=true`, `exclude_reblogs=true`, `limit=40` |
| Reposts | `exclude_replies=true`, `exclude_reblogs=false`, `limit=40` |

For Reposts, retain only mapped rows whose `resharedBy` is the requested profile. The mapper represents a Mastodon `reblog` envelope as the original status plus `resharedBy` set to the outer account, so this is the reliable pure-boost discriminator. A Mastodon quote status remains a normal authored status with a `quote`; it therefore belongs in Posts or Media as appropriate, never Reposts.

The Replies request deliberately cannot rely on `exclude_replies=false` alone: that merely allows replies. Filter mapped rows by the new `replyToAuthorId` condition to remove self-replies.

### Misskey adapter

Implement `MisskeySource.profileTimeline` with `POST /api/users/notes`. Include the access token, target `userId`, `limit=40`, and the unchanged opaque cursor as `untilId` when loading more.

Use these body flags as the best server-side reduction, then apply the shared mapped-post classification locally:

| Tab | Misskey request body flags |
| --- | --- |
| Posts | `withReplies=false`, `withRenotes=false` |
| Replies | `withReplies=true`, `withRenotes=false` |
| Media | `withFiles=true`, `withRenotes=false` |
| Reposts | `withReplies=false`, `withRenotes=true` |

For a profile's Reposts tab, `users/notes` can return ordinary notes as well as renotes on some implementations. Return only rows where the mapper identified a pure renote and assigned `resharedBy == targetId`.

Correct the existing pure-renote detection in `MisskeyMapper.post` while implementing this feature. Today it treats a null-or-empty `text` as equivalent; that can flatten an explicit empty-text quote-renote into a pure repost. Determine whether text is *absent/null* using `json.has("text")` and `json.isNull("text")`, while retaining the existing checks for files, poll, and CW. An explicit quote-renote—whether or not its text is visually non-empty—must keep the target as `author`, retain its `quote`, and be excluded from Reposts.

Misskey and compatible servers may differ in whether `replyUserId` and an expanded `reply` are returned for `users/notes`. The mapper should read both without throwing; missing metadata causes the strict Replies filter to omit that row as described above.

## UI and navigation behavior

### Profile screen

Refactor `ProfileScreen` so the profile chips defined by `profile_ui_roadmap.md` are backed by paginated lists rather than the current placeholder. The first four chips—Posts, Media, Reposts, and Replies—have real feeds; Show more... remains a reserved placeholder. Do not nest a `LazyColumn` inside the current vertically scrollable `Column`; nested vertical scroll containers produce broken measurement, gesture, and end-of-list behavior.

The resulting screen should:

- retain the banner placeholder, avatar, display name, handle, biography, and self-only profile controls; do not restore the retired profile-fields dialog or banner action;
- display exactly `Posts`, `Media`, `Reposts`, and `Replies`, in that order;
- key `rememberSaveable` tab selection to `account.id.connection` and `account.id.localId`, so opening another profile does not leave it on the previous person's selected tab;
- request/refresh the selected tab in `LaunchedEffect(account.id, selectedTab)` and request the selected tab when the profile screen is re-entered, so self-posts made elsewhere do not remain indefinitely stale;
- render initial loading, pull-to-refresh, load-more spinner, error/retry/sign-in-again, a tab-specific empty state, and a terminal “You’re up to date” state;
- use stable `Post.id` keys and the same `PostRow` rendering as Home so media, CW, quotes, timestamps, hashtags, profile navigation, and action affordances remain consistent;
- pass the existing action callbacks (`favorite`, `reshare`, reactions, replies, bookmarks) and profile/hashtag callbacks through to rows exactly as Home does;
- expose a “Load older posts” button whenever a cursor exists, **including when the filtered list is empty**. This avoids a dead-end if a compatible server ignores filtering flags and the first raw page contains only excluded records.

A dedicated `ProfileTimelineList` composable should own `LazyListState`, pagination-near-the-end detection, pull-to-refresh, and row rendering. This limits `Screens.kt` to profile chrome and makes scrolling behavior testable without duplicating `HomeFeed`'s full home-specific top/bottom padding.

### App wiring

In `ConnectedApp`, create the new assisted ViewModel beside `FeedViewModel` using the active session and `sourceFactory.create(session)`. It is acceptable for it to use a separate short-lived `SocialSource` instance: adapter state is session-scoped, it carries the same account token, and the profile VM does not own background sync. Dispose it on session generation changes and invoke `stop()`.

Collect the profile timeline state lifecycle-aware and pass it, plus refresh/load-more callbacks, through `PalustrisApp` into `ProfileScreen`/`ProfileTimelineList`.

`PalustrisApp` remains the owner of `viewedProfile` navigation state:

- Selecting the Profile destination still clears `viewedProfile` and shows the self profile.
- Opening an author/search result still sets `viewedProfile` and switches to the Profile destination.
- In either case, use `displayedProfile.id` as the profile-timeline query target.
- When no account is signed in or no profile ID is available, retain a non-network placeholder/empty state and do not invoke the ViewModel.

Do not add `Protocol.MASTODON`/`Protocol.MISSKEY` branches to Compose or either ViewModel.

## Exact file-change manifest

The implementation should touch **only** the following existing files and add the listed new source/test files. No migration, storage, navigation-route, capability-model, or dependency files are necessary.

### Existing production files to modify

| File | Required change |
| --- | --- |
| `app/src/main/java/me/foxtails/palustris/domain/SocialModels.kt` | Add `ProfileTimelineTab` and `ProfileTimelineQuery`; add `SocialSource.profileTimeline(query, cursor)` with the shared unsupported default; add nullable `Post.replyToAuthorId` adjacent to `replyTo`. |
| `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt` | Implement the protocol-neutral profile timeline operation using the safe account-statuses request, existing Link pagination validation, the tabled query parameters, mapped-page filtering, deduplication behavior, and correct opaque cursor handling. |
| `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonMapper.kt` | Map Mastodon `in_reply_to_account_id` into `Post.replyToAuthorId`; retain the existing reblog-envelope mapping so `resharedBy` remains the pure-repost marker. |
| `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeySource.kt` | Implement `users/notes` profile queries for the four real feed categories; bind `userId`, `untilId`, and flags; locally apply the shared filters; derive the next cursor from the outer raw note ID; update `MisskeyMapper` in this file to map reply-parent author identity and correctly distinguish an explicit quote-renote from a pure renote. |
| `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt` | Add optional profile-timeline state and callbacks to the composable API, preserve defaults for existing previews/tests, and pass the displayed account plus state/callbacks into `ProfileScreen`. Do not move profile navigation ownership out of this file. |
| `app/src/main/java/me/foxtails/palustris/ui/Screens.kt` | Back the Posts, Media, Reposts, and Replies chips with `ProfileTimelineList`; keep Show more... as a UI-only placeholder; refactor the scroll hierarchy and retain the profile header without restoring the retired dialog; key selection to profile identity. |
| `app/src/main/java/me/foxtails/palustris/ui/SignInScreen.kt` | In `ConnectedApp`, construct, collect, dispose, and wire `ProfileTimelineViewModel` from the active session alongside the existing feed model. |

### New production files to add

| File | Required contents |
| --- | --- |
| `app/src/main/java/me/foxtails/palustris/ui/ProfileTimelineState.kt` | `ProfileTimelineState` and `ProfileTimelinePageState` immutable UI state classes. Keep them separate from `FeedState` so the account home feed remains narrowly scoped. |
| `app/src/main/java/me/foxtails/palustris/ui/ProfileTimelineViewModel.kt` | Assisted Hilt ViewModel and factory; state flow; target/tab keyed refresh/load-more jobs; cancellation and stale-result guards; errors via the existing UI helpers; and `stop()`. |
| `app/src/main/java/me/foxtails/palustris/ui/ProfileTimelineList.kt` | Profile-specific pull-to-refresh `LazyColumn` rendering, threshold pagination, empty/error/footer states, and `PostRow` reuse. It must avoid HomeFeed-only top padding and preserve a reachable manual load-more action when filtering produces an empty page with a cursor. |

### Existing test files to modify

| File | Required test coverage |
| --- | --- |
| `app/src/test/java/me/foxtails/palustris/MastodonIntegrationTest.kt` | Assert request path, percent-safe target ID handling, Bearer authentication, query parameters, Link cursor reuse, and mapped results for all four Mastodon profile tabs. Include root post, other-user reply, self-reply, media post, pure boost, and quote status fixtures. |
| `app/src/test/java/me/foxtails/palustris/MisskeyIntegrationTest.kt` | Assert `users/notes` body fields (`i`, `userId`, `limit`, `untilId`, and tab flags), next cursor from the raw outer note ID, reply-parent author mapping, pure renote recognition, quote-renote exclusion from Reposts, and local filtering of a server response containing mixed records. |
| `app/src/test/java/me/foxtails/palustris/HomeFeedTest.kt` | Update the existing profile-opening Compose test to expect the five chip labels in the roadmap order, verify header/biography behavior, and verify that Show more... does not restore the retired dialog. |

### New test files to add

| File | Required test coverage |
| --- | --- |
| `app/src/test/java/me/foxtails/palustris/ProfileTimelineViewModelTest.kt` | Fake `SocialSource` tests for self and other profile IDs, tab-to-query forwarding, refresh reset, cursor paging/deduplication, repeated-cursor stop, loading/error/401 state, cancellation, and ignoring stale results after profile/tab changes. |
| `app/src/test/java/me/foxtails/palustris/ProfileTimelineScreenTest.kt` | Compose tests for the five chips in the required order, real loading/empty/error/retry states for the first four categories, the reserved Show more... placeholder, pull-to-refresh and manual load-more callbacks, rows rendered through the shared post presentation, self vs. viewed profile targeting, and a media/repost result not being displayed in the wrong category. |

No changes are required in `MainActivity.kt`, `SourceFactory.kt`, `FeedViewModel.kt`, `FeedState.kt`, `HomeFeed.kt`, `Components.kt`, the account/session store, capability probes, Gradle files, or manifest files. The default `SocialSource` implementation keeps existing test fakes compiling; update a fake only when its new profile behavior is explicitly under test.

## Implementation sequence

1. **Write red tests first.** Add mapper/adapter fixture tests that encode the classification matrix, particularly self-replies, pure boosts, and quote-reposts. This prevents UI work from masking incorrect source semantics.
2. **Extend the domain contract.** Add the tab/query types, source method, and parent-author field with defaults. Compile to confirm existing mocks remain source-compatible.
3. **Implement mapping fidelity.** Add Mastodon `in_reply_to_account_id` support. Add Misskey `replyUserId`/expanded-reply support and repair the explicit-empty quote-renote distinction. Test mappings independently before paging.
4. **Implement Mastodon paging.** Add safe initial URL construction, Link continuation, server hints, and strict local filters. Verify raw boost envelopes are not confused with quote statuses.
5. **Implement Misskey paging.** Add the `users/notes` body/`untilId` flow and strict local filters. Verify that cursor selection always uses the outer note, not a flattened renote target.
6. **Create the profile ViewModel/state.** Implement independent per-tab pages and stale-result guards. Add unit tests for request sequencing before Compose wiring.
7. **Build the profile list UI.** Refactor `ProfileScreen` out of its nested scroll shape, connect the first four roadmap chips to the reusable lazy list with all loading/error/empty/page-ending behavior, and leave Show more... as a non-network placeholder.
8. **Wire the active-session composition.** Create/collect/dispose the new VM in `ConnectedApp`; pass state and callbacks through `PalustrisApp`; verify self and remote profile opening both use the same wiring.
9. **Complete Compose tests and manual validation.** Run both protocol fixture suites, then sign into a real Mastodon server and a real Misskey-family server if test credentials are available. Test a profile with a known quote, a known pure repost, a self-reply, an other-user reply, and a media reply.
10. **Run project verification.** At minimum run the focused test classes during development, then `./gradlew lintDebug` and `./gradlew test assembleRelease` before merging. Preserve unrelated worktree changes.

## Acceptance criteria

- The profile has exactly five chips in this order: Posts, Media, Reposts, Replies, Show more.... The former About category and conventional tab row are absent.
- The first four chips operate as real feeds for the signed-in profile and a profile opened from any supported entry point; Show more... renders inline details without a timeline request.
- Mastodon and Misskey adapters both use their native profile-status endpoint, keep their cursor semantics inside the adapter, and return only the requested category.
- Posts includes authored root posts and quote-reposts, but no replies or pure reposts.
- Replies contains only verified replies to another account; self-replies and unknown-parent replies are absent.
- Media contains only target-authored posts with attachments; a boost of someone else's media is absent.
- Reposts contains only pure boosts/renotes by the target; quote-reposts are absent.
- Pagination, retry, refresh, authentication failure, empty state, and rapid profile switching do not show stale or duplicated records.
- UI/ViewModel code remains protocol-agnostic and no account credentials are exposed to a foreign pagination origin.

## Warnings and implementation risks

> **Warning — API compatibility varies across federated implementations.** Mastodon-compatible servers and Misskey/Sharkey forks may ignore `exclude_reblogs`, `withRenotes`, `withFiles`, or return expanded objects differently. Treat all server-side flags as hints and keep the local classification filter mandatory. Validate against at least one real server of each family.

> **Warning — response filtering can create empty intermediate pages.** A server may return a full raw page that contains no eligible records. The implementation must retain its next cursor and expose a manual “Load older posts” control even if the visible list is empty; otherwise the user cannot advance to eligible older content. Do not silently discard a non-null cursor.

> **Warning — missing parent metadata makes a reply unverifiable.** Do not guess that a reply belongs to another user when `replyToAuthorId` is missing. Excluding it is the only behavior that reliably satisfies the requested no-self-replies rule, but it can make Replies appear incomplete on older or privacy-restricted servers.

> **Warning — quote versus repost is protocol-specific.** A Mastodon `reblog` envelope is a pure boost; a Mastodon quote extension is an authored status with a quote. On Misskey, do not use nonblank text alone to identify a quote-renote—an explicit empty text field can still represent an authored quote. A misclassification here will violate both Posts and Reposts requirements.

> **Warning — never construct a Mastodon profile-status URL by concatenating the account ID.** `AccountId.localId` is opaque. Use URL path/query builders for the initial request, and retain the existing same-origin pagination validation before sending Bearer credentials to a Link URL.

> **Warning — avoid nested vertical scrolling.** Embedding the new paginated `LazyColumn` below the existing `verticalScroll` profile `Column` is likely to cause unbounded-height measurement failures, inaccessible pagination, and competing fling gestures. The refactor must leave one vertical scroll owner.

> **Warning — guard against stale concurrent requests.** Switching profiles or tabs quickly can reorder responses. Cancel old work and check `(profileId, tab)` at publication time; cancellation alone is insufficient because a response can finish just before cancellation is observed.

> **Warning — seed data may be partial.** A profile opened from a feed uses the `Account` embedded in a post for immediate rendering, then `ProfileViewModel` requests authoritative details for the active target. If the refresh fails, the seed remains visible with a retry state; live server behavior still needs validation against real accounts.

## Implementation status

The profile milestone described by this roadmap is implemented in the current source tree. Profile domain contracts and classification live under `domain/`, protocol-specific detail/timeline/relationship services live under the Mastodon and Misskey adapters, and the dedicated state owner and single lazy scroll owner live under `ui/profile/`. The first four categories apply the shared classifier after mapping, preserve adapter-owned cursors, retain filtered-page continuation, and render through the shared `PostRow`; `Show more...` displays inline details.

Focused classifier, ViewModel, screen, source-contract, adapter, navigation, and home-feed tests pass. A debug APK has also been installed and launched on the connected device. Authenticated Mastodon/Misskey profile behavior, server-version compatibility, and the broader device matrix remain live-validation work because no test accounts were supplied.
