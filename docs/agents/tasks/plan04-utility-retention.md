# Task State: Plan 04 Utility Ownership And Retention

**Plan:** `docs/decomposition_3/04.md` (Beeline Utility Ownership And Retention Plan).

**Specification:** `docs/decomposition_3/progressreport.md` section 4.

**Rebase context:** `docs/agents/tasks/decomposition-01-02-completion.md`,
`docs/agents/tasks/plan03-protocol-notifications.md`,
`docs/decomposition_3/03_corrected.md`.

**Started:** 2026-09-16.

**Status:** in progress. 04-A, 04-B, and 04-C are complete. 04-D through 04-K
remain. The cleanup-window audit added 04-K and prerequisites to 04-E, 04-H, and
04-J.

**This task is larger than one safe implementation slice.**

## Objective

Complete the Plan 04 boundaries. Move all generic browser handling into
`ExternalLinkHandler`. Move the static Unicode catalog out of the picker file.
Give each long-lived map an explicit key, lifetime, growth rule, release trigger,
miss behavior, and concurrency rule. Fix nearby ownership defects only when they
block those boundaries.

## Invariants

- Keep protocol JSON behind adapters. Keep shared models protocol-neutral.
- Keep `TrackingParameterCleaner` the pure cleanup owner.
- Keep share-sheet and clipboard behavior in post actions.
- Keep authentication launch and `palustris://notification` routing separate from generic links.
- Do not change a stored format without a migration in the same slice.
- Preserve signed URLs, raw paths, nontracking query values, and fragments.
- Do not apply ordinary LRU eviction to correctness records.
- Do not delete emoji content during an active read or decode.
- Do not cancel clients or close shared resources that active requests borrow.
- Do not create `Utils`, `CacheManager`, or another broad container.
- Use Beeline in user-facing text. Keep the codename out of user-facing content.
- One slice, one behavior, one commit. Commit only when the slice is green.
- Stage only files that belong to the slice. Preserve unrelated worktree changes.

## Rebase Findings

Source verified against `HEAD` at `bc4e13d` on 2026-09-16. The Plan 04 baseline
`c78e2cf` predates the Plan 01/02/03 completion, the S1/P1/Q1 work, and the T1
test-package mirroring. Recheck every finding at implementation start.

- 04-A is complete. `openExternal` is gone. `ExternalLinkHandler.open` holds the
  browser operation. This finding is closed.
- 04-B is complete. `DefaultUnicodeEmojis` is declared in
  `ui/emoji/DefaultUnicodeEmoji.kt`. This finding is closed.
- 04-C is complete. `EmojiAssetStore.urlLocks` is a fixed 64-lock array, so no
  URL-keyed lock entry remains. This finding is closed. The total budget
  `MAX_TOTAL_ASSET_BYTES = 128 MiB` is at `:221`. Chunk 04-D is still required.
- `MastodonDirectMessageService.directLastPosts` is still a
  `mutableMapOf<String, Post>` at `data/mastodon/MastodonDirectMessageService.kt:29`,
  written at `:41`, `:53`, and `:79`. Chunk 04-E is still required.
- `HttpClientPool.clients` is still an unbounded `ConcurrentHashMap` at
  `data/misskey/HttpClientPool.kt:18`. Chunk 04-G is still required.
- `CapabilityCache` is still declared at the end of
  `data/misskey/MisskeyCapabilityProbe.kt:126`. Its
  `CapabilityCacheKey(origin, accountId)` has no session revision at `:124`. Chunk
  04-H is still required.
- `AppRegistrationCache` is at `data/auth/AppRegistrationCache.kt:13`. Chunk 04-H
  is still required.
- `MediaTransitionState.begin` is at `ui/media/MediaTransitionState.kt:99`. Chunk
  04-I is still required.
- `NotificationSyncOrchestrator` keeps two `generations` maps at
  `data/notifications/NotificationSyncOrchestrator.kt:61` and `:115`. Chunk 04-J
  is still required.
- `AccountManager.kt` is now `ui/session/AccountManager.kt`, not `ui/AccountManager.kt`.
  The plan's source map needs this correction.
- The flat `ui/` package holds 28 production files, not the 43 named in the
  earlier audit.
- A `ktlint` gate with a checked-in baseline now exists. The baseline holds 430
  entries across 216 files and zero `no-wildcard-imports` entries. Slice style
  must not add findings outside the baseline. Baseline burn-down belongs to a
  separate static-analysis task, not to Plan 04.
- Test paths changed under T1. Slice verification must use the mirrored paths.

### Cleanup-Window Findings

A second audit compared `c78e2cf` to `bc4e13d`. It found new ownership and
retention defects that the Plan 01, 02, and 03 work introduced. Recheck each
path at slice start.

- `DirectMessageViewModel` constructs a private `DirectMessageWriteAuthority`
  at `ui/directmessages/DirectMessageViewModel.kt:38`.
  `DirectMessageRepository` constructs one at
  `data/directmessages/DirectMessageRepository.kt:28`. The production call at
  `ui/directmessages/DirectMessagesHost.kt:32` passes three factory arguments,
  so both defaults fire. `AccountManager.removeAccount` invalidates only the
  Hilt singleton at `ui/session/AccountManager.kt:332`. Chunk 04-E must repair
  this wiring before it removes the DM cache.
- `MastodonAuth` constructs a private `AppRegistrationCache` at
  `data/auth/MastodonAuth.kt:26`, `:28`, and `:31`. The Hilt module also
  provides one. Chunk 04-H must confirm every production login path uses the
  provided singleton.
- `DraftWriteAuthority` and `DirectMessageWriteAuthority` retain account keys
  for the process lifetime. Their `generations` and `locks` maps have no
  removal method. The entries are at `data/auth/DraftWriteAuthority.kt:25-26`
  and `data/directmessages/DirectMessageWriteAuthority.kt:26-27`. Chunk 04-J
  is required.
- `NotificationRepository.kt:54` adds a `writeLocks` map.
  `removeAccount` removes `states`, `storageHealth`, and `generations` at
  `NotificationRepository.kt:526-528`, but not `writeLocks`. Chunk 04-J is
  required.
- `NotificationSyncOrchestrator.kt:61` adds a second `generations` map to
  `NoOpNotificationSyncController`. No method removes an entry. Chunk 04-J is
  required.
- `ProfileTimelinePager.kt` is the live paging owner. `ProfileViewModel` keeps
  a dead copy. The dead members are `pageJobs` and `requestedCursors` at
  `ui/profile/ProfileViewModel.kt:65-66`, `loadPage` at `:475`, and
  `publishPageFailure` at `:539`. No caller uses them. The live calls are at
  `:128`, `:134`, and `:606`. Chunk 04-K is required.
- `ProfileTimelinePager.requestedCursors` at `ui/profile/ProfileTimelinePager.kt:25`
  grows one cursor set per tab for the pager lifetime. Chunk 04-K is required.

## Decisions Required Before Implementation

These decisions gate their slices. Record each decision in this file before the
first edit of the slice.

1. **04-E thread anchor.** Choose the typed request that carries a validated post
   anchor plus a separate conversation identity. Coordinate with Plan 02's DM
   contracts. Do not treat a conversation ID as a status ID.
2. **04-E provisional identity.** Decide whether the stored provisional
   conversation value needs an explicit identity field and a migration, or stays
   as local-only state. Do not reinterpret legacy values by string shape.
3. **04-H capability key.** Include session revision in the cache key, or remove
   cross-session reuse. Do not let a late old-session probe publish into a
   current-session entry.
4. **04-J generation allocator.** Choose one monotonic allocator over the
   orchestrator lifetime with entries only for active accounts. Coordinate with
   Plan 02's token allocation. Do not restart at one.
5. **Retention limits.** Treat all maximum counts and expiry periods as proposed.
   Inject small limits in tests. Require recorded measurements before release.
6. **04-E write-authority wiring.** Choose assisted injection or an explicit
   constructor argument so the ViewModel and repository share the singleton that
   account removal invalidates. Do not construct a private authority.
7. **04-J authority key release.** Decide when an authority `generations` or
   `locks` account key is safe to remove. Keep waiter safety. Do not remove a
   key whose lock has waiters.
8. **04-K cursor bound.** Choose the bound and the prune trigger for the live
   per-tab requested-cursor sets.

## Slice Plan

Order follows `04.md` section 15. Each slice needs characterization tests before
it changes behavior. A slice may split again when it holds several unrelated
behaviors.

| Slice | Behavior | Depends on | May split |
| --- | --- | --- | --- |
| 04-A | Complete external-link ownership in `ExternalLinkHandler`. | none | no |
| 04-B | Separate the Unicode catalog from picker presentation. | none | no |
| 04-C | Replace emoji URL locks with fixed stripes. | none | no |
| 04-D | Enforce emoji mapping and byte retention with reader leases. | 04-C | yes |
| 04-E | Remove the DM last-post cache dependence. Repair shared write-authority wiring first. | 04-E decisions, Plan 02 | yes |
| 04-F | Bound idle Misskey thread continuations. | lifecycle coordination | no |
| 04-G | Bound HTTP client lookup retention. | none | no |
| 04-H | Bound capability and registration caches. | 04-H decision, Plan 03 | yes |
| 04-I | Repair media registry hidden-state retention. | none | no |
| 04-K | Remove dead profile paging authority and bound the cursor sets. | none | yes |
| 04-J | Audit correctness-critical retention and publish the inventory. | 04-F..04-I, 04-K, Plan 02/03 | yes |

## Progress

### 04-A Complete External-Link Ownership

Committed. The slice commit is `ee52ba9`. Source verified against `HEAD` at
`aecab82` before the edit.

- `ExternalLinkHandler.open` holds the full browser operation. It prepares each
  URL once, accepts only HTTP or HTTPS with a nonblank host, starts at most one
  `Intent.ACTION_VIEW`, and shows the standard error Toast on
  `ActivityNotFoundException`.
- The `ui.openExternal` declaration is gone. The eight feature calls now use
  `ExternalLinkHandler.open`. No declaration, call, or import of `openExternal`
  remains.
- `prepare` stays for the share sheet and the copy path. The preference Boolean
  is the only handler state.
- `ExternalLinkHandlerTest` is new. It records launched intents, resets the
  preference, and covers the null and blank no-op, the non-HTTP and blank-host
  no-op, the single view intent, tracking cleanup, and the missing-browser Toast.
- `app/ktlint-baseline.xml` is regenerated for the relocated `PostRow` and
  `SinglePostScreen` entries. The removed `PostRow` keyword-spacing finding is
  gone.
- Verification: focused tests pass. `test assembleRelease` passes.
  `:app:ktlintCheck` passes. `:app:lintDebug` passes. Physical launch behavior
  stays device-unverified.

### 04-B Separate Unicode Data

Committed. The slice commit is `6a87c76`.

- `ui/emoji/DefaultUnicodeEmoji.kt` now holds the `internal val
  DefaultUnicodeEmojis` declaration. `EmojiPicker.kt` loses only that
  declaration and its following blank line.
- Both consumers, `EmojiPicker.kt` and `EmojiPickerGrouping.kt`, stay in the
  `ui.emoji` package, so no caller and no import changed. Picker behavior is
  unchanged.
- `DefaultUnicodeEmojiTest` compares the catalog to a fixed ordered snapshot in
  `app/src/test/resources/emoji/default-unicode-emojis.txt`. The snapshot was
  captured from the pre-move declaration and pins order, duplicates, variation
  selectors, surrogates, and joiners. The test does not rebuild the expectation
  at runtime.
- Verification: `DefaultUnicodeEmojiTest`, `EmojiPickerTest`,
  `EmojiCatalogViewModelTest`, `HomeFeedTest`, and `SignInScreenTest` pass.
  `test assembleRelease` passes. `:app:ktlintCheck` passes. `:app:lintDebug`
   passes. No baseline change is needed. Physical rendering stays
   device-unverified.

### 04-C Bound Emoji Coordination

Committed. The slice commit is `633cd7a`.

- `EmojiAssetStore.urlLocks` is now `Array(URL_LOCK_COUNT) { Any() }`, a fixed
  array of 64 locks. No URL-keyed lock entry remains, and the structure has
  constant size.
- `get` canonicalizes the URL before coordination, selects
  `stripeIndex(canonicalUrl)`, and holds the stripe across lookup, revalidation,
  and asset publication. Download, timeout, redirect, and byte-limit behavior is
  unchanged.
- `stripeIndex` uses `canonicalUrl.hashCode() and URL_LOCK_MASK`. The mask keeps
  the index nonnegative for every hash, including `Int.MIN_VALUE`, where `abs`
  would stay negative.
- Different canonical URLs can share a stripe. They serialize without becoming
  the same cache identity. Only colliding URLs lose network concurrency.
- `EmojiAssetStoreTest` gains concurrent same-URL requests with a barrier and one
  download, a deliberate stripe collision with latches and separate identity,
  independent stripes, failure followed by retry, cancellation that releases the
  stripe, and 10,000 unique URLs against the constant 64-stripe structure.
- `app/ktlint-baseline.xml` is regenerated for the shifted annotated `instance`
  declaration line.
- Verification: focused `EmojiAssetStoreTest` and `EmojiCacheDatabaseTest` pass.
  `test assembleRelease` passes. `:app:ktlintCheck` passes. `:app:lintDebug`
  passes. Physical picker download latency and device behavior stay
  device-unverified.

### 04-A Complete External-Link Ownership

- Move the full browser operation into `ExternalLinkHandler.open`.
- Remove the `ui.openExternal` declaration and all imports.
- Prepare each URL once. Accept only HTTP or HTTPS with a nonblank host.
- Preserve null and blank no-op behavior, the error string, and the Toast.
- Start at most one `Intent.ACTION_VIEW` and catch `ActivityNotFoundException`.
- Keep `prepare` for sharing and copying. Keep the preference Boolean, never a Context.
- Tests: proposed `ExternalLinkHandlerTest` with intent capture and preference reset. Keep callback and share-sheet tests.
- Gate: no remaining `openExternal` declaration, call, or import.

### 04-B Separate Unicode Data

- Create proposed `ui/emoji/DefaultUnicodeEmoji.kt`. Move the existing
  `internal val DefaultUnicodeEmojis` unchanged.
- Preserve element order, duplicates, variation selectors, surrogates, and joiners.
- Change neither consumer. Change no picker behavior.
- Tests: proposed `DefaultUnicodeEmojiTest` with a fixed ordered snapshot written
  before the move. Never compute the expectation from the catalog at runtime.
- Gate: `EmojiPickerTest` plus Home and sign-in reaction tests pass. One definition remains.

### 04-C Bound Emoji Coordination

- Replace `urlLocks` with a fixed array of 64 lock objects.
- Use a stable nonnegative stripe index with a mask. Do not use `abs(hash)`.
- Keep canonicalization before coordination. Hold the stripe across lookup,
  revalidation, and publication.
- Do not change download, timeout, redirect, or byte-limit behavior.
- Tests: extend `EmojiAssetStoreTest` with concurrent same-URL requests, deliberate
  collisions, failure and retry, and cancellation. Use barriers, not sleeps.
- Gate: no URL-keyed lock entries remain. The structure has constant size.

### 04-D Enforce Emoji Storage Retention

- Bound inactive URL mappings first. Start at 4,096. State the eviction order accurately.
- Prune unreferenced content toward 128 MiB. Keep mappings for one content hash coherent.
- Add a closeable asset lease. Release on decode success, failure, and cancellation.
- Exempt active leases and writes from eviction. Document temporary overage.
- Delete only shared, credential-free assets. Do not delete another account's assets.
- If metadata changes, export the Room schema and add migration coverage.
- Tests: count pressure, byte pressure, shared hashes, 304, offline fallback,
  missing files, failed deletion, restart cleanup, and a held lease during eviction.
- Gate: mapping and inactive-byte limits hold. Active readers keep working.

### 04-E Remove DM Cache Dependence

- Repair the shared write authority first. Pass the account-lifecycle singleton
  through the ViewModel into the repository. Remove both private constructions
  at `DirectMessageViewModel.kt:38` and `DirectMessageRepository.kt:28`.
- Test that the ViewModel and repository receive the instance that account
  removal invalidates. A revoked writer must not mark a conversation read.
- Remove `directLastPosts`. Remove the send-path insertion.
- Add the validated post anchor to the neutral thread request. Carry conversation identity separately.
- Load the anchor through supported status endpoints. Validate origin, ownership, and direct audience.
- Return a normalized unavailable result when the anchor is missing or inaccessible.
- Preserve provisional conversations without presenting them as verified server identity.
- Do not send a server mark-read request with a guessed conversation ID.
- Tests: cold open, restart, equal conversation and post values, provisional send
  and reconciliation, deleted and foreign anchors. Assert no individual conversation GET.
- Gate: retained adapter last-post entries are zero.

### 04-F Bound Misskey Continuations

- Add a focused continuation owner in the Misskey adapter. Keep at most 16 idle
  acquisitions per source. Expire after 10 minutes idle.
- Keep lookup, expiry, removal, and insertion atomic. Do not hold the lock over network work.
- Use an injectable monotonic clock. Prune on lookup and insertion.
- Keep tokens opaque and single-use. Validate the session key before consuming.
- Return the existing `SourceError.Unsupported("thread.continuation")`.
- Tests: proposed `MisskeyThreadContinuationTest` with tiny capacity and fake time.
- Gate: idle acquisitions are bounded. Foreign and reused tokens stay rejected.

### 04-G Bound HTTP Client Lookup

- Use an access-ordered synchronized map with an initial limit of 16 entries.
- Serialize lookup and construction.
- Evict only the lookup reference. Do not cancel or close borrowed clients.
- Keep timeouts, protocol-sensitive keys, and disabled redirects.
- Tests: proposed `HttpClientPoolTest`, or extend `CrossCuttingTest`, for reuse,
  eviction order, concurrent construction, and borrowed-client survival.
- Gate: lookup retention is bounded. Live sources keep usable clients.

### 04-H Bound Capability And Registration Caches

- Move `CapabilityCache` into a proposed `data/misskey/CapabilityCache.kt`.
- Bound it at 32 session-aware entries. Enforce freshness in lookup. Remove expired
  entries on access and insertion.
- Remove account entries during account removal. Prevent late old-session publication.
- Bound `AppRegistrationCache` at 16 entries with 24-hour idle expiry. Use a monotonic clock.
- Confirm instance wiring. `MastodonAuth` constructs private `AppRegistrationCache`
  instances at `MastodonAuth.kt:26`, `:28`, and `:31`. Confirm every production
  login path receives the Hilt singleton. Assert one provided instance serves
  all construction paths.
- Keep pending-login credentials captured. Preserve required-scope matching.
- Tests: same-origin accounts, reauthentication, revoked grants, expiry, capacity,
  late probe completion, scope upgrade, and concurrent creation.
- Gate: old revisions never authorize new-session behavior.

### 04-I Repair Media Registry Retention

- Represent not-hidden as absence. Remove the previous hidden key on replacement.
- Keep at most the current active key in hidden state.
- Keep owner identity checks in `end` and handoff.
- Tests: extend `MediaTransitionStateTest` with many different keys and a stale-owner finish.
- Gate: hidden metadata is bounded. Stale owners cannot clear a new owner.

### 04-J Review Correctness-Critical Retention

- Set notification dismissal IDs and delivery records to account-lifetime durable retention.
- Prefer one monotonic generation allocator with entries only for active accounts.
- Keep account locks alive while waiters hold them.
- Give the authority maps a release rule. Remove `DraftWriteAuthority` and
  `DirectMessageWriteAuthority` account keys during account removal after
  quiescence. Remove `NotificationRepository.writeLocks` entries during account
  removal. Give `NoOpNotificationSyncController.generations` the same policy as
  its production sibling or remove it.
- Treat the authority entries as correctness metadata. Do not add LRU eviction.
- Add `DraftWriteAuthority`, `DirectMessageWriteAuthority`, and the
  `writeLocks` map to the inventory with source links.
- Preserve the existing `MediaImageLoader` limits.
- Publish a completed retention inventory with source links and measured exceptions.
- Tests: replay after restart, pagination, re-addition generation invalidation,
  and retained notification volume. Add a test that proves an authority account
  key is released during account removal.
- Gate: every touched long-lived structure has a bounded rule or a justified lifetime.
  No exempt claim without measurements.

### 04-K Bound Profile Paging Authority

- Remove the dead paging copy from `ProfileViewModel`. Remove `pageJobs` and
  `requestedCursors` at `:65-66`, `loadPage` at `:475`, `publishPageFailure` at
  `:539`, and the dead clears at `:87`, `:600`, and `:605`.
- Keep the live `ProfileTimelinePager` behavior unchanged.
- Bound `ProfileTimelinePager.requestedCursors` at `:25`. Prune a set on refresh
  and on target change. Keep the duplicate-cursor guard while a request is in
  flight.
- Tests: a ViewModel test must fail if the dead members return. Assert a refresh
  clears the old cursor set and repeated pagination cannot grow a set without
  bound. Keep duplicate-cursor detection covered.
- Gate: one paging owner remains. Each cursor set has a prune trigger and a bound.

## Verification

Use the Gradle wrapper from the repository root with `--no-daemon --console=plain`
and an explicit timeout. New selectors become valid only after their suites exist.

```powershell
$env:GRADLE_OPTS="-Dorg.gradle.daemon=false"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "me.foxtails.palustris.ui.links.ExternalLinkHandlerTest" --tests "me.foxtails.palustris.domain.TrackingParameterCleanerTest"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "me.foxtails.palustris.ui.emoji.EmojiPickerTest" --tests "me.foxtails.palustris.ui.emoji.DefaultUnicodeEmojiTest" --tests "me.foxtails.palustris.data.emoji.EmojiAssetStoreTest" --tests "me.foxtails.palustris.data.emoji.EmojiCacheDatabaseTest"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "me.foxtails.palustris.DirectMessageSourceTest" --tests "me.foxtails.palustris.data.misskey.MisskeyIntegrationTest" --tests "me.foxtails.palustris.ui.thread.PostThreadViewModelTest" --tests "me.foxtails.palustris.CrossCuttingTest"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "me.foxtails.palustris.ui.media.MediaTransitionStateTest"
.\gradlew.bat --no-daemon --console=plain :app:ktlintCheck
.\gradlew.bat --no-daemon --console=plain test assembleRelease
.\gradlew.bat --no-daemon --console=plain :app:lintDebug
```

- Run focused tests first, then the full gate.
- Keep every existing test path current. T1 mirrored the tests, so old root paths
  no longer resolve. `TrackingParameterCleanerTest` is in `domain`,
  `MisskeyIntegrationTest` is in `data.misskey`, and `MediaTransitionStateTest`
  is in `ui.media`.
- 04-E needs a shared write-authority test. 04-H needs a registration-cache
  wiring test. 04-K needs a profile paging-owner test. Add the selectors after
  the suites exist.
- Use deterministic clocks and barriers. Avoid sleep-based expiry tests and
  immediate garbage-collection assertions.
- Run heap and disk measurements separately. Record API level, workload, accounts,
  bounds, and elapsed time.
- Device, browser, font, and live-server behavior stay unverified without a device.

## Exit Conditions

- Every Plan 04 verification checklist item in `04.md` is met or explicitly deferred with an owner.
- `ExternalLinkHandler` owns generic opening and depends on no post or profile presentation.
- The Unicode catalog has one definition and a fixed snapshot test.
- Every touched long-lived map has a bounded rule or a justified account-lifetime rule.
- One shared write authority serves the DM ViewModel and repository. One provided
  registration cache serves every production login path.
- The authority generation and lock maps have a release rule. No removed account
  keeps an entry.
- One profile paging owner remains. Each live cursor set has a prune trigger and a bound.
- The retention inventory lists source links, limits, measurements, and exceptions.
- The ownership pages and the human privacy and offline-cache guidance are updated.
- `test assembleRelease`, `:app:ktlintCheck`, and `:app:lintDebug` pass.
- No slice is described as complete while a known unbounded owner lacks a policy and its test.

## Blockers

- No emulator or device is reachable. Connected instrumentation stays unverified.
- Live-server and signed-release behavior stay unverified.
- 04-E needs the threaded request, provisional-identity, and write-authority wiring decisions.
- 04-H and 04-J need Plan 02 and Plan 03 coordination before shared contract changes.
- 04-J needs the authority key-release decision. 04-K needs the cursor-bound decision.
- The 04.md limits are proposals, not measured bounds. Release approval needs measurements.

## Last safe commit

`633cd7a` "Bound emoji URL coordination with fixed lock stripes".
