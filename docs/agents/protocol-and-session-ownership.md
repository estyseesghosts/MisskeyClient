# Protocol And Session Ownership

**Owner:** Protocol, session, and persistence maintainers.

**Status:** current. Source verified. Completion slices C-01, C-03, and C-06c are implemented and
test verified. C-01 closed the connected-identity gap. C-03 closed the direct-message write gap.
C-06c closed the draft removal gap.

**Last reviewed:** 2026-09-15.

**Source baseline:** `b629a2c` (planning). Status refreshed against `c9e06c8`.

**Evidence:** source verified. Test files were inspected, not executed in this review.
Device and live-server behavior remain unverified.

**Completion owner:** `docs/agents/tasks/decomposition-01-02-completion.md`.

## Source Ownership

- `domain/SocialSource.kt` is the only transport contract. Each adapter implements it.
- `data/misskey/MisskeySource` and `data/mastodon/MastodonSource` are the two adapters.
- `data/SourceFactory.kt` creates one source for a session. It branches on `Protocol` only there.
- `data/AccountSourceRegistry.kt` stores one source for each `AccountId` together with a
  generation from `NotificationSyncToken`.
- `sourceFor(token)` returns null when the generation is stale. `sourceFor(accountId)` ignores
  the generation. `isCurrent` compares identity.
- `NotificationSyncController.register` registers a source and returns the `NotificationSyncToken`
  that owns it.
- `AccountManager` registers the active source for notification sync when a session becomes active.
  It publishes one `ConnectedSessionContext` for that registered source.
- Unsupported operations throw `SourceError.Unsupported` through the `unsupported` helper.

## Identity Invariants

Keep these three identities separate. Never substitute one for another.

| Identity | Owner | Changes when |
| --- | --- | --- |
| `sessionGeneration` | `AccountManager` | A session connects. It is a runtime presentation generation. |
| `sessionRevision` | `Session` | A session is replaced. It is a durable revision. |
| Registry generation | `NotificationSyncToken` | A source is registered. |

A stale callback must fail the generation or revision check. Do not treat the numbers as
interchangeable.

### Connected Identity

`AccountManager.connect` creates the source, registers it for notification sync, and publishes one
`ConnectedSessionContext`. The context joins the account identity, the visible account, the durable
session revision, the runtime presentation generation, and the registered source. The shell reads
that context. It does not join separate session flows.

`ConnectedSessionHost` reads `connectedContext.source`. The unregistered
`sourceFactory.create(session)` fallback is removed. `AccountManager` owns source construction.

`ui/session/ConnectedSessionContext.kt` owns the context type. `ConnectedSessionContextTest` covers
account switching, same-account replacement, profile updates, and retired registrations.

## Error Contract

`domain/SourceError.kt` defines the shared failure types:

- `Unauthorized` and `AccountMismatch` protect the session.
- `Unsupported` means the adapter does not implement the feature.
- `AccessDenied` and `UnsupportedCredential` mean the grant is insufficient.
- `ServerUnsupported` means the server rejects the feature.
- `RateLimited`, `ResourceLimit`, `NetworkUnavailable`, and `ServerError` are transient or bounded.
- `ForeignOrigin` protects authenticated requests against another origin.

Keep unknown, denied, and unsupported states separate. Do not mark a feature unsupported after one
failed request.

## Origin Validation

`domain/Connection.kt` requires an HTTPS origin. `isValid()` rejects credentials, paths, queries,
and fragments. Validate the origin before an authenticated request. Validate pagination origins and
entity origins before account actions.

`MisskeySource` validates every entity identity through `validatePostId(id, feature)` before it
builds an authenticated request. The validator rejects a foreign connection origin and a blank
value. `post` and `delete` now call it, as do thread reads, reactions, favourites, reposts, saves,
repost undo, create reply origins, and create quote identities. A foreign public URL on a locally
fetched entity remains valid.

## Capability States

`domain/ServerCapabilities.kt` defines `CapabilityStatus`: `Supported`, `Denied`, `Unsupported`,
`TemporarilyUnavailable`, and `Unknown`. `effectiveCapabilityStatus` resolves the usable state from
the server status, the access status, and the implementation status. Keep `Unknown` separate from
`Unsupported`.

`SocialSource.observeCapabilities()` publishes a protocol-neutral capability snapshot. The default
returns the current snapshot once. `MisskeySource` and `MastodonSource` return their capability
state flow. `EmojiHost` collects that flow and passes `EmojiCapabilities` to `EmojiPresentation`, so
a refreshed probe can update reaction controls without a catalog or navigation change.

Mastodon reaction support comes from the recognized extension advertisement. `MastodonSource.react`
and `removeReaction` do not downgrade support on a resource failure. A resource 404, 403, 429,
network failure, or 5xx returns the normalized `SourceError` and keeps the advertised support.
`MastodonCapabilityProbe` owns the advertisement rules and the bounded metadata read. It first reads
instance metadata. When instance metadata has no recognized advertisement, it inspects
`/.well-known/nodeinfo` and fetches at most one same-origin NodeInfo document. Discovery URLs must
be same-origin with the validated connection origin, carry no credential, and carry no fragment.
The reads follow no redirect and are bounded. Every discovery failure stays Unknown; it is not
Unsupported evidence. `MastodonAuth` uses `probeCapabilities`, so login applies the same rules.

`ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION` is `5`. Revision 5 invalidates snapshots
that recorded reaction support from the removed sentinel mutation probe. `refreshCapabilities`
re-probes a snapshot whose schema revision is not current.

A successful refresh publishes the snapshot through `onCapabilitiesUpdated`. `SocialSourceFactory`
wires that callback to `SessionStore.updateCapabilities` with the source `sessionRevision`.
`updateCapabilities` compares the stored session revision inside its transaction and writes nothing
when the revision differs. A stale source therefore cannot overwrite a replaced session.

`MastodonSource` bounds capability refresh retries. After a metadata failure it records
`capabilitiesRetryNotBefore = now + 30 seconds`. A request inside that window reuses the existing
capability evidence instead of re-probing. A successful probe clears the window.

## Persistence Contracts

| Data | Location | Protection |
| --- | --- | --- |
| Account sessions | `noBackupFilesDir/accounts` | AES-GCM with one Android Keystore key. |
| Pending authentication | `noBackupFilesDir/accounts/pending.enc` | Encrypted. Expires after 15 minutes. |
| Drafts | `noBackupFilesDir/drafts` | Same encrypted account storage key as sessions. |
| Notifications | Room `notifications.db` | Explicit migrations. |
| Direct messages | Room `directmessages.db` in `noBackupFilesDir` | Application database. |
| Emoji catalog | `EmojiCacheDatabase` | Cache. |
| Preferences | File-backed repositories | Application, post, emoji-picker, and Photo Grid stores. |

Account identity in storage is the connection origin plus the local ID. The protocol is metadata.

The notification stored format is frozen by `NotificationJsonCodecTest` and the literal
fixtures in `app/src/test/resources/notifications/`. Fixture provenance is recorded in
`app/src/test/resources/notifications/PROVENANCE.md`. The fixtures are synthetic
characterization fixtures, not captured released files. Do not generate the expected
fixture content with the encoder under test.

`NotificationJsonCodec.kt` owns the state boundary and every recursive encode and decode
helper. `NotificationRepository.kt` keeps the merge, generation, query-validation, and
delivery-claim behavior. It keeps no JSON conversion helper. Both `FileNotificationStore` and
`RoomNotificationStore` use the same internal `encode` and `decode` boundary.

## Direct-Message Write Authority

`data/directmessages/DirectMessageWriteAuthority.kt` owns one writer generation for each account.

- `issue` returns the generation for a new writer.
- `isCurrent` compares a generation with the current value.
- `commitIfCurrent` runs a block under the account lock when the generation is current.
- `invalidate` revokes writers without deleting rows.
- `invalidateAndDelete` revokes writers and deletes rows in one serialized boundary.

`AccountManager.removeAccount` calls `invalidateAndDelete` before it deletes the store rows
(`AccountManager.kt:326`). It revokes the draft writer and calls `deleteAll` in one serialized
boundary (`AccountManager.kt:330-331`).

C-03 closed the direct-message gap. `DirectMessageRepository.markRead` routes its local write
through `commitIfCurrent` (commit `bfbd7ed`). Activation, revocation, deletion, and accepted writes
share one per-account boundary under `DirectMessageWriteAuthority`.

C-06c closed the draft gap. `DraftWriteAuthority` mirrors the direct-message authority.
`DraftActions` captures the writer generation and routes save and delete through `commitIfCurrent`.
A revoked writer writes nothing. Removal revokes the draft writer before it deletes rows
(commit `4454bae`).

## Account Removal Coverage

`AccountManager.removeAccount` stops the notification stream, disables push, removes sync, removes
post preferences, removes Photo Grid preferences, revokes direct-message writers and deletes the
direct-message store, deletes drafts, removes the emoji catalog and picker preferences, deletes
the session, and updates the account index.

The account-removal draft gap from an earlier review is closed. `AccountManager.removeAccount`
revokes the draft writer and deletes rows in one serialized boundary (`AccountManager.kt:330-331`).
The stale claim in older ownership and bug records is removed.

## Affected Tests

- `SocialSourceContractTest`, `MastodonSourceContractTest`, `ProfileSourceContractTest`
- `SessionViewModelTest`, `ConnectedSessionContextTest`, `AuthGatewayTest`
- `DirectMessageRepositoryTest`, `DirectMessageViewModelTest`
- `NotificationRepositoryTest`, `NotificationAdapterContractTest`, `NotificationSynchronizerTest`
- `PushRegistrationRepositoryTest`, `PushCancellationTest`
- `AccountSourceRegistry` behavior is exercised through the notification and session tests.

## Verification Limits

- The registry, error, and write-authority behavior are JVM tested. Live-server behavior is
  unverified.
- Completion slice C-01 added `ConnectedSessionContextTest`. It passed with the focused run and the
  full `test assembleRelease` gate.
- Completion slice C-03 added the direct-message write-authority tests. C-06c added the draft
  removal and late-write tests.
- A Room-backed removal and late-write instrumentation test is not written. Device behavior stays
  unverified.
