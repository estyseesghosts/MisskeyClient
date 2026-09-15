# Notification Repository Exploration

## Scope

This report follows `NotificationRepository`, its stores, notification callers, push delivery callers, domain models, and tests.

The current implementation uses `NotificationRepositoryState` as the account cache and persistence model.
`NotificationRepository` is a singleton in production.
`RoomNotificationStore` stores the complete state as one JSON string.

## State Ownership

`NotificationRepositoryState` in `NotificationRepository.kt:51-63` owns these fields:

| Field | Meaning | Main writers |
| --- | --- | --- |
| `items` | Up to 500 visible notifications | Page ingestion, stream events, dismissal, read-state actions |
| `unreadState` | Unread precision state | Page ingestion, unread refresh, stream read event, acknowledgement |
| `checkpoint` | Compatibility checkpoint for the all-category query | Page ingestion |
| `lastSyncedAtEpochMillis` | Timestamp from the active checkpoint | Page ingestion |
| `checkpoints` | Query-fingerprint to checkpoint map | Page ingestion |
| `dismissedIds` | Local tombstones | `dismissFromInbox` |
| `deliveries` | Durable Android delivery outbox | Ingestion, stream events, dismissal, claim, finish |
| `settings` | Account presentation settings | `updateSettings` |
| `pushRegistration` | Account push registration and retry state | Push registration code |

`items` are keyed by `Notification.id`. A notification belongs to the receiving account, not its actor, as defined in `domain/Notification.kt:5-18`.
The query-aware ingestion path filters by account and connection origin (`NotificationRepository.kt:183-190`).
The legacy path filters only by account (`:229-234`).

## Synchronization Boundary

`@Synchronized` protects direct methods that read or change repository maps:

- `observe` at `NotificationRepository.kt:86-89`.
- `checkpoint` at `:108-112`.
- `activate` at `:114-119`.
- `currentToken` at `:121-124`.
- `recoverToken` at `:126-141`.
- `invalidate` at `:143-147`.
- `settings` and `pushRegistration` at `:560-564`.
- `remove` at `:601-606`.

Mutation methods use `synchronized(this)` around validation, state read, transformation, and `MutableStateFlow` publication.
They then call `persistIfCurrent` after leaving the critical section.

The protected state transitions are `applyPage` (`:167-220`), `updateUnreadState` (`:330-339`), `applyStreamEvent` (`:341-388`), `markLocallySeen` (`:390-400`), `markSeen` (`:402-413`), `markPresented` (`:415-426`), `markAndroidDismissed` (`:428-443`), `acknowledge` (`:445-475`), `dismissFromInbox` (`:477-495`), `claimDelivery` (`:497-526`), `finishDelivery` (`:528-549`), `updateSettings` (`:566-575`), `updatePushRegistration` (`:577-588`), and `clearPushRegistration` (`:590-599`).

`persistIfCurrent` switches to `Dispatchers.IO`, reacquires the repository monitor, checks the token, and writes the current flow value (`:608-614`).
It intentionally ignores its `state` parameter.
This prevents an older persistence call from overwriting a newer state.

The synchronized boundary must retain these properties:

- Validate the token before every account-scoped mutation.
- Publish the complete next state as one `StateFlow` value.
- Check the token again before persistence.
- Keep claim creation and claim replacement atomic.
- Keep claim IDs and lease expiry checks atomic.
- Keep account removal and store deletion together.

Reducers can run inside this boundary, but they must not read mutable maps, publish flows, or persist state.

## Generation And Account Checks

`generations` maps each account to its current source generation (`NotificationRepository.kt:83-84`).
`isCurrentLocked` accepts generation zero only when the account has no in-memory generation (`:631-632`).
This supports restart-time push callbacks.

`NotificationSyncOrchestrator` creates a generation during registration (`NotificationSyncOrchestrator.kt:134-149`).
It increments and invalidates the generation during unregister (`:116-125`).
Account removal unregisters first, then deletes repository state (`:127-132`).

`recoverToken` checks the durable push registration session revision before creating a restart token (`NotificationRepository.kt:126-141`).
`updatePushRegistration` also requires matching account and generation (`:577-581`).

These checks protect REST pages, unread refresh, stream events, local read changes, server acknowledgement, delivery claims, delivery completion, settings, and push registration from stale writes.

`markAndroidDismissed` accepts only `AccountId` and notification ID because the Android delete receiver has no sync token (`AndroidNotificationDismissReceiver.kt:19-27`).
It requires a loaded item but does not check a generation (`NotificationRepository.kt:428-443`).
The receiver has a separate launch-payload validation boundary.

## Page Ingestion

The public page methods select semantic direction:

- `establishBaseline` uses `Initial` and marks the baseline (`NotificationRepository.kt:149-150`).
- `ingestNewerPage` uses `Newer` (`:152-153`).
- `ingestOlderPage` uses `Older` (`:155-156`).
- Deprecated `ingest` dispatches from `page.direction` (`:158-165`).

`applyPage` first checks the token.
It chooses the page query from the page checkpoint, or from the current all-query checkpoint when the page has items (`:175-181`).
Query-aware ingestion then filters by account and connection origin, prepends incoming items, deduplicates by ID, merges read state, removes tombstones, sorts newest first, and limits the result to `MAX_ITEMS` (`:182-190`).

The legacy path applies the same merge shape but lacks query checkpoint handling and the connection-origin filter (`:222-249`).

`mergeCheckpoint` at `:251-301` owns directional cursor rules:

- Older pages preserve the previous newest boundary.
- Newer pages preserve the previous oldest boundary.
- Initial pages establish available boundaries.
- Older terminal pages clear the oldest boundary.
- Newer pages update only newer continuation.
- Older pages update only older continuation.
- A page is complete when it reaches a boundary or has no continuation.
- Baseline status remains true after establishment.

The repository stores checkpoints under `query.stableKey` (`:200`).
Only the all-category query updates the compatibility `checkpoint` field (`:204`).
`NotificationQuery.stableKey` sorts category names and includes limit and grouping (`domain/NotificationPaging.kt:8-24`).
The repository does not compare cursor values.
It treats cursors as opaque adapter values.

## Delivery Outbox

`updateDeliveryOutbox` at `NotificationRepository.kt:308-328` is pure map transformation.
It creates records only for newer pages after a baseline, when the ID is not already cached, dismissed, or queued.
Baseline and older-history pages do not create Android alerts.

Stream notification events use a parallel merge in `applyStreamEvent` (`:347-369`).
They create one record after any stored checkpoint has a baseline.
Duplicate stream events do not create another record.

`pendingDeliveries` selects pending, failed, and expired posting records (`:551-558`).
`claimDelivery` atomically changes a claimable record to `Posting`, increments attempts, stores the attempt time, creates a UUID claim ID, and sets a two-minute lease (`:497-526`).
`finishDelivery` requires the current token and, when supplied, the current claim ID (`:535-545`).

`NotificationDeliveryWorker` loads cached notifications, claims records, applies the delivery plan, presents alerts, and finishes records (`NotificationWorkers.kt:125-195`).
The foreground stream controller enqueues delivery after received events (`ForegroundNotificationStreamController.kt:68-73`).
The sync orchestrator enqueues delivery after REST synchronization (`NotificationSyncOrchestrator.kt:226-240`).

The pure presentation policy belongs outside the repository.
`NotificationDeliveryPlanner.plan` decides presentation, suppression, channel, and preview behavior without changing repository state (`NotificationDeliveryPlanner.kt:29-79`).

## Read And Dismissal State

`mergeReadState` at `NotificationRepository.kt:616-625` is pure field-wise merging.
Known incoming server status replaces unknown incoming status.
Local seen, server acknowledgement, Android presentation, and Android dismissal flags use logical OR.

`markLocallySeen` changes only `locallySeen` (`:390-400`).
`markSeen` is the all-or-one form (`:402-413`).
`markPresented` changes only `androidPresented` (`:415-426`).
`markAndroidDismissed` changes only `androidDismissed` and never marks a notification read (`:428-443`).

`acknowledge` applies server coverage to every cached item (`:445-469`).
For `None` or `Exact`, it sets status to `Read` and `serverAcknowledged` to true.
For other unread states, it sets only `serverAcknowledged`.
It stores the acknowledgement unread state without changing local or Android flags.

`applyStreamEvent` applies `NotificationReadChanged` to every cached item (`:372-379`).
The Misskey stream maps `readAllNotifications` to this event, so the all-item behavior matches that protocol event.

`dismissFromInbox` removes the item, adds its ID to `dismissedIds`, and removes its delivery record (`:481-494`).
The tombstone survives restart and later page ingestion.
The `remoteApplied` parameter currently does not change the transition.
The ViewModel performs remote dismissal first and then calls this method (`NotificationsViewModel.kt:162-181`).

## Persistence And Migration

`NotificationStore` exposes `read`, `write`, and `delete` (`NotificationStore.kt:14-18`).
`InMemoryNotificationStore` is the unit-test map (`:20-25`).
`FileNotificationStore` uses an `AtomicFile` under `noBackupFilesDir/notifications` (`:27-50`).
`RoomNotificationStore` reads and writes one `NotificationStateEntity.stateJson` value (`:52-75`).

`LegacyNotificationFileImporter` imports an old atomic file once and creates a `.room-imported` marker (`LegacyNotificationFileImporter.kt:18-29`).
The marker is written even when no legacy state exists.

The active JSON format writes these top-level keys in `encode` (`NotificationRepository.kt:645-658`): `version`, `items`, `unread`, optional `checkpoint`, `lastSyncedAt`, `dismissedIds`, `checkpoints`, `deliveries`, `settings`, and optional `pushRegistration`.

Nested notification data includes IDs, account, time, typed activity, actors, target, destination, post, five read flags, raw type, and group (`:798-838`).
Nested post data includes media, polls, reactions, actions, quote, emoji, and nullable interaction counts (`:943-1031`).

`decode` ignores the version value (`:660-681`).
It skips many malformed array entries but malformed top-level checkpoint, delivery, push-registration, or account data can make the complete store read fail.
Compatibility defaults include target-to-in-app destination fallback, connected legacy push endpoint fallback, default settings, unknown unread state, and unknown read status (`:704-713`, `:824-835`, `:752-768`, `:1208-1214`).

`NotificationDatabase` declares ten Room entities at schema version 2 (`db/NotificationDatabase.kt:6-24`).
The DAO only reads and writes `notification_state`; its other methods only delete normalized tables (`db/NotificationDao.kt:8-44`).
The active repository does not use the normalized tables.
For example, `NotificationDeliveryEntity` lacks claim ID and claim expiry (`db/NotificationEntities.kt:65-76`).
`PushRegistrationEntity` lacks several fields that the JSON model persists, including session revision, server endpoint, confirmed endpoint generation, server remote ID, failure detail, failure stage, failure reason, and retry time (`:86-97`).
`NotificationQueryStateEntity` lacks the serialized account ID and query definition (`:43-54`).

`MIGRATION_1_2` creates only `notification_state` (`db/NotificationMigrations.kt:6-13`).
The application registers it in `AppModule.kt:154-159`.
The JSON blob in `notification_state.stateJson` is the actual stored format.

## Safe Reducer Boundaries

These functions are pure or can become pure reducer functions:

- `NotificationQuery.stableKey` and `NotificationPage` resolved-boundary properties.
- `Notification.mergeReadState`.
- Incoming filtering, prepend, deduplication, tombstone filtering, sorting, and the 500-item limit.
- `mergeCheckpoint` when it receives prior and page values as arguments.
- `updateDeliveryOutbox`.
- The `NotificationReceived` merge and delivery-map calculation.
- Read-state mapping for `NotificationReadChanged`.
- State-copy transitions for local seen, presentation, acknowledgement, dismissal, settings, and registration.
- `Notification.matches` at `NotificationRepository.kt:1251-1254`.
- `stableNotificationId` at `:1268`.
- `AccountId.stableFileName` at `:640-643`.
- JSON encode/decode functions from `:645` onward.

A reducer should accept immutable state and return immutable state plus explicit results.
It should not call `StateFlow`, `NotificationStore`, `Dispatchers`, UUID generation, or generation maps.

These functions must remain in the synchronized repository boundary:

- State-flow creation and lazy store loading.
- Generation activation, invalidation, current-token checks, and account removal.
- Critical sections around every mutation.
- Delivery claim creation and lease replacement.
- Claim-ID validation.
- The final current-token check before each store write.
- Store access and legacy-import marker handling.

## Serialization Move

The codec can move to a focused file or class without changing stored JSON.
Keep the module entry points `encode(state): JSONObject`, `decode(json): NotificationRepositoryState`, and `AccountId.stableFileName()`, or update all module tests together.

Keep key names, omission rules, enum strings, defaults, legacy fallbacks, cursor values, and version value unchanged.
The codec should operate on domain models and should not contain protocol JSON.

There is a current round-trip defect.
`encodeActivity` writes an `Unknown` activity destination under `destination` (`NotificationRepository.kt:1142-1144`), but `decodeActivity` ignores that field (`:1147-1183`).
An unknown activity with a server destination loses its destination after persistence.

## Existing Evidence

`NotificationRepositoryTest` covers duplicate merge, generation rejection, server acknowledgement, Android dismissal, local tombstones, directional cursors, stream merge, delivery suppression, filtered checkpoints, terminal history, duplicate delivery, claim recovery, singleton scope, and interaction-count persistence (`NotificationRepositoryTest.kt:38-397`).
`RoomNotificationStoreInstrumentedTest` covers blob round-trip, dismissal persistence, and deletion (`RoomNotificationStoreInstrumentedTest.kt:42-58`).
`MastodonNotificationSyncTest` covers multi-page newer continuation and terminal older history (`MastodonNotificationSyncTest.kt:69-149`).
`PushRegistrationRepositoryTest` covers restart token recovery, session revision rejection, endpoint persistence, and callback ownership (`PushRegistrationRepositoryTest.kt:44-161`).
`NotificationDeliveryPlannerTest` covers presentation policy separately.

## Missing Tests And Risks

The current tests do not lock these behaviors:

- Concurrent mutations followed by delayed persistence.
- A stale persistence call racing with account removal.
- Foreign notification IDs in `markAndroidDismissed`.
- A page whose checkpoint account differs from its token or items.
- Legacy-path pages with a foreign entity connection.
- Duplicate IDs with conflicting accounts.
- Delivery completion after dismissal or invalidation.
- Exact persistence of claim ID, claim expiry, attempt count, and failure fields.
- Every JSON activity, destination, push, group, and optional-field compatibility case.
- Malformed top-level JSON sections and resulting state-loss behavior.
- Account removal across a real Room database for every declared table.
- Legacy importer behavior for an existing marker, malformed source, and absent source.

The `remoteApplied` argument to `dismissFromInbox` is unused.
This is safe if the repository records only local visibility.
It is misleading if future behavior must distinguish remote acknowledgement from local tombstoning.

The normalized Room tables are not an alternate source of truth.
They must not be treated as persisted behavior during decomposition.

## Recommended Slice Order

1. Add characterization tests for JSON compatibility and unknown activity destinations.
2. Add reducer tests for merge, checkpoint, delivery, and read-state transitions.
3. Extract pure merge and checkpoint reducers while keeping synchronized callers unchanged.
4. Extract pure delivery and read-state reducers.
5. Extract the JSON codec without changing keys or defaults.
6. Keep generation, state publication, claim leases, store access, and persistence in `NotificationRepository`.
7. Decide separately whether normalized Room entities should be removed or implemented.

No source code changes were made during this exploration.
