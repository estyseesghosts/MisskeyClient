# Notifications and Direct Messages

Status: planned  
Owner: Notifications and messaging maintainers  
Last reviewed: 2026-09-16  
Stale when: Notification delivery or direct-message behavior changes.

Sources: `AGENTS.md`, `data/notifications/`, `data/directmessages/`, notification tests, and messaging tests.

## Purpose

<!-- Explain user-visible notification and direct-message behavior. -->

## Entries

<!-- Add inbox filters, unread state, delivery, push limits, account routing, and direct-message privacy limits. -->

### Direct-message threads

Source: `data/directmessages/DirectMessageRepository.kt`,
`data/mastodon/MastodonDirectMessageService.kt`,
`data/misskey/MisskeyDirectMessageService.kt`, and the direct-message tests.

- A conversation thread loads from a known post anchor in the stored conversation. The client
  does not guess a conversation identity from a post value.
- The Mastodon adapter loads the anchor through supported status endpoints. It never calls an
  undocumented individual conversation endpoint.
- Direct messages are federated private posts. They are not encrypted messaging.
