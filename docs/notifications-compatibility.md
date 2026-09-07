# Notifications compatibility notes

Updated September 7, 2026 for the Milestone 1 contract work. This is a
conservative implementation matrix, not a promise that every moving server
release supports every notification feature.

## Baseline contracts

| Concern | Misskey-family baseline | Mastodon baseline | App behavior |
|---|---|---|---|
| Listing | `POST /api/i/notifications`; `sinceId`/`untilId`; requests must send `markAsRead=false` | `GET /api/v1/notifications`; type filters and absolute `Link` continuations | Misskey listing remains a Milestone 2 adapter task; Mastodon uses opaque cursors and the v1 compatibility path |
| Read acknowledgement | Account-wide `POST /api/notifications/mark-all-as-read` | Notification timeline marker through `/api/v1/markers` | Modeled as explicit acknowledgement; no fetch implicitly marks the inbox read |
| Unread knowledge | User-level unread fields vary by server version; individual notification records do not guarantee a read flag | Timeline markers and server-specific support vary | `Exact`, `LowerBound`, `Boolean`, `None`, and `Unknown` are distinct |
| Grouping | Optional `i/notifications-grouped`, with Misskey-specific paging | Optional `/api/v2/notifications`; older servers use v1 | Group identity is account-scoped and never replaces notification identity |
| Push | `sw/register`, update, and unregister lifecycle | `/api/v1/push/subscription`; requires the separate `push` OAuth scope | UnifiedPush/Web Push is represented by `PushSubscriptionSpec`; transport wiring is deferred |

The Misskey listing and read-all behavior is based on the endpoint sources linked
from the roadmap. Mastodon application registration and push scope behavior are
based on the official application and push method documentation.

## Synthetic coverage

The shared contract fixture suite currently covers:

- actorless and unknown Mastodon notifications;
- actorless and custom-emoji Misskey reactions;
- canonical post targets and account-scoped notification identity;
- rejected non-HTTPS or credential-bearing destinations;
- notification capability resolution across server, access, and client states;
- old encrypted sessions and pending-login migration;
- Mastodon app-cache scope compatibility and permission-upgrade identity safety.

These fixtures intentionally use inline JSON so no access tokens, private posts,
or distributor credentials are stored in the repository or logs.

## Open Milestone 0 evidence

The authenticated UnifiedPush round trip is not yet verified. No designated
Misskey/Mastodon test accounts or distributor credentials were available in this
workspace, so this matrix does not claim registration, delivery, decryption, or
unregistration success. The implementation keeps push capability and requested
access explicit so the connector spike can be added without changing the shared
domain boundary.

References: [Mastodon applications](https://docs.joinmastodon.org/methods/apps/),
[Mastodon push subscriptions](https://docs.joinmastodon.org/methods/push/),
[Misskey API permissions](https://github.com/misskey-dev/misskey-hub/blob/main/src/docs/api/index.md),
and the endpoint sources linked in [the notifications roadmap](roadmaps/notifications.md).
