# Overview

Status: current  
Owner: Maintainers  
Last reviewed: 2026-09-15  
Stale when: Product scope, supported server families, platform limits, or feature status changes.

Sources: `README.md`, `AGENTS.md`, `app/build.gradle.kts`, `app/src/main/java/me/foxtails/palustris/ProductIdentity.kt`, and current application source.

## Purpose

Beeline is an Android client for the fediverse. Beeline treats several server families as one social space. It abstracts away the instance and the server software.

Beeline is one Android module in `:app`. Beeline uses Kotlin, Jetpack Compose, and Material 3. See [Architecture](architecture.md) for the layer boundaries.

## Platform

- Beeline supports Android 10 and later. The minimum SDK is 29.
- The compile SDK and the target SDK are 37. Source: `app/build.gradle.kts`.
- The application ID is `me.foxtails.palustris`. Existing identifiers keep the internal codename. Do not change them as unrelated cleanup.
- The product name is Beeline. Source: [`ProductIdentity.kt`](../../app/src/main/java/me/foxtails/palustris/ProductIdentity.kt).
- The version name is 0.2.7 by default. Source: `app/build.gradle.kts`.

## Supported Servers

- Beeline supports Misskey-family servers. Misskey has first-class protocol support.
- Beeline supports Mastodon-compatible servers. Mastodon has first-class adapter behavior.
- Beeline detects the protocol before it starts protocol-specific authentication. Source: [`DetectingAuthGateway.kt`](../../app/src/main/java/me/foxtails/palustris/data/auth/DetectingAuthGateway.kt).
- Pixelfed, Glitch, Pleroma, and Akkoma are not tested.

## Feature Areas

| Area | Status | Source owner |
| --- | --- | --- |
| Timelines | Available | [`FeedViewModel.kt`](../../app/src/main/java/me/foxtails/palustris/ui/FeedViewModel.kt) |
| Profiles and relationships | Available. Includes follow, block, mute, and report. | [`ProfileViewModel.kt`](../../app/src/main/java/me/foxtails/palustris/ui/profile/ProfileViewModel.kt), [`PostActionOwner.kt`](../../app/src/main/java/me/foxtails/palustris/ui/posts/PostActionOwner.kt) |
| Threads | Available | [`PostThreadViewModel.kt`](../../app/src/main/java/me/foxtails/palustris/ui/thread/PostThreadViewModel.kt) |
| Notifications | Available | [`NotificationRepository.kt`](../../app/src/main/java/me/foxtails/palustris/data/notifications/NotificationRepository.kt) |
| Direct messages | Available. Federated direct posts, not encrypted. | [`DirectMessageViewModel.kt`](../../app/src/main/java/me/foxtails/palustris/ui/directmessages/DirectMessageViewModel.kt) |
| Bookmarks and likes | Available | [`SavedPostsViewModel.kt`](../../app/src/main/java/me/foxtails/palustris/ui/SavedPostsViewModel.kt) |
| Photo Grid | Available. It belongs to the Search destination and keeps independent state. | [`PhotoGridScreen.kt`](../../app/src/main/java/me/foxtails/palustris/ui/PhotoGridScreen.kt) |
| Emoji catalog and reactions | Available | [`EmojiCatalogViewModel.kt`](../../app/src/main/java/me/foxtails/palustris/ui/emoji/EmojiCatalogViewModel.kt) |
| Moderation lists | Available. Blocked accounts, muted accounts, and muted hashtags. | [`ModerationViewModel.kt`](../../app/src/main/java/me/foxtails/palustris/ui/settings/ModerationViewModel.kt) |
| Settings | Available | [`SettingsHost.kt`](../../app/src/main/java/me/foxtails/palustris/ui/settings/SettingsHost.kt) |
| Localization | Available. Source: `app/src/main/res/values-*`. | [`AppLocaleController.kt`](../../app/src/main/java/me/foxtails/palustris/ui/localization/AppLocaleController.kt) |

The `README.md` TODO claims are stale for block, mute, and report. Current source implements those actions.

## Privacy

- The application keeps access tokens in encrypted no-backup storage. Source: [`SessionStore.kt`](../../app/src/main/java/me/foxtails/palustris/data/auth/SessionStore.kt).
- Direct messages are federated direct posts. Beeline does not encrypt them. Do not describe them as secure or private.
- Never log access tokens, client secrets, authorization codes, push keys, or authorization headers.

## Known Limits

- Media attachment upload is not available in the composer. Beeline displays attachments. Source: no attachment picker exists in `ui/`.
- Native Misskey chat is not implemented. Beeline uses the direct-message model for both protocols.
- Misskey antennas, followed hashtags, and Mastodon lists are not supported. Source: no matching domain type exists.
- Content warning handling uses local rules and post flags. Treat the behavior as partial.
- Live-server behavior and physical-device rendering are not fully verified. See `logs/BUGS.txt`.

## Verification Limits

- Mocked HTTP tests do not prove live-server behavior.
- Compose tests do not prove physical-device rendering.
- Release assembly does not prove signed publication.

See `AGENTS.md` (Verification section) for the commands and the evidence rules.
