# Architecture

Status: current  
Owner: Maintainers  
Last reviewed: 2026-09-15  
Stale when: A stable module boundary or a primary owner changes.

Sources: `AGENTS.md`, current application source, and current tests.

## Purpose

Beeline is one Android module. The code separates the protocol-neutral domain, the
protocol adapters, persistence, and the Compose presentation. Dependencies point
toward the domain.

## Layer Map

| Layer | Package | Responsibility |
| --- | --- | --- |
| Domain | `domain/` | Protocol-neutral models and contracts. |
| Data | `data/` | Data access and persistence. |
| Misskey adapter | `data/misskey/` | Misskey transport, mapping, and capabilities. |
| Mastodon adapter | `data/mastodon/` | Mastodon transport, mapping, and capabilities. |
| Authentication | `data/auth/` | Authentication and encrypted session storage. |
| Notifications | `data/notifications/` | Ingestion, storage, synchronization, and push. |
| Direct messages | `data/directmessages/` | Direct-message storage. |
| Preferences | `data/preferences/` | File-backed application preferences. |
| Media | `data/media/` | Image loading and AVIF decoding. |
| Emoji | `data/emoji/` | Emoji catalog cache and assets. |
| Dependency injection | `di/` | Hilt providers. |
| Presentation | `ui/` | Compose screens, ViewModels, and shell contracts. |

`domain/` does not import `data/`, `ui/`, or `di/`.

## Protocol Boundary

- [`SocialSource`](../../app/src/main/java/me/foxtails/palustris/domain/SocialSource.kt) is the shared source contract. Each adapter implements it.
- Unsupported operations throw `SourceError.Unsupported`. Do not treat one failed request as proof of server absence.
- [`SocialSourceFactory`](../../app/src/main/java/me/foxtails/palustris/data/SourceFactory.kt) creates a source for a session. It branches on `Protocol` only at creation.
- [`ServerCapabilities`](../../app/src/main/java/me/foxtails/palustris/domain/ServerCapabilities.kt) describes feature availability. `CapabilityStatus` separates unknown, denied, unsupported, and temporary states.
- `effectiveCapabilityStatus` resolves a usable state from server status, access status, and implementation status.
- Cursors stay opaque above the adapters. A cursor binds to its account and query.
- The `MISSKEY` and `MASTODON` branches live in adapters, authentication, source creation, and capability detection. Do not add them to generic UI or ViewModels.

## Account And Session Identity

- [`AccountId`](../../app/src/main/java/me/foxtails/palustris/domain/AccountId.kt) is a connection origin plus a local ID. The protocol is metadata.
- [`Connection`](../../app/src/main/java/me/foxtails/palustris/domain/Connection.kt) is a validated HTTPS origin. `isValid()` rejects credentials, paths, queries, and fragments.
- [`Session`](../../app/src/main/java/me/foxtails/palustris/domain/Session.kt) holds the token, capabilities, access grant, push state, and `sessionRevision`.
- [`AccountManager`](../../app/src/main/java/me/foxtails/palustris/ui/AccountManager.kt) owns account and session state. It restores sessions, runs sign-in, switches accounts, updates profiles, and removes accounts.
- [`AccountSourceRegistry`](../../app/src/main/java/me/foxtails/palustris/data/AccountSourceRegistry.kt) owns source identity at application scope. A source is valid only for its registered generation.

Keep three identities separate:

| Identity | Owner | Purpose |
| --- | --- | --- |
| `sessionGeneration` | `AccountManager` | Runtime presentation generation. It changes on each connect. |
| `sessionRevision` | `Session` | Durable revision. It increases on session replacement. |
| Notification registry generation | `NotificationSyncToken` | Source registration generation. `AccountSourceRegistry` validates it. |

## Persistence

- [`EncryptedSessionStore`](../../app/src/main/java/me/foxtails/palustris/data/auth/SessionStore.kt) keeps accounts under `noBackupFilesDir/accounts`. It uses AES-GCM with one non-exportable Android Keystore key.
- Pending authentication is encrypted in `pending.enc` and expires after 15 minutes.
- Notifications use the Room database `notifications.db` with explicit migrations.
- Direct messages use the Room database `directmessages.db` under `noBackupFilesDir`.
- The emoji catalog uses `EmojiCacheDatabase`.
- Preferences are file-backed for the application, posts, the emoji picker, and Photo Grid.
- Drafts use `EncryptedDraftStore` and the same encrypted account storage key.

## Presentation Composition

- [`MainActivity`](../../app/src/main/java/me/foxtails/palustris/MainActivity.kt) is the Android entry point. It dispatches intents, applies the locale, and applies the refresh-rate policy.
- [`ConnectedApp`](../../app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt) collects session and account index, chooses startup, sign-in, or connected presentation, installs theme and content policy, and composes the session host.
- [`ConnectedSessionHost`](../../app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt) owns one connected account and session lifetime. It resolves the source, composes the focused feature hosts, and keeps the shared feed, draft, post-action, and projection owners.
- [`PalustrisApp`](../../app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt) owns navigation, adaptive layout, and destination composition.
- `ui/shell/` holds narrow feature contracts. [`PostProjectionCoordinator`](../../app/src/main/java/me/foxtails/palustris/ui/shell/PostProjectionCoordinator.kt) is the single fan-out owner for normalized post updates and accepted publications.
- Feature packages own their screens and ViewModels. See [App Shell Ownership](../agents/app-shell-ownership.md) for the current contract map.
- Keep account ownership, transport behavior, protocol JSON, and persistent storage outside Compose functions.

### Completion State

The shell decomposition is complete. `ConnectedSessionHost` reads one connected
identity with no unregistered fallback. The composer editor lives in `ui/composer/`,
not in `PalustrisApp`. The acceptance matrix records each exit condition. See
[Decomposition 01 and 02 Acceptance Matrix](../agents/decomposition-01-02-acceptance-matrix.md).

## Notification And Delivery

- [`NotificationRepository`](../../app/src/main/java/me/foxtails/palustris/data/notifications/NotificationRepository.kt) is the merge point for notification data.
- [`NotificationSyncOrchestrator`](../../app/src/main/java/me/foxtails/palustris/data/notifications/NotificationSyncOrchestrator.kt) owns synchronization.
- [`ForegroundNotificationStreamController`](../../app/src/main/java/me/foxtails/palustris/data/notifications/ForegroundNotificationStreamController.kt) owns foreground streams.
- `UnifiedPushService` and `UnifiedPushRegistrationManager` own push registration.
- Background work uses WorkManager and unique account-scoped jobs.

## Dependency Rules

- `domain/` depends on nothing in the application.
- `data/` depends on `domain/`.
- `ui/` depends on `domain/` and receives data through Hilt and ViewModels.
- Keep protocol differences behind protocol boundaries.
- Keep shared domain behavior protocol-neutral.
- Do not tie background synchronization to a screen lifetime.

## Authoritative Tests

- Protocol contracts: `SocialSourceContractTest`, `MastodonSourceContractTest`, `ProfileSourceContractTest`.
- Sessions and accounts: `SessionViewModelTest`, `AuthGatewayTest`.
- Shell boundaries: `ShellCharacterizationTest`, `NavigationTest`, `HomeFeedTest`, `WideNavigationTest`, `PostProjectionCoordinatorTest`, `ShellBackPolicyTest`, `ShellNavigatorTest`, `ReplyComposerTest`, `ComposerOwnerTest`, `SearchPanelRestorationTest`, `SignInScreenTest`.
- Notifications: `NotificationRepositoryTest`, `NotificationContractTest`, `NotificationAdapterContractTest`.
- Persistence: `AppPreferencesRepositoryTest`, `PhotoGridPreferencesRepositoryTest`, `EmojiCacheDatabaseTest`.
- Device behavior: `Api29StartupInstrumentedTest`, `RoomNotificationStoreInstrumentedTest`.

Tests live in `app/src/test/java/me/foxtails/palustris/` and `app/src/androidTest/java/me/foxtails/palustris/`.
