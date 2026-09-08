# Notification roadmap progress

Updated 2026-09-08 while implementing the plan in `docs/roadmaps/notifications.md`.

## Current conclusion

The notification foundation and an account-scoped inbox are implemented without changing the recent Replies/Reposts/Likes chip or compact navigation-dock behavior. The app can retrieve, persist, merge, filter, page, locally mark, explicitly acknowledge, and dismiss notifications for the active account. Mastodon multi-page newer catch-up now follows the server's moving continuation and stops when the server reports no newer page. Stored accounts are supervised independently of `FeedViewModel`.

The implementation is ready for the user-provided live Samsung/Sunup verification. This is not a claim that the live round trip has already succeeded: distributor callbacks, authenticated server registration, background delivery, process death, and device presentation still require that test.

| Roadmap area | Status | Evidence or remaining gate |
|---|---|---|
| Milestone 0: protocol and push feasibility | Implemented in code; live gate open | Connector 3.3.5 is pinned, Sunup is the preferred installed distributor, and both server push contracts are implemented. Live version compatibility and a real push round trip remain. |
| Milestone 1: domain contracts and permissions | Mostly implemented | Typed account-bound notifications, opaque cursors, unread precision, acknowledgement contracts, access metadata, and typed follow-request targets exist. Capability refresh is not yet persisted as authoritative session state. |
| Milestone 2: REST adapters and mapping | Substantially implemented; Mastodon catch-up corrected | Listing, filters, opaque cursors, grouped mapping, unknown records, unread lookup, Misskey mark-all, Mastodon marker acknowledgement, dismiss, follow-request routing, and multi-page Mastodon newer traversal are covered synthetically. Live-version and marker race verification remain. |
| Milestone 3: durable repository and lifecycle | Core implemented | App-private atomic files, bounded deduplication, checkpoints, account isolation, generation fencing, local-seen/server-acknowledged/Android-presented state, account restoration, and account-lifetime polling are implemented. Migration, pending-operation outbox, and crash/restart race coverage remain. |
| Milestone 4: inbox and actions | Core implemented | `NotificationsViewModel`, cached list rows, Replies/Reposts/Likes filtering, refresh, older pagination, mark-all, local seen, dismiss, and Direct messages isolation are wired. Native target routing, follow-request controls, newer-arrival UX, content previews, and scroll restoration remain. |
| Milestone 5: UnifiedPush and Android alerts | Implemented; live gate open | Connector service, encrypted key manager, stable account mapping, endpoint registration, safe payload hints, WorkManager catch-up, permission-safe presentation, and account-bound pending intents are implemented. Live delivery remains unverified. |
| Milestone 6: streaming, recovery, and settings | Implemented; live gate open | Foreground-only adapter streams, reconnect/reconcile, account-unique workers, registration settings, permission request, quiet hours, categories, and periodic fallback are implemented. Live process-death and distributor recovery remain. |
| Milestone 7: release gates | Partial | Focused tests, lint, full tests, and release assembly must pass on the final push slice. Samsung rendering, authenticated servers, process death, distributor changes, and real push remain unverified until the user test. |

## Implementation log

### 2026-09-08 — repository ownership and marker contract

- Scoped `NotificationRepository` to the application Hilt component, so the repository injected into the source registry, synchronizer, and UI is the same instance.
- Corrected Mastodon's notification-marker acknowledgement to use `POST /api/v1/markers` and made the adapter contract test assert the HTTP verb.
- Focused `NotificationAdapterContractTest` and `NotificationRepositoryTest` pass. Marker conflict reconciliation and authoritative session revisions remain open.

### 2026-09-08 — stream state and periodic isolation

- Fixed stream insertion so each retained row merges only its own previous read state; a new event no longer changes cached rows to the new event's state.
- Moved periodic fallback scheduling to its dedicated WorkManager name and cancel the legacy incorrectly named periodic work when scheduling an upgraded installation.
- `NotificationRepositoryTest` passes, including a regression with an existing read row and an incoming streamed notification.

### 2026-09-08 — account-bound alert launch identity

- Notification launch data URIs now contain a deterministic opaque digest of the receiving account and canonical notification ID.
- Router parsing validates that digest against intent extras, preventing extras from being replaced by an equivalent PendingIntent belonging to another account on the same server.
- `NotificationLaunchRouterTest` passes, including account-specific URI and URI/extra mismatch coverage.

### 2026-09-08 — Mastodon moving continuation catch-up

- Mastodon newer reconciliation now prefers its moving `newerContinuation` over the committed newest boundary.
- The initial-page `min_id` fallback is limited to baseline requests; a continuation page with no `prev` link now terminates instead of inventing another request.
- `MastodonNotificationSyncTest` covers a baseline followed by three server pages and asserts every requested continuation URL.

### 2026-09-08 — Mastodon older-history exhaustion

- Older Mastodon pages now honor the persisted moving continuation when present.
- A terminal older page clears the repository's `oldest` cursor, preventing the inbox from requesting the final page repeatedly.
- The Mastodon synchronization regression now covers both multi-page newer catch-up and a two-page older range with terminal state.

## Implemented changes

### Account-scoped repository

`data/notifications/NotificationRepository.kt` is the single merge point for REST pages and local notification state. It provides:

- app-private `noBackupFilesDir` storage using `AtomicFile`;
- account filenames derived from a SHA-256 of connection metadata, with no token or response body persistence;
- stable notification identity deduplication and a 500-row bound;
- opaque checkpoints and truthful `Exact`, `AtLeast`, `Present`, `None`, and `Unknown` unread states;
- separate `locallySeen`, `serverAcknowledged`, and `androidPresented` flags;
- generation-fenced ingestion, acknowledgement, dismissal, and presentation writes;
- stale concurrent persistence protection by writing the newest current state under the repository lock.

`AccountSyncCoordinator` consumes the adapter unread endpoint instead of counting `Unread` values from a page whose read state may be `Unknown`. It checks the account generation after every suspended operation. `AccountManager` starts sync for every restored session and owns registration; `FeedViewModel` no longer starts or stops notification polling.

### Inbox UI

`NotificationsViewModel` owns account-bound refresh, older-page loading, explicit server acknowledgement, local seen state, dismissal, and repository observation. `NotificationsScreen` renders cached rows and retains the existing UI contract:

- Replies, Reposts, and Likes remain the only notification filters;
- tapping the selected chip clears the filter;
- compact chips remain in the bottom dock above contextual action/navigation;
- wide layout keeps the chips in normal content flow;
- Direct messages remains a separate destination and does not render notification filters.

Navigation and Compose regression coverage verifies these relationships.

### Protocol corrections

- Follow-request actions accept a typed receiving-side `AccountId` and validate origin/protocol before sending.
- Grouped Mastodon mapping rejects a group without an event identity instead of fabricating one from the presentation key.
- Misskey uses explicit `mark-all-as-read`; Mastodon acknowledges the latest notification through the marker endpoint and classifies unread counts as a lower bound.
- Adapter contract tests cover non-mutating listing, filters, cursors, grouped identity, unread lookup, acknowledgement routing, malformed groups, and account isolation.

### Android presentation boundary

`AndroidNotificationPresenter` is intentionally not invoked by the initial baseline. A first sync must not turn the existing backlog into an alert storm. Push and foreground stream events reconcile through the repository, select only eligible new events, call the presenter through the delivery worker, and then mark `androidPresented`.

## Remaining work for full support

1. Record the live Samsung/Sunup outcome against the selected Misskey-family and
   Mastodon server versions, including grouped pagination, marker semantics,
   push registration, delivery, process death, and logout cleanup.
2. Finish Mastodon older-history terminal/exhaustion handling and add
   serialized marker writes with conflict/re-read handling; verify adapter
   behavior against the selected live versions.
3. Persist refreshed capability results only for the matching session
   generation and expose supported/denied/unsupported/temporarily-unavailable
   states to UI.
4. Run the device matrix from the roadmap: denied notification permission,
   light/dark and wide layouts, multiple accounts, process death, reboot/Doze,
   distributor removal, revocation, and cold-start taps.

## Verification performed for this slice

Passed:

- `./gradlew :app:testDebugUnitTest --tests me.foxtails.palustris.NotificationRepositoryTest --tests me.foxtails.palustris.NotificationAdapterContractTest --tests me.foxtails.palustris.NotificationsScreenTest --tests me.foxtails.palustris.NavigationTest --tests me.foxtails.palustris.WideNavigationTest`
- `./gradlew :app:testDebugUnitTest --tests me.foxtails.palustris.MastodonNotificationSyncTest`
- `./gradlew lintDebug`
- `./gradlew test assembleRelease`
- `./gradlew :app:compileDebugKotlin`

Not verified:

- Android Studio Run-button install/launch on the connected live device. The native computer-use surface was unavailable in this task, so no device result is claimed.
- Authenticated Misskey/Mastodon delivery, Sunup callback behavior, process-death/reboot recovery, distributor removal, and cold-start notification taps.

Commits for the implementation are focused and pushed to `origin/main`; unrelated worktree files were preserved. The connector and lifecycle dependencies are recorded in the completion log with their reasons.
