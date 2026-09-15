# Protocol And Session Ownership

**Owner:** Protocol, session, and persistence maintainers.

**Status:** current. Source verified. Completion slice C-01 closed the connected-identity gap and
is test verified in the working tree.

**Last reviewed:** 2026-09-14.

**Source baseline:** `b629a2c`.

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

## Capability States

`domain/ServerCapabilities.kt` defines `CapabilityStatus`: `Supported`, `Denied`, `Unsupported`,
`TemporarilyUnavailable`, and `Unknown`. `effectiveCapabilityStatus` resolves the usable state from
the server status, the access status, and the implementation status. Keep `Unknown` separate from
`Unsupported`.

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

## Direct-Message Write Authority

`data/directmessages/DirectMessageWriteAuthority.kt` owns one writer generation for each account.

- `issue` returns the generation for a new writer.
- `isCurrent` compares a generation with the current value.
- `commitIfCurrent` runs a block under the account lock when the generation is current.
- `invalidate` revokes writers without deleting rows.
- `invalidateAndDelete` revokes writers and deletes rows in one serialized boundary.

`AccountManager.removeAccount` calls `invalidateAndDelete` before it deletes the store rows
(`AccountManager.kt:317`). It also calls `draftStore.deleteAll` (`AccountManager.kt:320`).

**Open gap:** `DirectMessageRepository.markRead` uses a separate `isCurrent` check and then a
store write (`DirectMessageRepository.kt:97-103`). It does not use `commitIfCurrent`. A removal can
interleave between the check and the write. Completion slice C-03 closes this race.

**Open gap:** `DirectMessageWriteAuthority.invalidate` changes the generation outside the commit
mutex. The class documents this as intentional. Completion slice C-03 must confirm that activation,
retirement, deletion, and accepted writes share one account boundary.

## Account Removal Coverage

`AccountManager.removeAccount` stops the notification stream, disables push, removes sync, removes
post preferences, removes Photo Grid preferences, revokes direct-message writers and deletes the
direct-message store, deletes drafts, removes the emoji catalog and picker preferences, deletes
the session, and updates the account index.

The account-removal draft gap from an earlier review is closed. `draftStore.deleteAll(accountId)`
runs at `AccountManager.kt:320`. The stale claim in older ownership and bug records is removed.

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
- Completion slice C-03 adds a Room-backed removal and late-write test.
