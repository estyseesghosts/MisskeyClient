# Beeline Codebase Decomposition Guide

## 1. Scope And Evidence

Verified on September 13, 2026, against commit `90f0708` and the current source worktree.
This guide replaces the earlier file-splitting plan.
It includes structural work, separate defect repairs, test improvements, and performance investigations.
It does not describe these changes as implemented.

The review read `AGENTS.md`, `importantdocs/writing_style.md`, and all four reports in `docs/decomp/results`.
The review checked their main claims against production source, callers, tests, Gradle configuration, and recent commits.
No application code or tests changed during this review.
No build, device test, live-server test, benchmark, or coverage report ran during this review.
Existing test methods establish intended coverage, not a new passing baseline.

Paths below are relative to `app/src/main/java/me/foxtails/palustris/`, unless another root is explicit.
Test names refer to `app/src/test/java/me/foxtails/palustris/`, including its child packages.
Line references identify the reviewed snapshot. Use the named symbol after lines move.
Existing internal identifiers remain exact technical references. Beeline is the product name.

### Recent Changes That Constrain This Work

| Commit | Current behavior that the guide must preserve |
| --- | --- |
| `90f0708` | System, System monochrome, and Palette are exclusive display modes. Selecting a swatch activates Palette. |
| `31c4e8b`, `bb183e3`, `e281051` | Display settings, fourteen palettes, settings Back behavior, persistence parsing, and theme tests already exist. |
| `e256032` | Settings and moderation are implemented. Global and account post preferences, content-warning rules, link cleanup, locale, refresh-rate policy, and profile images affect root wiring. |
| `df791fc`, `320c5e1`, `b1c650e` | Nullable interaction counts exist in models, mappers, optimistic state, notification JSON, and shared post presentation. |
| `d6ebf1f`, `2b6eada` | Thread contracts, bounded adapters, tree construction, and thread presentation already exist. |

The worktree already contained changes to `README.md` and `logs/DONE.txt`, plus untracked local files.
Do not overwrite those changes.
Commit `40c442a` ignores the root `docs/` directory and `AGENTS.md`.
An ordinary `git diff` therefore does not show this guide. Review the actual file before any later documentation commit.
Do not change ignore rules or force-add documentation without a specific reason and approval.

### Corrections To The Earlier Plan And Reports

| Earlier claim or instruction | Verified correction |
| --- | --- |
| `Screens.kt` contains four independent areas. | It contains three feature areas: Search, composer, and drafts. `Audience.label()` belongs to composer. |
| Move the theme into, or retain it in, the app root. | `ConnectedApp` applies `PalustrisTheme(preferences = ...)`. `PalustrisApp` no longer owns that theme wrapper. |
| Extract only the authenticated branch's ViewModel creation. | Most account ViewModels are currently created before `AnimatedContent`, not inside its authenticated branch. Moving creation changes lifetime. |
| Combine `PostActionBubbleHost` and `EmojiPickerHost` in one root popup call. | They occupy different composition positions, with modal surfaces between them. One call would reorder them. |
| Composer state is text, warning, quote, and reply only. | Audience, saved audience, draft error, explicit visibility validation, and tracking cleanup are also part of the contract. |
| Create `MediaViewerChrome.kt`. | That file already exists, as do `MediaPage.kt`, `MediaViewerTransitionState.kt`, and other media owners. |
| Create a new UI reducer for all reaction behavior. | `domain/PostReactionReducer.kt` already serves Feed, Profile, Saved Posts, and Thread. Do not duplicate it. |
| Create a new thread tree reducer. | `domain/ThreadTreeBuilder.kt` already constructs the tree. Extract storage and state assembly only if needed. |
| Move `PhotoGridFeedState` out of `FeedViewModel`. | It already lives in `ui/PhotoGridFeedState.kt`. Jobs and orchestration remain in the ViewModel. |
| Move Mastodon `getPage` into a timeline-only service. | Saved Posts, Likes, and hashtag search also call it. Keep one protocol-local paging owner. |
| All declarations below `NotificationRepository` are JSON helpers. | `Notification.matches`, `stableNotificationId`, and account filename hashing are not JSON behavior. Classify each caller. |
| Malformed delivery entries fail the entire notification decode. | Delivery entries are individually guarded. The singular top-level checkpoint and push registration are not. |
| Notification persistence can write the transform's captured state. | It deliberately writes the latest flow value under the monitor. Writing the captured value can restore older state. |
| Drafts has saveable deletion state. | `DraftsScreen` uses ordinary `remember` for pending deletion and confirmation. Do not claim restoration that does not exist. |
| Existing Back helpers define root policy. | `AppBackHandler` and `BackNavigationState` have no production caller found. Root Back uses a local policy. |

The exploration reports remain useful maps. Their proposed file names and claims are not requirements.
This guide resolves their conflicting extraction orders and removes mandatory duplicate owners.

## 2. Working Rules

### Separate Structural Work From Repairs

Structural slices preserve behavior. Repair slices intentionally change a documented behavior and add regression tests.
Do not freeze a known defect as a permanent characterization requirement.
Do not hide a repair inside a file move.

Use one coherent slice per implementation commit.
Each slice must include its required tests, imports, call-site changes, and any approved migration.
Do not leave empty wrappers, duplicate implementations, or two writable owners of the same state.
Do not combine all source services, all reducers, or all modal surfaces in one commit.
The slice order in Section 5 is the execution order. Earlier priority rankings no longer compete with it.

Keep existing APIs during an initial move where this limits risk.
Update internal imports together. Do not add compatibility wrappers for callers in this single module.
Widen `private` to `internal` only when the new boundary requires it.
Do not perform unrelated formatting, package renames, or wildcard-import cleanup.

### Keep Ownership Explicit

| Owner | Responsibility that must remain clear |
| --- | --- |
| `AccountManager` | Account index, authentication, session replacement, and sign-out coordination. |
| `AccountSourceRegistry`, `SocialSourceFactory` | Session-bound source selection and construction. |
| `ConnectedApp` | Application/session composition, preferences, lifecycle wiring, and settings host placement. |
| `PalustrisApp` | Destination and modal coordination during the structural stage. |
| `FeedViewModel` | Home feed and public account-feed API during controller extraction. |
| `ProfileViewModel` | One viewed-profile session and its generation. |
| `PostThreadViewModel` | Thread lifecycle, acquisition jobs, and action jobs. |
| `NotificationRepository` | Atomic notification merge, generation validation, publication, and durable state changes. |
| Preference repositories | Durable global or account-specific preferences. |
| Protocol services | Requests, protocol JSON mapping, and protocol-local pagination behind shared contracts. |

A controller under an existing ViewModel is an intermediate structural boundary, not a new global owner.
Use a parent scope or an explicitly canceled child scope. Do not create an unmanaged scope per operation.
A later feature ViewModel is valid when it improves lifetime ownership and has a separate wiring slice.

### Preserve State And Protocol Contracts

- Keep enum strings, draft identities, overlay constants, and destination provider keys compatible.
- Keep `remember`, `rememberSaveable`, effects, and handlers at their current call sites during presentation extraction.
- Remember that compiler-generated saveable identities depend on composition structure. Matching source text does not prove restoration safety.
- Keep `screenStates.SaveableStateProvider(animatedDestination.name)` and its holder in the root initially.
- Keep the account key around the app content and all account/session ViewModel keys unchanged initially.
- Preserve compact content under floating controls. Reserve obstruction clearance inside scroll content.
- Preserve wide pane ratios, hinge policy, IME positioning, system bars, and reduced-motion behavior.
- Keep Misskey and Mastodon JSON below the shared domain/UI boundary.
- Keep `SourceError` normalization, capability refresh, limits, request order, and cancellation behavior unchanged during moves.
- Keep opaque cursors unchanged. Do not compare identifiers to infer time or rebuild server pagination URLs in UI.
- Keep nullable counts unknown when unavailable. Keep favourites, reactions, reposts, and quotes distinct.
- Preserve stored notification keys, enum values, defaults, legacy fallbacks, and version `2` during codec extraction.
- Keep session secrets and push keys in their existing encrypted session storage. Do not move them into notification JSON.

## 3. Findings And Repair Register

These findings come from source inspection. A repair must first add a deterministic failing test.
They are not claims that a failure occurred on a live server or physical device during this review.
Fix urgent correctness issues before extracting their affected boundary.
Independent move-only slices can proceed without waiting for unrelated investigations.

### R1. Settings Writes Lack A Defined Job And Failure Owner

Evidence: `ui/SignInScreen.kt:485-503`; `ui/settings/SettingsViewModel.kt:20-40`.

The active settings callbacks create new `CoroutineScope(Dispatchers.Main.immediate)` instances.
They do not retain jobs, define cancellation, or catch repository write failures.
An exception from a root `launch` can reach uncaught exception handling.
The existing `SettingsViewModel` is not wired into `ConnectedApp` and lacks some current setters, including palette selection.
Its `runCatching` also suppresses failures rather than publishing useful error state.

Repair slice: choose one settings command owner and wire all active commands through it.
Prefer completing the existing ViewModel if its lifetime fits. Otherwise remove it after verifying no consumers remain.
Keep repository state canonical. Do not collect a second independent preference copy.
Bind account commands to a captured account and current session policy.
Use transforms against current repository state rather than replacing it with an old UI snapshot.

Tests: failed disk write, rapid independent setting changes, account change during a suspended update, sign-out, and settings used from sign-in.
Preserve the exclusive theme behavior from `90f0708`.

### R2. Post Preference Storage Blocks Its Caller And Differs From Its Fake

Evidence: `data/preferences/PostPreferencesRepository.kt:25-49,72-108,111-113,132-145`.

`FilePostPreferencesRepository.update` performs encoding, file writes, `fd.sync()`, and moves without an IO dispatcher boundary.
Current settings callbacks invoke it on Main. The blocking path is confirmed; its device latency is not measured.
The constructor also loads the file synchronously.
The file implementation normalizes only `favouriteEmoji` during updates.
The in-memory implementation also normalizes content-warning rules and muted hashtags.
Tests using only the fake can therefore pass while production stores different values.

Repair slice: use one canonical normalization function in both implementations.
Move blocking storage work to an injected IO dispatcher without changing the persisted format.
Specify load readiness if construction becomes asynchronous. Do not silently publish defaults over a pending load.

Tests: real-file normalization, reload, account isolation, removal, failed persistence, and updates waiting for initial load.
Use identical input cases against file and in-memory implementations.

### R3. Global Preference Load Errors Disappear

Evidence: `data/preferences/FileAppPreferencesRepository.kt:46-52`.

The load path publishes `AppPreferencesState(error = ...)`, then immediately replaces it with a successful-looking default state.
A malformed preference file can therefore lose its persistent error indication.

Repair slice: publish one final loaded state with either loaded values or fallback values plus an error.
Define recovery on the next successful update. Keep legacy theme parsing unchanged.
Tests: malformed JSON, read failure, missing file, error visibility after load completes, and successful recovery.

### R4. Moderation Paging Can Publish Stale Results

Evidence: `ui/settings/ModerationViewModel.kt:55-110,113-161,177-178`.

`load()` has a request generation. `loadMore()` has neither that guard nor a retained paging job.
`ensureCurrent()` runs before the request, not after it.
A refresh can replace the first page while an old paging response later appends rows and restores its old cursor.
Removal and local hashtag operations have similar post-suspension session checks to review.
Paging appends without deduplication or consumed-cursor protection.

Repair slice: bind load, paging, and mutation publication to the current account/source and request generation.
Cancel or invalidate old paging on refresh. Check again after suspension.
Deduplicate by the correct domain identity. Stop repeated cursors without sorting opaque identifiers.
Retain the last valid list and a useful retry state after temporary failure.

Tests: delayed page after refresh, replacement source, account removal, duplicate page, cursor cycle, failed removal, and local hashtag fallback.
The three adapter tests in `ModerationServiceTest` do not cover these ViewModel races.

### R5. Notification JSON Loses Unknown Activity Destinations

Evidence: `data/notifications/NotificationRepository.kt:1142-1182`.

`encodeActivity` writes an unknown activity's server destination.
`decodeActivity` constructs `Unknown` without that destination.
The nested activity therefore loses data across persistence.
The notification's separate top-level destination can still survive. Do not claim all notification navigation is broken.

Repair slice: decode the optional nested destination through validated URL/domain types.
Keep old JSON without that field readable.
Tests: unknown activity with a valid destination, missing destination, invalid URL, and complete notification round trip.
Commit this repair separately from codec movement.

### R6. Notification Checkpoint Ownership Is Inconsistent

Evidence: `data/notifications/NotificationRepository.kt:175-200,251-306`.

The code selects the query from the raw checkpoint before validating its account.
It later rejects that checkpoint for one argument, but `queryAccountId` still reads the raw page checkpoint.
With a previous matching query, a foreign checkpoint can supply the next stored checkpoint's account.
Without a previous matching query, `mergeCheckpoint` can throw because its accepted query is missing.
The legacy page path also lacks the query-aware path's entity-origin filter.

Repair slice: validate page ownership before any merge or query selection.
Pass the accepted account/query explicitly into checkpoint merging.
Define rejection without partial publication or checkpoint changes.
Tests: foreign checkpoint with and without previous state, foreign entity origin, empty page, filtered query, and valid legacy page.
Do not label this a demonstrated token leak. It is a repository integrity defect.

### R7. Concurrent Post Actions Can Undo Each Other

Evidence: `ui/FeedViewModel.kt:552-575`; `ui/thread/PostThreadViewModel.kt:453-471`.

Feed keys actions by action and displayed post ID, then rolls back the entire captured post on failure.
A failed favourite can therefore overwrite a successful concurrent repost or bookmark on the same post.
Reaction-backed favourites and emoji reactions also share fields but have separate Feed action keys.
Thread groups favourite/reaction jobs together, but its rollback restores the whole count object for several action types.
A failed favourite can therefore restore an older repost count while retaining the new repost flag.

Repair slice: define action-family serialization and field-specific rollback/reconciliation.
Use the effective target where wrappers identify the same actionable post.
Preserve unrelated action fields and newly received counts.
Do not introduce a universal mutation manager before establishing these semantics.

Tests: favourite failure after repost success, reaction/favourite overlap, two wrappers for one target, stale session input, and server response with unknown counts.
Extend Saved Posts and Profile tests where their mutation behavior has the same meaning.

### R8. Thread Reaction Overlays Restore An Incomplete Reaction State

Evidence: `ui/thread/PostThreadViewModel.kt:453-471,530-575`.

`ReactionState` stores `myReaction`, but `MutationOverlay.applyTo` does not use that stored value.
A replacement context can restore selected reactions while retaining a stale server `myReaction` value.
Reaction-backed favourite rollback restores `myReaction` and counts, but not the reaction list or selected reactions.

Repair slice: define one complete reaction-state transform, including explicit clearing of nullable fields.
Use the existing domain reaction reducer. Keep acquisition and action generation separate.
Tests: confirmed add/remove across stale refresh, failed reaction-backed favourite, empty reaction list, and single/independent modes.
The existing confirmed-favourite test is not sufficient coverage for these cases.

### R9. Misskey Parent Failures Do Not Always Become Partial Context

Evidence: `data/misskey/MisskeySource.kt:219-227,305-315,638-652`.

Ancestor loading catches `SourceError`, but `loadThreadPost` calls the API directly.
An HTTP `ApiFailure` reaches the outer request wrapper only after it escapes ancestor traversal.
A missing parent can therefore fail the whole request instead of producing `UnavailableParent`.
The report's parent-failure description applies only to errors already represented as `SourceError`.

Repair slice: normalize the ancestor request failure at the correct local boundary.
Preserve focal-post failure and cancellation behavior.
Do not silently convert authentication, resource-limit, and network failures to the same limitation without an explicit policy.
Tests: focal 404, parent 404, parent denial, cancellation, and usable descendants after an unavailable parent.

### R10. A Temporary Mastodon Reaction Failure Disables The Capability

Evidence: `data/mastodon/MastodonSource.kt:221-262`.

Both reaction methods downgrade mutation support after any non-cancellation exception.
A network error, rate limit, or server failure can mark a supported feature unsupported for the current capability state.

Repair slice: distinguish unsupported endpoint evidence from access denial and temporary failure.
Keep capability ownership in the facade. Do not make the extracted mutation service own a second capability cache.
Tests: timeout, 429, 500, denied access, confirmed unsupported endpoint, cancellation, and later successful retry.

### R11. Home Automatic Paging Uses The Unfiltered Row Count

Evidence: `ui/HomeFeed.kt:164-169,222-231,266-269`.

The paging threshold uses `state.posts.size`, but the list renders rows after muted-hashtag filtering.
Enough hidden rows prevent the visible last index from reaching the threshold.
Manual Load Older still exists. This is an automatic paging defect, not proof of an inaccessible feed.

Repair slice: derive rendered rows and paging position from one policy.
Handle an all-hidden page and retain a bounded manual continuation path.
Do not repeatedly fetch unbounded hidden pages to fill the viewport.
Tests: mixed visible/hidden rows, all-hidden pages, duplicate cursor, empty server page, and reachable manual continuation.

### R12. Complete The Product Identity Migration

Evidence: `app/build.gradle.kts:4,34-36` still derives the application label and build identity from the stale product value.

Use Beeline for labels, registration names, User-Agent values, documentation, and release output.
Audit consumers of `ProductIdentity` and generated build constants.
Do not rename the package, application ID, stored filename keys, or callback schemes as incidental cleanup.
Tests: generated label/build identity and authentication registration values.
Verify an installed upgrade separately from unit tests if compatibility identifiers change in a later dedicated migration.

## 4. Investigations And Performance Work

These items have source evidence but need measurement or a policy decision before implementation.
File decomposition alone does not make rendering, storage, or builds faster.

### P1. Notification Storage Work And Retention

Evidence: `NotificationRepository.persistIfCurrent`, `stateForLocked`, `updateDeliveryOutbox`, and `NotificationStore.kt:52-73`.

Room stores the complete state as JSON. Each persistent mutation rewrites that account's blob.
Repository persistence holds a single monitor during store writes.
Lazy `observe()` can read storage while holding the same monitor.
`RoomNotificationStore` uses `runBlocking(Dispatchers.IO)`, which still blocks the calling thread until completion.
Main-thread callers and other accounts can wait behind that work.

The 500-item limit applies to visible notifications, not the delivery map or dismissal tombstones.
Finished delivery records and tombstones can grow until account removal.
Measure blob size, encode time, store latency, lock wait, cold observation, and retained entries over a long synthetic history.
Do not log private notification content while measuring.

A later persistence redesign needs per-account ordered writes, removal barriers, stale-generation rejection, and durable delivery claims.
Do not move `store.write` outside the lock and assume correctness remains unchanged.
Do not prune tombstones or finished deliveries without defining duplicate/redelivery behavior after pruning.

Also define corrupt-store recovery separately from codec movement.
The legacy file store catches decode errors and returns no state. The Room store lets decode errors escape.
A malformed singular checkpoint or push registration can therefore fail a Room-backed observation.
Do not silently discard all settings, tombstones, and delivery history to keep valid notification rows.
Test section-level corruption and the chosen recovery behavior before changing this policy.

### P2. Room Schema Debt

Evidence: `data/notifications/db/NotificationDatabase.kt:6-20` and `NotificationDao.kt:10-44`.

The database declares ten entities at version `2`, with `exportSchema = false`.
Only `notification_state` has active read/write DAO methods. Other tables have deletion methods.
The normalized tables are not a second active source of truth.
Their fields do not fully represent the current JSON state, including durable claim and push fields.

Choose removal of unused schema or a separately designed normalized store. Do not maintain both as independent writers.
Any schema change requires a versioned migration and real database migration tests.
Establish reproducible schema history before relying on generated migration validation.
Keep this work separate from the codec extraction.

### P3. Thread Acquisition Bounds Need Accurate Claims

Misskey has per-acquisition node/request limits, but its continuation map has no eviction or expiry policy.
Abandoned threads can retain bounded acquisitions in an unbounded number of map entries for the source lifetime.
Measure repeated open/abandon behavior before selecting a cap and expiry policy.

The fifteen-second batch check runs between descendant requests. It is not a wall-clock timeout for the entire thread operation.
Ancestor work runs before that timer, and an in-flight request can exceed the remaining time.
The four-megabyte limit is per response, not a total acquisition byte budget.
Depth-ten children are not queued further, so deeper replies can remain undiscovered without the depth-limit branch executing.
Test that case before describing a result as complete.

Treat timeout, continuation retention, and completeness changes as separate behavioral slices.
Keep cancellation distinct from branch failure.

### P4. Rendering And State Assembly

`latestSelectedPost()` in `PalustrisApp.kt:984-998` concatenates multiple collections, including overlapping feed inputs.
Thread `rebuildState()` repeatedly constructs ancestor ID lists inside filters.
Home filtering parses hashtags across loaded rows; shared post rendering also performs text presentation work.

Measure allocations and frame time with realistic long feeds, emoji, hidden posts, and wide details.
Prefer direct lookup or one derived ID set where semantics are unchanged.
Do not introduce a global post cache only to avoid a small bounded scan.
Do not add Compose memoization with incomplete keys for preferences, emoji, or account changes.
Test visible output after each measured optimization.

### P5. Remaining Lifecycle And Origin Checks

Review these cases with controlled suspended fakes before changing ownership:

- Photo Grid hashtag saving uses an unretained job. `FeedViewModel.stop()` cancels grid loading and preference observation, not that save job.
- Home and Search rely more on cancellation than Photo Grid's generation guard. Test sources that return after cancellation.
- Feed mutation entry points check account identity but not session revision. Thread checks both.
- Profile and Saved Posts use different session-revision conventions. Define the migration across all `OwnedPost` producers before adding blanket rejection.
- Root draft text is saveable, but reply/quote targets use ordinary `remember` and are cleared by the account effect. Test unsaved reply restoration explicitly.
- Settings visibility is saveable, but `settingsRoute` is not. Recreation currently can reopen the main settings page rather than the prior subpage.
- `MisskeySource.post` lacks the explicit entity-origin validation used by thread loading. Mastodon quote creation validates reply origin but not quote origin.
- `parseRefreshHint` multiplies server-provided seconds by 1,000 without an overflow check. Define accepted delay bounds and test extreme headers.
- Push message and registration paths catch broad exceptions. Review cancellation separately from best-effort sign-out cleanup.
- Generation-zero notification callbacks support restart. Test delayed persistence and removal for that token, not only a positive active generation.

These are targeted review tasks, not permission to add a new account architecture or shared protocol service.
Promote a case to a repair slice only after its expected behavior and regression test are clear.

### P6. Localization And Unused Owners

Root selection sheets still render string state and English timeline descriptions directly.
Emoji grouping also embeds English labels in pure grouping output.
Keep stored string identities separate from translated display labels in a later localization slice.
Use locale and large-font layout tests. Do not rename saved sheet keys while translating them.

`SettingsViewModel`, `AppBackHandler`, and `BackNavigationState` are not active owners in the reviewed wiring.
Wire an existing owner only when it fits the required behavior, or remove it after a whole-repository usage check.
Do not preserve unused scaffolding merely because the earlier plan expected it.
Do not replace the explicit root Back order with an untested helper.

## 5. Slice Order

The table defines one sequence. Each row is one coherent implementation slice, not one entire feature rewrite.
Required repairs run before extraction of their affected behavior.
The whole-function move in S05 is an exception: S06 repairs its command ownership before any deeper wiring split.
Independent repairs can interrupt the sequence when correctness requires it.
Investigation rows end with a recorded decision, not an automatic architecture change.

| Slice | Purpose | Dependency | Verification focus |
| --- | --- | --- | --- |
| S00 | Establish the current baseline and add missing boundary tests for the next slice. | None | Section 8 commands and failure log. |
| S01 | Move compact overlay metrics. | S00 | Compact, wide, IME, and underlap tests. |
| S02 | Move Search composition, controls, and result lists out of `Screens.kt`. | S01 | Navigation/Search and result actions. |
| S03 | Move composer UI and `Audience.label()`. | S00 | Reply, visibility, selection, CW emoji insertion. |
| S04 | Move drafts UI and delete the now-empty `Screens.kt`. | S02, S03 | Save, delete confirmation, recreation. |
| S05 | Move `ConnectedApp` and its wiring-only helpers out of `SignInScreen.kt`. | S00 | Startup, sign-in, account lifecycle, settings, theme. |
| S06 | Repair settings command ownership. | S05; R1 | Failures, concurrent transforms, sign-in settings. |
| S07 | Repair file/fake post preferences and IO dispatch. | R2 | File persistence, normalization, account removal. |
| S08 | Repair global preference load error state. | R3 | Corrupt file, readiness, recovery. |
| S09 | Repair moderation request/session ownership. | R4 | Delayed paging, refresh, removal, replacement session. |
| S10 | Move the shared account avatar. | S00 | Profile, feed, notifications, DM, large navigation. |
| S11 | Move the shared post presentation family from Home. | S10 | Feed/detail modes, CW, counts, actions, semantics. |
| S12 | Repair filtered Home paging. | S11; R11 | Hidden-page and cursor behavior. |
| S13 | Move compact navigation presentation. | S01, S10 | Destination actions, long press, selected indicator. |
| S14 | Move explicit destination rendering. | S02-S04, S11, S13 | Restoration, compact/wide route matrix. |
| S15 | Move the wide detail assembly. | S14 | Post-origin dispatch and thread actions. |
| S16 | Move account/timeline selection sheet rendering. | S13 | Switch/add account, settings, sign-out, timeline change. |
| S17 | Move composer sheet rendering only. | S03 | Save/close/publish and nested emoji picker. |
| S18 | Move profile editor sheet rendering and profile patch calculation. | S00 | Dirty editor, Back, unchanged fields. |
| S19 | Move notification settings sheet rendering. | S00 | Root sheet and separate global settings host. |
| S20 | Move simple app dialogs. | S16, S18 | Dialog precedence and cancel/confirm behavior. |
| S21 | Move emoji grouping/catalog support. | S00 | Existing ordering, pin/collapse/search tests. |
| S22 | Move emoji scrollbar math only if it improves the boundary. | S21 | Existing scrollbar unit tests. |
| S23 | Repair notification unknown-activity persistence. | R5 | Compatibility and nested destination tests. |
| S24 | Move notification state models and JSON codec. | S23 | Stored fixtures, Room round trip, interaction counts. |
| S25 | Repair page/checkpoint ownership. | S24; R6 | Rejection without publication or persistence. |
| S26 | Extract notification page/checkpoint merge calculations. | S25 | Directional cursor and baseline invariants. |
| S27 | Extract notification read-state transforms. | S26 | Seen, acknowledgement, dismissal, stream read state. |
| S28 | Extract delivery transforms. | S26 | Claims, lease recovery, stale finish, removal race. |
| S29 | Repair Misskey ancestor failure normalization. | R9 | Partial context versus focal failure. |
| S30 | Extract Misskey thread acquisition service. | S29 | Requests, limits, cancellation, continuation ownership. |
| S31 | Extract Mastodon thread service. | S00 | Focal/context, refresh hints, origin and session checks. |
| S32 | Extract the shared Mastodon page-request helper. | S00 | Timeline, bookmarks, likes, hashtag cursors and security. |
| S33 | Extract Mastodon timeline service if the facade still benefits. | S32 | Source contracts and exact requests. |
| S34 | Extract Misskey timeline request/mapping if justified. | S30 | Raw renote cursor, Bubble and capability behavior. |
| S35 | Repair Mastodon reaction capability downgrade. | R10 | Temporary failure versus unsupported evidence. |
| S36 | Repair concurrent action rollback and reconciliation. | R7 | Cross-action interleavings and effective target identity. |
| S37 | Repair thread reaction overlays. | R8, S36 | Stale refresh, add/remove, reaction-backed favourite. |
| S38 | Extract Photo Grid controller. | S36; resolve P5 save lifetime | Independent state, preferences, stop, external updates. |
| S39 | Extract Search controller only if state publication remains simple. | S38 | Query replacement, paging, action propagation. |
| S40 | Extract remaining shared pure action transforms only where equivalent. | S36, S37 | Domain tests plus one wiring test per owner. |
| S41 | Move push interface and no-op implementation. | S24 | Construction and caller compilation. |
| S42 | Extract push message handling. | S27, S28, S41 | Callback ownership, decode failure, catch-up, delivery. |
| S43 | Extract push registration orchestration under the existing mutex. | S42 | Endpoint replacement, retry, disable, stale callbacks. |
| S44 | Move profile header/details presentation. | S10, S11 | Identity, fields, links, image viewer, layout. |
| S45 | Move wide profile presentation. | S44 | Wide ratios, docks, tabs, pinned rows. |
| S46 | Extract profile timeline pager. | S00 | Generation, per-tab paging, empty pages, failures. |
| S47 | Extract profile editor session only if still substantial. | S46 | Existing editor cancellation and stale-result tests. |
| S48 | Extract thread post storage and overlays. | S37 | Replacement/continuation, local replies, tree identity. |
| S49 | Extract pure thread refresh policy only if it clarifies ownership. | S48 | Foreground, hint bounds, one-shot refresh. |
| S50 | Split post-action bubble placement and presentation. | S21 | Popup lifetime, replacement, Back, gesture thresholds. |
| S51 | Extract media transition rendering and geometry. | S00 | Canvas, clipping, ownership, frame handoff. |
| S52 | Extract media dismiss gesture only after event tests exist. | S51 | Zoom, pager, multitouch, cancellation, velocity. |
| S53 | Evaluate connected-session wiring and small root state owners. | S06, S14-S20, S38 | Account replacement, restoration, settings layering. |
| S54 | Complete user-facing Beeline identity. | R12 | Metadata, registration, label, unchanged compatibility IDs. |
| S55 | Measure storage/rendering/test costs and decide follow-up slices. | Relevant stable boundaries | P1-P4 and Section 8. |
| S56 | Remove the unused arithmetic template test. | S00 | Test discovery and full completion gate. |
| S57 | Replace fixed media-test sleeps with bounded readiness controls. | S00 | Repeated runs, request counts, cache and image handoff assertions. |
| S58 | Make navigation screenshot output opt-in or failure-only. | S00 | Keep all pixel/bounds assertions and verify diagnostic capture. |
| S59 | Localize account/timeline sheet labels without changing saved identities. | S16; P6 | Long translations, font scale, sheet restoration. |
| S60 | Separate emoji group label rendering from pure group identity. | S21; P6 | Existing group order, translated labels, pin/collapse identity. |

S11 moves a single shared post presentation family. If its diff is too dense, divide it by body, metadata, and interactions.
S44 and S50 can likewise use one sub-slice per independent visual responsibility.
Do not reduce verification merely to retain the table's numbering.
Complete and verify each sub-slice before starting the next.
S56-S58 can run earlier after the baseline. They do not depend on the application decomposition.
S55 must create separate follow-up slices for any approved storage, retention, timeout, or schema change.
Do not treat an investigation decision as completed remediation.

## 6. Detailed Extraction Contracts

### 6.1 Compact Metrics And Navigation

Source: the declarations before `PalustrisApp()` in `ui/PalustrisApp.kt`.
Target: `ui/layout/CompactOverlayMetrics.kt` and `ui/navigation/CompactAppNavigation.kt`.

Move the following layout declarations without changing calculations:

- `CompactNavigationHeight`, `CompactTimelineSelectorWidth`, and `CompactTimelineSelectorHeight`.
- `CompactOverlayControlSpacing`, `CompactOverlayHorizontalPadding`, and `CompactOverlayVerticalPadding`.
- `CompactSearchChipRowHeight`, `CompactSearchControlsSpacing`, and `CompactSearchFieldHeight`.
- `CompactFilterDockHeight`, `CompactSearchDockHeight`, and `CompactContextualControlsPositioningClearance`.
- `LegacyFeedBottomClearance`.
- `compactGlobalNavigationPositioningInsets`, `compactContextualControlsPositioningInsets`, `compactScrollEndClearance`, and `compactHomeScrollEndClearance`.

Move `ContextualBottomAction`, `roundPressLayer`, `contextualActionFor`, `TimelineSelector`, and `CompactContextualNavigationBar` as presentation.
The root still selects destination, panel, timeline, visibility, and callbacks.
Keep `timelineDisplayOrder`, Bubble availability, profile long press, and notification/DM contextual actions.
Do not introduce a navigation ViewModel for this move.
The existing `ui/large/` package already owns the large shell and rail.

### 6.2 Search, Composer, And Drafts

Source: `ui/Screens.kt:64-727`.
Targets: `ui/search/SearchScreen.kt`, `SearchControls.kt`, `SearchResults.kt`, `ui/composer/ComposeScreen.kt`, and `DraftsScreen.kt`.

Search composition owns compact/wide presentation, shared/local query selection, category selection, list state, and clearance.
Move `SearchScreen` and `SearchContent` together first.
Move `SearchField` and result lists when that gives clear local ownership rather than one-function wrapper files.
Keep Search networking in Feed until S39.
Preserve current explicit submission behavior. Do not add debounce or search-as-you-type during extraction.

Composer must retain body/CW selection, `TextFieldValue` construction, emoji insertion acknowledgement, audience controls, quote removal, and tracking cleanup.
Move `Audience.label()` with it.
Preserve public callback defaults during the first move.
Do not turn audience display into protocol-specific UI.

Drafts owns list rendering and ordinary remembered delete confirmation.
It does not own durable storage. Keep stable draft item keys.
Delete `Screens.kt` only after all three features and their callers have moved.

### 6.3 Connected Application Wiring

Source: `ui/SignInScreen.kt:73-510`.
Initial target: `ui/ConnectedApp.kt`.

Move `ConnectedApp`, `settingsViewModelUpdate`, and `ModerationKind.toModerationListKind` together without redesigning them.
Repair the temporary settings helper in S06, not in the file move.
Leave suggested instances and sign-in-specific UI in `SignInScreen.kt`.

Preserve the current account/session keys for Feed, Notifications, DM, Saved Posts, Likes, Profile, Thread, and Emoji Catalog.
Also preserve settings-selected account keys for moderation and notification settings.
These accounts can differ from the active feed account.

Keep the following positions and lifetimes initially:

- Active-session/source selection before root rendering.
- Notification stream lifecycle effect and thread foreground lifecycle effect.
- Disposal calls to existing ViewModel `stop()` methods.
- Thread update propagation into Feed, Saved Posts, Likes, and Profile.
- Browser authorization and notification-launch effects.
- Preference readiness gating startup.
- Theme and content-warning/muted-hashtag composition providers.
- `SettingsHost` after the root `AnimatedContent`, outside the authenticated-only branch.

S53 must not move ViewModel creation under `AnimatedContent` merely because a new file is called `ConnectedSessionContent`.
That can duplicate collectors during transitions or change when jobs stop.
Keep source and account ownership outside reusable presentation.
Test settings opened from sign-in, notification launch during account switch, and session replacement for the same account.

### 6.4 Root Destination And Modal Extraction

Source: `ui/PalustrisApp.kt:504-1718`.
Targets: focused files under `ui/app/`, plus composer/profile/notification feature sheets.

`AppDestinationContent.kt` can receive a large explicit parameter list initially.
Move the destination and local-page branches, not root state or account storage.
Keep Home, Search, Photo Grid, Notifications, DM, Profile, saved/liked collections, drafts, and notification detail explicit.
Do not create a screen registry, generic configuration model, or navigation framework.
Do not hide all routing and every modal in a new `AppContent.kt`.

`AppLargeDetailPane.kt` receives the selected post, origin, available actions, thread state, and callbacks.
Keep origin-specific action dispatch, quote support, profile selection interaction, and Photo Grid detail clearing.
Keep `singlePost`, `singlePostOrigin`, and the thread activation effect in the root during extraction.
Characterize the compact/wide difference: wide lookup uses `latestSelectedPost()`, while compact detail receives `singlePost` directly.
Do not silently unify those refresh semantics in a move.

`AppSelectionSheets.kt` renders the existing account/timeline sheet.
Keep the saved values `"Timelines"` and `"Accounts"` unchanged.
State controls account switching, add-account, settings opening, sign-out, and timeline refresh.
Translate its labels later without changing those stored values.

`ComposerSheet.kt` receives state and callbacks. It does not read or write `DraftStore`.
Keep all draft fields, dirty-state calculation, save/publish jobs, and target reconstruction in the current root owner initially.
This includes `composerAudience`, `savedAudience`, `draftError`, reply/quote fields, and `pendingEmojiInsertion`.
Keep audience validation, save-before-publish, delete-after-success, and publish failure recovery in root callbacks.
S53 can later introduce a dedicated composer session owner with explicit account binding and restoration rules.

`EditProfileSheet.kt` wraps the existing `EditProfileScreen`.
Move `editableProfilePatch()` from `AppShellState.kt` into the profile package with its characterization tests.
Do not change editor dirty-state comparison or callback timing.

`NotificationSettingsSheet.kt` wraps the existing notification settings screen.
It is not a replacement for the separate account-selected settings route in `SettingsHost`.
`AppDialogs.kt` can contain the two simple discard-profile and sign-out declarations.

#### Root Back And Surface Order

Keep `dismissTopSurface()` and edge-swipe routing in the root initially.
Its current priority is profile image, wide settings/composer/editor, selected post, compact settings/composer/editor, notification route, local page, then Home.
The media viewer owns its own Back handling while open.
Sheets, dialogs, the settings host, and nonembedded post detail also participate in Back dispatch.
Test actual handlers, not only a pure list of route priorities.

Preserve this root composition order:

1. Destination content and navigation.
2. Post-action bubble host.
3. Compact selected-post presentation.
4. Media viewer.
5. Profile image viewer.
6. Account/timeline selection sheet.
7. Composer sheet.
8. Profile editor sheet.
9. Emoji picker host.
10. Notification settings sheet and its later Back handler.
11. Profile discard dialog.
12. Sign-out dialog.

Global `SettingsHost` is later in `ConnectedApp`, outside this list.
Do not combine the two popup hosts into one call at a different position.
Keep separate calls, or defer a host abstraction until its ordering contract has tests.

#### Second-Stage State Ownership

Small `AppNavigationState`, `SelectedPostState`, or composer session owners are options, not mandatory new types.
For each, specify lifetime, account key, saveable fields, effects, cancellation, and reset behavior before moving state.
Do not create one app-wide state object containing every feature.
The structural endpoint is a coordinator with explicit effects and host calls, not an arbitrary line-count target.

### 6.5 Shared Post Presentation

Source: `ui/HomeFeed.kt:82-108,295-976`.
Prefer the existing `ui/posts/` feature package rather than adding a competing `ui/post/` package.

| Target responsibility | Declarations |
| --- | --- |
| `ui/components/AccountAvatar.kt` | `AccountAvatar` and only avatar-specific rendering support. |
| `ui/posts/PostRow.kt` | `PostRow`, `PostInteractionPresentation`, row dimensions, and row-only helpers. |
| `ui/posts/PostBody.kt` | `PostBodyText` and body-only composition. Keep parsing in `PostTextPresentation.kt`. |
| `ui/posts/PostMetadataRow.kt` | `PostMetadataRow`, `postTimestamp`, `FilteredHashtagSummary`, and their support. |
| `ui/posts/PostInteractionRow.kt` | `InteractionRow`, `ReactionRow`, `InteractionSummaryRow`, and interaction buttons. |
| Shared post action policy | `actionsForPost`; keep one declaration for post/detail callers. |
| Existing `ui/links/` area, in a separate slice | `openExternal` and possibly sharing support after all callers are checked. |

`sharePost` has callers outside the interaction row, including media. Do not make it private to a visual row.
`openExternal` already delegates preparation to `ExternalLinkHandler`; do not invent `ui/util/ExternalLinks.kt` with another policy.

Preserve Feed versus Detailed presentation, count suppression in feeds, detailed summaries, reaction numbers, and unknown counts.
Preserve `ContentWarningPresentation`, muted-hashtag policy, effective action targets, accessibility descriptions, image requests, and media transition registration.
Keep current remembered expansion state and keys in the same row composition.
Leave Home refresh, paging, scroll-direction observation, fallback bubble host, and scroll clearance in Home.

Required caller review: Search, Profile timeline, Saved Posts, Single Post, threaded replies, notification detail, media, DM, and large navigation.
A passing Home test alone cannot validate this shared extraction.

### 6.6 Emoji Picker

Source: `ui/emoji/EmojiPicker.kt`.
The Unicode catalog is about thirty-five dense source lines, not a separate multi-thousand-line subsystem.
Grouping and compact/full rendering boundaries provide the stronger reason to split the file.

Move public target types into `EmojiPickerTarget.kt` if their consumers benefit.
Move `buildEmojiPickerGroups`, `EmojiPickerGroup`, and pure full-group helpers into `EmojiPickerGrouping.kt`.
Keep group identity keys, pin limit, collapsed behavior during search, recent ordering, and post-specific custom emoji behavior unchanged.
Preserve the Unicode sequence and current deduplication order in `UnicodeEmojiCatalog.kt`.

`PickerChoice` and `PickerSection` support the compact grid path, not the full-group builder.
Keep them private with that renderer unless the compact algorithm moves as a coherent unit.
Do not widen their visibility only to satisfy the earlier file list.

Scrollbar math already has direct JVM tests in `ui/emoji/EmojiGridScrollbarTest`.
Move it only if this makes the file easier to maintain. Do not add a second implementation or duplicate the same tests.
Keep existing `EmojiCatalogViewModel`, catalog repository, cache, and picker preferences as their current owners.

### 6.7 Notification Repository

Source: `data/notifications/NotificationRepository.kt`.
Targets stay in `data/notifications/`.

Move `NotificationRepositoryState` and `NotificationInboxSnapshot` without field changes.
Move codec entry points `encode` and `decode`, plus their recursive helpers, into `NotificationRepositoryStateJson.kt`.
Keep `AccountId.stableFileName()` available to stores with the same SHA-256 input and filename result.
Place filename identity with store support if it needs a separate owner.
Keep category matching with inbox selection and delivery identity with delivery calculation, not in the JSON file.

The stored JSON is the installed compatibility format, even though Room hosts it.
Preserve nullable interaction counts, recursive quote data, read flags, group IDs, checkpoints, claim leases, settings, and push registration fields.
Keep legacy image/destination/push defaults and omission rules.
A round trip from newly generated JSON alone does not prove compatibility. Use fixed historical input fixtures.
Compare JSON field structure and decoded values. Do not depend on object key serialization order.

Extract pure page/checkpoint transforms only after R6.
Inputs include current state, accepted page/account/query, direction, and explicit baseline status.
Outputs contain next immutable state and explicit results.
Preserve directional boundaries, filtered checkpoints, baseline suppression, local tombstones, and unread precision.
Notification tie sorting currently uses ID text after timestamps. Treat any transport-order repair as a separate contract change.

Read transforms must preserve separate local seen, server acknowledgement, Android presented, and Android dismissed flags.
Dismissal removes the visible item and delivery while retaining its tombstone.
Do not equate local dismissal with server acknowledgement.

Delivery transforms take explicit time and claim identity inputs where required.
UUID generation, token validation, claim replacement, and publication remain inside the synchronized repository operation.
Keep the two-minute lease and stale-claim finish rejection unless a separate repair changes them.
Presentation policy remains in `NotificationDeliveryPlanner`.

The repository retains one synchronization boundary for generation maps, per-account flows, lazy load, claims, removal, and persistence.
`persistIfCurrent` must recheck the token and write the latest flow value under the monitor.
Its unused state argument can be removed in a small cleanup after race tests establish the invariant.
Do not replace it with a captured snapshot write.

Do not create separate read, delivery, or push repositories that mutate another copy of this state.
The existing `PushRegistrationRepository` is a callback/session association helper, not a second notification store.

### 6.8 Protocol Sources

Existing protocol services already own Profile, Direct Messages, Notifications, Push, streaming, and moderation.
Mastodon also has a self-profile service.
Keep new services private implementation details of their source facade.
`SocialSourceFactory` continues to construct sources, not a new feature service graph exposed to UI.

#### Misskey Thread

Target: `data/misskey/MisskeyThreadService.kt`.
Move the continuation store, acquisition types, focal/ancestor/child loading, request reservation, queueing, and local normalization.
Dependencies are origin, token, API, account identity, session revision, and clock.
Keep the outer `request` wrapper and capability state in `MisskeySource`.
There is no current Misskey thread capability check in this method. Adding one is not a move-only change.

Preserve these current limits during extraction:

| Limit | Value |
| --- | --- |
| Ancestors | 20 |
| Descendants | 200 |
| Descendant depth | 10 |
| Child requests per batch | 8 |
| Requests per acquisition | 40 |
| Descendant batch time check | 15,000 ms |
| Children page size | 30 |
| Response byte cap | 4 MiB per response |

Preserve breadth-first queue order, raw child cursor selection, malformed-entry skipping, parent matching, origin filtering, and request deduplication.
Continuation tokens are instance-local, single-use, and bound to `ThreadSessionKey`.
Do not persist them or recreate the service on each call.
R9 and P3 explain gaps that the move itself must not disguise.

#### Misskey Timeline

Target: `data/misskey/MisskeyTimelineService.kt`, only if justified after thread extraction.
Move timeline body construction, endpoint selection, response mapping, and raw outer-note cursor selection.
Keep `limit = 30`, `withFiles = true`, and optional `untilId` unchanged initially.
Keep capability refresh, status-to-error mapping, and capability invalidation in the facade.
Do not silently remove media options while moving code.
Keep account lookup separate unless a concrete shared responsibility justifies moving it.

#### Mastodon Thread

Target: `data/mastodon/MastodonThreadService.kt`.
Move focal/context requests, response limits, array mapping, refresh-hint parsing, and context construction.
Keep origin validation, thread capability decision, continuation session-key validation, and outer error mapping in the facade initially.
Do not add a continuation store. Current Mastodon context returns no continuation.
Malformed context array entries are skipped independently.
Keep resource-limit mapping and cancellation propagation.

#### Mastodon Paging And Timeline

S32 first gives `getPage` and authenticated cursor validation one protocol-local owner, such as `MastodonPageClient`.
Its callers include timeline, bookmarks, likes, and hashtag search.
Do not make collection/search behavior depend on a timeline feature service or copy validation into each caller.
Reuse lower transport validation only after checking that its accepted paths and error semantics match.

Keep null, relative, and absolute cursor behavior explicit.
Validate scheme, host, port, credentials, and fragment before an authenticated absolute request.
Preserve the complete `Link` URL. Do not replace it with a parsed status ID.
Thread and timeline services remain protocol-specific. Do not create a generic federated thread acquisition service.

Post creation and mutation service extraction is optional after these slices.
Keep `R10` separate from that extraction, and do not move capability ownership into the new service.

### 6.9 Feed Controllers And Shared Actions

Source: `ui/FeedViewModel.kt`.
Prefer a Photo Grid feature package, such as `ui/photogrid/`, rather than implying that Photo Grid belongs to Home.
It remains a Search destination with independent feed state.

The controller owns grid loading, generation, consumed cursors, selected feed, preference observation, saved hashtags, and error state.
Keep the already separate `PhotoGridFeedState` model unchanged initially.
Dependencies include account ID, session revision, source, preference repository, scope, and the existing favourite presentation policy.
The earlier dependency list omitted that policy: fetched grid posts call `applyFavouritePreference` today.

Expose one `StateFlow` through Feed's existing API.
Provide explicit update operations for optimistic changes, confirmed external posts, and preference-driven favourite changes.
Feed must not retain a second writable grid copy after extraction.
Preserve duplicate-cursor stopping, failed-cursor retry, stale result rejection, lazy initial load, and all text-only page behavior.
Resolve the unretained hashtag-save job from P5 before defining controller stop semantics.

Search extraction follows only after grid ownership is stable.
`AccountSearchState` is currently nested in `FeedState`.
Choose either a controller result callback into Feed or one composed state flow. Do not run two competing writers.
Keep networking protocol-neutral and preserve query identity across continuation.

`domain/PostReactionReducer` remains canonical for reaction transforms.
Use its existing tests and add missing invariants, including repeated application if callers require idempotence.
Favourite/repost/bookmark transforms can join a focused domain owner only after R7 establishes equivalent semantics.
Do not extract network jobs into a pure reducer.
Do not force thread overlays, collection removal, and feed updates into one configurable action engine.

### 6.10 UnifiedPush

Source: `data/notifications/push/UnifiedPushRegistrationManager.kt`.
Existing `PushRegistrationRepository`, `PushMessageDecoder`, and Misskey payload parser remain distinct owners.

Move `PushRegistrationManager`, `PushRegistrationWorkResult`, and `NoOpPushRegistrationManager` into an interface file first.
Message handling can then move behind `UnifiedPushMessageHandler`.
Keep decoding, account/protocol dispatch, Misskey mapping, read-all handling, chat notification mapping, and REST catch-up decisions together.
Do not duplicate payload parsing or move protocol JSON into shared UI/domain models.
Keep REST catch-up as the freshness path after incomplete or undecodable push messages.

Registration orchestration can move into a focused reconciler, but preserve the same manager-level mutex boundaries.
Do not introduce a second mutex that permits enable, disable, and endpoint replacement to race.
Do not describe every callback as serialized today: `onNewEndpoint` records session data and schedules work outside that mutex.
Preserve the session store's endpoint generation checks and the later endpoint-processing checks.

Keep callback instance ownership, notification token, session revision, pending endpoint, confirmed server endpoint, and retry time distinct.
Keep push keys in the encrypted session store.
Disable must still complete local cleanup when remote cleanup fails.
Use tests at the manager orchestration boundary, not only parser and repository tests.

### 6.11 Profile

Source: `ui/profile/ProfileScreen.kt` and `ProfileViewModel.kt`.
Existing `ProfileUiState`, `ProfileCategory`, `ProfileTimelineList`, and `EditProfileScreen` already provide boundaries.

Move `ProfileHeader`, redirect banner, stats, badge, and header-only support into `ProfileHeader.kt`.
Move profile field/link rendering into `ProfileDetails.kt` when useful.
Move `LargeProfilePresentation` separately into `ProfileLargePresentation.kt`.
Keep shared status rendering in one place when compact and wide presentations both use it.
Do not assign `ProfileStatus` to a header-only owner merely because it is below the header in the source file.

Preserve seed/refreshed account selection, redirects, avatar/banner image opening, banner blur, category restoration, and test tags.
Keep compact list assembly in `ProfileScreen` and existing list rendering in `ProfileTimelineList`.
Do not group callbacks into large mutable objects during the first extraction.

The timeline pager owns per-tab jobs, requested cursor sets, loading/refresh/continuation, duplicate handling, and page results.
The ViewModel remains the target/generation owner and coordinates details, relationship, pinned posts, and editor.
Every pager result must carry or validate the parent's target and generation.
Preserve valid cursors through empty filtered pages and allow manual continuation.
Preserve job-identity checks in cleanup so an old job cannot remove a newer job.

An editor session extraction is optional after the pager.
Preserve separate editor generation, unchanged-patch behavior, failure state, close behavior, and stale response rejection.
Keep existing profile session-revision conventions during structural work; resolve P5 separately.

### 6.12 Thread State

Source: `ui/thread/PostThreadViewModel.kt`.
Targets can include `ThreadPostStore.kt` and, only if needed, `ThreadRefreshPolicy.kt`.

Move the canonical post map, mutation overlays, context application, and confirmed local reply preservation into one focused in-memory owner.
Do not make both the store and a new reducer independently build rows.
`ThreadTreeBuilder` remains the canonical tree algorithm.
Keep phase assembly near the ViewModel if it is still small after store extraction.

Preserve account/session/focal identity, replacement versus continuation, disconnected rows, effective targets, and confirmed reply deduplication.
Complete R7/R8 before claiming mutation overlays are a behavior lock.
Keep acquisition jobs, action jobs, foreground observation, listeners, and public stop/deactivate methods in the ViewModel.
A pure refresh policy can select bounded delay or no refresh; it must not launch jobs.
Test delayed results and actions after selecting another focal post, not only switching accounts.

### 6.13 Post-Action Bubbles

Source: `ui/PostActionBubbles.kt`.
Possible targets: target types, host, hashtag presentation, reaction presentation, and placement files in one feature package.

Keep `renderedTarget`, visible state, target replacement, local reaction mode, and delayed dismissal in the host.
Preserve `BubbleDismissDurationMillis` at 150 ms during extraction.
Keep popup focus, outside dismissal, Back dismissal, placement, bottom clearance, and flick expansion thresholds.
Do not move remembered host state into an individual bubble whose lifetime differs.

Extract geometry into pure calculations with explicit window, anchor, content, and clearance inputs.
Test top/bottom edges, small windows, large content, and layout direction.
Test a new target arriving during the previous target's dismissal delay.
Existing emoji grid tests do not prove popup host behavior.

### 6.14 Media Viewer

Source: `ui/media/MediaViewerScreen.kt`, currently 562 lines.
Reuse `MediaViewerChrome`, `MediaPage`, `ZoomableMediaImage`, `MediaViewerTransitionState`, and `MediaTransitionState`.
The remaining boundaries are transition rendering/geometry, dismiss gesture, and the inline description surface.

S51 moves `MediaTransitionImage`, `MediaTransitionImageCanvas`, and directly related frame support into `MediaViewerTransitionLayer.kt`.
Keep one source/destination geometry implementation. Do not duplicate crop/fit policy across files.
Preserve separate image, clip, and visible bounds, drawable-size fallback, corner clipping, and request-scoped ownership.

S52 can move vertical gesture interpretation into `MediaViewerDismissGesture.kt`.
Pass events into the existing transition state. Keep pointer event pass, touch slop, horizontal arbitration, zoom guards, multitouch cancellation, and velocity limits.
Keep actual close orchestration and frame-synchronized handoff with the viewer owner.

Keep pager, selected attachment, per-page quality/readiness, selected transition key, and high-level Back behavior in the viewer.
Description Back must close the description before closing the viewer.
Do not recreate chrome, image loading, or transition state under new names.
Use real navigation/device checks after unit and Compose verification. Compilation cannot prove animation continuity.

## 7. Areas Not To Split By Size Alone

`SvgIconPaths.kt` is static artwork data. It is not a priority coordinator defect.
If generation is later introduced, keep source artwork, deterministic output, and a documented generation command.

Protocol mappers can remain long when they provide one coherent mapping boundary.
Split only independently meaningful mapping families, not arbitrary line ranges.
Keep nullable interaction-count mapping and malformed payload behavior covered.

`PostTextPresentation.kt` already owns parsing. Do not spread its grammar across Compose files.
Optimize or split it only with evidence of independent parsing stages or measured repeated work.

`AppShellState.kt` is mostly coherent definitions and mappings.
Move `editableProfilePatch` to Profile, but keep destination enum names and stored identities compatible.
Existing settings screens and policy files are already feature-oriented.
Their primary issues are wiring and correctness, not a need for more tiny presentation files.

## 8. Test Strategy And Test Cost

### Coverage Assessment

The suite is substantial, but not comprehensive at the highest-risk boundaries.
It is not generally too comprehensive. Some setup and repeated rendering work are more expensive than their assertions require.
No coverage percentage or measured suite speed is available from this review.

| Boundary | Existing evidence | Add before risky changes |
| --- | --- | --- |
| Root layout/navigation | `NavigationTest`, `WideNavigationTest`, `SearchPanelRestorationTest`, `LargeLayoutModeTest`. | Modal precedence, edge swipe versus Back, settings layering, same-account session replacement. |
| Composer/drafts | `NavigationTest` recreation/autosave; `ReplyComposerTest` effective target. | Body/CW selection replacement, visibility, quote/reply restoration, save failure, account change during save/publish. |
| Shared post UI | `HomeFeedTest`, `SinglePostScreenTest`, `SavedPostsScreenTest`, `ProfileScreenTest`. | Preserve recent count modes and CW/mute behavior through all moved callers. |
| Notification merge | Fourteen tests in `NotificationRepositoryTest`, including generation, cursors, claims, and counts. | R5/R6, delayed persistence, removal races, malformed top-level sections, durable lease fields. |
| Notification storage | `RoomNotificationStoreInstrumentedTest`; repository/file round trips. | Historical fixtures, real migration, importer marker/error cases, populated-table removal. |
| Notification delivery/push | `NotificationDeliveryPlannerTest`, `PushRegistrationRepositoryTest`, connector and parser tests, adapter/sync tests. | Full registration/message orchestration, endpoint replacement, failure/cancellation, concurrent disable. |
| Misskey thread | One basic `postAndThreadCombineAncestorsRootAndChildren` integration test. | Continuation reuse/rejection, parent errors, malformed children, raw cursor, all resource bounds, cancellation. |
| Mastodon thread/paging | `MastodonIntegrationTest`, protocol source contract tests. | Extreme/invalid refresh hints, response limits, malformed context, credentials/fragment cursor rejection where absent. |
| Photo Grid | Three `PhotoGridFeedViewModelTest` cases: lazy independence, text-only paging/cursors, late canceled result. | Preference-save stop/failure, account replacement, external actions, unsupported choices, retry and cursor cycles. |
| Profile | `ProfileViewModelTest` already covers target generations, empty pages, editor races, reactions, and relationships. | Pager-specific refresh/mutation interleavings and any new lifetime boundary. Do not duplicate existing scenarios. |
| Thread ViewModel | Six `PostThreadViewModelTest` cases cover basic acquisition, continuation binding, confirmed favourite, and reply counts. | Cross-action races, reaction overlays, focal switch, stale completion, foreground cancellation, refresh bounds. |
| Tree algorithm | Four `ThreadTreeBuilderTest` cases cover ordering, missing parents, cycles, and identity. | Only uncovered invariants. Do not repeat the tree matrix through Compose. |
| Settings | `SettingsDisplayTest`, `PostPreferencesRepositoryTest`, `ContentWarningPolicyTest`, `TrackingParameterCleanerTest`, `ModerationServiceTest`. | R1-R4 and actual command/ViewModel wiring. Adapter tests do not cover the moderation UI owner. |
| Emoji | `EmojiPickerTest`, picker preference/catalog tests, `EmojiGridScrollbarTest`. | Popup lifecycle and any moved compact/full algorithm boundary. Reuse existing grouping tests. |
| Media | `MediaViewerScreenTest`, `MediaTransitionStateTest`, `PostMediaCarouselTest`, request/AVIF tests. | Gesture event arbitration, description Back, delayed readiness, ownership during replacement, physical frame continuity. |

Use a risk-based matrix, not every possible cross-product at every layer.
Test parser/reducer variants at the lowest useful layer.
Retain a small integration test at each adapter/store/ViewModel boundary and representative UI tests for actual wiring.
Keep account isolation, origin security, persistence compatibility, and cancellation tests even when they look repetitive.
They protect different failure boundaries.

### Known Test Inefficiencies

`ExampleUnitTest` starts Robolectric at API 35 to assert `2 + 2 == 4`.
Remove that template in a test-cleanup slice. It protects no application behavior.

`MediaViewerScreenTest:249-269` contains three fifteen-iteration loops with 100 ms sleeps.
That case imposes at least 4.5 seconds of wall-clock waiting, excluding idle work.
Other fixed sleeps exist in the same file and `PostMediaCarouselTest`.
Replace sleeps with bounded readiness assertions, controlled image/network completion, and Compose clock advancement where appropriate.
A virtual coroutine clock does not advance a real HTTP server or native decoder.
Keep the original request-count and cache assertions after removing sleeps.

`NavigationTest` creates a full `MainActivity` and root app in setup.
It writes screenshots in five cases and performs native bitmap checks in some layout tests.
Those files are diagnostic output, not snapshot comparisons by themselves.
Keep pixel checks that prove underlap or clipping. Gate diagnostic PNG output on failure or an explicit capture option.
Use semantics/bounds tests for nonvisual routing assertions where that removes unnecessary native rendering.

Pure grouping checks in `EmojiPickerTest` currently share a Compose test class and setup.
Move pure cases to a JVM test class when grouping is extracted; keep UI interaction tests in Compose.
Do not remove Robolectric from JSON/API tests without replacing the Android `org.json` runtime dependency correctly.

Avoid one new suite per moved file if it repeats the same assertions through a forwarding facade.
Extend existing behavior tests, then reorganize their ownership if the feature boundary improves.
Do not weaken failure assertions, disable slow tests, or mark flakes ignored to claim faster verification.

### Required Verification Commands

Run the smallest relevant set before the full gate.
Use exact package declarations for filters. Some emoji tests use `me.foxtails.palustris.ui.emoji` despite their filesystem location.
Examples for Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.foxtails.palustris.NotificationRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "me.foxtails.palustris.MisskeyIntegrationTest" --tests "me.foxtails.palustris.MastodonIntegrationTest"
.\gradlew.bat :app:testDebugUnitTest --tests "me.foxtails.palustris.NavigationTest" --tests "me.foxtails.palustris.WideNavigationTest"
.\gradlew.bat :app:lintDebug
.\gradlew.bat test assembleRelease
```

For adapter changes, also run `MisskeySourceContractTest`, `MastodonSourceContractTest`, and any affected profile/notification/moderation contracts.
Run lint after authentication, storage, adapter, domain contract, notification, or security-sensitive changes.
Run instrumented tests for Android storage/platform behavior:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Use `./gradlew` in Unix shells.
Do not run `clean` by default. Do not run concurrent Gradle invocations against the same build outputs.
Preserve the required full `test assembleRelease` completion gate for coding slices.
Record existing failures before attributing them to extraction.
Use timeouts that accommodate the full suite; the task log already records a prior 120-second timeout.

The current build uses API 29 minimum, compile/target API 37, Kotlin 2.2.10, Java 11 bytecode, and a Java 21 test launcher.
`app/build.gradle.kts` enables Android resources for unit tests and conditionally configures release signing.
An unsigned release assembly can succeed without release credentials. Do not report it as a signed-release verification.

Current CI runs debug unit tests, debug lint, debug assembly, release assembly, and API 29 instrumentation.
The emulator job disables animations. That is useful smoke coverage, not proof of normal animation behavior.
Any proposed CI reduction must measure debug/release test overlap and preserve variant-specific behavior and shrinker verification.

### Device And Live-Server Checks

For affected UI slices, check compact and wide layouts, system font scaling, long translations, IME, and light/dark themes.
For media, use normal and reduced motion, pager/zoom/dismiss transitions, and source disappearance during close.
For notification/push work, check fresh-process delivery, account switching, endpoint replacement, and server-unavailable cleanup.
For authentication, check the browser callback and session replacement on a real flow.

Historical logs contain locked-device/no-Compose-hierarchy failures and unverified live-server cases.
Check the current device state before treating those entries as current code defects.
Do not repeat obsolete API 29 crash findings as open defects without checking the later guards and tests.
Record any unavailable device or live-server check explicitly at handoff.

## 9. Completion Criteria

A structural slice is complete only when ownership improves and its verified behavior remains unchanged.
A repair slice is complete only when its regression test passes and affected boundary tests remain green.

- Each requested implementation slice has a small reviewed diff and the required verification record.
- Implementation commits follow repository instructions when commit authorization is present.
- Documentation-only review does not imply the implementation slices have run.
- `Screens.kt` disappears after its three feature moves, without a compatibility catch-all.
- `SignInScreen.kt` contains sign-in UI rather than application/session wiring.
- The root coordinates state and effects but does not implement every destination and sheet.
- Shared post presentation lives outside Home, with count and content-warning behavior intact.
- Notification JSON lives outside the synchronized repository without changing installed compatibility.
- The repository remains the only notification merge point and preserves ordered, current-generation persistence.
- Protocol thread services remain below `SocialSource`, with one paging validation implementation per appropriate protocol boundary.
- Photo Grid has one writable controller state and stays independent from Home.
- Existing reaction and tree algorithms remain canonical. New names do not duplicate them.
- Settings commands, moderation jobs, and post preference storage have explicit failure and lifetime owners.
- All state keys, account identities, origin checks, and protocol boundaries remain compatible unless a dedicated repair specifies otherwise.
- Remaining bugs, measurements, and device/live-server checks appear in task logs and the handoff.
- User-facing product text says Beeline. Existing compatibility-sensitive internal identifiers remain unchanged.

Do not declare the entire cleanup complete because files became shorter.
The result must make behavior easier to locate, state safer to own, tests cheaper to target, and failures easier to diagnose.
