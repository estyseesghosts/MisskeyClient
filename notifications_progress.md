# Notification roadmap progress and completion plan

## Scope and conclusion

This assessment is based on the current checkout, the notification roadmap in `docs/roadmaps/notifications.md`, `docs/notifications-compatibility.md`, the application source and tests, and the verification run performed for this task.

The repository has progressed well beyond the roadmap's original pre-implementation baseline: the shared notification domain, account ownership model, most of the REST adapter foundation, protocol-specific mappers, permission metadata, capability serialization, and synthetic adapter contract tests are present. In roadmap terms, the implementation is **substantially through Milestones 1 and 2, but not complete even at Milestone 2's production-readiness bar**.

It has **not** reached the part of the roadmap that makes notifications a usable product feature. The navigation destination is still a placeholder, there is no account-scoped notification repository or durable notification history, the existing polling coordinator still maintains an incorrect page-local badge, there is no Mastodon acknowledgement implementation, and there is no UnifiedPush, Android presentation, foreground stream, background worker, or notification settings implementation.

A useful status summary is:

| Roadmap area | Current status | Assessment |
|---|---|---|
| Milestone 0: protocol and push feasibility | Partial | Compatibility notes and synthetic fixtures exist, but the support matrix is not frozen to tested releases and no current source implements or proves a UnifiedPush round trip. |
| Milestone 1: domain contracts and permissions | Mostly implemented | Typed envelopes, queries, checkpoints, capabilities, access scopes, account ownership, and permission-upgrade safeguards exist. Capability resolution is not yet wired into a complete notification product. |
| Milestone 2: REST adapters and mapping | Substantially implemented | Misskey and Mastodon listing, filters, opaque cursors, grouped mapping, unknown records, unread lookup, and some actions are present and tested. Read acknowledgement, policy operations, live-server compatibility, and several edge cases remain. |
| Milestone 3: durable repository and account lifecycle | Not implemented | No notification store, deduplication layer, durable checkpoints, retention policy, read/presentation state, or application-lifetime source owner exists. |
| Milestone 4: inbox and actions | Not implemented | `NotificationsScreen` is still an empty/coming-soon state. There is no notification ViewModel, list, pagination UI, target routing, follow-request UI, or preferences UI. |
| Milestone 5: UnifiedPush and Android alerts | Not implemented | No connector, push service, subscription manager, push parser, Android notification presenter, channels, permission flow, or push deep-link routing exists. |
| Milestone 6: streaming, recovery, and settings | Not implemented | Both adapters inherit unsupported streaming; there is no reconnect/reconciliation worker, WorkManager integration, or delivery settings. |
| Milestone 7: release gates | Partial | Focused synthetic adapter tests, the full test task, lint, and release assembly pass. The required authenticated-server, push, device, process-death, and multi-account delivery evidence does not exist in the current implementation. |

So the project is best described as **a strong contract/adapter foundation at the end of the early roadmap section, not a partially usable complete notification system**. By implementation scope, this is roughly the first two milestone areas of an eight-area roadmap; by user-visible functionality, it is still near the beginning because the inbox and delivery paths are absent.

## What is already implemented

### Shared domain and ownership model

`domain/Notification.kt` now has the right general shape for a two-protocol client:

- notification identity is separate from the receiving `AccountId`;
- actor lists are optional and can contain multiple actors;
- activity is typed rather than requiring UI code to interpret a raw string;
- targets distinguish posts, profiles, polls, and conversations;
- destinations distinguish in-app targets from validated HTTPS server destinations;
- grouped rows have an account-scoped group identity separate from event identity;
- read state distinguishes `Read`, `Unread`, and `Unknown`, with a separate local-seen flag;
- unknown activity has safe fallback text instead of exposing protocol payloads.

`SocialModels.kt` also contains the main contract types: `NotificationQuery`, `NotificationCursor`, `NotificationPage`, `NotificationCheckpoint`, `NotificationUnreadState`, `NotificationAcknowledgement`, and push subscription models. `SocialSource` exposes older/newer retrieval, unread-state lookup, acknowledgement, dismissal, follow-request actions, push lifecycle operations, and streaming defaults that normalize unsupported features through `SourceError.Unsupported`.

`Event.kt` includes typed notification-received and read-changed event variants. `ServerCapabilities.kt` and `Access.kt` model notification capability categories, read semantics, unread precision, and requested versus known-granted access.

This is a good foundation. It must now be made authoritative by putting all notification-producing paths behind one repository rather than allowing the coordinator, future UI, push service, and stream to maintain separate state.

### Account and authentication groundwork

The account/session work contains several useful notification prerequisites:

- Misskey authentication requests `read:notifications` and `write:notifications`, along with the relationship permission used by follow-request actions.
- Mastodon application registration and authorization request `read write push`.
- Access scope state is persisted with sessions, including unknown state for older/migrated sessions.
- App-registration caching checks required scopes rather than blindly reusing a registration that lacks `push`.
- Permission upgrades retain the intended account identity and reject a returned account that does not match the account being upgraded.
- Session capabilities and access metadata are serialized by `AccountFileStore`.

These changes reduce account-crossing risk, but they do not yet create a background-capable notification session provider or persist notification data. A source currently exists as part of the active application/feed path, not as an independently supervised account service.

### Misskey adapter

`MisskeySource` and the `MisskeyMapper` portion of `MisskeySource.kt` provide a substantial REST foundation:

- listing uses `markAsRead=false` explicitly;
- category filters become Misskey `includeTypes` values;
- `sinceId` and `untilId` are used for newer and older traversal;
- cursors carry origin, account, query fingerprint, direction, and the opaque raw ID;
- account and query mismatches reject cursors before requests are sent;
- unread state reads the authenticated user and supports exact count, boolean presence, none, and unknown outcomes;
- explicit account-wide mark-all-as-read is implemented;
- follow-request accept/reject endpoints are present;
- actorless/system and unknown records are kept renderable;
- grouped reaction/renote-style records preserve actors, group identity, and canonical post targets;
- malformed optional post data is isolated from required notification identity.

The focused tests verify non-mutating fetches, filter parameters, both cursor directions, grouped mapping, account-scoped identity, unread lookup, and read-all request routing.

The adapter still needs live-version verification, complete capability evidence, a repository consumer, and push/stream integration. Grouped Misskey pagination must also be tested against the exact supported server/fork versions rather than assumed to have the same semantics as ungrouped listing.

### Mastodon adapter

`MastodonSource` and `MastodonMapper.kt` provide the other major foundation:

- v1 notification listing is the compatibility path;
- type filters and absolute `Link` continuations are supported;
- v2 grouped responses expand referenced accounts and statuses;
- cursor payloads bind account, query, API variant, direction, and validated continuation URL;
- pagination validates scheme, host, port, credentials, fragment, and notification route before attaching the bearer token;
- unread lookup returns a lower-bound/at-least result rather than pretending it is exact;
- dismiss is implemented;
- follow-request endpoints are implemented;
- actorless and unknown notifications survive optional malformed status data;
- `quoted_update` has a distinct activity and points at the user's quote status;
- grouped rows retain a separate account-scoped group identity and canonical status target.

The tests cover type filtering, cursor/query/account isolation, malicious foreign continuations, grouped status mapping, malformed optional records, and the quote-specific activity.

However, Mastodon does not implement `acknowledgeNotifications()`, so it currently inherits the unsupported default from `SocialSource`. The roadmap requires notification timeline marker handling, serialization of marker writes, conflict/re-read behavior, and explicit acknowledgement semantics. That is still a significant adapter gap.

## Confirmed notification problems and bugs to fix

The following are notification-specific correctness problems in the current code, not merely future features.

### 1. The current unread badge is effectively always zero

`ui/AccountSyncCoordinator.kt` calls the legacy `source.notifications()` method and sets:

```kotlin
unreadCount = page.items.count {
    it.readState.status == NotificationReadStatus.Unread
}
```

Both current mappers construct notifications with the default `NotificationReadState`, whose status is `Unknown`. The adapters do have separate `notificationUnreadState()` methods, but the coordinator never calls them. Consequently, a normal fetched page contributes no unread items and the current account badge remains zero even when the server reports unread notifications.

This is a direct user-visible bug. It must be replaced by a repository-level unread model that can represent exact counts, lower bounds, boolean presence, none, and unknown. Until that replacement exists, the coordinator must not expose its `Int` as an authoritative badge.

### 2. Late polling results can revive removed or replaced account state

The coordinator checks the account generation only before starting each loop iteration. It does not check the generation again after `source.notifications()` suspends and returns. If an account is removed, signed out, reauthenticated, or replaced while the request is in flight, the old response can still write `lastUpdated` and `unreadCount` into that account's state.

This violates the account/session fencing requirement. The fix is to capture a source/session generation, validate it after every suspended operation and before every state/repository write, and add a deterministic test where a delayed response completes after unregister or account replacement.

### 3. Notification sync is incorrectly owned by `FeedViewModel`

`FeedViewModel` registers the account with `AccountSyncCoordinator` during feed ViewModel construction and unregisters it when the feed stops. That means notification polling is tied to the existence and lifetime of the home-feed screen. It is not an application-lifetime service for every signed-in account, and it is not started by opening the notification destination because that destination does not yet have a ViewModel.

This causes stale or absent badges when the feed is not instantiated, and it makes future background delivery depend on a UI object. The coordinator must be restructured around available account sessions and a background-capable source provider. Feed lifecycle should not start or stop notification sync.

### 4. Follow-request action identity is unsafe at the shared boundary

`SocialSource.respondToFollowRequest(id: EntityId, accept: Boolean)` accepts one opaque entity ID. Both adapters then use `id.value` as the server-side user/account ID. A mapped notification has a distinct notification ID and actor account ID. A caller passing the row's notification ID—as the method name and current model make natural—will send the wrong identifier to Misskey or Mastodon.

No UI currently invokes this, so it may not have been observed in production, but it is a latent notification action bug. Change the contract to accept a typed receiving-side actor/account identity or a typed follow-request target, and test that the notification ID is never used as the follow-request account ID.

### 5. Grouped Mastodon mapping can fabricate an event identity

`MastodonMapper.groupedNotification()` falls back from `most_recent_notification_id` to `latest_page_notification_id`, and finally to the group key itself. A group key is a presentation aggregate key, not a notification event ID. Fabricating an `EntityId` from it can cause incorrect deduplication, unstable alert identity, and incorrect acknowledgement/reconciliation behavior.

A missing required notification identity should produce a diagnosed mapping failure or a safe group row whose identity is explicitly a group identity—not a fabricated notification ID. Add malformed-group fixtures and remove the group-key fallback for event identity.

### 6. Read semantics are truthful in the model but absent from the product

The default `Unknown` state is preferable to the old false unread assumption, but there is currently no repository to track locally seen state, server acknowledgement state, Android presentation state, or refresh freshness. No adapter maps individual notifications to read state, and no UI can mark rows seen or explicitly mark all read.

This is both a missing feature and a source of misleading behavior if the current `isRead` compatibility property is used. Keep `Unknown` distinct, deprecate/remove consumers of the boolean property, and implement the four-state model before adding badges or alerts.

### 7. Capability data is modeled but not yet authoritative for notifications

Notification capability fields and `effectiveCapabilityStatus()` exist, and capability values serialize correctly in tests. The default Misskey probe returns timeline/action data but no notification capability matrix; the default Mastodon capabilities also leave notification fields unknown. The source methods do not consistently gate or update those fields from verified endpoint evidence, and the session owner does not persist refreshed source capabilities back into the account session.

The UI therefore cannot yet distinguish “not granted,” “unsupported by this server,” “temporarily unavailable,” and “not probed.” Complete capability probing/resolution before hiding controls or presenting push/read features as available.

## What remains to reach full support

### Phase 0: freeze the real support matrix and remove evidence ambiguity

1. Choose the exact supported Misskey-family releases/forks, a current Mastodon release, and an older Mastodon without v2 grouped notifications. Record endpoint behavior, notification type names, read semantics, grouped pagination, push payload format, and unsupported behavior.
2. Pin documentation and test fixtures to those releases. Moving `develop` documentation can guide investigation but cannot be the sole compatibility contract.
3. Choose and pin a maintained UnifiedPush connector/distributor combination. Confirm its Android service/manifest contract and whether it delivers standard or legacy Web Push payloads.
4. Obtain designated test accounts and a device without Google Play Services. Do not mark push complete based on a local notification or mocked endpoint.
5. Resolve the discrepancy between historical `logs/DONE.txt` claims that Milestones 0–2 and push/device checks passed and the current source: the current repository has no push connector, push service, or Android notification presenter. The source and repeatable verification commands must be the completion evidence.

**Exit condition:** a versioned matrix, synthetic fixtures for every supported baseline, and a recorded real push round trip for at least one Misskey and one Mastodon account, with no secrets in the repository or logs.

### Phase 1: finish and correct the shared contracts

1. Keep notification identity, group identity, canonical target, receiving account, local-seen state, server-read/acknowledged state, and Android-presented state separate.
2. Replace the follow-request `EntityId` parameter with a typed target/account parameter.
3. Define notification sync results with freshness, gap detection, retry-after information, and unread precision. Do not expose a plain integer where the server only provides a lower bound or boolean.
4. Define one repository input model for REST pages, stream events, push hints, local visibility, dismissals, and acknowledgement outcomes. Every input must be account- and generation-bound.
5. Make capability resolution explicit and persist refreshed capabilities only for the matching account/session generation.
6. Add tests for unknown/system events, missing actors, missing optional targets, malformed required identity, group identity stability, account mismatch, and permission-upgrade cancellation.

### Phase 2: complete the REST adapters

#### Misskey

1. Verify ungrouped and grouped listing against the pinned server versions, including exact `sinceId`/`untilId` behavior, empty pages, repeated cursors, filtered pages, and more-than-one-page catch-up.
2. Keep `markAsRead=false` on every listing path, including grouped and retry paths.
3. Complete all supported notification type mappings: mentions/replies, renotes, quotes, reactions with Unicode and custom emoji, follows, follow requests, accepted requests, poll results, subscribed notes, system events, actorless events, and safe unknown fallback.
4. Confirm the authenticated-user unread fields and map exact count versus boolean presence honestly.
5. Keep read-all explicit and reconcile after it. Test a notification arriving during the read-all request.
6. Implement push registration/update/unregister only after the exact Misskey service-worker/Web Push contract is verified.

#### Mastodon

1. Implement `acknowledgeNotifications()` with notification timeline markers, including marker read/reconciliation behavior, serialized writes, conflict handling, and a rule that filtered Mentions browsing does not implicitly advance the account-wide marker.
2. Verify the unread-count endpoint and classify its result as exact or lower-bound according to the supported server versions.
3. Complete v1 type handling for mentions, replies inferred from status addressing, favourites, reblogs, follows, follow requests, subscribed statuses, polls, quotes, updates, moderation/report events, severed relationships, and supported collection variants.
4. Fix grouped identity fallback and test missing referenced accounts/statuses, deleted/private targets, empty actor samples, group count changes, and grouped pagination in both directions.
5. Add notification request/policy operations only for versions where the support matrix proves them, and keep request review separate from follow requests.
6. Implement Mastodon push subscription create/update/remove with verified endpoint and encryption-key handling.

#### Both adapters

1. Keep cursors opaque above each adapter and bind them to account, query, direction, and API variant.
2. Validate all continuation URLs before adding credentials. Preserve the no-redirect client behavior and test redirect responses explicitly.
3. Use transport order for cursor progression; never compare opaque IDs numerically, lexically, or by timestamp.
4. Reject fabricated required IDs and isolate malformed optional records so one bad item cannot erase a valid page.
5. Normalize rate limits, unauthorized access, revoked tokens, malformed JSON, unsupported endpoints, and network failures to useful `SourceError` states.

### Phase 3: build the durable account-scoped notification repository

1. Add `NotificationRepository` and an account-scoped persistent store. Room is a suitable choice because ingestion, deduplication, checkpoints, pending actions, and delivery records need atomic transactions and indexed queries; record the dependency rationale before adding it.
2. Persist canonical notification rows, group metadata, actors/previews, target snapshots, read knowledge, local-seen state, acknowledgement state, query checkpoints, sync gaps, presentation records, preferences, and pending operations.
3. Define a bounded retention policy and migration strategy. Pruning old content must not reset unread/read checkpoints or push delivery deduplication.
4. Keep private notification content in app-private storage. Exclude cached notification content, push keys, connector identity, and account secrets from inappropriate backup/device transfer. Delete account data and push material on logout.
5. Deduplicate all REST, stream, push, and retry inputs by receiving account plus canonical identity. Group rows must not cause repeated audible alerts merely because membership changed.
6. Establish a first-sync baseline: cache existing history without alerting for the backlog, then alert only events after the baseline. Add a bootstrap-race test.
7. Make all writes generation-fenced. Account removal, logout, failed permission upgrade, and reauthentication must prevent late network results from recreating rows, badges, preferences, or alerts.

### Phase 4: replace the polling scaffold and implement the inbox

1. Move notification synchronization out of `FeedViewModel` and into an application/account coordinator that can obtain every stored session independently of the feed.
2. Replace `AccountSyncState.unreadCount: Int` with precise unread knowledge and freshness. Use the adapter's unread endpoint, then reconcile through the repository rather than counting one page.
3. Implement `NotificationsViewModel` with account-bound `NotificationsState`: loading, cached, refreshing, paginating, new-arrival, empty-filter, empty-inbox, offline, retry, unauthorized, permission-upgrade, and unsupported states.
4. Replace the `NotificationsScreen` placeholder while retaining the existing All/Mentions navigation and separate Direct messages destination.
5. Implement type filters, pull-to-refresh, older pagination, newer catch-up, stable keys, account-specific filter and scroll restoration, and a new-activity indicator that does not jump the user while they read older entries.
6. Render actorless/system rows, custom emoji reactions, grouped actor counts, deleted/private targets, unknown types, timestamps, and read/seen cues accessibly. Reuse existing post/content-warning/sensitive-media presentation.
7. Route actor taps to profiles and post activity to the correct account-bound status/note/conversation. A cold-start Android tap must restore the receiving account before fetching the target; a removed account must show an explanatory state rather than silently using the currently selected account.
8. Add follow-request accept/reject controls using the corrected typed target. Keep ordinary post actions limited to capabilities actually supported by that account.
9. Add server notification policies where supported, and keep server delivery/filter settings separate from local Android alert preferences.

### Phase 5: implement UnifiedPush and Android presentation

1. Add the selected connector and an account/install-specific random registration identity. Never use an access token as a distributor identifier.
2. Implement distributor states: Off, choose service, registering, connected, permission needed, and temporarily unavailable. “Connected” must mean both distributor registration and social-server subscription succeeded.
3. Persist desired versus confirmed subscription, connector/distributor identity, endpoint generation, remote subscription metadata, and retry state. Handle endpoint rotation, duplicate callbacks, app updates, distributor removal, and process death.
4. Implement Misskey `sw/register`/update/unregister and Mastodon push subscription lifecycle with the exact verified Web Push key and payload contracts. Remove superseded subscriptions only after the replacement is confirmed.
5. Add a push service that validates account and registration generation, decrypts through the connector contract, parses notification/read/unrelated events, and stores a minimal event or refresh hint. Incomplete push payloads must trigger authenticated reconciliation and must not be sent directly through the full REST mapper.
6. Add persistent delivery deduplication and stable Android notification tags. REST, stream, and push delivery of the same event must converge on one stored row and at most one audible alert.
7. Add `POST_NOTIFICATIONS`, contextual permission request, stable channels, account grouping, privacy-safe previews, quiet behavior, DND/OS-setting respect, immutable account-bound pending intents, and cold-start routing.
8. Treat Android dismissal as a presentation event, not server deletion or mark-all-read. Suppress redundant sound only when the same activity is visibly being read.
9. On logout, fence local delivery first, attempt remote cleanup with the still-valid session, unregister the distributor identity, clear keys and displayed alerts, and reject later callbacks. Cleanup failure must not block logout.

### Phase 6: streaming, recovery, and settings

1. Implement typed account-bound streams for Misskey's authenticated main channel and Mastodon's supported user notification stream.
2. Add cancellation, exponential backoff with jitter, trusted origin rules, reconnect gap recovery, rate-limit handling, and authorization-failure stop conditions.
3. Use WorkManager for account-unique subscription retries, push-triggered reconciliation, and optional periodic fallback. Re-read session and generation when work starts. Present a 15-minute-or-later periodic check as delayed fallback, never as an invisible replacement for push.
4. Coalesce simultaneous push, resume, refresh, and reconnect requests into one account sync job.
5. Add settings for per-account alert enablement, categories, privacy previews, quiet hours, distributor selection/status, fallback checking, and supported server policies. Show selected versus successfully applied server settings and preserve unknown server fields.
6. Verify behavior after process death, reboot/unlock, Doze, offline recovery, force-stop, distributor removal, endpoint rotation, token revocation, and multiple accounts on the same origin.

### Phase 7: completion verification

Before claiming full notification support, add and pass all of the following:

- adapter contract tests for both protocols, every supported type, unknown/system records, malformed optional and required fields, filters, grouping, pagination directions, repeated cursors, malicious continuations, rate limits, revoked access, markers, and push lifecycle;
- repository tests for atomic ingestion, duplicate REST/stream/push delivery, bounded catch-up, retention, migration, corrupt-cache recovery, first-sync baseline, unread precision, read races, and concurrent account operations;
- coordinator tests proving cancellation and late responses cannot write after unregister, account removal, permission upgrade, or reauthentication;
- Compose/navigation tests for All, Mentions, filters, account badges, account switching, cached/error/empty/denied states, grouped rows, follow-request actions, target routing, large text, TalkBack semantics, light/dark themes, and wide layouts;
- Android presentation tests for permission denial, channels, privacy previews, quiet hours, stable tags, grouping, duplicate suppression, cold start, account removal, endpoint rotation, and logout;
- real authenticated Misskey and Mastodon delivery tests, including multiple accounts on one server, multiple origins, at least two maintained UnifiedPush distributors, a device without Play Services, a second client marking notifications read, more-than-one-page offline catch-up, endpoint replacement, process death, reboot/Doze, and token revocation;
- final release verification with `./gradlew test assembleRelease`, focused tests for each changed area, `./gradlew lintDebug`, dependency inspection proving no Firebase/FCM fallback, and a non-secret runtime evidence log.

## Current verification result

The following commands passed in this assessment:

```text
./gradlew :app:testDebugUnitTest --tests me.foxtails.palustris.NotificationContractTest --tests me.foxtails.palustris.NotificationAdapterContractTest
./gradlew test lintDebug assembleRelease
```

These results prove that the current code compiles, the synthetic notification contract/adapter tests pass, lint passes, and a release artifact can be assembled. They do **not** prove that notifications render in the app, that unread badges are correct, that read acknowledgement works on Mastodon, that accounts sync while the feed is absent, or that either protocol delivers a real UnifiedPush alert. No authenticated server round trip or device push smoke test was performed as part of this assessment.

Full support is reached only when the repository, inbox, read semantics, account lifecycle, target actions, UnifiedPush delivery, Android presentation, foreground streaming, recovery, and real-device verification all satisfy the completion gates above.
> Archived progress notes. The implemented notification architecture and tests are the current source of truth; statements below may describe intermediate work rather than current behavior.
