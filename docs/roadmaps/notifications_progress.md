# Notification roadmap progress

Updated 2026-09-08 while implementing the plan in `docs/roadmaps/notifications.md`.

## Current conclusion

The notification foundation and an account-scoped inbox are implemented without changing the recent Replies/Reposts/Likes chip or compact navigation-dock behavior. The app can retrieve, persist, merge, filter, page, locally mark, explicitly acknowledge, and dismiss notifications for the active account. Mastodon multi-page newer and older traversal now follows the server's moving continuations and records terminal history. Older-history and overlapping retained rows are excluded from new audible delivery eligibility. Newer catch-up refreshes adapter unread knowledge even when page payloads are unknown. Category switches now honor the requested state, including All-minus-one and empty selections. Delivery claims are now lease-based and claim-safe across worker recovery. REST reconciliation remains active even while a stream is marked connected, and Misskey streams send the account bearer token during the WebSocket upgrade; verified readiness and reconnect behavior remain open. Stored accounts are supervised independently of `FeedViewModel`. UnifiedPush callbacks can now rehydrate a restart-safe repository token, and server-confirmed subscription state is kept separate from a distributor endpoint that has only been received.

The user has reported a provisional Mastodon push smoke success on the connected
device. Misskey push is intentionally not advertised as equivalent: affected
Misskey-family versions reject the app's MiAuth credential at secure `sw/*`
endpoints, so the app reports that combination as unsupported and retains REST /
foreground fallback.

The implementation is ready for the remaining user-provided live Samsung/Sunup
verification. The Mastodon smoke report is not a complete acceptance result:
distributor callbacks, exact server registration, background/process-death
recovery, two-account isolation, and device target routing still require the
test. Misskey requires an exact server/fork compatibility decision first.

| Roadmap area | Status | Evidence or remaining gate |
|---|---|---|
| Milestone 0: protocol and push feasibility | Implemented in code; live evidence partial | Connector 3.3.5 is pinned, Sunup is the preferred installed distributor, both server push contracts are implemented, and callback/endpoint recovery is locally covered. Mastodon has a provisional user smoke result; Misskey secure-credential compatibility and the full round trip remain. |
| Milestone 1: domain contracts and permissions | Mostly implemented | Typed account-bound notifications, opaque cursors, unread precision, acknowledgement contracts, access metadata, and typed follow-request targets exist. Capability refresh is not yet persisted as authoritative session state. |
| Milestone 2: REST adapters and mapping | Substantially implemented; Mastodon catch-up corrected | Listing, filters, opaque cursors, grouped mapping, unknown records, unread lookup, Misskey mark-all, Mastodon marker acknowledgement, dismiss, follow-request routing, and multi-page Mastodon traversal are covered synthetically. Live-version and marker race verification remain. |
| Milestone 3: durable repository and lifecycle | Core implemented | App-private atomic files, bounded deduplication, checkpoints, account isolation, generation fencing, local-seen/server-acknowledged/Android-presented state, account restoration, and account-lifetime polling are implemented. Migration, pending-operation outbox, and crash/restart race coverage remain. |
| Milestone 4: inbox and actions | Core implemented | `NotificationsViewModel`, cached list rows, Replies/Reposts/Likes filtering, refresh, older pagination, mark-all, local seen, dismiss, Direct messages isolation, account-bound cold launches, read-only post targets, and profile routing are wired. Full live target/action coverage remains. |
| Milestone 5: UnifiedPush and Android alerts | Implemented; protocol evidence split | Connector service, encrypted key manager, stable account mapping, restart-safe callback ownership, confirmed-versus-pending endpoint state, safe payload hints, WorkManager catch-up, permission-safe presentation, and account-bound pending intents are implemented. Mastodon has a provisional smoke result; Misskey affected secure MiAuth combinations are unsupported. |
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

### 2026-09-08 — WebSocket transport request shape

- Misskey WebSocket requests retain an HTTPS URL so OkHttp performs the protocol upgrade through its supported WebSocket transport.
- Added a focused request-shape regression for the HTTPS scheme, streaming path, and authorization header.

### 2026-09-08 — Historical delivery eligibility

- Older-page ingestion no longer creates pending Android deliveries after an account baseline has been established.
- Incoming IDs already retained in the inbox are treated as overlap, so a repeated REST page cannot re-alert a cached event.
- `NotificationRepositoryTest` covers baseline, older history, and overlapping newer rows.

### 2026-09-08 — Misskey stream authentication

- Misskey's notification stream now passes the account bearer token to the shared validated WebSocket transport during connection setup.
- Adapter-level coverage verifies the authorization header reaches the request; live server authentication and readiness remain unverified.

### 2026-09-08 — Catch-up unread reconciliation

- Newer catch-up now refreshes the source unread endpoint after completion, cursor delay, and page-budget exhaustion.
- The repository is updated only when the adapter returns known unread precision; unknown endpoint results preserve the prior state.
- Mastodon synchronization coverage verifies a changed lower-bound unread result after a multi-page catch-up.

### 2026-09-08 — Category switch semantics

- Settings now pass each switch's requested checked value through to the ViewModel.
- Selecting an individual category expands All into explicit categories; disabling the last one leaves an intentional empty selection.
- Empty category selections survive the repository's JSON-backed restart path.

### 2026-09-08 — Delivery claim recovery

- Delivery records now persist an expiring claim identity so a worker killed during `Posting` can be recovered.
- Completion accepts the current claim identity, preventing a stale worker from finalizing a later attempt.
- Presentation failure returns `Result.retry()` and keeps the record eligible for a later drain.

### 2026-09-08 — REST fallback while streaming

- The account synchronizer no longer skips its periodic REST catch-up merely because the foreground stream state is connected.
- This keeps missed events and a socket that has not completed readiness from suppressing reconciliation.

### 2026-09-08 — UnifiedPush callback and endpoint recovery

- Distributor callbacks now restore a generation-zero repository token when the callback arrives before the account synchronizer has rebuilt in-memory state; the next account sync generation supersedes it.
- Push registration stores the last server-confirmed endpoint separately from the latest distributor endpoint, so a failed first server registration retries with create while an existing subscription continues to use update.
- File-backed notification state persists the confirmed endpoint and upgrades older connected records from the legacy single-endpoint representation.
- `PushRegistrationRepositoryTest` covers cold-process owner lookup and confirmed endpoint persistence; live distributor and server behavior remain unverified.

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
2. Add serialized marker writes with conflict/re-read handling and verify
   adapter behavior against the selected live versions.
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
- `./gradlew :app:testDebugUnitTest --tests me.foxtails.palustris.PushRegistrationRepositoryTest --tests me.foxtails.palustris.NotificationRepositoryTest`
- `./gradlew lintDebug`
- `./gradlew test assembleRelease`
- `./gradlew :app:compileDebugKotlin`

Not verified:

- Authenticated Misskey/Mastodon delivery, Sunup callback behavior, process-death/reboot recovery beyond the repository-level callback test, distributor removal, and cold-start notification taps.

The debug build was installed and launched with the local `platform-tools\adb` against the connected Samsung SM-G986W transport; the package install and launch command completed successfully. No live authenticated server or UnifiedPush delivery result is claimed. Commits for the implementation are focused and pushed to `origin/main`; unrelated worktree files were preserved. The connector and lifecycle dependencies are recorded in the completion log with their reasons.
